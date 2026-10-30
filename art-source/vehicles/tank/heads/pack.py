"""Pack the Onager's turret heads onto its sheet: mod/graphics/battle/vehicles/army-tank.png.

Run after ../pack.ps1, which writes the sheet's first four 768x768 frames (live hull, live turret, wreck hull, wreck
turret); the live and wreck turret are the heavy cannon's head. This keeps those four frames and appends, per head in
HEADS order, a live and a wreck frame, all facing LEFT at the turret frame's registration (7.789194 cells), then the
guns:

  frame 4, 5: autocannon live, wreck     frame 6, 7: rotary live, wreck
  frame 8: every head's gun, a 256-row strip each (heavy cannon, autocannon, rotary)

A live head is drawn as two layers, so firing kicks the gun alone and never the housing: its live frame is the
housing with the gun cut away (forward of the cut: the gun's band, and anything off the approved housing such as a
chute or a hose), and its gun is a strip of frame 8 that stands for the head frame's middle third, forward of the cut,
drawn under the housing. Each gun is seated back into the housing until its muzzle is the shared carriage's
(guide.py's MUZZLE_X), so a long gun shows no more barrel than the heavy cannon and shots leave from the drawn muzzle;
what it pushes aft of the cut is under the housing and is dropped. Re-running on a split sheet rebuilds the heavy
cannon's whole head from its housing and strip first. The wreck frames are whole heads, built from the seated gun.

Live frame: the head's ImageGen edit (<head>-raw.png, turret-only, in turret-master.png's coordinates) registered to
the approved turret. ImageGen returns the housing a little scaled and shifted, so the scale and offset that best
overlay the raw's housing alpha (everything aft of the cut, where the gun does not reach) on turret-target.png's are
searched and recorded in registration.json, and the raw is resampled so its housing lands on the approved turret's.

Wreck frame: the heavy cannon's wreck turret (frame 3) with its bent cannon cut away and the head's own gun laid in
its place, charred and knocked a few degrees down, as the wreck master's cannon is. No generation: the housing's
damage is the approved wreck's.

Run from anywhere: python art-source/vehicles/tank/heads/pack.py   (python ... pack.py --measure re-searches)
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
SHEET = HERE.parents[3] / "mod" / "graphics" / "battle" / "vehicles" / "army-tank.png"
REGISTRATION = HERE / "registration.json"
FRAME = 768
HEADS = ["autocannon", "rotary"]
BEARING = (1498, 454)       # the turret's bearing in turret-master.png
MASTER_SCALE = 0.7          # turret-master.png px to frame px (../registration.json)
CUT_X = 1362                # aft of this the housing; forward of it the gun (guide.py's CUT)
GUN_BAND = (408, 502)       # the gun's band across the turret, master y
ALPHA_FLOOR = 8
WRECK_SHADE = 0.42
WRECK_DROOP_DEG = -4.0
GUN_FRAME = 8               # the frame holding every head's gun, a strip each
STRIP = 256                 # a gun strip's height: a third of a frame
STRIP_TOP = 256             # the head frame rows a gun strip covers start here (the middle third)
SHARED_MUZZLE_X = FRAME / 2 + MASTER_SCALE * (1182 - BEARING[0])   # the carriage's muzzle (guide.py's MUZZLE_X)


def frame_x(master_x):
    return FRAME / 2 + MASTER_SCALE * (master_x - BEARING[0])


def frame_y(master_y):
    return FRAME / 2 + MASTER_SCALE * (master_y - BEARING[1])


def measure(raw):
    """The scale and offset (about the bearing) that best lay the approved housing over the raw's."""
    target = Image.open(HERE / "turret-target.png").convert("RGBA")
    tgt = np.asarray(target.getchannel("A")) > 128
    raw_mask = np.asarray(raw.getchannel("A")) > 128
    # Sample points: the master's housing aft of the cut, with a margin, every 3 px.
    x0, y0, x1, y1 = Image.fromarray(tgt.astype(np.uint8) * 255).getbbox()
    ys, xs = np.mgrid[max(0, y0 - 30):min(tgt.shape[0], y1 + 30):3, CUT_X:min(tgt.shape[1], x1 + 30):3]
    expected = tgt[ys, xs]

    def iou(s, dx, dy):
        # Master point -> raw pixel under this guess; the raw's housing alpha there, against the master's.
        rx = np.rint(BEARING[0] + dx + s * (xs - BEARING[0])).astype(int)
        ry = np.rint(BEARING[1] + dy + s * (ys - BEARING[1])).astype(int)
        ok = (rx >= 0) & (rx < raw_mask.shape[1]) & (ry >= 0) & (ry < raw_mask.shape[0])
        seen = np.zeros_like(expected)
        seen[ok] = raw_mask[ry[ok], rx[ok]]
        union = (seen | expected).sum()
        return (seen & expected).sum() / union if union else 0.0

    # Coarse, then fine about the coarse best.
    best = max((iou(s, dx, dy), s, dx, dy) for s in np.arange(0.90, 1.201, 0.02)
               for dx in range(-24, 25, 6) for dy in range(-24, 25, 6))
    _, s0, dx0, dy0 = best
    best = max((iou(s, dx, dy), s, dx, dy) for s in np.arange(s0 - 0.02, s0 + 0.0201, 0.0025)
               for dx in range(dx0 - 6, dx0 + 7) for dy in range(dy0 - 6, dy0 + 7))
    return {"scale": round(float(best[1]), 4), "offset": [int(best[2]), int(best[3])],
            "housingIoU": round(float(best[0]), 4)}


