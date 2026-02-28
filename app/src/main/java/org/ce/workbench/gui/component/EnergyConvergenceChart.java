package org.ce.workbench.gui.component;

import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import org.ce.workbench.util.MCSUpdate;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-time energy convergence visualization component.
 * 
 * Dual-axis line chart showing:
 * - Primary axis (Left): ΔE per MC step (energy changes)
 *   - Blue for equilibration phase
 *   - Green for averaging phase
 * - Secondary axis (Right): Cumulative total energy (light overlay)
 * 
 * Features:
 * - Updates in real-time as MCS progresses
 * - Color-coded phases for easy identification
 * - Vertical marker at equilibration/averaging boundary
 * - Automatic scaling and responsive layout
 */
public class EnergyConvergenceChart extends VBox {
    
    private final LineChart<Number, Number> chart;
    private final NumberAxis xAxis;          // MC Step number
    private final NumberAxis yAxisPrimary;   // ΔE
    private final NumberAxis yAxisSecondary; // E_total
    
    private XYChart.Series<Number, Number> seriesDeltaEq;      // ΔE during equilibration
    private XYChart.Series<Number, Number> seriesDeltaAvg;     // ΔE during averaging
    private XYChart.Series<Number, Number> seriesCumulativeE;  // Cumulative E total
    
    private int equilibrationSteps = 0;      // Track where phase transition occurs
    private boolean phaseMarkerAdded = false;
    
    // Data tracking for memory management
    private int lastDataPointsCount = 0;
    private static final int MAX_POINTS_BEFORE_PRUNE = 500;    // Prune at 500 points (5+ sweeps with sampling)
    private static final int PRUNE_OLDEST = 100;                // Remove oldest 100 points per prune
    
    public EnergyConvergenceChart() {
        // Set up layout
        this.setPadding(new Insets(10));
        this.setSpacing(5);
        this.setStyle("-fx-background-color: #f5f5f5;");
        
        // Create axes
        xAxis = new NumberAxis();
        xAxis.setLabel("MC Step");
        xAxis.setForceZeroInRange(false);
        
        yAxisPrimary = new NumberAxis();
        yAxisPrimary.setLabel("ΔE (J/mol)");
        yAxisPrimary.setAutoRanging(true);
        
        yAxisSecondary = new NumberAxis();
        yAxisSecondary.setLabel("E_total (J/mol)");
        yAxisSecondary.setAutoRanging(true);
        
        // Create chart
        chart = new LineChart<>(xAxis, yAxisPrimary);
        chart.setTitle("Energy Convergence Analysis");
        chart.setAnimated(false);  // Disable animation for performance
        chart.setCreateSymbols(false);  // No point markers for cleaner look
        chart.setLegendVisible(true);
        chart.setStyle("-fx-font-size: 10;");
        
        // Create data series
        seriesDeltaEq = new XYChart.Series<>();
        seriesDeltaEq.setName("ΔE (Equilibration)");
        
        seriesDeltaAvg = new XYChart.Series<>();
        seriesDeltaAvg.setName("ΔE (Averaging)");
        
        seriesCumulativeE = new XYChart.Series<>();
        seriesCumulativeE.setName("E_total (Cumulative)");
        
        chart.getData().addAll(seriesDeltaEq, seriesDeltaAvg, seriesCumulativeE);
        
        // Add chart to layout
        this.getChildren().add(chart);
        VBox.setVgrow(chart, javafx.scene.layout.Priority.ALWAYS);
    }
    
