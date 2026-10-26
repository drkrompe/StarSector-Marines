"""Build the listening-post sheet: the radar dish and fluid tank masters at an installation's scale.

The masters in sources/ stay the single copy of the art; they are staged into a scratch folder with this sheet's own
metadata and packed by stitch_atlas.py at 128 px a cell. Run from the repository root:

    python art-source/doodads/build_listening_post.py
"""

import shutil
import tempfile
from pathlib import Path

from stitch_atlas import BuildOptions, build_atlas

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
METADATA = HERE / "listening-post" / "_atlas.json"
MASTERS = ("military-radar-dish.png", "industrial-fluid-tank.png")


def main() -> None:
    with tempfile.TemporaryDirectory() as scratch:
        staged = Path(scratch)
        for name in MASTERS:
            shutil.copy(HERE / "sources" / name, staged / name)
        image, manifest, count = build_atlas(BuildOptions(
            input_dir=staged,
            output_image=ROOT / "mod" / "graphics" / "doodads" / "listening-post.png",
            output_manifest=ROOT / "mod" / "data" / "tilesets" / "listening-post.tileset.json",
            sheet_path="graphics/doodads/listening-post.png",
            metadata_path=METADATA,
            columns=6,
            cell_size=128,
            padding=4,
            id_prefix="doodad.",
            resample="lanczos",
            alpha_threshold=1,
            allow_opaque=False,
            only_prefix=None,
        ))
    print(f"Packed {count} assets")
    print(image)
    print(manifest)


if __name__ == "__main__":
    main()
