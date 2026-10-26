package spooler;

import spooler.controller.ApiController;
import spooler.server.HttpServerManager;
import spooler.service.ActivityLogger;
import spooler.service.PrinterSpooler;

import java.io.File;
import java.io.IOException;

/**
 * Main - Entry point for the Printer Spooler System.
 *
 * PRINTER SPOOLER SYSTEM - SPOS Mini Project
 *
 * This application demonstrates Operating System concepts:
 *   - Process/Thread Synchronization
 *   - Mutual Exclusion (ReentrantLock)
 *   - Critical Section (printer access)
 *   - Producer-Consumer Pattern
 *   - FIFO Queue Scheduling
 *   - Shared Resource Management
 *   - Condition Variables for signaling
 *
 * Architecture:
 *   Browser -> HTML/CSS/JS -> HTTP -> Java Server -> Spooler
 *
 * Usage:
 *   javac -d out src/spooler/**\/*.java
 *   java -cp out spooler.Main
 *   Open: http://localhost:8080
 */
public class Main {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        System.out.println();
        System.out.println("  +-------------------------------------------+");
        System.out.println("  |   PRINTER SPOOLER SYSTEM                  |");
        System.out.println("  |   SPOS Mini Project                       |");
        System.out.println("  |   Demonstrating OS Synchronization        |");
        System.out.println("  +-------------------------------------------+");
        System.out.println();

        // Determine the port
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number, using default: " + DEFAULT_PORT);
            }
        }

        // Determine the frontend path
        String frontendPath = determineFrontendPath();
        System.out.println("Frontend path: " + frontendPath);

        try {
            // Step 1: Initialize the Activity Logger
            ActivityLogger logger = new ActivityLogger();
            logger.logSystem("System initializing...");

            // Step 2: Initialize the Printer Spooler (core SPOS logic)
            PrinterSpooler spooler = new PrinterSpooler(logger);

            // Step 3: Initialize the API Controller
            ApiController apiController = new ApiController(spooler);

            // Step 4: Initialize and start the HTTP Server
            HttpServerManager httpServer = new HttpServerManager(port, apiController, frontendPath);
            httpServer.start();

            // Step 5: Start the Printer Worker thread
            spooler.startPrinterWorker();

            logger.logSystem("System fully initialized and ready");
            logger.logSystem("Open http://localhost:" + port + " in your browser");

            // Register shutdown hook for clean shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down...");
                spooler.shutdown();
                httpServer.stop();
                System.out.println("Shutdown complete.");
            }));

            // Keep the main thread alive
            Thread.currentThread().join();

        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.err.println("Is port " + port + " already in use?");
            System.exit(1);
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted.");
        }
    }

    /**
     * Determine the frontend directory path.
     * Tries multiple locations relative to the working directory.
     */
    private static String determineFrontendPath() {
        String[] candidates = {
            "../frontend",
            "frontend",
            "../../frontend",
            "./frontend"
        };

        for (String candidate : candidates) {
            File dir = new File(candidate);
            if (dir.exists() && dir.isDirectory()) {
                try {
                    return dir.getCanonicalPath();
                } catch (IOException e) {
                    return dir.getAbsolutePath();
                }
            }
        }

        System.out.println("WARNING: Frontend directory not found. Using default path.");
        return new File("../frontend").getAbsolutePath();
    }
}
