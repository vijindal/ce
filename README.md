# CE — Cluster Expansion / CVM / MCS Thermodynamics Workbench

A Java framework for alloy thermodynamics built around two clearly separated
calculation types.

---

## The Two Calculation Types

```
┌─────────────────────────────────────────────────────────────────────┐
│  TYPE 1 — Data Creation & Management  (runs once, stored on disk)   │
│                                                                       │
│  (a) Cluster Data              (b) CEC Database                      │
│      ─────────────────             ───────────────────────           │
│      Input : structure,            Input : elements, structure,      │
│              CVM model,                    CVM model, K              │
│              K components                                             │
│      Output: AllClusterData        Output: cec.json per system       │
│              → disk cache                  → disk database           │
│                                                                       │
│      Key: (structure, model, K)    Key: (elements, structure,        │
│           e.g. BCC_A2_T_bin              model, K)                   │
│                                         e.g. Nb-Ti_BCC_A2_T         │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  TYPE 2 — Thermodynamic Calculations  (reads from disk, runs fast)   │
│                                                                       │
│  Reads: AllClusterData + CEC database (from Type 1)                  │
│  Input: macro parameters — T (K), x[] (composition)                 │
│                                                                       │
│  Two equilibrium engines, identical external contract:               │
│                                                                       │
│  (a) CVM  ──── Newton-Raphson minimization of F = H − TS             │
│      → G, H, S, CFs, SROs, stability (Hessian)                      │
│                                                                       │
│  (b) MCS  ──── Metropolis Monte Carlo (canonical ensemble)           │
│      → H, Cv, CFs, SROs  (G and S require thermodynamic             │
│        integration — not available from a single MC run)             │
│                                                                       │
│  Shared output type: EquilibriumState                                │
└─────────────────────────────────────────────────────────────────────┘
```

The separation is strict: Type 2 calculations **never** re-run cluster
identification or re-read raw input files — they load pre-computed data from
disk. Type 1 jobs run once and are reused across all subsequent calculations
for any system sharing the same structure/model/K combination.

---

## What Gets Stored on Disk

### Cluster Data Cache — shared by structure/model/K
```
data/cluster_cache/
└── {structure}_{model}_{K}/          e.g. BCC_A2_T_bin, BCC_A2_T_tern
    └── all_cluster_data.json         Stages 1-3: clusters, CFs, C-matrix
```
One cache entry serves all alloy systems with the same crystal structure,
CVM model, and component count. Nb-Ti and Ti-V both use `BCC_A2_T_bin`.

### CEC Database — specific to elements + structure/model/K
```
app/src/main/resources/data/systems/
└── {Elements}_{structure}_{model}/   e.g. Nb-Ti_BCC_A2_T
    ├── cec.json                       ECI array [tet, tri, pair1, pair2]
    └── system_data.json               System metadata
```
CECs are fitted to DFT data and are element-specific. Higher-order (ternary+)
CECs are assembled from binary subsystem CECs via `CECAssemblyService`.

### Static Input Files — bundled with the application
```
app/src/main/resources/
├── cluster/          Maximal cluster definitions per structure (A2-T.txt, etc.)
├── symmetry/         Space group operations per structure (A2-SG.txt, etc.)
└── data/
    ├── elements.yaml
    ├── structure_models.yaml
    └── models/{structure}_{model}/model_data.json
```

---

## System Key Convention

Every system in the workbench is identified by a composite key:

```
{Elements} _ {Structure} _ {Model}
    │              │           │
    │              │           └─ CVM model: T = tetrahedron
    │              └─────────── Pearson symbol: A2 (disordered BCC),
    │                                           B2 (ordered BCC),
    │                                           A1 (disordered FCC)
    └────────────────────────── Elements in canonical order, e.g. Nb-Ti
```

Pearson symbols are **indivisible, complete** structural designations — A2
means disordered BCC and nothing else. Never split them into separate
"lattice" and "phase" components.

| Example key | Meaning |
|---|---|
| `BCC_A2_T_bin` | Cluster data: disordered BCC, tetrahedron model, binary |
| `BCC_A2_T_tern` | Cluster data: disordered BCC, tetrahedron model, ternary |
| `Nb-Ti_BCC_A2_T` | CEC database: Nb-Ti alloy, disordered BCC, tetrahedron |

---

## Data Flow

