"""C4 exterior: the camera in the sky over Greywater's industrial belt; Second Wind passes beneath.

    blender --background --factory-startup --python c4x_scene.py -- [--still t,...] [--frames a:b] [--step n] [--save]

Layout pass (EEVEE, block geometry). The camera hangs about 700 m up, looking nearly straight
down, and pans toward the port: it starts over the farmland and crosses the edge of the
industrial belt (hedges and fence, freight road, rail sidings, warehouses, container yard) to
ease to a stop over the port. Second Wind comes in beneath it faster than the camera moves,
pulls ahead and sinks toward Kilo Nine, its shadow closing on it as it descends. The two speeds
keep the ground, the ship and the shadow all moving against each other.

How the port works, as the picture should explain it: the Hegemony's public cradles each face
the way their lane brings ships in (the painted centre line says which), with a service tower,
fuel, supply modules and steel stock beside each. Freighters on them unload from the stern;
carriers shuttle containers to the container yard, whose cranes load the rail and the haulers
out to the belt. Kilo Nine is not a public cradle: it is Nessa's private yard, fenced, on the
port's east edge against the jungle, with repair scaffold, a derrick over the salvage, a hangar
and the workshop the families will walk into. Her tug and bowser roll out to meet the ship, and
a Hegemony customs car comes over from the public side, lights going.

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
CAM_Y0, CAM_Y1 = -470.0, 400.0          # pan along +Y: over the fields, then over the port's edge
CAM_X1 = 80.0                           # drifting east with the ship as it settles
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
    concrete = material('Concrete', (0.40, 0.39, 0.36), 0.9)
    ballast = material('Ballast', (0.20, 0.18, 0.17), 0.95)
    plane('Freight road', -2000, 2000, -95, -75, asphalt, 0.05)
    for y in (-40, -25, -10):
        plane('Siding %d' % y, -2000, 2000, y - 2, y + 2, ballast, 0.05)
    plane('Container yard', -600, 600, 215, 335, concrete, 0.05)
    plane('Port apron', -2000, 2000, 360, 2500, material('Apron', (0.27, 0.27, 0.26), 0.9), 0.04)
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
    vehicle('Robotic harvester', [((26, 14, 7), (0, 0, 4), green), ((3, 40, 2.4), (15, 0, 1.6), green)],
            [(0, 150, -1100), (FRAMES - 1, 150 + 3.0 * DURATION, -1100)])
    vehicle('Grain truck', [((3, 3, 3), (2.5, 0, 2), cream), ((8, 3, 2.6), (-3.5, 0, 2), cream)],
            [(0, 150, -1128), (FRAMES - 1, 150 + 3.0 * DURATION, -1128)])
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
P0 = Vector((-420.0, -900.0, 340.0))
P1 = Vector((405.0, 470.0, 70.0))     # over Kilo Nine (K9)


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


# ---------------------------------------------------------------- the port

# The public port, laid out in rows by size. Capital ships and cruisers never land; their cargo
# comes down in lighters. Every berth in a row faces the same way, turned to the approach
# corridor, and ships settle a few degrees off their markings.
ROW_HEADING = math.radians(98.0)
# Freighter berths: (x, settle offset in degrees, occupant). Sterns face the container yard. The
# east berth is the passenger ferry's, beside the arrivals hall.
BERTH_Y, BERTH_R = 530.0, 34.0
BERTHS = [(-400.0, 3.0, 'hound'), (-310.0, -2.0, 'hound'), (-220.0, 0.0, 'hound'),
          (-130.0, 4.0, 'empty'), (-40.0, -4.0, 'hound'), (50.0, 0.0, 'hound'), (140.0, 1.5, 'ferry')]
# Lighter pads, two in each gap between the freighters' cargo lanes beside the port road: Hermes
# shuttles bringing cargo down from ships at Jangala Station and taking it up.
LIGHTER_Y, LIGHTER_R = 412.0, 8.0
_LIGHTER_STATES = ['parked', 'parked', 'lifting', 'parked', 'empty', 'landing', 'parked', 'parked',
                   'parked', 'lifting', 'parked', 'empty', 'parked', 'landing', 'parked', 'parked']
LIGHTERS = [(gx + dx, _LIGHTER_STATES[2 * i + k])
            for i, gx in enumerate((-445.0, -355.0, -265.0, -175.0, -85.0, 5.0, 95.0))
            for k, dx in enumerate((-17.0, 17.0))]
# The heavy berth: a walled bay with a gantry for the largest hull that lands. Empty, cargo staged
# for an inbound.
HEAVY = (-215.0, -85.0, 630.0, 730.0)     # x0, x1, y0, y1
OFFICE = (245.0, 415.0)                   # arrivals: customs and decontamination, MuniSec's post
# The skinned Hound and Mudskipper carry their engines on +X: a hull facing heading h is turned
# half round so its stern, where the cargo comes out, points back down its lane.
STERN_FIRST = math.pi
# Kilo Nine, Nessa's private cradle, faces the way Second Wind comes in.
K9 = (405.0, 470.0)
YARD = (290.0, 530.0, 365.0, 590.0)       # Nessa's yard: x0, x1, y0, y1
GATE = (392.0, 410.0)                     # the gap in its west fence, onto the port road
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

    def pad(cx, cy, h, rad, lane):
        cylinder(pads, rad, 3, (cx, cy, 0), 40)
        for a in range(40):
            ang = 2 * math.pi * a / 40
            box(marks, (rad * 0.1, 1.4, 0.1), (cx + (rad - 6) * math.cos(ang), cy + (rad - 6) * math.sin(ang), 3.05),
                ang + math.pi / 2)
        box(marks, (2 * rad - 18, 1.8, 0.1), (cx, cy, 3.05), h)            # centre line: the way it faces
        box(marks, (1.8, rad * 0.8, 0.1), at(cx, cy, h, rad * 0.5, 0, 3.05), h)   # nose bar
        for k in range(lane):                                              # lead-in dashes on the apron
            box(marks, (6, 1.2, 0.1), at(cx, cy, h, -(rad + 10 + k * 11), 0, 0.1), h)

    # The public port: its road, the port office customs works from, then the rows.
    plane('Port road', -700, YARD[0], 366, 378, asphalt, 0.08)
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
    for x, who in LIGHTERS:
        pad(x, LIGHTER_Y, math.pi / 2, LIGHTER_R, 0)
        if who == 'parked':                                                 # stores and a crate or two
            for k in range(r.randrange(2, 5)):
                box(crateb, (2.2, 2.2, 2.0), (x + r.uniform(-6, 6), LIGHTER_Y - LIGHTER_R - 3 - k * 2.6, 1.0))
    h = ROW_HEADING
    for x, off, who in BERTHS:
        y = BERTH_Y
        pad(x, y, h, BERTH_R, 0)
        lattice_tower(towers, at(x, y, h, 4, BERTH_R + 10), h, (2.5, 2.5), 38)
        strut(towers, at(x, y, h, 4, BERTH_R + 10, 37), at(x, y, h, 4, BERTH_R - 12, 37), 1.4)   # service arm
        for k in (0, 1):
            cylinder(tankb, 4.5, 9, at(x, y, h, BERTH_R + 12, -18 - k * 11), 18)
        supplies((mods, stockb, crateb), r, x, y, h, BERTH_R + 6, 4)
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
        for y in (668, 686):
            strut(gc, (x, y, 0), (x, y, 46), 1.8)
    for y in (668, 686):
        box(gc, (hx1 - hx0 + 14, 2.4, 3), ((hx0 + hx1) / 2, y, 46))
    box(gc, (10, 22, 4), ((hx0 + hx1) / 2 - 20, 677, 48))                  # trolley
    mesh('Heavy berth gantry', gc, bpy.data.materials['Crane yellow'])

    # Kilo Nine: Nessa's yard, fenced, on the east edge with the jungle beyond.
    x0, x1, y0, y1 = YARD
    plane('Nessa yard', x0, x1, y0, y1, material('Yard gravel', (0.40, 0.36, 0.30), 0.95), 0.15)
    fence = bmesh.new()
    for x in range(int(x0), int(x1) + 1, 6):
        for y in (y0, y1):
            box(fence, (0.3, 0.3, 3), (x, y, 1.5))
    for y in range(int(y0), int(y1) + 1, 6):
        for x in (x0, x1):
            if x == x0 and GATE[0] < y < GATE[1]:
                continue
            box(fence, (0.3, 0.3, 3), (x, y, 1.5))
    box(fence, (x1 - x0, 0.15, 0.15), ((x0 + x1) / 2, y0, 2.8))
    box(fence, (x1 - x0, 0.15, 0.15), ((x0 + x1) / 2, y1, 2.8))
    box(fence, (0.15, y1 - y0, 0.15), (x1, (y0 + y1) / 2, 2.8))
    box(fence, (0.15, GATE[0] - y0, 0.15), (x0, (y0 + GATE[0]) / 2, 2.8))
    box(fence, (0.15, y1 - GATE[1], 0.15), (x0, (GATE[1] + y1) / 2, 2.8))
    mesh('Nessa fence', fence, bpy.data.materials['Fence steel'])
    kx, ky = K9
    kh = k9_heading()
    pad(kx, ky, kh, 38, 3)
    for side in (-1, 1):                                                   # repair scaffold either side
        lattice_tower(towers, at(kx, ky, kh, 0, side * 46), kh, (14, 3), 28, deck=deckb)
    # A derrick over the salvage, a hangar, and the workshop the families walk into.
    strut(towers, (505, 560, 0), (505, 560, 46), 1.6)
    strut(towers, (505, 560, 38), (462, 518, 50), 1.0)
    strut(towers, (505, 560, 44), (520, 575, 40), 1.0)
    strut(towers, (462, 518, 50), (462, 518, 18), 0.3)
    box(towers, (5, 5, 3), (522, 577, 39))
    hull = material('Hull plate', (0.22, 0.23, 0.24), 0.5, 0.6)
    hb = bmesh.new()
    for _ in range(16):
        box(hb, (r.uniform(6, 14), r.uniform(3, 6), 0.6), (r.uniform(440, 515), r.uniform(500, 545), r.uniform(0.5, 2.0)),
            r.uniform(0, math.pi))
    mesh('Salvage plates', hb, hull)
    hang = bmesh.new()
    box(hang, (40, 56, 20), (335, 548, 10))
    mesh('Nessa hangar', hang, bpy.data.materials['Roof rust'])
    shop = bmesh.new()
    box(shop, (28, 18, 7), (322, 392, 3.5))
    box(shop, (12, 10, 5), (345, 386, 2.5))
    mesh('Nessa workshop', shop, bpy.data.materials['Roof grey'])
    for k in (0, 1):
        cylinder(tankb, 4, 8, (508, 388 + k * 11, 0), 18)
    supplies((mods, stockb, crateb), r, kx, ky, kh, -44, 60)

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
    plane('Burn scar', x1 + 6, x1 + 26, 340, 2500, material('Burn scar', (0.08, 0.07, 0.06), 0.95), 0.13)
    plane('Jungle floor', x1 + 6, 2000, 340, 2500, material('Jungle floor', (0.05, 0.11, 0.04), 0.95), 0.12)
    jb = bmesh.new()
    for _ in range(420):
        cx, cy = r.uniform(x1 + 34, 1000), r.uniform(345, 1100)
        rad = r.uniform(6, 12)
        res = bmesh.ops.create_icosphere(jb, subdivisions=1, radius=rad)
        bmesh.ops.translate(jb, vec=(cx, cy, rad + 4), verts=res['verts'])
    mesh('Jungle canopy', jb, material('Jungle canopy', (0.05, 0.14, 0.05), 0.9))

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


def add_cargo_flow(r):
    """Freighters unload from the stern: containers set down on the apron, carriers shuttling them
    to the container yard."""
    yellow = bpy.data.materials['Crane yellow']
    cmats = [bpy.data.materials['Container %d' % i] for i in range(6)]
    lay = [bmesh.new() for _ in cmats]
    n = 0
    h = ROW_HEADING
    for x, off, who in BERTHS:
        cx, cy = x, BERTH_Y
        rad = BERTH_R
        s0 = at(cx, cy, h, -(rad + 4), 0)
        if who == 'ferry':
            n += 1
            continue
        lane = bmesh.new()                                          # straight back to the container yard
        box(lane, (16, s0[1] - 342.0, 0.02), (s0[0], (s0[1] + 342.0) / 2, 0.09))
        mesh('Cargo lane %d' % n, lane, bpy.data.materials['Asphalt'])
        if who != 'hound':
            n += 1
            continue
        for i in range(3):                                          # containers set down off the stern
            for j in (-1, 1):
                box(lay[r.randrange(6)], (12, 2.4, 2.6), at(cx, cy, h, -(rad + 8), j * (10 + i * 3.2), 1.3), h)
        for k, lat in enumerate((-4.0, 4.0)):
            shuttle('Carrier %d %d' % (n, k),
                    [((12.5, 0.8, 1.0), (0, 2.4, 6.5), yellow), ((12.5, 0.8, 1.0), (0, -2.4, 6.5), yellow),
                     ((12, 2.4, 2.6), (0, 0, 1.6), cmats[r.randrange(6)])],
                    (s0[0] + lat, s0[1]), (s0[0] + lat, 342.0), 9.0, 2.5, k / 2 + 0.17 * n)
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
                [(0, hx1 + 16, hy0 - 8 - k * 14)], [(0, 0.0)])
    for bm, m in zip(lay, cmats):
        mesh('Apron laydown ' + m.name, bm, m)


def add_k9_vehicles():
    """Nessa's tug, bowser and crew carrier roll out once the ship is close; customs comes over
    from the public side with its lights going and turns in at her gate as the ship settles."""
    yellow = bpy.data.materials['Crane yellow']
    white = bpy.data.materials['Hauler load']
    cream = bpy.data.materials['Truck cream']
    kx, ky = K9
    kh = k9_heading()
    t0, t1 = round(3.0 * FPS), FRAMES - 1
    for name, parts, (xa, ya), (u, v) in (
            ('Tug', [((8, 4, 2.4), (0, 0, 1.4), yellow)], (330, 512), (-46, 10)),
            ('Fuel bowser', [((3, 3, 3), (5, 0, 2), white), ((9, 3, 3), (-1.5, 0, 2), white)], (490, 405), (-24, -44)),
            ('Crew carrier', [((10, 3, 3.2), (0, 0, 2), cream)], (322, 408), (-42, 26))):
        xb, yb, _ = at(kx, ky, kh, u, v)
        vehicle(name, parts, [(0, xa, ya), (t0, xa, ya), (t1, xb, yb)], [(0, math.atan2(yb - ya, xb - xa))])
    lights = material('Customs lights', (1.0, 0.6, 0.2))
    lp = lights.node_tree.nodes['Principled BSDF']
    lp.inputs['Emission Color'].default_value = (1.0, 0.62, 0.25, 1)
    lp.inputs['Emission Strength'].default_value = 40.0
    vehicle('Customs car', [((5.2, 2.3, 1.5), (0, 0, 1.0), material('Customs white', (0.88, 0.88, 0.86), 0.4)),
                            ((1.4, 2.3, 0.1), (0.4, 0, 1.8), bpy.data.materials['Hegemony orange']),
                            ((0.6, 1.9, 0.35), (-0.6, 0, 1.95), lights)],
            [(0, OFFICE[0] - 8, 392), (110, OFFICE[0] - 8, 392), (128, 252, 372), (168, 280, 372), (FRAMES - 1, 300, 400)],
            [(0, -0.9), (112, -0.9), (128, 0.0), (165, 0.0), (FRAMES - 1, 0.75)])
    vehicle('MuniSec car at the gate', [((5.2, 2.3, 1.5), (0, 0, 1.0), bpy.data.materials['Customs white']),
                                        ((1.4, 2.3, 0.1), (0.4, 0, 1.8), bpy.data.materials['Hegemony orange'])],
            [(0, -250, -104)], [(0, 0.3)])


def add_port_traffic():
    """Real 3D hulls at the port: Hounds working the freighter berths, the Mudskipper ferry by the
    arrivals hall, Hermes lighters on their row, some lifting for orbit and some coming down."""
    def instancer(path, mpu):
        coll = append_collection(path, 'Collection')
        bpy.context.view_layer.layer_collection.children[coll.name].exclude = True
        # The hull files' own lamps blow out from 700 m in daylight. The ship's drive lights are
        # added after this, so at this point every lamp but the sun came from a hull file.
        for o in [o for o in bpy.data.objects if o.type == 'LIGHT' and o.name != 'Sun']:
            bpy.data.objects.remove(o)

        def place(label):
            e = link(bpy.data.objects.new(label, None))
            e.instance_type = 'COLLECTION'
            e.instance_collection = coll
            e.scale = (mpu,) * 3
            return e
        return place
    mud = instancer(INPUTS / 'mudskipper-skinned.blend', 0.85)
    hound = instancer(INPUTS / 'hound-skinned.blend', 0.88)
    hermes = instancer(CINE.parent / 'hermes-geometry' / 'hermes-geometry.blend', 0.11)
    for i, (x, off, who) in enumerate(BERTHS):
        if who == 'empty':
            continue
        e = (hound if who == 'hound' else mud)('%s at berth %d' % (who.title(), i))
        e.location = (x, BERTH_Y, 11)
        e.rotation_euler = (0, 0, ROW_HEADING + math.radians(off) + STERN_FIRST)
    for i, (x, who) in enumerate(LIGHTERS):
        if who == 'empty':
            continue
        e = hermes('Lighter %d %s' % (i, who))
        e.rotation_euler = (0, 0, math.pi / 2 + math.radians((i * 37) % 9 - 4))
        z0, z1 = {'parked': (2.5, 2.5), 'lifting': (2.5, 2.5 + 9.0 * DURATION),
                  'landing': (2.5 + 12.0 * DURATION, 2.5)}[who]
        for f, z in ((0, z0), (FRAMES - 1, z1)):
            e.location = (x, LIGHTER_Y, z)
            e.keyframe_insert('location', frame=f)

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
        cam.location = (CAM_X1 * k, CAM_Y0 + (CAM_Y1 - CAM_Y0) * k, CAM_POS.z)
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
    add_port()
    add_steam()
    add_port_traffic()
    add_ship()
    add_haze(skip={'Steam', 'Drive plume', 'Customs lights'})
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
