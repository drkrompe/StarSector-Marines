"""Writes the fort-emplacement-corners authoring document: twelve 192px full-frame cuts (columns nw ne sw se, the corner
the parapet notch sits in; rows intact, damaged, destroyed) of the retained raw's contiguous 4x3 block at the origin.
Ids follow the CoverPropDamage family convention: doodad.fort-emplacement-<shape>, -damaged-<shape>, -destroyed-<shape>."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NOTE = ("Inner-corner pieces of the tight fort.emplacement collar in three states. Built-in ImageGen reskin of "
        "templates/fort-emplacement-corners-guide.png (fort-emplacement-corners-guide.py, intact patch as material "
        "reference); exact prompt retained (fort-emplacement-corners.prompt.txt), raw retained unmodified, 1536x1024 "
        "RGBA with the 4x3 block held on the 192px grid. Each frame is platform concrete with one parapet notch in the "
        "named corner: the piece at a diagonal ring cell, joining the two edge pieces that meet at the body's corner. "
        "Doodads, not a block: no GridLayout resolves a diagonal. The ids are the CoverPropDamage family, so rounds wear "
        "them intact -> damaged -> destroyed. New sheet, shares no id with a shipped tileset.")
entries = []
for row, state in enumerate(('', 'damaged-', 'destroyed-')):
    for col, corner in enumerate(('nw', 'ne', 'sw', 'se')):
        entries.append({'id': 'doodad.fort-emplacement-%sinner-%s' % (state, corner), 'included': True,
                        'cover': 'none' if state == 'destroyed-' else 'heavy', 'ballisticHalfHeight': 0.1 if state == 'destroyed-' else 0.5, 'footprintCells': [1, 1],
                        'rect': [col * 192, row * 192, 192, 192],
                        'note': 'Parapet notch in the %s corner (%s).' % (corner, state.strip('-') or 'intact')})
doc = {'sheetName': 'fort-emplacement-corners', 'sheet': 'art-source/tilesets/fort-emplacement-corners.raw.png',
       'cellPx': 128, 'alphaMin': 16, 'idPrefix': 'doodad.fort-emplacement-corners', 'gridCols': 4, 'gridRows': 3,
       'note': NOTE, 'blocks': [], 'entries': entries}
(ROOT / 'art-source/tilesets/.fort-emplacement-corners-call.json').write_text(
    json.dumps({'name': 'fort-emplacement-corners', 'document': doc}, indent=1))
