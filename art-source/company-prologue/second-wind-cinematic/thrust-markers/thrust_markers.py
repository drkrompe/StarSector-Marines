"""Thruster markers: one convention for every nozzle of every flying craft in the arrival.

A marker is an empty named `THRUST | <craft> | <kind> <nn>` in the scene's `THRUST MARKERS`
collection, parented to the craft so it rides every move the shot gives it. README.md beside
this file is the convention; in short:

- location: the nozzle exit (the bell mouth plane);
- local +X: down the exhaust, local +Z as near hull-dorsal as that allows. This is the axis
  the geometry anchors (`Engine <i> exhaust anchor`, `RCS ... anchor`) and the VFX kit
  (second-wind-vfx, `SWFX anchor ...`) already use, so a plume built for one fits the other;
- custom properties: `thrust_kind` (main / maneuver / lift), `thrust_craft`, `thrust_index`,
  `radius_m` (nominal mouth radius, metres), `thrust_source` (what the position was measured
  from), `thrust_working` (False for a dead bell) and `thrust_throttle` (0..1, keyed over time:
  the one value plumes, lights and engine sound all follow).

Nothing here is guessed: positions come from the hull's own exhaust anchors, from its bell
meshes where a hull has no anchors, or from the hot bells painted into a card. The kind comes
from the anchor's name and the way it points (a jet blowing hull-down is lift).

    import thrust_markers as tm
    craft = tm.find_craft(scene)                       # every craft the view layer shows
    marks = tm.mark(craft['Flotilla 0 | onslaught'], 'Onslaught')
    tm.key(marks, [(0, 0.3)], fps=24)                  # cruise burn, the whole shot
    tm.report(scene)                                   # per craft: markers by kind

Runs inside Blender only (bpy, mathutils, numpy).
"""
import math
import re

import bpy
import numpy as np
from mathutils import Matrix, Quaternion, Vector

from card_bells import painted_bells

COLLECTION = 'THRUST MARKERS'
PREFIX = 'THRUST | '
KINDS = ('main', 'maneuver', 'lift')
PROP = 'thrust_throttle'

ENGINE_ANCHOR = re.compile(r'^Engine (\d+)(?: (?:port|starboard|centre))? exhaust anchor(?:\.\d+)?$')
RCS_ANCHOR = re.compile(r'^RCS (.+?) anchor(?:\.\d+)?$')
BELL_MESH = re.compile(r'^Engine (\d+) bell(?:\.\d+)?$')
# Meshes whose verts give a mouth radius where an anchor carries none, best first.
MOUTH_PARTS = ('mouth lip', 'aft-facing bell', 'bell', 'dark throat', 'drive can')
LIFT_DOT = 0.7          # an exhaust this close to hull-down is a lift jet


# ---------------------------------------------------------------- geometry

def chain(obj):
    """obj's matrix in the space of its top-most parent's own parent (world for an object in the
    scene, collection space for one inside an instanced collection). Built from the stored
    basis and parent-inverse matrices, because a hull's anchors often sit in a collection the
    view layer never evaluates, so their matrix_world is stale."""
    m = obj.matrix_parent_inverse @ obj.matrix_basis
    p = obj.parent
    while p is not None:
        m = p.matrix_parent_inverse @ p.matrix_basis @ m
        p = p.parent
    return m


def top(obj):
    while obj.parent is not None:
        obj = obj.parent
    return obj


def ancestors(obj):
    out = []
    while obj.parent is not None:
        obj = obj.parent
        out.append(obj)
    return out


def _unit_frame(m):
    loc, rot, _ = m.decompose()
    return Matrix.LocRotScale(loc, rot, None)


def _mouth_radius(anchor_m, meshes):
    """Mouth radius in the anchor frame's units: the best mouth mesh's verts, measured off the
    exhaust axis (for a bell or lip, only the verts near the mouth plane)."""
    inv = anchor_m.inverted()
    for part in MOUTH_PARTS:
        for ob, m in meshes.get(part, ()):
            co = np.empty(len(ob.data.vertices) * 3)
            ob.data.vertices.foreach_get('co', co)
            co = co.reshape(-1, 3)
            if not len(co):
                continue
            mm = np.array(inv @ m)
            p = co @ mm[:3, :3].T + mm[:3, 3]
            radial = np.hypot(p[:, 1], p[:, 2])
            if part not in ('drive can', 'dark throat'):
                near = np.abs(p[:, 0]) <= 0.25 * radial.max()
                if near.any():
                    radial = radial[near]
            r = float(np.percentile(radial, 98))
            if r > 0:
                return r, '%s mesh' % part
    return None, None


