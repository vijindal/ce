package org.ce.mcs;

import org.ce.identification.engine.Cluster;
import org.ce.workbench.util.MCSUpdate;
import org.ce.workbench.util.RollingWindow;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Metropolis Monte Carlo engine: equilibration then averaging sweeps.
 * Works for any number of chemical components and any structure.
 *
 * <h2>Sweep definition</h2>
 * <p>One sweep = {@code N} attempted moves (N = lattice sites).</p>
 *
 * <h2>Ensemble selection</h2>
 * <ul>
 *   <li>{@code useFlipStep = false} (default) — canonical
 *       {@link ExchangeStep}: conserves composition, picks two
 *       sites of different species and swaps them.</li>
 *   <li>{@code useFlipStep = true} — grand-canonical {@link FlipStep}:
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
     * Runs equilibration + averaging and returns the result.
     *
     * @param config  initial configuration; modified in-place
     * @param sampler sampler to fill during averaging phase
     * @return {@link MCResult}
     */
    public MCResult run(LatticeConfig config, MCSampler sampler) {
        return useFlipStep ? runFlip(config, sampler) : runExchange(config, sampler);
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

        for (int s = 0; s < nEquil; s++) {
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

        step.resetCounters();
        sampler.reset();

        for (int s = 0; s < nAvg; s++) {
            double sweepDeltaE = 0.0;
            
            for (int m = 0; m < N; m++) {
                double stepDeltaE = step.attempt(config);
                deltaEWindow.add(stepDeltaE);  // Track all attempts; rejected moves contribute 0.0
                currentEnergy += stepDeltaE;  // Accumulate (0 if rejected)
                sweepDeltaE += stepDeltaE;   // Track sweep aggregate
            }
            sampler.sample(config, emb, eci);
            
            // Emit update after each sweep (step = cumulative sweep number)
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

        for (int s = 0; s < nEquil; s++) {
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

        step.resetCounters();
        sampler.reset();

        for (int s = 0; s < nAvg; s++) {
            double sweepDeltaE = 0.0;
            
            for (int m = 0; m < N; m++) {
                double stepDeltaE = step.attempt(config);
                deltaEWindow.add(stepDeltaE);  // Track all attempts; rejected moves contribute 0.0
                currentEnergy += stepDeltaE;  // Accumulate (0 if rejected)
                sweepDeltaE += stepDeltaE;   // Track sweep aggregate
            }
            sampler.sample(config, emb, eci);
            
            // Emit update after each sweep (step = cumulative sweep number)
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
        return buildResult(config, sampler, step.acceptRate());
    }

    private MCResult buildResult(LatticeConfig config, MCSampler sampler,
                                  double acceptRate) {
        int L = (int) Math.round(Math.cbrt(config.getN() / 2.0));
        return new MCResult(T,
                config.composition(),
                sampler.meanCFs(),
                sampler.meanEnergyPerSite(),
                sampler.heatCapacityPerSite(T),
                acceptRate,
                nEquil, nAvg, L, config.getN());
    }
}
