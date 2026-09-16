import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServerMessage {
    private final LocalDateTime timestamp;
    private static final DateTimeFormatter timestampFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final String type;
    private final String sender;
    private final String target;
    private final String payload;

    public ServerMessage(LocalDateTime timestamp, String type, String sender, String target, String payload) {
        this.timestamp = timestamp;
        this.type = type;
        this.sender = sender;
        this.target = target;
        this.payload = payload;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getSender() { return sender; }
    public String getTarget() { return target; }
    public String getPayload() { return payload; }

    // Serialize to TIMESTAMP|TYPE|SENDER|TARGET|PAYLOAD
    public String serialize() {
        // Replace nulls with empty strings and ensure payload is present
        String sTimestamp = timestamp == null ? "" : timestamp.format(timestampFormatter);
        String sType = type == null ? "" : type;
        String sSender = sender == null ? "" : sender;
        String sTarget = target == null ? "" : target;
        String sPayload = payload == null ? "" : payload;
        return String.join("|", sTimestamp, sType, sSender, sTarget, sPayload);
    }



    @Override
    public String toString() {
        return String.format("ServerMessage[timestamp=%s,type=%s,sender=%s,target=%s,payload=%s]",
                timestamp, type, sender, target, payload);
    }
}