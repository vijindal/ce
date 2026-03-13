# CE Workbench — Project Status

**Last Updated:** March 13, 2026  
**Version:** 0.3.15  
**Build:** ✅ Clean — no errors, no warnings  
**Tests:** ✅ 94 / 94 passing  
**GUI:** ✅ Fully functional  

---

## Health Summary

| Area | Status | Notes |
|------|--------|-------|
| Type 1: Cluster identification pipeline | ✅ Complete | Stages 1-3 working for binary, ternary, quaternary |
| Type 1: CEC database management | ✅ Complete | Browse, edit, assemble in GUI and CLI |
| Type 2: CVM engine (binary K=2) | ✅ Correct | N-R converges; G, H, S, CFs, SROs verified |
| Type 2: CVM engine (ternary K≥3) | ⚠️ Partial | Convergence issue at random-state initial guess — see Known Issues |
| Type 2: MCS engine | ✅ Correct | ΔE tracking, CF normalization, Hmix verified |
| Unified pipeline (CVM ↔ MCS) | ✅ Complete | Both jobs use PhaseModel pattern + shared EquilibriumState |
| ECI/CEC loading | ✅ Unified | Both engines load ncf-length arrays; no expansion or padding |
| Architecture boundaries | ✅ Enforced | ArchitectureBoundaryTest passes; domain never imports infra/app |
| JUL logging | ✅ Complete | All three calculation paths instrumented |

---

## Test Suite

| Test class | Tests | What it covers |
|---|---|---|
| `CVMFreeEnergyTest` | 9 | R_GAS units, G=H−T·S identity, gradient, Hessian symmetry |
| `CMatrixBuilderTest` | 7 | C-matrix dimensions, CV positivity, normalization |
| `CVMCMatrixDiagnosticTest` | 15 | C-matrix diagnostics for binary and ternary |
| `NewtonRaphsonTest` | 11 | N-R convergence, gradient norm, initial-guess correctness |
| `CVMBinaryIntegrationTest` | 15 | Binary pipeline: convergence at multiple (T, x), G=H−T·S, CV≥0, symmetry |
| `CVMTernaryIntegrationTest` | 10 | Ternary pipeline: convergence, G=H−T·S, CV≥0, entropy range |
| `CVMDataFlowVerificationTest` | 10 | Step-by-step ECI mapping and moleFractions data-flow tracing |
| `CECAssemblyIntegrationTest` | 10 | CEC assembly from binary subsystems for ternary/quaternary |
| `AllClusterDataCacheCompatibilityTest` | 2 | Cache load/save compatibility across schema versions |
| `ClusterCacheSchemaMigratorTest` | 2 | Schema migration for legacy Stage-3 payloads |
| `ArchitectureBoundaryTest` | 3 | Layer dependency rules enforced |
| **Total** | **94** | |

---

## Open Work

### Active — Refactor Plan Phase 10

See [Refactor_Plan.md](Refactor_Plan.md) for full details and implementation instructions.

| Phase | Description | Priority |
|-------|-------------|----------|
| 10.3 | Fix ternary composition passed as scalar in `CVMPhaseModelJob` (D6) | HIGH |
| 10.4 | Remove orphaned `CVMCalculationUseCase` (D3) | MEDIUM |
| 10.5 | Retire `AbstractCalculationContext` DTO hierarchy (D4, D7) | MEDIUM |
| 10.6 | Add cooperative cancellation to CVM Newton-Raphson engine (D8) | LOW |

### Known Issues

**CVM ternary convergence (K≥3)**  
The Newton-Raphson solver oscillates at the random-state initial guess for K≥3 systems.
Root cause: many cluster variables (CVs) are zero at the equimolar starting point when
using the {−1, 0, 1} basis, causing a near-singular Hessian. Binary (K=2) is unaffected.

Options under consideration:
- CV regularization — add a small floor to CVs before Hessian evaluation
- Revised entropy formulation for zero/near-zero CVs in K≥3
- Alternative initial guess that avoids σ¹=0 regions

