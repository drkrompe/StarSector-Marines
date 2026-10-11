"""Full-cell sandbag damage cuts and exact rotations through the ordinary doodad packer."""
import json
from pathlib import Path
from PIL import Image
from stitch_atlas import BuildOptions, build_atlas

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

def main():
    layout = json.loads((HERE / "sandbag-damage-geometry.json").read_text(encoding="utf-8"))
    raw = Image.open(HERE / layout["source"]).convert("RGBA")
    if list(raw.size) != layout["sourceSize"]:
        raise ValueError("Sandbag damage source dimensions differ from reviewed cuts")
    sources = HERE / "sandbag-damage-sources"
    sources.mkdir(exist_ok=True)
    assets = {}
    for state in ("damaged", "destroyed"):
        for shape, facings in (("straight", ("n", "e", "s", "w")), ("corner", ("nw", "ne", "se", "sw"))):
            x, y, width, height = layout[state + "-" + shape]
            if min(x, y) < 0 or min(width, height) <= 0 or x + width > raw.width or y + height > raw.height:
                raise ValueError("Invalid full-cell cut")
            cell = raw.crop((x, y, x + width, y + height)).resize((384, 384), Image.Resampling.LANCZOS)
            for facing in facings:
                name = f"sandbag-{state}-{shape}-{facing}"
                cell.save(sources / (name + ".png"), optimize=True)
                assets[name + ".png"] = {"id": "doodad." + name, "order": len(assets),
                    "cover": "heavy" if state == "damaged" else "none",
                    "ballisticHalfHeight": 0.3 if state == "damaged" else 0,
                    "preserveCanvas": True, "padding": 0, "scale": 1}
                cell = cell.transpose(Image.Transpose.ROTATE_270)
    metadata = sources / "_atlas.json"
    metadata.write_text(json.dumps({"assets": assets}, indent=2) + "\n", encoding="utf-8")
    build_atlas(BuildOptions(input_dir=sources, metadata_path=metadata, columns=8,
        output_image=ROOT / "mod/graphics/doodads/sandbag-damage.png",
        output_manifest=ROOT / "mod/data/tilesets/sandbag-damage.tileset.json",
        sheet_path="graphics/doodads/sandbag-damage.png"))

if __name__ == "__main__":
    main()
