"""C4 THE ROAD AND THE PORT: the final composer (c4-layout.md, runtime-contract.md).

    python compose_c4.py            # review stills in review/ (1920x1080) and a contact sheet
    python compose_c4.py --frames   # every frame to build/cinematic/c4-road-and-port/frames,
                                    # timeline.json, and the review mp4 with the voice

Back to front: five registered painted plates (c4_sheets.py) slide at their c4_camera.py
rates; the moving cards (c4_cards.py) ride the layer they belong to, placed from the same
world numbers as the guides, and only translate; the orange-and-cream cabin re-viewed at
C4's camera (c4_cabin_render.py) is fixed on top. Frames carry no text. Captions, overlays
and audio cues are data (captions(), overlays(), audio_cues()); the review mp4 draws the
captions in C1's style for review only.
"""
import argparse
import json
import math
import subprocess
import sys
from functools import lru_cache
from multiprocessing import Pool
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c4_camera as cam  # noqa: E402
import c4_world as world  # noqa: E402

ROOT = cam.ROOT
OUT = ROOT / "build" / "cinematic" / "c4-road-and-port"
W, H, FPS = cam.OUT_W, cam.OUT_H, cam.FPS
DURATION = cam.DURATION
HORIZON_SKY = np.array((204, 218, 232), np.float32)
LAYER = {L["id"]: L for L in cam.LAYERS}
GROUND = ["L1-far", "L2-port", "L3-mid", "L4-near", "L4f-fore"]
CARDS = BASE / "cards"
WORDS = BASE / "c4-nessa-words.json"
VOICE_SOUND = cam.VOICE_ID


# ------------------------------------------------------------------ placement

def screen(lid, x, y, z, t):
    """Screen position of world point (x, y, z) riding layer lid. A pushbroom plate puts world
    X at column f * (x - x_centre) / d_rep whatever the point's depth (build_c4_guides warps X
    by y / d_rep and the level camera divides by y again), and true perspective vertically."""
    L = LAYER[lid]
    sx = cam.plate_x(L, x) / cam.PLATE_SCALE - cam.screen_offset(L, t)
    sy = cam.HORIZON_Y + cam.F_PX * (cam.ALTITUDE - z) / y
    return sx, sy


def hscale(lid):
    """Lateral px per metre on a layer's plate: the pushbroom's f / d_rep."""
    return cam.F_PX / LAYER[lid]["d_rep"]


def vscale(y):
    """Vertical px per metre at true depth y."""
    return cam.F_PX / y


# ------------------------------------------------------------------ assets

# The plates were painted from pre-hazed clay and keep most of the layer's haze; this light
# extra grade restores the humid falloff the layout asks for (c4-layout.md, "Sun and atmosphere").
EXTRA_HAZE = {"L1-far": 0.06, "L2-port": 0.08, "L3-mid": 0.04, "L4-near": 0.0, "L4f-fore": 0.0}


@lru_cache(maxsize=None)
def plate(lid):
    im = Image.open(BASE / "plates" / ("c4-%s-plate.png" % lid)).convert("RGBA")
    h = EXTRA_HAZE[lid]
    if h:
        a = np.asarray(im, np.float32).copy()
        a[..., :3] = a[..., :3] * (1 - h) + HORIZON_SKY * h
        im = Image.fromarray(a.astype(np.uint8), "RGBA")
    return im


@lru_cache(maxsize=1)
def cabin():
    """The cabin card. The projector never saw the top 76 rows, where only the jambs show (the
    window head is above the frame): the jamb paint there continues the first painted row
    upward (a level camera keeps the jambs vertical), with a little of the paint below for
    texture, darkening into the shadow of the head; the glass between the jambs is clear."""
    im = np.asarray(Image.open(BASE / "guides" / "c4-cabin-projected.png").convert("RGBA"), np.float32)
    un = np.asarray(Image.open(BASE / "guides" / "c4-cabin-unpainted.png").convert("L")) > 127
    out = im.copy()
    g0, g1 = int(cam.GLASS_X[0]), int(cam.GLASS_X[1])
    for x in range(W):
        rows = np.where(~un[:, x])[0]
        if len(rows) == 0:
            continue
        first = rows[0]
        if first == 0:
            continue
        first += 4                          # the projection's own edge row is soft; start below it
        if g0 <= x <= g1:
            out[:first, x] = 0
            continue
        ys = np.arange(first)
        smear = np.repeat(im[first:first + 1, x], first, 0)
        tex = im[first + 6 + ys, x]
        mix = smear * 0.72 + tex * 0.28
        shade = (0.62 + 0.38 * (ys / max(1, first)) ** 0.8)[:, None]
        mix[:, :3] *= shade
        mix[:, 3] = 255
        out[:first, x] = mix
    return Image.fromarray(out.astype(np.uint8), "RGBA")


