# Data Flow Analysis and Package Reorganization Plan

## Executive Summary

This document analyzes the data flow for both CVM (Cluster Variation Method) and MCS (Monte Carlo Simulation) calculations, identifies misalignments between data flow and current package structure, and proposes a reorganized architecture following clean architecture principles.

---

## Part 1: Data Flow Summary

### CVM Calculation Pipeline

```
                          10-Step Pipeline
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  UI LAYER           SERVICE LAYER          EXECUTION LAYER      RESULT      │
│  ─────────────────────────────────────────────────────────────────────────── │
│                                                                              │
│  [CalculationSetupPanel]                                                     │
│         │                                                                    │
│         ▼ (1)                                                                │
│  [CVMCalculationRequest] ───────┐                                            │
│         │                       │                                            │
│         ▼ (2)                   ▼                                            │
│  [CalculationService]     [SystemRegistry]                                   │
│         │                                                                    │
│    ┌────┴────┐                                                               │
│    │         │                                                               │
│    ▼ (3a)    ▼ (3b)                                                          │
│ [AllClusterDataCache]  [ECILoader]                                           │
│    │              │                                                          │
│    └──────┬───────┘                                                          │
│           ▼ (4)                                                              │
│  [CVMCalculationContext] ─────────────────────────────────┐                  │
│           │                                               │                  │
│           ▼ (5)                                           │                  │
│  [CVMCalculationJob] ◄─── [BackgroundJobManager]          │                  │
│           │                                               │                  │
│           ▼ (6)                                           │                  │
│      [CVMExecutor]                                        │                  │
│           │                                               │                  │
│           ▼ (7)                                           │                  │
│      [CVMEngine]                                          │                  │
│           │                                               │                  │
│           ▼ (8)                                           │                  │
│  [NewtonRaphsonSolverSimple]                              │                  │
│           │                                               │                  │
│           ▼ (9)                                           │                  │
│    [CVMSolverResult] ◄────────────────────────────────────┘                  │
│           │                                                                  │
│           ▼ (10)                                                             │
│     [ResultsPanel]                                                           │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘

Key Data Transformations:
─────────────────────────
(1) UI strings → validated DTO
(2) Request → Context (data aggregation)
(3a) clusterKey → AllClusterData (JSON→Java)
(3b) cecKey + T → ECI[] (T-dependent evaluation)
(4) Components → validated context
(5) Context → background task
(6) Context → solver invocation
(7) Context arrays → CVMData structure
(8) CVMData → G minimization (Newton-Raphson)
(9) Solver output → immutable result
(10) Result → formatted text display
```

### MCS Calculation Pipeline

```
                          12-Step Pipeline
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  UI LAYER           SERVICE LAYER         EXECUTION LAYER        RESULT     │
│  ─────────────────────────────────────────────────────────────────────────── │
│                                                                              │
│  [CalculationSetupPanel]                                                     │
│         │                                                                    │
│         ▼ (1)                                                                │
│  [MCSCalculationRequest] ───────┐                                            │
│         │                       │                                            │
│         ▼ (2)                   ▼                                            │
│  [CalculationService]     [SystemRegistry]                                   │
│         │                                                                    │
│    ┌────┴────┐                                                               │
│    │         │                                                               │
│    ▼ (3)     ▼ (4)                                                           │
│ [AllClusterDataCache]  [ECILoader]                                           │
│    │    ↓              │                                                     │
│    │ extracts          │                                                     │
│    │ ClusCoordListResult                                                     │
│    └──────┬────────────┘                                                     │
│           ▼ (5)                                                              │
│  [MCSCalculationContext] ─────────────────────────────────┐                  │
│           │                                               │                  │
│           ▼ (6)                                           │                  │
│  [MCSCalculationJob] ◄─── [BackgroundJobManager]          │                  │
│           │                                               │                  │
│           ▼ (7)                                           │                  │
│      [MCSExecutor]                                        │                  │
│           │                                               │                  │
│           ▼ (8)                                           │                  │
│      [MCSRunner]                                          │                  │
│           │                                               │                  │
│    ┌──────┼──────┬──────────────┐                         │                  │
│    │      │      │              │                         │                  │
│    ▼      ▼      ▼              ▼                         │                  │
│ positions embeddings config  MCEngine ─► MCSampler        │                  │
│                         │       │                         │                  │
│                         │       ▼ (9)                     │                  │
│                         │   [ExchangeStep] (Metropolis)   │                  │
│                         │       │                         │                  │
│                         │       ▼ (10)                    │                  │
│                         │   [MCSUpdate] (per sweep) ──────┼─► [ResultsPanel] │
│                         │       │                         │   (real-time)    │
│                         └───────┤                         │                  │
│                                 ▼ (11)                    │                  │
│                           [MCResult] ◄────────────────────┘                  │
│                                 │                                            │
│                                 ▼ (12)                                       │
│                          [ResultsPanel]                                      │
│                          (final summary)                                     │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘

Key Data Transformations:
─────────────────────────
(1)  UI strings → validated DTO
(2)  Request → Context preparation
(3)  clusterKey → AllClusterData → ClusCoordListResult
(4)  cecKey + T → ECI[]
(5)  Components → validated context
(6)  Context → background task
(7)  Context → MCSRunner builder
(8)  Context → supercell geometry + embeddings
(9)  Config → energy deltas (Metropolis)
(10) Sweep statistics → real-time updates
(11) Sampler statistics → final result
(12) MCResult → formatted summary
```

