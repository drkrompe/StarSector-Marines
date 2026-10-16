"""C3 FIRST OPEN SKY blockout animatic (storyboard-v3.md, c3-layout.md).

python compose_c3.py            # review stills only (review/*.png, 1920x1080)
python compose_c3.py --movie    # also every frame and first-open-sky/build/c3-blockout.mp4

Run c3_cabin_guide.py, c3_cabin_render.py (Blender) and c3_bands.py first. Every layer
only translates and scales. Rates come from c3_core.py's camera and distances. The
plates are the salvaged 1672x941 masters upscaled to the frame, so this is a motion and
layout check, not plate-resolution art: see c3-layout.md, "Plate resolution".
"""
import argparse
import math
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parent))
from c3_core import (BASE, CABIN_DEPTH_M, CIN, DURATION, F_PX, FPS, GROUND_SPEED, H, HORIZON_Y, LAYERS,  # noqa: E402
                     MASTER, PLATE_SCALE, PLATE_X0, PLATE_Y0, SINK_RATE, W, lean_scale, rise_px, smooth,
                     truck_px)

G = BASE / "guides"
EXTRACT = CIN / "forest-reveal-realism" / "assets"
OUT = BASE / "build"
DEBUG = False


def arr(img):
    return np.asarray(img, np.float32) / 255.0


def img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))


def shift(a, dx, dy):
    out = np.zeros_like(a)
    h, w = a.shape[:2]
    ys0, ys1 = max(0, dy), min(h, h + dy)
    xs0, xs1 = max(0, dx), min(w, w + dx)
    out[ys0:ys1, xs0:xs1] = a[ys0 - dy:ys1 - dy, xs0 - dx:xs1 - dx]
    return out


# ---------------------------------------------------------------- layer sources

def band_layers():
    """The approved master cut into bands; behind a nearer band, the salvaged
    extraction's completed content continues (registered by c3_bands.py)."""
    import json
    reg = json.loads((G / "c3-bands.json").read_text(encoding="utf-8"))["registration_px"]
    master = arr(Image.open(MASTER).convert("RGB"))
    mask = {n: arr(Image.open(G / ("c3-band-%s.png" % n)).convert("L")) > 0.5
            for n in ("mountains", "far", "mid", "near")}
    ex = {}
    for n in ("far", "mid", "near"):
        e = arr(Image.open(EXTRACT / ("%s-realism-v1.png" % n)).convert("RGBA"))
        ex[n] = shift(e, reg[n]["dx"], reg[n]["dy"])
    out = {}

    def soft(m, r=1.0):
        return arr(img(m.astype(np.float32)).filter(ImageFilter.GaussianBlur(r)))

    # Mountains: master range, extended 14 px under the ridge crests it sits behind
    # (the revealed sliver is a Codex fill; here it is the crest pixels, see inpaint guide).
    m = mask["mountains"]
    grow = np.zeros_like(m)
    for k in range(1, 15):
        grow[k:] |= m[:-k]
    a = soft(m | (grow & (ex["far"][..., 3] > 0.5)))
    out["mountains"] = np.dstack([master, a])
    # Far ridges: master where shown, far extraction where continued behind mid/near;
    # the range is cut away so it can ride its own, slower layer.
    rgb = np.where(mask["far"][..., None], master, ex["far"][..., :3])
    # Everything of the far extraction above the range's foot belongs to the range: its
    # skyline is registered only to a pixel or two, and a sliver left on the faster far
    # layer reads as a second skyline sliding through the sky.
    rows = np.arange(m.shape[0])[:, None]
    foot = np.where(m.any(0), m.shape[0] - 1 - np.argmax(m[::-1], axis=0), 0)[None, :]
    a = ex["far"][..., 3] * (1 - soft(m)) * (rows > foot)
    out["far"] = np.dstack([rgb, a])
    for n in ("mid", "near"):
        rgb = np.where(mask[n][..., None], master, ex[n][..., :3])
        out[n] = np.dstack([rgb, ex[n][..., 3]])
    return out


def mist_card(band_rgba, seed):
    """Soft mist wisps from the master's own mist in that band: a translucent card that
    can drift against the ridges it lies between."""
    rgb, a = band_rgba[..., :3], band_rgba[..., 3]
    lum = rgb @ np.array([0.299, 0.587, 0.114], np.float32)
    sat = rgb.max(-1) - rgb.min(-1)
    water = (((rgb[..., 2] - rgb[..., 0]) - 0.04) * 25).clip(0, 1)   # the river reflects sky: blue leads red
    water = (arr(img(water).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(6))) * 2).clip(0, 1)
    m = ((lum - 0.55) * 4).clip(0, 1) * ((0.2 - sat) * 8).clip(0, 1) * (a > 0.5) * (1 - water.clip(0, 1))
    m = arr(img(m).filter(ImageFilter.GaussianBlur(6)))
    m = (m * 1.6).clip(0, 0.55)
    col = np.ones_like(rgb) * np.array([0.90, 0.93, 0.95], np.float32)
    return np.dstack([col, m])


