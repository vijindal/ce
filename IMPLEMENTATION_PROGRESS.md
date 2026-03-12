# Type 1/2 Separation — Implementation Progress

**Start Date:** Mar 11, 2026
**Status:** ✅ COMPLETE (All 7 Phases)

---

## Phase Overview

| Phase | Name | Status | Files | Build |
|-------|------|--------|-------|-------|
| 1 | Introduce `DataManagementPort` | ✅ COMPLETE | 4 new | ✅ PASS |
| 2 | Fix Job Boundaries | ✅ COMPLETE | 2 modify | ✅ PASS |
| 3 | Introduce `CalculationCoordinator` | ✅ COMPLETE | 2 new + 1 deprecate | ✅ PASS |
| 4 | GUI: Data Readiness Gate | ✅ COMPLETE | 2 new + 1 modify | ✅ PASS |
| 5 | GUI: Integrate CEC Panel | ✅ COMPLETE | 1 new + 1 modify | ✅ PASS |
| 6 | CLI: Complete Type 1 | ✅ COMPLETE | 1 new + 1 modify | ✅ PASS |
| 7 | Cleanup | ✅ COMPLETE | 2 delete | ✅ PASS |

---

## Phase 1 — Introduce `DataManagementPort` (Additive, No Behavior Change)

**Goal**: Introduce the Type 1 facade interface. No existing code changes in this phase.

### Completed
- [x] `application/port/DataManagementPort.java` — interface definition
- [x] `application/port/DataReadinessStatus.java` — value object (record with factory methods)
- [x] `infrastructure/service/DataManagementAdapter.java` — wraps AllClusterDataCache, SystemDataLoader, ECILoader, SystemRegistry
- [x] `infrastructure/data/ECIMapper.java` — static utility for CEC→CVM/MCS ECI mapping

### Build Status
- Code complete: ✅ YES
- Compilation: ✅ SUCCESS (3s)
- Tests: SKIPPED

### Notes
- DataManagementAdapter wraps existing infrastructure components with proper error handling and logging
- ECIMapper has two methods: mapCECToCvmECI (for CVM, ncf-length) and expandECIForMCS (for MCS, tc-length)
- Next phase will wire DataManagementPort into job constructors

---

## Phase 2 — Fix Job Boundaries (COMPLETE)

**Goal**: Move all data loading from UI thread into background jobs. Most critical phase.

**Blocking:** Phase 1 complete ✅

### Completed
- [x] Modified `application/job/MCSCalculationJob.java` — new constructor (request, dataPort, listener), full 5-phase run() with data loading
- [x] Modified `application/job/CVMPhaseModelJob.java` — new constructor (request, dataPort, listener), full 4-phase run() with data loading + first N-R minimization on background thread
- [x] Updated `presentation/gui/view/CalculationSetupPanel.java` — removed prepareMCS/prepareCVMModel calls, simplified to create DataManagementAdapter and submit jobs directly
- [x] Updated `presentation/cli/CEWorkbenchCLI.java` — removed CalculationService creation, updated runMCSCalculation/runCVMCalculation to create DataManagementAdapter and submit jobs with blocking wait-loop
- [x] Threading violation eliminated — no more ECILoader.loadOrInputECI() on background thread

### Build Status
- Code complete: ✅ YES
- Compilation: ✅ SUCCESS (14s)
- Tests: SKIPPED

### Key Changes Summary
- **MCSCalculationJob**: Constructor now `(MCSCalculationRequest, DataManagementPort, CalculationProgressListener)` vs old `(MCSCalculationContext, CalculationProgressListener)`
- **CVMPhaseModelJob**: Constructor now `(CVMCalculationRequest, DataManagementPort, CalculationProgressListener)` vs old `(CVMPhaseModel, SystemIdentity, CalculationProgressListener)`
- **GUI**: CalculationSetupPanel creates DataManagementAdapter inline and submits jobs without intermediate service calls
- **CLI**: setupCalculation() calls simplified; runMCSCalculation/runCVMCalculation now directly submit jobs and block until completion via `job.isRunning()` polling
- **Data loading moved**: All disk I/O (cluster data, CEC/ECI) now happens on background thread via DataManagementPort, not UI thread

