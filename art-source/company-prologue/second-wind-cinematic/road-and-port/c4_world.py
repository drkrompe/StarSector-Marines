"""C4 world massing in metres (c4-layout.md). Pure Python, no Blender: the guide builder
(build_c4_guides.py) turns these primitives into meshes, and compose_c4.py draws the plan
and places the moving elements from the same numbers, so the guides, the animatic and the
moving-element paths cannot disagree.

Axes are c4_camera.py's: +X heading (screen right), +Y outward from the window, +Z up.
Every primitive names the layer that owns it. Land is laid out in strips parallel to X on
purpose: a rigid sliding plate cannot shear, and lines that run with the motion hide that.
"""
import math
import random

# Freight road: the one the company will later fight for. Parallel to the track, so a hauler
# on it only translates on screen.
ROAD_Y = (972.0, 988.0)
ROAD_LANES = (976.0, 984.0)          # inbound lane (toward the port, +X), outbound lane (-X)
SPUR_X = (1443.0, 1457.0)            # freight gate spur, turning outward into the terminal
WEIGH_STATION_X = (380.0, 470.0)
PORT_EDGE_X = 1000.0                 # native growth from here, port proper from about 1250

# Launch cradles on the far layer: lifters climb out of these (moving elements).
CRADLES = [(2600.0, 6400.0), (3300.0, 6900.0), (4050.0, 6500.0), (4700.0, 7100.0)]
CRANES_L2 = [(1360.0, 2250.0), (1490.0, 2250.0), (1620.0, 2250.0)]
LIGHT_MASTS_L2 = [(2060.0 + 55 * i, 2440.0) for i in range(9)]


