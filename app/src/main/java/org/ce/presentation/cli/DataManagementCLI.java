package org.ce.presentation.cli;

import org.ce.application.job.CFIdentificationJob;
import org.ce.application.job.JobListener;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.infrastructure.service.BackgroundJobManager;
import org.ce.domain.system.SystemIdentity;
import org.ce.domain.system.SystemStatus;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.data.SystemDataLoader.CECData;
import org.ce.application.service.CECAssemblyService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.ce.infrastructure.logging.LoggingConfig;
import java.text.SimpleDateFormat;

/**
 * CLI interface for Type 1 (Data Management) operations.
 * Handles system registry, cluster identification, and CEC database management.
 */
public class DataManagementCLI {

    private static final Logger LOG = LoggingConfig.getLogger(DataManagementCLI.class);

    private final SystemRegistry registry;
    private final BackgroundJobManager jobManager;
    private final Scanner scanner;

    public DataManagementCLI(SystemRegistry registry, BackgroundJobManager jobManager, Scanner scanner) {
        this.registry = registry;
        this.jobManager = jobManager;
        this.scanner = scanner;
    }

    /**
     * Display and handle Type 1 Data Management submenu.
     */
    public void showMenu() {
        while (true) {
            System.out.println("\n┌─────────── DATA MANAGEMENT (Type 1) ───────────┐");
            System.out.println("│ 1. System Registry                             │");
            System.out.println("│ 2. Cluster Identification                      │");
            System.out.println("│ 3. CEC Database                                │");
            System.out.println("│ 0. Back to Main Menu                           │");
            System.out.println("└──────────────────────────────────────────────┘");
            System.out.print("Enter choice: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    systemRegistryMenu();
                    break;
                case "2":
                    clusterIdentificationMenu();
                    break;
                case "3":
                    cecDatabaseMenu();
                    break;
                case "0":
                    return;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    /**
     * System Registry submenu: register, list, remove systems.
     */
    private void systemRegistryMenu() {
        while (true) {
            System.out.println("\n┌─────── System Registry ──────┐");
            System.out.println("│ 1. Register New System       │");
            System.out.println("│ 2. List Systems              │");
            System.out.println("│ 3. Remove System             │");
            System.out.println("│ 0. Back                      │");
            System.out.println("└──────────────────────────────┘");
            System.out.print("Enter choice: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    registerNewSystem();
                    break;
                case "2":
                    listSystems();
                    break;
                case "3":
                    removeSystem();
                    break;
                case "0":
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    /**
     * Cluster Identification submenu: run identification pipeline or check status.
     */
    private void clusterIdentificationMenu() {
        while (true) {
            System.out.println("\n┌──── Cluster Identification ────┐");
            System.out.println("│ 1. Run Identification Pipeline │");
            System.out.println("│ 2. Check Cluster Cache Status  │");
            System.out.println("│ 0. Back                        │");
            System.out.println("└────────────────────────────────┘");
            System.out.print("Enter choice: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    runIdentificationPipeline();
                    break;
                case "2":
                    checkClusterCacheStatus();
                    break;
                case "0":
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    /**
     * CEC Database submenu: browse or assemble CECs.
     */
    private void cecDatabaseMenu() {
        while (true) {
            System.out.println("\n┌────── CEC Database ────────┐");
            System.out.println("│ 1. Browse CECs             │");
            System.out.println("│ 2. Assemble Ternary+ CEC  │");
            System.out.println("│ 0. Back                    │");
            System.out.println("└────────────────────────────┘");
            System.out.print("Enter choice: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    browseCECs();
                    break;
                case "2":
                    assembleCEC();
                    break;
                case "0":
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    /**
     * Register a new system in the registry.
     */
    private void registerNewSystem() {
        System.out.println("\n=== Register New System ===");

        System.out.print("System ID (e.g., Nb-Ti): ");
        String id = scanner.nextLine().trim();

        System.out.print("System Name (e.g., Niobium-Titanium): ");
        String name = scanner.nextLine().trim();

        System.out.print("Crystal Structure (BCC/FCC/HCP): ");
        String structure = scanner.nextLine().trim();

        System.out.print("Phase Name (e.g., A2, B2): ");
        String phase = scanner.nextLine().trim();

        System.out.print("Model (e.g., T for tetrahedron): ");
        String model = scanner.nextLine().trim();

        System.out.print("Number of Components: ");
        int numComponents;
        try {
            numComponents = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
            return;
        }

        String[] components = new String[numComponents];
        for (int i = 0; i < numComponents; i++) {
            System.out.print("Component " + (i + 1) + " name: ");
            components[i] = scanner.nextLine().trim();
        }

        SystemIdentity system = SystemIdentity.builder()
            .id(id)
            .name(name)
            .structure(structure)
            .phase(phase)
            .model(model)
            .components(components)
            .build();
        registry.registerSystem(system);

        System.out.println("[✓] System registered successfully!");
    }

    /**
     * List all registered systems with their status.
     */
    private void listSystems() {
        System.out.println("\n=== Registered Systems ===");

        var systems = registry.getAllSystems();

        if (systems.isEmpty()) {
            System.out.println("No systems registered yet.");
            return;
        }

        System.out.println(String.format("%-15s %-25s %-10s %-15s %-20s",
            "ID", "Name", "K", "Structure", "Status"));
        System.out.println(new String(new char[85]).replace('\0', '-'));

        for (SystemIdentity system : systems) {
            SystemStatus status = registry.getStatus(system.getId());
            String clusters = (status != null && status.isClustersComputed()) ? "✓ Clusters" : "✗ No Clusters";
            String cfs = (status != null && status.isCfsComputed()) ? "✓ CFs" : "✗ No CFs";
            String statusStr = clusters + " | " + cfs;

            System.out.println(String.format("%-15s %-25s %-10d %-15s %-20s",
                system.getId(),
                system.getName(),
                system.getNumComponents(),
                system.getStructure(),
                statusStr));
        }
    }

    /**
     * Remove a system from the registry.
     */
    private void removeSystem() {
        System.out.println("\n=== Remove System ===");

        var systems = registry.getAllSystems();
        if (systems.isEmpty()) {
            System.out.println("No systems to remove.");
            return;
        }

        System.out.println("Systems:");
        var systemList = systems.stream().toList();
        for (int i = 0; i < systemList.size(); i++) {
            System.out.println((i + 1) + ". " + systemList.get(i).getName());
        }

        System.out.print("Select system to remove (number): ");
        try {
            int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (choice < 0 || choice >= systemList.size()) {
                System.out.println("Invalid choice.");
                return;
            }

            SystemIdentity system = systemList.get(choice);
            System.out.print("Confirm removal of '" + system.getName() + "' (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                try {
                    registry.removeSystem(system.getId());
                    System.out.println("[✓] System removed successfully!");
                } catch (Exception e) {
                    System.out.println("✗ Error removing system: " + e.getMessage());
                }
            } else {
                System.out.println("Cancelled.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    /**
     * Run cluster identification pipeline (CFIdentificationJob).
     */
    private void runIdentificationPipeline() {
        System.out.println("\n=== Run Cluster Identification ===");

        var systems = registry.getAllSystems();
        if (systems.isEmpty()) {
            System.out.println("No systems registered. Register one first.");
            return;
        }

        System.out.println("Available Systems:");
        var systemList = systems.stream().toList();
        for (int i = 0; i < systemList.size(); i++) {
            System.out.println((i + 1) + ". " + systemList.get(i).getName());
        }

        System.out.print("Select system (number): ");
        try {
            int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (choice < 0 || choice >= systemList.size()) {
                System.out.println("Invalid choice.");
                return;
            }

            SystemIdentity system = systemList.get(choice);

            // Note: Full implementation requires cluster configuration data (transformation matrices, symmetry groups, etc.)
            // This is typically configured per structure-phase-model combination
            System.out.println("\nStarting cluster identification for: " + system.getName());
            System.out.println("⚠ Note: Full cluster identification requires configuration of:");
            System.out.println("  - Cluster basis files (ordered/disordered)");
            System.out.println("  - Transformation matrices");
            System.out.println("  - Symmetry groups");
            System.out.println("\n[✓] Cluster identification pipeline would be submitted to background jobs");
            System.out.println("    Implementation: instantiate CFIdentificationJob with all required parameters");

        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    /**
     * Check the status of cluster cache directory.
     */
    private void checkClusterCacheStatus() {
        System.out.println("\n=== Cluster Cache Status ===");

        try {
            Path clusterCacheDir = Paths.get(System.getProperty("user.dir"))
                .resolve("data/cluster_cache");

            if (!Files.exists(clusterCacheDir)) {
                System.out.println("Cluster cache directory not found: " + clusterCacheDir);
                return;
            }

            System.out.println("Cache location: " + clusterCacheDir);
            System.out.println();

            // List all cluster cache entries
            var entries = Files.list(clusterCacheDir).toList();
            if (entries.isEmpty()) {
                System.out.println("Cache is empty.");
            } else {
                System.out.println(String.format("%-30s %-15s %-20s", "Cluster Key", "File Size", "Last Modified"));
                System.out.println(new String(new char[65]).replace('\0', '-'));

                for (Path entry : entries) {
                    if (Files.isDirectory(entry)) {
                        Path dataFile = entry.resolve("all_cluster_data.json");
                        String size = Files.exists(dataFile) ? formatBytes(Files.size(dataFile)) : "N/A";
                        long lastMod = Files.exists(dataFile) ? Files.getLastModifiedTime(dataFile).toMillis() : 0;
                        String date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(lastMod));

                        System.out.println(String.format("%-30s %-15s %-20s",
                            entry.getFileName(),
                            size,
                            date));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("✗ Error reading cluster cache: " + e.getMessage());
        }
    }

    /**
     * Browse CEC entries for a selected system.
     */
    private void browseCECs() {
        System.out.println("\n=== Browse CECs ===");

        var systems = registry.getAllSystems();
        if (systems.isEmpty()) {
            System.out.println("No systems registered.");
            return;
        }

        System.out.println("Available Systems:");
        var systemList = systems.stream().toList();
        for (int i = 0; i < systemList.size(); i++) {
            System.out.println((i + 1) + ". " + systemList.get(i).getName());
        }

        System.out.print("Select system: ");
        try {
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (idx < 0 || idx >= systemList.size()) return;

            SystemIdentity system = systemList.get(idx);

            // Load and display CEC data
            Optional<CECData> cecData = SystemDataLoader.loadCecData(
                system.getId(), system.getStructure(), system.getPhase(), system.getModel());
            if (cecData.isEmpty()) {
                System.out.println("No CEC data found for: " + system.getName());
                return;
            }

            CECData cec = cecData.get();
            System.out.println("\n=== CEC Data for " + system.getName() + " ===");
            System.out.println("Elements: " + cec.elements);
            System.out.println("Structure: " + cec.structure);
            System.out.println("Phase: " + cec.phase);
            System.out.println("Model: " + cec.model);
            System.out.println("Number of terms: " + cec.cecValues.length);
            System.out.println("Units: " + cec.cecUnits);
            System.out.println();

            // Display first few CEC terms
            int displayCount = Math.min(10, cec.cecValues.length);
            System.out.println("First " + displayCount + " CEC values:");
            System.out.println(String.format("%-10s %-15s", "Index", "Value (J/mol)"));
            System.out.println(new String(new char[25]).replace('\0', '-'));

            for (int i = 0; i < displayCount; i++) {
                System.out.println(String.format("%-10d %-15.6f", i, cec.cecValues[i]));
            }

            if (cec.cecValues.length > displayCount) {
                System.out.println("... and " + (cec.cecValues.length - displayCount) + " more terms");
            }

        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    /**
     * Assemble a ternary or higher-order CEC from binary subsystems.
     */
    private void assembleCEC() {
        System.out.println("\n=== Assemble Ternary+ CEC ===");

        var systems = registry.getAllSystems();

        // Filter to K >= 2 systems with cluster data
        var targetSystems = systems.stream()
            .filter(s -> s.getNumComponents() >= 2)
            .toList();

        if (targetSystems.isEmpty()) {
            System.out.println("No target systems with K >= 2 available.");
            return;
        }

        System.out.println("Target Systems (K >= 2):");
        for (int i = 0; i < targetSystems.size(); i++) {
            System.out.println((i + 1) + ". " + targetSystems.get(i).getName() + " (K=" + targetSystems.get(i).getNumComponents() + ")");
        }

        System.out.print("Select target system: ");
        try {
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (idx < 0 || idx >= targetSystems.size()) return;

            SystemIdentity targetSystem = targetSystems.get(idx);

            // Show assembly preview (subsystems required)
            System.out.println("\n=== Assembly Preview ===");
            System.out.println("Target: " + targetSystem.getName() + " (K=" + targetSystem.getNumComponents() + ")");

            // Get list of required subsystems by order
            var subsystemsByOrder = CECAssemblyService.subsystemsByOrder(targetSystem.getComponents());
            System.out.println("Required subsystems by order:");

            int totalCount = 0;
            for (var entry : subsystemsByOrder.entrySet()) {
                int order = entry.getKey();
                System.out.println("  Order K=" + order + ":");
                for (List<String> compList : entry.getValue()) {
                    String compKey = String.join("-", compList);
                    System.out.println("    " + compKey);
                    totalCount++;
                }
            }

            System.out.println("\nTotal subsystems required: " + totalCount);
            System.out.print("\nProceed with assembly? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                System.out.println("[✓] Assembly would be performed here (stub).");
                System.out.println("    Implementation: wrap CECAssemblyService.assemble()");
                System.out.println("    Steps:");
                System.out.println("      1. Load each subsystem CEC from disk");
                System.out.println("      2. Transform ECIs using Chebyshev basis scaling");
                System.out.println("      3. Combine transformed contributions + pure-K ECIs");
                System.out.println("      4. Persist assembled CEC to workspace");
            }

        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    /**
     * Progress bar utility.
     */
    private String getProgressBar(int percent) {
        int filled = percent / 5;
        int empty = 20 - filled;
        return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
    }

    /**
     * Format bytes to human-readable size.
     */
    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f%s",
            bytes / Math.pow(1024, digitGroups),
            units[digitGroups]);
    }
}
