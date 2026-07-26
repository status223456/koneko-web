package com.osuserverlist.koneko.plugin.api;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.osuserverlist.koneko.api.ApiException;

import io.javalin.http.Context;

/**
 * The bancho.jar API, as a plugin sees it.
 *
 * <p>Anything the backend exposes is reachable from here, including endpoints
 * added to bancho.jar later: nothing in this interface enumerates routes. The
 * {@code AsUser} variants attach the access token of the browser behind the
 * request, which is how a plugin calls an authenticated endpoint without ever
 * seeing the token itself.
 */
public interface ApiClient {

    /** Base url of the API, without a trailing slash. */
    String baseUrl();

    JsonNode get(String path, Map<String, String> query) throws ApiException;

    JsonNode getAsUser(Context ctx, String path, Map<String, String> query) throws ApiException;

    JsonNode postForm(String path, Map<String, String> form) throws ApiException;

    JsonNode postFormAsUser(Context ctx, String path, Map<String, String> form) throws ApiException;

    JsonNode postJson(String path, Object body) throws ApiException;

    JsonNode postJsonAsUser(Context ctx, String path, Object body) throws ApiException;

    /**
     * The escape hatch: any method, any body, any content type.
     *
     * @param method      GET, POST, PUT, PATCH or DELETE
     * @param path        path on the API, starting with a slash
     * @param query       query parameters, may be null
     * @param body        request body, may be null
     * @param contentType content type of the body, may be null
     * @param accessToken bearer token to send, may be null
     */
    JsonNode request(String method, String path, Map<String, String> query,
            String body, String contentType, String accessToken) throws ApiException;

    /**
     * Like {@link #get}, but returns null instead of throwing, for the common
     * case of a secondary card that may simply stay empty.
     */
    JsonNode quietly(String path, Map<String, String> query);
}
