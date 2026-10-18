"""Shepherd drives: the two main drive pods on the aft crossbeam and the four
maneuvering-jet clusters at its tips (the maneuveringjets system).

Engine slots from shepherd.ship, in file order (index i = 1..6):
  1 (-40, 20) w16 angle 180, 2 (-40, -19) w16 angle 180 - the main drives;
  3 (-22, 35) w2 140, 4 (-30, 33) w3 145, 5 (-22, -33) w2 220,
  6 (-30, -32) w3 215 - small LOW_TECH jets angled out and aft.
Read off drone_tender.png, each main drive is a whole burnt-orange mound
(X -14..-34, Y 12..27) ending in a grey bell (X -34..-41, Y 15..24): the
pod is the whole mound, not a nozzle stuck on the hull.

Pass 2 detail, all read off the sprite or demanded by function:
- The casing is segmented armour: a faceted 12-sided barrel lofted along X
  with dark seam grooves between modules, and two proud steel saddle bands
  that clamp it to the crossbeam (the aft one gripping the beam's aft edge)
  with bolted feet on the deck. A thrust yoke on the deck ahead of the cap
  and two thrust links to the forward band carry the drive's push into the
  crossbeam ('Engine <i> pylon' is the yoke).
- The sprite's round grey ring on each pod's outboard shoulder (X -24) is a
  bolted inspection hatch; the pale crest stripe on the port pod is a coolant
  trunk, the two red blocks on the starboard pod hazard placards. A recessed
  radiator fin well sits on the aft outboard shoulder (aft of the beam, so
  exposed), bolted service covers on the crest.
- Thick envelope (ENVELOPE.md): the casing is a taller oval, ry 8 x rz 10
  about the pod axis z = POD_Z (-1); the crossbeam deck is z +7, so only the
  pod's crest stands above the beam forward of X -29 and the bell, gimbal,
  anchors and jets sit on z = -1.
- The bell is a real low-tech nozzle: a contoured liner with film-cooling
  band steps, throat ring, injector face and pintle, heat-shield band and
  a stiffener hoop outside, a gimbal ring on two trunnions steered by two
  actuator rams from clevises on the casing's aft face, and a shroud collar round the
  well the bell sinks into.
- Each jet is a thruster block run out from the crossbeam tip along its slot
  angle: housing, face plate, the main nozzle, two small splayed vernier
  nozzles, a valve block and feed lines, and a mounting bracket.

Names follow the Second Wind: 'Engine <i> aft-facing bell', 'Engine <i>
mouth lip', 'Engine <i> dark throat', 'Engine <i> exhaust anchor' (an empty
on the mouth whose +X is the exhaust direction), plus 'Engine <i> drive pod
casing' / 'Engine <i> pylon' for the mains and 'Engine <i> jet housing' for
the jets. Everything added in pass 2 is 'Engine <i> ...' as well.
"""
import math
import bpy
from mathutils import Matrix, Vector
from sh_kit import material, pod, revolve_x, boolean

# (x, y, width, angle) exactly as shepherd.ship lists them.
ENGINES = [(-40, 20, 16, 180), (-40, -19, 16, 180), (-22, 35, 2, 140), (-30, 33, 3, 145),
           (-22, -33, 2, 220), (-30, -32, 3, 215)]
POD_R = 10.0    # drive pod casing: vertical half-height (thick envelope)
POD_Z = -1.0    # pod axis height: bells, throats and exhaust anchors sit on it
POD_RY = 8.0    # and half-width, the sprite's orange mound (Y 12..28)
# Casing envelope aft to fore (x, half-width, half-height): aft taper round
# the bell well, the barrel, the forward cap over the crossbeam.
ENVELOPE = [(-36, 5.5, 6.0), (-34.5, 7.5, 8.6), (-32, POD_RY, POD_R), (-16, POD_RY, POD_R), (-12.5, 6.0, 7.0)]
SEAMS = (-14.4, -22.6, -32.8)            # dark seam grooves between armour modules
BANDS = ((-17.8, -16.2), (-29.4, -28.0))  # steel saddle bands (x0, x1); the aft one grips the beam's aft edge
DECK_Z = 7.0                              # crossbeam deck (sh_hull crossbeam section, thick envelope)
MAIN_BELL = {'mouth_r': 4.7, 'throat_r': 2.8, 'length': 5.0}
ANCHORS = {}   # engine index -> (location, exhaust direction), filled by build()
N = 12         # casing facets
_M = {}


