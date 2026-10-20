"""Round 13: refined detail across the hull and the command deck.

Owner, round 12: "Manti-core looks good. Let's continue with refined detail
across the hull and command deck." The round-12 forms are accepted; this
module only lays detail on them, seated by ray-casts on the hull parts and
sized in true metres (`kit.mpu`), so it reads at a 91 m ship's scale:
- Command deck: (round 14: the glazing's mullions and sill moved to mt_detail) a second mast
  with a radar bar, yards, whip antennas and beacons, two comms dishes on
  pedestals, two roof access hatches aft of the dome, a walkway either side
  of it with 1.1 m rails, and an aft rail.
- Hull: plate seams following the forms (core shoulders and aft deck, the
  glacis bands, the wing armour, the pod collars and the beak strakes);
  pirate patch plates with weld beads, placed unevenly; crew doors on the
  core's flanks into the wing framework with landings; deck hatches; RCS
  clusters at the bow, the nacelles and the engine block's corners; finned
  heat exchangers on the engine block's shoulders; pipe runs joining each
  wing tank to its shoulder machinery bay.
"""
import math
import bpy
from mathutils import Vector
from mt_kit import material
from mt_hull import BRIDGE_DECK, POD
from mt_frames import struts

DOOR_M = (1.0, 2.0)          # crew door, width x height
HATCH_M = 1.2                # deck hatch diameter
RAIL_M = (1.1, 1.5)          # rail height, post pitch
COMMS_M = 1.6               # comms dish diameter
RIM = .05                    # how far detail stands proud of the surface it is seated on


def hit(kit, x, y):
    h = kit.surface(x, y)
    return (h[0], h[1]) if h else (Vector((x, y, 0)), Vector((0, 0, 1)))


def ribbon(kit, name, pts, width, height, mat, closed=False):
    """A thin raised strip laid on the hull along plan points, following its surface."""
    seated = [hit(kit, x, y) for x, y in pts]
    if closed:
        seated.append(seated[0])
    verts, faces = [], []
    n = len(seated)
    for i, (p, nrm) in enumerate(seated):
        a = seated[max(i-1, 0)][0]
        b = seated[min(i+1, n-1)][0]
        d = (b-a).normalized()
        side = nrm.cross(d).normalized()*width/2
        base = p-nrm*.05
        top = p+nrm*height
        verts += [tuple(base+side), tuple(base-side), tuple(top-side), tuple(top+side)]
    for i in range(n-1):
        a, b = 4*i, 4*(i+1)
        faces += [(a, a+1, b+1, b), (a+1, a+2, b+2, b+1), (a+2, a+3, b+3, b+2), (a+3, a, b, b+3)]
    faces += [(0, 1, 2, 3), tuple(reversed(range(4*(n-1), 4*n)))]
    return kit.mesh(name, verts, faces, mat, 0)


def line(a, b, step=.8):
    n = max(2, int(math.hypot(b[0]-a[0], b[1]-a[1])/step)+1)
    return [(a[0]+(b[0]-a[0])*k/(n-1), a[1]+(b[1]-a[1])*k/(n-1)) for k in range(n)]


def patch(kit, name, cx, cy, w, h, ang, mat, bead):
    """A patch plate seated corner by corner, with a weld bead round its edge."""
    c, s = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    corners = [(cx+c*dx-s*dy, cy+s*dx+c*dy) for dx, dy in ((-w/2, -h/2), (w/2, -h/2), (w/2, h/2), (-w/2, h/2))]
    seated = [hit(kit, x, y) for x, y in corners]
    nrm = sum((n for _, n in seated), Vector()).normalized()
    bottom = [p-nrm*.05 for p, _ in seated]
    top = [p+nrm*.18 for p, _ in seated]
    verts = [tuple(v) for v in bottom+top]
    faces = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    kit.mesh(name, verts, faces, mat, 0)
    edge = []
    for a, b in zip(corners, corners[1:]+corners[:1]):
        edge += line(a, b, .6)[:-1]
    ribbon(kit, name+' weld bead', edge, .22, .26, bead, closed=True)


