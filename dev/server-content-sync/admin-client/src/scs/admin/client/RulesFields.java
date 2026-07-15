package scs.admin.client;

import arc.struct.*;

/** Known Rules.java scalar fields for the admin editor. */
public final class RulesFields {
    public static final String PATH = "rules.hjson";

    public static class Field {
        public final String key;
        public final String type; // bool, float, int
        public final String group;
        public final String label;
        public final String def;

        public Field(String key, String type, String group, String label, String def) {
            this.key = key;
            this.type = type;
            this.group = group;
            this.label = label;
            this.def = def;
        }
    }

    public static final Seq<Field> ALL = Seq.with(
            // Mode
            f("allowEditRules", "bool", "Mode", "Allow edit rules", "false"),
            f("infiniteResources", "bool", "Mode", "Infinite resources", "false"),
            f("pvp", "bool", "Mode", "PvP", "false"),
            f("pvpAutoPause", "bool", "Mode", "PvP auto pause", "true"),
            f("attackMode", "bool", "Mode", "Attack mode", "false"),
            f("editor", "bool", "Mode", "Editor", "false"),
            f("waves", "bool", "Mode", "Waves enabled", "true"),
            f("waveTimer", "bool", "Mode", "Wave timer", "true"),
            f("waveSending", "bool", "Mode", "Manual wave send", "true"),
            f("waitEnemies", "bool", "Mode", "Wait enemies", "false"),
            f("airUseSpawns", "bool", "Mode", "Air use spawns", "false"),
            f("wavesSpawnAtCores", "bool", "Mode", "Waves spawn at cores", "true"),
            f("pauseDisabled", "bool", "Mode", "Pause disabled", "false"),

            // Cores
            f("canGameOver", "bool", "Cores", "Can game over", "true"),
            f("coreCapture", "bool", "Cores", "Core capture", "false"),
            f("coreDestroyClear", "bool", "Cores", "Core destroy clear", "false"),
            f("coreIncinerates", "bool", "Cores", "Core incinerates", "true"),
            f("derelictRepair", "bool", "Cores", "Derelict repair", "true"),
            f("cleanupDeadTeams", "bool", "Cores", "Cleanup dead teams", "true"),
            f("onlyDepositCore", "bool", "Cores", "Only deposit core", "false"),
            f("allowCoreUnloaders", "bool", "Cores", "Core unloaders", "true"),
            f("itemDepositCooldown", "float", "Cores", "Deposit CD", "0.5"),
            f("enemyCoreBuildRadius", "float", "Cores", "Core build radius", "400"),
            f("polygonCoreProtection", "bool", "Cores", "Polygon core protection", "false"),
            f("placeRangeCheck", "bool", "Cores", "Place range check", "false"),

            // Combat
            f("reactorExplosions", "bool", "Combat", "Reactor explosions", "true"),
            f("damageExplosions", "bool", "Combat", "Damage explosions", "true"),
            f("fire", "bool", "Combat", "Fire", "true"),
            f("possessionAllowed", "bool", "Combat", "Possession allowed", "true"),
            f("schematicsAllowed", "bool", "Combat", "Schematics allowed", "true"),
            f("ghostBlocks", "bool", "Combat", "Ghost blocks", "true"),
            f("hideSpawns", "bool", "Combat", "Hide spawns", "true"),
            f("randomWaveAI", "bool", "Combat", "Random wave AI", "false"),
            f("fog", "bool", "Combat", "Fog of war", "false"),
            f("staticFog", "bool", "Combat", "Static fog", "true"),
            f("lighting", "bool", "Combat", "Lighting", "false"),

            // Logic
            f("logicUnitControl", "bool", "Logic", "Logic unit control", "true"),
            f("logicUnitBuild", "bool", "Logic", "Logic unit build", "true"),
            f("logicUnitDeconstruct", "bool", "Logic", "Logic unit deconstruct", "false"),
            f("worldProcessorPlayerLink", "bool", "Logic", "World processor player link", "true"),
            f("allowEditWorldProcessors", "bool", "Logic", "Edit world processors", "false"),
            f("disableWorldProcessors", "bool", "Logic", "Disable world processors", "false"),
            f("allowLogicData", "bool", "Logic", "Allow logic data", "false"),

            // Units
            f("unitCapVariable", "bool", "Units", "Unit cap variable", "true"),
            f("disableUnitCap", "bool", "Units", "Disable unit cap", "false"),
            f("unitCap", "int", "Units", "Unit cap", "0"),
            f("unitBuildSpeedMultiplier", "float", "Units", "Build speed ×", "1"),
            f("unitCostMultiplier", "float", "Units", "Cost ×", "1"),
            f("unitDamageMultiplier", "float", "Units", "Damage ×", "1"),
            f("unitHealthMultiplier", "float", "Units", "Health ×", "1"),
            f("unitCrashDamageMultiplier", "float", "Units", "Crash damage ×", "1"),
            f("unitMineSpeedMultiplier", "float", "Units", "Mine speed ×", "1"),
            f("unitFactoryActivationDelay", "float", "Units", "Factory activation delay", "0"),
            f("unitPayloadUpdate", "bool", "Units", "Unit payload update", "false"),
            f("unitPayloadsExplode", "bool", "Units", "Unit payloads explode", "false"),

            // Building
            f("blockHealthMultiplier", "float", "Building", "Block health ×", "1"),
            f("blockDamageMultiplier", "float", "Building", "Block damage ×", "1"),
            f("buildCostMultiplier", "float", "Building", "Build cost ×", "1"),
            f("buildSpeedMultiplier", "float", "Building", "Build speed ×", "1"),
            f("deconstructRefundMultiplier", "float", "Building", "Deconstruct refund", "0.5"),
            f("solarMultiplier", "float", "Building", "Solar ×", "1"),
            f("instantBuild", "bool", "Building", "Instant build", "false"),
            f("allowEnvironmentDeconstruct", "bool", "Building", "Env deconstruct", "false"),
            f("hideBannedBlocks", "bool", "Building", "Hide banned blocks", "false"),
            f("blockWhitelist", "bool", "Building", "Block whitelist", "false"),
            f("unitWhitelist", "bool", "Building", "Unit whitelist", "false"),

            // Waves
            f("dropZoneRadius", "float", "Waves", "Drop zone R", "300"),
            f("waveSpacing", "float", "Waves", "Wave spacing", "7200"),
            f("initialWaveSpacing", "float", "Waves", "Initial spacing", "0"),
            f("winWave", "int", "Waves", "Win wave", "0"),
            f("objectiveTimerMultiplier", "float", "Waves", "Obj. timer ×", "1"),
            f("dragMultiplier", "float", "Waves", "Drag ×", "1"),

            // Misc
            f("showOtherTeamPings", "bool", "Misc", "Show other team pings", "false"),
            f("borderDarkness", "bool", "Misc", "Border darkness", "true"),
            f("alwaysPlayMusic", "bool", "Misc", "Always play music", "false"),
            f("disableMusic", "bool", "Misc", "Disable music", "false"),
            f("musicVolume", "float", "Misc", "Music volume", "1"),
            f("limitMapArea", "bool", "Misc", "Limit map area", "false"),
            f("disableOutsideArea", "bool", "Misc", "Disable outside area", "true")
    );

    private static Field f(String key, String type, String group, String label, String def) {
        return new Field(key, type, group, label, def);
    }

    private RulesFields() {}
}
