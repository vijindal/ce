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
import org.ce.identification.geometry.Vector3D;
import org.ce.workbench.backend.job.CFIdentificationJob;
import org.ce.workbench.util.mcs.StructureModelMapping;
import org.ce.workbench.util.cache.ClusterDataCache;
import org.ce.workbench.util.cache.AllClusterDataCache;
import org.ce.workbench.util.key.KeyUtils;
import org.ce.identification.result.ClusCoordListResult;

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
        Button createClusterButton = new Button("Create Cluster");
        Button clearButton = new Button("Clear");
        createButton.setStyle("-fx-font-size: 11; -fx-padding: 5 15;");
        createClusterButton.setStyle("-fx-font-size: 11; -fx-padding: 5 15; -fx-text-fill: #0066cc;");
        clearButton.setStyle("-fx-font-size: 11; -fx-padding: 5 15;");
        formButtons.getChildren().addAll(createClusterButton, createButton, clearButton);
        
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
            String componentSuffix = KeyUtils.componentSuffix(numComponents);

            // systemId: uniquely identifies this system instance (element + model)
            String systemId = SystemInfo.generateSystemId(elements, structure, phase, model);
            // cecKey: element + model — one CEC file per alloy+model combination
            String cecKey = KeyUtils.cecKey(elements, structure, phase, model);
            // clusterKey: component-count + model — shared across all alloys with same topology
            String clusterKey = KeyUtils.clusterKey(structure, phase, model, numComponents);

            logResult("\n[System Creation] ----------------------------------------");
            logResult("  Elements      : " + elements + " (" + numComponents + " components → " + componentSuffix + ")");
            logResult("  Structure     : " + structure + "  Phase: " + phase + "  Model: " + model);
            logResult("  systemId      : " + systemId);
            logResult("  CEC key       : " + cecKey + "  → /data/systems/" + cecKey + "/cec.json");
            logResult("  Cluster key   : " + clusterKey + "  → data/cluster_cache/" + clusterKey + "/cluster_result.json");

            // Resolve mapping
            String resolvedSymmetryGroup;
            String resolvedClusterFile;
            try {
                resolvedSymmetryGroup = StructureModelMapping.resolveSymmetryGroup(structurePhase);
                resolvedClusterFile   = StructureModelMapping.resolveClusterFile(structurePhase, model);
                logResult("  Symmetry group : " + resolvedSymmetryGroup);
                logResult("  Cluster file   : " + resolvedClusterFile);
            } catch (IllegalArgumentException ex) {
                showAlert("Mapping Error", ex.getMessage());
                return;
            }

            // --- (i) CEC check — element + model specific ---
            boolean cecAvailable = SystemDataLoader.cecExists(elements, structure, phase, model);
            logResult("  CEC (" + cecKey + "): " + (cecAvailable
                    ? "✓ found" : "⚠ NOT found — MCS will require manual ECI input"));
            if (!cecAvailable) {
                showAlert("CEC Data Missing",
                    "No CEC data found for '" + cecKey + "'.\n\n"
                    + "Expected file: /data/systems/" + cecKey + "/cec.json\n\n"
                    + "You can still create the system and provide ECI values manually "
                    + "when running MCS.");
            }

            // --- (ii) Cluster data check — component-count + model specific ---
            logResult("  Checking cluster cache: " + clusterKey + " ...");
            boolean clusterDataExists = ClusterDataCache.clusterDataExists(clusterKey);
            logResult("  Cluster data (" + clusterKey + "): " + (clusterDataExists
                    ? "✓ found in cache" : "⚠ not in cache — identification pipeline needed"));
            
            if (!clusterDataExists) {
                boolean generate = confirmAction(
                    "Cluster Data Missing",
                    "No cluster data found for '" + clusterKey + "'.\n\n"
                    + "This data is generated once and reused for all "
                    + componentSuffix + " " + structure + "_" + phase
                    + " systems regardless of element choice.\n\n"
                    + "Generate now?"
                );
                if (!generate) {
                    logResult("⚠ Cluster data generation skipped. System not created.");
                    return;
                }

                logResult("\n→ Starting identification pipeline  clusterKey=" + clusterKey);

                String systemName = elements + " " + structure + " " + phase + " (" + model + ")";
                SystemInfo system = new SystemInfo(systemId, systemName, structure, phase, model, componentArray);
                system.setCecAvailable(cecAvailable);
                system.setClustersComputed(false);
                system.setCfsComputed(false);
                system.setClusterFilePath(resolvedClusterFile);
                system.setSymmetryGroupName(resolvedSymmetryGroup);
                registry.registerSystem(system);
                logResult("  System registered: " + systemId);

                logResult("  Running CF identification (includes stages 1-3)...");
                if (!startCfIdentificationForSystem(system)) {
                    return;
                }

            } else {
                logResult("\n✓ Cluster cache hit (" + clusterKey + ") — no identification needed.");

                String systemName = elements + " " + structure + " " + phase + " (" + model + ")";
                SystemInfo system = new SystemInfo(systemId, systemName, structure, phase, model, componentArray);
                system.setCecAvailable(cecAvailable);
                system.setClustersComputed(true);
                system.setCfsComputed(true);
                system.setClusterFilePath(resolvedClusterFile);
                system.setSymmetryGroupName(resolvedSymmetryGroup);
                registry.registerSystem(system);
                logResult("✓ System created: " + systemName);
                logResult("  At MCS time → CEC loaded from key: " + cecKey);
                logResult("  At MCS time → cluster data loaded from key: " + clusterKey);
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
        
        // Create Cluster button handler - for testing cluster data creation
        createClusterButton.setOnAction(e -> {
            String elements = elementsField.getText().trim();
            String structurePhase = structurePhaseField.getText().trim();
            String model = modelField.getText().trim();
            
            if (structurePhase.isEmpty() || model.isEmpty()) {
                showAlert("Missing Fields", "Structure/Phase and Model are required for cluster creation.");
                return;
            }
            
            // Validate structure/phase format
            if (!StructureModelMapping.isValidStructurePhase(structurePhase)) {
                showAlert("Invalid Format", "Structure/Phase must be in format: Structure_Phase (e.g., BCC_A2)");
                return;
            }
            
            // Infer number of components from Elements field
            int numComponents;
            if (elements.isEmpty()) {
                showAlert("Missing Elements",
                    "The Elements field is required to determine the number of components.\n"
                    + "Enter element names separated by '-' (e.g., Ti-Nb for binary, Fe-Ni-Cr for ternary).");
                return;
            }
            String[] componentArray = elements.split("-");
            numComponents = componentArray.length;
            if (numComponents < 2 || numComponents > 5) {
                showAlert("Invalid Components",
                    "Number of components must be between 2 and 5.\n"
                    + "Found " + numComponents + " from Elements field: '" + elements + "'");
                return;
            }
            
            String[] parts = structurePhase.split("_");
            String structure = parts[0];
            String phase = parts[1];
            String componentSuffix = KeyUtils.componentSuffix(numComponents);
            String clusterKey = KeyUtils.clusterKey(structure, phase, model, numComponents);
            
            logResult("\n[Cluster Creation] ----------------------------------------");
            logResult("  Elements: " + elements + " → " + numComponents + " components (" + componentSuffix + ")");
            logResult("  Structure: " + structure + "  Phase: " + phase + "  Model: " + model);
            logResult("  Cluster Key: " + clusterKey);
            
            // Check if data already exists — warn and offer overwrite
            boolean mcsDataExists = ClusterDataCache.clusterDataExists(clusterKey);
            boolean allDataExists = AllClusterDataCache.exists(clusterKey);
            
            if (mcsDataExists || allDataExists) {
                java.nio.file.Path storageDir = AllClusterDataCache.resolveDir(clusterKey);
                
                StringBuilder warning = new StringBuilder();
                warning.append("Cluster data already exists for '").append(clusterKey).append("':\n\n");
                warning.append("Folder: ").append(storageDir.toAbsolutePath()).append("\n");
                if (allDataExists)  warning.append("  • all_cluster_data.json (CVM)\n");
                if (mcsDataExists)  warning.append("  • cluster_result.json (MCS)\n");
                warning.append("\nDo you want to overwrite the existing data?");
                
                logResult("  ⚠ Existing data found at: " + storageDir.toAbsolutePath());
                
                boolean overwrite = confirmAction("Data Already Exists", warning.toString());
                if (!overwrite) {
                    logResult("  ⚠ Cluster creation cancelled — existing data preserved.");
                    return;
                }
                logResult("  → User chose to overwrite existing data.");
            }
            
            // Show dialog for cluster files
            // Create a temporary system to pre-fill the dialog
            String tempId = "temp-cluster-" + clusterKey;
            String tempName = structure + "_" + phase + "_" + model + "_" + componentSuffix;
            SystemInfo tempSystem = new SystemInfo(
                tempId, tempName, structure, phase, model, componentArray
            );
            
            // Resolve default cluster file and symmetry group for dialog pre-fill
            try {
                tempSystem.setClusterFilePath(StructureModelMapping.resolveClusterFile(structurePhase, model));
                tempSystem.setSymmetryGroupName(StructureModelMapping.resolveSymmetryGroup(structurePhase));
            } catch (IllegalArgumentException ex) {
                // Not critical — dialog will just show empty fields
            }
            
            Optional<IdentificationInput> inputOpt = showIdentificationDialog(
                tempSystem,
                "Cluster Identification Input"
            );
            if (inputOpt.isEmpty()) {
                logResult("⚠ Cluster creation cancelled by user.");
                return;
            }
            
            IdentificationInput input = inputOpt.get();
            logResult("→ Using input files:");
            logResult("  Disordered cluster: " + input.disorderedClusterFile);
            logResult("  Ordered cluster: " + input.orderedClusterFile);
            logResult("  Disordered symmetry: " + input.disorderedSymmetryGroup);
            logResult("  Ordered symmetry: " + input.orderedSymmetryGroup);
            
            tempSystem.setClusterFilePath(input.disorderedClusterFile);
            tempSystem.setSymmetryGroupName(input.disorderedSymmetryGroup);
            
            logResult("  Submitting cluster identification job...");
            
            // Create and submit the job
            CFIdentificationJob job = new CFIdentificationJob(
                tempSystem,
                clusterKey,
                input.disorderedClusterFile,
                input.orderedClusterFile,
                input.disorderedSymmetryGroup,
                input.orderedSymmetryGroup,
                resolveMatrix(tempSystem),
                resolveTranslation(tempSystem),
                numComponents
            );
            
            jobManager.submitJob(job);
            logResult("✓ Job submitted: " + job.getId());
            
            // Background thread to monitor job and display results when complete
            final String finalClusterKey = clusterKey;
            Thread resultMonitor = new Thread(() -> {
                boolean wasRunning = true;
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(100); // Check every 100ms if job is complete
                        
                        boolean isRunning = job.isRunning();
                        
                        // Check if job just finished (was running, now not running)
                        if (wasRunning && !isRunning) {
                            // Give it a moment to ensure all data is flushed
                            Thread.sleep(200);
                            
                            if (job.isCompleted() && !job.isFailed()) {
                                // Job completed successfully — display all results with folder path
                                javafx.application.Platform.runLater(() -> {
                                    ClusterDataPresenter.present(job, finalClusterKey, this::logResult);
                                });
                            } else if (job.isFailed()) {
                                javafx.application.Platform.runLater(() -> {
                                    logResult("❌ Job failed: " + job.getStatusMessage());
                                });
                            }
                            break;
                        }
                        
                        wasRunning = isRunning;
                        
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            
            resultMonitor.setDaemon(true);
            resultMonitor.setName("ClusterResultMonitor-" + job.getId());
            resultMonitor.start();
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
                    
                    // CF identification job completes all stages (1-3) and updates system state
                    
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
    
    // getComponentSuffix() removed — use KeyUtils.componentSuffix() instead

    private boolean startCfIdentificationForSystem(SystemInfo system) {
        // Show dialog only if we don't have cached input
        if (cachedIdentificationInput == null) {
            Optional<IdentificationInput> inputOpt = showIdentificationDialog(system, "CF Identification");
            if (inputOpt.isEmpty()) {
                logResult("⚠ Identification cancelled by user.");
                return false;
            }
            cachedIdentificationInput = inputOpt.get();
            logResult("→ Input files cached for identification");
        }
        
        IdentificationInput input = cachedIdentificationInput;
        logResult("→ Using input files for CF identification");
        logResult("  Disordered cluster: " + input.disorderedClusterFile);
        logResult("  Ordered cluster: " + input.orderedClusterFile);
        logResult("  Disordered symmetry: " + input.disorderedSymmetryGroup);
        logResult("  Ordered symmetry: " + input.orderedSymmetryGroup);
        
        String clusterKey = KeyUtils.clusterKey(system);
        CFIdentificationJob job = new CFIdentificationJob(
            system,
            clusterKey,
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