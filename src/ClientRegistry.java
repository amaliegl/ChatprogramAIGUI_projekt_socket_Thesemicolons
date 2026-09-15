import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {
    // Map normalized username -> writer for that client's connection
    private final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();

    // Register a user and associate a writer so server can send messages to that client
    public boolean registerUser(String username, PrintWriter writer) {
        String normalized = normalizeUsername(username);
        if (normalized == null || writer == null) {
            return false;
        }

        return clients.putIfAbsent(normalized, writer) == null;
    }

    public boolean containsUser(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return false;
        }

        return clients.containsKey(normalized);
    }

    // Return the writer associated with a username, or null if not connected
    public PrintWriter getWriter(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return null;
        }

        return clients.get(normalized);
    }

    public Set<String> getUsers() {
        return Set.copyOf(clients.keySet());
    }

    public boolean unregisterUser(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return false;
        }

        return clients.remove(normalized) != null;
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }

        String normalized = username.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        return normalized.toLowerCase();
    }
}
