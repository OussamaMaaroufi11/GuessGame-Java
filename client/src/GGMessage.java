import java.util.List;

public class GGMessage {
    private final String rawMessage;
    private final String prefix;
    private final String type;
    private final List<String> fields;

    public GGMessage(String rawMessage, String prefix, String type, List<String> fields) {
        this.rawMessage = rawMessage == null ? "" : rawMessage;
        this.prefix = prefix == null ? "" : prefix;
        this.type = type == null ? "" : type;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getType() {
        return type;
    }

    public List<String> getFields() {
        return fields;
    }

    public String getField(int index) {
        if (index < 0 || index >= fields.size()) {
            return null;
        }
        return fields.get(index);
    }

    public int getFieldCount() {
        return fields.size();
    }

    @Override
    public String toString() {
        return "GGMessage{" +
                "rawMessage='" + rawMessage + '\'' +
                ", prefix='" + prefix + '\'' +
                ", type='" + type + '\'' +
                ", fields=" + fields +
                '}';
    }
}