# Folder Structure Review & Reorganization Plan

## Current Structure Analysis

### **Current Layout**
```
org/ce/
├── app/                           (MIXED CONCERNS - Problem!)
│   ├── Main.java
│   ├── CVMConfiguration.java      (Core logic)
│   ├── CVMPipeline.java           (Core logic)
│   ├── CVMPipelineRunner.java     (Core logic)
│   ├── CVMResult.java             (Core logic)
│   ├── examples/
│   ├── cli/                       (Application layer)
│   │   └── CEWorkbenchCLI.java
│   └── gui/                       (Application layer - partially structured)
│       ├── CEWorkbench.java.template
│       ├── backend/
│       │   ├── SystemRegistry.java
│       │   ├── BackgroundJobManager.java
│       │   └── jobs/
│       ├── models/
│       ├── ui/
│       ├── util/
│       └── visualization/
├── cvm/                           (Domain logic)
├── identification/                (Domain logic)
├── input/                         (Domain logic)
├── mcs/                           (Domain logic)
```

### **Current Issues**

| Issue | Impact | Example |
|-------|--------|---------|
| **Mixed Concerns** | `org.ce.app` contains both core pipeline AND UI/CLI code | CVMPipeline (core) alongside CEWorkbenchCLI (presentation) |
| **Unclear Hierarchy** | Core vs. Application layers not visually distinct | Can't tell what's domain logic vs. application logic |
| **GUI Models Placement** | GUI-specific models in app layer | `gui/models/CalculationConfig` separate from domain concerns |
| **Business Logic in GUI** | SystemRegistry and JobManager should be reusable across CLI/GUI | Currently only in `gui/backend/` |
| **Inconsistent Naming** | `cli/` and `gui/` at same level as `cvm/`, `mcs/` | Mixes abstraction levels |
| **No Clear Boundaries** | Hard to see what's exportable as library vs. application | Everything in org.ce |

---

## Professional Structure (Recommended)

### **Proposed Layout**

```
org/ce/
│
├── core/                          ✨ CORE ALGORITHMS & FRAMEWORK
│   ├── CVMConfiguration.java      (move from app/)
│   ├── CVMPipeline.java           (move from app/)
│   ├── CVMPipelineRunner.java     (move from app/)
│   ├── CVMResult.java             (move from app/)
│   ├── examples/                  (move from app/)
│   └── Main.java                  (update - run from workbench)
│
├── cvm/                           ✓ (KEEP - Domain logic)
├── mcs/                           ✓ (KEEP - Domain logic)
├── identification/                ✓ (KEEP - Domain logic)
├── input/                         ✓ (KEEP - Domain logic)
│
├── workbench/                     ✨ NEW - APPLICATION LAYER
│   │
│   ├── CEWorkbench.java           (Main entry point - demoted)
│   │
│   ├── cli/                       (CLI application)
│   │   ├── CEWorkbenchCLI.java
│   │   └── CLICommand.java        (interface for commands)
│   │
│   ├── gui/                       (GUI application)
│   │   ├── CEWorkbenchApplication.java  (Renamed from CEWorkbench.java.template)
│   │   ├── controller/                  (Controllers - coordinate View<->Model)
│   │   │   ├── CalculationController.java
│   │   │   ├── VisualizationController.java
│   │   │   └── BatchController.java
│   │   ├── view/                        (View layer - UI panels)
│   │   │   ├── CalculationSetupPanel.java
│   │   │   ├── MonitorPanel.java
│   │   │   ├── ResultsPanel.java
│   │   │   ├── VisualizationPanel.java
│   │   │   ├── BatchPanel.java
│   │   │   └── SystemRegistryPanel.java
│   │   ├── model/                       (GUI data models - DTO layer)
│   │   │   ├── CalculationState.java
│   │   │   ├── SystemInfo.java
│   │   │   ├── CalculationConfig.java   (move from gui/models/)
│   │   │   └── CalculationResults.java  (move from gui/models/)
│   │   ├── component/                   (Custom JavaFX components)
│   │   │   ├── JobProgressBar.java
│   │   │   ├── ParameterInputPanel.java
│   │   │   └── ChartPanel.java
│   │   ├── util/                        (GUI utilities)
│   │   │   ├── JavaFXUtils.java
│   │   │   └── StyleManager.java
│   │   └── style/                       (CSS/Styling)
│   │       └── dark-theme.css
│   │
│   ├── backend/                   (Shared business logic)
│   │   ├── service/               (Service layer)
│   │   │   ├── CalculationService.java
│   │   │   ├── RegistryService.java
│   │   │   └── JobService.java
│   │   ├── registry/              (System & result management)
│   │   │   ├── SystemRegistry.java           (move from gui/backend/)
│   │   │   ├── RegistryListener.java
│   │   │   └── persistence/       (Storage layer)
│   │   │       ├── FilePersistence.java
│   │   │       └── RegistryStorageManager.java
│   │   ├── job/                   (Background job management)
│   │   │   ├── BackgroundJobManager.java    (move from gui/backend/)
│   │   │   ├── BackgroundJob.java           (move from gui/backend/jobs/)
│   │   │   ├── JobListener.java             (move from gui/backend/jobs/)
│   │   │   ├── AbstractBackgroundJob.java   (move)
│   │   │   ├── ClusterIdentificationJob.java (move)
│   │   │   └── CFIdentificationJob.java     (move)
│   │   └── executor/              (Execution layer)
│   │       ├── CalculationExecutor.java      (TODO)
│   │       ├── CVMExecutor.java              (TODO)
│   │       └── MCSExecutor.java              (TODO)
│   │
│   ├── config/                    (Configuration)
│   │   ├── AppConfig.java
│   │   ├── GUIConfig.java
│   │   └── properties/
│   │       ├── application.properties
│   │       └── gui.properties
│   │
│   └── util/                      (Workbench utilities)
│       ├── Logger.java
│       ├── Constants.java
│       └── Validator.java
│
└── util/                          ✨ NEW (Optional) - Shared utilities
    ├── FileUtils.java
    ├── MathUtils.java
    └── ValidationUtils.java
```

