package org.ce.presentation.gui.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.ce.application.job.BackgroundJob;
import org.ce.domain.identification.geometry.Vector3D;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.mcs.StructureModelMapping;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.ce.infrastructure.registry.KeyUtils;
import org.ce.infrastructure.service.BackgroundJobManager;
import org.ce.infrastructure.service.IdentificationCoordinator;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.presentation.gui.component.IdentificationInputDialog;

import java.util.Collection;
import java.util.Optional;

/**
 * Left panel for system setup and background job monitoring.
 *
 * <p><b>Refactored (T1.5 / T1.6):</b> all orchestration (key resolution, cache checks,
 * job construction, job submission, completion handling) has moved to
 * {@link IdentificationCoordinator}.  The identification dialog has been extracted to
 * {@link IdentificationInputDialog}.  The raw polling thread has been eliminated;
 * completion arrives event-driven via the coordinator's JobManagerListener.</p>
 *
 * <p>This class now holds only form fields, button handlers that validate input and
 * delegate, and a job-queue progress display.</p>
 */
public class SystemRegistryPanel extends VBox {

    private final SystemRegistry registry;
    private final BackgroundJobManager jobManager;
    private final ResultsPanel resultsPanel;
    private final IdentificationCoordinator coordinator;

    private CalculationSetupPanel calcSetupPanel;

    public SystemRegistryPanel(SystemRegistry registry,
                               BackgroundJobManager jobManager,
                               ResultsPanel resultsPanel,
                               IdentificationCoordinator coordinator) {
        this.registry     = registry;
        this.jobManager   = jobManager;
        this.resultsPanel = resultsPanel;
        this.coordinator  = coordinator;

        setSpacing(6);
        setPadding(new Insets(8));
        setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 0 1 0 0;");

        coordinator.setLogger(this::logResult);
        coordinator.setOnJobCompleted((job, clusterKey) -> {
            ClusterDataPresenter.present(job, clusterKey, this::logResult);
            updateJobProgress();
        });
        coordinator.setOnJobFailed((jobId, err) -> {
            logResult("✗ Job failed (" + jobId + "): " + err);
            updateJobProgress();
        });

        getChildren().add(createSystemSection());
        setupJobMonitoring();
    }

