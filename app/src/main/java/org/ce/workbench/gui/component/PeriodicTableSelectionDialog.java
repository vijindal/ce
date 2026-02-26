package org.ce.workbench.gui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dialog for selecting chemical elements from an interactive periodic table.
 * Allows single or multiple element selection for system creation.
 */
public class PeriodicTableSelectionDialog extends Dialog<List<String>> {
    
    private final Map<String, Element> elementMap = new LinkedHashMap<>();
    private final Map<String, Button> elementButtons = new HashMap<>();
    private final Set<String> selectedElements = new LinkedHashSet<>();
    private final Label statusLabel = new Label("No elements selected");
    private final FlowPane selectedPane = new FlowPane(5, 5);
    
    private static final int MAX_ROWS = 7;
    private static final int MAX_COLS = 18;
    
    public PeriodicTableSelectionDialog() {
        this(new LinkedHashSet<>());
    }
    
    public PeriodicTableSelectionDialog(Set<String> preselected) {
        setTitle("Select Chemical Components");
        setHeaderText("Click elements to select components for your system");
        
        // Load elements from resource file
        loadElements();
        
        // Pre-select elements if provided
        if (preselected != null) {
            selectedElements.addAll(preselected);
        }
        
        // Create UI
        VBox content = createContent();
        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(900);
        getDialogPane().setPrefHeight(500);
        
        // Add buttons
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Set result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new ArrayList<>(selectedElements);
            }
            return null;
        });
        
        // Update initial display
        updateSelectedDisplay();
    }
    
    private VBox createContent() {
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(15));
        
        // Periodic table grid
        GridPane periodicTable = createPeriodicTable();
        
        // Selected elements display
        VBox selectedBox = new VBox(8);
        Label selectedLabel = new Label("Selected Components:");
        selectedLabel.setStyle("-fx-font-weight: bold;");
        
        selectedPane.setPadding(new Insets(5));
        selectedPane.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; " +
                             "-fx-background-color: #f9f9f9; -fx-min-height: 40;");
        
        statusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #666666;");
        
        selectedBox.getChildren().addAll(selectedLabel, selectedPane, statusLabel);
        
        vbox.getChildren().addAll(periodicTable, selectedBox);
        return vbox;
    }
    
    private GridPane createPeriodicTable() {
        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-background-color: #f0f0f0;");
        
        // Create buttons for each element
        for (Element element : elementMap.values()) {
            Button btn = createElementButton(element);
            elementButtons.put(element.getSymbol(), btn);
            
            // Place in grid (convert to 0-indexed)
            int row = element.getRow() - 1;
            int col = element.getColumn() - 1;
            grid.add(btn, col, row);
        }
        
        return grid;
    }
    
    private Button createElementButton(Element element) {
        Button btn = new Button(element.getSymbol());
        btn.setMinSize(45, 45);
        btn.setMaxSize(45, 45);
        btn.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        
        // Set initial state
        updateButtonStyle(btn, element.getSymbol(), false);
        
        // Tooltip with element info
        Tooltip tooltip = new Tooltip(
            element.getName() + " (" + element.getSymbol() + ")\n" +
            "Atomic #: " + element.getAtomicNumber() + "\n" +
            "Mass: " + String.format("%.2f", element.getMass())
        );
        btn.setTooltip(tooltip);
        
        // Click handler
        btn.setOnAction(e -> toggleElement(element.getSymbol()));
        
        return btn;
    }
    
    private void toggleElement(String symbol) {
        if (selectedElements.contains(symbol)) {
            selectedElements.remove(symbol);
        } else {
            selectedElements.add(symbol);
        }
        
        updateButtonStyle(elementButtons.get(symbol), symbol, selectedElements.contains(symbol));
        updateSelectedDisplay();
    }
    
    private void updateButtonStyle(Button btn, String symbol, boolean selected) {
        if (selected) {
            btn.setStyle("-fx-font-size: 12; -fx-font-weight: bold; " +
                        "-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                        "-fx-border-color: #2E7D32; -fx-border-width: 2;");
        } else {
            // Color by element type (simplified)
            String color = getElementColor(symbol);
            btn.setStyle("-fx-font-size: 12; -fx-font-weight: bold; " +
                        "-fx-background-color: " + color + "; " +
                        "-fx-border-color: #999999; -fx-border-width: 1;");
        }
    }
    
    private String getElementColor(String symbol) {
        // Simplified color coding
        Element elem = elementMap.get(symbol);
        if (elem == null) return "#e0e0e0";
        
        int col = elem.getColumn();
        
        // Alkali metals (column 1)
        if (col == 1 && elem.getRow() > 1) return "#ff6b6b";
        
        // Alkaline earth (column 2)
        if (col == 2 && elem.getRow() > 1) return "#ffa07a";
        
        // Transition metals (columns 3-12)
        if (col >= 3 && col <= 12) return "#ffd993";
        
        // Post-transition metals
        if ((col == 13 || col == 14) && elem.getRow() >= 4) return "#c7c7c7";
        
        // Metalloids
        if (symbol.equals("B") || symbol.equals("Si") || symbol.equals("Ge") || 
            symbol.equals("As") || symbol.equals("Sb") || symbol.equals("Te")) {
            return "#cccc99";
        }
        
        // Nonmetals
        if (col >= 14 && col <= 17) return "#a0d6b4";
        
        // Noble gases
        if (col == 18) return "#c9adff";
        
        return "#e0e0e0";
    }
    
    private void updateSelectedDisplay() {
        selectedPane.getChildren().clear();
        
        if (selectedElements.isEmpty()) {
            statusLabel.setText("No elements selected");
        } else {
            statusLabel.setText("Number of components: " + selectedElements.size());
            
            for (String symbol : selectedElements) {
                Label lbl = new Label(symbol);
                lbl.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                           "-fx-padding: 5 10 5 10; -fx-font-weight: bold; " +
                           "-fx-background-radius: 3;");
                
                // Click to remove
                lbl.setOnMouseClicked(e -> {
                    toggleElement(symbol);
                });
                lbl.setCursor(javafx.scene.Cursor.HAND);
                
                selectedPane.getChildren().add(lbl);
            }
        }
    }
    
    private void loadElements() {
        // Simple YAML-like parser for elements.yaml
        try (InputStream is = getClass().getResourceAsStream("/data/elements.yaml")) {
            if (is == null) {
                System.err.println("Could not find elements.yaml");
                loadDefaultElements();
                return;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            String symbol = null;
            String name = null;
            int atomicNumber = 0;
            double mass = 0.0;
            int row = 0;
            int column = 0;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                if (line.startsWith("- symbol:")) {
                    // Save previous element if exists
                    if (symbol != null) {
                        elementMap.put(symbol, new Element(symbol, name, atomicNumber, mass, row, column));
                    }
                    symbol = line.substring(line.indexOf(':') + 1).trim();
                } else if (line.startsWith("name:")) {
                    name = line.substring(line.indexOf(':') + 1).trim();
                } else if (line.startsWith("atomicNumber:")) {
                    atomicNumber = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                } else if (line.startsWith("mass:")) {
                    mass = Double.parseDouble(line.substring(line.indexOf(':') + 1).trim());
                } else if (line.startsWith("row:")) {
                    row = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                } else if (line.startsWith("column:")) {
                    column = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
            }
            
            // Save last element
            if (symbol != null) {
                elementMap.put(symbol, new Element(symbol, name, atomicNumber, mass, row, column));
            }
            
        } catch (Exception e) {
            System.err.println("Error loading elements: " + e.getMessage());
            loadDefaultElements();
        }
    }
    
    private void loadDefaultElements() {
        // Fallback: load some common elements for testing
        elementMap.put("H", new Element("H", "Hydrogen", 1, 1.008, 1, 1));
        elementMap.put("He", new Element("He", "Helium", 2, 4.003, 1, 18));
        elementMap.put("Li", new Element("Li", "Lithium", 3, 6.941, 2, 1));
        elementMap.put("C", new Element("C", "Carbon", 6, 12.01, 2, 14));
        elementMap.put("N", new Element("N", "Nitrogen", 7, 14.01, 2, 15));
        elementMap.put("O", new Element("O", "Oxygen", 8, 16.00, 2, 16));
        elementMap.put("Fe", new Element("Fe", "Iron", 26, 55.85, 4, 8));
        elementMap.put("Ni", new Element("Ni", "Nickel", 28, 58.69, 4, 10));
        elementMap.put("Cu", new Element("Cu", "Copper", 29, 63.55, 4, 11));
        elementMap.put("Al", new Element("Al", "Aluminum", 13, 26.98, 3, 13));
    }
    
    public Set<String> getSelectedElements() {
        return new LinkedHashSet<>(selectedElements);
    }
    
    public List<String> getComponentList() {
        return new ArrayList<>(selectedElements);
    }
    
    /**
     * Static convenience method to show dialog and get result.
     */
    public static Optional<List<String>> showDialog() {
        return showDialog(null);
    }
    
    public static Optional<List<String>> showDialog(Set<String> preselected) {
        PeriodicTableSelectionDialog dialog = new PeriodicTableSelectionDialog(preselected);
        return dialog.showAndWait();
    }
}
