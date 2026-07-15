package scs.plugin;

import arc.files.*;
import arc.util.*;
import mindustry.*;
import scs.HjsonLite;

/** Plugin configuration loaded from config/mods/server-content-sync/config.hjson */
public class SyncConfig {
    public boolean enabled = true;
    public String bind = "0.0.0.0";
    /** If <= 0, use gamePort + 1. */
    public int port = 0;

    public static SyncConfig load() {
        SyncConfig cfg = new SyncConfig();
        Fi folder = Vars.modDirectory.child("server-content-sync");
        folder.mkdirs();
        Fi file = folder.child("config.hjson");
        if (!file.exists()) {
            file.writeString("""
                # Server Content Sync
                enabled: true
                bind: "0.0.0.0"
                # 0 = gamePort + 1 (e.g. 6567 -> 6568)
                port: 0
                """);
            Log.info("[SCS] Wrote default config to @", file.absolutePath());
            return cfg;
        }

        try {
            var root = HjsonLite.parse(file.readString());
            cfg.enabled = root.getBoolean("enabled", true);
            cfg.bind = root.getString("bind", "0.0.0.0");
            cfg.port = root.getInt("port", 0);
        } catch (Exception e) {
            Log.err("[SCS] Failed to read config, using defaults", e);
        }
        return cfg;
    }

    public int resolvePort() {
        if (port > 0) return port;
        int gamePort = 6567;
        try {
            gamePort = arc.Core.settings.getInt("port", Vars.port);
        } catch (Throwable t) {
            gamePort = Vars.port;
        }
        return gamePort + 1;
    }
}
