package org.ce.presentation.cli;

import org.ce.domain.cvm.CVMPhaseModel;
import org.ce.application.dto.CVMCalculationRequest;
import org.ce.application.dto.PreparationResult;
import org.ce.application.dto.MCSCalculationRequest;
import org.ce.infrastructure.service.BackgroundJobManager;
import org.ce.infrastructure.registry.ResultRepository;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.application.job.BackgroundJob;
import org.ce.application.job.CFIdentificationJob;
import org.ce.application.job.JobListener;
import org.ce.infrastructure.service.CalculationService;
import org.ce.presentation.gui.model.CalculationConfig;
import org.ce.infrastructure.context.MCSCalculationContext;
import org.ce.domain.system.SystemIdentity;
import org.ce.domain.system.SystemStatus;

import java.nio.file.Paths;
import java.util.Scanner;
import java.util.logging.Level;
import org.ce.infrastructure.logging.LoggingConfig;

/**
 * Command-line interface for CE Thermodynamics Workbench.
 * Provides a text-based interface to test backend functionality.
 */
public class CEWorkbenchCLI {
    
    private final SystemRegistry registry;
    private final ResultRepository resultRepository;
    private final BackgroundJobManager jobManager;
    private final Scanner scanner;
    
    public CEWorkbenchCLI() throws Exception {
        String userHome = System.getProperty("user.home");
        this.registry = new SystemRegistry(Paths.get(userHome));
        this.resultRepository = new ResultRepository(Paths.get(userHome).resolve(".ce-workbench"));
        this.jobManager = new BackgroundJobManager(2);
        this.scanner = new Scanner(System.in);
    }
    
