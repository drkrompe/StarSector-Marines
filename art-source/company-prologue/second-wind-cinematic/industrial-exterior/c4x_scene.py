"""C4 exterior: the camera in the sky over Greywater's industrial belt; Second Wind passes beneath.

    blender --background --factory-startup --python c4x_scene.py -- [--still t,...] [--frames a:b] [--step n] [--save]

While sketching, render one frame per second (--step 24, sixteen frames, about four minutes) and
play it as a slideshow; the full motion pass (--step 2) takes about twenty minutes.

Layout pass (EEVEE, block geometry). The camera starts about 820 m up, looking nearly straight
down, and pans toward the port: it starts over the farmland and crosses the edge of the
industrial belt (hedges and fence, freight road, rail sidings, warehouses, container yard) to
then, over the pan's last seconds, drifts east and comes down straight over Kilo Nine at about
480 m. It lingers there for eight seconds more, drifting north over the yard. Second Wind sinks from
its hover and touches down in a ring of dust, and the yard goes to work around it. The wide reveal
of the port is saved for C5's title pull-out. Second Wind is under the camera from the first second,
pulls ahead and sinks toward Kilo Nine, its shadow closing on it as it descends. The two speeds
keep the ground, the ship and the shadow all moving against each other.

How the port works, as the picture should explain it: the Hegemony's public cradles each face
the way their lane brings ships in (the painted centre line says which), with a service tower,
fuel, supply modules and steel stock beside each. Freighters on them unload from the stern;
carriers shuttle containers to the container yard, whose cranes load the rail and the haulers
out to the belt. Kilo Nine is not a public cradle: it is Nessa's private yard, fenced, on the
port's east edge against the jungle, with repair scaffold, a derrick over the salvage, a hangar
and the workshop the families will walk into, and the works that mend hulls at its north end.
Its one gate is on the port road, and a haul road runs from it up the yard. Her tug, bowser and
crew carrier wait clear of the downwash and move in once the dust thins, and a Hegemony customs
car comes over from the public side and in through her gate, lights going.

World frame, metres: +Y toward the port; +Z up; ground at z = 0.
Inputs: build/c1v2/inputs/{second-wind-patched,mudskipper-skinned,hound-skinned}.blend, and the
port's other hulls as `port_fleet.py` lists them.
Outputs under build/c4x/.
"""
import json
import math
import os
import random
import sys
from pathlib import Path

import bmesh
import bpy
from mathutils import Matrix, Quaternion, Vector

BASE = Path(__file__).resolve().parent
CINE = BASE.parent
REPO = BASE.parents[3]
INPUTS = REPO / 'build' / 'c1v2' / 'inputs'
OUT = REPO / 'build' / 'c4x'
STATES = CINE / 'second-wind-states' / 'patched'

FPS = 24
# The approach is LATE seconds slower than the first cut (owner, 2026-10-19: Castle names Kilo Nine
# while it is still ahead, not as the ship is already coming down). Everything from the hover on
# moves later by the same amount.
LATE = 5.4
PAN = 8.0 + LATE                        # the pan; then the camera lingers over the landing
DURATION = 16.0 + LATE
FRAMES = round(DURATION * FPS)
W, H = 1920, 1080
LENS_MM, SENSOR_MM = 18.0, 36.0
CAM_POS = Vector((0.0, 150.0, 820.0))   # where haze distances are measured from (mid-pan)
CAM_Y0 = -400.0                         # pan along +Y from over the fields...
CAM_END = Vector((400.0, 465.0, 480.0))  # ...and down to straight over Kilo Nine by the end of the pan
CAM_LINGER = Vector((395.0, 535.0, 445.0))  # then drifting north over the yard while the ship settles
CAM_PITCH = math.radians(-85.0)          # nearly straight down, a touch toward the port
SUN_DIR = Vector((-0.45, -0.20, 0.87)).normalized()   # toward the sun: high, left, behind
SUN_RGB = (1.0, 0.95, 0.88)
HAZE_RGB = (0.72, 0.80, 0.88)
HAZE_STRENGTH = 0.35
HAZE_DIST = 9000.0                       # metres for 1/e of a surface's own contrast

rng = random.Random(7)


# ---------------------------------------------------------------- scene, sky, haze

def fresh_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.resolution_x, scene.render.resolution_y = W, H
    # Sketch passes (timing, layout) may render smaller to spare the shared GPU: CINE_SCALE=50 renders
    # 960x540. Anything shown to the owner is brought back to 1920x1080 (`c5_title`, the join).
    scene.render.resolution_percentage = int(os.environ.get('CINE_SCALE', '100'))
    scene.render.fps = FPS
    scene.frame_start, scene.frame_end = 0, FRAMES - 1
    return scene


def add_sky(scene):
    """Gradient sky by view direction: pale gold at the horizon, deepening upward, with a broad
    glow and a tighter bloom around the sun just out of frame."""
    world = bpy.data.worlds.new('C4x | backlit sky')
    scene.world = world
    world.use_nodes = True
    nt = world.node_tree
    bg = nt.nodes['Background']
    coord = nt.nodes.new('ShaderNodeTexCoord')
    norm = nt.nodes.new('ShaderNodeVectorMath')
    norm.operation = 'NORMALIZE'
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    height = nt.nodes.new('ShaderNodeValToRGB')
    el = height.color_ramp.elements
    el[0].position, el[0].color = 0.0, (*HAZE_RGB, 1)
    el[1].position, el[1].color = 0.10, (0.30, 0.45, 0.68, 1)
    dot = nt.nodes.new('ShaderNodeVectorMath')
    dot.operation = 'DOT_PRODUCT'
    dot.inputs[1].default_value = SUN_DIR
    glow = nt.nodes.new('ShaderNodeMath')
    glow.operation = 'POWER'
    glow.inputs[1].default_value = 350.0
    bloom = nt.nodes.new('ShaderNodeMath')
    bloom.operation = 'POWER'
    bloom.inputs[1].default_value = 4000.0
    total = nt.nodes.new('ShaderNodeMath')
    total.operation = 'MULTIPLY_ADD'
    total.inputs[1].default_value = 5.0
    tint = nt.nodes.new('ShaderNodeMix')
    tint.data_type = 'RGBA'
    tint.blend_type = 'ADD'
    tint.inputs['B'].default_value = (*SUN_RGB, 1)
    clamp = nt.nodes.new('ShaderNodeClamp')
    clamp.inputs['Max'].default_value = 4.0
    nt.links.new(coord.outputs['Generated'], norm.inputs[0])
    nt.links.new(norm.outputs[0], sep.inputs[0])
    nt.links.new(sep.outputs['Z'], height.inputs['Fac'])
    nt.links.new(norm.outputs[0], dot.inputs[0])
    nt.links.new(dot.outputs['Value'], glow.inputs[0])
    nt.links.new(dot.outputs['Value'], bloom.inputs[0])
    nt.links.new(bloom.outputs[0], total.inputs[0])
    nt.links.new(glow.outputs[0], total.inputs[2])
    nt.links.new(total.outputs[0], clamp.inputs['Value'])
    nt.links.new(clamp.outputs[0], tint.inputs['Factor'])
    nt.links.new(height.outputs['Color'], tint.inputs['A'])
    nt.links.new(tint.outputs['Result'], bg.inputs['Color'])
    bg.inputs['Strength'].default_value = 1.0


def add_haze(skip=()):
    """Aerial perspective for a fixed camera: mix each surface toward the backlit haze by its
    distance from the lens."""
    for m in bpy.data.materials:
        if not m.use_nodes or m.name in skip:
            continue
        nt = m.node_tree
        out = next((n for n in nt.nodes if n.type == 'OUTPUT_MATERIAL' and n.is_active_output), None)
        if out is None or not out.inputs['Surface'].links:
            continue
        src = out.inputs['Surface'].links[0].from_socket
        geo = nt.nodes.new('ShaderNodeNewGeometry')
        dist = nt.nodes.new('ShaderNodeVectorMath')
        dist.operation = 'DISTANCE'
        dist.inputs[1].default_value = CAM_POS
        fac = nt.nodes.new('ShaderNodeMath')
        fac.operation = 'DIVIDE'
        fac.inputs[1].default_value = -HAZE_DIST
        expo = nt.nodes.new('ShaderNodeMath')
        expo.operation = 'EXPONENT'
        inv = nt.nodes.new('ShaderNodeMath')
        inv.operation = 'SUBTRACT'
        inv.inputs[0].default_value = 1.0
        emit = nt.nodes.new('ShaderNodeEmission')
        emit.inputs['Color'].default_value = (*HAZE_RGB, 1)
        emit.inputs['Strength'].default_value = HAZE_STRENGTH
        mix = nt.nodes.new('ShaderNodeMixShader')
        nt.links.new(geo.outputs['Position'], dist.inputs[0])
        nt.links.new(dist.outputs['Value'], fac.inputs[0])
        nt.links.new(fac.outputs[0], expo.inputs[0])
        nt.links.new(expo.outputs[0], inv.inputs[1])
        nt.links.new(inv.outputs[0], mix.inputs['Fac'])
        nt.links.new(src, mix.inputs[1])
        nt.links.new(emit.outputs[0], mix.inputs[2])
        nt.links.new(mix.outputs[0], out.inputs['Surface'])


def material(name, rgb, rough=0.8, metal=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    p = m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*rgb, 1)
    p.inputs['Roughness'].default_value = rough
    p.inputs['Metallic'].default_value = metal
    return m


# ---------------------------------------------------------------- geometry helpers

def link(obj):
    bpy.context.scene.collection.objects.link(obj)
    return obj


def mesh(name, bm, mat):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(mat)
    return link(bpy.data.objects.new(name, me))


def box(bm, size, centre, rot_z=0.0):
    verts = bmesh.ops.create_cube(bm, size=1.0)['verts']
    m = Matrix.Translation(centre) @ Matrix.Rotation(rot_z, 4, 'Z') @ Matrix.Diagonal((*size, 1.0))
    bmesh.ops.transform(bm, matrix=m, verts=verts)


def cylinder(bm, radius, height, base, segments=16, top_radius=None):
    r = bmesh.ops.create_cone(bm, cap_ends=True, segments=segments, radius1=radius,
                              radius2=radius if top_radius is None else top_radius, depth=height)
    bmesh.ops.translate(bm, vec=Vector(base) + Vector((0, 0, height / 2)), verts=r['verts'])


def strut(bm, a, b, thick):
    a, b = Vector(a), Vector(b)
    d = b - a
    verts = bmesh.ops.create_cube(bm, size=1.0)['verts']
    rot = d.to_track_quat('Z', 'Y').to_matrix().to_4x4()
    m = Matrix.Translation((a + b) / 2) @ rot @ Matrix.Diagonal((thick, thick, d.length, 1.0))
    bmesh.ops.transform(bm, matrix=m, verts=verts)


def gantry_crane(bm, x, y, h, span):
    """A container gantry straddling the yard: four legs, a portal beam and a trolley."""
    for dx in (-span / 2, span / 2):
        for dy in (-8, 8):
            strut(bm, (x + dx, y + dy, 0), (x + dx, y + dy, h), 1.4)
        strut(bm, (x + dx, y - 8, h * 0.5), (x + dx, y + 8, h * 0.5), 0.8)
    for dy in (-8, 8):
        box(bm, (span + 6, 2.0, 2.4), (x, y + dy, h))
    box(bm, (6, 18, 3), (x + span * 0.2, y, h + 1.5))


def stack(bm, x, y, h, r):
    cylinder(bm, r, h, (x, y, 0), 14, r * 0.8)
    box(bm, (r * 2.6, r * 2.6, 1.2), (x, y, h * 0.7))


STACKS = []       # (x, y, top height) for the steam plumes
WAREHOUSES = []   # (x, y, w, d, h) footprints, for roof detail and loading docks
CROSS_STREETS = (-300.0, 0.0, 300.0)   # north-south streets from the freight road through to the port


def plane(name, x0, x1, y0, y1, mat, z=0.0):
    bm = bmesh.new()
    bm.faces.new([bm.verts.new(c) for c in ((x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z))])
    return mesh(name, bm, mat)


