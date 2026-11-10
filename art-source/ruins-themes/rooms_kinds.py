"""Block-out kinds for the ruins rooms (``ruins-theme-rooms``, ``RuinsRoom``): the furniture of a plant's control
room, workshop, lab, crew quarters and store, each drawn standing against the north wall (its back to the top edge,
its working face to the room below), since the ground turns a piece with its room.

Every kind is ``r/<shape>``; the brief says what each piece is, the block-out only fixes its outline and layout.
"""
import math
import random

BODY, EDGE, DARK, LIGHT = (128, 132, 138, 255), (70, 74, 80, 255), (40, 42, 46, 255), (176, 180, 186, 255)
SCREEN = (120, 170, 190, 255)
WARM = (196, 150, 90, 255)


def _box(d, x0, y0, x1, y1, fill=BODY, line=EDGE, width=6):
    d.rectangle((x0, y0, x1, y1), fill=fill, outline=line, width=width)


def draw_kind(im, d, kind, x, y, w, h):
    if not kind.startswith("r/"):
        return False
    shape = kind[2:]
    rng = random.Random(f"{kind}{x}{y}")
    i = 14  # inset from the frame
    if shape in ("console", "burnt"):
        # Cabinets against the wall, a screen on each facing the room.
        n = max(1, round(w / 160))
        cw = (w - 2 * i) / n
        for k in range(n):
            cx = x + i + k * cw
            _box(d, cx + 4, y + i, cx + cw - 4, y + 0.62 * h)
            d.rectangle((cx + 18, y + 0.42 * h, cx + cw - 18, y + 0.58 * h), fill=DARK if shape == "burnt" else SCREEN)
            d.rectangle((cx + 18, y + 0.66 * h, cx + cw - 18, y + 0.8 * h), fill=LIGHT, outline=EDGE)
        if shape == "burnt":
            for _ in range(5):
                bx, by = x + rng.uniform(0.1, 0.9) * w, y + rng.uniform(0.15, 0.6) * h
                d.ellipse((bx - 28, by - 22, bx + 28, by + 22), fill=DARK)
    elif shape == "board":
        # A wall board: a shallow cabinet along the wall, its face a big mimic panel with a line diagram.
        _box(d, x + i, y + i, x + w - i, y + 0.7 * h)
        d.rectangle((x + 30, y + 0.2 * h, x + w - 30, y + 0.6 * h), fill=DARK)
        pts = [(x + 50 + k * (w - 100) / 7, y + (0.3 if k % 2 else 0.5) * h) for k in range(8)]
        d.line(pts, fill=SCREEN, width=6)
        for px, py in pts[::2]:
            d.ellipse((px - 9, py - 9, px + 9, py + 9), fill=WARM)
        d.rectangle((x + 30, y + 0.74 * h, x + w - 30, y + 0.86 * h), fill=LIGHT, outline=EDGE)
    elif shape == "desk":
        _box(d, x + i, y + 0.2 * h, x + w - i, y + h - i)
        _box(d, x + 0.3 * w, y + 0.24 * h, x + 0.7 * w, y + 0.5 * h, fill=EDGE)
        d.rectangle((x + 0.34 * w, y + 0.4 * h, x + 0.66 * w, y + 0.48 * h), fill=SCREEN)
    elif shape == "panel":
        pts = [(x + 0.08 * w, y + 0.3 * h), (x + 0.86 * w, y + 0.12 * h), (x + 0.92 * w, y + 0.72 * h),
               (x + 0.14 * w, y + 0.88 * h)]
        d.polygon(pts, fill=LIGHT, outline=EDGE)
        d.line([(x + 0.5 * w, y + 0.5 * h), (x + 0.62 * w, y + 0.2 * h), (x + 0.75 * w, y + 0.85 * h)], fill=DARK, width=8)
    elif shape == "hatch":
        _box(d, x + i, y + i, x + w - i, y + 0.42 * h, fill=EDGE)
        d.rectangle((x + 0.25 * w, y + i + 10, x + 0.75 * w, y + 0.36 * h), fill=DARK)
        _box(d, x + 0.2 * w, y + 0.46 * h, x + 0.8 * w, y + 0.8 * h, fill=LIGHT)
    elif shape in ("hood", "store", "locker", "locker-open", "rack", "shelf", "bunk", "bin", "trolley", "terminal"):
        _box(d, x + i, y + i, x + w - i, y + h - i)
        if shape == "hood":
            d.rectangle((x + 30, y + 0.5 * h, x + w - 30, y + h - 28), fill=SCREEN)
        elif shape == "store":
            d.rectangle((x + 26, y + 26, x + w - 26, y + h - 26), fill=LIGHT, outline=EDGE)
            d.rectangle((x + w - 44, y + 0.4 * h, x + w - 32, y + 0.6 * h), fill=DARK)
        elif shape in ("locker", "locker-open"):
            d.line([(x + i, y + h / 2), (x + w - i, y + h / 2)], fill=EDGE, width=8)
            if shape == "locker-open":
                for oy in (y + 0.25 * h, y + 0.75 * h):
                    d.polygon([(x + w - i, oy - 50), (x + w + 30, oy - 30), (x + w + 30, oy + 30), (x + w - i, oy + 50)],
                              fill=LIGHT, outline=EDGE)
                for _ in range(4):
                    bx, by = x + w + rng.uniform(-10, 40), y + rng.uniform(0.2, 0.8) * h
                    d.ellipse((bx - 14, by - 10, bx + 14, by + 10), fill=WARM)
        elif shape in ("rack", "shelf"):
            rows = 4 if h > w else 2
            for k in range(1, rows):
                if h > w:
                    d.line([(x + i, y + k * h / rows), (x + w - i, y + k * h / rows)], fill=EDGE, width=8)
                else:
                    d.line([(x + i, y + k * h / rows), (x + w - i, y + k * h / rows)], fill=EDGE, width=8)
            for _ in range(5 if shape == "rack" else 3):
                bx, by = x + rng.uniform(0.25, 0.75) * w, y + rng.uniform(0.15, 0.85) * h
                d.rectangle((bx - 18, by - 12, bx + 18, by + 12), fill=WARM)
        elif shape == "bunk":
            d.rectangle((x + 24, y + 24, x + w - 24, y + 0.3 * h), fill=LIGHT)
            d.rectangle((x + 24, y + 0.34 * h, x + w - 24, y + h - 24), fill=(150, 140, 120, 255))
        elif shape == "bin":
            d.rectangle((x + i, y + i, x + w - i, y + 0.2 * h), fill=WARM)
            d.ellipse((x + 0.3 * w, y + 0.35 * h, x + 0.7 * w, y + 0.75 * h), fill=EDGE)
        elif shape == "trolley":
            d.rectangle((x + 30, y + 30, x + w - 30, y + h - 30), fill=LIGHT, outline=EDGE)
            d.rectangle((x + w / 2 - 40, y + h - i - 10, x + w / 2 + 40, y + h - i), fill=DARK)
        elif shape == "terminal":
            d.rectangle((x + 34, y + 34, x + w - 34, y + 0.55 * h), fill=SCREEN)
    elif shape in ("bench", "instrument"):
        _box(d, x + i, y + i, x + w - i, y + h - i)
        n = 3 if shape == "bench" else 1
        for k in range(n):
            cx = x + (k + 0.5) * w / n
            r = min(w / n, h) * (0.22 if shape == "bench" else 0.3)
            d.ellipse((cx - r, y + h / 2 - r, cx + r, y + h / 2 + r), fill=LIGHT, outline=EDGE, width=6)
            d.ellipse((cx - r / 2, y + h / 2 - r / 2, cx + r / 2, y + h / 2 + r / 2), fill=DARK)
    elif shape == "toppled":
        d.polygon([(x + 0.06 * w, y + 0.2 * h), (x + 0.9 * w, y + 0.32 * h), (x + 0.86 * w, y + 0.7 * h),
                   (x + 0.04 * w, y + 0.6 * h)], fill=BODY, outline=EDGE)
        for _ in range(7):
            bx, by = x + rng.uniform(0.1, 0.9) * w, y + rng.uniform(0.7, 0.92) * h
            d.rectangle((bx - 12, by - 9, bx + 12, by + 9), fill=WARM)
    elif shape == "frame":
        d.rectangle((x + i, y + i, x + w - i, y + h - i), outline=EDGE, width=40)
        for cx, cy in ((0.2, 0.2), (0.8, 0.2), (0.2, 0.8), (0.8, 0.8)):
            d.rectangle((x + cx * w - 22, y + cy * h - 22, x + cx * w + 22, y + cy * h + 22), fill=BODY, outline=EDGE)
    elif shape == "gantry":
        for cx in (x + 30, x + w - 30):
            for cy in (y + 30, y + h - 30):
                d.rectangle((cx - 20, cy - 20, cx + 20, cy + 20), fill=EDGE)
        for cy in (y + 30, y + h - 30):
            d.rectangle((x + 30, cy - 12, x + w - 30, cy + 12), fill=BODY, outline=EDGE)
        d.rectangle((x + w / 2 - 14, y + 30, x + w / 2 + 14, y + h - 30), fill=BODY, outline=EDGE)
        d.rectangle((x + w / 2 - 40, y + h / 2 - 30, x + w / 2 + 40, y + h / 2 + 30), fill=LIGHT, outline=EDGE)
    elif shape == "load":
        _box(d, x + 0.1 * w, y + 0.25 * h, x + 0.55 * w, y + 0.85 * h)
        d.line([(x + 0.55 * w, y + 0.5 * h), (x + 0.7 * w, y + 0.3 * h), (x + 0.82 * w, y + 0.6 * h),
                (x + 0.92 * w, y + 0.4 * h)], fill=DARK, width=12)
    elif shape == "tipped":
        _box(d, x + i, y + 0.15 * h, x + w - i, y + 0.45 * h)
        for lx in (0.12, 0.88):
            d.rectangle((x + lx * w - 10, y + 0.45 * h, x + lx * w + 10, y + 0.85 * h), fill=EDGE)
        d.ellipse((x + 0.3 * w, y + 0.55 * h, x + 0.7 * w, y + 0.95 * h), fill=(60, 58, 54, 160))
    elif shape in ("drums", "canisters", "reels"):
        n, r = {"drums": (3, 0.3), "canisters": (5, 0.2), "reels": (2, 0.42)}[shape]
        for k in range(n):
            cx = x + (k + 0.5) * w / n
            rr = r * h
            d.ellipse((cx - rr, y + h / 2 - rr, cx + rr, y + h / 2 + rr), fill=BODY, outline=EDGE, width=6)
            d.ellipse((cx - rr / 3, y + h / 2 - rr / 3, cx + rr / 3, y + h / 2 + rr / 3), fill=LIGHT)
    elif shape in ("crates", "totes"):
        cols = 3 if shape == "crates" else 4
        for k in range(cols):
            cx0 = x + i + k * (w - 2 * i) / cols
            _box(d, cx0 + 6, y + i + 10, cx0 + (w - 2 * i) / cols - 6, y + h - i - 10, fill=BODY if k % 2 else LIGHT)
    elif shape == "rods":
        for k in range(7):
            ry = y + 0.2 * h + k * 0.1 * h
            d.rectangle((x + i + 10, ry, x + w - i - 10, ry + 0.07 * h), fill=BODY, outline=EDGE, width=3)
        for lx in (0.2, 0.8):
            d.rectangle((x + lx * w - 12, y + 0.15 * h, x + lx * w + 12, y + 0.92 * h), fill=EDGE)
    elif shape == "sorter":
        _box(d, x + i, y + i, x + w - i, y + h - i)
        d.ellipse((x + 0.3 * w, y + 0.3 * h, x + 0.7 * w, y + 0.7 * h), fill=LIGHT, outline=EDGE, width=8)
        for k in range(3):
            a = k * 2 * math.pi / 3
            d.line([(x + w / 2, y + h / 2), (x + w / 2 + 0.35 * w * math.cos(a), y + h / 2 + 0.35 * h * math.sin(a))],
                   fill=DARK, width=18)
        d.rectangle((x + 0.42 * w, y + h - 70, x + 0.58 * w, y + h - i), fill=DARK)
    return True
