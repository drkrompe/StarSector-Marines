"""Paint one fallen-hull sheet over its guide (`make_guide.py`), one ImageGen pass, through `tools/codex_imagegen.py`.

  python art-source/fallen-hull/generate.py <job> [--dry]

Jobs: hull-survey, hull-carrier, hull-contingency (pick `hull-lineage`), forge, stake, debris, trench, stripper,
hauler, rig (pick `stripper-hauler` b). The brief is written to prompts/<job>.prompt.txt and the untouched result to
raw/<job>.png with its provenance. Nothing is regenerated over an existing raw.

The style references are vanilla sprites from the game install, upscaled with Lanczos into build/fallen-hull-refs/
(never tracked), and the project's own accepted derelict art.
"""
import subprocess
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "art-source"))
from starsector_path import GAME_CORE  # noqa: E402

REFS = ROOT / "build" / "fallen-hull-refs"
VANILLA = {
    "survey_mothership_battle": ("graphics/ships/derelict/survey_mothership_battle.png", 2),
    "derelict_rampart": ("graphics/ships/derelict/derelict_rampart.png", 4),
    "derelict_bastillon": ("graphics/ships/derelict/derelict_bastillon.png", 4),
    "derelict_guardian": ("graphics/ships/derelict/derelict_guardian.png", 3),
}

STYLE = (
    "Style and material: Starsector's own derelict Explorarium and Domain drone ships, as in the material references: "
    "kitbashed, densely greebled machinery read from directly above; pale sage-green and khaki armour plates with a "
    "few broad diagonal yellow hazard stripes, bone-white and tarnished brass segments, pipework and cable runs, "
    "rust-brown machinery and grime in the recesses, small red sensor lamps, mottled weathering on every plate, crisp "
    "hard edges, painted as a game sprite lit from the upper left. Never smooth, clean, glossy or toy-like. ")
NO_TEXT = ("Absolutely no text, letters, numbers, serial numbers, stencils, logos, emblems, insignia or symbols "
           "anywhere. ")
CAMERA = ("Strict 90-degree overhead orthographic view, no perspective tilt, no visible side walls, lit from the upper "
          "left. Keep each drawing exactly where the guide puts it, at exactly its size, position and outline; never "
          "move, rotate, rescale or recompose anything, and add nothing between the pieces. ")
ALPHA = ("True transparent background with genuine alpha outside the silhouettes, including inside holes the guide "
         "leaves transparent; no ground, floor, cast-shadow slab, labels, text, border, grid or checkerboard. Same 3:2 "
         "landscape canvas as the guide.")
GUIDE_KEY = ("In the guide pale grey is armour plate, mid grey is raised housings, seams and panels, near-black is "
             "vents, sockets, recesses and open voids (keep a void a dark open hole into the machine or the hull "
             "below), red dots are sensor lamps, orange is molten metal glowing, yellow bars are hazard striping. ")

HULL_SUBJECT = (
    "Paint image 1, a flat-colour geometry guide, as one finished top-down game sprite: a centuries-old starship "
    "about 1.2 kilometres... no: a capital-scale starship hull lying wrecked on a planet's surface after crashing, "
    "seen straight down from above, bow to the right (east). It is a ground-battle set piece: soldiers and walkers "
    "fight round it and inside it, so it reads at the scale of buildings. On the left, the snapped-off stern "
    "section (engine block with three engine bells) lies apart from the body, its torn edge facing the body's torn "
    "stern edge, a few torn plates thrown between them. The body keeps the guide's layout: a long armoured dorsal "
    "spine, a raised stack block over its middle third (the forge hall lies beneath it, vents and stacks venting), "
    "radiator fin banks along both flanks, flank bays, a bow module with a sensor blister and two forward launch "
    "bays (the port bay blown open into the dark interior), a sensor mast snapped and lying flat along the "
    "starboard side, a ragged breach torn through the dorsal plating near the bow. Crash damage everywhere: buckled "
    "and torn plating, scorch along the belly edges, dust and soil drifted against the flanks, but the hull is "
    "whole enough to be roofed over its interior. ")