def _mats():
    if not _M:
        _M['liner'] = material('Drive bell liner', (.085, .09, .095), .75, .42)
        _M['rod'] = material('Actuator rod chrome', (.5, .52, .53), .9, .22)
        _M['hazard'] = material('Drive hazard placard red', (.5, .07, .06), .1, .55)
        _M['heat'] = material('Nozzle heat-blued steel', (.11, .1, .13), .8, .35)
    return _M


def envelope(x):
    """Casing (half-width, half-height) at x, linear between ENVELOPE stations."""
    for (x0, y0, z0), (x1, y1, z1) in zip(ENVELOPE, ENVELOPE[1:]):
        if x0 <= x <= x1:
            t = (x-x0)/(x1-x0)
            return y0+(y1-y0)*t, z0+(z1-z0)*t
    raise ValueError(x)


def _vertex(ry, rz, k):
    a = math.pi/N+2*math.pi*k/N
    return ry*math.cos(a), rz*math.sin(a)


def facet(ry, rz, deg):
    """Centre (dy, dz), outward unit normal and tangent of the casing facet
    centred at deg (0 = outboard flank, 90 = crest), on the outboard (+Y) side."""
    k = int(round((deg-30)/30))
    (y0, z0), (y1, z1) = _vertex(ry, rz, k), _vertex(ry, rz, k+1)
    t = Vector((0, y1-y0, z1-z0)).normalized()
    n = Vector((0, t.z, -t.y))
    return Vector((0, (y0+y1)/2, (z0+z1)/2)), n, t, (Vector((0, y1-y0, z1-z0))).length


def on_facet(kit, name, x, cy, s, deg, dims, mat, lift=0.0, slide=0.0, bevel=.05, x_env=None, cyl=None):
    """A box (dims = along X, along the facet, out of it) standing on a
    casing facet, its base `lift` off the surface; s = +1 outboard is +Y.
    cyl=(radius, vertices) makes a cylinder whose axis is the facet normal."""
    ry, rz = envelope(x if x_env is None else x_env)
    c, n, t, _ = facet(ry, rz, deg)
    c, n, t = Vector((0, s*c.y, c.z)), Vector((0, s*n.y, n.z)), Vector((0, s*t.y, t.z))
    p = Vector((x, cy, POD_Z))+c+n*(lift+dims[2]/2)+t*slide
    if cyl:
        ob = kit.cylinder(name, p, cyl[0], dims[2], mat, 'Z', cyl[1], bevel)
    else:
        ob = kit.box(name, p, dims, mat, bevel)
    ob.rotation_euler = (math.atan2(-n.y, n.z), 0, 0)
    return ob


def shell(kit, name, x0, x1, cy, grow, mat, chamfer=.15, bevel=.04):
    """Hoop band hugging the casing between x0 and x1, `grow` proud of it."""
    st = []
    for x, g in ((x0, grow*.35), (x0+chamfer, grow), (x1-chamfer, grow), (x1, grow*.35)):
        ry, rz = envelope(x)
        st.append((x, ry+g, rz+g))
    return pod(kit, name, st, cy, POD_Z, mat, N, bevel)


def bolts(kit, name, pts, mat, r=.17, h=.22, axis_rot=None):
    for k, p in enumerate(pts, 1):
        ob = kit.cylinder(f'{name} {k}', p, r, h, mat, 'Z', 6, 0)
        if axis_rot is not None:
            ob.rotation_euler = axis_rot


def place(objs, loc, angle):
    """Turn parts built mouth-at-origin, interior along +X (exhaust -X) so the
    exhaust points along the slot angle, and move them to the mouth."""
    turn = Matrix.Translation(loc) @ Matrix.Rotation(math.radians(angle-180), 4, 'Z')
    bpy.context.view_layer.update()
    for ob in objs:
        ob.matrix_world = turn @ ob.matrix_world


