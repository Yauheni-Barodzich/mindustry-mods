package scs.admin.client;

import arc.*;
import arc.files.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import scs.*;

/** Remote server management dialog. Password is not saved to disk. */
public class AdminDialog extends BaseDialog {
    private static final float BTN_H = 48f;
    private static final float FIELD_H = 40f;

    private final TextField addressField = new TextField("");
    private final TextField urlField = new TextField("");
    private final TextField passwordField = new TextField("");
    private final Label status = new Label(Loc.get("scs.admin.hint", "Connect with server address and admin password."));
    private final TextArea statusArea = new TextArea("");
    private final TextArea configArea = new TextArea("");
    private final TextArea broadcastArea = new TextArea("");
    private String broadcastType = "chat";
    private final CheckBox spawnClampEnabled = new CheckBox("");
    private final TextField maxEnemySpawnsField = new TextField("3");
    private final TextField minEnemySpawnsField = new TextField("1");
    private final Table modTable = new Table();
    private final Table mapTable = new Table();
    private final TextField uploadPathField = new TextField("");
    private final TextField configPathField = new TextField("rules.hjson");
    private final Table rulesTable = new Table();
    private final Table presetsTable = new Table();
    private final Table templatesTable = new Table();
    private final TextField presetNameField = new TextField("");
    private final ObjectMap<String, CheckBox> ruleChecks = new ObjectMap<>();
    private final ObjectMap<String, TextField> ruleFields = new ObjectMap<>();
    private ObjectMap<String, String> rulesValues = new ObjectMap<>();

    private AdminApiClient api;

