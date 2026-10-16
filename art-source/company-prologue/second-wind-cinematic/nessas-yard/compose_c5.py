"""C5 NESSA'S YARD: the final composer (c5-layout.md, runtime-contract.md).

python compose_c5.py                  # review stills: review/c5-t0.0 .. t8.0 .png and a contact sheet
python compose_c5.py --frames         # every frame to build/cinematic/c5-nessas-yard/frames
python compose_c5.py --frames --mp4   # plus the review mp4 with the cue mix laid in

About 8 s at 1920x1080 / 24 fps: a slow push-in on Second Wind standing in cradle Kilo Nine,
yard sound only, then a straight cut into Y1. The camera only scales about one point; each
layer takes the push to its own weight, nearer layers more.

Layers, back to front (each a still unless named as moving):
  L1 sky        assets/c5-sky-v1.png, shifted so its tree line sits on the yard's horizon
  L2 district   assets/c5-yard-v1.png, registered to the guide and keyed off its sky
  L3 yard       the same plate's ground, cradle, gantry and foreground props
  L3 hull       layers/c5-hull.png and its cast shade layers/c5-hull-shadow.png (render_c5_hull.py)
  L3 moving     hoist crate (4 s loop), steam plumes and heat shimmer (procedural sheets),
                five yard crew on a walk cycle, Nessa still with a slow weight shift
Generated art: c5-sky-v1, c5-yard-v1, c5-crew-walk-v1, c5-nessa-crate-v1 (prompts/ and the
.provenance.json beside each master).

The runtime contract's data: captions() and overlays() are empty (no dialogue, no lock-ons);
audio_cues() lays the yard: the ship winding down, steam on the plumes' breathing cycle,
cooling hull ticks, the jib winch on the crate's loop, a gantry radio, a crane horn far off,
boots arriving late, and the jungle under all of it.
"""
import argparse
import json
import subprocess
import wave
from functools import lru_cache
from multiprocessing import Pool
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
OUT = ROOT / "build" / "cinematic" / "c5-nessas-yard"
CACHE = OUT / "cache"
AUDIO = ROOT / "asset-pipeline" / "src" / "tool" / "resources" / "audio" / "jangala-yard"
W, H, FPS, DURATION = 1920, 1080, 24, 8.0
R = 1.2                                   # layer resolution over output: headroom for the push
LW, LH = round(W * R), round(H * R)
META = json.loads((BASE / "guides" / "c5-camera.json").read_text(encoding="utf-8"))
PUSH = META["push"]
CX, CY = META["push_centre"]
WEIGHT = {"L1": 0.35, "L2": 0.6, "L3": 0.85}
STEAM_PERIOD = 2.9                        # the plumes breathe on this; c5_yard_steam_vent matches it
CRATE_M = 1.2
HOIST_X, HOIST_TOP = 1574.0, 560.0       # the painted jib's tip and underside (registered plate, at 1080)
HOIST_GROUND = META["hoist"]["low"][1]   # ground row at the hoist's depth
BELLY_FOOT = 712.0                        # row (at 1080) of the cradle's footing under the hull
SKY_TREE_BASE = 618.0                     # row (at 1080) where c5-sky-v1's tree line meets its ground
HOIST_LOW_AT = 1.0                        # the crate is lowest at 1 s and 5 s, highest at 3 s and 7 s
HOIST_PERIOD = 4.0                        # the crate's lower-and-raise loop; c5_yard_winch matches it

# Registration of the yard plate onto the guide. Codex kept every x but stretched the plate
# vertically, unevenly, so rows are remapped piecewise through matched landmarks
# (plate row -> guide row, at 1080): tower cab top, hall eave, jib, gantry roof, hall base,
# cradle pad tops, gantry feet. Measured by eye on gridded crops of both.
YARD_ROWS = [(0, 53), (343, 418), (420, 500), (480, 548), (532, 603), (600, 645), (610, 650),
             (690, 718), (1080, 1080)]


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def cam_scale(t):
    return PUSH[0] + (PUSH[1] - PUSH[0]) * smooth(t / DURATION)


