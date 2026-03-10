package org.ce.presentation.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.ce.infrastructure.service.BackgroundJobManager;
import org.ce.infrastructure.registry.ResultRepository;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.presentation.gui.component.CVMModelInspectorDialog;
import org.ce.presentation.gui.component.CECDatabaseDialog;
import org.ce.presentation.gui.view.CalculationSetupPanel;
import org.ce.presentation.gui.view.LogConsolePanel;
import org.ce.presentation.gui.view.SystemRegistryPanel;
import org.ce.presentation.gui.view.ResultsPanel;
import org.ce.domain.system.SystemIdentity;

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
    private SystemRegistryPanel registryPanel;
    private ResultsPanel resultsPanel;
    private LogConsolePanel logConsolePanel;

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
        // Initialize registry with workspace directory
        String userHome = System.getProperty("user.home");
        systemRegistry = new SystemRegistry(Paths.get(userHome));
        resultRepository = new ResultRepository(Paths.get(userHome).resolve(".ce-workbench"));
        
        // Initialize job manager with 2 concurrent jobs
        jobManager = new BackgroundJobManager(2);
        
        // Register test systems that have cached data
        registerCachedTestSystems();
    }
    
    /**
     * Registers test systems that have pre-computed cluster data in the cache.
     * This ensures the CVM Model Inspector and other tools can access these systems.
     */
    private void registerCachedTestSystems() {
        try {
            // Check if A-B test system exists in cache
            String clusterKey = "BCC_A2_T_bin";
            Optional<org.ce.domain.model.data.AllClusterData> cachedData =
                org.ce.infrastructure.persistence.AllClusterDataCache.load(clusterKey);

            if (cachedData.isPresent() && cachedData.get().isComplete()) {
                String systemId = "A-B_BCC_A2_T";
                // Only register if not already present
                if (systemRegistry.getSystem(systemId) == null) {
                    SystemIdentity testSystem = SystemIdentity.builder()
                        .id(systemId)
                        .name("A-B BCC A2 (T)")
                        .structure("BCC")
                        .phase("A2")
                        .model("T")
                        .components(new String[]{"A", "B"})
                        .clusterFilePath("cluster/A2-T.txt")
                        .symmetryGroupName("A2-SG")
                        .build();

                    systemRegistry.registerSystem(testSystem);
                    systemRegistry.markClustersComputed(systemId, true);
                    systemRegistry.markCfsComputed(systemId, true);
                    systemRegistry.markCecAvailable(systemId, true);

                    LOG.info("Auto-registered test system: " + systemId);
                }
            }

            // Register Nb-Ti system (same cluster key as A-B since both are K=2 BCC_A2_T)
            if (cachedData.isPresent() && cachedData.get().isComplete()) {
                String systemId = "Nb-Ti_BCC_A2_T";
                if (systemRegistry.getSystem(systemId) == null) {
                    SystemIdentity nbTiSystem = SystemIdentity.builder()
                        .id(systemId)
                        .name("Nb-Ti BCC A2 (T)")
                        .structure("BCC")
                        .phase("A2")
                        .model("T")
                        .components(new String[]{"Nb", "Ti"})
                        .clusterFilePath("cluster/A2-T.txt")
                        .symmetryGroupName("A2-SG")
                        .build();

                    systemRegistry.registerSystem(nbTiSystem);
                    systemRegistry.markClustersComputed(systemId, true);
                    systemRegistry.markCfsComputed(systemId, true);
                    systemRegistry.markCecAvailable(systemId, true);

                    LOG.info("Auto-registered test system: " + systemId);
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to register test systems: " + e.getMessage());
        }
    }
    
    private void buildUI(Stage stage) {
        // Root layout
        BorderPane root = new BorderPane();
        root.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11;");
        
        // Top: Menu bar
        root.setTop(createMenuBar());
        
        // Center: Splitter with left (system registry) and right (results) panels
        SplitPane mainSplitter = new SplitPane();
        mainSplitter.setDividerPositions(0.3);
        
        // Create shared results panel
        resultsPanel = new ResultsPanel();
        
        // Left panel: System registry + calculation setup
        registryPanel = new SystemRegistryPanel(systemRegistry, jobManager, resultsPanel);
        mainSplitter.getItems().add(registryPanel);
        
        // Right panel: Single Results tab
        TabPane rightPanel = createRightPanel();
        mainSplitter.getItems().add(rightPanel);
        
        root.setCenter(mainSplitter);
        
        // Bottom: Status bar
        root.setBottom(createStatusBar());
        
        // Create and display scene
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

        // Database menu
        Menu databaseMenu = new Menu("Database");
        MenuItem cecDatabaseItem = new MenuItem("CEC Database...");
        cecDatabaseItem.setOnAction(e -> showCECDatabase());
        databaseMenu.getItems().add(cecDatabaseItem);

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

        menuBar.getMenus().addAll(fileMenu, editMenu, databaseMenu, viewMenu, toolsMenu, helpMenu);
        return menuBar;
    }
    
    private TabPane createRightPanel() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Result tab
        Tab resultTab = new Tab("Result", resultsPanel);

        // Log tab — captures JUL output from the org.ce hierarchy
        logConsolePanel = new LogConsolePanel(initialLogLevel);
        Tab logTab = new Tab("Log", logConsolePanel);

        tabPane.getTabs().addAll(resultTab, logTab);
        return tabPane;
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

    private void showCECDatabase() {
        CECDatabaseDialog dialog = new CECDatabaseDialog(systemRegistry);
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

