"""Geometry only: one full-cell pressure hatch, horizontal bulkhead axis.

Run from the repository root with Pillow installed. ImageGen supplies final art.
The untouched square canvas is the placement frame; never trim its empty margins.
"""
from pathlib import Path
from PIL import Image, ImageDraw

image = Image.new("RGBA", (1024, 1024))
draw = ImageDraw.Draw(image)
draw.rectangle((0, 416, 1023, 607), fill="#454b49")
draw.rectangle((0, 416, 1023, 435), fill="#878b7f")
draw.rectangle((0, 588, 1023, 607), fill="#878b7f")
draw.rectangle((32, 444, 493, 579), fill="#666b60")
draw.rectangle((530, 444, 991, 579), fill="#666b60")
draw.rectangle((160, 496, 863, 527), fill="#9c9f8c")
for x in (96, 928):
    draw.rectangle((x - 28, 460, x + 28, 563), fill="#b1a360")
image.save(Path("art-source/templates/ship-sealed-hatch-guide.png"))
