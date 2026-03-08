package org.ce.infrastructure.logging;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

/**
 * Central JUL (java.util.logging) configuration for the CE application.
 *
 * <p>Four logging levels are used:
 * <ul>
 *   <li>{@link Level#FINEST}  – method entry ([>>]) and exit ([<<])</li>
 *   <li>{@link Level#FINE}    – mid-method milestones, loop sweep statistics</li>
 *   <li>{@link Level#INFO}    – operation-level events (calculation started/done)</li>
 *   <li>{@link Level#WARNING} – recoverable problems (cache miss, convergence slow, swallowed exception)</li>
 * </ul>
 *
 * <p>Call {@link #configure(Level)} once at application startup before any Logger is obtained.
 * Default production level is {@code INFO}; pass {@code Level.FINEST} for a debug session.
 */
public final class LoggingConfig {

    private static final String ROOT_LOGGER = "org.ce";

    /** Format: {@code HH:mm:ss.SSS [LEVEL  ] logger.name - message} */
    private static final String FORMAT =
            "%1$tT.%1$tL [%4$-7s] %3$s - %5$s%n";

    private LoggingConfig() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Configures JUL for the entire {@code org.ce} hierarchy.
     *
     * <ul>
     *   <li>Removes any handlers inherited from the root logger (avoids duplicate console output).</li>
     *   <li>Attaches a {@link ConsoleHandler} at the requested level.</li>
     *   <li>Optionally attaches a rotating {@link FileHandler} writing to {@code logs/ce-<date>.log}.</li>
     * </ul>
     *
     * @param consoleLevel minimum level printed to console (e.g. {@code Level.INFO} or {@code Level.FINEST})
     */
    public static void configure(Level consoleLevel) {
        // Silence the JDK root logger so we don't get duplicate output
        Logger rootJdk = Logger.getLogger("");
        for (Handler h : rootJdk.getHandlers()) {
            rootJdk.removeHandler(h);
        }
        rootJdk.setLevel(Level.OFF);

        // Configure our hierarchy
        Logger ceLogger = Logger.getLogger(ROOT_LOGGER);
        ceLogger.setUseParentHandlers(false);
        ceLogger.setLevel(consoleLevel);

        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(consoleLevel);
        console.setFormatter(new CompactFormatter(FORMAT));
        ceLogger.addHandler(console);

        tryAddFileHandler(ceLogger, consoleLevel);

        ceLogger.config("Logging configured at " + consoleLevel.getName());
    }

    /**
     * Changes the active log level at runtime (e.g. from a GUI settings panel).
     *
     * @param level new minimum level
     */
    public static void setLevel(Level level) {
        Logger ceLogger = Logger.getLogger(ROOT_LOGGER);
        ceLogger.setLevel(level);
        for (Handler h : ceLogger.getHandlers()) {
            h.setLevel(level);
        }
    }

    /**
     * Returns a named logger for {@code clazz}.
     * Equivalent to {@code Logger.getLogger(clazz.getName())} but uses the canonical name,
     * ensuring it sits in the {@code org.ce} hierarchy configured by {@link #configure}.
     *
     * @param clazz owning class
     * @return configured Logger
     */
    public static Logger getLogger(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void tryAddFileHandler(Logger ceLogger, Level level) {
        try {
            java.io.File logDir = new java.io.File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String datePart = new SimpleDateFormat("yyyyMMdd").format(new Date());
            FileHandler file = new FileHandler("logs/ce-" + datePart + ".log",
                    /* append */ true);
            file.setLevel(level);
            file.setFormatter(new CompactFormatter(FORMAT));
            ceLogger.addHandler(file);
        } catch (IOException | SecurityException e) {
            // Not fatal — log to console only if file logging fails
            Logger.getLogger(LoggingConfig.class.getName())
                  .warning("Could not create file log handler: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Formatter
    // -------------------------------------------------------------------------

    /** Minimal formatter that applies a printf-style format string. */
    private static final class CompactFormatter extends Formatter {

        private final String pattern;

        CompactFormatter(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public String format(LogRecord record) {
            String loggerShort = abbreviate(record.getLoggerName());
            String thrown = "";
            if (record.getThrown() != null) {
                thrown = " [" + record.getThrown().getClass().getSimpleName()
                        + ": " + record.getThrown().getMessage() + "]";
            }
            return String.format(pattern,
                    new Date(record.getMillis()),
                    null,                           // unused %2$s (source)
                    loggerShort,
                    record.getLevel().getName(),
                    record.getMessage() + thrown);
        }

        /** Abbreviates {@code org.ce.domain.mcs.MCEngine} to {@code o.c.d.m.MCEngine}. */
        private static String abbreviate(String name) {
            if (name == null) return "?";
            String[] parts = name.split("\\.");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                sb.append(parts[i].charAt(0)).append('.');
            }
            sb.append(parts[parts.length - 1]);
            return sb.toString();
        }
    }
}
