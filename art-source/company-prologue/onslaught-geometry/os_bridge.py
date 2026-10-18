"""The bone-white central spine and its two domes.

onslaught_hegemony.png draws a pale spine down the middle of the core, from
the arch at X ~96 back to X ~-60, swelling into two domes:
  command dome  X ~6..44, the brightest part of the sprite, with a
                checkerboard arc across its forward face at X 30..39: the
                bridge glazing, looking forward along the prow;
  aft dome      X ~-48..-16, a quieter helmet shape over the reactor spaces.
WS 025 (a decorative small hardpoint at X 17) sits on the command dome's
crown and is fitted here as the main sensor mast (os_fittings).

Pass 2 (mid-distance detail, read off the sprite at 7-12x):
  command dome  terraced: a base drum and walkway ledge, then a recessed
                riser carrying the glazing under an overhanging brow, then
                a ribbed cap. The glazing is the sprite's checkerboard arc:
                a horseshoe round the forward half of the riser, broken
                into panes by a frame. A dark hatch on the crown forward of
                the mast is the sprite's dark disc.
  aft dome      a helmet: a skirt, a ledge (the sprite's inner oval ring)
                and a raised, ribbed cap.
  neck          the pinched ridge between the domes, a saddle with a crest
                and a vent on each flank (the sprite's three dark dots).
  forward spine a raised pod (X 52..78) with the sprite's C-shaped recess,
                and three chevron steps (X 80..97) toward the arch.
  arch          a hoop across the spine's nose at X ~97 on two legs at Y
                +-9.5 (the sprite's two pale circles, capped by the sensor
                pods in os_fittings).
  spine         side steps down to the core deck fore and aft of the domes,
                and transverse ribs and vents on the aft spine.
Only the spine and the two domes are pressure hull and seating surfaces
(kit.hull); the added parts are not, so they never lift another module's
seat.
"""
import math
import bpy
from mathutils import Vector
from os_kit import boolean, ccw, loft_x, octagon, prism, section, strip_plan

SPINE_HALF = [(98, 6), (88, 9), (60, 10), (46, 16), (40, 22), (10, 26), (-6, 22), (-16, 18), (-46, 18),
              (-58, 13), (-66, 8)]
SPINE = section(16.0, 25.0, 2.0, 2.5)
CMD = {'x': 24.0, 'y': 0.0, 'r': 21.0, 'z0': 24.0, 'z1': 38.0}     # command dome
AFT = {'x': -31.0, 'y': 0.0, 'r': 16.5, 'z0': 24.0, 'z1': 33.0}    # aft dome
SPINE_TOP = 25.0
# Human fittings in true metres, converted with the hull's scale (kit.mpu, set by
# the driver from ship_scale): the bridge windows are a 1.4 m band of 1.1 m panes,
# the crown hatch a 1.8 m access hatch, the aft dome's ledge hatches 0.9 x 1.4 m.
GLAZING_M = 1.4
PANE_M = 1.1
CROWN_HATCH_M = 1.8
SERVICE_HATCH_M = (.9, 1.4)

# (radius fraction, height fraction) keel to crown. Repeated heights are
# horizontal steps: the sill under the glazing and the brow's soffit over it.
CMD_RINGS = [(1.00, 0.00), (1.00, .10), (.97, .13), (.89, .13),            # drum, ledge, sill
             (.89, .40), (.97, .40), (.97, .47),                              # recessed riser, brow
             (.90, .56), (.78, .70), (.60, .84), (.36, .95), (.12, 1.0)]     # ribbed cap
CMD_RISER, CMD_GLAZE = .89, (.16, .37)
CMD_CAP = [(.97, .47), (.90, .56), (.78, .70), (.60, .84), (.40, .935)]
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


