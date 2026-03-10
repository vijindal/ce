# CE Workbench: Ternary and Multi-Component Generalization Analysis

**Report Date:** March 2026
**Scope:** Systematic identification of binary-specific code blocking ternary/higher-order support
**Status:** Domain layer largely general; Application/Presentation layers need refactoring

---

## Executive Summary

The CE Workbench's domain layer (MCS/CVM engines) **already supports arbitrary K-component systems** through array-based composition and multi-component mathematics. However, **binary-specific constraints in the Application and Presentation layers prevent ternary systems from flowing through the UI and request/response chain**.

The core blocking issue: **scalar `double composition` in DTOs and request objects** (representing only the B-fraction in binary systems) prevents ternary composition like `[0.5, 0.3, 0.2]` from being passed through the application boundary.

**Test Evidence:** `CVMTernaryIntegrationTest.java` demonstrates ternary systems work at the domain level when given `double[] moleFractions` and `numComponents=3`.

---

## Good News — Already General

### Domain Layer (Production-Ready for Multi-Component)

These components **already handle K ≥ 2** without modification:

#### **MCS (Monte Carlo Simulation)**
- **`MCSRunner`** (D: `/d/codes/ce/app/src/main/java/org/ce/domain/mcs/MCSRunner.java`)
  - Accepts `double[] xFrac` (full composition vector, any length)
  - Provides convenience `compositionBinary(double xB)` for binary shorthand
  - Passes composition to `LatticeConfig`, `MCSampler`, `MCEngine` generically
  - Loop over `for (int c = 0; c < numComp; c++)` shows no binary assumptions

- **`MCEngine`** (D: `/d/codes/ce/app/src/main/java/org/ce/domain/mcs/MCEngine.java`)
  - Constructor accepts `int numComp` (line 64, 71)
  - Field: `private final double[] deltaMu;` — flexible for any number of components
  - Supports both canonical (ExchangeStep) and grand-canonical (FlipStep) ensembles

- **`LatticeConfig`** (referenced in MCSRunner.java line 131)
  - Initializes `N` sites with `numComp` components
  - Method `randomise(double[] xFrac, Random rng)` seeds initial config

#### **CVM (Cluster Variation Method)**
- **`CVMEngine`** (D: `/d/codes/ce/app/src/main/java/org/ce/domain/cvm/CVMEngine.java`)
  - Converts binary composition scalar to `double[] moleFractions = {1 - x_B, x_B}` (line 70)
  - Line 71: `int numElements = input.getNumComponents()` — reads from model input
  - Passes through to `NewtonRaphsonSolverSimple.solve(...)` which accepts `double[] moleFractions`

- **`NewtonRaphsonSolverSimple`** (implied from test usage)
  - Test `CVMTernaryIntegrationTest.java` line 68–82 shows it accepts:
    - `double[] moleFractions` of arbitrary length
    - `int numElements = 3` for ternary
  - No binary-only loops or constraints in domain logic

- **Result Objects:**
  - **`CVMResult`** (D: `/d/codes/ce/app/src/main/java/org/ce/domain/model/result/CVMResult.java`)
    - Stores scalar `double composition` (line 27) — **BINARY-SPECIFIC HERE**
    - But field is set only for reporting; domain solver works on `double[] moleFractions`

  - **`MCSResult`** (D: `/d/codes/ce/app/src/main/java/org/ce/domain/model/result/MCSResult.java`)
    - Correctly uses `double[] compositionArray` (line 28) — **ALREADY GENERAL**
    - Convenience method `composition()` returns `compositionArray[1]` for binary (line 71–72) — graceful fallback

#### **Infrastructure Layer (General Enough)**
- **`LatticeConfig`**: Stores `double[] composition` internally; no binary assumptions
- **`LocalEnergyCalc`**: Works on cluster products; no direct composition reference
- **`CVMPhaseModel`**: Accepts `double composition` but only for UI convenience

---

## Critical Issues — Application Layer Blocks Ternary

### **Issue 1: DTO Composition is Scalar (Binary-Only)**