Affected tests: 3 in `CVMTernaryIntegrationTest` (entropy at equimolar, all CVs positive,
entropy → ln(3) limit).

**MCS double-run on job startup**  
`MCSPhaseModel.create()` runs one MC simulation with default engine parameters (L=4,
nEquil=5000, nAvg=10000), then `MCSCalculationJob` immediately sets the correct
parameters from the request, triggering a second run. For large supercells the first
run is wasted. Fix: provide a lazy-create factory or pass engine parameters upfront.
(Tracked as departure D4 in Refactor_Plan.)

**JavaFX warnings on JDK 25**  
Restricted-method warnings from JavaFX 20.0.1 on JDK 25. Expected; no functional impact.
Will resolve when JavaFX releases JDK 25 support.

### Pending Features

| Item | Priority | Notes |
|------|----------|-------|
| SROs in `EquilibriumState` | MEDIUM | CVM computes them in `getSROs()` but does not package them in the shared result record; MCS does not compute them at all |
| Unified CF indexing across engines | MEDIUM | `correlationFunctions` in `EquilibriumState` has different length/semantics for CVM vs MCS — needs a canonical ncf-indexed representation |
| Manual CEC input dialog | MEDIUM | GUI workflow for entering CEC values by hand and saving to `cec.json` |
| Calculation panel data-readiness gate | LOW | Disable Type 2 panel when cluster data or CECs are missing; show "Missing CECs" / "Missing Clusters" |
| Additional test systems | LOW | Ti-V, Ti-Zr, Fe-Ni CEC data; demonstrates cluster cache reuse |
| Phase diagram plotting | LOW | Temperature/composition scan → G vs x curves |

---

## Architecture Invariants

These must never be broken:

1. `./gradlew build` passes after every commit — zero errors, zero warnings.
2. All 94 tests pass.
3. `ArchitectureBoundaryTest` passes — `domain` never imports `application`, `infrastructure`, or `presentation`.
4. `AllClusterData` is the single source of cluster topology for both CVM and MCS engines.
5. ECI arrays are always **ncf-length** — never tc-length, never padded with zeros.
6. `EquilibriumState` is the only result type returned from both `CVMPhaseModel` and `MCSPhaseModel`.
7. Type 2 calculations never re-run cluster identification — they always load from the disk cache.

---

## How to Run

```bash
# GUI (recommended)
./gradlew run

# CLI
./gradlew run --args='--cli'

# Build only
./gradlew build

# All tests
./gradlew test

# Specific test class
./gradlew test --tests "org.ce.domain.cvm.CVMBinaryIntegrationTest"
```

Requires Java 25 (Gradle auto-downloads if absent).

---

## Key File Locations (Quick Reference)

```
domain/cvm/CVMPhaseModel.java              CVM model — lazy N-R, getEquilibriumState()
domain/mcs/MCSPhaseModel.java              MCS model — lazy MC, getEquilibriumState()
domain/model/result/EquilibriumState.java  Shared immutable result record
domain/model/result/EngineMetrics.java     Sealed: CvmMetrics | McsMetrics

application/job/CVMPhaseModelJob.java      Type 2 background job: loads data, runs CVM
application/job/MCSCalculationJob.java     Type 2 background job: loads data, runs MCS
application/job/CFIdentificationJob.java   Type 1 background job: runs Stages 1-3
application/job/AbstractThermodynamicJob.java  Shared Phases 1+2 (load cluster data + ECI)

application/dto/ThermodynamicCalculationRequest.java  Shared request base (T, x[], systemId)
application/service/CECAssemblyService.java            Assembles higher-order CECs from binary

infrastructure/persistence/AllClusterDataCache.java    Reads/writes all_cluster_data.json
infrastructure/registry/SystemRegistry.java            System registration and lookup
infrastructure/registry/KeyUtils.java                  Canonical key construction
```