---

## Part 2: Current Package Structure Problems

### Current Layout
```
org.ce
├── app/                     # Entry point
├── cvm/                     # CVM engine (NewtonRaphsonSolverSimple, CVMEngine)
├── identification/          # Stages 1-3 pipeline
│   ├── cluster/             # Stage 1
│   ├── cf/                  # Stage 2
│   └── cmatrix/             # Stage 3
├── mcs/                     # MCS engine (MCEngine, MCSRunner, etc.)
├── symmetry/                # SymmetryOperations, TransformationMat
└── workbench/
    ├── backend/
    │   ├── dto/             # Request DTOs
    │   ├── job/             # Background jobs
    │   ├── service/         # CalculationService
    │   └── storage/         # ResultRepository
    ├── cli/                 # Command-line interface
    ├── gui/
    │   ├── controller/      # GUI controllers
    │   └── view/            # Panels
    └── util/
        ├── cache/           # AllClusterDataCache
        ├── context/         # Calculation contexts
        ├── cvm/             # CVMExecutor
        ├── eci/             # ECILoader
        ├── mcs/             # MCSExecutor
        └── registry/        # SystemRegistry
```

### Identified Problems

| Problem | Location | Impact |
|---------|----------|--------|
| **Domain model scatter** | `dto/`, `service/`, `cache/`, `context/` | Related types in 4+ packages |
| **Bidirectional dependency** | CVMEngine imports workbench classes | Core depends on presentation |
| **Executor misplacement** | CVMExecutor/MCSExecutor in `util/` | Executors are service-layer, not utility |
| **Context as util** | Contexts in `workbench/util/` | Domain concepts labeled as utilities |
| **Static utility coupling** | AllClusterDataCache, ECILoader | Hard-coded dependencies, untestable |
| **Result type explosion** | CVMSolverResult vs MCResult, different packages | No unified result abstraction |
| **Inconsistent layering** | Jobs know about executors, executors know about engines | Unclear dependency direction |

---

## Part 3: Proposed Package Reorganization

### Target Architecture (Clean Architecture aligned with Data Flow)

