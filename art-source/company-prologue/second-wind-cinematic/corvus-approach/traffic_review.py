"""C1 traffic review: stills, a contact sheet across the shot and low-res motion strips.

python traffic_review.py [--sheet] [--strip x0,y0,x1,y1] [--times 2.5,7,...] [--out DIR]

Imports compose_c1 and renders frames at chosen times without building the movie.
Writes to the scratch directory given by --out (default: review/traffic/).
"""
import argparse
import sys
import time
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import compose_c1  # noqa: E402
import c1_ship  # noqa: E402
from c1_core import BASE, Element  # noqa: E402

_ship_build = c1_ship.build


def _ship_or_stand_in(add, duration):
    """The ship pass is edited concurrently; when its module is mid-change, review the
    traffic against the earlier fixed entrance card instead of failing."""
    try:
        staged = []
        _ship_build(lambda *a, **k: staged.append((a, k)) or Element(*a, **k), duration)
    except Exception as e:  # noqa: BLE001
        print("c1_ship unavailable (%s); using the stand-in entrance card" % e)
        card = Image.open(BASE / "layers" / "second-wind-entrance.png").convert("RGBA")
        card = card.crop(card.getchannel("A").getbbox())
        card = card.resize((760, round(card.height * 760 / card.width)), Image.LANCZOS)
        add("second-wind", card, 0.6, [(0.0, -300, 1330, 1.6), (6.5, 470, 820, 0.78), (duration, 760, 700, 0.42)])
        return
    _ship_build(add, duration)


c1_ship.build = _ship_or_stand_in


def _gif_frame(args):
    t, dur, box, scale = args
    im = compose_c1.frame_image(t, dur).crop(box)
    im = im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)
    ImageDraw.Draw(im).text((4, 2), "%.1f" % t, fill=(255, 255, 0))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(Path(__file__).resolve().parent / "review" / "traffic"))
    ap.add_argument("--times", default="")
    ap.add_argument("--sheet", action="store_true")
    ap.add_argument("--strip", default="", help="crop box x0,y0,x1,y1 for a frame strip")
    ap.add_argument("--strip-times", default="")
    ap.add_argument("--gif", default="", help="crop box for a low-res gif x0,y0,x1,y1")
    ap.add_argument("--gif-fps", type=float, default=8)
    ap.add_argument("--gif-scale", type=float, default=0.5)
    ap.add_argument("--dump", action="store_true", help="print each top-level element's place over the shot")
    a = ap.parse_args()
    out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    dur = compose_c1.c1_duration()
    t0 = time.time()
    for t in [float(x) for x in a.times.split(",") if x]:
        compose_c1.frame_image(t, dur).save(out / ("t%05.1f.png" % t))
    if a.dump:
        compose_c1.frame_image(0.0, dur)
        for el in compose_c1.SCENE:
            if el.parent is None and el.name not in ("sky", "planet") and not el.name.startswith("orbit-light"):
                row = []
                for t in (0.0, dur / 2, dur):
                    x, y, sc = el.place(t)
                    w, h = el.image(t).size
                    row.append("(%5.0f,%4.0f) %3.0fpx d%.3f" % (x, y, max(w, h) * sc, el.depth_at(t)))
                print("%-28s %s" % (el.name, "  ".join(row)))
    if a.sheet:
        ts = [0.5 + i * (dur - 1) / 8 for i in range(9)]
        sheet = Image.new("RGB", (1920, 1080))
        for i, t in enumerate(ts):
            im = compose_c1.frame_image(t, dur).resize((640, 360), Image.LANCZOS)
            ImageDraw.Draw(im).text((6, 4), "%.1fs" % t, fill=(255, 255, 0))
            sheet.paste(im, ((i % 3) * 640, (i // 3) * 360))
        sheet.save(out / "sheet.png")
    if a.strip:
        x0, y0, x1, y1 = [int(v) for v in a.strip.split(",")]
        ts = [float(x) for x in a.strip_times.split(",")] if a.strip_times else [i * dur / 7 for i in range(8)]
        tiles = [compose_c1.frame_image(t, dur).crop((x0, y0, x1, y1)) for t in ts]
        w, h = tiles[0].size
        cols = 4
        strip = Image.new("RGB", (w * cols, h * ((len(tiles) + cols - 1) // cols)))
        for i, (t, im) in enumerate(zip(ts, tiles)):
            ImageDraw.Draw(im).text((4, 2), "%.1fs" % t, fill=(255, 255, 0))
            strip.paste(im, ((i % cols) * w, (i // cols) * h))
        strip.save(out / "strip.png")
    if a.gif:
        box = tuple(int(v) for v in a.gif.split(","))
        n = int(dur * a.gif_fps)
        from concurrent.futures import ProcessPoolExecutor
        with ProcessPoolExecutor(12) as pool:
            frames = list(pool.map(_gif_frame, [(i / a.gif_fps, dur, box, a.gif_scale) for i in range(n)],
                                   chunksize=4))
        frames[0].save(out / "motion.gif", save_all=True, append_images=frames[1:], duration=int(1000 / a.gif_fps),
                       loop=0)
    print("done in %.1fs -> %s" % (time.time() - t0, out))


if __name__ == "__main__":
    main()