@lru_cache(maxsize=None)
def card(name, height=None, width=None, flip=False, haze=0.0):
    im = Image.open(CARDS / name).convert("RGBA")
    if flip:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    if width:
        height = height or im.height * width / im.width
    elif height:
        width = im.width * height / im.height
    return im, (width or im.width), (height or im.height), haze


def place(canvas, name, cx, base_y, width=None, height=None, flip=False, haze=0.0, anchor="bottom"):
    """Draw a card at a subpixel position: (cx, base_y) is its bottom centre (or centre)."""
    im, w, h, _ = card(name, height, width, flip)
    sw, sh = max(1, math.ceil(w) + 2), max(1, math.ceil(h) + 2)
    x0 = cx - w / 2
    y0 = base_y - h if anchor == "bottom" else base_y - h / 2
    ix, iy = math.floor(x0), math.floor(y0)
    fx, fy = x0 - ix, y0 - iy
    # one resample does scale and the fractional offset together
    sx, sy = im.width / w, im.height / h
    patch = im.transform((sw, sh), Image.AFFINE, (sx, 0, -fx * sx, 0, sy, -fy * sy), Image.BICUBIC)
    if haze:
        a = np.asarray(patch, np.float32)
        a[..., :3] = a[..., :3] * (1 - haze) + HORIZON_SKY * haze
        patch = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")
    canvas.alpha_composite(patch, (ix, iy)) if ix >= 0 and iy >= 0 else _paste_clipped(canvas, patch, ix, iy)


def _paste_clipped(canvas, patch, ix, iy):
    cx0, cy0 = max(0, -ix), max(0, -iy)
    if cx0 >= patch.width or cy0 >= patch.height:
        return
    canvas.alpha_composite(patch.crop((cx0, cy0, patch.width, patch.height)), (ix + cx0, iy + cy0))


def slide(canvas, lid, t):
    """Composite a plate at its subpixel offset for time t."""
    p = plate(lid)
    off = cam.screen_offset(LAYER[lid], t)
    ix = math.floor(off)
    fx = off - ix
    crop = p.crop((ix, 0, ix + W + 2, H))
    if fx > 1e-4:
        crop = crop.transform((W + 1, H), Image.AFFINE, (1, 0, fx, 0, 1, 0), Image.BILINEAR)
    canvas.alpha_composite(crop.crop((0, 0, W, H)))


def overlay_patch(size, draw_fn, blur=0.0):
    im = Image.new("RGBA", size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im))
    return im.filter(ImageFilter.GaussianBlur(blur)) if blur else im


def add_light(canvas, x, y, radius, color, strength):
    """Additive glow dot (daylight: small). strength 0..1 scales the colour at the centre."""
    r = int(radius * 3) + 2
    yy, xx = np.mgrid[-r:r + 1, -r:r + 1].astype(np.float32)
    fx, fy = x - math.floor(x), y - math.floor(y)
    d2 = (xx - fx) ** 2 + (yy - fy) ** 2
    g = np.exp(-d2 / (2 * radius * radius)) * strength
    ix, iy = math.floor(x) - r, math.floor(y) - r
    if ix < 0 or iy < 0 or ix + 2 * r + 1 > W or iy + 2 * r + 1 > H:
        return
    a = np.asarray(canvas, np.float32)
    region = a[iy:iy + 2 * r + 1, ix:ix + 2 * r + 1, :3]
    region += g[..., None] * np.array(color, np.float32)
    a[iy:iy + 2 * r + 1, ix:ix + 2 * r + 1, :3] = np.clip(region, 0, 255)
    canvas.paste(Image.fromarray(a.astype(np.uint8), "RGBA"))


# ------------------------------------------------------------------ moving elements

def rng_noise(seed, n):
    return np.random.default_rng(seed).random(n)


