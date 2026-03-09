package org.ce.infrastructure.service;

import org.ce.application.usecase.MCSCalculationUseCase;
import org.ce.application.port.CalculationProgressPort;
import org.ce.application.port.CalculationProgressListener;
import org.ce.domain.cvm.CVMPhaseModel;
import org.ce.domain.cvm.CVMModelInput;
import org.ce.infrastructure.context.CVMCalculationContext;
import org.ce.infrastructure.context.MCSCalculationContext;
import org.ce.infrastructure.mcs.MCSRunnerAdapter;
import org.ce.infrastructure.persistence.migration.ClusterCachePreflight;
import org.ce.domain.identification.result.ClusCoordListResult;
import org.ce.domain.model.data.AllClusterData;
import org.ce.application.dto.CVMCalculationRequest;
import org.ce.application.dto.PreparationResult;
import org.ce.application.dto.MCSCalculationRequest;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.ce.infrastructure.data.ECILoader;
import org.ce.infrastructure.registry.KeyUtils;

import org.ce.infrastructure.logging.LoggingConfig;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Presentation-layer service for MCS and CVM calculation preparation.
 * 
 * <p><strong>Role:</strong> This service acts as a convenience faÃ§ade for the presentation
 * layer (GUI and CLI), handling infrastructure concerns like cache loading, registry lookups,
 * and ECI database access. It wraps these operations into a simple prepare-execute pattern.</p>
 * 
 * <p><strong>Architecture Position:</strong>
 * <ul>
 *   <li><strong>Above:</strong> Application use cases ({@link org.ce.application.calculation})</li>
 *   <li><strong>Below:</strong> Presentation controllers (GUI panels, CLI commands)</li>
 *   <li><strong>Coordinates:</strong> Infrastructure adapters (cache, registry, ECI loader)</li>
 * </ul>
 * 
 * <p><strong>Responsibilities:</strong>
 * <ul>
 *   <li>System lookup from {@link SystemRegistry}</li>
 *   <li>Cache access via {@link org.ce.infrastructure.cache.AllClusterDataCache}</li>
 *   <li>ECI/CEC database loading via {@link org.ce.infrastructure.eci.ECILoader}</li>
 *   <li>Context/model assembly and validation</li>
 *   <li>Thin delegation to application use cases</li>
 * </ul>
 * 
 * <p><strong>Future Direction (PR-9+):</strong> This service could be eliminated
 * by moving preparation logic into infrastructure-layer factories and having GUI/CLI
 * call use cases directly. For now, it remains as a single point of orchestration
 * to avoid duplicating preparation code across presentation layers.</p>
 * 
 * @see org.ce.application.mcs.MCSCalculationUseCase
 * @see org.ce.application.cvm.CVMCalculationUseCase
 * @since 2.0 (refactored from direct workbench coupling)
 */
public class CalculationService {

    private static final Logger LOG = LoggingConfig.getLogger(CalculationService.class);

    private final SystemRegistry registry;
    private final CalculationProgressListener listener;
    
    /**
     * Creates a new calculation service.
     * 
     * @param registry the system registry
     * @param listener progress listener for logging and updates
     */
    public CalculationService(SystemRegistry registry, CalculationProgressListener listener) {
        this.registry = registry;
        this.listener = listener;
        ClusterCachePreflight.runOnce(this.listener::logMessage);
    }
    
