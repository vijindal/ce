package org.ce.core;

import org.ce.core.CVMConfiguration;
import org.ce.identification.geometry.Vector3D;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boundary condition and edge case tests for CVMConfiguration.
 */
@DisplayName("CVM Boundary & Edge Cases")
public class CVMConfigurationErrorTest {

    @Test
    @DisplayName("Configuration with zero components fails")
    public void testZeroComponents() {
        assertThrows(
            Exception.class,
            () -> CVMConfiguration.builder()
                    .disorderedClusterFile("cluster/A2-T.txt")
                    .orderedClusterFile("cluster/A2-T.txt")
                    .disorderedSymmetryGroup("A2-SG")
                    .orderedSymmetryGroup("A2-SG")
                    .translationVector(new Vector3D(0, 0, 0))
                    .numComponents(0)
                    .build(),
            "Should reject zero components"
        );
    }

    @Test
    @DisplayName("Configuration with negative components fails")
    public void testNegativeComponents() {
        assertThrows(
            Exception.class,
            () -> CVMConfiguration.builder()
                    .disorderedClusterFile("cluster/A2-T.txt")
                    .orderedClusterFile("cluster/A2-T.txt")
                    .disorderedSymmetryGroup("A2-SG")
                    .orderedSymmetryGroup("A2-SG")
                    .translationVector(new Vector3D(0, 0, 0))
                    .numComponents(-1)
                    .build(),
            "Should reject negative component count"
        );
    }

    @Test
    @DisplayName("Large multi-component system (stress test)")
    public void testLargeComponentCount() {
        assertDoesNotThrow(() -> {
            CVMConfiguration config = CVMConfiguration.builder()
                    .disorderedClusterFile("cluster/B2-T.txt")
                    .orderedClusterFile("cluster/B2-T.txt")
                    .disorderedSymmetryGroup("B2-SG")
                    .orderedSymmetryGroup("B2-SG")
                    .translationVector(new Vector3D(0, 0, 0))
                    .numComponents(5)
                    .build();
            assertNotNull(config);
        }, "Configuration should handle 5-component systems gracefully");
    }

    @Test
    @DisplayName("Identity transformation matrix is accepted")
    public void testIdentityTransformationMatrix() throws Exception {
        double[][] identity = {
                {1.0, 0.0, 0.0},
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}
        };

        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                .transformationMatrix(identity)
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(2)
                .build();

        assertNotNull(config, "Identity matrix config should be valid");
    }

    @Test
    @DisplayName("Extreme translation vectors are handled")
    public void testExtremeTranslationVectors() throws Exception {
        Vector3D[] vectors = {
                new Vector3D(1e-10, 1e-10, 1e-10),
                new Vector3D(10.0, 10.0, 10.0),
                new Vector3D(-5.0, -5.0, -5.0)
        };

        for (Vector3D vec : vectors) {
            CVMConfiguration config = CVMConfiguration.builder()
                    .disorderedClusterFile("cluster/A2-T.txt")
                    .orderedClusterFile("cluster/A2-T.txt")
                    .disorderedSymmetryGroup("A2-SG")
                    .orderedSymmetryGroup("A2-SG")
                    .translationVector(vec)
                    .numComponents(2)
                    .build();

            assertNotNull(config, "Should handle vector: " + vec);
        }
    }
}
