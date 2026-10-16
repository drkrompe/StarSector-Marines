"""Camera estimate and reusable point markers for the approved command-deck plate.

Run from the repository root:  python -I art-source/hq-command-deck/markers.py

Writes, beside the plate:
  deck-plate-v2.markers.json   camera model plus named anchors (pixels, world metres,
                               pixels per metre, local up direction)
  deck-plate-v2.markers.svg    the same anchors at the plate's own pixel size, for
                               authoring live SVG overlays against
  deck-plate-v2.markers-review.png   the anchors drawn over the plate, for checking

The plate is a painting, not a render. It is drawn the way illustrators usually
draw an elevated interior: the camera is level and the frame is shifted down (a
shift lens), so the deck recedes to a horizon above the frame while verticals stay
parallel. A tilted pinhole cannot explain both, and the measurements say so: the
deck seams and the table's sides agree on a vanishing point ~70 px above the frame,
yet the vertical edges lean under 20 px across the whole height.

So the model is a level pinhole whose optical-centre row is the horizon. Focal length
comes from assuming the chart table's top is a 1.8:1 rectangle. Scale comes from the CRT
monitor on the ledger desk (a 17" set, 0.42 m of casing), which makes the desk under it
a normal ~0.7 m high. The chart table then measures about 0.5 m: a low plotting table,
painted short relative to the desk, which is why people sized to the table read small. Up is straight up in every pixel. Absolute distances are the
weakest numbers; overlays and figure placement depend only on the ratios.
"""
import json
import math
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
PLATE = HERE / "deck-plate-v2.master.png"
STEM = "deck-plate-v2"

# --- Measured on the plate (pixels in the 2172x724 master) -------------------------
DEPTH_VP = (1077.0, -70.1)       # Hough fit of receding deck seams; table sides give (1064, -78)
TABLE_ASPECT = 1.8               # assumed width:depth of the plotting surface; fixes focal length

# Scale reference: the CRT on the ledger desk, and the desk's front leg it sits above.
MONITOR_CASING_M = 0.42          # assumed: a 17" CRT's casing width
MONITOR_CASING_PX = (1471.7, 1559.3)
MONITOR_SET_BACK_M = 0.30        # monitor sits this far behind the desk's front edge
DESK_TOP_Y = 302.0               # front lip of the ledger desk
DESK_FLOOR_Y = 448.0             # its front leg on the deck
TABLE_FRONT_BASE_Y = 549.0       # front face meets the deck
TABLE_FRONT_RIM_Y = 420.0        # top of the front rim
TABLE_CENTRE_X = 980.0           # centre of the front face

BACK_WALL_BASE_Y = 364.0         # where the back wall meets the deck, behind the table

# Outline of the whole chart table (rim and front face), hand-measured. Anything
# standing behind the table is drawn under this shape: from this height the floor
# behind it is hidden, so a figure there is cut off around the hips.
TABLE_OCCLUDER = [(765.0, 307.0), (1217.0, 307.0), (1292.0, 425.0), (1277.0, 552.0),
                  (675.0, 552.0), (660.0, 425.0)]

# Floor contacts read off the plate for props that people use.
FLOOR_CONTACTS = {
    "comms-chair-left": (196.0, 521.0),
    "comms-chair-right": (402.0, 445.0),
    "ledger-desk-front": (1600.0, 448.0),
    "hatch-threshold": (1905.0, 440.0),
}

# Where each figure stands, in world metres relative to the table (x right of the
# table's centre line, z behind its front face). Chosen, not measured.
# The painting's props do not share one scale: at the monitor's scale a real-height
# person reads as a giant. Figures are drawn at this fraction of their real height,
# chosen by eye against the desk and table. It applies to every figure alike.
FIGURE_SCALE = 0.70

FIGURE_STANDS = {
    "castle": {"x": 0.45, "z_from_back": 0.15, "height_m": 1.68,
               "note": "At the far edge of the table, right of centre, facing the viewer."},
    "commander": {"x": -0.35, "z": -0.30, "height_m": 1.82,
                  "note": "Front of the table, left of centre, seen from behind."},
}


