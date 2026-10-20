"""The bridge capsule on the port bow, inside its crash cage.

The sprite's port forward pod (cols 13-27, rows 1-24) is a cylinder with a
lit, glazed nose (the cream lamps at rows 2-8), wrapped in a cage: proud
ring frames at rows 10-11, 14-15 and 18-19 reaching col 13, and fore-aft
bars at cols 14-15 between them. Inboard it leans on the gun fork's port
tine. The capsule is round by the shared segment rule (it was a flat-shaded
twelve-gon until the close-shot detail pass) and smooth-shaded; the cage is
elliptical so it stays within the hull's depth.

Pass 2 reads it as the command module of an old armoured freighter:
- armour sleeves between the cage rings, split into quarter panels over a
  dark liner so the seams are real gaps, painted as the sprite bands them
  (cream rows 3-14 and 18-21, oxide rows 14-17 and 20-24);
- the lamp rows of hound_lights.png (cols 18 and 23 at rows 6, 9, 13, 17)
  as framed, recessed viewports between the rings, and the nose lamp
  (cols 20-21, rows 4-5) as the top pane of a faceted canopy with heavy
  mullions and an armoured central spine under an armoured brow;
- a top airlock aft, a slim sensor mast with an array and a dish on the
  aft cage ring, whip antennas and (unlit) navigation lamp housings;
- a structural cage: clamp blocks and bolts where the bars cross the
  rings, standoff pads onto the capsule, saddle clamps onto the fork's
  port tine, and a yoke of struts and conduits back to the hull face.
"""
import math
from mathutils import Vector
from hd_kit import boolean, loft_x, material, round_shading, sides

CY, CZ = 9.2, 1.5
CORE = [(20.0, 5.2), (37.4, 5.2)]
NOSE = [(37.4, 5.2), (40.0, 4.7), (42.0, 3.8), (43.1, 2.3)]
# Ring frames at the sprite's rows 23, 18-19, 14-15 and 10-11.
RINGS = [21.5, 26.0, 30.0, 34.0]
RING_OUT, RING_IN, RING_W = (9.7, 7.1), (6.3, 5.4), 1.4
BAR_ANGLES = [90, 45, 0, -45, -90]
BAR_R = (7.9, 6.25)
# Canopy bands (pass 1 names kept): the forward glazing, upper arc only.
GLAZING = [(37.95, 39.35, -8, 188), (39.8, 41.35, 32, 128)]
# Armour sleeves between the rings, coloured as the sprite bands them.
SLEEVES = [(22.3, 25.2, 'cream'), (26.8, 29.2, 'orange'), (30.8, 33.2, 'cream'), (34.8, 37.35, 'cream')]
SLEEVE_SPLITS = [-15, 75, 165, 255, 345]
# hound_lights.png viewport lamps: rows 9, 13, 17 at cols 18 and 23.
VIEWPORT_X = [35.5, 31.5, 27.5]
VIEWPORT_Y = [12.5, 7.5]
# The capsule's rounds: vertices every STEP degrees from 15 (the segment rule at the
# capsule's 4.6 m radius asks for 68; 72 keeps the old twelve-gon's vertices).
STEP = 5
N_CAPSULE = 360//STEP


def circle(r, n=N_CAPSULE, ry=None, rz=None, phase=math.pi/12):
    ry = r if ry is None else ry
    rz = r if rz is None else rz
    return [(CY+ry*math.cos(phase+k*math.tau/n), CZ+rz*math.sin(phase+k*math.tau/n)) for k in range(n)]


def ring(kit, name, x, mat):
    # An elliptical frame: outer and inner rounds joined front and back.
    n = sides(max(RING_OUT), 16)
    outer = circle(0, n, *RING_OUT, phase=0)
    inner = circle(0, n, *RING_IN, phase=0)
    verts = []
    for dx in (-RING_W/2, RING_W/2):
        verts += [(x+dx, y, z) for y, z in outer] + [(x+dx, y, z) for y, z in inner]
    faces = []
    for k in range(n):
        a, b = k, (k+1) % n
        o0, i0, o1, i1 = 0, n, 2*n, 3*n
        faces += [(o0+a, o0+b, o1+b, o1+a), (i0+b, i0+a, i1+a, i1+b),
                  (o0+b, o0+a, i0+a, i0+b), (o1+a, o1+b, i1+b, i1+a)]
    return round_shading(kit.mesh(name, verts, faces, mat, .08))


# --- Capsule-surface helpers: everything here follows the capsule's round. ---

def capsule_r(x):
    """Radius of the capsule/nose at station x."""
    st = CORE[:1]+NOSE
    if x <= st[0][0]:
        return st[0][1]
    for (x0, r0), (x1, r1) in zip(st, st[1:]):
        if x <= x1:
            return r0+(r1-r0)*(x-x0)/(x1-x0)
    return st[-1][1]


