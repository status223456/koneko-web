package com.osuserverlist.koneko.plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.pf4j.JarPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.osuserverlist.koneko.config.Env;
import com.osuserverlist.koneko.config.SiteConfig;
import com.osuserverlist.koneko.plugin.api.Events;
import com.osuserverlist.koneko.plugin.api.KonekoContext;
import com.osuserverlist.koneko.plugin.api.KonekoExtension;

/**
 * The plugin host: loads the {@code .jar} files from the plugins directory with
 * PF4J, starts them, and collects everything they contribute.
 *
 * <p>All of this happens <em>before</em> the HTTP server is created, because
 * Javalin 7 takes its routes at creation time - a plugin page has to exist by
 * then or it can never be mounted. The order at boot is therefore:
 * {@link #boot(SiteConfig)}, then {@code Javalin.create(...)} with
 * {@link PluginRoutes#register}, then {@link #afterStart()}.
 *
 * <p>Everything here is defensive on purpose. A jar that fails to load, an
 * extension that throws in {@code onStart}, a plugin asking for a path the core
 * site owns: all of it is logged and skipped. A broken plugin must never keep
 * the site from starting.
 */
public final class PluginService {

    private static final Logger logger = LoggerFactory.getLogger("Plugins");

    private static final PluginEventBus EVENTS = new PluginEventBus();

    private static final Contributions CONTRIBUTIONS = new Contributions();

    private static volatile PluginManager manager;

    private static volatile ScheduledExecutorService scheduler;

    private static volatile boolean enabled;

    /** Where the .jar files live. Fixed, so no deployment setting is needed. */
    private static final String PLUGINS_DIR = "plugins";

    private static volatile Path pluginsDir;

    private PluginService() {
    }

    // ------------------------------------------------------------------
    // boot
    // ------------------------------------------------------------------

    /**
     * Loads and starts every plugin, then lets each of them register.
     *
     * @param site site config, for the kill switch, the disabled list and the
     *             plugin settings
     */
    public static synchronized void boot(SiteConfig site) {
        enabled = site.getPlugins().isEnabled();

        if (!enabled) {
            logger.info("Plugins are turned off");
            return;
        }

        pluginsDir = Path.of(PLUGINS_DIR).toAbsolutePath().normalize();

        if (!Files.isDirectory(pluginsDir)) {
            logger.info("No plugins directory at <{}>; nothing to load", pluginsDir);
            return;
        }

        // JarPluginManager loads plain .jar files and reads the plugin id,
        // version and class from their manifest, which is exactly the
        // "drop a jar in a folder" model we want here.
        JarPluginManager jarManager = new JarPluginManager(pluginsDir);
        manager = jarManager;

        try {
            jarManager.loadPlugins();
        } catch (RuntimeException e) {
            logger.error("Could not load the plugins from <{}>", pluginsDir, e);
            return;
        }

        disableAll(jarManager, disabledIds(site));

        try {
            jarManager.startPlugins();
        } catch (RuntimeException e) {
            logger.error("Could not start the plugins", e);
        }

        List<PluginWrapper> started = jarManager.getStartedPlugins();

        if (started.isEmpty()) {
            logger.info("No plugins were started from <{}>", pluginsDir);
            return;
        }

        for (PluginWrapper wrapper : started) {
            registerPlugin(jarManager, wrapper, site);
        }

        logger.info("Loaded {} plugin(s): {} page(s), {} action(s), {} component(s), {} slot(s)",
                CONTRIBUTIONS.contexts.size(), CONTRIBUTIONS.pages.size(),
                CONTRIBUTIONS.actions.size(), CONTRIBUTIONS.components.size(),
                CONTRIBUTIONS.slots.size());

        EVENTS.publish(new Events.Ready(CONTRIBUTIONS.pluginIds()));
    }

