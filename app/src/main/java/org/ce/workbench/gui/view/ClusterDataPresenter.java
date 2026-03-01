package org.ce.workbench.gui.view;

import org.ce.cvm.CMatrixResult;
import org.ce.identification.cf.CFIdentificationResult;
import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.identification.result.GroupedCFResult;
import org.ce.workbench.backend.data.AllClusterData;
import org.ce.workbench.backend.job.CFIdentificationJob;
import org.ce.workbench.util.cache.AllClusterDataCache;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Formats and presents cluster identification results for the GUI log panel.
 *
 * <p>This class was extracted from `SystemRegistryPanel.displayAllClusterResults()`
 * to keep the panel class focused on layout and user interaction.</p>
 */
public final class ClusterDataPresenter {

    private ClusterDataPresenter() {}

    /**
     * Formats all cluster results from a completed {@link CFIdentificationJob}
     * and emits each line to the supplied {@code logger}.
     *
     * @param job    completed CF identification job
     * @param logger line-by-line consumer (typically {@code resultsPanel::logMessage})
     */
    /**
     * Overload without clusterKey — no storage path is shown.
     */
    public static void present(CFIdentificationJob job, Consumer<String> logger) {
        present(job, null, logger);
    }

    /**
     * Formats all cluster results from a completed job and emits each line.
     *
     * @param job        completed CF identification job
     * @param clusterKey optional cluster key (if non-null, storage folder path is printed)
     * @param logger     line-by-line consumer
     */
    public static void present(CFIdentificationJob job, String clusterKey, Consumer<String> logger) {
        logger.accept("\n[DEBUG] displayAllClusterResults() called");
        logger.accept("  job.getId() = " + job.getId());
        logger.accept("  job.isCompleted() = " + job.isCompleted());
        logger.accept("  job.isFailed() = " + job.isFailed());

        try {
            logger.accept("[DEBUG] Extracting AllClusterData...");
            AllClusterData allData       = job.getAllClusterData();
            CMatrixResult  cmatrixResult = job.getCMatrixResult();

            logger.accept("[DEBUG] AllClusterData = " + (allData != null ? "NOT NULL" : "NULL"));
            logger.accept("[DEBUG] CMatrixResult = "  + (cmatrixResult != null ? "NOT NULL" : "NULL"));

            if (allData == null) {
                logger.accept("❌ ERROR: No cluster data available after job completion");
                return;
            }

            logger.accept("\n✓ CLUSTER DATA GENERATED SUCCESSFULLY");
            logger.accept("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.accept("Computation Time: " + allData.getComputationTimeMs() + " ms");
            logger.accept("Num Components: " + allData.getNumComponents());

            // Show storage folder if clusterKey is available
            if (clusterKey != null && !clusterKey.isEmpty()) {
                presentStoragePaths(clusterKey, logger);
            }

            presentStage1(allData.getStage1(), logger);
            presentStage2(allData.getStage2(), logger);
            presentStage3(cmatrixResult, logger);
            presentSummary(allData, logger);

            logger.accept("[DEBUG] displayAllClusterResults() completed successfully");

        } catch (Exception e) {
            logger.accept("❌ ERROR displaying cluster results: " + e.getMessage());
            logger.accept("[DEBUG] Stack trace:");
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logger.accept(sw.toString());
        }
    }

    /**
     * Prints the storage folder paths and lists the actual directory contents.
     */
    private static void presentStoragePaths(String clusterKey, Consumer<String> log) {
        try {
            Path dir = AllClusterDataCache.resolveDir(clusterKey);
            log.accept("\n[STORAGE] Data persisted at:");
            log.accept("─────────────────────────────────────────────────────");
            log.accept("  Folder: " + dir.toAbsolutePath());

            if (Files.isDirectory(dir)) {
                log.accept("  Contents:");
                try (Stream<Path> entries = Files.list(dir)) {
                    entries.sorted().forEach(entry -> {
                        String name = entry.getFileName().toString();
                        try {
                            long size = Files.size(entry);
                            String sizeStr;
                            if (size < 1024) {
                                sizeStr = size + " B";
                            } else if (size < 1024 * 1024) {
                                sizeStr = String.format("%.1f KB", size / 1024.0);
                            } else {
                                sizeStr = String.format("%.1f MB", size / (1024.0 * 1024));
                            }
                            log.accept("    " + name + "  (" + sizeStr + ")");
                        } catch (Exception ex) {
                            log.accept("    " + name);
                        }
                    });
                }
            } else {
                log.accept("  (Folder not yet created on disk)");
            }
            log.accept("─────────────────────────────────────────────────────");
        } catch (Exception e) {
            log.accept("  (Could not resolve storage path: " + e.getMessage() + ")");
        }
    }

    // =========================================================================
    // Stage sections
    // =========================================================================

    private static void presentStage1(ClusterIdentificationResult stage1,
                                      Consumer<String> log) {
        log.accept("\n[STAGE 1] CLUSTER IDENTIFICATION");
        log.accept("─────────────────────────────────────────────────────");

        if (stage1 == null) {
            log.accept("❌ Stage 1 data is null!");
            return;
        }

        log.accept("  tcdis (HSP cluster types):           " + stage1.getTcdis());
        log.accept("  nxcdis (HSP point-cluster types):    " + stage1.getNxcdis());
        log.accept("  tc (ordered-phase cluster types):    " + stage1.getTc());
        log.accept("  nxc (ordered-phase point types):     " + stage1.getNxc());

        // Kikuchi-Baker coefficients
        double[] kb = stage1.getKbCoefficients();
        if (kb != null) {
            log.accept("  Kikuchi-Baker coefficients (kb):     array[" + kb.length + "]");
            for (int i = 0; i < Math.min(5, kb.length); i++)
                log.accept("    kb[" + i + "] = " + String.format("%.6f", kb[i]));
            if (kb.length > 5) log.accept("    ... (" + (kb.length - 5) + " more entries)");
        }

        // LC array
        int[] lc = stage1.getLc();
        if (lc != null) {
            log.accept("  lc (cluster reps per type):          array[" + lc.length + "]");
            for (int i = 0; i < Math.min(8, lc.length); i++)
                log.accept("    lc[" + i + "] = " + lc[i]);
            if (lc.length > 8) log.accept("    ... (" + (lc.length - 8) + " more entries)");
        }

        // MH array
        double[][] mh = stage1.getMh();
        if (mh != null) {
            log.accept("  mh (multiplicities):                 matrix[" + mh.length + "][varying]");
            for (int i = 0; i < Math.min(5, mh.length); i++)
                if (mh[i] != null)
                    log.accept("    mh[" + i + "]: " + mh[i].length + " multiplicities");
            if (mh.length > 5) log.accept("    ... (" + (mh.length - 5) + " more types)");
        }

        // Nij table
        int[][] nij = stage1.getNijTable();
        if (nij != null)
            log.accept("  nijTable (containment):              matrix[" + nij.length + "][" + nij.length + "]");

        log.accept("✓ Stage 1 complete");
    }

    private static void presentStage2(CFIdentificationResult stage2,
                                      Consumer<String> log) {
        log.accept("\n[STAGE 2] CORRELATION FUNCTION (CF) IDENTIFICATION");
        log.accept("─────────────────────────────────────────────────────");

        if (stage2 == null) {
            log.accept("❌ Stage 2 data is null!");
            return;
        }

        log.accept("  tcf (total CFs):                     " + stage2.getTcf());
        log.accept("  tcfdis (HSP CFs):                    " + stage2.getTcfdis());
        log.accept("  nxcf (point-cluster CFs):           " + stage2.getNxcf());
        log.accept("  ncf (non-point CFs):                 " + stage2.getNcf());

        int[][] lcf = stage2.getLcf();
        if (lcf != null) {
            log.accept("  lcf (CFs per cluster type):          matrix[" + lcf.length + "][varying]");
            for (int i = 0; i < Math.min(8, lcf.length); i++)
                if (lcf[i] != null)
                    log.accept("    lcf[" + i + "]: " + lcf[i].length + " groups, total CFs = "
                            + String.format("%-4d", Arrays.stream(lcf[i]).sum()));
            if (lcf.length > 8) log.accept("    ... (" + (lcf.length - 8) + " more cluster types)");
        }

        GroupedCFResult groupedCFData = stage2.getGroupedCFData();
        if (groupedCFData != null) {
            log.accept("  groupedCFData:                       Hierarchically organized CFs");
            log.accept("    Structure: [cluster_type][ordered_group][CF_list]");
        }

        log.accept("✓ Stage 2 complete");
    }

    private static void presentStage3(CMatrixResult cmatrixResult,
                                      Consumer<String> log) {
        log.accept("\n[STAGE 3] C-MATRIX CONSTRUCTION");
        log.accept("─────────────────────────────────────────────────────");

        if (cmatrixResult == null) {
            log.accept("❌ Stage 3 data is null!");
            return;
        }

        // cmat
        List<List<double[][]>> cmat = cmatrixResult.getCmat();
        if (cmat != null) {
            log.accept("  cmat (C-matrix coefficients):        3D structure");
            log.accept("    Dimensions: " + cmat.size() + " cluster types x varying groups x matrices");
            int totalMatrices = 0;
            for (int i = 0; i < cmat.size(); i++) {
                List<double[][]> cm = cmat.get(i);
                if (cm != null) {
                    totalMatrices += cm.size();
                    if (i < 5) log.accept("    cmat[" + i + "]: " + cm.size() + " group(s)");
                }
            }
            if (cmat.size() > 5) log.accept("    ... (" + (cmat.size() - 5) + " more cluster types)");
            log.accept("    Total matrices: " + totalMatrices);
        }

        // lcv
        int[][] lcv = cmatrixResult.getLcv();
        if (lcv != null) {
            log.accept("  lcv (CV counts per cluster):         matrix[" + lcv.length + "][varying]");
            for (int i = 0; i < Math.min(8, lcv.length); i++)
                if (lcv[i] != null) {
                    int sum = 0;
                    for (int c : lcv[i]) sum += c;
                    log.accept("    lcv[" + i + "]: " + lcv[i].length + " groups, sum CVs = " + sum);
                }
            if (lcv.length > 8) log.accept("    ... (" + (lcv.length - 8) + " more cluster types)");
        }

        // wcv
        List<List<int[]>> wcv = cmatrixResult.getWcv();
        if (wcv != null) {
            log.accept("  wcv (CV weights):                    3D structure");
            log.accept("    Dimensions: " + wcv.size() + " cluster types x varying groups x weight arrays");
            int totalWeights = 0;
            for (int i = 0; i < wcv.size(); i++) {
                List<int[]> cw = wcv.get(i);
                if (cw != null) {
                    totalWeights += cw.size();
                    if (i < 5) log.accept("    wcv[" + i + "]: " + cw.size() + " group(s)");
                }
            }
            if (wcv.size() > 5) log.accept("    ... (" + (wcv.size() - 5) + " more cluster types)");
            log.accept("    Total weight arrays: " + totalWeights);
        }

        log.accept("✓ Stage 3 complete");
    }

    private static void presentSummary(AllClusterData allData, Consumer<String> log) {
        log.accept("\n[SUMMARY] All Data Available for MCS and CVM Calculations");
        log.accept("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.accept("✓ Cluster Types:       " + allData.getTcdis() + " (tcdis)");
        log.accept("✓ Correlation Funcs:   " + allData.getTcf() + " (tcf)");
        log.accept("✓ C-matrix Prepared:   Ready for CVM/MCS calculations");
        log.accept("✓ Computation Time:    " + allData.getComputationTimeMs() + " ms");
        log.accept("\nAll cluster data is ready for MCS and CVM calculations!");
        log.accept("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
