"""Writes the fort-emplacement-damage authoring document: two 3x3 patches of mechanical full-frame cuts of the retained
raw (left patch bbox x19-574, y11-577; right patch x768-1347, y11-577). Slots and order match fort.emplacement exactly,
so a destroyed or damaged frame swaps in place for its intact counterpart."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NOTE = ("Damage states of fort.emplacement. Built-in ImageGen reskin of templates/fort-emplacement-damage-guide.png "
        "(fort-emplacement-damage-guide.py, with the intact patch as material reference); exact prompt retained "
        "(fort-emplacement-damage.prompt.txt), raw retained unmodified, 1536x1024 RGBA. ImageGen did not hold the "
        "192px pitch, so the rects here are measured and authoritative. Blocks fort.emplacement-damaged (left patch) "
        "and fort.emplacement-destroyed (right patch) use the intact block's wall-3x3 slots. They are swapped in by "
        "code (EmplacementWreck), not by CoverPropDamage: concrete parapets carry no wall health. New sheet, shares no "
        "id with a shipped tileset.")
slots = {'nw': (0, 0), 'n': (1, 0), 'ne': (2, 0), 'w': (0, 1), 'center': (1, 1), 'e': (2, 1),
         'sw': (0, 2), 's': (1, 2), 'se': (2, 2)}
patches = (('fort.emplacement-damaged', 19, 185), ('fort.emplacement-destroyed', 768, 193))
entries = []
for block, x0, w in patches:
    for slot, (c, r) in slots.items():
        entries.append({'id': block + '.' + slot, 'block': block, 'slot': slot, 'included': True, 'cover': 'none',
                        'footprintCells': [1, 1], 'rect': [x0 + c * w, 11 + r * 189, w, 189]})
doc = {'sheetName': 'fort-emplacement-damage', 'sheet': 'art-source/tilesets/fort-emplacement-damage.raw.png',
       'cellPx': 128, 'alphaMin': 16, 'idPrefix': 'doodad.fort-emplacement-damage', 'gridCols': 8, 'gridRows': 4,
       'note': NOTE,
       'blocks': [{'id': b, 'layout': 'wall-3x3', 'fillRgb': '0x969690'} for b, _, _ in patches], 'entries': entries}
(ROOT / 'art-source/tilesets/.fort-emplacement-damage-call.json').write_text(
    json.dumps({'name': 'fort-emplacement-damage', 'document': doc}, indent=1))
