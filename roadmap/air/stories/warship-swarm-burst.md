# Story — Warship swarm-burst munitions (slice 2)

Status: PLANNED

Written: 2026-10-11

Read `warship-missiles.md` (the whole design) and `warship-missile-flight.md`
(the slice this builds on) first.

## Problem

A swarm weapon (Locust, Swarmer, Squall, an Annihilator stream) is thirty or
twenty straight shells, and a MIRV (Hurricane, Hydra, Sabot) lands as one carrier
blast at a fraction of its true payload. The owner wants a salvo that spreads,
weaves and arrives as a swarm of many small impacts.

## Goal

A volley owns several bodies, each with its own closed-form path: a launch fan,
weave, and staggered arrival onto distinct aim points; MIRV carriers fly to
`splitRange`, release the spec's warheads and bloom. The weapon's damage and area
are the budget; point defence can kill bodies individually and is saturated by
volume.

## Scope

1. **Volley and body.** A volley (one trigger pull) owns a seed, a cap and a
   cue; a body owns a path, a damage share and a blast. Bodies are what point
   defence sees (an interceptable shape replacing "shell or projectile" in
   `WarshipPointDefence`).
2. **Converge for racks and pods.** Fan, weave, golden-angle aim points over the
   footprint, near-to-far or far-to-near ripple, stagger jitter, via the
   authored archetype table (`ROCKET_RACK`, `SWARM_LIGHT`, `MIRV_BLOOM`, `HEAVY`)
   with id overrides; unmatched weapons derive from the spec.
3. **Bloom for MIRV.** Carrier to `splitRange` (`minTimeToSplit`, `canSplitEarly`),
   split sound and smoke, `numShots` warheads in the spec's `arc` at `spreadSpeed`
   curving to aim points. Damage per warhead is the sub-spec's, so the real
   payload lands (design question 3).
4. **Budget.** Summed damage equals the weapon's for any body count; where the
   body count is synthetic the area is preserved (`R / sqrt(n)` per body).
5. **Caps and fusing.** 32 bodies per volley, 160 per ship, 480 per battle; over
   a cap, fuse into weighted bodies that preserve sums.
6. **Point defence.** Toughness from `proj hitpoints`; a carrier killed before
   its split loses the payload; no other point-defence rule changes.
7. **Presets.** FAITHFUL, FLAVOURED (default), CINEMATIC as a per-ship switch;
   CINEMATIC synthetic splitting may be deferred to a later story if the owner
   wants it.
8. **Hardened targets.** Check that sub-threshold swarm hits still breach walls
   and roofs in aggregate; if not, accumulate wall damage per volley.

## Out of scope

Homing at moving units, anti-fighter preference, Dragonfire standoff, Hydra
flanking, footprint telegraphs, restricted weapons.

## Acceptance

- Unit: the budget (sums equal for any n; fusing preserves sums); the cap; path
  determinism per `(seed, index)`.
- Scene `warship-swarm-burst` against the single-blast control: Locust, Hurricane
  and Squall on a dispersed squad deliver equal total damage, kill more of a
  dispersed group, and impacts are staggered over a bounded window. Verdicts:
  `damage-budget-conserved`, `bodies-capped`, `landing-clock-preserved`.
- Scene extension `warship-point-defence`: same point defence against a 30-body
  volley and against three Reapers; verdicts `pd-saturates` and
  `carrier-kill-loses-payload`.
- `createSnapshots` suite `warship-missiles` gains rocket volley, swarm converge
  and Hurricane bloom loops at 1920x1080 with controls.
- A `renderEvidence`-style figure for the `SHUTTLES` layer with a full swarm in
  view and with the cap hit, against its own `-D` control.

## Plan

1. Volley/body split behind the existing shell list; same results at body count one.
2. Hashed fan, weave and aim-point layout; the archetype table.
3. MIRV bloom from the sub-spec; honest payload.
4. Budget, caps, fusing.
5. Point defence over bodies with toughness.
6. Scenes, snapshots, performance figure; fold into `air-nouns.md`.
