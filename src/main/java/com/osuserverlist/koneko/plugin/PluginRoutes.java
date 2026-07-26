package com.osuserverlist.koneko.plugin;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.plugin.api.PluginFilter;
import com.osuserverlist.koneko.vue.KonekoVue;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.Handler;

/**
 * Mounts everything the plugins contributed onto the Javalin config.
 *
 * <p>Called from inside {@code Javalin.create(...)}, after the core routes, so
 * the core site always wins a conflict. Three families of routes are added:
 *
 * <ul>
 *   <li>the plugin pages, at the absolute paths they asked for;</li>
 *   <li>the plugin actions, under {@code /plugins/{id}};</li>
 *   <li>the plugin assets, under {@code /plugin-assets/{id}};</li>
 * </ul>
 *
 * <p>plus {@code /data/plugins}, which tells the browser what is loaded.
 */
public final class PluginRoutes {

    private static final Logger logger = LoggerFactory.getLogger("Plugins");

    private PluginRoutes() {
    }

    public static void register(JavalinConfig config) {
        // The listing exists even with no plugins at all, so the front end can
        // always ask without special casing.
        config.routes.get("/data/plugins", PluginRoutes::listing);

        if (!PluginService.enabled()) {
            return;
        }

        Contributions contributions = PluginService.contributions();

        pages(config, contributions);
        actions(config, contributions);
        assets(config, contributions);
        filters(config, contributions);

        // The page shell needs the plugin components, the head and the body
        // injections; the hook lets plugins see every render.
        KonekoVue.setPluginSources(PluginVue::components, PluginVue::head, PluginVue::bodyEnd);
        KonekoVue.setRenderHook((ctx, component) -> PluginService.events()
                .publish(new com.osuserverlist.koneko.plugin.api.Events.PageRender(ctx, component)));
    }

    // ------------------------------------------------------------------
    // pages
    // ------------------------------------------------------------------

    private static void pages(JavalinConfig config, Contributions contributions) {
        for (Contributions.Page entry : contributions.pages) {
            String path = entry.page().path();

            Handler render = KonekoVue.component(entry.page().component(), entry.page().status());

            config.routes.get(path, ctx -> {
                if (!PluginGuard.allowPage(ctx, entry.page().requiresLogin(),
                        entry.page().minPrivileges())) {
                    return;
                }

                render.handle(ctx);
            });

            logger.info("<{}> serves the page <{}> with <{}>",
                    entry.pluginId(), path, entry.page().component());
        }
    }

    // ------------------------------------------------------------------
    // actions
    // ------------------------------------------------------------------

    private static void actions(JavalinConfig config, Contributions contributions) {
        for (Contributions.Action entry : contributions.actions) {
            String path = entry.mountedPath();

            Handler guarded = ctx -> {
                if (!PluginGuard.allowAction(ctx, entry.action().requiresLogin(),
                        entry.action().minPrivileges())) {
                    return;
                }

                entry.action().handler().handle(ctx);
            };

            String method = entry.action().method() == null
                    ? "GET"
                    : entry.action().method().trim().toUpperCase(Locale.ROOT);

            switch (method) {
                case "GET" -> config.routes.get(path, guarded);
                case "POST" -> config.routes.post(path, guarded);
                case "PUT" -> config.routes.put(path, guarded);
                case "PATCH" -> config.routes.patch(path, guarded);
                case "DELETE" -> config.routes.delete(path, guarded);
                default -> {
                    logger.warn("<{}> asked for the unsupported method <{}> on <{}>",
                            entry.pluginId(), method, path);
                    continue;
                }
            }

            logger.info("<{}> serves {} <{}>", entry.pluginId(), method, path);
        }
    }

    // ------------------------------------------------------------------
    // assets
    // ------------------------------------------------------------------

    private static void assets(JavalinConfig config, Contributions contributions) {
        if (contributions.assets.isEmpty() && contributions.assetRoots.isEmpty()) {
            return;
        }

        // One wildcard route for every plugin asset: a plugin cannot register a
        // Javalin static file handler of its own, because the config is closed
        // by the time it could.
        config.routes.get("/plugin-assets/{pluginId}/<path>", PluginRoutes::asset);
    }

