package org.ce.workbench.gui.component;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.ce.workbench.util.MCSUpdate;

/**
 * Status panel for displaying MCS simulation metrics and progress.
 * Shows:
 * - Progress bars for equilibration and averaging phases
 * - Current energy values
 * - Convergence status with color indicator
 * - Acceptance rate and other diagnostics
 * - Estimated time remaining
 */
public class MCSStatusPanel extends VBox {
    
    private Label phaseLabel;
    private Label elapsedTimeLabel;
    private Label remainingTimeLabel;
    
    private ProgressBar progressEq;
    private Label stepCountEq;
    
    private ProgressBar progressAvg;
    private Label stepCountAvg;
    
    private Label energyLabel;
    private Label deltaELabel;
    private Label sigmaDELabel;
    private Label convergenceStatusLabel;
    
    private Circle convergenceIndicator;
    private Label acceptanceRateLabel;
    
    public MCSStatusPanel() {
        this.setSpacing(12);
        this.setPadding(new Insets(12));
        this.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1; " +
                     "-fx-background-color: #ffffff;");
        
        // Title
        Label titleLabel = new Label("MCS Simulation Status");
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1976D2;");
        this.getChildren().add(titleLabel);
        
        // Phase and timing info
        HBox phaseBox = createPhaseBox();
        this.getChildren().add(phaseBox);
        
        // Progress section
        VBox progressSection = createProgressSection();
        this.getChildren().add(progressSection);
        
        // Divider
        this.getChildren().add(createDivider());
        
        // Energy metrics section
        VBox energySection = createEnergySection();
        this.getChildren().add(energySection);
        
        // Convergence status
        HBox convergenceBox = createConvergenceBox();
        this.getChildren().add(convergenceBox);
        