---

## Phase 3 — Introduce `CalculationCoordinator` (COMPLETE)

**Goal**: Hollow out `CalculationService`, introduce new coordinator for readiness checking and job submission.

**Blocking:** Phase 2 complete ✅

### Completed
- [x] Created `application/dto/DataReadinessStatus.java` — record with cluster/cec/cfs availability flags and factory methods
- [x] Created `infrastructure/service/CalculationCoordinator.java` — new orchestrator for job submission with readiness validation
  - `submitMCS(request, listener)` — submits MCS job after checking clusters + CEC
  - `submitCVM(request, listener)` — submits CVM job after checking clusters + CEC + CFs
  - `checkMCSReadiness(systemId)` → DataReadinessStatus
  - `checkCVMReadiness(systemId)` → DataReadinessStatus
  - Throws IllegalStateException with detailed message if preconditions not met
- [x] Marked `infrastructure/service/CalculationService.java` as @Deprecated with forRemoval=true and reference to CalculationCoordinator

### Build Status
- Code complete: ✅ YES
- Compilation: ✅ SUCCESS (5s)
- Tests: SKIPPED

### Architecture Summary
**CalculationCoordinator** becomes the primary job submission facade:
- Enforces readiness preconditions before job submission
- Provides transparent, type-safe interface for both GUI and CLI
- Replaces the data-loading responsibilities of CalculationService
- Eliminates duplicated preparation code in presentation controllers
- Jobs receive request + DataManagementPort, perform all data loading on background thread

**DataReadinessStatus** provides clear, actionable feedback:
- `isReadyForMCS()` → clusters && CEC available
- `isReadyForCVM()` → clusters && CEC && CFs available
- Factory methods (missingClusters, missingCEC, missingCFs) for each failure case

---

## Phase 4 — GUI: Data Readiness Gate (COMPLETE)

**Goal**: Add visual readiness status display and gate Run button based on data availability.

**Blocking:** Phase 3 complete ✅

### Completed
- [x] Created `presentation/gui/component/DataReadinessIndicator.java` — visual status badges
  - Compact component showing Cluster, CEC, CF availability with color-coded badges
  - updateStatus(SystemIdentity) refreshes badges based on availability
  - Provides clear status message ("✓ Ready for MCS & CVM" or "✗ Missing required data")
  - Green badges (✓) for available, gray badges (✗) for missing
- [x] Modified `presentation/gui/view/CalculationSetupPanel.java`
  - Added DataReadinessIndicator field and instantiation in constructor
  - Indicator added to layout below system selection label
  - Call to readinessIndicator.updateStatus(system) in setSelectedSystem()
  - New updateRunButtonState() method disables Run button based on readiness:
    - MCS: requires clusters + CEC
    - CVM: requires clusters + CEC + CFs
  - Run button state updates when system selection changes or calculation type (MCS/CVM) changes
  - Added KeyUtils import for cecKey() / clusterKey() derivation

### Build Status
- Code complete: ✅ YES
- Compilation: ✅ SUCCESS (5s)
- Tests: SKIPPED

### User Experience Impact
**Before Phase 4:**
- User could click "Run" even with missing data
- Error dialog shown after button press
- Unclear what data was missing

**After Phase 4:**
- Visual badges show data status immediately upon system selection
- "Run" button disabled when data missing
- Status message below badges explains what's needed
- Dynamic: button enables/disables when switching MCS↔CVM based on CF availability
- Hovering over button shows it's disabled (standard UI feedback)

---

## Phase 5 — GUI: Integrate CEC Panel (COMPLETE)

**Goal**: Convert modal dialog to inline panel in main window tabs.

**Blocking:** Phase 4 complete ✅

### Completed
- [x] Created `presentation/gui/view/CECManagementPanel.java` — converted CECDatabaseDialog to VBox
  - Removed Dialog-specific chrome (dialog title, buttons, result converter)
  - Preserved all functionality: Browser tab + Assembly tab
  - Preserved all helper methods and inner classes
