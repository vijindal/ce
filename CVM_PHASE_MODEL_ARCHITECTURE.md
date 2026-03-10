# CVMPhaseModel Architecture: Model-Centric Design with Parameter Updates

## Executive Summary

**User's Mental Model:**
```
User creates a CVM Phase Model for a given system
       ↓
User provides: System Parameters (CECs) + Macro Parameters (T, x)
       ↓
Model automatically returns: Equilibrium thermodynamic quantities (G, H, S, CFs, CVs, SROs)
       ↓
If user changes T, x, or CEC → Model auto-minimizes for new equilibrium
       ↓
Always ready to query current equilibrium state
```

**Data Flow Categories:**

| Category | Type | Changes Require | Immutability |
|----------|------|-----------------|---------------|
| **Cluster Data** | AllClusterData (Stage 1,2,3) | No re-minimization | Fixed at creation |
| **System Parameters** | ECI (CECs) | Full re-minimization | Mutable, triggers minimize |
| **Macro Parameters** | Temperature, Composition | Full re-minimization | Mutable, triggers minimize |
| **Micro Parameters** | CFs, CVs, thermodynamic quantities | Computed from macros | Cached, invalidated on parameter change |

---

## 1. CVMPhaseModel: Complete Redesign

### 1.1 Data Organization

```java
public class CVMPhaseModel {
    
    // =========================================================================
    // IMMUTABLE: Cluster Data (from AllClusterData) — never changes
    // =========================================================================
    private final int tcdis;
    private final int tcf;
    private final int ncf;
    private final List<Double> mhdis;
    private final double[] kb;
    private final double[][] mh;
    private final int[] lc;
    private final int[][] lcf;
    private final int[][] cfBasisIndices;
    private final List<List<double[][]>> cmat;
    private final int[][] lcv;
    private final List<List<int[]>> wcv;
    private final ClusterData clusters;
    
    private final SystemIdentity system;
    private final int numComponents;
    
    // =========================================================================
    // MUTABLE: System Parameters — can be changed at any time
    // =========================================================================
    private double[] eci;            // Effective Cluster Interactions (CECs)
    private double tolerance;        // Convergence criterion
    
    // =========================================================================
    // MUTABLE: Macro Parameters — can be changed at any time
    // =========================================================================
    private double temperature;      // Kelvin
    private double[] moleFractions;  // Composition, normalized
    
    // =========================================================================
    // CACHED: Equilibrium results — updated when parameters change
    // =========================================================================
    private boolean isMinimized = false;
    private long lastMinimizationTime;
    private double[] equilibriumCFs;              // [ncf]
    private CVMFreeEnergy.EvalResult equilibrium; // G, H, S, ∇G, ∇²G
    
    // =========================================================================
    // DIAGNOSTICS: Solver convergence info
    // =========================================================================
    private int lastIterations;
    private double lastGradientNorm;
    private String lastConvergenceStatus;
}
```

### 1.2 Factory Method (Immutable Cluster Data Only)

```java
/**
 * Factory creates a CVMPhaseModel from context.
 * 
 * Loads ALL cluster data (Stages 1-3) from AllClusterData.
 * User provides initial T, x, ECI values.
 * Does NOT minimize yet — happens on first query or explicit call.
 * 
 * @param context with AllClusterData loaded
 * @param eci initial CECs
 * @param temperature initial temperature (K)
 * @param composition initial composition
 * @return Model ready for parameter updates and queries
 */
public static CVMPhaseModel create(
        CVMCalculationContext context,
        double[] eci,
        double temperature,
        double composition) throws IllegalArgumentException {
    
    // Validate context + AllClusterData
    if (!context.isReady()) {
        throw new IllegalArgumentException("Context not ready: " + context.getReadinessError());
    }
    
    AllClusterData allData = context.getAllClusterData();
    if (!allData.isComplete()) {
        throw new IllegalArgumentException("AllClusterData incomplete");
    }
    
    CVMPhaseModel model = new CVMPhaseModel(allData, context.getSystem());
    
    // Set initial parameters (triggers validation + minimization)
    model.setECI(eci);
    model.setTemperature(temperature);
    model.setComposition(composition);
    model.ensureMinimized();  // First minimization on creation
    
    return model;
}
```

### 1.3 Constructor (Private)

