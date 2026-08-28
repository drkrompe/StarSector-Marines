# Company ship deck adoption

Status: IN PROGRESS

Written: 2026-08-26

Updated: 2026-08-27 — both screens are room views on the company ship, both
constant layout tables are deleted, and the UI snapshots photograph the real
generated deck. What remains is deriving the room breadcrumbs from deck and
compartment facts instead of literal strings. Read `company-ship.md` for where
the deck's parameters come from.

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
- **The ship runs; a screen is a camera.** The whole deck is manned and
  advanced, not the compartment somebody is framing. The Mech Lab therefore
  advances, as Barracks already does, and stops seeking: the pose sampler
  interpolates between stops in a straight line, which is a deliberate collision
  bypass for making a picture and which on a generated deck crosses bulkheads.
  Framing must not decide what exists, or the technicians a player walks away
  from stop working and the corridor between two rooms is empty because traffic
  in a passage is what the compartments at both ends of it produce. This is also
  the cheaper half: a fully manned capital transport — 107 compartments, 265
  hands — costs about 0.75 ms a frame, so there is no performance argument for
  simulating less ship than exists.

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
