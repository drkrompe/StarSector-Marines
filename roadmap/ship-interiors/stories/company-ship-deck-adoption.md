# Company ship deck adoption

Status: PROPOSED

Written: 2026-08-26

Updated: 2026-08-27 — renamed off the flagship framing. The company lives on a
ship the player designates, which need not be the ship they fly. Read
`company-ship.md` for where the deck's parameters come from.

Read `ship-interiors-nouns.md` before implementing this story. The crew model it
puts on the deck is in `ai-nouns.md`.

Retire the two constant scene layouts and host Barracks and Mech Lab on the
company ship's generated deck. Today each screen owns a private grid with its
own origin, its own wall predicate, and no relationship to the other; the ship
exists only as a breadcrumb string. After this story the two screens are room
views onto compartments of one ship, and the breadcrumbs describe something
real.

## Scope

- A company ship deck generated from that ship's effective crew, cargo and hull
  class, held rather than rebuilt per screen entry.
- Barracks and Mech Lab screens frame their compartment on that deck instead of
  constructing a private scene.
- Delete `BarracksSceneLayout` and `MechLabSceneLayout` and the constant tables
  they carry.

## Constraints

- Camera framing is viewport work at the established battle camera seam. Each
  screen frames its own compartment; a larger deck must not become a renderer
  concern or a second camera model.
- Headless evidence keeps collecting the same bounded battle-simulation commands
  as the live views, substituting only the final drain.
- The screens keep their current interaction and layout authority. This story
  changes the place they depict, not the surface.
- **A screen that seeks rather than advances will draw people through walls.**
  The Mech Lab freezes time and samples poses, and that sampler interpolates
  between stops in a straight line — deliberately, since it exists to make a
  picture rather than to simulate. On the two hand-authored layouts the straight
  lines are clear by construction; on a generated deck they are not, and a shift
  that reaches the mess crosses several bulkheads to get there. Moving these
  screens onto the deck is exactly what turns a documented bound into a visible
  defect, so decide it here: either these screens advance the bounded simulation
  the way Barracks already does, or the sampler walks stops rather than lines.

## Acceptance

- Barracks and Mech Lab UI snapshots show compartments that are *better filled*
  than the current `barracks-wide.png` and `mech-lab-wide.png`, not merely as
  good: fixtures grouped rather than sprinkled, no unargued expanse of open deck,
  and legible circulation. Framing stays readable and clutter never obscures the
  actors. A merely connected room is a regression, and so is a faithful
  reproduction of the current fill.
- Both screens read compartments of the same deck, and their breadcrumbs derive
  from deck and compartment facts rather than literal strings.
- The practice range still fires live rounds; the Mech Lab still hosts its
  gantries and fitting interaction unchanged.
- Low-resolution and 150% UI-scale snapshot variants still frame correctly.
- No constant layout table remains for either room.

## Out of scope

Upgrades, capacity consumption, and any facility beyond the two rooms that exist
today. Adding armory or medical compartments to the company ship is separate work
once those themes exist.
