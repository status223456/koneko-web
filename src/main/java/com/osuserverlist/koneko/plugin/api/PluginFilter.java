package com.osuserverlist.koneko.plugin.api;

import io.javalin.http.Handler;

/**
 * A handler that runs around other requests: rate limiting, extra headers,
 * analytics, a maintenance gate, a redirect.
 *
 * <p>{@code BEFORE} filters run before the matched route and may end the
 * request themselves (by writing a result or throwing a Javalin exception);
 * {@code AFTER} filters run once the route is done.
 *
 * @param stage       when it runs
 * @param pathPattern the paths it applies to, e.g. {@code /*} or {@code /u/*}
 * @param handler     the filter body
 */
public record PluginFilter(Stage stage, String pathPattern, Handler handler) {

    public enum Stage {
        BEFORE,
        AFTER
    }

    public static PluginFilter before(String pathPattern, Handler handler) {
        return new PluginFilter(Stage.BEFORE, pathPattern, handler);
    }

    public static PluginFilter after(String pathPattern, Handler handler) {
        return new PluginFilter(Stage.AFTER, pathPattern, handler);
    }
}
