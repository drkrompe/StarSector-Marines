"""Paint the C4/C5 vehicles from above: one sheet of every vehicle top, painted once, carried along.

Both shots look straight down, so a vehicle is its roof: a tug, a bowser or a customs car reads
by what is painted on top. The ground is painted where it lies (`ortho_paint.py`); a vehicle
moves, so its paint is laid out in the vehicle's own frame instead. Every vehicle is set out on
one sheet, nose to the right, drawn straight down with flat colours and outlines; ImageGen paints
the sheet once, and each vehicle wears its slot wherever it drives.

    blender --background --factory-startup --python vehicle_paint.py -- guide
    python  vehicle_paint.py paint               (outside Blender: one paid generation)

`guide` writes `vehicles/guide.png` (regenerated, never committed). `paint` sends it with
`vehicles/prompt.txt` (the ground's `ortho/prompts/common.txt` rules first) and keeps the master
as `vehicles/sheet.png` with its provenance. Once `vehicles/accepted` exists, `apply()` gives
each listed vehicle its own copy of its materials and lays its slot over them: a roof where it
was painted, a side as the edge of the roof above it, drawn down.
A vehicle that shares a slot (the port's bowsers, the MuniSec car) is built from the same parts
in the same frame, so it wears the same paint.
"""
import json
import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / 'vehicles'
REPO = HERE.parents[3]
SHEET_M = (48.0, 32.0)          # the sheet in metres; 1536x1024 px, about 3 cm a pixel
SHEET_PX = (1536, 1024)
HERMES_MPU = 0.11
SIDE_INSET = 0.15            # metres inside the roof edge a side samples, clear of the outline

# slot centre on the sheet (metres from its centre), and which vehicles wear it (name prefixes)
SLOTS = {
    'flatbed': ((-14.0, 10.0), ('Flatbed',)),
    'bowser': ((7.0, 10.0), ('Fuel bowser', 'Port bowser')),
    'carrier': ((-16.0, -1.5), ('Crew carrier',)),
    'tug': ((-3.5, -1.5), ('Tug',)),
    'hermes': ((13.0, -4.0), ()),
    'forklift': ((-15.0, -11.5), ('Yard forklift',)),
    'customs': ((-3.5, -11.5), ('Customs car', 'MuniSec car')),
}


# ---------------------------------------------------------------- inside Blender

def _roots():
    """(slot, root empty) for every vehicle in the scene that wears a slot."""
    import bpy
    out = []
    for ob in bpy.data.objects:
        if ob.type != 'EMPTY' or ob.instance_type == 'COLLECTION':
            continue
        for slot, (_, prefixes) in SLOTS.items():
            if prefixes and ob.name.startswith(prefixes):
                out.append((slot, ob))
    return out


def _hermes():
    """The Hermes source collection the lighters instance, or None."""
    import bpy
    for ob in bpy.data.objects:
        if ob.name.startswith('Lighter ') and ob.instance_collection is not None:
            return ob.instance_collection
    return None


def _parts(slot, root=None, hermes=None):
    """[(mesh object, matrix to the vehicle frame in metres)] for one vehicle."""
    from mathutils import Matrix
    if slot == 'hermes':
        s = Matrix.Scale(HERMES_MPU, 4)
        return [(o, s @ o.matrix_world) for o in hermes.all_objects if o.type == 'MESH']
    return [(o, o.matrix_basis.copy()) for o in root.children if o.type == 'MESH']


