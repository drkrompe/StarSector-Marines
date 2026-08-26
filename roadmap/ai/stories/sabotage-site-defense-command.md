# Sabotage site-defense command

Status: DRAFT — pair attacker site task groups with defender guards and a mobile reserve.

Written: 2026-08-25

Read `ai-nouns.md` and `autonomous-mission-command-foundation.md` before
planning this story.

## Intent

Make Sabotage a command duel around named sites. Defenders know and allocate
security to their own installations, but discover attackers through reports and
legal site alarms rather than through omniscient objective progress.

## Scope

- Declare site guards and externally owned posts separately from the mobile
  defender command pool.
- Allocate a bounded reserve across unfinished sites using site importance,
  legal alarm state, defender beliefs, route cost, and current coverage.
- Distinguish routine site security, alarm response, planter interdiction,
  reinforcement, and redistribution after a site completes.
- Publish defender site-group, reserve, phase, directive, reason, and authorized
  alarm evidence through the common snapshot.
- Pair acceptance with stable marine planter/security/reinforcement groups.

## Acceptance

- [ ] Defenders protect every unfinished site without sending the whole reserve
  to the nearest installation.
- [ ] Plant progress changes authoritative site state but does not disclose an
  attacker identity or exact cell unless the alarm contract explicitly permits
  that fact.
- [ ] A legal report mobilizes bounded support; expired evidence releases only
  assignments owned by defender site command.
- [ ] Completing a site redistributes its mobile security deterministically
  while authored posts retain their ownership.
- [ ] The defender commander never overwrites unit-level planter authority or
  reinforcement ownership.

## Constraints

- Sites are named objectives, not tracks or anonymous sectors.
- Charge objectives own progress and completion; AI consumes their disclosed
  state but cannot author it.
- Reinforcement requests and delivery remain outside mission strategy.

## Exit

Fold durable site-defense laws into `ai-nouns.md`, add this story to the AI
shipped ledger, and delete it when the paired Sabotage command ships.
