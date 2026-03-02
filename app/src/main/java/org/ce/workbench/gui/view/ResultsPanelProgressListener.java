package org.ce.workbench.gui.view;

import org.ce.workbench.backend.service.CalculationProgressListener;
import org.ce.workbench.util.mcs.MCSUpdate;

/**
 * Adapter that wraps ResultsPanel to implement CalculationProgressListener.
 * 
 * <p>Bridges the service layer to the GUI by delegating progress
 * callbacks to the ResultsPanel.</p>
 */
public class ResultsPanelProgressListener implements CalculationProgressListener {
    
    private final ResultsPanel resultsPanel;
    
    /**
     * Creates a new adapter wrapping the given ResultsPanel.
     * 
     * @param resultsPanel the panel to delegate to
     */
    public ResultsPanelProgressListener(ResultsPanel resultsPanel) {
        this.resultsPanel = resultsPanel;
    }
    
    @Override
    public void logMessage(String message) {
        resultsPanel.logMessage(message);
    }
    
    @Override
    public void setProgress(double progress) {
        resultsPanel.setProgress(progress);
    }
    
    @Override
    public void initializeMCS(int equilibrationSteps, int averagingSteps, long seed) {
        resultsPanel.initializeMCS(equilibrationSteps, averagingSteps, seed);
    }
    
    @Override
    public void updateMCSData(MCSUpdate update) {
        resultsPanel.updateMCSData(update);
    }
    
    /**
     * Returns the underlying ResultsPanel.
     */
    public ResultsPanel getResultsPanel() {
        return resultsPanel;
    }
}
