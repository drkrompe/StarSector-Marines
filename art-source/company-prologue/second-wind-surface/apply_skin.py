"""Surfacing phase: put the graded part skins and class swatches on a build.

  blender --background --factory-startup --python apply_skin.py
      [-- --blend IN.blend] [--out OUT.blend] [--fallback swatch|neutral|base]
          [--group g[,g]] [--manifest PATH] [--wear STATE]

Opens a geometry build (default the clean one, build/geom/second-wind-geometry.blend,
made by importing ../second-wind-geometry/build_reference.py; the geometry
modules are never edited). A damage state's build works the same way: the
skin keys off base object names and base faces, which sw_damage never
renames or removes. Per part in parts.PARTS that has a texture:
- selects the part's faces on the object's BASE mesh by the same rule
  render_parts.py used (parts.all_selections), so the skin lands on exactly
  the faces its guide showed;
- writes each corner's ortho projection into the part's tile to the object's
  one shared UV layer `skin` (one layer per object, not per part: Blender
  allows eight, and the main hull alone carries more parts than that; each
  face belongs to exactly one part, so one layer holds them all);
- gives those faces a material `Skin <part> | <base material>`: the base
  material's metallic and roughness, base colour from the part image through
  `skin`, times the part's variation gain (variation.json), blended toward
  the class swatch on faces turned more than ~30 deg from the part's view
  (parts.SQUARE): chamfers and the bevel modifier's strips.
Unclaimed faces next to a claimed one take that part's projection in `skin`
too, so a bevel strip between them interpolates sensible coordinates instead
of running to the tile corner.

Faces no part claims (`--fallback`):
- `swatch` (default) gives every base-material face (the five palette classes)
  `Swatch <class> | <base>`: the graded class swatch, box-projected in object
  space at parts.SWATCH_UNITS per image. A class without
  textures/swatch-<class>.png gets its flat palette colour.
- `neutral` swaps them for flat dark grey, so part coverage reads honestly.
- `base` leaves the build's flat base colours.
`--wear STATE` (a wear.json row: derelict, patched, refit, restored) then
routes every skin and swatch material through that state's wear layer
(wear.py): abrasion, pitting, fade, grime, soot and neglect, in ship space.
Without it the build carries the bare base skin.
Lamps, glazing and a damage state's scar and repair materials are never
touched. Modifiers stay live. Writes OUT (packed images) and a manifest
(default skin-manifest.json beside the scripts for the clean build).
"""
from pathlib import Path
import json
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import parts as P
import variation as V

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
def arg(name, default):
    return argv[argv.index(name) + 1] if name in argv else default
BLEND = HERE / Path(arg('--blend', HERE / 'build' / 'geom' / 'second-wind-geometry.blend'))
OUT = HERE / Path(arg('--out', HERE / 'build' / 'skinned.blend'))
FALLBACK = arg('--fallback', 'swatch')
GROUPS = P.group_arg(argv)
WEAR = arg('--wear', 'none')  # a wear.json state (wear.py), or none for the bare base skin
MANIFEST = HERE / Path(arg('--manifest', HERE / 'skin-manifest.json'))
PAL = json.loads((HERE / 'palette.json').read_text())['classes']
VAR = V.load()

bpy.ops.wm.open_mainfile(filepath=str(BLEND))
# The integrated run is strict; a group's own run tolerates another group's
# half-authored part.
selection = P.all_selections(strict=None if GROUPS is None else set(P.parts_in(GROUPS)))
images = {}


def srgb_to_linear(c):
    c = c / 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def load_image(path, name, colour=True):
    if name not in images:
        im = bpy.data.images.load(str(path))
        im.name = name
        if not colour:
            im.colorspace_settings.name = 'Non-Color'
        im.pack()
        images[name] = im
    return images[name]


def class_of(material):
    return P.MATERIAL_CLASS.get(material.name.split('.')[0]) if material else None


def swatch_group(cls):
    """Node group 'SW swatch <cls>': colour of the class swatch, box-projected."""
    name = f'SW swatch {cls}'
    if name in bpy.data.node_groups:
        return bpy.data.node_groups[name]
    g = bpy.data.node_groups.new(name, 'ShaderNodeTree')
    g.interface.new_socket('Color', in_out='OUTPUT', socket_type='NodeSocketColor')
    out = g.nodes.new('NodeGroupOutput')
    path = HERE / 'textures' / f'swatch-{cls}.png'
    if path.exists():
        tc = g.nodes.new('ShaderNodeTexCoord')
        mp = g.nodes.new('ShaderNodeMapping')
        s = 1 / P.SWATCH_UNITS
        mp.inputs['Scale'].default_value = (s, s, s)
        tex = g.nodes.new('ShaderNodeTexImage')
        tex.image = load_image(path, f'Swatch {cls}')
        tex.projection = 'BOX'
        tex.projection_blend = 0.3
        tex.extension = 'REPEAT'  # swatches are made periodic by swatches.py
        g.links.new(tc.outputs['Object'], mp.inputs['Vector'])
        g.links.new(mp.outputs['Vector'], tex.inputs['Vector'])
        g.links.new(tex.outputs['Color'], out.inputs['Color'])
    else:
        rgb = g.nodes.new('ShaderNodeRGB')
        srgb = PAL[cls].get('mean_srgb') or [128, 128, 128]
        rgb.outputs[0].default_value = (*[srgb_to_linear(c) for c in srgb], 1)
        g.links.new(rgb.outputs[0], out.inputs['Color'])
    return g


