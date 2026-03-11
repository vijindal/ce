# Type 1/2 Separation — Implementation Progress

**Start Date:** Mar 11, 2026
**Status:** IN PROGRESS

---

## Phase Overview

| Phase | Name | Status | Files | Build |
|-------|------|--------|-------|-------|
| 1 | Introduce `DataManagementPort` | ✅ COMPLETE | 4 new | ✅ PASS |
| 2 | Fix Job Boundaries | ✅ COMPLETE | 2 modify | ✅ PASS |
| 3 | Introduce `CalculationCoordinator` | ⏸️ PENDING | 2 files | — |
| 4 | GUI: Data Readiness Gate | ⏸️ PENDING | 2 modify | — |
| 5 | GUI: Integrate CEC Panel | ⏸️ PENDING | 3 modify | — |
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

## Phase 3 — Introduce `CalculationCoordinator`

**Goal**: Hollow out `CalculationService`, introduce new coordinator.

**Blocking:** Phase 2 complete

### Tasks (To Do)
- [ ] Create `infrastructure/service/CalculationCoordinator.java`
- [ ] Mark `infrastructure/service/CalculationService.java` deprecated

### Build Status
- Code complete: NO
- Compilation: NOT RUN
- Tests: NOT RUN

---

## Phase 4 — GUI: Data Readiness Gate

**Goal**: Add readiness status display and gate calculations on it.

**Blocking:** Phase 2 complete

### Tasks (To Do)
- [ ] Create `presentation/gui/component/DataReadinessIndicator.java`
- [ ] Modify `presentation/gui/view/CalculationSetupPanel.java` — wire indicator, gate button

### Build Status
- Code complete: NO
- Compilation: NOT RUN
- Tests: NOT RUN

---

## Phase 5 — GUI: Integrate CEC Panel

**Goal**: Convert modal dialog to inline panel in main window tabs.

**Blocking:** Phase 4 complete

### Tasks (To Do)
- [ ] Create `presentation/gui/view/CECManagementPanel.java` from dialog
- [ ] Modify `presentation/gui/CEWorkbenchApplication.java` — new tab layout
- [ ] Modify `presentation/gui/view/SystemRegistryPanel.java` — remove menu integration

### Build Status
- Code complete: NO
- Compilation: NOT RUN
- Tests: NOT RUN

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
- [ ] Delete `presentation/gui/component/CECDatabaseDialog.java`
- [ ] Fix `WorkspaceManager` / `SystemRegistry` path inconsistency
- [ ] Remove "Database" menu from `CEWorkbenchApplication`

### Build Status
- Code complete: NO
- Compilation: NOT RUN
- Tests: NOT RUN

---

## Notes

- All work tracked as phases; each phase must pass clean compilation before proceeding
- ThreadVerification: add `assert !Platform.isFxApplicationThread()` in job run() methods post-Phase 2
- ECILoader.loadOrInputECI() callsite must be removed from background paths (high priority in Phase 2)
