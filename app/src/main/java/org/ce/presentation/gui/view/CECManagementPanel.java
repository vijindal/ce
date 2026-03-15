package org.ce.presentation.gui.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.ce.application.service.CECAssemblyService;
import org.ce.application.service.CECManagementService;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.logging.LoggingConfig;
import org.ce.infrastructure.registry.KeyUtils;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.infrastructure.service.CECManagementCoordinator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Unified CEC Editor panel — combines CEC browsing/editing and lower-order subsystem
 * prefill (formerly the separate "CEC Assembly" tab) into a single view.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>The ECI table is <em>always editable</em> — no mode toggle required.</li>
 *   <li>For systems with K ≥ 2 components a <em>Subsystem Prefill</em> section
 *       appears below the table.  Each lower-order subsystem row has an
 *       <strong>Apply</strong> button that transforms that subsystem's ECIs into
 *       the target basis and merges the contribution via a subtract-old / add-new
 *       delta so re-applying after a source CEC change is safe and manual edits
 *       the user has typed elsewhere are never clobbered.</li>
 *   <li>A <strong>Clear</strong> button per subsystem removes its tracked
 *       contribution from the table.</li>
 *   <li><strong>Apply All Available</strong> fires every available subsystem's
 *       Apply action in sequence.</li>
 *   <li>All I/O goes through {@link CECManagementService}; subsystem
 *       transformations are delegated to {@link CECManagementCoordinator#applySubsystem}
 *       which runs on a background thread.</li>
 * </ul>
 */
public class CECManagementPanel extends VBox {

    private static final Logger LOG = LoggingConfig.getLogger(CECManagementPanel.class);

    private final SystemRegistry           registry;
    private final CECManagementService     cecService;
    private final CECManagementCoordinator coordinator;

    // ---- Mutable panel state ----
    private ComboBox<SystemIdentity>       systemCombo;
    private TableView<CecTermRow>          cecTable;
    private VBox                           prefillContainer;  // swapped on system change
    private Label                          statusLabel;

    private SystemIdentity                 currentSystem;
    private SystemDataLoader.CECData       currentCecData;

    /**
     * Tracks the contribution each subsystem has added so that re-applying a subsystem
     * only changes the delta (subtract old, add new) rather than accumulating.
     * Key = element string, e.g. "Nb-Ti".
     */
    private final Map<String, double[]>    subsystemContributions = new LinkedHashMap<>();

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

        getChildren().add(createContent());
    }

    // -----------------------------------------------------------------------
    // Content
    // -----------------------------------------------------------------------

    private VBox createContent() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label title = new Label("CEC Editor");
        title.setFont(Font.font("System", FontWeight.BOLD, 13));

        // ---- System selector ----
        systemCombo = new ComboBox<>();
        systemCombo.setPrefWidth(300);
        systemCombo.setPromptText("Choose a system…");
        systemCombo.getItems().addAll(registry.getAllSystems());
        applySystemCellFactory(systemCombo);

        // ---- Status label ----
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #008000;");

        // ---- ECI table — always editable ----
        cecTable = buildECITable();

        // ---- Prefill section (rebuilt on system change) ----
        prefillContainer = new VBox();

        // ---- Reference / notes metadata ----
        TextField referenceField = new TextField();
        referenceField.setPromptText("Reference / source");
        referenceField.setPrefWidth(400);
        TextField notesField = new TextField();
        notesField.setPromptText("Notes");
        notesField.setPrefWidth(400);
        VBox metaBox = new VBox(4,
            new HBox(8, new Label("Reference:"), referenceField),
            new HBox(8, new Label("Notes:     "), notesField));

        // ---- Buttons ----
        Button newCecButton = new Button("New CEC");
        Button saveButton   = new Button("Save");
        newCecButton.setStyle("-fx-font-size: 10; -fx-padding: 6 12;");
        saveButton.setStyle  ("-fx-font-size: 10; -fx-padding: 6 12;");

        newCecButton.setVisible(false);
        newCecButton.setManaged(false);

        HBox btnBox = new HBox(8, newCecButton, saveButton);

        // ---- Wire system selector ----
        systemCombo.setOnAction(e -> {
            SystemIdentity s = systemCombo.getValue();
            if (s == null) return;
            loadForSystem(s, newCecButton, referenceField, notesField);
        });

        // ---- New CEC ----
        newCecButton.setOnAction(e -> {
            SystemIdentity s = systemCombo.getValue();
            if (s == null) return;
            statusLabel.setText("Building CEC template from cluster data…");
            statusLabel.setStyle("-fx-text-fill: #666666;");
            Optional<SystemDataLoader.CECData> opt = cecService.scaffoldEmptyCEC(s);
            if (opt.isEmpty()) {
                statusLabel.setText("Cannot scaffold: cluster data not found for "
                        + KeyUtils.clusterKey(s) + ". Run identification first.");
                statusLabel.setStyle("-fx-text-fill: #cc6600;");
                return;
            }
            currentCecData = opt.get();
            currentSystem  = s;
            subsystemContributions.clear();
            populateTable(cecTable, currentCecData);
            rebuildPrefillSection(s);
            newCecButton.setVisible(false);
            newCecButton.setManaged(false);
            int ncf = currentCecData.cecTerms != null ? currentCecData.cecTerms.length : 0;
            statusLabel.setText("New CEC: " + ncf + " terms ready — enter values and Save.");
            statusLabel.setStyle("-fx-text-fill: #005599;");
        });

        // ---- Save ----
        saveButton.setOnAction(e -> {
            if (currentCecData == null || currentSystem == null) {
                statusLabel.setText("No CEC data to save");
                statusLabel.setStyle("-fx-text-fill: #ff0000;"); return;
            }
            try {
                // Commit any pending cell edits
                cecTable.edit(-1, null);
                currentCecData.structurePhase = currentSystem.getStructurePhase();
                currentCecData.model          = currentSystem.getModel();
                currentCecData.reference      = referenceField.getText();
                currentCecData.notes          = notesField.getText();
                cecService.saveCEC(currentCecData);
                String path = "~/.ce-workbench/data/systems/"
                        + currentSystem.getStructurePhase() + "_"
                        + currentCecData.elements + "_"
                        + currentSystem.getModel() + "/cec.json";
                statusLabel.setText("✓ Saved — " + path);
                statusLabel.setStyle("-fx-text-fill: #008000;");
            } catch (Exception ex) {
                statusLabel.setText("Save error: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #ff0000;");
                LOG.warning("CECManagementPanel.save — " + ex.getMessage());
            }
        });

        // ---- Initial load ----
        if (!systemCombo.getItems().isEmpty()) {
            systemCombo.getSelectionModel().selectFirst();
            loadForSystem(systemCombo.getValue(), newCecButton, referenceField, notesField);
        }

        root.getChildren().addAll(
            title,
            new HBox(10, new Label("System:"), systemCombo),
            statusLabel,
            new Separator(),
            cecTable,
            prefillContainer,
            new Separator(),
            metaBox,
            btnBox
        );
        return root;
    }

    // -----------------------------------------------------------------------
    // System load
    // -----------------------------------------------------------------------

    private void loadForSystem(SystemIdentity system,
                               Button newCecButton,
                               TextField referenceField,
                               TextField notesField) {
        String elements = String.join("-", system.getComponents());
        Optional<SystemDataLoader.CECData> opt =
            cecService.loadCEC(elements, system.getStructurePhase(), "", system.getModel());

        subsystemContributions.clear();
        cecTable.getItems().clear();

        if (opt.isPresent()) {
            currentCecData = opt.get();
            currentSystem  = system;
            populateTable(cecTable, currentCecData);
            referenceField.setText(currentCecData.reference != null ? currentCecData.reference : "");
            notesField.setText    (currentCecData.notes     != null ? currentCecData.notes     : "");

            int count = currentCecData.cecTerms  != null ? currentCecData.cecTerms.length
                      : currentCecData.cecValues != null ? currentCecData.cecValues.length : 0;
            String units = currentCecData.cecUnits != null ? currentCecData.cecUnits : "?";
            statusLabel.setText("Loaded: " + count + " terms  |  Units: " + units);
            statusLabel.setStyle("-fx-text-fill: #008000;");

            newCecButton.setVisible(false);
            newCecButton.setManaged(false);
        } else {
            currentCecData = null;
            currentSystem  = system;
            referenceField.clear();
            notesField.clear();
            statusLabel.setText("No CEC found for " + system.getId()
                    + " — click New CEC to create one.");
            statusLabel.setStyle("-fx-text-fill: #cc6600;");
            newCecButton.setVisible(true);
            newCecButton.setManaged(true);
        }

        rebuildPrefillSection(system);
    }

    // -----------------------------------------------------------------------
    // Subsystem prefill section
    // -----------------------------------------------------------------------

    /**
     * Replaces the content of {@link #prefillContainer} with a freshly built
     * subsystem prefill section for the given system.  Hidden entirely for binary
     * systems (no lower-order subsystems to contribute).
     */
    private void rebuildPrefillSection(SystemIdentity system) {
        prefillContainer.getChildren().clear();

        Map<Integer, List<List<String>>> byOrder =
                CECAssemblyService.subsystemsByOrder(system.getComponents());
        if (byOrder.isEmpty()) return;  // binary — nothing to prefill

        VBox section = new VBox(8);
        section.setStyle("-fx-border-color: #dde8f0; -fx-border-width: 1;"
                       + "-fx-padding: 10; -fx-background-color: #f8fbfd;");

        Label sectionTitle = new Label("Prefill from lower-order subsystems:");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 11));
        section.getChildren().add(sectionTitle);

        List<Runnable> allApplyActions = new ArrayList<>();

        for (int order : byOrder.keySet()) {
            for (List<String> comps : byOrder.get(order)) {
                String elemKey  = CECAssemblyService.toElementString(comps);
                boolean available = cecService.isCECAvailable(
                        elemKey, system.getStructurePhase(), "", system.getModel());

                Label icon = new Label(available ? "✓" : "✗");
                icon.setStyle(available
                        ? "-fx-text-fill: #008000; -fx-font-weight: bold;"
                        : "-fx-text-fill: #999999; -fx-font-weight: bold;");

                Label nameLabel = new Label(elemKey);

                Button applyBtn = new Button("Apply");
                Button clearBtn = new Button("Clear");
                applyBtn.setStyle("-fx-font-size: 10; -fx-padding: 4 10;");
                clearBtn.setStyle("-fx-font-size: 10; -fx-padding: 4 10;");
                applyBtn.setDisable(!available || currentCecData == null);
                clearBtn.setDisable(true);

                List<String> compsCopy = List.copyOf(comps);

                applyBtn.setOnAction(e -> {
                    if (currentCecData == null) {
                        statusLabel.setText("Create or load a CEC first before applying subsystems.");
                        statusLabel.setStyle("-fx-text-fill: #ff9900;");
                        return;
                    }
                    applyBtn.setDisable(true);
                    statusLabel.setText("Applying " + elemKey + "…");
                    statusLabel.setStyle("-fx-text-fill: #ff9900;");
                    coordinator.applySubsystem(currentSystem, compsCopy,
                        sc -> {
                            onSubsystemApplied(sc);
                            applyBtn.setDisable(false);
                            clearBtn.setDisable(false);
                        },
                        err -> {
                            onSubsystemFailed(elemKey, err);
                            applyBtn.setDisable(false);
                        });
                });

                clearBtn.setOnAction(e -> {
                    clearSubsystem(elemKey);
                    clearBtn.setDisable(true);
                });

                if (available) allApplyActions.add(() -> applyBtn.fire());

                section.getChildren().add(new HBox(8, icon, nameLabel, applyBtn, clearBtn));
            }
        }

        Button applyAllBtn = new Button("Apply All Available");
        applyAllBtn.setStyle("-fx-font-size: 10; -fx-padding: 6 14;");
        applyAllBtn.setDisable(allApplyActions.isEmpty() || currentCecData == null);
        applyAllBtn.setOnAction(e -> allApplyActions.forEach(Runnable::run));
        section.getChildren().add(applyAllBtn);

        prefillContainer.getChildren().add(section);
    }

    // -----------------------------------------------------------------------
    // Subsystem callbacks
    // -----------------------------------------------------------------------

    /**
     * Delta-merge callback: subtract the previous contribution of this subsystem
     * from {@link #currentCecData}, add the new contribution, then refresh the table.
     * The user's direct edits to other CFs are preserved because only the delta is applied.
     */
    private void onSubsystemApplied(CECManagementCoordinator.SubsystemContribution sc) {
        if (currentCecData == null) return;

        String   key       = sc.elements();
        double[] newContrib = sc.contribution();
        int      len       = newContrib.length;
        double[] oldContrib = subsystemContributions.getOrDefault(key, new double[len]);

        if (currentCecData.cecTerms != null) {
            for (int i = 0; i < currentCecData.cecTerms.length && i < len; i++)
                currentCecData.cecTerms[i].a += newContrib[i] - oldContrib[i];
        } else if (currentCecData.cecValues != null) {
            for (int i = 0; i < currentCecData.cecValues.length && i < len; i++)
                currentCecData.cecValues[i] += newContrib[i] - oldContrib[i];
        }

        subsystemContributions.put(key, newContrib);
        populateTable(cecTable, currentCecData);

        statusLabel.setText("✓ Applied " + key);
        statusLabel.setStyle("-fx-text-fill: #008000;");
        LOG.fine("CECManagementPanel.onSubsystemApplied — " + key);
    }

    private void onSubsystemFailed(String elements, String error) {
        statusLabel.setText("Failed to apply " + elements + ": " + error);
        statusLabel.setStyle("-fx-text-fill: #ff0000;");
        LOG.warning("CECManagementPanel.onSubsystemFailed — " + elements + ": " + error);
    }

    /**
     * Subtracts the tracked contribution for {@code elements} from the current ECI data
     * and removes it from {@link #subsystemContributions}.
     */
    private void clearSubsystem(String elements) {
        double[] oldContrib = subsystemContributions.remove(elements);
        if (oldContrib == null || currentCecData == null) return;

        if (currentCecData.cecTerms != null) {
            for (int i = 0; i < currentCecData.cecTerms.length && i < oldContrib.length; i++)
                currentCecData.cecTerms[i].a -= oldContrib[i];
        } else if (currentCecData.cecValues != null) {
            for (int i = 0; i < currentCecData.cecValues.length && i < oldContrib.length; i++)
                currentCecData.cecValues[i] -= oldContrib[i];
        }

        populateTable(cecTable, currentCecData);
        statusLabel.setText("Cleared " + elements + " contribution");
        statusLabel.setStyle("-fx-text-fill: #666666;");
    }

    // -----------------------------------------------------------------------
    // Table helpers
    // -----------------------------------------------------------------------

    /** Builds the always-editable ECI table. */
    private static TableView<CecTermRow> buildECITable() {
        TableView<CecTermRow> table = new TableView<>();
        table.setPrefHeight(360);
        table.setEditable(true);

        TableColumn<CecTermRow, Integer> indexCol = new TableColumn<>("Index");
        indexCol.setCellValueFactory(cf ->
            new javafx.beans.property.SimpleObjectProperty<>(cf.getValue().index));
        indexCol.setPrefWidth(55);

        TableColumn<CecTermRow, String> nameCol = new TableColumn<>("Cluster function");
        nameCol.setCellValueFactory(cf ->
            new javafx.beans.property.SimpleStringProperty(cf.getValue().name));
        nameCol.setPrefWidth(230);

        TableColumn<CecTermRow, Double> aCol = new TableColumn<>("ECI value (J/mol)");
        aCol.setCellValueFactory(cf -> cf.getValue().value.asObject());
        aCol.setPrefWidth(160);
        aCol.setEditable(true);
        aCol.setCellFactory(col -> new TableCell<CecTermRow, Double>() {
            private final TextField tf = new TextField();
            {
                tf.setOnAction(e -> commitEdit(parseDouble(tf.getText())));
                tf.focusedProperty().addListener((obs, was, now) -> {
                    if (!now) commitEdit(parseDouble(tf.getText()));
                });
            }
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null); setGraphic(null);
                } else {
                    tf.setText(value == null ? "0.0" : value.toString());
                    setGraphic(tf); setText(null);
                }
            }
            @Override
            public void startEdit() {
                super.startEdit();
                tf.setText(getItem() == null ? "0.0" : getItem().toString());
                setGraphic(tf); setText(null);
                tf.requestFocus(); tf.selectAll();
            }
            @Override
            public void commitEdit(Double newValue) {
                super.commitEdit(newValue);
                CecTermRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row != null) row.value.set(newValue != null ? newValue : 0.0);
            }
            private Double parseDouble(String s) {
                try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; }
            }
        });
        aCol.setOnEditCommit(ev -> {
            CecTermRow row = ev.getRowValue();
            if (row != null) row.value.set(ev.getNewValue());
        });

        table.getColumns().addAll(indexCol, nameCol, aCol);
        return table;
    }

    /** Fills the table from either cecTerms (preferred) or legacy cecValues. */
    private static void populateTable(TableView<CecTermRow> table,
                                      SystemDataLoader.CECData data) {
        table.getItems().clear();
        if (data.cecTerms != null) {
            for (int i = 0; i < data.cecTerms.length; i++) {
                CecTermRow row = new CecTermRow(i, data.cecTerms[i].name, data.cecTerms[i].a);
                int idx = i;
                row.value.addListener((obs, oldVal, newVal) ->
                    data.cecTerms[idx].a = newVal.doubleValue());
                table.getItems().add(row);
            }
        } else if (data.cecValues != null) {
            for (int i = 0; i < data.cecValues.length; i++) {
                CecTermRow row = new CecTermRow(i, "ECI_" + i, data.cecValues[i]);
                int idx = i;
                row.value.addListener((obs, oldVal, newVal) ->
                    data.cecValues[idx] = newVal.doubleValue());
                table.getItems().add(row);
            }
        }
    }

    private static void applySystemCellFactory(ComboBox<SystemIdentity> combo) {
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(SystemIdentity it, boolean empty) {
                super.updateItem(it, empty);
                setText(empty || it == null ? null
                        : it.getName() + " (K=" + it.getNumComponents() + ")");
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
    // DTO
    // -----------------------------------------------------------------------

    private static class CecTermRow {
        final int index;
        final String name;
        final javafx.beans.property.SimpleDoubleProperty value;
        CecTermRow(int i, String n, double v) {
            this.index = i; this.name = n;
            this.value = new javafx.beans.property.SimpleDoubleProperty(v);
        }
    }

    // -----------------------------------------------------------------------
    // Application-level integration hook
    // -----------------------------------------------------------------------

    /**
     * Pre-selects a system in the editor and loads its data.
     * Called when the user clicks "Open in CEC Database" in the Data Preparation panel.
     */
    public void selectSystem(SystemIdentity system) {
        if (systemCombo == null) return;
        systemCombo.getItems().setAll(registry.getAllSystems());
        systemCombo.getItems().stream()
            .filter(s -> s.getId().equals(system.getId()))
            .findFirst()
            .ifPresent(s -> {
                systemCombo.setValue(s);
                systemCombo.fireEvent(new javafx.event.ActionEvent());
            });
    }
}
