# Captain discovery

**Status:** COMPLETE — all three slices shipped (2026-08-19)

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

### Slice 2 salvage seam

- A transient `ShowLootListener` observes the interaction target immediately
  before vanilla opens its loot panel. It does not mutate the cargo, replace the
  interaction plugin, or own salvage completion.
- Only salvageable entities backed by vanilla's `DerelictShipEntityPlugin` are
  considered. Wrecks tagged as mission items, mission locations, or protected
  mission targets are excluded.
- Eligibility is a stable one-in-eight roll over `derelict:<entity id>`. The
  result cannot change with save/reload, fleet composition, or repeated loot
  callbacks.
- An eligible source deterministically selects a frozen name, vanilla mercenary
  portrait, `PRIVATE`/`CORPORAL`/`SERGEANT` starting rank, and either no trait or
  one already-wired professional trait (`FIELD_MEDIC`, `NATURAL_LEADER`, or
  `SALVAGE_EXPERT`).
- The listener is re-registered defensively on game load. `MarineRoster` remains
  the exactly-once authority if another listener callback reaches the same
  wreck.

## Slices

1. **Persistent intake domain** — source-keyed candidate discovery, durable
   resolution, capacity-safe exactly-once acceptance, legacy backfill/repair,
   and focused tests.
2. **Derelict salvage source** — hook the canonical salvage path, select
   eligible derelicts, create one deterministic cryo-pod survivor per source,
   and preserve vanilla salvage behavior.
3. **Interaction and roster presentation** — survivor copy, accept/decline/full
   roster feedback, and readable provenance/state where personnel is managed.

## Slice 1 complete

`ecf70b58` locks the three-slice acquisition boundary. `055abb97` adds the
XStream-safe candidate model, normalized namespaced source deduplication,
deterministic future-captain identity, immutable authored details, irreversible
resolution, and capacity-safe replayable admission. Legacy saves backfill an
empty candidate collection; repair removes invalid/duplicate rows, adopts an
already-present matching captain, and never invents one for a dangling accepted
offer. Focused intake coverage and the full root automated suite pass.

## Slice 2 complete

`3b70fcc4` locks the non-invasive vanilla seam and deterministic eligibility
policy. `d780e345` registers a transient `ShowLootListener`, filters to ordinary
salvageable wrecks outside mission authority, performs a stable one-in-eight
source roll, freezes a bounded profile from existing portraits and wired
professional traits, and publishes through the Slice 1 roster authority. The
listener never reads or mutates the displayed cargo and repeated callbacks
cannot duplicate the offer. Focused eligibility/profile/replay/cargo-isolation
coverage and the full root automated suite pass.

## Slice 3 complete

`7a32e771` retains the vanilla salvage interaction only when Captain Discovery
owns the continuation, presents the recovered survivor after the untouched loot
panel closes, and routes commission, rescue-service referral, and decide-later
choices through the persisted candidate authority. Full rosters disable the
commission and preserve the offer. Available survivors remain readable and
actionable in Personnel with frozen rank, trait, provenance, and discovery day.
The listener will not take over a continuation already owned by another salvage
flow. Focused interaction/full-roster/decline/replay/ownership coverage and the
merged full root automated suite pass. Manual in-game layout validation remains
deferred per session direction.

## Acceptance criteria

- Ordinary eligible wreck salvage can publish and present no more than one
  deterministic survivor without changing vanilla cargo or reward completion.
- Acceptance adds exactly one frozen captain and cannot exceed roster capacity.
- Decline is irreversible; decide-later and full-roster outcomes preserve an
  available candidate for Personnel review.
- Mission-tagged wrecks and salvage interactions with another continuation
  owner remain untouched.
- Legacy saves, repeated callbacks, stale options, and save repair cannot create
  duplicate captains or resurrect a resolved offer.

## Non-goals

- Random tavern hiring, markets, wages, dismissal, or captain trading.
- More than one candidate from a single source.
- New combat traits, outlook reversals, interpersonal conflict, or changes to
  the existing roster-cap formula.
