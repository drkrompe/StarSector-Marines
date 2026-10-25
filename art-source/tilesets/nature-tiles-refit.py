"""Re-draws the nature sheet's six plants and seven rock groups without touching any other piece.

The accepted generation (one plant per 512 px frame, 480 px to a world cell, see
art-source/templates/forest-floor-plants-guide.py) is far larger than the nature plate, which is drawn at
`strip.scale` (1.9) times the 56 px cell the strip ships at. Each frame is resampled (Lanczos on premultiplied
alpha) to the plate's pixels per cell and joined to the right of the accepted plate, and the six plant entries of
nature-tiles.tileset-authoring.json are pointed at their new pieces. Order on the strip is the address, so the ids
keep their slots and nothing else moves.

The rocks come from field-stones/field-stones.png (4x2 frames, 480 px to a cell at a 512 px pitch, see
art-source/templates/field-stones-guide.py) and join the strip after the plants the same way. Their cracked and
shattered states, field-stones-damaged.png and -destroyed.png, are generated over guides cut from the same stones,
so the script cuts every state of a group at the intact group's alpha box: the strip stretches an intact frame to its
cell and rock-damage's export stretches each rect to its cell, so equal boxes register. Each state is shifted onto
its intact group's mean colour, so a hit does not flash the stone lighter or darker. rock-damage.raw.png is written
as the damaged sheet over the destroyed sheet, and the fourteen rects of rock-damage.tileset-authoring.json follow.

The left PLATE_SIZE of nature-tiles.raw.png is the plate the sheet was accepted with and is never altered; the
script reads it back from there, so it can be re-run.
usage: python art-source/tilesets/nature-tiles-refit.py
       (rewrites nature-tiles.raw.png, the plant entries' notes and rects and the rock entries' rects in its
       authoring document, rock-damage.raw.png and the rects in rock-damage.tileset-authoring.json)
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
ROCKS = ["small-1", "small-2", "medium-1", "medium-2", "large-1", "large-2", "large-3"]
STONES = HERE / "field-stones"
PITCH, SOURCE_CELL = 512, 480
ALPHA_MIN = 40
GUTTER = 24
# Source pixels by which a rock group's stones overlap in x once close_gaps has slid them together.
OVERLAP = 12
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


def edit_entry(text, pid, rect, note=None):
    """Edits one entry's rect (and note) in place; the rest of the document keeps its exact text."""
    entry = re.search(r'\{[^{}]*"id": "' + re.escape(pid) + r'"[^{}]*\}', text)
    block = entry.group(0)
    if note is not None:
        block = re.sub(r'"note": "(?:[^"\\]|\\.)*"', lambda m: '"note": ' + json.dumps(note, ensure_ascii=False), block)
    block = re.sub(r'"rect": \[[^\]]*\]', lambda m: '"rect": ' + json.dumps(rect, indent=2).replace("\n", "\n      "), block)
    return text[:entry.start()] + block + text[entry.end():]


def alpha_box(img):
    return img.getchannel("A").point(lambda v: 255 if v >= ALPHA_MIN else 0).getbbox()


def match_colour(state, intact):
    """Shifts a state's opaque pixels onto the intact group's mean colour."""
    s, i = np.asarray(state, dtype=np.float32), np.asarray(intact, dtype=np.float32)
    sm, im = s[..., 3] >= ALPHA_MIN, i[..., 3] >= ALPHA_MIN
    s[..., :3] *= i[im][:, :3].mean(axis=0) / np.maximum(s[sm][:, :3].mean(axis=0), 1)
    return Image.fromarray(np.clip(np.round(s), 0, 255).astype(np.uint8), "RGBA")