    public AdminDialog() {
        super(Loc.get("scs.admin.title", "Server Admin"));
        setFillParent(true);
        passwordField.setPasswordMode(true);
        cont.clearChildren();
        cont.defaults().pad(4f);
        cont.margin(8f);

        cont.pane(p -> {
            p.defaults().pad(3f).growX();

            p.add("[accent]" + Loc.get("scs.admin.connection", "Connection") + "[]").left();
            p.row();

            p.add(Loc.get("scs.admin.server", "Server address")).left();
            p.row();
            p.add(addressField).height(FIELD_H).growX();
            p.row();

            p.add(Loc.get("scs.admin.url", "Admin URL")).left();
            p.row();
            p.table(row -> {
                row.add(urlField).height(FIELD_H).growX();
                row.button(Loc.get("scs.admin.url.auto", "Auto"), Styles.defaultt, this::fillAutoUrl)
                        .size(100f, FIELD_H).padLeft(6f).get().getLabel().setWrap(false);
            }).growX();
            p.row();

            p.add(Loc.get("scs.admin.password", "Password (empty = use local admin.password)")).left();
            p.row();
            p.add(passwordField).height(FIELD_H).growX();
            p.row();

            p.table(row -> {
                row.defaults().height(BTN_H).growX().pad(2f);
                row.button(Loc.get("scs.admin.login", "Login"), Icon.ok, Styles.defaultt, this::login)
                        .get().getLabel().setWrap(false);
                row.button(Loc.get("scs.admin.quickLogin", "Quick login"), Icon.play, Styles.defaultt, this::quickLogin)
                        .get().getLabel().setWrap(false);
            }).growX().padTop(4f);
            p.row();

            p.add(status).growX().wrap().left().padTop(4f).padBottom(8f);
            p.row();

            p.add("[accent]" + Loc.get("scs.admin.serverSection", "Server") + "[]").left();
            p.row();
            p.table(row -> {
                row.defaults().height(BTN_H).growX().pad(2f);
                var a = row.button(Loc.get("scs.admin.refresh", "Refresh status"), Icon.refresh, Styles.defaultt, this::refreshStatus).get();
                a.getLabel().setWrap(false);
                var b = row.button(Loc.get("scs.admin.restart", Loc.get("scs.admin.restartTitle", "Restart server")), Icon.trash, Styles.defaultt, this::restartServer).get();
                b.getLabel().setWrap(false);
            }).growX();
            p.row();
            p.add(statusArea).height(90f).growX().padBottom(8f);
            p.row();

            p.add("[accent]" + Loc.get("scs.admin.broadcast", "Broadcast") + "[]").left();
            p.row();
            p.add(Loc.get("scs.admin.broadcast.hint", "Message to all online players. No client mod required.")).wrap().growX().left();
            p.row();
            p.add(broadcastArea).height(70f).growX();
            p.row();
            p.table(row -> {
                row.defaults().height(BTN_H).growX().pad(2f);
                row.button(Loc.get("scs.admin.broadcast.chat", "Chat"), Styles.defaultt, () -> {
                    broadcastType = "chat";
                    sendBroadcast();
                }).get().getLabel().setWrap(false);
                row.button(Loc.get("scs.admin.broadcast.announce", "Announce"), Styles.defaultt, () -> {
                    broadcastType = "announce";
                    sendBroadcast();
                }).get().getLabel().setWrap(false);
                row.button(Loc.get("scs.admin.broadcast.toast", "Toast"), Styles.defaultt, () -> {
                    broadcastType = "toast";
                    sendBroadcast();
                }).get().getLabel().setWrap(false);
            }).growX().padBottom(8f);
            p.row();

            p.add("[accent]" + Loc.get("scs.admin.spawnClamp", "Enemy spawn clamp") + "[]").left();
            p.row();
            p.add(Loc.get("scs.admin.spawnClamp.hint",
                    "Limit enemy spawn points after map load. Saved to config/mods/server-admin/config.hjson.")).wrap().growX().left();
            p.row();
            spawnClampEnabled.setText(Loc.get("scs.admin.spawnClamp.enabled", "Enabled"));
            if (spawnClampEnabled.getLabel() != null) spawnClampEnabled.getLabel().setWrap(false);
            p.add(spawnClampEnabled).left().growX();
            p.row();
            p.table(row -> {
                row.add(Loc.get("scs.admin.spawnClamp.max", "Max")).left().padRight(6f);
                row.add(maxEnemySpawnsField).width(80f).height(FIELD_H).padRight(12f);
                row.add(Loc.get("scs.admin.spawnClamp.min", "Min")).left().padRight(6f);
                row.add(minEnemySpawnsField).width(80f).height(FIELD_H);
            }).left().growX();
            p.row();
            p.table(row -> {
                row.defaults().height(BTN_H).growX().pad(2f);
                row.button(Loc.get("scs.admin.spawnClamp.load", "Load"), Icon.download, Styles.defaultt, this::loadSpawnClamp)
                        .get().getLabel().setWrap(false);
                row.button(Loc.get("scs.admin.spawnClamp.save", "Save"), Icon.save, Styles.defaultt, this::saveSpawnClamp)
                        .get().getLabel().setWrap(false);
                row.button(Loc.get("scs.admin.spawnClamp.apply", "Apply now"), Icon.play, Styles.defaultt, this::applySpawnClampNow)
                        .get().getLabel().setWrap(false);
            }).growX().padBottom(8f);
            p.row();

            // Mods | Maps — two columns
            p.table(cols -> {
                cols.defaults().top().grow();

                cols.table(left -> {
                    left.defaults().growX();
                    left.add("[accent]" + Loc.get("scs.admin.mods", "Mods") + "[]").left();
                    left.row();
                    left.pane(modTable).height(160f).growX().padBottom(4f);
                    left.row();
                    left.button(Loc.get("scs.admin.uploadMod", "Upload mod"), Icon.upload, Styles.defaultt, () -> upload(false))
                            .height(BTN_H).growX().get().getLabel().setWrap(false);
                }).grow().padRight(6f);

                cols.table(right -> {
                    right.defaults().growX();
                    right.add("[accent]" + Loc.get("scs.admin.maps", "Maps") + "[]").left();
                    right.row();
                    right.pane(mapTable).height(160f).growX().padBottom(4f);
                    right.row();
                    right.button(Loc.get("scs.admin.uploadMap", "Upload map"), Icon.upload, Styles.defaultt, () -> upload(true))
                            .height(BTN_H).growX().get().getLabel().setWrap(false);
                }).grow().padLeft(6f);
            }).growX().padBottom(6f);
            p.row();

            p.add(Loc.get("scs.admin.localPath", "Local file path (for upload)")).left();
            p.row();
            p.add(uploadPathField).height(FIELD_H).growX().padBottom(8f);
            p.row();

            // --- Rules editor ---
            p.add("[accent]" + Loc.get("scs.admin.rules", "Server Rules") + "[] " + Loc.get("scs.admin.rules.file", "(config/rules.hjson)")).left();
            p.row();

            // Presets
            p.add("[accent]" + Loc.get("scs.admin.presets", "Presets") + "[]").left().padTop(4f);
            p.row();
            p.add(Loc.get("scs.admin.presets.hint", "Stored on server in config/presets/. Applying requires server restart.")).wrap().growX().left();
            p.row();
            p.pane(presetsTable).height(110f).growX().padBottom(4f);
            p.row();
            p.table(row -> {
                row.add(Loc.get("scs.admin.presets.name", "Name")).left().padRight(6f);
                row.add(presetNameField).height(FIELD_H).growX();
            }).growX();
            p.row();
            p.table(row -> {
                row.defaults().height(BTN_H).growX().pad(2f);
                row.button(Loc.get("scs.admin.presets.refresh", "Refresh"), Icon.refresh, Styles.defaultt, this::refreshPresets)
                        .get().getLabel().setWrap(false);
                row.button(Loc.get("scs.admin.presets.saveAs", "Save as"), Icon.save, Styles.defaultt, this::savePresetAs)
                        .get().getLabel().setWrap(false);
            }).growX();
            p.row();
            p.table(row -> {
                row.defaults().height(BTN_H).growX().pad(2f);
                row.button(Loc.get("scs.admin.presets.updateDefault", "Update default"), Styles.defaultt, this::updateDefaultPreset)
                        .get().getLabel().setWrap(false);
            }).growX();
            p.row();
            p.add(Loc.get("scs.admin.presets.templates", "Mode templates")).left().padTop(2f);
            p.row();
            p.add(templatesTable).growX().padBottom(4f);
            p.row();

            p.table(row -> {
                row.defaults().height(BTN_H).growX().pad(2f);
                row.button(Loc.get("scs.admin.rules.load", "Load rules"), Icon.download, Styles.defaultt, this::loadRules)
                        .get().getLabel().setWrap(false);
                row.button(Loc.get("scs.admin.rules.save", "Save rules"), Icon.save, Styles.defaultt, this::saveRules)
                        .get().getLabel().setWrap(false);
            }).growX();
            p.row();
            p.table(row -> {
                row.defaults().height(BTN_H).growX().pad(2f);
                row.button(Loc.get("scs.admin.rules.apply", "Apply live"), Icon.play, Styles.defaultt, this::applyRules)
                        .get().getLabel().setWrap(false);
                row.button(Loc.get("scs.admin.rules.reset", "Reset defaults"), Icon.refresh, Styles.defaultt, this::resetRules)
                        .get().getLabel().setWrap(false);
            }).growX();
            p.row();
            p.pane(rulesTable).height(320f).growX().padBottom(2f);
            p.row();
            p.add(Loc.get("scs.admin.rules.hintsHint", "[gray]Hover a setting for a tooltip (from Rules.java).[]")).wrap().growX().left().padBottom(8f);
            p.row();

            p.add("[accent]" + Loc.get("scs.admin.config", "Config (raw)") + "[]").left();
            p.row();
            p.add(Loc.get("scs.admin.config.path", "Path")).left();
            p.row();
            p.add(configPathField).height(FIELD_H).growX();
            p.row();
            p.table(row -> {
                row.defaults().height(BTN_H).growX().pad(2f);
                row.button(Loc.get("scs.admin.config.load", "Load config"), Icon.download, Styles.defaultt, this::loadConfig)
                        .get().getLabel().setWrap(false);
                row.button(Loc.get("scs.admin.config.save", "Save config"), Icon.save, Styles.defaultt, this::saveConfig)
                        .get().getLabel().setWrap(false);
            }).growX();
            p.row();
            p.add(configArea).height(100f).growX();
        }).grow().top();

        addCloseButton();
        addressField.setMessageText("host:6567");
        urlField.setMessageText("http://host:6569");
        passwordField.setMessageText("password");
        uploadPathField.setMessageText("C:\\path\\to\\file.zip");
        presetNameField.setMessageText("my-preset");
    }

