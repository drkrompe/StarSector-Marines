"""Check whether each quadrant of a generated swatch sheet tiles seamlessly.

  python swatch_check.py generated/swatches.png

Splits the sheet into its 2x2 tiles. For each, the wrap seam (last column next
to first column, last row next to first row) is compared with the ordinary
difference between neighbouring columns/rows inside the tile: a ratio near 1
means repeating the tile leaves no visible seam. Writes review/swatches.json and
build/review/swatches-tiled.png (each tile repeated 2x2, so wrap seams sit mid-cell).
"""
from pathlib import Path
import json
import sys
import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sheet = Image.open(sys.argv[1]).convert('RGB')
W, H = sheet.size
names = ['cream', 'oxide-orange', 'gunmetal', 'soot-steel']
report, cells = {}, []
for k, name in enumerate(names):
    x0, y0 = (k % 2) * W // 2, (k // 2) * H // 2
    tile = np.asarray(sheet.crop((x0 + 2, y0 + 2, x0 + W // 2 - 2, y0 + H // 2 - 2)), np.float32)
    inner_x = np.abs(np.diff(tile, axis=1)).mean()
    inner_y = np.abs(np.diff(tile, axis=0)).mean()
    wrap_x = np.abs(tile[:, -1] - tile[:, 0]).mean()
    wrap_y = np.abs(tile[-1] - tile[0]).mean()
    # Large-scale drift: mean colour of the left vs right quarter, top vs bottom.
    q = tile.shape[0] // 4
    drift_x = float(np.abs(tile[:, :q].mean((0, 1)) - tile[:, -q:].mean((0, 1))).mean())
    drift_y = float(np.abs(tile[:q].mean((0, 1)) - tile[-q:].mean((0, 1))).mean())
    report[name] = {'wrap_seam_ratio_x': float(wrap_x / inner_x), 'wrap_seam_ratio_y': float(wrap_y / inner_y),
                    'edge_quarter_mean_diff_x_0_255': drift_x, 'edge_quarter_mean_diff_y_0_255': drift_y,
                    'mean_srgb': [float(v) for v in tile.mean((0, 1))]}
    t = Image.fromarray(tile.astype(np.uint8))
    rep = Image.new('RGB', (t.width * 2, t.height * 2))
    for i in range(2):
        for j in range(2):
            rep.paste(t, (i * t.width, j * t.height))
    cells.append(rep.resize((t.width, t.height), Image.LANCZOS))
out = Image.new('RGB', (cells[0].width * 2, cells[0].height * 2))
for k, c in enumerate(cells):
    out.paste(c, ((k % 2) * c.width, (k // 2) * c.height))
(HERE / 'build' / 'review').mkdir(parents=True, exist_ok=True)
out.save(HERE / 'build' / 'review' / 'swatches-tiled.png')
report['method'] = 'ratio = mean |last - first| column (row) / mean |neighbour difference| inside the tile; tile inset 2px from the quadrant edge'
(HERE / 'review' / 'swatches.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=1))