# ================================================================ layer preparation

def _rgba(path, size=(LW, LH)):
    return Image.open(path).convert("RGBA").resize(size, Image.LANCZOS)


def _alpha(path):
    return np.asarray(Image.open(path).getchannel("A").resize((LW, LH), Image.LANCZOS), np.float32) / 255


def _blur(a, r):
    return np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r)), np.float32) / 255


def _grow(a, r):
    return np.asarray(Image.fromarray((a > 0.5).astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(2 * r + 1)), np.float32) / 255


def _shrink(a, r):
    return np.asarray(Image.fromarray((a > 0.5).astype(np.uint8) * 255).filter(ImageFilter.MinFilter(2 * r + 1)), np.float32) / 255


def register_yard():
    """The yard plate at layer resolution, rows remapped onto the guide."""
    src = np.asarray(_rgba(BASE / "assets" / "c5-yard-v1.png"), np.float32)
    plate_y, guide_y = zip(*YARD_ROWS)
    rows = np.interp(np.arange(LH) / R, guide_y, plate_y) * R          # guide row -> plate row
    r0 = np.clip(np.floor(rows).astype(int), 0, LH - 2)
    f = (rows - r0)[:, None, None]
    out = src[r0] * (1 - f) + src[r0 + 1] * f
    return out


def remove_painted_hook(img):
    """The plate's jib carries a painted cable and hook; the moving crate brings its own."""
    x0, x1 = int((HOIST_X - 10) * R), int((HOIST_X + 12) * R)
    top, bot = int((HOIST_TOP + 1) * R), int(628 * R)
    left, right = img[top:bot, x0 - 1], img[top:bot, x1 + 1]
    for i, x in enumerate(range(x0, x1 + 1)):
        u = i / (x1 - x0)
        img[top:bot, x] = left * (1 - u) + right * u
    return img


def key_sky(img, guide_any):
    """Alpha for everything that is not the plate's plain sky. Deep inside the guide's shapes it is
    solid; in a band around their edges it is decided by colour against that row's sky."""
    rgb = img[..., :3] / 255
    outside = _grow(guide_any, int(10 * R)) < 0.5
    sky = np.zeros((LH, 3), np.float32)
    last = rgb[0, 0]
    for y in range(LH):
        m = outside[y]
        if m.sum() > 40:
            last = np.median(rgb[y][m], axis=0)
        sky[y] = last
    d = np.sqrt(((rgb - sky[:, None, :]) ** 2).sum(-1))
    keyed = np.clip((d - 0.035) / 0.05, 0, 1)
    band = _grow(guide_any, int(30 * R))
    core = _shrink(guide_any, int(30 * R))
    a = np.maximum(core, keyed * band)
    return _blur(a, 0.6)


