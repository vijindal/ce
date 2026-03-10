package org.ce.domain.cvm;

import org.ce.application.service.CECAssemblyService;
import org.ce.domain.model.data.AllClusterData;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.registry.KeyUtils;
import org.ce.domain.system.SystemIdentity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CEC Assembly workflow (Phase 6).
 *
 * Tests the complete pipeline:
 * 1. Load AllClusterData for target system
 * 2. Classify CFs by minimum order
 * 3. Load and transform subsystem CECs
 * 4. Assemble final CEC array
 * 5. Persist to database
 */
@DisplayName("CEC Assembly Integration Tests (Phase 6)")
public class CECAssemblyIntegrationTest {

    private static final String SYSTEM_ID_NB_TI = "Nb-Ti_BCC_A2_T";
    private static final String STRUCTURE = "BCC";
    private static final String PHASE = "A2";
    private static final String MODEL = "T";

    @BeforeAll
    static void setupTest() throws Exception {
        // Verify cluster data exists in cache
        Optional<AllClusterData> cached = AllClusterDataCache.load("BCC_A2_T_bin");
        if (cached.isEmpty() || !cached.get().isComplete()) {
            throw new RuntimeException("Test requires BCC_A2_T_bin cluster data in cache");
        }
    }

    /**
     * Test 1: Load AllClusterData for target system
     */
    @Test
    @DisplayName("T1: Load AllClusterData for Nb-Ti system")
    void testLoadAllClusterData() throws Exception {
        String clusterKey = KeyUtils.clusterKey(createNbTiSystem());
        Optional<AllClusterData> data = AllClusterDataCache.load(clusterKey);

        assertTrue(data.isPresent(), "AllClusterData should be present for BCC_A2_T_bin");
        assertTrue(data.get().isComplete(), "AllClusterData should be complete");

        AllClusterData allData = data.get();
        assertTrue(allData.getStage2().getTcf() > 0, "tcf should be positive");
        assertTrue(allData.getStage3().getCfBasisIndices() != null, "cfBasisIndices should be present");
    }

    /**
     * Test 2: Classify CFs by minimum order
     */
    @Test
    @DisplayName("T2: Classify CFs by minimum order of appearance")
    void testClassifyCFsByOrder() throws Exception {
        Optional<AllClusterData> data = AllClusterDataCache.load("BCC_A2_T_bin");
        assertTrue(data.isPresent());

        AllClusterData allData = data.get();
        int[] cfOrderMap = CECAssemblyService.classifyCFsByOrder(allData);

        // All CFs should have a minimum order >= 2
        for (int order : cfOrderMap) {
            assertTrue(order >= 2, "Minimum order should be >= 2 (binary)");
            assertTrue(order <= allData.getNumComponents(),
                "Minimum order should be <= K=" + allData.getNumComponents());
        }

        // At least some CFs should be binary-compatible (order = 2)
        boolean hasBinaryCompatible = false;
        for (int order : cfOrderMap) {
            if (order == 2) {
                hasBinaryCompatible = true;
                break;
            }
        }
        assertTrue(hasBinaryCompatible, "Should have at least some binary-compatible CFs");

        System.out.println("✓ CF classification: " + cfOrderMap.length + " CFs classified");
    }

    /**
     * Test 3: Generate subsystems by order
     */
    @Test
    @DisplayName("T3: Generate subsystems by order (C(K,m) combinations)")
    void testSubsystemsByOrder() throws Exception {
        SystemIdentity nbTi = createNbTiSystem();
        List<String> components = nbTi.getComponents();

        Map<Integer, List<List<String>>> subsystemsByOrder =
            CECAssemblyService.subsystemsByOrder(components);

        // K=2: no subsystems (only M=2 to K-1, so 2 to 1)
        assertTrue(subsystemsByOrder.isEmpty(),
            "K=2 system should have no subsystem orders (M ranges from 2 to K-1=1)");

        System.out.println("✓ Subsystems generated: " + subsystemsByOrder.size() + " order levels");
    }

    /**
     * Test 4: Load subsystem CEC data
     */
    @Test
    @DisplayName("T4: Load binary subsystem CEC data")
    void testLoadSubsystemCEC() throws Exception {
        String elementKey = CECAssemblyService.toElementString(List.of("Nb", "Ti"));
        Optional<SystemDataLoader.CECData> cec = SystemDataLoader.loadCecData(
            elementKey, STRUCTURE, PHASE, MODEL);

        assertTrue(cec.isPresent(), "Nb-Ti CEC should exist in database");

        SystemDataLoader.CECData cecData = cec.get();
        assertTrue(cecData.elements != null && !cecData.elements.isEmpty(),
            "CEC should have elements field");
        assertTrue(cecData.cecValues != null || cecData.cecTerms != null,
            "CEC should have either cecValues or cecTerms");

        System.out.println("✓ CEC loaded: " + elementKey + ", elements=" + cecData.elements);
    }

