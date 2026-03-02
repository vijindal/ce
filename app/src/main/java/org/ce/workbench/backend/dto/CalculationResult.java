package org.ce.workbench.backend.dto;

import java.util.Optional;

/**
 * Result wrapper for calculation preparation operations.
 * 
 * <p>Encapsulates either a successfully prepared context or an error message,
 * providing a clean way to communicate success/failure without exceptions.</p>
 * 
 * @param <T> the type of context being prepared (e.g., MCSCalculationContext, CVMCalculationContext)
 */
public final class CalculationResult<T> {
    
    private final T context;
    private final String errorMessage;
    private final boolean success;
    
    private CalculationResult(T context, String errorMessage, boolean success) {
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
    public static <T> CalculationResult<T> success(T context) {
        return new CalculationResult<>(context, null, true);
    }
    
    /**
     * Creates a failure result with an error message.
     * 
     * @param errorMessage description of the failure
     * @param <T> the expected context type
     * @return failure result
     */
    public static <T> CalculationResult<T> failure(String errorMessage) {
        return new CalculationResult<>(null, errorMessage, false);
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
            return "CalculationResult{success=true, context=" + context + '}';
        } else {
            return "CalculationResult{success=false, error='" + errorMessage + "'}";
        }
    }
}
