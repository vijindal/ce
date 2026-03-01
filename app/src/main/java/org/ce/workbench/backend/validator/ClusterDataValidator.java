package org.ce.workbench.backend.validator;

import org.ce.workbench.backend.data.AllClusterData;
import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.identification.cf.CFIdentificationResult;
import org.ce.cvm.CMatrixResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates cluster data availability and completeness for CVM calculations.
 *
 * <p>Performs checks at multiple levels:</p>
 * <ul>
 *   <li><b>Structural</b>: Are all Stage 1-3 results present?</li>
 *   <li><b>Dimensional</b>: Do array sizes match expected relationships?</li>
 *   <li><b>Consistency</b>: Do values from different stages agree?</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * AllClusterData data = ...;
 * ValidationResult result = ClusterDataValidator.validate(data);
 * if (!result.isValid()) {
 *     System.out.println(result.getDiagnostics());
 * }
 * }</pre>
 */
public class ClusterDataValidator {

    // =========================================================================
    // Main validation method
    // =========================================================================

    /**
     * Performs comprehensive validation of cluster data.
     *
     * @param data cluster data to validate
     * @return validation result with diagnostics
     */
    public static ValidationResult validate(AllClusterData data) {
        ValidationResult result = new ValidationResult();

        if (data == null) {
            result.addError("AllClusterData is null");
            return result;
        }

        // Check structural completeness
        validateStructure(data, result);

        // If structure is OK, validate dimensions
        if (result.isValid()) {
            validateDimensions(data, result);
        }

        // Check consistency across stages
        if (result.isValid()) {
            validateConsistency(data, result);
        }

        return result;
    }

    // =========================================================================
    // Stage-specific validators
    // =========================================================================

    /**
     * Checks that all required stages are present and non-null.
     */
    private static void validateStructure(AllClusterData data, ValidationResult result) {
        if (!data.isStage1Complete()) {
            result.addError("Stage 1 (Cluster Identification) not computed");
            return;
        }

        if (!data.isStage2Complete()) {
            result.addError("Stage 2 (CF Identification) not computed");
            return;
        }

        if (!data.isStage3Complete()) {
            result.addError("Stage 3 (C-Matrix Construction) not computed");
            return;
        }
    }

    /**
     * Validates array dimensions and sizes.
     */
    private static void validateDimensions(AllClusterData data, ValidationResult result) {
        ClusterIdentificationResult stage1 = data.getStage1();
        CFIdentificationResult stage2 = data.getStage2();
        CMatrixResult stage3 = data.getStage3();

        int tcdis = stage1.getTcdis();
        int tcf = stage2.getTcf();

        // Validate Stage 1 dimensions
        if (tcdis <= 0) {
            result.addError("Stage 1: tcdis <= 0 (" + tcdis + ")");
        }

        // Validate Stage 2 dimensions
        if (tcf <= 0) {
            result.addError("Stage 2: tcf <= 0 (" + tcf + ")");
        }

        // Validate lcf dimensions
        int[][] lcf = stage2.getLcf();
        if (lcf == null || lcf.length == 0) {
            result.addError("Stage 2: lcf is null or empty");
        } else {
            if (lcf.length != tcdis) {
                result.addWarning(
                    String.format("Stage 2: lcf rows (%d) != tcdis (%d)", lcf.length, tcdis));
            }
            int lcf_sum = 0;
            for (int[] row : lcf) {
                if (row == null || row.length == 0) {
                    result.addWarning("Stage 2: lcf has empty row");
                } else {
                    for (int v : row) {
                        lcf_sum += v;
                    }
                }
            }
            if (lcf_sum != tcf) {
                result.addWarning(
                    String.format("Stage 2: sum(lcf) (%d) != tcf (%d)", lcf_sum, tcf));
            }
        }

        // Validate Stage 3 dimensions
        int[][] lcv = stage3.getLcv();
        if (lcv == null || lcv.length == 0) {
            result.addError("Stage 3: lcv is null or empty");
        } else {
            if (lcv.length != tcdis) {
                result.addWarning(
                    String.format("Stage 3: lcv length (%d) != tcdis (%d)", lcv.length, tcdis));
            }
        }

        Object wcv = stage3.getWcv();
        if (wcv == null) {
            result.addError("Stage 3: wcv is null");
        }

        Object cmat = stage3.getCmat();
        if (cmat == null) {
            result.addError("Stage 3: cmat is null");
        }
    }

