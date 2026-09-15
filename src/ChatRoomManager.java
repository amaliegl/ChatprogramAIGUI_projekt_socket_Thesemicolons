import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple chatrooms and their users.
 * Allows creation, joining, and messaging within named chatrooms.
 */
public class ChatRoomManager {
    // Map of chatroom name -> Chatroom instance
    private final Map<String, Chatroom> chatrooms = new ConcurrentHashMap<>();

    /**
     * Creates a new chatroom with the given name.
     * The chatroom name is normalized to lowercase.
     *
     * @param chatroomName the name of the chatroom to create
     * @return true if chatroom was created, false if it already exists
     */
    public boolean createChatroom(String chatroomName) {
        String normalizedName = normalizeChatroomName(chatroomName);
        if (normalizedName == null) {
            return false;
        }

        // Only create if it doesn't already exist
        return chatrooms.putIfAbsent(normalizedName, new Chatroom(normalizedName)) == null;
    }

    /**
     * Gets an existing chatroom by name.
     *
     * @param chatroomName the name of the chatroom
     * @return the Chatroom instance, or null if it doesn't exist
     */
    public Chatroom getChatroom(String chatroomName) {
        String normalizedName = normalizeChatroomName(chatroomName);
        if (normalizedName == null) {
            return null;
        }

        return chatrooms.get(normalizedName);
    }

    /**
     * Checks if a chatroom exists.
     *
     * @param chatroomName the name of the chatroom
     * @return true if the chatroom exists, false otherwise
     */
    public boolean existsChatroom(String chatroomName) {
        String normalizedName = normalizeChatroomName(chatroomName);
        if (normalizedName == null) {
            return false;
        }

        return chatrooms.containsKey(normalizedName);
    }

    /**
     * Normalizes a chatroom name to lowercase and trims whitespace.
     *
     * @param chatroomName the raw chatroom name
     * @return normalized name, or null if invalid
     */
    private String normalizeChatroomName(String chatroomName) {
        if (chatroomName == null) {
            return null;
        }

        String normalized = chatroomName.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        return normalized.toLowerCase();
    }
}
