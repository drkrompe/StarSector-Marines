"""The lit engine bells painted into a ship card, found from the pixels (numpy only, so both
Blender and plain Python use it).

    bells, centre = painted_bells(rgba)      # rgba: H x W x 4 floats in 0..1, top row first

bells are (x, y, radius) in card pixels; centre is the hull's alpha centroid, so the exhaust of
a bell runs from the centre out through it. A lit bell burns to near white at its core: a
blue-white drive (the traffic cards) or a warm one (C3's Atlas). Paint of the same hue, a blue
container or a pale hull panel, is told apart by being less bright or less tinted.
"""
import math

import numpy as np


def blobs(mask, value, min_px=6):
    """(x, y, radius, peak value, size) of each 8-connected cluster of True pixels."""
    todo = set(zip(*np.nonzero(mask)))
    out = []
    while todo:
        stack = [todo.pop()]
        comp = []
        while stack:
            y, x = stack.pop()
            comp.append((y, x))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    q = (y + dy, x + dx)
                    if q in todo:
                        todo.remove(q)
                        stack.append(q)
        if len(comp) >= min_px:
            ys, xs = np.array(comp).T
            out.append((float(xs.mean()), float(ys.mean()), math.sqrt(len(comp) / math.pi),
                        float(value[ys, xs].max()), len(comp)))
    return out


def painted_bells(rgba):
    rgb, a = rgba[..., :3], rgba[..., 3]
    solid = a > 0.5
    ys, xs = np.nonzero(solid)
    centre = (float(xs.mean()), float(ys.mean()))
    # Blue-white drives: keep the clusters whose whitest pixel is as white as the whitest anywhere.
    white = rgb.min(axis=2)
    found = blobs(solid & (rgb[..., 2] > 0.70) & (rgb[..., 2] - rgb[..., 0] > 0.12), white)
    peak = max((b[3] for b in found), default=0.0)
    found = [b for b in found if b[3] >= max(peak - 0.03, 0.8)]
    if not found:
        # Warm drives: near-saturated and clearly warmer than the pale hull plating around them.
        found = blobs(solid & (rgb.max(axis=2) > 0.95) & (rgb[..., 0] - rgb[..., 2] > 0.18), rgb.max(axis=2))
        if found:
            big = max(b[4] for b in found)
            found = [b for b in found if b[4] >= 0.25 * big]
    return [(x, y, r) for x, y, r, _, _ in found], centre
