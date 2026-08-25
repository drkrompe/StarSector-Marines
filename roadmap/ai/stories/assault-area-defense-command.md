# Assault area-defense command

Status: DRAFT — pair belief-honest attacker search with defender strongpoint and reserve behavior.

Written: 2026-08-25

Read `ai-nouns.md`, `autonomous-mission-command-foundation.md`,
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

- [ ] An unseen marine does not activate or retarget defender command.
- [ ] First legal contact can mobilize bounded reserve support without emptying
  every other defended area.
- [ ] Defender assignments remain stable across small report fluctuations and
  release when the report expires or the defended context ends.
- [ ] The attacker can complete a whole search from coverage and beliefs rather
  than live hidden-defender occupancy.
- [ ] Both command pictures remain distinguishable and deterministic in the
  selected-squad panel, dump, and headless trace.

## Constraints

- Assault has no territory, canonical axis, compound capture, or keep.
- Objective truth may know that defenders remain for victory; command search
  and targeting may not use their hidden live cells.
- Strongpoint/garrison ownership must be explicit before reserve allocation.

## Exit

Fold durable Assault defense vocabulary into `ai-nouns.md`, add this story to
the AI shipped ledger, and delete it when the paired command duel ships.
