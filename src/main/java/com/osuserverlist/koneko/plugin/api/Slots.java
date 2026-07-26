package com.osuserverlist.koneko.plugin.api;

/**
 * The slots the core site opens for plugins.
 *
 * <p>A slot is a named place where plugin components are rendered inside a page
 * the plugin does not own. Every core view carries a {@code .top} and a
 * {@code .bottom} slot, plus the two navigation slots and the two footer slots
 * that are shared by every page.
 *
 * <p>Slot names are just strings, so a plugin may open slots of its own inside
 * its own components with {@code <koneko-slot name="my.slot">} and let another
 * plugin fill them.
 */
public final class Slots {

    private Slots() {
    }

    /** In the navigation bar, next to Leaderboard and Beatmaps. */
    public static final String NAV_LINKS = "nav.links";

    /** In the navigation bar, next to the login button and the avatar. */
    public static final String NAV_ACTIONS = "nav.actions";

    /** Top of every page, right under the navigation bar. */
    public static final String PAGE_TOP = "page.top";

    /** Bottom of every page, right above the footer. */
    public static final String PAGE_BOTTOM = "page.bottom";

    /** Front page, above the welcome band. */
    public static final String HOME_TOP = "home.top";

    /** Front page, under the last section. */
    public static final String HOME_BOTTOM = "home.bottom";

    /** Profile page, above the player card. */
    public static final String PROFILE_TOP = "profile.top";

    /** Profile page, under the score lists. */
    public static final String PROFILE_BOTTOM = "profile.bottom";

    /** Ranking page, above the table. */
    public static final String LEADERBOARD_TOP = "leaderboard.top";

    /** Ranking page, under the table. */
    public static final String LEADERBOARD_BOTTOM = "leaderboard.bottom";

    /** Beatmap listing, above the filters. */
    public static final String BEATMAPS_TOP = "beatmaps.top";

    /** Beatmap listing, under the results. */
    public static final String BEATMAPS_BOTTOM = "beatmaps.bottom";

    /** Beatmap set page, above the set header. */
    public static final String BEATMAPSET_TOP = "beatmapset.top";

    /** Beatmap set page, under the difficulty list. */
    public static final String BEATMAPSET_BOTTOM = "beatmapset.bottom";

    /** Login page, above the form. */
    public static final String LOGIN_TOP = "login.top";

    /** Login page, under the form. */
    public static final String LOGIN_BOTTOM = "login.bottom";

    /** Footer, among the links. */
    public static final String FOOTER_LINKS = "footer.links";

    /** Footer, on its own line under everything. */
    public static final String FOOTER_BOTTOM = "footer.bottom";
}
