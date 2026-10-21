"""Ship crew layers: silhouette guide and mechanical export. Run from the repository root.

    python art-source/ship-crew/ship_crew.py guide
    python art-source/ship-crew/ship_crew.py export

The crew wear the same anatomy as the marines. Each guide frame is the alpha of a
shipped marine layer (fatigues body, bare head, infantry boot) scaled up by one
factor, so a reskin that keeps the guide registers in the compositor exactly as the
marine it stands beside does. Export cuts each frame mechanically, scales it back
down with nearest-neighbour sampling (the marines' pixel-art grid) and bounds its
alpha by the same marine silhouette.
"""

from pathlib import Path
import sys

from PIL import Image, ImageFilter

ROLES = ("command", "engineering", "medical", "service")
MARINE = Path("mod/graphics/battle/marine-modular-topdown")
SILHOUETTES = {
    "body": MARINE / "variants/armor/armorless/body.png",
    "head": MARINE / "variants/armor/armorless/head.png",
    "foot": MARINE / "marine-foot.png",
}
SCALE = 6
CANVAS = (1536, 1024)
# Top-left of each frame on the guide; frame size is the silhouette size x SCALE.
ORIGINS = {"body": (24, 152), "head": (960, 40), "foot": (1176, 640)}
GUIDE_GRAY = (163, 163, 163, 255)
# Crew and colonists are shorter in the torso than the marines (owner, 2026-10-21):
# the body layer is squashed vertically by this factor about the neck socket's
# centre row (the marine fatigues' socket spans rows 8-51 of 120), so the head still
# seats in the socket and only the chest below it shortens. Marines are unchanged.
BODY_Y_SCALE = 0.85
NECK_CENTRE_ROW = 29.5
SOURCE = Path("art-source/ship-crew")
TARGET = Path("mod/graphics/battle/ship-crew-modular-topdown")


def silhouette(part: str) -> Image.Image:
    return Image.open(SILHOUETTES[part]).convert("RGBA").getchannel("A")


def frame(part: str) -> tuple[int, int, int, int]:
    w, h = silhouette(part).size
    x, y = ORIGINS[part]
    return x, y, x + w * SCALE, y + h * SCALE


def guide() -> None:
    sheet = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    for part in SILHOUETTES:
        x0, y0, x1, y1 = frame(part)
        # Smooth the marine's pixel steps out of the enlarged edge, then make it hard.
        mask = silhouette(part).resize((x1 - x0, y1 - y0), Image.Resampling.BILINEAR)
        mask = mask.filter(ImageFilter.GaussianBlur(SCALE)).point(lambda a: 255 if a >= 128 else 0)
        sheet.paste(Image.new("RGBA", mask.size, GUIDE_GRAY), (x0, y0), mask)
    out = Path("art-source/templates/ship-crew-silhouettes.png")
    sheet.save(out)
    print(out)


def export() -> None:
    export_roles(ROLES, SOURCE, TARGET)


def export_roles(roles, source: Path, target_root: Path) -> None:
    """Cut each role's raw (source/raw/<role>.png) into target_root/<role>/{body,head,foot}.png."""
    for role in roles:
        raw = Image.open(source / "raw" / f"{role}.png").convert("RGBA")
        sx, sy = raw.width / CANVAS[0], raw.height / CANVAS[1]
        target = target_root / role
        target.mkdir(parents=True, exist_ok=True)
        for part in SILHOUETTES:
            x0, y0, x1, y1 = frame(part)
            mask = silhouette(part)
            cut = raw.crop((round(x0 * sx), round(y0 * sy), round(x1 * sx), round(y1 * sy)))
            layer = cut.resize(mask.size, Image.Resampling.NEAREST)
            r, g, b, a = layer.split()
            # Generated alpha inside the marine silhouette; nothing outside it.
            a = Image.composite(a, Image.new("L", mask.size, 0), mask.point(lambda v: 255 if v else 0))
            layer = Image.merge("RGBA", (r, g, b, a))
            if part == "body":
                layer = squash_body(layer, cut)
            layer.save(target / f"{part}.png", optimize=True)
            print(target / f"{part}.png")


def squash_body(layer: Image.Image, cut: Image.Image) -> Image.Image:
    """The body on its marine canvas, scaled by BODY_Y_SCALE about the neck socket's centre row.

    Resampled from the raw cut rather than from the 120-row layer, so the shorter
    torso keeps whole pixels instead of dropping every n-th row of the finished one.
    """
    if BODY_Y_SCALE == 1.0:
        return layer
    w, h = layer.size
    short = round(h * BODY_Y_SCALE)
    top = round(NECK_CENTRE_ROW * (1 - BODY_Y_SCALE))
    mask = silhouette("body").resize((w, short), Image.Resampling.NEAREST)
    r, g, b, a = cut.resize((w, short), Image.Resampling.NEAREST).split()
    a = Image.composite(a, Image.new("L", mask.size, 0), mask.point(lambda v: 255 if v else 0))
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(Image.merge("RGBA", (r, g, b, a)), (0, top))
    return out


if __name__ == "__main__":
    {"guide": guide, "export": export}[sys.argv[1] if len(sys.argv) > 1 else "guide"]()
