package com.osuserverlist.koneko.plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.osuserverlist.koneko.plugin.api.KonekoContext;
import com.osuserverlist.koneko.plugin.api.PluginAction;
import com.osuserverlist.koneko.plugin.api.PluginAsset;
import com.osuserverlist.koneko.plugin.api.PluginComponent;
import com.osuserverlist.koneko.plugin.api.PluginFilter;
import com.osuserverlist.koneko.plugin.api.PluginJob;
import com.osuserverlist.koneko.plugin.api.PluginNavItem;
import com.osuserverlist.koneko.plugin.api.PluginPage;
import com.osuserverlist.koneko.plugin.api.PluginRegistry;
import com.osuserverlist.koneko.plugin.api.PluginSlot;
import com.osuserverlist.koneko.plugin.api.StateContributor;

/**
 * The {@link PluginRegistry} the host hands to a plugin.
 *
 * <p>Everything is validated here, while the plugin that caused a problem is
 * still known: a page on a reserved path, a component whose name is already
 * taken, a resource that is not in the jar. A rejected contribution is logged
 * with the plugin id and skipped, the rest of the plugin still loads.
 */
final class RegistryImpl implements PluginRegistry {

    private static final Logger logger = LoggerFactory.getLogger("Plugins");

    private final String pluginId;
    private final KonekoContext context;
    private final Contributions contributions;
    private final ClassLoader loader;
    private final Path pluginPath;
    private final PluginEventBus events;

