"""C1 CORVUS layered animatic (storyboard-v3.md, c1-layout.md).

python art-source/company-prologue/second-wind-cinematic/corvus-approach/compose_c1.py [--stills] [--stand-ins]

Composes 1920x1080 / 24 fps frames from depth layers that only translate and
scale. Moving elements ride their own layer at that layer's parallax rate, along
keyframed paths. Second Wind makes its entrance from off-screen bottom left and
recedes toward Jangala. Writes build/cinematic/c1-corvus/{frames,c1-corvus.mp4}
and review stills to review/. The audio is the locked hail (lines 1-4) cut from
build_hail_preview.py's cabin mix, so run :asset-pipeline:processAudio first.

Painted layers come from assets/ and layers/ (see c1-layout.md). Where a painted
layer does not exist yet, --stand-ins substitutes the installed game's own
sprites (read from the install, never copied here) so timing can be reviewed.
"""
import argparse
import json
import math
import os
import subprocess
import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
GAME = Path(os.environ.get("STARSECTOR_DIR", r"C:\Program Files (x86)\Fractal Softworks\Starsector")) / "starsector-core" / "graphics"
OUT = ROOT / "build" / "cinematic" / "c1-corvus"
W, H, FPS = 1920, 1080, 24
PUSH = (1.00, 1.07)          # camera scale over the shot
FOCUS = (1000.0, 520.0)      # push centre, between the fleet and Jangala
FLOTILLA_WINDOW = (247, 148, 576, 324)   # frame x, y, w, h shared by both fleet guides
PLANET = (1290.0, 560.0)     # globe centre on the frame (interim disc and render target)
TRUCK = 140.0                # pixels a depth-1.0 layer slides left over the shot
ENTRANCE_NOZZLES = [(255, 815), (370, 815), (1005, 1005), (1085, 1025)]   # bell mouths, 2048x1152 render
STAND_INS = False

sys.path.insert(0, str(BASE.parent / "arrival-audio"))
import build_hail_preview as hail  # noqa: E402


def c1_duration():
    """C1 ends where Approach keys up for line 7, which belongs to C2."""
    lines = json.loads(hail.CATALOG.read_text(encoding="utf-8"))["lines"]
    _, squelch, _ = hail.timeline(lines)
    opens = [t for kind, t in squelch if kind == "open"]
    return opens[2] - 0.3


# ---------------------------------------------------------------- sources

def latest(stem):
    """Highest-numbered accepted master, e.g. assets/station-v3.png."""
    found = [p for p in (BASE / "assets").glob(stem + "-v*.png") if p.stem.rsplit("-v", 1)[1].isdigit()]
    found.sort(key=lambda p: int(p.stem.rsplit("-v", 1)[1]))
    return found[-1] if found else None


def cutout(path, width):
    """A painted card cropped to its subject. ImageGen leaves a faint 1-8 alpha haze across
    the canvas; it would lift black space, so it is cleared before cropping."""
    im = Image.open(path).convert("RGBA")
    im.putalpha(im.getchannel("A").point(lambda a: 0 if a <= 8 else a))
    im = im.crop(im.getchannel("A").getbbox())
    return im.resize((round(width), round(width * im.height / im.width)), Image.LANCZOS)


def sprite(rel, height, heading=0.0, haze=0.0, dim=1.0):
    """Stand-in: a vanilla sprite (bow up) scaled to a height, turned to a fixed heading."""
    im = Image.open(GAME / rel).convert("RGBA")
    im = im.resize((max(1, round(im.width * height / im.height)), round(height)), Image.LANCZOS)
    if heading:
        im = im.rotate(heading, resample=Image.BICUBIC, expand=True)
    rgb, a = im.convert("RGB"), im.getchannel("A")
    rgb = ImageEnhance.Brightness(ImageEnhance.Color(rgb).enhance(1 - 0.35 * haze)).enhance(dim)
    if haze:
        rgb = Image.blend(rgb, Image.new("RGB", rgb.size, (70, 95, 130)), 0.25 * haze)
    rgb.putalpha(a)
    return rgb


