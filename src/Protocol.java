import java.time.Instant;

public class Protocol {
    // Client-side parse: TYPE|TARGET|PAYLOAD
    public static Message parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty message");
        }
        String[] parts = line.split("\\|", 3);
        String type = parts.length > 0 ? parts[0].trim() : "";
        String target = parts.length > 1 ? parts[1].trim() : "";
        String payload = parts.length > 2 ? parts[2].trim() : "";
        if (type.isEmpty()) {
            throw new IllegalArgumentException("Missing TYPE");
        }
        return new Message(type, target, payload);
    }

    // Server-side: TIMESTAMP|TYPE|SENDER|TARGET|PAYLOAD
    public static ServerMessage parseServer(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty server message");
        }
        String[] parts = line.split("\\|", 5);
        String timestamp = parts.length > 0 ? parts[0].trim() : "";
        String type = parts.length > 1 ? parts[1].trim() : "";
        String sender = parts.length > 2 ? parts[2].trim() : "";
        String target = parts.length > 3 ? parts[3].trim() : "";
        String payload = parts.length > 4 ? parts[4].trim() : "";
        if (timestamp.isEmpty() || type.isEmpty() || payload.isEmpty()) {
            throw new IllegalArgumentException("Server message missing required fields");
        }
        return new ServerMessage(timestamp, type, sender, target, payload);
    }

    public static String formatServerMessage(ServerMessage msg) {
        // Ensure timestamp exists
        String ts = msg.getTimestamp();
        if (ts == null || ts.isEmpty()) {
            ts = Instant.now().toString();
        }
        ServerMessage out = new ServerMessage(ts, msg.getType(), msg.getSender(), msg.getTarget(), msg.getPayload());
        return out.serialize();
    }
}
