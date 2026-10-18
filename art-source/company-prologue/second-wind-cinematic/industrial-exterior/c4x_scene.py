"""C4 exterior: the camera in the sky over Greywater's industrial belt; Second Wind passes beneath.

    blender --background --factory-startup --python c4x_scene.py -- [--still t,...] [--frames a:b] [--step n] [--save]

Layout pass (EEVEE, block geometry). The camera hangs about 700 m up, looking nearly straight
down, and pans toward the port: it starts over the farmland and crosses the edge of the
industrial belt (hedges and fence, freight road, rail sidings, warehouses, container yard) to
ease to a stop over the cradles. Second Wind comes in beneath it faster than the camera moves,
pulls ahead and sinks toward the empty cradle, its shadow closing on it as it descends. The
two speeds keep the ground, the ship and the shadow all moving against each other.

World frame, metres: +Y toward the port; +Z up; ground at z = 0.
Inputs: build/c1v2/inputs/{second-wind-patched,mudskipper-skinned,hound-skinned}.blend.
Outputs under build/c4x/.
"""
import json
import math
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
DURATION = 8.0
FRAMES = round(DURATION * FPS)
W, H = 1920, 1080
LENS_MM, SENSOR_MM = 24.0, 36.0
CAM_POS = Vector((0.0, 150.0, 700.0))   # where haze distances are measured from (mid-pan)
CAM_Y0, CAM_Y1 = -470.0, 300.0          # pan along +Y: over the fields, then over the port's edge
CAM_PITCH = math.radians(-85.0)          # nearly straight down, a touch toward the port
SUN_DIR = Vector((-0.45, -0.20, 0.87)).normalized()   # toward the sun: high, left, behind
SUN_RGB = (1.0, 0.95, 0.88)
HAZE_RGB = (0.72, 0.80, 0.88)
HAZE_STRENGTH = 0.6
HAZE_DIST = 9000.0                       # metres for 1/e of a surface's own contrast

rng = random.Random(7)


# ---------------------------------------------------------------- scene, sky, haze

def fresh_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.resolution_x, scene.render.resolution_y = W, H
    scene.render.resolution_percentage = 100
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
            nt.links.new(wave.outputs['Fac'], mix.inputs['Factor'])
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
    plane('Drainage ditch', -2000, 2000, -170, -160, material('Ditch water', (0.10, 0.14, 0.13), 0.2), 0.04)
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
    concrete = material('Concrete', (0.46, 0.45, 0.42), 0.9)
    ballast = material('Ballast', (0.20, 0.18, 0.17), 0.95)
    plane('Freight road', -2000, 2000, -95, -75, asphalt, 0.05)
    for y in (-40, -25, -10):
        plane('Siding %d' % y, -2000, 2000, y - 2, y + 2, ballast, 0.05)
    plane('Container yard', -600, 600, 215, 335, concrete, 0.05)
    plane('Port apron', -2000, 2000, 360, 2500, concrete, 0.04)
    for x in (-300, 0, 300):
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
    for row in range(6):
        y = 225 + row * 18
        x = -560
        while x < 560:
            if rng.random() < 0.85:
                for level in range(rng.randrange(1, 4)):
                    box(cms[rng.randrange(len(colours))], (12, 2.4, 2.6),
                        (x + 6, y + rng.choice((-2, 2)), 1.3 + 2.6 * level))
            x += 12.6
    for i, (bm, c) in enumerate(zip(cms, colours)):
        mesh('Containers %d' % i, bm, material('Container %d' % i, c, 0.6, 0.2))
    bm = bmesh.new()
    for x in (-380, -120, 150, 410):
        gantry_crane(bm, x, 280, 34, 44)
    mesh('Yard cranes', bm, crane)
    # Tank farm and plant at the west end; stacks.
    bm, bt = bmesh.new(), bmesh.new()
    for i in range(3):
        for j in range(2):
            cylinder(bt, 11, 16, (-760 + i * 28, 40 + j * 28, 0), 24)
    for x, y, h in ((-640, 150, 70), (520, 60, 85)):
        stack(bm, x, y, h, 4)
        STACKS.append((x, y, h))
    box(bm, (30, 18, 42), (-660, 120, 21))
    mesh('Plant', bm, steel)
    mesh('Tank farm', bt, tanks)
    # Port cradles beyond the yard.
    bm = bmesh.new()
    for cx, cy in ((-260, 470), (220, 450), (480, 420)):
        cylinder(bm, 70, 4, (cx, cy, 0), 40)
        for a in range(6):
            ang = a * math.pi / 3
            box(bm, (6, 6, 26), (cx + 76 * math.cos(ang), cy + 76 * math.sin(ang), 13))
    mesh('Cradles', bm, concrete)