def bell_curve(r, rt, length, start=30, end=10):
    """Liner radius at depth a behind the mouth: a quadratic Bezier leaving
    the throat at `start` degrees and meeting the mouth at `end` degrees."""
    m1, m2 = math.tan(math.radians(start)), math.tan(math.radians(end))
    bc = min(max((r-rt-m2*length)/(m1-m2), .05*length), .95*length)
    pts = []
    for k in range(41):
        u = k/40
        b = 2*u*(1-u)*bc+u*u*length
        rho = (1-u)**2*rt+2*u*(1-u)*(rt+m1*bc)+u*u*r
        pts.append((length-b, rho))
    pts.sort()

    def rho(a):
        for (a0, r0), (a1, r1) in zip(pts, pts[1:]):
            if a <= a1:
                return r0+(r1-r0)*(a-a0)/max(a1-a0, 1e-9)
        return pts[-1][1]
    return rho


def nozzle(kit, i, r, rt, length, wall, bands=0, chamber=.12, lip=None, n=32):
    """Bell liner, mouth lip, throat ring, injector face and dark throat in the
    local frame (mouth at origin, interior along +X). Returns (parts, rho)."""
    m = _mats()
    rho = bell_curve(r, rt, length)
    ch = chamber*r
    inner = []
    if bands:
        joints = [length*j/bands for j in range(bands+1)]
        step = .012*r
        for j, (a0, a1) in enumerate(zip(joints, joints[1:])):
            for q in range(3):
                a = a0+(a1-a0)*q/2*.999
                inner.append((a, rho(a)-(0 if j == 0 else step*(a1-a)/(a1-a0))))
    else:
        inner += [(length*q/8, rho(length*q/8)) for q in range(9)]
    inner += [(length+ch*.5, rt*1.08), (length+ch, rt*1.12)]
    outer = [(a, rr+wall) for a, rr in [(length+ch, rt*1.12), (length+ch*.5, rt*1.08)]]
    outer += [(length*q/10, rho(length*q/10)+wall) for q in range(10, -1, -1)]
    parts = [revolve_x(kit, f'Engine {i} aft-facing bell', inner+outer, 0, 0, m['liner'], n)]
    lr = .07*r if lip is None else lip
    bpy.ops.mesh.primitive_torus_add(major_radius=r+wall/2, minor_radius=wall/2+lr, major_segments=n,
                                     minor_segments=8, location=(0, 0, 0), rotation=(0, math.pi/2, 0))
    parts.append(kit.finish(bpy.context.object, f'Engine {i} mouth lip', kit.grey))
    xi = length+ch
    parts.append(kit.cylinder(f'Engine {i} dark throat', (xi+.04*r, 0, 0), rt*1.12+wall*.5, .08*r, kit.dark, 'X', n, 0))
    parts.append(revolve_x(kit, f'Engine {i} throat ring',
                           [(length-.05*r, rho(length-.05*r)-.01), (length-.02*r, rt*.95), (length+.05*r, rt*.95),
                            (length+.05*r, rt*1.1), (length-.05*r, rho(length-.05*r)+wall*.5)], 0, 0, kit.steel, n))
    parts.append(kit.cylinder(f'Engine {i} injector pintle', (xi-.04*r, 0, 0), .09*r, .14*r, kit.steel, 'X', 12, .01))
    parts.append(revolve_x(kit, f'Engine {i} injector ring',
                           [(xi-.03*r, rt*.5), (xi-.03*r, rt*.58), (xi, rt*.58), (xi, rt*.5)], 0, 0, kit.steel, 24))
    return parts, rho


def ring_x(kit, name, x0, x1, r_in, r_out, mat, n=24, cy=0, cz=0):
    """Flat annulus along X (a collar plate or hoop)."""
    return revolve_x(kit, name, [(x0, r_in), (x1, r_in), (x1, r_out), (x0, r_out)], cy, cz, mat, n)


def stick(kit, name, a, b, radius, mat, verts=10, bevel=.02):
    """Cylinder from point a to point b."""
    a, b = Vector(a), Vector(b)
    ob = kit.cylinder(name, (a+b)/2, radius, (b-a).length, mat, 'Z', verts, bevel)
    ob.rotation_euler = (b-a).to_track_quat('Z', 'Y').to_euler()
    return ob


