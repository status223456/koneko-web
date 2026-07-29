package com.osuserverlist.koneko.routes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.api.ApiException;
import com.osuserverlist.koneko.auth.Auth;
import com.osuserverlist.koneko.auth.UserSession;
import com.osuserverlist.koneko.auth.Verification;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

/**
 * The account of the logged in player: reading it, and changing it.
 *
 * <p>Public data is fetched by the browser straight from the API, but none of
 * this is public. The access token lives in the session here and never reaches
 * the browser, so every call that needs one has to go through this service.
 * These routes are that path and nothing more: the body is handed to the API
 * unchanged and its answer comes back unchanged.
 *
 * <p>The API asks for the current password again on the three dangerous
 * changes, so a stolen session cookie alone cannot take an account over.
 */
public final class AccountRoutes {

    private static final Logger logger = LoggerFactory.getLogger("AccountRoutes");

    /** What the browser may post, and where it goes on the API. */
    private static final Map<String, String> ACTIONS = Map.of(
            "update", "/api/v1/me/update",
            "email", "/api/v1/me/email",
            "password", "/api/v1/me/password",
            "delete", "/api/v1/me/delete");

    private AccountRoutes() {
    }

    public static void register(JavalinConfig config) {
        config.routes.get("/account/me", AccountRoutes::me);
        config.routes.post("/account/{action}", AccountRoutes::action);
    }

    /**
     * The own profile, private fields included: the email, the userpage and the
     * silence and supporter timestamps no one else may see.
     */
    private static void me(Context ctx) {
        UserSession session = Auth.current(ctx);

        if (session == null) {
            deny(ctx);
            return;
        }

        // An account waiting for its first in-game login has nothing to read or change here
        // yet, and the API would refuse the token anyway.
        if (Verification.blocksApi(ctx, session)) {
            return;
        }

        try {
            JsonNode body = App.api.getAuthed("/api/v1/me", Map.of(),
                    session.getTokens().getAccessToken());

            ctx.header("Cache-Control", "private, no-store");
            ctx.json(body);
        } catch (ApiException e) {
            fail(ctx, e, "me");
        }
    }

    private static void action(Context ctx) {
        String action = ctx.pathParam("action");
        String path = ACTIONS.get(action);

        if (path == null) {
            ctx.status(404).json(Map.of("status", "Unknown account action."));
            return;
        }

        UserSession session = Auth.current(ctx);

        if (session == null) {
            deny(ctx);
            return;
        }

        // An account waiting for its first in-game login has nothing to read or change here
        // yet, and the API would refuse the token anyway.
        if (Verification.blocksApi(ctx, session)) {
            return;
        }

        boolean endsSession = "password".equals(action) || "delete".equals(action);

        try {
            JsonNode body = App.api.request("POST", path, null, ctx.body(),
                    "application/json", session.getTokens().getAccessToken());

            // A new password revokes every other session of the account on the
            // API side, and a deleted account has none left at all. Either way
            // the tokens behind this browser are done, so the session goes with
            // them rather than lingering as a login that can no longer call
            // anything.
            if (endsSession) {
                Auth.destroy(ctx);
            }

            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("status", "success");
            answer.put("body", body);
            answer.put("loggedOut", endsSession);

            ctx.json(answer);
        } catch (ApiException e) {
            fail(ctx, e, action);
        }
    }

    private static void deny(Context ctx) {
        ctx.status(401).json(Map.of("status", "Log in to change your account."));
    }

    /**
     * Hands the API's own status and message on. A wrong password is the
     * player's business and not worth a log line; anything unexpected is.
     */
    private static void fail(Context ctx, ApiException e, String action) {
        if (e.getStatus() != 400 && e.getStatus() != 401 && e.getStatus() != 403) {
            logger.warn("Account action <{}> failed with {}: {}",
                    action, e.getStatus(), e.getMessage());
        }

        ctx.status(e.getStatus()).json(Map.of("status", e.getMessage()));
    }
}
