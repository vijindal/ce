# CE Workbench - Project Status

**Last Updated:** February 27, 2026  
**Version:** 0.3.0  
**Compilation:** ✅ Successful  
**GUI Status:** ✅ Fully Functional

---

## Current Architecture

### Data Structure (UPDATED - Feb 2026)
Three-tier data organization:

```
app/src/main/resources/
├── cluster_data/                   # Runtime persistence (NEW)
│   └── Ti-Nb_BCC_A2_T/
│       └── cluster_result.json    # Cluster identification results
├── data/
│   ├── systems/                    # Element-specific CECs
│   │   ├── Ti-Nb/
│   │   │   └── cec.json
│   │   ├── Ti-V/
│   │   │   └── cec.json
│   │   └── Nb-Ti/
│   │       └── cec.json
│   └── models/                     # Shared model data (metadata only)
│       ├── BCC_A2_T/
│       │   └── model_data.json
│       └── FCC_L12_T/
│           └── model_data.json
├── cluster/                        # Cluster input files
└── symmetry/                       # Symmetry group files
```

**Rationale:** 
- **CECs** are element-pair specific (Ti-Nb ≠ Ti-V)
- **Model metadata** is shared across systems (Ti-Nb, Ti-V, Ti-Zr all use BCC_A2_T)
- **Cluster results** are system-specific runtime data (NEW: persisted for distribution)

### GUI Components
- **SystemRegistryPanel** - System management with guided text input (replaces periodic table)
  - Text fields: Elements (Ti-Nb), Structure/Phase (BCC_A2), Model (T)
  - Data availability checking (CEC + Model status)
  - Tree view showing CEC/Cluster/CF status per system
  - Single identification dialog with cached input (eliminates redundancy)
  - Pre-filled ordered cluster/symmetry fields
  - Comprehensive diagnostic logging
  
- **CalculationSetupPanel** - Configure and run calculations
- **BackgroundJobManager** - Async job execution
- **SystemDataLoader** - Load from separated data structure
- **ClusterDataCache** (NEW) - JSON persistence for cluster identification results

### Backend
- **SystemRegistry** - System registration and metadata management
- **SystemInfo** - Enhanced with model field and availability flags
- **CEWorkbenchCLI** - Command-line interface
- **ClusterIdentificationJob** - Saves cluster data before job completion
- **CFIdentificationJob** - Reuses cached input files

---

## Recent Changes (Feb 2026)

### ✅ Completed (Week of Feb 27)
1. **Cluster Data Persistence** - NEW
   - ClusterDataCache utility for JSON serialization
   - Results saved to `cluster_data/{systemId}/cluster_result.json`
   - Project-based storage (not user home directory) for distribution
   
2. **UX Improvements** - MAJOR
   - **Single identification dialog** - Eliminated duplicate file prompts
   - **Cached input pattern** - CF identification reuses cluster identification inputs
   - **Pre-filled fields** - Ordered cluster/symmetry auto-populated with resolved values
   - **Diagnostic logging** - Component-prefixed console output (`[ClusterJob]`, `[ClusterDataCache]`)
   
3. **Bug Fixes** - CRITICAL
   - Fixed system availability check (now checks actual `cluster_result.json`)
   - Fixed job timing issue (cluster data saved before job removed from queue)
   - Fixed dialog redundancy (single prompt for entire identification pipeline)

### ✅ Completed (Week of Feb 20-26)
4. **Window Sizing** - Responsive sizing (90% screen, centered)
5. **UI Redesign** - Replaced periodic table with guided text fields
6. **Data Separation** - Split CECs from model data for proper reuse
7. **SystemDataLoader** - Rewritten for new dual-source loading
8. **Nb-Ti System** - Added CEC data (4 values from phase diagram)
9. **BCC_A2_T Model** - Added shared model data (tcdis=5, tcf=15)

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

### Known Limitations
- Cluster data only available during same session as identification
  - Full Cluster objects are not serializable (complex Nd4j dependencies)
  - Workaround: cluster_result.json stores essential metadata
  - App restart requires re-running identification

---

## Next Steps

### High Priority
1. **✅ COMPLETED: Cluster Data Persistence**
   - ✅ Store cluster_result.json in project folder
   - ✅ Distribute example cluster data to users
   - ✅ Fix system availability checks
   - ✅ Eliminate duplicate identification dialogs

2. **Manual CEC Input Workflow**
   - Create dialog for entering CEC values manually
   - Save to systems/<Elements>/cec.json
   - Integrate with "Add System" flow

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

6. **Full Cluster Serialization** (if needed)
   - Serialize complete Cluster objects with Nd4j matrices
   - Enable cross-session cluster data loading

7. **Documentation**
   - User guide for adding new systems
   - Developer guide for data structure

### Low Priority
8. **Phase Diagram Plotting** (future)
9. **MCS Integration** (future)
10. **Multi-component Systems** (future - ternary, quaternary)

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
│   │   │   │   ├── SystemDataLoader.java       # Load CECs + model data
│   │   │   │   └── ClusterDataCache.java       # NEW: JSON persistence
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
│   ├── cluster_data/                            # NEW: Runtime persistence
│   │   └── {systemId}/cluster_result.json      # Cluster identification results
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
