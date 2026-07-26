package com.osuserverlist.koneko.plugin.api;

import java.util.Map;

/**
 * One of this plugin's components, rendered inside a named slot of a page that
 * already exists.
 *
 * <p>This is what lets a plugin change the site without forking a single
 * {@code .vue} file: the core pages open the slots listed in {@link Slots}, and
 * every component registered for a slot is rendered there, ordered by
 * {@link #order()}.
 *
 * <p>The component receives {@code props} as attributes, plus a
 * {@code slotContext} prop carrying whatever the surrounding page knows (the
 * player on a profile, the set on a beatmap page, and so on).
 *
 * @param slot      slot name, see {@link Slots}
 * @param component name of a vue component of this plugin
 * @param order     lower renders first, default 100
 * @param props     static props handed to the component, never null
 */
public record PluginSlot(String slot, String component, int order, Map<String, Object> props) {

    public static PluginSlot of(String slot, String component) {
        return new PluginSlot(slot, component, 100, Map.of());
    }

    public PluginSlot withOrder(int value) {
        return new PluginSlot(slot, component, value, props);
    }

    public PluginSlot withProps(Map<String, Object> values) {
        return new PluginSlot(slot, component, order, values == null ? Map.of() : Map.copyOf(values));
    }
}