def add_farmland():
    """Fields up to the belt's edge: parcels with rows in different directions, hedgerows,
    farm tracks, tree clumps, then a ditch and the belt's perimeter fence."""
    import random as _r
    r = _r.Random(12)
    crops = [((0.20, 0.34, 0.10), (0.30, 0.26, 0.15)), ((0.30, 0.38, 0.12), (0.36, 0.31, 0.18)),
             ((0.42, 0.40, 0.16), (0.38, 0.32, 0.20)), ((0.16, 0.28, 0.09), (0.24, 0.22, 0.13))]
    mats = []
    for i, (a, b) in enumerate(crops):
        for axis in ('X', 'Y'):
            m = material('Crop %d%s' % (i, axis), a, 0.9)
            nt = m.node_tree
            coord = nt.nodes.new('ShaderNodeTexCoord')
            wave = nt.nodes.new('ShaderNodeTexWave')
            wave.bands_direction = axis
            wave.inputs['Scale'].default_value = 0.09     # rows about 11 m apart: they read from 700 m
            mix = nt.nodes.new('ShaderNodeMix')
            mix.data_type = 'RGBA'
            mix.inputs['A'].default_value = (*a, 1)
            mix.inputs['B'].default_value = (*b, 1)
            nt.links.new(coord.outputs['Object'], wave.inputs['Vector'])
            rows = nt.nodes.new('ShaderNodeMath')                # crisp crop rows over bare soil
            rows.operation = 'GREATER_THAN'
            rows.inputs[1].default_value = 0.62
            nt.links.new(wave.outputs['Fac'], rows.inputs[0])
            nt.links.new(rows.outputs[0], mix.inputs['Factor'])
            nt.links.new(mix.outputs['Result'], nt.nodes['Principled BSDF'].inputs['Base Color'])
            mats.append(m)
    hedge = material('Hedgerow', (0.07, 0.14, 0.05), 0.9)
    track = material('Farm track', (0.48, 0.42, 0.32), 0.95)
    hb, tb = bmesh.new(), bmesh.new()
    xs = [-1100, -780, -520, -230, 60, 330, 610, 900, 1200]
    ys = [-1500, -1180, -900, -640, -400, -180]
    for (x0, x1) in zip(xs, xs[1:]):
        for (y0, y1) in zip(ys, ys[1:]):
            plane('Field %d %d' % (x0, y0), x0 + 4, x1 - 4, y0 + 4, y1 - 4, r.choice(mats), 0.03)
            if r.random() < 0.6:
                box(hb, (x1 - x0, 5, 4), ((x0 + x1) / 2, y1, 2))
            if r.random() < 0.4:
                box(hb, (5, y1 - y0, 4), (x1, (y0 + y1) / 2, 2))
    for x in (-230, 610):
        box(tb, (7, 1320, 0.1), (x, -840, 0.08))
    box(tb, (2400, 7, 0.1), (0, -640, 0.08))
    trees = material('Tree crowns', (0.08, 0.17, 0.06), 0.9)
    bt = bmesh.new()
    for _ in range(70):
        cx, cy = r.uniform(-1100, 1100), r.uniform(-1450, -200)
        for k in range(r.randrange(2, 6)):
            rad = r.uniform(4, 8)
            loc = (cx + r.uniform(-12, 12), cy + r.uniform(-12, 12), rad + 2)
            res = bmesh.ops.create_icosphere(bt, subdivisions=1, radius=rad)
            bmesh.ops.translate(bt, vec=loc, verts=res['verts'])
    mesh('Hedgerows', hb, hedge)
    mesh('Farm tracks', tb, track)
    mesh('Tree clumps', bt, trees)
    # The edge: a ditch, a strip of rough ground and the belt's perimeter fence on posts.
    plane('Drainage ditch', -2000, 2000, -170, -160, material('Ditch water', (0.10, 0.14, 0.13), 0.6), 0.04)
    plane('Rough verge', -2000, 2000, -160, -100, material('Rough verge', (0.32, 0.30, 0.20), 0.95), 0.03)
    fb = bmesh.new()
    for x in range(-1500, 1501, 12):
        box(fb, (0.3, 0.3, 3), (x, -110, 1.5))
    box(fb, (3000, 0.15, 0.15), (0, -110, 2.8))
    mesh('Perimeter fence', fb, material('Fence steel', (0.25, 0.25, 0.25), 0.6, 0.5))


def add_yard():
    """The industrial belt from above, in bands toward the port (+Y)."""
    plane('Ground', -2000, 2000, -1500, 2500, material('Yard dirt', (0.30, 0.27, 0.22), 0.95))
    asphalt = material('Asphalt', (0.12, 0.12, 0.13), 0.9)
    concrete = material('Concrete', (0.40, 0.39, 0.36), 0.9)
    ballast = material('Ballast', (0.20, 0.18, 0.17), 0.95)
    plane('Freight road', -2000, 2000, -95, -75, asphalt, 0.05)
    for y in (-40, -25, -10):
        plane('Siding %d' % y, -2000, 2000, y - 2, y + 2, ballast, 0.05)
    plane('Container yard', -600, 600, 215, 335, concrete, 0.05)
    plane('Loading strip', -600, 600, 336, 358, asphalt, 0.07)
    plane('Port apron', -2000, 2000, 360, 2500, material('Apron', (0.18, 0.18, 0.17), 0.9), 0.04)
    for x in CROSS_STREETS:
        plane('Cross street %d' % x, x - 7, x + 7, -75, 360, asphalt, 0.06)
    roofs = [material('Roof rust', (0.42, 0.22, 0.12), 0.7, 0.3),
             material('Roof grey', (0.38, 0.40, 0.42), 0.5, 0.5),
             material('Roof white', (0.70, 0.70, 0.66), 0.6),
             material('Roof green', (0.20, 0.30, 0.24), 0.6)]
    steel = material('Steel', (0.16, 0.16, 0.17), 0.5, 0.6)
    tanks = material('Tank white', (0.78, 0.78, 0.74), 0.4, 0.2)
    crane = material('Crane yellow', (0.82, 0.60, 0.10), 0.5)
    bms = [bmesh.new() for _ in roofs]
    # Warehouses between the sidings and the container yard, broken by the cross streets.
    for y0, y1 in ((10, 90), (110, 200)):
        for xa, xb in ((-900, -310), (-290, -10), (10, 290), (310, 900)):
            x = xa
            while x < xb - 20:
                w = min(rng.uniform(40, 95), xb - x)
                h = rng.uniform(8, 20)
                box(bms[rng.randrange(len(roofs))], (w - 4, y1 - y0 - 6, h), (x + w / 2, (y0 + y1) / 2, h / 2))
                WAREHOUSES.append((x + w / 2, (y0 + y1) / 2, w - 4, y1 - y0 - 6, h))
                x += w
    for bm, m in zip(bms, roofs):
        mesh('Warehouses ' + m.name, bm, m)
    # Containers stacked in rows, cranes over them.
    colours = [(0.62, 0.18, 0.10), (0.10, 0.28, 0.50), (0.15, 0.40, 0.20), (0.75, 0.55, 0.12),
               (0.55, 0.55, 0.52), (0.40, 0.12, 0.30)]
    cms = [bmesh.new() for _ in colours]
    aisles = (-395.0, -305.0, -215.0, -125.0, -35.0)      # where the berths' cargo lanes arrive
    for a in aisles:
        plane('Yard aisle %d' % a, a - 8, a + 8, 215, 336, asphalt, 0.06)
    for row in range(6):
        y = 225 + row * 18
        x = -560
        while x < 560:
            if rng.random() < 0.85 and all(abs(x + 6 - a) > 13 for a in aisles):
                # The cross streets run on through the yard to the loading strip: nothing stands on
                # them. A slot there still draws its numbers, so every other stack stays as it was.
                on_street = any(abs(x + 6 - s) < 14 for s in CROSS_STREETS)
                for level in range(rng.randrange(1, 4)):
                    c = cms[rng.randrange(len(colours))]
                    pos = (x + 6, y + rng.choice((-2, 2)), 1.3 + 2.6 * level)
                    if not on_street:
                        box(c, (12, 2.4, 2.6), pos)
            x += 12.6
    for i, (bm, c) in enumerate(zip(cms, colours)):
        mesh('Containers %d' % i, bm, material('Container %d' % i, c, 0.6, 0.2))
    bm = bmesh.new()
    for x in (-380, -120, 150, 410):
        gantry_crane(bm, x, 280, 34, 44)
    mesh('Yard cranes', bm, crane)
    # Tank farm and plant at the west end; stacks.
    bm, bt, sk = bmesh.new(), bmesh.new(), bmesh.new()
    for i in range(3):
        for j in range(2):
            cylinder(bt, 11, 16, (-760 + i * 28, 40 + j * 28, 0), 24)
    for x, y, h in ((-640, 150, 70), (520, 60, 85)):
        stack(sk, x, y, h, 4)
        STACKS.append((x, y, h))
    box(bm, (30, 18, 42), (-660, 120, 21))
    mesh('Plant', bm, steel)
    mesh('Stacks', sk, concrete)
    mesh('Tank farm', bt, tanks)


def vehicle(name, parts, keys, heading_keys=None):
    """A small vehicle under one root, keyed (frame, x, y). A part is (size, centre, material), a box,
    or (('tube', radius, length), centre, material), a tank lying along the vehicle. Parts sharing a
    material become one mesh."""
    root = link(bpy.data.objects.new(name, None))
    by_mat = {}
    for size, centre, mat in parts:
        bm = by_mat.setdefault(mat.name, (bmesh.new(), mat))[0]
        if size[0] == 'tube':
            r = bmesh.ops.create_cone(bm, cap_ends=True, segments=16, radius1=size[1], radius2=size[1],
                                      depth=size[2])
            m = Matrix.Translation(centre) @ Matrix.Rotation(math.pi / 2, 4, 'Y')
            bmesh.ops.transform(bm, matrix=m, verts=r['verts'])
        else:
            box(bm, size, centre)
    for i, (bm, mat) in enumerate(by_mat.values()):
        mesh('%s %d' % (name, i), bm, mat).parent = root
    for f, x, y in keys:
        root.location = (x, y, 0)
        root.keyframe_insert('location', frame=f)
    for f, h in (heading_keys or [(0, 0.0)]):
        root.rotation_euler = (0, 0, h)
        root.keyframe_insert('rotation_euler', frame=f)
    return root


def tyre():
    return bpy.data.materials.get('Tyre') or material('Tyre', (0.03, 0.03, 0.03), 0.9)


def wheels(xs, half_track, r=0.55, w=0.45):
    """Wheel boxes at each x, both sides, inside the body's width."""
    return [((r * 2, w, r * 2), (x, side * half_track, r), tyre()) for x in xs for side in (-1, 1)]


# Rough shapes for the yard's vehicles, front toward +x. Each keeps the footprint its painted roof
# was measured on (`vehicle_paint.py`), and its blocks stand where the painting put the cab, tank,
# load or windscreens.

def tug_parts(paint):
    return [((8, 4, 0.9), (0, 0, 0.95), paint),                    # chassis and deck
            ((5.6, 3.6, 0.5), (-1.2, 0, 1.65), paint),              # engine covers
            ((2.2, 2.45, 1.3), (2.3, -0.15, 2.05), paint)           # the operator's cab
            ] + wheels((-2.6, 2.6), 1.6, 0.6, 0.6)


def bowser_parts(paint):
    return [((12.5, 2.2, 0.4), (0.25, 0, 0.95), paint),             # chassis rails
            (('tube', 1.35, 9.0), (-1.5, 0, 2.5), paint),           # the tank
            ((3.0, 3.0, 2.4), (5.0, 0, 2.2), paint)                 # cab
            ] + wheels((-4.6, -3.4, 0.6, 5.4), 1.25, 0.5, 0.45)


def carrier_parts(paint):
    return [((10, 3, 2.5), (0, 0, 1.9), paint),                     # the bus body
            ((9.4, 2.7, 0.3), (-0.2, 0, 3.3), paint)                # roof
            ] + wheels((-3.4, 3.3), 1.3, 0.55, 0.4)


def car_parts(paint):
    return [((5.2, 2.3, 0.85), (0, 0, 0.8), paint),                 # lower body
            ((4.6, 2.1, 0.55), (0.0, 0, 1.5), paint)                # cabin: glass at both ends, roof between
            ] + wheels((-1.6, 1.6), 0.95, 0.38, 0.3)


def flatbed_parts(cab, bed, load):
    return [((11.7, 3.2, 0.5), (-1.15, 0, 1.25), bed),              # the flatbed deck
            ((8.7, 2.9, 2.4), (-1.2, 0, 2.7), load),                # a hull section lashed on
            ((3.7, 3.0, 1.6), (6.85, 0, 1.5), cab),                 # cab, lower
            ((2.8, 2.8, 1.5), (6.6, 0, 3.0), cab)                   # cab, upper
            ] + wheels((-5.4, -4.2, 2.6, 7.6), 1.3, 0.55, 0.5)