def _kind(name, axis_in_hull):
    """Kind from the anchor name, and from where it points: a jet blowing hull-down is lift."""
    if axis_in_hull.dot(Vector((0, 0, -1))) > LIFT_DOT:
        return 'lift'
    return 'maneuver' if RCS_ANCHOR.match(name) else 'main'


def hull_nozzles(objects, engines=None, base=None):
    """Every nozzle in a hull's objects, as dicts in the space `base` maps the objects' common
    space (see chain) into: matrix (unit scale), radius (in that space's units, or metres when
    radius_is_m), kind. Everything is measured in that space, so a bell's direction from the
    engine table and its vertices agree however the craft is turned.

    objects: the hull's objects (an instanced collection's, or a root's hierarchy).
    base:    the craft's local space from the common space (default: the common space).
    engines: optional {engine index: {'role': ..., 'dir': (x, y, z)}} from a hull's
             geometry.json, for a hull whose bells must be measured (the skinned Mudskipper)
             or whose engine table names a maneuvering jet."""
    engines = engines or {}
    base = base if base is not None else Matrix.Identity(4)

    def at(o):
        return base @ chain(o)
    objects = [o for o in objects if o.type in ('EMPTY', 'MESH')]
    mesh_parts = {}
    for o in objects:
        if o.type != 'MESH':
            continue
        m = re.match(r'^(Engine \d+) (.+?)(?:\.\d+)?$', o.name)
        if m and m.group(2) in MOUTH_PARTS:
            mesh_parts.setdefault(m.group(1), {}).setdefault(m.group(2), []).append((o, at(o)))
    # Hull frame: the root the anchors hang from, so 'down' is the hull's own down.
    out = []
    anchors = [o for o in objects if o.type == 'EMPTY' and (ENGINE_ANCHOR.match(o.name) or RCS_ANCHOR.match(o.name))]
    for a in sorted(anchors, key=lambda o: o.name):
        m = _unit_frame(at(a))
        hull = _unit_frame(at(a.parent)) if a.parent is not None else _unit_frame(base)
        axis = (hull.inverted().to_3x3() @ m.to_3x3() @ Vector((1, 0, 0))).normalized()
        em = ENGINE_ANCHOR.match(a.name)
        index = int(em.group(1)) if em else None
        kind = _kind(a.name, axis)
        if em and 'maneuver' in engines.get(index, {}).get('role', ''):
            kind = 'maneuver'
        if 'radius_m' in a:
            radius, rsrc, metres = float(a['radius_m']), 'anchor radius_m', True
        elif 'radius' in a or 'mouth_radius' in a:
            parent_scale = (at(a.parent) if a.parent is not None else base).to_scale()[0]
            radius = float(a['radius'] if 'radius' in a else a['mouth_radius']) * parent_scale
            rsrc, metres = 'anchor radius', False
        else:
            radius, rsrc = _mouth_radius(m, mesh_parts.get('Engine %d' % index, {})) if em else (None, None)
            metres = False
            if radius is None:
                radius, rsrc = 0.3 if not em else 1.0, 'default'
        out.append(dict(name=a.name, kind=kind, index=index, matrix=m, radius=radius, radius_is_m=metres,
                        source='%s (%s)' % (a.name, rsrc)))
    if out:
        return out
    # No anchors: measure each bell mesh. The mouth is its vertex extreme along the exhaust,
    # which points hull-aft (-X) unless the hull's geometry.json says otherwise.
    for o in objects:
        bm = BELL_MESH.match(o.name) if o.type == 'MESH' else None
        if not bm:
            continue
        index = int(bm.group(1))
        m = at(o)
        co = np.empty(len(o.data.vertices) * 3)
        o.data.vertices.foreach_get('co', co)
        co = co.reshape(-1, 3)
        mm = np.array(m)
        p = co @ mm[:3, :3].T + mm[:3, 3]
        role = engines.get(index, {}).get('role', '')
        d = np.array(engines.get(index, {}).get('dir', (-1.0, 0.0, 0.0)), float)
        d /= np.linalg.norm(d)
        s = p @ d
        lip = p[s >= s.max() - 0.04 * np.ptp(s)]
        centre = lip.mean(axis=0)
        r = lip - centre
        radius = float(np.percentile(np.linalg.norm(r - np.outer(r @ d, d), axis=1), 98))
        x = Vector(d)
        z = Vector((0, 0, 1)) - x * x.z
        z = z.normalized() if z.length > 1e-6 else Vector((0, 1, 0))
        y = z.cross(x)
        frame = Matrix((x, y, z)).transposed().to_4x4()
        frame.translation = Vector(centre)
        kind = 'maneuver' if 'maneuver' in role else _kind(o.name, x)
        out.append(dict(name=o.name, kind=kind, index=index, matrix=frame, radius=radius, radius_is_m=False,
                        source='%s mesh mouth (measured)' % re.sub(r'\.\d+$', '', o.name)))
    return sorted(out, key=lambda n: n['index'])


