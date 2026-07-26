package com.osuserverlist.koneko.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.auth.Auth;
import com.osuserverlist.koneko.auth.UserSession;
import com.osuserverlist.koneko.config.Env;
import com.osuserverlist.koneko.config.SiteConfig;
import com.osuserverlist.koneko.plugin.api.ApiClient;
import com.osuserverlist.koneko.plugin.api.EventBus;
import com.osuserverlist.koneko.plugin.api.KonekoContext;
import com.osuserverlist.koneko.plugin.api.PluginCache;
import com.osuserverlist.koneko.plugin.api.PluginSettings;
import com.osuserverlist.koneko.plugin.api.PluginUser;

import io.javalin.http.Context;

/** The host services handed to one plugin. */
final class PluginContextImpl implements KonekoContext {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String pluginId;
    private final String pluginVersion;
    private final Logger logger;
    private final ApiClient api;
    private final PluginCache cache;
    private final PluginSettings settings;
    private final EventBus events;
    private final Path dataDir;

    PluginContextImpl(String pluginId, String pluginVersion, PluginSettings settings,
            EventBus events, Path dataDir) {

        this.pluginId = pluginId;
        this.pluginVersion = pluginVersion;
        this.logger = LoggerFactory.getLogger("Plugin/" + pluginId);
        this.api = new PluginApiClient(pluginId);
        this.cache = new PluginCacheImpl(pluginId);
        this.settings = settings;
        this.events = events;
        this.dataDir = dataDir;
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    @Override
    public String pluginVersion() {
        return pluginVersion;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Env env() {
        return App.env;
    }

    @Override
    public SiteConfig site() {
        return App.site;
    }

    @Override
    public ApiClient api() {
        return api;
    }

    @Override
    public PluginCache cache() {
        return cache;
    }

    @Override
    public ObjectMapper json() {
        return MAPPER;
    }

    @Override
    public Path dataDir() {
        // Created on demand rather than at boot, so a plugin that never stores
        // anything leaves no empty folder behind.
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            logger.warn("Could not create the data directory <{}>: {}", dataDir, e.getMessage());
        }

        return dataDir;
    }

    @Override
    public PluginSettings settings() {
        return settings;
    }

    @Override
    public EventBus events() {
        return events;
    }

    @Override
    public PluginUser user(Context ctx) {
        UserSession session = ctx == null ? null : Auth.current(ctx);

        if (session == null) {
            return null;
        }

        return new PluginUser(session.getUserId(), session.getUsername(),
                session.getPrivileges(), session.getTokens().getAccessToken());
    }

    @Override
    public List<String> plugins() {
        return PluginService.pluginIds();
    }

    @Override
    public String assetUrl(String path) {
        return "/plugin-assets/" + pluginId + PluginResources.normalise(path);
    }

    @Override
    public String actionUrl(String path) {
        return "/plugins/" + pluginId + PluginResources.normalise(path);
    }

    @Override
    public boolean dev() {
        return App.env.isDev();
    }
}
