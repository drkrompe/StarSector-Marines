"""Top-view silhouette IoU against the sprite, measured from the geometry: no render.

Chain it after build_fighter.py in the same Blender process (or open a built .blend):
  blender -b --factory-startup --python build_fighter.py --python top_silhouette.py -- --fighter ID --views none
  blender -b <id>/<id>-geometry.blend --python top_silhouette.py -- --fighter ID
Every evaluated mesh triangle of "<NAME> | geometry" is projected onto the XY plane and filled on a
grid aligned with the sprite's pixels (K samples per pixel), the sprite's alpha > 127 is the other
mask, and the IoU, the share of the sprite not covered and the share of the model outside it are
written to <id>/geometry.json under 'silhouette' (method 'geometry'). <id>/top-overlay.png
(ignored output) shows the sprite at half strength over the model mask, red where the sprite is
not covered and cyan where the model is outside it; nose up, port left, as the sprite is drawn.
compose_fighter.py does the same from a transparent top render; this one needs no renderer, so it
can run in a session that may not render.
"""
from pathlib import Path
import json
import sys
import bpy
import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[1]))
from starsector_path import GAME_CORE as GAME  # noqa: E402

argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
fid = argv[argv.index('--fighter')+1]
out_dir = Path(argv[argv.index('--out')+1]) if '--out' in argv else HERE/fid
K = 16
path = out_dir/'geometry.json'
man = json.loads(path.read_text(encoding='utf-8'))
name = man['hull']['name']
SW, SH = man['sprite']['size']
CX, CY = man['sprite']['center']

img = bpy.data.images.load(str(GAME/man['hull']['source']['sprite']))
w, h = img.size
px = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1]      # row 0 at the top
sprite = np.kron(px[..., 3] > .5, np.ones((K, K), bool))
H, W = sprite.shape

coll = bpy.data.collections[f'{name.upper()} | geometry']
dg = bpy.context.evaluated_depsgraph_get()
model = np.zeros((H, W), bool)
ii, jj = np.mgrid[0:H, 0:W]
Xc = (SH-CY)-(ii+.5)/K        # row i -> X (nose up)
Yc = CX-(jj+.5)/K             # col j -> Y (port left)
for ob in coll.all_objects:
    if ob.type != 'MESH' or ob.hide_render:
        continue
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    me.calc_loop_triangles()
    mw = ev.matrix_world
    co = np.array([(mw @ v.co)[:2] for v in me.vertices])
    tris = np.array([t.vertices[:] for t in me.loop_triangles], dtype=np.int64)
    ev.to_mesh_clear()
    if not len(tris):
        continue
    for a, b, c in co[tris]:
        x0, x1 = min(a[0], b[0], c[0]), max(a[0], b[0], c[0])
        y0, y1 = min(a[1], b[1], c[1]), max(a[1], b[1], c[1])
        r0, r1 = int(max(0, np.floor(((SH-CY)-x1)*K))), int(min(H, np.ceil(((SH-CY)-x0)*K)+1))
        c0, c1 = int(max(0, np.floor((CX-y1)*K))), int(min(W, np.ceil((CX-y0)*K)+1))
        if r1 <= r0 or c1 <= c0:
            continue
        X, Y = Xc[r0:r1, c0:c1], Yc[r0:r1, c0:c1]
        d = (b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(d) < 1e-12:
            continue
        l0 = ((b[1]-c[1])*(X-c[0])+(c[0]-b[0])*(Y-c[1]))/d
        l1 = ((c[1]-a[1])*(X-c[0])+(a[0]-c[0])*(Y-c[1]))/d
        model[r0:r1, c0:c1] |= (l0 >= 0) & (l1 >= 0) & (l0+l1 <= 1)

iou = float((sprite & model).sum()/(sprite | model).sum())
missing = float((sprite & ~model).sum()/sprite.sum())
extra = float((model & ~sprite).sum()/sprite.sum())
man['silhouette'] = {'iou': round(iou, 4), 'sprite_not_covered': round(missing, 4), 'model_outside_sprite': round(extra, 4),
                     'method': f'geometry: evaluated triangles projected on XY, {K} samples per sprite pixel, '
                               'sprite alpha > 0.5; no render (top_silhouette.py)'}
path.write_text(json.dumps(man, indent=2)+'\n', encoding='utf-8')

rgb = np.full((H, W, 3), .87, np.float32)
rgb[model] = (.45, .47, .5)
spr_rgb = np.kron(px[..., :3], np.ones((K, K, 1)))
rgb[sprite] = rgb[sprite]*.5+spr_rgb[sprite]*.5
rgb[sprite & ~model] = (.86, .12, .12)
rgb[model & ~sprite] = (.08, .74, .86)
o = bpy.data.images.new('top overlay', W, H, alpha=True)
o.pixels[:] = np.dstack([rgb, np.ones((H, W, 1), np.float32)])[::-1].ravel().tolist()
o.filepath_raw = str(out_dir/'top-overlay.png')
o.file_format = 'PNG'
o.save()
cov = model.reshape(h, K, w, K).mean(axis=(1, 3))
alpha = px[..., 3] > .5
grid = '\n'.join(''.join(('#' if c > .75 else ('+' if c > .25 else '.')) if a else ('o' if c > .25 else ' ')
                         for a, c in zip(ra, rc)) for ra, rc in zip(alpha, cov))
print('SILHOUETTE', name, json.dumps(man['silhouette']))
print('COVERAGE (sprite px: # covered, + partly, . missed; o model outside)\n'+grid)
