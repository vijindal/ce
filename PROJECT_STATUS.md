# CE Workbench - Project Status

**Last Updated:** March 1, 2026  
**Version:** 0.3.3  
**Compilation:** ✅ Successful  
**GUI Status:** ✅ Fully Functional  
**Binary CVM Solver:** ✅ Phase 5 Complete (K=2) — 13/13 Tests Pass  
**Ternary CVM Solver:** ⏳ Phase 5 In Progress (K≥3) — 8/11 Tests Pass

---

## Current Architecture

### Data Structure (UPDATED - Feb 2026)
Three-tier data organization:

```
data/cluster_cache/                 # Runtime persistence (auto-generated)
│   └── BCC_A2_T_bin/
│       └── all_cluster_data.json   # Full pipeline data (Stages 1-3)
app/src/main/resources/
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
- **AllClusterDataCache** - JSON persistence for all cluster data (Stages 1-3)

### Backend
- **SystemRegistry** - System registration and metadata management
- **SystemInfo** - Enhanced with model field and availability flags
- **CEWorkbenchCLI** - Command-line interface
- **ClusterIdentificationJob** - Saves cluster data before job completion
- **CFIdentificationJob** - Reuses cached input files

---

## Recent Changes (Mar 2026)

### ✅ Completed (Mar 1, 2026)
**Phase 5: Multi-Component CVM Solver Generalization (K > 2)**

**Binary System (K=2):** All 13 CVM solver tests **PASSING** ✅
- Newton-Raphson solver converges in <10 iterations
- Converges to ||Gcu|| < 1e-10 tolerance (excellent)
- Point correlation function ordering fixed using `cfBasisIndices`
- Random-state cluster variable (CV) verification working correctly
- Entropy at random state validates to ln(2) formula
- Hessian computation well-conditioned and stable

**Multi-Component API Generalization:**
- Changed signature from `solve(double composition, ...)` to `solve(double[] moleFractions, int K, ...)`
- Enables K-component systems (K ≥ 2)
- Backward-compatible binary wrapper: still supports old API
- `cfBasisIndices` propagated through entire call chain for proper CF placement

**Ternary System (K=3):** 8/11 tests passing, convergence issue identified
- Root cause: **Hessian ill-conditioning at random state due to zero cluster variables**
- For equimolar ternary with basis {-1, 0, 1}: σ¹ = 0
- Many multi-site CFs = 0 because they are products involving σ¹
- Zero CVs trigger smooth entropy extension (for CV < 1e-6)
- Smooth extension sets invEff = 1/EPS = 1e6 for numerical stability
- This creates massive values in Hessian → ill-conditioned or singular
- NR solver oscillates at ~1e-8 gradient norm, never reaches 1e-10 tolerance
- Step sizes collapse to ~1e-15 (numerical precision limit reached)

**Test Status:**
- Binary CVMSolverTest: **13/13 PASS** ✅
- Ternary CVMTernaryTest: **8/11 PASS** (3 fail at convergence check)

**Next Phase:** Fix ternary Hessian computation
- Option 1: CV regularization (add small offset to CV to keep > threshold)
- Option 2: Revised entropy formulation for K≥3
- Option 3: Alternative solver approach (gradient descent, trust region, etc.)

### Previous Session (Feb 28, 2026)

### ✅ Completed (Feb 28 - Part 2)
2. **MCS Performance Optimization** - UI Slowdown Fixed
   - Root cause: Chart updated every sweep → JavaFX redraw queue buildup
   - Solution: Sample chart updates (every 10 sweeps) instead of every sweep
   - Solution: Sample text output (every 50 sweeps) instead of every 100 updates
   - Solution: Keep only 300 chart points (vs 10k) with continuous pruning
   - Result: **~80 ms/sweep consistently** (formerly 20-25+ sec/sweep at 500+ sweeps)
   - Verification: 1000-sweep run now **85.5 seconds** (was 6+ hours on slowdown path)
   - Performance gain: **250x faster** for long runs, **linear timing** throughout
   - Files modified:
     - [ResultsPanel.java](app/src/main/java/org/ce/workbench/gui/view/ResultsPanel.java) — Sampling logic
     - [EnergyConvergenceChart.java](app/src/main/java/org/ce/workbench/gui/component/EnergyConvergenceChart.java) — Aggressive pruning

### ✅ Completed (Feb 28 - Part 1)
3. **MCS Energy Tracking Optimization** - CRITICAL PERFORMANCE FIX
   - Implemented true ΔE accumulation: energy updates only on accepted moves
   - Modified `ExchangeStep.attempt()` to return `double ΔE` instead of `boolean`
   - Modified `FlipStep.attempt()` to return `double ΔE` instead of `boolean`
   - MCEngine accumulates per-step: `currentEnergy += stepDeltaE` (0.0 if rejected)
   - Only non-zero ΔE values added to rolling window (accepted moves only)
   - Performance improvement: **~1000x faster** than recalculation per step
   - Verification method: Periodic full-energy recalculation every 10 sweeps
   - Test results: ✓ MATCH with zero numerical drift (sweeps 10, 20)
   - Threading fix: Wrapped `ResultsPanel.initializeMCS()` in `Platform.runLater()` to fix `ConcurrentModificationException`
   - Code cleanup: Removed diagnostic logging and verification blocks (production ready)
   - Build status: ✅ Clean compilation, no errors or warnings

### ✅ Completed (Week of Feb 27)
2. **CF Normalization Fix** - CRITICAL BUG FIX ⚠️
   - Fixed incorrect correlation function normalization formula in MCSampler
   - OLD (WRONG): `CF = Σ(Φ) / (N × orbitSize)` caused CFs to scale incorrectly
   - NEW (CORRECT): `CF = Σ(Φ) / embedCount` - average cluster product per embedding
   - Fixed empty cluster generation (Type 5 was being skipped)
   - All cluster types now correctly produce CF=1.0 for all-B configuration
   - See [CF_NORMALIZATION_FIX_SUMMARY.md](CF_NORMALIZATION_FIX_SUMMARY.md) for detailed analysis
   
3. **MCS UI Enhancements**
   - Added Supercell Size (L) parameter to CalculationSetupPanel
   - Standardized gas constant to R=8.314 J/(mol·K) for all calculations
   - Removed legacy R=1.0 convention

4. **Cluster Data Persistence** - NEW
   - AllClusterDataCache for unified JSON serialization (Stages 1-3)
   - Results saved to `data/cluster_cache/{clusterKey}/all_cluster_data.json`
   - Project-based storage (not user home directory) for distribution
   
5. **UX Improvements** - MAJOR
   - **Single identification dialog** - Eliminated duplicate file prompts
   - **Cached input pattern** - CF identification reuses cluster identification inputs
   - **Pre-filled fields** - Ordered cluster/symmetry auto-populated with resolved values
   - **Diagnostic logging** - Component-prefixed console output (`[ClusterJob]`, `[AllClusterDataCache]`)
   
6. **Bug Fixes** - CRITICAL
   - Fixed system availability check (now checks actual `cluster_result.json`)
   - Fixed job timing issue (cluster data saved before job removed from queue)
   - Fixed dialog redundancy (single prompt for entire identification pipeline)

### ✅ Completed (Week of Feb 20-26)
7. **Window Sizing** - Responsive sizing (90% screen, centered)
8. **UI Redesign** - Replaced periodic table with guided text fields
9. **Data Separation** - Split CECs from model data for proper reuse
10. **SystemDataLoader** - Rewritten for new dual-source loading
11. **Nb-Ti System** - Added CEC data (4 values from phase diagram)
12. **BCC_A2_T Model** - Added shared model data (tcdis=5, tcf=15)

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

### CVM Ternary Convergence Issue (In Progress)
- NR solver oscillates for K≥3 systems at random state
- Root cause: Hessian ill-conditioned due to zero cluster variables
- Affects 3 tests: entropy at equimolar, all CVs positive, entropy approaches ln(3)
- Binary (K=2) working perfectly — not a systematic CVM issue
- Next: Implement CV regularization or entropy reformulation for K≥3

### Known Limitations
- Cluster data only available during same session as identification
  - Full Cluster objects are not serializable (complex Nd4j dependencies)
  - Workaround: all_cluster_data.json stores essential metadata
  - App restart requires re-running identification

---

## Next Steps

### High Priority (Phase 5 Completion)
1. **Fix Ternary CVM Convergence** (In Progress)
   - Implement CV regularization or entropy reformulation
   - Target: 11/11 ternary tests passing
   - Then: Extend to K=4, K=5 systems

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
10. **Quaternary and Higher-Order Systems** (Phase 6+)

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

### Run Tests
```bash
# Binary CVM tests only
.\gradlew test --tests CVMSolverTest

