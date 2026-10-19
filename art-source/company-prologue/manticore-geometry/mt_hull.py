"""The Manticore (P)'s structure: bow head, neck, core, wings, aft body, pods.

Read off manticore_pirate.png (164x154, .ship centre (82, 67) from the bottom
edge; sprite column c, row r from the top covers X 86-r..87-r and Y
81-c..82-c). The hull is wider (Y +-79) than it is long (X -66..86):
- Bow head (rows 4-26, X 62-84): a squared gunmetal head, Y +-20, carrying
  the small WS 002 turret on a raised ring, two red armour horns on its
  forward shoulders, a red crossbar behind the ring, a grey prow bar.
- Neck (rows 26-45, X 42-62): narrower (pinched to +-16 at X 58), a gunmetal
  spine of ribs between two long red rails.
- Core (X 44 to the stern, Y +-24): the thick central body. The large WS 000
  turret stands in a well at X 21; the pale command dome (rows 87-110) sits
  aft of it on a raised command tier.
Round 2 (owner: one flat surface, a thin flying wing, a bridge that sees only
the back of the main gun): head, neck and core are lofted through faceted
sections reaching far below the deck, flat only where a turret ring stands;
the neck is a glacis climbing to the main gun's deck and the deck drops in a
step ahead of the gun's well; the arms droop; the command tier lifts the
bridge so its eye clears the sunk main gun and sees the bow gun over it.
- Wings (X 31 to -26): a swept delta out to the outer turret pods, its
  trailing edge running from (6, 72) to (-18, 47); a gunmetal structure that
  carries the red armour plates (pass 2), its upper surface falling outboard.
- Outer turret pods (centred on WS 001 / WS 003 at (23, +-65)): octagonal
  gunmetal drums at the wing tips.
- Launcher housings (WS 004 / WS 005 at (32, +-43)): the medium missile
  hardpoints stand in a two-cheeked housing on the wing's leading edge.
- Aft body (X -18 to -56, Y +-46 narrowing to +-37): the engine block whose
  aft face carries the five drives.
Heights are proposed art; the game gives none.
"""
import math
import bmesh
import bpy
from mt_kit import boolean, facet_normal, loft_x, plane_cut, prism, section, sym

HEAD = sym([(85.5, 9), (84, 14), (80.5, 20), (66, 21), (63, 18.5), (61, 16.5)])
NECK = sym([(63, 16.5), (58, 16), (50, 18), (44, 19.5), (38, 21)])
CORE = sym([(45, 19.5), (38, 22.5), (31, 25), (10, 25), (-20, 24), (-40, 24), (-57.5, 23)])
WING = sym([(41, 21), (34, 25), (31, 31), (31, 54), (24, 64), (12, 73), (6, 72.5), (-4, 61),
            (-17.5, 47), (-26, 45), (-30, 30)])
AFT = sym([(-15, 40), (-26, 45.5), (-30, 43), (-35, 38.5), (-54, 37), (-56, 34), (-56, 4)])
CORE_DECK, CORE_KEEL = 15.0, -22.0
NECK_DECK, HEAD_DECK = 11.0, 8.0
HEAD_KEEL, NECK_KEEL = -10.0, -14.0
WING_DECK, WING_KEEL = 9.0, -9.0
AFT_DECK, AFT_KEEL = 12.0, -18.0
BRIDGE_DECK = 16.5   # the bridge plinth's roof, just above the core deck (owner: never a taller bridge)
MAIN_WELL = (17.5, 7.4)   # the main gun stands in a well (radius, depth below the core deck)
POD = (23, 65, 13.5)   # outer turret pod: centre x, |y|, radius
# Owner, round 3 (a bird of prey): the arms bend down toward their tips, 0.16
# per unit outboard of |Y| 24 and 0.42 outboard of |Y| 46. Round 4 (owner: the
# neck still climbs; the central hull can go down): the whole forward hull
# pitches down from just ahead of the bridge, 0.10 per unit forward of X 10, and
# the beak bends harder, 0.32 per unit forward of X 62. Turret rings stay level.
WING_DROOP = [(24.0, .16), (46.0, .42)]
NOSE_DROOP = [(10.0, .10), (62.0, .32)]


def wing_drop(y):
    """How far the wing deck has fallen at |y|."""
    y, d = abs(y), 0.0
    for k, (y0, slope) in enumerate(WING_DROOP):
        y1 = WING_DROOP[k+1][0] if k+1 < len(WING_DROOP) else 1e9
        if y > y0:
            d += slope*(min(y, y1)-y0)
    return d


