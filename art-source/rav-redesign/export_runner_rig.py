"""Export the Runner's bone sprites for the game: the round-5 layers cut bone by bone, as
`exploration/render_runner.py` draws them.

  python export_runner_rig.py

Writes mod/graphics/battle/rav-runner/*.png. Every sprite is laid out the way `RavRunnerComposer`
draws it: the bone runs from its root at the bottom to its end at the top, and the sprite's centre is
the bone's midpoint, so the game turns it toward the projected bone and stretches it to the projected
length without a pivot. Legs and arms come as -l and -r (the right one mirrored).

The bone lengths the composer needs are the row tables below times SCALE; keep
`RavRunnerComposer` in step when a table or SCALE changes.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

HERE = Path(__file__).parent
import stage_sprites  # noqa: E402
sys.path.insert(0, str(HERE / "exploration"))
from motion_runner import key  # noqa: E402
from render_runner import ARM_ROWS, BODY_ROWS, FRONT_ROWS, HIND_ROWS, slices  # noqa: E402

LAYERS = HERE / "exploration" / "rounds" / "round-5" / "layers"
# round 6 slimmed the body from side to side so the stride shows past the flanks; same rows as round 5
BODY = HERE / "exploration" / "rounds" / "round-6" / "layers" / "body-slim-b.png"
OUT = HERE.parent.parent / "mod" / "graphics" / "battle" / "rav-runner"
SCALE = 0.2          # sprite px per layer px; the trunk is the largest sprite and stays under 320 px
MAX_PX = 320


def centred(piece):
    """A bone slice turned root-down and padded so the bone's midpoint is the sprite's centre."""
    img, (cx, ry), length = piece
    img = img.rotate(180)
    w, h = img.size
    mx, my = w - cx, h - ry - length / 2
    a = np.asarray(img.getchannel("A")) > 8
    ys, xs = np.nonzero(a)
    hw = int(np.ceil(max(abs(xs.min() - mx), abs(xs.max() + 1 - mx)))) + 2
    hh = int(np.ceil(max(abs(ys.min() - my), abs(ys.max() + 1 - my)))) + 2
    canvas = Image.new("RGBA", (2 * hw, 2 * hh), (0, 0, 0, 0))
    canvas.alpha_composite(img, (int(round(hw - mx)), int(round(hh - my))))
    out = canvas.resize((max(2, round(canvas.width * SCALE)), max(2, round(canvas.height * SCALE))),
                        Image.LANCZOS)
    if max(out.size) > MAX_PX:
        raise SystemExit(f"sprite {out.size} exceeds {MAX_PX} px; lower SCALE")
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    body = key(BODY)
    n = BODY_ROWS["neck"]
    # the trunk fades out over the neck, where the head piece takes over
    trunk = body.copy()
    a = np.asarray(trunk.getchannel("A")).astype(float)
    ramp = np.clip((np.arange(1536) - (n - 30)) / 40, 0, 1)[:, None]
    trunk.putalpha(Image.fromarray((a * ramp).astype(np.uint8)))
    # the trunk splits at mid-spine so it can bend through a turn: a front half (mid to the shoulders) and a
    # rear half (mid to the hips, tail and all), feathered across the joint so the seam never shows
    mid = (BODY_ROWS["shoulder"] + BODY_ROWS["hip"]) / 2
    half = BODY_ROWS["hip"] - mid
    rows = np.arange(1536)[:, None]
    ta = np.asarray(trunk.getchannel("A")).astype(float)
    front_half = trunk.copy()
    front_half.putalpha(Image.fromarray((ta * np.clip((mid + 24 - rows) / 24, 0, 1)).astype(np.uint8)))
    rear_half = trunk.copy()
    rear_half.putalpha(Image.fromarray((ta * np.clip((rows - (mid - 24)) / 24, 0, 1)).astype(np.uint8)))
    sprites = {
        # root at mid-spine, end at the shoulder row (turned so the shoulders lie below the root, as slices do)
        "trunk-front": (front_half.rotate(180), (512, 1536 - mid), half),
        # root at mid-spine, end at the hip row
        "trunk-rear": (rear_half, (512, mid), half),
        # root at the neck row, end at the head row: the flipped head-and-neck crop
        "fore": (ImageOps.flip(body.crop((0, 0, 1024, n + 40))), (512, 40), n - BODY_ROWS["head"]),
    }
    for name, layer, rows, flip in (("front", "front.png", FRONT_ROWS, False),
                                    ("hind", "hind.png", HIND_ROWS, False),
                                    ("arm", "arm.png", ARM_ROWS, True)):
        for i, piece in enumerate(slices(key(LAYERS / layer), rows, flip)):
            sprites[f"{name}-{i}"] = piece
    for name, piece in sprites.items():
        if name == "fore":
            # the head is drawn the right way round: root down, nose up, the creature's right on the right
            img = body.crop((0, 0, 1024, n + 40))
            out = centred((img.rotate(180), (512, 40), n - BODY_ROWS["head"]))
        else:
            out = centred(piece)
        if name in ("trunk-front", "trunk-rear", "fore"):
            out.save(OUT / f"{name}.png")
        else:
            out.save(OUT / f"{name}-l.png")
            ImageOps.mirror(out).save(OUT / f"{name}-r.png")
        print(f"{name:8s} {out.size}  bone {piece[2] * SCALE:.1f} px")
    # the whelp's and the elder's sets are recoloured from this one, so they never drift from it
    stage_sprites.write("runner")


if __name__ == "__main__":
    main()
