package com.osuserverlist.koneko.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.osuserverlist.koneko.plugin.api.PluginSettings;

/**
 * Settings of one plugin, merged from {@code config.yml} and the plugin's own
 * yml file next to the jars, the file winning.
 *
 * <p>Reading is deliberately forgiving: a missing key, a wrong type or a broken
 * file all fall back instead of throwing, because a plugin should degrade rather
 * than take the site down over a typo in a config file.
 */
final class PluginSettingsImpl implements PluginSettings {

    private static final Logger logger = LoggerFactory.getLogger("Plugins");

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Object> values;

    private PluginSettingsImpl(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * @param fromSiteConfig the {@code plugins.settings.{id}} block, may be null
     * @param configFile     {@code plugins/config/{id}.yml}, may not exist
     */
    @SuppressWarnings("unchecked")
    static PluginSettingsImpl load(String pluginId, Map<String, Object> fromSiteConfig, Path configFile) {
        Map<String, Object> merged = new LinkedHashMap<>();

        if (fromSiteConfig != null) {
            merged.putAll(fromSiteConfig);
        }

        if (configFile != null && Files.isRegularFile(configFile)) {
            try {
                Map<String, Object> fromFile = YAML.readValue(configFile.toFile(), Map.class);

                if (fromFile != null) {
                    merged.putAll(fromFile);
                }
            } catch (IOException | RuntimeException e) {
                logger.warn("Could not read the settings of <{}> from <{}>: {}",
                        pluginId, configFile, e.getMessage());
            }
        }

        return new PluginSettingsImpl(merged);
    }

    @Override
    public boolean has(String key) {
        return resolve(key) != null;
    }

    @Override
    public String string(String key, String fallback) {
        Object value = resolve(key);
        return value == null ? fallback : String.valueOf(value);
    }

    @Override
    public int integer(String key, int fallback) {
        return (int) number(key, fallback);
    }

    @Override
    public long number(String key, long fallback) {
        Object value = resolve(key);

        if (value instanceof Number n) {
            return n.longValue();
        }

        if (value == null) {
            return fallback;
        }

        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public boolean bool(String key, boolean fallback) {
        Object value = resolve(key);

        if (value instanceof Boolean b) {
            return b;
        }

        if (value == null) {
            return fallback;
        }

        String text = String.valueOf(value).trim();

        return "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text) || "1".equals(text);
    }

    @Override
    public List<String> list(String key) {
        Object value = resolve(key);

        if (value instanceof List<?> raw) {
            List<String> items = new ArrayList<>();

            for (Object item : raw) {
                if (item != null) {
                    items.add(String.valueOf(item));
                }
            }

            return items;
        }

        if (value == null) {
            return List.of();
        }

        // A single value is a list of one, which is what a config file usually
        // means when the yml has no dash.
        return List.of(String.valueOf(value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> map(String key) {
        Object value = resolve(key);

        if (value instanceof Map<?, ?> raw) {
            return new LinkedHashMap<>((Map<String, Object>) raw);
        }

        return Map.of();
    }

    @Override
    public Map<String, Object> all() {
        return new LinkedHashMap<>(values);
    }

    @Override
    public <T> T as(Class<T> type) {
        return MAPPER.convertValue(values, type);
    }

    /** Walks a dotted key into the nested maps. */
    private Object resolve(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        Object current = values;

        for (String part : key.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }

            current = map.get(part);

            if (current == null) {
                return null;
            }
        }

        return current;
    }
}