def forklift_parts(paint, module):
    return [((2.3, 1.9, 1.1), (-0.45, 0.05, 0.95), paint),          # body and counterweight
            ((1.5, 1.6, 0.12), (-0.2, 0.05, 2.4), paint),           # overhead guard
            ((0.25, 1.4, 3.0), (0.7, 0.05, 1.7), paint),            # mast
            ((3.8, 4, 2.4), (2.7, 0, 1.9), module)                  # the module on the forks
            ] + wheels((-1.0, 0.2), 0.75, 0.32, 0.3)


def add_detail():
    """What reads from 700 m up: roof plant, docks, pylons and their shadows, working vehicles,
    painted markings."""
    r = random.Random(21)
    pale = material('Roof skylights', (0.78, 0.80, 0.80), 0.3, 0.1)
    plant = material('Roof plant', (0.30, 0.31, 0.32), 0.5, 0.5)
    sb, pb = bmesh.new(), bmesh.new()
    for x, y, w, d, h in WAREHOUSES:
        if r.random() < 0.6:                                         # skylight strips
            for k in range(int(w // 14)):
                box(sb, (3, d * 0.8, 0.4), (x - w / 2 + 7 + k * 14, y, h + 0.2))
        for _ in range(r.randrange(1, 5)):                            # roof units and vents
            box(pb, (r.uniform(3, 8), r.uniform(3, 6), r.uniform(1.5, 3)),
                (x + r.uniform(-w / 2 + 5, w / 2 - 5), y + r.uniform(-d / 2 + 4, d / 2 - 4), h + 1))
    mesh('Roof skylights', sb, pale)
    mesh('Roof plant', pb, plant)
    # Trailers backed up to loading docks on the road side of the first warehouse row.
    trailer = material('Dock trailers', (0.72, 0.72, 0.68), 0.6)
    tb = bmesh.new()
    for x, y, w, d, h in WAREHOUSES:
        if y < 100 and r.random() < 0.7:
            for k in range(r.randrange(1, 4)):
                box(tb, (3, 13, 3.2), (x - w / 2 + 8 + k * 6, y - d / 2 - 7, 1.9))
    mesh('Dock trailers', tb, trailer)
    # The transmission line crossing the fields into the belt, conductors sagging between towers.
    steel = bpy.data.materials['Steel']
    lb = bmesh.new()
    towers = [(-640 + 0.18 * (y + 1500), y) for y in range(-1500, 60, 230)]
    for x, y in towers:
        for cx, cy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            strut(lb, (x + cx * 6, y + cy * 6, 0), (x + cx * 1.5, y + cy * 1.5, 42), 0.8)
        strut(lb, (x - 14, y, 34), (x + 14, y, 34), 1.0)
        strut(lb, (x - 9, y, 42), (x + 9, y, 42), 1.0)
    for dx, z in ((-13, 33), (13, 33), (-8, 41), (8, 41)):
        for (xa, ya), (xb, yb) in zip(towers, towers[1:]):
            for j in range(8):
                u0, u1 = j / 8, (j + 1) / 8
                strut(lb, (xa + (xb - xa) * u0 + dx, ya + (yb - ya) * u0, z - 6 * 4 * u0 * (1 - u0)),
                      (xa + (xb - xa) * u1 + dx, ya + (yb - ya) * u1, z - 6 * 4 * u1 * (1 - u1)), 0.35)
    mesh('Transmission line', lb, steel)
    material('Truck cream', (0.80, 0.76, 0.62), 0.5)      # the crew carrier's; the farm machines are c4_farm's
    # The gate: a hauler comes up the farm track and waits at the perimeter checkpoint.
    orange = bpy.data.materials['Hauler cab']
    white = bpy.data.materials['Hauler load']
    gate = material('Gate booth', (0.60, 0.58, 0.52), 0.6)
    bm = bmesh.new()
    box(bm, (5, 4, 3.5), (-242, -118, 1.75))
    box(bm, (10, 0.5, 0.5), (-232, -112, 1.2))          # barrier arm, down
    mesh('Checkpoint', bm, gate)
    plane('Gate apron', -240, -220, -160, -95, bpy.data.materials['Asphalt'], 0.07)
    vehicle('Gate hauler', [((3.4, 3.0, 3.4), (0, 0, 2.1), orange), ((12.5, 3.0, 3.2), (-9, 0, 2.4), white),
                            ((12.5, 3.0, 3.2), (-22.5, 0, 2.4), white)],
            [(0, -230, -560), (round(5.5 * FPS), -230, -128), (FRAMES - 1, -230, -128)],
            [(0, math.pi / 2)])


def add_road_traffic():
    cab = material('Hauler cab', (0.78, 0.34, 0.10), 0.5)
    load = material('Hauler load', (0.80, 0.80, 0.76), 0.6)
    wagon = material('Rail wagon', (0.35, 0.20, 0.12), 0.7)
    for i, (x0, v, lane) in enumerate(((-420, 20, -90), (-470, 20, -90), (300, -16, -80),
                                         (-120, 18, -90), (120, -15, -80))):
        root = link(bpy.data.objects.new('Hauler %d' % i, None))
        root.rotation_euler = (0, 0, 0.0 if v > 0 else math.pi)
        for f in (0, FRAMES - 1):
            root.location = (x0 + v * f / FPS, lane, 0)
            root.keyframe_insert('location', frame=f)
        bm = bmesh.new()
        box(bm, (3.4, 3.0, 3.4), (0, 0, 2.1))
        mesh('Hauler %d cab' % i, bm, cab).parent = root
        bm = bmesh.new()
        for k in range(2):
            box(bm, (12.5, 3.0, 3.2), (-9 - k * 13.5, 0, 2.4))
        mesh('Hauler %d load' % i, bm, load).parent = root
    # A freight train creeping along the middle siding.
    train = link(bpy.data.objects.new('Freight train', None))
    for f in (0, FRAMES - 1):
        train.location = (-300 + 6.0 * f / FPS, -25, 0)
        train.keyframe_insert('location', frame=f)
    bm = bmesh.new()
    for k in range(18):
        box(bm, (15, 3.0, 3.6), (-k * 16, 0, 2.2))
    mesh('Freight wagons', bm, wagon).parent = train


# ---------------------------------------------------------------- steam

def add_steam():
    """Soft, slowly rising plumes over the stacks: a camera-facing card each, backlit."""
    m = bpy.data.materials.new('Steam')
    m.use_nodes = True
    m.surface_render_method = 'BLENDED'
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (0.95, 0.90, 0.84, 1)
    p.inputs['Emission Color'].default_value = (*SUN_RGB, 1)
    p.inputs['Emission Strength'].default_value = 1.4           # forward-scattered sun
    coord = nt.nodes.new('ShaderNodeTexCoord')
    noise = nt.nodes.new('ShaderNodeTexNoise')
    noise.noise_dimensions = '4D'
    noise.inputs['Scale'].default_value = 3.5
    noise.inputs['Detail'].default_value = 6.0
    d = noise.inputs['W'].driver_add('default_value').driver
    d.type = 'SCRIPTED'
    d.expression = 'frame / 60'
    shape = nt.nodes.new('ShaderNodeMapRange')
    shape.inputs['From Min'].default_value = 0.36
    shape.inputs['From Max'].default_value = 0.68
    # Fade to the sides (|u - 0.5|) and up the plume (v): a column that thins as it rises.
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    side = nt.nodes.new('ShaderNodeMath')
    side.operation = 'PINGPONG'
    side.inputs[1].default_value = 0.5
    side_r = nt.nodes.new('ShaderNodeMapRange')
    side_r.inputs['From Min'].default_value = 0.0
    side_r.inputs['From Max'].default_value = 0.45
    rise = nt.nodes.new('ShaderNodeMapRange')
    rise.inputs['From Min'].default_value = 1.0
    rise.inputs['From Max'].default_value = 0.1
    a1 = nt.nodes.new('ShaderNodeMath')
    a1.operation = 'MULTIPLY'
    a2 = nt.nodes.new('ShaderNodeMath')
    a2.operation = 'MULTIPLY'
    a3 = nt.nodes.new('ShaderNodeMath')
    a3.operation = 'MULTIPLY'
    a3.inputs[1].default_value = 1.0
    nt.links.new(coord.outputs['UV'], noise.inputs['Vector'])
    nt.links.new(coord.outputs['UV'], sep.inputs[0])
    nt.links.new(noise.outputs['Fac'], shape.inputs['Value'])
    nt.links.new(sep.outputs['X'], side.inputs[0])
    nt.links.new(side.outputs[0], side_r.inputs['Value'])
    nt.links.new(sep.outputs['Y'], rise.inputs['Value'])
    nt.links.new(shape.outputs['Result'], a1.inputs[0])
    nt.links.new(side_r.outputs['Result'], a1.inputs[1])
    nt.links.new(a1.outputs[0], a2.inputs[0])
    nt.links.new(rise.outputs['Result'], a2.inputs[1])
    nt.links.new(a2.outputs[0], a3.inputs[0])
    nt.links.new(a3.outputs[0], p.inputs['Alpha'])
    for i, (x, y, h) in enumerate(STACKS):
        bm = bmesh.new()
        uv = bm.loops.layers.uv.new()
        w, tall = h * 0.9, h * 2.2
        # Leaning downwind (+x) as it rises.
        f = bm.faces.new([bm.verts.new(c) for c in ((-w * 0.15, 0, 0), (w * 0.15, 0, 0),
                                                     (w * 0.9, 0, tall), (-w * 0.1, 0, tall))])
        for loop, co in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
            loop[uv].uv = co
        ob = mesh(f'Steam {i}', bm, m)
        ob.location = (x, y, h - 2)
        ob.rotation_euler = (math.radians(-80), 0, 0)    # laid back toward the camera above


# ---------------------------------------------------------------- hulls

def append_collection(path, name):
    with bpy.data.libraries.load(str(path), link=False) as (src, dst):
        dst.collections = [name]
    coll = dst.collections[0]
    bpy.context.scene.collection.children.link(coll)
    return coll


def attitude(heading, pitch, roll):
    q = (Quaternion((0, 0, 1), heading) @ Quaternion((0, 1, 0), -pitch) @ Quaternion((1, 0, 0), roll))
    return q.to_euler('XYZ')


def smoothstep(a, b, x):
    x = min(1.0, max(0.0, (x - a) / (b - a)))
    return x * x * (3 - 2 * x)


# In under the camera from the lower left, crossing toward the port and sinking: about 330 m up
# at the start, 70 m at the end, slowing as it goes.
P0 = Vector((-220.0, -470.0, 330.0))   # in frame, lower left, from the first second
# Second Wind's hull runs 27 m aft and 39 m forward of its root and hangs 7.9 m below it, so the
# root sits 5.9 m aft of the pad centre (centring the hull in the ring) and 8.3 m up at rest, on
# Kilo Nine's low slab (K9_SLAB).
HULL_FWD = 5.9
K9_SLAB = 0.4                         # a private yard's pad: a low slab people and vehicles walk onto
REST_Z = K9_SLAB + 7.9
ARRIVE = 6.6 + LATE                   # seconds: braked to a hover over the pad
HOVER_Z = 60.0
T_DOWN = 11.0 + LATE                  # touchdown; then the drive spools down and the yard goes to work


def landing_point():
    h = math.atan2(465.0 - P0.y, 400.0 - P0.x)       # the pad's line, as k9_heading
    return Vector((400.0 - HULL_FWD * math.cos(h), 465.0 - HULL_FWD * math.sin(h), REST_Z))


P1 = landing_point()


def ship_path(t):
    k = min(1.0, t / ARRIVE)
    p = P0.lerp(P1, 1 - (1 - k) ** 2)                  # braking into the hover
    if t < ARRIVE:
        p.z = P0.z + (HOVER_Z - P0.z) * smoothstep(0.0, 1.0, k)   # holds height, dives, levels off
    else:
        p.z = HOVER_Z + (REST_Z - HOVER_Z) * smoothstep(ARRIVE, T_DOWN, t)
    return p


def add_ship():
    manifest = json.loads((STATES / 'manifest.json').read_text())
    append_collection(INPUTS / 'second-wind-patched.blend', manifest['append_collection'])
    root = bpy.data.objects[manifest['root']]
    # The exhaust anchors are the root's children in a collection that never reaches the scene, so
    # they are not moved with the ship. Read each one's place relative to the root and hang the
    # drive glows on the root itself.
    root.scale = (manifest['metres_per_unit'],) * 3
    root.rotation_mode = 'XYZ'
    glow = material('Drive plume', (0.6, 0.8, 1.0))
    glow.node_tree.nodes['Principled BSDF'].inputs['Emission Color'].default_value = (0.6, 0.8, 1.0, 1)
    glow.node_tree.nodes['Principled BSDF'].inputs['Emission Strength'].default_value = 25.0
    for i in manifest['engines_running']:
        anchor = bpy.data.objects.get('Engine %d exhaust anchor' % i)
        if anchor is None:
            continue
        local = anchor.matrix_parent_inverse @ anchor.matrix_basis
        light = bpy.data.objects.new('Drive %d glow' % i, bpy.data.lights.new('Drive %d glow' % i, 'POINT'))
        light.data.energy = 6.0e5
        light.data.color = (0.55, 0.75, 1.0)
        light.parent = root
        light.matrix_basis = local @ Matrix.Translation((3.0, 0, 0))
        link(light)
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=8, radius=1.0)
        core = mesh('Drive %d core' % i, bm, glow)
        core.parent = root
        core.matrix_basis = local @ Matrix.Translation((2.5, 0, 0)) @ Matrix.Diagonal((5.0, 2.2, 2.2, 1.0))
        full = core.scale.copy()
        for t, e in ((T_DOWN, 1.0), (T_DOWN + 1.5, 0.0)):           # spooling down after touchdown
            f = round(t * FPS)
            light.data.energy = 6.0e5 * e
            light.data.keyframe_insert('energy', frame=f)
            core.scale = full * max(e, 0.001)
            core.keyframe_insert('scale', frame=f)
    dt = 1.0 / FPS
    for f in range(FRAMES):
        t = f / FPS
        p = ship_path(t)
        v = ship_path(min(DURATION, t + dt)) - ship_path(max(0.0, t - dt))
        if v.xy.length > 0.5:                                  # hovering: keep the last heading
            course = math.atan2(v.y, v.x)
        descent = math.atan2(v.z, max(v.xy.length, 30.0))
        pitch = 0.45 * descent + math.radians(1.0) * math.sin(2 * math.pi * t / 5.0)
        roll = math.radians(-6.0) + math.radians(2.0) * math.sin(2 * math.pi * t / 7.0)
        level = smoothstep(ARRIVE - 1.5, T_DOWN - 1.0, t)        # level by the time the legs touch
        heading = course + (k9_heading() - course) * level        # square on the pad's line
        pitch *= 1.0 - level
        roll *= 1.0 - level
        root.location = p
        root.rotation_euler = attitude(heading, pitch, roll)
        root.keyframe_insert('location', frame=f)
        root.keyframe_insert('rotation_euler', frame=f)
    return manifest


# ---------------------------------------------------------------- the port

# The public port, laid out in rows by size. Capital ships and cruisers never land; their cargo
# comes down in lighters. Every berth in a row faces the same way, turned to the approach
# corridor, and ships settle a few degrees off their markings.
ROW_HEADING = math.radians(98.0)
# Freighter berths: (x, settle offset in degrees, occupant). Sterns face the container yard. The
# east berth is the passenger ferry's, beside the arrivals hall.
BERTH_Y, BERTH_R = 530.0, 34.0
BERTH_DY = {-400.0: 12.0, -310.0: -8.0, -220.0: 0.0, -130.0: 15.0, -40.0: -10.0, 50.0: 5.0, 140.0: 0.0}
BERTHS = [(-400.0, 9.0, 'hound'), (-310.0, -11.0, 'unloading'), (-220.0, 4.0, 'empty'),
          (-130.0, 12.0, 'lifting'), (-40.0, -7.0, 'fuelling'), (140.0, -4.0, 'ferry')]   # x 50: the heavy berth's road
# Lighter pads, two in each gap between the freighters' cargo lanes beside the port road: Hermes
# shuttles bringing cargo down from ships at Jangala Station and taking it up.
LIGHTER_Y, LIGHTER_R = 412.0, 13.0
LIGHTERS = [(-445.0, 'parked'), (-355.0, 'lifting'), (-265.0, 'parked'), (-175.0, 'landing'),
            (-85.0, 'parked'), (5.0, 'parked'), (95.0, 'parked')]
# The heavy berth: a walled bay with a gantry for the largest hull that lands. Empty, cargo staged
# for an inbound.
HEAVY = (-60.0, 70.0, 615.0, 715.0)       # x0, x1, y0, y1: behind the berths, its road down to the port road
OFFICE = (240.0, 415.0)                   # arrivals: customs and decontamination, MuniSec's post
# Kilo Nine, Nessa's private cradle, faces the way Second Wind comes in.
K9 = (400.0, 465.0)
K9_R = 48.0                               # a 96 m pad: Second Wind sits inside its ring
YARD = (290.0, 495.0, 384.0, 755.0)       # Nessa's yard: x0, x1, y0, y1; the works fill its north half
GATE = (296.0, 318.0)                     # its one gate: in the south fence, where the port road ends
TRACK = (298.0, 312.0)                    # the yard's haul road, from the gate up the west fence
MODULE_RGB = [(0.70, 0.70, 0.66), (0.30, 0.42, 0.30), (0.72, 0.40, 0.12), (0.25, 0.32, 0.45)]


def at(cx, cy, h, u, v, z=0.0):
    """The point u ahead along heading h and v to its left, from (cx, cy)."""
    return (cx + u * math.cos(h) - v * math.sin(h), cy + u * math.sin(h) + v * math.cos(h), z)


def lattice_tower(bm, base, h, half, height, step=7.0, deck=None):
    """Four legs braced every `step` metres: service towers and repair scaffold."""
    legs = [at(base[0], base[1], h, a, b)[:2] for a, b in
            ((-half[0], -half[1]), (half[0], -half[1]), (half[0], half[1]), (-half[0], half[1]))]
    for lx, ly in legs:
        strut(bm, (lx, ly, 0), (lx, ly, height), 0.7)
    z = step
    while z <= height + 0.01:
        for (ax, ay), (bx, by) in zip(legs, legs[1:] + legs[:1]):
            strut(bm, (ax, ay, z), (bx, by, z), 0.5)
        if deck is not None:
            box(deck, (2 * half[0], 2 * half[1], 0.4), (base[0], base[1], z), h)
        z += step


def supplies(bms, r, cx, cy, h, u, v):
    """What a ship takes on or is mended with, staged beside its pad: modules, steel, crates."""
    mods, steel, crates = bms
    for i in range(3):
        for j in range(2):
            for lvl in range(r.randrange(1, 3)):
                box(mods[r.randrange(len(mods))], (7, 5, 4), at(cx, cy, h, u + i * 8, v + j * 6, 2 + 4 * lvl), h)
    for k in range(4):
        for lvl in range(3):
            box(steel, (20, 0.9, 0.9), at(cx, cy, h, u + 10, v - 6 - k * 1.6, 0.5 + lvl * 0.95), h)
    for _ in range(8):
        box(crates, (2.2, 2.2, 2.0), at(cx, cy, h, u + r.uniform(0, 22), v + r.uniform(13, 19), 1.0),
            h + r.uniform(-0.2, 0.2))


def k9_heading():
    return math.atan2(K9[1] - P0.y, K9[0] - P0.x)


def add_port():
    r = random.Random(33)
    asphalt = bpy.data.materials['Asphalt']
    concrete = bpy.data.materials['Concrete']
    steel = bpy.data.materials['Steel']
    paint = material('Pad paint', (0.85, 0.70, 0.15), 0.6)
    stock = material('Steel stock', (0.38, 0.20, 0.11), 0.7, 0.4)
    crate = material('Crates', (0.50, 0.42, 0.28), 0.8)
    plank = material('Scaffold decks', (0.55, 0.47, 0.32), 0.8)
    mod_mats = [material('Module %d' % i, c, 0.6, 0.2) for i, c in enumerate(MODULE_RGB)]
    pads, marks, towers, tankb, stockb, crateb, deckb = (bmesh.new() for _ in range(7))
    mods = [bmesh.new() for _ in mod_mats]

    def pad(cx, cy, h, rad, lane, top=3.0):
        cylinder(pads, rad, top, (cx, cy, 0), 40)
        for a in range(40):
            ang = 2 * math.pi * a / 40
            box(marks, (rad * 0.1, 1.4, 0.1), (cx + (rad - 6) * math.cos(ang), cy + (rad - 6) * math.sin(ang), top + 0.05),
                ang + math.pi / 2)
        box(marks, (2 * rad - 18, 1.8, 0.1), (cx, cy, top + 0.05), h)            # centre line: the way it faces
        box(marks, (1.8, rad * 0.8, 0.1), at(cx, cy, h, rad * 0.5, 0, top + 0.05), h)   # nose bar
        for k in range(lane):                                              # lead-in dashes on the apron
            box(marks, (6, 1.2, 0.1), at(cx, cy, h, -(rad + 10 + k * 11), 0, 0.1), h)

    # The public port: its road, the port office customs works from, then the rows.
    plane('Port road', -700, GATE[1] + 4, 366, 378, asphalt, 0.08)    # runs on to Nessa's gate
    ox, oy = OFFICE
    office = bmesh.new()
    box(office, (40, 34, 10), (ox, oy, 5))
    fx, fy, _ = at(140.0, BERTH_Y, ROW_HEADING, 0, -(BERTH_R + 4))       # the ferry's side door
    box(office, (5, abs(fy - oy) - 10, 4), (fx, (fy + oy) / 2 + 8, 2))     # covered walk down to the hall
    box(office, (abs(ox - fx) - 18, 5, 4), ((fx + ox) / 2 - 8, oy, 2))
    mesh('Arrivals hall', office, material('Arrivals hall', (0.80, 0.80, 0.78), 0.5))
    band = bmesh.new()
    box(band, (40, 4, 0.3), (ox, oy, 10.15))
    mesh('Arrivals band', band, material('Hegemony orange', (0.96, 0.59, 0.12), 0.5))
    strut(towers, (ox + 14, oy + 14, 0), (ox + 14, oy + 14, 24), 0.6)      # the office's mast
    lring = bmesh.new()
    for x, who in LIGHTERS:
        pad(x, LIGHTER_Y, math.pi / 2, LIGHTER_R, 0)
        for a in range(32):
            ang = 2 * math.pi * a / 32
            box(lring, (2.8, 1.5, 0.12), (x + (LIGHTER_R + 1.5) * math.cos(ang), LIGHTER_Y + (LIGHTER_R + 1.5) * math.sin(ang), 0.3),
                ang + math.pi / 2)
        if who == 'parked':                                                 # stores and a crate or two
            for k in range(r.randrange(2, 5)):
                box(crateb, (2.2, 2.2, 2.0), (x + r.uniform(-6, 6), LIGHTER_Y - LIGHTER_R - 3 - k * 2.6, 1.0))
    h = ROW_HEADING
    for x, off, who in BERTHS:
        y = BERTH_Y + BERTH_DY[x]
        pad(x, y, h, BERTH_R, 0)
        lattice_tower(towers, at(x, y, h, 4, BERTH_R + 10), h, (2.5, 2.5), 38)
        strut(towers, at(x, y, h, 4, BERTH_R + 10, 37), at(x, y, h, 4, BERTH_R - 12, 37), 1.4)   # service arm
        for k in (0, 1):
            cylinder(tankb, 4.5, 9, at(x, y, h, BERTH_R + 12, -18 - k * 11), 18)
        supplies((mods, stockb, crateb), r, x, y, h, BERTH_R + 6, 4)
    mesh('Lighter rings', lring, material('Lighter ring', (0.95, 0.80, 0.20), 0.5))
    # Hegemony crest on the arrivals hall roof: an orange disc and a white bar.
    crest = bmesh.new()
    cylinder(crest, 12, 0.3, (OFFICE[0], OFFICE[1], 10.2), 32)
    mesh('Hegemony crest', crest, bpy.data.materials['Hegemony orange'])
    bar = bmesh.new()
    box(bar, (17, 3.5, 0.2), (OFFICE[0], OFFICE[1], 10.6))
    mesh('Hegemony crest bar', bar, material('Crest white', (0.92, 0.92, 0.90), 0.5))
    # The heavy berth's service road, between the lighter pads, down to the port road; staging beside it.
    plane('Heavy berth road', 43, 57, 378, HEAVY[2], asphalt, 0.085)
    for i in range(6):
        for j in range(5):
            for lvl in range(r.randrange(1, 3)):
                box(mods[r.randrange(len(mods))], (7, 5, 4), (100 + i * 9, 600 + j * 7, 2 + 4 * lvl))
    for k in range(5):
        for lvl in range(3):
            box(stockb, (24, 0.9, 0.9), (180, 612 + k * 1.8, 0.5 + lvl * 0.95))
    for i in range(4):                                                  # fuel farm, west of the heavy berth
        for j in range(2):
            cylinder(tankb, 7, 10, (-390 + i * 30, 660 + j * 30, 0), 24)
    plane('Fuel farm bund', -405, -285, 645, 705, concrete, 0.09)
    shed = bmesh.new()                                                  # freight terminal shed, east
    box(shed, (80, 50, 12), (220, 670, 6))
    mesh('Terminal shed', shed, bpy.data.materials['Roof grey'])
    doors = bmesh.new()
    for k in range(5):
        box(doors, (7, 1.0, 6), (190 + k * 15, 644.6, 3))
    mesh('Terminal doors', doors, bpy.data.materials['Steel'])
    plane('Fuel farm road', -300, -285, 560, 645, asphalt, 0.085)
    vehicle('Terminal truck', [((3.4, 3.0, 3.4), (0, 0, 2.1), bpy.data.materials['Hauler cab']),
                               ((12.5, 3.0, 3.2), (-9, 0, 2.4), bpy.data.materials['Hauler load'])],
            [(0, 205, 630)], [(0, -math.pi / 2)])
    plane('Terminal road', 57, 180, 680, 692, asphalt, 0.085)
    # The heavy berth: blast walls on three sides, gantry rails along them, a crane across.
    hx0, hx1, hy0, hy1 = HEAVY
    plane('Heavy berth deck', hx0, hx1, hy0, hy1, concrete, 0.2)
    wall = bmesh.new()
    box(wall, (2, hy1 - hy0, 6), (hx0, (hy0 + hy1) / 2, 3))
    box(wall, (2, hy1 - hy0, 6), (hx1, (hy0 + hy1) / 2, 3))
    box(wall, (hx1 - hx0, 2, 6), ((hx0 + hx1) / 2, hy1, 3))
    for y in (hy0 + 12, hy1 - 12):                                          # deck markings: a big box
        box(marks, (hx1 - hx0 - 30, 1.6, 0.1), ((hx0 + hx1) / 2, y, 0.3))
    box(marks, (1.6, hy1 - hy0 - 24, 0.1), (hx0 + 15, (hy0 + hy1) / 2, 0.3))
    box(marks, (1.6, hy1 - hy0 - 24, 0.1), (hx1 - 15, (hy0 + hy1) / 2, 0.3))
    mesh('Heavy berth walls', wall, concrete)
    gc = bmesh.new()
    for x in (hx0 - 4, hx1 + 4):
        box(gc, (2, hy1 - hy0, 0.8), (x, (hy0 + hy1) / 2, 0.4))             # rails
        for y in (HEAVY[2] + 38, HEAVY[2] + 56):
            strut(gc, (x, y, 0), (x, y, 46), 1.8)
    for y in (HEAVY[2] + 38, HEAVY[2] + 56):
        box(gc, (hx1 - hx0 + 14, 2.4, 3), ((hx0 + hx1) / 2, y, 46))
    box(gc, (10, 22, 4), ((hx0 + hx1) / 2 - 20, HEAVY[2] + 47, 48))                  # trolley
    mesh('Heavy berth gantry', gc, bpy.data.materials['Crane yellow'])

    # Kilo Nine: Nessa's yard, fenced, on the east edge with the jungle beyond.
    x0, x1, y0, y1 = YARD
    plane('Nessa yard', x0, x1, y0, y1, material('Yard gravel', (0.30, 0.27, 0.22), 0.95), 0.15)
    fence = bmesh.new()
    for x in range(int(x0), int(x1) + 1, 6):
        for y in (y0, y1):
            if y == y0 and GATE[0] < x < GATE[1]:
                continue
            box(fence, (0.6, 0.6, 3), (x, y, 1.5))
    for y in range(int(y0), int(y1) + 1, 6):
        for x in (x0, x1):
            box(fence, (0.6, 0.6, 3), (x, y, 1.5))
    box(fence, (GATE[0] - x0, 0.15, 0.15), ((x0 + GATE[0]) / 2, y0, 2.8))
    box(fence, (x1 - GATE[1], 0.15, 0.15), ((GATE[1] + x1) / 2, y0, 2.8))
    box(fence, (x1 - x0, 0.15, 0.15), ((x0 + x1) / 2, y1, 2.8))
    box(fence, (0.15, y1 - y0, 0.15), (x0, (y0 + y1) / 2, 2.8))
    box(fence, (0.15, y1 - y0, 0.15), (x1, (y0 + y1) / 2, 2.8))
    mesh('Nessa fence', fence, bpy.data.materials['Fence steel'])
    edge = material('Fence line', (0.07, 0.07, 0.06), 0.9)
    plane('Fence line S1', x0, GATE[0], y0 - 1, y0 + 1, edge, 0.17)
    plane('Fence line S2', GATE[1], x1, y0 - 1, y0 + 1, edge, 0.17)
    plane('Fence line N', x0, x1, y1 - 1, y1 + 1, edge, 0.17)
    plane('Fence line E', x1 - 1, x1 + 1, y0, y1, edge, 0.17)
    plane('Fence line W', x0 - 1, x0 + 1, y0, y1, edge, 0.17)
    # The gate: the port road turns in through it, past a gatehouse with a barrier raised, and the
    # haul road runs on up the west fence to the works at the north end.
    plane('Gate apron', GATE[0], GATE[1], 366, y0 + 8, asphalt, 0.085)
    track = material('Haul road', (0.20, 0.19, 0.17), 0.9)
    plane('Haul road', TRACK[0], TRACK[1], y0, y1 - 8, track, 0.18)
    plane('Haul road to the pad', TRACK[1], 352, 424, 436, track, 0.18)
    plane('Haul road to the works', TRACK[1], 400, 588, 600, track, 0.18)
    gh = bmesh.new()
    box(gh, (5, 4, 3), (GATE[1] + 5, y0 + 6, 1.5))                       # gatehouse
    mesh('Gatehouse', gh, bpy.data.materials['Roof grey'])
    arm = bmesh.new()
    strut(arm, (GATE[1] + 1, y0 + 1, 1.2), (GATE[1] - 2, y0 + 1, 11.0), 0.35)   # barrier up
    mesh('Gate barrier', arm, bpy.data.materials['Hegemony orange'])
    leaves = bmesh.new()
    box(leaves, (0.3, 9, 2.6), (GATE[0] - 1, y0 - 4.5, 1.3))             # the gate leaves, swung open
    box(leaves, (0.3, 9, 2.6), (GATE[1] + 1, y0 - 4.5, 1.3))
    mesh('Gate leaves', leaves, bpy.data.materials['Fence steel'])
    kx, ky = K9
    kh = k9_heading()
    pad(kx, ky, kh, K9_R, 3, K9_SLAB)
    ring = bmesh.new()
    for a in range(64):
        ang = 2 * math.pi * a / 64
        box(ring, (4.0, 2.2, 0.12), (kx + (K9_R + 2) * math.cos(ang), ky + (K9_R + 2) * math.sin(ang), 0.3), ang + math.pi / 2)
    mesh('Kilo Nine ring', ring, material('Kilo Nine ring', (0.95, 0.80, 0.20), 0.5))
    for side in (-1, 1):                                                   # repair scaffold either side
        lattice_tower(towers, at(kx, ky, kh, 0, side * (K9_R + 10)), kh, (14, 3), 28, deck=deckb)
    # A derrick over the salvage, a hangar, and the workshop the families walk into.
    strut(towers, (455, 545, 0), (455, 545, 46), 2.0)
    strut(towers, (455, 545, 38), (420, 512, 50), 2.0)
    strut(towers, (455, 545, 44), (463, 553, 40), 1.4)
    strut(towers, (420, 512, 50), (420, 512, 18), 0.3)
    box(towers, (5, 5, 3), (465, 555, 39))
    hull = material('Hull plate', (0.22, 0.23, 0.24), 0.5, 0.6)
    hb = bmesh.new()
    for _ in range(16):
        box(hb, (r.uniform(6, 14), r.uniform(3, 6), 0.6), (r.uniform(412, 462), r.uniform(514, 550), r.uniform(0.5, 2.0)),
            r.uniform(0, math.pi))
    mesh('Salvage plates', hb, hull)
    hang = bmesh.new()
    box(hang, (34, 42, 18), (330, 540, 9))
    mesh('Nessa hangar', hang, bpy.data.materials['Roof rust'])
    shop = bmesh.new()
    box(shop, (28, 18, 7), (334, 400, 3.5))
    box(shop, (12, 10, 5), (357, 395, 2.5))
    mesh('Nessa workshop', shop, bpy.data.materials['Roof grey'])
    for k in (0, 1):
        cylinder(tankb, 4, 8, (455, 395 + k * 11, 0), 18)
    supplies((mods, stockb, crateb), r, kx, ky, kh, -50, 68)
    life = bmesh.new()
    box(life, (12, 7, 0.2), (334, 413, 3.2))                            # tarp awning at the door
    mesh('Workshop awning', life, material('Tarp orange', (0.85, 0.42, 0.12), 0.8))
    wash = [material('Washing %d' % i, c, 0.9) for i, c in
            enumerate(((0.97, 0.97, 0.95), (0.30, 0.45, 0.70), (0.75, 0.30, 0.25), (0.85, 0.75, 0.35)))]
    wb = [bmesh.new() for _ in wash]
    for line in (0, 1):                                                 # washing lines by the workshop
        for k in range(9):
            box(wb[0 if (k + line) % 3 else 1 + (k % 3)], (1.8, 0.7, 1.4), (368 + k * 2.1, 396 + line * 4, 1.9))
    for bm, m in zip(wb, wash):
        mesh(m.name + ' line', bm, m)
    for y in (396, 400):
        strut(towers, (366, y, 0), (366, y, 2.6), 0.15)
        strut(towers, (387, y, 0), (387, y, 2.6), 0.15)
    garden = material('Garden rows', (0.18, 0.40, 0.12), 0.9)
    gb = bmesh.new()
    for k in range(6):                                                  # vegetable rows against the back fence
        box(gb, (16, 1.0, 0.5), (476, 428 - k * 1.8, 0.4))
    mesh('Garden patch', gb, garden)
    plane('Garden soil', 467, 485, 418, 432, material('Garden soil', (0.20, 0.13, 0.08), 0.95), 0.2)
    chairs = bmesh.new()
    for x, y in ((325, 416), (327, 419), (342, 417)):
        box(chairs, (0.6, 0.6, 0.9), (x, y, 0.45))
    mesh('Chairs', chairs, crate)
    people = material('People', (0.12, 0.12, 0.13), 0.9)
    pb = bmesh.new()
    for x, y in ((330, 417), (336, 419), (339, 416), (448, 418), (352, 487), (356, 490), (480, 436),
                 (322, 640), (327, 641), (410, 700), (458, 712), (306, 742)):
        box(pb, (0.9, 0.9, 1.8), (x, y, 0.9))
    mesh('Yard people', pb, people)
    add_k9_works(r, mods, stockb, crateb, towers, tankb)

    mesh('Pads', pads, concrete)
    mesh('Pad markings', marks, paint)
    mesh('Port towers and scaffold', towers, steel)
    mesh('Port fuel tanks', tankb, bpy.data.materials['Tank white'])
    mesh('Steel stock', stockb, stock)
    mesh('Crates', crateb, crate)
    mesh('Scaffold decks', deckb, plank)
    for bm, m in zip(mods, mod_mats):
        mesh(m.name + ' stacks', bm, m)

    # The jungle past Nessa's back fence.
    burn = bmesh.new()
    for _ in range(26):
        box(burn, (r.uniform(8, 22), r.uniform(10, 40), 0.05), (x1 + r.uniform(6, 18), r.uniform(340, 1000), 0.14),
            r.uniform(-0.4, 0.4))
    mesh('Burn patches', burn, material('Burn scar', (0.16, 0.12, 0.07), 0.95))
    plane('Jungle floor', x1 + 6, 2000, 340, 2500, material('Jungle floor', (0.05, 0.11, 0.04), 0.95), 0.12)
    jb = bmesh.new()
    for _ in range(420):
        cx, cy = r.uniform(x1 + 14, 1000), r.uniform(345, 1100)
        rad = r.uniform(6, 12)
        res = bmesh.ops.create_icosphere(jb, subdivisions=1, radius=rad)
        bmesh.ops.translate(jb, vec=(cx, cy, rad + 4), verts=res['verts'])
    for _ in range(14):                                                 # over the back fence
        rad = r.uniform(5, 9)
        cx, cy = r.choice(((r.uniform(x1 - 6, x1 + 4), r.uniform(y0 + 20, y1)),
                           (r.uniform(x0 + 80, x1), r.uniform(y1 - 4, y1 + 6))))
        res = bmesh.ops.create_icosphere(jb, subdivisions=1, radius=rad)
        bmesh.ops.translate(jb, vec=(cx, cy, rad + 3), verts=res['verts'])
    mesh('Jungle canopy', jb, material('Jungle canopy', (0.05, 0.14, 0.05), 0.9))
    # The apron stops at a tree line, so nothing beyond reads as unused ground.
    tb = bmesh.new()
    for x in range(-700, int(x1), 9):
        rad = r.uniform(5, 8)
        res = bmesh.ops.create_icosphere(tb, subdivisions=1, radius=rad)
        bmesh.ops.translate(tb, vec=(x + r.uniform(-3, 3), 770 + r.uniform(-4, 10), rad + 3), verts=res['verts'])
    mesh('Port tree line', tb, bpy.data.materials['Jungle canopy'])
    plane('Beyond the port', -2000, x1 + 6, 765, 2500, bpy.data.materials['Jungle floor'], 0.12)

    add_cargo_flow(r)
    add_k9_vehicles()


def shuttle(name, parts, a, b, speed, hold, phase):
    """A vehicle running back and forth between a and b: hold, drive, hold, drive back."""
    ax, ay = a[0], a[1]
    bx, by = b[0], b[1]
    dist = math.hypot(bx - ax, by - ay)
    run = dist / speed
    period = 2 * (run + hold)
    keys = []
    for f in range(0, FRAMES + 2, 3):
        u = (f / FPS + phase * period) % period
        if u < hold:
            k = 0.0
        elif u < hold + run:
            k = (u - hold) / run
        elif u < 2 * hold + run:
            k = 1.0
        else:
            k = 1.0 - (u - 2 * hold - run) / run
        keys.append((min(f, FRAMES - 1), ax + (bx - ax) * k, ay + (by - ay) * k))
    v = vehicle(name, parts, keys, [(0, math.atan2(by - ay, bx - ax))])
    for fc in v.animation_data.action.fcurves if hasattr(v.animation_data.action, 'fcurves') else []:
        for kp in fc.keyframe_points:
            kp.interpolation = 'LINEAR'
    return v


def add_k9_works(r, mods, stockb, crateb, towers, tankb):
    """The north half of Nessa's yard: where hulls are actually mended. A fabrication hall, an
    overhead crane over the plate stock, a parts store of stacked modules, the yard's own power, a
    scrap heap, and the turning ground at the end of the haul road."""
    hall = bmesh.new()
    box(hall, (72, 34, 14), (352, 625, 7))
    mesh('Fabrication hall', hall, bpy.data.materials['Roof rust'])
    lights = bmesh.new()
    for k in range(4):
        box(lights, (64, 1.6, 0.2), (352, 613 + k * 8, 14.1))           # roof lights
    mesh('Fabrication hall roof lights', lights, material('Roof glazing', (0.55, 0.62, 0.66), 0.2, 0.3))
    doors = bmesh.new()
    for k in range(3):
        box(doors, (12, 1.0, 10), (326 + k * 26, 607.6, 5))
    mesh('Fabrication hall doors', doors, bpy.data.materials['Steel'])
    # Overhead crane: rails on legs either side of the plate laydown, a bridge that runs along them.
    crane = bmesh.new()
    for x in (404.0, 474.0):
        box(crane, (1.6, 92, 1.4), (x, 645, 16))
        for y in range(600, 692, 15):
            strut(crane, (x, y, 0), (x, y, 16), 1.0)
    mesh('Plate crane rails', crane, bpy.data.materials['Crane yellow'])
    bridge = bmesh.new()
    box(bridge, (74, 3, 2.4), (0, 0, 17.6))
    box(bridge, (5, 5, 2), (8, 0, 15.2))
    br = mesh('Plate crane bridge', bridge, bpy.data.materials['Crane yellow'])
    for t, y in ((0.0, 612), (5.0, 668), (8.0, 668), (13.0, 630), (DURATION, 620)):   # working the laydown
        br.location = (439, y, 0)
        br.keyframe_insert('location', frame=round(t * FPS))
    plate = material('Plate stock', (0.30, 0.31, 0.32), 0.5, 0.6)
    pl = bmesh.new()
    for i in range(3):
        for j in range(4):
            for lvl in range(r.randrange(1, 5)):
                box(pl, (14, 6, 0.5), (418 + i * 20, 610 + j * 18, 0.4 + lvl * 0.55))
    for k in range(3):                                                  # hull sections waiting to go on
        box(pl, (12, 8, 6), (420 + k * 22, 684, 3), r.uniform(-0.2, 0.2))
    mesh('Plate stock and hull sections', pl, plate)
    for k in range(5):
        for lvl in range(3):
            box(stockb, (24, 0.9, 0.9), (446, 600 + k * 1.8, 0.5 + lvl * 0.95))
    # Parts store: modules stacked in rows north of the hall, aisles between.
    for i in range(7):
        for j in range(3):
            for lvl in range(r.randrange(1, 3)):
                box(mods[r.randrange(len(mods))], (7, 5, 4), (322 + i * 9, 660 + j * 12, 2 + 4 * lvl))
    # The yard's own power: generator sets, a transformer, two day tanks, and cable runs.
    gens = bmesh.new()
    for k in range(3):
        box(gens, (12, 3, 3.2), (448, 704 + k * 6, 1.6))
    box(gens, (5, 5, 4), (470, 712, 2))
    mesh('Generator sets', gens, material('Generator green', (0.24, 0.34, 0.24), 0.6, 0.2))
    for k in range(2):
        cylinder(tankb, 3.5, 7, (484, 700 + k * 9, 0), 16)
    # The scrap heap in the north-east corner: offcuts and stripped parts.
    scrap = bmesh.new()
    for _ in range(40):
        box(scrap, (r.uniform(1.5, 6), r.uniform(1.5, 5), r.uniform(0.6, 2.5)),
            (r.uniform(462, 488), r.uniform(726, 748), r.uniform(0.4, 2.5)), r.uniform(0, math.pi))
    mesh('Scrap heap', scrap, bpy.data.materials['Hull plate'])
    # Turning ground at the head of the haul road, a flatbed with a hull section on it.
    plane('Turning ground', 312, 400, 718, 750, bpy.data.materials['Haul road'], 0.18)
    vehicle('Flatbed', flatbed_parts(bpy.data.materials['Hauler cab'], bpy.data.materials['Steel'],
                                     bpy.data.materials['Plate stock']),
            [(0, 352, 734)], [(0, 0.0)])
    # A forklift running modules between the store and the hall the whole time.
    shuttle('Yard forklift', forklift_parts(bpy.data.materials['Crane yellow'], bpy.data.materials['Module 2']),
            (318, 652), (318, 612), 4.0, 2.0, 0.3)


# The lift jets' throttle, as the thruster markers key it (`thrust_shots.mark_c4`): lit over the two
# seconds before the hover, full through the descent, off a second after touchdown.
LIFT_ON, LIFT_OFF = 2.0, 1.0


def lift_throttle(t):
    return smoothstep(ARRIVE - LIFT_ON, ARRIVE, t) * (1 - smoothstep(T_DOWN, T_DOWN + LIFT_OFF, t))


def lift_nozzles(manifest):
    """Second Wind's working belly jets, from the hull's own anchors (the thruster markers' source):
    the two under the bow are one source, then each engine pod's and the starboard stern jet. The
    port stern jet is dead, so that corner of the pad blows less."""
    down = [r for r in manifest['rcs'] if r['exhaust_dir'][2] < -0.7 and r['working']]
    bow = [r['at'] for r in down if r['cluster'] == 'ventral']
    out = [Vector(r['at']) for r in down if r['cluster'] != 'ventral']
    if bow:
        out.append(sum((Vector(a) for a in bow), Vector()) / len(bow))
    return out


def dust_material():
    """Soft, noisy puffs. Each object's colour carries its own tint, and its alpha the fade."""
    m = bpy.data.materials.new('Landing dust')
    m.use_nodes = True
    m.surface_render_method = 'BLENDED'
    m.use_transparent_shadow = True                                    # the cloud shades the pad under it
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    p.inputs['Roughness'].default_value = 1.0
    info = nt.nodes.new('ShaderNodeObjectInfo')
    # Billows read from above by their sunny and shaded sides: brighten the face turned to the sun,
    # darken the far side (sunlight alone barely shades a thin, flattened puff).
    geo = nt.nodes.new('ShaderNodeNewGeometry')
    sun = nt.nodes.new('ShaderNodeVectorMath')
    sun.operation = 'DOT_PRODUCT'
    sun.inputs[1].default_value = SUN_DIR
    lit = nt.nodes.new('ShaderNodeMapRange')
    lit.inputs['From Min'].default_value, lit.inputs['From Max'].default_value = -0.2, 1.0
    lit.inputs['To Min'].default_value, lit.inputs['To Max'].default_value = 0.5, 1.15
    shade = nt.nodes.new('ShaderNodeVectorMath')
    shade.operation = 'SCALE'
    nt.links.new(geo.outputs['Normal'], sun.inputs[0])
    nt.links.new(sun.outputs['Value'], lit.inputs['Value'])
    nt.links.new(info.outputs['Color'], shade.inputs[0])
    nt.links.new(lit.outputs['Result'], shade.inputs['Scale'])
    nt.links.new(shade.outputs[0], p.inputs['Base Color'])
    coord = nt.nodes.new('ShaderNodeTexCoord')
    noise = nt.nodes.new('ShaderNodeTexNoise')
    noise.noise_dimensions = '4D'                                       # each puff its own lumps
    noise.inputs['Scale'].default_value = 2.0
    noise.inputs['Detail'].default_value = 6.0
    noise.inputs['Roughness'].default_value = 0.62
    seed = nt.nodes.new('ShaderNodeMath')
    seed.operation = 'MULTIPLY'
    seed.inputs[1].default_value = 40.0
    nt.links.new(info.outputs['Random'], seed.inputs[0])
    nt.links.new(seed.outputs[0], noise.inputs['W'])
    soft = nt.nodes.new('ShaderNodeMapRange')
    soft.inputs['From Min'].default_value = 0.42
    soft.inputs['From Max'].default_value = 0.64
    edge = nt.nodes.new('ShaderNodeLayerWeight')                       # thin toward the rim: no hard ball edges
    edge.inputs['Blend'].default_value = 0.5
    rim = nt.nodes.new('ShaderNodeMath')
    rim.operation = 'SUBTRACT'
    rim.inputs[0].default_value = 1.0
    rim2 = nt.nodes.new('ShaderNodeMath')
    rim2.operation = 'POWER'
    rim2.inputs[1].default_value = 1.2
    nt.links.new(coord.outputs['Object'], noise.inputs['Vector'])
    nt.links.new(noise.outputs['Fac'], soft.inputs['Value'])
    nt.links.new(edge.outputs['Facing'], rim.inputs[1])
    nt.links.new(rim.outputs[0], rim2.inputs[0])
    a = soft.outputs['Result']
    for other in (info.outputs['Alpha'], rim2.outputs[0]):
        mul = nt.nodes.new('ShaderNodeMath')
        mul.operation = 'MULTIPLY'
        nt.links.new(a, mul.inputs[0])
        nt.links.new(other, mul.inputs[1])
        a = mul.outputs[0]
    nt.links.new(a, p.inputs['Alpha'])
    return m


def add_landing_dust(manifest):
    """Downwash off Kilo Nine as Second Wind comes down from its hover on its belly jets. Each jet
    hits the pad below it and blows a wall of dust and grit out along the slab: faint stirring at
    sixty metres, a rolling ring running out past the pad's edge as the hull sinks, grit streaking
    out ahead of it and grey smoke boiling up round the pods. The jets cut a second after the legs
    touch; the last of it drifts downwind and thins, and every puff is gone before C4 ends (C5
    removes them at the join)."""
    scene = bpy.context.scene
    root = bpy.data.objects[manifest['root']]
    nozzles = lift_nozzles(manifest)
    m = dust_material()
    r = random.Random(91)
    kx, ky = K9
    wind = Vector((1.6, 0.3, 0.0))                      # metres a second: the steam leans the same way
    gone = DURATION - 0.4                               # all faded by here
    f_a, f_b = math.floor((ARRIVE - LIFT_ON) * FPS), math.ceil((T_DOWN + LIFT_OFF) * FPS)

    # Where each jet hits the ground, and how hard, frame by frame along the ship's own keyed path.
    track = {}
    for f in range(f_a, f_b + 1):
        scene.frame_set(f)
        mw = root.matrix_world
        centre = (mw @ Vector((9.0, 0.0, 0.0))).to_2d()
        hits = []
        for n in nozzles:
            w = mw @ n
            ground = K9_SLAB if math.hypot(w.x - kx, w.y - ky) < K9_R else 0.15
            h = max(0.0, w.z - ground)
            hits.append((Vector((w.x, w.y, ground)), lift_throttle(f / FPS) * (1 - smoothstep(10.0, 80.0, h)), h))
        track[f] = (centre, hits)
    scene.frame_set(0)

    def spawn(name, ts, p0, centre, life, motion, tint):
        """One puff: motion(tau) -> (offset from p0, (sx, sy, sz), heading, alpha)."""
        bm = bmesh.new()
        bmesh.ops.create_icosphere(bm, subdivisions=2, radius=1.0)
        ob = mesh(name, bm, m)
        ob.rotation_mode = 'XYZ'
        f0 = round(ts * FPS)
        f1 = min(round(gone * FPS), f0 + round(life * FPS))
        for f in [f0 - 1] + list(range(f0, f1, 2)) + [f1]:
            tau = max(0.0, (f - f0) / FPS)
            off, sc, hd, a = motion(tau)
            a *= 1 - smoothstep(gone - 2.5, gone, f / FPS)        # whatever is left thins out by the join
            if f < f0 or f >= f1:
                a = 0.0
            ob.location = p0 + off
            ob.scale = sc
            ob.rotation_euler = (0.0, 0.0, hd)
            ob.color = tint + (a,)
            for prop in ('location', 'scale', 'rotation_euler', 'color'):
                ob.keyframe_insert(prop, frame=f)
        return ob

    def outward(p, centre, spread):
        """Out from the hull's middle through the jet's footprint, fanned."""
        d = p.to_2d() - centre
        base = math.atan2(d.y, d.x) if d.length > 2.0 else r.uniform(0, 2 * math.pi)
        a = base + r.gauss(0.0, spread)
        return Vector((math.cos(a), math.sin(a), 0.0)), a

    # Spawn by accumulated strength: the harder and closer the jets, the more comes off the pad.
    budget = {'dust': 210, 'grit': 140, 'smoke': 46}
    total = sum(s for f in track for _, s, _ in track[f][1]) or 1.0
    acc = {k: r.random() for k in budget}
    n = {k: 0 for k in budget}
    for f in range(f_a, f_b + 1):
        centre, hits = track[f]
        for p, s, h in hits:
            if s <= 0.0:
                continue
            ts = f / FPS + r.uniform(0.0, 1.0 / FPS)
            for kind in budget:
                acc[kind] += budget[kind] * s / total
                while acc[kind] >= 1.0:
                    acc[kind] -= 1.0
                    n[kind] += 1
                    if kind == 'dust':
                        u, hd = outward(p, centre, 0.55)
                        v0 = r.uniform(26.0, 46.0) * (0.45 + 0.55 * s)
                        drag = r.uniform(1.1, 1.7)
                        reach = v0 * drag
                        life = min(r.uniform(2.6, 4.6), gone - ts)
                        size0, size1 = r.uniform(3.0, 5.0), r.uniform(11.0, 19.0) * (0.6 + 0.4 * s)
                        peak = min(1.0, r.uniform(0.35, 1.1)) * (0.35 + 0.65 * s)
                        lum = r.uniform(0.82, 1.12)
                        tint = (0.40 * lum, 0.335 * lum, 0.25 * lum)   # the yard's own dirt

                        def motion(tau, u=u, hd=hd, reach=reach, drag=drag, life=life, size0=size0,
                                   size1=size1, peak=peak):
                            run = 1 - math.exp(-tau / drag)
                            k = tau / life
                            # Wall jet: fast and low off the slab, slowing, the front rolling up as it goes.
                            off = u * (4.0 + reach * run) + wind * tau + Vector((0, 0, 1.2 + 5.5 * run + 0.5 * tau))
                            sz = size0 + (size1 - size0) * (1 - math.exp(-tau / (0.6 * drag + 0.4)))
                            # Thin while it is a fast sheet off the jet, thickest as the front piles up.
                            a = peak * (0.25 + 0.75 * smoothstep(0.05, 0.4, k)) * smoothstep(0.0, 0.1, tau) * (1 - smoothstep(0.55, 1.0, k))
                            return off, (sz * 1.2, sz * 0.85, sz * (0.38 + 0.2 * run)), hd + math.pi / 2, a
                        spawn('Landing dust %d' % n[kind], ts, p, centre, life, motion, tint)
                    elif kind == 'grit' and s > 0.25:
                        u, hd = outward(p, centre, 0.7)
                        v0 = r.uniform(55.0, 85.0) * s
                        life = min(r.uniform(0.5, 0.95), gone - ts)
                        ln, wd = r.uniform(5.0, 10.0), r.uniform(0.5, 0.9)
                        peak = r.uniform(0.35, 0.6)
                        tint = (0.24, 0.20, 0.15)

                        def motion(tau, u=u, hd=hd, v0=v0, life=life, ln=ln, wd=wd, peak=peak):
                            run = 0.55 * (1 - math.exp(-tau / 0.55))
                            off = u * (3.0 + v0 * run) + Vector((0, 0, 0.6))
                            a = peak * smoothstep(0.0, 0.05, tau) * (1 - smoothstep(0.25, 1.0, tau / life))
                            return off, (ln * (1 - 0.4 * tau / life), wd, wd * 0.5), hd, a
                        spawn('Landing dust grit %d' % n[kind], ts, p, centre, life, motion, tint)
                    elif kind == 'smoke':
                        ang = r.uniform(0, 2 * math.pi)
                        u = Vector((math.cos(ang), math.sin(ang), 0.0))
                        life = min(r.uniform(3.2, 5.0), gone - ts)
                        rise = r.uniform(9.0, 18.0)
                        size0, size1 = r.uniform(3.0, 4.5), r.uniform(9.0, 15.0)
                        peak = r.uniform(0.3, 0.5) * (0.4 + 0.6 * s)
                        g = r.uniform(0.17, 0.24)
                        tint = (g, g * 0.98, g * 0.95)

                        def motion(tau, u=u, life=life, rise=rise, size0=size0, size1=size1, peak=peak):
                            k = tau / life
                            # Boiling up off the jet's footprint round the hull's edge, leaning downwind.
                            off = u * (6.0 + 10.0 * (1 - math.exp(-tau))) + wind * 1.6 * tau + Vector((0, 0, 2.0 + rise * (1 - math.exp(-tau / 1.6))))
                            sz = size0 + (size1 - size0) * smoothstep(0.0, 0.8, k)
                            a = peak * smoothstep(0.0, 0.2, tau) * (1 - smoothstep(0.25, 1.0, k))
                            return off, (sz, sz, sz * 0.7), 0.0, a
                        spawn('Landing dust smoke %d' % n[kind], ts, p, centre, life, motion, tint)

    # Settling: a broad, thin pall over the pad from touchdown, drifting off and gone by the join.
    centre = track[f_b][0]
    for i in range(14):
        ts = T_DOWN - 0.6 + r.uniform(0.0, 1.0)
        ang = r.uniform(0, 2 * math.pi)
        d = r.uniform(20.0, 70.0)
        p = Vector((centre.x + d * math.cos(ang), centre.y + d * math.sin(ang), 2.0))
        life = gone - ts
        size = r.uniform(26.0, 40.0)
        peak = r.uniform(0.08, 0.14)

        def motion(tau, life=life, size=size, peak=peak):
            k = tau / life
            a = peak * smoothstep(0.0, 0.25, k) * (1 - smoothstep(0.35, 1.0, k))
            sz = size * (0.8 + 0.4 * k)
            return wind * tau + Vector((0, 0, 1.5 * tau)), (sz, sz, sz * 0.22), 0.0, a
        spawn('Landing dust pall %d' % i, ts, p, centre, life, motion, (0.42, 0.36, 0.28))

    # The jets' own light on the slab and the dust they kick up, under the hull's edge.
    for j, _ in enumerate(nozzles):
        lamp = link(bpy.data.objects.new('Landing dust glow %d' % j, bpy.data.lights.new('Landing dust glow %d' % j, 'POINT')))
        lamp.data.color = (0.62, 0.78, 1.0)
        lamp.data.shadow_soft_size = 2.0
        for f in [f_a - 1] + list(range(f_a, f_b + 1, 2)) + [f_b + 1]:
            p, s, h = track[min(max(f, f_a), f_b)][1][j]
            lamp.location = p + Vector((0, 0, 1.5))
            lamp.data.energy = 0.0 if f < f_a or f > f_b else 1.2e4 * s
            lamp.keyframe_insert('location', frame=f)
            lamp.data.keyframe_insert('energy', frame=f)
    print('LANDING_DUST', n, 'nozzles', len(nozzles), flush=True)


def add_cargo_flow(r):
    """Freighters unload from the stern: containers set down on the apron, carriers shuttling them
    to the container yard."""
    yellow = bpy.data.materials['Crane yellow']
    cmats = [bpy.data.materials['Container %d' % i] for i in range(6)]
    lay = [bmesh.new() for _ in cmats]
    n = 0
    h = ROW_HEADING
    for x, off, who in BERTHS:
        cx, cy = x, BERTH_Y + BERTH_DY[x]
        rad = BERTH_R
        s0 = at(cx, cy, h, -(rad + 4), 0)
        if who == 'ferry':
            n += 1
            continue
        lane = bmesh.new()                                          # straight back to the container yard
        box(lane, (16, s0[1] - 342.0, 0.02), (s0[0], (s0[1] + 342.0) / 2, 0.09))
        mesh('Cargo lane %d' % n, lane, bpy.data.materials['Asphalt'])
        if who not in ('hound', 'unloading', 'fuelling'):
            n += 1
            continue
        for i in range(3):                                          # containers set down off the stern
            for j in (-1, 1):
                box(lay[r.randrange(6)], (12, 2.4, 2.6), at(cx, cy, h, -(rad + 8), j * (10 + i * 3.2), 1.3), h)
        lats = (-5.0, 0.0, 5.0) if who == 'unloading' else (-4.0, 4.0)
        if who == 'fuelling':
            vehicle('Port bowser %d' % n, bowser_parts(bpy.data.materials['Hauler load']),
                    [(0,) + at(cx, cy, h, 4, -(rad + 6))[:2]], [(0, h)])
        for k, lat in enumerate(lats):
            shuttle('Carrier %d %d' % (n, k),
                    [((12.5, 0.8, 1.0), (0, 2.4, 6.5), yellow), ((12.5, 0.8, 1.0), (0, -2.4, 6.5), yellow),
                     ((12, 2.4, 2.6), (0, 0, 1.6), cmats[r.randrange(6)])],
                    (s0[0] + lat, s0[1]), (s0[0] + lat, 345.0), 9.0, 2.5, k / len(lats) + 0.17 * n)
        n += 1
    # Containers staged on the heavy berth's apron for an inbound, carriers waiting in line.
    hx0, hx1, hy0, hy1 = HEAVY
    for i in range(4):
        for j in range(3):
            for lvl in range(r.randrange(1, 3)):
                box(lay[r.randrange(6)], (12, 2.4, 2.6), (hx0 + 30 + i * 18, hy0 - 10 - j * 4, 1.3 + 2.6 * lvl))
    for k in range(3):
        vehicle('Waiting carrier %d' % k,
                [((12.5, 0.8, 1.0), (0, 2.4, 6.5), yellow), ((12.5, 0.8, 1.0), (0, -2.4, 6.5), yellow)],
                [(0, 64, hy0 - 10 - k * 16)], [(0, math.pi / 2)])
    for bm, m in zip(lay, cmats):
        mesh('Apron laydown ' + m.name, bm, m)


# When the yard's vehicles leave their stand-off and when they stop at the hull, in C4 seconds. They
# move off as the dust thins at the end of C4 and are still rolling in when C5 opens, so the crews
# climb out on screen in C5 (`c5_scene.cast`). The customs car comes in last, behind them.
K9_ROLL_IN = {n: (go + LATE, stop + LATE) for n, (go, stop) in
              {'Tug': (14.2, 18.5), 'Crew carrier': (14.5, 19.5), 'Fuel bowser': (14.8, 20.5)}.items()}
CUSTOMS_ARRIVE = 21.0 + LATE


def add_k9_vehicles():
    """Nessa's tug, bowser and crew carrier roll out once the ship is close; customs comes over
    from the public side with its lights going and turns in at her gate as the ship settles."""
    yellow = bpy.data.materials['Crane yellow']
    white = bpy.data.materials['Hauler load']
    cream = bpy.data.materials['Truck cream']
    kx, ky = K9
    kh = k9_heading()
    # Out of the hangar and the workshop to a stand-off clear of the downwash, then in to the hull
    # once the dust thins.
    sec = lambda t: round((t + LATE) * FPS)              # timed against the first cut, moved by LATE
    for name, parts, a, s, (u1, v1) in (
            ('Tug', tug_parts(yellow), (342, 528), (342, 507), (-46, 22)),
            ('Fuel bowser', bowser_parts(white), (470, 405), (462, 418), (0, -40)),
            ('Crew carrier', carrier_parts(cream), (322, 430), (336, 430), (-50, 4))):
        xb, yb, _ = at(kx, ky, kh, u1, v1)
        go, stop = (t - LATE for t in K9_ROLL_IN[name])
        vehicle(name, parts, [(0,) + a, (sec(5.0),) + a, (sec(8.0),) + s, (sec(go),) + s, (sec(stop), xb, yb)],
                [(0, math.atan2(s[1] - a[1], s[0] - a[0])), (sec(go), math.atan2(s[1] - a[1], s[0] - a[0])),
                 (sec(go + 0.6), math.atan2(yb - s[1], xb - s[0]))])
    # Yard hands in hi-vis, out from the hangar to the scaffold once it is down, arriving in C5. The ramp crew rides in on the crew carrier and gets out in C5
    # (`c5_scene.cast`); the first seven draws were theirs on foot, kept so the rest stay put.
    hivis = material('Hi-vis', (1.0, 0.55, 0.08), 0.7)
    r = random.Random(57)
    for n in range(10):
        xa, ya = (334 + r.uniform(-6, 6), 418 + r.uniform(-1, 3)) if n < 7 else (346 + r.uniform(-4, 4), 515)
        if n < 7:
            xb, yb, _ = at(kx, ky, kh, -38 + r.uniform(-4, 4), r.uniform(-12, 12))   # at the stern ramp
        else:
            xb, yb, _ = at(kx, ky, kh, r.uniform(-10, 10), 52)                       # the north scaffold
        go = 11.8 + r.uniform(0, 1.0)
        walk = math.hypot(xb - xa, yb - ya) / 2.2
        if n < 7:
            continue
        vehicle('Scaffold hand %d' % n, [((1.3, 1.3, 1.8), (0, 0, 0.9), hivis)],     # on through C5
                [(0, xa, ya), (sec(go), xa, ya), (sec(go + walk), xb, yb)])
    # Scaffold arms swing in to the hull, and work starts on it: arc-light at the decks.
    arms = material('Scaffold arms', (0.85, 0.70, 0.15), 0.6)
    arc = material('Arc light', (0.7, 0.85, 1.0))
    ap = arc.node_tree.nodes['Principled BSDF']
    ap.inputs['Emission Color'].default_value = (0.7, 0.85, 1.0, 1)
    for side in (-1, 1):
        base = at(kx, ky, kh, 8, side * (K9_R + 7))
        bm = bmesh.new()
        box(bm, (20, 2.2, 1.6), (10, 0, 0))
        ob = mesh('Scaffold arm %d' % side, bm, arms)
        ob.location = (base[0], base[1], 21)
        for t, a in ((0.0, kh), (12.8, kh), (14.4, kh - side * math.pi / 2)):
            ob.rotation_euler = (0, 0, a)
            ob.keyframe_insert('rotation_euler', frame=sec(t))
        for j in range(2):
            bm = bmesh.new()
            box(bm, (0.8, 0.8, 0.8), (0, 0, 0))
            sp = mesh('Arc %d %d' % (side, j), bm, arc)
            sp.location = at(kx, ky, kh, -6 + j * 18, side * 30, 14 + j * 4)
        for f in range(0, FRAMES, 2):
            on = f >= sec(14.8) and random.Random(f * 7 + side).random() < 0.55
            ap.inputs['Emission Strength'].default_value = 400.0 if on else 0.0
            ap.inputs['Emission Strength'].keyframe_insert('default_value', frame=f)
    lights = material('Customs lights', (1.0, 0.6, 0.2))
    lp = lights.node_tree.nodes['Principled BSDF']
    lp.inputs['Emission Color'].default_value = (1.0, 0.62, 0.25, 1)
    for f in range(0, FRAMES + round(41.0 * FPS), 6):   # flashing, on into C5 (40 s)
        lp.inputs['Emission Strength'].default_value = 120.0 if (f // 6) % 2 else 15.0
        lp.inputs['Emission Strength'].keyframe_insert('default_value', frame=f)
    car = vehicle('Customs car', car_parts(material('Customs white', (0.88, 0.88, 0.86), 0.4))
                  + [((0.3, 1.6, 0.18), (0.1, 0, 1.86), lights)],
            [(0, OFFICE[0] - 8, 392), (sec(7.0), OFFICE[0] - 8, 392), (sec(7.8), 252, 372), (sec(9.2), 300, 372),
             (sec(9.8), 305, 384), (sec(10.8), 305, 424), (sec(14.0), 305, 430), (sec(CUSTOMS_ARRIVE - LATE), 355, 428)],
            [(0, -0.9), (sec(7.1), -0.9), (sec(7.8), 0.0), (sec(9.2), 0.0), (sec(9.8), math.pi / 2),
             (sec(14.0), math.pi / 2), (sec(14.6), 0.0)])
    # No point light with the bar: in daylight a 200 kW beacon 2 m over a white roof blew the car out
    # to a glowing white block in C5 (owner, 2026-10-19). The flashing bar itself is the signal.
    vehicle('MuniSec car at the gate', car_parts(bpy.data.materials['Customs white']),
            [(0, -250, -104)], [(0, 0.3)])


def add_port_traffic():
    """Real 3D hulls at the port and over it: `port_fleet.py` says which hull works each berth, the
    Colossus in the heavy berth, the Hermes lighters, and the ships lifting and letting down."""
    if str(BASE) not in sys.path:
        sys.path.insert(0, str(BASE))
    import port_fleet
    port_fleet.add(sys.modules[__name__])


# ---------------------------------------------------------------- light, camera, render

def add_cloud_shadows():
    m = bpy.data.materials.new('Cloud shadow')
    m.use_nodes = True
    m.surface_render_method = 'DITHERED'
    try:
        m.use_transparent_shadow = True
    except AttributeError:
        pass
    nt = m.node_tree
    pr = nt.nodes['Principled BSDF']
    coord = nt.nodes.new('ShaderNodeTexCoord')
    noise = nt.nodes.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = 0.0022
    noise.inputs['Detail'].default_value = 3.0
    ramp = nt.nodes.new('ShaderNodeMapRange')
    ramp.inputs['From Min'].default_value = 0.58
    ramp.inputs['From Max'].default_value = 0.68
    ramp.inputs['To Max'].default_value = 0.5                  # half-dark: soft cloud shade, not scorch
    nt.links.new(coord.outputs['Object'], noise.inputs['Vector'])
    nt.links.new(noise.outputs['Fac'], ramp.inputs['Value'])
    nt.links.new(ramp.outputs['Result'], pr.inputs['Alpha'])
    ob = plane('Cloud shadows', -3000, 3000, -3000, 3000, m, 0.0)
    ob.visible_camera = False
    for f, x in ((0, 0.0), (FRAMES - 1, 40.0)):
        ob.location = (x, 0, 560.0)
        ob.keyframe_insert('location', frame=f)


def add_sun():
    sun = link(bpy.data.objects.new('Sun', bpy.data.lights.new('Sun', 'SUN')))
    sun.data.energy = 3.5
    sun.data.color = SUN_RGB
    sun.data.angle = math.radians(1.0)
    sun.rotation_euler = SUN_DIR.to_track_quat('Z', 'Y').to_euler()


def add_camera(scene):
    cam = link(bpy.data.objects.new('C4x | sky camera', bpy.data.cameras.new('C4x | sky camera')))
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = LENS_MM, SENSOR_MM, 'HORIZONTAL'
    cam.data.clip_start, cam.data.clip_end = 5.0, 60000.0
    pan = round(PAN * FPS)
    for f in range(FRAMES):
        if f < pan:
            u = f / (pan - 1)
            k = 0.35 * u + 0.65 * smoothstep(0.0, 1.0, u)   # under way from the first frame, easing out
            drift = smoothstep(0.35, 1.0, u)               # east after the ship, over the second half
            down = smoothstep(0.45, 1.0, u)                # and down toward the yard
            cam.location = (CAM_END.x * drift, CAM_Y0 + (CAM_END.y - CAM_Y0) * k,
                            CAM_POS.z + (CAM_END.z - CAM_POS.z) * down)
        else:                                              # lingering: a slow drift north over the yard
            cam.location = CAM_END.lerp(CAM_LINGER, smoothstep(pan - 1, FRAMES - 1, f))
            down = 1.0
        cam.rotation_euler = (math.pi / 2 + CAM_PITCH + (-math.pi / 2 - CAM_PITCH) * down, 0, 0)  # to straight down
        cam.keyframe_insert('location', frame=f)
        cam.keyframe_insert('rotation_euler', frame=f)
    scene.camera = cam


def build():
    scene = fresh_scene()
    add_sky(scene)
    add_sun()
    add_farmland()
    add_yard()
    add_road_traffic()
    add_detail()
    add_port()
    add_steam()
    add_port_traffic()
    add_cloud_shadows()
    add_landing_dust(add_ship())
    if str(BASE) not in sys.path:
        sys.path.insert(0, str(BASE))
    import port_life                                    # the public port at work, on through C5
    import port_fleet                                   # its hulls (placed by add_port_traffic)
    port_life.add(sys.modules[__name__])
    import c4_farm                                       # the farmland at work: field detail, machines, traffic
    c4_farm.add(sys.modules[__name__])
    port_life.after_farm(sys.modules[__name__])         # the farm's road traffic on through C5
    import port_detail                                  # the public port's standing clutter
    port_detail.add(sys.modules[__name__])
    import c4_jungle                                    # layered canopy past the fence
    c4_jungle.apply(YARD[1], 768.0)
    import vehicle_paint                                 # painted vehicle tops, once accepted
    vehicle_paint.apply()
    import ortho_paint                                  # painted overhead tiles, where accepted
    manifest = json.loads((STATES / 'manifest.json').read_text())
    ship = bpy.data.collections.get(manifest['append_collection'])
    ortho_paint.apply(scene, {o.name for o in ship.all_objects} if ship else ())
    import wall_paint                                   # painted walls and roof plant
    wall_paint.apply(WAREHOUSES)
    add_haze(skip={'Steam', 'Drive plume', 'Customs lights', 'Cloud shadow', 'Landing dust', 'Arc light'} | c4_farm.HAZE_SKIP
             | port_life.HAZE_SKIP | port_fleet.HAZE_SKIP)
    add_camera(scene)
    sys.path.insert(0, str(CINE / 'thrust-markers'))
    import thrust_shots                                  # every craft's nozzles marked, through C5
    thrust_shots.mark_c4(sys.modules[__name__])
    port_fleet.after_markers(sys.modules[__name__])     # the departing Colossus's burn, off its markers
    scene.render.engine = 'BLENDER_EEVEE'
    scene.eevee.taa_render_samples = 6     # layout pass: speed over smoothness
    scene.render.use_persistent_data = True   # keep the synced scene and shaders between frames
    scene.eevee.shadow_resolution_scale = 0.5
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'
    scene.view_settings.view_transform = 'AgX'
    try:
        scene.view_settings.look = 'AgX - Medium High Contrast'
    except TypeError:
        pass
    return scene


def main():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    scene = build()
    OUT.mkdir(parents=True, exist_ok=True)
    if '--save' in argv:
        bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'c4x-layout.blend'))
    if '--still' in argv:
        frames = [min(FRAMES - 1, round(float(t) * FPS)) for t in argv[argv.index('--still') + 1].split(',')]
        target = OUT / 'stills'
    else:
        a, b = (0, FRAMES - 1)
        if '--frames' in argv:
            a, b = (int(x) for x in argv[argv.index('--frames') + 1].split(':'))
        step = int(argv[argv.index('--step') + 1]) if '--step' in argv else 1
        frames = range(a, b + 1, step)
        target = OUT / 'frames'
    target.mkdir(parents=True, exist_ok=True)
    if '--still' in argv:
        for f in frames:
            scene.frame_set(f)
            scene.render.filepath = str(target / ('%05d.png' % f))
            bpy.ops.render.render(write_still=True)
            print('C4X_FRAME', f, flush=True)
        return
    # One animation job: the scene is synced once and kept between frames.
    for old in target.glob('*.png'):
        old.unlink()
    scene.frame_start, scene.frame_end, scene.frame_step = frames.start, frames.stop - 1, frames.step
    scene.render.filepath = str(target / 'f#####')
    bpy.ops.render.render(animation=True)
    for i, f in enumerate(frames):            # a contiguous sequence for ffmpeg, whatever the step
        (target / ('f%05d.png' % f)).rename(target / ('%05d.png' % i))


if __name__ == '__main__':
    main()
