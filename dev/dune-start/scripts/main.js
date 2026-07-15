const MAX_ROVERS = 3;
const UNIT_ID = "dune-start-sand-rover";
const FACTORY_ID = "dune-start-rover-factory";
const COST_AMOUNT = 5;
/** Разгрузка только вплотную (зазор краёв). */
const UNLOAD_EDGE_GAP = 2;
/** Как часто сыпать пыль при движении (тики). */
const DUST_INTERVAL = 5;
/** Множитель скорости: пустой / полный трюм. */
const SPEED_EMPTY = 1.55;
const SPEED_FULL = 0.48;

const unloading = {};
const dustTimer = {};
/** Раскраска вездеходов: id -> { cabin, bed, roof } */
const roverPaints = {};

function roverType() {
  return Vars.content.unit(UNIT_ID);
}

/** Возврат стоимости при лимите: по текущему плану завода или медь по умолчанию. */
function refundRoverCost(core, spawner) {
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
  core.items.add(Items.copper, COST_AMOUNT);
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

/** Случайная окраска; seed в unit.flag — сохранится в сейве. */
function ensurePaint(u) {
  if (u == null) return null;
  if (roverPaints[u.id] != null) return roverPaints[u.id];

  let seed = u.flag;
  if (seed == 0) {
    seed = 1 + Math.floor(Mathf.random(1, 2000000000));
    u.flag = seed;
  }

  const rand = new Rand(seed);
  const paint = {
    cabin: colorFromRand(rand),
    bed: colorFromRand(rand),
    roof: colorFromRand(rand),
  };
  roverPaints[u.id] = paint;
  return paint;
}

function drawRoverPaint(unit) {
  const p = ensurePaint(unit);
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
      ensurePaint(unit);
    },
    draw(unit) {
      drawRoverPaint(unit);
    },
    displayBars(unit, bars) {
      bars
        .add(
          new Bar(
            () => {
              const it = unit.stack.item;
              const label =
                it != null
                  ? it.localizedName
                  : Core.bundle.get("dune-start.cargo-empty");
              return Core.bundle.format(
                "dune-start.cargo-bar",
                label,
                unit.stack.amount,
                unit.itemCapacity()
              );
            },
            () =>
              unit.stack.item != null ? unit.stack.item.color : Pal.items,
            () => unit.stack.amount / Math.max(unit.itemCapacity(), 1)
          )
        )
        .row();
    },
  });
  ability.display = false;
  return ability;
}

