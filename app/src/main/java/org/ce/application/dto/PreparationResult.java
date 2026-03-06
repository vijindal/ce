package org.ce.application.dto;

import java.util.Optional;

/**
 * Result wrapper for calculation preparation operations.
 * 
 * <p>Encapsulates either a successfully prepared context or an error message,
 * providing a clean way to communicate success/failure without exceptions.</p>
 * 
 * <p>Renamed from CalculationResult to avoid name collision with the domain-level
 * {@code org.ce.domain.model.result.CalculationResult} sealed interface.</p>
 * 
 * @param <T> the type of context being prepared (e.g., MCSCalculationContext, CVMPhaseModel)
 */
public final class PreparationResult<T> {
    
    private final T context;
    private final String errorMessage;
    private final boolean success;
    
    private PreparationResult(T context, String errorMessage, boolean success) {
        this.context = context;
        this.errorMessage = errorMessage;
        this.success = success;
    }
    
    /**
     * Creates a successful result with the prepared context.
     * 
     * @param context the prepared calculation context
     * @param <T> the context type
     * @return successful result
     */
    public static <T> PreparationResult<T> success(T context) {
        return new PreparationResult<>(context, null, true);
    }
    
    /**
     * Creates a failure result with an error message.
     * 
     * @param errorMessage description of the failure
     * @param <T> the expected context type
     * @return failure result
     */
    public static <T> PreparationResult<T> failure(String errorMessage) {
        return new PreparationResult<>(null, errorMessage, false);
    }
    
    /**
     * Returns true if the operation succeeded.
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Returns true if the operation failed.
     */
    public boolean isFailure() {
        return !success;
    }
    
    /**
     * Returns the prepared context, if successful.
     * 
     * @return Optional containing the context, or empty if failed
     */
    public Optional<T> getContext() {
        return Optional.ofNullable(context);
    }
    
    /**
     * Returns the error message, if failed.
     * 
     * @return Optional containing the error message, or empty if successful
     */
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }
    
    /**
     * Returns the context directly. Use only when isSuccess() is true.
     * 
     * @return the context
     * @throws IllegalStateException if the result is a failure
     */
    public T getContextOrThrow() {
        if (!success) {
            throw new IllegalStateException("Cannot get context from failed result: " + errorMessage);
        }
        return context;
    }
    
    @Override
    public String toString() {
        if (success) {
            return "PreparationResult{success=true, context=" + context + '}';
        } else {
            return "PreparationResult{success=false, error='" + errorMessage + "'}";
        }
    }
}

