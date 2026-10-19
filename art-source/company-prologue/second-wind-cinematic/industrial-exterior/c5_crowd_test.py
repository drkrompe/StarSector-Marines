"""C5 crowd test: how to put walking people in Nessa's yard, seen from overhead.

    blender --background --factory-startup --python c5_crowd_test.py -- [--stills] [--loop]

Builds C4's scene at its last frame (Second Wind down on Kilo Nine) and walks a line of families
from the ramp toward the workshop, two ways side by side:

  west file  flat sprite cards on the ground, turned to the way each person walks, flipping
             through a top-down walk cycle; a second, upright card per person shows only to the
             sun, so its shadow carries a side-on walk cycle.
  east file  simple 3D walkers: a body, a head and two swinging legs.

The sprite sheets here are drawn by this script as placeholders, at the layout the real ones
(Codex) must follow: 8 frames a cycle across, 4 clothing variants down; top-down 64x64 px a
frame at 2.5 cm a pixel, the walker facing +x; side-on 64x96 px a frame, the walker facing +x.

--stills renders the three candidate cameras to build/c5x/stills/; --loop renders one walk cycle
(24 frames) from the closest camera to build/c5x/loop/.
"""
import math
import random
import sys
from pathlib import Path

import bmesh
import bpy
import numpy as np
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
import c4x_scene as c4  # noqa: E402

OUT = c4.REPO / 'build' / 'c5x'
PX = 0.025                  # metres a sprite pixel
FRAMES_PER_CYCLE = 8
STEP_FRAMES = 3             # 8 x 3 = one stride cycle a second at 24 fps
WALK = 1.3                  # metres a second
SHEET_TOP = (64, 64)        # frame size, top-down
SHEET_SIDE = (64, 96)       # frame size, side-on
VARIANTS = [((0.35, 0.40, 0.48), (0.16, 0.12, 0.10)), ((0.55, 0.30, 0.22), (0.10, 0.08, 0.07)),
            ((0.62, 0.58, 0.48), (0.30, 0.20, 0.12)), ((0.25, 0.36, 0.28), (0.12, 0.10, 0.09))]


# ---------------------------------------------------------------- placeholder sprite sheets

def ellipse(img, cx, cy, rx, ry, rgba):
    h, w, _ = img.shape
    y, x = np.mgrid[0:h, 0:w]
    m = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0
    img[m] = rgba


def top_sheet(poses=False):
    """Seen from straight above, walking toward +x: feet and arms swing fore and aft under and
    beside the shoulders; the head bobs a pixel. With `poses`, eight more frames follow the walk:
    stand, crouch a/b, signal up/out, carry, talk, arms folded (`c5_crowd.SHEET_COLUMNS`)."""
    fw, fh = SHEET_TOP
    cols = FRAMES_PER_CYCLE + (len(POSES) if poses else 0)
    sheet = np.zeros((fh * len(VARIANTS), fw * cols, 4), np.float32)
    for row, (cloth, hair) in enumerate(VARIANTS):
        for i in range(cols):
            img = np.zeros((fh, fw, 4), np.float32)
            cx, cy = fw / 2, fh / 2
            if i < FRAMES_PER_CYCLE:
                s = math.sin(2 * math.pi * i / FRAMES_PER_CYCLE)
                feet = ((s * 9, -4), (-s * 9, 4))
                arms = ((-s * 5, -11, 4.0, 2.6), (s * 5, 11, 4.0, 2.6))
                body, head = (0, 6.5, 10.5), 1
            else:
                feet, arms, body, head = POSES[i - FRAMES_PER_CYCLE]
            for dx, dy in feet:
                ellipse(img, cx + dx, cy + dy, 4.5, 2.5, (0.08, 0.07, 0.06, 1))
            for dx, dy, rx, ry in arms:
                ellipse(img, cx + dx, cy + dy, rx, ry, (*cloth, 1))
            ellipse(img, cx + body[0], cy, body[1], body[2], (*cloth, 1))   # shoulders and back
            ellipse(img, cx + head, cy, 4.2, 4.2, (*hair, 1))
            sheet[row * fh:(row + 1) * fh, i * fw:(i + 1) * fw] = img
    return sheet