LINEAGE = {
    "hull-survey": (
        "LINEAGE: an Explorarium SURVEY MOTHERSHIP, built to explore and prepare worlds for colonists, carrying "
        "autoforges. Match image 2's hull material and palette closely (a vanilla survey mothership): pale "
        "teal-green and sage plate in large stepped slabs, khaki and bone segments, rust-brown machinery blocks "
        "between the plates, tarnished brass, a few broad hazard-yellow diagonals; dense kitbashed machinery along "
        "the flanks, smooth broad plate on the spine. Never its emblem. ",
        ["survey_mothership_battle", "derelict_rampart"],
        ["Hull material, palette and greeble style (vanilla survey mothership, 2x Lanczos); not layout, shape or "
         "emblem", "Material and weathering style (vanilla derelict_rampart, 4x Lanczos); not layout or subject"]),
    "hull-carrier": (
        "LINEAGE: a Domain-era military DRONE CARRIER, a frontier garrison's warship that launched war drones. A "
        "harder military read than a survey ship: darker olive-khaki and gunmetal plate, heavy armour belts along "
        "the flanks, the flank bays painted as rows of drone launch tubes and armoured hangar doors, hazard-yellow "
        "chevrons at every launch bay, red lamps, burnt and pitted armour; the stack block an armoured flight-control "
        "citadel. Same derelict family as images 2 and 3, darker and more martial. ",
        ["derelict_bastillon", "derelict_guardian"],
        ["Material, palette and greeble style (vanilla derelict_bastillon, 4x Lanczos); not layout or subject",
         "Material, palette and greeble style (vanilla derelict_guardian, 3x Lanczos); not layout or subject"]),
    "hull-contingency": (
        "LINEAGE: a Tri-Tachyon CONTINGENCY HULL, a corporate black-site ship whose automated defences still run an "
        "executive contingency order. Tri-Tachyon high-tech: seamless composite shells and sealed panels in "
        "corporate white and pale grey with graphite seams, GONE DULL with age (yellowed, dust-greyed, scorched, "
        "cracked panels), thin pale grey-blue trim instead of hazard yellow, lamps a weak glowing pale cyan where "
        "the guide has red; the machinery in the open bays and breaches dark gunmetal and dull brass. Image 2 shows "
        "this livery on the same derelict network's structures (bottom row): livery, palette and wear only. ",
        ["contingency-livery"],
        ["Livery, palette and wear reference only (bottom row: contingency security's structures); not layout, "
         "shape or subject"]),
}

