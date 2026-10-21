# Breach mission spine

Status: IN PROGRESS — skeleton landed (colony map, breach sequence, eruption-site seam, `colony-breach` evidence); militia, heavy assets remain

Written: 2026-10-21

Updated: 2026-10-21 — items 1, 2 and the evidence argument landed; 3 and 4 open.

Model: `colony-breach-nouns.md`. Marine side: `extraction-command.md`.

## Scope

Turn `BattleSetup.createCivilianRescue` into the Colony Breach battle, in place:

1. **Colony map.** A precinct plan for the rescue (town plus outlying
   installation, BALANCED sprawl) at a new ~400x240 `MapScale` for the campaign
   (HIGH) path. Add a `precinctPlanFor` branch or a rescue-owned derive call, and
   a `MissionMapRequirements` entry naming what the colony must contain
   (residential shelter POI, a clear lift footprint, at least N precinct places
   eligible for bores). Keep the smaller scales for LOW/MEDIUM risk and the
   debug stress path.
2. **Breach sequence.** A `ColonyBreachSequence` installed through the
   existing story-sequence pattern (field on `BattleSimulation`, install
   method, tick line). It owns the breach clock and emits the beat table in the
   noun doc. In this story it drives what exists: perimeter seepage before
   `tremor`, then the existing director. It exposes a narrow seam for
   `bore-spawners.md` to plug eruption into: a list of **eruption sites**
   chosen deterministically from precinct places (outside shelter/lift
   protected zones, minimum spacing, spread between shelter, lift and flanks;
   one mother-bore site far from the lift) and the times they erupt. The sites
   are computed and published (snapshot + beats) here even before bores exist,
   so evidence can see them.
3. **Colony militia.** Replace the bespoke shelter-only garrison with militia
   posts across several colony buildings using the ordinary held-place /
   garrison manning, keeping the shelter guard as one of them. Seat any
   colony turret emplacements through defense-post layouts or `MapTurret`, and
   one or two militia technicals via `VehicleType.MILITIA_TECHNICAL`.
4. **Player heavy assets.** The rescue honours what the player brought: a
   `MechDropDeclaration` allowance for the Marine side when the manifest
   carries mechs, and any vehicle deployment. Do not grant assets the player
   did not bring.
5. **Evidence.** Add `colony-breach` as a `commanderEvidence` mission argument
   with a canonical fixture (`src/test/resources/battle-fixtures/`), and make
   sure the existing `extraction` Rescue evidence still runs (update its
   expectations if the map changed shape). Update `CLAUDE.md`'s supported
   mission list.

## Out of scope

Bores as structures, strains, flow fields, tender call-down, hive posture,
audio. Do not touch `SwarmPressureBehavior` movement (flow-field story) or
`UnitType` (strains story).

## Files owned

`BattleSetup` rescue path, `MapScale`, precinct plan derive for rescue,
`MissionMapRequirements`, new `ColonyBreachSequence` (+ progress/snapshot),
`RescueShelterGarrison` and militia placement, `BattleSimulation` install/tick
lines, `CivilianRescueBattleFixture`, `MissionLaunch` rescue branch, evidence
wiring. `SwarmReinforcementSystem` only for the seepage-then-director switch.

## Acceptance

- The campaign rescue generates a precinct colony at the new scale and passes
  its map requirements, re-rolling deterministically as today.
- `battleProbe` on the canonical fixture shows the beats in order through
  `shelter_reached`, `tremor`, the eruption-site beats and the lift beats.
- Eruption sites are seeded and stable across replays; none sits inside a
  protected zone.
- Militia hold buildings and fight; the shelter release law is unchanged.
- `commanderEvidence -Pmission=colony-breach` and `-Pmission=extraction` both
  complete; new unit tests are under a second each.
- `colony-breach-nouns.md` updated with anything the build taught us.

## Merge early

`bore-spawners.md` is blocked on this story's skeleton: land the sequence,
eruption-site seam and map plan to `main` as soon as they are green, then
continue with militia and heavy assets.
