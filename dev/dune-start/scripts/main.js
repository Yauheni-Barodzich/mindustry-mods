/**
 * Дюна: Старт — вездеходы + дрон-камикадзе.
 * Добыча — CommandAI/MinerAI. Дроны — оборона базы (лимит 2).
 */

const MAX_ROVERS = 3;
const MAX_DRONES = 2;
const UNIT_ID = "dune-start-sand-rover";
const DRONE_ID = "dune-start-kamikaze-drone";
const FACTORY_ID = "dune-start-rover-factory";
const COST_AMOUNT = 10;
const DRONE_COST_LEAD = 15;
/** Радиус поиска врагов от своего ядра (клетки). */
const DRONE_DEFENSE_TILES = 56;
/** Дистанция подрыва. */
const DRONE_EXPLODE_DST = 26;
const DUST_INTERVAL = 5;
const SPEED_EMPTY = 1.55;
const SPEED_FULL = 0.48;

const SPEED_VAR = 0.1;
const MINE_SPEED_MIN = 0.8;
const MINE_SPEED_MAX = 2.0;
const HEALTH_MUL_MIN = 0.65;
const HEALTH_MUL_MAX = 1.2;
const WEAR_PER_FULL_UNLOAD = 0.14;

const dustTimer = {};
const roverTraits = {};
/** Предыдущий stack.amount — износ при выгрузке MinerAI. */
const prevStackAmount = {};

const PREFER_ITEMS = [Items.copper, Items.lead, Items.coal, Items.scrap];

function roverType() {
  return Vars.content.unit(UNIT_ID);
}

function droneType() {
  return Vars.content.unit(DRONE_ID);
}

function refundPlanCost(core, spawner, fallbackItem, fallbackAmount) {
  if (spawner != null && spawner.block != null && spawner.block.plans != null) {
    const idx = spawner.currentPlan;
    if (idx >= 0 && idx < spawner.block.plans.size) {
      const plan = spawner.block.plans.get(idx);
      if (plan != null && plan.requirements != null) {
        for (let i = 0; i < plan.requirements.length; i++) {
          const stack = plan.requirements[i];
          core.items.add(stack.item, stack.amount);
        }
        return;
      }
    }
  }
  if (fallbackItem != null) core.items.add(fallbackItem, fallbackAmount);
}

function refundRoverCost(core, spawner) {
  refundPlanCost(core, spawner, Items.copper, COST_AMOUNT);
}

function refundDroneCost(core, spawner) {
  refundPlanCost(core, spawner, Items.lead, DRONE_COST_LEAD);
}

function removeOverLimit(unit, spawner, max, refundFn, toastKey) {
  const type = unit.type;
  if (unit.team.data().countType(type) <= max) return false;

  const core = unit.team.core();
  if (core != null) refundFn(core, spawner);

  if (spawner != null) {
    spawner.payload = null;
    spawner.progress = 0;
  }

  unit.remove();

  if (!Vars.headless && Vars.ui != null) {
    Vars.ui.showInfoToast(Core.bundle.get(toastKey), 3);
  }
  return true;
}

function droneDefenseRange() {
  return DRONE_DEFENSE_TILES * Vars.tilesize;
}

/** Враг/здание врага в радиусе от точки (без Units.closestEnemy — в JS часто падает). */
function findThreatNear(team, x, y, range) {
  let best = null;
  let bestD2 = range * range;

  Groups.unit.each((e) => {
    if (e == null || e.team == team) return;
    if (e.dead) return;
    try {
      if (!e.type.targetable) return;
    } catch (ex) {}
    const d2 = e.dst2(x, y);
    if (d2 <= bestD2) {
      bestD2 = d2;
      best = e;
    }
  });

  Groups.build.each((b) => {
    if (b == null || b.team == team) return;
    if (!b.isValid() || b.dead) return;
    try {
      if (b.block != null && !b.block.targetable) return;
    } catch (ex) {}
    const d2 = b.dst2(x, y);
    if (d2 <= bestD2) {
      bestD2 = d2;
      best = b;
    }
  });

  return best;
}

/**
 * Оборона базы: лететь на угрозу у ядра и взорваться вплотную.
 * Движение через movePref после AI — CommandAI не мешает.
 */
