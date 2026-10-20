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
POD = (23, 65, 13.5)
# The nacelle's lofted stations (x, half-width, deck, keel), before the wing droop.
POD_STATIONS = [(42, 2, 3, -6), (36, 7.5, 9, -11), (29, 10.5, 11, -13), (17, 10.5, 11, -13), (10, 8, 9, -10),
                (6, 4, 6, -6)]   # outer turret pod: centre x, |y|, radius
# Owner, round 3 (a bird of prey): the arms bend down toward their tips, 0.16
# per unit outboard of |Y| 24 and 0.42 outboard of |Y| 46. Round 4 (owner: the
# neck still climbs; the central hull can go down): the whole forward hull
# pitches down from just ahead of the bridge, 0.10 per unit forward of X 10, and
# the beak bends harder, 0.32 per unit forward of X 62. Turret rings stay level.
WING_DROOP = [(24.0, .30)]
NOSE_DROOP = [(10.0, .18)]


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
LAUNCHER_WELL_DEPTH = 3.5
# Round 7 (owner: cohesive angling, not wobbly): one angle vocabulary for the
# whole hull. Every lofted body's shoulders fall 30 deg and its lower faces 45
# deg; armour plates fall 30 deg outboard and rake 45 deg at their faces; the
# forward hull bends down in one line from the bridge and the arms in one
# anhedral. (The stern keeps its own accepted sections.)
SHOULDER = math.tan(math.radians(30))
LOWER = 1.0
FALL, RAKE = 30, 45


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


def facets(hw, top, bot, flat=.5, keel=.45, up=SHOULDER, down=LOWER):
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
HEAD_STATIONS = [(87.5, 4, 2.5, -4), (83, 14, 6, -7), (78, 20, 8, -10), (66, 21, 8, -12), (61, 17, 8.5, -13)]
NECK_STATIONS = [(63, 16.5, 8.5, -13), (58, 16, 9, -15), (50, 18, 9.2, -17), (43, 19.5, 9.5, -19)]
# Round 5 (owner: the belly is too fat): the core's underside is a slender V,
# its keel a quarter of the beam wide and no deeper than -18.
CORE_STATIONS = [(45, 19.5, 9.5, -13), (38, 22.5, 9.5, -15), (34.6, 24.2, 10, -15.5), (29, 25, 15, -16),
                 (-20, 24, 15, -18), (-40, 24, 13.5, -17), (-57.5, 23, 11, -13)]
# The ventral blade under the core: a faceted fin to -30 for the side profile.
KEEL_STATIONS = [(26, 2, -14, -17), (12, 3, -15, -22), (-34, 3, -15, -22), (-48, 2, -13, -16)]
# The aft engine block (round 5: less blocky): lofted through faceted sections
# that taper to the drive face, so the cans stand out of it as drive units.
AFT_STATIONS = [(-15, 40, 12, -12), (-26, 45.5, 12, -14), (-35, 38.5, 11.5, -14), (-54, 37, 9, -11),
                (-56, 34, 8, -9)]


def lofted(kit, name, stations, mat, **kw):
    return loft_x(kit, name, [(x, facets(hw, top, bot, **kw)) for x, hw, top, bot in stations], mat)


