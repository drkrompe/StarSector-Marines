"""C1 v2 — the turn-around: one Blender scene, camera at the origin (see c1-v2-shot.md).

Run in background Blender:

    blender -b --factory-startup --python c1v2_scene.py -- [--animatic | --final] [--frames a:b]
                     [--from s] [--to s] [--step n] [--still t ...] [--samples n]

CINE_SCALE=50 in the environment renders at half size, for sketch passes that only ask about
layout or timing; finals and anything shown to the owner as a picture are at 100.

--animatic   EEVEE at final timing, written to build/c1v2/animatic/frames/%05d.png under the
             repository root (ignored output). This is the layout pass the owner signs off.
--final      the look pass: Cycles at full size, motion blur and denoising, every volume and the
             planet's air shell, written to build/cinematic/c1-corvus/frames/%05d.png (the C1
             delivery folder). Frames already on disk are skipped and a frame in progress is held
             by a placeholder, so several machines can share a range, and a stopped run resumes.
--still t    render single frames at these times (seconds) to build/c1v2/stills/ (with --final,
             Cycles stills to build/c1v2/stills-final/).
--samples n  Cycles samples per pixel for --final (default 128, denoised; 64 under --profile cheap).
--profile p  Cycles settings for --final (or C1_PROFILE in the environment). 'production' (the
             default) is what every C1 final so far has rendered with: 128 samples, no adaptive
             sampling, default bounces, OIDN on the CPU, the scene re-synced every frame. 'cheap'
             is the faster candidate under test: 64 samples with adaptive sampling (threshold
             0.02), max bounces 6, volume bounces 0, persistent data, OIDN on the GPU. Never mix
             profiles within one shot's frames.
--volume-step-rate r
             switch volumes to biased ray marching at this step rate (volume_step_rate only acts
             when volume_biased is on; the default null-scattering volumes ignore it).
--from s, --to s, --step n
             with --animatic, render only those seconds of the shot, every nth frame (default
             every frame): a motion question rarely needs the whole 38 s at 24 fps.
--planet-layer dir
             with --final, render only Jangala (everything else held out, volumes hidden, the
             frame cropped to the disc) as RGBA to dir/%05d.png, for planet_fix.py. Takes
             --frames a:b or --frame-list f,f,...
--air-legacy the unscaled, opaque air shell the 2026-10-22 C1 final rendered with; only for the
             'before' planet layer.
--save       also write the assembled scene to build/c1v2/c1v2-scene.blend.

World frame, metres: +Y is the final view direction (toward Jangala), +Z up. The camera only
rotates, so anything far away keeps a correct perspective whatever it is: the planet, the
station and the flotilla are distant, and the painted ones are camera-facing cards. Hulls the
camera passes close to are the real 3D models at their own metres_per_unit.

Inputs the scene appends are build outputs of other packages (ignored, not in Git). They are
read from build/c1v2/inputs/ under the repository root:
    second-wind-patched.blend   second-wind-cinematic/second-wind-states/patched
    hound-skinned.blend         hound-surface/build/skinned.blend
    hound-a-clean.blend         hound-export (export_hound.py, with the VFX kit's SWFX), and its
                                manifest as hound-a-clean-manifest.json
    mudskipper-skinned.blend    mudskipper-surface/build/skinned.blend
    legion-geometry.blend       legion-geometry/legion-geometry.blend
    onslaught.blend             Drive builds/onslaught/1480919b1 or later, with its SWFX effects
                                (and onslaught-manifest.json)
    legion.blend, mudskipper.blend   legion-cinematic / mudskipper-cinematic exports with SWFX (and
                                <hull>-manifest.json). Optional: without them the scene falls back
                                to legion-geometry.blend and mudskipper-skinned.blend.
    lasher.blend                Drive builds/lasher/4e2ed50 (with lasher-geometry.json)
    colossus.blend              colossus-cinematic (finalize.sh), optional colossus-manifest.json
    shepherd.blend              shepherd-cinematic (finalize.sh), optional shepherd-manifest.json
"""
import json
import math
import os
import sys
from pathlib import Path

import bmesh
import bpy
from mathutils import Euler, Matrix, Quaternion, Vector

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import render_planet as planet  # noqa: E402  (guarded main; materials and sphere helper)

REPO = BASE.parents[3]
INPUTS = REPO / 'build' / 'c1v2' / 'inputs'
OUT = REPO / 'build' / 'c1v2'
FINAL_FRAMES = REPO / 'build' / 'cinematic' / 'c1-corvus' / 'frames'   # the C1 delivery folder
ASSETS = BASE / 'assets'
STATES = BASE.parent / 'second-wind-states' / 'patched'
# The Second Wind build this shot renders: its .blend input is that build's patched state, and its
# manifest is committed under ../pins/. The shot does not follow second-wind-states/'s latest
# manifest; adopting a new build means moving SW_PIN and committing that build's manifest beside it.
SW_PIN = 'dc62710dc'
# C1_SW_PIN names another committed pin, for a comparison still against an earlier build only;
# finals render the pin above.
SW_PIN = os.environ.get('C1_SW_PIN', SW_PIN)
SW_MANIFEST = BASE.parent / 'pins' / ('second-wind-%s-patched.manifest.json' % SW_PIN)


def check_pinned_blend(path, manifest):
    """Refuse a Second Wind .blend that is missing or is not the pinned build (SW_PIN). Copy it from
    Drive builds/second-wind/<SW_PIN>/patched/."""
    import hashlib
    if not Path(path).exists():
        raise SystemExit('%s is missing: copy it from Drive builds/second-wind/%s/patched/' % (path, SW_PIN))
    digest = hashlib.sha256(Path(path).read_bytes()).hexdigest()
    if digest != manifest['asset_sha256']:
        raise SystemExit('%s is not Second Wind build %s (sha256 %s, pin %s)'
                         % (path, SW_PIN, digest[:12], manifest['asset_sha256'][:12]))
