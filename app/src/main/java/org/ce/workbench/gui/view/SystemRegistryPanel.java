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
import org.ce.identification.engine.Vector3D;
import org.ce.workbench.backend.job.CFIdentificationJob;
import org.ce.workbench.backend.job.ClusterIdentificationJob;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Left panel for system registry and background job management.
 * Displays registered systems with caching status and active job queue.
 */
public class SystemRegistryPanel extends VBox {
    
    private final SystemRegistry registry;
    private final BackgroundJobManager jobManager;
    private TreeView<String> systemTree;
    private TextArea jobLogArea;
    private ProgressBar jobProgressBar;
    private final Map<TreeItem<String>, SystemInfo> systemItemMap = new HashMap<>();
    
    public SystemRegistryPanel(SystemRegistry registry, BackgroundJobManager jobManager) {
        this.registry = registry;
        this.jobManager = jobManager;
        
        this.setSpacing(10);
        this.setPadding(new Insets(10));
        this.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 0 1 0 0;");
        
        // Build UI components
        VBox systemSection = createSystemSection();
        VBox jobSection = createJobSection();
        
        // Add to layout with splitter
        SplitPane splitter = new SplitPane();
        splitter.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitter.setDividerPositions(0.6);
        splitter.getItems().addAll(systemSection, jobSection);
        
        this.getChildren().add(splitter);
        
        // Initialize data
        refreshSystemTree();
        setupJobMonitoring();
    }
    
    private VBox createSystemSection() {
        VBox vbox = new VBox(10);
        
        Label titleLabel = new Label("System Registry");
        titleLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        
        // Search box
        TextField searchBox = new TextField();
        searchBox.setPromptText("Search systems...");
        
        // System tree
        systemTree = new TreeView<>();
        systemTree.setPrefHeight(200);
        
        // Toolbar
        HBox toolbar = new HBox(5);
        Button addButton = new Button("+ Add System");
        Button deleteButton = new Button("Delete");
        Button clusterIdButton = new Button("Cluster ID");
        Button cfIdButton = new Button("CF ID");
        
        addButton.setStyle("-fx-font-size: 10;");
        deleteButton.setStyle("-fx-font-size: 10;");
        clusterIdButton.setStyle("-fx-font-size: 10;");
        cfIdButton.setStyle("-fx-font-size: 10;");
        
        toolbar.getChildren().addAll(addButton, deleteButton, clusterIdButton, cfIdButton);
        
        vbox.getChildren().addAll(titleLabel, searchBox, new Label("Systems:"),
                  systemTree, toolbar);
        VBox.setVgrow(systemTree, Priority.ALWAYS);

        addButton.setOnAction(e -> showAddSystemDialog());
        clusterIdButton.setOnAction(e -> startClusterIdentification());
        cfIdButton.setOnAction(e -> startCfIdentification());
        
        return vbox;
    }
    
    private VBox createJobSection() {
        VBox vbox = new VBox(10);
        
        Label titleLabel = new Label("Background Job Monitor");
        titleLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        
        // Job progress
        jobProgressBar = new ProgressBar(0);
        jobProgressBar.setPrefHeight(20);
        
        // Job log
        jobLogArea = new TextArea();
        jobLogArea.setWrapText(true);
        jobLogArea.setPrefHeight(100);
        jobLogArea.setEditable(false);
        jobLogArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 9;");
        
        // Control buttons
        HBox controls = new HBox(5);
        Button pauseButton = new Button("Pause");
        Button cancelButton = new Button("Cancel");
        Button clearButton = new Button("Clear");
        
        pauseButton.setStyle("-fx-font-size: 10;");
        cancelButton.setStyle("-fx-font-size: 10;");
        clearButton.setStyle("-fx-font-size: 10;");
        
        controls.getChildren().addAll(pauseButton, cancelButton, clearButton);
        
        vbox.getChildren().addAll(titleLabel, jobProgressBar, new Label("Job Log:"), 
                      jobLogArea, controls);
        VBox.setVgrow(jobLogArea, Priority.ALWAYS);
        
        return vbox;
    }
    
    private void refreshSystemTree() {
        TreeItem<String> root = new TreeItem<>("Systems");
        root.setExpanded(true);
        
        Collection<SystemInfo> systems = registry.getAllSystems();
        systemItemMap.clear();
        
        for (SystemInfo system : systems) {
            TreeItem<String> systemItem = new TreeItem<>(system.getName());
            systemItemMap.put(systemItem, system);
            
            // Status indicators
            String clustersStatus = system.isClustersComputed() ? "✓" : "○";
            String cfsStatus = system.isCfsComputed() ? "✓" : "○";
            
            TreeItem<String> clustersItem = new TreeItem<>(clustersStatus + " Clusters");
            TreeItem<String> cfsItem = new TreeItem<>(cfsStatus + " CFs");
            
            systemItem.getChildren().addAll(clustersItem, cfsItem);
            root.getChildren().add(systemItem);
        }
        
        systemTree.setRoot(root);
    }
    
