"""Retained full-cell cuts, quarter-turns and ordinary atlas export for fence damage."""
import json
from pathlib import Path
from PIL import Image
from stitch_atlas import BuildOptions, build_atlas

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

def main():
    layout = json.loads((HERE / "industrial-fence-damage-geometry.json").read_text(encoding="utf-8"))
    raw = Image.open(HERE / layout["source"]).convert("RGBA")
    if list(raw.size) != layout["sourceSize"]:
        raise ValueError("Fence damage source dimensions differ from reviewed cuts")
    sources = HERE / "fence-damage-sources"
    sources.mkdir(exist_ok=True)
    assets = {}
    for state in ("damaged", "destroyed"):
        for shape, facings in (("straight", ("h", "v")), ("corner", ("nw", "ne", "se", "sw"))):
            x, y, width, height = layout[state + "-" + shape]
            # Retain the entire authored cell, including its empty breach; never alpha-trim.
            cell = raw.crop((x, y, x + width, y + height)).resize((384, 384), Image.Resampling.LANCZOS)
            for facing in facings:
                name = f"industrial-fence-{state}-{shape}-{facing}"
                cell.save(sources / (name + ".png"), optimize=True)
                assets[name + ".png"] = {"id": "doodad." + name, "order": len(assets),
                    "cover": "light" if state == "damaged" else "none",
                    "ballisticHalfHeight": 0.72 if state == "damaged" else 0,
                    "preserveCanvas": True, "padding": 0, "scale": 1}
                cell = cell.transpose(Image.Transpose.ROTATE_270)
    metadata = sources / "_atlas.json"
    metadata.write_text(json.dumps({"assets": assets}, indent=2) + "\n", encoding="utf-8")
    build_atlas(BuildOptions(input_dir=sources, metadata_path=metadata, columns=6,
        output_image=ROOT / "mod/graphics/doodads/fence-damage.png",
        output_manifest=ROOT / "mod/data/tilesets/fence-damage.tileset.json",
        sheet_path="graphics/doodads/fence-damage.png"))

if __name__ == "__main__":
    main()