def prepare():
    """Build every still layer once, at layer resolution, under build/.../cache."""
    CACHE.mkdir(parents=True, exist_ok=True)
    stamp = CACHE / "stamp.json"
    inputs = [Path(__file__), BASE / "assets" / "c5-yard-v1.png", BASE / "assets" / "c5-sky-v1.png",
              BASE / "layers" / "c5-hull.png", BASE / "layers" / "c5-hull-shadow.png", BASE / "guides" / "c5-camera.json"]
    key = {str(p.name): p.stat().st_mtime for p in inputs}
    if stamp.exists() and json.loads(stamp.read_text()) == key:
        return
    yard = remove_painted_hook(register_yard())
    # dry concrete: the plate's sheen on the ground reads as wet, so its highlights are pulled
    # down below the horizon (the style contract rules out wet ground)
    lum = yard[..., :3].mean(-1, keepdims=True)
    low = (np.arange(LH) > 640 * R)[:, None, None]
    yard[..., :3] = np.where(low & (lum > 150), yard[..., :3] - (lum - 150) * 0.45, yard[..., :3])
    g2 = _alpha(BASE / "guides" / "c5-L2-yard-guide.png")
    g3 = _alpha(BASE / "guides" / "c5-L3-front-guide.png")
    g4 = _alpha(BASE / "guides" / "c5-L4-fore-guide.png")
    nonsky = key_sky(yard, np.maximum(np.maximum(g2, g3), g4))
    # district: its own shapes, carried 10 px down past the building bases so the faster near
    # ground never opens a gap under them
    dist = np.clip(g2 * (1 - g3) + 0.0, 0, 1)
    down = np.zeros_like(dist)
    for k in range(int(10 * R)):
        down[k:] = np.maximum(down[k:], dist[:LH - k] if k else dist)
    a2 = nonsky * _blur((down > 0.5).astype(np.float32), 0.8)
    a3 = nonsky * (1 - _blur(((g2 > 0.5) & (g3 < 0.5)).astype(np.float32), 0.8))
    # the plate's own horizon (top of the near ground at the right edge) sets the sky's height
    col = a3[:, int(LW * 0.97):].mean(1)
    horizon = int(np.argmax(col > 0.5)) / R
    for name, a in (("L2", a2), ("L3", a3)):
        im = np.dstack([yard[..., :3], a * 255]).astype(np.uint8)
        Image.fromarray(im, "RGBA").save(CACHE / (name + ".png"))
    sky = _rgba(BASE / "assets" / "c5-sky-v1.png")
    # the sky plate's tree line stands on its own ground at SKY_TREE_BASE; stretch the plate
    # about its top so that base lands a few px under the yard's horizon (hidden by it)
    k = (horizon + 4) / SKY_TREE_BASE
    sky2 = sky.resize((LW, round(LH * k)), Image.LANCZOS).crop((0, 0, LW, LH))
    shift = round((horizon + 4 - SKY_TREE_BASE) * R)
    sky2.save(CACHE / "L1.png")
    hull = _rgba(BASE / "layers" / "c5-hull.png")
    h = np.asarray(hull, np.float32)
    haze = np.array([0.80, 0.86, 0.92]) * 255
    h[..., :3] = h[..., :3] * 0.95 + haze * 0.05
    Image.fromarray(h.astype(np.uint8), "RGBA").save(CACHE / "hull.png")
    sh = np.asarray(_rgba(BASE / "layers" / "c5-hull-shadow.png"), np.float32)
    # the hardstand under the belly is in deep shade: from the hull's lowest edge in each column
    # down to the cradle's footing, fading, feathered sideways
    ha = h[..., 3] > 128
    rows = np.arange(LH)[:, None]
    belly = np.where(ha.any(0), LH - 1 - np.argmax(ha[::-1], 0), -1)
    foot = BELLY_FOOT * R
    depth = np.clip((rows - belly[None, :]) / np.maximum(foot - belly[None, :], 1), 0, 1)
    under = np.where((belly[None, :] > 0) & (rows > belly[None, :]), 0.62 * (1 - depth) ** 0.8 + 0.12, 0)
    under *= np.clip((foot + 18 * R - rows) / (18 * R), 0, 1)
    under = _blur(np.clip(under, 0, 1).astype(np.float32), 6 * R)
    shade = np.zeros((LH, LW, 4), np.uint8)
    shade[..., :3] = (22, 26, 34)
    shade[..., 3] = np.clip(np.maximum(sh[..., 3] * 0.82, under * 255), 0, 255)
    Image.fromarray(shade, "RGBA").save(CACHE / "shadow.png")
    stamp.write_text(json.dumps(key))
    print("layers prepared; yard horizon at y=%.0f, sky shifted %+d px" % (horizon, shift / R))


# ================================================================ cards

def _split_cells(im, cols, rows):
    w, h = im.width // cols, im.height // rows
    return [[im.crop((c * w, r * h, (c + 1) * w, (r + 1) * h)) for c in range(cols)] for r in range(rows)]


