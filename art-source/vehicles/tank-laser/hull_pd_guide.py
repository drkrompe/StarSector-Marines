"""Geometry guide for the PD-laser variant's hull: hull-raw-b4 with the capacitor pack's footprint
blocked in as a fire-control radar dish over a short cell bank. Writes hull-guide-pd.png."""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
img = Image.open(HERE / "hull-raw-b4.png").convert("RGBA")
d = ImageDraw.Draw(img)
# The capacitor pack's housing (x 700-905, y 292-605) becomes the radar bay over a short bank.
d.rectangle((700, 292, 905, 605), fill=(70, 72, 76, 255))
d.ellipse((712, 300, 893, 481), fill=(150, 152, 156, 255), outline=(40, 40, 44, 255), width=6)
d.ellipse((782, 370, 823, 411), fill=(60, 60, 64, 255))
d.rectangle((722, 506, 883, 592), fill=(48, 48, 52, 255))
for cx in (762, 802, 842):
    d.ellipse((cx - 16, 533, cx + 16, 565), fill=(200, 40, 30, 255))
img.save(HERE / "hull-guide-pd.png")
