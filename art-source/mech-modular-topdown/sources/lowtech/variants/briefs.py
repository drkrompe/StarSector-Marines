"""Concept briefs for the low-tech row's first faction variants: pirate field rigs and Luddic Path conversions
(`mech-lines.md`, order of work step 2). A variant stays in its line's family and weight class (law 2) and owns its
own silhouette and default loadout (`mechs-nouns.md`, variant), so each concept reworks the Hound, Warden or Bulwark
frame rather than repainting it; the pirate scrap bodies already cover the paint.

Builds two references, then writes one brief per sheet:

  ref-lowtech-row.png   the three pirate scrap bodies side by side: frame identity (outline, hips, canopy, mounts)
  ref-path-material.png solid swatches cut from inside vanilla's Pather hulls: material and colour only, no outline

  python tools/codex_imagegen.py --prompt sheets/pirates.prompt.txt --out sheets/pirates-sheet.png
      --ref ref-lowtech-row.png="Frame identity: ..."
  python tools/codex_imagegen.py --prompt sheets/path.prompt.txt --out sheets/path-sheet.png
      --ref ref-lowtech-row.png="Frame identity: ..." --ref ref-path-material.png="Material, colour only ..."

Run from anywhere: python art-source/mech-modular-topdown/sources/lowtech/variants/briefs.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
LOWTECH = HERE.parent
SHIPS = Path(r"C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core\graphics\ships")

PATH_HULLS = ["colossus/colossus2_pather.png", "manticore/manticore_pather.png", "lasher/lasher_pather.png", "brawler/brawler_pather.png",
              "buffalo/buffalo_luddic_path.png", "hound/hound_luddic_path.png"]


def row_reference():
    bodies = [Image.open(LOWTECH / "pirates" / f"{n}-master.png").convert("RGBA") for n in ("hound", "warden", "bulwark")]
    h = 900
    scaled = [b.resize((round(b.width * h / b.height), h), Image.LANCZOS) for b in bodies]
    sheet = Image.new("RGBA", (sum(s.width for s in scaled) + 80 * 4, h + 160), (0, 0, 0, 0))
    x = 80
    for s in scaled:
        sheet.alpha_composite(s, (x, 80))
        x += s.width + 80
    sheet.save(HERE / "ref-lowtech-row.png")


def path_material_reference():
    """Opaque squares cut from the middle of each hull, so the sheet carries surface but no ship outline."""
    tiles = []
    for rel in PATH_HULLS:
        img = Image.open(SHIPS / rel).convert("RGBA")
        a = img.getchannel("A")
        best = None
        side = max(32, int(min(img.width, img.height) / 2.6))
        for y in range(0, img.height - side, max(4, side // 4)):
            for x in range(0, img.width - side, max(4, side // 4)):
                box = (x, y, x + side, y + side)
                if a.crop(box).getextrema()[0] == 255:
                    detail = sum(Image.open(SHIPS / rel).convert("L").crop(box).getextrema())
                    if best is None or detail > best[0]:
                        best = (detail, box)
        if best:
            tiles.append(img.crop(best[1]).resize((300, 300), Image.LANCZOS))
    sheet = Image.new("RGBA", (3 * 320 + 20, ((len(tiles) + 2) // 3) * 320 + 20), (0, 0, 0, 0))
    for i, t in enumerate(tiles):
        sheet.alpha_composite(t, (20 + (i % 3) * 320, 20 + (i // 3) * 320))
    sheet.save(HERE / "ref-path-material.png")


COMMON = """
Strict 90-degree zenith orthographic top-down view, every machine facing the TOP edge of the sheet, each symmetric \
about its own vertical centre line unless the brief says one side is different. One wide landscape sheet, the three \
machines in one row left to right, LIGHT, MEDIUM, HEAVY, evenly spaced with clear empty space round each, drawn to \
one scale (the heavy clearly the biggest).

Every weapon mount is drawn EMPTY: a flat bolted mounting plate or a dark socket ring, with no gun, barrel, launcher \
or turret on it. Guns are separate parts added later. Tools that ARE the variant's job (a ram, a claw, a crane arm, a \
cutting head, a cargo cage) are part of the body and are drawn.

