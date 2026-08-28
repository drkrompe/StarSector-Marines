# Assault area-defense command

Status: IN PROGRESS — production implementation complete; awaiting live acceptance and canonical-duration evidence review.

Written: 2026-08-25

Updated: 2026-08-27 — moved under `assault-command.md`; production ownership,
reserve, and hidden-information behavior remain awaiting live evidence review.

The completed implementation review added immediate readiness-to-response
supersession, dispatchable reserve exchange, casualty coverage repair,
source-honest cross-area report relocation, and strength-based bounded
counter-concentration. The 600-tick forced-serial smoke now gates attacker
allocation, defender response, response bounds, unrelated coverage, and repeat
trace/metric determinism; live and canonical-duration review remain deferred.

Read `mission-command-nouns.md`, `assault-command.md`,
`autonomous-mission-command-foundation.md`,
`assault-search-sector-picture.md`, and
`commander-trace-and-balance-harness.md` before planning this story.

## Intent

Give Assault defenders a coherent two-dimensional security problem rather than
leaving them as ambient targets for an omniscient marine sweep. Defenders hold
authored places, retain a mobile reserve, and respond to their own reports;
attackers search and converge using their own coverage and beliefs.

## Scope

- Define defender area-security groups around authored strongpoints or bounded
  patrol areas, plus an explicit mobile reserve.
- Mobilize bounded support toward defender-reported active sectors while
  preserving externally owned garrisons and avoiding exact hostile briefings.
- Define local fallback and counter-concentration when a place is overmatched,
  without inventing a directional front.
- Publish defender phase, group, reserve, report, directive, and reason through
  the common commander snapshot.
- Add paired no-input acceptance with the attacker story.

## Acceptance

- [x] An unseen marine does not activate or retarget defender command.
- [x] First legal contact can mobilize bounded reserve support without emptying
  every other defended area.
- [x] Defender assignments remain stable across small report fluctuations and
  release when the report expires or the defended context ends.
- [x] The attacker can complete a whole search from coverage and beliefs rather
  than live hidden-defender occupancy.
- [x] Both command pictures remain distinguishable and deterministic in the
  selected-squad panel, dump, and headless trace.

## Constraints

- Assault has no territory, canonical axis, compound capture, or keep.
- Objective truth may know that defenders remain for victory; command search
  and targeting may not use their hidden live cells.
- Strongpoint/garrison ownership must be explicit before reserve allocation.
- Production setup must reserve at least one complete mobile patrol squad when
  force size permits; the current zero-minimum allocation may consume the
  entire roster as authored garrisons.
- Existing reinforcement squads retain `REINFORCEMENT` authority until an
  explicit handoff. The Assault defender command may own only its captured
  starting mobile pool.
- `GarrisonAmbush` may not use its current map-wide live-hostile scan as a
  security alarm, and Assault may not install the occupancy-driven
  `ObjectiveLostTrigger`. Both require belief/report-honest replacements in
  this story; own-force `GarrisonDepletedTrigger` remains legal.

## Exit

Fold durable Assault defense vocabulary into `assault-command.md`, add this story to
the mission-command shipped ledger, and delete it when the paired command duel ships.

The durable vocabulary is folded. Keep this story until the deferred live play
pass and canonical-duration `commanderEvidence -Pmission=assault` review close
the paired command duel.
