package org.ce.mcs;

import org.ce.identification.geometry.Site;

import java.util.List;

/**
 * Static utility for computing cluster-expansion energy contributions
 * for any structure and any number of chemical components.
 *
 * <h2>Cluster product — general n-component case</h2>
 * <p>A decorated cluster (CF) orbit member has sites labelled with basis
 * symbols {@code "s1"}, {@code "s2"}, …  The cluster product is:</p>
 * <pre>
 *   Φ(e) = Π_{k=0}^{size−1}  φ_{α(k)}( occ[siteIndices[k]] )
 * </pre>
 * <p>where {@code α(k)} is the basis-function index of the {@code k}-th site
 * in the orbit member (read from its {@code "sα"} symbol), and
 * {@code φ_α} is evaluated by {@link SiteOperatorBasis}.</p>
 *
 * <h2>Embedding decoration</h2>
 * <p>Each {@link Embedding} stores its {@code orbitMemberIndex}.  The orbit
 * member cluster is retrieved from
 * {@link org.ce.identification.result.ClusCoordListResult#getOrbitList()
 * orbitList[clusterType][orbitMemberIndex]} to read the site symbols.</p>
 *
 * <h2>Energy formula</h2>
 * <pre>
 *   H = Σ_{e ∈ allEmbeddings}  ECI[e.type] · Φ(e) / size(e)    [for size > 0]
 *       + ECI[empty] · Φ(empty)                                  [for empty cluster]
 * </pre>
 * <p>Dividing by {@code size(e)} corrects for the fact that each physical
 * cluster appears {@code clusterSize} times in {@code allEmbeddings}.
 * For empty clusters (size=0), no division is performed to avoid NaN.</p>
 *
 * <h2>ΔE for a single-site occupation change at site i</h2>
 * <pre>
 *   ΔE = Σ_{e ∈ siteToEmbeddings[i]}  ECI[e.type] · [Φ_new(e) − Φ_old(e)]
 * </pre>
 *
 * @author  CE Project
 * @version 1.0
 * @see     LatticeConfig
 * @see     SiteOperatorBasis
 * @see     ExchangeStep
 */
public final class LocalEnergyCalc {

    private LocalEnergyCalc() {}

    // -------------------------------------------------------------------------
    // Cluster product — general
    // -------------------------------------------------------------------------

    /**
     * Computes the cluster product {@code Φ(e)} for embedding {@code e}
     * given the current configuration and the orbit data needed for site symbols.
     *
     * @param e       embedding to evaluate
     * @param config  current occupation configuration
     * @param orbits  {@code orbits.get(t).get(o)} = orbit-member cluster for
     *                cluster type {@code t}, orbit member {@code o};
     *                sourced from {@link org.ce.identification.result.ClusCoordListResult#getOrbitList()}
     * @return cluster product {@code Φ(e)}
     */
    public static double clusterProduct(Embedding e,
                                         LatticeConfig config,
                                         List<List<org.ce.identification.geometry.Cluster>> orbits) {
        SiteOperatorBasis basis = config.getBasis();
        double prod = 1.0;
        int[] idx = e.getSiteIndices();
        int[] alphas = e.getAlphaIndices();
        if (alphas != null) {
            for (int k = 0; k < idx.length; k++) {
                prod *= basis.evaluate(alphas[k], config.getOccupation(idx[k]));
            }
        } else {
            int t = e.getClusterType();
            int o = e.getOrbitMemberIndex();
            List<Site> sites = orbits.get(t).get(o).getAllSites();
            for (int k = 0; k < idx.length; k++) {
                String sym = sites.get(k).getSymbol();
                int alpha  = SiteOperatorBasis.alphaFromSymbol(sym);
                prod *= basis.evaluate(alpha, config.getOccupation(idx[k]));
            }
        }
        return prod;
    }

    // -------------------------------------------------------------------------
    // Local energy
    // -------------------------------------------------------------------------

    /**
     * Returns the local energy contribution at site {@code i}.
     *
     * @param i      site index
     * @param config current configuration
     * @param emb    embedding data
     * @param eci    effective cluster interactions; {@code eci[t]} for type {@code t}
     * @param orbits orbit list from {@code ClusCoordListResult.getOrbitList()}
     * @return local energy at site {@code i}
     */
    public static double localEnergy(int i,
                                      LatticeConfig config,
                                      EmbeddingData emb,
                                      double[] eci,
                                      List<List<org.ce.identification.geometry.Cluster>> orbits) {
        double sum = 0.0;
        for (Embedding e : emb.getSiteToEmbeddings()[i]) {
            sum += eci[e.getClusterType()] * clusterProduct(e, config, orbits);
        }
        return sum;
    }

    // -------------------------------------------------------------------------
    // Total energy
    // -------------------------------------------------------------------------

    /**
     * Returns the total cluster-expansion energy of the supercell.
     *
     * @param config current configuration
     * @param emb    embedding data
     * @param eci    effective cluster interactions
     * @param orbits orbit list from {@code ClusCoordListResult.getOrbitList()}
     * @return total energy H
     */
    public static double totalEnergy(LatticeConfig config,
                                      EmbeddingData emb,
                                      double[] eci,
                                      List<List<org.ce.identification.geometry.Cluster>> orbits) {
        double sum = 0.0;
        for (Embedding e : emb.getAllEmbeddings()) {
            int size = e.size();
            if (size > 0) {
                // Normal clusters: divide by size to avoid double-counting
                sum += eci[e.getClusterType()] * clusterProduct(e, config, orbits) / size;
            } else {
                // Empty cluster (constant term): no division needed
                sum += eci[e.getClusterType()] * clusterProduct(e, config, orbits);
            }
        }
        return sum;
    }

