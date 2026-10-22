"""Asharu surface detail: ImageGen tiles laid over the game's desert01 map (see README.md).

Pure numpy, no Pillow and no bpy, so the same code builds the guides offline (make_guides.py,
with Pillow for files) and composites the tiles inside Blender (battle_blockout.py, with bpy for
files; Blender bundles numpy). Images here are float arrays, H x W x 3, row 0 at the top.

A tile is a square on the tangent plane at the battle's sub-point: planet-object X east and Y
north, in metres, the same object coordinates the planet shader reads. A direction d from the
centre lands on tile point (R d.x, R d.y). That is an orthographic projection, so a tile is a
little compressed toward its edge (1.7% at 1,700 km out on a 5,000 km planet); the guide is
sampled through the same projection, so the generation and the base map agree everywhere.
"""
import math

import numpy as np

R = 5.0e6
MAP_LAT, MAP_LON = 12.0, 95.0     # battle_blockout.py's; the battle sits over this map point

# name -> (centre x, centre y, side, pixels), metres. Biggest first: smaller ones are laid over.
#   regional: everything a 25-degree-down wide shot sees out to the 1,580 km horizon (S1, S4, S12)
#   mid:      the near ring of the wide shots, about 600 km out in every direction
#   near:     under the convoy; S1a's straight-down frame is 94 x 53 km of it
#   far:      S12's far band, 450-1,850 km down the track to the horizon (laid over mid, under track)
#   aft:      S4's ground, looking back and down past the platform to the north-west
#   flank:    S1's ground, looking forward and down past the convoy to the south-east
#   track:    S12's ground, 310-730 km down the track toward the northern canyons
TILES = {
    'regional': (300e3, 0.0, 3400e3, 1024),
    'mid': (0.0, 0.0, 1300e3, 1024),
    'far': (1150e3, 0.0, 1400e3, 1024),
    'aft': (-240e3, 350e3, 500e3, 1024),
    'flank': (230e3, -360e3, 440e3, 1024),
    'track': (520e3, 20e3, 420e3, 1024),
    'near': (0.0, 0.0, 160e3, 1024),
}
# Each tile's guide is the finished tile it sits in (the base map for the first), so every
# generation adds detail to the one above it rather than reinventing it.
PARENT = {'regional': None, 'mid': 'regional', 'far': 'regional', 'aft': 'mid', 'flank': 'mid', 'track': 'mid', 'near': 'mid'}
FEATHER = .14    # share of a tile's half-width over which it fades into the layer below


