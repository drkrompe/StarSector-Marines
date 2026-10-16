"""Centre hull: the cargo body, its shielded-hold deckhouse, the engine spar
and the two stern skids either side of the flare notch.

Planforms are measured off hound_hegemony.png (62x72, centre 31,27): pixel
column c, row r covers X 44-r..45-r, Y 30-c..31-c. The body is the box
between the pods (cols 18-43, rows 25-62), the spar is the transverse beam
(rows 41-52) that carries the pods, and the skids are the two aft legs
(cols 18-24 and 37-43, rows 63-68) framing the notch where the hidden
flare launcher sits. Heights are proposed art: the sprite gives none.

Pass 2 detail, read off the sprite and the lore (an old, rugged freighter
kept to Hegemony auxiliary standard):
- Deck: segmented armour plates over a dark seam underlay, the turret ring
  (WS 002, not ours) framed by a coaming and kept flat within 5.6 of
  (-4, 4). The sprite's lighter plates either side over the spar (rows
  41-52) are the spar armour plates; orange transverse straps sit at its
  bands (rows 30, 40-41, 59-60).
- Dorsal spine: the light fore-aft ridge at cols 34-36 (rows 30-62), in
  segments, doubling as the centre beam of the aft cargo hatch.
- Deckhouse: cream hatch ribs on the port half (cols 22-25), the three
  amber lamps at col 21 (hound_lights.png), brown hatch panels on the
  starboard half and a dark drain gutter (cols 38-42).
- Aft cargo hatch (rows 51-58) in a coaming, two leaves hinged on the spine.
- Gold coolant loop in front of the deckhouse (rows 25-28) turning aft along
  the starboard edge (col 42, rows 29-34).
- Flanks: grooved armour modules over dark seam cores, recessed bolted
  service covers.
- Stern: the dark band (rows 62-63) is a recessed bulkhead with clamshell
  cargo doors either side of the flare dispenser, a header and hinge line.
- Skids: landing rams beside the open U slot, foot pads, green-grey RCS blocks (rows
  66-69) with nozzles.
- Underside: keel beam, plated keel over a dark underlay, ventral cargo
  hatch, ventral vents and forward landing pads.
"""
import math
import bpy
from mathutils import Vector
from hd_kit import boolean, facet_normal, loft_x, material, mirror_y, octagon, plane_cut, prism, section

KEEL, DECK = -5.0, 6.0
# Aft of the spar the body is +-14 (rows 52-62), forward of it +-13.
BODY_PLAN = [(-18, -14), (-2, -14), (-2, -13), (19, -13), (20.5, -11.5), (20.5, 11.5), (19, 13), (-2, 13), (-2, 14), (-18, 14)]
BODY = section(KEEL, DECK, 2.0, 1.2)

# Shielded cargo holds: a raised armoured deckhouse over the forward hold
# (the sprite's ribbed block, rows 27-39), with hatch ribs along it.
HOUSE_PLAN = [(6, -10.5), (17, -10.5), (18.5, -9), (18.5, 9.5), (17, 11), (6, 11)]
HOUSE = [(0, DECK-.6), (0, 8.8), (.9, 9.7)]
# Cream ribs on the port half of the house (sprite cols 22-25).
HATCH_RIBS = [2.6, 3.75, 4.9, 6.05, 7.2, 8.35]
RIB_X = (7.2, 13.7)
# Amber hold lamps (hound_lights.png col 21, rows 32, 34, 37).
LAMPS = [(12.5, 9.5), (10.5, 9.5), (7.5, 9.5)]

# Engine spar: the transverse beam the pods hang on, reaching into each pod.
SPAR_PLAN = [(-7, -16.5), (4, -16.5), (4, 16.5), (-7, 16.5)]
SPAR = section(-3.6, 3.2, 1.0, 1.2)

# Stern skids, port side; mirrored to starboard. Each is a U opening forward
# in the sprite (rows 63-68): an outer cheek and inner rail joined by a foot.
SKID_PLAN = [(-24, 6), (-17, 6), (-17, 14), (-24, 14)]
SKID_SLOT = ((-22.2, -18.4), (8.2, 9.6))
SKID = section(KEEL+.5, 2.2, 1.5, 1.0)

# WS 002 seats on the deck at (-4, 4); everything we add keeps clear of it.
TURRET = (-4.0, 4.0)
RING = (5.6, 6.1)          # coaming inner / outer radius
CLEAR = RING[1]+.05        # plates are cut back to this radius

