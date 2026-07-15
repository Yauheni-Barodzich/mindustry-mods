package scs.admin.plugin;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.CoreBlock.*;

import static mindustry.Vars.*;

/**
 * Clamps enemy spawn overlay tiles after map load:
 * at most {@link AdminConfig#maxEnemySpawns}, at least {@link AdminConfig#minEnemySpawns}.
 */
public final class SpawnClamp {
    private final AdminConfig config;

    public SpawnClamp(AdminConfig config) {
        this.config = config;
    }

    public void register() {
        Events.on(WorldLoadEvent.class, e -> Time.runTask(1f, this::clampSafe));
    }

    private void clampSafe() {
        try {
            clamp();
        } catch (Throwable t) {
            Log.err("[Admin] Spawn clamp failed", t);
        }
    }

    public void clamp() {
        if (!config.spawnClampEnabled) return;
        if (world == null || state == null || spawner == null) return;

        int max = config.maxEnemySpawns;
        int min = config.minEnemySpawns;
        Seq<Tile> current = spawner.getSpawns().copy();
        int before = current.size;

        if (max > 0 && current.size > max) {
            // keep farthest from player cores
            current.sort((a, b) -> Float.compare(scoreKeep(b), scoreKeep(a)));
            for (int i = max; i < current.size; i++) {
                Tile t = current.get(i);
                if (t != null) {
                    t.setOverlayNet(Blocks.air);
                }
            }
            Log.info("[Admin] Spawn clamp: removed @ excess spawn(s) (had @, max @)",
                    before - max, before, max);
        }

        int now = spawner.countSpawns();
        if (min > 0 && now < min) {
            if (!state.rules.waves && !state.rules.attackMode) {
                Log.info("[Admin] Spawn clamp: @ spawn(s), min=@ skipped (waves/attack off)", now, min);
                return;
            }
            int added = 0;
            ObjectSet<Tile> used = new ObjectSet<>();
            for (Tile s : spawner.getSpawns()) {
                used.add(s);
            }

            for (int need = min - now; need > 0; need--) {
                Tile t = findNewSpawnTile(used);
                if (t == null) {
                    Log.warn("[Admin] Spawn clamp: could not place enough spawns (@/@)", now + added, min);
                    break;
                }
                t.setOverlayNet(Blocks.spawn);
                used.add(t);
                added++;
            }
            if (added > 0) {
                Log.info("[Admin] Spawn clamp: added @ spawn(s) (had @, min @) → now @",
                        added, now, min, spawner.countSpawns());
            }
        } else if (before != spawner.countSpawns()) {
            Log.info("[Admin] Spawn clamp: @ → @ spawn(s) (min=@ max=@)",
                    before, spawner.countSpawns(), min, max);
        }
    }

    private static float scoreKeep(Tile t) {
        if (t == null) return -1f;
        return distToNearestPlayerCore(t.worldx(), t.worldy());
    }

    private static float distToNearestPlayerCore(float wx, float wy) {
        float best = Float.MAX_VALUE;
        boolean any = false;
        Seq<CoreBuild> cores = state.teams.playerCores();
        for (CoreBuild core : cores) {
            any = true;
            best = Math.min(best, Mathf.dst(wx, wy, core.x, core.y));
        }
        if (!any) {
            float cx = world.width() * tilesize / 2f;
            float cy = world.height() * tilesize / 2f;
            return Mathf.dst(wx, wy, cx, cy);
        }
        return best;
    }

    private Tile findNewSpawnTile(ObjectSet<Tile> used) {
        Tile best = null;
        float bestScore = -1f;
        int w = world.width();
        int h = world.height();
        int margin = 1;

        for (int x = margin; x < w - margin; x++) {
            float s1 = scoreCandidate(world.tile(x, margin), used);
            float s2 = scoreCandidate(world.tile(x, h - 1 - margin), used);
            if (s1 > bestScore) {
                bestScore = s1;
                best = world.tile(x, margin);
            }
            if (s2 > bestScore) {
                bestScore = s2;
                best = world.tile(x, h - 1 - margin);
            }
        }
        for (int y = margin; y < h - margin; y++) {
            float s1 = scoreCandidate(world.tile(margin, y), used);
            float s2 = scoreCandidate(world.tile(w - 1 - margin, y), used);
            if (s1 > bestScore) {
                bestScore = s1;
                best = world.tile(margin, y);
            }
            if (s2 > bestScore) {
                bestScore = s2;
                best = world.tile(w - 1 - margin, y);
            }
        }
        return bestScore > 0 ? best : null;
    }

    private static float scoreCandidate(Tile t, ObjectSet<Tile> used) {
        if (t == null || used.contains(t)) return -1f;
        if (t.overlay() == Blocks.spawn) return -1f;
        if (t.solid()) return -1f;
        if (t.floor().isDeep()) return -1f;
        if (!t.floor().placeableOn) return -1f;
        float d = distToNearestPlayerCore(t.worldx(), t.worldy());
        if (d < tilesize * 12f) return -1f;
        return d;
    }
}
