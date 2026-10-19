"""Paint the C4/C5 port from above: overhead guides out, painted tiles back in.

Both shots look down on the port, so its surfaces are textured the way an aerial survey is made:
the ground and every roof are painted as one picture seen straight down, cut into tiles, and laid
back over the scene from directly above. The geometry-first rule (`art-source/geometry-first-
imagegen.md`) holds: this code owns every edge, ImageGen only paints material inside them.

    blender --background --factory-startup --python ortho_paint.py -- guide <tile> [<tile> ...]
    python  ortho_paint.py paint <tile> [--style <tile>]   (outside Blender: one paid generation)

Tiles are listed in `ortho/tiles-<layer>.json`: a name, a centre and size in metres, and a pixel
size. A list's `layer` orders it: coarse tiles over the whole port first, finer ones over them.
`guide` renders each one straight down through an orthographic camera with flat material colours
and outlines, everything that moves or carries its own texture left out (the ship, port traffic,
vehicles, people, dust, steam, cloud shadow), to `ortho/guides/<tile>.png`. `paint` sends that
guide and the tile's prompt (`ortho/prompts/<tile>.txt`, the shared `ortho/prompts/common.txt`
first) to `tools/codex_imagegen.py` and keeps the master as `ortho/raw/<tile>.png` with its
provenance. Guides are regenerated, never committed; the paint, its prompt and provenance are.
A tile is used by the scene once it is marked `"accepted": true` in its list: `apply(scene)` lays
every accepted tile over the materials of the static objects (`obj['ortho'] = 1`), on faces that
look up, and leaves walls and anything moving alone. Tiles later in the list lie over earlier ones,
their edges feathered so neighbouring paintings blend rather than seam (a tile's optional
`feather`, in metres, widens or narrows that blend; it should stay within half its overlap).
"""
import json
import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ORTHO = HERE / 'ortho'
REPO = HERE.parents[3]
FEATHER = 6.0                 # metres of blend at a tile's edge


def tiles():
    """Every tile in `ortho/tiles-*.json`, lower layers first (a coarse layer, then finer ones
    painted over it), each file owned by one piece of work so two never edit the same list."""
    out = {}
    lists = [json.loads(p.read_text()) for p in sorted(ORTHO.glob('tiles-*.json'))]
    for d in sorted(lists, key=lambda d: d.get('layer', 0)):
        for t in d['tiles']:
            out[t['name']] = t
    return out


# ---------------------------------------------------------------- inside Blender

def moving_or_textured(ob, ship_names):
    """True for what the overhead painting must leave out."""
    import bpy  # noqa: F401
    skip = ('Cloud shadows', 'Steam', 'Landing dust', 'Drive ', 'Scaffold arm', 'Arc ', 'Customs beacon',
            'Yard people', 'Yard hand', 'Jungle bay ground')
    if ob.name in ship_names or ob.name.startswith(skip) or ob.instance_type == 'COLLECTION':
        return True
    p = ob
    while p is not None:
        if p.animation_data is not None and p.animation_data.action is not None:
            return True
        p = p.parent
    return False


def mark_static(scene_objects, ship_names):
    scene_objects = list(scene_objects)
    for ob in scene_objects:
        ob['ortho'] = 0.0 if moving_or_textured(ob, ship_names) else 1.0
    # A hull placed as a collection instance is drawn from objects that are not in the scene, and
    # those may carry a stale 1 (or none, read through to whatever else holds the name): mark every
    # object of every instanced collection off, or the pad's markings are painted across its deck.
    seen = set()
    todo = [ob.instance_collection for ob in scene_objects if ob.instance_type == 'COLLECTION' and ob.instance_collection]
    while todo:
        coll = todo.pop()
        if coll.name in seen:
            continue
        seen.add(coll.name)
        for ob in coll.all_objects:
            ob['ortho'] = 0.0
            if ob.instance_type == 'COLLECTION' and ob.instance_collection:
                todo.append(ob.instance_collection)


def ship_names(c4):
    import bpy
    manifest = json.loads((c4.STATES / 'manifest.json').read_text())
    coll = bpy.data.collections.get(manifest['append_collection'])
    return {o.name for o in coll.all_objects} if coll else set()