def _band(kit, name, d, rf, hf, a0, a1, half_h, out_in, out_out, mat, n=32):
    """A rail of square-ish section running round the dome at (rf, hf) between bearings a0..a1."""
    verts, faces = [], []
    for i in range(n+1):
        a = math.radians(a0+(a1-a0)*i/n)
        for out, dz in ((out_in, -half_h), (out_out, -half_h), (out_out, half_h), (out_in, half_h)):
            x, y, z = _ring_pt(d, rf, hf, a, 1.0, out)
            verts.append((x, y, z+dz))
    for s in range(n):
        for i in range(4):
            j = (i+1) % 4
            faces.append((4*s+i, 4*s+j, 4*(s+1)+j, 4*(s+1)+i))
    faces += [(3, 2, 1, 0), (4*n, 4*n+1, 4*n+2, 4*n+3)]
    return kit.mesh(name, verts, faces, mat)


def _glazing(kit, name, d, a0, a1, h0, h1, n=32, proud=.15, depth=.35):
    """Glass on the command dome's recessed riser between bearings a0..a1 (deg from the bow),
    following the riser's true circle so it sits flush and even."""
    verts, faces = [], []
    for out in (proud, -depth):
        for hf in (h0, h1):
            verts += [_ring_pt(d, CMD_RISER, hf, math.radians(a0+(a1-a0)*i/n), 1.0, out) for i in range(n+1)]
    m = n+1
    ol, oh, il, ih = 0, m, 2*m, 3*m     # outer-low, outer-high, inner-low, inner-high
    for i in range(n):
        faces += [(ol+i, ol+i+1, oh+i+1, oh+i), (il+i, ih+i, ih+i+1, il+i+1),
                  (ol+i, il+i, il+i+1, ol+i+1), (oh+i, oh+i+1, ih+i+1, ih+i)]
    for i in (0, n):
        faces.append((ol+i, oh+i, ih+i, il+i))
    return kit.mesh(name, verts, faces, kit.glass)


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


def _command_dome_detail(kit, cmd):
    # The glazing: a horseshoe of glass round the forward half of the riser,
    # framed into panes, with a sill and a head rail; a band GLAZING_M tall,
    # centred on the riser, with mullions every PANE_M.
    centre = sum(CMD_GLAZE)/2
    half = GLAZING_M/kit.mpu/(CMD['z1']-CMD['z0'])/2
    h0, h1 = max(centre-half, .14), min(centre+half, .39)
    arc = CMD['r']*CMD_RISER*math.radians(156)
    panes = max(8, round(arc/(PANE_M/kit.mpu)))
    _glazing(kit, 'Bridge glazing', CMD, -78, 78, h0, h1)
    step = 156/panes
    _sweep(kit, 'Bridge glazing frame', CMD,
           [(-78+step*k, [(CMD_RISER, h0-.02), (CMD_RISER, h1+.02)], .3, .25) for k in range(panes+1)], kit.steel)
    _band(kit, 'Bridge glazing sill', CMD, CMD_RISER, h0-.012, -80, 80, .18, -.3, .35, kit.steel)
    _band(kit, 'Bridge glazing head rail', CMD, CMD_RISER, h1+.012, -80, 80, .18, -.3, .35, kit.steel)
    # Segment ribs on the cap, clear of the mast drum on the aft crown.
    _sweep(kit, 'Bridge command dome ribs', CMD, [(15+30*k, CMD_CAP, 1.5, .45) for k in range(12)], kit.bone)
    # The crown: a dark hatch forward of the mast (the sprite's dark disc).
    hit = _ray_top([cmd], 27.5, 0)
    z = hit[0].z if hit else CMD['z1']
    hr = CROWN_HATCH_M/kit.mpu/2
    kit.cylinder('Bridge command dome hatch rim', (27.5, 0, z+.15), hr+.7, .9, kit.bone, vertices=16, bevel=.08)
    kit.cylinder('Bridge command dome hatch', (27.5, 0, z+.45), hr, .5, kit.dark, vertices=16, bevel=0)


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
    st = [(-14.0, octagon(-6.5, 6.5, 23.5, 28.0, 2.6, 0)),
          (-6.0, octagon(-7.5, 7.5, 23.5, 29.2, 3.0, 0)),
          (2.0, octagon(-6.5, 6.5, 23.5, 28.6, 2.8, 0)),
          (6.0, octagon(-5.5, 5.5, 23.5, 27.0, 2.4, 0))]
    loft_x(kit, 'Bridge neck saddle', st, kit.bone, bevel=.2)
    # The crest rides the saddle's ridge line.
    crest = [(x+(1.0 if i == 0 else -1.0 if i == len(st)-1 else 0), sec) for i, (x, sec) in enumerate(st)]
    loft_x(kit, 'Bridge neck crest', [(x, octagon(-.8, .8, max(z for _, z in sec)-.6, max(z for _, z in sec)+1.0,
                                                     .3, 0)) for x, sec in crest], kit.bone, bevel=.1)
    # A louvred vent on each flank chamfer.
    for side, s in (('port', 1), ('starboard', -1)):
        ob = kit.box(f'Bridge neck vent {side}', (-5.5, s*5.95, 27.7), (8.0, .6, 2.2), kit.dark, bevel=0)
        ob.rotation_euler[0] = s*math.radians(45)