# Top-down poses: (feet [(dx, dy)], arms [(dx, dy, rx, ry)], body (dx, rx, ry), head dx), facing +x.
POSES = [
    ([(1, -4), (1, 4)], [(0, -11, 4.0, 2.6), (0, 11, 4.0, 2.6)], (0, 6.5, 10.5), 1),                 # stand
    ([(-6, -5), (-6, 5)], [(9, -7, 5.0, 2.4), (6, 7, 5.0, 2.4)], (2, 8.5, 10.5), 7),                 # crouch a
    ([(-6, -5), (-6, 5)], [(6, -7, 5.0, 2.4), (9, 7, 5.0, 2.4)], (2, 8.5, 10.5), 7),                 # crouch b
    ([(1, -4), (1, 4)], [(0, -15, 3.0, 4.5), (0, 15, 3.0, 4.5)], (0, 6.5, 10.5), 1),                 # signal up
    ([(1, -4), (1, 4)], [(0, -19, 2.6, 8.0), (0, 19, 2.6, 8.0)], (0, 6.5, 10.5), 1),                 # signal out
    ([(1, -4), (1, 4)], [(8, -8, 5.0, 2.4), (8, 8, 5.0, 2.4)], (0, 6.5, 10.5), 1),                   # carry
    ([(1, -4), (1, 4)], [(0, -11, 4.0, 2.6), (9, 13, 5.5, 2.4)], (0, 6.5, 10.5), 1),                 # talk
    ([(1, -4), (1, 4)], [(6, 0, 3.0, 9.0)], (0, 6.5, 10.5), 1),                                       # folded
]


def segment(img, a, b, r):
    n = max(2, int(math.hypot(b[0] - a[0], b[1] - a[1])))
    for k in range(n + 1):
        t = k / n
        ellipse(img, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, r, r, (0, 0, 0, 1))


def side_sheet(poses=False):
    """Side-on silhouette walking toward +x: only its outline matters, as a cast shadow. With
    `poses`, the same eight poses as `top_sheet` follow the walk."""
    fw, fh = SHEET_SIDE
    cols = FRAMES_PER_CYCLE + (len(SIDE_POSES) if poses else 0)
    sheet = np.zeros((fh * len(VARIANTS), fw * cols, 4), np.float32)
    for row in range(len(VARIANTS)):
        for i in range(cols):
            img = np.zeros((fh, fw, 4), np.float32)
            hx, hy = fw / 2, fh - 36                                    # the hip; image rows run down
            if i < FRAMES_PER_CYCLE:
                ph = 2 * math.pi * i / FRAMES_PER_CYCLE
                for sgn in (1, -1):                                     # legs from the hip to the foot
                    a = sgn * 0.45 * math.sin(ph)
                    segment(img, (hx, hy), (hx + math.sin(a) * 35, hy + math.cos(a) * 35), 2.0)
                drop, lean, arms = 0, 0, []
            else:
                legs, drop, lean, arms = SIDE_POSES[i - FRAMES_PER_CYCLE]
                hy2 = hy + drop
                for knee, foot in legs:
                    segment(img, (hx, hy2), (hx + knee[0], hy2 + knee[1]), 2.0)
                    segment(img, (hx + knee[0], hy2 + knee[1]), (hx + foot[0], hy + 35), 2.0)
            hy2 = hy + drop
            sx, sy = hx + lean, hy2 - 28                                 # the shoulder
            segment(img, (hx, hy2), (sx, sy), 5.0)                       # body
            ellipse(img, sx + 1, sy - 6, 4.5, 4.5, (0, 0, 0, 1))
            for dx, dy in arms:
                segment(img, (sx, sy + 2), (sx + dx, sy + 2 + dy), 1.6)
            if i == FRAMES_PER_CYCLE + 5:                                # the carried box
                ellipse(img, sx + 16, sy + 8, 8, 6, (0, 0, 0, 1))
            sheet[row * fh:(row + 1) * fh, i * fw:(i + 1) * fw] = img
    return sheet


# Side poses: (legs [(knee dx, dy), (foot dx)], hip drop px, shoulder lean px, arms [(dx, dy)]).
_STRAIGHT = [((0, 17), (0, 0)), ((1, 17), (1, 0))]
SIDE_POSES = [
    (_STRAIGHT, 0, 0, [(1, 24)]),                                              # stand
    ([((14, 2), (4, 0)), ((10, 4), (-2, 0))], 16, 10, [(14, 14)]),             # crouch a
    ([((14, 2), (4, 0)), ((10, 4), (-2, 0))], 16, 10, [(16, 8)]),              # crouch b
    (_STRAIGHT, 0, 0, [(6, -24), (-6, -24)]),                                  # signal up
    (_STRAIGHT, 0, 0, [(22, -4), (-22, -4)]),                                  # signal out
    (_STRAIGHT, 0, 1, [(14, 6)]),                                              # carry
    (_STRAIGHT, 0, 0, [(1, 24), (16, 4)]),                                     # talk
    (_STRAIGHT, 0, 0, [(8, 10)]),                                              # folded
]


