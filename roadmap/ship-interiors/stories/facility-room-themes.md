# Facility room themes

Status: PLANNED

Written: 2026-08-26

Read `ship-interiors-nouns.md` before implementing this story. Depends on
`ship-deck-family.md`.

Fill mech bay and barracks compartments from parameters. The acceptance target is
already on screen: `MechLabSceneLayout` and `BarracksSceneLayout` are
hand-authored constant tables that produce genuinely good rooms. This story
reproduces them from a themed fill without their literal coordinates, and the
existing UI snapshots are the reference for whether it succeeded.

Start with the mech bay. It is the higher-information case — four identical
gantry bays make the parameter obvious, and the hazard-striped floor, service
bulkhead, and prop banding give a fill something specific to earn.

## Scope

- A themed fill per facility purpose, selected from the compartment's semantic
  purpose, that places fixtures from parameters: gantry count and spacing for a
  mech bay; berth count, lounge extent, and firing lane count for a barracks.
- Fixtures declare their tactical effect and their ambient affordance. Affordance
  is authored here and consumed by `fixture-derived-ambient-routes.md`.
- Compartment extent follows from its parameters. A four-gantry bay and a
  six-gantry bay are different sizes, not the same room with more clutter.

## Constraints

- Fixtures use existing registry doodad ids. This story adds no art.
- Decorative placement cannot silently alter topology; anything that blocks,
  covers, or breaks a sightline declares it.
- Capacity is not duplicated. The fixture count is the capacity; do not introduce
  a parallel number alongside it.

## Acceptance

- A mech bay generated at four gantries is tactically equivalent to today's
  hand-authored Mech Lab: same gantry count and service access, comparable cover
  and sightlines, comparable prop density. Judge against `mech-lab-wide.png` in
  the `ui` snapshot suite.
- A barracks generated at twelve berths and three lanes is likewise equivalent to
  today's hand-authored room, including the blast wall separating the range from
  the commons. Judge against `barracks-wide.png`.
- Changing a parameter changes both the fixture count and the compartment extent,
  and the result stays connected and deployable.
- A deterministic seed sweep produces no room whose fixtures block its own
  circulation or seal a hatch.
- Neither `MechLabSceneLayout` nor `BarracksSceneLayout` is consulted by the
  fill. They remain in place as the comparison reference until
  `flagship-deck-adoption.md` retires them.

## Out of scope

Ambient routes, adoption by the operations screens, and any facility beyond mech
bay and barracks. Armory and medical themes follow the same shape once these two
prove it.
