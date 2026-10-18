"""The Lasher's structure: bow head, nose cap, spine, wing body, engine nacelles.

Read off lasher_base.png (68x96, .ship centre (34, 35) from the bottom edge;
sprite column c, row r covers X 60-r..61-r and Y 33-c..34-c):
- Bow head (rows 12-31): a shield-shaped armoured head, widest (Y +-16.5)
  at X 42, carrying the WS 007 turret on a raised orange ring (the ring
  round the turret, rows 16-31) and gunmetal machinery pads at its
  shoulders (cols 18-24).
- Nose cap (rows 1-12): the gunmetal prow forward of the head, raked top
  and chin, with two dark sensor wells (rows 5-6).
- Spine (rows 31-94): the central keel structure. Y +-9.5 through the neck
  between the gun pods, pinched to +-4.5 between the two waist turrets (the
  lighter centre stripe), swelling to +-9 aft where it houses the main
  drive (Engine 1) and ends at X -30.
- Wing body (rows 45-79): the broad orange armoured body the waist turrets
  stand on, out to Y +-33.5 under the missile pods, with an outboard armour
  strake (the orange stripes, rows 57-72).
- Engine nacelles (rows 69-89): gunmetal machinery blocks either side of the
  spine aft, whose aft faces carry the four small drives.

Pass 2 (light; the hull is a distant escort): the bridge viewport arc and
shoulder viewports exactly where lasher_lights.png lights them, the spine's
two flank viewports, the aft heat-vent grilles glowing at the lights layer's
four aft streaks, the spine ridge, a few dark seam bands and vent recesses.
"""
import math
import bmesh
import bpy
from ls_kit import facet_normal, plane_cut, prism, section, sym

HEAD = sym([(49.5, 14), (45, 15), (42, 16), (35.5, 16), (34, 14), (33, 12), (31, 10)])
NOSE = sym([(60.5, 1.2), (59, 3.6), (57, 6.6), (53, 10.6), (50, 11.2), (49.3, 14), (48, 14)])
SPINE = sym([(31.5, 9.5), (17, 9.5), (14, 4.5), (-3, 4.5), (-7, 9), (-27, 9), (-31, 8), (-32.6, 6)])
WING = sym([(16.5, 4), (16.5, 20), (14, 21), (6, 21.5), (5, 32.3), (-1, 32.3), (-2.5, 33), (-10, 33),
            (-13.5, 31.6), (-18, 28), (-19, 24), (-12, 17), (-10, 4)])
NACELLE = [(-6, 8.5), (-29.2, 8.5), (-29.2, 14), (-27, 17.5), (-24.5, 22.5), (-21, 25),
           (-16, 29.5), (-11, 31), (-7, 22)]
WING_SLOPE = (12.0, .15)
# Human fittings in true metres, converted with the hull's scale (kit.mpu).
VIEWPORT_M = (1.0, .8)       # one viewport pane, fore-aft x athwartships
BRIDGE_BAND_M = 1.0          # the bridge viewport arc's glazed band  # the wing's upper surface falls outboard of |Y| 12 at 0.15 per unit

HEAD_DECK = 6.5
RING_TOP = 7.8
SPINE_DECK = 8.5
WING_DECK = 5.0
NACELLE_DECK = 7.0


def ccw(poly):
    area = sum(x0*y1-x1*y0 for (x0, y0), (x1, y1) in zip(poly, poly[1:]+poly[:1]))
    return poly if area > 0 else list(reversed(poly))


def mirror(poly):
    return ccw([(x, -y) for x, y in poly])


