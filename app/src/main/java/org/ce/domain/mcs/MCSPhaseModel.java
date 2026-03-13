package org.ce.domain.mcs;

import java.util.function.Consumer;
import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.model.result.EngineMetrics;
import org.ce.domain.model.result.EquilibriumState;

import java.util.logging.Logger;

/**
 * Central thermodynamic model for MCS (Monte Carlo Simulation) calculations.
 *
 * <p><b>User's Mental Model:</b>
 * Create an MCS phase model for a given system, provide system parameters (ECIs)
 * and macro parameters (T, composition, supercell size), and query for equilibrium
 * thermodynamic quantities. When parameters change, the model automatically
 * re-runs the MC simulation to find the new equilibrium.
 *
 * <p>This class mirrors the {@code CVMPhaseModel} pattern — same inputs, same
 * outputs ({@link EquilibriumState}), different internal engine (Metropolis MC
 * instead of Newton-Raphson). Both models share the same unified vision:
 * <pre>
 *   INPUTS:  AllClusterData + ECI (ncf-length) + T + x[]
 *   ENGINE:  MCS → Metropolis MC sweeps
 *   OUTPUTS: EquilibriumState (CFs, Hmix; no G or S — physics boundary)
 * </pre>
 *
 * <p><b>Data Ownership:</b>
 * <ul>
 *   <li><b>Immutable:</b> Cluster data (AllClusterData) and ECI — fixed at creation</li>
 *   <li><b>Mutable:</b> Macro parameters (T, x) and engine parameters
 *       (supercellSize, nEquil, nAvg) — can change anytime</li>
 *   <li><b>Cached:</b> Equilibrium results — invalidated when parameters change</li>
 * </ul>
 *
 * <p><b>Typical Usage:</b>
 * <pre>{@code
 * MCSPhaseModel model = MCSPhaseModel.create(allData, eci, numComponents,
 *                                            800.0, new double[]{0.5, 0.5});
 * EquilibriumState state = model.getEquilibriumState();
 * double hmix = state.enthalpy();
 *
 * model.setTemperature(1000.0);
 * EquilibriumState newState = model.getEquilibriumState(); // re-runs MC
 * }</pre>
 *
 * <p><b>Stochastic Note:</b>
 * Each re-run with a fixed seed is reproducible. Changing parameters and calling
 * {@link #getEquilibriumState()} triggers a fresh MC run with the stored seed.
 * Call {@link #setSeed(long)} to request a new random trajectory.
 *
 * @since 2.1
 * @see CVMPhaseModel
 * @see MCSRunner
 */
public class MCSPhaseModel {

    private static final Logger LOG = Logger.getLogger(MCSPhaseModel.class.getName());

    /** Gas constant R = 8.314 J/(mol·K) — must match ECI units. */
    private static final double R = 8.314;

    // =========================================================================
    // IMMUTABLE: Cluster data and interactions — fixed at creation
    // =========================================================================

    private final AllClusterData allData;
    private final double[] ncfEci;          // ncf-length ECI, defensive copy
    private final int numComponents;

    // =========================================================================
    // MUTABLE: Macro parameters — invalidate cache on change
    // =========================================================================

    private double temperature;             // Kelvin
    private double[] moleFractions;         // length numComponents

    // =========================================================================
    // MUTABLE: Engine parameters — invalidate cache on change
    // =========================================================================

    private int supercellSize = 4;          // L (N = 2·L³ for BCC)
    private int nEquil = 5000;              // equilibration sweeps
    private int nAvg = 10000;               // averaging sweeps
    private long seed;                      // random seed for reproducibility
    private Consumer<MCSUpdate> updateListener = null;  // optional live-update callback

    // =========================================================================
    // CACHED: Equilibrium results — null when dirty
    // =========================================================================

    private boolean isDirty = true;
    private EquilibriumState cachedState;

    // =========================================================================
    // FACTORY METHOD
    // =========================================================================

    /**
     * Creates an MCS phase model and runs the first simulation.
     *
     * @param allData       pre-computed cluster data (Stages 1-3)
     * @param ncfEci        ncf-length effective cluster interactions (J/mol)
     * @param numComponents number of chemical components (K ≥ 2)
     * @param temperature   initial temperature in Kelvin
     * @param moleFractions initial composition fractions (length must equal numComponents)
     * @return MCSPhaseModel with first simulation already run
     */
    public static MCSPhaseModel create(
            AllClusterData allData,
            double[] ncfEci,
            int numComponents,
            double temperature,
            double[] moleFractions) {

        if (allData == null) throw new IllegalArgumentException("allData must not be null");
        if (ncfEci == null) throw new IllegalArgumentException("ncfEci must not be null");

        MCSPhaseModel model = new MCSPhaseModel(allData, ncfEci, numComponents);
        model.setTemperature(temperature);
        model.setMoleFractions(moleFractions);

        // Auto-generate seed from current time for first run
        model.seed = System.nanoTime();

        // First run
        model.ensureRun();
        return model;
    }