# Deck layers: dark seam underlay, plates, straps, spine.
UNDER = DECK+.06
PLATE = UNDER+.36
STRAP = UNDER+.52
SPINE_Y = (-5.0, -3.6)

# Deck plates (x0, x1, y0, y1) by name. The body narrows at X -2.
DECK_PLATES = [
    ('Hull spar armour plate starboard aft', (-9.4, -2.2, -12.6, -5.3), 'pale'),
    ('Hull spar armour plate starboard fore', (-2.0, 2.9, -11.6, -5.3), 'pale'),
    ('Hull spar armour plate port aft', (-9.4, -2.2, 9.0, 12.6), 'pale'),
    ('Hull spar armour plate port fore', (-2.0, 2.9, 9.0, 11.6), 'pale'),
    ('Hull deck plate turret starboard aft', (-9.4, -3.0, -3.3, -.6), 'orange'),
    ('Hull deck plate turret starboard fore', (-2.8, 2.9, -3.3, -.6), 'orange'),
    ('Hull deck plate turret port aft', (-9.4, -3.0, -.4, 8.8), 'orange'),
    ('Hull deck plate turret port fore', (-2.8, 2.9, -.4, 8.8), 'orange'),
    ('Hull deck plate forward sill', (4.8, 5.9, -11.6, 11.6), 'orange'),
    ('Hull deck plate aft port', (-14.6, -9.6, 2.4, 6.6), 'orange'),
    ('Hull deck plate aft starboard', (-14.6, -9.6, -12.6, -11.0), 'orange'),
]
HATCH = (-14.6, -9.6, -10.6, 1.6)   # aft cargo hatch coaming, outer

# Keel plates (x0, x1, y0, y1), mirrored to starboard.
KEEL_PLATES = [(-10.2, -4.6), (-4.4, 1.4), (7.9, 12.6), (12.8, 17.0)]
KEEL_ROWS = [(1.5, 5.9), (6.1, 10.8)]
VENTRAL_HATCH = (1.6, 7.7, 5.6)


def bx(kit, name, x0, x1, y0, y1, z0, z1, mat, bevel=.06):
    return kit.box(name, ((x0+x1)/2, (y0+y1)/2, (z0+z1)/2), (x1-x0, y1-y0, z1-z0), mat, bevel)


def blocks(kit, name, boxes, mat, bevel=0):
    """Many small boxes (bolts, slats, stiffeners) as one object."""
    verts, faces = [], []
    for x0, x1, y0, y1, z0, z1 in boxes:
        b = len(verts)
        verts += [(x, y, z) for z in (z0, z1) for y in (y0, y1) for x in (x0, x1)]
        faces += [(b, b+2, b+3, b+1), (b+4, b+5, b+7, b+6), (b, b+1, b+5, b+4),
                  (b+2, b+6, b+7, b+3), (b, b+4, b+6, b+2), (b+1, b+3, b+7, b+5)]
    return kit.mesh(name, verts, faces, mat, bevel)


def bolt(x, y, z, s=.24, h=.12, axis='Z'):
    """A square bolt head standing proud along an axis from (x, y, z)."""
    if axis == 'Z':
        return (x-s/2, x+s/2, y-s/2, y+s/2, z, z+h)
    if axis == 'Y':
        return (x-s/2, x+s/2, y, y+h, z-s/2, z+s/2) if h > 0 else (x-s/2, x+s/2, y+h, y, z-s/2, z+s/2)
    return (x, x+h, y-s/2, y+s/2, z-s/2, z+s/2) if h > 0 else (x+h, x, y-s/2, y+s/2, z-s/2, z+s/2)


def clear_turret(kit, ob, x0, x1, y0, y1, z0, z1):
    """Cut a deck part back to the turret clearance radius where it reaches in."""
    cx = min(max(TURRET[0], x0), x1)
    cy = min(max(TURRET[1], y0), y1)
    if math.hypot(cx-TURRET[0], cy-TURRET[1]) < CLEAR:
        cut = kit.cylinder('Temporary turret clearance', (*TURRET, (z0+z1)/2), CLEAR, z1-z0+2, kit.dark, 'Z', 32, 0)
        boolean(ob, cut)
    return ob