def main_bell(kit, i, loc, s, casing):
    """The main nozzle, its gimbal and its well, mouth at loc, exhaust -X."""
    m = _mats()
    b = MAIN_BELL
    r, rt, L = b['mouth_r'], b['throat_r'], b['length']
    wall = .22
    parts, rho = nozzle(kit, i, r, rt, L, wall, bands=5, lip=.18)
    out = lambda a: rho(a)+wall
    # Heat-shield band just inside the mouth and two stiffener hoops.
    parts.append(revolve_x(kit, f'Engine {i} heat-shield band',
                           [(.35, out(.35)-.02), (.45, out(.45)+.13), (1.55, out(1.55)+.13), (1.65, out(1.65)-.02)],
                           0, 0, m['heat'], 32))
    for k, a in enumerate((1.95,), 1):
        parts.append(ring_x(kit, f'Engine {i} bell stiffener hoop {k}', a, a+.22, out(a+.11)-.05, out(a+.11)+.16,
                            kit.steel, 32))
    # Gimbal ring hugging the bell, on two trunnions at the sides.
    g0, g1 = 2.55, 3.05
    ring_out = out(g1)+.6
    parts.append(ring_x(kit, f'Engine {i} gimbal ring', g0, g1, out(g0)-.04, ring_out, kit.steel, 24))
    for side in (1, -1):
        parts.append(kit.box(f'Engine {i} gimbal trunnion {"outboard" if side == s else "inboard"}',
                             ((g0+g1)/2, side*(ring_out+.25), 0), (.75, .7, 1.1), kit.steel, .06))
        parts.append(kit.cylinder(f'Engine {i} trunnion pin cap {"outboard" if side == s else "inboard"}',
                                  ((g0+g1)/2, side*(ring_out+.62), 0), .32, .12, m['rod'], 'Y', 12, .02))
    # Actuator rams from clevises on the casing's aft taper to lever ears
    # on the gimbal ring: one on the crest, one low outboard (pitch, yaw).
    for k, deg in enumerate((90, -40), 1):
        u = Vector((0, s*math.cos(math.radians(deg)), math.sin(math.radians(deg))))
        ear = Vector(((g0+g1)/2, 0, 0))+u*(ring_out+.25)
        # Clevis bolted proud of the casing's flat aft face.
        clevis = Vector((-36.25-loc.x, 0, 0))+u*5.0
        ear_box = kit.box(f'Engine {i} gimbal lever ear {k}', ear, (.5, .55, .55), kit.steel, .04)
        parts.append(ear_box)
        cl = kit.box(f'Engine {i} actuator clevis {k}', clevis, (.8, .9, .7), kit.steel, .05)
        cl.rotation_euler = (math.atan2(-u.y, u.z), 0, 0)
        parts.append(cl)
        mid = clevis+(ear-clevis)*.55
        parts.append(stick(kit, f'Engine {i} gimbal actuator {k}', clevis, mid, .3, kit.steel, 10))
        parts.append(stick(kit, f'Engine {i} gimbal actuator {k} rod', mid, ear, .15, m['rod'], 8))
    place(parts, loc, 180)
    # The bell sinks into a well in the casing's aft cap; a dark shroud
    # collar round the well shows the gimbal clearance.
    well = kit.cylinder(f'Temporary engine {i} bell well', (-35.2, loc.y, POD_Z), out(L)+.45, 3.6, kit.dark, 'X', 24, 0)
    boolean(casing, well)
    ring_x(kit, f'Engine {i} shroud collar', -36.15, -35.85, out(L)+.4, 4.3, kit.dark, 24, loc.y, POD_Z)
    ring_x(kit, f'Engine {i} shroud collar bolt ring', -36.3, -36.1, 3.95, 4.2, kit.steel, 24, loc.y, POD_Z)
    # Bolts round the casing's flat aft face, outside the collar.
    for k in range(10):
        t = 2*math.pi*(k+.5)/10
        kit.cylinder(f'Engine {i} aft face bolt {k+1}', (-36.08, loc.y+4.75*math.cos(t), POD_Z+5.1*math.sin(t)),
                     .2, .2, kit.steel, 'X', 6, 0)


