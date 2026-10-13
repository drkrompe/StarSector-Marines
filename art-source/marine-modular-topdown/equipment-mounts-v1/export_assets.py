"""Measure retained ImageGen sources and normalize transparent accessory sprites.

No painted pixels, alpha keying, recoloring, or inferred camera corrections.
The optional explicit crop is a measured registration, never a guide guess.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
DEFAULT_OUTPUT = HERE.parents[2] / "mod/graphics/battle/marine-modular-topdown/variants/equipment"

def export(output):
    geometries = json.loads((HERE / "geometry.json").read_text())
    registrations = json.loads((HERE / "source-regions.json").read_text()) if (HERE / "source-regions.json").exists() else {}
    report, pending = {}, {}
    for name, geometry in geometries.items():
        path = HERE / f"{name}-source.png"
        raw = Image.open(path)
        if "A" not in raw.getbands():
            raise ValueError(f"{name}: ImageGen source lacks alpha; regenerate with true transparency")
        source = raw.convert("RGBA")
        alpha = source.getchannel("A")
        if alpha.getextrema()[0] != 0 or alpha.getextrema()[1] < 128:
            raise ValueError(f"{name}: source has no transparent exterior or substantial opaque subject")
        region = tuple(registrations.get(name, {}).get("crop", (0,0,source.width,source.height)))
        if not (0 <= region[0] < region[2] <= source.width and 0 <= region[1] < region[3] <= source.height):
            raise ValueError(f"{name}: measured crop exceeds raw source")
        registered = source.crop(region)
        bounds = registered.getchannel("A").getbbox()
        if bounds is None:
            raise ValueError(f"{name}: empty measured crop")
        piece = registered.crop(bounds)
        target = geometry["content_size"]
        ratio_error = abs(piece.width / piece.height / (target[0]/target[1])-1)
        if ratio_error > .25:
            raise ValueError(f"{name}: source aspect differs from guide by {ratio_error:.1%}; inspect/revise source")
        scale = min(target[0]/piece.width, target[1]/piece.height)
        size = (max(1, round(piece.width*scale)), max(1, round(piece.height*scale)))
        normalized = piece.resize(size, Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", geometry["export_canvas"])
        origin = ((canvas.width-size[0])//2, (canvas.height-size[1])//2)
        canvas.alpha_composite(normalized, origin)
        pending[output / f"{name}.png"] = canvas
        report[name] = {"source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                        "source_registration": registrations.get(name, {}),
                        "source_dimensions": list(source.size), "measured_region": list(region),
                        "source_content_bounds": [bounds[0]+region[0],bounds[1]+region[1],bounds[2]+region[0],bounds[3]+region[1]],
                        "source_content_size": list(piece.size), "source_aspect_error": ratio_error,
                        "export_canvas": list(canvas.size), "export_content_size": list(size),
                        "export_content_origin": list(origin), "alpha_bounds": list(canvas.getchannel("A").getbbox()),
                        "target_content_size_sw": geometry["runtime_content_size_sw"],
                        "actual_content_size_sw": [geometry["runtime_content_size_sw"][0]*size[0]/target[0],
                                                   geometry["runtime_content_size_sw"][1]*size[1]/target[1]],
                        "export_canvas_size_sw": [geometry["runtime_content_size_sw"][0]*canvas.width/target[0],
                                                  geometry["runtime_content_size_sw"][1]*canvas.height/target[1]],
                        "orientation": "north-forward; orthographic overhead", "alpha_processing": "retained generated alpha; resampling only"}
    for path, image in pending.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
    (HERE / "export-measurements.json").write_text(json.dumps(report, indent=2)+"\n")
    return report

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    print(json.dumps(export(parser.parse_args().output), indent=2))
