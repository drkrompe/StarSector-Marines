"""Guide for the fourth pass on hull option b: the viewing window moved up onto the deck.

The owner read the third pass's window, at the nose edge, as sitting too low on the hull. This
puts it at the rear of the front glacis plate, where the glacis meets the upper deck just ahead
of the mount, like a cab windscreen. Writes hull-guide-b4.png from the clean hull-raw-b.png.
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
img = Image.open(HERE / "hull-raw-b.png").convert("RGBA")
d = ImageDraw.Draw(img)
d.polygon([(218, 330), (264, 306), (264, 596), (218, 572)], fill="#2a2e33")     # graphite frame
d.polygon([(226, 338), (256, 322), (256, 580), (226, 564)], fill="#14212b")     # smoked glass
d.polygon([(232, 346), (241, 341), (241, 562), (232, 557)], fill="#3d6478")     # reflection
img.save(HERE / "hull-guide-b4.png")
