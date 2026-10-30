"""Cut the high-tech posts' laser heads into mod/graphics/structures/ground-turrets/laser/ (`ground-turret-tiers.md`).

Each head is a painted, up-res'd vanilla laser turret (upres_guide.py; the PL-3 (Sweep) head is the laser tank's
Tri-Tachyon skin), turned nose up and scaled about its pivot, the sprite's centre as in the game, so the head turns on
its mount. A laser has no recoil, so there is no barrel layer; the contact shadow is baked in as for every head. The
heads are named for the laser they carry (`mechs-nouns.md`, Weapon names).

  pl-3-sweep-head.png   160x160 (visualCells 1.25): the PL-3 (Sweep) emplacement, from art-source/vehicles/tank-laser/pd-skin-tt-raw.png
  pl-3-head.png         160x160 (visualCells 1.25): the PL-3 emplacement's twin emitters, from burstpd-raw.png
  heavy-laser-head.png  384x384 (visualCells 3.0):  the hardpoint's PL-3 (Sweep), the laser tank's own red head
                        (art-source/vehicles/tank-laser/pd-skin-red-raw.png) at hardpoint size

Prints each head's muzzle (the emitter tip on the axis), in cells forward of the pivot, for the mounts.

Usage: python art-source/ground-turrets/laser/export_laser.py
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from export import contact_shadow  # noqa: E402

ROOT = HERE.parent.parent.parent
OUT = ROOT / "mod" / "graphics" / "structures" / "ground-turrets" / "laser"
PPC = 128
# name: (source, turns to put the nose up, sprite px, body length in cells, contact-shadow radius as a share)
HEADS = {
    "pl-3-sweep": (ROOT / "art-source" / "vehicles" / "tank-laser" / "pd-skin-tt-raw.png", -90, 160, 0.78, 0.24),
    "pl-3": (HERE / "burstpd-raw.png", 0, 160, 0.86, 0.26),
    "heavy-laser": (ROOT / "art-source" / "vehicles" / "tank-laser" / "pd-skin-red-raw.png", -90, 384, 1.44, 0.18),
}


def export(name, source, turn, size, length_cells, shadow):
    raw = Image.open(source).convert("RGBA").rotate(turn, resample=Image.BICUBIC)
    box = raw.getchannel("A").point(lambda v: 255 if v > 32 else 0).getbbox()
    k = length_cells * PPC / (box[3] - box[1])
    scaled = raw.resize((round(raw.width * k), round(raw.height * k)), Image.LANCZOS)
    # About the centre, padding or trimming the empty canvas round the body.
    left, top = (scaled.width - size) // 2, (scaled.height - size) // 2
    head = scaled.crop((left, top, left + size, top + size))
    out = contact_shadow(size, shadow)
    out.alpha_composite(head)
    OUT.mkdir(parents=True, exist_ok=True)
    out.save(OUT / f"{name}-head.png")
    tip = head.getchannel("A").point(lambda v: 255 if v > 64 else 0).getbbox()[1]
    print(f"{name}: muzzle {(size / 2 - tip) / PPC:.3f} cells forward")


def main():
    for name, spec in HEADS.items():
        export(name, *spec)


if __name__ == "__main__":
    main()