---

## Reorganization Steps

### **Phase 1: Create New Directory Structure**

```bash
# Create workbench package structure
mkdir -p app/src/main/java/org/ce/workbench/{cli,gui/{controller,view,model,component,util,style},backend/{service,registry/persistence,job,executor},config,util}

# Create core package
mkdir -p app/src/main/java/org/ce/core

# Create shared util (if needed)
mkdir -p app/src/main/java/org/ce/util
```

### **Phase 2: Move Core Files**

Move these from `org.ce.app` to `org.ce.core`:
- `CVMConfiguration.java`
- `CVMPipeline.java`
- `CVMPipelineRunner.java`
- `CVMResult.java`
- `examples/` directory

### **Phase 3: Reorganize GUI/Backend**

Move from `org.ce.app.gui.backend` → `org.ce.workbench.backend.job`:
- `BackgroundJobManager.java`
- `jobs/BackgroundJob.java`
- `jobs/AbstractBackgroundJob.java`
- `jobs/ClusterIdentificationJob.java`
- `jobs/CFIdentificationJob.java`
- `jobs/JobListener.java`

Move from `org.ce.app.gui.models` → `org.ce.workbench.gui.model`:
- `CalculationState.java`
- `SystemInfo.java`
- `CalculationConfig.java`
- `CalculationResults.java`

Move `SystemRegistry.java` from `org.ce.app.gui.backend` → `org.ce.workbench.backend.registry`

### **Phase 4: Reorganize UI Layer**

Move from `org.ce.app.gui.ui.panels` → `org.ce.workbench.gui.view`:
- `SystemRegistryPanel.java`
- Create new panels as `CalculationSetupPanel.java`, etc.

Move from `org.ce.app.gui.visualization` → `org.ce.workbench.gui.component`:
- All visualization components

### **Phase 5: Update Package Declarations**

Update all `package` declarations in moved files:
- `org.ce.app` → `org.ce.core`
- `org.ce.app.gui.backend` → `org.ce.workbench.backend.job` or `.registry`
- `org.ce.app.gui.models` → `org.ce.workbench.gui.model`
- `org.ce.app.gui.ui.panels` → `org.ce.workbench.gui.view`
- `org.ce.app.cli` → `org.ce.workbench.cli`

### **Phase 6: Update Imports**

Update all import statements across the codebase to reflect new package locations.

### **Phase 7: Update Build Configuration**

- Update `build.gradle` mainClass if needed
- Update any resource file references

---

## Benefits of This Structure

| Benefit | How It Helps |
|---------|-------------|
| **Clear Separation** | Core algorithms vs. Application layer is immediately obvious |
| **Scalability** | Easy to add other interfaces (REST API, Web UI, etc.) without cluttering core |
| **Testability** | Can test backend independently from CLI/GUI |
| **Reusability** | Business logic in `workbench.backend` usable by both CLI and GUI |
| **Maintainability** | Clear responsibility: each package has one reason to change |
| **Professional** | Follows standard layer-based architecture pattern |
| **IDE Navigation** | Easy to find files - layout mirrors functionality |

---

## Implementation Priority

### **Must Do (Before GUI)**
- [ ] Create `org.ce.workbench.backend.{registry,job}`
- [ ] Move business logic out of GUI package

### **Should Do (For Professional Structure)**
- [ ] Move core to `org.ce.core`
- [ ] Create `org.ce.workbench.gui.{view,model,controller}`
- [ ] Create `org.ce.workbench.{cli,config}`

### **Nice to Have (Polish)**
- [ ] Add `org.ce.util` for shared utilities
- [ ] Move `examples/` to proper location
- [ ] Add configuration files

---

## File Relocation Checklist

### **From org.ce.app → org.ce.core**
- [ ] CVMConfiguration.java
- [ ] CVMPipeline.java
- [ ] CVMPipelineRunner.java
- [ ] CVMResult.java
- [ ] examples/ (directory)

### **From org.ce.app.gui.backend → org.ce.workbench.backend.job**
- [ ] BackgroundJobManager.java
- [ ] jobs/BackgroundJob.java
- [ ] jobs/AbstractBackgroundJob.java
- [ ] jobs/ClusterIdentificationJob.java
- [ ] jobs/CFIdentificationJob.java
- [ ] jobs/JobListener.java

### **From org.ce.app.gui → org.ce.workbench.backend.registry**
- [ ] backend/SystemRegistry.java

### **From org.ce.app.gui.models → org.ce.workbench.gui.model**
- [ ] CalculationState.java
- [ ] SystemInfo.java
- [ ] CalculationConfig.java
- [ ] CalculationResults.java

### **From org.ce.app.gui.ui.panels → org.ce.workbench.gui.view**
- [ ] SystemRegistryPanel.java

### **From org.ce.app.cli → org.ce.workbench.cli**
- [ ] CEWorkbenchCLI.java

### **From org.ce.app.gui → org.ce.workbench.gui**
- [ ] CEWorkbench.java.template → CEWorkbenchApplication.java

---

## Next Actions

1. **Review this structure** - Does it match your vision?
2. **Approve changes** - Any modifications before we proceed?
3. **Execute reorganization** - I'll handle all file moves and package updates
4. **Verify compilation** - Ensure all imports are correct post-move
5. **Update documentation** - Update NEXT_STEPS.md with new locations

**Shall I proceed with reorganizing the project structure?**
