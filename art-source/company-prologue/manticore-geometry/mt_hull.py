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
  turret stands on a raised ring at X 21; the pale command dome (rows 87-110)
  sits on it aft of the ring.
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
from mt_kit import boolean, facet_normal, plane_cut, prism, section, sym

HEAD = sym([(85.5, 9), (84, 14), (80.5, 20), (66, 21), (63, 18.5), (61, 16.5)])
NECK = sym([(63, 16.5), (58, 16), (50, 18), (44, 19.5), (38, 21)])
CORE = sym([(45, 19.5), (38, 22.5), (31, 25), (10, 25), (-20, 24), (-40, 24), (-56, 23)])
WING = sym([(41, 21), (34, 25), (31, 31), (31, 54), (24, 64), (12, 73), (6, 72.5), (-4, 61),
            (-17.5, 47), (-26, 45), (-30, 30)])
AFT = sym([(-15, 40), (-26, 45.5), (-30, 43), (-35, 38.5), (-54, 37), (-56, 34), (-56, 4)])
CORE_DECK, CORE_KEEL = 10.0, -9.0
NECK_DECK, HEAD_DECK = 8.0, 7.5
WING_DECK, WING_KEEL = 5.0, -4.0
AFT_DECK, AFT_KEEL = 8.5, -8.0
POD = (23, 65, 13.5)   # outer turret pod: centre x, |y|, radius
WING_SLOPE = (26.0, .11)   # the wing's upper surface falls outboard of |Y| 26 at 0.11 per unit
LAUNCHER = (32, 43)    # WS 004 / WS 005 hardpoint, port side


def ccw(poly):
    area = sum(x0*y1-x1*y0 for (x0, y0), (x1, y1) in zip(poly, poly[1:]+poly[:1]))
    return poly if area > 0 else list(reversed(poly))


def shear(ob, top_only=False):
    """Lower vertices outboard of WING_SLOPE's |Y| so a part follows the wing's falling deck."""
    y0, k = WING_SLOPE
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    for s in (1, -1):  # a crease at |Y| = y0 keeps every face planar
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:]+bm.edges[:]+bm.faces[:], plane_co=(0, s*y0, 0),
                               plane_no=(0, 1, 0))
    for v in bm.verts:
        if (not top_only or v.co.z > 0) and abs(v.co.y) > y0:
            v.co.z -= k*(abs(v.co.y)-y0)
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()
    return ob


def octagon_plan(cx, cy, r, n=8, phase=math.pi/8):
    return [(cx+r*math.cos(phase+2*math.pi*k/n), cy+r*math.sin(phase+2*math.pi*k/n)) for k in range(n)]


