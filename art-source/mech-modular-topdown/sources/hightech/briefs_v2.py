"""Writes the second-pass ImageGen briefs for the high-tech row (<weight>/<c|d>.prompt.txt), after the owner's note on
the first ("Feels too clean ... Look at Tri-Tach ship sprites for clues"). Each is generated with its guide_v2.py guide
as the geometry authority and ref-tt-hulls.png (vanilla Tempest, Aurora, Medusa, Paragon) as the material reference:

  python tools/codex_imagegen.py --prompt <weight>/<v>.prompt.txt --out <weight>/<v>-master.png
      --ref <weight>/guide-<v>.png="Geometry/layout authority: the edit target"
      --ref ref-tt-hulls.png="Material/style only; not layout"

Run from anywhere: python art-source/mech-modular-topdown/sources/hightech/briefs_v2.py
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent

ROLES = {
    "light": "a fast, light shield skirmisher walking machine that closes in behind a narrow flux shield",
    "medium": "a medium drone-controller walking machine that carries and relaunches a wing of small combat drones",
    "heavy": "a heavy lance platform walking machine: a wide flux shield and a shoulder-mounted lance laser",
}

TAKES = {
    "c": "separate armour lobes with wide channels of exposed machinery between them",
    "d": "fewer, larger armour lobes wrapping the flanks, machinery showing along the spine and at the rear",
}

SOCKETS = {
    "light": "The dark rings set into the lobes are recessed round weapon sockets, empty.",
    "medium": "The dark rings set into the lobes are DRONE CRADLES: recessed round docking sockets, empty, with clamp "
              "teeth round the rim, because the drones are drawn separately.",
    "heavy": "The dark rings set into the shoulder lobes are recessed round weapon sockets, empty.",
}

TEMPLATE = """Asset type: production-source concept master for a top-down game sprite: the chassis of a new Tri-Tachyon \
HIGH-TECH mech line, {role}, drawn in the same design language as Starsector's high-tech warships.

Image 1 is the exact silhouette and layout authority, a grey value blockout: fill it in, never move, resize or add to \
its shapes, and keep all empty canvas empty, including the open channel between the two nose lobes where a gun barrel \
shows. It is built as {take}. The grey values mean: the DARK shapes are the exposed machine core, a dense mass of \
dark gunmetal machinery (ribbed conduits, actuator stacks, heat-sink fins, cable bundles, small blue-lit ports) that \
shows in every gap between the lobes and as a ribbed engine bank at the rear; the MID-GREY smooth shapes are armour \
lobes, each one a bulging, rounded shell with soft highlights and deep shading at its edges, like carapace; the light \
flat rectangle on the spine is a plain mounting plate where a weapon pod bolts on later; the small light disc near the \
front is a sensor bulb; the lightest disc is the SHIELD EMITTER, a round projector bulb with a soft blue core. {sockets} \
Symmetric about the vertical centre line.

Image 2 is material and style reference only, never layout: Starsector's high-tech hulls, the Tempest, Aurora, Medusa \
and Paragon. Match their finish: pale steel-blue and blue-grey armour shells with cool blue shading, rounded and \
layered, set over dark, busy, finely detailed machinery; round recessed weapon mounts sunk into the shells; small \
blue glow slots; a little wear, scorch and grime in the recesses; light from the upper left. Paint it at full \
resolution as a crisp, detailed game-sprite master, not pixel art.

Strict 90-degree zenith orthographic top-down view of the chassis top surfaces, the machine facing the TOP edge of the \
canvas. True transparent background, including all negative space; no ground, shadow slab, cast shadow, halo, glow \
outside the machine, border, text, labels or logos. Draw only the chassis body: no weapons, missile racks, drones, \
legs or feet, which are separate layers drawn later.

Avoid: clean white plastic, flat untextured panels, long glowing light strips, rivets, tan or olive paint, a framed \
canopy, a pilot figure, perspective, side view, isometric view, cropped edges, a second machine, text.
"""


def main():
    for weight in ROLES:
        for take in TAKES:
            text = TEMPLATE.format(role=ROLES[weight], take=TAKES[take], sockets=SOCKETS[weight])
            (HERE / weight / f"{take}.prompt.txt").write_bytes(text.encode("utf-8"))
    print("wrote six second-pass briefs")


if __name__ == "__main__":
    main()