# ---------------------------------------------------------------- finding craft

def _instance_objects(coll):
    objs = set(coll.all_objects)
    for o in list(objs):
        objs.update(o.children_recursive)
    return objs


def find_craft(scene=None, view_layer=None):
    """{object name: object} for every craft the view layer shows: an instance of a hull
    collection that holds nozzles, or the top-most shown ancestor of a hull's anchors/bells.
    Markers already placed are ignored."""
    view_layer = view_layer or bpy.context.view_layer
    view_layer.update()            # objects of a newly linked collection appear only after this
    shown = {o.name: o for o in view_layer.objects}
    craft = {}
    cache = {}
    for o in shown.values():
        if o.name.startswith(PREFIX):
            continue
        if o.instance_type == 'COLLECTION' and o.instance_collection is not None:
            c = o.instance_collection
            if c.name not in cache:
                cache[c.name] = bool(hull_nozzles(_instance_objects(c)))
            if cache[c.name]:
                craft[o.name] = o
    for o in bpy.data.objects:
        if o.type not in ('EMPTY', 'MESH') or not (ENGINE_ANCHOR.match(o.name) or RCS_ANCHOR.match(o.name)
                                                   or (o.type == 'MESH' and BELL_MESH.match(o.name))):
            continue
        up = [a for a in ancestors(o) if a.name in shown]
        if up:
            craft.setdefault(up[-1].name, up[-1])
    # A craft inside another craft's hierarchy is the same craft.
    for name in list(craft):
        if any(a.name in craft for a in ancestors(craft[name])):
            del craft[name]
    return craft


def craft_nozzles(craft_obj, engines=None):
    """Nozzles of one craft in the craft object's local space (what a child marker uses)."""
    if craft_obj.instance_type == 'COLLECTION' and craft_obj.instance_collection is not None:
        c = craft_obj.instance_collection
        return hull_nozzles(_instance_objects(c), engines, Matrix.Translation(-Vector(c.instance_offset)))
    # A shown root: its descendants carry the nozzles; express them below the root.
    objs = list(craft_obj.children_recursive)
    return hull_nozzles(objs, engines, chain(craft_obj).inverted())


# ---------------------------------------------------------------- markers

def collection(scene=None):
    scene = scene or bpy.context.scene
    coll = bpy.data.collections.get(COLLECTION)
    if coll is None:
        coll = bpy.data.collections.new(COLLECTION)
    if coll.name not in scene.collection.children:
        scene.collection.children.link(coll)
    return coll


def markers(craft_obj=None, scene=None):
    """The markers in the scene (or on one craft)."""
    coll = bpy.data.collections.get(COLLECTION)
    if coll is None:
        return []
    out = [o for o in coll.objects if o.name.startswith(PREFIX)]
    if craft_obj is not None:
        out = [o for o in out if o.parent == craft_obj]
    return sorted(out, key=lambda o: o.name)


def clear(craft_obj):
    for o in markers(craft_obj):
        bpy.data.objects.remove(o, do_unlink=True)


def _world_scale(obj):
    return (obj.matrix_parent_inverse @ obj.matrix_basis).to_scale()[0] * \
        (_world_scale(obj.parent) if obj.parent is not None else 1.0)


