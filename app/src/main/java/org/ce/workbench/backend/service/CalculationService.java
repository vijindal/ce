package org.ce.workbench.backend.service;

import org.ce.application.calculation.CVMCalculationUseCase;
import org.ce.application.calculation.MCSCalculationUseCase;
import org.ce.application.port.CalculationProgressPort;
import org.ce.identification.result.ClusCoordListResult;
import org.ce.presentation.adapter.CalculationProgressListenerAdapter;
import org.ce.presentation.adapter.MCSProgressListenerAdapter;
import org.ce.workbench.backend.data.AllClusterData;
import org.ce.workbench.backend.dto.CVMCalculationRequest;
import org.ce.workbench.backend.dto.CalculationResult;
import org.ce.workbench.backend.dto.MCSCalculationRequest;
import org.ce.workbench.backend.registry.SystemRegistry;
import org.ce.workbench.model.SystemIdentity;
import org.ce.workbench.util.cache.AllClusterDataCache;
import org.ce.workbench.util.context.CVMCalculationContext;
import org.ce.workbench.util.context.MCSCalculationContext;
import org.ce.workbench.util.eci.ECILoader;
import org.ce.workbench.util.key.KeyUtils;

import java.util.Optional;

/**
 * Service layer for MCS and CVM calculations.
 * 
 * <p>Encapsulates all business logic for preparing and executing calculations:
 * <ul>
 *   <li>System lookup from registry</li>
 *   <li>Cache access for cluster data</li>
 *   <li>ECI/CEC database loading</li>
 *   <li>Context assembly and validation</li>
 *   <li>Executor delegation</li>
 * </ul>
 * 
 * <p>This service can be used by both GUI and CLI interfaces, eliminating
 * duplication of business logic in UI classes.</p>
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
    public CalculationResult<MCSCalculationContext> prepareMCS(MCSCalculationRequest request) {
        listener.logMessage("\n>>> MCS Calculation Requested");
        listener.logMessage("Request: " + request);
        
        // 1. Look up system
        SystemIdentity system = registry.getSystem(request.getSystemId());
        if (system == null) {
            return CalculationResult.failure("System not found: " + request.getSystemId());
        }
        
        if (!registry.isClustersComputed(system.getId())) {
            return CalculationResult.failure(
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
                return CalculationResult.failure(
                    "No valid cluster data found for '" + clusterKey + "'.\n\n" +
                    "The identification pipeline has not been run for this " +
                    componentSuffix + " " + system.getStructure() + "_" + system.getPhase() + " system.\n\n" +
                    "Delete this system, recreate it, and run identification.");
            }
            AllClusterData allData = cached.get();
            if (allData.getStage1() == null || allData.getStage1().getDisClusterData() == null) {
                return CalculationResult.failure(
                    "Cluster data for '" + clusterKey + "' is missing Stage 1 (cluster identification).\n\n" +
                    "The cached data may be corrupted. Delete and re-run identification.");
            }
            clusterData = allData.getStage1().getDisClusterData();
        } catch (Exception ex) {
            return CalculationResult.failure(
                "Failed to load cluster data for '" + clusterKey + "':\n" + ex.getMessage());
        }
        
        context.setClusterData(clusterData);
        listener.logMessage("✓ Cluster data loaded: tc=" + clusterData.getTc() +
            "  orbitList=" + clusterData.getOrbitList().size());
        
        // 5. Load ECI/CEC
        int requiredECILength = clusterData.getTc();
        listener.logMessage("[MCS] Loading CEC  key=" + cecKey + "  required length=" + requiredECILength);
        
        Optional<double[]> eciOpt = loadECI(elementsStr, system, request.getTemperature(), requiredECILength);
        
        if (eciOpt.isEmpty()) {
            return CalculationResult.failure("ECI loading cancelled or failed. Cannot run MCS.");
        }
        
        context.setECI(eciOpt.get());
        listener.logMessage("✓ ECI set: " + eciOpt.get().length + " values");
        
        // 6. Validate context readiness
        if (!context.isReady()) {
            return CalculationResult.failure("ECI/Cluster Mismatch: " + context.getReadinessError());
        }
        
        return CalculationResult.success(context);
    }
    
    /**
     * Prepares a CVM calculation context from the request.
     * 
     * <p>This method performs all data loading and validation:
     * <ol>
     *   <li>Looks up the system from registry</li>
     *   <li>Loads AllClusterData from cache</li>
     *   <li>Loads ECI/CEC from database</li>
     *   <li>Assembles and validates the context</li>
     * </ol>
     * 
     * @param request the CVM calculation request
     * @return result containing the prepared context or error message
     */
    public CalculationResult<CVMCalculationContext> prepareCVM(CVMCalculationRequest request) {
        listener.logMessage("\n>>> CVM Calculation Requested");
        listener.logMessage("Request: " + request);
        
        // 1. Look up system
        SystemIdentity system = registry.getSystem(request.getSystemId());
        if (system == null) {
            return CalculationResult.failure("System not found: " + request.getSystemId());
        }
        
        if (!registry.isCfsComputed(system.getId())) {
            return CalculationResult.failure(
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
                return CalculationResult.failure(
                    "No AllClusterData found for '" + clusterKey + "'.\n\n" +
                    "The identification pipeline may not have saved the complete data.\n" +
                    "Delete this system, recreate it, and run identification again.");
            }
            allData = cached.get();
        } catch (Exception ex) {
            return CalculationResult.failure(
                "Failed to load AllClusterData for '" + clusterKey + "':\n" + ex.getMessage());
        }
        
        if (!allData.isComplete()) {
            return CalculationResult.failure(
                "AllClusterData for '" + clusterKey + "' is incomplete.\n\n" + 
                allData.getCompletionStatus());
        }
        
        listener.logMessage("✓ AllClusterData loaded: " + allData);
        listener.logMessage("  Stage 1: tcdis=" + allData.getTcdis());
        listener.logMessage("  Stage 2: tcf=" + allData.getTcf());
        listener.logMessage("  Stage 3: C-matrix ready");
        
        // 4. Load ECI/CEC
        int requiredECILength = allData.getStage1().getTc();
        listener.logMessage("[CVM] Loading CEC  key=" + cecKey + "  required length=" + requiredECILength);
        
        Optional<double[]> eciOpt = loadECI(elementsStr, system, request.getTemperature(), requiredECILength);
        
        if (eciOpt.isEmpty()) {
            return CalculationResult.failure("ECI loading cancelled or failed. Cannot run CVM.");
        }
        
        // 5. Build CVM context
        CVMCalculationContext context = new CVMCalculationContext(
            system, request.getTemperature(), request.getComposition(), request.getTolerance());
        context.setAllClusterData(allData);
        context.setECI(eciOpt.get());
        listener.logMessage("✓ ECI set: " + eciOpt.get().length + " values");
        
        // 6. Validate context readiness
        if (!context.isReady()) {
            return CalculationResult.failure("CVM Context Not Ready: " + context.getReadinessError());
        }
        
        listener.logMessage("✓ CVM context is ready");
        listener.logMessage(context.getSummary());
        
        return CalculationResult.success(context);
    }
    
    /**
     * Executes an MCS calculation with the prepared context.
     * 
     * <p>This method should be called on a background thread for GUI applications
     * to avoid blocking the UI. Uses the new application layer use case internally.</p>
     * 
     * @param context the prepared MCS context
     */
    public void executeMCS(MCSCalculationContext context) {
        // Adapt legacy listener to new MCS progress port
        CalculationProgressPort progressPort = new MCSProgressListenerAdapter(listener);
        MCSCalculationUseCase useCase = new MCSCalculationUseCase(progressPort);
        useCase.execute(context);
    }
    
    /**
     * Executes a CVM calculation with the prepared context.
     * 
     * <p>This method should be called on a background thread for GUI applications
     * to avoid blocking the UI. Uses the new application layer use case internally.</p>
     * 
     * @param context the prepared CVM context
     */
    public void executeCVM(CVMCalculationContext context) {
        // Adapt legacy listener to new progress port
        CalculationProgressPort progressPort = new CalculationProgressListenerAdapter(listener);
        CVMCalculationUseCase useCase = new CVMCalculationUseCase(progressPort);
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
            listener.logMessage("✓ CEC loaded from database: " + dbResult.message);
            if (dbResult.temperatureEvaluated) {
                listener.logMessage("  (T-dependent terms evaluated at T=" + temperature + "K)");
            }
            return Optional.of(dbResult.eci);
        }
        
        listener.logMessage("⚠ CEC database load failed: " + dbResult.message);
        
        // Fallback to interactive input (for GUI only)
        return ECILoader.loadOrInputECI(
            elementsStr,
            system.getStructure(),
            system.getPhase(),
            system.getModel(),
            temperature,
            requiredLength);
    }
}
