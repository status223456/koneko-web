package com.osuserverlist.koneko.routes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.api.ApiException;
import com.osuserverlist.koneko.auth.Auth;
import com.osuserverlist.koneko.auth.UserSession;
import com.osuserverlist.koneko.auth.Verification;
import com.osuserverlist.koneko.vue.VueState;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

/**
 * The per user side of the frontend: the page state and the session.
 *
 * <p>Public page data is not served here any more. The bancho.jar API answers
 * under {@code /api/v1} on this very origin, so the browser asks it directly
 * instead of having every list fetched twice: once by the browser from this
 * service, and once by this service from the API.
 *
 * <p>What is left is the state that depends on the session cookie, which only
 * this service can read.
 */
public final class DataRoutes {

    private static final Logger logger = LoggerFactory.getLogger("DataRoutes");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The player endpoints, which do not answer everybody the same.
     *
     * <p>Ordinary page data is fetched from the API by the browser directly, with no token
     * involved, because none of it depends on who is asking. A profile does: a restricted
     * account is a {@code 404} to players and an ordinary profile to staff, and the API can
     * only tell the two apart from an access token. That token lives in the session on this
     * side and must not reach the browser, so these calls come through here and the token is
     * added on the way out.
     *
     * <p>An allowlist rather than a prefix rewrite, for the same reason the panel uses one: a
     * proxy that forwards any path it is handed will eventually forward one nobody meant it to.
     */
    private static final Map<String, String> PLAYER_READS = Map.of(
            "get_player_details", "/api/v1/get_player_details",
            "get_player_scores", "/api/v1/get_player_scores",
            "get_player_first_places", "/api/v1/get_player_first_places",
            "get_player_most_played", "/api/v1/get_player_most_played",
            "get_player_playcounts", "/api/v1/get_player_playcounts",
            "get_player_rank_history", "/api/v1/get_player_rank_history",
            "get_player_achievements", "/api/v1/get_player_achievements",
            "get_player_beatmapsets", "/api/v1/get_player_beatmapsets");

    private DataRoutes() {
    }

    public static void register(JavalinConfig config) {
        config.routes.get("/data/player/{endpoint}", DataRoutes::player);

        config.routes.get("/data/bootstrap.js", DataRoutes::bootstrap);
        config.routes.get("/data/session", DataRoutes::session);

        // Never gated: this is how the verification page finds out that it is no longer
        // needed, and gating it would leave that page asking a question it cannot get an
        // answer to.
        config.routes.get("/data/verification", DataRoutes::verification);
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
     * One player endpoint, forwarded to the API with this session's token when there is one.
     *
     * <p>Open to visitors without a session as well: they simply get the public answer, which
     * is what the browser would have received asking the API itself. The point of the detour is
     * that staff get their own answer without the token ever being handed to a page.
     */
    private static void player(Context ctx) {
        String endpoint = ctx.pathParam("endpoint");
        String path = PLAYER_READS.get(endpoint);

        if (path == null) {
            ctx.status(404).json(Map.of("status", "Unknown player request."));
            return;
        }

        UserSession session = Auth.current(ctx);

        try {
            JsonNode body = session == null
                    ? App.api.get(path, query(ctx))
                    : App.api.getAuthed(path, query(ctx),
                            session.getTokens().getAccessToken());

            // The answer depends on the session, so it must never be kept by a shared cache.
            ctx.header("Cache-Control", "private, no-store");
            ctx.json(body);
        } catch (ApiException e) {
            int status = e.getStatus() < 400 ? 502 : e.getStatus();
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "The server could not be reached."
                    : e.getMessage();

            // The API's own status travels with it, so a hidden or missing player stays a 404
            // for the page instead of becoming a generic failure.
            ctx.status(status).header("Cache-Control", "private, no-store")
                    .json(Map.of("status", message));
        }
    }

    /** The browser's query string, flattened: the API takes no repeated parameters. */
    private static Map<String, String> query(Context ctx) {
        Map<String, String> params = new LinkedHashMap<>();

        ctx.queryParamMap().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                params.put(key, values.get(0));
            }
        });

        return params;
    }

    /**
     * Whether the account of this session has completed its in-game login yet.
     *
     * <p>Polled by the verification page while the player is logging into osu!, and answered
     * from the API rather than from anything remembered here, so the page turns itself into a
     * redirect within seconds of them connecting.
     */
    private static void verification(Context ctx) {
        UserSession session = Auth.current(ctx);

        boolean verified = session != null && Verification.verified(session);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loggedIn", session != null);
        body.put("verified", verified);

        if (session != null && !verified) {
            body.put("status", Verification.MESSAGE);
        }

        ctx.header("Cache-Control", "private, no-store");
        ctx.json(body);
    }
}