def deck_plate(kit, name, x0, x1, y0, y1, z0, z1, mat, bevel=.06):
    ob = bx(kit, name, x0, x1, y0, y1, z0, z1, mat, bevel)
    return clear_turret(kit, ob, x0, x1, y0, y1, z0, z1)


def seat(kit, x, y, default):
    hit = kit.surface(x, y)
    return hit[0].z if hit else default


def tube(kit, name, pts, r, mat, sides=8):
    """A faceted pipe swept along a polyline (parallel-transported frame)."""
    P = [Vector(p) for p in pts]
    n = len(P)
    rings = []
    u = None
    for i, p in enumerate(P):
        if i == 0:
            t = (P[1]-p).normalized()
        elif i == n-1:
            t = (p-P[-2]).normalized()
        else:
            t = ((p-P[i-1]).normalized()+(P[i+1]-p).normalized()).normalized()
        if u is None:
            ref = Vector((0, 0, 1)) if abs(t.z) < .9 else Vector((1, 0, 0))
            u = t.cross(ref).normalized()
        else:
            u = (u-t*u.dot(t)).normalized()
        v = t.cross(u).normalized()
        rings.append([p+(u*math.cos(a)+v*math.sin(a))*r for a in (k*math.tau/sides+math.pi/sides for k in range(sides))])
    verts = [tuple(q) for ring in rings for q in ring]
    faces = [tuple(range(sides)), tuple(range((n-1)*sides, n*sides))]
    for s in range(n-1):
        faces += [(s*sides+k, s*sides+(k+1) % sides, (s+1)*sides+(k+1) % sides, (s+1)*sides+k) for k in range(sides)]
    return kit.mesh(name, verts, faces, mat, 0)


def ring_solid(kit, name, centre, r0, r1, z0, z1, mat, verts=32):
    outer = kit.cylinder(name, (*centre, (z0+z1)/2), r1, z1-z0, mat, 'Z', verts, 0)
    inner = kit.cylinder('Temporary ring bore', (*centre, (z0+z1)/2), r0, z1-z0+2, kit.dark, 'Z', verts, 0)
    boolean(outer, inner)
    return outer


def flanks(kit, body):
    """Grooved armour modules on the body walls, seams over dark cores, and
    recessed bolted service covers."""
    cores = {'port': [], 'starboard': []}
    grooves = [(8.5, 13), (13.0, 13), (17.2, 13), (-12.5, 14)]
    for s, label in ((1, 'port'), (-1, 'starboard')):
        for x, w in grooves:
            cut = bx(kit, 'Temporary flank groove', x-.18, x+.18, s*w-.45, s*w+.45, KEEL+2.2, DECK-1.4, kit.dark, 0)
            boolean(body, cut)
            yc = s*(w-.42)
            cores[label].append((x-.2, x+.2, min(yc, yc+s*.14), max(yc, yc+s*.14), KEEL+.3, DECK-.3))
        # Horizontal strake seam: the armour is in two courses.
        for x0, x1, w in ((4.2, 19.2, 13), (-18.2, -7.2, 14)):
            cut = bx(kit, 'Temporary strake groove', x0, x1, s*w-.4, s*w+.4, .7, 1.05, kit.dark, 0)
            boolean(body, cut)
            yc = s*(w-.38)
            cores[label].append((x0, x1, min(yc, yc+s*.14), max(yc, yc+s*.14), .65, 1.1))
    for label, b in cores.items():
        blocks(kit, f'Hull flank seam cores {label}', b, kit.dark)
    # Service covers: recess cut into the wall, a cover plate sitting just
    # inside it with a dark gap all round, four bolt heads.
    covers = [(17.6, 18.9, 1.6, 3.9, 13), (-16.9, -13.1, 1.5, 3.9, 14), (-11.9, -8.1, 1.5, 3.9, 14)]
    k = 0
    for s, label in ((1, 'port'), (-1, 'starboard')):
        for x0, x1, z0, z1, w in covers:
            k += 1
            cut = bx(kit, 'Temporary service cover recess', x0-.12, x1+.12, s*w-.3, s*w+.3, z0-.12, z1+.12, kit.dark, 0)
            boolean(body, cut)
            y0, y1 = sorted((s*(w-.3), s*(w-.08)))
            bx(kit, f'Hull flank service cover {k} {label}', x0, x1, y0, y1, z0, z1, kit.steel, .05)
            bolts = [bolt(x, s*(w-.08), z, .22, s*.1, 'Y') for x in (x0+.25, x1-.25) for z in (z0+.25, z1-.25)]
            blocks(kit, f'Hull flank service cover {k} {label} bolts', bolts, kit.steel)


