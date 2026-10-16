"""Write guide-live.png / guide-wreck.png: registered hull frame at 2x with the
blade geometry blocked in as flat grey under it (the hull covers the arm roots)."""
from PIL import Image
from hulls import hull_frame, HERE
import blade_geometry as bg

S = 2
for index, name in ((0, 'guide-live.png'), (2, 'guide-wreck.png')):
    hull = hull_frame(index).resize((768 * S, 768 * S), Image.LANCZOS)
    g = bg.draw(S, wreck=index == 2)
    g.alpha_composite(hull)
    g.save(HERE / name)
