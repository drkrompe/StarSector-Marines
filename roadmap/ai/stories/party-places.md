# Party places

Status: IN PROGRESS

Written: 2026-10-21

Model: `garrisonables.md` (parties, place directives, locked positions). This
story is the implementation unit; the standing model lives there.

## Scope

1. **Parties as sides of the garrison system.** `ALLY` gets a faction picture of
   its own (`CommanderInfluenceService`, built from allied squads' believed
   contacts, only when allied combatants exist and the party switch is on). A
   story commander for any party hosts the shared planner (`PartyPlaces` over
   `MissionGarrisons`), offers its standing-duty squads for the place their post
   stands in, and its orders stay its own (issuer, authority, picture).
2. **Place directives and triggers** — the seam others consume (procedural
   counter-attacks will read `COUNTER_ATTACK` directives):
   - `PlaceTarget` — region (centre, radius); `contains`, places in it.
   - `PlaceTrigger` — sealed, records: `always`, `never`, `tickAtLeast(t)`,
     `activeFor(ticks)`, `stageAtLeast(name, v)`, `flag(name)`,
     `held(PlaceHold...)` (own target) / `heldAt(target, PlaceHold...)`,
     `lost()`, `pressureAtLeast(level)`, `anyOf`, `allOf`, `not`. Evaluated
     against `PlaceTrigger.Context`.
   - `PlaceSignals` — one pulse's context for one party: tick, the mission's
     disclosed stages and flags, and the party's own belief over a target (own
     squads in it, believed contacts in it, pressure on it).
   - `PlaceDirective` — id, party, kind (`HOLD`, `LOCK`, `COUNTER_ATTACK`),
     target, posts, radius, allowance, reason, mission key, `when`, `until`,
     re-arm.
   - `PlaceDirectives` — the book a commander holds: `evaluate(signals)` once a
     pulse (the lifecycle in the noun doc), `active(kind)` in activation order,
     transition log for traces and evidence.
3. **Locked positions.** `PlaceDirectives.lockFor(detachment)` — detachments
   fill active locks oldest first, surplus reinforces the newest; the order is
   the lock's defend area at its post (posts cycle by detachment) or the
   garrisonable's manning order. Squads on locks are never offered to reactive
   manning. Thunder Run's militia re-expressed on it, byte for byte.
4. **Wiring.** The Yard's Owners and Limited Warrant ally commanders host
   `PartyPlaces`: constables offered for the place they stand on, and hold
   directives triggered by the story's own signals. Thunder Run's ally on locks.

## Constraints

- Beats identical on every fixture where nothing the story counts moves;
  explain any deliberate change.
- `-Dbattle.party.places=false` is the control and must reproduce the previous
  battle (no allied picture, no manning, no hold directives).
- Unit tests under a second: trigger evaluation, lifecycle (one transition per
  pulse, re-arm, activation order), lock allocation, Thunder Run equivalence to
  the old militia targets.
- Scene `party-places`: an allied party takes and holds a locked position, and
  an allied party reactively holds a place under a trigger.
- Keep green: `story-defence`, `mission-defence`, `mission-envelopes`,
  `mission-flow`, `place-counterattack`, `machine-guard`, `sally-door`.

## Acceptance

- `commanderEvidence` for `yard-owners`, `limited-warrant`, `thunder-run`
  against the same-build control: fixtures, allied manning, beats, brawl share,
  defender and allied shots from a post, outcomes.
- The fight mix does not move toward the siege the law forbids.

## Plan

1. Docs (this story, the noun sections) to `main` first: the seam is named.
2. Directive core (`PlaceTarget`, `PlaceTrigger`, `PlaceSignals`,
   `PlaceDirective`, `PlaceDirectives`) with unit tests; to `main`.
3. Allied picture and `PartyPlaces`; Thunder Run on locks (equivalence test).
4. Yard's Owners and Limited Warrant wiring, evidence harness reads allied
   figures; scene; evidence; fold.
