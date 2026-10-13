"""Lays the two bolder wire frames from fort-wire.raw.png over the original earthworks sheet.

fort-earthworks.base.raw.png is the untouched earthworks generation (its wire frames are the thin v1 coil);
fort-wire.raw.png is the geometry-first reskin. Only the two 192px frames at (0, 576) and (192, 576) are replaced;
the result is fort-earthworks.raw.png, the sheet the tileset document cuts from.
"""
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
base = Image.open(HERE / 'fort-earthworks.base.raw.png').convert('RGBA')
wire = Image.open(HERE / 'fort-wire.raw.png').convert('RGBA')
box = (0, 576, 384, 768)
base.paste(wire.crop(box), box[:2])  # paste replaces, including alpha
base.save(HERE / 'fort-earthworks.raw.png')
