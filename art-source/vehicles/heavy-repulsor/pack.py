"""Pack the Onager's sheet from the owner's heavy repulsor concept (hegemony-armor-line.md, slice 4).

  mod/graphics/battle/vehicles/heg-heavy-repulsor.png   4 x 768x576: hull, closed head, wreck, wrecked head

The live hull is `hegemony-concepts/heg-heavy-repulsor-tank.png` verbatim (its alpha is clean) and the wreck
`wreck-raw.png`, an ImageGen edit made on it with `wreck.prompt.txt`, registered to it pixel for pixel (alpha IoU
0.99). Both are drawn nose up, so each is turned a quarter anticlockwise to point LEFT, scaled uniformly so the hull is
HULL_LENGTH_PX long in the 768 px frame (`VehicleType.BREAKTHROUGH_TANK` draws the frame 4.0 cells long, so the hull
is 3.6 cells long and 2.22 wide, its footprint) and placed so the concept's turret ring centre lands on the centre
line, RING_AHEAD_PX ahead of the frame centre, which centres the hull in its frame. `VehicleType.Onager.TURRET_MOUNT_Y`
is that offset in cells.

It hovers, so there are no treads to scroll. A fitted weapon is drawn under the shared Hegemony head (`TurretHead`).
Slots 1 and 3 hold that head, live and wrecked, at the size the battle draws it over the ring, as the fallback for a
hull drawn without a gun card.
Run: python art-source/vehicles/heavy-repulsor/pack.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
VEHICLES = REPO / "mod" / "graphics" / "battle" / "vehicles"
OUT = VEHICLES / "heg-heavy-repulsor.png"
LIVE = REPO / "art-source" / "vehicles" / "hegemony-concepts" / "heg-heavy-repulsor-tank.png"
WRECK = HERE / "wreck-raw.png"

SLOT_W, SLOT_H = 768, 576
DRAWN_LENGTH_CELLS = 4.0             # VehicleType.BREAKTHROUGH_TANK.drawnLengthCells
VISUAL_LENGTH_CELLS = 3.6            # its footprint length: the hull's painted length
CONCEPT_NOSE_Y, CONCEPT_TAIL_Y = 16, 1169  # the concept hull's opaque extent along its length (alpha > 128)
CONCEPT_LEFT_X, CONCEPT_RIGHT_X = 271, 982  # and across it
CONCEPT_RING = (627, 557)            # the turret ring centre, fitted to its collar (outer r 190, inner disc r 127)
CONCEPT_COLLAR_RADIUS = 190
HEAD_SHARE_OF_RING = 1.1497          # VehicleGun.HEAD_SHARE_OF_MOUNT
HULL_LENGTH_PX = SLOT_W * VISUAL_LENGTH_CELLS / DRAWN_LENGTH_CELLS
SCALE = HULL_LENGTH_PX / (CONCEPT_TAIL_Y - CONCEPT_NOSE_Y)
CELL_PX = SLOT_W / DRAWN_LENGTH_CELLS
VISUAL_WIDTH_CELLS = (CONCEPT_RIGHT_X - CONCEPT_LEFT_X) * SCALE / CELL_PX
RING_SHARE_OF_WIDTH = 2 * CONCEPT_COLLAR_RADIUS / (CONCEPT_RIGHT_X - CONCEPT_LEFT_X)
CONCEPT_CENTRE_Y = (CONCEPT_NOSE_Y + CONCEPT_TAIL_Y) / 2
RING_AHEAD_PX = (CONCEPT_CENTRE_Y - CONCEPT_RING[1]) * SCALE
RING_IN_FRAME = (SLOT_W / 2 - RING_AHEAD_PX, SLOT_H / 2)


def hull_frame(source: Path) -> Image.Image:
    image = Image.open(source).convert("RGBA")
    width = image.width
    turned = image.rotate(90, expand=True)  # anticlockwise: the top (nose) goes to the left
    ring = (CONCEPT_RING[1], width - 1 - CONCEPT_RING[0])
    scaled = turned.resize((round(turned.width * SCALE), round(turned.height * SCALE)), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (SLOT_W, SLOT_H), (0, 0, 0, 0))
    frame.alpha_composite(scaled, (round(RING_IN_FRAME[0] - ring[0] * SCALE),
                                   round(RING_IN_FRAME[1] - ring[1] * SCALE)))
    return frame


def head_frame(texture: str) -> Image.Image:
    """The head, gun slot to the left, centred in the frame at its battle size over this hull's ring."""
    head = Image.open(VEHICLES / texture).convert("RGBA").rotate(90, expand=True)
    head_cells = VISUAL_WIDTH_CELLS * RING_SHARE_OF_WIDTH * HEAD_SHARE_OF_RING
    side = round(head_cells * CELL_PX)
    head = head.resize((side, side), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (SLOT_W, SLOT_H), (0, 0, 0, 0))
    frame.alpha_composite(head, ((SLOT_W - side) // 2, (SLOT_H - side) // 2))
    return frame


def main() -> None:
    frames = [hull_frame(LIVE), head_frame("turret-head-hegemony.png"),
              hull_frame(WRECK), head_frame("turret-head-hegemony-wreck.png")]
    sheet = Image.new("RGBA", (SLOT_W * 4, SLOT_H), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        sheet.paste(frame, (index * SLOT_W, 0))
    sheet.save(OUT)
    print("wrote", OUT)
    print(f"footprint {VISUAL_LENGTH_CELLS:.3f} x {VISUAL_WIDTH_CELLS:.4f} cells; ring share of width "
          f"{RING_SHARE_OF_WIDTH:.4f}; ring ahead of centre {RING_AHEAD_PX:.3f} px = {RING_AHEAD_PX / CELL_PX:.6f} cells")


if __name__ == "__main__":
    main()
