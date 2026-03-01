# CE — Cluster Expansion / Cluster Variation Method Framework

A Java implementation of the **Cluster Expansion (CE)** and **Cluster Variation
Method (CVM)** pipeline for alloy thermodynamics, with a Monte Carlo Simulation
(MCS) engine path for equilibrium verification.

**GUI Application:** CE Thermodynamics Workbench - Interactive system management and calculation setup. See [PROJECT_STATUS.md](PROJECT_STATUS.md) for full details.

## 🔧 Latest Updates (Mar 1, 2026)

### Phase 5: Multi-Component CVM Solver Generalization (K > 2)
**Status: Binary (K=2) Complete ✅ | Ternary (K≥3) In Progress**

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

### Previous Update (Feb 28, 2026)

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
```java
// Build a configuration
CVMConfiguration config = CVMConfiguration.builder()
    .disorderedClusterFile("cluster/A2-T.txt")
    .orderedClusterFile("cluster/A2-T.txt")
    .disorderedSymmetryGroup("A2-SG")
    .orderedSymmetryGroup("A2-SG")
    .numComponents(2)
    .build();

// Run the two-stage identification pipeline
CVMResult result = CVMPipeline.identify(config);
result.printDebug();
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
│   └── cluster_cache/    # Pre-computed cluster results (auto-generated JSON)
│
└── docs/                 # Design documents
```

- **`app/src/`**: All application code and static configuration files
- **`data/cluster_cache/`**: Runtime-generated cluster identification results

---

## Package Structure

Packages follow the **data flow** exactly — each package owns one
transformation stage and produces a well-defined output consumed by the next.

```
org.ce
├── input                         Stage 0 — Read files → domain objects
├── identification
│   ├── geometry                  Foundational types — Cluster, Site, Vector3D, Sublattice
│   ├── symmetry                  SpaceGroup, SymmetryOperation, OrbitUtils
│   ├── result                    Stage output types — ClusCoordListResult, ClassifiedClusterResult, etc.
│   ├── subcluster                Sub-cluster enumeration and classification
│   ├── engine                    Generators — ClusCoordListGenerator, CFGroupGenerator
│   ├── cluster                   Stage 1 — Cluster identification (component-independent)
│   └── cf                        Stage 2 — CF identification (component-dependent)
├── cvm                           Stage 3-5 — CVM free-energy path
├── mcs                           MCS path — supercell, Metropolis engine
├── core                          Pipeline orchestration (CVMPipeline, CVMConfiguration)
└── workbench
    ├── gui/                      JavaFX GUI (panels, components, models)
    ├── cli/                      Command-line interface
    ├── backend/                  Backend services (jobs, registry, data loading)
    └── util/                     Utilities
        ├── cache/                Cluster data serialization & persistence
        ├── context/              Calculation context holders
        ├── eci/                  ECI loading from database
        ├── key/                  Key-building utilities
        └── mcs/                  MCS execution, monitoring, mapping
```

### Dependency rule

Dependencies flow **strictly downward**. No package imports anything from a
package that appears later in the flow:

