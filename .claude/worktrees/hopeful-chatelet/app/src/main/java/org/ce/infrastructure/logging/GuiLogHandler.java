package org.ce.infrastructure.logging;

import javafx.application.Platform;

import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A JUL {@link Handler} that feeds log records into a JavaFX TextArea in batches.
 *
 * <p>Log records are collected into a {@link ConcurrentLinkedQueue} on whichever thread
 * calls {@link #publish(LogRecord)} (typically a background calculation thread).
 * A daemon scheduler fires every 100 ms, drains the queue, and dispatches a single
 * {@link Platform#runLater} call — preventing the JavaFX event queue from being flooded
 * during FINEST-level calculation traces.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * GuiLogHandler handler = new GuiLogHandler(textArea::appendText, Level.INFO);
 * Logger.getLogger("org.ce").addHandler(handler);
 * // ... later on shutdown:
 * handler.close();
 * }</pre>
 */
public final class GuiLogHandler extends Handler {

    /** Format: {@code HH:mm:ss.SSS [LEVEL  ] logger.name - message} */
    private static final String FORMAT = "%1$tT.%1$tL [%4$-7s] %3$s - %5$s%n";

    private final ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService flusher;
    private final Consumer<String> textAppender;

    /**
     * Constructs the handler.
     *
     * @param textAppender callback invoked on the JavaFX Application Thread with batched text
     *                     (e.g. {@code textArea::appendText})
     * @param level        minimum log level to capture
     */
    public GuiLogHandler(Consumer<String> textAppender, Level level) {
        this.textAppender = textAppender;
        setLevel(level);
        setFormatter(new GuiFormatter(FORMAT));

        flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gui-log-flusher");
            t.setDaemon(true);
            return t;
        });
        flusher.scheduleAtFixedRate(this::flush, 100, 100, TimeUnit.MILLISECONDS);
    }

    // -------------------------------------------------------------------------
    // Handler contract
    // -------------------------------------------------------------------------

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) return;
        buffer.add(getFormatter().format(record));
    }

    @Override
    public void flush() {
        if (buffer.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = buffer.poll()) != null) {
            sb.append(line);
        }
        String text = sb.toString();
        Platform.runLater(() -> textAppender.accept(text));
    }

    @Override
    public void close() {
        flusher.shutdown();
        try {
            flusher.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        flush(); // drain any remainder
    }

    // -------------------------------------------------------------------------
    // Formatter
    // -------------------------------------------------------------------------

    /** Same compact format used by LoggingConfig's ConsoleHandler/FileHandler. */
    private static final class GuiFormatter extends Formatter {

        private final String pattern;

        GuiFormatter(String pattern) {
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
                    null,
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
