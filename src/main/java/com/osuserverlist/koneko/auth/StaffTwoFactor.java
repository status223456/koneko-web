package com.osuserverlist.koneko.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.api.ApiException;

import io.javalin.http.Context;

/**
 * The gate in front of the staff panel: a session gets in only after answering a code from the
 * account's authenticator.
 *
 * <p>Staff bits and a password are not enough here. A stolen session cookie is exactly the case
 * this is for - it carries every privilege the account has, and the panel is where those
 * privileges are worth stealing. So the panel asks for something the cookie cannot carry.
 *
 * <p>Two states, one gate. An account without an authenticator is sent to set one up, because
 * there is nothing to ask it for; an account with one is asked for a code. Both land on
 * {@link #PATH}, which works out which of the two it is looking at.
 *
 * <p>The answer is remembered on the session, not the account, and expires on its own. Asking
 * once per browser per working day is the trade being made: often enough that a stolen cookie
 * is close to useless, rarely enough that nobody starts leaving the panel open to avoid it.
 *
 * <p>Modelled on {@link Verification}, and applied the same way - per route, in front of the
 * pages and in front of every call the panel makes, rather than as one filter somewhere else.
 */
public final class StaffTwoFactor {

    private static final Logger logger = LoggerFactory.getLogger("StaffTwoFactor");

    /** Where a session that has not answered a code is sent, and the one panel page it may open. */
    public static final String PATH = "/admin/verify";

    /** What an account without an authenticator is told. */
    public static final String SETUP_MESSAGE =
            "The staff panel needs two-factor authentication. Turn it on in your settings first.";

    /** What an account with one is told. */
    public static final String CODE_MESSAGE =
            "Enter a code from your authenticator to open the staff panel.";

    /**
     * How long one answered code opens the panel for.
     *
     * <p>A working day, and no more: long enough not to interrupt somebody who is actually
     * moderating, short enough that a cookie taken this morning is useless tomorrow. The
     * session's own idle timeout still applies on top of it and is usually shorter.
     */
    private static final Duration CODE_LIFETIME = Duration.ofHours(12);

    /**
     * How long an answer about the account's authenticator is trusted for.
     *
     * <p>Short, because it changes on the other side of the API: somebody who just turned 2FA on
     * in another tab should not have to log out to be let in.
     */
    private static final Duration RECHECK_AFTER = Duration.ofSeconds(30);

    private StaffTwoFactor() {
    }

    /**
     * Whether the account behind this session has an authenticator set up.
     *
     * <p>Read from the API rather than stored here, and cached for a few seconds so a page of
     * parallel panel requests asks once. A failure to ask keeps the previous answer, which for a
     * fresh session is "no": if this cannot be established, the panel stays shut.
     */
    public static boolean enabled(UserSession session) {
        if (session == null) {
            return false;
        }

        if (Duration.between(session.getLastTwoFactorCheck(), Instant.now())
                .compareTo(RECHECK_AFTER) < 0) {
            return session.isTwoFactorEnabled();
        }

        try {
            JsonNode body = App.api.getAuthed("/api/v1/me/2fa", Map.of(),
                    session.getTokens().getAccessToken());

            session.setTwoFactorEnabled(body.path("enabled").asBoolean(false));
            session.setLastTwoFactorCheck(Instant.now());
        } catch (ApiException e) {
            // Not cached, so the next request tries again rather than sitting on a failure.
            logger.warn("Could not read the 2FA state of <{}>: {}",
                    session.getUsername(), e.getMessage());
        }

        return session.isTwoFactorEnabled();
    }

    /** Whether this session may use the panel: an authenticator, and a recent code from it. */
    public static boolean satisfied(UserSession session) {
        if (session == null) {
            return false;
        }

        return Duration.between(session.getStaffCodeAt(), Instant.now())
                .compareTo(CODE_LIFETIME) < 0 && enabled(session);
    }

    /** Records that this session has just answered a code. */
    public static void accept(UserSession session) {
        session.setStaffCodeAt(Instant.now());

        logger.info("Staff <{}>({}) answered a two factor code for the panel",
                session.getUsername(), session.getUserId());
    }

    /**
     * Guard for a panel page: sends the session to the gate.
     *
     * @return true when the request has been answered and the handler must stop
     */
    public static boolean blocksPage(Context ctx, UserSession session) {
        if (satisfied(session)) {
            return false;
        }

        ctx.redirect(PATH);

        return true;
    }

    /**
     * Guard for anything the panel calls.
     *
     * <p>{@code 403} rather than {@code 401}: the session is perfectly valid, it is this
     * particular door that is shut. The body says which of the two states it is in, so a panel
     * left open in a tab can say what to do about it instead of showing a bare refusal.
     *
     * @return true when the request has been answered and the handler must stop
     */
    public static boolean blocksApi(Context ctx, UserSession session) {
        if (satisfied(session)) {
            return false;
        }

        boolean hasAuthenticator = enabled(session);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", hasAuthenticator ? CODE_MESSAGE : SETUP_MESSAGE);
        body.put("twoFactor", hasAuthenticator ? "code" : "setup");

        ctx.status(403).header("Cache-Control", "private, no-store").json(body);

        return true;
    }
}