    public void showForAddress(String address) {
        if (address != null) addressField.setText(address);
        fillAutoUrl();
        updatePasswordHint();
        show();
        // Auto quick-login when local password file exists
        if (LocalAdminPassword.hasLocalPassword()) {
            Core.app.post(this::quickLogin);
        }
    }

    private void fillAutoUrl() {
        urlField.setText(AdminApiClient.defaultUrlFromAddress(addressField.getText()));
    }

    private void updatePasswordHint() {
        Fi file = LocalAdminPassword.passwordFile();
        if (LocalAdminPassword.hasLocalPassword()) {
            status.setText(Loc.format("scs.admin.localFound", "[accent]Local admin.password found[] — Quick login uses it.\n{0}", file.absolutePath()));
            passwordField.setMessageText("(using local file)");
        } else {
            status.setText(Loc.format("scs.admin.localMissing", "No local admin.password. Put copy at:\n{0}", file.absolutePath()));
            passwordField.setMessageText("password");
        }
    }

    /** Login using typed password, or local admin.password if field empty. */
    private void login() {
        doLogin(false);
    }

    /** Always prefer local admin.password file. */
    private void quickLogin() {
        doLogin(true);
    }

    private void doLogin(boolean preferLocalFile) {
        String url = urlField.getText().trim();
        if (url.isEmpty()) url = AdminApiClient.defaultUrlFromAddress(addressField.getText());
        if (url.isEmpty()) {
            status.setText(Loc.get("scs.admin.noUrl", "[scarlet]No admin URL."));
            return;
        }
        if (!addressField.getText().trim().isEmpty()) {
            Core.settings.put("admin-last-address", addressField.getText().trim());
        }

        String typed = passwordField.getText();
        String pass;
        String source;
        if (preferLocalFile || typed == null || typed.isEmpty()) {
            pass = LocalAdminPassword.read();
            source = Loc.get("scs.admin.source.local", "local admin.password");
            if (pass == null || pass.isEmpty()) {
                if (typed != null && !typed.isEmpty()) {
                    pass = typed;
                    source = Loc.get("scs.admin.source.field", "password field");
                } else {
                    status.setText(Loc.get("scs.admin.noPassword", "[scarlet]No password: type it or create local admin.password"));
                    return;
                }
            }
        } else {
            pass = typed;
            source = Loc.get("scs.admin.source.field", "password field");
        }

        api = new AdminApiClient(url);
        status.setText(Loc.format("scs.admin.loggingIn", "Logging in via {0}...", source));
        final String passFinal = pass;
        final String sourceFinal = source;
        Threads.daemon(() -> {
            try {
                api.login(passFinal);
                Core.app.post(() -> {
                    status.setText(Loc.format("scs.admin.loggedIn", "[accent]Logged in[] ({0}).", sourceFinal));
                    passwordField.setText("");
                    refreshAll();
                });
            } catch (Throwable t) {
                Core.app.post(() -> status.setText(Loc.format("scs.admin.loginFail", "[scarlet]Login failed: {0}", t.getMessage())));
            }
        });
    }

    private void refreshAll() {
        refreshStatus();
        refreshMods();
        refreshMaps();
        loadRules();
        refreshPresets();
        loadSpawnClamp();
    }

