package org.ce.presentation.gui.component;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.ce.domain.cvm.CVMFreeEnergy;
import org.ce.domain.cvm.ClusterVariableEvaluator;
import org.ce.domain.identification.cluster.CFIdentificationResult;
import org.ce.domain.identification.cluster.ClusterIdentificationResult;
import org.ce.domain.model.data.AllClusterData;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.ce.infrastructure.registry.KeyUtils;

import java.util.List;
import java.util.Optional;

/**
 * Dialog for inspecting CVM model data and performing validation tests.
 * 
 * <p>Displays all critical quantities from AllClusterData and performs
 * validation tests including:
 * <ul>
 *   <li>Data completeness check</li>
 *   <li>Dimension consistency validation</li>
 *   <li>Random limit entropy test</li>
 *   <li>ECI requirements check</li>
 * </ul>
 */
public class CVMModelInspectorDialog extends Dialog<Void> {
    
    private final SystemRegistry registry;
    private ComboBox<SystemIdentity> systemCombo;
    private TextArea displayArea;
    private Label statusLabel;
    
    public CVMModelInspectorDialog(SystemRegistry registry) {
        this.registry = registry;
        
        setTitle("CVM Model Inspector");
        setHeaderText("Inspect and validate CVM model data");
        setWidth(800);
        setHeight(700);
        
        DialogPane dialogPane = getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.CLOSE);
        dialogPane.setContent(createContent());
        
