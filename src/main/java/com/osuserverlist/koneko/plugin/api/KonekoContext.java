package com.osuserverlist.koneko.plugin.api;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osuserverlist.koneko.config.Env;
import com.osuserverlist.koneko.config.SiteConfig;

import io.javalin.http.Context;

/** Everything the host lends to a plugin. */
public interface KonekoContext {

    /** Id of the plugin, from {@code Plugin-Id} in the jar manifest. */
    String pluginId();

    /** Version of the plugin, from {@code Plugin-Version}. */
    String pluginVersion();

    /** A logger named after the plugin. */
    Logger logger();

    /** Deployment settings of the site ({@code .env}). Read only. */
    Env env();

    /** Texts of the site ({@code config.yml}). Read only. */
    SiteConfig site();

    /** The bancho.jar API, both anonymously and as the current player. */
    ApiClient api();

    /** The shared FastLoad cache, namespaced to this plugin. */
    PluginCache cache();

    /** The Jackson mapper of the host, so a plugin ships no second one. */
    ObjectMapper json();

    /**
     * A private directory for this plugin, created on demand. Use it for
     * anything that has to survive a restart.
     */
    Path dataDir();

    /** This plugin's section of {@code config.yml} plus its own yml file. */
    PluginSettings settings();

    /** The event bus, for publishing and subscribing at runtime. */
    EventBus events();

    /** The player behind a request, or null when nobody is logged in. */
    PluginUser user(Context ctx);

    /** Ids of every started plugin, so plugins can detect each other. */
    List<String> plugins();

    /** The public url of one of this plugin's assets. */
    String assetUrl(String path);

    /** The public url of one of this plugin's relative actions. */
    String actionUrl(String path);

    /** Whether the site runs with {@code LEVEL=DEV}. */
    boolean dev();
}
