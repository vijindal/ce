package org.ce.workbench.gui.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.ce.cvm.CVMPhaseModel;
import org.ce.workbench.backend.dto.CVMCalculationRequest;
import org.ce.workbench.backend.dto.CalculationResult;
import org.ce.workbench.backend.dto.MCSCalculationRequest;
import org.ce.workbench.backend.registry.SystemRegistry;
import org.ce.workbench.backend.job.BackgroundJobManager;
import org.ce.workbench.backend.job.CVMPhaseModelJob;
import org.ce.workbench.backend.job.MCSCalculationJob;
import org.ce.workbench.backend.service.CalculationService;
import org.ce.workbench.model.SystemIdentity;
import org.ce.workbench.util.context.MCSCalculationContext;

/**
 * Calculation setup panel with system selection and MCS/CVM inputs.
 * Shows either MCS or CVM parameters based on calculation type selection.
 * 
 * <p>CVM calculations are executed through the model-centric CVMPhaseModel path.</p>
 */
public class CalculationSetupPanel extends VBox {

    private final SystemRegistry registry;
    private final ResultsPanel resultsPanel;
    private final BackgroundJobManager jobManager;
    private final RadioButton mcsToggle;
    private final RadioButton cvmToggle;
    private final VBox parametersContainer;
    
    // Explicit system selection (set by SystemRegistryPanel)
    private SystemIdentity selectedSystem;
    private final Label selectedSystemLabel;
    
    // Common parameters
    private final TextField temperatureField;
    private final TextField compositionField;
    
    // MCS parameters
    private final TextField mcsSupercellSizeField;
    private final TextField mcsEquilibrationField;
    private final TextField mcsAveragingField;
    
    // CVM parameters
    private final TextField cvmToleranceField;
    
    public CalculationSetupPanel(SystemRegistry registry, BackgroundJobManager jobManager, ResultsPanel resultsPanel) {
        this.registry = registry;
        this.jobManager = jobManager;
        this.resultsPanel = resultsPanel;

        setSpacing(8);
        setPadding(new Insets(12));
        setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");
        setPrefHeight(300);

        // Title
        Label title = new Label("Calculation Setup");
        title.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");

        // Selected system display
        selectedSystemLabel = new Label("No system selected");
        selectedSystemLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666; -fx-font-style: italic;");

        // Calculation type toggle
        Label calcTypeLabel = new Label("Type");
        ToggleGroup calcTypeGroup = new ToggleGroup();
        mcsToggle = new RadioButton("MCS");
        cvmToggle = new RadioButton("CVM");
        mcsToggle.setToggleGroup(calcTypeGroup);
        cvmToggle.setToggleGroup(calcTypeGroup);
        mcsToggle.setSelected(true);
        mcsToggle.setStyle("-fx-font-size: 10;");
        cvmToggle.setStyle("-fx-font-size: 10;");

        HBox calcTypeRow = new HBox(15, calcTypeLabel, mcsToggle, cvmToggle);
        calcTypeLabel.setMinWidth(70);

        // Initialize common parameters
        temperatureField = new TextField("7.0");
        compositionField = new TextField("0.5");
        
        // Initialize MCS parameters
        mcsSupercellSizeField = new TextField("4");
        mcsEquilibrationField = new TextField("5000");
        mcsAveragingField = new TextField("10000");
        
        // Initialize CVM parameters
        cvmToleranceField = new TextField("1e-6");
        
        // Build common parameters section
        VBox commonSection = buildCommonParametersSection();

        // Build parameters container - will hold either MCS or CVM section
        parametersContainer = new VBox(6);
        parametersContainer.setPadding(new Insets(5, 0, 0, 0));
        updateParametersDisplay();

        // Toggle handler to switch between MCS and CVM
        mcsToggle.selectedProperty().addListener((obs, oldVal, newVal) -> updateParametersDisplay());
        cvmToggle.selectedProperty().addListener((obs, oldVal, newVal) -> updateParametersDisplay());

        // Action buttons
        HBox actionRow = buildActionButtons();

        // Add all components
        getChildren().addAll(
            title,
            selectedSystemLabel,
            calcTypeRow,
            new Separator(),
            commonSection,
            parametersContainer,
            new Separator(),
            actionRow
        );
        
        VBox.setVgrow(parametersContainer, Priority.SOMETIMES);
    }

