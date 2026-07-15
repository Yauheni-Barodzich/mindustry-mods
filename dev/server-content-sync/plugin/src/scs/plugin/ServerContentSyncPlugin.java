package scs.plugin;

import arc.util.*;
import mindustry.mod.*;

/** Server-side plugin: indexes content and serves it over HTTP. */
public class ServerContentSyncPlugin extends Plugin {
    private ContentIndex index;
    private SyncConfig config;
    private SyncHttpServer http;

    @Override
    public void init() {
        if (!mindustry.Vars.headless) {
            Log.info("[SCS] Server plugin skipped on client.");
            return;
        }
        config = SyncConfig.load();
        index = new ContentIndex();
        index.rebuild();
        http = new SyncHttpServer(index, config);
        try {
            http.start();
        } catch (Exception e) {
            Log.err("[SCS] Failed to start HTTP server", e);
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("sync-reload", "Rebuild content index and restart sync HTTP if needed.", args -> {
            config = SyncConfig.load();
            index.rebuild();
            try {
                http.stop();
                http = new SyncHttpServer(index, config);
                http.start();
                Log.info("[SCS] Reloaded. Port=@ mods=@ maps=@", http.getBoundPort(), index.modCount(), index.mapCount());
            } catch (Exception e) {
                Log.err("[SCS] Reload failed", e);
            }
        });

        handler.register("sync-status", "Show Server Content Sync status.", args -> {
            Log.info("[SCS] enabled=@ port=@ bind=@ mods=@ maps=@",
                    config.enabled, http.getBoundPort(), config.bind, index.modCount(), index.mapCount());
        });
    }
}