def to_image(name, arr, path):
    h, w, _ = arr.shape
    img = bpy.data.images.new(name, w, h, alpha=True)
    img.pixels = np.flipud(arr).ravel()                                 # Blender rows run up
    img.filepath_raw = str(path)
    img.file_format = 'PNG'
    img.save()
    return img


# ---------------------------------------------------------------- sprite material: frame from the clock

def sprite_material(name, img, shadow_only):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.surface_render_method = 'DITHERED'
    try:
        m.use_transparent_shadow = True
    except AttributeError:
        pass
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    p.inputs['Roughness'].default_value = 0.9
    clock = nt.nodes.new('ShaderNodeValue')
    d = clock.outputs[0].driver_add('default_value').driver
    d.type = 'SCRIPTED'
    d.expression = 'frame'
    info = nt.nodes.new('ShaderNodeObjectInfo')

    def math_node(op, a, b=None):
        n = nt.nodes.new('ShaderNodeMath')
        n.operation = op
        for i, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                n.inputs[i].default_value = v
            else:
                nt.links.new(v, n.inputs[i])
        return n.outputs[0]
    # frame index: floor(mod(frame / STEP + random * 8, 8)); variant row: floor(fract(random * 7.3) * 4)
    phase = math_node('MULTIPLY', info.outputs['Random'], float(FRAMES_PER_CYCLE))
    idx = math_node('FLOOR', math_node('MODULO', math_node('ADD', math_node('DIVIDE', clock.outputs[0], float(STEP_FRAMES)), phase),
                                       float(FRAMES_PER_CYCLE)))
    row = math_node('FLOOR', math_node('MULTIPLY', math_node('FRACT', math_node('MULTIPLY', info.outputs['Random'], 7.3)),
                                       float(len(VARIANTS))))
    uv = nt.nodes.new('ShaderNodeTexCoord').outputs['UV']
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(uv, sep.inputs[0])
    u = math_node('DIVIDE', math_node('ADD', sep.outputs['X'], idx), float(FRAMES_PER_CYCLE))
    v = math_node('DIVIDE', math_node('ADD', sep.outputs['Y'], math_node('SUBTRACT', float(len(VARIANTS) - 1), row)),
                  float(len(VARIANTS)))
    comb = nt.nodes.new('ShaderNodeCombineXYZ')
    nt.links.new(u, comb.inputs['X'])
    nt.links.new(v, comb.inputs['Y'])
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Closest'
    nt.links.new(comb.outputs[0], tex.inputs['Vector'])
    nt.links.new(tex.outputs['Color'], p.inputs['Base Color'])
    nt.links.new(tex.outputs['Alpha'], p.inputs['Alpha'])
    return m


def card(name, w, h, mat, upright):
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    if upright:                                   # in the x-z plane, standing on the ground
        co = ((-w / 2, 0, 0), (w / 2, 0, 0), (w / 2, 0, h), (-w / 2, 0, h))
    else:                                         # flat, a hair above the yard
        co = ((-w / 2, -h / 2, 0), (w / 2, -h / 2, 0), (w / 2, h / 2, 0), (-w / 2, h / 2, 0))
    f = bm.faces.new([bm.verts.new(c) for c in co])
    for loop, t in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
        loop[uvl].uv = t
    return c4.mesh(name, bm, mat)


# ---------------------------------------------------------------- the two files of walkers

def route(n, lateral):
    """Families from the foot of the ramp toward the workshop door, strung out along the way."""
    kx, ky = c4.K9
    kh = c4.k9_heading()
    a = Vector(c4.at(kx, ky, kh, -40, lateral)[:2])
    b = Vector((334.0 + lateral * 0.4, 418.0))
    r = random.Random(5 + int(lateral))
    return [(a.lerp(b, (i + r.uniform(-0.3, 0.3)) / n), (b - a).normalized()) for i in range(n)]


def add_sprite_walkers(top_img, side_img, start_frame):
    top = sprite_material('Walker top', top_img, False)
    side = sprite_material('Walker shadow', side_img, True)
    sun_xy = Vector((c4.SUN_DIR.x, c4.SUN_DIR.y)).normalized()
    for n, (p, d) in enumerate(route(14, -5.0)):
        heading = math.atan2(d.y, d.x)
        root = c4.link(bpy.data.objects.new('Sprite walker %d' % n, None))
        t = card('Sprite walker %d top' % n, SHEET_TOP[0] * PX, SHEET_TOP[1] * PX, top, False)
        t.parent = root
        t.location = (0, 0, 0.25)
        t.rotation_euler = (0, 0, heading)
        s = card('Sprite walker %d shadow' % n, SHEET_SIDE[0] * PX, SHEET_SIDE[1] * PX, side, True)
        s.parent = root
        # Face the sun, so the shadow is the full silhouette; flipped to keep the walk direction.
        s.rotation_euler = (0, 0, math.atan2(sun_xy.y, sun_xy.x) + math.pi / 2)
        s.scale.x = 1.0 if d.dot(Vector((-sun_xy.y, sun_xy.x))) >= 0 else -1.0
        s.visible_camera = False
        s.visible_diffuse = s.visible_glossy = False
        move(root, p, d, start_frame)