def render_guides(names):
    import bpy
    sys.path.insert(0, str(HERE))
    os.environ['ORTHO_PAINT_OFF'] = '1'           # a guide is drawn from the blockout, never from paint
    import c4x_scene as c4
    scene = c4.build()
    scene.frame_set(c4.FRAMES - 1)
    ships = ship_names(c4)
    for ob in scene.objects:
        ob.hide_render = moving_or_textured(ob, ships) and ob.type in ('MESH', 'EMPTY', 'CURVE')
    for ob in scene.objects:                      # a hidden parent does not hide its children
        p = ob.parent
        while p is not None:
            if p.hide_render:
                ob.hide_render = True
                break
            p = p.parent
    for m in bpy.data.materials:                  # the flat renderer reads each material's display colour
        bsdf = m.node_tree and next((n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
        if bsdf is None:
            continue
        base = bsdf.inputs['Base Color']
        if not base.links:
            m.diffuse_color = base.default_value
        elif base.links[0].from_node.type == 'MIX':      # a patterned material (crop rows): its mean colour
            mix = base.links[0].from_node
            a, b = mix.inputs['A'].default_value, mix.inputs['B'].default_value
            m.diffuse_color = [(x + y) / 2 for x, y in zip(a, b)]
    scene.render.engine = 'BLENDER_WORKBENCH'
    sh = scene.display.shading
    sh.light = 'FLAT'
    sh.color_type = 'MATERIAL'
    sh.show_cavity = True
    sh.cavity_type = 'WORLD'
    sh.show_object_outline = True
    sh.object_outline_color = (0.05, 0.05, 0.05)
    sh.show_shadows = False
    scene.view_settings.view_transform = 'Standard'
    scene.view_settings.look = 'None'
    scene.render.film_transparent = False
    cam = c4.link(bpy.data.objects.new('Ortho guide', bpy.data.cameras.new('Ortho guide')))
    cam.data.type = 'ORTHO'
    cam.data.clip_start, cam.data.clip_end = 1.0, 2000.0
    scene.camera = cam
    (ORTHO / 'guides').mkdir(parents=True, exist_ok=True)
    all_tiles = tiles()
    for name in names:
        t = all_tiles[name]
        (cx, cy), (w, h), (pw, ph) = t['centre'], t['size'], t['pixels']
        cam.data.ortho_scale = max(w, h)
        cam.location = (cx, cy, 600.0)
        cam.rotation_euler = (0, 0, 0)
        scene.render.resolution_x, scene.render.resolution_y = pw, ph
        scene.render.resolution_percentage = 100
        scene.render.filepath = str(ORTHO / 'guides' / (name + '.png'))
        bpy.ops.render.render(write_still=True)
        print('ORTHO_GUIDE', name, flush=True)


def apply(scene, ships=()):
    """Lay every accepted tile over the static objects' materials, on upward faces only."""
    import bpy
    if os.environ.get('ORTHO_PAINT_OFF'):
        return 0
    accepted = [(t, ORTHO / 'raw' / (t['name'] + '.png')) for t in tiles().values() if t.get('accepted')]
    accepted = [(t, p) for t, p in accepted if p.exists()]
    if not accepted:
        return 0
    mark_static(scene.objects, set(ships))
    images = [(t, bpy.data.images.load(str(p), check_existing=True)) for t, p in accepted]
    done = 0
    for m in bpy.data.materials:
        if not m.use_nodes or any(n.type == 'TEX_IMAGE' for n in m.node_tree.nodes):
            continue
        nt = m.node_tree
        bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
        if bsdf is None:
            continue
        base = bsdf.inputs['Base Color']
        if base.links:
            colour = base.links[0].from_socket
        else:
            rgb = nt.nodes.new('ShaderNodeRGB')
            rgb.outputs[0].default_value = base.default_value
            colour = rgb.outputs[0]
        geo = nt.nodes.new('ShaderNodeNewGeometry')
        sep = nt.nodes.new('ShaderNodeSeparateXYZ')
        nt.links.new(geo.outputs['Position'], sep.inputs[0])
        nsep = nt.nodes.new('ShaderNodeSeparateXYZ')
        nt.links.new(geo.outputs['Normal'], nsep.inputs[0])
        facing = nt.nodes.new('ShaderNodeMapRange')               # upward faces only
        facing.inputs['From Min'].default_value = 0.6
        facing.inputs['From Max'].default_value = 0.8
        nt.links.new(nsep.outputs['Z'], facing.inputs['Value'])
        attr = nt.nodes.new('ShaderNodeAttribute')
        attr.attribute_type = 'OBJECT'
        attr.attribute_name = '["ortho"]'
        gate = nt.nodes.new('ShaderNodeMath')
        gate.operation = 'MULTIPLY'
        nt.links.new(facing.outputs['Result'], gate.inputs[0])
        nt.links.new(attr.outputs['Fac'], gate.inputs[1])
        for t, img in images:
            (cx, cy), (w, h) = t['centre'], t['size']                # pixels share the size's aspect
            u = math_node(nt, 'MULTIPLY_ADD', sep.outputs['X'], 1.0 / w, 0.5 - cx / w)
            v = math_node(nt, 'MULTIPLY_ADD', sep.outputs['Y'], 1.0 / h, 0.5 - cy / h)
            uv = nt.nodes.new('ShaderNodeCombineXYZ')
            nt.links.new(u, uv.inputs['X'])
            nt.links.new(v, uv.inputs['Y'])
            tex = nt.nodes.new('ShaderNodeTexImage')
            tex.image = img
            tex.extension = 'CLIP'
            tex.interpolation = 'Linear'
            nt.links.new(uv.outputs[0], tex.inputs['Vector'])
            # feather: full strength from `feather` metres inside the tile's edge, nothing at the edge.
            # At most half the tile's overlap with the one beneath, so this blend ends before that one fades.
            feather = t.get('feather', FEATHER)
            fu = math_node(nt, 'MINIMUM', u, math_node(nt, 'SUBTRACT', 1.0, u))
            fv = math_node(nt, 'MINIMUM', v, math_node(nt, 'SUBTRACT', 1.0, v))
            edge = math_node(nt, 'MINIMUM', math_node(nt, 'MULTIPLY', fu, w / feather),
                             math_node(nt, 'MULTIPLY', fv, h / feather))
            edge = math_node(nt, 'MINIMUM', math_node(nt, 'MAXIMUM', edge, 0.0), 1.0)
            k = math_node(nt, 'MULTIPLY', math_node(nt, 'MULTIPLY', tex.outputs['Alpha'], edge), gate.outputs[0])
            mix = nt.nodes.new('ShaderNodeMix')
            mix.data_type = 'RGBA'
            nt.links.new(k, mix.inputs['Factor'])
            nt.links.new(colour, mix.inputs['A'])
            nt.links.new(tex.outputs['Color'], mix.inputs['B'])
            colour = mix.outputs['Result']
        nt.links.new(colour, base)
        done += 1
    return done


def math_node(nt, op, a, b, c=None):
    n = nt.nodes.new('ShaderNodeMath')
    n.operation = op
    for i, v in enumerate((a, b, c)):
        if v is None:
            continue
        if isinstance(v, (int, float)):
            n.inputs[i].default_value = v
        else:
            nt.links.new(v, n.inputs[i])
    return n.outputs[0]


# ---------------------------------------------------------------- outside Blender

def paint(name, style=None):
    guide = ORTHO / 'guides' / (name + '.png')
    if not guide.exists():
        raise SystemExit('render the guide first: %s' % guide)
    prompt = (ORTHO / 'prompts' / 'common.txt').read_text() + '\n' + (ORTHO / 'prompts' / (name + '.txt')).read_text()
    (ORTHO / 'raw').mkdir(parents=True, exist_ok=True)
    full = ORTHO / 'raw' / (name + '.prompt.txt')
    full.write_text(prompt)
    out = ORTHO / 'raw' / (name + '.png')
    cmd = [sys.executable, str(REPO / 'tools' / 'codex_imagegen.py'), '--prompt', str(full), '--out', str(out),
           '--ref', '%s=%s' % (guide, 'EDIT TARGET: the overhead guide to repaint; keep every edge where it is')]
    if style:                                     # an accepted neighbour: same materials, same finish
        cmd += ['--ref', '%s=%s' % (ORTHO / 'raw' / (style + '.png'),
                                    'STYLE REFERENCE: an already painted neighbouring tile of the same place. '
                                    'Match its materials, colours, wear and finish exactly; do not copy its layout')]
    raise SystemExit(subprocess.call(cmd))


if __name__ == '__main__':
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    if argv[0] == 'guide':
        render_guides(argv[1:])
    elif argv[0] == 'paint':
        paint(argv[1], argv[argv.index('--style') + 1] if '--style' in argv else None)
