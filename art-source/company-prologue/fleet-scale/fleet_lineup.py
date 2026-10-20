"""Fleet scale lineup: every cinematic hull at its own metres_per_unit, side by side.

The owner's question is a feeling, not a number: does the Hound read as a trivial insect
beside the Legion? This puts every hull into one scene in metres, with one neutral
material, one studio light, a 1.8 m figure and a 10 m / 50 m grid, so size is the only
thing that differs between them, and renders six 1920x1080 frames plus a sizes table.

    python3 fleet_lineup.py                  # build, render, label, write the table
    python3 fleet_lineup.py --post mac       # ... and post the run to the Drive review folder
    python3 fleet_lineup.py --basis solved   # size hulls by ship_scale.py's solution instead

Sizes are never written here. Each hull's metres_per_unit is read from its package's
geometry.json. A hull with no package yet (no .blend and geometry.json) goes in as a
neutral box at ship_scale.py's solved size, labelled ESTIMATE, and switches to the real
model on the first run after its package lands. --basis solved uses ship_scale.py's
solution for every hull it registers, to preview a basis before it is published.

Run as plain Python it is the driver: it launches Blender on itself (blender_path.py),
then labels the renders with Pillow and writes sizes.csv / sizes.md. Inside Blender it
builds the scene and renders. Output goes under <repo>/build/fleet-scale/<date>/
(ignored); renders are never committed.
"""
import json
import os
import subprocess
import sys
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent                      # art-source/company-prologue
ART = PROLOGUE.parent                       # art-source
REPO = ART.parent
sys.path.insert(0, str(PROLOGUE))
sys.path.insert(0, str(ART))
import ship_scale  # noqa: E402  (plain Python: json and sys only)

W, H = 1920, 1080
FIGURE_M = 1.8
EYE_M = 1.65
GAP_M = 12.0
ALONGSIDE_M = 25.0      # frame 5: the Legion's starboard side to the Hound's port side

# What each hull is and where its model lives, in order of preference. No sizes.
#   export:   (blend, collection, root) of a cinematic export, used when it has been built
#   geometry: the package's tracked geometry .blend (collection None = every object)
#   json:     the geometry.json whose metres_per_unit sizes it
#   scale_key: its row in ship_scale.HULLS, if registered there
#   vanilla:  the game's .ship id, for an estimate box's beam
HULLS = [
    dict(key='onslaught', name='Onslaught', kind='ship', scale_key='Onslaught', vanilla='onslaught',
         drive_build=('onslaught', 'onslaught.blend', 'ONSLAUGHT | clean', 'ONSLAUGHT | clean | root'),
         geometry=('onslaught-geometry/onslaught-geometry.blend', None),
         json='onslaught-geometry/geometry.json'),
    dict(key='paragon', name='Paragon', kind='ship', scale_key='Paragon', vanilla='paragon',
         drive_build=('paragon', 'paragon.blend', 'PARAGON | clean', 'PARAGON | clean | root'),
         geometry=('paragon-geometry/paragon-geometry.blend', None), json='paragon-geometry/geometry.json'),
    dict(key='legion', name='Legion (XIV)', kind='ship', scale_key='Legion (XIV)', vanilla='legion',
         geometry=('legion-geometry/legion-geometry.blend', None), json='legion-geometry/geometry.json'),
    dict(key='lasher', name='Lasher', kind='ship', scale_key='Lasher', vanilla='lasher',
         drive_build=('lasher', 'lasher.blend', 'LASHER', 'LASHER | root'),
         geometry=('lasher-geometry/lasher-geometry.blend', None), json='lasher-geometry/geometry.json'),
    dict(key='colossus', name='Colossus', kind='ship', scale_key='Colossus', vanilla='colossus',
         geometry=('colossus-geometry/colossus-geometry.blend', None), json='colossus-geometry/geometry.json'),
    dict(key='hound', name='Hound (A)', kind='ship', scale_key='Hound (A)',
         export=('hound-export/clean/hound-a-clean.blend', 'HOUND (A) | clean', 'HOUND (A) | clean | root'),
         geometry=('hound-geometry/hound-geometry.blend', None), json='hound-geometry/geometry.json'),
    dict(key='second-wind', name='Second Wind (patched)', kind='ship', scale_key='Second Wind (Cerberus)',
         export=('second-wind-cinematic/second-wind-states/patched/second-wind-patched.blend',
                 'SECOND WIND | patched', 'SECOND WIND | patched | root'),
         geometry=('second-wind-geometry/states/patched/second-wind-geometry.blend', None),
         json='second-wind-geometry/states/patched/geometry.json'),
    dict(key='mudskipper', name='Mudskipper', kind='ship',
         geometry=('mudskipper-geometry/mudskipper-geometry.blend', None), json='mudskipper-geometry/geometry.json'),
    dict(key='shepherd', name='Shepherd (Morrow)', kind='ship',
         geometry=('shepherd-geometry/shepherd-geometry.blend', None), json='shepherd-geometry/geometry.json'),
    dict(key='hermes', name='Hermes', kind='ship',
         geometry=('hermes-geometry/hermes-geometry.blend', None), json='hermes-geometry/geometry.json'),
    dict(key='broadsword', name='Broadsword', kind='fighter',
         geometry=('broadsword-geometry/broadsword-geometry.blend', 'BROADSWORD | geometry'),
         json='broadsword-geometry/geometry.json'),
    # The Asharu battle's hulls. Built .blends are on the Drive (drive_build), except the
    # Manticore's, which is built locally by --build-missing (local_build: script, build/<dir>, blend).
    dict(key='wolf', name='Wolf (P)', kind='ship', scale_key='Wolf (P)',
         drive_build=('wolf-p', 'wolf-p.blend', 'WOLF_P', 'WOLF_P | root'),
         geometry=('wolf-geometry/wolf-geometry.blend', None), json='wolf-geometry/geometry.json'),
    dict(key='manticore', name='Manticore (P)', kind='ship', scale_key='Manticore (P)',
         local_build=('manticore-geometry/build_manticore.py', 'manticore', 'manticore.blend',
                      'MANTICORE', 'MANTICORE | root'),
         geometry=('manticore-geometry/manticore-geometry.blend', None), json='manticore-geometry/geometry.json'),
    dict(key='kite', name='Kite (P)', kind='ship',
         drive_build=('kite-p', 'kite-p.blend', 'KITE (P)', 'KITE (P) | root'),
         geometry=('kite-geometry/kite-geometry.blend', None), json='kite-geometry/geometry.json'),
    dict(key='falcon', name='Falcon (P)', kind='ship',
         drive_build=('falcon-p', 'falcon-p.blend', 'FALCON (P)', 'FALCON (P) | root'),
         geometry=('falcon-geometry/falcon-geometry.blend', None), json='falcon-geometry/geometry.json'),
    dict(key='colossus-mk3', name='Colossus Mk.III', kind='ship',
         drive_build=('colossus-mk3', 'colossus-mk3.blend', 'COLOSSUS MK.III', 'COLOSSUS MK.III | root'),
         geometry=('colossus-mk3-geometry/colossus-mk3-geometry.blend', None),
         json='colossus-mk3-geometry/geometry.json'),
    dict(key='tarsus', name='Tarsus', kind='ship',
         drive_build=('tarsus', 'clean/tarsus.blend', 'TARSUS', 'TARSUS | root'),
         geometry=('tarsus-geometry/tarsus-geometry.blend', None), json='tarsus-geometry/geometry.json'),
    dict(key='phaeton', name='Phaeton', kind='ship',
         drive_build=('phaeton', 'intact/phaeton.blend', 'PHAETON', 'PHAETON | root'),
         geometry=('phaeton-geometry/phaeton-geometry.blend', None), json='phaeton-geometry/geometry.json'),
] + [dict(key=f, name=f.capitalize(), kind='fighter',
          geometry=(f'fighters-geometry/{f}/{f}-geometry.blend', f'{f.upper()} | geometry'),
          json=f'fighters-geometry/{f}/geometry.json')
     for f in ('thunder', 'dagger', 'longbow', 'talon', 'wasp')]

# The class lineup (--class-lineup): rows top to bottom, each a role class.
CLASSES = [
    ('fighters', 'Fighters and boats', ('broadsword', 'thunder', 'dagger', 'longbow', 'talon', 'wasp', 'hermes')),
    ('frigates', 'Frigates', ('kite', 'hound', 'second-wind', 'mudskipper', 'shepherd', 'wolf', 'lasher')),
    ('destroyers', 'Destroyers', ('manticore',)),
    ('freighters', 'Freighters and tankers', ('phaeton', 'tarsus', 'colossus-mk3', 'colossus')),
    ('capitals', 'Cruisers and capitals', ('falcon', 'legion', 'onslaught', 'paragon')),
]
# The game's sprite each model was read from: (.ship id, .skin id or None).
SPRITES = {
    'onslaught': ('onslaught', None), 'paragon': ('paragon', None), 'legion': ('legion', 'legion_xiv'),
    'lasher': ('lasher', None), 'colossus': ('colossus', None), 'hound': ('hound', 'hound_hegemony'),
    'second-wind': ('warhound', None), 'mudskipper': ('mudskipper', None), 'shepherd': ('shepherd', None),
    'hermes': ('hermes', None), 'broadsword': ('broadsword', None), 'thunder': ('thunder', None),
    'dagger': ('dagger', None), 'longbow': ('longbow', None), 'talon': ('talon', None), 'wasp': ('wasp', None),
    'wolf': ('wolf', 'wolf_d_pirates'), 'manticore': ('manticore', 'manticore_pirates'),
    'kite': ('kite', 'kite_pirates'), 'falcon': ('falcon', 'falcon_p'), 'colossus-mk3': ('colossus3', None),
    'tarsus': ('tarsus', None), 'phaeton': ('phaeton', None),
}
SPRITE_REFERENCE = 'hound'      # the sprite lineup is scaled so this hull's sprite matches its model


def scale_solution(key):
    return ship_scale.record(key) if key in ship_scale.HULLS else None


def resolve(h, basis):
    """Which model and which metres_per_unit a hull uses this run, and why."""
    out = dict(h)
    exp = h.get('export')
    geo_blend = PROLOGUE/h['geometry'][0]
    gjson = PROLOGUE/h['json']
    sol = scale_solution(h.get('scale_key'))
    out['solved_mpu'] = sol['solved_metres_per_unit'] if sol else None
    head_build = drive_build(h['drive_build'], gjson.parent) if h.get('drive_build') and gjson.exists() else None
    lb = h.get('local_build')
    if lb and (local_build_path(lb)).exists() and gjson.exists():
        out['source'] = dict(blend=str(local_build_path(lb)), collection=lb[3], root=lb[4])
        out['source_label'] = f'build/{lb[1]}/{lb[2]} (local build of {lb[0]})'
    elif exp and (PROLOGUE/exp[0]).exists() and gjson.exists():
        out['source'] = dict(blend=str(PROLOGUE/exp[0]), collection=exp[1], root=exp[2])
        out['source_label'] = h['export'][0]
    elif head_build and head_build[2]:          # the Drive build of the package's latest commit
        out['source'] = dict(blend=str(head_build[0]), collection=h['drive_build'][2], root=h['drive_build'][3])
        out['source_label'] = head_build[1]
    elif geo_blend.exists() and gjson.exists():
        out['source'] = dict(blend=str(geo_blend), collection=h['geometry'][1], root=None)
        out['source_label'] = h['geometry'][0]
    elif head_build:
        blend, label, _ = head_build
        out['source'] = dict(blend=str(blend), collection=h['drive_build'][2], root=h['drive_build'][3])
        out['source_label'] = label
    else:
        out['source'] = None
        out['source_label'] = 'no package yet: estimate box'
    if out['source']:
        out['published_mpu'] = json.load(open(gjson))['metres_per_unit']
        out['estimate'] = False
        if basis == 'solved' and sol:
            out['mpu'], out['mpu_from'] = out['solved_mpu'], 'ship_scale.py solution (preview, not published)'
        else:
            out['mpu'], out['mpu_from'] = out['published_mpu'], h['json']
    else:
        if not sol:
            return None                       # nothing to show and nothing to estimate from
        out['published_mpu'] = None
        out['estimate'] = True
        out['mpu'], out['mpu_from'] = sol['solved_metres_per_unit'], 'ship_scale.py estimate (no model)'
        reg = ship_scale.HULLS[h['scale_key']]
        k = ship_scale.ESTIMATE_K[reg['cls']]
        area = (reg['units3']/k)**(2/3)                 # sprite alpha area the estimate came from
        spec = vanilla_spec(h.get('vanilla'))
        width_units = spec['width'] if spec else area/reg['length_units']*1.25
        out['units3'] = reg['units3']
        out['box_units'] = [reg['length_units'], width_units, reg['units3']/area]
        out['sprite'] = spec['sprite'] if spec else None
    return out


