package org.ce.presentation.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.ce.infrastructure.service.BackgroundJobManager;
import org.ce.infrastructure.service.CECManagementCoordinator;
import org.ce.infrastructure.service.IdentificationCoordinator;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.application.service.CECManagementService;
import org.ce.infrastructure.registry.ResultRepository;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.infrastructure.registry.WorkspaceManager;
import org.ce.presentation.gui.component.CVMModelInspectorDialog;
import org.ce.presentation.gui.view.CalculationPanel;
import org.ce.presentation.gui.view.CECManagementPanel;
import org.ce.presentation.gui.view.DataPreparationPanel;
import org.ce.presentation.gui.view.LogConsolePanel;
import org.ce.presentation.gui.view.ResultsPanel;
import org.ce.domain.system.SystemIdentity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ce.infrastructure.logging.LoggingConfig;

/**
 * Main JavaFX application for the CE Thermodynamics Workbench GUI.
 * Entry point and orchestrator for all UI components.
 */
public class CEWorkbenchApplication extends Application {

    private static final Logger LOG = LoggingConfig.getLogger(CEWorkbenchApplication.class);

    private SystemRegistry systemRegistry;
    private ResultRepository resultRepository;
    private BackgroundJobManager jobManager;
    private WorkspaceManager workspace;
    private IdentificationCoordinator identificationCoordinator;
    private CECManagementCoordinator cecManagementCoordinator;
    private CECManagementService cecManagementService;
    private ResultsPanel resultsPanel;
    private LogConsolePanel logConsolePanel;
    private CalculationPanel calculationPanel;
    private DataPreparationPanel dataPreparationPanel;
    private TabPane rightPanel;
    private Tab logTab;
    private Tab resultTab;
    private Tab cecDatabaseTab;

    /** Retained across the static/instance boundary so LogConsolePanel can use it. */
    private static Level initialLogLevel = Level.INFO;

    private Stage primaryStage;
    
    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        
        // Initialize backend services
        initializeBackgroundServices();
        
        // Build UI
        buildUI(stage);
        
        // Show window with responsive sizing
        stage.setTitle("CE Thermodynamics Workbench");
        
        // Get screen bounds
        javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
        javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();
        
        // Set window to 90% of screen size, centered
        double width = Math.min(1400, bounds.getWidth() * 0.9);
        double height = Math.min(850, bounds.getHeight() * 0.9);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX((bounds.getWidth() - width) / 2);
        stage.setY((bounds.getHeight() - height) / 2);
        
