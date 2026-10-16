"""Shared core for the Second Wind VFX and lighting kit.

Every effect module receives one Context: the condition state and what it
allows, the effect anchors, a controls empty carrying the animatable knobs,
per-group collections, and small builders shared by all groups. Nothing in
this package edits the geometry build; it appends onto whatever ship is in
the scene, found by the stable object names the pipeline guarantees
(`second-wind-pipeline.md`).

Coordinates are hull-local: +X bow, +Y port, +Z dorsal, in vanilla hull
units. The real-world scale is metres_per_unit from geometry.json (0.65 m at
writing: the ship ~100 units / ~66 m, a bell mouth 2.6-3.9 units); physical
sizes, speeds and falloffs are derived through units(), never hard-coded.
"""
import bpy
from mathutils import Vector

import json
from pathlib import Path

GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'second-wind-geometry' / 'geometry.json'


def metres_per_unit():
    """The ship's one real-world scale, read from the geometry build's
    manifest (second-wind-pipeline.md, Scale). Never hard-code metres: author
    in hull units, or convert with units(m) so a rescale is one edit there."""
    try:
        return float(json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))['metres_per_unit'])
    except Exception:
        return 0.65  # the published figure, if the manifest is unreadable


METRES_PER_UNIT = metres_per_unit()


def units(metres):
    """Metres -> hull units (e.g. a 1.8 m person is units(1.8) ~ 2.8 units)."""
    return metres / METRES_PER_UNIT


ROOT = 'SWFX'
CONTROLS = 'SWFX controls'
FPS = 24

# Engines: 1 port outer, 2 port inner, 3 starboard inner, 4 starboard outer
# (sw_engines.ENGINES order; +Y is port).
PORT_ENGINES = (1, 2)
STARBOARD_ENGINES = (3, 4)

# What each condition state allows. Engine modes:
#   'dead'     cold, unpowered (derelict hulk; nothing burns)
#   'capped'   port pod dead and capped (patched / refit); never a plume
#   'salvaged' running on the bolted-on salvaged drive coil: a slightly
#              unstable, flickering, dirtier plume
#   'clean'    a healthy drive
# Canon (second-wind-skins/README.txt): at arrival no active fire and no
# catastrophic venting; seal leaks stay a minor wisp. Leaks come from the
# borrowed seal gaskets, which only the patched build carries (the refit
# replaced them), so refit has none.
STATES = {
    'derelict': dict(engines={1: 'dead', 2: 'dead', 3: 'dead', 4: 'dead'}, powered=False,
                     guns=False, seal_wisps=False, geometry='derelict'),
    'patched':  dict(engines={1: 'capped', 2: 'capped', 3: 'salvaged', 4: 'salvaged'}, powered=True,
                     guns=True, seal_wisps=True, geometry='patched'),
    'refit':    dict(engines={1: 'capped', 2: 'capped', 3: 'salvaged', 4: 'salvaged'}, powered=True,
                     guns=True, seal_wisps=False, geometry='refit'),
    'restored': dict(engines={1: 'clean', 2: 'clean', 3: 'clean', 4: 'clean'}, powered=True,
                     guns=True, seal_wisps=False, geometry='restored'),
    'clean':    dict(engines={1: 'clean', 2: 'clean', 3: 'clean', 4: 'clean'}, powered=True,
                     guns=True, seal_wisps=False, geometry='clean'),
}
# 'geometry' is the build_reference.py --state that builds that condition.


