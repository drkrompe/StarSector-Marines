"""C5 NESSA'S YARD: blockout animatic stills (c5-layout.md).

python compose_c5.py            # review/c5-blockout-t0.0 .. t8.0 .png
python compose_c5.py --frames   # also every frame to build/cinematic/c5-nessas-yard/frames

Composes build_c5_guides.py's clay layers at 1920x1080. The camera only pushes in (scale
about one point); nearer layers feel the push more, as in compose_c1.py. The steam layer
drifts and breathes, and each yard crew figure walks parallel to the picture plane at
1.3 m/s, cut out of the crew pass at its own screen position. Nessa does not move.
"""
import argparse
import json
from pathlib import Path

from PIL import Image

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
W, H, FPS, DURATION = 1920, 1080, 24, 8.0
META = json.loads((BASE / "guides" / "c5-camera.json").read_text(encoding="utf-8"))
PUSH = META["push"]
CX, CY = META["push_centre"]
# how strongly each layer takes the push (1.0 = full camera scale)
WEIGHT = {"L1-sky": 0.35, "L2-yard": 0.6, "L3-ship": 0.85, "L3s-steam": 0.85, "L3e-crew": 0.85, "L4-fore": 1.0}
WALK_SPEED = 1.3


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def load(name):
    im = Image.open(BASE / "guides" / ("c5-%s-guide.png" % name)).convert("RGBA")
    return im.resize((W, H), Image.LANCZOS)


def place(canvas, im, s, dx=0.0, dy=0.0, alpha=1.0):
    """Scale a full-frame layer by s about the push centre, then offset."""
    w, h = round(W * s), round(H * s)
    big = im.resize((w, h), Image.BICUBIC)
    x0, y0 = round(CX - CX * s + dx), round(CY - CY * s + dy)
    if alpha < 1.0:
        a = big.getchannel("A").point(lambda v: int(v * alpha))
        big.putalpha(a)
    canvas.alpha_composite(big, (x0, y0)) if x0 >= 0 and y0 >= 0 else canvas.alpha_composite(
        big.crop((-min(0, x0), -min(0, y0), w, h)), (max(0, x0), max(0, y0)))


def crew_cards(crew_pass):
    """Each walker cut from the crew pass around its own feet, plus a static remainder (Nessa)."""
    cards = []
    rest = crew_pass.copy()
    for c in META["crew"]:
        fx, fy = c["start"][0], c["start"][1]
        hh = max(8.0, fy - c["head"][1])
        box = (int(fx - hh * 0.6), int(fy - hh * 1.25), int(fx + hh * 0.6) + 1, int(fy + hh * 0.2) + 1)
        cards.append((crew_pass.crop(box), box, (c["step_1m"][0] - fx, c["step_1m"][1] - fy)))
        rest.paste((0, 0, 0, 0), box)
    return cards, rest


def frame_image(t, L, cards, rest):
    cam = PUSH[0] + (PUSH[1] - PUSH[0]) * smooth(t / DURATION)
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    for name in ("L1-sky", "L2-yard", "L3-ship"):
        place(canvas, L[name], cam ** WEIGHT[name])
    s3 = cam ** WEIGHT["L3e-crew"]
    walkers = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    walkers.alpha_composite(rest)
    for card, box, (sx, sy) in cards:
        d = WALK_SPEED * t
        walkers.alpha_composite(card, (round(box[0] + sx * d), round(box[1] + sy * d)))
    place(canvas, walkers, s3)
    breathe = 0.75 + 0.25 * ((t * 0.35) % 1.0)
    place(canvas, L["L3s-steam"], cam ** WEIGHT["L3s-steam"], dx=6.0 * t, dy=-9.0 * t, alpha=breathe)
    place(canvas, L["L4-fore"], cam ** WEIGHT["L4-fore"])
    return canvas.convert("RGB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frames", action="store_true")
    a = ap.parse_args()
    L = {n: load(n) for n in WEIGHT}
    cards, rest = crew_cards(L["L3e-crew"])
    (BASE / "review").mkdir(exist_ok=True)
    for t in (0.0, 2.5, 5.0, 8.0):
        frame_image(min(t, DURATION - 1 / FPS), L, cards, rest).save(BASE / "review" / ("c5-blockout-t%.1f.png" % t))
    if a.frames:
        out = ROOT / "build" / "cinematic" / "c5-nessas-yard" / "frames"
        out.mkdir(parents=True, exist_ok=True)
        for i in range(round(DURATION * FPS)):
            frame_image(i / FPS, L, cards, rest).save(out / ("%05d.png" % i))
    print("C5 stills in review/")


if __name__ == "__main__":
    main()
