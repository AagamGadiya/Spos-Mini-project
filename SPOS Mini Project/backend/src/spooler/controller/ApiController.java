package spooler.controller;

import com.sun.net.httpserver.HttpExchange;
import spooler.model.PrintJob;
import spooler.model.PrintJobStatus;
import spooler.service.PrinterSpooler;
import spooler.util.JsonUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * ApiController - Handles all REST-like HTTP API requests.
 * 
 * This class routes incoming HTTP requests to the appropriate
 * service methods and returns JSON responses.
 * 
 * Architecture:
 *   Browser → HTTP Request → ApiController → PrinterSpooler → JSON Response
 * 
 * All endpoints return JSON with consistent structure:
 *   Success: {"success": true, "data": {...}, "message": "..."}
 *   Error:   {"success": false, "error": "..."}
 */
public class ApiController {

    private final PrinterSpooler spooler;

    public ApiController(PrinterSpooler spooler) {
        this.spooler = spooler;
    }

    /**
     * Route and handle an API request.
     * This is the main entry point called by HttpServerManager.
     */
    public void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // Add CORS headers for browser compatibility
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

        // Handle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            sendResponse(exchange, 200, "{}");
            return;
        }

        try {
            // Route the request
            if (path.equals("/api/health")) {
                handleHealth(exchange);
            } else if (path.equals("/api/status")) {
                handleStatus(exchange);
            } else if (path.equals("/api/jobs") && "GET".equalsIgnoreCase(method)) {
                handleGetJobs(exchange);
            } else if (path.equals("/api/jobs") && "POST".equalsIgnoreCase(method)) {
                handleSubmitJob(exchange);
            } else if (path.startsWith("/api/jobs/") && "GET".equalsIgnoreCase(method)) {
                handleGetJob(exchange, path);
            } else if (path.startsWith("/api/jobs/") && "DELETE".equalsIgnoreCase(method)) {
                handleCancelJob(exchange, path);
            } else if (path.equals("/api/queue")) {
                handleGetQueue(exchange);
            } else if (path.equals("/api/simulation/start") && "POST".equalsIgnoreCase(method)) {
                handleSimulation(exchange);
            } else if (path.equals("/api/printer/start") && "POST".equalsIgnoreCase(method)) {
                handleStartPrinter(exchange);
            } else if (path.equals("/api/printer/pause") && "POST".equalsIgnoreCase(method)) {
                handlePausePrinter(exchange);
            } else if (path.equals("/api/printer/resume") && "POST".equalsIgnoreCase(method)) {
                handleResumePrinter(exchange);
            } else if (path.equals("/api/statistics")) {
                handleStatistics(exchange);
            } else if (path.equals("/api/logs")) {
                handleLogs(exchange);
            } else if (path.equals("/api/synchronization")) {
                handleSynchronization(exchange);
            } else if (path.equals("/api/reset") && "POST".equalsIgnoreCase(method)) {
                handleReset(exchange);
            } else if (path.equals("/api/speed") && "POST".equalsIgnoreCase(method)) {
                handleSetSpeed(exchange);
            } else {
                sendResponse(exchange, 404, JsonUtil.errorJson("Endpoint not found: " + path));
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, JsonUtil.errorJson("Internal server error: " + e.getMessage()));
        }
    }

    // ===================================================================
    //  ENDPOINT HANDLERS
    // ===================================================================

    /** GET /api/health - Health check */
    private void handleHealth(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200, "{\"success\":true,\"status\":\"UP\",\"message\":\"Printer Spooler System is running\"}");
    }

    /** GET /api/status - Overall system status (for dashboard) */
    private void handleStatus(HttpExchange exchange) throws IOException {
        var printer = spooler.getPrinter();
        var stats = spooler.getStatsManager();
        var queueMgr = spooler.getQueueManager();

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"success\":true,");
        sb.append("\"printer\":").append(printer.toJson()).append(",");
        sb.append("\"queueSize\":").append(queueMgr.getQueueSize()).append(",");
        sb.append("\"statistics\":").append(stats.toJson()).append(",");
        sb.append("\"workerRunning\":").append(spooler.isWorkerRunning()).append(",");
        sb.append("\"simulationSpeed\":").append(spooler.getMsPerPage());
        sb.append("}");

        sendResponse(exchange, 200, sb.toString());
    }

    /** GET /api/jobs - List all jobs */
    private void handleGetJobs(HttpExchange exchange) throws IOException {
        String jobsJson = spooler.getQueueManager().allJobsToJsonArray();
        sendResponse(exchange, 200, "{\"success\":true,\"jobs\":" + jobsJson + "}");
    }

    /** GET /api/jobs/{id} - Get a specific job */
    private void handleGetJob(HttpExchange exchange, String path) throws IOException {
        String jobId = path.substring("/api/jobs/".length());
        PrintJob job = spooler.getQueueManager().getJobById(jobId);

        if (job != null) {
            sendResponse(exchange, 200, "{\"success\":true,\"job\":" + job.toJson() + "}");
        } else {
            sendResponse(exchange, 404, JsonUtil.errorJson("Job not found: " + jobId));
        }
    }

    /** POST /api/jobs - Submit a new print job */
    private void handleSubmitJob(HttpExchange exchange) throws IOException {
        String body = JsonUtil.readRequestBody(exchange.getRequestBody());
        Map<String, String> params = JsonUtil.parseJsonObject(body);

        String userId = params.get("userId");
        String userName = params.get("userName");
        String documentName = params.get("documentName");
        String pagesStr = params.get("pages");

        // Validate
        if (userId == null || userId.trim().isEmpty()) {
            sendResponse(exchange, 400, JsonUtil.errorJson("User ID is required"));
            return;
        }
        if (userName == null || userName.trim().isEmpty()) {
            sendResponse(exchange, 400, JsonUtil.errorJson("User name is required"));
            return;
        }
        if (documentName == null || documentName.trim().isEmpty()) {
            sendResponse(exchange, 400, JsonUtil.errorJson("Document name is required"));
            return;
        }

        int pages;
        try {
            pages = Integer.parseInt(pagesStr);
            if (pages <= 0) {
                sendResponse(exchange, 400, JsonUtil.errorJson("Pages must be a positive integer"));
                return;
            }
            if (pages > 1000) {
                sendResponse(exchange, 400, JsonUtil.errorJson("Maximum 1000 pages per job"));
                return;
            }
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, JsonUtil.errorJson("Pages must be a valid positive integer"));
            return;
        }

        try {
            PrintJob job = spooler.submitJob(userId, userName, documentName, pages);
            sendResponse(exchange, 201,
                    "{\"success\":true,\"message\":\"Job submitted successfully\",\"job\":" + job.toJson() + "}");
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 400, JsonUtil.errorJson(e.getMessage()));
        }
    }

    /** DELETE /api/jobs/{id} - Cancel a job */
    private void handleCancelJob(HttpExchange exchange, String path) throws IOException {
        String jobId = path.substring("/api/jobs/".length());

        // First check if job exists
        PrintJob job = spooler.getQueueManager().getJobById(jobId);
        if (job == null) {
            sendResponse(exchange, 404, JsonUtil.errorJson("Job not found: " + jobId));
            return;
        }

        // Check if job is in a state that can be cancelled
        if (job.getStatus() == PrintJobStatus.PRINTING) {
            sendResponse(exchange, 400,
                    JsonUtil.errorJson("Cannot cancel job " + jobId + ": Job is currently being printed. Only QUEUED jobs can be cancelled."));
            return;
        }
        if (job.getStatus() == PrintJobStatus.COMPLETED) {
            sendResponse(exchange, 400,
                    JsonUtil.errorJson("Cannot cancel job " + jobId + ": Job is already completed."));
            return;
        }
        if (job.getStatus() == PrintJobStatus.CANCELLED) {
            sendResponse(exchange, 400,
                    JsonUtil.errorJson("Job " + jobId + " is already cancelled."));
            return;
        }

        boolean cancelled = spooler.cancelJob(jobId);
        if (cancelled) {
            sendResponse(exchange, 200,
                    JsonUtil.successJson("Job " + jobId + " cancelled successfully"));
        } else {
            sendResponse(exchange, 400,
                    JsonUtil.errorJson("Failed to cancel job " + jobId));
        }
    }

    /** GET /api/queue - Get current print queue */
    private void handleGetQueue(HttpExchange exchange) throws IOException {
        String queueJson = spooler.getQueueManager().queueToJsonArray();
        sendResponse(exchange, 200,
                "{\"success\":true,\"queue\":" + queueJson + ",\"queueSize\":"
                        + spooler.getQueueManager().getQueueSize() + "}");
    }

    /** POST /api/simulation/start - Start concurrent simulation */
    private void handleSimulation(HttpExchange exchange) throws IOException {
        String body = JsonUtil.readRequestBody(exchange.getRequestBody());
        Map<String, String> params = JsonUtil.parseJsonObject(body);

        int numUsers;
        int jobsPerUser;

        try {
            numUsers = Integer.parseInt(params.getOrDefault("numUsers", "5"));
            jobsPerUser = Integer.parseInt(params.getOrDefault("jobsPerUser", "2"));
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, JsonUtil.errorJson("Invalid simulation parameters"));
            return;
        }

        if (numUsers < 1 || numUsers > 20) {
            sendResponse(exchange, 400, JsonUtil.errorJson("Number of users must be between 1 and 20"));
            return;
        }
        if (jobsPerUser < 1 || jobsPerUser > 10) {
            sendResponse(exchange, 400, JsonUtil.errorJson("Jobs per user must be between 1 and 10"));
            return;
        }

        int totalJobs = spooler.runConcurrentSimulation(numUsers, jobsPerUser);
        sendResponse(exchange, 200,
                "{\"success\":true,\"message\":\"Concurrent simulation started\","
                        + "\"numUsers\":" + numUsers + ","
                        + "\"jobsPerUser\":" + jobsPerUser + ","
                        + "\"expectedTotalJobs\":" + totalJobs + "}");
    }

    /** POST /api/printer/start - Start the printer worker */
    private void handleStartPrinter(HttpExchange exchange) throws IOException {
        spooler.startPrinterWorker();
        sendResponse(exchange, 200, JsonUtil.successJson("Printer worker started"));
    }

    /** POST /api/printer/pause - Pause the printer */
    private void handlePausePrinter(HttpExchange exchange) throws IOException {
        spooler.pausePrinter();
        sendResponse(exchange, 200, JsonUtil.successJson("Printer paused"));
    }

    /** POST /api/printer/resume - Resume the printer */
    private void handleResumePrinter(HttpExchange exchange) throws IOException {
        spooler.resumePrinter();
        sendResponse(exchange, 200, JsonUtil.successJson("Printer resumed"));
    }

    /** GET /api/statistics - Get system statistics */
    private void handleStatistics(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200,
                "{\"success\":true,\"statistics\":" + spooler.getStatsManager().toJson() + "}");
    }

    /** GET /api/logs - Get activity logs */
    private void handleLogs(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200,
                "{\"success\":true,\"logs\":" + spooler.getLogger().toJsonArray() + "}");
    }

    /** GET /api/synchronization - Get synchronization monitor data */
    private void handleSynchronization(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200,
                "{\"success\":true,\"sync\":" + spooler.getSynchronizationStatus() + "}");
    }

    /** POST /api/reset - Reset the entire system */
    private void handleReset(HttpExchange exchange) throws IOException {
        spooler.getLogger().clear();
        spooler.resetSystem();
        sendResponse(exchange, 200, JsonUtil.successJson("System reset successfully"));
    }

    /** POST /api/speed - Set simulation speed */
    private void handleSetSpeed(HttpExchange exchange) throws IOException {
        String body = JsonUtil.readRequestBody(exchange.getRequestBody());
        Map<String, String> params = JsonUtil.parseJsonObject(body);

        String speed = params.getOrDefault("speed", "normal");
        long msPerPage = switch (speed.toLowerCase()) {
            case "fast" -> PrinterSpooler.SPEED_FAST;
            case "slow" -> PrinterSpooler.SPEED_SLOW;
            default -> PrinterSpooler.SPEED_NORMAL;
        };

        spooler.setSimulationSpeed(msPerPage);
        sendResponse(exchange, 200,
                "{\"success\":true,\"message\":\"Speed set to " + speed + "\",\"msPerPage\":" + msPerPage + "}");
    }

    // ===================================================================
    //  RESPONSE HELPER
    // ===================================================================

    /**
     * Send an HTTP response with the given status code and JSON body.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