class Context:
    def __init__(self, scene, state, anchors):
        if state not in STATES:
            raise ValueError(f'unknown state {state!r}; expected one of {sorted(STATES)}')
        self.scene = scene
        self.state = state
        self.spec = STATES[state]
        self.anchors = anchors
        self.records = {}
        self.root = collection(ROOT, scene.collection)
        self.controls = bpy.data.objects.get(CONTROLS)
        if self.controls is None:
            self.controls = bpy.data.objects.new(CONTROLS, None)
            self.root.objects.link(self.controls)
            self.controls.empty_display_type = 'CUBE'
            self.controls.hide_render = True
        self.controls['swfx_state'] = state

    # --- organisation ---------------------------------------------------
    def collection(self, group):
        """'SWFX | <group>' under the SWFX root collection."""
        return collection(f'{ROOT} | {group}', self.root)

    def engine_mode(self, i):
        return self.spec['engines'][i]

    # --- controls ---------------------------------------------------------
    def prop(self, name, default, lo=0.0, hi=1.0, description=''):
        """An animatable float knob on the controls empty (idempotent).
        Keyframe it with ctx.key(name, frame, value)."""
        c = self.controls
        if name not in c.keys():
            c[name] = float(default)
            ui = c.id_properties_ui(name)
            ui.update(min=lo, max=hi, soft_min=lo, soft_max=hi, description=description)
            # A prop set from Python does not re-tag the evaluated copy; without
            # this a driver built on it can read nothing and stay invalid.
            c.update_tag()
        return f'["{name}"]'

    def set(self, name, value):
        """Set a knob from Python and re-tag the controls so drivers see it."""
        self.controls[name] = float(value)
        self.controls.update_tag()

    def key(self, name, frame, value, interpolation=None):
        c = self.controls
        c[name] = float(value)
        c.keyframe_insert(f'["{name}"]', frame=frame)
        if interpolation:
            fc = find_fcurve(c, f'["{name}"]')
            if fc:
                for kp in fc.keyframe_points:
                    if abs(kp.co.x - frame) < 1e-4:
                        kp.interpolation = interpolation
        return c

    def drive(self, owner, path, expression, props=(), index=-1, extra=None):
        """Drive owner.path with an expression of control props.

        props: control prop names, each available in the expression under its
        own name. extra: {var: (id, data_path)} for other single-prop inputs.
        'frame' is always available in Blender driver expressions."""
        fc = owner.driver_add(path, index) if index >= 0 else owner.driver_add(path)
        d = fc.driver
        d.type = 'SCRIPTED'
        for name in props:
            v = d.variables.new()
            v.name = name
            v.type = 'SINGLE_PROP'
            v.targets[0].id = self.controls
            v.targets[0].data_path = f'["{name}"]'
        for name, (ident, data_path) in (extra or {}).items():
            v = d.variables.new()
            v.name = name
            v.type = 'SINGLE_PROP'
            v.targets[0].id = ident
            v.targets[0].data_path = data_path
        d.expression = expression
        return fc


def refresh_drivers(scene):
    """Re-evaluate the controls and clear every driver's sticky invalid flag,
    so drivers built on knobs created this session read them (found by the
    lighting pass on cached ship blends). swfx.apply calls this last."""
    c = bpy.data.objects.get(CONTROLS)
    if c is not None:
        c.update_tag()
    bpy.context.view_layer.update()
    stores = (bpy.data.objects, bpy.data.lights, bpy.data.materials, bpy.data.worlds,
              bpy.data.scenes, bpy.data.node_groups, bpy.data.meshes, bpy.data.cameras)
    for store in stores:
        for idb in store:
            for owner in (idb, getattr(idb, 'node_tree', None)):
                ad = getattr(owner, 'animation_data', None) if owner is not None else None
                if ad:
                    for fc in ad.drivers:
                        fc.driver.is_valid = True
    scene.frame_set(scene.frame_current)


def collection(name, parent):
    coll = bpy.data.collections.get(name)
    if coll is None:
        coll = bpy.data.collections.new(name)
    if coll.name not in [c.name for c in parent.children]:
        parent.children.link(coll)
    return coll


def find_fcurve(idblock, data_path, index=0):
    """F-curve lookup that works with both legacy and layered (5.x) actions."""
    ad = idblock.animation_data
    if not ad or not ad.action:
        return None
    act = ad.action
    curves = getattr(act, 'fcurves', None)
    if curves is not None:
        try:
            return curves.find(data_path, index=index)
        except Exception:
            pass
    for layer in getattr(act, 'layers', []):
        for strip in layer.strips:
            for bag in strip.channelbags:
                fc = bag.fcurves.find(data_path, index=index)
                if fc:
                    return fc
    return None


def empty(name, coll, size=1.0, display='SINGLE_ARROW'):
    ob = bpy.data.objects.new(name, None)
    coll.objects.link(ob)
    ob.empty_display_type = display
    ob.empty_display_size = size
    return ob


def attach(child, anchor, local=(0, 0, 0), rotation=(0, 0, 0)):
    """Parent child to an anchor so it rides with it: local coordinates are
    the anchor's frame (+X along the effect axis)."""
    child.parent = anchor
    child.matrix_parent_inverse.identity()
    child.location = local
    child.rotation_euler = rotation
    return child


