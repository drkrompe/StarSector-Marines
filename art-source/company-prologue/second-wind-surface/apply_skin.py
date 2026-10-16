"""Surfacing phase: put the graded part skins on the CLEAN build.

  blender --background --factory-startup --python apply_skin.py
      [-- --blend IN.blend] [--out OUT.blend] [--fallback neutral|base]

Opens the clean geometry build (default build/geom/second-wind-geometry.blend,
made by importing ../second-wind-geometry/build_reference.py; the geometry
modules are never edited) and, per part in parts.PARTS:
- selects the part's faces on the object's BASE mesh by the same rule
  render_parts.py used (parts.all_selections), so the skin lands on exactly
  the faces its guide showed;
- adds a UV layer named `skin:<part>` holding each corner's ortho projection
  into the part's tile (for a skin_from part, into the source part's tile,
  through a camera fitted to its own face group); and
- gives those faces a material `Skin <part> | <base material>` whose base
  colour samples textures/<source part>.png through that UV layer, keeping the
  base material's metallic and roughness.
Modifiers stay live (bevels interpolate the UVs onto their strips), and object
names, other faces and other UV layers are untouched, so a later damage phase
(sw_damage.py's Booleans) cuts skinned faces like any other: surviving faces
keep their UVs and skin, new cut faces take the cutter's material.

Faces no part claims keep a fallback: `neutral` (default) swaps every base
base material (the four palette classes) on them for one flat dark grey,
darker still for recesses, so coverage reads honestly in review; lamps,
glazing and a damage state's scar and repair materials are left alone; `base` leaves the clean build's flat base colours.
Writes build/skinned.blend (packed images) and skin-manifest.json.
"""
from pathlib import Path
import json
import sys
import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import parts as P

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
def arg(name, default):
    return argv[argv.index(name) + 1] if name in argv else default
BLEND = Path(arg('--blend', HERE / 'build' / 'geom' / 'second-wind-geometry.blend'))
OUT = Path(arg('--out', HERE / 'build' / 'skinned.blend'))
FALLBACK = arg('--fallback', 'neutral')

bpy.ops.wm.open_mainfile(filepath=str(BLEND))
selection = P.all_selections()
part_list = json.loads((HERE / 'part-list.json').read_text())
images = {}


def image(src):
    if src not in images:
        im = bpy.data.images.load(str(HERE / 'textures' / f'{src}.png'))
        im.name = f'Skin {src}'
        im.pack()
        images[src] = im
    return images[src]


def skin_material(part, base):
    name = f'Skin {part} | {base.name}'
    if name in bpy.data.materials:
        return bpy.data.materials[name]
    m = base.copy()
    m.name = name
    nt = m.node_tree
    bsdf = next(n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED')
    uv = nt.nodes.new('ShaderNodeUVMap')
    uv.uv_map = f'skin:{part}'
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = image(P.source_part(part))
    tex.extension = 'EXTEND'
    tex.interpolation = 'Cubic'
    nt.links.new(uv.outputs['UV'], tex.inputs['Vector'])
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    m['skin_part'] = part
    m['skin_source'] = P.source_part(part)
    return m


manifest = {'blend_in': BLEND.name, 'fallback': FALLBACK, 'parts': {}}
skinned_faces = {}
for part, per in selection.items():
    src = P.source_part(part)
    if not (HERE / 'textures' / f'{src}.png').exists():
        print('SURF no texture for', part)
        continue
    # Each member's own fitted camera: the same tile framing its guide used,
    # or for a skin_from part the source framing applied to its own extent.
    cam = P.tile_camera(part, P.group_extent(part, per))
    for name, idx in per.items():
        ob = bpy.data.objects[name]
        me, mw = ob.data, ob.matrix_world
        layer = me.uv_layers.get(f'skin:{part}') or me.uv_layers.new(name=f'skin:{part}')
        if layer is None:
            raise SystemExit(f'{name}: no UV layer slot left for {part}')
        for i in idx:
            poly = me.polygons[i]
            for li in poly.loop_indices:
                u, v = P.project_uv(part, cam, mw @ me.vertices[me.loops[li].vertex_index].co)
                layer.data[li].uv = (u, v)
            base = ob.material_slots[poly.material_index].material if ob.material_slots else None
            mat = skin_material(part, base)
            k = ob.data.materials.find(mat.name)
            if k < 0:
                ob.data.materials.append(mat)
                k = len(ob.data.materials) - 1
            poly.material_index = k
            skinned_faces.setdefault(name, set()).add(i)
    manifest['parts'][part] = {'source_texture': f'textures/{src}.png', 'uv_layer': f'skin:{part}',
                               'objects': {n: len(i) for n, i in per.items()},
                               'texels_per_unit_stored': round(P.TILE[0] / cam[2], 1)}

# Fallback on everything no part claimed.
if FALLBACK == 'neutral':
    grey = {}
    for cls, v in (('cream', .12), ('oxide', .12), ('gunmetal', .12), ('dark', .03)):
        m = bpy.data.materials.new(f'Unskinned fallback {cls}')
        m.use_nodes = True
        b = m.node_tree.nodes.get('Principled BSDF')
        b.inputs['Base Color'].default_value = (v, v, v, 1)
        b.inputs['Roughness'].default_value = .7
        grey[cls] = m
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH':
            continue
        me = ob.data
        remap = {}
        for k, m in enumerate(list(me.materials)):
            if m is None or m.name.startswith('Skin '):
                continue
            cls = P.MATERIAL_CLASS.get(m.name.split('.')[0])
            if cls is None:
                continue  # lamps, glazing and damage/repair materials keep their own look
            j = me.materials.find(grey[cls].name)
            if j < 0:
                me.materials.append(grey[cls])
                j = len(me.materials) - 1
            remap[k] = j
        if remap:
            for p in me.polygons:
                if p.material_index in remap and p.index not in skinned_faces.get(ob.name, ()):
                    p.material_index = remap[p.material_index]

total = sum(len(v) for v in skinned_faces.values())
manifest['skinned_objects'] = {n: len(v) for n, v in sorted(skinned_faces.items())}
manifest['skinned_face_count'] = total
(HERE / 'skin-manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(OUT))
print('SURF skinned', total, 'faces on', len(skinned_faces), 'objects ->', OUT)