def strip(kit, name, points, width, z, height, mat):
    """A thin raised ribbon along a polyline in plan, its base at z."""
    verts, faces = [], []
    n = len(points)
    for i, (x, y) in enumerate(points):
        ax, ay = points[max(i-1, 0)]
        bx, by = points[min(i+1, n-1)]
        dx, dy = bx-ax, by-ay
        length = math.hypot(dx, dy)
        nx, ny = -dy/length*width/2, dx/length*width/2
        verts += [(x+nx, y+ny, z), (x-nx, y-ny, z), (x-nx, y-ny, z+height), (x+nx, y+ny, z+height)]
    for i in range(n-1):
        a, b = 4*i, 4*(i+1)
        faces += [(a, a+1, b+1, b), (a+1, a+2, b+2, b+1), (a+2, a+3, b+3, b+2), (a+3, a, b, b+3)]
    faces += [(0, 1, 2, 3), tuple(reversed(range(4*(n-1), 4*n)))]
    return kit.mesh(name, verts, faces, mat)


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


def seat(kit, x, y, fallback):
    hit = kit.surface(x, y)
    return hit[0].z if hit else fallback


def build(kit):
    head = prism(kit, 'Hull bow head', ccw(HEAD), section(-6, HEAD_DECK, 2.0, 2.2), kit.orange)
    nose = prism(kit, 'Hull nose cap', ccw(NOSE), section(-6, HEAD_DECK, 2.0, 2.2), kit.steel)
    # Raked prow: the top falls from the head deck to the tip, the chin rises.
    plane_cut(nose, 'nose top rake', (49, 0, HEAD_DECK), facet_normal((1, 0), 21))
    n = facet_normal((1, 0), 24)
    plane_cut(nose, 'nose chin rake', (51, 0, -6), (n[0], 0, -n[2]))
    for s in (1, -1):  # cheek facets on the nose
        plane_cut(nose, f'nose {s} cheek', (52, s*9, HEAD_DECK-1), facet_normal((.5, s), 40))
    spine = prism(kit, 'Hull spine', ccw(SPINE), section(-6.5, SPINE_DECK, 2.0, 2.4), kit.orange)
    plane_cut(spine, 'spine aft rake', (-25, 0, SPINE_DECK), facet_normal((-1, 0), 30))
    wing = shear(prism(kit, 'Hull wing body', ccw(WING), section(-3.5, WING_DECK, 1.2, 1.4), kit.orange), True)
    nac = {}
    for side, plan in (('port', ccw(NACELLE)), ('starboard', mirror(NACELLE))):
        nac[side] = prism(kit, f'Hull {side} engine nacelle', plan, section(-6, NACELLE_DECK, 2.0, 2.2), kit.steel)
        s = 1 if side == 'port' else -1
        plane_cut(nac[side], f'{side} nacelle aft rake', (-22, s*20, NACELLE_DECK), facet_normal((-1, 0), 38))
        plane_cut(nac[side], f'{side} nacelle outer shoulder', (-15, s*21, NACELLE_DECK), facet_normal((0, s), 32))
    ring = kit.cylinder('Hull bow turret ring', (37, 0, (HEAD_DECK+RING_TOP)/2-.5), 7.4, RING_TOP-HEAD_DECK+1,
                        kit.orange, vertices=16, bevel=.25)
    for s in (1, -1):  # the head's shoulders fall away either side of the turret ring: a dome
        plane_cut(head, f'head {s} shoulder', (38, s*9.5, HEAD_DECK), facet_normal((0, s), 28))
        plane_cut(nose, f'nose {s} shoulder', (50, s*9.5, HEAD_DECK), facet_normal((0, s), 28))
    # Ventral keel under the spine: depth for the side profile.
    keel = prism(kit, 'Hull ventral keel', ccw(sym([(27, 5.5), (-22, 5.5), (-26, 4)])),
                 section(-9.5, -5, 1.6, 0), kit.steel)
    kit.hull += [head, nose, spine, wing, ring, *nac.values()]

    # --- Pass 2: plates, pads, glazing, vents. ---
    vp = [m/kit.mpu for m in VIEWPORT_M]
    for side, s in (('port', 1), ('starboard', -1)):
        pad = ccw([(45, s*10.5), (44.5, s*15), (40, s*16), (34, s*15.2), (33, s*11.5), (38, s*10)])
        kit.hull.append(prism(kit, f'Hull bow {side} machinery pad', pad, section(HEAD_DECK-4.5, HEAD_DECK-.2, .3, .6),
                              kit.steel, .1))
        kit.box(f'Hull bow {side} pad vent', (39, s*13.2, HEAD_DECK-.15), (5, 1.6, .2), kit.dark, 0)
        strake = ccw([(4, s*31.8), (4, s*22), (-9, s*20), (-12.5, s*31.6)])
        shear(prism(kit, f'Hull wing {side} armour strake', strake, section(WING_DECK-.5, WING_DECK+.55, .2, .4),
                    kit.orange, .1))
        for x in (-1.5, -6.5):  # strake joints
            shear(kit.box(f'Hull wing {side} strake seam {x:+.1f}', (x, s*26.5, WING_DECK+.56), (.35, 10.5, .1),
                          kit.dark, 0))
        # Nacelle top: dark vent wells and two raised ribs (the sprite's grey stacks).
        kit.box(f'Hull {side} nacelle vent well', (-14.5, s*15.5, NACELLE_DECK+.02), (7, 3.6, .3), kit.dark, 0)
        for k, y in enumerate((12.5, 18.5), 1):
            kit.box(f'Hull {side} nacelle rib {k}', (-15, s*y, NACELLE_DECK+.35), (12, 1.1, .8), kit.steel, .1)
        # Shoulder viewports on the machinery pads (lights layer r17/20/23).
        for k, (x, y) in enumerate(((44, 8.5), (41, 10.5), (38, 12.5)), 1):
            z = seat(kit, x, s*y, HEAD_DECK)
            kit.box(f'Hull bow {side} viewport {k}', (x, s*y, z), (*vp, .25), kit.glass, 0)
        # Spine flank viewports (lights layer rows 36-37, cols 28/38).
        kit.box(f'Hull spine {side} viewport', (24.5, s*5.2, SPINE_DECK+.02), (*vp, .2), kit.glass, 0)
        # Aft heat-vent grille: dark well with two glowing slats (lights rows 72-82).
        kit.box(f'Hull spine {side} heat vent well', (-16, s*2.6, SPINE_DECK-.1), (10.5, 2.6, .5), kit.dark, 0)
        for k, y in enumerate((1.9, 3.3), 1):
            kit.box(f'Hull spine {side} heat vent slat {k}', (-16, s*y, SPINE_DECK+.1), (9.5, .35, .2), kit.vent, 0)
    # Spine ridge (the lighter centre stripe) and its seam bands.
    kit.box('Hull spine ridge', (11.5, 0, SPINE_DECK+.3), (37, 2.6, .7), kit.orange, .15)
    for x in (17.2, -3.2, -20.5):
        kit.box(f'Hull spine seam {x:+.1f}', (x, 0, SPINE_DECK+.05), (.4, 9, .2), kit.dark, 0)
    # Bridge viewport arc across the head, forward of the bow turret (lights rows 12-15).
    arc = [(44.0+5.3*math.cos(a), 5.3*math.sin(a)) for a in [math.radians(d) for d in range(-70, 71, 14)]]
    strip(kit, 'Hull bridge viewport arc', arc, BRIDGE_BAND_M/kit.mpu, HEAD_DECK-.15, .45, kit.glass)
    strip(kit, 'Hull bridge visor brow', [(x+.7, y*1.1) for x, y in arc], .5, HEAD_DECK, .8, kit.steel)
    # Nose sensor wells (rows 5-6, cols 29-32 and 36-39).
    for s in (1, -1):
        z = seat(kit, 55.5, s*2.6, HEAD_DECK)
        kit.box(f'Hull nose sensor well {"port" if s > 0 else "starboard"}', (55.5, s*2.6, z), (1.6, 2.4, .5),
                kit.dark, 0)
