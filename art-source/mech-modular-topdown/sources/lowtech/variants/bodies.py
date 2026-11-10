"""Production bodies for the low-tech row's faction variants (owner picks from sheets/pirates-sheet.png,
sheets/path-sheet.png and the flamer sheets): the pirate Hauler and the Luddic Path's Pilgrim on the Bulwark frame, the
Path's Foundry Breaker and the pirate Torch on the Hound frame, and the Path's Censer on the Warden frame
(`mech-lines.md`, law 2; `mechs-nouns.md`, variant). A flamer's fuel load is its body; its burner is a weapon layer of
its own (flamer_parts.py).

A variant keeps its frame's mounts, so each guide is the frame's own production master registered exactly as the
runtime fits it (build.fitted: the outline's box filling the chassis square's height, centred), shrunk by FRAME_SCALE
inside a larger canvas so the variant's own parts can reach past the frame's outline (the Pilgrim's blade, the
Hauler's crane and chained slabs). Its new parts are drawn on top as flat grey blocks for ImageGen to fill in. The
runtime sprite is the whole canvas at 208 / FRAME_SCALE px, so the composer, which sizes a chassis sprite by its pixel
size over 208 (LayeredMechComposer.SOURCE_REFERENCE_PX), draws the frame part at exactly the frame's size and
registration and the extra parts past it.

Places kept clear, in hull widths from the frame's centre, +y forward (family_guide.py, hound/guide.py):
  Bulwark  arm guns under the shoulders, rear pivots (+-0.37, -0.15), barrels ahead of the shoulders;
           racks over the chassis, rear pivots (+-0.40, -0.30), over the shoulder pads
  Hound    nose gun under the chassis up the nose channel, rear pivot (0, -0.02), about 0.54 long;
           dorsal pod over the chassis, rear pivot (0, -0.30), 0.42 long and 0.23 wide
  Warden   nose gun under the chassis, rear pivot (0, -0.05); racks over the chassis, rear pivots (+-0.40, -0.30),
           the right one as long as a lance (0.62)

  python art-source/mech-modular-topdown/sources/lowtech/variants/bodies.py guides [name ...]
  python tools/codex_imagegen.py --prompt <name>/<name>.prompt.txt --out <name>/<name>-master.png
      --ref <name>/guide.png="..." --ref sheets/<sheet>-<name>.png="..." [--ref ref-path-material.png="..."]
  python art-source/mech-modular-topdown/sources/lowtech/variants/bodies.py pack [name ...]

Runtime output: mod/graphics/battle/mech-modular-topdown/variants/<variant id>.png (MechVariantBodies)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
LOWTECH = HERE.parent
REPOSITORY = HERE.parents[4]
OUT = REPOSITORY / "mod" / "graphics" / "battle" / "mech-modular-topdown" / "variants"
S = 1254
CHASSIS_PX = 208

BLOCK = (128, 128, 124, 255)
DARK = (92, 92, 88, 255)
LIGHT = (160, 160, 156, 255)
LINE = (44, 44, 44, 255)


class Body:
    def __init__(self, name, variant_id, frame_master, frame_scale, sheet, sheet_box):
        self.name = name
        self.variant_id = variant_id
        self.frame_master = frame_master
        self.frame_scale = frame_scale
        self.sheet = sheet
        self.sheet_box = sheet_box

    def runtime_px(self) -> int:
        return round(CHASSIS_PX / self.frame_scale)

    def px(self, x: float, y: float) -> tuple[float, float]:
        """Frame hull widths (+y forward) as guide pixels."""
        return S / 2 + x * self.frame_scale * S, S / 2 - y * self.frame_scale * S


def frame_layer(body: Body) -> Image.Image:
    """The frame master as build.fitted registers it, shrunk by the body's scale into the guide canvas."""
    image = Image.open(LOWTECH / body.frame_master).convert("RGBA")
    box = image.getchannel("A").point(lambda a: 255 if a >= 24 else 0).getbbox()
    image = image.crop(box)
    height = body.frame_scale * S
    width = image.width * height / image.height
    image = image.resize((round(width), round(height)), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (S, S))
    canvas.alpha_composite(image, (round((S - image.width) / 2), round((S - image.height) / 2)))
    return canvas