    private void refreshPresets() {
        if (api == null || !api.isLoggedIn()) return;
        Threads.daemon(() -> {
            try {
                String presetsJson = api.listPresets();
                String templatesJson = api.listTemplates();
                Core.app.post(() -> {
                    rebuildPresetsUi(presetsJson);
                    rebuildTemplatesUi(templatesJson);
                });
            } catch (Throwable t) {
                Core.app.post(() -> status.setText(Loc.format("scs.admin.presets.fail", "[scarlet]Presets: {0}", t.getMessage())));
            }
        });
    }

    private void rebuildTemplatesUi(String jsonArray) {
        templatesTable.clear();
        templatesTable.defaults().height(BTN_H).growX().pad(2f);
        JsonValue arr = new JsonReader().parse(jsonArray);
        if (!arr.isArray() || arr.size == 0) {
            templatesTable.add(Loc.get("scs.admin.presets.noTemplates", "[gray](no templates)[]")).left();
            return;
        }
        for (JsonValue v = arr.child; v != null; v = v.next) {
            String id = v.getString("id", "");
            String label = v.getString("label", id);
            String btn = Loc.get("scs.admin.presets.tpl." + id, Loc.format("scs.admin.presets.tplFallback", "Template {0}", label));
            String tid = id;
            templatesTable.button(btn, Styles.defaultt, () -> installModeTemplate(tid))
                    .get().getLabel().setWrap(false);
        }
    }

    private void rebuildPresetsUi(String jsonArray) {
        presetsTable.clear();
        presetsTable.defaults().pad(2f).left().growX();
        JsonValue arr = new JsonReader().parse(jsonArray);
        if (!arr.isArray() || arr.size == 0) {
            presetsTable.add(Loc.get("scs.admin.empty", "[gray](empty)[]")).left();
            return;
        }
        for (JsonValue v = arr.child; v != null; v = v.next) {
            String name = v.getString("name", "");
            boolean prot = v.getBoolean("protected", false);
            presetsTable.table(row -> {
                String text = prot ? name + " [" + Loc.get("scs.admin.presets.locked", "default") + "]" : name;
                Label label = new Label(text, Styles.outlineLabel);
                label.setWrap(false);
                row.add(label).left().growX();
                row.button(Loc.get("scs.admin.presets.apply", "Apply"), Styles.defaultt, () -> applyPreset(name))
                        .width(90f).height(34f).get().getLabel().setWrap(false);
                if (!prot) {
                    row.button(Loc.get("scs.admin.del", "Del"), Styles.defaultt, () -> deletePreset(name))
                            .width(70f).height(34f).padLeft(4f).get().getLabel().setWrap(false);
                }
            }).growX();
            presetsTable.row();
        }
    }

    private void savePresetAs() {
        if (api == null || !api.isLoggedIn()) return;
        String name = presetNameField.getText().trim();
        if (name.isEmpty()) {
            status.setText(Loc.get("scs.admin.presets.needName", "[scarlet]Enter preset name."));
            return;
        }
        String safe = name.toLowerCase().replaceAll("[^a-z0-9_\\-]", "");
        if ("default".equals(safe)) {
            status.setText(Loc.get("scs.admin.presets.useUpdateDefault",
                    "[scarlet]Cannot save as \"default\". Use \"Update default\"."));
            return;
        }
        // save current editor values to rules first, then copy rules -> preset
        collectRulesFromUi();
        String text = RulesHjson.write(rulesValues);
        Threads.daemon(() -> {
            try {
                api.putRules(text);
                api.putPreset(name, text);
                Core.app.post(() -> {
                    status.setText(Loc.format("scs.admin.presets.saved", "[accent]Preset saved: {0}", name));
                    refreshPresets();
                });
            } catch (Throwable t) {
                Core.app.post(() -> status.setText(Loc.format("scs.admin.presets.fail", "[scarlet]Presets: {0}", t.getMessage())));
            }
        });
    }

    private void updateDefaultPreset() {
        if (api == null || !api.isLoggedIn()) return;
        Vars.ui.showCustomConfirm(
                Loc.get("scs.admin.presets.updateDefaultTitle", "Update default"),
                Loc.get("scs.admin.presets.updateDefaultBody",
                        "Overwrite the protected \"default\" preset with current rules from the editor?\nThis does not restart the server."),
                Loc.get("scs.admin.presets.updateDefault", "Update default"),
                Loc.get("scs.admin.cancel", "Cancel"),
                () -> {
                    collectRulesFromUi();
                    String text = RulesHjson.write(rulesValues);
                    Threads.daemon(() -> {
                        try {
                            api.putRules(text);
                            api.updateDefaultPreset(text);
                            Core.app.post(() -> {
                                status.setText(Loc.get("scs.admin.presets.defaultUpdated", "[accent]Default preset updated."));
                                refreshPresets();
                            });
                        } catch (Throwable t) {
                            Core.app.post(() -> status.setText(Loc.format("scs.admin.presets.fail", "[scarlet]Presets: {0}", t.getMessage())));
                        }
                    });
                },
                () -> {}
        );
    }

