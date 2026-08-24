# Progressive reinforcement playtest

Status: PARKED — code complete; manual in-game acceptance pending.

Written: 2026-08-23

The standing reinforcement model and Conquest ownership live in
`reinforcement-nouns.md`. This parked story contains only the manual acceptance
and tuning work still needed for the shipped front-line response.

## Acceptance

- [ ] On a Conquest map, an open once-manned defender position in a contested
  slice receives a response through the front-line trigger; non-Conquest maps
  retain their legacy compound trigger.
- [ ] A full-wipe response uses a safe delivery area, then advances toward the
  assigned position without duplicating requests or dropping troops into
  impassable/building cells.
- [ ] A response that is wiped while advancing reopens the position for a
  later request; a position in a conceded slice does not attract ordinary
  front-line reinforcement.
- [ ] Playtest cadence, rear-shift distance, and spread across biome bands at
  normal and accelerated battle speeds; tune only values that make the front
  legible and responsive.
- [ ] Confirm supply-loss fallback and the absence of duplicate or runaway
  requests during a full front wipe.

## Exit

When these checks pass, fold any final standing law into
`reinforcement-nouns.md`, add this story to the Reinforcement shipped ledger,
and delete it.