def _resize(im, scale):
    w, h = max(1, round(im.width * scale)), max(1, round(im.height * scale))
    return im.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")


def _clean(im):
    """Codex's alpha keeps a faint dark halo; cut it so a figure has no smoke around it."""
    a = np.asarray(im.getchannel("A"), np.float32)
    a = np.clip((a - 40) / 150, 0, 1) * 255
    out = im.copy()
    out.putalpha(Image.fromarray(a.astype(np.uint8)))
    return out


@lru_cache(None)
def crew_frames():
    """Two walkers x 8 frames, scaled so a 1.8 m figure is the guide's measured height."""
    sheet = Image.open(BASE / "assets" / "c5-crew-walk-v1.png").convert("RGBA")
    rows = _split_cells(sheet, 8, 2)
    target = np.median([c["start"][1] - c["head"][1] for c in META["crew"]])
    out = []
    for row in rows:
        cells = [_clean(c) for c in row]
        boxes = [c.getchannel("A").point(lambda v: 255 if v > 128 else 0).getbbox() for c in cells]
        tall = np.median([b[3] - b[1] for b in boxes])
        foot = max(b[3] for b in boxes)
        k = target / tall
        frames = []
        for c in cells:
            small = _resize(c, k)
            frames.append((small, (c.width / 2) * k, foot * k))
        out.append(frames)
    return out


@lru_cache(None)
def nessa_and_crate():
    sheet = _clean(Image.open(BASE / "assets" / "c5-nessa-crate-v1.png").convert("RGBA"))
    a = np.asarray(sheet.getchannel("A")) > 128
    cols = a.any(0)
    runs, x = [], 0
    while x < len(cols):
        if cols[x]:
            s = x
            while x < len(cols) and cols[x]:
                x += 1
            if x - s > 40:
                runs.append((s, x))
        x += 1
    nessa = []
    target = META["nessa"]["foot"][1] - META["nessa"]["head"][1]
    for s, e in runs[:2]:
        crop = sheet.crop((s, 0, e, sheet.height))
        b = crop.getchannel("A").point(lambda v: 255 if v > 128 else 0).getbbox()
        crop = crop.crop((0, b[1], crop.width, b[3]))
        k = target / crop.height
        small = _resize(crop, k)
        nessa.append((small, small.width / 2, small.height))
    s, e = runs[2]
    crate = sheet.crop((s, 0, e, sheet.height))
    b = crate.getchannel("A").point(lambda v: 255 if v > 128 else 0).getbbox()
    crate = crate.crop(b)
    px_m = (META["hoist"]["low"][1] - META["hoist"]["up"][1]) / 2.0
    ca = np.asarray(crate.getchannel("A")) > 128
    box_w = ca[int(ca.shape[0] * 0.85)].sum()                        # the box itself, below the sling
    k = CRATE_M * px_m / box_w
    crate = _resize(crate, k)
    return nessa, crate, px_m


# ================================================================ procedural sheets

@lru_cache(None)
def fbm(seed, size=512, beta=2.6):
    """Tileable fractal noise in [0, 1], shaped in the frequency domain (wraps both ways)."""
    rng = np.random.default_rng(seed)
    f = np.fft.fftfreq(size)
    r = np.sqrt(f[:, None] ** 2 + f[None, :] ** 2)
    r[0, 0] = 1
    spec = np.fft.fft2(rng.standard_normal((size, size))) / r ** (beta / 2)
    spec[0, 0] = 0
    n = np.real(np.fft.ifft2(spec))
    n = (n - n.min()) / (n.max() - n.min())
    return n.astype(np.float32)


def _sample(tex, x, y):
    s = tex.shape[0]
    xi, yi = np.floor(x).astype(int), np.floor(y).astype(int)
    fx, fy = x - xi, y - yi
    x0, y0, x1, y1 = xi % s, yi % s, (xi + 1) % s, (yi + 1) % s
    return (tex[y0, x0] * (1 - fx) * (1 - fy) + tex[y0, x1] * fx * (1 - fy)
            + tex[y1, x0] * (1 - fx) * fy + tex[y1, x1] * fx * fy)


