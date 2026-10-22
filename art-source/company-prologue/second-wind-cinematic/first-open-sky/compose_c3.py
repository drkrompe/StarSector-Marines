"""C3 FIRST OPEN SKY: final frames (storyboard-v3.md, c3-layout.md, runtime-contract.md).

python compose_c3.py                 # review stills and contact sheet only (review/, 1920x1080)
python compose_c3.py --movie         # also every frame and the review mp4
python compose_c3.py --movie --fps 8 # a sketch: every third frame, held, for timing review
python compose_c3.py --coverage      # count window pixels no world layer covers, shot-wide

Writes build/cinematic/c3-first-open-sky/frames/%05d.png (1920x1080, 24 fps, sRGB, no
text) and c3-first-open-sky.mp4 beside them, with the audio cues mixed in for review.
The player takes captions(), overlays() and audio_cues() from here (runtime-contract.md).

Build order: c3_bands.py, c3_plates.py prep/check/assemble, c3_layers.py, then this.
Every world layer translates and scales at the rate c3_core.py's camera and distances
give it, and the whole world turns about the lens centre with the ship's attitude: the
shot opens nose down and descending out of the entry and eases level (c3_core.LEVEL_SPAN),
so the horizon swings level while the shake eases off. The window opens in the entry's
last cloud: frame 0 is the aperture filled with flat soft white, which burns off and
slides aft over the first 1.6 s. The camera shake moves the whole picture, cabin and all,
and is the entry's own shake carried on (c3_core.camera_shake).
"""
import argparse
import json
import math
import os
import subprocess
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parent))
sys.path.insert(1, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "arrival-audio"))
import castle_lines  # noqa: E402
from c3_look import grade_land, grade_sky  # noqa: E402
from c3_settlement import MAST_FOOT  # noqa: E402
from c3_core import (BASE, CABIN_DEPTH_M, CASTLE_REELS_AT, CASTLE_REELS_TAKE, CIN, DURATION, ENTRY_LEN,  # noqa: E402
                     F_PX, FPS, H, HORIZON_Y, LAYERS, ROOT, W, PLATE_SCALE, PLATE_X0, PLATE_Y0, camera_shake, lean_scale,
                     nose_down_deg, rise_at, smooth, travelled, truck_at, world_buffet)

LAYER_DIR = BASE / "build" / "layers"
AUDIO_SRC = ROOT / "asset-pipeline" / "src" / "tool" / "resources" / "audio" / "jangala-arrival"
OUT = ROOT / "build" / "cinematic" / "c3-first-open-sky"
SHOT = "c3-first-open-sky"

# The entry ends inside cloud; C3 picks up on this flat white in the aperture (the
# hand-off agreed in the shared notes, c3-handoff.md), then burns it off.
ENTRY_WHITE = np.array([244, 246, 248], np.float32) / 255
BURN = 1.6
FLOCK_IN = 8.6                       # s: the flock clears the right jamb, under Castle's line
DEBUG = False


# ---------------------------------------------------------------- runtime contract

def duration():
    return DURATION


def captions():
    """Castle's travel-reels line, once the ship is level (castle_lines.py, voice_timing())."""
    return castle_lines.captions("c3")


def voice_timing():
    """Where Castle's line sits in this shot, for the mix and the timeline."""
    return {"line": "c3_reels", "take": CASTLE_REELS_TAKE[0], "t": CASTLE_REELS_AT,
            "ends": round(CASTLE_REELS_AT + CASTLE_REELS_TAKE[1], 3)}


def overlays():
    return []


def audio_cues():
    """(t, sound_id, volume[, until]). Built by :asset-pipeline:processAudio from
    audio.json; sources from c3_audio.py. The bed loops for the whole shot; the wind
    is one pass whose buffeting eases with the shake; Castle's line over the intercom."""
    return [
        (0.0, "jangala_arrival_cabin_bed", 0.55, DURATION),
        (0.0, "jangala_arrival_wind", 0.80),
    ] + [(c["t"], c["sound"], c["volume"]) for c in castle_lines.cues("c3")]


# ---------------------------------------------------------------- sources

def arr(im):
    return np.asarray(im, np.float32) / 255.0


