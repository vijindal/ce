# CE Workbench - Project Status

**Last Updated:** February 26, 2026  
**Version:** 0.2.0  
**Compilation:** ✅ Successful  
**GUI Status:** ✅ Functional

---

## Current Architecture

### Data Structure (NEW - Feb 2026)
Separated element-specific data from shared model data:

```
app/src/main/resources/data/
├── systems/
│   ├── Ti-Nb/
│   │   └── cec.json              # Element-specific CECs
│   ├── Ti-V/
│   │   └── cec.json
│   └── Nb-Ti/
│       └── cec.json
└── models/
    ├── BCC_A2_T/
    │   └── model_data.json       # Shared cluster/CF metadata
    └── FCC_L12_T/
        └── model_data.json
```

**Rationale:** CECs are element-pair specific (Ti-Nb ≠ Ti-V), but multiple alloy systems can share the same structure/phase/model data (Ti-Nb, Ti-V, Ti-Zr all use BCC_A2_T).

### GUI Components
- **SystemRegistryPanel** - System management with guided text input (replaces periodic table)
  - Text fields: Elements (Ti-Nb), Structure/Phase (BCC_A2), Model (T)
  - Data availability checking (CEC + Model status)
  - Tree view showing CEC/Cluster/CF status per system
  
- **CalculationSetupPanel** - Configure and run calculations
- **BackgroundJobManager** - Async job execution
- **SystemDataLoader** - Load from separated data structure

### Backend
- **SystemRegistry** - System registration and metadata management
- **SystemInfo** - Enhanced with model field and availability flags
- **CEWorkbenchCLI** - Command-line interface

---

## Recent Changes (Feb 2026)

### ✅ Completed
1. **Window Sizing** - Responsive sizing (90% screen, centered)
2. **UI Redesign** - Replaced periodic table with guided text fields
3. **Data Separation** - Split CECs from model data for proper reuse
4. **SystemDataLoader** - Rewritten for new dual-source loading
5. **Nb-Ti System** - Added CEC data (4 values from phase diagram)
6. **BCC_A2_T Model** - Added shared model data (tcdis=5, tcf=15)

### 🔄 In Progress
- Testing complete workflow with new data structure
- Creating additional alloy system examples (Ti-V, Ti-Zr)

---

## Known Issues

### Build Warnings
- Test compilation fails (CVMConfiguration references in tests need updating)
  - **Workaround:** Use `.\gradlew.bat run --no-configuration-cache` (skips tests)
  - **Fix Required:** Update test classes to match new architecture

### JavaFX Warnings
- JDK 25 restricted method warnings (JavaFX 20.0.1)
  - Expected behavior, does not affect functionality
  - Will be resolved when JavaFX updates for JDK 25

---

## Next Steps

### High Priority
1. **Manual CEC Input Workflow**
   - Create dialog for entering CEC values manually
   - Save to systems/<Elements>/cec.json
   - Integrate with "Add System" flow

2. **Cluster/CF Calculation Trigger**
   - Implement "Calculate Clusters" button
   - Run cluster identification via background job
   - Save results to models/<Model>/model_data.json

3. **Calculation Panel Gating**
   - Disable calculation panel if system data incomplete
   - Show status: "Missing CECs" or "Missing Clusters/CFs"

### Medium Priority
4. **Additional Test Systems**
   - Add Ti-V, Ti-Zr, Fe-Ni CEC data
   - Demonstrate model reuse (same BCC_A2_T for all)

5. **Import/Export**
   - Import CEC data from CSV/JSON
   - Export system configurations

6. **Documentation**
   - User guide for adding new systems
   - Developer guide for data structure

### Low Priority
7. **Phase Diagram Plotting** (future)
8. **MCS Integration** (future)
9. **Multi-component Systems** (future - ternary, quaternary)

---

## How to Run

### GUI Application
```bash
.\gradlew.bat run --no-configuration-cache
```

### CLI (System Creation)
```bash
.\gradlew.bat run --args='--cli'
```

### Build Only
```bash
.\gradlew.bat compileJava --no-configuration-cache
```

---

## Project Structure

```
ce/
├── app/src/main/java/org/ce/
│   ├── workbench/
│   │   ├── gui/
│   │   │   ├── CEWorkbenchApplication.java    # JavaFX entry point
│   │   │   ├── model/
│   │   │   │   └── SystemInfo.java             # System metadata
│   │   │   └── view/
│   │   │       ├── SystemRegistryPanel.java    # Left panel (system management)
│   │   │       └── CalculationSetupPanel.java  # Right panel (calculations)
│   │   ├── backend/
│   │   │   ├── SystemRegistry.java             # Central system registry
│   │   │   ├── BackgroundJobManager.java       # Job execution
│   │   │   ├── data/
│   │   │   │   └── SystemDataLoader.java       # Load CECs + model data
│   │   │   └── jobs/
│   │   │       ├── ClusterIdentificationJob.java
│   │   │       └── CFIdentificationJob.java
│   │   └── cli/
│   │       └── CEWorkbenchCLI.java             # Command-line interface
│   └── core/                                    # Core algorithms
│       ├── CVMConfiguration.java
│       ├── CVMPipeline.java
│       └── ...
├── app/src/main/resources/
│   └── data/
│       ├── systems/                             # Element-specific CECs
│       │   ├── Ti-Nb/cec.json
│       │   └── ...
│       ├── models/                              # Shared model data
│       │   ├── BCC_A2_T/model_data.json
│       │   └── ...
│       ├── cluster/                             # Cluster input files
│       └── symmetry/                            # Symmetry group files
└── docs/
    └── extracted-mathematica-functions.md       # Algorithm reference
```

---

## Contact & References

**Repository:** vijindal/ce  
**Built With:** Java 25, JavaFX 20.0.1, Gradle 9.3.1  
**License:** See LICENSE file