```java
/**
 * Private constructor — only called by factory.
 * Extracts and caches all cluster data from AllClusterData.
 */
private CVMPhaseModel(AllClusterData allData, SystemIdentity system) {
    // Extract Stage 1: Cluster Identification
    ClusterIdentificationResult stage1 = allData.getStage1();
    this.tcdis = stage1.getTcdis();
    this.mhdis = stage1.getDisClusterData().getMultiplicities();
    this.kb = stage1.getKbCoefficients();
    this.mh = stage1.getMh();
    this.lc = stage1.getLc();
    this.clusters = stage1.getDisClusterData();
    
    // Extract Stage 2: CF Identification
    CFIdentificationResult stage2 = allData.getStage2();
    this.tcf = stage2.getTcf();
    this.ncf = stage2.getNcf();
    this.lcf = stage2.getLcf();
    
    // Extract Stage 3: C-Matrix Structure
    CMatrixResult stage3 = allData.getStage3();
    this.cfBasisIndices = stage3.getCfBasisIndices();
    this.cmat = stage3.getCmat();
    this.lcv = stage3.getLcv();
    this.wcv = stage3.getWcv();
    
    // System info
    this.system = system;
    this.numComponents = allData.getNumComponents();
    
    // Initialize parameter storage (not yet set)
    this.eci = null;
    this.temperature = Double.NaN;
    this.moleFractions = null;
    this.tolerance = 1.0e-6;
    this.isMinimized = false;
}
```

---

## 2. Parameter Update Interface

### 2.1 Setters (Trigger Re-minimization)

```java
/**
 * Sets cluster interaction energies (CECs).
 * Invalidates cached results — next query will re-minimize.
 */
public void setECI(double[] newECI) throws IllegalArgumentException {
    if (newECI == null || newECI.length != this.ncf) {
        throw new IllegalArgumentException(
            "ECI length mismatch: got " + (newECI == null ? 0 : newECI.length) +
            ", expected " + this.ncf);
    }
    this.eci = newECI.clone();
    invalidateMinimization();
}

/**
 * Sets temperature.
 * Invalidates cached results — next query will re-minimize.
 */
public void setTemperature(double T_K) throws IllegalArgumentException {
    if (T_K <= 0) {
        throw new IllegalArgumentException("Temperature must be positive: " + T_K);
    }
    this.temperature = T_K;
    invalidateMinimization();
}

/**
 * Sets composition (binary shorthand).
 * Automatically converts to mole fractions for K-component system.
 */
public void setComposition(double x_B) throws IllegalArgumentException {
    if (x_B < 0 || x_B > 1) {
        throw new IllegalArgumentException("Composition must be in [0,1]: " + x_B);
    }
    
    if (numComponents == 2) {
        this.moleFractions = new double[]{1.0 - x_B, x_B};
    } else {
        // For K > 2: assume binary input is first component
        this.moleFractions = new double[numComponents];
        this.moleFractions[0] = 1.0 - x_B;
        this.moleFractions[1] = x_B;
        // Other components remain 0 (or user should use setMoleFractions directly)
    }
    
    invalidateMinimization();
}

/**
 * Sets full mole fraction vector (for K ≥ 3).
 */
public void setMoleFractions(double[] fractions) throws IllegalArgumentException {
    if (fractions == null || fractions.length != numComponents) {
        throw new IllegalArgumentException(
            "Mole fractions length mismatch: got " + (fractions == null ? 0 : fractions.length) +
            ", expected " + numComponents);
    }
    
    double sum = 0;
    for (double x : fractions) {
        if (x < 0 || x > 1) {
            throw new IllegalArgumentException("Mole fractions must be in [0,1]");
        }
        sum += x;
    }
    if (Math.abs(sum - 1.0) > 1.0e-9) {
        throw new IllegalArgumentException("Mole fractions don't sum to 1: " + sum);
    }
    
    this.moleFractions = fractions.clone();
    invalidateMinimization();
}

/**
 * Sets convergence tolerance.
 */
public void setTolerance(double tol) throws IllegalArgumentException {
    if (tol <= 0 || tol > 1.0e-3) {
        throw new IllegalArgumentException("Tolerance out of range: " + tol);
    }
    this.tolerance = tol;
    invalidateMinimization();
}

/**
 * Invalidates cached minimization results.
 * Next call to getEquilibrium* will trigger re-minimization.
 */
private void invalidateMinimization() {
    this.isMinimized = false;
    this.equilibriumCFs = null;
    this.equilibrium = null;
}
```

### 2.2 Automatic Lazy Minimization

