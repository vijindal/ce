package org.ce.presentation.gui.view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DoubleStringConverter;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.ce.application.job.AssemblyResult;
import org.ce.application.service.CECAssemblyService;
import org.ce.application.service.CECManagementService;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.logging.LoggingConfig;
import org.ce.infrastructure.registry.KeyUtils;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.infrastructure.service.CECManagementCoordinator;

import java.util.*;
import java.util.logging.Logger;

/**
 * Inline panel for browsing, editing, and assembling CEC databases.
 *
 * <p><b>Refactored (T1.3 / T1.4):</b></p>
 * <ul>
 *   <li>All CEC I/O now goes through {@link CECManagementService} — no direct calls
 *       to {@link org.ce.infrastructure.data.SystemDataLoader} or raw workspace paths.</li>
 *   <li>Assembly is delegated to {@link CECManagementCoordinator}, which runs it on the
 *       executor thread via {@link org.ce.application.job.CECAssemblyJob} — assembly no
 *       longer blocks the JavaFX thread.</li>
 *   <li>The four mutable assembly-state fields have been replaced by a single
 *       immutable {@link AssemblyResult} snapshot.</li>
 * </ul>
 */
public class CECManagementPanel extends VBox {

    private static final Logger LOG = LoggingConfig.getLogger(CECManagementPanel.class);

    private final SystemRegistry registry;
    private final CECManagementService cecService;
    private final CECManagementCoordinator coordinator;

    // Browser tab state
    private SystemDataLoader.CECData currentCecData;
    private SystemIdentity           currentBrowserSystem;
    private boolean editMode = false;

    // Assembly tab state — single immutable snapshot (replaces 4 mutable fields)
    private AssemblyResult lastAssemblyResult;

    // Assembly tab UI references (needed in callbacks)
    private TableView<ECITableRow> derivedECIsTable;
    private TableView<ECITableRow> pureKECIsTable;
    private VBox    step3Box;
    private Button  assemblyActionSaveButton;
    private Label   assemblyStatusLabel;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    public CECManagementPanel(SystemRegistry registry,
                              CECManagementService cecService,
                              CECManagementCoordinator coordinator) {
        this.registry    = registry;
        this.cecService  = cecService;
        this.coordinator = coordinator;

        setSpacing(10);
        setPadding(new Insets(12));
        setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");

        // Wire coordinator callbacks — delivered on the FX thread
        coordinator.setOnAssemblyCompleted(this::onAssemblyCompleted);
        coordinator.setOnAssemblyFailed(this::onAssemblyFailed);

        getChildren().addAll(createContent());
    }

    // -----------------------------------------------------------------------
    // Content
    // -----------------------------------------------------------------------

