"""Wolf landing gear and the designed underside (round 8, owner: "Underside and
landing needs to be figured out for this ship"; then, on the first design:
"I'd rather have 'wedges' that come out along the side of the under hull with
'thick' bracing. The ship should sit on its 'keel' with the edges stopping it
from rolling").

How it lands. The Wolf sits on its keel: two thick armoured keel shoes run
along the keel's bottom, fore and aft of the docking collar, and they are the
lowest thing on the hull, so the collar and the sensor blister stand clear of
the ground. A keel alone rolls, so four wedges (two a side, fore and aft) swing
down off the lower hull sides and plant their toes well outboard: the stance
is about 34 units (25 m) wide on a keel 2 units wide. In flight each wedge lies
flat against the lower hull side, its outer face part of the side armour. It
turns about a hinge along the bottom edge of the side, falling outward and
down about 130 degrees, until its cut toe sits flat on the ground.

Bracing. Each wedge is a thick tapered plate stiffened by heavy ribs on the
face that lies against the hull in flight, so on the ground the ribs stand on
its upper side. Two thick hydraulic rams brace it from above: they run from
deep in the main body, out through slots in the hull side that the stowed
wedge covers, down to its two end ribs. The roll load goes up the rams into
the main body and along the hinge line into the hull side. Four ventral lift thrusters in heat-shield tile rings set it
down on that stance, because the drives only push aft.

Motion. Each wedge is parented to the empty 'Hull <wedge> hinge' (rotation about
hull X; custom property stow_deg is its angle at gear_down 0). Each ram is two
parts: the cylinder on 'Hull <wedge> ram <k> pivot' at its hull end, and the rod
on 'Hull <wedge> ram <k> rod pivot' at the wedge, both aimed along the line
between the two ends. The geometry is built deployed; pose(t) sets every
rotation and position: 0 is stowed (the rest state), 1 deployed. The cinematic
export drives the same rotations and positions from the gear_down knob, with
the closed-form expressions in expressions().
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector

from wf_fittings import SIDES, down, frame, join, pipe, plate, put, side, strip, up

GROUND_Z = -19.4            # the parked ground plane in hull units: the keel shoes' soles
# (wedge, x0, x1): two a side, under the main body forward and the aft body.
WEDGES = [('fore wedge', 15.0, 24.0), ('aft wedge', -17.5, -8.5)]
HINGE_Y = 12.6              # the hinge line along the lower edge of the hull side
TOE_OUT = 4.2               # the toe lands this far outboard of the hinge line
ROOT_T, TIP_T = 2.4, 1.0    # wedge thickness at the hinge (1.8 m) and at the toe (0.8 m)
RAM_HULL = (8.0, 2.0)       # (|Y|, Z) of each ram's hull end, deep in the main body
# Ventral lift thrusters: (index, x, y). Two under the neck, two under the drive section.
LIFTS = [(1, 32.0, 5.2), (2, 32.0, -5.2), (3, -24.6, 5.6), (4, -24.6, -5.6)]
RECORD = {}


def heat_tile_material():
    from wf_kit import material
    return material('Wolf heat shield tile', (.035, .033, .032), .1, .75)


def hinge(name, loc, **props):
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_type, ob.empty_display_size = 'SINGLE_ARROW', 1.0
    ob.location = loc
    bpy.context.collection.objects.link(ob)
    for k, v in props.items():
        ob[k] = v
    return ob


def adopt(parent, objs):
    bpy.context.view_layer.update()
    inv = parent.matrix_world.inverted()
    for ob in objs:
        mw = ob.matrix_world.copy()
        ob.parent = parent
        ob.matrix_parent_inverse = inv
        ob.matrix_world = mw


def rounded(ob, width, segments=3):
    b = ob.modifiers.new('Rounded edge', 'BEVEL')
    b.width, b.segments, b.limit_method = width, segments, 'ANGLE'
    return ob


def lathe_z(kit, name, profile, x, y, mat, n=64):
    """Closed solid of revolution about the vertical axis through (x, y); profile [(r, z), ...] closed."""
    m = len(profile)
    verts = [(x+r*math.cos(2*math.pi*k/n), y+r*math.sin(2*math.pi*k/n), z) for k in range(n) for r, z in profile]
    faces = [(k*m+i, ((k+1) % n)*m+i, ((k+1) % n)*m+(i+1) % m, k*m+(i+1) % m) for k in range(n) for i in range(m)]
    ob = kit.mesh(name, verts, faces, mat)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-4)
    bmesh.ops.dissolve_degenerate(bm, edges=bm.edges, dist=1e-4)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def belly_range(kit, x, y, r):
    """(lowest, highest) belly z under a disc of radius r at (x, y)."""
    zs = []
    for k in range(13):
        rr = 0 if k == 0 else r
        a = 2*math.pi*k/12
        p, _ = up(kit, x+rr*math.cos(a), y+rr*math.sin(a))
        if p is not None:
            zs.append(p.z)
    return min(zs), max(zs)


def yz_prism(kit, name, poly, x0, x1, mat, bevel=0):
    """A solid extruded along X from a YZ polygon [(y, z), ...]."""
    n = len(poly)
    verts = [(x, y, z) for x in (x0, x1) for y, z in poly]
    faces = [tuple(range(n)), tuple(range(2*n-1, n-1, -1))]
    faces += [(i, n+i, n+(i+1) % n, (i+1) % n) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, bevel)


def rot(v, a):
    """v (y, z) turned by a about hull X."""
    c, s = math.cos(a), math.sin(a)
    return Vector((v[0]*c - v[1]*s, v[0]*s + v[1]*c))


def ang(v):
    return math.atan2(v[1], v[0])


def keel_shoes(kit):
    """The keel's two armoured shoes: the hull rests on these. Flat soles on the
    ground plane, ramped ends, fore and aft of the docking collar."""
    from wf_kit import loft_x
    steel = kit.steel
    shoes = []
    for name, x0, x1 in (('Hull keel shoe aft', -17.5, -2.4), ('Hull keel shoe fore', 6.4, 18.8)):
        st = []
        for i in range(25):
            x = x0 + (x1-x0)*i/24
            p, _ = up(kit, x, 0.0)
            top = p.z + .7
            ramp = min(1.0, (x-x0)/1.6, (x1-x)/1.6)
            g = GROUND_Z + (1-ramp)*.9
            hw = 1.25 + .1*ramp
            st.append((x, [(-hw, g), (hw, g), (hw+.4, g+.45), (hw+.4, top), (-hw-.4, top), (-hw-.4, g+.45)][::-1]))
        ob = loft_x(kit, name, st, steel, 0)
        rounded(ob, .06, 2)
        shoes.append({'name': name, 'x': [x0, x1], 'sole_z': GROUND_Z})
    RECORD['keel_shoes'] = shoes


def wedges(kit):
    from wf_kit import boolean
    red, steel, dark = kit.red, kit.steel, kit.dark
    chrome = bpy.data.materials.get('Wolf oleo chrome')
    if chrome is None:
        from wf_kit import material
        chrome = material('Wolf oleo chrome', (.55, .56, .58), 1.0, .18)
    out = []
    for wname, x0, x1 in WEDGES:
        xm = (x0+x1)/2
        for side_, s in SIDES:
            n = f'Hull {side_} {wname}'
            hp, _ = up(kit, xm, s*HINGE_Y)
            H = Vector((s*HINGE_Y, hp.z - .1))
            # Stowed, the wedge is the hull side's own shape: its outer face follows the
            # side from the hinge up, so it closes flush. Walk up the side until the chord
            # from the hinge is long enough to reach the ground when deployed.
            u = Vector((s*TOE_OUT, GROUND_Z - H.y)).normalized()     # deployed chord direction
            Lc = (GROUND_Z - .5 - H.y)/u.y                            # the outer toe is lowest deployed
            prof = []
            z = H.y + .15
            while z < H.y + 30:
                p, nn = side(kit, xm, z, s)
                if p is not None:
                    E = Vector((p.y + s*.05, p.z))
                    if (E - H).length > .3:
                        prof.append((E, Vector((nn.y, nn.z)).normalized()))
                        if (E - H).length >= Lc:
                            break
                z += .08
            prof = [(H.copy(), prof[0][1])] + prof
            arc = [0.0]
            for (a, _), (b, _) in zip(prof, prof[1:]):
                arc.append(arc[-1] + (b - a).length)
            pick = [min(range(len(arc)), key=lambda j: abs(arc[j] - arc[-1]*i/16)) for i in range(17)]
            prof = [prof[j] for j in sorted(set(pick))]
            arcs = [arc[j] for j in sorted(set(pick))]
            fr = [a/arcs[-1] for a in arcs]
            stow = (ang(prof[-1][0] - H) - ang(u) + math.pi) % (2*math.pi) - math.pi

            def dep(v):
                return H + rot(v - H, -stow)
            E = [dep(e) for e, _ in prof]
            I = [dep(e - nn*(ROOT_T + (TIP_T-ROOT_T)*f)) for (e, nn), f in zip(prof, fr)]
            RIB = [dep(e - nn*(ROOT_T + (TIP_T-ROOT_T)*f + 1.4 - .9*f)) for (e, nn), f in zip(prof, fr)]
            IN = [dep(e - nn*(ROOT_T + (TIP_T-ROOT_T)*f - .05)) for (e, nn), f in zip(prof, fr)]
            ground = kit.box(f'Temporary {n} ground clip', (xm, H.x, GROUND_Z + 20), (40, 60, 40), steel, 0)
            body = yz_prism(kit, n, [tuple(v) for v in E + I[::-1]], x0, x1, red)
            boolean(body, ground, 'INTERSECT', keep=True)
            rounded(body, .12, 3)
            parts = [body]
            # Ribs on the face that lies against the hull in flight: the bracing, deep at
            # the root, shallower toward the toe; on the ground they stand on top.
            for k, xr in enumerate((x0+.45, xm, x1-.45), 1):
                rib = yz_prism(kit, f'{n} rib {k}', [tuple(v) for v in IN + RIB[::-1]], xr-.35, xr+.35, steel)
                boolean(rib, ground, 'INTERSECT', keep=True)
                parts.append(rounded(rib, .07, 2))
            # A tread plate on the toe's cut sole.
            poly = E + I[::-1]
            ys = []
            for a, b in zip(poly, poly[1:] + poly[:1]):
                if (a.y - GROUND_Z)*(b.y - GROUND_Z) < 0:
                    ys.append(a.x + (b.x - a.x)*(GROUND_Z - a.y)/(b.y - a.y))
            ys = sorted(ys)[:1] + sorted(ys)[-1:]
            sole_y = (ys[0]+ys[-1])/2
            sole = kit.box(f'{n} sole', (xm, sole_y, GROUND_Z + .03), (x1-x0-.5, max(.4, abs(ys[-1]-ys[0])-.3), .06),
                           dark, 0)
            parts.append(sole)
            mid = len(prof)//2
            W_ram = (I[mid] + RIB[mid])/2
            # Hinge knuckles on the wedge; hull lugs between them stay with the hull.
            for k, (a, b) in enumerate(((x0+.2, x0+2.0), (xm-.9, xm+.9), (x1-2.0, x1-.2)), 1):
                parts.append(kit.cylinder(f'{n} knuckle {k}', ((a+b)/2, H.x, H.y), 1.0, b-a, steel, 'X', 48, .06))
            for k, (a, b) in enumerate(((x0+2.1, xm-1.0), (xm+1.0, x1-2.1)), 1):
                kit.cylinder(f'{n} hinge lug {k}', ((a+b)/2, H.x, H.y), .92, b-a, steel, 'X', 48, .06)
            kit.cylinder(f'{n} hinge pin', (xm, H.x, H.y), .42, x1-x0+.4, dark, 'X', 24, .02)
            bpy.data.objects.remove(ground, do_unlink=True)
            h = hinge(f'{n} hinge', (xm, H.x, H.y), open_axis='X', stow_deg=round(math.degrees(stow), 3),
                      note='built deployed; rotate stow_deg about hull X to stow (gear_down 0)')
            adopt(h, parts)
            rec = {'name': n, 'hinge': h.name, 'H': [round(H.x, 4), round(H.y, 4)], 'stow_deg': h['stow_deg'],
                   'x': [x0, x1], 'sole': sole.name, 'sole_y': round(sole_y, 4), 'rams': []}
            # Rams: hull end inside the main body, rod end on each end rib.
            P = Vector((s*RAM_HULL[0], RAM_HULL[1]))
            W = W_ram
            d = W - P
            dirv = Vector((0, d.x, d.y)).normalized()
            q = Vector((0, 0, 1)).rotation_difference(dirv).to_matrix().to_4x4()
            for k, xr in enumerate((x0+1.1, x1-1.1), 1):
                pv = hinge(f'{n} ram {k} pivot', (xr, P.x, P.y), open_axis='X', note='hull end of the ram')
                rv = hinge(f'{n} ram {k} rod pivot', (xr, W.x, W.y), open_axis='X', note='wedge end of the ram')
                cyl_len, rod_len = .6*d.length, .62*d.length

                def along(name, start, length, r, mat):
                    ob = kit.cylinder(name, (0, 0, 0), r, length, mat, 'Z', 40, .04)
                    ob.matrix_world = Matrix.Translation(Vector((xr, start.x, start.y)) + dirv*length/2) @ q
                    return ob
                cyl = [along(f'{n} ram {k} cylinder', P, cyl_len, 1.05, steel),
                       along(f'{n} ram {k} gland', P + d.normalized()*(cyl_len-.22), .44, 1.22, dark)]
                rodp = [along(f'{n} ram {k} rod', W - d.normalized()*rod_len, rod_len, .7, chrome)]
                bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=1.0, location=(xr, W.x, W.y))
                rodp.append(kit.finish(bpy.context.object, f'{n} ram {k} eye', steel))
                adopt(pv, cyl)
                adopt(rv, rodp)
                # The slot the deployed ram leaves the hull side by (under the stowed wedge).
                for i in range(1, 80):
                    c = P + d*(i/80)
                    bp, bn = side(kit, xr, c.y, s)
                    if bp is not None and s*c.x > s*bp.y:
                        put(kit.box(f'{n} ram {k} slot', (0, 0, .02), (2.1, 2.6, .06), dark, 0), frame(bp, bn))
                        break
                rec['rams'].append({'pivot': pv.name, 'rod_pivot': rv.name, 'P': [round(P.x, 4), round(P.y, 4)],
                                    'W': [round(W.x, 4), round(W.y, 4)], 'base': round(ang(d), 6)})
            out.append(rec)
    RECORD['wedges'] = out


def wedge_state(rec, t):
    """(hinge angle, [(ram angle offset, rod pivot (y, z)), ...]) at gear_down t."""
    a = math.radians(rec['stow_deg'])*(1-t)
    H = Vector(rec['H'])
    rams = []
    for r in rec['rams']:
        W = H + rot(Vector(r['W']) - H, a)
        rams.append((ang(W - Vector(r['P'])) - r['base'], W))
    return a, rams


def pose(gear_down):
    """0 = stowed (the rest state), 1 = deployed."""
    t = max(0.0, min(1.0, gear_down))
    for rec in RECORD.get('wedges', []):
        a, rams = wedge_state(rec, t)
        bpy.data.objects[rec['hinge']].rotation_euler.x = a
        for r, (da, W) in zip(rec['rams'], rams):
            bpy.data.objects[r['pivot']].rotation_euler.x = da
            rv = bpy.data.objects[r['rod_pivot']]
            rv.location.y, rv.location.z = W.x, W.y
            rv.rotation_euler.x = da
    bpy.context.view_layer.update()


def expressions(rec):
    """Driver expressions in the knob k: [(object, data path, index, expression), ...].

    Blender truncates a driver expression at 256 characters, so constants are
    folded and rounded to 5 decimals (a ten-thousandth of a unit)."""
    def f(v):
        return f'{v:.5f}'.rstrip('0').rstrip('.')
    a = f"({f(math.radians(rec['stow_deg']))}*(1-k))"
    out = [(rec['hinge'], 'rotation_euler', 0, a)]
    hy, hz = rec['H']
    for r in rec['rams']:
        dy, dz = r['W'][0] - hy, r['W'][1] - hz
        wy = f"{f(hy)}+{f(dy)}*cos({a})-{f(dz)}*sin({a})"
        wz = f"{f(hz)}+{f(dy)}*sin({a})+{f(dz)}*cos({a})"
        ry = f"{f(hy - r['P'][0])}+{f(dy)}*cos({a})-{f(dz)}*sin({a})"
        rz = f"{f(hz - r['P'][1])}+{f(dy)}*sin({a})+{f(dz)}*cos({a})"
        da = f"atan2({rz},{ry})-({f(r['base'])})"
        out += [(r['pivot'], 'rotation_euler', 0, da), (r['rod_pivot'], 'rotation_euler', 0, da),
                (r['rod_pivot'], 'location', 1, wy), (r['rod_pivot'], 'location', 2, wz)]
    assert all(len(e[3]) < 256 for e in out), max(len(e[3]) for e in out)
    return out


def gear(kit):
    keel_shoes(kit)
    wedges(kit)
    pose(0.0)


def lifts(kit):
    """Ventral lift thrusters: a lipped nozzle ring, turning vanes, a dark throat and a
    ring of heat-shield tiles round each, all seated on the belly."""
    steel, dark = kit.steel, kit.dark
    tile = heat_tile_material()
    out = []
    for i, x, y in LIFTS:
        p, nrm = up(kit, x, y)
        zb = belly_range(kit, x, y, 2.6)[0]
        z = zb - .05
        lathe_z(kit, f'Hull lift {i} nozzle', [(1.55, z+.6), (1.55, z-.25), (2.05, z-.38), (2.25, z-.2),
                                              (2.25, z+.6)], x, y, steel, 64)
        kit.cylinder(f'Hull lift {i} throat', (x, y, z+.3), 1.56, .1, dark, 'Z', 64, 0)
        vanes = [kit.box('vane', (x-1.2+k*.4, y, z+.0), (.09, 2.9, .5), steel, 0) for k in range(7)]
        for v in vanes:
            v.matrix_world = Matrix.Translation(v.location) @ Matrix.Rotation(math.radians(14), 4, 'Y')
        cut = kit.cylinder(f'Temporary lift {i} vane trim', (x, y, z), 1.5, 2.0, dark, 'Z', 64, 0)
        vj = join(vanes, f'Hull lift {i} vanes')
        from wf_kit import boolean
        boolean(vj, cut, 'INTERSECT')
        tiles = []
        for k in range(16):
            a = 2*math.pi*(k+.5)/16
            for ring, rr in enumerate((2.75, 3.45)):
                tx, ty = x+rr*math.cos(a), y+rr*math.sin(a)
                tp, tn = up(kit, tx, ty)
                if tp is None:
                    continue
                m = frame(tp, tn, math.degrees(a))
                tiles.append(put(kit.box('tile', (0, 0, .06), (.62, (1.05 if ring else .8), .14), tile, .03), m))
        join(tiles, f'Hull lift {i} heat shield tiles')
        a = kit.empty(f'Lift {i} exhaust anchor', (x, y, z-.38), 0, size=1.0)
        a.rotation_euler = (0, math.radians(90), 0)            # local +X points down, the exhaust axis
        out.append({'index': i, 'location': [x, y, round(z-.38, 3)], 'exhaust_direction': [0, 0, -1],
                    'mouth_radius': 1.55, 'anchor': a.name})
    RECORD['lift_thrusters'] = out


def keel(kit):
    """Keel frames across the keel, and a heat-shield tile field either side of the
    keel shoes, clear of the sensor blister and the docking collar."""
    steel, dark = kit.steel, kit.dark
    tile = heat_tile_material()
    frames = []
    for x in [-16.0+i*4.0 for i in range(11)]:
        if abs(x-2.0) < 3.2 or abs(x-22.0) < 3.2:
            continue
        pts = [up(kit, x, y) for y in [-4.9+j*.49 for j in range(21)]]
        st = strip(kit, f'Hull keel frame {x:+.0f}', [(p, n) for p, n in pts if p is not None and n.z < -.5], .5, .16, steel)
        if st:
            frames.append(st)
    join(frames, 'Hull keel frames')
    tiles = []
    for x in [-15.0+i*1.9 for i in range(21)]:
        if abs(x-2.0) < 3.0 or abs(x-22.0) < 3.0:
            continue
        for y in (-3.3, -2.3, 2.3, 3.3):
            p, n = up(kit, x, y)
            if p is None or n.z > -.85:          # only where the keel faces down
                continue
            tiles.append(put(kit.box('tile', (0, 0, .05), (1.75, .9, .12), tile, .03), frame(p, n)))
    join(tiles, 'Hull keel heat shield tiles')


def hatches_and_vents(kit):
    steel, red, dark = kit.steel, kit.red, kit.dark
    # A ventral boarding hatch under the head, with a fold-down ladder housing beside it.
    p, n = up(kit, 37.0, 0.0)
    m = frame(p, n)
    plate(kit, 'Hull ventral boarding hatch', m, 2.6, 2.0, steel)
    put(rounded(kit.box('Hull ventral boarding hatch ladder housing', (0, 1.8, .15), (2.4, .7, .3), steel, 0), .06), m)
    # Ventral service hatches on the main body between the gear sponsons.
    for side_, s in SIDES:
        for k, x in enumerate((7.0, -2.0), 1):
            p, n = up(kit, x, s*8.6)
            plate(kit, f'Hull {side_} ventral service hatch {k}', frame(p, n), 2.2, 1.6, red)
        # Drains and vents: a drain grille on the aft body, a vent grille on the drive section.
        for k, (x, y) in enumerate(((-6.0, 11.6), (-27.5, 2.2)), 1):
            p, n = up(kit, x, s*y)
            if p is None:
                continue
            m = frame(p, n)
            put(rounded(kit.box(f'Hull {side_} ventral vent {k} frame', (0, 0, .06), (1.9, 1.2, .14), steel, 0),
                        .05), m)
            put(kit.box(f'Hull {side_} ventral vent {k} well', (0, 0, .1), (1.6, .9, .1), dark, 0), m)
            slats = [put(kit.box('slat', (-.6+j*.24, 0, .12), (.07, .86, .16), steel, 0), m) for j in range(6)]
            join(slats, f'Hull {side_} ventral vent {k} slats')
        # Wing and pod undersides: a seam line along each wing and an access panel.
        pts = [up(kit, x, s*(17.0 + (x+12)*.12)) for x in [-12.0+i*.6 for i in range(36)]]
        strip(kit, f'Hull {side_} wing ventral seam', [(p, n) for p, n in pts if p is not None], .2, .07, dark)
        p, n = up(kit, 2.0, s*20.0)
        if p is not None:
            plate(kit, f'Hull {side_} wing ventral panel', frame(p, n), 2.4, 1.6, steel)
        # A ventral sensor window under each outrigger.
        p, n = up(kit, 38.0, s*13.0)
        if p is not None:
            m = frame(p, n)
            put(rounded(kit.box(f'Hull {side_} outrigger ventral sensor frame', (0, 0, .08), (1.6, 1.0, .2), steel, 0),
                        .06), m)
            put(kit.box(f'Hull {side_} outrigger ventral sensor glass', (0, 0, .1), (1.2, .6, .1), kit.glass, 0), m)


def ventral_rcs(kit):
    """Down-firing RCS blocks: under the jaws, the wing tips and the drive section."""
    steel, dark = kit.steel, kit.dark
    for side_, s in SIDES:
        for where, x, y in (('bow', 47.0, 7.0), ('wing', 10.0, 21.0), ('stern', -26.0, 9.5)):
            p, n = up(kit, x, s*y)
            if p is None:
                continue
            m = frame(p - n*.5, n)
            parts = [rounded(kit.box('rcs', (0, 0, .3), (1.5, 1.2, .7), steel, 0), .08)]
            for dx in (-.35, .35):
                parts.append(kit.cylinder('rcs n', (dx, 0, .8), .22, .4, steel, 'Z', 24, .03))
                parts.append(kit.cylinder('rcs b', (dx, 0, 1.0), .15, .04, dark, 'Z', 24, 0))
            for o in parts:
                put(o, m)
            join(parts, f'Hull {side_} {where} ventral RCS cluster')


def build(kit):
    RECORD.clear()
    gear(kit)
    lifts(kit)
    keel(kit)
    hatches_and_vents(kit)
    ventral_rcs(kit)
    RECORD['ground_z_units'] = GROUND_Z


def record():
    return {'ground_z_units': GROUND_Z, 'keel_shoes': RECORD['keel_shoes'], 'wedges': RECORD['wedges'],
            'lift_thrusters': RECORD['lift_thrusters'],
            'note': 'rest is wedges stowed (flight); at gear_down 1 the hull rests on its keel shoes on '
                    'ground_z_units in hull space, the four wedges braced on the ground outboard'}