        // Acceptance rate
        HBox acceptanceBox = createAcceptanceBox();
        this.getChildren().add(acceptanceBox);
    }
    
    private HBox createPhaseBox() {
        HBox hbox = new HBox(20);
        hbox.setPadding(new Insets(5, 0, 0, 0));
        
        phaseLabel = new Label("Status: IDLE");
        phaseLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        
        elapsedTimeLabel = new Label("Elapsed: --");
        elapsedTimeLabel.setStyle("-fx-font-size: 10px;");
        
        remainingTimeLabel = new Label("Est. Remaining: --");
        remainingTimeLabel.setStyle("-fx-font-size: 10px;");
        
        hbox.getChildren().addAll(phaseLabel, elapsedTimeLabel, remainingTimeLabel);
        return hbox;
    }
    
    private VBox createProgressSection() {
        VBox vbox = new VBox(8);
        
        // Equilibration progress
        Label eqLabel = new Label("Equilibration Phase:");
        eqLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        
        HBox eqBox = new HBox(10);
        progressEq = new ProgressBar(0);
        progressEq.setPrefHeight(16);
        progressEq.setStyle("-fx-accent: #1976D2;");
        stepCountEq = new Label("0 / 10000");
        stepCountEq.setStyle("-fx-font-size: 10px;");
        stepCountEq.setPrefWidth(80);
        eqBox.getChildren().addAll(progressEq, stepCountEq);
        HBox.setHgrow(progressEq, javafx.scene.layout.Priority.ALWAYS);
        
        // Averaging progress
        Label avgLabel = new Label("Averaging Phase:");
        avgLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        
        HBox avgBox = new HBox(10);
        progressAvg = new ProgressBar(0);
        progressAvg.setPrefHeight(16);
        progressAvg.setStyle("-fx-accent: #43A047;");
        stepCountAvg = new Label("0 / 10000");
        stepCountAvg.setStyle("-fx-font-size: 10px;");
        stepCountAvg.setPrefWidth(80);
        avgBox.getChildren().addAll(progressAvg, stepCountAvg);
        HBox.setHgrow(progressAvg, javafx.scene.layout.Priority.ALWAYS);
        
        vbox.getChildren().addAll(eqLabel, eqBox, avgLabel, avgBox);
        return vbox;
    }
    
    private VBox createEnergySection() {
        VBox vbox = new VBox(6);
        
        Label energySectionLabel = new Label("Energy Metrics:");
        energySectionLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(5);
        
        Label currentELabel = new Label("Current Energy:");
        currentELabel.setStyle("-fx-font-size: 10px;");
        energyLabel = new Label("--");
        energyLabel.setStyle("-fx-font-size: 10px; -fx-font-family: 'Courier New';");
        
        Label deltaEHeadLabel = new Label("Latest ΔE:");
        deltaEHeadLabel.setStyle("-fx-font-size: 10px;");
        deltaELabel = new Label("--");
        deltaELabel.setStyle("-fx-font-size: 10px; -fx-font-family: 'Courier New';");
        
        Label sigmaDEHeadLabel = new Label("σ(ΔE) [Stability]:");
        sigmaDEHeadLabel.setStyle("-fx-font-size: 10px;");
        sigmaDELabel = new Label("--");
        sigmaDELabel.setStyle("-fx-font-size: 10px; -fx-font-family: 'Courier New'; " +
                             "-fx-font-weight: bold;");
        
        grid.add(currentELabel, 0, 0);
        grid.add(energyLabel, 1, 0);
        grid.add(deltaEHeadLabel, 0, 1);
        grid.add(deltaELabel, 1, 1);
        grid.add(sigmaDEHeadLabel, 0, 2);
        grid.add(sigmaDELabel, 1, 2);
        
        vbox.getChildren().addAll(energySectionLabel, grid);
        return vbox;
    }
    
    private HBox createConvergenceBox() {
        HBox hbox = new HBox(8);
        hbox.setPadding(new Insets(5, 0, 0, 0));
        
        convergenceIndicator = new Circle(6);
        convergenceIndicator.setFill(Color.web("#999999"));  // Gray = unknown
        
        convergenceStatusLabel = new Label("Status: --");
        convergenceStatusLabel.setStyle("-fx-font-size: 10px;");
        
        hbox.getChildren().addAll(convergenceIndicator, convergenceStatusLabel);
        return hbox;
    }
    
    private HBox createAcceptanceBox() {
        HBox hbox = new HBox(8);
        
        Label accLabel = new Label("Acceptance Rate:");
        accLabel.setStyle("-fx-font-size: 10px;");
        
        acceptanceRateLabel = new Label("--");
        acceptanceRateLabel.setStyle("-fx-font-size: 10px; -fx-font-family: 'Courier New';");
        
        hbox.getChildren().addAll(accLabel, acceptanceRateLabel);
        return hbox;
    }
    
    private VBox createDivider() {
        VBox divider = new VBox();
        divider.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        divider.setPrefHeight(1);
        return divider;
    }
    
    /**
     * Update status display with new MCS data.
     * @param update MCSUpdate containing current metrics
     * @param totalEqSteps total equilibration steps expected
     * @param totalAvgSteps total averaging steps expected
     */
    public void updateStatus(MCSUpdate update, int totalEqSteps, int totalAvgSteps) {
        // Phase label
        String phaseStr = update.getPhase() == MCSUpdate.Phase.EQUILIBRATION 
            ? "EQUILIBRATING" : "AVERAGING";
        phaseLabel.setText("Status: " + phaseStr);
        
        // Timing
        long elapsedSec = update.getElapsedMs() / 1000;
        long elapsedMin = elapsedSec / 60;
        long elapsedSecRem = elapsedSec % 60;
        elapsedTimeLabel.setText(String.format("Elapsed: %dm %ds", elapsedMin, elapsedSecRem));
        
        // Progress
        if (update.getPhase() == MCSUpdate.Phase.EQUILIBRATION) {
            double progress = Math.min(1.0, (double) update.getStep() / totalEqSteps);
            progressEq.setProgress(progress);
            stepCountEq.setText(update.getStep() + " / " + totalEqSteps);
            progressAvg.setProgress(0);
            stepCountAvg.setText("0 / " + totalAvgSteps);
        } else {
            progressEq.setProgress(1.0);
            stepCountEq.setText(totalEqSteps + " / " + totalEqSteps);
            
            int avgStep = update.getStep() - totalEqSteps;
            double progress = Math.min(1.0, (double) avgStep / totalAvgSteps);
            progressAvg.setProgress(progress);
            stepCountAvg.setText(avgStep + " / " + totalAvgSteps);
        }
        
        // Energy metrics
        energyLabel.setText(String.format("%.6f J/mol", update.getE_total()));
        deltaELabel.setText(String.format("%.8f J/mol", update.getDeltaE()));
        sigmaDELabel.setText(String.format("%.6f J/mol", update.getSigmaDE()));
        
        // Convergence status and color
        var status = update.getConvergenceStatus();
        convergenceStatusLabel.setText("Convergence: " + update.getStatusLabel());
        convergenceIndicator.setFill(Color.web(update.getStatusColor()));
        
        // Acceptance rate
        acceptanceRateLabel.setText(String.format("%.1f%%", update.getAcceptanceRate() * 100));
        
        // Estimate remaining time (simple extrapolation)
        if (update.getStep() > 0) {
            long averageTimePerStep = update.getElapsedMs() / update.getStep();
            int stepsRemaining;
            if (update.getPhase() == MCSUpdate.Phase.EQUILIBRATION) {
                stepsRemaining = totalEqSteps - update.getStep();
            } else {
                stepsRemaining = totalAvgSteps - (update.getStep() - totalEqSteps);
            }
            long remainingMs = stepsRemaining * averageTimePerStep;
            long remainingSec = remainingMs / 1000;
            long remainingMin = remainingSec / 60;
            long remainingSecRem = remainingSec % 60;
            remainingTimeLabel.setText(String.format("Est. Remaining: %dm %ds", 
                remainingMin, remainingSecRem));
        }
    }
    
    /**
     * Reset status panel to initial state.
     */
    public void reset() {
        phaseLabel.setText("Status: IDLE");
        elapsedTimeLabel.setText("Elapsed: --");
        remainingTimeLabel.setText("Est. Remaining: --");
        
        progressEq.setProgress(0);
        stepCountEq.setText("0 / ?");
        
        progressAvg.setProgress(0);
        stepCountAvg.setText("0 / ?");
        
        energyLabel.setText("--");
        deltaELabel.setText("--");
        sigmaDELabel.setText("--");
        convergenceStatusLabel.setText("Status: --");
        convergenceIndicator.setFill(Color.web("#999999"));
        acceptanceRateLabel.setText("--");
    }
}
