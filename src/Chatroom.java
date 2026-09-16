import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a chatroom where users can send and receive messages.
 * Each chatroom maintains its own member list and message delivery.
 */
public class Chatroom {
    private final String name;
    // Map username -> PrintWriter for members of this chatroom
    private final Map<String, PrintWriter> members = new ConcurrentHashMap<>();

    public Chatroom(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Adds a user to this chatroom.
     *
     * @param username the username to add
     * @param writer the PrintWriter for sending messages to this user
     * @return true if user was added, false if already a member
     */
    public boolean addMember(String username, PrintWriter writer) {
        String normalized = normalizeUsername(username);
        if (normalized == null || writer == null) {
            return false;
        }

        return members.putIfAbsent(normalized, writer) == null;
    }

    /**
     * Removes a user from this chatroom.
     *
     * @param username the username to remove
     * @return true if user was removed, false if not a member
     */
    public boolean removeMember(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return false;
        }

        return members.remove(normalized) != null;
    }

    /**
     * Removes a user and notifies remaining members that the user left.
     * Also sends a special message to the sole remaining member if exactly one remains.
     *
     * @param username the username leaving
     * @return true if user was removed, false otherwise
     */
    public boolean removeMemberWithNotify(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) return false;

        boolean removed = members.remove(normalized) != null;
        if (!removed) return false;

        // Special-case: the "alle" chatroom is a global broadcast channel. Do not send
        // leave/alone notifications for 'alle' to avoid noisy messages — its semantics
        // are that clients implicitly are part of it while connected. For other chatrooms
        // we still send notifications.
        if (name.equalsIgnoreCase("alle")) {
            return true;
        }

        // Notify remaining members that this user has left
        ServerMessage leaveNotice = new ServerMessage(null, "TEXT", username, name, username + " har forladt " + name + ".");
        notifyMembersExcept(username, leaveNotice);

        int remaining = getMemberCount();
        if (remaining == 1) {
            // Inform the remaining single member that they are alone
            for (Map.Entry<String, PrintWriter> e : members.entrySet()) {
                PrintWriter pw = e.getValue();
                if (pw != null) {
                    ServerMessage alone = new ServerMessage(null, "INFO", "server", name, "Du er nu den eneste tilbage i " + name + ".");
                    synchronized (pw) {
                        pw.println(Protocol.formatServerMessage(alone));
                    }
                }
            }
        }

        return true;
    }

    /**
     * Checks if a user is a member of this chatroom.
     *
     * @param username the username to check
     * @return true if user is a member, false otherwise
     */
    public boolean isMember(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return false;
        }

        return members.containsKey(normalized);
    }

    /**
     * Gets the number of members in this chatroom.
     *
     * @return the number of members
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * Broadcasts a message to all members of this chatroom except the sender.
     *
     * @param sender the username of the sender
     * @param payload the message content
     * @return true if broadcast was successful, false if only sender is online
     */
    public boolean broadcastMessage(String sender, String payload) {
        Collection<String> memberNames = members.keySet();

        // If only the sender is online, chatroom is considered empty for broadcast
        if (memberNames.size() <= 1) {
            return false;
        }

        // Broadcast to all members except sender
        for (String username : memberNames) {
            if (username.equalsIgnoreCase(sender)) {
                continue;
            }

            PrintWriter targetWriter = members.get(username);
            if (targetWriter != null) {
                // Create and send a TEXT message from sender to this chatroom
                ServerMessage message = new ServerMessage(
                        null,
                        "TEXT",
                        sender,
                        name,
                        payload
                );
                // Synchronize on the PrintWriter to avoid interleaved writes from multiple threads
                synchronized (targetWriter) {
                    targetWriter.println(Protocol.formatServerMessage(message));
                }
            }
        }

        return true;
    }

    /**
     * Notify all members except the excluded username using the provided ServerMessage.
     * This method will send the message even if only one member remains.
     *
     * @param excludeUsername username to exclude from notification (may be null)
     * @param message the ServerMessage to send
     */
    public void notifyMembersExcept(String excludeUsername, ServerMessage message) {
        if (message == null) return;
        for (Map.Entry<String, PrintWriter> e : members.entrySet()) {
            String member = e.getKey();
            if (excludeUsername != null && member.equalsIgnoreCase(excludeUsername)) continue;
            PrintWriter pw = e.getValue();
            if (pw != null) {
                synchronized (pw) {
                    pw.println(Protocol.formatServerMessage(message));
                }
            }
        }
    }

    /**
     * Normalizes a username to lowercase.
     *
     * @param username the raw username
     * @return normalized username, or null if invalid
     */
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
