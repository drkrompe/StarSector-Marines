"""Writes the ImageGen briefs for the high-tech row's concept masters (<weight>/<a|b>.prompt.txt), one per guide from
guide.py: a shared brief round each body's own description. Each is generated with its guide as the geometry authority
and ref-hover-scout.png as the material reference:

  python tools/codex_imagegen.py --prompt <weight>/<v>.prompt.txt --out <weight>/<v>-master.png
      --ref <weight>/guide-<v>.png="Geometry/layout authority: the edit target"
      --ref ref-hover-scout.png="Material/style only; not layout"

Run from anywhere: python art-source/mech-modular-topdown/sources/hightech/briefs.py
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent

ROLES = {
    "light": "a fast, light shield skirmisher walking machine: it closes in behind a narrow flux shield and fights at "
             "short range with twin pulse lasers",
    "medium": "a medium drone-controller walking machine: it carries and relaunches a wing of small combat drones and "
              "shields itself while they fight",
    "heavy": "a heavy lance platform walking machine: a wide flux shield and a shoulder-mounted lance laser, the "
             "high-tech counterpart of a line battle walker",
}

FAMILY = {
    "a": "Its silhouette is kin to the Tri-Tachyon hover craft: rounded composite slabs set inside a thick dark "
         "graphite bumper rim made of segmented rubberised blocks, like the reference's hull.",
    "b": "Its silhouette is the swept, curved high-tech style of Tri-Tachyon warships: one continuous smooth shell, "
         "crescent wing fairings sweeping back over the legs, no hard corners, panel lines following the curves.",
}

SHAPES = {
    ("light", "a"): "a slim spine body inside its bumper rim; two long rounded leg fairings on the flanks, each in its "
                    "own bumper rim; two narrow nose prongs either side of an open channel",
    ("light", "b"): "a slim body flaring into two swept crescent wings over the legs; two narrow curved nose prongs "
                    "either side of an open channel",
    ("medium", "a"): "a broad rounded body inside its bumper rim; two squat sponsons on the rear flanks; two nose prongs "
                     "either side of an open channel",
    ("medium", "b"): "a broad swept delta body with long curved wing tips at the rear flanks; two curved nose prongs "
                     "either side of an open channel",
    ("heavy", "a"): "a big rounded slab body inside its bumper rim; a tall armoured shoulder block on each flank",
    ("heavy", "b"): "a big rounded shield-shaped shell, a blunt pointed prow, broad curved shoulders and a notched "
                    "rear",
}

PARTS = {
    "light": "The lighter disc at the rear of the spine is the SHIELD EMITTER: a round projector housing with a "
             "glowing cyan core, like the reference's sensor orb but larger.",
    "medium": "The four dark rings are DRONE CRADLES: recessed circular docking pads with clamp arms and small cyan "
              "docking lights, empty, because the drones are drawn separately. The lighter disc at the very rear is "
              "the SHIELD EMITTER: a round projector housing with a glowing cyan core.",
    "heavy": "The large lighter disc in the middle of the spine is the SHIELD EMITTER: a broad round projector with "
             "concentric rings and a glowing cyan core, the machine's centrepiece.",
}

TEMPLATE = """Asset type: production-source concept master for a top-down game sprite: the chassis of a new Tri-Tachyon \
HIGH-TECH mech line, {role}.

Image 1 is the exact silhouette and layout authority, a grey blockout: fill it in, never move, resize, widen or add to \
its shapes, and keep all empty canvas empty, including the open channel between the nose prongs where a gun barrel \
shows. Its shapes: {shapes}; a lighter arrow-shaped SENSOR VISOR on the spine near the front; a flat lighter \
rectangular deck down the middle of the spine where a weapon pod bolts on later, kept a plain flat mounting plate. \
{parts} Everything is symmetric about the vertical centre line. {family}

Image 2 is material and style reference only, never layout: the Tri-Tachyon hover scout this walker serves beside. \
Match its finish exactly: clean off-white and pale grey composite panels with fine recessed seams, small vents and \
access hatches, graphite and dark gunmetal working parts, thin glowing cyan light strips along panel edges, the arrow \
visor in dark smoked glass with a faint cyan sheen, crisp painted game-sprite finish, light from the upper left. It is \
corporate high-tech hardware: sealed, smooth, precise and expensive, with nothing exposed: no rivets, no pipes, no \
bolted plate, no rust or grime, only light scuffs.

Strict 90-degree zenith orthographic top-down view of the chassis top surfaces, the machine facing the TOP edge of the \
canvas. True transparent background, including all negative space; no ground, shadow slab, cast shadow, halo, glow \
outside the machine, border, text, labels or logos. Draw only the chassis body: no weapons, missile racks, drones, \
legs or feet, which are separate layers drawn later.

Avoid: rivets, exposed pipes, tan or olive paint, rust, a framed bomber canopy, a pilot figure, perspective, side view, \
isometric view, cropped edges, a second machine, text.
"""


def main():
    for (weight, variant), shapes in SHAPES.items():
        text = TEMPLATE.format(role=ROLES[weight], shapes=shapes, parts=PARTS[weight], family=FAMILY[variant])
        (HERE / weight / f"{variant}.prompt.txt").write_bytes(text.encode("utf-8"))
    print("wrote six briefs")


if __name__ == "__main__":
    main()
