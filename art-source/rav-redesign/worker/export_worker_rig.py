"""Export one skitterer look option as a bone set on the Runner's skeleton.

  python export_worker_rig.py --option hauler|tender|mite [--out <dir>]

Writes trunk-front.png, trunk-rear.png, fore.png, front-{0,1}-{l,r}.png and hind-{0,1,2}-{l,r}.png into
mod/graphics/battle/rav-worker/ (or --out), overwriting. There are no arm sprites: the skitterer has no spike arms,
and its pincer mandibles are part of fore.png.

The body raw (raw/options/<option>-body-v2.png) is painted on the Runner body layer's canvas and rows, so it is cut
exactly as `../export_runner_rig.py` cuts the Runner: the trunk fades out over the neck, splits at mid-spine into a
front and a rear half, and the head is the crop above the neck, each laid out root-down with the bone's midpoint at the
sprite's centre at the same SCALE. The bone px `RavRunnerComposer` holds (TRUNK_HALF_PX, FORE_PX) therefore hold for
the worker unchanged; the game draws the whole worker smaller by its own scale.

Legs are the Runner's shipped leg sprites, narrowed across (the height, so the bone length and pivot, is untouched)
and recoloured to the option's plates, so they never drift from the Runner's skeleton.
"""
import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

HERE = Path(__file__).parent
REDESIGN = HERE.parent
sys.path.insert(0, str(REDESIGN))
sys.path.insert(0, str(REDESIGN / "exploration"))
from cut_clean import save  # noqa: E402
from export_runner_rig import centred  # noqa: E402
from motion_runner import key  # noqa: E402
from render_runner import BODY_ROWS  # noqa: E402

GRAPHICS = REDESIGN.parent.parent / "mod" / "graphics" / "battle"
RUNNER = GRAPHICS / "rav-runner"
OUT = GRAPHICS / "rav-worker"
LEGS = ["front-0", "front-1", "hind-0", "hind-1", "hind-2"]

# per option: the accepted body raw, how much narrower than the Runner's its legs are, and their recolour
OPTIONS = {
    # heavier legs under a broad back, a shade duskier
    "hauler": dict(body="hauler-body-v2.png", leg_width=0.88, lift=0.0, tint=(0.94, 0.90, 0.98)),
    # slim legs under a laden sac
    "tender": dict(body="tender-body-v2.png", leg_width=0.78, lift=0.0, tint=(1.0, 1.0, 1.0)),
    # pale whelp-lilac legs to match its pale plates
    "mite": dict(body="mite-body-v2.png", leg_width=0.84, lift=0.22, tint=(1.0, 0.98, 1.0)),
}


def body_pieces(body):
    """trunk-front, trunk-rear and fore from a body layer, cut as the Runner's are."""
    n = BODY_ROWS["neck"]
    trunk = body.copy()
    a = np.asarray(trunk.getchannel("A")).astype(float)
    ramp = np.clip((np.arange(body.height) - (n - 30)) / 40, 0, 1)[:, None]
    trunk.putalpha(Image.fromarray((a * ramp).astype(np.uint8)))
    mid = (BODY_ROWS["shoulder"] + BODY_ROWS["hip"]) / 2
    half = BODY_ROWS["hip"] - mid
    rows = np.arange(body.height)[:, None]
    ta = np.asarray(trunk.getchannel("A")).astype(float)
    front = trunk.copy()
    front.putalpha(Image.fromarray((ta * np.clip((mid + 24 - rows) / 24, 0, 1)).astype(np.uint8)))
    rear = trunk.copy()
    rear.putalpha(Image.fromarray((ta * np.clip((rows - (mid - 24)) / 24, 0, 1)).astype(np.uint8)))
    head = body.crop((0, 0, body.width, n + 40))
    return {
        "trunk-front": centred((front.rotate(180), (512, body.height - mid), half)),
        "trunk-rear": centred((rear, (512, mid), half)),
        "fore": centred((head.rotate(180), (512, 40), n - BODY_ROWS["head"])),
    }


def leg(img, width, lift, tint):
    """A Runner leg sprite narrowed about its centre line and recoloured; its height and centre stay put."""
    w = max(2, round(img.width * width))
    w += (img.width - w) % 2                       # keep the centre column where it was
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.alpha_composite(img.resize((w, img.height), Image.LANCZOS), ((img.width - w) // 2, 0))
    a = np.asarray(out).astype(float)
    rgb = a[..., :3] * np.array(tint)
    rgb = rgb + (255 - rgb) * lift
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), a[..., 3:]]).astype(np.uint8), "RGBA")


def export(option, out_dir):
    spec = OPTIONS[option]
    out_dir.mkdir(parents=True, exist_ok=True)
    body = key(HERE / "raw" / "options" / spec["body"])
    if body.size != (1024, 1536):
        raise SystemExit(f"{spec['body']} is {body.size}; the cut assumes the Runner body layer's 1024x1536 canvas")
    for name, img in body_pieces(body).items():
        save(img, out_dir / f"{name}.png")
        print(f"{name:12s} {img.size}")
    for name in LEGS:
        img = leg(Image.open(RUNNER / f"{name}-l.png").convert("RGBA"), spec["leg_width"], spec["lift"], spec["tint"])
        save(img, out_dir / f"{name}-l.png")
        save(ImageOps.mirror(img), out_dir / f"{name}-r.png")
        print(f"{name:12s} {img.size}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", required=True, choices=sorted(OPTIONS))
    ap.add_argument("--out", type=Path, default=OUT)
    args = ap.parse_args()
    export(args.option, args.out)


if __name__ == "__main__":
    main()
