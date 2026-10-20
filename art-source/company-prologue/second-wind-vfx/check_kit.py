"""Integration check: build the whole kit on one ship build and report.

  blender --background --factory-startup --python check_kit.py -- --blend build/_ships/patched.blend --state patched
      [--hull DIR] [--fingerprint OUT.json] [--fire]

Prints one JSON line: anchors found, objects per SWFX collection, running
plumes, seal wisps, conduit spark emitters (one per sparking scar anchor),
lights, gun and launcher rigs, and every driver that is invalid or not a
simple expression (those would need Python auto-run, which a cinematic file
must not depend on). Exits non-zero if any driver is invalid, a state rule is
broken, a build is not repeatable (a second apply must give the same counts),
two guns share a knob label, or a mount got the wrong kind of rig (a launcher
tube carries a launch bloom and no muzzle flash; a gun the reverse).

--hull DIR      another hull's profile (vfx_hull.py; swfx.apply(hull=DIR)),
                for an exported asset such as ../legion-cinematic/legion.blend
--fingerprint   writes a canonical dump of everything the kit built (objects,
                transforms, custom properties, keyframes, drivers, lights,
                materials, meshes, records) and prints its sha256: run it
                before and after a kit change to show a hull's output is
                byte-identical
--fire          afterwards fires every turret (three rounds) and checks that
                guns make tracers or puffs, energy mounts a flash with no puff
                or brass, and launchers missiles, never the other's effects
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import swfx  # noqa: E402
import swfx_core  # noqa: E402

args = argparse.ArgumentParser()
args.add_argument('--blend', required=True)
args.add_argument('--state', required=True)
args.add_argument('--preset', default='deep-space')
args.add_argument('--hull')
args.add_argument('--fingerprint')
args.add_argument('--fire', action='store_true')
args = args.parse_args(sys.argv[sys.argv.index('--') + 1:])
bpy.ops.wm.open_mainfile(filepath=str(Path(args.blend).resolve()))
HULL = str(Path(args.hull).resolve()) if args.hull else None


def drivers():
    bad = []
    total = 0
    stores = (bpy.data.objects, bpy.data.lights, bpy.data.materials, bpy.data.worlds,
              bpy.data.scenes, bpy.data.node_groups, bpy.data.meshes, bpy.data.cameras)
    for store in stores:
        for idb in store:
            for owner in (idb, getattr(idb, 'node_tree', None)):
                ad = getattr(owner, 'animation_data', None) if owner is not None else None
                if not ad:
                    continue
                for fc in ad.drivers:
                    total += 1
                    d = fc.driver
                    if not d.is_valid or (d.type == 'SCRIPTED' and not d.is_simple_expression):
                        bad.append(f'{idb.name}:{fc.data_path} valid={d.is_valid} simple={d.is_simple_expression}')
    return total, bad


def census():
    root = bpy.data.collections[swfx_core.ROOT]
    return {c.name: len(c.objects) for c in root.children_recursive}


def _val(v):
    if isinstance(v, float):
        return round(v, 6)
    if hasattr(v, '__len__') and not isinstance(v, str):
        try:
            return [_val(x) for x in v]
        except TypeError:
            return str(v)
    if isinstance(v, (int, str, bool)) or v is None:
        return v
    return str(v)


def _anim(idb):
    ad = getattr(idb, 'animation_data', None)
    if not ad:
        return None
    out = {'drivers': sorted([fc.data_path, fc.array_index, fc.driver.type, fc.driver.expression,
                              sorted([v.name, v.type, v.targets[0].data_path,
                                      getattr(v.targets[0].id, 'name', None)] for v in fc.driver.variables)]
                             for fc in ad.drivers)}
    import swfx_guns
    out['fcurves'] = sorted([fc.data_path, fc.array_index,
                             [[_val(k.co.x), _val(k.co.y), k.interpolation] for k in fc.keyframe_points]]
                            for fc in swfx_guns._fcurves(idb))
    return out


def _props(idb):
    return {k: _val(idb[k]) for k in sorted(idb.keys()) if not k.startswith('_')}


def _tree(nt):
    if nt is None:
        return None
    nodes = []
    for n in sorted(nt.nodes, key=lambda n: n.name):
        ins = [[i.identifier, _val(i.default_value) if hasattr(i, 'default_value') else None] for i in n.inputs]
        extra = {a: _val(getattr(n, a)) for a in ('operation', 'data_type', 'noise_dimensions', 'blend_type')
                 if hasattr(n, a)}
        nodes.append([n.name, n.bl_idname, ins, extra])
    links = sorted([l.from_node.name, l.from_socket.identifier, l.to_node.name, l.to_socket.identifier]
                   for l in nt.links)
    return {'nodes': nodes, 'links': links, 'anim': _anim(nt)}


def fingerprint(ctx):
    """Everything the kit built, canonically: names, transforms, properties,
    keyframes, drivers, lights, materials and meshes, plus its records."""
    root = bpy.data.collections[swfx_core.ROOT]
    obs = sorted({o for c in [root, *root.children_recursive] for o in c.objects}, key=lambda o: o.name)
    dump = {'collections': sorted([c.name, sorted(o.name for o in c.objects)] for c in [root, *root.children_recursive])}
    rows = []
    for o in obs:
        row = {'name': o.name, 'type': o.type, 'parent': o.parent.name if o.parent else None,
               'basis': _val([list(r) for r in o.matrix_basis]),
               'parent_inverse': _val([list(r) for r in o.matrix_parent_inverse]),
               'world': _val([list(r) for r in o.matrix_world]),
               'hide_render': o.hide_render, 'color': _val(o.color), 'props': _props(o), 'anim': _anim(o),
               'flags': [o.visible_camera, o.visible_shadow, o.empty_display_type if o.type == 'EMPTY' else None,
                         _val(o.empty_display_size) if o.type == 'EMPTY' else None],
               'data': o.data.name if o.data else None}
        if o.type == 'LIGHT':
            d = o.data
            row['light'] = [d.type, _val(d.energy), _val(d.color), _val(d.shadow_soft_size), _anim(d),
                            _val(getattr(d, 'spot_size', 0)), _tree(d.node_tree) if d.use_nodes else None]
        if o.type == 'MESH':
            me = o.data
            co = [round(c, 5) for v in me.vertices for c in v.co]
            row['mesh'] = [len(me.vertices), len(me.polygons), hashlib.sha256(json.dumps(co).encode()).hexdigest(),
                           [m.name if m else None for m in me.materials]]
        rows.append(row)
    dump['objects'] = rows
    mats = sorted({m for o in obs if o.type == 'MESH' for m in o.data.materials if m}, key=lambda m: m.name)
    mats += sorted((m for m in bpy.data.materials if m.name.startswith('SWFX') and m not in mats), key=lambda m: m.name)
    dump['materials'] = [[m.name, _tree(m.node_tree), _props(m)] for m in mats]
    dump['lens_materials'] = [[m.name, _tree(m.node_tree), _props(m)] for m in sorted(bpy.data.materials, key=lambda m: m.name)
                              if not m.name.startswith('SWFX') and m.get('swfx_base_emission') is not None]
    dump['scene'] = {'world': _tree(ctx.scene.world.node_tree) if ctx.scene.world and ctx.scene.world.node_tree else None,
                     'anim': _anim(ctx.scene), 'props': _props(ctx.scene)}
    dump['records'] = json.loads(json.dumps(ctx.records, default=str))
    return dump


ctx = swfx.apply(state=args.state, preset=args.preset, hull=HULL)
first = census()
ctx = swfx.apply(state=args.state, preset=args.preset, hull=HULL)
second = census()
bpy.context.scene.frame_set(12)
total, bad = drivers()
plumes = sorted({o.name.split(' e')[-1].split()[0] for o in bpy.data.objects
                 if o.name.startswith('SWFX thrusters plume e')})
problems = list(bad)
if first != second:
    problems.append(f'rebuild changed counts: {first} -> {second}')
mode = swfx_core.STATES[args.state]['engines']
for i in sorted(mode):
    running = str(i) in plumes
    should = mode[i] in ('salvaged', 'clean')
    if running != should:
        problems.append(f'engine {i}: plume={running} but mode={mode[i]}')
wisps = len(ctx.records.get('smoke', {}).get('seal_wisps', []))
if bool(wisps) != swfx_core.STATES[args.state]['seal_wisps']:
    problems.append(f'seal wisps: {wisps} built, state allows {swfx_core.STATES[args.state]["seal_wisps"]}')
# Conduit sparks: exactly one emitter per scar spark anchor that is sparking.
spark_anchors = ctx.records['anchors'].get('sparks', [])
live = sorted(s['name'] for s in spark_anchors if s['sparking'])
built = sorted(s['anchor'] for s in ctx.records.get('smoke', {}).get('sparks', []))
if built != live:
    problems.append(f'conduit sparks built at {built}, but sparking anchors are {live}')
# Guns: one knob label per barrel, and each mount the right kind of rig.
import swfx_guns  # noqa: E402  (swfx.apply reloaded it)
import swfx_hull  # noqa: E402
guns = list(ctx.anchors.guns) if ctx.spec.get('guns', True) else []
labels = [swfx_guns.gun_label(g) for g in guns]
dupes = sorted({l for l in labels if labels.count(l) > 1})
if dupes:
    problems.append(f'guns share knob labels: {dupes}')
launch_labels = {r['label'] for r in ctx.records.get('guns', {}).get('launchers', [])}
for g, label in zip(guns, labels):
    launcher = getattr(g, 'kind', 'gun') == 'launcher'
    flash = bpy.data.objects.get(f'{swfx_guns.PREFIX} flash {label}')
    bloom = bpy.data.objects.get(f'{swfx_guns.PREFIX} launch {label}')
    if launcher and (flash is not None or bloom is None or label not in launch_labels):
        problems.append(f'launcher t{label}: needs a launch bloom and no muzzle flash')
    if not launcher and (flash is None or bloom is not None):
        problems.append(f'gun {label}: needs a muzzle flash and no launch bloom')
lit = [o.name for o in bpy.data.objects if o.type == 'LIGHT' and o.name.startswith('SWFX lighting')
       and 'rig' not in o.name.lower() and o.data.energy > 0]
fired, shots = {}, []
if args.fire:
    for t in sorted({g.turret for g in guns}):
        recs = swfx_guns.fire(ctx, t, 10, 3, 4)
        kinds = {getattr(g, 'kind', 'gun') for g in guns if g.turret == t}
        launcher = kinds == {'launcher'}
        energy = kinds == {'energy'}
        fired[t] = len(recs)
        shots += recs
        for r in recs:
            if launcher and ('missile' not in r or 'tracer' in r or 'casing' in r):
                problems.append(f'turret {t} launch {r.get("tag")} f{r["frame"]}: expected a missile and no gun effects')
            # An energy mount fires a flash and a bolt in its colour: no propellant puff, no brass.
            if energy and ('missile' in r or 'puff' in r or 'casing' in r or not r.get('flash_frames')):
                problems.append(f'turret {t} bolt {r.get("tag")} f{r["frame"]}: expected a flash, no puff, brass or missile')
            if not launcher and not energy and ('missile' in r or 'puff' not in r):
                problems.append(f'turret {t} round {r.get("tag")} f{r["frame"]}: expected gun effects and no missile')
        if len(recs) != 3:
            problems.append(f'turret {t}: fired {len(recs)} rounds of 3')
    bpy.context.scene.frame_set(14)
    total2, bad2 = drivers()
    problems += [b for b in bad2 if b not in bad]
digest = None
if args.fingerprint:
    dump = fingerprint(ctx)
    dump['shots'] = json.loads(json.dumps(shots, default=str))
    blob = json.dumps(dump, indent=1, sort_keys=True)
    Path(args.fingerprint).resolve().write_text(blob + '\n', encoding='utf-8')
    digest = hashlib.sha256(blob.encode()).hexdigest()
print('CHECK ' + json.dumps({
    'state': args.state, 'hull': swfx_hull.NAME,
    'anchors': {k: len(v) for k, v in ctx.records['anchors'].items() if isinstance(v, list)},
    'collections': second, 'plumes': plumes, 'seal_wisps': wisps,
    'spark_anchors': len(spark_anchors), 'spark_emitters': len(built), 'ship_lamps_energised': len(lit),
    'guns': len(guns) - len(launch_labels), 'launchers': len(launch_labels), 'fired': fired,
    'drivers': total, 'fingerprint': digest, 'problems': problems}))
sys.exit(1 if problems else 0)