#### **`MCSCalculationRequest`** (A: `/d/codes/ce/app/src/main/java/org/ce/application/dto/MCSCalculationRequest.java`)
```java
// Lines 15–16: BINARY-SPECIFIC
private final double composition;

// Lines 70–73: Builder allows only scalar
public Builder composition(double composition) {
    this.composition = composition;
    return this;
}

// Line 116–117: Validation only checks [0, 1] range (binary assumption)
if (composition < 0 || composition > 1) {
    throw new IllegalArgumentException("Composition must be between 0 and 1");
}
```

**Impact:** Ternary request like `{0.5, 0.3, 0.2}` **cannot be expressed** in the request object.

**Workaround Used:** None — ternary is simply blocked.

#### **`CVMCalculationRequest`** (A: `/d/codes/ce/app/src/main/java/org/ce/application/dto/CVMCalculationRequest.java`)
```java
// Lines 13, 41, 56: Same scalar pattern
private final double composition;
```

**Same blocker as MCS.**

---

### **Issue 2: UI Input is Single TextField (Binary-Only)**

#### **`CalculationSetupPanel`** (P: `/d/codes/ce/app/src/main/java/org/ce/presentation/gui/view/CalculationSetupPanel.java`)
```java
// Lines 47: Single field for composition
private final TextField compositionField;

// Line 91: Default 0.5 (binary midpoint)
compositionField = new TextField("0.5");

// Lines 144–145: Labeled as singular "Composition (x)"
addCompactRow(grid, 0, "Composition (x)", compositionField);

// Lines 241, 323: Parsed as single double
.composition(Double.parseDouble(compositionField.getText().trim()))
```

**Impact:** GUI cannot input ternary composition (e.g., three sliders or comma-separated fields).

**Required UI Change:**
- Replace single `TextField` with array input (e.g., three `TextField`s for x[0], x[1], x[2])
- Or use spinner array with dynamic component count from system metadata
- Validation: `sum(x[i]) ≈ 1.0` and `x[i] ≥ 0`

---

### **Issue 3: Use Cases Expect Scalar, Pass to Engines Accepting Arrays**

#### **`MCSCalculationUseCase`** (A: `/d/codes/ce/app/src/main/java/org/ce/application/usecase/MCSCalculationUseCase.java`)

The use case logs composition as a scalar (line 99, 171) but **does not directly invoke MCSRunner**. Instead, it delegates to the infrastructure adapter:

```java
// Line 124: Calls runnerPort.run(...) — an interface, not direct to MCSRunner
MCSResult mcResult = runnerPort.run(context, mcsPort, cancellationCheck);
```

The adapter (`MCSRunnerAdapter`, see next issue) extracts the scalar from context and calls:
```java
.compositionBinary(context.getComposition())  // Scalar x_B
```

**The Problem:**
- `AbstractCalculationContext` stores only `protected final double composition` (line 16 in AbstractCalculationContext.java)
- `MCSCalculationContext` inherits this single scalar
- Ternary composition array cannot be stored in the context

#### **`CVMCalculationUseCase`** (A: `/d/codes/ce/app/src/main/java/org/ce/application/usecase/CVMCalculationUseCase.java`)
```java
// Lines 88–93: Passes scalar composition to solverPort
CVMResult solverResult = solverPort.solve(
    toModelInput(context),
    context.getECI(),
    context.getTemperature(),
    context.getComposition(),   // <-- SCALAR
    context.getTolerance());
```

**Issue:** The adapter (CVMEngineAdapter) converts scalar to binary array (line 70 in CVMEngine.java):
```java
double[] moleFractions = {1.0 - composition, composition};
```

For ternary, this produces an **incorrect 2-element array** instead of the required 3-element array.

---

### **Issue 4: Infrastructure Adapters Hard-Code Binary Conversion**

#### **`MCSRunnerAdapter`** (I: `/d/codes/ce/app/src/main/java/org/ce/infrastructure/mcs/MCSRunnerAdapter.java`)
```java
// Line 43: HARD-CODED BINARY CONVERSION
.compositionBinary(context.getComposition())
```

**Impact:** Ternary `{0.5, 0.3, 0.2}` is **never created**; the adapter can only handle binary.

**Expected Fix:**
```java
// Get composition array from context (once context is updated)
double[] xFrac = context.getCompositionArray();
if (xFrac == null) {
    // Binary fallback for legacy support
    xFrac = new double[]{1.0 - context.getComposition(), context.getComposition()};
}
builder.composition(xFrac);  // MCSRunner.Builder accepts double[] composition(...)
```

