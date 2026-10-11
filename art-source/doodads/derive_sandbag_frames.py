"""Derive directional sandbags from full geometry-authored cells, without trimming."""

import json
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
SOURCES = HERE / "sources"


def main() -> None:
    layout = json.loads((HERE / "sandbags-geometry.json").read_text(encoding="utf-8"))
    raw = Image.open(HERE / layout["source"]).convert("RGBA")
    if list(raw.size) != layout["sourceSize"]:
        raise ValueError("Sandbag source dimensions differ from the reviewed cut layout")
    SOURCES.mkdir(parents=True, exist_ok=True)
    for family, directions in (("straight", ("n", "e", "s", "w")),
                               ("corner", ("nw", "ne", "se", "sw"))):
        x, y, size = layout[family]
        if size <= 0 or x < 0 or y < 0 or x + size > raw.width or y + size > raw.height:
            raise ValueError(f"Invalid full-cell cut for {family}")
        cell = raw.crop((x, y, x + size, y + size))
        for direction in directions:
            destination = SOURCES / f"sandbag-{family}-{direction}.png"
            cell.save(destination, optimize=True)
            print(destination)
            cell = cell.transpose(Image.Transpose.ROTATE_270)


if __name__ == "__main__":
    main()
