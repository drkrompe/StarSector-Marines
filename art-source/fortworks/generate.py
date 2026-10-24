"""Paint one emplacement style's fortification works over the shared guide (`make_guide.py`), one ImageGen pass per
look, through `tools/codex_imagegen.py`.

  python art-source/fortworks/generate.py <look> [--dry]

A look is a key of LOOKS: the style it dresses, the material brief, and the style's turret plinth as the material
reference (so a lot's walls are the same stuff as its guns). The brief is written to prompts/<look>.prompt.txt and the
untouched result to raw/<look>.raw.png with its provenance. Nothing is regenerated over an existing raw.
"""
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
TURRETS = ROOT / 'mod/graphics/structures/ground-turrets'

LAYOUT = (
    "Use case: sketch-to-render, game sprite sheet. Image 1 is the authoritative geometry guide and edit target. Image 2 "
    "is the gun emplacement plinth of the same set: a MATERIAL AND STYLE REFERENCE ONLY. Match its materials, colours, "
    "painting finish and level of detail; do not copy its layout, its stair, its gun well or its shadow. Reskin the "
    "guide into finished painted game sprites, preserving every shape, band, tone relationship and position, the exact "
    "1536x1024 canvas and all empty space. Do not move, rescale, rotate, merge or add pieces. The sheet holds three "
    "separate groups. "
    "GROUP 1, THE PLATE (the 960x960 block at x 0-959, y 32-991): ONE continuous picture, five by five cells of 192px, "
    "with no gaps, gutters, cell borders, seams, frames or margin, painted in one pass. It is a square raised parapet "
    "seen from directly overhead, a closed square ring round a flat 3x3 gun platform (the 576x576 centre). The ring is "
    "the guide's band, about 88 pixels INSIDE the plate's outer edge: from the platform outward a narrow sloped inner "
    "face, a lighter flat top face, then a dark narrow outer face, exactly as the guide's bands run, mitred at the four "
    "corners. Outside the band, the outer 88 pixels of the plate all round are FLAT hard-standing of the same material "
    "as the platform inside: keep that flat margin, never extend the parapet to the plate's edge. The parapet's profile "
    "and texture must be identical along every side and into every corner so any single cell can be cut out and set "
    "against another of its kind with no visible seam. "
    "GROUP 2, THE BARRICADE RING (x 992-1519, y 32-559): a low free-standing barricade, a band about 72 pixels wide "
    "running round the OUTSIDE border of that square, with a sloped outer face, a flat top and a short inner face as "
    "the guide shades them. The middle of the square is EMPTY and fully transparent. Same build all the way round, "
    "so any 176-pixel cell of it can be cut out and joined to another. "
    "GROUP 3, THE WALL TILES (sixteen separate 84x84 squares in a 4x4 grid at x 1014-1385, y 642-1013): the flat top "
    "of a thick defensive wall seen from above, one tile per square. Each tile is a flat wall cap. A tile has a raised "
    "rim ONLY along the edges where the guide shows a lighter or darker strip; along every other edge the flat cap "
    "runs straight to the tile's edge with NO rim, bevel, border, shadow or darkening, because those edges join the "
    "next wall tile seamlessly. Several tiles have no rim at all and are a plain flat cap edge to edge. Keep each "
    "tile's strips exactly where the guide has them. ")
CLOSE = (
    " Straight 90-degree overhead orthographic camera, top-down like a strategy-game map sprite, no perspective tilt. "
    "Sun from the upper left: keep the guide's lit and shaded faces exactly where they are. Hand-painted gritty "
    "science-fiction game-sprite look like Starsector, restrained palette, coarse readable detail that survives being "
    "shrunk to about 32 to 64 pixels per cell. No text, no letters, no numbers, no labels, no watermark, no UI, no grass, "
    "no extra objects, no cast shadow outside the pieces, no border. True transparent background outside every piece and "
    "inside the barricade ring (if transparency is impossible, a flat pure white background). Change only material, "
    "surface detail and subtle shading, never placement, size or silhouette.")

