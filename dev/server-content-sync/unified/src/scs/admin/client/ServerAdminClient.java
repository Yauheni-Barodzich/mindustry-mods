package scs.admin.client;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;
import scs.*;

/** Client entry for Server Admin. */
public class ServerAdminClient extends Mod {
    private AdminDialog dialog;
    private boolean buttonAdded;

    @Override
    public void init() {
        Core.settings.defaults("admin-last-address", "");

        Events.on(ClientLoadEvent.class, e -> {
            dialog = new AdminDialog();
            Vars.ui.settings.addCategory(Loc.get("scs.admin.settings", "Server Admin"), Icon.settings, table -> {
                table.add(Loc.get("scs.admin.settings.hint",
                        "Remote management of your server.\nPassword is read from the server file only.")).wrap().width(420f).left();
                table.row();
                table.button(Loc.get("scs.admin.open", "Open Server Admin"), Icon.play, () -> {
                    try {
                        Vars.ui.settings.hide();
                    } catch (Throwable ignored) {
                    }
                    dialog.showForAddress(Core.settings.getString("admin-last-address", ""));
                }).size(280f, 48f).padTop(8f);
            });
            hookJoin();
            Log.info("[Admin] Client ready.");
        });
    }

    private void hookJoin() {
        if (Vars.ui == null || Vars.ui.join == null) return;
        JoinDialog join = Vars.ui.join;
        join.shown(() -> {
            if (!buttonAdded) {
                buttonAdded = true;
                join.buttons.button(Loc.get("scs.admin.joinButton", "Server Admin"), Icon.settings, () -> {
                    String addr = Core.settings.getString("admin-last-address", "");
                    if (addr.isEmpty()) addr = Core.settings.getString("lastServer", Core.settings.getString("ip", ""));
                    dialog.showForAddress(addr);
                }).size(210f, 64f);
            }
            try {
                String ip = Core.settings.getString("lastServer", Core.settings.getString("ip", ""));
                if (ip != null && !ip.isEmpty()) Core.settings.put("admin-last-address", ip);
            } catch (Throwable ignored) {
            }
        });
    }
}