def deck(kit, faded):
    """Deck plating, straps, turret coaming, spine, aft hatch and vent."""
    under_plan = [(-16.5, -12.7), (-2.1, -12.7), (-2.1, -11.75), (6.1, -11.75),
                  (6.1, 11.75), (-2.1, 11.75), (-2.1, 12.7), (-16.5, 12.7)]
    under = prism(kit, 'Hull deck seam underlay', under_plan, [(0, DECK-.02), (0, UNDER)], kit.dark, 0)
    bore = kit.cylinder('Temporary underlay bore', (*TURRET, DECK), RING[0], 2, kit.dark, 'Z', 32, 0)
    boolean(under, bore)
    for name, (x0, x1, y0, y1), m in DECK_PLATES:
        deck_plate(kit, name, x0, x1, y0, y1, UNDER, PLATE, faded if m == 'pale' else kit.orange)
    ring_solid(kit, 'Hull turret ring coaming', TURRET, RING[0], RING[1], DECK-.02, PLATE+.14, kit.steel)
    rr = (RING[0]+RING[1])/2
    blocks(kit, 'Hull turret ring coaming bolts',
           [bolt(TURRET[0]+rr*math.cos(a), TURRET[1]+rr*math.sin(a), PLATE+.14, .2, .1)
            for a in (k*math.tau/16 for k in range(16))], kit.steel)
    # Service covers on the spar plates: octagonal, bolted.
    for k, (x, y) in enumerate([(-1.0, -8.6), (-6.4, -9.2), (-6.4, 10.9)], 1):
        z = PLATE
        kit.cylinder(f'Hull deck service cover {k}', (x, y, z+.08), .85, .16, kit.steel, 'Z', 8, .04)
        blocks(kit, f'Hull deck service cover {k} bolts',
               [bolt(x+.62*math.cos(a), y+.62*math.sin(a), z+.16, .14, .08) for a in (j*math.tau/6 for j in range(6))], kit.steel)

    # Orange transverse straps at the sprite's bands, each with a bolt row.
    straps = [('forward', 3.4, 4.6, -11.6, 11.6, UNDER, STRAP),
              ('aft', -16.3, -14.8, -12.6, 12.6, UNDER, STRAP)]
    zh = seat(kit, 14.5, 0, 9.7)
    straps.append(('deckhouse', 14.0, 15.0, -9.5, 10.0, zh-.05, zh+.42))
    for label, x0, x1, y0, y1, z0, z1 in straps:
        deck_plate(kit, f'Hull {label} armour strap', x0, x1, y0, y1, z0, z1, kit.orange, .08)
        ys = [y0+.5+i*(y1-y0-1)/9 for i in range(10)]
        blocks(kit, f'Hull {label} armour strap bolts',
               [bolt(x, y, z1, .2, .1) for y in ys for x in (x0+.28, x1-.28)], kit.steel)

    # Aft cargo hatch: coaming frame, two leaves hinged on the spine beam.
    x0, x1, y0, y1 = HATCH
    w = .45
    frame = [(x0, x1, y0, y0+w), (x0, x1, y1-w, y1), (x0, x0+w, y0+w, y1-w), (x1-w, x1, y0+w, y1-w)]
    for i, (a, b, c, d) in enumerate(frame, 1):
        deck_plate(kit, f'Hull aft cargo hatch coaming {i}', a, b, c, d, UNDER, PLATE+.3, kit.steel, .06)
    leaves = [('starboard', (y0+w+.15, SPINE_Y[0]-.2), kit.rust), ('port', (SPINE_Y[1]+.2, y1-w-.15), faded)]
    for label, (ya, yb), m in leaves:
        deck_plate(kit, f'Hull aft cargo hatch leaf {label}', x0+w+.15, x1-w-.15, ya, yb, UNDER, PLATE-.04, m, .05)
        blocks(kit, f'Hull aft cargo hatch leaf {label} stiffeners',
               [(x-.15, x+.15, ya+.3, yb-.3, PLATE-.04, PLATE+.08) for x in (x0+1.6, (x0+x1)/2, x1-1.6)], kit.steel)

    # Dorsal spine: the sprite's light ridge, in segments with seam gaps.
    def spine(name, xa, xb, z0, z1, mat):
        sec = octagon(SPINE_Y[0], SPINE_Y[1], z0, z1, .3, .06)
        return loft_x(kit, name, [(xa, sec), (xb, sec)], mat, .05)
    pale = kit.spine_mat
    spine('Hull dorsal spine segment aft', -16.6, -14.75, PLATE-.05, PLATE+.6, pale)
    spine('Hull dorsal spine hatch beam', -14.5, -9.7, PLATE-.05, PLATE+.65, kit.steel)
    spine('Hull dorsal spine segment mid', -9.4, -1.5, PLATE-.05, PLATE+.6, pale)
    spine('Hull dorsal spine segment fore', -1.3, 5.95, PLATE-.05, PLATE+.6, pale)
    spine('Hull dorsal spine segment deckhouse', 6.9, 13.9, zh-.05, zh+.55, pale)
    blocks(kit, 'Hull dorsal spine hinge pins',
           [(x-.3, x+.3, SPINE_Y[0]-.25, SPINE_Y[1]+.25, PLATE+.1, PLATE+.42) for x in (-13.6, -12.1, -10.6)], kit.steel)

    # Aft deck vent (sprite's light blob, rows 53-58, cols 19-24).
    vx0, vx1, vy0, vy1 = -14.3, -10.1, 7.0, 11.6
    bx(kit, 'Hull aft deck vent well', vx0+.3, vx1-.3, vy0+.3, vy1-.3, UNDER, PLATE+.1, kit.dark, 0)
    vf = [(vx0, vx1, vy0, vy0+.3), (vx0, vx1, vy1-.3, vy1), (vx0, vx0+.3, vy0+.3, vy1-.3), (vx1-.3, vx1, vy0+.3, vy1-.3)]
    blocks(kit, 'Hull aft deck vent frame', [(a, b, c, d, UNDER, PLATE+.22) for a, b, c, d in vf], kit.steel)
    blocks(kit, 'Hull aft deck vent louvres',
           [(x-.14, x+.14, vy0+.3, vy1-.3, PLATE-.1, PLATE+.16) for x in [vx0+.75+i*.6 for i in range(5)]], kit.steel)


