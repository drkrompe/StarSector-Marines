# Compound prepared ground

Status: IN PROGRESS

Written: 2026-10-13

Updated: 2026-10-20 — decisions settled; aprons generated on Conquest, Junction and Relay Nine and wired to every garrisonable consumer; building envelopes wait for `garrison-stretches.md`.

## Intent

Fighting positions come only from fort belts and any plain sandbag line long
enough to make a twelve-cell run. Compounds, facilities and objective buildings
mostly offer short sandbag aprons that never qualify, and buildings with firing
windows are not garrisonable places at all outside the frontage derivation.
`garrisonables.md` expects many more places worth holding; this story is the map
side of that.

## Survey (before)

Generated Conquest compounds laid no prepared ground at all: on five production
seeds (138 compound places) the map had no fighting-position run outside a fort.
The compound's lookout stood on one bare cell outside its attack-facing edge.
Story maps: Junction had its two dug trenches, Relay Nine and Route Security none
(`gradlew.bat garrisonEvidence`); after, Junction three runs, Relay Nine and Route Security three each.

## Decisions

- **Stamped to qualify, not a smaller kind.** A compound's apron is laid whole
  or not at all, and only where what it leaves is a full run. It is then an
  ordinary fighting-position run, so every consumer — occupy, line, GARRISON,
  the defender's reactive manning, the attacker's hold, the highlight — has it
  with no new kind. A smaller kind with its own floor would have split every
  consumer in two for ground that reads exactly like a trench line.
- **Shape: a sandbagged door.** One course of six thick parapet pieces (the
  fort's own art, so a sandbagged door reads like a sandbagged belt), three
  cells out from the door with a gap exactly the door's width in front of it,
  each wing turning back toward the wall by one bag. The way in stays straight
  and open; a defender behind the course covers the approach and the returns
  cover his flank. About twenty-five cells: a squad's worth.
- **Where: one per compound place, toward the attack.** A door facing the
  attack first, then the attack-facing face itself (the first open ground past
  its wall, for a face with no door), then a door on a flank; never a door facing
  away. Doors wider than two cells are vehicle entrances and get none.
- **Laws by refusal.** No bag on a doorway, wall, fixture, reserved road,
  vehicle corridor or beside another door; the lane and the cells behind the
  bags must be open floor; walkability is unchanged everywhere.
- **The lookout stands in it.** The compound's existing two-man perimeter
  lookout is seated on the cells behind the bags: the token picket, as a fort
  keeps at its gate. The rest of the apron is empty until pressure mans it.
- **Layers.** An apron is the layer in front of its building: the defender's
  garrison driven off it falls back inside the building and holds it until the
  building has been quiet a while or the marines take the compound. The attacker
  mans the aprons of compounds it has taken, under the same share as a taken
  fort's belt.

## Scope

- [x] Survey what prepared ground generated compounds and facilities produce.
- [x] Decide qualify-versus-kind (above).
- [x] Generation: `CompoundPreparedGround`, laid by the Conquest perimeter
  lookout stage and by Junction and Relay Nine.
- [x] Consumers: defender manning and fall back into the building, attacker
  hold of taken compounds; occupy, line and GARRISON take it as any run.
- [ ] Publish building envelopes (apertures and stance cells, already derived by
  frontage) as garrisonables with posts. Waits for `garrison-stretches.md`,
  which is reworking the place model in the registry and the director.
- Keep the authored laws: doors stay open, circulation and vehicle widths
  survive, stand positions never overwrite authored footprints.

## Evidence

- `CompoundPreparedGroundTest` (unit), `CompoundGroundTest`, `HeldGroundTest`.
- `generatedSpaceEvidence`: `CompoundPreparedGroundEvidence` — 98 aprons on
  138 compound places over five seeds, every one a run of 18–26 cells with the
  lookout on it, walkability unchanged.
- `sceneEvidence -Pscene=compound-apron`: quiet, pressed, breach.
- `createSnapshots -Psnapshot=compound-prepared-ground`: control, apron and
  position overlay at one framing; `-Psnapshot=compound-apron` records the scene.
- `commanderEvidence -Pmission=conquest-trial` against its own control
  (`-Dbattle.mapgen.compoundPreparedGround=false`), 2026-10-20: the trial ground's
  objective and keep each take an apron (8 runs at the start become 10). Where the
  fort is on the path nothing moves, because the fight is at the fort. Where it is
  offset and the marines go straight for the compounds, the defender manned the
  aprons 5 times over the four fixtures against 1 without them. Outcomes are within
  noise over the matrix: marine losses 313 against 318, defender losses 332
  against 328, the keep taken in every fixture both ways but the rifles timeout.

## Depends on

Reactive manning, proven on fort belts first (`garrisonables.md`, shipped
2026-10-20); without it, more garrisonables only means more static garrisons.
