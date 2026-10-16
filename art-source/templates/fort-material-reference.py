"""Crops the fort's own paving and wall art out of a production render, as the material and lighting reference for the
concrete emplacement sheets. Usage: fort-material-reference.py <render-parity fort-aa-mount-close.png> (1920x1080, 20 cells
across = 96 px/cell, rescaled here to the 128 px/cell the emplacement art is cut for). Output: fort-material-reference.png.
Left: the fort wall (dark grey fill with a pale concrete lip, light from the NW) against the warm courtyard paving.
Right: the cool grey-blue compound floor tile."""
import sys
from pathlib import Path
from PIL import Image

src = Image.open(sys.argv[1]).convert('RGB')
k = 128 / 96


def crop(box):
    c = src.crop(box)
    return c.resize((round(c.width * k), round(c.height * k)), Image.LANCZOS)


wall = crop((1200, 40, 1580, 520))      # wall steps + courtyard paving
floor = crop((20, 222, 720, 312))       # compound floor tile (cracked, cool grey)
sheet = Image.new('RGB', (wall.width + floor.width + 24, max(wall.height, floor.height)), (0, 0, 0))
sheet.paste(wall, (0, 0))
sheet.paste(floor, (wall.width + 24, 0))
sheet.save(Path(__file__).resolve().parent / 'fort-material-reference.png')

