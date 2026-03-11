package org.ce.presentation.cli;

import org.ce.application.dto.CVMCalculationRequest;
import org.ce.application.dto.MCSCalculationRequest;
import org.ce.infrastructure.service.BackgroundJobManager;
import org.ce.infrastructure.registry.ResultRepository;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.application.job.BackgroundJob;
import org.ce.application.job.CVMPhaseModelJob;
import org.ce.application.job.MCSCalculationJob;
import org.ce.infrastructure.service.DataManagementAdapter;
import org.ce.domain.system.SystemIdentity;
import org.ce.domain.system.SystemStatus;

import java.nio.file.Paths;
import java.util.Scanner;
import java.util.logging.Level;
import org.ce.infrastructure.logging.LoggingConfig;

/**
 * Command-line interface for CE Thermodynamics Workbench.
 * Provides a text-based interface to test backend functionality.
 * Type 2 (Calculation) entry point; delegates Type 1 (Data Management) to DataManagementCLI.
 */
public class CEWorkbenchCLI {

    private final SystemRegistry registry;
    private final ResultRepository resultRepository;
    private final BackgroundJobManager jobManager;
    private final Scanner scanner;
    private final DataManagementCLI dataManagementCLI;

    public CEWorkbenchCLI() throws Exception {
        String userHome = System.getProperty("user.home");
        this.registry = new SystemRegistry(Paths.get(userHome));
        this.resultRepository = new ResultRepository(Paths.get(userHome).resolve(".ce-workbench"));
        this.jobManager = new BackgroundJobManager(2);
        this.scanner = new Scanner(System.in);
        this.dataManagementCLI = new DataManagementCLI(registry, jobManager, scanner);
    }

    public void run() {
        displayHeader();

        while (true) {
            displayMenu();
            System.out.print("Enter choice: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    dataManagementCLI.showMenu();
                    break;
                case "2":
                    setupCalculation();
                    break;
                case "3":
                    monitorJobs();
                    break;
                case "4":
                    showStats();
                    break;
                case "0":
                    shutdown();
                    return;
                default:
                    System.out.println("Invalid choice. Try again.");
            }

            System.out.println();
        }
    }

    private void displayHeader() {
        System.out.println("========================================================");
        System.out.println("  CE Thermodynamics Workbench - CLI Interface");
        System.out.println("  Cluster Expansion & Monte Carlo Simulation");
        System.out.println("========================================================");
        System.out.println();
    }

    private void displayMenu() {
        System.out.println("[MAIN MENU]");
        System.out.println("  1. Data Management (Type 1 Operations)");
        System.out.println("  2. Setup MCS/CVM Calculation (Type 2 Operations)");
        System.out.println("  3. Monitor Background Jobs");
        System.out.println("  4. Show Registry Statistics");
        System.out.println("  0. Exit");
    }

    private void monitorJobs() {
        System.out.println("\n=== Background Job Monitor ===");

        var jobs = jobManager.getActiveJobs();
        var queued = jobManager.getQueuedJobs();

        System.out.println("Active Jobs: " + (jobs.size() - queued.size()));
        System.out.println("Queued Jobs: " + queued.size());
        System.out.println("Total: " + jobs.size());

        if (jobs.isEmpty()) {
            System.out.println("\nNo active jobs.");
            return;
        }

        System.out.println("\nJob Details:");
        for (BackgroundJob job : jobs) {
            System.out.println("  ID: " + job.getId());
            System.out.println("  Name: " + job.getName());
            System.out.println("  Progress: [" + getProgressBar(job.getProgress()) + "] "
                            + job.getProgress() + "%");
            System.out.println("  Status: " + job.getStatusMessage());

            if (job.isFailed()) {
                System.out.println("  Error: " + job.getErrorMessage());
            }
            System.out.println();
        }
    }

