/**
 * MCS (Monte Carlo Simulation) engine path.
 *
 * <p>This package implements the Monte Carlo sampling path of the CE framework.
 * It constructs a periodic supercell from the identification results and finds
 * thermodynamic equilibrium by Metropolis sampling over the configuration space.</p>
 *
 * <h2>Currently implemented</h2>
 * <ul>
 *   <li>{@link org.ce.mcs.EmbeddingGenerator} — generates all cluster instances in an
 *       L×L×L supercell; tiles orbit members with PBC; deduplicates by sorted site-index set</li>
 *   <li>{@link org.ce.mcs.EmbeddingData} — three views of embeddings:
 *       flat list, by-site index (for spin-flip ΔE), by type+site</li>
 *   <li>{@link org.ce.mcs.Embedding} — one concrete cluster instance:
 *       int[] site indices + cluster-type tag + orbit-member index</li>
 *   <li>{@link org.ce.mcs.ClusterTemplate} — orbit member as integer displacement
 *       vectors relative to an anchor; used for efficient supercell tiling</li>
 *   <li>{@link org.ce.mcs.Vector3DKey} — HashMap-compatible Vector3D wrapper
 *       used for embedding deduplication</li>
 * </ul>
 *
 * <h2>Planned classes</h2>
 * <ul>
 *   <li>{@code LatticeConfig}    — flat int[] spin array, length N = 2·L³;
 *                                   lattice-agnostic configuration store</li>
 *   <li>{@code LocalEnergyCalc} — ΔE for site i using getSiteToEmbeddings(i) + ECIs;
 *                                   generalized replacement for expmcs calLocalEnergy</li>
 *   <li>{@code ExchangeStep}    — canonical MC step: swap two sites of different spin;
 *                                   applies Metropolis criterion; conserves composition</li>
 *   <li>{@code FlipStep}        — grand-canonical MC step: flip a single site's spin</li>
 *   <li>{@code MCEngine}        — Metropolis loop for nSteps at temperature T;
 *                                   separates equilibration from averaging phases</li>
 *   <li>{@code MCSampler}       — accumulates running averages of CFs, ⟨E⟩, ⟨E²⟩</li>
 *   <li>{@code MCSRunner}       — top-level orchestrator: PhaseContext → MCResult</li>
 *   <li>{@code MCResult}        — average CFs, energyPerSite, heatCapacity, acceptRate</li>
 * </ul>
 *
 * <h2>Inputs consumed</h2>
 * <ul>
 *   <li>{@code org.ce.identification.cluster.ClusterIdentificationResult}</li>
 *   <li>{@code org.ce.identification.cf.CFIdentificationResult}</li>
 *   <li>{@code double[] eci}, {@code double T}, {@code int L} (supercell size)</li>
 * </ul>
 *
 * @see org.ce.identification.engine.EmbeddingGenerator
 * @see org.ce.cvm
 */
package org.ce.mcs;
