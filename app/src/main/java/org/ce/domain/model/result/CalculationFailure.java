package org.ce.domain.model.result;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a failed calculation with error details.
 *
 * <p>Used when a calculation cannot complete due to:</p>
 * <ul>
 *   <li>Missing or invalid input data</li>
 *   <li>Solver convergence failure</li>
 *   <li>System errors during execution</li>
 * </ul>
 *
 * @param errorMessage  human-readable description of the failure
 * @param errorCode     optional machine-readable error code
 * @param cause         optional underlying exception
 * @param timestamp     when this failure occurred
 *
 * @since 2.0
 */
public record CalculationFailure(
        String errorMessage,
        String errorCode,
        Throwable cause,
        Instant timestamp
) implements CalculationResult {

    /**
     * Canonical constructor with validation.
     */
    public CalculationFailure {
        Objects.requireNonNull(errorMessage, "errorMessage");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    /**
     * Creates a failure with just an error message.
     */
    public static CalculationFailure of(String message) {
        return new CalculationFailure(message, null, null, Instant.now());
    }

    /**
     * Creates a failure with a message and error code.
     */
    public static CalculationFailure of(String message, String errorCode) {
        return new CalculationFailure(message, errorCode, null, Instant.now());
    }

    /**
     * Creates a failure from an exception.
     */
    public static CalculationFailure fromException(Throwable cause) {
        return new CalculationFailure(
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName(),
                cause.getClass().getSimpleName(),
                cause,
                Instant.now()
        );
    }

    /**
     * Creates a failure from an exception with additional context.
     */
    public static CalculationFailure fromException(String context, Throwable cause) {
        return new CalculationFailure(
                context + ": " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()),
                cause.getClass().getSimpleName(),
                cause,
                Instant.now()
        );
    }

    @Override
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("CALCULATION FAILED\n");
        sb.append("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•\n");
        sb.append("  Error: ").append(errorMessage).append("\n");
        if (errorCode != null) {
            sb.append("  Code:  ").append(errorCode).append("\n");
        }
        if (cause != null) {
            sb.append("  Cause: ").append(cause.getClass().getName()).append("\n");
        }
        sb.append("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
        return sb.toString();
    }
}

