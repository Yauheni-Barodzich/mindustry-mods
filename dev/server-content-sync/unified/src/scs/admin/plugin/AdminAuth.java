package scs.admin.plugin;

import arc.files.*;
import arc.util.*;
import mindustry.*;
import scs.*;

import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Password file auth — password never exposed via HTTP. */
public class AdminAuth {
    private final AdminConfig config;
    private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();
    private final Fi adminDir;
    private final Fi passwordFile;

    public AdminAuth(AdminConfig config) {
        this.config = config;
        this.adminDir = Vars.modDirectory.child("server-admin");
        this.passwordFile = adminDir.child(AdminConstants.PASSWORD_FILE);
        ensurePasswordFile();
    }

    private void ensurePasswordFile() {
        adminDir.mkdirs();
        if (!passwordFile.exists()) {
            passwordFile.writeString("# Set your admin password on the next line (via FTP/SSH only).\n# This file is never sent over the API.\n");
            Log.info("[Admin] Created @ — set password via FTP/SSH", passwordFile.absolutePath());
        }
    }

    public Fi getPasswordFile() {
        return passwordFile;
    }

    /** Reload password from disk on each login attempt. */
    public Optional<String> login(String password) {
        if (password == null || password.isEmpty()) return Optional.empty();
        String expected = readPasswordFromDisk();
        if (expected == null || expected.isEmpty()) {
            Log.warn("[Admin] Login rejected: password file empty");
            return Optional.empty();
        }
        if (!constantTimeEquals(password.trim(), expected)) {
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        long expiry = System.currentTimeMillis() + config.sessionTtlSec * 1000L;
        sessions.put(token, expiry);
        purgeExpired();
        return Optional.of(token);
    }

    public boolean validate(String token) {
        if (token == null || token.isEmpty()) return false;
        Long expiry = sessions.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void logout(String token) {
        if (token != null) sessions.remove(token);
    }

    private String readPasswordFromDisk() {
        try {
            if (!passwordFile.exists()) return null;
            for (String line : passwordFile.readString().split("\\R")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                return t;
            }
        } catch (Exception e) {
            Log.err("[Admin] Failed to read password file", e);
        }
        return null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(x, y);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> e.getValue() < now);
    }
}