TIMELINE = REPO / 'mod' / 'data' / 'cinematic' / 'jangala-arrival' / 'c1.json'

FPS = 24
W, H = 1920, 1080
LENS_MM, SENSOR_MM = 50.0, 36.0
F_PX = LENS_MM / SENSOR_MM * W                    # 2667 px, the v1 lens
DURATION = json.loads(TIMELINE.read_text())['duration']
FRAMES = round(DURATION * FPS)

# Light: Corvus high and to the side of the lane, leaning slightly toward the opening view, so
# the hero's dorsal deck and near flank are lit from the first frame. Jangala is then about half
# lit, day side left, night side (city lights) right.
SUN_DIR = Vector((-0.75, 0.15, 0.65)).normalized()   # toward the sun
SUN_STRENGTH = 4.6
STAR_CELLS = 300.0   # star lattice cells per radian of sky (a cell is about 9 px)
STAR_FILL = 0.25     # share of cells that hold a visible star

# Final framing: looking down the lane at Jangala, pitched slightly down.
FINAL_PITCH = math.radians(-4.0)
EEVEE_PREVIEW = True   # layout passes; the final look pass renders Cycles
AIR_LEGACY = False     # --air-legacy: the unscaled (opaque) air shell, for planet_layer's difference
EEVEE_VOLUME_RANGE = 3000.0   # metres of depth EEVEE renders volumes in: out to the hero at the end

# --- Second Wind's pass ----------------------------------------------------------------------
LANE_X, LANE_Z = 110.0, -45.0     # passes on the camera's left (opening view), below: dorsal reads
HERO_LIST = math.radians(-9.0)    # a standing list to port
HERO_TRIM = math.radians(-5.0)    # nose down toward Jangala
PASS_T, PASS_SPEED = 10.0, 28.0   # closest approach at 10 s, crawling past at 28 m/s
START_S, END_S = -1400.0, 2600.0  # along the lane (y), metres, at t=0 and at the end


def lane_s(t):
    """Distance along the lane. Quadratic each side of the pass, matched speed at the pass:
    a hull braking hard to cross the customs line slowly, then opening its drive toward Jangala."""
    if t <= PASS_T:
        u = PASS_T - t
        a = (-START_S - PASS_SPEED * PASS_T) / PASS_T ** 2
        return -(a * u * u + PASS_SPEED * u)
    w = t - PASS_T
    c = (END_S - PASS_SPEED * (DURATION - PASS_T)) / (DURATION - PASS_T) ** 2
    return c * w * w + PASS_SPEED * w


def hero_pose(t):
    """Position and limp: a slow roll and yaw wander; the drive is not steady."""
    loc = Vector((LANE_X, lane_s(t), LANE_Z))
    # Nothing in space is level: the hull carries a standing list to port and a nose-down
    # attitude toward Jangala, and the drive's unsteadiness wanders both.
    roll = HERO_LIST + math.radians(3.0) * math.sin(2 * math.pi * t / 11.0)
    pitch = HERO_TRIM + math.radians(1.5) * math.sin(2 * math.pi * t / 13.0 + 1.9)
    yaw = math.radians(1.5) * math.sin(2 * math.pi * t / 17.0 + 0.7)
    return loc, attitude(math.pi / 2 + yaw, pitch, roll)   # hull +X (bow) along world +Y


def attitude(heading, pitch, roll):
    """Hull orientation for the +X-bow convention: heading about world Z, then nose up (+pitch)
    about the hull's Y, then roll about its X (+ is starboard wing down)."""
    q = (Quaternion((0, 0, 1), heading) @ Quaternion((0, 1, 0), -pitch)
         @ Quaternion((1, 0, 0), roll))
    return q.to_euler('XYZ')


def smoothstep(a, b, x):
    x = min(1.0, max(0.0, (x - a) / (b - a)))
    return x * x * (3 - 2 * x)


def look_quat(direction):
    return direction.normalized().to_track_quat('-Z', 'Y')


def final_dir():
    return Vector((0.0, math.cos(FINAL_PITCH), math.sin(FINAL_PITCH)))


def camera_quat(t):
    """Hold the hero, then hand over to the final framing on Jangala.

    The aim leads the hull slightly in the direction it travels so it is never centred dead,
    and from 18 s the aim eases from the hull to the final view (done by 28 s)."""
    loc, _ = hero_pose(t)
    ahead, _ = hero_pose(t + 1.2)
    aim = (loc * 0.7 + ahead * 0.3)
    w = smoothstep(18.0, 28.0, t)
    q = look_quat(aim).slerp(look_quat(final_dir()), w)
    # The camera is not level with the lane either: a slight roll that settles as it lands.
    roll = math.radians(7.0) + (math.radians(3.0) - math.radians(7.0)) * smoothstep(4.0, 30.0, t)
    return q @ Quaternion((0, 0, 1), roll)


def direction_at_px(px, py, final=True):
    """World direction through pixel (px, py) of the final framing."""
    v = Vector(((px - W / 2) / F_PX, -(py - H / 2) / F_PX, -1.0))
    return (look_quat(final_dir()).to_matrix() @ v).normalized()


# --- scene pieces ----------------------------------------------------------------------------

def fresh_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.fps = FPS
    scene.frame_start, scene.frame_end = 0, FRAMES - 1
    scene.render.resolution_x, scene.render.resolution_y = W, H
    scene.render.resolution_percentage = int(os.environ.get('CINE_SCALE', '100'))
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Base Contrast'
    return scene