```
TYPE 1 ─────────────────────────────────────────────────────────────────

 Raw input files          CFIdentificationJob
 (cluster/, symmetry/) ─→ Stage 1: ClusterIdentifier   → tcdis, kb, nij
                           Stage 2: CFIdentifier         → tcf, ncf, lcf
                           Stage 3: CMatrixBuilder        → C-matrix
                           └─→ all_cluster_data.json  (disk cache)

 User-supplied ECIs     → CECManagementPanel / CLI
                           └─→ cec.json              (disk database)

TYPE 2 ─────────────────────────────────────────────────────────────────

 all_cluster_data.json ─┐
 cec.json ──────────────┤  AbstractThermodynamicJob.loadSystemData()
 T, x[] ────────────────┘
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
       CVMPhaseModelJob      MCSCalculationJob
       CVMPhaseModel         MCSPhaseModel
       Newton-Raphson        Metropolis MC
              │                     │
              └──────────┬──────────┘
                         ▼
                  EquilibriumState
                  { T, x[], CFs, H,
                    G? (CVM only),
                    S? (CVM only),
                    Cv? (MCS only),
                    EngineMetrics }
```

---

## Architecture

Four layers with strict downward dependency:

```
presentation  (GUI / CLI)
     ↓
application   (jobs, use-cases, ports, DTOs)
     ↓
domain        (CVM engine, MCS engine, identification pipeline, results)

infrastructure  → implements ports; accesses disk, registry, logging
```

Dependency rule enforced by `ArchitectureBoundaryTest`: `domain` never
imports `application`, `infrastructure`, or `presentation`.

### Type 1 entry points
| Class | Layer | Role |
|---|---|---|
| `CFIdentificationJob` | application/job | Background job: runs Stages 1-3, writes `all_cluster_data.json` |
| `CVMPipeline` | application/usecase | Programmatic API: `CVMPipeline.identify(config)` → `AllClusterData` |
| `CECManagementPanel` | presentation/gui | GUI panel: browse, edit, and assemble CECs |
| `CECAssemblyService` | application/service | Assembles ternary/quaternary CECs from binary subsystems |

### Type 2 entry points
| Class | Layer | Role |
|---|---|---|
| `CVMPhaseModelJob` | application/job | Background job: loads data, creates `CVMPhaseModel`, runs N-R |
| `MCSCalculationJob` | application/job | Background job: loads data, creates `MCSPhaseModel`, runs MC |
| `CVMPhaseModel` | domain/cvm | Domain model: lazy N-R minimization, `getEquilibriumState()` |
| `MCSPhaseModel` | domain/mcs | Domain model: lazy MC simulation, `getEquilibriumState()` |
| `EquilibriumState` | domain/model/result | Unified immutable result record for both engines |

### Shared data loading (both Type 2 jobs)
`AbstractThermodynamicJob.loadSystemData()` handles Phases 1 and 2 identically
for CVM and MCS: load `AllClusterData` from cache, load ncf-length ECI from
the CEC database.

---

## Package Structure

```
org.ce
├── domain
│   ├── cvm                 CVM free-energy engine: C-matrix, N-R solver,
│   │                       CVMPhaseModel, CVMFreeEnergy (24 files)
│   ├── mcs                 MCS engine: lattice, MC steps, sampler,
│   │                       MCSPhaseModel, MCSRunner (18 files)
│   ├── identification
│   │   ├── cluster         Stage 1+2 orchestrators (6 files)
│   │   ├── engine          Enumeration kernel — geometry, symmetry (5 files)
│   │   ├── geometry        Vector3D, Site, Cluster (7 files)
│   │   ├── result          Identification result types (5 files)
│   │   ├── subcluster      Sub-cluster generation (5 files)
│   │   └── symmetry        Space group operations (4 files)
│   ├── model
│   │   ├── data            AllClusterData, CalculationRecord (6 files)
│   │   └── result          EquilibriumState, EngineMetrics (6 files)
│   ├── port                Domain repository interfaces (4 files)
│   └── system              SystemIdentity, SystemStatus (2 files)
├── application
│   ├── dto                 ThermodynamicCalculationRequest, CVMCalculationRequest,
│   │                       MCSCalculationRequest (5 files)
│   ├── job                 AbstractThermodynamicJob, CVMPhaseModelJob,
│   │                       MCSCalculationJob, CFIdentificationJob (7 files)
│   ├── port                DataManagementPort, CalculationProgressListener (5 files)
│   ├── service             CECAssemblyService (1 file)
│   └── usecase             CVMPipeline, CVMConfiguration, ClusterDataValidator (3 files)
├── infrastructure
│   ├── context             ApplicationContext (2 files)
│   ├── cvm                 CVMPhaseModelExecutor, examples (3 files)
│   ├── data                ECILoader, ECIMapper, SystemDataLoader (3 files)
│   ├── input               ClusterParser, SpaceGroupParser, InputLoader (3 files)
│   ├── logging             LoggingConfig, GuiLogHandler (2 files)
│   ├── mcs                 StructureModelMapping (1 file)
│   ├── persistence         AllClusterDataCache, serializer, repository adapters,
│   │                       schema migration (11 files)
│   ├── registry            SystemRegistry, WorkspaceManager, KeyUtils (5 files)
│   └── service             BackgroundJobManager, CalculationCoordinator,
│                           DataManagementAdapter, progress adapters (5 files)
└── presentation
    ├── cli                 CEWorkbenchCLI, DataManagementCLI (3 files)
    └── gui
        ├── component       Dialogs, charts, inspectors (8 files)
        ├── model           MVC model layer (2 files)
        └── view            SystemRegistryPanel, CalculationSetupPanel,
                            CECManagementPanel, ResultsPanel, LogConsolePanel (6 files)
```

