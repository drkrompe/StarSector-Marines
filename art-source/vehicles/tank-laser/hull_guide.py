"""Edit target and guides for the laser tank's Tri-Tachyon hull options.

Writes, beside this script:
  hull-target.png       art-source/vehicles/tank/master.png with the turret half erased (hull alone)
  hull-guide-<opt>.png  that target with the up-res ship mount set into the deck at the turret
                        bearing and one option's changes blocked in as flat shapes:
    a  TT heavy, skirted: armoured side skirts over the track tops, a capacitor pack over the rear grilles
    b  TT heavy, exposed tracks: the capacitor pack only
    c  repulsor sled: tracks removed, four repulsor pads and slim side fairings, the capacitor pack
Coordinates are hull master pixels: bearing (508,451), 0.73 to the sheet's 178.6 px/cell, so 244.7 px/cell.
The mount is sized for a 1.25-cell turret: mount-upres-raw.png's race ring (centre (628,626), inner
radius 160) lands just outside the turret's base ring.
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
TANK = HERE.parent / "tank"
BEARING = (508, 451)
SHEET_PPC = 768 / 4.3
TURRET_CELLS = 1.25
MOUNT_C, MOUNT_KEEP = (628, 626), 200
PACK, CELL, CELL_CAP, FIN, FIN_GAP, SKIRT, SKIRT_EDGE, PAD, PAD_CORE = (
    "#5a6068", "#2c3036", "#7fd3e6", "#9aa2aa", "#2a2d31", "#b9bec4", "#80868c", "#3a3f45", "#6fc8dc")


def mount_layer(size):
    m = Image.open(HERE / "mount-upres-raw.png").convert("RGBA")
    mask = Image.new("L", m.size, 0)
    cx, cy = MOUNT_C
    ImageDraw.Draw(mask).ellipse((cx - MOUNT_KEEP, cy - MOUNT_KEEP, cx + MOUNT_KEEP, cy + MOUNT_KEEP), fill=255)
    m.putalpha(Image.composite(m.getchannel("A"), Image.new("L", m.size, 0), mask))
    turret_px = TURRET_CELLS * SHEET_PPC / 668          # sheet px per up-res turret px (body 668 px long)
    scale = turret_px * 270 / 160 / 0.73                 # master px per mount px
    w = round(m.width * scale)
    m = m.resize((w, w), Image.LANCZOS)
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    layer.alpha_composite(m, (round(BEARING[0] - cx * scale), round(BEARING[1] - cy * scale)))
    return layer


def pack(d):
    d.rectangle((700, 296, 892, 606), fill=PACK)
    for col in range(2):
        for row in range(5):
            x, y = 744 + col * 84, 330 + row * 58
            d.ellipse((x - 24, y - 24, x + 24, y + 24), fill=CELL)
            d.ellipse((x - 12, y - 12, x + 12, y + 12), fill=CELL_CAP)
    d.rectangle((896, 296, 948, 606), fill=FIN_GAP)
    for i in range(10):
        d.rectangle((900, 302 + i * 30, 944, 302 + i * 30 + 18), fill=FIN)


def main():
    master = Image.open(TANK / "master.png").convert("RGBA")
    target = master.copy()
    ImageDraw.Draw(target).rectangle((1020, 0, master.width, master.height), fill=(0, 0, 0, 0))
    target.save(HERE / "hull-target.png")
    mount = mount_layer(master.size)
    for opt in ("a", "b", "c"):
        g = target.copy()
        d = ImageDraw.Draw(g)
        if opt == "c":
            d.rectangle((0, 160, 1020, 236), fill=(0, 0, 0, 0))
            d.rectangle((0, 664, 1020, 740), fill=(0, 0, 0, 0))
            for y0, y1 in ((208, 240), (660, 692)):
                d.rectangle((96, y0, 940, y1), fill=SKIRT_EDGE)
            for x in (220, 800):
                for y in (226, 676):
                    d.ellipse((x - 54, y - 54, x + 54, y + 54), fill=PAD)
                    d.ellipse((x - 32, y - 32, x + 32, y + 32), fill=PAD_CORE)
        if opt == "a":
            for y0, y1 in ((184, 236), (662, 714)):
                d.rectangle((72, y0, 958, y1), fill=SKIRT)
                d.rectangle((72, y1 - 8 if y0 > 400 else y0, 958, y1 if y0 > 400 else y0 + 8), fill=SKIRT_EDGE)
        pack(d)
        g.alpha_composite(mount)
        g.save(HERE / f"hull-guide-{opt}.png")


if __name__ == "__main__":
    main()
