"""The bone-white central spine, the command citadel and the aft dome.

onslaught_hegemony.png draws a pale spine down the middle of the core, from
the arch at X ~96 back to X ~-60, swelling into two domes:
  command dome  X ~6..44, the brightest part of the sprite, with a
                checkerboard arc across its forward face at X 30..39: the
                bridge glazing, looking forward along the prow; it tapers
                aft into the neck (the keyhole);
  aft dome      X ~-48..-16, a quieter helmet shape over the reactor spaces.
WS 025 (a decorative small hardpoint at X 17) sits on the command dome's
crown and is fitted here as the main sensor mast (os_fittings).

Round 4 (owner: the command tower read as a grain silo): the command dome is
an armoured command citadel for a low-tech brawler, lower and broader than the
round-3 tower and faceted, not round:
  drum          a battered twelve-sided armoured drum on the inner tier, hung
                with armour plates, one per flat, a sill on top, and a battered
                cheek slab on each flank;
  glazing       a recessed riser over the forward five flats carrying the
                1.4 m window band (kit.mpu), sill and head rails and mullions;
  brow          the riser's overhang all round, and over the forward three
                flats a heavy visor jutting past the drum, so the windows look
                out from under armour;
  dome          two faceted armour tiers stepping in to a flat crown, the
                facet seams strapped; a sensor crown (the mast's armoured
                pedestal, side blisters) and the crown hatch;
  bustle        a sloped armour block aft of the drum stepping down into the
                neck: the sprite's keyhole taper.
The spine's forward run (X 100..48) is a rising glacis from the prow gun deck
(os_hull: its last tier at 31.5 runs to the arch) up to the inner tier, so the
dorsal line climbs to the citadel; the pod and chevrons lie on that slope.

Pass 2 detail kept:
  aft dome      a helmet: a skirt, a ledge (the sprite's inner oval ring)
                and a raised, ribbed cap.
  neck          the pinched ridge between the domes, a saddle with a crest
                and a vent on each flank (the sprite's three dark dots).
  forward spine a raised pod (X 52..78) with the sprite's C-shaped recess,
                and three chevron steps (X 80..97) toward the arch.
  arch          a hoop across the spine's nose at X ~98 on two legs at Y
                +-8.2 (the sprite's two pale circles, capped by the sensor
                pods in os_fittings), its beam bedded on the spine's nose and
                the gun deck's end, with no daylight under it.
  spine         side steps down to the core deck fore and aft of the domes,
                and transverse ribs and vents on the aft spine.
Only the spine, the citadel, the aft dome and the sensor crown are seating
surfaces (kit.hull); the added parts are not, so they never lift another
module's seat.
"""
import math
import bpy
from mathutils import Matrix, Vector
from os_kit import boolean, ccw, facet_normal, loft_x, octagon, plane_cut, prism, section, strip_plan

# Under the citadel (X 56..4) the spine is narrowed to the citadel's own keel: the
# citadel and its bustle carry the sprite's keyhole there, a wide spine at the
# inner tier's height would show as a white skirt round the citadel's foot, and
# the glacis must stay inside the rims of WS 012/013 (os_turrets lifts a mount
# whose rim meets anything higher than its seat).
SPINE_HALF = [(100, 6), (88, 9), (60, 9.5), (56, 8.5), (4, 8.5), (-6, 22), (-16, 18), (-46, 18),
              (-58, 13), (-66, 8)]
