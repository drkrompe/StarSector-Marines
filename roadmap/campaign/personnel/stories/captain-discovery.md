# Captain discovery

**Status:** ACTIVE — Slice 1 in progress (2026-08-19)

## Problem

Captains currently enter the campaign only through the one-off starting-roster
bootstrap. The personnel model has no canonical way for campaign discoveries to
offer a named captain, remember that offer across saves, or admit the captain
without duplicating them when an interaction replays.

The intended first acquisition source is a survivor recovered from a cryo-pod
aboard a salvageable derelict. Personnel owns the durable candidate and roster
transition; salvage owns detection and the interaction that presents it.

## Contract

### Candidate authority

- `MarineRoster` owns persisted captain candidates as part of its existing
  XStream object graph. Legacy saves backfill an empty collection.
- Each candidate has a non-blank, namespaced source key. Discovering the same
  source again returns the original candidate without changing its identity or
  authored details.
- A candidate freezes the future captain id, name, portrait, starting rank,
  optional professional trait, and discovery day. It never stores campaign API
  objects.
- `IDEALIST` and `CYNICAL` are earned moral outlooks and cannot be seeded by
  discovery.

### Resolution and roster admission

- A candidate is `AVAILABLE`, `ACCEPTED`, or `DECLINED`. Resolution is
  irreversible.
- Acceptance is atomic: the candidate must be valid and available, and the
  roster must have room. Failure changes neither candidate nor roster.
- Successful acceptance creates exactly one `ACTIVE` captain with the frozen
  id and authored details, then marks the candidate accepted.
- Replaying acceptance returns the already-admitted captain and never consumes
  a second roster slot. A declined candidate cannot later be accepted.
- The existing direct `MarineRoster.add` seam remains for bootstrap, tests, and
  migration. Campaign acquisition must use candidate acceptance.

### Save repair

- Missing candidate collections backfill empty.
- Duplicate source keys fail closed: roster order keeps the first valid row and
  discards later duplicates during repair.
- An available candidate whose frozen captain id is already present is repaired
  to accepted. An accepted candidate without its matching captain remains
  resolved and cannot invent a replacement.

### Source and presentation boundary

- Derelict salvage will provide a stable namespaced source key and authored or
  deterministically generated candidate details.
- The salvage interaction will present accept/decline choices and explain a
  full roster without consuming the offer.
- Candidate discovery does not alter normal salvage rewards, captain promotion,
  injury recovery, stationing, moral-outlook drift, or battle balance.

## Slices

1. **Persistent intake domain** — source-keyed candidate discovery, durable
   resolution, capacity-safe exactly-once acceptance, legacy backfill/repair,
   and focused tests.
2. **Derelict salvage source** — hook the canonical salvage path, select
   eligible derelicts, create one deterministic cryo-pod survivor per source,
   and preserve vanilla salvage behavior.
3. **Interaction and roster presentation** — survivor copy, accept/decline/full
   roster feedback, and readable provenance/state where personnel is managed.

## Non-goals

- Random tavern hiring, markets, wages, dismissal, or captain trading.
- More than one candidate from a single source.
- New combat traits, outlook reversals, interpersonal conflict, or changes to
  the existing roster-cap formula.
