# Unified CVM/MCS Pipeline — Refactor Plan
**Project:** CE Thermodynamics Workbench  
**Status:** IN PROGRESS  
**Version:** 1.0 (created Mar 13, 2026)  
**Owner:** Update after each session

---

## The Unified Vision (immutable — do not change)

Both CVM and MCS are equilibrium engines with identical external contracts.

```
INPUTS:   AllClusterData + ECI (ncf-length) + T + x[]
ENGINE:   CVM → Newton-Raphson  |  MCS → Metropolis MC
OUTPUTS:  EquilibriumState { CFs[], G?, H, S?, Cv?, SROs, EngineMetrics }
```

- G and S are `OptionalDouble.empty()` for MCS (physics boundary)
- Cv is `OptionalDouble.empty()` for CVM (single-run limitation)
- Both models use `model.getEquilibriumState()` as the single query method
- Both models auto-re-run their engine when parameters change (lazy evaluation)

---

## Departures Identified (source: Claude review, Mar 13 2026)

| ID | Description | Severity | Status |
|----|-------------|----------|--------|
| D1 | CVM job returns live model; MCS job returns frozen result — asymmetric | HIGH | ✅ DONE (Phase 10.2) |
| D2 | `MCSPhaseModel` exists in domain but is never used in the pipeline | HIGH | ✅ DONE (Phase 10.2) |
| D3 | `CVMCalculationUseCase` orphaned; CVM job bypasses it entirely | MEDIUM | ⬜ OPEN |
| D4 | Mutable `AbstractCalculationContext` DTO layer is redundant given PhaseModel objects | MEDIUM | ⬜ OPEN |
| D5 | `CVMPhaseModelExecutor` accesses `state.G`, `state.H` (old field API) → **build failure** | HIGH | ✅ DONE (Phase 10.1) |
| D6 | Ternary composition collapsed to `composition[0]` scalar in `CVMPhaseModelJob` | MEDIUM | ⬜ OPEN |
| D7 | `MCSRunnerPort.run()` takes `MCSCalculationContext` DTO, not domain types | MEDIUM | ⬜ OPEN |
| D8 | Cancellation support is MCS-only; CVM has no intra-minimization cancel | LOW | ⬜ OPEN |

---

## Implementation Phases

### Phase 10.1 — Fix the build (D5) ✅ prerequisite for everything
**Goal:** Get `./gradlew build` passing again. Zero other changes.  
**File:** `infrastructure/cvm/CVMPhaseModelExecutor.java`  
**Change:** Replace field access (`state.G`, `state.H`, `state.S`, `state.iterations`,
`state.convergenceMeasure`, `state.correlationFunctions`) with record accessor calls
(`state.gibbsEnergy().getAsDouble()`, `state.enthalpy()`, `state.entropy().getAsDouble()`,
`state.metrics()` cast to `CvmMetrics` for iterations/gradientNorm, `state.correlationFunctions()`).

```java
// OLD (broken):
CVMPhaseModel.EquilibriumState state = model.getEquilibriumState();
state.G        // field access — no longer exists
state.iterations

// NEW (correct):
EquilibriumState state = model.getEquilibriumState();
state.gibbsEnergy().getAsDouble()
((EngineMetrics.CvmMetrics) state.metrics()).iterations()
```

**Tests:** `./gradlew build` must pass. All 104 tests green.  
**Commit message:** `fix: restore CVMPhaseModelExecutor to use EquilibriumState record API (D5)`

---

### Phase 10.2 — Wire MCSPhaseModel into MCSCalculationJob (D1, D2)
**Goal:** MCS job uses `MCSPhaseModel` exactly as CVM job uses `CVMPhaseModel`.  
**Files touched:**
- `application/job/MCSCalculationJob.java` — replace context/usecase wiring with `MCSPhaseModel.create()`
- `application/port/MCSRunnerPort.java` — can be removed or kept for adapter use inside MCSPhaseModel
- `infrastructure/mcs/MCSRunnerAdapter.java` — keep; MCSPhaseModel delegates to MCSRunner internally

**Target shape of `MCSCalculationJob.run()`:**
```java
// Phase 1+2: unchanged (AbstractThermodynamicJob.loadSystemData)
ThermodynamicJobData jobData = loadSystemData(request);

// Phase 3: Create MCSPhaseModel (mirrors CVMPhaseModelJob exactly)
double[] composition = resolveComposition(request);
MCSPhaseModel model = MCSPhaseModel.create(
    jobData.clusterData(),
    jobData.ncfEci(),
    jobData.system().getNumComponents(),
    request.getTemperature(),
    composition
);

// Phase 4: Query equilibrium state (mirrors CVMPhaseModelJob)
EquilibriumState state = model.getEquilibriumState();
this.result = state;
markCompleted();
```

**Decision point:** Does MCSCalculationJob store the `MCSPhaseModel` (for interactive scanning)
or just the `EquilibriumState`? For symmetry with CVM, store the model. See Phase 10.5 for GUI.

**Tests:** All 104 tests green. Verify Nb-Ti MCS result unchanged numerically.  
**Commit message:** `feat: wire MCSPhaseModel into MCSCalculationJob — unified model pattern (D1, D2)`

---

### Phase 10.3 — Fix ternary composition in CVMPhaseModelJob (D6)
**Goal:** Stop collapsing composition array to scalar.  
**File:** `application/job/CVMPhaseModelJob.java`

