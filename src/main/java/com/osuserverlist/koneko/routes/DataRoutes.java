package com.osuserverlist.koneko.routes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private DataRoutes() {
    }

    public static void register(JavalinConfig config) {
        config.routes.get("/data/bootstrap.js", DataRoutes::bootstrap);
        config.routes.get("/data/session", DataRoutes::session);
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
}