def close_gaps(intact, states):
    """Slides a group's column bands together until no clear column remains between its stones.

    The strip loader splits frames at any run of fully clear columns (SpriteSheetSlicer.MIN_GAP), so a group whose
    stones sit side by side with daylight between them would load as two frames and rename every piece after it.
    Each band moves toward the first by the gap's width plus OVERLAP, and every state moves with it, so the states
    still register."""
    clear = ~(np.asarray(intact)[..., 3] >= 16).any(axis=0)
    filled = np.nonzero(~clear)[0]
    cols = np.arange(filled[0], filled[-1] + 1)
    runs, start = [], None
    for c in cols:
        if clear[c] and start is None:
            start = c
        elif not clear[c] and start is not None:
            runs.append((start, c))
            start = None
    if not runs:
        return [intact] + states
    out = []
    for img in [intact] + states:
        moved = Image.new("RGBA", img.size, (0, 0, 0, 0))
        edges = [0] + [(a + b) // 2 for a, b in runs] + [img.width]
        shift = 0
        for k in range(len(edges) - 1):
            if k:
                shift -= runs[k - 1][1] - runs[k - 1][0] + OVERLAP
            band = img.crop((edges[k], 0, edges[k + 1], img.height))
            moved.alpha_composite(band, (edges[k] + shift, 0))
        out.append(moved)
    return out


def rocks():
    """The seven groups' intact frames for the strip, and rock-damage's sheet and rects cut at the same boxes."""
    intact = Image.open(STONES / "field-stones.png").convert("RGBA")
    pitch = intact.width / 4
    states = [Image.open(STONES / f"field-stones-{s}.png").convert("RGBA").resize(intact.size, Image.LANCZOS)
              for s in ("damaged", "destroyed")]
    sheet = Image.new("RGBA", (intact.width, intact.height * 2), (0, 0, 0, 0))
    frames, cuts = [], []
    for slot in range(7):
        x0, y0 = round(slot % 4 * pitch), round(slot // 4 * pitch)
        slot_box = (x0, y0, round(x0 + pitch), round(y0 + pitch))
        whole, *broken = close_gaps(intact.crop(slot_box), [s.crop(slot_box) for s in states])
        frames.append(whole)
        bx = alpha_box(whole)
        for row, state in enumerate(broken):
            sheet.paste(match_colour(state, whole), (x0, y0 + row * intact.height))
            cuts.append((row, slot, [x0 + bx[0], y0 + bx[1] + row * intact.height, bx[2] - bx[0], bx[3] - bx[1]]))
    return frames, intact.width / 2048 * SOURCE_CELL, sheet, cuts


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
    rock_frames, rock_cell, damage_sheet, cuts = rocks()
    pieces += [resample(f, doc["cellPx"] * doc["strip"]["scale"] / rock_cell) for f in rock_frames]
    ids = [(pid, note) for pid, note in PLANTS] + [("nature.rock-" + r, None) for r in ROCKS]
    width = plate.width + GUTTER + sum(p.width + GUTTER for p in pieces)
    out = Image.new("RGBA", (width, max([plate.height] + [p.height for p in pieces])), (0, 0, 0, 0))
    out.paste(plate, (0, 0))
    x0 = plate.width + GUTTER
    for piece, (pid, note) in zip(pieces, ids):
        out.paste(piece, (x0, 0))
        box = alpha_box(piece)
        rect = [x0 + box[0], box[1], box[2] - box[0], box[3] - box[1]]
        text = edit_entry(text, pid, rect, note)
        print(pid, rect)
        x0 += piece.width + GUTTER
    out.save(HERE / "nature-tiles.raw.png")
    doc_path.write_text(text, encoding="utf-8", newline="")

    damage_path = HERE / "rock-damage.tileset-authoring.json"
    damage = json.loads(damage_path.read_text(encoding="utf-8"))
    for row, slot, rect in cuts:
        pid = "nature.rock-" + ROCKS[slot] + ("-damaged", "-destroyed")[row]
        next(e for e in damage["entries"] if e["id"] == pid)["rect"] = rect
    damage_sheet.save(HERE / "rock-damage.raw.png")
    damage_path.write_text(json.dumps(damage, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="")


if __name__ == "__main__":
    main()
