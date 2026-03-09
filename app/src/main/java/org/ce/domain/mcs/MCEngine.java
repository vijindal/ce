package org.ce.domain.mcs;

import org.ce.domain.identification.geometry.Cluster;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Metropolis Monte Carlo engine: equilibration then averaging sweeps.
 * Works for any number of chemical components and any structure.
 *
 * <h2>Sweep definition</h2>
 * <p>One sweep = {@code N} attempted moves (N = lattice sites).</p>
 *
 * <h2>Ensemble selection</h2>
 * <ul>
 *   <li>{@code useFlipStep = false} (default) â€” canonical
 *       {@link ExchangeStep}: conserves composition, picks two
 *       sites of different species and swaps them.</li>
 *   <li>{@code useFlipStep = true} â€” grand-canonical {@link FlipStep}:
 *       changes one site's occupation to any other species.</li>
 * </ul>
 *
 * @author  CE Project
 * @version 1.0
 * @see     ExchangeStep
 * @see     FlipStep
 * @see     MCSampler
 * @see     MCSRunner
 */
public class MCEngine {

    private static final Logger LOG = Logger.getLogger(MCEngine.class.getName());

    private final EmbeddingData       emb;
    private final double[]            eci;
    private final List<List<Cluster>> orbits;
    private final int                 numComp;
    private final double              T;
    private final double              R;
    private final int                 nEquil;
    private final int                 nAvg;
    private final boolean             useFlipStep;
    private final double[]            deltaMu;
    private final Random              rng;
    
    // MCS monitoring callback and state
    private Consumer<MCSUpdate>       updateListener = null;
    private RollingWindow             deltaEWindow = new RollingWindow(500);
    
    // Cancellation support for cooperative shutdown
    private BooleanSupplier           cancellationCheck = () -> false;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

        /** Canonical constructor (ExchangeStep). */
        public MCEngine(EmbeddingData emb, double[] eci,
                    List<List<Cluster>> orbits, int numComp,
                    double T, int nEquil, int nAvg, Random rng) {
           this(emb, eci, orbits, numComp, T, nEquil, nAvg,
               false, new double[numComp], 1.0, rng);
        }

    /** Full constructor with ensemble selection. */
    public MCEngine(EmbeddingData emb, double[] eci,
                    List<List<Cluster>> orbits, int numComp,
                    double T, int nEquil, int nAvg,
                    boolean useFlipStep, double[] deltaMu, double R, Random rng) {
        if (T      <= 0) throw new IllegalArgumentException("T must be > 0");
        if (nEquil <  0) throw new IllegalArgumentException("nEquil must be >= 0");
        if (nAvg   <  1) throw new IllegalArgumentException("nAvg must be >= 1");
        this.emb         = emb;
        this.eci         = eci;
        this.orbits      = orbits;
        this.numComp     = numComp;
        this.T           = T;
        this.R           = R;
        this.nEquil      = nEquil;
        this.nAvg        = nAvg;
        this.useFlipStep = useFlipStep;
        this.deltaMu     = deltaMu.clone();
        this.rng         = rng;
        this.hmixCoeff   = emb.computeHmixCoeff(eci, orbits.size());
    }

    // -------------------------------------------------------------------------
    // Run
    // -------------------------------------------------------------------------

    /**
     * Registers a callback for real-time MCS update events.
     * Used for GUI monitoring and visualization.
     * @param listener callback to receive MCSUpdate events
     */
    public void setUpdateListener(Consumer<MCSUpdate> listener) {
        this.updateListener = listener;
    }
    
    /**
     * Sets a cancellation check supplier.
     * The supplier is tested every sweep; if it returns true, a CancellationException is thrown.
     * @param check supplier that returns true when cancellation is requested
     */
    public void setCancellationCheck(BooleanSupplier check) {
        this.cancellationCheck = check != null ? check : () -> false;
    }