def drive_build(spec, package):
    """A package whose built .blend is not tracked publishes it to the shared Drive under
    builds/<hull>/<short commit>/. Prefer the build of the package's latest commit; else the newest."""
    root = drive_root()
    hull, blend = spec[0], spec[1]
    if not root or not (root/'builds'/hull).is_dir():
        return None
    builds = [d for d in (root/'builds'/hull).iterdir() if (d/blend).exists() and not (d/'SUPERSEDED.txt').exists()]
    if not builds:
        return None
    try:
        head = subprocess.check_output(['git', '-C', str(REPO), 'log', '-1', '--format=%h', '--', str(package)],
                                       text=True).strip()
    except Exception:
        head = ''
    def current(d):
        # the build's commit holds the same package files as HEAD (it may be a later merge)
        if head and (head.startswith(d.name) or d.name.startswith(head)):
            return True
        return subprocess.call(['git', '-C', str(REPO), 'diff', '--quiet', d.name, 'HEAD', '--', str(package)],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) == 0

    match = [d for d in sorted(builds, key=lambda d: -(d/blend).stat().st_mtime) if current(d)]
    pick = match[0] if match else max(builds, key=lambda d: (d/blend).stat().st_mtime)
    note = '' if match else f' (not the package head {head})'
    return pick/blend, f'Drive builds/{hull}/{pick.name}/{blend}{note}', bool(match)


def local_build_path(spec):
    return REPO/'build'/spec[1]/spec[2]


def build_missing():
    """Run the package build for every hull whose model is only a local build output and is missing
    (the Manticore's build/manticore/manticore.blend), so the lineup shows the model, not an estimate."""
    from blender_path import BLENDER
    for h in HULLS:
        lb = h.get('local_build')
        if not lb or local_build_path(lb).exists():
            continue
        script = PROLOGUE/lb[0]
        print(f"[fleet] building {h['name']}: {lb[0]} -> build/{lb[1]}/{lb[2]}", flush=True)
        subprocess.check_call([BLENDER, '--background', '--factory-startup', '--python-exit-code', '1',
                               '--python', str(script), '--', '--views', 'none',
                               '--out', str(local_build_path(lb).parent)], cwd=str(script.parent))


def sprite_dims(key):
    """The game sprite behind a hull: its path and its opaque extent in pixels, (length along the bow,
    width), read from the install (None without the game or Pillow)."""
    if key not in SPRITES:
        return None
    try:
        import re
        from PIL import Image
        from starsector_path import GAME_CORE
        ship, skin = SPRITES[key]
        f = GAME_CORE/'data'/'hulls'/(f'skins/{skin}.skin' if skin else f'{ship}.ship')
        sprite = re.search(r'"spriteName"\s*:\s*"([^"]+)"', f.read_text()).group(1)   # skins are loose JSON
        path = GAME_CORE/sprite
        x0, y0, x1, y1 = Image.open(path).convert('RGBA').getchannel('A').point(lambda a: 255 if a > 127 else 0).getbbox()
        return dict(path=str(path), sprite=sprite, length_px=y1 - y0, width_px=x1 - x0)
    except Exception as e:
        print(f'[fleet] no sprite for {key}: {e}')
        return None


def vanilla_spec(ship_id):
    """The game's .ship width and sprite path, for an estimate's footprint (None without the game)."""
    if not ship_id:
        return None
    try:
        import re
        from starsector_path import GAME_CORE
        t = re.sub(r'#.*', '', (GAME_CORE/'data'/'hulls'/f'{ship_id}.ship').read_text())
        d = json.loads(re.sub(r',\s*([}\]])', r'\1', t))
        sprite = GAME_CORE/d['spriteName']
        return dict(width=float(d['width']), sprite=str(sprite) if sprite.exists() else None)
    except Exception:
        return None


# ----------------------------------------------------------------------------- Blender

