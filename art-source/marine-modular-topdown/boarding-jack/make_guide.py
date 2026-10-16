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


# ---------------------------------------------------------------------------------------------
# Edit targets. A 150x95 flat guide is far too small to hand to ImageGen, so each part is also
# drawn as vector shapes at a large, exact multiple of its export canvas, cropped to a recorded
# window of that canvas. The exporter maps a raw back through the same window, so registration
# is arithmetic rather than a measurement of the generated picture.
# ---------------------------------------------------------------------------------------------

LATCH_CANVAS = (136, 120)   # 0.34 x 0.30 shoulder widths at the emitter's 400 px per shoulder width


def edit_windows(geometry):
    """Part -> (export canvas, crop window in export pixels, scale). Windows are fixed numbers."""
    body = tuple(geometry["export_canvas"])
    return {
        "torso": (body, (0, 0, 150, 95), 8),
        "left-shoulder": (body, (0, 12, 56, 72), 16),
        "right-shoulder": (body, (94, 18, 145, 66), 16),
        "head": (tuple(geometry["head_canvas"]), (0, 0, 72, 69), 14),
        "foot": (tuple(geometry["foot_canvas"]), (0, 0, 38, 52), 20),
        "latch-screen": (LATCH_CANVAS, (0, 0, 136, 120), 8),
    }


def _rgb(geometry, key, shade=0):
    r, g, b = geometry["materials"][key]["guide_rgb"]
    return (max(0, min(255, r + shade)), max(0, min(255, g + shade)), max(0, min(255, b + shade)), 255)


