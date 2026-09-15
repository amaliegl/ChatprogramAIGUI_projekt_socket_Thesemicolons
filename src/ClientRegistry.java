import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {
    private final Set<String> users = ConcurrentHashMap.newKeySet();

    public boolean registerUser(String username) {
        if (username == null) {
            return false;
        }

        String normalized = username.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        return users.add(normalized);
    }

    public boolean containsUser(String username) {
        if (username == null) {
            return false;
        }

        return users.contains(username.trim());
    }

    public Set<String> getUsers() {
        return Set.copyOf(users);
    }
}
