package com.osuserverlist.koneko.plugin.api;

import java.util.List;
import java.util.Map;

/**
 * Configuration of one plugin.
 *
 * <p>Two sources, the file winning over the site config:
 *
 * <ol>
 *   <li>the {@code plugins.settings.{pluginId}} block of {@code config.yml};</li>
 *   <li>{@code .config/plugins/{pluginId}.yml}.</li>
 * </ol>
 *
 * <p>Dotted keys walk into nested maps, so {@code settings.string("discord.channel", "")}
 * reads what you would expect.
 */
public interface PluginSettings {

    boolean has(String key);

    String string(String key, String fallback);

    int integer(String key, int fallback);

    long number(String key, long fallback);

    boolean bool(String key, boolean fallback);

    List<String> list(String key);

    Map<String, Object> map(String key);

    /** Everything, as a plain map. Never null. */
    Map<String, Object> all();

    /** Binds the whole settings tree onto a class of your own. */
    <T> T as(Class<T> type);
}
