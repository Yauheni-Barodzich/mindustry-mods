package scs.plugin;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.mod.*;
import scs.*;

import java.io.*;
import java.util.zip.*;

/** Scans mod/map directories and builds a syncable manifest. */
public class ContentIndex {
    private final ObjectMap<String, IndexedFile> modsByName = new ObjectMap<>();
    private final ObjectMap<String, IndexedFile> mapsByName = new ObjectMap<>();
    private final Seq<ManifestEntry> modEntries = new Seq<>();
    private final Seq<ManifestEntry> mapEntries = new Seq<>();

    public synchronized void rebuild() {
        modsByName.clear();
        mapsByName.clear();
        modEntries.clear();
        mapEntries.clear();

        scanMods();
        scanMaps();
        Log.info("[SCS] Index rebuilt: @ mods, @ maps", modEntries.size, mapEntries.size);
    }

    public synchronized Seq<ManifestEntry> mods() {
        return modEntries.copy();
    }

    public synchronized Seq<ManifestEntry> maps() {
        return mapEntries.copy();
    }

    public synchronized IndexedFile findMod(String fileName) {
        return modsByName.get(fileName);
    }

    public synchronized IndexedFile findMap(String fileName) {
        return mapsByName.get(fileName);
    }

    public synchronized int modCount() {
        return modEntries.size;
    }

    public synchronized int mapCount() {
        return mapEntries.size;
    }

    private void scanMods() {
        Fi dir = Vars.modDirectory;
        if (dir == null || !dir.exists()) return;

        for (Fi file : dir.list()) {
            String name = file.name();
            if (file.isDirectory()) continue;
            if (!(name.endsWith(".zip") || name.endsWith(".jar"))) continue;

            try {
                ModMeta meta = readMeta(file);
                if (meta == null) {
                    // bare file without meta — skip
                    continue;
                }
                if (shouldSkipMod(meta, file)) continue;

                String hash = HashUtils.sha256(file.file());
                String id = meta.name == null ? stripExt(name) : meta.name;
                String version = meta.version == null ? "" : meta.version;
                ManifestEntry entry = new ManifestEntry(id, name, version, hash, file.length(), "mod");
                modsByName.put(name, new IndexedFile(file, entry));
                modEntries.add(entry);
            } catch (Exception e) {
                Log.err("[SCS] Failed to index mod @", name);
                Log.err(e);
            }
        }
    }

    private void scanMaps() {
        Fi dir = Vars.customMapDirectory;
        if (dir == null || !dir.exists()) return;

        for (Fi file : dir.list()) {
            if (file.isDirectory()) continue;
            String name = file.name();
            String lower = name.toLowerCase();
            if (!(lower.endsWith(".msav") || lower.endsWith(".mmap"))) continue;

            try {
                String hash = HashUtils.sha256(file.file());
                String id = stripExt(name);
                ManifestEntry entry = new ManifestEntry(id, name, "", hash, file.length(), "map");
                mapsByName.put(name, new IndexedFile(file, entry));
                mapEntries.add(entry);
            } catch (Exception e) {
                Log.err("[SCS] Failed to index map @", name);
                Log.err(e);
            }
        }
    }

    private boolean shouldSkipMod(ModMeta meta, Fi file) {
        if (ScsConstants.isInfraModName(meta.name)) return true;
        if (meta.hidden) return true;
        // plugin.json / Plugin subclasses are server-only
        if (hasPluginMeta(file)) return true;
        return false;
    }

    private boolean hasPluginMeta(Fi file) {
        try (ZipInputStream zis = new ZipInputStream(file.read())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String n = entry.getName();
                if (n.equals("plugin.hjson") || n.equals("plugin.json")
                        || n.endsWith("/plugin.hjson") || n.endsWith("/plugin.json")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private ModMeta readMeta(Fi file) {
        byte[] bytes = readZipEntry(file, "mod.hjson");
        if (bytes == null) bytes = readZipEntry(file, "mod.json");
        if (bytes == null) bytes = readZipEntry(file, "plugin.hjson");
        if (bytes == null) bytes = readZipEntry(file, "plugin.json");
        if (bytes == null) {
            // nested GitHub zip: look one level deep
            bytes = readFirstNestedMeta(file);
        }
        if (bytes == null) return null;

        try {
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            // strip HJSON comments roughly by using JsonReader after simple cleanup is hard;
            // Mindustry's Jval can parse hjson-ish — use JsonReader with hjson via Vars...
            // Prefer arc's JsonValue via mindustry's JsonIO if available.
            return parseMeta(text);
        } catch (Exception e) {
            return null;
        }
    }

    private ModMeta parseMeta(String text) {
        String cleaned = stripComments(text).trim();
        // Mindustry mod.hjson is often bare key/values without surrounding braces
        if (!cleaned.startsWith("{")) {
            cleaned = "{" + cleaned + "}";
        }
        JsonValue root = new JsonReader().parse(cleaned);
        ModMeta meta = new ModMeta();
        meta.name = root.getString("name", null);
        meta.displayName = root.getString("displayName", meta.name);
        meta.version = root.getString("version", "1.0");
        if (meta.version == null || meta.version.isEmpty()) {
            meta.version = String.valueOf(root.getFloat("version", 1f));
        }
        meta.hidden = root.getBoolean("hidden", false);
        meta.main = root.getString("main", null);
        meta.minGameVersion = root.getString("minGameVersion", null);
        if (meta.minGameVersion == null) {
            meta.minGameVersion = String.valueOf((int) root.getFloat("minGameVersion", 0));
        }
        meta.description = root.getString("description", "");
        meta.author = root.getString("author", "");
        return meta;
    }

    private static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
                out.append(c);
                continue;
            }
            if (!inString) {
                if (c == '#') {
                    while (i < text.length() && text.charAt(i) != '\n') i++;
                    if (i < text.length()) out.append('\n');
                    continue;
                }
                if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                    while (i < text.length() && text.charAt(i) != '\n') i++;
                    if (i < text.length()) out.append('\n');
                    continue;
                }
                if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < text.length() && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private byte[] readZipEntry(Fi file, String entryName) {
        try (ZipInputStream zis = new ZipInputStream(file.read())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName) || entry.getName().endsWith("/" + entryName)) {
                    return readAll(zis);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private byte[] readFirstNestedMeta(Fi file) {
        String[] names = {"mod.hjson", "mod.json", "plugin.hjson", "plugin.json"};
        try (ZipInputStream zis = new ZipInputStream(file.read())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String n = entry.getName();
                if (n.contains("..")) continue;
                int slash = n.indexOf('/');
                if (slash < 0) continue;
                String rest = n.substring(slash + 1);
                if (rest.contains("/")) continue;
                for (String want : names) {
                    if (rest.equals(want)) {
                        return readAll(zis);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    public static class IndexedFile {
        public final Fi file;
        public final ManifestEntry entry;

        public IndexedFile(Fi file, ManifestEntry entry) {
            this.file = file;
            this.entry = entry;
        }
    }

    /** Minimal meta fields we care about. */
    public static class ModMeta {
        public String name, displayName, description, author, version, main, minGameVersion;
        public boolean hidden;
    }
}
