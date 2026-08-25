# Opening-operation objective command

Status: DRAFT — use the opening ladder as the first small-force, non-Conquest command duel.

Written: 2026-08-25

Read `ai-nouns.md`, `early-operation-nouns.md`, and
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
- [ ] Reversing commander dispatch order does not change the command result.
- [ ] Neither side receives a hidden hostile cell from mission command.
- [ ] Command intent does not change terminal outcome authority; any new
  preserve/secure victory law is separately authored and accepted by Early
  Operations.
- [ ] The selected-squad panel and dump explain scenario role, phase, objective,
  directive authority, and reason for both factions.
- [ ] `opening-ladder-live-acceptance.md` remains the owner of live pacing,
  lift, force-ratio, and green-company acceptance.

## Constraints

- Do not generalize opening-operation small-force rules to every Assault.
- Do not add reinforcements, late-game support, or a third ladder rung.
- Do not let objective state disclose a hostile identity or exact approach.
- Do not move terminal objective or victory ownership into AI command.

## Exit

Fold durable opening-scenario command laws into `early-operation-nouns.md`, add
this story to the AI shipped ledger, and delete it when implementation ships.
