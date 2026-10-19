"""Phaeton drives: the stern drive block with its six LOW_TECH bells, the two
reaction-mass drums on its corners, and the two drive housings either side
of the machinery spine with their guard arcs.

Engine slots from phaeton.ship, in file order (index i = 1..6), all angle
180 at X -93: Y +36 (w12), +24, +9, -9, -24 (w16), -36 (w12). Read off
tanker_medium.png (bow right, port up):
- the drive block is the wide grey stern X -91..-62, Y -42..42. Its crown
  is the sprite's grey machinery pattern: fore-aft ribs either side of the
  centreline (Y +-13) and near the drums (Y +-31), a transverse frame at its
  forward edge (X -65), conduit runs between the ribs, and the round seat of
  turret WS 001 at (-75, 0), kept a flat plate;
- the bells sit in a bay under an overhanging thrust shelf: the block's
  aft face is set back to X -85 so each bell stands proud of it to its slot
  mouth, and the shelf above (to X -91.5) and the thrust-frame gussets
  between the bells keep the sprite's square stern in plan;
- the drums at its corners (X -90..-66, Y 32..55 each side) are X-axis
  cylinders with a red band round them at X -79: reaction-mass drums, with
  hoop stiffeners, a dished aft cap with a valve block outboard (the bright
  knuckle at the sprite's corner), a forward valve block and feed lines over
  the crown into the block;
- the dark rings at X -62..-38, Y 15..40 each side, with a pink glow at
  their centre, are vertical drive housings (fusion plant): a segmented dark
  crown ring with radial ribs round a recessed port, the glow deep in it
  behind an open iris; plated sides with seams and a girth band;
- the tan arcs outboard of them (Y 40..58) are guard frames: box-section
  crescents (top and bottom flanges, inner and outer chords, radial ribs)
  with lightening holes in the top flange and outer chord; the flare
  launchers (WS 005 / WS 006, ph_mounts) sit on the outer rim of the crown.
Bells are low-tech nozzles: contoured liners with film-cooling steps, a
throat ring and injector, a heat-shield band and stiffener hoop, a gimbal
ring on trunnions steered by two actuator rams from the block face, and a
shroud collar where each bell meets the block.

Names follow the Second Wind: 'Engine <i> aft-facing bell', 'Engine <i>
mouth lip', 'Engine <i> dark throat', 'Engine <i> exhaust anchor' (an empty
on the mouth whose +X is the exhaust direction). Pass 2 parts keep the
'Drive ...', 'Frame ...' and 'Engine <i> ...' prefixes.
"""
import math
import re
import bpy
from mathutils import Vector
from ph_kit import prism, section, pod, revolve_x, boolean, material

ENGINES = [(-93, 36, 12, 180), (-93, 24, 16, 180), (-93, 9, 16, 180), (-93, -9, 16, 180),
           (-93, -24, 16, 180), (-93, -36, 12, 180)]
ENGINE_Z = -1.0
MOUTH_PER_WIDTH = .29     # bell mouth radius per unit of slot width (the Shepherd's ratio)
THROAT_PER_MOUTH = .55
BELL_LENGTH = {16: 6.5, 12: 5.8}
BLOCK_AFT = -85.0         # drive block aft face, set back so the bells stand proud of it
SHELF_AFT = -92.0         # thrust shelf over the bell bay: keeps the sprite's stern in plan
SHELF_Z = (4.4, 8.0)
SILL_Z = (-12.5, -8.0)
GUSSETS_Y = (0.0, 16.5, -16.5, 30.5, -30.5)   # thrust-frame webs between the bells
CROWN_Z = 9.0
SEAT = (-75.0, 0.0, 8.6)  # WS 001 seat: centre and radius kept clear of crown work
ANCHORS = {}              # engine index -> (location, exhaust direction)
HOUSINGS = {'port': (-47, 29.0), 'starboard': (-47, -29.0)}
HOUSING_R = 16.0
HOUSING_Z = (-10.0, 8.0)
PORT_R, PORT_FLOOR = 9.0, 4.4   # recessed crown port: well radius and floor height
DRUM_Y, DRUM_Z, DRUM_R = 42.5, 1.0, 8.8
ARC = {'r': (15.0, 29.5), 'a': (65.0, 115.0), 'z': (-5.0, 5.0), 'ribs': (65.6, 75, 85, 95, 105, 114.4)}
# Repeated small details joined into one object each (pattern -> merged name), to keep
# the module's object count down; primary parts stay separate for the destroyed state.
MERGES = [
    (r'^(Drive \w+ housing (?:plate seam|crown segment|crown rib|iris blade)) \d+$', '{}s'),
    (r'^(Drive block \w+ service cover \d+ bolt) \d+$', '{}s'),
    (r'^(Drive block \w+ (?:grille slot|conduit clamp|side seam)) \d+$', '{}s'),
    (r'^(Drive block \w+ conduit) \d+(?: drop)?$', '{}s'),
    (r'^(Drive block (?:crown|keel|keel cross|shelf) seam) \d+(?: \w+)?$', '{}s'),
    (r'^(Drive block aft face) (?:girth )?seam(?: \d+)?$', '{} seams'),
    (r'^(Drive \w+ drum \w*\s?feed line(?: \d)?) (?:elbow )?\d+$', '{}'),
    (r'^(Drive \w+ drum) (?:band seam|hoop stiffener) \d+$', '{} hoops'),
    (r'^(Drive \w+ drum saddle) \d+$', '{}s'),
    (r'^(Frame \w+ guard arc \w+ root bolt) \d+$', '{}s'),
    (r'^(Frame \w+ yoke pin(?: cap)?) \d+$', '{}s'),
    (r'^(Engine \d+) (?:gimbal actuator \d+(?: rod)?|actuator clevis \d+|gimbal lever ear \d+)$', '{} gimbal actuators'),
    (r'^(Engine \d+) (?:gimbal trunnion|trunnion pin cap) \w+$', '{} gimbal trunnions'),
]
LAUNCHER_ANGLE = 97.8     # WS 005/006 seen from the housing centre: no outer-chord hole there
_M = {}


