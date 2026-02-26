# System Creation Dialog - Implementation Plan

**Date:** February 26, 2026  
**Status:** Design Phase  
**Priority:** CRITICAL BLOCKER FIX

## Overview

Redesign the "Add New System" dialog to be more realistic and functional:
- **Phase 1:** Component Selection (periodic table UI)
- **Phase 2:** Structure/Model Selection (BCC_A2, FCC_L12, etc.)
- **Phase 3:** CEC Database Integration
- **Phase 4:** Auto-fetch Cluster/CF Data

---

## Phase 1: Component Selection UI

### Requirements
1. **Periodic Table Widget**
   - Display interactive periodic table
   - Click to select/deselect elements
   - Visual indication of selected elements
   - Support 1-component to N-component systems

2. **Component Count**
   - Auto-determine from selected elements
   - Display selected elements in order
   - Allow reordering (for binary systems A-B vs B-A matters)

### Implementation Strategy

**New Class:** `PeriodicTableSelectionDialog.java`
```
Location: org.ce.workbench.gui.component

Methods:
- PeriodicTableSelectionDialog(Set<String> selectedElements)
- showDialog(): Optional<Set<String>>
- getSelectedElements(): Set<String>
- getComponentList(): List<String>  // ordered list
```

**UI Layout:**
```
┌─────────────────────────────────────────────────┐
│  Select Chemical Components                      │
├─────────────────────────────────────────────────┤
│                                                   │
│  ┌─ Periodic Table ─────────────────────────┐  │
│  │  H                                 He     │  │
│  │  Li Be B  C  N  O  F  Ne              │  │
│  │  Na Mg Al Si P  S  Cl Ar             │  │
│  │  ... (interactive grid)                  │  │
│  └─────────────────────────────────────────┘  │
│                                                   │
│  Selected Components: [Fe] [Ni] [Al]             │
│  Number of Components: 3                         │
│                                                   │
│  [OK]  [Cancel]                                 │
└─────────────────────────────────────────────────┘
```

**Data Structure:**
```java
// Elements database (in resource file: elements.yaml)
Element:
  - symbol: Fe
    name: Iron
    atomicNumber: 26
    mass: 55.845
    column: 8
    row: 4
  - symbol: Ni
    name: Nickel
    atomicNumber: 28
    mass: 58.693
    column: 10
    row: 4
  ...
```

**Key Features:**
- Hover highlights element name and properties
- Click to toggle selection
- Selected elements show checkmark
- Ordered selection (maintains order added)
- Support drag-to-reorder for binary systems

---

## Phase 2: Structure/Model Selection

### Requirements
1. **Structure-Phase Combinations**
   - BCC (A2, B2, D03, etc.)
   - FCC (A1, L12, L10, etc.)
   - HCP (A3, etc.)
   - Other crystal systems

2. **CVM Approximation Levels**
   - Tetrahedron (T)
   - Octahedron (O)
   - Tetrahedron + Octahedron (T+O)

3. **Model Availability**
   - Not all structure-phase-model combos exist
   - Show only available combinations based on database

### Implementation Strategy

**New Class:** `StructureModelSelectionPanel.java`
```
Location: org.ce.workbench.gui.component

Methods:
- StructureModelSelectionPanel(List<String> components)
- getSelectedStructure(): String      // "BCC"
- getSelectedPhase(): String          // "A2"
- getSelectedCvmModel(): String       // "T" for Tetrahedron
- getModelInfo(): StructureModelInfo  // metadata
```

**Model Info Class:**
```java
public class StructureModelInfo {
    String structure;           // "BCC", "FCC", etc.
    String phase;               // "A2", "B2", etc.
    String cvmModel;            // "T", "O", "T+O"
    String clusterFile;         // "cluster/A2-T.txt"
    String disSymmetryGroup;    // "A2-SG"
    String orderedSymmetryGroup;// "B2-SG" (for ordered)
    boolean ordered;            // is this an ordered phase?
    double[][] transformMatrix; // lattice transformation
    double[] translationVec;    // translation vector
    int maxClusterSize;         // for CVM approximation
    Set<Integer> supportedComponents; // e.g., {2, 3}
}
```

**UI Layout:**
```
┌──────────────────────────────────┐
│  Structure & CVM Model Selection  │
├──────────────────────────────────┤
│                                   │
│ Crystal Structure:                │
│  [▼] BCC                         │
│                                   │
│ Phase:                            │
│  [▼] A2 (disordered reference)   │
│     B2 (ordered)                  │
│     D03 (super-ordered)           │
│                                   │
│ CVM Approximation:                │
│  [●] Tetrahedron (T)             │
│  [ ] Octahedron (O)              │
│  [ ] T + O (combined)            │
│                                   │
│ Available Models for Fe-Ni binary:│
│  ✓ BCC_A2_T                      │
│  ✓ BCC_A2_O                      │
│  ✗ FCC_A1_T (not in database)    │
│                                   │
│ [OK]  [Cancel]                   │
└──────────────────────────────────┘
```