def nose_drop(x):
    """How far the forward hull has bent down at x."""
    d = 0.0
    for k, (x0, slope) in enumerate(NOSE_DROOP):
        x1 = NOSE_DROOP[k+1][0] if k+1 < len(NOSE_DROOP) else 1e9
        if x > x0:
            d += slope*(min(x, x1)-x0)
    return d


def droop(ob):
    """Bend a part down forward of NOSE_DROOP's breaks (a crease at each keeps faces planar)."""
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    for x0, _k in NOSE_DROOP:
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:]+bm.edges[:]+bm.faces[:], plane_co=(x0, 0, 0),
                               plane_no=(1, 0, 0))
    for v in bm.verts:
        v.co.z -= nose_drop(v.co.x)
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()
    return ob
LAUNCHER = (32, 43)    # WS 004 / WS 005 hardpoint, port side


def ccw(poly):
    area = sum(x0*y1-x1*y0 for (x0, y0), (x1, y1) in zip(poly, poly[1:]+poly[:1]))
    return poly if area > 0 else list(reversed(poly))


def shear(ob, top_only=False):
    """Lower vertices outboard of WING_DROOP's first |Y| so a part follows the wing's falling deck."""
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    for y0, _k in WING_DROOP:
        for s in (1, -1):  # a crease at each break keeps every face planar
            bmesh.ops.bisect_plane(bm, geom=bm.verts[:]+bm.edges[:]+bm.faces[:], plane_co=(0, s*y0, 0),
                                   plane_no=(0, 1, 0))
    for v in bm.verts:
        if not top_only or v.co.z > 0:
            v.co.z -= wing_drop(v.co.y)
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()
    return ob


def octagon_plan(cx, cy, r, n=8, phase=math.pi/8):
    return [(cx+r*math.cos(phase+2*math.pi*k/n), cy+r*math.sin(phase+2*math.pi*k/n)) for k in range(n)]


def facets(hw, top, bot, flat=.5, keel=.45, up=.45, down=.55):
    """A faceted cross-section [(y, z)] round the hull, never a slab: a flat
    deck only `flat` x the half-width wide (where a turret ring stands),
    shoulders falling `up` per unit to the beam, a short vertical beam, and
    lower faces rising `down` per unit from a keel `keel` x the half-width."""
    a, b = flat*hw, keel*hw
    mu = top-up*(hw-a)
    ml = min(bot+down*(hw-b), mu-2)
    return [(a, top), (-a, top), (-hw, mu), (-hw, ml), (-b, bot), (b, bot), (hw, ml), (hw, mu)]


# Lofted stations (x, half-width, deck, keel), bow first. Owner, round 2: the
# hull is not one flat surface and does not float in water. The bow head is
# low; the neck is a glacis climbing aft to the main gun's deck, so the main
# gun superfires over the bow gun; the deck drops in a steep step ahead of the
# main gun's well, so its barrel overhangs the fall rather than lying on it; the core reaches far below its deck.
HEAD_STATIONS = [(86.5, 7, 2.5, -3), (83, 14, 6, -7), (78, 20, 8, -10), (66, 21, 8, -12), (61, 17, 8.5, -13)]
NECK_STATIONS = [(63, 16.5, 8.5, -13), (58, 16, 9, -15), (50, 18, 9.2, -17), (43, 19.5, 9.5, -19)]
CORE_STATIONS = [(45, 19.5, 9.5, -19), (38, 22.5, 9.5, -21), (34.6, 24.2, 10, -21.5), (29, 25, 15, -22), (10, 25, 15, -23),
                 (-20, 24, 15, -24), (-40, 24, 13.5, -22), (-57.5, 23, 11, -16)]
# The ventral blade under the core: a faceted fin to -30 for the side profile.
KEEL_STATIONS = [(32, 5, -18, -22), (20, 9, -20, -30), (-36, 9, -20, -30), (-50, 5, -16, -21)]


def lofted(kit, name, stations, mat, **kw):
    return loft_x(kit, name, [(x, facets(hw, top, bot, **kw)) for x, hw, top, bot in stations], mat)


