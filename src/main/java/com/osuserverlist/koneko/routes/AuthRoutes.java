package com.osuserverlist.koneko.routes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.api.ApiException;
import com.osuserverlist.koneko.api.TokenPair;
import com.osuserverlist.koneko.auth.Auth;
import com.osuserverlist.koneko.auth.UserSession;
import com.osuserverlist.koneko.config.Env;
import com.osuserverlist.koneko.plugin.PluginService;
import com.osuserverlist.koneko.plugin.api.Events;
import com.osuserverlist.koneko.plugin.api.PluginUser;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

/**
 * Login and logout.
 *
 * <p>These two routes are the only place where a password is handled, and it
 * is never stored: it goes straight into the password grant of the bancho.jar
 * API and is forgotten once the token pair comes back.
 */
public final class AuthRoutes {

    private static final Logger logger = LoggerFactory.getLogger("AuthRoutes");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuthRoutes() {
    }

    public static void register(JavalinConfig config) {
        config.routes.post("/auth/login", AuthRoutes::login);
        config.routes.post("/auth/logout", AuthRoutes::logout);
    }

    private static void login(Context ctx) {
        String username = field(ctx, "username");
        String password = field(ctx, "password");

        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            ctx.status(400).json(Map.of("status", "Both a username and a password are required."));
            return;
        }

        // A second login in the same browser replaces the first one instead of
        // leaving an orphaned session with live tokens behind.
        if (Auth.current(ctx) != null) {
            Auth.destroy(ctx);
        }

        try {
            TokenPair tokens = App.api.passwordGrant(username.trim(), password, Env.DEFAULT_API_SCOPES);
            JsonNode info = App.api.userInfo(tokens.getAccessToken());
            JsonNode user = info.path("user");

            UserSession session = Auth.establish(ctx,
                    user.path("id").asInt(),
                    user.path("name").asText(username.trim()),
                    user.path("priv").asInt(),
                    tokens);

            // Plugins see the login with the token, so one can call an
            // authenticated backend endpoint on the player's behalf right away.
            PluginService.events().publish(new Events.Login(ctx,
                    new PluginUser(session.getUserId(), session.getUsername(),
                            session.getPrivileges(), tokens.getAccessToken())));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "success");
            body.put("user", Map.of(
                    "id", session.getUserId(),
                    "name", session.getUsername(),
                    "priv", session.getPrivileges()));

            ctx.json(body);
        } catch (ApiException e) {
            // 401 from the grant means bad credentials; anything else is our
            // problem, not the player's, so it is worth a log line.
            if (e.getStatus() != 401) {
                logger.warn("Login of <{}> failed with {}: {}", username, e.getStatus(), e.getMessage());
            }

            int status = e.getStatus() == 401 ? 401 : e.getStatus();
            ctx.status(status).json(Map.of("status", e.getMessage()));
        }
    }

    private static void logout(Context ctx) {
        // Read before the session is gone, so plugins learn who left.
        UserSession session = Auth.current(ctx);

        Auth.destroy(ctx);

        PluginUser user = session == null ? null : new PluginUser(session.getUserId(),
                session.getUsername(), session.getPrivileges(), null);

        PluginService.events().publish(new Events.Logout(ctx, user));

        ctx.json(Map.of("status", "success"));
    }

    /**
     * Reads a field from a JSON body, falling back to a form post, so the
     * login form keeps working even without JavaScript.
     */
    private static String field(Context ctx, String name) {
        String form = ctx.formParam(name);

        if (form != null) {
            return form;
        }

        String body = ctx.body();

        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            JsonNode node = MAPPER.readTree(body).path(name);
            return node.isMissingNode() || node.isNull() ? null : node.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
