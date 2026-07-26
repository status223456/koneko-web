package com.osuserverlist.koneko.plugin.api;

import java.util.function.Supplier;

/**
 * The FastLoad cache of the host, namespaced per plugin.
 *
 * <p>Same contract as the core one: a fresh entry is handed out without calling
 * the loader, a stale entry is handed out while it is refreshed in the
 * background, and only successful values are stored. Nothing user specific may
 * be cached here - the cache is shared by every visitor.
 */
public interface PluginCache {

    /** True when caching is on at all; false means every call loads. */
    boolean enabled();

    /** Returns the cached value, loading it when the window has passed. */
    <T> T get(String key, Supplier<T> loader);

    /** Drops every entry of this plugin whose key starts with the prefix. */
    void invalidate(String prefix);

    /** Drops everything this plugin has cached. */
    void clear();
}