# The bridge is drawn at its pass-2 heights below and then lifted LIFT as a whole
# (build), so it sits ON the raised citadel (os_hull: terrace 36, inner tier 40)
# like a brawler's head between its shoulders, not sunk into it. The spine's
# keel is drawn LIFT deeper so it still beds into the core and tail decks.
LIFT = 8.0
SPINE = section(0.0-LIFT, 25.0, 2.0, 2.5)    # keel buried deep: it seats on whatever deck is under it
AFT = {'x': -31.0, 'y': 0.0, 'r': 16.5, 'z0': 24.0, 'z1': 36.0}    # aft dome
SPINE_TOP = 25.0
SPINE_FALL = 22.0   # deg: the spine's fall toward the tail aft of X -50
# The forward spine's glacis (pre-lift z): 24 at the nose (X 100, just over the
# gun deck's last tier) rising to 34 (42 after the lift, two over the inner tier)
# where it runs into the citadel's drum at X 46; inside the drum it steps back
# down to SPINE_TOP.
GLACIS = ((100.0, 24.0), (46.0, 34.0))
# Human fittings in true metres, converted with the hull's scale (kit.mpu, set by
# the driver from ship_scale): the bridge windows are a 1.4 m band of 1.1 m panes,
# the crown hatch a 1.8 m access hatch, the aft dome's ledge hatches 0.9 x 1.4 m.
GLAZING_M = 1.4
PANE_M = 1.1
CROWN_HATCH_M = 1.8
SERVICE_HATCH_M = (.9, 1.4)

# The command citadel, in final heights (after the lift). Twelve flats, one
# facing the bow; CIT_A is the drum foot's apothem. Profile: (inset from the
# foot's flats, z) foot to crown. Repeated heights are horizontal steps: the
# walkway ledge, the brow's soffit over the glazing riser, the tier step.
CIT_C = (24.0, 0.0)
CIT_SIDES = 12
CIT_A = 21.7
CIT_PROFILE = [(0.0, 37.5), (1.6, 49.6),                 # battered drum, foot buried in the inner tier
               (2.8, 49.6), (2.8, 52.9),                 # sill, glazing riser
               (1.5, 52.9), (1.5, 54.8),                 # brow: soffit and face
               (4.6, 57.0), (5.6, 57.0),                 # first armour tier, step
               (9.4, 59.6), (13.0, 61.2)]                # second tier, crown
CIT_LEDGE, CIT_RISER_TOP, CIT_RISER_IN = 49.6, 52.9, 2.8
VISOR_ARC = (-50.0, 50.0)    # the heavy brow over the forward three flats
GLAZE_ARC = (-75.0, 75.0)    # the forward five flats
AFT_RINGS = [(1.00, 0.00), (1.00, .18), (.95, .28), (.85, .28),            # skirt and ledge
             (.84, .40), (.78, .60), (.62, .80), (.38, .94), (.12, 1.0)]    # helmet cap
AFT_CAP = [(.84, .40), (.78, .60), (.62, .80), (.42, .925)]


def _ring_pt(d, rf, hf, a, stretch=1.0, out=0.0):
    r = d['r']*rf+out
    return (d['x']+stretch*r*math.cos(a), d['y']+r*math.sin(a), d['z0']+(d['z1']-d['z0'])*hf)


def _dome(kit, name, d, mat, rings, n=48, stretch=1.0):
    """A faceted dome of (radius fraction, height fraction) rings, capped. stretch elongates along X."""
    verts, faces = [], []
    for rf, hf in rings:
        verts += [_ring_pt(d, rf, hf, 2*math.pi*(i+.5)/n, stretch) for i in range(n)]
    faces.append(tuple(reversed(range(n))))
    for k in range(len(rings)-1):
        faces += [(k*n+i, k*n+(i+1) % n, (k+1)*n+(i+1) % n, (k+1)*n+i) for i in range(n)]
    top = (len(rings)-1)*n
    faces.append(tuple(range(top, top+n)))
    return kit.mesh(name, verts, faces, mat)


def _sweep(kit, name, d, paths, mat, stretch=1.0):
    """Raised bars lying on a dome. Each path is (bearing deg, [(rf, hf), ...], width, proud):
    a rectangular section swept up the profile, sunk .3 into the surface."""
    verts, faces = [], []
    for bearing, prof, width, proud in paths:
        a = math.radians(bearing)
        side = Vector((-math.sin(a), math.cos(a), 0))
        base = len(verts)
        for rf, hf in prof:
            for out in (-.3, proud):
                p = Vector(_ring_pt(d, rf, hf, a, stretch, out))
                verts += [tuple(p-side*width/2), tuple(p+side*width/2)]
        m = len(prof)
        # per station: 0 inner-left, 1 inner-right, 2 outer-left, 3 outer-right
        for s in range(m-1):
            a0, a1 = base+4*s, base+4*(s+1)
            for i, j in ((0, 2), (2, 3), (3, 1), (1, 0)):
                faces.append((a0+i, a0+j, a1+j, a1+i))
        faces.append((base, base+1, base+3, base+2))
        e = base+4*(m-1)
        faces.append((e, e+2, e+3, e+1))
    return kit.mesh(name, verts, faces, mat)