```
org.ce.workbench (gui/cli/backend/util)
  ↓
org.ce.core
  ↓
org.ce.cvm  ←(parallel)→  org.ce.mcs
  ↓                              ↓
org.ce.identification.cf
  ↓
org.ce.identification.cluster
  ↓
org.ce.identification.engine
  ↓
org.ce.identification.subcluster / result / symmetry / geometry
  ↓
org.ce.input
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
 │  org.ce.identification.cf                                    │
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
 │  Stage 5: NIM solver │    │  LatticeConfig  [planned]        │
 │  [planned]           │    │  MCEngine       [planned]        │
 └──────────┬───────────┘    │  MCSampler      [planned]        │
            │  CVMEngineResult└────────────────┬────────────────┘
            │  { u[], F, Hmix }               │  MCResult
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
| `ClusterParser` | Parses `cluster/A2-T.txt` → `List<Cluster>` |
| `SpaceGroupParser` | Parses `symmetry/A2-SG.txt` → `List<SymmetryOperation>` |

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

### `org.ce.identification.cluster` — Stage 1
Component-independent cluster identification.

| Class | Role | Mathematica |
|---|---|---|
| `ClusterIdentifier` | Stage 1 orchestrator | — |
| `NijTableCalculator` | `nij[i][j]` = sub-cluster containment counts | `getNijTable` |
| `KikuchiBakerCalculator` | KB entropy weights via inclusion-exclusion recurrence | `generateKikuchiBakerCoefficients` |
| `ClusterIdentificationResult` | Immutable result: `tcdis, kbCoeff[], nijTable[][], lc[], mh[][]` | — |

---

### `org.ce.identification.cf` — Stage 2
Component-dependent CF identification.

| Class | Role | Mathematica |
|---|---|---|
| `CFIdentifier` | Stage 2 orchestrator | — |
| `CFIdentificationResult` | Immutable result: `lcf[][], tcf, nxcf, ncf, GroupedCFResult` | — |

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

### `org.ce.mcs` *(partial)*
Monte Carlo engine path.

| Class | Status | Role |
|---|---|---|
| `EmbeddingGenerator` | ✅ Done | All cluster instances in L×L×L supercell |
| `EmbeddingData` | ✅ Done | Three views: flat, by-site, by type+site |
| `Embedding` | ✅ Done | One cluster instance: int[] site indices |
| `ClusterTemplate` | ✅ Done | Displacement vectors for supercell tiling |
| `Vector3DKey` | ✅ Done | HashMap-compatible Vector3D wrapper |
| `LatticeConfig` | 📋 Planned | Flat int[] spin array, length N = 2·L³ |
| `LocalEnergyCalc` | 📋 Planned | ΔE for site i via EmbeddingData + ECIs |
| `ExchangeStep` | 📋 Planned | Canonical Metropolis step (conserves composition) |
| `FlipStep` | 📋 Planned | Grand-canonical step (single spin flip) |
| `MCEngine` | 📋 Planned | Metropolis loop, equilibration + averaging phases |
| `MCSampler` | 📋 Planned | Running averages: CFs, ⟨E⟩, ⟨E²⟩ |
| `MCSRunner` | 📋 Planned | Top-level orchestrator → MCResult |
| `MCResult` | 📋 Planned | `{ u[], energyPerSite, heatCapacity, acceptRate }` |

---

### `org.ce.app`
Entry points, pipeline API, and examples.

| Class | Role |
|---|---|
| `CVMPipeline` | Single-call orchestrator: `CVMPipeline.identify(config)` |
| `CVMConfiguration` | Builder for all pipeline inputs |
| `CVMResult` | Unified wrapper for Stage 1 + Stage 2 results |
| `CVMPipelineRunner` | Integration test with Nij, KB, and lcf validation |
| `Main` | Demo: A2 binary identification pipeline |
| `examples/SimpleDemo` | Minimal A2 binary example |
| `examples/OrderedPhaseExample` | B2 ordered-phase example |
| `examples/CompareBinaryTernary` | Binary vs ternary CF count comparison |

---

## Implementation Status

| Package | Done | Planned | Total |
|---|---|---|---|
| `org.ce.input` | 3 | 0 | 3 |
| `org.ce.identification.engine` | 19 | 0 | 19 |
| `org.ce.identification.cluster` | 4 | 0 | 4 |
| `org.ce.identification.cf` | 2 | 0 | 2 |
| `org.ce.cvm` | 0 | 14 | 14 |
| `org.ce.mcs` | 5 | 8 | 13 |
| `org.ce.app` | 8 | 3 | 11 |
| **Total** | **41** | **25** | **66** |

The shared identification pipeline (Stages 1 + 2) is fully implemented.
The CVM free-energy path (Stages 3–5) and remaining MCS classes are next.

---

## Resource Files

```
src/main/resources/
├── cluster/
│   ├── A2-T.txt          BCC tetrahedron maximal cluster
│   ├── B2-T.txt          B2 ordered-phase tetrahedron cluster
│   └── A1-TO.txt         FCC tetrahedron-octahedron cluster
└── symmetry/
    ├── A2-SG.txt         A2 (BCC) space group operations
    ├── A2-SG_mat.txt     A2 space group (matrix format)
    ├── B2-SG.txt         B2 space group operations
    ├── B2-SG_mat.txt     B2 space group (matrix format)
    └── A1-SG.txt         A1 (FCC) space group operations
```

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
