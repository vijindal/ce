package org.ce.infrastructure.data;

import org.ce.domain.model.data.AllClusterData;

import java.util.logging.Logger;

/**
 * Utility for mapping CEC arrays to CVM-expected ECI arrays.
 *
 * CVM uses ncf-length ECI arrays (non-point cluster functions only).
 * CEC database may store ncf, ncf+1 (with point term), or ncf+2 (with point and empty terms).
 * This utility handles the conversion.
 */
public class ECIMapper {

    private static final Logger LOG = Logger.getLogger(ECIMapper.class.getName());

    /**
     * Map a raw CEC array to CVM ECI array (ncf length).
     *
     * Supports three input formats:
     * - cecRaw.length == ncf: return as-is
     * - cecRaw.length == ncf + 1: drop trailing point-cluster term
     * - cecRaw.length == ncf + 2: drop trailing point and empty-cluster terms
     *
     * @param cecRaw the raw CEC array from database
     * @param allData cluster data containing ncf information
     * @return ECI array of length ncf for CVM
     * @throws IllegalArgumentException if CEC length is not supported
     */
    public static double[] mapCECToCvmECI(double[] cecRaw, AllClusterData allData) {
        int ncf = allData.getStage2().getNcf();

        if (cecRaw.length == ncf) {
            LOG.fine("CEC length matches CVM ncf=" + ncf + " (no mapping needed)");
            return cecRaw.clone();
        }

        if (cecRaw.length == ncf + 1) {
            LOG.fine("Mapping CEC (" + cecRaw.length + ") -> CVM ECI (" + ncf
                + ") by dropping trailing point-cluster term");
            double[] mapped = new double[ncf];
            System.arraycopy(cecRaw, 0, mapped, 0, ncf);
            return mapped;
        }

        if (cecRaw.length == ncf + 2) {
            LOG.fine("Mapping CEC (" + cecRaw.length + ") -> CVM ECI (" + ncf
                + ") by dropping trailing point and empty-cluster terms");
            double[] mapped = new double[ncf];
            System.arraycopy(cecRaw, 0, mapped, 0, ncf);
            return mapped;
        }

        throw new IllegalArgumentException(
            "Unsupported CEC length " + cecRaw.length +
            " for CVM ncf=" + ncf +
            ". Expected one of {" + ncf + ", " + (ncf + 1) + ", " + (ncf + 2) + "}.");
    }

    /**
     * Expand an ncf-length ECI array to tc (total cluster count) length for MCS.
     *
     * MCS needs all cluster types including point (index tc-2) and empty (index tc-1).
     * The CEC database stores only ncf (non-point) terms. This utility expands to tc
     * by zero-padding the point and empty cluster slots.
     *
     * @param nciEci the ncf-length ECI array from database
     * @param tc total cluster count from AllClusterData Stage 1
     * @return expanded ECI array of length tc with zeros for point/empty
     */
    public static double[] expandECIForMCS(double[] nciEci, int tc) {
        if (nciEci.length == tc) {
            // Already expanded (e.g., from a previous call)
            return nciEci.clone();
        }

        int ncf = nciEci.length;
        if (ncf > tc) {
            throw new IllegalArgumentException(
                "ECI array length (" + ncf + ") exceeds total cluster count (" + tc + ")");
        }

        double[] expanded = new double[tc];
        System.arraycopy(nciEci, 0, expanded, 0, ncf);
        // Remaining elements (point and empty clusters) are zero-padded by default

        LOG.fine("Expanded ECI from ncf=" + ncf + " to tc=" + tc
            + " (zero-padded " + (tc - ncf) + " point/empty cluster slots)");

        return expanded;
    }

    private ECIMapper() {
        // Utility class, no instances
    }
}