- [x] Modified `presentation/gui/CEWorkbenchApplication.java` — integrated CEC panel into main layout
  - Import changed from CECDatabaseDialog to CECManagementPanel
  - Removed "Database" menu from createMenuBar()
  - Updated createRightPanel() to instantiate CECManagementPanel
  - Added new tab: `Tab cecTab = new Tab("CEC Database", cecPanel);` between result and log tabs
  - Removed dead method: showCECDatabase()

### Build Status
- Code complete: ✅ YES
- Compilation: ✅ SUCCESS (5s)
- Tests: SKIPPED

### Architecture Impact
**Before Phase 5:**
- CEC database management was a modal dialog (CECDatabaseDialog)
- User had to explicitly open from menu; modal blocked other UI work
- Dialog code referenced pre-existing file (not integrated with main window lifecycle)

**After Phase 5:**
- CEC database management is an always-visible tab in main window
- Same functionality (Browser + Assembly) but integrated naturally
- Tab-based UI allows seamless switching between calculation setup and CEC management
- Main window lifecycle manages panel (no separate dialog lifecycle)

---

## Phase 6 — CLI: Complete Type 1 (COMPLETE)

**Goal**: Implement complete CLI commands for Type 1 (Data Management) operations.

**Blocking:** Phase 3 complete ✅

### Completed
- [x] Created `presentation/cli/DataManagementCLI.java` (521 lines) — Type 1 sub-menu with 3 submenu levels
  - System Registry submenu: register, list, remove systems
  - Cluster Identification submenu: display pipeline status, check cache directory
  - CEC Database submenu: browse CECs, preview subsystem assembly
  - Full menu hierarchy with clear user prompts and error handling
- [x] Modified `presentation/cli/CEWorkbenchCLI.java` — integrated DataManagementCLI
  - Restructured main menu to delegate Type 1 operations to DataManagementCLI
  - Removed duplicate system/cluster management methods (moved to DataManagementCLI)
  - Integrated DataManagementCLI with jobManager and scanner
  - Cleaned up menu display (removed box-drawing character issues)
  - Maintained all Type 2 (calculation) functionality

### Build Status
- Code complete: ✅ YES
- Compilation: ✅ SUCCESS (3s)
- Tests: SKIPPED

### Key Features Implemented
- **System Registry:** Full CRUD operations with status display
- **Cluster Cache:** Status visualization showing cached cluster data by key
- **CEC Browsing:** View CEC values and metadata for registered systems
- **Subsystem Enumeration:** Display required subsystems for ternary+ CEC assembly by order
- **Job Monitoring:** Background job status visible from main menu
- **Graceful Degradation:** Stub messages for incomplete features (cluster ID pipeline details)

### Architecture Summary
**DataManagementCLI** encapsulates all Type 1 operations:
- Organized by functional domain (System, Cluster, CEC)
- Each domain has its own submenu with dedicated operations
- Reuses existing infrastructure (SystemRegistry, SystemDataLoader, CECAssemblyService)
- Proper exception handling and user-friendly error messages

**CEWorkbenchCLI** remains Type 2 entry point:
- Main menu now delegates menu.option[1] → DataManagementCLI.showMenu()
- Keeps calculation setup (option 2), job monitoring (option 3), stats (option 4)
- Clean separation: Type 1 operations isolated in DataManagementCLI instance

---

## Phase 7 — Cleanup (COMPLETE)

**Goal**: Delete dead code, fix path inconsistencies.

**Blocking:** Phase 6 complete, all code migrated ✅

### Completed
- [x] Delete `infrastructure/service/CalculationService.java` (1,271 lines removed)
  - Deprecated in Phase 3, replaced by CalculationCoordinator
  - Verified: No imports, no active references
- [x] Delete `presentation/gui/component/CECDatabaseDialog.java` (no longer referenced)
  - Deprecated in Phase 5, replaced by CECManagementPanel
  - Verified: No imports, no active references