def place(craft_obj, label, nozzles, working=None, activity=''):
    """Markers for these nozzles on craft_obj (replacing any it had). Returns them.
    working: optional {engine index: bool}; a dead bell keeps its marker, switched off."""
    clear(craft_obj)
    coll = collection()
    s = abs(_world_scale(craft_obj)) or 1.0
    count = {}
    out = []
    for n in nozzles:
        count[n['kind']] = count.get(n['kind'], 0) + 1
        k = count[n['kind']] if n['index'] is None or n['kind'] != 'main' else n['index']
        e = bpy.data.objects.new('%s%s | %s %02d' % (PREFIX, label, n['kind'], k), None)
        coll.objects.link(e)
        e.parent = craft_obj
        e.matrix_parent_inverse = Matrix.Identity(4)
        e.matrix_basis = n['matrix']
        e.empty_display_type = 'ARROWS'
        radius_m = n['radius'] if n['radius_is_m'] else n['radius'] * s
        e.empty_display_size = max(radius_m, 0.05) / s
        e.hide_render = True
        e['thrust_craft'] = label
        e['thrust_kind'] = n['kind']
        e['thrust_index'] = k
        e['radius_m'] = round(radius_m, 4)
        e['thrust_source'] = n['source']
        ok = True if working is None or n['index'] is None or n['kind'] != 'main' else working.get(n['index'], True)
        e['thrust_working'] = bool(ok)
        e[PROP] = 0.0
        out.append(e)
    craft_obj['thrust_craft'] = label
    craft_obj['thrust_markers'] = len(out)
    if activity:
        craft_obj['thrust_activity'] = activity
    return out


def mark(craft_obj, label=None, engines=None, working=None, activity=''):
    """Measure craft_obj's nozzles and place its markers."""
    return place(craft_obj, label or craft_obj.name, craft_nozzles(craft_obj, engines), working, activity)


def card(card_obj, label, image, activity=''):
    """Markers on a painted card (a 1x1 plane scaled to the image): at each hot bell painted in
    the image, +X in the card plane from the hull's centre of mass out through the bell."""
    w, h = image.size
    px = np.empty(w * h * 4, np.float32)
    image.pixels.foreach_get(px)
    px = px.reshape(h, w, 4)[::-1]
    bells, (cx, cy) = painted_bells(px)
    nozzles = []
    for bx, by, r in bells:
        d = Vector(((bx - cx) / w, -(by - cy) / h, 0))
        if d.length < 1e-6:
            continue
        x = d.normalized()
        z = Vector((0, 0, 1))
        y = z.cross(x)
        m = Matrix((x, y, z)).transposed().to_4x4()
        m.translation = Vector((bx / w - 0.5, 0.5 - by / h, 0.0))
        nozzles.append(dict(name=image.name, kind='main', index=len(nozzles) + 1, matrix=m,
                            radius=r / w * card_obj.scale.x, radius_is_m=True,
                            source='%s painted bell at px (%d, %d)' % (image.name, bx, by)))
    out = place(card_obj, label, nozzles, activity=activity)
    for e in out:            # the card's own non-uniform scale is not the nozzle's
        e.empty_display_size = 0.02
    return out


def lift_from_mains(craft_obj, label=None):
    """Lift markers for a hull with no belly jets that the shot still lifts and lands (the port's
    Hermes lighters and Hounds): each main bell vectored down. One lift marker per main, at the
    same mouth, +X hull-down and +Z toward the bow. Nothing is added to a hull that already has
    lift jets. Returns the new markers."""
    have = markers(craft_obj)
    if any(m['thrust_kind'] == 'lift' for m in have):
        return []
    label = label or craft_obj.get('thrust_craft', craft_obj.name)
    coll = collection()
    down = Matrix(((0, 0, 1), (0, 1, 0), (-1, 0, 0))).to_4x4()   # columns: +X down, +Y, +Z bow
    out = []
    for m in [m for m in have if m['thrust_kind'] == 'main']:
        e = bpy.data.objects.new('%s%s | lift %02d' % (PREFIX, label, m['thrust_index']), None)
        coll.objects.link(e)
        e.parent = craft_obj
        e.matrix_parent_inverse = Matrix.Identity(4)
        e.matrix_basis = Matrix.Translation(m.matrix_basis.translation) @ down
        e.empty_display_type = 'ARROWS'
        e.empty_display_size = m.empty_display_size
        e.hide_render = True
        for k in ('thrust_craft', 'thrust_index', 'radius_m', 'thrust_working'):
            e[k] = m[k]
        e['thrust_kind'] = 'lift'
        e['thrust_vectored'] = m.name        # the main this is the same drive as, turned down
        e['thrust_source'] = 'main %02d vectored down (the hull has no belly jets): %s' % (
            m['thrust_index'], m['thrust_source'])
        e[PROP] = 0.0
        out.append(e)
    craft_obj['thrust_markers'] = len(have) + len(out)
    return out


