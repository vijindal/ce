package org.ce.application.job;

import org.ce.application.port.CECOperationListener;
import org.ce.application.service.CECAssemblyService;
import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.logging.LoggingConfig;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.ce.infrastructure.registry.KeyUtils;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background job that assembles higher-order CECs from subsystem ECIs.
 *
 * <p>Mirrors {@link CFIdentificationJob}'s structure for Type 1(b), closing the
 * job-symmetry gap described in the Type 1 Architecture Plan.  Assembly now
 * runs on the executor thread managed by {@code BackgroundJobManager}, never on
 * the JavaFX application thread.</p>
 *
 * <h2>Steps executed in {@link #run()}</h2>
 * <ol>
 *   <li>Load {@link AllClusterData} for the target system from {@code AllClusterDataCache}</li>
 *   <li>Classify CFs by order via {@link CECAssemblyService#classifyCFsByOrder}</li>
 *   <li>For each subsystem order, load the subsystem CEC and call
 *       {@link CECAssemblyService#transformToTarget}, accumulating contributions</li>
 *   <li>Produce an immutable {@link AssemblyResult} and fire
 *       {@link CECOperationListener#onAssemblyCompleted}</li>
 * </ol>
 *
 * <p>The optional {@link CECOperationListener} receives progress callbacks; if
 * none is supplied the job still runs correctly (callbacks are no-ops).</p>
 */
public class CECAssemblyJob extends AbstractBackgroundJob {

    private static final Logger LOG = LoggingConfig.getLogger(CECAssemblyJob.class);

    private final CECOperationListener operationListener;

    /** Populated on successful completion; {@code null} otherwise. */
    private AssemblyResult result;

    public CECAssemblyJob(SystemIdentity targetSystem, CECOperationListener operationListener) {
        super(
            "cec-" + targetSystem.getId() + "-" + UUID.randomUUID(),
            "CEC Assembly: " + targetSystem.getName() + " (K=" + targetSystem.getNumComponents() + ")",
            targetSystem
        );
        // Allow null listener — we use a no-op guard below
        this.operationListener = operationListener;
    }

    /** Convenience constructor without a listener. */
    public CECAssemblyJob(SystemIdentity targetSystem) {
        this(targetSystem, null);
    }

    // -----------------------------------------------------------------------
    // BackgroundJob implementation
    // -----------------------------------------------------------------------

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        LOG.info("CECAssemblyJob.run — ENTER: job=" + getId() + ", system=" + system.getId());

        if (shouldStop()) return;

        try {
            running = true;
            int K = system.getNumComponents();
            List<List<String>> allSubsystems = countAllSubsystems(system.getComponents(), K);
            int subsystemCount = allSubsystems.size();

            fireAssemblyStarted(system.getId(), subsystemCount);

            // ------------------------------------------------------------------
            // Step 1 — Load AllClusterData for target
            // ------------------------------------------------------------------
            setStatusMessage("Loading cluster data for " + system.getName() + "...");
            setProgress(10);
            if (shouldStop()) return;

            String clusterKey = KeyUtils.clusterKey(system);
            Optional<AllClusterData> dataOpt = AllClusterDataCache.load(clusterKey);
            if (dataOpt.isEmpty() || !dataOpt.get().isComplete()) {
                String msg = "AllClusterData not available for " + system.getId()
                        + " (key=" + clusterKey + ")";
                LOG.warning("CECAssemblyJob.run — " + msg);
                markFailed(msg);
                fireAssemblyFailed(msg);
                return;
            }
            AllClusterData targetData = dataOpt.get();
            int tcf = targetData.getStage2().getTcf();

            // ------------------------------------------------------------------
            // Step 2 — Classify CFs by order
            // ------------------------------------------------------------------
            setStatusMessage("Classifying correlation functions by order...");
            setProgress(20);
            if (shouldStop()) return;

            int[] cfOrderMap = CECAssemblyService.classifyCFsByOrder(targetData);

            // ------------------------------------------------------------------
            // Step 3 — Load subsystem CECs and transform
            // ------------------------------------------------------------------
            Map<Integer, List<List<String>>> subsystemsByOrder =
                    CECAssemblyService.subsystemsByOrder(system.getComponents());

            Map<Integer, double[]> transformedByOrder = new TreeMap<>();
            int progressSlice = subsystemCount > 0 ? 50 / subsystemCount : 50;
            int progressBase = 25;

            for (int order = 2; order < K; order++) {
                if (shouldStop()) return;

                List<List<String>> subsystemsAtOrder =
                        subsystemsByOrder.getOrDefault(order, Collections.emptyList());
                if (subsystemsAtOrder.isEmpty()) continue;

                double[] orderContributions = new double[tcf];

                for (List<String> subsys : subsystemsAtOrder) {
                    if (shouldStop()) return;

                        String subsysKey = CECAssemblyService.toElementString(subsys);
                        setStatusMessage("Processing subsystem " + subsysKey + " (order " + order + ")...");

                        String structurePhase = system.getStructurePhase();
                        Optional<SystemDataLoader.CECData> cecDataOpt = SystemDataLoader.loadCecData(
                            subsysKey, structurePhase, "", system.getModel());

                        if (cecDataOpt.isEmpty()) {
                        String msg = "CEC not found for subsystem " + subsysKey
                            + " (" + structurePhase
                            + "_" + system.getModel() + ")";
                        LOG.warning("CECAssemblyJob.run — " + msg);
                        markFailed(msg);
                        fireAssemblyFailed(msg);
                        return;
                    }

                    double[] sourceECIs = extractECIValues(cecDataOpt.get());
                    // Pad to target numCF if the subsystem CEC has fewer CFs than the target.
                    // Zeros are correct for positions that have no binary counterpart.
                    if (sourceECIs.length < tcf) {
                        double[] padded = new double[tcf];
                        System.arraycopy(sourceECIs, 0, padded, 0, sourceECIs.length);
                        sourceECIs = padded;
                    }
                    double[] transformed = CECAssemblyService.transformToTarget(
                            sourceECIs, order, K, cfOrderMap, targetData);

                    for (int i = 0; i < tcf && i < transformed.length; i++) {
                        orderContributions[i] += transformed[i];
                    }

                    fireSubsystemProcessed(subsysKey, order);
                    LOG.fine("CECAssemblyJob.run — transformed subsystem=" + subsysKey
                            + " order=" + order);

                    progressBase = Math.min(75, progressBase + progressSlice);
                    setProgress(progressBase);
                }

                transformedByOrder.put(order, orderContributions);
            }

            // ------------------------------------------------------------------
            // Step 4 — Compute derived ECIs and count pure-K CFs
            // ------------------------------------------------------------------
            setStatusMessage("Accumulating derived ECIs...");
            setProgress(80);
            if (shouldStop()) return;

            double[] derivedECIs = new double[tcf];
            for (double[] contributions : transformedByOrder.values()) {
                for (int i = 0; i < tcf && i < contributions.length; i++) {
                    derivedECIs[i] += contributions[i];
                }
            }

            int pureKCount = 0;
            for (int order : cfOrderMap) {
                if (order == K) pureKCount++;
            }

            // ------------------------------------------------------------------
            // Step 5 — Build result record
            // ------------------------------------------------------------------
            this.result = new AssemblyResult(system, targetData, cfOrderMap, derivedECIs, pureKCount);

            setProgress(100);
            setStatusMessage("CEC assembly complete");
            LOG.info("CECAssemblyJob.run — EXIT: COMPLETED — job=" + getId()
                    + ", tcf=" + tcf + ", pureKCount=" + pureKCount
                    + ", elapsed=" + (System.currentTimeMillis() - start) + " ms");

            markCompleted();
            fireAssemblyCompleted(this.result);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "CECAssemblyJob.run — EXCEPTION: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            String msg = "CEC assembly failed: " + e.getMessage();
            markFailed(msg);
            fireAssemblyFailed(msg);
        } finally {
            running = false;
        }
    }

    // -----------------------------------------------------------------------
    // Result accessor
    // -----------------------------------------------------------------------

    /**
     * Returns the assembly result.
     *
     * @return {@link AssemblyResult}; {@code null} if the job has not completed successfully
     */
    public AssemblyResult getResult() {
        return result;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Extracts double[] ECI values from a loaded {@link SystemDataLoader.CECData}.
     * Handles both the temperature-dependent {@code cecTerms} schema (uses the {@code a}
     * constant part) and the legacy flat {@code cecValues} array.
     * Falls back to an empty array if the CEC data has no entries.
     */
    private static double[] extractECIValues(SystemDataLoader.CECData cecData) {
        if (cecData == null) return new double[0];
        if (cecData.cecTerms != null && cecData.cecTerms.length > 0) {
            double[] vals = new double[cecData.cecTerms.length];
            for (int i = 0; i < cecData.cecTerms.length; i++) {
                vals[i] = cecData.cecTerms[i].a;
            }
            return vals;
        }
        if (cecData.cecValues != null) return cecData.cecValues.clone();
        return new double[0];
    }

    /**
     * Returns a flat list of all subsystem component lists across all orders
     * (used only for counting total subsystems for the listener callback).
     */
    private static List<List<String>> countAllSubsystems(List<String> components, int K) {
        List<List<String>> all = new ArrayList<>();
        Map<Integer, List<List<String>>> byOrder = CECAssemblyService.subsystemsByOrder(components);
        for (int order = 2; order < K; order++) {
            all.addAll(byOrder.getOrDefault(order, Collections.emptyList()));
        }
        return all;
    }

    // -----------------------------------------------------------------------
    // Listener fire helpers — guarded against null listener
    // -----------------------------------------------------------------------

    private void fireAssemblyStarted(String targetId, int subsystemCount) {
        if (operationListener != null) {
            try { operationListener.onAssemblyStarted(targetId, subsystemCount); }
            catch (Exception ex) { LOG.warning("CECOperationListener.onAssemblyStarted threw: " + ex.getMessage()); }
        }
    }

    private void fireSubsystemProcessed(String subsysKey, int order) {
        if (operationListener != null) {
            try { operationListener.onSubsystemProcessed(subsysKey, order); }
            catch (Exception ex) { LOG.warning("CECOperationListener.onSubsystemProcessed threw: " + ex.getMessage()); }
        }
    }

    private void fireAssemblyCompleted(AssemblyResult r) {
        if (operationListener != null) {
            try { operationListener.onAssemblyCompleted(r); }
            catch (Exception ex) { LOG.warning("CECOperationListener.onAssemblyCompleted threw: " + ex.getMessage()); }
        }
    }

    private void fireAssemblyFailed(String msg) {
        if (operationListener != null) {
            try { operationListener.onAssemblyFailed(msg); }
            catch (Exception ex) { LOG.warning("CECOperationListener.onAssemblyFailed threw: " + ex.getMessage()); }
        }
    }

    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}