    private static void asset(Context ctx) throws IOException {
        String pluginId = ctx.pathParam("pluginId");
        String path = ctx.pathParam("path");

        if (!PluginResources.isSafePath(path)) {
            ctx.status(400).result("Bad asset path");
            return;
        }

        Contributions contributions = PluginService.contributions();

        // A file registered one by one may override a directory entry, and may
        // carry its own content type.
        for (Contributions.Asset entry : contributions.assets) {
            if (!entry.pluginId().equals(pluginId)) {
                continue;
            }

            if (!entry.urlPath().equals("/" + path)) {
                continue;
            }

            byte[] bytes = PluginResources.readBytes(entry.loader(), entry.asset().resource());

            if (bytes == null) {
                break;
            }

            send(ctx, bytes, entry.asset().contentType() == null
                    ? PluginResources.contentTypeOf(path)
                    : entry.asset().contentType());
            return;
        }

        for (Contributions.AssetRoot root : contributions.assetRoots) {
            if (!root.pluginId().equals(pluginId)) {
                continue;
            }

            String resource = root.resourceRoot().endsWith("/")
                    ? root.resourceRoot() + path
                    : root.resourceRoot() + "/" + path;

            byte[] bytes = PluginResources.readBytes(root.loader(), resource);

            if (bytes != null) {
                send(ctx, bytes, PluginResources.contentTypeOf(path));
                return;
            }
        }

        ctx.status(404).result("Not found");
    }

    private static void send(Context ctx, byte[] bytes, String contentType) {
        ctx.contentType(contentType);

        // Plugin assets change with the jar, not with the request, so they may
        // be cached like the core css. In DEV nothing is cached, to keep an
        // edit-reload cycle honest.
        if (App.env.isDev()) {
            ctx.header("Cache-Control", "no-store");
        } else {
            ctx.header("Cache-Control", "public, max-age=" + App.env.getPluginAssetTtlSeconds());
        }

        ctx.result(bytes);
    }

    // ------------------------------------------------------------------
    // filters
    // ------------------------------------------------------------------

    /**
     * Registers before/after filters.
     *
     * <p>Done reflectively on purpose: {@code config.routes} grew these two
     * methods in Javalin 7 and their exact shape is the one thing here that a
     * minor version could move. A missing method costs the filters a warning
     * instead of costing the whole site its boot.
     */
    private static void filters(JavalinConfig config, Contributions contributions) {
        if (contributions.filters.isEmpty()) {
            return;
        }

        for (Contributions.Filter entry : contributions.filters) {
            String name = entry.filter().stage() == PluginFilter.Stage.BEFORE ? "before" : "after";
            String pattern = entry.filter().pathPattern() == null || entry.filter().pathPattern().isBlank()
                    ? "/*"
                    : entry.filter().pathPattern();

            if (!invoke(config, name, pattern, entry.filter().handler())) {
                logger.warn("<{}> asked for a {} filter on <{}>, which this Javalin does not support",
                        entry.pluginId(), name, pattern);
            }
        }
    }

    private static boolean invoke(JavalinConfig config, String name, String pattern, Handler handler) {
        Object routes = config.routes;

        for (Method method : routes.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 2) {
                continue;
            }

            Class<?>[] types = method.getParameterTypes();

            if (!types[0].isAssignableFrom(String.class) || !types[1].isAssignableFrom(Handler.class)) {
                continue;
            }

            try {
                method.invoke(routes, pattern, handler);
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                logger.warn("Could not register a {} filter: {}", name, e.getMessage());
                return false;
            }
        }

        return false;
    }

    // ------------------------------------------------------------------
    // listing
    // ------------------------------------------------------------------

    private static void listing(Context ctx) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("enabled", PluginService.enabled());
        body.put("plugins", PluginService.describe());

        ctx.header("Cache-Control", "no-store");
        ctx.json(body);
    }
}
