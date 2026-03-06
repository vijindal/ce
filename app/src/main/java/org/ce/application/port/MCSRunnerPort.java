package org.ce.application.port;

import org.ce.domain.model.result.MCSResult;
import org.ce.infrastructure.context.MCSCalculationContext;

import java.util.function.BooleanSupplier;

/**
 * Application port for MCS execution.
 *
 * <p>Implemented by infrastructure adapters that bridge to the concrete
 * Monte Carlo engine.</p>
 */
public interface MCSRunnerPort {

    /**
     * Runs MCS and returns the mapped domain result.
     */
    MCSResult run(
            MCSCalculationContext context,
            MCSProgressPort progressPort,
            BooleanSupplier cancellationCheck);
}

