"""Round 3: purple Runner concepts. Sheet 01 is round 2's concept sheet; sheet 02 puts every
concept, exactly as generated (no rim), on each ground at 32 px per cell with the live unit shadow,
and measures contrast.

  python round3.py rounds/round-3 ../../../build/reports <out dir> <names...>
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from boards import font
from round2 import (ACC, BG, DIM, INK, PPC32, concept_sheet, cut_marines, ground_tiles, place, scaled)
from round_sheet import key_plate


def lum(a):
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


def grounds_sheet(round_dir, root, out, names, title):
    raws = sorted(Path(round_dir, "raw").glob("c*.png"), key=lambda p: int(p.stem[1:]))
    m32 = cut_marines(root, "rav-zoom32-marines", PPC32)
    tiles = ground_tiles(root, (400, 150))
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 10), title, font=font(24, True), fill=INK)
    d.text((24, 40), "32 px per cell beside a Marine, live unit shadow at its normal strength, no rim, nothing added. "
                     "Columns: grounds.", font=font(14), fill=DIM)
    for j, (gname, _) in enumerate(tiles):
        d.text((250 + j * 412, 62), gname, font=font(17, True), fill=ACC)
    y, results = 88, {}
    for p in raws:
        num = int(p.stem[1:])
        body = scaled(key_plate(p).rotate(-90, expand=True, resample=Image.BICUBIC), PPC32)
        d.text((24, y + 50), f"#{num}", font=font(24, True), fill=ACC)
        d.text((74, y + 56), names[num - 1] if num - 1 < len(names) else "", font=font(15, True), fill=INK)
        for j, (gname, g) in enumerate(tiles):
            t = g.copy()
            place(t, m32[j % len(m32)], 300, 75, PPC32)
            before = np.asarray(t.convert("RGB")).astype(float)
            place(t, body, 150, 75, PPC32)
            after = np.asarray(t.convert("RGB")).astype(float)
            mask = np.zeros(before.shape[:2], bool)
            ba = np.asarray(body.getchannel("A")) > 200
            ox, oy = int(150 - body.width / 2), int(75 - body.height / 2)
            mask[oy:oy + ba.shape[0], ox:ox + ba.shape[1]] = ba
            c = float(np.abs(lum(after) - lum(before))[mask].mean())
            results[(num, gname)] = c
            img.paste(t.convert("RGB"), (250 + j * 412, y))
            d.rectangle([250 + j * 412, y + 132, 250 + j * 412 + 92, y + 150], fill=BG)
            d.text((254 + j * 412, y + 133), f"contrast {c:.0f}", font=font(13), fill=INK)
        y += 162
    img.save(out)
    return results


if __name__ == "__main__":
    rd, root, out_dir = sys.argv[1], sys.argv[2], Path(sys.argv[3])
    names = sys.argv[4:]
    concept_sheet(rd, root, out_dir / "01-runner-concepts.png", names,
                  "RAV BLOOM RUNNER  /  ROUND 3  /  purples, RTS alien-swarm finish")
    r = grounds_sheet(rd, root, out_dir / "02-on-the-grounds.png", names,
                      "ROUND 3  /  every concept as generated on every ground")
    for (n, g), c in r.items():
        print(f"#{n} {g:15s} {c:5.1f}")
