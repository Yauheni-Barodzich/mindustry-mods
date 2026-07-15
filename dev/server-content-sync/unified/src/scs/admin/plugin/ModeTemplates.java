package scs.admin.plugin;

import arc.struct.*;
import arc.util.*;

import java.io.*;

/** Built-in rules templates for common game modes. */
public final class ModeTemplates {
    public static final String ID_PVP = "pvp";
    public static final String ID_ATTACK = "attack";
    public static final String ID_SURVIVAL = "survival";

    private static final Seq<Info> ALL = Seq.with(
            new Info(ID_SURVIVAL, "Survival", "mode-survival"),
            new Info(ID_PVP, "PvP", "mode-pvp"),
            new Info(ID_ATTACK, "Attack", "mode-attack")
    );

    private ModeTemplates() {}

    public static Seq<Info> list() {
        return ALL;
    }

    public static Seq<String> ids() {
        Seq<String> out = new Seq<>();
        for (Info i : ALL) out.add(i.id);
        return out;
    }

    public static boolean isKnown(String id) {
        return find(id) != null;
    }

    public static Info find(String id) {
        if (id == null) return null;
        String n = id.trim().toLowerCase();
        for (Info i : ALL) {
            if (i.id.equals(n)) return i;
        }
        return null;
    }

    /** Preset file name without extension, e.g. mode-pvp. */
    public static String presetName(String id) {
        Info info = find(id);
        if (info != null) return info.preset;
        return "mode-" + PresetStore.sanitize(id);
    }

    public static String toJsonArray() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ALL.size; i++) {
            if (i > 0) sb.append(',');
            Info t = ALL.get(i);
            sb.append("{\"id\":\"").append(esc(t.id)).append("\",")
                    .append("\"label\":\"").append(esc(t.label)).append("\",")
                    .append("\"preset\":\"").append(esc(t.preset)).append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    public static String load(String id) throws IOException {
        Info info = find(id);
        if (info == null) throw new FileNotFoundException("Unknown template: " + id);
        String path = "/templates/mode-" + info.id + ".hjson";
        try (InputStream in = ModeTemplates.class.getResourceAsStream(path)) {
            if (in == null) throw new FileNotFoundException(path);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** Writes template content into presets/mode-{id}.hjson (overwrites). */
    public static String install(PresetStore store, String id) throws IOException {
        String content = load(id);
        String name = presetName(id);
        store.writeUser(name, content);
        Log.info("[Admin] Installed mode template '@' as preset '@'", id, name);
        return name;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static final class Info {
        public final String id;
        public final String label;
        public final String preset;

        public Info(String id, String label, String preset) {
            this.id = id;
            this.label = label;
            this.preset = preset;
        }
    }
}