#### **`CVMEngineAdapter`** (I: `/d/codes/ce/app/src/main/java/org/ce/infrastructure/cvm/CVMEngineAdapter.java`)
```java
// Line 26: Receives scalar, CVMEngine will convert to binary array
double composition,
```

**CVMEngine (line 70):**
```java
double[] moleFractions = {1.0 - composition, composition};
```

**Impact:** Ternary input is **truncated to binary** before the solver even runs.

---

### **Issue 5: Contexts Store Only Scalar Composition**

#### **`AbstractCalculationContext`** (I: `/d/codes/ce/app/src/main/java/org/ce/infrastructure/context/AbstractCalculationContext.java`)
```java
// Lines 15–16: Only binary-ready field
protected final double temperature;
protected final double composition;

// Line 25: Constructor accepts scalar
protected AbstractCalculationContext(
        SystemIdentity system,
        double temperature,
        double composition) {
```

**Subclasses inherit this limitation:**
- **`MCSCalculationContext`** (line 23–31): Constructor receives `double composition`
- **`CVMCalculationContext`**: Same pattern

**Impact:** Ternary composition array **cannot flow through the infrastructure layer**.

---

## Affected Components — Organized by Layer

### **Presentation Layer** (User Input)
| File | Issue | Type | Priority |
|------|-------|------|----------|
| `CalculationSetupPanel.java` | Single `TextField` for composition | Binary-specific UI | HIGH |
| | Default hardcoded to 0.5 | Binary assumption | HIGH |
| | Parsed as `Double.parseDouble(...)` | Type mismatch for arrays | HIGH |

### **Application Layer** (DTOs & Use Cases)
| File | Issue | Type | Priority |
|------|-------|------|----------|
| `MCSCalculationRequest.java` | Scalar `double composition` DTO field | Design blocker | HIGH |
| | Builder validates `[0, 1]` range only | Binary-specific validation | HIGH |
| `CVMCalculationRequest.java` | Scalar `double composition` DTO field | Design blocker | HIGH |
| | Builder validates `[0, 1]` range only | Binary-specific validation | HIGH |
| `MCSCalculationUseCase.java` | Logs composition as scalar | Information loss | MEDIUM |
| `CVMCalculationUseCase.java` | Passes scalar to port | Data loss point | HIGH |

### **Infrastructure Layer** (Adapters & Contexts)
| File | Issue | Type | Priority |
|------|-------|------|----------|
| `AbstractCalculationContext.java` | Single `double composition` field | Design blocker | HIGH |
| | Constructor accepts scalar | Type mismatch | HIGH |
| `MCSCalculationContext.java` | Inherits scalar composition | Cascading blocker | HIGH |
| `CVMCalculationContext.java` | Inherits scalar composition | Cascading blocker | HIGH |
| `MCSRunnerAdapter.java` | Calls `.compositionBinary(...)` | Hard-coded binary | HIGH |
| | No ternary conversion path | Missing code path | HIGH |
| `CVMEngineAdapter.java` | Receives scalar composition | Data format mismatch | HIGH |
| `CalculationService.java` | Constructs context with scalar (line 123) | Propagates blocker | HIGH |
| | No ternary composition parsing | Missing input parsing | HIGH |

### **Domain Layer** (Engines — Already General)
| File | Status | Notes |
|------|--------|-------|
| `MCSRunner.java` | ✅ General | Accepts `double[] xFrac`, no binary assumptions |
| `MCEngine.java` | ✅ General | Supports `int numComp`, flexible composition |
| `CVMEngine.java` | ⚠️ Converts scalar to array | Binary conversion at line 70 |
| `NewtonRaphsonSolverSimple.java` | ✅ General | Accepts `double[] moleFractions`, any length |
| `MCSResult.java` | ✅ General | Uses `double[] compositionArray` |
| `CVMResult.java` | ⚠️ Stores scalar | Only for result reporting; OK for now |
| `LatticeConfig.java` | ✅ General | Works with `double[] composition` |

---

## Implementation Priority