Image 1 is the frame identity reference: the three machines these variants are built from (left to right the light \
Hound, the medium Warden, the heavy Bulwark), seen from above. Each variant must still read as that machine: keep its \
proportions, its round hip bearings, its pilot canopy and its mount places, and change what the brief says on top.

True transparent background, including all negative space; no ground, shadow slab, cast shadow, halo, border, text, \
letters, numbers, logos or skulls. Crisp painted game-sprite concept art with rivets and weld seams, light from the \
upper left, not pixel art.

Avoid: guns or barrels of any kind; perspective, side view or isometric view; cropped machines; more or fewer than \
three machines; a starship's outline; legs drawn as bird or chicken legs; text.
"""

PIRATES = """Asset type: concept sheet for a top-down game: three PIRATE FIELD RIGS, captured Hegemony walking \
machines that a pirate crew has rebuilt for its own work, light, medium and heavy.

Pirates fight for loot, ambush and escape, not to hold ground. Each rig is reworked for one pirate job, visible in \
its outline at a glance:
LIGHT, the RUNNER (from the Hound): stripped for speed. Most of its side plate is torn off to show the frame and \
cables; a big scavenged freighter thruster pack is welded across its back, and a crude cow-catcher wedge of \
scrap bars juts from its nose. One empty mounting plate on the nose.
MEDIUM, the BOARDER (from the Warden): a breaching rig. One arm ends in a huge welded hydraulic grapple claw, the \
other keeps an empty mounting plate; a thick ramming shield of stacked scrap plate is bolted across its front, with \
a slot cut for the canopy. Its two shoulder mount places stay as empty plates.
HEAVY, the HAULER (from the Bulwark): a loot carrier. A cargo cage of welded bars sits on its back holding lashed \
crates and a canister; a folding salvage crane arm rides one shoulder, folded back along the body; the other \
shoulder keeps an empty socket ring. Extra scrap slabs hang off its flanks on chains.

Paint them as the pirate scrap bodies in image 1: mismatched salvaged panels (faded tan, grey, olive, blue-grey \
civilian panels, red-oxide primer, bare steel) welded on with visible beads and oversized bolts, crude hand-brushed \
dark red over old hazard paint, worn and grimy but legible.
""" + COMMON

PATH = """Asset type: concept sheet for a top-down game: three LUDDIC PATH CONVERSIONS, industrial and mining \
walking machines that a Pather cell has converted to war, light, medium and heavy.

The Path converts civilian and industrial tools because they are available and deniable; it attacks technology and \
demolishes. Each conversion shows the industrial machine it was and the job it now does:
LIGHT, the FOUNDRY BREAKER (from the Hound): a demolition walker. Its nose carries a heavy blunt breaching collar of \
foundry casting round an empty socket ring (its demolition cannon is added later); a rack of five fat shell \
canisters is strapped along its back; hazard-banded mining plate over its hips.
MEDIUM, the CUTTER (from the Warden): a mining-cutter conversion. One arm is a broad industrial cutting head, a \
wide flat disc-saw guard or plasma-cutter shroud with heat-blackened vents; the other arm keeps an empty mounting \
plate; fuel or gas tanks for the cutter ride its back in a welded frame.
HEAVY, the PILGRIM (from the Bulwark): a slow armoured mining hauler turned assault walker. A huge dozer-blade \
prow of scarred mining plate spans its front below the canopy; ore-hopper ribs run along its back; both shoulder \
places keep empty socket rings. Prayer cloth strips and hand-painted devotional marks (simple shapes, no text) hang \
from its plate.

Image 2 is material and colour reference only, a sheet of surface swatches cut from inside the Luddic Path's \
warships; it holds no shapes to copy. Paint the conversions in that Pather scheme: drab olive and yellow-green plate, \
pale cream panels, bold red stripe bands, dark grey machinery, grime and sooty scorching round vents, scuffed bare \
metal on worn edges, hand-welded repairs. Plainer and more uniform than pirate patchwork: one cell's workshop, not a scrapyard.
""" + COMMON


def main():
    row_reference()
    path_material_reference()
    (HERE / "sheets").mkdir(exist_ok=True)
    (HERE / "sheets" / "pirates.prompt.txt").write_bytes(PIRATES.encode("utf-8"))
    (HERE / "sheets" / "path.prompt.txt").write_bytes(PATH.encode("utf-8"))
    print("wrote two references and two briefs")


if __name__ == "__main__":
    main()