    /**
     * Checks consistency across stages.
     */
    private static void validateConsistency(AllClusterData data, ValidationResult result) {
        ClusterIdentificationResult stage1 = data.getStage1();
        CFIdentificationResult stage2 = data.getStage2();

        // Verify Kikuchi-Baker coefficients present
        double[] kb = stage1.getKbCoefficients();
        if (kb == null || kb.length == 0) {
            result.addWarning("Stage 1: Kikuchi-Baker coefficients missing");
        } else {
            if (kb.length != stage1.getTcdis()) {
                result.addWarning(
                    String.format("Stage 1: kb length (%d) != tcdis (%d)",
                        kb.length, stage1.getTcdis()));
            }
        }

        // Verify multiplicity data
        // (specifics depend on ClusterIdentificationResult structure)

        // Verify grouped CF data
        if (stage2.getGroupedCFData() == null) {
            result.addWarning("Stage 2: Grouped CF data is null");
        }
    }

    // =========================================================================
    // Convenience methods
    // =========================================================================

    /**
     * Quick check: is data ready for CVM calculations?
     *
     * @param data cluster data to check
     * @return true if complete and valid
     */
    public static boolean isReadyForCVM(AllClusterData data) {
        if (data == null || !data.isComplete()) {
            return false;
        }
        ValidationResult result = validate(data);
        return result.isValid();
    }

    /**
     * Get human-readable diagnostics for data issues.
     *
     * @param data cluster data to diagnose
     * @return diagnostic message string
     */
    public static String diagnose(AllClusterData data) {
        if (data == null) {
            return "ERROR: AllClusterData is null";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Cluster Data Diagnostic Report\n");
        sb.append("═══════════════════════════════\n");
        sb.append(data.getCompletionStatus()).append("\n\n");

        ValidationResult result = validate(data);

        if (result.isValid()) {
            sb.append("✓ All validations passed\n");
            sb.append("  Data is ready for CVM calculations.\n");
        } else {
            sb.append("✗ Validation failed\n\n");

            if (!result.getErrors().isEmpty()) {
                sb.append("ERRORS:\n");
                for (String error : result.getErrors()) {
                    sb.append("  • ").append(error).append("\n");
                }
                sb.append("\n");
            }

            if (!result.getWarnings().isEmpty()) {
                sb.append("WARNINGS:\n");
                for (String warning : result.getWarnings()) {
                    sb.append("  ⚠ ").append(warning).append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    // =========================================================================
    // Inner class: ValidationResult
    // =========================================================================

    /**
     * Encapsulates validation results with error and warning messages.
     */
    public static class ValidationResult {

        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String message) {
            errors.add(message);
        }

        public void addWarning(String message) {
            warnings.add(message);
        }

        /**
         * Returns true if there are no errors (warnings are OK).
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }

        /**
         * Returns all messages (errors and warnings).
         */
        public List<String> getAllMessages() {
            List<String> all = new ArrayList<>(errors);
            all.addAll(warnings);
            return all;
        }

        /**
         * Human-readable summary.
         */
        public String getSummary() {
            if (isValid() && warnings.isEmpty()) {
                return "✓ Valid - All checks passed";
            } else if (isValid()) {
                return "✓ Valid - " + warnings.size() + " warning(s)";
            } else {
                return "✗ Invalid - " + errors.size() + " error(s)";
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getSummary()).append("\n");

            if (!errors.isEmpty()) {
                sb.append("Errors: ").append(errors.size()).append("\n");
                for (String error : errors) {
                    sb.append("  - ").append(error).append("\n");
                }
            }

            if (!warnings.isEmpty()) {
                sb.append("Warnings: ").append(warnings.size()).append("\n");
                for (String warning : warnings) {
                    sb.append("  - ").append(warning).append("\n");
                }
            }

            return sb.toString();
        }
    }
}
