package com.osuserverlist.koneko.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.api.ApiException;
import com.osuserverlist.koneko.api.TokenPair;

import io.javalin.http.Context;

/**
 * Everything the routes need to know about who is logged in.
 *
 * <p>The cookie is written by hand instead of through the Javalin cookie
 * helper, because the flags matter here: HttpOnly keeps scripts away from it,
 * SameSite=Lax stops a foreign site from posting with it, and Secure is
 * dropped in DEV so plain http on localhost still works.
 */
public final class Auth {

    private static final Logger logger = LoggerFactory.getLogger("Auth");

    /** Name of the session cookie handed to the browser. */
    public static final String COOKIE = "koneko_session";

    private Auth() {
    }

    /**
     * Resolves the session behind the request, refreshing the access token
     * when it is about to expire.
     *
     * @return the session, or null when nobody is logged in
     */
    public static UserSession current(Context ctx) {
        UserSession session = SessionStore.get(ctx.cookie(COOKIE));

        if (session == null) {
            return null;
        }

        if (!session.getTokens().needsRefresh()) {
            return session;
        }

        try {
            TokenPair refreshed = App.api.refresh(session.getTokens().getRefreshToken());
            session.setTokens(refreshed);
            return session;
        } catch (ApiException e) {
            // The chain is gone (expired, revoked, or replayed): the only
            // correct answer is to log the browser out rather than keep a
            // session around that can no longer call anything.
            logger.info("Dropping the session of <{}>: {}", session.getUsername(), e.getMessage());
            destroy(ctx);
            return null;
        }
    }

    /** Creates a session for a freshly issued token pair and sets the cookie. */
    public static UserSession establish(Context ctx, int userId, String username, int privileges, TokenPair tokens) {
        UserSession session = new UserSession(userId, username, privileges, tokens);
        String id = SessionStore.create(session);

        ctx.res().addHeader("Set-Cookie", cookie(id, App.env.getSessionTimeoutMinutes() * 60));

        logger.info("<{}>({}) logged in", username, userId);

        return session;
    }

    /** Revokes the tokens, forgets the session and clears the cookie. */
    public static void destroy(Context ctx) {
        UserSession session = SessionStore.remove(ctx.cookie(COOKIE));

        if (session != null) {
            App.api.revokeRefreshToken(session.getTokens().getRefreshToken());
            logger.info("<{}>({}) logged out", session.getUsername(), session.getUserId());
        }

        ctx.res().addHeader("Set-Cookie", cookie("", 0));
    }

    private static String cookie(String value, int maxAgeSeconds) {
        StringBuilder cookie = new StringBuilder();

        cookie.append(COOKIE).append("=").append(value);
        cookie.append("; Path=/");
        cookie.append("; Max-Age=").append(maxAgeSeconds);
        cookie.append("; HttpOnly");
        cookie.append("; SameSite=Lax");

        if (App.env.useSecureCookies()) {
            cookie.append("; Secure");
        }

        return cookie.toString();
    }
}
