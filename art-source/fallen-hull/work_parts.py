"""The salvage walkers' work parts, cut from their shipped torsos (owner, 2026-11-11: arms that unfold to work and tuck
away to walk, and a bed that visibly fills).

export.py's --ship calls this on each walker it ships, over the torso it has just cut from the master; run alone, it
cuts each walker's torso from the master again (export.py's cut, into build/) and writes the parts into mod/. Parts at
128 px a cell (DerelictWalkerAssets.PX_PER_CELL):

  graphics/battle/derelict-stripper/
    torso.png    the torso with its two cutting arms taken off at the shoulders (the arms are drawn as parts)
    arm.png      one cutting arm's boom, upright: its shoulder end at the bottom, the saw's mount at the top
    saw.png      one saw disc with its guard, centred on its hub
    pile.png     a heap of cut scrap on the ground, the salvage a stripper works (drawn shrinking as it is cut)
  graphics/battle/derelict-hauler/
    torso.png    the torso with its load bed empty: a dark ribbed floor between the bed's plated sides
    load-1..4.png the bed's scrap at a quarter, half, three quarters and full, each the bed's inner box, filled from
                 the rear forward in clumps

The arms' and bed's geometry (shoulders, bed box) is fixed here against the shipped master; a new master needs these
numbers measured again.
"""
import argparse
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]

# Stripper torso (307 x 297 px): the left arm's saw hub and radius, its boom's box, and the line the arms are taken
# off above (the body's top edge), keeping the sensor head between them.
SAW_HUB = (97, 47)
SAW_RADIUS = 40
BOOM_BOX = (76, 64, 116, 152)
SHOULDER_Y = 150
HEAD_SPAN = (116, 186)
HEAD_TOP = 100

# Hauler torso (261 x 253 px): the load bed's inner box.
BED_BOX = (60, 121, 202, 201)
LOAD_STAGES = 4


def stripper(folder: Path, source: Path):
    torso = Image.open(source / "torso.png").convert("RGBA")
    w, h = torso.size
    # The saw: the disc and its guard, kept within its circle.
    hx, hy = SAW_HUB
    r = SAW_RADIUS
    saw = torso.crop((hx - r, hy - r, hx + r, hy + r))
    mask = Image.new("L", saw.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, 2 * r - 1, 2 * r - 1], fill=255)
    saw.putalpha(Image.composite(saw.getchannel("A"), Image.new("L", saw.size, 0), mask))
    # The boom: the column under the disc, the disc's lower half taken off it.
    boom = torso.crop(BOOM_BOX)
    bm = boom.getchannel("A").copy()
    d = ImageDraw.Draw(bm)
    bx0, by0 = BOOM_BOX[0], BOOM_BOX[1]
    d.ellipse([hx - r - bx0, hy - r - by0, hx + r - bx0, hy + r - by0], fill=0)
    # Its top: a small mount plate where the saw's hub sits, so the boom reads whole under a turning disc.
    boom.putalpha(bm)
    top = max(0, hy + r - by0 - 14)
    boom = boom.crop((0, top, boom.width, boom.height))
    # The body: everything above the shoulders off, except the sensor head between the arms.
    body = torso.copy()
    alpha = body.getchannel("A")
    d = ImageDraw.Draw(alpha)
    d.rectangle([0, 0, HEAD_SPAN[0] - 1, SHOULDER_Y - 1], fill=0)
    d.rectangle([HEAD_SPAN[1], 0, w - 1, SHOULDER_Y - 1], fill=0)
    # The discs' teeth that reach in over the head's span, above the head.
    d.rectangle([0, 0, w - 1, HEAD_TOP - 1], fill=0)
    body.putalpha(alpha)
    folder.mkdir(parents=True, exist_ok=True)
    body.save(folder / "torso.png")
    boom.save(folder / "arm.png")
    saw.save(folder / "saw.png")


def noise(w, h, seed, scale):
    """Smooth value noise in [0, 1], blobs about {@code scale} px across."""
    rnd = random.Random(seed)
    gw, gh = int(w / scale) + 2, int(h / scale) + 2
    grid = Image.new("L", (gw, gh))
    grid.putdata([rnd.randint(0, 255) for _ in range(gw * gh)])
    return grid.resize((int(gw * scale), int(gh * scale)), Image.BICUBIC).crop((0, 0, w, h))


