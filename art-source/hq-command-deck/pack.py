"""Pack the command deck for the game: the plate band, one sprite per figure, and the layout.

Run from the repository root:  python -I art-source/hq-command-deck/pack.py

The banner shows a wide band of the 3:1 plate, with nothing laid over it. The band is
chosen to keep every head (the quartermaster's, at guide row 206, is highest) inside
its top and to reach as far down the deck as that allows. Figures are packed as separate sprites, each
already cut by its stand's matte, so the screen can light one station's people without
redrawing the scene. Station rectangles and overlay anchors come from the Blender layout
and the placed cards, never from measuring the painting, so a repaint registered on the
same guide needs only a re-run.

Writes:
  mod/graphics/ui/hq-command-deck/deck-plate.png and <figure>.png
  mod/data/ui/hq-command-deck.json    sizes, figure rects, stations, overlay anchors
  pack-review.png                      the packed band with station rectangles drawn
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import compose  # noqa: E402  (beside this script; run with -I)

REPO = HERE.parent.parent
SPRITES = "graphics/ui/hq-command-deck"
OUT_JSON = REPO / "mod/data/ui/hq-command-deck.json"
# Rows of the guide frame the banner shows, about 5:1 at full width: the commander's
# feet (row 583) fall inside it; Sabine, nearest the camera, is cut at the shin.
BAND_TOP, BAND_BOTTOM = 180, 618
PACKED_WIDTH = 2048

# Stations in screen order. Later stations draw over earlier ones where they overlap,
# so the table comes first and the people standing at it after.
STATIONS = [
    ("situation", "SITUATION_BOARD", ["commander"], ["plot", "commander"]),
    ("comms", "COMMS_INTEL", ["comms-left", "comms-right"], ["comms-left", "comms-right", "comms-screens"]),
    ("castle", "CASTLE_ADJUTANT", ["castle"], ["castle"]),
    ("finance", "FINANCE_STANDING", ["quartermaster"], ["quartermaster", "ledger-screen"]),
    ("force", "FORCE_STATUS", ["sabine"], ["sabine"]),
]
PAD = 8  # guide pixels around a station's parts


def main():
    layout = json.loads((HERE / compose.LAYOUT).read_text(encoding="utf-8"))
    size = layout["size"]
    plate = Image.open(HERE / compose.PLATE).convert("RGB")
    if list(plate.size) != size:
        plate = plate.resize(tuple(size), Image.LANCZOS)
    k = PACKED_WIDTH / size[0]
    packed_h = round((BAND_BOTTOM - BAND_TOP) * k)

    def to_packed(x, y):
        return [round(x * k, 1), round((y - BAND_TOP) * k, 1)]

    out_dir = REPO / "mod" / SPRITES
    out_dir.mkdir(parents=True, exist_ok=True)
    band = plate.crop((0, BAND_TOP, size[0], BAND_BOTTOM)).resize((PACKED_WIDTH, packed_h), Image.LANCZOS)
    band.save(out_dir / "deck-plate.png")
    review = band.convert("RGBA")

    figures, parts = [], {}
    for name, (path, bottom_m) in compose.CARDS.items():
        card = Image.open(HERE / path).convert("RGBA")
        matte = Image.open(HERE / "masks" / f"{name}.occluder.png")
        frame, _, _ = compose.layer(card, layout["people"][name], matte, bottom_m, size)
        frame = frame[BAND_TOP:BAND_BOTTOM]
        ys, xs = np.nonzero(frame[..., 3] > 0.004)
        x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
        crop = frame[y0:y1, x0:x1]
        # resample premultiplied, then return to straight alpha for the sprite
        w, h = max(1, round((x1 - x0) * k)), max(1, round((y1 - y0) * k))
        small = np.stack([np.asarray(Image.fromarray((crop[..., i] * 255).round().astype(np.uint8))
                                     .resize((w, h), Image.LANCZOS)) for i in range(4)], -1).astype(float) / 255
        alpha = small[..., 3:]
        rgb = np.where(alpha > 0, small[..., :3] / np.maximum(alpha, 1e-6), 0).clip(0, 1)
        sprite = Image.fromarray((np.concatenate([rgb, alpha], -1) * 255).round().astype(np.uint8), "RGBA")
        sprite.save(out_dir / f"{name}.png")
        rect = [round(x0 * k, 1), round(y0 * k, 1), w, h]
        figures.append({"id": name, "sprite": f"{SPRITES}/{name}.png", "rect": rect})
        review.alpha_composite(sprite, (round(x0 * k), round(y0 * k)))
        parts[name] = (x0, y0 + BAND_TOP, x1, y1 + BAND_TOP)

    m = layout["markers"]
    plot = m["plot-corners"]
    parts["plot"] = (min(p[0] for p in plot), min(p[1] for p in plot), max(p[0] for p in plot), max(p[1] for p in plot))
    screens = list(m["comms-screens"].values()) + list(m["comms-repeaters"].values())
    parts["comms-screens"] = (min(p[0] for p in screens) - 40, min(p[1] for p in screens) - 30,
                              max(p[0] for p in screens) + 40, max(p[1] for p in screens) + 30)
    lx, ly = m["ledger-screen"]
    parts["ledger-screen"] = (lx - 40, ly - 30, lx + 40, ly + 40)

    stations = []
    draw = ImageDraw.Draw(review)
    for sid, label, people, names in STATIONS:
        boxes = [parts[n] for n in names]
        gx0 = max(0, min(b[0] for b in boxes) - PAD)
        gy0 = max(BAND_TOP, min(b[1] for b in boxes) - PAD)
        gx1 = min(size[0], max(b[2] for b in boxes) + PAD)
        gy1 = min(BAND_BOTTOM, max(b[3] for b in boxes) + PAD)
        x0, y0 = to_packed(gx0, gy0)
        x1, y1 = to_packed(gx1, gy1)
        stations.append({"id": sid, "label": label, "figures": people,
                         "rect": [round(x0 / PACKED_WIDTH, 4), round(y0 / packed_h, 4),
                                  round((x1 - x0) / PACKED_WIDTH, 4), round((y1 - y0) / packed_h, 4)]})
        draw.rectangle([x0, y0, x1, y1], outline=(120, 230, 210, 255), width=2)
        draw.text((x0 + 4, y0 + 2), label, fill=(120, 230, 210, 255))

    doc = {
        "size": [PACKED_WIDTH, packed_h],
        "plate": f"{SPRITES}/deck-plate.png",
        "figures": figures,
        "stations": stations,
        "overlays": {
            "plot": [to_packed(*p) for p in plot],
            "screens": [to_packed(*p) for p in screens + list(m["main-display"].values()) + [m["ledger-screen"]]],
        },
        "source": {"plate": compose.PLATE, "layout": compose.LAYOUT, "bandRows": [BAND_TOP, BAND_BOTTOM]},
    }
    OUT_JSON.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    review.convert("RGB").save(HERE / "pack-review.png")
    print("packed", PACKED_WIDTH, "x", packed_h, "with", len(figures), "figures and", len(stations), "stations")


if __name__ == "__main__":
    main()