def register(img, scale, center):
    half = FRAME / 2 / scale
    pad = int(half) + 8
    padded = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    padded.paste(img, (pad, pad))
    cx, cy = center[0] + pad, center[1] + pad
    box = (cx - half, cy - half, cx + half, cy + half)
    return padded.convert("RGBa").resize((FRAME, FRAME), Image.LANCZOS, box=box).convert("RGBA")


def live(raw, reg):
    floor = raw.getchannel("A").point(lambda v: 0 if v <= ALPHA_FLOOR else v)
    raw = raw.copy()
    raw.putalpha(floor)
    s = reg["scale"]
    centre = (BEARING[0] + reg["offset"][0], BEARING[1] + reg["offset"][1])
    return register(raw, MASTER_SCALE / s, centre)


def gun_region_mask():
    mask = Image.new("L", (FRAME, FRAME), 0)
    ImageDraw.Draw(mask).rectangle((0, frame_y(GUN_BAND[0]), frame_x(CUT_X), frame_y(GUN_BAND[1])), fill=255)
    return mask


def wreck(stock_wreck, head_live):
    """The approved wreck housing, its cannon cut away, with the head's own gun charred and drooped in its place."""
    region = gun_region_mask()
    out = stock_wreck.copy()
    out.putalpha(Image.fromarray(np.minimum(np.asarray(out.getchannel("A")),
                                            255 - np.asarray(region)).astype(np.uint8)))
    gun = head_live.copy()
    gun.putalpha(Image.fromarray(np.minimum(np.asarray(gun.getchannel("A")), np.asarray(region)).astype(np.uint8)))
    rgb = np.asarray(gun.convert("RGB")).astype(np.float32)
    grey = rgb.mean(axis=2, keepdims=True)
    charred = (0.6 * rgb + 0.4 * grey) * WRECK_SHADE
    burnt = Image.fromarray(np.clip(charred, 0, 255).astype(np.uint8), "RGB")
    burnt.putalpha(gun.getchannel("A"))
    pivot = (frame_x(CUT_X), FRAME / 2)
    burnt = burnt.rotate(WRECK_DROOP_DEG, resample=Image.BICUBIC, center=pivot)
    out.alpha_composite(burnt)
    return out


def cut_px():
    return int(round(frame_x(CUT_X)))


def band_mask():
    """The gun's band forward of the cut, as a boolean frame array: the cannon and its mantlet stubs."""
    return np.asarray(gun_region_mask()) > 0


