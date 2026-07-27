package com.osuserverlist.koneko.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;

/**
 * Deployment settings, read from {@code .env} exactly like bancho.jar does.
 *
 * <p>Only the handful of values that really differ between deployments live
 * here; everything else is a constant below. Anything that describes the site
 * itself (names, texts, links) lives in {@link SiteConfig} instead, which is
 * read from {@code .config/config.yml}.
 */
@Getter
public final class Env {

    private static final Logger logger = LoggerFactory.getLogger("Env");

    /** Directory that holds every configuration file of the site. */
    public static final String CONFIG_DIR = ".config";

    /** The site texts. Not configurable: the location is part of the layout. */
    public static final String CONFIG_PATH = CONFIG_DIR + "/config.yml";

    /** Scopes asked for at login. identify = read own profile, profile = edit it. */
    public static final String API_SCOPES = "identify profile";

    /** Timeout of a single call to the bancho.jar API. */
    public static final int API_TIMEOUT_SECONDS = 10;

    /** How long a logged in browser survives without any request. */
    public static final int SESSION_TIMEOUT_MINUTES = 1440;

    private final int port;
    private final String domain;
    private final String apiBaseUrl;
    private final String level;
    private final String apiClientId;
    private final int fastLoadTtlSeconds;
    private final int fastLoadStaleSeconds;
    private final int fastLoadClientTtlSeconds;

    private Env(Dotenv dotenv) {
        this.port = intOf(dotenv, "PORT", 8300);
        this.domain = stringOf(dotenv, "DOMAIN", "localhost");
        this.apiBaseUrl = stringOf(dotenv, "API_BASE_URL", "");
        this.level = stringOf(dotenv, "LEVEL", "PROD");
        this.apiClientId = stringOf(dotenv, "API_CLIENT_ID", "koneko-web");
        this.fastLoadTtlSeconds = intOf(dotenv, "FASTLOAD_TTL_SECONDS", 15);
        this.fastLoadStaleSeconds = intOf(dotenv, "FASTLOAD_STALE_SECONDS", 120);
        this.fastLoadClientTtlSeconds = intOf(dotenv, "FASTLOAD_CLIENT_TTL_SECONDS", 600);
    }

    /** Loads {@code .env} from the working directory; a missing file is fine. */
    public static Env load() {
        Dotenv dotenv = Dotenv.configure().systemProperties().ignoreIfMissing().load();
        return new Env(dotenv);
    }

    /** FastLoad is off entirely when the server side window is zero. */
    public boolean isFastLoadEnabled() {
        return fastLoadTtlSeconds > 0 || fastLoadClientTtlSeconds > 0;
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
        String value = System.getenv(key);

        if (value == null || value.isBlank()) {
            value = dotenv.get(key);
        }

        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int intOf(Dotenv dotenv, String key, int fallback) {
        String raw = System.getenv(key);

        if (raw == null || raw.isBlank()) {
            raw = dotenv.get(key);
        }

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