def drive_pod(kit, i, x, y, s):
    """Casing, armour modules, bands, hatches, fins, yoke and links."""
    m = _mats()
    side = 'port' if s > 0 else 'starboard'
    # Casing: the envelope with a 0.3-deep groove at every seam.
    st = list(ENVELOPE)
    for g in SEAMS:
        for dx, d in ((-.2, 0), (-.15, .3), (.15, .3), (.2, 0)):
            ry, rz = envelope(g+dx)
            st.append((g+dx, ry-d, rz-d))
    st.sort()
    casing = pod(kit, f'Engine {i} drive pod casing', st, y, POD_Z, kit.orange, N, .05)
    casing['side'] = side
    kit.hull.append(casing)
    for k, g in enumerate(SEAMS, 1):
        a, b = envelope(g-.2), envelope(g+.2)
        pod(kit, f'Engine {i} armour seam {k}', [(g-.21, a[0]-.24, a[1]-.24), (g+.21, b[0]-.24, b[1]-.24)],
            y, POD_Z, kit.dark, N, 0)

    # Saddle bands clamping the casing to the crossbeam, bolted to its deck.
    for k, (x0, x1) in enumerate(BANDS, 1):
        tag = 'forward' if k == 1 else 'aft'
        shell(kit, f'Engine {i} {tag} saddle band', x0, x1, y, .32, kit.steel)
        ry, rz = envelope((x0+x1)/2)
        ztop = POD_Z+(rz+.32)*math.sin(math.radians(75))
        bolts(kit, f'Engine {i} {tag} saddle band bolt', [((x0+x1)/2, y+dy, ztop+.08) for dy in (-1.3, 0, 1.3)],
              kit.grey)
        for out_side in (1, -1):
            # Foot where the band meets the deck: the casing is about 6.3 off axis at z = 4.
            fy = y+out_side*5.2
            fx = (x0+x1)/2 if k == 1 else x1-.35
            kit.box(f'Engine {i} {tag} band foot {"outboard" if out_side == s else "inboard"}',
                    (fx, fy, DECK_Z+.35), (x1-x0+.8 if k == 1 else 1.3, 1.6, .7), kit.steel, .08)
            bolts(kit, f'Engine {i} {tag} band foot bolt {"outboard" if out_side == s else "inboard"}',
                  [(fx+dx, fy+out_side*.35, DECK_Z+.75) for dx in ((-.6, .6) if k == 1 else (0,))], kit.grey, .15, .14)

    # Pylon: the thrust yoke on the deck ahead of the forward cap, and two
    # thrust links from it over the cap to the forward band.
    yv = []
    for xx, hw, z0, z1 in ((-13.6, 3.2, DECK_Z-.3, DECK_Z+1.7), (-9.9, 2.4, DECK_Z-.3, DECK_Z+.9)):
        yv += [(xx, y-hw, z0), (xx, y+hw, z0), (xx, y+hw, z1), (xx, y-hw, z1)]
    kit.mesh(f'Engine {i} pylon', yv, [(0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)],
             kit.steel, .12)
    kit.box(f'Engine {i} pylon cheek plate', (-11.8, y, DECK_Z+1.25), (2.6, 3.6, .3), kit.grey, .05)
    bolts(kit, f'Engine {i} pylon cheek bolt', [(-11.8+dx, y+dy, DECK_Z+1.45) for dx in (-.8, .8) for dy in (-1.2, 1.2)],
          kit.dark, .14, .14)
    for k, dy in enumerate((-2.0, 2.0), 1):
        ztop = POD_Z+(envelope(-16.2)[1]+.32)*math.sin(math.radians(75))
        stick(kit, f'Engine {i} pylon thrust link {k}', (-10.6, y+dy, DECK_Z+.9), (-16.4, y+dy*1.05, ztop-.1), .3, kit.steel)
        kit.box(f'Engine {i} pylon thrust link lug {k}', (-16.6, y+dy*1.05, ztop+.05), (.7, .6, .5), kit.steel, .05)

    # Crest: the port pod's pale coolant trunk, the starboard pod's hazard placards.
    crest_z = POD_Z+POD_R*math.sin(math.radians(75))
    if s > 0:
        kit.box(f'Engine {i} dorsal coolant trunk', (-22.3, y-.8, crest_z+.35), (7.4, 1.3, .8), kit.cream, .2)
        for k, xx in enumerate((-19.2, -22.3, -25.4), 1):
            kit.box(f'Engine {i} coolant trunk strap {k}', (xx, y-.8, crest_z+.32), (.35, 1.7, .9), kit.steel, .05)
    else:
        for k, xx in enumerate((-20.5, -24.5), 1):
            kit.box(f'Engine {i} hazard placard {k}', (xx, y+.6, crest_z+.06), (2.3, 1.9, .14), m['hazard'], .03)
            kit.box(f'Engine {i} hazard placard {k} frame', (xx, y+.6, crest_z+.02), (2.6, 2.2, .1), kit.dark, .02)
    # Bolted service covers on the crest, aft module and forward of the band.
    for k, (xx, cy, ln, wd) in enumerate(((-31.0, y, 2.6, 2.8), (-26.9, y+s*.9, 1.6, 1.8)), 1):
        kit.box(f'Engine {i} crest service cover {k}', (xx, cy, crest_z+.06), (ln, wd, .16), kit.orange, .04)
        bolts(kit, f'Engine {i} crest service cover {k} bolt',
              [(xx+ex, cy+ey, crest_z+.16) for ex in (-ln/2+.3, ln/2-.3) for ey in (-wd/2+.3, wd/2-.3)],
              kit.steel, .12, .1)

    # The sprite's grey ring on the outboard shoulder: an inspection hatch.
    on_facet(kit, f'Engine {i} shoulder inspection hatch rim', -24.6, y, s, 60, (0, 0, .35), kit.grey,
             -.05, slide=1.0, cyl=(1.45, 16))
    on_facet(kit, f'Engine {i} shoulder inspection hatch', -24.6, y, s, 60, (0, 0, .38), kit.dark, -.05, slide=1.0, cyl=(.95, 16))
    on_facet(kit, f'Engine {i} shoulder hatch locking wheel', -24.6, y, s, 60, (0, 0, .2), kit.steel, .3, slide=1.0, cyl=(.5, 8))
    # Forward module: a framed access panel on the outboard shoulder.
    on_facet(kit, f'Engine {i} shoulder access panel frame', -20.2, y, s, 60, (3.6, 2.2, .12), kit.dark, -.04, slide=1.0)
    on_facet(kit, f'Engine {i} shoulder access panel', -20.2, y, s, 60, (3.2, 1.8, .14), kit.orange, -.02, slide=1.0)
    on_facet(kit, f'Engine {i} shoulder access panel grab bar', -20.2, y, s, 60, (2.0, .3, .25), kit.steel, .1,
             slide=1.4)
    # Radiator fin well on the aft outboard shoulder, cut into the casing.
    xs = (-32.3, -29.75)
    cut = on_facet(kit, f'Temporary engine {i} radiator well cutter', sum(xs)/2, y, s, 30,
                   (xs[1]-xs[0], 2.7, 1.4), kit.dark, -.7, bevel=0, x_env=-31)
    boolean(casing, cut)
    on_facet(kit, f'Engine {i} radiator well floor', sum(xs)/2, y, s, 30, (xs[1]-xs[0], 2.7, .12), kit.dark,
             -.72, x_env=-31, bevel=0)
    for k in range(6):
        on_facet(kit, f'Engine {i} radiator fin {k+1}', xs[0]+.3+k*.4, y, s, 30, (.12, 2.5, .66), kit.steel,
                 -.66, x_env=-31, bevel=0)
    return casing