    RegistryImpl(String pluginId, KonekoContext context, Contributions contributions,
            ClassLoader loader, Path pluginPath, PluginEventBus events) {

        this.pluginId = pluginId;
        this.context = context;
        this.contributions = contributions;
        this.loader = loader;
        this.pluginPath = pluginPath;
        this.events = events;
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    @Override
    public KonekoContext context() {
        return context;
    }

    // ------------------------------------------------------------------
    // pages and actions
    // ------------------------------------------------------------------

    @Override
    public PluginRegistry page(PluginPage page) {
        if (page == null || page.path() == null || page.component() == null) {
            logger.warn("<{}> tried to register an incomplete page", pluginId);
            return this;
        }

        if (contributions.isReserved(page.path())) {
            logger.warn("<{}> tried to take <{}>, which belongs to the core site",
                    pluginId, page.path());
            return this;
        }

        if (contributions.pageTaken(page.path())) {
            logger.warn("<{}> tried to take <{}>, which another plugin already serves",
                    pluginId, page.path());
            return this;
        }

        contributions.pages.add(new Contributions.Page(pluginId, page));
        return this;
    }

    @Override
    public PluginRegistry action(PluginAction action) {
        if (action == null || action.path() == null || action.handler() == null) {
            logger.warn("<{}> tried to register an incomplete action", pluginId);
            return this;
        }

        String path = action.absolute()
                ? PluginResources.normalise(action.path())
                : "/plugins/" + pluginId + PluginResources.normalise(action.path());

        if (action.absolute() && contributions.isReserved(path)) {
            logger.warn("<{}> tried to take <{}>, which belongs to the core site", pluginId, path);
            return this;
        }

        contributions.actions.add(new Contributions.Action(pluginId, path, action));
        return this;
    }

    @Override
    public PluginRegistry filter(PluginFilter filter) {
        if (filter == null || filter.handler() == null) {
            return this;
        }

        contributions.filters.add(new Contributions.Filter(pluginId, filter));
        return this;
    }

    // ------------------------------------------------------------------
    // vue
    // ------------------------------------------------------------------

    @Override
    public PluginRegistry component(PluginComponent component) {
        if (component == null) {
            return this;
        }

        String source = component.source();

        if (source == null) {
            try {
                source = PluginResources.readText(loader, component.resource());
            } catch (IOException e) {
                logger.warn("<{}> could not read <{}>: {}", pluginId, component.resource(), e.getMessage());
                return this;
            }
        }

        if (source == null || source.isBlank()) {
            logger.warn("<{}> registered an empty component <{}>", pluginId, component.name());
            return this;
        }

        if (contributions.componentTaken(component.name())) {
            logger.warn("<{}> registered <{}>, a component name that is already taken",
                    pluginId, component.name());
            return this;
        }

        contributions.components.add(new Contributions.Component(pluginId, component.name(), source));
        return this;
    }

    @Override
    public PluginRegistry components(String resourceDirectory) {
        List<String> files;

        try {
            files = PluginResources.list(pluginPath, resourceDirectory, ".vue");
        } catch (IOException e) {
            logger.warn("<{}> could not list <{}>: {}", pluginId, resourceDirectory, e.getMessage());
            return this;
        }

        if (files.isEmpty()) {
            logger.warn("<{}> has no .vue files under <{}>", pluginId, resourceDirectory);
            return this;
        }

        for (String file : files) {
            component(PluginComponent.fromResource(file));
        }

        return this;
    }

    @Override
    public PluginRegistry slot(PluginSlot slot) {
        if (slot == null || slot.slot() == null || slot.component() == null) {
            return this;
        }

        contributions.slots.add(new Contributions.Slot(pluginId, slot));
        return this;
    }

    @Override
    public PluginRegistry navItem(PluginNavItem item) {
        if (item == null || item.label() == null || item.href() == null) {
            return this;
        }

        contributions.navItems.add(new Contributions.Nav(pluginId, item));
        return this;
    }

    @Override
    public PluginRegistry hideNavItem(String id) {
        if (id == null || id.isBlank()) {
            return this;
        }

        contributions.hiddenNav.add(id.trim());
        return this;
    }

    // ------------------------------------------------------------------
    // static files and raw html
    // ------------------------------------------------------------------

    @Override
    public PluginRegistry asset(PluginAsset asset) {
        if (asset == null || asset.urlPath() == null || asset.resource() == null) {
            return this;
        }

        String url = PluginResources.normalise(asset.urlPath());

        contributions.assets.add(new Contributions.Asset(pluginId, url, asset, loader));
        return this;
    }

    @Override
    public PluginRegistry assets(String resourceDirectory) {
        contributions.assetRoots.add(new Contributions.AssetRoot(pluginId,
                PluginResources.normalise(resourceDirectory), loader));

        return this;
    }

    @Override
    public PluginRegistry stylesheet(String url) {
        if (url == null || url.isBlank()) {
            return this;
        }

        return head("<link rel=\"stylesheet\" href=\"" + escape(url) + "\">");
    }

    @Override
    public PluginRegistry script(String url) {
        if (url == null || url.isBlank()) {
            return this;
        }

        return bodyEnd("<script src=\"" + escape(url) + "\"></script>");
    }

    @Override
    public PluginRegistry head(String html) {
        if (html != null && !html.isBlank()) {
            contributions.head.add(new Contributions.Html(pluginId, html));
        }

        return this;
    }

    @Override
    public PluginRegistry bodyEnd(String html) {
        if (html != null && !html.isBlank()) {
            contributions.bodyEnd.add(new Contributions.Html(pluginId, html));
        }

        return this;
    }

    // ------------------------------------------------------------------
    // data
    // ------------------------------------------------------------------

    @Override
    public PluginRegistry state(String key, StateContributor contributor) {
        if (key == null || key.isBlank() || contributor == null) {
            return this;
        }

        contributions.state.add(new Contributions.State(pluginId, key, contributor));
        return this;
    }

    @Override
    public PluginRegistry job(PluginJob job) {
        if (job == null || job.task() == null) {
            return this;
        }

        contributions.jobs.add(new Contributions.Job(pluginId, job));
        return this;
    }

    @Override
    public <E> PluginRegistry on(Class<E> eventType, Consumer<E> listener) {
        events.subscribe(pluginId, eventType, listener);
        return this;
    }

    /** Minimal attribute escaping for the urls that end up in the shell. */
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
