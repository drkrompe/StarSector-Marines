"""Join an accepted raw sheet and a later generation side by side into one raw, without touching a pixel.

Used where only some pieces of a kit were re-drawn at real size: the pieces that were already true keep their
original generation (left), the re-drawn ones come from the new generation (right), and the authoring document cuts
each from its own half. usage: real-scale-montage.py <left.png> <right.png> <out.png>
"""
import sys
from PIL import Image

left, right = (Image.open(p).convert("RGBA") for p in sys.argv[1:3])
out = Image.new("RGBA", (left.width + right.width, max(left.height, right.height)), (0, 0, 0, 0))
out.paste(left, (0, 0))
out.paste(right, (left.width, 0))
out.save(sys.argv[3])
print(sys.argv[3], out.size, "right half from x =", left.width)
