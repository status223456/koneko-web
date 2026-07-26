package com.osuserverlist.koneko.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FastLoad, server side: a small in memory cache in front of the bancho.jar
 * API.
 *
 * <p>Every entry has two ages. While it is <em>fresh</em> it is handed out
 * directly, so a page render costs no API call at all. Once it is only
 * <em>stale</em> it is still handed out immediately, but a background refresh
 * is started, so the next visitor gets the new numbers - a request never waits
 * for the API because of a cache miss it did not cause. Past that window the
 * entry is loaded again the normal, blocking way.
 *
 * <p>Only successful bodies are cached: an exception propagates and nothing is
 * stored, so a short API outage cannot be frozen into the cache.
 *
 * <p>Nothing user specific may be put in here - the cache is shared by every
 * visitor. Sessions and the logged in player come from the request itself.
 */
public final class FastCache {

    private static final Logger logger = LoggerFactory.getLogger("FastCache");

    /** Enough for the front page plus a few hundred profiles. */
    private static final int MAX_ENTRIES = 512;

    private record Entry(Object value, long storedAt) {
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> REFRESHING = new ConcurrentHashMap<>();

    private static volatile long freshMillis;
    private static volatile long staleMillis;

    private FastCache() {
    }

    /**
     * @param ttlSeconds   how long an entry is served without any API call at
     *                     all. Zero turns FastLoad off completely.
     * @param staleSeconds how much longer an entry may still be served while it
     *                     is refreshed in the background.
     */
    public static void configure(int ttlSeconds, int staleSeconds) {
        freshMillis = Math.max(0, ttlSeconds) * 1000L;
        staleMillis = Math.max(0, staleSeconds) * 1000L;

        ENTRIES.clear();
        REFRESHING.clear();
    }

    public static boolean enabled() {
        return freshMillis > 0;
    }

    /** The fresh window in seconds, for the Cache-Control header. */
    public static int freshSeconds() {
        return (int) (freshMillis / 1000L);
    }

    /**
     * Returns the cached value for the key, loading it when needed.
     *
     * @param loader called on a miss and by the background refresh. It must be
     *               safe to call from another thread.
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> loader) {
        if (!enabled()) {
            return loader.get();
        }

        Entry entry = ENTRIES.get(key);

        if (entry == null) {
            return store(key, loader.get());
        }

        long age = System.currentTimeMillis() - entry.storedAt();

        if (age <= freshMillis) {
            return (T) entry.value();
        }

        if (age <= freshMillis + staleMillis) {
            refresh(key, loader);
            return (T) entry.value();
        }

        return store(key, loader.get());
    }

    /** Drops every entry whose key starts with the prefix. */
    public static void invalidate(String prefix) {
        ENTRIES.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static <T> T store(String key, T value) {
        if (value != null) {
            if (ENTRIES.size() >= MAX_ENTRIES) {
                purge();
            }

            ENTRIES.put(key, new Entry(value, System.currentTimeMillis()));
        }

        return value;
    }

    private static <T> void refresh(String key, Supplier<T> loader) {
        // One refresh per key: a popular page must not turn one expiry into a
        // hundred parallel API calls.
        if (REFRESHING.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }

        Thread.ofVirtual().name("fastload").start(() -> {
            try {
                store(key, loader.get());
            } catch (RuntimeException e) {
                logger.warn("Background refresh of <{}> failed: {}", key, e.getMessage());
            } finally {
                REFRESHING.remove(key);
            }
        });
    }

    private static void purge() {
        long cutoff = System.currentTimeMillis() - (freshMillis + staleMillis);

        ENTRIES.entrySet().removeIf(entry -> entry.getValue().storedAt() < cutoff);

        if (ENTRIES.size() >= MAX_ENTRIES) {
            ENTRIES.clear();
        }
    }
}
