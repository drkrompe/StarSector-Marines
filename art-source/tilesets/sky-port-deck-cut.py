"""Cuts the sky port deck and pad textures (ImageGen, 1254 px seamless squares,
sky-port-deck.raw.png and sky-port-pad.raw.png) into sky-port-deck.cut.png at
64 px a cell: the deck shrunk with Lanczos to 12x12 cells (a 4 m plate spans
three), the pad to 8x8, stacked deck above pad. The authoring document cuts it
one to one, row by row, into two blocks laid as patterns of those sizes.

Why shrink here: the exporter shrinks a cut with one bilinear pass, which
aliases fine plate detail; a single Lanczos pass over the whole picture keeps
it smooth and keeps neighbouring cells continuous.
"""
from pathlib import Path
from PIL import Image

here = Path(__file__).parent
PATTERNS = (('sky-port-deck.raw.png', 12), ('sky-port-pad.raw.png', 8))
sheet = Image.new('RGB', (12 * 64, 20 * 64))
top = 0
for name, cells in PATTERNS:
    plate = Image.open(here / name).convert('RGB').resize((cells * 64, cells * 64), Image.LANCZOS)
    sheet.paste(plate, (0, top))
    top += cells * 64
sheet.save(here / 'sky-port-deck.cut.png')
