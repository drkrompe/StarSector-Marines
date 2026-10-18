"""Cuts the painted drone sheet (drones-raw.png) into one shipped sprite per role.

Each quadrant of the sheet holds one drone facing LEFT (guide.py). A sprite is cropped to the drone's alpha bounds,
turned to face UP (the battle renderer's 0 degrees is +Y) and scaled so its long axis is SPRITE_PX pixels.
"""
from pathlib import Path

from PIL import Image

from guide import PANELS

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[2] / "mod" / "graphics" / "battle" / "drones"
SPRITE_PX = 192


def main():
    sheet = Image.open(HERE / "drones-raw.png").convert("RGBA")
    half_w, half_h = sheet.width // 2, sheet.height // 2
    OUT.mkdir(parents=True, exist_ok=True)
    for role, (col, row, _) in PANELS.items():
        panel = sheet.crop((col * half_w, row * half_h, (col + 1) * half_w, (row + 1) * half_h))
        alpha = panel.getchannel("A").point(lambda a: 255 if a > 24 else 0)
        box = alpha.getbbox()
        drone = panel.crop(box).rotate(-90, expand=True, resample=Image.BICUBIC)
        scale = SPRITE_PX / max(drone.size)
        drone = drone.resize((max(1, round(drone.width * scale)), max(1, round(drone.height * scale))),
                             Image.LANCZOS)
        drone.save(OUT / f"tt-{role}.png")
        print(role, drone.size)


if __name__ == "__main__":
    main()
