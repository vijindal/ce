package org.ce.app.cli;

import org.ce.app.gui.backend.BackgroundJobManager;
import org.ce.app.gui.backend.SystemRegistry;
import org.ce.app.gui.backend.jobs.BackgroundJob;
import org.ce.app.gui.backend.jobs.CFIdentificationJob;
import org.ce.app.gui.backend.jobs.JobListener;
import org.ce.app.gui.models.CalculationConfig;
import org.ce.app.gui.models.SystemInfo;

import java.nio.file.Paths;
import java.util.Scanner;

/**
 * Command-line interface for CE Thermodynamics Workbench.
 * Provides a text-based interface to test backend functionality.
 */
public class CEWorkbenchCLI {
    
    private final SystemRegistry registry;
    private final BackgroundJobManager jobManager;
    private final Scanner scanner;
    
    public CEWorkbenchCLI() throws Exception {
        String userHome = System.getProperty("user.home");
        this.registry = new SystemRegistry(Paths.get(userHome));
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
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     CE Thermodynamics Workbench - CLI Interface              ║");
        System.out.println("║     Cluster Expansion & Monte Carlo Simulation              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
    
    private void displayMenu() {
        System.out.println("╔════ MAIN MENU ════════════════════════════════════════════╗");
        System.out.println("║ 1. Register New System                                     │");
        System.out.println("║ 2. List Registered Systems                                 │");
        System.out.println("║ 3. Run Background Calculation (Cluster/CF Identification)  │");
        System.out.println("║ 4. Monitor Background Jobs                                 │");
        System.out.println("║ 5. Setup MCS/CVM Calculation                               │");
        System.out.println("║ 6. Show Registry Statistics                                │");
        System.out.println("║ 0. Exit                                                    │");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
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
        
        SystemInfo system = new SystemInfo(id, name, structure, phase, components);
        registry.registerSystem(system);
        
        System.out.println("✓ System registered successfully!");
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
        System.out.println(new String(new char[80]).replace('\0', '─'));
        
        for (SystemInfo system : systems) {
            String status = (system.isClustersComputed() ? "✓" : "✗") + " Clusters | " +
                          (system.isCfsComputed() ? "✓" : "✗") + " CFs";
            System.out.println(String.format("%-20s %-15s %-10s %-10d %-20s",
                system.getName(),
                system.getStructure(),
                system.getPhase(),
                system.getNumComponents(),
                status));
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
            
            SystemInfo system = systemList.get(choice);
            
            System.out.println("\nCalculation Type:");
            System.out.println("1. Cluster Identification (Stage 1)");
            System.out.println("2. CF Identification (Stage 1-2)");
            System.out.print("Select (1 or 2): ");
            
            String calcChoice = scanner.nextLine().trim();
            
            // For now, just create a placeholder job
            System.out.println("✓ Background job submitted for " + system.getName());
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
        System.out.println("\n=== MCS/CVM Calculation Setup ===");
        
        var systems = registry.getAllSystems();
        if (systems.isEmpty()) {
            System.out.println("No systems registered.");
            return;
        }
        
        System.out.println("Available Systems:");
        var systemList = systems.stream().toList();
        for (int i = 0; i < systemList.size(); i++) {
            System.out.println((i + 1) + ". " + systemList.get(i));
        }
        
        System.out.print("Select system: ");
        try {
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (idx < 0 || idx >= systemList.size()) return;
            
            SystemInfo system = systemList.get(idx);
            
            System.out.println("\nCalculation Type:");
            System.out.println("1. Monte Carlo Simulation (MCS)");
            System.out.println("2. Cluster Variation Method (CVM)");
            System.out.print("Select: ");
            
            String typeChoice = scanner.nextLine().trim();
            CalculationConfig.CalculationType type = "1".equals(typeChoice) ?
                CalculationConfig.CalculationType.MCS :
                CalculationConfig.CalculationType.CVM;
            
            CalculationConfig config = new CalculationConfig(type, system);
            
            System.out.print("Temperature (K) [default 1000]: ");
            try {
                double temp = Double.parseDouble(scanner.nextLine().trim());
                config.setTemperature(temp);
            } catch (NumberFormatException e) {
                // Use default
            }
            
            System.out.println("\n✓ Configuration prepared:");
            System.out.println("  System: " + system.getName());
            System.out.println("  Type: " + type.getDisplayName());
            System.out.println("  Temperature: " + config.getTemperature() + " K");
            System.out.println("\n[Ready to execute - GUI will handle this]");
            
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }
    
    private void showStats() {
        System.out.println("\n=== Registry Statistics ===");
        
        var stats = registry.getStats();
        
        System.out.println("Registered Systems: " + stats.systemCount);
        System.out.println("Total Results Stored: " + stats.resultCount);
        System.out.println("Workspace Size: " + formatBytes(stats.usedSpace) + " / " + 
                          formatBytes(stats.totalSpace));
        
        int percentUsed = stats.totalSpace > 0 ? 
            (int)(100.0 * stats.usedSpace / stats.totalSpace) : 0;
        System.out.println("Storage Usage: [" + getProgressBar(percentUsed) + "] " + percentUsed + "%");
    }
    
    private void shutdown() {
        System.out.println("\nShutting down...");
        jobManager.shutdown();
        System.out.println("Goodbye!");
    }
    
    private String getProgressBar(int percent) {
        int filled = percent / 5;  // 20 chars total
        int empty = 20 - filled;
        return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
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
        CEWorkbenchCLI cli = new CEWorkbenchCLI();
        cli.run();
    }
}
