# D5 — Located armor for vehicles, and a mech's back

> A flanked tank should lose its side, not a share of everything.

Status: IN PROGRESS — implemented; mech rear plate on, vehicle plates behind a default-off switch awaiting an owner call on the free pincer
Written: 2026-10-20
Updated: 2026-10-20 — flank paradox diagnosed: a held flank dies much faster; a free tank reverses out of the pincer and strands the side plates

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

## Reading (armorMatchup, 2026-10-20)

Mean TTK, located / control, eight seeds, breakthrough tank / heavy-gun tank.

**Two Hounds either side of a free tank (`-PattackerSpread=180`): 60.5 / 44.2 s
and 65.8 / 28.6 s.** Not a reader of the ARMOR column: mech target choice, duel
band and mount order read the armor *rating* only. A per-hit trace says what
happens. The tank turns to one Hound and reverses out of the pincer, backing past
the other, so both Hounds end on its front; in every traced fight the rear plate
was never hit and the front was always stripped to zero. Every kill goes through
the front plate, which holds the whole old pool, so the side plates the Hounds
stripped while the tank turned are spent on top of the control's armor: about
780 armor chewed against the control's 550. That is located armor working — a
hull that turns its face spreads the damage — and with the plates held on top of
the front it makes any fight the hull un-flanks longer than the one-pool control.

**A held flank dies much faster.** With the defending hull's duel driving off
(`-Dbattle.vehicle.duelRange=false`), so it keeps the facing it was set down
with, two Hounds kill it from the rear in 14.8 / 29.7 s and 15.2 / 25.5 s, from
the side in 32.6 / 45.8 s and 33.3 / 48.2 s, and head-on in 57.2 / 67.3 s and
65.3 / 51.6 s (near parity, as the sizing intends). One Hound or one Bulwark
loses to either tank on every facing, so those rows time the tank's kill, not the
hull's.

Two levers measured on the free pincer, neither enough alone:

- Thinner flanks (plates 1 / .25 / .25 / .15): 54.5 and 52.3 s, head-on rows
  unchanged (the side plates are never reached head-on). The drive pool made no
  measurable difference.
- A mech dueling a plated hull stepping to an in-band cell on a weaker face
  (`MechDuelRange`, experiment, not kept): 57.5 and 58.9 s, within noise. The
  tank keeps moving, so a step chosen against its present facing is stale by
  arrival.

Owner call before the default flips — what the free pincer should read:

1. Accept it: a tank that faces its attackers is meant to outlast the one-pool
   control, and the flank pays only while held. Flip as is, perhaps with the
   thinner flanks. Multi-attacker fights on tanks get 30–130% longer.
2. Make side and rear plates windows onto the front pool: a flank hit spends the
   facing plate and the same armor from the front, so nothing is stranded,
   head-on stays exact and a stripped side still opens the hull. This breaks the
   acceptance line that a left-only tank keeps FRONT full.
3. Hold the pincer by behaviour: attackers that re-establish a flank against a
   moving hull, or a tank that does not reverse into its second attacker. Real
   AI work in both duel rules.

Before flipping, the infantry special gate (`TacticalScoring.projectedResolvedDamage`,
`shouldCommitSpecial`) should read the plate facing the shooter and the hull's own
structure rather than the summed columns; with plates on it over-reads both.

## Acceptance

- A tank shot only from its left loses its LEFT plate, then hull, while FRONT,
  RIGHT and REAR stay full.
- A destroyed drive stops movement and leaves the turret firing.
- A mech shot from behind spends `TORSO_REAR`, not `TORSO`.
- `armorMatchup` rows move against the control in the directions the model
  predicts (flanking and rear fire faster), with the reading folded into
  `combat-durability-nouns.md`.
