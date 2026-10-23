"""Pack the laser tank sheet: mod/graphics/battle/vehicles/army-tank-laser.png.

Four 768x768 frames: live hull, live turret, wreck hull, wreck turret, all facing LEFT.
Hull frames are copied verbatim from the breakthrough sheet: the laser tank is that hull.
Turret frames come from the ImageGen raws (turret-only canvases in the breakthrough masters'
coordinates, the emitter fit painted over guide.py's blocks), then:
  * alphaFloor: generator haze at or below this alpha is cleared;
  * registration: the bearing lands on the frame center at the stated uniform scale
    (Lanczos, premultiplied), exactly as art-source/vehicles/tank-heavy-gun/pack.py does.
Run from anywhere: python art-source/vehicles/tank-laser/pack.py [registration.json] [out.png]
The optional arguments pack an unchosen option (registration-c.json) somewhere other than the mod.
"""
import json
import sys
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[2] / "mod" / "graphics" / "battle" / "vehicles" / "army-tank-laser.png"


def register(img, scale, center, frame):
    half = frame / 2 / scale
    pad = int(half) + 8  # the frame reaches past the master's edges; pad with transparency
    padded = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    padded.paste(img, (pad, pad))
    cx, cy = center[0] + pad, center[1] + pad
    box = (cx - half, cy - half, cx + half, cy + half)
    return padded.convert("RGBa").resize((frame, frame), Image.LANCZOS, box=box).convert("RGBA")


def main():
    reg = json.loads((HERE / (sys.argv[1] if len(sys.argv) > 1 else "registration.json")).read_text())
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else OUT
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
            floor = f.get("alphaFloor", 0)
            src.putalpha(src.getchannel("A").point(lambda v: 0 if v <= floor else v))
            tile = register(src, f["scale"], f["sourceCenter"], frame)
            assert f["targetCenter"] == [i * frame + frame // 2, h // 2]
        sheet.paste(tile, (i * frame, 0))
    sheet.save(out)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