function tickDroneDefense(u) {
  if (u == null || u.dead) return;

  const core = u.closestCore();
  if (core == null) return;

  const range = droneDefenseRange();
  const enemy = findThreatNear(u.team, core.x, core.y, range);

  // погасить добычу/цели CommandAI
  u.mineTile = null;
  const ai = u.isCommandable() ? u.command() : null;
  if (ai != null) {
    ai.targetPos = null;
    ai.attackTarget = null;
    if (ai.commandQueue != null) ai.commandQueue.clear();
  }

  if (enemy != null) {
    const ex = enemy.x;
    const ey = enemy.y;
    Tmp.v1.set(ex - u.x, ey - u.y);
    const len = Tmp.v1.len();
    if (len > 0.001) {
      Tmp.v1.scl(u.speed() / len);
      u.movePref(Tmp.v1);
    }
    try {
      u.lookAt(ex, ey);
    } catch (e) {}

    if (u.within(ex, ey, DRONE_EXPLODE_DST)) {
      u.kill();
    }
    return;
  }

  // нет угрозы — барражировать у ядра
  if (!u.within(core.x, core.y, Vars.tilesize * 10)) {
    Tmp.v1.set(core.x - u.x, core.y - u.y);
    const len = Tmp.v1.len();
    if (len > 0.001) {
      Tmp.v1.scl(u.speed() / len);
      u.movePref(Tmp.v1);
    }
  }
}

function colorFromRand(rand) {
  const c = new Color();
  c.fromHsv(
    rand.random(360),
    0.42 + rand.random(0.48),
    0.5 + rand.random(0.4)
  );
  return c;
}

function ensureTraits(u) {
  if (u == null) return null;
  if (roverTraits[u.id] != null) return roverTraits[u.id];

  let seed = u.flag;
  if (seed == 0) {
    seed = 1 + Math.floor(Mathf.random(1, 2000000000));
    u.flag = seed;
  }

  const rand = new Rand(seed);
  // порядок Rand: цвета → скорость → (бывший capacity, discard) → prefer → mine → health
  const cabin = colorFromRand(rand);
  const bed = colorFromRand(rand);
  const roof = colorFromRand(rand);
  const speedMul = 1 - SPEED_VAR + rand.random(SPEED_VAR * 2);
  // раньше capacity 100..500 — тот же вызов, чтобы не сдвинуть сид
  Math.floor(rand.random(401));
  const preferSlot = Math.floor(rand.random(1024));
  const mineSpeedMul =
    MINE_SPEED_MIN + rand.random(MINE_SPEED_MAX - MINE_SPEED_MIN);
  const healthMul =
    HEALTH_MUL_MIN + rand.random(HEALTH_MUL_MAX - HEALTH_MUL_MIN);

  const traits = {
    cabin: cabin,
    bed: bed,
    roof: roof,
    speedMul: speedMul,
    preferSlot: preferSlot,
    mineSpeedMul: mineSpeedMul,
    healthMul: healthMul,
  };
  roverTraits[u.id] = traits;
  return traits;
}

function roverCapacity(u) {
  if (u != null && u.type != null) return u.type.itemCapacity;
  const type = roverType();
  return type != null ? type.itemCapacity : 200;
}

function roverSpeedMul(u) {
  const t = ensureTraits(u);
  return t != null ? t.speedMul : 1;
}

function itemHasOre(item) {
  return item != null && Vars.indexer != null && Vars.indexer.hasOre(item);
}

function mapMineableItems(type) {
  const out = [];
  if (type == null) return out;
  const seen = {};

  const add = (item) => {
    if (item == null || seen[item.id]) return;
    if (item.hardness < 1 || item.hardness > type.mineTier) return;
    if (!itemHasOre(item)) return;
    out.push(item);
    seen[item.id] = true;
  };

  for (let i = 0; i < PREFER_ITEMS.length; i++) add(PREFER_ITEMS[i]);
  Vars.content.items().each(add);
  return out;
}

function roverPreferItem(u) {
  const t = ensureTraits(u);
  const type = u != null ? u.type : roverType();
  const available = mapMineableItems(type);
  if (available.length == 0) {
    for (let i = 0; i < PREFER_ITEMS.length; i++) {
      const item = PREFER_ITEMS[i];
      if (item != null && type != null && item.hardness <= type.mineTier) {
        return item;
      }
    }
    return Items.copper;
  }
  let slot = 0;
  if (t != null) {
    if (t.preferSlot != null) slot = t.preferSlot;
    else if (t.preferItem != null) slot = t.preferItem.id;
  }
  return available[Math.abs(slot) % available.length];
}

function roverMineSpeedMul(u) {
  const t = ensureTraits(u);
  return t != null && t.mineSpeedMul != null ? t.mineSpeedMul : 1;
}

function roverHealthMul(u) {
  const t = ensureTraits(u);
  return t != null && t.healthMul != null ? t.healthMul : 1;
}

