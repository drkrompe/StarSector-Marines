"""Deterministic edit targets and geometry guides for the heavy-gun turret.

Writes, beside this script:
  turret-target.png  turret-master.png with the hull erased (turret alone, same canvas/pose)
  turret-guide.png   that target with the short cannon cut away and the heavy gun blocked in
  wreck-target.png   wreck-master.png with the wreck hull erased
  wreck-guide.png    that target with the heavy gun blocked in, bent slightly near the brake
Coordinates are source pixels of the breakthrough tank masters (art-source/vehicles/tank/).
Live: bearing (1498,454), 0.7 scale -> 140.857 px/cell. Wreck: bearing (1344,447), 0.855 -> 115.32 px/cell.
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
TANK = HERE.parent / "tank"

def erase_left(img, x_end):
    img = img.copy()
    ImageDraw.Draw(img).rectangle((0, 0, x_end - 1, img.height), fill=(0, 0, 0, 0))
    return img

def gun(d, s, cx, cy, muzzle_x, mantlet_x, bend=None):
    """Block out mantlet, tube, fume extractor and muzzle brake. s scales live px to this master."""
    def r(v): return round(v * s)
    # Heavy mantlet block, chamfered.
    mx0, mx1 = mantlet_x - r(88), mantlet_x
    d.polygon([(mx0 + r(10), cy - r(46)), (mx1, cy - r(46)), (mx1, cy + r(46)), (mx0 + r(10), cy + r(46)),
               (mx0, cy + r(34)), (mx0, cy - r(34))], fill="#525b49")
    d.rectangle((mx0 + r(12), cy - r(32), mx1 - r(8), cy + r(32)), fill="#646c56")
    # Thick tube, slight taper toward the brake.
    t0 = mx0 + r(4)
    bx = muzzle_x + r(40)  # brake rear face
    if bend is None:
        d.polygon([(bx, cy - r(19)), (t0, cy - r(22)), (t0, cy + r(22)), (bx, cy + r(19))], fill="#454e43")
        ex = muzzle_x + r(150)
        d.rectangle((ex, cy - r(30), ex + r(52), cy + r(30)), fill="#777d65")  # fume extractor
        d.rectangle((muzzle_x, cy - r(31), bx, cy + r(31)), fill="#626953")      # muzzle brake
        for px in (muzzle_x + r(9), muzzle_x + r(24)):
            d.rectangle((px, cy - r(31), px + r(7), cy - r(18)), fill="#262a24")
            d.rectangle((px, cy + r(18), px + r(7), cy + r(31)), fill="#262a24")
    else:
        kx, dy = bend  # knee x, lateral drop at muzzle
        d.polygon([(kx, cy - r(20)), (t0, cy - r(22)), (t0, cy + r(22)), (kx, cy + r(20))], fill="#454e43")
        d.polygon([(bx, cy + dy - r(19)), (kx, cy - r(20)), (kx, cy + r(20)), (bx, cy + dy + r(19))], fill="#3f463c")
        ex = kx + r(30)
        d.rectangle((ex, cy - r(30), ex + r(52), cy + r(30)), fill="#6a6e5c")
        d.rectangle((muzzle_x, cy + dy - r(31), bx, cy + dy + r(31)), fill="#585d4c")
        for px in (muzzle_x + r(9), muzzle_x + r(24)):
            d.rectangle((px, cy + dy - r(31), px + r(7), cy + dy - r(18)), fill="#1f221d")
            d.rectangle((px, cy + dy + r(18), px + r(7), cy + dy + r(31)), fill="#1f221d")

def main():
    live = Image.open(TANK / "turret-master.png").convert("RGBA")
    target = erase_left(live, 1100)
    target.save(HERE / "turret-target.png")
    guide = target.copy()
    d = ImageDraw.Draw(guide)
    d.rectangle((1100, 430, 1300, 480), fill=(0, 0, 0, 0))  # cut the short cannon away
    # Muzzle 1012: (1498-1012)/140.857 = 3.450 cells.
    gun(d, 1.0, 1498, 454, muzzle_x=1012, mantlet_x=1352)
    guide.save(HERE / "turret-guide.png")

    wreck = Image.open(TANK / "wreck-master.png").convert("RGBA")
    wtarget = erase_left(wreck, 1000)
    wtarget.save(HERE / "wreck-target.png")
    wguide = wtarget.copy()
    d = ImageDraw.Draw(wguide)
    d.rectangle((1000, 425, 1192, 468), fill=(0, 0, 0, 0))
    s = 115.32 / 140.857
    # Muzzle 964: (1344-964)/115.32 = 3.30 cells, bent 7 px off-axis beyond the knee.
    gun(d, s, 1344, 447, muzzle_x=964, mantlet_x=1218, bend=(1040, 7))
    wguide.save(HERE / "wreck-guide.png")

if __name__ == "__main__":
    main()
