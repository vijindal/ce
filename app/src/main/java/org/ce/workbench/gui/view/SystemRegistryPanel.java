package org.ce.workbench.gui.view;

import javafx.geometry.Insets;
import javafx.scene.control.*; 
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox; 
import javafx.scene.layout.GridPane; 
import javafx.scene.layout.Priority; 
import org.ce.workbench.backend.job.BackgroundJobManager;
import org.ce.workbench.backend.registry.SystemRegistry;
import org.ce.workbench.backend.job.BackgroundJob;
import org.ce.workbench.gui.model.SystemInfo;
import org.ce.workbench.backend.data.SystemDataLoader;
import org.ce.identification.engine.Vector3D;
import org.ce.workbench.backend.job.CFIdentificationJob;
import org.ce.workbench.backend.job.ClusterIdentificationJob;
import org.ce.workbench.util.StructureModelMapping;
import org.ce.workbench.util.ClusterDataCache;
import org.ce.identification.engine.ClusCoordListResult;

import java.util.Collection;
import java.util.Optional;

/**
 * Left panel for system registry and background job management.
 * Displays registered systems with caching status and active job queue.
 */
public class SystemRegistryPanel extends VBox {
    
    private final SystemRegistry registry;
    private final BackgroundJobManager jobManager;
    private final ResultsPanel resultsPanel;
    
    // Cache identification input to avoid asking twice
    private IdentificationInput cachedIdentificationInput;
    
    public SystemRegistryPanel(SystemRegistry registry, BackgroundJobManager jobManager, ResultsPanel resultsPanel) {
        this.registry = registry;
        this.jobManager = jobManager;
        this.resultsPanel = resultsPanel;
        
        this.setSpacing(6);
        this.setPadding(new Insets(8));
        this.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 0 1 0 0;");
        
        // Build UI components - left panel only
        VBox systemSection = createSystemSection();
        
        // Add to layout (no splitter needed - entire left panel is system section)
        this.getChildren().add(systemSection);
        
        // Initialize data
        setupJobMonitoring();
    }
    