function syncRoverHealth(u) {
  if (u == null) return;
  const mul = roverHealthMul(u);
  const max = Math.max(1, Math.round(u.type.health * mul));
  if (Math.abs(u.maxHealth - max) < 0.5) return;
  const ratio = u.maxHealth > 0 ? u.health / u.maxHealth : 1;
  u.maxHealth = max;
  u.health = Mathf.clamp(ratio * max, 0, max);
}

function applyUnloadWear(u, unloaded) {
  if (u == null || unloaded <= 0) return;
  const cap = Math.max(roverCapacity(u), 1);
  const frac = Mathf.clamp(unloaded / cap);
  const dmg = u.maxHealth * WEAR_PER_FULL_UNLOAD * frac;
  if (dmg <= 0) return;
  u.damagePierce(dmg, false);
}

/** Стартовый prefer-stance один раз; дальше игрок/MinerAI сами. */
function applyPreferMine(u) {
  const item = roverPreferItem(u);
  if (item == null || !u.canMine(item)) return;

  const ai = u.isCommandable() ? u.command() : null;
  if (ai == null) return;

  const stance = ItemUnitStance.getByItem(item);
  if (stance != null) {
    if (ai.hasStance(UnitStance.mineAuto)) {
      ai.disableStance(UnitStance.mineAuto);
    }
    ai.setStance(stance);
  }
}

function drawRoverPaint(unit) {
  const p = ensureTraits(unit);
  if (p == null) return;

  const white = Core.atlas.find("white");
  if (white == null || !white.found()) return;

  const z = Draw.z();
  Draw.z(Layer.groundUnit + 0.08);
  const rot = unit.rotation - 90;
  const hs = unit.hitSize;

  const blot = (color, forward, side, w, h, alpha) => {
    Tmp.v1.trns(unit.rotation, forward);
    Tmp.v2.trns(unit.rotation + 90, side);
    Draw.color(color);
    Draw.alpha(alpha);
    Draw.rect(
      white,
      unit.x + Tmp.v1.x + Tmp.v2.x,
      unit.y + Tmp.v1.y + Tmp.v2.y,
      w,
      h,
      rot
    );
  };

  blot(p.cabin, hs * 0.22, 0, hs * 0.5, hs * 0.36, 0.65);
  blot(p.bed, -hs * 0.14, 0, hs * 0.58, hs * 0.5, 0.55);
  blot(p.roof, hs * 0.02, 0, hs * 0.4, hs * 0.2, 0.5);

  Draw.color();
  Draw.z(z);
}

function makeCargoAbility() {
  const ability = extend(Ability, {
    getBundle() {
      return "dune-start.cargo-ability";
    },
    localized() {
      return Core.bundle.get("dune-start.cargo-ability");
    },
    created(unit) {
      ensureTraits(unit);
      applyPreferMine(unit);
    },
    draw(unit) {
      drawRoverPaint(unit);
    },
    displayBars(unit, bars) {
      bars
        .add(
          new Bar(
            () => {
              const t = ensureTraits(unit);
              const spd = t != null ? Math.round(t.speedMul * 100) : 100;
              const mine = t != null ? Math.round(t.mineSpeedMul * 100) : 100;
              return Core.bundle.format("dune-start.rover-speed", spd, mine);
            },
            () => Pal.accent,
            () => {
              const t = ensureTraits(unit);
              const mine =
                t != null && t.mineSpeedMul != null ? t.mineSpeedMul : 1;
              return Mathf.clamp(
                (mine - MINE_SPEED_MIN) / (MINE_SPEED_MAX - MINE_SPEED_MIN)
              );
            }
          )
        )
        .row();
      bars
        .add(
          new Bar(
            () => {
              const prefer = roverPreferItem(unit);
              return Core.bundle.format(
                "dune-start.rover-ore",
                prefer != null ? prefer.localizedName : "?"
              );
            },
            () => {
              const prefer = roverPreferItem(unit);
              return prefer != null ? prefer.color : Pal.items;
            },
            () => 1
          )
        )
        .row();
      bars
        .add(
          new Bar(
            () => {
              const it = unit.stack.item;
              const label =
                it != null
                  ? it.localizedName
                  : Core.bundle.get("dune-start.cargo-empty");
              const cap = roverCapacity(unit);
              return Core.bundle.format(
                "dune-start.cargo-bar",
                label,
                unit.stack.amount,
                cap
              );
            },
            () =>
              unit.stack.item != null ? unit.stack.item.color : Pal.items,
            () => unit.stack.amount / Math.max(roverCapacity(unit), 1)
          )
        )
        .row();
    },
  });
  ability.display = false;
  return ability;
}

