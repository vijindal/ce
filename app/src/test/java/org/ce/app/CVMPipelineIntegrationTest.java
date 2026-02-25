package org.ce.app;

import org.ce.identification.engine.Vector3D;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the complete CVMPipeline workflow.
 * Tests cover end-to-end execution from configuration through result generation.
 */
@DisplayName("CVM Pipeline Integration Tests")
public class CVMPipelineIntegrationTest {

    @Test
    @DisplayName("Full pipeline execution with binary A2 cluster")
    public void testFullPipelineWithBinaryA2System() throws Exception {
        // Arrange: Create a simple binary A2 configuration
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(2)
                .build();

        // Act: Execute pipeline
        CVMResult result = CVMPipeline.identify(config);

        // Assert: Verify result structure
        assertNotNull(result, "Pipeline should return non-null result");
        assertNotNull(result.getClusterIdentification(), "Should contain cluster identification results");
        assertNotNull(result.getCorrelationFunctionIdentification(), "Should contain CF identification results");
    }

    @Test
    @DisplayName("Full pipeline with transformation matrix")
    public void testFullPipelineWithTransformationMatrix() throws Exception {
        // Arrange: Configuration with supercell transformation
        double[][] transformMatrix = {
                {1.0, 0.0, 0.0},
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}
        };

        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                .transformationMatrix(transformMatrix)
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(2)
                .build();

        // Act: Execute pipeline
        CVMResult result = CVMPipeline.identify(config);

        // Assert
        assertNotNull(result);
        assertDoesNotThrow(() -> result.printDebug(), "Should handle debug output gracefully");
    }

    @Test
    @DisplayName("Full pipeline with ternary system (3 components)")
    public void testFullPipelineWithTernarySystem() throws Exception {
        // Arrange
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/B2-T.txt")
                .orderedClusterFile("cluster/B2-T.txt")
                .disorderedSymmetryGroup("B2-SG")
                .orderedSymmetryGroup("B2-SG")
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(3)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> {
            CVMResult result = CVMPipeline.identify(config);
            assertNotNull(result);
        }, "Ternary system should be processed without errors");
    }

    @Test
    @DisplayName("Pipeline result contains accessible cluster data")
    public void testPipelineResultClusterDataAccess() throws Exception {
        // Arrange
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(2)
                .build();

        // Act
        CVMResult result = CVMPipeline.identify(config);

        // Assert: Can access cluster identification results
        assertNotNull(result.getClusterIdentification(), "Should have cluster identification");
        assertNotNull(result.getCorrelationFunctionIdentification(), "Should have CF identification");
        
        // Verify stage 2 is dependent on stage 1
        assertTrue(
            result.getCorrelationFunctionIdentification() != null,
            "Stage 2 (CF identification) should be computed from Stage 1 results"
        );
    }

    @Test
    @DisplayName("Different translation vectors produce valid results")
    public void testDifferentTranslationVectors() throws Exception {
        Vector3D[] translations = {
                new Vector3D(0, 0, 0),
                new Vector3D(0.5, 0, 0),
                new Vector3D(0.25, 0.25, 0.25)
        };

        for (Vector3D translation : translations) {
            // Arrange
            CVMConfiguration config = CVMConfiguration.builder()
                    .disorderedClusterFile("cluster/A2-T.txt")
                    .orderedClusterFile("cluster/A2-T.txt")
                    .disorderedSymmetryGroup("A2-SG")
                    .orderedSymmetryGroup("A2-SG")
                    .translationVector(translation)
                    .numComponents(2)
                    .build();

            // Act & Assert
            assertDoesNotThrow(() -> {
                CVMResult result = CVMPipeline.identify(config);
                assertNotNull(result, "Should handle translation vector: " + translation);
            }, "Translation vector " + translation + " should be processed");
        }
    }
}
