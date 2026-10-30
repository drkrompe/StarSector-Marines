"""Edit targets and geometry guides for the Onager's turret heads (one housing, a gun per weapon family).

The approved angular turret housing (../turret-master.png, the single-cannon edit of the Onager's turret) is the body
every head shares; only the gun and mantlet change. This writes, in turret-master.png's own 1774x887 coordinates:

  * turret-target.png: turret-master.png with the hull erased (x < 1020), the identity/material authority;
  * guide-<head>.png: the target with its cannon and twin mantlet stubs cut away (forward of the cheek wedges, on the
    centre band) and the head's gun drawn as flat grey blocks, the geometry authority.

Every head keeps its muzzle at the approved cannon's (x 1182, y 454), so the shared carriage's muzzle offset
(2.2434501 cells) stays where the shots leave. Run: python art-source/vehicles/tank/heads/guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
MASTER = HERE.parent / "turret-master.png"
CY = 454          # the turret's centre line
MUZZLE_X = 1182   # the approved cannon's muzzle
CUT = (1100, 410, 1362, 500)  # the cannon and its mantlet stubs, between the cheek wedges

DARK = (58, 62, 54, 255)
MID = (104, 108, 92, 255)
LIGHT = (150, 152, 132, 255)


def target():
    master = Image.open(MASTER).convert("RGBA")
    out = master.copy()
    ImageDraw.Draw(out).rectangle((0, 0, 1019, master.height), fill=(0, 0, 0, 0))
    return out


def cut(img):
    out = img.copy()
    ImageDraw.Draw(out).rectangle(CUT, fill=(0, 0, 0, 0))
    return out


def autocannon(d):
    """A short, thick autocannon: a boxed mantlet, a ribbed recoil sleeve, a heavy slotted muzzle brake."""
    d.rectangle((1318, 424, 1366, 484), fill=DARK)               # boxed mantlet
    d.rectangle((1326, 432, 1358, 476), fill=MID)
    d.rectangle((1262, 440, 1320, 468), fill=MID)                # recoil sleeve
    for x in range(1268, 1318, 10):
        d.rectangle((x, 438, x + 4, 470), fill=DARK)
    d.rectangle((1206, 445, 1264, 463), fill=LIGHT)              # thick barrel
    d.rectangle((MUZZLE_X, 436, 1210, 472), fill=DARK)           # muzzle brake
    for x in (1188, 1198):
        d.rectangle((x, 436, x + 4, 472), fill=MID)
    d.rectangle((1330, 486, 1360, 502), fill=MID)                # feed chute off the lower cheek


def rotary(d):
    """A rotary gun: a drum housing on the mantlet and a bundle of six barrels held by a front clamp."""
    d.ellipse((1318, 422, 1370, 486), fill=DARK)                 # drum housing
    d.rectangle((1300, 430, 1340, 478), fill=MID)                # breech block
    d.rectangle((MUZZLE_X + 6, 436, 1304, 472), fill=DARK)       # barrel bundle
    for y in (439, 445, 451, 457, 463, 469):
        d.rectangle((MUZZLE_X + 6, y - 1, 1300, y + 1), fill=LIGHT)
    d.rectangle((MUZZLE_X, 432, MUZZLE_X + 10, 476), fill=MID)   # muzzle clamp
    d.rectangle((1250, 432, 1258, 476), fill=MID)                # mid clamp
    d.rectangle((1330, 488, 1362, 504), fill=MID)                # ammunition chute


def rail(d):
    """A linear cannon: two flat rails held apart by insulator bands, fed by a capacitor block on the mantlet."""
    d.rectangle((1316, 420, 1370, 488), fill=DARK)               # capacitor block
    for y in (428, 446, 464):
        d.rectangle((1322, y, 1364, y + 10), fill=MID)
    d.rectangle((MUZZLE_X, 437, 1318, 447), fill=LIGHT)          # upper rail
    d.rectangle((MUZZLE_X, 461, 1318, 471), fill=LIGHT)          # lower rail
    d.rectangle((MUZZLE_X + 4, 447, 1318, 461), fill=(40, 46, 52, 255))  # bore channel
    for x in range(1196, 1316, 22):
        d.rectangle((x, 431, x + 7, 477), fill=MID)              # insulator bands


HEADS = {"autocannon": autocannon, "rotary": rotary, "rail": rail}


def main():
    tgt = target()
    tgt.save(HERE / "turret-target.png")
    for name, draw in HEADS.items():
        guide = cut(tgt)
        draw(ImageDraw.Draw(guide))
        guide.save(HERE / f"guide-{name}.png")
    print("wrote turret-target.png and", ", ".join(f"guide-{n}.png" for n in HEADS))


if __name__ == "__main__":
    main()