def deckhouse(kit, house):
    """Ribs, lamps, hatch panels, gutter and access hatch on the deckhouse."""
    for i, y in enumerate(HATCH_RIBS, 1):
        z = seat(kit, (RIB_X[0]+RIB_X[1])/2, y, 9.7)
        kit.box(f'Hull hold hatch rib {i}', ((RIB_X[0]+RIB_X[1])/2, y, z+.2), (RIB_X[1]-RIB_X[0], .55, .5), kit.cream, .08)
    for i, (x, y) in enumerate(LAMPS, 1):
        z = seat(kit, x, y, 9.7)
        bx(kit, f'Hull hold lamp {i} housing', x-.45, x+.45, y-.42, y+.42, z-.05, z+.3, kit.steel, .05)
        bx(kit, f'Hull hold lamp {i} lens', x-.28, x+.28, y-.26, y+.26, z+.25, z+.4, kit.amber, .03)
    k = 0
    for xa, xb in ((7.2, 10.3), (10.6, 13.7)):
        for ya, yb in ((-7.9, SPINE_Y[0]-.3), (SPINE_Y[1]+.3, 1.9)):
            k += 1
            z = seat(kit, (xa+xb)/2, (ya+yb)/2, 9.7)
            bx(kit, f'Hull hold hatch panel {k}', xa, xb, ya, yb, z-.05, z+.24, kit.hatch, .05)
            blocks(kit, f'Hull hold hatch panel {k} dogs',
                   [bolt(x, y, z+.24, .2, .08) for x in (xa+.3, xb-.3) for y in (ya+.3, yb-.3)], kit.steel)
    z = seat(kit, 10.4, -8.9, 9.7)
    bx(kit, 'Hull hold drain gutter', 7.0, 13.8, -9.5, -8.3, z-.02, z+.04, kit.dark, 0)
    # Crew access hatch on the deckhouse's aft wall, facing the turret.
    bx(kit, 'Hull deckhouse aft access hatch frame', 5.8, 6.1, -.2, 3.0, 6.6, 8.7, kit.steel, .04)
    bx(kit, 'Hull deckhouse aft access hatch', 5.7, 5.95, .1, 2.7, 6.85, 8.45, kit.rust, .04)
    blocks(kit, 'Hull deckhouse aft access hatch dogs',
           [bolt(5.7, y, z, .2, -.1, 'X') for y in (.4, 2.4) for z in (7.15, 8.15)], kit.steel)
    # Louvred side vents on the deckhouse walls.
    for s, label in ((1, 'port'), (-1, 'starboard')):
        w = 11.0 if s > 0 else 10.5
        y0, y1 = sorted((s*(w-.1), s*(w+.12)))
        bx(kit, f'Hull deckhouse side vent {label}', 8.0, 12.0, y0, y1, 6.9, 8.3, kit.dark, 0)
        blocks(kit, f'Hull deckhouse side vent {label} louvres',
               [(8.1, 11.9, *sorted((s*(w+.05), s*(w+.2))), z-.08, z+.08) for z in (7.15, 7.6, 8.05)], kit.steel)


