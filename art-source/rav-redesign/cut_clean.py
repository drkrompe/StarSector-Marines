"""Clean-up passes the rig exporters run on every cut layer, so a layer is one piece with no floating islands.

  clip_to(img, under)   keep a hero cut only where the generated underlay is, fading over a few px at the
                        clip; a body layer then carries no claw or scythe stubs the cut left outside the trunk
  clean(img)            zero faint alpha speckle and drop every component but the largest (and any near its
                        size); run on the saved sprite, where the flood fill is cheap

Neither moves anything: canvas size and placement stay exactly as the exporter made them, so the pivots
the composers hold are untouched.
"""
from collections import deque

import numpy as np
from PIL import Image, ImageFilter

FLOOR = 10          # alpha at or below this is speckle, never part of a sprite
KEEP_FRAC = 0.15    # a second piece at least this fraction of the largest is kept (none of ours has one)


def clip_to(img, under, grow=6, feather=3, where=None):
    """img's alpha times the underlay's silhouette, grown by `grow` px and feathered by `feather` px.

    `where` (0..1 per pixel) limits the clip to part of the canvas, for a hero whose approved outline is wider
    than its underlay everywhere but around the cuts."""
    m = under.getchannel("A").point(lambda v: 255 if v > 60 else 0)
    m = np.asarray(m.filter(ImageFilter.MaxFilter(2 * grow + 1)).filter(ImageFilter.GaussianBlur(feather))) / 255
    if where is not None:
        m = 1 - where * (1 - m)
    out = img.copy()
    a = np.asarray(img.getchannel("A")).astype(float) * m
    out.putalpha(Image.fromarray(a.astype(np.uint8)))
    return out


def components(mask):
    """8-connected labels of a boolean mask and each label's size (label 0 is the background)."""
    lab = np.zeros(mask.shape, np.int32)
    h, w = mask.shape
    sizes = [0]
    for y, x in zip(*np.nonzero(mask)):
        if lab[y, x]:
            continue
        n = len(sizes)
        lab[y, x] = n
        q, cnt = deque([(y, x)]), 0
        while q:
            cy, cx = q.popleft()
            cnt += 1
            for ny in (cy - 1, cy, cy + 1):
                for nx in (cx - 1, cx, cx + 1):
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not lab[ny, nx]:
                        lab[ny, nx] = n
                        q.append((ny, nx))
        sizes.append(cnt)
    return lab, np.array(sizes)


def clean(img, floor=FLOOR, keep_frac=KEEP_FRAC):
    """Drop alpha speckle and every island; returns the image unchanged in size and placement."""
    arr = np.asarray(img.convert("RGBA")).copy()
    a = arr[..., 3]
    a[a <= floor] = 0
    lab, sizes = components(a > 0)
    if len(sizes) > 1:
        keep = np.nonzero(sizes >= sizes.max() * keep_frac)[0]
        keep = keep[keep > 0]
        a[~np.isin(lab, keep)] = 0
    arr[..., 3] = a
    return Image.fromarray(arr, "RGBA")


def save(img, path):
    clean(img).save(path)