    /**
     * Prepares an MCS calculation context from the request.
     * 
     * <p>This method performs all data loading and validation:
     * <ol>
     *   <li>Looks up the system from registry</li>
     *   <li>Loads cluster data from cache</li>
     *   <li>Loads ECI/CEC from database</li>
     *   <li>Assembles and validates the context</li>
     * </ol>
     * 
     * @param request the MCS calculation request
     * @return result containing the prepared context or error message
     */
    public PreparationResult<MCSCalculationContext> prepareMCS(MCSCalculationRequest request) {
        LOG.info("CalculationService.prepareMCS — ENTER: system=" + request.getSystemId()
                + ", T=" + request.getTemperature() + " K, x=" + request.getComposition()
                + ", L=" + request.getSupercellSize()
                + ", nEquil=" + request.getEquilibrationSteps()
                + ", nAvg=" + request.getAveragingSteps());
        listener.logMessage("\n>>> MCS Calculation Requested");
        listener.logMessage("Request: " + request);

        // 1. Look up system
        SystemIdentity system = registry.getSystem(request.getSystemId());
        if (system == null) {
            LOG.warning("CalculationService.prepareMCS — FAILED: system not found: " + request.getSystemId());
            return PreparationResult.failure("System not found: " + request.getSystemId());
        }
        
        if (!registry.isClustersComputed(system.getId())) {
            return PreparationResult.failure(
                "Clusters not computed for system '" + system.getName() + "'.\n" +
                "Run the identification pipeline first.");
        }
        
        listener.logMessage("System: " + system.getName());
        listener.logMessage("Seed: " + request.getSeed());
        listener.logMessage("Parameters validated. Loading required data...");
        
        // 2. Create calculation context
        MCSCalculationContext context = new MCSCalculationContext(
            system,
            request.getTemperature(),
            request.getComposition(),
            request.getSupercellSize(),
            request.getEquilibrationSteps(),
            request.getAveragingSteps(),
            request.getSeed()
        );
        
        // 3. Derive keys
        String elementsStr = String.join("-", system.getComponents());
        String cecKey = KeyUtils.cecKey(system);
        String clusterKey = KeyUtils.clusterKey(system);
        String componentSuffix = KeyUtils.componentSuffix(system.getNumComponents());
        
        listener.logMessage("[MCS] CEC key     : " + cecKey);
        listener.logMessage("[MCS] Cluster key : " + clusterKey);
        
        // 4. Load cluster data from unified cache
        listener.logMessage("[MCS] Loading cluster data from cache...");
        ClusCoordListResult clusterData;
        AllClusterData allData = null;
        try {
            // Load from AllClusterDataCache and extract the MCS-relevant subset
            Optional<AllClusterData> cached = AllClusterDataCache.load(clusterKey);
            if (cached.isEmpty()) {
                LOG.warning("CalculationService.prepareMCS — FAILED: no cluster cache for key=" + clusterKey);
                return PreparationResult.failure(
                    "No valid cluster data found for '" + clusterKey + "'.\n\n" +
                    "The identification pipeline has not been run for this " +
                    componentSuffix + " " + system.getStructure() + "_" + system.getPhase() + " system.\n\n" +
                    "Delete this system, recreate it, and run identification.");
            }
            allData = cached.get();
            if (allData.getStage1() == null || allData.getStage1().getDisClusterData() == null) {
                return PreparationResult.failure(
                    "Cluster data for '" + clusterKey + "' is missing Stage 1 (cluster identification).\n\n" +
                    "The cached data may be corrupted. Delete and re-run identification.");
            }
            clusterData = allData.getStage1().getDisClusterData();
        } catch (Exception ex) {
            return PreparationResult.failure(
                "Failed to load cluster data for '" + clusterKey + "':\n" + ex.getMessage());
        }

        context.setClusterData(clusterData);
        // Also set AllClusterData for ECI validation (expects ncf, not tc)
        if (allData != null && context instanceof MCSCalculationContext mcsContext) {
            mcsContext.setAllClusterData(allData);
        }
        listener.logMessage("✔ Cluster data loaded: tc=" + clusterData.getTc() +
            "  orbitList=" + clusterData.getOrbitList().size());

        // 5. Load ECI/CEC
        // For MCS, ECI count must match ncf (non-point CFs), not tc (total cluster types)
        int requiredECILength = (allData != null && allData.getStage2() != null)
            ? allData.getStage2().getNcf()
            : clusterData.getTc();  // fallback if Stage2 not available
        listener.logMessage("[MCS] Loading CEC  key=" + cecKey + "  required length=" + requiredECILength);
        
        Optional<double[]> eciOpt = loadECI(elementsStr, system, request.getTemperature(), requiredECILength);
        
        if (eciOpt.isEmpty()) {
            LOG.warning("CalculationService.prepareMCS — FAILED: ECI load failed/cancelled for system=" + request.getSystemId());
            return PreparationResult.failure("ECI loading cancelled or failed. Cannot run MCS.");
        }

        // Expand ncf-length ECI to tc-length by padding with zeros for point/empty clusters
        // (MCS embeddings use cluster types 0 to tc-1, but only ncf are optimization parameters)
        double[] loadedECI = eciOpt.get();
        double[] expandedECI = loadedECI;
        if (loadedECI.length < clusterData.getTc()) {
            expandedECI = new double[clusterData.getTc()];
            System.arraycopy(loadedECI, 0, expandedECI, 0, loadedECI.length);
            // Remaining indices (point=tc-2, empty=tc-1) stay 0.0
            listener.logMessage("[MCS] Expanded ECI from " + loadedECI.length + " (ncf) to "
                    + expandedECI.length + " (tc) by padding with zeros for constants");
        }

        context.setECI(expandedECI);
        listener.logMessage("✔ ECI set: " + expandedECI.length + " values (tc=" + clusterData.getTc() + ")");

        // 6. Validate context readiness
        if (!context.isReady()) {
            LOG.warning("CalculationService.prepareMCS — FAILED: ECI/Cluster mismatch — " + context.getReadinessError());
            return PreparationResult.failure("ECI/Cluster Mismatch: " + context.getReadinessError());
        }
        LOG.info("CalculationService.prepareMCS — EXIT: context ready — tc=" + clusterData.getTc()
                + " cluster types, ECI length=" + expandedECI.length);
        return PreparationResult.success(context);
    }
    
