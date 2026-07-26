package com.osuserverlist.koneko.plugin.api;

/**
 * A static file shipped inside the plugin jar and served by the site.
 *
 * <p>Everything lands under {@code /plugin-assets/{pluginId}/...}, so two
 * plugins can both ship an {@code app.css} without colliding, and the url of a
 * file is always {@code context.assetUrl("/app.css")}.
 *
 * @param urlPath     public path under the plugin's asset root, e.g. /app.css
 * @param resource    resource path inside the jar, e.g. /static/app.css
 * @param contentType content type to send, or null to guess from the extension
 */
public record PluginAsset(String urlPath, String resource, String contentType) {

    public static PluginAsset of(String urlPath, String resource) {
        return new PluginAsset(urlPath, resource, null);
    }

    public PluginAsset withContentType(String value) {
        return new PluginAsset(urlPath, resource, value);
    }
}
