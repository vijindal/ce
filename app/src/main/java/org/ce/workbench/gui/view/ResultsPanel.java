package org.ce.workbench.gui.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Right panel for displaying all results, progress, and identification outputs.
 * Single unified panel for showing system creation progress, identification results, and calculation outputs.
 */
public class ResultsPanel extends VBox {
    
    private TextArea resultsArea;
    private ProgressBar progressBar;
    
    public ResultsPanel() {
        this.setSpacing(10);
        this.setPadding(new Insets(15));
        
        // Title
        Label titleLabel = new Label("Progress & Results");
        titleLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        
        // Progress bar
        progressBar = new ProgressBar(0);
        progressBar.setPrefHeight(20);
        
        // Results display area
        resultsArea = new TextArea();
        resultsArea.setWrapText(true);
        resultsArea.setEditable(false);
        resultsArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 9;");
        
        // Control buttons
        HBox controls = new HBox(5);
        Button clearButton = new Button("Clear Results");
        Button copyButton = new Button("Copy All");
        clearButton.setStyle("-fx-font-size: 10;");
        copyButton.setStyle("-fx-font-size: 10;");
        
        clearButton.setOnAction(e -> resultsArea.clear());
        copyButton.setOnAction(e -> {
            if (!resultsArea.getText().isEmpty()) {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(resultsArea.getText());
                clipboard.setContent(content);
            }
        });
        
        controls.getChildren().addAll(clearButton, copyButton);
        
        // Add all to layout
        this.getChildren().addAll(
            titleLabel,
            progressBar,
            new Label("Status & Output:"),
            resultsArea,
            controls
        );
        
        VBox.setVgrow(resultsArea, Priority.ALWAYS);
    }
    
    /**
     * Log a message to the results panel with timestamp.
     */
    public void logMessage(String message) {
        resultsArea.appendText("[" + java.time.LocalTime.now() + "] " + message + "\n");
    }
    
    /**
     * Update the progress bar.
     */
    public void setProgress(double progress) {
        progressBar.setProgress(progress);
    }
    
    /**
     * Clear all results.
     */
    public void clearResults() {
        resultsArea.clear();
        progressBar.setProgress(0);
    }
    
    /**
     * Get the current results text.
     */
    public String getResultsText() {
        return resultsArea.getText();
    }
    
    /**
     * Append text without timestamp.
     */
    public void appendText(String text) {
        resultsArea.appendText(text);
    }
}