def poly(a_deg, r):
    """Point on the capsule round of radius r at angle a (degrees), as a (dy, dz) offset."""
    a = math.radians(a_deg)
    return r*math.cos(a), r*math.sin(a)


def panel(kit, name, x0, x1, a0, a1, t_in, t_out, mat, bevel=.04, t_out1=None):
    """A curved plate on the capsule from x0..x1 and angle a0..a1, standing
    t_in..t_out off the surface (t_out1 at x1 flares the outer face); its vertices
    follow the capsule's, so it is as round as the capsule."""
    t_out1 = t_out if t_out1 is None else t_out1
    angles = [a0]+[STEP*k for k in range(-72, 144) if a0 < STEP*k < a1]+[a1]
    verts = []
    for a in angles:
        for x, t in ((x0, t_in), (x0, t_out), (x1, t_out1), (x1, t_in)):
            dy, dz = poly(a, capsule_r(x)+t)
            verts.append((x, CY+dy, CZ+dz))
    faces = [(0, 1, 2, 3)]
    m = len(angles)
    for i in range(m-1):
        for j in range(4):
            j1 = (j+1) % 4
            faces.append((i*4+j, i*4+j1, (i+1)*4+j1, (i+1)*4+j))
    faces.append(tuple(reversed(range((m-1)*4, m*4))))
    return round_shading(kit.mesh(name, verts, faces, mat, bevel))


def radial(kit, name, obj, a_deg):
    """Turn an object built about the capsule axis so its local +Z points out at angle a."""
    obj.rotation_euler[0] = math.radians(a_deg-90)
    return obj


def strut(kit, name, p0, p1, w, h, mat, up=(0, 0, 1), bevel=.06):
    """A rectangular member from p0 to p1, w wide and h deep."""
    p0, p1 = Vector(p0), Vector(p1)
    d = (p1-p0).normalized()
    s = d.cross(Vector(up))
    if s.length < 1e-4:
        s = d.cross(Vector((0, 1, 0)))
    s.normalize()
    u = s.cross(d).normalized()
    verts = []
    for p in (p0, p1):
        for a, b in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            verts.append(tuple(p+s*(a*w/2)+u*(b*h/2)))
    faces = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
    return kit.mesh(name, verts, faces, mat, bevel)


def cage_point(x, a_deg, r=BAR_R):
    a = math.radians(a_deg)
    return (x, CY+r[0]*math.cos(a), CZ+r[1]*math.sin(a))


def ring_outer(x, a_deg, out=.0):
    a = math.radians(a_deg)
    return (x, CY+(RING_OUT[0]+out)*math.cos(a), CZ+(RING_OUT[1]+out)*math.sin(a))


def angle_of_y(y, r):
    return math.degrees(math.acos(max(-1, min(1, (y-CY)/r))))


