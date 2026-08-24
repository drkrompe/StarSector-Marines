# C5 — Battle HUD company rollup

> The battle HUD already drills squad → marine. Add the tier above it, and
> give the squads their real names.

Status: PLANNED
Written: 2026-08-22
Updated: 2026-08-23 — migrated under `company-view-nouns.md`.

Depends on the shipped deployment-identity law in `company-view-nouns.md`
and on `c2-formation-model.md` for the shared shape.

Read `company-view-nouns.md` before changing this story.

## Problem

`SquadOverviewPanel` lists every player squad as a 34px row: battle squad
id, alive/peak, alert dot, weapon summary, morale bar. `SquadDetailPanel`
swaps in on click with one row per marine. Both are good; both are keyed on
the ephemeral battle squad id.

What is missing:

- **Names.** `SQUAD 3` is not an object the player has a relationship with.
  `2nd Squad` is — especially once C6 (`c6-after-action-by-fireteam.md`)
  hands it back to them at debrief.
- **The tier above.** With six or seven squads on a Conquest map, the
  overview list is a wall of rows with no summary. There is no "how is my
  force doing" line anywhere in the battle.
- **Split landings can fragment the presentation.** Repeated lifts at one
  landing zone now join correctly, but the same campaign squad deployed at
  distinct zones deliberately becomes several battle squads. The HUD should
  explain those labelled fragments without pretending they are one tactical
  unit.
- **It has to hold twenty-plus squads.** The player may field a large
  organization, and the overview list is a fixed-row scroll today.

## Goal

Three levels in the HUD — company → squad → marine — sharing the
existing dock slot and selection model, with no new player agency.

## Design

### Company header row

A single band above the squad list: teams committed / teams still combat
effective, marines alive of landed, aggregate morale, and an alert mix
(how many teams are ENGAGED / SUSPICIOUS / UNAWARE). Every input already
exists on `Squad` — `aliveMembers`, `originalSize`, `morale`,
`moraleBroken`, `alertLevel` — and the four squad systems refresh them once
per tick.

This is the readout that tells the player, without reading seven rows,
that their force is degrading.

### Rows named by squad

Row label becomes the frozen `campaignLabel` when present, falling back to
today’s `SQUAD <id>` for anything without a campaign identity (militia,
walk-in reinforcements, employer forces). Squads sharing a squad id —
the split-landing case from the deployment-identity law — group under one entry with their `(A)`/`(B)`
suffixes visible on expansion.

### Density at scale

`SquadListViewport` already fits a whole number of fixed-height rows below
the header and scrolls the remainder — the right hook for the large case.
Add a compact row height once the list passes roughly a dozen squads
(drop the weapon summary, keep name + pips + alert dot + morale bar), and
make the scroll indicator state the total so a player with twenty-four
squads knows how many are below the fold. Grouping by officer is the real
answer at that size; the company band is what makes the collapsed state
useful.

### Selection stays view-only

`Selection` already carries squad id + optional pinned unit id, and the
panels already share one dock slot keyed off `hasSquadSelection()`. Add the
company level as the *collapsed* state of that same slot: company band →
click a team → `SquadDetailPanel` → back chip. No third panel, no modal
stack manager, and no order channel — the noun model keeps this surface
read-only.

### Non-player squads

Untouched. The overview panel already filters to the player's faction.

## Slices

1. **Company header band.** Aggregate line above the existing list.
   Independently valuable and does not need frozen identity.
2. **Named rows.** Consume the frozen campaign label with fallback.
3. **Split-team grouping.** Fold `(A)`/`(B)` squads under one entry.

## Acceptance

- The player can read force state from one line without scanning rows.
- Every row that came from campaign personnel shows its squad name;
  every other row is unchanged.
- Reinforcement waves that rejoin an existing squad do not create a
  second unrelated row.
- Twenty-four player squads render legibly: compacted, grouped, and with
  the off-screen count stated rather than silently clipped.
- No change to `Selection` semantics, no new input claims, no interference
  with `CommandPowerPanel`'s targeting flow (it is registered after
  `WorldPicker` specifically to claim clicks first — do not disturb that
  order).
- Panels keep snapshotting displayed values in `update()` rather than
  holding live entity refs: a member killed during the frame's `advance()`
  is released from the registry by render time, and reading its accessors
  then is fail-loud. `SquadDetailPanel` documents this pattern; the new
  band must follow it.
- No sim reads from render code beyond the existing snapshot pass.

## Files touched

- `battle/ui/panel/SquadOverviewPanel.java` — band + labels.
- `battle/ui/BattleHud.java` — if the band needs its own dock slot.
- `battle/squad/Squad.java` — read-only use of frozen campaign fields.

## Out of scope

- Orders, waypoints, target designation, or any write path from HUD to
  `Squad.assignedObjective`. That field is owned by the `MissionCommand`
  tier; a player order channel needs its own design against
  `ai-nouns.md` in the AI track.
- A minimap or objective panel (long-standing HUD backlog, unrelated).
- Changing the squad-selection highlight in the world.

## Open questions

- Should the band show the *commander's* current assignment per team
  (`AssignmentKind` — CLEAR_ZONE, SECURE_COMPOUND, HOLD_NODE…)? It is one
  field read and it would make the AI's intent legible, which is a real
  want. Risk: it reads as an order the player gave, on a surface that does
  not accept orders. Decide with the band on screen.
- Does the company band belong in the same dock as the squad list, or top-
  center where it is readable while the player is looking at the world?