```java
/**
 * Ensures model is minimized.
 * If parameters changed since last minimization, re-minimizes.
 * Safe to call multiple times — only computes if needed.
 */
public synchronized void ensureMinimized() throws Exception {
    if (isMinimized && equilibriumCFs != null && equilibrium != null) {
        return;  // Already minimized, all results cached
    }
    
    // Validate parameters are set
    if (eci == null || Double.isNaN(temperature) || moleFractions == null) {
        throw new IllegalStateException(
            "Cannot minimize: missing parameters\n" +
            "  ECI set: " + (eci != null) + "\n" +
            "  T set: " + !Double.isNaN(temperature) + "\n" +
            "  x set: " + (moleFractions != null));
    }
    
    // Perform minimization
    minimize();
    
    if (!isMinimized) {
        throw new Exception("Minimization failed: " + lastConvergenceStatus);
    }
}

/**
 * Internal minimization routine (private).
 */
private void minimize() {
    long startTime = System.nanoTime();
    
    try {
        // 1. Generate good initial guess using K-component random CF formula
        double[] initialCFs = evaluateRandomCFsForComposition(moleFractions);
        
        // 2. Run Newton-Raphson solver
        CVMSolverResult result = NewtonRaphsonSolverSimple.solve(
            initialCFs,
            moleFractions, numComponents,
            temperature, eci,
            mhdis, kb, mh, lc,
            cmat, lcv, wcv,
            tcdis, tcf, ncf,
            lcf, cfBasisIndices,
            tolerance
        );
        
        // 3. Cache results
        if (result.hasConverged()) {
            this.equilibriumCFs = result.getEquilibriumCFs();
            this.equilibrium = result.getThermodynamics();
            this.isMinimized = true;
            this.lastIterations = result.getIterations();
            this.lastGradientNorm = result.getGradientNorm();
            this.lastConvergenceStatus = "OK";
            this.lastMinimizationTime = System.nanoTime() - startTime;
            
            logMinimizationSuccess();
        } else {
            this.isMinimized = false;
            this.lastConvergenceStatus = result.getConvergenceError();
            this.lastIterations = result.getIterations();
            this.lastGradientNorm = result.getGradientNorm();
            logMinimizationFailure();
        }
    } catch (Exception e) {
        this.isMinimized = false;
        this.lastConvergenceStatus = "Exception: " + e.getMessage();
        System.err.println("[CVMPhaseModel.minimize] Error: " + e);
    }
}

/**
 * Generate random CFs for current composition (K-component generalized).
 */
private double[] evaluateRandomCFsForComposition(double[] fractions) {
    // Build basis vectors for point operators
    int[] basis = RMatrixCalculator.buildBasis(numComponents);
    
    // Compute K-1 point CFs (site operators)
    double[] pointCFs = new double[numComponents - 1];
    for (int k = 0; k < numComponents - 1; k++) {
        double sigma = 0.0;
        for (int i = 0; i < numComponents; i++) {
            sigma += fractions[i] * basis[i]; // basis^(k+1)
        }
        pointCFs[k] = Math.pow(sigma, k + 1);
    }
    
    // Compute random CFs using cfBasisIndices
    double[] randomCFs = new double[ncf];
    for (int i = 0; i < ncf; i++) {
        randomCFs[i] = 1.0;
        for (int j : cfBasisIndices[i]) {
            if (j > 0 && j <= numComponents - 1) {
                randomCFs[i] *= pointCFs[j - 1];
            }
        }
    }
    
    return randomCFs;
}

private void logMinimizationSuccess() {
    System.out.println("[CVMPhaseModel] Minimization successful");
    System.out.println("  T: " + temperature + " K");
    System.out.println("  x: " + Arrays.toString(moleFractions));
    System.out.println("  Iterations: " + lastIterations);
    System.out.println("  ||∇G||: " + String.format("%8e", lastGradientNorm));
    System.out.println("  Time: " + (lastMinimizationTime / 1_000_000) + " ms");
    System.out.println("  G_eq: " + String.format("%12.6e", equilibrium.G) + " J/mol");
}

private void logMinimizationFailure() {
    System.err.println("[CVMPhaseModel] Minimization FAILED");
    System.err.println("  Status: " + lastConvergenceStatus);
    System.err.println("  Iterations: " + lastIterations);
}
```

---

## 3. Query Interface (Always Returns Equilibrium Values)