    /**
     * Prepares a CVMPhaseModel for parameter-scan calculations.
     * 
     * <p>This method performs all data loading and validation, then creates
     * a CVMPhaseModel ready for queries and parameter mutations:
     * <ol>
     *   <li>Looks up the system from registry</li>
     *   <li>Loads AllClusterData from cache</li>
     *   <li>Loads ECI/CEC from database</li>
     *   <li>Creates and initializes CVMPhaseModel with first minimization</li>
     * </ol>
     * 
     * <p>The returned model can then be used for efficient parameter scanning
     * by calling setTemperature(), setComposition(), etc., which trigger
     * automatic re-minimization only when needed.</p>
     * 
     * @param request the CVM calculation request (provides initial parameters)
     * @return result containing the prepared CVMPhaseModel or error message
     */
    public PreparationResult<CVMPhaseModel> prepareCVMModel(CVMCalculationRequest request) {
        LOG.info("CalculationService.prepareCVMModel — ENTER: system=" + request.getSystemId()
                + ", T=" + request.getTemperature() + " K, x=" + request.getComposition()
                + ", tolerance=" + request.getTolerance());
        listener.logMessage("\n>>> CVM Phase Model Creation Requested");
        listener.logMessage("Request: " + request);
        
        // 1. Look up system
        SystemIdentity system = registry.getSystem(request.getSystemId());
        if (system == null) {
            return PreparationResult.failure("System not found: " + request.getSystemId());
        }
        
        if (!registry.isCfsComputed(system.getId())) {
            return PreparationResult.failure(
                "CFs not computed for system '" + system.getName() + "'.\n" +
                "Run the identification pipeline first.");
        }
        
        listener.logMessage("System: " + system.getName());
        listener.logMessage("Temperature: " + request.getTemperature() + " K");
        listener.logMessage("Composition: " + request.getComposition());
        listener.logMessage("Tolerance: " + request.getTolerance());
        listener.logMessage("Parameters validated. Loading required data...");
        
        // 2. Derive keys
        String clusterKey = KeyUtils.clusterKey(system);
        String cecKey = KeyUtils.cecKey(system);
        String elementsStr = String.join("-", system.getComponents());
        
        listener.logMessage("[CVM] CEC key     : " + cecKey);
        listener.logMessage("[CVM] Cluster key : " + clusterKey);
        
        // 3. Load AllClusterData
        listener.logMessage("[CVM] Loading AllClusterData from cache...");
        AllClusterData allData;
        try {
            Optional<AllClusterData> cached = AllClusterDataCache.load(clusterKey);
            if (cached.isEmpty()) {
                return PreparationResult.failure(
                    "No AllClusterData found for '" + clusterKey + "'.\n\n" +
                    "The identification pipeline may not have saved the complete data.\n" +
                    "Delete this system, recreate it, and run identification again.");
            }
            allData = cached.get();
        } catch (Exception ex) {
            return PreparationResult.failure(
                "Failed to load AllClusterData for '" + clusterKey + "':\n" + ex.getMessage());
        }
        
        if (!allData.isComplete()) {
            return PreparationResult.failure(
                "AllClusterData for '" + clusterKey + "' is incomplete.\n\n" + 
                allData.getCompletionStatus());
        }
        
        listener.logMessage("✔ AllClusterData loaded: " + allData);
        listener.logMessage("  Stage 1: tcdis=" + allData.getTcdis());
        listener.logMessage("  Stage 2: tcf=" + allData.getTcf());
        listener.logMessage("  Stage 3: C-matrix ready");
        
        // 4. Load ECI/CEC
        // CVM expects ncf-length ECI (non-point cluster functions)
        int requiredECILength = (allData.getStage2() != null)
            ? allData.getStage2().getNcf()
            : allData.getStage1().getDisClusterData().getTc();  // fallback
        listener.logMessage("[CVM] Loading CEC  key=" + cecKey + "  required length=" + requiredECILength);
        
        Optional<double[]> eciOpt = loadECI(elementsStr, system, request.getTemperature(), requiredECILength);
        
        if (eciOpt.isEmpty()) {
            return PreparationResult.failure("ECI loading cancelled or failed. Cannot create CVM model.");
        }

        double[] cvmEci;
        try {
            cvmEci = mapCECToCvmECI(eciOpt.get(), allData, "CVM Phase Model");
        } catch (IllegalArgumentException ex) {
            return PreparationResult.failure("CVM Phase Model ECI mapping failed: " + ex.getMessage());
        }
        
        listener.logMessage("✔ CVM ECI set: " + cvmEci.length + " values (non-point CF basis)");

        CVMModelInput cvmInput;
        try {
            cvmInput = new CVMModelInput(
                system.getId(),
                system.getName(),
                allData.getNumComponents(),
                allData.getStage1(),
                allData.getStage2(),
                allData.getStage3());
        } catch (IllegalArgumentException ex) {
            return PreparationResult.failure("CVM stage-data contract invalid: " + ex.getMessage());
        }
        
        // 6. Create CVMPhaseModel
        try {
            listener.logMessage("[CVM] Creating CVMPhaseModel...");
            CVMPhaseModel model = CVMPhaseModel.create(
                cvmInput,
                cvmEci,
                request.getTemperature(),
                request.getComposition());
            
            listener.logMessage("✔ CVMPhaseModel created successfully");
            listener.logMessage("  First minimization completed");
            listener.logMessage("  Model ready for parameter scanning");
            listener.logMessage("  Input: system=" + cvmInput.getSystemName() +
                " components=" + cvmInput.getNumComponents());
            
            LOG.info("CalculationService.prepareCVMModel — EXIT: CVMPhaseModel ready, system=" + system.getId()
                    + ", tcdis=" + allData.getTcdis()
                    + ", tcf=" + allData.getTcf()
                    + ", ncf=" + allData.getStage2().getNcf()
                    + ", ECI length=" + cvmEci.length);
            return PreparationResult.success(model);

        } catch (Exception ex) {
            LOG.log(java.util.logging.Level.WARNING,
                    "CalculationService.prepareCVMModel — EXCEPTION creating CVMPhaseModel", ex);
            return PreparationResult.failure(
                "Failed to create CVMPhaseModel: " + ex.getMessage());
        }
    }
    
