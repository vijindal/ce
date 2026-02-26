# CE GUI Implementation - Next Steps

## Current State
✅ **Phase 1 Complete**: Robust backend with system registry, job management, and CLI

---

## Immediate Next Steps (Session 2)

### 1. **Resolve JavaFX Dependency** (1-2 hours)

The build system is ready, just needs proper JavaFX configuration. Choose one approach:

#### **Approach A: Gluon Gradle Plugin** (Recommended)
```gradle
// app/build.gradle
plugins {
    id 'application'
    id 'org.openjfx.javafxplugin' version '0.1.0'
}

dependencies {
    // Remove manual JavaFX entries
}

javafx {
    version = "20.0.1"
    modules = ['javafx.controls', 'javafx.fxml', 'javafx.graphics', 'javafx.swing']
}
```

#### **Approach B: Download & Local JARs**
```bash
# 1. Download JavaFX SDK 20 from: https://gluonhq.com/products/javafx/
# 2. Extract to: app/lib/javafx/
# 3. Update build.gradle:
implementation files('lib/javafx/*.jar')
```

#### **Approach C: Maven Local Repository**
Use Gradle's local Maven repository with predownloaded JARs.

---

### 2. **Restore GUI Files** (5 minutes)
```bash
mv app/src/main/java/org/ce/app/gui/CEWorkbench.java.template \
   app/src/main/java/org/ce/app/gui/CEWorkbench.java

mv app/src/main/java/org/ce/app/gui/ui/panels/SystemRegistryPanel.java.template \
   app/src/main/java/org/ce/app/gui/ui/panels/SystemRegistryPanel.java
```

### 3. **Verify Compilation**
```bash
./gradlew app:compileJava
./gradlew app:run  # Should start GUI
```

---

## Phase 2: GUI Implementation (8-12 hours)

### 4. **Complete Remaining Panels**

Create these in `app/src/main/java/org/ce/app/gui/ui/panels/`:

#### **CalculationSetupPanel.java** (4-5 hours)
```java
public class CalculationSetupPanel extends VBox {
    // Select system from registry
    // Choose MCS or CVM
    // Input parameters:
    //   - ECI vector (table)
    //   - Temperature
    //   - Composition
    //   - MC parameters (L, steps, seed)
    // Optional sweeps (T sweep, X sweep)
    // [Run] button -> execute CalculationExecutor
}
```

#### **MonitorPanel.java** (4-5 hours)
```java
public class MonitorPanel extends VBox {
    // Real-time calculation progress
    // Energy convergence plot
    // Entropy evolution
    // SRO time series
    // Control buttons (pause, stop)
    // Statistics summary
}
```

#### **ResultsPanel.java** (3-4 hours)
```java
public class ResultsPanel extends VBox {
    // Display calculation results
    // Properties table:
    //   - G, H, S, Cp
    //   - CF values
    //   - SRO, Warren-Cowley order
    // Export to CSV/JSON/PDF
    // Save to registry
}
```

#### **VisualizationPanel.java** (5-6 hours)
```java
public class VisualizationPanel extends VBox {
    // 3D structure viewer
    // Energy vs steps plot
    // Phase diagram
    // SRO heatmaps
    // Comparisons between systems
}
```

#### **BatchPanel.java** (3-4 hours)
```java
public class BatchPanel extends VBox {
    // Select systems and parameter sweeps
    // T sweep range and delta
    // Composition sweep range
    // Parallel job configuration
    // Queue multiple calculations
}
```

---

## Phase 3: Visualization (4-6 hours)

### 5. **Add 3D Rendering**

Create `visualization/StructureRenderer3D.java`:
```java
public class StructureRenderer3D {
    // Use JavaFX 3D API or integrate jMonkeyEngine
    // Render atomic structure with bonds
    // Interactive camera controls
    // Color code atoms by type
    // Show crystal axes
}
```

### 6. **Add Charts**

Create `visualization/ChartBuilder.java`:
```java
public class ChartBuilder {
    // Energy convergence (LineChart)
    // Property vs composition (BarChart/LineChart)
    // Phase diagram (ScatterChart)
    // SRO/CF heatmaps (custom)
}
```

---

## Phase 4: Integration (4-6 hours)

### 7. **Connect Backend to UI**

Create `backend/CalculationExecutor.java`:
```java
public class CalculationExecutor {
    // Execute MCS with CalculationConfig
    // Stream results to UI in real-time
    // Update MonitorPanel during execution
    // Store results when complete
}
```

### 8. **Add Result Persistence**

Update `backend/SystemRegistry.java`:
```java
// Implement JSON serialization for:
//   - SystemInfo
//   - CalculationResults
//   - CalculationConfig presets
```

---

## Testing Checklist

