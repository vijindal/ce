# Ternary Generalization — Quick Reference

## The Core Problem in One Sentence
**Scalar `double composition` in DTOs, contexts, and adapters blocks ternary `double[] composition` from reaching domain engines that already support it.**

---

## The Five Blockers

### 1. **DTOs (Application Layer)**
- `MCSCalculationRequest.java`: Stores `double composition` only
- `CVMCalculationRequest.java`: Stores `double composition` only
- **Fix:** Add `double[] composition` field; keep scalar for binary backward compatibility

### 2. **Contexts (Infrastructure Layer)**
- `AbstractCalculationContext.java`: Field is `protected final double composition`
- `MCSCalculationContext`: Inherits scalar-only constructor
- `CVMCalculationContext`: Inherits scalar-only constructor
- **Fix:** Add `protected double[] compositionArray` field + constructor overload

### 3. **Adapters (Infrastructure Layer)**
- `MCSRunnerAdapter.java` line 43: Calls `.compositionBinary(context.getComposition())`
- `CVMEngineAdapter.java`: Passes scalar to `CVMEngine.solve()`
- **Fix:** Extract array from context, pass to engines; fallback to binary conversion if needed

### 4. **Engines (Domain Layer — Already General)**
- `CVMEngine.java` line 70: Hard-codes `moleFractions = {1-x, x}` for binary
- **Fix:** Accept `double[] moleFractions` directly; skip binary conversion

### 5. **UI (Presentation Layer)**
- `CalculationSetupPanel.java` line 47: Single `TextField compositionField`
- **Fix:** Dynamic array of `TextField`s (number = system component count)

---

## File-by-File Changes

| Priority | File | Change | Lines |
|----------|------|--------|-------|
| **1** | `AbstractCalculationContext.java` | Add `double[] compositionArray` field & constructor | 12–26 |
| **2** | `MCSCalculationRequest.java` | Add `composition(double[])` builder method | 49–93 |
| **3** | `CVMCalculationRequest.java` | Add `composition(double[])` builder method | 35–75 |
| **4** | `CalculationService.java` | Parse composition array from request | ~120–130 |
| **5** | `MCSRunnerAdapter.java` | Use `context.getCompositionArray()` | ~38–47 |
| **6** | `CVMEngineAdapter.java` | Pass array not scalar | ~15–27 |
| **7** | `CVMEngine.java` | Accept `double[]` not `double` | ~40–70 |
| **8** | `CalculationSetupPanel.java` | Replace single `TextField` with array | ~47, ~91–145 |

---

## Validation Rules

**For `double[] composition` arrays:**
```
1. Length must equal system.getNumComponents()
2. All x[i] ∈ [0, 1]
3. Sum must ≈ 1.0 (tolerance 1e-6)
```

**UI Feedback:**
- If sum ≠ 1.0, highlight in red
- Optional "Auto-normalize" checkbox: `x[i] ← x[i] / sum`

---

## Backward Compatibility

**Keep scalar `double composition` as:**
- Binary shorthand in builder: `.compositionBinary(0.5)`
- Fallback in contexts for old code
- Result reporting (first element of composition array)

**Example Binary Code Still Works:**
```java
MCSCalculationRequest.builder()
    .systemId("Fe-Al")
    .compositionBinary(0.3)  // ← Works with new builder
    .build();
```

---

## Testing Checklist

- [ ] Unit test: `MCSCalculationRequest` with `double[]`
- [ ] Unit test: `CVMCalculationRequest` with `double[]`
- [ ] Unit test: `AbstractCalculationContext` array constructor
- [ ] Integration test: Binary → ternary in full pipeline
- [ ] Integration test: Ternary input → ternary output
- [ ] GUI test: Ternary composition field input
- [ ] GUI test: Validation feedback (sum ≠ 1.0)
- [ ] Regression: Binary calculations still work

---

## Code Snippets

### DTO Builder (New)
```java
public Builder composition(double[] composition) {
    this.composition = composition.clone();
    validateComposition();
    return this;
}

private void validateComposition() {
    double sum = 0;
    for (double x : composition) {
        if (x < 0 || x > 1) throw new IllegalArgumentException("x[i] ∉ [0,1]");
        sum += x;
    }
    if (Math.abs(sum - 1.0) > 1e-6) throw new IllegalArgumentException("sum ≠ 1.0");
}
```

### Context (New Constructor)
```java
protected AbstractCalculationContext(
        SystemIdentity system,
        double temperature,
        double[] compositionArray) {
    this.system = system;
    this.temperature = temperature;
    this.composition = compositionArray.length > 1 ? compositionArray[1] : 0.0;
    this.compositionArray = compositionArray.clone();
}
```

### Adapter (Fixed)
```java
public MCSResult run(MCSCalculationContext context, ...) {
    double[] xFrac = context.getCompositionArray();
    if (xFrac == null) {
        xFrac = new double[]{1.0 - context.getComposition(), context.getComposition()};
    }
    builder.composition(xFrac);
    // ...
}
```

### UI (New)
```java
private void buildCompositionFields(int numComponents) {
    for (int i = 0; i < numComponents; i++) {
        TextField field = new TextField(String.format("%.3f", 1.0 / numComponents));
        compositionFields.add(field);
        addCompactRow(grid, i, "x[" + i + "]", field);
    }
}
```

---

## Why This Works

1. **Domain engines already accept `double[]`** → No engine changes needed (except CVMEngine input)
2. **Backward compatibility via `double` field** → Binary code still compiles
3. **Structural change only** → No algorithmic changes required
4. **Tests prove ternary works** → CVMTernaryIntegrationTest passes at domain level

---

## Effort Estimate

| Task | Days | Notes |
|------|------|-------|
| Refactor DTOs | 0.5 | Straightforward field additions |
| Refactor Contexts | 0.5 | Inherit + add constructor |
| Fix Adapters | 0.5 | 3 files, simple changes |
| Fix CVMEngine | 0.5 | Accept array input |
| UI Replacement | 1.5 | Most complex; dynamic field creation |
| Testing | 1.5 | Integration tests + regression |
| **Total** | **5 days** | |