def blender_main(cfg):
    import math
    import bpy
    from bpy_extras.object_utils import world_to_camera_view
    from mathutils import Vector, Matrix

    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    out = Path(cfg['raw_dir'])
    out.mkdir(parents=True, exist_ok=True)

    # ---- render settings: Cycles on the GPU where there is one
    scene.render.engine = 'CYCLES'
    scene.render.resolution_x, scene.render.resolution_y = W, H
    scene.render.resolution_percentage = 100
    scene.cycles.samples = cfg['samples']
    scene.cycles.use_denoising = True
    scene.view_settings.view_transform = 'AgX'
    try:
        prefs = bpy.context.preferences.addons['cycles'].preferences
        for dev in ('METAL', 'OPTIX', 'CUDA', 'HIP'):
            try:
                prefs.compute_device_type = dev
                prefs.get_devices()
                if any(d.type == dev for d in prefs.devices):
                    for d in prefs.devices:
                        d.use = True
                    scene.cycles.device = 'GPU'
                    break
            except TypeError:
                continue
    except Exception:
        pass

    # ---- one neutral material for every hull, one for the figure, one for estimate boxes
    def principled(name, rgb, rough=.55, metal=0.0):
        m = bpy.data.materials.new(name)
        m.use_nodes = True
        b = m.node_tree.nodes['Principled BSDF']
        b.inputs['Base Color'].default_value = (*rgb, 1)
        b.inputs['Roughness'].default_value = rough
        b.inputs['Metallic'].default_value = metal
        return m

    hull_mat = principled('Fleet scale hull', (.55, .56, .57))
    est_mat = principled('Fleet scale estimate', (.62, .60, .55), rough=.8)
    fig_mat = principled('Fleet scale figure', (.95, .45, .08), rough=.6)

    # For section views: a darker grey, and a face seen from behind is transparent, so a hull cut by
    # the camera's clip plane reads as a cutaway, lets light into what it encloses, and what stands
    # inside it (in the ordinary grey) reads against it.
    cut_mat = principled('Fleet scale hull, cutaway', (.26, .27, .28))
    nt = cut_mat.node_tree
    geo = nt.nodes.new('ShaderNodeNewGeometry')
    mix = nt.nodes.new('ShaderNodeMixShader')
    clear = nt.nodes.new('ShaderNodeBsdfTransparent')
    out_node = next(n for n in nt.nodes if n.type == 'OUTPUT_MATERIAL')
    nt.links.new(geo.outputs['Backfacing'], mix.inputs[0])
    nt.links.new(nt.nodes['Principled BSDF'].outputs[0], mix.inputs[1])
    nt.links.new(clear.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out_node.inputs['Surface'])

    def cutaway(key, on):
        for o in hulls[key]['objs']:
            if o.type == 'MESH':
                o.data.materials[0] = cut_mat if on else hull_mat

    def grid_material(name, a, b):
        """Grey grid in object coordinates a/b: 10 m lines, 50 m majors."""
        m = bpy.data.materials.new(name)
        m.use_nodes = True
        nt = m.node_tree
        N, L = nt.nodes, nt.links
        bsdf = N['Principled BSDF']
        bsdf.inputs['Roughness'].default_value = .9
        tc = N.new('ShaderNodeTexCoord')
        sep = N.new('ShaderNodeSeparateXYZ')
        L.new(tc.outputs['Object'], sep.inputs[0])

        def op(o, x, y=None):
            n = N.new('ShaderNodeMath')
            n.operation = o
            for i, v in enumerate((x, y)):
                if v is None:
                    continue
                if isinstance(v, (int, float)):
                    n.inputs[i].default_value = v
                else:
                    L.new(v, n.inputs[i])
            return n.outputs[0]

        def line(axis, period, half_width):
            f = op('FRACT', op('DIVIDE', sep.outputs[axis], period))
            g = op('ABSOLUTE', op('SUBTRACT', f, .5))
            return op('GREATER_THAN', g, .5 - half_width/period)

        minor = op('MAXIMUM', line(a, 10, .18), line(b, 10, .18))
        major = op('MAXIMUM', line(a, 50, .5), line(b, 50, .5))
        v = op('ADD', op('ADD', .035, op('MULTIPLY', minor, .09)), op('MULTIPLY', major, .22))
        L.new(v, bsdf.inputs['Base Color'])
        return m

    def mesh_box(name, size, mat, loc=(0, 0, 0)):
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        o = bpy.context.object
        o.name = name
        o.scale = size
        bpy.ops.object.transform_apply(scale=True)
        o.data.materials.append(mat)
        return o

    # ---- the 1.8 m figure: legs, torso, arms, head
    def figure(name, loc):
        parts = []
        for nm, size, c in (('legs', (.24, .34, .86), (0, 0, .43)),
                            ('torso', (.24, .44, .62), (0, 0, 1.17)),
                            ('arms', (.16, .62, .6), (0, 0, 1.15))):
            parts.append(mesh_box(f'{name} {nm}', size, fig_mat, c))
        bpy.ops.mesh.primitive_uv_sphere_add(radius=.11, location=(0, 0, FIGURE_M - .11), segments=16, ring_count=8)
        head = bpy.context.object
        head.data.materials.append(fig_mat)
        parts.append(head)
        bpy.ops.object.select_all(action='DESELECT')
        for p in parts:
            p.select_set(True)
        bpy.context.view_layer.objects.active = parts[0]
        bpy.ops.object.join()
        o = bpy.context.object
        o.name = name
        o.location = loc
        return o

    def estimate_slab(h):
        """The game sprite's silhouette extruded to the estimate's mean thickness, in hull units:
        the plan area the estimate was taken from, at the volume ship_scale.py gives it."""
        import bmesh
        import numpy as np
        img = bpy.data.images.load(h['sprite'])
        w, hgt = img.size
        a = np.array(img.pixels[:], dtype=np.float32).reshape(hgt, w, 4)[:, :, 3] > .5
        step = 2
        cells = a[:hgt//step*step, :w//step*step].reshape(hgt//step, step, w//step, step).mean(axis=(1, 3)) > .5
        thick = h['units3']/float(a.sum())
        bm = bmesh.new()
        verts = {}

        def v(r, c):
            if (r, c) not in verts:
                # sprite up is the bow (+X); sprite left is port (+Y)
                verts[(r, c)] = bm.verts.new(((r*step - hgt/2), (w/2 - c*step), 0))
            return verts[(r, c)]

        for r, c in zip(*np.nonzero(cells)):
            bm.faces.new((v(r, c), v(r, c + 1), v(r + 1, c + 1), v(r + 1, c)))
        ext = bmesh.ops.extrude_face_region(bm, geom=bm.faces[:])
        bmesh.ops.translate(bm, verts=[e for e in ext['geom'] if isinstance(e, bmesh.types.BMVert)],
                            vec=(0, 0, thick))
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
        me = bpy.data.meshes.new(f"FLEET | {h['key']} | estimate silhouette")
        bm.to_mesh(me)
        bm.free()
        me.materials.append(est_mat)
        o = bpy.data.objects.new(me.name, me)
        scene.collection.objects.link(o)
        bpy.data.images.remove(img)
        return o

    ref_mat = principled('Fleet scale reference', (.6, .58, .54), rough=.8)

    def build_references():
        """Real-world objects at their real size, as simple primitives in one neutral grey: the
        silhouette at the right size is what matters. Added to the end of the lineup rows."""
        def part(name, size, loc, kind='box', rot=None):
            if kind == 'box':
                bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
            elif kind == 'cyl':
                bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=1, depth=1, location=loc,
                                                    rotation=(0, math.radians(90), 0))
            elif kind == 'frustum':                 # lying along X: base size[1] square, top size[3]
                L, b, t = size[0], size[1]/2, size[3]/2
                vs = [(-L/2, y, z) for y, z in ((-b, -b), (b, -b), (b, b), (-b, b))] + \
                     [(L/2, y, z) for y, z in ((-t, -t), (t, -t), (t, t), (-t, t))]
                fs = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
                me = bpy.data.meshes.new(name)
                me.from_pydata([(x + loc[0], y + loc[1], z + loc[2]) for x, y, z in vs], [], fs)
                o = bpy.data.objects.new(name, me)
                scene.collection.objects.link(o)
                o.data.materials.append(ref_mat)
                return o
            o = bpy.context.object
            o.name = name
            o.scale = size if kind == 'box' else (size[2]/2, size[1]/2, size[0])
            bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
            o.data.materials.append(ref_mat)
            return o

        specs = [
            ('ref-container', '20 ft container', '6.1 x 2.4 x 2.6 m', lambda: [
                part('container', (6.1, 2.4, 2.6), (0, 0, 1.3))]),
            ('ref-747', 'Boeing 747', '70.7 m long, 64.4 m span', lambda: [
                part('747 fuselage', (70.7, 6.5, 6.5), (0, 0, 5.6), 'cyl'),
                part('747 wing', (13, 64.4, .9), (2, 0, 4.2)),
                part('747 tailplane', (7, 22, .6), (-31, 0, 8)),
                part('747 fin', (9, .7, 10.5), (-30.5, 0, 14.15))] + [
                part(f'747 engine {i}', (6, 2.8, 2.8), (6 - abs(y)*.25, y, 2.6), 'cyl') for i, y in
                enumerate((-21, -11, 11, 21))]),
            ('ref-iss', 'ISS', '109 m truss', lambda: [
                part('ISS truss', (109, 5, 4), (0, 0, 2))] + [
                part(f'ISS array {i}', (12, 35, .2), (x, y, 2)) for i, (x, y) in
                enumerate((x, y) for x in (-46, -32, 32, 46) for y in (-20, 20))]),
            ('ref-eiffel', 'Eiffel Tower, lying flat', '330 m', lambda: [
                part('Eiffel', (330, 125, 0, 8), (0, 0, 62.5), 'frustum')]),
            ('ref-nimitz', 'Nimitz-class carrier', '333 m, 77 m flight deck', lambda: [
                part('Nimitz hull', (317, 41, 18), (0, 0, 9)),
                part('Nimitz flight deck', (333, 77, 1.5), (0, 6, 18.75)),
                part('Nimitz island', (28, 10, 22), (25, -28, 30.5))]),
            ('ref-pitch', 'Football pitch', '105 x 68 m', lambda: [
                part('pitch', (105, 68, .1), (0, 0, .05))]),
        ]
        keys = []
        for key, name, desc, make in specs:
            col = collection(f'FLEET | {key}')
            root = bpy.data.objects.new(f'FLEET | {key} | root', None)
            col.objects.link(root)
            meshes = make()
            for o in meshes:
                for c in o.users_collection:
                    c.objects.unlink(o)
                col.objects.link(o)
                o.parent = root
            bpy.context.view_layer.update()
            pts = [o.matrix_world @ Vector(c) for o in meshes for c in o.bound_box]
            mn = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
            mx = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
            sz = mx - mn
            hulls[key] = dict(cfg=dict(key=key, name=name, desc=desc, reference=True, estimate=False,
                                       size_m=[round(sz.x, 2), round(sz.y, 2), round(sz.z, 2)]),
                              col=col, root=root, objs=meshes, mn=mn, mx=mx)
            keys.append(key)
        return keys

    # ---- hulls
    def collection(name, parent=None):
        c = bpy.data.collections.new(name)
        (parent or scene.collection).children.link(c)
        return c

    hulls = {}
    for h in cfg['hulls']:
        col = collection(f"FLEET | {h['key']}")
        src = h['source']
        if src:
            with bpy.data.libraries.load(src['blend'], link=False) as (lib, dst):
                if src['collection'] and src['collection'] in lib.collections:
                    dst.collections = [src['collection']]
                    picked = 'collection'
                else:
                    dst.objects = list(lib.objects)
                    picked = 'objects'
            if picked == 'collection':
                col.children.link(dst.collections[0])
                objs = list(dst.collections[0].all_objects)
            else:
                objs = [o for o in dst.objects if o is not None]
                for o in objs:
                    col.objects.link(o)
            for o in list(objs):
                if o.type in ('CAMERA', 'LIGHT', 'LIGHT_PROBE', 'SPEAKER'):
                    objs.remove(o)
                    bpy.data.objects.remove(o)
            root = bpy.data.objects.get(src['root']) if src['root'] else None
            if root is None:
                root = bpy.data.objects.new(f"FLEET | {h['key']} | root", None)
                col.objects.link(root)
                members = set(objs)
                for o in objs:
                    if o.parent is None or o.parent not in members:
                        # An appended object's matrix_world is not evaluated yet: keep its own
                        # transform as the transform under the (identity) root.
                        basis = o.matrix_basis.copy()
                        o.parent = root
                        o.matrix_parent_inverse = Matrix.Identity(4)
                        o.matrix_basis = basis
            meshes = []
            for o in objs:
                if o.type in ('MESH', 'CURVE', 'SURFACE', 'META', 'FONT'):
                    if o.type == 'MESH':
                        o.data.materials.clear()
                        o.data.materials.append(hull_mat)
                        for s in o.material_slots:
                            s.link = 'DATA'
                    if not o.hide_render:
                        meshes.append(o)
                elif o.type == 'EMPTY':
                    o.hide_render = True
        else:
            root = bpy.data.objects.new(f"FLEET | {h['key']} | root", None)
            col.objects.link(root)
            box = estimate_slab(h) if h.get('sprite') else None
            if box is None:
                l, b, t = h['box_units']
                box = mesh_box(f"FLEET | {h['key']} | estimate box", (l, b, t), est_mat)
            for c in box.users_collection:
                c.objects.unlink(box)
            col.objects.link(box)
            box.parent = root
            meshes = [box]
        root.location = (0, 0, 0)
        root.rotation_euler = (0, 0, 0)
        root.scale = (h['mpu'],)*3
        bpy.context.view_layer.update()
        mn = Vector((1e18,)*3)
        mx = Vector((-1e18,)*3)
        for o in meshes:
            for c in o.bound_box:
                w = o.matrix_world @ Vector(c)
                mn = Vector(map(min, mn, w))
                mx = Vector(map(max, mx, w))
        size = mx - mn
        # A pending basis (--pending) names a length the owner chose that has not been published yet.
        # It scales the hull to that length until its geometry.json lands within tolerance of it,
        # after which the published value is used and the hull drops its 'pending' label.
        target = h.get('pending_length_m')
        tol = h.get('pending_tolerance', cfg.get('pending_tolerance', .05))
        h['pending'] = bool(target) and abs(size.x/target - 1) > tol
        if h['pending']:
            h['published_length_m'] = round(size.x, 2)
            h['ghost_mpu'] = h['mpu']           # the --size preview draws the current size as a ghost
            f = target/size.x
            h['mpu'] = round(h['mpu']*f, 4)
            h['mpu_from'] = f"{cfg['basis_label']}: {target:g} m by hand until published ({h['pending_label']})"
            root.scale = (h['mpu'],)*3
            h['ghost_box'] = [list(mn), list(mx)]
            mn, mx = mn*f, mx*f
            size = mx - mn
        hulls[h['key']] = dict(cfg=h, col=col, root=root, objs=meshes, mn=mn.copy(), mx=mx.copy())
        h['size_m'] = [round(size.x, 2), round(size.y, 2), round(size.z, 2)]
        print(f"[fleet] {h['name']:24} {h['mpu']:.3f} m/u  {size.x:6.1f} x {size.y:6.1f} x {size.z:5.1f} m"
              f"  {'ESTIMATE' if h['estimate'] else h['source_label']}{'  (PENDING ' + cfg['basis_label'] + ')' if h['pending'] else ''}",
              flush=True)

    def place(key, at, ref='min'):
        """Move a hull so its bbox (min corner, or centre) lands at `at`."""
        r = hulls[key]
        base = r['mn'] if ref == 'min' else (r['mn'] + r['mx'])/2
        r['root'].location = Vector(at) - base

    def world_bbox(key):
        r = hulls[key]
        return r['mn'] + r['root'].location, r['mx'] + r['root'].location

    def show(keys):
        for k, r in hulls.items():
            r['col'].hide_render = k not in keys

    # ---- light: one sun and a dim grey world
    world = bpy.data.worlds.new('Fleet scale world')
    scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes['Background']
    bg.inputs['Color'].default_value = (.06, .065, .07, 1)
    bg.inputs['Strength'].default_value = 1.0
    sun_data = bpy.data.lights.new('Fleet scale sun', 'SUN')
    sun_data.energy = 3.2
    sun_data.angle = math.radians(2)
    sun = bpy.data.objects.new('Fleet scale sun', sun_data)
    scene.collection.objects.link(sun)

    def aim_sun(direction):
        sun.rotation_euler = Vector(direction).normalized().to_track_quat('-Z', 'Y').to_euler()

    cam_data = bpy.data.cameras.new('Fleet scale camera')
    cam = bpy.data.objects.new('Fleet scale camera', cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam_data.clip_start, cam_data.clip_end = .05, 20000

    def look(loc, target, lens=None, ortho=None):
        cam.location = loc
        cam.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
        if ortho:
            cam_data.type, cam_data.ortho_scale = 'ORTHO', ortho
        else:
            cam_data.type, cam_data.lens = 'PERSP', lens
        cam_data.sensor_width, cam_data.sensor_fit = 36, 'HORIZONTAL'

    def px(p):
        bpy.context.view_layer.update()
        v = world_to_camera_view(scene, cam, Vector(p))
        return [round(v.x*W, 1), round((1 - v.y)*H, 1), round(v.z, 2)]

    frames = []

    def shoot(n, file):
        scene.render.filepath = str(out/file)
        if not cfg.get('frames') or n in cfg['frames']:
            print(f'[fleet] rendering {file}  camera {tuple(round(v, 1) for v in cam.location)}', flush=True)
            bpy.ops.render.render(write_still=True)

    def render(n, slug, title, subtitle, labels, rects=()):
        """One whole-frame render. A frame is a list of parts: a raw render's rows [crop],
        pasted at row `dest` of the labelled 1920x1080 frame."""
        file = f'{n:02d}-{slug}.png'
        shoot(n, file)
        frames.append(dict(n=n, file=file, title=title, subtitle=subtitle,
                           parts=[dict(file=file, crop=[0, H], dest=0, labels=labels, rects=list(rects))]))

    if cfg.get('class_lineup'):
        class_lineup(cfg, hulls, scene, cam, look, show, out)
        return

    order = sorted(hulls, key=lambda k: -(hulls[k]['mx'].x - hulls[k]['mn'].x))
    ships = [k for k in order]
    refs = build_references() if cfg.get('references') else []
    lineup = ships + refs

    def tag(k):
        h = hulls[k]['cfg']
        if h.get('reference'):
            return [h['name'], h['desc']], False
        lines = [h['name'], f"{h['size_m'][0]:.1f} m  ·  {h['mpu']:.3f} m/u"]
        if h['estimate']:
            lines.append('ESTIMATE')
        if h.get('pending'):
            lines.append(h['pending_label'])
        elif h.get('pending_note'):
            lines.append(h['pending_note'])
        return lines, h['estimate']

    # ---- lineup layout (shared by broadside and top-down): longest first, left to right
    x = 0.0
    figs = []
    for k in lineup:
        lx = hulls[k]['mx'].x - hulls[k]['mn'].x
        if k == refs[0] if refs else False:
            x += 3*GAP_M                    # a wider gap between the fleet and the references
        place(k, (x, -(hulls[k]['mn'].y + hulls[k]['mx'].y)/2 + hulls[k]['mn'].y, 0))
        lo, hi = world_bbox(k)
        if k in ships:
            figs.append(figure(f'Figure {k}', (lo.x - 3.0, lo.y - 4.0, 0)))
        x += lx + GAP_M
    span = x - GAP_M
    max_beam = max(world_bbox(k)[1].y - world_bbox(k)[0].y for k in lineup)
    max_h = max(world_bbox(k)[1].z for k in lineup)

    ground = None
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 0, 0))
    ground = bpy.context.object
    ground.name = 'Fleet scale ground grid'
    ground.scale = (span + 400, max_beam*3 + 400, 1)
    ground.location = (span/2, 0, -0.02)
    bpy.ops.object.transform_apply(scale=True, location=False)
    ground.data.materials.append(grid_material('Fleet scale ground grid', 'X', 'Y'))
    # Object coordinates are the plane's own: move the texture with the plane's location.
    gx = ground.location.x
    ground.location = (0, 0, -.02)
    for v in ground.data.vertices:
        v.co.x += gx

    wall_y = max_beam/2 + 60
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 0, 0), rotation=(math.radians(90), 0, 0))
    wall = bpy.context.object
    wall.name = 'Fleet scale height wall'
    bpy.ops.object.transform_apply(rotation=True)
    for v in wall.data.vertices:
        v.co.x = v.co.x*(span + 400) + span/2
        v.co.z = (v.co.z + .5)*(max_h*3 + 200)
    wall.location = (0, wall_y, 0)
    wall.data.materials.append(grid_material('Fleet scale height wall', 'X', 'Z'))

    lineup_objs = [ground, wall] + figs
    every = set(hulls)

    def lineup_visible(on):
        for o in lineup_objs:
            o.hide_render = not on

    # 01 broadside, orthographic from starboard: the whole fleet above, the small end magnified below
    show(every)
    lineup_visible(True)
    aim_sun((.35, .75, -.55))

    def broadside(keys, x0, x1, ground_row, group_below=0.0):
        """Labels for `keys`; hulls shorter than group_below share one label (the magnified
        panel names them one by one)."""
        ortho = (x1 - x0)*1.04
        m = ortho/W
        cz = (ground_row - H/2)*m
        look(((x0 + x1)/2, -5000, cz), ((x0 + x1)/2, 0, cz), ortho=ortho)
        labs = []
        tiny = [k for k in keys if k in ships and hulls[k]['mx'].x - hulls[k]['mn'].x < group_below]
        if tiny:
            lo = min(world_bbox(k)[0].x for k in tiny)
            hi = max(world_bbox(k)[1].x for k in tiny)
            labs.append(dict(lines=['Fighters and Hermes', f'{len(tiny)} craft, named in the panel below'],
                             estimate=False, anchor=px(((lo + hi)/2, -20, 5)), place='above'))
        for k in keys:
            if k in tiny:
                continue
            lo, hi = world_bbox(k)
            lines, est = tag(k)
            labs.append(dict(lines=lines, estimate=est, anchor=px(((lo.x + hi.x)/2, lo.y, hi.z)), place='above'))
        return m, labs

    gy = int(H*.8)
    mpp, labels = broadside([k for k in lineup if k != 'ref-pitch'], 0, span, gy, group_below=25.0)
    shoot(1, '01a-broadside-fleet.png')
    small = ships[ships.index('hound'):] if 'hound' in ships else ships[len(ships)//2:]
    small += [k for k in refs if k == 'ref-container']
    zx0 = world_bbox(small[0])[0].x - 8
    zx1 = world_bbox(small[-1])[1].x + 4
    mppz, zlabels = broadside(small, zx0, zx1, gy)
    zlabels.append(dict(lines=['1.8 m figure'], estimate=False,
                        anchor=px(figs[ships.index(small[0])].location + Vector((0, 0, FIGURE_M))), place='above'))
    shoot(1, '01b-broadside-small-end.png')
    frames.append(dict(
        n=1, file='01-broadside.png', title='Fleet scale lineup: broadside',
        subtitle=f'Orthographic from starboard, longest first{", then real-world references" if refs else ""}. '
                 f'Grid 10 m, majors 50 m. Orange figure 1.8 m beside each hull.',
        parts=[dict(file='01a-broadside-fleet.png', crop=[gy - 370, gy + 60], dest=90, labels=labels, rects=[],
                    caption=f'Whole fleet, 1 px = {mpp:.2f} m'),
               dict(file='01b-broadside-small-end.png', crop=[gy - 400, gy + 60], dest=574, labels=zlabels,
                    rects=[], caption=f'{hulls[small[0]]["cfg"]["name"]} and smaller, magnified '
                                      f'{mpp/mppz:.1f}x: 1 px = {mppz:.2f} m')]))

    # 02 top-down, orthographic (the height wall would only cast a band of shadow here)
    wall.hide_render = True
    aim_sun((.3, .45, -.84))
    ortho = span*1.08
    look((span/2, 0, 5000), (span/2, 0, 0), ortho=ortho)
    cam.rotation_euler = (0, 0, 0)
    labels = []
    for k in lineup:
        lo, hi = world_bbox(k)
        lines, est = tag(k)
        labels.append(dict(lines=lines, estimate=est, anchor=px(((lo.x + hi.x)/2, lo.y, hi.z)), place='below'))
    render(2, 'top-down', 'Fleet scale lineup: top-down',
           f'Orthographic from above, bow to the right, 1 px = {ortho/W:.2f} m. Grid 10 m, majors 50 m.', labels)

    lineup_visible(False)

    # ---- the Legion frames: Legion at its own scale at the origin, in space
    if 'legion' not in hulls or 'hound' not in hulls:
        json.dump(dict(frames=frames, hulls=cfg['hulls'], basis_label=cfg.get('basis_label'),
                       references=[hulls[k]['cfg'] for k in refs]), open(out/'layout.json', 'w'), indent=1)
        return
    L = hulls['legion']
    L['root'].location = (0, 0, 0)
    basis_suffix = f" — {cfg['basis_label']}" if cfg.get('basis_label') else ''
    lg = json.load(open(PROLOGUE/'legion-geometry/geometry.json'))
    sL = L['cfg']['mpu']
    deck = next(d for d in lg['flight_decks'] if d['slot'] == 'WS 010')
    mouth = Vector(deck['mouth']['centre'])*sL
    hx = [v*sL for v in deck['hangar']['x']]
    hy = [v*sL for v in deck['hangar']['y']]
    floor = deck['deck']['floor_z']*sL
    lane = deck['hangar']['lane_clearance']
    lane_w, lane_h = lane['clear_width_full_height']*sL, lane['clear_height']*sL
    mouth_w, mouth_h = deck['mouth']['clear_width']*sL, deck['mouth']['clear_height']*sL
    Hd = hulls['hound']
    hs = Hd['mx'] - Hd['mn']
    hound_name = Hd['cfg']['name']
    lane_y = (hy[0] + hy[1])/2

    def name_px(k, p, extra=()):
        lines, est = tag(k)
        return dict(lines=lines + list(extra), estimate=est, anchor=px(p), place='callout')

    # 03 Hound holding just outside the aft port hangar mouth, seen from far off on a long lens
    # so perspective does not inflate the nearer hull
    show({'legion', 'hound'})
    place('hound', (mouth.x - 30 - hs.x/2, mouth.y + 4, mouth.z + 2), ref='centre')
    hlo, hhi = world_bbox('hound')
    hc = (hlo + hhi)/2
    f3 = figure('Figure frame 3', (hhi.x - 8, hc.y, hhi.z))
    aim_sun((.45, -.55, -.7))
    tgt = Vector((mouth.x + 10, mouth.y - 45, mouth.z))
    reach = (L['mx'].x - L['mn'].x)/180.0      # frame the Legion whatever its length
    look(tgt + Vector((-330, 260, 150))*reach, tgt, lens=50)
    fits = 'fits' if hs.y + 3 <= mouth_w and hs.z + 2 <= mouth_h else 'does not fit'
    render(3, 'hound-at-hangar-mouth', f'{hound_name} holding off a Legion hangar mouth' + basis_suffix,
           f'Aft port mouth (WS 010) clear {mouth_w:.1f} x {mouth_h:.1f} m; the Hound is '
           f'{hs.x:.1f} x {hs.y:.1f} x {hs.z:.1f} m, so it {fits} through it. 50 mm lens from about {450*reach:.0f} m.',
           [name_px('legion', ((hx[0] + hx[1])/2, hy[1], floor + lane_h + 10)),
            name_px('hound', (hc.x, hc.y, hhi.z)),
            dict(lines=['hangar mouth'], estimate=False, anchor=px(mouth), place='callout')])
    f3.hide_render = True

    # 04 Hound inside the lane: an orthographic section from astern, cut just inside the aft mouth,
    # with the lane's clear section outlined. Port (outboard) is on the left.
    cut = hx[0] + 2
    place('hound', (cut + .5, lane_y - hs.y/2, floor))
    hlo, hhi = world_bbox('hound')
    f4 = figure('Figure frame 4', (cut + .5, lane_y + lane_w/2 - 2, floor))
    aim_sun((.85, -.3, -.45))                   # low, from astern: into the cut
    sec_w = 150.0*max(1.0, (L['mx'].x - L['mn'].x)/180.0)
    cz = floor + sec_w*.15
    look((cut - 500, lane_y - 30, cz), (cut, lane_y - 30, cz), ortho=sec_w)
    cam_data.clip_start = 500
    cutaway('legion', True)
    fit = 'it fits' if hs.y + 3 <= lane_w and hs.z <= lane_h else 'it does not fit'
    corners = [px((cut, lane_y + sy*lane_w/2, floor + sz*lane_h)) for sy, sz in ((1, 0), (-1, 0), (-1, 1), (1, 1))]
    render(4, 'hound-in-hangar-lane', f'{hound_name} in a Legion hangar lane' + basis_suffix,
           f'Section from astern, cut just inside the aft port mouth; port to the left. Lane clear {lane_w:.1f} m '
           f'wide x {lane_h:.1f} m high at {sL:.2f} m/u; the Hound is {hs.y:.1f} m wide x {hs.z:.1f} m high: {fit}.',
           [name_px('hound', ((hlo.x + hhi.x)/2, (hlo.y + hhi.y)/2, hhi.z)),
            name_px('legion', (cut, lane_y - 60, floor + 30)),
            dict(lines=['1.8 m figure'], estimate=False, anchor=px(f4.location + Vector((0, 0, FIGURE_M))),
                 place='callout')],
           rects=[dict(pts=corners, text=f'lane clear {lane_w:.1f} x {lane_h:.1f} m')])
    cam_data.clip_start = .05
    cutaway('legion', False)
    f4.hide_render = True

    # 05 eye level on the Hound's walkway, the Legion alongside to port
    walk = [o for o in Hd['objs'] if o.name.startswith('Wheelhouse walkway port') and 'tread' not in o.name]
    Lmn, Lmx = world_bbox('legion')
    place('hound', (0, 0, 0))
    bpy.context.view_layer.update()
    def walkway_box():
        if not walk:                           # no walkway in this model: the top of the port side
            p = Hd['mn'] + Hd['root'].location + Vector((hs.x*.4, hs.y - 1, hs.z))
            return p, p
        pts = [walk[0].matrix_world @ Vector(c) for c in walk[0].bound_box]
        return (Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts))),
                Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts))))

    w_hi = walkway_box()[1] - Hd['root'].location
    # Hound to the Legion's starboard, walkway (port side) facing it, deck just above the Legion's keel
    Hd['root'].location = Vector((-hs.x*.3, Lmn.y - ALONGSIDE_M - Hd['mx'].y, Lmn.z + 6 - w_hi.z))
    bpy.context.view_layer.update()
    wlo, whi = walkway_box()
    wy = (wlo.y + whi.y)/2
    eye = Vector((wlo.x + .4, wy - .1, whi.z + EYE_M))
    aim = eye + Vector((math.sin(math.radians(40)), math.cos(math.radians(40)), math.tan(math.radians(27))))
    aim_sun((.25, .6, -.75))
    look(eye, aim, lens=14)
    gap = Lmn.y - world_bbox('hound')[1].y
    render(5, 'walkway-eye-level', f'Eye level on the {hound_name} walkway, the Legion alongside' + basis_suffix,
           f'Camera at {EYE_M} m above the walkway deck, 14 mm lens. The Legion is {gap:.0f} m off the '
           f"Hound's port side; the camera is a 1.8 m person's eye.",
           [name_px('legion', ((Lmn.x + Lmx.x)/2, Lmn.y, (Lmn.z + Lmx.z)/2))])

    # 06 the fighters in the same lane, two abreast, taxied in from the aft mouth
    fighters = [k for k in ('broadsword', 'thunder', 'dagger', 'longbow', 'talon', 'wasp') if k in hulls]
    if fighters:
        show({'legion'} | set(fighters))
        x0 = hx[0] + 12
        row_len = 0.0
        for i, k in enumerate(fighters):
            r = hulls[k]
            fsz = r['mx'] - r['mn']
            side = 1 if i % 2 == 0 else -1
            place(k, (x0, lane_y + side*(lane_w/4 + .5) - fsz.y/2, floor))
            row_len = max(row_len, fsz.x)
            if side < 0:
                x0 += row_len + 7
                row_len = 0.0
        x_end = x0 + row_len
        f6 = figure('Figure frame 6', (hx[0] + 5, lane_y, floor))
        # Orthographic section from outboard, cut just inside the lane's outboard wall
        aim_sun((-.25, -.9, -.35))              # low, from outboard: into the cut
        sec_w = (hx[1] - hx[0])*1.08
        cxs, czs = (hx[0] + hx[1])/2, floor + sec_w*H/W*.32
        cut_y = lane_y + lane_w/2 - .5
        look((cxs, cut_y + 500, czs), (cxs, cut_y, czs), ortho=sec_w)
        cam_data.clip_start = 500
        cutaway('legion', True)
        sun_data.use_shadow = False             # the clipped outboard wall would still shade the lane
        labels = []
        for k in fighters:
            lo, hi = world_bbox(k)
            lines, est = tag(k)
            labels.append(dict(lines=lines, estimate=est, anchor=px(((lo.x + hi.x)/2, hi.y, hi.z)),
                               place='above'))
        labels.append(dict(lines=['1.8 m figure'], estimate=False,
                           anchor=px(f6.location + Vector((0, 0, FIGURE_M))), place='above'))
        lane_rect = [px((x, cut_y, floor + z*lane_h)) for x, z in ((hx[0], 0), (hx[1], 0), (hx[1], 1), (hx[0], 1))]
        render(6, 'fighters-in-hangar-lane', 'Legion fighters in the same hangar lane' + basis_suffix,
               f'Section from outboard, cut just inside the port lane wall (no shadows); bow left, aft mouth right. '
               f'Two abreast; lane clear {lane_w:.1f} x {lane_h:.1f} m at {sL:.2f} m/u.', labels,
               rects=[dict(pts=lane_rect, text=f'lane clear height {lane_h:.1f} m')])
        cam_data.clip_start = .05
        sun_data.use_shadow = True
        cutaway('legion', False)

    # 07 the capitals side by side, broadside, with the Hound and a 747 in front for scale
    caps = [k for k in ('legion', 'onslaught', 'paragon') if k in hulls]
    front = [k for k in ('hound', 'ref-747') if k in hulls]
    if len(caps) >= 2:
        show(set(caps) | set(front))
        lineup_visible(True)
        for f in figs:
            f.hide_render = True
        wall.hide_render = False
        x = 0.0
        for k in caps:
            place(k, (x, -(hulls[k]['mx'].y - hulls[k]['mn'].y)/2, 0))
            x += hulls[k]['mx'].x - hulls[k]['mn'].x + 40
        x_end = x - 40
        y_front = min(world_bbox(k)[0].y for k in caps) - 200   # a front row, well clear of the capitals
        for k, c in zip(front, caps):
            lo, hi = world_bbox(c)
            r = hulls[k]
            place(k, ((lo.x + hi.x)/2 - (r['mx'].x - r['mn'].x)/2, y_front - (r['mx'].y - r['mn'].y), 0))
        f7 = figure('Figure frame 7', (world_bbox(front[0])[0].x - 6, y_front - 10, 0)) if front else None
        aim_sun((.35, .75, -.55))
        # orthographic (no perspective size cue) but looking 18 degrees down, so the front row
        # sits below the capitals in the frame instead of across them
        ortho = x_end*1.12
        m = ortho/W
        pitch = math.radians(18)
        tgt = Vector((x_end/2, y_front/2, 30))
        look(tgt + Vector((0, -math.cos(pitch), math.sin(pitch)))*5000, tgt, ortho=ortho)
        labels = []
        for k in caps + front:
            lo, hi = world_bbox(k)
            lines, est = tag(k)
            labels.append(dict(lines=lines, estimate=est, anchor=px(((lo.x + hi.x)/2, lo.y, hi.z)), place='above'))
        if f7:
            labels.append(dict(lines=['1.8 m figure'], estimate=False,
                               anchor=px(f7.location + Vector((0, 0, FIGURE_M))), place='above'))
        names = ' and '.join(hulls[k]['cfg']['name'] for k in caps)
        render(7, 'capitals-broadside', f'{names}, broadside' + basis_suffix,
               f'Orthographic from starboard, 18 degrees above the beam, 1 px = {m:.2f} m across; '
               + ' and '.join(hulls[k]['cfg']['name'] for k in front)
               + ' in front for scale. Grid 10 m, majors 50 m.', labels)
        lineup_visible(False)

    hound_lane = dict(lane_w=lane_w, lane_h=lane_h, mouth_w=mouth_w, mouth_h=mouth_h)
    json.dump(dict(frames=frames, hulls=cfg['hulls'], legion_lane=hound_lane, basis_label=cfg.get('basis_label'),
                   references=[hulls[k]['cfg'] for k in refs]),
              open(out/'layout.json', 'w'), indent=1)


