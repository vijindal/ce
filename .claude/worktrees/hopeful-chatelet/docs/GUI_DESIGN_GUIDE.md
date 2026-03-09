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
