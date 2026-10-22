"""Assembles the creep look-option sheets from CreepOptionsEvidence runs.

Each option is one renderEvidence run with its own -Dbattle.render.creep.look.*
values, written to build/reports/render/creep-options/<variant>/. This crops
every option 1:1 (no resampling) from the same place in its GL frame, labels
them, and writes the three sheets plus one crop per option and options.json:

    python art-source/hive-creep/option_sheets.py <out_dir> [commit]
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
SRC = os.path.join(ROOT, 'build', 'reports', 'render', 'creep-options')
W, H = 1920, 1080
BG = (18, 20, 26)
INK = (230, 228, 235)
SUB = (160, 158, 170)

SURFACE = [
    ('A', 'surface-A', 'Organic plates (as built)',
     'Warped chitin scales in two sizes, in patches, bare membrane with blisters between.'),
    ('B', 'surface-B', 'Grown chitin',
     'Plates over nearly all the body, taller and glossier, so it reads as shell rather than stone.'),
    ('C', 'surface-C', 'Tissue cells',
     'Cells about twice as fine and everywhere: a honeycomb or tissue rather than plates.'),
    ('D', 'surface-D', 'Smooth membrane',
     'A soft membrane with the plates only faintly showing through.'),
]
WALL = [
    ('A', 'surface-A', 'Four stages (as built)',
     'Veins reach the wall top and cross it, then a narrow crust, then the crust takes the top.'),
    ('B', 'wall-B', 'Fast two-stage',
     'Veins first, then the whole wall top crusts over at once.'),
    ('C', 'wall-C', 'No climbing',
     'Creep stops at the wall foot; only the dark contact crease marks the wall.'),
]
VEINS = [
    ('A', 'veins-none', 'None', 'Plates and membrane only.'),
    ('B', 'veins-sparse', 'Sparse', 'Fewer, thicker veins, about twice as far apart.'),
    ('C', 'surface-A', 'Medium (as built)', 'Thin veins one to two cells apart.'),
    ('D', 'veins-dense', 'Dense', 'A finer, busier network, nearly twice as many lines.'),
]
WALL_STAGES = ['early', 'mid', 'late']


def font(size):
    for name in ('arialbd.ttf', 'Arial Bold.ttf', 'DejaVuSans-Bold.ttf', 'arial.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def crop(variant, name, w, h):
    """A w x h window centred on the framed wall, which every option's camera centres on."""
    im = Image.open(os.path.join(SRC, variant, name)).convert('RGB')
    assert im.size == (W, H), (variant, name, im.size)
    x0, y0 = (W - w) // 2, (H - h) // 2
    return im.crop((x0, y0, x0 + w, y0 + h))


def labelled(img, key, label, band=34):
    out = Image.new('RGB', (img.width, img.height + band), BG)
    out.paste(img, (0, band))
    d = ImageDraw.Draw(out)
    d.text((10, 5), key, font=font(24), fill=(255, 214, 120))
    d.text((44, 7), label, font=font(20), fill=INK)
    return out


def grid_sheet(title, panels, cols):
    sheet = Image.new('RGB', (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((14, 8), title, font=font(22), fill=SUB)
    pw, ph = panels[0].size
    rows = (len(panels) + cols - 1) // cols
    gx = (W - cols * pw) // (cols + 1)
    top = 40
    gy = max(4, (H - top - rows * ph) // (rows + 1))
    for i, p in enumerate(panels):
        r, c = divmod(i, cols)
        sheet.paste(p, (gx + c * (pw + gx), top + gy + r * (ph + gy)))
    return sheet


def main():
    out = sys.argv[1]
    commit = sys.argv[2] if len(sys.argv) > 2 else ''
    os.makedirs(out, exist_ok=True)
    questions = []

    def options(question, rows, files):
        questions.append({'question': question, 'options': [
            {'key': k, 'label': label, 'file': f, 'note': note} for (k, _, label, note), f in zip(rows, files)]})

    # Surface and veins: one patch each, 2 x 2.
    for sheet_name, title, question, rows, prefix in (
            ('01-surface.png', 'Creep surface at play zoom (32 px a cell), GL', 'Which creep surface?', SURFACE, 'surface'),
            ('03-veins.png', 'Creep vein density at play zoom (32 px a cell), GL', 'How many veins?', VEINS, 'veins')):
        panels, files = [], []
        for key, variant, label, _ in rows:
            c = crop(variant, 'patch.png', 940, 480)
            f = '%s-%s.png' % (prefix, key)
            c.save(os.path.join(out, f))
            files.append(f)
            panels.append(labelled(c, key, label))
        grid_sheet(title, panels, 2).save(os.path.join(out, sheet_name))
        options(question, rows, files)

    # Wall fill: each option a strip, early / mid / late.
    sheet = Image.new('RGB', (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((14, 8), 'Creep on walls: one wall as it ages (about 0 s, 45 s, 110 s after the creep reached it), GL, 32 px a cell',
           font=font(22), fill=SUB)
    files = []
    cw, ch, band = 620, 290, 34
    y = 44
    for key, variant, label, _ in WALL:
        stages = [crop(variant, 'wall-%d.png' % i, cw, ch) for i in range(3)]
        strip = Image.new('RGB', (3 * cw + 2 * 10, ch), BG)
        for i, s in enumerate(stages):
            strip.paste(s, (i * (cw + 10), 0))
        f = 'wall-%s.png' % key
        strip.save(os.path.join(out, f))
        files.append(f)
        row = labelled(strip, key, label + '    (early | mid | late)', band)
        sheet.paste(row, ((W - row.width) // 2, y))
        y += row.height + 22
    sheet.save(os.path.join(out, '02-wall-fill.png'))
    options('How should creep take a wall?', WALL, files)

    json.dump(questions, open(os.path.join(out, 'options.json'), 'w'), indent=2)
    preview = [
        ('01-surface.png', 'Creep options: surface', 'Four surfaces on the same patch at play zoom: A as built, B grown chitin, C tissue cells, D smooth membrane.'),
        ('02-wall-fill.png', 'Creep options: wall fill', 'One wall as the creep takes it, early, mid and late: A four stages as built, B fast two-stage, C no climbing.'),
        ('03-veins.png', 'Creep options: vein density', 'The same patch with no veins, sparse, medium (as built) and dense.'),
    ]
    json.dump([{'id': 'hive-creep-options-' + f[3:-4], 'section': 'swarm', 'title': t, 'caption': cap,
                'file': f, 'kind': 'image', 'commit': commit} for f, t, cap in preview],
              open(os.path.join(out, 'preview.json'), 'w'), indent=2)


if __name__ == '__main__':
    main()
