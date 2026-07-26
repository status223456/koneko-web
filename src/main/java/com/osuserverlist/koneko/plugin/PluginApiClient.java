package com.osuserverlist.koneko.plugin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.api.ApiException;
import com.osuserverlist.koneko.auth.Auth;
import com.osuserverlist.koneko.auth.UserSession;
import com.osuserverlist.koneko.plugin.api.ApiClient;

import io.javalin.http.Context;

/**
 * The plugin facing view of {@link com.osuserverlist.koneko.api.BanchoApi}.
 *
 * <p>The point of going through here rather than handing plugins the client
 * itself is the {@code AsUser} family: the access token is looked up from the
 * session behind the request, so a plugin can call an authenticated backend
 * endpoint without ever holding a token, and a plugin cannot accidentally act
 * as another player.
 */
final class PluginApiClient implements ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Logger logger;

    PluginApiClient(String pluginId) {
        this.logger = LoggerFactory.getLogger("Plugin/" + pluginId);
    }

    @Override
    public String baseUrl() {
        return App.api.getBaseUrl();
    }

    @Override
    public JsonNode get(String path, Map<String, String> query) throws ApiException {
        return App.api.get(path, query == null ? Map.of() : query);
    }

    @Override
    public JsonNode getAsUser(Context ctx, String path, Map<String, String> query) throws ApiException {
        return App.api.getAuthed(path, query == null ? Map.of() : query, token(ctx));
    }

    @Override
    public JsonNode postForm(String path, Map<String, String> form) throws ApiException {
        return App.api.request("POST", path, null, formBody(form),
                "application/x-www-form-urlencoded", null);
    }

    @Override
    public JsonNode postFormAsUser(Context ctx, String path, Map<String, String> form) throws ApiException {
        return App.api.postFormAuthed(path, form == null ? Map.of() : form, token(ctx));
    }

    @Override
    public JsonNode postJson(String path, Object body) throws ApiException {
        return App.api.request("POST", path, null, json(body), "application/json", null);
    }

    @Override
    public JsonNode postJsonAsUser(Context ctx, String path, Object body) throws ApiException {
        return App.api.request("POST", path, null, json(body), "application/json", token(ctx));
    }

    @Override
    public JsonNode request(String method, String path, Map<String, String> query,
            String body, String contentType, String accessToken) throws ApiException {

        return App.api.request(method, path, query, body, contentType, accessToken);
    }

    @Override
    public JsonNode quietly(String path, Map<String, String> query) {
        try {
            return get(path, query);
        } catch (ApiException e) {
            logger.warn("<{}> failed with {}: {}", path, e.getStatus(), e.getMessage());
            return null;
        }
    }

    /** The access token of the browser behind the request. */
    private static String token(Context ctx) throws ApiException {
        UserSession session = ctx == null ? null : Auth.current(ctx);

        if (session == null) {
            throw new ApiException(401, "This action needs a logged in player.");
        }

        return session.getTokens().getAccessToken();
    }

    private static String json(Object body) throws ApiException {
        if (body == null) {
            return "";
        }

        if (body instanceof String text) {
            return text;
        }

        try {
            return MAPPER.writeValueAsString(body);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ApiException(500, "The request body could not be serialised.", e);
        }
    }

    private static String formBody(Map<String, String> form) {
        if (form == null || form.isEmpty()) {
            return "";
        }

        StringBuilder body = new StringBuilder();

        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }

            if (body.length() > 0) {
                body.append('&');
            }

            body.append(java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8));
            body.append('=');
            body.append(java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8));
        }

        return body.toString();
    }
}
