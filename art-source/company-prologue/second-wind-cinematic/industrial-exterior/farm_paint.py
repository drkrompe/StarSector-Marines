"""Paint C4's farm machines and road traffic from above: one sheet, painted once, worn as they move.

The same method as `vehicle_paint.py` (the yard's vehicles), on its own sheet so the two can be
painted and accepted apart: every machine `c4_farm.py` builds is laid out nose to the right, drawn
straight down in flat colours with outlines; ImageGen paints the sheet once; `fit` finds where each
machine landed on the painting; and each one wears its slot wherever it drives.

    blender --background --factory-startup --python farm_paint.py -- guide
    python  farm_paint.py paint               (outside Blender: one paid generation)
    python  farm_paint.py fit

`guide` writes `farm-vehicles/guide.png` (regenerated, never committed) and `slots.json`. `paint`
sends it with `farm-vehicles/prompt.txt` (the ground's `ortho/prompts/common.txt` rules first) and
keeps the master as `farm-vehicles/sheet.png` with its provenance. Once `farm-vehicles/accepted`
exists, `apply()` paints every listed machine. Dust and the combine's unloading auger
are left out of a slot: they are not the machine's roof.
"""
import json
import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / 'farm-vehicles'
REPO = HERE.parents[3]
SHEET_M = (64.0, 40.0)          # the sheet in metres; 1536x960 px, about 4 cm a pixel
SHEET_PX = (1536, 960)
NOT_ROOF = ('Farm dust', 'auger')

# slot centre on the sheet (metres from its centre), and which machines wear it (name prefixes,
# first match wins)
SLOTS = {
    'hauler': ((-15.5, 17.0), ('Road hauler',)),
    'tanker': ((16.0, 17.0), ('Road tanker',)),
    'box-truck': ((-26.0, 12.0), ('Box truck',)),
    'grain-truck': ((-14.0, 12.0), ('Farm grain truck',)),
    'farm-truck-hay': ((-2.0, 12.0), ('Farm truck hay',)),
    'farm-truck-produce': ((10.0, 12.0), ('Farm truck produce',)),
    'pickup-b': ((27.5, 12.0), ('Farm pickup parked',)),
    'pickup': ((20.0, 12.0), ('Farm pickup',)),
    'van': ((-28.0, 7.0), ('Road van',)),
    'tractor': ((-20.0, 7.0), ('Farm tractor parked',)),
    'plough': ((-6.0, 7.0), ('Plough tractor',)),
    'cart': ((8.0, 7.0), ('Grain cart',)),
    'combine': ((-24.0, -6.0), ('Combine',)),
    'sprayer': ((-6.0, -8.0), ('Field sprayer',)),
    'tender': ((23.0, -6.5), ('Crop tender',)),
}


def _vp():
    if str(HERE) not in sys.path:
        sys.path.insert(0, str(HERE))
    import vehicle_paint
    return vehicle_paint


# ---------------------------------------------------------------- inside Blender

def _roots():
    import bpy
    out = []
    for ob in bpy.data.objects:
        if ob.type != 'EMPTY' or ob.instance_type == 'COLLECTION':
            continue
        for slot, (_, prefixes) in SLOTS.items():
            if ob.name.startswith(prefixes):
                out.append((slot, ob))
                break
    return out


def _parts(root):
    return [(o, o.matrix_basis.copy()) for o in root.children
            if o.type == 'MESH' and not any(k in o.name for k in NOT_ROOF)]


