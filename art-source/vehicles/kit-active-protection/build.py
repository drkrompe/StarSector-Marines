"""Build the active-protection overlays: the hull and Onager turret overlays in mod/graphics/battle/vehicles/
(kit-active-protection{,-turret}.png) and one per mech chassis in mod/graphics/battle/mech-modular-topdown/
(active-protection-<chassis>.png).

The active protection was the retired Parapet's own turret (art-source/vehicles/tank-screened, whose pack.py writes
screened-turret-frame.png here): an interceptor
launcher box on each rear flank of the turret and a sensor panel inset on each cheek, painted by ImageGen onto the
approved breakthrough turret. Now that it is a compartment internal, those painted launchers and panels are lifted off
that turret as an overlay: on the Onager they ride its turret housing where the Parapet wore them, and on any other gun
tank they are set on the hull deck, launchers on the rear flanks inside the tracks and panels on the front cheeks.

Sources (both 768x768 live turret frames):
  * screened-turret-frame.png: the Parapet's live turret frame (army-tank-screened.png frame 1);
  * stock-turret-frame.png: the breakthrough's live turret frame (army-tank.png frame 1), the same registration.
A part is the pixels the two frames disagree on inside its box (the fit, not the housing under it). Each is scaled
from the turret frame's 98.6 px/cell (7.789194 cells) to the hull frame's 178.6 px/cell (4.3 cells), so it keeps its
painted size, and set on the hull frame with a soft shadow down and right, as the sheets' baked shadows fall.

Vehicle outputs, each one 768x768 frame facing LEFT:
  * kit-active-protection-turret.png, in the Onager's turret frame registration: the fit where the Parapet wore it,
    on the angular turret housing every Onager head shares, so it turns with the head;
  * kit-active-protection.png, in the breakthrough hull frame's registration: the fit bolted to the hull deck, for a
    gun tank whose heads are not that housing (the laser tank), drawn exactly over the live hull.

A mech fits the same part (mechs-nouns.md, one machine, one part catalog), so the same four lifted parts are set on
each chassis too: one 208x208 overlay per chassis, facing UP in that chassis sprite's registration, drawn over the
chassis and turning with the torso. Each part is turned a quarter so the turret's nose (left) becomes the mech's (up),
then placed by the chassis's row of MECH_ANCHORS: the launchers on the rear flanks or shoulders, the panels on the front
cheeks, scaled down to read at the mech's drawn size (a mech is a fraction of a tank; at true scale a launcher box
would be wider than a Hound's spine).

Run from anywhere: python art-source/vehicles/kit-active-protection/build.py [--preview <dir>]
--preview also writes each chassis bare and fitted, enlarged, for checking the anchors (review output, never tracked).
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

HERE = Path(__file__).resolve().parent
GRAPHICS = HERE.parents[2] / "mod" / "graphics" / "battle"
VEHICLES = GRAPHICS / "vehicles"
MECHS = GRAPHICS / "mech-modular-topdown"
OUT = VEHICLES / "kit-active-protection.png"
OUT_TURRET = VEHICLES / "kit-active-protection-turret.png"
FRAME = 768
TURRET_PX_PER_CELL = FRAME / 7.789194
HULL_PX_PER_CELL = FRAME / 4.3
SCALE = HULL_PX_PER_CELL / TURRET_PX_PER_CELL

# Boxes in the turret frame (x0, y0, x1, y1), and where each part's centre lands on the hull frame. The hull's nose
# is to the left; its tracks run along y 187..216 and 550..574, the deck between them; the engine grilles are aft.
PARTS = [
    ((412, 286, 492, 330), (604, 252)),   # launcher, upper rear flank
    ((412, 437, 492, 482), (604, 516)),   # launcher, lower rear flank
    ((306, 312, 356, 348), (150, 250)),   # sensor panel, upper front cheek
    ((306, 427, 356, 463), (150, 518)),   # sensor panel, lower front cheek
]
DIFF_FLOOR = 60
SHADOW_OFFSET = (7, 7)
SHADOW_ALPHA = .55

# The mech overlays. A chassis sprite is MECH_FRAME px square, facing up, its torso pivot at the centre; the overlay
# shares that registration, so the composer draws it exactly where it draws the chassis. Per chassis: its sprite, the
# scales the turret-frame launchers and panels are drawn at (a panel a little larger, so its face reads), and where
# the centre of each launcher and each panel stands, as (lateral px from the centre line, px down from the top edge).
# They are chassis capability data (MechVariant.hostsPointDefence). The right-hand part is the turret's upper one (a left-facing
# turret's right side is up), the left-hand part its mirror twin, lifted from the lower box.
MECH_FRAME = 208
MECH_CENTRE = MECH_FRAME / 2
MECH_ANCHORS = {
    # The broad Bulwark: launchers on its two rear corner blocks, behind the shoulder racks; panels on the cheeks of
    # the raised cockpit block, inside the shoulder plates.
    "bulwark": {"sprite": "chassis.png", "scale": .46, "panel_scale": .6, "launcher": (54, 168), "panel": (40, 52)},
    # The narrow Hound: launchers on its hips, either side of the dorsal rack's tail; panels on the cheeks of its nose.
    "hound": {"sprite": "chassis-hound.png", "scale": .34, "panel_scale": .44, "launcher": (25, 168), "panel": (18, 54)},
    # The delta Warden: launchers on the rear lobes of its wing; panels on the front cheeks either side of the spine.
    "warden": {"sprite": "chassis-warden.png", "scale": .40, "panel_scale": .5, "launcher": (48, 150), "panel": (24, 70)},
}
MECH_SHADOW_OFFSET = (2, 2)
MECH_SHADOW_BLUR = 1.2


def part(screened, stock, box):
    """The fit inside {@code box}: screened pixels that differ from the stock turret, alpha kept, the rest cleared."""
    a = screened.crop(box)
    b = stock.crop(box)
    diff = ImageChops.difference(a.convert("RGB"), b.convert("RGB")).convert("L")
    mask = diff.point(lambda v: 255 if v * 3 > DIFF_FLOOR else 0).filter(ImageFilter.MaxFilter(3)).filter(
        ImageFilter.MinFilter(3))
    alpha = ImageChops.multiply(a.getchannel("A"), mask)
    a.putalpha(alpha)
    return a


def shadowed(parts, offset, blur):
    """{@code parts} over a soft shadow of themselves, down and right."""
    out = Image.new("RGBA", parts.size, (0, 0, 0, 0))
    shade = parts.getchannel("A").point(lambda v: int(v * SHADOW_ALPHA)).filter(ImageFilter.GaussianBlur(blur))
    out.putalpha(ImageChops.offset(shade, *offset))
    out.alpha_composite(parts)
    return out


def scaled(piece, factor):
    """{@code piece} resized by {@code factor}, premultiplied so its cleared edge does not bleed dark."""
    w, h = max(1, round(piece.width * factor)), max(1, round(piece.height * factor))
    return piece.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")


def place(frame, piece, centre):
    frame.alpha_composite(piece, (round(centre[0] - piece.width / 2), round(centre[1] - piece.height / 2)))


def mech_overlay(pieces, anchors):
    """One chassis's overlay: the four parts turned to face up, scaled, and set at the chassis's anchors."""
    factors = (anchors["scale"], anchors["scale"], anchors["panel_scale"], anchors["panel_scale"])
    upper_launcher, lower_launcher, upper_panel, lower_panel = (
        scaled(p.rotate(-90, expand=True), factor) for p, factor in zip(pieces, factors))
    frame = Image.new("RGBA", (MECH_FRAME, MECH_FRAME), (0, 0, 0, 0))
    for (right, left), (dx, y) in (((upper_launcher, lower_launcher), anchors["launcher"]),
                                   ((upper_panel, lower_panel), anchors["panel"])):
        place(frame, right, (MECH_CENTRE + dx, y))
        place(frame, left, (MECH_CENTRE - dx, y))
    return shadowed(frame, MECH_SHADOW_OFFSET, MECH_SHADOW_BLUR)


def preview(directory, overlays):
    """Each chassis bare and fitted, side by side at 3x on a ground-grey field."""
    directory.mkdir(parents=True, exist_ok=True)
    zoom = 3
    sheet = Image.new("RGBA", (MECH_FRAME * zoom * 2 * len(overlays), MECH_FRAME * zoom), (88, 92, 84, 255))
    for i, (name, overlay) in enumerate(overlays.items()):
        chassis = Image.open(MECHS / MECH_ANCHORS[name]["sprite"]).convert("RGBA")
        fitted = chassis.copy()
        fitted.alpha_composite(overlay)
        for j, image in enumerate((chassis, fitted)):
            sheet.alpha_composite(image.resize((MECH_FRAME * zoom,) * 2, Image.NEAREST),
                                  ((2 * i + j) * MECH_FRAME * zoom, 0))
    sheet.save(directory / "mech-active-protection-preview.png")


def main():
    screened = Image.open(HERE / "screened-turret-frame.png").convert("RGBA")
    stock = Image.open(HERE / "stock-turret-frame.png").convert("RGBA")
    hull = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
    turret = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
    pieces = []
    for box, centre in PARTS:
        piece = part(screened, stock, box)
        pieces.append(piece)
        turret.alpha_composite(piece, box[:2])
        w, h = round(piece.width * SCALE), round(piece.height * SCALE)
        resized = piece.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")
        hull.alpha_composite(resized, (round(centre[0] - w / 2), round(centre[1] - h / 2)))
    shadowed(hull, SHADOW_OFFSET, 4).save(OUT)
    # On the turret the parts are painted where they stood, with their own baked shading: no added shadow.
    turret.save(OUT_TURRET)
    overlays = {}
    for name, anchors in MECH_ANCHORS.items():
        overlays[name] = mech_overlay(pieces, anchors)
        overlays[name].save(MECHS / f"active-protection-{name}.png")
    print(f"wrote {OUT}, {OUT_TURRET} and {len(overlays)} mech overlays in {MECHS}")
    if "--preview" in sys.argv:
        directory = Path(sys.argv[sys.argv.index("--preview") + 1])
        preview(directory, overlays)
        print(f"preview in {directory}")


if __name__ == "__main__":
    main()
