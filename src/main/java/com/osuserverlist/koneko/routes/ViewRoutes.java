package com.osuserverlist.koneko.routes;

import com.osuserverlist.koneko.auth.Auth;
import com.osuserverlist.koneko.auth.UserSession;
import com.osuserverlist.koneko.vue.KonekoVue;

import io.javalin.config.JavalinConfig;

/**
 * The pages. Every one of them is a single Vue component, so the routing table
 * stays a table and all markup lives in the .vue files.
 */
public final class ViewRoutes {

    private ViewRoutes() {
    }

    public static void register(JavalinConfig config) {
        config.routes.get("/", KonekoVue.component("home-view"));
        config.routes.get("/login", KonekoVue.component("login-view"));
        config.routes.get("/u/{identifier}", KonekoVue.component("profile-view"));

        // Convenience route so the navigation bar can link to a profile
        // without knowing the id up front.
        config.routes.get("/me", ctx -> {
            UserSession session = Auth.current(ctx);

            if (session == null) {
                ctx.redirect("/login");
                return;
            }

            ctx.redirect("/u/" + session.getUserId());
        });

        // Unknown pages render inside the same shell instead of a bare Jetty
        // error page.
        config.routes.error(404, "html", KonekoVue.component("not-found-view", 404));
    }
}
