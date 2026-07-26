package com.osuserverlist.koneko.vue;

import java.util.LinkedHashMap;
import java.util.Map;

import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.auth.Auth;
import com.osuserverlist.koneko.auth.UserSession;
import com.osuserverlist.koneko.config.SiteConfig;

import io.javalin.http.Context;

/**
 * Builds the object every page needs before it can render anything: the site
 * texts and who is looking at it.
 *
 * <p>It reaches the browser as a plain blocking script from
 * {@code /data/bootstrap.js}, so the data is on the page before Vue starts and
 * no component has to wait for a fetch to render the shell.
 */
public final class VueState {

    private VueState() {
    }

    /** The state of the current request, ready to be serialised to JSON. */
    public static Map<String, Object> of(Context ctx) {
        Map<String, Object> state = new LinkedHashMap<>();

        state.put("site", site());
        state.put("domain", App.env.getDomain());
        state.put("user", user(ctx));
        state.put("fastload", fastload());

        return state;
    }

    /** FastLoad settings the browser needs to know about. */
    private static Map<String, Object> fastload() {
        Map<String, Object> fastload = new LinkedHashMap<>();
        fastload.put("enabled", App.env.getFastLoadClientTtlSeconds() > 0);
        fastload.put("clientTtl", App.env.getFastLoadClientTtlSeconds());

        return fastload;
    }

    private static Map<String, Object> site() {
        SiteConfig config = App.site;

        Map<String, Object> site = new LinkedHashMap<>();
        site.put("name", config.getServer().getName());
        site.put("tagline", config.getServer().getTagline());
        site.put("description", config.getServer().getDescription());

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("discord", config.getLinks().getDiscord());
        links.put("github", config.getLinks().getGithub());
        links.put("apiDocs", config.getLinks().getApiDocs());
        site.put("links", links);

        Map<String, Object> home = new LinkedHashMap<>();
        home.put("showStats", config.getHome().isShowStats());
        home.put("showLeaderboard", config.getHome().isShowLeaderboard());
        home.put("leaderboardSize", config.getHome().getLeaderboardSize());
        home.put("leaderboardMode", config.getHome().getLeaderboardMode());
        home.put("showConnectGuide", config.getHome().isShowConnectGuide());
        home.put("heroImage", config.getHome().getHeroImage());
        home.put("logoImage", config.getHome().getLogoImage());
        home.put("announcement", config.getHome().getAnnouncement());
        home.put("announcementLink", config.getHome().getAnnouncementLink());
        site.put("home", home);

        return site;
    }

    /** The logged in player, or null. Never the tokens. */
    public static Map<String, Object> user(Context ctx) {
        UserSession session = Auth.current(ctx);

        if (session == null) {
            return null;
        }

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", session.getUserId());
        user.put("name", session.getUsername());
        user.put("priv", session.getPrivileges());

        return user;
    }
}