    /** Returns total energy per lattice site. */
    public static double totalEnergyPerSite(LatticeConfig config,
                                             EmbeddingData emb,
                                             double[] eci,
                                             List<List<org.ce.identification.geometry.Cluster>> orbits) {
        return totalEnergy(config, emb, eci, orbits) / config.getN();
    }

    // -------------------------------------------------------------------------
    // ΔE for a single-site change
    // -------------------------------------------------------------------------

    /**
     * Returns the energy change if site {@code i} changes occupation from
     * {@code oldOcc} to {@code newOcc}, <em>without modifying the config</em>.
     *
     * <p>This is the general form used by both {@link ExchangeStep} and
     * {@link FlipStep}:</p>
     * <pre>
     *   ΔE = Σ_{e ∋ i}  ECI[t] · [Φ(e, newOcc_i) − Φ(e, oldOcc_i)]
     * </pre>
     *
     * @param i      site index
     * @param newOcc new occupation for site {@code i}
     * @param config current configuration (site {@code i} still holds the old occupation)
     * @param emb    embedding data
     * @param eci    effective cluster interactions
     * @param orbits orbit list from {@code ClusCoordListResult.getOrbitList()}
     * @return energy change ΔE
     */
    public static double deltaESingleSite(int i,
                                           int newOcc,
                                           LatticeConfig config,
                                           EmbeddingData emb,
                                           double[] eci,
                                           List<List<org.ce.identification.geometry.Cluster>> orbits) {
        long __p = Profiler.tic("LocalEnergyCalc.deltaESingleSite");
        int oldOcc = config.getOccupation(i);
        if (oldOcc == newOcc) {
            Profiler.toc("LocalEnergyCalc.deltaESingleSite", __p);
            return 0.0;
        }

        double dE = 0.0;
        SiteOperatorBasis basis = config.getBasis();

        for (Embedding e : emb.getSiteToEmbeddings()[i]) {
            int t = e.getClusterType();
            int[] idx = e.getSiteIndices();
            int[] alphas = e.getAlphaIndices();
            double restProduct = 1.0;
            int alphaI = -1;

            if (alphas != null) {
                for (int k = 0; k < idx.length; k++) {
                    if (idx[k] == i) {
                        alphaI = alphas[k];
                    } else {
                        restProduct *= basis.evaluate(alphas[k], config.getOccupation(idx[k]));
                    }
                }
            } else {
                int o = e.getOrbitMemberIndex();
                List<Site> sites = orbits.get(t).get(o).getAllSites();
                for (int k = 0; k < idx.length; k++) {
                    String sym = sites.get(k).getSymbol();
                    if (idx[k] == i) {
                        alphaI = SiteOperatorBasis.alphaFromSymbol(sym);
                    } else {
                        int alpha = SiteOperatorBasis.alphaFromSymbol(sym);
                        restProduct *= basis.evaluate(alpha, config.getOccupation(idx[k]));
                    }
                }
            }

            if (alphaI < 0) continue;  // site i not found (shouldn't happen)

            double phiOld = basis.evaluate(alphaI, oldOcc);
            double phiNew = basis.evaluate(alphaI, newOcc);
            dE += eci[t] * (phiNew - phiOld) * restProduct;
        }
        Profiler.toc("LocalEnergyCalc.deltaESingleSite", __p);
        return dE;
    }

    /**
     * Returns the energy change for a canonical exchange of sites {@code i}
     * and {@code j} (requires {@code occ[i] != occ[j]}).
     *
     * <p>Applies the single-site formula twice, accounting for the coupling
     * in embeddings that span both sites by applying changes sequentially
     * in a temporary copy.</p>
     *
     * @param i site index
     * @param j site index (must have different occupation from {@code i})
     * @param config current configuration (not modified)
     * @param emb    embedding data
     * @param eci    effective cluster interactions
     * @param orbits orbit list
     * @return energy change ΔE
     */
    public static double deltaEExchange(int i, int j,
                                         LatticeConfig config,
                                         EmbeddingData emb,
                                         double[] eci,
                                         List<List<org.ce.identification.geometry.Cluster>> orbits) {
        long __p = Profiler.tic("LocalEnergyCalc.deltaEExchange");
        int occI = config.getOccupation(i);
        int occJ = config.getOccupation(j);
        if (occI == occJ) {
            Profiler.toc("LocalEnergyCalc.deltaEExchange", __p);
            return 0.0;
        }

        // ΔE_i: change site i from occI → occJ (all others, including j, unchanged)
        double dEi = deltaESingleSite(i, occJ, config, emb, eci, orbits);

        // ΔE_j: change site j from occJ → occI, given that site i is NOW occJ
        // Apply i's change temporarily
        config.setOccupation(i, occJ);
        double dEj = deltaESingleSite(j, occI, config, emb, eci, orbits);
        config.setOccupation(i, occI);  // restore

        Profiler.toc("LocalEnergyCalc.deltaEExchange", __p);
        return dEi + dEj;
    }
}
