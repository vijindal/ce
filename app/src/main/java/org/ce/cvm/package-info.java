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
 *   <li><b>Stage 3 — C-Matrix construction</b><br>
 *       Relates cluster probability variables (CVs) to correlation functions
 *       through a linear coefficient matrix.</li>
 *   <li><b>Stage 4 — Free-energy functional</b><br>
 *       Assembles {@code F(u) = H(u) − T·S(u)} from the C-matrix, KB
 *       coefficients, and effective cluster interactions (ECIs).</li>
 *   <li><b>Stage 5 — NIM solver</b><br>
 *       Minimizes {@code F(u)} over the CF variables using the Natural
 *       Iteration Method.</li>
 * </ol>
 *
 * <h2>Planned classes</h2>
 * <ul>
 *   <li>{@code SiteListBuilder}       — collects unique site coords, assigns indices</li>
 *   <li>{@code RMatrixCalculator}     — R-matrix for p→s operator conversion</li>
 *   <li>{@code PRulesBuilder}         — substitution rules p[site][elem]→s[α][site]</li>
 *   <li>{@code CFSiteOpListBuilder}   — maps decorated sub-clusters to site operators</li>
 *   <li>{@code SubstituteRulesBuilder}— product of site-operators → CF symbol lookup</li>
 *   <li>{@code CMatrixBuilder}        — core C-matrix computation (genCV)</li>
 *   <li>{@code CMatrixResult}         — immutable carrier: cmat, lcv, wcv</li>
 *   <li>{@code ClusterVariables}      — cv = cmat · uList</li>
 *   <li>{@code EnthalpyCalculator}    — H(u) = Σ m·ECI·CF</li>
 *   <li>{@code EntropyCalculator}     — S(u) = −k_B Σ kb·m·Σ wcv·cv·ln(cv)</li>
 *   <li>{@code FreeEnergyFunctional}  — F = H − T·S, with gradient</li>
 *   <li>{@code NaturalIterationSolver}— NIM: iterates u until |ΔF| &lt; ε</li>
 *   <li>{@code CVMEngine}             — top-level orchestrator</li>
 *   <li>{@code CVMEngineResult}       — equilibrium CFs, free energy, Hmix, Smix</li>
 * </ul>
 *
 * <h2>Inputs consumed</h2>
 * <ul>
 *   <li>{@code org.ce.identification.cluster.ClusterIdentificationResult}
 *       — tcdis, kbCoeff, lc, mh</li>
 *   <li>{@code org.ce.identification.cf.CFIdentificationResult}
 *       — lcf, GroupedCFResult (cfData[t][j])</li>
 *   <li>{@code double[] eci} — effective cluster interactions (one per CF)</li>
 *   <li>{@code double T}    — temperature in Kelvin</li>
 * </ul>
 *
 * @see org.ce.identification.cluster
 * @see org.ce.identification.cf
 * @see org.ce.mcs
 */
package org.ce.cvm;