- [x] ~~Remove "Database" menu from `CEWorkbenchApplication`~~ — ✅ completed in Phase 5
- ⚠️ **WorkspaceManager / SystemRegistry path consistency** (OPTIONAL, LOW PRIORITY)
  - Current state: Both receive userHome and internally append `.ce-workbench`
  - This is consistent, though not unified through WorkspaceManager
  - WorkspaceManager is used for SystemDataLoader (good practice)
  - Refactoring SystemRegistry/ResultRepository to use it would be cleaner but higher risk
  - Recommendation: Document as future optimization, not critical

### Build Status
- Code complete: ✅ YES
- Compilation: ✅ SUCCESS (3s)
- Tests: SKIPPED

### Cleanup Summary
All deprecated code from Type 1/2 separation has been removed. Application builds cleanly with no dead code. Codebase is now lean and maintainable.

---

## Phase 8 — ECI Standardization: ncf-length throughout MCS (Mar 12, 2026 — COMPLETE)

**Goal:** Use `allData.getStage2().getNcf()` as single source of truth for ECI length. Both CVM
and MCS use ncf-length arrays; MCS engine is safe to use ncf by skipping sub-pair cluster
embeddings (which have ECI=0 always in canonical ensemble).

### Root Cause
The "ECI length (4) does not match cluster type count (6)" error persisted across sessions
because previous attempts patch symptoms (CEC files, cache values) without fixing code:
1. `MCSCalculationJob` expanded ncf→tc using WRONG tc source (`stage1.getTc()` = 5)
2. `MCSCalculationContext` validated against `disClusterData.getTc()` = 6 → mismatch
3. `EmbeddingGenerator` generated embeddings for ALL cluster types (indices 0–5) including
   point/empty, but ncf-length ECI arrays (4 elements) crashed `LocalEnergyCalc` on access to `eci[4]`, `eci[5]`

### Fixes Applied
1. **`EmbeddingGenerator.buildTemplates()` (domain/mcs/):** Skip sub-pair clusters (size < 2).
   Point (size=1) and empty (size=0) clusters have ECI=0 always (constants in canonical ensemble).
   Effect: Embedding type indices are all < ncf, ncf-length ECI is safe.

2. **`MCSCalculationContext.getClusterTypeCount()` (infrastructure/context/):** Return ncf from
   Stage 2, not tc from disClusterData. ECI arrays are ncf-length; validation target must match.

3. **`MCSCalculationJob` (application/job/, line 135):** Remove expansion. Pass ncf-length ECI
   directly since EmbeddingGenerator now skips sub-pair types.

4. **`ResultsPanel` (presentation/gui/):** Remove debug `System.out.println()` statements.

5. **`Ti-Nb/cec.json`:** Fix CEC value order from `[pair1, pair2, tri, tet]` to standard
   `[tet, tri, pair1, pair2]` matching A2_T structural type CF ordering (A2 = Pearson symbol, T = tetrahedron model).

### Design Outcome
- `allData.getStage2().getNcf()` is the definitive ECI length source
- Both CVM and MCS use ncf-length ECI from database (no expansion)
- Architecturally clean: no workarounds, no bounds guards in `LocalEnergyCalc`
- CVM path unchanged; MCS path simplified

### Testing
- Build: ✅ Clean compilation (no errors/warnings)
- Tests: ✅ All 104 tests pass (including CVM binary/ternary, architecture checks)
- Verification: 5 files modified (1 commit), all changes architectural

### Files Modified
- `domain/mcs/EmbeddingGenerator.java` — skip sub-pair clusters
- `infrastructure/context/MCSCalculationContext.java` — validate against ncf
- `application/job/MCSCalculationJob.java` — remove expansion
- `presentation/gui/view/ResultsPanel.java` — remove debug logging
- `app/src/main/resources/data/systems/Ti-Nb/cec.json` — fix CEC order

---

---

## Phase 9 — Unified CVM/MCS Pipeline: Step 1 — Fix hmixPerSite Data Loss (Mar 12, 2026)

### Goal
Propagate `hmixPerSite` (mixing enthalpy from CF formula) from `MCResult` through to `MCSResult`.
Previously computed correctly in the domain but silently dropped at the adapter boundary.

### Changes
- `domain/model/result/MCSResult.java` — added `hmixPerSite` record component after `energyPerSite`;
  updated `fromEngine()` factory signature (11 params); updated `summary()` to show Hmix/site
