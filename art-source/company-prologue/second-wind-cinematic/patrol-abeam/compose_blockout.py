"""C2 PATROL ABEAM + entry transition: animatic-quality blockout stills (c2-layout.md).

python compose_blockout.py

Run make_guides.py and patrol_guide.py (Blender) first. Writes 1920x1080 stills to
review/ at the five moments the layout names, plus a look-test still that drops the one
ImageGen patrol test (prompts/patrol-abeam-lookdev-v1.png) onto the abeam moment.

Layers, back to front, exactly as the layout's table: black space (no stars at this
exposure); Jangala (ray-traced from C1's maps, drifting aft); the patrol (Blender
blockout rendered on its track, true light); the entry glow and cloud cards (stand-ins
cut from entry-window's rendered movie); the fixed realism frame, graded for each
light state. The frame and its glass never move. Nothing here is final art.
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c2_core as C  # noqa: E402
import make_guides as G  # noqa: E402

CIN = BASE.parent
ENTRY_MOVIE = CIN / "entry-window" / "review" / "jangala-entry-window.mp4"
LOOK = BASE / "prompts" / "patrol-abeam-lookdev-v1.png"
REVIEW = BASE / "review"

STILLS = [  # (file, clock, time within the clock, caption)
    ("1-bow-enters", "c2", 3.0, "Approach: \"...cleared to Greywater Down on civilian corridor four.\"  The patrol's bow lobes "
                                "slide in from the left jamb, flank in shadow."),
    ("2-abeam", "c2", 8.0, "Approach: \"Patrol has your signature.\"  Hull centred abeam at 105 m: lit deck and turrets, "
                           "black flank against Jangala."),
    ("3-stern-rising", "c2", 11.4, "Castle: \"Corridor four, mounts cold...\"  De-orbit burn: the patrol pulls ahead and "
                                   "'climbs' out of the glass, engines toward us."),
    ("4-entry-glow", "entry", 0.9, "Castle, off channel: \"Well.\"  Heat glow wipes in from the bow side (right); "
                                   "the frame takes the orange."),
    ("5-entry-cloud", "entry", 2.2, "\"Somebody knows we're here.\"  Cloud wipes over the glow; first soft light "
                                    "in the cabin; C3 opens out of the white."),
]


def check_hail():
    """Refuse to compose against timings that have moved since the layout was written."""
    sys.path.insert(0, str(CIN / "arrival-audio"))
    import build_hail_preview as hail
    lines = json.loads(hail.CATALOG.read_text(encoding="utf-8"))["lines"]
    starts, squelch, _ = hail.timeline(lines)
    opens = [t for kind, t in squelch if kind == "open"]
    got = {"c2_start": opens[2] - 0.3, "line7": starts[4], "line8": starts[5], "line9": starts[6]}
    want = {"c2_start": C.C2_START, "line7": C.HAIL["line7"][0], "line8": C.HAIL["line8"][0], "line9": C.HAIL["line9"][0]}
    for k in want:
        if abs(got[k] - want[k]) > 0.02:
            raise SystemExit("Hail timing moved: %s is %.2f, c2_core says %.2f" % (k, got[k], want[k]))


def entry_frames():
    """Two stills cut from entry-window's movie: its glow (0.0 s) and its cloud (2.0 s)."""
    tmp = Path(tempfile.mkdtemp(prefix="c2-entry-"))
    out = {}
    for name, t in (("glow", 0.0), ("cloud", 2.0)):
        path = tmp / (name + ".png")
        subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-ss", str(t), "-i", str(ENTRY_MOVIE),
                        "-frames:v", "1", str(path)], check=True)
        # Its glass is a different cabin's; cut well inside it and stretch over our opening.
        out[name] = np.asarray(Image.open(path).convert("RGB").crop((330, 10, 1590, 640))
                               .resize((1764 - 157 + 40, 704 + 10), Image.LANCZOS), np.float64) / 255
    return out


def card_on_glass(card):
    canvas = np.zeros((C.H, C.W, 3))
    canvas[0:card.shape[0], 137:137 + card.shape[1]] = card[:, :min(card.shape[1], C.W - 137)]
    return canvas


def graded_frame(state, amount=1.0):
    """The fixed frame under one light state. These are grades standing in for relit plates."""
    f = np.asarray(G.frame_image(), np.float64) / 255
    lum = f @ np.array([0.3, 0.59, 0.11])
    if state == "space":
        # Hard sun only: shadows to black, highlights kept, warm daylight bounce removed.
        g = np.clip(lum * 1.15 - 0.06, 0, 1) ** 1.6
        cool = f * 0.35 + lum[..., None] * 0.65
        return np.clip(cool * (g / np.maximum(lum, 1e-3))[..., None] * 0.85, 0, 1)
    space = graded_frame("space")
    ys, xs = np.mgrid[0:C.H, 0:C.W]
    near_glass = np.clip(1 - (ys - C.SILL_Y).clip(0) / 520.0, 0, 1) ** 1.5
    if state == "glow":
        warm = np.array([1.0, 0.42, 0.12]) * (0.25 + 0.75 * lum[..., None]) * near_glass[..., None] * 0.9
        return np.clip(space + warm * amount, 0, 1)
    if state == "cloud":
        soft = f * 0.9 + 0.08                                  # the first fill light of the sequence
        soft = soft * (0.75 + 0.25 * near_glass[..., None])
        return np.clip(space * (1 - amount) + soft * amount, 0, 1)
    raise ValueError(state)