def coolant_loop(kit):
    """The sprite's gold pipe arc in front of the deckhouse (rows 25-28),
    running down the starboard edge (col 42, rows 29-34)."""
    zc = DECK+.45
    pts = [(18.95, 5.4, DECK-.1), (18.95, 5.4, zc-.25), (18.95, 5.05, zc), (18.95, -8.6, zc),
           (18.75, -9.35, zc), (17.35, -10.75, zc), (16.6, -10.95, zc), (10.5, -10.95, zc),
           (10.15, -10.95, zc-.2), (10.1, -10.95, DECK-.1)]
    tube(kit, 'Hull coolant loop pipe', pts, .34, kit.brass, 8)
    for k, (x, y) in enumerate([(18.95, 5.4), (10.1, -10.95)], 1):
        kit.cylinder(f'Hull coolant loop deck flange {k}', (x, y, DECK+.08), .58, .16, kit.steel, 'Z', 8, .03)
    clamps = [(18.95, 2.0, 'Y'), (18.95, -2.6, 'Y'), (18.95, -7.6, 'Y'), (14.6, -10.95, 'X'), (12.2, -10.95, 'X')]
    for k, (x, y, along) in enumerate(clamps, 1):
        dims = (.95, .32, .9) if along == 'Y' else (.32, .95, .9)
        kit.box(f'Hull coolant loop bracket {k}', (x, y, DECK+.4), dims, kit.steel, .05)
    kit.cylinder('Hull coolant loop valve', (18.95, -4.6, zc), .5, .9, kit.brass, 'Y', 8, .04)
    kit.cylinder('Hull coolant loop valve wheel', (18.95, -4.6, zc+.75), .42, .1, kit.steel, 'Z', 8, 0)


def stern(kit, body):
    """Recessed stern bulkhead with clamshell cargo doors either side of the
    flare dispenser (WS 003 at (-19, 0, -0.5)); centre left clear."""
    yr, z0, z1 = 5.7, -2.9, 4.4
    cut = bx(kit, 'Temporary stern recess', -18.6, -17.6, -yr, yr, z0, z1, kit.dark, 0)
    boolean(body, cut)
    # The sprite's dark stern band: the recess back wall.
    bx(kit, 'Hull aft bulkhead band', -17.9, -17.55, -yr, yr, z0, z1, kit.dark, 0)
    bx(kit, 'Hull stern door header', -18.4, -17.85, -6.2, 6.2, 2.3, 2.9, kit.steel, .05)
    bx(kit, 'Hull stern door sill', -18.3, -17.85, -yr, yr, z0, -2.6, kit.steel, .04)
    blocks(kit, 'Hull stern transom louvres',
           [(-18.0, -17.85, -yr+.2, yr-.2, z-.1, z+.1) for z in (3.25, 3.65, 4.05)], kit.steel)
    for s, label in ((1, 'port'), (-1, 'starboard')):
        ya, yb = sorted((s*2.3, s*5.45))
        bx(kit, f'Hull stern cargo door {label}', -18.12, -17.88, ya, yb, -2.5, 2.2, kit.steel, .04)
        blocks(kit, f'Hull stern cargo door {label} stiffeners',
               [(-18.24, -18.1, y-.14, y+.14, -2.3, 2.0) for y in (s*3.2, s*4.4)]
               + [(-18.24, -18.1, ya+.15, yb-.15, z-.12, z+.12) for z in (-1.3, .9)], kit.steel)
        for k, z in enumerate((-1.7, .1, 1.7), 1):
            kit.cylinder(f'Hull stern cargo door {label} hinge {k}', (-18.2, s*5.52, z), .2, .6, kit.dark, 'Z', 8, 0)
        # The sprite's dark band runs on above the skids to Y +-9.
        ya, yb = sorted((s*5.85, s*9.2))
        bx(kit, f'Hull stern bulkhead wing {label}', -18.08, -17.95, ya, yb, 2.45, 4.4, kit.dark, 0)


