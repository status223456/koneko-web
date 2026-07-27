package com.osuserverlist.koneko.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.osuserverlist.koneko.plugin.api.PluginUser;

import io.javalin.http.Context;

/**
 * The {@code plugins} section of the page state, plus the two hooks the core
 * routes call.
 *
 * <p>The browser gets the slot table, the navigation entries and whatever the
 * plugins contributed as state; the vue side needs nothing else to render plugin
 * content inside core pages.
 *
 * <p>Navigation entries and slot entries are filtered per request, so an entry
 * that needs privileges is not even sent to a browser that may not see it.
 */
public final class PluginBootstrap {

    private static final Logger logger = LoggerFactory.getLogger("Plugins");

    private PluginBootstrap() {
    }

    /** Builds {@code window.__koneko.plugins} for one request. */
    public static Map<String, Object> state(Context ctx, PluginUser user) {
        Map<String, Object> plugins = new LinkedHashMap<>();

        if (!PluginService.enabled()) {
            plugins.put("ids", List.of());
            plugins.put("nav", List.of());
            plugins.put("navHidden", List.of());
            plugins.put("slots", Map.of());
            plugins.put("pages", List.of());
            plugins.put("data", Map.of());

            return plugins;
        }

        Contributions contributions = PluginService.contributions();

        plugins.put("ids", contributions.pluginIds());
        plugins.put("nav", nav(ctx, contributions));
        plugins.put("navHidden", List.copyOf(contributions.hiddenNav));
        plugins.put("slots", slots(contributions));
        plugins.put("pages", pages(contributions));
        plugins.put("data", data(ctx, contributions));

        return plugins;
    }

    private static List<Map<String, Object>> nav(Context ctx, Contributions contributions) {
        List<Map<String, Object>> items = new ArrayList<>();

        for (Contributions.Nav entry : contributions.navItems) {
            if (!PluginGuard.visible(ctx, entry.item().requiresLogin(), entry.item().minPrivileges())) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pluginId", entry.pluginId());
            // The id another plugin - or the site itself - can hide this by.
            item.put("id", entry.pluginId() + ":" + entry.item().href());
            item.put("label", entry.item().label());
            item.put("href", entry.item().href());
            item.put("order", entry.item().order());
            item.put("external", entry.item().external());
            item.put("footer", entry.item().footer());

            items.add(item);
        }

        items.sort((left, right) -> Integer.compare(
                (int) left.get("order"), (int) right.get("order")));

        return items;
    }

    private static Map<String, List<Map<String, Object>>> slots(Contributions contributions) {
        Map<String, List<Map<String, Object>>> slots = new LinkedHashMap<>();

        for (Contributions.Slot entry : contributions.slots) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pluginId", entry.pluginId());
            item.put("component", entry.slot().component());
            item.put("order", entry.slot().order());
            item.put("props", entry.slot().props());

            slots.computeIfAbsent(entry.slot().slot(), key -> new ArrayList<>()).add(item);
        }

        slots.values().forEach(list -> list.sort((left, right) -> Integer.compare(
                (int) left.get("order"), (int) right.get("order"))));

        return slots;
    }

    private static List<Map<String, Object>> pages(Contributions contributions) {
        List<Map<String, Object>> pages = new ArrayList<>();

        for (Contributions.Page entry : contributions.pages) {
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("pluginId", entry.pluginId());
            page.put("path", entry.page().path());
            page.put("component", entry.page().component());
            page.put("title", entry.page().title());

            pages.add(page);
        }

        return pages;
    }

    /** The state contributors, grouped by plugin. */
    private static Map<String, Map<String, Object>> data(Context ctx, Contributions contributions) {
        Map<String, Map<String, Object>> data = new LinkedHashMap<>();

        for (Contributions.State entry : contributions.state) {
            try {
                Object value = entry.contributor().build(ctx);

                if (value == null) {
                    continue;
                }

                data.computeIfAbsent(entry.pluginId(), key -> new LinkedHashMap<>())
                        .put(entry.key(), value);
            } catch (RuntimeException e) {
                logger.warn("<{}> failed to build the state key <{}>: {}",
                        entry.pluginId(), entry.key(), e.getMessage());
            }
        }

        return data;
    }
}
