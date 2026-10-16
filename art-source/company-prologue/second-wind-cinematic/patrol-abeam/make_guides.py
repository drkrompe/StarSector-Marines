"""C2 PATROL ABEAM and the entry transition: 2D guides (c2-layout.md).

python make_guides.py

Writes to guides/:
  window-mask.png, window-mask-3840.png   the fixed frame's glass opening: frame opaque,
                                          glass transparent (the alpha every layer sits
                                          behind); measured off realism-lookdev
  window-mask-check.png                   the mask over the realism frame, glass shown
                                          magenta, to prove registration
  planet-blockout.png                     Jangala from 200 km through the C2 camera:
                                          horizon, sunlit surface and limb (C1's own
                                          albedo and cloud maps, read-only)
  c2-timeline.png                         hail lines 7-9, the patrol's pass and the
                                          transition on one clock
  transition-timing.png                   the entry wipes: opacity curves and the glass
                                          at seven moments
Pure Python + Pillow + numpy; nothing is paid, nothing outside this folder is written.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c2_core as C  # noqa: E402

CIN = BASE.parent
GUIDES = BASE / "guides"
FRAME_MASTER = CIN / "realism-lookdev" / "window-realism-v1.png"
ALBEDO = CIN / "corvus-approach" / "assets" / "planet-albedo-detail-v1.png"
CLOUDS = CIN / "corvus-approach" / "assets" / "planet-clouds-detail-v1.png"


def font(size):
    for name in ("C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf"):
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default()


# ---------------------------------------------------------------- the window

def glass_alpha(scale=1, supersample=4):
    """1.0 where the glass is (world shows through), 0.0 on the frame; anti-aliased."""
    s = scale * supersample
    im = Image.new("L", (C.W * s, C.H * s), 0)
    ImageDraw.Draw(im).polygon([(x * s, y * s) for x, y in C.APERTURE], fill=255)
    return im.resize((C.W * scale, C.H * scale), Image.LANCZOS)


def frame_image():
    """The realism frame at 1920x1080 (the 1672x941 master upscaled; no native detail added)."""
    return Image.open(FRAME_MASTER).convert("RGB").resize((C.W, C.H), Image.LANCZOS)


def write_masks():
    for scale, name in ((1, "window-mask.png"), (2, "window-mask-3840.png")):
        glass = glass_alpha(scale)
        frame = Image.new("RGBA", glass.size, (255, 255, 255, 255))
        frame.putalpha(Image.eval(glass, lambda a: 255 - a))
        frame.save(GUIDES / name)
    check = frame_image().convert("RGBA")
    tint = Image.new("RGBA", check.size, (255, 0, 200, 0))
    tint.putalpha(Image.eval(glass_alpha(), lambda a: int(a * 0.55)))
    check.alpha_composite(tint)
    d = ImageDraw.Draw(check)
    d.polygon(C.APERTURE, outline=(0, 255, 120), width=2)
    d.text((24, 1030), "glass opening (magenta) measured on realism-lookdev/window-realism-v1.png:"
           "  jambs x = 157 + 0.172y and 1764 - 0.172y, sill y = 704", fill=(255, 255, 255), font=font(22))
    check.convert("RGB").save(GUIDES / "window-mask-check.png")


# ---------------------------------------------------------------- Jangala

def bilerp(img, u, v):
    """Bilinear sample of an HxWxC float array at fractional pixel coordinates."""
    u0, v0 = np.floor(u).astype(int), np.floor(v).astype(int)
    fu, fv = (u - u0)[..., None], (v - v0)[..., None]
    a, b = img[v0, u0], img[v0, u0 + 1]
    c, d = img[v0 + 1, u0], img[v0 + 1, u0 + 1]
    return (a * (1 - fu) + b * fu) * (1 - fv) + (c * (1 - fu) + d * fu) * fv


def planet_layer(t=0.0, w=C.W, h=C.H):
    """Jangala through the C2 camera at C2 time t: ray-traced sphere, C1's maps.

    The surface drifts aft (right to left) with the orbit; the horizon does not move.
    Returns RGBA (alpha 0 in empty space, which stays black: no stars at this exposure)."""
    fwd, right, up = (np.array(v) for v in C.basis())
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    a = (xs + 0.5 - w / 2) * (C.W / w) / C.F_PX
    b = (h / 2 - ys - 0.5) * (C.H / h) / C.F_PX
    d = fwd[None, None] + a[..., None] * right + b[..., None] * up
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    R, alt = C.PLANET_RADIUS_M, C.ALTITUDE_M
    centre = np.array([0.0, 0.0, -(R + alt)])
    oc = -centre
    bq = d @ oc
    cq = oc @ oc - R * R
    disc = bq * bq - cq
    hit = disc > 0
    tt = np.where(hit, -bq - np.sqrt(np.maximum(disc, 0)), 0)
    hit &= tt > 0
    p = d * tt[..., None] - centre                        # point on the sphere, planet frame
    n = p / R
    # Orbit: we move +X, so the ground turns aft about the planet's Y axis.
    ang = C.ORBITAL_SPEED / (R + alt) * t
    ca, sa = math.cos(ang), math.sin(ang)
    nx, nz = n[..., 0] * ca + n[..., 2] * sa, -n[..., 0] * sa + n[..., 2] * ca
    lon = np.degrees(np.arctan2(nx, nz))                  # 0 at the sub-point, + toward the bow
    lat = np.degrees(np.arcsin(np.clip(n[..., 1], -1, 1)))
    alb = np.asarray(Image.open(ALBEDO).convert("RGB"), np.float64) / 255
    cld = np.asarray(Image.open(CLOUDS).convert("L"), np.float64) / 255
    mh, mw = alb.shape[:2]
    u = np.clip((lon + 50.0) / 100.0 * (mw - 1), 0, mw - 1.001)          # map spans 100 x 50 degrees
    v = np.clip((25.0 - lat) / 50.0 * (mh - 1), 0, mh - 1.001)
    ground = bilerp(alb, u, v) ** 1.3 * 0.85
    cloud = np.clip((bilerp(cld[..., None], u, v)[..., 0] - 0.25) / 0.6, 0, 1)[..., None]
    surf = ground * (1 - cloud) + 0.92 * cloud
    sun = np.array(C.SUN)
    lit = np.clip(n @ sun, 0, 1)[..., None]
    rgb = surf * (0.03 + 1.05 * lit)
    # Aerial perspective toward the horizon, then the limb: a thin bright blue shell.
    graze = np.clip(1 - (-(d * n).sum(-1)), 0, 1)[..., None]
    rgb = rgb * (1 - 0.55 * graze ** 3) + np.array([0.45, 0.62, 0.95]) * 0.55 * graze ** 3 * lit
    closest = np.sqrt(np.maximum(oc @ oc - bq * bq, 0)) - R           # ray's lowest altitude
    shell = np.exp(-np.maximum(closest, 0) / 20000.0) * (~hit) * (bq < 0)
    rgb = np.where(hit[..., None], rgb, 0) + shell[..., None] * np.array([0.35, 0.55, 1.0]) * 0.9
    alpha = np.clip(hit + shell * 1.2, 0, 1)
    arr = np.dstack([np.clip(rgb, 0, 1), alpha])
    return Image.fromarray((arr * 255).astype(np.uint8), "RGBA")


# ---------------------------------------------------------------- charts

def bar(d, x0, x1, y, hgt, fill, label, f, text_fill=(20, 20, 20)):
    d.rounded_rectangle((x0, y, x1, y + hgt), radius=6, fill=fill)
    if not label:
        return
    if d.textlength(label, font=f) + 16 <= x1 - x0:
        d.text((x0 + 8, y + hgt / 2), label, fill=text_fill, font=f, anchor="lm")
    else:                                   # too long for its bar: hang it underneath
        d.text((x0 + 2, y + hgt + 3), label, fill=fill, font=f, anchor="lt")


def timeline():
    Wd, Hd = 1920, 860
    im = Image.new("RGB", (Wd, Hd), (24, 26, 30))
    d = ImageDraw.Draw(im)
    f, fs, fb = font(20), font(17), font(26)
    t0, t1 = C.C2_START - 1.0, C.ENTRY_START + C.ENTRY_LEN + 1.0
    X = lambda t: 60 + (t - t0) / (t1 - t0) * (Wd - 120)
    d.text((60, 24), "C2 PATROL ABEAM + entry transition: one clock (absolute seconds on the hail timeline; C2 time = absolute - 37.70)",
           fill=(235, 235, 235), font=fb)
    for s in range(int(t0) + 1, int(t1) + 1):
        x = X(s)
        d.line((x, 80, x, Hd - 40), fill=(48, 50, 56))
        d.text((x, Hd - 34), "%d" % s, fill=(150, 150, 150), font=fs, anchor="mt")
    # shot bands
    bar(d, X(t0), X(C.C2_START), 80, 40, (90, 90, 100), "C1", f, (230, 230, 230))
    bar(d, X(C.C2_START), X(C.ENTRY_START), 80, 40, (70, 110, 160), "C2 PATROL ABEAM  37.70-52.70  (15.0 s)", f, (240, 240, 240))
    bar(d, X(C.ENTRY_START), X(C.ENTRY_START + C.ENTRY_LEN), 80, 40, (190, 110, 50), "ENTRY 3.0 s", f, (250, 250, 250))
    bar(d, X(C.ENTRY_START + C.ENTRY_LEN), X(t1), 80, 40, (60, 120, 70), "C3", f, (240, 240, 240))
    hl = C.HAIL
    rows = [
        (160, "Approach (radio)", [(hl["squelch_open_7"], hl["squelch_open_7"] + 0.16, (200, 200, 200), ""),
                                   (hl["line7"][0], hl["line7_mounts_cold"][0] - 0.16, (150, 200, 240), "7: cleared ... corridor four."),
                                   (hl["line7_mounts_cold"][0], hl["line7_mounts_cold"][1], (120, 180, 235), "mounts cold, stay in corridor"),
                                   (hl["line7_patrol"][0], hl["line7_patrol"][1], (255, 220, 120), "PATROL HAS YOUR SIGNATURE"),
                                   (hl["squelch_close_7"][0], hl["squelch_close_7"][1], (200, 200, 200), "")]),
        (250, "Castle (intercom)", [(hl["line8"][0], hl["line8"][1], (230, 190, 160), "8: Corridor four, mounts cold. Thank you, Approach."),
                                    (hl["line9_well"][0], hl["line9_well"][1], (255, 160, 110), "9: Well."),
                                    (hl["line9_knows"][0], hl["line9_knows"][1], (255, 160, 110), "Somebody knows we're here.")]),
    ]
    for y, name, items in rows:
        d.text((60, y - 4), name, fill=(200, 200, 200), font=fs, anchor="lb")
        for a, b, col, label in items:
            bar(d, X(a), X(b), y, 34, col, label, fs)
    # patrol
    y = 340
    d.text((60, y - 4), "Patrol hull in the glass (rendered sequence)", fill=(200, 200, 200), font=fs, anchor="lb")
    s = C.C2_START
    bar(d, X(s), X(s + 2.0), y, 34, (110, 110, 120), "far bow", fs, (230, 230, 230))
    bar(d, X(s + 2.0), X(s + 5.2), y, 34, (150, 150, 160), "near bow crosses the glass", fs)
    bar(d, X(s + 5.2), X(s + 10.8), y, 34, (200, 200, 210), "broadside fills the glass  (centred %.1f)" % C.PATROL_T_MID, fs)
    bar(d, X(s + 10.8), X(s + 13.6), y, 34, (150, 150, 160), "stern + engines, rising out", fs)
    y = 420
    d.text((60, y - 4), "Second Wind", fill=(200, 200, 200), font=fs, anchor="lb")
    bar(d, X(s), X(s + C.PATROL_DROP_T), y, 34, (90, 100, 110), "coasting in corridor four", fs, (230, 230, 230))
    bar(d, X(s + C.PATROL_DROP_T), X(C.ENTRY_START), y, 34, (170, 120, 80), "de-orbit burn (cabin rumble rises; patrol 'climbs' away)", fs)
    bar(d, X(C.ENTRY_START), X(C.ENTRY_START + C.ENTRY_LEN), y, 34, (220, 140, 60), "entry (time-compressed)", fs)
    # transition detail
    e = C.ENTRY_START
    y = 490
    d.text((60, y - 4), "Entry wipes (right to left)", fill=(200, 200, 200), font=fs, anchor="lb")
    bar(d, X(e + C.GLOW_IN[0]), X(e + C.GLOW_IN[1]), y, 34, (255, 140, 50), "glow in", fs)
    bar(d, X(e + C.GLOW_HOLD[0]), X(e + C.GLOW_HOLD[1]), y, 34, (255, 175, 90), "hold", fs)
    bar(d, X(e + C.CLOUD_IN[0]), X(e + C.CLOUD_IN[1]), y + 60, 34, (225, 230, 240), "cloud in", fs)
    bar(d, X(e + C.WHITE_HOLD[0]), X(e + C.WHITE_HOLD[1]), y + 60, 34, (250, 250, 250), "white", fs)
    # stills
    y = 610
    d.text((60, y - 4), "Review stills", fill=(200, 200, 200), font=fs, anchor="lb")
    for label, t in (("1", s + 3.0), ("2", s + 8.0), ("3", s + 11.4), ("4", e + 0.9), ("5", e + 2.2)):
        d.ellipse((X(t) - 16, y, X(t) + 16, y + 32), fill=(240, 240, 240))
        d.text((X(t), y + 16), label, fill=(20, 20, 20), font=f, anchor="mm")
    notes = ["Cut C1->C2 at 37.70: 0.3 s before Approach keys up for line 7 (C1's rule).",
             "Hull centred abeam at C2 %.1f s, under 'Patrol has your signature' (C2 7.50-8.84)." % C.PATROL_T_MID,
             "Castle's read-back (line 8) starts the de-orbit; the hull clears the glass as she finishes.",
             "No squelch before line 9: the channel is closed. 'Well.' over the glow, 'Somebody knows we're here.' over the cloud.",
             "Cut to C3 at 55.70, 0.12 s after line 9 ends; C3 opens out of the white."]
    for i, n in enumerate(notes):
        d.text((60, 670 + i * 26), n, fill=(190, 190, 190), font=fs)
    im.save(GUIDES / "c2-timeline.png")


def wipe_mask(progress, soft=160.0, slant=0.35):
    """Coverage of a right-to-left wipe across the glass: 0 before the front, 1 behind it.
    The front leans with the jambs and is soft, like a front of glow or cloud, not a blade."""
    ys, xs = np.mgrid[0:C.H, 0:C.W].astype(np.float64)
    x0, x1 = 157.0 - soft, 1764.0 + soft
    front = x1 - progress * (x1 - x0) + slant * (ys - 350)
    return np.clip((xs - front) / soft + 0.5, 0, 1)


def transition_chart():
    Wd, Hd = 1920, 1080
    im = Image.new("RGB", (Wd, Hd), (24, 26, 30))
    d = ImageDraw.Draw(im)
    f, fs, fb = font(20), font(17), font(26)
    d.text((60, 24), "Entry transition: 3.0 s, same fixed frame. Glow, then cloud, both wiping right to left (bow to stern).",
           fill=(235, 235, 235), font=fb)
    # curves
    gx0, gx1, gy0, gy1 = 120, 1860, 90, 420
    d.rectangle((gx0, gy0, gx1, gy1), outline=(80, 80, 90))
    X = lambda T: gx0 + T / C.ENTRY_LEN * (gx1 - gx0)
    Y = lambda v: gy1 - v * (gy1 - gy0)
    for T in np.arange(0, C.ENTRY_LEN + 0.01, 0.5):
        d.line((X(T), gy0, X(T), gy1), fill=(50, 52, 58))
        d.text((X(T), gy1 + 8), "%.1f" % T, fill=(160, 160, 160), font=fs, anchor="mt")

    def curve(fn, col, label, ly):
        pts = [(X(T), Y(fn(T))) for T in np.linspace(0, C.ENTRY_LEN, 300)]
        d.line(pts, fill=col, width=4)
        d.text((gx1 - 10, ly), label, fill=col, font=fs, anchor="rm")
    glow = lambda T: C.smooth((T - C.GLOW_IN[0]) / (C.GLOW_IN[1] - C.GLOW_IN[0])) * (1 - 0.3 * C.smooth((T - C.CLOUD_IN[0]) / 1.2))
    cloud = lambda T: C.smooth((T - C.CLOUD_IN[0]) / (C.CLOUD_IN[1] - C.CLOUD_IN[0]))
    framewarm = lambda T: 0.8 * glow(T) * (1 - cloud(T))
    framesoft = lambda T: 0.9 * cloud(T)
    curve(glow, (255, 140, 50), "glow (wipe coverage x intensity)", 104)
    curve(cloud, (230, 235, 245), "cloud wipe coverage", 128)
    curve(framewarm, (255, 90, 40), "frame: orange relight", 152)
    curve(framesoft, (140, 190, 255), "frame: first soft fill (cloud light)", 176)
    for label, T0 in (("'Well.'", C.HAIL["line9_well"][0] - C.ENTRY_START),
                      ("'Somebody knows we're here.'", C.HAIL["line9_knows"][0] - C.ENTRY_START)):
        d.line((X(T0), gy0, X(T0), gy1), fill=(255, 200, 120), width=2)
        d.text((X(T0) + 6, gy0 + 6), label, fill=(255, 200, 120), font=fs)
    d.text((gx0, gy1 + 34), "seconds from 52.70 (cut from C2); cut to C3 at 3.0 = 55.70", fill=(160, 160, 160), font=fs)
    # thumbnails
    glass = np.asarray(glass_alpha(), np.float64) / 255
    base = np.zeros((C.H, C.W, 3))
    ys = np.mgrid[0:C.H, 0:C.W][0]
    planet = np.asarray(planet_layer(C.C2_LEN).convert("RGB"), np.float64) / 255
    orange = np.dstack([np.full((C.H, C.W), v) for v in (1.0, 0.45, 0.12)]) * (0.65 + 0.35 * (1 - ys / C.H))[..., None]
    white = np.dstack([np.full((C.H, C.W), v) for v in (0.93, 0.95, 0.98)])
    tw, th = 340, 191
    for i, T in enumerate((0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0)):
        gm = wipe_mask(C.smooth((T - C.GLOW_IN[0]) / (C.GLOW_IN[1] - C.GLOW_IN[0])))[..., None]
        cm = wipe_mask(C.smooth((T - C.CLOUD_IN[0]) / (C.CLOUD_IN[1] - C.CLOUD_IN[0])), soft=260)[..., None]
        world = planet * (1 - gm) + orange * gm
        world = world * (1 - cm) + white * cm
        frame_lum = 0.25 + 0.5 * framewarm(T) + 0.6 * framesoft(T)
        frame_col = np.array([0.32, 0.33, 0.35]) * frame_lum + np.array([0.6, 0.25, 0.05]) * framewarm(T) * 0.6
        comp = world * glass[..., None] + (base + frame_col) * (1 - glass[..., None])
        thumb = Image.fromarray((np.clip(comp, 0, 1) * 255).astype(np.uint8)).resize((tw, th), Image.LANCZOS)
        x = 60 + (i % 4) * (tw + 120) if i < 4 else 60 + (i - 4) * (tw + 120) + (tw + 120) // 2
        y = 520 if i < 4 else 790
        im.paste(thumb, (x, y))
        d.text((x, y - 8), "T = %.1f s" % T, fill=(220, 220, 220), font=f, anchor="lb")
    d.text((60, 1040), "Thumbnails: flat colour stand-ins for the glow and cloud cards over the planet layer, inside the measured glass; "
           "frame shown as flat grey with its relight level.", fill=(160, 160, 160), font=fs)
    im.save(GUIDES / "transition-timing.png")


def main():
    GUIDES.mkdir(exist_ok=True)
    write_masks()
    planet_layer(0.0).save(GUIDES / "planet-blockout.png")
    timeline()
    transition_chart()
    print("guides written to", GUIDES)


if __name__ == "__main__":
    main()