class Camera:
    """Level pinhole with a shifted frame: the optical-centre row is the horizon."""

    def __init__(self, width, height, focal=None):
        self.w, self.h = width, height
        self.cx, self.cy = DEPTH_VP[0], DEPTH_VP[1]
        self.f = focal if focal is not None else self._solve_focal()
        self._fit_scale()

    def _fit_scale(self):
        # Rows below the horizon obey y - cy = f * drop / distance, so a vertical edge on
        # the deck gives camera height in its own units. The desk's front leg is that edge;
        # the monitor above it, a little further back, says how tall the leg is.
        desk_base, desk_top = DESK_FLOOR_Y - self.cy, DESK_TOP_Y - self.cy
        monitor_px = MONITOR_CASING_PX[1] - MONITOR_CASING_PX[0]
        self.desk_height = 0.7
        for _ in range(8):
            self.height = self.desk_height * desk_base / (desk_base - desk_top)
            desk_distance = self.f * self.height / desk_base
            shrink = desk_distance / (desk_distance + MONITOR_SET_BACK_M)
            self.desk_height = MONITOR_CASING_M * (desk_base - desk_top) / monitor_px * shrink
        base, rim = TABLE_FRONT_BASE_Y - self.cy, TABLE_FRONT_RIM_Y - self.cy
        self.table_height = self.height * (base - rim) / base
        self.table_distance = self.f * self.height / base
        self.x = -(TABLE_CENTRE_X - self.cx) * self.table_distance / self.f
        self.pos = np.array([self.x, self.height, -self.table_distance])

    def _solve_focal(self):
        quad = table_top_quad(np.asarray(Image.open(PLATE).convert("RGB")).astype(float))
        lo, hi = 200.0, 8000.0
        for _ in range(60):
            self.f = (lo + hi) / 2
            self._fit_scale()
            w = [self.on_plane(p, self.table_height) for p in quad]
            width = np.linalg.norm(w[3] - w[2])
            depth = np.mean([w[0][2], w[1][2]]) - np.mean([w[2][2], w[3][2]])
            if width / depth > TABLE_ASPECT:
                lo = self.f            # depth grows with focal length; width does not
            else:
                hi = self.f
        return self.f

    def project(self, p):
        v = np.asarray(p, float) - self.pos
        return (self.cx + self.f * v[0] / v[2], self.cy - self.f * v[1] / v[2])

    def ray(self, px):
        r = np.array([px[0] - self.cx, -(px[1] - self.cy), self.f])
        return r / np.linalg.norm(r)

    def on_plane(self, px, plane_y):
        r = self.ray(px)
        t = (plane_y - self.pos[1]) / r[1]
        return self.pos + t * r

    def px_per_metre(self, p):
        a, b = self.project(p), self.project(np.asarray(p) + [0.0, 1.0, 0.0])
        return math.dist(a, b)

    def up_at(self, px):
        return (0.0, -1.0)


def table_top_quad(rgb):
    """Corners of the glowing plotting surface, fitted from its bright pixels."""
    lum = rgb.mean(axis=2)
    band = slice(300, 430)
    cols = slice(650, 1300)
    mask = lum[band, cols] > 150      # the glow runs 170-215; its frame falls below 120
    rows = {}
    for i, row in enumerate(mask):
        xs = np.nonzero(row)[0]
        if len(xs) > 200:
            rows[i + band.start] = (xs.min() + cols.start, xs.max() + cols.start)
    ys = sorted(rows)
    top, bottom = ys[0], ys[-1]
    inner = ys[3:-3]
    lf = np.polyfit(inner, [rows[y][0] for y in inner], 1)
    rf = np.polyfit(inner, [rows[y][1] for y in inner], 1)
    q = lambda fit, y: float(np.polyval(fit, y))
    return [(q(lf, top), top), (q(rf, top), top), (q(rf, bottom), bottom), (q(lf, bottom), bottom)]


