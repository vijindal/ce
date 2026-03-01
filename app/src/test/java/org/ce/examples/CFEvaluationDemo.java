package org.ce.examples;

import org.ce.input.InputLoader;
import org.ce.identification.engine.ClusCoordListGenerator;
import org.ce.identification.result.ClusCoordListResult;
import org.ce.identification.geometry.Cluster;
import org.ce.identification.geometry.Vector3D;
import org.ce.mcs.*;

import java.util.List;

/**
 * Simple CF evaluation without MCS run.
 * 
 * <p>Creates a configuration with all sites filled with B (occupation = 1),
 * generates all embeddings for L=4 supercell, and directly evaluates
 * correlation functions for this fixed configuration.</p>
 * 
 * <p>This is useful for testing CF calculation without running the full
 * Monte Carlo simulation.</p>
 */
public class CFEvaluationDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("=== CF Evaluation Demo (All B Sites, L=4) ===\n");

        // Load maximal clusters and space-group (uses classpath resources)
        List<Cluster> maxClusters = InputLoader.parseClusterFile("cluster/A2-T.txt");
        List<?> symOps = InputLoader.parseSymmetryFile("A2-SG");

        // Generate geometric cluster-coordinate list (HSP / disordered)
        ClusCoordListResult clusterData = ClusCoordListGenerator.generate(maxClusters, (List) symOps);
        System.out.println("Cluster types (tc) = " + clusterData.getTc());

        // Binary system parameters
        int numComp = 2;
        int L = 4;

        // Build BCC supercell positions (N = 2*L^3 = 128 sites)
        List<Vector3D> positions = MCSRunner.buildBCCPositions(L);
        int N = positions.size();
        System.out.println("Supercell size L = " + L + "  →  N = " + N + " sites");

        // Generate all embeddings for the supercell
        System.out.println("\nGenerating embeddings...");
        EmbeddingData emb = EmbeddingGenerator.generateEmbeddings(
                positions, clusterData, L);
        int totalEmbeddings = emb.getAllEmbeddings().size();
        System.out.println("Total embeddings: " + totalEmbeddings);

        // Create configuration: ALL SITES = 1 (all B atoms)
        System.out.println("\nCreating configuration: ALL SITES = 1 (all B)");
        LatticeConfig config = new LatticeConfig(N, numComp);
        for (int i = 0; i < N; i++) {
            config.setOccupation(i, 1);  // 1 = B, 0 = A
        }
        System.out.println("Configuration created: all " + N + " sites set to element 1 (B)");

        // Set ECI vector: all zeros for this demo
        // (CF calculation works regardless of ECI values)
        double[] eci = new double[clusterData.getTc()];
        System.out.println("\nECI vector: all zeros (CFs independent of ECI)");

        // Evaluate CFs for this configuration
        System.out.println("\n--- Evaluating Correlation Functions ---");
        
        double[] cfNum = new double[clusterData.getTc()];
        int[] embeddingCountByType = new int[clusterData.getTc()];
        
        for (Embedding e : emb.getAllEmbeddings()) {
            int t = e.getClusterType();
            if (t < clusterData.getTc()) {
                embeddingCountByType[t]++;
                double clusterProd = LocalEnergyCalc.clusterProduct(
                    e, config, clusterData.getOrbitList()
                );
                cfNum[t] += clusterProd;
            }
        }

        // Gather detailed information about each cluster type
        List<Cluster> clusCoordList = clusterData.getClusCoordList();
        List<Double> multiplicities = clusterData.getMultiplicities();
        List<List<Cluster>> orbits = clusterData.getOrbitList();
        
        System.out.println("\n[DEBUG] Detailed Embedding and Multiplicity Analysis\n");
        System.out.println("Type | ClusterSize | OrbitSize | Multiplicity | Embeddings | Product Sum | Current CF | Analysis");
        System.out.println("-----|-------------|-----------|--------------|------------|-------------|------------|----------");
        
        // Analyze each cluster type
        for (int t = 0; t < clusterData.getTc(); t++) {
            int clusterSize = clusCoordList.get(t).getAllSites().size();
            int orbitSize = orbits.get(t).size();
            double mult = multiplicities.get(t);
            int embedCount = embeddingCountByType[t];
            double prodSum = cfNum[t];
            double currentCF = prodSum / (N * orbitSize);
            
            // Calculate expected count
            // For a uniform lattice: expected unique clusters = N * clusterSize / orbitSize
            // But we're counting all embeddings (with multiplicity)
            double expectedUnique = (double)(N * clusterSize) / orbitSize;
            double embedsPerUnique = (orbitSize > 0) ? (double)embedCount / expectedUnique : 0;
            
            System.out.printf("%3d  | %11d | %9d | %12.4f | %10d | %11.1f | %10.6f | E/uniq=%.2f%n",
                t, clusterSize, orbitSize, mult, embedCount, prodSum, currentCF, embedsPerUnique);
        }
        
        System.out.println("\n[DEBUG] Alternative Normalization Formulas\n");
        System.out.println("Type | Formula: Σ/Count | Formula: Σ/(N) | Formula: Σ/(N*mult) | Formula: (Σ/Count)/clusterSize");
        System.out.println("-----|------------------|-----------------|---------------------|--------------------------------");
        
        for (int t = 0; t < clusterData.getTc(); t++) {
            int clusterSize = clusCoordList.get(t).getAllSites().size();
            double mult = multiplicities.get(t);
            int embedCount = embeddingCountByType[t];
            double prodSum = cfNum[t];
            
            double cf1 = (embedCount > 0) ? prodSum / embedCount : 0;
            double cf2 = prodSum / N;
            double cf3 = (mult > 0) ? prodSum / (N * mult) : 0;
            double cf4 = (embedCount > 0 && clusterSize > 0) ? (prodSum / embedCount) / clusterSize : 0;
            
            System.out.printf("%3d  | %16.6f | %13.6f | %19.6f | %32.6f%n",
                t, cf1, cf2, cf3, cf4);
        }
        
        System.out.println("\n[DEBUG] Expected Behavior for All-B Configuration\n");
        System.out.println("When all sites = B (occupation = 1):");
        System.out.println("- φ_1(B) = +1.0");
        System.out.println("- Cluster product Φ = product of all basis functions");
        System.out.println("  * For ANY cluster size: Φ = (+1.0)^n = +1.0 for pure B");
        System.out.println("- Therefore: CF should always equal 1.0 (constant function)");
        System.out.println();

        // Calculate total energy
        System.out.println("\n--- Energy Calculation ---");
        double totalEnergy = LocalEnergyCalc.totalEnergy(config, emb, eci, clusterData.getOrbitList());
        double energyPerSite = totalEnergy / N;
        System.out.printf("Total energy (H): %.6f%n", totalEnergy);
        System.out.printf("Energy per site:  %.6f%n", energyPerSite);

        System.out.println("\n=== CF Evaluation Complete ===");
    }
}