---

## Quick Start

### GUI (recommended)
```bash
./gradlew run
```

Launches the CE Thermodynamics Workbench with three main panels:

| Panel | Type | Purpose |
|---|---|---|
| System Registry | Type 1 | Register systems, run cluster identification |
| CEC Management | Type 1 | Browse, edit, and assemble CEC databases |
| Calculation Setup | Type 2 | Run CVM or MCS calculations, view results |

### CLI
```bash
./gradlew run --args='--cli'
```

### Build / Test
```bash
./gradlew build
./gradlew test
```

Requires Java 25 (auto-downloaded by Gradle if absent).

---

## Programmatic API

### Type 1 — Cluster data generation
```java
CVMConfiguration config = CVMConfiguration.builder()
    .disorderedClusterFile("cluster/A2-T.txt")
    .disorderedSymmetryGroup("A2-SG")
    .numComponents(2)
    .build();

AllClusterData allData = CVMPipeline.identify(config);
// Stages 1-3 complete: clusters, CFs, C-matrix all in allData
```

### Type 2 — CVM thermodynamic calculation
```java
// Create model: loads AllClusterData + ECI, runs first N-R minimization
CVMPhaseModel model = CVMPhaseModel.create(cvmInput, eci, 1000.0,
    new double[]{0.5, 0.5});

// Query equilibrium properties
EquilibriumState state = model.getEquilibriumState();
double H = state.enthalpy();
state.gibbsEnergy().ifPresent(G -> System.out.printf("G = %.4f J/mol%n", G));
state.entropy().ifPresent(S  -> System.out.printf("S = %.4f J/(mol·K)%n", S));

// Parameter scan — model re-minimizes lazily on each change
for (double T = 300; T <= 1500; T += 100) {
    model.setTemperature(T);
    System.out.printf("T=%.0f K  G=%.4f J/mol%n", T, model.getEquilibriumG());
}
```

### Type 2 — MCS thermodynamic calculation
```java
// Same inputs as CVM; different internal engine
MCSPhaseModel model = MCSPhaseModel.create(allData, eci, 2, 1000.0,
    new double[]{0.5, 0.5});

EquilibriumState state = model.getEquilibriumState();
double H  = state.enthalpy();
state.heatCapacity().ifPresent(Cv -> System.out.printf("Cv = %.4f%n", Cv));
// state.gibbsEnergy() → OptionalDouble.empty()  (physics boundary for MCS)

// Re-run with different engine parameters
model.setTemperature(1200.0);
model.setSupercellSize(6);
EquilibriumState newState = model.getEquilibriumState(); // re-runs MC once
```

---

## ECI / CEC Convention

CECs are stored in the database in **descending body-count order**:

```
index:  0     1     2      3      4       5
value: [tet,  tri,  pair1, pair2, point,  empty]
```

Both CVM and MCS load exactly `ncf` values (non-point, non-empty cluster
types). For a tetrahedron model `ncf = 4` → `[tet, tri, pair1, pair2]`.
Point and empty cluster ECIs are identically zero in the canonical ensemble
and are never stored or loaded.

---

## Documentation

| File | Purpose |
|---|---|
| `README.md` | This file — architecture overview and API reference |
| `PROJECT_STATUS.md` | Build status, test results, known issues, next steps |
| `Refactor_Plan.md` | Active Phase 10.x refactor plan with open items |
| `ARCHITECTURE_CONTRACT.md` | Layer rules enforced by `ArchitectureBoundaryTest` |
| `CLAUDE_SESSION_HANDOFF.md` | Session continuity context for AI-assisted development |
| `docs/extracted-mathematica-functions.md` | Algorithm reference from original Mathematica derivations |

---

**Build:** Java 25 · JavaFX 22.0.2 · Gradle 9.3.1  
**Repository:** vijindal/ce  
**License:** See LICENSE