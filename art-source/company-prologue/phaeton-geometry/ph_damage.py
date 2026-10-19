"""Phaeton condition state 'destroyed': the S6 wreck (asharu-battle-shot.md).

Manticore (P) missiles hit her amidships: the mid tank goes up, the hull
breaks in two places between the tanks, and the three sections drift apart
burning and venting. The state follows second-wind-pipeline.md's layers:
the clean build stays the base; damage never renames or deletes a base
object. It cuts base objects (their surface coordinates and materials
survive on the remaining faces), and everything it adds is named
'Scar <id> ...'. New faces a cut makes take scar materials, never a skin.

Scar table (stable ids; the same id is the same damage in every shot):
  S01  fore break, between tanks 2 and 3 (X ~66): the bow section parts
  S02  aft break, between tanks 1 and 2 (X ~22): the stern section parts
  S03  tank 1 (aft) ruptured: a torn hole in its forward-port crown
  S04  tank 2 (mid) shattered: the missile strike; its shell in fragments
  S05  tank 3 (fore) ruptured: a torn hole in its aft crown, facing the blast
  S06  debris cloud: plating, frames and pipe from the mid section
  S07  exposed frames and broken pipe at both break faces

Pieces of a base object that a break cuts away become
'Scar S01 bow piece | <base name>' (or S02 ... mid piece); the base object
keeps its stern-most piece. Every object belongs to one section, and each
section hangs from an empty, 'Scar S0x <section> section', whose pose is the
wreck's drift: the export can drive it from the clean pose to the wreck's,
which is how the explosion is staged (pass 7). Fire and vent anchors are
empties, 'Scar <id> fire anchor <k>' and 'Scar <id> vent anchor <k>' (+X the
vent direction), parented to their sections.
"""
import math
import random
import bmesh
import bpy
from mathutils import Euler, Matrix, Vector
from ph_kit import material

STATES = ('clean', 'destroyed')
BREAKS = {'S02': 22.0, 'S01': 66.0}      # aft, fore: X of each break (before its jag)
JAG = 6.0                                 # jagged break surface amplitude, units
JAG_CELLS = 31                            # jag grid resolution across the 180 x 120 unit cutter
CRATER = ('S04', (44.0, 8.0, 3.0), 21.0)  # strike crater: centre, radius
SECTIONS = ('stern', 'mid', 'bow')
SECTION_SCAR = {'stern': 'S02', 'mid': 'S04', 'bow': 'S01'}
# Wreck pose of each section, about its pivot (ship-local): (pivot, translate, rotation deg XYZ).
# Bow: blown forward and up, nose rising and rolling to port. Stern: pushed
# aft and down, yawing. Mid: what is left of the strike keel drops away.
POSE = {
    'bow':   ((66, 0, -4), (26, 7, 12), (16, 8, 6)),
    'mid':   ((44, 0, -4), (2, -6, -14), (-24, -10, 14)),
    'stern': ((22, 0, -4), (-14, -4, -5), (-5, -4, -7)),
}
TANK_RUPTURES = {  # scar id: (tank, hole centre direction, hole radius)
    'S03': ('Tank 1 aft sphere', (.55, .45, .7), 9.0),
    'S05': ('Tank 3 fore sphere', (-.75, -.2, .63), 10.0),
}
SHATTER = ('S04', 'Tank 2 mid sphere')
SEED = 2026_10_19
REST = {}        # section -> its pose empty name
ANCHORS = []     # [(name, section, kind)]
_M = {}


def _mats():
    if not _M:
        _M['torn'] = material('Scar burnt torn plate', (.035, .03, .028), .55, .7)
        _M['ember'] = material('Scar ember edge', (.08, .03, .01), .3, .6, emission=(1.0, .35, .08), strength=6.0)
        _M['inside'] = material('Scar scorched tank interior', (.05, .045, .04), .4, .8)
        _M['frame'] = material('Scar exposed frame', (.12, .11, .10), .7, .45)
    return _M


def _world_x_range(ob, dg):
    ev = ob.evaluated_get(dg)
    xs = [(ob.matrix_world @ Vector(c)).x for c in ev.bound_box]
    return min(xs), max(xs)


def _section_of(x):
    if x < BREAKS['S02']:
        return 'stern'
    if x < BREAKS['S01']:
        return 'mid'
    return 'bow'


