package scs.admin.plugin;

import arc.files.*;
import arc.util.*;
import mindustry.*;
import scs.HjsonLite;

/** Plugin config: config/mods/server-admin/config.hjson */
public class AdminConfig {
    public boolean enabled = true;
    public String bind = "0.0.0.0";
    /** If <= 0, use gamePort + 2. */
    public int port = 0;
    /** Session lifetime in seconds. */
    public int sessionTtlSec = 3600;

    /** Limit enemy spawn overlays after each map load. */
    public boolean spawnClampEnabled = true;
    /** Max enemy spawn points; <=0 = no max. */
    public int maxEnemySpawns = 3;
    /** Min enemy spawn points when waves/attack on; <=0 = no min. */
    public int minEnemySpawns = 1;

    public static AdminConfig load() {
        AdminConfig cfg = new AdminConfig();
        Fi folder = Vars.modDirectory.child("server-admin");
        folder.mkdirs();
        Fi file = folder.child("config.hjson");
        if (!file.exists()) {
            file.writeString(defaultText());
            Log.info("[Admin] Wrote default config to @", file.absolutePath());
            return cfg;
        }
        try {
            var root = HjsonLite.parse(file.readString());
            cfg.enabled = root.getBoolean("enabled", true);
            cfg.bind = root.getString("bind", "0.0.0.0");
            cfg.port = root.getInt("port", 0);
            cfg.sessionTtlSec = root.getInt("sessionTtlSec", 3600);
            cfg.spawnClampEnabled = root.getBoolean("spawnClampEnabled", true);
            cfg.maxEnemySpawns = root.getInt("maxEnemySpawns", 3);
            cfg.minEnemySpawns = root.getInt("minEnemySpawns", 1);
        } catch (Exception e) {
            Log.err("[Admin] Failed to read config, using defaults", e);
        }
        return cfg;
    }

    /** Persist current settings to config/mods/server-admin/config.hjson. */
    public void save() {
        Fi folder = Vars.modDirectory.child("server-admin");
        folder.mkdirs();
        folder.child("config.hjson").writeString(toHjson());
        Log.info("[Admin] Saved config (spawnClamp=@ max=@ min=@)",
                spawnClampEnabled, maxEnemySpawns, minEnemySpawns);
    }

    public String toHjson() {
        return "# Server Admin API\n"
                + "enabled: " + enabled + "\n"
                + "bind: \"" + (bind == null ? "0.0.0.0" : bind) + "\"\n"
                + "# 0 = gamePort + 2 (e.g. 6567 -> 6569)\n"
                + "port: " + port + "\n"
                + "sessionTtlSec: " + sessionTtlSec + "\n"
                + "\n"
                + "# Enemy spawn points after map load\n"
                + "spawnClampEnabled: " + spawnClampEnabled + "\n"
                + "# max enemy spawn overlays (0 = unlimited)\n"
                + "maxEnemySpawns: " + maxEnemySpawns + "\n"
                + "# min when waves/attack enabled (0 = do not add)\n"
                + "minEnemySpawns: " + minEnemySpawns + "\n";
    }

    private static String defaultText() {
        return new AdminConfig().toHjson();
    }

    public int resolvePort() {
        if (port > 0) return port;
        int gamePort = 6567;
        try {
            gamePort = arc.Core.settings.getInt("port", Vars.port);
        } catch (Throwable t) {
            gamePort = Vars.port;
        }
        return gamePort + scs.AdminConstants.DEFAULT_PORT_OFFSET;
    }
}
