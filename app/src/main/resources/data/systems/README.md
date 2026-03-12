# System Data Directory

This directory contains element-specific CEC (Cluster Expansion Coefficient) data for CE/CVM calculations.

## Directory Structure (NEW - Feb 2026)

**Element-specific CECs** are stored separately from **shared structural definitions** (based on Pearson symbols like A2, B2, A1):

```
data/
├── systems/              # Element-specific CECs
│   ├── Ti-Nb/
│   │   └── cec.json
│   ├── Nb-Ti/
│   │   └── cec.json
│   └── Ti-V/
│       └── cec.json
└── models/               # Shared structural type definitions (Pearson symbol–based)
    └── A2_T/             # A2=Pearson symbol (disordered BCC); T=CVM model (tetrahedron)
        └── model_data.json
```

### Rationale for Separation

**CECs are element-pair specific:**
- Ti-Nb has different CECs than Ti-V (different interatomic interactions)
- Each element pair needs its own `systems/{Elements}/cec.json`

**Structural definitions are shared across alloys:**
- Multiple alloys (Ti-Nb, Ti-V, Ti-Zr) can all use `models/A2_T/model_data.json`
- **A2 = disordered BCC (Pearson symbol) + T = tetrahedron CVM model** form a single, complete structural designation
- Never separate "structure=BCC" and "phase=A2" as independent choices; always use indivisible designations like A2_T

## CEC File Format

**systems/{Elements}/cec.json**

```json
{
  "elements": "Ti-Nb",
  "cecValues": [-390.0, -260.0, 0.0, 0.0],
  "cecUnits": "J/mol",
  "reference": "Nb-Ti phase diagram optimization"
}
```

## Adding New Systems

1. Create folder: `systems/{Element1-Element2}/`
2. Create `cec.json` with CEC values (ncf-length array only; no point/empty CFs)
3. Optionally create reverse order (e.g., both Ti-Nb and Nb-Ti)
4. Structural model data is shared — add once to `models/{PearsonSymbol}_{Model}/` where:
   - **PearsonSymbol**: A2 (disordered BCC), B2 (ordered BCC), A1 (disordered FCC), etc.
   - **Model**: T (tetrahedron), Q (quadruplet), H (hexagon), etc.
   - Example: `models/A2_T/` = A2 Pearson symbol + T model

## Bundled Systems

### Ti-Nb / Nb-Ti
- **Elements**: Ti-Nb (and reverse Nb-Ti)
- **Structural type**: A2_T where:
  - **A2** = Pearson symbol for disordered BCC (this is a **complete, indivisible** structural designation)
  - **T** = tetrahedron CVM model
- **CECs**: 4 values from phase diagram fitting (ncf=4, non-point cluster functions only)
  - Tetrahedron (tet): 0.0 J/mol
  - Triangle (tri): 0.0 J/mol
  - 1st neighbor pair: -390.0 J/mol
  - 2nd neighbor pair: -260.0 J/mol
- **Reference**: Nb-Ti binary phase diagram optimization