def add_3d_walkers(start_frame):
    cloth = [c4.material('Walker cloth %d' % i, c, 0.9) for i, (c, _) in enumerate(VARIANTS)]
    skin = c4.material('Walker head', (0.20, 0.14, 0.10), 0.8)
    for n, (p, d) in enumerate(route(14, 9.0)):
        root = c4.link(bpy.data.objects.new('3D walker %d' % n, None))
        body = bmesh.new()
        c4.box(body, (0.30, 0.50, 0.70), (0, 0, 1.15))
        c4.mesh('3D walker %d body' % n, body, cloth[n % len(cloth)]).parent = root
        head = bmesh.new()
        c4.box(head, (0.24, 0.22, 0.26), (0.02, 0, 1.65))
        c4.mesh('3D walker %d head' % n, head, skin).parent = root
        legs = []
        for side in (-1, 1):
            hip = c4.link(bpy.data.objects.new('3D walker %d hip %d' % (n, side), None))
            hip.parent = root
            hip.location = (0, side * 0.13, 0.85)
            leg = bmesh.new()
            c4.box(leg, (0.16, 0.16, 0.85), (0, 0, -0.425))
            c4.mesh('3D walker %d leg %d' % (n, side), leg, skin).parent = hip
            legs.append((hip, side))
        root.rotation_euler = (0, 0, math.atan2(d.y, d.x))
        ph0 = random.Random(n).uniform(0, 2 * math.pi)
        for f in range(start_frame, start_frame + 25):
            for hip, side in legs:
                hip.rotation_euler = (0, side * 0.45 * math.sin(ph0 + 2 * math.pi * (f - start_frame) / 24), 0)
                hip.keyframe_insert('rotation_euler', frame=f)
        move(root, p, d, start_frame)


def move(root, p, d, start_frame):
    for f in (start_frame, start_frame + 24):
        q = p + d * WALK * (f - start_frame) / c4.FPS
        root.location = (q.x, q.y, 0)
        root.keyframe_insert('location', frame=f)


# ---------------------------------------------------------------- cameras

CENTRE = Vector((362.0, 432.0))       # between the ramp's foot and the workshop door
CAMERAS = [('a-18mm-200m', 18.0, 200.0), ('b-50mm-555m', 50.0, 555.0), ('c-50mm-220m', 50.0, 220.0)]


def camera(name, lens, height):
    cam = c4.link(bpy.data.objects.new('C5 test ' + name, bpy.data.cameras.new('C5 test ' + name)))
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = lens, c4.SENSOR_MM, 'HORIZONTAL'
    cam.data.clip_start, cam.data.clip_end = 5.0, 60000.0
    cam.location = (CENTRE.x, CENTRE.y, height)
    cam.rotation_euler = (0, 0, 0)                 # straight down
    return cam


def main():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    scene = c4.build()
    (OUT / 'sprites').mkdir(parents=True, exist_ok=True)
    top = to_image('Walker sheet top', top_sheet(), OUT / 'sprites' / 'walker-top.png')
    side = to_image('Walker sheet side', side_sheet(), OUT / 'sprites' / 'walker-side.png')
    start = c4.FRAMES - 1
    scene.frame_end = start + 24
    add_sprite_walkers(top, side, start)
    add_3d_walkers(start)
    cams = {n: camera(n, lens, h) for n, lens, h in CAMERAS}
    if '--stills' in argv:
        (OUT / 'stills').mkdir(parents=True, exist_ok=True)
        scene.frame_set(start + 6)
        for n, cam in cams.items():
            scene.camera = cam
            scene.render.filepath = str(OUT / 'stills' / (n + '.png'))
            bpy.ops.render.render(write_still=True)
            print('C5X_STILL', n, flush=True)
    if '--loop' in argv:
        target = OUT / 'loop'
        target.mkdir(parents=True, exist_ok=True)
        for old in target.glob('*.png'):
            old.unlink()
        scene.camera = cams['c-50mm-220m']
        for i, f in enumerate(range(start, start + 24)):
            scene.frame_set(f)
            scene.render.filepath = str(target / ('%05d.png' % i))
            bpy.ops.render.render(write_still=True)


if __name__ == '__main__':
    main()
