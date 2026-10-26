package spooler.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * JsonUtil - Simple JSON parsing and generation utility.
 * 
 * This class provides basic JSON parsing without external libraries,
 * keeping the project self-contained and easy to compile.
 * 
 * Note: This is a simplified parser sufficient for the flat JSON objects
 * used in this project. For production use, a library like Gson or
 * Jackson would be preferred.
 */
public class JsonUtil {

    /**
     * Read the entire body from an InputStream as a String.
     */
    public static String readRequestBody(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * Parse a flat JSON object string into a Map of key-value pairs.
     * Handles string values, numeric values, boolean values, and null.
     * 
     * Example: {"name":"John","age":25} → {name=John, age=25}
     */
    public static Map<String, String> parseJsonObject(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        json = json.trim();
        // Remove outer braces
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1).trim();
        }
        if (json.isEmpty()) return map;

        // Simple state-machine parser for flat JSON objects
        int i = 0;
        while (i < json.length()) {
            // Skip whitespace and commas
            while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ',' 
                    || json.charAt(i) == '\n' || json.charAt(i) == '\r' || json.charAt(i) == '\t')) {
                i++;
            }
            if (i >= json.length()) break;

            // Parse key (must be a quoted string)
            if (json.charAt(i) != '"') { i++; continue; }
            i++; // skip opening quote
            StringBuilder key = new StringBuilder();
            while (i < json.length() && json.charAt(i) != '"') {
                if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                    key.append(json.charAt(i + 1));
                    i += 2;
                } else {
                    key.append(json.charAt(i));
                    i++;
                }
            }
            i++; // skip closing quote

            // Skip colon and whitespace
            while (i < json.length() && (json.charAt(i) == ':' || json.charAt(i) == ' ')) {
                i++;
            }
            if (i >= json.length()) break;

            // Parse value
            String value;
            if (json.charAt(i) == '"') {
                // String value
                i++; // skip opening quote
                StringBuilder val = new StringBuilder();
                while (i < json.length() && json.charAt(i) != '"') {
                    if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                        val.append(json.charAt(i + 1));
                        i += 2;
                    } else {
                        val.append(json.charAt(i));
                        i++;
                    }
                }
                value = val.toString();
                i++; // skip closing quote
            } else if (json.charAt(i) == 'n' && json.startsWith("null", i)) {
                value = null;
                i += 4;
            } else if (json.charAt(i) == 't' && json.startsWith("true", i)) {
                value = "true";
                i += 4;
            } else if (json.charAt(i) == 'f' && json.startsWith("false", i)) {
                value = "false";
                i += 5;
            } else {
                // Numeric value
                StringBuilder val = new StringBuilder();
                while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}' 
                        && json.charAt(i) != ' ' && json.charAt(i) != '\n') {
                    val.append(json.charAt(i));
                    i++;
                }
                value = val.toString();
            }

            map.put(key.toString(), value);
        }
        return map;
    }

    /**
     * Create a simple JSON error response.
     */
    public static String errorJson(String message) {
        return "{\"success\":false,\"error\":\"" + escapeJson(message) + "\"}";
    }

    /**
     * Create a simple JSON success response.
     */
    public static String successJson(String message) {
        return "{\"success\":true,\"message\":\"" + escapeJson(message) + "\"}";
    }

    /**
     * Create a JSON success response with data.
     */
    public static String successJsonWithData(String message, String dataJson) {
        return "{\"success\":true,\"message\":\"" + escapeJson(message) + "\",\"data\":" + dataJson + "}";
    }

    /**
     * Escape a string for safe inclusion in JSON.
     */
    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
