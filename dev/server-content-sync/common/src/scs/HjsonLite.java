package scs;

import arc.util.serialization.*;

/**
 * Minimal HJSON support for Mindustry-style config files:
 * strip # comments and wrap bare key/values in braces for JsonReader.
 */
public final class HjsonLite {
    private HjsonLite() {}

    public static JsonValue parse(String text) {
        String cleaned = stripComments(text == null ? "" : text).trim();
        if (cleaned.isEmpty()) cleaned = "{}";
        if (!cleaned.startsWith("{")) {
            cleaned = "{" + cleaned + "}";
        }
        return new JsonReader().parse(cleaned);
    }

    public static String stripComments(String text) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("#")) continue;
            int hash = line.indexOf('#');
            if (hash >= 0 && !line.substring(0, hash).contains("\"")) {
                cleaned.append(line, 0, hash).append('\n');
            } else {
                cleaned.append(line).append('\n');
            }
        }
        return cleaned.toString();
    }
}
