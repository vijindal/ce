# Analysis: Gas Constant Treatment in MCS Calculations

## Problem Found

The universal gas constant **R is hardcoded to 1.0** in the MCS engine, which is physically incorrect.

### Location
- **File**: [MCSRunner.java](app/src/main/java/org/ce/mcs/MCSRunner.java#L223)
- **Class**: `MCSRunner.Builder`
- **Default Value**: `R = 1.0`
- **Comment**: "Phase gas constant (eV/K). Default 1.0 to match original code."

### Usage in Acceptance Probability
- **File**: [ExchangeStep.java](app/src/main/java/org/ce/mcs/ExchangeStep.java#L70)
- **Formula**: `beta = 1.0 / (R * T)`
- **Acceptance**: `P_accept = min(1, exp(-beta * ΔE))`
- **Effective Formula**: `P_accept = exp(-ΔE / (R * T))`

## Units Issue

### Current Implementation
- CEC energies: **J/mol** (from CEC JSON files)
- Temperature: **K** (Kelvin)
- Gas constant: **1.0** (undefined, treated as arbitrary scaling)

This is physically incorrect because units don't match!

### Correct Gas Constant Values
1. **Molar Gas Constant**: 
   - R = 8.314 J/(mol·K)
   - Units match: J/mol ÷ K = J/K/mol ✓

2. **Boltzmann Constant** (per particle/atom):
   - kB = 1.38065 × 10⁻²³ J/K
   - In eV: kB ≈ 8.617 × 10⁻⁵ eV/K
   - For per-site calculations with J/mol energies: convert to per-site energy first

## Where R Should Be Set

Currently, R is **never set** in the GUI execution path:

```java
// MCSExecutor.java (line 44)
MCSRunner runner = MCSRunner.builder()
    .clusterData(context.getClusterData())
    .eci(context.getECI())
    .numComp(context.getSystem().getNumComponents())
    .T(context.getTemperature())
    .compositionBinary(context.getComposition())
    .nEquil(context.getEquilibrationSteps())
    .nAvg(context.getAveragingSteps())
    .L(context.getSupercellSize())
    .seed(context.getSeed())
    // ⚠️ NO .R() SETTING = defaults to 1.0
    .build();
```

## Impact on Calculations

With **R = 1.0 (incorrect)** vs **R = 8.314 J/(mol·K) (correct)**:

For a typical energy change ΔE = 1 J/mol at T = 800 K:

| Parameter | Current (R=1.0) | Correct (R=8.314) |
|-----------|-----------------|-------------------|
| β = 1/(R·T) | 1/(1.0 × 800) = 0.00125 K⁻¹ | 1/(8.314 × 800) ≈ 1.5 × 10⁻⁴ K⁻¹ |
| Exponent: -β·ΔE | -0.00125 | -0.00015 |
| exp(-β·ΔE) | ≈ 0.9988 (almost always accept) | ≈ 0.9998 (always accept) |

**Problem**: With R=1.0, the system is essentially isothermal at artificial thermodynamics!

## Correct Solution

Set R based on energy units:

```java
// In MCSExecutor.java, after creating builder:
runner = MCSRunner.builder()
    // ... other settings ...
    .R(8.314)  // J/(mol·K) — must match energy units in CEC!
    .build();
```

Or:

```java
// In CalculationSetupPanel.java, when loading CEC:
// Convert CEC energies from J/mol to eV if needed, OR
// Ensure R is set correctly for the energy units
```

## Verification Needed

1. **Confirm CEC energy units**: Check if `-390.0` and `-260.0` are truly in J/mol
2. **Verify per-site vs per-mole**: Are cluster interaction energies per site or per mole?
3. **Check conversion factors**: If needed, convert R and energies to consistent units

## Related Issues

1. **CEC File Inconsistency**: Current cec.json has value `-8.3144` at index 2, which suspiciously equals the gas constant. This may indicate confusion about units or a copy-paste error.

2. **Missing Documentation**: No clear guidance in code comments about required units for:
   - CEC values
   - Temperature
   - Gas constant R

## Recommended Fix

Add explicit gas constant parameter with proper unit documentation:

```java
// In GUI CalculationSetupPanel or MCSExecutor
double gasConstant = 8.314; // J/(mol·K) for J/mol energies
runner = MCSRunner.builder()
    // ... parameters ...
    .R(gasConstant)
    .build();
```

And add validation:
```java
if (eciUnits.equals("J/mol") && !R_set) {
    log.warn("CEC energies are J/mol but R not explicitly set!");
    // Set R to 8.314 or warn user
}
```