JOBS = {
    "forge": (
        "Paint image 1, a flat-colour geometry guide, as a sheet of six finished top-down game sprites: three "
        "alternative designs (the columns) of the AUTOFORGE of a crashed Explorarium survey mothership, the "
        "self-replicating fabricator in the hull's forge hall that prints survey stakes and walking war machines "
        "from scrap metal. About twelve metres across, a heavy industrial machine bolted into the deck. Top row: "
        "working, its core glowing with molten metal, heat haze, hot orange light spilling on its housing. Bottom "
        "row: the same three put out, cold and dark, its core a dead black pit, a breach blown through its top "
        "plate, cracked and scorched, cooled slag. Keep each bottom drawing the same machine as the one above it.\n"
        "- column 1, CRUCIBLE: a square press housing, a round crucible mouth under a press head, feed hoppers at two "
        "corners, a print bed along the bottom.\n"
        "- column 2, GANTRY: a gantry printer, two heavy rails along a long build bed with a half-printed walker "
        "frame on it, a bridge carrying the print head across.\n"
        "- column 3, RING: a ring of eight forming heads round a deep forge pit, a feed conveyor running in from "
        "the south.\n" + STYLE + NO_TEXT + GUIDE_KEY + CAMERA + ALPHA,
        ["node-domain", "walker-sentinel", "derelict_rampart"],
        ["Family reference: an accepted structure of the same derelict network (material, palette, detail scale "
         "only; not layout or subject)",
         "Family reference: the accepted derelict walker (material and detail only; not layout or subject)",
         "Material and weathering style (vanilla derelict_rampart, 4x Lanczos); not layout or subject"]),
    "stake": (
        "Paint image 1, a flat-colour geometry guide, as a sheet of six finished top-down game sprites: two "
        "alternative designs (the rows) of a SURVEY STAKE, a prefabricated automated outpost that a crashed survey "
        "mothership prints and plants in the ground to claim it; three stages of it (the columns). Armoured, "
        "unarmed machinery of the derelict security network, same family as image 2.\n"
        "- column 1, PLANTED: the stake just planted, folded shut: an armoured pylon driven into the ground, its "
        "panels folded tight, a few hazard marks, unlit. Row 1 SPIRE: a square spike with four folded petals. Row 2 "
        "TRIPOD: a round core with three legs folded against it.\n"
        "- column 2, RAISED: the same pylon opened out: its petals or legs splayed and anchored into the ground, a "
        "control node mast risen in the middle with a red sensor lamp, and a round drone cradle (a launch well) "
        "beside it.\n"
        "- column 3, STORE: the established stake's feedstock store: an armoured open hopper heaped with stripped "
        "scrap metal (torn plate, girders, wreck parts), a chute on one side.\n"
        + STYLE + NO_TEXT + GUIDE_KEY + CAMERA + ALPHA,
        ["node-domain", "post-domain", "derelict_rampart"],
        ["Family reference: the same network's control node (material, palette, detail scale; not layout)",
         "Family reference: the same network's pop-up post (material, palette, detail scale; not layout)",
         "Material and weathering style (vanilla derelict_rampart, 4x Lanczos); not layout or subject"]),
    "debris": (
        "Paint image 1, a flat-colour geometry guide, as a sheet of eleven finished top-down game sprites: the "
        "debris field of a crashed capital starship and loose sections of its hull plating, lying on the ground of "
        "a battle map. Top row, left to right: a square section of hull plating with four torn edges; the same with "
        "a vent block and a hatch; a wider section with a torn breach into the dark below; a long strip of hull "
        "edge whose top side is torn and buckled; two torn plates lying loose. Second row: a snapped structural "
        "girder; a torn-off equipment pod; an engine bell on its side, its mouth to the left; a snapped sensor mast "
        "with a dish at its top; a heap of smaller scrap. All crash-damaged: buckled, torn, scorched, dusty. "
        + STYLE + NO_TEXT + GUIDE_KEY + CAMERA + ALPHA,
        ["survey_mothership_battle", "derelict_rampart"],
        ["Hull material, palette and greeble style (vanilla survey mothership, 2x Lanczos); not layout or emblem",
         "Material and weathering style (vanilla derelict_rampart, 4x Lanczos); not layout or subject"]),
    "trench": (
        "Use case: sketch-to-render, game ground tiles. Image 1 is the authoritative geometry guide and edit target. "
        "Reskin it into finished painted ground, preserving every shape and position and the exact canvas; keep "
        "everything outside the pieces transparent. This is the IMPACT TRENCH a crashed starship ploughed through a "
        "world's ground, seen from directly overhead. Four pieces. PIECE 1, the field (x 0-383, y 128-511): ONE "
        "continuous seamless stretch of the trench floor, ploughed and scorched earth and crushed rock, furrows "
        "running left to right exactly where the guide has them, so the left edge continues into the right and the "
        "top into the bottom when tiled; even, quiet, no single large feature near an edge. PIECE 2, the lip "
        "(x 440-1495, y 40-135): the trench's thrown-up berm as a long straight strip, identical all along: the "
        "untouched field at the top edge, a crest of heaped, broken earth and torn sod along the middle, falling "
        "away into scorched shadow at the bottom (the trench side). PIECES 3-5, loose ground patches with soft "
        "irregular feathered edges that blend into any ground: a scorch splash (black-brown, burnt ash, a darker "
        "core); a long gouge furrow (three parallel scored furrows with thrown earth); a glassed patch (fused, "
        "cracked dark grey-green glass from re-entry heat in a burnt rim). Restrained earthy palette: dun, umber, "
        "ash, soot, a little rust; no vegetation, no metal debris, no objects. Straight 90-degree overhead "
        "orthographic camera, top-down like a strategy-game map tile, sun from the upper left. Hand-painted gritty "
        "science-fiction game-tile look like Starsector, coarse readable detail that survives being shrunk to 32 to "
        "64 pixels per cell. " + NO_TEXT + "True transparent background outside the pieces; no border.",
        [], []),
}

WALKER = {
    "stripper": (
        "DESIGN: a STRIPPER, an unarmed salvage walker that cuts up buildings, fences, vehicles and wrecks for "
        "scrap: a low, long, light-plated body with a small rounded sensor head between two heavy cutting arms that "
        "reach forward, each ending in a big circular cutting saw disc with a guard; a two-section armoured back and "
        "a coolant pack behind. Legs a little lighter than the guide's sentinel. Smaller than the sentinel: a "
        "working machine, not a warrior. No guns. "),
    "hauler": (
        "DESIGN: a HAULER, an unarmed load-carrying walker: a broad, heavy body whose back is one deep open load "
        "bed (a hopper with plated sides) carrying stripped scrap metal (torn plates, girder ends, wreck parts), a "
        "small rounded sensor head forward of it, a power pack behind. Thick, planted legs built to carry weight. "
        "No guns. "),
    "rig": (
        "DESIGN: a STRIPPER-HAULER in one: an unarmed salvage walker that both cuts and carries: two heavy "
        "cutting arms forward ending in circular saw discs, a small rounded sensor head between them, a broad body "
        "whose back is an open load bed (a hopper, empty here, its floor plating visible), a power pack behind. "
        "Thick legs. No guns. "),
}
WALKER_FRAME = (
    "Game sprite sheet for a top-down tactical game: an unpiloted Domain-era derelict WALKER of the same machine "
    "line as reference image 2 (the accepted security walker, the 'sentinel'): it must read as the same family and "
    "maker, its own build at its own size, never a recolour or copy of it. No cockpit, canopy or pilot.\n\n"
    "Reference image 1 is the GEOMETRY AUTHORITY (a grey block-in). Keep its layout exactly: on the LEFT the whole "
    "walker assembled, the torso in the middle and four legs reaching out in an X; on the RIGHT the same machine's "
    "parts drawn apart, upright: the torso alone (top), one thigh segment (bottom left: hip joint at its lower end, "
    "knee end at its top) and one shin segment (bottom right: knee joint at its lower end, a heavy three-toed clamp "
    "foot at its top). Keep every part's position, size, outline and orientation as in the guide, so the parts can "
    "be cut out by their boxes; keep the gaps between parts empty. The torso on the right must be the same design "
    "as on the left, and every leg on the left uses the parts on the right.\n\n")
