# Type 1 Architecture — Review & Refactor Plan (Revised)

**Date:** March 13, 2026

---

## 1. What Type 1 Is — Two Independent Branches, Each Needing a Job

```
TYPE 1(a) — Cluster Data Management
  Input  : structure + phase + model + K  (element-independent)
  Process: Stages 1-3 identification pipeline
  Output : AllClusterData  →  all_cluster_data.json
  Key    : BCC_A2_T_bin

TYPE 1(b) — CEC Database Management
  Input  : elements + structure + phase + model  (element-specific)
  Process: (i) manual entry/edit  OR  (ii) assembly from subsystem CECs + basis scaling
  Output : double[] ECIs  →  cec.json
  Key    : Nb-Ti_BCC_A2_T
```

Both run once per key and write to disk. Type 2 reads both.

---

## 2. The Central Problem — Job Symmetry Gap

Type 1 has **two branches** but only **one background job**:

| Branch | Background Job | Through `BackgroundJobManager`? | GUI thread safe? |
|--------|---------------|--------------------------------|-----------------|
| 1(a) Cluster data | `CFIdentificationJob extends AbstractBackgroundJob` | ✅ Yes | ✅ Yes |
| 1(b) CEC database | **None** | ❌ No job at all | ❌ Assembly runs on JavaFX thread |

`CECManagementPanel` does everything inline — assembly is a button handler that does
disk I/O, domain computation, and multiple CEC loads all synchronously on the UI thread.
There is no `BackgroundJob` subclass, nothing goes through `BackgroundJobManager`,
no progress reporting, no cancellation, no consistent completion event.

**The primary goal of this refactor is to give Type 1(b) the same job-based
architecture that Type 1(a) already has.**

---

## 3. Target Job Architecture — Symmetric

```
TYPE 1(a)                              TYPE 1(b)
─────────────────────────────────      ─────────────────────────────────
CFIdentificationJob                    CECAssemblyJob          (NEW)
  extends AbstractBackgroundJob          extends AbstractBackgroundJob
  submitted via BackgroundJobManager     submitted via BackgroundJobManager
  reports via IdentificationListener     reports via CECOperationListener (NEW)
  result: AllClusterData                 result: AssemblyResult            (NEW)

Manual entry/edit: no job needed
  (instant save — stays as button handler
   but routed through CECManagementService)
```

---

## 4. Secondary Problems — Both Branches

Once the job gap is closed, a second layer of issues remains. These are about
layering and wiring — neither panel should be reaching into infrastructure directly.

### 4(a) — Cluster Data side

**P1 — `SystemRegistryPanel` owns too much logic (HIGH)**
The panel (~600 lines) directly: resolves keys (duplicating KeyUtils), checks
`AllClusterDataCache.exists()`, builds and submits `CFIdentificationJob`, manages
the identification input dialog (inner class + private method), and spawns a raw
polling thread to detect job completion:
```java
Thread resultMonitor = new Thread(() -> {
    while (...) { Thread.sleep(100); if (wasRunning && !isRunning) { ... } }
});
```
All orchestration belongs in an `IdentificationCoordinator`. The panel should only
hold form fields and delegate.

**P2 — Polling thread instead of event-driven (HIGH)**
`BackgroundJobManager.JobManagerListener.onJobFinished(jobId)` exists and the panel
implements it — but only to log a line, because it has no way to map `jobId` back to
the submitted `CFIdentificationJob`. A coordinator with `Map<jobId, CFIdentificationJob>`
solves this without any thread.

**P3 — No intermediate progress output (MEDIUM)**
The pipeline runs silently for 10-30 seconds. CVM and MCS already write rich
intermediate output via `CalculationProgressListener`. Type 1(a) needs an equivalent.

**P4 — Identification dialog embedded and duplicated (MEDIUM)**
`showIdentificationDialog()` and `IdentificationInput` inner class live inside the panel,
called from two nearly identical code paths (Create System / Create Cluster).
Should be a standalone `IdentificationInputDialog` component.

**P5 — `CVMPipeline` / `CVMConfiguration` naming and package (LOW)**
Both names imply CVM-only. The pipeline feeds CVM and MCS equally.
Both live in `application/usecase/` alongside Type 2 code — wrong package.

### 4(b) — CEC Database side