### 3.1 Main Thermodynamic Properties

```java
/**
 * Gets Gibbs energy of mixing at current equilibrium.
 * Automatically minimizes if parameters changed.
 */
public double getEquilibriumG() throws Exception {
    ensureMinimized();
    return equilibrium.G;
}

public double getEquilibriumH() throws Exception {
    ensureMinimized();
    return equilibrium.H;
}

public double getEquilibriumS() throws Exception {
    ensureMinimized();
    return equilibrium.S;
}

public double getHelmholtzF() throws Exception {
    ensureMinimized();
    return equilibrium.H - temperature * equilibrium.S;
}

/**
 * Full equilibrium state as a result object.
 */
public EquilibriumState getEquilibriumState() throws Exception {
    ensureMinimized();
    return new EquilibriumState(
        temperature,
        moleFractions,
        equilibriumCFs,
        equilibrium,
        lastIterations,
        lastGradientNorm,
        lastMinimizationTime
    );
}

/**
 * Immutable result object combining all equilibrium properties.
 */
public static class EquilibriumState {
    public final double temperature;
    public final double[] moleFractions;
    public final double[] correlationFunctions;  // [ncf]
    public final double G, H, S;
    public final double[] gradientG;
    public final double[][] hessianG;
    public final int iterations;
    public final double convergenceMeasure;
    public final long computationTime;  // nanos
    
    // Constructor...
}
```

### 3.2 Microstate Properties (CVs, SROs)

```java
/**
 * Cluster variables at equilibrium.
 */
public double[][][] getEquilibriumCVs() throws Exception {
    ensureMinimized();
    double[] uFull = buildFullCFVector(equilibriumCFs);
    return ClusterVariableEvaluator.evaluate(uFull, cmat, lcv, tcdis, lc);
}

/**
 * Short-range order parameters.
 */
public double[] getSROs() throws Exception {
    ensureMinimized();
    double[][][] cvs = getEquilibriumCVs();
    double[] sro = new double[tcdis];
    
    for (int t = 0; t < tcdis - 1; t++) {  // Exclude point type
        for (int j = 0; j < lc[t]; j++) {
            for (int v = 0; v < lcv[t][j]; v++) {
                sro[t] += wcv.get(t).get(j)[v] * cvs[t][j][v];
            }
        }
    }
    return sro;
}

/**
 * Stability matrix: determinant tells if phase is stable.
 * For stability: all eigenvalues of ∇²G must be positive.
 */
public double[][] getStabilityMatrix() throws Exception {
    ensureMinimized();
    return equilibrium.Gcuu;
}

public boolean isStable() throws Exception {
    ensureMinimized();
    // Check all eigenvalues positive (Hessian must be positive definite)
    Eigen eigen = new EigenDecomposition(new Array2DRowRealMatrix(equilibrium.Gcuu));
    double[] eigenvalues = eigen.getRealEigenvalues();
    for (double ev : eigenvalues) {
        if (ev < -1.0e-6) return false;  // Negative eigenvalue → unstable
    }
    return true;
}

/**
 * Activity coefficients γ_i for each component.
 */
public double[] getActivityCoefficients() throws Exception {
    ensureMinimized();
    // γ_i = exp(μ_i / RT) where μ_i is chemical potential
    // μ_i = (∂G/∂n_i)_{T,P}
    // Requires composition-CF Jacobian (deferred to detailed implementation)
    double[] gamma = new double[numComponents];
    // TODO: implement with proper thermodynamic partials
    return gamma;
}
```

### 3.3 Model Introspection