    /**
     * Test 5: Extract ECI values (test helper method)
     */
    @Test
    @DisplayName("T5: Extract ECI values from CEC data (both formats)")
    void testExtractECIValues() throws Exception {
        String elementKey = CECAssemblyService.toElementString(List.of("Nb", "Ti"));
        Optional<SystemDataLoader.CECData> cec = SystemDataLoader.loadCecData(
            elementKey, STRUCTURE, PHASE, MODEL);

        assertTrue(cec.isPresent());
        SystemDataLoader.CECData cecData = cec.get();

        // Test extraction (mimics CECDatabaseDialog.extractECIValues())
        double[] values;
        if (cecData.cecTerms != null) {
            values = new double[cecData.cecTerms.length];
            for (int i = 0; i < cecData.cecTerms.length; i++) {
                values[i] = cecData.cecTerms[i].a;
            }
        } else if (cecData.cecValues != null) {
            values = cecData.cecValues.clone();
        } else {
            values = new double[0];
        }

        assertTrue(values.length > 0, "Should extract ECI values");
        System.out.println("✓ Extracted " + values.length + " ECI values");
    }

    /**
     * Test 6: Transform CEC to target basis
     */
    @Test
    @DisplayName("T6: Transform subsystem CEC to target basis (Chebyshev scaling)")
    void testTransformToTarget() throws Exception {
        Optional<AllClusterData> data = AllClusterDataCache.load("BCC_A2_T_bin");
        assertTrue(data.isPresent());
        AllClusterData targetData = data.get();

        int K = targetData.getNumComponents();
        int tcf = targetData.getStage2().getTcf();
        int[] cfOrderMap = CECAssemblyService.classifyCFsByOrder(targetData);

        // Create mock source ECIs (all 1.0 for simplicity)
        double[] sourceECIs = new double[10];
        for (int i = 0; i < sourceECIs.length; i++) {
            sourceECIs[i] = 1.0;
        }

        double[] transformed = CECAssemblyService.transformToTarget(
            sourceECIs, 2, K, cfOrderMap, targetData);

        assertEquals(tcf, transformed.length, "Transformed array should match target tcf");

        // For K=2→K=2, scaling factor = 1.0, so transformed values ≈ source values
        if (K == 2) {
            assertTrue(transformed[0] > 0, "First CF should have positive contribution for K=2");
        }

        System.out.println("✓ Transformation successful: " + transformed.length + " CFs transformed");
    }

    /**
     * Test 7: Assemble final CEC (combining derived + pure-K)
     */
    @Test
    @DisplayName("T7: Assemble final CEC array from derived + pure-K contributions")
    void testAssembleCEC() throws Exception {
        Optional<AllClusterData> data = AllClusterDataCache.load("BCC_A2_T_bin");
        assertTrue(data.isPresent());
        AllClusterData targetData = data.get();

        int K = targetData.getNumComponents();
        int tcf = targetData.getStage2().getTcf();
        int[] cfOrderMap = CECAssemblyService.classifyCFsByOrder(targetData);

        // Mock transformed contributions
        Map<Integer, double[]> transformedByOrder = new java.util.TreeMap<>();
        double[] mockContrib = new double[tcf];
        for (int i = 0; i < tcf; i++) {
            mockContrib[i] = 10.0; // arbitrary value
        }
        transformedByOrder.put(2, mockContrib);

        // Mock pure-K values
        double[] pureECIs = new double[tcf];
        for (int i = 0; i < cfOrderMap.length; i++) {
            if (cfOrderMap[i] == K) {
                pureECIs[i] = 20.0; // arbitrary value for pure-K CFs
            }
        }

        double[] assembled = CECAssemblyService.assemble(
            transformedByOrder, pureECIs, cfOrderMap, targetData);

        assertEquals(tcf, assembled.length, "Assembled array should match tcf");
        assertTrue(assembled[0] != 0.0, "At least first CF should be non-zero");

        System.out.println("✓ Assembly successful: " + assembled.length + " CFs in final array");
    }

