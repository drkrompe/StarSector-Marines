# Raid objective and command contract

Status: IN PROGRESS — the primary-target objective, paired commanders, diagnostics, fixture, and evidence adapter are implemented; canonical-duration and live acceptance remain.

Written: 2026-08-27

Updated: 2026-08-27 — fixed the first contract as abstract seizure plus survivor egress and implemented the production vertical.

Read `mission-command-nouns.md`, `raid-command.md`, and `contracts-nouns.md`
before planning this story.

## Intent

Give Raid a battle objective that supports a distinct strike-and-withdraw
command duel instead of inheriting Assault elimination or Sabotage planting.

## Scope

- Define stable raid-target identity, authored geometry, interaction/progress,
  completion, and battle terminal law.
- Define whether objective value is destroyed, seized, carried, or abstractly
  secured, and how campaign loot/disruption consumes the result.
- Define when egress becomes legal, which actors or payload must withdraw, and
  how partial success or abandonment resolves.
- Specify perspective disclosure and identity-free defender alarms separately
  from faction beliefs.
- Replace the placeholder factory with a Raid fixture and objective owner.
- Implement paired target-network plus ingress/egress command, diagnostics,
  dump/overlay presentation, and argument-selected headless evidence.

## Acceptance

- [x] Raid can succeed through its authored target and egress law without
  eliminating every defender.
- [x] Attackers progress through the first contract's approach/breach, service,
  and withdrawal phases from legal facts without hidden occupancy. Seizure is
  abstract, so no separate carrier-consolidation phase exists yet.
- [x] Defenders preserve targets and mobilize bounded response from the first
  contract's legal alarm without learning exact raider state.
- [x] Objective, command, campaign resolution, and reinforcement ownership have
  distinct tested authorities.
- [x] Selected-squad, mission dump, and duplicate forced-serial traces explain
  both perspectives and the neutral outcome.

## Remaining acceptance

- [ ] Review a canonical-duration `commanderEvidence -Pmission=raid` duplicate
  run for target arrival, alarm response, seizure, and egress behavior.
- [ ] Live-accept that local contact interruptions resume service/withdrawal
  assignments without creating a target-room brick or leaking egress to the
  defender picture.

## Constraints

- Do not model Raid as three Sabotage sites with shorter timers.
- Do not use campaign disruption or rolled loot as battle-time commander input.
- Do not create a Raid-only Gradle task.

## Exit

Fold durable objective-independent command laws into `raid-command.md`; fold
objective and campaign laws into their owning mission/campaign noun docs; add
the story to `shipped.md` and delete it when the paired migration ships.