def smoothstep_arr(u):
    u = np.clip(u, 0, 1)
    return u * u * (3 - 2 * u)


def breathe(t, phase=0.0):
    u = ((t + phase) / STEAM_PERIOD) % 1.0
    return 0.55 + 0.45 * np.exp(-u * 3.0)


PLUMES = [  # (screen x, y at 1080 before the push, width m, height m, lean, seed, phase)
    dict(at=META["steam"][0]["at"][:2], w=2.4, h=10.0, lean=0.35, seed=3, phase=0.0, gain=1.0, sun=0.55),
    dict(at=META["steam"][1]["at"][:2], w=2.0, h=8.5, lean=0.30, seed=5, phase=1.1, gain=0.9, sun=0.55),
    dict(at=(785.0, 545.0), w=1.4, h=8.0, lean=0.5, seed=7, phase=0.6, gain=0.85, sun=1.0),   # off the hull past the bell
]


def steam_sheet(p, t, px_m):
    """One plume at time t, as an RGBA card and the card's anchor (base centre) offset."""
    wpx, hpx = p["w"] * px_m, p["h"] * px_m
    cw, ch = int(wpx * 5 + hpx * p["lean"]) + 8, int(hpx) + 8
    yy, xx = np.mgrid[0:ch, 0:cw].astype(np.float32)
    v = 1 - yy / hpx                              # 0 at the base, 1 at the top
    v = np.clip(v, 0, 1.2)
    cxv = wpx * 2.0 + p["lean"] * hpx * v ** 1.3  # drifts right as it rises
    width = wpx * (0.5 + 2.2 * v)
    u = (xx - cxv) / width
    env = np.exp(-u ** 2 * 2.2) * smoothstep_arr(v / 0.22) * np.clip(1 - v, 0, 1) ** 1.4
    rise = 1.4 * px_m * t                         # metres per second upward, plus a sideways drift
    sc = 140.0 / hpx
    n1 = _sample(fbm(p["seed"]), (xx - 0.25 * px_m * t) * sc * 1.0, (yy + rise) * sc)
    n2 = _sample(fbm(p["seed"] + 1), (xx - 0.15 * px_m * t) * sc * 2.3, (yy + rise * 1.4) * sc * 2.3)
    dens = env * np.clip((0.55 * n1 + 0.45 * n2) * 2.4 - 0.75 + 0.6 * (1 - v), 0, None)
    dens *= breathe(t, p["phase"]) * p["gain"]
    a = np.clip(dens * 1.6, 0, 0.72)
    lit = np.array([247, 246, 241], np.float32)
    shade = np.array([168, 178, 190], np.float32)
    # vents stand in the belly's shade; their steam brightens only as it rises clear of it
    k = np.clip(0.45 + 0.55 * n2 + 0.25 * v, 0, 1) * np.clip(p["sun"] + (1 - p["sun"]) * v * 1.4, 0, 1)
    k = k[..., None]
    rgb = shade * (1 - k) + lit * k
    card = np.dstack([rgb, a * 255]).astype(np.uint8)
    return Image.fromarray(card, "RGBA"), (wpx * 2.0, hpx)


SHIMMER = [  # hot bell mouths on the near (starboard) side: screen x, y, width px, height px
    (470, 445, 150, 200), (125, 505, 180, 170),
]


