# Assault search-sector picture

Status: IN PROGRESS — paired production implementation complete; awaiting live acceptance and canonical-duration evidence review.

Written: 2026-08-24

Updated: 2026-08-26 — contracted persistent sector coverage, report-driven
convergence, bounded rechecks, and common command diagnostics for the attacker
slice.

Read `ai-nouns.md`, `autonomous-mission-command-foundation.md`, and
`commander-trace-and-balance-harness.md` before planning this story.

## Intent

Make Assault squads search a lethal, partially known battlefield as a shared
operation. Preserve the useful rectangular sector sweep, but publish which
areas are searched, suspected, active, or reinforced so squads converge on
remaining contact without duplicating the same route or reading hidden units.

## Scope

- Publish an immutable Assault command picture with sector search progress,
  assigned squads, believed contacts, current sweep legs, and assignment reason.
- Keep sector identity stable enough for squads to complete useful search work,
  while allowing a cleared squad to reinforce a neighboring active or suspected
  sector.
- Distinguish a directly active sector from one being searched because of stale
  belief or incomplete coverage.
- Migrate the marine strategy from unrestricted live defender occupancy to its
  frozen command frame and faction-local evidence.
- Expose the picture through selected-squad presentation, the state dump, and
  the common commander trace.

## Acceptance

- [x] Production Assault installs a frame-only Marine commander; no strategy
  decision reads live defender identity, occupancy, or position.
- [x] Stable rectangular sectors publish bounds, visited/total sweep legs,
  status, believed-contact count, assigned squads, and current sweep targets.
- [x] Initial allocation covers reachable unfinished sectors before surplus
  squads reinforce an active or suspected sector.
- [x] Fresh direct reports make a sector active; older or indirect reports make
  it suspected; missing belief is never published as positive clearance.
- [x] Search progress survives tactical replans and sector affinity remains
  stable until coverage, contact, reachability, ownership, or directive
  stability legally changes it.
- [x] A completed first pass becomes a bounded recheck rather than leaving live
  squads command-idle while objective truth still has hidden defenders.
- [x] Selected-squad presentation, the state dump, map overlay, and canonical
  perspective trace show the same published sector and directive facts.
- [x] Focused tests cover hidden-hostile non-disclosure, spread-before-
  reinforcement, report convergence, external ownership, progress persistence,
  deterministic serialization, and production installation.

Paired headless evidence extends the shared `commanderEvidence` task with
`-Pmission=assault`; mission, fixture, and maximum-tick selection are arguments,
not new mission-specific Gradle tasks. A forced-serial smoke run proves both
perspective blocks and byte-stable duplicate traces; canonical-duration review
remains deferred with live acceptance.

The smoke contract also requires live attacker search allocation, a legal
defender report paired with a bounded response, and surviving unrelated-area
coverage. Its summary labels argument-shortened runs `AD_HOC` and publishes
these metrics; focused tests separately prove legal report expiry and responder
release without turning a short continuous-contact smoke into invented evidence.

Both production slices and the shared Assault evidence argument are complete.
Do not retire this file until the deferred live play pass and canonical-duration
evidence review close the paired command duel.

## Constraints

- Assault is a two-dimensional search-and-destroy mission, not a Conquest front;
  it has no canonical forward axis, territorial ownership, or keep convergence.
- Hostile sector state derives from marine beliefs. Live hidden defenders may
  remain objective truth for victory, but may not appear in the command picture.
- Search coverage belongs to command and persists across tactical replans; local
  contact doctrine still decides how a squad reacts once contact is made.
- Defender strongpoints, reserve, reports, and counter-concentration belong to
  `assault-area-defense-command.md`; paired no-input acceptance requires both
  stories.

## Exit

Fold durable search-sector vocabulary and laws into `ai-nouns.md`, add the story
to the AI shipped ledger, and delete it when implementation and live acceptance
ship.
