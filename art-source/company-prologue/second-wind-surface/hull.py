"""Which hull a surfacing run works on: the harness's per-hull inputs.

The scripts in this folder are the surfacing harness. They were written for the
Second Wind and are shared by every cinematic hull. What differs per hull
lives in that hull's own surfacing folder (the Hound (A)'s is
`../hound-surface/`):
  surface_hull.py   the constants below (geometry build, material classes,
                    swatch layout, review views, wear response, ...)
  groups/<g>.py     the part tables (PARTS, SHEETS), one module per group
  palette.json, variation.json, wear.json, reference/, prompts/, generated/,
  guide/, part-list/, registration/, grading/, textures/, review/, build/
                    its data, written and read by the harness

Select the hull with `--hull <folder>` anywhere in a script's arguments (after
`--` for Blender) or the SURF_HULL environment variable. With neither, the hull
is this folder itself: the Second Wind, whose own surface_hull.py sits beside
the harness, so every Second Wind command works exactly as it did before.
"""
import importlib.util
import os
import sys
from pathlib import Path

HARNESS = Path(__file__).resolve().parent


def _locate():
    if '--hull' in sys.argv:
        p = Path(sys.argv[sys.argv.index('--hull') + 1])
    elif os.environ.get('SURF_HULL'):
        p = Path(os.environ['SURF_HULL'])
    else:
        return HARNESS
    return (p if p.is_absolute() else Path.cwd() / p).resolve()


DIR = _locate()
_spec = importlib.util.spec_from_file_location('surface_hull', DIR / 'surface_hull.py')
CONFIG = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(CONFIG)
if str(DIR) not in sys.path:
    sys.path.insert(0, str(DIR))  # its groups/ package


def get(name, default=None):
    return getattr(CONFIG, name, default)


NAME = CONFIG.NAME
BLEND = DIR / CONFIG.BLEND                      # the clean geometry build the parts are selected on
GROUPS = CONFIG.GROUPS
MATERIAL_CLASS = CONFIG.MATERIAL_CLASS          # base material name -> palette class
CLASS_ID = CONFIG.CLASS_ID                      # palette class -> flat id colour (plus 'other')
CLASSES = CONFIG.CLASSES                        # graded classes, in reading order
FAMILY = CONFIG.FAMILY                          # class -> paint family for the swap check
SWATCH_UNITS = CONFIG.SWATCH_UNITS              # hull units one swatch image spans
SWATCH_ORDER = CONFIG.SWATCH_ORDER              # classes painted as squares on the swatch sheet
SWATCH_DERIVED = get('SWATCH_DERIVED', [])      # (class, source square) regraded from another square
MEASURED_CLASSES = CONFIG.MEASURED_CLASSES      # classes read off the finish reference
REFERENCE_CAMERA = CONFIG.REFERENCE_CAMERA      # the build camera the finish reference was made from
derive_palette = get('derive_palette', lambda classes: {})
VARIATION_SPAN = CONFIG.VARIATION_SPAN
WEAR_RESPONSE = CONFIG.WEAR_RESPONSE            # class -> (paint, fade, metal-reveal)
ENGINES = CONFIG.ENGINES                        # drive bell centres, hull units (soot)
HULL_OBJECT = CONFIG.HULL_OBJECT                # the object whose space wear is placed in
REVIEW_WHOLE = CONFIG.REVIEW_WHOLE
REVIEW_CLOSE = CONFIG.REVIEW_CLOSE
REVIEW_EXTRA = get('REVIEW_EXTRA', {})          # name -> (azimuth, elevation, ortho scale, target)
REVIEW_PAGES = get('REVIEW_PAGES', [])
CAPTION = get('CAPTION', NAME)
SHEARED_FACES = get('SHEARED_FACES', False)  # bent per vertex after build: faces may be out of plane (parts.py)
# Per-face paint check (apply_skin.py): a claimed face whose tile pixels are mostly
# another paint family (a neighbour's paint at a registration edge) takes its
# class swatch instead. The share of declared-family paint a face needs; None is off.
FACE_FAMILY_MIN = get('FACE_FAMILY_MIN', None)
# Overlay hulls (the Paragon): the base material keeps its own procedural finish (its
# plating pattern and relief) and the graded skin or swatch only modulates it. Class ->
# strength: the texture divided by its class's palette mean, mixed toward 1 by
# (1 - strength), multiplies the base colour. None (every other hull): the texture
# replaces the base colour, as before.
OVERLAY = get('OVERLAY', None)

