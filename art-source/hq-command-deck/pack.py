"""Pack the command deck for the game: the plate band, the space behind its windows, one sprite per figure, the
screens and buttons the game draws, and the layout.

Run from the repository root:  python -I art-source/hq-command-deck/pack.py
It reads the guide render's id pass, which is regenerated rather than tracked: run `blender/deck_blockout.py` through
the render queue first and copy `deck-guide-v10*` beside this script.

The banner shows a wide band of the 3:1 plate. The plate's keyed screens and windows become holes (`cut_keys.py`),
and its keyed button boards become dark. Figures are packed as separate sprites, each already cut by its stand's
matte, so the screen can light one station's people without redrawing the scene. Everything the game draws comes
from the Blender model's id pass, so a repaint on the same guide needs only a re-run: each screen and each board
of buttons is nudged by however far the painter moved that key, measured here, and a button the model shows hidden
behind something nearer is left out.

Writes:
  mod/graphics/ui/hq-command-deck/deck-plate.png, deck-space.png and <figure>.png
  mod/data/ui/hq-command-deck.json    sizes, figure rects, stations, overlay anchors, screens and buttons
  pack-review.png                      the packed band with stations, screens and buttons drawn
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import compose  # noqa: E402  (beside this script; run with -I)
import cut_keys  # noqa: E402

REPO = HERE.parent.parent
SPRITES = "graphics/ui/hq-command-deck"
OUT_JSON = REPO / "mod/data/ui/hq-command-deck.json"
# Rows of the guide frame the banner shows, about 5:1 at full width: the commander's
# feet (row 583) fall inside it; Morgan, nearest the camera, is cut at the shin.
BAND_TOP, BAND_BOTTOM = 180, 618
PACKED_WIDTH = 2048

# Stations in screen order. Later stations draw over earlier ones where they overlap,
# so the table comes first and the people standing at it after.
STATIONS = [
    ("situation", "SITUATION_BOARD", ["morgan"], ["plot", "morgan"]),
    ("comms", "COMMS_INTEL", ["comms-left", "comms-right"], ["comms-left", "comms-right", "comms-screens"]),
    ("castle", "CASTLE_ADJUTANT", ["castle"], ["castle"]),
    ("finance", "FINANCE_STANDING", ["quartermaster"], ["quartermaster", "ledger-screen"]),
]
PAD = 8  # guide pixels around a station's parts
IDS_PNG, IDS_JSON = HERE / "deck-guide-v10-ids.png", HERE / "deck-guide-v10.ids.json"
SPACE = "deck-space.master.png"
# Figures further from the camera than the table: the hologram above it draws in front of them.
BEHIND_PLOT = {"comms-left", "comms-right", "quartermaster"}
# What each screen shows, by the model's object name.
SCREENS = {
    "status board screen": ("status-board", "schematic"), "ledger screen 0": ("ledger-accounts", "ledger"),
    "ledger screen 1": ("ledger-stores", "bars"), "comms bank 0 crt 0 screen": ("comms-0", "comms"),
    "comms bank 1 crt 0 screen": ("comms-1", "comms"), "helm screen -1": ("helm-course", "sector"),
    "helm screen 1": ("helm-drive", "waveform"), "navigator screen -1": ("navigator-chart", "sector"),
    "navigator screen 1": ("navigator-fix", "telemetry"), "near port screen -1": ("sensors-sweep", "sweep"),
    "near port screen 1": ("sensors-log", "telemetry"), "near aft screen -1": ("fire-control-tracks", "sector"),
    "near aft screen 1": ("fire-control-feed", "waveform"),
}
# What a board of buttons answers to, by the start of its name; the first match wins (`DeckPanels`).
ROLES = [("comms", "comms"), ("ledger indicator", "finance"), ("status board", "actions"), ("systems board", "systems"),
         ("helm", "flight"), ("navigator", "flight")]
HIDDEN_ABOVE = 0.4   # a button is drawn if more than this share of it shows in the id pass


def hull(points):
    points = sorted({tuple(p) for p in points})

    def half(ps):
        out = []
        for p in ps:
            while len(out) > 1 and (out[-1][0] - out[-2][0]) * (p[1] - out[-2][1]) - (out[-1][1] - out[-2][1]) * (p[0] - out[-2][0]) <= 0:
                out.pop()
            out.append(p)
        return out[:-1]

    return half(points) + half(points[::-1])


def corners(points, pad=0.0):
    """Four corners of a projected flat shape, clockwise from top-left, pushed out by `pad` from its middle."""
    tl = min(points, key=lambda p: p[0] + p[1])
    tr = max(points, key=lambda p: p[0] - p[1])
    br = max(points, key=lambda p: p[0] + p[1])
    bl = min(points, key=lambda p: p[0] - p[1])
    quad = [tl, tr, br, bl]
    cx, cy = sum(p[0] for p in quad) / 4, sum(p[1] for p in quad) / 4
    out = []
    for x, y in quad:
        n = max(1e-3, ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5)
        out.append((x + (x - cx) / n * pad, y + (y - cy) / n * pad))
    return out


def drift(ids, object_id, key):
    """How far the painter moved this object's key, as (dx, dy) in guide pixels, and the share of it matched."""
    ys, xs = np.nonzero(ids == object_id)
    if not len(ys):
        return None, 0.0
    h, w = ids.shape
    best, moved = -1, (0, 0)
    for dy in range(-8, 9):
        for dx in range(-8, 9):
            found = key[np.clip(ys + dy, 0, h - 1), np.clip(xs + dx, 0, w - 1)].sum()
            if found > best or (found == best and abs(dx) + abs(dy) < abs(moved[0]) + abs(moved[1])):
                best, moved = found, (dx, dy)
    return moved, best / len(ys)