    private VBox createSystemSection() {
        VBox vbox = new VBox(10);
        
        Label titleLabel = new Label("System Setup");
        titleLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        
        // Create a scroll pane for the form
        ScrollPane formScroll = new ScrollPane();
        formScroll.setFitToWidth(true);
        
        // Main form container
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-padding: 10;");
        
        // Elements field
        TextField elementsField = new TextField();
        elementsField.setText("Ti-Nb");
        Label elementsHelp = new Label("Format: Element1-Element2 (e.g., Ti-Nb, Fe-Ni-Cr)");
        elementsHelp.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");
        
        // Structure/Phase field
        TextField structurePhaseField = new TextField();
        structurePhaseField.setText("BCC_A2");
        Label structureHelp = new Label("Format: Structure_Phase (e.g., BCC_A2, FCC_L12)");
        structureHelp.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");
        
        // Model field
        TextField modelField = new TextField();
        modelField.setText("T");
        Label modelHelp = new Label("CVM approximation (e.g., T=tetrahedron, P=pair)");
        modelHelp.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");
        
        int row = 0;
        grid.add(new Label("Elements:"), 0, row);
        grid.add(elementsField, 1, row++);
        grid.add(elementsHelp, 1, row++);
        
        grid.add(new Label("Structure/Phase:"), 0, row);
        grid.add(structurePhaseField, 1, row++);
        grid.add(structureHelp, 1, row++);
        
        grid.add(new Label("Model:"), 0, row);
        grid.add(modelField, 1, row++);
        grid.add(modelHelp, 1, row++);
        
        grid.add(new Separator(), 0, row, 2, 1);
        row++;
        
        // Create/Clear buttons
        HBox formButtons = new HBox(10);
        Button createButton = new Button("Create System");
        Button clearButton = new Button("Clear");
        createButton.setStyle("-fx-font-size: 11; -fx-padding: 5 15;");
        clearButton.setStyle("-fx-font-size: 11; -fx-padding: 5 15;");
        formButtons.getChildren().addAll(createButton, clearButton);
        
        grid.add(new Separator(), 0, row, 2, 1);
        row++;
        grid.add(formButtons, 0, row, 2, 1);
        
        formScroll.setContent(grid);
        
        // Add calculation setup section
        CalculationSetupPanel calcSetupPanel = new CalculationSetupPanel(registry, jobManager, resultsPanel);
        
        vbox.getChildren().addAll(titleLabel, formScroll, new Separator(), calcSetupPanel);
        VBox.setVgrow(formScroll, Priority.SOMETIMES);
        VBox.setVgrow(calcSetupPanel, Priority.ALWAYS);
        
        // Create System button handler with full availability checking
        createButton.setOnAction(e -> {
            // Clear any cached identification input from previous system
            cachedIdentificationInput = null;
            
            String elements = elementsField.getText().trim();
            String structurePhase = structurePhaseField.getText().trim();
            String model = modelField.getText().trim();
            
            if (elements.isEmpty() || structurePhase.isEmpty() || model.isEmpty()) {
                showAlert("Missing Fields", "All fields are required.");
                return;
            }
            
            // Validate structure/phase format
            if (!StructureModelMapping.isValidStructurePhase(structurePhase)) {
                showAlert("Invalid Format", "Structure/Phase must be in format: Structure_Phase (e.g., BCC_A2)\n" +
                    "Known phases: " + String.join(", ", StructureModelMapping.getKnownPhases()));
                return;
            }
            
            String[] parts = structurePhase.split("_");
            String structure = parts[0];
            String phase = parts[1];
            String[] componentArray = elements.split("-");
            int numComponents = componentArray.length;
            
            // Resolve mapping
            String resolvedSymmetryGroup;
            String resolvedClusterFile;
            try {
                resolvedSymmetryGroup = StructureModelMapping.resolveSymmetryGroup(structurePhase);
                resolvedClusterFile = StructureModelMapping.resolveClusterFile(structurePhase, model);
                logResult("Mapping resolved:");
                logResult("  " + structurePhase + " → Symmetry: " + resolvedSymmetryGroup);
                logResult("  " + structurePhase + " + " + model + " → Cluster file: " + resolvedClusterFile);
            } catch (IllegalArgumentException ex) {
                showAlert("Mapping Error", ex.getMessage());
                return;
            }
            
            // Generate component suffix (e.g., "bin" for 2 components, "tern" for 3, "quat" for 4)
            String componentSuffix = getComponentSuffix(numComponents);
            String clusterDataId = structure + "_" + phase + "_" + model + "_" + componentSuffix;
            
            // Check CEC availability
            boolean cecAvailable = SystemDataLoader.cecExists(elements);
            logResult("Checking system: " + elements + " " + structure + "_" + phase + "_" + model);
            logResult("CEC data: " + (cecAvailable ? "✓ Available" : "⚠ Not available - will need manual input"));
            
            // Check cluster data availability - look for actual cluster_result.json in project resources
            String systemId = SystemInfo.generateSystemId(elements, structure, phase, model);
            boolean clusterDataExists = ClusterDataCache.clusterDataExists(systemId);
            logResult("Cluster data (" + clusterDataId + "): " + (clusterDataExists ? "✓ Available" : "⚠ Not available"));
            
            if (!clusterDataExists) {
                boolean createClusterAlgebra = confirmAction(
                    "Cluster Data Missing",
                    "Cluster algebra is not available for this system.\nDo you want to create it now?"
                );
                if (!createClusterAlgebra) {
                    logResult("⚠ Cluster algebra creation skipped by user.");
                    return;
                }
                
                logResult("\n→ Cluster data missing. Starting identification pipeline...");
                logResult("Step 1: Cluster identification");
                
                // Create system object (using systemId already generated)
                String systemName = elements + " " + structure + " " + phase + " (" + model + ")";
                SystemInfo system = new SystemInfo(systemId, systemName, structure, phase, model, componentArray);
                system.setCecAvailable(cecAvailable);
                system.setClustersComputed(false);
                system.setCfsComputed(false);
                system.setClusterFilePath(resolvedClusterFile);
                system.setSymmetryGroupName(resolvedSymmetryGroup);
                
                registry.registerSystem(system);
                
                // Trigger cluster identification
                logResult("\n>>> Starting cluster identification for: " + systemName);
                if (!startClusterIdentificationForSystem(system)) {
                    return;
                }
                
                logResult("Step 2: CF identification");
                startCfIdentificationForSystem(system);
                
            } else {
                // Cluster data exists - create system directly
                logResult("\n✓ All required data available. Creating system...");
                
                String systemName = elements + " " + structure + " " + phase + " (" + model + ")";
                SystemInfo system = new SystemInfo(systemId, systemName, structure, phase, model, componentArray);
                system.setCecAvailable(cecAvailable);
                system.setClustersComputed(true);
                system.setCfsComputed(true);
                system.setClusterFilePath(resolvedClusterFile);
                system.setSymmetryGroupName(resolvedSymmetryGroup);
                
                registry.registerSystem(system);
                logResult("✓ System created successfully: " + systemName);
            }
            
            // Clear form
            elementsField.clear();
            structurePhaseField.clear();
            modelField.clear();
        });
        
        clearButton.setOnAction(e -> {
            elementsField.clear();
            structurePhaseField.clear();
            modelField.clear();
        });
        
        return vbox;
    }
    
