# Opening-operation objective command

Status: IN PROGRESS — paired frozen command, explicit force ownership, and
scenario diagnostics are implemented; authored-place legibility and live
acceptance remain.

Written: 2026-08-25

Updated: 2026-08-28 — migrated both sides from nearest-hostile scanning to one
setup-authored public place, with spawn-time ownership and typed diagnostics.

Read `mission-command-nouns.md`, `assault-command.md`,
`early-operation-nouns.md`, and
`autonomous-mission-command-foundation.md` before planning this story.

## Intent

Replace omniscient nearest-enemy chasing with a belief-honest paired command
whose duties express the authored opening scenario. Relief treats the local
anchor as the place to preserve against an assault; Counterattack treats the
bandit depot as the place to secure against a defense and bounded local
counteraction. Early Operations remains the authority for terminal mission
objectives and victory.

## Scope

- Consume preserve/secure context authored by Early Operations through
  perspective-appropriate disclosure. Until that feature supplies a terminal
  objective law, preserve/secure are command intent rather than a new victory
  condition.
- Migrate both factions to command frames and explicit pools. Authored local
  garrisons keep their duty; mobile finite forces receive stable scenario roles.
- Remove direct scanning of live opponent positions. Initial movement follows
  authored place/approach context; contact response follows faction knowledge.
- Publish the generic commander snapshot and the rung-specific phase/reason.
- Preserve the finite militia/bandit force model and all campaign ladder laws.

## Acceptance

- [ ] Relief militia preserve the anchor while mobile raiders assault it; the
  arriving player tips a battle that progresses before exact contact knowledge.
- [ ] Counterattack attackers advance on and secure the depot while bandits
  defend or locally counter from legally known context.
- [x] Reversing commander dispatch order does not change the command result.
- [x] Neither side receives a hidden hostile cell from mission command.
- [x] Command intent does not change terminal outcome authority; any new
  preserve/secure victory law is separately authored and accepted by Early
  Operations.
- [x] The selected-squad panel and dump explain scenario role, phase, objective,
  directive authority, and reason for both factions.
- [x] `opening-ladder-live-acceptance.md` remains the owner of live pacing,
  lift, force-ratio, and green-company acceptance.

## Implemented architecture checkpoint

Both perspectives now freeze before either plans, and neither strategy receives
`BattleView`. Relief discloses the local-line anchor; Counterattack discloses a
deterministic walkable bandit-depot anchor. Predeployed local squads retain
`GARRISON` authority, raiders enter the defender mission pool at spawn, and
every opening shuttle conveys Marine mission ownership to the squad it mints.
Stable nearby rallies use existing `DEFEND_AREA` and `SWEEP_SECTOR` execution,
so preserve/secure remains command intent rather than a fabricated interaction
or victory condition.

The current cells are honest deterministic command geometry, not yet guaranteed
COMMS/depot structures. The two unchecked behavioral bullets therefore remain
owned by authored-place work and the live opening-ladder pass.

## Constraints

- Do not generalize opening-operation small-force rules to every Assault.
- Do not add reinforcements, late-game support, or a third ladder rung.
- Do not let objective state disclose a hostile identity or exact approach.
- Do not move terminal objective or victory ownership into AI command.

## Exit

Fold durable opening-scenario command laws into `early-operation-nouns.md`, add
this story to the mission-command shipped ledger, and delete it when implementation ships.