function isAbilityArray(abilities) {
  return abilities != null && abilities.getClass != null && abilities.getClass().isArray();
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

/** Все руды по hardness до mineTier; медь/свинец первыми для ранней игры. */
function fillMineItems(type) {
  if (type == null) return;
  type.mineItems.clear();

  const prefer = [
    Items.copper,
    Items.lead,
    Items.coal,
    Items.titanium,
    Items.thorium,
    Items.scrap,
    Items.beryllium,
    Items.tungsten,
  ];
  const seen = {};

  const add = (item) => {
    if (item == null || seen[item.id]) return;
    if (item.hardness < 1 || item.hardness > type.mineTier) return;
    type.mineItems.add(item);
    seen[item.id] = true;
  };

  for (let i = 0; i < prefer.length; i++) add(prefer[i]);
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

  Groups.unit.each((u) => {
    if (u.type != type) return;
    syncUnitCargo(u);
    ensurePaint(u);
  });
}

function isStorage(b) {
  if (b == null || b.block == null) return false;
  if (b.block instanceof CoreBlock) return true;
  const n = b.block.name;
  return (
    n == "container" ||
    n == "vault" ||
    n == "reinforced-container" ||
    n == "reinforced-vault"
  );
}

function findNearestStorage(u, item) {
  let best = null;
  let bestDst = Infinity;
  Groups.build.each((b) => {
    if (b.team != u.team) return;
    if (!isStorage(b)) return;
    if (item != null && b.acceptStack(item, 1, u) <= 0) return;
    const d = u.dst2(b);
    if (d < bestDst) {
      bestDst = d;
      best = b;
    }
  });
  return best;
}

function edgeGap(u, store) {
  const buildingR = store.block.size * Vars.tilesize * 0.5;
  const unitR = u.hitSize * 0.5;
  return u.dst(store) - buildingR - unitR;
}

function canUnloadAt(u, store) {
  return store != null && edgeGap(u, store) <= UNLOAD_EDGE_GAP;
}

function tryUnload(u, store) {
  if (store == null || u.stack == null || u.stack.amount <= 0) return false;
  if (!canUnloadAt(u, store)) return false;
  const item = u.stack.item;
  const amount = u.stack.amount;
  const accepted = store.acceptStack(item, amount, u);
  if (accepted <= 0) return false;
  Call.transferItemTo(u, item, accepted, u.x, u.y, store);
  return true;
}

function approachPoint(u, store) {
  const slot = Math.abs(u.id) % 3;
  const angle = slot * 120 + (Math.abs(u.id) % 40);
  const rad = store.block.size * Vars.tilesize * 0.5 + u.hitSize * 0.5 + 1.25;
  return new Vec2(
    store.x + Angles.trnsx(angle, rad),
    store.y + Angles.trnsy(angle, rad)
  );
}

function isMinerReturning(u, ai) {
  if (ai == null || ai.command != UnitCommand.mineCommand) return false;
  if (u.stack == null || u.stack.amount <= 0) return false;
  const ctrl = ai.commandController;
  return ctrl != null && ctrl instanceof MinerAI && !ctrl.mining;
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
  const cap = Math.max(u.itemCapacity(), 1);
  const fill = Mathf.clamp(u.stack.amount / cap);
  const t = fill * fill;
  const mul = Mathf.lerp(SPEED_EMPTY, SPEED_FULL, t);
  u.applyDynamicStatus().speedMultiplier = mul;
}

/** В Rhino поле command перекрывает метод command(UnitCommand) — только присваивание. */
function setUnitCommand(ai, cmd) {
  if (ai == null || cmd == null) return;
  if (ai.unit != null) {
    ai.unit.mineTile = null;
    try {
      ai.unit.clearBuilding();
    } catch (e) {}
  }
  ai.command = cmd;
}

function resumeMining(ai) {
  if (ai == null) return;
  ai.targetPos = null;
  ai.attackTarget = null;
  if (ai.commandQueue != null) ai.commandQueue.clear();
  setUnitCommand(ai, UnitCommand.mineCommand);
}

function itemHasOre(item) {
  return item != null && Vars.indexer.hasOre(item);
}

function miningTargetItem(u, ai) {
  if (u.stack != null && u.stack.amount > 0 && u.stack.item != null) {
    return u.stack.item;
  }

  if (ai != null) {
    const items = Vars.content.items();
    for (let i = 0; i < items.size; i++) {
      const item = items.get(i);
      if (!u.canMine(item) || !itemHasOre(item)) continue;
      const stance = ItemUnitStance.getByItem(item);
      if (stance != null && ai.hasStance(stance)) return item;
    }
  }

  if (ai != null && ai.commandController instanceof MinerAI) {
    const ti = ai.commandController.targetItem;
    if (ti != null && u.canMine(ti) && itemHasOre(ti)) return ti;
  }

  const core = u.closestCore();
  let best = null;
  let bestScore = Infinity;
  const list = u.type.mineItems;
  if (list != null) {
    for (let i = 0; i < list.size; i++) {
      const item = list.get(i);
      if (!u.canMine(item) || !itemHasOre(item)) continue;
      const score = core != null ? core.items.get(item) : 0;
      if (score < bestScore) {
        bestScore = score;
        best = item;
      }
    }
  }
  if (best != null) return best;
  return Items.copper;
}

function isReachableFloorOre(tile, item) {
  if (tile == null || item == null) return false;
  if (tile.drop() != item) return false;
  if (tile.solid()) return false;
  if (tile.x <= 1 || tile.y <= 1) return false;
  if (tile.x >= Vars.world.width() - 2 || tile.y >= Vars.world.height() - 2) {
    return false;
  }
  return true;
}

/**
 * Правим только MinerAI.ore (куда ехать).
 * Не ставим mineTile издалека — иначе луч смотрит на одно, а едут на другое
 * (MinerAI по умолчанию ищет руду от ядра).
 */
function syncMinerTarget(u, ai) {
  if (ai == null || ai.command != UnitCommand.mineCommand) return;
  if (!(ai.commandController instanceof MinerAI)) return;
  if (unloading[u.id]) return;
  if (u.stack != null && u.stack.amount >= u.type.itemCapacity) return;

  ai.targetPos = null;
  ai.attackTarget = null;

  const m = ai.commandController;
  const item = miningTargetItem(u, ai);
  m.targetItem = item;
  m.mining = true;

  // сбросить «луч в никуда» от старого mineTile
  if (u.mineTile != null && !isReachableFloorOre(u.mineTile, item)) {
    u.mineTile = null;
  }

  if (isReachableFloorOre(m.ore, item)) {
    // не даём MinerAI раз в 60 тиков перебить цель на руду у ядра
    if (m.timer != null) {
      try {
        m.timer.reset(m.timerTarget3, 0);
      } catch (e) {}
    }
    return;
  }

  const ore = Vars.indexer.findClosestOre(u.x, u.y, item);
  if (isReachableFloorOre(ore, item)) {
    m.ore = ore;
    if (m.timer != null) {
      try {
        m.timer.reset(m.timerTarget3, 0);
      } catch (e) {}
    }
  } else {
    m.ore = null;
  }
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
  const type = roverType();
  if (type == null || e.unit == null || e.unit.type != type) return;

  syncUnitCargo(e.unit);
  ensurePaint(e.unit);

  if (e.unit.team.data().countType(type) <= MAX_ROVERS) return;

  const core = e.unit.team.core();
  if (core != null) {
    refundRoverCost(core, e.spawner);
  }

  if (e.spawner != null) {
    e.spawner.payload = null;
    e.spawner.progress = 0;
  }

  e.unit.remove();

  if (!Vars.headless && Vars.ui != null) {
    Vars.ui.showInfoToast(Core.bundle.get("dune-start.rover-limit"), 3);
  }
});

