"""Pack the medium tank's sheet from the owner's Hegemony concept (hegemony-armor-line.md, slice 5).

  mod/graphics/battle/vehicles/heg-medium-tank.png   4 x 768x576: hull, closed head, wreck, wrecked head

The live hull is `hegemony-concepts/heg-medium-tank.png` verbatim (its alpha is clean) and the wreck `wreck-raw.png`,
an ImageGen edit made on it with `wreck.prompt.txt`, registered to it pixel for pixel. Both are drawn nose up, so each
is turned a quarter anticlockwise to point LEFT, scaled uniformly so the hull is HULL_LENGTH_PX long in the 768 px
frame (`VehicleType.MEDIUM_TANK` draws the frame 3.84 cells long, so the hull is 3.4 cells) and placed so the
concept's turret ring centre lands on the centre line, RING_AHEAD_PX ahead of the frame centre, which centres the hull
in its frame. `VehicleType.MediumTank.TURRET_MOUNT_Y` is that offset in cells.

A fitted weapon is drawn under the shared Hegemony head (`TurretHead`). Slots 1 and 3 hold that head, live and
wrecked, at the size the battle draws it over the ring, as the fallback for a hull drawn without a gun card.

Prints the tread bands for `VehicleTreadRender` (MEDIUM_TANK): each track's exposed straight runs in frame pixels and
the link pitch measured on them.
Run: python art-source/vehicles/medium-tank/pack.py
"""
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
VEHICLES = REPO / "mod" / "graphics" / "battle" / "vehicles"
OUT = VEHICLES / "heg-medium-tank.png"
LIVE = REPO / "art-source" / "vehicles" / "hegemony-concepts" / "heg-medium-tank.png"
WRECK = HERE / "wreck-raw.png"

SLOT_W, SLOT_H = 768, 576
DRAWN_LENGTH_CELLS = 3.84            # VehicleType.MEDIUM_TANK.drawnLengthCells
VISUAL_WIDTH_CELLS = 1.75            # its footprint width, which sizes the ring and so the head
RING_SHARE_OF_WIDTH = .62            # VehicleType.MEDIUM_TANK.turretRingShareOfWidth()
HEAD_SHARE_OF_RING = 1.1497          # VehicleGun.HEAD_SHARE_OF_MOUNT
HULL_LENGTH_PX = 680                 # the hull's opaque length in its frame
CONCEPT_NOSE_Y, CONCEPT_TAIL_Y = 47, 1147  # the concept hull's opaque extent along its length (alpha > 128)
CONCEPT_RING = (627, 536)            # the turret ring centre, fitted to the collar's inner edge (r 132)
SCALE = HULL_LENGTH_PX / (CONCEPT_TAIL_Y - CONCEPT_NOSE_Y)
CONCEPT_CENTRE_Y = (CONCEPT_NOSE_Y + CONCEPT_TAIL_Y) / 2
RING_AHEAD_PX = (CONCEPT_CENTRE_Y - CONCEPT_RING[1]) * SCALE
RING_IN_FRAME = (SLOT_W / 2 - RING_AHEAD_PX, SLOT_H / 2)

# The exposed straight runs of each track in concept pixels: (x0, x1, y0, y1). The armour skirts hide the inner links,
# and amidships the skirt covers the left track to its edge, so each side has a forward and an aft run.
TRACK_RUNS = {
    "right forward": (890, 906, 236, 556),
    "right aft": (862, 905, 840, 1046),
    "left forward": (347, 363, 236, 556),
    "left aft": (348, 391, 840, 1046),
}


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
    side = round(head_cells / DRAWN_LENGTH_CELLS * SLOT_W)
    head = head.resize((side, side), Image.Resampling.LANCZOS)
    frame = Image.new("RGBA", (SLOT_W, SLOT_H), (0, 0, 0, 0))
    frame.alpha_composite(head, ((SLOT_W - side) // 2, (SLOT_H - side) // 2))
    return frame


def to_frame(x: float, y: float) -> tuple[float, float]:
    """A concept pixel's place in the frame."""
    width = Image.open(LIVE).width
    return (RING_IN_FRAME[0] + (y - CONCEPT_RING[1]) * SCALE,
            RING_IN_FRAME[1] + (width - 1 - x - (width - 1 - CONCEPT_RING[0])) * SCALE)


def link_pitch(frame: Image.Image, x0: int, x1: int, y0: int, y1: int) -> int:
    lum = np.asarray(frame.convert("RGBA"), dtype=float)[y0:y1, x0:x1, :3].mean(axis=2).mean(axis=0)
    lum -= lum.mean()
    return max(range(14, 30), key=lambda p: np.corrcoef(lum[:-p], lum[p:])[0, 1])


def report_bands(frame: Image.Image) -> None:
    for name, (x0, x1, y0, y1) in TRACK_RUNS.items():
        ax, ay = to_frame(x0, y1)
        bx, by = to_frame(x1, y0)
        start, end = round(min(ax, bx)), round(max(ax, bx))
        top, bottom = round(min(ay, by)), round(max(ay, by))
        pitch = link_pitch(frame, start, end, top, bottom)
        print(f"{name}: Band({start}, {end}, {top}, {bottom - top}, {start + 2}, {pitch})")


def main() -> None:
    live = hull_frame(LIVE)
    frames = [live, head_frame("turret-head-hegemony.png"),
              hull_frame(WRECK) if WRECK.exists() else live, head_frame("turret-head-hegemony-wreck.png")]
    sheet = Image.new("RGBA", (SLOT_W * 4, SLOT_H), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        sheet.paste(frame, (index * SLOT_W, 0))
    sheet.save(OUT)
    print("wrote", OUT)
    print(f"ring ahead of centre: {RING_AHEAD_PX:.3f} px = {RING_AHEAD_PX * DRAWN_LENGTH_CELLS / SLOT_W:.6f} cells")
    report_bands(live)


if __name__ == "__main__":
    main()
