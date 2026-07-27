package com.osuserverlist.koneko.config;

import java.io.IOException;
import java.io.InputStream;
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
     * <p>The file always lives at {@link Env#CONFIG_PATH}.
     *
     * <p>A missing file is not an error: the built in defaults are used and a
     * warning is logged, so a fresh checkout starts without any setup. A
     * broken file is also survivable for the same reason - the site stays up
     * with default texts instead of refusing to boot.
     */
    public static SiteConfig load() {
        Path path = Path.of(Env.CONFIG_PATH);

        if (!Files.isRegularFile(path)) {
            logger.warn("No config file at <{}>; using the built in defaults. "
                    + "Copy .config/config.example.yml to get started.", path.toAbsolutePath());
            try (InputStream in = SiteConfigLoader.class.getResourceAsStream("/config.example.yml")) {
                if (in == null) {
                    logger.error("config.example.yml is missing from the JAR");
                } else {
                    Files.createDirectories(path.getParent());
                    Files.copy(in, path);
                    logger.info("Copied example config to <{}>", path.toAbsolutePath());
                }
            }catch(IOException e) {
                logger.error("Could not copy example config to <{}>", path.toAbsolutePath(), e);
            }
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
