"""One review sheet per design round: numbered ImageGen concepts, each as a
large top-down plate and at true battle zoom on paving beside a Marine.

  python round_sheet.py <round dir> <colony frame-0004.png> <out.png> "<title>" [names...]

Plates are keyed off their flat background, cropped, and scaled so every
concept covers the same opaque area at battle zoom (a Runner's footprint), so
the zoom tiles compare like for like.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from boards import font
from motion import CELL, ground, marine_cutouts

BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)
RUNNER_AREA_PX = 140  # opaque pixels of a Runner at 17.1 px per cell (about 10 px shoulders)


def key_plate(path):
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(int)
    h, w, _ = a.shape
    corners = np.concatenate([a[:20, :20].reshape(-1, 3), a[:20, -20:].reshape(-1, 3),
                              a[-20:, :20].reshape(-1, 3), a[-20:, -20:].reshape(-1, 3)])
    bg = np.median(corners, axis=0)
    dist = np.sqrt(((a - bg) ** 2).sum(2))
    alpha = np.clip((dist - 12) / 26, 0, 1)
    # drop specks far from the creature: keep rows/cols with real mass
    rgba = np.dstack([a, (alpha * 255).astype(int)]).astype(np.uint8)
    out = Image.fromarray(rgba, "RGBA")
    box = out.getchannel("A").point(lambda v: 255 if v > 60 else 0).getbbox()
    return out.crop(box) if box else out


def to_zoom(cut):
    a = np.asarray(cut.getchannel("A")) > 128
    area = a.sum()
    k = (RUNNER_AREA_PX / max(1, area)) ** 0.5
    size = (max(1, round(cut.width * k)), max(1, round(cut.height * k)))
    return cut.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def sheet(round_dir, frame, out, title, names):
    rd = Path(round_dir)
    raws = sorted(p for p in (rd / "raw").glob("c*.png") if p.stem[1:].isdigit())
    marines = marine_cutouts(frame)
    gnd = ground(frame, (700, 980, 1240, 1080))
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 12), title, font=font(24, True), fill=INK)
    d.text((24, 42), "each concept: ImageGen top-down plate; below it, true battle zoom (17.1 px per cell) on colony paving "
                     "beside a Marine, then the same pixels x3", font=font(14), fill=DIM)
    cw, ch = 474, 506
    for i, p in enumerate(raws):
        col, row = i % 4, i // 4
        x0, y0 = 12 + col * (cw + 6), 66 + row * (ch + 4)
        d.rectangle([x0, y0, x0 + cw, y0 + ch], fill=(24, 28, 36))
        num = int(p.stem[1:])
        d.text((x0 + 10, y0 + 6), f"#{num}", font=font(26, True), fill=ACC)
        if num - 1 < len(names):
            d.text((x0 + 62, y0 + 12), names[num - 1], font=font(17, True), fill=INK)
        cut = key_plate(p)
        # plate
        k = min((cw - 20) / cut.width, 290 / cut.height)
        pl = cut.convert("RGBa").resize((int(cut.width * k), int(cut.height * k)), Image.LANCZOS).convert("RGBA")
        plate_bg = Image.new("RGBA", (cw - 20, 292), (180, 180, 180, 255))
        plate_bg.alpha_composite(pl, ((plate_bg.width - pl.width) // 2, (plate_bg.height - pl.height) // 2))
        img.paste(plate_bg.convert("RGB"), (x0 + 10, y0 + 40))
        # zoom: 1:1 tile, then x3
        z = to_zoom(cut).rotate(-90, expand=True, resample=Image.BICUBIC)  # face the Marine on the right
        tile = gnd.crop((i * 40 % 300, 0, i * 40 % 300 + 110, 60)).copy()
        tile.alpha_composite(z, (36 - z.width // 2, 30 - z.height // 2))
        m = marines[i % len(marines)]
        tile.alpha_composite(m, (82 - m.width // 2, 30 - m.height // 2))
        img.paste(tile.convert("RGB"), (x0 + 10, y0 + 342))
        big = tile.resize((tile.width * 3, tile.height * 3), Image.NEAREST)
        img.paste(big.convert("RGB"), (x0 + 130, y0 + 330))
        d.text((x0 + 10, y0 + 410), "1:1", font=font(13), fill=DIM)
        d.text((x0 + 10, y0 + 428), "x3 right", font=font(13), fill=DIM)
    img.save(out)


if __name__ == "__main__":
    sheet(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5:])
