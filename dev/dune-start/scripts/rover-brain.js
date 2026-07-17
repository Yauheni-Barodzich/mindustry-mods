/**
 * State-machine вездехода: toOre → mine → toStore → (unload) → toOre.
 * Pathfinding через CommandAI.move; добыча — MinerAI только в радиусе.
 */

const MODE_MINE = "mine";
const MODE_TO_ORE = "toOre";
const MODE_TO_STORE = "toStore";
const MODE_MANUAL = "manual";

const states = {};

function getState(u) {
  let s = states[u.id];
  if (s == null) {
    s = { mode: MODE_MINE };
    states[u.id] = s;
  }
  return s;
}

function clearUnit(u) {
  if (u != null) delete states[u.id];
}

function setMode(s, mode) {
  s.mode = mode;
}

/** В Rhino поле command перекрывает метод — только присваивание. */
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

function clearMoveTarget(ai) {
  if (ai == null) return;
  ai.targetPos = null;
  ai.attackTarget = null;
  if (ai.commandQueue != null) ai.commandQueue.clear();
}

function setMoveTarget(ai, x, y) {
  if (ai == null) return;
  if (ai.command != UnitCommand.moveCommand) {
    setUnitCommand(ai, UnitCommand.moveCommand);
  }
  if (ai.targetPos == null || ai.targetPos.dst(x, y) > Vars.tilesize * 1.5) {
    ai.commandPosition(new Vec2(x, y));
  }
}

function enterMine(ai, m, ore) {
  clearMoveTarget(ai);
  if (ai.command != UnitCommand.mineCommand) {
    setUnitCommand(ai, UnitCommand.mineCommand);
  }
  if (m != null && ore != null) {
    m.ore = ore;
    m.mining = true;
    if (m.timer != null) {
      try {
        m.timer.reset(m.timerTarget3, 0);
      } catch (e) {}
    }
  }
}

function minerAI(ai) {
  if (ai != null && ai.commandController instanceof MinerAI) {
    return ai.commandController;
  }
  return null;
}

function isPlayerManualMove(ai, s) {
  return (
    ai.command == UnitCommand.moveCommand &&
    s.mode != MODE_TO_ORE &&
    s.mode != MODE_TO_STORE
  );
}

/**
 * api:
 *  roverCapacity(u), miningTargetItem(u, ai), isReachableFloorOre(tile, item),
 *  findNearestStorage(u, item), approachPoint(u, store),
 *  tryUnload(u, store), isMinerReturning(u, ai)
 */
function tick(u, ai, api) {
  if (u == null || ai == null || api == null) return;

  const s = getState(u);
  const cap = api.roverCapacity(u);
  const amount = u.stack != null ? u.stack.amount : 0;
  const full = amount >= cap;

  // ручной move — не трогаем, пока трюм не полный
  if (isPlayerManualMove(ai, s) && !full) {
    setMode(s, MODE_MANUAL);
    return;
  }

  const item = amount > 0 && u.stack.item != null ? u.stack.item : null;
  const store = item != null ? api.findNearestStorage(u, item) : null;

  if (store != null && amount > 0 && api.tryUnload(u, store)) {
    setMode(s, MODE_TO_ORE);
    clearMoveTarget(ai);
    setUnitCommand(ai, UnitCommand.mineCommand);
    return;
  }

  const wantStore =
    amount > 0 &&
    (full ||
      s.mode == MODE_TO_STORE ||
      api.isMinerReturning(u, ai));

  if (wantStore && store != null) {
    setMode(s, MODE_TO_STORE);
    u.mineTile = null;
    const p = api.approachPoint(u, store);
    setMoveTarget(ai, p.x, p.y);
    return;
  }

  if (wantStore && store == null) {
    setMode(s, MODE_TO_STORE);
    u.mineTile = null;
    clearMoveTarget(ai);
    return;
  }

  const m = minerAI(ai);
  const targetItem = api.miningTargetItem(u, ai);
  if (m != null) {
    m.targetItem = targetItem;
    m.mining = true;
  }

  if (u.mineTile != null && !api.isReachableFloorOre(u.mineTile, targetItem)) {
    u.mineTile = null;
  }

  let ore = m != null ? m.ore : null;
  if (!api.isReachableFloorOre(ore, targetItem)) {
    ore = Vars.indexer.findClosestOre(u.x, u.y, targetItem);
    if (!api.isReachableFloorOre(ore, targetItem)) ore = null;
    if (m != null) m.ore = ore;
  }

  if (ore == null) {
    setMode(s, MODE_MINE);
    enterMine(ai, m, null);
    return;
  }

  const mineRange = Math.max(u.type.mineRange * 0.7, Vars.tilesize * 2);
  if (u.within(ore.worldx(), ore.worldy(), mineRange)) {
    setMode(s, MODE_MINE);
    enterMine(ai, m, ore);
    return;
  }

  setMode(s, MODE_TO_ORE);
  u.mineTile = null;
  setMoveTarget(ai, ore.worldx(), ore.worldy());
  if (m != null) {
    m.ore = ore;
    if (m.timer != null) {
      try {
        m.timer.reset(m.timerTarget3, 0);
      } catch (e) {}
    }
  }
}

module.exports = {
  clearUnit: clearUnit,
  tick: tick,
};
