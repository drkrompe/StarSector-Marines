# D5 — Located armor for vehicles, and a mech's back

> A flanked tank should lose its side, not a share of everything.

Status: IN PROGRESS — implemented; mech rear plate on, vehicle plates behind a default-off switch until flanking reads right
Written: 2026-10-20
Updated: 2026-10-20 — first balance reading: flanked tanks die slower, not faster

Read `combat-durability-nouns.md` and `mechs-nouns.md` before implementing this story.
The damage doll that draws these locations is `damage-doll.md`; the location
names below are the contract between the two and change only in both.

## Goal

Vehicles hold damage where it lands, as mechs already do, so that flanking is a
tactic and a damage doll can show something true. Owner decision 2026-10-20:
real per-location damage, MechWarrior-style, for vehicles; and a rear torso
plate for mechs.

This amends the standing law that a mitigation arc is no argument for localized
armor: vehicles join mechs as a deliberate exception, by this model.

## Model

**Vehicle locations.** A hull's armor capacity is divided into four **plates**,
`FRONT`, `LEFT`, `RIGHT`, `REAR`, each an armor pool holding an authored share
(per `VehicleType`, beside its `ArmorArcs` profile). A hit is assigned to the
plate on the side it arrives from: `ArmorArcs.classify` against the hull's
facing, with SIDE split into LEFT and RIGHT by the sign of the bearing. The arc
profile's rating and plate multipliers still apply to that plate, so a front
plate is both larger and more effective.

Behind the plates is the **hull**: the vehicle's structure pool, and the only
one whose zero is death. Damage a plate cannot absorb, and every hit on a side
whose plate is gone, reaches the hull.

The **turret** stays the section it already is (`HullTurretSection`): its own
share, losing only the gun at zero.

A new **drive** location (tracks, wheels, lift fans) holds a structure share and
takes an authored share of hits from the sides and rear, and a smaller share from
the front. At zero the vehicle is **immobilised**: it can no longer move, still
turns its turret and fires, and is not dead. Drive overflow reaches the hull.

**Mech rear torso.** The torso's armor pool splits into `TORSO` (front and side)
and `TORSO_REAR`, an authored rear share. A hit the torso arcs classify as REAR
spends the rear plate; the torso's structure stays one pool behind both. The
existing `TORSO_ARCS` efficiency still applies.

## Location names (contract)

Vehicle: `FRONT`, `LEFT`, `RIGHT`, `REAR` (armor), `HULL` (structure), `TURRET`
(armor and structure, optional), `DRIVE` (structure; destroyed = immobilised).

Mech: `HEAD`, `TORSO`, `TORSO_REAR` (armor only; structure is the torso's),
`ARMS`, `LEFT_SHOULDER`, `RIGHT_SHOULDER`, `LEGS`.

Each location exposes, read-only to presentation: fitted, armor fraction (or
none), structure fraction (or none), destroyed.

## Constraints

- Allocation-free and deterministic damage path, as today.
- Sizing: the FRONT plate holds the whole of the old hull pool (and TORSO the
  whole torso armor), with the side, rear and drive held on top of it, so a
  machine shot head-on lasts as long as it did. Splitting the old pool instead
  made every head-on fight about 35% shorter: a broad nerf, not a flank bonus.
  Damage with no origin meets the FRONT plate and never the drive; letting it
  pick a plate walked unattributed damage across all four.
- `-Dbattle.vehicle.locatedArmor` switches vehicle locations; it is **off** by
  default until the reading below is answered. `-Dbattle.mech.torsoRear=false`
  is the control for the mech rear plate, which is on.
- Balance on `armorMatchup` against its own control, including
  `-PdefenderFacing=90|180`, and on the hard-target time-to-kill harness. Total
  armor and structure are unchanged; only where they sit changes.
- Existing saves need not load (owner, 2026-10-20).

## First reading (armorMatchup, 2026-10-20)

Mean TTK against each run's own control (located / control): head-on vehicle
duels 28.0s / 26.4s and mech duels 42.6s / 45.1s, both close to parity as the
sizing intends. **Two Hounds either side of a tank (`-PattackerSpread=180`) took
60.5s / 44.2s, and 65.8s / 28.6s against the heavy-gun tank**: the flank that
should be faster is slower. The attackers stood further off (median range 17.3
against 10.7) and spent twice as long flux-limited, so the cause is most likely a
reader of the vehicle's ARMOR column — now the sum of four plates and the turret —
changing how a mech chooses to fight it, rather than the damage path.
`-PdefenderFacing` is no clean test here: the defending hulls turn under their own
doctrine. Next: find what reads ARMOR / ARMOR_MAX for target choice or duel range,
and have it read the plate it would hit, then re-run the flank pair.

## Acceptance

- A tank shot only from its left loses its LEFT plate, then hull, while FRONT,
  RIGHT and REAR stay full.
- A destroyed drive stops movement and leaves the turret firing.
- A mech shot from behind spends `TORSO_REAR`, not `TORSO`.
- `armorMatchup` rows move against the control in the directions the model
  predicts (flanking and rear fire faster), with the reading folded into
  `combat-durability-nouns.md`.