def box_volume(name, coll, x0, x1, half_y, half_z, material):
    """A closed box spanning x0..x1 along local X and +/-half in Y, Z: only a
    volume container (the material should have no surface output). Origin at
    local x=0, so scaling X grows a plume away from an attached nozzle."""
    verts = [(x, y, z) for x in (x0, x1) for y in (-half_y, half_y) for z in (-half_z, half_z)]
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    me.materials.append(material)
    return ob


def node_material(name):
    """A fresh node material with an emptied tree; returns (mat, nodes, links)."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.node_tree.nodes.clear()
    return m, m.node_tree.nodes, m.node_tree.links


def math_node(nodes, links, op, a, b=None, clamp=False):
    n = nodes.new('ShaderNodeMath')
    n.operation = op
    n.use_clamp = clamp
    for idx, value in enumerate((a, b)):
        if value is None:
            continue
        if hasattr(value, 'node'):
            links.new(value, n.inputs[idx])
        else:
            n.inputs[idx].default_value = value
    return n.outputs[0]


def value_node(ctx, nodes, name, expression, props=(), extra=None):
    """A Value node whose output is driven by control props (by expression)."""
    n = nodes.new('ShaderNodeValue')
    n.name = n.label = name
    ctx.drive(n.outputs[0], 'default_value', expression, props, extra=extra)
    return n.outputs[0]


def point_light(name, coll, color, energy, radius=.5):
    data = bpy.data.lights.new(name, 'POINT')
    data.color = color
    data.energy = energy
    data.shadow_soft_size = radius
    ob = bpy.data.objects.new(name, data)
    coll.objects.link(ob)
    return ob


# --- review rendering ----------------------------------------------------
def setup_render(scene, engine='CYCLES', samples=64, res=(1920, 1080), fps=FPS):
    """1920x1080 at 100% for anything shown to the owner (CLAUDE.md)."""
    r = scene.render
    r.resolution_x, r.resolution_y = res
    r.resolution_percentage = 100
    r.fps = fps
    r.image_settings.file_format = 'PNG'
    scene.view_settings.view_transform = 'AgX'
    r.engine = engine
    if engine != 'CYCLES':
        return
    c = scene.cycles
    c.samples = samples
    c.use_denoising = True
    c.volume_bounces = 1
    # Keeps BVH and shader compilation between animation frames (measured by
    # the guns pass: ~25 s -> ~6 s a frame uncontended).
    r.use_persistent_data = True
    c.device = 'CPU'
    try:
        prefs = bpy.context.preferences.addons['cycles'].preferences
    except Exception:
        return
    for kind in ('OPTIX', 'CUDA'):
        try:
            prefs.compute_device_type = kind
            prefs.get_devices()
        except TypeError:
            continue
        if any(d.type == kind for d in prefs.devices):
            for d in prefs.devices:
                d.use = d.type == kind
            c.device = 'GPU'
            return


def look_camera(name, coll, location, target, lens=50.0, ortho_scale=None):
    data = bpy.data.cameras.new(name)
    if ortho_scale:
        data.type = 'ORTHO'
        data.ortho_scale = ortho_scale
    else:
        data.lens = lens
    data.clip_start, data.clip_end = .1, 5000
    ob = bpy.data.objects.new(name, data)
    coll.objects.link(ob)
    ob.location = location
    ob.rotation_euler = (Vector(target) - Vector(location)).to_track_quat('-Z', 'Y').to_euler()
    return ob


def hide_reference_lights(scene):
    """The geometry build adds its own 'Key'/'Fill' review lights and a grey
    world; effect reviews and lighting rigs replace them."""
    for name in ('Key', 'Fill'):
        ob = bpy.data.objects.get(name)
        if ob:
            ob.hide_render = True
            ob.hide_viewport = True


def dark_world(scene, color=(0.0, 0.0, 0.0), strength=1.0):
    """A plain world, replacing the geometry build's grey review backdrop."""
    world = scene.world or bpy.data.worlds.new('SWFX world')
    scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get('Background')
    bg.inputs[0].default_value = (*color, 1)
    bg.inputs[1].default_value = strength
    return world


def encode_movie(frame_pattern, out_path, fps=FPS):
    """ffmpeg (on PATH) frames -> H.264 MP4 at 1920x1080, for review."""
    import subprocess
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(fps), '-i', str(frame_pattern),
                    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '18', str(out_path)], check=True)