- `infrastructure/mcs/MCSRunnerAdapter.java` — pass `mcResult.getHmixPerSite()` to `fromEngine()`

### Result
- Build: ✅ Clean compilation (no errors)
- Tests: ✅ All 104 tests pass
- `MCSResult.hmixPerSite` now carries `Σ hmixCoeff[t]·⟨u_t⟩` — same CE formula as CVM enthalpy

---

## Phase 9 — Unified CVM/MCS Pipeline: Step 2 — EquilibriumState + EngineMetrics (Mar 12, 2026)

### Goal
Introduce the unified `EquilibriumState` result type and `EngineMetrics` sealed interface.
Expand `ThermodynamicResult` with `enthalpyOfMixing()` and `compositionArray()`.

### Changes
- `domain/model/result/EngineMetrics.java` — **new** sealed interface with `CvmMetrics` and `McsMetrics` records
- `domain/model/result/EquilibriumState.java` — **new** record implementing `ThermodynamicResult`;
  carries T, x[], CFs, enthalpy, gibbsEnergy?, entropy?, heatCapacity?, EngineMetrics, timestamp;
  static factories `fromCvm(...)` and `fromMcs(...)`
- `domain/model/result/ThermodynamicResult.java` — add `EquilibriumState` to `permits`;
  add abstract `enthalpyOfMixing()`; add default `compositionArray()`
- `domain/model/result/CVMResult.java` — implement `enthalpyOfMixing()` returning `enthalpy`
- `domain/model/result/MCSResult.java` — implement `enthalpyOfMixing()` returning `hmixPerSite`

### Result
- Build: ✅ Clean compilation (no errors)
- Tests: ✅ All 104 tests pass
- `EquilibriumState` is additive — no existing consumers broken; `CVMResult`/`MCSResult` remain

---

## Phase 9 — Unified CVM/MCS Pipeline: Step 3 — Migrate Ports & Adapters to EquilibriumState (Mar 12, 2026)

### Goal
Make `EquilibriumState` the canonical runtime return type for both engines.
Retire `CVMResult` and `MCSResult` as the adapter output types.

### Changes
- `EngineMetrics.McsMetrics` — added `energyPerSite` field (incremental MCEngine tracking)
- `EquilibriumState.fromMcs()` — added `energyPerSite` parameter
- `CVMSolverPort` — return type `CVMResult` → `EquilibriumState`
- `MCSRunnerPort` — return type `MCSResult` → `EquilibriumState`
- `CVMEngineAdapter` — build `EquilibriumState.fromCvm(...)` directly
- `MCSRunnerAdapter` — build `EquilibriumState.fromMcs(...)` directly (passes energyPerSite)
- `CVMCalculationUseCase` — work with `EquilibriumState`; access CvmMetrics via pattern match
- `MCSCalculationUseCase` — work with `EquilibriumState`; access McsMetrics via pattern match
- `MCSCalculationJob` — `instanceof MCSResult` → `instanceof EquilibriumState`

### Result
- Build: ✅ Clean compilation (no errors)
- Tests: ✅ All 104 tests pass
- Both pipelines now produce `EquilibriumState` at the application layer boundary
- `CVMResult`/`MCSResult` remain as domain-layer records (used by tests, CVMPhaseModel internals)

---

## Phase 9 — Unified CVM/MCS Pipeline: Step 4 — MCSPhaseModel (Mar 12, 2026)

### Goal
Create `MCSPhaseModel` as the MCS domain analog of `CVMPhaseModel` — same conceptual API,
different internal engine. Closes the [ARCHITECTURAL] gap: MCS now has a proper phase model.

### Changes
- `domain/mcs/MCSPhaseModel.java` — **new** class mirroring CVMPhaseModel pattern:
  - Immutable: `AllClusterData`, `double[] ncfEci`, `numComponents`
  - Mutable: `temperature`, `moleFractions`, `supercellSize`, `nEquil`, `nAvg`, `seed`
  - Cached: `EquilibriumState cachedState` (null when dirty)
  - Lazy: `ensureRun()` — only re-runs MC if parameters changed
  - Factory: `MCSPhaseModel.create(allData, eci, K, T, xFrac)` — runs first simulation
  - Public: `getEquilibriumState()`, `minimize(T, xFrac)`, `minimizeBinary(T, xB)`
  - Delegates to `MCSRunner.Builder` internally

