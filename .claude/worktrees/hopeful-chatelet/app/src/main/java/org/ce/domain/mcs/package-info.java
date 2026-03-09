/**
 * MCS (Monte Carlo Simulation) engine path.
 *
 * <p>This package implements the Monte Carlo sampling path of the CE framework.
 * It constructs a periodic supercell from the identification results and finds
 * thermodynamic equilibrium by Metropolis sampling over the configuration space.</p>
 *
 * <h2>Currently implemented</h2>
 * <ul>
 *   <li>{@link org.ce.domain.mcs.EmbeddingGenerator} â€” generates all cluster instances in an
 *       LÃ—LÃ—L supercell; tiles orbit members with PBC; deduplicates by sorted site-index set</li>
 *   <li>{@link org.ce.domain.mcs.EmbeddingData} â€” three views of embeddings:
 *       flat list, by-site index (for spin-flip Î”E), by type+site</li>
 *   <li>{@link org.ce.domain.mcs.Embedding} â€” one concrete cluster instance:
 *       int[] site indices + cluster-type tag + orbit-member index</li>
 *   <li>{@link org.ce.domain.mcs.ClusterTemplate} â€” orbit member as integer displacement
 *       vectors relative to an anchor; used for efficient supercell tiling</li>
 *   <li>{@link org.ce.domain.mcs.Vector3DKey} â€” HashMap-compatible Vector3D wrapper
 *       used for embedding deduplication</li>
 * </ul>
 *
 * <h2>Planned classes</h2>
 * <ul>
 *   <li>{@code LatticeConfig}    â€” flat int[] spin array, length N = 2Â·LÂ³;
 *                                   lattice-agnostic configuration store</li>
 *   <li>{@code LocalEnergyCalc} â€” Î”E for site i using getSiteToEmbeddings(i) + ECIs;
 *                                   generalized replacement for expmcs calLocalEnergy</li>
 *   <li>{@code ExchangeStep}    â€” canonical MC step: swap two sites of different spin;
 *                                   applies Metropolis criterion; conserves composition</li>
 *   <li>{@code FlipStep}        â€” grand-canonical MC step: flip a single site's spin</li>
 *   <li>{@code MCEngine}        â€” Metropolis loop for nSteps at temperature T;
 *                                   separates equilibration from averaging phases</li>
 *   <li>{@code MCSampler}       â€” accumulates running averages of CFs, âŸ¨EâŸ©, âŸ¨EÂ²âŸ©</li>
 *   <li>{@code MCSRunner}       â€” top-level orchestrator: PhaseContext â†’ MCResult</li>
 *   <li>{@code MCResult}        â€” average CFs, energyPerSite, heatCapacity, acceptRate</li>
 * </ul>
 *
 * <h2>Inputs consumed</h2>
 * <ul>
 *   <li>{@code org.ce.domain.identification.cluster.ClusterIdentificationResult}</li>
 *   <li>{@code org.ce.domain.identification.cf.CFIdentificationResult}</li>
 *   <li>{@code double[] eci}, {@code double T}, {@code int L} (supercell size)</li>
 * </ul>
 *
 * @see org.ce.domain.identification.engine.EmbeddingGenerator
 * @see org.ce.cvm
 */
package org.ce.domain.mcs;


