package com.osuserverlist.koneko.plugin.api;

import io.javalin.http.Handler;

/**
 * An HTTP endpoint contributed by a plugin.
 *
 * <p>Relative paths are mounted under {@code /plugins/{pluginId}}, so a plugin
 * can never take a path the core site needs, and its endpoints are recognisable
 * in the access log. {@link #asAbsolute()} lifts that, for a plugin that has to
 * own a specific url (a webhook, an OAuth callback, {@code /robots.txt}).
 *
 * @param method        GET, POST, PUT, PATCH or DELETE
 * @param path          path, relative unless {@code absolute} is set
 * @param handler       the Javalin handler doing the work
 * @param requiresLogin when true, anonymous callers get 401
 * @param minPrivileges privilege bits the player must have, or 403
 * @param absolute      when true, {@code path} is used as given
 */
public record PluginAction(String method, String path, Handler handler,
        boolean requiresLogin, int minPrivileges, boolean absolute) {

    public static PluginAction get(String path, Handler handler) {
        return new PluginAction("GET", path, handler, false, 0, false);
    }

    public static PluginAction post(String path, Handler handler) {
        return new PluginAction("POST", path, handler, false, 0, false);
    }

    public static PluginAction put(String path, Handler handler) {
        return new PluginAction("PUT", path, handler, false, 0, false);
    }

    public static PluginAction patch(String path, Handler handler) {
        return new PluginAction("PATCH", path, handler, false, 0, false);
    }

    public static PluginAction delete(String path, Handler handler) {
        return new PluginAction("DELETE", path, handler, false, 0, false);
    }

    /** Only for logged in players; everyone else gets 401. */
    public PluginAction withLogin() {
        return new PluginAction(method, path, handler, true, minPrivileges, absolute);
    }

    /** Requires these privilege bits, which implies a login. */
    public PluginAction withPrivileges(int mask) {
        return new PluginAction(method, path, handler, true, mask, absolute);
    }

    /** Mounts the path as given instead of under /plugins/{pluginId}. */
    public PluginAction asAbsolute() {
        return new PluginAction(method, path, handler, requiresLogin, minPrivileges, true);
    }
}