def _ray_top(obs, x, y, top=150.0):
    """Highest (point, normal) on the given objects under (x, y)."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    best = None
    for ob in [o.evaluated_get(dg) for o in obs]:
        inv = ob.matrix_world.inverted()
        hit, p, nrm, _ = ob.ray_cast(inv @ Vector((x, y, top)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized())
        if hit:
            p = ob.matrix_world @ p
            if best is None or p.z > best[0].z:
                best = (p, (ob.matrix_world.to_3x3() @ nrm).normalized())
    return best


def _half_at(x):
    """Spine half-width at X (linear between SPINE_HALF stations)."""
    pts = SPINE_HALF
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        if xb <= x <= xa:
            return ya+(yb-ya)*(x-xa)/(xb-xa)
    return pts[0][1] if x > pts[0][0] else pts[-1][1]



# --- the command citadel --------------------------------------------------------

def _z(z):
    """A final height as drawn (the bridge is lifted LIFT after it is built)."""
    return z-LIFT


def _cit_pt(apo, bearing, z):
    """A point on the citadel's flats at apothem `apo`, bearing deg from the bow, final height z."""
    step = 360.0/CIT_SIDES
    d = (bearing+step/2) % step-step/2          # from the nearest flat's centre
    r = apo/math.cos(math.radians(d))
    b = math.radians(bearing)
    return (CIT_C[0]+r*math.cos(b), CIT_C[1]+r*math.sin(b), _z(z))


def _bearings(a0, a1):
    step = 360.0/CIT_SIDES
    corners = {step*(k+.5) for k in range(-CIT_SIDES, CIT_SIDES) if a0 < step*(k+.5) < a1}
    return sorted({a0, a1} | corners)