    private void installModeTemplate(String templateId) {
        if (api == null || !api.isLoggedIn()) return;
        Threads.daemon(() -> {
            try {
                String resp = api.installModeTemplate(templateId);
                String name = extractJsonString(resp, "name");
                if (name == null || name.isEmpty()) name = "mode-" + templateId;
                String shown = name;
                Core.app.post(() -> {
                    status.setText(Loc.format("scs.admin.presets.tplInstalled",
                            "[accent]Template installed as preset: {0}", shown));
                    refreshPresets();
                });
            } catch (Throwable t) {
                Core.app.post(() -> status.setText(Loc.format("scs.admin.presets.fail", "[scarlet]Presets: {0}", t.getMessage())));
            }
        });
    }

    private void applyPreset(String name) {
        if (api == null || !api.isLoggedIn()) return;
        Vars.ui.showCustomConfirm(
                Loc.get("scs.admin.presets.applyTitle", "Apply preset"),
                Loc.format("scs.admin.presets.applyBody",
                        "Write preset \"{0}\" to rules.hjson.\n[accent]Server restart is required[] after that. Restart now?",
                        name),
                Loc.get("scs.admin.presets.applyRestart", "Apply + Restart"),
                Loc.get("scs.admin.cancel", "Cancel"),
                () -> Threads.daemon(() -> {
                    try {
                        api.applyPreset(name);
                        Core.app.post(() -> {
                            status.setText(Loc.format("scs.admin.presets.applied",
                                    "[accent]Preset {0} applied. Restarting server...", name));
                            loadRules();
                            refreshPresets();
                        });
                        // restart after short delay so UI updates
                        try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                        api.restart();
                    } catch (Throwable t) {
                        Core.app.post(() -> status.setText(Loc.format("scs.admin.presets.fail", "[scarlet]Presets: {0}", t.getMessage())));
                    }
                }),
                () -> {}
        );
    }

    private void deletePreset(String name) {
        Vars.ui.showCustomConfirm(
                Loc.get("scs.admin.deleteTitle", "Delete"),
                Loc.format("scs.admin.presets.deleteBody", "Delete preset \"{0}\"?", name),
                Loc.get("scs.admin.delete", "Delete"),
                Loc.get("scs.admin.cancel", "Cancel"),
                () -> Threads.daemon(() -> {
                    try {
                        api.deletePreset(name);
                        Core.app.post(() -> {
                            status.setText(Loc.format("scs.admin.presets.deleted", "[accent]Deleted preset {0}", name));
                            refreshPresets();
                        });
                    } catch (Throwable t) {
                        Core.app.post(() -> status.setText(Loc.format("scs.admin.presets.fail", "[scarlet]Presets: {0}", t.getMessage())));
                    }
                }),
                () -> {}
        );
    }

    private void loadRules() {
        if (api == null || !api.isLoggedIn()) return;
        Threads.daemon(() -> {
            try {
                String resp = api.getRules();
                String content = extractContent(resp);
                ObjectMap<String, String> parsed = RulesHjson.parse(content);
                // fill missing with defaults
                for (RulesFields.Field f : RulesFields.ALL) {
                    if (!parsed.containsKey(f.key)) parsed.put(f.key, f.def);
                }
                Core.app.post(() -> {
                    rulesValues = parsed;
                    rebuildRulesUi();
                    status.setText(Loc.get("scs.admin.rules.loaded", "[accent]Rules loaded."));
                });
            } catch (Throwable t) {
                Core.app.post(() -> status.setText(Loc.format("scs.admin.rules.loadFail", "[scarlet]Rules load: {0}", t.getMessage())));
            }
        });
    }

    private void saveRules() {
        if (api == null || !api.isLoggedIn()) return;
        collectRulesFromUi();
        String text = RulesHjson.write(rulesValues);
        Threads.daemon(() -> {
            try {
                api.putRules(text);
                Core.app.post(() -> status.setText(Loc.get("scs.admin.rules.saved", "[accent]Rules saved to rules.hjson")));
            } catch (Throwable t) {
                Core.app.post(() -> status.setText(Loc.format("scs.admin.rules.saveFail", "[scarlet]Rules save: {0}", t.getMessage())));
            }
        });
    }

    private void applyRules() {
        if (api == null || !api.isLoggedIn()) return;
        Vars.ui.showCustomConfirm(
                Loc.get("scs.admin.rules.applyTitle", "Apply rules"),
                Loc.get("scs.admin.rules.applyBody", "Save and apply rules to the running game now?"),
                Loc.get("scs.admin.apply", "Apply"),
                Loc.get("scs.admin.cancel", "Cancel"),
                () -> {
                    collectRulesFromUi();
                    String text = RulesHjson.write(rulesValues);
                    Threads.daemon(() -> {
                        try {
                            api.putRules(text);
                            api.applyRules();
                            Core.app.post(() -> status.setText(Loc.get("scs.admin.rules.applyOk", "[accent]Rules saved + applied live.")));
                        } catch (Throwable t) {
                            Core.app.post(() -> status.setText(Loc.format("scs.admin.rules.applyFail", "[scarlet]Apply: {0}", t.getMessage())));
                        }
                    });
                },
                () -> {}
        );
    }

