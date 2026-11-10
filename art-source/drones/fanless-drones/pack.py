"""Cuts the painted fanless drone sheets into the sprites the game draws (`DroneLook`).

    python pack.py                      # into mod/
    python pack.py --root <overlay dir> # a look candidate, outside mod/

Each sheet (<sheet>-raw.png, painted over <sheet>-guide.png) holds one drone per panel facing LEFT (guide.py). A drone
is cropped to its alpha bounds, turned to face UP (the battle renderer's 0 degrees is +Y) and scaled so its long axis
is its sheet's pixel size. Written under <root>/graphics/battle/drones/ by the names below.
"""
import argparse
from pathlib import Path

from PIL import Image

from guide import PANELS

HERE = Path(__file__).resolve().parent
MOD = HERE.parents[2] / "mod"

# Sheet -> (sprite long-axis pixels, role -> file name).
OUTPUTS = {
    "tt": (192, {"escort": "tt-escort", "sensor": "tt-sensor", "halo": "tt-halo", "strike": "tt-strike"}),
    "family": (192, {"low": "hub-drone-low-tech", "mid": "hub-drone-midline", "high": "hub-drone-high-tech"}),
    "swarm": (128, {"domain": "swarm-domain", "contingency": "swarm-contingency"}),
    "repair": (192, {"repair-domain": "repair-domain", "repair-contingency": "repair-contingency"}),
}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--root", type=Path, default=MOD)
    out = p.parse_args().root / "graphics" / "battle" / "drones"
    out.mkdir(parents=True, exist_ok=True)
    for sheet_name, (px, names) in OUTPUTS.items():
        sheet = Image.open(HERE / f"{sheet_name}-raw.png").convert("RGBA")
        half_w, half_h = sheet.width // 2, sheet.height // 2
        for role, (col, row) in PANELS[sheet_name].items():
            panel = sheet.crop((col * half_w, row * half_h, (col + 1) * half_w, (row + 1) * half_h))
            box = panel.getchannel("A").point(lambda a: 255 if a > 24 else 0).getbbox()
            drone = panel.crop(box).rotate(-90, expand=True, resample=Image.BICUBIC)
            scale = px / max(drone.size)
            drone = drone.resize((max(1, round(drone.width * scale)), max(1, round(drone.height * scale))),
                                 Image.LANCZOS)
            drone.save(out / f"{names[role]}.png")
            print(names[role], drone.size)


if __name__ == "__main__":
    main()