def build(kit):
    # The bow head (owner, round 1: a better reading than a red block). The sprite
    # draws a gunmetal frame round the raised WS 002 ring, two red armour horns
    # on its forward shoulders (rows 6-17, the bright angled plates), a red
    # crossbar behind the ring (rows 22-26) and a grey prow bar at the stem.
    head = prism(kit, 'Hull bow head', ccw(HEAD), section(-5.5, HEAD_DECK, 3.4, 3.0), kit.steel)
    plane_cut(head, 'bow top rake', (79, 0, HEAD_DECK), facet_normal((1, 0), 32))
    n = facet_normal((1, 0), 34)
    plane_cut(head, 'bow chin rake', (78, 0, -5.5), (n[0], 0, -n[2]))
    for s in (1, -1):
        plane_cut(head, f'bow {s} cheek', (82, s*15, HEAD_DECK-1), facet_normal((.6, s), 38))
        plane_cut(head, f'bow {s} crown fall', (70, s*11, HEAD_DECK), facet_normal((0, s), 18))
        side = 'port' if s > 0 else 'starboard'
        # Armour horn: a red wedge on the forward shoulder, its top falling outboard
        # and forward from a ridge along its inboard edge.
        # It wraps the head's shoulder (out past the side wall) and stands well
        # proud of the deck, so the bow reads as armour round a gun, not a slab.
        horn = prism(kit, f'Hull bow {side} horn', ccw([(85, s*9), (85, s*16), (80, s*21.5), (69, s*21.5),
                                                         (67.5, s*15), (72, s*8.5)]),
                     section(HEAD_DECK-9.0, HEAD_DECK+4.4, 1.6, .9), kit.red, .15)
        plane_cut(horn, f'{side} horn outboard fall', (76, s*12, HEAD_DECK+4.4), facet_normal((0, s), 34))
        plane_cut(horn, f'{side} horn forward rake', (78, s*12, HEAD_DECK+4.4), facet_normal((1, 0), 36))
        plane_cut(horn, f'{side} horn aft rake', (71, s*12, HEAD_DECK+4.4), facet_normal((-1, 0), 40))
        n = facet_normal((1, 0), 40)
        plane_cut(horn, f'{side} horn chin', (82, s*12, HEAD_DECK-9.0), (n[0], 0, -n[2]))
        kit.hull.append(horn)
        # Neck rail: the long red strake down each side of the neck (rows 26-45).
        rail = prism(kit, f'Hull neck {side} rail', ccw([(64, s*10.5), (64, s*15), (52, s*17.5), (43, s*19),
                                                          (43, s*14.5), (52, s*12.5)]),
                     section(NECK_DECK-4.0, NECK_DECK+1.6, .6, .7), kit.red, .15)
        plane_cut(rail, f'{side} rail outboard fall', (52, s*13, NECK_DECK+1.6), facet_normal((0, s), 26))
        kit.hull.append(rail)
    crossbar = prism(kit, 'Hull bow crossbar', ccw(sym([(66.5, 7.5), (64, 9.5), (61, 8.5), (60.5, 4)])),
                     section(NECK_DECK-2.0, HEAD_DECK+1.4, .5, 1.3), kit.red, .15)
    prow = prism(kit, 'Hull bow prow block', ccw(sym([(86.5, 6), (84, 8.5), (80, 8.5), (80, 4)])),
                 section(-4.5, HEAD_DECK-1.0, 1.4, 1.6), kit.plate, .15)
    plane_cut(prow, 'prow face rake', (85, 0, HEAD_DECK-1.0), facet_normal((1, 0), 40))
    kit.hull.append(prow)
    kit.hull.append(crossbar)
    neck = prism(kit, 'Hull neck', ccw(NECK), section(-6, NECK_DECK, 1.8, 2.4), kit.steel)
    core = prism(kit, 'Hull core', ccw(CORE), section(CORE_KEEL, CORE_DECK, 3.4, 3.0), kit.red)
    plane_cut(core, 'core bow rake', (40, 0, CORE_DECK), facet_normal((1, 0), 30))
    for s in (1, -1):  # a crowned deck: the core's shoulders fall away outboard of the turret ring
        plane_cut(core, f'core {s} shoulder fall', (0, s*15, CORE_DECK), facet_normal((0, s), 22))
    plane_cut(core, 'core aft deck fall', (-46, 0, CORE_DECK), facet_normal((-1, 0), 14))
    wing = shear(prism(kit, 'Hull wing body', ccw(WING), section(WING_KEEL, WING_DECK, 3.0, 1.8), kit.steel), True)
    aft = prism(kit, 'Hull aft body', ccw(AFT), section(AFT_KEEL, AFT_DECK, 2.2, 2.6), kit.steel)
    for s in (1, -1):
        plane_cut(aft, f'aft {s} shoulder', (-30, s*36, AFT_DECK), facet_normal((0, s), 34))
    # Ventral keel under the core: depth for the side profile.
    keel = prism(kit, 'Hull ventral keel', ccw(sym([(36, 10), (-46, 12), (-52, 8)])),
                 section(CORE_KEEL-3.5, CORE_KEEL+.5, 1.6, 0), kit.steel)
    kit.hull += [head, neck, core, wing, aft, keel]

    # Outer turret pods: octagonal drums at the wing tips.
    x, y, r = POD
    for side, s in (('port', 1), ('starboard', -1)):
        pod = prism(kit, f'Hull {side} turret pod', ccw(octagon_plan(x, s*y, r)), section(-5.5, 6.0, 3.0, 3.4),
                    kit.steel)
        kit.hull.append(pod)
        # The red armour collar round the pod's outboard half (the sprite's red crescents).
        collar = prism(kit, f'Hull {side} pod armour collar', ccw(octagon_plan(x, s*y, r+1.0)),
                       section(-2.5, 4.4, 1.0, 1.4), kit.red, .12)
        hole = prism(kit, f'Temporary {side} pod collar cutter', ccw(octagon_plan(x, s*y, r-1.2)),
                     section(-8, 12, 0, 0), kit.red)
        boolean(collar, hole)
        plane_cut(collar, f'{side} collar inboard half', (x, s*(y-3), 0), (.35, -s, 0))
        kit.hull.append(collar)
        # Launcher housing: a base block with two cheeks either side of the rack.
        lx, ly = LAUNCHER
        base = prism(kit, f'Hull {side} launcher housing',
                     ccw([(lx+11, s*(ly-9)), (lx+11, s*(ly+9)), (lx-7, s*(ly+10)), (lx-7, s*(ly-10))]),
                     section(-2.0, 4.0, .8, 1.0), kit.steel)
        kit.hull.append(base)
        for k, dy in (('inboard', -8.6), ('outboard', 8.6)):
            cheek = prism(kit, f'Hull {side} launcher {k} cheek',
                          ccw([(lx+12, s*(ly+dy-.9)), (lx+12, s*(ly+dy+.9)), (lx-4, s*(ly+dy+1.1)),
                               (lx-4, s*(ly+dy-1.1))]), section(2.0, 10.0, .3, .6), kit.steel, .1)
            plane_cut(cheek, f'{side} {k} cheek rake', (lx+8, s*(ly+dy), 10.0), facet_normal((1, 0), 40))
            kit.hull.append(cheek)

    # Raised ring round the large WS 000 turret, and the small bow ring for WS 002.
    kit.hull.append(kit.cylinder('Hull main turret ring', (21, 0, CORE_DECK-.4), 17.5, 3.6, kit.steel,
                                 vertices=24, bevel=.25))
    kit.hull.append(kit.cylinder('Hull bow turret ring', (73, 0, HEAD_DECK+.4), 8.0, 1.2, kit.steel,
                                 vertices=16, bevel=.2))
    # The pale command dome aft of the main ring (sprite rows 87-110).
    dome = prism(kit, 'Bridge command dome', ccw(sym([(-1, 5), (-3, 9.5), (-8, 11.5), (-15, 11), (-19.5, 7)])),
                 section(CORE_DECK-1, CORE_DECK+6.5, .6, 3.0), kit.pale)
    plane_cut(dome, 'dome front rake', (-3, 0, CORE_DECK+6.5), facet_normal((1, 0), 38))
    kit.hull.append(dome)