    /**
     * Builds an MCS phase model with all engine parameters set but WITHOUT running
     * the simulation. The caller must call {@link #getEquilibriumState()} (or wire
     * an update listener first) to trigger the first run.
     *
     * <p>Use this in preference to {@link #create} when the caller needs to attach
     * an update listener before the simulation starts, or wants to avoid the
     * redundant first run that {@code create} performs with default engine parameters.</p>
     *
     * @param allData          pre-computed cluster data (Stages 1-3)
     * @param ncfEci           ncf-length effective cluster interactions (J/mol)
     * @param numComponents    number of chemical components (K ≥ 2)
     * @param temperature      initial temperature in Kelvin
     * @param moleFractions    initial composition fractions (length == numComponents)
     * @param supercellSize    supercell edge length L (N = 2·L³ for BCC)
     * @param equilibrationSweeps  number of equilibration MC sweeps
     * @param averagingSweeps  number of averaging MC sweeps
     * @param seed             random seed for reproducibility
     * @return MCSPhaseModel ready to run — simulation NOT yet started
     */
    public static MCSPhaseModel buildOnly(
            AllClusterData allData,
            double[] ncfEci,
            int numComponents,
            double temperature,
            double[] moleFractions,
            int supercellSize,
            int equilibrationSweeps,
            int averagingSweeps,
            long seed) {

        if (allData == null) throw new IllegalArgumentException("allData must not be null");
        if (ncfEci == null)  throw new IllegalArgumentException("ncfEci must not be null");

        MCSPhaseModel model = new MCSPhaseModel(allData, ncfEci, numComponents);
        model.temperature   = temperature;
        model.moleFractions = moleFractions.clone();
        model.supercellSize = supercellSize;
        model.nEquil        = equilibrationSweeps;
        model.nAvg          = averagingSweeps;
        model.seed          = seed;
        // isDirty already true — first call to getEquilibriumState() will run
        return model;
    }

    // =========================================================================
    // CONSTRUCTOR (private — only via factory)
    // =========================================================================

    private MCSPhaseModel(AllClusterData allData, double[] ncfEci, int numComponents) {
        this.allData = allData;
        this.ncfEci = ncfEci.clone();
        this.numComponents = numComponents;
    }

    // =========================================================================
    // PARAMETER SETTERS (invalidate cache)
    // =========================================================================

    /**
     * Sets temperature. Invalidates cached result — next query re-runs.
     *
     * @param T_K temperature in Kelvin (must be positive)
     */
    public void setTemperature(double T_K) {
        if (T_K <= 0) throw new IllegalArgumentException("Temperature must be positive: " + T_K);
        this.temperature = T_K;
        invalidate();
    }

    /**
     * Sets composition as a full mole fraction array.
     * Invalidates cached result — next query re-runs.
     *
     * @param xFrac mole fractions for all components (length must equal numComponents)
     */
    public void setMoleFractions(double[] xFrac) {
        if (xFrac == null || xFrac.length != numComponents) {
            throw new IllegalArgumentException(
                    "moleFractions length must equal numComponents ("
                    + numComponents + "), got: " + (xFrac == null ? 0 : xFrac.length));
        }
        this.moleFractions = xFrac.clone();
        invalidate();
    }

    /**
     * Sets composition for binary systems (K=2) using B-fraction shorthand.
     * Invalidates cached result — next query re-runs.
     *
     * @param xB mole fraction of component B in [0, 1]
     */
    public void setCompositionBinary(double xB) {
        if (numComponents != 2) {
            throw new IllegalArgumentException(
                    "setCompositionBinary is only valid for K=2, this model has K=" + numComponents);
        }
        setMoleFractions(new double[]{1.0 - xB, xB});
    }

    /**
     * Sets supercell size L (N = 2·L³ for BCC). Invalidates cache.
     */
    public void setSupercellSize(int L) {
        if (L < 1) throw new IllegalArgumentException("supercellSize must be >= 1");
        this.supercellSize = L;
        invalidate();
    }

