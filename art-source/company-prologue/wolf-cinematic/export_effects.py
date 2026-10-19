"""The Wolf (P)'s cinematic asset: the textured export plus the shared VFX kit's effects.

  blender --background --factory-startup --python export_effects.py -- [--base PATH] [--out DIR]

Input is the appendable export ../wolf-geometry/export_wolf.py makes from the
textured build (finalize.sh runs it on ../wolf-surface/build/skinned.blend
into build/export/): one collection `WOLF_P`, its root `WOLF_P | root`
(metres_per_unit 0.95), anchors child collection, images packed. The build
already declares the kit's muzzles (`Anchor turret <i> barrel|tube <k>
muzzle`, riding the turret kit's own muzzle anchors), so this script adds
only the effects: `swfx.apply(state='clean', hull=<this folder>)` with
vfx_hull.py (plumes, cores, bell glow and glow lights on the three drives,
exhaust haze, two energy gun rigs and two five-tube launcher rigs, the lamps;
no RCS, no landing dust, no lighting-rig preset), with `smk_atmosphere` 0
(vacuum). Saves wolf-p.blend and manifest.json beside this script.

Append `WOLF_P` alone for the bare ship, or with `SWFX` in the same load for
the effects (they ride the ship's anchors).
"""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import bpy

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
sys.path.insert(0, str(PROLOGUE / 'second-wind-vfx'))
sys.path.insert(0, str(HERE))
import swfx  # noqa: E402
import swfx_core  # noqa: E402
import vfx_hull  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--base', default=str(HERE / 'build' / 'export' / 'wolf-p.blend'))
ap.add_argument('--out', default=str(HERE))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
BASE, OUT = Path(args.base).resolve(), Path(args.out).resolve()
base_man = json.loads((BASE.parent / 'manifest.json').read_text(encoding='utf-8'))
GROUPS = ('thrusters', 'smoke', 'guns', 'lighting')
ASSET = 'wolf-p.blend'

bpy.ops.wm.open_mainfile(filepath=str(BASE))
ctx = swfx.apply(state='clean', groups=GROUPS, hull=HERE, rcs=False, dust=False)
ctx.set('smk_atmosphere', 0.0)
swfx_core.refresh_drivers(bpy.context.scene)
coll = bpy.data.collections[swfx_core.ROOT]
objs = list(coll.all_objects)
for img in bpy.data.images:
    if img.source == 'FILE' and not img.packed_file:
        img.pack()
OUT.mkdir(parents=True, exist_ok=True)
bpy.context.preferences.filepaths.save_version = 0
blend = OUT / ASSET
bpy.ops.wm.save_as_mainfile(filepath=str(blend), compress=True)


def summary(rec):
    if isinstance(rec, dict):
        return {k: (len(v) if isinstance(v, (list, dict)) else v) for k, v in rec.items()
                if isinstance(v, (list, dict, int, float, str, bool))}
    return rec


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


eng, guns = ctx.anchors.engines, ctx.anchors.guns
man = dict(base_man)
man['asset'] = ASSET
man['state'] = 'clean'
man['engines_running'] = [e.index for e in eng]
man['engine_sides'] = {'port': list(vfx_hull.PORT_ENGINES), 'starboard': list(vfx_hull.STARBOARD_ENGINES)}
man['gun_rigs'] = [{'mount': g.turret, 'tag': g.tag, 'kind': getattr(g, 'kind', 'gun'), 'scale': g.scale,
                    'anchor': g.empty.name} for g in guns]
man['gun_rigs_note'] = ("Kit mount numbers (swfx_guns.fire(ctx, mount, ...)): 1 WS 001 ion cannon and 2 WS 004 "
                        "mining blaster are 'energy' mounts (ENERGY_COLOURS), 3 WS 005 and 4 WS 006 the "
                        "Annihilator pods, five-tube launchers.")
man['lamps'] = [{'object': n, 'role': role, 'rgb': list(c) if c else None} for n, role, c in vfx_hull.LAMPS]
man['effects'] = {
    'collection': swfx_core.ROOT,
    'kit': 'second-wind-vfx (swfx.apply, hull profile wolf-cinematic/vfx_hull.py, state clean, '
           'groups thrusters, smoke, guns, lighting; rcs=False, dust=False; no rig preset)',
    'defaults': {'smk_atmosphere': 0.0},
    'objects': len(objs),
    'anchors': {k: (len(v) if isinstance(v, list) else v) for k, v in ctx.records['anchors'].items()},
    'engines': [{'index': e.index, 'side': e.side, 'radius': round(e.radius, 3), 'width': e.width} for e in eng],
    'controls': 'SWFX controls (animatable custom properties; see second-wind-vfx/README.md)',
    'groups': {g: summary(ctx.records.get(g)) for g in GROUPS},
    'append': f"load both '{base_man['append_collection']}' and '{swfx_core.ROOT}' in one call; "
              "the ship alone works without it",
}
man['sources'] = dict(base_man.get('sources', {}), vfx_hull_sha256=sha(HERE / 'vfx_hull.py'))
man['asset_sha256'] = sha(blend)
(OUT / 'manifest.json').write_text(json.dumps(man, indent=1) + '\n', encoding='utf-8')
print('EFFECTS', len(objs), 'objects; engines', len(eng), [(e.index, e.side, round(e.radius, 2)) for e in eng],
      'guns', len(guns), [(g.turret, g.tag, getattr(g, 'kind', 'gun')) for g in guns], 'lamps', len(ctx.anchors.lamps))
