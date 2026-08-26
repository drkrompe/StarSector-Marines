# Flagship deck adoption

Status: PROPOSED

Written: 2026-08-26

Read `ship-interiors-nouns.md` before implementing this story. Depends on
`fixture-derived-ambient-routes.md`.

Retire the two constant scene layouts and host Barracks and Mech Lab on generated
flagship decks. Today each screen owns a private grid with its own origin, its
own wall predicate, and no relationship to the other; the ship exists only as a
breadcrumb string. After this story the two screens are views onto compartments
of one flagship, and the breadcrumbs describe something real.

## Scope

- A flagship deck set generated from the company's owned facilities, held for the
  campaign's duration rather than rebuilt per screen entry.
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

## Acceptance

- Barracks and Mech Lab UI snapshots remain at least as legible as the current
  `barracks-wide.png` and `mech-lab-wide.png`: comparable framing, readable
  fixture density, no empty dead space and no clutter that obscures the actors.
  A merely connected room is a regression.
- Both screens read compartments of the same deck set, and their breadcrumbs
  derive from deck and compartment facts rather than literal strings.
- The practice range still fires live rounds; the Mech Lab still hosts its
  gantries and fitting interaction unchanged.
- Low-resolution and 150% UI-scale snapshot variants still frame correctly.
- No constant layout table remains for either room.

## Out of scope

Upgrades, capacity consumption, and any facility beyond the two rooms that exist
today. Adding armory or medical compartments to the flagship is separate work
once those themes exist.