**P6 — `CECManagementPanel` calls infrastructure directly (HIGH)**
The panel (~760 lines) calls `SystemDataLoader`, `AllClusterDataCache`, and
`CECAssemblyService` directly. No application-layer service mediates CEC I/O.
Bypasses `DataManagementPort` that Type 2 uses for the same data.

**P7 — Assembly result stored as mutable panel fields (MEDIUM)**
After assembly, four fields on the panel hold the result:
```java
private int[] assemblyCfOrderMap;
private Map<Integer, double[]> assemblyTransformedByOrder;
private AllClusterData assemblyTargetData;
private SystemIdentity currentAssemblyTarget;
```
These go stale if the user changes the target between Assemble and Save.
Should be an immutable `AssemblyResult` record.

**P8 — Workspace path hardcoded in two places (MEDIUM)**
Both the Browser save and Assembly save hardcode
`Paths.get(System.getProperty("user.home")).resolve(".ce-workbench")`.
`SystemDataLoader.setWorkspaceRoot()` manages this centrally — the panel should
never be constructing workspace paths.

---

## 5. Target Architecture — Full Picture

```
presentation/gui/
  view/SystemRegistryPanel              UI only → delegates to IdentificationCoordinator
  view/CECManagementPanel               UI only → delegates to CECManagementCoordinator
  component/IdentificationInputDialog   NEW: extracted standalone dialog
  view/ClusterDataPresenter             unchanged

application/
  job/AbstractBackgroundJob             unchanged
  job/CFIdentificationJob               + IdentificationProgressListener wiring
  job/CECAssemblyJob                    NEW: background job for CEC assembly
  pipeline/IdentificationPipeline       RENAMED from CVMPipeline, moved package
  pipeline/IdentificationRequest        RENAMED from CVMConfiguration, moved package
  port/IdentificationProgressListener   NEW: stage callbacks for 1(a)
  port/CECOperationListener             NEW: assembly callbacks for 1(b)
  service/CECAssemblyService            unchanged (pure domain math)
  service/CECManagementService          NEW: single place for CEC load/save/validate

infrastructure/
  service/CalculationCoordinator        unchanged (Type 2)
  service/IdentificationCoordinator     NEW: cluster data job lifecycle
  service/CECManagementCoordinator      NEW: CEC assembly job lifecycle + save
```

---

## 6. Refactor Steps

### T1.1 — Create `CECAssemblyJob` ⟵ closes the central job gap

New `application/job/CECAssemblyJob.java extends AbstractBackgroundJob`. Runs:
1. Load `AllClusterData` for target system
2. Classify CFs by order (`CECAssemblyService.classifyCFsByOrder`)
3. For each subsystem order: load CEC, call `transformToTarget`, accumulate
4. Produce immutable `AssemblyResult` record:
   `(double[] derivedECIs, int[] cfOrderMap, int pureKCount, AllClusterData targetData)`

Submitted through `BackgroundJobManager` like `CFIdentificationJob`.
Assembly never runs on the JavaFX thread again.
The four mutable panel fields are replaced by a single `AssemblyResult lastResult`.

**Files:** 1 new (`CECAssemblyJob`), 1 new (`AssemblyResult`). Build: green. Tests: 94/94.

---

### T1.2 — Add `CECOperationListener` and `IdentificationProgressListener`

Two new listener interfaces:

`application/port/CECOperationListener.java`:
```java
public interface CECOperationListener {
    void onAssemblyStarted(String targetSystemId, int subsystemCount);
    void onSubsystemProcessed(String subsystemKey, int order);
    void onAssemblyCompleted(AssemblyResult result);
    void onAssemblyFailed(String errorMessage);
}
```

`application/port/IdentificationProgressListener.java`:
```java
public interface IdentificationProgressListener {
    void onStageStarted(int stage, String description);
    void onStageCompleted(int stage, String summary);  // "Stage 2: ncf=4, tcf=15"
    void onPipelineCompleted(AllClusterData result);
    void onPipelineFailed(String errorMessage);
    default void logMessage(String message) {}
}
```

Wire `IdentificationProgressListener` into `CFIdentificationJob` (optional constructor arg).
Wire `CECOperationListener` into `CECAssemblyJob` (optional constructor arg).

**Files:** 2 new interfaces, 2 modified jobs. Build: green. Tests: 94/94.

