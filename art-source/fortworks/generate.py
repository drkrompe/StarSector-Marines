"""Paint one emplacement style's fortification works over the shared guide (`make_guide.py`), one ImageGen pass per
look, through `tools/codex_imagegen.py`.

  python art-source/fortworks/generate.py <look> [--dry]
  python art-source/fortworks/generate.py <look> --state damaged|destroyed [--dry]

A look is a key of LOOKS: the style it dresses, the material brief, and the style's turret plinth as the material
reference (so a lot's walls are the same stuff as its guns). The brief is written to prompts/<look>.prompt.txt and the
untouched result to raw/<look>.raw.png with its provenance. Nothing is regenerated over an existing raw.

A damage state is an edit of the look's accepted intact raw, so every cell registers with the intact cell it replaces
(the exporter cuts both with the intact raw's measurements): raw/<look>-<state>.raw.png.
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


# The parts of a damage-state edit: what changes, given the look's own material brief.
STATE_OPEN = (
    "Use case: image edit of a finished sprite sheet. Image 1 is this set's INTACT sheet and the authoritative edit "
    "target: keep EVERYTHING about its geometry exactly where it is: the same 1536x1024 canvas, the same three groups "
    "at the same positions and sizes, the same empty transparent canvas round and between them and inside the "
    "barricade ring, so that every cell of this image can replace the same cell of image 1. Keep the same materials, "
    "colours, painting finish and north-west lighting as image 1. ")
STATES = {
    'damaged': (
        "Paint battle damage into the same pieces: on the parapet plate, chipped and spalled edges, dents and gouges, "
        "short dark cracks, scorch and soot blooms and bullet pitting, a few small loose fragments on the flat "
        "hard-standing; on the barricade ring, the same kind of hits, its pieces dented, holed and scorched; the wall "
        "tiles scuffed and pitted. Everything is still standing and complete: nothing missing, no gaps, every band "
        "still full width. Spread the damage so it looks alike on all four sides and the four corners, with no single "
        "large feature and no hard lines along cell boundaries. "),
    'destroyed': (
        "Paint the parapet and the barricade shattered by repeated hits: the parapet band broken into low jagged "
        "stumps and gaps down to the flat hard-standing, its debris and broken pieces lying where they fell, thick "
        "black scorch, blast pits on the flat hard-standing, the plate's outer edge still a clean edge; the barricade "
        "ring knocked down into low wreckage and spilled debris lying inside the band's own footprint, with gaps of "
        "transparent background where it is gone, nothing outside the band; the wall tiles cracked and scorched. Spread "
        "the wreck evenly so every side and corner reads alike, every cell's wreckage kept inside its own cell, no pile "
        "crossing a cell boundary. "),
}
STATE_CLOSE = (
    "Straight 90-degree overhead orthographic camera, no perspective. No fire, no smoke, no text, no letters, no "
    "labels, no watermark, no grid lines, no UI. Transparent background exactly where image 1 has it, plus the gaps "
    "described.")


# The gatehouse: the fort's gate towers and gate leaves, painted over the fort-buildings gatehouse guide in the look's
# material (`export_gatehouse.py` cuts them), with the look's accepted works sheet as the wall material reference.
GATEHOUSE = (
    "Use case: sketch-to-render, game sprite sheet. Image 1 is the authoritative geometry guide and edit target on a "
    "1536x1024 canvas: reskin it into finished painted game sprites, preserving every shape, position, size, "
    "registration and all transparent empty space exactly; do not move, rescale, rotate, merge, add or remove pieces. "
    "Image 2 is the same set's wall and barricade sheet and image 3 its gun emplacement plinth: MATERIAL AND STYLE "
    "REFERENCES ONLY, never layout. The gate must be built of the same stuff as those walls. "
    "The sheet is parts of a fortress GATEHOUSE seen from directly above, three panels in the first three quadrants "
    "of a 2x2 grid of 768x512 quadrants (the fourth stays empty), each a 2-wide by 3-tall portrait panel exactly where "
    "the guide places it. Top-left: a gate-tower roof, a hardened cap with chamfered corners and a raised inner cap, "
    "narrow dark embrasure slits along the left flank and on both the top and bottom ends, and a small round roof "
    "hatch. Top-right: a heavy GATE: the passage between them stays open and transparent, and two thick gate leaves "
    "stand folded back against the jambs along the top and bottom edges, with big hinge pins and a thin worn threshold "
    "sill line across the middle. Bottom-left: the same tower roof DAMAGED: cracked, scorched, one embrasure blown "
    "wide. Same materials and registration across panels. ")
GATEHOUSE_CLOSE = (
    " Straight 90-degree overhead orthographic camera: no perspective, no visible vertical faces, no cast or drop "
    "shadow, no ground outside the guide shapes, no labels, text, watermark, grid lines or UI marks. Genuine "
    "transparent background everywhere the guide is empty. Coarse readable detail that survives being shrunk to about "
    "40 pixels per cell; hand-painted Starsector-like finish, light from the upper left.")


def main():
    look = sys.argv[1]
    dry = '--dry' in sys.argv
    state = sys.argv[sys.argv.index('--state') + 1] if '--state' in sys.argv else None
    gatehouse = '--gatehouse' in sys.argv
    style, brief = LOOKS[look]
    name = ('gatehouse-' + look) if gatehouse else look if state is None else look + '-' + state
    prompt = HERE / 'prompts' / (name + '.prompt.txt')
    raw = HERE / 'raw' / (name + '.raw.png')
    prompt.parent.mkdir(exist_ok=True)
    raw.parent.mkdir(exist_ok=True)
    if gatehouse:
        prompt.write_text(GATEHOUSE + brief + GATEHOUSE_CLOSE + '\n', encoding='utf-8')
        refs = ['%s=Geometry authority and edit target: keep every panel where and how big it is'
                % (ROOT / 'art-source/templates/fort-buildings-gatehouse-guide.png'),
                '%s=Wall material reference only; not layout' % (HERE / 'raw' / (look + '.raw.png')),
                '%s=Material and style reference only; not layout' % (TURRETS / style / 'plinth.png')]
    elif state is None:
        prompt.write_text(LAYOUT + brief + CLOSE + '\n', encoding='utf-8')
        refs = ['%s=Geometry/layout authority and edit target: keep every piece where and how big it is'
                % (HERE / 'guides/works-guide.png'),
                '%s=Material and style reference only; not layout' % (TURRETS / style / 'plinth.png')]
    else:
        prompt.write_text(STATE_OPEN + STATES[state] + brief + ' ' + STATE_CLOSE + '\n', encoding='utf-8')
        refs = ['%s=The intact sheet: geometry authority and edit target; change only the surface'
                % (HERE / 'raw' / (look + '.raw.png'))]
    if raw.exists():
        print('exists, not regenerating:', raw)
        return
    cmd = [sys.executable, str(ROOT / 'tools/codex_imagegen.py'), '--prompt', str(prompt), '--out', str(raw)]
    for ref in refs:
        cmd += ['--ref', ref]
    print(' '.join(cmd))
    if not dry:
        subprocess.run(cmd, check=True, cwd=ROOT)

if __name__ == '__main__':
    main()
