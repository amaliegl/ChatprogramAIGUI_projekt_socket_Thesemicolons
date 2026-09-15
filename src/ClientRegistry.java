import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {
    private final Set<String> users = ConcurrentHashMap.newKeySet();

    public boolean registerUser(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return false;
        }

        return users.add(normalized);
    }

    public boolean containsUser(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return false;
        }

        return users.contains(normalized);
    }

    public Set<String> getUsers() {
        return Set.copyOf(users);
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