def bloom(rgb, threshold=0.85, radius=18, gain=0.9):
    hot = np.clip(rgb - threshold, 0, 1)
    img = Image.fromarray((np.clip(hot, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))
    return np.clip(rgb + np.asarray(img, np.float64) / 255 * gain, 0, 1)


def composite(world_rgb, frame_rgb):
    glass = np.asarray(G.glass_alpha(), np.float64)[..., None] / 255
    return world_rgb * glass + frame_rgb * (1 - glass)


def over(dst, src_rgba):
    a = src_rgba[..., 3:4]
    return dst * (1 - a) + src_rgba[..., :3] * a


def patrol_layer(t):
    path = BASE / "guides" / ("patrol-blockout-t%04.1f.png" % t)
    return np.asarray(Image.open(path).convert("RGBA"), np.float64) / 255


def look_layer():
    """The look test, fitted onto the abeam guide's box (it came back reframed, IoU 0.77)."""
    guide = Image.open(BASE / "guides" / "patrol-pose-abeam.png").getchannel("A")
    gx0, gy0, gx1, gy1 = [v / 2 for v in guide.point(lambda a: 255 if a > 128 else 0).getbbox()]
    im = Image.open(LOOK).convert("RGBA")
    im.putalpha(im.getchannel("A").point(lambda a: 0 if a <= 8 else a))
    im = im.crop(im.getchannel("A").point(lambda a: 255 if a > 128 else 0).getbbox())
    im = im.resize((round(gx1 - gx0), round((gx1 - gx0) * im.height / im.width)), Image.LANCZOS)
    canvas = Image.new("RGBA", (C.W, C.H))
    canvas.alpha_composite(im, (round(gx0), round(gy0)))
    return np.asarray(canvas, np.float64) / 255


def caption(img, clock, t, text, tag="BLOCKOUT: grey hull, graded frame, stand-in cards"):
    d = ImageDraw.Draw(img, "RGBA")
    absolute = (C.C2_START if clock == "c2" else C.ENTRY_START) + t
    head = ("C2 %.1f s" % t if clock == "c2" else "ENTRY %.1f s" % t) + "   (%.2f on the hail clock)" % absolute
    d.rectangle((0, 1000, C.W, C.H), fill=(0, 0, 0, 170))
    d.text((24, 1008), head + "   " + tag, fill=(255, 210, 120), font=G.font(22))
    d.text((24, 1040), text, fill=(235, 235, 235), font=G.font(22))
    return img


def still(clock, t, entry=None, look=False):
    space = np.zeros((C.H, C.W, 3))
    world = over(space, np.asarray(G.planet_layer(t if clock == "c2" else C.C2_LEN), np.float64) / 255)
    if clock == "c2":
        world = over(world, look_layer() if look else patrol_layer(t))
        world = bloom(world)
        return composite(world, graded_frame("space"))
    gl = C.smooth((t - C.GLOW_IN[0]) / (C.GLOW_IN[1] - C.GLOW_IN[0]))
    cl = C.smooth((t - C.CLOUD_IN[0]) / (C.CLOUD_IN[1] - C.CLOUD_IN[0]))
    glow = card_on_glass(entry["glow"])
    glow = np.clip(glow * np.array([1.15, 0.62, 0.32]) + np.array([0.25, 0.06, 0.0]), 0, 1)   # hotter than the salvage
    # Hottest toward the bow (right) and low in the glass, where the plasma sheath wraps
    # the hull's leading underside; dimmer and streakier aft.
    ys, xs = np.mgrid[0:C.H, 0:C.W]
    glow = glow * (0.45 + 0.55 * (xs / C.W) ** 0.8 * (0.7 + 0.3 * ys / C.SILL_Y))[..., None]
    cloud = card_on_glass(entry["cloud"]) * 0.85 + 0.15
    gm = G.wipe_mask(gl)[..., None]
    cm = G.wipe_mask(cl, soft=260)[..., None]
    world = world * (1 - gm) + glow * gm
    world = world * (1 - cm) + cloud * cm
    frame = graded_frame("glow", gl * (1 - cl)) if cl < 0.5 else graded_frame("cloud", cl)
    return composite(bloom(world, 0.9, 30, 0.5), frame)


def main():
    check_hail()
    REVIEW.mkdir(exist_ok=True)
    entry = entry_frames()
    for name, clock, t, text in STILLS:
        rgb = still(clock, t, entry)
        img = caption(Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8)), clock, t, text)
        img.save(REVIEW / (name + ".png"))
        print("Wrote", (REVIEW / (name + ".png")).relative_to(BASE))
    if LOOK.exists():
        rgb = still("c2", 8.0, look=True)
        img = caption(Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8)), "c2", 8.0,
                      "LOOK TEST: one ImageGen pass over the abeam guide (prompts/patrol-abeam-lookdev-v1.png), "
                      "fitted to the guide's box.",
                      tag="LOOK TEST: painted hull (one generation), graded frame")
        img.save(REVIEW / "2b-abeam-lookdev.png")
        print("Wrote review/2b-abeam-lookdev.png")


if __name__ == "__main__":
    main()
