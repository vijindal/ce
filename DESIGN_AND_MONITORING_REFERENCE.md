# CE Workbench - GUI Design Guide

**Version:** 0.4.0  
**Last Updated:** February 28, 2026

---

## Design Philosophy

### Core Principles

**1. MVVM Architecture (Model-View-ViewModel)**
- Separation of concerns between UI and business logic
- Observable properties for reactive updates
- Testable ViewModels without UI dependencies
- Data binding for automatic synchronization

**2. Material Design Adaptation**
- Card-based layouts for logical grouping
- Elevation system for hierarchy
- 8dp grid spacing system
- Consistent color palette
- Clear typography hierarchy

**3. Domain-Driven Design**
- UI reflects the scientific workflow
- Terminology matches domain (clusters, ECIs, correlation functions)
- Progressive disclosure of complexity
- Expert-friendly power features

---

## Visual Design System

### Color Palette

```css
/* Primary Colors */
Primary:      #1976D2  /* Science blue - headers, primary actions */
Primary Dark: #004BA0  /* Hover states */
Primary Light:#63A4FF  /* Backgrounds, highlights */

/* Secondary Colors */
Secondary:    #43A047  /* Success, valid states */
Warning:      #FB8C00  /* Warnings, attention needed */
Error:        #D32F2F  /* Errors, invalid states */

/* Neutral Colors */
Background:   #FAFAFA  /* Main background */
Surface:      #FFFFFF  /* Cards, panels */
Border:       #E0E0E0  /* Dividers, borders */
Text Primary: #212121  /* Main text */
Text Secondary:#757575 /* Helper text, labels */
Text Disabled:#9E9E9E  /* Disabled elements */
```

### Typography

```css
/* Headings */
H1 (Page Title):        18px, Bold, Primary Color
H2 (Section Title):     14px, Bold, Text Primary
H3 (Subsection):        12px, Semi-Bold, Text Primary

/* Body Text */
Body:                   11px, Regular, Text Primary
Caption (Helper):       10px, Regular, Text Secondary
Code/Monospace:         10px, Courier New, Text Primary

/* Input Fields */
Field Text:            11px, Regular, Text Primary
Field Label:           10px, Semi-Bold, Text Secondary
```

### Spacing System (8dp Grid)

```
Extra Small:  4px   (xs)   - Icon padding, tight spacing
Small:        8px   (sm)   - Between related elements
Medium:       16px  (md)   - Between sections
Large:        24px  (lg)   - Between major components
Extra Large:  32px  (xl)   - Panel margins
```

### Component Sizes

```
Button Height:         28px (compact), 36px (standard)
Input Height:          32px
Icon Size:             16px (small), 24px (standard)
Card Border Radius:    4px
Panel Border Radius:   0px (maintains sharp professional look)
```

---

## Architecture Guidelines

### MVVM Pattern

#### Model (`org.ce.domain.system`)
Domain objects with no UI dependencies (for example `SystemIdentity`, `SystemStatus`).

#### ViewModel (`org.ce.presentation.gui.model`)
UI state and user input mapping remain in the presentation layer.

#### View (FXML + Controller)
Minimal logic, binds to ViewModel:
```java
public class SystemController {
    @FXML private TextField elementsField;
    @FXML private Button identifyButton;
    
    private SystemViewModel viewModel;
    
    public void initialize() {
        elementsField.textProperty().bindBidirectional(viewModel.elementsProperty());
        identifyButton.disableProperty().bind(viewModel.isIdentifyingProperty());
    }
}
```

### Package Structure

```
org.ce.presentation.gui/      # JavaFX entry point, views, components, UI models
org.ce.presentation.cli/      # CLI front-end
org.ce.application.service/   # Presentation-facing orchestration service
org.ce.application.job/       # Job abstractions and orchestration jobs
org.ce.infrastructure.job/    # Scheduling/execution manager
org.ce.infrastructure.registry/ # Registry and result repositories
org.ce.domain.system/         # System identity/status domain models
```

