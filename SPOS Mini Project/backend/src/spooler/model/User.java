package spooler.model;

/**
 * User - Represents a user/process that submits print jobs.
 * 
 * SPOS Concept: Each user acts as a "producer" in the producer-consumer model.
 * Multiple users can concurrently submit print jobs (multiple producers),
 * while the printer is the single consumer processing those jobs.
 */
public class User {
    private final String userId;
    private final String userName;

    public User(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Converts this User object to a JSON string.
     */
    public String toJson() {
        return String.format("{\"userId\":\"%s\",\"userName\":\"%s\"}",
                escapeJson(userId), escapeJson(userName));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return "User{userId='" + userId + "', userName='" + userName + "'}";
    }
}
