# CE — Cluster Expansion / Cluster Variation Method Framework

A Java implementation of the **Cluster Expansion (CE)** and **Cluster Variation
Method (CVM)** pipeline for alloy thermodynamics, with a Monte Carlo Simulation
(MCS) engine path for equilibrium verification.

**GUI Application:** CE Thermodynamics Workbench - Interactive system management and calculation setup. See [PROJECT_STATUS.md](PROJECT_STATUS.md) for full details.

## Latest Updates (Mar 9, 2026 - Evening)

### ECI Standardization — CVM & MCS Unified Loading

Both models now consistently use **ncf-length** (4 values) ECI arrays from the database:

- **CVM:** Loads 4 ECI values (tet, tri, pair1, pair2), uses directly
- **MCS:** Loads 4 ECI values, expands to 6 (tc) by padding point/empty with zeros
- **Database:** `cec.json` stores only non-point CFs (constants don't need optimization)

**Result:**
- ✅ CF values match CVM ↔ MCS within 0.5%
- ✅ Enthalpy agreement within 6% (finite-size + sampling effects)
- ✅ Both use same ECI source, eliminating format inconsistencies

See [PROJECT_STATUS.md](PROJECT_STATUS.md#eci-standardization-mar-9-2026---evening) for implementation details.

---

## Previous Updates (Mar 8, 2026)

### CVM Calculation Bug Fixes — 7 Root Causes Resolved

Step-by-step data-flow verification of the CVM pipeline identified and fixed 7 root causes producing wrong G/H/S values for both binary and ternary systems.

**Root causes fixed:**

| ID | File | Bug | Effect |
|---|---|---|---|
| RC-1 | `CVMFreeEnergy` | Gas constant `R = 1.0` (dimensionless) | G/H/S off by factor of 8.314 |
| RC-2 | `NewtonRaphsonSolverSimple` | Binary-only initial guess `σ = 2·x_B − 1` for K≥3 | Wrong starting point for ternary/quaternary |
| RC-3 | `NewtonRaphsonSolverSimple` | Binary-only point-CF in step limiter for K≥3 | CVs go negative mid-iteration for K≥3 |
| RC-4 | `CVMPhaseModel.isStable()` | Diagonal-only Hessian check | Missed indefinite (saddle-point) solutions |
| RC-5 | `CalculationService.mapCECToCvmECI` | Stripped first 2 CEC elements instead of last 2 | Pair ECIs assigned to tet/triangle types (wrong multiplicities) |
| RC-6 | `CVMEngine` | `moleFractions = {x_B, 1−x_B}` (inverted) | Legacy path used x_A as x_B |
| RC-7 | `CVMPhaseModel.setComposition` | Silently zeroed components 2+ for K>2 | Ternary models behaved as binary without error |

**RC-5 detail (critical):** `cec.json` stores ECIs as `[tet, tri, pair1, pair2, point, empty]`.
`mapCECToCvmECI` was stripping indices 0–1 (tet/tri) instead of the last 2 (point/empty), so
Nb-Ti pair ECIs `[−390, −260]` were assigned to the tetrahedron (mult=6) and triangle (mult=12)
cluster types instead of the correct pair types (mult=4, 3).

**RC-4 detail:** Replaced diagonal check `H[i][i] > 0` with Cholesky decomposition, which is the
correct positive-definiteness test for the full symmetric Hessian.

**New test suite — 69 tests, all passing:**

| Test class | Tests | What it verifies |
|---|---|---|
| `CVMFreeEnergyTest` | 9 | RC-1; R_GAS units, G=H−T·S, gradient, Hessian symmetry |
| `CMatrixBuilderTest` | 7 | C-matrix dimensions, CV positivity, normalization |
| `NewtonRaphsonTest` | 10 | NR convergence, gradient norm, initial-guess correctness |
| `CVMBinaryIntegrationTest` | 14 | Binary pipeline: convergence, G=H−T·S, CV≥0, symmetry, dG/dT |
| `CVMTernaryIntegrationTest` | 10 | Ternary pipeline: convergence, G=H−T·S, CV≥0, entropy physical range |
| `CVMDataFlowVerificationTest` | 9 | Step-by-step ECI mapping and moleFractions data-flow tracing |

---

### JUL Logging — All Three Calculation Types

Structured `java.util.logging` (JUL) is now in place for every calculation path:

| Calculation | Entry point | Files instrumented |
|---|---|---|
| (1) Cluster data generation | `CFIdentificationJob` | 6 |
| (2) MCS simulation | `MCSCalculationJob` | 13 |
| (3) CVM phase model | `CVMPhaseModelJob` | 6 |

**Log levels used:**

| Level | Purpose |
|---|---|
| `WARNING` | Exceptions, failed convergence, null input |
| `INFO` | Job lifecycle (ENTER / EXIT COMPLETED / EXCEPTION) |
| `FINE` | Method ENTER/EXIT with key parameters and results |
| `FINEST` | Per-iteration detail (guarded; off by default) |

**Key classes added / modified:**

- `org.ce.infrastructure.logging.LoggingConfig` — Logger factory; infrastructure/application layers use this
- `org.ce.presentation.gui.view.LogConsolePanel` — GUI log console (existing)
- Domain classes use `Logger.getLogger(X.class.getName())` directly

**Cluster data generation path (Calculation 1):**
- `CFIdentificationJob.run` — INFO ENTER/EXIT with system, numComponents, clusterKey, tcdis, tcf, ncf, elapsed
- `CVMPipeline.identify` — FINE ENTER/EXIT with numComponents, tcdis, tc, tcf, ncf, elapsed
- `ClusterIdentifier.identify` — FINE ENTER/EXIT + per-stage boundary messages; `System.out` removed
- `CFIdentifier.identify` — FINE ENTER/EXIT + per-stage boundary messages; `System.out` removed
- `CMatrixBuilder.build` — FINE ENTER (tc/ncf/numElements) / EXIT (totalCVs)
- `AllClusterDataCache.save/load` — FINE ENTER/EXIT/NOT FOUND; old `[>>]` format replaced

**CVM path (Calculation 3):**
- `CVMPhaseModelJob.run` — INFO ENTER/EXIT (COMPLETED/FAILED); `e.printStackTrace()` replaced with `LOG.warning`
- `CVMPhaseModelExecutor.initializeModel` — FINE ENTER/EXIT; WARNING on failure
- `CalculationSetupPanel.runCVMPhaseModelCalculation` — FINE ENTER/EXIT; WARNING on prepare failure
- `CalculationService.prepareCVMModel` — EXIT enhanced with tcdis, tcf, ncf, ECI length
- `CVMPhaseModel.logMinimizationSuccess/Failure` — `System.out/err` replaced with FINE/WARNING LOG
- `CVMPhaseModel.ensureMinimized` — FINE log before `minimize()` call
- `NewtonRaphsonSolverSimple.minimize` — FINE ENTER; FINEST per-iteration (guarded); FINE CONVERGED; WARNING SINGULAR / STALLED / NOT CONVERGED

**Cluster data now version-controlled:**
- Removed `data/cluster_cache/` from `.gitignore` so pre-computed cluster data is tracked in git

### Package Consolidation (Mar 8, 2026)

Reduced the project from 47 packages to 32 (32% reduction) by merging single/two-file packages into their logical neighbors. The 4-layer architecture (domain / application / infrastructure / presentation) is unchanged.

Key merges:
- `application.{cvm,mcs,pipeline,validation}` consolidated into `application.usecase`
- `application.service` (listener interface) merged into `application.port`
- `infrastructure.{cache,eci,key,job,adapter,config}` merged into their logical neighbors (`persistence`, `data`, `registry`, `service`, `context`)
- `domain.identification.cf` merged into `domain.identification.cluster`
- `domain.mcs.event` merged into `domain.mcs`
- `domain.model.cvm` merged into `domain.cvm`

---

### Phase 6: CVM Phase Model - Model-Centric Thermodynamic API ✅
A new `CVMPhaseModel` class provides a clean, user-friendly API for thermodynamic queries:

**Features:**
- **Create once, query indefinitely:** Single model manages all parameter changes
- **Automatic re-minimization:** Changing T, x, or CECs triggers re-optimization only when needed
- **Smart caching:** Results cached; multiple queries don't re-compute
- **K-agnostic:** Works for binary (K=2), ternary (K≥3), any number of components
- **Thread-safe:** Built-in synchronization for concurrent access
- **Expression consistency fix (Mar 6, 2026):** Newton-Raphson now evaluates `G`, `dG/du`, and `d²G/du²` through `CVMFreeEnergy` (single source of truth) and includes full per-iteration trace (`CF` + `dG/du`) in GUI logs

**Usage:**
```java
CVMPhaseModel model = CVMPhaseModel.create(context, eci, 1000.0, 0.5);

// Temperature scan (efficient: one model, 100 parameter changes)
for (double T = 300; T <= 1500; T += 100) {
    model.setTemperature(T);
    System.out.println(model.getEquilibriumG());  // Auto-minimizes
}

// Get full state (G, H, S, CFs, CVs, SROs, stability, convergence)
CVMPhaseModel.EquilibriumState state = model.getEquilibriumState();
```

See [Phase 6 details](#current-architecture) below.

---

### Phase 5: Multi-Component CVM Solver Generalization (K≥3) ⏳

- **Binary CVM Solver:** All 13 tests **PASSING** — Newton-Raphson converges in <10 iterations
  - Point correlation function (CF) ordering corrected using `cfBasisIndices`
  - Random-state CV verification implemented and passing
  - Entropy computation at random state validates to ln(K) formula
  - Hessian computation well-conditioned and stable

- **Multi-Component API Generalized:** Signature changed from binary-only to K-component
  - Old: `solve(double composition, ...)` → binary shorthands
  - New: `solve(double[] moleFractions, int K, ...)` → K-component flexible API
  - Backward-compatible binary wrapper maintains old API

- **Ternary System (K=3):** 8/11 tests passing, convergence issue identified
  - Root cause: Hessian ill-conditioning due to zero cluster variables (CVs) at random state
  - For equimolar ternary with basis {-1, 0, 1}, many CFs = 0 because σ¹ = 0
  - Zero CVs trigger smooth entropy extension (for CV < 1e-6) with invEff = 1/EPS = 1e6
  - This creates singular/ill-conditioned Hessian matrix
  - NR solver oscillates at ~1e-8 gradient norm instead of converging to 1e-10

- **Next Phase:** Fix Hessian computation for ternary — likely requires CV regularization or revised entropy formulation for K≥3

### Previous Update (Mar 6, 2026)

### MCS Logging (Mar 6–7, 2026)
Full JUL instrumentation for the MCS calculation path (13 files): job lifecycle, method ENTER/EXIT, per-step FINEST hooks, and result summaries. See PROJECT_STATUS.md for details.

### MCS Energy Tracking Optimization (Feb 28, 2026)
**Performance improvement: O(1) per step instead of O(N²) recalculation** - Implemented true delta-energy (ΔE) accumulation where energy updates only on accepted MC moves. The `ExchangeStep.attempt()` and `FlipStep.attempt()` methods now return the energy change directly instead of a boolean, enabling per-step accumulation. Verified correct with periodic full-energy recalculation (✓ MATCH at multiple time points, zero numerical drift). Threading synchronization fixed for background MCS execution.

### Previous Update (Feb 27, 2026)
**Critical bug fix for correlation function (CF) normalization** - The CF calculation formula in MCSampler was incorrect, causing CFs to scale improperly with supercell size and orbit symmetry. This has been fixed and validated. See [CF_NORMALIZATION_FIX_SUMMARY.md](CF_NORMALIZATION_FIX_SUMMARY.md) for complete analysis.

---

## Quick Start

### GUI Application (Recommended)
```bash
./gradlew run
```

Launch the CE Thermodynamics Workbench with:
- System registry and management
- Background cluster/CF identification
- Interactive calculation setup
- Real-time job monitoring

### Programmatic API

#### CVM Phase Model (Recommended - Mar 2026)
```java
// Create a thermodynamic model for a system (single API path)
CVMPhaseModel model = service.prepareCVMModel(request).getContextOrThrow();

// Query equilibrium properties (auto-minimizes on first call)
double G = model.getEquilibriumG();  // Gibbs energy (J/mol)
double S = model.getEquilibriumS();  // Entropy (J/(mol·K))

// Change temperature → auto-minimizes at new T
model.setTemperature(1100.0);
double newG = model.getEquilibriumG();  // Different from before

// Scan composition (efficient: reuses model)
for (double x = 0; x <= 1.0; x += 0.1) {
    model.setComposition(x);
    System.out.println("x=" + x + " : stable=" + model.isStable());
}

// Get full equilibrium state
CVMPhaseModel.EquilibriumState state = model.getEquilibriumState();
// Contains: T, x, G, H, S, CFs, gradients, iteration count, timing
```

**Key Features:**
- **Lazy re-minimization:** Only re-computes when parameters change AND queried
- **Smart caching:** Multiple queries use cached results (no redundant solver calls)
- **Parameter mutations:** Change T, x, CECs at any time
- **K-agnostic:** Works for binary, ternary, quaternary systems
- **Thread-safe:** Built-in synchronization

#### Manual CVM Pipeline (Advanced - Legacy)
```java
// Cluster identification
CVMConfiguration config = CVMConfiguration.builder()
    .disorderedClusterFile("cluster/A2-T.txt")
    .orderedClusterFile("cluster/A2-T.txt")
    .disorderedSymmetryGroup("A2-SG")
    .orderedSymmetryGroup("A2-SG")
    .numComponents(2)
    .build();

// Run three-stage identification pipeline
AllClusterData allData = CVMPipeline.identify(config);

// Access results
ClusterIdentificationResult stage1 = allData.getStage1();
CFIdentificationResult stage2 = allData.getStage2();
CMatrixResult stage3 = allData.getStage3();
```

---

## Directory Structure

```
ce/
├── app/src/              # Application source code (Gradle module)
│   ├── main/java/        # Java source files (org.ce.*)
│   ├── main/resources/   # Static configs (cluster defs, YAML, symmetry)
│   └── test/java/        # Unit tests + examples + integration tests
│
├── data/                 # Runtime data (project root)
│   └── cluster_cache/    # Pre-computed cluster results (version-controlled JSON)
│
└── docs/                 # Design documents
```

- **`app/src/`**: All application code and static configuration files
- **`data/cluster_cache/`**: Cluster identification results — version-controlled so pre-computed data ships with the repo

## Documentation

Project documentation is organized by audience and purpose:

**Essential documents (start here):**
- `README.md`: Project overview and quick start guide (this file)
- `PROJECT_STATUS.md`: Current implementation status, known issues, and next steps
- `ARCHITECTURE_CONTRACT.md`: Enforceable layer/dependency rules

**Design & Implementation reference:**
- `DESIGN_AND_MONITORING_REFERENCE.md`: GUI design guide + MCS real-time monitoring design
- `PERFORMANCE_OPTIMIZATION_REFERENCE.md`: MCS delta-energy optimization technical details
- `docs/extracted-mathematica-functions.md`: Algorithm reference from source derivations

---

## Package Structure

Packages follow the **data flow** exactly — each package owns one
transformation stage and produces a well-defined output consumed by the next.

```
org.ce
├── domain                        Core business logic and immutable models
│   ├── cvm                       CVM engine, phase model, solvers, free energy (24 files)
│   ├── identification            Cluster/CF identification pipeline
│   │   ├── cluster               Stage 1 cluster + Stage 2 CF identification (6 files)
│   │   ├── engine                Enumeration kernel (5 files)
│   │   ├── geometry              Vector/site/cluster geometry (7 files)
│   │   ├── result                Identification result types (5 files)
│   │   ├── subcluster            Sub-cluster generation (5 files)
│   │   └── symmetry              Symmetry operations (4 files)
│   ├── mcs                       MCS engine, samplers, lattice, steps (18 files)
│   ├── model                     Shared data/result models
│   │   ├── data                  AllClusterData, EmbeddingData, etc. (6 files)
│   │   └── result                CalculationResult types (6 files)
│   ├── port                      Domain port interfaces (4 files)
│   └── system                    System identity/status value types (2 files)
├── application                   Use-case orchestration and app-level jobs
│   ├── dto                       Data transfer objects (3 files)
│   ├── job                       Background job contracts + orchestration jobs (6 files)
│   ├── port                      Application port interfaces + listener (5 files)
│   └── usecase                   CVM/MCS use cases, pipeline, validation (5 files)
├── infrastructure                Technical adapters and I/O concerns
│   ├── context                   Calculation contexts (5 files)
│   ├── cvm                       CVM executor + examples (3 files)
│   ├── data                      Metadata loaders + ECI loading (2 files)
│   ├── input                     Cluster/symmetry file parsers (3 files)
│   ├── logging                   JUL LoggingConfig factory + log routing (2 files)
│   ├── mcs                       MCS executor + adapters (2 files)
│   ├── persistence               Cache, serializer, repository adapters (6 files)
│   │   └── migration             Schema migration infrastructure (5 files)
│   ├── registry                  System/result repositories + key utils (3 files)
│   └── service                   CalculationService + job manager + progress adapters (4 files)
└── presentation                  GUI/CLI user interfaces
    ├── cli                       Command-line interface (2 files)
    └── gui                       JavaFX application (1 file)
        ├── component             Dialogs, charts, inspectors (7 files)
        ├── model                 MVC model layer (2 files)
        └── view                  Panels and views (6 files)
```

### Dependency rule

Dependencies flow **strictly downward**. No package imports anything from a
package that appears later in the flow:

```
org.ce.presentation
  ↓
org.ce.application
  ↓
org.ce.domain

org.ce.infrastructure -> implements ports used by org.ce.application/org.ce.domain
```

---

## Data Flow

```
 ┌─────────────────────────────────────────────────────────────┐
 │  INPUT FILES   org.ce.input                                  │
 │  cluster/A2-T.txt  ·  symmetry/A2-SG.txt  ·  ECI file      │
 └──────────────────────────┬──────────────────────────────────┘
                            │  List<Cluster>  +  List<SymmetryOperation>
                            ▼
 ┌─────────────────────────────────────────────────────────────┐
 │  STAGE 1 — CLUSTER IDENTIFICATION                            │
 │  org.ce.identification.cluster                               │
 │                                                              │
 │  1a. HSP clusters (binary basis)                             │
 │      → Nij table  →  Kikuchi-Baker coefficients              │
 │  1b. Phase clusters (binary basis)                           │
 │      → classify into HSP types  →  lc, mh                   │
 └──────────────────────────┬──────────────────────────────────┘
                            │  ClusterIdentificationResult
                            │  { tcdis, kbCoeff[], nijTable, lc, mh }
                            ▼
 ┌─────────────────────────────────────────────────────────────┐
 │  STAGE 2 — CF IDENTIFICATION                                 │
 │  org.ce.identification.cluster                               │
 │                                                              │
 │  2a. HSP CFs  (n-component basis)                            │
 │  2b. Phase CFs  →  classify  →  groupCFData                  │
 │      →  lcf, tcf, nxcf, ncf                                  │
 └──────────────────────────┬──────────────────────────────────┘
                            │  CFIdentificationResult
                            │  { lcf[][], tcf, GroupedCFResult }
                            │
              ┌─────────────┴──────────────┐
              ▼                            ▼
 ┌──────────────────────┐    ┌─────────────────────────────────┐
 │  CVM PATH            │    │  MCS PATH                        │
 │  org.ce.cvm          │    │  org.ce.mcs                      │
 │                      │    │                                  │
 │  Stage 3: C-Matrix   │    │  EmbeddingGenerator              │
 │  Stage 4: F = H − TS │    │  EmbeddingData  (supercell)      │
 │  Stage 5: NIM solver │    │  LatticeConfig                   │
 │                      │    │  MCEngine                        │
 └──────────┬───────────┘    │  MCSampler                       │
            │  CVMSolverResult└────────────────┬────────────────┘
            │  { u[], G, H, S }               │  MCResult
            └──────────────┬──────────────────┘
                           ▼
                    org.ce.app   (RunHybrid, comparison)
```

---

## Package Reference

### `org.ce.input`
Reads physical problem description from files.

| Class | Role |
|---|---|
| `InputLoader` | Top-level API: `parseClusterFile`, `parseSymmetryFile` |
| `ClusterParser` | Parses `cluster/A2-T.txt` → `List<Cluster>` (A2 = Pearson structural type) |
| `SpaceGroupParser` | Parses `symmetry/A2-SG.txt` → `List<SymmetryOperation>` (A2 = Pearson structural type) |

---

### `org.ce.identification.engine`
The shared enumeration kernel. All geometry, symmetry, and cluster-counting
algorithms live here. No dependency on any other `org.ce.*` package.

**Domain objects:** `Vector3D`, `Site`, `Sublattice`, `Cluster`, `ClusterType`, `MaxClusterSet`

**Symmetry:** `SymmetryOperation`, `OrbitUtils`, `SpaceGroup`, `PointGroup`, `NormalizerCalculator`

**Primitives:** `AffineOp`, `Tolerance`

**Sub-cluster generation:** `SubClusterGenerator`, `DecoratedSubClusterGenerator`

**Enumeration:** `BasisSymbolGenerator`, `ClusCoordListGenerator`, `ClusCoordListResult`

**Classification:** `OrderedToDisorderedTransformer`, `OrderedClusterClassifier`, `ClassifiedClusterResult`

**CF grouping:** `CFGroupGenerator`, `GroupedCFResult`, `CFGroupingResult`, `CFGroupingUtils`

---

### `org.ce.identification.cluster` — Stages 1 & 2
Cluster identification (Stage 1, component-independent) and CF identification (Stage 2, component-dependent).

| Class | Stage | Role | Mathematica |
|---|---|---|---|
| `ClusterIdentifier` | 1 | Stage 1 orchestrator | — |
| `NijTableCalculator` | 1 | `nij[i][j]` = sub-cluster containment counts | `getNijTable` |
| `KikuchiBakerCalculator` | 1 | KB entropy weights via inclusion-exclusion recurrence | `generateKikuchiBakerCoefficients` |
| `ClusterIdentificationResult` | 1 | Immutable result: `tcdis, kbCoeff[], nijTable[][], lc[], mh[][]` | — |
| `CFIdentifier` | 2 | Stage 2 orchestrator | — |
| `CFIdentificationResult` | 2 | Immutable result: `lcf[][], tcf, nxcf, ncf, GroupedCFResult` | — |

---

### `org.ce.cvm` *(planned)*
CVM free-energy engine.

| Class | Stage | Role |
|---|---|---|
| `SiteListBuilder` | 3 | Unique site coords → integer indices |
| `RMatrixCalculator` | 3 | R-matrix: p[site][elem] → s[α][site] |
| `PRulesBuilder` | 3 | Substitution rules for occupation operators |
| `CFSiteOpListBuilder` | 3 | Decorated sub-cluster → site-operator list |
| `SubstituteRulesBuilder` | 3 | Product of site operators → CF symbol lookup |
| `CMatrixBuilder` | 3 | Core C-matrix computation (`genCV`) |
| `CMatrixResult` | 3 | Immutable: `cmat, lcv, wcv` |
| `ClusterVariables` | 4 | `cv = cmat · uList` |
| `EnthalpyCalculator` | 4 | `H = Σ m·ECI·CF` |
| `EntropyCalculator` | 4 | `S = −k_B Σ kb·m·Σ wcv·cv·ln(cv)` |
| `FreeEnergyFunctional` | 4 | `F = H − T·S` with gradient |
| `NaturalIterationSolver` | 5 | NIM: iterate u until convergence |
| `CVMEngine` | 5 | Top-level orchestrator |
| `CVMEngineResult` | 5 | `{ u[], F, Hmix, Smix }` |

---

### `org.ce.mcs`
Monte Carlo engine path.

| Class | Status | Role |
|---|---|---|
| `EmbeddingGenerator` | Done | All cluster instances in L×L×L supercell |
| `EmbeddingData` | Done | Three views: flat, by-site, by type+site |
| `Embedding` | Done | One cluster instance: int[] site indices |
| `ClusterTemplate` | Done | Displacement vectors for supercell tiling |
| `Vector3DKey` | Done | HashMap-compatible Vector3D wrapper |
| `LatticeConfig` | Done | Flat int[] spin array, length N = 2·L³ |
| `LocalEnergyCalc` | Done | ΔE for site i via EmbeddingData + ECIs |
| `ExchangeStep` | Done | Canonical Metropolis step (conserves composition) |
| `FlipStep` | Done | Grand-canonical step (single spin flip) |
| `MCEngine` | Done | Metropolis loop, equilibration + averaging phases |
| `MCSampler` | Done | Running averages: CFs, E, E² |
| `MCSRunner` | Done | Top-level orchestrator → MCResult |
| `MCResult` | Done | energyPerSite, heatCapacity, acceptRate |
| `MCSUpdate` | Done | Application event model for MCS progress |

---

### `org.ce.application.usecase`
Use-case orchestrators, pipeline API, and configuration.

| Class | Role |
|---|---|
| `CVMPipeline` | Single-call orchestrator: `CVMPipeline.identify(config)` returns `AllClusterData` |
| `CVMConfiguration` | Builder for all pipeline inputs |
| `CVMCalculationUseCase` | CVM calculation orchestration |
| `MCSCalculationUseCase` | MCS calculation orchestration |
| `ClusterDataValidator` | Validates cluster data completeness |

---

## Implementation Status

| Layer | Packages | Files |
|---|---|---|
| `domain` | 12 | 81 |
| `application` | 4 | 19 |
| `infrastructure` | 10 | 36 |
| `presentation` | 4 | 18 |
| **Total** | **30** | **165** |

The shared identification pipeline (Stages 1 + 2) and the complete MCS engine are fully implemented.
The CVM free-energy path (Stages 3-5) is partially complete via `CVMPhaseModel`.
Package consolidation reduced 47 packages to 32 (Mar 8, 2026).

---

## Resource Files

Crystal structures are identified by **Pearson symbols** (e.g., A2, B2, A1, L1₂, L2₁) which are **single, complete structural designations** combining lattice type and atomic ordering. A2 = disordered BCC; B2 = ordered BCC; A1 = disordered FCC. These are not separated into independent "structure" and "phase" choices:

```
src/main/resources/
├── cluster/
│   ├── A2-T.txt          A2 tetrahedron maximal cluster (disordered BCC)
│   ├── B2-T.txt          B2 tetrahedron cluster (ordered BCC)
│   └── A1-TO.txt         A1 tetrahedron-octahedron cluster (disordered FCC)
└── symmetry/
    ├── A2-SG.txt         A2 space group operations (disordered BCC)
    ├── A2-SG_mat.txt     A2 space group (matrix format)
    ├── B2-SG.txt         B2 space group operations (ordered BCC)
    ├── B2-SG_mat.txt     B2 space group (matrix format)
    └── A1-SG.txt         A1 space group operations (disordered FCC)
```

**Nomenclature:** A2, B2, A1 are Pearson symbols from crystallography. Each is a **complete structural type** — never select "structure=BCC" independently from "phase=A2"; always work with indivisible designations like A2_T or B2_T.

---

## Building

```bash
# Build
./gradlew build

# Run main demo
./gradlew run

# Run a specific example
./gradlew run --args="SimpleDemo"
```

Requires Java 25 toolchain (auto-downloaded by Gradle if not present).