function isAbilityArray(abilities) {
  return (
    abilities != null &&
    abilities.getClass != null &&
    abilities.getClass().isArray()
  );
}

function abilityCount(abilities) {
  if (abilities == null) return 0;
  return isAbilityArray(abilities) ? abilities.length : abilities.size;
}

function abilityAt(abilities, i) {
  return isAbilityArray(abilities) ? abilities[i] : abilities.get(i);
}

function isCargoAbility(a) {
  if (a == null) return false;
  try {
    return a.getBundle() == "dune-start.cargo-ability";
  } catch (e) {
    try {
      return a.localized() == Core.bundle.get("dune-start.cargo-ability");
    } catch (e2) {
      return false;
    }
  }
}

function countCargoAbilities(abilities) {
  let n = 0;
  for (let i = 0; i < abilityCount(abilities); i++) {
    if (isCargoAbility(abilityAt(abilities, i))) n++;
  }
  return n;
}

function dedupeAbilities(abilities) {
  const kept = [];
  let hasCargo = false;
  for (let i = 0; i < abilityCount(abilities); i++) {
    const a = abilityAt(abilities, i);
    if (isCargoAbility(a)) {
      if (hasCargo) continue;
      hasCargo = true;
    }
    if (a != null) kept.push(a);
  }
  return { kept: kept, hasCargo: hasCargo };
}

function setUnitAbilities(u, list) {
  const neu = java.lang.reflect.Array.newInstance(Ability, list.length);
  for (let i = 0; i < list.length; i++) {
    neu[i] = list[i];
  }
  u.abilities = neu;
}

function syncUnitCargo(u) {
  const result = dedupeAbilities(u.abilities);
  if (!result.hasCargo) {
    result.kept.push(makeCargoAbility());
  }
  const before = abilityCount(u.abilities);
  const cargoBefore = countCargoAbilities(u.abilities);
  if (cargoBefore != 1 || before != result.kept.length) {
    setUnitAbilities(u, result.kept);
  }
}

function fillMineItems(type) {
  if (type == null) return;
  type.mineItems.clear();

  const onMap = mapMineableItems(type);
  if (onMap.length > 0) {
    for (let i = 0; i < onMap.length; i++) {
      type.mineItems.add(onMap[i]);
    }
    return;
  }

  const seen = {};
  const add = (item) => {
    if (item == null || seen[item.id]) return;
    if (item.hardness < 1 || item.hardness > type.mineTier) return;
    type.mineItems.add(item);
    seen[item.id] = true;
  };
  for (let i = 0; i < PREFER_ITEMS.length; i++) add(PREFER_ITEMS[i]);
  Vars.content.items().each(add);
}

function installRoverCommands(type) {
  if (type == null) return;
  if (!type.commands.contains(UnitCommand.moveCommand)) {
    type.commands.insert(0, UnitCommand.moveCommand);
  }
  if (!type.commands.contains(UnitCommand.mineCommand)) {
    type.commands.add(UnitCommand.mineCommand);
  }
  for (let i = type.commands.size - 1; i >= 0; i--) {
    const c = type.commands.get(i);
    if (c != null && c.name != null && c.name.indexOf("rover-deposit") >= 0) {
      type.commands.remove(i);
    }
  }
  type.defaultCommand = UnitCommand.mineCommand;
}

function installCargoAbility() {
  const type = roverType();
  if (type == null) return;

  // itemCapacity из hjson — общий для всех; range нужен MinerAI для сдачи в ядро
  if (type.range < 40) type.range = 50;
  fillMineItems(type);
  installRoverCommands(type);

  const typeResult = dedupeAbilities(type.abilities);
  type.abilities.clear();
  for (let i = 0; i < typeResult.kept.length; i++) {
    type.abilities.add(typeResult.kept[i]);
  }
  if (!typeResult.hasCargo) {
    type.abilities.add(makeCargoAbility());
  }

  const drone = droneType();
  if (drone != null) {
    try {
      drone.commands.clear();
    } catch (e) {}
    drone.range = Math.max(drone.range, 320);
  }

  Groups.unit.each((u) => {
    if (u.type != type) return;
    syncUnitCargo(u);
    ensureTraits(u);
    syncRoverHealth(u);
  });
}

