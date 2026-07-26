package com.osuserverlist.koneko.routes;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.api.ApiException;
import com.osuserverlist.koneko.api.FastCache;
import com.osuserverlist.koneko.plugin.PluginBootstrap;
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

    /** How many rows one page of the ranking holds. */
    private static final int LEADERBOARD_PAGE_SIZE = 50;

    /** Sort orders the ranking accepts, anything else falls back to pp. */
    private static final Set<String> LEADERBOARD_SORTS = Set.of("pp", "score", "acc", "plays");

    /** How many months the playcount graph covers. */
    private static final int PLAYCOUNT_MONTHS = 12;

    /** How many beatmap sets one page of the listing holds. */
    private static final int BEATMAPS_PAGE_SIZE = 24;

    private DataRoutes() {
    }

    public static void register(JavalinConfig config) {
        config.routes.get("/data/bootstrap.js", DataRoutes::bootstrap);
        config.routes.get("/data/session", DataRoutes::session);
        config.routes.get("/data/home", DataRoutes::home);
        config.routes.get("/data/profile/{identifier}", DataRoutes::profile);
        config.routes.get("/data/scores/{identifier}", DataRoutes::scores);
        config.routes.get("/data/leaderboard", DataRoutes::leaderboard);
        config.routes.get("/data/beatmaps", DataRoutes::beatmaps);
        config.routes.get("/data/beatmapset/{setId}", DataRoutes::beatmapset);
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

    /**
     * Front page: the counters and the top of the leaderboard.
     *
     * <p>Nothing here depends on who is asking, so it goes through
     * {@link FastCache}: the usual answer is served from memory and the API
     * is only called when the window has passed.
     */
    private static void home(Context ctx) {
        fastHeaders(ctx);
        // Every answer passes through the plugins, which may add fields to it.
        // The cached body is copied first, so nothing a plugin writes ends up
        // in the cache.
        ctx.json(PluginBootstrap.enrich(ctx, "home", FastCache.get("home", DataRoutes::homeBody)));
    }

    private static Map<String, Object> homeBody() {
        Map<String, Object> body = new LinkedHashMap<>();

        if (App.site.getHome().isShowStats()) {
            body.put("stats", counters(quietly("/api/v1/get_server_stats", Map.of())));
        }

        // The ranking lives on its own page now, so the front page only
        // carries the counters.
        return body;
    }

    /**
     * Profile page. The id or the name is accepted in the same path segment,
     * so both /u/3 and /u/TestName work; a numeric segment is treated as an
     * id, everything else as a name.
     */
    private static void profile(Context ctx) {
        String identifier = ctx.pathParam("identifier");
        int mode = intQuery(ctx, "mode", 0);

        // A profile is public, so the same page may be served from the
        // cache to everyone. The key has to carry the mode as well.
        String key = "profile:" + mode + ":" + identifier.toLowerCase(Locale.ROOT);

        Map<String, Object> body;

        try {
            body = FastCache.get(key, () -> profileBody(identifier, mode));
        } catch (ApiFailure e) {
            ctx.status(e.status == 404 ? 404 : 502).json(Map.of("status", e.getMessage()));
            return;
        }

        fastHeaders(ctx);
        ctx.json(PluginBootstrap.enrich(ctx, "profile", body));
    }

    private static Map<String, Object> profileBody(String identifier, int mode) {
        Map<String, String> who = who(identifier);

        JsonNode details;

        try {
            details = App.api.get("/api/v1/get_player_details", withAll(who, Map.of("scope", "all")));
        } catch (ApiException e) {
            // Thrown out of the cache loader, so a failure is never stored.
            throw new ApiFailure(e);
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

        body.put("firstPlaces", quietly("/api/v1/get_player_first_places", withAll(who, paging)));

        body.put("playcounts", quietly("/api/v1/get_player_playcounts", withAll(who, Map.of(
                "mode", String.valueOf(mode),
                "months", String.valueOf(PLAYCOUNT_MONTHS)))));

        body.put("mostPlayed", quietly("/api/v1/get_player_most_played", withAll(who, paging)));

        // The route added to bancho.jar for locally submitted sets: this is
        // what makes a player's own beatmaps visible on the web.
        body.put("beatmapsets", quietly("/api/v1/get_player_beatmapsets",
                withAll(who, Map.of("limit", String.valueOf(PROFILE_PAGE_SIZE)))));

        // Medals are not per mode, so this is fetched without one. It rides
        // along with the profile instead of being its own request: the whole
        // catalogue is under a hundred rows.
        body.put("achievements", quietly("/api/v1/get_player_achievements", who));

        return body;
    }

    /**
     * Paging for the score lists of a profile. The profile itself ships the
     * first page of every list; this is what "load more" asks for.
     *
     * <p>Scope is "best", "recent" or "first", the last one being the number
     * one scores, which live on their own endpoint.
     */
    private static void scores(Context ctx) {
        String identifier = ctx.pathParam("identifier");
        int mode = intQuery(ctx, "mode", 0);
        int offset = Math.max(0, intQuery(ctx, "offset", 0));

        String scope = trimmed(ctx.queryParam("scope"));

        if (scope.isEmpty()) {
            scope = "recent";
        }

        boolean firstPlaces = "first".equals(scope);

        Map<String, String> params = new LinkedHashMap<>(who(identifier));
        params.put("mode", String.valueOf(mode));
        params.put("offset", String.valueOf(offset));
        params.put("limit", String.valueOf(PROFILE_PAGE_SIZE));

        if (!firstPlaces) {
            params.put("scope", scope);
        }

        String path = firstPlaces
                ? "/api/v1/get_player_first_places"
                : "/api/v1/get_player_scores";

        String key = "scores:" + identifier.toLowerCase(Locale.ROOT)
                + ":" + scope + ":" + mode + ":" + offset;

        Map<String, Object> body = FastCache.get(key, () -> {
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("scores", quietly(path, params));

            return page;
        });

        fastHeaders(ctx);
        ctx.json(PluginBootstrap.enrich(ctx, "scores", body));
    }

    /**
     * The beatmap listing. Search text and filters are passed straight to
     * the API, which does the grouping by set.
     */
    private static void beatmaps(Context ctx) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("limit", String.valueOf(BEATMAPS_PAGE_SIZE));
        params.put("offset", String.valueOf(Math.max(0, intQuery(ctx, "offset", 0))));

        for (String name : new String[] { "q", "status", "mode", "server", "sort" }) {
            String value = trimmed(ctx.queryParam(name));

            if (!value.isEmpty()) {
                params.put(name, value);
            }
        }

        // The same search means the same page for everyone, so the whole
        // parameter set is the cache key.
        Map<String, Object> body = FastCache.get("beatmaps:" + params, () -> {
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("beatmapsets", quietly("/api/v1/search_beatmapsets", params));

            return page;
        });

        fastHeaders(ctx);
        ctx.json(PluginBootstrap.enrich(ctx, "beatmaps", body));
    }

    /** One beatmap set with all of its difficulties. */
    private static void beatmapset(Context ctx) {
        String setId = ctx.pathParam("setId");

        Map<String, Object> body = FastCache.get("beatmapset:" + setId, () -> {
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("beatmapset", quietly("/api/v1/get_beatmapset", Map.of("id", setId)));

            return page;
        });

        fastHeaders(ctx);
        ctx.json(PluginBootstrap.enrich(ctx, "beatmapset", body));
    }

    /**
     * The ranking page. Mode, country and sort order all come from the
     * query string, so any filtered ranking is a link that can be shared.
     *
     * <p>The list of countries is handed out together with the rows: it is
     * built from the players that actually exist, so the filter never
     * offers a country with nobody in it.</p>
     */
    private static void leaderboard(Context ctx) {
        int mode = intQuery(ctx, "mode", App.site.getHome().getLeaderboardMode());
        int offset = Math.max(0, intQuery(ctx, "offset", 0));

        String sort = trimmed(ctx.queryParam("sort")).toLowerCase(Locale.ROOT);
        String country = trimmed(ctx.queryParam("country")).toLowerCase(Locale.ROOT);

        if (!LEADERBOARD_SORTS.contains(sort)) {
            sort = "pp";
        }

        // Anything that is not a plain two letter code means no filter.
        if (!country.matches("[a-z]{2}")) {
            country = "";
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("mode", String.valueOf(mode));
        params.put("sort", sort);
        params.put("offset", String.valueOf(offset));
        params.put("limit", String.valueOf(LEADERBOARD_PAGE_SIZE));

        if (!country.isEmpty()) {
            params.put("country", country);
        }

        String key = "leaderboard:" + mode + ":" + sort + ":" + country + ":" + offset;

        Map<String, Object> body = FastCache.get(key, () -> {
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("leaderboard", quietly("/api/v1/get_leaderboard", params));
            page.put("countries", quietly("/api/v1/get_countries",
                    Map.of("mode", String.valueOf(mode))));

            return page;
        });

        fastHeaders(ctx);
        ctx.json(PluginBootstrap.enrich(ctx, "leaderboard", body));
    }

    /**
     * Tells the browser how long the answer may be reused. With FastLoad
     * off this stays a plain no-store, so nothing is cached anywhere.
     */
    private static void fastHeaders(Context ctx) {
        if (FastCache.enabled()) {
            ctx.header("Cache-Control", "public, max-age=" + FastCache.freshSeconds());
        } else {
            ctx.header("Cache-Control", "no-store");
        }
    }

    /** An API failure carried out of a cache loader. */
    private static final class ApiFailure extends RuntimeException {

        private final int status;

        private ApiFailure(ApiException cause) {
            super(cause.getMessage(), cause);
            this.status = cause.getStatus();
        }
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

    /**
     * Normalises the counters of the stats endpoint into the names the front
     * page uses, so a field rename on the backend cannot silently blank a
     * card. Both the camelCase and the snake_case spelling are accepted.
     */
    private static Map<String, Object> counters(JsonNode stats) {
        if (stats == null) {
            return null;
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("online", number(stats, "onlinePlayers", "online_players", "online"));
        counts.put("players", number(stats, "totalPlayers", "total_players", "players"));
        counts.put("beatmaps", number(stats, "maps", "beatmaps", "totalMaps"));
        counts.put("scores", number(stats, "scores", "totalScores"));
        return counts;
    }

    private static Long number(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);

            if (value != null && value.isNumber()) {
                return value.asLong();
            }
        }

        return null;
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

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
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