# ----------------------------------------------------------------------------- class lineup

# Pixel layout of a class-lineup frame: a class-name column on the left, one row per class, a label
# band under each row, a scale bar and footer at the bottom.
CL_X0, CL_X1, CL_Y0, CL_Y1 = 250, W - 30, 100, H - 74
CL_LABEL, CL_ROW_GAP, CL_MIN_SLOT, CL_GAP = 62, 8, 150, 14
CL_SETS = [('all', 'every class', [c[0] for c in CLASSES]),
           ('mid', 'frigates, destroyers, freighters and tankers', ['frigates', 'destroyers', 'freighters']),
           ('close', 'fighters, frigates and destroyers, magnified', ['fighters', 'frigates', 'destroyers'])]


def class_layout(dims, sets):
    """For each frame set: one metres-per-pixel shared by its model and sprite frames, the x slot of every
    hull (shared by every view, so a hull sits at the same place in each), and the rows of each view.
    dims[key] = dict(L=, B=, Z=) model extents in metres, each the larger of the hull and its ghost, plus
    SL=, SB= for its sprite at the sprite lineup's scale."""
    out = {}
    for set_id, _, class_ids in sets:
        rows = [(cid, title, [k for k in keys if k in dims]) for cid, title, keys in CLASSES if cid in class_ids]
        rows = [r for r in rows if r[2]]

        def longest(k):
            return max(dims[k]['L'], dims[k].get('SL', 0))

        def broadest(k):
            return max(dims[k]['B'], dims[k].get('SB', 0))

        def fits(m):
            for _, _, keys in rows:
                if sum(max(longest(k)/m, CL_MIN_SLOT) for k in keys) + CL_GAP*(len(keys) - 1) > CL_X1 - CL_X0:
                    return False
            used = sum(max(broadest(k) for k in keys)/m + CL_LABEL + CL_ROW_GAP for _, _, keys in rows)
            return used <= CL_Y1 - CL_Y0

        mpp = .01
        while not fits(mpp):
            mpp *= 1.01
        slots = {}
        for _, _, keys in rows:
            keys.sort(key=lambda k: dims[k]['L'])       # by the model's length
            x = CL_X0
            for k in keys:
                w = max(longest(k)/mpp, CL_MIN_SLOT)
                slots[k] = x + w/2
                x += w + CL_GAP
        views = {}
        for view, ext in (('top', broadest), ('side', lambda k: dims[k]['Z'])):
            y = CL_Y0
            vr = []
            for cid, title, keys in rows:
                h = max(ext(k) for k in keys)/mpp
                vr.append(dict(id=cid, title=title, keys=keys, y0=round(y, 1), y1=round(y + h, 1),
                               centre=round(y + h/2, 1)))
                y += h + CL_LABEL + CL_ROW_GAP
            views[view] = vr
        out[set_id] = dict(mpp=mpp, slots=slots, views=views)
    return out