    private void resetRules() {
        if (api == null || !api.isLoggedIn()) return;
        Vars.ui.showCustomConfirm(
                Loc.get("scs.admin.rules.resetTitle", "Reset rules"),
                Loc.get("scs.admin.rules.resetBody", "Overwrite rules.hjson with defaults?"),
                Loc.get("scs.admin.reset", "Reset"),
                Loc.get("scs.admin.cancel", "Cancel"),
                () -> Threads.daemon(() -> {
                    try {
                        String resp = api.resetRules();
                        String content = extractContent(resp);
                        ObjectMap<String, String> parsed = RulesHjson.parse(content);
                        for (RulesFields.Field f : RulesFields.ALL) {
                            if (!parsed.containsKey(f.key)) parsed.put(f.key, f.def);
                        }
                        Core.app.post(() -> {
                            rulesValues = parsed;
                            rebuildRulesUi();
                            status.setText(Loc.get("scs.admin.rules.resetOk", "[accent]Rules reset to defaults."));
                        });
                    } catch (Throwable t) {
                        Core.app.post(() -> status.setText(Loc.format("scs.admin.rules.resetFail", "[scarlet]Reset: {0}", t.getMessage())));
                    }
                }),
                () -> {}
        );
    }

    private void collectRulesFromUi() {
        for (ObjectMap.Entry<String, CheckBox> e : ruleChecks) {
            rulesValues.put(e.key, e.value.isChecked() ? "true" : "false");
        }
        for (ObjectMap.Entry<String, TextField> e : ruleFields) {
            String v = e.value.getText().trim();
            if (!v.isEmpty()) rulesValues.put(e.key, v);
        }
    }

    private void rebuildRulesUi() {
        rulesTable.clear();
        ruleChecks.clear();
        ruleFields.clear();
        rulesTable.top().left();
        rulesTable.defaults().pad(3f).left().top().growX();

        String lastGroup = "";
        Table leftCol = null;
        Table rightCol = null;
        int inGroup = 0;

        for (RulesFields.Field f : RulesFields.ALL) {
            if (!f.group.equals(lastGroup)) {
                lastGroup = f.group;
                inGroup = 0;
                rulesTable.add("[accent]" + Loc.get("scs.rules.group." + f.group, f.group) + "[]").left().growX().padTop(10f).padBottom(4f);
                rulesTable.row();

                Table groupRow = new Table();
                leftCol = new Table();
                rightCol = new Table();
                leftCol.top().left();
                rightCol.top().left();
                leftCol.defaults().left().top().padBottom(6f).growX();
                rightCol.defaults().left().top().padBottom(6f).growX();
                groupRow.add(leftCol).top().left().growX().uniformX().padRight(12f);
                groupRow.add(rightCol).top().left().growX().uniformX();
                rulesTable.add(groupRow).growX();
                rulesTable.row();
            }

            Table target = (inGroup % 2 == 0) ? leftCol : rightCol;
            inGroup++;
            String val = rulesValues.get(f.key, f.def);

            if ("bool".equals(f.type)) {
                CheckBox box = new CheckBox(Loc.get("scs.rules." + f.key, f.label));
                box.setChecked("true".equalsIgnoreCase(val));
                if (box.getLabel() != null) box.getLabel().setWrap(false);
                ruleChecks.put(f.key, box);
                target.table(row -> {
                    row.add(box).left().growX();
                    attachRuleHint(row, f.key);
                }).left().growX();
                target.row();
            } else {
                final String key = f.key;
                final String label = Loc.get("scs.rules." + f.key, f.label);
                final String v0 = val;
                // label on its own line, value below — no overlap
                target.table(block -> {
                    block.left();
                    Label lab = new Label(label);
                    lab.setWrap(false);
                    block.add(lab).left().padBottom(2f);
                    block.row();
                    TextField field = new TextField(v0);
                    ruleFields.put(key, field);
                    block.add(field).width(120f).height(36f).left();
                    attachRuleHint(block, key);
                }).left().growX();
                target.row();
            }
        }
    }

    /** Hover / tap tooltip with Rules.java meaning. */
    private static void attachRuleHint(Element el, String key) {
        String tip = Loc.get("scs.rules.hint." + key, "");
        if (tip == null || tip.isEmpty()) return;
        Tooltip tooltip = new Tooltip(t -> {
            t.background(Styles.black8);
            t.margin(8f);
            t.add("[accent]" + key + "[]").left();
            t.row();
            t.add(tip).width(320f).wrap().left().padTop(4f);
        });
        tooltip.allowMobile = true;
        el.addListener(tooltip);
    }