    /**
     * Test 8: Build CECData for persistence (structure validation)
     * Note: File I/O persistence tested in Phase 6 UI integration tests
     */
    @Test
    @DisplayName("T8: Build CECData for persistence")
    void testSaveAssembledCEC() throws Exception {
        Optional<AllClusterData> data = AllClusterDataCache.load("BCC_A2_T_bin");
        assertTrue(data.isPresent());
        AllClusterData targetData = data.get();

        int tcf = targetData.getStage2().getTcf();

        // Create test assembled ECI array
        double[] assembled = new double[tcf];
        for (int i = 0; i < tcf; i++) {
            assembled[i] = 25.0 + i * 0.1; // arbitrary values
        }

        // Build CECData object (structure for persistence)
        SystemDataLoader.CECData cecData = new SystemDataLoader.CECData();
        cecData.elements = "Nb-Ti";
        cecData.cecValues = assembled;
        cecData.cecUnits = "J/mol";
        cecData.reference = SYSTEM_ID_NB_TI;
        cecData.tc = tcf;

        // Verify CECData is properly constructed
        assertEquals("Nb-Ti", cecData.elements);
        assertEquals(tcf, cecData.cecValues.length);
        assertEquals("J/mol", cecData.cecUnits);
        assertEquals(SYSTEM_ID_NB_TI, cecData.reference);
        assertEquals(tcf, cecData.tc);

        System.out.println("✓ CECData structure valid for persistence");
    }

    /**
     * Test 9: Element string formatting (utility)
     */
    @Test
    @DisplayName("T9: Element string formatting (sorted, hyphen-separated)")
    void testElementStringFormatting() throws Exception {
        assertEquals("Nb-Ti", CECAssemblyService.toElementString(List.of("Nb", "Ti")));
        assertEquals("Nb-Ti", CECAssemblyService.toElementString(List.of("Ti", "Nb"))); // unsorted
        assertEquals("A-B", CECAssemblyService.toElementString(List.of("B", "A")));

        System.out.println("✓ Element string formatting correct");
    }

    /**
     * Test 10: Full assembly workflow (end-to-end)
     */
    @Test
    @DisplayName("T10: Full assembly workflow (end-to-end integration)")
    void testFullAssemblyWorkflow() throws Exception {
        // Step 1: Load target system data
        Optional<AllClusterData> targetData = AllClusterDataCache.load("BCC_A2_T_bin");
        assertTrue(targetData.isPresent(), "Target AllClusterData must exist");

        AllClusterData target = targetData.get();
        int K = target.getNumComponents();
        int tcf = target.getStage2().getTcf();

        // Step 2: Classify CFs
        int[] cfOrderMap = CECAssemblyService.classifyCFsByOrder(target);
        assertEquals(tcf, cfOrderMap.length, "cfOrderMap should cover all CFs");

        // Step 3: Load subsystem (for K=2, this is the system itself)
        Optional<SystemDataLoader.CECData> subCEC = SystemDataLoader.loadCecData(
            "Nb-Ti", STRUCTURE, PHASE, MODEL);
        assertTrue(subCEC.isPresent(), "Subsystem CEC must exist");

        // Step 4: Extract values
        double[] sourceECIs;
        if (subCEC.get().cecTerms != null) {
            sourceECIs = new double[subCEC.get().cecTerms.length];
            for (int i = 0; i < sourceECIs.length; i++) {
                sourceECIs[i] = subCEC.get().cecTerms[i].a;
            }
        } else {
            sourceECIs = subCEC.get().cecValues.clone();
        }

        // Step 5: Transform
        double[] transformed = CECAssemblyService.transformToTarget(
            sourceECIs, K, K, cfOrderMap, target);
        assertEquals(tcf, transformed.length);

        // Step 6: Accumulate (single subsystem for K=2)
        Map<Integer, double[]> transformedByOrder = new java.util.TreeMap<>();
        transformedByOrder.put(K, transformed);

        // Step 7: Prepare pure-K ECIs (none for K=2, all CFs are binary)
        double[] pureECIs = new double[tcf];
        for (int i = 0; i < tcf; i++) {
            pureECIs[i] = 0.0; // K=2 has no pure-K ECIs
        }

        // Step 8: Assemble
        double[] assembled = CECAssemblyService.assemble(
            transformedByOrder, pureECIs, cfOrderMap, target);
        assertEquals(tcf, assembled.length);

        System.out.println("✓ Full workflow successful:");
        System.out.println("  - Loaded AllClusterData (K=" + K + ", tcf=" + tcf + ")");
        System.out.println("  - Classified " + tcf + " CFs by order");
        System.out.println("  - Loaded subsystem CEC (" + sourceECIs.length + " values)");
        System.out.println("  - Transformed to target basis");
        System.out.println("  - Assembled final CEC (" + assembled.length + " values)");
    }

    // ==================== Helper Methods ====================

    private SystemIdentity createNbTiSystem() {
        return SystemIdentity.builder()
            .id(SYSTEM_ID_NB_TI)
            .name("Nb-Ti BCC A2 (T)")
            .structure(STRUCTURE)
            .phase(PHASE)
            .model(MODEL)
            .components(new String[]{"Nb", "Ti"})
            .clusterFilePath("cluster/A2-T.txt")
            .symmetryGroupName("A2-SG")
            .build();
    }
}
