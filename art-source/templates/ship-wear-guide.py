"""Placement guide only; ImageGen supplies finished transparent deck-wear art."""
from PIL import Image, ImageDraw
image = Image.new('RGBA', (1024, 1024))
d = ImageDraw.Draw(image)
for i in range(8):
    x = 65 + i * 43
    d.line((x, 190 + i % 3 * 18, x + 55, 305 + i % 2 * 16), fill='#70695b', width=5)
d.polygon([(607,170),(718,138),(879,206),(917,320),(758,361),(628,295)],fill='#555046')
d.polygon([(90,635),(340,612),(421,659),(408,901),(117,918),(72,788)],fill='#535a55')
d.line((90,635,340,612,421,659,408,901,117,918,72,788,90,635),fill='#88857b',width=12)
d.ellipse((630,667,905,855),fill='#393633')
for i in range(5):
    d.line((664+i*31,703,613+i*32,621),fill='#393633',width=8)
image.save('art-source/templates/ship-wear-guide.png')