---

### T1.3 — Create `CECManagementService`

New `application/service/CECManagementService.java`:
- `loadCEC(elements, structure, phase, model) → Optional<CECData>`
- `saveCEC(CECData)` — validates, delegates to `SystemDataLoader` (no raw path construction)
- `isCECAvailable(cecKey) → boolean`

`CECManagementPanel` Browser tab stops calling `SystemDataLoader` directly.
Workspace path construction removed from panel.

**Files:** 1 new. 1 modified (CECManagementPanel). Build: green. Tests: 94/94.

---

### T1.4 — Create `CECManagementCoordinator`

New `infrastructure/service/CECManagementCoordinator.java`. Owns:
- `startAssembly(target)` — builds `CECAssemblyJob` with listener, submits to `BackgroundJobManager`
- `saveAssembledCEC(AssemblyResult, double[] pureKValues)` — calls `CECManagementService.saveCEC`
- `Map<String, CECAssemblyJob> activeAssemblyJobs`
- Registered as `BackgroundJobManager.JobManagerListener` — delivers result to panel via
  `Platform.runLater()` when assembly job completes

`CECManagementPanel` becomes tabs + delegation. No infrastructure calls.

**Files:** 1 new. 1 modified (CECManagementPanel). Build: green. Tests: 94/94.

---

### T1.5 — Extract `IdentificationInputDialog`

Move `showIdentificationDialog()` + `IdentificationInput` inner class from
`SystemRegistryPanel` into `presentation/gui/component/IdentificationInputDialog.java`.
Removes duplication between Create System and Create Cluster call sites.

**Files:** 1 new, 1 modified. Build: green. Tests: 94/94.

---

### T1.6 — Create `IdentificationCoordinator`

New `infrastructure/service/IdentificationCoordinator.java`. Owns:
- `createSystem(request)` — cache check, register system, build+submit `CFIdentificationJob`
- `createClusterData(request)` — pure cluster-key pipeline (no system needed)
- `Map<String, CFIdentificationJob> activeJobs`
- Registered as `BackgroundJobManager.JobManagerListener` — on `onJobFinished(jobId)` calls
  `ClusterDataPresenter.present()` via `Platform.runLater()`

`SystemRegistryPanel` becomes form + delegation. No polling thread. ~200 lines removed.

**Files:** 1 new, 1 heavily modified (SystemRegistryPanel). Build: green. Tests: 94/94.

---

### T1.7 — Rename `CVMPipeline` → `IdentificationPipeline`, move package

Rename `CVMPipeline` → `IdentificationPipeline`, `CVMConfiguration` → `IdentificationRequest`.
Move both from `application/usecase/` to new `application/pipeline/`.
Update callers (one after T1.6: `IdentificationCoordinator`).

**Files:** 2 renamed/moved, 1 caller updated. Build: green. Tests: 94/94.

---

## 7. Recommended Order

```
T1.1  CECAssemblyJob              ← closes the fundamental job gap first
T1.2  both listener interfaces    ← unblocks progress wiring in T1.4 and T1.6
T1.3  CECManagementService        ← foundation for clean CEC I/O
T1.4  CECManagementCoordinator    ← completes Type 1(b) job architecture
T1.5  IdentificationInputDialog   ← low-risk cleanup for Type 1(a)
T1.6  IdentificationCoordinator   ← completes Type 1(a) job architecture
T1.7  rename/move                 ← cosmetic, last
```

Each step compiles independently and passes all 94 tests.
No step touches the domain layer or any Type 2 code.

---

## 8. What Does NOT Change

| File | Reason |
|------|--------|
| Domain: Stages 1-3 engines | Pure computation, correct, well-tested |
| `CVMPipeline.identify()` internals | Only renamed in T1.7, not restructured |
| `CFIdentificationJob.run()` structure | Only adds listener calls in T1.2 |
| `CECAssemblyService` math | Pure domain math, correct and tested |
| `AllClusterDataCache` | Clean persistence layer |
| `ClusterDataValidator` | Good utility |
| `KeyUtils` | Correct key construction |
| `StructureModelMapping` | Correct lookup table |
| `SystemDataLoader` I/O logic | Correct workspace/classpath logic |
| `BackgroundJobManager` | Unchanged — both new jobs slot in via existing interface |
