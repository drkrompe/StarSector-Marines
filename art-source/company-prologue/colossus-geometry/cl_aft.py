"""Colossus waist and aft body: the keel spine, the aft machinery body and the open lattice frames.

The keel spine carries the cargo body's loads aft through the narrow waist
(X -18..-33) into the aft machinery body, which stands between the drive pods
and carries the bridge deckhouse (cl_bridge). Open lattice trusses tie the
aft side modules and the drive pods to the spine. Planform from colossus.png;
heights proposed. Symmetric about cl_hull.YC.

Pass 2 detail, all read off colossus.png at 8-13x:
- the spine's top is a ladder: two pale rails at Y rel +-7 over a dark
  recessed well, bridged by rungs, with dark slot pockets in the outer channels;
- each truss is an open space frame (chords, verticals, Warren lacing) with a
  dark walkway grating at mid-depth and a pipe run on it, so the plan stays
  as opaque as the sprite's dark lattice while every oblique view sees through;
- the aft body is segmented armour: three transverse seam rings, a strata
  groove round the walls, two longitudinal deck seams, bolted service hatches
  and louvred vents on the outboard shelves;
- pale ribs across the striped band forward of the bridge (X -33..-41);
- the stern below the bridge: two pale equipment housings, the dark radiator
  grille (fore-aft fins in a recessed well, continued as vertical slats down
  the aft faces), a bolted centre hatch and the small tail block (X -86..-93.5,
  Y rel +-5.5) with the two stern lamps the sprite's amber dots suggest.

The scale solids ('Hull keel spine', 'Hull aft machinery body') stay closed:
their seams and wells are shallow MANIFOLD grooves, nothing is hollowed.
"""
import math
import bmesh
import bpy
from mathutils import Vector
from cl_kit import prism, section, inset, boolean, material
from cl_hull import AFT_KEEL, AFT_DECK, AFT_BAY_COLS, SIDES, YC, plan, y

AFT_HALF = [(-30, 34), (-80, 35), (-84, 27), (-87, 16), (-90, 9), (-92, 5)]
SEAM = .6       # seam groove width and depth


# --- small geometry helpers (used by cl_bridge too): the shared kit's ---
from ship_detail_kit import beam, block, solid, seat, lamp, bolted_plate, louvre_vent, space_frame, pipes


# --- shallow grooves in the closed scale solids ---

def _groove_box(kit, target, lo, hi, groove_mat):
    v, f = [], []
    block(v, f, lo, hi)
    cutter = solid(kit, 'Temporary groove cutter', v, f, groove_mat)
    boolean(target, cutter)


def _seam_shell(kit, slab_lo, slab_hi, half, keel, deck, keel_c, shoulder_c, groove_mat):
    """A thin cutter: the slab minus the solid offset SEAM inward, so only a skin-deep band remains."""
    v, f = [], []
    block(v, f, slab_lo, slab_hi)
    shell = solid(kit, 'Temporary seam shell', v, f, groove_mat)
    inner = prism(kit, 'Temporary seam core', inset(plan(half), SEAM),
                  section(keel+SEAM, deck-SEAM, keel_c, shoulder_c), groove_mat, bevel=0)
    boolean(shell, inner)
    return shell