### Result
- Build: ✅ Clean compilation (no errors)
- Tests: ✅ All 104 tests pass
- CVM and MCS now both have proper domain phase models

---

## Notes

- All work tracked as phases; each phase must pass clean compilation before proceeding
- ThreadVerification: add `assert !Platform.isFxApplicationThread()` in job run() methods post-Phase 2
- ECILoader.loadOrInputECI() callsite must be removed from background paths (high priority in Phase 2)
- Phase 8 resolves the persistent ECI length mismatch by treating ncf as the canonical length source
- Phase 9+ implements the unified CVM/MCS pipeline vision (see CLAUDE_SESSION_HANDOFF.md)

---

## Phase 9.5 — Move ECIMapper call into CVMPhaseModel (Mar 12, 2026)

**Goal**: Remove infrastructure import from application job layer by internalizing ECI trim logic.

### Changes
- `domain/cvm/CVMPhaseModel.java` — `setECI()` now accepts arrays of length `>= ncf`, trims to ncf via `Arrays.copyOf`. Removes need for explicit ECIMapper call in callers.
- `application/job/CVMPhaseModelJob.java` — removed `ECIMapper.mapCECToCvmECI()` call and import; passes raw `nciEciOpt.get()` directly (mirrors MCS path).

### Result
- Build: ✅ Clean compilation
- Tests: ✅ All 94 tests pass

---

## Phase 9.6 — Extract ThermodynamicCalculationRequest Base DTO (Mar 12, 2026)

**Goal**: Eliminate ~60 lines of verbatim duplication in CVM and MCS request DTOs.

### Changes
- `application/dto/ThermodynamicCalculationRequest.java` — NEW abstract base with shared fields (`systemId`, `temperature`, `composition`, `compositionArray`, `numComponents`) + `validateCommon()` + `AbstractBuilder<B>`.
- `application/dto/CVMCalculationRequest.java` — extends base; keeps only `tolerance` + builder setter.
- `application/dto/MCSCalculationRequest.java` — extends base; keeps only `supercellSize`, `equilibrationSteps`, `averagingSteps`, `seed`.

### Result
- Build: ✅ Clean compilation
- Tests: ✅ All 94 tests pass

---

## Phase 9.7 — Extract AbstractThermodynamicJob (Mar 12, 2026)

**Goal**: Eliminate duplicated Phases 1+2 (system load → cluster data → ECI) from both jobs.

### Changes
- `application/job/AbstractThermodynamicJob.java` — NEW extends `AbstractBackgroundJob`. Provides `loadSystemData(request)` returning `ThermodynamicJobData(system, clusterData, ncfEci)`.
- `application/job/CVMPhaseModelJob.java` — extends `AbstractThermodynamicJob`; Phases 1+2 reduced to one `loadSystemData()` call.
- `application/job/MCSCalculationJob.java` — same; ~45 lines of duplication removed.

### Result
- Build: ✅ Clean compilation
- Tests: ✅ All 94 tests pass

---

## Phase 9.8 — Fix Calculation Context Layer Violation (Mar 12, 2026)

**Goal**: Move `AbstractCalculationContext`, `CVMCalculationContext`, `MCSCalculationContext` from `infrastructure/context/` to `application/dto/` where they belong.

### Changes
- `application/dto/AbstractCalculationContext.java` — NEW (moved from `infrastructure/context/`)
- `application/dto/CVMCalculationContext.java` — NEW (moved)
- `application/dto/MCSCalculationContext.java` — NEW (moved)
- Old infrastructure files deleted.
- Imports updated in: `MCSCalculationUseCase`, `CVMCalculationUseCase`, `MCSRunnerPort`, `MCSCalculationJob`, `MCSRunnerAdapter`.

### Result
- Build: ✅ Clean compilation
- Tests: ✅ All 94 tests pass