```java
// =========================================================================
// CURRENT STATE ACCESSORS (non-minimization)
// =========================================================================

public double getTemperature() { return temperature; }
public double[] getMoleFractions() { return moleFractions.clone(); }
public double[] getECI() { return eci.clone(); }
public int getNumComponents() { return numComponents; }
public int getNumCFs() { return ncf; }
public int getTotalCFs() { return tcf; }
public int getNumClusterTypes() { return tcdis; }
public double getTolerance() { return tolerance; }
public SystemIdentity getSystem() { return system; }

/**
 * Minimization status (diagnostic).
 */
public boolean isMinimized() { return isMinimized; }
public int getLastIterations() throws IllegalStateException {
    if (!isMinimized) throw new IllegalStateException("Not yet minimized");
    return lastIterations;
}
public double getLastGradientNorm() throws IllegalStateException {
    if (!isMinimized) throw new IllegalStateException("Not yet minimized");
    return lastGradientNorm;
}
public long getLastMinimizationTimeMs() {
    return lastMinimizationTime / 1_000_000;
}

/**
 * Summary report.
 */
public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== CVMPhaseModel ===\n");
    sb.append("System: ").append(system).append("\n");
    sb.append("Components: ").append(numComponents).append("\n");
    sb.append("Cluster types: ").append(tcdis).append("\n");
    sb.append("CFs: ").append(ncf).append(" (total ").append(tcf).append(")\n");
    sb.append("\nCurrent Parameters:\n");
    sb.append("  T: ").append(temperature).append(" K\n");
    sb.append("  x: ").append(Arrays.toString(moleFractions)).append("\n");
    sb.append("  ECI length: ").append(eci == null ? "not set" : eci.length).append("\n");
    sb.append("\nMinimization Status:\n");
    sb.append("  Minimized: ").append(isMinimized).append("\n");
    if (isMinimized) {
        try {
            sb.append("  G_eq: ").append(String.format("%.6e", getEquilibriumG())).append(" J/mol\n");
            sb.append("  H_eq: ").append(String.format("%.6e", getEquilibriumH())).append(" J/mol\n");
            sb.append("  S_eq: ").append(String.format("%.6e", getEquilibriumS())).append(" J/(mol·K)\n");
            sb.append("  ||∇G||: ").append(String.format("%.2e", getLastGradientNorm())).append("\n");
            sb.append("  Iterations: ").append(getLastIterations()).append("\n");
        } catch (Exception e) {
            sb.append("  [Query failed: ").append(e.getMessage()).append("]\n");
        }
    }
    return sb.toString();
}
```

---

## 4. Data Flow with Parameter Updates

```
┌────────────────────────────────────────────────────────────────┐
│ User: "Create a CVM model for this system"                     │
└────┬─────────────────────────────────────────────────────────────┘
     │
     ▼
┌────────────────────────────────────────────────────────────────┐
│ CVMPhaseModel.create(context, eci_init, T_init, x_init)       │
│  - Extract AllClusterData (Stages 1-3)                         │
│  - Set initial parameters                                      │
│  - Trigger first minimization                                  │
└────┬─────────────────────────────────────────────────────────────┘
     │
     ▼
┌────────────────────────────────────────────────────────────────┐
│ model.minimize()  [FIRST TIME]                                 │
│  ├─ Generate random CFs for composition                        │
│  ├─ RunNewtonRaphsonSolver                                     │
│  ├─ Cache: equilibriumCFs, equilibrium (G, H, S, ∇G, ∇²G)    │
│  └─ isMinimized = true                                         │
└────┬─────────────────────────────────────────────────────────────┘
     │
     │ Model ready for queries
     │
     ▼
┌────────────────────────────────────────────────────────────────┐
│ User: "Get properties at current (T, x, ECI)"                  │
│                                                                 │
│ model.getEquilibriumG()                                        │
│  └─ ensureMinimized()  [already true, skip]                    │
│  └─ return cached equilibrium.G                                │
│                                                                 │
│ model.getEquilibriumS()                                        │
│  └─ ensureMinimized()  [already true, skip]                    │
│  └─ return cached equilibrium.S                                │
└────┬─────────────────────────────────────────────────────────────┘
     │
     │ No re-minimization needed
     │
     ▼
┌────────────────────────────────────────────────────────────────┐
│ User: "Change T to 500K"                                       │
│                                                                 │
│ model.setTemperature(500.0)                                    │
│  └─ this.temperature = 500.0                                   │
│  └─ isMinimized = false              [INVALIDATE]             │
│  └─ equilibrium = null                                         │
└────┬─────────────────────────────────────────────────────────────┘
     │
     ▼
┌────────────────────────────────────────────────────────────────┐
│ User: "Get properties at new T"                                │
│                                                                 │
│ model.getEquilibriumG()                                        │
│  └─ ensureMinimized()  [false, proceed]                        │
│  └─ minimize()  [SECOND TIME, with new T]                     │
│     ├─ Generate random CFs for composition                     │
│     ├─ Run Newton-Raphson (now at 500K)                       │
│     ├─ Cache new results                                       │
│     └─ isMinimized = true                                      │
│  └─ return new equilibrium.G  [different from before]         │
└────┬─────────────────────────────────────────────────────────────┘
                        │
                        ▼ [Can repeat for any parameter change]
```

