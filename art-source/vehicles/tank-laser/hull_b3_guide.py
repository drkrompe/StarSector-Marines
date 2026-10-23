"""Guide for the third pass on hull option b: a bolder viewing port across the nose.

Writes hull-guide-b3.png: hull-raw-b.png with a wide chamfered dark-glass window band blocked in on
the front glacis, behind the nose's light strip, so the front reads at play scale.
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
img = Image.open(HERE / "hull-raw-b.png").convert("RGBA")
d = ImageDraw.Draw(img)
d.polygon([(126, 336), (176, 300), (176, 602), (126, 566)], fill="#2a2e33")     # graphite frame
d.polygon([(134, 344), (168, 318), (168, 584), (134, 558)], fill="#14212b")     # smoked glass
d.polygon([(140, 352), (150, 344), (150, 556), (140, 550)], fill="#3d6478")     # reflection
img.save(HERE / "hull-guide-b3.png")
