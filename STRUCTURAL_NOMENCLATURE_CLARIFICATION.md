# Structural Nomenclature Clarification

**Date:** March 12, 2026
**Purpose:** Clarify that crystal structure designations are NOT separated into independent "structure" and "phase" components.

---

## Key Concept

**Pearson symbols (A2, B2, A1, L1₂, etc.) are INDIVISIBLE, COMPLETE structural designations.**

- **A2** = disordered BCC (body-centered cubic) — a **single type**, not "structure=BCC" + "phase=A2"
- **B2** = ordered BCC — a **single type**, not "structure=BCC" + "phase=B2"
- **A1** = disordered FCC — a **single type**, not "structure=FCC" + "phase=A1"
- **L1₂** = ordered FCC — a **single type**, not "structure=FCC" + "phase=L12"

When combined with a CVM model (T=tetrahedron, Q=quadruplet, etc.), the designation becomes indivisible:
- **A2_T** = A2 Pearson symbol + tetrahedron CVM model
- **B2_T** = B2 Pearson symbol + tetrahedron CVM model
- **L12_T** = L1₂ Pearson symbol + tetrahedron CVM model

These designations are stored in:
- **Cluster cache directories:** `data/cluster_cache/A2_T_bin/`, `data/cluster_cache/B2_T_tern/`, etc.
- **Structural model directories:** `app/src/main/resources/data/models/A2_T/`, `app/src/main/resources/data/models/L12_T/`, etc.

---

## Why This Matters

### Incorrect Understanding
❌ "I can choose structure=BCC independently, then choose phase=A2"
❌ "BCC_A2_T means structure=BCC, phase=A2, model=T as three separate decisions"
❌ "I can combine BCC with B2 for a different variant"

### Correct Understanding
✅ A2, B2, A1, L1₂ are **complete structural types** from crystallography (Pearson notation)
✅ **Never** separate them into independent lattice + ordering choices
✅ **Always** work with complete designations: A2_T, B2_T, A1_T, L12_T
✅ Each designation maps to a complete set of cluster/CF definitions

---

## What Changed in Documentation

### Files Updated

1. **README.md**
   - Clarified Resource Files section: explained Pearson symbols as indivisible designations
   - Added nomenclature note: "each is a complete structural type"

2. **app/src/main/resources/data/systems/README.md**
   - Renamed "shared model data" section header to emphasize Pearson symbol basis
   - Updated "Adding New Systems" to clarify PearsonSymbol component (A2, B2, A1, etc.)
   - Explained that A2 = "disordered BCC" (Pearson symbol), a single, complete designation

3. **PROJECT_STATUS.md**
   - Updated "Current Architecture" section to use A2_T instead of BCC_A2_T
   - Clarified that A2 is a "Pearson symbol for disordered BCC" (single designation)
   - Renamed GUI field from "Structure/Phase" to "Structural Type" with warning about indivisibility

4. **IMPLEMENTATION_PROGRESS.md**
   - Updated ECI standardization notes to reference A2_T instead of BCC_A2_T
   - Added clarification: "A2 = Pearson symbol"

5. **app/src/main/java/org/ce/domain/system/SystemIdentity.java**
   - Added javadoc comment clarifying that structure + phase together form an indivisible designation
   - Emphasized: "Never separate them as independent choices"

---

## Impact on Code

**No code changes required.** The system already stores structure and phase in separate fields internally for technical reasons. This clarification is purely **semantic/documentation** to prevent user confusion:

- Users should understand that "A2" is a **complete type**, not a "phase" chosen independently from "structure"
- When developing new systems, always work with complete designations like A2_T, B2_T, etc.
- When documenting systems, clarify: "A2 = Pearson symbol for disordered BCC" instead of "structure=BCC, phase=A2"

---

## References

- **Pearson notation:** Standardized crystallographic notation for crystal structures (ISO 4287)
  - First symbol (A, B, L, etc.): lattice type (A=simple, B=body-centered, C=face-centered, L=hexagonal, etc.)
  - Second symbol (numeric): ordering (1=disordered, 2=ordered variant, etc.)
- **CVM model types:** T=tetrahedron, Q=quadruplet, H=hexagon, etc. (cluster selection within a Pearson type)

---

## Examples

### Correct Usage
- "This system uses the A2_T structural type" ✅
- "A2 is the Pearson symbol for disordered BCC" ✅
- "We need to define B2_T (ordered BCC with tetrahedron model) data" ✅

### Incorrect Usage
- "This system uses structure=BCC and phase=A2" ❌
- "A2 is the phase in BCC" ❌
- "We combined BCC with B2 for a different variant" ❌