def gun_mask(head_live, housing_alpha):
    """Which pixels of a whole head are its gun: forward of the cut, in the gun's band or off the approved housing
    (a chute or a hose the gun carries beside a cheek), and inside the strip a gun layer keeps."""
    alpha = np.asarray(head_live.getchannel("A"))
    housing = Image.fromarray((housing_alpha > ALPHA_FLOOR).astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(7))
    off_housing = (alpha > ALPHA_FLOOR) & (np.asarray(housing) == 0)
    mask = band_mask() | off_housing
    mask[:, cut_px():] = False
    mask[:STRIP_TOP, :] = False
    mask[STRIP_TOP + STRIP:, :] = False
    return mask


def split(head_live, housing_alpha):
    """A whole head as its housing (the gun cut away) and its gun, the gun seated back into the housing so its muzzle
    is the shared carriage's (SHARED_MUZZLE_X), and kept only forward of the cut: aft of it the housing covers it."""
    mask = gun_mask(head_live, housing_alpha)
    rgba = np.asarray(head_live).copy()
    housing = rgba.copy()
    housing[mask, 3] = 0
    gun = rgba.copy()
    gun[~mask, 3] = 0
    cols = np.where((gun[:, :, 3] > 64).any(axis=0))[0]
    seat = max(0, int(round(SHARED_MUZZLE_X - cols.min()))) if len(cols) else 0
    seated = np.zeros_like(gun)
    seated[:, seat:] = gun[:, :FRAME - seat]
    seated[:, cut_px():, 3] = 0
    return Image.fromarray(housing, "RGBA"), Image.fromarray(seated, "RGBA"), seat


def whole(housing, gun):
    """The head as it stands: its gun under its housing."""
    out = gun.copy()
    out.alpha_composite(housing)
    return out


def main():
    remeasure = "--measure" in sys.argv
    regs = json.loads(REGISTRATION.read_text()) if REGISTRATION.exists() else {}
    sheet = Image.open(SHEET).convert("RGBA")
    if sheet.height != FRAME or sheet.width < 4 * FRAME:
        raise SystemExit("run ../pack.ps1 first: the sheet needs its four base frames")
    base = [sheet.crop((i * FRAME, 0, (i + 1) * FRAME, FRAME)) for i in range(4)]
    if sheet.width >= FRAME * GUN_FRAME + FRAME:
        # Already split: the heavy cannon's whole head is its housing over its gun strip (seated by nothing).
        strip = sheet.crop((GUN_FRAME * FRAME, 0, GUN_FRAME * FRAME + FRAME, STRIP))
        gun = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
        gun.paste(strip, (0, STRIP_TOP))
        base[1] = whole(base[1], gun)
    # The approved housing: the heavy cannon's head with its cannon and mantlet stubs cut away.
    housing_alpha = np.asarray(base[1].getchannel("A")).copy()
    housing_alpha[band_mask()] = 0
    heavy_housing, heavy_gun, heavy_seat = split(base[1], housing_alpha)
    frames = [base[0], heavy_housing, base[2], base[3]]
    guns = [heavy_gun]
    seats = {"heavy-cannon": heavy_seat}
    for head in HEADS:
        raw = Image.open(HERE / f"{head}-raw.png").convert("RGBA")
        if head not in regs or remeasure:
            regs[head] = measure(raw)
            print(head, regs[head])
        head_housing, head_gun, seats[head] = split(live(raw, regs[head]), housing_alpha)
        frames += [head_housing, wreck(base[3], whole(head_housing, head_gun))]
        guns.append(head_gun)
    REGISTRATION.write_text(json.dumps(regs, indent=2) + "\n")
    strips = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
    for i, gun in enumerate(guns):
        strips.paste(gun.crop((0, STRIP_TOP, FRAME, STRIP_TOP + STRIP)), (0, i * STRIP))
    frames.append(strips)
    out = Image.new("RGBA", (FRAME * len(frames), FRAME), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (i * FRAME, 0))
    out.save(SHEET)
    print(f"wrote {SHEET}: {len(frames)} frames; guns seated back by {seats} px")


if __name__ == "__main__":
    main()
