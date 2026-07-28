package com.osuserverlist.koneko.routes;

import com.osuserverlist.koneko.auth.Auth;
import com.osuserverlist.koneko.auth.UserSession;
import com.osuserverlist.koneko.vue.KonekoVue;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Handler;

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
        config.routes.get("/leaderboard", KonekoVue.component("leaderboard-view"));
        config.routes.get("/beatmaps", KonekoVue.component("beatmaps-view"));
        config.routes.get("/beatmapsets/{setId}", KonekoVue.component("beatmapset-view"));
        // Not in the navigation: reached through the search box in the bar.
        config.routes.get("/players", KonekoVue.component("players-view"));
        config.routes.get("/scores/{scoreId}", KonekoVue.component("score-view"));

        // Only renders forms; what may actually be changed is decided by the
        // API when the form is submitted.
        config.routes.get("/settings", KonekoVue.component("settings-view"));

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

        // The staff panel. Which sections a page actually shows is decided by the
        // API; these routes only keep the pages away from players entirely.
        // Every panel address serves the same component, which reads the path and
        // renders the section that belongs to it. These routes still exist so a
        // typed or bookmarked address is checked for staff rights before anything
        // is served, and so an unknown /admin path is still a 404.
        Handler panel = staffPage("admin-panel-view");

        config.routes.get("/admin", panel);
        config.routes.get("/admin/requests", panel);
        config.routes.get("/admin/moderation", panel);
        config.routes.get("/admin/moderation/{userId}", panel);
        config.routes.get("/admin/logs", panel);
        config.routes.get("/admin/server", panel);

        // Unknown pages render inside the same shell instead of a bare Jetty
        // error page.
        config.routes.error(404, "html", KonekoVue.component("not-found-view", 404));
    }

    /**
     * A page only staff may open.
     *
     * <p>A visitor who is not logged in is sent to the login form, because that is probably what
     * they need. A logged in player is sent home instead of being told a staff panel exists.
     *
     * <p>This only decides whether the shell is rendered. The data behind it is fetched
     * separately and authorised again by the API, so a hand written URL gets an empty page at
     * worst, never someone else's data.
     */
    private static Handler staffPage(String component) {
        Handler page = KonekoVue.component(component);

        return ctx -> {
            UserSession session = Auth.current(ctx);

            if (session == null) {
                ctx.redirect("/login");
                return;
            }

            if (!AdminRoutes.isStaff(session)) {
                ctx.redirect("/");
                return;
            }

            page.handle(ctx);
        };
    }
}