    private VBox createSystemSection() {
        VBox vbox = new VBox(10);

        Label titleLabel = new Label("System Setup");
        titleLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");

        ScrollPane formScroll = new ScrollPane();
        formScroll.setFitToWidth(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-padding: 10;");

        TextField elementsField = new TextField("A-B");
        Label elementsHelp = new Label("Format: Element1-Element2 (e.g., Ti-Nb, Fe-Ni-Cr)");
        elementsHelp.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");

        TextField structurePhaseField = new TextField("BCC_A2");
        Label structureHelp = new Label("Format: Structure_Phase (e.g., BCC_A2, FCC_L12)");
        structureHelp.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");

        TextField modelField = new TextField("T");
        Label modelHelp = new Label("CVM approximation (e.g., T=tetrahedron, P=pair)");
        modelHelp.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");

        int row = 0;
        grid.add(new Label("Elements:"),        0, row); grid.add(elementsField,       1, row++);
        grid.add(elementsHelp,                  1, row++);
        grid.add(new Label("Structure/Phase:"), 0, row); grid.add(structurePhaseField, 1, row++);
        grid.add(structureHelp,                 1, row++);
        grid.add(new Label("Model:"),           0, row); grid.add(modelField,          1, row++);
        grid.add(modelHelp,                     1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;

        Button createButton        = new Button("Create System");
        Button createClusterButton = new Button("Create Cluster");
        Button clearButton         = new Button("Clear");
        createButton.setStyle("-fx-font-size: 11; -fx-padding: 5 15;");
        createClusterButton.setStyle("-fx-font-size: 11; -fx-padding: 5 15; -fx-text-fill: #0066cc;");
        clearButton.setStyle("-fx-font-size: 11; -fx-padding: 5 15;");

        HBox formButtons = new HBox(10);
        formButtons.getChildren().addAll(createClusterButton, createButton, clearButton);
        grid.add(new Separator(), 0, row, 2, 1); row++;
        grid.add(formButtons,     0, row, 2, 1);

        formScroll.setContent(grid);
        calcSetupPanel = new CalculationSetupPanel(registry, jobManager, resultsPanel);
        vbox.getChildren().addAll(titleLabel, formScroll, new Separator(), calcSetupPanel);
        VBox.setVgrow(formScroll,     Priority.SOMETIMES);
        VBox.setVgrow(calcSetupPanel, Priority.ALWAYS);

        // ------------------------------------------------------------------ Create System
        createButton.setOnAction(e -> {
            String elements       = elementsField.getText().trim();
            String structurePhase = structurePhaseField.getText().trim();
            String model          = modelField.getText().trim();
            if (!validateFields(elements, structurePhase, model)) return;

            String[] parts          = structurePhase.split("_");
            String   structure      = parts[0], phase = parts[1];
            String[] componentArray = elements.split("-");
            int      K              = componentArray.length;

            String systemId   = SystemIdentity.generateSystemId(elements, structure, phase, model);
            String cecKey     = KeyUtils.cecKey(elements, structure, phase, model);
            String clusterKey = KeyUtils.clusterKey(structure, phase, model, K);

            logResult("\n[System Creation] ----------------------------------------");
            logResult("  Elements: " + elements + " (" + K + " components)");
            logResult("  systemId: " + systemId + "  clusterKey: " + clusterKey);

            String resolvedSym, resolvedCluster;
            try {
                resolvedSym     = StructureModelMapping.resolveSymmetryGroup(structurePhase);
                resolvedCluster = StructureModelMapping.resolveClusterFile(structurePhase, model);
            } catch (IllegalArgumentException ex) { showAlert("Mapping Error", ex.getMessage()); return; }

            boolean cecAvailable = SystemDataLoader.cecExists(elements, structure, phase, model);
            if (!cecAvailable)
                showAlert("CEC Data Missing", "No CEC found for '" + cecKey + "'.\nYou can still create the system.");

            boolean cacheHit = AllClusterDataCache.exists(clusterKey);
            logResult("  Cluster cache: " + (cacheHit ? "✓ hit" : "⚠ miss — pipeline needed"));

            String name = elements + " " + structure + " " + phase + " (" + model + ")";
            SystemIdentity system = buildSystem(systemId, name, structure, phase, model,
                    componentArray, resolvedCluster, resolvedSym);

            if (!cacheHit) {
                if (!confirmAction("Cluster Data Missing",
                        "No cluster data for '" + clusterKey + "'.\nGenerate now?")) {
                    logResult("⚠ Skipped."); return;
                }
                Optional<IdentificationInputDialog.Input> inp =
                        IdentificationInputDialog.show(system, "CF Identification");
                if (inp.isEmpty()) { logResult("⚠ Cancelled."); return; }

                calcSetupPanel.setSelectedSystem(system);
                coordinator.createSystem(system, cecAvailable, clusterKey, false,
                        inp.get().disorderedClusterFile(), inp.get().orderedClusterFile(),
                        inp.get().disorderedSymmetryGroup(), inp.get().orderedSymmetryGroup(),
                        defaultMatrix(), defaultVec());
            } else {
                coordinator.createSystem(system, cecAvailable, clusterKey, true,
                        resolvedCluster, resolvedCluster, resolvedSym, resolvedSym,
                        defaultMatrix(), defaultVec());
                calcSetupPanel.setSelectedSystem(system);
                logResult("✓ System created: " + name);
            }
            resetForm(elementsField, structurePhaseField, modelField);
        });

        // ------------------------------------------------------------------ Create Cluster
        createClusterButton.setOnAction(e -> {
            String elements       = elementsField.getText().trim();
            String structurePhase = structurePhaseField.getText().trim();
            String model          = modelField.getText().trim();
            if (!validateFields(elements, structurePhase, model)) return;

            String[] componentArray = elements.split("-");
            int K = componentArray.length;
            if (K < 2 || K > 5) { showAlert("Invalid Components", "Components must be 2–5."); return; }

            String[] parts      = structurePhase.split("_");
            String   structure  = parts[0], phase = parts[1];
            String   clusterKey = KeyUtils.clusterKey(structure, phase, model, K);

            logResult("\n[Cluster Creation] ----------------------------------------");
            logResult("  Cluster Key: " + clusterKey);

            if (AllClusterDataCache.exists(clusterKey) &&
                    !confirmAction("Data Exists", "Overwrite existing data for '" + clusterKey + "'?")) {
                logResult("  ⚠ Cancelled."); return;
            }

            String resolvedCluster = null, resolvedSym = null;
            try {
                resolvedCluster = StructureModelMapping.resolveClusterFile(structurePhase, model);
                resolvedSym     = StructureModelMapping.resolveSymmetryGroup(structurePhase);
            } catch (IllegalArgumentException ignored) {}

            String tempId   = "temp-cluster-" + clusterKey;
            String tempName = structure + "_" + phase + "_" + model + "_" + KeyUtils.componentSuffix(K);
            SystemIdentity temp = buildSystem(tempId, tempName, structure, phase, model,
                    componentArray, resolvedCluster, resolvedSym);

            Optional<IdentificationInputDialog.Input> inp =
                    IdentificationInputDialog.show(temp, "Cluster Identification Input");
            if (inp.isEmpty()) { logResult("⚠ Cancelled."); return; }

            coordinator.createClusterData(temp, clusterKey,
                    inp.get().disorderedClusterFile(), inp.get().orderedClusterFile(),
                    inp.get().disorderedSymmetryGroup(), inp.get().orderedSymmetryGroup(),
                    defaultMatrix(), defaultVec(), K);
        });

        clearButton.setOnAction(e -> resetForm(elementsField, structurePhaseField, modelField));
        return vbox;
    }

    private void setupJobMonitoring() {
        jobManager.addManagerListener(new BackgroundJobManager.JobManagerListener() {
            @Override public void onJobQueued(String id, int sz) { updateJobProgress(); }
            @Override public void onJobStarted(String id)        { updateJobProgress(); }
            @Override public void onJobFinished(String id)       { updateJobProgress(); }
            @Override public void onJobCancelled(String id)      { updateJobProgress(); }
        });
    }

    private void updateJobProgress() {
        Collection<BackgroundJob> jobs = jobManager.getActiveJobs();
        if (jobs.isEmpty()) { resultsPanel.setProgress(0); return; }
        double avg = jobs.stream().mapToInt(BackgroundJob::getProgress).average().orElse(0);
        resultsPanel.setProgress(avg / 100.0);
    }

    // ------------------------------------------------------------------ helpers

    private boolean validateFields(String elements, String structurePhase, String model) {
        if (elements.isEmpty() || structurePhase.isEmpty() || model.isEmpty()) {
            showAlert("Missing Fields", "All fields are required."); return false;
        }
        if (!StructureModelMapping.isValidStructurePhase(structurePhase)) {
            showAlert("Invalid Format", "Structure/Phase must be Structure_Phase (e.g., BCC_A2).\n"
                    + "Known: " + String.join(", ", StructureModelMapping.getKnownPhases()));
            return false;
        }
        return true;
    }

    private static SystemIdentity buildSystem(String id, String name, String structure, String phase,
                                              String model, String[] components,
                                              String clusterFile, String symGroup) {
        return SystemIdentity.builder()
                .id(id).name(name).structure(structure).phase(phase).model(model)
                .components(components).clusterFilePath(clusterFile).symmetryGroupName(symGroup)
                .build();
    }

    private static double[][] defaultMatrix() { return new double[][]{{1,0,0},{0,1,0},{0,0,1}}; }
    private static Vector3D   defaultVec()    { return new Vector3D(0, 0, 0); }

    private static void resetForm(TextField e, TextField sp, TextField m) {
        e.setText("A-B"); sp.setText("BCC_A2"); m.setText("T");
    }

    private void logResult(String msg)   { resultsPanel.logMessage(msg); }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    private boolean confirmAction(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }
}