# ---------------------------------------------------------------- throttle

def key(marks, keys, fps, kinds=None):
    """Key thrust_throttle on these markers: keys are (seconds, 0..1), held linear between.
    kinds limits it to those kinds; a marker whose bell is dead stays at 0."""
    for e in marks:
        if kinds and e['thrust_kind'] not in kinds:
            continue
        _drop_throttle(e)            # only the throttle curve: a vectored marker's swing stays
        for t, v in keys:
            e[PROP] = float(v) if e['thrust_working'] else 0.0
            e.keyframe_insert('["%s"]' % PROP, frame=round(t * fps))
        for fc in fcurves(e):
            if fc.data_path == '["%s"]' % PROP:
                for k in fc.keyframe_points:
                    k.interpolation = 'LINEAR'


def _drop_throttle(ob):
    ad = ob.animation_data
    if ad is None or ad.action is None:
        return
    act, path_ = ad.action, '["%s"]' % PROP
    for fc in list(getattr(act, 'fcurves', []) or []):
        if fc.data_path == path_:
            act.fcurves.remove(fc)
    for layer in getattr(act, 'layers', []):
        for strip in layer.strips:
            for bag in strip.channelbags:
                for fc in list(bag.fcurves):
                    if fc.data_path == path_:
                        bag.fcurves.remove(fc)


def fcurves(ob):
    ad = ob.animation_data
    if ad is None or ad.action is None:
        return []
    act = ad.action
    out = list(getattr(act, 'fcurves', []) or [])
    for layer in getattr(act, 'layers', []):
        for strip in layer.strips:
            for bag in strip.channelbags:
                out += list(bag.fcurves)
    return out


def throttle_at(marker, frame):
    for fc in fcurves(marker):
        if fc.data_path == '["%s"]' % PROP:
            return fc.evaluate(frame)
    return float(marker.get(PROP, 0.0))


def throttle_keys(marker, fps):
    for fc in fcurves(marker):
        if fc.data_path == '["%s"]' % PROP:
            return [(round(k.co[0] / fps, 3), round(k.co[1], 3)) for k in fc.keyframe_points]
    return [(0.0, float(marker.get(PROP, 0.0)))]


def path(obj, frames):
    """obj's own keyed location at these frames (no parent motion), from its fcurves."""
    fc = {(f.data_path, f.array_index): f for f in fcurves(obj)}
    out = []
    for fr in frames:
        out.append(Vector([fc[('location', i)].evaluate(fr) if ('location', i) in fc else obj.location[i]
                           for i in range(3)]))
    return out


VECTOR_FULL = 6.0   # m/s of way on at which a vectored drive has swung fully back to aft


def _smooth(lo, hi, x):
    t = min(1.0, max(0.0, (x - lo) / (hi - lo)))
    return t * t * (3.0 - 2.0 * t)