# Haulers on the freight road (L4). Inbound loaded convoy toward the port at 18 m/s,
# centred in the window about t = 8 s; outbound empties pass early and again over the port.
INBOUND = [(524.0 + i * 62.0, "hauler-loaded-%d.png" % i) for i in range(5)]
OUTBOUND = [(438.0 + i * 58.0, "hauler-empty-%d.png" % (5 + i)) for i in range(3)] + \
           [(1426.0 + i * 60.0, "hauler-empty-%d.png" % (5 + i)) for i in range(2)]
HAULER_SPEED = 18.0


def haulers(t):
    out = []
    for x0, name in INBOUND:
        out.append((x0 + HAULER_SPEED * t, world.ROAD_LANES[0], name, False))
    for x0, name in OUTBOUND:
        out.append((x0 - HAULER_SPEED * t, world.ROAD_LANES[1], name, True))
    return out


def draw_haulers(canvas, t):
    for x, lane, name, flip in sorted(haulers(t), key=lambda h: -h[1]):     # far lane first
        sx, sy = screen("L4-near", x, lane, 0, t)
        length = 32.0 * hscale("L4-near")
        if sx + length < 150 or sx - length > W - 150:
            continue
        # contact shadow: the sun is high, behind and to the left, so a short dark band
        # falls just under and slightly right of the skirts
        sh = overlay_patch((int(length) + 12, 10), lambda d: d.rectangle([4, 3, 4 + length, 6], fill=(20, 24, 18, 110)), 1.2)
        canvas.alpha_composite(sh, (int(sx - length / 2 + 2), int(sy - 4)))
        place(canvas, name, sx, sy + 0.6, width=length, flip=flip, haze=0.10)


# Crop tender (L4 fields, Y = 905 m), hovering 2.5 m up and working along the rows at 5 m/s.
TENDER_Y, TENDER_Z, TENDER_SPEED, TENDER_X0 = 905.0, 2.5, 5.0, 410.0


def draw_tender(canvas, t):
    x = TENDER_X0 + TENDER_SPEED * t
    k = hscale("L4-near")
    gx, gy = screen("L4-near", x, TENDER_Y, 0, t)
    if gx < 100 or gx > W + 100:
        return
    span = 24.0 * k
    # mist left behind on the crop, fixed to the ground: older is fainter and wider
    mist = Image.new("RGBA", (int(span * 3) + 40, 24), (0, 0, 0, 0))
    md = ImageDraw.Draw(mist)
    for i in range(14):
        age = i * 0.45
        mx, _ = screen("L4-near", x - TENDER_SPEED * age, TENDER_Y, 0, t)
        a = int(70 * math.exp(-age / 2.2))
        hw = span * (0.42 + 0.03 * age)
        px = mx - (gx - span * 1.5 - 20)
        md.ellipse([px - hw, 9 - 2 - age * 0.3, px + hw, 9 + 3], fill=(228, 236, 228, a))
    mist = mist.filter(ImageFilter.GaussianBlur(2.2))
    canvas.alpha_composite(mist, (int(gx - span * 1.5 - 20), int(gy - 9)))
    # soft shadow on the ground, offset right (sun behind-left)
    sh = overlay_patch((int(span) + 16, 10), lambda d: d.ellipse([6, 3, 6 + span * 0.55, 7], fill=(15, 20, 12, 120)), 1.5)
    canvas.alpha_composite(sh, (int(gx - span * 0.2), int(gy - 4)))
    # the spray fan under the boom, flickering a little
    sx, sy = screen("L4-near", x, TENDER_Y, TENDER_Z, t)
    flick = 0.85 + 0.15 * math.sin(t * 23.0)
    fan = overlay_patch((int(span) + 8, int(gy - sy) + 8),
                        lambda d: d.polygon([(4 + span * 0.04, 2), (4 + span * 0.96, 2), (4 + span, gy - sy + 3),
                                             (4, gy - sy + 3)], fill=(236, 242, 236, int(95 * flick))), 0.8)
    canvas.alpha_composite(fan, (int(sx - span / 2 - 4), int(sy - 1)))
    place(canvas, "tender.png", sx, sy + 1.2, width=span, haze=0.06)


# Lifters on the far launch field (L1). One already climbing; one launching at 7.5 s.
LIFT_A = dict(cradle=world.CRADLES[0], t0=-9.0, acc=0.7, z0=70.0)
LIFT_B = dict(cradle=world.CRADLES[1], t0=7.5, acc=1.2, z0=2.0)
LIFTER_LEN, LIFTER_HAZE = 220.0, 0.42


