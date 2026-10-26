package spooler.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import spooler.controller.ApiController;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * HttpServerManager - Manages the built-in Java HTTP server.
 *
 * Architecture:
 *   This class creates and configures a com.sun.net.httpserver.HttpServer
 *   that serves both:
 *     1. Static files (HTML, CSS, JS) for the frontend
 *     2. API endpoints for the backend logic
 *
 *   This means the entire application runs from a single Java process.
 *   No separate frontend server (Node.js, etc.) is needed.
 *
 *   URL: http://localhost:8080
 *
 *   Routes:
 *     /api/*  -> ApiController (REST endpoints)
 *     /*      -> Static file server (frontend files)
 */
public class HttpServerManager {

    private final HttpServer server;
    private final ApiController apiController;
    private final String frontendPath;
    private final int port;

    /**
     * Create the HTTP server.
     *
     * @param port The port to listen on (default: 8080)
     * @param apiController The API controller for handling API requests
     * @param frontendPath Absolute path to the frontend directory
     */
    public HttpServerManager(int port, ApiController apiController, String frontendPath) throws IOException {
        this.port = port;
        this.apiController = apiController;
        this.frontendPath = frontendPath;

        // Create HTTP server bound to all interfaces on the specified port
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        // Set up a thread pool for handling requests concurrently
        this.server.setExecutor(Executors.newFixedThreadPool(10));

        // Register request handlers
        setupRoutes();
    }

    /**
     * Set up URL routes/contexts.
     */
    private void setupRoutes() {
        // API routes: /api/* -> handled by ApiController
        server.createContext("/api/", exchange -> {
            try {
                apiController.handleRequest(exchange);
            } catch (Exception e) {
                System.err.println("API Error: " + e.getMessage());
                sendError(exchange, 500, "Internal Server Error");
            }
        });

        // Static file routes: /* -> serve frontend files
        server.createContext("/", exchange -> {
            try {
                serveStaticFile(exchange);
            } catch (Exception e) {
                System.err.println("Static file error: " + e.getMessage());
                sendError(exchange, 500, "Internal Server Error");
            }
        });
    }

    /**
     * Serve static files from the frontend directory.
     */
    private void serveStaticFile(HttpExchange exchange) throws IOException {
        String uriPath = exchange.getRequestURI().getPath();

        // Default to index.html for root path
        if (uriPath.equals("/") || uriPath.isEmpty()) {
            uriPath = "/index.html";
        }

        // Prevent directory traversal attacks
        if (uriPath.contains("..")) {
            sendError(exchange, 403, "Forbidden");
            return;
        }

        // Resolve file path
        Path filePath = Path.of(frontendPath, uriPath);
        File file = filePath.toFile();

        if (!file.exists() || !file.isFile()) {
            // If file not found, serve index.html (SPA fallback)
            filePath = Path.of(frontendPath, "index.html");
            file = filePath.toFile();
            if (!file.exists()) {
                sendError(exchange, 404, "File not found: " + uriPath);
                return;
            }
        }

        // Determine content type based on file extension
        String contentType = getContentType(file.getName());
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");

        // Send file contents
        byte[] fileBytes = Files.readAllBytes(filePath);
        exchange.sendResponseHeaders(200, fileBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileBytes);
        }
    }

    /**
     * Get MIME content type from file extension.
     */
    private String getContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    /**
     * Send an error response.
     */
    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String response = "{\"success\":false,\"error\":\"" + message + "\"}";
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = response.getBytes();
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Start the HTTP server.
     */
    public void start() {
        server.start();
        System.out.println("+--------------------------------------------------+");
        System.out.println("|     PRINTER SPOOLER SYSTEM - HTTP Server          |");
        System.out.println("+--------------------------------------------------+");
        System.out.println("|  Server started on port " + port + "                      |");
        System.out.println("|  URL: http://localhost:" + port + "                     |");
        System.out.println("|  Frontend: " + frontendPath);
        System.out.println("|                                                    |");
        System.out.println("|  API Endpoints:                                    |");
        System.out.println("|    GET  /api/health          - Health check         |");
        System.out.println("|    GET  /api/status          - System status        |");
        System.out.println("|    GET  /api/jobs            - List all jobs        |");
        System.out.println("|    POST /api/jobs            - Submit a job         |");
        System.out.println("|    GET  /api/jobs/{id}       - Get job details      |");
        System.out.println("|    DEL  /api/jobs/{id}       - Cancel a job         |");
        System.out.println("|    GET  /api/queue           - Print queue          |");
        System.out.println("|    POST /api/simulation/start - Simulation          |");
        System.out.println("|    POST /api/printer/start   - Start printer        |");
        System.out.println("|    POST /api/printer/pause   - Pause printer        |");
        System.out.println("|    POST /api/printer/resume  - Resume printer       |");
        System.out.println("|    GET  /api/statistics      - Statistics           |");
        System.out.println("|    GET  /api/logs            - Activity logs        |");
        System.out.println("|    GET  /api/synchronization - Sync monitor         |");
        System.out.println("|    POST /api/reset           - Reset system         |");
        System.out.println("|    POST /api/speed           - Set speed            |");
        System.out.println("|                                                    |");
        System.out.println("|  Press Ctrl+C to stop the server                   |");
        System.out.println("+--------------------------------------------------+");
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        server.stop(0);
        System.out.println("HTTP Server stopped.");
    }
}