- [ ] CLI interface fully functional
- [ ] JavaFX builds and runs
- [ ] System registration workflow
- [ ] Background job execution
- [ ] Result caching verified
- [ ] GUI panels render correctly
- [ ] Real-time progress updates
- [ ] Result visualization
- [ ] Export functionality
- [ ] Batch processing
- [ ] End-to-end test (register → compute → view → export)

---

## Build & Run Commands

```bash
# Compile
./gradlew app:compileJava

# Build
./gradlew app:build

# Run CLI (current)
./gradlew app:run

# Run GUI (after JavaFX setup)
./gradlew app:run

# Clean everything
./gradlew app:clean app:build
```

---

## Code Statistics

**Current**:
- Backend: ~1,800 LOC
- CLI: ~310 LOC
- Total: ~2,100 LOC

**Estimated After Phase 2-4**:
- GUI Panels: ~2,500 LOC
- Visualization: ~800 LOC
- Additional backend: ~500 LOC
- **Total: ~6,000 LOC**

---

## Key Design Patterns Used

1. **Model-View-Controller** - Separate models from UI
2. **Observer/Listener** - Job progress notifications
3. **Builder** - Configuration creation
4. **Registry** - Centralized system management
5. **Factory** - Job creation
6. **Thread Pool** - Concurrent execution

---

## File Organization Reference

```
app/src/main/
├── java/org/ce/app/
│   ├── cli/
│   │   └── CEWorkbenchCLI.java          ✓ Working
│   │
│   └── gui/
│       ├── CEWorkbench.java             ⏳ Ready (needs JavaFX)
│       │
│       ├── backend/
│       │   ├── SystemRegistry.java      ✓ Ready
│       │   ├── BackgroundJobManager.java ✓ Ready
│       │   ├── CalculationExecutor.java (TODO)
│       │   │
│       │   └── jobs/
│       │       ├── BackgroundJob.java    ✓ Ready
│       │       ├── AbstractBackgroundJob.java ✓ Ready
│       │       ├── ClusterIdentificationJob.java ✓ Ready
│       │       ├── CFIdentificationJob.java ✓ Ready
│       │       └── JobListener.java      ✓ Ready
│       │
│       ├── models/
│       │   ├── SystemInfo.java          ✓ Ready
│       │   ├── CalculationConfig.java   ✓ Ready
│       │   ├── CalculationResults.java  ✓ Ready
│       │   └── CalculationState.java    ✓ Ready
│       │
│       ├── ui/
│       │   ├── panels/
│       │   │   ├── SystemRegistryPanel.java (TODO - pending JavaFX)
│       │   │   ├── CalculationSetupPanel.java (TODO)
│       │   │   ├── MonitorPanel.java    (TODO)
│       │   │   ├── ResultsPanel.java    (TODO)
│       │   │   ├── VisualizationPanel.java (TODO)
│       │   │   └── BatchPanel.java      (TODO)
│       │   │
│       │   └── components/              (placeholder)
│       │
│       ├── visualization/               (placeholder)
│       └── util/                        (placeholder)
│
└── resources/
    ├── fxml/                           (Empty - ready for layouts)
    └── css/                            (Empty - ready for styling)
```

---

## Success Criteria

- ✅ Backend executes MCS and CVM calculations
- ✅ Systems are cached and reused
- ✅ GUI is responsive (no freezing)
- ✅ Results persist across sessions
- ✅ Charts update in real-time
- ✅ User can perform complete workflow (register → compute → visualize → export)

---

## Questions to Resolve

1. **JavaFX Platform**: Windows/Linux/Mac build support?
2. **Database**: Keep JSON files or migrate to SQLite?
3. **3D Rendering**: JavaFX 3D or jMonkeyEngine?
4. **Export Formats**: CSV/JSON/PDF or Excel sheets?
5. **Batch Processing**: How many parallel calculations?

---

## Start with These Commands (Next Session)

```bash
# 1. Fix JavaFX
# (Choose approach A, B, or C from "Resolve JavaFX Dependency" section)

# 2. Restore files
mv app/src/main/java/org/ce/app/gui/CEWorkbench.java.template \
   app/src/main/java/org/ce/app/gui/CEWorkbench.java

# 3. Test
./gradlew app:compileJava
./gradlew app:run

# 4. If successful, proceed with Phase 2 panel implementation
```

---

## Contact Points in Existing Code

The GUI integrates with these existing classes:
- `org.ce.app.CVMPipeline` - Cluster/CF identification
- `org.ce.app.CVMConfiguration` - Pipeline configuration
- `org.ce.app.CVMResult` - Pipeline results
- `org.ce.input.InputLoader` - File parsing
- `org.ce.identification.engine.ClusCoordListGenerator` - Cluster generation
- `org.ce.mcs.MCSRunner` - Monte Carlo execution

No modifications to these needed - the GUI just orchestrates them.

---

**Ready to continue? Start with the JavaFX resolution!**