def star_world(scene):
    """Stars and a faint galactic band from the view direction: a full sphere, so any pan works."""
    world = bpy.data.worlds.new('C1v2 | sky')
    scene.world = world
    world.use_nodes = True
    nt = world.node_tree
    g = planet.Graph(world)
    bg = nt.nodes['Background']
    coord = nt.nodes.new('ShaderNodeTexCoord').outputs['Generated']
    # Stars are Voronoi feature points of a lattice the sky sphere passes through. Judging them by
    # their 3D distance from the sphere keeps only points in a thin shell, and wherever the sphere
    # runs nearly flat through a lattice layer (straight down an axis, which is down the lane) that
    # shell sorts them into rings. Each point is instead projected onto the sphere and judged by
    # its angular distance from the view direction, which has no shell and so no rings.
    view = g.vmath('NORMALIZE', coord)
    vor = nt.nodes.new('ShaderNodeTexVoronoi')
    vor.inputs['Scale'].default_value = 1.0
    lattice = nt.nodes.new('ShaderNodeVectorMath')
    lattice.operation = 'SCALE'
    nt.links.new(view, lattice.inputs[0])
    lattice.inputs['Scale'].default_value = STAR_CELLS
    nt.links.new(lattice.outputs['Vector'], vor.inputs['Vector'])
    feature = g.vmath('NORMALIZE', vor.outputs['Position'])
    apart = g.math('MULTIPLY', g.vmath('DISTANCE', feature, view, out='Value'), STAR_CELLS)
    star = g.smooth(apart, 0.12, 0.0)                                  # discs about a pixel across
    # Every cell holds a point, so only some of them are lit: one random channel picks which
    # (about the density the shell used to keep), another how bright.
    rand = nt.nodes.new('ShaderNodeSeparateColor')
    nt.links.new(vor.outputs['Color'], rand.inputs['Color'])
    lit = g.math('GREATER_THAN', rand.outputs[0], 1.0 - STAR_FILL)
    bright = g.math('POWER', rand.outputs[1], 8.0)                      # few bright, many dim
    stars = g.math('MULTIPLY', g.math('MULTIPLY', star, lit), g.math('MULTIPLY', bright, 6.0))
    band_n = Vector((0.3, -0.2, 0.93)).normalized()
    across = g.vmath('DOT_PRODUCT', g.vmath('NORMALIZE', coord), tuple(band_n), out='Value')
    band = g.smooth(g.math('ABSOLUTE', across), 0.22, 0.0)
    noise = nt.nodes.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = 6.0
    noise.inputs['Detail'].default_value = 8.0
    nt.links.new(coord, noise.inputs['Vector'])
    glow = g.math('MULTIPLY', band, g.math('MULTIPLY', noise.outputs['Fac'], 0.035))
    g.wire(bg.inputs['Strength'], g.math('ADD', stars, glow))
    bg.inputs['Color'].default_value = (0.92, 0.94, 1.0, 1.0)


def add_sun(scene):
    sun = bpy.data.objects.new('C1v2 | Corvus', bpy.data.lights.new('C1v2 | Corvus', 'SUN'))
    sun.data.energy = SUN_STRENGTH
    sun.data.color = (1.0, 0.97, 0.9)
    sun.data.angle = math.radians(0.5)
    sun.rotation_euler = SUN_DIR.to_track_quat('Z', 'Y').to_euler()
    scene.collection.objects.link(sun)


def add_planet(scene):
    """v1's Jangala (render_planet materials), placed where v1 framed it in the final view."""
    alpha = math.atan(planet.DISC_RADIUS_PX / F_PX)
    distance = 30000.0
    radius = distance * math.sin(alpha)
    centre = direction_at_px(*planet.DISC_CENTRE_PX) * distance
    # render_planet shades from object-space position and expects the camera on its -Y side,
    # so the spheres are translated and scaled, never rotated.
    # The air's density is per planet radius; Cycles integrates per world unit, so it is scaled
    # with the sphere. --air-legacy keeps the unscaled shell the 2026-10-22 final rendered with,
    # opaque and grey, only so planet_layer can difference it out of those frames.
    air_scale = 1.0 if AIR_LEGACY else radius
    for name, r, mat in (('Jangala | surface', 1.0, planet.surface_material(SUN_DIR)),
                         ('Jangala | clouds', planet.CLOUD_RADIUS, planet.cloud_material()),
                         ('Jangala | air', planet.AIR_RADIUS, planet.air_material(air_scale))):
        obj = planet.sphere(name, r)
        obj.data.materials.append(mat)
        obj.location = centre
        obj.scale = (radius,) * 3
        if name.endswith('air') and EEVEE_PREVIEW:
            obj.hide_render = True   # the scattering shell is a Cycles material; EEVEE smears it


def card(scene, image_name, px, py, height_px, distance, name):
    """A painted imposter: unlit, alpha, facing the camera at the origin."""
    img = bpy.data.images.load(str(ASSETS / image_name), check_existing=True)
    aspect = img.size[0] / img.size[1]
    h = height_px / F_PX * distance
    bpy.ops.mesh.primitive_plane_add(size=1.0)
    obj = bpy.context.active_object
    obj.name = name
    obj.scale = (h * aspect, h, 1.0)
    d = direction_at_px(px, py)
    obj.location = d * distance
    obj.rotation_euler = (-d).to_track_quat('Z', 'Y').to_euler()
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    if hasattr(mat, 'surface_render_method'):
        mat.surface_render_method = 'BLENDED'
    nt = mat.node_tree
    nt.nodes.remove(nt.nodes['Principled BSDF'])
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    emit = nt.nodes.new('ShaderNodeEmission')
    clear = nt.nodes.new('ShaderNodeBsdfTransparent')
    mix = nt.nodes.new('ShaderNodeMixShader')
    nt.links.new(tex.outputs['Color'], emit.inputs['Color'])
    nt.links.new(tex.outputs['Alpha'], mix.inputs['Fac'])
    nt.links.new(clear.outputs[0], mix.inputs[1])
    nt.links.new(emit.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], nt.nodes['Material Output'].inputs['Surface'])
    obj.data.materials.append(mat)
    return obj