def bsdf_of(m):
    return next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')


def skin_material(part, base):
    name = f'Skin {part} | {base.name}'
    if name in bpy.data.materials:
        return bpy.data.materials[name]
    cls = class_of(base) or 'gunmetal'
    m = base.copy()
    m.name = name
    nt = m.node_tree
    bsdf = bsdf_of(m)
    uv = nt.nodes.new('ShaderNodeUVMap')
    uv.uv_map = 'skin'
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.name = 'SKIN image'
    tex.image = load_image(HERE / 'textures' / f'{P.source_part(part)}.png', f'Skin {P.source_part(part)}')
    tex.extension = 'EXTEND'
    tex.interpolation = 'Cubic'
    nt.links.new(uv.outputs['UV'], tex.inputs['Vector'])
    # Variation: a linear-light gain taking the palette mean to mean + offset.
    unit = VAR.get(part, {}).get('unit_offset_L_a_b') or V.offset(part)
    gain = V.gain(PAL[cls]['mean'], unit, cls)
    gn = nt.nodes.new('ShaderNodeRGB')
    gn.name = 'SKIN variation gain'
    gn.outputs[0].default_value = (*gain, 1)
    mul = nt.nodes.new('ShaderNodeMix')
    mul.data_type, mul.blend_type = 'RGBA', 'MULTIPLY'
    mul.inputs['Factor'].default_value = 1
    nt.links.new(tex.outputs['Color'], mul.inputs['A'])
    nt.links.new(gn.outputs[0], mul.inputs['B'])
    # Oblique faces (chamfers, bevel strips) blend to the class swatch.
    geo = nt.nodes.new('ShaderNodeNewGeometry')
    dot = nt.nodes.new('ShaderNodeVectorMath')
    dot.operation = 'DOT_PRODUCT'
    dot.inputs[1].default_value = Vector(P.PARTS[part]['dir']).normalized()
    ab = nt.nodes.new('ShaderNodeMath')
    ab.operation = 'ABSOLUTE'
    sq = P.PARTS[part].get('square', P.SQUARE)
    rng = nt.nodes.new('ShaderNodeMapRange')
    rng.interpolation_type = 'SMOOTHSTEP'
    rng.inputs['From Min'].default_value = sq - P.SQUARE_BAND
    rng.inputs['From Max'].default_value = sq
    nt.links.new(geo.outputs['True Normal'], dot.inputs[0])
    nt.links.new(dot.outputs['Value'], ab.inputs[0])
    nt.links.new(ab.outputs[0], rng.inputs['Value'])
    sw = nt.nodes.new('ShaderNodeGroup')
    sw.node_tree = swatch_group(cls)
    mix = nt.nodes.new('ShaderNodeMix')
    mix.name = 'SKIN square blend'
    mix.data_type = 'RGBA'
    nt.links.new(rng.outputs['Result'], mix.inputs['Factor'])
    nt.links.new(sw.outputs['Color'], mix.inputs['A'])
    nt.links.new(mul.outputs['Result'], mix.inputs['B'])
    nt.links.new(mix.outputs['Result'], bsdf.inputs['Base Color'])
    m['skin_part'] = part
    m['skin_source'] = P.source_part(part)
    m['skin_class'] = cls
    m['skin_gain'] = gain
    return m


def swatch_material(base):
    cls = class_of(base)
    name = f'Swatch {cls} | {base.name}'
    if name in bpy.data.materials:
        return bpy.data.materials[name]
    m = base.copy()
    m.name = name
    nt = m.node_tree
    sw = nt.nodes.new('ShaderNodeGroup')
    sw.node_tree = swatch_group(cls)
    nt.links.new(sw.outputs['Color'], bsdf_of(m).inputs['Base Color'])
    m['skin_swatch'] = cls
    return m


def overridden(ob, k):
    """A damage state's object-level material override (sw_damage `scar_override`,
    e.g. primer gun houses) on slot k: its faces keep the override, unskinned."""
    return k < len(ob.material_slots) and ob.material_slots[k].link == 'OBJECT'


def slot_of(me, mat):
    k = me.materials.find(mat.name)
    if k < 0:
        me.materials.append(mat)
        k = len(me.materials) - 1
    return k