    private void setupJobMonitoring() {
        jobManager.addManagerListener(new BackgroundJobManager.JobManagerListener() {
            @Override
            public void onJobQueued(String jobId, int queueSize) {
                javafx.application.Platform.runLater(() -> {
                    logResult(jobId + " - queued (queue size: " + queueSize + ")");
                });
            }
            
            @Override
            public void onJobStarted(String jobId) {
                javafx.application.Platform.runLater(() -> {
                    logResult(jobId + " - started");
                });
            }
            
            @Override
            public void onJobFinished(String jobId) {
                javafx.application.Platform.runLater(() -> {
                    logResult(jobId + " - finished");
                    
                    // CF identification completion is already logged
                    // Cluster data is saved directly in ClusterIdentificationJob.run()
                    // Just persist the updated system state
                    
                    registry.persistSystems();
                    updateIdentificationProgress();
                });
            }
            
            @Override
            public void onJobCancelled(String jobId) {
                javafx.application.Platform.runLater(() -> {
                    logResult(jobId + " - cancelled");
                });
            }
        });
    }
    
    private void logResult(String message) {
        resultsPanel.logMessage(message);
    }
    
    private String getComponentSuffix(int numComponents) {
        switch (numComponents) {
            case 2: return "bin";
            case 3: return "tern";
            case 4: return "quat";
            case 5: return "quint";
            default: return "comp" + numComponents;
        }
    }
    
    private boolean startClusterIdentificationForSystem(SystemInfo system) {
        // Show dialog only if we don't have cached input
        if (cachedIdentificationInput == null) {
            Optional<IdentificationInput> inputOpt = showIdentificationDialog(system, "Cluster Identification");
            if (inputOpt.isEmpty()) {
                logResult("⚠ Identification cancelled by user.");
                return false;
            }
            cachedIdentificationInput = inputOpt.get();
            logResult("→ Input files cached for subsequent operations");
        } else {
            logResult("→ Reusing input files from previous step");
        }
        
        IdentificationInput input = cachedIdentificationInput;
        logResult("  Disordered cluster: " + input.disorderedClusterFile);
        logResult("  Ordered cluster: " + input.orderedClusterFile);
        logResult("  Disordered symmetry: " + input.disorderedSymmetryGroup);
        logResult("  Ordered symmetry: " + input.orderedSymmetryGroup);
        
        ClusterIdentificationJob job = new ClusterIdentificationJob(
            system,
            input.disorderedClusterFile,
            input.orderedClusterFile,
            input.disorderedSymmetryGroup,
            input.orderedSymmetryGroup,
            resolveMatrix(system),
            resolveTranslation(system)
        );
        jobManager.submitJob(job);
        system.setClusterJobId(job.getId());
        logResult("Submitted cluster identification job: " + job.getId());
        return true;
    }

