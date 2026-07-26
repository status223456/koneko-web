package com.osuserverlist.koneko.plugin.api;

import java.util.List;
import java.util.Map;

import io.javalin.http.Context;

/**
 * The events the host publishes. Subscribe with
 * {@code registry.on(Events.Login.class, event -> ...)}.
 *
 * <p>Records whose fields are mutable maps are meant to be written to: that is
 * how a plugin adds data to a page it does not own.
 */
public final class Events {

    private Events() {
    }

    /** Published once per extension, right after the plugin started. */
    public record Startup(KonekoContext context) {
    }

    /** Published when every plugin has been started and registered. */
    public record Ready(List<String> pluginIds) {
    }

    /** Published while the site shuts down, before the plugins are stopped. */
    public record Shutdown(KonekoContext context) {
    }

    /**
     * Published when the page state is built, before it reaches the browser.
     * Writing to {@code state} adds keys to {@code window.__koneko}.
     */
    public record Bootstrap(Context ctx, Map<String, Object> state, PluginUser user) {
    }

    /**
     * Published for every {@code /data/*} answer of the core site, with the
     * body that is about to be serialised. Writing to {@code body} adds fields
     * to the answer, which is how a plugin extends an existing page.
     *
     * @param key one of home, profile, scores, leaderboard, beatmaps, beatmapset
     */
    public record Data(Context ctx, String key, Map<String, Object> body) {
    }

    /** Published right before a page is rendered. */
    public record PageRender(Context ctx, String component) {
    }

    /** Published after a successful login. */
    public record Login(Context ctx, PluginUser user) {
    }

    /** Published after a logout. The user may be null if the session was gone. */
    public record Logout(Context ctx, PluginUser user) {
    }
}
