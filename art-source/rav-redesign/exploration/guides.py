"""Concept-plate guides: each direction's strains drawn by its rig on a quiet
grey field, used as the geometry/layout authority for one ImageGen plate."""
import sys
from PIL import Image, ImageDraw
from rig import render
import chitin  # noqa: F401  (registers D1, D2)
from creatures import DIRECTIONS, st

W, H = 1672, 941


def guide(key):
    D = DIRECTIONS[key]
    img = Image.new("RGBA", (W, H), (128, 128, 124, 255))
    # runner at rest, runner mid-lash, brute, spitter, matriarch thumbnail
    slots = [("runner", None, 200, (2.2, 2.6), (0.5, 0.6), (20, 300)),
             ("runner", 0.5, 200, (2.2, 2.6), (0.5, 0.75), (360, 180)),
             ("brute", None, 190, (2.6, 2.6), (0.5, 0.5), (720, 30)),
             ("spitter", None, 220, (2.2, 2.2), (0.5, 0.5), (1190, 30)),
             ("matriarch", None, 95, (3.4, 3.4), (0.5, 0.5), (1000, 590))]
    for kind, s, ppc, size, ctr, pos in slots:
        im = render(D[kind](st(s=s, tau=0.3)), ppc, size, ctr, shadow=False)
        img.alpha_composite(im, pos)
    return img


if __name__ == "__main__":
    for k in sys.argv[2:]:
        guide(k).convert("RGB").save(f"{sys.argv[1]}/guide-{k}.png")
