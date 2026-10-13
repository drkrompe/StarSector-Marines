"""Retain a measured cap-cut guide; this script supplies no finished artwork."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent


def make_guide():
    geometry = json.loads((HERE / "geometry.json").read_text())
    source = Image.open(HERE / geometry["source"]).convert("RGBA")
    if source.size != tuple(geometry["source_canvas"]):
        raise ValueError("Accepted v1 source dimensions changed")
    bounds = tuple(geometry["source_body_bounds"])
    crop = source.crop(bounds)
    guide = Image.new("RGBA", (1920, 1080), (22, 27, 33, 255))
    draw = ImageDraw.Draw(guide)
    origin = (220, 300)
    guide.alpha_composite(crop, origin)
    for name, part in geometry["shoulders"].items():
        polygon = [(x - bounds[0] + origin[0], y - bounds[1] + origin[1])
                   for x, y in part["cut_polygon"]]
        color = (255, 172, 32, 255) if name == "left-shoulder" else (100, 226, 240, 255)
        draw.line(polygon + [polygon[0]], fill=color, width=3)
        x, y = part["hinge_source"]
        x += origin[0] - bounds[0]
        y += origin[1] - bounds[1]
        draw.line((x - 12, y, x + 12, y), fill=color, width=3)
        draw.line((x, y - 12, x, y + 12), fill=color, width=3)
        mask = Image.new("L", source.size)
        ImageDraw.Draw(mask).polygon(part["cut_polygon"], fill=255)
        mask.save(HERE / f"{name}-cut-mask.png")
    draw.text((220, 260), "Specter v2: authored cap cuts and inner hinge pivots", fill="white")
    draw.text((220, 740), "Guide only. Original source supplies cap pixels. Dark undersuit backing supplies hidden overlap.", fill="white")
    draw.text((220, 765), "All exported body layers: full 150 x 95 canvas. Original helmet and boot remain unchanged.", fill="white")
    guide.save(HERE / "cut-guide.png")
    # Raw original body reference at exact registration for a focused ImageGen edit.
    crop.save(HERE / "body-reference.png")
    return geometry


if __name__ == "__main__":
    make_guide()
