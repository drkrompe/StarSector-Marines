"""Writes the second-pass ImageGen briefs for the midline row (frames/<d|e|f>.prompt.txt), after the owner's note on the
first pass: "These all just have the shape of the Eagle or look like chickens. We should consider the armor and color,
but not the raw shape of the source ship ... A wheeled concept might not be bad to pursue."

So the Eagle gives only its material: ref-midline-material.png is a sheet of solid swatches cut from inside the
Eagle, Falcon, Hammerhead, Centurion and Champion sprites, with no outline in it to copy. Each brief is a wheeled row
whose outline follows from what the machine does, never from a ship: no forked prow, prongs, bird legs or dome.

  python tools/codex_imagegen.py --prompt frames/<v>.prompt.txt --out frames/<v>-sheet.png
      --ref ref-midline-material.png="Material, colour and surface detail only; contains no shapes to copy"

Run from anywhere: python art-source/mech-modular-topdown/sources/midline/briefs_v2.py
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent

FRAMES = {
    "d": """ROVER WALKERS: wheel-legged machines. Each is a compact, squat, octagonal armoured hull, about as wide as \
it is long, flat-topped with chamfered corners, carried on jointed legs that each end in a fat armoured drive wheel; \
the legs can lift a wheel to step over rubble. The legs leave the hull's flanks horizontally and kink down to the \
wheels, so the wheels sit well out from the hull at the corners. LIGHT: a small square hull on four wheel-legs, one \
empty round weapon socket on top. MEDIUM: a rectangular hull on four heavier wheel-legs, two empty round sockets side \
by side on top and one empty square missile hatch. HEAVY: a long rectangular hull on six wheel-legs (three a side), \
two large empty round sockets and two empty square missile hatches.""",
    "e": """ARTICULATED CARRIERS: each machine is two armoured modules, one behind the other, joined by a visible \
articulation joint (a hinge with exposed pistons and cables) so the body bends in the middle to steer, and each module \
rides on its own pair of chunky wheels. The front module is a short, blunt drive cab with a flat front and narrow \
vision slits; the rear module is a swappable mission box with a flat top. LIGHT: two short modules, four wheels in \
all, one empty round weapon socket on the rear module. MEDIUM: a longer rear module, six wheels in all, two empty \
round sockets and one empty square missile hatch. HEAVY: three modules in a chain (cab, power module, mission module), \
eight wheels in all, two large empty round sockets and two empty square missile hatches.""",
    "f": """BIG-WHEEL MACHINES: each machine rides on a few very large, wide, armoured balloon drum wheels, taller \
than its body, with the armoured body slung low between them; the wheels have deep chevron treads and armoured hub \
covers. LIGHT: a narrow body between two huge wheels side by side, like a monowheel pair, with a small tail wheel \
behind; one empty round weapon socket on the body. MEDIUM: a box body between four huge wheels at its corners; two \
empty round sockets and one empty square missile hatch. HEAVY: a long body on six huge wheels; two large empty round \
sockets and two empty square missile hatches.""",
}

TEMPLATE = """Asset type: concept sheet for a top-down game: the three machines of a new MIDLINE wheeled combat \
machine row, light, medium and heavy, drawn whole.

The row is {frame}

Every weapon mount is drawn EMPTY: a dark recessed round socket ring or a square hatch, with no gun, barrel, launcher \
or turret in it. Guns are separate parts added later.

Layout: one wide landscape sheet, the three machines in one row left to right, LIGHT, MEDIUM, HEAVY, evenly spaced \
with clear empty space round each, drawn to one scale so the heavy is clearly the biggest (about twice the light's \
length). Every machine faces the TOP edge of the sheet. Each machine is symmetric about its own vertical centre line.

The reference image is a sheet of surface swatches cut from Starsector's midline warships: take only their material, \
colour and surface detail, never any shape. Weathered tan, khaki and bone-coloured armour plate with olive-grey \
shading and crisp engraved panel lines, chamfered slab panels bolted in clean overlapping layers, over dark grey and \
gunmetal machinery (pistons, conduits, vents) showing in the gaps; round recessed socket rings; small yellow-green \
indicator lights; light grime in the recesses; light from the upper left. These are compact ground machines about \
the size of a tank or an armoured car.

Strict 90-degree zenith orthographic top-down view of the machines' top surfaces. True transparent background, \
including all negative space between wheels, legs and parts; no ground, shadow slab, cast shadow, halo, border, text, \
labels or logos. Paint at full resolution as crisp, detailed game-sprite concept art, not pixel art.

Avoid: any resemblance to a starship; a forked or split prow, twin prongs or a V-shaped nose; a dome; bird or \
chicken legs; guns or barrels of any kind; tracks; a hovering skimmer; a two-legged walker; rusty scrap; steel-blue \
or white plastic; a cockpit canopy; a pilot figure; perspective, side view or isometric view; cropped machines; more \
or fewer than three machines; text.
"""


def main():
    (HERE / "frames").mkdir(exist_ok=True)
    for take, frame in FRAMES.items():
        (HERE / "frames" / f"{take}.prompt.txt").write_bytes(TEMPLATE.format(frame=frame).encode("utf-8"))
    print("wrote three second-pass midline briefs")


if __name__ == "__main__":
    main()