**Data Source:**
```yaml
# File: structure_models.yaml
StructureModels:
  BCC:
    A2:
      description: "BCC disordered (reference)"
      ordered: false
      cvm_models:
        T:
          clusterFile: "cluster/A2-T.txt"
          symGroup: "A2-SG"
          maxClusterSize: 4
        O:
          clusterFile: "cluster/A2-O.txt"
          symGroup: "A2-SG"
          maxClusterSize: 6
    B2:
      description: "BCC ordered CsCl structure"
      ordered: true
      cvm_models:
        T:
          clusterFile: "cluster/B2-T.txt"
          symGroup: "B2-SG"
          transformMatrix: [[1,0,0],[0,1,0],[0,0,1]]
          translationVector: [0.5, 0.5, 0.5]
  FCC:
    A1:
      description: "FCC disordered"
      ordered: false
      cvm_models:
        T:
          clusterFile: "cluster/A1-T.txt"
          symGroup: "A1-SG"
    L12:
      description: "FCC ordered Cu3Au structure"
      ordered: true
      cvm_models:
        T:
          clusterFile: "cluster/L12-T.txt"
          symGroup: "L12-SG"
```

---

## Phase 3: CEC Database Integration

### Requirements
1. **CEC Database Structure**
   - Key: (component_set, structure, phase, cvm_model)
   - Value: ECI coefficients for each cluster type

2. **Database Access**
   - Load from files (JSON/YAML)
   - Cache in memory
   - Lazy-load on demand

### Implementation Strategy

**New Class:** `CECDatabase.java`
```
Location: org.ce.workbench.backend.database

Methods:
- CECDatabase()  // loads all CEC files
- getCEC(List<String> components, String structure, 
        String phase, String cvmModel): Optional<EciVector>
- getAvailableModels(List<String> components): Set<String>
- hasData(String key): boolean
```

**Data Structure:**
```yaml
# File: cec_database/Fe-Ni_BCC_A2_T.yaml
System: "Fe-Ni BCC A2"
CvmModel: "Tetrahedron"
NumComponents: 2
NumClusters: 5

# ECI values in eV
ECI:
  - type: "point"      # cluster type
    value: 0.0         # by definition, point = 0
  - type: "pair"
    value: -0.125
  - type: "triangle"
    value: 0.032
  - type: "tetrahedron"
    value: 0.018

# Optional: reference data
Reference:
  paper: "Smith et al. (2020)"
  doi: "10.1234/example"
  temperature_range: "0-1500 K"
```

**Loading Strategy:**
```java
// At startup in CEWorkbenchApplication.start()
CECDatabase cecDatabase = new CECDatabase("resources/cec_database");

// Later, in SystemCreation:
Optional<EciVector> eci = cecDatabase.getCEC(
    Arrays.asList("Fe", "Ni"),
    "BCC",
    "A2",
    "T"
);
if (eci.isPresent()) {
    systemInfo.setEciVector(eci.get());
}
```

---

## Phase 4: Auto-fetch Cluster/CF Data

### Requirements
1. **Cluster File Management**
   - Load cluster coordinates from files
   - Cache loaded data
   - Handle missing files gracefully

2. **Symmetry Group Data**
   - Load symmetry operations
   - Cache per structure-phase combination

3. **SystemInfo Enrichment**
   - Automatically populate based on selections:
     * clusterFilePath → correct cluster file
     * symmetryGroupName → correct symmetry group
     * transformationMatrix
     * translationVector
     * cfsComputed flag (set to false)

### Implementation Strategy

**Enhanced SystemRegistry:**
```java
public SystemInfo createSystemFromSelections(
    List<String> components,
    String structure,
    String phase,
    String cvmModel
) throws IOException {
    StructureModelInfo modelInfo = structureModels.get(structure, phase, cvmModel);
    if (modelInfo == null) {
        throw new IllegalArgumentException("Model not found");
    }
    
    SystemInfo system = new SystemInfo(
        generateId(components, structure, phase),
        generateName(components, structure, phase),
        structure,
        phase,
        components.toArray(new String[0])
    );
    
    // Auto-fetch cluster/CF data
    system.setClusterFilePath(modelInfo.clusterFile);
    system.setSymmetryGroupName(modelInfo.disSymmetryGroup);
    system.setTransformationMatrix(modelInfo.transformMatrix);
    system.setTranslationVector(modelInfo.translationVec);
    
    // Load and cache cluster data
    List<Cluster> clusters = InputLoader.parseClusterFile(
        modelInfo.clusterFile
    );
    
    return system;
}

private String generateId(List<String> components, String struct, String phase) {
    // "Fe-Ni-BCC-A2" or similar
    String elemStr = String.join("-", components);
    return elemStr + "-" + struct + "-" + phase;
}

private String generateName(List<String> components, String struct, String phase) {
    // "Fe-Ni (BCC A2)" or similar
    String elemStr = String.join("-", components);
    return elemStr + " (" + struct + " " + phase + ")";
}
```

---

## Revised Dialog Architecture

