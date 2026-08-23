# C3 — Company card stack (fleet view)

> Replace the two-column checkbox list with the thing the player actually
> has: a company, made of squads, made of marines.

**Status:** not started. Depends on C2 (`c2-formation-model.md`).

## Problem

`SquadDeploymentScreen` is 140 lines of flat list: `[X] Name  n/6 RTD`
plus a WIA/MIA/KIA string, two columns, no captain, no individuals, no
sense of an organization. It is also the *only* place the player sees their
squads as squads — `ArmoryScreen` organizes by marine and by
inventory, and `StationingScreen` by contract.

The result is that a company of seven teams reads as fourteen unlabelled
list rows, and the player cannot see the shape of their force.

## Goal

A card stack: a company band, one card per formation, each card made of
squad rows, each squad expandable to its marines. Same selection
semantics as today — this is a presentation replacement, not a rules
change.

## Design

### Three levels, one screen

> Language note: C7 (`c7-organization-and-ranks.md`) settles the unit as a
> **twelve-marine squad** of three four-marine fire teams, led by an NCO,
> and the card as **one officer's command**. The screens say "fireteam"
> today; that word moves to the four-marine element, and the row the player
> sees is a squad row. The class has always been `MarineSquad`; only the UI
> string changes. Fire teams are not a card level
> (C9 (`c9-fire-teams-as-the-maneuver-element.md`)); they show up only as
> pip grouping.

**Company band (top).** Strength / ready / wounded / recovering, squads
deployable vs. rank cap, seats required vs. filled for this sortie. This is
the `READY SEATS … COMPANY … SHORT … FIRETEAMS n/cap` line that exists
today, promoted from a single label into a real header with bars.

**Formation card (per captain command).** Captain name + rank + status +
portrait thumb, the command's own rollup, and its squad rows. Cards
stack vertically in a scroll region. Unassigned squads get a card with
no captain; the reserve pool gets a distinct, visually quieter band rather
than a card (it is a holding pen, not a formation).

**Squad row (inside a card).** Name, twelve member pips coloured by status
(ACTIVE / WIA / MIA / KIA / vacant) and grouped 4 / 4 / 4 so a gutted fire
team is visible at a glance, a readiness bar, a whereabouts chip
(C4 (`c4-whereabouts-and-deployed-state.md`)), and the selection toggle with
today's `CaptainDeploymentPolicy` gate and `COMMAND LIMIT` state. The pips
are the density win: six glyphs say what `4/6 RTD  1 WIA  1 KIA` says, at a
glance, in less space.

**Marine rows (expanded).** Click a squad row to expand it in place:
name, status (with return day when recovering), and the kit/quality marks
that progression [S8](../../progression/stories/s8-roster-legibility.md)
defines. **Leave S8's row design to S8** — C3 reserves the space and owns
the expand/collapse, not the contents of the leaf.

### Reuse the widget vocabulary

The mod already has this pattern working in the comms console:
`DossierCardWidget` (clickable record card) → `ExpandedCardWidget` (frame
whose sub-controls are added flat into the shared tree at known positions
inside the card rect) → `ScrollRegionWidget` (scroll capture behind the
content) → `SelectableRowWidget` / `CaptainRowWidget` (row with hover +
selected tints and a left accent) → `StatBarWidget`, `SpriteThumbWidget`.

Build the squad card on that vocabulary rather than a new container
model. Keep the flat widget tree — it is a deliberate constraint of
`WidgetRoot`, and `ExpandedCardWidget` documents why.

### Density tiers — the view must survive a big company

A card per squad stops working somewhere past twenty squads. The player is
allowed to field that many; the UI has to degrade gracefully rather than
forbid it.

| Squads shown | Treatment |
| --- | --- |
| ≤ 8 | Full cards, expandable to marines. |
| 9–20 | Compact rows — pips + readiness bar + chip, expansion on demand only. |
| > 20 | Paginate (or virtualize) within the formation grouping, with a page/scroll indicator that states the total. |

The grouping is what makes the large case readable: twenty-four squads
under four officers is four cards, not twenty-four rows. Never let the list
length drive the screen height, and never silently truncate — if rows are
off-page, the count says so.

### Typography and density

Per the overview's commitment and S8's measured table: header
`orbitron20aa`, body `insignia17LTaa`, dense squad rows
`insignia15LTaa`, all numeric columns `arial14` (the only vanilla face with
tabular digits — Orbitron's narrow `1` makes columns wobble). Bars and pips
before text. Verify at UI scale 1.0x / 1.25x / 1.5x, not one unstated
setting.

### Where it lives

Extend `SquadDeploymentScreen` rather than adding a screen shell — it is
already routed from the briefing and already owns the selection state on
`MarineOpsContext`. C4 (`c4-whereabouts-and-deployed-state.md`) then adds
the non-mission entry point so the same stack is readable outside a
pre-battle flow.

## Slices

1. **Company band.** Promote the readiness line into a header with bars.
   Cheap, independently valuable, exercises C2's snapshot.
2. **Squad rows with pips.** Replace the checkbox rows; selection
   semantics unchanged.
3. **Formation cards.** Group rows under captain cards; scroll region.
4. **Expand to marines.** In-place expansion with a placeholder leaf row
   until S8 lands.

## Acceptance

- Every selection behavior of today's screen is preserved: whole-squad
  toggle, rank-capped `COMMAND LIMIT` block, reserve exclusion, the two
  navigation buttons.
- A seven-squad company is readable without scrolling past the fold at
  1920×1080, and scrolls cleanly at smaller viewports.
- A twenty-four-squad organization is still navigable: grouped, compacted,
  and honest about what is off-page.
- Member state is readable from the collapsed row — the player does not
  have to expand a team to learn it lost someone.
- Numeric columns align between rows.
- Sprite handling per the shipped pattern: cache the `SpriteAPI` per screen
  and load the texture before measuring.
- GL bracket discipline around every direct draw.
- In-game feel pass. Layout density is not testable from source.

## Out of scope

- The contents of the marine leaf row (S8).
- Moving marines between teams — `ArmoryScreen` owns transfers; the card
  links to it.
- Renaming squads from the card (roster supports it; not this story).
- Orders of any kind.

## Open questions

- Does the expanded state persist across screen navigation? Probably yes,
  on `MarineOpsContext` beside the selection set, so returning from the
  armory does not collapse everything.