    /**
     * Sets number of equilibration sweeps. Invalidates cache.
     */
    public void setEquilibrationSweeps(int nEquil) {
        if (nEquil <= 0) throw new IllegalArgumentException("nEquil must be positive");
        this.nEquil = nEquil;
        invalidate();
    }

    /**
     * Sets number of averaging sweeps. Invalidates cache.
     */
    public void setAveragingSweeps(int nAvg) {
        if (nAvg <= 0) throw new IllegalArgumentException("nAvg must be positive");
        this.nAvg = nAvg;
        invalidate();
    }

    /**
     * Sets random seed for the next run. Invalidates cache.
     * Use to get a different stochastic trajectory at the same state point.
     */
    /**
     * Sets an optional live-update callback, fired after each MC sweep.
     * Connects to {@link MCSRunner.Builder#updateListener}. Pass {@code null} to disable.
     * Does not invalidate cached state — takes effect on the next simulation run.
     */
    public void setUpdateListener(Consumer<MCSUpdate> listener) {
        this.updateListener = listener;
    }

    public void setSeed(long seed) {
        this.seed = seed;
        invalidate();
    }

    // =========================================================================
    // EQUILIBRIUM QUERIES
    // =========================================================================

    /**
     * Returns the equilibrium state, running the MC simulation if parameters
     * have changed since the last run.
     *
     * @return EquilibriumState with CFs, Hmix, Cv, and McsMetrics
     */
    public EquilibriumState getEquilibriumState() {
        ensureRun();
        return cachedState;
    }

    /**
     * Convenience: set T and x[], then return equilibrium state.
     */
    public EquilibriumState minimize(double T, double[] xFrac) {
        setTemperature(T);
        setMoleFractions(xFrac);
        return getEquilibriumState();
    }

    /**
     * Convenience for binary systems: set T and x_B, then return equilibrium state.
     */
    public EquilibriumState minimizeBinary(double T, double xB) {
        setTemperature(T);
        setCompositionBinary(xB);
        return getEquilibriumState();
    }

    // =========================================================================
    // ENGINE
    // =========================================================================

    /**
     * Runs MC simulation if the cache is dirty (parameters changed).
     */
    private void ensureRun() {
        if (!isDirty && cachedState != null) return;

        if (temperature <= 0 || moleFractions == null) {
            throw new IllegalStateException(
                    "MCSPhaseModel: temperature and moleFractions must be set before running");
        }

        LOG.fine("MCSPhaseModel.ensureRun — T=" + temperature
                + " K, x[1]=" + moleFractions[1]
                + ", L=" + supercellSize
                + ", nEquil=" + nEquil + ", nAvg=" + nAvg);

        long startNs = System.nanoTime();
        MCSRunner.Builder runnerBuilder = MCSRunner.builder()
                .clusterData(allData.getStage1().getDisClusterData())
                .eci(ncfEci)
                .numComp(numComponents)
                .T(temperature)
                .composition(moleFractions)
                .L(supercellSize)
                .nEquil(nEquil)
                .nAvg(nAvg)
                .seed(seed)
                .R(R);
        if (updateListener != null) {
            runnerBuilder.updateListener(updateListener);
        }
        MCResult mcResult = runnerBuilder.build().run();
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        cachedState = EquilibriumState.fromMcs(
                mcResult.getTemperature(),
                mcResult.getComposition(),
                mcResult.getAvgCFs(),
                mcResult.getHmixPerSite(),
                mcResult.getHeatCapacityPerSite(),
                mcResult.getAcceptRate(),
                mcResult.getNEquilSweeps(),
                mcResult.getNAvgSweeps(),
                mcResult.getSupercellSize(),
                mcResult.getNSites(),
                mcResult.getEnergyPerSite());

        isDirty = false;

        if (cachedState.metrics() instanceof EngineMetrics.McsMetrics m) {
            LOG.fine("MCSPhaseModel.ensureRun — DONE in " + elapsedMs + " ms"
                    + ": acceptRate=" + String.format("%.3f", m.acceptRate())
                    + ", Hmix/site=" + String.format("%.6f", cachedState.enthalpy()));
        }
    }

    private void invalidate() {
        isDirty = true;
        cachedState = null;
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public double getTemperature()    { return temperature; }
    public double[] getMoleFractions(){ return moleFractions != null ? moleFractions.clone() : null; }
    public int getSupercellSize()     { return supercellSize; }
    public int getEquilibrationSweeps(){ return nEquil; }
    public int getAveragingSweeps()   { return nAvg; }
    public long getSeed()             { return seed; }
    public int getNumComponents()     { return numComponents; }
    public boolean isRunCompleted()   { return !isDirty && cachedState != null; }
}