```
org.ce
├── domain/                           # CORE - No dependencies on other layers
│   ├── model/                        # Pure domain entities
│   │   ├── system/                   # SystemIdentity, ComponentSpec
│   │   ├── cluster/                  # Cluster, ClusterType, Embedding
│   │   ├── thermodynamics/           # Temperature, Composition (value objects)
│   │   └── result/                   # CalculationResult (sealed), CVMResult, MCSResult
│   │
│   ├── engine/                       # Pure computation (stateless)
│   │   ├── cvm/                      # CVMEngine, NewtonRaphsonSolver
│   │   ├── mcs/                      # MCEngine, ExchangeStep, MCSampler
│   │   └── common/                   # LinearAlgebraUtils, MathUtils
│   │
│   └── identification/               # Cluster/CF identification pipeline
│       ├── stage1/                   # ClusterIdentifier
│       ├── stage2/                   # CFIdentifier
│       └── stage3/                   # CMatrixBuilder
│
├── application/                      # USE CASES - Orchestration
│   ├── calculation/                  # Calculation orchestration
│   │   ├── CVMCalculationUseCase.java
│   │   ├── MCSCalculationUseCase.java
│   │   └── CalculationProgressPort.java  # Interface for progress
│   │
│   ├── identification/               # Identification orchestration
│   │   └── IdentificationUseCase.java
│   │
│   ├── request/                      # Input DTOs (commands)
│   │   ├── CVMCalculationRequest.java
│   │   └── MCSCalculationRequest.java
│   │
│   └── port/                         # Interfaces to infrastructure
│       ├── ClusterDataRepository.java   # Interface for data access
│       ├── ECIRepository.java
│       └── SystemRepository.java
│
├── infrastructure/                   # ADAPTERS - External integrations
│   ├── persistence/                  # Data loading/storage
│   │   ├── cache/                    # AllClusterDataCache (implements ClusterDataRepository)
│   │   ├── eci/                      # ECILoader (implements ECIRepository)
│   │   └── registry/                 # SystemRegistry (implements SystemRepository)
│   │
│   ├── context/                      # Calculation contexts (state assembly)
│   │   ├── CVMCalculationContext.java
│   │   └── MCSCalculationContext.java
│   │
│   └── resource/                     # File/classpath resource loading
│       └── ResourceLoader.java
│
├── presentation/                     # UI LAYER
│   ├── gui/
│   │   ├── view/                     # Panel components
│   │   │   ├── CalculationSetupPanel.java
│   │   │   ├── ResultsPanel.java
│   │   │   └── SystemRegistryPanel.java
│   │   ├── controller/               # GUI controllers
│   │   └── adapter/                  # Implements application ports
│   │       └── ResultsPanelProgressAdapter.java
│   │
│   └── cli/                          # Command-line interface
│
└── app/                              # APPLICATION BOOTSTRAP
    ├── CEApplication.java            # Entry point
    └── config/                       # Dependency injection/wiring
        └── ApplicationContext.java
```

### Key Principles

1. **Domain at Center**: `domain/` has ZERO dependencies on other packages
2. **Dependency Rule**: Dependencies point inward (presentation → application → domain)
3. **Port/Adapter Pattern**: `application/port/` defines interfaces, `infrastructure/` implements them
4. **Data Flow Alignment**: Package structure mirrors the 10-12 step pipelines

---

## Part 4: Implementation Plan

### Phase 1: Foundation (Low Risk, High Value) ✅ COMPLETE
**Goal**: Establish domain layer without breaking existing code

| Task | Priority | Effort | Files to Create/Move | Status |
|------|----------|--------|---------------------|--------|
| 1.1 Create empty domain structure | P0 | 1h | Create folders only | ✅ Done |
| 1.2 Move pure math utilities | P0 | 1h | LinearAlgebraUtils, MathUtils | ✅ Done: `domain/engine/common/LinearAlgebra.java` |
| 1.3 Create result abstractions | P0 | 2h | sealed interface CalculationResult | ✅ Done: `domain/model/result/` |
| 1.4 Define repository interfaces | P0 | 2h | ClusterDataRepository, ECIRepository, SystemRepository | ✅ Done: `domain/port/` |

### Phase 2: Infrastructure Adapters ✅ COMPLETE
**Goal**: Create infrastructure adapters wrapping existing caches

| Task | Priority | Effort | Files Created | Status |
|------|----------|--------|---------------|--------|
| 2.1 ClusterDataRepositoryAdapter | P1 | 1h | `infrastructure/persistence/ClusterDataRepositoryAdapter.java` | ✅ Done |
| 2.2 ECIRepositoryAdapter | P1 | 1h | `infrastructure/persistence/ECIRepositoryAdapter.java` | ✅ Done |
| 2.3 SystemRepositoryAdapter | P1 | 1h | `infrastructure/persistence/SystemRepositoryAdapter.java` | ✅ Done |
| 2.4 Delete deprecated ClusterDataCache | P1 | 0.5h | Removed old file | ✅ Done |

### Phase 3: Use Case Layer ✅ COMPLETE
**Goal**: Create application use cases