        stage.show();
    }
    
    private void initializeBackgroundServices() throws Exception {
        // Robustly detect the project root (directory containing 'app/')
        Path cwd = Paths.get("").toAbsolutePath();
        Path projectRoot = cwd;
        while (projectRoot != null && !projectRoot.resolve("app").toFile().exists()) {
            projectRoot = projectRoot.getParent();
        }
        if (projectRoot == null) {
            throw new IllegalStateException("Cannot locate project root (directory containing 'app') from cwd=" + cwd);
        }

        // Single workspace manager — owns all persistent path resolution
        workspace = new WorkspaceManager(projectRoot);

        // Configure workspace root for both CEC and cluster data persistence
        SystemDataLoader.setWorkspaceRoot(workspace.getRoot());
        AllClusterDataCache.setWorkspaceRoot(workspace.getRoot());

        // Registry and repository both root under the same workspace
        systemRegistry    = new SystemRegistry(projectRoot);
        resultRepository  = new ResultRepository(projectRoot);

        // Initialize job manager with 2 concurrent jobs
        jobManager = new BackgroundJobManager(2);

        // Migrate any cluster data still sitting in the old project-tree location
        migrateClusterCacheIfNeeded();

        // Application-layer service and infrastructure coordinators (Type 1 architecture)
        cecManagementService     = new CECManagementService(workspace);
        identificationCoordinator = new IdentificationCoordinator(jobManager, systemRegistry);
        cecManagementCoordinator  = new CECManagementCoordinator(jobManager, cecManagementService);

        // Auto-discover and register alloy systems from the filesystem
        loadAlloySystemsFromFilesystem();
    }

    /**
     * Auto-discover and register alloy systems from the filesystem.
     *
     * <p>Scans {@code app/src/main/resources/data/systems/} for CEC files and
     * automatically registers discovered systems. No code changes needed to add new systems.</p>
     */
    private void loadAlloySystemsFromFilesystem() {
        try {
            // Determine the absolute path to systems directory
            // Try multiple strategies, in order of preference:

            // Strategy 1: Relative to current working directory
            Path systemsDir = Paths.get("app/src/main/resources/data/systems");
            if (Files.exists(systemsDir)) {
                int loaded = systemRegistry.loadSystemsFromFilesystem(systemsDir);
                LOG.info("Loaded " + loaded + " alloy systems from filesystem");
                return;
            }

            // Strategy 2: From classpath location (when running from IDE/gradle)
            String urlPath = CEWorkbenchApplication.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();

            // Fix Windows path: URL returns /C:/... but Paths.get() needs C:/...
            String classesPath = urlPath;
            if (classesPath.startsWith("/") && classesPath.length() > 2 &&
                Character.isLetter(classesPath.charAt(1)) && classesPath.charAt(2) == ':') {
                classesPath = classesPath.substring(1);
            }

            // Navigate from build output up to project root
            // classesPath is like C:/Users/admin/codes/ce/app/build/classes/java/main/
            // We need to find the project root by walking up and checking for build.gradle
            Path current = Paths.get(classesPath);
            for (int i = 0; i < 10 && current != null; i++) {
                Path candidate = current.resolve("app/src/main/resources/data/systems");
                if (Files.exists(candidate)) {
                    systemsDir = candidate;
                    break;
                }
                current = current.getParent();
            }

            if (!Files.exists(systemsDir)) {
                LOG.warning("Systems directory not found: " + systemsDir);
                return;
            }

            // Auto-discover and register systems
            int loaded = systemRegistry.loadSystemsFromFilesystem(systemsDir);
            LOG.info("Loaded " + loaded + " alloy systems from filesystem");

        } catch (Exception e) {
            LOG.warning("Failed to load alloy systems from filesystem: " + e.getMessage());
        }
    }
    
    /**
     * One-time migration: moves cluster data from the old project-tree location
     * ({@code data/cluster_cache/}) into the unified workspace
     * ({@code ~/.ce-workbench/data/cluster_cache/}).
     *
     * <p>Safe to call on every startup — skips keys that already exist in the
     * workspace and ignores stale {@code cluster_result.json} legacy files.</p>
     */
    private void migrateClusterCacheIfNeeded() {
        java.nio.file.Path workspaceCacheRoot = workspace.getRoot().resolve("data/cluster_cache");
        String[] legacyRoots = {
            "data/cluster_cache",
            "app/src/main/resources/data/cluster_cache"
        };

        for (String legacyBase : legacyRoots) {
            java.nio.file.Path legacy = java.nio.file.Paths.get(legacyBase);
            if (!java.nio.file.Files.exists(legacy)) continue;

            try (java.util.stream.Stream<java.nio.file.Path> dirs = java.nio.file.Files.list(legacy)) {
                dirs.filter(java.nio.file.Files::isDirectory).forEach(keyDir -> {
                    java.nio.file.Path src = keyDir.resolve("all_cluster_data.json");
                    if (!java.nio.file.Files.exists(src)) return;

                    java.nio.file.Path dest = workspaceCacheRoot
                            .resolve(keyDir.getFileName())
                            .resolve("all_cluster_data.json");

                    if (java.nio.file.Files.exists(dest)) return; // already migrated

                    try {
                        java.nio.file.Files.createDirectories(dest.getParent());
                        java.nio.file.Files.copy(src, dest);
                        LOG.info("Migrated cluster cache: " + keyDir.getFileName() + " → workspace");
                    } catch (Exception e) {
                        LOG.warning("Failed to migrate cluster cache for "
                                + keyDir.getFileName() + ": " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                LOG.warning("migrateClusterCacheIfNeeded error for " + legacyBase + ": " + e.getMessage());
            }
        }
    }

    private void buildUI(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11;");

        root.setTop(createMenuBar());

        SplitPane mainSplitter = new SplitPane();
        mainSplitter.setDividerPositions(0.28);

        // ---- Shared panels ----
        resultsPanel    = new ResultsPanel();
        logConsolePanel = new LogConsolePanel(initialLogLevel);

        // ---- Right panel — permanent tabs shared across modes ----
        rightPanel = new TabPane();
        rightPanel.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        resultTab      = new Tab("Result",       resultsPanel);
        logTab         = new Tab("Log",          logConsolePanel);

        CECManagementPanel cecPanel = new CECManagementPanel(
                systemRegistry, cecManagementService, cecManagementCoordinator);
        cecDatabaseTab = new Tab("CEC Database", cecPanel);
        cecDatabaseTab.setClosable(false);

        // Default to Calculation mode (Result + Log only)
        rightPanel.getTabs().setAll(resultTab, logTab);

        // ---- Left panel — tabbed: Calculation (default) + Data Preparation ----
        calculationPanel = new CalculationPanel(systemRegistry, jobManager, resultsPanel);

        dataPreparationPanel = new DataPreparationPanel(
                systemRegistry, jobManager, identificationCoordinator);

        // When pipeline starts → switch right panel to Log
        dataPreparationPanel.setOnPipelineStarted(() -> {
            if (!rightPanel.getTabs().contains(logTab))
                rightPanel.getTabs().add(logTab);
            rightPanel.getSelectionModel().select(logTab);
        });

        // When Open in CEC Database → switch right panel to CEC Database + pre-select
        dataPreparationPanel.setOnOpenCECBrowser(system -> {
            rightPanel.getTabs().setAll(cecDatabaseTab, logTab);
            rightPanel.getSelectionModel().select(cecDatabaseTab);
            // Tell the CEC panel to load this system
            ((CECManagementPanel) cecDatabaseTab.getContent()).selectSystem(system);
            // Refresh the calculation dropdown so newly-registered system appears
            calculationPanel.refreshSystems();
        });

        TabPane leftTabs = new TabPane();
        leftTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        leftTabs.setStyle("-fx-tab-min-width: 100;");

        Tab calcTab = new Tab("Calculation",      calculationPanel);
        Tab prepTab = new Tab("Data preparation", dataPreparationPanel);
        leftTabs.getTabs().addAll(calcTab, prepTab);

        // Switch right panel content when left tab changes
        leftTabs.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> {
            if (now == calcTab) {
                rightPanel.getTabs().setAll(resultTab, logTab);
            } else {
                rightPanel.getTabs().setAll(cecDatabaseTab, logTab);
            }
        });

        mainSplitter.getItems().addAll(leftTabs, rightPanel);
        root.setCenter(mainSplitter);
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root);
        stage.setScene(scene);
    }
    
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        
        // File menu
        Menu fileMenu = new Menu("File");
        MenuItem newSystemItem = new MenuItem("New System...");
        MenuItem openItem = new MenuItem("Open Workspace");
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> primaryStage.close());
        fileMenu.getItems().addAll(newSystemItem, new SeparatorMenuItem(), openItem, 
                                   new SeparatorMenuItem(), exitItem);
        
        // Edit menu
        Menu editMenu = new Menu("Edit");
        MenuItem preferencesItem = new MenuItem("Preferences");
        editMenu.getItems().add(preferencesItem);

        // View menu
        Menu viewMenu = new Menu("View");
        CheckMenuItem backgroundJobsItem = new CheckMenuItem("Background Jobs Panel");
        backgroundJobsItem.setSelected(true);
        viewMenu.getItems().add(backgroundJobsItem);
        
        // Tools menu
        Menu toolsMenu = new Menu("Tools");
        MenuItem inspectCVMModelItem = new MenuItem("Inspect CVM Model...");
        inspectCVMModelItem.setOnAction(e -> showCVMModelInspector());
        MenuItem clearCacheItem = new MenuItem("Clear Cache");
        MenuItem batchProcessItem = new MenuItem("Batch Processing");
        toolsMenu.getItems().addAll(inspectCVMModelItem, new SeparatorMenuItem(), 
                                   clearCacheItem, new SeparatorMenuItem(), batchProcessItem);
        
        // Help menu
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        MenuItem docsItem = new MenuItem("Documentation");
        helpMenu.getItems().addAll(aboutItem, new SeparatorMenuItem(), docsItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, toolsMenu, helpMenu);
        return menuBar;
    }
    
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 1 0 0 0;");
        
        Label statusLabel = new Label("Status: Idle");
        Label systemLabel = new Label("No system selected");
        Label memoryLabel = new Label("Memory: -- / -- MB");
        
        statusBar.getChildren().addAll(
            statusLabel,
            new Separator(),
            systemLabel,
            new Separator(),
            memoryLabel
        );
        
        return statusBar;
    }
    
    @Override
    public void stop() throws Exception {
        if (logConsolePanel != null) {
            logConsolePanel.shutdown();
        }
        if (jobManager != null) {
            jobManager.shutdown();
        }
        if (systemRegistry != null) {
            systemRegistry.shutdown();
        }
        super.stop();
    }
    
    private void showCVMModelInspector() {
        CVMModelInspectorDialog dialog = new CVMModelInspectorDialog(systemRegistry);
        dialog.showAndWait();
    }

    public static void main(String[] args) {
        // Parse --log-level flag before launching JavaFX
        Level logLevel = Level.INFO;
        for (String arg : args) {
            if (arg.startsWith("--log-level=")) {
                try {
                    logLevel = Level.parse(arg.substring("--log-level=".length()).toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        LoggingConfig.configure(logLevel);
        initialLogLevel = logLevel;
        launch(args);
    }
}