    /**
     * Executes an MCS calculation with the prepared context.
     * 
     * <p>This method should be called on a background thread for GUI applications
     * to avoid blocking the UI. Delegates to {@link MCSCalculationUseCase} internally.</p>
     * 
     * <p><strong>Note:</strong> This is a thin wrapper for backwards compatibility.
     * Future PR iterations may remove this in favor of direct use-case invocation
     * by presentation controllers.</p>
     * 
     * @param context the prepared MCS context
     */
    public void executeMCS(MCSCalculationContext context) {
        // Adapt legacy listener to new MCS progress port
        CalculationProgressPort progressPort = new MCSProgressListenerAdapter(listener);
        MCSCalculationUseCase useCase = new MCSCalculationUseCase(progressPort, new MCSRunnerAdapter());
        useCase.execute(context);
    }
    
    /**
     * Helper method to load ECI from database with fallback to interactive input.
     */
    private Optional<double[]> loadECI(String elementsStr, SystemIdentity system,
                                        double temperature, int requiredLength) {
        LOG.fine("CalculationService.loadECI — ENTER: elements=" + elementsStr
                + ", structure=" + system.getStructure() + ", phase=" + system.getPhase()
                + ", model=" + system.getModel()
                + ", T=" + temperature + ", required length=" + requiredLength);
        ECILoader.DBLoadResult dbResult = ECILoader.loadECIFromDatabase(
            elementsStr,
            system.getStructure(),
            system.getPhase(),
            system.getModel(),
            temperature,
            requiredLength);
        
        if (dbResult.status == ECILoader.DBLoadResult.Status.OK) {
            LOG.fine("CalculationService.loadECI — EXIT: loaded " + dbResult.eci.length
                    + " ECI values from database (T-dependent=" + dbResult.temperatureEvaluated + ")");
            listener.logMessage("✔ CEC loaded from database: " + dbResult.message);
            if (dbResult.temperatureEvaluated) {
                listener.logMessage("  (T-dependent terms evaluated at T=" + temperature + "K)");
            }
            return Optional.of(dbResult.eci);
        }
        
        listener.logMessage("âš  CEC database load failed: " + dbResult.message);
        
        // Fallback to interactive input (for GUI only)
        LOG.fine("CalculationService.loadECI — EXIT: database miss, falling back to interactive input");
        return ECILoader.loadOrInputECI(
            elementsStr,
            system.getStructure(),
            system.getPhase(),
            system.getModel(),
            temperature,
            requiredLength);
    }