def auto(craft_obj, marks, fps, frames, step=6, still=0.3, hover=False):
    """Throttle from the craft's own keyed motion, for traffic a shot moves without saying why:
    parked (no motion) is off; climbing, sinking or (with hover, for a shot in air) hovering
    still more than a metre above its lowest point is lift at full and mains off;
    travelling is mains at cruise (0.6) with lift off.

    A hull whose lift markers are its mains turned down (`lift_from_mains`, `thrust_vectored`)
    instead swings them: while it has no way on they point hull-down and carry the thrust; as
    horizontal speed rises to VECTOR_FULL they turn back toward the main's own aft axis (keyed
    rotation) and hand their throttle over to the main marker at the same mouth, so the two
    always add up to the drive's one throttle (1.0 hovering, 0.6 cruising). Returns the activity."""
    fr = list(range(frames[0], frames[1] + 1, step))
    if fr[-1] != frames[1]:
        fr.append(frames[1])
    p = path(craft_obj, fr)
    # In air (`hover=True`), standing still above its lowest point is a hover, not parked.
    floor = min(q.z for q in p) + 1.0 if hover else float('inf')
    vectored = [e for e in marks if e['thrust_kind'] == 'lift' and e.get('thrust_vectored')]
    keys_main, keys_lift, blend, moved = [], [], [], False
    for i, f in enumerate(fr):
        a, b = p[max(0, i - 1)], p[min(len(p) - 1, i + 1)]
        dt = max(1, fr[min(len(p) - 1, i + 1)] - fr[max(0, i - 1)]) / fps
        v = (b - a) / dt
        vz, vh = abs(v.z), v.xy.length
        if vectored:
            s = round(_smooth(still, VECTOR_FULL, vh), 3)
            hover = p[i].z > floor
            thr = 0.0 if vz <= still and vh <= still and not hover else round(1.0 - 0.4 * s, 3)
            lm, ll = round(thr * s, 3), round(thr * (1.0 - s), 3)
            blend.append((f, s))
        elif (vz > still and vz >= 0.5 * vh) or (vh <= still and p[i].z > floor):
            lm, ll = 0.0, 1.0
        elif vh > still:
            lm, ll = 0.6, 0.0
        else:
            lm, ll = 0.0, 0.0
        moved |= lm > 0 or ll > 0
        keys_main.append((f / fps, lm))
        keys_lift.append((f / fps, ll))
    key(marks, _simplify(keys_main), fps, kinds=('main',))
    key(marks, _simplify(keys_lift), fps, kinds=('lift',))
    key(marks, [(frames[0] / fps, 0.0)], fps, kinds=('maneuver',))
    if vectored:
        swing(vectored, blend)
    if not moved:
        return 'parked'
    if vectored:
        return 'vectored lift/travel'
    return 'lifting/landing' if any(v for _, v in keys_lift) else 'travelling'


def swing(vectored, blend):
    """Key each vectored lift marker's rotation: hull-down at blend 0, its main's aft axis at 1.
    blend: [(frame, 0..1)]."""
    down = Matrix(((0, 0, 1), (0, 1, 0), (-1, 0, 0))).to_quaternion()
    for e in vectored:
        main = bpy.data.objects.get(e['thrust_vectored'])
        if main is None:
            continue
        # A pitch about the hull's side axis, from hull-down onto the main's own exhaust axis.
        aft = (main.matrix_basis.to_3x3() @ Vector((1, 0, 0))).normalized()
        turn = Vector((0, 0, -1)).rotation_difference(aft)
        e.rotation_mode = 'QUATERNION'
        for f, s in _simplify(blend):
            e.rotation_quaternion = Quaternion().slerp(turn, s) @ down
            e.keyframe_insert('rotation_quaternion', frame=f)
        for fc in fcurves(e):
            if fc.data_path == 'rotation_quaternion':
                for k in fc.keyframe_points:
                    k.interpolation = 'LINEAR'


def _simplify(keys):
    out = []
    for i, (t, v) in enumerate(keys):
        if 0 < i < len(keys) - 1 and keys[i - 1][1] == v == keys[i + 1][1]:
            continue
        out.append((t, v))
    return out


# ---------------------------------------------------------------- report

def report(scene=None, fps=None, extra_suspects=()):
    """[(craft name, activity, {kind: count}, flying)] for every craft in the view layer, plus
    any suspect object (a likely ship the finder did not recognise) with its count."""
    scene = scene or bpy.context.scene
    fps = fps or scene.render.fps
    craft = find_craft(scene)
    for name in extra_suspects:
        ob = bpy.data.objects.get(name)
        if ob is not None:
            craft.setdefault(name, ob)
    for ob in bpy.context.view_layer.objects:
        if 'thrust_craft' in ob and not ob.name.startswith(PREFIX):
            craft.setdefault(ob.name, ob)
    rows = []
    for name in sorted(craft):
        ob = craft[name]
        ms = markers(ob)
        kinds = {}
        for m in ms:
            kinds[m['thrust_kind']] = kinds.get(m['thrust_kind'], 0) + 1
        flying = any(max(v for _, v in throttle_keys(m, fps)) > 0 for m in ms)
        rows.append((name, ob.get('thrust_activity', ''), kinds, flying))
    return rows
