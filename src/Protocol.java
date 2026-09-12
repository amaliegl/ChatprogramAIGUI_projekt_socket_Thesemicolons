public class Protocol {
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
}