def screens(rgb):
    """Bright, warm console screens outside the chart table, as pixel boxes."""
    small = rgb[::2, ::2]
    lum = small.mean(axis=2)
    mask = (lum > 165) & (small[:, :, 0] > small[:, :, 2] + 10)
    mask[:95, :] = False                      # ceiling lights and the viewport
    mask[150:215, 320:650] = False            # the chart table glow
    seen = np.zeros_like(mask)
    found = []
    h, w = mask.shape
    for y0, x0 in zip(*np.nonzero(mask)):
        if seen[y0, x0]:
            continue
        queue, pts = deque([(y0, x0)]), []
        seen[y0, x0] = True
        while queue:
            y, x = queue.popleft()
            pts.append((y, x))
            for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    queue.append((ny, nx))
        if len(pts) >= 120:
            ys, xs = zip(*pts)
            box = (min(xs) * 2, min(ys) * 2, max(xs) * 2 + 1, max(ys) * 2 + 1)
            bw, bh = box[2] - box[0], box[3] - box[1]
            if bh >= 16 and bw <= 3 * bh:     # screens are boxy; the planet limb and paper are not
                found.append(box)
    found.sort()
    return [{"id": f"screen-{i + 1}", "box": [int(v) for v in b]} for i, b in enumerate(found)]


