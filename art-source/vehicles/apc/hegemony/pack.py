"""Pack the APC's sheet from the owner's Hegemony concept (hegemony-armor-line.md, slice 2).

  mod/graphics/battle/vehicles/army-apc.png   4 x 768x576: Hegemony hull, twin-MG turret, Hegemony wreck, wrecked turret

The live hull is `hegemony-concepts/heg-apc-concept.png` verbatim (alpha is clean; its dark glow is colour on zero
alpha) and the wreck `wreck-raw.png`, an ImageGen edit made on it with `wreck.prompt.txt`, registered to it pixel for
pixel. Both are drawn nose up, so each is turned a quarter anticlockwise to point LEFT, scaled uniformly so the hull is
as long as the old APC's (722 px of the 768 px frame) and placed so the concept's turret ring centre lands on the roof
bearing (335.333, 288) that `VehicleType.Apc.TURRET_MOUNT_Y` and the mortar and LRM carriers' packs already use. The
hull's own centre then sits about 9 px aft of the frame centre, 0.03 cells.

Slots 1 and 3 keep the old twin-MG turret frames from `../master.png` and `../wreck-master.png` at their original
registration (`../registration.json`). A fitted weapon is drawn under the turret head instead (`TurretHead`), so these
frames are only a fallback for a hull drawn without a gun card.

After running this, re-run the mortar and LRM carrier packs: both copy this sheet's hull slots.
Run: python art-source/vehicles/apc/hegemony/pack.py
"""
import json
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
APC = HERE.parent
REPO = HERE.parents[3]
OUT = REPO / "mod" / "graphics" / "battle" / "vehicles" / "army-apc.png"
LIVE = REPO / "art-source" / "vehicles" / "hegemony-concepts" / "heg-apc-concept.png"
WRECK = HERE / "wreck-raw.png"

SLOT_W, SLOT_H = 768, 576
HULL_LENGTH_PX = 722                 # the old APC hull's opaque length in its frame
CONCEPT_NOSE_Y, CONCEPT_TAIL_Y = 92, 1365  # the concept hull's opaque extent along its length (alpha > 128)
CONCEPT_RING = (514, 658)            # the concept's turret ring centre, measured off its disc
ROOF_BEARING = (335.333, 288)        # where the ring lands in the frame


def hull_frame(source: Path) -> Image.Image:
    image = Image.open(source).convert("RGBA")
    width = image.width
    turned = image.rotate(90, expand=True)  # anticlockwise: the top (nose) goes to the left
    ring = (CONCEPT_RING[1], width - 1 - CONCEPT_RING[0])
    scale = HULL_LENGTH_PX / (CONCEPT_TAIL_Y - CONCEPT_NOSE_Y)
    scaled = turned.resize((round(turned.width * scale), round(turned.height * scale)), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (SLOT_W, SLOT_H), (0, 0, 0, 0))
    frame.alpha_composite(scaled, (round(ROOF_BEARING[0] - ring[0] * scale), round(ROOF_BEARING[1] - ring[1] * scale)))
    return frame


def old_turret_frame(entry: dict) -> Image.Image:
    source = Image.open(APC / entry["source"]).convert("RGBA")
    x, y, w, h = entry["sourceRect"]
    scale = entry["scale"]
    part = source.crop((x, y, x + w, y + h))
    part = part.resize((round(w * scale), round(h * scale)), Image.Resampling.BICUBIC)
    local_x = entry["targetCenter"][0] % SLOT_W
    frame = Image.new("RGBA", (SLOT_W, SLOT_H), (0, 0, 0, 0))
    frame.alpha_composite(part, (round(local_x - (entry["sourceCenter"][0] - x) * scale),
                                 round(entry["targetCenter"][1] - (entry["sourceCenter"][1] - y) * scale)))
    return frame


def main() -> None:
    registration = json.loads((APC / "registration.json").read_text())
    frames = [hull_frame(LIVE), old_turret_frame(registration["frames"][1]),
              hull_frame(WRECK), old_turret_frame(registration["frames"][3])]
    sheet = Image.new("RGBA", (SLOT_W * 4, SLOT_H), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        sheet.paste(frame, (index * SLOT_W, 0))
    sheet.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