# look -> (style key whose plinth is the reference, material brief)
LOOKS = {
    'derelict-colony-steel': ('derelict', (
        "Material and style: an abandoned colony fortification of industrial colony steel, long derelict: riveted dark "
        "gunmetal and galvanised plate laid over a steel frame, heavy rust blooms and streaks, flaking sun-faded "
        "yellow-and-black hazard striping along the parapet top and the wall rims, dented and buckled panels, grime and "
        "rain streaks, the barricade a run of rusted steel plate barriers bolted to posts.")),
    'derelict-ai-war': ('derelict', (
        "Material and style: an AI-war fortification abandoned for generations: massive old grey-brown concrete, "
        "pitted, cracked and spalled, with corroded steel armour facing plates bolted along the parapet top and the wall "
        "rims, exposed rusted rebar at chipped arrises, dark water staining, lichen and soot, faded military paint in "
        "patches; the barricade a line of old cast concrete barrier blocks, chipped and rust-stained.")),
    'derelict-pirate': ('derelict', (
        "Material and style: an old colony steel fortification squatted by pirates: rusted riveted plate and old hazard "
        "striping patched with mismatched scrap: welded-on hull plates in different colours, girder offcuts, sheet "
        "metal, oil-drum halves and tarpaulin over holes, crude weld seams and spray-painted marks without letters; the "
        "barricade a row of welded scrap plates, drums and cut girders.")),
    'brutalist': ('brutalist', (
        "Material and style: low-tech prepared works in Brutalist utilitarian concrete: board-formed poured concrete, "
        "pale warm grey, with form-tie holes, cast seams, weathering streaks and chipped arrises, the parapet and the "
        "wall caps massive and plain; the barricade a run of precast concrete barrier blocks with lifting slots. No "
        "paint, no hazard stripes, no steel plate.")),
    'earthwork': ('earthwork', (
        "Material and style: low-tech field works dug and filled by a garrison: tan sandbags in tidy courses, "
        "gabion baskets of wire mesh filled with stones, rammed earth and timber revetting; the parapet a sandbagged "
        "earth berm faced with planks, the hard-standing packed earth and gravel, the wall caps the tops of earth-"
        "filled gabion walls with sandbags along the exposed edges; the barricade a run of sandbags. No concrete, no "
        "paint.")),
    'modular': ('modular', (
        "Material and style: Persean League prepared works in League coalition standard: pressed modular steel panels "
        "in slate blue-grey with sealed seams, recessed strip lamps along the parapet top and the wall rims, small "
        "white unit chevrons, standard bolted access hatches, scuffed paint and clean repairs; the barricade a run of "
        "interlocking slate blue-grey modular barrier panels on feet.")),
    'barrier': ('barrier', (
        "Material and style: Persean League field works assembled on site: collapsible wire-mesh barrier bins lined "
        "with grey geotextile and filled with earth, in square cells, ratchet straps, a sun-faded blue tarp lashed over "
        "part of the parapet, stacked grey storage cases, slate blue-grey standard fittings; the wall caps the earth "
        "tops of two-high stacked mesh barrier bins; the barricade a run of single mesh barrier bins.")),
    'export': ('export', (
        "Material and style: Sindrian Diktat prepared works in export grade: sand-ochre ceramic-steel panels with "
        "rust-brown trim, crisp red unit bands along the parapet top and the wall rims, flush rivet lines, dust and "
        "sun-bleaching, clean military finish; the barricade a run of ochre ceramic-steel barrier panels with a red "
        "band.")),
    'blockwork': ('blockwork', (
        "Material and style: Sindrian Diktat field works of interlocking moulded barrier blocks: water-filled "
        "plastic and cast blocks in alternating orange-red and white, with moulded handholds and interlock lugs, "
        "dusty, scuffed and sun-faded, sand drifted against them; the parapet built of the same blocks on an ochre "
        "pad, the wall caps the tops of two-high block walls; the barricade a run of alternating orange-red and white "
        "blocks.")),
    'composite': ('composite', (
        "Material and style: Tri-Tachyon high-tech prepared works: seamless pearl-grey composite shells with a fine "
        "hexagonal ablative armour pattern, smooth rounded edges, thin glowing blue light lines along the parapet top "
        "and the wall rims, graphite trim, nothing exposed; scorching rather than rust. The barricade a run of smooth "
        "pearl composite barrier shells with a blue light line.")),
    'deployed': ('deployed', (
        "Material and style: Tri-Tachyon high-tech deployed kit: folding graphite pylons and pale composite deck pads "
        "set down in the field, joined by translucent pale blue hard-light barrier panes with glowing emitter edges, "
        "white and graphite casings with small cyan status lights; the parapet a ring of pylons and hard-light panes "
        "on a deck pad, the wall caps the tops of tall deployed barrier modules; the barricade a run of low pylons "
        "with hard-light panes between them.")),
}


def main():
    look = sys.argv[1]
    dry = '--dry' in sys.argv
    style, brief = LOOKS[look]
    prompt = HERE / 'prompts' / (look + '.prompt.txt')
    raw = HERE / 'raw' / (look + '.raw.png')
    prompt.parent.mkdir(exist_ok=True)
    raw.parent.mkdir(exist_ok=True)
    prompt.write_text(LAYOUT + brief + CLOSE + '\n', encoding='utf-8')
    if raw.exists():
        print('exists, not regenerating:', raw)
        return
    cmd = [sys.executable, str(ROOT / 'tools/codex_imagegen.py'), '--prompt', str(prompt), '--out', str(raw),
           '--ref', '%s=Geometry/layout authority and edit target: keep every piece where and how big it is'
           % (HERE / 'guides/works-guide.png'),
           '--ref', '%s=Material and style reference only; not layout' % (TURRETS / style / 'plinth.png')]
    print(' '.join(cmd))
    if not dry:
        subprocess.run(cmd, check=True, cwd=ROOT)


if __name__ == '__main__':
    main()