| Task | Priority | Effort | Description | Status |
|------|----------|--------|-------------|--------|
| 3.1 Create CVMCalculationUseCase | P1 | 4h | Extract from CalculationService + CVMExecutor | ✅ Done: `application/calculation/CVMCalculationUseCase.java` |
| 3.2 Create MCSCalculationUseCase | P1 | 4h | Extract from CalculationService + MCSExecutor | ✅ Done: `application/calculation/MCSCalculationUseCase.java` |
| 3.3 Create CalculationProgressPort | P1 | 1h | Progress reporting interface | ✅ Done: `application/port/CalculationProgressPort.java` |
| 3.4 Create MCSProgressPort | P1 | 1h | MCS-specific real-time updates | ✅ Done: `application/port/MCSProgressPort.java` |
| 3.5 Create IdentificationUseCase | P2 | 3h | Orchestrate Stage 1-2-3 | ⏳ Future work |
| 3.6 Delete empty redundant folders | P1 | 0.5h | Clean up empty packages | ✅ Done: `util/`, `backend/executor/` |
| 3.7 Deprecate old executors | P1 | 0.5h | Mark CVMExecutor/MCSExecutor for removal | ✅ Done: Added @Deprecated annotations |

### Phase 4: Infrastructure Cleanup ✅ COMPLETE
**Goal**: Create dependency injection and application wiring

| Task | Priority | Effort | Description | Status |
|------|----------|--------|-------------|--------|
| 4.1 Create ApplicationContext | P1 | 2h | DI container for service wiring | ✅ Done: `infrastructure/config/ApplicationContext.java` |
| 4.2 CleanupRedundant folders | P1 | 0.5h | Delete empty folders | ✅ Done: `registry/persistence/`, `gui/util/` |
| 4.3 Add context documentation | P2 | 1h | Document context classes | ⏳ Pending |

### Phase 5: Presentation Refactor (Lower Priority) ✅ IN PROGRESS
**Goal**: Clean up GUI layer and integrate new use cases

| Task | Priority | Effort | Description | Status |
|------|----------|--------|-------------|--------|
| 5.1 Create CalculationProgressListenerAdapter | P1 | 1h | Bridge legacy listener to new port | ✅ Done: `presentation/adapter/CalculationProgressListenerAdapter.java` |
| 5.2 Create MCSProgressListenerAdapter | P1 | 1h | MCS-specific adapter with MCSUpdate conversion | ✅ Done: `presentation/adapter/MCSProgressListenerAdapter.java` |
| 5.3 Wire use cases into CalculationService | P1 | 2h | Replace deprecated executors with use cases | ✅ Done: Updated `executeCVM()` and `executeMCS()` methods |
| 5.4 Refactor SystemRegistryPanel (644 lines) | P2 | 6h | Split into SysCreationComponent + validation | ⏳ In Progress |
| 5.5 Extract validation logic | P2 | 2h | Create ClusterDataValidator, ECIValidator components | ⏳ Pending |
| 5.6 Delete deprecated executors | P1 | 0.5h | Remove CVMExecutor, MCSExecutor from util/ | ⏳ Pending (after verification) |

---

## Part 5: Migration Strategy

### Step-by-Step Migration (Preserve Working Code)

```
Phase 1: PARALLEL STRUCTURE
───────────────────────────
Week 1:
├─ Create new domain/ structure (empty)
├─ Copy (not move) domain classes
├─ Update copies to remove bad dependencies
└─ TESTS: All existing tests must pass

Phase 2: REDIRECT IMPORTS
─────────────────────────
Week 2:
├─ Update consumers to import from new locations
├─ Mark old classes @Deprecated
├─ Run full test suite
└─ TESTS: Add tests for new classes

Phase 3: CLEANUP
────────────────
Week 3:
├─ Delete deprecated classes
├─ Remove old packages
├─ Update documentation
└─ TESTS: Final integration test
```

### Risk Mitigation

1. **Create before Delete**: Always create new structure, then migrate
2. **Test Gate**: No migration proceeds without passing tests
3. **Incremental**: One package at a time, not big-bang
4. **Feature Flags**: New paths optional until stable

---

## Part 6: Progress Summary and Next Steps

### Completed Work

#### Phase 1: Domain Foundation ✅
- Created `domain/model/result/` with sealed `CalculationResult` hierarchy
- Created `domain/port/` with repository interfaces (`ClusterDataRepository`, `ECIRepository`, `SystemRepository`)
- Created `domain/engine/common/LinearAlgebra.java` utility class

#### Phase 2: Infrastructure Adapters ✅
- Created `infrastructure/persistence/ClusterDataRepositoryAdapter.java`
- Created `infrastructure/persistence/ECIRepositoryAdapter.java`
- Created `infrastructure/persistence/SystemRepositoryAdapter.java`
- Deleted deprecated `ClusterDataCache.java`

