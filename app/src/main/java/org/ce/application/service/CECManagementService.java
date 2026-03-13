package org.ce.application.service;

import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.logging.LoggingConfig;
import org.ce.infrastructure.registry.WorkspaceManager;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Application-layer service for CEC load / save / validate operations.
 *
 * <p>This is the single authorised gateway for all CEC I/O.  Previously,
 * {@code CECManagementPanel} called {@link SystemDataLoader} directly and
 * hardcoded {@code ~/.ce-workbench} in two places.  All of that is now
 * centralised here — callers never construct workspace paths themselves.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Load a CEC entry for a given system key</li>
 *   <li>Save a CEC entry through {@link WorkspaceManager} (no raw path construction)</li>
 *   <li>Check CEC availability by key</li>
 * </ul>
 *
 * <p>This class is intentionally stateless so that one instance can be shared
 * safely across threads (the underlying {@link SystemDataLoader} and
 * {@link WorkspaceManager} are already thread-safe).</p>
 */
public class CECManagementService {

    private static final Logger LOG = LoggingConfig.getLogger(CECManagementService.class);

    private final WorkspaceManager workspaceManager;

    /**
     * Constructs the service.
     *
     * @param workspaceManager workspace path resolver — must already be initialised
     *                         at application startup via {@link WorkspaceManager#WorkspaceManager}
     */
    public CECManagementService(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    /**
     * Loads the CEC data for the given element + structure + phase + model combination.
     *
     * <p>Checks the user workspace first, then falls back to bundled classpath resources,
     * delegating entirely to {@link SystemDataLoader#loadCecData}.</p>
     *
     * @param elements  element string, e.g. {@code "Nb-Ti"}
     * @param structure structure identifier, e.g. {@code "BCC"}
     * @param phase     phase identifier, e.g. {@code "A2"}
     * @param model     model identifier, e.g. {@code "T"}
     * @return {@link Optional} containing the CEC data if found, empty otherwise
     */
    public Optional<SystemDataLoader.CECData> loadCEC(
            String elements, String structure, String phase, String model) {

        LOG.fine("CECManagementService.loadCEC — elements=" + elements
                + " structure=" + structure + " phase=" + phase + " model=" + model);
        return SystemDataLoader.loadCecData(elements, structure, phase, model);
    }

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    /**
     * Validates and saves the given CEC data to the user workspace.
     *
     * <p>The {@code cecData.structure}, {@code cecData.phase}, and
     * {@code cecData.model} fields must be populated before calling this method
     * so that the correct directory key ({@code elements_structure_phase_model})
     * is resolved.  The workspace root path is obtained from the injected
     * {@link WorkspaceManager} — callers must never construct paths themselves.</p>
     *
     * @param cecData the CEC data to persist; must have {@code elements},
     *                {@code structure}, {@code phase}, and {@code model} set
     * @throws IllegalArgumentException if required fields are missing
     */
    public void saveCEC(SystemDataLoader.CECData cecData) {
        validate(cecData);
        LOG.fine("CECManagementService.saveCEC — elements=" + cecData.elements
                + " structure=" + cecData.structure + " phase=" + cecData.phase
                + " model=" + cecData.model);
        SystemDataLoader.saveCecData(cecData, workspaceManager.getRoot());
    }

    // -----------------------------------------------------------------------
    // Availability check
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if a CEC file exists for the given element + model key.
     *
     * @param elements  element string, e.g. {@code "Nb-Ti"}
     * @param structure structure identifier, e.g. {@code "BCC"}
     * @param phase     phase identifier, e.g. {@code "A2"}
     * @param model     model identifier, e.g. {@code "T"}
     * @return {@code true} if the CEC is available (workspace or classpath)
     */
    public boolean isCECAvailable(String elements, String structure, String phase, String model) {
        return SystemDataLoader.cecExists(elements, structure, phase, model);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static void validate(SystemDataLoader.CECData cecData) {
        if (cecData == null) throw new IllegalArgumentException("cecData must not be null");
        if (cecData.elements == null || cecData.elements.isBlank())
            throw new IllegalArgumentException("cecData.elements must be set");
        if (cecData.structure == null || cecData.structure.isBlank())
            throw new IllegalArgumentException("cecData.structure must be set");
        if (cecData.phase == null || cecData.phase.isBlank())
            throw new IllegalArgumentException("cecData.phase must be set");
        if (cecData.model == null || cecData.model.isBlank())
            throw new IllegalArgumentException("cecData.model must be set");
    }
}