def vehicle(name, parts, keys, heading_keys=None):
    """A small vehicle: (size, centre, material) boxes under one root, keyed (frame, x, y)."""
    root = link(bpy.data.objects.new(name, None))
    for i, (size, centre, mat) in enumerate(parts):
        bm = bmesh.new()
        box(bm, size, centre)
        mesh('%s %d' % (name, i), bm, mat).parent = root
    for f, x, y in keys:
        root.location = (x, y, 0)
        root.keyframe_insert('location', frame=f)
    for f, h in (heading_keys or [(0, 0.0)]):
        root.rotation_euler = (0, 0, h)
        root.keyframe_insert('rotation_euler', frame=f)
    return root


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
    # Harvest: a combine working down a field with its grain truck alongside.
    green = material('Combine green', (0.45, 0.55, 0.10), 0.5)
    cream = material('Truck cream', (0.80, 0.76, 0.62), 0.5)
    vehicle('Combine', [((9, 4.5, 4), (0, 0, 2.5), green), ((1.6, 12, 1.2), (5.5, 0, 0.9), green)],
            [(0, 150, -1100), (FRAMES - 1, 150 + 3.0 * DURATION, -1100)])
    vehicle('Grain truck', [((3, 3, 3), (2.5, 0, 2), cream), ((8, 3, 2.6), (-3.5, 0, 2), cream)],
            [(0, 150, -1112), (FRAMES - 1, 150 + 3.0 * DURATION, -1112)])
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
    # Port: painted rings on the cradles, Kilo Nine's marked, and its ground crew driving out.
    paint = material('Cradle paint', (0.85, 0.70, 0.15), 0.6)
    bm = bmesh.new()
    for cx, cy in ((-260, 470), (220, 450), (480, 420)):
        for a in range(48):
            ang = 2 * math.pi * a / 48
            box(bm, (5.8, 1.6, 0.1), (cx + 58 * math.cos(ang), cy + 58 * math.sin(ang), 4.05), ang + math.pi / 2)
    for dx in (-14, 0, 14):                                  # three bars: the cradle's marking
        box(bm, (3, 30, 0.1), (220 + dx, 450, 4.05))
    mesh('Cradle markings', bm, paint)
    yellow = bpy.data.materials['Crane yellow']
    t0, t1 = round(3.0 * FPS), FRAMES - 1
    for name, parts, (x0, y0), (x1, y1) in (
            ('Tug', [((8, 4, 2.4), (0, 0, 1.4), yellow)], (60, 365), (150, 420)),
            ('Fuel bowser', [((3, 3, 3), (5, 0, 2), white), ((9, 3, 3), (-1.5, 0, 2), white)], (20, 372), (140, 404)),
            ('Crew carrier', [((10, 3, 3.2), (0, 0, 2), cream)], (-20, 368), (125, 395))):
        heading = math.atan2(y1 - y0, x1 - x0)
        vehicle(name, parts, [(0, x0, y0), (t0, x0, y0), (t1, x1, y1)], [(0, heading)])


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
P0 = Vector((-420.0, -900.0, 340.0))
P1 = Vector((220.0, 450.0, 70.0))     # over the empty cradle


def ship_path(t):
    k = t / DURATION
    p = P0.lerp(P1, 1 - (1 - k) ** 1.4)
    p.z = P0.z + (P1.z - P0.z) * (k ** 1.3)
    return p


