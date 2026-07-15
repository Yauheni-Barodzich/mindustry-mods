package scs;

import arc.struct.*;
import arc.util.serialization.*;

/** JSON helpers for manifest payloads. */
public final class ManifestJson {
    private ManifestJson() {}

    public static String writeManifest(Seq<ManifestEntry> mods, Seq<ManifestEntry> maps) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"mods\":[");
        appendEntries(sb, mods);
        sb.append("],\"maps\":[");
        appendEntries(sb, maps);
        sb.append("]}");
        return sb.toString();
    }

    private static void appendEntries(StringBuilder sb, Seq<ManifestEntry> entries) {
        for (int i = 0; i < entries.size; i++) {
            if (i > 0) sb.append(',');
            ManifestEntry e = entries.get(i);
            sb.append('{');
            field(sb, "id", e.id, true);
            field(sb, "fileName", e.fileName, true);
            field(sb, "version", e.version == null ? "" : e.version, true);
            field(sb, "sha256", e.sha256, true);
            sb.append("\"size\":").append(e.size).append(',');
            field(sb, "kind", e.kind, false);
            sb.append('}');
        }
    }

    private static void field(StringBuilder sb, String key, String value, boolean comma) {
        sb.append('"').append(key).append("\":\"").append(escape(value)).append('"');
        if (comma) sb.append(',');
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static ManifestPayload parseManifest(String body) {
        JsonValue root = new JsonReader().parse(body);
        ManifestPayload out = new ManifestPayload();
        out.mods = parseList(root.get("mods"));
        out.maps = parseList(root.get("maps"));
        return out;
    }

    private static Seq<ManifestEntry> parseList(JsonValue arr) {
        Seq<ManifestEntry> list = new Seq<>();
        if (arr == null || !arr.isArray()) return list;
        for (JsonValue v = arr.child; v != null; v = v.next) {
            ManifestEntry e = new ManifestEntry();
            e.id = v.getString("id", "");
            e.fileName = v.getString("fileName", "");
            e.version = v.getString("version", "");
            e.sha256 = v.getString("sha256", "");
            e.size = v.getLong("size", 0);
            e.kind = v.getString("kind", "mod");
            list.add(e);
        }
        return list;
    }

    public static class ManifestPayload {
        public Seq<ManifestEntry> mods = new Seq<>();
        public Seq<ManifestEntry> maps = new Seq<>();
    }
}