def lifter_z(L, t):
    return L["z0"] + 0.5 * L["acc"] * max(0.0, t - L["t0"]) ** 2


def draw_plume(canvas, cx, top_y, base_y, width, t, seed, ignite=1.0):
    """Exhaust: a hot core under the engines, then a pale column back down to the cradle."""
    h = max(4, int(base_y - top_y) + 2)            # the column stops at the apron
    pw = int(width * 6) + 20
    im = Image.new("RGBA", (pw, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    mid = pw / 2
    r = rng_noise(seed, 64)
    for i in range(48):                                    # the column, widening with depth below
        f = i / 47
        y = f * (h - 8)
        wob = (r[i % 64] - 0.5) * width * 0.5 * f + math.sin(t * 0.6 + i * 0.7) * width * 0.15 * f
        w = width * (0.35 + 1.9 * f)
        a = int(ignite * (90 - 55 * f))
        d.ellipse([mid + wob - w / 2, y - w * 0.4, mid + wob + w / 2, y + w * 0.4], fill=(236, 238, 240, a))
    im = im.filter(ImageFilter.GaussianBlur(max(1.0, width * 0.18)))
    canvas.alpha_composite(im, (int(cx - mid), int(top_y)))
    core = overlay_patch((int(width * 2) + 8, int(width * 3) + 8),
                         lambda dd: dd.polygon([(4 + width * 0.72, 2), (4 + width * 1.28, 2), (4 + width, 2 + width * 2.2)],
                                               fill=(255, 240, 205, int(220 * ignite))), 1.1)
    keep = int(base_y - (top_y - 2))                # the flame never shows below the apron
    if keep > 0:
        core = core.crop((0, 0, core.width, min(core.height, keep)))
        canvas.alpha_composite(core, (int(cx - width - 4), int(top_y - 2)))
    add_light(canvas, cx, top_y + width * 0.6, width * 0.35, (255, 214, 150), 0.35 * ignite)


def draw_steam(canvas, cx, ground_y, t, t_launch, scale, seed):
    """Launch steam at the cradle: builds from ignition and rolls outward, then thins."""
    age = t - (t_launch - 1.2)
    if age <= 0:
        return
    grow = 1 - math.exp(-age / 1.6)
    fade = math.exp(-max(0.0, age - 5.0) / 6.0)
    r = rng_noise(seed, 90)
    reach = 180.0 * scale * grow                           # metres of lateral roll -> px
    height = 60.0 * scale * grow
    pw, ph = int(reach * 2.6) + 40, int(height * 2.2) + 30
    im = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i in range(30):
        side = -1 if i % 2 else 1
        f = r[i]
        bx = pw / 2 + side * f * reach * (0.6 + 0.4 * grow) + math.sin(t * 0.4 + i) * 2
        by = ph - 12 - r[i + 30] * height
        br = (8 + r[i + 60] * 14) * scale * (0.4 + grow)
        a = int(170 * fade * (0.6 + 0.4 * r[i + 30]))
        d.ellipse([bx - br, by - br * 0.7, bx + br, by + br * 0.7], fill=(240, 240, 238, a))
    im = im.filter(ImageFilter.GaussianBlur(1.6))
    canvas.alpha_composite(im, (int(cx - pw / 2), int(ground_y - ph + 10)))


def draw_lifters(canvas, t):
    for L, seed in ((LIFT_A, 3), (LIFT_B, 7)):
        cx_w, cy_w = L["cradle"]
        z = lifter_z(L, t)
        bx, by = screen("L1-far", cx_w, cy_w, z, t)                 # engines (tail) height
        gx, gy = screen("L1-far", cx_w, cy_w, 0, t)
        k = hscale("L1-far")
        length = LIFTER_LEN * vscale(cy_w)
        width = length * 50 / 300
        if L is LIFT_B:
            draw_steam(canvas, gx, gy, t, L["t0"], k, seed)
        if t >= L["t0"] - 0.6:
            ign = min(1.0, (t - (L["t0"] - 0.6)) / 0.6)
            draw_plume(canvas, bx, by, gy, width, t, seed, ign)
        place(canvas, "lifter.png", bx, by, height=length, haze=LIFTER_HAZE)


# Port: gantry trolleys on L2 and landing lights.
def draw_cranes(canvas, t):
    for i, (cx, cy) in enumerate(world.CRANES_L2):
        ph = t * 0.11 + i * 0.37
        f = 0.5 - 0.5 * math.cos(2 * math.pi * ph)                 # trolley along the boom (outward)
        yb = cy - 70 + 90 * f
        tx, ty = screen("L2-port", cx, yb, 53.0, t)
        hook = 44.0 - 30.0 * (0.5 - 0.5 * math.cos(2 * math.pi * (ph * 2 + 0.25)))
        hx, hy = screen("L2-port", cx, yb, hook, t)
        if not 150 < tx < W - 150:
            continue
        d = ImageDraw.Draw(canvas)
        d.rectangle([tx - 1.2, ty - 0.8, tx + 1.2, ty + 0.8], fill=(52, 52, 50, 255))
        d.line([tx, ty + 0.8, hx, hy], fill=(60, 60, 60, 160), width=1)
        d.rectangle([hx - 1.8, hy, hx + 1.8, hy + 1.4], fill=(150, 72, 50, 255) if i != 1 else (70, 96, 120, 255))


def draw_lights(canvas, t):
    for i, (x, y) in enumerate(world.LIGHT_MASTS_L2):              # pad masts: a slow chase
        phase = (t * 1.1 - i * 0.12) % 1.0
        if phase < 0.18:
            sx, sy = screen("L2-port", x, y, 26.0, t)
            add_light(canvas, sx, sy, 0.9, (255, 70, 50), 0.9 * (1 - phase / 0.18))
    if (t * 0.75) % 1.0 < 0.12:                                    # control tower beacon
        sx, sy = screen("L2-port", 1990.0, 2700.0, 97.0, t)
        add_light(canvas, sx, sy, 1.0, (255, 60, 50), 0.8)
    if (t * 0.55 + 0.3) % 1.0 < 0.1:                               # launch-field beacon mast
        sx, sy = screen("L1-far", 3000.0, 7400.0, 182.0, t)
        add_light(canvas, sx, sy, 0.9, (255, 255, 255), 0.6)


def jungle_occluder(t):
    """The native block on the near side of the road (X 1000-1260 m, Y 840-962 m) stands in
    front of the haulers: re-composite the L4 plate's own trees over them there."""
    a0, _ = screen("L4-near", 1000.0, 960.0, 0, t)
    a1, _ = screen("L4-near", 1260.0, 960.0, 0, t)
    return a0, a1


def frame_image(t):
    canvas = Image.new("RGBA", (W, H), tuple(HORIZON_SKY.astype(int)) + (255,))
    slide(canvas, "L1-far", t)
    draw_lifters(canvas, t)
    slide(canvas, "L2-port", t)
    draw_cranes(canvas, t)
    draw_lights(canvas, t)
    slide(canvas, "L3-mid", t)
    slide(canvas, "L4-near", t)
    draw_haulers(canvas, t)
    a0, a1 = jungle_occluder(t)
    if a1 > 0 and a0 < W:
        top = int(screen("L4-near", 0, world.ROAD_Y[1], 6.0, t)[1])
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        slide(layer, "L4-near", t)
        x0, x1 = max(0, int(a0) + 6), min(W, int(a1) - 6)
        if x1 > x0:
            canvas.alpha_composite(layer.crop((x0, top, x1, H)), (x0, top))
    draw_tender(canvas, t)
    slide(canvas, "L4f-fore", t)
    canvas.alpha_composite(cabin())
    return canvas.convert("RGB")


# ------------------------------------------------------------------ contract data

def audio_cues():
    """(t, sound_id, volume): Castle's Nessa memory, in the cabin, unprocessed (dialogue)."""
    return [(cam.VOICE_IN, VOICE_SOUND, 1.0)]


def overlays():
    return []


def duration():
    return DURATION


def captions():
    """(t_in, t_out, speaker_id, text), paged the way C1 pages a long line (at most two
    rows, split at sentences), each page timed from the take's own word timings."""
    sys.path.insert(0, str(BASE.parent / "corvus-approach"))
    import c1_overlay as c1   # noqa: E402  (its paging and fonts are the shared caption style)
    line = next(l for l in json.loads(cam.CATALOG.read_text(encoding="utf-8"))["lines"] if l["id"] == cam.VOICE_ID)
    words = json.loads(WORDS.read_text(encoding="utf-8"))["words"]
    pages = c1.chunks(line["text"])
    out, i = [], 0
    for n, page in enumerate(pages):
        count = len(page.split())
        first, last = words[i], words[i + count - 1]
        i += count
        t_in = cam.VOICE_IN + first["start"] - 0.12
        out.append([t_in, cam.VOICE_IN + last["end"], "Castle", page])
    for n in range(len(out)):
        nxt = out[n + 1][0] - 0.04 if n + 1 < len(out) else DURATION
        out[n][1] = min(nxt, out[n][1] + c1.TAIL)
    return [tuple(round(v, 3) if isinstance(v, float) else v for v in o) for o in out]


def review_with_captions(img, t):
    """C1's caption drawing over a frame, for the review mp4 and stills only."""
    sys.path.insert(0, str(BASE.parent / "corvus-approach"))
    import c1_overlay as c1   # noqa: E402
    caps = captions()
    sched = [("Castle", caps[0][0], caps[-1][1], [(text, a, b) for a, b, _, text in caps])]
    c1.caption_schedule = lambda duration, _s=sched: _s
    frame = np.asarray(img, np.float32) / 255
    frame = frame.copy()
    c1.draw_captions(frame, t, DURATION)
    return Image.fromarray((np.clip(frame, 0, 1) * 255 + 0.5).astype(np.uint8))


# ------------------------------------------------------------------ outputs

def render(i):
    frame_image(i / FPS).save(OUT / "frames" / ("%05d.png" % i))
    return i


def review_frame(i):
    t = i / FPS
    img = Image.open(OUT / "frames" / ("%05d.png" % i)).convert("RGB")
    review_with_captions(img, t).save(OUT / "review-frames" / ("%05d.jpg" % i), quality=94)
    return i


STILLS = [0.5, 3.0, 6.0, 8.5, 11.4, 13.5, 15.9, 18.2]


def still_path(t):
    return BASE / "review" / (("c4-final-t%05.2f" % t).replace(".", "_") + ".jpg")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frames", action="store_true")
    ap.add_argument("--jobs", type=int, default=8)
    a = ap.parse_args()
    (BASE / "review").mkdir(exist_ok=True)
    n = round(DURATION * FPS)
    print("C4 %.3f s, %d frames; voice at %.2f s" % (DURATION, n, cam.VOICE_IN))
    for t in STILLS:
        review_with_captions(frame_image(t), t).save(still_path(t), quality=93)
    sheet = Image.new("RGB", (1920, 1080 // 4 * 2 * 2))
    for k, t in enumerate(STILLS):
        im = Image.open(still_path(t)).resize((480, 270), Image.LANCZOS)
        sheet.paste(im, ((k % 4) * 480, (k // 4) * 270))
    sheet.crop((0, 0, 1920, 540)).save(BASE / "review" / "c4-final-contact.jpg", quality=93)
    if not a.frames:
        return
    for sub in ("frames", "review-frames"):
        (OUT / sub).mkdir(parents=True, exist_ok=True)
        for old in (OUT / sub).glob("*.*"):
            old.unlink()
    with Pool(a.jobs) as pool:
        for done in pool.imap_unordered(render, range(n), chunksize=6):
            if done % 48 == 0:
                print("frame", done, flush=True)
        list(pool.imap_unordered(review_frame, range(n), chunksize=6))
    timeline = dict(shot="c4-road-and-port", duration=DURATION, fps=FPS, size=[W, H], frames=n,
                    captions=[dict(t_in=a_, t_out=b_, speaker=s_, text=x_) for a_, b_, s_, x_ in captions()],
                    overlays=overlays(), audio_cues=[dict(t=t_, sound=s_, volume=v_) for t_, s_, v_ in audio_cues()])
    (OUT / "timeline.json").write_text(json.dumps(timeline, indent=1) + "\n", encoding="utf-8")
    voice = ROOT / "mod" / "sounds" / "story" / "jangala-arrival" / (VOICE_SOUND + ".ogg")
    for src, name in (("frames/%05d.png", "c4-road-and-port.mp4"), ("review-frames/%05d.jpg", "c4-road-and-port-captioned.mp4")):
        subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", str(FPS), "-i", str(OUT / src),
                        "-i", str(voice), "-filter_complex", "[1:a]adelay=%d|%d,apad[a]" % (cam.VOICE_IN * 1000, cam.VOICE_IN * 1000),
                        "-map", "0:v", "-map", "[a]", "-t", "%.3f" % DURATION, "-c:v", "libx264", "-pix_fmt", "yuv420p",
                        "-crf", "16", "-preset", "slow", "-c:a", "aac", "-b:a", "192k", str(OUT / name)], check=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