def append_collection(path, name):
    with bpy.data.libraries.load(str(path), link=False) as (src, dst):
        dst.collections = [name]
    coll = dst.collections[0]
    bpy.context.scene.collection.children.link(coll)
    return coll


def rooted(path, label, metres_per_unit):
    """Append a flat 'Collection' hull and hang it under one scaled root empty."""
    coll = append_collection(path, 'Collection')
    root = bpy.data.objects.new(label + ' | root', None)
    bpy.context.scene.collection.objects.link(root)
    for obj in coll.all_objects:
        if obj.parent is None:
            obj.parent = root
    root.scale = (metres_per_unit,) * 3
    return root


# Second Wind's attitude corrections on the pass: (VFX kit manoeuvre, start s, end s). The limp
# wanders its roll and yaw (hero_pose); these are the puffs that catch it.
HERO_RCS = (
    ('roll-port-down', 3.4, 4.2),
    ('yaw-starboard', 5.9, 6.5),
    ('roll-starboard-down', 8.6, 9.5),
    ('pitch-up', 11.2, 11.8),
    ('yaw-port', 14.0, 14.6),
)


def add_hero(scene):
    """Second Wind, patched, with its own effects when the export carries them: the two running
    drives, RCS corrections on the pass, the conduit sparks and seal wisps. The exhaust haze and
    landing dust are smoke in air and stay out."""
    manifest = json.loads(SW_MANIFEST.read_text())
    check_pinned_blend(INPUTS / 'second-wind-patched.blend', manifest)
    ship, fx = load_with_effects(scene, INPUTS / 'second-wind-patched.blend', manifest)
    root = bpy.data.objects[manifest['root']]
    root.scale = (manifest['metres_per_unit'],) * 3
    root.rotation_mode = 'XYZ'
    if fx is not None:
        for obj in fx.all_objects:
            if obj is not None and (' haze' in obj.name or 'dust' in obj.name):
                obj.hide_render = obj.hide_viewport = True
        sys.path.insert(0, str(BASE.parents[1] / 'second-wind-vfx'))
        import swfx_core
        import swfx_thrusters

        class Knobs:            # the kit's keyframing on this asset's controls empty
            key = swfx_core.Context.key

            def __init__(self, controls):
                self.controls = controls
        knobs = Knobs(next(o for o in fx.all_objects if o and o.name.startswith('SWFX controls')))
        knobs.controls['thr_throttle'] = 1.0
        for move, t0, t1 in HERO_RCS:
            swfx_thrusters.manoeuvre(knobs, move, round(t0 * FPS), round(t1 * FPS))
    else:
        # Stand-in drive glow at the running engines, for an export without effects: hang a light
        # on the root at each exhaust anchor's place relative to it.
        for i in manifest['engines_running']:
            anchor = bpy.data.objects.get('Engine %d exhaust anchor' % i)
            if anchor is None:
                continue
            light = bpy.data.objects.new('Drive %d glow' % i, bpy.data.lights.new('Drive %d glow' % i, 'POINT'))
            light.data.energy = 2.5e5
            light.data.color = (0.55, 0.75, 1.0)
            light.data.shadow_soft_size = 1.5
            light.parent = root
            light.matrix_basis = anchor.matrix_parent_inverse @ anchor.matrix_basis @ Matrix.Translation((2.0, 0, 0))
            scene.collection.objects.link(light)
    for f in range(FRAMES):
        loc, rot = hero_pose(f / FPS)
        root.location, root.rotation_euler = loc, rot
        root.keyframe_insert('location', frame=f)
        root.keyframe_insert('rotation_euler', frame=f)
    return root


def load_with_effects(scene, path, manifest):
    """Append a finished asset's ship collection and, when it has them, its anchors collection and
    the VFX kit's SWFX, all from one load, and link every one into the scene. The effects hang off
    the anchors, and an object outside the view layer is never evaluated: unlinked, the anchors
    would stay where the file left them while the ship moved away. Returns (ship, SWFX or None)."""
    wanted = [manifest['append_collection'], manifest.get('anchors_collection'), 'SWFX']
    with bpy.data.libraries.load(str(path), link=False) as (src, dst):
        dst.collections = [n for n in wanted if n and n in src.collections]
    linked = {c.name for c in scene.collection.children_recursive}
    for coll in dst.collections:
        if coll.name not in linked:
            scene.collection.children.link(coll)
            linked |= {coll.name} | {c.name for c in coll.children_recursive}
    fx = next((c for c in dst.collections if c.name.split('.')[0] == 'SWFX'), None)
    return dst.collections[0], fx


def fly(root, t0, t1, p0, p1, pitch=0.0, roll=0.0):
    """Constant velocity through p0 at t0 and p1 at t1, for the whole shot: nothing parks.
    The bow points along the velocity, then the hull takes its own pitch and roll."""
    v = p1 - p0
    heading = math.atan2(v.y, v.x)
    climb = math.atan2(v.z, v.xy.length)
    root.rotation_euler = attitude(heading, climb + pitch, roll)
    for f in range(FRAMES):
        k = (f / FPS - t0) / (t1 - t0)
        root.location = p0.lerp(p1, k)
        root.keyframe_insert('location', frame=f)


def hull_collection(path, name='Collection'):
    """An appended hull kept out of the render itself, for collection instances."""
    coll = append_collection(path, name)
    bpy.context.view_layer.layer_collection.children[coll.name].exclude = True
    return coll