#### Phase 3: Application Use Cases ✅
- Created `application/port/CalculationProgressPort.java` - base progress interface
- Created `application/port/MCSProgressPort.java` - MCS-specific real-time updates
- Created `application/calculation/CVMCalculationUseCase.java` - CVM orchestration
- Created `application/calculation/MCSCalculationUseCase.java` - MCS orchestration
- Deleted empty folders: `org.ce.util/`, `workbench/backend/executor/`
- Added `@Deprecated(forRemoval = true)` to old `CVMExecutor` and `MCSExecutor`

#### Phase 4: Infrastructure Wiring ✅
- Created `infrastructure/config/ApplicationContext.java` - DI container
- Deleted empty folders: `registry/persistence/`, `gui/util/`
- Integrated repository factories with lazy initialization

### Remaining Work

#### Phase 5: Presentation Layer (Refactoring and Integration)
- [x] Create adapter bridge from `CalculationProgressListener` → `CalculationProgressPort`
- [x] Create MCS-specific adapter with `MCSProgressPort` support
- [x] Wire new use cases into existing `CalculationService`
- [ ] Split large GUI panels:
  - [ ] SystemRegistryPanel (644 lines) → SystemCreationPanel + StatusPanel
  - [ ] CalculationSetupPanel (341 lines) → extract validation
  - [ ] ResultsPanel (323 lines) → extract formatting
  - [ ] PeriodicTableSelectionDialog (302 lines) → split into reusable components
- [ ] Extract view models from controllers
- [ ] Mark deprecated packages for removal (core/, input/)

#### Future Improvements
- [ ] Create `IdentificationUseCase` for Stage 1-2-3 orchestration (replaces CVMPipeline)
- [ ] Move engine classes to `domain/engine/` packages
- [ ] Migrate context classes to `infrastructure/context/`
- [ ] Add comprehensive unit tests for new layers
- [ ] Implement MVVM pattern for GUI","oldString": "## Part 6: Immediate Next Steps

### Today (Developer Actions)

