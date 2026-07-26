package com.osuserverlist.koneko.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/** Reads {@link SiteConfig} from a yml file, falling back to the defaults. */
public final class SiteConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger("SiteConfig");

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private SiteConfigLoader() {
    }

    /**
     * Loads the site configuration.
     *
     * <p>A missing file is not an error: the built in defaults are used and a
     * warning is logged, so a fresh checkout starts without any setup. A
     * broken file is also survivable for the same reason - the site stays up
     * with default texts instead of refusing to boot.
     */
    public static SiteConfig load(String configPath) {
        Path path = Path.of(configPath);

        if (!Files.isRegularFile(path)) {
            logger.warn("No config file at <{}>; using the built in defaults. "
                    + "Copy config.example.yml to get started.", path.toAbsolutePath());
            return new SiteConfig();
        }

        try {
            SiteConfig config = YAML.readValue(path.toFile(), SiteConfig.class);

            if (config == null) {
                logger.warn("<{}> is empty; using the built in defaults", path);
                return new SiteConfig();
            }

            logger.info("Loaded the site configuration from <{}>", path.toAbsolutePath());
            return config;
        } catch (IOException e) {
            logger.error("Could not read <{}>; using the built in defaults", path, e);
            return new SiteConfig();
        }
    }
}
