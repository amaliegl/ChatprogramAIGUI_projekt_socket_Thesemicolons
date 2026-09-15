import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ClientRegistry {
    private final Set<String> users = new HashSet<>();

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
        return Collections.unmodifiableSet(new HashSet<>(users));
    }
}
