package scs.admin.plugin;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;

import java.io.*;

/** Stores rules presets under config/presets/. */
public class PresetStore {
    public static final String DEFAULT_NAME = "default";

    public Fi dir() {
        Fi d = Vars.dataDirectory.child("presets");
        d.mkdirs();
        return d;
    }

    public void ensureDefault(Fi rulesFile, Runnable writeDefaultsToRules) {
        Fi def = file(DEFAULT_NAME);
        if (def.exists()) return;
        if (rulesFile != null && rulesFile.exists()) {
            rulesFile.copyTo(def);
            Log.info("[Admin] Created default preset from rules.hjson");
            return;
        }
        if (writeDefaultsToRules != null) {
            writeDefaultsToRules.run();
            if (rulesFile != null && rulesFile.exists()) {
                rulesFile.copyTo(def);
                return;
            }
        }
        def.writeString("# default rules preset\nreactorExplosions: false\nlogicUnitBuild: false\nlogicUnitDeconstruct: false\n");
        Log.info("[Admin] Created empty default preset");
    }

    public Fi file(String name) {
        String safe = sanitize(name);
        return dir().child(safe + ".hjson");
    }

    public static String sanitize(String name) {
        if (name == null) return "";
        String n = name.trim().toLowerCase();
        n = n.replaceAll("[^a-z0-9_\\-]", "");
        if (n.isEmpty()) n = "preset";
        if (n.length() > 48) n = n.substring(0, 48);
        return n;
    }

    public static boolean isProtected(String name) {
        return DEFAULT_NAME.equalsIgnoreCase(sanitize(name));
    }

    public Seq<PresetInfo> list() {
        Seq<PresetInfo> out = new Seq<>();
        for (Fi f : dir().list()) {
            if (f.isDirectory()) continue;
            String n = f.name();
            if (!n.endsWith(".hjson")) continue;
            String id = n.substring(0, n.length() - ".hjson".length());
            out.add(new PresetInfo(id, isProtected(id), f.length()));
        }
        out.sort((a, b) -> {
            if (isProtected(a.name) && !isProtected(b.name)) return -1;
            if (!isProtected(a.name) && isProtected(b.name)) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        return out;
    }

    public String read(String name) throws IOException {
        Fi f = file(name);
        if (!f.exists()) throw new FileNotFoundException(name);
        return f.readString();
    }

    /**
     * Writes a user preset. Refuses to overwrite the protected {@code default} —
     * use {@link #updateDefault(String)} instead.
     */
    public void write(String name, String content) throws IOException {
        if (isProtected(name)) {
            throw new IOException("Cannot overwrite default via save; use update-default");
        }
        writeUser(name, content);
    }

    /** Writes any preset name including user mode templates (not for default). */
    public void writeUser(String name, String content) {
        if (content == null) content = "";
        file(name).writeString(content);
    }

    /** Explicitly replace the protected default preset. */
    public void updateDefault(String content) {
        if (content == null) content = "";
        file(DEFAULT_NAME).writeString(content);
        Log.info("[Admin] Updated default preset");
    }

    public void delete(String name) throws IOException {
        if (isProtected(name)) throw new IOException("Cannot delete default preset");
        Fi f = file(name);
        if (!f.exists()) throw new FileNotFoundException(name);
        f.delete();
    }

    public void applyTo(String name, Fi rulesFile) throws IOException {
        String content = read(name);
        rulesFile.writeString(content);
    }

    public static class PresetInfo {
        public final String name;
        public final boolean protectedPreset;
        public final long size;

        public PresetInfo(String name, boolean protectedPreset, long size) {
            this.name = name;
            this.protectedPreset = protectedPreset;
            this.size = size;
        }
    }
}
