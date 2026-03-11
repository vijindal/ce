package org.ce.presentation.gui.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DoubleStringConverter;
import javafx.scene.layout.*;
import java.nio.file.Paths;
import java.util.TreeMap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.infrastructure.registry.KeyUtils;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.ce.domain.model.data.AllClusterData;
import org.ce.application.service.CECAssemblyService;

import java.util.*;
import java.util.logging.Logger;
import org.ce.infrastructure.logging.LoggingConfig;

/**
 * Inline panel for browsing, editing, and assembling CEC (Cluster Expansion Coefficient) databases.
 *
 * <p>Provides two tabs:
 * <ul>
 *   <li><b>CEC Browser:</b> View and edit CEC entries for individual systems</li>
 *   <li><b>CEC Assembly:</b> Assemble higher-order (ternary+) CECs from binary subsystems</li>
 * </ul>
 *
 * <p>Previously a modal dialog (CECDatabaseDialog), now integrated as an inline panel in the main window.
 */
public class CECManagementPanel extends VBox {

    private static final Logger LOG = LoggingConfig.getLogger(CECManagementPanel.class);

    private final SystemRegistry registry;
    private String workspacePath;

    // Browser tab state
    private SystemDataLoader.CECData currentCecData;
    private SystemIdentity currentBrowserSystem;
    private boolean editMode = false;

    // Assembly tab state
    private int[] assemblyCfOrderMap;
    private Map<Integer, double[]> assemblyTransformedByOrder;
    private AllClusterData assemblyTargetData;
    private SystemIdentity currentAssemblyTarget;

    public CECManagementPanel(SystemRegistry registry) {
        this.registry = registry;
        this.workspacePath = System.getProperty("user.home");

        // Initialize as VBox with content
        setSpacing(10);
        setPadding(new Insets(12));
        setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");

        // Create and add content
        getChildren().addAll(createContent());
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

        TableColumn<CecTermRow, Double> aCol = new TableColumn<>("Value (J/mol)");
        aCol.setCellValueFactory(cf -> new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().value));
        aCol.setPrefWidth(120);

        cecTable.getColumns().addAll(indexCol, nameCol, aCol);

        // Status and buttons
        Label statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #008000;");

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Button editButton = new Button("Edit Row");
        Button saveButton = new Button("Save");

        buttonBox.getChildren().addAll(editButton, saveButton);

        // System selection change handler
        systemCombo.setOnAction(e -> {
            SystemIdentity system = systemCombo.getValue();
            if (system != null) {
                loadCECData(system, cecTable, statusLabel);
            }
        });

