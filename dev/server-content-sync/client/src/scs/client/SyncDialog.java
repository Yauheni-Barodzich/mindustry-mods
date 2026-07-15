package scs.client;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import scs.*;

/** Sync dialog: resolve URL, show diff, download with progress. */
public class SyncDialog extends BaseDialog {
    private final SyncService service = new SyncService();
    private final TextField addressField = new TextField("");
    private final TextField urlField = new TextField("");
    private final Label status = new Label("");
    private final Label progressLabel = new Label("");
    private final Table listTable = new Table();
    private Bar progressBar;

    private float progress;
    private SyncService.DiffResult lastDiff;
    private String baseUrl = "";

    public SyncDialog() {
        super(Loc.get("scs.sync.title", "Server Content Sync"));
        setFillParent(true);
        status.setText(Loc.get("scs.sync.hint", "Enter server address (host:port) and fetch manifest."));
        progressBar = new Bar(() -> progressLabel.getText().toString(), () -> mindustry.graphics.Pal.accent, () -> progress);
        cont.clearChildren();
        cont.defaults().pad(4f);
        cont.margin(8f);

        cont.add(Loc.get("scs.sync.server", "Server address")).left();
        cont.row();
        cont.add(addressField).growX().height(40f);
        cont.row();

        cont.add(Loc.get("scs.sync.url", "Sync URL")).left();
        cont.row();
        cont.table(row -> {
            row.add(urlField).height(40f).growX();
            row.button(Loc.get("scs.sync.url.auto", "Auto"), Styles.defaultt, this::fillAutoUrl)
                    .size(100f, 40f).padLeft(6f).get().getLabel().setWrap(false);
        }).growX();
        cont.row();

        cont.table(t -> {
            t.defaults().height(48f).growX().pad(2f);
            t.button(Loc.get("scs.sync.url.load", "Settings URL"), Styles.defaultt, () -> {
                String o = Core.settings.getString("scs-url-override", "").trim();
                if (!o.isEmpty()) urlField.setText(o);
            }).get().getLabel().setWrap(false);
        }).growX();
        cont.row();

        cont.table(t -> {
            t.defaults().height(48f).growX().pad(2f);
            t.button(Loc.get("scs.sync.fetch", "Fetch"), Icon.download, Styles.defaultt, this::fetch)
                    .get().getLabel().setWrap(false);
            t.button(Loc.get("scs.sync.download", "Download"), Icon.downOpen, Styles.defaultt, this::download)
                    .get().getLabel().setWrap(false);
        }).growX();
        cont.row();

        cont.add(status).growX().wrap().left().padTop(4f);
        cont.row();
        cont.add(progressBar).growX().height(24f);
        cont.row();
        cont.add(progressLabel).growX().left();
        cont.row();
        cont.pane(listTable).grow().minHeight(180f);

        addCloseButton();

        addressField.changed(this::fillAutoUrl);
        urlField.setMessageText("http://host:6568");
        addressField.setMessageText("host:6567");
    }

    public void showForAddress(String address) {
        if (address != null) addressField.setText(address);
        String override = Core.settings.getString("scs-url-override", "").trim();
        if (!override.isEmpty()) {
            urlField.setText(override);
        } else {
            fillAutoUrl();
        }
        show();
    }

    private void fillAutoUrl() {
        urlField.setText(SyncService.defaultUrlFromAddress(addressField.getText()));
    }

    private String currentBaseUrl() {
        String typed = urlField.getText().trim();
        if (!typed.isEmpty()) {
            if (typed.endsWith("/")) typed = typed.substring(0, typed.length() - 1);
            return typed;
        }
        return SyncService.resolveBaseUrl(addressField.getText());
    }

