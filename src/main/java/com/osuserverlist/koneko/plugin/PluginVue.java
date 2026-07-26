package com.osuserverlist.koneko.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The plugin side of the page shell.
 *
 * <p>Plugin components are inlined into {@code layout.html} exactly like the
 * core ones, after them, so a plugin component may use any core component and
 * override nothing. The three strings are built once - plugins cannot register
 * after boot - and handed to {@link com.osuserverlist.koneko.vue.KonekoVue}.
 */
public final class PluginVue {

    private static volatile String cachedComponents;
    private static volatile String cachedHead;
    private static volatile String cachedBody;

    private PluginVue() {
    }

    /** Every plugin component, as one block of html. */
    public static String components() {
        String cached = cachedComponents;

        if (cached != null) {
            return cached;
        }

        StringBuilder builder = new StringBuilder();

        for (Contributions.Component component : PluginService.contributions().components) {
            builder.append("<!-- plugin ").append(component.pluginId())
                    .append(": ").append(component.name()).append(" -->\n");
            builder.append(component.source()).append('\n');
        }

        String components = builder.toString();
        cachedComponents = components;

        return components;
    }

    /** Everything plugins asked to put in the head. */
    public static String head() {
        String cached = cachedHead;

        if (cached != null) {
            return cached;
        }

        String head = join(PluginService.contributions().head);
        cachedHead = head;

        return head;
    }

    /** Everything plugins asked to put at the end of the body. */
    public static String bodyEnd() {
        String cached = cachedBody;

        if (cached != null) {
            return cached;
        }

        String body = join(PluginService.contributions().bodyEnd);
        cachedBody = body;

        return body;
    }

    /** Drops the cached strings; only useful in DEV. */
    public static void reset() {
        cachedComponents = null;
        cachedHead = null;
        cachedBody = null;
    }

    private static String join(List<Contributions.Html> parts) {
        List<String> lines = new ArrayList<>();

        for (Contributions.Html part : parts) {
            lines.add("<!-- plugin " + part.pluginId() + " -->");
            lines.add(part.html());
        }

        return String.join("\n", lines);
    }
}
