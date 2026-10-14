"""PLACEHOLDER damaged and destroyed states for the fort belt's sandbag parapet pieces.

This is procedural stand-in art, not the shipping look. It awaits a geometry-first
ImageGen reskin (see ``art-source/geometry-first-imagegen.md``); when that lands, drop the
real frames into ``fort-sandbag-damage-sources/`` under the same filenames and re-run the
packer. The ids, covers and fighting-position roles below are the contract and do not change.

Each of the eight intact ``doodad.fort-sandbag-*`` frames is cut from the retained raw sheet
(``art-source/tilesets/fort-earthworks.raw.png``, rects taken from the authoring document) and
transformed deterministically (seeded by the piece name, so a re-run is byte-identical):

* damaged   -- the same silhouette with two bites taken out of its rim, soot scorch, and a few
               loose bags beside the breaks. Still heavy cover at the intact half height and still
               a ``parapet`` fighting position.
* destroyed -- no standing parapet: a clear breach where the run was holed, with a low scatter of
               flattened bags and scuffed earth along what was the footprint. No cover, no role.

Fort frames are distinct art per facing (unlike the rotated plain sandbags), so each state is
derived per frame in its own orientation. The result goes through the ordinary packer
(``stitch_atlas.build_atlas``) at the fort sheet's 128px cell into
``mod/graphics/doodads/fort-sandbag-damage.png`` and ``mod/data/tilesets/fort-sandbag-damage.tileset.json``.
"""
import json
import math
import random
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from stitch_atlas import BuildOptions, build_atlas

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
AUTHORING = ROOT / "art-source/tilesets/fort-earthworks.tileset-authoring.json"
SOURCES = HERE / "fort-sandbag-damage-sources"
SHAPES = ("n", "e", "s", "w", "corner-ne", "corner-se", "corner-sw", "corner-nw")
FRAME = 192
HALF_HEIGHT = 0.4
BREACH_RADIUS = 30
PLACEHOLDER = "PLACEHOLDER procedural art awaiting geometry-first ImageGen. "


def load_intact():
    document = json.loads(AUTHORING.read_text(encoding="utf-8"))
    rects = {e["id"]: e["rect"] for e in document["entries"] if e["id"].startswith("doodad.fort-sandbag-")}
    raw = Image.open(ROOT / document["sheet"]).convert("RGBA")
    frames = {}
    for shape in SHAPES:
        x, y, w, h = rects["doodad.fort-sandbag-" + shape]
        if (w, h) != (FRAME, FRAME):
            raise ValueError("Fort sandbag frame is not the reviewed 192px cell")
        frames[shape] = raw.crop((x, y, x + w, y + h))
    return frames


def rng_for(name):
    return random.Random(zlib.crc32(name.encode("utf-8")))


def mean_bag_colour(rgba):
    data = np.asarray(rgba, dtype=np.float64)
    solid = data[..., 3] > 200
    return tuple(data[..., :3][solid].mean(axis=0))


def disc_distance(shape, cx, cy):
    yy, xx = np.ogrid[:shape[0], :shape[1]]
    return np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)


def boundary_points(alpha, lo=40, hi=FRAME - 40):
    solid = alpha > 0.5
    padded = np.pad(solid, 4, constant_values=False)
    inner = padded[4:-4, 4:-4]
    shrunk = inner.copy()
    for dx, dy in ((4, 0), (-4, 0), (0, 4), (0, -4)):
        shrunk &= np.roll(np.roll(padded, dy, axis=0), dx, axis=1)[4:-4, 4:-4]
    edge = inner & ~shrunk
    ys, xs = np.nonzero(edge)
    keep = (xs >= lo) & (xs <= hi) & (ys >= lo) & (ys <= hi)
    return list(zip(xs[keep].tolist(), ys[keep].tolist()))


