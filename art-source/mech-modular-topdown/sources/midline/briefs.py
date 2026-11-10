"""Writes the first ImageGen briefs for the midline row (frames/<a|b|c>.prompt.txt): concept sheets of a whole row, light,
medium and heavy side by side, so the row's own frames, locomotion and mounts can be laid out from the owner's pick
(mech-lines.md: midline is balanced and modular, mixed mounts, shared parts, easy repair). Inspiration is the Eagle
(owner) and its midline kin: tan and khaki armour slabs over grey machinery, a forked twin-prong prow, many round
turret sockets, a central dome. Each row moves in a way no low-tech walker (two-legged, broad torso, shoulder racks)
or high-tech line (the four-legged crawler, the skimmers) does, so a midline line is never a livery of another (law 1).
Every mount is drawn empty (law 4: machines are modular); a gun is its own layer.

ref-midline-hulls.png (vanilla Centurion, Hammerhead, Falcon, Eagle, scaled up) is the material reference only:

  python tools/codex_imagegen.py --prompt frames/<v>.prompt.txt --out frames/<v>-sheet.png
      --ref ref-midline-hulls.png="Material/style only; not layout"

Run from anywhere: python art-source/mech-modular-topdown/sources/midline/briefs.py
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent

FRAMES = {
    "a": ("combat walker", """PRONG WALKERS on two digitigrade legs. Each machine is a slim, forward-leaning armoured body on two \
backward-bending bird-like legs set wide under its hips, the feet splayed into three toes. From the body's front shoulders \
two long armoured booms reach forward side by side like the Eagle's twin prongs, with a gap between them; the mounts sit \
on the booms. A rounded sensor dome sits between the booms' roots. LIGHT: a narrow body with short booms, one empty round \
weapon socket on each boom. MEDIUM: longer booms, two empty sockets on each and a rack hatch (an empty square missile \
mount) on the back. HEAVY: broad, heavily plated booms like the Eagle's, two large empty sockets on each, two square \
missile mounts on the back and a dome flanked by small empty sockets."""),
    "b": ("combat walker", """WHEEL-WALKERS on four legs that roll. Each machine is a flat, compact, panelled hull on four \
jointed legs, one at each corner, each leg ending in a single big armoured drive wheel instead of a foot, so it can roll \
on roads and step over rubble. The legs fold out sideways from the hull like a lunar rover's. The hull's front is split \
into a short forked prow like the Eagle's. LIGHT: a small low hull, one empty round weapon socket at the prow. MEDIUM: a \
longer hull with an empty round socket in each fork of the prow and an empty square missile mount on the back. HEAVY: a \
broad hull with a large empty round socket in each prow fork, a third on the spine and two square missile mounts."""),
    "c": ("combat vehicle", """ARMOURED CARS on wheels. Each machine is a wheeled armoured fighting vehicle with exposed \
armoured wheels along its flanks, its bow split into a forked twin prow like the Eagle's with the driver's slit between \
the forks, and a raised central dome behind them. LIGHT: a four-wheeled scout car, one empty round weapon socket on the \
dome. MEDIUM: a six-wheeled car, one empty round socket in each prow fork and one on the dome. HEAVY: an eight-wheeled car, \
large empty round sockets in each fork, a big empty turret ring on the dome, and two square missile mounts on the rear \
deck."""),
}

TEMPLATE = """Asset type: concept sheet for a top-down game: the three machines of a new MIDLINE {kind} row, light, \
medium and heavy, drawn whole (body and locomotion together) in the design language of Starsector's midline warships, \
the Eagle above all.

The row is {frame}

Every weapon mount is drawn EMPTY: a dark recessed round socket ring or a square hatch, with no gun, barrel, launcher \
or turret in it. Guns are separate parts added later.

Layout: one wide landscape sheet, the three machines in one row left to right, LIGHT, MEDIUM, HEAVY, evenly spaced \
with clear empty space round each, drawn to one scale so the heavy is clearly the biggest (about twice the light's \
length). Every machine faces the TOP edge of the sheet. Each machine is symmetric about its own vertical centre line.

The reference image is material and style reference only, never layout: Starsector's midline hulls, the Centurion, \
Hammerhead, Falcon and Eagle. Match their finish: weathered tan, khaki and bone-coloured armour slabs with olive-grey \
shading and crisp panel lines, flat-faced and chamfered, bolted in clean overlapping panels, set over dark grey and \
gunmetal machinery (pistons, conduits, vents) showing in the gaps; round recessed mounts; a pale rounded dome; small \
yellow-green indicator lights; light grime in the recesses; light from the upper left. These are compact ground machines \
about the size of a tank, not starships: no engine nozzles, no hangar bays, no bridge towers, no long ship hulls.

Strict 90-degree zenith orthographic top-down view of the machines' top surfaces. True transparent background, \
including all negative space between legs, wheels and parts; no ground, shadow slab, cast shadow, halo, border, text, \
labels or logos. Paint at full resolution as crisp, detailed game-sprite concept art, not pixel art.

Avoid: guns or barrels of any kind, a broad-torsoed two-legged walker with box missile racks on its shoulders, a \
four-legged crab shell, a hovering skimmer, rusty scrap, steel-blue carapace, white plastic, cockpit canopy, pilot \
figure, perspective, side view, isometric view, cropped machines, more or fewer than three machines, text.
"""


def main():
    (HERE / "frames").mkdir(exist_ok=True)
    for take, (kind, frame) in FRAMES.items():
        text = TEMPLATE.format(kind=kind, frame=frame)
        (HERE / "frames" / f"{take}.prompt.txt").write_bytes(text.encode("utf-8"))
    print("wrote three midline briefs")


if __name__ == "__main__":
    main()
