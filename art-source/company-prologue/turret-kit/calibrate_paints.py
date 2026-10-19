"""Solve each sprite paint's base colour so the studio renders the sprite's colour.

  blender --background --factory-startup --python calibrate_paints.py [-- --iterations 6]

The sprites are pictures of their turrets from above, so a paint is right when
the model's top view shows the sprite's colour. Under the review studio
(studio.py: AgX, a bright grey world, three suns) a flat roof of base colour C
renders paler and greyer than C. For every paint the hand-built turrets use
(turret_kit.paint_targets, plus the loaded rounds' bodies) this lays a flat
tile of the real weathered material (turret_kit.paint_material) under the
studio, renders it from straight above, compares the tile's centre with the
target, and corrects the base channel by channel until they agree. Open flat
plate carries no wear or grime, so the tile measures the paint itself.

Writes paint_calibration.json beside it: {finish: {target: base}}, linear RGB.
Rerun it after changing the studio, a paint or the paint material.
"""
import argparse
import json
import math
import sys
from pathlib import Path
import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import turret_kit as tk
import studio

argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument('--iterations', type=int, default=6)
ap.add_argument('--out', default=str(HERE/'paint_calibration.json'))
ap.add_argument('--scratch', default=str(HERE.parents[2]/'build'/'turret-calibration'))
args = ap.parse_args(argv)


def to_srgb(c):
    return 12.92*c if c <= .0031308 else 1.055*c**(1/2.4)-.055


def to_lin(c):
    return c/12.92 if c <= .04045 else ((c+.055)/1.055)**2.4


targets = {}           # (finish, key) -> rgb
for w, spec in tk.WEAPONS.items():
    if spec.get('house') != 'hand':
        continue
    s = tk.spec_for(w)
    for key, rgb in tk.paint_targets(s).items():
        targets[(tk.FINISH[key], tk._key(rgb))] = rgb
    if 'mcol' in s:
        m = tk.livery(s['mcol'], 'missile')
        targets[('paint', tk._key(m))] = m
items = sorted(targets.items())
print(f'{len(items)} paints to calibrate')

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
studio.setup(scene, samples=16)
TILE, PITCH, PX = 1.0, 3.0, 24
cols = math.ceil(math.sqrt(len(items)))
rows = math.ceil(len(items)/cols)
scene.render.resolution_x, scene.render.resolution_y = cols*PX*3, rows*PX*3
cam_data = bpy.data.cameras.new('cal')
cam_data.type = 'ORTHO'
cam_data.ortho_scale = cols*PITCH
cam = bpy.data.objects.new('cal', cam_data)
scene.collection.objects.link(cam)
cam.location = ((cols-1)*PITCH/2, -(rows-1)*PITCH/2, 50)
scene.camera = cam
tiles = []
for i, ((finish, key), rgb) in enumerate(items):
    bpy.ops.mesh.primitive_plane_add(size=TILE, location=((i % cols)*PITCH, -(i//cols)*PITCH, 0))
    tiles.append(bpy.context.object)

base = {k: tuple(rgb) for k, rgb in items}
Path(args.scratch).mkdir(parents=True, exist_ok=True)
for it in range(args.iterations+1):
    for ob, ((finish, key), rgb) in zip(tiles, items):
        old = ob.data.materials[0] if ob.data.materials else None
        ob.data.materials.clear()
        if old is not None:
            bpy.data.materials.remove(old)
        ob.data.materials.append(tk.paint_material(f'cal {it} {finish} {key}', rgb, finish, base=base[(finish, key)]))
    path = str(Path(args.scratch)/f'cal-{it}.png')
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    img = bpy.data.images.load(path)
    W, H = img.size
    px = list(img.pixels)
    worst = 0.0
    for i, ((finish, key), rgb) in enumerate(items):
        cx, cy = (i % cols)*PX*3+PX*1.5, H-1-((i//cols)*PX*3+PX*1.5)
        acc, n = [0.0, 0.0, 0.0], 0
        for y in range(int(cy-PX*.3), int(cy+PX*.3)):
            for x in range(int(cx-PX*.3), int(cx+PX*.3)):
                o = 4*(y*W+x)
                for c in range(3):
                    acc[c] += px[o+c]
                n += 1
        got = [a/n for a in acc]                         # display (sRGB-encoded) values
        want = [to_srgb(c) for c in rgb]
        err = max(abs(g-w_) for g, w_ in zip(got, want))
        worst = max(worst, err)
        if it == args.iterations and err > 6/255:
            print(f'  {finish} {key}: want {[round(255*c) for c in want]} got {[round(255*c) for c in got]}'
                  f' base {[round(c, 3) for c in base[(finish, key)]]}')
        if it < args.iterations:
            base[(finish, key)] = tuple(min(1.0, max(1e-4, b*to_lin(w_)/max(to_lin(g), 1e-5)))
                                        for b, g, w_ in zip(base[(finish, key)], got, want))
    bpy.data.images.remove(img)
    print(f'iteration {it}: worst channel error {255*worst:.1f}/255')

out = {'paint': {}, 'metal': {}}
for (finish, key), b in base.items():
    out[finish][key] = [round(c, 5) for c in b]
Path(args.out).write_text(json.dumps(out, indent=1)+'\n', encoding='utf-8')
print('wrote', args.out)
