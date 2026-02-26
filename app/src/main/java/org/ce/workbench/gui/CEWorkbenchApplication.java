package org.ce.workbench.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.ce.workbench.backend.job.BackgroundJobManager;
import org.ce.workbench.backend.registry.SystemRegistry;
import org.ce.workbench.gui.view.CalculationSetupPanel;
import org.ce.workbench.gui.view.SystemRegistryPanel;

import java.nio.file.Paths;

/**
 * Main JavaFX application for the CE Thermodynamics Workbench GUI.
 * Entry point and orchestrator for all UI components.
 */
public class CEWorkbenchApplication extends Application {
    
    private SystemRegistry systemRegistry;
    private BackgroundJobManager jobManager;
    private SystemRegistryPanel registryPanel;
    
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
        
        // Initialize job manager with 2 concurrent jobs
        jobManager = new BackgroundJobManager(2);
    }
    
    private void buildUI(Stage stage) {
        // Root layout
        BorderPane root = new BorderPane();
        root.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11;");
        
        // Top: Menu bar
        root.setTop(createMenuBar());
        
        // Center: Splitter with left (system registry) and right (calculations) panels
        SplitPane mainSplitter = new SplitPane();
        mainSplitter.setDividerPositions(0.25);
        
        // Left panel: System registry + background jobs
        registryPanel = new SystemRegistryPanel(systemRegistry, jobManager);
        mainSplitter.getItems().add(registryPanel);
        
        // Right panel: Tabbed calculation interface (placeholder for now)
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
        
        // View menu
        Menu viewMenu = new Menu("View");
        CheckMenuItem backgroundJobsItem = new CheckMenuItem("Background Jobs Panel");
        backgroundJobsItem.setSelected(true);
        viewMenu.getItems().add(backgroundJobsItem);
        
        // Tools menu
        Menu toolsMenu = new Menu("Tools");
        MenuItem clearCacheItem = new MenuItem("Clear Cache");
        MenuItem batchProcessItem = new MenuItem("Batch Processing");
        toolsMenu.getItems().addAll(clearCacheItem, new SeparatorMenuItem(), batchProcessItem);
        
        // Help menu
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        MenuItem docsItem = new MenuItem("Documentation");
        helpMenu.getItems().addAll(aboutItem, new SeparatorMenuItem(), docsItem);
        
        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, toolsMenu, helpMenu);
        return menuBar;
    }
    
    private TabPane createRightPanel() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Tab 1: Calculation Setup
        Tab setupTab = new Tab("Setup", createSetupPanelContent());
        setupTab.setStyle("-fx-font-weight: bold;");
        
        // Tab 2: Monitor
        Tab monitorTab = new Tab("Monitor", createMonitorPanelContent());
        
        // Tab 3: Results
        Tab resultsTab = new Tab("Results", createResultsPanelContent());
        
        // Tab 4: Visualization
        Tab visTab = new Tab("Visualization", createVisualizationContent());
        
        // Tab 5: Batch
        Tab batchTab = new Tab("Batch", createBatchContent());
        
        tabPane.getTabs().addAll(setupTab, monitorTab, resultsTab, visTab, batchTab);
        return tabPane;
    }
    
    private VBox createSetupPanelContent() {
        return new CalculationSetupPanel(systemRegistry);
    }
    
    private VBox createMonitorPanelContent() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(15));
        
        Label label = new Label("Live Calculation Monitor");
        label.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        VBox placeholder = new VBox(10);
        placeholder.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");
        placeholder.setPadding(new Insets(20));
        placeholder.getChildren().add(new Label("Monitor panel content coming soon..."));
        
        vbox.getChildren().addAll(label, placeholder);
        return vbox;
    }
    
    private VBox createResultsPanelContent() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(15));
        
        Label label = new Label("Results Viewer");
        label.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        VBox placeholder = new VBox(10);
        placeholder.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");
        placeholder.setPadding(new Insets(20));
        placeholder.getChildren().add(new Label("Results panel content coming soon..."));
        
        vbox.getChildren().addAll(label, placeholder);
        return vbox;
    }
    
    private VBox createVisualizationContent() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(15));
        
        Label label = new Label("Visualization & Analysis");
        label.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        VBox placeholder = new VBox(10);
        placeholder.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");
        placeholder.setPadding(new Insets(20));
        placeholder.getChildren().add(new Label("Visualization panel content coming soon..."));
        
        vbox.getChildren().addAll(label, placeholder);
        return vbox;
    }
    
    private VBox createBatchContent() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(15));
        
        Label label = new Label("Batch Processing & Comparison");
        label.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        VBox placeholder = new VBox(10);
        placeholder.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");
        placeholder.setPadding(new Insets(20));
        placeholder.getChildren().add(new Label("Batch panel content coming soon..."));
        
        vbox.getChildren().addAll(label, placeholder);
        return vbox;
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
        if (jobManager != null) {
            jobManager.shutdown();
        }
        super.stop();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
