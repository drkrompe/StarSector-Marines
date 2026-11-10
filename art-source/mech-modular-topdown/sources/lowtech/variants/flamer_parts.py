"""Runtime drawings of the machine flamers (`mechs-nouns.md`, Machine flamers), each its own swappable weapon layer
(`mech-lines.md`, law 4): the FL-1, the pirate Torch's burner, cut from the loose part on sheets/pirate-torch-sheet.png
(transparent already), and the FL-2, the Path Censer's lance, generated clean on a transparent ground from the loose part
on sheets/path-censer-sheet.png, whose dark backdrop and glow would not cut clean.

  python art-source/mech-modular-topdown/sources/lowtech/variants/flamer_parts.py concepts
  python tools/codex_imagegen.py --prompt parts/censer-lance.prompt.txt --out parts/censer-lance-master.png
      --ref parts/censer-lance-concept.png="Identity, design and detail of the chosen part; copy it, ..."
  python art-source/mech-modular-topdown/sources/lowtech/variants/flamer_parts.py pack

Each drawing points to the top, its rear edge the mount's pivot, at the size the composer draws it (208 px a hull
width): the burner 118 px long (MechHardpointGeometry, ARMS_FL_1), the lance 156 px (POD_FL_2).

Runtime output: mod/graphics/battle/mech-modular-topdown/fl-1.png and fl-2.png
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parents[4]
OUT = REPOSITORY / "mod" / "graphics" / "battle" / "mech-modular-topdown"

# runtime file: (source, canvas size)
PARTS = {
    "fl-1.png": ("parts/torch-burner-concept.png", (44, 118)),
    "fl-2.png": ("parts/censer-lance-master.png", (52, 156)),
}


def concepts() -> None:
    (HERE / "parts").mkdir(exist_ok=True)
    Image.open(HERE / "sheets" / "pirate-torch-sheet.png").convert("RGBA").crop((1200, 220, 1380, 720)).save(
        HERE / "parts" / "torch-burner-concept.png")
    Image.open(HERE / "sheets" / "path-censer-sheet.png").convert("RGBA").crop((1130, 150, 1360, 1000)).save(
        HERE / "parts" / "censer-lance-concept.png")
    print("wrote parts/torch-burner-concept.png, parts/censer-lance-concept.png")


def fitted(source: Path, size: tuple[int, int]) -> Image.Image:
    """The part's visible content fitted to the canvas's height, centred across it, its rear on the bottom edge."""
    image = Image.open(source).convert("RGBA")
    # Clear the faint fringe a generated ground leaves, then crop to what is left.
    alpha = image.getchannel("A").point(lambda a: 0 if a < 24 else a)
    image.putalpha(alpha)
    image = image.crop(alpha.getbbox())
    width, height = size
    scale = height / image.height
    if image.width * scale > width:
        scale = width / image.width
    image = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))),
                         Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", size)
    canvas.alpha_composite(image, (round((width - image.width) / 2), height - image.height))
    return canvas


def pack() -> None:
    for name, (source, size) in PARTS.items():
        fitted(HERE / source, size).save(OUT / name)
    print("packed", ", ".join(PARTS), "into", OUT)


if __name__ == "__main__":
    {"concepts": concepts, "pack": pack}[sys.argv[1] if len(sys.argv) > 1 else "pack"]()