def class_lineup(cfg, hulls, scene, cam, look, show, out):
    """--class-lineup: every hull in role-class rows (CLASSES), top-down and broadside, one scale per frame
    set, under flat Workbench shading: size is the only question, so a few seconds a frame. Writes raw
    renders with alpha, a ghost-only render for hulls previewed by --size (their current size), and the
    pixel layout that the driver labels and lays the game sprites over."""
    from mathutils import Vector
    scene.render.engine = 'BLENDER_WORKBENCH'
    sh = scene.display.shading
    sh.light, sh.color_type, sh.single_color = 'STUDIO', 'SINGLE', (.62, .63, .65)
    scene.display.render_aa = '8'
    scene.render.film_transparent = True
    scene.view_settings.view_transform = 'Standard'
    scene.render.image_settings.color_mode = 'RGBA'

    def ext(mn, mx):
        s = Vector(mx) - Vector(mn)
        return s.x, s.y, s.z

    sprites = cfg.get('sprites', {})
    ref = sprites.get(SPRITE_REFERENCE)
    k_sprite = (hulls[SPRITE_REFERENCE]['mx'].x - hulls[SPRITE_REFERENCE]['mn'].x)/ref['length_px'] \
        if ref and SPRITE_REFERENCE in hulls else None
    dims = {}
    for k, r in hulls.items():
        L, B, Z = ext(r['mn'], r['mx'])
        g = r['cfg'].get('ghost_box')
        if g:
            gL, gB, gZ = ext(*g)
            L, B, Z = max(L, gL), max(B, gB), max(Z, gZ)
        dims[k] = dict(L=L, B=B, Z=Z)
        s = sprites.get(k)
        if s and k_sprite:
            dims[k].update(SL=s['length_px']*k_sprite, SB=s['width_px']*k_sprite)
    layout = class_layout(dims, CL_SETS)
    every = set(hulls)
    ghosts = [k for k in hulls if hulls[k]['cfg'].get('ghost_box')]
    frames = []

    def put(k, view, cx, row, box):
        """Place a hull (its bbox `box` under a root at the origin) at slot cx in `row`: centred on the
        row's centre line from above, on its keel line from the side."""
        r = hulls[k]
        mn, mx = Vector(box[0]), Vector(box[1])
        m = lay['mpp']
        x = (cx - W/2)*m - (mn.x + mx.x)/2
        if view == 'top':
            r['root'].location = Vector((x, (H/2 - row['centre'])*m - (mn.y + mx.y)/2, -mx.z))
        else:
            r['root'].location = Vector((x, -(mn.y + mx.y)/2, (H/2 - row['y1'])*m - mn.z))

    n = 0
    for set_id, set_title, _ in CL_SETS:
        lay = layout[set_id]
        m = lay['mpp']
        for view in ('top', 'side'):
            n += 1
            rows = lay['views'][view]
            keys = [k for row in rows for k in row['keys']]
            for row in rows:
                for k in row['keys']:
                    hulls[k]['root'].scale = (hulls[k]['cfg']['mpu'],)*3
                    put(k, view, lay['slots'][k], row, (hulls[k]['mn'], hulls[k]['mx']))
            if view == 'top':
                look((0, 0, 5000), (0, 0, 0), ortho=W*m)
                cam.rotation_euler = (0, 0, 0)
            else:
                look((0, -5000, 0), (0, 0, 0), ortho=W*m)
            file = f'{n:02d}-models-{"top-down" if view == "top" else "broadside"}-{set_id}.png'
            show(set(keys))
            scene.render.filepath = str(out/file)
            if not cfg.get('frames') or n in cfg['frames']:
                print(f'[fleet] rendering {file}  {m:.3f} m/px', flush=True)
                bpy_render()
            ghost_file = None
            gk = [k for k in ghosts if k in keys]
            if gk:
                ghost_file = file.replace('.png', '-ghost.png')
                for k in gk:
                    row = next(r_ for r_ in rows if k in r_['keys'])
                    hulls[k]['root'].scale = (hulls[k]['cfg']['ghost_mpu'],)*3
                    put(k, view, lay['slots'][k], row, hulls[k]['cfg']['ghost_box'])
                show(set(gk))
                scene.render.filepath = str(out/ghost_file)
                if not cfg.get('frames') or n in cfg['frames']:
                    bpy_render()
                for k in gk:
                    hulls[k]['root'].scale = (hulls[k]['cfg']['mpu'],)*3
            frames.append(dict(n=n, kind='model', view=view, set=set_id, set_title=set_title, file=file,
                               raw=file, ghost_raw=ghost_file, mpp=m, rows=rows))
    for set_id, set_title, _ in CL_SETS:
        n += 1
        frames.append(dict(n=n, kind='sprite', view='top', set=set_id, set_title=set_title,
                           file=f'{n:02d}-sprites-top-down-{set_id}.png', mpp=layout[set_id]['mpp'],
                           rows=layout[set_id]['views']['top'],
                           model_raw=next(f['raw'] for f in frames if f['set'] == set_id and f['view'] == 'top'),
                           model_n=next(f['n'] for f in frames if f['set'] == set_id and f['view'] == 'top')))
    show(every)
    for h in cfg['hulls']:
        if h['key'] in hulls:
            r = hulls[h['key']]
            h['size_m'] = [round(v, 2) for v in ext(r['mn'], r['mx'])]
    json.dump(dict(class_frames=frames, frames=[], slots={s: layout[s]['slots'] for s in layout},
                   hulls=cfg['hulls'], sprite_m_per_px=k_sprite, sprite_reference=SPRITE_REFERENCE,
                   basis_label=cfg.get('basis_label')), open(out/'layout.json', 'w'), indent=1)


