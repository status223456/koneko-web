package com.osuserverlist.koneko.plugin.api;

/**
 * A task the host runs on a schedule while the site is up.
 *
 * <p>Jobs run on virtual threads from a shared scheduler and are stopped when
 * the plugin stops. An exception is logged and does not cancel the schedule, so
 * one bad run is not the end of the job.
 *
 * <p>A job must not touch a request: there is none. Everything it produces goes
 * through {@link PluginCache}, the plugin's {@code dataDir()} or a state
 * contributor.
 *
 * @param name           used for the thread name and the log lines
 * @param initialDelay   seconds before the first run
 * @param period         seconds between runs, 0 for a one shot task
 * @param task           what to run
 */
public record PluginJob(String name, long initialDelay, long period, Runnable task) {

    /** A repeating job. */
    public static PluginJob every(String name, long periodSeconds, Runnable task) {
        return new PluginJob(name, periodSeconds, periodSeconds, task);
    }

    /** A one shot task, run once after the delay. */
    public static PluginJob once(String name, long delaySeconds, Runnable task) {
        return new PluginJob(name, delaySeconds, 0, task);
    }

    public PluginJob withInitialDelay(long seconds) {
        return new PluginJob(name, seconds, period, task);
    }
}
