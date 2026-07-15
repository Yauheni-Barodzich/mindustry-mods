package scs.admin.client;

import arc.struct.*;

import java.util.*;

/** Simple HJSON flat key/value parse & write for rules.hjson. */
public final class RulesHjson {
    private RulesHjson() {}

    public static ObjectMap<String, String> parse(String text) {
        ObjectMap<String, String> map = new ObjectMap<>();
        if (text == null) return map;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim();
            String val = line.substring(colon + 1).trim();
            // strip trailing comment
            int hash = indexOutsideQuotes(val, '#');
            if (hash >= 0) val = val.substring(0, hash).trim();
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                val = val.substring(1, val.length() - 1);
            }
            map.put(key, val);
        }
        return map;
    }

    public static String write(ObjectMap<String, String> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Mindustry server rules — edited via Server Admin\n");
        sb.append("# Defaults from Rules.java\n\n");

        String lastGroup = "";
        for (RulesFields.Field f : RulesFields.ALL) {
            if (!f.group.equals(lastGroup)) {
                if (!lastGroup.isEmpty()) sb.append('\n');
                sb.append("# --- ").append(f.group).append(" ---\n");
                lastGroup = f.group;
            }
            String v = values.containsKey(f.key) ? values.get(f.key) : f.def;
            sb.append(f.key).append(": ").append(v).append('\n');
        }

        // Preserve unknown keys from original
        ObjectSet<String> known = new ObjectSet<>();
        for (RulesFields.Field f : RulesFields.ALL) known.add(f.key);
        boolean extra = false;
        for (ObjectMap.Entry<String, String> e : values) {
            if (known.contains(e.key)) continue;
            if (!extra) {
                sb.append("\n# --- Extra ---\n");
                extra = true;
            }
            sb.append(e.key).append(": ").append(e.value).append('\n');
        }
        return sb.toString();
    }

    private static int indexOutsideQuotes(String s, char ch) {
        boolean in = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') in = !in;
            if (!in && c == ch) return i;
        }
        return -1;
    }
}