    public void run() {
        displayHeader();
        
        while (true) {
            displayMenu();
            System.out.print("Enter choice: ");
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    addNewSystem();
                    break;
                case "2":
                    listSystems();
                    break;
                case "3":
                    runBackgroundCalculation();
                    break;
                case "4":
                    monitorJobs();
                    break;
                case "5":
                    setupCalculation();
                    break;
                case "6":
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
        System.out.println("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
        System.out.println("â•‘     CE Thermodynamics Workbench - CLI Interface              â•‘");
        System.out.println("â•‘     Cluster Expansion & Monte Carlo Simulation              â•‘");
        System.out.println("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
        System.out.println();
    }
    
    private void displayMenu() {
        System.out.println("â•”â•â•â•â• MAIN MENU â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
        System.out.println("â•‘ 1. Register New System                                     â”‚");
        System.out.println("â•‘ 2. List Registered Systems                                 â”‚");
        System.out.println("â•‘ 3. Run Background Calculation (Cluster/CF Identification)  â”‚");
        System.out.println("â•‘ 4. Monitor Background Jobs                                 â”‚");
        System.out.println("â•‘ 5. Setup MCS/CVM Calculation                               â”‚");
        System.out.println("â•‘ 6. Show Registry Statistics                                â”‚");
        System.out.println("â•‘ 0. Exit                                                    â”‚");
        System.out.println("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
    }
    
    private void addNewSystem() {
        System.out.println("\n=== Register New System ===");
        
        System.out.print("System ID (e.g., A2-FeNi): ");
        String id = scanner.nextLine().trim();
        
        System.out.print("System Name (e.g., Fe-Ni A2): ");
        String name = scanner.nextLine().trim();
        
        System.out.print("Crystal Structure (BCC/FCC/HCP): ");
        String structure = scanner.nextLine().trim();
        
        System.out.print("Phase Name (e.g., A2, B2): ");
        String phase = scanner.nextLine().trim();
        
        System.out.print("Model (e.g., T for tetrahedron, P for pair): ");
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
        
        System.out.println("[OK] System registered successfully!");
    }
    
    private void listSystems() {
        System.out.println("\n=== Registered Systems ===");
        
        var systems = registry.getAllSystems();
        
        if (systems.isEmpty()) {
            System.out.println("No systems registered yet.");
            return;
        }
        
        System.out.println(String.format("%-20s %-15s %-10s %-10s %-20s",
            "Name", "Structure", "Phase", "Components", "Status"));
        System.out.println(new String(new char[80]).replace('\0', '-'));
        
        for (SystemIdentity system : systems) {
            SystemStatus status = registry.getStatus(system.getId());
            String statusStr = (status != null && status.isClustersComputed() ? "OK" : "NO") + " Clusters | " +
                          (status != null && status.isCfsComputed() ? "OK" : "NO") + " CFs";
            System.out.println(String.format("%-20s %-15s %-10s %-10d %-20s",
                system.getName(),
                system.getStructure(),
                system.getPhase(),
                system.getNumComponents(),
                statusStr));
        }
    }
    
    private void runBackgroundCalculation() {
        System.out.println("\n=== Background Calculation ===");
        
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
            
            System.out.println("\nCalculation Type:");
            System.out.println("1. Cluster Identification (Stage 1)");
            System.out.println("2. CF Identification (Stage 1-2)");
            System.out.print("Select (1 or 2): ");
            
            String calcChoice = scanner.nextLine().trim();
            
            // For now, just create a placeholder job
            System.out.println("âœ“ Background job submitted for " + system.getName());
            System.out.println("  Job ID: bg-job-" + System.currentTimeMillis());
            
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
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
            String statusStr = (status != null && status.isClustersComputed() ? "âœ“" : "âœ—") + " clusters";
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
            
            // Create service with console listener
            ConsoleProgressListener listener = new ConsoleProgressListener();
            CalculationService calcService = new CalculationService(registry, listener);
            
            if (isMCS) {
                runMCSCalculation(system, temperature, composition, calcService);
            } else {
                runCVMCalculation(system, temperature, composition, calcService);
            }
            
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }
    
    private void runMCSCalculation(SystemIdentity system, double temperature, 
                                   double composition, CalculationService calcService) {
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
            request = MCSCalculationRequest.builder()
                .systemId(system.getId())
                .temperature(temperature)
                .composition(composition)
                .supercellSize(supercellSize)
                .equilibrationSteps(equilibration)
                .averagingSteps(averaging)
                .build();
        } catch (IllegalArgumentException ex) {
            System.out.println("âœ— Invalid parameters: " + ex.getMessage());
            return;
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Starting MCS Calculation");
        System.out.println("=".repeat(60));
        
        // Prepare and execute
        PreparationResult<MCSCalculationContext> result = calcService.prepareMCS(request);
        
        if (result.isFailure()) {
            System.out.println("\nâœ— Preparation failed: " + result.getErrorMessage().orElse("Unknown error"));
            return;
        }
        
        calcService.executeMCS(result.getContextOrThrow());
    }
    
    private void runCVMCalculation(SystemIdentity system, double temperature, 
                                   double composition, CalculationService calcService) {
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
            request = CVMCalculationRequest.builder()
                .systemId(system.getId())
                .temperature(temperature)
                .composition(composition)
                .tolerance(tolerance)
                .build();
        } catch (IllegalArgumentException ex) {
            System.out.println("âœ— Invalid parameters: " + ex.getMessage());
            return;
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Starting CVM Phase Model Calculation");
        System.out.println("=".repeat(60));
        
        // Prepare and execute via CVMPhaseModel only
        PreparationResult<CVMPhaseModel> result = calcService.prepareCVMModel(request);
        
        if (result.isFailure()) {
            System.out.println("\nâœ— Preparation failed: " + result.getErrorMessage().orElse("Unknown error"));
            return;
        }

        try {
            CVMPhaseModel model = result.getContextOrThrow();
            CVMPhaseModel.EquilibriumState state = model.getEquilibriumState();

            System.out.println("\n" + "-".repeat(60));
            System.out.println("CVM Phase Model Result");
            System.out.println("-".repeat(60));
            System.out.printf("Gibbs Energy (G): %.8e%n", state.G);
            System.out.printf("Enthalpy (H):     %.8e%n", state.H);
            System.out.printf("Entropy (S):      %.8e%n", state.S);
            System.out.printf("Iterations:       %d%n", state.iterations);
            System.out.printf("Convergence:      %.6e%n", state.convergenceMeasure);
            System.out.printf("Time:             %d ms%n", state.getComputationTimeMs());
        } catch (Exception ex) {
            System.out.println("\nâœ— CVM Phase Model query failed: " + ex.getMessage());
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
        return "â–ˆ".repeat(Math.max(0, filled)) + "â–‘".repeat(Math.max(0, empty));
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