def _rotation():
    """The planet shader's lookup turn: object direction -> map frame (Z up, X at longitude 0)."""
    def rz(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1.]])

    def ry(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return rz(math.radians(MAP_LON)) @ ry(math.radians(90 - MAP_LAT)) @ rz(math.radians(90))


def tile_xy(name):
    """Object-plane X, Y (metres) at every pixel centre of a tile, row 0 north."""
    cx, cy, side, n = TILES[name]
    s = (np.arange(n) + .5) / n - .5
    x = cx + s[None, :] * side
    y = cy - s[:, None] * side
    return np.broadcast_to(x, (n, n)), np.broadcast_to(y, (n, n))


def map_uv(x, y):
    """Equirectangular (u, v) of desert01 at object-plane points, v = 0 at the south pole."""
    dx, dy = x / R, y / R
    dz = np.sqrt(np.clip(1 - dx * dx - dy * dy, 0, 1))
    d = np.stack([dx, dy, dz], -1) @ _rotation().T
    lon = np.arctan2(d[..., 1], d[..., 0])
    lat = np.arcsin(np.clip(d[..., 2], -1, 1))
    return lon / (2 * math.pi) + .5, lat / math.pi + .5


def sample(img, u, v):
    """Bilinear lookup in an equirectangular image (wraps in u, clamps in v)."""
    h, w = img.shape[:2]
    fx = (u % 1.0) * w - .5
    fy = (1 - v) * h - .5
    x0 = np.floor(fx).astype(int)
    y0 = np.floor(fy).astype(int)
    tx, ty = (fx - x0)[..., None], (fy - y0)[..., None]
    x1 = (x0 + 1) % w
    x0 %= w
    y1 = np.clip(y0 + 1, 0, h - 1)
    y0 = np.clip(y0, 0, h - 1)
    top = img[y0, x0] * (1 - tx) + img[y0, x1] * tx
    bot = img[y1, x0] * (1 - tx) + img[y1, x1] * tx
    return top * (1 - ty) + bot * ty


def base_tile(base_map, name):
    """What the base map paints over a tile: the guide a generation reskins."""
    return sample(base_map, *map_uv(*tile_xy(name)))


def blur(img, radius):
    """Three box passes, a near-Gaussian low-pass of about `radius` pixels, edges clamped."""
    out = img.astype(np.float64)
    r = max(1, int(round(radius / 1.7)))
    for _ in range(3):
        for axis in (0, 1):
            pad = [(0, 0)] * out.ndim
            pad[axis] = (r + 1, r)
            c = np.cumsum(np.pad(out, pad, mode='edge'), axis=axis)
            n = out.shape[axis]
            hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
            lo = np.take(c, np.arange(0, n), axis=axis)
            out = (hi - lo) / (2 * r + 1)
    return out


def resize(img, n):
    """Area/bilinear resize of a square-ish image to n x n (a generation's size varies)."""
    h, w = img.shape[:2]
    ys = (np.arange(n) + .5) / n * h - .5
    xs = (np.arange(n) + .5) / n * w - .5
    y0 = np.clip(np.floor(ys).astype(int), 0, h - 1)
    x0 = np.clip(np.floor(xs).astype(int), 0, w - 1)
    y1 = np.clip(y0 + 1, 0, h - 1)
    x1 = np.clip(x0 + 1, 0, w - 1)
    ty = np.clip(ys - y0, 0, 1)[:, None, None]
    tx = np.clip(xs - x0, 0, 1)[None, :, None]
    if h > 2 * n:   # downsizing a lot: pre-blur so it averages instead of picking pixels
        img = blur(img, h / n / 2)
    a, b = img[y0][:, x0], img[y0][:, x1]
    c, d = img[y1][:, x0], img[y1][:, x1]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def match_low(gen, guide, radius):
    """Keep the generation's detail and the guide's broad colour: the guide's low-pass with the
    generation's high-pass on top. So at a tile's feathered edge both layers agree in everything
    the layer below can show, and the blend has no seam."""
    return np.clip(gen - blur(gen, radius) + blur(guide, radius), 0, 1)


def feather(n, share=FEATHER):
    """A tile's own blend weight: 1 inside, smoothstep to 0 at its edge."""
    s = np.abs((np.arange(n) + .5) / n * 2 - 1)
    t = np.clip((1 - s) / share, 0, 1)
    t = t * t * (3 - 2 * t)
    return np.minimum(t[:, None], t[None, :])


def sample_tile(img, name, x, y):
    """Bilinear lookup of a finished tile at object-plane points (clamped at its edge)."""
    cx, cy, side, n = TILES[name]
    fx = ((x - cx) / side + .5) * n - .5
    fy = (.5 - (y - cy) / side) * n - .5
    x0 = np.clip(np.floor(fx).astype(int), 0, n - 1)
    y0 = np.clip(np.floor(fy).astype(int), 0, n - 1)
    tx = np.clip(fx - x0, 0, 1)[..., None]
    ty = np.clip(fy - y0, 0, 1)[..., None]
    x1 = np.clip(x0 + 1, 0, n - 1)
    y1 = np.clip(y0 + 1, 0, n - 1)
    top = img[y0, x0] * (1 - tx) + img[y0, x1] * tx
    bot = img[y1, x0] * (1 - tx) + img[y1, x1] * tx
    return top * (1 - ty) + bot * ty


def mask_composite(base, edit, mask, radius=6):
    """Inpainting by compositing: an edited generation over its original, inside a soft mask,
    with the edit's broad colour matched to the original so the patch does not show."""
    edit = resize(edit, base.shape[0])
    edit = match_low(edit, base, radius * 3)
    m = blur(mask[..., None] if mask.ndim == 2 else mask, radius)
    return base * (1 - m) + edit * m


def inside(name, x, y):
    """A tile's feathered blend weight at object-plane points (0 outside it)."""
    cx, cy, side, n = TILES[name]
    sx = np.abs((x - cx) / side * 2)
    sy = np.abs((y - cy) / side * 2)
    t = np.clip((1 - np.maximum(sx, sy)) / FEATHER, 0, 1)
    return (t * t * (3 - 2 * t))[..., None]


def stack_at(base_map, finished, x, y):
    """What the planet shows at object-plane points: the base map with every finished tile laid
    over it in order, each by its feathered weight. The renderer composites exactly this."""
    col = sample(base_map, *map_uv(x, y))
    for name, img in finished.items():
        w = inside(name, x, y)
        col = col * (1 - w) + sample_tile(img, name, x, y) * w
    return col


def guide_for(name, base_map, finished):
    """The image a tile's generation reskins: everything below it, as the planet shows it."""
    return stack_at(base_map, {k: v for k, v in finished.items() if k != name}, *tile_xy(name))


def finish_all(base_map, raw):
    """Every tile whose generation exists, finished in order: raw(name) -> array or None.
    Returns {name: colour}. A tile is skipped until the one it refines (PARENT) is finished."""
    finished = {}
    for name in TILES:
        parent = PARENT[name]
        if parent is not None and parent not in finished:
            continue
        gen = raw(name)
        if gen is None:
            continue
        guide = guide_for(name, base_map, finished)
        cx, cy, side, n = TILES[name]
        cell = side / n
        if parent is None:
            radius = 45e3 / cell                   # the base map's own resolution, ~31 km a pixel
        else:
            radius = 3 * TILES[parent][2] / TILES[parent][3] / cell
        col = match_low(resize(gen, n), guide, min(max(2.0, radius), n / 6))
        edit = raw(name + '-settled')
        if edit is not None:
            col = mask_composite(col, edit, settlement_mask(name), radius=3)
        finished[name] = col
    return finished

# ------------------------------------------------------------------------------- settlements
# Painted into a finished tile by an ImageGen edit (raw/<tile>-settled.png) composited back
# inside these shapes only. Each: (tile, kind, centre x, centre y, half-width, half-height, turn
# degrees), metres in the planet-object plane. Roads are polylines: (tile, 'road', [(x, y), ...], width).
SETTLEMENTS = [
    # Under S1a's frame (x -26..27 km, y -47..47 km), clear of the convoy at its centre.
    ('near', 'town', 19e3, -31.5e3, 5e3, 4e3, 20),      # walled town where two wadis meet
    ('near', 'port', -10e3, 33e3, 5e3, 3e3, -35),       # landing grid on the flat sand
    ('near', 'mine', 3e3, -14e3, 4.5e3, 2.8e3, -40),        # terraced open pits on the dark ridge
    ('near', 'road', [(19e3, -31.5e3), (11e3, -22e3), (3e3, -14e3), (-2e3, 2e3), (-6e3, 20e3),
                      (-10e3, 33e3), (-16e3, 62e3)], 120),
    ('near', 'road', [(19e3, -31.5e3), (34e3, -40e3), (62e3, -52e3)], 120),
    # Down S12's track toward the northern canyons, 450-650 km out.
    ('track', 'port', 482e3, -73e3, 16e3, 9e3, -15),     # spaceport on the salt pan
    ('track', 'town', 537e3, 45e3, 9e3, 7e3, 10),         # walled town at the canyon mouth
    ('track', 'mine', 646e3, -5e3, 14e3, 8e3, 25),         # strip mines on the massif's flank
    ('track', 'road', [(537e3, 45e3), (520e3, 10e3), (500e3, -40e3), (482e3, -73e3)], 300),
    ('track', 'road', [(482e3, -73e3), (560e3, -45e3), (646e3, -5e3)], 300),
    ('track', 'road', [(537e3, 45e3), (430e3, 70e3), (330e3, 95e3)], 300),
]


def _pixel(name, x, y):
    cx, cy, side, n = TILES[name]
    return ((x - cx) / side + .5) * n, (.5 - (y - cy) / side) * n


def settlement_mask(name, grow=1.35):
    """1 where a settlement or road of this tile lies (grown by `grow`), else 0."""
    cx, cy, side, n = TILES[name]
    cell = side / n
    yy, xx = np.mgrid[0:n, 0:n] + .5
    m = np.zeros((n, n))
    for s in SETTLEMENTS:
        if s[0] != name:
            continue
        if s[1] == 'road':
            pts = [_pixel(name, *p) for p in s[2]]
            w = s[3] / cell * grow + 1.5
            for (ax, ay), (bx, by) in zip(pts, pts[1:]):
                dx, dy = bx - ax, by - ay
                t = np.clip(((xx - ax) * dx + (yy - ay) * dy) / max(dx * dx + dy * dy, 1e-9), 0, 1)
                d = np.hypot(xx - ax - t * dx, yy - ay - t * dy)
                m = np.maximum(m, (d < w).astype(float))
            continue
        _, kind, sx, sy, hw, hh, turn = s
        px, py = _pixel(name, sx, sy)
        a = math.radians(turn)
        u = (xx - px) * math.cos(a) + (yy - py) * math.sin(a)
        v = -(xx - px) * math.sin(a) + (yy - py) * math.cos(a)
        rx, ry = hw / cell * grow + 2, hh / cell * grow + 2
        m = np.maximum(m, ((u / rx) ** 2 + (v / ry) ** 2 < 1).astype(float))
    return m