def _arc(kit, name, a0, a1, sec, mat, bevel=0):
    """A solid swept round the citadel's flats between bearings a0..a1: `sec` is a closed
    (apothem, final z) section, so the part follows the flats and sits flush on them."""
    bs = _bearings(a0, a1)
    n = len(sec)
    verts = [_cit_pt(a, b, z) for b in bs for a, z in sec]
    faces = [tuple(range(n)), tuple(range((len(bs)-1)*n, len(bs)*n))]
    for s in range(len(bs)-1):
        faces += [(s*n+i, s*n+(i+1) % n, (s+1)*n+(i+1) % n, (s+1)*n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, bevel)


def _boxes(kit, name, boxes, mat):
    """One mesh of oriented boxes: (centre, axes (u, v, w) orthonormal, half sizes (hu, hv, hw))."""
    verts, faces = [], []
    for c, axes, half in boxes:
        c = Vector(c)
        u, v, w = (Vector(a)*h for a, h in zip(axes, half))
        base = len(verts)
        for su in (-1, 1):
            for sv in (-1, 1):
                for sw in (-1, 1):
                    verts.append(tuple(c+u*su+v*sv+w*sw))
        q = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
        faces += [tuple(base+i for i in f) for f in q]
    return kit.mesh(name, verts, faces, mat)


def _wall_in(z):
    """The drum's batter: inset from the foot's flats at height z."""
    (i0, z0), (i1, z1) = CIT_PROFILE[0], CIT_PROFILE[1]
    return i0+(i1-i0)*(z-z0)/(z1-z0)


def _citadel(kit):
    step = 360.0/CIT_SIDES
    rc = CIT_A/math.cos(math.radians(step/2))
    plan = [(CIT_C[0]+rc*math.cos(math.radians(step*(k+.5))), CIT_C[1]+rc*math.sin(math.radians(step*(k+.5))))
            for k in range(CIT_SIDES)]
    cmd = prism(kit, 'Bridge command dome', plan, [(d, _z(z)) for d, z in CIT_PROFILE], kit.bone, bevel=.2)
    a = CIT_A
    # Armour plates on the drum, one per flat, with a seam at every corner.
    for k in range(CIT_SIDES):
        c = step*k
        sec = [(a-_wall_in(40.0)-.4, 40.0), (a-_wall_in(40.0)+.6, 40.0), (a-_wall_in(48.5)+.6, 48.5),
               (a-_wall_in(49.2)+.05, 49.2), (a-_wall_in(49.2)-.4, 49.2)]
        _arc(kit, f'Bridge citadel drum plate {k+1}', c-step/2+2.2, c+step/2-2.2, sec, kit.bone, bevel=.15)
    # Cheek armour: a battered slab on each flank, from the inner tier up under the
    # brow, so the citadel broadens to its foot and reads as a fortified mass.
    for side, s in (('port', 1), ('starboard', -1)):
        b0, b1 = sorted((s*80.0, s*140.0))
        _arc(kit, f'Bridge citadel cheek {side}', b0, b1,
             [(a-2.5, 35.5), (a+3.6, 35.5), (a+3.6, 40.4), (a-1.0, 53.3), (a-2.5, 53.3)], kit.bone, bevel=.2)
    # The glazing: a GLAZING_M band centred on the recessed riser across the forward flats,
    # with sill and head rails and a mullion every pane.
    riser = a-CIT_RISER_IN
    mid = (CIT_LEDGE+CIT_RISER_TOP)/2
    half = GLAZING_M/kit.mpu/2
    h0, h1 = mid-half, mid+half
    g0, g1 = GLAZE_ARC
    _arc(kit, 'Bridge glazing', g0, g1, [(riser-.35, h0), (riser+.15, h0), (riser+.15, h1), (riser-.35, h1)],
         kit.glass)
    for nm, z in (('Bridge glazing sill', h0-.14), ('Bridge glazing head rail', h1+.14)):
        _arc(kit, nm, g0-2.0, g1+2.0, [(riser-.3, z-.2), (riser+.4, z-.2), (riser+.4, z+.2), (riser-.3, z+.2)],
             kit.steel)
    width = 2*riser*math.tan(math.radians(step/2))
    panes = max(1, round(width/(PANE_M/kit.mpu)))
    bars = []
    for f in range(round(g0/step), round(g1/step)+1):
        c = math.radians(step*f)
        out, along = Vector((math.cos(c), math.sin(c), 0)), Vector((-math.sin(c), math.cos(c), 0))
        for i in range(panes+1):
            if i == 0 and f != round(g0/step):
                continue            # a corner mullion is shared with the flat before
            t = -width/2+width*i/panes
            p = Vector((CIT_C[0], CIT_C[1], _z(mid)))+out*(riser+.05)+along*t
            bars.append((p, (along, out, Vector((0, 0, 1))), (.14, .2, half+.05)))
    _boxes(kit, 'Bridge glazing frame', bars, kit.steel)
    _arc(kit, 'Bridge citadel riser band', g1+1.0, 360.0+g0-1.0,
         [(a-3.0, 49.4), (a-1.55, 49.4), (a-1.55, 53.1), (a-3.0, 53.1)], kit.bone)
    # The brow's visor: a heavy plate over the forward glazing, standing proud of the
    # brow as far as the drum plates' top edge (no further, so nothing overhangs but
    # the window slot) and sloping back into the first armour tier. Aft of the
    # glazing the slot is closed by a band flush with the brow, so the recess and its
    # brow face forward only.
    _arc(kit, 'Bridge command brow visor', *VISOR_ARC,
         [(a-2.6, 52.5), (a-.9, 52.3), (a-.9, 54.6), (a-1.6, 55.8), (a-5.2, 56.8), (a-5.2, 53.2)],
         kit.bone, bevel=.2)
    # Straps along the second tier's facet seams (the armour plates' joints).
    bars = []
    lo, hi = CIT_PROFILE[-5], CIT_PROFILE[-4]
    for k in range(CIT_SIDES):
        b = step*(k+.5)
        p0, p1 = Vector(_cit_pt(a-lo[0], b, lo[1])), Vector(_cit_pt(a-hi[0], b, hi[1]))
        along = (p1-p0).normalized()
        side = Vector((-math.sin(math.radians(b)), math.cos(math.radians(b)), 0))
        out = side.cross(along)
        if out.z < 0:
            out = -out
        bars.append(((p0+p1)/2+out*.08, (along, side, out), ((p1-p0).length/2, .5, .38)))
    _boxes(kit, 'Bridge command dome ribs', bars, kit.bone)
    # The sensor crown: an armoured pedestal for the mast (WS 025, os_fittings) and two
    # sensor blisters either side of the crown hatch.
    oc = [(17.0+4.6*math.cos(math.radians(22.5+45*k)), 4.6*math.sin(math.radians(22.5+45*k))) for k in range(8)]
    crown = prism(kit, 'Bridge citadel sensor crown', oc, [(0.0, _z(57.5)), (.5, _z(63.0)), (1.2, _z(63.7))],
                  kit.bone, bevel=.15)
    for side, s in (('port', 1), ('starboard', -1)):
        kit.box(f'Bridge citadel sensor blister {side}', (28.0, s*5.6, _z(61.9)), (3.2, 2.2, 1.8), kit.bone,
                bevel=.35)
    # The crown hatch forward of the mast (the sprite's dark disc).
    hit = _ray_top([cmd], 27.5, 0)
    z = hit[0].z if hit else _z(CIT_PROFILE[-1][1])
    hr = CROWN_HATCH_M/kit.mpu/2
    kit.cylinder('Bridge command dome hatch rim', (27.5, 0, z+.15), hr+.7, .9, kit.bone, vertices=16, bevel=.08)
    kit.cylinder('Bridge command dome hatch', (27.5, 0, z+.45), hr, .5, kit.dark, vertices=16, bevel=0)
    # The bustle: a sloped armour block tapering aft from the drum into the neck.
    st = [(12.0, octagon(-14.0, 14.0, _z(37.0), _z(55.0), 3.0, 0)),
          (2.0, octagon(-12.5, 12.5, _z(37.0), _z(52.5), 3.0, 0)),
          (-3.0, octagon(-10.0, 10.0, _z(37.0), _z(46.0), 2.6, 0))]
    loft_x(kit, 'Bridge command citadel bustle', st, kit.bone, bevel=.2)
    return cmd, crown


def _aft_dome_detail(kit):
    _sweep(kit, 'Bridge aft dome ribs', AFT, [(22.5+45*k, AFT_CAP, 1.4, .4) for k in range(8)], kit.bone,
           stretch=1.15)
    # Dark service hatches round the ledge: the sprite's tick marks.
    zl = AFT['z0']+(AFT['z1']-AFT['z0'])*.28
    for k in range(10):
        a = 2*math.pi*(k+.5)/10
        x, y, _ = _ring_pt(AFT, .90, .28, a, 1.15)
        ob = kit.box(f'Bridge aft dome ledge hatch {k+1}', (x, y, zl+.05),
                     (SERVICE_HATCH_M[0]/kit.mpu, SERVICE_HATCH_M[1]/kit.mpu, .5), kit.dark, bevel=0)
        ob.rotation_euler[2] = math.atan2(math.sin(a), 1.15*math.cos(a))


def _neck(kit):
    """The pinched ridge between the domes: a saddle with a crest and vents on its flanks."""
    st = [(-14.0, octagon(-6.5, 6.5, 20.0, 31.0, 2.6, 0)),
          (-6.0, octagon(-7.5, 7.5, 20.0, 32.2, 3.0, 0)),
          (2.0, octagon(-6.5, 6.5, 20.0, 31.6, 2.8, 0)),
          (6.0, octagon(-5.5, 5.5, 20.0, 30.0, 2.4, 0))]
    loft_x(kit, 'Bridge neck saddle', st, kit.bone, bevel=.2)
    # The crest rides the saddle's ridge line.
    crest = [(x+(1.0 if i == 0 else -1.0 if i == len(st)-1 else 0), sec) for i, (x, sec) in enumerate(st)]
    loft_x(kit, 'Bridge neck crest', [(x, octagon(-.8, .8, max(z for _, z in sec)-.6, max(z for _, z in sec)+1.0,
                                                     .3, 0)) for x, sec in crest], kit.bone, bevel=.1)
    # A louvred vent on each flank chamfer.
    for side, s in (('port', 1), ('starboard', -1)):
        ob = kit.box(f'Bridge neck vent {side}', (-8.0, s*5.95, 30.7), (6.0, .6, 2.2), kit.dark, bevel=0)
        ob.rotation_euler[0] = s*math.radians(45)




def _glacis(x):
    """The forward spine's top (pre-lift z) at X."""
    (xa, za), (xb, zb) = GLACIS
    t = min(max((xa-x)/(xa-xb), 0.0), 1.0)
    return za+(zb-za)*t


def _spine_top(x):
    """The spine's top (pre-lift z) along X: the glacis, level under the citadel, a raked
    step down behind it into the neck, level, then the fall toward the tail."""
    if x >= GLACIS[1][0]:
        return _glacis(x)
    if x >= 40.0:
        return GLACIS[1][1]
    if x >= 36.0:
        return GLACIS[1][1]+(SPINE_TOP-GLACIS[1][1])*(40.0-x)/4.0
    if x >= -50.0:
        return SPINE_TOP
    return SPINE_TOP-math.tan(math.radians(SPINE_FALL))*(-50.0-x)


def _spine(kit):
    xs = sorted({x for x, _ in SPINE_HALF} | {GLACIS[1][0], 40.0, 36.0, -50.0}, reverse=True)
    st = [(x, octagon(-_half_at(x), _half_at(x), SPINE[0][1], _spine_top(x), SPINE[-1][0], SPINE[0][0]))
          for x in xs]
    return loft_x(kit, 'Bridge spine', st, kit.bone, bevel=.12)


def _lay_on_glacis(obs, pivot_x=65.0):
    """Tilt parts drawn on the old level spine top (SPINE_TOP) down onto the glacis: a
    rotation about Y through (pivot_x, SPINE_TOP) and a lift to the glacis there."""
    (xa, za), (xb, zb) = GLACIS
    theta = math.atan2(zb-za, xa-xb)
    p = Vector((pivot_x, 0, SPINE_TOP))
    m = (Matrix.Translation((0, 0, _glacis(pivot_x)-SPINE_TOP)) @ Matrix.Translation(p) @
         Matrix.Rotation(theta, 4, 'Y') @ Matrix.Translation(-p))
    for ob in obs:
        ob.matrix_world = m @ ob.matrix_world


def _forward_spine(kit):
    before = set(bpy.data.objects)
    # The raised pod with its C-shaped recess (sprite X 55..76), drawn on the old level
    # spine top and laid on the glacis with the chevrons below.
    half = [(78, 0), (77, 3.5), (74, 6), (70, 7), (58, 7), (53, 5), (52, 0)]
    plan = ccw(half+[(x, -y) for x, y in reversed(half[1:-1])])
    pod = prism(kit, 'Bridge forward pod', plan, [(1.2, 24.0), (0, 25.4), (0, 27.0), (1.4, 28.4)], kit.bone)
    arc = [(66+4.6*math.cos(math.radians(a)), 4.6*math.sin(math.radians(a))) for a in range(-150, 151, 15)]
    cutter = prism(kit, 'Bridge forward pod recess cutter', ccw(strip_plan(arc, 1.8)),
                   [(0, 27.2), (0, 30.0)], kit.dark, bevel=0)
    boolean(pod, cutter)
    prism(kit, 'Bridge forward pod recess', ccw(strip_plan(arc, 1.8)), [(0, 26.9), (0, 27.3)], kit.dark, bevel=0)
    # The spike inside the C: a raised blade pointing aft (the sprite's arrow).
    kit.box('Bridge forward pod blade', (59.0, 0, 28.7), (9.0, 1.4, 1.2), kit.bone, bevel=.2)
    # Three chevron steps toward the arch, apex forward.
    for k, x0 in enumerate((94.0, 88.5, 83.0), 1):
        w = _half_at(x0-5.0)-1.6
        pts = [(x0-5.0, -w), (x0, 0), (x0-5.0, w)]
        prism(kit, f'Bridge spine chevron {k}', ccw(strip_plan(pts, 1.8)), [(0, 24.4), (.3, 26.0)], kit.bone,
              bevel=0)
    _lay_on_glacis(set(bpy.data.objects)-before)
    # The arch over the spine's nose: two legs at Y +-8.2 (the sprite's pale circles) and
    # a crossbeam bedded on the spine's nose and the gun deck's last tier (os_hull, 31.5
    # after the lift), so there is no daylight under it.
    for side, s in (('port', 1), ('starboard', -1)):
        kit.box(f'Bridge spine arch leg {side}', (98.0, s*8.2, 16.95), (3.6, 4.4, 18.9), kit.bone, bevel=.3)
    kit.box('Bridge spine arch', (98.0, 0, 24.9), (2.8, 21.0, 3.0), kit.bone, bevel=.3)


def _spine_detail(kit):
    # Side steps from the spine shoulder down to the core deck, fore and aft of the domes.
    for xa, xb, tag in ((94.0, 60.0, 'fore'), (-20.0, -64.0, 'aft')):
        edge = [(x, _half_at(x)+.6) for x in [xa+(xb-xa)*i/8 for i in range(9)]]
        for side, s in (('port', 1), ('starboard', -1)):
            pts = [(x, s*y) for x, y in edge]
            st = prism(kit, f'Bridge spine side step {side} {tag}', ccw(strip_plan(pts, 2.6)),
                       [(0, -LIFT), (0, 22.8), (.6, 23.4)], kit.bone, bevel=0)
            if tag == 'aft':
                plane_cut(st, 'step aft fall', (-50.0, 0, 23.4), facet_normal((-1, 0), SPINE_FALL))
    # Transverse ribs across the aft spine, behind the helmet, with dark vents between.
    fall = lambda x: SPINE_TOP-math.tan(math.radians(SPINE_FALL))*max(0.0, -50.0-x)
    for k, x in enumerate((-52.0, -57.0, -62.0), 1):
        w = _half_at(x)-2.8          # inside the spine's shoulder chamfer, so no end floats
        ob = kit.box(f'Bridge spine aft rib {k}', (x, 0, fall(x)+.35), (1.6, 2*w, 1.3), kit.bone, bevel=.2)
        ob.rotation_euler[1] = -math.radians(SPINE_FALL)
    for k, x in enumerate((-54.5, -59.5), 1):
        for side, s in (('port', 1), ('starboard', -1)):
            ob = kit.box(f'Bridge spine aft vent {side} {k}', (x, s*4.0, fall(x)+.05), (2.6, 4.2, .4), kit.dark,
                         bevel=0)
            ob.rotation_euler[1] = -math.radians(SPINE_FALL)




def build(kit):
    before = set(bpy.data.objects)
    spine = _spine(kit)
    cmd, crown = _citadel(kit)
    aft = _dome(kit, 'Bridge aft dome', AFT, kit.bone, AFT_RINGS, stretch=1.15)
    _aft_dome_detail(kit)
    _neck(kit)
    _forward_spine(kit)
    _spine_detail(kit)
    for ob in set(bpy.data.objects)-before:
        ob.location.z += LIFT
    bpy.context.view_layer.update()
    kit.hull += [spine, cmd, aft, crown]
    return {'spine': spine, 'command dome': cmd, 'aft dome': aft}
