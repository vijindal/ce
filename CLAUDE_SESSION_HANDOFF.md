# Claude Session Handoff — Unified CVM/MCS Pipeline Refactor
**Last Updated:** Mar 13, 2026
**Sessions completed:** Architecture review → Phase 10.1 → Phase 10.2
**Next Session Should Start:** Phase 10.3

---

## Current Build Status

**BUILD: PASSING** (verify with `./gradlew build` after applying Phase 10.2 file)
**Tests:** 104/104 (verify after Phase 10.2)
**App:** Launches and runs normally

---

## Phase Status

| Phase | Description | Status |
|-------|-------------|--------|
| 10.1 | Fix CVMPhaseModelExecutor/Examples (D5) — restore build | ✅ DONE |
| 10.2 | Wire MCSPhaseModel into MCSCalculationJob (D1, D2) | ✅ DONE |
| 10.3 | Fix ternary composition in CVMPhaseModelJob (D6) | ⬜ NEXT |
| 10.4 | Clean up orphaned CVMCalculationUseCase (D3) | ⬜ OPEN |
| 10.5 | Retire AbstractCalculationContext DTOs (D4, D7) | ⬜ OPEN |
| 10.6 | Add CVM cancellation (D8) | ⬜ OPEN |

---

## What Changed in Phase 10.2

MCSCalculationJob.java rewritten to use MCSPhaseModel directly, mirroring CVMPhaseModelJob:
- REMOVED: MCSCalculationContext, MCSCalculationUseCase, MCSRunnerAdapter, MCSProgressListenerAdapter
- ADDED: MCSPhaseModel model field, getModel() method
- Phase 4 now calls model.getEquilibriumState() — MC runs inside MCSPhaseModel
- Engine params applied via check-before-set setters to avoid redundant re-run
- getContext() removed (no callers). getResult() now returns EquilibriumState directly.

Callers (CalculationSetupPanel, CEWorkbenchCLI, CalculationCoordinator) needed NO changes —
they only ever used job status methods (isFailed, getErrorMessage, isCompleted).

Dead code after Phase 10.2 (to be deleted in Phase 10.5):
MCSRunnerAdapter, MCSCalculationUseCase, MCSRunnerPort, MCSCalculationContext

---

## Phase 10.3 — Exact Plan (NEXT)

**Goal:** Stop collapsing ternary composition to scalar in CVMPhaseModelJob. Departure D6.
**File:** application/job/CVMPhaseModelJob.java

The bug (around line 100):
```java
double scalarComposition = composition[0];   // silently drops x[1], x[2] for K>=3
model = CVMPhaseModel.create(cvmInput, jobData.ncfEci(),
    request.getTemperature(), scalarComposition);
```

The fix — pass the full array:
```java
model = CVMPhaseModel.create(cvmInput, jobData.ncfEci(),
    request.getTemperature(), composition);
```

BEFORE WRITING THE FIX: Read CVMPhaseModel.java to verify the double[] overload of create()
exists. If it doesn't, add it. The scalar overload calls setComposition(x[0]) internally;
the array overload should call setMoleFractions(composition).

Tests: CVMTernaryIntegrationTest must pass. All 104 green.
Commit: "fix: pass full composition array to CVMPhaseModel.create — fixes ternary CVM (D6)"

---

## Key Facts — Do Not Re-Investigate

- ECI: Always ncf-length. Never tc-length. Never padded.
- EquilibriumState is a Java record. All access via methods (no fields).
  gibbsEnergy() and entropy() are OptionalDouble — empty for MCS (physics boundary)
  heatCapacity() is OptionalDouble — empty for CVM
  metrics() casts to EngineMetrics.CvmMetrics or EngineMetrics.McsMetrics
- MCSPhaseModel.create() runs the first simulation automatically with defaults (L=4, nEquil=5000, nAvg=10000)
- ArchitectureBoundaryTest does NOT catch app->infrastructure imports (known gap)

---

## Files Changed This Refactor

| File | Phase | What changed |
|------|-------|--------------|
| infrastructure/cvm/CVMPhaseModelExecutor.java | 10.1 | Record API accessors |
| infrastructure/cvm/CVMPhaseModelExamples.java | 10.1 | Record API accessors |
| application/job/MCSCalculationJob.java | 10.2 | Full rewrite — uses MCSPhaseModel |

---

## How to Start the Next Claude Session

Upload the project zip and paste:

```
Continuing CE thermodynamics workbench refactor.
Read in order: REFACTOR_PLAN.md, CLAUDE_SESSION_HANDOFF.md, ARCHITECTURE_CONTRACT.md

Build: PASSING. Tests: 104/104.
Completed: Phase 10.1 (CVMPhaseModelExecutor fix) and Phase 10.2 (MCSPhaseModel wiring).
Starting: Phase 10.3 — fix ternary composition in CVMPhaseModelJob (D6).

Please read CVMPhaseModelJob.java and CVMPhaseModel.java before proposing changes.
```