    /** Runs the contributed jobs. Called once the server is listening. */
    public static synchronized void afterStart() {
        if (CONTRIBUTIONS.jobs.isEmpty()) {
            return;
        }

        scheduler = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "koneko-plugin-jobs");
            thread.setDaemon(true);
            return thread;
        });

        for (Contributions.Job job : CONTRIBUTIONS.jobs) {
            schedule(job);
        }

        logger.info("Scheduled {} plugin job(s)", CONTRIBUTIONS.jobs.size());
    }

    /** Stops the jobs and the plugins. Called from the shutdown hook. */
    public static synchronized void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        PluginManager current = manager;

        if (current == null) {
            return;
        }

        for (KonekoContext context : CONTRIBUTIONS.contexts.values()) {
            EVENTS.publish(new Events.Shutdown(context));
        }

        try {
            current.stopPlugins();
        } catch (RuntimeException e) {
            logger.warn("A plugin failed while stopping: {}", e.getMessage());
        }

        manager = null;
    }

    // ------------------------------------------------------------------
    // state
    // ------------------------------------------------------------------

    /** True when the plugin host is on and a directory was found. */
    public static boolean enabled() {
        return enabled && manager != null;
    }

    /** The event bus, also used by the core routes to publish. */
    public static com.osuserverlist.koneko.plugin.api.EventBus events() {
        return EVENTS;
    }

    /** Ids of every plugin that registered something. */
    public static List<String> pluginIds() {
        return CONTRIBUTIONS.pluginIds();
    }

    /** Package private: what the routes, the shell and the state builder read. */
    static Contributions contributions() {
        return CONTRIBUTIONS;
    }

    /**
     * A short description of every loaded plugin, for {@code /data/plugins} and
     * the boot log.
     */
    public static List<Map<String, Object>> describe() {
        List<Map<String, Object>> all = new ArrayList<>();

        PluginManager current = manager;

        if (current == null) {
            return all;
        }

        for (PluginWrapper wrapper : current.getPlugins()) {
            Map<String, Object> one = new LinkedHashMap<>();

            one.put("id", wrapper.getPluginId());
            one.put("version", wrapper.getDescriptor().getVersion());
            one.put("provider", wrapper.getDescriptor().getProvider());
            one.put("description", wrapper.getDescriptor().getPluginDescription());
            one.put("state", String.valueOf(wrapper.getPluginState()).toLowerCase(java.util.Locale.ROOT));
            one.put("pages", CONTRIBUTIONS.pages.stream()
                    .filter(page -> page.pluginId().equals(wrapper.getPluginId()))
                    .map(page -> page.page().path()).toList());

            all.add(one);
        }

        return all;
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /** Ids listed as disabled in {@code .config/config.yml}. */
    private static Set<String> disabledIds(SiteConfig site) {
        Set<String> disabled = new LinkedHashSet<>();

        List<String> fromConfig = site.getPlugins().getDisabled();

        if (fromConfig != null) {
            for (String id : fromConfig) {
                if (id != null && !id.isBlank()) {
                    disabled.add(id.trim());
                }
            }
        }

        return disabled;
    }

    private static void disableAll(PluginManager jarManager, Set<String> disabled) {
        for (String id : disabled) {
            if (jarManager.getPlugin(id) == null) {
                logger.warn("<{}> is listed as disabled but was not found", id);
                continue;
            }

            jarManager.disablePlugin(id);
            logger.info("<{}> is disabled by configuration", id);
        }
    }

    /** Starts and registers every extension of one plugin. */
    private static void registerPlugin(PluginManager jarManager, PluginWrapper wrapper, SiteConfig site) {
        String id = wrapper.getPluginId();

        if (wrapper.getPluginState() != PluginState.STARTED) {
            return;
        }

        List<KonekoExtension> extensions;

        try {
            extensions = new ArrayList<>(jarManager.getExtensions(KonekoExtension.class, id));
        } catch (RuntimeException e) {
            logger.error("<{}> could not be inspected for extensions", id, e);
            return;
        }

        if (extensions.isEmpty()) {
            logger.warn("<{}> was started but has no KonekoExtension; it does nothing", id);
            return;
        }

        extensions.sort(Comparator.comparingInt(KonekoExtension::order));

        KonekoContext context = new PluginContextImpl(id,
                wrapper.getDescriptor().getVersion(),
                PluginSettingsImpl.load(id, site.getPlugins().settingsOf(id), settingsFile(id)),
                EVENTS,
                dataDirOf(id));

        RegistryImpl registry = new RegistryImpl(id, context, CONTRIBUTIONS,
                wrapper.getPluginClassLoader(), wrapper.getPluginPath(), EVENTS);

        for (KonekoExtension extension : extensions) {
            try {
                extension.onStart(context);
                extension.register(registry);
            } catch (RuntimeException | LinkageError e) {
                logger.error("<{}> failed while registering <{}>; it is skipped",
                        id, extension.getClass().getName(), e);
            }
        }

        CONTRIBUTIONS.contexts.put(id, context);
        EVENTS.publish(new Events.Startup(context));

        logger.info("<{}> {} is ready", id, wrapper.getDescriptor().getVersion());
    }

    private static Path settingsFile(String pluginId) {
        return Path.of(Env.CONFIG_DIR).resolve("plugins").resolve(pluginId + ".yml");
    }

    private static Path dataDirOf(String pluginId) {
        Path base = pluginsDir == null ? Path.of(PLUGINS_DIR) : pluginsDir;
        return base.resolve("data").resolve(pluginId);
    }

    private static void schedule(Contributions.Job job) {
        ScheduledExecutorService current = scheduler;

        if (current == null) {
            return;
        }

        String name = job.pluginId() + "/" + job.job().name();

        Runnable safe = () -> {
            // Jobs run on a virtual thread, so a slow one never occupies the
            // single scheduler thread and delays the others.
            Thread.ofVirtual().name("plugin-job-" + name).start(() -> {
                try {
                    job.job().task().run();
                } catch (RuntimeException | Error e) {
                    logger.warn("The job <{}> failed: {}", name, e.getMessage(), e);
                }
            });
        };

        long delay = Math.max(0, job.job().initialDelay());
        long period = job.job().period();

        if (period <= 0) {
            current.schedule(safe, delay, TimeUnit.SECONDS);
        } else {
            current.scheduleAtFixedRate(safe, delay, period, TimeUnit.SECONDS);
        }
    }
}
