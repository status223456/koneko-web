package com.osuserverlist.koneko.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In memory store of logged in browsers.
 *
 * <p>Deliberately not backed by Redis: a frontend session holds nothing worth
 * surviving a restart, and losing them only asks the players to log in again.
 * Idle sessions are swept once a minute so a long uptime does not leak memory.
 */
public final class SessionStore {

    private static final Logger logger = LoggerFactory.getLogger("SessionStore");

    private static final int ID_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Map<String, UserSession> SESSIONS = new ConcurrentHashMap<>();

    private static volatile Duration idleTimeout = Duration.ofMinutes(1440);

    private static volatile ScheduledExecutorService sweeper;

    private SessionStore() {
    }

    /** Called once at boot with the timeout from the environment. */
    public static synchronized void configure(int idleTimeoutMinutes) {
        idleTimeout = Duration.ofMinutes(Math.max(1, idleTimeoutMinutes));

        if (sweeper != null) {
            return;
        }

        sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "koneko-session-sweeper");
            thread.setDaemon(true);
            return thread;
        });

        sweeper.scheduleAtFixedRate(SessionStore::sweep, 1, 1, TimeUnit.MINUTES);
    }

    /** Stores a session and returns the id the browser gets as a cookie. */
    public static String create(UserSession session) {
        byte[] bytes = new byte[ID_BYTES];
        RANDOM.nextBytes(bytes);

        String id = ENCODER.encodeToString(bytes);
        SESSIONS.put(id, session);

        return id;
    }

    /** Returns the session, or null when it is unknown or has gone idle. */
    public static UserSession get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        UserSession session = SESSIONS.get(id);

        if (session == null) {
            return null;
        }

        if (isExpired(session)) {
            SESSIONS.remove(id);
            return null;
        }

        session.touch();
        return session;
    }

    public static UserSession remove(String id) {
        return id == null ? null : SESSIONS.remove(id);
    }

    public static int size() {
        return SESSIONS.size();
    }

    private static boolean isExpired(UserSession session) {
        return Instant.now().isAfter(session.getLastSeen().plus(idleTimeout))
                || session.getTokens().isDead();
    }

    private static void sweep() {
        int before = SESSIONS.size();

        Iterator<Map.Entry<String, UserSession>> iterator = SESSIONS.entrySet().iterator();

        while (iterator.hasNext()) {
            if (isExpired(iterator.next().getValue())) {
                iterator.remove();
            }
        }

        int removed = before - SESSIONS.size();

        if (removed > 0) {
            logger.debug("Swept {} idle session(s), {} left", removed, SESSIONS.size());
        }
    }
}
