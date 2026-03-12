---
# Claude Session Handoff — CVM/MCS Refactor

## What was accomplished in the prior Claude session

A deep architectural review was conducted across the full CVM and MCS
pipelines. The reviewer had access to the src zip (partial — application
and domain/cvm only) plus the README, PROJECT_STATUS, and
ARCHITECTURAL_CONTRACT files.

## The Unified Vision (agreed and documented)

Both CVM and MCS are equilibrium engines sharing identical inputs and
outputs. They differ only in their internal equilibrium engine.

    INPUTS:  AllClusterData + ECI (ncf-length) + T + x[]
    ENGINE:  CVM → Newton-Raphson  |  MCS → Metropolis MC
    OUTPUTS: EquilibriumState (shared type)
               - correlationFunctions[]
               - gibbsEnergy, enthalpy, entropy, heatCapacity
               - EngineMetrics (engine-specific diagnostics)

## Departures from the vision identified in the code review

1. [ARCHITECTURAL] No MCSPhaseModel domain object exists.
   MCSCalculationContext (infrastructure) is a mutable data bag,
   not a model. CVMPhaseModel is the correct pattern to follow.

2. [HIGH] CVMResult and MCSResult have incompatible fields and names.
   Both should be replaced by a shared EquilibriumState type.
   CVMResult fields: converged, iterations, gradientNorm,
                     gibbsEnergy, enthalpy, entropy, equilibriumCFs
   MCSResult fields: correlationFunctions, energyPerSite,
                     heatCapacityPerSite, acceptRate
   Neither contains all the fields the unified model needs.

3. [MEDIUM] CVMCalculationRequest and MCSCalculationRequest duplicate
   identical composition validation logic (systemId, temperature,
   composition, compositionArray, numComponents fields + validation).
   Should share a ThermodynamicRequest abstract base class.

4. [MEDIUM] ECIMapper is called in CVMPhaseModelJob (job layer).
   ECI format mapping belongs inside CVMPhaseModel constructor —
   invisible to callers. Both models should accept raw nciEci.
   NOTE: Phase 8 already removed ECIMapper from MCSCalculationJob.

5. [MEDIUM] CVMPhaseModelJob and MCSCalculationJob duplicate Phases
   1+2 (getSystem → loadClusterData → loadECI). Should be extracted
   into AbstractThermodynamicJob.

6. [LOW] CVMPhaseModel calls CVMFreeEnergy.evaluate() twice after
   the NR solver — once for G/H/S (already in CVMSolverResult),
   once for gradient/Hessian (needed for stability). Minor
   inefficiency; the second call is legitimate.

7. [LOW] MCS does not expose enthalpy or entropy as first-class
   outputs. MCSResult has energyPerSite but not G explicitly.
   Under the unified model both should appear in EquilibriumState.

## Key facts learned (to avoid re-investigation)

- Build: Gradle, Java 25. Command: ./gradlew build
- Tests: 104 passing. Do NOT break these.
- Architecture tests: ArchitectureBoundaryTest.java enforces
  layer boundaries. Every change must pass it.
- ECI: Both CVM and MCS now use ncf-length ECI directly (Phase 8).
  No expansion or mapping between them.
- CVMEngine: Was actively used (RC-6 was fixed there). Not dead code.
  Re-examine before deleting.
- NewtonRaphsonSolverSimple: Already implemented and working.
  Issues 3a/3b from original review are already resolved.
- ARCHITECTURAL_CONTRACT.md: Already strict. Application layer
  must NOT import infrastructure (Rule B).
- MCSCalculationContext lives in infrastructure/context/ — this
  violates Rule B (application ports reference it). Moving it
  to application/dto is the correct fix.
- domain/model/result/ has 6 result files — need to read these
  before proposing new result types.

## What the next Claude session should do first

1. Read ALL governance files: README, PROJECT_STATUS,
   ARCHITECTURAL_CONTRACT, MEMORY (if exists),
   IMPLEMENTATION_PROGRESS (if exists), this file.

2. Read the actual current source files the prior session
   could NOT see:
   - domain/model/result/  (all files — what result types exist?)
   - domain/mcs/MCSPhaseModel.java  (does it exist?)
   - infrastructure/context/MCSCalculationContext.java
   - infrastructure/service/CalculationService.java
   - infrastructure/mcs/  (all files)
   - application/port/  (all 5 files)
   - domain/cvm/CVMPhaseModel.java  (inner EquilibriumState class)

3. Do a gap analysis: for each departure listed above, check
   whether it is already resolved in the current code.

4. Only then propose implementation steps.

## Prompts already drafted and ready to use

The following prompts were drafted and are correct given the
architectural vision. They need gap-analysis validation before
running (step 3 above may show some are already done):

- Prompt 0:  Populate governance files with unified vision
- Prompt 4:  Create EquilibriumState + EngineMetrics
- Prompt 5:  Create ThermodynamicRequest base class
- Prompt 6:  CVM model alignment (ECIMapper into constructor)
- Prompt 7:  Create MCSPhaseModel
- Prompt 8:  Move MCSCalculationContext, retire MCSResult
- Prompt 9:  AbstractThermodynamicJob
- Prompt 10: Retire CalculationProgressListener
- Prompt 11: Final dead code sweep

Ask the new Claude session to draft each prompt fully
before running it, confirming against actual current code.

## How to resume with a new Claude instance

Say: "I am resuming a CVM/MCS Java refactor. Read
CLAUDE_SESSION_HANDOFF.md, all governance files, and the
source files listed in the handoff under 'What the next
Claude session should do first'. Then tell me what you
found and what gaps remain before we start any prompts."