def _mats():
    if not _M:
        _M['liner'] = material('Drive bell liner', (.085, .09, .095), .75, .42)
        _M['heat'] = material('Nozzle heat-blued steel', (.11, .1, .13), .8, .35)
        _M['rod'] = material('Actuator rod chrome', (.5, .52, .53), .9, .22)
    return _M


# --- small geometry helpers -------------------------------------------------

def revolve_z(kit, name, profile, cx, cy, mat, n=32, bevel=0):
    """Solid of revolution about a vertical line: profile [(r, z), ...] is the closed outline."""
    verts = [(cx+r*math.cos(2*math.pi*i/n), cy+r*math.sin(2*math.pi*i/n), z) for r, z in profile for i in range(n)]
    m = len(profile)
    faces = []
    for a in range(m):
        b = (a+1) % m
        faces += [(a*n+i, a*n+(i+1) % n, b*n+(i+1) % n, b*n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, bevel)


def sector(kit, name, cx, cy, s, r0, r1, a0, a1, z0, z1, mat, n=12, bevel=0):
    """Closed annular sector about (cx, cy), angles in degrees, mirrored in Y by s."""
    verts, faces = [], []
    for i in range(n+1):
        a = math.radians(a0+(a1-a0)*i/n)
        for rr in (r0, r1):
            for z in (z0, z1):
                verts.append((cx+rr*math.cos(a), cy+s*rr*math.sin(a), z))
    for i in range(n):
        a, b = 4*i, 4*(i+1)
        faces += [(a, b, b+1, a+1), (a+2, a+3, b+3, b+2), (a, a+2, b+2, b), (a+1, b+1, b+3, a+3)]
    faces += [(0, 1, 3, 2), (4*n, 4*n+2, 4*n+3, 4*n+1)]
    return kit.mesh(name, verts, faces, mat, bevel)


def stick(kit, name, a, b, radius, mat, verts=10, bevel=.02):
    """Cylinder from point a to point b."""
    a, b = Vector(a), Vector(b)
    ob = kit.cylinder(name, (a+b)/2, radius, (b-a).length, mat, 'Z', verts, bevel)
    ob.rotation_euler = (b-a).to_track_quat('Z', 'Y').to_euler()
    return ob


def pipe(kit, name, pts, radius, mat):
    """A pipe run through points, with a knuckle at each bend."""
    for k, (a, b) in enumerate(zip(pts, pts[1:]), 1):
        stick(kit, f'{name} {k}', a, b, radius, mat)
    for k, p in enumerate(pts[1:-1], 1):
        kit.cylinder(f'{name} elbow {k}', p, radius*1.25, radius*2.5, mat, 'Z', 10, .02)


def ring_x(kit, name, x0, x1, r_in, r_out, cy, cz, mat, n=24):
    """Flat annulus along X (a collar plate or hoop)."""
    return revolve_x(kit, name, [(x0, r_in), (x1, r_in), (x1, r_out), (x0, r_out)], cy, cz, mat, n)


def bell_curve(r, rt, length, start=30, end=10):
    """Liner radius at depth a behind the mouth: a quadratic Bezier leaving the
    throat at `start` degrees and meeting the mouth at `end` degrees."""
    m1, m2 = math.tan(math.radians(start)), math.tan(math.radians(end))
    bc = min(max((r-rt-m2*length)/(m1-m2), .05*length), .95*length)
    pts = []
    for k in range(41):
        u = k/40
        b = 2*u*(1-u)*bc+u*u*length
        pts.append((length-b, (1-u)**2*rt+2*u*(1-u)*(rt+m1*bc)+u*u*r))
    pts.sort()

    def rho(a):
        for (a0, r0), (a1, r1) in zip(pts, pts[1:]):
            if a <= a1:
                return r0+(r1-r0)*(a-a0)/max(a1-a0, 1e-9)
        return pts[-1][1]
    return rho


# --- engines ----------------------------------------------------------------

def engine(kit, i, x, y, w):
    """One low-tech bell standing proud of the block face, mouth at the slot (x, y, ENGINE_Z)."""
    m = _mats()
    z = ENGINE_Z
    r = MOUTH_PER_WIDTH*w
    rt = THROAT_PER_MOUTH*r
    L = BELL_LENGTH[w]
    wall = .05*r
    rho = bell_curve(r, rt, L)
    X = lambda a: x+a      # depth behind the mouth -> model X (the bell runs forward into the block)
    # Liner: inner surface stepped at each film-cooling band, chamber, then the outer skin back to the mouth.
    bands = 4
    step = .014*r
    inner = []
    joints = [L*j/bands for j in range(bands+1)]
    for j, (a0, a1) in enumerate(zip(joints, joints[1:])):
        for q in range(3):
            a = a0+(a1-a0)*q/2*.999
            inner.append((X(a), rho(a)-(0 if j == 0 else step*(a1-a)/(a1-a0))))
    ch = .14*r
    inner += [(X(L+ch*.5), rt*1.08), (X(L+ch), rt*1.12)]
    outer = [(X(L+ch), rt*1.12+wall), (X(L+ch*.5), rt*1.08+wall)]
    outer += [(X(L*q/10), rho(L*q/10)+wall) for q in range(10, -1, -1)]
    revolve_x(kit, f'Engine {i} aft-facing bell', inner+outer, y, z, m['liner'], 32)
    out = lambda a: rho(a)+wall
    bpy.ops.mesh.primitive_torus_add(major_radius=r+wall/2, minor_radius=wall/2+.07*r, major_segments=32,
                                     minor_segments=8, location=(x, y, z), rotation=(0, math.pi/2, 0))
    kit.finish(bpy.context.object, f'Engine {i} mouth lip', kit.grey)
    xi = X(L+ch)
    kit.cylinder(f'Engine {i} dark throat', (xi-.03*r, y, z), rt*1.12, .06*r, kit.dark, 'X', 32, 0)
    revolve_x(kit, f'Engine {i} throat ring', [(X(L-.06*r), out(L-.06*r)-.02), (X(L-.06*r), out(L-.06*r)+.18),
                                               (X(L+.06*r), out(L)+.18), (X(L+.06*r), out(L)-.02)], y, z, kit.steel, 32)
    kit.cylinder(f'Engine {i} injector pintle', (xi-.1*r, y, z), .1*r, .16*r, kit.steel, 'X', 12, .01)
    # Heat-shield band near the mouth and a stiffener hoop mid-bell.
    h0, h1 = .08*L, .26*L
    revolve_x(kit, f'Engine {i} heat-shield band', [(X(h0), out(h0)-.02), (X(h0+.1), out(h0+.1)+.12),
                                                    (X(h1-.1), out(h1-.1)+.12), (X(h1), out(h1)-.02)], y, z, m['heat'], 32)
    a = .46*L
    ring_x(kit, f'Engine {i} bell stiffener hoop', X(a), X(a+.25), out(a+.12)-.05, out(a+.12)+.2, y, z, kit.steel, 32)
    # Gimbal ring on two trunnions near the throat.
    g0, g1 = .7*L, .8*L
    ring_out = out(g0)+.55
    ring_x(kit, f'Engine {i} gimbal ring', X(g0), X(g1), out(g1)-.04, ring_out, y, z, kit.steel, 24)
    for side in (1, -1):
        tag = 'port' if side > 0 else 'starboard'
        kit.box(f'Engine {i} gimbal trunnion {tag}', (X((g0+g1)/2), y+side*(ring_out+.2), z), (.7, .6, 1.0), kit.steel, .05)
        kit.cylinder(f'Engine {i} trunnion pin cap {tag}', (X((g0+g1)/2), y+side*(ring_out+.55), z), .3, .12,
                     m['rod'], 'Y', 12, .02)
    # Two actuator rams, low on either side (pitch and yaw), from clevises on the block face to ears on the ring.
    for k, deg in enumerate((-45, -135), 1):
        u = Vector((0, math.cos(math.radians(deg)), math.sin(math.radians(deg))))
        ear = Vector((X((g0+g1)/2), y, z))+u*(ring_out+.25)
        clevis = Vector((BLOCK_AFT-.4, y, z))+u*(rt+2.4)
        kit.box(f'Engine {i} gimbal lever ear {k}', ear, (.45, .5, .5), kit.steel, .04)
        cl = kit.box(f'Engine {i} actuator clevis {k}', clevis, (.8, .9, .9), kit.steel, .05)
        cl.rotation_euler = (math.atan2(-u.y, u.z), 0, 0)
        mid = clevis+(ear-clevis)*.55
        stick(kit, f'Engine {i} gimbal actuator {k}', clevis, mid, .26, kit.steel, 10)
        stick(kit, f'Engine {i} gimbal actuator {k} rod', mid, ear, .13, m['rod'], 8)
    # Drive stub from the injector into the block, and the shroud collar on the block face.
    kit.cylinder(f'Engine {i} drive stub', ((xi+BLOCK_AFT)/2, y, z), rt*1.05, BLOCK_AFT-xi+.2, kit.steel, 'X', 24, .03)
    ring_x(kit, f'Engine {i} shroud collar', BLOCK_AFT-.7, BLOCK_AFT+.05, rt*1.05, rt+1.7, y, z, kit.grey, 32)
    ring_x(kit, f'Engine {i} shroud collar seam', BLOCK_AFT-.72, BLOCK_AFT-.6, rt+1.0, rt+1.15, y, z, kit.dark, 32)
    kit.empty(f'Engine {i} exhaust anchor', (x, y, z), 180)
    ANCHORS[i] = ((x, y, z), (-1, 0, 0))


# --- drive block --------------------------------------------------------------

def block(kit):
    b = prism(kit, 'Drive block', [(BLOCK_AFT, -36), (-80, -41), (-64, -41), (-60, -35), (-60, 35), (-64, 41),
                                   (-80, 41), (BLOCK_AFT, 36)], section(-13, CROWN_Z, 4, 3), kit.steel)
    kit.hull.append(b)
    # Bell bay: thrust shelf overhead, a low sill, and the thrust-frame gussets between the bells.
    shelf = prism(kit, 'Drive block thrust shelf', [(SHELF_AFT, -33.5), (-84, -41), (-82, -41), (-82, 41), (-84, 41),
                                                    (SHELF_AFT, 33.5)], section(*SHELF_Z, .6, 1.0), kit.steel)
    kit.hull.append(shelf)
    for k, x in enumerate((-89.5, -87.0), 1):   # shelf plate seams
        kit.box(f'Drive block shelf seam {k}', (x, 0, SHELF_Z[1]+.01), (.12, 62-2*(x+91.5), .04), kit.dark, 0)
    for k, y in enumerate((-20.0, 0.0, 20.0), 1):
        kit.box(f'Drive block shelf rib {k}', (-87.5, y, SHELF_Z[1]+.3), (7.5, .9, .6), kit.grey, .08)
    kit.box('Drive block shelf edge girder', (SHELF_AFT+.6, 0, SHELF_Z[0]+.45), (1.0, 62, .9), kit.grey, .08)
    kit.hull.append(prism(kit, 'Drive block bay sill', [(-88.5, -33), (-84, -38), (-82, -38), (-82, 38), (-84, 38),
                                                         (-88.5, 33)], section(*SILL_Z, 1.2, .5), kit.steel))
    for k, y in enumerate(GUSSETS_Y, 1):
        verts = [(px, y+dy, pz) for dy in (-.5, .5) for px, pz in
                 ((BLOCK_AFT+.2, SILL_Z[1]), (-87.5, SILL_Z[1]), (SHELF_AFT+.3, SHELF_Z[0]), (BLOCK_AFT+.2, SHELF_Z[0]))]
        kit.mesh(f'Drive block thrust gusset {k}', verts, [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2),
                                                           (2, 6, 7, 3), (3, 7, 4, 0)], kit.grey, .08)
        kit.box(f'Drive block gusset flange {k}', (-87.6, y, -1.8), (.4, 1.6, 12.0), kit.steel, .05).rotation_euler[1] = -.4
    # Aft face: vertical plate seams behind the bells, bolted lower course.
    for k, y in enumerate((-30.5, -16.5, 0.0, 16.5, 30.5), 1):
        kit.box(f'Drive block aft face seam {k}', (BLOCK_AFT-.02, y+.9, -1.8), (.06, .14, 12.0), kit.dark, 0)
    kit.box('Drive block aft face girth seam', (BLOCK_AFT-.02, 0, -6.2), (.06, 70, .14), kit.dark, 0)

    # Crown: the turret seat, ribs, conduits, hatches and grilles (kept low under WS 001's aft arc).
    sx, sy, sr = SEAT
    kit.hull.append(kit.cylinder('Drive block turret seat plate', (sx, sy, CROWN_Z+.15), sr, .3, kit.grey, 'Z', 32, .05))
    kit.cylinder('Drive block turret seat seam', (sx, sy, CROWN_Z+.01), sr+.45, .02, kit.dark, 'Z', 32, 0)
    for side, s in (('port', 1), ('starboard', -1)):
        kit.box(f'Drive block crown rib {side} inner', (-72.5, s*13, CROWN_Z+.45), (19, 1.4, .9), kit.grey, .1)
        kit.box(f'Drive block crown rib {side} outer', (-72.5, s*31, CROWN_Z+.45), (19, 1.4, .9), kit.grey, .1)
        kit.box(f'Drive block crown frame {side}', (-65.5, s*22, CROWN_Z+.45), (1.4, 17.6, .9), kit.grey, .1)
        kit.box(f'Drive block crown aft frame {side}', (-80.5, s*22, CROWN_Z+.45), (1.4, 17.6, .9), kit.grey, .1)
        for k, yy in enumerate((16.6, 18.6, 20.6), 1):   # conduit run between the ribs
            stick(kit, f'Drive block {side} conduit {k}', (-80, s*yy, CROWN_Z+.55), (-60.5, s*yy, CROWN_Z+.55), .45,
                  kit.steel if k != 2 else kit.dark, 8)
            stick(kit, f'Drive block {side} conduit {k} drop', (-60.5, s*yy, CROWN_Z+.55), (-59.6, s*yy, CROWN_Z-2.5),
                  .45, kit.steel if k != 2 else kit.dark, 8)
        for k, x in enumerate((-73, -67), 1):
            kit.box(f'Drive block {side} conduit clamp {k}', (x, s*18.6, CROWN_Z+.5), (.5, 5.2, 1.2), kit.dark, 0)
        for k, x in enumerate((-77, -69.5), 1):           # bolted service covers
            kit.box(f'Drive block {side} service cover {k}', (x, s*26, CROWN_Z+.15), (5.2, 6.0, .3), kit.steel, .08)
            kit.box(f'Drive block {side} service cover {k} handle', (x+2.0, s*26, CROWN_Z+.4), (.3, 1.6, .25), kit.dark, 0)
            for q, (dx, dy) in enumerate(((-2.2, -2.6), (2.2, -2.6), (2.2, 2.6), (-2.2, 2.6)), 1):
                kit.cylinder(f'Drive block {side} service cover {k} bolt {q}', (x+dx, s*26+dy, CROWN_Z+.36), .16, .14,
                             kit.grey, 'Z', 6, 0)
        for k in range(5):                                 # louvred grille beside the drum
            kit.box(f'Drive block {side} grille slot {k+1}', (-78+2.4*k, s*35.5, CROWN_Z+.02), (1.2, 3.0, .06), kit.dark, 0)
        kit.cylinder(f'Drive block {side} pump housing', (-66.5, s*7.0, CROWN_Z+.8), 1.9, 1.6, kit.grey, 'Z', 16, .08)
        kit.cylinder(f'Drive block {side} pump cap', (-66.5, s*7.0, CROWN_Z+1.65), 1.2, .2, kit.dark, 'Z', 16, 0)
        # Thrust frame on the forward face, carrying the push into the housing yokes.
        kit.box(f'Drive block {side} thrust post', (-59.4, s*17.5, -1.5), (1.4, 2.0, 18), kit.grey, .1)
        stick(kit, f'Drive block {side} thrust brace', (-59.4, s*17.5, 6.5), (-51.8, s*15.5, 2.5), .55, kit.grey, 8)
        # Side plating below the drum: vertical seams and a keel course.
        for k, x in enumerate((-76, -70), 1):
            kit.box(f'Drive block {side} side seam {k}', (x, s*41.03, -9), (.14, .06, 6), kit.dark, 0)
    for k, x in enumerate((-80, -72, -66), 1):            # crown transverse seams outside the seat
        for side, s in (('port', 1), ('starboard', -1)):
            kit.box(f'Drive block crown seam {k} {side}', (x+1.6, s*24.5, CROWN_Z+.01), (.12, 23, .04), kit.dark, 0)
    for k, y in enumerate((-20, 0, 20), 1):                # keel plate seams
        kit.box(f'Drive block keel seam {k}', (-72, y, -13.02), (18, .14, .04), kit.dark, 0)
    for k, x in enumerate((-76, -68), 1):
        kit.box(f'Drive block keel cross seam {k}', (x, 0, -13.02), (.14, 66, .04), kit.dark, 0)