    private void fetch() {
        baseUrl = currentBaseUrl();
        if (baseUrl.isEmpty()) {
            status.setText(Loc.get("scs.sync.noUrl", "[scarlet]No sync URL."));
            return;
        }
        String auto = SyncService.defaultUrlFromAddress(addressField.getText());
        if (!baseUrl.equals(auto)) {
            Core.settings.put("scs-url-override", baseUrl);
        }
        if (!addressField.getText().trim().isEmpty()) {
            Core.settings.put("scs-last-address", addressField.getText().trim());
        }
        status.setText(Loc.format("scs.sync.fetching", "Fetching {0} ...", baseUrl));
        listTable.clear();
        progress = 0f;
        Threads.daemon(() -> {
            try {
                String info = service.fetchInfo(baseUrl);
                var manifest = service.fetchManifest(baseUrl);
                var diff = service.diff(manifest);
                Core.app.post(() -> {
                    lastDiff = diff;
                    status.setText("OK: " + info);
                    rebuildList(diff);
                });
            } catch (Throwable t) {
                Log.err(t);
                Core.app.post(() -> status.setText(Loc.format("scs.sync.fetchFail", "[scarlet]Fetch failed: {0}", t.getMessage())));
            }
        });
    }

    private void rebuildList(SyncService.DiffResult diff) {
        listTable.clear();
        listTable.defaults().left().pad(2);

        listTable.add(Loc.format("scs.sync.modsLine", "[accent]Mods[] ok={0} missing={1} outdated={2}",
                diff.okMods, diff.missingMods, diff.outdatedMods)).growX();
        listTable.row();
        listTable.add(Loc.format("scs.sync.mapsLine", "[accent]Maps[] ok={0} missing={1} outdated={2}",
                diff.okMaps, diff.missingMaps, diff.outdatedMaps)).growX();
        listTable.row();
        listTable.add(Loc.format("scs.sync.downloadLine", "Download: {0} file(s), ~{1}",
                diff.toDownload.size, formatBytes(diff.totalBytes()))).growX();
        listTable.row();

        for (ManifestEntry e : diff.toDownload) {
            listTable.add("- [" + e.kind + "] " + e.fileName + " (" + formatBytes(e.size) + ")").growX();
            listTable.row();
        }
        if (diff.extraModFiles.size > 0) {
            listTable.add(Loc.get("scs.sync.extras", "[gray]Local mods not on server (not removed):[]")).growX();
            listTable.row();
            for (String name : diff.extraModFiles) {
                listTable.add("[gray]- " + name + "[]").growX();
                listTable.row();
            }
        }
        if (!diff.needsWork()) {
            status.setText(Loc.get("scs.sync.inSync", "[accent]Already in sync."));
        }
    }

    private void download() {
        if (lastDiff == null || !lastDiff.needsWork()) {
            status.setText(Loc.get("scs.sync.nothing", "Nothing to download. Fetch first."));
            return;
        }
        baseUrl = currentBaseUrl();
        status.setText(Loc.get("scs.sync.downloading", "Downloading..."));
        Seq<ManifestEntry> queue = lastDiff.toDownload.copy();
        service.downloadAll(baseUrl, queue,
                (entry, p) -> Core.app.post(() -> {
                    progress = p;
                    progressLabel.setText(entry.fileName + " — " + (int) (p * 100) + "%");
                }),
                () -> {
                    boolean modsChanged = Core.settings.getBool("scs-last-mods-changed", false);
                    status.setText(Loc.get("scs.sync.done", "[accent]Download complete."));
                    progress = 1f;
                    lastDiff = null;
                    if (modsChanged) {
                        Vars.ui.showCustomConfirm(
                                Loc.get("scs.sync.restartTitle", "Restart required"),
                                Loc.get("scs.sync.restartBody", "Mods were updated. Restart Mindustry to load them, then join the server."),
                                Loc.get("scs.sync.restart", "Restart"),
                                Loc.get("scs.sync.later", "Later"),
                                () -> Core.app.exit(),
                                () -> {}
                        );
                    } else {
                        try {
                            Vars.maps.reload();
                        } catch (Throwable ignored) {
                        }
                        Vars.ui.showInfo(Loc.get("scs.sync.mapsUpdated", "Maps updated."));
                    }
                },
                err -> {
                    status.setText(Loc.format("scs.sync.downloadFail", "[scarlet]Download failed: {0}", err.getMessage()));
                    Log.err(err);
                }
        );
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
}
