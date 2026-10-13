"""Reproduce source-cell fog annotation from the accepted full-frame alpha.

This reads art; it never alters a PNG or supplies navigation/collision geometry.
Cells qualify when at least one fifth of their pixels have alpha >= 128.
Rows are emitted bottom first, matching the runtime source-cell fog contract.
"""
from pathlib import Path
import json
from PIL import Image


def fog_surface(image, columns, rows):
    alpha = image.getchannel("A")
    width, height = image.size
    mask = []
    for row in reversed(range(rows)):
        top, bottom = round(row * height / rows), round((row + 1) * height / rows)
        cells = []
        for column in range(columns):
            left, right = round(column * width / columns), round((column + 1) * width / columns)
            histogram = alpha.crop((left, top, right, bottom)).histogram()
            pixels = (right - left) * (bottom - top)
            cells.append("#" if sum(histogram[128:]) * 5 >= pixels else ".")
        mask.append("".join(cells))
    return mask


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    document = root / "art-source/tilesets/basin-colossus.tileset-authoring.json"
    annotation = json.loads(document.read_text(encoding="utf-8"))
    entry = annotation["entries"][0]
    with Image.open(root / annotation["sheet"]) as source:
        entry["fogSurface"] = fog_surface(source, *entry["footprintCells"])
    document.write_text(json.dumps(annotation, indent=2) + "\n", encoding="utf-8")