# --- reaction drums -----------------------------------------------------------

def drum(kit, side, s):
    cy = s*DRUM_Y
    d = pod(kit, f'Drive {side} reaction drum', [(-87.5, 5.6, 5.6), (-86, DRUM_R, DRUM_R), (-69, DRUM_R, DRUM_R),
                                                 (-67, 7, 7)], cy, DRUM_Z, kit.grey, n=20)
    kit.hull.append(d)
    kit.cylinder(f'Drive {side} drum band', (-79, cy, DRUM_Z), 9.2, 3.0, kit.red, 'X', 20, .1)
    for k, x in enumerate((-80.7, -77.3), 1):
        ring_x(kit, f'Drive {side} drum band seam {k}', x-.08, x+.08, 9.0, 9.25, cy, DRUM_Z, kit.dark, 20)
    for k, x in enumerate((-85.2, -73.2, -70.0), 1):
        ring_x(kit, f'Drive {side} drum hoop stiffener {k}', x, x+.6, DRUM_R-.1, DRUM_R+.45, cy, DRUM_Z, kit.steel, 20)
    # Dished aft cap, its valve block outboard (the sprite's bright corner knuckle).
    pod(kit, f'Drive {side} drum aft end cap', [(-89.6, 2.2, 2.2), (-88.9, 3.6, 3.6), (-87.4, 5.0, 5.0)],
        cy, DRUM_Z, kit.steel, n=20)
    kit.cylinder(f'Drive {side} drum aft manway', (-89.8, cy, DRUM_Z), 1.6, .4, kit.grey, 'X', 16, .05)
    kit.box(f'Drive {side} drum aft valve block', (-89.2, s*45.6, DRUM_Z+2.6), (2.6, 3.4, 3.2), kit.steel, .12)
    kit.cylinder(f'Drive {side} drum aft valve wheel', (-90.6, s*45.6, DRUM_Z+2.6), .95, .22, kit.dark, 'X', 12, .03)
    pipe(kit, f'Drive {side} drum aft feed line', [(-89.0, s*45.6, DRUM_Z+4.2), (-89.0, s*45.0, DRUM_Z+7.6),
                                                   (-84.0, s*40.0, DRUM_Z+8.9)], .55, kit.steel)
    # Forward valve block and the feed line into the drive housing.
    kit.box(f'Drive {side} drum forward valve block', (-66.3, cy, DRUM_Z), (1.8, 4.2, 4.2), kit.steel, .12)
    pipe(kit, f'Drive {side} drum housing feed line', [(-64.5, s*41.5, DRUM_Z+1.0), (-62.0, s*39.5, DRUM_Z+1.0),
                                                       (-59.0, s*38.0, DRUM_Z+1.0)], .7, kit.steel)
    # Feed lines over the drum's inboard shoulder into the crown manifold.
    for k, x in enumerate((-81.0, -75.5), 1):
        pipe(kit, f'Drive {side} drum crown feed line {k}', [(x, s*39.0, DRUM_Z+DRUM_R-.6), (x, s*36.0, CROWN_Z+1.3),
                                                             (x, s*29.0, CROWN_Z+1.3)], .5, kit.steel)
    kit.box(f'Drive {side} crown feed manifold', (-78.2, s*27.5, CROWN_Z+1.0), (7.6, 2.4, 1.6), kit.grey, .1)
    # Hold-down saddles clamping the drum to the block side.
    for k, x in enumerate((-83.5, -71.6), 1):
        kit.box(f'Drive {side} drum saddle {k}', (x, s*(DRUM_Y-6.5), DRUM_Z+6.6), (1.4, 4.5, 1.2), kit.grey, .08)


