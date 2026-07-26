package com.osuserverlist.koneko.plugin.api;

/**
 * An entry in the navigation bar and the footer.
 *
 * <p>The short way to link a plugin page. Anything more than a link (a badge, a
 * dropdown, a counter) is a component in {@link Slots#NAV_LINKS} instead.
 *
 * @param label         the text of the link
 * @param href          where it points, usually a page of the same plugin
 * @param order         lower comes first, default 100
 * @param external      when true, opens in a new tab with rel=noopener
 * @param footer        when true, the entry is also added to the footer
 * @param requiresLogin only shown to logged in players
 * @param minPrivileges only shown to players holding these privilege bits
 */
public record PluginNavItem(String label, String href, int order, boolean external,
        boolean footer, boolean requiresLogin, int minPrivileges) {

    public static PluginNavItem of(String label, String href) {
        return new PluginNavItem(label, href, 100, false, true, false, 0);
    }

    public PluginNavItem withOrder(int value) {
        return new PluginNavItem(label, href, value, external, footer, requiresLogin, minPrivileges);
    }

    public PluginNavItem asExternal() {
        return new PluginNavItem(label, href, order, true, footer, requiresLogin, minPrivileges);
    }

    /** Keeps the entry out of the footer. */
    public PluginNavItem navOnly() {
        return new PluginNavItem(label, href, order, external, false, requiresLogin, minPrivileges);
    }

    public PluginNavItem withLogin() {
        return new PluginNavItem(label, href, order, external, footer, true, minPrivileges);
    }

    public PluginNavItem withPrivileges(int mask) {
        return new PluginNavItem(label, href, order, external, footer, true, mask);
    }
}
