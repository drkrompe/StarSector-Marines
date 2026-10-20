"""Wolf landing gear and the designed underside (round 8, owner: "Underside and
landing needs to be figured out for this ship").

How it lands. Four telescoping legs, each in a flat-bottomed gear sponson under
the main body, and four ventral lift thrusters, because the three drives only
push aft: a frigate that sets down on a pad or open ground has to hover on its
belly first. Telescoping legs suit an armed, fast hull better than the
alternatives the project already has:
- folding gear (the Broadsword's tricycle) needs long wells and hinged doors,
  and every hinged door is an open seam on the belly that takes fire;
- broad keel skids (the Colossus) are for a hauler that never needs a clean,
  fast hull, and they would hang below the keel the whole flight;
- a telescoping leg stows straight up into its own well, its round foot pad
  is the armoured plug that closes it, flush with the sponson's flat floor,
  and there is no door to jam or lose.

Load paths. The keel is the backbone. Each sponson sits on a transverse gear
frame that runs from the keel's side wall out under the main body, so a leg's
load goes up its barrel into the frame and the keel. Forward pair under the
neck/main body join (X 20, |Y| 9), aft pair under the aft body ahead of the
drive section (X -14, |Y| 10), which carries the drives' mass: a 34-unit
(26 m) wheelbase and an 18-20 unit (14-15 m) track.

Size. The game gives the Wolf a mass of 200, which is relative. By volume
(about 17,000 m3 at 0.76 m/u) a hull this size is a couple of thousand tonnes;
four pads 1.8 m in radius (2.4 units) carry that on firm gravel at a few
hundred kPa. Parked, the keel stands GROUND clear of the ground.

Retraction. Each leg is three nested stages: the barrel is fixed in the well;
stage 2 and the oleo (stage 3, carrying the pad) slide straight down. Every
moving part is parented to the empty 'Hull <leg> stage <k> slide', whose
custom property slide_units is its travel at gear_down 1 (along hull -Z).
pose(t) sets them: 0 is stowed (built), 1 deployed. The cinematic export
drives the same translations from its control empty's gear_down knob.
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector

from wf_fittings import SIDES, down, frame, join, pipe, plate, put, side, strip, up

GROUND_Z = -22.0            # the parked ground plane in hull units (keel about 3.4 above)
PAD_R, WELL_R, SPONSON_R = 2.4, 2.56, 3.05
# (leg, x, y)
LEGS = [('port fore gear', 20.0, 9.0), ('starboard fore gear', 20.0, -9.0),
        ('port aft gear', -14.0, 10.0), ('starboard aft gear', -14.0, -10.0)]
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


def gear(kit):
    from wf_kit import boolean
    red, steel, dark = kit.red, kit.steel, kit.dark
    chrome = bpy.data.materials.get('Wolf oleo chrome')
    if chrome is None:
        from wf_kit import material
        chrome = material('Wolf oleo chrome', (.55, .56, .58), 1.0, .18)
    legs = []
    for leg, x, y in LEGS:
        n = f'Hull {leg}'
        lo, hi = belly_range(kit, x, y, SPONSON_R)
        seat = round(lo - .55, 3)               # the sponson's flat floor and the stowed pad's sole
        travel = round(seat - GROUND_Z, 3)
        s = 1 if y > 0 else -1
        # Sponson: a short drum faired into the belly, rounded at its floor, with a
        # gear frame running inboard to the keel's side wall.
        top = hi + 1.2
        spon = lathe_z(kit, f'{n} sponson', [(0, seat), (SPONSON_R-.35, seat), (SPONSON_R, seat+.35),
                                             (SPONSON_R, top), (0, top)], x, y, red)
        well = kit.cylinder(f'Temporary {n} well', (x, y, seat+2.6), WELL_R, 5.4, dark, 'Z', 64, 0)
        boolean(spon, well)
        rounded(spon, .08, 2)
        ceil = lo - .03                         # just under the belly skin: the well reads as a dark recess
        lathe_z(kit, f'{n} well liner', [(0, ceil), (WELL_R-.01, ceil), (WELL_R-.01, seat+.2),
                                         (WELL_R-.08, seat+.2), (WELL_R-.08, ceil-.08), (0, ceil-.08)], x, y, dark)
        ky = 4.6*s
        frame_len = abs(y - ky) - SPONSON_R + .6
        fy = ky + s*frame_len/2
        # The gear frame: a deep beam from the keel's side wall to the sponson, its
        # underside level with the sponson's rim, buried in the belly above.
        z0, z1 = seat + .45, belly_range(kit, x, fy, .1)[1] + 1.0
        web = kit.box(f'{n} frame', (x, fy, (z0+z1)/2), (1.8, frame_len, z1-z0), steel, 0)
        rounded(web, .18, 3)
        # The leg, built stowed: pad sole flush with the sponson floor.
        barrel = [kit.cylinder(f'{n} barrel', (x, y, seat+3.0), 1.1, 4.0, steel, 'Z', 48, .05),
                  kit.cylinder(f'{n} barrel gland', (x, y, seat+1.05), 1.24, .34, dark, 'Z', 48, .04)]
        st2 = [kit.cylinder(f'{n} stage 2', (x, y, seat+3.05), .88, 5.0, steel, 'Z', 48, .04),
               kit.cylinder(f'{n} stage 2 gland', (x, y, seat+.72), 1.0, .3, dark, 'Z', 48, .03)]
        st3 = [kit.cylinder(f'{n} oleo', (x, y, seat+3.15), .66, 5.0, chrome, 'Z', 48, .03),
               kit.cylinder(f'{n} pad', (x, y, seat+.22), PAD_R, .36, red, 'Z', 64, .06),
               kit.cylinder(f'{n} pad sole', (x, y, seat+.02), PAD_R-.12, .04, steel, 'Z', 64, 0),
               kit.cylinder(f'{n} pad boss', (x, y, seat+.54), 1.2, .3, steel, 'Z', 48, .05)]
        bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=.72, location=(x, y, seat+.8))
        st3.append(kit.finish(bpy.context.object, f'{n} pad ball joint', steel))
        # Anti-rotation torque links: two arms from the stage-2 gland to the pad boss,
        # folded flat when stowed, shown as the straight deployed pair.
        for k, a in enumerate((0, math.pi), 1):
            off = Vector((math.cos(a)*1.25, math.sin(a)*1.25, 0))
            st3.append(pipe(kit, f'{n} torque link {k}', Vector((x, y, seat+.7))+off,
                            Vector((x, y, seat+1.3))+off*.8, .11, steel))
        h2 = hinge(f'{n} stage 2 slide', (x, y, seat), slide_axis='-Z', slide_units=round(travel/2, 3),
                   note='built stowed; at gear_down 1 it has slid slide_units along hull -Z')
        h3 = hinge(f'{n} stage 3 slide', (x, y, seat), slide_axis='-Z', slide_units=travel,
                   note='built stowed; carries the oleo and the pad; slide_units along hull -Z at gear_down 1')
        adopt(h2, st2)
        adopt(h3, st3)
        legs.append({'name': n, 'xy': [x, y], 'seat_z': seat, 'travel_units': travel,
                     'slides': [h2.name, h3.name], 'pad': f'{n} pad', 'pad_radius_units': PAD_R,
                     'sole': f'{n} pad sole'})
    RECORD['legs'] = legs


def pose(gear_down):
    """0 = stowed (built), 1 = deployed. Translations only."""
    t = max(0.0, min(1.0, gear_down))
    for leg in RECORD.get('legs', []):
        for nm in leg['slides']:
            h = bpy.data.objects[nm]
            h.location.z = leg['seat_z'] - h['slide_units']*t
    bpy.context.view_layer.update()


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
    """Keel frames, a rubbing strake down the centre, and a heat-shield tile field
    either side of it, clear of the sensor blister and the docking collar."""
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
    pts = [up(kit, x, 0.0) for x in [-18.0+i*.5 for i in range(83)]]
    pts = [(p, n) for p, n in pts if p is not None and not (abs(p.x-2.0) < 2.6 or abs(p.x-22.0) < 2.8)]
    runs, cur = [], []
    for p, n in pts:
        if cur and (p - cur[-1][0]).length > .9:
            runs.append(cur)
            cur = []
        cur.append((p, n))
    runs.append(cur)
    for k, run in enumerate(runs, 1):
        strip(kit, f'Hull keel strake {k}', run, 1.1, .3, steel)
    tiles = []
    for x in [-15.0+i*1.9 for i in range(21)]:
        if abs(x-2.0) < 3.0 or abs(x-22.0) < 3.0:
            continue
        for y in (-2.6, -1.4, 1.4, 2.6):
            p, n = up(kit, x, y)
            if p is None or n.z > -.85:          # only where the keel faces down
                continue
            tiles.append(put(kit.box('tile', (0, 0, .05), (1.75, 1.1, .12), tile, .03), frame(p, n)))
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
    return {'ground_z_units': GROUND_Z, 'legs': RECORD['legs'], 'lift_thrusters': RECORD['lift_thrusters'],
            'note': 'rest is gear stowed (flight); at gear_down 1 every pad sole stands on ground_z_units in hull space'}