def _forward_spine(kit):
    # The raised pod with its C-shaped recess (sprite X 55..76).
    half = [(78, 0), (77, 4), (74, 7), (70, 8.5), (58, 8.5), (53, 6), (52, 0)]
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
    # The arch over the spine's nose: two legs at Y +-9.5 (the sprite's pale circles) and a crossbeam.
    for side, s in (('port', 1), ('starboard', -1)):
        kit.box(f'Bridge spine arch leg {side}', (98.0, s*9.5, 25.8), (3.6, 3.6, 9.6), kit.bone, bevel=.3)
    kit.box('Bridge spine arch', (98.0, 0, 30.0), (2.8, 22.0, 2.2), kit.bone, bevel=.3)


def _spine_detail(kit):
    # Side steps from the spine shoulder down to the core deck, fore and aft of the domes.
    for xa, xb, tag in ((94.0, 60.0, 'fore'), (-20.0, -64.0, 'aft')):
        edge = [(x, _half_at(x)+.6) for x in [xa+(xb-xa)*i/8 for i in range(9)]]
        for side, s in (('port', 1), ('starboard', -1)):
            pts = [(x, s*y) for x, y in edge]
            prism(kit, f'Bridge spine side step {side} {tag}', ccw(strip_plan(pts, 2.6)),
                  [(0, 21.0), (0, 22.8), (.6, 23.4)], kit.bone, bevel=0)
    # Transverse ribs across the aft spine, behind the helmet, with dark vents between.
    for k, x in enumerate((-52.0, -57.0, -62.0), 1):
        w = _half_at(x)-1.2
        kit.box(f'Bridge spine aft rib {k}', (x, 0, SPINE_TOP+.35), (1.6, 2*w, 1.3), kit.bone, bevel=.2)
    for k, x in enumerate((-54.5, -59.5), 1):
        for side, s in (('port', 1), ('starboard', -1)):
            kit.box(f'Bridge spine aft vent {side} {k}', (x, s*4.0, SPINE_TOP+.05), (2.6, 4.2, .4), kit.dark,
                    bevel=0)


def build(kit):
    half = SPINE_HALF
    plan = ccw(list(half)+[(x, -y) for x, y in reversed(half)])
    spine = prism(kit, 'Bridge spine', plan, SPINE, kit.bone)
    cmd = _dome(kit, 'Bridge command dome', CMD, kit.bone, CMD_RINGS)
    aft = _dome(kit, 'Bridge aft dome', AFT, kit.bone, AFT_RINGS, stretch=1.15)
    _command_dome_detail(kit, cmd)
    _aft_dome_detail(kit)
    _neck(kit)
    _forward_spine(kit)
    _spine_detail(kit)
    kit.hull += [spine, cmd, aft]
    return {'spine': spine, 'command dome': cmd, 'aft dome': aft}
