"""Every kit turret on its barbette and every hardpoint, built headless for the detail check.

  SDK_CHECK="--mpu 0.95 --label turret-kit" sh ../../blender.sh --background --factory-startup \
      --python kit_sampler.py --python ../ship-detail-kit/check_detail.py -- [--mpu 0.95] [--only a,b]

No render and no deck: the turrets and hardpoints alone, at the catalogue's sprite rings
(LARGE 12.5, MEDIUM 9.5, SMALL 5.5 units) and a hull's metres per unit, so
ship-detail-kit/check_detail.py measures the kit against the shared standard
(ship-detail-guide.md, Edge treatment) the way it measures a hull.
"""
import argparse
import sys
from pathlib import Path
import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent))
import turret_kit as tk  # noqa: E402
import studio  # noqa: E402

argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument('--mpu', type=float, default=.95)
ap.add_argument('--only', default='')
args, _ = ap.parse_known_args(argv)

bpy.ops.wm.read_factory_settings(use_empty=True)
MATS = studio.hull_mats()
RING = {'LARGE': 12.5, 'MEDIUM': 9.5, 'SMALL': 5.5}
BOX = {'harpoonpod': 'MEDIUM', 'sabotpod': 'MEDIUM', 'salamanderpod': 'SMALL', 'annihilatorpod': 'MEDIUM',
       'pilum': 'MEDIUM', 'typhoon': 'LARGE', 'none': 'SMALL'}
only = set(args.only.split(',')) if args.only else None
i = 0
for w, s in tk.WEAPONS.items():
    if only and w not in only:
        continue
    r = RING[tk.sprite(w)['size'] if s['house'] in ('sprite', 'hand') else BOX.get(w, 'MEDIUM')]
    x, y = (i % 6)*70, -(i//6)*70
    lift = .22*r
    tk.barbette(f'Weapon {w} barbette', r, lift, MATS, args.mpu, foot=1.0, location=(x, y, 0))
    piv = bpy.data.objects.new(f'Weapon {w} pivot', None)
    bpy.context.scene.collection.objects.link(piv)
    piv.location = (x, y, lift)
    tk.build(w, f'Weapon {w}', piv, r, MATS, args.mpu)
    i += 1
for w in tk.HARDPOINT_WEAPONS:
    if only and f'hp-{w}' not in only:
        continue
    x, y = (i % 6)*70, -(i//6)*70
    mount = bpy.data.objects.new(f'Hardpoint {w} mount', None)
    bpy.context.scene.collection.objects.link(mount)
    mount.location = (x, y, 4)
    tk.build_hardpoint(w, f'Hardpoint {w}', mount, MATS, args.mpu)
    i += 1
bpy.context.view_layer.update()
print(f'kit sampler: {i} mounts at {args.mpu} m/u')
