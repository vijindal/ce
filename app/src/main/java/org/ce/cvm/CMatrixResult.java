package org.ce.cvm;

import java.util.List;

/**
 * Holds C-matrix data: coefficients, counts, and weights.
 */
public final class CMatrixResult {

    private final List<List<double[][]>> cmat;
    private final int[][] lcv;
    private final List<List<int[]>> wcv;

    public CMatrixResult(
            List<List<double[][]>> cmat,
            int[][] lcv,
            List<List<int[]>> wcv) {
        this.cmat = cmat;
        this.lcv = lcv;
        this.wcv = wcv;
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
}
