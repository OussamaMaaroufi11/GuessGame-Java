import java.util.ArrayList;
import java.util.List;

public class MessageParser {

    private static final String EXPECTED_PREFIX = "GG";

    private MessageParser() {
        // classe utilitaire
    }

    public static GGMessage parse(String rawMessage) {
        if (rawMessage == null) {
            return null;
        }

        String cleaned = rawMessage.trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        String[] parts = cleaned.split("\\|", -1);
        if (parts.length < 2) {
            return null;
        }

        String prefix = parts[0] == null ? "" : parts[0].trim();
        String type = parts[1] == null ? "" : parts[1].trim();

        if (!EXPECTED_PREFIX.equalsIgnoreCase(prefix) || type.isEmpty()) {
            return null;
        }

        List<String> fields = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            fields.add(parts[i] == null ? "" : parts[i].trim());
        }

        return new GGMessage(cleaned, prefix, type, fields);
    }
}