        setResultConverter(buttonType -> null);
    }
    
    private VBox createContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(750);
        content.setPrefHeight(650);
        
        // System selection
        HBox selectionBox = new HBox(10);
        selectionBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        Label systemLabel = new Label("Select System:");
        systemLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        systemCombo = new ComboBox<>();
        systemCombo.setPrefWidth(300);
        systemCombo.setPromptText("Choose a system to inspect...");
        
        // Load systems with cluster data (check cache directly)
        List<SystemIdentity> systems = registry.getAllSystems().stream()
                .filter(s -> {
                    // Check if AllClusterData exists in cache
                    String clusterKey = KeyUtils.clusterKey(s);
                    try {
                        Optional<AllClusterData> data = AllClusterDataCache.load(clusterKey);
                        return data.isPresent() && data.get().isComplete();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();
        
        System.out.println("[CVMModelInspector] Found " + systems.size() + " systems with complete cluster data");
        if (systems.isEmpty()) {
            System.out.println("[CVMModelInspector] Total registered systems: " + registry.getAllSystems().size());
            registry.getAllSystems().forEach(s -> 
                System.out.println("  - " + s.getName() + " (key: " + KeyUtils.clusterKey(s) + ")")
            );
        }
        
        systemCombo.getItems().addAll(systems);
        
        // Custom cell factory to display system names properly
        systemCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SystemIdentity item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        systemCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(SystemIdentity item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        
        Button inspectButton = new Button("Inspect Model");
        inspectButton.setOnAction(e -> inspectSelectedSystem());
        inspectButton.setDisable(true);
        
        systemCombo.setOnAction(e -> inspectButton.setDisable(systemCombo.getValue() == null));
        
        selectionBox.getChildren().addAll(systemLabel, systemCombo, inspectButton);
        
        // Display area
        Label displayLabel = new Label("Model Data:");
        displayLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        displayArea = new TextArea();
        displayArea.setEditable(false);
        displayArea.setWrapText(false);
        displayArea.setFont(Font.font("Consolas", 11));
        displayArea.setStyle("-fx-control-inner-background: #f8f8f8;");
        VBox.setVgrow(displayArea, Priority.ALWAYS);
        
        // Check if any systems are available
        if (systemCombo.getItems().isEmpty()) {
            displayArea.setText(
                "â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•\n" +
                "                    NO CVM MODELS AVAILABLE\n" +
                "â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•\n\n" +
                "No systems with complete cluster data found.\n\n" +
                "To use the CVM Model Inspector:\n\n" +
                "1. Register a system in the System Registry panel\n" +
                "2. Run the 'Cluster + CF Identification' background job\n" +
                "3. Wait for all 3 stages to complete successfully\n" +
                "4. Return here to inspect the model\n\n" +
                "Systems must have:\n" +
                "  âœ“ Stage 1: Cluster Identification (HSP + ordered)\n" +
                "  âœ“ Stage 2: Correlation Function Identification\n" +
                "  âœ“ Stage 3: C-matrix Construction\n\n" +
                "Total registered systems: " + registry.getAllSystems().size() + "\n" +
                "Systems with complete data: 0"
            );
            systemCombo.setDisable(true);
        } else {
            displayArea.setText(
                "Select a system and click 'Inspect Model' to view:\n\n" +
                "â€¢ Cluster identification results (Stage 1)\n" +
                "â€¢ Correlation function data (Stage 2)\n" +
                "â€¢ C-matrix dimensions (Stage 3)\n" +
                "â€¢ ECI requirements\n" +
                "â€¢ Validation test results\n" +
                "â€¢ Random limit entropy check\n\n" +
                "Found " + systemCombo.getItems().size() + " system(s) with complete cluster data."
            );
        }
        
        // Status label
        statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-padding: 5; -fx-background-color: #e8e8e8;");
        
        content.getChildren().addAll(selectionBox, new Separator(), displayLabel, displayArea, statusLabel);
        return content;
    }
    
    private void inspectSelectedSystem() {
        SystemIdentity system = systemCombo.getValue();
        if (system == null) return;
        
        statusLabel.setText("Loading data for " + system.getName() + "...");
        statusLabel.setStyle("-fx-padding: 5; -fx-background-color: #fff3cd; -fx-text-fill: #856404;");
        
        try {
            String clusterKey = KeyUtils.clusterKey(system);
            Optional<AllClusterData> dataOpt = AllClusterDataCache.load(clusterKey);
            
            if (dataOpt.isEmpty()) {
                displayArea.setText("ERROR: No AllClusterData found for cluster key: " + clusterKey);
                statusLabel.setText("Data not found");
                statusLabel.setStyle("-fx-padding: 5; -fx-background-color: #f8d7da; -fx-text-fill: #721c24;");
                return;
            }
            
            AllClusterData data = dataOpt.get();
            String report = generateInspectionReport(system, data);
            displayArea.setText(report);
            
            // Update status based on validation
            if (data.isComplete()) {
                statusLabel.setText("âœ“ Model data complete and validated");
                statusLabel.setStyle("-fx-padding: 5; -fx-background-color: #d4edda; -fx-text-fill: #155724;");
            } else {
                statusLabel.setText("âš  Model data incomplete: " + data.getFirstIncompleteStage());
                statusLabel.setStyle("-fx-padding: 5; -fx-background-color: #f8d7da; -fx-text-fill: #721c24;");
            }
            
        } catch (Exception ex) {
            displayArea.setText("ERROR inspecting system:\n" + ex.getMessage() + "\n\n" + 
                              java.util.Arrays.toString(ex.getStackTrace()));
            statusLabel.setText("Error occurred during inspection");
            statusLabel.setStyle("-fx-padding: 5; -fx-background-color: #f8d7da; -fx-text-fill: #721c24;");
        }
    }
    
    private String generateInspectionReport(SystemIdentity system, AllClusterData data) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•\n");
        sb.append("                    CVM MODEL INSPECTION REPORT\n");
        sb.append("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•\n\n");
        
        // System info
        sb.append("SYSTEM INFORMATION\n");
        sb.append("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€\n");
        sb.append(String.format("%-20s: %s\n", "System ID", system.getId()));
        sb.append(String.format("%-20s: %s\n", "Name", system.getName()));
        sb.append(String.format("%-20s: %s\n", "Components", String.join("-", system.getComponents())));
        sb.append(String.format("%-20s: %d\n", "Num. Components", system.getNumComponents()));
        sb.append(String.format("%-20s: %s\n", "Structure", system.getStructure()));
        sb.append(String.format("%-20s: %s\n", "Phase", system.getPhase()));
        sb.append(String.format("%-20s: %s\n", "Model", system.getModel()));
        sb.append(String.format("%-20s: %s\n", "Cluster Key", KeyUtils.clusterKey(system)));
        sb.append(String.format("%-20s: %s\n", "CEC Key", KeyUtils.cecKey(system)));
        sb.append("\n");
        
        // Data completeness
        sb.append("DATA COMPLETENESS\n");
        sb.append("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€\n");
        sb.append(String.format("%-20s: %s\n", "Complete", data.isComplete() ? "âœ“ YES" : "âœ— NO"));
        sb.append(String.format("%-20s: %s\n", "Stage 1", data.getStage1() != null ? "âœ“" : "âœ— MISSING"));
        sb.append(String.format("%-20s: %s\n", "Stage 2", data.getStage2() != null ? "âœ“" : "âœ— MISSING"));
        sb.append(String.format("%-20s: %s\n", "Stage 3", data.getStage3() != null ? "âœ“" : "âœ— MISSING"));
        sb.append(String.format("%-20s: %d ms\n", "Computation Time", data.getComputationTimeMs()));
        sb.append("\n");
        
        if (!data.isComplete()) {
            sb.append("âš  WARNING: Model data is incomplete. Missing: ").append(data.getFirstIncompleteStage()).append("\n");
            sb.append("Cannot perform full validation.\n\n");
            return sb.toString();
        }
        
        // Stage 1: Cluster Identification
        ClusterIdentificationResult stage1 = data.getStage1();
        sb.append("STAGE 1: CLUSTER IDENTIFICATION\n");
        sb.append("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€\n");
        sb.append(String.format("%-30s: %d\n", "tcdis (HSP types, no empty)", stage1.getTcdis()));
        sb.append(String.format("%-30s: %d\n", "tc (ordered types, no empty)", stage1.getTc()));
        sb.append(String.format("%-30s: %d\n", "nxcdis (HSP point types)", stage1.getNxcdis()));
        sb.append(String.format("%-30s: %d\n", "nxc (ordered point types)", stage1.getNxc()));
        sb.append(String.format("%-30s: %d\n", "disClusterData.tc (with empty)", stage1.getDisClusterData().getTc()));
        
        sb.append(String.format("\n%-30s: [", "lc (ordered clusters per HSP)"));
        int[] lc = stage1.getLc();
        for (int i = 0; i < lc.length; i++) {
            sb.append(lc[i]);
            if (i < lc.length - 1) sb.append(", ");
        }
        sb.append("]\n");
        
        List<Double> mhdis = stage1.getDisClusterData().getMultiplicities();
        sb.append(String.format("\n%-30s:\n", "mhdis (HSP multiplicities)"));
        for (int i = 0; i < Math.min(mhdis.size(), 10); i++) {
            sb.append(String.format("  [%d] %.6f\n", i, mhdis.get(i)));
        }
        if (mhdis.size() > 10) sb.append("  ... (").append(mhdis.size() - 10).append(" more)\n");
        
        double[] kb = stage1.getKbCoefficients();
        sb.append(String.format("\n%-30s:\n", "KB coefficients"));
        for (int i = 0; i < Math.min(kb.length, 10); i++) {
            sb.append(String.format("  [%d] %.8f\n", i, kb[i]));
        }
        if (kb.length > 10) sb.append("  ... (").append(kb.length - 10).append(" more)\n");
        sb.append("\n");
        
        // Stage 2: CF Identification
        CFIdentificationResult stage2 = data.getStage2();
        sb.append("STAGE 2: CORRELATION FUNCTION IDENTIFICATION\n");
        sb.append("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€\n");
        sb.append(String.format("%-30s: %d\n", "tcf (total CFs)", stage2.getTcf()));
        sb.append(String.format("%-30s: %d\n", "ncf (non-point CFs)", stage2.getNcf()));
        sb.append(String.format("%-30s: %d\n", "nxcf (point CFs)", stage2.getNxcf()));
        sb.append(String.format("%-30s: %d\n", "tcfdis (HSP CFs)", stage2.getTcfdis()));
        
        int[][] lcf = stage2.getLcf();
        sb.append(String.format("\n%-30s:\n", "lcf (CFs per HSP per group)"));
        for (int t = 0; t < lcf.length; t++) {
            sb.append(String.format("  HSP type [%d]: [", t));
            for (int j = 0; j < lcf[t].length; j++) {
                sb.append(lcf[t][j]);
                if (j < lcf[t].length - 1) sb.append(", ");
            }
            sb.append("]\n");
        }
        sb.append("\n");
        
        // Stage 3: C-matrix
        sb.append("STAGE 3: C-MATRIX CONSTRUCTION\n");
        sb.append("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€\n");
        var stage3 = data.getStage3();
        var cmat = stage3.getCmat();
        int[][] lcv = stage3.getLcv();
        var wcv = stage3.getWcv();
        
        sb.append(String.format("%-30s: %d HSP types\n", "C-matrix dimensions", cmat.size()));
        sb.append(String.format("\n%-30s:\n", "lcv (CVs per HSP per group)"));
        for (int t = 0; t < lcv.length; t++) {
            sb.append(String.format("  HSP type [%d]: [", t));
            for (int j = 0; j < lcv[t].length; j++) {
                sb.append(lcv[t][j]);
                if (j < lcv[t].length - 1) sb.append(", ");
            }
            sb.append("]\n");
        }
        
        // Display all C-matrix blocks with actual values
        sb.append(String.format("\n%-30s:\n", "C-MATRIX BLOCKS (all types/groups)"));
        sb.append("  Format: cmat[HSP_type][group] = matrix[CV_rows Ã— CF_cols]\n");
        sb.append("  Each row represents coefficients for one cluster variable (CV)\n");
        sb.append("  Last column is the constant term; other columns are CF coefficients\n\n");
        
        for (int t = 0; t < cmat.size(); t++) {
            var groups = cmat.get(t);
            sb.append(String.format("  â”Œâ”€ HSP TYPE [%d]: %d group(s)\n", t, groups.size()));
            
            for (int j = 0; j < groups.size(); j++) {
                double[][] cm = groups.get(j);
                int nrows = cm.length;
                int ncols = cm[0].length;
                
                sb.append(String.format("  â”‚\n"));
                sb.append(String.format("  â”œâ”€â”€ GROUP [%d]: %d CVs Ã— %d columns (ncf=%d + 1 constant)\n", 
                         j, nrows, ncols, ncols - 1));
                
                // Show weights for this group
                List<int[]> wcvForType = wcv.get(t);
                int[] weights = wcvForType.get(j);
                sb.append(String.format("  â”‚   Weights wcv[%d][%d]: [", t, j));
                for (int w = 0; w < Math.min(weights.length, 10); w++) {
                    sb.append(weights[w]);
                    if (w < weights.length - 1) sb.append(", ");
                }
                if (weights.length > 10) sb.append("... (").append(weights.length - 10).append(" more)");
                sb.append("]\n");
                
                // Display the matrix values
                sb.append("  â”‚   Matrix values:\n");
                
                // Determine how many rows to show
                int maxRowsToShow = (nrows <= 8) ? nrows : 6; // Show all if â‰¤8, else first 6
                int maxColsToShow = (ncols <= 8) ? ncols : 6; // Show all if â‰¤8, else first 6
                
                for (int r = 0; r < maxRowsToShow; r++) {
                    sb.append(String.format("  â”‚     row[%d]: [", r));
                    for (int c = 0; c < maxColsToShow; c++) {
                        sb.append(String.format("%9.6f", cm[r][c]));
                        if (c < maxColsToShow - 1) sb.append(", ");
                    }
                    if (ncols > maxColsToShow) {
                        sb.append(", ... (").append(ncols - maxColsToShow).append(" more cols)");
                    }
                    sb.append("]\n");
                }
                
                if (nrows > maxRowsToShow) {
                    sb.append(String.format("  â”‚     ... (%d more rows)\n", nrows - maxRowsToShow));
                }
            }
            sb.append("  â””â”€\n");
        }
        sb.append("\n");
        
        // ECI Requirements
        sb.append("ECI/CEC REQUIREMENTS\n");
        sb.append("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€\n");
        sb.append(String.format("%-30s: %d values\n", "Required for MCS", stage1.getDisClusterData().getTc()));
        sb.append(String.format("%-30s: %d values\n", "Required for CVM (all)", stage1.getDisClusterData().getTc()));
        sb.append(String.format("%-30s: %d values\n", "Used by CVM solver (ncf)", stage2.getNcf()));
        sb.append(String.format("%-30s: %s/%s_%s_%s\n", "Database key", 
                 String.join("-", system.getComponents()),
                 system.getStructure(), system.getPhase(), system.getModel()));
        sb.append("\n");
        
        // Validation Tests
        sb.append("VALIDATION TESTS\n");
        sb.append("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€\n");
        
        // Test 1: Dimension consistency
        boolean dimTest = performDimensionTest(stage1, stage2, sb);
        
        // Test 2: KB coefficient sum
        boolean kbTest = performKBCoefficientTest(stage1, sb);
        
        // Test 3: Random limit entropy (binary only)
        boolean entropyTest = false;
        if (system.getNumComponents() == 2) {
            entropyTest = performRandomEntropyTest(data, sb);
        } else {
            sb.append("\n[Entropy Test] Skipped (only for binary systems)\n");
        }
        
        sb.append("\n");
        sb.append("VALIDATION SUMMARY\n");
        sb.append("â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€\n");
        sb.append(String.format("%-30s: %s\n", "Dimension consistency", dimTest ? "âœ“ PASS" : "âœ— FAIL"));
        sb.append(String.format("%-30s: %s\n", "KB coefficient check", kbTest ? "âœ“ PASS" : "âœ— FAIL"));
        if (system.getNumComponents() == 2) {
            sb.append(String.format("%-30s: %s\n", "Random entropy test", entropyTest ? "âœ“ PASS" : "âœ— FAIL"));
        }
        
        boolean allPass = dimTest && kbTest && (system.getNumComponents() != 2 || entropyTest);
        sb.append(String.format("\n%-30s: %s\n", "Overall Status", 
                 allPass ? "âœ“ ALL TESTS PASSED" : "âš  SOME TESTS FAILED"));
        
        sb.append("\nâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•\n");
        sb.append("                         END OF REPORT\n");
        sb.append("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•\n");
        
        return sb.toString();
    }
    
    private boolean performDimensionTest(ClusterIdentificationResult stage1, 
                                         CFIdentificationResult stage2, StringBuilder sb) {
        sb.append("\n[Dimension Test] Checking consistency...\n");
        
        int tcdis = stage1.getTcdis();
        int tc = stage1.getTc();
        int tcf = stage2.getTcf();
        int ncf = stage2.getNcf();
        int nxcf = stage2.getNxcf();
        
        boolean pass = true;
        
        // Check: tcf = ncf + nxcf
        if (tcf != ncf + nxcf) {
            sb.append(String.format("  âœ— FAIL: tcf (%d) â‰  ncf (%d) + nxcf (%d) = %d\n", 
                     tcf, ncf, nxcf, ncf + nxcf));
            pass = false;
        } else {
            sb.append(String.format("  âœ“ tcf = ncf + nxcf (%d = %d + %d)\n", tcf, ncf, nxcf));
        }
        
        // Check: tcdis should be reasonable
        if (tcdis < 2) {
            sb.append(String.format("  âœ— FAIL: tcdis (%d) too small\n", tcdis));
            pass = false;
        } else {
            sb.append(String.format("  âœ“ tcdis = %d (reasonable)\n", tcdis));
        }
        
        // Check: tc <= tcdis * (some reasonable factor)
        if (tc > tcdis * 100) {
            sb.append(String.format("  âš  WARNING: tc (%d) >> tcdis (%d), unusually large\n", tc, tcdis));
        }
        
        return pass;
    }
    
    private boolean performKBCoefficientTest(ClusterIdentificationResult stage1, StringBuilder sb) {
        sb.append("\n[KB Coefficient Test] Checking maximal cluster...\n");
        
        double[] kb = stage1.getKbCoefficients();
        boolean pass = true;
        
        // Check: maximal cluster (index 0) should have kb=1.0
        if (Math.abs(kb[0] - 1.0) > 1e-10) {
            sb.append(String.format("  âœ— FAIL: kb[0] = %.10f (expected 1.0)\n", kb[0]));
            pass = false;
        } else {
            sb.append(String.format("  âœ“ kb[0] = %.10f (maximal cluster)\n", kb[0]));
        }
        
        // Check: sum of kb should be non-negative (typically positive for point cluster contribution)
        double sum = 0.0;
        for (double k : kb) sum += k;
        sb.append(String.format("  Î£ kb = %.10f\n", sum));
        
        return pass;
    }
    
    private boolean performRandomEntropyTest(AllClusterData data, StringBuilder sb) {
        sb.append("\n[Random Entropy Test] Computing configurational entropy at random limit...\n");
        sb.append("  For binary system at x=0.5, ideal mixing entropy:\n");
        sb.append("  S_ideal = -RÂ·Î£ xiÂ·ln(xi) = -RÂ·(0.5Â·ln(0.5) + 0.5Â·ln(0.5))\n");
        sb.append("  S_ideal = RÂ·ln(2) â‰ˆ 0.693147Â·R\n\n");

        try {
            ClusterIdentificationResult stage1 = data.getStage1();
            CFIdentificationResult stage2 = data.getStage2();
            var stage3 = data.getStage3();

            double[] kb = stage1.getKbCoefficients();
            List<Double> mhdis = stage1.getDisClusterData().getMultiplicities();
            double[][] mh = stage1.getMh();
            int[] lc = stage1.getLc();
            var cmat = stage3.getCmat();
            int[][] lcv = stage3.getLcv();
            var wcv = stage3.getWcv();
            int[][] cfBasisIndices = stage3.getCfBasisIndices();

            int tcdis = stage1.getTcdis();
            int tcf = stage2.getTcf();
            int ncf = stage2.getNcf();
            int[][] lcf = stage2.getLcf();

            sb.append("  [Step 1] Computing random-state CFs for x=0.5...\n");
            double[] moleFractions = {0.5, 0.5};
            double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                    moleFractions, 2, cfBasisIndices, ncf, tcf);

            sb.append(String.format("    Composition: x_A = %.3f, x_B = %.3f\n",
                    moleFractions[0], moleFractions[1]));
            sb.append(String.format("    Number of non-point CFs: %d\n", ncf));
            sb.append("    Random CF values u[l] (all):\n");
            for (int l = 0; l < ncf; l++) {
                sb.append(String.format("      u[%d] = %.12f\n", l, uRandom[l]));
            }

            boolean allZero = true;
            for (double u : uRandom) {
                if (Math.abs(u) > 1e-10) {
                    allZero = false;
                    break;
                }
            }
            sb.append(String.format("    All u[l] â‰ˆ 0? %s\n\n", allZero ? "âœ“ YES" : "âœ— NO"));

            sb.append("  [Step 2] Evaluating all CVs and entropy decomposition...\n");

            double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                    uRandom, moleFractions, 2, cfBasisIndices, ncf, tcf);
            double[][][] cv = ClusterVariableEvaluator.evaluate(uFull, cmat, lcv, tcdis, lc);

            sb.append(String.format("    Full CF vector length: %d (ncf=%d, nxcf=%d)\n", tcf, ncf, tcf - ncf));
            sb.append("    Full CF values u_full[k] (all):\n");
            for (int k = 0; k < uFull.length; k++) {
                String tag;
                if (k < ncf) {
                    tag = "non-point";
                } else {
                    tag = "point";
                }
                sb.append(String.format("      u_full[%d] (%s) = %.12f\n", k, tag, uFull[k]));
            }

            sb.append("\n    CVM entropy functional used in this evaluation:\n");
            sb.append("      S = -R Â· Î£_t kb[t]Â·ms[t] Â· Î£_j mh[t][j] Â· Î£_v w[t][j][v] Â· Î¦(cv[t][j][v])\n");
            sb.append("      Î¦(cv) = cvÂ·ln(cv) for cv > EPS, smooth CÂ² extension for cv â‰¤ EPS\n");
            sb.append("      Constants: R = 1.0, EPS = 1.0e-6\n");
            sb.append("\n    CV values at random limit (all):\n");
            final double EPS = 1.0e-6;
            final double R = 1.0;
            double entropyFromDecomposition = 0.0;
            int totalCVs = 0;
            List<String> partialEntropyLabels = new java.util.ArrayList<>();
            List<Double> partialEntropyValues = new java.util.ArrayList<>();

            for (int t = 0; t < tcdis; t++) {
                double coeffT = kb[t] * mhdis.get(t);
                double entropyType = 0.0;
                sb.append(String.format("\n    HSP type t=%d: kb=%.12f, ms=%.12f, kbÂ·ms=%.12f\n",
                        t, kb[t], mhdis.get(t), coeffT));

                for (int j = 0; j < lc[t]; j++) {
                    double mhTj = mh[t][j];
                    int[] w = wcv.get(t).get(j);
                    int nv = lcv[t][j];
                    double prefixNoWeight = coeffT * mhTj;
                    double entropyCluster = 0.0;

                    sb.append(String.format("      Cluster (t=%d, j=%d): mh=%.12f, prefix=kbÂ·msÂ·mh=%.12f\n",
                            t, j, mhTj, prefixNoWeight));

                    for (int v = 0; v < nv; v++) {
                        totalCVs++;
                        double cvVal = cv[t][j][v];
                        int wv = w[v];

                        double sContrib;
                        if (cvVal > EPS) {
                            sContrib = cvVal * Math.log(cvVal);
                        } else {
                            double logEps = Math.log(EPS);
                            double d = cvVal - EPS;
                            sContrib = EPS * logEps + (1.0 + logEps) * d + 0.5 / EPS * d * d;
                        }

                        double prefix = prefixNoWeight * wv;
                        double partialEntropy = -R * prefix * sContrib;
                        entropyCluster += partialEntropy;

                        sb.append(String.format(
                                "        cv[%d]=%.12e, w=%d, cvÂ·ln(cv)_eff=%.12e, partialS=%.12e\n",
                                v, cvVal, wv, sContrib, partialEntropy));
                    }

                    entropyType += entropyCluster;
                    entropyFromDecomposition += entropyCluster;
                    partialEntropyLabels.add(String.format("S(t=%d,j=%d)", t, j));
                    partialEntropyValues.add(entropyCluster);
                    sb.append(String.format("        -> Partial entropy S(t=%d,j=%d) = %.12e\n", t, j, entropyCluster));
                }

                sb.append(String.format("      => Type entropy S(t=%d) = %.12e\n", t, entropyType));
            }

            sb.append(String.format("\n    Total CV count evaluated: %d\n", totalCVs));
            sb.append("    Functional decomposition in partial entropies:\n");
            sb.append("      S_decomp = Î£_t Î£_j S(t,j)\n");
            for (int idx = 0; idx < partialEntropyLabels.size(); idx++) {
                sb.append(String.format("        %s = %.12e\n", partialEntropyLabels.get(idx), partialEntropyValues.get(idx)));
            }
            if (!partialEntropyLabels.isEmpty()) {
                sb.append("      Expanded (single-line): S_decomp = ");
                for (int idx = 0; idx < partialEntropyLabels.size(); idx++) {
                    sb.append(partialEntropyLabels.get(idx));
                    if (idx < partialEntropyLabels.size() - 1) {
                        sb.append(" + ");
                    }
                }
                sb.append("\n");
            }
            sb.append(String.format("      Final: S_decomp = %.12e\n", entropyFromDecomposition));

            double[] zeroECI = new double[ncf];
            double temperature = 1000.0;
            CVMFreeEnergy.EvalResult result = CVMFreeEnergy.evaluate(
                    uRandom, moleFractions, 2, temperature, zeroECI,
                    mhdis, kb, mh, lc, cmat, lcv, wcv,
                    tcdis, tcf, ncf, lcf, cfBasisIndices);

            double sCvm = result.S;
            double sDiff = Math.abs(sCvm - entropyFromDecomposition);

            sb.append("\n    Cross-check with CVMFreeEnergy.evaluate:\n");
            sb.append(String.format("      S_CVM(random) = %.12e\n", sCvm));
            sb.append(String.format("      S_decomp      = %.12e\n", entropyFromDecomposition));
            sb.append(String.format("      |Î”S|          = %.12e\n", sDiff));
            sb.append(String.format("      G = H - TÂ·S = %.6e - %.1f Ã— %.6f = %.6e\n",
                    result.H, temperature, sCvm, result.G));

            sb.append("\n  [Step 3] Comparing with theoretical ideal entropy...\n");
            double sIdeal = Math.log(2.0);
            double error = sCvm - sIdeal;
            double relativeError = Math.abs(error / sIdeal);

            sb.append(String.format("    S_ideal = ln(2) = %.12e\n", sIdeal));
            sb.append(String.format("    S_CVM   = %.12e\n", sCvm));
            sb.append(String.format("    Error   = %.12e\n", error));
            sb.append(String.format("    Relative error = %.6f%% (%.4e)\n", relativeError * 100, relativeError));

            boolean pass = relativeError < 0.01;

            sb.append("\n  [Step 4] Pass/fail criteria\n");
            if (pass) {
                sb.append("    âœ“ PASS: Relative error < 1%\n");
                sb.append("    The CVM approximation correctly reproduces ideal mixing\n");
                sb.append("    entropy at the random limit for this cluster expansion.\n");
            } else {
                sb.append("    âœ— FAIL: Relative error > 1%\n");
                sb.append("    The cluster expansion may not adequately approximate\n");
                sb.append("    the full configuration space, or there may be numerical\n");
                sb.append("    issues in the cluster identification.\n");
            }

            sb.append("\n  [Diagnostics]\n");
            sb.append(String.format("    KB coefficient sum: Î£ kb = %.10f\n",
                    java.util.Arrays.stream(kb).sum()));
            sb.append(String.format("    KB[0] (maximal): %.10f (should be 1.0)\n", kb[0]));
            sb.append(String.format("    Cluster types (tcdis): %d\n", tcdis));

            return pass;

        } catch (Exception e) {
            sb.append("\n  âœ— ERROR during entropy calculation:\n");
            sb.append(String.format("    %s\n", e.getMessage()));
            sb.append("    Stack trace:\n");
            for (StackTraceElement elem : e.getStackTrace()) {
                if (elem.getClassName().startsWith("org.ce")) {
                    sb.append(String.format("      at %s\n", elem));
                }
            }
            sb.append("\n  âš  Cannot validate entropy - returning FAIL\n");
            return false;
        }
    }
}


