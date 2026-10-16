"""Render the Boarding Jack geometry guide. Supplies no finished artwork.

The guide is the edit target for a built-in ImageGen reskin (see README.md). Shapes are
flat region-coded fills on each part's real export canvas, enlarged for legibility, with
the inner-hinge pivots and the material legend drawn on. Writes
art-source/templates/boarding-jack-guide.png and one flat per-part guide beside this file.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
TEMPLATES = HERE.parent.parent / "templates"
SCALE = 4
PARTS = ["torso", "left-shoulder", "right-shoulder", "head", "foot"]


def poly(points, size):
    w, h = size
    return [(x * w, y * h) for x, y in points]


def ellipse(center, radii, size):
    w, h = size
    cx, cy = center[0] * w, center[1] * h
    rx, ry = radii[0] * w, radii[1] * h
    return (cx - rx, cy - ry, cx + rx, cy + ry)


def canvas(geometry, name):
    if name in ("torso", "left-shoulder", "right-shoulder"):
        return tuple(geometry["export_canvas"])
    return tuple(geometry["head_canvas" if name == "head" else "foot_canvas"])


def part_image(geometry, name):
    """One part on its true export canvas: transparent background, flat material fills."""
    mats = {k: tuple(v["guide_rgb"]) + (255,) for k, v in geometry["materials"].items()}
    spec = geometry["layers"][name]
    size = canvas(geometry, name)
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if name == "torso":
        d.polygon(poly(spec["slab"], size), fill=mats["joint_backing"])
        inner = [(x + (0.5 - x) * 0.12, y + (0.5 - y) * 0.08) for x, y in spec["slab"]]
        d.polygon(poly(inner, size), fill=mats["casing"])
        d.polygon(poly(spec["power_pack"], size), fill=mats["bare_steel"])
        socket = spec["helmet_socket"]
        d.ellipse(ellipse(socket["center"], socket["radii"], size), fill=(0, 0, 0, 0))
        for x in (0.30, 0.70):
            d.line(poly([(0.5, 0.78), (x, 0.36)], size), fill=mats["hoses"], width=2)
        d.rectangle(poly([(0.56, 0.60), (0.64, 0.66)], size), fill=mats["service_labels"])
    elif name in ("left-shoulder", "right-shoulder"):
        d.polygon(poly(spec["slab"], size), fill=mats["casing"])
        cx = sum(p[0] for p in spec["slab"]) / len(spec["slab"])
        cy = sum(p[1] for p in spec["slab"]) / len(spec["slab"])
        d.line(poly([(cx - 0.08, cy), (cx + 0.08, cy)], size), fill=mats["bare_steel"], width=1)
        if name == "left-shoulder":
            d.rectangle(poly([(0.08, 0.50), (0.16, 0.58)], size), fill=mats["service_labels"])
    elif name == "head":
        d.ellipse(ellipse(spec["dome"]["center"], spec["dome"]["radii"], size), fill=mats["casing"])
        d.polygon(poly(spec["brow"], size), fill=mats["bare_steel"])
        d.ellipse(ellipse((0.5, 0.22), (0.03, 0.03), size), fill=mats["indicator"])
        for x in (0.36, 0.64):
            d.ellipse(ellipse((x, 0.86), (0.06, 0.05), size), fill=mats["hoses"])
    else:
        d.polygon(poly(spec["sole"], size), fill=mats["joint_backing"])
        d.polygon(poly([(0.20, 0.08), (0.80, 0.08), (0.84, 0.36), (0.16, 0.36)], size),
                  fill=mats["casing"])
    return img


def enlarge(img, scale=SCALE):
    return img.resize((img.size[0] * scale, img.size[1] * scale), Image.NEAREST)


def make_guide():
    geometry = json.loads((HERE / "geometry.json").read_text())
    images = {name: part_image(geometry, name) for name in PARTS}
    for name, img in images.items():
        img.save(HERE / f"guide-{name}.png")

    guide = Image.new("RGBA", (1920, 1080), (22, 27, 33, 255))
    draw = ImageDraw.Draw(guide)
    white = (255, 255, 255, 255)
    grey = (90, 90, 90, 255)

    # Assembled idle: shoulders share the torso canvas, so they composite in place.
    origin = (120, 140)
    w, h = geometry["export_canvas"]
    for name in ("torso", "left-shoulder", "right-shoulder"):
        guide.alpha_composite(enlarge(images[name]), origin)
    # The helmet is mastered separately; only its socket is outlined here.
    socket = geometry["layers"]["torso"]["helmet_socket"]
    sx, sy = socket["center"]
    rx, ry = socket["radii"]
    draw.ellipse((origin[0] + (sx - rx) * w * SCALE, origin[1] + (sy - ry) * h * SCALE,
                  origin[0] + (sx + rx) * w * SCALE, origin[1] + (sy + ry) * h * SCALE),
                 outline=(255, 255, 255, 255), width=2)
    for name, color in (("left-shoulder", (255, 172, 32, 255)), ("right-shoulder", (100, 226, 240, 255))):
        px, py = geometry["layers"][name]["pivot"]
        x, y = origin[0] + px * w * SCALE, origin[1] + py * h * SCALE
        draw.line((x - 14, y, x + 14, y), fill=color, width=3)
        draw.line((x, y - 14, x, y + 14), fill=color, width=3)
    draw.rectangle((origin[0], origin[1], origin[0] + w * SCALE, origin[1] + h * SCALE), outline=grey)
    draw.text((origin[0], origin[1] - 30),
              "Boarding Jack: assembled idle on the 150x95 shell canvas (x4); white ring is the helmet socket, crosses the Specter v2 hinge pivots.",
              fill=white)

    # Separated parts, each on its own complete canvas.
    slots = {"left-shoulder": (820, 140), "right-shoulder": (1320, 140),
             "torso": (820, 560), "head": (1320, 560), "foot": (1600, 560)}
    for name, (x, y) in slots.items():
        big = enlarge(images[name], 3)
        guide.alpha_composite(big, (x, y))
        draw.rectangle((x, y, x + big.size[0], y + big.size[1]), outline=grey)
        draw.text((x, y - 20), f"{name}: {images[name].size[0]}x{images[name].size[1]} (x3)", fill=white)

    ly = 600
    for key, mat in geometry["materials"].items():
        draw.rectangle((120, ly, 150, ly + 20), fill=tuple(mat["guide_rgb"]) + (255,))
        draw.text((160, ly + 4), key + (" (paintable)" if mat["paintable"] else ""), fill=white)
        ly += 32
    draw.text((120, 1000), "GUIDE ONLY. ImageGen reskins these silhouettes; it may not move cuts, pivots, "
              "the helmet socket or the canvas.", fill=white)
    draw.text((120, 1025), "No weapon art: handheld weapons are the shared infantry layers. "
              "No extra guns, spikes or comedic junk.", fill=white)
    TEMPLATES.mkdir(parents=True, exist_ok=True)
    guide.save(TEMPLATES / "boarding-jack-guide.png")


if __name__ == "__main__":
    make_guide()
