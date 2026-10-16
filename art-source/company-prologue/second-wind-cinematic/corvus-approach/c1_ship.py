"""C1 Second Wind: the entrance and the run toward Jangala.

Owned by the ship pass. `build(add, duration)` adds Second Wind and its engine glows.
"""
from PIL import Image

from c1_core import BASE, glow_dot

ENTRANCE_NOZZLES = [(255, 815), (370, 815), (1005, 1005), (1085, 1025)]   # bell mouths, 2048x1152 render


def build(add, duration):
    # Second Wind's entrance: from off-screen bottom left, close and large, receding toward
    # Jangala. The rear three-quarter card keeps one viewing angle because the ship travels
    # away along roughly the camera's own line of sight.
    card = Image.open(BASE / "layers" / "second-wind-entrance.png").convert("RGBA")
    box = card.getchannel("A").getbbox()
    card = card.crop(box)
    base_w = 760.0
    k = base_w / card.width
    card = card.resize((round(base_w), round(card.height * k)), Image.LANCZOS)
    sw = add("second-wind", card, 0.6, [(0.0, -300, 1330, 1.6),
                                         (6.5, 470, 820, 0.78),
                                         (duration, 760, 700, 0.42)])
    for i, (nx, ny) in enumerate(ENTRANCE_NOZZLES):
        add("sw-engine-%d" % i, glow_dot(16, (150, 205, 255), 0.9), 0.6, None, additive=True,
            parent=sw, offset=((nx - box[0]) * k, (ny - box[1]) * k))
