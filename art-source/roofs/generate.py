"""Paint one roof style's sheet over its guide (`make_guide.py`), one ImageGen pass, through `tools/codex_imagegen.py`.

  python art-source/roofs/generate.py <style> [--dry]

The brief is written to prompts/<style>.prompt.txt and the untouched result to raw/<style>.raw.png with its provenance.
Nothing is regenerated over an existing raw.
"""
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

LAYOUT = (
    "Use case: sketch-to-render, game tile sheet. Image 1 is the authoritative geometry guide and edit target. "
    "Reskin it into finished painted game art, preserving every shape, band, hole, beam and position, the exact "
    "1536x1024 canvas and all empty white space. Do not move, rescale, rotate, merge or add pieces. Everything is a "
    "building's ROOF seen from directly overhead. The sheet holds five separate pieces. "
    "PIECE 1, THE FIELD (x 0-767, y 128-895): ONE continuous seamless stretch of roof, two by two cells of 384 px with "
    "no gaps, borders or frames. Keep the guide's grain (courses, ribs, seams, plates) exactly where it is and evenly "
    "spaced, so the left edge continues into the right edge and the top into the bottom when tiled. Even, quiet "
    "weathering, no single large feature near an edge. "
    "PIECE 2, THE EAVE EDGE (x 800-1503, y 40-103): the roof's finished outer edge as a long straight strip, its outside "
    "at the top: the lit lip along the top, the edge band, a shaded foot along the bottom. Identical all along. "
    "PIECE 3, HOLED (x 800-1151, y 136-487): one cell of the same roof punched through by a shell: the near-black "
    "middle is an OPEN HOLE onto the dark room below; paint its ragged rim of broken, bent and splintered roofing, "
    "cracks and grit, scorching round the rim. "
    "PIECE 4, COLLAPSED (x 1168-1519, y 136-487): one cell where the roof has fallen in: the strip along the top is "
    "the roof still standing with a torn lower edge, below it a slab of roof sagging and tilted down, and broken joists "
    "hanging; the near-black is OPEN onto the dark room below. "
    "PIECE 5, BURNT THROUGH (x 800-1151, y 520-871): one cell burnt through by fire: charred, cracked rafters and a "
    "broken purlin over the open dark room, with a few blackened scraps of roofing left on them, ash, faint ember glow "
    "in the cracks. "
    "Keep every near-black void a flat, plain near-black. ")
CLOSE = (
    " Straight 90-degree overhead orthographic camera, top-down like a strategy-game map tile, no perspective. Sun from "
    "the upper left. Hand-painted gritty science-fiction game-tile look like Starsector, restrained palette, coarse "
    "readable detail that survives being shrunk to about 32 to 64 pixels per cell. No text, letters, numbers, labels, "
    "watermark, UI, people, plants or extra objects; no border; white everywhere outside the five pieces. Change only "
    "material, surface detail and subtle shading, never placement, size or silhouette.")

MATERIAL = {
    'clay': "Material: a colony town house's roof of sun-faded terracotta pantiles in courses over timber battens, "
            "warm red-orange with dusty ochre and moss-dark patches, a few cracked or replaced tiles; the edge a row "
            "of half-round ridge tiles bedded in pale mortar; the burnt rafters timber.",
    'slab': "Material: a civic or commercial building's flat roof: a poured concrete deck, warm pale grey, with "
            "tarred seams, scattered gravel, water stains, a drain and a small steel vent box; the edge a precast "
            "concrete coping lip; holes show snapped rebar; the burnt state scorched concrete over steel joists.",
    'corrugated': "Material: an industrial shed's roof of galvanised corrugated steel sheet, dull grey-green with rust "
                  "streaks at laps and rivets, a few replaced brighter sheets; the edge a folded steel flashing; holes "
                  "torn and curled back; the burnt state warped blackened steel purlins.",
    'plate': "Material: a fortified post's roof of riveted slate-grey armour plate with welded seams, a heavy hatch, "
             "scuffed olive primer at the edges; the edge a thick riveted armour band; holes punched craters in "
             "the plate with bent petals; the burnt state blackened steel I-beams.",
}


def main():
    style = sys.argv[1]
    dry = '--dry' in sys.argv
    prompt = HERE / 'prompts' / f'{style}.prompt.txt'
    raw = HERE / 'raw' / f'{style}.raw.png'
    prompt.parent.mkdir(parents=True, exist_ok=True)
    raw.parent.mkdir(parents=True, exist_ok=True)
    prompt.write_text(LAYOUT + MATERIAL[style] + CLOSE + '\n', encoding='utf-8')
    if raw.exists():
        print(f'{raw} exists; not regenerating')
        return
    cmd = [sys.executable, str(ROOT / 'tools/codex_imagegen.py'), '--prompt', str(prompt), '--out', str(raw),
           '--ref', f"{HERE / 'guides' / (style + '-guide.png')}=Geometry/layout authority and edit target"]
    print(' '.join(cmd))
    if not dry:
        subprocess.run(cmd, check=True, cwd=ROOT)


if __name__ == '__main__':
    main()
