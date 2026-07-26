package com.osuserverlist.koneko.routes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.api.ApiException;
import com.osuserverlist.koneko.vue.VueState;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

/**
 * The read side of the frontend: everything the pages fetch.
 *
 * <p>All of it is a proxy in front of the bancho.jar API. Going through the
 * backend instead of calling the API from the browser keeps the whole thing
 * same-origin, so no CORS is involved and no token has to be exposed to
 * JavaScript.
 */
public final class DataRoutes {

    private static final Logger logger = LoggerFactory.getLogger("DataRoutes");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** How many best scores and sets a profile page shows at once. */
    private static final int PROFILE_PAGE_SIZE = 10;

    private DataRoutes() {
    }

    public static void register(JavalinConfig config) {
        config.routes.get("/data/bootstrap.js", DataRoutes::bootstrap);
        config.routes.get("/data/session", DataRoutes::session);
        config.routes.get("/data/home", DataRoutes::home);
        config.routes.get("/data/profile/{identifier}", DataRoutes::profile);
    }

    /**
     * Serves the site texts and the current user as a plain script, so the
     * shell of the page never has to wait for a fetch before it can render.
     */
    private static void bootstrap(Context ctx) {
        String json;

        try {
            json = MAPPER.writeValueAsString(VueState.of(ctx));
        } catch (Exception e) {
            logger.error("Could not serialise the page state", e);
            json = "{}";
        }

        ctx.contentType("application/javascript");
        // Per user content: never let a proxy hand one player's session to
        // another player.
        ctx.header("Cache-Control", "private, no-store");
        ctx.result("window.__koneko = " + json + ";");
    }

    private static void session(Context ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", VueState.user(ctx));

        ctx.json(body);
    }

    /** Front page: the counters and the top of the leaderboard. */
    private static void home(Context ctx) {
        Map<String, Object> body = new LinkedHashMap<>();

        if (App.site.getHome().isShowStats()) {
            body.put("stats", quietly("/api/v1/get_server_stats", Map.of()));
        }

        if (App.site.getHome().isShowLeaderboard()) {
            int size = Math.max(1, Math.min(100, App.site.getHome().getLeaderboardSize()));

            body.put("leaderboard", quietly("/api/v1/get_leaderboard", Map.of(
                    "mode", String.valueOf(App.site.getHome().getLeaderboardMode()),
                    "sort", "pp",
                    "limit", String.valueOf(size))));
        }

        ctx.json(body);
    }

    /**
     * Profile page. The id or the name is accepted in the same path segment,
     * so both /u/3 and /u/TestName work; a numeric segment is treated as an
     * id, everything else as a name.
     */
    private static void profile(Context ctx) {
        String identifier = ctx.pathParam("identifier");
        int mode = intQuery(ctx, "mode", 0);

        Map<String, String> who = who(identifier);

        JsonNode details;

        try {
            details = App.api.get("/api/v1/get_player_details", withAll(who, Map.of("scope", "all")));
        } catch (ApiException e) {
            ctx.status(e.getStatus() == 404 ? 404 : 502).json(Map.of("status", e.getMessage()));
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("player", details.path("player"));
        body.put("mode", mode);

        Map<String, String> paging = Map.of(
                "mode", String.valueOf(mode),
                "limit", String.valueOf(PROFILE_PAGE_SIZE));

        body.put("best", quietly("/api/v1/get_player_scores",
                withAll(who, withAll(paging, Map.of("scope", "best")))));

        body.put("recent", quietly("/api/v1/get_player_scores",
                withAll(who, withAll(paging, Map.of("scope", "recent")))));

        body.put("mostPlayed", quietly("/api/v1/get_player_most_played", withAll(who, paging)));

        // The route added to bancho.jar for locally submitted sets: this is
        // what makes a player's own beatmaps visible on the web.
        body.put("beatmapsets", quietly("/api/v1/get_player_beatmapsets",
                withAll(who, Map.of("limit", String.valueOf(PROFILE_PAGE_SIZE)))));

        ctx.json(body);
    }

    /**
     * Calls the API and returns null instead of failing when a secondary
     * section is unavailable: a missing score list should grey out one card,
     * not take the whole profile down.
     */
    private static JsonNode quietly(String path, Map<String, String> query) {
        try {
            return App.api.get(path, query);
        } catch (ApiException e) {
            logger.warn("<{}> failed with {}: {}", path, e.getStatus(), e.getMessage());
            return null;
        }
    }

    private static Map<String, String> who(String identifier) {
        if (identifier != null && identifier.chars().allMatch(Character::isDigit) && !identifier.isBlank()) {
            return Map.of("id", identifier);
        }

        return Map.of("name", identifier == null ? "" : identifier);
    }

    private static Map<String, String> withAll(Map<String, String> first, Map<String, String> second) {
        Map<String, String> merged = new LinkedHashMap<>(first);
        merged.putAll(second);
        return merged;
    }

    private static int intQuery(Context ctx, String name, int fallback) {
        String raw = ctx.queryParam(name);

        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