def hauler(folder: Path, source: Path):
    torso = Image.open(source / "torso.png").convert("RGBA")
    x0, y0, x1, y1 = BED_BOX
    bw, bh = x1 - x0, y1 - y0
    scrap = torso.crop(BED_BOX)
    # The empty floor: the bed's darkest tone, ribbed across, darker toward its walls, a little grime.
    px = sorted(sum(scrap.getpixel((x, y))[:3]) for x in range(0, bw, 3) for y in range(0, bh, 3))
    base = px[len(px) // 4] / 3 + 10
    floor = Image.new("RGBA", (bw, bh))
    rnd = random.Random(11)
    fd = floor.load()
    for y in range(bh):
        rib = 16 if (y % 12) in (0, 1) else (-10 if (y % 12) in (2, 3) else 0)
        for x in range(bw):
            edge = min(x, bw - 1 - x, y, bh - 1 - y)
            shade = base + rib - max(0, 10 - edge) * 1.6 + rnd.uniform(-4, 4)
            v = max(0, min(255, int(shade)))
            fd[x, y] = (v + 6, v + 5, v, 255)
    floor = floor.filter(ImageFilter.SMOOTH)
    body = torso.copy()
    body.paste(floor, (x0, y0))
    # The load stages: the scrap revealed in clumps, the rear of the bed first.
    field = noise(bw, bh, 23, 18)
    fp = field.load()
    order = Image.new("L", (bw, bh))
    op = order.load()
    for y in range(bh):
        rear = y / (bh - 1)
        for x in range(bw):
            op[x, y] = int(255 * (0.55 * (1 - rear) + 0.45 * fp[x, y] / 255))
    folder.mkdir(parents=True, exist_ok=True)
    body.save(folder / "torso.png")
    ranked = sorted(order.tobytes())
    for k in range(1, LOAD_STAGES + 1):
        # Each stage shows its share of the scrap: the threshold at that quantile of the fill order.
        cut = ranked[min(len(ranked) - 1, len(ranked) * k // LOAD_STAGES)]
        m = order.point(lambda v, c=cut: 255 if v <= c else 0)
        if k == LOAD_STAGES:
            m = Image.new("L", (bw, bh), 255)
        m = m.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
        stage = scrap.copy()
        stage.putalpha(m)
        stage.save(folder / f"load-{k}.png")
    return scrap


PILE_SIZE = (176, 152)


def pile(folder: Path, scrap: Image.Image):
    """A scrap heap: pieces of the bed's full load strewn at random turns, densest at the heap's crown, the lower
    layers shaded as the heap's foot."""
    w, h = PILE_SIZE
    rnd = random.Random(41)
    heap = Image.new("RGBA", (w, h))
    for layer in range(3):
        for _ in range(26 - 6 * layer):
            size = rnd.randint(30, 46) - 6 * layer
            sx, sy = rnd.randint(0, scrap.width - size), rnd.randint(0, scrap.height - size)
            piece = scrap.crop((sx, sy, sx + size, sy + size))
            disc = Image.new("L", piece.size, 0)
            ImageDraw.Draw(disc).ellipse([1, 1, size - 2, size - 2], fill=255)
            piece.putalpha(disc.filter(ImageFilter.GaussianBlur(2)))
            piece = piece.rotate(rnd.uniform(0, 360), resample=Image.BICUBIC, expand=True)
            spread = 0.42 - 0.12 * layer
            r, t = min(0.62, abs(rnd.gauss(0, spread))), rnd.uniform(0, 2 * math.pi)
            cx, cy = w / 2 + math.cos(t) * r * w / 2, h / 2 + math.sin(t) * r * h / 2
            shade = 0.62 + 0.2 * layer
            piece = Image.merge("RGBA", [c.point(lambda v, f=shade: int(v * f)) for c in piece.split()[:3]]
                                + [piece.getchannel("A")])
            heap.alpha_composite(piece, (int(cx - piece.width / 2), int(cy - piece.height / 2)))
    # The heap's shadow on the ground under it.
    shadow = Image.new("L", (w, h), 0)
    ImageDraw.Draw(shadow).ellipse([w * 0.1, h * 0.12, w * 0.9, h * 0.92], fill=150)
    base = Image.new("RGBA", (w, h), (20, 18, 14, 0))
    base.putalpha(shadow.filter(ImageFilter.GaussianBlur(8)))
    base.alpha_composite(heap)
    folder.mkdir(parents=True, exist_ok=True)
    base.save(folder / "pile.png")


def cut_into(design: str, folder: Path, source: Path):
    """The work parts of {@code design} into {@code folder}, from the as-cut parts in {@code source} (its torso with
    the arms and the full bed, as export.py cuts it)."""
    if design == "stripper":
        stripper(folder, source)
    elif design == "hauler":
        scrap = hauler(folder, source)
        pile(folder.parent / "derelict-stripper", scrap)


def main():
    import importlib.util
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=str(ROOT / "mod"))
    args = ap.parse_args()
    spec = importlib.util.spec_from_file_location("fallen_hull_export", Path(__file__).with_name("export.py"))
    export = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(export)
    cut = ROOT / "build" / "fallen-hull-art"
    for design in ("stripper", "hauler"):
        export.export_walker(design, cut)
        cut_into(design, Path(args.root) / "graphics" / "battle" / f"derelict-{design}", cut / f"walker-{design}")
    print("cut the work parts into", args.root)


if __name__ == "__main__":
    main()
