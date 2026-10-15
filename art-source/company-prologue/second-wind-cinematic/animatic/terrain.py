"""Restrained, untextured Jangala landforms for the Second Wind animatic.

World metres: +X approach, +Y port. All detail is massing rather than trees.
The landing yard and the final 700 m of approach remain unobstructed.
"""

import math
import random


def build_terrain(bpy, helpers):
    """Build deterministic environment geometry and return framing metadata."""
    get = helpers.get if isinstance(helpers, dict) else lambda k: getattr(helpers, k)
    box = get("box")
    material = get("material")
    before = len(bpy.data.objects)
    earth = material("Terrain | damp green earth", (0.16, 0.205, 0.16))
    greens = [material("Canopy | %02d" % i, c) for i, c in enumerate([
        (0.115, 0.205, 0.145), (0.145, 0.245, 0.165),
        (0.18, 0.275, 0.185), (0.21, 0.295, 0.205)])]
    water = material("River | pewter green", (0.24, 0.36, 0.365))
    clearing = material("Plantation | cleared soil", (0.29, 0.305, 0.235))
    crop = material("Plantation | managed green", (0.245, 0.32, 0.205))
    road = material("Plantation | service roads", (0.39, 0.405, 0.35))
    cloud = material("Storm | distant grey masses", (0.43, 0.465, 0.475))
    farhill = material("Horizon | grey green ridge", (0.26, 0.325, 0.30))
    rng = random.Random(1947)

    def mesh(name, vertices, faces, mats, indices=None):
        data = bpy.data.meshes.new(name)
        data.from_pydata(vertices, [], faces)
        data.update()
        obj = bpy.data.objects.new(name, data)
        bpy.context.collection.objects.link(obj)
        for mat in mats:
            data.materials.append(mat)
        if indices:
            for polygon, index in zip(data.polygons, indices):
                polygon.material_index = index
        return obj

    def river_y(x):
        return 2850 + 560 * math.sin(x / 3200) + 230 * math.sin(x / 1250)

    def height(x, y):
        # Flatten the settlement, managed land and entire low approach lane.
        if -1100 < x < 1000 and abs(y) < 700:
            return 0.0
        if -6500 < x < -1800 and 200 < y < 2300:
            return 8.0
        lane = min(1.0, max(0.0, (abs(y) - 110) / 800))
        low = 32 + 27 * math.sin(x / 1250) * math.cos(y / 1500)
        ridge = max(0.0, (abs(y) - 5200) / 8000) * (
            410 + 280 * math.sin(x / 2600) ** 2)
        h = (low + ridge) * lane
        # A broad depressed riverbed keeps the ribbon visible above the mesh.
        bank = min(1.0, max(0.0, (abs(y - river_y(x)) - 220) / 330))
        return -12 + (h + 12) * bank

    xs = list(range(-22000, 10001, 400))
    # Include explicit flat-yard and lane boundary rows in the coarse mesh.
    ys = sorted(set(list(range(-8000, 16001, 400)) + [-700, -110, 0, 110, 700]))
    verts = [(x, y, height(x, y)) for y in ys for x in xs]
    faces = []
    for j in range(len(ys) - 1):
        for i in range(len(xs) - 1):
            a = j * len(xs) + i
            faces.append((a, a + 1, a + 1 + len(xs), a + len(xs)))
    mesh("Jangala | continuous landform", verts, faces, [earth])

    # One uninterrupted river landmark, with winding banks and a far estuary.
    verts, faces = [], []
    for i, x in enumerate(range(-22400, 10401, 200)):
        width = 195 + 65 * math.sin(x / 2100) ** 2
        y = river_y(x)
        verts.extend([(x, y - width, -2), (x, y + width, -2)])
        if i:
            a = 2 * i
            faces.append((a - 2, a, a + 1, a - 1))
    mesh("Jangala | winding river", verts, faces, [water])
    box("Jangala | far estuary", (-6000, 13700, -5), (32000, 4700, 6), water)

    def masses(name, specs, mats):
        """Batch rounded faceted crowns into one mesh, avoiding flat skirts."""
        vertices, polygons, indices = [], [], []
        for cx, cy, cz, rx, ry, rz, mi in specs:
            base = len(vertices)
            vertices.append((cx, cy, cz - rz))
            for ring, (radius, z) in enumerate(((0.65, -0.65), (1.0, 0.0), (0.65, 0.65))):
                for k in range(8):
                    angle = k * math.tau / 8 + (ring % 2) * 0.13
                    vertices.append((cx + math.cos(angle) * rx * radius,
                                     cy + math.sin(angle) * ry * radius, cz + rz * z))
            vertices.append((cx, cy, cz + rz))
            for k in range(8):
                n = (k + 1) % 8
                polygons.append((base, base + 1 + n, base + 1 + k))
                indices.append(mi)
                for ring in range(2):
                    a = base + 1 + 8 * ring
                    polygons.append((a + k, a + n, a + 8 + n, a + 8 + k))
                    indices.append(mi)
                polygons.append((base + 17 + k, base + 17 + n, base + 25))
                indices.append(mi)
        return mesh(name, vertices, polygons, mats, indices)

    forest = []
    for x in range(-20000, 7001, 750):
        for y in (-2800, -1400, 850, 1800, 4000, 5200, 6900, 9000):
            cx, cy = x + rng.uniform(-260, 260), y + rng.uniform(-250, 250)
            rx, ry = rng.uniform(390, 650), rng.uniform(320, 590)
            if abs(cy - river_y(cx)) < ry + 330:
                continue
            if -6800 < cx < -1500 and 0 < cy < 2500:
                continue
            if -1500 < cx < 1300 and abs(cy) < 1600:
                continue
            # A forest patch is a connected cluster of overlapping crowns,
            # rather than a single kilometre-wide flattened ellipsoid.
            for ix in range(-2, 3):
                for iy in range(-2, 3):
                    if abs(ix) == 2 and abs(iy) == 2:
                        continue
                    px = cx + ix * rx * 0.37 + rng.uniform(-35, 35)
                    py = cy + iy * ry * 0.37 + rng.uniform(-35, 35)
                    radius = rng.uniform(0.25, 0.37)
                    rz = rng.uniform(85, 155)
                    forest.append((px, py, height(px, py) + rz * 0.56,
                                   rx * radius, ry * radius, rz, rng.randrange(4)))
    masses("Jangala | interlocking forest canopy", forest, greens)

    # Close port-side treeline gives parallax during the last window shot.
    near = []
    for i in range(16):
        x = -2100 + i * 150
        y = 440 + 75 * math.sin(i * 1.8)
        for j in range(7):
            angle = j * math.tau / 7
            px = x + math.cos(angle) * 65
            py = y + math.sin(angle) * 52
            rz = 29 + rng.uniform(0, 23)
            near.append((px, py, height(px, py) + rz * 0.6,
                         40 + rng.uniform(0, 14), 38 + rng.uniform(0, 12),
                         rz, (i + j) % 4))
    masses("Jangala | port approach canopy", near, greens)

    # Large rectangular clearings and visibly regular crop strips establish
    # managed land without introducing tiny assets or individual tree models.
    for col in range(5):
        x = -5950 + col * 780
        for row in range(2):
            y = 650 + row * 820
            box("Plantation | clearing %d %d" % (col, row),
                (x, y, 10), (680, 690, 3), clearing)
            for strip in range(5):
                box("Plantation | crop %d %d %d" % (col, row, strip),
                    (x, y - 260 + strip * 130, 16), (620, 76, 10), crop)
        box("Plantation | cross road %d" % col,
            (x + 390, 1110, 12), (30, 1900, 5), road)
    box("Plantation | collector road", (-4250, 1060, 12), (4500, 38, 5), road)

    hills = []
    for i in range(18):
        x = -22000 + i * 1900
        hills.append((x, 11300 + rng.uniform(-700, 700), 260,
                      1600, 1300, rng.uniform(450, 850), 0))
    masses("Jangala | distant folded ridgeline", hills, [farhill])
    storms = []
    for i in range(16):
        cx = -19000 + i * 1700
        cy = 10500 + rng.uniform(-600, 600)
        for j in range(7):
            # Joined billows give the storm a deep, irregular underside and
            # vertical silhouette; keep the entire bank far off the flight lane.
            storms.append((cx + (j - 3) * 360, cy + rng.uniform(-350, 350),
                           2650 + rng.uniform(-200, 400),
                           rng.uniform(550, 850), rng.uniform(500, 800),
                           rng.uniform(440, 780), 0))
    masses("Jangala | distant storm shelf proxies", storms, [cloud])

    return {"bounds": [[-22400, -8000, -12], [10400, 16000, 3400]],
            "object_count": len(bpy.data.objects) - before,
            "flight_lane": {"y": 0, "max_ground_z": 0,
                            "clear_final_approach": [-700, 150, -80, 80]},
            "landmarks": {"river": [-6000, river_y(-6000), -2],
                          "plantations": [-4250, 1100, 16],
                          "storm_shelf": [-5000, 10500, 2700]},
            "canopy_mass_count": len(forest) + len(near)}
