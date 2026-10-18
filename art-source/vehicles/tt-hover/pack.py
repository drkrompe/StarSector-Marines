"""Pack Tri-Tachyon's two hover sheets from the retained ImageGen raws.

  mod/graphics/battle/vehicles/tt-hover-scout.png    4 x 672x576: hull, turret, wreck hull, wreck turret
  mod/graphics/battle/vehicles/tt-drone-skimmer.png  4 x 832x640: hull, (no turret), wreck hull, (no turret)

Each raw is one 2:1 generation of two panels (live | wreck) painted over guide.py's 1792x896 guide at 256 px per
cell. A raw is resized to the guide canvas (premultiplied Lanczos), so every panel centre is a hull's or turret's
bearing, and the frame is cut about that centre. Turret frames are drawn at the hull's own scale, so the turret's
frame cells equal the hull frame's. Generator alpha haze at or below 8 is cleared. Everything faces LEFT.

    python art-source/vehicles/tt-hover/pack.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = ROOT / "mod" / "graphics" / "battle" / "vehicles"
GUIDE = (1792, 896)
PANEL = 896
PX = 256

SCOUT_FRAME = (672, 576)
SKIMMER_FRAME = (832, 640)


def load(name):
    raw = Image.open(HERE / f"{name}-raw.png").convert("RGBA")
    if raw.size != GUIDE:
        raw = raw.convert("RGBa").resize(GUIDE, Image.LANCZOS).convert("RGBA")
    a = raw.getchannel("A").point(lambda v: 0 if v <= 8 else v)
    raw.putalpha(a)
    return raw


def panel(raw, index, frame):
    cx, cy = PANEL * index + PANEL // 2, PANEL // 2
    w, h = frame
    return raw.crop((cx - w // 2, cy - h // 2, cx + w // 2, cy + h // 2))


def sheet(frame, frames):
    out = Image.new("RGBA", (frame[0] * 4, frame[1]), (0, 0, 0, 0))
    for i, img in enumerate(frames):
        if img is not None:
            out.paste(img, (i * frame[0], 0))
    return out


def muzzle_cells(turret_frame):
    """Distance from the bearing (frame centre) to the emitters' leading edge, and their lateral offset, in cells."""
    w, h = turret_frame.size
    a = turret_frame.getchannel("A")
    cy = h // 2
    rows = [y for y in range(cy - 40, cy + 40) if any(a.getpixel((x, y)) > 128 for x in range(w // 2))]
    left = min(x for y in rows for x in range(w // 2) if a.getpixel((x, y)) > 128)
    tip_rows = [y for y in rows if a.getpixel((left + 3, y)) > 128]
    lateral = sum(abs(y - cy) for y in tip_rows) / max(1, len(tip_rows)) / PX
    return (w // 2 - left) / PX, lateral


def main():
    scout = load("scout-hull")
    turret = load("scout-turret")
    skimmer = load("skimmer-hull")
    live_turret = panel(turret, 0, SCOUT_FRAME)
    sheet(SCOUT_FRAME, [panel(scout, 0, SCOUT_FRAME), live_turret,
                        panel(scout, 1, SCOUT_FRAME), panel(turret, 1, SCOUT_FRAME)]).save(OUT / "tt-hover-scout.png")
    sheet(SKIMMER_FRAME, [panel(skimmer, 0, SKIMMER_FRAME), None,
                          panel(skimmer, 1, SKIMMER_FRAME), None]).save(OUT / "tt-drone-skimmer.png")
    reach, lateral = muzzle_cells(live_turret)
    print(f"scout frame {SCOUT_FRAME[0] / PX:.4f} cells; skimmer frame {SKIMMER_FRAME[0] / PX:.4f} cells")
    print(f"emitter muzzle {reach:.3f} cells ahead of the bearing, {lateral:.3f} cells lateral")


if __name__ == "__main__":
    main()
