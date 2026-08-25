# Sabotage site task groups

Status: DRAFT — build the attacker half of a paired site command duel with `sabotage-site-defense-command.md`.

Written: 2026-08-24

Updated: 2026-08-25 — added shared command-foundation and paired defender dependencies.

Read `ai-nouns.md` and `autonomous-mission-command-foundation.md` before
planning this story.

## Intent

Coordinate Sabotage around the mission's named charge sites. Each unfinished
site should have an understandable task group that protects its planter,
secures the approach, and can receive reinforcement without every planterless
squad drifting to the same nearest fight.

## Scope

- Publish one site task-group state per unfinished charge objective, including
  planter squad, security squads, believed pressure, progress, and reinforcement
  need.
- Give squads stable site affinity with deterministic rebalance when a planter
  dies, a charge completes, or another site becomes critically unsupported.
- Distinguish approach security, planter protection, and reinforcing work while
  preserving the planter's unit-level mission authority.
- Expose group membership and assignment reason through the selected-squad panel
  and dump using the common commander snapshot.

## Constraints

- Charge sites are named objectives, not lateral tracks or anonymous search
  sectors. Group state ends or redistributes when its site completes.
- Squad command must not overwrite a live planter's unit-level objective or
  invent charge progress.
- Threat and pressure metrics consume marine beliefs only; objective state may
  come from the mission's authoritative charge-site objective.
- Defender site guards, reserve, legal alarms, and redistribution belong to
  `sabotage-site-defense-command.md`; neither side's story is a reason to share
  perspective state.

## Exit

Fold durable site-task-group vocabulary and laws into `ai-nouns.md`, add the
story to the AI shipped ledger, and delete it when implementation and live
acceptance ship.
