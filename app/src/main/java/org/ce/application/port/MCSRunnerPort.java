package org.ce.application.port;

import org.ce.domain.model.result.EquilibriumState;
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
     * Runs MCS and returns the equilibrium state.
     */
    EquilibriumState run(
            MCSCalculationContext context,
            MCSProgressPort progressPort,
            BooleanSupplier cancellationCheck);
}