def rcs(kit, name, x, y, s, out):
    """A reaction-control quad: a block on the hull with nozzles out, up and fore-and-aft."""
    p, _ = hit(kit, x, y)
    kit.box(name+' block', (x, y, p.z+.45), (1.8, 1.8, 1.1), kit.steel, .1)
    z = p.z+.5
    segs = [((x, y+s*.85, z), (x, y+s*1.5, z), .32, .32, 8),
            ((x, y, z+.5), (x, y, z+1.1), .32, .32, 8),
            ((x+.85*out, y, z), (x+1.5*out, y, z), .32, .32, 8)]
    struts(kit, name+' nozzles', segs, kit.dark)


def command_deck(kit, beacon):
    u = 1/kit.mpu
    # (Round 14: the glazing's mullions and sill are mt_detail's, recessed into the dome.)
    # Roof: a second mast aft with a radar bar and two yards, whip antennas,
    # two comms dishes on pedestals, and a beacon on each mast.
    # Round 14 (owner: "strange low poly radar dish and light pole"): tapered
    # masts on bolted, gusseted flanges with beacon housings (mt_fittings), a
    # forward light pole and the aft mast with its yards, array and whips, and
    # the comms dishes as real reflectors on yokes.
    from mt_fittings import dish, light_mast, taper
    roof = hit(kit, -15.5, -5)[0].z
    light_mast(kit, 'Bridge light pole', (-18.6, 0), hit(kit, -18.6, 0)[0].z, 3.0*u, kit.steel, beacon,
               .2, .1, 'Bridge mast beacon 1')
    top = light_mast(kit, 'Bridge aft mast', (-15.5, -5), roof, 7.4, kit.steel, beacon, .34, .16, 'Bridge mast beacon 2')
    for k, (zz, half) in enumerate(((roof+5.0, 2.2), (roof+6.3, 1.6)), 1):
        taper(kit, f'Bridge aft mast yard {k}', (-15.5, -5-half, zz), (-15.5, -5+half, zz), .07, .07, kit.steel, 16)
    kit.smooth(kit.box('Bridge aft mast radar bar', (-15.5, -5, top-.55), (.55, 5.4, .42), kit.plate, .14), segments=3)
    kit.smooth(kit.box('Bridge aft mast radar bar mount', (-15.5, -5, top-.9), (.4, .5, .4), kit.steel, .08), segments=2)
    for k, (x, y) in enumerate(((-15.5, 7.5), (-5.0, -7.0), (-5.0, 7.0)), 1):
        z = hit(kit, x, y)[0].z
        kit.smooth(kit.cylinder(f'Bridge whip {k} insulator', (x, y, z+.25), .14, .5, kit.dark, vertices=32, bevel=.04), segments=2)
        taper(kit, f'Bridge whip {k}', (x, y, z+.5), (x, y, z+4.4), .055, .018, kit.steel, 16)
    for k, (x, y) in enumerate(((-7.0, -4.6), (-17.0, 5.2)), 1):
        z = hit(kit, x, y)[0].z
        dish(kit, f'Bridge comms dish {k}', (x, y), z, COMMS_M/2*u, 90 if y > 0 else -90, 35, kit.plate, kit.steel,
             pedestal=.35)
    # Roof access hatches aft of the dome, and walkways with rails either side of it.
    r = HATCH_M*u/2
    deck = BRIDGE_DECK
    for k, y in enumerate((5.5, -5.5), 1):
        kit.smooth(kit.cylinder(f'Bridge roof hatch {k} rim', (-22.5, y, deck+.12), r+.2, .3, kit.steel, vertices=40, bevel=.05), segments=2)
        kit.smooth(kit.cylinder(f'Bridge roof hatch {k} lid', (-22.5, y, deck+.3), r-.05, .16, kit.plate, vertices=40, bevel=.04), segments=2)
        kit.box(f'Bridge roof hatch {k} hinge', (-22.5-r, y, deck+.32), (.3, 1.0, .25), kit.steel, .02)
    rail_h, pitch = RAIL_M[0]*u, RAIL_M[1]*u
    rails = []
    for s in (1, -1):
        kit.box(f'Bridge walkway {"port" if s > 0 else "starboard"}', (-12.5, s*12.8, deck+.08), (22.0, 2.0, .16),
                kit.dark, 0)
        xs = [-1.5-pitch*k for k in range(int(22/pitch)+1)]
        for x in xs:
            rails.append(((x, s*13.7, deck), (x, s*13.7, deck+rail_h), .07, .07, 12))
        rails += [((xs[0], s*13.7, deck+rail_h), (xs[-1], s*13.7, deck+rail_h), .08, .08, 12),
                  ((xs[0], s*13.7, deck+rail_h/2), (xs[-1], s*13.7, deck+rail_h/2), .06, .06, 12)]
    ys = [13.7-pitch*k for k in range(int(27.4/pitch)+1)]
    for y in ys:
        rails.append(((-24.6, y, deck), (-24.6, y, deck+rail_h), .07, .07, 12))
    rails.append(((-24.6, ys[0], deck+rail_h), (-24.6, ys[-1], deck+rail_h), .08, .08, 12))
    kit.smooth(struts(kit, 'Bridge rails', rails, kit.steel), .015, 1)


