import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;

/**
 * Represents a chatroom that can broadcast messages to all connected clients.
 * Currently supports the "alle" (all) chatroom where all active users receive messages.
 */
public class Chatroom {
    private final String name;
    private final ClientRegistry clientRegistry;

    public Chatroom(String name, ClientRegistry clientRegistry) {
        this.name = name;
        this.clientRegistry = clientRegistry;
    }

    public String getName() {
        return name;
    }

    /**
     * Broadcasts a message to all users in this chatroom.
     * For the "alle" chatroom, this includes all active users.
     *
     * @param sender the username of the sender
     * @param payload the message content
     * @return true if broadcast was successful, false if chatroom is empty (only sender online)
     */
    public boolean broadcastMessage(String sender, String payload) {
        Collection<String> activeUsers = clientRegistry.getUsers();

        // If only the sender is online, chatroom is considered empty
        if (activeUsers.size() <= 1) {
            return false;
        }

        // Broadcast to all active users
        for (String username : activeUsers) {
            // Skip sending to self
            if (username.equalsIgnoreCase(sender)) {
                continue;
            }

            PrintWriter targetWriter = clientRegistry.getWriter(username);
            if (targetWriter != null) {
                // Create and send a TEXT message from sender to this user
                ServerMessage message = new ServerMessage(
                        null,
                        "TEXT",
                        sender,
                        "alle",
                        payload
                );
                targetWriter.println(Protocol.formatServerMessage(message));
            }
        }

        return true;
    }
}