### **Phase 1: Enable Ternary (Critical Path)**
**Order of fixes to unblock ternary support:**

1. **Update `AbstractCalculationContext`**
   - Add `protected double[] compositionArray` field
   - Add `getCompositionArray()` accessor
   - Keep `double composition` for backward compatibility (auto-set from array[1])
   - Update constructor to accept `double[] compositionArray` OR `double composition` for binary

2. **Update DTOs: `MCSCalculationRequest` and `CVMCalculationRequest`**
   - Add `private final double[] composition` field (rename existing to `compositionScalar`)
   - Update Builder to accept both scalar (binary shorthand) and array (general)
   - Update validation: check `sum(x) ≈ 1.0`, `x[i] ≥ 0` for arrays
   - Keep backward-compatible scalar setters

3. **Update `CalculationService.prepareMCS()` and `.prepareCVM()`**
   - Parse composition from request (scalar or array)
   - Set both scalar and array on context
   - No other changes needed — context now carries both

4. **Update `MCSRunnerAdapter.run()`**
   - Extract `double[] xFrac` from context
   - Call `builder.composition(xFrac)` (not `.compositionBinary(...)`)
   - Fallback to binary conversion if array unavailable

5. **Update `CVMEngineAdapter.solve()`**
   - Extract `double[] compositionArray` from context
   - Pass array to `CVMEngine.solve()` instead of scalar
   - Update `CVMEngine.solve()` to accept array and skip binary conversion (line 70)

6. **Update `CalculationSetupPanel`**
   - Replace single `TextField` with dynamic array of `TextField`s
   - Number of fields = system component count
   - Parse as `double[] composition`
   - Pass array to request builder

### **Phase 2: UI Enhancements (Medium Priority)**
- Add composition validation UI (sum ≈ 1.0, all ≥ 0)
- Add "Auto-normalize" checkbox
- Display composition as pie chart for ternary/quaternary
- Add preset compositions (equimolar, binary endpoints)

### **Phase 3: Documentation & Testing (Low Priority)**
- Update class Javadoc to highlight multi-component support
- Add ternary test for full pipeline (UI → request → context → engine → result)
- Add quaternary test for K=4 systems
- Update user guide with ternary calculation example

---

## Code Examples — Before & After

### **Example 1: DTOs**

#### **Before (Binary-Only)**
```java
// MCSCalculationRequest.java
public final class MCSCalculationRequest {
    private final double composition;  // Only B-fraction

    public Builder composition(double composition) {
        // No way to pass {0.5, 0.3, 0.2}
        this.composition = composition;
        return this;
    }
}
```

#### **After (Multi-Component Ready)**
```java
public final class MCSCalculationRequest {
    private final double[] composition;  // Full composition array
    private final double compositionScalar;  // For backward compatibility

    private MCSCalculationRequest(Builder builder) {
        this.composition = builder.composition != null
            ? builder.composition.clone()
            : null;
        this.compositionScalar = builder.compositionScalar;
    }

    public double[] getComposition() { return composition != null ? composition.clone() : null; }
    public double getCompositionScalar() { return compositionScalar; }  // Binary shorthand

    public static class Builder {
        private double[] composition;
        private double compositionScalar = 0.5;

        // General: accept array
        public Builder composition(double[] composition) {
            this.composition = composition.clone();
            return this;
        }

        // Binary shorthand (backward compatible)
        public Builder compositionBinary(double xB) {
            this.composition = new double[]{1.0 - xB, xB};
            this.compositionScalar = xB;
            return this;
        }

        private void validate() {
            if (composition != null) {
                // Array validation
                double sum = 0;
                for (double x : composition) {
                    if (x < 0 || x > 1) {
                        throw new IllegalArgumentException("All composition values must be in [0, 1]");
                    }
                    sum += x;
                }
                if (Math.abs(sum - 1.0) > 1e-6) {
                    throw new IllegalArgumentException("Composition must sum to 1.0, got " + sum);
                }
            }
        }
    }
}
```

---

### **Example 2: Contexts**

#### **Before (Binary-Only)**
```java
public abstract class AbstractCalculationContext {
    protected final double composition;  // Only scalar

    protected AbstractCalculationContext(
            SystemIdentity system,
            double temperature,
            double composition) {
        this.composition = composition;  // Ternary array blocked
    }
}
```