def build(kit):
    lens_red = material('Bridge navigation lens red', (.35, .02, .02), 0, .2)
    lens_white = material('Bridge navigation lens white', (.6, .6, .55), 0, .2)
    paint = {'cream': kit.cream, 'orange': kit.orange}

    round_shading(loft_x(kit, 'Bridge capsule hull', [(x, circle(r)) for x, r in CORE], kit.orange))
    round_shading(loft_x(kit, 'Bridge capsule nose', [(x, circle(r)) for x, r in NOSE], kit.cream))

    # Armour sleeves over a dark liner: quarter panels with open seams.
    round_shading(loft_x(kit, 'Bridge capsule seam liner', [(x, circle(5.3)) for x in (20.6, 37.3)], kit.dark, 0))
    for s, (x0, x1, col) in enumerate(SLEEVES, 1):
        for q, (a0, a1) in enumerate(zip(SLEEVE_SPLITS, SLEEVE_SPLITS[1:]), 1):
            panel(kit, f'Bridge armour sleeve {s} panel {q}', x0, x1, a0+1.2, a1-1.2, .05, .26, paint[col], .05)
    # Aft collar where the capsule enters the cradle (sprite rows 22-24, oxide).
    panel(kit, 'Bridge aft collar', 20.3, 21.0, -180, 180, .05, .42, kit.orange, .05)

    # Canopy: the pass-1 glazing bands, now upper-arc panes sunk between
    # heavy mullions, with an armoured spine over the crown and a brow.
    for k, (x0, x1, a0, a1) in enumerate(GLAZING, 1):
        panel(kit, f'Bridge glazing band {k}', x0, x1, a0, a1, -.08, .1, kit.glass, 0)
        for e, (ex0, ex1) in enumerate(((x0-.25, x0+.05), (x1-.05, x1+.25)), 1):
            panel(kit, f'Bridge canopy {k} frame {"aft" if e == 1 else "fore"}', ex0, ex1, a0-4, a1+4, -.05, .38, kit.steel)
    for a in (-8, 35, 125, 188):
        panel(kit, f'Bridge canopy 1 mullion {a}', 37.95, 39.35, a-2.5, a+2.5, -.05, .4, kit.steel)
    panel(kit, 'Bridge canopy armoured spine', 37.7, 39.6, 64, 96, -.05, .44, kit.steel, .05)
    # Band 2's crown pane (between the mullions at 64 and 96) is the nose
    # lamp of hound_lights.png (cols 20-21, rows 4-5).
    for a in (32, 64, 96, 128):
        panel(kit, f'Bridge canopy 2 mullion {a}', 39.8, 41.35, a-3, a+3, -.05, .36, kit.steel)
    panel(kit, 'Bridge visor brow', 36.9, 38.05, -12, 192, .12, .55, kit.orange, .06, t_out1=.85)
    panel(kit, 'Bridge chin armour', 38.0, 41.8, 200, 340, -.05, .3, kit.rust, .05)

    # Viewport rows (hound_lights cols 18 and 23): framed, recessed, glazed.
    for i, x in enumerate(VIEWPORT_X, 1):
        r = capsule_r(x)
        for y in VIEWPORT_Y:
            side = 'outboard' if y > CY else 'inboard'
            a = angle_of_y(y, r)
            frame = panel(kit, f'Bridge viewport {i} {side} frame', x-.75, x+.75, a-9, a+9, .1, .7, kit.steel, .04)
            cut = panel(kit, 'Temporary viewport cutter', x-.48, x+.48, a-5.5, a+5.5, .38, 1.2, kit.dark, 0)
            boolean(frame, cut)
            panel(kit, f'Bridge viewport {i} {side} glass', x-.5, x+.5, a-6, a+6, .1, .42, kit.glass, 0)

    # Crash cage: rings and bars (pass 1), now clamped, bolted and braced.
    for k, x in enumerate(RINGS, 1):
        ring(kit, f'Bridge cage ring {k}', x, kit.steel)
    for k, a in enumerate(BAR_ANGLES, 1):
        t = math.radians(a)
        y, z = CY+BAR_R[0]*math.cos(t), CZ+BAR_R[1]*math.sin(t)
        kit.box(f'Bridge cage bar {k}', ((RINGS[0]+RINGS[-1]+2)/2, y, z), (RINGS[-1]-RINGS[0]+2, 1.0, 1.0), kit.steel, .08)
    for r, x in enumerate(RINGS, 1):
        for b, a in enumerate(BAR_ANGLES, 1):
            p = cage_point(x, a)
            c = kit.box(f'Bridge cage clamp ring {r} bar {b}', p, (RING_W+.9, 1.7, 1.45), kit.dark, .1)
            radial(kit, None, c, a)
            if a >= 0:
                for s, dx in enumerate((-.62, .62), 1):
                    q = Vector(p)+Vector((dx, 0, 0))+.74*Vector((0, math.cos(math.radians(a)), math.sin(math.radians(a))))
                    bolt = kit.cylinder(f'Bridge cage clamp ring {r} bar {b} bolt {s}', tuple(q), .22, .22, kit.steel, vertices=6, bevel=0)
                    radial(kit, None, bolt, a)
        # Standoff pads from ring to capsule where the ellipse stands off it.
        for a, tag in ((0, 'outboard'), (180, 'inboard')):
            y_in = CY+RING_IN[0]*math.cos(math.radians(a))
            y_cap = CY+5.45*math.cos(math.radians(a))
            kit.box(f'Bridge cage ring {r} standoff {tag}', (x, (y_in+y_cap)/2, CZ), (RING_W*.7, abs(y_in-y_cap)+.3, 1.4), kit.dark, .06)
        # Saddle clamp onto the fork's port tine (Y -3..3) at the ring's inboard foot.
        kit.box(f'Bridge cage ring {r} tine saddle', (x, 1.2, CZ), (RING_W+1.0, 3.6, 2.6), kit.steel, .12)
    # Cradle and yoke onto the hull's forward face (X 20.5).
    kit.box('Bridge capsule cradle', (19.6, CY-.4, -1.2), (4.2, 10, 6.5), kit.steel, .15)
    # Lower yoke: struts from the hull face under the capsule to ring 2.
    for name, foot, a in (('lower outboard', (20.6, 11.0, -3.4), -60), ('lower inboard', (20.6, 5.2, -3.4), -120)):
        head = cage_point(RINGS[1]-RING_W/2-.1, a, (RING_OUT[0]-1.2, RING_OUT[1]-1.0))
        strut(kit, f'Bridge yoke strut {name}', foot, head, .8, .8, kit.steel, up=(0, 1, 0))
        kit.box(f'Bridge yoke foot {name}', (20.85, foot[1], foot[2]), (.7, 1.8, 1.8), kit.dark, .08)
    # Upper yoke: short braces from the forward deck edge to ring 1's crown.
    for name, y, a in (('upper outboard', 12.0, 60), ('upper inboard', 6.9, 120)):
        hit = kit.surface(19.2, y)
        fz = hit[0].z if hit else 6.0
        head = ring_outer(RINGS[0]-RING_W/2+.1, a, -.6)
        strut(kit, f'Bridge yoke strut {name}', (19.2, y, fz+.3), head, .8, .7, kit.steel, up=(0, 1, 0))
        kit.box(f'Bridge yoke foot {name}', (19.2, y, fz+.2), (1.6, 1.6, .45), kit.dark, .06)
    # Conduits from the cradle into the capsule's underside, clamped once.
    for i, a in enumerate((205, 222, 239), 1):
        dy, dz = poly(a, 5.2+.42)
        kit.cylinder(f'Bridge conduit {i}', ((21.6+25.4)/2, CY+dy, CZ+dz), .24, 3.8, kit.dark, axis='X', vertices=8, bevel=0)
    panel(kit, 'Bridge conduit clamp', 23.4, 23.9, 198, 246, .05, .78, kit.steel)

    # Top airlock between rings 1 and 2, on the inboard crown clear of the bars.
    coaming = panel(kit, 'Bridge airlock coaming', 22.55, 25.0, 112, 150, .2, .62, kit.dark, .04)
    panel(kit, 'Bridge airlock hatch', 22.8, 24.75, 116, 146, .3, .78, kit.steel, .06)
    for i, x in enumerate((23.2, 24.35), 1):
        panel(kit, f'Bridge airlock dog {i}', x-.15, x+.15, 112.5, 117, .3, .9, kit.steel, .02)
    dy, dz = poly(131, 5.2+1.0)
    w = kit.cylinder('Bridge airlock handwheel', (23.78, CY+dy, CZ+dz), .5, .16, kit.dark, vertices=8, bevel=0)
    radial(kit, None, w, 131)

    # Sensor mast on ring 1's crown, with an array, a dish and its lamp.
    mx, my = RINGS[0], 11.0
    mz = CZ+RING_OUT[1]*math.sqrt(1-((my-CY)/RING_OUT[0])**2)
    kit.box('Bridge sensor mast step', (mx, my, mz+.15), (1.5, 1.3, .5), kit.dark, .06)
    kit.cylinder('Bridge sensor mast', (mx, my, mz+2.2), .2, 4.0, kit.steel, vertices=6, bevel=0)
    kit.box('Bridge sensor mast yard', (mx, my, mz+3.3), (.25, 2.4, .22), kit.steel, 0)
    arr = kit.box('Bridge sensor array', (mx+.15, my, mz+4.15), (.18, 1.7, 1.0), kit.dark, .03)
    arr.rotation_euler[1] = math.radians(-12)
    kit.cylinder('Bridge masthead lamp', (mx, my, mz+4.85), .16, .3, lens_white, vertices=6, bevel=0)
    kit.box('Bridge sensor dish arm', (mx+.45, my, mz+1.8), (.9, .2, .2), kit.steel, 0)
    dish = kit.cylinder('Bridge sensor dish', (mx+1.0, my, mz+1.95), .75, .2, kit.cream, vertices=10, bevel=.03)
    dish.rotation_euler[1] = math.radians(60)
    # Whip antennas on the capsule crown aft of the airlock.
    for i, (x, a, h) in enumerate(((22.0, 70, 2.6), (25.6, 125, 2.0)), 1):
        dy, dz = poly(a, 5.2+.26)
        kit.cylinder(f'Bridge whip antenna {i} base', (x, CY+dy, CZ+dz+.1), .2, .3, kit.dark, vertices=6, bevel=0)
        kit.cylinder(f'Bridge whip antenna {i}', (x, CY+dy, CZ+dz+h/2), .06, h, kit.steel, vertices=4, bevel=0)

    # Navigation lamp housings (unlit lenses: the sprite's light layer shows none here).
    for x, a, lens, tag in ((36.2, -22, lens_red, 'port'), (32.2, 160, lens_white, 'inboard')):
        h = panel(kit, f'Bridge nav lamp {tag} housing', x-.45, x+.45, a-6, a+6, .2, .6, kit.dark, .03)
        panel(kit, f'Bridge nav lamp {tag} lens', x-.3, x+.3, a-4, a+4, .3, .75, lens, 0)
