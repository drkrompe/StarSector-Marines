"""Writes the fort-emplacement authoring document: nine mechanical full-frame cuts of the retained raw patch plus three
platform variants. Cut rectangles were measured from the raw (patch bbox x22-563, y11-555; variants x768-1415)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NOTE = ("Fort emplacement: a snappable poured-concrete gun-platform revetment. Built-in ImageGen reskin of "
        "templates/fort-emplacement-guide.png (fort-emplacement-guide.py); exact prompt retained beside this file "
        "(fort-emplacement.prompt.txt), raw retained unmodified. 1536x1024 RGBA. ImageGen did not hold the 192px "
        "pitch: the nine patch frames are one contiguous 3x3 picture cut at 181px squares from (22,11), and the "
        "three platform variants are 212px squares - the document's rects are authoritative, not the 8x4 grid. "
        "Block fort.emplacement (wall-3x3, slots read 'the exterior is on this side'): raised parapet along the "
        "exterior, flat platform inside; the centre slot is platform. Platform variants are doodads. Presentation "
        "art only - cover and topology belong to the post/pad that stamps it. New sheet, shares no id with a "
        "shipped tileset.")
slots = {'nw': (0, 0), 'n': (1, 0), 'ne': (2, 0), 'w': (0, 1), 'center': (1, 1), 'e': (2, 1),
         'sw': (0, 2), 's': (1, 2), 'se': (2, 2)}
entries = []
for slot, (c, r) in slots.items():
    entries.append({'id': 'fort.emplacement.' + slot, 'block': 'fort.emplacement', 'slot': slot, 'included': True,
                    'cover': 'none', 'footprintCells': [1, 1], 'rect': [22 + c * 181, 11 + r * 181, 181, 181]})
# The same four edge cuts again as doodads (CoverPropDamage family ids), which is what the stamper places.
# Cut windows slid along the band (away from the model's drawn slab-joint lines at raw x=195/394, y=213/425) and
# trimmed across the e/w pieces so no joint falls inside; the band's depth stays about half the cell on every side.
EDGE_RECTS = {'n': (205, 11, 181, 181), 's': (205, 373, 181, 181),
              'w': (22, 224, 169, 181), 'e': (397, 224, 168, 181)}
for slot in ('n', 'e', 's', 'w'):
    entries.append({'id': 'doodad.fort-emplacement-' + slot, 'included': True, 'cover': 'heavy',
                    'ballisticHalfHeight': 0.5, 'footprintCells': [1, 1],
                    'rect': list(EDGE_RECTS[slot]), 'note': 'Parapet edge piece, exterior ' + slot + '.'})
for i, (x, note) in enumerate(((770, 'Plain slab with hairline cracks.'),
                               (985, 'Four tie-down plates and a central drain.'),
                               (1200, 'Worn square bearing recess.'))):
    entries.append({'id': 'doodad.fort-emplacement-platform-%d' % i, 'included': True, 'cover': 'none',
                    'footprintCells': [1, 1], 'rect': [x, 10, 212, 212],
                    'note': 'Flat gun-platform cell. ' + note})
doc = {'sheetName': 'fort-emplacement', 'sheet': 'art-source/tilesets/fort-emplacement.raw.png', 'cellPx': 128,
       'alphaMin': 16, 'idPrefix': 'doodad.fort-emplacement', 'gridCols': 8, 'gridRows': 4, 'note': NOTE,
       'blocks': [{'id': 'fort.emplacement', 'layout': 'wall-3x3', 'fillRgb': '0x969690'}], 'entries': entries}
out = {'name': 'fort-emplacement', 'document': doc}
(ROOT / 'art-source/tilesets/.fort-emplacement-call.json').write_text(json.dumps(out, indent=1))