def add_ship():
    manifest = json.loads((STATES / 'manifest.json').read_text())
    append_collection(INPUTS / 'second-wind-patched.blend', manifest['append_collection'])
    root = bpy.data.objects[manifest['root']]
    root.scale = (manifest['metres_per_unit'],) * 3
    root.rotation_mode = 'XYZ'
    glow = material('Drive plume', (0.6, 0.8, 1.0))
    glow.node_tree.nodes['Principled BSDF'].inputs['Emission Color'].default_value = (0.6, 0.8, 1.0, 1)
    glow.node_tree.nodes['Principled BSDF'].inputs['Emission Strength'].default_value = 60.0
    for i in manifest['engines_running']:
        anchor = bpy.data.objects.get('Engine %d exhaust anchor' % i)
        if anchor is None:
            continue
        light = bpy.data.objects.new('Drive %d glow' % i, bpy.data.lights.new('Drive %d glow' % i, 'POINT'))
        light.data.energy = 6.0e5
        light.data.color = (0.55, 0.75, 1.0)
        light.parent = anchor
        light.location = (3.0, 0, 0)
        link(light)
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=8, radius=1.0)
        core = mesh('Drive %d core' % i, bm, glow)
        core.parent = anchor
        core.location = (2.5, 0, 0)
        core.scale = (5.0, 2.2, 2.2)
    dt = 1.0 / FPS
    for f in range(FRAMES):
        t = f / FPS
        p = ship_path(t)
        v = ship_path(min(DURATION, t + dt)) - ship_path(max(0.0, t - dt))
        heading = math.atan2(v.y, v.x)
        descent = math.atan2(v.z, v.xy.length)
        pitch = 0.45 * descent + math.radians(1.0) * math.sin(2 * math.pi * t / 5.0)
        roll = math.radians(-6.0) + math.radians(2.0) * math.sin(2 * math.pi * t / 7.0)
        root.location = p
        root.rotation_euler = attitude(heading, pitch, roll)
        root.keyframe_insert('location', frame=f)
        root.keyframe_insert('rotation_euler', frame=f)
    return manifest


def add_port_traffic():
    """Real 3D hulls at the port: a Hound parked in a cradle, a Mudskipper lifting out."""
    def instancer(path, mpu):
        coll = append_collection(path, 'Collection')
        bpy.context.view_layer.layer_collection.children[coll.name].exclude = True

        def place(label):
            e = link(bpy.data.objects.new(label, None))
            e.instance_type = 'COLLECTION'
            e.instance_collection = coll
            e.scale = (mpu,) * 3
            return e
        return place
    mud = instancer(INPUTS / 'mudskipper-skinned.blend', 0.85)
    hound = instancer(INPUTS / 'hound-skinned.blend', 0.88)
    parked = hound('Hound parked')
    parked.location = (-260, 470, 12)
    parked.rotation_euler = (0, 0, 0.7)
    lifter = mud('Mudskipper lifting off')
    lifter.rotation_euler = (0, 0, 2.0)
    for f, z in ((0, 12.0), (FRAMES - 1, 12.0 + 8.0 * DURATION)):
        lifter.location = (480, 420, z)
        lifter.keyframe_insert('location', frame=f)


# ---------------------------------------------------------------- light, camera, render

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
    cam.rotation_euler = (math.pi / 2 + CAM_PITCH, 0, 0)
    for f in range(FRAMES):
        k = smoothstep(0.0, 1.0, f / (FRAMES - 1))
        k = 0.35 * (f / (FRAMES - 1)) + 0.65 * k     # under way from the first frame, easing out
        cam.location = (0.0, CAM_Y0 + (CAM_Y1 - CAM_Y0) * k, CAM_POS.z)
        cam.keyframe_insert('location', frame=f)
    scene.camera = cam


def build():
    scene = fresh_scene()
    add_sky(scene)
    add_sun()
    add_farmland()
    add_yard()
    add_road_traffic()
    add_detail()
    add_steam()
    add_port_traffic()
    add_ship()
    add_haze(skip={'Steam', 'Drive plume'})
    add_camera(scene)
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
    for i, f in enumerate(frames):
        scene.frame_set(f)
        name = f if '--still' in argv else i     # a contiguous sequence for ffmpeg, whatever the step
        scene.render.filepath = str(target / ('%05d.png' % name))
        bpy.ops.render.render(write_still=True)
        print('C4X_FRAME', f, flush=True)


if __name__ == '__main__':
    main()
