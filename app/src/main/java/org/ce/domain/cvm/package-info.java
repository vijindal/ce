/**
 * CVM (Cluster Variation Method) free-energy engine.
 *
 * <p>This package implements the variational minimization path of the
 * Cluster Expansion / Cluster Variation Method framework. It consumes
 * the two-stage identification results from {@code org.ce.identification}
 * and finds thermodynamic equilibrium by minimizing the CVM free-energy
 * functional.</p>
 *
 * <h2>Processing stages in this package</h2>
 * <ol>
 *   <li><b>Stage 3 â€” C-Matrix construction</b><br>
 *       Relates cluster probability variables (CVs) to correlation functions
 *       through a linear coefficient matrix.</li>
 *   <li><b>Stage 4 â€” Free-energy functional</b><br>
 *       Assembles {@code F(u) = H(u) âˆ’ TÂ·S(u)} from the C-matrix, KB
 *       coefficients, and effective cluster interactions (ECIs).</li>
 *   <li><b>Stage 5 â€” Newton-Raphson solver</b><br>
 *       Minimizes {@code F(u)} over the CF variables using the Newton-Raphson
 *       method with step damping.</li>
 * </ol>
 *
 * <h2>Planned classes</h2>
 * <ul>
 *   <li>{@code SiteListBuilder}       â€” collects unique site coords, assigns indices</li>
 *   <li>{@code RMatrixCalculator}     â€” R-matrix for pâ†’s operator conversion</li>
 *   <li>{@code PRulesBuilder}         â€” substitution rules p[site][elem]â†’s[Î±][site]</li>
 *   <li>{@code CFSiteOpListBuilder}   â€” maps decorated sub-clusters to site operators</li>
 *   <li>{@code SubstituteRulesBuilder}â€” product of site-operators â†’ CF symbol lookup</li>
 *   <li>{@code CMatrixBuilder}        â€” core C-matrix computation (genCV)</li>
 *   <li>{@code CMatrixResult}         â€” immutable carrier: cmat, lcv, wcv</li>
 *   <li>{@code LinearAlgebraUtils}    â€” Gaussian elimination with partial pivoting</li>
 *   <li>{@code ClusterVariableEvaluator} â€” cv = cmat Â· uList</li>
 *   <li>{@code CVMFreeEnergy}         â€” H, S, G with gradient and Hessian</li>
 *   <li>{@code NewtonRaphsonSolver}   â€” NR iteration: Î´u = solve(Gcuu, âˆ’Gcu)</li>
 *   <li>{@code CVMSolverResult}       â€” equilibrium CFs, free energy, Hmix, Smix</li>
 *   <li>{@code CVMEngine}             â€” top-level orchestrator</li>
 * </ul>
 *
 * <h2>Inputs consumed</h2>
 * <ul>
 *   <li>{@code org.ce.domain.identification.cluster.ClusterIdentificationResult}
 *       â€” tcdis, kbCoeff, lc, mh</li>
 *   <li>{@code org.ce.domain.identification.cf.CFIdentificationResult}
 *       â€” lcf, GroupedCFResult (cfData[t][j])</li>
 *   <li>{@code double[] eci} â€” effective cluster interactions (one per CF)</li>
 *   <li>{@code double T}    â€” temperature in Kelvin</li>
 * </ul>
 *
 * @see org.ce.domain.identification.cluster
 * @see org.ce.domain.identification.cf
 * @see org.ce.mcs
 */
package org.ce.domain.cvm;


