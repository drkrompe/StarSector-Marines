# C2 — Formation model: the hierarchy as data

> Four surfaces each recompute "how many of my marines are ready" by hand.
> Build the rollup once.

Status: PLANNED
Written: 2026-08-22
Updated: 2026-08-23 — migrated under `company-view-nouns.md`.

No dependencies. Prerequisite for `c3-company-card-stack.md`,
`c4-whereabouts-and-deployed-state.md`, and
`c5-battle-hud-company-rollup.md`.

Read `company-view-nouns.md` before changing this story.

## Problem

The hierarchy the player is meant to perceive — company, squad, marine —
exists in `MarineRoster` only as separate lists plus lookup helpers
(`squadsCommandedBy`, `squadMembers`, `readyCount`, `manningCount`,
`vacancies`, `squadsStationedOn`). Every consumer assembles its own view:

- `SquadDeploymentScreen` loops `roster.squads()`, filters reserves, calls
  `readyCount`, then hand-counts WIA/MIA/KIA per team inline.
- `PersonnelReadiness` computes selected-vs-company ready and shortfall.
- Fleet Armory builds its own per-soldier ordering.
- `ResultsScreen` and the debrief path count survivors their own way.

Adding a card UI on top of that means a fifth ad-hoc assembly, and any two
of them can disagree.

## Goal

One derived, read-only snapshot of the player's organization that every
surface reads: company rollup → captain command → squad → marine, each
level carrying its own counts and state, with deterministic ordering.

## Design

A snapshot record tree, built on demand from `MarineRoster`, holding no
live roster references:

```
CompanySnapshot
  strength / ready / wounded / missing / lost    (marine counts)
  squadsTotal / squadsDeployable
  commands: List<CommandSnapshot>                (one per active captain)
  unassigned: List<SquadSnapshot>             (no homeCaptainId)
  reserve: SquadSnapshot                      (the reserve pool)

CommandSnapshot
  captainId / captainName / rank / captainStatus
  squadCap     (the officer's rank cap, in squads)
  squads: List<SquadSnapshot>
  + the same count rollup, scoped to this command

SquadSnapshot
  squadId / name / captainId
  manning / vacancies / ready / wounded / missing / lost
  whereabouts   (C4 fills this; HOME until then)
  members: List<MarineSnapshot>

MarineSnapshot
  soldierId / name / status / unavailableUntilDay
  aptitude / band (resolved from issued armour)
  primary + grade / secondary / armor
```

### Rules

- **Derived, never persisted.** No `Serializable`, no xstream exposure, no
  save migration. Built from the roster each time the view rebuilds; the
  roster stays authoritative.
- **Deterministic order.** Captains in roster order, squads in roster
  order within a captain, members in `memberIds` order. Two builds of an
  unchanged roster are equal.
- **Both groupings available.** `commands` gives the per-captain shape;
  a flat `squads()` accessor over all commands + unassigned gives the
  whole-roster shape. Whether C3 emphasizes an officer command or the whole
  company is then a rendering choice, not a model change.
- **Counts defined once.** "Ready" is `MarineRoster.readyCount` semantics
  (ACTIVE and available); "living" includes WIA per
  `MarineRoster.livingCount`. Reuse the roster's existing definitions
  rather than inventing parallel ones — the point is to stop the drift.

### Fold in, don't fork

`PersonnelReadiness` stays the *selection*-scoped answer (required seats vs
selected vs company, shortfall, `needsPersonnel`). Give it a constructor
path that takes a `CompanySnapshot` so the two never disagree, or have it
delegate its company-side counts to the snapshot. Do not duplicate the
seat-requirement logic into the snapshot — deployment capacity is a
mission concern, not an organization concern.

## Slices

1. **The records + builder.** `CompanySnapshot.of(roster)` and the four
   record types. Unit-tested against a fixture roster.
2. **First consumer.** Rewire `SquadDeploymentScreen`'s counts to read the
   snapshot. Behavior-identical; this is the proof the model is right
   before any new UI depends on it.
3. **Readiness reconciliation.** `PersonnelReadiness` sources its company
   counts from the snapshot.

## Acceptance

- `SquadDeploymentScreen` renders exactly the same numbers it does today,
  sourced from the snapshot.
- A roster with two captains, one unassigned squad, and a reserve pool
  produces a snapshot that accounts for every soldier exactly once.
- Building twice without mutating the roster yields equal snapshots.
- No new serialized state; a save round-trip is unaffected.

## Files touched

- New: `ops/detachment/CompanySnapshot.java` (+ `CommandSnapshot`,
  `SquadSnapshot`, `MarineSnapshot`, or nested records in one file).
  `detachment/` already owns the "frozen view of the player's committed
  force" concept (`Detachment`, `CampaignMarineDeployment`,
  `PersonnelReadiness`) and has a `package-info.java` charter to update.
- `ops/SquadDeploymentScreen.java` — first consumer.
- `ops/detachment/PersonnelReadiness.java` — delegate company counts.

## Out of scope

- Whereabouts beyond a `HOME` placeholder — C4 (`c4-whereabouts-and-deployed-state.md`).
- Career/telemetry fields on `MarineSnapshot` — use the standing evidence in
  `progression-nouns.md`; add the projection only when this story needs it.
- Any rendering.

## Open questions

- Does the snapshot belong in `ops/detachment/` or in `marine/` beside the
  roster it derives from? `detachment/` is the better fit for a frozen
  view, but a battle-side consumer (C5) reads a *different* structure
  anyway, so nothing forces the choice. Leaning `ops/detachment/`.
- Should the builder be incremental (cache + invalidate on roster
  mutation)? No — build it on view rebuild, measure if it ever matters.
  A company is tens of objects.
