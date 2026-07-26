package com.osuserverlist.koneko.plugin.api;

/**
 * A vue component contributed by a plugin.
 *
 * <p>The source is the same single file format the core site uses: a
 * {@code <template id="name">} block and a {@code <script>} block calling
 * {@code app.component("name", { template: "#name", ... })}. It is inlined into
 * the page shell, so no build step and no bundler are involved.
 *
 * <p>Normally a plugin ships its components as resources in the jar and
 * registers the whole folder with {@link PluginRegistry#components(String)};
 * this record is for the cases where one file, or a generated string, is
 * needed.
 *
 * @param name     the component name, only used for logging and de-duplication
 * @param source   the file contents, or null when {@code resource} is set
 * @param resource resource path inside the plugin jar, or null
 */
public record PluginComponent(String name, String source, String resource) {

    /** A component from a string built at runtime. */
    public static PluginComponent inline(String name, String source) {
        return new PluginComponent(name, source, null);
    }

    /** A component read from the plugin jar, e.g. {@code /vue/my-view.vue}. */
    public static PluginComponent fromResource(String resource) {
        return new PluginComponent(nameOf(resource), null, resource);
    }

    private static String nameOf(String resource) {
        String path = resource == null ? "" : resource;
        int slash = path.lastIndexOf('/');
        String file = slash < 0 ? path : path.substring(slash + 1);

        return file.endsWith(".vue") ? file.substring(0, file.length() - 4) : file;
    }
}
