package scs.admin.client;

import arc.files.*;
import mindustry.*;
import scs.*;

/**
 * Local copy of server admin.password for quick login.
 * Path: {@code %AppData%/Mindustry/mods/server-admin/admin.password}
 * Same format as on server (first non-comment line). Never uploaded/synced by this client.
 */
public final class LocalAdminPassword {
    private LocalAdminPassword() {}

    public static Fi passwordFile() {
        return Vars.modDirectory.child("server-admin").child(AdminConstants.PASSWORD_FILE);
    }

    public static boolean hasLocalPassword() {
        String p = read();
        return p != null && !p.isEmpty();
    }

    /** @return password or null */
    public static String read() {
        Fi file = passwordFile();
        if (!file.exists()) return null;
        try {
            for (String line : file.readString().split("\\R")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                return t;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