def sky_layer():
    return arr(Image.open(CIN / "managed-land-realism" / "assets" / "sky-realism-v1.png").convert("RGBA"))


def atlas_card(length_px):
    """Identity stand-in: the earlier painted side view (bow right). Haze for ~2.2 km."""
    im = Image.open(CIN / "parallax-window" / "traffic" / "atlas-painted-v1.png").convert("RGBA")
    im = im.crop(im.getchannel("A").getbbox())
    im = im.resize((round(length_px), round(length_px * im.height / im.width)), Image.LANCZOS)
    a = arr(im)
    haze = np.array([0.70, 0.80, 0.90], np.float32)
    a[..., :3] = a[..., :3] * 0.82 + haze * 0.18
    return a


def cabin_layer():
    """The approved painted cabin re-viewed from C3's camera (c3_cabin_render.py). The
    band the projector never saw (above y=213) comes from the Codex completion
    assets/c3-cabin-top-v1.png when present, else from the greybox guide.

    The completion is registered to 1 px below y=213 but drew a deeper window head than
    the geometry: its glass starts at y~169 where the cabin's is at 77. Its band is
    therefore re-registered vertically: at the window head it is lifted by that
    measured error, easing to no lift at y=213, so the rim lands on the true aperture
    and the jamb paint joins the projected paint without a seam. The jambs between
    take a vertical stretch (bolts there read slightly tall); a v2 brief with the rim
    drawn into the guide is the clean fix (c3-layout.md, open question 3)."""
    cab = arr(Image.open(G / "c3-cabin-projected-p09.png").convert("RGBA"))
    un = arr(Image.open(G / "c3-cabin-unpainted-p09.png").convert("L"))
    seam = int(np.argmin(un[:, W // 2] > 0.5))                 # first painted row (213)
    head = int(np.argmax(cab[:, W // 2, 3] < 0.5))              # geometric aperture top (77)
    done = BASE / "assets" / "c3-cabin-top-v1.png"
    if done.exists():
        gen = arr(Image.open(done).convert("RGB").resize((W, H), Image.LANCZOS))
        glass = np.abs(gen - np.array([149, 168, 182], np.float32) / 255).sum(-1) < 30 / 255
        gen_head = int(np.argmax(glass[:, W // 2]))
        lift = gen_head - head
        yy = np.arange(seam, dtype=np.float32)
        src = yy + lift * np.clip((seam - yy) / max(1, seam - head), 0, 1)
        top = gen.copy()
        top[:seam] = gen[np.clip(np.round(src).astype(int), 0, H - 1)]
    else:
        top = arr(Image.open(G / "c3-cabin-geometry-p09.png").convert("RGB")) * np.array([1.25, 1.05, 0.85], np.float32)
    yy = np.arange(H, dtype=np.float32)[:, None]
    w = ((seam + 6 - yy) / 12).clip(0, 1) * np.ones((1, W), np.float32)
    w = np.maximum(w, un)
    rgb = cab[..., :3] * (1 - w[..., None]) + top * w[..., None]
    return np.dstack([rgb, cab[..., 3]])


def bird_frame(phase, span):
    """One small dark flier, wings at a flap phase (0..1). A procedural sheet frame."""
    k = 4                                            # drawn at 4x, then reduced
    s = int(span * k * 1.4 + 8)
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c, sp = s / 2, span * k
    lift = math.sin(phase * 2 * math.pi) * sp * 0.35
    col = (24, 27, 26, 255)
    d.line([(c - sp / 2, c - lift), (c, c + sp * 0.05), (c + sp / 2, c - lift)], fill=col, width=max(2, round(sp / 7)))
    return arr(im.resize((max(1, s // k), max(1, s // k)), Image.LANCZOS))


# ---------------------------------------------------------------- scene

class Layer:
    def __init__(self, name, rgba, scale, x, y, vx=0.0, vy=0.0, additive=False, alpha=None, path=None,
                 world=True):
        self.name, self.rgba, self.scale, self.x, self.y = name, rgba, scale, x, y
        self.vx, self.vy, self.additive, self.alpha, self.path, self.world = vx, vy, additive, alpha, path, world


def build():
    L = []
    D = {n: d for n, d, _ in LAYERS}
    bands = band_layers()
    sky = sky_layer()
    # Sky rides the horizon layer (40 km) with the range. Its pale base sits on the
    # horizon. The plate's upper clouds are really ~5 km away; c3-layout.md plans them as
    # a cumulus row on the far layer, which this blockout does not split out yet.
    sky_scale = 1.25
    sky_y = HORIZON_Y + 90 - sky.shape[0] * sky_scale
    L.append(Layer("sky", sky, sky_scale, -60, sky_y, vx=-truck_px(D["horizon"]) / DURATION))
    for n, d in (("mountains", D["horizon"]), ("far", D["far"])):
        L.append(Layer(n, bands[n], PLATE_SCALE, PLATE_X0, PLATE_Y0,
                       vx=-truck_px(d) / DURATION, vy=-rise_px(d) / DURATION))
    # high mist: between the far ridges (~4.5 km), carried 1 m/s aft by the breeze
    mh = mist_card(bands["far"], 1)
    L.append(Layer("mist-high", mh, PLATE_SCALE, PLATE_X0, PLATE_Y0 + 6,
                   vx=(-truck_px(4500) - F_PX * 1.0 * DURATION / 4500) / DURATION, vy=-rise_px(4500) / DURATION))
    L.append(Layer("mid", bands["mid"], PLATE_SCALE, PLATE_X0, PLATE_Y0,
                   vx=-truck_px(D["mid"]) / DURATION, vy=-rise_px(D["mid"]) / DURATION))
    # Atlas: 300 m hull at 2.2 km (190 px), bow right, climbing out at 60 m/s and 8 m/s up.
    atlas_d = 2200.0
    atlas = atlas_card(F_PX * 300 / atlas_d)
    own = F_PX * 60.0 / atlas_d
    L.append(Layer("atlas", atlas, 1.0, 520, 205, vx=-F_PX * GROUND_SPEED / atlas_d + own,
                   vy=-F_PX * (8.0 + SINK_RATE) / atlas_d))
    ml = mist_card(bands["mid"], 2)
    L.append(Layer("mist-low", ml, PLATE_SCALE, PLATE_X0, PLATE_Y0 + 4,
                   vx=(-truck_px(1700) - F_PX * 1.0 * DURATION / 1700) / DURATION, vy=-rise_px(1700) / DURATION))
    L.append(Layer("near", bands["near"], PLATE_SCALE, PLATE_X0, PLATE_Y0,
                   vx=-truck_px(D["near"]) / DURATION, vy=-rise_px(D["near"]) / DURATION))
    # Flock: eleven 3 m fliers ~700 m out at about our altitude, so they cross the bright
    # low sky; flying aft at 8 m/s, they pass the ship and enter from behind the right jamb.
    flock_d = 700.0
    span = F_PX * 3.0 / flock_d * 1.4          # 5.9 px true; drawn 1.4x so a wingbeat reads at 1080p
    rng = np.random.default_rng(11)
    birds = [(rng.uniform(-70, 70), rng.uniform(-14, 14), rng.uniform(0, 1), rng.uniform(0.8, 1.15)) for _ in range(11)]
    vx_f = (-F_PX * GROUND_SPEED - F_PX * 8.0) / flock_d
    for i, (bx, by, ph, sz) in enumerate(birds):
        def frames(t, ph=ph, sz=sz):
            return bird_frame((t * 3.2 + ph) % 1.0, span * sz)
        L.append(Layer("bird%d" % i, None, 1.0, 1900 + bx - vx_f * 2.5, 255 + by, vx=vx_f, vy=-0.8, path=frames))
    # The entry's last cloud: the transition ends inside a thinning cloud, which slides
    # aft at ~800 m depth and burns off over the first 1.6 s (c3-layout.md, "Pickup").
    cloud = arr(Image.open(CIN / "managed-land-realism" / "assets" / "clouds-realism-v1.png").convert("RGBA"))
    L.append(Layer("entry-cloud", cloud, 2.4, -1250, -560, vx=-F_PX * GROUND_SPEED / 800.0,
                   alpha=lambda t: 1 - smooth(t / 1.6)))
    L.append(Layer("cabin", cabin_layer(), 1.0, 0, 0, world=False))
    return L


SCENE = None


def jitter(t):
    """Cabin vibration easing: a few px of world shake decaying over the first 3 s."""
    amp = 2.4 * math.exp(-t / 0.9)
    return amp * math.sin(t * 37.0) + 0.6 * amp * math.sin(t * 23.0 + 1.3), amp * 0.7 * math.sin(t * 29.0 + 0.4)


def frame(t):
    global SCENE
    if SCENE is None:
        SCENE = build()
    canvas = np.zeros((H, W, 3), np.float32)
    jx, jy = jitter(t)
    for L in SCENE:
        src = L.path(t) if L.path is not None else L.rgba
        h, w = src.shape[:2]
        if L.world:
            x, y, s = L.x + L.vx * t + jx, L.y + L.vy * t + jy, L.scale
            # the lean moves the eye 5 cm toward km-distant layers: scale 1.00004, kept for honesty
        else:
            s = L.scale * lean_scale(t, CABIN_DEPTH_M)
            x = W / 2 + (L.x - W / 2) * s / L.scale
            y = H / 2 + (L.y - H / 2) * s / L.scale
        bx0, by0 = max(0, math.floor(x)), max(0, math.floor(y))
        bx1, by1 = min(W, math.ceil(x + w * s)), min(H, math.ceil(y + h * s))
        if bx1 <= bx0 or by1 <= by0:
            continue
        tile = img(src).transform((bx1 - bx0, by1 - by0), Image.AFFINE,
                                  (1 / s, 0, (bx0 - x) / s, 0, 1 / s, (by0 - y) / s),
                                  resample=Image.BICUBIC, fillcolor=(0, 0, 0, 0))
        a = arr(tile)
        rgb, al = a[..., :3], a[..., 3:4]
        if L.alpha is not None:
            al = al * L.alpha(t)
        if DEBUG and L.name in TINT:
            rgb = rgb * 0.45 + np.array(TINT[L.name], np.float32) * 0.55
        reg = canvas[by0:by1, bx0:bx1]
        canvas[by0:by1, bx0:bx1] = reg * (1 - al) + rgb * al
    return img(canvas)


TINT = {"sky": (0.35, 0.6, 1.0), "mountains": (0.6, 0.45, 1.0), "far": (1.0, 0.8, 0.25),
        "mist-high": (1, 1, 1), "mid": (0.25, 0.85, 0.45), "atlas": (1.0, 0.3, 0.9),
        "mist-low": (1, 1, 1), "near": (1.0, 0.35, 0.25)}


def label(im, text):
    d = ImageDraw.Draw(im)
    d.rectangle([0, H - 34, W, H], fill=(0, 0, 0))
    d.text((14, H - 26), text, fill=(235, 235, 235))
    return im


_D = {n: d for n, d, _ in LAYERS}


def _slid(t):
    u = t / DURATION
    return "near %d px, mid %d, far %d, horizon %d" % tuple(round(truck_px(_D[n]) * u) for n in ("near", "mid", "far", "horizon"))


STILLS = [
    ("01-pickup", 0.0, "t=0.0s  pickup from the entry tail: its last cloud burning off and sliding aft, world shake still in it"),
    ("02-settle", 4.0, "t=4.0s  vibration gone; slid so far: " + _slid(4.0)),
    ("03-lean", 8.0, "t=8.0s  lean nearly complete: cabin x1.04, the frame widens on the sky; Atlas climbing right, flock in from the right"),
    ("04-out", DURATION - 1 / FPS, "t=12.0s  out; slid: " + _slid(DURATION) + "; the cut to C4 continues the leftward flow"),
]


def main():
    global DEBUG
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--movie", action="store_true")
    args = ap.parse_args()
    rev = BASE / "review"
    rev.mkdir(exist_ok=True)
    for name, t, text in STILLS:
        label(frame(t), "C3 blockout  " + text).save(rev / ("c3-%s.png" % name))
    DEBUG = True
    label(frame(6.0), "C3 band check t=6.0s  sky / range / far / mid / near / Atlas tinted; mist white; cabin untinted").save(
        rev / "c3-05-bands.png")
    DEBUG = False
    # a parallax proof: t=0 and t=12 differenced, so rates read as smear length
    a, b = arr(frame(0.0)), arr(frame(DURATION - 1 / FPS))
    proof = np.dstack([a.mean(-1), b.mean(-1), b.mean(-1)])
    label(img(proof), "C3 parallax proof, t=0 vs t=12: red channel t0, cyan t12 (a dark body shows cyan where it was, red where it is). Offset grows with nearness; the cabin moves only by the lean").save(
        rev / "c3-06-parallax-proof.png")
    print("stills in", rev)
    if not args.movie:
        return
    fr = OUT / "frames"
    fr.mkdir(parents=True, exist_ok=True)
    n = round(DURATION * FPS)
    for i in range(n):
        frame(i / FPS).save(fr / ("%04d.png" % i))
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", str(FPS), "-i", str(fr / "%04d.png"),
                    "-c:v", "libx264", "-crf", "18", "-pix_fmt", "yuv420p", str(OUT / "c3-blockout.mp4")], check=True)
    print("wrote", OUT / "c3-blockout.mp4")


if __name__ == "__main__":
    main()