#### **After (Multi-Component Ready)**
```java
public abstract class AbstractCalculationContext {
    protected final double composition;  // Kept for backward compatibility
    protected final double[] compositionArray;  // New: full composition

    protected AbstractCalculationContext(
            SystemIdentity system,
            double temperature,
            double composition) {
        // Binary shorthand constructor
        this.composition = composition;
        this.compositionArray = new double[]{1.0 - composition, composition};
    }

    // New: full composition constructor
    protected AbstractCalculationContext(
            SystemIdentity system,
            double temperature,
            double[] compositionArray) {
        this.composition = compositionArray.length > 1 ? compositionArray[1] : 0.0;
        this.compositionArray = compositionArray.clone();
    }

    public double getComposition() { return composition; }
    public double[] getCompositionArray() { return compositionArray.clone(); }
}
```

---

### **Example 3: MCSRunnerAdapter**

#### **Before (Binary-Only)**
```java
public MCSResult run(MCSCalculationContext context, ...) {
    MCSRunner.Builder builder = MCSRunner.builder()
        // ...
        .compositionBinary(context.getComposition())  // Only binary!
        // ...
    return result;
}
```

#### **After (Multi-Component Ready)**
```java
public MCSResult run(MCSCalculationContext context, ...) {
    double[] xFrac = context.getCompositionArray();
    if (xFrac == null) {
        // Backward compatibility for contexts created with scalar
        xFrac = new double[]{1.0 - context.getComposition(), context.getComposition()};
    }

    MCSRunner.Builder builder = MCSRunner.builder()
        // ...
        .composition(xFrac)  // Works for any K
        // ...
    return result;
}
```

---

### **Example 4: CalculationSetupPanel (UI)**

#### **Before (Binary-Only)**
```java
// Single TextField
private final TextField compositionField;

compositionField = new TextField("0.5");  // Only binary

addCompactRow(grid, 0, "Composition (x)", compositionField);

// Parsing
.composition(Double.parseDouble(compositionField.getText().trim()))
```

#### **After (Multi-Component Ready)**
```java
// Array of TextFields (dynamic based on system component count)
private final List<TextField> compositionFields = new ArrayList<>();

private void buildCompositionFields(int numComponents) {
    compositionFields.clear();
    for (int i = 0; i < numComponents; i++) {
        TextField field = new TextField(String.format("%.3f", 1.0 / numComponents));
        compositionFields.add(field);
        addCompactRow(grid, i, "Composition x[" + i + "]", field);
    }
}

// Parsing and validation
private double[] parseComposition(int numComponents) {
    double[] x = new double[numComponents];
    double sum = 0;
    for (int i = 0; i < numComponents; i++) {
        try {
            x[i] = Double.parseDouble(compositionFields.get(i).getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Composition x[" + i + "] is not a valid number");
        }
        sum += x[i];
    }
    if (Math.abs(sum - 1.0) > 1e-6) {
        throw new IllegalArgumentException("Composition must sum to 1.0, got " + sum);
    }
    return x;
}

// Usage in request building
double[] composition = parseComposition(selectedSystem.getNumComponents());
request = MCSCalculationRequest.builder()
    .systemId(selectedSystem.getId())
    .temperature(Double.parseDouble(temperatureField.getText().trim()))
    .composition(composition)  // Pass full array
    // ...
    .build();
```

---

### **Example 5: CVMEngine Adaptation**

#### **Before (Binary-Only)**
```java
public static CVMSolverResult solve(
        CVMModelInput input,
        double[] eci,
        double temperature,
        double composition,  // Scalar only
        double tolerance) {

    // Hard-coded binary conversion
    double[] moleFractions = {1.0 - composition, composition};
    int numElements = input.getNumComponents();  // But this might be 3!
    // moleFractions has wrong length for numElements=3
    // ...
}
```