SEAMS = [  # (name, plan points): port side, mirrored; the forms' plate breaks
    ('core shoulder 1', [(-3.0, 17.0), (-3.0, 24.5)]), ('core shoulder 2', [(-14.0, 17.0), (-14.0, 24.5)]),
    ('core shoulder 3', [(-26.0, 17.0), (-26.0, 24.5)]), ('aft deck', [(-31.8, 1.0), (-31.8, 15.0)]),
    ('aft deck end', [(-51.5, 1.0), (-51.5, 15.0)]), ('glacis 1', [(19.5, 33.0), (31.2, 33.0)]),
    ('glacis 2', [(19.5, 53.0), (30.0, 53.0)]), ('wing armour', [(16.0, 46.0), (21.5, 46.0)]),
    ('wing armour root', [(3.5, 22.0), (12.5, 31.2)]), ('pod collar', [(11.3, 69.0), (11.3, 76.0)]),
    ('beak strake 1', [(55.0, 12.5), (55.0, 19.5)]), ('beak strake 2', [(70.0, 8.5), (70.0, 21.5)]),
]
PATCHES = [  # (x, y, w, h, deg): placed unevenly, as a pirate yard would weld them
    (24.0, 29.5, 3.5, 2.8, 15), (20.5, -53.0, 2.4, 2.0, -10), (18.5, 51.0, 2.4, 2.2, 5), (11.0, -31.0, 3.0, 2.4, 20),
    (8.3, -73.0, 3.0, 2.0, 0), (62.0, 14.0, 3.4, 2.4, -15), (-54.0, -12.0, 3.5, 2.4, 10), (52.0, -11.0, 2.6, 2.0, 0),
]


