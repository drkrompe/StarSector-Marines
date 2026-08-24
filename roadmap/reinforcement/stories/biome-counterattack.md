# Biome counterattack playtest

Status: PARKED — code complete; manual in-game acceptance pending.

Written: 2026-08-23

The standing counterattack model and resource semantics live in
`reinforcement-nouns.md`. This parked story contains only the manual acceptance
and tuning work still needed for the shipped counterattack.

## Acceptance

- [ ] With a stable contested front and a reclaimable conceded slice, a
  counterattack telegraphs, launches a concentrated wave, and resolves through
  the normal front-line presence model.
- [ ] The telegraph gives a truthful reaction window at normal and accelerated
  battle speeds; the world marker, countdown, inbound notice, and outcome
  notice remain readable at common zoom levels and map resolutions.
- [ ] A naturally re-contested target aborts before launch and refunds the
  reservation; a launched failure burns its committed reserve and does not
  silently restore it.
- [ ] Tune burst cost, surplus floor, wave rarity, cooldown, and resolve timing
  so the event threatens the player without making captured ground feel
  permanently unstable.
- [ ] Confirm repeated events, failed waves, and successful retakes leave the
  ordinary reinforcement front and supply gates coherent.

## Exit

When these checks pass, fold any final standing law into
`reinforcement-nouns.md`, add this story to the Reinforcement shipped ledger,
and delete it.