    private static String extractJsonString(String resp, String key) {
        if (resp == null || key == null) return null;
        try {
            JsonValue root = new JsonReader().parse(resp);
            return root.getString(key, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractContent(String resp) {
        // reuse content extractor from config responses
        String search = "\"content\":\"";
        int i = resp.indexOf(search);
        if (i < 0) return resp;
        int start = i + search.length();
        StringBuilder sb = new StringBuilder();
        for (int p = start; p < resp.length(); p++) {
            char c = resp.charAt(p);
            if (c == '\\' && p + 1 < resp.length()) {
                char n = resp.charAt(p + 1);
                if (n == 'n') sb.append('\n');
                else if (n == 'r') sb.append('\r');
                else if (n == 't') sb.append('\t');
                else if (n == '"') sb.append('"');
                else if (n == '\\') sb.append('\\');
                else sb.append(n);
                p++;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void refreshStatus() {
        if (api == null || !api.isLoggedIn()) {
            status.setText(Loc.get("scs.admin.loginFirst", "Login first."));
            return;
        }
        Threads.daemon(() -> {
            try {
                String s = api.status();
                Core.app.post(() -> statusArea.setText(prettyJson(s)));
            } catch (Throwable t) {
                Core.app.post(() -> status.setText("[scarlet]" + t.getMessage()));
            }
        });
    }

    private void restartServer() {
        if (api == null || !api.isLoggedIn()) return;
        Vars.ui.showCustomConfirm(
                Loc.get("scs.admin.restartTitle", "Restart server"),
                Loc.get("scs.admin.restartBody", "Server will stop and should restart via systemd/supervisor. Continue?"),
                Loc.get("scs.admin.restart", "Restart server"),
                Loc.get("scs.admin.cancel", "Cancel"),
                () -> Threads.daemon(() -> {
                    try {
                        api.restart();
                        Core.app.post(() -> status.setText(Loc.get("scs.admin.restartOk", "[accent]Restart command sent.")));
                    } catch (Throwable t) {
                        Core.app.post(() -> status.setText("[scarlet]" + t.getMessage()));
                    }
                }),
                () -> {}
        );
    }

    private void sendBroadcast() {
        if (api == null || !api.isLoggedIn()) {
            status.setText(Loc.get("scs.admin.loginFirst", "Login first."));
            return;
        }
        String msg = broadcastArea.getText();
        if (msg == null || msg.trim().isEmpty()) {
            status.setText(Loc.get("scs.admin.broadcast.empty", "[scarlet]Enter a message."));
            return;
        }
        final String type = broadcastType == null ? "chat" : broadcastType;
        Threads.daemon(() -> {
            try {
                String resp = api.broadcast(msg.trim(), type);
                int players = 0;
                try {
                    JsonValue root = new JsonReader().parse(resp);
                    players = root.getInt("players", 0);
                } catch (Throwable ignored) {
                }
                final int n = players;
                Core.app.post(() -> status.setText(Loc.format("scs.admin.broadcast.ok",
                        "[accent]Sent ({0}) to {1} player(s).", type, n)));
            } catch (Throwable t) {
                Core.app.post(() -> status.setText(Loc.format("scs.admin.broadcast.fail",
                        "[scarlet]Broadcast: {0}", t.getMessage())));
            }
        });
    }

    private void loadSpawnClamp() {
        if (api == null || !api.isLoggedIn()) return;
        Threads.daemon(() -> {
            try {
                String resp = api.getSpawnClamp();
                JsonValue root = new JsonReader().parse(resp);
                boolean en = root.getBoolean("spawnClampEnabled", true);
                int max = root.getInt("maxEnemySpawns", 3);
                int min = root.getInt("minEnemySpawns", 1);
                Core.app.post(() -> {
                    spawnClampEnabled.setChecked(en);
                    maxEnemySpawnsField.setText(String.valueOf(max));
                    minEnemySpawnsField.setText(String.valueOf(min));
                });
            } catch (Throwable t) {
                Core.app.post(() -> status.setText(Loc.format("scs.admin.spawnClamp.fail",
                        "[scarlet]Spawn clamp: {0}", t.getMessage())));
            }
        });
    }

    private void saveSpawnClamp() {
        if (api == null || !api.isLoggedIn()) {
            status.setText(Loc.get("scs.admin.loginFirst", "Login first."));
            return;
        }
        int max, min;
        try {
            max = Integer.parseInt(maxEnemySpawnsField.getText().trim());
            min = Integer.parseInt(minEnemySpawnsField.getText().trim());
        } catch (Exception e) {
            status.setText(Loc.get("scs.admin.spawnClamp.badNumber", "[scarlet]Max/Min must be integers."));
            return;
        }
        boolean en = spawnClampEnabled.isChecked();
        Threads.daemon(() -> {
            try {
                api.putSpawnClamp(en, max, min);
                Core.app.post(() -> status.setText(Loc.get("scs.admin.spawnClamp.saved",
                        "[accent]Spawn clamp saved to server config.")));
            } catch (Throwable t) {
                Core.app.post(() -> status.setText(Loc.format("scs.admin.spawnClamp.fail",
                        "[scarlet]Spawn clamp: {0}", t.getMessage())));
            }
        });
    }

    private void applySpawnClampNow() {
        if (api == null || !api.isLoggedIn()) {
            status.setText(Loc.get("scs.admin.loginFirst", "Login first."));
            return;
        }
        Threads.daemon(() -> {
            try {
                // save first so apply uses latest values
                int max = Integer.parseInt(maxEnemySpawnsField.getText().trim());
                int min = Integer.parseInt(minEnemySpawnsField.getText().trim());
                api.putSpawnClamp(spawnClampEnabled.isChecked(), max, min);
                String resp = api.applySpawnClamp();
                Core.app.post(() -> status.setText(Loc.format("scs.admin.spawnClamp.applied",
                        "[accent]Spawn clamp applied. {0}", resp)));
            } catch (Throwable t) {
                Core.app.post(() -> status.setText(Loc.format("scs.admin.spawnClamp.fail",
                        "[scarlet]Spawn clamp: {0}", t.getMessage())));
            }
        });
    }

    private void refreshMods() {
        if (api == null || !api.isLoggedIn()) return;
        Threads.daemon(() -> {
            try {
                String json = api.listMods();
                Core.app.post(() -> buildFileTable(modTable, json, false));
            } catch (Throwable t) {
                Core.app.post(() -> status.setText("[scarlet]" + t.getMessage()));
            }
        });
    }

    private void refreshMaps() {
        if (api == null || !api.isLoggedIn()) return;
        Threads.daemon(() -> {
            try {
                String json = api.listMaps();
                Core.app.post(() -> buildFileTable(mapTable, json, true));
            } catch (Throwable t) {
                Core.app.post(() -> status.setText("[scarlet]" + t.getMessage()));
            }
        });
    }

    private void buildFileTable(Table table, String jsonArray, boolean maps) {
        table.clear();
        table.defaults().pad(2f).left();
        JsonValue arr = new JsonReader().parse(jsonArray);
        if (!arr.isArray()) {
            table.add(Loc.get("scs.admin.empty", "[gray](empty)[]")).left();
            return;
        }
        for (JsonValue v = arr.child; v != null; v = v.next) {
            String name = v.getString("name", "");
            long size = v.getLong("size", 0);
            table.table(row -> {
                row.add(name).left().growX().wrap().width(180f);
                row.add("[gray]" + formatBytes(size) + "[]").right().padLeft(4f).padRight(6f);
                row.button(Loc.get("scs.admin.del", "Del"), Styles.defaultt, () -> deleteFile(name, maps))
                        .size(70f, 34f).get().getLabel().setWrap(false);
            }).growX();
            table.row();
        }
    }

    private void deleteFile(String name, boolean maps) {
        Vars.ui.showCustomConfirm(Loc.get("scs.admin.deleteTitle", "Delete"), Loc.format("scs.admin.deleteBody", "Delete {0}?", name), Loc.get("scs.admin.delete", "Delete"), Loc.get("scs.admin.cancel", "Cancel"), () -> Threads.daemon(() -> {
            try {
                if (maps) api.deleteMap(name);
                else api.deleteMod(name);
                Core.app.post(() -> {
                    status.setText(Loc.format("scs.admin.deleted", "[accent]Deleted {0}", name));
                    if (maps) refreshMaps();
                    else refreshMods();
                });
            } catch (Throwable t) {
                Core.app.post(() -> status.setText("[scarlet]" + t.getMessage()));
            }
        }), () -> {});
    }

    private void upload(boolean asMap) {
        if (api == null || !api.isLoggedIn()) return;
        String path = uploadPathField.getText().trim();
        if (path.isEmpty()) {
            status.setText(Loc.get("scs.admin.setPath", "Set local file path."));
            return;
        }
        Fi file = Core.files.absolute(path);
        if (!file.exists()) {
            status.setText(Loc.format("scs.admin.fileNotFound", "[scarlet]File not found: {0}", path));
            return;
        }
        Threads.daemon(() -> {
            try {
                byte[] data = file.readBytes();
                String name = file.name();
                if (asMap) api.uploadMap(name, data);
                else api.uploadMod(name, data);
                Core.app.post(() -> {
                    status.setText(Loc.format("scs.admin.uploaded", "[accent]Uploaded {0}", name));
                    if (asMap) refreshMaps();
                    else refreshMods();
                });
            } catch (Throwable t) {
                Core.app.post(() -> status.setText("[scarlet]" + t.getMessage()));
            }
        });
    }

    private void loadConfig() {
        if (api == null || !api.isLoggedIn()) return;
        String path = configPathField.getText().trim();
        Threads.daemon(() -> {
            try {
                String resp = api.getConfig(path);
                String content = jsonField(resp, "content");
                Core.app.post(() -> {
                    configArea.setText(unescape(content));
                    status.setText(Loc.format("scs.admin.config.loaded", "[accent]Loaded {0}", path));
                });
            } catch (Throwable t) {
                Core.app.post(() -> status.setText("[scarlet]" + t.getMessage()));
            }
        });
    }

    private void saveConfig() {
        if (api == null || !api.isLoggedIn()) return;
        String path = configPathField.getText().trim();
        String text = configArea.getText();
        Threads.daemon(() -> {
            try {
                api.putConfig(path, text);
                Core.app.post(() -> status.setText(Loc.format("scs.admin.config.saved", "[accent]Saved {0}", path)));
            } catch (Throwable t) {
                Core.app.post(() -> status.setText("[scarlet]" + t.getMessage()));
            }
        });
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }

    private static String prettyJson(String raw) {
        return raw.replace(",", ",\n").replace("{", "{\n").replace("}", "\n}");
    }

    private static String jsonField(String json, String key) {
        String search = "\"content\":\"";
        if ("content".equals(key)) {
            int i = json.indexOf(search);
            if (i < 0) return "";
            int start = i + search.length();
            StringBuilder sb = new StringBuilder();
            for (int p = start; p < json.length(); p++) {
                char c = json.charAt(p);
                if (c == '\\' && p + 1 < json.length()) {
                    char n = json.charAt(p + 1);
                    if (n == 'n') sb.append('\n');
                    else if (n == 'r') sb.append('\r');
                    else if (n == 't') sb.append('\t');
                    else if (n == '"') sb.append('"');
                    else if (n == '\\') sb.append('\\');
                    else sb.append(n);
                    p++;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return "";
    }

    private static String unescape(String s) {
        return s == null ? "" : s;
    }
}