---

## 5. Usage Pattern (User Perspective)

```java
// ===== SETUP =====

// Load system data once
CVMCalculationContext context = service.prepareCVM(request).getValue();

// Create model with initial parameters
CVMPhaseModel model = CVMPhaseModel.create(
    context,
    eci_data,           // System parameters (CECs)
    1000.0,             // T = 1000 K
    0.5                 // x = 0.5
);

// ===== QUERY AT INITIAL STATE =====
System.out.println(model.getSummary());
System.out.println("G @ T=1000, x=0.5: " + model.getEquilibriumG());
System.out.println("H @ T=1000, x=0.5: " + model.getEquilibriumH());

// ===== SCAN OVER TEMPERATURE =====
for (double T = 300; T <= 1500; T += 100) {
    model.setTemperature(T);  // Triggers re-minimization on next query
    
    double G = model.getEquilibriumG();      // Auto-minimizes at new T
    double S = model.getEquilibriumS();      // Uses cached results
    double[] sro = model.getSROs();          // Uses cached results
    
    System.out.println("T=" + T + "K: G=" + G + " S=" + S);
}

// ===== SCAN OVER COMPOSITION =====
for (double x = 0.1; x <= 0.9; x += 0.1) {
    model.setComposition(x);  // Triggers re-minimization on next query
    
    EquilibriumState state = model.getEquilibriumState();  // Auto-minimizes at new x
    
    System.out.println("x=" + x + ": G=" + state.G + " stable=" + model.isStable());
}

// ===== CHANGE SYSTEM PARAMETERS (CECs) =====
double[] newECI = loadNewECIFromDatabase();
model.setECI(newECI);           // Triggers re-minimization

double G_newECI = model.getEquilibriumG();  // Auto-minimizes with new ECI

// ===== BATCH ANALYSIS =====
model.setTemperature(800.0);
for (double x = 0; x <= 1.0; x += 0.05) {
    model.setComposition(x);
    System.out.println("T=800, x=" + x + ": " + model.getEquilibriumG());
}
```

---

## 6. Key Design Properties

| Property | Implementation |
|----------|----------------|
| **Cluster Data** | Immutable, loaded once from AllClusterData |
| **System Parameters (ECI)** | Mutable, setter triggers re-minimization |
| **Macro Parameters (T, x)** | Mutable, setter triggers re-minimization |
| **Equilibrium State** | Cached, invalidated on parameter change |
| **Minimization Trigger** | Lazy — only on next query if params changed |
| **Thread Safety** | `synchronized ensureMinimized()` |
| **Memory** | Single result object returned, no copies |
| **Error Handling** | Exceptions on parameter validation, minimization failure |

---

## 7. Integration with Calculation Service

```java
// ===== CalculationService (updated) =====

/**
 * New method: prepares CVM model ready to use.
 * Returns model directly, not context.
 */
public CVMPhaseModel createCVMModel(CVMCalculationRequest request) {
    // 1. Load context and validate
    CalculationResult<CVMCalculationContext> ctxResult = prepareCVM(request);
    if (!ctxResult.isSuccess()) {
        throw new IllegalStateException(ctxResult.getError());
    }
    
    CVMCalculationContext context = ctxResult.getValue();
    double[] eci = loadECI(...);
    
    // 2. Create and return model (minimization happens automatically)
    return CVMPhaseModel.create(
        context,
        eci,
        request.getTemperature(),
        request.getComposition()
    );
}

/**
 * Alternative: if user already has context.
 * Supports scanning multiple temperatures/compositions.
 */
public CVMPhaseModel createCVMModel(CVMCalculationContext context, double[] eci) {
    return CVMPhaseModel.create(
        context,
        eci,
        context.getTemperature(),
        context.getComposition()
    );
}
```

---

## 8. Benefits Summary

✅ **User-Friendly**: Create once, change parameters, get results
✅ **Automatic Minimization**: No solver calls from user code
✅ **Lazy Evaluation**: Only re-minimizes when parameters actually change
✅ **Cached Results**: Multiple queries don't re-compute
✅ **K-Component**: Works for binary, ternary, quaternary, etc.
✅ **Clear Semantics**: Model = state holder + thermodynamic API
✅ **Flexible**: Scan T, x, ECI without creating new models
✅ **Type-Safe**: EquilibriumState bundles all results together
✅ **Diagnostic Info**: Iteration count, gradient norm, timing available
