# Story: collapse the Unit / local* duality (Unit → id handle)

Phase A of the [component model](../component-model.md). Read that for the
why; this is the how.

## Context

`Unit` is half-hollowed. Promoted primitives (hp, cellX/cellY,
cooldownTimer, targetId, the AI timers, …) are canonically stored as SoA
columns on `UnitRegistry`, and `Unit`'s accessors delegate by dense index:

```java
public float getHp()            { return registry != null ? registry.getHp(denseIdx)      : localHp; }
public long  getTargetId()      { return registry != null ? registry.getTargetId(denseIdx): localTargetId; }
```

But every promoted field still has a `local*` twin on `Unit`, live as a
fallback whenever `registry == null` (pre-allocation seed, post-release
corpse snapshot). So we maintain **two stores** bridged by accessors, with
a "don't read `local*` directly" hazard documented at every field site.
That duality is the standing friction every reader hits — and it's about
to get worse as vehicle HP + optional bodies pile on more promoted state.

## Goal

Make the registry the **sole source of truth** for promoted state and
shrink `Unit` toward an id + `denseIdx` + thin accessor shim. Reduce
`local*` to the **minimum the lifecycle genuinely requires**, ideally zero.

## The real question: what does `local*` actually buy?

`local*` exists for the window where a `Unit` is *not* registered:

1. **Pre-allocation seed** — a `Unit` is constructed with stats before
   `UnitRegistry.allocate` copies them into the columns. Could be replaced
   by: pass the seed values *into* `allocate(...)` (a spawn-spec / builder)
   so the unit is never observable in an unregistered-but-live state.
2. **Post-release corpse snapshot** — after `release` tail-swaps the unit
   out, legacy post-death systems (drone-crash sprite, un-migrated
   iteration) still read its last values. Could be replaced by: a small
   explicit `Corpse`/death-snapshot record captured at release, instead of
   every `Unit` permanently carrying a shadow copy of every column.

Pin which of these survive and which dissolve **before** writing code — the
answer decides whether `Unit` keeps a handful of `local*` or none.

## Scope

- The `local*` fields for every promoted primitive + their accessor
  fallback branches.
- The `allocate` / `release` seam in `UnitRegistry` (where `local*` is
  read/written) and the spawn path that constructs units.
- The corpse/post-death read paths (design rule 3 in
  [`overview.md`](../overview.md)) — find every consumer that reads a
  released unit and decide its replacement.
- **Out of scope:** the non-promoted object-side capability fields
  (`primaryWeapon`, `mech`, …) — those are Phase B (component model). This
  story is only about the SoA-promoted-primitive duality.

## Approach

Incremental, registry-truth-first. Likely shape:
1. Route the spawn path through a seed-spec into `allocate` so a live unit
   is always registered — removes the pre-allocation `local*` read.
2. Replace the corpse-snapshot reads with an explicit death record (or
   confirm the consumers can read the column before tail-swap).
3. Delete `local*` + the fallback branch per field once both readers are
   gone; accessors become unconditional `registry.getX(denseIdx)`.

Watch the **xstream/Serializable caveat** (overview.md design rule 2) and
keep the tail-swap denseIdx test green at every step.

## Acceptance

- `Unit` no longer holds authoritative promoted state; accessors read the
  registry unconditionally (or `local*` is reduced to a pinned, documented
  minimum with a clear lifecycle reason).
- The "don't read `local*` directly" hazard is gone or contained to the
  registry's own allocate/release.
- Full suite green; `UnitRegistry` lifecycle tests still cover
  allocate-seed / release-snapshot / tail-swap.
- **Unblocks** the `battle.unit.Unit` → `battle.entity.Entity` rename
  (overview.md naming north star) — though the rename itself stays a
  separate, last step.

## Priority

**High / next.** The user called collapsing this "amazing," and the vehicle
optional-component work is the deadline: do it before more promoted state
ossifies the duality. Pairs naturally with — and slightly precedes —
[`component-grouping`](component-grouping.md).

## Progress

Spawn + corpse seams were mapped before writing code (the story's "pin which
`local*` survives" step). Verdict: the 22 promoted columns partition by
lifecycle into three groups, which became the slice plan **N → S → C**:

| Group | Columns | Pre-allocate seed? | Post-release reader? |
|---|---|---|---|
| **N — mid-combat** (14) | cooldownTimer, moveProgress, secondary{Cooldown,Action}Timer, secondaryAimTargetId, burst{Remaining,Timer,TargetId}, targetId, repositionCooldown, fallback{Timer,CellX,CellY}, wanderDwellTimer | no (default 0/-1) | **no** |
| **S — seed-only** (4) | maxHp, attackDamage, attackRange, accuracy | yes (ctor + deboard loadout) | no |
| **C — corpse** (5) | hp, cellX, cellY, renderX, renderY | yes (spawn pos / hp) | **yes** |

Corpse readers (Group C, the only post-release reads): `isAlive()` via
`getHp()` (turret/hub demolition, drone-crash gate, dead-sprite + drone HP
bar), demolition cell (`getCellX/Y` in TurretDemolition + HubDemolition),
dead-sprite position + death-voice audio (`getRenderX/Y`). Drones draw from
`body.x/y`, *not* renderX/Y. `deathPoseIdx` already lives on `Unit` (no SoA
column, set pre-release) and needs no snapshot.

Spawn seam: one `allocate` site (`UnitRosterService.addUnit`). Setup /
deboard / reinforcement allocate synchronously; only `DroneSpawner` defers
via `pendingSpawns` (drone not observable in that window). No code reads a
unit's promoted state between construction and `allocate` except the seeding
writes themselves.

### Slice 1 — Group N collapse — SHIPPED (`c50e50d`, 2026-06-01)

Deleted the 14 Group-N `local*` fields from `Unit`; their accessors now read
the registry unconditionally (fail-loud NPE on pre-allocate/post-release
misuse instead of a stale snapshot — verified no such caller exists).
`allocate` resets these columns to defaults (0/0L, -1 for fallbackCell) so a
dense slot reused after swap-and-pop release is clean; `release` snapshots
only the seed/corpse subset. `UnitRegistryTest` rewritten: dropped the 13
release-snapshot tests, converted allocate-seed → allocate-defaults, added a
slot-reuse reset test. ~250 net lines gone. Suite: `UnitRegistryTest` green;
full `:test` blocked by an unrelated sibling compile break (ShotRenderService).

### Slice 2 — Group S (seed-only) — NEXT

maxHp/attackDamage/attackRange/accuracy are written pre-allocate but never
read post-release. Collapsing their `local*` needs the seed to reach
`allocate` without a twin: either reorder the deboard path so `allocate`
precedes the loadout setters (so those route through the registry), or pass a
seed-spec into `allocate`. Subclass ctors (Drone/DroneHubUnit/MapTurret)
override these stats, so a seed-spec must carry per-instance values — the
reorder is likely simpler. Removes the pre-allocate window entirely.

### Slice 3 — Group C (corpse) — DESIGN FORK

The 5 corpse-read fields need a value readable after `release`. Direction
(user, 2026-06-01): an explicit **death-snapshot** over permanent `local*`
shadows — and the corpse could be realized as a stamped **decal**, or as a
minimal separate **"body" entity** (renderable + location only), which would
also leave room for a future **revive** mechanic. Decide the concrete shape
when we reach it (re-examine the readers with fresh eyes first).