def drive_glows(coll, root_name, engines, energy):
    """Stand-in drive glow inside a published hull's collection, so every instance carries it.

    The exhaust anchors sit in the hull's anchors collection, which is excluded with the hull and
    never evaluated: read each one's place relative to the root and hang the light on the root.
    engines: (anchor name, mouth radius in hull units) pairs."""
    root = bpy.data.objects[root_name]
    for anchor_name, mouth in engines:
        anchor = bpy.data.objects.get(anchor_name)
        if anchor is None:
            continue
        name = '%s | %s glow' % (root_name.split(' |')[0], anchor_name.replace(' exhaust anchor', ''))
        light = bpy.data.objects.new(name, bpy.data.lights.new(name, 'POINT'))
        light.data.energy = energy * (mouth / 4.6) ** 2
        light.data.color = (1.0, 0.72, 0.42)   # low-tech drives burn orange
        light.data.shadow_soft_size = mouth * 0.5
        light.parent = root
        light.matrix_basis = (anchor.matrix_parent_inverse @ anchor.matrix_basis
                              @ Matrix.Translation((mouth * 0.6, 0, 0)))
        coll.objects.link(light)


def quiet_lamps(coll, strength):
    """Running lights sized for a close camera blow out to blobs larger than the hull at picket
    range: cap their emission for distant formation work."""
    for obj in coll.all_objects:
        if obj.type != 'MESH' or not obj.name.startswith('Light '):
            continue
        for mat in obj.data.materials:
            if mat is None or not mat.use_nodes:
                continue
            for node in mat.node_tree.nodes:
                sock = node.inputs.get('Emission Strength') or (
                    node.inputs.get('Strength') if node.type == 'EMISSION' else None)
                if sock is not None and sock.default_value > strength:
                    sock.default_value = strength


def instance(coll, metres_per_unit, name):
    e = bpy.data.objects.new(name, None)
    e.instance_type = 'COLLECTION'
    e.instance_collection = coll
    e.scale = (metres_per_unit,) * 3
    bpy.context.scene.collection.objects.link(e)
    return e


# Hegemony picket out by the station, 6-7.5 km off: it has come out to meet the inbound lane and
# is heading back down it toward the player, bows on but 20 degrees off head-on (the way the C2
# Legion meets Second Wind), so over the shot it grows and slides slowly right toward the lane.
# Each hull is placed through a pixel of the final view at its own range, all on one heading with
# small attitude differences of their own. The Onslaught leads, Lashers screen ahead of it, the
# Legion holds the second line and the Hounds close up the rear.
FLOTILLA_HEADING = math.radians(290)
FLOTILLA_SPEED = 30.0
FLOTILLA = (  # (hull, px, py, range m, pitch deg, roll deg)
    ('onslaught', 490, 360, 6000, 2, -3),
    ('lasher', 330, 340, 6100, 1, -4),
    ('lasher', 640, 370, 6200, 3, 2),
    ('lasher', 400, 420, 5800, 0, -2),
    ('lasher', 590, 430, 5900, 2, 3),
    ('legion', 470, 300, 6900, 3, -2),
    ('hound', 350, 280, 7400, 4, -5),
    ('hound', 580, 290, 7300, 2, 4),
)
# Pixels are where each hull sits at mid-shot.


def add_flotilla(scene, hulls):
    """hulls maps a hull to (collection, metres per unit) for instancing, or to a function that
    links a single finished asset and returns its root."""
    if isinstance(hulls['legion'], tuple):
        for obj in hulls['legion'][0].all_objects:
            if obj.type == 'MESH' and not obj.data.materials:
                obj.data.materials.append(grey('Legion | hull grey', 0.32))
    drift = flotilla_drift()
    starts = []
    for i, (hull, px, py, rng, pitch, roll) in enumerate(FLOTILLA):
        if callable(hulls[hull]):
            e = hulls[hull]()
        else:
            e = instance(*hulls[hull], 'Flotilla %d | %s' % (i, hull))
        e.rotation_euler = attitude(FLOTILLA_HEADING, math.radians(pitch), math.radians(roll))
        start = direction_at_px(px, py) * rng - drift * (DURATION / 2)
        starts.append(start)
        for f in range(FRAMES):
            e.location = start + drift * (f / FPS)
            e.keyframe_insert('location', frame=f)
    return starts


def flotilla_drift():
    return Vector((math.cos(FLOTILLA_HEADING), math.sin(FLOTILLA_HEADING), 0.0)) * FLOTILLA_SPEED


# The Legion's air wing (the XIV Legion flies Broadswords) working round the picket: pairs flying
# loops about a hull of the formation, each loop in its own tilted plane, banked into the turn and
# carried along with the formation's drift. At 6-7 km a Broadsword is a few pixels: these are
# motion and scale, not detail.
BROADSWORD = BASE.parents[1] / 'broadsword-geometry'
FIGHTER_ELEMENTS = (  # (FLOTILLA index circled, radius m, period s (negative: other way), phase, tilt deg)
    (5, 450, 14.0, 0.0, 20),
    (5, 620, -18.0, 2.0, -15),
    (0, 720, 21.0, 1.0, 10),
    (0, 950, -25.0, 4.0, 30),
)
WINGMAN = (-30.0, 22.0)   # metres behind the leader along the loop, and outboard of it
FIGHTER_BANK = math.radians(55)
FIGHTER_GLOW, FIGHTER_GLOW_RADIUS = 40.0, 3.5   # drive bead emission and radius (m)


