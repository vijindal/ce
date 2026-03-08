package org.ce.presentation.gui.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.ce.infrastructure.logging.GuiLogHandler;
import org.ce.infrastructure.logging.LoggingConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX panel that displays live JUL log output in a TextArea.
 *
 * <p>The panel registers a {@link GuiLogHandler} on the {@code org.ce} logger at construction
 * time. Log records are delivered to the TextArea in batches every ~100 ms via
 * {@link javafx.application.Platform#runLater}, keeping the UI responsive during
 * FINEST-level calculation traces.</p>
 *
 * <h2>Controls</h2>
 * <ul>
 *   <li><b>Level ComboBox</b> — changes the active log level at runtime via
 *       {@link LoggingConfig#setLevel(Level)}</li>
 *   <li><b>Clear</b> — clears the TextArea</li>
 *   <li><b>Line counter</b> — shows the current line count; capped at {@value #MAX_LINES}</li>
 * </ul>
 *
 * <p>Call {@link #shutdown()} when the owning window closes to stop the background
 * flush thread and remove the handler from the logger.</p>
 */
public class LogConsolePanel extends VBox {

    private static final int MAX_LINES  = 5_000;
    private static final int TRIM_LINES = 1_000; // lines to drop when cap is reached

    private final TextArea      logArea;
    private final Label         lineCountLabel;
    private final GuiLogHandler handler;

    private int lineCount = 0;

    /**
     * Constructs the panel and immediately starts capturing log output.
     *
     * @param initialLevel the log level to apply on startup (typically the value parsed from
     *                     the {@code --log-level} command-line flag)
     */
    public LogConsolePanel(Level initialLevel) {
        // Assign final fields FIRST so lambdas defined below can reference them.
        lineCountLabel = new Label("Lines: 0");
        lineCountLabel.setStyle("-fx-text-fill: #888;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 9;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        handler = new GuiLogHandler(this::appendBatch, initialLevel);
        Logger.getLogger("org.ce").addHandler(handler);

        // -- VBox layout ----------------------------------------------------
        setSpacing(4);
        setPadding(new Insets(8));
        setStyle("-fx-background-color: #fafafa;");

        // -- Toolbar --------------------------------------------------------
        ComboBox<Level> levelBox = new ComboBox<>(FXCollections.observableArrayList(
                Level.WARNING, Level.INFO, Level.FINE, Level.FINEST));
        levelBox.setValue(initialLevel);
        levelBox.setConverter(new StringConverter<>() {
            @Override public String toString(Level l)    { return l == null ? "" : l.getName(); }
            @Override public Level  fromString(String s) { return Level.parse(s); }
        });
        levelBox.setOnAction(e -> {
            Level chosen = levelBox.getValue();
            if (chosen != null) {
                LoggingConfig.setLevel(chosen);
                handler.setLevel(chosen);
            }
        });

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> {
            logArea.clear();
            lineCount = 0;
            lineCountLabel.setText("Lines: 0");
        });

        HBox toolbar = new HBox(8, new Label("Level:"), levelBox, clearButton, lineCountLabel);
        toolbar.setPadding(new Insets(0, 0, 4, 0));

        getChildren().addAll(toolbar, logArea);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Releases resources: stops the background flush thread and removes the handler
     * from the {@code org.ce} logger.  Call this from the application's {@code stop()} method.
     */
    public void shutdown() {
        Logger.getLogger("org.ce").removeHandler(handler);
        handler.close();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Called on the JavaFX Application Thread by {@link GuiLogHandler} with a batch
     * of already-formatted log lines.
     */
    private void appendBatch(String text) {
        int newLines = countNewlines(text);
        logArea.appendText(text);
        lineCount += newLines;
        lineCountLabel.setText("Lines: " + lineCount);

        if (lineCount > MAX_LINES) {
            trimOldLines();
        }

        // Auto-scroll to bottom
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    /** Removes the oldest {@value #TRIM_LINES} lines from the TextArea. */
    private void trimOldLines() {
        String content = logArea.getText();
        int pos = 0;
        int found = 0;
        while (pos < content.length() && found < TRIM_LINES) {
            if (content.charAt(pos) == '\n') found++;
            pos++;
        }
        if (pos < content.length()) {
            logArea.setText(content.substring(pos));
            lineCount = MAX_LINES - TRIM_LINES;
        }
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }
}
