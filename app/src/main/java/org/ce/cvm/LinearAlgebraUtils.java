package org.ce.cvm;

/**
 * Minimal linear algebra utilities for the CVM Newton-Raphson solver.
 *
 * <p>Provides Gaussian elimination with partial pivoting for solving
 * {@code A · x = b}. This avoids a dependency on external linear algebra
 * libraries for the small (ncf × ncf, typically 4×4) systems encountered
 * in CVM calculations.</p>
 */
public final class LinearAlgebraUtils {

    private LinearAlgebraUtils() { /* utility class */ }

    /**
     * Solves the linear system {@code A · x = b} using Gaussian elimination
     * with partial pivoting.
     *
     * <p>The input arrays are <em>not</em> modified (copies are made internally).</p>
     *
     * @param A  coefficient matrix (n × n)
     * @param b  right-hand side vector (length n)
     * @return   solution vector x (length n)
     * @throws IllegalArgumentException if A is singular or dimensions mismatch
     */
    public static double[] solve(double[][] A, double[] b) {
        int n = A.length;
        if (n == 0) throw new IllegalArgumentException("Empty matrix");
        if (A[0].length != n) throw new IllegalArgumentException("Matrix must be square");
        if (b.length != n) throw new IllegalArgumentException("RHS length must match matrix size");

        // Work on copies
        double[][] M = new double[n][n];
        double[] rhs = new double[n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, M[i], 0, n);
            rhs[i] = b[i];
        }

        // Forward elimination with partial pivoting
        for (int col = 0; col < n; col++) {
            // Find pivot
            int maxRow = col;
            double maxVal = Math.abs(M[col][col]);
            for (int row = col + 1; row < n; row++) {
                double v = Math.abs(M[row][col]);
                if (v > maxVal) {
                    maxVal = v;
                    maxRow = row;
                }
            }

            if (maxVal < 1e-30) {
                throw new IllegalArgumentException(
                        "Singular or near-singular matrix (pivot = " + maxVal + " at column " + col + ")");
            }

            // Swap rows
            if (maxRow != col) {
                double[] tmp = M[col]; M[col] = M[maxRow]; M[maxRow] = tmp;
                double t = rhs[col]; rhs[col] = rhs[maxRow]; rhs[maxRow] = t;
            }

            // Eliminate below
            double pivot = M[col][col];
            for (int row = col + 1; row < n; row++) {
                double factor = M[row][col] / pivot;
                for (int j = col; j < n; j++) {
                    M[row][j] -= factor * M[col][j];
                }
                rhs[row] -= factor * rhs[col];
            }
        }

        // Back substitution
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = rhs[i];
            for (int j = i + 1; j < n; j++) {
                sum -= M[i][j] * x[j];
            }
            x[i] = sum / M[i][i];
        }

        return x;
    }
}