def rect(d, body, x0, y0, x1, y1, fill=BLOCK, radius=0.015):
    a, b = body.px(x0, y1), body.px(x1, y0)
    d.rounded_rectangle((a[0], a[1], b[0], b[1]), radius=radius * S, fill=fill, outline=LINE, width=6)


def poly(d, body, points, fill=BLOCK):
    d.polygon([body.px(x, y) for x, y in points], fill=fill, outline=LINE, width=6)


def ring(d, body, x, y, r, fill=DARK):
    cx, cy = body.px(x, y)
    rr = r * body.frame_scale * S
    d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=fill, outline=LINE, width=6)


def chain(d, body, x, y0, y1):
    a, b = body.px(x, y0), body.px(x, y1)
    d.line((a, b), fill=LINE, width=8)


def hauler(body: Body, d: ImageDraw.ImageDraw) -> None:
    # Cargo cage on the back, between the racks' places: bars round lashed crates and a canister.
    rect(d, body, -0.21, -0.40, 0.21, 0.02, LIGHT, 0.01)
    for x in (-0.14, 0.0, 0.14):
        rect(d, body, x - 0.006, -0.40, x + 0.006, 0.02, DARK, 0.0)
    for y in (-0.26, -0.12):
        rect(d, body, -0.21, y - 0.006, 0.21, y + 0.006, DARK, 0.0)
    # Folding salvage crane on the outer left shoulder, folded back along the flank, outside the rack's place.
    rect(d, body, -0.51, -0.24, -0.42, 0.22, BLOCK, 0.02)
    ring(d, body, -0.465, 0.22, 0.045)
    ring(d, body, -0.465, -0.22, 0.04)
    # The hook hangs off the crane's front end.
    ring(d, body, -0.44, 0.30, 0.025, LIGHT)
    # Scrap slabs hung on chains off the flanks, past the shoulder pads.
    for x0, x1, y0, y1 in ((0.42, 0.53, -0.12, 0.04), (0.42, 0.54, -0.36, -0.16), (-0.53, -0.42, -0.46, -0.30)):
        chain(d, body, (x0 + x1) / 2, y1, y1 + 0.05)
        rect(d, body, x0, y0, x1, y1, BLOCK, 0.01)
    # The empty socket ring on the right shoulder.
    ring(d, body, 0.35, -0.04, 0.05)


def pilgrim(body: Body, d: ImageDraw.ImageDraw) -> None:
    # The dozer blade across the FRONT, ahead of the head plate and between the arm guns, on two push arms.
    for side in (-1, 1):
        rect(d, body, side * 0.20 - 0.03, 0.36, side * 0.20 + 0.03, 0.49, DARK, 0.01)
    poly(d, body, [(-0.33, 0.46), (-0.20, 0.49), (0.20, 0.49), (0.33, 0.46), (0.35, 0.56), (0.20, 0.585),
                   (-0.20, 0.585), (-0.35, 0.56)], LIGHT)
    # Ore-hopper ribs down the back.
    rect(d, body, -0.15, -0.36, 0.15, 0.04, BLOCK, 0.01)
    for y in (-0.30, -0.22, -0.14, -0.06):
        rect(d, body, -0.15, y - 0.008, 0.15, y + 0.008, DARK, 0.0)
    # Empty socket rings on both shoulders.
    for side in (-1, 1):
        ring(d, body, side * 0.35, -0.04, 0.05)


def breaker(body: Body, d: ImageDraw.ImageDraw) -> None:
    # A heavy foundry-cast breaching collar round the nose, the gun channel left open down its middle.
    for side in (-1, 1):
        poly(d, body, [(side * 0.045, 0.30), (side * 0.15, 0.30), (side * 0.15, 0.44), (side * 0.10, 0.52),
                       (side * 0.045, 0.52)], DARK)
    # Five shell canisters lying across the back, behind the dorsal pod's place.
    for i in range(5):
        y = -0.31 - i * 0.042
        rect(d, body, -0.13, y - 0.018, 0.13, y + 0.018, LIGHT, 0.018)