    private void setupJobMonitoring() {
        jobManager.addManagerListener(new BackgroundJobManager.JobManagerListener() {
            @Override
            public void onJobQueued(String jobId, int queueSize) {
                javafx.application.Platform.runLater(() -> {
                    logJobEvent(jobId + " - queued (queue size: " + queueSize + ")");
                });
            }
            
            @Override
            public void onJobStarted(String jobId) {
                javafx.application.Platform.runLater(() -> {
                    logJobEvent(jobId + " - started");
                });
            }
            
            @Override
            public void onJobFinished(String jobId) {
                javafx.application.Platform.runLater(() -> {
                    logJobEvent(jobId + " - finished");
                    registry.persistSystems();
                    refreshSystemTree();
                });
            }
            
            @Override
            public void onJobCancelled(String jobId) {
                javafx.application.Platform.runLater(() -> {
                    logJobEvent(jobId + " - cancelled");
                });
            }
        });
    }
    
    private void logJobEvent(String message) {
        jobLogArea.appendText("[" + java.time.LocalTime.now() + "] " + message + "\n");
    }
    
    private void showAddSystemDialog() {
        Dialog<SystemInfo> dialog = new Dialog<>();
        dialog.setTitle("Add New System");
        dialog.setHeaderText("Create a new calculation system");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        TextField idField = new TextField();
        idField.setPromptText("e.g., FE-NI-001");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g., Fe-Ni A1");
        TextField structureField = new TextField();
        structureField.setPromptText("e.g., BCC");
        TextField phaseField = new TextField();
        phaseField.setPromptText("e.g., A2");
        TextField componentsField = new TextField();
        componentsField.setPromptText("e.g., Fe,Ni");
        
        grid.add(new Label("ID:"), 0, 0);
        grid.add(idField, 1, 0);
        grid.add(new Label("Name:"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(new Label("Structure:"), 0, 2);
        grid.add(structureField, 1, 2);
        grid.add(new Label("Phase:"), 0, 3);
        grid.add(phaseField, 1, 3);
        grid.add(new Label("Components (comma-separated):"), 0, 4);
        grid.add(componentsField, 1, 4);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                if (idField.getText().isEmpty() || nameField.getText().isEmpty()) {
                    showAlert("Missing Fields", "ID and Name are required.");
                    return null;
                }
                String[] components = componentsField.getText().split(",");
                for (int i = 0; i < components.length; i++) {
                    components[i] = components[i].trim();
                }
                return new SystemInfo(
                    idField.getText().trim(),
                    nameField.getText().trim(),
                    structureField.getText().trim(),
                    phaseField.getText().trim(),
                    components
                );
            }
            return null;
        });
        
        Optional<SystemInfo> result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            SystemInfo newSystem = result.get();
            registry.registerSystem(newSystem);
            refreshSystemTree();
            logJobEvent("Added system: " + newSystem.getName());
        }
    }
    
    public void updateJobProgress() {
        Collection<BackgroundJob> activeJobs = jobManager.getActiveJobs();
        if (activeJobs.isEmpty()) {
            jobProgressBar.setProgress(0);
        } else {
            // Average progress of all active jobs
            double avgProgress = activeJobs.stream()
                .mapToInt(BackgroundJob::getProgress)
                .average()
                .orElse(0);
            jobProgressBar.setProgress(avgProgress / 100.0);
        }
    }

    private SystemInfo getSelectedSystem() {
        TreeItem<String> selected = systemTree.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return null;
        }
        SystemInfo direct = systemItemMap.get(selected);
        if (direct != null) {
            return direct;
        }
        TreeItem<String> parent = selected.getParent();
        return parent == null ? null : systemItemMap.get(parent);
    }

    private void startClusterIdentification() {
        SystemInfo system = getSelectedSystem();
        if (system == null) {
            showAlert("No system selected", "Please select a system to run cluster identification.");
            return;
        }

        Optional<IdentificationInput> inputOpt = showIdentificationDialog(system, "Cluster Identification");
        if (inputOpt.isEmpty()) {
            return;
        }

        IdentificationInput input = inputOpt.get();
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
        logJobEvent("Submitted cluster identification for " + system.getName());
    }

    private void startCfIdentification() {
        SystemInfo system = getSelectedSystem();
        if (system == null) {
            showAlert("No system selected", "Please select a system to run CF identification.");
            return;
        }

        Optional<IdentificationInput> inputOpt = showIdentificationDialog(system, "CF Identification");
        if (inputOpt.isEmpty()) {
            return;
        }

        IdentificationInput input = inputOpt.get();
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
        logJobEvent("Submitted CF identification for " + system.getName());
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

        TextField disClusterField = new TextField(defaultIfNull(system.getClusterFilePath()));
        TextField ordClusterField = new TextField();
        TextField disSymField = new TextField(defaultIfNull(system.getSymmetryGroupName()));
        TextField ordSymField = new TextField();

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