def add_fighters(scene, starts):
    geo = json.loads((BROADSWORD / 'geometry.json').read_text())
    coll = hull_collection(BROADSWORD / 'broadsword-geometry.blend', geo['collections']['model'])
    for obj in coll.all_objects:
        if obj.type == 'MESH' and not obj.data.materials:
            obj.data.materials.append(grey('Broadsword | hull grey', 0.45))
    # The hull alone is a speck at picket range; what reads is the drive. Each engine carries an
    # emissive bead a few metres across, deliberately larger than the bell, so the wing shows as
    # sparks working round the formation.
    glow = bpy.data.materials.new('Broadsword | drive bead')
    glow.use_nodes = True
    nt = glow.node_tree
    nt.nodes.remove(nt.nodes['Principled BSDF'])
    emit = nt.nodes.new('ShaderNodeEmission')
    emit.inputs['Color'].default_value = (1.0, 0.78, 0.5, 1.0)
    emit.inputs['Strength'].default_value = FIGHTER_GLOW
    nt.links.new(emit.outputs[0], nt.nodes['Material Output'].inputs['Surface'])
    root = bpy.data.objects[geo['root']['name']]
    mpu = geo['metres_per_unit']
    for eng in geo['engines']:
        mesh = bpy.data.meshes.new('Broadsword | drive bead %d' % eng['index'])
        bead = bpy.data.objects.new(mesh.name, mesh)
        bead['thrust_plume'] = True          # the drive as it reads at picket range (`thrust_markers.craft_drive_fx`)
        coll.objects.link(bead)
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=8, radius=FIGHTER_GLOW_RADIUS / mpu)
        bm.to_mesh(mesh)
        bm.free()
        mesh.materials.append(glow)
        bead.parent = root
        bead.location = Vector(eng['anchor_location']) + Vector((-FIGHTER_GLOW_RADIUS / mpu, 0, 0))
    drift = flotilla_drift()
    for k, (anchor, radius, period, phase, tilt) in enumerate(FIGHTER_ELEMENTS):
        tilt = math.radians(tilt)
        sense = 1 if period > 0 else -1
        u = Vector((1.0, 0.0, 0.0))
        v = Vector((0.0, math.cos(tilt), math.sin(tilt)))
        normal = u.cross(v) * sense
        for m, (behind, out) in enumerate(((0.0, 0.0), WINGMAN)):
            r = radius + out
            e = instance(coll, geo['metres_per_unit'], 'Fighter %d.%d | broadsword' % (k, m))
            e.rotation_mode = 'QUATERNION'
            prev = None
            for f in range(FRAMES):
                t = f / FPS
                th = phase + sense * (behind / r + 2 * math.pi * t / abs(period))
                centre = starts[anchor] + drift * t
                radial = u * math.cos(th) + v * math.sin(th)
                tangent = (-u * math.sin(th) + v * math.cos(th)) * sense
                vel = tangent * (2 * math.pi * r / abs(period)) + drift
                x = vel.normalized()
                z = normal * math.cos(FIGHTER_BANK) - radial * math.sin(FIGHTER_BANK)
                z = (z - x * z.dot(x)).normalized()
                y = z.cross(x)
                q = Matrix((x, y, z)).transposed().to_quaternion()
                if prev is not None and prev.dot(q) < 0:
                    q.negate()
                prev = q
                e.location = centre + radial * r
                e.rotation_quaternion = q
                e.keyframe_insert('location', frame=f)
                e.keyframe_insert('rotation_quaternion', frame=f)


def add_traffic(scene):
    # Each hull file is appended once; every appearance of it is an instance of that copy.
    # Hull scales from ship_scale.py (option C, 2026-10-18).
    hulls = {'hound': (hull_collection(INPUTS / 'hound-skinned.blend'), 0.88)}
    # The picket's two capitals are single finished assets carrying their own drive plumes, gun
    # rigs and lamps (the VFX kit's SWFX), so they are linked, not instanced. Lamps are capped for
    # picket range.
    hulls['onslaught'] = lambda: effects_asset(scene, 'onslaught.blend', 'onslaught-manifest.json', 6.0)
    if (INPUTS / 'legion.blend').exists():
        hulls['legion'] = lambda: effects_asset(scene, 'legion.blend', 'legion-manifest.json', 6.0)
    else:
        hulls['legion'] = (hull_collection(INPUTS / 'legion-geometry.blend'), 0.99)
    las = json.loads((INPUTS / 'lasher-geometry.json').read_text())
    coll = hull_collection(INPUTS / 'lasher.blend', las['asset']['collection'])
    drive_glows(coll, las['asset']['root'], [(e['anchor'], e['mouth_radius']) for e in las['engines']], 3.0e5)
    quiet_lamps(coll, 6.0)
    hulls['lasher'] = (coll, las['metres_per_unit'])
    add_fighters(scene, add_flotilla(scene, hulls))
    # Local working traffic across the lane during the reveal, about a kilometre out.
    if (INPUTS / 'mudskipper.blend').exists():
        mud = effects_asset(scene, 'mudskipper.blend', 'mudskipper-manifest.json')
    else:
        mud = rooted(INPUTS / 'mudskipper-skinned.blend', 'Mudskipper', 0.85)
    fly(mud, 13.0, 23.0, Vector((520, 980, -110)), Vector((-280, 900, -20)), roll=math.radians(18))
    # Customs: a Hegemony Hound crosses between camera and hero during hail 03.
    fly(effects_asset(scene, 'hound-a-clean.blend', 'hound-a-clean-manifest.json'), 23.0, 32.0, Vector((-300, 220, -70)), Vector((260, 270, -30)),
        roll=math.radians(-24))
    # Civilian freight in the lanes and queuing at the station: the published freighter hulls.
    import c1_fleet
    c1_fleet.add(sys.modules[__name__])


def effects_asset(scene, blend, manifest_name, lamp_cap=None):
    """A finished cinematic hull linked with the VFX kit's SWFX effects from the same load, so the
    plumes, gun rigs and lamps ride its anchors. Linked rather than instanced, because the effects
    hang off anchors in the hull's own hierarchy; each call is one more copy of the asset.

    Every drive runs at full throttle. The kit's exhaust haze is smoke in air; in vacuum it is
    left out entirely, not dialled down. Returns the root, scaled to metres."""
    manifest = json.loads((INPUTS / manifest_name).read_text())
    ship, fx = load_with_effects(scene, INPUTS / blend, manifest)
    if fx is not None:
        controls = next(o for o in fx.all_objects if o and o.name.startswith('SWFX controls'))
        controls['thr_throttle'] = 1.0
        for child in fx.children_recursive:
            if child.name.startswith('SWFX | Smoke'):
                for obj in child.all_objects:
                    obj.hide_render = obj.hide_viewport = True
    if lamp_cap is not None:
        quiet_lamps(ship, lamp_cap)
    root = bpy.data.objects[manifest['root']]
    root.scale = (manifest['metres_per_unit'],) * 3
    root.rotation_mode = 'XYZ'
    return root