def hull_detail(kit, beacon):
    u = 1/kit.mpu
    for name, pts in SEAMS:
        for s, side in ((1, 'port'), (-1, 'starboard')):
            pts2 = []
            for a, b in zip(pts, pts[1:]):
                pts2 += line((a[0], s*a[1]), (b[0], s*b[1]))
            ribbon(kit, f'Hull seam {side} {name}', pts2, .22, RIM, kit.dark)
    for k, (x, y, w, h, ang) in enumerate(PATCHES, 1):
        patch(kit, f'Hull patch plate {k}', x, y, w, h, ang, kit.red, kit.steel)
    # Crew doors on the core's flanks, opening into the wing framework, each with a landing.
    dw, dh = DOOR_M[0]*u, DOOR_M[1]*u
    for s, side in ((1, 'port'), (-1, 'starboard')):
        for k, x in enumerate((-6.0, -13.0), 1):
            z0 = 2.5
            kit.box(f'Hull core {side} crew door {k}', (x, s*24.95, z0+dh/2), (dw, .3, dh), kit.dark, 0)
            fy = s*25.12
            frame = [((x-dw/2-.15, fy, z0), (x-dw/2-.15, fy, z0+dh+.15), .15, .12),
                     ((x+dw/2+.15, fy, z0), (x+dw/2+.15, fy, z0+dh+.15), .15, .12),
                     ((x-dw/2-.15, fy, z0+dh+.15), (x+dw/2+.15, fy, z0+dh+.15), .15, .12)]
            struts(kit, f'Hull core {side} crew door {k} frame', frame, kit.steel)
            kit.box(f'Hull core {side} crew door {k} landing', (x, s*26.4, z0-.12), (dw+1.2, 2.8, .24), kit.steel, .03)
    # Deck hatches on the forward deck and the aft deck.
    r = HATCH_M*u/2
    for k, (x, y) in enumerate(((40.0, 7.0), (40.0, -7.0), (-54.0, 0.0)), 1):
        p, _ = hit(kit, x, y)
        kit.cylinder(f'Hull deck hatch {k} rim', (x, y, p.z+.1), r+.2, .3, kit.steel, vertices=16, bevel=.04)
        kit.cylinder(f'Hull deck hatch {k} lid', (x, y, p.z+.25), r-.05, .14, kit.plate, vertices=16, bevel=.03)
    # RCS clusters at the bow, the nacelles and the engine block's corners.
    for s, side in ((1, 'port'), (-1, 'starboard')):
        rcs(kit, f'Hull RCS {side} bow', 78.0, s*15.5, s, 1)
        rcs(kit, f'Hull RCS {side} nacelle', 13.0, s*(POD[1]+7.5), s, -1)
        rcs(kit, f'Hull RCS {side} stern', -38.0, s*37.0, s, -1)
    # Finned heat exchangers on the engine block's shoulders, beside the vent bays.
    for s, side in ((1, 'port'), (-1, 'starboard')):
        fins, verts, faces = [], [], []
        for k in range(10):
            x = -36.5-1.2*k
            a, b = hit(kit, x, s*15.5)[0], hit(kit, x, s*21.5)[0]
            base = len(verts)
            for dx in (-.1, .1):
                verts += [(x+dx, a.y, a.z-.2), (x+dx, b.y, b.z-.2), (x+dx, b.y, b.z+1.4), (x+dx, a.y, a.z+1.4)]
            faces += [(base, base+1, base+2, base+3), (base+4, base+7, base+6, base+5), (base, base+4, base+5, base+1),
                      (base+1, base+5, base+6, base+2), (base+2, base+6, base+7, base+3), (base+3, base+7, base+4, base)]
        kit.mesh(f'Hull aft {side} heat exchanger fins', verts, faces, kit.steel, 0)
        for y in (15.5, 21.5):
            a, b = hit(kit, -36.0, s*y)[0], hit(kit, -47.8, s*y)[0]
            fins.append(((-35.8, s*y, a.z+1.45), (-48.0, s*y, b.z+1.45), .2, .2))
        struts(kit, f'Hull aft {side} heat exchanger frame', fins, kit.plate)
    # Pipe runs joining each wing tank to its shoulder machinery bay through the engine block's face.
    for s, side in ((1, 'port'), (-1, 'starboard')):
        p = [(-10.4, s*38.0, -3.0), (-14.0, s*36.5, -1.0), (-16.8, s*35.0, 2.6), (-18.8, s*33.5, 4.8)]
        struts(kit, f'Hull {side} tank transfer pipe', [(a, b, .65, .65, 8) for a, b in zip(p, p[1:])], kit.plate)


def build(kit):
    beacon = material('Manticore mast beacon red', (.1, .01, .01), 0, .4, emission=(1.0, .06, .03), strength=12.0)
    command_deck(kit, beacon)
    hull_detail(kit, beacon)