    private VBox createContent() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
            new Tab("CEC Browser",  createBrowserTab()),
            new Tab("CEC Assembly", createAssemblyTab())
        );

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.getChildren().add(tabPane);
        return root;
    }

    // ==================== TAB 1: CEC BROWSER ====================

    private VBox createBrowserTab() {
        VBox tab = new VBox(10);
        tab.setPadding(new Insets(15));

        Label title = new Label("CEC Browser");
        title.setFont(Font.font("System", FontWeight.BOLD, 13));

        ComboBox<SystemIdentity> systemCombo = new ComboBox<>();
        systemCombo.setPrefWidth(300);
        systemCombo.setPromptText("Choose a system...");
        systemCombo.getItems().addAll(registry.getAllSystems());
        if (!systemCombo.getItems().isEmpty()) systemCombo.getSelectionModel().selectFirst();
        applySystemCellFactory(systemCombo);

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
        aCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        aCol.setOnEditCommit(ev -> {
            CecTermRow row = ev.getRowValue();
            if (row != null && currentCecData != null && currentCecData.cecTerms != null
                    && row.index < currentCecData.cecTerms.length) {
                currentCecData.cecTerms[row.index].a = ev.getNewValue();
                row.value = ev.getNewValue();
                cecTable.refresh();
            }
        });

        cecTable.getColumns().addAll(indexCol, nameCol, aCol);
        cecTable.setEditable(false);

        Label statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #008000;");

        Button editButton = new Button("Edit Row");
        Button saveButton = new Button("Save");
        HBox   btnBox     = new HBox(10, editButton, saveButton);

        systemCombo.setOnAction(e -> {
            SystemIdentity s = systemCombo.getValue();
            if (s != null) loadCECData(s, cecTable, statusLabel);
        });

        editButton.setOnAction(e -> {
            if (cecTable.getSelectionModel().getSelectedItem() == null) {
                statusLabel.setText("Please select a row to edit");
                statusLabel.setStyle("-fx-text-fill: #ff9900;"); return;
            }
            editMode = !editMode;
            editButton.setText(editMode ? "Done Editing" : "Edit Row");
            editButton.setStyle(editMode ? "-fx-border-color: #ff9900;" : "");
            aCol.setEditable(editMode);
            cecTable.setEditable(editMode);
            statusLabel.setText(editMode ? "Editing mode ON" : "Editing complete");
            statusLabel.setStyle(editMode ? "-fx-text-fill: #ff9900;" : "-fx-text-fill: #008000;");
        });

        saveButton.setOnAction(e -> {
            if (currentCecData == null || currentBrowserSystem == null) {
                statusLabel.setText("No CEC data loaded to save");
                statusLabel.setStyle("-fx-text-fill: #ff0000;"); return;
            }
            try {
                currentCecData.structure = currentBrowserSystem.getStructure();
                currentCecData.phase     = currentBrowserSystem.getPhase();
                currentCecData.model     = currentBrowserSystem.getModel();
                cecService.saveCEC(currentCecData);        // ← no raw path construction
                statusLabel.setText("Saved successfully");
                statusLabel.setStyle("-fx-text-fill: #008000;");
                editMode = false; editButton.setText("Edit Row");
                aCol.setEditable(false); cecTable.setEditable(false);
            } catch (Exception ex) {
                statusLabel.setText("Error saving: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #ff0000;");
                LOG.warning("CECManagementPanel.saveButton — " + ex.getMessage());
            }
        });

        if (!systemCombo.getItems().isEmpty())
            loadCECData(systemCombo.getItems().get(0), cecTable, statusLabel);

        tab.getChildren().addAll(title,
            new HBox(10, new Label("Select System:"), systemCombo),
            new Separator(), new Label("CEC Table:"),
            cecTable, statusLabel, btnBox);
        return tab;
    }

    // ==================== TAB 2: CEC ASSEMBLY ====================

    private ScrollPane createAssemblyTab() {
        VBox tab = new VBox(10);
        tab.setPadding(new Insets(15));

        Label title = new Label("CEC Assembly");
        title.setFont(Font.font("System", FontWeight.BOLD, 13));

        Label instruction = new Label(
            "Assemble higher-order (ternary+) CECs from binary subsystem CECs + pure-order terms.");
        instruction.setStyle("-fx-font-size: 10; -fx-text-fill: #666666;");

        // ---- Step 1: target system ----
        VBox step1Box = borderedStep("STEP 1: Select Target System");
        ComboBox<SystemIdentity> targetCombo = new ComboBox<>();
        targetCombo.setPrefWidth(350);
        targetCombo.setPromptText("Choose a target system (K ≥ 2)...");
        targetCombo.getItems().addAll(
            registry.getAllSystems().stream().filter(s -> s.getNumComponents() >= 2).toList());
        applySystemCellFactory(targetCombo);
        step1Box.getChildren().add(new HBox(10, new Label("Target System:"), targetCombo));

        // ---- Step 2: subsystem validation ----
        VBox step2Box = borderedStep("STEP 2: Verify Required Subsystems");
        VBox subsystemsContent = new VBox(8);
        subsystemsContent.setPadding(new Insets(5));
        ScrollPane subsystemsScroll = new ScrollPane(subsystemsContent);
        subsystemsScroll.setPrefHeight(150);
        step2Box.getChildren().addAll(
            new Label("Shows all subsystems required to assemble the target system:"),
            subsystemsScroll);

        assemblyStatusLabel = new Label("Select a target system to begin");
        assemblyStatusLabel.setStyle("-fx-text-fill: #666666;");

        // ---- Step 3: results (initially hidden) ----
        step3Box = borderedStep("STEP 3: Assembly Results");
        step3Box.setVisible(false);

        derivedECIsTable = buildECITable(false);
        pureKECIsTable   = buildECITable(true);

        step3Box.getChildren().addAll(
            new Label("3a. Derived ECIs (from subsystem transformations) — READ ONLY"),
            derivedECIsTable,
            new Label("3b. Pure Order-K ECIs — ENTER VALUES MANUALLY"),
            pureKECIsTable);

        // ---- Buttons ----
        Button assembleButton = new Button("ASSEMBLE");
        assemblyActionSaveButton = new Button("SAVE Assembled CECs");
        assembleButton.setStyle("-fx-font-size: 10; -fx-padding: 8 15;");
        assemblyActionSaveButton.setStyle("-fx-font-size: 10; -fx-padding: 8 15;");
        assemblyActionSaveButton.setDisable(true);
        assembleButton.setDisable(true);

        // Target selection → refresh subsystem checklist
        targetCombo.setOnAction(e -> {
            SystemIdentity target = targetCombo.getValue();
            if (target == null) return;

            subsystemsContent.getChildren().clear();
            int K = target.getNumComponents();
            Map<Integer, List<List<String>>> byOrder = CECAssemblyService.subsystemsByOrder(target.getComponents());

            int total = 0, found = 0;
            boolean allExist = true;

            for (int order = 2; order < K; order++) {
                List<List<String>> subs = byOrder.getOrDefault(order, List.of());
                if (subs.isEmpty()) continue;

                VBox section = new VBox(4);
                section.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 0 0 1 0; -fx-padding: 5;");
                Label orderLbl = new Label("Order " + order + " Subsystems:");
                orderLbl.setFont(Font.font("System", FontWeight.BOLD, 10));
                section.getChildren().add(orderLbl);

                for (List<String> sub : subs) {
                    String key = CECAssemblyService.toElementString(sub);
                    boolean exists = cecService.isCECAvailable(key,
                            target.getStructure(), target.getPhase(), target.getModel());
                    total++; if (exists) found++; else allExist = false;

                    Label icon = new Label(exists ? "✓" : "✗");
                    icon.setStyle(exists
                            ? "-fx-text-fill: #008000; -fx-font-weight: bold;"
                            : "-fx-text-fill: #ff0000; -fx-font-weight: bold;");
                    section.getChildren().add(new HBox(10, icon, new Label(key)));
                }
                subsystemsContent.getChildren().add(section);
            }

            if (allExist) {
                assemblyStatusLabel.setText("✓ All " + found + " subsystems found. Ready to assemble!");
                assemblyStatusLabel.setStyle("-fx-text-fill: #008000;");
                assembleButton.setDisable(false);
            } else {
                assemblyStatusLabel.setText("✗ Missing " + (total - found) + "/" + total + " subsystems.");
                assemblyStatusLabel.setStyle("-fx-text-fill: #ff0000;");
                assembleButton.setDisable(true);
            }
        });

        // Assemble → delegate to coordinator (runs on background thread)
        assembleButton.setOnAction(e -> {
            SystemIdentity target = targetCombo.getValue();
            if (target == null) return;
            assemblyStatusLabel.setText("Assembling... please wait");
            assemblyStatusLabel.setStyle("-fx-text-fill: #ff9900;");
            assembleButton.setDisable(true);
            coordinator.startAssembly(target);   // ← no JavaFX thread work
        });

        // Save → delegate to coordinator
        assemblyActionSaveButton.setOnAction(e -> {
            if (lastAssemblyResult == null) {
                assemblyStatusLabel.setText("Please assemble first");
                assemblyStatusLabel.setStyle("-fx-text-fill: #ff0000;"); return;
            }
            try {
                double[] pureK = pureKECIsTable.getItems().stream()
                        .mapToDouble(r -> r.value).toArray();
                coordinator.saveAssembledCEC(lastAssemblyResult, pureK);
                assemblyStatusLabel.setText("✓ Saved assembled CECs for "
                        + lastAssemblyResult.targetSystem().getId());
                assemblyStatusLabel.setStyle("-fx-text-fill: #008000;");
            } catch (Exception ex) {
                assemblyStatusLabel.setText("Save error: " + ex.getMessage());
                assemblyStatusLabel.setStyle("-fx-text-fill: #ff0000;");
                LOG.warning("CECManagementPanel.save — " + ex.getMessage());
            }
        });

        tab.getChildren().addAll(title, instruction, new Separator(),
            step1Box, step2Box, step3Box, new Separator(),
            new HBox(10, assembleButton, assemblyActionSaveButton),
            assemblyStatusLabel);

        ScrollPane scroll = new ScrollPane(tab);
        scroll.setFitToWidth(true);
        return scroll;
    }

    // -----------------------------------------------------------------------
    // Assembly callbacks (called on FX thread by coordinator)
    // -----------------------------------------------------------------------

    private void onAssemblyCompleted(AssemblyResult result) {
        lastAssemblyResult = result;
        int K = result.targetSystem().getNumComponents();

        derivedECIsTable.getItems().clear();
        pureKECIsTable.getItems().clear();

        int[] cfOrderMap  = result.cfOrderMap();
        double[] derived  = result.derivedECIs();

        for (int i = 0; i < cfOrderMap.length; i++) {
            if (cfOrderMap[i] < K)  derivedECIsTable.getItems().add(new ECITableRow(i, derived[i]));
            if (cfOrderMap[i] == K) pureKECIsTable.getItems().add(new ECITableRow(i, 0.0));
        }

        step3Box.setVisible(true);
        assemblyActionSaveButton.setDisable(false);
        assemblyStatusLabel.setText("✓ Assembly complete! Enter pure-K ECI values and click Save.");
        assemblyStatusLabel.setStyle("-fx-text-fill: #008000;");
        LOG.fine("CECManagementPanel.onAssemblyCompleted — system=" + result.targetSystem().getId());
    }

    private void onAssemblyFailed(String errorMessage) {
        assemblyStatusLabel.setText("Assembly error: " + errorMessage);
        assemblyStatusLabel.setStyle("-fx-text-fill: #ff0000;");
        LOG.warning("CECManagementPanel.onAssemblyFailed — " + errorMessage);
    }

    // -----------------------------------------------------------------------
    // Browser helpers
    // -----------------------------------------------------------------------

    private void loadCECData(SystemIdentity system, TableView<CecTermRow> table, Label statusLabel) {
        String elements = String.join("-", system.getComponents());
        Optional<SystemDataLoader.CECData> opt =
                cecService.loadCEC(elements, system.getStructure(), system.getPhase(), system.getModel());

        table.getItems().clear();
        editMode = false;

        if (opt.isPresent()) {
            SystemDataLoader.CECData data = opt.get();
            currentCecData       = data;
            currentBrowserSystem = system;
            int count = 0;
            if (data.cecTerms != null) {
                for (int i = 0; i < data.cecTerms.length; i++)
                    table.getItems().add(new CecTermRow(i, data.cecTerms[i].name, data.cecTerms[i].a));
                count = data.cecTerms.length;
            } else if (data.cecValues != null) {
                for (int i = 0; i < data.cecValues.length; i++)
                    table.getItems().add(new CecTermRow(i, "ECI_" + i, data.cecValues[i]));
                count = data.cecValues.length;
            }
            statusLabel.setText("Loaded: " + count + " terms, Units: " + data.cecUnits);
            statusLabel.setStyle("-fx-text-fill: #008000;");
        } else {
            statusLabel.setText("CEC not found for: " + system.getId());
            statusLabel.setStyle("-fx-text-fill: #ff0000;");
        }
    }

    // -----------------------------------------------------------------------
    // UI factory helpers
    // -----------------------------------------------------------------------

    private static VBox borderedStep(String headerText) {
        VBox box = new VBox(6);
        box.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-padding: 10;");
        Label lbl = new Label(headerText);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 11));
        box.getChildren().add(lbl);
        return box;
    }

    private static TableView<ECITableRow> buildECITable(boolean editable) {
        TableView<ECITableRow> t = new TableView<>();
        t.setPrefHeight(150);
        t.setEditable(editable);

        TableColumn<ECITableRow, Integer> idxCol = new TableColumn<>("CF Index");
        idxCol.setCellValueFactory(cf ->
            new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().cfIndex));
        idxCol.setPrefWidth(80);

        TableColumn<ECITableRow, Double> valCol = new TableColumn<>("ECI Value (J/mol)");
        valCol.setCellValueFactory(cf ->
            new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().value));
        valCol.setPrefWidth(200);
        if (editable) {
            valCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
            valCol.setOnEditCommit(ev -> {
                if (ev.getRowValue() != null) {
                    ev.getRowValue().value = ev.getNewValue();
                    t.refresh();
                }
            });
        }
        t.getColumns().addAll(idxCol, valCol);
        return t;
    }

    private static void applySystemCellFactory(ComboBox<SystemIdentity> combo) {
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(SystemIdentity it, boolean empty) {
                super.updateItem(it, empty);
                setText(empty || it == null ? null : it.getName() + " (K=" + it.getNumComponents() + ")");
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(SystemIdentity it, boolean empty) {
                super.updateItem(it, empty);
                setText(empty || it == null ? null : it.getName());
            }
        });
    }

    // -----------------------------------------------------------------------
    // DTOs
    // -----------------------------------------------------------------------

    private static class CecTermRow {
        int index; String name; double value;
        CecTermRow(int i, String n, double v) { index = i; name = n; value = v; }
    }

    private static class ECITableRow {
        int cfIndex; double value;
        ECITableRow(int i, double v) { cfIndex = i; value = v; }
    }
}