def bpy_render():
    import bpy
    bpy.ops.render.render(write_still=True)


def outline(alpha, width):
    """The edge band of a silhouette mask: a `width`-px ring just outside it."""
    from PIL import ImageChops, ImageFilter
    a = alpha.point(lambda v: 255 if v > 96 else 0)
    return ImageChops.subtract(a.filter(ImageFilter.MaxFilter(2*width + 1)), a)


def compose_class(run, layout, footer):
    """Label the class-lineup renders and build the sprite frames at the same pixel layout."""
    from PIL import Image, ImageDraw
    from review_font import review_font
    f_title, f_sub = review_font(30, True), review_font(18)
    f_row, f_lab_b, f_lab = review_font(20, True), review_font(15, True), review_font(14)
    by_key = {h['key']: h for h in layout['hulls']}
    k_sprite = layout.get('sprite_m_per_px')
    raw = run/'raw'
    for fr in layout['class_frames']:
        m = fr['mpp']
        slots = layout['slots'][fr['set']]
        img = Image.new('RGBA', (W, H), (17, 19, 23, 255))
        d = ImageDraw.Draw(img)
        for i, row in enumerate(fr['rows']):
            band = (CL_X0 - 16, row['y0'] - 4, W - 8, row['y1'] + CL_LABEL - 4)
            d.rectangle(band, fill=(24, 27, 32, 255) if i % 2 == 0 else (20, 22, 27, 255))
            d.text((24, (row['y0'] + row['y1'] + CL_LABEL)/2 - 22), row['title'], font=f_row, fill=(240, 240, 240))
            d.text((24, (row['y0'] + row['y1'] + CL_LABEL)/2 + 4), f"{len(row['keys'])} hull{'s' if len(row['keys']) > 1 else ''}",
                   font=f_lab, fill=(150, 150, 150))
        if fr['kind'] == 'model':
            if not (raw/fr['raw']).exists():
                print(f"[fleet] {fr['file']}: no raw render yet, not labelled")
                continue
            if fr.get('ghost_raw') and (raw/fr['ghost_raw']).exists():
                ga = Image.open(raw/fr['ghost_raw']).getchannel('A')
                tint = Image.new('RGBA', (W, H), (255, 150, 60, 60))
                img.paste(tint, (0, 0), ga.point(lambda v: 255 if v > 96 else 0))
                ring = outline(ga, 2)
                img.paste(Image.new('RGBA', (W, H), (255, 160, 70, 255)), (0, 0), ring)
            model = Image.open(raw/fr['raw']).convert('RGBA')
            img.alpha_composite(model)
        else:
            if not k_sprite:
                print(f"[fleet] {fr['file']}: no sprites (no game install), skipped")
                continue
            for row in fr['rows']:
                for k in row['keys']:
                    s = by_key[k].get('sprite_info')
                    if not s or not s.get('path'):          # dims given, game not installed here
                        continue
                    sp = Image.open(s['path']).convert('RGBA')
                    sp = sp.crop(sp.getchannel('A').point(lambda a: 255 if a > 127 else 0).getbbox())
                    sp = sp.rotate(-90, expand=True)                 # bow up -> bow right, port up
                    f = k_sprite/m
                    sp = sp.resize((max(1, round(sp.width*f)), max(1, round(sp.height*f))), Image.LANCZOS)
                    img.alpha_composite(sp, (round(slots[k] - sp.width/2), round(row['centre'] - sp.height/2)))
            if (raw/fr['model_raw']).exists():
                ring = outline(Image.open(raw/fr['model_raw']).getchannel('A'), 1)
                img.paste(Image.new('RGBA', (W, H), (90, 220, 255, 255)), (0, 0), ring)
        d = ImageDraw.Draw(img)
        for row in fr['rows']:
            boxes = []
            for k in row['keys']:
                h = by_key[k]
                L = h['size_m'][0]
                if fr['kind'] == 'model':
                    lines = [h['name'], f'{L:.0f} m' + (' proposed' if h.get('pending') else '')]
                    if h.get('pending'):
                        lines.append(f"ghost: {h['published_length_m']:.0f} m now")
                    cols = [(240, 240, 240), (255, 190, 80) if h.get('pending') else (200, 200, 200), (255, 160, 70)]
                else:
                    s = h.get('sprite_info')
                    if not s:
                        continue
                    sl = s['length_px']*k_sprite
                    lines = [h['name'], f"sprite {sl:.0f} m ({s['length_px']} px)", f'model {L:.0f} m']
                    off = sl/L - 1
                    cols = [(240, 240, 240), (200, 200, 200),
                            (255, 130, 100) if abs(off) > .2 else (255, 210, 120) if abs(off) > .1 else (150, 220, 160)]
                tw = max(d.textlength(t, font=f_lab_b if i == 0 else f_lab) for i, t in enumerate(lines)) + 10
                x0 = min(max(slots[k] - tw/2, CL_X0 - 12), W - tw - 10)
                y0 = row['y1'] + 6
                th = 18*len(lines)
                while any(not (x0 + tw < b[0] or x0 > b[2] or y0 + th < b[1] or y0 > b[3]) for b in boxes):
                    y0 += 18                                         # a neighbour's label is in the way
                boxes.append((x0, y0, x0 + tw, y0 + th))
                for i, t in enumerate(lines):
                    d.text((x0 + 5, y0 + 18*i), t, font=f_lab_b if i == 0 else f_lab, fill=cols[i])
        # scale bar: the largest round length under 300 px
        bar = max(v for v in (1, 2, 5, 10, 20, 25, 50, 100, 200) if v/m <= 300)
        by = H - 58
        d.rectangle([CL_X0, by, CL_X0 + bar/m, by + 8], fill=(235, 235, 235))
        for i in range(0, 6):
            if bar*i/5/m <= bar/m:
                d.line([(CL_X0 + bar*i/5/m, by - 4), (CL_X0 + bar*i/5/m, by + 12)], fill=(235, 235, 235))
        d.text((CL_X0 + bar/m + 12, by - 4), f'{bar} m   ·   1 px = {m:.2f} m', font=f_lab_b, fill=(235, 235, 235))
        if fr['kind'] == 'model':
            what = 'Models' + (', top-down' if fr['view'] == 'top' else ', broadside')
            sub = (('Orthographic from above, bow to the right.' if fr['view'] == 'top' else
                    'Orthographic from starboard, bow to the right, keels on each row\'s line.')
                   + ' Sizes as published' + (', except where marked proposed (--size): the orange ghost is the '
                                              'size published today.' if any(h.get('pending') for h in layout['hulls'])
                                              else '.'))
        else:
            ref = by_key.get(layout['sprite_reference'], {})
            what = 'Game sprites at their relative pixel sizes'
            sub = (f"Every sprite at one {k_sprite:.3f} m per sprite pixel, so the {ref.get('name')} sprite matches its "
                   f"{ref.get('size_m', [0])[0]:.0f} m model. Cyan: each model's outline, at the size in frame "
                   f"{fr['model_n']:02d}. The third line is red past 20% off the model, amber past 10%.")
        d.rectangle([0, 0, W, 86], fill=(17, 19, 23, 255))
        d.text((24, 12), f"{fr['n']:02d}  {what}: {fr['set_title']}", font=f_title, fill=(240, 240, 240))
        d.text((24, 54), sub, font=f_sub, fill=(190, 190, 190))
        d.rectangle([0, H - 30, W, H], fill=(17, 19, 23, 255))
        d.text((24, H - 25), footer, font=review_font(15), fill=(170, 170, 170))
        img.convert('RGB').save(run/fr['file'])


