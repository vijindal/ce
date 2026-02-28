package org.ce.workbench.gui.view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.ce.workbench.util.MCSUpdate;

/**
 * Results panel with vertical split: graphical output (top) and text output (bottom).
 * Simple, generic design that works for various calculation types.
 * 
 * For MCS: Shows Energy vs MCS plot in chart, detailed metrics in text.
 */
public class ResultsPanel extends VBox {
    
    private SplitPane splitPane;
    
    // Top: Graphical area
    private LineChart<Number, Number> energyChart;
    private XYChart.Series<Number, Number> energySeries;
    
    // Bottom: Text output area
    private TextArea outputArea;
    
    // Control buttons
    private Button pauseButton;
    private Button resumeButton;
    private Button cancelButton;
    private Button clearButton;
    
    // State tracking
    private Runnable onPauseCallback;
    private Runnable onResumeCallback;
    private Runnable onCancelCallback;
    private boolean isPaused = false;
    private long mcsDataPointCount = 0;
    
    public ResultsPanel() {
        this.setSpacing(10);
        this.setPadding(new Insets(12));
        this.setStyle("-fx-background-color: #ffffff;");
        
        // Title
        HBox titleBar = createTitleBar();
        this.getChildren().add(titleBar);
        
        // Vertical split pane: top (graphical) / bottom (text)
        splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.setDividerPosition(0, 0.4);  // 40% for chart, 60% for text
        
        // Top: Chart area
        VBox chartContainer = createChartArea();
        
        // Bottom: Text output area
        VBox textContainer = createTextArea();
        
        splitPane.getItems().addAll(chartContainer, textContainer);
        this.getChildren().add(splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        
        // Control buttons
        HBox controlBar = createControlBar();
        this.getChildren().add(controlBar);
    }
    
    private HBox createTitleBar() {
        HBox hbox = new HBox(10);
        hbox.setPadding(new Insets(0, 0, 8, 0));
        
        Label titleLabel = new Label("Results & Visualization");
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        
        hbox.getChildren().add(titleLabel);
        return hbox;
    }
    
    private VBox createChartArea() {
        VBox chartContainer = new VBox();
        chartContainer.setPadding(new Insets(10));
        chartContainer.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        
        // Number axes
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("MC Sweep");
        xAxis.setAutoRanging(true);
        
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Energy (eV)");
        yAxis.setAutoRanging(true);
        
        // Create chart
        energyChart = new LineChart<>(xAxis, yAxis);
        energyChart.setTitle("Energy vs MCS");
        energyChart.setAnimated(false);
        energyChart.setCreateSymbols(false);
        energyChart.setLegendVisible(true);
        energyChart.setStyle("-fx-font-size: 10;");
        
        // Data series
        energySeries = new XYChart.Series<>();
        energySeries.setName("Total Energy");
        energyChart.getData().add(energySeries);
        
        chartContainer.getChildren().add(energyChart);
        VBox.setVgrow(energyChart, Priority.ALWAYS);
        
        return chartContainer;
    }
    
    private VBox createTextArea() {
        VBox textContainer = new VBox(8);
        textContainer.setPadding(new Insets(10));
        
        Label label = new Label("Detailed Output:");
        label.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        
        outputArea = new TextArea();
        outputArea.setWrapText(true);
        outputArea.setEditable(false);
        outputArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 9;");
        
        textContainer.getChildren().addAll(label, outputArea);
        VBox.setVgrow(outputArea, Priority.ALWAYS);
        
        return textContainer;
    }
    
    private HBox createControlBar() {
        HBox hbox = new HBox(5);
        hbox.setPadding(new Insets(5, 0, 0, 0));
        
        pauseButton = new Button("Pause");
        resumeButton = new Button("Resume");
        cancelButton = new Button("Cancel");
        clearButton = new Button("Clear");
        
        pauseButton.setStyle("-fx-font-size: 10;");
        resumeButton.setStyle("-fx-font-size: 10;");
        cancelButton.setStyle("-fx-font-size: 10;");
        clearButton.setStyle("-fx-font-size: 10;");
        
        pauseButton.setOnAction(e -> handlePause());
        resumeButton.setOnAction(e -> handleResume());
        cancelButton.setOnAction(e -> handleCancel());
        clearButton.setOnAction(e -> clearAll());
        
        hbox.getChildren().addAll(pauseButton, resumeButton, cancelButton, clearButton);
        
        return hbox;
    }
    
    // ===== MCS Monitoring Methods =====
    
    /**
     * Update chart with new MCS data.
     * Thread-safe, can be called from background threads.
     */
    public void updateMCSData(MCSUpdate update) {
        if (Platform.isFxApplicationThread()) {
            updateMCSDataUI(update);
        } else {
            Platform.runLater(() -> updateMCSDataUI(update));
        }
    }
    
    private void updateMCSDataUI(MCSUpdate update) {
        mcsDataPointCount++;
        
        // Sample chart updates every 10 sweeps to avoid expensive redraws
        if (update.getStep() % 10 == 0) {
            energySeries.getData().add(new XYChart.Data<>(update.getStep(), update.getE_total()));
            
            // Aggressive pruning: keep only recent 300 points (avoids ~10k point redraws)
            if (energySeries.getData().size() > 300) {
                energySeries.getData().remove(0, 50);
            }
        }
        
        // Sampled text output every 50 sweeps (not every update)
        if (update.getStep() % 50 == 0) {
            String output = String.format(
                "[Sweep %d] E=%.6f eV | ΔE=%.8f | σ(ΔE)=%.6f | Accept=%.1f%% | %s | Time=%dms%n",
                update.getStep(),
                update.getE_total(),
                update.getDeltaE(),
                update.getSigmaDE(),
                update.getAcceptanceRate() * 100,
                update.getStatusLabel(),
                update.getElapsedMs()
            );
            outputArea.appendText(output);
            
            // Auto-scroll to bottom
            Platform.runLater(() -> outputArea.setScrollTop(Double.MAX_VALUE));
        }
    }
    
    /**
     * Initialize MCS monitoring for a new run.
     */
    public void initializeMCS(int eqSteps, int avgSteps, long seed) {
        Platform.runLater(() -> {
            energySeries.getData().clear();
            outputArea.clear();
            mcsDataPointCount = 0;
            isPaused = false;
            updateButtonStates();
            
            appendText(String.format(
                "=== MCS Simulation Started ===\n" +
                "Seed: %d\n" +
                "Equilibration: %d sweeps\n" +
                "Averaging: %d sweeps\n" +
                "Total: %d sweeps\n\n",
                seed, eqSteps, avgSteps, eqSteps + avgSteps
            ));
        });
    }
    
    // ===== Traditional Output Methods =====
    
    /**
     * Log a message with timestamp.
     */
    public void logMessage(String message) {
        appendText("[" + java.time.LocalTime.now() + "] " + message + "\n");
    }
    
    /**
     * Append text without timestamp.
     */
    public void appendText(String text) {
        if (Platform.isFxApplicationThread()) {
            outputArea.appendText(text);
            Platform.runLater(() -> outputArea.setScrollTop(Double.MAX_VALUE));
        } else {
            Platform.runLater(() -> {
                outputArea.appendText(text);
                outputArea.setScrollTop(Double.MAX_VALUE);
            });
        }
    }
    
    /**
     * Get the current output text.
     */
    public String getOutputText() {
        return outputArea.getText();
    }
    
    /**
     * Set progress value (for compatibility with existing code).
     * Note: Progress bar was removed from simplified UI.
     */
    public void setProgress(double progress) {
        // No-op: progress tracking removed from simplified vertical split layout
    }
    
    // ===== Control Methods =====
    
    private void handlePause() {
        isPaused = true;
        updateButtonStates();
        if (onPauseCallback != null) {
            onPauseCallback.run();
        }
        appendText(">>> Simulation PAUSED\n");
    }
    
    private void handleResume() {
        isPaused = false;
        updateButtonStates();
        if (onResumeCallback != null) {
            onResumeCallback.run();
        }
        appendText(">>> Simulation RESUMED\n");
    }
    
    private void handleCancel() {
        if (onCancelCallback != null) {
            onCancelCallback.run();
        }
        appendText(">>> Simulation CANCELLED\n");
        pauseButton.setDisable(true);
        resumeButton.setDisable(true);
        cancelButton.setDisable(true);
    }
    
    private void clearAll() {
        outputArea.clear();
        energySeries.getData().clear();
        mcsDataPointCount = 0;
        updateButtonStates();
    }
    
    private void updateButtonStates() {
        pauseButton.setDisable(isPaused);
        resumeButton.setDisable(!isPaused);
    }
    
    // ===== Callback Registration =====
    
    public void setOnPauseCallback(Runnable callback) {
        this.onPauseCallback = callback;
    }
    
    public void setOnResumeCallback(Runnable callback) {
        this.onResumeCallback = callback;
    }
    
    public void setOnCancelCallback(Runnable callback) {
        this.onCancelCallback = callback;
    }
    
    public void clearResults() {
        clearAll();
    }
}
