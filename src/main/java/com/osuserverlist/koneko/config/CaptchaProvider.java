package com.osuserverlist.koneko.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum CaptchaProvider {

    /** No captcha at all. The registration form renders without one. */
    NONE("", "", ""),

    /** Cloudflare Turnstile. */
    TURNSTILE("cf-turnstile-response",
            "https://challenges.cloudflare.com/turnstile/v0/api.js",
            "turnstile"),

    /** Google reCAPTCHA v2, the checkbox widget. */
    RECAPTCHA("g-recaptcha-response",
            "https://www.google.com/recaptcha/api.js",
            "grecaptcha");

    private static final Logger logger = LoggerFactory.getLogger("CaptchaProvider");

    private final String field;
    private final String scriptUrl;
    private final String globalName;

    CaptchaProvider(String field, String scriptUrl, String globalName) {
        this.field = field;
        this.scriptUrl = scriptUrl;
        this.globalName = globalName;
    }

    public String getField() {
        return field;
    }

    public String getScriptUrl() {
        return scriptUrl;
    }

    public String getGlobalName() {
        return globalName;
    }

    public static CaptchaProvider of(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }

        String value = raw.trim().toLowerCase().replace('-', '_').replace(' ', '_');

        switch (value) {
            case "none", "off", "false", "disabled", "no" -> {
                return NONE;
            }
            case "turnstile", "cloudflare", "cf", "cf_turnstile" -> {
                return TURNSTILE;
            }
            case "recaptcha", "re_captcha", "google", "recaptcha_v2", "recaptcha2", "g_recaptcha" -> {
                return RECAPTCHA;
            }
            default -> {
                logger.warn("<{}> is not a known CAPTCHA_PROVIDER, running without a captcha", raw.trim());
                return NONE;
            }
        }
    }
}