def grey(name, value):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    p = mat.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (value, value, value * 1.03, 1)
    p.inputs['Roughness'].default_value = 0.55
    p.inputs['Metallic'].default_value = 0.6
    return mat


def add_cards(scene):
    card(scene, 'station-v2.png', 790, 250, 290, 9000.0, 'Card | Jangala Station')
    # The Atlas card's place (170, 90 at 5.2 km) now holds a 3D Colossus: c1_fleet.LANE.


def add_camera(scene):
    cam = bpy.data.objects.new('C1v2 | camera', bpy.data.cameras.new('C1v2 | camera'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = LENS_MM, SENSOR_MM, 'HORIZONTAL'
    cam.data.clip_start, cam.data.clip_end = 2.0, 60000.0
    cam.rotation_mode = 'QUATERNION'
    prev = None
    for f in range(FRAMES):
        q = camera_quat(f / FPS)
        if prev is not None and prev.dot(q) < 0:
            q.negate()
        cam.rotation_quaternion = q
        cam.keyframe_insert('rotation_quaternion', frame=f)
        prev = q
    return cam


def cycles(scene, samples, profile='production', volume_step_rate=None):
    """The look pass. A GPU is used when the machine has one Cycles can drive."""
    scene.render.engine = 'CYCLES'
    prefs = bpy.context.preferences.addons['cycles'].preferences
    for kind in ('OPTIX', 'CUDA', 'METAL', 'HIP', 'ONEAPI'):
        try:
            prefs.compute_device_type = kind
        except TypeError:
            continue
        prefs.get_devices()
        gpus = [d for d in prefs.devices if d.type == kind]
        if gpus:
            for d in gpus:
                d.use = True
            scene.cycles.device = 'GPU'
            break
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    scene.cycles.denoiser = 'OPENIMAGEDENOISE'
    if profile == 'cheap':
        scene.cycles.use_adaptive_sampling = True
        scene.cycles.adaptive_threshold = 0.02
        scene.cycles.max_bounces = 6
        scene.cycles.volume_bounces = 0
        scene.cycles.denoising_use_gpu = True
        scene.render.use_persistent_data = True
    elif profile != 'production':
        raise SystemExit('unknown --profile %r (production or cheap)' % profile)
    if volume_step_rate is not None:
        scene.cycles.volume_biased = True
        scene.cycles.volume_step_rate = volume_step_rate
    scene.render.use_motion_blur = True
    scene.render.motion_blur_shutter = 0.5          # 180 degrees, as C2's patrol pass
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'
    scene.render.use_overwrite = False
    scene.render.use_placeholder = True
    c = scene.cycles
    print('C1V2 Cycles on %s, profile %s, %d samples, adaptive %s (%.3f), bounces %d/vol %d, '
          'persistent %s, denoise %s gpu %s, volumes %s, SW pin %s'
          % (c.device, profile, samples, c.use_adaptive_sampling, c.adaptive_threshold, c.max_bounces,
             c.volume_bounces, scene.render.use_persistent_data, c.denoiser, c.denoising_use_gpu,
             ('biased step %.2f' % c.volume_step_rate) if c.volume_biased else 'null scattering', SW_PIN),
          flush=True)


def eevee(scene, samples=16):
    try:
        scene.render.engine = 'BLENDER_EEVEE_NEXT'
    except TypeError:
        scene.render.engine = 'BLENDER_EEVEE'
    scene.eevee.taa_render_samples = samples
    # EEVEE spreads one grid of volume samples over the depth its volumes span. With the picket's
    # and the freighters' plumes 5-9 km out, the customs Hound's plumes up close came out in
    # blocks. Layout passes keep the grid to the depth Second Wind's drives reach by the end of
    # the shot (at 700 m its plumes vanished as it pulled away) and pack its slices toward the
    # camera, so the near Hound keeps its resolution. The far plumes are a few pixels; the Cycles
    # look pass renders every volume at its own resolution.
    scene.eevee.use_volume_custom_range = True
    scene.eevee.volumetric_start, scene.eevee.volumetric_end = 2.0, EEVEE_VOLUME_RANGE
    scene.eevee.volumetric_tile_size = '4'
    scene.eevee.volumetric_sample_distribution = 0.95
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'


def build(final=False, samples=128, profile='production', volume_step_rate=None):
    global EEVEE_PREVIEW
    EEVEE_PREVIEW = not final
    scene = fresh_scene()
    star_world(scene)
    add_sun(scene)
    add_planet(scene)
    add_cards(scene)
    add_hero(scene)
    add_traffic(scene)
    add_camera(scene)
    sys.path.insert(0, str(BASE.parent / 'thrust-markers'))
    import thrust_shots                      # every craft's nozzles marked for VFX and sound
    thrust_shots.mark_c1(sys.modules[__name__])
    if final:
        cycles(scene, samples, profile, volume_step_rate)
    else:
        eevee(scene)
    return scene


def planet_disc_px(frame):
    """(cx, cy, r) of Jangala's disc in full-size pixels at a frame, or None behind the camera."""
    alpha = math.atan(planet.DISC_RADIUS_PX / F_PX)
    distance = 30000.0
    centre = direction_at_px(*planet.DISC_CENTRE_PX) * distance
    v = camera_quat(frame / FPS).inverted() @ centre
    depth = -v.z
    if depth <= 0:
        return None
    return W / 2 + F_PX * v.x / depth, H / 2 - F_PX * v.y / depth, F_PX * distance * math.sin(alpha) / depth


def planet_layer(scene, out_dir, frames):
    """Render Jangala alone over a transparent film: every other object is a holdout, every
    volume (drive plumes, dust) is hidden, and the frame is cropped to the disc and its air.

    Two of these, one with --air-legacy, are the before and after of the planet in a finished
    frame: `planet_fix.py` adds their difference to the shipped frames, so ships and plumes
    in front of the planet keep the pixels they were rendered with.
    """
    for obj in scene.objects:
        if obj.name.startswith('Jangala |'):
            continue
        mats = [s.material for s in getattr(obj, 'material_slots', []) if s.material]
        volume = any(m.use_nodes and any(n.type == 'OUTPUT_MATERIAL' and n.inputs['Volume'].is_linked
                                         for n in m.node_tree.nodes) for m in mats)
        if volume or obj.get('thrust_plume'):
            obj.hide_render = True
        elif obj.type not in ('LIGHT', 'CAMERA'):
            obj.is_holdout = True
    for lc in bpy.context.view_layer.layer_collection.children:
        lc.holdout = True
    # The plume guard asks whether every thrusting craft shows its drive. In this layer no craft
    # shows anything (held out, plumes hidden on purpose): build() has already passed the guard
    # with the plumes on, so its render-time re-check is disarmed here and only here.
    import thrust_shots
    if thrust_shots._check_plumes in bpy.app.handlers.render_init:
        bpy.app.handlers.render_init.remove(thrust_shots._check_plumes)
    # The opaque legacy shell is wider than the true planet and hid the stars behind its rim, so
    # the corrected layer keeps the sky: (after - before) then puts those stars back. The legacy
    # layer is transparent, its alpha the footprint that difference is applied over.
    scene.render.film_transparent = AIR_LEGACY
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.image_settings.color_depth = '16'
    scene.render.use_border = True
    scene.render.use_crop_to_border = False
    margin = planet.AIR_RADIUS * 1.02
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    for f in frames:
        disc = planet_disc_px(f)
        if disc is None:
            continue
        cx, cy, r = disc
        r *= margin
        x0, x1 = max(0.0, cx - r - 8), min(float(W), cx + r + 8)
        y0, y1 = max(0.0, cy - r - 8), min(float(H), cy + r + 8)
        if x0 >= x1 or y0 >= y1:
            continue
        path = out_dir / ('%05d.png' % f)
        if path.exists() and path.stat().st_size > 0:
            continue
        scene.render.border_min_x, scene.render.border_max_x = x0 / W, x1 / W
        scene.render.border_min_y, scene.render.border_max_y = 1 - y1 / H, 1 - y0 / H
        scene.frame_set(f)
        scene.render.filepath = str(path)
        bpy.ops.render.render(write_still=True)
        print('C1V2 planet layer %05d' % f, flush=True)


def opts():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    o = {'animatic': '--animatic' in argv, 'final': '--final' in argv, 'save': '--save' in argv,
         'still': [], 'frames': None, 'samples': None, 'step': 1,
         'profile': os.environ.get('C1_PROFILE', 'production'), 'volume_step_rate': None,
         'air_legacy': '--air-legacy' in argv, 'planet_layer': None, 'frame_list': None}
    for i, a in enumerate(argv):
        if a == '--from':
            o['frames'] = (round(float(argv[i + 1]) * FPS), (o['frames'] or (0, FRAMES - 1))[1])
        if a == '--to':
            o['frames'] = ((o['frames'] or (0, 0))[0], min(FRAMES - 1, round(float(argv[i + 1]) * FPS)))
        if a == '--step':
            o['step'] = int(argv[i + 1])
        if a == '--samples':
            o['samples'] = int(argv[i + 1])
        if a == '--profile':
            o['profile'] = argv[i + 1]
        if a == '--volume-step-rate':
            o['volume_step_rate'] = float(argv[i + 1])
        if a == '--still':
            o['still'] = [float(x) for x in argv[i + 1].split(',')]
        if a == '--planet-layer':
            o['planet_layer'] = argv[i + 1]
        if a == '--frame-list':
            o['frame_list'] = [int(x) for x in argv[i + 1].split(',')]
        if a == '--frames':
            a0, a1 = argv[i + 1].split(':')
            o['frames'] = (int(a0), int(a1))
    if o['samples'] is None:
        o['samples'] = 64 if o['profile'] == 'cheap' else 128
    return o


def main():
    global AIR_LEGACY
    o = opts()
    AIR_LEGACY = o['air_legacy']
    scene = build(o['final'], o['samples'], o['profile'], o['volume_step_rate'])
    if o['save']:
        OUT.mkdir(parents=True, exist_ok=True)
        bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'c1v2-scene.blend'))
    for t in o['still']:
        scene.frame_set(round(t * FPS))
        scene.render.filepath = str(OUT / ('stills-final' if o['final'] else 'stills') / ('t%05.1f.png' % t))
        bpy.ops.render.render(write_still=True)
    if o['planet_layer']:
        a, b = o['frames'] or (0, FRAMES - 1)
        planet_layer(scene, o['planet_layer'], o['frame_list'] or range(a, b + 1))
    elif o['animatic']:
        a, b = o['frames'] or (0, FRAMES - 1)
        scene.frame_start, scene.frame_end, scene.frame_step = a, b, o['step']
        scene.render.filepath = str(OUT / 'animatic' / 'frames' / '#####')
        bpy.ops.render.render(animation=True)
    elif o['final'] and not o['still']:
        a, b = o['frames'] or (0, FRAMES - 1)
        scene.frame_start, scene.frame_end = a, b
        scene.render.filepath = str(FINAL_FRAMES / '#####')
        bpy.ops.render.render(animation=True)


if __name__ == '__main__':
    main()
