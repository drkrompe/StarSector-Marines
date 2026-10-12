"""Geometry only: one full-cell pressure hatch, horizontal bulkhead axis.

Run from the repository root with Pillow installed. ImageGen supplies final art.
The untouched square canvas is the placement frame; never trim its empty margins.
"""
from pathlib import Path
from PIL import Image, ImageDraw

image = Image.new("RGBA", (1024, 1024))
draw = ImageDraw.Draw(image)
draw.rectangle((0, 224, 1023, 799), fill="#454b49")
draw.rectangle((0, 224, 1023, 287), fill="#878b7f")
draw.rectangle((0, 736, 1023, 799), fill="#878b7f")
draw.rectangle((32, 312, 493, 711), fill="#666b60")
draw.rectangle((530, 312, 991, 711), fill="#666b60")
draw.rectangle((160, 465, 863, 558), fill="#9c9f8c")
for x in (96, 928):
    draw.rectangle((x - 28, 360, x + 28, 655), fill="#b1a360")
image.save(Path("art-source/templates/ship-sealed-hatch-guide.png"))