    private boolean startCfIdentificationForSystem(SystemInfo system) {
        // Use cached input from cluster identification (same files)
        if (cachedIdentificationInput == null) {
            logResult("⚠ No cached identification input. Please run cluster identification first.");
            return false;
        }
        
        IdentificationInput input = cachedIdentificationInput;
        logResult("→ Using same input files for CF identification");
        
        CFIdentificationJob job = new CFIdentificationJob(
            system,
            input.disorderedClusterFile,
            input.orderedClusterFile,
            input.disorderedSymmetryGroup,
            input.orderedSymmetryGroup,
            resolveMatrix(system),
            resolveTranslation(system),
            system.getNumComponents()
        );
        jobManager.submitJob(job);
        logResult("Submitted CF identification job: " + job.getId());
        return true;
    }

    
    private void updateIdentificationProgress() {
        Collection<BackgroundJob> activeJobs = jobManager.getActiveJobs();
        if (activeJobs.isEmpty()) {
            resultsPanel.setProgress(0);
        } else {
            // Average progress of all active jobs
            double avgProgress = activeJobs.stream()
                .mapToInt(BackgroundJob::getProgress)
                .average()
                .orElse(0);
            resultsPanel.setProgress(avgProgress / 100.0);
        }
    }


    private boolean confirmAction(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private Optional<IdentificationInput> showIdentificationDialog(SystemInfo system, String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Provide cluster files and symmetry groups for " + system.getName());

        ButtonType runButton = new ButtonType("Run", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(runButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // Pre-fill with resolved values from system
        TextField disClusterField = new TextField(defaultIfNull(system.getClusterFilePath()));
        TextField ordClusterField = new TextField(defaultIfNull(system.getClusterFilePath()));
        TextField disSymField = new TextField(defaultIfNull(system.getSymmetryGroupName()));
        TextField ordSymField = new TextField(defaultIfNull(system.getSymmetryGroupName()));

        addDialogRow(grid, 0, "Disordered cluster file", disClusterField);
        addDialogRow(grid, 1, "Ordered cluster file", ordClusterField);
        addDialogRow(grid, 2, "Disordered symmetry group", disSymField);
        addDialogRow(grid, 3, "Ordered symmetry group", ordSymField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == runButton) {
            String disCluster = disClusterField.getText().trim();
            String ordCluster = ordClusterField.getText().trim();
            String disSym = disSymField.getText().trim();
            String ordSym = ordSymField.getText().trim();

            if (disCluster.isEmpty() || ordCluster.isEmpty() || disSym.isEmpty() || ordSym.isEmpty()) {
                showAlert("Missing input", "All fields are required to start identification.");
                return Optional.empty();
            }

            IdentificationInput input = new IdentificationInput(
                disCluster,
                ordCluster,
                disSym,
                ordSym
            );
            return Optional.of(input);
        }
        return Optional.empty();
    }

    private void addDialogRow(GridPane grid, int row, String labelText, TextField field) {
        Label label = new Label(labelText);
        field.setPromptText(labelText);
        field.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(label, 0, row);
        grid.add(field, 1, row);
    }

    private String defaultIfNull(String value) {
        return value == null ? "" : value;
    }

    private double[][] resolveMatrix(SystemInfo system) {
        double[][] matrix = system.getTransformationMatrix();
        if (matrix != null && matrix.length == 3) {
            return matrix;
        }
        return new double[][] {
            {1, 0, 0},
            {0, 1, 0},
            {0, 0, 1}
        };
    }

    private Vector3D resolveTranslation(SystemInfo system) {
        double[] vector = system.getTranslationVector();
        if (vector != null && vector.length == 3) {
            return new Vector3D(vector[0], vector[1], vector[2]);
        }
        return new Vector3D(0, 0, 0);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class IdentificationInput {
        private final String disorderedClusterFile;
        private final String orderedClusterFile;
        private final String disorderedSymmetryGroup;
        private final String orderedSymmetryGroup;

        private IdentificationInput(String disorderedClusterFile,
                                    String orderedClusterFile,
                                    String disorderedSymmetryGroup,
                                    String orderedSymmetryGroup) {
            this.disorderedClusterFile = disorderedClusterFile;
            this.orderedClusterFile = orderedClusterFile;
            this.disorderedSymmetryGroup = disorderedSymmetryGroup;
            this.orderedSymmetryGroup = orderedSymmetryGroup;
        }
    }
}
