# Rescue corridor command picture

Status: DRAFT — expose the asymmetric escort-versus-pressure operation without fabricating a conventional swarm commander.

Written: 2026-08-25

Read `ai-nouns.md`, `campaign-event-nouns.md`,
`autonomous-mission-command-foundation.md`, and
`commander-trace-and-balance-harness.md` before planning this story.

## Intent

Make civilian rescue legible as an autonomous asymmetric battle. Marine command
owns the shelter-to-cohort-to-lift corridor, guards, and moving screen; the
swarm pressure director owns its spawned attackers and mission-authorized
pressure. Both publish inspectable intent under their actual fiction.

## Scope

- Publish a marine corridor picture with phase, cohort progress, screen slots,
  pickup/shelter duties, locally slowed elements, pressure, directives, and
  reasons.
- Give the swarm pressure system an immutable threat-pressure picture covering
  spawn wave ownership, legal cohort/screen target context, approach/choke
  allocation, and current pressure reason.
- Define exactly what cohort route/state the pressure director is authorized to
  know from mission setup, distinguishing that from discovered marine positions.
- Include both pictures in headless trace and perspective-labelled diagnostics.
  Marine squad directives appear in selected-squad and squad-dump surfaces;
  swarm pressure intent appears in a mission-director diagnostic and dump.
- Preserve reinforcement/spawn authority and existing separated escort slots.

## Acceptance

- [ ] A zero-input rescue progresses from shelter release through corridor
  escort to pickup or an explained terminal failure.
- [ ] Marine mobile squads remain distributed across the screen while authored
  pickup and shelter guards keep their duties.
- [ ] The pressure director allocates its owned attackers across legal
  approaches/chokes without being represented as a fake human reserve command.
- [ ] Neither picture reveals facts outside its mission disclosure and
  faction-knowledge contracts.
- [ ] Selected-squad and squad-dump output agree on Marine phase, ownership,
  directive reason, and target semantics.
- [ ] Mission-director diagnostics and dump agree with the perspective command
  trace on swarm phase, owned wave, pressure reason, and target context; the
  neutral referee trace reports outcomes without becoming director knowledge.

## Constraints

- Do not force swarm units into squad beliefs, conventional garrisons, or a
  mirrored `MissionCommand` solely for architectural symmetry.
- Civilian objective and evacuation systems retain cohort state and outcome
  authority.
- This story does not define generic Extraction; that mode still lacks one
  authoritative payload and egress law.

## Exit

Fold durable asymmetric-director vocabulary into `ai-nouns.md`, add this story
to the AI shipped ledger, and delete it when both pictures and trace ship.