---

## Component Design Patterns

### 1. Status Cards

```java
public class StatusCard extends VBox {
    private Label titleLabel;
    private Label statusLabel;
    private ProgressIndicator progress;
    
    public StatusCard(String title) {
        getStyleClass().add("status-card");
        // Build card with consistent styling
    }
    
    public void setStatus(Status status) { ... }
}
```

**Usage:** System status, job progress, data availability

### 2. Validation Text Fields

```java
public class ValidatedTextField extends TextField {
    private BooleanProperty valid = new SimpleBooleanProperty(true);
    private Predicate<String> validator;
    
    public ValidatedTextField(Predicate<String> validator) {
        this.validator = validator;
        textProperty().addListener((obs, old, newVal) -> validate());
    }
    
    private void validate() {
        boolean isValid = validator.test(getText());
        valid.set(isValid);
        pseudoClassStateChanged(PseudoClass.getPseudoClass("invalid"), !isValid);
    }
}
```

**CSS:**
```css
.validated-text-field:invalid {
    -fx-border-color: #D32F2F;
    -fx-border-width: 2px;
}
```

### 3. Job Progress Panel

```java
public class JobProgressPanel extends VBox {
    private ListView<JobStatus> jobList;
    private ProgressBar overallProgress;
    
    public void bindToJobManager(BackgroundJobManager manager) {
        jobList.itemsProperty().bind(manager.activeJobsProperty());
    }
}
```

---

## Layout Guidelines

### Main Window Structure

```
┌──────────────────────────────────────────────────────────┐
│ Menu Bar                                                  │
├──────────────┬───────────────────────────────────────────┤
│              │                                            │
│   System     │           Results & Calculations          │
│   Setup      │                                            │
│   (30%)      │               (70%)                       │
│              │                                            │
│  [Card 1]    │  [Tab: Results]                          │
│  [Card 2]    │  [Tab: Visualization]                    │
│  [Card 3]    │                                            │
│              │                                            │
│              │                                            │
│              │                                            │
├──────────────┴───────────────────────────────────────────┤
│ Status Bar: [Icon] Ready  |  Background Jobs: 2 active   │
└──────────────────────────────────────────────────────────┘
```

### Responsive Breakpoints

```
Minimum Window:  800 x 600
Recommended:     1400 x 850 (current)
Large Screen:    1920 x 1080
```

**Responsive behavior:**
- < 1000px width: Stack panels vertically
- < 800px: Disable unnecessary chrome, compact mode
- > 1600px: Expand visualization area, show more detail

---

## Interaction Patterns

### 1. Progressive Disclosure
- Show essential controls first
- Advanced options in expandable sections
- Tooltips for complex parameters

### 2. Workflow Guidance
```
Step 1: Define System → [Status: Complete ✓]
Step 2: Identify Clusters → [Status: In Progress...]
Step 3: Calculate ECIs → [Status: Locked 🔒]
```

### 3. Feedback & States

**Loading States:**
- Spinner + descriptive text ("Identifying clusters...")
- Progress bar with percentage
- Ability to cancel long operations

**Validation:**
- Real-time field validation
- Error messages below fields
- Visual indicators (red border, warning icon)

**Success/Error:**
- Toast notifications for quick feedback
- Inline messages for form errors
- Dialog for critical errors

---

## Accessibility Guidelines

### Keyboard Navigation
- Tab order follows logical flow
- All actions accessible via keyboard
- Shortcut keys for common operations

### Visual Accessibility
- Minimum contrast ratio: 4.5:1 for text
- Color not the only indicator (use icons + text)
- Resizable text (respect system font scaling)

### Screen Reader Support
- Meaningful labels on all controls
- ARIA landmarks for major sections
- Status message announcements

---

## CSS Architecture

### File Structure
```
resources/styles/
├── theme.css              # Main theme
├── components.css         # Reusable components
├── layout.css             # Layout utilities
└── dark-theme.css         # Dark mode (future)
```