def main():
    image = Image.open(PLATE).convert("RGB")
    rgb = np.asarray(image.filter(ImageFilter.GaussianBlur(0.8))).astype(float)
    cam = Camera(*image.size)
    w_half = image.size[0] / 2.0
    r2 = lambda v: [round(float(c), 2) for c in v]
    r1 = lambda v: [round(float(c), 1) for c in v]

    anchors = []

    def anchor(aid, kind, world, note=None, px=None):
        world = np.asarray(world, float)
        p = cam.project(world) if px is None else px
        entry = {"id": aid, "kind": kind, "px": r1(p), "world": r2(world),
                 "pxPerMetre": round(cam.px_per_metre(world), 1), "up": r2(cam.up_at(p))}
        if note:
            entry["note"] = note
        anchors.append(entry)
        return world

    quad_px = table_top_quad(rgb)
    quad_world = [cam.on_plane(p, cam.table_height) for p in quad_px]
    names = ["back-left", "back-right", "front-right", "front-left"]
    for name, p, w in zip(names, quad_px, quad_world):
        anchor(f"table-top-{name}", "table-corner", w, px=p)
    back_z = float(np.mean([quad_world[0][2], quad_world[1][2]]))
    front_z = float(np.mean([quad_world[2][2], quad_world[3][2]]))
    centre = np.mean(quad_world, axis=0)
    anchor("table-top-centre", "table-centre", centre, "Origin for a live plot on the table.")

    for aid, px in FLOOR_CONTACTS.items():
        anchor(aid, "floor-contact", cam.on_plane(px, 0.0), px=px)

    figures = []
    for fid, spec in FIGURE_STANDS.items():
        z = back_z + spec["z_from_back"] if "z_from_back" in spec else front_z + spec["z"]
        feet = anchor(f"{fid}-feet", "figure-stand", [spec["x"], 0.0, z], spec["note"])
        drawn = spec["height_m"] * FIGURE_SCALE
        head = cam.project(feet + [0.0, drawn, 0.0])
        anchor(f"{fid}-head", "figure-head", feet + [0.0, drawn, 0.0])
        fx, fy = cam.project(feet)
        figures.append({"id": fid, "feetPx": r1((fx, fy)), "headPx": r1(head),
                        "heightPx": round(fy - head[1], 1), "heightM": spec["height_m"],
                        "figureScale": FIGURE_SCALE})

    table_w = float(np.linalg.norm(quad_world[3] - quad_world[2]))
    table_d = float(back_z - front_z)
    data = {
        "plate": PLATE.name,
        "size": list(image.size),
        "frame": "World metres: origin on the deck below the centre of the chart table's front face; "
                 "x right, y up, z away from the viewer. Pixels: plate master, origin top-left.",
        "camera": {
            "focalPx": round(cam.f, 1),
            "horizontalFovDeg": round(2 * math.degrees(math.atan(w_half / cam.f)), 1),
            "model": "level pinhole, shifted frame: optical-centre row is the horizon; verticals parallel",
            "opticalCentrePx": [cam.cx, cam.cy],
            "horizonY": round(DEPTH_VP[1], 1),
            "depthVanishingPoint": list(DEPTH_VP),
            "up": [0.0, -1.0],
            "positionM": r2(cam.pos),
            "assumptions": {"monitorCasingM": MONITOR_CASING_M, "tableAspect": TABLE_ASPECT},
            "derived": {"deskHeightM": round(cam.desk_height, 2), "tableHeightM": round(cam.table_height, 2)},
            "uncertainty": "Painting, not render. Horizon/VP within ~15 px. Focal length rests on the "
                           "table aspect and scale on the ledger monitor; absolute distances are the "
                           "weakest values, ratios near the table the strongest.",
        },
        "chartTable": {"widthM": round(table_w, 2), "depthM": round(table_d, 2),
                       "heightM": round(cam.table_height, 2), "topQuadPx": [r1(p) for p in quad_px],
                       "occluderPx": [r1(p) for p in TABLE_OCCLUDER],
                       "occludes": ["castle"]},
        "figures": figures,
        "anchors": anchors,
        "screens": screens(rgb),
    }
    (HERE / f"{STEM}.markers.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

    w, h = image.size
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">',
           f'  <line id="horizon" x1="0" y1="{DEPTH_VP[1]}" x2="{w}" y2="{DEPTH_VP[1]}" stroke="#ff0" />',
           '  <polygon id="table-top" fill="none" stroke="#0ff" points="'
           + " ".join(f"{x:.1f},{y:.1f}" for x, y in quad_px) + '" />',
           '  <polygon id="table-occluder" fill="none" stroke="#f33" points="'
           + " ".join(f"{x:.1f},{y:.1f}" for x, y in TABLE_OCCLUDER) + '" />']
    for f in figures:
        (x0, y0), (x1, y1) = f["feetPx"], f["headPx"]
        svg.append(f'  <line id="{f["id"]}-stand" x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}" stroke="#f0f" />')
    for s in data["screens"]:
        x0, y0, x1, y1 = s["box"]
        svg.append(f'  <rect id="{s["id"]}" x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="none" stroke="#f80" />')
    for a in anchors:
        x, y = a["px"]
        svg.append(f'  <circle id="{a["id"]}" class="{a["kind"]}" cx="{x}" cy="{y}" r="5" fill="#f0f" />')
    svg.append("</svg>")
    (HERE / f"{STEM}.markers.svg").write_text("\n".join(svg) + "\n", encoding="utf-8")

    review = image.copy()
    d = ImageDraw.Draw(review)
    d.polygon(TABLE_OCCLUDER, outline=(255, 60, 60), width=2)
    d.polygon(quad_px, outline=(0, 255, 255), width=3)
    for s in data["screens"]:
        d.rectangle(s["box"], outline=(255, 140, 0), width=2)
    # deck grid on the floor plane, 1 m squares, to show the recovered perspective
    wall_z = float(cam.on_plane((cam.cx, BACK_WALL_BASE_Y), 0.0)[2])
    for gx in range(-8, 9):
        a, b = cam.project([gx, 0, front_z - 3]), cam.project([gx, 0, wall_z])
        d.line([a, b], fill=(90, 160, 90), width=1)
    for gz in np.arange(front_z - 3, wall_z + 0.01, 1.0):
        d.line([cam.project([-8, 0, gz]), cam.project([8, 0, gz])], fill=(90, 160, 90), width=1)
    for f in figures:
        d.line([tuple(f["feetPx"]), tuple(f["headPx"])], fill=(255, 0, 255), width=4)
    for a in anchors:
        x, y = a["px"]
        ux, uy = a["up"]
        d.ellipse([x - 6, y - 6, x + 6, y + 6], outline=(255, 0, 255), width=2)
        d.line([(x, y), (x + ux * 40, y + uy * 40)], fill=(255, 255, 0), width=2)
        d.text((x + 8, y - 14), a["id"], fill=(255, 255, 255))
    review.save(HERE / f"{STEM}.markers-review.png")
    print(json.dumps({k: data[k] for k in ("camera", "chartTable", "figures")}, indent=1))
    print("screens", len(data["screens"]), "anchors", len(anchors))


if __name__ == "__main__":
    main()
