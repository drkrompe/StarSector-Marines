"""Re-draws the nature sheet's six plants from forest-floor-plants/forest-floor-plants.png without touching any other piece.

The accepted generation (one plant per 512 px frame, 480 px to a world cell, see
art-source/templates/forest-floor-plants-guide.py) is far larger than the nature plate, which is drawn at
`strip.scale` (1.9) times the 56 px cell the strip ships at. Each frame is resampled (Lanczos on premultiplied
alpha) to the plate's pixels per cell and joined to the right of the accepted plate, and the six plant entries of
nature-tiles.tileset-authoring.json are pointed at their new pieces. Order on the strip is the address, so the ids
keep their slots and nothing else moves.

The left PLATE_SIZE of nature-tiles.raw.png is the plate the sheet was accepted with and is never altered; the
script reads it back from there, so it can be re-run.
usage: python art-source/tilesets/nature-plants-refit.py
       (rewrites nature-tiles.raw.png and the six plant entries' notes and rects in the authoring document)
"""
import json
import re
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
# Slot order on forest-floor-plants.png, and the note each entry gets for its new picture.
PLANTS = [
    ("nature.shrub-1", "broad low fern rosette seen from above — the widest plant on the sheet"),
    ("nature.shrub-2", "small scrubby leafy bush — the smallest shrub, reads as scrub at cell size"),
    ("nature.shrub-3", "low leafy bush with a scatter of small cream flowers"),
    ("nature.tuft-1", "small upright grass tuft seen from above"),
    ("nature.tuft-2", "wider, looser grass tuft"),
    ("nature.tuft-3", "tall wild grass with pale seed heads"),
]
PITCH, SOURCE_CELL = 512, 480
ALPHA_MIN = 40
GUTTER = 24
# The accepted plate, 2026-09; everything to its right is this script's.
PLATE_SIZE = (2172, 724)


def resample(frame, scale):
    # Premultiplied, so the transparent surround cannot bleed a dark fringe into the edge.
    rgba = np.asarray(frame, dtype=np.float32) / 255
    rgba[..., :3] *= rgba[..., 3:]
    size = (round(frame.width * scale), round(frame.height * scale))
    bands = [Image.fromarray(rgba[..., c], mode="F").resize(size, Image.LANCZOS) for c in range(4)]
    out = np.clip(np.stack([np.asarray(b) for b in bands], axis=-1), 0, 1)
    alpha = out[..., 3:]
    out[..., :3] = np.where(alpha > 1e-3, out[..., :3] / np.maximum(alpha, 1e-3), 0)
    return Image.fromarray(np.round(np.clip(out, 0, 1) * 255).astype(np.uint8), "RGBA")


def main():
    doc_path = HERE / "nature-tiles.tileset-authoring.json"
    text = doc_path.read_text(encoding="utf-8")
    doc = json.loads(text)
    plate = Image.open(HERE / "nature-tiles.raw.png").convert("RGBA").crop((0, 0) + PLATE_SIZE)
    plants = Image.open(HERE / "forest-floor-plants" / "forest-floor-plants.png").convert("RGBA")
    scale = doc["cellPx"] * doc["strip"]["scale"] / SOURCE_CELL
    pieces = []
    for i in range(6):
        frame = plants.crop((i % 3 * PITCH, i // 3 * PITCH, i % 3 * PITCH + PITCH, i // 3 * PITCH + PITCH))
        pieces.append(resample(frame, scale))
    cell = pieces[0].width
    out = Image.new("RGBA", (plate.width + GUTTER + 6 * (cell + GUTTER), max(plate.height, cell)), (0, 0, 0, 0))
    out.paste(plate, (0, 0))
    for i, (piece, (pid, note)) in enumerate(zip(pieces, PLANTS)):
        x0 = plate.width + GUTTER + i * (cell + GUTTER)
        out.paste(piece, (x0, 0))
        box = piece.getchannel("A").point(lambda v: 255 if v >= ALPHA_MIN else 0).getbbox()
        rect = [x0 + box[0], box[1], box[2] - box[0], box[3] - box[1]]
        # Edit the entry's note and rect in place; the rest of the document keeps its exact text.
        entry = re.search(r'\{[^{}]*"id": "' + re.escape(pid) + r'"[^{}]*\}', text)
        block = entry.group(0)
        block = re.sub(r'"note": "(?:[^"\\]|\\.)*"', lambda m: '"note": ' + json.dumps(note, ensure_ascii=False), block)
        block = re.sub(r'"rect": \[[^\]]*\]',
                       lambda m: '"rect": ' + json.dumps(rect, indent=2).replace("\n", "\n      "), block)
        text = text[:entry.start()] + block + text[entry.end():]
        print(pid, rect)
    out.save(HERE / "nature-tiles.raw.png")
    doc_path.write_text(text, encoding="utf-8", newline="")


if __name__ == "__main__":
    main()