def _jag(rng, n):
    """Torn break surface: a coherent wander plus sharp per-vertex tearing."""
    ph = [rng.uniform(0, 2*math.pi) for _ in range(4)]
    out = []
    for i in range(n):
        row = []
        for j in range(n):
            u, v = i/(n-1), j/(n-1)
            wander = .55*math.sin(7*u+ph[0])*math.cos(5*v+ph[1])+.3*math.sin(13*u+11*v+ph[2])
            row.append(JAG*(wander+rng.uniform(-.45, .45)))
        out.append(row)
    return out


def _half_space(name, x0, jag, ahead):
    """A closed solid filling X > the jagged surface (ahead) or X < it."""
    n = len(jag)
    ys = [-90+180*i/(n-1) for i in range(n)]
    zs = [-60+120*j/(n-1) for j in range(n)]
    far = 400 if ahead else -400
    bm = bmesh.new()
    front = [[bm.verts.new((x0+jag[i][j], ys[i], zs[j])) for j in range(n)] for i in range(n)]
    back = [[bm.verts.new((far, ys[i], zs[j])) for j in range(n)] for i in range(n)]
    for i in range(n-1):
        for j in range(n-1):
            bm.faces.new((front[i][j], front[i+1][j], front[i+1][j+1], front[i][j+1]))
            bm.faces.new((back[i][j+1], back[i+1][j+1], back[i+1][j], back[i][j]))
    ring_f = [front[i][0] for i in range(n)]+[front[n-1][j] for j in range(1, n)] + \
        [front[i][n-1] for i in range(n-2, -1, -1)]+[front[0][j] for j in range(n-2, 0, -1)]
    ring_b = [back[i][0] for i in range(n)]+[back[n-1][j] for j in range(1, n)] + \
        [back[i][n-1] for i in range(n-2, -1, -1)]+[back[0][j] for j in range(n-2, 0, -1)]
    for k in range(len(ring_f)):
        a, b = ring_f[k], ring_f[(k+1) % len(ring_f)]
        bm.faces.new((a, b, ring_b[(k+1) % len(ring_b)], ring_b[k]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    ob.data.materials.append(_mats()['torn'])
    ob.hide_render = ob.hide_viewport = True
    return ob


def _cut(target, cutter, op='DIFFERENCE'):
    bpy.context.view_layer.objects.active = target
    mod = target.modifiers.new('Scar boolean', 'BOOLEAN')
    mod.operation = op
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    try:
        bpy.ops.object.modifier_apply(modifier=mod.name)
    except RuntimeError:
        mod.solver = 'EXACT'
        bpy.ops.object.modifier_apply(modifier=mod.name)
    if target.data.materials.find(cutter.data.materials[0].name) < 0:
        pass  # the solver brings the cutter's material over with its faces


def _empty(name, loc, rot=(0, 0, 0), size=3):
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_type = 'ARROWS'
    ob.empty_display_size = size
    ob.location = loc
    ob.rotation_euler = rot
    bpy.context.collection.objects.link(ob)
    return ob


def _blob(name, centre, radius, rng, mat):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=3, radius=radius, location=centre)
    ob = bpy.context.object
    ob.name = name
    for v in ob.data.vertices:
        v.co *= 1+rng.uniform(-.35, .25)
    ob.data.materials.append(mat)
    ob.hide_render = ob.hide_viewport = True
    return ob


def _hollow(ob, thickness):
    """Make a solid tank a shell whose inner face takes the scorched interior."""
    m = _mats()['inside']
    ob.data.materials.append(m)
    sol = ob.modifiers.new('Scar hollow', 'SOLIDIFY')
    sol.thickness = thickness
    sol.offset = -1
    sol.material_offset = len(ob.data.materials)-1
    sol.material_offset_rim = len(ob.data.materials)-1
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.modifier_move_to_index(modifier=sol.name, index=0)
    bpy.ops.object.modifier_apply(modifier=sol.name)


def _split(obs, rng):
    """Cut every object that spans a break; return {object: section}."""
    dg = bpy.context.evaluated_depsgraph_get()
    jags = {sid: _jag(rng, JAG_CELLS) for sid in BREAKS}
    solids = {(sid, ahead): _half_space(f'Scar {sid} cutter {"ahead" if ahead else "behind"}', x, jags[sid], ahead)
              for sid, x in BREAKS.items() for ahead in (True, False)}
    owner = {}
    for ob in obs:
        lo, hi = _world_x_range(ob, dg)
        spans = [s for s in SECTIONS if _overlaps(s, lo, hi)]
        if len(spans) == 1 or ob.type != 'MESH':
            owner[ob] = _section_of((lo+hi)/2)
            continue
        base_section = spans[0]
        for s in spans[1:]:
            sid = 'S01' if s == 'bow' else 'S02'
            piece = ob.copy()
            piece.data = ob.data.copy()
            piece.name = piece.data.name = f'Scar {sid} {s} piece | {ob.name}'
            bpy.context.collection.objects.link(piece)
            for cut in _keep_cutters(s, solids):
                _cut(piece, cut)
            if len(piece.data.polygons):
                owner[piece] = s
            else:
                bpy.data.objects.remove(piece, do_unlink=True)
        for cut in _keep_cutters(base_section, solids):
            _cut(ob, cut)
        owner[ob] = base_section
    for c in solids.values():
        bpy.data.objects.remove(c, do_unlink=True)
    return owner


def _overlaps(section, lo, hi):
    a = {'stern': -1e9, 'mid': BREAKS['S02']-JAG, 'bow': BREAKS['S01']-JAG}[section]
    b = {'stern': BREAKS['S02']+JAG, 'mid': BREAKS['S01']+JAG, 'bow': 1e9}[section]
    return hi > a and lo < b


def _keep_cutters(section, solids):
    """Half-spaces to subtract so only `section` remains."""
    if section == 'stern':
        return [solids[('S02', True)]]
    if section == 'mid':
        return [solids[('S02', False)], solids[('S01', True)]]
    return [solids[('S01', False)]]


def _rupture(sid, tank, direction, radius, rng):
    ob = bpy.data.objects[tank]
    c = ob.matrix_world.translation
    r = ob.dimensions.x/2
    d = Vector(direction).normalized()
    _hollow(ob, .5)
    blob = _blob(f'Scar {sid} hole cutter', c+d*r, radius, rng, _mats()['ember'])
    _cut(ob, blob)
    bpy.data.objects.remove(blob, do_unlink=True)
    # Petals: torn shell leaves curling out round the hole.
    for k in range(5):
        a = 2*math.pi*k/5+rng.uniform(-.3, .3)
        t = d.orthogonal().normalized()
        u = d.cross(t)
        p = c+d*r*.98+(t*math.cos(a)+u*math.sin(a))*radius*.85
        _petal(f'Scar {sid} torn petal {k+1}', p, d, (t*math.cos(a)+u*math.sin(a)), rng)
    ANCHORS.append((_empty(f'Scar {sid} fire anchor 1', c+d*(r-1)), None, 'fire'))
    vent = _empty(f'Scar {sid} vent anchor 1', c+d*(r+.5), d.to_track_quat('X', 'Z').to_euler())
    ANCHORS.append((vent, None, 'vent'))


def _petal(name, p, out, side, rng, length=5.0, width=3.0):
    """A curled leaf of torn plate: a thin bent quad strip."""
    bm = bmesh.new()
    rows = []
    for i in range(4):
        t = i/3
        curl = out*(t*t*length*.8)+side*(t*length*.6)
        w = width*(1-.6*t)
        n = out.cross(side).normalized()
        rows.append([bm.verts.new(p+curl+n*w/2), bm.verts.new(p+curl-n*w/2)])
    for i in range(3):
        bm.faces.new((rows[i][0], rows[i][1], rows[i+1][1], rows[i+1][0]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    sol = ob.modifiers.new('Plate thickness', 'SOLIDIFY')
    sol.thickness = .35
    ob.data.materials.append(bpy.data.materials.get('Tank dome red') or _mats()['torn'])
    ob.data.materials.append(_mats()['inside'])
    sol.material_offset = 1
    return ob


def _shatter(rng):
    sid, tank = SHATTER
    ob = bpy.data.objects[tank]
    c = ob.matrix_world.translation.copy()
    r = ob.dimensions.x/2
    _hollow(ob, .5)
    blob = _blob(f'Scar {sid} burst cutter', c+Vector((0, .2, .3))*r, r*1.12, rng, _mats()['ember'])
    _cut(ob, blob)
    bpy.data.objects.remove(blob, do_unlink=True)
    # What is left is a ring of shell near the deck: break it into fragments.
    bpy.context.view_layer.objects.active = ob
    for o in bpy.context.selected_objects:
        o.select_set(False)
    ob.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.mesh.separate(type='LOOSE')
    bpy.ops.object.mode_set(mode='OBJECT')
    parts = sorted(bpy.context.selected_objects, key=lambda o: -len(o.data.vertices))
    frags = []
    for k, p in enumerate(parts[1:], 1):
        p.name = p.data.name = f'Scar {sid} shell fragment {k}'
        frags.append(p)
    # Fling: base fragment stays; the loose ones are thrown out radially.
    for p in frags:
        bpy.context.view_layer.update()
        centre = sum((p.matrix_world @ v.co for v in p.data.vertices), Vector())/len(p.data.vertices)
        out = (centre-c).normalized()
        p.matrix_world = Matrix.Translation(out*rng.uniform(8, 20)) @ p.matrix_world
    for k in range(3):
        ANCHORS.append((_empty(f'Scar {sid} fire anchor {k+1}', c+Vector((rng.uniform(-6, 6), rng.uniform(-6, 6), rng.uniform(-3, 2)))), 'mid', 'fire'))
    return frags


def _debris(rng, count=34):
    """S06: plate, frame and pipe thrown out of the mid section."""
    red = bpy.data.materials.get('Hull red plating') or _mats()['torn']
    steel = bpy.data.materials.get('Gunmetal machinery') or _mats()['frame']
    out = []
    for i in range(count):
        p = Vector((rng.uniform(26, 62), rng.uniform(-45, 45), rng.uniform(-25, 25)))
        kind = rng.random()
        if kind < .55:   # torn plate
            dims = (rng.uniform(3, 9), rng.uniform(2, 7), rng.uniform(.3, .6))
            mat = red if rng.random() < .7 else _mats()['torn']
        elif kind < .85:  # frame member
            dims = (rng.uniform(5, 12), rng.uniform(.6, 1.2), rng.uniform(.6, 1.2))
            mat = steel
        else:             # pipe stub
            dims = None
            mat = steel
        if dims:
            bpy.ops.mesh.primitive_cube_add(size=1, location=p)
            ob = bpy.context.object
            ob.dimensions = dims
            bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
            for v in ob.data.vertices:  # bent and torn: jitter the corners
                v.co += Vector((rng.uniform(-.6, .6), rng.uniform(-.6, .6), rng.uniform(-.25, .25)))
        else:
            bpy.ops.mesh.primitive_cylinder_add(vertices=10, radius=rng.uniform(.5, 1.0), depth=rng.uniform(4, 9), location=p)
            ob = bpy.context.object
        ob.name = ob.data.name = f'Scar S06 debris {i+1}'
        ob.rotation_euler = Euler([rng.uniform(0, 2*math.pi) for _ in range(3)])
        ob.data.materials.append(mat)
        ob.data.materials.append(_mats()['torn'])
        out.append(ob)
        if i % 7 == 0:
            ANCHORS.append((_empty(f'Scar S06 fire anchor {i//7+1}', p), 'mid', 'fire'))
    return out


def _break_face_fittings(rng):
    """S07: frames and pipe ends standing out of both break faces, with fire and vents."""
    made = []
    for sid, x in BREAKS.items():
        for side, sgn in (('aft face', -1), ('fore face', 1)):
            section = ('stern' if sid == 'S02' else 'mid') if sgn < 0 else ('mid' if sid == 'S02' else 'bow')
            k = 0
            for y in (-24, -12, 0, 12, 24):
                for z in (-14, -2):
                    if rng.random() < .55:
                        continue
                    k += 1
                    L = rng.uniform(3, 8)
                    bpy.ops.mesh.primitive_cube_add(size=1, location=(x+sgn*L/2, y+rng.uniform(-2, 2), z+rng.uniform(-2, 2)))
                    ob = bpy.context.object
                    ob.dimensions = (L, rng.uniform(.6, 1.0), rng.uniform(1.0, 2.0))
                    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
                    ob.rotation_euler = (rng.uniform(-.4, .4), rng.uniform(-.3, .3), rng.uniform(-.5, .5))
                    ob.name = ob.data.name = f'Scar S07 {sid} {side} frame {k}'
                    ob.data.materials.append(_mats()['frame'])
                    made.append((ob, section))
            for j, (y, z) in enumerate(((0, 2), (rng.uniform(-20, -10), -6), (rng.uniform(10, 20), -6)), 1):
                ANCHORS.append((_empty(f'Scar {sid} fire anchor {side} {j}', (x+sgn*1.5, y, z)), section, 'fire'))
            vent = _empty(f'Scar {sid} vent anchor {side}', (x+sgn*1.0, rng.uniform(-6, 6), 4),
                          Euler((0, 0, 0 if sgn > 0 else math.pi)))
            ANCHORS.append((vent, section, 'vent'))
    return made


def _crater(base, rng):
    """S04: the strike blows out the deck and the port side round the mid tank."""
    sid, centre, radius = CRATER
    blob = _blob(f'Scar {sid} crater cutter', centre, radius, rng, _mats()['torn'])
    c = Vector(centre)
    dg = bpy.context.evaluated_depsgraph_get()
    for ob in base:
        if ob.type != 'MESH' or ob.name.startswith('Tank '):
            continue
        ev = ob.evaluated_get(dg)
        pts = [ob.matrix_world @ Vector(b) for b in ev.bound_box]
        lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
        hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
        if all(lo[k] < c[k]+radius*1.3 and hi[k] > c[k]-radius*1.3 for k in range(3)):
            _cut(ob, blob)
    bpy.data.objects.remove(blob, do_unlink=True)


def _pose(section):
    pivot, move, rot = POSE[section]
    return (Matrix.Translation(Vector(pivot)+Vector(move)) @ Euler([math.radians(a) for a in rot]).to_matrix().to_4x4()
            @ Matrix.Translation(-Vector(pivot)))


def apply(state='destroyed'):
    """Turn the built clean model in the open file into the destroyed wreck."""
    if state == 'clean':
        return {}
    if state not in STATES:
        raise ValueError(state)
    rng = random.Random(SEED)
    ANCHORS.clear()
    base = [o for o in bpy.context.scene.objects if not o.name.startswith(('Camera', 'Key', 'Fill', 'Scar'))
            and o.type in ('MESH', 'EMPTY') and o.parent is None]
    for sid, (tank, d, rad) in TANK_RUPTURES.items():
        _rupture(sid, tank, d, rad, rng)
    frags = _shatter(rng)
    _crater(base, rng)
    owner = _split([o for o in base if o.type == 'MESH'], rng)
    for o in base:
        if o.type == 'EMPTY':
            owner[o] = _section_of(o.matrix_world.translation.x)
    for f in frags:
        owner[f] = 'mid'
    for d in _debris(rng):
        owner[d] = 'mid'
    for ob, s in _break_face_fittings(rng):
        owner[ob] = s
    for ob in bpy.context.scene.objects:  # petals and anything else added here
        if ob.name.startswith('Scar ') and ob.type == 'MESH' and ob not in owner:
            owner[ob] = _section_of(ob.matrix_world.translation.x)
    for anchor, section, kind in ANCHORS:
        owner[anchor] = section or _section_of(anchor.matrix_world.translation.x)
    bpy.context.view_layer.update()
    for s in SECTIONS:
        pivot = POSE[s][0]
        e = _empty(f'Scar {SECTION_SCAR[s]} {s} section', pivot, size=8)
        e['clean_matrix'] = [list(r) for r in Matrix.Identity(4)]
        e['wreck_pose'] = {'pivot': list(POSE[s][0]), 'translate': list(POSE[s][1]), 'rotate_deg': list(POSE[s][2])}
        REST[s] = e.name
        bpy.context.view_layer.update()
        for ob, sec in owner.items():
            if sec == s and ob.parent is None:
                mw = ob.matrix_world.copy()
                ob.parent = e
                ob.matrix_parent_inverse = e.matrix_world.inverted()
                ob.matrix_world = mw
        e.matrix_world = _pose(s) @ e.matrix_world
    bpy.context.view_layer.update()
    return {
        'state': state,
        'scars': {'S01': 'fore break X~66', 'S02': 'aft break X~22', 'S03': 'tank 1 ruptured',
                  'S04': 'tank 2 shattered (strike point)', 'S05': 'tank 3 ruptured', 'S06': 'debris cloud',
                  'S07': 'exposed frames at the break faces'},
        'sections': {s: {'empty': REST[s], 'pivot': list(POSE[s][0]), 'translate': list(POSE[s][1]),
                         'rotate_deg': list(POSE[s][2])} for s in SECTIONS},
        'fire_anchors': sorted(a.name for a, _, k in ANCHORS if k == 'fire'),
        'vent_anchors': sorted(a.name for a, _, k in ANCHORS if k == 'vent'),
    }