def glow_dot(radius, color, strength=1.0):
    size = int(radius * 6) | 1
    yy, xx = np.mgrid[:size, :size] - size // 2
    d = np.sqrt(xx * xx + yy * yy) / radius
    a = np.clip(np.exp(-d * d * 1.6) * strength, 0, 1)
    arr = np.zeros((size, size, 4), np.uint8)
    arr[..., :3] = color
    arr[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def interim_globe(diameter):
    """Placeholder until render_planet.py lands: the earlier plate's texture on a lit disc."""
    src = Image.open(BASE.parent / "space-approach" / "assets" / "planet-plate.png").convert("RGB")
    d = int(diameter)
    tex = np.asarray(src.crop((500, 0, 1400, 900)).resize((d, d), Image.LANCZOS), np.float32) / 255
    yy, xx = (np.mgrid[:d, :d] + 0.5) / d * 2 - 1
    r = np.sqrt(xx * xx + yy * yy)
    z = np.sqrt(np.clip(1 - r * r, 0, 1))
    sun = np.array([-0.55, -0.45, 0.70]); sun /= np.linalg.norm(sun)
    lit = np.clip(xx * sun[0] + yy * sun[1] + z * sun[2], 0, 1)[..., None]
    rgb = tex * (0.04 + 0.96 * lit) + np.array([0.35, 0.55, 0.9]) * (1 - z[..., None]) ** 3 * lit * 0.6
    alpha = np.clip((1 - r) * d / 2, 0, 1)
    arr = np.dstack([np.clip(rgb, 0, 1), alpha])
    return Image.fromarray((arr * 255).astype(np.uint8), "RGBA")


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


class Element:
    """An image on one depth layer. Path keys are (t, cx, cy, scale), centre coordinates,
    eased between keys; with one key the element drifts at (vx, vy) px/s. A child sits
    at an offset in its parent's image pixels and follows the parent's scale."""

    def __init__(self, name, img, depth, keys, vx=0.0, vy=0.0, additive=False, blink=None, parent=None, offset=(0, 0)):
        self.name, self.img, self.depth, self.keys = name, img, depth, keys
        self.vx, self.vy, self.additive, self.blink = vx, vy, additive, blink
        self.parent, self.offset = parent, offset

    def place(self, t):
        if self.parent is not None:
            px, py, ps = self.parent.place(t)
            pw, ph = self.parent.img.size
            return px + (self.offset[0] - pw / 2) * ps, py + (self.offset[1] - ph / 2) * ps, ps
        keys = self.keys
        if len(keys) == 1:
            _, x, y, s = keys[0]
            return x + self.vx * t, y + self.vy * t, s
        if t <= keys[0][0]:
            return keys[0][1:]
        for (t0, x0, y0, s0), (t1, x1, y1, s1) in zip(keys, keys[1:]):
            if t <= t1:
                u = smooth((t - t0) / (t1 - t0))
                return x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, s0 * (s1 / s0) ** u
        return keys[-1][1:]


# ---------------------------------------------------------------- scene

def build_scene(duration):
    els = []

    def add(*a, **k):
        el = Element(*a, **k)
        els.append(el)
        return el

    sky = Image.open(BASE / "assets" / "star-sky-v1.png").convert("RGBA")
    scale = max(2300 / sky.width, 1300 / sky.height)        # cover the overscan
    sky = ImageEnhance.Brightness(sky.convert("RGB")).enhance(0.55).convert("RGBA")   # stars stay behind the hulls
    add("sky", sky.resize((round(sky.width * scale), round(sky.height * scale)), Image.LANCZOS), 0.03,
        [(0, 960, 540, 1.0)])

    # Pulled-back camera: Jangala is a whole distant globe at centre right, and the action
    # plays in open space in front of it.
    planet = BASE / "layers" / "planet.png"
    if planet.exists():
        img = Image.open(planet).convert("RGBA").resize((W, H), Image.LANCZOS)   # registered to the frame
        add("planet", img, 0.04, [(0, 960, 540, 1.0)])
    else:
        add("planet", interim_globe(760), 0.04, [(0, PLANET[0], PLANET[1], 1.0)])

    # The battlegroup: two silhouette-guided layers (flotilla_guides.py) registered to one
    # frame window, every bow on the same heading toward the inbound lane.
    flotilla = None
    for stem, depth in (("heg-flotilla-far", 0.07), ("heg-flotilla-mid", 0.09)):
        path = BASE / "assets" / (stem + "-v2-registered.png")
        path = path if path.exists() else latest(stem)
        if path:
            layer = Image.open(path).convert("RGBA").resize(FLOTILLA_WINDOW[2:], Image.LANCZOS)
            layer.putalpha(layer.getchannel("A").point(lambda a: 0 if a <= 8 else a))
            cx = FLOTILLA_WINDOW[0] + FLOTILLA_WINDOW[2] / 2
            cy = FLOTILLA_WINDOW[1] + FLOTILLA_WINDOW[3] / 2
            flotilla = add(stem, layer, depth, [(0, cx, cy, 1.0)], vx=-0.8, vy=0.2)

    # Jangala Station just off the planet's upper-left limb, small at this distance.
    station = latest("station")
    img = cutout(station, 82) if station else sprite("stations/station_jangala.png", 128, 0, haze=0.15)
    st = add("station", img, 0.06, [(0, 905, 300, 1.0)], vx=0.4)
    for i, (ox, oy) in enumerate([(img.width * 0.45, img.height * 0.2), (img.width * 0.55, img.height * 0.8)]):
        add("station-light-%d" % i, glow_dot(1.6, (255, 235, 200)), 0.06, None, additive=True,
            blink=(1.7, i * 0.6), parent=st, offset=(ox, oy))

    hero = latest("heg-hero")
    if hero:
        add("heg-hero", cutout(hero, 240), 0.2, [(0, 1560, 74, 1.0)], vx=-11.0, vy=0.2)
    if not flotilla and STAND_INS:
        for i, (rel, h, x, y) in enumerate([("ships/onslaught/onslaught_hegemony.png", 110, 560, 300),
                                            ("ships/dominator/dominator_hegemony.png", 64, 460, 380),
                                            ("ships/eagle/eagle_hegemony.png", 46, 650, 240)]):
            add("heg-%d" % i, sprite(rel, h, heading=140.0, haze=0.3, dim=0.92), 0.08, [(0, x, y, 1.0)],
                vx=-0.8, vy=0.2)

    # Traffic toward the planet: the hero Atlas crosses in front of the lit disc; small
    # freighters on a farther lane; a shuttle drops from the station toward the surface.
    atlas = latest("traffic-atlas")
    if atlas:
        add("atlas", cutout(atlas, 230), 0.18, [(0, -220, 600, 1.0)], vx=36.0, vy=-2.5)
    # A convoy on the Atlas's lane, clear of the battlegroup above it.
    lane = [("tarsus", 80, 160, 520, 0.14, 30, -1.6), ("mule", 70, -420, 490, 0.13, 30, -1.6),
            ("buffalo", 64, -950, 560, 0.14, 31, -1.8), ("shuttle", 40, 930, 360, 0.07, 9, 6.0)]
    small = []
    for name, width, x, y, depth, vx, vy in lane:
        path = BASE / "assets" / ("traffic-small-v1-%s.png" % name)
        if path.exists():
            small.append(add("lane-" + name, cutout(path, width), depth, [(0, x, y, 1.0)], vx=vx, vy=vy))
    if not small and STAND_INS:
        for i, (rel, h, x, y, v) in enumerate([("ships/tarsus.png", 40, -260, 470, 24),
                                               ("ships/buffalo_dd.png", 34, -640, 500, 22)]):
            add("lane-%d" % i, sprite(rel, h, heading=-90, haze=0.5, dim=0.85), 0.12, [(0, x, y, 1.0)],
                vx=v, vy=0.4)

    # Second Wind's entrance: from off-screen bottom left, close and large, receding toward
    # Jangala. The rear three-quarter card keeps one viewing angle because the ship travels
    # away along roughly the camera's own line of sight.
    card = Image.open(BASE / "layers" / "second-wind-entrance.png").convert("RGBA")
    box = card.getchannel("A").getbbox()
    card = card.crop(box)
    base_w = 760.0
    k = base_w / card.width
    card = card.resize((round(base_w), round(card.height * k)), Image.LANCZOS)
    sw = add("second-wind", card, 0.6, [(0.0, -300, 1330, 1.6),
                                         (6.5, 470, 820, 0.78),
                                         (duration, 760, 700, 0.42)])
    for i, (nx, ny) in enumerate(ENTRANCE_NOZZLES):
        add("sw-engine-%d" % i, glow_dot(16, (150, 205, 255), 0.9), 0.6, None, additive=True,
            parent=sw, offset=((nx - box[0]) * k, (ny - box[1]) * k))
    return els


# ---------------------------------------------------------------- rendering

SCENE = None


def frame_image(t, duration):
    global SCENE
    if SCENE is None:
        SCENE = build_scene(duration)
    u = t / duration
    cam = PUSH[0] + (PUSH[1] - PUSH[0]) * smooth(u)
    canvas = np.zeros((H, W, 3), np.float32)
    for el in SCENE:
        if el.blink is not None and ((t + el.blink[1]) % el.blink[0]) > el.blink[0] * 0.18:
            continue
        cx, cy, s_el = el.place(t)
        cx -= TRUCK * el.depth * u
        zoom = cam ** (0.4 + 0.6 * el.depth / 0.6)      # nearer layers feel the push more
        s = s_el * zoom
        sx, sy = FOCUS[0] + (cx - FOCUS[0]) * zoom, FOCUS[1] + (cy - FOCUS[1]) * zoom
        w, h = el.img.size
        x0, y0 = sx - w * s / 2, sy - h * s / 2
        bx0, by0 = max(0, math.floor(x0)), max(0, math.floor(y0))
        bx1, by1 = min(W, math.ceil(x0 + w * s)), min(H, math.ceil(y0 + h * s))
        if bx1 <= bx0 or by1 <= by0:
            continue
        # Inverse affine into just the covered box: sub-pixel placement, no stepping.
        tile = el.img.transform((bx1 - bx0, by1 - by0), Image.AFFINE,
                                (1 / s, 0, (bx0 - x0) / s, 0, 1 / s, (by0 - y0) / s),
                                resample=Image.BICUBIC, fillcolor=(0, 0, 0, 0))
        arr = np.asarray(tile, np.float32) / 255
        rgb, a = arr[..., :3], arr[..., 3:4]
        region = canvas[by0:by1, bx0:bx1]
        canvas[by0:by1, bx0:bx1] = region + rgb * a if el.additive else region * (1 - a) + rgb * a
    return Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8))