WALKER_CLOSE = (
    "\n\nSURFACE: like image 2 and the vanilla derelict ships (image 3): never smooth, clean, rounded or toy-like. "
    "Many small overlapping armour plates with hard bevelled edges and dark seams, exposed machinery, pipes, cable "
    "bundles and pistons between plates, rivet rows, vents, dents, scrapes, chipped paint, grime in every recess; "
    "dense detail that still reads at sprite scale, crisp edges. COLOUR: sage-green and khaki plate, yellow "
    "diagonal hazard bands, bone-white and brass machinery, rust-brown grime, red sensor lamps; the saw discs and "
    "cutting edges bare worn steel. Muted, old and gritty. " + NO_TEXT + "\n\nStrict overhead orthographic view, "
    "the camera straight down, the machine facing the TOP of the canvas. Plain flat light grey background (#b4b4b4) "
    "everywhere around and between the parts; no ground, no cast shadow, no shadow slab, no border, no labels.")

PROJECT_REFS = {
    "node-domain": ROOT / "mod/graphics/battle/derelict/node-domain.png",
    "post-domain": ROOT / "art-source/derelict-security/post-v2b-raw.png",
    "walker-sentinel": ROOT / "art-source/derelict-walker/raw/walker-sentinel-v3.png",
    "contingency-livery": ROOT / "art-source/derelict-security/node-v2b-raw.png",
}


def ref(name):
    if name in PROJECT_REFS:
        return PROJECT_REFS[name]
    REFS.mkdir(parents=True, exist_ok=True)
    out = REFS / f"{name}.png"
    if not out.exists():
        rel, k = VANILLA[name]
        img = Image.open(GAME_CORE / rel).convert("RGBA")
        img.resize((img.width * k, img.height * k), Image.LANCZOS).save(out)
    return out


def job(name):
    """(guide, brief, [(reference path, role)])"""
    if name in LINEAGE:
        text, refs, roles = LINEAGE[name]
        brief = HULL_SUBJECT + text + STYLE.replace("Style and material: ", "Rendering: ", 1) + NO_TEXT + GUIDE_KEY \
            + CAMERA + ALPHA
        return "hull", brief, list(zip(map(ref, refs), roles))
    if name in WALKER:
        brief = WALKER_FRAME + WALKER[name] + WALKER_CLOSE
        refs = [(ref("walker-sentinel"), "Family and identity: the accepted derelict walker of this line (its "
                 "material, plating, colour, detail and part language); not this machine's layout or design"),
                (ref("derelict_rampart"), "Material and rendering style only (vanilla derelict_rampart, 4x Lanczos); "
                 "not layout or subject")]
        return name, brief, refs
    brief, refs, roles = JOBS[name]
    return name, brief, list(zip(map(ref, refs), roles))


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry" in sys.argv
    name = args[0]
    guide, brief, refs = job(name)
    (HERE / "prompts").mkdir(exist_ok=True)
    (HERE / "raw").mkdir(exist_ok=True)
    prompt = HERE / "prompts" / f"{name}.prompt.txt"
    prompt.write_text(brief, encoding="utf-8")
    out = HERE / "raw" / f"{name}.png"
    if out.exists():
        raise SystemExit(f"{out} exists; not regenerating")
    cmd = [sys.executable, str(ROOT / "tools" / "codex_imagegen.py"), "--prompt", str(prompt), "--out", str(out),
           "--ref", f"{HERE / 'guides' / (guide + '-guide.png')}=Geometry authority: exact silhouettes, layout, "
                    "placement and scale; flat colours are placeholders, not material"]
    for path, role in refs:
        cmd += ["--ref", f"{path}={role}"]
    print(" ".join(cmd))
    if not dry:
        subprocess.run(cmd, check=True, cwd=ROOT)


if __name__ == "__main__":
    main()
