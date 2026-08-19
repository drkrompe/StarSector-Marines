# S3c — obstacle catch heights

> **Shipped 2026-08-19** on `session/ballistics-obstacle-heights`,
> implementation commit `139fcb3b`. Probabilistic doodad crossings and
> directional wall-edge cover now test the round's Z against a data-driven
> catch band before rolling their unchanged block chance. Structural walls
> remain full-height hard stops. Full suite: 1541 tests green.
>
> **Landed vs. planned deviations:** none. Level-zero and vertically cleared
> cover contacts no longer consume a random value, which is the specified
> two-stage interception order and keeps deterministic queues honest.

Original contract below, kept for the record.

---

> Let visible high/low fire interact honestly with cover. Structural walls
> remain full-height hard stops; probabilistic doodad and wall-edge cover rolls
> occur only when the round's target-plane Z intersects an authored catch band.

Parent design: [`../overview.md`](../overview.md) §4–6.
Depends on shipped S3b (`../complete/s3b-target-plane-accuracy.md`).

## Problem

S3b gives every small-arms round a real target-plane elevation, but obstacle
interception still ignores it:

- a floor-level rubble decal can block a visibly high bolt;
- a shelf and a sandbag line have the same infinite vertical reach whenever
  their cover roll succeeds;
- directional wall-edge cover can clip a round above the exposed body;
- structural walls correctly stop everything, but that full-height behavior is
  currently shared implicitly by every obstacle.

The block probability still expresses gaps, material, and imperfect footprint
coverage. It should be conditional on first intersecting the obstacle's vertical
silhouette.

## Two-stage interception

At each probabilistic obstacle contact:

1. Compute the round's existing linear Z at the crossing/contact.
2. Test `abs(roundZ) <= catchHalfHeight`.
3. Only when that vertical test passes, roll the existing block probability for
   the obstacle's cover level (`15% / 30% / 45%`).

Clearing the catch band consumes no block roll. This intentionally reduces the
aggregate protection of low cover while preserving every conditional block
chance and all weapon accuracy values.

The height remains a symmetric target-plane combat silhouette centered on Z=0,
matching `UnitType.hitHalfHeight`. It is not terrain-relative altitude or a
promise of full 3D geometry.

## Structural walls versus wall-edge cover

`NavigationGrid.firstWallOnLine` continues to treat non-walkable, opaque wall
cells as full-height hard stops. A high or low round cannot fly through a
building wall.

Directional cover on the target's cell represents clipping the exposed edge of
an adjacent wall, parapet, embankment, or mount. `NavigationGrid` therefore
stores a per-facing `coverCatchHalfHeight` alongside the cover level:

- the existing `setCoverAtFacing(x, y, facing, level)` API uses a default
  `0.35`-cell catch band for non-zero cover;
- an overload accepts an explicit catch half-height for authored special cases;
- zero cover always stores zero height;
- the resolver gates only the edge-clip roll, never the structural wall cap.

## Doodad data

Registered doodads gain a JSON `ballisticHalfHeight` field carried through
`DoodadDef` into `Doodad`. Built-in values are authored by silhouette family:

| Family | half-height |
|---|---:|
| rubble/debris decals | 0.16 |
| damaged chairs/boxes | 0.22 |
| boxes/pallets/sandbags | 0.28–0.30 |
| chests/pipes | 0.32 |
| crates/desks/reels | 0.38–0.40 |
| drums/scrap | 0.45 |
| dumpsters/generators | 0.50–0.55 |
| crate stacks/damaged shelves | 0.60/0.55 |
| full shelves/closed doors | 0.75 |

The JSON field is optional for submod/backward compatibility. Missing values
fall back by cover level: light `0.18`, medium `0.38`, heavy `0.60`, none `0`.
Legacy frame constructors use the same fallback and expose an explicit-height
overload for tests and non-registry markers.

Several doodads may occupy one cell. `DoodadService` stores the maximum authored
height separately for each cover level, then returns the strongest level whose
height contains the queried Z. This avoids accidentally combining a short heavy
prop with a tall light prop into tall heavy cover.

## Tests

- Registry parsing pins representative low/medium/tall built-in heights and the
  cover-level fallback.
- Doodad service pins per-level stacked-height selection and symmetric high/low
  clearance.
- Resolver pins a centered round being blocked, an elevated round clearing a
  low doodad without consuming its block roll, and a tall doodad still catching
  the same path.
- Directional cover pins intersecting edge cover blocking and an elevated
  on-target round bypassing a low catch band without consuming a roll.
- Structural walls remain hard stops for elevated trajectories.
- Existing flat-shot event-order and tuning-anchor tests remain unchanged.

## Non-goals

- Absolute ground height, muzzle height, gravity, or airborne-unit altitude.
- Per-pixel/sprite-alpha collision or horizontal doodad sub-cell footprints.
- Making structural walls passable to high shots.
- Rebalancing the 15/30/45% conditional block chances.
- Applying the resolver to mech/turret direct fire (still S4).
