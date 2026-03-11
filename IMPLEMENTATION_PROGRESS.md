# Type 1/2 Separation — Implementation Progress

**Start Date:** Mar 11, 2026
**Status:** IN PROGRESS

---

## Phase Overview

| Phase | Name | Status | Files | Build |
|-------|------|--------|-------|-------|
| 1 | Introduce `DataManagementPort` | ✅ COMPLETE | 4 new | ✅ PASS |
| 2 | Fix Job Boundaries | ✅ COMPLETE | 2 modify | ✅ PASS |
| 3 | Introduce `CalculationCoordinator` | ✅ COMPLETE | 2 new + 1 deprecate | ✅ PASS |
| 4 | GUI: Data Readiness Gate | ✅ COMPLETE | 2 new + 1 modify | ✅ PASS |
| 5 | GUI: Integrate CEC Panel | ✅ COMPLETE | 1 new + 1 modify | ✅ PASS |
| 6 | CLI: Complete Type 1 | ⏸️ PENDING | 2 files | — |
| 7 | Cleanup | ⏸️ PENDING | Clean | — |

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

## Phase 6 — CLI: Complete Type 1

**Goal**: Implement stub CLI commands for cluster identification and CEC management.

**Blocking:** Phase 3 complete

### Tasks (To Do)
- [ ] Create `presentation/cli/DataManagementCLI.java` — Type 1 sub-menu
- [ ] Modify `presentation/cli/CEWorkbenchCLI.java` — add DataManagementCLI calls

### Build Status
- Code complete: NO
- Compilation: NOT RUN
- Tests: NOT RUN

---

## Phase 7 — Cleanup

**Goal**: Delete dead code, fix path inconsistencies.

**Blocking:** Phase 6 complete, all code migrated

### Tasks (To Do)
- [ ] Delete `infrastructure/service/CalculationService.java`
- [ ] Delete `presentation/gui/component/CECDatabaseDialog.java` — ✅ completed in Phase 5 (no longer referenced)
- [ ] Fix `WorkspaceManager` / `SystemRegistry` path inconsistency
- [x] ~~Remove "Database" menu from `CEWorkbenchApplication`~~ — ✅ completed in Phase 5

### Build Status
- Code complete: NO (2 of 4 tasks completed in Phase 5)
- Compilation: NOT RUN
- Tests: NOT RUN

---

## Notes

- All work tracked as phases; each phase must pass clean compilation before proceeding
- ThreadVerification: add `assert !Platform.isFxApplicationThread()` in job run() methods post-Phase 2
- ECILoader.loadOrInputECI() callsite must be removed from background paths (high priority in Phase 2)