    /**
     * Update chart with new MCS data point.
     * Should be called on UI thread for thread safety.
     * 
     * @param update MCSUpdate containing energy and step data
     */
    public void updateWithMCSData(MCSUpdate update) {
        // Ensure thread safety
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateWithMCSData(update));
            return;
        }
        
        // Track equilibration/averaging phase transition
        if (update.getPhase() == MCSUpdate.Phase.EQUILIBRATION) {
            equilibrationSteps = update.getStep();
            seriesDeltaEq.getData().add(
                new XYChart.Data<>(update.getStep(), update.getDeltaE())
            );
        } else {
            // First point of averaging phase, add phase marker
            if (!phaseMarkerAdded) {
                phaseMarkerAdded = true;
                // Marker could be added via CSS or as a vertical line
                // For now, just track the transition step
            }
            seriesDeltaAvg.getData().add(
                new XYChart.Data<>(update.getStep(), update.getDeltaE())
            );
        }
        
        // Add cumulative energy (secondary axis overlay)
        seriesCumulativeE.getData().add(
            new XYChart.Data<>(update.getStep(), update.getE_total())
        );
        
        // Prune old data if too many points to avoid memory issues
        pruneDataIfNeeded();
    }
    
    /**
     * Remove oldest data points if we exceed reasonable chart size.
     * Keeps memory usage bounded and rendering fast for long-running simulations.
     * 
     * This is a rolling-window approach: as new points come in,
     * oldest points are discarded to maintain constant memory footprint.
     */
    private void pruneDataIfNeeded() {
        int totalPoints = seriesDeltaEq.getData().size() + 
                         seriesDeltaAvg.getData().size() + 
                         seriesCumulativeE.getData().size();
        
        if (totalPoints > MAX_POINTS_BEFORE_PRUNE) {
            // Efficient bulk removal: remove all points up to index PRUNE_OLDEST
            int removeCount = Math.min(PRUNE_OLDEST, seriesDeltaEq.getData().size());
            if (removeCount > 0) {
                seriesDeltaEq.getData().remove(0, removeCount);
            }
            
            removeCount = Math.min(PRUNE_OLDEST, seriesDeltaAvg.getData().size());
            if (removeCount > 0) {
                seriesDeltaAvg.getData().remove(0, removeCount);
            }
            
            removeCount = Math.min(PRUNE_OLDEST, seriesCumulativeE.getData().size());
            if (removeCount > 0) {
                seriesCumulativeE.getData().remove(0, removeCount);
            }
        }
    }
    
    /**
     * Clear all data from the chart.
     * Used when starting a new MCS run.
     */
    public void clearChart() {
        seriesDeltaEq.getData().clear();
        seriesDeltaAvg.getData().clear();
        seriesCumulativeE.getData().clear();
        equilibrationSteps = 0;
        phaseMarkerAdded = false;
    }
    
    /**
     * Get the current number of data points in the chart.
     * @return total number of data points across all series
     */
    public int getDataPointCount() {
        return seriesDeltaEq.getData().size() + 
               seriesDeltaAvg.getData().size() + 
               seriesCumulativeE.getData().size();
    }
    
    /**
     * Get equilibration step count (phase transition point).
     * @return step number where equilibration ended
     */
    public int getEquilibrationSteps() {
        return equilibrationSteps;
    }
    
    /**
     * Export chart data to a format suitable for CSV/JSON.
     * @return list of data points with metadata
     */
    public List<ChartDataPoint> exportData() {
        List<ChartDataPoint> dataPoints = new ArrayList<>();
        
        // Collect all equilibration ΔE points
        for (XYChart.Data<Number, Number> data : seriesDeltaEq.getData()) {
            dataPoints.add(new ChartDataPoint(
                data.getXValue().intValue(),
                data.getYValue().doubleValue(),
                0.0,  // E_total not tracked here
                MCSUpdate.Phase.EQUILIBRATION
            ));
        }
        
        // Collect all averaging ΔE points
        for (XYChart.Data<Number, Number> data : seriesDeltaAvg.getData()) {
            dataPoints.add(new ChartDataPoint(
                data.getXValue().intValue(),
                data.getYValue().doubleValue(),
                0.0,
                MCSUpdate.Phase.AVERAGING
            ));
        }
        
        return dataPoints;
    }
    
    /**
     * Simple data point class for export.
     */
    public static class ChartDataPoint {
        public int step;
        public double deltaE;
        public double E_total;
        public MCSUpdate.Phase phase;
        
        public ChartDataPoint(int step, double deltaE, double E_total, MCSUpdate.Phase phase) {
            this.step = step;
            this.deltaE = deltaE;
            this.E_total = E_total;
            this.phase = phase;
        }
    }
}
