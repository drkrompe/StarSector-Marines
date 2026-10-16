"""Writes the authoring document of one fort-emplacement state (intact, damaged, destroyed): eight doodad cuts of the
retained raw's one-plate collar. Usage: make-fort-emplacement-doc.py <intact|damaged|destroyed>.

All three raws share the intact plate's layout (the damaged and destroyed passes edit the intact raw), so one set of
measured cell rectangles serves them. The plate is the 5x5 collar of a 3x3 body, 173.6 x 168 px per cell, measured from
the intact raw (solid bbox x42-909, y90-929). Edge pieces are cut from the middle cell of each side, inner corners
from the ring corners. Ids are the CoverPropDamage family: doodad.fort-emplacement-[damaged-|destroyed-]<shape>."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATE = sys.argv[1]
X = [48, 216, 389, 563, 737, 904]   # cell column boundaries; outer edges inset 6px off the plate's soft rim (alpha < 255)
Y = [96, 258, 426, 594, 762, 924]


def cell(c, r):
    return [X[c], Y[r], X[c + 1] - X[c], Y[r + 1] - Y[r]]


# shape -> plate cell. The cell north of the body wears the piece whose parapet is on its south edge, and so on.
SHAPES = {'s': (2, 0), 'n': (2, 4), 'e': (0, 2), 'w': (4, 2),
          'inner-se': (0, 0), 'inner-sw': (4, 0), 'inner-ne': (0, 4), 'inner-nw': (4, 4)}
PREFIX = {'intact': '', 'damaged': 'damaged-', 'destroyed': 'destroyed-'}[STATE]
SHEET = 'fort-emplacement' + ('' if STATE == 'intact' else '-' + STATE)
NOTE = ("Fort emplacement collar, %s state: the pieces a tight concrete parapet ring is built from (four swapped "
        "edge pieces and four inner corners), all cut from ONE contiguous 5x5 plate painted in a single ImageGen pass "
        "from templates/fort-emplacement-guide.png (fort-emplacement-guide.py) with the fort's own concrete as "
        "material reference (templates/fort-material-reference.png); exact prompt retained "
        "(%s.prompt.txt), raw retained unmodified. ImageGen painted the plate 868x840 rather than 960x960, so the "
        "rects are measured and authoritative. Doodads, not a block: the collar is stamped swapped (the north ring "
        "cell wears the south-edge piece) and no GridLayout resolves that. The ids are the CoverPropDamage family, so "
        "rounds wear each piece intact -> damaged -> destroyed. Presentation art only - cover and topology belong to "
        "the post or pad that stamps it.") % (STATE, SHEET)
entries = []
for shape, (c, r) in SHAPES.items():
    destroyed = STATE == 'destroyed'
    entries.append({'id': 'doodad.fort-emplacement-%s%s' % (PREFIX, shape), 'included': True,
                    'cover': 'none' if destroyed else 'heavy', 'ballisticHalfHeight': 0.1 if destroyed else 0.5,
                    'footprintCells': [1, 1], 'rect': cell(c, r),
                    'note': 'Collar piece %s (%s).' % (shape, STATE)})
doc = {'sheetName': SHEET, 'sheet': 'art-source/tilesets/%s.raw.png' % SHEET, 'cellPx': 128, 'alphaMin': 16,
       'idPrefix': 'doodad.fort-emplacement', 'gridCols': 8, 'gridRows': 4, 'note': NOTE, 'blocks': [],
       'entries': entries}
(ROOT / ('art-source/tilesets/.%s-call.json' % SHEET)).write_text(json.dumps({'name': SHEET, 'document': doc}, indent=1))
