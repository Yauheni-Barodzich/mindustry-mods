package scs.admin.plugin;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.gen.*;
import scs.*;

import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated admin HTTP API. Password file is never served. */
public class AdminHttpServer {
    private final AdminAuth auth;
    private final AdminConfig config;
    private final SpawnClamp spawnClamp;
    private final PresetStore presets = new PresetStore();
    private final long startedAt = System.currentTimeMillis();
    private HttpServer server;
    private int boundPort = -1;

    public AdminHttpServer(AdminAuth auth, AdminConfig config, SpawnClamp spawnClamp) {
        this.auth = auth;
        this.config = config;
        this.spawnClamp = spawnClamp;
    }

    public void start() throws IOException {
        stop();
        if (!config.enabled) {
            Log.info("[Admin] HTTP disabled.");
            return;
        }
        // ensure rules + default preset exist
        Fi rules = rulesFile();
        if (!rules.exists()) {
            ensureDefaultRules(rules);
        }
        presets.ensureDefault(rules, () -> ensureDefaultRules(rules));

        int port = config.resolvePort();
        server = HttpServer.create(new InetSocketAddress(config.bind, port), 0);
        String p = AdminConstants.API_PREFIX;
        server.createContext(p + "/login", this::handleLogin);
        server.createContext(p + "/status", ex -> withAuth(ex, this::handleStatus));
        server.createContext(p + "/restart", ex -> withAuth(ex, this::handleRestart));
        server.createContext(p + "/broadcast", ex -> withAuth(ex, this::handleBroadcast));
        server.createContext(p + "/spawn-clamp", ex -> withAuth(ex, this::handleSpawnClamp));
        server.createContext(p + "/spawn-clamp/apply", ex -> withAuth(ex, this::handleSpawnClampApply));
        server.createContext(p + "/mods", ex -> withAuth(ex, this::handleMods));
        server.createContext(p + "/mods/", ex -> withAuth(ex, this::handleModItem));
        server.createContext(p + "/maps", ex -> withAuth(ex, this::handleMaps));
        server.createContext(p + "/maps/", ex -> withAuth(ex, this::handleMapItem));
        server.createContext(p + "/configs", ex -> withAuth(ex, this::handleConfigs));
        server.createContext(p + "/configs/", ex -> withAuth(ex, this::handleConfigFile));
        server.createContext(p + "/rules", ex -> withAuth(ex, this::handleRules));
        server.createContext(p + "/rules/apply", ex -> withAuth(ex, this::handleRulesApply));
        server.createContext(p + "/rules/reset", ex -> withAuth(ex, this::handleRulesReset));
        server.createContext(p + "/presets", ex -> withAuth(ex, this::handlePresets));
        server.createContext(p + "/presets/", ex -> withAuth(ex, this::handlePresetItem));
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "admin-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        boundPort = server.getAddress().getPort();
        Log.info("[Admin] HTTP listening on @:@ (password: @)", config.bind, boundPort, auth.getPasswordFile().absolutePath());
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

    private void handleLogin(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        String body = readBody(ex);
        String password = jsonField(body, "password");
        var token = auth.login(password);
        if (token.isEmpty()) {
            json(ex, 401, "{\"error\":\"Invalid password or password file not configured\"}");
            return;
        }
        json(ex, 200, "{\"token\":\"" + token.get() + "\",\"expiresIn\":" + config.sessionTtlSec + "}");
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        String gameVer;
        try {
            gameVer = mindustry.core.Version.combined();
        } catch (Throwable t) {
            gameVer = String.valueOf(mindustry.core.Version.build);
        }
        int players = 0;
        try {
            players = Groups.player.size();
        } catch (Throwable ignored) {
        }
        boolean hosting = false;
        String mapName = "";
        String mode = "";
        try {
            hosting = Vars.state.isGame();
            if (Vars.state.map != null) mapName = Vars.state.map.name();
            if (Vars.state.rules != null) {
                if (Vars.state.rules.pvp) mode = "pvp";
                else if (Vars.state.rules.attackMode) mode = "attack";
                else mode = "survival";
            }
        } catch (Throwable ignored) {
        }
        int port = Vars.port;
        try {
            port = arc.Core.settings.getInt("port", Vars.port);
        } catch (Throwable ignored) {
        }
        String serverName = "Mindustry Server";
        try {
            serverName = arc.Core.settings.getString("servername", serverName);
        } catch (Throwable ignored) {
        }
        long uptimeSec = (System.currentTimeMillis() - startedAt) / 1000;
        String body = "{"
                + "\"adminVersion\":\"" + AdminConstants.ADMIN_VERSION + "\","
                + "\"gameVersion\":\"" + esc(gameVer) + "\","
                + "\"serverName\":\"" + esc(serverName) + "\","
                + "\"port\":" + port + ","
                + "\"hosting\":" + hosting + ","
                + "\"map\":\"" + esc(mapName) + "\","
                + "\"mode\":\"" + esc(mode) + "\","
                + "\"players\":" + players + ","
                + "\"uptimeSec\":" + uptimeSec + ","
                + "\"spawnClampEnabled\":" + config.spawnClampEnabled + ","
                + "\"maxEnemySpawns\":" + config.maxEnemySpawns + ","
                + "\"minEnemySpawns\":" + config.minEnemySpawns + ","
                + "\"mods\":" + listFilesJson(Vars.modDirectory, ".zip", ".jar") + ","
                + "\"maps\":" + listFilesJson(Vars.customMapDirectory, ".msav", ".mmap")
                + "}";
        json(ex, 200, body);
    }

    private void handleSpawnClamp(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            json(ex, 200, spawnClampJson());
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            String body = readBody(ex);
            try {
                if (body != null && !body.isBlank()) {
                    JsonValue root = new JsonReader().parse(body);
                    if (root.has("spawnClampEnabled")) {
                        config.spawnClampEnabled = root.getBoolean("spawnClampEnabled");
                    }
                    if (root.has("maxEnemySpawns")) {
                        config.maxEnemySpawns = root.getInt("maxEnemySpawns");
                    }
                    if (root.has("minEnemySpawns")) {
                        config.minEnemySpawns = root.getInt("minEnemySpawns");
                    }
                }
                config.save();
                json(ex, 200, spawnClampJson());
            } catch (Exception e) {
                json(ex, 400, "{\"error\":\"" + esc(String.valueOf(e.getMessage())) + "\"}");
            }
            return;
        }
        json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
    }

