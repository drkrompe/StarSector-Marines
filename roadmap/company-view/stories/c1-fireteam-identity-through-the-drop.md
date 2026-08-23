# C1 — Squad identity through the drop seam

> A battle squad is currently "whoever rode this dropship." Make it able to
> name the campaign squad it came from.

**Status:** not started. No dependencies. Enabling slice for
[C5](c5-battle-hud-company-rollup.md) and
[C6](c6-after-action-by-fireteam.md).

## Problem

The campaign→battle handoff preserves *individual* identity and destroys
*organizational* identity:

- `CampaignMarineDeployment.freeze` walks the selected `MarineSquad`s,
  collects their ACTIVE members into one flat `List<MarineSoldier>`, and
  emits a flat `List<MarineLoadout>` seat list. The squad boundary is
  gone at that line.
- `MarineLoadout` carries `campaignSoldierId` (good) and no squad id.
- `CampaignMarineDeployment.applyTo` pours those seats into
  `ShuttleMission.cycleLoadouts` in index order, skipping employer-owned
  missions.
- `InfantryPayload.tryDeploy` mints one battle `Squad` per shuttle mission
  (`mission.squadId == Squad.NO_SQUAD` guard) on the first deboard; every
  marine off that sortie joins it.

Two consequences:

1. Nothing downstream — HUD, debrief, comms, telemetry — can say "1st
   Squad". It can only say `SQUAD 3`.
2. The grouping is arbitrary. `ShuttleType.AEROSHUTTLE` and `KITE` carry
   **4** seats against a `MarineSquad.CAPACITY` of **6**, so even a single
   selected squad is split across lifts, and two half-teams routinely
   land as one battle squad.

## Goal

A battle `Squad` spawned from player personnel knows which campaign
squad it is, and carries a display label. No AI behavior changes.

## Design

### Carry an id, not a reference

`battle/` stays campaign-free. Two plain fields cross the seam:

- `MarineLoadout` gains `campaignSquadId` (String, null for generated
  employer/defender personnel) and `squadLabel` (String, the display
  name frozen at deploy time — the battle tier must never look a name up).
- `Squad` gains the same two fields, defaulted to null / `NO_SQUAD`-style
  absent, so every existing spawn path (defenders, militia, drones, mechs,
  debug fixtures) is untouched and the HUD falls back to today's
  `SQUAD <id>`.

`CampaignMarineDeployment.merge` already rebuilds a loadout from
`(scenario, allocation)` — the two new fields ride the allocation side with
the rest of the campaign-owned data.

### Seat the manifest squad-contiguously

`freeze` currently appends members in selection-set iteration order, which
is already squad-major by accident. Make it deliberate and stable:

- Iterate selected squads in roster order (not `Set` order — today's
  iteration is over a `Set<String>`, so seat assignment is not
  deterministic across runs).
- Keep each squad's members adjacent, and pack **whole fire teams** into
  lifts. With [C8](c8-lift-capacity-and-multi-pass-drops.md)'s
  team-denominated capacities (4 / 8 / 12) that always divides cleanly, so
  a lift never carries three-quarters of a team; a twelve-marine squad
  simply spans one to three lifts.

### Mint by squad, per landing zone

Replace the "one squad per shuttle mission" rule for player marines with
"one battle squad per (campaign squad, landing zone)":

- `InfantryPayload.tryDeploy` looks up an existing battle squad for the
  seat's `campaignSquadId` at this LZ before minting.
- Seats with no squad id keep the current per-mission minting exactly.
- Members arriving on a later lift to the same LZ join the existing squad
  and bump `originalSize`, so morale ratios stay honest.
- A team that lands at two different LZs becomes two squads, labelled
  `2nd Squad (A)` / `(B)`. That keeps leader-pull cohesion from dragging
  members across the map between landings — the alternative (one squad
  spanning LZs) is a known cohesion hazard.

**Decided (2026-08-22): a split squad stays one squad.**
[C8](c8-lift-capacity-and-multi-pass-drops.md) denominates lift capacity in
four-marine fire teams, so a twelve-marine squad normally arrives across
one to three passes — only the heaviest transport lands it intact. Later
arrivals **join the existing squad and catch up** rather than forming a
second unit, and the squad holds at its LZ until it has formed up. The
`(A)`/`(B)` labelling survives only for the genuinely-two-landing-zones
case. Note `AirSystem` currently resets
`mission.squadId` on each cycle (`AirSystem.java:471`), so today every wave
mints a new squad; scoping that reset is C8's slice 2.

Note `originalSize` is currently incremented per deboard, and morale caps
on `aliveMembers / originalSize`; joining a second lift into an existing
squad must keep that increment, not reset it.

### Where the label comes from

`MarineSquad.name()` at freeze time. Frozen, not live — a rename mid-battle
must not change what the HUD says, and the battle tier has no roster access
to re-read it anyway.

## Slices

1. **Fields + freeze.** Add the two fields to `MarineLoadout`, populate in
   `freeze`/`merge`, make squad iteration deterministic (roster order).
   No consumer yet. Pure plumbing; verifiable by test.
2. **Squad-keyed minting.** `Squad` fields + the `(campaign squad, LZ)` lookup
   in `InfantryPayload`. Fallback path for null ids unchanged.
3. **Split labelling.** `(A)` / `(B)` suffixes when one squad lands at
   more than one LZ.

## Acceptance

- Deploying two squads into a 3-lift manifest produces battle squads
  whose members all share one `campaignSquadId`, with no mixed squads
  at a single LZ.
- A player marine spawned outside the campaign path (debug fixture,
  `MarineInsertion` power, walk-in reinforcement) still spawns cleanly with
  a null squad id and today's behavior.
- Employer/militia/defender spawns are byte-identical in behavior — the
  `applyTo` employer-mission skip still holds.
- No change to any AI decision: alert, morale, fallback, GOAP, and the
  commanders read the same squad state they read today.
- Deterministic across runs: same roster + same selection ⇒ same seat
  order ⇒ same squad composition.

## Tests

- `freeze` with two selected squads: seats are squad-contiguous and
  ordered by roster position, not `Set` iteration order.
- `applyTo` with an employer mission first: skip count still correct once
  the new fields exist.
- Deboard simulation: one squad across three lifts to one LZ ⇒ one squad,
  `originalSize == 12`; across two LZs ⇒ two squads.
- Mixed manifest: player squads + generated militia in the same battle,
  militia squads unaffected.

## Files touched

- `battle/infantry/MarineLoadout.java` — two fields, constructor overloads.
- `ops/detachment/CampaignMarineDeployment.java` — `freeze`, `merge`,
  deterministic squad iteration.
- `battle/squad/Squad.java` — two fields (lifecycle-documented per the
  field-doc convention in that file).
- `battle/air/InfantryPayload.java` — squad-keyed squad lookup.
- `battle/air/ShuttleMission.java` / `AirDeliveryContext` — likely a
  per-LZ squad→squadId map lives here rather than on the mission.

## Out of scope

- Showing the label anywhere. C5 and C6 consume it.
- Changing shuttle capacities or manifest resolution to fit 6-marine teams
  (`DetachmentResolver` owns that, and it is a balance question, not a
  legibility one). Worth noting in the roadmap backlog if the split case
  turns out to be the common case in play.
- Persisting battle squads. Battles remain transient.

## Open questions

- Should `MarineInsertion` (the reinforcement command power) draw from
  named reserve personnel and therefore carry a squad id too? It
  currently spawns generic marines. Deferring, but it is the one
  player-side spawn path that would look wrong once everything else has a
  name.
