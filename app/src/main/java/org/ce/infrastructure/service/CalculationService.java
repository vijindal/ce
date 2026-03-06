package org.ce.infrastructure.service;

import org.ce.application.mcs.MCSCalculationUseCase;
import org.ce.application.port.CalculationProgressPort;
import org.ce.application.service.CalculationProgressListener;
import org.ce.domain.cvm.CVMPhaseModel;
import org.ce.domain.model.cvm.CVMModelInput;
import org.ce.infrastructure.context.CVMCalculationContext;
import org.ce.infrastructure.context.MCSCalculationContext;
import org.ce.infrastructure.mcs.MCSRunnerAdapter;
import org.ce.infrastructure.persistence.migration.ClusterCachePreflight;
import org.ce.domain.identification.result.ClusCoordListResult;
import org.ce.infrastructure.adapter.MCSProgressListenerAdapter;
import org.ce.domain.model.data.AllClusterData;
import org.ce.application.dto.CVMCalculationRequest;
import org.ce.application.dto.PreparationResult;
import org.ce.application.dto.MCSCalculationRequest;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.cache.AllClusterDataCache;
import org.ce.infrastructure.eci.ECILoader;
import org.ce.infrastructure.key.KeyUtils;

import java.util.Optional;

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
        listener.logMessage("\n>>> MCS Calculation Requested");
        listener.logMessage("Request: " + request);
        
        // 1. Look up system
        SystemIdentity system = registry.getSystem(request.getSystemId());
        if (system == null) {
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
        try {
            // Load from AllClusterDataCache and extract the MCS-relevant subset
            Optional<AllClusterData> cached = AllClusterDataCache.load(clusterKey);
            if (cached.isEmpty()) {
                return PreparationResult.failure(
                    "No valid cluster data found for '" + clusterKey + "'.\n\n" +
                    "The identification pipeline has not been run for this " +
                    componentSuffix + " " + system.getStructure() + "_" + system.getPhase() + " system.\n\n" +
                    "Delete this system, recreate it, and run identification.");
            }
            AllClusterData allData = cached.get();
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
        listener.logMessage("âœ“ Cluster data loaded: tc=" + clusterData.getTc() +
            "  orbitList=" + clusterData.getOrbitList().size());
        
        // 5. Load ECI/CEC
        int requiredECILength = clusterData.getTc();
        listener.logMessage("[MCS] Loading CEC  key=" + cecKey + "  required length=" + requiredECILength);
        
        Optional<double[]> eciOpt = loadECI(elementsStr, system, request.getTemperature(), requiredECILength);
        
        if (eciOpt.isEmpty()) {
            return PreparationResult.failure("ECI loading cancelled or failed. Cannot run MCS.");
        }
        
        context.setECI(eciOpt.get());
        listener.logMessage("âœ“ ECI set: " + eciOpt.get().length + " values");
        
        // 6. Validate context readiness
        if (!context.isReady()) {
            return PreparationResult.failure("ECI/Cluster Mismatch: " + context.getReadinessError());
        }
        
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
        
        listener.logMessage("âœ“ AllClusterData loaded: " + allData);
        listener.logMessage("  Stage 1: tcdis=" + allData.getTcdis());
        listener.logMessage("  Stage 2: tcf=" + allData.getTcf());
        listener.logMessage("  Stage 3: C-matrix ready");
        
        // 4. Load ECI/CEC
        int requiredECILength = allData.getStage1().getDisClusterData().getTc();
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
        
        listener.logMessage("âœ“ CVM ECI set: " + cvmEci.length + " values (non-point CF basis)");

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
            
            listener.logMessage("âœ“ CVMPhaseModel created successfully");
            listener.logMessage("  First minimization completed");
            listener.logMessage("  Model ready for parameter scanning");
            listener.logMessage("  Input: system=" + cvmInput.getSystemName() +
                " components=" + cvmInput.getNumComponents());
            
            return PreparationResult.success(model);
            
        } catch (Exception ex) {
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
        ECILoader.DBLoadResult dbResult = ECILoader.loadECIFromDatabase(
            elementsStr,
            system.getStructure(),
            system.getPhase(),
            system.getModel(),
            temperature,
            requiredLength);
        
        if (dbResult.status == ECILoader.DBLoadResult.Status.OK) {
            listener.logMessage("âœ“ CEC loaded from database: " + dbResult.message);
            if (dbResult.temperatureEvaluated) {
                listener.logMessage("  (T-dependent terms evaluated at T=" + temperature + "K)");
            }
            return Optional.of(dbResult.eci);
        }
        
        listener.logMessage("âš  CEC database load failed: " + dbResult.message);
        
        // Fallback to interactive input (for GUI only)
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
     * <p>Database CEC vectors are stored by cluster type and commonly include
     * both empty-cluster and point-cluster terms. CVM minimization uses only
     * non-point CF terms (pairs and higher), length = ncf.</p>
     *
     * <p>Supported mappings:
     * <ul>
     *   <li>already ncf length: used directly</li>
     *   <li>(ncf + 1): drops leading empty-cluster term</li>
     *   <li>(ncf + 2): drops leading empty and point terms (pairs onward)</li>
     * </ul></p>
     */
    private double[] mapCECToCvmECI(double[] cecRaw, AllClusterData allData, String modeName) {
        int ncf = allData.getStage2().getNcf();

        if (cecRaw.length == ncf) {
            listener.logMessage("[" + modeName + "] CEC length matches CVM ncf=" + ncf + " (no mapping needed)");
            return cecRaw.clone();
        }

        if (cecRaw.length == ncf + 1) {
            listener.logMessage("[" + modeName + "] Mapping CEC (" + cecRaw.length + ") -> CVM ECI (" + ncf + ") by dropping empty-cluster term");
            double[] mapped = new double[ncf];
            System.arraycopy(cecRaw, 1, mapped, 0, ncf);
            return mapped;
        }

        if (cecRaw.length == ncf + 2) {
            listener.logMessage("[" + modeName + "] Mapping CEC (" + cecRaw.length + ") -> CVM ECI (" + ncf + ") by dropping empty and point terms (pairs+)");
            double[] mapped = new double[ncf];
            System.arraycopy(cecRaw, 2, mapped, 0, ncf);
            return mapped;
        }

        throw new IllegalArgumentException(
            "Unsupported CEC length " + cecRaw.length +
            " for CVM ncf=" + ncf +
            ". Expected one of {" + ncf + ", " + (ncf + 1) + ", " + (ncf + 2) + "}.");
    }
}