### New Flow:
```
User clicks "Add System"
    ↓
┌─────────────────────────────────────────┐
│  Step 1: Component Selection             │
│  (Periodic Table)                        │
│  ✓ Fe, Ni selected                      │
│  Components: 2                           │
└─────────────────────────────────────────┘
    ↓ [Next]
┌─────────────────────────────────────────┐
│  Step 2: Structure & Model Selection     │
│  ✓ BCC_A2_T selected                    │
│  ✓ CEC data available: Yes               │
└─────────────────────────────────────────┘
    ↓ [Finish]
┌─────────────────────────────────────────┐
│  System Created ✓                        │
│  - ID: Fe-Ni-BCC-A2                     │
│  - Components: Fe, Ni                    │
│  - Structure: BCC A2                     │
│  - CVM Model: Tetrahedron                │
│  - ECI loaded: ✓                         │
│  - Clusters loaded: ✓ (5 types)         │
└─────────────────────────────────────────┘
    ↓
Tree updated, ready for calculations
```

### Alternative: Single Step Dialog
If two steps is too verbose:
```
┌─────────────────────────────────────────┐
│  Add New System                          │
├─────────────────────────────────────────┤
│                                          │
│  LEFT SIDE: Periodic Table               │
│  (Component Selection)                   │
│                                          │
│  RIGHT SIDE:                             │
│  ┌─ Structure ─────────────────────┐   │
│  │ ☐ BCC (A2 ✓, B2 ✓, D03 ✓)      │   │
│  │ ☐ FCC (A1 ✓, L12 ✓)            │   │
│  │ ☐ HCP (A3, ...)                 │   │
│  └─────────────────────────────────┘   │
│                                          │
│  ┌─ CVM Model ─────────────────────┐   │
│  │ ●  T (Tetrahedron)              │   │
│  │ ○  O (Octahedron)               │   │
│  │ ○  T+O (Combined)               │   │
│  └─────────────────────────────────┘   │
│                                          │
│  System Info (auto-generated):           │
│    ID: Fe-Ni-BCC-A2                     │
│    ECI Available: ✓                      │
│                                          │
│  [Create System]  [Cancel]              │
└─────────────────────────────────────────┘
```

---

## Implementation Phases & Timeline

### Phase 1A: Periodic Table Component (1-2 hours) ✅ COMPLETE
- [x] Create `PeriodicTableSelectionDialog.java`
- [x] Load element data from resource file
- [x] Implement grid UI with click-to-select
- [x] Display selected elements list
- [x] Test periodic table interaction

### Phase 1B: Structure/Model Selection (1-2 hours) ✅ COMPLETE
- [x] Load structure_models.yaml
- [x] Create `StructureModelSelectionDialog.java`
- [x] Implement filtering (show available models for selected components)
- [x] Display model details and metadata
- [x] Test all combination selections

### Phase 2: CEC Database Integration (1 hour)
- [ ] Create `CECDatabase.java`
- [ ] Load sample CEC data files
- [ ] Implement caching mechanism
- [ ] Wire into system creation
- [ ] Test CEC fetching

### Phase 3: System Info Enrichment (30 min)
- [ ] Enhance `SystemRegistry.createSystemFromSelections()`
- [ ] Auto-load cluster files
- [ ] Auto-load symmetry groups
- [ ] Set transformation matrices
- [ ] Test end-to-end creation flow

### Phase 4: UI Integration (30 min)
- [ ] Integrate into `SystemRegistryPanel`
- [ ] Replace single dialog with new flow
- [ ] Update "Add System" button
- [ ] Test dialog launch and workflow

**Total Estimated Time: 4-5 hours**

---

## File Structure to Create

```
app/src/main/java/org/ce/workbench/
├── gui/
│   └── component/
│       ├── PeriodicTableSelectionDialog.java   (NEW)
│       ├── StructureModelSelectionPanel.java   (NEW)
│       └── StructureModelInfo.java             (NEW)
└── backend/
    └── database/
        └── CECDatabase.java                     (NEW)

app/src/main/resources/
├── data/
│   ├── elements.yaml                            (NEW)
│   ├── structure_models.yaml                    (NEW)
│   └── cec_database/
│       ├── Fe-Ni_BCC_A2_T.yaml                 (NEW)
│       ├── Fe-Ni_BCC_A2_O.yaml                 (NEW)
│       └── ...                                  (NEW)
```

---

## Questions for Refinement

1. **Default CEC Values?** Should we create mock/default CEC values for testing if real database is unavailable?

2. **Component Ordering?** For binary Fe-Ni, does order matter (Fe-Ni vs Ni-Fe)?

3. **Multi-Structure Binding?** Can user select multiple structure-model combos for the same component set in one dialog, or one-at-a-time?

4. **Database Format?** Prefer YAML (human-readable), JSON (standard), or embedded data classes?

5. **CEC Validation?** Should we validate CEC data completeness before allowing system creation?

---

## Success Criteria

✅ User can select 2+ elements via periodic table
✅ UI shows available structure models for selected elements
✅ CEC data auto-fetches for selected combination
✅ SystemInfo created with all metadata populated
✅ Dialog provides clear feedback on data status
✅ Handle gracefully if CEC data unavailable
✅ Build remains clean (0 compile errors)