def shimmer_sheet(x, y, w, h, t, seed):
    """Heat over the bells as a translucent sheet: rising, wavering pale streaks, no refraction."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    v = 1 - yy / h
    env = np.sin(np.pi * xx / w) ** 2 * np.clip(v / 0.15, 0, 1) * (1 - v) ** 1.2
    n = _sample(fbm(40 + seed, beta=2.0), xx * 1.6 + 6 * np.sin(yy * 0.08 + t * 5), (yy + 90 * t) * 0.5)
    a = env * np.clip((n - 0.42) * 2.5, 0, 1) * 0.17
    card = np.zeros((h, w, 4), np.uint8)
    card[..., :3] = (236, 232, 222)
    card[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(card, "RGBA")


# ================================================================ the frame

@lru_cache(None)
def layers():
    return {n: Image.open(CACHE / (n + ".png")).convert("RGBA") for n in ("L1", "L2", "L3", "hull", "shadow")}


def place(canvas, im, s, res=R):
    """Scale a full-frame layer (res px per output px) by s about the push centre."""
    a, c = res / s, res * (CX - CX / s)
    e, f = res / s, res * (CY - CY / s)
    canvas.alpha_composite(im.transform((W, H), Image.AFFINE, (a, 0, c, 0, e, f), Image.BICUBIC))


def at(p, s):
    """A point on a layer, given at 1080 before the push, where it lands after scale s."""
    return CX + (p[0] - CX) * s, CY + (p[1] - CY) * s


def paste_card(canvas, card, anchor, pos, s):
    """Card anchored at (ax, ay) placed with that anchor on pos, scaled with its layer."""
    if abs(s - 1) > 1e-3:
        card = _resize(card, s)
    x, y = pos[0] - anchor[0] * s, pos[1] - anchor[1] * s
    xi, yi = int(np.floor(x)), int(np.floor(y))
    fx, fy = x - xi, y - yi
    # subpixel placement: shift the card inside a 1 px larger transparent frame
    big = Image.new("RGBA", (card.width + 1, card.height + 1))
    big.paste(card, (0, 0))
    big = big.transform(big.size, Image.AFFINE, (1, 0, -fx, 0, 1, -fy), Image.BILINEAR)
    canvas.alpha_composite(big, (xi, yi)) if xi >= 0 and yi >= 0 else canvas.paste(big, (xi, yi), big)


def contact_shadow(canvas, pos, s, w=16, h=4):
    sh = Image.new("RGBA", (int(w * 3), int(h * 5)))
    ImageDraw.Draw(sh).ellipse((w * 0.5, h * 1.5, w * 2.5, h * 3.5), fill=(20, 22, 28, 110))
    sh = sh.filter(ImageFilter.GaussianBlur(2))
    canvas.alpha_composite(sh, (int(pos[0] - w * 1.2 + 5 * s), int(pos[1] - h * 2.5)))


CREW = [  # walker row (0 empty hands, 1 tool case), start delay s, cycle phase, scale, tint
    (0, 0.00, 0, 1.00, (1.00, 1.00, 1.00)),
    (1, 0.12, 3, 1.03, (0.97, 0.98, 1.00)),
    (0, 0.20, 5, 0.97, (1.02, 1.00, 0.97)),
    (1, 0.08, 6, 0.99, (0.95, 0.96, 0.98)),
    (0, 0.17, 2, 1.04, (0.99, 1.00, 1.02)),
]
CYCLE_S = 1.12                    # one full stride (two steps) at 1.3 m/s


def frame_image(t):
    s = cam_scale(t)
    L = layers()
    s1, s2, s3 = (s ** WEIGHT[k] for k in ("L1", "L2", "L3"))
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    place(canvas, L["L1"], s1)
    place(canvas, L["L2"], s2)
    place(canvas, L["L3"], s3)
    place(canvas, L["shadow"], s3)
    nessa, crate, px_m = nessa_and_crate()
    # hoist: the crate rides between 1.9 m and 3.5 m on a 4 s loop, behind the crew and above
    # their heads, and is at the top while the file passes under the jib (about 1.6 to 4.2 s)
    lift = 0.5 - 0.5 * np.cos(2 * np.pi * (t - HOIST_LOW_AT) / HOIST_PERIOD)   # 0 low .. 1 high
    lift = smooth(lift)
    bottom_m = 1.9 + 1.6 * lift
    gx, gy0 = HOIST_X, HOIST_GROUND
    crate_bottom = (gx, gy0 - bottom_m * px_m)
    crate_top_y = crate_bottom[1] - crate.height
    jib = at((HOIST_X, HOIST_TOP), s3)
    ct = at((gx, crate_top_y + 2), s3)
    d = ImageDraw.Draw(canvas)
    d.line([jib, ct], fill=(48, 48, 46, 255), width=max(1, round(1.4 * s3)))
    paste_card(canvas, crate, (crate.width / 2, crate.height), at(crate_bottom, s3), s3)
    place(canvas, L["hull"], s3)
    for x, y, w, h in SHIMMER:
        pos = at((x, y), s3)
        canvas.alpha_composite(shimmer_sheet(x, y, int(w * s3), int(h * s3), t, x), (int(pos[0] - w * s3 / 2), int(pos[1] - h * s3)))
    for p in PLUMES[2:]:
        card, anc = steam_sheet(p, t, px_m)
        paste_card(canvas, card, anc, at(p["at"], s3), s3)
    # Nessa: still, arms folded; her weight shifts once, slowly
    w8 = smooth((t - 3.6) / 0.8)
    pos = at(META["nessa"]["foot"][:2], s3)
    contact_shadow(canvas, pos, s3)
    a_card, b_card = nessa[0], nessa[1]
    if w8 < 1:
        paste_card(canvas, a_card[0] if w8 == 0 else Image.blend(a_card[0], _match(b_card[0], a_card[0]), w8),
                   a_card[1:], pos, s3)
    else:
        paste_card(canvas, b_card[0], b_card[1:], pos, s3)
    frames = crew_frames()
    walk = META["walk_speed"]
    order = sorted(range(len(META["crew"])), key=lambda i: META["crew"][i]["start"][2], reverse=True)
    for i in order:
        c = META["crew"][i]
        row, delay, ph, k, tint = CREW[i]
        tt = max(0.0, t - delay)
        step = np.array(c["step_1m"][:2]) - np.array(c["start"][:2])
        foot = np.array(c["start"][:2]) + step * walk * tt
        idx = (int(tt / CYCLE_S * 8) + ph) % 8 if t >= delay else ph
        card, ax, ay = frames[row][idx]
        if tint != (1.0, 1.0, 1.0):
            card = _tint(card, tint)
        if k != 1.0:
            card = _resize(card, k)
            ax, ay = ax * k, ay * k
        p = at(foot, s3)
        contact_shadow(canvas, p, s3)
        paste_card(canvas, card, (ax, ay), p, s3)
    for p in PLUMES[:2]:
        card, anc = steam_sheet(p, t, px_m)
        paste_card(canvas, card, anc, at(p["at"], s3), s3)
    return grade(canvas, t)


def _match(b, a):
    out = Image.new("RGBA", a.size)
    out.paste(b, ((a.width - b.width) // 2, a.height - b.height))
    return out


def _tint(card, tint):
    r, g, b, a = card.split()
    r, g, b = (ch.point(lambda v, m=m: min(255, int(v * m))) for ch, m in zip((r, g, b), tint))
    return Image.merge("RGBA", (r, g, b, a))


@lru_cache(None)
def _vignette():
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    r = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    return (1 - 0.10 * np.clip(r - 0.55, 0, None) ** 1.6)[..., None]


def grade(canvas, t):
    a = np.asarray(canvas.convert("RGB"), np.float32)
    a = a * _vignette()
    grain = np.random.default_rng(int(t * FPS) + 7).normal(0, 2.2, (H, W, 1)).astype(np.float32)
    a = np.clip(a + grain, 0, 255)
    return Image.fromarray(a.astype(np.uint8), "RGB")


# ================================================================ runtime contract data

def duration():
    return DURATION


def captions():
    """No dialogue over C5: the silence after C4's "Let me talk first." is the beat."""
    return []


