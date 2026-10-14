# Fighting-position occupation

Status: IN PROGRESS — slices 1–4 built (slice 3 for fort belts); slice 5 waits on art

Written: 2026-10-13

Updated: 2026-10-13 — occupy order, overlay, evidence counts, belt-post structure defence and belt-nest garrisons built; on the fort control 20 belt squads (120 defenders) hold their trenches dug in, drawn from the patrol pool. Ordinary compound garrisons are unchanged.

## Intent

Fighting positions exist and a defend-area squad fills them (see the fighting
positions section of `ai-nouns.md` and prepared ground in `mapgen-nouns.md`). What is missing is everything
around that core: the player cannot see a position or aim an order at one
directly, nothing on the defending side uses them, generated maps have never
been checked for them, and the fort's own earthworks cannot be destroyed.
Balance is explicitly not a goal while the feature is being built.

## Slices

### 1. Occupy order

A selected infantry squad's ordinary ground click that lands on a
fighting-position cell becomes an **occupy** order: the existing defend-area
context, centred on that run near the click, so the claims fill the position
with no new execution path. It is contextual in the same way a click inside an
uncaptured compound already becomes secure-compound. The order plate and order
readout name it as occupying a position. A click off prepared ground is
unchanged.

### 2. Seeing positions

In the battle view: fighting-position cells are drawn as a subtle ground
overlay while a friendly infantry squad is selected or an order is being aimed,
claimed cells for the selection are distinguished, and a dug-in marine carries a
small marker. Presentation observes; it never decides. Preview at 1920x1080 and
100% UI scale.

### 3. Defenders use positions

A defending squad whose held ground (garrison node, defend site, defend area)
contains fighting-position cells occupies them through the same claims rather
than standing on ordinary floor beside a trench. Commanders gain no new
authority: the claim is a manner of holding ground already assigned.

### 4. Generated-map check

Commander evidence reports fighting-position run and cell counts per fixture, so
a fort-bearing Conquest map is shown to produce positions and a change that
drops them is visible.

### 5. Destructible fort earthworks

Fort sandbags (and trench parapets, if they are to be breakable) gain damaged
and destroyed states through the ordinary cover-prop damage families, so fort
positions shrink under fire like plain sandbag lines. New art follows the
geometry-first workflow in `art-source/geometry-first-imagegen.md`; this slice
waits on that art.

## Acceptance

- A click on a trench with a squad selected fills it; a click beside it is an
  ordinary move.
- Positions, claims and dug-in marines are visible in the battle view.
- Defenders holding ground with a trench in it stand in the trench.
- Conquest evidence lists non-zero position runs on fort maps.
- Shelling a fort belt removes cells from its positions.
