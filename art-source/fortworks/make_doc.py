"""Cut one painted look (`generate.py`) into its style's fortification works and export them (`fort-works.md`).

  python art-source/fortworks/make_doc.py <look> <style>     e.g. derelict-colony-steel derelict

Writes the authoring document of sheet `fortworks-<style>` (cuts measured from the raw, never assumed: ImageGen moves
and rescales a little) and exports it through `tools/authoring.sh`, which writes the atlas and
mod/data/tilesets/fortworks-<style>.tileset.json. Every id is a look, `<base id>@<style>`, of a piece the generators
already lay; a look carries art only, so its cover fields are copied from the base for the catalog card and read by
nothing (`FortWorks.dress`).
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import make_guide as guide  # noqa: E402

ALPHA = 128
# Pixels cut off each side of a painted wall tile (about 4% of it).
WALL_INSET = 4

# The collar plate: shape -> plate cell, as `make-fort-emplacement-doc.py` cuts it (the cell north of the body wears
# the piece whose parapet is on its south edge).
PARAPET = {'s': (2, 0), 'n': (2, 4), 'e': (0, 2), 'w': (4, 2),
           'inner-se': (0, 0), 'inner-sw': (4, 0), 'inner-ne': (0, 4), 'inner-nw': (4, 4)}
# The barricade ring: shape -> ring cell. The band lies on the named edges.
RING = {'n': (1, 0), 's': (1, 2), 'w': (0, 1), 'e': (2, 1),
        'corner-nw': (0, 0), 'corner-ne': (2, 0), 'corner-sw': (0, 2), 'corner-se': (2, 2)}
# The barricade families a ring piece dresses, as id templates over the ring shape.
BARRICADES = [('doodad.fort-sandbag-%s', lambda shape: shape, 'heavy', 0.4),
              ('doodad.sandbag-%s', lambda shape: shape if shape.startswith('corner') else 'straight-' + shape,
               'heavy', 0.3)]
WALL = 'installation.dark-concrete-wall'


def opaque_box(alpha, x0, y0, x1, y1):
    """Bounding box [x0, y0, x1, y1) of the opaque pixels inside the region."""
    region = alpha[y0:y1, x0:x1] >= ALPHA
    ys, xs = np.nonzero(region)
    return [int(x0 + xs.min()), int(y0 + ys.min()), int(x0 + xs.max() + 1), int(y0 + ys.max() + 1)]


def runs(alpha, x0, y0, x1, y1):
    """The four column spans and four row spans of the wall tiles' 4x4 grid inside the region, from the transparent
    gutters between them: each span is the (start, end) of a run of columns or rows that hold opaque pixels."""
    region = alpha[y0:y1, x0:x1] >= ALPHA
    def spans(profile, origin):
        found, start = [], None
        for i, filled in enumerate(list(profile) + [False]):
            if filled and start is None:
                start = i
            elif not filled and start is not None:
                if i - start > 20:
                    found.append((origin + start, origin + i))
                start = None
        if len(found) != 4:
            raise SystemExit('expected 4 wall tile spans, found %s' % found)
        return found
    return spans(region.sum(axis=0) > 20, x0), spans(region.sum(axis=1) > 20, y0)


def grid(box, n):
    """The box split into n x n cells: cell (c, r) -> [x, y, w, h]."""
    xs = [round(box[0] + (box[2] - box[0]) * i / n) for i in range(n + 1)]
    ys = [round(box[1] + (box[3] - box[1]) * i / n) for i in range(n + 1)]
    return lambda c, r: [xs[c], ys[r], xs[c + 1] - xs[c], ys[r + 1] - ys[r]]


def document(look, style, raw):
    alpha = np.array(Image.open(raw).convert('RGBA'))[:, :, 3]
    plate = grid(opaque_box(alpha, 0, 0, 976, 1024), 5)
    ring = grid(opaque_box(alpha, 976, 0, 1536, 600), 3)
    entries = []
    for shape, (c, r) in PARAPET.items():
        entries.append({'id': 'doodad.fort-emplacement-%s@%s' % (shape, style), 'included': True, 'cover': 'heavy',
                        'ballisticHalfHeight': 0.5, 'footprintCells': [1, 1], 'rect': plate(c, r),
                        'note': 'Parapet collar piece %s, %s look.' % (shape, style)})
    for shape, (c, r) in RING.items():
        for template, name, cover, half in BARRICADES:
            entries.append({'id': (template % name(shape)) + '@' + style, 'included': True, 'cover': cover,
                            'ballisticHalfHeight': half, 'footprintCells': [1, 1], 'rect': ring(c, r),
                            'note': 'Barricade piece %s, %s look.' % (shape, style)})
    cols, rows = runs(alpha, 976, 600, 1536, 1024)
    for mask in range(16):
        (x0, x1), (y0, y1) = cols[mask % 4], rows[mask // 4]
        # ImageGen outlines each separate tile; cut inside it, or every joint between wall cells draws a dark seam.
        box = [x0 + WALL_INSET, y0 + WALL_INSET, x1 - WALL_INSET, y1 - WALL_INSET]
        entries.append({'id': '%s@%s.faces-%d' % (WALL, style, mask), 'block': '%s@%s' % (WALL, style),
                        'slot': 'faces-%d' % mask, 'included': True, 'cover': 'none', 'footprintCells': [1, 1],
                        'rect': [box[0], box[1], box[2] - box[0], box[3] - box[1]]})
    note = ("Fortification works of the %s emplacement style, look '%s': every piece a lot's walls, parapets and "
            "barricades are drawn from, painted in ONE ImageGen pass over art-source/fortworks/guides/works-guide.png "
            "(make_guide.py) with the style's turret plinth as material reference; prompt "
            "art-source/fortworks/prompts/%s.prompt.txt, raw retained unmodified. Cuts are measured from the raw "
            "(make_doc.py). Every id is a look <base id>@%s of a piece the generators lay: FortWorks dresses a piece on "
            "a lot built in this style with it, art only, so cover and blocking stay the base piece's "
            "(fort-works.md). One ring piece serves both barricade families.") % (style, look, look, style)
    return {'sheetName': 'fortworks-' + style, 'sheet': str(raw.relative_to(ROOT)).replace('\\', '/'), 'cellPx': 128,
            'alphaMin': 16, 'idPrefix': 'doodad', 'gridCols': 12, 'gridRows': 8, 'note': note,
            'blocks': [{'id': '%s@%s' % (WALL, style), 'layout': 'wall-faces-4x4',
                        'windowBlock': 'installation.dark-concrete-window',
                        'doorBlock': 'installation.dark-concrete-door',
                        'rubbleBlock': 'installation.dark-concrete-rubble'}],
            'entries': entries}


def authoring(tool, args):
    shell = ['bash', str(ROOT / 'tools/authoring.sh')]
    result = subprocess.run(shell + [tool, '-'], input=json.dumps(args), text=True, cwd=ROOT,
                            capture_output=True)
    print(result.stdout.strip()[:600])
    if result.returncode != 0:
        print(result.stderr[-2000:])
        sys.exit(result.returncode)


def main():
    look, style = sys.argv[1], sys.argv[2]
    raw = HERE / 'raw' / (look + '.raw.png')
    name = 'fortworks-' + style
    authoring('tileset_write_document', {'name': name, 'document': document(look, style, raw)})
    authoring('tileset_export', {'name': name})


if __name__ == '__main__':
    main()
