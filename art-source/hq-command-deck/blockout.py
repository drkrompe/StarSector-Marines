"""Real-scale 3D blockout of the command deck, rendered as an ImageGen layout guide.

Run from the repository root:  python -I art-source/hq-command-deck/blockout.py

The first plates were painted from a text brief and their props did not share one
scale: a desk sized for a person made the chart table knee-high. This blockout
fixes the proportions before anything is painted. Every prop has real dimensions,
one camera projects them all, and ImageGen is told to keep the guide's geometry.

Writes:
  deck-guide-v3.png         plain grey guide for ImageGen (no people)
  deck-guide-v3-people.png  the same with 1.75 m stand-ins, for checking proportions
  deck-guide-v3.layout.json camera and props, so markers come from the layout rather
                            than being estimated from the painting
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
W, H = 2172, 724
SS = 2                                   # supersample

# Camera: level pinhole with a shifted frame, the way an elevated interior is
# usually painted; verticals stay parallel. The camera is a little above head height
# so the chart table's top reads as a surface, with the horizon high in the frame so
# the ceiling, beams and viewport still show.
CAM = {"height": 2.7, "focal": 1050.0, "cx": W / 2, "cy": 170.0}

# Room, metres. x right, y up, z = distance in front of the camera.
ROOM = {"halfWidth": 7.0, "near": 3.2, "back": 11.0, "ceiling": 4.2}

# Props: axis-aligned boxes (x0, x1, y0, y1, z0, z1) with a role.
PROPS = [
    # chart table: waist high, the plotting surface glows
    ("chart-table", (-1.6, 1.6, 0.0, 0.95, 5.6, 7.2), "table"),
    # comms bank against the back wall, left
    ("comms-desk", (-6.8, -2.9, 0.0, 0.76, 10.2, 11.0), "furniture"),
    ("comms-rack", (-6.8, -2.9, 0.76, 2.3, 10.75, 11.0), "furniture"),
    ("comms-screen-1", (-6.5, -5.95, 0.95, 1.35, 10.6, 10.75), "screen"),
    ("comms-screen-2", (-5.75, -5.2, 0.95, 1.35, 10.6, 10.75), "screen"),
    ("comms-screen-3", (-4.6, -4.05, 0.95, 1.35, 10.6, 10.75), "screen"),
    ("comms-screen-4", (-3.85, -3.3, 0.95, 1.35, 10.6, 10.75), "screen"),
    ("comms-screen-5", (-6.2, -5.5, 1.5, 1.95, 10.7, 10.75), "screen"),
    ("comms-screen-6", (-4.3, -3.6, 1.5, 1.95, 10.7, 10.75), "screen"),
    ("comms-chair-1", (-5.9, -5.35, 0.0, 1.05, 9.4, 9.95), "chair"),
    ("comms-chair-2", (-4.2, -3.65, 0.0, 1.05, 9.4, 9.95), "chair"),
    # ledger station, back wall right
    ("ledger-desk", (3.0, 5.4, 0.0, 0.76, 10.2, 11.0), "furniture"),
    ("ledger-monitor", (3.6, 4.02, 0.76, 1.16, 10.45, 10.85), "furniture"),
    ("ledger-monitor-screen", (3.66, 3.96, 0.83, 1.09, 10.43, 10.45), "screen"),
    ("ledger-papers", (4.4, 4.9, 0.76, 0.86, 10.3, 10.7), "furniture"),
    ("supply-crate", (2.2, 2.9, 0.0, 0.6, 10.0, 10.7), "furniture"),
    # hatch and roster board, back wall far right
    ("hatch", (5.8, 6.8, 0.0, 2.1, 10.95, 11.0), "opening"),
    ("roster-board", (3.1, 5.3, 1.45, 2.35, 10.97, 11.0), "board"),
    # ceiling beams for height
    ("beam-1", (-7.0, 7.0, 3.9, 4.2, 6.0, 6.35), "structure"),
    ("beam-2", (-7.0, 7.0, 3.9, 4.2, 8.8, 9.15), "structure"),
]
VIEWPORT = (-2.6, 2.6, 1.45, 3.3)        # x0, x1, y0, y1 on the back wall

# Where people stand: feet (x, z) and height. Castle behind the table, the commander
# at its left end, the comms pair in their chairs, the quartermaster at the desk.
PEOPLE = {
    "castle": (0.7, 7.55, 1.68),
    "commander": (-2.1, 6.0, 1.82),
    "comms-left": (-5.62, 9.65, 1.30),     # seated
    "comms-right": (-3.92, 9.65, 1.30),    # seated
    "quartermaster": (4.2, 9.8, 1.80),
}

TONES = {"floor": 112, "wall": 150, "ceiling": 95, "furniture": 128, "table": 120,
         "chair": 105, "structure": 88, "board": 175, "opening": 60}
GLOW = {"table": 235, "screen": 245}


def project(p):
    x, y, z = p
    return (CAM["cx"] + CAM["focal"] * x / z,
            CAM["cy"] - CAM["focal"] * (y - CAM["height"]) / z)


def box_faces(b):
    x0, x1, y0, y1, z0, z1 = b
    v = lambda x, y, z: (x, y, z)
    return {
        "front": ([v(x0, y0, z0), v(x1, y0, z0), v(x1, y1, z0), v(x0, y1, z0)], (0, 0, -1)),
        "top": ([v(x0, y1, z0), v(x1, y1, z0), v(x1, y1, z1), v(x0, y1, z1)], (0, 1, 0)),
        "left": ([v(x0, y0, z0), v(x0, y0, z1), v(x0, y1, z1), v(x0, y1, z0)], (-1, 0, 0)),
        "right": ([v(x1, y0, z0), v(x1, y0, z1), v(x1, y1, z1), v(x1, y1, z0)], (1, 0, 0)),
    }


def faces():
    r = ROOM
    hw, n, b, c = r["halfWidth"], r["near"], r["back"], r["ceiling"]
    out = [
        ("floor", [(-hw, 0, n), (hw, 0, n), (hw, 0, b), (-hw, 0, b)], TONES["floor"], 0),
        ("ceiling", [(-hw, c, n), (hw, c, n), (hw, c, b), (-hw, c, b)], TONES["ceiling"], 0),
        ("left-wall", [(-hw, 0, n), (-hw, 0, b), (-hw, c, b), (-hw, c, n)], TONES["wall"] - 20, 1),
        ("right-wall", [(hw, 0, n), (hw, 0, b), (hw, c, b), (hw, c, n)], TONES["wall"] - 20, 1),
        ("back-wall", [(-hw, 0, b), (hw, 0, b), (hw, c, b), (-hw, c, b)], TONES["wall"], 1),
    ]
    x0, x1, y0, y1 = VIEWPORT
    out.append(("viewport", [(x0, y0, b - 0.01), (x1, y0, b - 0.01), (x1, y1, b - 0.01), (x0, y1, b - 0.01)], 40, 2))
    cam = np.array([0.0, CAM["height"], 0.0])
    for pid, bx, role in PROPS:
        for side, (pts, normal) in box_faces(bx).items():
            centre = np.mean(pts, axis=0)
            if np.dot(np.array(normal), cam - centre) <= 0:
                continue
            tone = TONES.get(role, 130)
            if side == "top" and role in GLOW:
                tone = GLOW[role]
            elif side == "front" and role == "screen":
                tone = GLOW["screen"]
            elif side != "front":
                tone = int(tone * (0.8 if side == "top" else 0.7))
            out.append((f"{pid}:{side}", pts, tone, 3))
    return out


def render(people):
    img = Image.new("RGB", (W * SS, H * SS), (0, 0, 0))
    d = ImageDraw.Draw(img)
    s = lambda p: (p[0] * SS, p[1] * SS)
    grey = lambda t: (t, t, t)
    fs = faces()
    shell = sorted([f for f in fs if f[3] != 3], key=lambda f: (f[3], -np.mean([p[2] for p in f[1]])))
    for name, pts, tone, _ in shell:
        d.polygon([s(project(p)) for p in pts], fill=grey(tone), outline=grey(30))
    # props and stand-ins, far first; a box's far faces before its near faces
    items = [(max(p[2] for p in pts) + 0.001 * np.mean([p[2] for p in pts]), "face", (pts, tone))
             for name, pts, tone, layer in fs if layer == 3]
    if people:
        items += [(z + 0.0005, "person", (pid, x, z, h)) for pid, (x, z, h) in PEOPLE.items()]
    for _, kind, data in sorted(items, key=lambda i: -i[0]):
        if kind == "face":
            pts, tone = data
            d.polygon([s(project(p)) for p in pts], fill=grey(tone), outline=grey(30))
        else:
            pid, x, z, h = data
            fx, fy = project((x, 0.0, z))
            hx, hy = project((x, h, z))
            half = (fy - hy) * 0.13
            d.ellipse([s((hx - half * 0.55, hy)), s((hx + half * 0.55, hy + half * 1.1))], fill=(220, 60, 60))
            d.rounded_rectangle([s((fx - half, hy + half * 1.1)), s((fx + half, fy))], radius=12, fill=(220, 60, 60))
            d.text(s((fx + half + 4, hy)), pid, fill=(255, 220, 220))
    return img.resize((W, H), Image.LANCZOS)


def main():
    render(False).save(HERE / "deck-guide-v3.png")
    render(True).save(HERE / "deck-guide-v3-people.png")
    layout = {"size": [W, H], "camera": CAM, "room": ROOM, "viewport": VIEWPORT,
              "props": [{"id": p, "box": list(b), "role": r} for p, b, r in PROPS],
              "people": {k: {"x": v[0], "z": v[1], "heightM": v[2],
                             "feetPx": [round(c, 1) for c in project((v[0], 0, v[1]))],
                             "headPx": [round(c, 1) for c in project((v[0], v[2], v[1]))]}
                         for k, v in PEOPLE.items()}}
    (HERE / "deck-guide-v3.layout.json").write_text(json.dumps(layout, indent=2) + "\n", encoding="utf-8")
    for k, v in layout["people"].items():
        print(k, "height px", round(v["feetPx"][1] - v["headPx"][1]))


if __name__ == "__main__":
    main()
