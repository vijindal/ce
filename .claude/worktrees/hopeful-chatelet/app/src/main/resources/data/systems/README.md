# System Data Directory

This directory contains element-specific CEC (Cluster Expansion Coefficient) data for CE/CVM calculations.

## Directory Structure (NEW - Feb 2026)

**Element-specific CECs** are stored separately from **shared model data**:

```
data/
├── systems/              # Element-specific CECs
│   ├── Ti-Nb/
│   │   └── cec.json
│   ├── Nb-Ti/
│   │   └── cec.json
│   └── Ti-V/
│       └── cec.json
└── models/               # Shared cluster/CF data (see ../models/)
    └── BCC_A2_T/
        └── model_data.json
```

### Rationale for Separation

**CECs are element-pair specific:**
- Ti-Nb has different CECs than Ti-V
- Each element pair needs its own `systems/{Elements}/cec.json`

**Model data is shared across alloys:**
- Multiple alloys (Ti-Nb, Ti-V, Ti-Zr) can all use `models/BCC_A2_T/model_data.json`
- No need to duplicate cluster/CF metadata for each alloy

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
2. Create `cec.json` with CEC values
3. Optionally create reverse order (e.g., both Ti-Nb and Nb-Ti)
4. Model data is shared - add once to `models/{Structure}_{Phase}_{Model}/`

## Bundled Systems

### Ti-Nb / Nb-Ti
- **Elements**: Ti-Nb (and reverse Nb-Ti)
- **CECs**: 4 values from phase diagram fitting
  - 1st neighbor pair (E21): -390 J/mol
  - 2nd neighbor pair (E22): -260 J/mol
  - Triangle (E3): 0 J/mol
  - Tetrahedron (E4): 0 J/mol
- **Compatible Models**: BCC_A2_T
- **Reference**: Nb-Ti binary phase diagram optimization

