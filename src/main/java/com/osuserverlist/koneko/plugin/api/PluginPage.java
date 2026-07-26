package com.osuserverlist.koneko.plugin.api;

/**
 * A full page contributed by a plugin.
 *
 * <p>Exactly like a core page: one path, one vue component, rendered inside the
 * same shell, so a plugin page gets the navigation bar, the footer, the shared
 * formatting helpers and the bootstrap state for free.
 *
 * <p>Paths may carry Javalin parameters ({@code /tournaments/{id}}) and
 * wildcards ({@code /wiki/<path>}).
 *
 * @param path           absolute path, must start with a slash
 * @param component      name of a vue component this plugin registered
 * @param title          browser title, empty to leave it to the component
 * @param requiresLogin  when true, anonymous visitors are sent to /login
 * @param minPrivileges  bancho.jar privilege bits the player must have
 * @param status         HTTP status of the answer, normally 200
 */
public record PluginPage(String path, String component, String title,
        boolean requiresLogin, int minPrivileges, int status) {

    public static PluginPage of(String path, String component) {
        return new PluginPage(path, component, "", false, 0, 200);
    }

    public PluginPage withTitle(String value) {
        return new PluginPage(path, component, value == null ? "" : value,
                requiresLogin, minPrivileges, status);
    }

    /** Only for logged in players; everyone else is redirected to /login. */
    public PluginPage withLogin() {
        return new PluginPage(path, component, title, true, minPrivileges, status);
    }

    /** Requires these privilege bits, which implies a login. */
    public PluginPage withPrivileges(int mask) {
        return new PluginPage(path, component, title, true, mask, status);
    }

    public PluginPage withStatus(int value) {
        return new PluginPage(path, component, title, requiresLogin, minPrivileges, value);
    }
}
