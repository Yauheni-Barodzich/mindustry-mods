package scs.client;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import scs.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;

/** Fetches manifest, diffs local files, downloads and verifies. */
public class SyncService {
    public static String defaultUrlFromAddress(String address) {
        if (address == null || address.isEmpty()) return "";
        String host = address.trim();
        int port = 6567;
        // strip scheme if pasted
        if (host.startsWith("http://")) host = host.substring(7);
        if (host.startsWith("https://")) host = host.substring(8);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);

        int colon = host.lastIndexOf(':');
        // IPv6 in brackets
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            if (end > 0) {
                String inner = host.substring(1, end);
                String rest = host.substring(end + 1);
                if (rest.startsWith(":") && rest.length() > 1) {
                    try { port = Integer.parseInt(rest.substring(1)); } catch (Exception ignored) {}
                }
                return "http://" + inner + ":" + (port + 1);
            }
        }

        if (colon > 0 && host.indexOf(':') == colon) {
            try {
                port = Integer.parseInt(host.substring(colon + 1));
                host = host.substring(0, colon);
            } catch (Exception ignored) {
                port = 6567;
            }
        }
        return "http://" + host + ":" + (port + 1);
    }

    public static String resolveBaseUrl(String serverAddress) {
        String override = Core.settings.getString("scs-url-override", "").trim();
        if (!override.isEmpty()) {
            if (override.endsWith("/")) override = override.substring(0, override.length() - 1);
            return override;
        }
        return defaultUrlFromAddress(serverAddress);
    }

    public ManifestJson.ManifestPayload fetchManifest(String baseUrl) throws IOException {
        String body = httpGetString(baseUrl + SyncConstants.API_PREFIX + "/manifest");
        return ManifestJson.parseManifest(body);
    }

    public String fetchInfo(String baseUrl) throws IOException {
        return httpGetString(baseUrl + SyncConstants.API_PREFIX + "/info");
    }

    public DiffResult diff(ManifestJson.ManifestPayload remote) {
        DiffResult result = new DiffResult();
        ObjectMap<String, String> localModHashes = hashDir(Vars.modDirectory, f -> {
            String n = f.name().toLowerCase();
            return n.endsWith(".zip") || n.endsWith(".jar");
        });
        ObjectMap<String, String> localMapHashes = hashDir(Vars.customMapDirectory, f -> {
            String n = f.name().toLowerCase();
            return n.endsWith(".msav") || n.endsWith(".mmap");
        });

        for (ManifestEntry e : remote.mods) {
            String local = localModHashes.get(e.fileName);
            if (local == null) {
                result.toDownload.add(e);
                result.missingMods++;
            } else if (!local.equalsIgnoreCase(e.sha256)) {
                result.toDownload.add(e);
                result.outdatedMods++;
            } else {
                result.okMods++;
            }
        }
        for (ManifestEntry e : remote.maps) {
            String local = localMapHashes.get(e.fileName);
            if (local == null) {
                result.toDownload.add(e);
                result.missingMaps++;
            } else if (!local.equalsIgnoreCase(e.sha256)) {
                result.toDownload.add(e);
                result.outdatedMaps++;
            } else {
                result.okMaps++;
            }
        }

        // extras (content that is local but not on server) — report only
        ObjectSet<String> remoteModFiles = new ObjectSet<>();
        for (ManifestEntry e : remote.mods) remoteModFiles.add(e.fileName);
        for (String localName : localModHashes.keys()) {
            if (!remoteModFiles.contains(localName)
                    && !localName.toLowerCase().contains("server-content-sync")) {
                result.extraModFiles.add(localName);
            }
        }
        return result;
    }

    public void downloadAll(String baseUrl, Seq<ManifestEntry> entries, ProgressCons progress, Runnable onDone, Cons<Throwable> onError) {
        Threads.daemon(() -> {
            try {
                boolean modsChanged = false;
                for (int i = 0; i < entries.size; i++) {
                    ManifestEntry e = entries.get(i);
                    float base = i / (float) Math.max(1, entries.size);
                    downloadOne(baseUrl, e, p -> {
                        if (progress != null) progress.get(e, base + p / Math.max(1, entries.size));
                    });
                    if ("mod".equals(e.kind)) modsChanged = true;
                }
                if (progress != null && entries.size > 0) {
                    progress.get(entries.peek(), 1f);
                }
                boolean finalModsChanged = modsChanged;
                Core.app.post(() -> {
                    Core.settings.put("scs-last-mods-changed", finalModsChanged);
                    if (onDone != null) onDone.run();
                });
            } catch (Throwable t) {
                Core.app.post(() -> {
                    if (onError != null) onError.get(t);
                });
            }
        });
    }

    private void downloadOne(String baseUrl, ManifestEntry e, Cons<Float> progress) throws IOException {
        String kindPath = "map".equals(e.kind) ? "maps" : "mods";
        String url = baseUrl + SyncConstants.API_PREFIX + "/file/" + kindPath + "/"
                + URLEncoder.encode(e.fileName, StandardCharsets.UTF_8).replace("+", "%20");

        Fi targetDir = "map".equals(e.kind) ? Vars.customMapDirectory : Vars.modDirectory;
        targetDir.mkdirs();
        Fi temp = targetDir.child(e.fileName + ".scs-download");
        Fi dest = targetDir.child(e.fileName);
        if (temp.exists()) temp.delete();

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code + " for " + e.fileName);
        }

        long expected = e.size > 0 ? e.size : conn.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new BufferedOutputStream(temp.write(false))) {
            byte[] buf = new byte[8192];
            long read = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                read += n;
                if (progress != null && expected > 0) {
                    progress.get(Math.min(1f, read / (float) expected));
                } else if (progress != null) {
                    progress.get(0.5f);
                }
            }
        } finally {
            conn.disconnect();
        }

        String hash = HashUtils.sha256(temp.file());
        if (!hash.equalsIgnoreCase(e.sha256)) {
            temp.delete();
            throw new IOException("Hash mismatch for " + e.fileName + " (expected " + e.sha256 + ", got " + hash + ")");
        }

        if (dest.exists()) dest.delete();
        temp.moveTo(dest);

        if ("map".equals(e.kind)) {
            Core.app.post(() -> {
                try {
                    Vars.maps.reload();
                } catch (Throwable ignored) {
                }
            });
        }
    }

    private ObjectMap<String, String> hashDir(Fi dir, Boolf<Fi> filter) {
        ObjectMap<String, String> map = new ObjectMap<>();
        if (dir == null || !dir.exists()) return map;
        for (Fi f : dir.list()) {
            if (f.isDirectory() || !filter.get(f)) continue;
            try {
                map.put(f.name(), HashUtils.sha256(f.file()));
            } catch (Exception e) {
                Log.err("[SCS] Local hash failed for @", f.name());
            }
        }
        return map;
    }

    private String httpGetString(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) throw new IOException("HTTP " + code);
        try (InputStream in = stream) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (code != 200) throw new IOException("HTTP " + code + ": " + body);
            return body;
        } finally {
            conn.disconnect();
        }
    }

    public static class DiffResult {
        public final Seq<ManifestEntry> toDownload = new Seq<>();
        public final Seq<String> extraModFiles = new Seq<>();
        public int missingMods, outdatedMods, okMods;
        public int missingMaps, outdatedMaps, okMaps;

        public long totalBytes() {
            long s = 0;
            for (ManifestEntry e : toDownload) s += e.size;
            return s;
        }

        public boolean needsWork() {
            return toDownload.size > 0;
        }
    }

    public interface ProgressCons {
        void get(ManifestEntry entry, float progress);
    }
}