def jet(kit, i, x, y, w, angle):
    """A thruster block on the crossbeam tip: the pass-1 housing footprint,
    with a nozzle block on its outer corner turned to the slot angle."""
    m = _mats()
    d = Vector((math.cos(math.radians(angle)), math.sin(math.radians(angle)), 0))
    loc = Vector((x, y, POD_Z))+d*.2
    s = 1 if y > 0 else -1
    # Housing: the blockout's box, run out from the crossbeam tip.
    inner = 29.0 if x > -26 else 26.0
    hy = s*(inner+abs(y))/2-s*.8
    hw = abs(y)-inner
    kit.box(f'Engine {i} jet housing', (x+.5, hy, POD_Z), (5, hw, 6.0), kit.steel, .2)
    kit.box(f'Engine {i} jet housing seam', (x+.5, hy, POD_Z), (.14, hw+.04, 6.04), kit.dark, 0)
    for k, dx in enumerate((-1.6, 2.1), 1):
        kit.box(f'Engine {i} jet housing strap {k}', (x+.5+dx, hy, POD_Z), (.45, hw+.2, 6.25), kit.grey, .06)
    vy = hy+s*(hw/2-1.3)
    kit.box(f'Engine {i} jet valve block', (x+1.4, vy, POD_Z+3.3), (1.6, 1.8, .7), kit.grey, .08)
    for k, sx in enumerate((-.45, .45), 1):
        # Propellant lines run inboard from the valve into the crossbeam's flank.
        kit.cylinder(f'Engine {i} jet feed line {k}', (x+1.4+sx, vy-s*1.6, POD_Z+3.25), .2, 2.6, kit.steel, 'Y', 8, .02)
    # Dorsal and ventral verniers (roll and pitch) on the housing.
    for tag, sz in (('dorsal', 1), ('ventral', -1)):
        vx, vy, vr = x-.9, hy+s*(hw/2-.9), .42
        noz = revolve_x(kit, f'Engine {i} vernier nozzle {tag}', [(0, vr), (0, vr+.09), (.7, vr*.6+.09), (.7, vr*.6)],
                        0, 0, m['liner'], 12)
        thr = kit.cylinder(f'Engine {i} vernier nozzle {tag} throat', (.62, 0, 0), vr*.62, .1, kit.dark, 'X', 12, 0)
        # Built along +X interior; turn the exhaust (-X) to +Z or -Z.
        tm = Matrix.Translation((vx, vy, POD_Z+sz*3.6)) @ Matrix.Rotation(math.radians(-90*sz), 4, 'Y')
        bpy.context.view_layer.update()
        for ob in (noz, thr):
            ob.matrix_world = tm @ ob.matrix_world
        kit.cylinder(f'Engine {i} vernier nozzle {tag} boss', (vx, vy, POD_Z+sz*3.05), vr+.25, .3, kit.grey, 'Z', 12, .04)
    # Nozzle block turned to the slot angle, the main nozzle in its face.
    r = .5+.35*w
    L = 1.6*r
    parts, _ = nozzle(kit, i, r, r*.55, L, .14, lip=.06, n=20)
    parts.append(kit.box(f'Engine {i} jet nozzle block', (L*.75+1.1, 0, 0), (2.2, 2*r+.7, min(2*r+.5, 3.3)),
                         kit.steel, .15))
    parts.append(kit.box(f'Engine {i} jet face plate', (L*.75-.05, 0, 0), (.25, 2*r+.5, min(2*r+.3, 3.1)),
                         kit.grey, .06))
    parts.append(kit.box(f'Engine {i} jet heat baffle', (L*.75+.4, 0, -min(r+.25, 1.65)-.1), (.6, 2*r+.4, .3),
                         m['heat'], .03))
    place(parts, loc, angle)
    return loc, d


def build(kit):
    ANCHORS.clear()
    for i, (x, y, w, angle) in enumerate(ENGINES[:2], 1):
        s = 1 if y > 0 else -1
        casing = drive_pod(kit, i, x, y, s)
        loc = Vector((x-.5, y, POD_Z))
        main_bell(kit, i, loc, s, casing)
        d = Vector((math.cos(math.radians(angle)), math.sin(math.radians(angle)), 0))
        kit.empty(f'Engine {i} exhaust anchor', loc, angle)
        ANCHORS[i] = (tuple(round(v, 3) for v in loc), tuple(round(v, 4) for v in d))
    for i, (x, y, w, angle) in enumerate(ENGINES[2:], 3):
        loc, d = jet(kit, i, x, y, w, angle)
        kit.empty(f'Engine {i} exhaust anchor', loc, angle)
        ANCHORS[i] = (tuple(round(v, 3) for v in loc), tuple(round(v, 4) for v in d))
