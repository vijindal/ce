package org.ce.domain.mcs;

import org.ce.domain.identification.geometry.Cluster;
import org.ce.domain.identification.result.ClusCoordListResult;
import org.ce.domain.identification.geometry.Vector3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Top-level orchestrator for the MCS engine path.
 *
 * <p>Accepts cluster identification results (any structure, any number of
 * components), builds the supercell, initialises a random configuration, and
 * delegates to {@link MCEngine} for equilibration and averaging.</p>
 *
 * <h2>Generalisation</h2>
 * <ul>
 *   <li>The orbit list from {@link ClusCoordListResult#getOrbitList()} is
 *       passed through to every component that needs to evaluate decorated
 *       cluster products â€” no binary-only assumptions.</li>
 *   <li>Composition is specified as a full {@code double[] xFrac} of length
 *       {@code numComp}.  Use {@link Builder#compositionBinary} for the
 *       common binary case.</li>
 *   <li>The supercell geometry is provided via a {@link StructureBuilder} or
 *       the built-in {@link #buildBCCPositions(int)} helper.  Override with
 *       {@link Builder#latticePositions} for other structures.</li>
 * </ul>
 *
 * <h2>Typical usage â€” binary BCC</h2>
 * <pre>{@code
 * MCResult result = MCSRunner.builder()
 *         .clusterData(stage1.getDisClusterData())
 *         .eci(eci)
 *         .numComp(2)
 *         .L(4)
 *         .T(800.0)
 *         .compositionBinary(0.25)
 *         .nEquil(2000).nAvg(5000).seed(42)
 *         .build().run();
 * result.printDebug();
 * }</pre>
 *
 * <h2>Typical usage â€” ternary, custom positions</h2>
 * <pre>{@code
 * MCResult result = MCSRunner.builder()
 *         .clusterData(stage1.getDisClusterData())
 *         .eci(eci)
 *         .numComp(3)
 *         .L(4)
 *         .T(900.0)
 *         .composition(new double[]{0.5, 0.3, 0.2})
 *         .latticePositions(myFCCPositions, 4)
 *         .nEquil(3000).nAvg(6000)
 *         .build().run();
 * }</pre>
 *
 * @author  CE Project
 * @version 1.0
 * @see     MCEngine
 * @see     MCSampler
 * @see     MCResult
 */
public class MCSRunner {

    private static final Logger LOG = Logger.getLogger(MCSRunner.class.getName());

    private final ClusCoordListResult clusterData;
    private final double[]            eci;
    private final int                 numComp;
    private final double              T;
    private final double[]            xFrac;
    private final double              R;
    private final int                 nEquil;
    private final int                 nAvg;
    private final int                 L;
    private final List<Vector3D>      customPositions;
    private final boolean             useFlipStep;
    private final double[]            deltaMu;
    private final long                seed;
    private final Consumer<MCSUpdate> updateListener;
    private final BooleanSupplier     cancellationCheck;

    private MCSRunner(Builder b) {
        this.clusterData     = b.clusterData;
        this.eci             = b.eci.clone();
        this.numComp         = b.numComp;
        this.T               = b.T;
        this.xFrac           = b.xFrac.clone();
        this.nEquil          = b.nEquil;
        this.nAvg            = b.nAvg;
        this.L               = b.L;
        this.customPositions = b.customPositions;
        this.useFlipStep     = b.useFlipStep;
        this.deltaMu         = b.deltaMu.clone();
        this.seed            = b.seed;
        this.R               = b.R;
        this.updateListener  = b.updateListener;
        this.cancellationCheck = b.cancellationCheck;
    }

    // -------------------------------------------------------------------------
    // Run
    // -------------------------------------------------------------------------

    /**
     * Runs the full simulation and returns the averaged thermodynamic
     * observables.
     *
     * @return {@link MCResult} containing average CFs, energy, and heat capacity
     */
    public MCResult run() {
        Random rng = new Random(seed);

        // 1. Supercell positions
        List<Vector3D> positions = (customPositions != null)
                ? customPositions
                : buildBCCPositions(L);
        int N = positions.size();
        LOG.fine(String.format("MCSRunner.run — ENTER: L=%d, N=%d, numComp=%d, T=%.1f K, nEquil=%d, nAvg=%d, seed=%d", L, N, numComp, T, nEquil, nAvg, seed));

        // 2. Embeddings
        EmbeddingData emb = EmbeddingGenerator.generateEmbeddings(
                positions, clusterData, L);

        // 3. Configuration
        LatticeConfig config = new LatticeConfig(N, numComp);
        config.randomise(xFrac, rng);
        double[] x = config.composition();
        StringBuilder compSb = new StringBuilder("Composition: ");
        for (int c = 0; c < numComp; c++)
            compSb.append(String.format("x[%d]=%.4f  ", c, x[c]));
        LOG.fine(compSb.toString());

        // 4. Orbit sizes, orbit list, and CF-based energy coefficients for sampler
        int tc = clusterData.getTc();
        int[] orbitSizes = new int[tc];
        List<List<Cluster>> orbits = clusterData.getOrbitList();
        for (int t = 0; t < tc; t++)
            orbitSizes[t] = orbits.get(t).size();
        double[] hTotalCoeff = emb.computeHTotalCoeff(eci, tc);
        double   hTotalConst = emb.computeHTotalConst(eci, tc);
        MCSampler sampler = new MCSampler(N, orbitSizes, orbits, R, hTotalCoeff, hTotalConst);

        // 5. Engine
        MCEngine engine = new MCEngine(
            emb, eci, orbits, numComp,
            T, nEquil, nAvg,
            useFlipStep, deltaMu, R, rng);
        if (updateListener != null) {
            engine.setUpdateListener(updateListener);
        }
        if (cancellationCheck != null) {
            engine.setCancellationCheck(cancellationCheck);
        }
        MCResult result = engine.run(config, sampler);
        LOG.fine(String.format("MCSRunner.run — EXIT: acceptRate=%.3f, <E>/site=%.6f eV", result.getAcceptRate(), result.getEnergyPerSite()));
        return result;
    }

    // -------------------------------------------------------------------------
    // BCC position builder
    // -------------------------------------------------------------------------

    /**
     * Builds site positions for an LÃ—LÃ—L BCC supercell.
     * N = 2Â·LÂ³ sites; corners at integer coords, body-centres at half-integers.
     *
     * @param L supercell repetition factor; must be â‰¥ 1
     * @return list of 2Â·LÂ³ site positions
     */
    public static List<Vector3D> buildBCCPositions(int L) {
        if (L < 1) throw new IllegalArgumentException("L must be >= 1, got " + L);
        List<Vector3D> pos = new ArrayList<>(2 * L * L * L);
        for (int ix = 0; ix < L; ix++)
            for (int iy = 0; iy < L; iy++)
                for (int iz = 0; iz < L; iz++) {
                    pos.add(new Vector3D(ix,       iy,       iz      ));
                    pos.add(new Vector3D(ix + 0.5, iy + 0.5, iz + 0.5));
                }
        LOG.fine("MCSRunner.buildBCCPositions — L=" + L + " → N=" + pos.size() + " BCC sites built");
        return pos;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link MCSRunner}.
     *
     * <p>Required: {@code clusterData}, {@code eci}, {@code numComp},
     * {@code T}, composition (via {@code composition} or
     * {@code compositionBinary}), {@code nEquil}, {@code nAvg}.</p>
     */
    public static class Builder {
        private ClusCoordListResult clusterData;
        private double[]            eci;
        private int                 numComp     = 2;
        private double              T;
        private double[]            xFrac;
        private int                 nEquil      = 1000;
        private int                 nAvg        = 2000;
        private int                 L           = 4;
        private List<Vector3D>      customPositions = null;
        private boolean             useFlipStep = false;
        private double[]            deltaMu;
        private long                seed        = 0L;
        private double              R           = 1.0;
        private Consumer<MCSUpdate> updateListener = null;
        private BooleanSupplier     cancellationCheck = null;

        private Builder() {}

        /** Cluster data from Stage 1 ({@code stage1.getDisClusterData()}). */
        public Builder clusterData(ClusCoordListResult d) { this.clusterData = d; return this; }

        /** Effective cluster interactions; {@code eci[t]} for cluster type {@code t}. */
        public Builder eci(double[] e) { this.eci = e; return this; }

        /** Number of chemical components (must match the identification stage). */
        public Builder numComp(int n) { this.numComp = n; return this; }

        /** Temperature in Kelvin. */
        public Builder T(double t) { this.T = t; return this; }

        /** Full composition array; length must equal {@code numComp}. */
        public Builder composition(double[] x) { this.xFrac = x.clone(); return this; }

        /** Convenience for binary: {@code x = [1-xB, xB]}. */
        public Builder compositionBinary(double xB) {
            this.xFrac = new double[]{1.0 - xB, xB}; return this;
        }

        /** Number of equilibration sweeps. */
        public Builder nEquil(int n) { this.nEquil = n; return this; }

        /** Number of averaging sweeps. */
        public Builder nAvg(int n) { this.nAvg = n; return this; }

        /** Supercell size L (for BCC: N = 2Â·LÂ³). */
        public Builder L(int l) { this.L = l; return this; }

        /**
         * Overrides default BCC positions with a custom position list.
         * The {@code L} value must still be set correctly for PBC wrapping.
         */
        public Builder latticePositions(List<Vector3D> pos) {
            this.customPositions = pos; return this;
        }

        /** Use grand-canonical FlipStep instead of canonical ExchangeStep. */
        public Builder useFlipStep(boolean b) { this.useFlipStep = b; return this; }

        /**
         * Chemical-potential differences {@code Î¼_c âˆ’ Î¼_0} in eV;
         * length must equal {@code numComp}; only used with FlipStep.
         */
        public Builder deltaMu(double[] mu) { this.deltaMu = mu.clone(); return this; }

        /** Random seed for reproducibility. */
        public Builder seed(long s) { this.seed = s; return this; }

        /** Phase gas constant in J/(molÂ·K). Must match energy units (CEC in J/mol requires R=8.314). */
        public Builder R(double r) { this.R = r; return this; }

        /** Optional callback for real-time MCS updates (for GUI plotting/logging). */
        public Builder updateListener(Consumer<MCSUpdate> listener) {
            this.updateListener = listener;
            return this;
        }

        /** Optional cancellation check; if returns true, the simulation throws CancellationException. */
        public Builder cancellationCheck(BooleanSupplier check) {
            this.cancellationCheck = check;
            return this;
        }

        public MCSRunner build() {
            if (clusterData == null) throw new IllegalStateException("clusterData required");
            if (eci == null)         throw new IllegalStateException("eci required");
            if (T <= 0)              throw new IllegalStateException("T must be > 0");
            if (xFrac == null) {
                // Default: equimolar
                xFrac = new double[numComp];
                for (int c = 0; c < numComp; c++) xFrac[c] = 1.0 / numComp;
            }
            if (deltaMu == null) deltaMu = new double[numComp];
            return new MCSRunner(this);
        }
    }
}