1. **Create domain/model/result/** package with:
   ```java
   public sealed interface CalculationResult permits CVMResult, MCSResult {}
   ```

2. **Create application/port/** with repository interfaces:
   ```java
   public interface ClusterDataRepository {
       Optional<AllClusterData> load(String clusterKey);
   }
   ```

3. **Create domain/engine/common/** and move LinearAlgebraUtils

4. **Add @Deprecated to CVMExecutor/MCSExecutor** in util packages

### This Week

- [ ] Complete Phase 1 tasks (foundation)
- [ ] Write tests for new repository interfaces
- [ ] Identify remaining CVMEngine → workbench dependencies
- [ ] Create tracking issue for Phase 2

---

## Appendix: File Movement Map

| Current Location | New Location | Status |
|------------------|--------------|--------|
| `org.ce.cvm.CVMEngine` | `org.ce.domain.engine.cvm.CVMEngine` | ⏳ Future |
| `org.ce.cvm.NewtonRaphsonSolverSimple` | `org.ce.domain.engine.cvm.NewtonRaphsonSolver` | ⏳ Future |
| `org.ce.cvm.CVMSolverResult` | `org.ce.domain.model.result.CVMResult` | ✅ Created (new) |
| `org.ce.mcs.MCEngine` | `org.ce.domain.engine.mcs.MCEngine` | ⏳ Future |
| `org.ce.mcs.MCResult` | `org.ce.domain.model.result.MCSResult` | ✅ Created (new) |
| `org.ce.workbench.backend.dto.*` | `org.ce.application.request.*` | ⏳ Future |
| `org.ce.workbench.util.cache.AllClusterDataCache` | `org.ce.infrastructure.persistence.cache.AllClusterDataCache` | ⏳ Future |
| `org.ce.workbench.util.eci.ECILoader` | `org.ce.infrastructure.persistence.eci.ECILoader` | ⏳ Future |
| `org.ce.workbench.util.context.*` | `org.ce.infrastructure.context.*` | ⏳ Future |
| `org.ce.workbench.util.cvm.CVMExecutor` | `org.ce.application.calculation.CVMCalculationUseCase` | ✅ Created (new) |
| `org.ce.workbench.util.mcs.MCSExecutor` | `org.ce.application.calculation.MCSCalculationUseCase` | ✅ Created (new) |
| `org.ce.workbench.util.registry.SystemRegistry` | `org.ce.infrastructure.persistence.registry.SystemRegistry` | ⏳ Future |

### New Files Created

| Package | File | Description |
|---------|------|-------------|
| `org.ce.domain.port` | `ClusterDataRepository.java` | Repository interface for cluster data |
| `org.ce.domain.port` | `ECIRepository.java` | Repository interface for ECI loading |
| `org.ce.domain.port` | `SystemRepository.java` | Repository interface for system lookup |
| `org.ce.domain.model.result` | `CalculationResult.java` | Sealed interface hierarchy root |
| `org.ce.domain.model.result` | `ThermodynamicResult.java` | Base for thermo results |
| `org.ce.domain.model.result` | `CVMResult.java` | CVM calculation result record |
| `org.ce.domain.model.result` | `MCSResult.java` | MCS calculation result record |
| `org.ce.domain.model.result` | `CalculationFailure.java` | Failure result record |
| `org.ce.domain.engine.common` | `LinearAlgebra.java` | Pure math utilities |
| `org.ce.infrastructure.persistence` | `ClusterDataRepositoryAdapter.java` | Adapts AllClusterDataCache |
| `org.ce.infrastructure.persistence` | `ECIRepositoryAdapter.java` | Adapts ECILoader |
| `org.ce.infrastructure.persistence` | `SystemRepositoryAdapter.java` | Adapts SystemRegistry |
| `org.ce.application.port` | `CalculationProgressPort.java` | Progress reporting interface |
| `org.ce.application.port` | `MCSProgressPort.java` | MCS-specific progress interface |
| `org.ce.application.calculation` | `CVMCalculationUseCase.java` | CVM calculation orchestration |
| `org.ce.application.calculation` | `MCSCalculationUseCase.java` | MCS calculation orchestration |
| `org.ce.infrastructure.config` | `ApplicationContext.java` | DI container for wiring |

### Deleted Files/Folders

| Location | Reason |
|----------|--------|
| `org.ce.util/` | Empty package |
| `workbench/backend/executor/` | Empty package |
| `backend/registry/persistence/` | Empty package |
| `gui/util/` | Empty package |
| `ClusterDataCache.java` | Deprecated, superseded by AllClusterDataCache |
| `org.ce.workbench.util.registry.SystemRegistry` | `org.ce.infrastructure.persistence.registry.SystemRegistry` |

---

## Appendix B: Redundant Code Analysis (Phase 5)

### Legacy Packages (Lower Priority for Removal)

| Package | Purpose | Usage | Recommendation |
|---------|---------|-------|-----------------|
| `org.ce.core/` | Legacy CVM pipeline entry point | `CFIdentificationJob` only | Keep for now; plan `IdentificationUseCase` in Phase 5+ |
| `org.ce.input/` | CSV/file input parsers | Used only by `CVMPipeline` | Keep for now; deprecate after `IdentificationUseCase` |
| `org.ce.workbench.util.cvm.CVMExecutor` | Deprecated CVM execution wrapper | Replaced by `CVMCalculationUseCase` | Mark @Deprecated(forRemoval=true) ✅ Done |
| `org.ce.workbench.util.mcs.MCSExecutor` | Deprecated MCS execution wrapper | Replaced by `MCSCalculationUseCase` | Mark @Deprecated(forRemoval=true) ✅ Done |

### Identified Large GUI Components (Refactoring Candidates)

| Component | Lines | Responsibilities | Refactoring Action |
|-----------|-------|------------------|-------------------|
| `SystemRegistryPanel` | 644 | System form, validation, job monitoring, calc setup | **Extract**: SystemCreationForm + ClusterStatusPanel |
| `StructureModelSelectionDialog` | 481 | Structure/phase/model selection, mapping resolution | **Extract**: StructureModelSelector component |
| `PeriodicTableSelectionDialog` | 302 | Element selection with filtering and caching | Already well-encapsulated; minor cleanup |
| `MCSStatusPanel` | 285 | Real-time MCS progress visualization | **Extract**: EnergyTrendChart + StatisticsIndicator |
| `CalculationSetupPanel` | 341 | Temp, composition, parameters | Well-structured; consider extracting validators |
| `ClusterDataPresenter` | 257 | Cluster data formatting and display | **Extract**: ClusterSummaryPresenter + DetailPresenter |
| `EnergyConvergenceChart` | 236 | Energy trajectory visualization | Already well-encapsulated; good as-is |


