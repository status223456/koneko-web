package com.osuserverlist.koneko.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;

/**
 * Deployment settings, read from {@code .env} exactly like bancho.jar does.
 *
 * <p>Everything has a default so a missing or partial {@code .env} still
 * starts. Anything that describes the site itself (names, texts, links) lives
 * in {@link SiteConfig} instead.
 */
@Getter
public final class Env {

    private static final Logger logger = LoggerFactory.getLogger("Env");

    private final int port;
    private final String domain;
    private final String apiBaseUrl;
    private final String level;
    private final String configPath;
    private final int sessionTimeoutMinutes;
    private final int apiTimeoutSeconds;
    private final String apiClientId;
    private final String apiScopes;

    private Env(Dotenv dotenv) {
        this.port = intOf(dotenv, "PORT", 8300);
        this.domain = stringOf(dotenv, "DOMAIN", "localhost");
        this.apiBaseUrl = stringOf(dotenv, "API_BASE_URL", "");
        this.level = stringOf(dotenv, "LEVEL", "PROD");
        this.configPath = stringOf(dotenv, "CONFIG_PATH", "config.yml");
        this.sessionTimeoutMinutes = intOf(dotenv, "SESSION_TIMEOUT_MINUTES", 1440);
        this.apiTimeoutSeconds = intOf(dotenv, "API_TIMEOUT_SECONDS", 10);
        this.apiClientId = stringOf(dotenv, "API_CLIENT_ID", "koneko-web");
        this.apiScopes = stringOf(dotenv, "API_SCOPES", "identify profile");
    }

    /** Loads {@code .env} from the working directory; a missing file is fine. */
    public static Env load() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        return new Env(dotenv);
    }

    /** True while {@code LEVEL=DEV}: hot reloaded views, no Secure cookies. */
    public boolean isDev() {
        return "DEV".equalsIgnoreCase(level);
    }

    /** Session cookies are only marked Secure outside of DEV. */
    public boolean useSecureCookies() {
        return !isDev();
    }

    private static String stringOf(Dotenv dotenv, String key, String fallback) {
        String value = dotenv.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int intOf(Dotenv dotenv, String key, int fallback) {
        String raw = dotenv.get(key);

        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            logger.warn("<{}> is not a number, falling back to <{}>", key, fallback);
            return fallback;
        }
    }
}