def edit_guide(geometry, name):
    canvas_size, window, scale = edit_windows(geometry)[name]
    x0, y0, x1, y1 = window
    size = ((x1 - x0) * scale, (y1 - y0) * scale)
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cw, ch = canvas_size

    def P(points):
        return [((x * cw - x0) * scale, (y * ch - y0) * scale) for x, y in points]

    def E(center, radii):
        cx, cy = (center[0] * cw - x0) * scale, (center[1] * ch - y0) * scale
        rx, ry = radii[0] * cw * scale, radii[1] * ch * scale
        return (cx - rx, cy - ry, cx + rx, cy + ry)

    def line(points, key, width, shade=0):
        d.line(P(points), fill=_rgb(geometry, key, shade), width=width, joint="curve")

    def bolts(points, r=0.006):
        for p in points:
            d.ellipse(E(p, (r, r * cw / ch)), fill=_rgb(geometry, "bare_steel", -30))

    casing, dark_casing, light_casing = (_rgb(geometry, "casing"), _rgb(geometry, "casing", -38),
                                         _rgb(geometry, "casing", 22))
    spec = geometry["layers"].get(name, {})
    if name == "torso":
        d.polygon(P(spec["slab"]), fill=_rgb(geometry, "joint_backing"))
        # Two mismatched chest plates welded down the centre seam, a collar plate, a belly plate.
        d.polygon(P([(0.345, 0.25), (0.50, 0.24), (0.50, 0.66), (0.36, 0.70), (0.34, 0.45)]), fill=casing)
        d.polygon(P([(0.50, 0.23), (0.655, 0.27), (0.665, 0.52), (0.64, 0.68), (0.50, 0.66)]), fill=light_casing)
        d.polygon(P([(0.37, 0.70), (0.63, 0.69), (0.62, 0.79), (0.38, 0.80)]), fill=dark_casing)
        line([(0.50, 0.23), (0.50, 0.67)], "casing", 5, -70)                      # weld bead
        line([(0.36, 0.70), (0.64, 0.685)], "casing", 4, -70)
        bolts([(0.36, 0.30), (0.36, 0.62), (0.64, 0.32), (0.635, 0.60), (0.40, 0.76), (0.60, 0.755)])
        d.polygon(P(spec["power_pack"]), fill=_rgb(geometry, "bare_steel"))
        for y in (0.76, 0.81, 0.86):                                               # pack fins
            line([(0.41, y), (0.59, y)], "bare_steel", 4, -45)
        socket = spec["helmet_socket"]
        d.ellipse(E(socket["center"], (socket["radii"][0] + 0.012, socket["radii"][1] + 0.02)),
                  fill=_rgb(geometry, "bare_steel", -20))                          # neck ring
        d.ellipse(E(socket["center"], socket["radii"]), fill=(0, 0, 0, 0))
        for side in (-1, 1):                                                       # guarded hoses, pack to shoulders
            line([(0.5 + side * 0.07, 0.72), (0.5 + side * 0.15, 0.55), (0.5 + side * 0.16, 0.34)], "hoses", 14)
            line([(0.5 + side * 0.07, 0.72), (0.5 + side * 0.15, 0.55), (0.5 + side * 0.16, 0.34)], "hoses", 4, 40)
        d.rectangle(P([(0.555, 0.58), (0.635, 0.645)]), fill=_rgb(geometry, "service_labels"))
        d.ellipse(E((0.43, 0.27), (0.006, 0.0095)), fill=_rgb(geometry, "indicator"))
    elif name in ("left-shoulder", "right-shoulder"):
        slab = spec["slab"]
        d.polygon(P(slab), fill=_rgb(geometry, "joint_backing"))
        cx = sum(p[0] for p in slab) / len(slab)
        cy = sum(p[1] for p in slab) / len(slab)
        inner = [(x + (cx - x) * 0.10, y + (cy - y) * 0.10) for x, y in slab]
        if name == "left-shoulder":
            d.polygon(P(inner), fill=casing)
            d.polygon(P([(0.07, 0.25), (0.29, 0.22), (0.30, 0.40), (0.07, 0.42)]), fill=light_casing)  # welded doubler
            line([(0.07, 0.42), (0.30, 0.40)], "casing", 5, -70)
            line([(0.20, 0.235), (0.20, 0.41)], "casing", 4, -70)
            bolts([(0.09, 0.28), (0.27, 0.26), (0.09, 0.60), (0.27, 0.62), (0.30, 0.45)])
            d.rectangle(P([(0.08, 0.50), (0.17, 0.58)]), fill=_rgb(geometry, "service_labels"))
            line([(0.31, 0.24), (0.32, 0.64)], "bare_steel", 10)                    # hinge strap
        else:
            d.polygon(P(inner), fill=dark_casing)
            for y in (0.33, 0.42, 0.51):                                            # cargo-frame rails
                line([(0.70, y), (0.90, y)], "bare_steel", 9)
            line([(0.685, 0.28), (0.69, 0.58)], "bare_steel", 10)                   # hinge strap
            bolts([(0.72, 0.29), (0.86, 0.29), (0.72, 0.57), (0.87, 0.56)])
            d.ellipse(E((0.88, 0.47), (0.006, 0.0095)), fill=_rgb(geometry, "indicator"))
    elif name == "head":
        d.ellipse(E((0.5, 0.53), (0.40, 0.43)), fill=_rgb(geometry, "joint_backing"))   # seal collar
        d.ellipse(E(spec["dome"]["center"], spec["dome"]["radii"]), fill=casing)
        d.ellipse(E((0.47, 0.50), (0.20, 0.22)), fill=light_casing)                      # crown patch
        line([(0.16, 0.56), (0.84, 0.56)], "casing", 5, -70)                             # weld ring
        d.polygon(P(spec["brow"]), fill=_rgb(geometry, "bare_steel"))
        d.polygon(P([(0.32, 0.20), (0.68, 0.20), (0.66, 0.26), (0.34, 0.26)]), fill=_rgb(geometry, "joint_backing"))  # view slot
        d.ellipse(E((0.70, 0.36), (0.03, 0.03)), fill=_rgb(geometry, "indicator"))
        for x in (0.36, 0.64):
            d.ellipse(E((x, 0.86), (0.075, 0.065)), fill=_rgb(geometry, "bare_steel", -20))
            d.ellipse(E((x, 0.86), (0.045, 0.04)), fill=_rgb(geometry, "hoses"))
    elif name == "foot":
        d.polygon(P(spec["sole"]), fill=_rgb(geometry, "joint_backing"))
        d.polygon(P([(0.20, 0.08), (0.80, 0.08), (0.85, 0.36), (0.15, 0.36)]), fill=casing)   # toe plate
        d.polygon(P([(0.22, 0.42), (0.78, 0.42), (0.80, 0.62), (0.20, 0.62)]), fill=dark_casing)  # instep plate
        d.rectangle(P([(0.30, 0.74), (0.70, 0.90)]), fill=_rgb(geometry, "bare_steel"))         # heel latch
        bolts([(0.26, 0.14), (0.74, 0.14), (0.26, 0.30), (0.74, 0.30)], r=0.03)
    else:  # latch-screen: actuator plate on the shoulder, narrow salvaged riot screen forward of it
        steel = _rgb(geometry, "bare_steel")
        d.polygon(P([(0.34, 0.50), (0.66, 0.50), (0.70, 0.92), (0.30, 0.92)]), fill=steel)          # actuator plate
        d.rectangle(P([(0.40, 0.58), (0.60, 0.70)]), fill=_rgb(geometry, "joint_backing"))          # ram housing
        line([(0.44, 0.36), (0.44, 0.60)], "bare_steel", 22, -40)                                   # twin actuator rods
        line([(0.56, 0.36), (0.56, 0.60)], "bare_steel", 22, -40)
        line([(0.36, 0.86), (0.36, 0.55), (0.30, 0.40)], "hoses", 14)                               # guarded hose
        d.polygon(P([(0.06, 0.24), (0.30, 0.13), (0.70, 0.13), (0.94, 0.24), (0.92, 0.36),
                     (0.70, 0.27), (0.30, 0.27), (0.08, 0.36)]), fill=_rgb(geometry, "joint_backing"))  # screen frame
        d.polygon(P([(0.10, 0.25), (0.31, 0.16), (0.69, 0.16), (0.90, 0.25), (0.89, 0.31),
                     (0.69, 0.24), (0.31, 0.24), (0.11, 0.31)]), fill=_rgb(geometry, "bare_steel", 25))  # screen face
        d.rectangle(P([(0.44, 0.18), (0.56, 0.22)]), fill=_rgb(geometry, "service_labels"))
        bolts([(0.38, 0.55), (0.62, 0.55), (0.36, 0.86), (0.64, 0.86)], r=0.012)
        d.ellipse(E((0.63, 0.78), (0.016, 0.018)), fill=_rgb(geometry, "indicator"))
    return img


def make_edit_guides():
    geometry = json.loads((HERE / "geometry.json").read_text())
    for name in edit_windows(geometry):
        edit_guide(geometry, name).save(TEMPLATES / f"boarding-jack-{name}-guide.png")


if __name__ == "__main__":
    make_guide()
    make_edit_guides()
