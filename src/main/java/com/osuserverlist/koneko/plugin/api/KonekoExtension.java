package com.osuserverlist.koneko.plugin.api;

import org.pf4j.ExtensionPoint;

/**
 * The single extension point of koneko-web.
 *
 * <p>A plugin jar contains one {@code org.pf4j.Plugin} class (named in the
 * manifest as {@code Plugin-Class}) and any number of classes implementing this
 * interface, each annotated with {@code @org.pf4j.Extension}. Everything a
 * plugin adds to the site - pages, actions, vue components, slots, navigation
 * entries, assets, background jobs, event listeners - is declared in
 * {@link #register(PluginRegistry)}.
 *
 * <p>Lifecycle, in order:
 *
 * <ol>
 *   <li>the jar is loaded and started by PF4J;</li>
 *   <li>{@link #onStart(KonekoContext)} is called, once per extension;</li>
 *   <li>{@link #register(PluginRegistry)} is called, still before the HTTP
 *       server exists, because Javalin 7 wants every route at creation time;</li>
 *   <li>the site runs;</li>
 *   <li>{@link #onStop(KonekoContext)} is called on shutdown.</li>
 * </ol>
 *
 * <p>Both lifecycle methods are optional. Anything thrown out of them is logged
 * and disables that one extension only, never the whole site.
 */
public interface KonekoExtension extends ExtensionPoint {

    /**
     * Declares everything this extension contributes.
     *
     * <p>Called exactly once, before the server starts. Registering later has
     * no effect for routes, so all pages and actions must be declared here.
     */
    void register(PluginRegistry registry);

    /** Called before {@link #register}, with the services of the host. */
    default void onStart(KonekoContext context) {
    }

    /** Called when the site shuts down or the plugin is stopped. */
    default void onStop(KonekoContext context) {
    }

    /**
     * Order among the extensions of every plugin: lower runs first. Only
     * matters when two plugins touch the same slot or the same state key.
     */
    default int order() {
        return 0;
    }
}
