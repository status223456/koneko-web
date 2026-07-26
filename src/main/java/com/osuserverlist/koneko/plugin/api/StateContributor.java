package com.osuserverlist.koneko.plugin.api;

import io.javalin.http.Context;

/**
 * Builds one value for the bootstrap state of a page.
 *
 * <p>Called on every page render, so keep it cheap. Returning null leaves the
 * key out entirely, and anything thrown is logged and skipped rather than
 * breaking the page.
 */
@FunctionalInterface
public interface StateContributor {

    Object build(Context ctx);
}