    /**
     * Runs equilibration + averaging and returns the result.
     *
     * @param config  initial configuration; modified in-place
     * @param sampler sampler to fill during averaging phase
     * @return {@link MCResult}
     */
    public MCResult run(LatticeConfig config, MCSampler sampler) {
        LOG.fine("MCEngine.run — ENTER: N=" + config.getN() + ", T=" + T + " K"
                + ", nEquil=" + nEquil + ", nAvg=" + nAvg
                + ", ensemble=" + (useFlipStep ? "GRAND_CANONICAL" : "CANONICAL"));
        MCResult result = useFlipStep ? runFlip(config, sampler) : runExchange(config, sampler);
        LOG.fine("MCEngine.run — EXIT: acceptRate=" + String.format("%.3f", result.getAcceptRate()));
        return result;
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private MCResult runExchange(LatticeConfig config, MCSampler sampler) {
        ExchangeStep step = new ExchangeStep(emb, eci, orbits, numComp, T, R, rng);
        int N = config.getN();

        // Initialize MCS monitoring  
        deltaEWindow.clear();
        long startTime = System.currentTimeMillis();
        
        // Calculate initial total energy (expensive, done once at start)
        double currentEnergy = LocalEnergyCalc.totalEnergy(config, emb, eci, orbits);
        LOG.fine("MCEngine.runExchange — ENTER: N=" + N + ", E_initial=" + String.format("%.4f", currentEnergy) + " eV");

        for (int s = 0; s < nEquil; s++) {
            // Check for cancellation at the start of each sweep
            if (cancellationCheck.getAsBoolean()) {
                throw new CancellationException("MCS calculation cancelled during equilibration (sweep " + s + ")");
            }
            
            double sweepDeltaE = 0.0;
            
            for (int m = 0; m < N; m++) {
                double stepDeltaE = step.attempt(config);
                deltaEWindow.add(stepDeltaE);  // Track all attempts; rejected moves contribute 0.0
                currentEnergy += stepDeltaE;  // Accumulate (0 if rejected)
                sweepDeltaE += stepDeltaE;   // Track sweep aggregate
            }
            
            // Emit update after each sweep (step = sweep number)
            if (updateListener != null) {
                long elapsedMs = System.currentTimeMillis() - startTime;
                int sweepNum = s + 1;  // Sweep number (1, 2, 3, ...)
                
                MCSUpdate update = new MCSUpdate(
                    sweepNum,
                    currentEnergy,
                    sweepDeltaE,
                    deltaEWindow.getStdDev(),
                    deltaEWindow.getMean(),
                    MCSUpdate.Phase.EQUILIBRATION,
                    step.acceptRate(),
                    System.currentTimeMillis(),
                    elapsedMs
                );
                updateListener.accept(update);
            }
        }

        LOG.fine("MCEngine.runExchange — EQUIL DONE: sweeps=" + nEquil
                + ", E_final=" + String.format("%.4f", currentEnergy) + " eV"
                + ", acceptRate=" + String.format("%.3f", step.acceptRate()));
        step.resetCounters();
        sampler.reset();
        LOG.fine("MCEngine.runExchange — AVERAGING: " + nAvg + " sweeps");

        for (int s = 0; s < nAvg; s++) {
            // Check for cancellation at the start of each sweep
            if (cancellationCheck.getAsBoolean()) {
                throw new CancellationException("MCS calculation cancelled during averaging (sweep " + (nEquil + s) + ")");
            }
            
            double sweepDeltaE = 0.0;
            
            for (int m = 0; m < N; m++) {
                double stepDeltaE = step.attempt(config);
                deltaEWindow.add(stepDeltaE);  // Track all attempts; rejected moves contribute 0.0
                currentEnergy += stepDeltaE;  // Accumulate (0 if rejected)
                sweepDeltaE += stepDeltaE;   // Track sweep aggregate
            }
            sampler.sample(config, emb);

            // Emit update after each sweep.
            // currentEnergy = H_total tracked via incremental ΔE (monitoring only).
            // ΔHmix = ΔH_total for canonical ensemble (reference x·H_pure is fixed),
            // so convergence trends are valid. Final Hmix comes from time-averaged CFs.
            if (updateListener != null) {
                long elapsedMs = System.currentTimeMillis() - startTime;
                int sweepNum = nEquil + s + 1;  // Cumulative sweep count

                MCSUpdate update = new MCSUpdate(
                    sweepNum,
                    currentEnergy,
                    sweepDeltaE,
                    deltaEWindow.getStdDev(),
                    deltaEWindow.getMean(),
                    MCSUpdate.Phase.AVERAGING,
                    step.acceptRate(),
                    System.currentTimeMillis(),
                    elapsedMs
                );
                updateListener.accept(update);
            }
        }
        LOG.fine("MCEngine.runExchange — EXIT: " + nAvg + " avg sweeps done, acceptRate="
                + String.format("%.3f", step.acceptRate()));
        return buildResult(config, sampler, step.acceptRate());
    }

    private MCResult runFlip(LatticeConfig config, MCSampler sampler) {
        FlipStep step = new FlipStep(emb, eci, orbits, numComp, T, deltaMu, R, rng);
        int N = config.getN();

        // Initialize MCS monitoring
        deltaEWindow.clear();
        long startTime = System.currentTimeMillis();
        
        // Calculate initial total energy (expensive, done once at start)
        double currentEnergy = LocalEnergyCalc.totalEnergy(config, emb, eci, orbits);
        LOG.fine("MCEngine.runFlip — ENTER: N=" + N + ", E_initial=" + String.format("%.4f", currentEnergy) + " eV");

        for (int s = 0; s < nEquil; s++) {
            // Check for cancellation at the start of each sweep
            if (cancellationCheck.getAsBoolean()) {
                throw new CancellationException("MCS calculation cancelled during equilibration (sweep " + s + ")");
            }
            
            double sweepDeltaE = 0.0;
            
            for (int m = 0; m < N; m++) {
                double stepDeltaE = step.attempt(config);
                deltaEWindow.add(stepDeltaE);  // Track all attempts; rejected moves contribute 0.0
                currentEnergy += stepDeltaE;  // Accumulate (0 if rejected)
                sweepDeltaE += stepDeltaE;   // Track sweep aggregate
            }
            

            // Emit update after each sweep (step = sweep number)
            if (updateListener != null) {
                long elapsedMs = System.currentTimeMillis() - startTime;
                int sweepNum = s + 1;  // Sweep number (1, 2, 3, ...)
                
                MCSUpdate update = new MCSUpdate(
                    sweepNum,
                    currentEnergy,
                    sweepDeltaE,
                    deltaEWindow.getStdDev(),
                    deltaEWindow.getMean(),
                    MCSUpdate.Phase.EQUILIBRATION,
                    step.acceptRate(),
                    System.currentTimeMillis(),
                    elapsedMs
                );
                updateListener.accept(update);
            }
        }

        LOG.fine("MCEngine.runFlip — EQUIL DONE: sweeps=" + nEquil
                + ", E_final=" + String.format("%.4f", currentEnergy) + " eV"
                + ", acceptRate=" + String.format("%.3f", step.acceptRate()));
        step.resetCounters();
        sampler.reset();
        LOG.fine("MCEngine.runFlip — AVERAGING: " + nAvg + " sweeps");

        for (int s = 0; s < nAvg; s++) {
            // Check for cancellation at the start of each sweep
            if (cancellationCheck.getAsBoolean()) {
                throw new CancellationException("MCS calculation cancelled during averaging (sweep " + (nEquil + s) + ")");
            }
            
            double sweepDeltaE = 0.0;
            
            for (int m = 0; m < N; m++) {
                double stepDeltaE = step.attempt(config);
                deltaEWindow.add(stepDeltaE);  // Track all attempts; rejected moves contribute 0.0
                currentEnergy += stepDeltaE;  // Accumulate (0 if rejected)
                sweepDeltaE += stepDeltaE;   // Track sweep aggregate
            }
            sampler.sample(config, emb);

            // Emit update after each sweep.
            // currentEnergy = H_total tracked via incremental ΔE (monitoring only).
            // ΔHmix = ΔH_total for canonical ensemble (reference x·H_pure is fixed),
            // so convergence trends are valid. Final Hmix comes from time-averaged CFs.
            if (updateListener != null) {
                long elapsedMs = System.currentTimeMillis() - startTime;
                int sweepNum = nEquil + s + 1;  // Cumulative sweep count

                MCSUpdate update = new MCSUpdate(
                    sweepNum,
                    currentEnergy,
                    sweepDeltaE,
                    deltaEWindow.getStdDev(),
                    deltaEWindow.getMean(),
                    MCSUpdate.Phase.AVERAGING,
                    step.acceptRate(),
                    System.currentTimeMillis(),
                    elapsedMs
                );
                updateListener.accept(update);
            }
        }
        LOG.fine("MCEngine.runFlip — EXIT: " + nAvg + " avg sweeps done, acceptRate="
                + String.format("%.3f", step.acceptRate()));
        return buildResult(config, sampler, step.acceptRate());
    }

    private MCResult buildResult(LatticeConfig config, MCSampler sampler,
                                  double acceptRate) {
        int L = (int) Math.round(Math.cbrt(config.getN() / 2.0));
        double[] cfs = sampler.meanCFs();

        // Hmix/site = sum_t  hmixCoeff[t] * <u_t>   (CVM-equivalent formula)
        double hmix = 0.0;
        for (int t = 0; t < Math.min(hmixCoeff.length, cfs.length); t++) {
            hmix += hmixCoeff[t] * cfs[t];
        }

        MCResult result = new MCResult(T,
                config.composition(),
                cfs,
                sampler.meanEnergyPerSite(),
                hmix,
                sampler.heatCapacityPerSite(T),
                acceptRate,
                nEquil, nAvg, L, config.getN());
        int _cfN = Math.min(5, cfs.length);
        StringBuilder _cfStr = new StringBuilder("[");
        for (int _i = 0; _i < _cfN; _i++) {
            if (_i > 0) _cfStr.append(", ");
            _cfStr.append(String.format("%.5f", cfs[_i]));
        }
        if (cfs.length > _cfN) _cfStr.append(", ...");
        _cfStr.append("]");
        LOG.fine("MCEngine.buildResult — T=" + T + " K, N=" + config.getN() + " (L=" + L + ")"
                + ", acceptRate=" + String.format("%.3f", acceptRate)
                + ", <E>/site=" + String.format("%.6f", result.getEnergyPerSite())
                + ", Hmix/site=" + String.format("%.6f", result.getHmixPerSite())
                + ", Cv/site=" + String.format("%.4e", result.getHeatCapacityPerSite())
                + ", CFs[0.." + (_cfN-1) + "]=" + _cfStr);
        return result;
    }
}


