"""C4 life pass: working-farm traffic and structure on the managed-land flyover.

    blender --background --factory-startup --python life_pass.py -- [--still N,...] [--samples S]

Opens managed-land-realism.blend unchanged, adds a LIFE collection in the moving source-world
frame ('LAYERED | inverse source flight'), and renders the shot's four seconds (output frames
1..96 = source 240..287.5) to build/life-pass/frames. Nothing is saved back into the blend; a
copy with the pass is written to build/life-pass/life-pass.blend for inspection.

Source-world facts this relies on (measured by ray cast from the side-window camera at 240):
flat ground at source z = 8 near the window; an along-flight road at y ~ 1045 and a cross road
at x ~ -4000 meeting it; the window sees x -4710..-3000 at 240, and the world moves 159.4 m/s
in -x, so the view gains 638 m of +x over the shot. The port lies ahead in +x.
"""
import argparse
import math
import sys
from pathlib import Path

import bmesh
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
OUT = HERE / 'build' / 'life-pass'
GROUND_Z = 8.0
ROAD_Y = 1045.0
CROSS_X = -4000.0
SPEED = 159.4          # source m/s of the camera along +x
T0 = 240               # first source frame
FPS = 12               # source timeline rate

parser = argparse.ArgumentParser()
parser.add_argument('--still', help='comma-separated output frames 1..96 to render')
parser.add_argument('--samples', type=int, default=32)
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])

bpy.ops.wm.open_mainfile(filepath=str(HERE / 'managed-land-realism.blend'))
scene = bpy.context.scene
scene.camera = bpy.data.objects['03 LAND / side window']
for marker in scene.timeline_markers:
    marker.camera = None
ROOT = bpy.data.objects['LAYERED | inverse source flight']
bpy.context.preferences.edit.keyframe_new_interpolation_type = 'LINEAR'

LIFE = bpy.data.collections.new('LIFE | working farm')
scene.collection.children.link(LIFE)


# ---------------------------------------------------------------- building blocks

def material(name, rgb, rough=0.7, metal=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    p = m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*rgb, 1.0)
    p.inputs['Roughness'].default_value = rough
    p.inputs['Metallic'].default_value = metal
    return m


def mesh_object(name, bm, mat, parent):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    LIFE.objects.link(ob)
    ob.parent = parent
    return ob


def box(bm, size, centre, rot_z=0.0):
    geom = bmesh.ops.create_cube(bm, size=1.0)['verts']
    m = Matrix.Translation(centre) @ Matrix.Rotation(rot_z, 4, 'Z') @ Matrix.Diagonal((*size, 1.0))
    bmesh.ops.transform(bm, matrix=m, verts=geom)


def strut(bm, a, b, thick):
    """A square bar from a to b."""
    a, b = Vector(a), Vector(b)
    d = b - a
    geom = bmesh.ops.create_cube(bm, size=1.0)['verts']
    rot = d.to_track_quat('Z', 'Y').to_matrix().to_4x4()
    m = Matrix.Translation((a + b) / 2) @ rot @ Matrix.Diagonal((thick, thick, d.length, 1.0))
    bmesh.ops.transform(bm, matrix=m, verts=geom)


def frame_empty(name, parent=ROOT):
    e = bpy.data.objects.new(name, None)
    LIFE.objects.link(e)
    e.parent = parent
    return e


def drive(ob, start, velocity, f0=T0 - 40, f1=T0 + 90, at=T0):
    """Constant source-world velocity (m/s) through `start` at source frame `at`."""
    start, velocity = Vector(start), Vector(velocity)
    for f in (f0, f1):
        ob.location = start + velocity * ((f - at) / FPS)
        ob.keyframe_insert('location', frame=f)


