"""Motion study GIF for one direction, 1920x1080 at 100% scale.

  python motion.py <frame.png from a colony-breach render> <out dir> A|B|C [...]

Panels: Runner walk (1x), Runner strike (0.5x), Brute strike and Spitter spit
(0.5x), and a strip at true battle zoom (17.14 px per cell) on real colony
ground with real Marines cut from the render, plus a x4 nearest-neighbour inset
of the same pixels. The ground frame is a regenerable render; pass its path.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from review_font import review_font  # noqa: E402

from creatures import DIRECTIONS, st  # noqa: E402
from rig import render  # noqa: E402

CELL = 1920 / 112  # 17.142857: battle zoom
FPS = 20
FRAMES = 48  # 2.4 s loop
WALK_PERIOD = 0.6  # s per gait cycle at 1x
STRIKE_PERIOD = 0.6  # s per strike at 1x (anticipation 0.2 s, lash 0.06 s)
BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)

PHASES = [(0.0, "anticipation"), (0.34, "lash"), (0.44, "impact"), (0.58, "recover")]


def phase_name(s):
    name = PHASES[0][1]
    for t, n in PHASES:
        if s >= t:
            name = n
    return name


def marine_cutouts(frame_path, region=(850, 960, 1100, 1080)):
    """Marine sprites from a colony-breach frame: pixels that differ from tick 0."""
    frame = Image.open(frame_path).convert("RGB")
    first = Path(frame_path).with_name("frame-0000-tick-000000.png")
    a = np.asarray(Image.open(first).convert("RGB")).astype(int)
    b = np.asarray(frame).astype(int)
    m = (np.abs(a - b).sum(2) > 40)
    x0, y0, x1, y1 = region
    sub = m[y0:y1, x0:x1]
    seen = np.zeros_like(sub)
    outs = []
    for y in range(sub.shape[0]):
        for x in range(sub.shape[1]):
            if sub[y, x] and not seen[y, x]:
                stack = [(y, x)]
                pts = []
                seen[y, x] = 1
                while stack:
                    cy, cx = stack.pop()
                    pts.append((cy, cx))
                    for dy in (-2, -1, 0, 1, 2):
                        for dx in (-2, -1, 0, 1, 2):
                            ny, nx = cy + dy, cx + dx
                            if 0 <= ny < sub.shape[0] and 0 <= nx < sub.shape[1] and sub[ny, nx] and not seen[ny, nx]:
                                seen[ny, nx] = 1
                                stack.append((ny, nx))
                if len(pts) < 25:
                    continue
                ys = [p[0] for p in pts]
                xs = [p[1] for p in pts]
                bx0, by0, bx1, by1 = min(xs) + x0, min(ys) + y0, max(xs) + x0 + 1, max(ys) + y0 + 1
                crop = frame.crop((bx0, by0, bx1, by1)).convert("RGBA")
                alpha = Image.fromarray((m[by0:by1, bx0:bx1] * 255).astype(np.uint8))
                crop.putalpha(alpha)
                outs.append(crop)
    return outs


def ground(frame_path, box):
    first = Path(frame_path).with_name("frame-0000-tick-000000.png")
    return Image.open(first).convert("RGBA").crop(box)


def text(d, xy, s, size=20, bold=False, fill=INK):
    d.text(xy, s, font=review_font(size, bold), fill=fill)


def panel_bg(img, box, col=(58, 54, 50)):
    d = ImageDraw.Draw(img)
    d.rectangle(box, fill=col)


def scroll_marks(img, box, offset_px, col=(74, 70, 64)):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    step = 60
    for y in range(int(y0 - step + offset_px % step), y1, step):
        for x in range(x0 + 20, x1, step):
            if y0 < y < y1 - 6:
                d.rectangle([x, y, x + 18, y + 4], fill=col)


def save_gif(frames, out, fps):
    """One shared palette; every frame after the first stores only the pixels
    that changed (the rest transparent over the previous frame), so the static
    panels cost nothing and the GIF stays under the preview limit."""
    picks = frames[:: max(1, len(frames) // 6)]
    w, h = picks[0].size
    sheet = Image.new("RGB", (w, h * len(picks)))
    for i, fr in enumerate(picks):
        sheet.paste(fr, (0, h * i))
    ref = sheet.resize((w // 2, h * len(picks) // 2), Image.NEAREST).quantize(colors=255, method=Image.MEDIANCUT)
    palette = ref.getpalette()[: 255 * 3] + [0, 0, 0]
    arrays = [np.asarray(fr.quantize(palette=ref, dither=Image.NONE)) for fr in frames]
    images = []
    prev = None
    for a in arrays:
        b = a.copy()
        if prev is not None:
            b[a == prev] = 255
        prev = a
        im = Image.fromarray(b.astype(np.uint8), "P")
        im.putpalette(palette)
        im.info["transparency"] = 255
        images.append(im)
    images[0].save(out, save_all=True, append_images=images[1:], duration=int(1000 / fps), loop=0,
                   disposal=1, transparency=255, optimize=False)


def build(key, frame_path, out_dir):
    D = DIRECTIONS[key]
    marines = marine_cutouts(frame_path)
    # ground strip at battle zoom: open paving south of the blocks
    gbox = (420, 820, 1240, 1080)
    gnd = ground(frame_path, gbox)
    frames = []
    for f in range(FRAMES):
        t = f / FPS
        img = Image.new("RGBA", (1920, 1080), BG + (255,))
        d = ImageDraw.Draw(img)
        text(d, (40, 18), f"RAV BLOOM  /  DIRECTION {key}: {D['name']}  /  motion study", 30, True)
        text(d, (40, 56), "procedural rig standing in for layered art: segment timing, reach and secondary motion are the subject, not the finish", 18, fill=DIM)

        # --- walk, 1x
        box = (40, 90, 620, 690)
        panel_bg(img, box)
        w = (t / WALK_PERIOD) % 1
        scroll_marks(img, box, t * 1.0 * 3.0 * 220)  # ~3 cells/s
        im = render(D["runner"](st(w=w, move=1.0, tau=t)), 220, (2.5, 2.6), (0.5, 0.6))
        img.alpha_composite(im, (box[0] + (580 - im.width) // 2, box[1] + 40))
        text(d, (box[0] + 14, box[1] + 10), "Runner walk  ·  1x  ·  one gait cycle 0.6 s", 18, True)

        # --- strike, 0.5x
        box = (660, 90, 1240, 690)
        panel_bg(img, box)
        s = (t * 0.5 / STRIKE_PERIOD) % 1
        im = render(D["runner"](st(s=s, tau=t)), 220, (2.5, 2.5), (0.5, 0.64))
        ox, oy = box[0] + (580 - im.width) // 2, box[1] + 60
        # target ring one cell ahead of the body centre
        cx, cy = ox + im.width * 0.5, oy + im.height * 0.64 - 1.05 * 220
        d.ellipse([cx - 26, cy - 26, cx + 26, cy + 26], outline=(120, 190, 230), width=3)
        d.line([ox + 20, oy + im.height * 0.64, box[2] - 20, oy + im.height * 0.64], fill=(80, 76, 70), width=2)
        img.alpha_composite(im, (ox, oy))
        text(d, (box[0] + 14, box[1] + 10), f"Runner strike  ·  0.5x  ·  {phase_name(s)}", 18, True)
        text(d, (box[0] + 14, box[1] + 34), "ring: target one cell ahead; line: body centre at rest", 16, fill=DIM)

        # --- brute & spitter, 0.5x
        box = (1280, 90, 1880, 390)
        panel_bg(img, box)
        sb = (t * 0.5 / 0.8) % 1
        im = render(D["brute"](st(s=sb, tau=t)), 105, (2.4, 2.6), (0.5, 0.58))
        img.alpha_composite(im, (box[0] + 20, box[1] + 5))
        text(d, (box[0] + 290, box[1] + 20), "Brute blow", 18, True)
        text(d, (box[0] + 290, box[1] + 46), f"0.5x  ·  {phase_name(sb)}", 16, fill=DIM)
        box = (1280, 400, 1880, 690)
        panel_bg(img, box)
        sp = (t * 0.5 / 0.8) % 1
        im = render(D["spitter"](st(s=sp, tau=t)), 120, (2.2, 2.3), (0.5, 0.6))
        img.alpha_composite(im, (box[0] + 20, box[1] + 5))
        text(d, (box[0] + 290, box[1] + 20), "Spitter spit", 18, True)
        text(d, (box[0] + 290, box[1] + 46), f"0.5x  ·  {phase_name(sp)}", 16, fill=DIM)

        # --- battle zoom strip, 1x
        sx, sy = 40, 720
        g = gnd.copy()
        # marines hold the right side
        mpos = [(640, 120), (672, 150), (650, 186), (700, 104)]
        for k, (mx, my) in enumerate(mpos):
            m = marines[k % len(marines)]
            g.alpha_composite(m, (mx - m.width // 2, my - m.height // 2))
        # pack: runners advance then strike; brute and spitter behind
        speed = 3.0 * CELL  # px/s
        pack = [("runner", (578, 122), 0.0), ("runner", (586, 152), 0.25), ("runner", (562, 186), 0.5),
                ("brute", (430, 140), 0.1), ("spitter", (400, 100), 0.3)]
        for kind, (px, py), ph in pack:
            tt = (t + ph * 0.6) % 2.4
            walk_t = min(tt, 1.2)
            x = px + walk_t * speed * (0.6 if kind == "brute" else 1.0)
            strike = None
            if kind == "runner" and tt > 1.2:
                strike = ((tt - 1.2) / STRIKE_PERIOD) % 1
            if kind == "spitter":
                x = px + walk_t * speed * 0.3
                if tt > 1.2:
                    strike = ((tt - 1.2) / 0.8) % 1
            mv = 1.0 if tt <= 1.2 else 0.0
            S = st(w=(walk_t * speed / CELL) % 1, move=mv, s=strike, tau=t + ph)
            im = render(D[kind](S), CELL, (5, 5), (0.5, 0.5), yaw=90, ss=6)
            g.alpha_composite(im, (int(x - im.width / 2), int(py - im.height / 2)))
        img.alpha_composite(g, (sx, sy))
        text(d, (sx, sy + g.height + 6), "True battle zoom (17.1 px per cell), real colony ground and Marines from a colony-breach render", 16, fill=DIM)
        # x4 inset of the strike pair
        inset = g.crop((500, 92, 740, 174)).resize((960, 328), Image.NEAREST)
        img.alpha_composite(inset, (900, 720))
        d.rectangle([900, 720, 1860, 1050], outline=(90, 96, 104), width=2)
        text(d, (906, 1054), "same pixels x4 (nearest)", 16, fill=DIM)
        frames.append(img.convert("RGB"))

    out = Path(out_dir) / f"motion-{key}.gif"
    save_gif(frames, out, FPS)
    return out


if __name__ == "__main__":
    for k in sys.argv[3:]:
        print(build(k, sys.argv[1], sys.argv[2]))
