package org.ce.cvm;

import java.util.List;

/**
 * Holds C-matrix data: coefficients, counts, and weights.
 */
public final class CMatrixResult {

    private final List<List<double[][]>> cmat;
    private final int[][] lcv;
    private final List<List<int[]>> wcv;

    /**
     * Per-CF basis-index decoration patterns.
     *
     * <p>{@code cfBasisIndices[col]} = array of basis indices (1-based) for
     * the CF at column {@code col} in the C-matrix.  For a multi-site CF
     * decorated with site operators σ^{k₁}, σ^{k₂}, …, this array is
     * {@code [k₁, k₂, …]}.  Point CFs (single-site) have a single entry.</p>
     *
     * <p>This is the Java equivalent of Mathematica's {@code cfSiteOpList}
     * decoration info, used to compute the random-state CF values
     * ({@code uRandRules}) as products of point CFs.</p>
     */
    private final int[][] cfBasisIndices;

    public CMatrixResult(
            List<List<double[][]>> cmat,
            int[][] lcv,
            List<List<int[]>> wcv,
            int[][] cfBasisIndices) {
        this.cmat = cmat;
        this.lcv = lcv;
        this.wcv = wcv;
        this.cfBasisIndices = cfBasisIndices;
    }

    public List<List<double[][]>> getCmat() {
        return cmat;
    }

    public int[][] getLcv() {
        return lcv;
    }

    public List<List<int[]>> getWcv() {
        return wcv;
    }

    /**
     * Returns the per-CF basis-index decoration patterns.
     * See field documentation for details.
     *
     * @return cfBasisIndices[col] = basis indices for CF at column col
     */
    public int[][] getCfBasisIndices() {
        return cfBasisIndices;
    }
}
