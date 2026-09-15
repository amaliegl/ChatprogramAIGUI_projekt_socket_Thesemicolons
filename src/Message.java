public class Message {
    private final String type;
    private final String target;
    private final String payload;

    public Message(String type, String target, String payload) {
        this.type = type;
        this.target = target;
        this.payload = payload;
    }

    public String getType() { return type; }
    public String getTarget() { return target; }
    public String getPayload() { return payload; }

    @Override
    public String toString() {
        return String.format("Message[type=%s,target=%s,payload=%s]", type, target, payload);
    }
}
