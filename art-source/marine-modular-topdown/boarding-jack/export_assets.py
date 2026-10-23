"""Mechanical export of the Boarding Jack layered rig: retained ImageGen raws -> mod/graphics/.

Nothing here repaints. Each raw is resampled onto its code-owned edit guide (written by
make_guide.py), keeps its own alpha bounded by a slightly grown guide silhouette so a drifted
edge cannot add material the geometry never had, is downsampled through the guide's recorded
window and pasted onto the part's complete export canvas. Registration is therefore the guide's
arithmetic, not a measurement of the generated picture.

The casing mask is the paintable region: warm painted plate inside the bounded silhouette.
Bare steel, joint backing, hoses, service labels and lamps are never painted. The default export
is the raw rust-orange paint; the other palettes run the Specter v1 luminance ramp through the
mask, so every paint shares one silhouette and alpha.

  armor/boarding-jack/{body,shoulder-left,shoulder-right,head,foot}.png    rust-orange (default)
  armor/boarding-jack/paints/pirate/...                                     soot weathered with crude red (own raws)
  art-source/.../unshipped-paints/boarding-jack/<palette>/...               hull-grey, oxide-green, soot

Only pirate is drawn in play, so the ramp palettes are written outside mod/ rather than shipped.
"""
import hashlib
import json
import runpy
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parents[2]
TEMPLATES = REPOSITORY / "art-source/templates"
VARIANTS = REPOSITORY / "mod/graphics/battle/marine-modular-topdown/variants"
OUTPUT = VARIANTS / "armor/boarding-jack"
UNSHIPPED = HERE.parent / "unshipped-paints/boarding-jack"
EXPORT_NAMES = {"torso": "body.png", "left-shoulder": "shoulder-left.png",
                "right-shoulder": "shoulder-right.png", "head": "head.png", "foot": "foot.png"}
PALETTES = {
    "hull-grey": ("#2a2f33", "#6c757b", "#b8bfc3"),
    "oxide-green": ("#222a21", "#5a6a48", "#a8b08a"),
    "soot": ("#19191a", "#3e3c3a", "#7a756e"),
}
GROW = 0.012       # guide silhouette growth allowed for drift, as a fraction of the guide's long side