# --- drive housings ------------------------------------------------------------

def housing(kit, side, s):
    m = _mats()
    hx, hy = HOUSINGS[side]
    z0, z1 = HOUSING_Z
    R = HOUSING_R
    h = revolve_z(kit, f'Drive {side} housing', [(2.0, z0), (R-1.0, z0), (R, z0+1.5), (R, z1-2.2), (R-1.4, z1),
                                                 (PORT_R+1.2, z1), (PORT_R, z1-1.0), (PORT_R, PORT_FLOOR), (2.0, PORT_FLOOR)],
                  hx, hy, kit.steel, 40, .1)
    kit.hull.append(h)
    kit.cylinder(f'Drive {side} housing base boss', (hx, hy, z0-.25), 6.0, .5, kit.grey, 'Z', 24, .06)
    # The glow lies deep in the well, behind an open iris; the well floor round it is dark.
    kit.cylinder(f'Drive {side} housing port well floor', (hx, hy, PORT_FLOOR+.03), PORT_R+.05, .06, kit.dark, 'Z', 40, 0)
    kit.cylinder(f'Drive {side} housing crown port', (hx, hy, PORT_FLOOR+.12), 5.6, .12, kit.glow, 'Z', 32, 0)
    for k in range(8):
        a = 2*math.pi*k/8
        blade = kit.box(f'Drive {side} housing iris blade {k+1}',
                        (hx+7.3*math.cos(a), hy+7.3*math.sin(a), PORT_FLOOR+.4), (1.8, 3.8, .3), kit.steel, .05)
        blade.rotation_euler = (.18, 0, a+.45)
    revolve_z(kit, f'Drive {side} housing port lip', [(PORT_R-.3, z1-1.0), (PORT_R+.9, z1-1.0), (PORT_R+.9, z1-.6),
                                                     (PORT_R-.3, z1-.6)], hx, hy, kit.grey, 40)
    # Crown ring: dark segments with radial ribs between them (the sprite's dark ring).
    n = 12
    for k in range(n):
        a = 2*math.pi*(k+.5)/n
        seg = sector(kit, f'Drive {side} housing crown segment {k+1}', hx, hy, 1, PORT_R+1.5, R-1.7,
                     math.degrees(a)-12.5, math.degrees(a)+12.5, z1, z1+.25, kit.dark, 3)
        a = 2*math.pi*k/n
        rib = kit.box(f'Drive {side} housing crown rib {k+1}', (hx+12.4*math.cos(a), hy+12.4*math.sin(a), z1+.35),
                      (5.2, .8, .7), kit.grey, .06)
        rib.rotation_euler[2] = a
    # Side plating: vertical seams, a girth band and a bolted inspection hatch outboard-aft.
    for k in range(16):
        a = 2*math.pi*(k+.5)/16
        seam = kit.box(f'Drive {side} housing plate seam {k+1}', (hx+(R+.01)*math.cos(a), hy+(R+.01)*math.sin(a), -2.5),
                       (.06, .14, 11.0), kit.dark, 0)
        seam.rotation_euler[2] = a
    revolve_z(kit, f'Drive {side} housing girth band', [(R-.05, -1.2), (R+.3, -1.0), (R+.3, .4), (R-.05, .6)],
              hx, hy, kit.grey, 40)
    revolve_z(kit, f'Drive {side} housing base course seam', [(R-.05, -6.1), (R+.03, -6.1), (R+.03, -5.95),
                                                              (R-.05, -5.95)], hx, hy, kit.dark, 40)
    a = math.radians(s*25)   # forward-outboard, above the hull's aft frame
    hatch = kit.box(f'Drive {side} housing inspection hatch', (hx+(R+.15)*math.cos(a), hy+(R+.15)*math.sin(a), 4.6),
                    (.4, 2.6, 2.0), kit.grey, .08)
    hatch.rotation_euler[2] = a


