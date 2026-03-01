package org.ce.cvm;

import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.identification.cf.CFIdentificationResult;
import org.ce.identification.geometry.Cluster;
import org.ce.identification.geometry.Sublattice;
import org.ce.identification.geometry.Site;
import org.ce.identification.geometry.Vector3D;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the C-matrix that relates CVs to CFs.
 */
public final class CMatrixBuilder {

    private CMatrixBuilder() {}

    public static CMatrixResult build(
            ClusterIdentificationResult clusterResult,
            CFIdentificationResult cfResult,
            List<Cluster> maxClusters,
            int numElements) {

        if (clusterResult == null || cfResult == null) {
            throw new IllegalArgumentException("clusterResult and cfResult must not be null");
        }
        if (maxClusters == null) {
            throw new IllegalArgumentException("maxClusters must not be null");
        }

        List<Vector3D> siteList = SiteListBuilder.buildSiteList(maxClusters);
        PRules pRules = PRulesBuilder.build(siteList.size(), numElements);

        List<List<List<List<SiteOp>>>> cfSiteOpList =
                CFSiteOpListBuilder.build(cfResult.getGroupedCFData(), siteList);
        SubstituteRules substituteRules = SubstituteRulesBuilder.build(cfSiteOpList);

        int totalCfs = cfResult.getTcf();
        int[][] lcf = cfResult.getLcf();

        Map<CFIndex, Integer> cfColumn = buildCfColumnMap(lcf);

        List<List<Cluster>> ordClustersByType = clusterResult.getOrdClusterData().getCoordList();

        List<List<double[][]>> cmat = new ArrayList<>();
        List<List<int[]>> wcv = new ArrayList<>();
        int[][] lcv = new int[ordClustersByType.size()][];

        for (int t = 0; t < ordClustersByType.size(); t++) {
            List<Cluster> groups = ordClustersByType.get(t);
            List<double[][]> cmatType = new ArrayList<>();
            List<int[]> wcvType = new ArrayList<>();
            lcv[t] = new int[groups.size()];

            for (int j = 0; j < groups.size(); j++) {
                Cluster cluster = groups.get(j);
                List<Integer> siteIndices = flattenSiteIndices(cluster, siteList);

                List<int[]> configs = generateConfigs(siteIndices.size(), numElements);
                Map<PolynomialKey, Integer> countMap = new LinkedHashMap<>();
                Map<PolynomialKey, double[]> rowMap = new LinkedHashMap<>();

                for (int[] config : configs) {
                    double[] row = computeCvRow(siteIndices, config, pRules, substituteRules,
                            cfColumn, totalCfs);
                    PolynomialKey key = new PolynomialKey(row);
                    countMap.put(key, countMap.getOrDefault(key, 0) + 1);
                    rowMap.putIfAbsent(key, row);
                }

                int ncv = countMap.size();
                lcv[t][j] = ncv;

                double[][] cmatGroup = new double[ncv][totalCfs + 1];
                int[] wcvGroup = new int[ncv];

                int idx = 0;
                for (Map.Entry<PolynomialKey, Integer> entry : countMap.entrySet()) {
                    PolynomialKey key = entry.getKey();
                    double[] row = rowMap.get(key);
                    cmatGroup[idx] = row;
                    wcvGroup[idx] = entry.getValue();
                    idx++;
                }

                cmatType.add(cmatGroup);
                wcvType.add(wcvGroup);
            }

            cmat.add(cmatType);
            wcv.add(wcvType);
        }

        return new CMatrixResult(cmat, lcv, wcv);
    }

    private static Map<CFIndex, Integer> buildCfColumnMap(int[][] lcf) {
        Map<CFIndex, Integer> map = new LinkedHashMap<>();
        int col = 0;
        for (int t = 0; t < lcf.length; t++) {
            for (int j = 0; j < lcf[t].length; j++) {
                for (int k = 0; k < lcf[t][j]; k++) {
                    map.put(new CFIndex(t, j, k), col++);
                }
            }
        }
        return map;
    }

    private static List<Integer> flattenSiteIndices(Cluster cluster, List<Vector3D> siteList) {
        List<Integer> indices = new ArrayList<>();
        for (Sublattice sub : cluster.getSublattices()) {
            for (Site site : sub.getSites()) {
                int idx = indexOf(siteList, site.getPosition());
                if (idx < 0) {
                    throw new IllegalStateException("Site position not found in site list: "
                            + site.getPosition());
                }
                indices.add(idx);
            }
        }
        return indices;
    }

    private static int indexOf(List<Vector3D> siteList, Vector3D pos) {
        for (int i = 0; i < siteList.size(); i++) {
            if (siteList.get(i).equals(pos)) {
                return i;
            }
        }
        return -1;
    }

    private static List<int[]> generateConfigs(int numSites, int numElements) {
        List<int[]> configs = new ArrayList<>();
        int total = (int) Math.pow(numElements, numSites);
        for (int i = 0; i < total; i++) {
            int[] cfg = new int[numSites];
            int x = i;
            for (int s = numSites - 1; s >= 0; s--) {
                cfg[s] = x % numElements;
                x /= numElements;
            }
            configs.add(cfg);
        }
        return configs;
    }

    private static double[] computeCvRow(
            List<Integer> siteIndices,
            int[] config,
            PRules pRules,
            SubstituteRules substituteRules,
            Map<CFIndex, Integer> cfColumn,
            int totalCfs) {

        Map<SiteOpProductKey, Double> poly = new LinkedHashMap<>();
        poly.put(new SiteOpProductKey(List.of()), 1.0);

        for (int s = 0; s < siteIndices.size(); s++) {
            int siteIndex = siteIndices.get(s);
            int elementIndex = config[s];
            double[] coeffs = pRules.coefficientsFor(siteIndex, elementIndex);

            Map<SiteOpProductKey, Double> next = new LinkedHashMap<>();
            for (Map.Entry<SiteOpProductKey, Double> entry : poly.entrySet()) {
                List<SiteOp> baseOps = entry.getKey().getOps();
                double baseCoeff = entry.getValue();

                for (int a = 0; a < coeffs.length; a++) {
                    double c = coeffs[a];
                    if (Math.abs(c) < 1e-12) {
                        continue;
                    }
                    List<SiteOp> newOps = new ArrayList<>(baseOps);
                    if (a > 0) {
                        newOps.add(new SiteOp(siteIndex, a));
                    }
                    SiteOpProductKey key = new SiteOpProductKey(newOps);
                    next.put(key, next.getOrDefault(key, 0.0) + baseCoeff * c);
                }
            }
            poly = next;
        }

        double[] row = new double[totalCfs + 1];
        for (Map.Entry<SiteOpProductKey, Double> entry : poly.entrySet()) {
            SiteOpProductKey key = entry.getKey();
            double coeff = entry.getValue();
            List<SiteOp> ops = key.getOps();
            if (ops.isEmpty()) {
                row[totalCfs] += coeff;
                continue;
            }
            CFIndex cfIndex = substituteRules.lookup(ops);
            if (cfIndex == null) {
                // TODO: Handle unmapped site-op products properly
                // For now, print warning and add to constant term
                System.err.println("[CMatrixBuilder] WARNING: No CF mapping for site-op product: " + key 
                        + " (coeff=" + coeff + "), absorbing into constant term");
                row[totalCfs] += coeff;
                continue;
            }
            Integer col = cfColumn.get(cfIndex);
            if (col == null) {
                throw new IllegalStateException("No CF column for index: " + cfIndex);
            }
            row[col] += coeff;
        }

        return row;
    }
}
