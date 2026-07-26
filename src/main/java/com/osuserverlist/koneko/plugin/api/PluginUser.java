package com.osuserverlist.koneko.plugin.api;

/**
 * The logged in player, as a plugin sees one.
 *
 * <p>The access token is included so a plugin can call an authenticated
 * bancho.jar endpoint through {@link ApiClient}; it must never be written to a
 * page, a log or a file.
 *
 * @param id         numeric player id on the game server
 * @param name       current username
 * @param privileges the bancho.jar privilege bitmask
 * @param token      the OAuth2 access token of this session
 */
public record PluginUser(int id, String name, int privileges, String token) {

    /** True when every bit of the mask is set on this player. */
    public boolean has(int mask) {
        return (privileges & mask) == mask;
    }

    /** The same record without the token, for anything that leaves the server. */
    public PluginUser withoutToken() {
        return new PluginUser(id, name, privileges, null);
    }
}