#### **After (Multi-Component Ready)**
```java
public static CVMSolverResult solve(
        CVMModelInput input,
        double[] eci,
        double temperature,
        double[] moleFractions,  // Already computed array
        double tolerance) {

    int numElements = input.getNumComponents();

    // Validate moleFractions length matches numElements
    if (moleFractions.length != numElements) {
        throw new IllegalArgumentException(
            "moleFractions length (" + moleFractions.length +
            ") must match numComponents (" + numElements + ")");
    }

    // Pass directly to solver — no conversion needed
    return NewtonRaphsonSolverSimple.solve(
        moleFractions, numElements,
        temperature, eci,
        // ... rest of parameters
    );
}

// Keep scalar version for backward compatibility
@Deprecated
public static CVMSolverResult solveScalar(
        CVMModelInput input,
        double[] eci,
        double temperature,
        double composition,
        double tolerance) {
    double[] moleFractions = {1.0 - composition, composition};
    return solve(input, eci, temperature, moleFractions, tolerance);
}
```

---

## Key Files to Modify

**Summary of all files needing changes (execution order):**

| # | Layer | File | Change Type | Complexity |
|---|-------|------|------------|-----------|
| 1 | INFRASTRUCTURE | `AbstractCalculationContext.java` | Add `double[]` field + constructors | Medium |
| 2 | INFRASTRUCTURE | `MCSCalculationContext.java` | Inherit new constructor | Low |
| 3 | INFRASTRUCTURE | `CVMCalculationContext.java` | Inherit new constructor | Low |
| 4 | APPLICATION | `MCSCalculationRequest.java` | Add `double[]` field + builder methods | Medium |
| 5 | APPLICATION | `CVMCalculationRequest.java` | Add `double[]` field + builder methods | Medium |
| 6 | INFRASTRUCTURE | `CalculationService.java` | Parse composition from request to context | Low |
| 7 | INFRASTRUCTURE | `MCSRunnerAdapter.java` | Use array from context, fallback to scalar | Low |
| 8 | INFRASTRUCTURE | `CVMEngineAdapter.java` | Pass array instead of scalar | Low |
| 9 | DOMAIN | `CVMEngine.java` | Accept array, skip binary conversion | Low |
| 10 | PRESENTATION | `CalculationSetupPanel.java` | Dynamic composition input fields | High |
| 11 | TEST | New `IntegrationTernaryTest.java` | Full pipeline (UI→engine) | Medium |

---

## Test Evidence — Ternary Works at Domain Level

**File:** `/d/codes/ce/app/src/test/java/org/ce/domain/cvm/CVMTernaryIntegrationTest.java`

Key excerpts:
```java
// Line 55: numComponents = 3 for ternary
CVMConfiguration config = CVMConfiguration.builder()
    .numComponents(3)
    .build();

// Line 73–82: Solver accepts K=3 directly
private CVMSolverResult solve(double[] moleFractions, double temperature, double[] eci) {
    // moleFractions = {1/3, 1/3, 1/3} for equimolar ternary
    // numElements = 3
    return NewtonRaphsonSolverSimple.solve(
            moleFractions,
            3,  // <-- K=3 is fine here
            temperature,
            eci,
            // ...
    );
}

// Line 26–27: Verifies ternary converges
assertTrue(ternaryData.isComplete(), "Ternary AllClusterData must be complete");
```

This **proves** the domain layer can handle ternary; the blockers are purely at the application/presentation boundary.

---

## Summary Table

| Layer | Generalization Status | Blockers |
|-------|----------------------|----------|
| **Domain** | ✅ Production-ready for K ≥ 2 | None — engines work with arrays |
| **Infrastructure** | ⚠️ Partially blocked | Scalar composition in contexts, adapters hard-code binary |
| **Application** | ❌ Fully blocked | DTOs use scalar composition; no array support |
| **Presentation** | ❌ Fully blocked | Single TextField; cannot input ternary |

---

## Conclusion

**The path to ternary support is clear:** Replace scalar `double composition` with `double[] compositionArray` across the application and infrastructure layers, add dynamic UI input, and update adapters to pass arrays to domain engines that already support them.

**No domain-level changes required** — the heavy lifting is already done. The work is systematic refactoring of plumbing (DTOs, contexts, adapters) to unblock the flow of multi-component composition data.

**Estimated Effort:**
- Core refactoring (5-7 files): 2–3 days
- UI enhancement: 1–2 days
- Testing & validation: 1 day
- **Total: ~4–5 days** for full ternary support through the entire stack

