"""Apply a condition state to an already-built .blend (the skinned clean build).

  sh ../../blender.sh --background --factory-startup --python damage_blend.py -- IN.blend OUT.blend [--state boarded]

Writes OUT.blend and OUT's sibling scars.json (the ts_damage record). The cuts open
the skin like any face: opened faces take 'Scar burnt metal'.
"""
import json
import sys
from pathlib import Path
import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import ts_damage

argv = sys.argv[sys.argv.index('--') + 1:]
src, dst = Path(argv[0]).resolve(), Path(argv[1]).resolve()
dst.parent.mkdir(parents=True, exist_ok=True)
state = argv[argv.index('--state') + 1] if '--state' in argv else 'boarded'
bpy.ops.wm.open_mainfile(filepath=str(src))
geom = json.loads((HERE / 'geometry.json').read_text(encoding='utf-8'))
record = ts_damage.apply(state, geom['metres_per_unit'])
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(dst))
(dst.parent / 'scars.json').write_text(json.dumps({'state': state, 'source': src.name, 'scars': record}, indent=1) + '\n')
for row in record:
    print('SCAR', row['id'], row['kind'], row.get('hit', ''), len(row.get('cut', [])), 'cut')
