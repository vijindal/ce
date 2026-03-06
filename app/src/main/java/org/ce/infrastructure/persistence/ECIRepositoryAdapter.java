package org.ce.infrastructure.persistence;

import org.ce.domain.port.ECIRepository;
import org.ce.infrastructure.eci.ECILoader;

/**
 * Adapter implementing {@link ECIRepository} by delegating to
 * the static {@link ECILoader}.
 *
 * <p>This adapter provides an instance-based, injectable interface
 * to ECI data access, converting between the legacy {@code DBLoadResult}
 * and the new sealed {@code LoadResult} hierarchy.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ECIRepository repo = new ECIRepositoryAdapter();
 * LoadResult result = repo.loadAt("Nb-Ti", "BCC", "A2", "T", 800.0, 5);
 * switch (result) {
 *     case LoadResult.Success s -> useECI(s.eci());
 *     case LoadResult.NotFound n -> handleMissing(n.message());
 *     case LoadResult.LengthMismatch m -> handleMismatch(m.message());
 * }
 * }</pre>
 *
 * @since 2.0
 */
public class ECIRepositoryAdapter implements ECIRepository {

    /**
     * Creates a new adapter instance.
     */
    public ECIRepositoryAdapter() {
        // Stateless adapter
    }

    @Override
    public LoadResult loadAt(String elements, String structure, String phase,
                             String model, double temperature, int requiredLength) {
        ECILoader.DBLoadResult dbResult = ECILoader.loadECIFromDatabase(
                elements, structure, phase, model, temperature, requiredLength);
        return convertResult(dbResult);
    }

    @Override
    public LoadResult load(String elements, String structure, String phase,
                           String model, int requiredLength) {
        ECILoader.DBLoadResult dbResult = ECILoader.loadECIFromDatabase(
                elements, structure, phase, model, requiredLength);
        return convertResult(dbResult);
    }

    @Override
    @Deprecated
    public LoadResult loadLegacy(String elements, int requiredLength) {
        ECILoader.DBLoadResult dbResult = ECILoader.loadECIFromDatabase(
                elements, requiredLength);
        return convertResult(dbResult);
    }

    /**
     * Converts legacy DBLoadResult to the new sealed LoadResult hierarchy.
     */
    private LoadResult convertResult(ECILoader.DBLoadResult dbResult) {
        return switch (dbResult.status) {
            case OK -> new LoadResult.Success(
                    dbResult.eci,
                    dbResult.foundLen,
                    dbResult.temperatureEvaluated,
                    dbResult.message
            );
            case NOT_FOUND -> new LoadResult.NotFound(
                    "unknown", // key not available in DBLoadResult
                    dbResult.neededLen,
                    dbResult.message
            );
            case LENGTH_MISMATCH -> new LoadResult.LengthMismatch(
                    "unknown",
                    dbResult.foundLen,
                    dbResult.neededLen,
                    dbResult.message
            );
        };
    }
}

