"""Draw thruster markers over a shot's still: the debug picture for the marker review.

    python draw_glyphs.py <out dir> <stem> [--title TEXT]

Reads <stem>-plain.png and <stem>-glyphs.json (check_thrust_markers.py --still: the stem is
<shot>-<t>s) and writes <stem>-markers.png, 1920x1080. Each nozzle is a ring at the mouth with a line down its exhaust:
orange for a main drive, cyan for a lift jet, magenta for a maneuvering jet. A filled ring is
burning at that moment (the throttle sets how bright), a hollow one is off, and a grey one is a
dead bell. One label per craft, beside its nozzles.
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

COLOURS = {'main': (255, 150, 40), 'lift': (60, 220, 255), 'maneuver': (255, 70, 220)}


def font(size):
    for name in ('segoeui.ttf', 'arial.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main():
    out, shot = Path(sys.argv[1]), sys.argv[2]
    title = sys.argv[sys.argv.index('--title') + 1] if '--title' in sys.argv else shot
    data = json.loads((out / ('%s-glyphs.json' % shot)).read_text(encoding='utf-8'))
    im = Image.open(out / ('%s-plain.png' % shot)).convert('RGB')
    assert im.size == (1920, 1080), im.size
    over = Image.new('RGBA', im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    small, big = font(17), font(26)
    by_craft = {}
    w, h = im.size
    for g in data['markers']:
        x, y = g['xy']
        if not (-50 <= x <= w + 50 and -50 <= y <= h + 50):
            continue
        by_craft.setdefault(g['craft'], []).append((x, y))
        col = COLOURS[g['kind']] if g['working'] else (150, 150, 150)
        r = max(6.0, min(g["r_px"], 60.0))
        if g['tip']:
            d.line([(x, y), tuple(g['tip'])], fill=col + (230,), width=2)
        on = g['throttle'] > 0.01 and g['working']
        fill = col + (int(90 + 165 * g['throttle']),) if on else None
        d.ellipse([x - r, y - r, x + r, y + r], outline=col + (255,), fill=fill, width=2 if on else 1)
    taken = [(0, 0, w, 44)]                       # the title bar
    for craft, pts in sorted(by_craft.items(), key=lambda kv: min(p[1] for p in kv[1])):
        label = '%s (%d)' % (craft, len(pts))
        tw = d.textlength(label, font=small)
        x = min(max(max(p[0] for p in pts) + 12, 4), w - tw - 4)
        y = min(max(min(p[1] for p in pts) - 10, 48), h - 24)
        while any(x < bx1 and x + tw > bx0 and y < by1 and y + 20 > by0 for bx0, by0, bx1, by1 in taken):
            y += 20                                  # step down past a label already there
        taken.append((x, y, x + tw, y + 20))
        if y - min(p[1] for p in pts) > 30:        # moved well away: a leader line back to it
            d.line([(x - 2, y + 10), (max(p[0] for p in pts), min(p[1] for p in pts))], fill=(255, 255, 255, 120))
        d.text((x + 1, y + 1), label, font=small, fill=(0, 0, 0, 220))
        d.text((x, y), label, font=small, fill=(255, 255, 255, 255))
    head = '%s  t=%.1fs   orange main, cyan lift, magenta maneuver; filled = burning, hollow = off, grey = dead bell' % (
        title, data['t'])
    d.rectangle([0, 0, w, 40], fill=(0, 0, 0, 170))
    d.text((14, 6), head, font=big, fill=(255, 255, 255, 255))
    im = Image.alpha_composite(im.convert('RGBA'), over).convert('RGB')
    im.save(out / ('%s-markers.png' % shot))
    print('wrote', out / ('%s-markers.png' % shot), len(data['markers']), 'markers in view of', len(by_craft), 'craft')


if __name__ == '__main__':
    main()
