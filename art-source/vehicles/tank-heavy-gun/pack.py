"""Pack the heavy-gun tank sheet: mod/graphics/battle/vehicles/army-tank-heavy-gun.png.

Four 768x768 frames: live hull, live turret, wreck hull, wreck turret, all facing LEFT.
Hull frames are copied verbatim from the breakthrough sheet so both variants share one hull.
Turret frames come from the ImageGen raws (turret-only canvases in the breakthrough masters'
coordinates), then:
  * shorten: each [x, width] removes a straight run of plain tube [x, x+width) and closes the
    gap by sliding everything left of it rightward (applied in listed order, right to left), so
    the muzzle lands at the registered length; the housing right of the cut never moves;
  * alphaFloor: generator haze at or below this alpha is cleared;
  * registration: the bearing lands on the frame center at the stated uniform scale
    (Lanczos, premultiplied), exactly as art-source/vehicles/tank/pack.ps1 registers its turret.
Run from anywhere: python art-source/vehicles/tank-heavy-gun/pack.py
"""
import json
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[2] / "mod" / "graphics" / "battle" / "vehicles" / "army-tank-heavy-gun.png"

def shorten(img, cuts):
    for x, w in cuts:
        left = img.crop((0, 0, x, img.height))
        out = img.copy()
        out.paste((0, 0, 0, 0), (0, 0, x + w, img.height))
        out.paste(left, (w, 0))
        img = out
    return img

def register(img, scale, center, frame):
    half = frame / 2 / scale
    pad = int(half) + 8  # the frame reaches past the master's edges; pad with transparency
    padded = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    padded.paste(img, (pad, pad))
    cx, cy = center[0] + pad, center[1] + pad
    box = (cx - half, cy - half, cx + half, cy + half)
    return padded.convert("RGBa").resize((frame, frame), Image.LANCZOS, box=box).convert("RGBA")

def main():
    reg = json.loads((HERE / "registration.json").read_text())
    w, h = reg["outputSize"]
    frame = w // len(reg["frames"])
    sheet = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for i, f in enumerate(reg["frames"]):
        if "copy" in f:
            src = Image.open((HERE / f["copy"]).resolve()).convert("RGBA")
            tile = src.crop((f["frame"] * frame, 0, (f["frame"] + 1) * frame, h))
        else:
            src = Image.open(HERE / f["source"]).convert("RGBA")
            if list(src.size) != f["sourceSize"]:
                raise SystemExit(f"Re-measure registration when replacing {f['source']}")
            src = shorten(src, f.get("shorten", []))
            floor = f.get("alphaFloor", 0)
            a = src.getchannel("A").point(lambda v: 0 if v <= floor else v)
            src.putalpha(a)
            tile = register(src, f["scale"], f["sourceCenter"], frame)
            assert f["targetCenter"] == [i * frame + frame // 2, h // 2]
        sheet.paste(tile, (i * frame, 0))
    sheet.save(OUT)
    print(f"wrote {OUT}")

if __name__ == "__main__":
    main()