def _footprint(parts):
    xs, ys = [], []
    for o, m in parts:
        for v in o.data.vertices:
            p = m @ v.co
            xs.append(p.x)
            ys.append(p.y)
    return (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, max(xs) - min(xs), max(ys) - min(ys)


def render_guide():
    import bpy
    from mathutils import Matrix
    sys.path.insert(0, str(HERE))
    os.environ['ORTHO_PAINT_OFF'] = '1'
    os.environ['VEHICLE_PAINT_OFF'] = '1'
    import c4x_scene as c4
    c4.build()
    roots = {}
    for slot, root in _roots():
        roots.setdefault(slot, root)
    hermes = _hermes()
    scene = bpy.data.scenes.new('Vehicle sheet')
    for m in bpy.data.materials:                  # the flat renderer reads each material's display colour
        bsdf = m.node_tree and next((n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
        if bsdf is not None and not bsdf.inputs['Base Color'].links:
            m.diffuse_color = bsdf.inputs['Base Color'].default_value
    sizes = {}
    for slot, ((sx, sy), _) in SLOTS.items():
        if slot != 'hermes' and slot not in roots:
            raise SystemExit('no vehicle in the scene wears ' + slot)
        parts = _parts(slot, roots.get(slot), hermes)
        fx, fy, w, h = _footprint(parts)
        print('VEHICLE_SLOT %s %.1f x %.1f m' % (slot, w, h), flush=True)
        sizes[slot] = (w, h)
        place = Matrix.Translation((sx - fx, sy - fy, 0))
        for o, m in parts:
            c = bpy.data.objects.new('%s | %s' % (slot, o.name), o.data)
            c.matrix_world = place @ m
            scene.collection.objects.link(c)
    scene.render.engine = 'BLENDER_WORKBENCH'
    sh = scene.display.shading
    sh.light = 'FLAT'
    sh.color_type = 'MATERIAL'
    sh.show_cavity = True
    sh.cavity_type = 'WORLD'
    sh.show_object_outline = True
    sh.object_outline_color = (0.05, 0.05, 0.05)
    scene.view_settings.view_transform = 'Standard'
    scene.world = bpy.data.worlds.new('Sheet')
    scene.world.color = (0.5, 0.5, 0.5)
    cam = bpy.data.objects.new('Sheet camera', bpy.data.cameras.new('Sheet camera'))
    scene.collection.objects.link(cam)
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = SHEET_M[0]
    cam.data.clip_start, cam.data.clip_end = 1.0, 500.0
    cam.location = (0, 0, 200)
    scene.camera = cam
    scene.render.resolution_x, scene.render.resolution_y = SHEET_PX
    scene.render.resolution_percentage = 100
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'slots.json').write_text(json.dumps(sizes, indent=2) + '\n')
    scene.render.filepath = str(OUT / 'guide.png')
    bpy.ops.render.render(write_still=True, scene=scene.name)
    print('VEHICLE_GUIDE written', flush=True)


def apply():
    """Lay the accepted sheet over every listed vehicle. Returns vehicles painted."""
    import bpy
    sheet = OUT / 'sheet.png'
    if os.environ.get('VEHICLE_PAINT_OFF') or not (OUT / 'accepted').exists() or not sheet.exists():
        return 0
    img = bpy.data.images.load(str(sheet), check_existing=True)
    done = 0
    wearers = [(slot, _parts(slot, root)) for slot, root in _roots()]
    hermes = _hermes()
    if hermes is not None:
        wearers.append(('hermes', _parts('hermes', hermes=hermes)))
    painted = {k: v['painted'] for k, v in json.loads((OUT / 'fit.json').read_text()).items()}
    pw, ph = SHEET_PX
    for slot, parts in wearers:
        if not parts:
            continue
        fx, fy, w, h = _footprint(parts)
        x0, y0, x1, y1 = painted[slot]              # where the painting put it, rows from the top
        copies = {}
        for o, m in parts:
            uv = o.data.uv_layers.get('sheet') or o.data.uv_layers.new(name='sheet')
            rot = m.to_3x3()
            for poly in o.data.polygons:
                n = (rot @ poly.normal).normalized()
                # a side takes the paint from just inside the roof's edge above it
                inset = (n.x * SIDE_INSET, n.y * SIDE_INSET) if abs(n.z) < 0.5 else (0.0, 0.0)
                for li in poly.loop_indices:
                    loop = o.data.loops[li]
                    p = m @ o.data.vertices[loop.vertex_index].co
                    p.x -= inset[0]
                    p.y -= inset[1]
                    col = x0 + (p.x - fx + w / 2) / w * (x1 - x0)
                    row = y0 + (fy + h / 2 - p.y) / h * (y1 - y0)
                    uv.data[li].uv = (col / pw, 1.0 - row / ph)
            for i, mat in enumerate(o.data.materials):
                if mat is None:
                    continue
                if mat.name not in copies:
                    copies[mat.name] = _painted(mat.copy(), img)
                o.data.materials[i] = copies[mat.name]
        done += 1
    return done


def _painted(m, img):
    m.name = m.name.split('.')[0] + ' | painted'
    nt = m.node_tree
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    if bsdf is None:
        return m
    base = bsdf.inputs['Base Color']
    if base.links:
        colour = base.links[0].from_socket
    else:
        rgb = nt.nodes.new('ShaderNodeRGB')
        rgb.outputs[0].default_value = base.default_value
        colour = rgb.outputs[0]
    uv = nt.nodes.new('ShaderNodeUVMap')
    uv.uv_map = 'sheet'
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.extension = 'CLIP'
    nt.links.new(uv.outputs['UV'], tex.inputs['Vector'])
    mix = nt.nodes.new('ShaderNodeMix')
    mix.data_type = 'RGBA'
    mix.inputs['Factor'].default_value = 1.0
    nt.links.new(colour, mix.inputs['A'])
    nt.links.new(tex.outputs['Color'], mix.inputs['B'])
    nt.links.new(mix.outputs['Result'], base)
    return m


# ---------------------------------------------------------------- outside Blender

def _box(mask, box, cover=0.25):
    """Grow or shrink a pixel box until each edge line is at least `cover` filled and the line
    just outside it is not: the extent of the one shape the box started in."""
    x0, y0, x1, y1 = box
    h, w = mask.shape
    for _ in range(4000):
        moved = False
        for side in range(4):
            if side == 0:
                out, edge = (mask[y0:y1, x0 - 1] if x0 > 0 else None), mask[y0:y1, x0]
            elif side == 1:
                out, edge = (mask[y0:y1, x1] if x1 < w else None), mask[y0:y1, x1 - 1]
            elif side == 2:
                out, edge = (mask[y0 - 1, x0:x1] if y0 > 0 else None), mask[y0, x0:x1]
            else:
                out, edge = (mask[y1, x0:x1] if y1 < h else None), mask[y1 - 1, x0:x1]
            grow = out is not None and out.mean() >= cover
            shrink = not grow and edge.mean() < cover and (x1 - x0 > 2 and y1 - y0 > 2)
            if grow or shrink:
                d = 1 if grow else -1
                if side == 0:
                    x0 -= d
                elif side == 1:
                    x1 += d
                elif side == 2:
                    y0 -= d
                else:
                    y1 += d
                moved = True
        if not moved:
            break
    return [x0, y0, x1, y1]


def _close(mask, r):
    """Fill gaps narrower than 2r pixels (a cab standing off its load) without growing the shape."""
    import numpy as np

    def spread(m, op):
        out = m.copy()
        for axis in (0, 1):
            acc = out.copy()
            for k in range(1, r + 1):
                acc = op(acc, np.roll(out, k, axis))
                acc = op(acc, np.roll(out, -k, axis))
            out = acc
        return out
    return spread(spread(mask, np.logical_or), np.logical_and)


def fit():
    """Where each vehicle actually landed on the painted sheet. ImageGen keeps the layout but not
    always the size: a shape may come back a little larger or shifted. Each slot is found on the
    guide from its centre, then followed onto the painting, and `apply` maps the vehicle onto
    the painted box rather than the guide's."""
    import numpy as np
    from PIL import Image
    sizes = json.loads((OUT / 'slots.json').read_text())
    px_m = SHEET_PX[0] / SHEET_M[0]
    out = {}
    masks = []
    for f in ('guide.png', 'sheet.png'):
        a = np.asarray(Image.open(OUT / f).convert('RGB')).astype(int)
        bg = np.median(a.reshape(-1, 3), axis=0)
        masks.append(_close(np.abs(a - bg).sum(2) > 30, 12))
    for slot, ((sx, sy), _) in SLOTS.items():
        w, h = sizes[slot]
        g = [round(SHEET_PX[0] / 2 + (sx - w / 2) * px_m), round(SHEET_PX[1] / 2 - (sy + h / 2) * px_m),
             round(SHEET_PX[0] / 2 + (sx + w / 2) * px_m), round(SHEET_PX[1] / 2 - (sy - h / 2) * px_m)]
        p = _box(masks[1], g)
        out[slot] = {'guide': g, 'painted': p}
        print('VEHICLE_FIT %-8s guide %s painted %s' % (slot, g, p))
    (OUT / 'fit.json').write_text(json.dumps(out, indent=2) + '\n')


def paint():
    guide = OUT / 'guide.png'
    if not guide.exists():
        raise SystemExit('render the guide first: %s' % guide)
    full = OUT / 'sheet.prompt.txt'
    full.write_text((HERE / 'ortho' / 'prompts' / 'common.txt').read_text() + '\n' + (OUT / 'prompt.txt').read_text())
    cmd = [sys.executable, str(REPO / 'tools' / 'codex_imagegen.py'), '--prompt', str(full),
           '--out', str(OUT / 'sheet.png'),
           '--ref', '%s=%s' % (guide, 'EDIT TARGET: the overhead guide to repaint; keep every edge where it is'),
           '--ref', '%s=%s' % (HERE / 'ortho' / 'raw' / 'k9-south.png',
                               'STYLE REFERENCE: the painted yard these vehicles work in. Match its wear, '
                               'grime and finish; do not copy its layout')]
    raise SystemExit(subprocess.call(cmd))


if __name__ == '__main__':
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    if argv[0] == 'guide':
        render_guide()
    elif argv[0] == 'paint':
        paint()
    elif argv[0] == 'fit':
        fit()
