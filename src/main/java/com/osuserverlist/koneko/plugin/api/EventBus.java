package com.osuserverlist.koneko.plugin.api;

import java.util.function.Consumer;

/**
 * The event bus shared by the host and every plugin.
 *
 * <p>Listeners are called on the thread that publishes, in registration order,
 * and one failing listener never stops the others or the request.
 *
 * <p>Plugins may publish types of their own, which is the intended way for two
 * plugins to talk without depending on each other's classes.
 */
public interface EventBus {

    /** Subscribes to an event type and every subtype of it. */
    <E> void subscribe(Class<E> type, Consumer<E> handler);

    /** Publishes an event to every matching listener. */
    void publish(Object event);
}
