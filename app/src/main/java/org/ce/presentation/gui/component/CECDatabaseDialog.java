package org.ce.presentation.gui.component;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.infrastructure.registry.KeyUtils;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.ce.domain.model.data.AllClusterData;
import org.ce.application.service.CECAssemblyService;

import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import org.ce.infrastructure.logging.LoggingConfig;

/**
 * Dialog for browsing, editing, and assembling CEC (Cluster Expansion Coefficient) databases.
 *
 * <p>Provides two tabs:
 * <ul>
 *   <li><b>CEC Browser:</b> View and edit CEC entries for individual systems</li>
 *   <li><b>CEC Assembly:</b> Assemble higher-order (ternary+) CECs from binary subsystems</li>
 * </ul>
 */
public class CECDatabaseDialog extends Dialog<Void> {

    private static final Logger LOG = LoggingConfig.getLogger(CECDatabaseDialog.class);

    private final SystemRegistry registry;
    private String workspacePath;

    public CECDatabaseDialog(SystemRegistry registry) {
        this.registry = registry;
        this.workspacePath = System.getProperty("user.home");

        setTitle("CEC Database");
        setHeaderText("Manage Cluster Expansion Coefficients (CECs)");
        setWidth(1000);
        setHeight(700);

        DialogPane dialogPane = getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.CLOSE);
        dialogPane.setContent(createContent());

