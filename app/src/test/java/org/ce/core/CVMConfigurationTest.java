package org.ce.core;

import org.ce.core.CVMConfiguration;
import org.ce.identification.geometry.Vector3D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CVMConfiguration builder and initialization.
 */
public class CVMConfigurationTest {

    @Test
    public void testBuilderWithBasicConfiguration() throws Exception {
        Vector3D translation = new Vector3D(0, 0, 0);
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                .translationVector(translation)
                .numComponents(2)
                .build();

        assertNotNull(config);
    }

    @Test
    public void testBuilderWithTransformationMatrix() throws Exception {
        Vector3D translation = new Vector3D(1, 0, 0);
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A1-TO.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A1-SG")
                .orderedSymmetryGroup("A2-SG")
                .transformationMatrix(new double[][]{
                        {1.0, 0.0, 0.0},
                        {0.0, 1.0, 0.0},
                        {0.0, 0.0, 1.0}
                })
                .translationVector(translation)
                .numComponents(2)
                .build();

        assertNotNull(config);
    }

    @Test
    public void testBuilderWithTernarySystem() throws Exception {
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/B2-T.txt")
                .orderedClusterFile("cluster/B2-T.txt")
                .disorderedSymmetryGroup("B2-SG")
                .orderedSymmetryGroup("B2-SG")
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(3)
                .build();

        assertNotNull(config);
    }
}