def resized(rgba, size):
    """Straight RGBA in 0..1, resampled premultiplied so a hole's colour cannot bleed into its edge, as an image."""
    pre = rgba.copy()
    pre[..., :3] *= pre[..., 3:]
    small = np.stack([np.asarray(Image.fromarray((pre[..., i] * 255).round().astype(np.uint8)).resize(size, Image.LANCZOS))
                      for i in range(4)], -1).astype(float) / 255
    alpha = small[..., 3:]
    rgb = np.where(alpha > 0, small[..., :3] / np.maximum(alpha, 1e-6), 0).clip(0, 1)
    return Image.fromarray((np.concatenate([rgb, alpha], -1) * 255).round().astype(np.uint8), "RGBA")


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
    painted = np.asarray(plate).astype(float) / 255
    band = resized(cut_keys.cut(painted)[BAND_TOP:BAND_BOTTOM], (PACKED_WIDTH, packed_h))
    band.save(out_dir / "deck-plate.png")
    space = Image.open(HERE / SPACE).convert("RGB").resize(tuple(size), Image.LANCZOS)
    space = space.crop((0, BAND_TOP, size[0], BAND_BOTTOM)).resize((PACKED_WIDTH, packed_h), Image.LANCZOS)
    space.save(out_dir / "deck-space.png")
    review = Image.new("RGBA", band.size, (40, 44, 50, 255))
    review.alpha_composite(band)

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
        figures.append({"id": name, "sprite": f"{SPRITES}/{name}.png", "rect": rect, "behindPlot": name in BEHIND_PLOT})
        review.alpha_composite(sprite, (round(x0 * k), round(y0 * k)))
        parts[name] = (x0, y0 + BAND_TOP, x1, y1 + BAND_TOP)

    m = layout["markers"]
    plot = m["plot-corners"]
    parts["plot"] = (min(p[0] for p in plot), min(p[1] for p in plot), max(p[0] for p in plot), max(p[1] for p in plot))
    screens = list(m["comms-screens"].values())
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

    ids_doc = json.loads(IDS_JSON.read_text(encoding="utf-8"))
    pixels = np.asarray(Image.open(IDS_PNG).convert("RGB")).astype(int)
    ids = pixels[..., 0] << 16 | pixels[..., 1] << 8 | pixels[..., 2]
    id_of = {name: int(i) for i, name in ids_doc["ids"].items()}
    screen_key, _, board_key = cut_keys.keys(painted)
    screen_key, board_key = screen_key > 0.5, board_key > 0.5

    displays, screen_moves = [], []
    for name, (did, kind) in SCREENS.items():
        moved, matched = drift(ids, id_of[name], screen_key)
        if moved is None or matched < 0.3:
            print("screen", name, "not found in the paint, matched", round(matched, 2))
            moved = (0, 0)
        ys, xs = np.nonzero(ids == id_of[name])
        screen_moves.append((moved, xs.mean(), ys.mean()))
        # a little past the tube, so the plate's hole never shows past the glass drawn behind it
        quad = [to_packed(x + moved[0], y + moved[1]) for x, y in corners(hull(ids_doc["screens"][name]), pad=5.0)]
        displays.append({"id": did, "kind": kind, "quad": quad})
        draw.polygon([tuple(p) for p in quad], outline=(255, 120, 40, 255))

    panels, shown, hidden = [], 0, 0
    for group, buttons in ids_doc["buttons"].items():
        board = id_of.get(f"{group} board")
        if board is None or not (ids == board).any():
            hidden += len(buttons)
            continue
        moved, matched = drift(ids, board, board_key)
        if matched < 0.5:
            # the painter left no patch for this small board: it follows the nearest screen
            ys, xs = np.nonzero(ids == board)
            moved = min(screen_moves, key=lambda v: (v[1] - xs.mean()) ** 2 + (v[2] - ys.mean()) ** 2)[0]
        quads = []
        for points in buttons.values():
            outline = hull(points)
            mask = Image.new("L", (size[0], size[1]))
            ImageDraw.Draw(mask).polygon(outline, fill=255)
            ys, xs = np.nonzero(np.asarray(mask))
            if not len(ys) or (ids[ys, xs] == board).mean() < HIDDEN_ABOVE:
                hidden += 1
                continue
            quad = [to_packed(x + moved[0], y + moved[1]) for x, y in corners(outline)]
            quads.append([v for p in quad for v in p])
            draw.polygon([tuple(p) for p in quad], outline=(120, 200, 255, 255))
        if quads:
            role = next((r for prefix, r in ROLES if group.startswith(prefix)), "idle")
            panels.append({"id": group.replace(" ", "-"), "role": role, "buttons": quads})
            shown += len(quads)
    print("buttons drawn", shown, "hidden", hidden, "on", len(panels), "panels")

    doc = {
        "size": [PACKED_WIDTH, packed_h],
        "plate": f"{SPRITES}/deck-plate.png",
        "space": f"{SPRITES}/deck-space.png",
        "figures": figures,
        "stations": stations,
        "overlays": {
            "plot": [to_packed(*p) for p in plot],
            "screens": [to_packed(*p) for p in screens + [m["status-board"], m["ledger-screen"]]],
        },
        # The deck camera in packed pixels, so live overlays can project points in the room
        # (metres; x right, y up from the deck, z away from the camera) onto the banner.
        # Monitor faces, corners clockwise from top-left in packed pixels; live readouts
        # are drawn into them in perspective, behind the plate's holes.
        "displays": displays,
        # Boards of buttons, each button's corners in packed pixels, drawn over the plate.
        "panels": panels,
        "camera": {"focal": round(layout["camera"]["focal"] * k, 3), "cx": round(layout["camera"]["cx"] * k, 3),
                   "cy": round((layout["camera"]["cy"] - BAND_TOP) * k, 3), "height": layout["camera"]["height"]},
        "table": layout["table"],
        "source": {"plate": compose.PLATE, "layout": compose.LAYOUT, "bandRows": [BAND_TOP, BAND_BOTTOM]},
    }
    OUT_JSON.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    review.convert("RGB").save(HERE / "pack-review.png")
    print("packed", PACKED_WIDTH, "x", packed_h, "with", len(figures), "figures and", len(stations), "stations")


if __name__ == "__main__":
    main()
