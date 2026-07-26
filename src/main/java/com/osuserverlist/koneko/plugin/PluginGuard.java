package com.osuserverlist.koneko.plugin;

import java.util.Map;

import com.osuserverlist.koneko.auth.Auth;
import com.osuserverlist.koneko.auth.UserSession;

import io.javalin.http.Context;

/**
 * The login and privilege checks in front of plugin pages and actions.
 *
 * <p>Kept in the host on purpose: a plugin declares what it needs
 * ({@code withLogin()}, {@code withPrivileges(mask)}) and the host decides, so
 * a plugin cannot get the check subtly wrong and open an admin page to
 * everyone.
 */
final class PluginGuard {

    private PluginGuard() {
    }

    /**
     * Checks a page request.
     *
     * @return true when the request may continue; false when it was already
     *         answered with a redirect or an error page
     */
    static boolean allowPage(Context ctx, boolean requiresLogin, int minPrivileges) {
        if (!requiresLogin && minPrivileges == 0) {
            return true;
        }

        UserSession session = Auth.current(ctx);

        if (session == null) {
            ctx.redirect("/login");
            return false;
        }

        if (!hasPrivileges(session, minPrivileges)) {
            ctx.status(403).contentType("text/html; charset=utf-8")
                    .result("<h1>403</h1><p>This page is not for your account.</p>");
            return false;
        }

        return true;
    }

    /**
     * Checks an action request. Answers with JSON, because every action is
     * called from a fetch.
     */
    static boolean allowAction(Context ctx, boolean requiresLogin, int minPrivileges) {
        if (!requiresLogin && minPrivileges == 0) {
            return true;
        }

        UserSession session = Auth.current(ctx);

        if (session == null) {
            ctx.status(401).json(Map.of("status", "This action needs a logged in player."));
            return false;
        }

        if (!hasPrivileges(session, minPrivileges)) {
            ctx.status(403).json(Map.of("status", "This action is not for your account."));
            return false;
        }

        return true;
    }

    /** Bitmask check against the bancho.jar privileges of the player. */
    static boolean hasPrivileges(UserSession session, int mask) {
        return mask == 0 || (session.getPrivileges() & mask) == mask;
    }

    /** Whether the player behind the request may see a navigation entry. */
    static boolean visible(Context ctx, boolean requiresLogin, int minPrivileges) {
        if (!requiresLogin && minPrivileges == 0) {
            return true;
        }

        UserSession session = Auth.current(ctx);

        return session != null && hasPrivileges(session, minPrivileges);
    }
}
