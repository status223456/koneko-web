package com.osuserverlist.koneko.plugin.api;

import java.util.function.Consumer;

/**
 * Everything a plugin can add to koneko-web.
 *
 * <p>Handed to {@link KonekoExtension#register(PluginRegistry)}. Every method
 * returns the registry itself, so a whole plugin is usually one chained call.
 *
 * <pre>{@code
 * registry.components("/vue")
 *         .page(PluginPage.of("/tournaments", "tournaments-view").withTitle("Tournaments"))
 *         .action(PluginAction.get("/list", ctx -> ctx.json(service.list())))
 *         .slot(PluginSlot.of(Slots.NAV_LINKS, "tournaments-nav-link"))
 *         .navItem(PluginNavItem.of("Tournaments", "/tournaments"))
 *         .assets("/static")
 *         .stylesheet(registry.context().assetUrl("/tournaments.css"))
 *         .on(Events.Login.class, event -> log.info("{} is back", event.user().name()));
 * }</pre>
 */
public interface PluginRegistry {

    /** Id of the plugin this registry belongs to, from the jar manifest. */
    String pluginId();

    /** The host services of this plugin. */
    KonekoContext context();

    // ------------------------------------------------------------------
    // pages and actions
    // ------------------------------------------------------------------

    /**
     * Adds a full page at an absolute path, rendered from a vue component of
     * this plugin. A path already taken by the core site is refused.
     */
    PluginRegistry page(PluginPage page);

    /**
     * Adds an HTTP endpoint. Relative paths are mounted under
     * {@code /plugins/{pluginId}}; see {@link PluginAction#asAbsolute()} to
     * take a path of your own.
     */
    PluginRegistry action(PluginAction action);

    /** Adds a before/after filter around requests. */
    PluginRegistry filter(PluginFilter filter);

    // ------------------------------------------------------------------
    // vue
    // ------------------------------------------------------------------

    /** Adds one vue component, either inline or read from the plugin jar. */
    PluginRegistry component(PluginComponent component);

    /**
     * Adds every {@code .vue} file found under a directory of the plugin jar,
     * which is how a plugin normally ships its markup.
     *
     * @param resourceDirectory a resource path such as {@code /vue}
     */
    PluginRegistry components(String resourceDirectory);

    /**
     * Renders one of this plugin's components inside a named slot of the
     * existing site. See {@link Slots} for the slots the core pages open.
     */
    PluginRegistry slot(PluginSlot slot);

    /** Adds an entry to the navigation bar and the footer. */
    PluginRegistry navItem(PluginNavItem item);

    /**
     * Hides an entry of the navigation bar or the footer, so a plugin can
     * replace a core link with one of its own instead of adding a second one.
     *
     * <p>The ids of the core entries are {@code leaderboard}, {@code beatmaps},
     * {@code community}, {@code api} in the bar and {@code footer.leaderboard},
     * {@code footer.beatmaps}, {@code footer.discord}, {@code footer.github},
     * {@code footer.api} in the footer. An entry contributed by a plugin has the
     * id {@code pluginId:href}, so plugins can hide each other's links too.
     *
     * <p>An unknown id is simply ignored, which keeps a plugin working when the
     * site later drops a link.
     */
    PluginRegistry hideNavItem(String id);

    // ------------------------------------------------------------------
    // static files and raw html
    // ------------------------------------------------------------------

    /** Serves one file from the plugin jar under {@code /plugin-assets/{id}}. */
    PluginRegistry asset(PluginAsset asset);

    /**
     * Serves a whole directory of the plugin jar under
     * {@code /plugin-assets/{id}}, so {@code /static/app.css} of the jar
     * becomes {@code /plugin-assets/{id}/app.css}.
     */
    PluginRegistry assets(String resourceDirectory);

    /** Adds a stylesheet link to the {@code <head>} of every page. */
    PluginRegistry stylesheet(String url);

    /** Adds a script tag at the end of the body of every page. */
    PluginRegistry script(String url);

    /** Adds raw html to the {@code <head>} of every page. */
    PluginRegistry head(String html);

    /** Adds raw html at the end of the body of every page. */
    PluginRegistry bodyEnd(String html);

    // ------------------------------------------------------------------
    // data
    // ------------------------------------------------------------------

    /**
     * Adds a value to the bootstrap state of every page, reachable in vue as
     * {@code this.pluginData('pluginId', 'key')}.
     *
     * <p>The contributor runs on every page render, so it must be cheap; use
     * {@link KonekoContext#cache()} for anything that talks to the API.
     */
    PluginRegistry state(String key, StateContributor contributor);

    /** Runs a task on a schedule for as long as the site is up. */
    PluginRegistry job(PluginJob job);

    /** Listens to a host event; see {@link Events} for the types. */
    <E> PluginRegistry on(Class<E> eventType, Consumer<E> listener);
}
