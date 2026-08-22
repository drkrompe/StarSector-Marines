# C4 — Whereabouts: where every team actually is

> The game already knows a fireteam is garrisoned three systems away for
> another forty days. No screen says so.

**Status:** not started. Depends on [C2](c2-formation-model.md),
[C3](c3-company-card-stack.md).

## Problem

A fireteam can be: at home and ready, committed to the sortie being
briefed, bound to a stationing contract, or hollowed out by casualties who
return on a known day. The data exists —
`MarineSquad.stationingContractId`, `roster.squadsStationedOn(contractId)`,
`MarineSoldier.status()` + `unavailableUntilDay()`, and the deployment
selection set on `MarineOpsContext` — and no surface aggregates it.

Today the player learns a team is stationed by opening `StationingScreen`,
a separate screen organized by contract rather than by formation, and
learns a marine is recovering by opening the armory. The company view is
assembled in the player's head.

There is also no way to look at the company *at all* except inside a
pre-battle flow. Marine Ops is entered from a planet interaction; the
roster surfaces hang off briefing → deployment → armory. "How is my army
doing" is not a question the mod currently answers on its own terms.

## Goal

Every fireteam carries a legible state chip on its card, and the company
view is reachable without accepting a mission.

## Design

### The whereabouts axis

One enum on `FireteamSnapshot`, resolved in C2's builder:

- `HOME` — available, at the company's home posting.
- `COMMITTED` — selected for the sortie currently being briefed. View-state
  from `MarineOpsContext`, so the builder takes the selection set as an
  argument rather than reading UI state.
- `STATIONED` — bound to a stationing contract; carries the contract id,
  the market/location name, and the term end so the chip can read
  `GARRISON · Kazeron · 41d`.
- `RECOVERING` — no deployable members; the team exists but cannot go.
  Distinct from `HOME` with vacancies, which is deployable-but-thin.
- `LOST` — every member KIA/MIA and the team is awaiting reconstitution.

Marine-level state stays on `MarineSnapshot` (status + return day); the
fireteam chip is a rollup, not a replacement for it.

### Consequences on the card

- A `STATIONED` team is visible but not selectable, with the reason on the
  chip rather than a bare disabled checkbox. Today's screen simply filters
  or fails such a team without saying why.
- A `RECOVERING` team shows the earliest return day, so "wait four days and
  I have a full team again" is a decision the player can make.
- The company band totals separate *strength* (living marines on the
  books) from *available strength* (deployable right now). The gap between
  those two numbers is the interesting one and currently has no display.

### The non-mission entry point

Add a company/personnel route into Marine Ops that lands directly on the
card stack, so the roster is readable between contracts. The dialog
takeover already exists (`showCustomVisualDialog`, `MarineOpsPanelPlugin`,
`ScreenId` routing) — this is a new `ScreenId` destination and an entry
row, not new infrastructure.

## Slices

1. **Whereabouts in the model.** Enum + resolution in C2's builder, with
   the stationing contract lookup and the recovery rollup.
2. **Chips on the card.** Render state, block selection with a reason,
   split strength vs. available in the band.
3. **Standalone entry.** Route to the card stack outside a briefing flow.

## Acceptance

- A stationed fireteam is identifiable, with its location and remaining
  term, without leaving the company view.
- A team whose last deployable marine is recovering reads as unavailable
  *with a return day*, not as an empty row.
- Selection blocking always states a reason (command limit, stationed,
  no deployable members).
- Opening the company view between contracts shows the same stack with the
  briefing-only affordances (seat counts, selection) absent or inert.
- Replay-safe: the view reads state, never writes it. No contract or
  stationing mutation happens from this screen.

## Files touched

- `ops/detachment/CompanySnapshot.java` — whereabouts resolution.
- `ops/SquadDeploymentScreen.java` — chips, blocked-selection reasons.
- `ops/ScreenId.java` + `ops/MarineOpsContext.java` — the standalone route.
- Read-only use of `campaign/` stationing state; no writes.

## Out of scope

- Changing stationing mechanics, terms, or the incident payloads
  ([`campaign/contracts/`](../../campaign/contracts/) owns those, and
  G31/G32 are actively reshaping them — read that track's `next-session.md`
  before touching the term/deadline fields).
- Recalling a stationed team from this screen. That is a mutation with real
  contract consequences; if it is wanted, it belongs to the contracts
  track.

## Open questions

- Does `COMMITTED` belong in the model at all, or is it purely a rendering
  concern of the briefing flow? Modelling it keeps the chip logic in one
  place; keeping it out keeps the snapshot free of UI state. Leaning:
  model it, but pass the selection set in explicitly so the builder never
  reaches into a screen.
- Should the standalone view be its own `ScreenId` or a tab on the armory?
  The armory is already 1406 lines with two tabs; a third tab is cheap but
  the company view is arguably the *front page* of Marine Ops, not a tab
  inside the equipment screen.