    private void setupCalculation() {
        System.out.println("\n=== MCS/CVM Calculation ===" );

        var systems = registry.getAllSystems();
        if (systems.isEmpty()) {
            System.out.println("No systems registered.");
            return;
        }

        System.out.println("Available Systems:");
        var systemList = systems.stream().toList();
        for (int i = 0; i < systemList.size(); i++) {
            SystemIdentity sys = systemList.get(i);
            SystemStatus status = registry.getStatus(sys.getId());
            String statusStr = (status != null && status.isClustersComputed() ? "✓" : "✗") + " clusters";
            System.out.println((i + 1) + ". " + sys.getName() + " [" + statusStr + "]");
        }

        System.out.print("Select system: ");
        try {
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (idx < 0 || idx >= systemList.size()) return;

            SystemIdentity system = systemList.get(idx);

            System.out.println("\nCalculation Type:");
            System.out.println("1. Monte Carlo Simulation (MCS)");
            System.out.println("2. Cluster Variation Method (CVM)");
            System.out.print("Select: ");

            String typeChoice = scanner.nextLine().trim();
            boolean isMCS = "1".equals(typeChoice);

            // Get common parameters
            System.out.print("Temperature (K) [default 800]: ");
            double temperature = 800.0;
            try {
                String input = scanner.nextLine().trim();
                if (!input.isEmpty()) {
                    temperature = Double.parseDouble(input);
                }
            } catch (NumberFormatException e) {
                // Use default
            }

            System.out.print("Composition (0-1) [default 0.5]: ");
            double composition = 0.5;
            try {
                String input = scanner.nextLine().trim();
                if (!input.isEmpty()) {
                    composition = Double.parseDouble(input);
                }
            } catch (NumberFormatException e) {
                // Use default
            }

            if (isMCS) {
                runMCSCalculation(system, temperature, composition);
            } else {
                runCVMCalculation(system, temperature, composition);
            }

        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    private void runMCSCalculation(SystemIdentity system, double temperature,
                                   double composition) {
        // Get MCS-specific parameters
        System.out.print("Supercell size (L) [default 4]: ");
        int supercellSize = 4;
        try {
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) supercellSize = Integer.parseInt(input);
        } catch (NumberFormatException e) { /* use default */ }

        System.out.print("Equilibration steps [default 5000]: ");
        int equilibration = 5000;
        try {
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) equilibration = Integer.parseInt(input);
        } catch (NumberFormatException e) { /* use default */ }

        System.out.print("Averaging steps [default 10000]: ");
        int averaging = 10000;
        try {
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) averaging = Integer.parseInt(input);
        } catch (NumberFormatException e) { /* use default */ }

        // Build request
        MCSCalculationRequest request;
        try {
            int K = system.getNumComponents();
            double[] compositionArray = new double[K];
            double defaultVal = 1.0 / K;
            for (int i = 0; i < K; i++) {
                compositionArray[i] = defaultVal;
            }
            request = MCSCalculationRequest.builder()
                .systemId(system.getId())
                .temperature(temperature)
                .compositionArray(compositionArray)
                .numComponents(K)
                .supercellSize(supercellSize)
                .equilibrationSteps(equilibration)
                .averagingSteps(averaging)
                .build();
        } catch (IllegalArgumentException ex) {
            System.out.println("✗ Invalid parameters: " + ex.getMessage());
            return;
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Starting MCS Calculation");
        System.out.println("=".repeat(60));

        // Create data management port and CLI listener
        DataManagementAdapter dataPort = new DataManagementAdapter(registry);
        ConsoleProgressListener listener = new ConsoleProgressListener();

        // Submit job and wait for completion
        MCSCalculationJob job = new MCSCalculationJob(request, dataPort, listener);
        jobManager.submitJob(job);

        // Block until job completes
        try {
            int maxWaitSeconds = 3600; // 1 hour timeout
            long startTime = System.currentTimeMillis();
            while (job.isRunning()) {
                if (System.currentTimeMillis() - startTime > maxWaitSeconds * 1000) {
                    System.out.println("✗ Job timeout after " + maxWaitSeconds + " seconds");
                    return;
                }
                Thread.sleep(500);
            }

            if (job.isFailed()) {
                System.out.println("\n✗ MCS Calculation failed: " + job.getErrorMessage());
                return;
            }

            System.out.println("\n✓ MCS Calculation completed successfully!");
        } catch (InterruptedException ex) {
            System.out.println("✗ Interrupted while waiting for job: " + ex.getMessage());
        }
    }