        // Edit Row button handler
        editButton.setOnAction(e -> {
            CecTermRow selected = cecTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                statusLabel.setText("Please select a row to edit");
                statusLabel.setStyle("-fx-text-fill: #ff9900;");
                return;
            }
            editMode = !editMode;
            editButton.setText(editMode ? "Done Editing" : "Edit Row");
            editButton.setStyle(editMode ? "-fx-border-color: #ff9900;" : "");

            // Update column editability
            aCol.setEditable(editMode);

            if (editMode) {
                statusLabel.setText("Editing mode ON - modify values and click 'Done Editing'");
                statusLabel.setStyle("-fx-text-fill: #ff9900;");
            } else {
                statusLabel.setText("Editing complete");
                statusLabel.setStyle("-fx-text-fill: #008000;");
            }
            LOG.fine("CECManagementPanel.editButton — edit mode toggled: " + editMode);
        });

        // Make value column editable
        aCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        aCol.setOnEditCommit(event -> {
            CecTermRow row = event.getRowValue();
            Double newValue = event.getNewValue();
            if (row != null && newValue != null && currentCecData != null && currentCecData.cecTerms != null) {
                if (row.index < currentCecData.cecTerms.length) {
                    currentCecData.cecTerms[row.index].a = newValue;
                    row.value = newValue;
                    LOG.fine("CECManagementPanel.valueCol — updated index " + row.index + " to " + newValue);
                    cecTable.refresh();
                }
            }
        });

        cecTable.setEditable(false);  // Initially not editable

        // Save button handler
        saveButton.setOnAction(e -> {
            if (currentCecData == null || currentBrowserSystem == null) {
                statusLabel.setText("No CEC data loaded to save");
                statusLabel.setStyle("-fx-text-fill: #ff0000;");
                return;
            }
            try {
                // Populate model fields so saveCecData uses the full cecKey as directory
                currentCecData.structure = currentBrowserSystem.getStructure();
                currentCecData.phase     = currentBrowserSystem.getPhase();
                currentCecData.model     = currentBrowserSystem.getModel();
                SystemDataLoader.saveCecData(currentCecData,
                    Paths.get(System.getProperty("user.home")).resolve(".ce-workbench"));
                statusLabel.setText("Saved successfully to database");
                statusLabel.setStyle("-fx-text-fill: #008000;");
                editMode = false;
                editButton.setText("Edit Row");
                aCol.setEditable(false);
                cecTable.setEditable(false);
                LOG.fine("CECManagementPanel.saveButton — CEC data saved for system: " + currentBrowserSystem.getId());
            } catch (Exception ex) {
                statusLabel.setText("Error saving: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #ff0000;");
                LOG.warning("CECManagementPanel.saveButton — error saving: " + ex.getMessage());
            }
        });

        // Initial load
        if (!allSystems.isEmpty()) {
            loadCECData(allSystems.get(0), cecTable, statusLabel);
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

    /**
     * Extracts ECI values from CECData, handling both cecTerms and cecValues formats.
     */
    private double[] extractECIValues(SystemDataLoader.CECData cecData) {
        if (cecData.cecTerms != null) {
            double[] vals = new double[cecData.cecTerms.length];
            for (int i = 0; i < cecData.cecTerms.length; i++) {
                vals[i] = cecData.cecTerms[i].a;
            }
            return vals;
        } else if (cecData.cecValues != null) {
            return cecData.cecValues.clone();
        }
        return new double[0];
    }

    private void loadCECData(SystemIdentity system, TableView<CecTermRow> table,
                             Label statusLabel) {
        LOG.fine("CECManagementPanel.loadCECData — loading CEC for system: " + system.getId());

        String elements = String.join("-", system.getComponents());
        Optional<SystemDataLoader.CECData> cecData = SystemDataLoader.loadCecData(
            elements, system.getStructure(), system.getPhase(), system.getModel());

        table.getItems().clear();
        editMode = false;

        if (cecData.isPresent()) {
            SystemDataLoader.CECData data = cecData.get();
            this.currentCecData = data;
            this.currentBrowserSystem = system;

            int cecCount = 0;
            if (data.cecTerms != null) {
                for (int i = 0; i < data.cecTerms.length; i++) {
                    SystemDataLoader.CECTerm term = data.cecTerms[i];
                    table.getItems().add(new CecTermRow(i, term.name, term.a));
                }
                cecCount = data.cecTerms.length;
            } else if (data.cecValues != null) {
                for (int i = 0; i < data.cecValues.length; i++) {
                    double val = data.cecValues[i];
                    table.getItems().add(new CecTermRow(i, "ECI_" + i, val));
                }
                cecCount = data.cecValues.length;
            }

            statusLabel.setText("Loaded: " + cecCount + " terms, Units: " + data.cecUnits);
            statusLabel.setStyle("-fx-text-fill: #008000;");
            LOG.fine("CECManagementPanel.loadCECData — loaded " + table.getItems().size() + " CEC terms");
        } else {
            statusLabel.setText("CEC not found for system: " + system.getId());
            statusLabel.setStyle("-fx-text-fill: #ff0000;");
            LOG.warning("CECManagementPanel.loadCECData — CEC not found for system: " + system.getId());
        }
    }

    // Simple DTO for CEC table rows
    private static class CecTermRow {
        int index;
        String name;
        double value;

        CecTermRow(int index, String name, double value) {
            this.index = index;
            this.name = name;
            this.value = value;
        }
    }

    // ==================== TAB 2: CEC ASSEMBLY ====================

    private ScrollPane createAssemblyTab() {
        VBox tab = new VBox(10);
        tab.setPadding(new Insets(15));

        // Title
        Label title = new Label("CEC Assembly");
        title.setFont(Font.font("System", FontWeight.BOLD, 13));

        // Instruction
        Label instruction = new Label(
            "Assemble higher-order (ternary+) CECs from binary subsystem CECs + pure-order terms.");
        instruction.setStyle("-fx-font-size: 10; -fx-text-fill: #666666;");

        // Step 1: Target system selector
        VBox step1Box = new VBox(6);
        step1Box.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-padding: 10;");

        Label step1Label = new Label("STEP 1: Select Target System");
        step1Label.setFont(Font.font("System", FontWeight.BOLD, 11));

        HBox targetBox = new HBox(10);
        targetBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label targetLabel = new Label("Target System:");
        targetLabel.setPrefWidth(120);

        ComboBox<SystemIdentity> targetCombo = new ComboBox<>();
        targetCombo.setPrefWidth(350);
        targetCombo.setPromptText("Choose a target system (K >= 2)...");

        // Filter to systems with K >= 2 and AllClusterData available
        List<SystemIdentity> validSystems = registry.getAllSystems().stream()
            .filter(s -> s.getNumComponents() >= 2)
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
        step1Box.getChildren().addAll(step1Label, targetBox);

        // Step 2: Required subsystems validation
        VBox step2Box = new VBox(6);
        step2Box.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-padding: 10;");

        Label step2Label = new Label("STEP 2: Verify Required Subsystems");
        step2Label.setFont(Font.font("System", FontWeight.BOLD, 11));

        ScrollPane subsystemsScroll = new ScrollPane();
        VBox subsystemsContent = new VBox(8);
        subsystemsContent.setPadding(new Insets(5));
        subsystemsScroll.setContent(subsystemsContent);
        subsystemsScroll.setPrefHeight(150);
        subsystemsScroll.setStyle("-fx-control-inner-background: #ffffff;");

        Label subsystemsHelp = new Label("Shows all subsystems required to assemble the target system:");
        subsystemsHelp.setStyle("-fx-font-size: 9; -fx-text-fill: #666666;");

        step2Box.getChildren().addAll(step2Label, subsystemsHelp, subsystemsScroll);

        // Status
        Label statusLabel = new Label("Select a target system to begin");
        statusLabel.setStyle("-fx-text-fill: #666666;");

        // STEP 3: Assembly Results (initially hidden)
        VBox step3Box = new VBox(8);
        step3Box.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-padding: 10;");
        step3Box.setVisible(false);

        Label step3Label = new Label("STEP 3: Assembly Results");
        step3Label.setFont(Font.font("System", FontWeight.BOLD, 11));

        // 3a: Derived ECIs table
        Label derivedLabel = new Label("3a. Derived ECIs (from subsystem transformations) — READ ONLY");
        derivedLabel.setStyle("-fx-font-size: 10; -fx-font-weight: bold;");

        TableView<ECITableRow> derivedECIsTable = new TableView<>();
        derivedECIsTable.setPrefHeight(150);

        TableColumn<ECITableRow, Integer> derivedIndexCol = new TableColumn<>("CF Index");
        derivedIndexCol.setCellValueFactory(cf -> new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().cfIndex));
        derivedIndexCol.setPrefWidth(80);

        TableColumn<ECITableRow, Double> derivedValueCol = new TableColumn<>("Assembled ECI (J/mol)");
        derivedValueCol.setCellValueFactory(cf -> new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().value));
        derivedValueCol.setPrefWidth(200);

        derivedECIsTable.getColumns().addAll(derivedIndexCol, derivedValueCol);
        derivedECIsTable.setEditable(false);

        // 3b: Pure-K ECIs table
        Label pureKLabel = new Label("3b. Pure Order-K ECIs — ENTER VALUES MANUALLY");
        pureKLabel.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-padding: 10 0 0 0;");

        TableView<ECITableRow> pureKECIsTable = new TableView<>();
        pureKECIsTable.setPrefHeight(150);

        TableColumn<ECITableRow, Integer> pureIndexCol = new TableColumn<>("CF Index");
        pureIndexCol.setCellValueFactory(cf -> new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().cfIndex));
        pureIndexCol.setPrefWidth(80);

        TableColumn<ECITableRow, Double> pureValueCol = new TableColumn<>("ECI Value (J/mol)");
        pureValueCol.setCellValueFactory(cf -> new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().value));
        pureValueCol.setPrefWidth(200);
        pureValueCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        pureValueCol.setOnEditCommit(event -> {
            ECITableRow row = event.getRowValue();
            if (row != null) {
                row.value = event.getNewValue();
                pureKECIsTable.refresh();
            }
        });

        pureKECIsTable.getColumns().addAll(pureIndexCol, pureValueCol);
        pureKECIsTable.setEditable(true);

        step3Box.getChildren().addAll(
            step3Label,
            derivedLabel,
            derivedECIsTable,
            pureKLabel,
            pureKECIsTable
        );

        // Action buttons
        HBox actionBox = new HBox(10);
        Button assembleButton = new Button("ASSEMBLE");
        Button saveButton = new Button("SAVE Assembled CECs");
        assembleButton.setStyle("-fx-font-size: 10; -fx-padding: 8 15;");
        saveButton.setStyle("-fx-font-size: 10; -fx-padding: 8 15;");
        saveButton.setDisable(true);
        actionBox.getChildren().addAll(assembleButton, saveButton);

        // Save button handler
        saveButton.setOnAction(e -> {
            if (currentAssemblyTarget == null || assemblyCfOrderMap == null) {
                statusLabel.setText("Please assemble first");
                statusLabel.setStyle("-fx-text-fill: #ff0000;");
                return;
            }

            try {
                statusLabel.setText("Saving... please wait");
                statusLabel.setStyle("-fx-text-fill: #ff9900;");

                int K = currentAssemblyTarget.getNumComponents();
                int tcf = assemblyTargetData.getStage2().getTcf();

                // Collect pure-K values from table
                double[] pureKECIs = new double[pureKECIsTable.getItems().size()];
                for (int i = 0; i < pureKECIsTable.getItems().size(); i++) {
                    pureKECIs[i] = pureKECIsTable.getItems().get(i).value;
                }

                // Call assemble to combine derived + pure-K contributions
                double[] finalECIs = CECAssemblyService.assemble(
                    assemblyTransformedByOrder, pureKECIs, assemblyCfOrderMap, assemblyTargetData);

                // Build CECData
                SystemDataLoader.CECData cecData = new SystemDataLoader.CECData();
                cecData.elements  = String.join("-", currentAssemblyTarget.getComponents());
                cecData.structure = currentAssemblyTarget.getStructure();
                cecData.phase     = currentAssemblyTarget.getPhase();
                cecData.model     = currentAssemblyTarget.getModel();
                cecData.cecValues = finalECIs;
                cecData.cecUnits  = "J/mol";
                cecData.reference = currentAssemblyTarget.getId();
                cecData.tc        = tcf;

                // Save to workspace
                SystemDataLoader.saveCecData(cecData,
                    Paths.get(System.getProperty("user.home")).resolve(".ce-workbench"));

                statusLabel.setText("✓ Saved assembled CECs for " + currentAssemblyTarget.getId());
                statusLabel.setStyle("-fx-text-fill: #008000;");
                LOG.fine("Assembly: saved CECs for " + currentAssemblyTarget.getId() + " to database");

            } catch (Exception ex) {
                statusLabel.setText("Save error: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #ff0000;");
                LOG.warning("Assembly save error: " + ex.getMessage());
            }
        });

        // Target system selection handler
        targetCombo.setOnAction(e -> {
            SystemIdentity target = targetCombo.getValue();
            if (target != null) {
                subsystemsContent.getChildren().clear();

                // Get all subsystems by order
                Map<Integer, List<List<String>>> subsystemsByOrder = CECAssemblyService
                    .subsystemsByOrder(target.getComponents());

                int K = target.getNumComponents();
                boolean allExists = true;
                int totalSubsystems = 0;
                int foundSubsystems = 0;

                for (int order = 2; order < K; order++) {
                    List<List<String>> subsystemsAtOrder = subsystemsByOrder.getOrDefault(order, new ArrayList<>());
                    if (subsystemsAtOrder.isEmpty()) continue;

                    // Create section for this order
                    VBox orderSection = new VBox(4);
                    orderSection.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 0 0 1 0; -fx-padding: 5;");

                    Label orderLabel = new Label("Order " + order + " Subsystems:");
                    orderLabel.setFont(Font.font("System", FontWeight.BOLD, 10));

                    VBox subsystemList = new VBox(2);
                    subsystemList.setPadding(new Insets(3, 0, 3, 15));

                    for (List<String> subsys : subsystemsAtOrder) {
                        String subsysKey = CECAssemblyService.toElementString(subsys);
                        HBox subsysRow = new HBox(10);

                        Label subsysLabel = new Label(subsysKey);
                        subsysLabel.setPrefWidth(120);

                        // Check if CEC exists
                        boolean cecExists = SystemDataLoader.cecExists(subsysKey, target.getStructure(),
                            target.getPhase(), target.getModel());
                        totalSubsystems++;
                        if (cecExists) foundSubsystems++;
                        else allExists = false;

                        Label statusIcon = new Label(cecExists ? "✓" : "✗");
                        statusIcon.setPrefWidth(30);
                        if (cecExists) {
                            statusIcon.setStyle("-fx-text-fill: #008000; -fx-font-weight: bold;");
                        } else {
                            statusIcon.setStyle("-fx-text-fill: #ff0000; -fx-font-weight: bold;");
                        }

                        subsysRow.getChildren().addAll(statusIcon, subsysLabel);
                        subsystemList.getChildren().add(subsysRow);
                    }

                    orderSection.getChildren().addAll(orderLabel, subsystemList);
                    subsystemsContent.getChildren().add(orderSection);
                }

                // Update status
                if (allExists) {
                    statusLabel.setText("✓ All " + foundSubsystems + " subsystems found. Ready to assemble!");
                    statusLabel.setStyle("-fx-text-fill: #008000;");
                    assembleButton.setDisable(false);
                } else {
                    statusLabel.setText("✗ Missing " + (totalSubsystems - foundSubsystems) + " of " + totalSubsystems
                        + " subsystems. Cannot assemble.");
                    statusLabel.setStyle("-fx-text-fill: #ff0000;");
                    assembleButton.setDisable(true);
                }

                LOG.fine("CECManagementPanel.Assembly — target system selected: " + target.getId()
                    + " with K=" + K + " components (" + foundSubsystems + "/" + totalSubsystems + " subsystems found)");
            }
        });

        // Assemble handler
        assembleButton.setOnAction(e -> {
            SystemIdentity target = targetCombo.getValue();
            if (target == null) {
                statusLabel.setText("Please select a target system");
                statusLabel.setStyle("-fx-text-fill: #ff0000;");
                return;
            }

            try {
                statusLabel.setText("Assembling... please wait");
                statusLabel.setStyle("-fx-text-fill: #ff9900;");

                // 1. Load AllClusterData
                String clusterKey = KeyUtils.clusterKey(target);
                Optional<AllClusterData> dataOpt = AllClusterDataCache.load(clusterKey);
                if (dataOpt.isEmpty() || !dataOpt.get().isComplete()) {
                    statusLabel.setText("Error: AllClusterData not available for " + target.getId());
                    statusLabel.setStyle("-fx-text-fill: #ff0000;");
                    LOG.warning("Assembly: AllClusterData not found for " + clusterKey);
                    return;
                }

                AllClusterData targetData = dataOpt.get();
                int K = target.getNumComponents();
                int tcf = targetData.getStage2().getTcf();

                // 2. Classify CFs by minimum order
                int[] cfOrderMap = CECAssemblyService.classifyCFsByOrder(targetData);

                // 3. For each subsystem order, load CEC and transform
                Map<Integer, List<List<String>>> subsystemsByOrder = CECAssemblyService.subsystemsByOrder(target.getComponents());
                Map<Integer, double[]> transformedByOrder = new TreeMap<>();

                for (int order = 2; order < K; order++) {
                    List<List<String>> subsystems = subsystemsByOrder.getOrDefault(order, new ArrayList<>());
                    if (subsystems.isEmpty()) continue;

                    double[] orderContributions = new double[tcf];

                    for (List<String> subsys : subsystems) {
                        String subsysKey = CECAssemblyService.toElementString(subsys);
                        Optional<SystemDataLoader.CECData> cecDataOpt = SystemDataLoader.loadCecData(
                            subsysKey, target.getStructure(), target.getPhase(), target.getModel());

                        if (cecDataOpt.isEmpty()) {
                            statusLabel.setText("Error: CEC not found for subsystem " + subsysKey);
                            statusLabel.setStyle("-fx-text-fill: #ff0000;");
                            LOG.warning("Assembly: CEC not found for " + subsysKey);
                            return;
                        }

                        SystemDataLoader.CECData cecData = cecDataOpt.get();
                        double[] sourceECIs = extractECIValues(cecData);

                        double[] transformed = CECAssemblyService.transformToTarget(
                            sourceECIs, order, K, cfOrderMap, targetData);

                        // Accumulate contributions for this order
                        for (int i = 0; i < tcf && i < transformed.length; i++) {
                            orderContributions[i] += transformed[i];
                        }

                        LOG.fine("Assembly: transformed " + subsysKey + " (order " + order + ")");
                    }

                    transformedByOrder.put(order, orderContributions);
                }

                // 4. Save state for Save button
                this.assemblyCfOrderMap = cfOrderMap;
                this.assemblyTransformedByOrder = transformedByOrder;
                this.assemblyTargetData = targetData;
                this.currentAssemblyTarget = target;

                // 5. Compute accumulated derived ECIs
                double[] derivedECIs = new double[tcf];
                for (double[] contributions : transformedByOrder.values()) {
                    for (int i = 0; i < tcf && i < contributions.length; i++) {
                        derivedECIs[i] += contributions[i];
                    }
                }

                // 6. Populate derived ECIs table
                derivedECIsTable.getItems().clear();
                for (int i = 0; i < cfOrderMap.length; i++) {
                    if (cfOrderMap[i] < K) {
                        derivedECIsTable.getItems().add(new ECITableRow(i, derivedECIs[i]));
                    }
                }

                // 7. Populate pure-K ECIs table with default 0.0
                pureKECIsTable.getItems().clear();
                for (int i = 0; i < cfOrderMap.length; i++) {
                    if (cfOrderMap[i] == K) {
                        pureKECIsTable.getItems().add(new ECITableRow(i, 0.0));
                    }
                }

                // 8. Show results section and enable Save
                step3Box.setVisible(true);
                saveButton.setDisable(false);

                statusLabel.setText("✓ Assembly complete! Enter pure-K ECI values and click Save.");
                statusLabel.setStyle("-fx-text-fill: #008000;");
                LOG.fine("Assembly: complete for " + target.getId() + " (tcf=" + tcf + ")");

            } catch (Exception ex) {
                statusLabel.setText("Assembly error: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #ff0000;");
                LOG.warning("Assembly error: " + ex.getMessage());
            }
        });

        assembleButton.setDisable(true);

        tab.getChildren().addAll(
            title,
            instruction,
            new Separator(),
            step1Box,
            step2Box,
            step3Box,
            new Separator(),
            actionBox,
            statusLabel
        );

        // Wrap in ScrollPane and return
        ScrollPane scrollPane = new ScrollPane(tab);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }

    /**
     * Simple DTO for ECI table rows.
     */
    private static class ECITableRow {
        int cfIndex;
        double value;

        ECITableRow(int cfIndex, double value) {
            this.cfIndex = cfIndex;
            this.value = value;
        }
    }
}