    /**
     * Maps full CEC vectors from database ordering to CVM solver ECI ordering.
     *
     * <p>CEC files store cluster ECIs in descending body-count order:
     * {@code [tet, tri, pair1, pair2, ..., point, empty]}.
     * The CVM solver requires only the {@code ncf} non-point cluster ECIs,
     * i.e., the first {@code ncf} elements of the raw CEC array.</p>
     *
     * <p>Supported sizes:
     * <ul>
     *   <li>exactly ncf: used directly (already contains only non-point ECIs)</li>
     *   <li>(ncf + 1): drops the trailing point-cluster term</li>
     *   <li>(ncf + 2): drops the trailing point and empty-cluster terms</li>
     * </ul>
     * In all overshoot cases the first {@code ncf} values are taken.</p>
     */
    private double[] mapCECToCvmECI(double[] cecRaw, AllClusterData allData, String modeName) {
        int ncf = allData.getStage2().getNcf();

        if (cecRaw.length == ncf) {
            listener.logMessage("[" + modeName + "] CEC length matches CVM ncf=" + ncf + " (no mapping needed)");
            return cecRaw.clone();
        }

        if (cecRaw.length == ncf + 1) {
            listener.logMessage("[" + modeName + "] Mapping CEC (" + cecRaw.length + ") -> CVM ECI (" + ncf + ") by dropping trailing point-cluster term");
            double[] mapped = new double[ncf];
            System.arraycopy(cecRaw, 0, mapped, 0, ncf);
            return mapped;
        }

        if (cecRaw.length == ncf + 2) {
            listener.logMessage("[" + modeName + "] Mapping CEC (" + cecRaw.length + ") -> CVM ECI (" + ncf + ") by dropping trailing point and empty-cluster terms");
            double[] mapped = new double[ncf];
            System.arraycopy(cecRaw, 0, mapped, 0, ncf);
            return mapped;
        }

        throw new IllegalArgumentException(
            "Unsupported CEC length " + cecRaw.length +
            " for CVM ncf=" + ncf +
            ". Expected one of {" + ncf + ", " + (ncf + 1) + ", " + (ncf + 2) + "}.");
    }
}