def overlays():
    return []


def audio_cues():
    """(t, sound_id, volume[, until]). Sound ids are built by processAudio from audio.json."""
    return [
        (0.0, "c5_yard_spool_down", 0.55),
        (0.0, "c5_yard_jungle_bed", 0.40, DURATION),
        (0.0, "c5_yard_steam_vent", 0.45, DURATION),
        (1.0, "c5_yard_winch", 0.22),
        (0.4, "c5_yard_hull_ticks", 0.50),
        (1.7, "c5_yard_radio_chatter", 0.20),
        (3.2, "c5_yard_crane_horn", 0.22),
        (3.6, "c5_yard_boots", 0.30),
        (5.0, "c5_yard_winch", 0.22),
    ]


def mix_cues(path):
    """Lay audio_cues() over the shot's length from the retained masters, for the review mp4."""
    n = int(DURATION * 44100)
    mix = np.zeros((n, 2), np.float32)
    for cue in audio_cues():
        t, sid, vol = cue[:3]
        until = cue[3] if len(cue) > 3 else None
        with wave.open(str(AUDIO / (sid + ".wav"))) as w:
            x = np.frombuffer(w.readframes(w.getnframes()), "<i2").reshape(-1, 2).astype(np.float32) / 32767
        a = int(t * 44100)
        if until is not None:
            x = x[: int((until - t) * 44100)]
        e = min(n, a + len(x))
        mix[a:e] += x[: e - a] * vol
    mix /= max(1.0, np.abs(mix).max() / 0.9)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(44100)
        w.writeframes((mix * 32767).astype("<i2").tobytes())


