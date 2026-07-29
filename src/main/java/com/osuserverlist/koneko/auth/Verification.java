package com.osuserverlist.koneko.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.osuserverlist.koneko.App;
import com.osuserverlist.koneko.api.ApiException;

import io.javalin.http.Context;

/**
 * The gate between registering on the website and being an actual player.
 *
 * <p>An account made here has never proved that whoever made it can log into the game with it.
 * Until it has, it is treated as a placeholder: every page redirects to {@code /verify} and
 * every call that would need the account's token is answered {@code 401}. The proof is the
 * in-game login itself, which is where bancho.jar sets the VERIFIED bit, so nothing has to be
 * clicked, mailed or approved by hand.
 *
 * <p>The bit is read from the API rather than remembered here. While a session is unverified
 * the API is asked again every few seconds, which means the restrictions fall away on the next
 * page load after the player connects, without them logging out and in again.
 */
public final class Verification {

    private static final Logger logger = LoggerFactory.getLogger("Verification");

    /** The page an unverified player is sent to, and the one page they may open. */
    public static final String PATH = "/verify";

    /** What an unverified player is told, on the page and in every refused answer. */
    public static final String MESSAGE = "Log into osu! once with this account to verify it.";

    /**
     * How long a "still not verified" answer is trusted for.
     *
     * <p>Short, because the player is most likely staring at the verification page waiting for
     * it to change, and long enough that a page of parallel requests asks the API once.
     */
    private static final Duration RECHECK_AFTER = Duration.ofSeconds(3);

    private Verification() {
    }

    /**
     * Whether this session may use the site, re-asking the API when the answer might have
     * changed.
     *
     * <p>A session that is not logged in is not unverified: it is simply anonymous, and the
     * public part of the site is open to it. Callers decide what that means for them.
     */
    public static boolean verified(UserSession session) {
        if (session == null) {
            return true;
        }

        if (session.isVerified()) {
            return true;
        }

        if (Duration.between(session.getLastVerificationCheck(), Instant.now())
                .compareTo(RECHECK_AFTER) < 0) {
            return false;
        }

        session.setLastVerificationCheck(Instant.now());

        refresh(session);

        return session.isVerified();
    }

    /**
     * Re-reads the account's privileges from the API.
     *
     * <p>Userinfo is the one authenticated endpoint that still answers an unverified token, for
     * exactly this reason. A failure is not fatal: the session stays unverified and the next
     * request tries again.
     */
    private static void refresh(UserSession session) {
        try {
            JsonNode body = App.api.getAuthed("/api/v1/oauth/userinfo", Map.of(),
                    session.getTokens().getAccessToken());

            JsonNode user = body.path("user");

            if (user.hasNonNull("priv")) {
                session.setPrivileges(user.get("priv").asInt());
            }

            if (session.isVerified()) {
                logger.info("<{}>({}) verified themselves in game",
                        session.getUsername(), session.getUserId());
            }
        } catch (ApiException e) {
            logger.debug("Could not re-check the verification of <{}>: {}",
                    session.getUsername(), e.getMessage());
        }
    }

    /**
     * Guard for a page: sends an unverified player to the verification page.
     *
     * @return true when the request has been answered and the handler must stop
     */
    public static boolean blocksPage(Context ctx) {
        UserSession session = Auth.current(ctx);

        if (verified(session)) {
            return false;
        }

        ctx.redirect(PATH);

        return true;
    }

    /**
     * Guard for anything a script calls: refuses an unverified player outright.
     *
     * <p>{@code 401} rather than {@code 403}, to say the same thing the API says about the
     * token behind this session: it is not usable yet.
     *
     * @return true when the request has been answered and the handler must stop
     */
    public static boolean blocksApi(Context ctx, UserSession session) {
        if (verified(session)) {
            return false;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", MESSAGE);
        body.put("verified", false);

        ctx.status(401).header("Cache-Control", "private, no-store").json(body);

        return true;
    }

    /** The same guard for routes that have not resolved the session themselves. */
    public static boolean blocksApi(Context ctx) {
        return blocksApi(ctx, Auth.current(ctx));
    }
}
