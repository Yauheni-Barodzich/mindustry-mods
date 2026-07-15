package scs.admin.client;

import arc.util.*;
import scs.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;

/** HTTP client for Server Admin API. Token kept in memory only. */
public class AdminApiClient {
    private final String baseUrl;
    private String token;

    public AdminApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public boolean isLoggedIn() {
        return token != null && !token.isEmpty();
    }

    public void logout() {
        token = null;
    }

    public void login(String password) throws IOException {
        String body = "{\"password\":\"" + escapeJson(password) + "\"}";
        String resp = request("POST", AdminConstants.API_PREFIX + "/login", body, null, "application/json");
        token = jsonField(resp, "token");
        if (token == null || token.isEmpty()) {
            throw new IOException("Login failed");
        }
    }

    public String status() throws IOException {
        return request("GET", AdminConstants.API_PREFIX + "/status", null, token, null);
    }

    public void restart() throws IOException {
        request("POST", AdminConstants.API_PREFIX + "/restart", "", token, "application/json");
    }

    /** Global message to all connected players (vanilla Call — no client mod needed). type: chat|announce|toast */
    public String broadcast(String message, String type) throws IOException {
        String body = "{\"message\":\"" + escapeJson(message) + "\",\"type\":\"" + escapeJson(type == null ? "chat" : type) + "\"}";
        return request("POST", AdminConstants.API_PREFIX + "/broadcast", body, token, "application/json");
    }

    public String getSpawnClamp() throws IOException {
        return request("GET", AdminConstants.API_PREFIX + "/spawn-clamp", null, token, null);
    }

    public String putSpawnClamp(boolean enabled, int max, int min) throws IOException {
        String body = "{\"spawnClampEnabled\":" + enabled
                + ",\"maxEnemySpawns\":" + max
                + ",\"minEnemySpawns\":" + min + "}";
        return request("PUT", AdminConstants.API_PREFIX + "/spawn-clamp", body, token, "application/json");
    }

    public String applySpawnClamp() throws IOException {
        return request("POST", AdminConstants.API_PREFIX + "/spawn-clamp/apply", "", token, "application/json");
    }

    public String listMods() throws IOException {
        return request("GET", AdminConstants.API_PREFIX + "/mods", null, token, null);
    }

    public String listMaps() throws IOException {
        return request("GET", AdminConstants.API_PREFIX + "/maps", null, token, null);
    }

    public void deleteMod(String name) throws IOException {
        request("DELETE", AdminConstants.API_PREFIX + "/mods/" + enc(name), null, token, null);
    }

    public void deleteMap(String name) throws IOException {
        request("DELETE", AdminConstants.API_PREFIX + "/maps/" + enc(name), null, token, null);
    }

    public void uploadMod(String fileName, byte[] data) throws IOException {
        request("POST", AdminConstants.API_PREFIX + "/mods?name=" + enc(fileName), data, token, "application/octet-stream");
    }

    public void uploadMap(String fileName, byte[] data) throws IOException {
        request("POST", AdminConstants.API_PREFIX + "/maps?name=" + enc(fileName), data, token, "application/octet-stream");
    }

    public String listConfigs() throws IOException {
        return request("GET", AdminConstants.API_PREFIX + "/configs", null, token, null);
    }

    public String getConfig(String path) throws IOException {
        return request("GET", AdminConstants.API_PREFIX + "/configs/" + encPath(path), null, token, null);
    }

    public void putConfig(String path, String content) throws IOException {
        request("PUT", AdminConstants.API_PREFIX + "/configs/" + encPath(path), content.getBytes(StandardCharsets.UTF_8), token, "text/plain");
    }

    public String getRules() throws IOException {
        return request("GET", AdminConstants.API_PREFIX + "/rules", null, token, null);
    }

    public void putRules(String content) throws IOException {
        request("PUT", AdminConstants.API_PREFIX + "/rules", content.getBytes(StandardCharsets.UTF_8), token, "text/plain");
    }

    public void applyRules() throws IOException {
        request("POST", AdminConstants.API_PREFIX + "/rules/apply", "", token, "application/json");
    }

    public String resetRules() throws IOException {
        return request("POST", AdminConstants.API_PREFIX + "/rules/reset", "", token, "application/json");
    }

    public String listPresets() throws IOException {
        return request("GET", AdminConstants.API_PREFIX + "/presets", null, token, null);
    }

    public String listTemplates() throws IOException {
        return request("GET", AdminConstants.API_PREFIX + "/presets/templates", null, token, null);
    }

    public String getPreset(String name) throws IOException {
        return request("GET", AdminConstants.API_PREFIX + "/presets/" + enc(name), null, token, null);
    }

    /** Empty body = copy current rules.hjson into this preset. */
    public void putPreset(String name, String contentOrNull) throws IOException {
        byte[] body = contentOrNull == null ? new byte[0] : contentOrNull.getBytes(StandardCharsets.UTF_8);
        request("PUT", AdminConstants.API_PREFIX + "/presets/" + enc(name), body, token, "text/plain");
    }

    public void deletePreset(String name) throws IOException {
        request("DELETE", AdminConstants.API_PREFIX + "/presets/" + enc(name), null, token, null);
    }

    public String applyPreset(String name) throws IOException {
        return request("POST", AdminConstants.API_PREFIX + "/presets/" + enc(name) + "/apply", "", token, "application/json");
    }

    /** Explicitly overwrite protected default (body empty = copy current rules.hjson). */
    public String updateDefaultPreset(String contentOrNull) throws IOException {
        byte[] body = contentOrNull == null ? new byte[0] : contentOrNull.getBytes(StandardCharsets.UTF_8);
        return request("POST", AdminConstants.API_PREFIX + "/presets/default/update", body, token, "text/plain");
    }

    /** Install built-in mode template (pvp / attack) as presets/mode-{id}.hjson. */
    public String installModeTemplate(String templateId) throws IOException {
        return request("POST", AdminConstants.API_PREFIX + "/presets/from-template/" + enc(templateId), "", token, "application/json");
    }

    public static String defaultUrlFromAddress(String address) {
        if (address == null || address.isEmpty()) return "";
        String host = address.trim();
        int port = 6567;
        if (host.startsWith("http://")) host = host.substring(7);
        if (host.startsWith("https://")) host = host.substring(8);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            try {
                port = Integer.parseInt(host.substring(colon + 1));
                host = host.substring(0, colon);
            } catch (Exception ignored) {
                port = 6567;
            }
        }
        return "http://" + host + ":" + (port + AdminConstants.DEFAULT_PORT_OFFSET);
    }

    private String request(String method, String path, Object body, String authToken, String contentType) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(120000);
        conn.setRequestMethod(method);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        if (body != null) {
            conn.setDoOutput(true);
            if (contentType != null) conn.setRequestProperty("Content-Type", contentType);
            byte[] bytes = body instanceof byte[] b ? b : body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) throw new IOException("HTTP " + code);
        try (InputStream in = stream) {
            String resp = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (code >= 400) throw new IOException("HTTP " + code + ": " + resp);
            return resp;
        } finally {
            conn.disconnect();
        }
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

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encPath(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