def img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8))


class Layer:
    """`pos(t)` is the layer's top-left in the level, unshaken frame at C3 time t."""
    def __init__(self, name, im, pos, scale=1.0, path=None, opaque=False):
        self.name, self.im, self.pos, self.scale, self.path, self.opaque = name, im, pos, scale, path, opaque


def baked(name, meta):
    """A baked layer at frame scale: anything stored larger is reduced once, with a
    proper filter, so per-frame placement never minifies (no shimmer in motion)."""
    im = Image.open(LAYER_DIR / (name + ".png")).convert("RGBA")
    s = meta[name]["scale"]
    if s < 1.0:
        im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
        s = 1.0
    return im, s


def bird_frame(phase, span, glide):
    """One small dark flier at a point of its flap cycle (phase 0..1), or gliding.
    A wing is two segments, shoulder-wrist and wrist-tip; the hand lags the arm, so the
    downstroke reads as a stroke and not as a hinge. Drawn at 6x and reduced."""
    k = 6
    s = int(span * k * 1.5 + 12)
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c, half = s / 2, span * k / 2
    if glide:
        arm, hand = 0.18, 0.10                                    # held a little up
    else:
        arm = 0.55 * math.sin(2 * math.pi * phase)
        hand = 0.75 * math.sin(2 * math.pi * (phase - 0.09))
    col = (36, 40, 42, 255)
    w = max(2, round(span * k / 8))
    for side in (-1, 1):
        wx, wy = c + side * half * 0.48 * math.cos(arm), c - half * 0.48 * math.sin(arm)
        tx, ty = wx + side * half * 0.52 * math.cos(hand), wy - half * 0.52 * math.sin(hand)
        d.line([(c, c), (wx, wy), (tx, ty)], fill=col, width=w, joint="curve")
    d.ellipse([c - w * 1.1, c - w * 0.7, c + w * 1.1, c + w * 0.8], fill=col)
    return im.resize((max(1, s // k), max(1, s // k)), Image.LANCZOS)


MAST_HEIGHT_M = 70.0
MAST_DISTANCE_M = 2000.0
MAST_STROBE_S = 1.5                  # a white daytime obstruction strobe, once every 1.5 s


def relay_mast(strobe):
    """The relay mast (a nod to Relay Nine): a guyed lattice in aviation red and white bands, its
    antenna panels near the top, 70 m tall at 2 km (about 48 px). Drawn at 8x and reduced, then
    given the grade's 2 km of haze; `strobe` lights the white obstruction strobe at its tip."""
    k = 8
    h = F_PX * MAST_HEIGHT_M / MAST_DISTANCE_M
    W8, H8 = round(14 * k), round((h + 6) * k)
    im = Image.new("RGBA", (W8, H8), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx, foot, top = W8 / 2, H8 - 1, 6 * k
    base, tip = 1.6 * k, 0.55 * k
    bands = 7
    for i in range(bands):
        y0 = top + (foot - top) * i / bands
        y1 = top + (foot - top) * (i + 1) / bands
        w0 = tip + (base - tip) * (y0 - top) / (foot - top)
        w1 = tip + (base - tip) * (y1 - top) / (foot - top)
        col = (196, 64, 44, 255) if i % 2 == 0 else (226, 224, 216, 255)
        d.polygon([(cx - w0 / 2, y0), (cx + w0 / 2, y0), (cx + w1 / 2, y1), (cx - w1 / 2, y1)], fill=col)
    # the shaded (right) face of the lattice
    d.polygon([(cx, top), (cx + tip / 2, top), (cx + base / 2, foot), (cx, foot)], fill=(0, 0, 0, 70))
    for y, w in ((top + 2.2 * k, 2.2 * k), (top + 5.5 * k, 1.8 * k)):           # antenna panels
        d.rectangle([cx - w, y, cx + w, y + 1.4 * k], fill=(214, 214, 208, 255))
    d.ellipse([cx + 0.6 * k, top + 9 * k, cx + 2.6 * k, top + 11.5 * k], fill=(222, 222, 216, 255))   # a dish
    d.line([(cx, top - 4 * k), (cx, top)], fill=(200, 200, 196, 255), width=round(0.35 * k))      # whip
    for gx in (-5.5, 5.5):                                                     # guy wires, barely there
        d.line([(cx, top + (foot - top) * 0.35), (cx + gx * k, foot)], fill=(200, 200, 196, 60), width=round(0.25 * k))
    im = im.resize((W8 // k, H8 // k), Image.LANCZOS)
    a = arr(im)
    a[..., :3] = grade_land(a[..., :3], MAST_DISTANCE_M)
    if strobe:
        yy, xx = np.mgrid[0:a.shape[0], 0:a.shape[1]].astype(np.float32)
        g = np.exp(-((xx - a.shape[1] / 2) ** 2 + (yy - (6 - 4)) ** 2) / 2.2)[..., None]
        a[..., :3] = a[..., :3] * (1 - g) + 1.0 * g
        a[..., 3:] = np.maximum(a[..., 3:], g)
    return img(a)


# The riverside freight road as Codex painted it (centreline traced on the plate, plate px): along
# the town's waterfront, through the depot yard below its container stacks (above the barge), past the weigh station, and upstream to where it
# passes under the canopy.
ROAD = [(2260, 1006.0), (2297, 997.0), (2335, 986.0), (2370, 975.0), (2390, 968.5), (2410, 966.5),
        (2430, 962.5), (2445, 956.0), (2457, 947.5), (2470, 940.0), (2550, 908.0), (2600, 886.0), (2690, 860.0),
        (2740, 838.5), (2800, 821.5), (2860, 812.5), (2945, 798.0)]
PLATE_M = 1.16                       # plate px per metre at ~2 km (c3_settlement.PX_PER_M)
TRUCK_M = (24.0, 2.6, 3.8)           # length, width, height: a road train, cab and two box trailers
# Both drive the town's waterfront past the buildings for the whole shot, down-left along the road
# (owner, 2026-10-22: in clear view, and against the scene's rightward drift, which already has the
# Atlas, the flock's entry and the slide): a two-truck convoy out of the weigh station toward the
# hill road, the red cab leading, the blue about 90 m behind. Speeds in m/s (43 and 40 km/h: town
# speeds); `lane` is plate px off the centreline.
TRUCKS = [
    {"name": "lead", "start": 2620.0, "t0": 0.0, "speed": -12.0, "lane": 0.0, "cab": (190, 60, 40)},
    {"name": "second", "start": 2720.0, "t0": 0.0, "speed": -11.0, "lane": 0.0, "cab": (52, 96, 160)},
]


def road_at(x):
    """(y, heading in degrees on screen) of the road's centreline at plate x."""
    xs = [p[0] for p in ROAD]
    ys = [p[1] for p in ROAD]
    y = float(np.interp(x, xs, ys))
    dy = float(np.interp(x + 4, xs, ys) - np.interp(x - 4, xs, ys)) / 8
    return y, math.degrees(math.atan(dy))


def truck_x(t, spec):
    """Plate x of the truck's centre at C3 time t (the road is near enough parallel to the frame
    that its speed along x is its speed)."""
    return spec["start"] + spec["speed"] * PLATE_M * (t - spec["t0"])


def truck_alpha(t, spec):
    """In view only on the open stretch: it fades in as it comes out from under the trees (or
    pulls away from the station) and out where the road goes under the canopy."""
    x = truck_x(t, spec)
    lo, hi = ROAD[0][0], ROAD[-1][0] - 6
    return float(np.clip((x - lo) / 12, 0, 1) * np.clip((hi - x) / 12, 0, 1)) if t >= spec["t0"] else 0.0


def truck_pos(t, spec, dist):
    x = truck_x(t, spec)
    y, _ = road_at(x)
    k = PLATE_SCALE / 2.0
    im = truck_image(t, spec)
    return (PLATE_X0 - truck_at(t, dist) + x * k - im.width / 2,
            PLATE_Y0 - rise_at(t, dist) + (y + spec["lane"]) * k - im.height / 2)


_TRUCK_CACHE = {}


def truck_image(t, spec):
    """The road train seen from 2 km, 15 degrees down: a coloured cab and two pale box trailers in
    profile, their roofs catching the sun, a shadow to the right, red dust kicked up behind it.
    Drawn at 8x along the road's heading, reduced, given 2 km of haze, scaled a touch with distance
    (farther up the stretch is farther away)."""
    x = truck_x(t, spec)
    y, heading = road_at(x)
    a = truck_alpha(t, spec)
    scale = 1.0 - 0.0006 * (ROAD[0][1] - y)                 # smaller up the stretch, farther away
    key = (spec["name"], round(heading), round(scale, 2), round(a, 1))
    if key in _TRUCK_CACHE:
        return _TRUCK_CACHE[key]
    k = 8
    px = PLATE_SCALE / 2.0 * PLATE_M * scale * k            # canvas px per metre
    length, width, height = TRUCK_M
    S = int(length * px * 3)
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = S / 2
    fwd = 1 if spec["speed"] > 0 else -1
    L, Hh, Dp = length * px, height * px, width * px * math.sin(math.radians(15))
    x0, x1 = c - L / 2, c + L / 2
    base = c + Hh / 2
    # dust behind, then shadow, trailers, cab: pale aluminium trailers against the red road, the cab
    # in colour, so it reads as a vehicle and not as one more container in the yard
    tail = x0 if fwd > 0 else x1
    for i in range(7):
        r = (0.35 + 0.22 * i) * Hh
        cx = tail - fwd * (0.25 + 0.42 * i) * L * 0.5
        d.ellipse([cx - r * 1.8, base - r * 1.1, cx + r * 1.8, base + r * 0.3], fill=(196, 150, 116, int(120 - 15 * i)))
    d.polygon([(x0 + 0.3 * Hh, base), (x1 + 0.3 * Hh, base), (x1 + 0.6 * Hh, base + 0.25 * Hh), (x0 + 0.6 * Hh, base + 0.25 * Hh)],
              fill=(20, 26, 18, 150))
    cab = 0.14 * L
    gap = 0.03 * L
    if fwd > 0:
        cabx, body = (x1 - cab, x1), (x0, x1 - cab - gap)
    else:
        cabx, body = (x0, x0 + cab), (x0 + cab + gap, x1)
    half = (body[1] - body[0] - gap) / 2
    for b0 in (body[0], body[0] + half + gap):
        d.rectangle([b0, base - Hh, b0 + half, base], fill=(196, 198, 194, 255))
        d.rectangle([b0, base - Hh - Dp, b0 + half, base - Hh], fill=(232, 234, 230, 255))
    d.rectangle([cabx[0], base - 0.8 * Hh, cabx[1], base], fill=spec["cab"] + (255,))
    d.rectangle([cabx[0], base - 0.8 * Hh - Dp, cabx[1], base - 0.8 * Hh], fill=tuple(min(255, int(v * 1.3)) for v in spec["cab"]) + (255,))
    im = im.rotate(-heading, resample=Image.BICUBIC, center=(c, c))
    im = im.resize((S // k, S // k), Image.LANCZOS)
    arr_ = np.asarray(im, np.float32) / 255
    arr_[..., :3] = grade_land(arr_[..., :3], MAST_DISTANCE_M)
    arr_[..., 3] *= a
    out = img(arr_)
    _TRUCK_CACHE[key] = out
    return out


def build():
    meta = json.loads((LAYER_DIR / "layers.json").read_text(encoding="utf-8"))
    D = {n: d for n, d, _ in LAYERS}
    L = []
    sky = Image.open(CIN / "managed-land-realism" / "assets" / "sky-realism-v1.png").convert("RGBA")
    sky_scale = SKY_SCALE
    sky = sky.resize((round(sky.width * sky_scale), round(sky.height * sky_scale)), Image.LANCZOS)
    sky_a = arr(sky)
    sky = img(np.dstack([grade_sky(sky_a[..., :3]), sky_a[..., 3]]))   # the C4/C5 air (c3_look.py)
    sky_x, sky_y = SKY_X, HORIZON_Y + 90 - sky.height
    L.append(Layer("sky", sky, lambda t: (sky_x - truck_at(t, D["horizon"]), sky_y), opaque=True))
    breeze = 1.0                                                  # m/s from the bow

    def plate(name, dist, breeze_dist=None, opaque=False):
        im, s = baked(name, meta)

        def pos(t):
            x = PLATE_X0 - truck_at(t, dist) - (F_PX * breeze * t / breeze_dist if breeze_dist else 0.0)
            return x, PLATE_Y0 - rise_at(t, dist)
        L.append(Layer(name, im, pos, scale=s, opaque=opaque))

    plate("range", D["horizon"], opaque=True)
    plate("far", D["far"], opaque=True)
    plate("mist-high", 4500.0, breeze_dist=4500.0)
    plate("mid", D["mid"], opaque=True)
    # Atlas: 300 m hull at 2.2 km (189 px), bow right, climbing out at 60 m/s and 8 m/s.
    atlas, s = baked("atlas", meta)
    ad = 2200.0
    L.append(Layer("atlas", atlas, lambda t: (520 + F_PX * (60.0 * t - travelled(t)[0]) / ad,
                                              205 - F_PX * (8.0 * t + travelled(t)[1]) / ad), scale=s))
    # Relay mast on the hill above the settlement (c3_settlement.MAST_FOOT), riding the mid layer.
    mast_on, mast_off = relay_mast(True), relay_mast(False)
    mfx, mfy = MAST_FOOT
    k = PLATE_SCALE / 2.0                                          # frame px per plate px

    def mast_pos(t):
        x = PLATE_X0 - truck_at(t, D["mid"]) + mfx * k - mast_off.width / 2
        return x, PLATE_Y0 - rise_at(t, D["mid"]) + mfy * k - mast_off.height + 1
    L.append(Layer("mast", None, mast_pos,
                   path=(lambda t: mast_on if (t + 0.4) % MAST_STROBE_S < 0.09 else mast_off, lambda t: 0.0)))
    # Freight trucks on the riverside road, riding the mid layer under the near bank's crowns.
    for spec in TRUCKS:
        L.append(Layer("truck-" + spec["name"], None, lambda t, sp=spec: truck_pos(t, sp, D["mid"]),
                       path=(lambda t, sp=spec: truck_image(t, sp), lambda t: 0.0)))
    plate("mist-low", 1700.0, breeze_dist=1700.0)
    plate("near", D["near"], opaque=True)
    # Flock: eleven 3 m fliers ~700 m out at about our altitude, against the bright low
    # sky; flying aft at 8 m/s they pass the ship and enter from behind the right jamb.
    fd = 700.0
    span = F_PX * 3.0 / fd * 1.4          # 5.9 px true; drawn 1.4x so a wingbeat reads at 1080p
    rng = np.random.default_rng(11)

    def flock_x(t):
        return F_PX * (travelled(t)[0] + 8.0 * t) / fd

    for i in range(11):
        bx, by = rng.uniform(-70, 70), rng.uniform(-14, 14)
        ph, sz, rate = rng.uniform(0, 1), rng.uniform(0.8, 1.15), rng.uniform(2.9, 3.5)
        glide_at = rng.uniform(0, 3)                              # each bird glides now and then

        def frames(t, ph=ph, sz=sz, rate=rate, g0=glide_at):
            glide = ((t + g0) % 2.6) > 2.0
            return bird_frame((t * rate + ph) % 1.0, span * sz, glide)

        def bob(t, ph=ph):
            return 1.2 * math.sin(2 * math.pi * (t * 0.7 + ph))

        x0 = 1900 + bx + flock_x(FLOCK_IN)
        L.append(Layer("bird%d" % i, None, lambda t, x0=x0, y0=255 + by: (x0 - flock_x(t), y0 - 0.8 * (t - FLOCK_IN)),
                       path=(frames, bob)))
    cabin, _ = baked("cabin", meta)
    L.append(Layer("cabin", cabin, lambda t: (0, 0)))
    # density of the entry's last cloud, sliding aft at ~800 m (c3-layout.md, Pickup)
    cloud = Image.open(CIN / "managed-land-realism" / "assets" / "clouds-realism-v1.png").convert("RGBA")
    cloud = cloud.filter(ImageFilter.GaussianBlur(5))             # out of focus, 0.8 km off
    return L, cloud


SKY_SCALE, SKY_X = 1.45, -260.0      # the sky plate is enlarged so the turned window stays inside it
SCENE = None


def scene():
    global SCENE
    if SCENE is None:
        SCENE = build()
    return SCENE


def world_affine(x, y, s, roll_deg):
    """PIL AFFINE coefficients (frame -> source) for a layer at top-left (x, y), scale s,
    with the world turned roll_deg counter-clockwise (horizon up on the right) about the
    lens centre. Through a port window the ship's pitch is exactly this roll."""
    th = math.radians(roll_deg)
    c, n = math.cos(th), math.sin(th)
    cx, cy = W / 2, H / 2
    return (c / s, -n / s, (-c * cx + n * cy + cx - x) / s,
            n / s, c / s, (-n * cx - c * cy + cy - y) / s)


def place(canvas, im, x, y, s, roll=0.0, alpha=1.0, tint=None, cov=None):
    """Composite a layer placed at top-left (x, y), scale s, turned with the world by roll."""
    w, h = im.size
    th = math.radians(roll)
    corners = []
    for qx, qy in ((0, 0), (w, 0), (0, h), (w, h)):
        px, py = x + qx * s - W / 2, y + qy * s - H / 2
        corners.append((W / 2 + math.cos(th) * px + math.sin(th) * py, H / 2 - math.sin(th) * px + math.cos(th) * py))
    bx0, by0 = max(0, math.floor(min(c[0] for c in corners))), max(0, math.floor(min(c[1] for c in corners)))
    bx1, by1 = min(W, math.ceil(max(c[0] for c in corners))), min(H, math.ceil(max(c[1] for c in corners)))
    if bx1 <= bx0 or by1 <= by0:
        return
    a_, b_, c_, d_, e_, f_ = world_affine(x, y, s, roll)
    tile = im.transform((bx1 - bx0, by1 - by0), Image.AFFINE,
                        (a_, b_, c_ + a_ * bx0 + b_ * by0, d_, e_, f_ + d_ * bx0 + e_ * by0),
                        resample=Image.BICUBIC, fillcolor=(0, 0, 0, 0))
    a = arr(tile)
    rgb, al = a[..., :3], a[..., 3:4] * alpha
    if tint is not None:
        rgb = rgb * 0.45 + np.array(tint, np.float32) * 0.55
    reg = canvas[by0:by1, bx0:bx1]
    canvas[by0:by1, bx0:bx1] = reg * (1 - al) + rgb * al
    if cov is not None:
        cr = cov[by0:by1, bx0:bx1]
        cov[by0:by1, bx0:bx1] = cr * (1 - al[..., 0]) + al[..., 0]


def burn_off(canvas, cloud, t, jx, jy, roll):
    """The entry's cloud over the world: flat white at t = 0, thinning from its
    thinnest parts first as it slides aft, gone at BURN."""
    if t >= BURN:
        return
    u = smooth(t / BURN)
    s = 2.4
    x, y = -1250 - truck_at(t, 800.0) + jx, -560 - rise_at(t, 800.0) + jy
    tile = cloud.transform((W, H), Image.AFFINE, world_affine(x, y, s, roll),
                           resample=Image.BILINEAR, fillcolor=(0, 0, 0, 0))
    c = arr(tile)
    dens = c[..., 3] * (0.55 + 0.45 * (c[..., :3] @ np.array([0.3, 0.6, 0.1], np.float32)))
    # A gentle slope: thin cloud fades while thick cloud still stands, so the world
    # comes through the cloud rather than through holes cut in it.
    a = np.clip((0.4 + 0.6 * dens + 1.0 - 2.4 * u) * 1.3, 0, 1)[..., None]
    col = ENTRY_WHITE * (1 - u) + (c[..., :3] * c[..., 3:4] + ENTRY_WHITE * (1 - c[..., 3:4])) * u
    canvas[:] = canvas * (1 - a) + col * a


def shaken(canvas, s):
    """The camera shake at entry second s, applied to the whole picture (cabin included).
    `canvas` is a float sRGB array; returns an Image."""
    dx, dy, rot, zoom = camera_shake(s)
    im = img(canvas)
    if abs(dx) + abs(dy) + abs(rot) * 20 + (zoom - 1) * 1000 < 0.02:
        return im
    th = math.radians(rot)
    c, n = math.cos(th) / zoom, math.sin(th) / zoom
    cx, cy = W / 2, H / 2
    # frame -> source: undo the translation, then the turn and zoom about the centre
    return im.transform((W, H), Image.AFFINE, (c, -n, cx - c * (cx + dx) + n * (cy + dy),
                                                n, c, cy - n * (cx + dx) - c * (cy + dy)),
                        resample=Image.BICUBIC)


def frame(t, shake_s=None, coverage=None, unshaken=False):
    """C3 at time t. shake_s overrides the shake clock (entry seconds; default ENTRY_LEN + t).
    `coverage`, a list, receives the count of window pixels no opaque world layer covers.
    unshaken=True returns the float array before the camera shake."""
    layers, cloud = scene()
    s_ent = ENTRY_LEN + t if shake_s is None else shake_s
    canvas = np.zeros((H, W, 3), np.float32)
    cov = np.zeros((H, W), np.float32) if coverage is not None else None
    jx, jy = world_buffet(s_ent)
    roll = nose_down_deg(t)
    for L in layers:
        if L.name == "cabin":
            if coverage is not None:
                glass = arr(L.im)[..., 3] < 0.5
                coverage.append(int(((cov < 0.98) & glass).sum()))
            burn_off(canvas, cloud, t, jx, jy, roll)
            s = L.scale * lean_scale(t, CABIN_DEPTH_M)
            place(canvas, L.im, W / 2 - W / 2 * s, H / 2 - H / 2 * s, s)
            continue
        x, y = L.pos(t)
        if L.path is not None:
            draw, bob = L.path
            place(canvas, draw(t), x + jx, y + bob(t) + jy, 1.0, roll)
            continue
        # the lean moves the eye 5 cm toward km-distant layers: scale 1.00004, ignored
        tint = TINT.get(L.name) if DEBUG else None
        place(canvas, L.im, x + jx, y + jy, L.scale, roll, tint=tint, cov=cov if L.opaque else None)
    return canvas if unshaken else shaken(canvas, s_ent)


TINT = {"sky": (0.35, 0.6, 1.0), "range": (0.6, 0.45, 1.0), "far": (1.0, 0.8, 0.25),
        "mist-high": (1, 1, 1), "mid": (0.25, 0.85, 0.45), "atlas": (1.0, 0.3, 0.9),
        "mist-low": (1, 1, 1), "near": (1.0, 0.35, 0.25)}


# ---------------------------------------------------------------- review

def label(im, text):
    d = ImageDraw.Draw(im)
    d.rectangle([0, H - 34, W, H], fill=(0, 0, 0))
    d.text((14, H - 26), text, fill=(235, 235, 235))
    return im


STILLS = [
    ("01-pickup", 0.0, "t=0.0s  pickup: the aperture full of the entry's flat white, nose down, shaking"),
    ("02-burn", 0.8, "t=0.8s  the cloud burning off, sliding aft and up; horizon still tilted"),
    ("03-levelling", 2.8, "t=2.8s  flattening out: the horizon swinging level, the shake easing"),
    ("04-level", 6.0, "t=6.0s  level, shake gone; the sideways slide carries on"),
    ("05-line", 10.0, "t=10.0s  under Castle's line: the lean under way, Atlas climbing, flock in from the right"),
    ("06-out", DURATION - 1 / FPS, "t=16.0s  out"),
]


def contact_sheet(path):
    sheet = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(sheet)
    n = 16
    for i in range(n):
        t = min(DURATION - 1 / FPS, i * DURATION / (n - 1))
        th = frame(t).resize((W // 4, H // 4), Image.LANCZOS)
        x, y = (i % 4) * W // 4, (i // 4) * H // 4
        sheet.paste(th, (x, y))
        d.rectangle([x, y, x + 64, y + 18], fill=(0, 0, 0))
        d.text((x + 4, y + 4), "%.2fs" % t, fill=(255, 255, 255))
    sheet.save(path)


STEP = 1


def render(i):
    frame(i / FPS).save(OUT / "frames" / ("%05d.png" % i))
    return i


def _coverage(i):
    holes = []
    frame(i / FPS, coverage=holes, unshaken=True)
    return i, holes[0]


def review_audio(path):
    cues = audio_cues()
    inputs, graph, labels = [], [], []
    for k, cue in enumerate(cues):
        t, sid, vol = cue[:3]
        loop = len(cue) > 3
        if loop:
            inputs += ["-stream_loop", "-1"]
        built = ROOT / "mod" / "sounds" / "story" / "jangala-arrival" / (sid + ".ogg")   # a voice: processed
        inputs += ["-i", str(AUDIO_SRC / (sid + ".flac") if (AUDIO_SRC / (sid + ".flac")).exists() else built)]
        graph.append("[%d:a]aformat=sample_rates=44100:channel_layouts=stereo,volume=%.2f,adelay=%d:all=1%s[a%d]"
                     % (k, vol, round(t * 1000), ",atrim=0:%.3f" % cue[3] if loop else "", k))
        labels.append("[a%d]" % k)
    graph.append("%samix=inputs=%d:normalize=0:duration=longest,atrim=0:%.3f[m]" % ("".join(labels), len(cues), DURATION))
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", *inputs, "-filter_complex", ";".join(graph),
                    "-map", "[m]", "-c:a", "pcm_s16le", str(path)], check=True)


def main():
    global DEBUG
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--movie", action="store_true")
    ap.add_argument("--fps", type=int, default=FPS, help="sketch rate: render every FPS/fps-th frame and hold it")
    ap.add_argument("--coverage", action="store_true")
    ap.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    args = ap.parse_args()
    n = round(DURATION * FPS)
    if args.coverage:
        worst = []
        with ProcessPoolExecutor(args.workers) as pool:
            for i, holes in pool.map(_coverage, range(0, n, 3), chunksize=4):
                if holes:
                    worst.append((holes, i))
        print("frames with uncovered window pixels:", len(worst), "worst:", sorted(worst)[-5:])
        return
    rev = BASE / "review"
    rev.mkdir(exist_ok=True)
    for old in rev.glob("c3-0*.png"):
        old.unlink()
    for name, t, text in STILLS:
        label(frame(t), "C3  " + text).save(rev / ("c3-%s.png" % name))
    DEBUG = True
    label(frame(8.0), "C3 band check t=8.0s  sky / range / far / mid / near / Atlas tinted; mist white; cabin untinted").save(
        rev / "c3-07-bands.png")
    DEBUG = False
    contact_sheet(rev / "c3-08-contact-sheet.png")
    print("stills and contact sheet in", rev)
    if not args.movie:
        return
    fr = OUT / "frames"
    fr.mkdir(parents=True, exist_ok=True)
    for old in fr.glob("*.png"):
        old.unlink()
    step = max(1, round(FPS / args.fps))
    with ProcessPoolExecutor(args.workers) as pool:
        for i in pool.map(render, range(0, n, step), chunksize=2):
            if i % 48 == 0:
                print("frame", i, flush=True)
    if step > 1:                                                  # a sketch: hold each drawn frame
        import shutil
        for i in range(n):
            if i % step:
                shutil.copyfile(fr / ("%05d.png" % (i - i % step)), fr / ("%05d.png" % i))
    wav = OUT / "c3-review-audio.wav"
    review_audio(wav)
    movie = OUT / (SHOT + ".mp4")
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", str(FPS), "-i", str(fr / "%05d.png"),
                    "-i", str(wav), "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-crf", "16", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "160k", "-shortest", str(movie)], check=True)
    (OUT / "timeline.json").write_text(json.dumps({
        "shot": SHOT, "duration": duration(), "fps": FPS, "frames": n, "sketch_step": step,
        "captions": captions(), "overlays": overlays(), "audio_cues": audio_cues(),
        "voice": voice_timing()}, indent=1), encoding="utf-8")
    print("wrote", movie)


if __name__ == "__main__":
    main()
