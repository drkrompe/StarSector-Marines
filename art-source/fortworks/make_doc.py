"""Cut one painted look (`generate.py`) into its style's fortification works and export them (`fort-works.md`).

  python art-source/fortworks/make_doc.py <look> <style> [--state damaged|destroyed]
                                                    e.g. derelict-colony-steel derelict

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
import trim  # noqa: E402

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
# The wall surfaces a lot's walls wear the look on, each with the openings and rubble of its own kind: the fort's
# perimeter, and the walls of the buildings inside it, so a fort's rooms are built of the same stuff as its wall.
WALLS = [(WALL, 'dark'), ('installation.light-concrete-wall', 'light')]


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


def document(look, style, raw, state=None):
    """The authoring document of one look, or of its damaged or destroyed state when `state` is given: a state is cut
    with the intact raw's measurements (its edit kept the geometry, and a destroyed piece's own outline is partly
    gone), carries the damage family's ids and has no wall tiles, since a broken wall cell is rubble."""
    intact = HERE / 'raw' / (look + '.raw.png')
    alpha = np.array(Image.open(intact).convert('RGBA'))[:, :, 3]
    plate = grid(opaque_box(alpha, 0, 0, 976, 1024), 5)
    ring = grid(opaque_box(alpha, 976, 0, 1536, 600), 3)
    prefix = '' if state is None else state + '-'
    gone = state == 'destroyed'
    entries = []
    for shape, (c, r) in PARAPET.items():
        entries.append({'id': 'doodad.fort-emplacement-%s%s@%s' % (prefix, shape, style), 'included': True,
                        'cover': 'none' if gone else 'heavy', 'ballisticHalfHeight': 0.1 if gone else 0.5,
                        'footprintCells': [1, 1], 'rect': plate(c, r),
                        'note': 'Parapet collar piece %s, %s look%s.' % (shape, style, '' if state is None else ', ' + state)})
    for shape, (c, r) in RING.items():
        for template, name, cover, half in BARRICADES:
            entries.append({'id': (template % (prefix + name(shape))) + '@' + style, 'included': True,
                            'cover': 'none' if gone else cover, 'ballisticHalfHeight': 0.0 if gone else half,
                            'footprintCells': [1, 1], 'rect': ring(c, r),
                            'note': 'Barricade piece %s, %s look%s.' % (shape, style, '' if state is None else ', ' + state)})
    blocks = []
    if state is None:
        cols, rows = runs(alpha, 976, 600, 1536, 1024)
        for wall, kind in WALLS:
            for mask in range(16):
                (x0, x1), (y0, y1) = cols[mask % 4], rows[mask // 4]
                # ImageGen outlines each separate tile; cut inside it, or every joint between wall cells draws a seam.
                box = [x0 + WALL_INSET, y0 + WALL_INSET, x1 - WALL_INSET, y1 - WALL_INSET]
                entries.append({'id': '%s@%s.faces-%d' % (wall, style, mask), 'block': '%s@%s' % (wall, style),
                                'slot': 'faces-%d' % mask, 'included': True, 'cover': 'none', 'footprintCells': [1, 1],
                                'rect': [box[0], box[1], box[2] - box[0], box[3] - box[1]]})
            blocks.append({'id': '%s@%s' % (wall, style), 'layout': 'wall-faces-4x4',
                           'windowBlock': 'installation.%s-concrete-window' % kind,
                           'doorBlock': 'installation.%s-concrete-door' % kind,
                           'rubbleBlock': 'installation.%s-concrete-rubble' % kind})
    name = look if state is None else look + '-' + state
    note = ("Fortification works of the %s emplacement style%s, look '%s': every piece a lot's walls, parapets and "
            "barricades are drawn from, painted in ONE ImageGen pass over art-source/fortworks/guides/works-guide.png "
            "(make_guide.py) with the style's turret plinth as material reference%s; prompt "
            "art-source/fortworks/prompts/%s.prompt.txt, raw retained unmodified. Cuts are measured from the intact raw "
            "(make_doc.py). Every id is a look <base id>@%s of a piece the generators lay: FortWorks dresses a piece on "
            "a lot built in this style with it, art only, so cover and blocking stay the base piece's "
            "(fort-works.md). One ring piece serves both barricade families.") % (
        style, '' if state is None else ', ' + state + ' state', look,
        '' if state is None else ', then edited into its ' + state + ' state with the intact raw as edit target',
        name, style)
    return {'sheetName': 'fortworks-' + style + ('' if state is None else '-' + state),
            'sheet': str(raw.relative_to(ROOT)).replace('\\', '/'), 'cellPx': 128,
            'alphaMin': 16, 'idPrefix': 'doodad', 'gridCols': 12, 'gridRows': 8, 'note': note,
            'blocks': blocks, 'entries': entries}


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
    state = sys.argv[sys.argv.index('--state') + 1] if '--state' in sys.argv else None
    raw = HERE / 'raw' / (look + ('' if state is None else '-' + state) + '.raw.png')
    name = 'fortworks-' + style + ('' if state is None else '-' + state)
    doc = document(look, style, raw, state)
    if style not in trim.STYLES:
        authoring('tileset_write_document', {'name': name, 'document': doc})
        authoring('tileset_export', {'name': name})
        return
    # The style's grade (trim.py) goes on a copy of the master under build/, which the atlas is cut from; the document
    # then names the master again, since the copy is regenerated and never tracked.
    doc['note'] += (' Graded by art-source/fortworks/trim.py at export: export through make_doc.py, never'
                    ' tileset_export alone, or the atlas ships ungraded.')
    graded = ROOT / 'build' / 'fortworks-graded' / raw.name
    graded.parent.mkdir(parents=True, exist_ok=True)
    trim.graded(Image.open(raw), style).save(graded)
    authoring('tileset_write_document', {'name': name, 'document': dict(doc, sheet=graded.relative_to(ROOT).as_posix())})
    authoring('tileset_export', {'name': name})
    authoring('tileset_write_document', {'name': name, 'document': doc})


if __name__ == '__main__':
    main()