Events.on(UnitDestroyEvent, (e) => {
  if (e.unit != null) {
    delete unloading[e.unit.id];
    delete dustTimer[e.unit.id];
    delete roverPaints[e.unit.id];
  }
});

Events.run(Trigger.update, () => {
  const type = roverType();
  if (type == null) return;

  Groups.build.each((b) => {
    if (b.block == null || b.block.name != FACTORY_ID) return;
    if (b.payload != null) return;
    if (b.team.data().countType(type) < MAX_ROVERS) return;
    b.progress = 0;
  });

  Groups.unit.each((u) => {
    if (u.type != type) return;

    emitMoveDust(u);
    applyCargoSpeed(u);

    if (u.stack != null && u.stack.item == Items.sand) {
      u.clearItem();
    }

    const ai = u.isCommandable() ? u.command() : null;
    if (ai == null) return;

    const sandStance = ItemUnitStance.getByItem(Items.sand);
    if (sandStance != null && ai.hasStance(sandStance)) {
      ai.disableStance(sandStance);
      ai.setStance(UnitStance.mineAuto);
    }

    const item = u.stack != null && u.stack.amount > 0 ? u.stack.item : null;
    const store = item != null ? findNearestStorage(u, item) : null;

    if (store != null && tryUnload(u, store)) {
      delete unloading[u.id];
      if (ai.command == UnitCommand.moveCommand) {
        resumeMining(ai);
      }
      return;
    }

    const full = u.stack != null && u.stack.amount >= u.type.itemCapacity;
    const needDeposit = full || isMinerReturning(u, ai) || unloading[u.id];
    if (needDeposit && store != null) {
      unloading[u.id] = true;
      u.mineTile = null;
      if (ai.command != UnitCommand.moveCommand) {
        setUnitCommand(ai, UnitCommand.moveCommand);
      }
      ai.commandPosition(approachPoint(u, store));
      return;
    }

    if (unloading[u.id]) {
      if (u.stack == null || u.stack.amount <= 0) {
        delete unloading[u.id];
        resumeMining(ai);
      }
    }
  });
});

/** После MinerAI: иначе он снова целится в руду у ядра. */
Events.run(Trigger.afterGameUpdate, () => {
  if (!Vars.state.isPlaying()) return;
  const type = roverType();
  if (type == null) return;

  Groups.unit.each((u) => {
    if (u.type != type) return;
    const ai = u.isCommandable() ? u.command() : null;
    if (ai == null) return;
    syncMinerTarget(u, ai);
  });
});
