# Story — Turret mounts from the ship spec (retire per-ship scaling)

> Shared-core member of the [`air/`](../overview.md) category. Corrects the
> turret-placement approach from
> [`global-pixel-density-scale.md`](../complete/global-pixel-density-scale.md)
> and [`per-turret-los.md`](../complete/per-turret-los.md). **Active.**

## The bug

Shuttle turrets sit at **hand-authored generic offsets** (`ShuttleType.a2gKit`,
keyed only by hardpoint *count*) and are then multiplied by `turretSpread`
(hull-length / a reference) to "keep them on the hull." Both halves are wrong:

- The offsets were never the hull's real turret hardpoints — they're a made-up
  layout, so a Valkyrie's turrets float in generic spots, not on its painted
  wing/nose mounts.
- `turretSpread` is exactly the **per-ship scaling** that shouldn't exist. Every
  hull's geometry should come out of the *same* global pixel density
  (`AirScale.METERS_PER_PX`) the hull and engine slots already use — not a
  per-ship factor.

## The fix — real `weaponSlots`, one density

Vanilla `.ship` files carry the real turret hardpoints, in the same pixel frame
as `bounds`/`engineSlots` (confirmed: Valkyrie has 4 `BALLISTIC` wing slots + 2
`ENERGY` nose slots; Kite has 2 `MISSILE` + 1 `BALLISTIC`). Scrape them and
convert with the **same** pixel→cell transform as engine slots
([[vanilla_ship_spec_scraping]]), at the global density:

```
our.x = -vy_px * METERS_PER_PX      // port→starboard sign flip
our.y =  vx_px * METERS_PER_PX      // forward axis straight through
```

So a mount's position is `pixelSlot × METERS_PER_PX` — identical density to every
other sprite/geometry in the game. No `turretSpread`, no per-ship reference; the
relative sizes and placements fall out of vanilla's own art consistency, base
and modded, for free.

### Loadout stays; only position changes source

`a2gKit` keeps choosing **what** turrets a hull carries (by hardpoint count —
gameplay balance). The fix only changes **where** they sit: pair each kit kind
with a real weapon slot.

- `ShuttleType.kitFor` returns `TurretKind[]` (the loadout — kinds only), not
  positioned `TurretMount[]`.
- A new `TurretSlotResolver.resolve(hullId)` scrapes `weaponSlots`, filters to
  mountable weapon types (BALLISTIC / ENERGY / MISSILE / COMPOSITE / HYBRID /
  UNIVERSAL / SYNERGY — skip SYSTEM / DECORATIVE / BUILT_IN), converts at the
  global density, and caches by hull id (mirrors `EngineSlotResolver` /
  `HullFootprintResolver`).
- `BattleSetup.equipDefaultTurrets` zips `kit[i]` with `slots[i]` for the first
  `min(kit.length, hardpoints, slots.size())` slots, building a
  `TurretMount(kind, slotX, slotY)` each.

### Retire `turretSpread`

With positions absolute (already density-scaled), the position helpers lose the
per-ship factor:

- `Shuttle.turretSpread()` + `cachedTurretSpread` — **deleted**.
- `AirScale.TURRET_AUTHORING_HULL_CELLS` — **deleted**.
- `Shuttle.turretWorldX/Y(mount, cos, sin, extraScale)` keep only `extraScale`
  (render altitude zoom; sim passes 1) — they just rotate the real offset by
  facing and add `body`. Turret **size** stays fixed per kind
  (`visualCells × scaleMult`), already correct.

### Per-turret LoS is unaffected (better, even)

The per-turret LoS from [`per-turret-los.md`](../complete/per-turret-los.md) keys
on each mount's `originCellX/Y`. Real slots spread across the hull *more*
faithfully than the generic offsets did, so front-vs-rear divergence still holds
— now at the actual hardpoints.

## Implementation slices

1. **`TurretSlotResolver`** (`battle/air/engine/`) — scrape + filter + convert +
   cache `weaponSlots` → `(x, y[, angle])` in our frame at global density.
2. **`ShuttleType.kitFor` / `a2gKit`** → return `TurretKind[]`.
3. **`BattleSetup.equipDefaultTurrets`** → resolve slots, zip kinds with slot
   positions, build mounts (clamp to available slots).
4. **Delete `turretSpread`** (Shuttle) + `TURRET_AUTHORING_HULL_CELLS` (AirScale);
   simplify `turretWorldX/Y` to `extraScale` only.
5. **Verify**: turrets land on the painted hardpoints across Kite / Valkyrie /
   Buffalo; per-turret front-vs-rear LoS still differentiates.

## Out of scope

- Slot **angle/arc** honoring (rest pose, firing arcs) — our turrets free-aim;
  default facing stays nose. Note as a later nicety.
- Matching turret **kind** to slot **type/size** (mount a missile pod on a
  MISSILE slot) — loadout stays count-driven for now.
- Ground `MapTurret`s, ships' concave collision, flyby fighters.

## Done when

- Shuttle turrets render on their hull's real `weaponSlots`, sized by one global
  density with no per-ship factor anywhere.
- `turretSpread` / `TURRET_AUTHORING_HULL_CELLS` are gone.
- Per-turret LoS still differentiates front from rear.
- Story moves to `complete/`; the two superseded complete docs get a pointer.