def fingerprint(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def odd(n):
    n = max(3, int(round(n)))
    return n if n % 2 else n + 1


def warm_paint(raw):
    """Soft weight for painted orange plate; steel, rubber, purple hoses and cream labels score zero."""
    weights = []
    for r, g, b, a in raw.get_flattened_data():
        if a == 0:
            weights.append(0)
            continue
        warm = min(1.0, max(0.0, (r - b - 45) / 50))
        not_label = min(1.0, max(0.0, (r - g - 22) / 30))
        weights.append(round(255 * warm * not_label))
    mask = Image.new("L", raw.size)
    mask.putdata(weights)
    return mask


def downsample(image, size):
    return image.convert("RGBa").resize(size, Image.Resampling.LANCZOS).convert("RGBA")


def registered(name, raw_path):
    """A raw resampled onto its part's edit target, fitted and bounded; returns (image, fit, guide)."""
    guide = Image.open(TEMPLATES / f"boarding-jack-{name}-guide.png").convert("RGBA")
    raw = Image.open(raw_path).convert("RGBA")
    ratio = (raw.width / raw.height) / (guide.width / guide.height)
    if abs(ratio - 1) > 0.03:
        raise ValueError(f"{name}: raw aspect {raw.size} departs from its guide {guide.size}")
    raw = raw.resize(guide.size, Image.Resampling.LANCZOS)
    # ImageGen kept each silhouette but not its exact scale on the canvas (the torso came back
    # filling more of the frame than the guide). Fit the raw's opaque extent onto the guide's:
    # one per-axis scale and offset, measured, recorded, applied to every pixel alike.
    raw_box = raw.getchannel("A").point(lambda a: 255 if a >= 128 else 0).getbbox()
    guide_box = guide.getchannel("A").point(lambda a: 255 if a >= 128 else 0).getbbox()
    fitted = Image.new("RGBA", guide.size, (0, 0, 0, 0))
    fitted.paste(raw.crop(raw_box).convert("RGBa").resize(
        (guide_box[2] - guide_box[0], guide_box[3] - guide_box[1]), Image.Resampling.LANCZOS).convert("RGBA"),
        guide_box[:2])
    fit = {"raw_box_on_guide": list(raw_box), "guide_box": list(guide_box),
           "scale": [round((guide_box[2] - guide_box[0]) / (raw_box[2] - raw_box[0]), 4),
                     round((guide_box[3] - guide_box[1]) / (raw_box[3] - raw_box[1]), 4)]}
    if not all(0.8 <= s <= 1.25 for s in fit["scale"]):
        raise ValueError(f"{name}: raw silhouette drifted beyond a mechanical fit: {fit}")
    grow = odd(GROW * max(guide.size))
    bound = guide.getchannel("A").point(lambda a: 255 if a else 0).filter(ImageFilter.MaxFilter(grow))
    fitted.putalpha(ImageChops.multiply(fitted.getchannel("A"), bound))
    return fitted, fit, guide


def to_canvas(image, canvas_size, window, mode=None):
    size = (window[2] - window[0], window[3] - window[1])
    if mode == "L":
        out = Image.new("L", canvas_size)
        out.paste(image.resize(size, Image.Resampling.BOX), (window[0], window[1]))
        return out
    out = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    out.alpha_composite(downsample(image, size), (window[0], window[1]))
    return out


def export_part(geometry, windows, name):
    canvas_size, window, _ = windows[name]
    raw_path = HERE / "raw" / f"{name}.raw.png"
    raw, fit, guide = registered(name, raw_path)
    # Paint is classified by colour inside the bounded silhouette. The guide's own casing regions are
    # too strict a fence: ImageGen narrowed the backing rims and widened the plates over them.
    casing = warm_paint(raw)
    casing = ImageChops.multiply(casing, raw.getchannel("A").point(lambda a: 255 if a >= 128 else 0))
    part = to_canvas(raw, canvas_size, window)
    mask = ImageChops.multiply(to_canvas(casing, canvas_size, window, "L"),
                               part.getchannel("A").point(lambda a: 255 if a else 0))
    return part, mask, {"raw_sha256": fingerprint(raw_path), "raw_size": list(Image.open(raw_path).size),
                        "guide_size": list(guide.size), "window": list(window), "canvas": list(canvas_size), "fit": fit,
                        "casing_pixels": sum(v >= 128 for v in mask.get_flattened_data())}, raw, casing


# ---------------------------------------------------------------------------------------------
# Pirate paint: Greyhook's own rigs. A luminance ramp can recolour plate but cannot scrape it,
# scorch it or daub a stripe on it, so this paint has hand-made detail of its own. The soot paint
# is applied to each part at edit-target resolution and written to pirate/<part>-base.png; ImageGen
# weathers that base and adds crude red crew markings (generate.sh pirate-<part>); the result is
# registered like any raw and then held to the default export's alpha, so the pirate paint shares
# the one silhouette every other paint has.
# ---------------------------------------------------------------------------------------------
PIRATE = HERE / "pirate"


def write_pirate_bases():
    guide_module = runpy.run_path(str(HERE / "make_guide.py"))
    recolor = runpy.run_path(str(HERE.parent / "specter-v1" / "export_assets.py"))["recolor"]
    geometry = json.loads((HERE / "geometry.json").read_text())
    windows = guide_module["edit_windows"](geometry)
    PIRATE.mkdir(exist_ok=True)
    for name in EXPORT_NAMES:
        _, _, _, raw, casing = export_part(geometry, windows, name)
        recolor(raw, casing, PALETTES["soot"]).save(PIRATE / f"{name}-base.png")


def pirate_part(geometry, windows, name, default, soot):
    canvas_size, window, _ = windows[name]
    raw_path = HERE / "raw" / f"pirate-{name}.raw.png"
    raw, fit, _ = registered(name, raw_path)
    painted = to_canvas(raw, canvas_size, window)
    # Where the weathered pass left a hole or a fringe inside the shared silhouette, soot shows.
    part = Image.alpha_composite(soot.copy(), painted)
    part.putalpha(default.getchannel("A"))
    return part, {"raw_sha256": fingerprint(raw_path), "raw_size": list(Image.open(raw_path).size), "fit": fit}


def export():
    guide_module = runpy.run_path(str(HERE / "make_guide.py"))
    recolor = runpy.run_path(str(HERE.parent / "specter-v1" / "export_assets.py"))["recolor"]
    geometry = json.loads((HERE / "geometry.json").read_text())
    windows = guide_module["edit_windows"](geometry)
    outputs, report = {}, {"palettes": {"rust-orange": "raw ImageGen paint", **PALETTES}, "parts": {}}
    (HERE / "masks").mkdir(exist_ok=True)
    for name, export_name in EXPORT_NAMES.items():
        part, mask, record, _, _ = export_part(geometry, windows, name)
        if record["casing_pixels"] == 0:
            raise ValueError(f"{name}: casing mask is empty")
        report["parts"][name] = record
        outputs[OUTPUT / export_name] = part
        mask.save(HERE / "masks" / f"{name}-casing.png")
        for palette, ramp in PALETTES.items():
            outputs[UNSHIPPED / palette / export_name] = recolor(part, mask, ramp)
        if (HERE / "raw" / f"pirate-{name}.raw.png").exists():
            pirate, pirate_record = pirate_part(geometry, windows, name, part,
                                                outputs[UNSHIPPED / "soot" / export_name])
            outputs[OUTPUT / "paints" / "pirate" / export_name] = pirate
            record["pirate"] = pirate_record
    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path, optimize=True)
    (HERE / "export-measurements.json").write_text(json.dumps(report, indent=2) + "\n")
    return report


if __name__ == "__main__":
    import sys
    if "--pirate-bases" in sys.argv:
        write_pirate_bases()
    else:
        print(json.dumps(export(), indent=2))
