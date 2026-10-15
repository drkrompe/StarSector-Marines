"""Geometry only: the fort's operator seat for the seated console banks.

    python fort-fittings-guide.py          # writes fort-fittings-guide.png beside this file

Canvas 1536x1024, 3 columns x 2 rows of 512px frames, overhead and orthographic on transparent ground. Every frame holds
the same guide, the operator seat: a round padded swivel seat on a five-legged castor base, 400 px across and centred in
its frame. The generator is asked for six renderings of it so the best can be chosen by eye; image generation does not
hold absolute scale on a mostly empty canvas, so the guide fills it instead of sitting in one corner. The seat is
single-cell, so the room fitting turns it with the fort pose; it is round with no backrest or arm so that no pose reads
wrong at any of the stations it stands at (a bank's operator side varies by wall; the seat must not).
"""
from pathlib import Path
import math
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
S = 512
K = S / 192
PAD = '#4b5359'
PAD_EDGE = '#8d9498'
LEG = '#6f757a'
CASTOR = '#262a2e'
AMBER = '#d9a21e'


def seat(image):
    d = ImageDraw.Draw(image)
    cx, cy = S // 2, S // 2
    for i in range(5):
        a = math.radians(-90 + i * 72)
        x, y = cx + math.cos(a) * 66 * K, cy + math.sin(a) * 66 * K
        d.line((cx, cy, x, y), fill=LEG, width=round(9 * K))
        r = 9 * K
        d.ellipse((x - r, y - r, x + r, y + r), fill=CASTOR, outline=PAD_EDGE, width=4)
    r = 50 * K
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=PAD, outline=PAD_EDGE, width=10)
    r = 34 * K
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=PAD_EDGE, width=4)
    r = 8 * K
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=LEG, outline=PAD_EDGE, width=4)
    d.rectangle((cx - 4 * K, cy - 48 * K, cx + 4 * K, cy - 42 * K), fill=AMBER)


if __name__ == '__main__':
    sheet = Image.new('RGBA', (1536, 1024))
    for slot in range(6):
        frame = Image.new('RGBA', (S, S))
        seat(frame)
        sheet.paste(frame, ((slot % 3) * S, (slot // 3) * S))
    sheet.save(HERE / 'fort-fittings-guide.png')