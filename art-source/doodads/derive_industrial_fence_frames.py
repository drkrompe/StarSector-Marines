"""Cut full fence cells from the connected ImageGen frame; never trim or recenter."""

import json
from pathlib import Path

from PIL import Image


HERE = Path(__file__).resolve().parent
SOURCES = HERE / "sources"


def main() -> None:
    layout = json.loads((HERE / "industrial-fence-geometry.json").read_text(encoding="utf-8"))
    raw = Image.open(HERE / layout["source"]).convert("RGBA")
    if list(raw.size) != layout["sourceSize"]:
        raise ValueError("Fence source dimensions differ from the reviewed cut layout")
    SOURCES.mkdir(parents=True, exist_ok=True)
    for family, directions in (("corner", ("nw", "ne", "se", "sw")),
                               ("straight", ("h", "v"))):
        x, y, size = layout[family]
        if size <= 0 or x < 0 or y < 0 or x + size > raw.width or y + size > raw.height:
            raise ValueError(f"Invalid full-cell cut for {family}")
        cell = raw.crop((x, y, x + size, y + size))
        for direction in directions:
            destination = SOURCES / f"industrial-fence-{family}-{direction}.png"
            cell.save(destination, optimize=True)
            print(destination)
            cell = cell.transpose(Image.Transpose.ROTATE_270)


if __name__ == "__main__":
    main()