function emitMoveDust(u) {
  if (Vars.headless) return;
  if (u.deltaLen() < 0.04) {
    dustTimer[u.id] = 0;
    return;
  }

  let t = dustTimer[u.id] || 0;
  t += Time.delta;
  if (t < DUST_INTERVAL) {
    dustTimer[u.id] = t;
    return;
  }
  dustTimer[u.id] = 0;

  const back = Tmp.v1.trns(u.rotation + 180, u.hitSize * 0.42);
  const floor = u.floorOn();
  const color = floor != null ? floor.mapColor : Color.valueOf("c2a060");

  for (let side = -1; side <= 1; side += 2) {
    const sideOff = Tmp.v2.trns(u.rotation + 90, side * u.hitSize * 0.22);
    Fx.unitDust.at(
      u.x + back.x + sideOff.x,
      u.y + back.y + sideOff.y,
      u.rotation + 180,
      color
    );
  }
}

function applyCargoSpeed(u) {
  const cap = Math.max(roverCapacity(u), 1);
  const fill = Mathf.clamp(u.stack.amount / cap);
  const t = fill * fill;
  const cargoMul = Mathf.lerp(SPEED_EMPTY, SPEED_FULL, t);
  u.applyDynamicStatus().speedMultiplier = cargoMul * roverSpeedMul(u);
}

function applyMineSpeed(u) {
  if (u.mineTile == null) return;
  const mul = roverMineSpeedMul(u);
  if (Math.abs(mul - 1) < 0.001) return;
  const rules = Vars.state.rules.unitMineSpeed(u.team);
  u.mineTimer += Time.delta * u.type.mineSpeed * rules * (mul - 1);
  if (u.mineTimer < 0) u.mineTimer = 0;
}

function trackUnloadWear(u) {
  const amount = u.stack != null ? Number(u.stack.amount) : 0;
  const prev = prevStackAmount[u.id];
  if (prev != null && prev > 0 && amount < prev) {
    applyUnloadWear(u, prev - amount);
  }
  prevStackAmount[u.id] = amount;
}

Events.on(ClientLoadEvent, (e) => {
  installCargoAbility();
});

Events.on(ServerLoadEvent, (e) => {
  installCargoAbility();
});

Events.on(WorldLoadEvent, (e) => {
  installCargoAbility();
});

Events.on(UnitCreateEvent, (e) => {
  if (e.unit == null) return;

  const rover = roverType();
  if (rover != null && e.unit.type == rover) {
    syncUnitCargo(e.unit);
    ensureTraits(e.unit);
    syncRoverHealth(e.unit);
    applyPreferMine(e.unit);
    removeOverLimit(
      e.unit,
      e.spawner,
      MAX_ROVERS,
      refundRoverCost,
      "dune-start.rover-limit"
    );
    return;
  }

  const drone = droneType();
  if (drone != null && e.unit.type == drone) {
    removeOverLimit(
      e.unit,
      e.spawner,
      MAX_DRONES,
      refundDroneCost,
      "dune-start.drone-limit"
    );
  }
});

Events.on(UnitDestroyEvent, (e) => {
  if (e.unit != null) {
    delete dustTimer[e.unit.id];
    delete roverTraits[e.unit.id];
    delete prevStackAmount[e.unit.id];
  }
});

Events.run(Trigger.update, () => {
  const rover = roverType();
  const drone = droneType();

  Groups.build.each((b) => {
    if (b.block == null || b.block.name != FACTORY_ID) return;
    if (b.payload != null) return;
    if (b.block.plans == null || b.currentPlan < 0) return;
    if (b.currentPlan >= b.block.plans.size) return;
    const plan = b.block.plans.get(b.currentPlan);
    if (plan == null || plan.unit == null) return;

    if (rover != null && plan.unit == rover) {
      if (b.team.data().countType(rover) >= MAX_ROVERS) b.progress = 0;
    } else if (drone != null && plan.unit == drone) {
      if (b.team.data().countType(drone) >= MAX_DRONES) b.progress = 0;
    }
  });

  if (rover != null) {
    Groups.unit.each((u) => {
      if (u.type != rover) return;
      ensureTraits(u);
      syncRoverHealth(u);
      trackUnloadWear(u);
      emitMoveDust(u);
      applyCargoSpeed(u);
      applyMineSpeed(u);
    });
  }
});

/** После AI: дроны летят на угрозу у базы / взрываются. */
Events.run(Trigger.afterGameUpdate, () => {
  if (!Vars.state.isPlaying()) return;
  const drone = droneType();
  if (drone == null) return;
  Groups.unit.each((u) => {
    if (u.type != drone) return;
    tickDroneDefense(u);
  });
});