def skids(kit):
    for side, plan in (('port', SKID_PLAN), ('starboard', mirror_y(SKID_PLAN))):
        skid = prism(kit, f'Hull stern skid {side}', plan, SKID, kit.rust)
        plane_cut(skid, f'Skid {side} aft rake', (-24, 0, -1), facet_normal((-1, 0), 130))
        (x0, x1), (y0, y1) = SKID_SLOT
        sy = 1 if side == 'port' else -1
        cut = kit.box('Temporary skid slot', ((x0+x1)/2, sy*(y0+y1)/2, 0), (x1-x0, y1-y0, 20), kit.dark, 0)
        boolean(skid, cut)
        yc = sy*(y0+y1)/2
        # Landing rams either side of the U slot (the slot stays open, as the
        # sprite shows) and the foot pad they carry under the skid.
        for k, y in enumerate((sy*7.3, sy*11.6), 1):
            kit.cylinder(f'Hull stern skid {side} landing ram {k}', (-20.3, y, KEEL-.15), .38, 1.3, kit.steel, 'Z', 8, .03)
        # Two runners, so the slot stays open from above.
        for part, (ya, yb) in (('outer', (9.75, 13.4)), ('inner', (6.6, 8.05))):
            pad = octagon(*sorted((sy*ya, sy*yb)), KEEL-1.25, KEEL-.55, .3, .25)
            loft_x(kit, f'Hull stern skid {side} foot pad {part}', [(-22.8, pad), (-18.2, pad)], kit.steel, .05)
        # Green-grey RCS blocks on the skid's aft top (sprite rows 66-69).
        for k, (ya, yb) in enumerate((sorted((sy*6.4, sy*7.9)), sorted((sy*10.0, sy*13.4))), 1):
            bx(kit, f'Hull stern skid {side} RCS block {k}', -23.7, -21.2, ya, yb, 1.6, 3.0, kit.rcs, .08)
            kit.cylinder(f'Hull stern skid {side} RCS nozzle {k} aft', (-23.75, (ya+yb)/2, 2.3), .32, .3, kit.dark, 'X', 8, 0)
        kit.cylinder(f'Hull stern skid {side} RCS nozzle outboard', (-22.4, sy*13.45, 2.3), .32, .3, kit.dark, 'Y', 8, 0)
        # Seam dividing the skid into its leg and foot modules: a cut over a
        # dark core.
        cut = bx(kit, 'Temporary skid seam', -19.2, -18.9, -20, 20, -10, 10, kit.dark, 0)
        boolean(skid, cut)
        blocks(kit, f'Hull stern skid {side} seam core',
               [(-19.25, -18.85, *sorted((sy*6.15, sy*8.2)), KEEL+2.1, 1.1),
                (-19.25, -18.85, *sorted((sy*9.6, sy*13.85)), KEEL+2.1, 1.1)], kit.dark)