### CSS Naming Convention
```css
/* Component-based naming */
.panel-card { }
.panel-card__header { }
.panel-card__body { }

/* State modifiers */
.button--primary { }
.button--disabled { }

/* Utility classes */
.mt-md { margin-top: 16px; }
.p-lg { padding: 24px; }
```

---

## Implementation Priorities

### Phase 1: Foundation (Week 1-2)
- [ ] Create CSS theme file
- [ ] Extract inline styles to CSS classes
- [ ] Set up color/spacing constants
- [ ] Create base ViewModel classes

### Phase 2: Architecture (Week 3-4)
- [ ] Implement MVVM for SystemPanel
- [ ] Convert main panels to FXML
- [ ] Add data binding
- [ ] Create reusable components

### Phase 3: Polish (Week 5-6)
- [ ] Add animations/transitions
- [ ] Implement validation framework
- [ ] Add keyboard shortcuts
- [ ] Create dark mode theme

### Phase 4: Advanced (Week 7+)
- [ ] Visualization components
- [ ] Advanced data binding
- [ ] Performance optimization
- [ ] User preferences system

---

## Code Examples

### Before (Current)
```java
Label titleLabel = new Label("System Setup");
titleLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
```

### After (With CSS)
```java
Label titleLabel = new Label("System Setup");
titleLabel.getStyleClass().add("section-title");
```

```css
/* theme.css */
.section-title {
    -fx-font-size: 14px;
    -fx-font-weight: bold;
    -fx-text-fill: #1976D2;
}
```

---

## Resources