def _prims():
    P = []
    rng = random.Random(4)

    def box(layer, mat, x0, x1, y0, y1, z0, z1, name=""):
        P.append(dict(kind="box", layer=layer, mat=mat, x=(x0, x1), y=(y0, y1), z=(z0, z1), name=name))

    def cyl(layer, mat, cx, cy, r, z0, z1, name=""):
        P.append(dict(kind="cyl", layer=layer, mat=mat, c=(cx, cy), r=r, z=(z0, z1), name=name))

    def dome(layer, mat, cx, cy, r, h, z0=0.0):
        P.append(dict(kind="dome", layer=layer, mat=mat, c=(cx, cy), r=r, h=h, z0=z0))

    def plane(layer, mat, x0, x1, y0, y1, z=0.0, name=""):
        P.append(dict(kind="plane", layer=layer, mat=mat, x=(x0, x1), y=(y0, y1), z=z, name=name))

    def prism(layer, mat, x0, x1, y0, y1, z0, z1, name=""):
        """Gable along X: ridge at mid-Y."""
        P.append(dict(kind="prism", layer=layer, mat=mat, x=(x0, x1), y=(y0, y1), z=(z0, z1), name=name))

    def canopy_mass(layer, x0, x1, y0, y1, density, rmin, rmax, hscale=1.0, mats=("canopy", "canopy2")):
        n = int((x1 - x0) * (y1 - y0) * density)
        for _ in range(n):
            r = rng.uniform(rmin, rmax)
            dome(layer, rng.choice(mats), rng.uniform(x0, x1), rng.uniform(y0, y1), r, r * rng.uniform(1.1, 1.8) * hscale)

    def crop_strips(layer, x0, x1, y0, y1, gap_x=()):
        y = y0
        mats = ["crop_a", "crop_b", "crop_c", "crop_a", "soil", "crop_b"]
        i = 0
        while y < y1:
            w = rng.uniform(18.0, 34.0)
            m = mats[i % len(mats)] if rng.random() > 0.15 else rng.choice(mats)
            xs = [x0] + [g for pair in gap_x for g in pair] + [x1]
            for a, b in zip(xs[::2], xs[1::2]):
                plane(layer, m, a, b, y, min(y + w - 1.5, y1), 0.0)
            plane(layer, "track", x0, x1, min(y + w - 1.5, y1), min(y + w, y1), 0.02)
            y += w
            i += 1

    # ------------------------------------------------------------ L4 near: fields and the road
    L = "L4-near"
    plane(L, "ground", -700, 1900, 830, 1100, -0.05)
    crop_strips(L, -700, 1000, 840, 962, gap_x=((290.0, 300.0),))
    plane(L, "track", 290, 300, 840, 962, 0.02)                 # one farm track, the only cross line
    plane(L, "verge", -700, 1900, 962, ROAD_Y[0], 0.01)
    plane(L, "road", -700, 1900, ROAD_Y[0], ROAD_Y[1], 0.03, name="freight road")
    plane(L, "marking", -700, 1900, 979.6, 980.4, 0.05)
    plane(L, "verge", -700, 1900, ROAD_Y[1], 1000, 0.01)
    # hedgerow beyond the road: emergent trees in a ragged belt (its top is the L3 seam)
    canopy_mass(L, -700, 1000, 1040, 1100, 0.0011, 5, 11, 1.4)
    canopy_mass(L, -700, 1000, 1090, 1100, 0.002, 6, 12, 1.3)
    # weigh station: lay-by, canopy, office, barrier
    x0, x1 = WEIGH_STATION_X
    plane(L, "concrete", x0, x1, 988, 1018, 0.04)
    for px in (x0 + 25, x1 - 25):
        for py in (995, 1012):
            box(L, "steel", px - 0.4, px + 0.4, py - 0.4, py + 0.4, 0, 7)
    box(L, "roof", x0 + 22, x1 - 22, 992, 1015, 7, 8, name="weigh station canopy")
    box(L, "building", x1 - 18, x1 - 4, 1020, 1032, 0, 4.5, name="weigh office")
    box(L, "marking", x0 + 30, x0 + 31, 988, 996, 1.0, 1.4)
    # native growth pressing in between farm and port
    canopy_mass(L, 1000, 1260, 840, 962, 0.0016, 7, 15, 1.5)
    canopy_mass(L, 1000, 1260, 1000, 1100, 0.0016, 7, 15, 1.5)
    # port edge on the near layer: fence, gate spur, queued trailers, low sheds
    plane(L, "scrub", 1260, 1900, 840, 962, 0.0)
    box(L, "fence", 1260, 1900, 1000, 1001, 0, 3)
    plane(L, "road", SPUR_X[0], SPUR_X[1], ROAD_Y[1], 1100, 0.03, name="gate spur")
    plane(L, "concrete", 1260, 1900, 1001, 1100, 0.02)
    for i in range(3):
        y = 1010 + i * 24
        box(L, "trailer", 1446, 1454, y, y + 18, 0.6, 4.4, name="queued hauler")
    box(L, "gate", 1430, 1470, 1000, 1004, 0, 9, name="freight gate frame")
    box(L, "building", 1540, 1700, 1040, 1090, 0, 11, name="freight shed")
    prism(L, "roof", 1540, 1700, 1040, 1090, 11, 15)
    box(L, "building", 1740, 1900, 1030, 1080, 0, 9)
    for i in range(6):
        cyl(L, "steel", 1300 + i * 90, 1003, 0.35, 0, 16, name="light mast")

    # ------------------------------------------------------------ L3 mid: fields, farmstead, terminal
    L = "L3-mid"
    plane(L, "ground", -900, 2200, 1100, 1700, -0.05)
    crop_strips(L, -900, 960, 1100, 1170)
    plane(L, "water", -900, 960, 1172, 1184, 0.0, name="irrigation canal")
    crop_strips(L, -900, 960, 1186, 1610, gap_x=((-280.0, -100.0),))
    plane(L, "concrete", -280, -100, 1230, 1400, 0.02, name="farmstead yard")
    for i in range(3):
        cyl(L, "silo", -250 + i * 16, 1300, 6, 0, 22, name="silo")
    box(L, "building", -200, -150, 1260, 1290, 0, 9, name="barn")
    prism(L, "roof", -200, -150, 1260, 1290, 9, 13)
    box(L, "building", -140, -110, 1320, 1350, 0, 7)
    canopy_mass(L, -900, 1000, 1610, 1700, 0.0012, 8, 15, 1.4)   # tree belt; its top is the L2 seam
    canopy_mass(L, 960, 1100, 1100, 1700, 0.0014, 8, 16, 1.5)   # native growth
    # freight terminal
    plane(L, "concrete", 1100, 2200, 1100, 1700, 0.01)
    box(L, "fence", 1100, 2200, 1118, 1119, 0, 3)
    plane(L, "road", SPUR_X[0], SPUR_X[1], 1100, 1460, 0.03, name="gate spur")
    for i in range(9):
        y = 1140 + (i % 3) * 22
        x = 1180 + (i // 3) * 140 + (i % 2) * 18
        box(L, "trailer", x, x + 16, y, y + 3.2, 0.6, 4.4)
    for x0 in (1200, 1500, 1800):
        box(L, "building", x0, x0 + 180, 1300, 1360, 0, 14, name="freight warehouse")
        for k in range(6):
            a = x0 + k * 30
            prism(L, "roof", a, a + 30, 1300, 1360, 14, 18)
    box(L, "building", 1500, 1560, 1450, 1520, 0, 22, name="dispatch block")
    for i in range(10):                                       # container stacks on the terminal apron
        for j in range(2):
            x = 1150 + i * 78
            box(L, "container", x, x + 60, 1560 + j * 40, 1572 + j * 40, 0, 5.2 + ((i + j) % 3) * 2.6)
    for x0 in (1620, 1760):
        box(L, "building", x0, x0 + 110, 1440, 1520, 0, 16, name="cold store")
    for i in range(8):
        cyl(L, "steel", 1150 + i * 120, 1260, 0.45, 0, 24, name="light mast")
    canopy_mass(L, 1100, 2200, 1640, 1700, 0.0008, 8, 14, 1.4)

    # ------------------------------------------------------------ L2 port district and far farmland
    L = "L2-port"
    plane(L, "ground", -2600, 3600, 1700, 3800, -0.05)
    crop_strips(L, -2600, 600, 1700, 2150)
    crop_strips(L, -2600, -1100, 2150, 3400)
    crop_strips(L, -300, 600, 2150, 3400)
    # a settlement: the people who live here
    plane(L, "concrete", -1100, -300, 2200, 2700, 0.01, name="village")
    plane(L, "road", -1100, -300, 2440, 2452, 0.02)
    for i in range(34):
        x = -1080 + (i % 17) * 46 + rng.uniform(-6, 6)
        y = 2380 if i < 17 else 2475
        y += rng.uniform(-10, 10)
        box(L, "house", x, x + 14, y, y + 11, 0, 6)
        prism(L, "roof", x, x + 14, y, y + 11, 6, 9)
    box(L, "building", -700, -620, 2560, 2620, 0, 12, name="co-op hall")
    cyl(L, "silo", -520, 2600, 7, 0, 30, name="water tower")
    canopy_mass(L, -2600, 600, 3350, 3800, 0.0008, 10, 20, 1.4)
    canopy_mass(L, -2600, 600, 2900, 2960, 0.0010, 9, 16, 1.4)
    canopy_mass(L, 600, 960, 1700, 3800, 0.0012, 10, 20, 1.5)    # native growth band
    # port district
    plane(L, "concrete", 960, 3600, 1750, 3300, 0.01)
    for i in range(8):
        cyl(L, "tank", 1010 + (i % 4) * 40, 1980 + (i // 4) * 45, 15, 0, 18, name="tank")
    for i in range(6):
        for j in range(3):
            box(L, "container", 1300 + i * 62, 1350 + i * 62, 2190 + j * 40, 2202 + j * 40, 0, 8 + (i * 7 + j) % 3 * 2.6)
    for cx, cy in CRANES_L2:
        for dx in (-14, 14):
            for dy in (-22, 22):
                box(L, "crane", cx + dx - 1, cx + dx + 1, cy + dy - 1, cy + dy + 1, 0, 42)
        box(L, "crane", cx - 15, cx + 15, cy - 26, cy + 26, 42, 46)
        box(L, "crane", cx - 2, cx + 2, cy - 70, cy + 26, 52, 56, name="crane boom")
        box(L, "crane", cx - 3, cx + 3, cy + 18, cy + 26, 46, 58)
    for x0 in (1720, 1860):
        box(L, "building", x0, x0 + 120, 1980, 2060, 0, 30, name="overhaul hall")
        prism(L, "roof", x0, x0 + 120, 1980, 2060, 30, 38)
    cyl(L, "steel", 1990, 2700, 5, 0, 85, name="control tower shaft")
    box(L, "glass", 1980, 2000, 2690, 2710, 85, 95, name="control tower cab")
    for cx, cy in ((2180, 2620), (2400, 2700), (2600, 2600)):
        cyl(L, "pad", cx, cy, 40, 0, 0.6, name="shuttle pad")
    box(L, "hull", 2170, 2215, 2610, 2630, 0.6, 9, name="parked shuttle")
    for cx, cy in LIGHT_MASTS_L2:
        cyl(L, "steel", cx, cy, 0.6, 0, 26, name="light mast")
    box(L, "building", 2700, 3000, 2100, 2250, 0, 24)
    for i in range(5):                                        # repair halls and stores along the port road
        x0 = 1050 + i * 300
        box(L, "building", x0, x0 + 200, 2900, 2990, 0, 20 + (i % 2) * 8, name="repair hall")
        prism(L, "roof", x0, x0 + 200, 2900, 2990, 20 + (i % 2) * 8, 26 + (i % 2) * 8)
    for i in range(4):
        box(L, "building", 2050 + i * 140, 2150 + i * 140, 1900, 1980, 0, 14)
    box(L, "building", 3050, 3300, 2400, 2500, 0, 18)
    cyl(L, "steel", 2850, 2600, 4, 0, 60, name="stack")
    canopy_mass(L, 960, 3600, 3300, 3800, 0.0009, 10, 20, 1.5)

    # ------------------------------------------------------------ L1 far: plain, launch field, ridges
    L = "L1-far"
    plane(L, "plain", -14000, 16000, 3800, 12000, -0.05)
    canopy_mass(L, -14000, 1800, 4200, 5200, 0.00012, 20, 45, 1.2)
    canopy_mass(L, -14000, 16000, 8000, 11000, 0.00004, 30, 60, 1.2)
    plane(L, "concrete", 2000, 5400, 5600, 7800, 0.01, name="launch field")
    for cx, cy in CRADLES:
        cyl(L, "pad", cx, cy, 70, 0, 2, name="cradle apron")
        for a in range(4):
            ang = a * math.pi / 2 + math.pi / 4
            px, py = cx + 30 * math.cos(ang), cy + 30 * math.sin(ang)
            box(L, "cradle", px - 4, px + 4, py - 4, py + 4, 0, 140, name="cradle leg")
        box(L, "cradle", cx - 34, cx + 34, cy - 34, cy + 34, 130, 140, name="cradle ring")
    cyl(L, "hull", 4050, 6500, 17, 2, 210, name="lifter standing in cradle")
    for i, (x0, w, h) in enumerate(((2200, 260, 48), (3600, 200, 62), (5000, 220, 40))):
        box(L, "building", x0, x0 + w, 7600, 7700, 0, h, name="terminal block")
    cyl(L, "steel", 3000, 7400, 6, 0, 180, name="beacon mast")
    # ridges and the volcanic cone: continuity landmark from the farmland and port studies
    P.append(dict(kind="terrain", layer=L, mat="mountain", x=(-30000.0, 36000.0), y=(12000.0, 46000.0)))
    return P


PRIMS = _prims()


def terrain_height(x, y):
    """Far ridges plus the cone. Heights in metres above the plain."""
    t = max(0.0, min(1.0, (y - 12000.0) / 6000.0))
    ridge = (260 + 180 * math.sin(x / 2300.0 + 1.3) + 120 * math.sin(x / 870.0 + 0.4)
             + 90 * math.sin(x / 410.0 + y / 900.0)) * (0.5 + y / 40000.0)
    cone = 1150.0 * max(0.0, 1.0 - math.hypot(x + 1800.0, y - 19000.0) / 5200.0) ** 1.35
    return max(0.0, t * ridge + cone)
