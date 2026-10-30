"""Pack the retired Parapet's live turret frame into the active-protection kit's source:
art-source/vehicles/kit-active-protection/screened-turret-frame.png (the kit's build.py lifts the fit off it).

The Parapet was a hull of its own until active protection became a rack kit (owner, 2026-10-30); its sheet
(army-tank-screened.png) no longer ships, and only its live turret frame is still used.

Four 768x768 frames: live hull, live turret, wreck hull, wreck turret, all facing LEFT.
Hull frames are copied verbatim from the breakthrough sheet: the screened tank is that hull.
Turret frames come from the ImageGen raws (turret-only canvases in the breakthrough masters'
coordinates, the interceptor fit painted over guide.py's blocks), then:
  * alphaFloor: generator haze at or below this alpha is cleared;
  * registration: the bearing lands on the frame center at the stated uniform scale
    (Lanczos, premultiplied).
Run from anywhere: python art-source/vehicles/tank-screened/pack.py
"""
import json
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "kit-active-protection" / "screened-turret-frame.png"


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
            floor = f.get("alphaFloor", 0)
            src.putalpha(src.getchannel("A").point(lambda v: 0 if v <= floor else v))
            tile = register(src, f["scale"], f["sourceCenter"], frame)
            assert f["targetCenter"] == [i * frame + frame // 2, h // 2]
        sheet.paste(tile, (i * frame, 0))
    sheet.crop((frame, 0, 2 * frame, h)).save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
