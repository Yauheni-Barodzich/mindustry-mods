package scs.admin.plugin;

import arc.util.*;
import mindustry.mod.*;

/** Server admin plugin — HTTP API with password file auth. */
public class ServerAdminPlugin extends Plugin {
    private AdminAuth auth;
    private AdminConfig config;
    private AdminHttpServer http;
    private SpawnClamp spawnClamp;

    @Override
    public void init() {
        config = AdminConfig.load();
        auth = new AdminAuth(config);
        spawnClamp = new SpawnClamp(config);
        spawnClamp.register();
        http = new AdminHttpServer(auth, config, spawnClamp);
        try {
            http.start();
        } catch (Exception e) {
            Log.err("[Admin] Failed to start HTTP", e);
        }
        Log.info("[Admin] Spawn clamp enabled=@ max=@ min=@",
                config.spawnClampEnabled, config.maxEnemySpawns, config.minEnemySpawns);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("admin-status", "Show Server Admin API status.", args -> {
            Log.info("[Admin] enabled=@ port=@ passwordFile=@ (set password via FTP/SSH only)",
                    config.enabled, http.getBoundPort(), auth.getPasswordFile().absolutePath());
            Log.info("[Admin] spawnClamp=@ max=@ min=@",
                    config.spawnClampEnabled, config.maxEnemySpawns, config.minEnemySpawns);
        });
        handler.register("admin-spawn-clamp", "Re-run enemy spawn clamp on the current map.", args -> {
            spawnClamp.clamp();
            Log.info("[Admin] Spawn clamp done. spawns=@", mindustry.Vars.spawner.countSpawns());
        });
    }
}
