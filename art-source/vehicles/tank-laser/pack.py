"""Pack the laser tank sheet: mod/graphics/battle/vehicles/army-tank-laser.png.

Five 768x768 frames, all facing LEFT: live hull, live turret, wreck hull, wreck turret, firing turret.
  * Hull frames come from edits of the breakthrough hull master, so they keep its registration:
    the bearing (508,451) lands on the frame centre at 0.73 (the sheet's 178.6 px per cell).
  * Turret frames come from the up-res vanilla PD laser (pd-upres-raw.png and edits of it). The pivot
    is the vanilla sprite's centre, (627,627) on that canvas, and its 668-px body is turretBodyCells
    long; a frame spans turretCells. ImageGen returns an edit at a slightly different size now and then
    (the red/white skin came back 1.32x), so each turret source is fitted to pd-upres-raw.png by its
    alpha bounding box unless "fit": false.
  * Generator alpha haze at or below 8 is cleared; resampling is Lanczos on premultiplied alpha.
Prints the turret muzzle (the lens front) in cells for mount.tank-pl-3.
Run from anywhere: python art-source/vehicles/tank-laser/pack.py [registration.json] [out.png]
(the burst laser tank; the PD laser tank is registration-pd.json into mod/graphics/battle/vehicles/army-tank-pd-laser.png)
"""
import json
import sys
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[2] / "mod" / "graphics" / "battle" / "vehicles" / "army-tank-laser.png"
BASE, PIVOT, BODY_PX = "pd-upres-raw.png", (627, 627), 668
FRAME = 768


def bbox(img):
    return img.getchannel("A").point(lambda v: 255 if v > 128 else 0).getbbox()


def register(img, scale, center):
    half = FRAME / 2 / scale
    pad = int(half) + 8
    padded = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    padded.paste(img, (pad, pad))
    cx, cy = center[0] + pad, center[1] + pad
    box = (cx - half, cy - half, cx + half, cy + half)
    return padded.convert("RGBa").resize((FRAME, FRAME), Image.LANCZOS, box=box).convert("RGBA")


def load(name):
    img = Image.open(HERE / name).convert("RGBA")
    img.putalpha(img.getchannel("A").point(lambda v: 0 if v <= 8 else v))
    return img


def main():
    reg = json.loads((HERE / (sys.argv[1] if len(sys.argv) > 1 else "registration.json")).read_text())
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else OUT
    w, h = reg["outputSize"]
    assert w == FRAME * len(reg["frames"]) and h == FRAME
    turret_scale = FRAME / reg["turretCells"] / (BODY_PX / reg["turretBodyCells"])
    b = bbox(Image.open(HERE / BASE).convert("RGBA"))
    base_c, base_w = ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2), b[2] - b[0]
    sheet = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for i, f in enumerate(reg["frames"]):
        img = load(f["source"])
        if f["role"] == "hull":
            tile = register(img, f["scale"], f["sourceCenter"])
        else:
            s, pivot = 1.0, PIVOT
            if f.get("fit", True):
                r = bbox(img)
                s = (r[2] - r[0]) / base_w
                rc = ((r[0] + r[2]) / 2, (r[1] + r[3]) / 2)
                pivot = (rc[0] + s * (PIVOT[0] - base_c[0]), rc[1] + s * (PIVOT[1] - base_c[1]))
            tile = register(img, turret_scale / s, pivot)
            if i == 1:
                front = bbox(img)[0]
                print(f"muzzle (lens front) {(pivot[0] - front) / s / (BODY_PX / reg['turretBodyCells']):.3f} cells forward")
        sheet.paste(tile, (i * FRAME, 0))
    sheet.save(out)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
