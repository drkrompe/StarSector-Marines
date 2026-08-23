# W6 — Emplacements and structures

> A turret is a platform, a mount, and a gun. Today it is one enum.

Status: PLANNED

Written: 2026-08-22

Updated: 2026-08-23 — follows W3 and must precede W4 enum retirement.

Read `moddable-weapons-nouns.md` before implementing this story.

## The finding

`TurretKind` was flagged during W1 as a wart — an emplacement's `maxHp` on
the *weapon*. Reading the consumers, it is worse and more interesting than
that: **the same `TurretKind` is already mounted on three different
platforms, and only one of them uses its HP.**

| Platform | Where | Uses `maxHp` | Uses `startingAmmo` | Honors `role` |
| --- | --- | --- | --- | --- |
| Static emplacement | `MapTurret.create` | **yes** | no — "bolted-down defenses don't run dry" | no — forced A2G |
| Shuttle hardpoint | `MountedTurret` / `AirSystem` | no — the shuttle owns HP | **yes** | **yes** |
| Ground vehicle turret | `GroundSystem` | no — the vehicle owns HP | no | no |

The javadoc already documents the conflict rather than resolving it:
`startingAmmo` says "Static `MapTurret`s ignore this", and `role` says
"Static `MapTurret`s default to `A2G`; mounted shuttle turrets honor the
role for target filtering." Those are not weapon properties with awkward
exceptions. They are **platform** properties living on the wrong object.

Two more things are already true and make the split cheap:

- **The renderer already composites two layers.** `UnitRenderService` draws
  a recoil-slid *barrel* sprite over a *base* sprite, both keyed by
  `TurretKind`. The layering exists; only the keying is wrong.
- **Mount slots already exist twice.** `TurretMount` is a shuttle hardpoint
  (which kind + local offset); `MechMountSlot` is the mech equivalent.
  Neither is generalised.

## The shape

Adopt the model mechs already use, which is precisely the three layers this
needs:

```
MechVariant          →  platform: HP, geometry, chassis appearance, slots
MechWeaponComponent  →  mount:    which weapon, ammo capacity, rack size,
                                  appearance layer, which slot family it fits
MechWeapon           →  weapon:   projectile behavior
```

Applied to emplacements:

- **`StructureDef`** (platform) — `maxHp`, footprint radius and
  `hitHalfHeight`, base appearance, and a list of mount slots. A static
  turret emplacement has one slot; a **drone hub has zero**, which is what
  makes this a *structure* model rather than a *turret* model. Future
  bunkers and wall emplacements have one or more.
- **`TurretMountDef`** (mount hardware) — which `WeaponDef` is installed,
  ammo capacity, traverse rate, visual scale, and the base/barrel appearance
  layers. Everything the three platforms above genuinely share.
- **`WeaponDef`** (already exists) — range, damage, accuracy, cooldown,
  burst, AoE, wall damage, arc, flight time, spread, minimum range, indirect
  fire, and its W2 effect layers.

`maxHp` leaves the weapon entirely. Shuttles and vehicles keep supplying
their own, exactly as they already do; static emplacements get theirs from
`StructureDef` instead of from the gun bolted to them.

### What this buys

The same gun on different platforms, which is the thing the current model
cannot express: a Vulcan on a hardened static emplacement, on a shuttle
hardpoint with a magazine, and on a convoy vehicle — three different HP,
ammo and targeting-role answers, one weapon definition. Today that requires
three `TurretKind` entries that duplicate every ballistic field.

It also makes "structures" a first-class thing without inventing a
subsystem. A drone hub is a `StructureDef` with no mounts. A wall bunker is
one with a mount. The distinction the player feels — *this is a building,
not a soldier* — becomes a data shape rather than three special cases in
`UnitType`.

### The sprite question, answered

Vanilla turret art bundles the gun with its mount (`vulcan_turret_base.png`
is a Vulcan *on a pedestal*), so the base/barrel pair belongs to the
**mount**, not the platform — the same call `MechWeaponComponent` makes with
its `appearanceSelector`. The platform's own appearance layer stays empty for
today's emplacements and is there for structures that have a visible body of
their own, like the drone hub.

That means **no new art is required to land this.** It is a re-keying of
sprites that already exist.

## Out of scope

- Walls. They are a separate damage currency and a separate render path; a
  wall becoming a `StructureDef` is a later question.
- Player-facing turret customization. This makes swappable mounts
  *expressible*; whether the player ever swaps one is a progression
  question, not a substrate one.
- `MechVariant` itself. It already has this shape and does not need to move.

## Acceptance

- `TurretKind` is gone, replaced by a structure catalog, a mount catalog and
  `WeaponDef` entries.
- The three platforms each supply their own HP, with no field that two of
  them ignore.
- A single weapon def is mounted by at least two different platforms in the
  shipped data, proving the split is real rather than a renaming.
- Turret behavior, aim, recoil rendering and shuttle mounts are unchanged —
  parity-pinned the same way W1 and W2 pin theirs.

## Open questions

- Does `StructureDef` subsume `UnitType.TURRET` / `DRONE_HUB_STRUCTURE`, or
  sit beside them? Those two are already "zero-base placeholder" archetypes
  whose real stats come from elsewhere, so subsuming looks right — but
  `UnitType` is load-bearing for the roster's archetype predicates
  (`isStatic`, `isTurret`, `isDroneHub`) and that is a wider change than this
  story.
- Is traverse rate a mount property or a platform one? Mount, on the
  argument that it describes the gun carriage — but a heavier chassis
  plausibly slews slower, which would make it a product of both.