def build(kit):
    head = droop(lofted(kit, 'Hull bow head', HEAD_STATIONS, kit.steel))
    for s in (1, -1):
        side = 'port' if s > 0 else 'starboard'
        # Bow armour strake (owner, round 6: the front needs cohesion): one red
        # armour piece a side runs from the core down the neck and wraps the
        # head's shoulder to the beak's tip, where the two converge, its top
        # falling outboard from a ridge along its inboard edge. It replaces round
        # 1's separate horns, neck rails and prow block.
        horn = prism(kit, f'Hull bow {side} horn', ccw([(88, s*2.5), (86.5, s*7), (81, s*21.5), (67, s*22.5),
                                                         (60, s*18), (52, s*19), (43, s*20.5), (43, s*15.5),
                                                         (52, s*13), (62, s*11), (72, s*9.5), (82, s*5)]),
                     section(-9.0, 11.2, 2.6, .9), kit.red, .15)
        plane_cut(horn, f'{side} horn outboard fall', (60, s*13, 11.2), facet_normal((0, s), FALL))
        plane_cut(horn, f'{side} horn beak rake', (80, 0, 11.2), facet_normal((1, 0), RAKE))
        n = facet_normal((1, 0), RAKE)
        plane_cut(horn, f'{side} horn chin', (82, 0, -9.0), (n[0], 0, -n[2]))
        kit.hull.append(droop(horn))
    crossbar = prism(kit, 'Hull bow crossbar', ccw(sym([(66.5, 7.5), (64, 9.5), (61, 8.5), (60.5, 4)])),
                     section(4.0, HEAD_DECK+1.6, .5, 1.3), kit.red, .15)
    kit.hull += [droop(crossbar)]

    neck = droop(lofted(kit, 'Hull neck', NECK_STATIONS, kit.steel))
    core = droop(lofted(kit, 'Hull core', CORE_STATIONS, kit.red, keel=.25))
    # The main gun stands in a well, so its roof sits low under the bridge's sightline.
    r, d = MAIN_WELL
    well = kit.cylinder('Temporary main well cutter', (21, 0, CORE_DECK-d+10-nose_drop(21)), r, 20, kit.red, vertices=24)
    boolean(core, well)
    # The arms droop outboard (anhedral) with deep chamfers below: the gun pods
    # at their tips hang low and forward of the core's flanks.
    wing = droop(shear(prism(kit, 'Hull wing body', ccw(WING), section(WING_KEEL, WING_DECK, 4.5, 3.0), kit.steel)))
    aft = lofted(kit, 'Hull aft body', AFT_STATIONS, kit.steel, flat=.45, keel=.35, up=.45, down=.6)
    keel = lofted(kit, 'Hull ventral keel', KEEL_STATIONS, kit.steel, flat=.6, keel=.25, up=.2, down=.6)
    kit.hull += [head, neck, core, wing, aft, keel]

    # Outer turret pods: octagonal drums at the drooping wing tips.
    x, y, r = POD
    drop = wing_drop(y)
    lx, ly = LAUNCHER
    ldrop = wing_drop(ly)
    for side, s in (('port', 1), ('starboard', -1)):
        # Gun nacelle (owner, round 6: the arm ends were oddly cylindrical): a
        # faceted armoured nacelle tapering to a point forward and a short
        # stern, flat only under the turret ring.
        pod = loft_x(kit, f'Hull {side} turret pod',
                     [(px, [(s*y+yy, zz-drop) for yy, zz in facets(hw, top, bot, flat=.68, keel=.3)])
                      for px, hw, top, bot in POD_STATIONS], kit.steel)
        kit.hull.append(droop(pod))
        # Angular armour on the nacelle (after the Onslaught's layered plate):
        # an outboard flank plate with a raked face, and a chevron glacis on
        # the nacelle's nose.
        flank = prism(kit, f'Hull {side} pod armour collar',
                      ccw([(38, s*(y+4.5)), (31, s*(y+11.2)), (12, s*(y+11.2)), (7.5, s*(y+6.5))]),
                      section(-11.0-drop, 7.5-drop, 2.4, 1.6), kit.red, .12)
        plane_cut(flank, f'{side} flank outboard fall', (22, s*(y+8.5), 7.5-drop), facet_normal((0, s), FALL))
        plane_cut(flank, f'{side} flank forward rake', (33, s*(y+8), 7.5-drop), facet_normal((1, .4*s), RAKE))
        kit.hull.append(droop(flank))
        glacis = prism(kit, f'Hull {side} pod nose glacis',
                       ccw([(42.5, s*y), (36, s*(y+6.5)), (32, s*(y+6)), (32, s*(y-6)), (36, s*(y-6.5))]),
                       section(-6.0-drop, 8.6-drop, 2.0, .8), kit.red, .12)
        plane_cut(glacis, f'{side} glacis rake', (34, s*y, 8.6-drop), facet_normal((1, 0), RAKE))
        kit.hull.append(droop(glacis))
        # Launcher bay (round 5: inset; round 7: not a wedge cut into the
        # wing). The Harpoon slot sits on the arm's leading edge, so the arm
        # grows a faceted armoured bay forward round it, its deck flush with the
        # wing's, its sides and nose in the hull's 30/45 deg vocabulary; the rack
        # sits down a chamfered recess in it, open forward for the tubes.
        # The bay follows the arm's anhedral like the wing (sheared, then bent
        # with the forward hull); the recess floor stays level for the rack.
        bay = loft_x(kit, f'Hull {side} launcher bay',
                     [(bx, [(s*ly+yy, zz) for yy, zz in facets(hw, WING_DECK-dt, WING_DECK-10, flat=.75, keel=.5)])
                      for bx, hw, dt in ((lx+14, 4.5, 3.5), (lx+9, 10.5, 0), (lx-10, 10.5, 0))], kit.red)
        droop(shear(bay))
        deck = WING_DECK-ldrop
        recess = droop(prism(kit, f'Temporary {side} launcher recess cutter',
                       ccw([(lx+40, s*(ly-7.5)), (lx+40, s*(ly+7.5)), (lx-7, s*(ly+7.5)), (lx-7, s*(ly-7.5))]),
                       section(deck-LAUNCHER_WELL_DEPTH, deck+30, 2.5, 0), kit.steel, 0))
        boolean(wing, recess, keep=True)
        boolean(bay, recess)
        kit.hull.append(bay)

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
