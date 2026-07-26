package com.osuserverlist.koneko.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.osuserverlist.koneko.plugin.api.EventBus;

/**
 * The event bus behind {@link com.osuserverlist.koneko.plugin.api.Events}.
 *
 * <p>Listeners are stored per event type and matched by assignability, so a
 * listener on a supertype (or on {@code Object}) sees the subtypes as well.
 * Delivery is synchronous on the publishing thread, which is what makes the
 * mutable events - the bootstrap state, the data bodies - work at all.
 *
 * <p>A failing listener is logged with the plugin that registered it and then
 * ignored: one broken plugin must not take a page render down.
 */
final class PluginEventBus implements EventBus {

    private static final Logger logger = LoggerFactory.getLogger("Plugins");

    private record Subscription(String pluginId, Class<?> type, Consumer<Object> handler) {
    }

    private final Map<Class<?>, List<Subscription>> byType = new ConcurrentHashMap<>();

    @Override
    public <E> void subscribe(Class<E> type, Consumer<E> handler) {
        subscribe("host", type, handler);
    }

    @SuppressWarnings("unchecked")
    <E> void subscribe(String pluginId, Class<E> type, Consumer<E> handler) {
        if (type == null || handler == null) {
            return;
        }

        byType.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
                .add(new Subscription(pluginId, type, (Consumer<Object>) handler));
    }

    @Override
    public void publish(Object event) {
        if (event == null) {
            return;
        }

        for (Subscription subscription : matching(event.getClass())) {
            try {
                subscription.handler().accept(event);
            } catch (RuntimeException e) {
                logger.warn("<{}> failed while handling {}: {}", subscription.pluginId(),
                        event.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /** Every listener whose type accepts this event class. */
    private List<Subscription> matching(Class<?> eventType) {
        List<Subscription> exact = byType.get(eventType);

        // The common case: a listener registered on the concrete record type.
        boolean onlyExact = true;

        for (Class<?> type : byType.keySet()) {
            if (!type.equals(eventType) && type.isAssignableFrom(eventType)) {
                onlyExact = false;
                break;
            }
        }

        if (onlyExact) {
            return exact == null ? List.of() : exact;
        }

        List<Subscription> all = new ArrayList<>();

        byType.forEach((type, subscriptions) -> {
            if (type.isAssignableFrom(eventType)) {
                all.addAll(subscriptions);
            }
        });

        return all;
    }

    /** Drops every listener of one plugin, used when it is stopped. */
    void forget(String pluginId) {
        byType.values().forEach(list -> list.removeIf(sub -> sub.pluginId().equals(pluginId)));
    }
}