    private VBox buildCommonParametersSection() {
        VBox section = new VBox(4);
        Label label = new Label("Common");
        label.setStyle("-fx-font-size: 10; -fx-font-weight: bold;");
        
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(0));
        
        temperatureField.setPrefHeight(22);
        compositionField.setPrefHeight(22);
        
        addCompactRow(grid, 0, "Temperature (K)", temperatureField);
        addCompactRow(grid, 1, "Composition (x)", compositionField);
        
        section.getChildren().addAll(label, grid);
        return section;
    }

    private VBox buildMcsParametersSection() {
        VBox section = new VBox(4);
        Label label = new Label("MCS Parameters");
        label.setStyle("-fx-font-size: 10; -fx-font-weight: bold;");
        
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(0));
        
        mcsSupercellSizeField.setPrefHeight(22);
        mcsEquilibrationField.setPrefHeight(22);
        mcsAveragingField.setPrefHeight(22);
        
        addCompactRow(grid, 0, "Supercell Size (L)", mcsSupercellSizeField);
        addCompactRow(grid, 1, "Equilibration", mcsEquilibrationField);
        addCompactRow(grid, 2, "Averaging", mcsAveragingField);
        
        section.getChildren().addAll(label, grid);
        return section;
    }

    private VBox buildCvmParametersSection() {
        VBox section = new VBox(4);
        Label label = new Label("CVM Parameters");
        label.setStyle("-fx-font-size: 10; -fx-font-weight: bold;");
        
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(0));
        
        cvmToleranceField.setPrefHeight(22);
        
        addCompactRow(grid, 0, "Tolerance", cvmToleranceField);
        
        section.getChildren().addAll(label, grid);
        return section;
    }

    private HBox buildActionButtons() {
        Button runButton = new Button("Run");
        Button resetButton = new Button("Reset");
        runButton.setStyle("-fx-font-size: 10; -fx-padding: 6 20;");
        resetButton.setStyle("-fx-font-size: 10; -fx-padding: 6 20;");
        
        // Run button handler - execute MCS or CVM
        runButton.setOnAction(e -> {
            if (mcsToggle.isSelected()) {
                runMCSCalculation();
            } else {
                runCVMCalculation();
            }
        });
        
        // Reset button handler
        resetButton.setOnAction(e -> {
            temperatureField.setText("7.0");
            compositionField.setText("0.5");
            mcsSupercellSizeField.setText("4");
            mcsEquilibrationField.setText("5000");
            mcsAveragingField.setText("10000");
            cvmToleranceField.setText("1e-6");
        });
        
        HBox row = new HBox(8, runButton, resetButton);
        return row;
    }
    
    private void runMCSCalculation() {
        if (selectedSystem == null) {
            showError("No System Selected", 
                "Please create a system in the System Setup section first.");
            return;
        }
        
        // Validate system has required data
        if (!registry.isClustersComputed(selectedSystem.getId())) {
            showError("System Not Ready", 
                "The selected system has not completed cluster identification.\n" +
                "Please wait for the identification pipeline to complete.");
            return;
        }
        
        // Build request from UI fields
        MCSCalculationRequest request;
        try {
            request = MCSCalculationRequest.builder()
                .systemId(selectedSystem.getId())
                .temperature(Double.parseDouble(temperatureField.getText().trim()))
                .composition(Double.parseDouble(compositionField.getText().trim()))
                .supercellSize(Integer.parseInt(mcsSupercellSizeField.getText().trim()))
                .equilibrationSteps(Integer.parseInt(mcsEquilibrationField.getText().trim()))
                .averagingSteps(Integer.parseInt(mcsAveragingField.getText().trim()))
                .build();
        } catch (IllegalArgumentException ex) {
            showError("Invalid Parameter", ex.getMessage());
            return;
        }
        
        // Create service with GUI listener
        ResultsPanelProgressListener listener = new ResultsPanelProgressListener(resultsPanel);
        CalculationService service = new CalculationService(registry, listener);
        
        // Prepare context (loads data from cache/database)
        CalculationResult<MCSCalculationContext> result = service.prepareMCS(request);
        
        if (result.isFailure()) {
            showError("MCS Preparation Failed", result.getErrorMessage().orElse("Unknown error"));
            return;
        }
        
        MCSCalculationContext context = result.getContextOrThrow();
        
        // Submit MCS job to BackgroundJobManager for managed execution
        MCSCalculationJob job = new MCSCalculationJob(context, listener);
        jobManager.submitJob(job);
    }

    /**
     * Sets the currently selected system for calculations.
     * Called by SystemRegistryPanel when a system is created or selected.
     *
     * @param system the system to use for calculations
     */
    public void setSelectedSystem(SystemIdentity system) {
        this.selectedSystem = system;
        if (system != null) {
            selectedSystemLabel.setText("System: " + system.getId());
            selectedSystemLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #006600; -fx-font-weight: bold;");
        } else {
            selectedSystemLabel.setText("No system selected");
            selectedSystemLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666; -fx-font-style: italic;");
        }
    }

    /**
     * Returns the currently selected system.
     */
    public SystemIdentity getSelectedSystem() {
        return selectedSystem;
    }

    private void runCVMCalculation() {
        if (selectedSystem == null) {
            showError("No System Selected", 
                "Please create a system in the System Setup section first.");
            return;
        }
        
        // Validate system has required data
        if (!registry.isCfsComputed(selectedSystem.getId())) {
            showError("System Not Ready", 
                "The selected system has not completed CF identification.\n" +
                "Please wait for the identification pipeline to complete.");
            return;
        }

        // Build request from UI fields
        CVMCalculationRequest request;
        try {
            request = CVMCalculationRequest.builder()
                .systemId(selectedSystem.getId())
                .temperature(Double.parseDouble(temperatureField.getText().trim()))
                .composition(Double.parseDouble(compositionField.getText().trim()))
                .tolerance(Double.parseDouble(cvmToleranceField.getText().trim()))
                .build();
        } catch (IllegalArgumentException ex) {
            showError("Invalid Parameter", ex.getMessage());
            return;
        }
        
        // Create service with GUI listener
        ResultsPanelProgressListener listener = new ResultsPanelProgressListener(resultsPanel);
        CalculationService service = new CalculationService(registry, listener);
        
        runCVMPhaseModelCalculation(request, service, listener);
    }
    
    /**
     * Runs CVM using the new model-centric approach (Phase Model).
     */
    private void runCVMPhaseModelCalculation(
            CVMCalculationRequest request,
            CalculationService service,
            ResultsPanelProgressListener listener) {
        
        // Prepare CVMPhaseModel
        CalculationResult<CVMPhaseModel> result = service.prepareCVMModel(request);
        
        if (result.isFailure()) {
            showError("CVM Phase Model Preparation Failed", 
                result.getErrorMessage().orElse("Unknown error"));
            return;
        }
        
        CVMPhaseModel model = result.getContextOrThrow();
        
        // Submit CVMPhaseModelJob to BackgroundJobManager for managed execution
        CVMPhaseModelJob job = new CVMPhaseModelJob(model, listener);
        jobManager.submitJob(job);
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateParametersDisplay() {
        parametersContainer.getChildren().clear();
        if (mcsToggle.isSelected()) {
            parametersContainer.getChildren().add(buildMcsParametersSection());
        } else {
            parametersContainer.getChildren().add(buildCvmParametersSection());
        }
    }

    private void addCompactRow(GridPane grid, int row, String labelText, Control field) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 9;");
        field.setMaxWidth(Double.MAX_VALUE);
        field.setStyle("-fx-font-size: 9;");
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(label, 0, row);
        grid.add(field, 1, row);
    }
}