def init(stand_ins):
    global STAND_INS
    STAND_INS = stand_ins


def render(index, duration):
    frame_image(index / FPS, duration).save(OUT / "frames" / ("%05d.png" % index))
    return index


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stills", action="store_true", help="only the review stills")
    parser.add_argument("--stand-ins", action="store_true", help="vanilla sprites where painted layers are missing")
    args = parser.parse_args()
    init(args.stand_ins)
    duration = c1_duration()
    frames = round(duration * FPS)
    (BASE / "review").mkdir(exist_ok=True)
    for name, t in (("entrance", 2.5), ("settle", 7.0), ("mid", duration / 2), ("end", duration - 1 / FPS)):
        frame_image(t, duration).save(BASE / "review" / (name + ".png"))
    print("C1 %.2fs, %d frames; stills in review/" % (duration, frames))
    if args.stills:
        return
    (OUT / "frames").mkdir(parents=True, exist_ok=True)
    for old in (OUT / "frames").glob("*.png"):
        old.unlink()
    with ProcessPoolExecutor(max(1, (os.cpu_count() or 2) // 2), initializer=init, initargs=(args.stand_ins,)) as pool:
        for done in pool.map(render, range(frames), [duration] * frames, chunksize=8):
            if done % 120 == 0:
                print("frame", done, flush=True)
    hail.main()
    audio = ROOT / "build" / "audio-previews" / "jangala-hail" / "cabin.mp3"
    movie = OUT / "c1-corvus.mp4"
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", str(FPS),
                    "-i", str(OUT / "frames" / "%05d.png"), "-i", str(audio),
                    "-filter_complex", "[1:a]atrim=0:%.3f,afade=t=out:st=%.3f:d=0.6[a]" % (duration, duration - 0.6),
                    "-map", "0:v", "-map", "[a]", "-c:v", "libx264", "-crf", "18", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "160k", "-shortest", str(movie)], check=True)
    print("Wrote", movie.relative_to(ROOT))


if __name__ == "__main__":
    main()
