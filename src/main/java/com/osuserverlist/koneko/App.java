package com.osuserverlist.koneko;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.osuserverlist.koneko.api.BanchoApi;
import com.osuserverlist.koneko.api.FastCache;
import com.osuserverlist.koneko.auth.SessionStore;
import com.osuserverlist.koneko.config.Env;
import com.osuserverlist.koneko.config.SiteConfig;
import com.osuserverlist.koneko.config.SiteConfigLoader;
import com.osuserverlist.koneko.routes.AuthRoutes;
import com.osuserverlist.koneko.routes.DataRoutes;
import com.osuserverlist.koneko.routes.ViewRoutes;
import com.osuserverlist.koneko.vue.KonekoVue;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

/**
 * Entry point of koneko-web, the web frontend of a bancho.jar server.
 *
 * <p>There is no database here. Every page is one Vue component rendered by
 * {@link KonekoVue} from the files in {@code src/main/resources/vue}, and every
 * piece of data comes from the bancho.jar public API through {@link BanchoApi}.
 */
public final class App {

    private static final Logger logger = LoggerFactory.getLogger("Koneko");

    /** Deployment settings, from {@code .env}. */
    public static Env env;

    /** Site texts, from {@code config.yml}. */
    public static SiteConfig site;

    /** Client for the bancho.jar public API. */
    public static BanchoApi api;

    private App() {
    }

    public static void main(String[] args) {
        env = Env.load();
        site = SiteConfigLoader.load(env.getConfigPath());
        api = new BanchoApi(env);

        SessionStore.configure(env.getSessionTimeoutMinutes());
        FastCache.configure(env.getFastLoadTtlSeconds(), env.getFastLoadStaleSeconds());
        KonekoVue.configure(env.isDev());

        logger.info("Starting koneko-web for <{}> at <{}>", site.getServer().getName(), env.getDomain());
        logger.info("Using the bancho.jar API at <{}>", api.getBaseUrl());

        if (FastCache.enabled()) {
            logger.info("FastLoad is on: {}s fresh, {}s stale, {}s in the browser",
                    env.getFastLoadTtlSeconds(), env.getFastLoadStaleSeconds(),
                    env.getFastLoadClientTtlSeconds());
        }

        if (env.isDev()) {
            logger.warn("Running in DEV mode: vue files are re-read per request and cookies are not Secure");
        }

        // Javalin 7 registers everything through the config object, so all the
        // routes are mounted inside create().
        Javalin app = Javalin.create(config -> {
            // /public is served from the classpath, so css and images end up
            // inside the shaded jar.
            config.staticFiles.add("/public", Location.CLASSPATH);

            config.routes.exception(Exception.class, (e, ctx) -> {
                logger.error("Unhandled exception while processing {} {}", ctx.method(), ctx.path(), e);

                ctx.status(500).result("Internal Server Error");
            });

            ViewRoutes.register(config);
            AuthRoutes.register(config);
            DataRoutes.register(config);

            if (env.isDev()) {
                config.bundledPlugins.enableDevLogging();
            }
        });

        app.start(env.getPort());

        logger.info("koneko-web is listening on port <{}>", env.getPort());
    }
}
