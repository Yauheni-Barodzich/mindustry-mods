package scs.client;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;
import scs.*;

/** Client entry: settings + Join button + sync dialog. */
public class ServerContentSyncClient extends Mod {
    private SyncDialog dialog;
    private boolean joinButtonAdded;

    @Override
    public void init() {
        Core.settings.defaults("scs-url-override", "");
        Core.settings.defaults("scs-last-address", "");
        Core.settings.defaults("scs-last-mods-changed", false);

        Events.on(ClientLoadEvent.class, e -> {
            dialog = new SyncDialog();
            Vars.ui.settings.addCategory(Loc.get("scs.sync.settings", "Server Sync"), Icon.download, table -> {
                table.add(Loc.get("scs.sync.settings.hint",
                        "Use Join → Content Sync, or open here.\nOptional URL override: set in sync dialog.")).wrap().width(420f).left();
                table.row();
                table.button(Loc.get("scs.sync.open", "Open Content Sync"), Icon.play, () -> {
                    dialog.showForAddress(Core.settings.getString("scs-last-address", ""));
                }).size(280f, 48f).padTop(8f);
                table.row();
                table.button(Loc.get("scs.sync.clearUrl", "Clear URL override"), Icon.trash, () -> {
                    Core.settings.put("scs-url-override", "");
                    Vars.ui.showInfo(Loc.get("scs.sync.clearUrlOk", "scs-url-override cleared."));
                }).size(280f, 48f);
            });
            hookJoinButton();
            Log.info("[SCS] Client ready.");
        });
    }

    private void hookJoinButton() {
        if (Vars.ui == null || Vars.ui.join == null) return;
        JoinDialog join = Vars.ui.join;
        join.shown(() -> {
            if (!joinButtonAdded) {
                joinButtonAdded = true;
                join.buttons.button(Loc.get("scs.sync.joinButton", "Content Sync"), Icon.download, () -> {
                    String address = Core.settings.getString("scs-last-address", "");
                    if (address.isEmpty()) {
                        address = Core.settings.getString("lastServer", "");
                    }
                    if (address.isEmpty()) {
                        address = Core.settings.getString("ip", "");
                    }
                    dialog.showForAddress(address);
                }).size(210f, 64f);
            }
            try {
                String ip = Core.settings.getString("lastServer", Core.settings.getString("ip", ""));
                if (ip != null && !ip.isEmpty()) {
                    Core.settings.put("scs-last-address", ip);
                }
            } catch (Throwable ignored) {
            }
        });
    }
}
