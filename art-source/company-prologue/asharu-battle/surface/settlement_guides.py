"""Settlement guides: a finished tile with each settlement's plan drawn over it (see README.md).

The plan is a sketch for ImageGen to render as built ground seen from orbit: where the walls,
streets, pads, pits and roads go and how big they are. Only the pixels inside
surface_detail.settlement_mask are taken back from the generation.
"""
import math

import numpy as np
from PIL import Image, ImageDraw

import surface_detail as sd

WALL = (205, 196, 182)
STREET = (172, 160, 142)
ROOF = (128, 116, 102)
PAD = (214, 210, 200)
TERRACE = (150, 112, 82)
BENCH = (196, 160, 120)
ROAD = (200, 186, 160)


def draw(name, tile):
    items = [s for s in sd.SETTLEMENTS if s[0] == name]
    if not items:
        return None
    n = tile.shape[0]
    cell = sd.TILES[name][2] / n
    k = 4      # draw at 4x and shrink, so thin lines antialias
    im = Image.fromarray((np.clip(tile, 0, 1) * 255).astype(np.uint8)).resize((n * k, n * k), Image.BICUBIC)
    d = ImageDraw.Draw(im)

    def px(x, y):
        a, b = sd._pixel(name, x, y)
        return a * k, b * k

    for s in items:
        if s[1] == 'road':
            d.line([px(*p) for p in s[2]], fill=ROAD, width=max(2, int(s[3] / cell * k * 2)))
    for s in items:
        if s[1] == 'road':
            continue
        _, kind, x, y, hw, hh, turn = s
        cx, cy = px(x, y)
        rw, rh = hw / cell * k, hh / cell * k
        a = math.radians(turn)

        def at(u, v):
            return (cx + u * rw * math.cos(a) - v * rh * math.sin(a),
                    cy + u * rw * math.sin(a) + v * rh * math.cos(a))

        def poly(pts, **kw):
            d.polygon([at(u, v) for u, v in pts], **kw)

        if kind == 'town':
            ring = [(math.cos(t) * .95, math.sin(t) * .95) for t in np.linspace(0, 2 * math.pi, 9)[:-1]]
            poly(ring, fill=STREET, outline=WALL, width=max(2, k * 2))
            for i in range(-4, 5):          # a grid of blocks inside the wall
                for j in range(-4, 5):
                    u, v = i / 5, j / 5
                    if u * u + v * v < .6:
                        poly([(u - .07, v - .07), (u + .07, v - .07), (u + .07, v + .07), (u - .07, v + .07)], fill=ROOF)
        elif kind == 'port':
            poly([(-1, -1), (1, -1), (1, 1), (-1, 1)], fill=(186, 180, 168))
            for i in range(-3, 4):          # a grid of round landing pads
                for j in range(-1, 2):
                    u, v = i * .27, j * .6
                    r = min(rw * .1, rh * .22)
                    px_, py_ = at(u, v)
                    d.ellipse((px_ - r, py_ - r, px_ + r, py_ + r), fill=PAD, outline=ROOF, width=max(1, k))
            poly([(-1, .85), (1, .85), (1, 1), (-1, 1)], fill=ROOF)   # hangars along one side
        elif kind == 'mine':
            for i, r in enumerate((1, .78, .56, .34)):
                ring = [(math.cos(t) * r, math.sin(t) * r) for t in np.linspace(0, 2 * math.pi, 33)[:-1]]
                poly(ring, fill=BENCH if i % 2 else TERRACE)
            poly([(1, .1), (1.6, .1), (1.6, .45), (1, .45)], fill=(110, 100, 92))   # tailings
    im = im.resize((n, n), Image.LANCZOS)
    return np.asarray(im, dtype=np.float64) / 255.0
