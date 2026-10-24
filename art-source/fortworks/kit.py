"""The rest of a style's kit (`fort-works.md`): every piece a lot still drew in the shared base art once its walls,
parapets and barricades had looks of their own. That is the openings (window frames and glass, open doorways), wall
rubble, the trench, the hedgehog, the fence and the light post's vent ring, all on ONE 1536x1024 sheet so a style's kit
is painted in one pass, as the works are.

  python art-source/fortworks/kit.py guide                  writes guides/kit-guide.png
  python art-source/fortworks/kit.py export <look> <style>  cuts raw/kit-<look>.raw.png into tileset fortworks-<style>-kit
  python art-source/fortworks/kit.py export <look> <style> <variant>   an owner option's draft: the window slots of
                                                            raw/kit-<look>-<variant>.raw.png over the look's own raw
                                                            (re-export without it to restore)

The guide is the base art itself, drawn grey: each piece at its slot, so the painted look keeps the base piece's
geometry (the channel of a trench meets a cell edge where the base one does, a window sits on the edge it does). The
exporter registers the painted raw against the guide (ImageGen moves and rescales a little), cuts each slot, and writes
the sheet through the authoring tools. A look carries art only: its cover fields are copied from the base for the
catalog card and read by nothing.

Every look is `<base id>@<style>`. The openings and rubble are grid blocks the renderer finds through the lot's wall
(`FortWorks.styledBlock`); one painting serves both the dark and the light wall, as a style's wall tiles do.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import trim  # noqa: E402

W, H = 1536, 1024
PITCH = 160
BODY = 144
ORIGIN = ((W - 8 * PITCH) // 2, 32)
# Pixels cut off each side of a painted slot, inside any outline ImageGen draws round a piece.
INSET = 3
CELL_PX = 128

TILESETS = ROOT / 'art-source/tilesets'
FACINGS = ['n', 'e', 's', 'w']
FENCE = ['straight-h', 'straight-v', 'corner-nw', 'corner-ne', 'corner-sw', 'corner-se']
VENT = 'doodad.light-post-vent'


def _authored(sheet, entry_id):
    doc = json.loads((TILESETS / (sheet + '.tileset-authoring.json')).read_text(encoding='utf-8'))
    entry = next(e for e in doc['entries'] if e['id'] == entry_id)
    return ROOT / doc['sheet'], entry


def _cell_of(sheet_path, col, row, px):
    return sheet_path, {'rect': [col * px, row * px, px, px]}


def slots():
    """Every slot of the kit: (col, row, source, look entry fields). `source` is (sheet path, rect) of the base art the
    slot is drawn from; the look entry names the look's id, or the block and slot it fills."""
    out = []
    for i, facing in enumerate(FACINGS):
        c, r = i % 2, i // 2
        for kind, dc in (('window', 0), ('door', 2)):
            sheet, entry = _authored('concrete-openings', 'installation.light-concrete-%s.facing-%s' % (kind, facing))
            out.append((dc + c, r, (sheet, entry['rect']),
                        {'kind': kind, 'slot': 'facing-' + facing, 'cover': 'none'}))
    for m in range(16):
        sheet, entry = _authored('concrete-rubble', 'installation.light-concrete-rubble.faces-%d' % m)
        out.append((m % 4, 2 + m // 4, (sheet, entry['rect']), {'kind': 'rubble', 'slot': 'faces-%d' % m,
                                                                 'cover': 'none'}))
    for m in range(16):
        sheet, entry = _authored('fort-earthworks', 'doodad.fort-trench-%d' % m)
        out.append((4 + m % 4, m // 4, (sheet, entry['rect']), _doodad(entry)))
    sheet, entry = _authored('fort-earthworks', 'doodad.fort-hedgehog')
    out.append((4, 4, (sheet, entry['rect']), _doodad(entry)))
    out.append((5, 4, (ROOT / 'mod/graphics/tilesets/urban-tileset-2.png', [11 * 32, 2 * 32, 32, 32]),
                {'id': VENT, 'cover': 'heavy', 'ballisticHalfHeight': 0.5,
                 'note': 'Light post vent ring cell (the road-sheet vent frame).'}))
    doodads = json.loads((ROOT / 'mod/data/tilesets/doodads.tileset.json').read_text(encoding='utf-8'))
    for i, piece in enumerate(FENCE):
        d = next(e for e in doodads['doodads'] if e['id'] == 'doodad.industrial-fence-' + piece)
        px = doodads['cellPx']
        out.append((6 + i % 2 if i < 2 else 4 + (i - 2), 4 if i < 2 else 5,
                    (ROOT / 'mod' / doodads['sheet'], [d['col'] * px, d['row'] * px, px, px]),
                    {'id': d['id'], 'cover': d['cover'], 'ballisticHalfHeight': d['ballisticHalfHeight']}))
    return out


def _doodad(entry):
    fields = {k: entry[k] for k in ('id', 'cover', 'ballisticHalfHeight', 'fightingPosition', 'note') if k in entry}
    return fields


def slot_box(col, row):
    """The slot's body [x0, y0, x1, y1) on the guide."""
    x = ORIGIN[0] + col * PITCH + (PITCH - BODY) // 2
    y = ORIGIN[1] + row * PITCH + (PITCH - BODY) // 2
    return [x, y, x + BODY, y + BODY]


def guide():
    sheet = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    cache = {}
    for col, row, (path, rect), _ in slots():
        if path not in cache:
            cache[path] = Image.open(path).convert('RGBA')
        x, y, w, h = rect
        piece = cache[path].crop((x, y, x + w, y + h))
        piece = piece.resize((BODY, BODY), Image.BICUBIC if w <= 32 else Image.LANCZOS)
        a = np.asarray(piece).astype(np.float64)
        grey = a[..., :3] @ np.array([0.299, 0.587, 0.114])
        a[..., 0] = a[..., 1] = a[..., 2] = grey
        piece = Image.fromarray(a.clip(0, 255).astype(np.uint8), 'RGBA')
        box = slot_box(col, row)
        sheet.alpha_composite(piece, (box[0], box[1]))
    return sheet


def register(raw_alpha, guide_alpha):
    """The scale and offset that best lay the guide's pieces over the painted raw's: raw = guide * s + (dx, dy)."""
    def small(alpha, f):
        return np.asarray(Image.fromarray(((alpha >= 128) * 255).astype(np.uint8)).resize(
            (alpha.shape[1] // f, alpha.shape[0] // f), Image.BILINEAR)).astype(np.float64) / 255.0
    f = 4
    g = small(guide_alpha, f)
    r = small(raw_alpha, f)
    best = None
    for s in np.linspace(0.96, 1.04, 17):
        gw, gh = int(round(g.shape[1] * s)), int(round(g.shape[0] * s))
        gs = np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize((gw, gh), Image.BILINEAR)) / 255.0
        for dy in range(-8, 9):
            for dx in range(-8, 9):
                canvas = np.zeros_like(r)
                ys, xs = max(dy, 0), max(dx, 0)
                gy, gx = max(-dy, 0), max(-dx, 0)
                hh = min(r.shape[0] - ys, gh - gy)
                ww = min(r.shape[1] - xs, gw - gx)
                if hh <= 0 or ww <= 0:
                    continue
                canvas[ys:ys + hh, xs:xs + ww] = gs[gy:gy + hh, gx:gx + ww]
                score = (canvas * r).sum() / max((canvas + r - canvas * r).sum(), 1e-6)
                if best is None or score > best[0]:
                    best = (score, s, dx * f, dy * f)
    return best


def document(look, style, sheet_rel, raw):
    g = np.asarray(guide())[..., 3]
    r = np.asarray(Image.open(raw).convert('RGBA'))[..., 3]
    score, s, dx, dy = register(r, g)
    print('registered %s: iou %.3f scale %.3f offset %d,%d' % (look, score, s, dx, dy))
    entries, blocks = [], {}
    for col, row, _, fields in slots():
        x0, y0, x1, y1 = slot_box(col, row)
        rx0, ry0 = x0 * s + dx + INSET, y0 * s + dy + INSET
        rx1, ry1 = x1 * s + dx - INSET, y1 * s + dy - INSET
        rect = [int(round(rx0)), int(round(ry0)), int(round(rx1 - rx0)), int(round(ry1 - ry0))]
        if 'kind' in fields:
            for wall in ('dark', 'light'):
                block = 'installation.%s-concrete-%s@%s' % (wall, fields['kind'], style)
                layout = 'wall-faces-4x4' if fields['kind'] == 'rubble' else 'cardinal-2x2'
                blocks[block] = {'id': block, 'layout': layout}
                entries.append({'id': '%s.%s' % (block, fields['slot']), 'block': block, 'slot': fields['slot'],
                                'included': True, 'cover': 'none', 'footprintCells': [1, 1], 'rect': rect})
        else:
            entry = dict(fields, id=fields['id'] + '@' + style, included=True, footprintCells=[1, 1], rect=rect)
            entry['note'] = '%s look of %s.' % (style.capitalize(), fields['id'])
            entries.append(entry)
    note = ("The rest of the %s works style's kit, look '%s': window and door openings, wall rubble, trench, hedgehog, "
            "fence and the light post's vent ring, painted in ONE ImageGen pass over art-source/fortworks/guides/"
            "kit-guide.png (kit.py: the base art drawn grey) with the style's works sheet as material reference; prompt "
            "art-source/fortworks/prompts/kit-%s.prompt.txt, raw retained unmodified. Cuts are registered against the "
            "guide (kit.py). Every id is a look <base id>@%s: art only, cover and blocking stay the base piece's "
            "(fort-works.md).") % (style, look, look, style)
    return {'sheetName': 'fortworks-%s-kit' % style, 'sheet': sheet_rel, 'cellPx': CELL_PX, 'alphaMin': 16,
            'idPrefix': 'doodad', 'gridCols': 8, 'gridRows': 6, 'note': note,
            'blocks': list(blocks.values()), 'entries': entries}


def variant_raw(look, variant):
    """The look's raw with the window slots painted from a variant's edit of it, under build/. The edit is asked to
    change only the windows, but ImageGen repaints the whole sheet and may lose its transparency (the earthwork
    loophole edit came back with an opaque ground), so only the window slots' colour is taken and every alpha stays
    the look's own."""
    own = np.array(Image.open(HERE / 'raw' / ('kit-%s.raw.png' % look)).convert('RGBA'))
    edit = np.array(Image.open(HERE / 'raw' / ('kit-%s-%s.raw.png' % (look, variant))).convert('RGBA'))
    margin = 16
    for col, row, _, fields in slots():
        if fields.get('kind') != 'window':
            continue
        x0, y0, x1, y1 = slot_box(col, row)
        own[y0 - margin:y1 + margin, x0 - margin:x1 + margin, :3] = edit[y0 - margin:y1 + margin, x0 - margin:x1 + margin, :3]
    out = ROOT / 'build' / 'fortworks-variant' / ('kit-%s-%s.raw.png' % (look, variant))
    out.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(own).save(out)
    return out


def export(look, style, variant=None):
    from make_doc import authoring
    raw = HERE / 'raw' / ('kit-%s.raw.png' % look) if variant is None else variant_raw(look, variant)
    name = 'fortworks-%s-kit' % style
    doc = document(look, style, raw.relative_to(ROOT).as_posix(), raw)
    if style not in trim.STYLES:
        authoring('tileset_write_document', {'name': name, 'document': doc})
        authoring('tileset_export', {'name': name})
        return
    # As make_doc.py: the graded copy under build/ is what the atlas is cut from; the document names the master.
    doc['note'] += (' Graded by art-source/fortworks/trim.py at export: export through kit.py, never'
                    ' tileset_export alone, or the atlas ships ungraded.')
    graded = ROOT / 'build' / 'fortworks-graded' / raw.name
    graded.parent.mkdir(parents=True, exist_ok=True)
    trim.graded(Image.open(raw), style).save(graded)
    authoring('tileset_write_document', {'name': name, 'document': dict(doc, sheet=graded.relative_to(ROOT).as_posix())})
    authoring('tileset_export', {'name': name})
    authoring('tileset_write_document', {'name': name, 'document': doc})


def main():
    if sys.argv[1] == 'guide':
        out = HERE / 'guides' / 'kit-guide.png'
        guide().save(out)
        print('wrote', out)
    elif sys.argv[1] == 'export':
        export(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else None)


if __name__ == '__main__':
    main()