### JavaFX
- [Official JavaFX Documentation](https://openjfx.io/)
- [ControlsFX](https://controlsfx.github.io/) - Additional controls
- [JFoenix](http://www.jfoenix.com/) - Material Design components

### Design Systems
- [Material Design](https://material.io/) - Google's design system
- [Fluent Design](https://www.microsoft.com/design/fluent/) - Microsoft's design system
- [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/) - Apple's guidelines

### Architecture
- [MVVMFx](https://github.com/sialcasa/mvvmFX) - MVVM framework for JavaFX
- [ReactFX](https://github.com/TomasMikula/ReactFX) - Reactive programming for JavaFX

---

## Next Steps

1. **Review this guide** with the team
2. **Create CSS theme file** as foundation
3. **Start with one panel** (SystemPanel) to pilot MVVM approach
4. **Iterate and refine** based on feedback
5. **Document patterns** as they emerge

**Goal:** Transform from functional prototype to polished professional application while maintaining scientific rigor and usability for expert users.

---

# MCS Real-Time Monitoring Design

# MCS Real-Time Monitoring Design

**Focus:** Energy convergence tracking with real-time visualization  
**Update Frequency:** Every 1% of steps (moderate detail/performance balance)  
**Primary User:** Researcher monitoring long-running simulations

---

## Design Overview

### The Challenge
- MCS calculations can run for **hours** without feedback
- Need to track convergence WITHOUT cluttering logs
- Distinguish between **equilibration phase** (warm-up) and **averaging phase** (data collection)
- Minimize UI performance impact from data streaming

### Optimization: Energy Tracking via ΔE

**Key Insight:** Total lattice energy recalculation is expensive!  
**Solution:** Track energy changes (ΔE) only

```
Initialization (One-time, expensive):
  E_total = calculateFullEnergy(lattice)  // Done once ✓

Per MC Step (Cheap, every step):
  ΔE = calculateEnergyChange(site_i, site_j)  // ~1000x faster
  E_total += ΔE
  
Monitoring:
  - Plot ΔE per step (shows acceptance pattern)
  - Accumulate running E_total (shows convergence trend)
  - Track ΔE statistics (stability metric via σ(ΔE))
```

**Benefits:**
- ✅ Energy tracking at every step with negligible cost
- ✅ Can update chart every step (not just every 100)
- ✅ More detailed convergence visibility
- ✅ Better stability metrics (std dev of ΔE is better than total E)

### Solution: **Progressive Disclosure Dashboard**

Three layers of detail:
1. **At-a-Glance Status** (minimal overhead from ΔE tracking)
2. **Convergence Charts** (real-time updates, high frequency now!)
3. **Detailed Logs** (inspect when needed)

---

## Recommended Layout: Dual-Panel with Tabs

```
┌─────────────────────────────────────────────────────────┐
│ Results Panel (Right side of main window)               │
├─────────────────────────────────────────────────────────┤
│ [Chart View] [Log View]  [Data Export]  [Stop Calc...]  │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Energy Convergence                               │  │
│  │ ┌──────────────────────────────────────────────┐ │  │
│  │ │                                              │ │  │
│  │ │    Energy (E)                                │ │  │
│  │ │    ^                                         │ │  │
│  │ │    │     EQUILIBRATION      │ AVERAGING     │ │  │
│  │ │    │         [___]          │  [====]       │ │  │
│  │ │    │                        │               │ │  │
│  │ │    └────────────────────────┼───────────────│ │  │
│  │ │                             ↑ Transition    │ │  │
│  │ │                        MC Steps →           │ │  │
│  │ │  Equilibration: 3,245 / 5,000 (65%)         │ │  │
│  │ │  Averaging:    0 / 10,000                   │ │  │
│  │ │  Current E: -124.56 J/mol  ΔE: +0.23 J/mol │ │  │
│  │ │                                              │ │  │
│  │ └──────────────────────────────────────────────┘ │  │
│  │                                                   │  │
│  │  Status: EQUILIBRATING  Progress: [████░░░░░] 65% │  │
│  │  Est. Time Remaining: 2h 15m  Elapsed: 1h 10m     │  │
│  │  Current Temperature: 800.0 K                      │  │
│  │  Avg Acceptance Rate: 78.2% (last 100 steps)      │  │
│  │                                                   │  │
│  └───────────────────────────────────────────────────┘  │
│                                                          │
│  [Pause] [Resume] [Cancel] [Clear] | [Data Table]      │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### Why This Layout?

✅ **Chart View (Default)**
- Full real estate for visualization
- Clear energy convergence pattern
- Phase distinction (equilibration vs averaging)
- Progress indicators
- Action buttons for control

✅ **Log View (Tab 2)**
- Detailed timestamped messages
- For debugging or reference
- Collapsible for future expansion

✅ **Smart Updates**
- Updates every ~100 steps (1% for typical 10K sweep)
- Chart only redraws on new data
- Plot animations smooth but not distracting

---

## Core Information Architecture

### 1. Real-Time Dual-Axis Chart (ΔE per Step + Cumulative Energy)

**What to Plot:**
```
Primary (Left Y-axis):    ΔE per step (energy change, in J/mol)
Secondary (Right Y-axis): Cumulative Energy (E_total, in J/mol)
X-axis:                   MC step number (0 to total_steps)

Visual Elements:
├── ΔE Scatter/Histogram (Primary axis)
│   ├── EQUILIBRATION (Steps 0 - Eq_steps): Blue (#1976D2)
│   │   └── Wide fluctuation envelope = still converging
│   ├── PHASE TRANSITION MARKER: Vertical dashed line
│   └── AVERAGING (Steps Eq_steps - Total): Green (#43A047)
│       └── Narrow envelope = stable plateau
│
└── Cumulative Energy Line (Secondary axis, light gray)
    ├── Shows overall trend
    └── Should be nearly vertical (flat slope = convergence)
```

**Real-time Updates (Leveraging ΔE Optimization):**
```java
Update frequency:  Every step (or every N steps, user configurable)
Data point 1:      (step_number, ΔE_value)      // Very cheap ✓
Data point 2:      (step_number, E_cumulative)  // Just an accumulation
Smoothing:         None for ΔE (shows raw activity)
Performance:       Now feasible to update every step!
                   ΔE calculation is ~1000x cheaper than full energy
```

### 2. Status Indicators (Below Chart)

```
Equilibration Progress:  [████████░░░░░░░░░░░░] 35% (3,500 / 10,000 steps)
Averaging Progress:      [░░░░░░░░░░░░░░░░░░░░] 0%  (0 / 10,000 steps)

Status:                  EQUILIBRATING (Live)  ⏱️ 18 min elapsed
Current Energy:          -124.5678 J/mol (E_initial + Σ(ΔE))

Energy Stability (from ΔE statistics):
  Avg ΔE (rolling 500 steps):   -0.001 J/mol (near zero = converged)
  σ(ΔE) [Stability metric]:     ±0.234 J/mol (std dev of ΔE)
  ΔE Range (last 500 steps):    -0.50 to +0.42 J/mol
  Convergence Trend:            🟡 Still converging (σ > 0.1)

Acceptance Rate:         77.3% (flip success rate)
Estimated Time Left:     22 min (based on current speed)
```

**Why ΔE Statistics are Better than Energy:**
- `σ(ΔE)` directly indicates energy fluctuation magnitude
- Small σ(ΔE) = steps are small + converged = ready to average
- Large σ(ΔE) = still exploring/equilibrating
- `mean(ΔE)` ≈ 0 = no drift (acceptance balanced)

### 3. Detailed Log View (Secondary Tab)

```
┌─────────────────────────────────────────────────┐
│ [Log View] [Data Table] [Export] [Settings...]  │
├─────────────────────────────────────────────────┤
│ [15:42:08] MC Run started: T=800K, N=128, L=4  │
│ [15:42:09] E_initial = -112.34 J/mol (full calc)│
│ [15:42:10] EQUILIBRATION PHASE STARTED          │
│ [15:42:15] Step 100: ΔE_avg=-0.080 J/mol       │
│ [15:42:20] Step 200: ΔE_avg=-0.045 J/mol       │
│ [15:42:25] Step 300: ΔE_avg=-0.023 J/mol       │
│ [15:42:30] Step 400: ΔE_avg=-0.012 J/mol       │
│ ...                                              │
│ [15:48:00] Step 5000: EQUILIBRATION COMPLETE   │
│           E_total = -124.50 J/mol               │
│           σ(ΔE) = 0.15 (converged, ready)      │
│ [15:48:00] AVERAGING PHASE STARTED              │
│ [15:48:05] Step 5100: ΔE=-0.002 J/mol          │
│                                                  │
│ ⬜ Auto-scroll ON    [Clear Log]  [Copy All]    │
└─────────────────────────────────────────────────┘
```

### 4. Data Export/Inspection

```
┌────────────────────────────────────┐
│ [Data Table]                       │
├────────────────────────────────────┤
│ Step  │ Energy      │ Accept % │    │
│────────────────────────────────────│
│ 0     │ -112.34     │ —        │    │
│ 100   │ -120.45     │ 81.2     │    │
│ 200   │ -122.12     │ 76.4     │    │
│ ...   │ ...         │ ...      │    │
│ 5000  │ -124.50     │ 77.1     │    │
│ 5100  │ -124.48     │ 75.8     │    │
│                                    │
│ [Export CSV] [Copy to Clipboard]   │
└────────────────────────────────────┘
```

---

## Control Panel

### Action Buttons

```
[Pause]      → Pause calculation, keep memory/state intact
[Resume]     → Resume from pause point
[Cancel]     → Stop calculation, discard progress
[Clear]      → Clear chart and logs (start fresh view)
[Export]     → Save data to CSV/JSON
[Settings]   → Configure update frequency, plot colors, etc.
```

### Keyboard Shortcuts
- **Spacebar**: Pause/Resume
- **Ctrl+X**: Cancel calculation
- **Ctrl+S**: Export data
- **Ctrl+C**: Copy current log to clipboard
- **Ctrl+L**: Clear view

---

## Data Flow Architecture

### Real-Time Data Pipeline

```
┌────────────────┐
│ MCEngine       │
│ Simulator      │
└────────┬───────┘
         │ Every step
         ↓
┌────────────────────────┐
│ MCSDataCollector       │
│ - Buffers data         │
│ - Computes rolling avg │
│ - Formats for UI       │
└────────┬───────────────┘
         │ Every 100 steps
         ↓
┌────────────────────────┐
│ MCSUpdateEvent         │
│ - step number          │
│ - energy value         │
│ - phase (eq/avg)       │
│ - acceptance rate      │
│ - computed properties  │
└────────┬───────────────┘
         │
         ↓
┌────────────────────────┐
│ ResultsPanel           │
│ - Update chart         │
│ - Update status        │
│ - Append log (optional)│
└────────────────────────┘
```

### Implementation Pattern (with ΔE Optimization)

```java
// In MCEngine or MCRunner
public void runSimulation(MCConfig config, Consumer<MCSUpdate> updateListener) {
    // ONE-TIME: Calculate initial total energy (expensive, done once)
    double E_total = calculateFullEnergy(lattice);
    
    // Track ΔE statistics for stability metric
    RollingWindow<Double> deltaEWindow = new RollingWindow<>(500);
    
    for (int step = 0; step < totalSteps; step++) {
        // Per-step: Calculate ONLY energy change (cheap, ~1000x faster!)
        int site_i = selectRandomSite();
        int site_j = selectRandomNeighbor(site_i);
        double deltaE = calculateEnergyChange(site_i, site_j);  // <<< This is FAST
        
        // Metropolis acceptance criterion
        boolean accepted = accept(deltaE, temperature);
        if (accepted) {
            performFlip(site_i, site_j);
            E_total += deltaE;  // Simple accumulation
        }
        
        deltaEWindow.add(deltaE);  // Track for statistics
        acceptanceTracker.record(accepted);
        
        // Emit update every N steps or every step (now feasible!)
        if (shouldReportProgress(step)) {
            MCSUpdate update = new MCSUpdate(
                step,
                E_total,                           // Accumulated energy
                deltaE,                            // Latest ΔE value
                deltaEWindow.getStdDev(),          // σ(ΔE) - Stability!
                deltaEWindow.getMean(),            // mean(ΔE) - Drift check
                getCurrentPhase(),                 // EQUILIBRATION or AVERAGING
                acceptanceTracker.getRate()
            );
            updateListener.accept(update);  // Send to UI
        }
    }
}

// RollingWindow helper for efficient statistics
public class RollingWindow<T> {
    private final Deque<Double> window;
    private int maxSize;
    
    public RollingWindow(int maxSize) {
        this.maxSize = maxSize;
        this.window = new ArrayDeque<>();
    }
    
    public void add(double value) {
        window.add(value);
        if (window.size() > maxSize) {
            window.removeFirst();
        }
    }
    
    public double getStdDev() {
        // Return standard deviation of values in window
        // Small σ(ΔE) → converged
        // Large σ(ΔE) → still equilibrating
    }
    
    public double getMean() {
        // Return mean of ΔE in window
        // Should be near zero after convergence
    }
}
```

**Key Algorithm Insights:**
1. Calculate E_init **once** (expensive but one-time cost)
2. Each step: calculate only ΔE (very cheap)
3. Accumulate: E_total += ΔE (O(1) operation)
4. Track statistics: σ(ΔE) tells convergence story
5. Update UI with: (E_total, ΔE, σ(ΔE), mean(ΔE))

---

## Chart Component Implementation

### Using JavaFX LineChart

```java
public class EnergyConvergenceChart extends HBox {
    private LineChart<Number, Number> chart;
    private XYChart.Series<Number, Number> equilibrationSeries;
    private XYChart.Series<Number, Number> averagingSeries;
    
    private final List<Double> energyData = new ArrayList<>();
    private final List<Integer> stepData = new ArrayList<>();
    
    public void updateWithMCSData(MCSUpdate update) {
        // Add new data point
        if (update.getPhase() == Phase.EQUILIBRATION) {
            equilibrationSeries.getData().add(
                new XYChart.Data<>(update.getStep(), update.getEnergy())
            );
        } else {
            averagingSeries.getData().add(
                new XYChart.Data<>(update.getStep(), update.getEnergy())
            );
        }
        
        // Prune old data if too many points (every 10K points, keep last 2K)
        if (equilibrationSeries.getData().size() > 10000) {
            equilibrationSeries.getData().remove(0, 
                equilibrationSeries.getData().size() - 2000);
        }
    }
    
    public void markPhaseTransition(int step) {
        // Add vertical line at equilibration/averaging boundary
        ValueAxis<Number> xAxis = (ValueAxis<Number>) chart.getXAxis();
        // Visual marker for transition
    }
}
```

---

## Performance Considerations

### For Hour-Long Simulations (with ΔE Optimization)

**Problem 1: Memory Usage**
- 10K equilibration + 10K averaging = 20K data points
- Each point = (step, ΔE, E_total) ≈ 24 bytes
- Total: ~480 KB (negligible)
- ✅ No pruning needed for reasonable sweep counts

**Problem 2: UI Responsiveness**
- Update frequency: **Can now be every step!** (or every N steps)
- ΔE calculation is ~1000x cheaper than full energy
- Chart redraws on UI thread (use Platform.runLater)
- ✅ Update every 10-100 steps for smooth real-time updates

**Problem 3: Chart Rendering**
- LineChart with up to 50K+ points still smooth
- Disable point markers (reduces visual overhead)
- Two series (ΔE + cumulative E) adds negligible cost
- ✅ High-frequency updates now feasible

**ΔE Optimization Impact:**
```
Before: Calculate full E every 100 steps    = 0.5 sec per update
After:  Accumulate ΔE per step, update UI    = 0.01 sec per update
                                    → 50x FASTER!
```

---

## Status Information Display

### Key Metrics to Show

```
╔════════════════════════════════════════════════════════════╗
║ EQUILIBRATION PHASE (Warm-up - establishing baseline)     ║
╠════════════════════════════════════════════════════════════╣
║ Progress:      [████████░░░░░░░░░░░░░░░░░░░░░░░░] 35%     ║
║ Steps:         3,500 / 10,000                              ║
║ Time Elapsed:  18 min 45 sec                               ║
║ Est. Time Rem: 35 min 30 sec                               ║
╠════════════════════════════════════════════════════════════╣
║ Current Energy:        -124.5678 J/mol                     ║
║ Energy Change (ΔE):    +0.234 J/mol (last 500 steps)       ║
║ Stability Status:      🟡 Still converging (ΔE > 0.01)   ║
║ Acceptance Rate:       77.3% (good range 60-85%)           ║
║ Temperature:           800.0 K                              ║
║ Supercell Size:        L=4 (128 atoms)                     ║
╚════════════════════════════════════════════════════════════╝
```

### Stability Indicators (σ(ΔE)-Based)

| σ(ΔE) Magnitude | Status | Color | Meaning |
|--|--|--|--|
| < 0.05 J/mol | ✅ Converged | Green | Plateau reached, ready to average |
| 0.05 - 0.15 J/mol | 🟡 Converging | Yellow | Still equilibrating, trend downward |
| 0.15 - 0.50 J/mol | 🟠 Early Stage | Orange | Early equilibration, wide fluctuations |
| > 0.50 J/mol | 🔴 Far from it | Red | Very far from equilibrium |

**Why σ(ΔE) is the Right Metric:**
- Direct measure of energy step fluctuations
- Small σ(ΔE) = small moves = exploring near equilibrium
- Large σ(ΔE) = large moves = far from convergence
- Universally applicable (system-independent)

---

## MVP (Minimum Viable Product) - Phase 1

For immediate implementation (1 week):

```java
[✓] EnergyConvergenceChart component
    - Line chart with two series
    - Updates every 100 steps
    - Phase marking (color coding)
    
[✓] Status Panel
    - Progress bars (Eq and Avg phases)
    - Current energy display
    - Phase indicator
    
[✓] Log View
    - TextArea with timestamped messages
    - Auto-scroll at bottom
    
[✓] Control Buttons
    - Pause/Resume
    - Cancel
    - Clear
```

## Phase 2 (Optional - Week 2-3)

```java
[ ] Acceptance rate plot (secondary chart)
[ ] Energy stability metric display
[ ] Data export to CSV
[ ] Zoom/pan on chart
[ ] Statistics panel (min, max, avg, std dev)
```

---

## Code Integration Points

### Current Architecture → Enhanced

```
MCEngine/MCRunner
    ↓
[NEW] MCSDataCollector (Data aggregation)
    ↓
[NEW] MCSUpdate Event (Type-safe data)
    ↓
BackgroundJobManager
    ↓
[ENHANCED] ResultsPanel
    ├── [NEW] EnergyConvergenceChart
    ├── [NEW] StatusPanel
    ├── [KEEP] LogView (TextArea)
    └── ControlPanel (Pause/Resume/Cancel)
```

### Minimal Changes to Existing Code

1. **MCEngine**: Add callback parameter
   ```java
   public void run(Consumer<MCSUpdate> updateListener) { ... }
   ```

2. **BackgroundJobManager**: Pass listener to job
   ```java
   job.addProgressListener(update -> resultsPanel.updateMCS(update));
   ```

3. **ResultsPanel**: Add chart and status display
   ```java
   public void updateMCS(MCSUpdate update) {
       chart.addDataPoint(update);
       statusPanel.updateStatus(update);
   }
   ```

---

## User Experience: Hour-Long Simulation

**Scenario:** User starts MCS at T=800K, L=4, 10K equilibration + 10K averaging (~2 hours)

```
T=0 min
├─ Click "Run MCS"
├─ Progress bar appears
├─ Chart initializes (empty)
└─ Status: "EQUILIBRATING | Elapsed: 0s | Est. 2h 15m"

T=10 min (1000 steps)
├─ Energy line appears on chart
├─ Status updates with current energy
├─ Progress bar shows 10%
└─ Can still cancel if parameters wrong

T=30 min (5000 steps, halfway through equilibration)
├─ Energy curve shows trend
├─ ΔE metric shows convergence progress
├─ Can identify issues: "Acceptance too low" or "Still fluctuating"
└─ Can adjust if needed, or wait

T=70 min (end of equilibration)
├─ Chart shows phase transition
├─ Switches to AVERAGING phase
├─ Energy line stabilizes
└─ Now averaging for final data

T=130 min (completion)
├─ Chart shows full equilibration + averaging
├─ Final statistics displayed
├─ Export data button active
├─ Can save for analysis
```

**Key UX Points:**
✅ User can **see progress** (not just "running...")
✅ User can **assess convergence** (early stopping if bad)
✅ User can **compare** this run to others later
✅ User can **interrupt** if something looks wrong

---

## Design Decision Summary

| Aspect | Recommendation | Why |
|--------|---|---|
| Layout | **Chart as primary**, log as tab | Convergence visualization is most important |
| Update Freq | **Every 1% of steps** | Balance detail vs. performance |
| Chart Type | **Line chart, no markers** | Clean, responsive, shows trend |
| Phases | **Color-coded zones** | Clear visual distinction |
| Metrics | **Energy only (MVP)** | Most important, extensible later |
| Controls | **Pause/Resume/Cancel** | Manage long jobs flexibly |
| Data Storage | **In-memory until export** | No disk I/O overhead |

---

## Next Steps

1. **Implement EnergyConvergenceChart** component
2. **Create StatusPanel** for metrics display
3. **Add MCSUpdate event** class for type-safe data passing
4. **Modify MCEngine** to emit updates
5. **Connect to ResultsPanel** UI
6. **Test with hour-long simulation**

**Estimated Timeline:** 3-4 days for MVP