def bag(draw_layer, cx, cy, rx, ry, angle, colour, rng):
    """One loose sandbag: a shaded rounded blob, drawn on a 4x supersampled layer."""
    scale = 4
    size = int((max(rx, ry) + 4) * 2 * scale)
    tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(tile)
    mid = size / 2
    def ellipse(ox, oy, ex, ey, fill):
        d.ellipse((mid + (ox - ex) * scale, mid + (oy - ey) * scale,
                   mid + (ox + ex) * scale, mid + (oy + ey) * scale), fill=fill)
    jitter = rng.uniform(0.9, 1.05)
    base = tuple(int(max(0, min(255, c * jitter))) for c in colour)
    dark = tuple(int(c * 0.55) for c in colour)
    light = tuple(int(min(255, c * 1.12)) for c in colour)
    ellipse(0, 0, rx, ry, dark + (255,))
    ellipse(-0.6, -0.8, rx - 1.6, ry - 1.6, base + (255,))
    ellipse(-rx * 0.2, -ry * 0.35, rx * 0.55, ry * 0.4, light + (200,))
    tile = tile.rotate(angle, resample=Image.Resampling.BICUBIC)
    tile = tile.resize((size // scale, size // scale), Image.Resampling.LANCZOS)
    draw_layer.alpha_composite(tile, (int(cx - tile.width / 2), int(cy - tile.height / 2)))


def derive_damaged(shape, intact):
    rng = rng_for("damaged-" + shape)
    data = np.asarray(intact, dtype=np.float64) / 255.0
    rgb, alpha = data[..., :3].copy(), data[..., 3].copy()
    candidates = boundary_points(alpha)
    notches = []
    while len(notches) < 2:
        point = rng.choice(candidates)
        if all(math.dist(point, other) > 56 for other in notches):
            notches.append(point)
    for cx, cy in notches:
        radius = rng.uniform(15, 20)
        ox, oy = rng.uniform(-9, 9), rng.uniform(-9, 9)
        for centre, r in (((cx, cy), radius), ((cx + ox, cy + oy), radius * 0.6)):
            ratio = disc_distance(alpha.shape, *centre) / r
            alpha *= np.clip((ratio - 0.85) / 0.25, 0, 1)
        scorch = np.exp(-(disc_distance(alpha.shape, cx, cy) / (radius * 1.9)) ** 2 * 2.0)
        rgb *= (1 - 0.5 * scorch)[..., None]
    for _ in range(2):
        solid = np.argwhere(alpha > 0.5)
        y, x = solid[rng.randrange(len(solid))]
        radius = rng.uniform(22, 34)
        soot = np.exp(-(disc_distance(alpha.shape, x, y) / radius) ** 2 * 2.0) * rng.uniform(0.35, 0.55)
        rgb = rgb * (1 - soot)[..., None] + np.array([0.12, 0.10, 0.09]) * soot[..., None]
    result = Image.fromarray((np.dstack([rgb, alpha]).clip(0, 1) * 255).astype(np.uint8), "RGBA")
    colour = tuple(c * 1.3 for c in mean_bag_colour(intact))
    empty = np.asarray(result)[..., 3] < 8
    for notch, wanted in zip(notches, (2, 1)):
        cx, cy = notch
        placed = 0
        for _ in range(80):
            if placed >= wanted:
                break
            angle = rng.uniform(0, 2 * math.pi)
            distance = rng.uniform(20, 34)
            px, py = int(cx + math.cos(angle) * distance), int(cy + math.sin(angle) * distance)
            if 14 <= px < FRAME - 14 and 14 <= py < FRAME - 14 and empty[py - 9:py + 10, px - 13:px + 14].all():
                bag(result, px, py, rng.uniform(11, 14), rng.uniform(7.5, 9.5), rng.uniform(0, 180), colour, rng)
                empty[py - 9:py + 10, px - 13:px + 14] = False
                placed += 1
    return result


def derive_destroyed(shape, intact):
    rng = rng_for("destroyed-" + shape)
    alpha = np.asarray(intact)[..., 3] / 255.0
    solid = np.argwhere(alpha > 0.5)
    centroid = solid.mean(axis=0)
    nearest = solid[np.argmin(((solid - centroid) ** 2).sum(axis=1))]
    breach_y, breach_x = int(nearest[0]), int(nearest[1])
    ring = [(int(x), int(y)) for y, x in solid
            if math.dist((x, y), (breach_x, breach_y)) >= BREACH_RADIUS + 16 and 10 <= x <= FRAME - 10 and 10 <= y <= FRAME - 10]
    colour = tuple(c * 1.15 for c in mean_bag_colour(intact))
    layer = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
    scuff = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
    sd = ImageDraw.Draw(scuff)
    for _ in range(9):
        x, y = rng.choice(ring)
        r = rng.uniform(10, 18)
        sd.ellipse((x - r, y - r * 0.7, x + r, y + r * 0.7), fill=(74, 60, 44, 90))
    layer.alpha_composite(scuff.filter(ImageFilter.GaussianBlur(3)))
    chosen = []
    for _ in range(400):
        if len(chosen) >= 12:
            break
        x, y = rng.choice(ring)
        x += rng.randint(-5, 5)
        y += rng.randint(-5, 5)
        if math.dist((x, y), (breach_x, breach_y)) < BREACH_RADIUS + 14:
            continue
        if all(math.dist((x, y), other) > 18 for other in chosen):
            chosen.append((x, y))
    for x, y in sorted(chosen, key=lambda p: p[1]):
        bag(layer, x, y, rng.uniform(9, 13), rng.uniform(6, 8), rng.uniform(0, 180), colour, rng)
    return layer


def description(state, shape):
    role = ("Heavy cover and a parapet fighting position remain." if state == "damaged"
            else "No cover and no fighting-position role.")
    what = "Bitten, scorched fort sandbag parapet" if state == "damaged" else "Flattened spill of fort sandbag"
    return f"{PLACEHOLDER}{what}, facing {shape}. {role}"


def write_card(manifest_path, manifest):
    """The packer writes no catalog card; this one says plainly that the art is a placeholder."""
    lines = ["# fort-sandbag-damage tileset", "",
             "Generated by `art-source/doodads/derive_fort_sandbag_damage_frames.py`. Do not edit by hand.", "",
             "**PLACEHOLDER procedural art** awaiting a geometry-first ImageGen reskin; ids and roles are final.", "",
             f"- atlas: `{manifest['sheet']}`", f"- cell size: {manifest['cellPx']} px", "", "## Doodads", "",
             "| id | cover | half height | role | note |", "|---|---|---|---|---|"]
    notes = {c["name"]: c.get("description", "") for c in manifest["cells"]}
    for d in manifest["doodads"]:
        lines.append(f"| `{d['id']}` | {d['cover']} | {d.get('ballisticHalfHeight', 0)} | "
                     f"{d.get('fightingPosition', '—')} | {notes.get(d['id'], '')} |")
    manifest_path.with_suffix(".md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    intact = load_intact()
    SOURCES.mkdir(exist_ok=True)
    assets = {}
    for state in ("damaged", "destroyed"):
        for shape in SHAPES:
            frame = derive_damaged(shape, intact[shape]) if state == "damaged" else derive_destroyed(shape, intact[shape])
            name = f"fort-sandbag-{state}-{shape}"
            frame.save(SOURCES / (name + ".png"), optimize=True)
            damaged = state == "damaged"
            assets[name + ".png"] = {"id": "doodad." + name, "name": "doodad." + name, "order": len(assets),
                "description": description(state, shape),
                "cover": "heavy" if damaged else "none",
                "ballisticHalfHeight": HALF_HEIGHT if damaged else 0,
                **({"fightingPosition": "parapet"} if damaged else {}),
                "preserveCanvas": True, "padding": 0, "scale": 1}
    metadata = SOURCES / "_atlas.json"
    metadata.write_text(json.dumps({"assets": assets}, indent=2) + "\n", encoding="utf-8")
    manifest_path = ROOT / "mod/data/tilesets/fort-sandbag-damage.tileset.json"
    build_atlas(BuildOptions(input_dir=SOURCES, metadata_path=metadata, columns=8, cell_size=128,
        output_image=ROOT / "mod/graphics/doodads/fort-sandbag-damage.png",
        output_manifest=manifest_path, sheet_path="graphics/doodads/fort-sandbag-damage.png"))
    write_card(manifest_path, json.loads(manifest_path.read_text(encoding="utf-8")))


if __name__ == "__main__":
    main()
