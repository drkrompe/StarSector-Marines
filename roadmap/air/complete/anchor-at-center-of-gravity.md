# Story — Anchor air entities at the authored center of gravity

> Shared-core member of the [`air/`](../overview.md) category. The unifying fix
> behind the turret-position bug (see
> [`turret-mounts-from-ship-spec.md`](turret-mounts-from-ship-spec.md)) — it
> **supersedes** the per-slot center compensation shipped there.

## Shipped — `5146218`, in-game rotation eyeball pending

Landed as designed. New `HullPivotResolver` gives `(pixelCentre − center)` per
hull; `ShuttleRenderSystem` offsets the hull sprite's pixel centre from `body` by
that pivot (rotated by facing, scaled by altitude), so `center` (the CoG) sits at
`body` and the hull rotates about it. The `WeaponSlotParser` compensation was
reverted to the raw center-relative scrape; turrets, engine FX, and the sim all
read `body + R(facing)·offset` unchanged — now correct because `body` is the CoG.
Engine FX fixed for free (no engine-parser change). `TurretSlotPreviewTest`
re-anchored at `center`; regenerated previews confirm quads sit on the painted
hardpoints across the roster.

**Outstanding:** in-game eyeball that a long hull (Valkyrie) now pivots about its
aft CoG rather than its geometric middle, and that turrets/plumes stay glued
through the turn. Future collision `bounds` (ships story) inherit this anchor for
free. The full `gradlew :test` run regenerated the previews clean.

## The insight

A vanilla `.ship`'s `center` is the hull's **centroid of gravity** — the point
the ship pivots around, and the origin **all** authored geometry is relative to:
`weaponSlots`, `engineSlots`, and `bounds`. It is `[xFromLeft, yFromBottom]` in
sprite pixels, and it is **not** the sprite's geometric pixel centre (it sits
toward the tail — engines/armour mass aft: Kite −5px, Buffalo −12px, Valkyrie
−19px of vertical offset).

Today the air stack anchors everything at the **sprite pixel centre**:
- the hull sprite renders centred on its pixel centre at `body`, and **rotates
  around the pixel centre**;
- slot scrapes were authored at pixel centre, so the turret story had to fold a
  `(center − pixelCentre)` compensation into `WeaponSlotParser` to land turrets
  on their hardpoints — a band-aid on the symptom.

## The fix — make `body` the centre of gravity

Treat each air entity's position (`AirBody.x/y`) as the authored `center` (CoG):

1. **Render** the hull so `center` sits at `body`, and **rotate around `center`**
   (standard rotate-about-arbitrary-pivot):
   `drawCentre = body + R(facing) · (pixelCentre − center)_local`, then draw the
   sprite centred at `drawCentre` rotated by `facing`. Now `center` is fixed at
   `body` for every facing.
2. **All geometry becomes center-relative with no compensation.** Turret/weapon
   slots, engine slots, and (later) collision `bounds` are drawn at
   `body + R(facing) · slotOffset` directly — because `body` *is* the origin they
   were authored against.

### What this subsumes / fixes, in one move

- **Turrets** land on hardpoints with the raw center-relative scrape — so the
  `WeaponSlotParser` center compensation is **reverted** (the offset moves to the
  one hull-anchor instead of every slot).
- **Engine FX** are fixed **for free**: `ShipSpecEngineParser` has the same
  latent pixel-centre offset today (just hidden on fuzzy glows); once the hull
  anchors at CoG, engine slots are correct with no parser change.
- **Rotation feel** becomes correct: a long ship swings its nose and tail around
  its centre of mass — a Valkyrie pivots around its aft CoG, not its geometric
  middle. This is the "flight model assumes the authored pivot" upgrade.
- **Collision bounds** (future ships story) inherit the right origin, so hull
  hits resolve against the real silhouette without a second compensation.

## Design

- **`HullPivot` resolver** (`battle/air/engine/`) — `(pixelCentre − center)` in
  our cell frame, per hull, from the `.ship` `center`/`width`/`height`. Cached,
  mirrors `HullFootprintResolver`. (Or fold into `HullFootprintResolver` as a
  second accessor — both read the same spec.)
- **`ShuttleRenderSystem` hull pass** — offset the draw centre by
  `R(facing)·pivotOffset` so `center` lands on `body`.
- **`Shuttle.turretWorldX/Y` + `EngineFxRenderer`** — already
  `body + R(facing)·offset`; just feed them **raw center-relative** slot offsets
  (revert the `WeaponSlotParser` compensation).
- **Sim semantics** — `body.x/y` now denotes the CoG. LZ arrival / hover-follow
  read `body` as "where the ship is"; the sub-cell shift is immaterial. Confirm
  nothing keys on body == geometric centre.

## Verification

- Extend `TurretSlotPreviewTest` (and an engine equivalent) to draw the hull
  re-anchored at `center` and confirm slots land with the **raw** scrape.
- In-game: a hovering Valkyrie should rotate about its aft CoG, not its middle;
  turrets + engine plumes stay glued through the turn.

## Out of scope

- Ground vehicles / `MapTurret`s (their art origin == position already).
- The kinematic turn *dynamics* (moment of inertia etc.) — this is a render +
  origin-semantics change; `AirBody` stays a point-mass + facing.

## Relationship to shipped work

- Reverts the `WeaponSlotParser` `(center − pixelCentre)` compensation from
  [`turret-mounts-from-ship-spec.md`](../complete/turret-mounts-from-ship-spec.md)
  (moves it to the single hull anchor).
- Closes the "engines have the same latent offset" note left on
  `WeaponSlotParser` / `ShipSpecEngineParser`.