# ================================================================ driver

def _render(i):
    out = OUT / "frames" / ("%05d.png" % i)
    frame_image(i / FPS).save(out, compress_level=3)
    return i


def contact_sheet(frames, path):
    sheet = Image.new("RGB", (W, H), (12, 12, 12))
    cols, rows = 3, 3
    tw, th = W // cols, H // rows
    for k, f in enumerate(frames[: cols * rows]):
        sheet.paste(f.resize((tw - 4, th - 4), Image.LANCZOS), ((k % cols) * tw + 2, (k // cols) * th + 2))
    sheet.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frames", action="store_true")
    ap.add_argument("--mp4", action="store_true")
    ap.add_argument("--workers", type=int, default=8)
    a = ap.parse_args()
    prepare()
    (BASE / "review").mkdir(exist_ok=True)
    stills = []
    for t in (0.0, 2.5, 5.0, 8.0):
        im = frame_image(min(t, DURATION - 1 / FPS))
        im.save(BASE / "review" / ("c5-t%.1f.png" % t))
    n = round(DURATION * FPS)
    for k in range(9):
        stills.append(frame_image(k * (n - 1) / 8 / FPS))
    contact_sheet(stills, BASE / "review" / "c5-contact.png")
    if a.frames:
        (OUT / "frames").mkdir(parents=True, exist_ok=True)
        with Pool(a.workers) as pool:
            for i in pool.imap_unordered(_render, range(n), chunksize=4):
                pass
        print("%d frames in %s" % (n, OUT / "frames"))
    if a.mp4:
        mix_cues(OUT / "c5-cues.wav")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", str(OUT / "frames" / "%05d.png"),
                        "-i", str(OUT / "c5-cues.wav"), "-c:v", "libx264", "-crf", "16", "-pix_fmt", "yuv420p",
                        "-c:a", "aac", "-b:a", "192k", "-shortest", str(OUT / "c5-nessas-yard.mp4")], check=True)
        print("review mp4:", OUT / "c5-nessas-yard.mp4")
    (OUT / "timeline.json").parent.mkdir(parents=True, exist_ok=True)
    (OUT / "timeline.json").write_text(json.dumps(dict(duration=duration(), captions=captions(), overlays=overlays(),
                                                       audio_cues=audio_cues()), indent=1), encoding="utf-8")
    print("C5 stills and contact sheet in review/")


if __name__ == "__main__":
    main()
