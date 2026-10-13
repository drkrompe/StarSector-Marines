"""Split accepted v1 steel caps and add retained ImageGen undersuit backing.

All color ramps operate on each whole original v1 part before the split.
Generated pixels supply only the hidden dark shoulder backing. No exported
head, boot, cap or central torso pixel is repainted by the backing input.
"""
import argparse
import hashlib
import json
import runpy
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

HERE = Path(__file__).resolve().parent
V1 = HERE.parent / "specter-v1"
REPOSITORY = HERE.parents[2]
OUTPUT = REPOSITORY / "mod/graphics/battle/marine-modular-topdown/variants/armor/specter-heavy"


def fingerprint(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def retained_v1_parts():
    """Reuse accepted cuts and aligned casing masks without changing mastering."""
    recipe = runpy.run_path(str(V1 / "export_assets.py"))
    measurements = json.loads((V1 / "export-measurements.json").read_text())
    for name, key in (("source.png", "source_sha256"), ("material-id.png", "material_id_sha256")):
        if fingerprint(V1 / name) != measurements[key]:
            raise ValueError(f"v1 {name} differs from its accepted export measurements")
    source = Image.open(V1 / "source.png").convert("RGBA")
    originals = {}
    masks = {}
    for name, part in measurements["parts"].items():
        originals[name] = source.crop(tuple(part["source_content_bounds"])).resize(
            tuple(part["export_size"]), Image.Resampling.NEAREST)
        masks[name] = Image.open(V1 / "masks" / f"{name}.png").convert("L")
        if masks[name].size != originals[name].size:
            raise ValueError(f"v1 {name} casing mask dimensions differ from retained export")
    return recipe, measurements, source, originals, masks


def shoulder_masks(geometry, source):
    masks = {}
    for name, part in geometry["shoulders"].items():
        mask = Image.new("L", source.size)
        ImageDraw.Draw(mask).polygon(part["cut_polygon"], fill=255)
        masks[name] = mask.crop(tuple(geometry["source_body_bounds"])).resize(
            tuple(geometry["export_canvas"]), Image.Resampling.NEAREST)
    if ImageChops.multiply(*masks.values()).getbbox() is not None:
        raise ValueError("Left and right cap cuts overlap")
    return masks


def registered_backing(path, geometry):
    registration = json.loads((HERE / "backing-regions.json").read_text())
    backing = Image.open(path).convert("RGBA")
    if backing.size != tuple(registration["source_dimensions"]):
        raise ValueError("Backing dimensions changed; measure and update backing-regions.json")
    bounds = tuple(registration["body_bounds"])
    if not (0 <= bounds[0] < bounds[2] <= backing.width and
            0 <= bounds[1] < bounds[3] <= backing.height):
        raise ValueError("Backing body bounds leave the retained raw image")
    return backing.crop(bounds).resize(tuple(geometry["export_canvas"]), Image.Resampling.NEAREST), registration


def split_body(body, backing, cuts):
    union = ImageChops.lighter(*cuts.values())
    alpha = body.getchannel("A")
    # Accepted source interiors are mostly alpha 253, not 255. Preserve that
    # cap alpha and keep underlay away from the actual antialiased fringe.
    support = ImageChops.multiply(union, alpha.point(lambda a: 255 if a >= 250 else 0))
    cyan_exclusions = Image.new("L", backing.size)
    cyan_exclusions.putdata([0 if g-r >= 15 and b-r >= 15 and g >= 110 else 255
                             for r, g, b, a in backing.getdata()])
    support = ImageChops.multiply(support, cyan_exclusions)
    generated_alpha = backing.getchannel("A")
    covered = sum(a >= 128 for a, selected in zip(generated_alpha.getdata(), support.getdata()) if selected)
    required = sum(selected != 0 for selected in support.getdata())
    if covered / max(1, required) < .95:
        raise ValueError(f"Backing misses opaque cap interior: {covered / max(1, required):.1%} covered")
    # New backing retains its own RGB and alpha; the guide owns its exact cut.
    hidden = backing.copy()
    hidden.putalpha(ImageChops.multiply(generated_alpha, support))
    torso = body.copy()
    torso.putalpha(ImageChops.multiply(alpha, ImageChops.invert(union)))
    torso = Image.alpha_composite(torso, hidden)
    shoulders = {}
    for name, cut in cuts.items():
        shoulder = body.copy()
        shoulder.putalpha(ImageChops.multiply(alpha, cut))
        shoulders[name] = shoulder
    assembled = torso.copy()
    for shoulder in shoulders.values():
        assembled = Image.alpha_composite(assembled, shoulder)
    alpha_delta = max(abs(before[3]-after[3]) for before, after in zip(body.getdata(), assembled.getdata()))
    rgb_deltas = [abs(before[i]-after[i]) for before, after in zip(body.getdata(), assembled.getdata())
                  if before[3] for i in range(3)]
    if alpha_delta > 5 or max(rgb_deltas) > 5:
        raise ValueError("Idle assembly exceeds nearopaque source compositing tolerance")
    outside = ImageChops.invert(union)
    if any(before != after for before, after, weight in zip(body.getdata(), torso.getdata(), outside.getdata())
           if weight and before[3]):
        raise ValueError("Generated backing changed protected central torso")
    metrics = {"max_alpha_delta": alpha_delta, "max_rgb_delta": max(rgb_deltas),
               "mean_rgb_delta": sum(rgb_deltas) / max(1, len(rgb_deltas)),
               "hidden_backing_coverage": covered / max(1, required),
               "generated_cyan_pixels_excluded": sum(w and not c for w, c in zip(union.getdata(), cyan_exclusions.getdata()))}
    return torso, shoulders, support, metrics


def export(backing=HERE / "backing-source.png"):
    backing = Path(backing)
    if not backing.exists():
        raise FileNotFoundError("Retained backing-source.png is required; generate and measure the hidden undersuit first")
    geometry = json.loads((HERE / "geometry.json").read_text())
    recipe, measurements, source, originals, casing_masks = retained_v1_parts()
    if list(source.size) != geometry["source_canvas"]:
        raise ValueError("Original v1 canvas differs from shoulder geometry")
    if measurements["parts"]["body"]["source_content_bounds"] != geometry["source_body_bounds"]:
        raise ValueError("Original body crop differs from shoulder geometry")
    if originals["body"].size != tuple(geometry["export_canvas"]):
        raise ValueError("Body mastering differs from shoulder export canvas")
    registered, registration = registered_backing(backing, geometry)
    cuts = shoulder_masks(geometry, source)
    report = {"source_sha256": fingerprint(V1 / "source.png"),
              "material_id_sha256": fingerprint(V1 / "material-id.png"),
              "backing_sha256": fingerprint(backing), "backing_registration": registration,
              "body_source_bounds": geometry["source_body_bounds"],
              "body_export_canvas": geometry["export_canvas"],
              "shoulders": geometry["shoulders"], "palettes": {}}
    # Prepare and validate every output before changing shipped assets.
    outputs = {}
    for palette, ramp in recipe["PALETTES"].items():
        directory = OUTPUT if palette == "slate" else OUTPUT / "paints" / palette
        whole_body = recipe["recolor"](originals["body"], casing_masks["body"], ramp)
        torso, shoulders, support, metrics = split_body(whole_body, registered, cuts)
        outputs[directory / "body.png"] = torso
        for name, shoulder in shoulders.items():
            outputs[directory / geometry["shoulders"][name]["export_name"]] = shoulder
        for name in ("head", "foot"):
            outputs[directory / f"{name}.png"] = recipe["recolor"](originals[name], casing_masks[name], ramp)
        report["palettes"][palette] = {"idle_composite": metrics,
                                       "head_and_foot": "Unchanged v1 recipe", "cap_colors": "Unchanged whole-body v1 palette ramp"}
    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
    masks_dir = HERE / "masks"
    masks_dir.mkdir(exist_ok=True)
    union = ImageChops.lighter(*cuts.values())
    ImageChops.multiply(casing_masks["body"], ImageChops.invert(union)).save(masks_dir / "body.png")
    support.save(masks_dir / "hidden-backing.png")
    for name, cut in cuts.items():
        cut.save(masks_dir / f"{name}-cut.png")
        ImageChops.multiply(casing_masks["body"], cut).save(masks_dir / f"{name}-casing.png")
    (HERE / "export-measurements.json").write_text(json.dumps(report, indent=2) + "\n")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--backing", type=Path, default=HERE / "backing-source.png")
    args = parser.parse_args()
    print(json.dumps(export(args.backing), indent=2))
