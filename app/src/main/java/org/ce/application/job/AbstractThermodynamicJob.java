package org.ce.application.job;

import org.ce.application.dto.ThermodynamicCalculationRequest;
import org.ce.application.port.DataManagementPort;
import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.logging.LoggingConfig;
import org.ce.infrastructure.registry.KeyUtils;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Abstract base for thermodynamic calculation jobs (CVM, MCS).
 *
 * <p>Encapsulates the two data-loading phases shared by every
 * thermodynamic job:</p>
 * <ol>
 *   <li><strong>Phase 1</strong> — load {@link SystemIdentity} and
 *       {@link AllClusterData} from the cluster cache.</li>
 *   <li><strong>Phase 2</strong> — load the ncf-length ECI array from the
 *       CEC database.</li>
 * </ol>
 *
 * <p>Subclasses call {@link #loadSystemData(ThermodynamicCalculationRequest)}
 * at the start of their {@code run()} method and proceed to engine-specific
 * phases with the returned {@link ThermodynamicJobData} bundle.</p>
 */
public abstract class AbstractThermodynamicJob extends AbstractBackgroundJob {

    private static final Logger LOG =
            LoggingConfig.getLogger(AbstractThermodynamicJob.class);

    protected final DataManagementPort dataPort;

    protected AbstractThermodynamicJob(
            String id, String name, DataManagementPort dataPort) {
        super(id, name, null); // system is loaded dynamically in run()
        this.dataPort = dataPort;
    }

    /**
     * Immutable bundle of the data loaded during Phases 1 and 2.
     *
     * @param system      resolved system identity
     * @param clusterData pre-computed cluster identification data
     * @param ncfEci      ncf-length ECI array from the CEC database
     */
    public record ThermodynamicJobData(
            SystemIdentity system,
            AllClusterData clusterData,
            double[] ncfEci) {}

    /**
     * Executes Phases 1 and 2 for the given request.
     *
     * <p>On success, returns a non-null {@link ThermodynamicJobData}.
     * On failure, calls {@link #markFailed(String)} and returns
     * {@code null} — the caller should check for {@code null} and
     * return immediately.</p>
     *
     * <p>Progress is advanced to 20 % upon success.</p>
     *
     * @param request the thermodynamic calculation request
     * @return loaded data, or {@code null} if loading failed
     */
    protected ThermodynamicJobData loadSystemData(
            ThermodynamicCalculationRequest request) {

        // --- Phase 1: System metadata ---
        setStatusMessage("Loading system metadata...");
        setProgress(5);
        if (shouldStop()) return null;

        SystemIdentity system = dataPort.getSystem(request.getSystemId());
        if (system == null) {
            markFailed("System not found: " + request.getSystemId());
            return null;
        }

        setStatusMessage("Loading cluster data...");
        setProgress(10);
        if (shouldStop()) return null;

        String clusterKey = KeyUtils.clusterKey(system);
        Optional<AllClusterData> allDataOpt = dataPort.loadClusterData(clusterKey);
        if (allDataOpt.isEmpty()) {
            markFailed("Cluster data not found for key: " + clusterKey);
            return null;
        }
        AllClusterData allData = allDataOpt.get();

        // --- Phase 2: ECI ---
        setStatusMessage("Loading CEC/ECI database...");
        setProgress(20);
        if (shouldStop()) return null;

        Optional<double[]> nciEciOpt = dataPort.loadECI(
                String.join("-", system.getComponents()),
                system.getStructure(),
                system.getPhase(),
                system.getModel(),
                request.getTemperature(),
                allData.getStage2().getNcf()
        );
        if (nciEciOpt.isEmpty()) {
            markFailed("CEC not found for key: " + KeyUtils.cecKey(system)
                    + ". Use Data > CEC Database to add it.");
            return null;
        }

        LOG.fine("AbstractThermodynamicJob.loadSystemData — loaded system="
                + system.getId() + " ncfEci.length=" + nciEciOpt.get().length);

        return new ThermodynamicJobData(system, allData, nciEciOpt.get());
    }
}
