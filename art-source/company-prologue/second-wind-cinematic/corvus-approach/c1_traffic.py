"""C1 traffic: the Hegemony flotilla, hero warship, Jangala Station and the freighter lanes.

Owned by the traffic pass. `build(add, duration, stand_ins)` adds Elements through
`add(...)` (same signature as `c1_core.Element`), back to front, and returns nothing.
"""
from PIL import Image

from c1_core import BASE, cutout, glow_dot, latest, sprite

FLOTILLA_WINDOW = (247, 148, 576, 324)   # frame x, y, w, h shared by both fleet guides


def build(add, duration, stand_ins):
    # The battlegroup: two silhouette-guided layers (flotilla_guides.py) registered to one
    # frame window, every bow on the same heading toward the inbound lane.
    flotilla = None
    for stem, depth in (("heg-flotilla-far", 0.07), ("heg-flotilla-mid", 0.09)):
        path = BASE / "assets" / (stem + "-v2-registered.png")
        path = path if path.exists() else latest(stem)
        if path:
            layer = Image.open(path).convert("RGBA").resize(FLOTILLA_WINDOW[2:], Image.LANCZOS)
            layer.putalpha(layer.getchannel("A").point(lambda a: 0 if a <= 8 else a))
            cx = FLOTILLA_WINDOW[0] + FLOTILLA_WINDOW[2] / 2
            cy = FLOTILLA_WINDOW[1] + FLOTILLA_WINDOW[3] / 2
            flotilla = add(stem, layer, depth, [(0, cx, cy, 1.0)], vx=-0.8, vy=0.2)

    # Jangala Station just off the planet's upper-left limb, small at this distance.
    station = latest("station")
    img = cutout(station, 82) if station else sprite("stations/station_jangala.png", 128, 0, haze=0.15)
    st = add("station", img, 0.06, [(0, 905, 300, 1.0)], vx=0.4)
    for i, (ox, oy) in enumerate([(img.width * 0.45, img.height * 0.2), (img.width * 0.55, img.height * 0.8)]):
        add("station-light-%d" % i, glow_dot(1.6, (255, 235, 200)), 0.06, None, additive=True,
            blink=(1.7, i * 0.6), parent=st, offset=(ox, oy))

    hero = latest("heg-hero")
    if hero:
        add("heg-hero", cutout(hero, 240), 0.2, [(0, 1560, 74, 1.0)], vx=-11.0, vy=0.2)
    if not flotilla and stand_ins:
        for i, (rel, h, x, y) in enumerate([("ships/onslaught/onslaught_hegemony.png", 110, 560, 300),
                                            ("ships/dominator/dominator_hegemony.png", 64, 460, 380),
                                            ("ships/eagle/eagle_hegemony.png", 46, 650, 240)]):
            add("heg-%d" % i, sprite(rel, h, heading=140.0, haze=0.3, dim=0.92), 0.08, [(0, x, y, 1.0)],
                vx=-0.8, vy=0.2)

    # Traffic toward the planet: the hero Atlas crosses in front of the lit disc; small
    # freighters on a farther lane; a shuttle drops from the station toward the surface.
    atlas = latest("traffic-atlas")
    if atlas:
        add("atlas", cutout(atlas, 230), 0.18, [(0, -220, 600, 1.0)], vx=36.0, vy=-2.5)
    # A convoy on the Atlas's lane, clear of the battlegroup above it.
    lane = [("tarsus", 80, 160, 520, 0.14, 30, -1.6), ("mule", 70, -420, 490, 0.13, 30, -1.6),
            ("buffalo", 64, -950, 560, 0.14, 31, -1.8), ("shuttle", 40, 930, 360, 0.07, 9, 6.0)]
    small = []
    for name, width, x, y, depth, vx, vy in lane:
        path = BASE / "assets" / ("traffic-small-v1-%s.png" % name)
        if path.exists():
            small.append(add("lane-" + name, cutout(path, width), depth, [(0, x, y, 1.0)], vx=vx, vy=vy))
    if not small and stand_ins:
        for i, (rel, h, x, y, v) in enumerate([("ships/tarsus.png", 40, -260, 470, 24),
                                               ("ships/buffalo_dd.png", 34, -640, 500, 22)]):
            add("lane-%d" % i, sprite(rel, h, heading=-90, haze=0.5, dim=0.85), 0.12, [(0, x, y, 1.0)],
                vx=v, vy=0.4)