manifest = {'blend_in': str(BLEND.name), 'fallback': FALLBACK, 'uv_layer': 'skin', 'parts': {}}
skinned_faces = {}
cams = {}
face_part = {}
for part, per in selection.items():
    src = P.source_part(part)
    if not (HERE / 'textures' / f'{src}.png').exists():
        print('SURF no texture for', part)
        continue
    # Each part's own fitted camera: the same tile framing its guide used,
    # or for a skin_from part the source framing applied to its own extent.
    cam = cams[part] = P.tile_camera(part, P.group_extent(part, per))
    for name, idx in per.items():
        ob = bpy.data.objects[name]
        me, mw = ob.data, ob.matrix_world
        layer = me.uv_layers.get('skin') or me.uv_layers.new(name='skin')
        if layer is None:
            raise SystemExit(f'{name}: no UV layer slot left for skin')
        for i in idx:
            poly = me.polygons[i]
            if overridden(ob, poly.material_index):
                continue
            for li in poly.loop_indices:
                layer.data[li].uv = P.project_uv(part, cam, mw @ me.vertices[me.loops[li].vertex_index].co)
            base = me.materials[poly.material_index] if me.materials else None
            poly.material_index = slot_of(me, skin_material(part, base))
            skinned_faces.setdefault(name, set()).add(i)
            face_part[(name, i)] = part
    manifest['parts'][part] = {'group': P.PART_GROUP[part], 'source_texture': f'textures/{src}.png',
                               'mirror_u': bool(P.PARTS[part].get('mirror_u')),
                               'objects': {n: len(i) for n, i in per.items()},
                               'texels_per_unit_stored': round(P.TILE[0] / cam[2], 1),
                               'variation_unit_offset': VAR.get(part, {}).get('unit_offset_L_a_b') or V.offset(part)}

# Unclaimed neighbours: continue the adjacent part's projection into `skin`.
for name in skinned_faces:
    ob = bpy.data.objects[name]
    me, mw = ob.data, ob.matrix_world
    layer = me.uv_layers['skin']
    vert_part = {}
    for i in skinned_faces[name]:
        for v in me.polygons[i].vertices:
            vert_part.setdefault(v, face_part[(name, i)])
    for poly in me.polygons:
        if poly.index in skinned_faces[name]:
            continue
        part = next((vert_part[v] for v in poly.vertices if v in vert_part), None)
        if part is None:
            continue
        for li in poly.loop_indices:
            layer.data[li].uv = P.project_uv(part, cams[part], mw @ me.vertices[me.loops[li].vertex_index].co)

# Fallback on everything no part claimed.
counts = {'skin': 0, 'swatch': 0, 'neutral': 0}
grey = {}
for ob in bpy.context.scene.objects:
    if ob.type != 'MESH' or FALLBACK == 'base':
        continue
    me = ob.data
    remap = {}
    for k, m in enumerate(list(me.materials)):
        if m is None or m.name.startswith(('Skin ', 'Swatch ', 'Unskinned ')) or overridden(ob, k):
            continue
        cls = class_of(m)
        if cls is None:
            continue  # lamps, glazing and damage/repair materials keep their own look
        if FALLBACK == 'swatch':
            remap[k] = slot_of(me, swatch_material(m))
        else:
            if cls not in grey:
                v = .03 if cls == 'dark' else .12
                g = bpy.data.materials.new(f'Unskinned fallback {cls}')
                g.use_nodes = True
                b = g.node_tree.nodes.get('Principled BSDF')
                b.inputs['Base Color'].default_value = (v, v, v, 1)
                b.inputs['Roughness'].default_value = .7
                grey[cls] = g
            remap[k] = slot_of(me, grey[cls])
    if remap:
        for p in me.polygons:
            if p.material_index in remap and p.index not in skinned_faces.get(ob.name, ()):
                p.material_index = remap[p.material_index]
                counts['swatch' if FALLBACK == 'swatch' else 'neutral'] += 1

# Wear (wear.py): bake the hull frame on every mesh carrying a skin or swatch,
# then route each skin/swatch material through the state's wear group.
if WEAR != 'none':
    import wear as W
    worn = set()
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH':
            continue
        mats = [m for m in ob.data.materials if m is not None and (m.get('skin_part') or m.get('skin_swatch'))]
        if not mats:
            continue
        W.bake_hull_frame(ob)
        for m in mats:
            if m.name not in worn:
                W.insert(m, WEAR, m.get('skin_class') or m.get('skin_swatch'))
                worn.add(m.name)
    manifest['wear'] = {'state': WEAR, 'weights': W.STATES[WEAR], 'materials': len(worn)}

total = sum(len(v) for v in skinned_faces.values())
counts['skin'] = total
manifest['swatches'] = {c: (HERE / 'textures' / f'swatch-{c}.png').exists() for c in PAL}
manifest['face_counts'] = counts
manifest['skinned_objects'] = {n: len(v) for n, v in sorted(skinned_faces.items())}
manifest['skinned_face_count'] = total
MANIFEST.write_text(json.dumps(manifest, indent=1) + '\n')
bpy.context.preferences.filepaths.save_version = 0
OUT.parent.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT))
print('SURF skinned', total, 'faces on', len(skinned_faces), 'objects;', counts, '->', OUT)
