package org.ce.workbench.gui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Dialog for selecting crystal structure, phase, and CVM approximation model.
 * Filters available options based on number of components selected.
 */
public class StructureModelSelectionDialog extends Dialog<StructureModelInfo> {
    
    private final int componentCount;
    private final List<String> componentList;
    
    private ComboBox<String> structureCombo;
    private ComboBox<String> phaseCombo;
    private ToggleGroup cvmToggleGroup;
    private VBox cvmBox;
    private Label descriptionLabel;
    private Label availabilityLabel;
    
    private Map<String, Map<String, Map<String, Map<String, Object>>>> structureData;
    private StructureModelInfo currentSelection;
    
    public StructureModelSelectionDialog(List<String> components) {
        this.componentList = components;
        this.componentCount = components.size();
        
        setTitle("Select Structure & CVM Model");
        setHeaderText("Choose crystal structure, phase, and CVM approximation for:\n" +
                     String.join("-", components) + " (" + componentCount + " component" + 
                     (componentCount > 1 ? "s" : "") + ")");
        
        // Load structure model data
        loadStructureModels();
        
        // Create dialog content
        DialogPane dialogPane = getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogPane.setContent(createContent());
        
        // Disable OK button until valid selection is made
        Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        
        // Set result converter
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK && currentSelection != null) {
                return currentSelection;
            }
            return null;
        });
        
        // Initialize with first available structure
        if (!structureCombo.getItems().isEmpty()) {
            structureCombo.getSelectionModel().selectFirst();
        }
    }
    
    private VBox createContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        
        // Structure selection
        Label structureLabel = new Label("Crystal Structure:");
        structureLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        structureCombo = new ComboBox<>();
        structureCombo.setMaxWidth(Double.MAX_VALUE);
        structureCombo.setOnAction(e -> onStructureChanged());
        
        // Phase selection
        Label phaseLabel = new Label("Phase:");
        phaseLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        phaseCombo = new ComboBox<>();
        phaseCombo.setMaxWidth(Double.MAX_VALUE);
        phaseCombo.setOnAction(e -> onPhaseChanged());
        
        // CVM Model selection
        Label cvmLabel = new Label("CVM Approximation:");
        cvmLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        cvmToggleGroup = new ToggleGroup();
        cvmBox = new VBox(5);
        cvmBox.setPadding(new Insets(5, 0, 0, 20));
        
        // Description area
        descriptionLabel = new Label();
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 10;");
        
        // Availability indicator
        availabilityLabel = new Label();
        availabilityLabel.setWrapText(true);
        
        // Populate structure combo
        populateStructureCombo();
        
        content.getChildren().addAll(
            structureLabel, structureCombo,
            phaseLabel, phaseCombo,
            cvmLabel, cvmBox,
            new Separator(),
            descriptionLabel,
            availabilityLabel
        );
        
        return content;
    }
    
    private void loadStructureModels() {
        structureData = new HashMap<>();
        
        try (InputStream input = getClass().getResourceAsStream("/data/structure_models.yaml")) {
            if (input == null) {
                showError("Cannot load structure_models.yaml");
                return;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String line;
            
            String currentStructure = null;
            String currentPhase = null;
            String currentCvmModel = null;
            Map<String, Map<String, Map<String, Object>>> structPhases = null;
            Map<String, Object> phaseData = null;
            Map<String, Object> cvmData = null;
            Map<String, Map<String, Object>> cvmModels = null;
            
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                
                int indent = line.indexOf(line.trim());
                
                // Structure level (indent 2)
                if (indent == 2 && !trimmed.startsWith("-") && trimmed.contains(":") && !trimmed.contains("structures:")) {
                    currentStructure = trimmed.substring(0, trimmed.indexOf(':')).trim();
                    structPhases = new HashMap<>();
                    structPhases.put("phases", new HashMap<>());
                    structureData.put(currentStructure, structPhases);
                    currentPhase = null;
                    currentCvmModel = null;
                }
                // Phase level (indent 6)
                else if (indent == 6 && !trimmed.startsWith("-") && trimmed.contains(":") && 
                        currentStructure != null && !trimmed.startsWith("cvmModels:")) {
                    currentPhase = trimmed.substring(0, trimmed.indexOf(':')).trim();
                    phaseData = new HashMap<>();
                    cvmModels = new HashMap<>();
                    phaseData.put("cvmModels", cvmModels);
                    structPhases.get("phases").put(currentPhase, phaseData);
                    currentCvmModel = null;
                }
                // CVM Model level (indent 10)
                else if (indent == 10 && !trimmed.startsWith("-") && trimmed.contains(":") && 
                        currentPhase != null && trimmed.length() <= 4) {
                    currentCvmModel = trimmed.substring(0, trimmed.indexOf(':')).trim();
                    cvmData = new HashMap<>();
                    cvmModels.put(currentCvmModel, cvmData);
                }
                // Properties
                else if (trimmed.contains(":")) {
                    String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                    String value = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                    
                    if (currentCvmModel != null && cvmData != null) {
                        // CVM model property
                        parseCvmProperty(cvmData, key, value);
                    } else if (currentPhase != null && phaseData != null) {
                        // Phase property
                        parsePhaseProperty(phaseData, key, value);
                    }
                }
            }
            
        } catch (Exception e) {
            showError("Error loading structure models: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void parsePhaseProperty(Map<String, Object> phaseData, String key, String value) {
        switch (key) {
            case "description":
                phaseData.put("description", value.replace("\"", ""));
                break;
            case "ordered":
                phaseData.put("ordered", Boolean.parseBoolean(value));
                break;
            case "supportedComponents":
                // Parse [2, 3, 4] format
                String nums = value.replace("[", "").replace("]", "").trim();
                List<Integer> supported = new ArrayList<>();
                for (String num : nums.split(",")) {
                    supported.add(Integer.parseInt(num.trim()));
                }
                phaseData.put("supportedComponents", supported);
                break;
        }
    }
    
    private void parseCvmProperty(Map<String, Object> cvmData, String key, String value) {
        switch (key) {
            case "name":
                cvmData.put("name", value.replace("\"", ""));
                break;
            case "clusterFile":
                cvmData.put("clusterFile", value.replace("\"", ""));
                break;
            case "symGroup":
                cvmData.put("symGroup", value.replace("\"", ""));
                break;
            case "symMatFile":
                if (!value.equals("null")) {
                    cvmData.put("symMatFile", value.replace("\"", ""));
                }
                break;
            case "maxClusterSize":
                cvmData.put("maxClusterSize", Integer.parseInt(value));
                break;
            case "transformMatrix":
                // Parse [[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]] format
                // Simplified: just store as string for now, parse when needed
                cvmData.put("transformMatrix", value);
                break;
            case "translationVector":
                // Parse [0.5, 0.5, 0.5] format
                cvmData.put("translationVector", value);
                break;
        }
    }
    
    private void populateStructureCombo() {
        structureCombo.getItems().clear();
        
        // Add structures that have at least one phase supporting this component count
        for (Map.Entry<String, Map<String, Map<String, Map<String, Object>>>> structEntry : structureData.entrySet()) {
            String structureName = structEntry.getKey();
            Map<String, Map<String, Object>> phases = structEntry.getValue().get("phases");
            
            if (phases != null && hasCompatiblePhase(phases)) {
                structureCombo.getItems().add(structureName);
            }
        }
    }
    
    private boolean hasCompatiblePhase(Map<String, Map<String, Object>> phases) {
        for (Map<String, Object> phaseData : phases.values()) {
            List<Integer> supported = (List<Integer>) phaseData.get("supportedComponents");
            if (supported != null && supported.contains(componentCount)) {
                return true;
            }
        }
        return false;
    }
    
    private void onStructureChanged() {
        String structure = structureCombo.getValue();
        if (structure == null) return;
        
        phaseCombo.getItems().clear();
        cvmBox.getChildren().clear();
        
        Map<String, Map<String, Object>> phases = structureData.get(structure).get("phases");
        if (phases == null) return;
        
        // Add compatible phases
        for (Map.Entry<String, Map<String, Object>> phaseEntry : phases.entrySet()) {
            String phaseName = phaseEntry.getKey();
            Map<String, Object> phaseData = phaseEntry.getValue();
            List<Integer> supported = (List<Integer>) phaseData.get("supportedComponents");
            
            if (supported != null && supported.contains(componentCount)) {
                phaseCombo.getItems().add(phaseName);
            }
        }
        
        // Select first phase
        if (!phaseCombo.getItems().isEmpty()) {
            phaseCombo.getSelectionModel().selectFirst();
        }
        
        updateDescription();
    }
    
    private void onPhaseChanged() {
        String structure = structureCombo.getValue();
        String phase = phaseCombo.getValue();
        if (structure == null || phase == null) return;
        
        cvmBox.getChildren().clear();
        cvmToggleGroup.getToggles().clear();
        
        Map<String, Object> phaseData = structureData.get(structure).get("phases").get(phase);
        if (phaseData == null) return;
        
        Map<String, Map<String, Object>> cvmModels = (Map<String, Map<String, Object>>) phaseData.get("cvmModels");
        if (cvmModels == null) return;
        
        // Create radio buttons for each CVM model
        for (Map.Entry<String, Map<String, Object>> cvmEntry : cvmModels.entrySet()) {
            String cvmCode = cvmEntry.getKey();
            Map<String, Object> cvmData = cvmEntry.getValue();
            String cvmName = (String) cvmData.get("name");
            
            RadioButton radio = new RadioButton(cvmName + " (" + cvmCode + ")");
            radio.setToggleGroup(cvmToggleGroup);
            radio.setUserData(cvmCode);
            radio.setOnAction(e -> onCvmChanged());
            
            cvmBox.getChildren().add(radio);
        }
        
        // Select first CVM model
        if (!cvmToggleGroup.getToggles().isEmpty()) {
            cvmToggleGroup.getToggles().get(0).setSelected(true);
            onCvmChanged();
        }
        
        updateDescription();
    }
    
    private void onCvmChanged() {
        updateModelInfo();
        updateDescription();
        
        // Enable OK button
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(currentSelection == null);
    }
    
    private void updateModelInfo() {
        String structure = structureCombo.getValue();
        String phase = phaseCombo.getValue();
        Toggle selectedToggle = cvmToggleGroup.getSelectedToggle();
        
        if (structure == null || phase == null || selectedToggle == null) {
            currentSelection = null;
            return;
        }
        
        String cvmCode = (String) selectedToggle.getUserData();
        
        Map<String, Object> phaseData = structureData.get(structure).get("phases").get(phase);
        Map<String, Object> cvmData = ((Map<String, Map<String, Object>>) phaseData.get("cvmModels")).get(cvmCode);
        
        // Extract data with defaults
        String description = (String) phaseData.get("description");
        Boolean orderedObj = (Boolean) phaseData.get("ordered");
        boolean ordered = orderedObj != null ? orderedObj : false;
        String clusterFile = (String) cvmData.get("clusterFile");
        String symGroup = (String) cvmData.get("symGroup");
        String symMatFile = (String) cvmData.get("symMatFile");
        Integer maxClusterSize = (Integer) cvmData.get("maxClusterSize");
        List<Integer> supportedCounts = (List<Integer>) phaseData.get("supportedComponents");
        
        // Transform matrix and translation vector (optional)
        double[][] transformMatrix = null;
        double[] translationVector = null;
        
        if (cvmData.containsKey("transformMatrix")) {
            String matrixStr = (String) cvmData.get("transformMatrix");
            transformMatrix = parseTransformMatrix(matrixStr);
        }
        
        if (cvmData.containsKey("translationVector")) {
            String vecStr = (String) cvmData.get("translationVector");
            translationVector = parseTranslationVector(vecStr);
        }
        
        // Create StructureModelInfo
        currentSelection = new StructureModelInfo(
            structure, phase, cvmCode,
            description, ordered,
            clusterFile, symGroup, symMatFile,
            maxClusterSize != null ? maxClusterSize : 4,
            new HashSet<>(supportedCounts),
            transformMatrix, translationVector
        );
    }
    
    private double[][] parseTransformMatrix(String matrixStr) {
        // Parse "[[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]]"
        try {
            matrixStr = matrixStr.replace("[", "").replace("]", "").trim();
            String[] rows = matrixStr.split(",\\s*(?![^\\[]*\\])");
            double[][] matrix = new double[3][3];
            
            int idx = 0;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    String[] parts = matrixStr.split(",");
                    if (idx < parts.length) {
                        matrix[i][j] = Double.parseDouble(parts[idx++].trim());
                    }
                }
            }
            return matrix;
        } catch (Exception e) {
            return null;
        }
    }
    
    private double[] parseTranslationVector(String vecStr) {
        // Parse "[0.5, 0.5, 0.5]"
        try {
            vecStr = vecStr.replace("[", "").replace("]", "").trim();
            String[] parts = vecStr.split(",");
            double[] vec = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vec[i] = Double.parseDouble(parts[i].trim());
            }
            return vec;
        } catch (Exception e) {
            return null;
        }
    }
    
    private void updateDescription() {
        if (currentSelection == null) {
            descriptionLabel.setText("Select a structure, phase, and CVM model.");
            availabilityLabel.setText("");
            return;
        }
        
        descriptionLabel.setText(
            "Selected: " + currentSelection.getDisplayName() + "\n" +
            "Description: " + currentSelection.getDescription() + "\n" +
            "Type: " + (currentSelection.isOrdered() ? "Ordered" : "Disordered") + "\n" +
            "Max Cluster Size: " + currentSelection.getMaxClusterSize() + " atoms"
        );
        
        // Check if cluster file exists
        String clusterFile = currentSelection.getClusterFile();
        boolean clusterExists = checkResourceExists("/" + clusterFile);
        
        String symGroup = currentSelection.getSymGroup();
        boolean symExists = checkResourceExists("/symmetry/" + symGroup + ".txt");
        
        String availability = "Status: ";
        if (clusterExists && symExists) {
            availability += "✓ All required files available";
            availabilityLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else {
            List<String> missing = new ArrayList<>();
            if (!clusterExists) missing.add("cluster file");
            if (!symExists) missing.add("symmetry group");
            availability += "⚠ Missing: " + String.join(", ", missing);
            availabilityLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        }
        
        availabilityLabel.setText(availability);
    }
    
    private boolean checkResourceExists(String path) {
        return getClass().getResource(path) != null;
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Structure Model Loading Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Static convenience method to show dialog and get result
     */
    public static Optional<StructureModelInfo> showDialog(List<String> components) {
        StructureModelSelectionDialog dialog = new StructureModelSelectionDialog(components);
        return dialog.showAndWait();
    }
}