def render_guide():
    import bpy
    from mathutils import Matrix
    sys.path.insert(0, str(HERE))
    os.environ['ORTHO_PAINT_OFF'] = '1'
    os.environ['VEHICLE_PAINT_OFF'] = '1'
    os.environ['FARM_PAINT_OFF'] = '1'
    import c4x_scene as c4
    c4.build()
    vp = _vp()
    roots = {}
    for slot, root in _roots():
        roots.setdefault(slot, root)
    scene = bpy.data.scenes.new('Farm sheet')
    for m in bpy.data.materials:
        bsdf = m.node_tree and next((n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
        if bsdf is not None and not bsdf.inputs['Base Color'].links:
            m.diffuse_color = bsdf.inputs['Base Color'].default_value
    sizes = {}
    for slot, ((sx, sy), _) in SLOTS.items():
        if slot not in roots:
            raise SystemExit('no machine in the scene wears ' + slot)
        parts = _parts(roots[slot])
        fx, fy, w, h = vp._footprint(parts)
        print('FARM_SLOT %s %.1f x %.1f m' % (slot, w, h), flush=True)
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
    print('FARM_GUIDE written', flush=True)


def apply():
    """Lay the accepted sheet over every listed machine. Returns machines painted."""
    import bpy
    sheet = OUT / 'sheet.png'
    if os.environ.get('FARM_PAINT_OFF') or not (OUT / 'accepted').exists() or not sheet.exists():
        return 0
    vp = _vp()
    img = bpy.data.images.load(str(sheet), check_existing=True)
    painted = {k: v['painted'] for k, v in json.loads((OUT / 'fit.json').read_text()).items()}
    pw, ph = SHEET_PX
    done = 0
    for slot, root in _roots():
        parts = _parts(root)
        if not parts or slot not in painted:
            continue
        fx, fy, w, h = vp._footprint(parts)
        x0, y0, x1, y1 = painted[slot]
        copies = {}
        for o, m in parts:
            uv = o.data.uv_layers.get('sheet') or o.data.uv_layers.new(name='sheet')
            rot = m.to_3x3()
            for poly in o.data.polygons:
                n = (rot @ poly.normal).normalized()
                inset = (n.x * vp.SIDE_INSET, n.y * vp.SIDE_INSET) if abs(n.z) < 0.5 else (0.0, 0.0)
                for li in poly.loop_indices:
                    p = m @ o.data.vertices[o.data.loops[li].vertex_index].co
                    p.x -= inset[0]
                    p.y -= inset[1]
                    col = x0 + (p.x - fx + w / 2) / w * (x1 - x0)
                    row = y0 + (fy + h / 2 - p.y) / h * (y1 - y0)
                    uv.data[li].uv = (col / pw, 1.0 - row / ph)
            for i, mat in enumerate(o.data.materials):
                if mat is None or mat.name.startswith('Tyre'):
                    continue
                if mat.name not in copies:
                    copies[mat.name] = vp._painted(mat.copy(), img)
                o.data.materials[i] = copies[mat.name]
        done += 1
    return done


# ---------------------------------------------------------------- outside Blender

GROW_PX = 8                     # how far a painted machine may lie outside its guide box


def _extent(mask, box, grow=GROW_PX, least=3):
    """The painted extent of the one machine whose guide box is `box`: inside that box grown by
    `grow` pixels (so a neighbour a few pixels off never joins it), every column and row holding at
    least `least` painted pixels. Thin parts count: a sprayer boom or a tender's spars are a few
    pixels wide but run the machine's whole width, and a coverage rule would cut them off."""
    h, w = mask.shape
    x0, y0 = max(0, box[0] - grow), max(0, box[1] - grow)
    x1, y1 = min(w, box[2] + grow), min(h, box[3] + grow)
    win = mask[y0:y1, x0:x1]
    cols = [i for i, n in enumerate(win.sum(0)) if n >= least]
    rows = [j for j, n in enumerate(win.sum(1)) if n >= least]
    return [x0 + cols[0], y0 + rows[0], x0 + cols[-1] + 1, y0 + rows[-1] + 1]


def fit():
    """Where each machine landed on the painted sheet. As `vehicle_paint.fit`, but each machine is
    measured only near its guide box: the farm sheet packs machines closer, and some are mostly
    thin booms and spars."""
    import numpy as np
    from PIL import Image
    sizes = json.loads((OUT / 'slots.json').read_text())
    px_m = SHEET_PX[0] / SHEET_M[0]
    masks = []
    for f in ('guide.png', 'sheet.png'):
        im = Image.open(OUT / f).convert('RGB')
        if im.size != SHEET_PX:                  # ImageGen keeps the aspect, not always the size
            if abs(im.size[0] / im.size[1] - SHEET_PX[0] / SHEET_PX[1]) > 0.01:
                raise SystemExit('%s is %dx%d, not the shape of %dx%d' % (f, *im.size, *SHEET_PX))
            im = im.resize(SHEET_PX, Image.LANCZOS)
        a = np.asarray(im).astype(int)
        bg = np.median(a.reshape(-1, 3), axis=0)
        masks.append(np.abs(a - bg).sum(2) > 30)
    out = {}
    for slot, ((sx, sy), _) in SLOTS.items():
        w, h = sizes[slot]
        g = [round(SHEET_PX[0] / 2 + (sx - w / 2) * px_m), round(SHEET_PX[1] / 2 - (sy + h / 2) * px_m),
             round(SHEET_PX[0] / 2 + (sx + w / 2) * px_m), round(SHEET_PX[1] / 2 - (sy - h / 2) * px_m)]
        p = _extent(masks[1], g)
        out[slot] = {'guide': g, 'painted': p}
        moved = max(abs(a - b) for a, b in zip(g, p))
        print('FARM_FIT %-18s guide %s painted %s  (%s px)' % (slot, g, p, moved))
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
           '--ref', '%s=%s' % (HERE / 'vehicles' / 'sheet.png',
                               'STYLE REFERENCE: the port\'s yard vehicles, already painted from above the same '
                               'way. Match their wear, grime, detail and finish; do not copy their layout')]
    raise SystemExit(subprocess.call(cmd))


if __name__ == '__main__':
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    if argv[0] == 'guide':
        render_guide()
    elif argv[0] == 'paint':
        paint()
    elif argv[0] == 'fit':
        fit()