def build(kit):
    head = droop(lofted(kit, 'Hull bow head', HEAD_STATIONS, kit.steel))
    for s in (1, -1):
        side = 'port' if s > 0 else 'starboard'
        # Armour horn (owner, round 1: a better head than a red block): a red
        # wedge wrapping each forward shoulder, its top falling outboard and
        # forward from a ridge along its inboard edge, its chin cut back.
        horn = prism(kit, f'Hull bow {side} horn', ccw([(85, s*9), (85, s*16), (80, s*21.5), (69, s*21.5),
                                                         (67.5, s*15), (72, s*8.5)]),
                     section(HEAD_DECK-16.0, HEAD_DECK+4.4, 2.6, .9), kit.red, .15)
        plane_cut(horn, f'{side} horn outboard fall', (76, s*12, HEAD_DECK+4.4), facet_normal((0, s), 34))
        plane_cut(horn, f'{side} horn forward rake', (78, s*12, HEAD_DECK+4.4), facet_normal((1, 0), 36))
        plane_cut(horn, f'{side} horn aft rake', (71, s*12, HEAD_DECK+4.4), facet_normal((-1, 0), 40))
        n = facet_normal((1, 0), 40)
        plane_cut(horn, f'{side} horn chin', (80, s*12, HEAD_DECK-16.0), (n[0], 0, -n[2]))
        kit.hull.append(droop(horn))
        # Neck rail: the long red strake down each side of the neck (rows 26-45),
        # riding the glacis.
        rail = prism(kit, f'Hull neck {side} rail', ccw([(64, s*10.5), (64, s*15), (52, s*17.5), (43, s*19),
                                                          (43, s*14.5), (52, s*12.5)]),
                     section(NECK_KEEL+3.0, 17.0, 1.0, .7), kit.red, .15)
        plane_cut(rail, f'{side} rail glacis', (63, 0, 10.0), facet_normal((1, 0), 2))
        plane_cut(rail, f'{side} rail outboard fall', (52, s*13, 10.4), facet_normal((0, s), 26))
        kit.hull.append(droop(rail))
    crossbar = prism(kit, 'Hull bow crossbar', ccw(sym([(66.5, 7.5), (64, 9.5), (61, 8.5), (60.5, 4)])),
                     section(4.0, HEAD_DECK+1.6, .5, 1.3), kit.red, .15)
    # The prow block stands proud of the head's stem face: coincident faces shade black.
    prow = prism(kit, 'Hull bow prow block', ccw(sym([(87.3, 6), (84.8, 8.5), (80, 8.5), (80, 4)])),
                 section(-6.0, 4.5, 2.0, 1.6), kit.plate, .15)
    plane_cut(prow, 'prow face rake', (85.8, 0, 4.5), facet_normal((1, 0), 40))
    n = facet_normal((1, 0), 40)
    plane_cut(prow, 'prow chin rake', (84.8, 0, -6.0), (n[0], 0, -n[2]))
    kit.hull += [droop(prow), droop(crossbar)]

    neck = droop(lofted(kit, 'Hull neck', NECK_STATIONS, kit.steel))
    core = droop(lofted(kit, 'Hull core', CORE_STATIONS, kit.red))
    # The main gun stands in a well, so its roof sits low under the bridge's sightline.
    r, d = MAIN_WELL
    well = kit.cylinder('Temporary main well cutter', (21, 0, CORE_DECK-d+10-nose_drop(21)), r, 20, kit.red, vertices=24)
    boolean(core, well)
    # The arms droop outboard (anhedral) with deep chamfers below: the gun pods
    # at their tips hang low and forward of the core's flanks.
    wing = droop(shear(prism(kit, 'Hull wing body', ccw(WING), section(WING_KEEL, WING_DECK, 4.5, 3.0), kit.steel)))
    aft = prism(kit, 'Hull aft body', ccw(AFT), section(AFT_KEEL, AFT_DECK, 5.0, 3.5), kit.steel)
    for s in (1, -1):
        plane_cut(aft, f'aft {s} shoulder', (-30, s*36, AFT_DECK), facet_normal((0, s), 34))
    keel = lofted(kit, 'Hull ventral keel', KEEL_STATIONS, kit.steel, flat=.6, keel=.25, up=.2, down=.6)
    kit.hull += [head, neck, core, wing, aft, keel]

    # Outer turret pods: octagonal drums at the drooping wing tips.
    x, y, r = POD
    drop = wing_drop(y)
    lx, ly = LAUNCHER
    ldrop = wing_drop(ly)
    for side, s in (('port', 1), ('starboard', -1)):
        pod = prism(kit, f'Hull {side} turret pod', ccw(octagon_plan(x, s*y, r)),
                    section(-14.0-drop, 11.0-drop, 5.0, 3.4), kit.steel)
        kit.hull.append(droop(pod))
        # The red armour collar round the pod's outboard half (the sprite's red crescents).
        collar = prism(kit, f'Hull {side} pod armour collar', ccw(octagon_plan(x, s*y, r+1.0)),
                       section(-8.0-drop, 8.6-drop, 2.0, 1.4), kit.red, .12)
        hole = prism(kit, f'Temporary {side} pod collar cutter', ccw(octagon_plan(x, s*y, r-1.2)),
                     section(-40, 40, 0, 0), kit.red)
        boolean(collar, hole)
        plane_cut(collar, f'{side} collar inboard half', (x, s*(y-3), 0), (.35, -s, 0))
        kit.hull.append(droop(collar))
        # Launcher housing: a base block with two cheeks either side of the rack.
        base = prism(kit, f'Hull {side} launcher housing',
                     ccw([(lx+11, s*(ly-9)), (lx+11, s*(ly+9)), (lx-7, s*(ly+10)), (lx-7, s*(ly-10))]),
                     section(-8.0-ldrop, 8.0-ldrop, 2.4, 1.0), kit.steel)
        kit.hull.append(droop(base))
        for k, dy in (('inboard', -8.6), ('outboard', 8.6)):
            cheek = prism(kit, f'Hull {side} launcher {k} cheek',
                          ccw([(lx+12, s*(ly+dy-.9)), (lx+12, s*(ly+dy+.9)), (lx-4, s*(ly+dy+1.1)),
                               (lx-4, s*(ly+dy-1.1))]), section(5.5-ldrop, 14.5-ldrop, .3, .6), kit.steel, .1)
            plane_cut(cheek, f'{side} {k} cheek rake', (lx+8, s*(ly+dy), 14.5-ldrop), facet_normal((1, 0), 40))
            kit.hull.append(droop(cheek))

    # The main gun's ring at the bottom of its well, and the small bow ring for WS 002.
    kit.hull.append(kit.cylinder('Hull main turret ring', (21, 0, CORE_DECK-MAIN_WELL[1]-.6-nose_drop(21)), MAIN_WELL[0]-.6, 1.4,
                                 kit.steel, vertices=24, bevel=.2))
    # The bow ring stands level on the drooping beak: its top is the deck's height
    # at the ring's centre, its foot buried in the falling deck ahead.
    kit.hull.append(kit.cylinder('Hull bow turret ring', (73, 0, HEAD_DECK+1.0-nose_drop(73)-1.8), 8.0, 4.8, kit.steel,
                                 vertices=16, bevel=.2))

    # The bridge plinth: a low raked armoured base under the command dome, not a
    # tower (owner, round 3: a taller bridge is not the answer). The view comes
    # from the hull falling away round the bridge instead: the main gun is sunk,
    # the beak droops ahead and the arms bend down outboard.
    tier = prism(kit, 'Bridge command tier', ccw(sym([(2.5, 10), (-1, 16), (-24, 16.5), (-30, 11), (-31, 4)])),
                 section(CORE_DECK-3.0, BRIDGE_DECK, 1.0, 1.0), kit.red)
    plane_cut(tier, 'tier front rake', (0, 0, BRIDGE_DECK), facet_normal((1, 0), 44))
    plane_cut(tier, 'tier aft rake', (-25, 0, BRIDGE_DECK), facet_normal((-1, 0), 30))
    for s in (1, -1):
        plane_cut(tier, f'tier {s} side fall', (-10, s*14, BRIDGE_DECK), facet_normal((0, s), 30))
    kit.hull.append(tier)
    # The pale command dome on the tier (sprite rows 87-110).
    dome = prism(kit, 'Bridge command dome', ccw(sym([(-1, 5), (-3, 9.5), (-8, 11.5), (-15, 11), (-19.5, 7)])),
                 section(BRIDGE_DECK-1, BRIDGE_DECK+5.0, .6, 2.4), kit.pale)
    plane_cut(dome, 'dome front rake', (-3, 0, BRIDGE_DECK+5.0), facet_normal((1, 0), 38))
    kit.hull.append(dome)