    private void handleSpawnClampApply(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        if (spawnClamp == null) {
            json(ex, 500, "{\"error\":\"Spawn clamp not available\"}");
            return;
        }
        arc.Core.app.post(() -> {
            try {
                spawnClamp.clamp();
            } catch (Throwable t) {
                Log.err("[Admin] Spawn clamp apply failed", t);
            }
        });
        int n = 0;
        try {
            n = Vars.spawner.countSpawns();
        } catch (Throwable ignored) {
        }
        json(ex, 200, "{\"ok\":true,\"spawns\":" + n + ",\"message\":\"Clamp scheduled on server thread\"}");
    }

    private String spawnClampJson() {
        return "{"
                + "\"spawnClampEnabled\":" + config.spawnClampEnabled + ","
                + "\"maxEnemySpawns\":" + config.maxEnemySpawns + ","
                + "\"minEnemySpawns\":" + config.minEnemySpawns
                + "}";
    }

    private void handleRestart(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        Log.info("[Admin] Restart requested via API");
        json(ex, 200, "{\"ok\":true,\"message\":\"Server will restart in 3 seconds\"}");
        arc.Core.app.post(() -> Threads.daemon(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            Log.info("[Admin] Exiting for restart (use systemd/supervisor to bring back up)");
            arc.Core.app.exit();
        }));
    }

    /** Broadcast to all connected players via vanilla Call — no client mod required. */
    private void handleBroadcast(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        String body = readBody(ex);
        String message = null;
        String type = "chat";
        try {
            if (body != null && !body.isBlank()) {
                JsonValue root = new JsonReader().parse(body);
                if (root != null) {
                    message = root.getString("message", null);
                    type = root.getString("type", "chat");
                }
            }
        } catch (Throwable ignored) {
            message = jsonField(body, "message");
            String t = jsonField(body, "type");
            if (t != null && !t.isEmpty()) type = t;
        }
        if (message == null || message.isBlank()) {
            json(ex, 400, "{\"error\":\"message required\"}");
            return;
        }
        message = message.trim();
        if (message.length() > 500) message = message.substring(0, 500);
        if (type == null || type.isBlank()) type = "chat";
        type = type.trim().toLowerCase();
        if (!type.equals("chat") && !type.equals("announce") && !type.equals("toast")) {
            type = "chat";
        }

        final String text = "[scarlet][Admin][] " + message;
        final String mode = type;
        int players;
        try {
            players = Groups.player.size();
        } catch (Throwable t) {
            players = 0;
        }

        arc.Core.app.post(() -> {
            try {
                switch (mode) {
                    case "announce" -> Call.announce(text);
                    case "toast" -> Call.infoToast(text, 6f);
                    default -> Call.sendMessage(text);
                }
                Log.info("[Admin] Broadcast (@) to players: @", mode, text);
            } catch (Throwable t) {
                Log.err("[Admin] Broadcast failed, falling back to chat", t);
                try {
                    Call.sendMessage(text);
                } catch (Throwable ignored) {
                }
            }
        });

        json(ex, 200, "{\"ok\":true,\"type\":\"" + esc(mode) + "\",\"players\":" + players + "}");
    }

    private void handleMods(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            json(ex, 200, listFilesJson(Vars.modDirectory, ".zip", ".jar"));
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            String name = query(ex, "name");
            if (name == null || !safeFileName(name)) {
                json(ex, 400, "{\"error\":\"Invalid name query param\"}");
                return;
            }
            if (!name.endsWith(".zip") && !name.endsWith(".jar")) {
                json(ex, 400, "{\"error\":\"Only .zip and .jar allowed\"}");
                return;
            }
            Fi dest = Vars.modDirectory.child(name);
            writeUpload(ex, dest);
            json(ex, 200, "{\"ok\":true,\"file\":\"" + esc(name) + "\"}");
            return;
        }
        json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
    }

    private void handleMaps(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            json(ex, 200, listFilesJson(Vars.customMapDirectory, ".msav", ".mmap"));
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            String name = query(ex, "name");
            if (name == null || !safeFileName(name)) {
                json(ex, 400, "{\"error\":\"Invalid name\"}");
                return;
            }
            String lower = name.toLowerCase();
            if (!lower.endsWith(".msav") && !lower.endsWith(".mmap")) {
                json(ex, 400, "{\"error\":\"Only .msav/.mmap allowed\"}");
                return;
            }
            Fi dest = Vars.customMapDirectory.child(name);
            writeUpload(ex, dest);
            try {
                Vars.maps.reload();
            } catch (Throwable ignored) {
            }
            json(ex, 200, "{\"ok\":true,\"file\":\"" + esc(name) + "\"}");
            return;
        }
        json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
    }

    private void handleConfigs(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        Seq<String> paths = listConfigPaths();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < paths.size; i++) {
            if (i > 0) sb.append(',');
            sb.append("\"").append(esc(paths.get(i))).append("\"");
        }
        sb.append(']');
        json(ex, 200, sb.toString());
    }

    private void handleConfigFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String prefix = AdminConstants.API_PREFIX + "/configs/";
        if (!path.startsWith(prefix)) {
            json(ex, 404, "{\"error\":\"Not found\"}");
            return;
        }
        String rel = urlDecode(path.substring(prefix.length()));
        if (!safeConfigPath(rel)) {
            json(ex, 400, "{\"error\":\"Invalid path\"}");
            return;
        }
        Fi file = Vars.dataDirectory.child(rel);
        if (!file.exists() || file.isDirectory()) {
            json(ex, 404, "{\"error\":\"Not found\"}");
            return;
        }
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            String text = file.readString();
            json(ex, 200, "{\"path\":\"" + esc(rel) + "\",\"content\":\"" + esc(text) + "\"}");
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            byte[] data = readBodyBytes(ex);
            file.writeBytes(data, false);
            json(ex, 200, "{\"ok\":true,\"path\":\"" + esc(rel) + "\"}");
            return;
        }
        json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
    }

    private Fi rulesFile() {
        return Vars.dataDirectory.child("rules.hjson");
    }

    private void handleRules(HttpExchange ex) throws IOException {
        Fi file = rulesFile();
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            if (!file.exists()) {
                ensureDefaultRules(file);
            }
            json(ex, 200, "{\"path\":\"rules.hjson\",\"content\":\"" + esc(file.readString()) + "\"}");
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            byte[] data = readBodyBytes(ex);
            file.writeBytes(data, false);
            json(ex, 200, "{\"ok\":true,\"path\":\"rules.hjson\"}");
            return;
        }
        json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
    }

    private void handleRulesApply(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        Fi file = rulesFile();
        if (!file.exists()) {
            json(ex, 404, "{\"error\":\"rules.hjson missing\"}");
            return;
        }
        try {
            String text = stripHjsonComments(file.readString());
            // wrap bare HJSON object if needed
            String cleaned = text.trim();
            if (!cleaned.startsWith("{")) {
                cleaned = "{" + cleaned + "}";
            }
            mindustry.game.Rules parsed = mindustry.io.JsonIO.json.fromJson(mindustry.game.Rules.class, cleaned);
            Vars.state.rules = parsed;
            Log.info("[Admin] Applied rules.hjson to live state");
            json(ex, 200, "{\"ok\":true,\"message\":\"Rules applied to running game\"}");
        } catch (Throwable t) {
            Log.err("[Admin] Failed to apply rules", t);
            json(ex, 500, "{\"error\":\"" + esc(String.valueOf(t.getMessage())) + "\"}");
        }
    }

    /** Strip # // and /* comments so JsonIO can parse HJSON-like rules. */
    private static String stripHjsonComments(String text) {
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

    private void handleRulesReset(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        Fi file = rulesFile();
        ensureDefaultRules(file);
        json(ex, 200, "{\"ok\":true,\"content\":\"" + esc(file.readString()) + "\"}");
    }

    private void handlePresets(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        presets.ensureDefault(rulesFile(), () -> ensureDefaultRules(rulesFile()));
        StringBuilder sb = new StringBuilder("[");
        Seq<PresetStore.PresetInfo> list = presets.list();
        for (int i = 0; i < list.size; i++) {
            if (i > 0) sb.append(',');
            PresetStore.PresetInfo info = list.get(i);
            sb.append("{\"name\":\"").append(esc(info.name)).append("\",")
                    .append("\"protected\":").append(info.protectedPreset).append(',')
                    .append("\"size\":").append(info.size).append('}');
        }
        sb.append(']');
        json(ex, 200, sb.toString());
    }

    private void handlePresetItem(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String prefix = AdminConstants.API_PREFIX + "/presets/";
        if (!path.startsWith(prefix)) {
            json(ex, 404, "{\"error\":\"Not found\"}");
            return;
        }
        String rest = urlDecode(path.substring(prefix.length()));
        if (rest.contains("..") || rest.contains("\\")) {
            json(ex, 400, "{\"error\":\"Invalid name\"}");
            return;
        }

        // GET .../templates — catalog of built-in mode templates
        if ("templates".equalsIgnoreCase(rest)) {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            json(ex, 200, ModeTemplates.toJsonArray());
            return;
        }

        // .../default/update — explicit overwrite of protected default
        if ("default/update".equalsIgnoreCase(rest) || rest.equalsIgnoreCase(PresetStore.DEFAULT_NAME + "/update")) {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            String body = readBody(ex);
            String content;
            if (body == null || body.trim().isEmpty()) {
                Fi rules = rulesFile();
                if (!rules.exists()) ensureDefaultRules(rules);
                content = rules.readString();
            } else {
                content = body;
            }
            presets.updateDefault(content);
            Log.info("[Admin] Updated default preset from admin request");
            json(ex, 200, "{\"ok\":true,\"name\":\"default\"}");
            return;
        }

        // .../from-template/{id} — install built-in mode template as preset
        if (rest.startsWith("from-template/")) {
            String tid = rest.substring("from-template/".length());
            if (tid.contains("/")) {
                json(ex, 400, "{\"error\":\"Invalid template id\"}");
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                String name = ModeTemplates.install(presets, tid);
                json(ex, 200, "{\"ok\":true,\"template\":\"" + esc(PresetStore.sanitize(tid))
                        + "\",\"name\":\"" + esc(name) + "\"}");
            } catch (FileNotFoundException e) {
                json(ex, 404, "{\"error\":\"Unknown template\"}");
            } catch (Exception e) {
                json(ex, 500, "{\"error\":\"" + esc(String.valueOf(e.getMessage())) + "\"}");
            }
            return;
        }

        // .../name/apply
        if (rest.endsWith("/apply")) {
            String name = rest.substring(0, rest.length() - "/apply".length());
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            try {
                presets.applyTo(name, rulesFile());
                Log.info("[Admin] Applied preset '@' -> rules.hjson (restart required)", PresetStore.sanitize(name));
                json(ex, 200, "{\"ok\":true,\"preset\":\"" + esc(PresetStore.sanitize(name))
                        + "\",\"restartRequired\":true,\"message\":\"Preset written to rules.hjson. Restart the server to fully apply.\"}");
            } catch (FileNotFoundException e) {
                json(ex, 404, "{\"error\":\"Preset not found\"}");
            } catch (Exception e) {
                json(ex, 500, "{\"error\":\"" + esc(String.valueOf(e.getMessage())) + "\"}");
            }
            return;
        }

        String name = rest;
        if (name.contains("/")) {
            json(ex, 400, "{\"error\":\"Invalid path\"}");
            return;
        }
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            try {
                String content = presets.read(name);
                json(ex, 200, "{\"name\":\"" + esc(PresetStore.sanitize(name))
                        + "\",\"protected\":" + PresetStore.isProtected(name)
                        + ",\"content\":\"" + esc(content) + "\"}");
            } catch (FileNotFoundException e) {
                json(ex, 404, "{\"error\":\"Preset not found\"}");
            }
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            if (PresetStore.isProtected(name)) {
                json(ex, 403, "{\"error\":\"Cannot overwrite default via save; use POST /presets/default/update\"}");
                return;
            }
            String body = readBody(ex);
            String content;
            if (body == null || body.trim().isEmpty()) {
                Fi rules = rulesFile();
                if (!rules.exists()) ensureDefaultRules(rules);
                content = rules.readString();
            } else {
                content = body;
            }
            try {
                String safe = PresetStore.sanitize(name);
                presets.write(safe, content);
                json(ex, 200, "{\"ok\":true,\"name\":\"" + esc(safe) + "\"}");
            } catch (IOException e) {
                json(ex, 403, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            try {
                presets.delete(name);
                json(ex, 200, "{\"ok\":true}");
            } catch (FileNotFoundException e) {
                json(ex, 404, "{\"error\":\"Preset not found\"}");
            } catch (IOException e) {
                json(ex, 403, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
            return;
        }
        json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
    }

    private void ensureDefaultRules(Fi file) {
        try (InputStream in = ServerAdminPlugin.class.getResourceAsStream("/rules.defaults.hjson")) {
            if (in != null) {
                file.writeBytes(in.readAllBytes(), false);
                return;
            }
        } catch (Exception ignored) {
        }
        // fallback minimal
        if (!file.exists()) {
            file.writeString("""
                reactorExplosions: false
                logicUnitBuild: false
                logicUnitDeconstruct: false
                """);
        }
    }

    private void handleModItem(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String prefix = AdminConstants.API_PREFIX + "/mods/";
        String name = urlDecode(path.substring(prefix.length()));
        if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
            handleModsDelete(ex, name);
            return;
        }
        json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
    }

    private void handleMapItem(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String prefix = AdminConstants.API_PREFIX + "/maps/";
        String name = urlDecode(path.substring(prefix.length()));
        if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
            handleMapsDelete(ex, name);
            return;
        }
        json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
    }

    private void handleModsDelete(HttpExchange ex, String name) throws IOException {
        if (!"DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        if (!safeFileName(name)) {
            json(ex, 400, "{\"error\":\"Invalid name\"}");
            return;
        }
        Fi f = Vars.modDirectory.child(name);
        if (!f.exists()) {
            json(ex, 404, "{\"error\":\"Not found\"}");
            return;
        }
        if (name.contains("server-admin") || name.contains("server-content-sync")) {
            json(ex, 403, "{\"error\":\"Protected mod\"}");
            return;
        }
        f.delete();
        json(ex, 200, "{\"ok\":true}");
    }

    private void handleMapsDelete(HttpExchange ex, String name) throws IOException {
        if (!safeFileName(name)) {
            json(ex, 400, "{\"error\":\"Invalid name\"}");
            return;
        }
        Fi f = Vars.customMapDirectory.child(name);
        if (!f.exists()) {
            json(ex, 404, "{\"error\":\"Not found\"}");
            return;
        }
        f.delete();
        try {
            Vars.maps.reload();
        } catch (Throwable ignored) {
        }
        json(ex, 200, "{\"ok\":true}");
    }

    private interface Handler {
        void handle(HttpExchange ex) throws IOException;
    }

    private void withAuth(HttpExchange ex, Handler handler) throws IOException {
        addCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }
        if (!checkAuth(ex)) return;
        handler.handle(ex);
    }

    private boolean checkAuth(HttpExchange ex) throws IOException {
        String authHeader = ex.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null) authHeader = ex.getRequestHeaders().getFirst("authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }
        if (!this.auth.validate(token)) {
            json(ex, 401, "{\"error\":\"Unauthorized\"}");
            return false;
        }
        return true;
    }

    private Seq<String> listConfigPaths() {
        Seq<String> out = new Seq<>();
        Fi root = Vars.dataDirectory;
        if (root == null || !root.exists()) return out;
        walkConfigs(root, root, out);
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    private void walkConfigs(Fi root, Fi dir, Seq<String> out) {
        String rootPath = root.absolutePath().replace('\\', '/');
        for (Fi f : dir.list()) {
            if (f.isDirectory()) {
                walkConfigs(root, f, out);
                continue;
            }
            String abs = f.absolutePath().replace('\\', '/');
            if (!abs.startsWith(rootPath)) continue;
            String rel = abs.substring(rootPath.length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            if (!safeConfigPath(rel)) continue;
            String lower = f.name().toLowerCase();
            if (lower.endsWith(".hjson") || lower.endsWith(".json") || lower.endsWith(".properties")
                    || lower.endsWith(".cfg") || lower.endsWith(".txt") || lower.endsWith(".yml")
                    || lower.endsWith(".yaml")) {
                out.add(rel);
            }
        }
    }

    private boolean safeConfigPath(String rel) {
        if (rel == null || rel.isEmpty()) return false;
        if (rel.contains("..") || rel.startsWith("/") || rel.contains("\\")) return false;
        if (rel.endsWith(AdminConstants.PASSWORD_FILE) || rel.contains("/" + AdminConstants.PASSWORD_FILE)) return false;
        return true;
    }

    private static boolean safeFileName(String name) {
        return name != null && !name.isEmpty() && !name.contains("..") && !name.contains("/") && !name.contains("\\");
    }

    private String listFilesJson(Fi dir, String... extensions) {
        StringBuilder sb = new StringBuilder("[");
        if (dir != null && dir.exists()) {
            int i = 0;
            for (Fi f : dir.list()) {
                if (f.isDirectory()) continue;
                String n = f.name();
                boolean ok = false;
                for (String ext : extensions) {
                    if (n.toLowerCase().endsWith(ext)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) continue;
                if (i++ > 0) sb.append(',');
                sb.append("{\"name\":\"").append(esc(n)).append("\",\"size\":").append(f.length()).append('}');
            }
        }
        sb.append(']');
        return sb.toString();
    }

    private void writeUpload(HttpExchange ex, Fi dest) throws IOException {
        dest.parent().mkdirs();
        byte[] data = readBodyBytes(ex);
        dest.writeBytes(data, false);
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(readBodyBytes(ex), StandardCharsets.UTF_8);
    }

    private static byte[] readBodyBytes(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return in.readAllBytes();
        }
    }

    private static String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return null;
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                return urlDecode(part.substring(eq + 1));
            }
        }
        return null;
    }

    private static String jsonField(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\"";
        int i = json.indexOf(search);
        if (i < 0) return null;
        int colon = json.indexOf(':', i);
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static void json(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        addCors(ex);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
