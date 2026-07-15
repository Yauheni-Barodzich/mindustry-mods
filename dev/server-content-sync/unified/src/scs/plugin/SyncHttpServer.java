package scs.plugin;

import arc.util.*;
import mindustry.*;
import scs.*;

import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.*;

/** Embedded HTTP server serving sync API. */
public class SyncHttpServer {
    private final ContentIndex index;
    private final SyncConfig config;
    private HttpServer server;
    private int boundPort = -1;

    public SyncHttpServer(ContentIndex index, SyncConfig config) {
        this.index = index;
        this.config = config;
    }

    public void start() throws IOException {
        stop();
        if (!config.enabled) {
            Log.info("[SCS] HTTP disabled by config.");
            return;
        }

        int port = config.resolvePort();
        InetSocketAddress addr = new InetSocketAddress(config.bind, port);
        server = HttpServer.create(addr, 0);
        server.createContext(SyncConstants.API_PREFIX + "/info", this::handleInfo);
        server.createContext(SyncConstants.API_PREFIX + "/manifest", this::handleManifest);
        server.createContext(SyncConstants.API_PREFIX + "/file/", this::handleFile);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "scs-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        boundPort = server.getAddress().getPort();
        Log.info("[SCS] HTTP listening on @:@", config.bind, boundPort);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            boundPort = -1;
        }
    }

    public int getBoundPort() {
        return boundPort;
    }

    private void handleInfo(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "Method Not Allowed");
            return;
        }
        String name = "server";
        try {
            if (Vars.netServer != null && Vars.netServer.admins != null) {
                // Administration may expose server name via settings
            }
            name = CoreSafe.serverName();
        } catch (Throwable ignored) {
        }

        String gameVer;
        try {
            gameVer = mindustry.core.Version.combined();
        } catch (Throwable t) {
            gameVer = String.valueOf(mindustry.core.Version.build);
        }
        String body = "{"
                + "\"syncVersion\":\"" + SyncConstants.SYNC_VERSION + "\","
                + "\"gameVersion\":\"" + escape(gameVer) + "\","
                + "\"serverName\":\"" + escape(name) + "\","
                + "\"mods\":" + index.modCount() + ","
                + "\"maps\":" + index.mapCount()
                + "}";
        send(ex, 200, "application/json; charset=utf-8", body);
    }

    private void handleManifest(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "Method Not Allowed");
            return;
        }
        String body = ManifestJson.writeManifest(index.mods(), index.maps());
        send(ex, 200, "application/json; charset=utf-8", body);
    }

    private void handleFile(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String path = ex.getRequestURI().getPath();
        String prefix = SyncConstants.API_PREFIX + "/file/";
        if (!path.startsWith(prefix)) {
            send(ex, 404, "text/plain", "Not Found");
            return;
        }
        String rest = path.substring(prefix.length());
        // mods/fileName or maps/fileName
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            send(ex, 400, "text/plain", "Bad path");
            return;
        }
        String kind = rest.substring(0, slash);
        String fileName = urlDecode(rest.substring(slash + 1));
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            send(ex, 400, "text/plain", "Invalid file name");
            return;
        }

        ContentIndex.IndexedFile indexed;
        if ("mods".equals(kind)) {
            indexed = index.findMod(fileName);
        } else if ("maps".equals(kind)) {
            indexed = index.findMap(fileName);
        } else {
            send(ex, 404, "text/plain", "Unknown kind");
            return;
        }

        if (indexed == null || indexed.file == null || !indexed.file.exists()) {
            send(ex, 404, "text/plain", "File not found");
            return;
        }

        byte[] data = indexed.file.readBytes();
        ex.getResponseHeaders().add("Content-Type", "application/octet-stream");
        ex.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + indexed.file.name() + "\"");
        ex.getResponseHeaders().add("X-SHA-256", indexed.entry.sha256);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Avoid hard dependency on Core settings layout. */
    private static class CoreSafe {
        static String serverName() {
            try {
                return arc.Core.settings.getString("servername", "Mindustry Server");
            } catch (Throwable t) {
                return "Mindustry Server";
            }
        }
    }
}
