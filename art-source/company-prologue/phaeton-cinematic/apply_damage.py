"""Turn the skinned clean Phaeton into the skinned wreck (condition state 'destroyed').

  blender --background --factory-startup --python apply_damage.py -- [--skinned PATH] [--out PATH]

Surfacing is applied on the clean build (second-wind-pipeline.md, Layers), so
the damage runs after it: ph_damage's booleans cut the skin like any face,
the faces they make take the scar materials, and every base object keeps its
name, material and `skin` UVs. Input ../phaeton-surface/build/skinned.blend,
output ../phaeton-surface/build/skinned-destroyed.blend (both ignored).
"""
from pathlib import Path
import argparse
import sys
import bpy

HERE = Path(__file__).resolve().parent
SURFACE = HERE.parent / 'phaeton-surface'
sys.path.insert(0, str(HERE.parent / 'phaeton-geometry'))
import ph_damage  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--skinned', default=str(SURFACE / 'build' / 'skinned.blend'))
ap.add_argument('--out', default=str(SURFACE / 'build' / 'skinned-destroyed.blend'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])

bpy.ops.wm.open_mainfile(filepath=str(Path(args.skinned).resolve()))
for ob in bpy.context.selected_objects:
    ob.select_set(False)
record = ph_damage.apply('destroyed')
bpy.context.scene['phaeton_state'] = 'destroyed'
bpy.context.scene['phaeton_scars'] = record['scars']
print('DAMAGE', len(record['fire_anchors']), 'fire anchors', len(record['vent_anchors']), 'vent anchors')
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(Path(args.out).resolve()), compress=True)
print('DAMAGE saved', args.out)
