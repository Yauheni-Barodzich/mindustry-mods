package scs;

import arc.util.*;
import mindustry.*;
import mindustry.mod.*;
import scs.admin.client.ServerAdminClient;
import scs.admin.plugin.ServerAdminPlugin;
import scs.client.ServerContentSyncClient;
import scs.plugin.ServerContentSyncPlugin;

/**
 * Unified mod entry (mod.hjson wins over plugin.hjson in one zip).
 * On headless dedicated server we must bootstrap plugin-side sync + admin HTTP here.
 */
public class ScsClient extends Mod {
    private ServerContentSyncPlugin syncPlugin;
    private ServerAdminPlugin adminPlugin;
    private ServerContentSyncClient syncClient;
    private ServerAdminClient adminClient;

    @Override
    public void init() {
        if (Vars.headless) {
            syncPlugin = new ServerContentSyncPlugin();
            adminPlugin = new ServerAdminPlugin();
            syncPlugin.init();
            adminPlugin.init();
            Log.info("[SCS] Suite plugin ready (via mod entry).");
            return;
        }
        syncClient = new ServerContentSyncClient();
        adminClient = new ServerAdminClient();
        syncClient.init();
        adminClient.init();
        Log.info("[SCS] Suite client ready.");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        if (Vars.headless && syncPlugin != null && adminPlugin != null) {
            syncPlugin.registerServerCommands(handler);
            adminPlugin.registerServerCommands(handler);
        }
    }
}
