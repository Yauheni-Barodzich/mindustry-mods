package scs;

import arc.util.*;
import mindustry.mod.*;
import scs.admin.plugin.ServerAdminPlugin;
import scs.plugin.ServerContentSyncPlugin;

/** Unified server entry: content sync + admin API. */
public class ScsPlugin extends Plugin {
    private final ServerContentSyncPlugin sync = new ServerContentSyncPlugin();
    private final ServerAdminPlugin admin = new ServerAdminPlugin();

    @Override
    public void init() {
        sync.init();
        admin.init();
        Log.info("[SCS] Suite plugin ready.");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        sync.registerServerCommands(handler);
        admin.registerServerCommands(handler);
    }
}