    private void runCVMCalculation(SystemIdentity system, double temperature,
                                   double composition) {
        // Get CVM-specific parameters
        System.out.print("Tolerance [default 1e-6]: ");
        double tolerance = 1e-6;
        try {
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) tolerance = Double.parseDouble(input);
        } catch (NumberFormatException e) { /* use default */ }

        // Build request
        CVMCalculationRequest request;
        try {
            int K = system.getNumComponents();
            double[] compositionArray = new double[K];
            double defaultVal = 1.0 / K;
            for (int i = 0; i < K; i++) {
                compositionArray[i] = defaultVal;
            }
            request = CVMCalculationRequest.builder()
                .systemId(system.getId())
                .temperature(temperature)
                .compositionArray(compositionArray)
                .numComponents(K)
                .tolerance(tolerance)
                .build();
        } catch (IllegalArgumentException ex) {
            System.out.println("✗ Invalid parameters: " + ex.getMessage());
            return;
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Starting CVM Phase Model Calculation");
        System.out.println("=".repeat(60));

        // Create data management port and CLI listener
        DataManagementAdapter dataPort = new DataManagementAdapter(registry);
        ConsoleProgressListener listener = new ConsoleProgressListener();

        // Submit job and wait for completion
        CVMPhaseModelJob job = new CVMPhaseModelJob(request, dataPort, listener);
        jobManager.submitJob(job);

        // Block until job completes
        try {
            int maxWaitSeconds = 3600; // 1 hour timeout
            long startTime = System.currentTimeMillis();
            while (job.isRunning()) {
                if (System.currentTimeMillis() - startTime > maxWaitSeconds * 1000) {
                    System.out.println("✗ Job timeout after " + maxWaitSeconds + " seconds");
                    return;
                }
                Thread.sleep(500);
            }

            if (job.isFailed()) {
                System.out.println("\n✗ CVM Calculation failed: " + job.getErrorMessage());
                return;
            }

            System.out.println("\n✓ CVM Calculation completed successfully!");
        } catch (InterruptedException ex) {
            System.out.println("✗ Interrupted while waiting for job: " + ex.getMessage());
        }
    }

    private void showStats() {
        System.out.println("\n=== Registry Statistics ===");

        var stats = registry.getStats();

        System.out.println("Registered Systems: " + stats.systemCount);
        System.out.println("Total Results Stored: " + resultRepository.count());
        System.out.println("Workspace Size: " + formatBytes(stats.usedSpace) + " / " +
                          formatBytes(stats.totalSpace));

        int percentUsed = stats.totalSpace > 0 ?
            (int)(100.0 * stats.usedSpace / stats.totalSpace) : 0;
        System.out.println("Storage Usage: [" + getProgressBar(percentUsed) + "] " + percentUsed + "%");
    }

    private void shutdown() {
        System.out.println("\nShutting down...");
        jobManager.shutdown();
        registry.shutdown();
        System.out.println("Goodbye!");
    }

    private String getProgressBar(int percent) {
        int filled = percent / 5;  // 20 chars total
        int empty = 20 - filled;
        return "#".repeat(Math.max(0, filled)) + "-".repeat(Math.max(0, empty));
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f%s",
            bytes / Math.pow(1024, digitGroups),
            units[digitGroups]);
    }

    public static void main(String[] args) throws Exception {
        // Configure logging before anything else; honour --log-level=FINEST etc.
        Level logLevel = Level.INFO;
        for (String arg : args) {
            if (arg.startsWith("--log-level=")) {
                try {
                    logLevel = Level.parse(arg.substring("--log-level=".length()).toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        LoggingConfig.configure(logLevel);

        CEWorkbenchCLI cli = new CEWorkbenchCLI();
        cli.run();
    }
}