def haze_material(name, rgb, opacity):
    """Soft dust/mist card: noisy alpha falling off from the card's centre to its rim."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.surface_render_method = 'BLENDED'
    m.use_backface_culling = False
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*rgb, 1.0)
    p.inputs['Roughness'].default_value = 1.0
    coord = nt.nodes.new('ShaderNodeTexCoord')
    centre = nt.nodes.new('ShaderNodeVectorMath')
    centre.operation = 'MULTIPLY_ADD'
    centre.inputs[1].default_value = (2.0, 2.0, 0.0)
    centre.inputs[2].default_value = (-1.0, -1.0, 0.0)
    length = nt.nodes.new('ShaderNodeVectorMath')
    length.operation = 'LENGTH'
    fall = nt.nodes.new('ShaderNodeMapRange')
    fall.inputs['From Min'].default_value = 1.0
    fall.inputs['From Max'].default_value = 0.1
    noise = nt.nodes.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = 0.08
    noise.inputs['Detail'].default_value = 6.0
    shape = nt.nodes.new('ShaderNodeMapRange')
    shape.inputs['From Min'].default_value = 0.35
    shape.inputs['From Max'].default_value = 0.7
    mul = nt.nodes.new('ShaderNodeMath')
    mul.operation = 'MULTIPLY'
    mul2 = nt.nodes.new('ShaderNodeMath')
    mul2.operation = 'MULTIPLY'
    mul2.inputs[1].default_value = opacity
    nt.links.new(coord.outputs['UV'], centre.inputs[0])
    nt.links.new(centre.outputs['Vector'], length.inputs[0])
    nt.links.new(length.outputs['Value'], fall.inputs['Value'])
    nt.links.new(coord.outputs['Object'], noise.inputs['Vector'])
    nt.links.new(noise.outputs['Fac'], shape.inputs['Value'])
    nt.links.new(fall.outputs['Result'], mul.inputs[0])
    nt.links.new(shape.outputs['Result'], mul.inputs[1])
    nt.links.new(mul.outputs['Value'], mul2.inputs[0])
    nt.links.new(mul2.outputs['Value'], p.inputs['Alpha'])
    return m


def blob(name, radii, mat, parent, offset, rot_z=0.0):
    """Two crossed cards (ground-parallel and upright along the length) carrying a haze."""
    rx, ry, rz = radii
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new()
    for quad in (((-rx, -ry, 0), (rx, -ry, 0), (rx, ry, 0), (-rx, ry, 0)),
                 ((-rx, 0, -rz), (rx, 0, -rz), (rx, 0, rz), (-rx, 0, rz))):
        f = bm.faces.new([bm.verts.new(c) for c in quad])
        for loop, co in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
            loop[uv].uv = co
    ob = mesh_object(name, bm, mat, parent)
    ob.location = offset
    ob.rotation_euler = (0, 0, rot_z)
    return ob


# ---------------------------------------------------------------- materials

M_STEEL = material('LIFE steel grey', (0.36, 0.37, 0.38), 0.55, 0.6)
M_WOOD = material('LIFE pole timber', (0.18, 0.13, 0.09), 0.85)
M_WIRE = material('LIFE conductor', (0.06, 0.06, 0.06), 0.4, 0.8)
M_CAB = [material('LIFE cab oxide', (0.75, 0.30, 0.08), 0.5),
         material('LIFE cab cream', (0.85, 0.82, 0.70), 0.5),
         material('LIFE cab yellow', (0.85, 0.62, 0.10), 0.5)]
M_LOAD = [material('LIFE load produce crates', (0.90, 0.55, 0.15), 0.8),
          material('LIFE load white container', (0.88, 0.88, 0.84), 0.6),
          material('LIFE load tank', (0.80, 0.80, 0.78), 0.35, 0.5)]
M_BIRD = material('LIFE egret white', (0.85, 0.84, 0.80), 0.7)
M_DUST = haze_material('LIFE road dust', (0.86, 0.76, 0.60), 1.0)
M_MIST = haze_material('LIFE irrigation mist', (0.95, 0.97, 1.0), 0.6)


# ---------------------------------------------------------------- haulers and dust

def hauler(name, start, velocity, cab, load, trailers=1):
    """Cab plus trailers on the road, facing along its velocity, trailing a dust plume."""
    frame = frame_empty(f'LIFE | {name}')
    heading = math.atan2(velocity[1], velocity[0])
    frame.rotation_euler = (0, 0, heading)
    drive(frame, start, velocity)
    bm = bmesh.new()
    box(bm, (3.4, 3.0, 3.4), (0, 0, 2.1))           # cab, local +X forward
    box(bm, (2.0, 3.0, 0.7), (-1.0, 0, 0.6))         # chassis under the hitch
    mesh_object(f'LIFE | {name} cab', bm, M_CAB[cab], frame)
    bm = bmesh.new()
    for i in range(trailers):
        x = -2.0 - 6.5 - i * 13.5
        box(bm, (12.5, 3.0, 3.2), (x, 0, 2.4))
    mesh_object(f'LIFE | {name} load', bm, M_LOAD[load], frame)
    tail = 2.0 + trailers * 13.5
    blob(f'LIFE | {name} dust', (75.0, 18.0, 12.0), M_DUST, frame, (-tail - 60.0, 5.0, 7.0))
    return frame


hauler('produce hauler 1', (-3820, ROAD_Y - 3, GROUND_Z), (21, 0, 0), 0, 0, 2)
hauler('produce hauler 2', (-3895, ROAD_Y - 3, GROUND_Z), (21, 0, 0), 1, 1, 2)
hauler('empty hauler', (-3310, ROAD_Y + 3, GROUND_Z), (-17, 0, 0), 2, 2, 1)
hauler('field truck', (CROSS_X + 3, 760, GROUND_Z), (0, 13, 0), 1, 1, 1)


# ---------------------------------------------------------------- harvest in the near field

def harvester(name, start, cab_mat):
    """Combine cutting along +x with its unloading auger over a grain truck pacing it."""
    frame = frame_empty(f'LIFE | {name}')
    drive(frame, start, (3.2, 0, 0))
    bm = bmesh.new()
    box(bm, (8.5, 4.2, 3.8), (0, 0, 2.6))           # body
    box(bm, (2.4, 3.0, 2.4), (3.0, 0, 5.4))          # cab
    box(bm, (1.6, 11.0, 1.2), (5.2, 0, 0.9))         # cutting header
    strut(bm, (-1.0, -2.0, 4.6), (1.0, -8.5, 5.2), 0.6)  # unloading auger to the truck side
    mesh_object(f'LIFE | {name} body', bm, cab_mat, frame)
    bm = bmesh.new()
    box(bm, (3.0, 3.0, 3.0), (2.5, -10.5, 2.0))      # grain truck cab
    box(bm, (8.0, 3.0, 2.6), (-3.5, -10.5, 2.2))     # open grain box
    mesh_object(f'LIFE | {name} grain truck', bm, M_CAB[1], frame)
    blob(f'LIFE | {name} chaff', (40.0, 14.0, 9.0), M_DUST, frame, (-30.0, -3.0, 5.0))


M_COMBINE = material('LIFE combine green-yellow', (0.55, 0.62, 0.12), 0.5)
harvester('combine 1', (-4560, 790, GROUND_Z), M_COMBINE)
harvester('combine 2', (-4180, 745, GROUND_Z), M_COMBINE)


# ---------------------------------------------------------------- transmission line

def pylon(bm, x, y):
    h, base, top = 38.0, 5.0, 1.4
    corners = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
    for cx, cy in corners:
        strut(bm, (x + cx * base, y + cy * base, GROUND_Z), (x + cx * top, y + cy * top, GROUND_Z + h), 0.7)
    for z, w in ((GROUND_Z + 14, 3.6), (GROUND_Z + 26, 2.4)):     # bracing girdles
        for (ax, ay), (bx, by) in zip(corners, corners[1:] + corners[:1]):
            strut(bm, (x + ax * w, y + ay * w, z), (x + bx * w, y + by * w, z), 0.45)
    box(bm, (1.0, 22.0, 1.0), (x, y, GROUND_Z + 30))               # lower crossarm, across the line
    box(bm, (1.0, 13.0, 1.0), (x, y, GROUND_Z + h))                # upper crossarm


LINE_Y, LINE_X0, LINE_STEP, LINE_N = 560.0, -5200.0, 260.0, 12
bm = bmesh.new()
for i in range(LINE_N):
    pylon(bm, LINE_X0 + i * LINE_STEP, LINE_Y)
mesh_object('LIFE | transmission pylons', bm, M_STEEL, ROOT)


def wire(name, points, radius):
    cu = bpy.data.curves.new(name, 'CURVE')
    cu.dimensions = '3D'
    cu.bevel_depth = radius
    cu.bevel_resolution = 1
    sp = cu.splines.new('POLY')
    sp.points.add(len(points) - 1)
    for p, co in zip(sp.points, points):
        p.co = (*co, 1.0)
    ob = bpy.data.objects.new(name, cu)
    cu.materials.append(M_WIRE)
    LIFE.objects.link(ob)
    ob.parent = ROOT
    return ob


for k, (dy, dz) in enumerate(((-10.5, 29.4), (10.5, 29.4), (-6.0, 37.4), (6.0, 37.4))):
    pts = []
    for i in range(LINE_N - 1):
        x0 = LINE_X0 + i * LINE_STEP
        for j in range(12):
            u = j / 12
            sag = 9.0 * 4 * u * (1 - u)
            pts.append((x0 + u * LINE_STEP, LINE_Y + dy, GROUND_Z + dz - sag))
    pts.append((LINE_X0 + (LINE_N - 1) * LINE_STEP, LINE_Y + dy, GROUND_Z + dz))
    wire(f'LIFE | conductor {k}', pts, 0.22)


# ---------------------------------------------------------------- farm-track poles

POLE_Y, POLE_STEP = 860.0, 55.0
bm = bmesh.new()
pole_xs = [-4900 + i * POLE_STEP for i in range(38)]
for x in pole_xs:
    box(bm, (0.45, 0.45, 11.0), (x, POLE_Y, GROUND_Z + 5.5))
    box(bm, (0.35, 2.4, 0.3), (x, POLE_Y, GROUND_Z + 10.4))
mesh_object('LIFE | farm-track poles', bm, M_WOOD, ROOT)
for k, dy in enumerate((-1.0, 1.0)):
    pts = []
    for a, b in zip(pole_xs, pole_xs[1:]):
        for j in range(6):
            u = j / 6
            pts.append((a + u * (b - a), POLE_Y + dy, GROUND_Z + 10.5 - 1.4 * 4 * u * (1 - u)))
    pts.append((pole_xs[-1], POLE_Y + dy, GROUND_Z + 10.5))
    wire(f'LIFE | farm wire {k}', pts, 0.12)


# ---------------------------------------------------------------- linear-move irrigation rig

RIG_X, RIG_Y0, RIG_Y1 = -3720.0, 1120.0, 1460.0
rig = frame_empty('LIFE | irrigation rig')
drive(rig, (RIG_X, 0, 0), (0.6, 0, 0))
bm = bmesh.new()
towers = [RIG_Y0 + i * (RIG_Y1 - RIG_Y0) / 6 for i in range(7)]
for y in towers:
    strut(bm, (-2.5, y, GROUND_Z), (0, y, GROUND_Z + 5.0), 0.4)
    strut(bm, (2.5, y, GROUND_Z), (0, y, GROUND_Z + 5.0), 0.4)
    box(bm, (1.2, 1.6, 1.4), (0, y, GROUND_Z + 0.7))              # drive cart
box(bm, (0.9, RIG_Y1 - RIG_Y0, 0.9), (0, (RIG_Y0 + RIG_Y1) / 2, GROUND_Z + 5.2))
for a, b in zip(towers, towers[1:]):                               # under-truss
    m = (a + b) / 2
    strut(bm, (0, a, GROUND_Z + 5.2), (0, m, GROUND_Z + 3.2), 0.3)
    strut(bm, (0, m, GROUND_Z + 3.2), (0, b, GROUND_Z + 5.2), 0.3)
mesh_object('LIFE | irrigation truss', bm, M_STEEL, rig)
for a, b in zip(towers, towers[1:]):
    blob(f'LIFE | irrigation mist {a:.0f}', ((b - a) / 2 + 3, 6.0, 3.6), M_MIST, rig,
         (0, (a + b) / 2, GROUND_Z + 2.4), math.pi / 2)


# ---------------------------------------------------------------- birds

def bird(name, parent, offset, span, phase):
    """Egret-like glider: tapered body, swept pointed wings hinged at the shoulder."""
    body = frame_empty(name, parent)
    body.location = offset
    bm = bmesh.new()
    v = [bm.verts.new(c) for c in ((0.9, 0, 0.05), (0.2, 0.18, 0), (-0.9, 0.08, 0), (-1.1, 0, 0),
                                    (-0.9, -0.08, 0), (0.2, -0.18, 0))]
    bm.faces.new(v)
    mesh_object(name + ' body', bm, M_BIRD, body)
    for side in (1, -1):
        hinge = frame_empty(f'{name} wing {side}', body)
        hinge.location = (0.15, side * 0.15, 0)
        bm = bmesh.new()
        h = span / 2
        outline = ((0.25, 0), (0.15, 0.45 * h), (-0.15, 0.85 * h), (-0.55, h), (-0.45, 0.6 * h), (-0.35, 0))
        v = [bm.verts.new((x, side * y, 0)) for x, y in outline]
        bm.faces.new(v if side > 0 else list(reversed(v)))
        mesh_object(f'{name} wing {side} mesh', bm, M_BIRD, hinge)
        d = hinge.driver_add('rotation_euler', 0).driver
        d.type = 'SCRIPTED'
        d.expression = f'{side}*(0.10+0.45*sin(frame*1.55+{phase:.2f}))'


# a flock of large gliders crossing under the window around output 2.0 s, against our travel;
# camera at source frame 264 is (-4503, 30, 359) looking along (0.56, 0.80, -0.21)
flock = frame_empty('LIFE | flock')
flock.rotation_euler = (0, 0, math.pi)       # birds face -x, their direction of flight
drive(flock, (-4503 + 45, 30 + 66, 359 - 19), (-12, 2, 0.4), T0 - 30, T0 + 80, at=264)
offsets = [(0, 0, 0), (-6, 4, 1), (-6, -5, -1), (-13, 9, 2), (-12, -9, 0), (-19, 13, 1),
           (-20, -12, 2), (-3, 13, -2), (-15, 1, -3), (-26, 5, 0), (-9, -16, 3), (-24, -4, -2)]
for i, o in enumerate(offsets):
    bird(f'LIFE | bird {i}', flock, o, 4.0, i * 1.7)


# ---------------------------------------------------------------- render

scene.eevee.taa_render_samples = args.samples
scene.render.image_settings.file_format = 'PNG'
assert (scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage) == (1920, 1080, 100)
OUT.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'life-pass.blend'), copy=True)
frames = OUT / 'frames'
frames.mkdir(exist_ok=True)
wanted = [int(n) for n in args.still.split(',')] if args.still else range(1, 97)
for index in wanted:
    source = T0 + (index - 1) * 0.5
    scene.frame_set(int(source), subframe=source % 1)
    scene.render.filepath = str(frames / f'frame_{index:04d}.png')
    bpy.ops.render.render(write_still=True)
    print('LIFE_FRAME', index, flush=True)
