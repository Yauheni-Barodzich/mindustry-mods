package scs;

import arc.util.*;
import mindustry.mod.*;
import scs.admin.client.ServerAdminClient;
import scs.client.ServerContentSyncClient;

/** Unified client entry: content sync UI + admin UI. */
public class ScsClient extends Mod {
    private final ServerContentSyncClient sync = new ServerContentSyncClient();
    private final ServerAdminClient admin = new ServerAdminClient();

    @Override
    public void init() {
        sync.init();
        admin.init();
        Log.info("[SCS] Suite client ready.");
    }
}