```java
// OLD (broken for K≥3):
double scalarComposition = composition[0];
model = CVMPhaseModel.create(cvmInput, jobData.ncfEci(),
    request.getTemperature(), scalarComposition);   // ← binary-only

// NEW:
model = CVMPhaseModel.create(cvmInput, jobData.ncfEci(),
    request.getTemperature(), composition);         // ← full array
```

This requires `CVMPhaseModel.create()` to accept `double[]` rather than `double`.
Check `CVMPhaseModel.create()` signature and add/update the multi-component factory.

**Tests:** `CVMTernaryIntegrationTest` must pass with correct composition.  
**Commit message:** `fix: pass full composition array to CVMPhaseModel.create — fixes ternary CVM (D6)`

---

### Phase 10.4 — Clean up orphaned CVMCalculationUseCase (D3)
**Goal:** Either wire it into `CVMPhaseModelJob` (for single-shot queries) or delete it.  
**Decision:** The use-case pattern is redundant once CVMPhaseModel is the entry point.
Delete `CVMCalculationUseCase`, `CVMSolverPort`, `CVMEngineAdapter` unless there is a
non-GUI caller that needs a stateless single-shot API.

If keeping for CLI: wire it through `CVMPhaseModel.minimize(T, x)` factory.
If deleting: update architecture test allowlists accordingly.

**Tests:** Architecture boundary test must still pass. 104 tests green.  
**Commit message:** `refactor: remove orphaned CVMCalculationUseCase / CVMSolverPort (D3)`

---

### Phase 10.5 — Retire AbstractCalculationContext and context DTOs (D4, D7)
**Goal:** Remove the mutable hydration bag pattern. Jobs pass typed values directly to PhaseModel.  
**Files to delete/simplify:**
- `application/dto/AbstractCalculationContext.java`
- `application/dto/CVMCalculationContext.java`
- `application/dto/MCSCalculationContext.java`
- `application/port/MCSRunnerPort.java` (replace DTO param with domain types)

**Prerequisite:** Phases 10.2 and 10.3 complete. No caller of MCSCalculationContext remains.

**Tests:** 104 tests green. Architecture test green.  
**Commit message:** `refactor: retire AbstractCalculationContext DTO hierarchy (D4, D7)`

---

### Phase 10.6 — Add cancellation to CVM (D8) [optional / low priority]
**Goal:** Allow GUI cancel during long N-R iterations.  
**File:** `domain/cvm/NewtonRaphsonSolverSimple.java` — add `BooleanSupplier cancellationCheck`
parameter; check it at the top of each outer iteration loop.  
**Commit message:** `feat: add cooperative cancellation to CVM Newton-Raphson engine (D8)`

---

## Invariants — Never Break These

1. `./gradlew build` must pass after every commit
2. All 104 tests must be green after every commit
3. `ArchitectureBoundaryTest` must pass — domain never imports application/infrastructure
4. `AllClusterData` is the single source of cluster topology for both engines
5. ECI arrays are always ncf-length — never tc-length, never padded
6. `EquilibriumState` is the only result type — never return engine-specific structs to callers

---

## Key File Map (quick reference for new sessions)

```
domain/cvm/CVMPhaseModel.java          — CVM model, lazy N-R, getEquilibriumState()
domain/mcs/MCSPhaseModel.java          — MCS model, lazy MC, getEquilibriumState()
domain/model/result/EquilibriumState.java — shared immutable result record
domain/model/result/EngineMetrics.java — sealed: CvmMetrics | McsMetrics

application/job/CVMPhaseModelJob.java  — background job: loads data, creates CVMPhaseModel
application/job/MCSCalculationJob.java — background job: loads data, (should) create MCSPhaseModel
application/job/AbstractThermodynamicJob.java — shared Phases 1+2 (load system + ECI)
application/dto/ThermodynamicCalculationRequest.java — shared immutable request base

infrastructure/cvm/CVMPhaseModelExecutor.java  — BROKEN (D5) — needs record API fix
infrastructure/mcs/MCSRunnerAdapter.java       — builds MCSRunner, maps MCResult → EquilibriumState
infrastructure/service/DataManagementAdapter  — wraps cache/loader/registry

presentation/gui/view/CalculationSetupPanel.java — submits jobs, holds job references
```

---

## Session Handoff Protocol

At the END of each working session, update this file:
1. Mark completed phases in the table above (⬜ → ✅)
2. Note any discoveries that change the plan
3. Record exact `git log --oneline -3` output so next session knows the commit baseline
4. Note any tests that are currently failing and why

**Current baseline commit:** (fill in after first commit)  
**Tests passing:** 104/104 (pre-Phase-10 baseline)  
**Build status:** FAILING (D5 — CVMPhaseModelExecutor field access)

---

## How to Start a New Claude Session

Paste this into the chat:

```
Please review this project. Read these files first in order:
1. REFACTOR_PLAN.md        — current plan and phase status
2. PROJECT_STATUS.md       — overall project health  
3. ARCHITECTURE_CONTRACT.md — layer rules (enforce strictly)
4. CLAUDE_SESSION_HANDOFF.md — prior session context

Then read the source files for the current phase before proposing any changes.
Current phase: [FILL IN PHASE NUMBER]
Current build status: [PASS/FAIL]
```