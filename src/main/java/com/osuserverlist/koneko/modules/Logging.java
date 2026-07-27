package com.osuserverlist.koneko.modules;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;
import io.javalin.http.Context;
import io.javalin.http.RequestLogger;

public class Logging {

    private static Logger logger = LoggerFactory.getLogger(Logging.class);

    public static class LoggerHighlighter extends ForegroundCompositeConverterBase<ILoggingEvent> {
        @Override
        protected String getForegroundColorCode(ILoggingEvent event) {
            return switch (event.getLevel().toInt()) {
                case Level.ERROR_INT -> "1;31"; // bold red
                case Level.WARN_INT -> "38;5;208"; // orange
                case Level.INFO_INT -> "38;5;194"; // bright green
                case Level.DEBUG_INT -> "38;5;245"; // gray
                default -> ANSIConstants.DEFAULT_FG;
            };
        }
    }

    public static class KonekoWebLogger implements RequestLogger {
        @Override
        public void handle(@NotNull Context ctx, @NotNull Float executionTimeMs) throws Exception {
            logger.info(String.format("%s %s - %s (%.2f ms)", ctx.method().toString(), ctx.url(),
                    ctx.status().toString(), executionTimeMs));
        }
    }

}
