"""Writes the third-pass ImageGen briefs for the high-tech row (frames/<e|f|g>.prompt.txt), after the owner's note on the
heavy-shell bodies worn on the Hound, Warden and Bulwark: "These seem just like reskinned versions of the hound/bulwark".
Those bodies were painted into guides drawn round the low-tech frames' outlines and mounts, so they kept those frames'
shape (mech-lines.md, law 1: a line is never a livery of another). These briefs drop the guides: each asks for a
concept sheet of the whole row, light, medium and heavy side by side, on a way of moving that no low-tech frame has, so
the row's own frames, legs and mounts can be laid out from the pick. ref-tt-hulls.png (vanilla Tempest, Aurora, Medusa,
Paragon) is the material reference only:

  python tools/codex_imagegen.py --prompt frames/<v>.prompt.txt --out frames/<v>-sheet.png
      --ref ref-tt-hulls.png="Material/style only; not layout"

Run from anywhere: python art-source/mech-modular-topdown/sources/hightech/briefs_v3.py
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent

FRAMES = {
    "e": """SKIMMERS, with no legs at all. Each machine floats a hand's breadth off the ground on grav-lift drives: \
round recessed lift rings sunk into the underside edges, seen from above as glowing blue rings peeking out past the \
hull rim, plus a ribbed thruster bank at the stern. The hulls are low, flat and streamlined like the Tempest seen from \
above. LIGHT: a slim dart, a single small energy gun in a round socket at the nose and a shield emitter bulb just \
behind it. MEDIUM: a broad manta-ray shape with two round drone cradles (empty docking rings) sunk into its wings and \
a short energy gun at the nose. HEAVY: a long, heavy hull with a big lance laser built into its spine, the barrel \
running down the centre line past the nose, and a large shield emitter bulb at the prow.""",
    "f": """STRIDERS on three long legs. Each machine is a compact rounded body pod held high on a tripod of long, \
thin, jointed legs, one leg pointing forward-left, one forward-right and one straight back, splayed wide so the \
legs reach well beyond the body on every side; each leg ends in a small narrow foot. The legs are slender machined \
struts with exposed actuators at the knees, much longer than the body is wide. LIGHT: a small egg-shaped pod with a \
single small energy gun under its nose and a shield emitter bulb on top. MEDIUM: a wider pod ringed with three round \
drone cradles (empty docking rings) and a short energy gun. HEAVY: a big pod carrying a long lance laser on its \
back, the barrel reaching past the front, and a large shield emitter bulb.""",
    "g": """CRAWLERS on four legs. Each machine is a low, wide, rounded carapace like a crab or a beetle shell, with \
four short, thick, jointed legs coming out at its four corners, the two front legs reaching forward and the two rear \
legs reaching back, and the shell's rim hanging over the hips. LIGHT: a small round shell, narrow and quick-looking, \
with a single small energy gun in a socket at the front and a shield emitter bulb. MEDIUM: a wider, flatter oval shell \
with two round drone cradles (empty docking rings) sunk into its back and a short energy gun at the front. HEAVY: a \
broad armoured shell with a long lance laser mounted along its centre line, the barrel past the front edge, and a \
large shield emitter bulb.""",
}

TEMPLATE = """Asset type: concept sheet for a top-down game: the three machines of a new Tri-Tachyon HIGH-TECH combat \
walker row, light, medium and heavy, drawn whole (body, locomotion and weapons together) in the design language of \
Starsector's high-tech warships.

The row is {frame}

Layout: one wide landscape sheet, the three machines in one row left to right, LIGHT, MEDIUM, HEAVY, evenly spaced \
with clear empty space round each, drawn to one scale so the heavy is clearly the biggest (about twice the light's \
length). Every machine faces the TOP edge of the sheet. Each machine is symmetric about its own vertical centre line.

The reference image is material and style reference only, never layout: Starsector's high-tech hulls, the Tempest, \
Aurora, Medusa and Paragon. Match their finish: pale steel-blue and blue-grey armour shells with cool blue shading, \
rounded, bulging and layered like carapace, set over dark, busy, finely detailed gunmetal machinery (ribbed conduits, \
actuators, heat-sink fins, small blue-lit ports) showing in the gaps; round recessed mounts sunk into the shells; \
small blue glow slots; a little wear and grime in the recesses; light from the upper left. These are compact ground \
machines about the size of a tank, not starships: no hangar bays, no bridge towers, no long ship hulls.

Strict 90-degree zenith orthographic top-down view of the machines' top surfaces. True transparent background, \
including all negative space between legs and parts; no ground, shadow slab, cast shadow, halo, border, text, labels \
or logos. Paint at full resolution as crisp, detailed game-sprite concept art, not pixel art.

Avoid: a two-legged walker with a broad torso and box missile racks on its shoulders, clean white plastic, flat \
untextured panels, long glowing light strips, tan or olive paint, a cockpit canopy, a pilot figure, perspective, side \
view, isometric view, cropped machines, more or fewer than three machines, text.
"""


def main():
    (HERE / "frames").mkdir(exist_ok=True)
    for take, frame in FRAMES.items():
        text = TEMPLATE.format(frame=frame)
        (HERE / "frames" / f"{take}.prompt.txt").write_bytes(text.encode("utf-8"))
    print("wrote three third-pass briefs")


if __name__ == "__main__":
    main()
