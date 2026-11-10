"""Second-pass briefs for the low-tech faction variants: two flamer rigs, after the owner's notes on the first sheets
(briefs.py). Owner: the pirate Runner's back engines and the Boarder's claw "feel wrong", the Path Cutter "feels
wrong", and "a runner 'flamer' variant might be interesting" / "again a flamer of some type might be interesting".

A machine flamer builds on the infantry flamer and the fire rules (`fire-nouns.md`). The flame gun stays its own
swappable part (`mech-lines.md`, law 4): each sheet draws the bare machine with an empty nose or arm mount and, beside
it, the flame gun as a loose part. The machine's fuel load is the variant's body.

  python tools/codex_imagegen.py --prompt sheets/<pirate-torch|path-censer>.prompt.txt --out sheets/<...>-sheet.png
      --ref ref-lowtech-row.png="Frame identity: ..." [--ref ref-path-material.png="Material, colour only ..."]

The Censer was generated from the first wording (before "burner" replaced "flame gun", after the image filter
refused the Torch); its provenance holds that prompt as sent.

Run from anywhere: python art-source/mech-modular-topdown/sources/lowtech/variants/flamers.py
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent

COMMON = """
Layout: one wide landscape sheet. On the left, the machine whole, large, facing the TOP edge of the sheet, symmetric \
about its own vertical centre line except where the brief says otherwise. On the right, with clear space between, \
its burner nozzle drawn on its own as a loose part, seen from above, pointing to the top, at the same scale. Nothing \
else on the sheet.

Image 1 is the frame identity reference: the three machines these variants are built from (left to right the light \
Hound, the medium Warden, the heavy Bulwark), seen from above. The machine must still read as the one named: keep \
its proportions, its round hip bearings, its pilot canopy and its mount places, and change only what the brief says. \
No thrusters or engines on its back, no claw, no saw blade.

Strict 90-degree zenith orthographic top-down view. True transparent background, including all negative space; no \
ground, shadow slab, cast shadow, halo, glow, flame, smoke, border, text, letters, numbers, logos or skulls. Crisp \
painted game-sprite concept art with rivets and weld seams, light from the upper left, not pixel art.

Avoid: fire, flame or smoke effects; any gun other than the one burner nozzle; perspective, side view or isometric view; a \
starship's outline; bird or chicken legs; text.
"""

PIRATE = """Asset type: concept sheet for a top-down game: a PIRATE FLAMER RIG, the TORCH, a captured Hegemony light \
walking machine (the Hound, left in image 1) a pirate crew has rebuilt as a fuel-tanker work rig carrying an industrial burner.

The machine: the Hound's body, its side plate partly cut away for lightness. Its back carries the fuel: three or \
four mismatched scavenged fuel drums and a pressure bottle lashed in a welded cradle with chains and straps, joined by \
patched hoses and hand valves that run forward under the body to its nose. The nose mount is EMPTY, a flat bolted \
plate with a hose coupling. A dented heat shield of scrap plate is bolted over the canopy's front edge. Paint it as \
the pirate scrap bodies in image 1: mismatched salvaged panels (faded tan, grey, olive, blue-grey civilian panels, \
red-oxide primer, bare steel) welded on with visible beads and oversized bolts, crude hand-brushed dark red, worn \
and grimy but legible; the fuel drums each a different faded colour with crude hand-painted hazard marks.

The loose part beside it: the pirate burner, a cut-down industrial burner nozzle on a short welded boom, a pilot \
light shroud at its tip, a hose coupling at its base, wrapped in heat tape.
""" + COMMON

PATH = """Asset type: concept sheet for a top-down game: a LUDDIC PATH FLAMER CONVERSION, the CENSER, an industrial \
medium walking machine (the Warden, middle in image 1) a Pather cell has converted to burn out what it calls \
corruption.

The machine: the Warden's body, its shoulders kept. Its back carries a large tall ribbed pressure tank of \
promethium fuel in a welded frame, like a church boiler or an industrial gas cylinder, with gauges, valves and a \
pipe manifold, joined by armoured hoses along its right side to its right arm. The right arm mount is EMPTY, a flat \
bolted plate with a hose coupling; the left arm keeps an empty mounting plate. Prayer cloth strips and simple painted \
devotional shapes (no text) hang from its plate. Scorch soot darkens the front of the body.

Image 2 is material and colour reference only, a sheet of surface swatches cut from inside the Luddic Path's \
warships; it holds no shapes to copy. Paint the conversion in that Pather scheme: drab olive and yellow-green plate, \
pale cream panels, bold red stripe bands, dark grey machinery, grime and sooty scorching, scuffed bare metal on worn \
edges, hand-welded repairs.

The loose part beside it: the Path burner, a long heavy industrial burner lance with a flared cowl at its \
tip, cooling fins, a pilot light and a hose coupling at its base, in dark scorched steel with a red band.
""" + COMMON


def main():
    (HERE / "sheets").mkdir(exist_ok=True)
    (HERE / "sheets" / "pirate-torch.prompt.txt").write_bytes(PIRATE.encode("utf-8"))
    # The Censer is generated; its prompt as sent stays in sheets/path-censer.prompt.txt and its provenance.
    print("wrote the Torch brief")


if __name__ == "__main__":
    main()