def underside(kit):
    """Plated keel, keel beam, ventral hatch, vents and forward pads."""
    plan = [(-15.9, -12.0), (-2.0, -12.0), (-2.0, -11.0), (17.2, -11.0), (17.2, 11.0), (-2.0, 11.0), (-2.0, 12.0), (-15.9, 12.0)]
    prism(kit, 'Hull keel seam underlay', plan, [(0, KEEL-.06), (0, KEEL+.02)], kit.dark, 0)
    zp = (KEEL-.36, KEEL-.06)
    k = 0
    for s, label in ((1, 'port'), (-1, 'starboard')):
        for xa, xb in KEEL_PLATES:
            for ya, yb in KEEL_ROWS:
                k += 1
                y0, y1 = sorted((s*ya, s*yb))
                bx(kit, f'Hull keel plate {k}', xa, xb, y0, y1, *zp, kit.orange, .05)
        # Inner row of the aftmost bay.
        k += 1
        y0, y1 = sorted((s*1.5, s*5.9))
        bx(kit, f'Hull keel plate {k}', -15.8, -10.4, y0, y1, *zp, kit.orange, .05)
        # Ventral vent in the outer aft bay.
        y0, y1 = sorted((s*6.2, s*10.7))
        bx(kit, f'Hull ventral vent {label} well', -15.6, -10.6, y0, y1, KEEL-.2, KEEL, kit.dark, 0)
        blocks(kit, f'Hull ventral vent {label} louvres',
               [(x-.16, x+.16, y0+.2, y1-.2, KEEL-.42, KEEL-.1) for x in [-15.1+i*.7 for i in range(7)]], kit.steel)
        # Plates outboard of the ventral hatch.
        x0, x1, h = VENTRAL_HATCH
        y0, y1 = sorted((s*(h+.2), s*10.8))
        bx(kit, f'Hull keel plate ventral hatch {label}', x0, x1, y0, y1, *zp, kit.orange, .05)
        # Forward landing pad on a short ram.
        kit.cylinder(f'Hull forward landing ram {label}', (14.6, s*7.6, KEEL-.6), .4, .6, kit.steel, 'Z', 8, .02)
        kit.cylinder(f'Hull forward landing pad {label}', (14.6, s*7.6, KEEL-1.05), 1.25, .4, kit.steel, 'Z', 8, .05)
    # Exposed transverse keel frames along the plate seams.
    blocks(kit, 'Hull keel frames',
           [(x-.16, x+.16, -10.8, 10.8, KEEL-.5, KEEL-.06) for x in (-10.3, -4.5, 7.8, 12.7)], kit.steel)
    beam = octagon(-1.3, 1.3, KEEL-.95, KEEL+.05, .06, .45)
    loft_x(kit, 'Hull keel beam aft', [(-16.4, beam), (1.4, beam)], kit.steel, .05)
    loft_x(kit, 'Hull keel beam fore', [(7.9, beam), (16.0, beam)], kit.steel, .05)
    x0, x1, h = VENTRAL_HATCH
    w = .4
    frame = [(x0, x1, -h, -h+w), (x0, x1, h-w, h), (x0, x0+w, -h+w, h-w), (x1-w, x1, -h+w, h-w)]
    blocks(kit, 'Hull ventral cargo hatch coaming', [(a, b, c, d, KEEL-.55, KEEL) for a, b, c, d in frame], kit.steel)
    for label, (ya, yb) in (('port', (.1, h-w-.1)), ('starboard', (-h+w+.1, -.1))):
        bx(kit, f'Hull ventral cargo hatch leaf {label}', x0+w+.1, x1-w-.1, ya, yb, KEEL-.3, KEEL, kit.rust, .04)
    # Stringers under the spar ends where they leave the body.
    for s, label in ((1, 'port'), (-1, 'starboard')):
        y0, y1 = sorted((s*13.2, s*14.4))
        bx(kit, f'Hull spar underside rib {label}', -6.5, 3.5, y0, y1, -3.9, -3.5, kit.steel, .04)


def build(kit):
    kit.brass = material('Hull coolant brass', (.62, .42, .12), .85, .35)
    kit.amber = material('Hull amber lamp', (.35, .16, .03), 0, .3, emission=(1.0, .38, .04), strength=3.5)
    kit.hatch = material('Hull hold hatch brown', (.25, .13, .075), .35)
    kit.rcs = material('Hull RCS green-grey', (.15, .18, .145), .5)
    kit.spine_mat = material('Hull spine pale steel', (.36, .32, .29), .5)
    faded = material('Hull faded oxide armour', (.56, .25, .10), .3)

    body = prism(kit, 'Hull cargo body', BODY_PLAN, BODY, kit.orange)
    # Forward face of the body sits behind the bridge capsule and gun fork;
    # a raked chin keeps the keel from reading as a brick in side view.
    plane_cut(body, 'Hull chin rake', (21, 0, KEEL+3), facet_normal((1, 0), 140))
    house = prism(kit, 'Hull shielded hold deckhouse', HOUSE_PLAN, HOUSE, kit.rust)
    plane_cut(house, 'Deckhouse forward rake', (18.5, 0, 7.8), facet_normal((1, 0), 35))
    spar = prism(kit, 'Hull engine spar', SPAR_PLAN, SPAR, kit.orange)
    kit.hull = [body, house, spar]

    flanks(kit, body)
    stern(kit, body)
    deckhouse(kit, house)
    deck(kit, faded)
    coolant_loop(kit)
    skids(kit)
    underside(kit)
    kit.hull = [body, house, spar]
    return body