def torch(body: Body, d: ImageDraw.ImageDraw) -> None:
    # The fuel load: a welded cradle across the back holding four mismatched drums round a pressure bottle, behind
    # the nose channel, which stays open for the burner.
    rect(d, body, -0.24, -0.50, 0.24, -0.06, DARK, 0.012)
    for x, y in ((-0.125, -0.17), (0.125, -0.17), (-0.125, -0.39), (0.125, -0.39)):
        ring(d, body, x, y, 0.095, LIGHT)
    rect(d, body, -0.032, -0.46, 0.032, -0.10, BLOCK, 0.03)
    # Hoses forward from the cradle along both flanks of the nose channel.
    for side in (-1, 1):
        rect(d, body, side * 0.075 - 0.012, -0.06, side * 0.075 + 0.012, 0.10, DARK, 0.01)


def censer(body: Body, d: ImageDraw.ImageDraw) -> None:
    # The promethium tank lying across the rear, past the frame's outline, in its welded frame.
    rect(d, body, -0.33, -0.60, 0.33, -0.40, LIGHT, 0.09)
    for x in (-0.20, 0.0, 0.20):
        rect(d, body, x - 0.008, -0.60, x + 0.008, -0.40, DARK, 0.0)
    # Gauges and a manifold on the tank's front, and the armoured hose up the right flank to the right mount's base.
    ring(d, body, -0.12, -0.38, 0.035, BLOCK)
    ring(d, body, 0.10, -0.38, 0.035, BLOCK)
    poly(d, body, [(0.20, -0.42), (0.26, -0.42), (0.34, -0.30), (0.28, -0.27)], DARK)
    # Empty bolted plates on both rack places, and prayer cloth strips off the left flank and the right hip.
    ring(d, body, -0.31, -0.22, 0.05)
    ring(d, body, 0.31, -0.22, 0.05)
    for x0, y0 in ((-0.29, 0.02), (-0.255, -0.02)):
        rect(d, body, x0, y0 - 0.12, x0 + 0.025, y0, LIGHT, 0.004)


BODIES = {
    "hauler": (Body("hauler", "hauler", "pirates/bulwark-master.png", 0.80, "pirates-sheet.png", (1400, 0, 2116, 743)), hauler),
    "pilgrim": (Body("pilgrim", "pilgrim", "bulwark/bulwark-master.png", 0.80, "path-sheet.png", (1400, 0, 2116, 743)), pilgrim),
    "breaker": (Body("breaker", "foundry_breaker", "hound/canopy-1-master.png", 0.88, "path-sheet.png", (130, 0, 560, 743)), breaker),
    "torch": (Body("torch", "torch", "pirates/hound-master.png", 0.88, "pirate-torch-sheet.png", (270, 0, 730, 887)), torch),
    "censer": (Body("censer", "censer", "warden/warden-master.png", 0.84, "path-censer-sheet.png", (250, 0, 970, 1010)), censer),
}


def chosen() -> dict:
    """The bodies named after the command, or all of them."""
    names = sys.argv[2:]
    return {n: BODIES[n] for n in names} if names else BODIES


def guides() -> None:
    for name, (body, draw) in chosen().items():
        (HERE / name).mkdir(exist_ok=True)
        guide = frame_layer(body)
        draw(body, ImageDraw.Draw(guide))
        guide.save(HERE / name / "guide.png")
        Image.open(HERE / "sheets" / body.sheet).crop(body.sheet_box).save(HERE / name / "concept.png")
    print("wrote", ", ".join(f"{n}/guide.png" for n in chosen()))


def pack() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (body, _) in chosen().items():
        master = Image.open(HERE / name / f"{name}-master.png").convert("RGBA")
        if master.size != (S, S):
            raise ValueError(f"{name}: master is {master.size}, its guide {S}x{S}")
        size = body.runtime_px()
        master.resize((size, size), Image.Resampling.LANCZOS).save(OUT / f"{body.variant_id}.png")
    print("packed", ", ".join(chosen()), "into", OUT)


if __name__ == "__main__":
    {"guides": guides, "pack": pack}[sys.argv[1] if len(sys.argv) > 1 else "guides"]()
