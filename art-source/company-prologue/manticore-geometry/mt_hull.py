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
from mt_kit import armour_plates, hull_plating, boolean, facet_normal, loft_x, plane_cut, prism, section, sweep, sym

HEAD = sym([(85.5, 9), (84, 14), (80.5, 20), (66, 21), (63, 18.5), (61, 16.5)])
NECK = sym([(63, 16.5), (58, 16), (50, 18), (44, 19.5), (38, 21)])
CORE = sym([(45, 19.5), (38, 22.5), (31, 25), (10, 25), (-20, 24), (-40, 24), (-57.5, 23)])
# Round 9: the swept trailing edge is one straight line from the pod to the aft body.
WING = sym([(41, 21), (34, 25), (31, 31), (31, 54), (24, 64), (12, 73), (6, 72.5),
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
# Round 9 (owner: long angular sections, not small ones): three panels a side,
# a long taper to the point, a long parallel body and a short tail.
# Round 15: read off the sprite's plan (outboard edge Y 77-79 from X 12 to 31,
# a blunt chamfered nose ending at X 37): the nose station is the blunt front.
POD_STATIONS = [(37.5, 4.5, 11, -9), (31.5, 12.5, 11, -13), (14, 12.5, 11, -13), (5, 6, 7.5, -7)]
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
# Round 11 (owner: the armour is spent angled ahead, facing the target): the
# beak strakes stand higher, each leading edge is a thick raked glacis, the
# gun nacelles carry glacis noses, and behind that fighting face the wings
# are open framework (mt_frames).
HORN_TOP = 12.6
GLACIS = 12.5          # the leading-edge band's depth back from the edge
LAUNCHER_WELL_DEPTH = 3.5
# Round 7 (owner: cohesive angling, not wobbly): one angle vocabulary for the
# whole hull. Every lofted body's shoulders fall 30 deg and its lower faces 45
# deg; armour plates fall 30 deg outboard and rake 45 deg at their faces; the
# forward hull bends down in one line from the bridge and the arms in one
# anhedral. (The stern keeps its own accepted sections.)
SHOULDER = math.tan(math.radians(30))
LOWER = 1.0
FALL, RAKE = 30, 45


POD_LIP = 1.0     # the armour coaming round the nacelle's turret deck, outboard


def pod_section(hw, top, bot, s):
    """The gun nacelle's section [(dy, z)], dy outboard positive (mirrored by s):
    a flat turret deck, a raised armour coaming along its outboard edge with a
    30 deg outer shoulder, a vertical outboard wall, 45 deg lower faces to a
    narrow keel, and a lower inboard side with a 30 deg shoulder."""
    a, b, lip = .7*hw, .3*hw, POD_LIP
    mu = top-SHOULDER*(hw-a)
    ml = min(bot+LOWER*(hw-b), mu-2)
    pts = [(.68*hw, top+lip), (.86*hw, top+lip), (hw, top+lip-SHOULDER*.14*hw), (hw, ml), (b, bot), (-b, bot),
           (-hw, ml), (-hw, mu), (-a, top), (.64*hw, top)]
    return [(s*dy, z) for dy, z in pts]


def pod_half_width(x):
    """The nacelle's half-width at x (linear between POD_STATIONS)."""
    st = sorted(POD_STATIONS)
    if x <= st[0][0]:
        return st[0][1]
    for (xa, wa, _, _), (xb, wb, _, _) in zip(st, st[1:]):
        if x <= xb:
            return wa+(wb-wa)*(x-xa)/(xb-xa)
    return st[-1][1]


def paint_pod_armour(kit, pod, y, s):
    """Red armour on the nacelle's nose, rakes, outboard side and coaming top;
    the turret deck, keel and inboard side stay the dark structure."""
    me = pod.data
    if kit.red.name not in me.materials:
        me.materials.append(kit.red)
    ri = [m.name for m in me.materials].index(kit.red.name)
    for f in me.polygons:
        n, c = f.normal, f.center
        out = (c.y*s-y)
        if n.x > .3 or n.y*s > .25 or (n.z > .9 and out > .66*pod_half_width(c.x)):
            f.material_index = ri


DOME_OUTLINE = [(-1, 5), (-3, 9.5), (-8, 11.5), (-15, 11), (-19.5, 7)]   # port side, bow to stern
DOME_SHOULDER = 1.0   # radius of the roof's rounded shoulder


def dome_plan(per_span=10):
    """The command dome's plan: a closed Catmull-Rom curve through DOME_OUTLINE
    and its mirror, sampled `per_span` points between each pair (CCW)."""
    pts = ccw(sym(DOME_OUTLINE))
    n, out = len(pts), []
    for i in range(n):
        p0, p1, p2, p3 = pts[i-1], pts[i], pts[(i+1) % n], pts[(i+2) % n]
        for k in range(per_span):
            t = k/per_span
            out.append(tuple(.5*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t**3)
                             for a, b, c, d in zip(p0, p1, p2, p3)))
    return out


def dome_profile(steps=8):
    """(inset, z) keel to roof: a keel chamfer, a vertical wall, a rounded shoulder."""
    keel, roof, r = BRIDGE_DECK-1, BRIDGE_DECK+5.0, DOME_SHOULDER
    prof = [(.6, keel), (0, keel+.6)]
    for k in range(steps+1):
        a = math.pi/2*k/steps
        prof.append((r*(1-math.cos(a)), roof-r+r*math.sin(a)))
    return prof


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


# Round 9 (owner: "long angular hull sections"; round 8 read as a blob of
# small facets): each body is lofted through as few stations as its outline
# allows, so its faces are long panels and its creases long straight lines.
# The core runs one constant section from the gun's step to the engine block.
# Lofted stations (x, half-width, deck, keel), bow first. Owner, round 2: the
# hull is not one flat surface and does not float in water. The bow head is
# low; the neck is a glacis climbing aft to the main gun's deck, so the main
# gun superfires over the bow gun; the deck drops in a steep step ahead of the
# main gun's well, so its barrel overhangs the fall rather than lying on it; the core reaches far below its deck.
HEAD_STATIONS = [(87.5, 4, 2.5, -4), (78, 20, 8, -10.5), (66, 21, 8, -12), (61, 17, 8.5, -13)]
NECK_STATIONS = [(63, 16.5, 8.5, -13), (43, 19.5, 9.5, -19)]
# Round 5 (owner: the belly is too fat): the core's underside is a slender V,
# its keel a quarter of the beam wide and no deeper than -18.
CORE_STATIONS = [(45, 19.5, 9.5, -13), (34.6, 24.5, 10, -15.5), (29, 25, 15, -16.5), (-40, 25, 15, -16.5),
                 (-57.5, 23, 11, -13)]
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
        # Round 9: its inboard edge is one straight line from the core to the
        # beak, its outboard edge three, so the strake is one long plate.
        horn = prism(kit, f'Hull bow {side} horn', ccw([(88, s*2.5), (81, s*21.5), (67, s*22.5), (60, s*18),
                                                         (43, s*20.5), (43, s*15.5), (86.5, s*3.5)]),
                     section(-9.0, HORN_TOP, 2.6, .9), kit.red, .15)
        plane_cut(horn, f'{side} horn outboard fall', (60, s*13, HORN_TOP), facet_normal((0, s), FALL))
        plane_cut(horn, f'{side} horn beak rake', (79, 0, HORN_TOP), facet_normal((1, 0), RAKE))
        n = facet_normal((1, 0), RAKE)
        plane_cut(horn, f'{side} horn chin', (82, 0, -9.0), (n[0], 0, -n[2]))
        # Round 15: the strake is combat armour, laid in courses (mt_kit.armour_plates).
        droop(horn)
        armour_plates(kit, horn, [((x, 0, 0), (1, 0, 0)) for x in (50, 55, 62, 70, 78)])
        kit.hull.append(horn)
    crossbar = prism(kit, 'Hull bow crossbar', ccw(sym([(66.5, 7.5), (64, 9.5), (61, 8.5), (60.5, 4)])),
                     section(4.0, HEAD_DECK+1.6, .5, 1.3), kit.red, .15)
    droop(crossbar)
    armour_plates(kit, crossbar)
    kit.hull += [crossbar]

    neck = droop(lofted(kit, 'Hull neck', NECK_STATIONS, kit.steel))
    core = droop(lofted(kit, 'Hull core', CORE_STATIONS, kit.red, keel=.25))
    # Round 15: the core is hull, not combat armour: fine plate lines at its
    # frames; the decks where the bridge and fittings stand stay whole.
    hull_plating(kit, core, [((x, 0, 0), (1, 0, 0)) for x in (34, 21, 8, -3, -14, -26)])
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
        # Gun nacelle. Round 15 (owner: "the wing tips near the turrets have
        # weird hull still"): one armoured body read off the sprite's plan, a
        # blunt chamfered nose at X 37 and the outboard edge at Y 77.5, in
        # place of round 11's point to X 42 and the separate collar and nose
        # glacis that stood below its deck. Its outboard side and nose are the
        # red armour (with a raised coaming round the turret deck), its inboard
        # side the dark structure that meets the wing framework, and the
        # armour is laid in plates (mt_kit.armour_plates).
        pod = loft_x(kit, f'Hull {side} turret pod',
                     [(px, [(s*(y+yy), zz-drop) for yy, zz in pod_section(hw, top, bot, s)])
                      for px, hw, top, bot in POD_STATIONS], kit.steel)
        nose_x = POD_STATIONS[1][0]+1.5
        plane_cut(pod, f'{side} pod nose rake', (nose_x, s*y, POD_STATIONS[1][2]-drop+POD_LIP),
                  facet_normal((1, 0), 40))
        n = facet_normal((1, 0), 45)
        plane_cut(pod, f'{side} pod chin', (nose_x-2, s*y, POD_STATIONS[1][3]-drop), (n[0], 0, -n[2]))
        paint_pod_armour(kit, pod, y, s)
        droop(pod)
        armour_plates(kit, pod, [((x, 0, 0), (1, 0, 0)) for x in (9, 15, 21, 27, 33)], plate_mat=kit.red)
        kit.hull.append(pod)
        # Launcher bay (rounds 5-8: inset, flush with the deck, ending at the
        # leading edge; the rack sits down a recess open forward through it).
        # Round 9 (owner: long sections): the leading-edge armour and the
        # launcher bay are one red band carried along the whole leading edge,
        # from the neck to the gun nacelle, its wall, raked face and top each
        # one panel the arm's length; the Harpoon recess is cut into it.
        # Round 11: the band is the arm's fighting face, a glacis rising from a
        # short chin at the edge to a flat top GLACIS units back.
        d0, top = -.3, WING_DECK+3.0
        band = sweep(kit, f'Hull {side} launcher bay', [(44.5, s*19.3), (34, s*25), (31, s*31), (31, s*55)],
                     [(d0, WING_KEEL+6.0), (d0+4.6, WING_KEEL+1.4), (GLACIS, WING_KEEL+1.4), (GLACIS, top),
                      (d0+10.0, top)], kit.red, s=s)
        plane_cut(band, f'{side} band root rake', (44.5, 0, 4.0), facet_normal((1, 0), 60))
        plane_cut(band, f'{side} band inboard chamfer', (40, s*19, top), facet_normal((0, -s), 45))
        bay = droop(shear(band))
        # Round 15: the glacis band is the arm's fighting face, laid in courses.
        armour_plates(kit, bay, [((0, s*y0, 0), (0, 1, 0)) for y0 in (28, 33, 38, 43, 48, 53)])
        # The band's root must stop at the main gun's well (it reached into it, rounds 9-10).
        r, d = MAIN_WELL
        boolean(bay, kit.cylinder(f'Temporary {side} band well cutter', (21, 0, CORE_DECK-d-nose_drop(21)), r, 40,
                                  kit.red, vertices=24))
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
    hull_plating(kit, tier, [((-8, 0, 0), (1, 0, 0)), ((-17, 0, 0), (1, 0, 0))])
    kit.hull.append(tier)
    # The pale command dome on the tier (sprite rows 87-110). Round 14 (owner:
    # "too low poly"): its plan is a smooth closed curve through the round-3
    # outline, and its shoulder a rounded quarter round, not one chamfer; the
    # visor rake starts further forward so the glazing has a wall to sit in.
    dome = prism(kit, 'Bridge command dome', dome_plan(), dome_profile(), kit.pale)
    plane_cut(dome, 'dome front rake', (-1.8, 0, BRIDGE_DECK+5.0), facet_normal((1, 0), 38))
    kit.hull.append(dome)
