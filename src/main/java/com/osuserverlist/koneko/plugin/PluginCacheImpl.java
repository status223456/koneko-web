package com.osuserverlist.koneko.plugin;

import java.util.function.Supplier;

import com.osuserverlist.koneko.api.FastCache;
import com.osuserverlist.koneko.plugin.api.PluginCache;

/**
 * The core FastLoad cache, namespaced per plugin.
 *
 * <p>Every key is prefixed with {@code plugin:{id}:}, so two plugins cannot
 * read or clear each other's entries and {@code clear()} stays a per plugin
 * operation.
 */
final class PluginCacheImpl implements PluginCache {

    private final String prefix;

    PluginCacheImpl(String pluginId) {
        this.prefix = "plugin:" + pluginId + ":";
    }

    @Override
    public boolean enabled() {
        return FastCache.enabled();
    }

    @Override
    public <T> T get(String key, Supplier<T> loader) {
        return FastCache.get(prefix + key, loader);
    }

    @Override
    public void invalidate(String keyPrefix) {
        FastCache.invalidate(prefix + (keyPrefix == null ? "" : keyPrefix));
    }

    @Override
    public void clear() {
        FastCache.invalidate(prefix);
    }
}