        setResultConverter(buttonType -> null);
    }

    private VBox createContent() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: CEC Browser
        Tab browserTab = new Tab("CEC Browser", createBrowserTab());
        browserTab.setClosable(false);

        // Tab 2: CEC Assembly
        Tab assemblyTab = new Tab("CEC Assembly", createAssemblyTab());
        assemblyTab.setClosable(false);

        tabPane.getTabs().addAll(browserTab, assemblyTab);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.getChildren().add(tabPane);

        return root;
    }

    // ==================== TAB 1: CEC BROWSER ====================

    private VBox createBrowserTab() {
        VBox tab = new VBox(10);
        tab.setPadding(new Insets(15));

        // Title
        Label title = new Label("CEC Browser");
        title.setFont(Font.font("System", FontWeight.BOLD, 13));

        // System selector
        HBox systemBox = new HBox(10);
        systemBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label systemLabel = new Label("Select System:");
        systemLabel.setPrefWidth(80);

        ComboBox<SystemIdentity> systemCombo = new ComboBox<>();
        systemCombo.setPrefWidth(300);
        systemCombo.setPromptText("Choose a system...");
        List<SystemIdentity> allSystems = new ArrayList<>(registry.getAllSystems());
        systemCombo.getItems().addAll(allSystems);

        if (!allSystems.isEmpty()) {
            systemCombo.getSelectionModel().selectFirst();
        }

        // Custom cell factory to display system names
        systemCombo.setCellFactory(lv -> new ListCell<SystemIdentity>() {
            @Override
            protected void updateItem(SystemIdentity item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + " (K=" + item.getNumComponents() + ")");
            }
        });
        systemCombo.setButtonCell(new ListCell<SystemIdentity>() {
            @Override
            protected void updateItem(SystemIdentity item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        systemBox.getChildren().addAll(systemLabel, systemCombo);

        // CEC table
        TableView<CecTermRow> cecTable = new TableView<>();
        cecTable.setPrefHeight(400);

        TableColumn<CecTermRow, Integer> indexCol = new TableColumn<>("Index");
        indexCol.setCellValueFactory(cf -> new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().index));
        indexCol.setPrefWidth(60);

        TableColumn<CecTermRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cf -> new javafx.beans.property.SimpleStringProperty(cf.getValue().name));
        nameCol.setPrefWidth(150);

        TableColumn<CecTermRow, Double> aCol = new TableColumn<>("a (J/mol)");
        aCol.setCellValueFactory(cf -> new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().a));
        aCol.setPrefWidth(100);

        TableColumn<CecTermRow, Double> bCol = new TableColumn<>("b (J/(mol·K))");
        bCol.setCellValueFactory(cf -> new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().b));
        bCol.setPrefWidth(120);

        TableColumn<CecTermRow, Double> eciCol = new TableColumn<>("ECI @ T");
        eciCol.setCellValueFactory(cf -> new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().eciAtT));
        eciCol.setPrefWidth(100);

        cecTable.getColumns().addAll(indexCol, nameCol, aCol, bCol, eciCol);

        // Status and buttons
        Label statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #008000;");

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TextField tempField = new TextField("298.15");
        tempField.setPrefWidth(100);
        Label tempLabel = new Label("Temperature (K):");

        Button editButton = new Button("Edit Row");
        Button saveButton = new Button("Save");

        buttonBox.getChildren().addAll(tempLabel, tempField, editButton, saveButton);

        // System selection change handler
        systemCombo.setOnAction(e -> {
            SystemIdentity system = systemCombo.getValue();
            if (system != null) {
                loadCECData(system, cecTable, statusLabel, tempField);
            }
        });

        // Initial load
        if (!allSystems.isEmpty()) {
            loadCECData(allSystems.get(0), cecTable, statusLabel, tempField);
        }

        tab.getChildren().addAll(
            title,
            systemBox,
            new Separator(),
            new Label("CEC Table:"),
            cecTable,
            statusLabel,
            buttonBox
        );

        return tab;
    }

    private void loadCECData(SystemIdentity system, TableView<CecTermRow> table,
                             Label statusLabel, TextField tempField) {
        LOG.fine("CECDatabaseDialog.loadCECData — loading CEC for system: " + system.getId());

        String elements = String.join("-", system.getComponents());
        Optional<SystemDataLoader.CECData> cecData = SystemDataLoader.loadCecData(
            elements, system.getStructure(), system.getPhase(), system.getModel());

        table.getItems().clear();

        if (cecData.isPresent()) {
            SystemDataLoader.CECData data = cecData.get();
            double temp = 298.15;
            try {
                temp = Double.parseDouble(tempField.getText());
            } catch (NumberFormatException ignored) {}

            if (data.cecTerms != null) {
                for (int i = 0; i < data.cecTerms.length; i++) {
                    SystemDataLoader.CECTerm term = data.cecTerms[i];
                    double eciAtT = term.a + term.b * temp;
                    table.getItems().add(new CecTermRow(i, term.name, term.a, term.b, eciAtT));
                }
            } else if (data.cecValues != null) {
                for (int i = 0; i < data.cecValues.length; i++) {
                    double val = data.cecValues[i];
                    table.getItems().add(new CecTermRow(i, "ECI_" + i, val, 0.0, val));
                }
            }

            statusLabel.setText("Loaded: " + data.cecTerms.length + " terms, Units: " + data.cecUnits);
            statusLabel.setStyle("-fx-text-fill: #008000;");
            LOG.fine("CECDatabaseDialog.loadCECData — loaded " + table.getItems().size() + " CEC terms");
        } else {
            statusLabel.setText("CEC not found for system: " + system.getId());
            statusLabel.setStyle("-fx-text-fill: #ff0000;");
            LOG.warning("CECDatabaseDialog.loadCECData — CEC not found for system: " + system.getId());
        }
    }

    // Simple DTO for CEC table rows
    private static class CecTermRow {
        int index;
        String name;
        double a;
        double b;
        double eciAtT;

        CecTermRow(int index, String name, double a, double b, double eciAtT) {
            this.index = index;
            this.name = name;
            this.a = a;
            this.b = b;
            this.eciAtT = eciAtT;
        }
    }

    // ==================== TAB 2: CEC ASSEMBLY ====================

    private VBox createAssemblyTab() {
        VBox tab = new VBox(10);
        tab.setPadding(new Insets(15));

        // Title
        Label title = new Label("CEC Assembly");
        title.setFont(Font.font("System", FontWeight.BOLD, 13));

        // Target system selector
        HBox targetBox = new HBox(10);
        targetBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label targetLabel = new Label("Target System:");
        targetLabel.setPrefWidth(100);

        ComboBox<SystemIdentity> targetCombo = new ComboBox<>();
        targetCombo.setPrefWidth(350);
        targetCombo.setPromptText("Choose a target system (K >= 2)...");

        // Filter to systems with AllClusterData
        List<SystemIdentity> validSystems = registry.getAllSystems().stream()
            .filter(s -> {
                String clusterKey = KeyUtils.clusterKey(s);
                try {
                    Optional<AllClusterData> data = AllClusterDataCache.load(clusterKey);
                    return data.isPresent() && data.get().isComplete();
                } catch (Exception e) {
                    return false;
                }
            })
            .toList();

        targetCombo.getItems().addAll(validSystems);

        // Custom cell factory
        targetCombo.setCellFactory(lv -> new ListCell<SystemIdentity>() {
            @Override
            protected void updateItem(SystemIdentity item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + " (K=" + item.getNumComponents() + ")");
            }
        });
        targetCombo.setButtonCell(new ListCell<SystemIdentity>() {
            @Override
            protected void updateItem(SystemIdentity item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        targetBox.getChildren().addAll(targetLabel, targetCombo);

        // Temperature field
        HBox tempBox = new HBox(10);
        Label tempLabel = new Label("Temperature (K):");
        tempLabel.setPrefWidth(100);
        TextField tempField = new TextField("298.15");
        tempField.setPrefWidth(100);
        tempBox.getChildren().addAll(tempLabel, tempField);

        // Subsystems panel (placeholder for now)
        VBox subsystemsPanel = new VBox(8);
        subsystemsPanel.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-padding: 10;");
        Label subsystemsTitle = new Label("Binary Subsystems:");
        subsystemsTitle.setFont(Font.font("System", FontWeight.BOLD, 11));
        TextArea subsystemsDisplay = new TextArea();
        subsystemsDisplay.setEditable(false);
        subsystemsDisplay.setPrefHeight(150);
        subsystemsDisplay.setWrapText(true);

        subsystemsPanel.getChildren().addAll(subsystemsTitle, subsystemsDisplay);

        // Buttons
        HBox actionBox = new HBox(10);
        Button assembleButton = new Button("Assemble");
        Button saveButton = new Button("Save Assembled CECs");
        actionBox.getChildren().addAll(assembleButton, saveButton);

        // Status
        Label statusLabel = new Label("Select a target system");
        statusLabel.setStyle("-fx-text-fill: #666666;");

        // Target system selection handler
        targetCombo.setOnAction(e -> {
            SystemIdentity target = targetCombo.getValue();
            if (target != null) {
                List<List<String>> subsystemsByOrder = CECAssemblyService
                    .subsystemsByOrder(target.getComponents()).getOrDefault(2, new ArrayList<>());
                StringBuilder sb = new StringBuilder();
                sb.append("Binary subsystems for ").append(target.getName()).append(":\n");
                for (List<String> subsys : subsystemsByOrder) {
                    sb.append("  - ").append(CECAssemblyService.toElementString(subsys)).append("\n");
                }
                subsystemsDisplay.setText(sb.toString());
                statusLabel.setText("Ready to assemble");
                statusLabel.setStyle("-fx-text-fill: #008000;");
                LOG.fine("CECDatabaseDialog.Assembly — target system selected: " + target.getId());
            }
        });

        // Assemble handler (placeholder)
        assembleButton.setOnAction(e -> {
            SystemIdentity target = targetCombo.getValue();
            if (target != null) {
                statusLabel.setText("Assembly in progress... (placeholder)");
                statusLabel.setStyle("-fx-text-fill: #ff9900;");
                LOG.fine("CECDatabaseDialog.Assembly — assemble button clicked for: " + target.getId());
            } else {
                statusLabel.setText("Please select a target system");
                statusLabel.setStyle("-fx-text-fill: #ff0000;");
            }
        });

        tab.getChildren().addAll(
            title,
            targetBox,
            tempBox,
            new Separator(),
            subsystemsPanel,
            new Separator(),
            actionBox,
            statusLabel
        );

        return tab;
    }
}