def class_table(run, layout, sizes):
    """hull, class, current, proposed, sprite-implied length; written as class-sizes.md / .csv."""
    k_sprite = layout.get('sprite_m_per_px')
    by_key = {h['key']: h for h in layout['hulls']}
    rows = []
    for cid, title, keys in CLASSES:
        for k in keys:
            h = by_key.get(k)
            if not h:
                continue
            cur = h.get('published_length_m') or h['size_m'][0]
            prop = h['size_m'][0]
            s = h.get('sprite_info')
            sl = s['length_px']*k_sprite if s and k_sprite else None
            note = []
            if h.get('pending'):
                note.append(f"previewed at {prop:.0f} m (--size), published {cur:.0f} m")
            if h.get('estimate'):
                note.append('ESTIMATE (no model)')
            if sl:
                off = prop/sl - 1
                if abs(off) > .2:
                    note.append(f"model {abs(off)*100:.0f}% {'over' if off > 0 else 'under'} the sprite")
            rows.append(dict(hull=h['name'], cls=title, current_m=round(cur, 1), proposed_m=round(prop, 1),
                             sprite_px=s['length_px'] if s else '', sprite_implied_m=round(sl, 1) if sl else '',
                             proposed_over_sprite=round(prop/sl, 2) if sl else '', notes='; '.join(note)))
    import csv
    with open(run/'class-sizes.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    cols = list(rows[0])
    md = ['| ' + ' | '.join(cols) + ' |', '|' + '---|'*len(cols)]
    md += ['| ' + ' | '.join(str(r[c]) for c in cols) + ' |' for r in rows]
    md += ['', f"sprite_implied_m: the sprite's opaque length in pixels x {k_sprite or 0:.3f} m/px, the factor "
           f"that makes the {SPRITE_REFERENCE} sprite match its model. Lengths are the render-visible model's "
           'bounding box.']
    (run/'class-sizes.md').write_text('\n'.join(md) + '\n')
    return rows


# ----------------------------------------------------------------------------- driver

def label_frames(run, layout):
    from PIL import Image, ImageDraw
    from review_font import review_font
    f_title, f_sub, f_lab = review_font(30, True), review_font(18), review_font(16)
    f_lab_b = review_font(16, True)
    for fr in layout['frames']:
        if not all((run/'raw'/part['file']).exists() for part in fr['parts']):
            print(f"[fleet] {fr['file']}: no raw render yet, not labelled")
            continue
        img = Image.new('RGB', (W, H), (14, 16, 19))
        d = ImageDraw.Draw(img)
        boxes = []
        for part in fr['parts']:
            c0, c1 = part['crop']
            dest = part['dest']
            src = Image.open(run/'raw'/part['file']).convert('RGB').crop((0, c0, W, c1))
            img.paste(src, (0, dest))
            band = (max(dest, 88), min(dest + c1 - c0, H - 32))
            dy = dest - c0
            if part.get('caption'):
                d.rectangle([16, band[0] + 8, 24 + d.textlength(part['caption'], font=f_lab_b) + 8, band[0] + 34],
                            fill=(30, 34, 40))
                d.text((24, band[0] + 12), part['caption'], font=f_lab_b, fill=(255, 210, 120))
                boxes.append((16, band[0] + 8, 32 + d.textlength(part['caption'], font=f_lab_b), band[0] + 34))
            for rect in part.get('rects', []):
                pts = [(x, y + dy) for x, y, _ in rect['pts']]
                for a, b in zip(pts, pts[1:] + pts[:1]):
                    d.line([a, b], fill=(80, 220, 255), width=3)
                tx = min(p[0] for p in pts)
                ty = max(p[1] for p in pts) + 6
                d.text((tx, ty), rect['text'], font=f_lab_b, fill=(80, 220, 255))
            for lab in sorted(part['labels'], key=lambda lab: lab['anchor'][0]):
                ax, ay = lab['anchor'][0], lab['anchor'][1] + dy
                lines = lab['lines']
                tw = max(d.textlength(t, font=f_lab_b if i == 0 else f_lab) for i, t in enumerate(lines)) + 12
                th = 20*len(lines) + 8
                for tier in range(40):
                    if lab['place'] == 'below':
                        y0 = ay + 24 + tier*(th + 6)
                    elif lab['place'] == 'above':
                        y0 = ay - 24 - th - tier*(th + 6)
                    else:
                        y0 = ay - 60 - th - tier*(th + 6)
                    x0 = min(max(ax - tw/2, 8), W - tw - 8)
                    y0 = min(max(y0, band[0] + 4), band[1] - th - 4)
                    b = (x0 - 3, y0 - 3, x0 + tw + 3, y0 + th + 3)
                    if all(b[2] < o[0] or b[0] > o[2] or b[3] < o[1] or b[1] > o[3] for o in boxes):
                        break
                boxes.append(b)
                col = (255, 120, 90) if lab['estimate'] else (235, 235, 235)
                ly = y0 + th if y0 < ay else y0
                d.line([(ax, ay), (min(max(ax, x0 + 4), x0 + tw - 4), ly)], fill=(200, 200, 200), width=1)
                d.ellipse([ax - 2, ay - 2, ax + 2, ay + 2], fill=(255, 200, 60))
                d.rectangle([x0, y0, x0 + tw, y0 + th], fill=(18, 20, 24))
                for i, t in enumerate(lines):
                    d.text((x0 + 6, y0 + 4 + 20*i), t, font=f_lab_b if i == 0 else f_lab,
                           fill=(255, 120, 90) if t == 'ESTIMATE' else
                           (255, 190, 80) if 'pending' in t else col)
        d.rectangle([0, 0, W, 84], fill=(14, 16, 19))
        d.text((24, 12), f"{fr['n']:02d}  {fr['title']}", font=f_title, fill=(240, 240, 240))
        d.text((24, 52), fr['subtitle'], font=f_sub, fill=(190, 190, 190))
        d.rectangle([0, H - 30, W, H], fill=(14, 16, 19))
        d.text((24, H - 25), layout['footer'], font=review_font(15), fill=(170, 170, 170))
        img.save(run/fr['file'])


def sizes_table(run, hulls):
    leg = next((h for h in hulls if h['key'] == 'legion'), None)
    hnd = next((h for h in hulls if h['key'] == 'hound'), None)

    def box(h):
        a = h['size_m']
        return a[0]*a[1]*a[2]

    def enclosed(h):
        reg = ship_scale.HULLS.get(h.get('scale_key') or '')
        return round(reg['units3']*h['mpu']**3) if reg else None

    rows = []
    for h in sorted(hulls, key=lambda h: -h['size_m'][0]):
        l, b, t = h['size_m']
        rows.append(dict(
            hull=h['name'], metres_per_unit=h['mpu'], length_m=l, beam_m=b, height_m=t,
            box_volume_m3=round(box(h)), enclosed_volume_m3=enclosed(h) or '',
            length_x_legion=round(l/leg['size_m'][0], 3) if leg else '',
            length_x_hound=round(l/hnd['size_m'][0], 3) if hnd else '',
            volume_x_legion=round(box(h)/box(leg), 4) if leg else '',
            volume_x_hound=round(box(h)/box(hnd), 3) if hnd else '',
            value='estimate' if h['estimate'] else
                  (h['pending_label'] if h.get('pending') else
                   ('published' if h['mpu'] == h['published_mpu'] else 'preview')
                   + (f" ({h['pending_note']})" if h.get('pending_note') else '')),
            published_metres_per_unit=h['published_mpu'] or '',
            ship_scale_solution=h['solved_mpu'] or '', scale_source=h['mpu_from'], model=h['source_label']))
    import csv
    with open(run/'sizes.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    cols = ['hull', 'metres_per_unit', 'length_m', 'beam_m', 'height_m', 'box_volume_m3', 'enclosed_volume_m3',
            'length_x_legion', 'length_x_hound', 'volume_x_legion', 'volume_x_hound', 'value',
            'published_metres_per_unit', 'ship_scale_solution']
    md = ['| ' + ' | '.join(cols) + ' |', '|' + '---|'*len(cols)]
    for r in rows:
        md.append('| ' + ' | '.join(f'{r[c]:,}' if isinstance(r[c], int) else str(r[c]) for c in cols) + ' |')
    md += ['', 'Length, beam and height are the measured bounding box of the render-visible model at its '
           'metres_per_unit. box_volume is their product, for comparison between every hull; '
           'enclosed_volume is ship_scale.py\'s crew-usable volume (its units3 x m/u^3) where the hull is '
           'registered there. Volume ratios use the box. `value`: published = geometry.json; estimate = '
           'no model yet, ship_scale.py solution; preview = --basis solved; pending publish = scaled by hand to the '
           'chosen length (--pending) until the hull publishes it.']
    (run/'sizes.md').write_text('\n'.join(md) + '\n')
    return rows


def git_commit():
    try:
        sha = subprocess.check_output(['git', '-C', str(REPO), 'rev-parse', '--short', 'HEAD'], text=True).strip()
        dirty = subprocess.call(['git', '-C', str(REPO), 'diff', '--quiet', 'HEAD', '--', str(HERE)]) != 0
        return sha + (' (+ uncommitted fleet-scale changes)' if dirty else '')
    except Exception:
        return 'unknown'


def drive_root():
    if sys.platform == 'darwin':
        cands = sorted(Path.home().glob('Library/CloudStorage/GoogleDrive-*/My Drive/starsector'))
        return cands[0] if cands else None
    p = Path(r'G:\My Drive\starsector')
    return p if p.exists() else None


def write_notes(run, rows, layout, basis, commit, what):
    pub = [r for r in rows if r['value'].startswith('published')]
    est = [r for r in rows if r['value'] == 'estimate']
    pre = [r for r in rows if r['value'] == 'preview']
    pen = [r for r in rows if r['value'].startswith('pending')]
    lane = layout.get('legion_lane', {})
    leg = next((r for r in rows if r['hull'].startswith('Legion')), None)
    hnd = next((r for r in rows if r['hull'].startswith('Hound')), None)
    n = [f'# Fleet scale lineup ({what})', '',
         f'Built from commit `{commit}` by `art-source/company-prologue/fleet-scale/fleet_lineup.py` '
         f'(basis: {basis}). Every hull at its own metres_per_unit, one grey material, one sun, '
         'a 1.8 m orange figure, 10 m grid with 50 m majors.', '',
         '## Which scale each hull used', '']
    for r in rows:
        n.append(f"- **{r['hull']}**: {r['metres_per_unit']} m/u, {r['length_m']} m long — {r['value']} "
                 f"({r['scale_source']}; model: {r['model']})"
                 + (f"; ship_scale.py solves {r['ship_scale_solution']}" if r['ship_scale_solution'] else ''))
    n += ['', f'Published: {len(pub)}. Estimates (neutral box, labelled ESTIMATE): '
          + (', '.join(r['hull'] for r in est) or 'none') + '.'
          + (f" Preview values: {', '.join(r['hull'] for r in pre)}." if pre else '')
          + (f" Scaled by hand to {layout.get('basis_label')} until published: "
             f"{', '.join(r['hull'] + ' (published ' + str(r['published_metres_per_unit']) + ' m/u)' for r in pen)}."
             if pen else ''), '']
    by = {r['hull'].split(' ')[0]: r for r in rows}
    refs = {r['name']: r for r in layout.get('references', [])}
    nim = next((r['size_m'][0] for r in layout.get('references', []) if r['name'].startswith('Nimitz')), None)
    if 'Hound' in by and 'Lasher' in by:
        n += ['## Ratios at this run\'s sizes', '',
              '| hull | length m | x Hound | x Lasher' + (' | x Nimitz |' if nim else ' |'),
              '|---|---|---|---' + ('|---|' if nim else '|')]
        for k in ('Paragon', 'Legion', 'Onslaught', 'Lasher', 'Hound'):
            if k in by:
                L_ = by[k]['length_m']
                n.append(f"| {by[k]['hull']} | {L_:.1f} | {L_/by['Hound']['length_m']:.2f} | "
                         f"{L_/by['Lasher']['length_m']:.2f}" + (f' | {L_/nim:.2f} |' if nim else ' |'))
        n.append('')
    if refs:
        n += ['References (solid grey, at the end of the rows): '
              + '; '.join(f"{k} {v['desc']}" for k, v in refs.items()) + '.', '']
    if leg and hnd:
        n += [f"Legion to Hound: {leg['length_m']/hnd['length_m']:.2f}x the length, "
              f"{leg['box_volume_m3']/hnd['box_volume_m3']:.0f}x the box volume.", '']
    if lane:
        n += [f"Legion port hangar lane at this scale: {lane['lane_w']:.1f} m wide x {lane['lane_h']:.1f} m high; "
              f"aft mouth {lane['mouth_w']:.1f} x {lane['mouth_h']:.1f} m.", '']
    n += ['## Images', '']
    for fr in layout['frames']:
        n.append(f"- `{fr['file']}` — {fr['title']}. {fr['subtitle']}")
    n += ['', '## What to judge', '',
          *(([f"- This run shows the {layout['basis_label']} capital sizes. Hulls labelled 'pending' "
              'are scaled by hand or still under audit; a rerun after their sessions publish uses the real values.']
             ) if layout.get('basis_label') else []),
          '- 01/02: does the size ladder read right — fighters, Hermes, frigates and freighters, capitals?',
          '- 03-05: does the Hound feel like a trivial insect beside the Legion? If not, which one is off: '
          'the Legion too small, or the Hound too big?',
          '- 04/06: should a Hound-class hull fit inside a Legion hangar lane, or only fighters?',
          '- The estimate boxes are placeholders at ship_scale.py\'s solution and switch to the real models '
          'on the next run after their packages land.', '',
          '`sizes.md` / `sizes.csv` in this folder hold the full table.', '']
    (run/'NOTES.md').write_text('\n'.join(n))


def pillow_python():
    """This interpreter if it has Pillow, else $PYTHON or another python3 on the machine that does
    (Homebrew's python3 on the Mac has none; the system one does)."""
    try:
        import PIL  # noqa: F401
        return None
    except ImportError:
        pass
    import shutil
    for cand in (os.environ.get('PYTHON'), '/usr/bin/python3', shutil.which('python3'), shutil.which('python')):
        if cand and Path(cand).exists() and Path(cand).resolve() != Path(sys.executable).resolve() and \
                subprocess.call([cand, '-c', 'import PIL'], stderr=subprocess.DEVNULL) == 0:
            return cand
    sys.exit('fleet_lineup.py needs Pillow for its labels; set $PYTHON to a python3 that has it')


def driver(argv):
    import argparse
    import shutil
    other = pillow_python()
    if other:
        os.execv(other, [other, str(Path(__file__).resolve())] + argv)
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--basis', choices=('published', 'solved'), default='published')
    ap.add_argument('--samples', type=int, default=96)
    ap.add_argument('--out', default=None, help='run folder (default build/fleet-scale/<date>[-solved])')
    ap.add_argument('--post', metavar='MACHINE', choices=('mac', 'windows'),
                    help='copy the run to review/fleet-scale/<date>_<what>_<machine>/ on the shared Drive')
    ap.add_argument('--what', default=None, help="the run folder's <what> (default lineup / lineup-solved)")
    ap.add_argument('--skip-render', action='store_true', help='relabel and retable an existing run')
    ap.add_argument('--frames', default='', help='render only these frame numbers (e.g. 3,5); others keep their raw')
    ap.add_argument('--pending', default=None, metavar='JSON',
                    help='a chosen basis not yet published (e.g. pending-c.json): scales those hulls by hand')
    ap.add_argument('--no-references', action='store_true', help='leave out the real-world references')
    ap.add_argument('--class-lineup', action='store_true',
                    help='only the class lineup: models in role-class rows (top-down, broadside) and the game '
                         'sprites at their relative pixel sizes, flat Workbench shading')
    ap.add_argument('--size', action='append', default=[], metavar='HULL=METRES',
                    help='preview a hull at this length (e.g. lasher=75) without publishing it; its published '
                         'size is drawn as a ghost in the class lineup. Repeatable')
    ap.add_argument('--sprite-dims', default=None, metavar='JSON',
                    help='sprite pixel sizes {"hull": [length_px, width_px], ...} (inline or a file) instead of '
                         'reading the game install, so a machine without the game lays out the same frames')
    ap.add_argument('--print-sprite-dims', action='store_true', help='print the --sprite-dims JSON and exit')
    ap.add_argument('--build-missing', action='store_true',
                    help='first run the package build of any hull whose model is a missing local build output')
    a = ap.parse_args(argv)
    what = a.what or ('class-lineup' if a.class_lineup else 'lineup' if a.basis == 'published' else 'lineup-solved')
    today = date.today().isoformat()
    run = Path(a.out) if a.out else REPO/'build'/'fleet-scale'/(
        today + ('-class' if a.class_lineup else '' if a.basis == 'published' else '-solved'))
    (run/'raw').mkdir(parents=True, exist_ok=True)
    if a.print_sprite_dims:
        print(json.dumps({h['key']: [s['length_px'], s['width_px']] for h in HULLS
                          for s in [sprite_dims(h['key'])] if s}, separators=(',', ':')))
        return
    given = None
    if a.sprite_dims:
        given = json.loads(Path(a.sprite_dims).read_text() if Path(a.sprite_dims).is_file() else a.sprite_dims)
    if a.build_missing and not a.skip_render:
        build_missing()
    hulls = [r for r in (resolve(h, a.basis) for h in HULLS) if r]
    cfg = dict(hulls=hulls, raw_dir=str(run/'raw'), samples=a.samples,
               frames=[int(f) for f in a.frames.split(',') if f], references=not a.no_references,
               class_lineup=a.class_lineup)
    if a.class_lineup:
        cfg['references'] = False
        cfg['sprites'] = {}
        for h in hulls:
            h['sprite_info'] = sprite_dims(h['key']) if given is None else (
                dict(path=None, length_px=given[h['key']][0], width_px=given[h['key']][1]) if h['key'] in given else None)
            if h['sprite_info']:
                cfg['sprites'][h['key']] = h['sprite_info']
    if a.pending:
        pend = json.load(open(Path(a.pending) if Path(a.pending).exists() else HERE/a.pending))
        cfg['basis_label'] = pend['basis_label']
        cfg['pending_tolerance'] = pend.get('tolerance', .05)
        for h in hulls:
            e = pend.get('hulls', {}).get(h['key'])
            if e:
                h['pending_length_m'] = e['length_m']
                h['pending_label'] = e.get('label', f"{pend['basis_label'].split(' ')[0]}, pending publish")
                h['pending_note'] = e.get('note')
    for spec in a.size:
        key, _, metres = spec.partition('=')
        h = next((h for h in hulls if h['key'] == key), None)
        if not h or not metres:
            sys.exit(f'--size {spec}: expected HULL=METRES with HULL one of ' + ', '.join(h['key'] for h in hulls))
        h['pending_length_m'] = float(metres)
        h['pending_tolerance'] = .001
        h['pending_label'] = f'proposed {float(metres):g} m'
        cfg.setdefault('basis_label', 'proposed (--size)')
    cfg_path = run/'config.json'
    cfg_path.write_text(json.dumps(cfg, indent=1))
    if not a.skip_render:
        from blender_path import BLENDER
        subprocess.check_call([BLENDER, '--background', '--factory-startup', '--python', str(Path(__file__)),
                               '--', '--blender', str(cfg_path)])
    layout = json.load(open(run/'raw'/'layout.json'))
    commit = git_commit()
    if a.class_lineup:
        if a.skip_render:                      # relabel: today's sprites over the rendered layout
            for h in layout['hulls']:
                h['sprite_info'] = sprite_dims(h['key'])
        footer = (f'fleet_lineup.py --class-lineup @ {commit}  ·  '
                  + ('  '.join(f'--size {s}' for s in a.size) + '  ·  ' if a.size else '')
                  + "sizes from each package's geometry.json; sprites from the game install, read-only")
        compose_class(run, layout, footer)
        rows = class_table(run, layout, None)
        sizes_table(run, layout['hulls'])
        write_class_notes(run, rows, layout, commit, a.size)
        print((run/'class-sizes.md').read_text())
        print(f'[fleet] run written to {run}')
        if a.post:
            post(run, today, what, a.post, [f['file'] for f in layout['class_frames']],
                 ('NOTES.md', 'class-sizes.md', 'class-sizes.csv', 'sizes.md', 'sizes.csv'))
        return
    layout['footer'] = (f'fleet_lineup.py @ {commit}  ·  basis: {a.basis}'
                        + (f" + {layout['basis_label']} pending" if layout.get('basis_label') else '') + '  ·  '
                        'sizes from each package\'s geometry.json; ESTIMATE = no model yet (ship_scale.py)')
    label_frames(run, layout)
    rows = sizes_table(run, layout['hulls'])
    write_notes(run, rows, layout, a.basis, commit, what)
    print((run/'sizes.md').read_text())
    print(f'[fleet] run written to {run}')
    if a.post:
        post(run, today, what, a.post, [fr['file'] for fr in layout['frames']], ('NOTES.md', 'sizes.md', 'sizes.csv'))


def post(run, today, what, machine, images, extras):
    import shutil
    root = drive_root()
    if not root:
        sys.exit('no shared Drive folder found')
    dest = root/'review'/'fleet-scale'/f'{today}_{what}_{machine}'
    if dest.exists():
        sys.exit(f'{dest} exists: one run, one writer; pick another --what')
    dest.mkdir(parents=True)
    for f in list(images) + list(extras):
        if (run/f).exists():
            shutil.copy2(run/f, dest/f)
    print(f'[fleet] posted to {dest}')


def write_class_notes(run, rows, layout, commit, sizes):
    n = ['# Fleet class lineup', '',
         f'Built from commit `{commit}` by `art-source/company-prologue/fleet-scale/fleet_lineup.py --class-lineup`'
         + (' ' + ' '.join(f'--size {s}' for s in sizes) if sizes else '') + '. Models under flat Workbench '
         'shading in role-class rows; the game sprites at their relative pixel sizes in the same layout and at '
         'the same metres per pixel as the matching model frame.', '',
         '## Images', '']
    for fr in layout['class_frames']:
        n.append(f"- `{fr['file']}`: {'models' if fr['kind'] == 'model' else 'game sprites'}, "
                 f"{'top-down' if fr['view'] == 'top' else 'broadside'}, {fr['set_title']}, 1 px = {fr['mpp']:.2f} m")
    n += ['', '## Sizes', '', (run/'class-sizes.md').read_text(), '',
          'Hull sizes follow role and the neighbouring hulls (owner, 2026-10-20); the sprites are not the source '
          'of truth either, they show what the game implies.', '']
    (run/'NOTES.md').write_text('\n'.join(n), encoding='utf-8')


if __name__ == '__main__':
    if '--blender' in sys.argv:
        blender_main(json.load(open(sys.argv[sys.argv.index('--blender') + 1])))
    else:
        driver(sys.argv[1:])
