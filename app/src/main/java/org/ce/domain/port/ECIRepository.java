package org.ce.domain.port;

import java.util.Optional;

/**
 * Repository interface for accessing Effective Cluster Interactions (ECI) data.
 *
 * <p>ECI values define the energy contributions of each cluster type and are
 * essential for both CVM free-energy calculations and MCS energy computations.</p>
 *
 * <h2>Data Sources</h2>
 * <p>Implementations may load ECI from:</p>
 * <ul>
 *   <li>CEC database files ({@code /data/systems/{elements}/cec.json})</li>
 *   <li>User-provided input</li>
 *   <li>First-principles calculation results</li>
 * </ul>
 *
 * <h2>Temperature Dependence</h2>
 * <p>Some CEC terms are temperature-dependent (e.g., {@code a + b*T}).
 * The {@link #loadAt} method evaluates these at a specific temperature.</p>
 *
 * @since 2.0
 */
public interface ECIRepository {

    /**
     * Result of an ECI lookup operation.
     */
    sealed interface LoadResult permits LoadResult.Success, LoadResult.NotFound, LoadResult.LengthMismatch {
        
        /**
         * Successful load with ECI values.
         */
        record Success(double[] eci, int length, boolean temperatureEvaluated, String message) 
                implements LoadResult {}

        /**
         * No ECI data found for the given key.
         */
        record NotFound(String key, int requiredLength, String message) 
                implements LoadResult {}

        /**
         * ECI data found but length doesn't match cluster count.
         */
        record LengthMismatch(String key, int foundLength, int requiredLength, String message) 
                implements LoadResult {}
    }

    /**
     * Loads ECI values for the given system parameters, evaluating any
     * temperature-dependent terms at the specified temperature.
     *
     * @param elements       element string, e.g., "Nb-Ti"
     * @param structure      crystal structure, e.g., "BCC"
     * @param phase          phase designator, e.g., "A2"
     * @param model          approximation model, e.g., "T"
     * @param temperature    temperature in Kelvin for T-dependent evaluation
     * @param requiredLength expected ECI array length (must match tc)
     * @return load result (success, not found, or length mismatch)
     */
    LoadResult loadAt(String elements, String structure, String phase, 
                      String model, double temperature, int requiredLength);

    /**
     * Loads ECI values without temperature evaluation (for T-independent data).
     *
     * @param elements       element string
     * @param structure      crystal structure
     * @param phase          phase designator
     * @param model          approximation model
     * @param requiredLength expected ECI array length
     * @return load result
     */
    LoadResult load(String elements, String structure, String phase, 
                    String model, int requiredLength);

    /**
     * Loads ECI values using legacy element-only key (deprecated usage).
     *
     * @param elements       element string
     * @param requiredLength expected ECI array length
     * @return load result
     * @deprecated Prefer the model-qualified methods for explicit system identification
     */
    @Deprecated
    LoadResult loadLegacy(String elements, int requiredLength);
}