# Ternary CVM tests
.\gradlew test --tests CVMTernaryTest
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
│   ├── cvm/                                     # CVM Solver (Phase 5)
│   │   ├── CVMFreeEnergy.java                  # Free-energy evaluation + gradient/Hessian
│   │   ├── NewtonRaphsonSolver.java            # NR solver with diagnostics
│   │   ├── ClusterVariableEvaluator.java       # CV computation with CF handling
│   │   ├── CVMSolverResult.java                # Result wrapper
│   │   └── ...
│   └── core/                                    # Core algorithms
│       ├── CVMConfiguration.java
│       ├── CVMPipeline.java
│       └── ...
├── app/src/test/java/org/ce/cvm/
│   ├── CVMSolverTest.java                       # Binary tests (13/13 PASS)
│   └── CVMTernaryTest.java                      # Ternary tests (8/11 PASS)
├── data/cluster_cache/                          # Runtime persistence
│   └── {clusterKey}/
│       └── all_cluster_data.json                # Full pipeline data (Stages 1-3)
├── app/src/main/resources/
│   ├── data/
│   │   ├── systems/                             # Element-specific CECs
│   │   │   ├── Ti-Nb/cec.json
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
**Built With:** Java 25, JavaFX 22.0.2, Gradle 9.3.1  
**License:** See LICENSE file
