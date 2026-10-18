"""Shepherd main structure: the central crew body, the spine, the aft
crossbeam and the aft machinery block between the drive pods.

The sprite (drone_tender.png) is almost a square block: a sand-tan central
body running the whole length, the drone bay down the port side, the repair
gantry down the starboard side, and one wide crossbeam across the stern that
carries both drive pods. The central body is where the crew lives ("internal
volume given to crew comfort"), so it is the deepest section.

Names: 'Hull ...'. Pressure hull for the scale is the central body, the
spine and the aft machinery block (build_shepherd.PRESSURE_HULL).
"""
from sh_kit import prism, section, boolean, plane_cut

# Planform of the central body (CCW, +X bow, +Y port), read off the sprite:
# the tan body is Y -10..+5 forward of the crossbeam, widening aft into the
# machinery between the pods; the bow tapers to the bridge cab.
BODY_PLAN = [(-31, -11), (-12, -11), (6, -11), (31, -11), (34.5, -7.5), (34.5, 5),
             (27, 6), (6, 6), (-12, 9), (-31, 9)]
BODY_KEEL, BODY_DECK = -9.0, 5.0
SPINE_PLAN = [(-14, 5), (36, 5), (37, 6.5), (37, 11), (-14, 11)]
# Its outer ends run aft past the pods to carry the jet clusters (sprite X -24..-29).
CROSSBEAM_PLAN = [(-29, -30.5), (-23, -31.5), (-9, -31.5), (-9, 31.5), (-23, 31.5), (-29, 30.5)]
MACHINERY_PLAN = [(-31.6, -13), (-14, -13), (-14, 13), (-31.6, 13)]  # proud of the body's aft face


def build(kit):
    body = prism(kit, 'Hull central body', BODY_PLAN, section(BODY_KEEL, BODY_DECK, 2.5, 2.0), kit.tan)
    # Bounded bow facets: the cab's forward faces rake down to the keel.
    plane_cut(body, 'bow brow', (30.5, 0, 5), (1, 0, 1))
    plane_cut(body, 'bow chin', (34.5, 0, -4), (5, 0, -6))
    kit.hull.append(body)

    spine = prism(kit, 'Hull port spine', SPINE_PLAN, section(-4, 8, 1.5, 1.2), kit.tan)
    kit.hull.append(spine)

    beam = prism(kit, 'Hull aft crossbeam', CROSSBEAM_PLAN, section(-4, 4, 1.5, 1.5), kit.tan)
    kit.hull.append(beam)

    mach = prism(kit, 'Hull aft machinery block', MACHINERY_PLAN, section(-8, 3, 2.0, 1.5), kit.steel)
    kit.hull.append(mach)
    # Its aft corners step back beside the pods (sprite rows X -31..-33).
    for side, s in (('port', 1), ('starboard', -1)):
        kit.box(f'Hull aft machinery {side} quarter block', (-32, s*10.5, -1.5), (3, 5, 7), kit.steel, .15)
    # The paired dark intakes the sprite shows on the machinery deck (X -18, Y 0..-6).
    for i, y in enumerate((1.5, -4.5), 1):
        cut = kit.cylinder(f'Temporary intake {i} cutter', (-18, y, 3), 2.6, 3, kit.dark, 'Z', 16, 0)
        boolean(mach, cut)
        kit.cylinder(f'Hull aft machinery intake {i} grille', (-18, y, 1.6), 2.6, .4, kit.dark, 'Z', 16, 0)
    # Stern running-light posts where the sprite's red (starboard) and green
    # (port) lamps sit at the aft edge of the machinery block.
    for name, y, mat in (('port green', 8.5, kit.cream), ('starboard red', -8.0, kit.cream)):
        kit.box(f'Hull stern lamp post {name}', (-31.5, y, 1), (1.2, 3, 2), mat, .1)