def build(kit):
    groove = material('Seam groove shadow', (.05, .045, .045), .4, .8)
    for side, s in SIDES:
        # Waist truss: open space frame tying the aft side module to the spine and the pod.
        frame, zm = space_frame(kit, f'Frame {side} waist truss', -35, -19, *sorted((y(20, s), y(48, s))), -5, 5, 4, 6)
        lo, hi = sorted((y(20.5, s), y(47.5, s)))
        pipes(kit, f'Frame {side} waist truss pipe run',
              [((-22.5, lo, zm+1.1), (-22.5, hi, zm+1.1)), ((-25, lo, zm+1.1), (-25, hi, zm+1.1)),
               ((-28.5, lo, zm+.9), (-28.5, hi, zm+.9))], .8, kit.pale)
        # Pod truss: a long lattice girder between the aft body's flank and the drive pod (Y rel 35..45).
        frame, zm = space_frame(kit, f'Frame {side} pod truss', -72, -32, *sorted((y(34.5, s), y(45, s))), -7, 7, 8, 2)
        lo, hi = sorted((y(37, s), y(42.5, s)))
        pipes(kit, f'Frame {side} pod truss pipe run',
              [((-72, y(38.2, s), zm+1.1), (-32, y(38.2, s), zm+1.1)),
               ((-72, y(40.6, s), zm+1.1), (-32, y(40.6, s), zm+1.1)),
               ((-72, y(42.4, s), zm+.8), (-32, y(42.4, s), zm+.8))], .75, kit.pale)
        # Pod collar truss: the short frame between the aft side module's tail and the pod's nose,
        # stopping at the hull lip round the module's aft container bays (pass 3 round 2).
        space_frame(kit, f'Frame {side} pod collar truss', -29, -20.3, *sorted((y(50, s), y(62, s))), -5, 3, 2, 3)
        # Thrust yoke (owner, pass 3 round 1: the pods push the cargo block like tugs). Heavy
        # struts run from the pod's nose and keel and converge on a bearing shoe on the cell-guide
        # post the module's aft container bays leave for it (cl_hull.AFT_BAY_COLS[1]), so the push
        # lands on the hull's frame rather than on the stacked containers.
        post = AFT_BAY_COLS[1]
        v, f = [], []
        for r0, r1 in ((53.0, post-.6), (61.0, post+.6)):
            beam(v, f, (-33.5, y(r0, s), -4), (-22.2, y(r1, s), -12), 2.2, 2.2, (0, 1, 0))     # nose struts
            beam(v, f, (-38.0, y(r0, s), -11), (-22.2, y(r1, s), -30), 2.0, 2.0, (0, 1, 0))    # keel struts
        beam(v, f, (-27.0, y(53.6, s), -8.5), (-27.0, y(60.6, s), -8.5), 1.4, 1.4, (1, 0, 0))   # cross ties
        beam(v, f, (-29.0, y(54.0, s), -21.0), (-29.0, y(60.2, s), -21.0), 1.4, 1.4, (1, 0, 0))
        solid(kit, f'Frame {side} thrust yoke', v, f, kit.steel, bevel=.15)
        v, f = [], []
        block(v, f, (-22.4, min(y(post-1.5, s), y(post+1.5, s)), -34), (-20.0, max(y(post-1.5, s), y(post+1.5, s)), -6))
        solid(kit, f'Frame {side} thrust pad', v, f, kit.dark, bevel=.3)
        v, f = [], []
        for r in (post-1.1, post+1.1):
            for zb in (-32.0, -26.0, -20.0, -14.0, -8.0):
                block(v, f, (-22.8, y(r, s)-.25, zb-.25), (-22.4, y(r, s)+.25, zb+.25))
        solid(kit, f'Frame {side} thrust pad bolts', v, f, kit.pale)

    # --- keel spine: the waist's ladder ---
    spine = prism(kit, 'Hull keel spine', plan([(-12, 20), (-46, 20)]), section(-15, 12, 4, 3), kit.red)
    _groove_box(kit, spine, (-29.6, y(-5.2, 1), 10.4), (-19.4, y(5.2, 1), 13), groove)      # central well
    for side, s in SIDES:
        a, b = sorted((y(10.6, s), y(14.4, s)))
        for x0, x1 in ((-28.6, -24.6), (-23.6, -19.6)):
            _groove_box(kit, spine, (x0, a, 11), (x1, b, 13), groove)                    # slot pockets
    v, f = [], []
    for side, s in SIDES:
        a, b = sorted((y(6, s), y(8.6, s)))
        block(v, f, (-33.5, a, 11.6), (-19.2, b, 13.4))                                  # ladder rails
    for k in range(5):
        x = -20.6-k*2.2
        block(v, f, (x-.45, y(-6.2, 1), 11.3), (x+.45, y(6.2, 1), 12.2))                 # rungs
    solid(kit, 'Hull keel spine ladder rails', v, f, kit.pale, bevel=.08)
    v, f = [], []
    block(v, f, (-29.5, y(-5.1, 1), 10.4), (-19.5, y(5.1, 1), 10.6))
    solid(kit, 'Hull keel spine well floor', v, f, kit.dark)
    v, f = [], []
    for side, s in SIDES:                                                                 # cross struts to the knees
        for x in (-21.5, -27.5):
            beam(v, f, (x, y(19.5, s), 6), (x, y(27, s), 2), .9, .9, (1, 0, 0))
    solid(kit, 'Hull keel spine cross struts', v, f, kit.steel)

    # --- aft machinery body: segmented armour ---
    aft = prism(kit, 'Hull aft machinery body', plan(AFT_HALF), section(AFT_KEEL, AFT_DECK, 5, 4), kit.red)
    for xs in (-41.5, -58.0, -77.5):                                                       # transverse seam rings
        boolean(aft, _seam_shell(kit, (xs-SEAM/2, -100, -40), (xs+SEAM/2, 100, 40), AFT_HALF,
                                 AFT_KEEL, AFT_DECK, 5, 4, groove))
    boolean(aft, _seam_shell(kit, (-120, -100, -3.3), (0, 100, -2.7), AFT_HALF,
                             AFT_KEEL, AFT_DECK, 5, 4, groove))                            # strata groove
    for side, s in SIDES:
        a, b = sorted((y(26.2, s), y(26.8, s)))
        _groove_box(kit, aft, (-75.5, a, AFT_DECK-SEAM), (-35, b, AFT_DECK+1), groove)     # deck seams
        a, b = sorted((y(4.8, s), y(13.6, s)))
        _groove_box(kit, aft, (-86.5, a, AFT_DECK-1.6), (-78.2, b, AFT_DECK+2), groove)     # grille well
        a, b = sorted((y(13.2, s), y(13.8, s)))
        _groove_box(kit, aft, (-84, a, AFT_KEEL-1), (-34, b, AFT_KEEL+SEAM), groove)       # keel seams
    kit.hull += [spine, aft]

    # Striped band forward of the bridge: pale ribs on the red deck (X -33..-41).
    v, f = [], []
    for x in (-33.2, -35.2, -37.2, -39.2, -41.2):
        z = seat(kit, x, YC+9, AFT_DECK)
        block(v, f, (x-.55, y(-20, 1), z-1.2), (x+.55, y(20, 1), z+.45))
    solid(kit, 'Hull aft striped band ribs', v, f, kit.pale, bevel=.06)

    for side, s in SIDES:
        # Edge lamps along the striped band's outboard ends, where the sprite has pale dots.
        for k, x in enumerate((-34.5, -37.5, -40.5)):
            yy = y(21.6, s)
            lamp(kit, f'Hull aft {side} band edge lamp {k+1}', (x, yy, seat(kit, x, yy, AFT_DECK)+.2), .35)
        # Outboard machinery shelves: bolted service hatches and louvred vents.
        for k, x in enumerate((-45, -54.5)):
            bolted_plate(kit, f'Hull aft {side} service hatch {k+1}', x, y(30, s), AFT_DECK, 5.0, 4.2, kit.maroon)
        louvre_vent(kit, f'Hull aft {side} shelf vent', -66.5, y(30.2, s), AFT_DECK, 5.5, 3.6)
        # Stern below the bridge: pale equipment housing either side of the door landing.
        lo, hi = sorted((y(5.2, s), y(11.8, s)))
        kit.box(f'Hull aft {side} stern housing', (-75, (lo+hi)/2, AFT_DECK+1.3), (5, hi-lo, 2.6), kit.pale, bevel=.25)
        # Radiator grille: fore-aft fins standing in the recessed well.
        v, f = [], []
        for k in range(7):
            yy = y(5.6+k*1.25, s)
            block(v, f, (-85.6, yy-.17, AFT_DECK-1.6), (-78.6, yy+.17, AFT_DECK-.1))
        solid(kit, f'Hull aft {side} stern grille fins', v, f, kit.steel)
        # ... continued down the aft faces as vertical slats on a dark backing.
        v, f, bv, bf = [], [], [], []
        for (xa, ra), (xb, rb), n in (((-87, 16), (-90, 9), 6), ((-90, 9), (-92, 5), 3)):
            pa, pb = Vector((xa, y(ra, s), 0)), Vector((xb, y(rb, s), 0))
            e = (pb-pa).normalized()
            out = Vector((-e.y, e.x, 0))
            if out.dot(Vector((-1, 0, 0))) < 0:
                out = -out
            beam(bv, bf, pa+out*.15-e*.2, pb+out*.15+e*.2, .3, 16, out)   # backing plate, Z -8..8
            for i in range(n):
                p = pa+(pb-pa)*((i+.5)/n)+out*.6
                beam(v, f, (p.x, p.y, -7.5), (p.x, p.y, 7.5), 1.0, .4, out)
        solid(kit, f'Hull aft {side} stern face grille', v, f, kit.steel)
        solid(kit, f'Hull aft {side} stern face grille backing', bv, bf, kit.dark)

    # The pale-pink bolted cover between the two grilles.
    bolted_plate(kit, 'Hull aft stern centre hatch', -81.5, YC, AFT_DECK, 6.0, 7.4, kit.pale)
    # The small tail block on the stern centreline, with the two stern lamps the sprite's amber dots suggest.
    kit.box('Hull aft tail block', (-90.2, YC, 1.5), (7, 11, 12), kit.steel, bevel=.3)
    v, f = [], []
    block(v, f, (-94.0, YC-5.8, 6.4), (-88.5, YC+5.8, 7.9))          # pale cap band (the sprite's light corners)
    for z in (-1.5, 1.5):
        block(v, f, (-94.0, YC-5.2, z-.2), (-93.5, YC+5.2, z+.2))   # guard ribs across the aft face
    solid(kit, 'Hull aft tail block cap', v, f, kit.pale, bevel=.05)
    for side, s in SIDES:
        lamp(kit, f'Hull aft {side} stern lamp', (-93.75, y(3.2, s), 5.2), .45).rotation_euler = (0, math.pi/2, 0)
    # Ventral access hatches on the aft body's flat keel.
    for k, x in enumerate((-50, -66)):
        bolted_plate(kit, f'Hull aft ventral hatch {k+1}', x, YC, AFT_KEEL, 7, 9, kit.maroon, down=True)