# --- guard arcs ---------------------------------------------------------------

def guard_arc(kit, side, s):
    hx, hy = HOUSINGS[side]
    r0, r1 = ARC['r']
    a0, a1 = ARC['a']
    z0, z1 = ARC['z']
    P = lambda r, a, z: (hx+r*math.cos(math.radians(a)), hy+s*r*math.sin(math.radians(a)), z)
    yaw = lambda a: math.atan2(s*math.sin(math.radians(a)), math.cos(math.radians(a)))
    # Outer chord (keeps the blockout's name), inner chord, top and bottom flanges.
    outer = sector(kit, f'Frame {side} guard arc', hx, hy, s, r1-2.2, r1, a0, a1, z0, z1, kit.tan, 16, .1)
    inner = sector(kit, f'Frame {side} guard arc inner chord', hx, hy, s, r0, r0+1.8, a0, a1, z0, z1, kit.tan, 16, .1)
    top = sector(kit, f'Frame {side} guard arc top flange', hx, hy, s, r0, r1+.3, a0, a1, z1-1.1, z1, kit.tan, 16, .08)
    sector(kit, f'Frame {side} guard arc bottom flange', hx, hy, s, r0, r1+.3, a0, a1, z0, z0+1.1, kit.tan, 16, .08)
    kit.hull.append(top)
    # Box-section radial ribs and their caps proud of the top flange.
    for k, a in enumerate(ARC['ribs'], 1):
        rib = kit.box(f'Frame {side} guard arc rib {k}', P((r0+r1)/2, a, 0), (r1-r0-1, 1.6, z1-z0-.4), kit.tan, .08)
        rib.rotation_euler[2] = yaw(a)
        cap = kit.box(f'Frame {side} guard arc rib cap {k}', P((r0+r1)/2+.4, a, z1+.25), (r1-r0-1.6, 1.0, .5), kit.tan, .06)
        cap.rotation_euler[2] = yaw(a)
    # Edge girders along both rims of the crown.
    sector(kit, f'Frame {side} guard arc outer rim', hx, hy, s, r1-1.2, r1+.3, a0, a1, z1, z1+.45, kit.tan, 16, .05)
    sector(kit, f'Frame {side} guard arc inner rim', hx, hy, s, r0, r0+1.0, a0, a1, z1, z1+.45, kit.tan, 16, .05)
    # Lightening holes: one through the top flange and one through the outer chord in each bay.
    bays = [(a+b)/2 for a, b in zip(ARC['ribs'], ARC['ribs'][1:])]
    for k, a in enumerate(bays, 1):
        c = kit.cylinder(f'Frame {side} top hole cutter {k}', P(22.4, a, z1-.55), 2.6, 3.0, kit.dark, 'Z', 20, 0)
        boolean(top, c)
        if abs(a-LAUNCHER_ANGLE) > 6:
            c = kit.cylinder(f'Frame {side} chord hole cutter {k}', P(r1-1.1, a, 0), 2.2, 4.0, kit.dark, 'X', 20, 0)
            c.rotation_euler = (0, math.pi/2, yaw(a))
            boolean(outer, c)
    # Housing yoke: a clevis block on the spine side, pinned, with tie straps.
    kit.box(f'Frame {side} housing yoke', (hx, s*15.0, -1), (10, 6, 8), kit.grey, .3)
    for k, dx in enumerate((-3.6, 3.6), 1):
        kit.cylinder(f'Frame {side} yoke pin {k}', (hx+dx, s*15.0, 1.0), .8, 6.8, kit.steel, 'Y', 12, .05)
        kit.cylinder(f'Frame {side} yoke pin cap {k}', (hx+dx, s*18.5, 1.0), 1.1, .3, kit.dark, 'Y', 12, .03)
    kit.box(f'Frame {side} yoke tie strap', (hx, s*15.0, 3.2), (9.2, 6.4, .5), kit.steel, .06)
    # Roots: where the arc springs from the housing's forward and aft shoulders, with a bolted gusset each.
    for end, plan in (('forward', [(-42, 30), (-31, 33), (-33, 49), (-41, 50)]),
                      ('aft', [(-61, 37), (-53, 38), (-54, 50), (-61, 48)])):
        plan = [(px, s*py) for px, py in plan]
        if s < 0:
            plan.reverse()
        root = prism(kit, f'Frame {side} guard arc {end} root', plan, section(-5, 5, 1, 1), kit.tan)
        boolean(root, kit.cylinder(f'Frame {side} {end} root well cutter', (hx, hy, 6.0), PORT_R+.4, 6.0, kit.dark,
                                   'Z', 32, 0))
        kit.hull.append(root)
        cx = sum(p[0] for p in plan)/4
        cy = sum(p[1] for p in plan)/4
        kit.box(f'Frame {side} guard arc {end} root cap', (cx, cy, 5.25), (5.0, 9.0, .5), kit.tan, .08)
        for q, dy in enumerate((-3.2, -1.1, 1.1, 3.2), 1):
            kit.cylinder(f'Frame {side} guard arc {end} root bolt {q}', (cx, cy+dy, 5.6), .25, .2, kit.grey, 'Z', 6, 0)


def merge(objs):
    """Join each MERGES group among objs into one object (modifiers applied first)."""
    groups = {}
    for ob in objs:
        for pat, fmt in MERGES:
            m = re.match(pat, ob.name)
            if m:
                groups.setdefault(fmt.format(m.group(1)), []).append(ob)
                break
    for name, obs in groups.items():
        bpy.ops.object.select_all(action='DESELECT')
        for ob in obs:
            ob.select_set(True)
        bpy.context.view_layer.objects.active = obs[0]
        bpy.ops.object.convert(target='MESH')
        bpy.ops.object.join()
        ob = bpy.context.view_layer.objects.active
        ob.name = ob.data.name = name


def build(kit):
    before = set(bpy.data.objects)
    block(kit)
    for side, s in (('port', 1), ('starboard', -1)):
        drum(kit, side, s)
        housing(kit, side, s)
        guard_arc(kit, side, s)
    for i, (x, y, w, a) in enumerate(ENGINES, 1):
        engine(kit, i, x, y, w)
    merge([ob for ob in bpy.data.objects if ob not in before and ob.type == 'MESH'])
