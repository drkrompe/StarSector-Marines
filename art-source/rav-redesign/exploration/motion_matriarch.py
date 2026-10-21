"""Matriarch motion study: the charcoal Siege Crown Matriarch (round 3 #5) rigged from ImageGen layers,
in three clips:

  A  walk: a slow, heavy four-beat walk, the legs under the body
  B  reap: walking into two Marines; the right scythe sweeps across the front, then the left
  C  breach: walks up to a house, plants, shoves the wall twice with the crown (the second breaks
     it), roars, and walks through the breach

  python motion_matriarch.py <render root> <out dir>
      writes 01-A-walk.gif, 02-B-reap.gif, 03-C-breach.gif (one clip each, 64 and 32 px per cell),
      04-key-moments.png and 05-layers.png

Rig: the hero is cut into a body, the crown and head, the two scythes and four feet, so at rest the
composite is the chosen design. Under it lies body-under.png (generated over the hero: no crown, head,
scythes or legs, a neck stump) to fill what a moved part uncovers. The crown sits on top and hides the
scythes' roots, so a scythe turns about a root nobody sees. head-gape.png (generated, then scaled 0.69 to
register) replaces the head while it roars. Shanks come from leg-front.png and leg-hind.png. The gait is
matriarch_skeleton.py: planted feet, shoulder and hip sway, the body winding against a swinging blade.

<render root> holds brute-wall96/ and brute-wall32/ (see motion_brute.py) and rav-lz96/ for the Marine
sprites (see motion_runner.py).
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from boards import font
from matriarch_skeleton import (ANCHOR_Y, FPS, HP_ROW, ROAR, S_LASH, S_WIND, SH_ROW, SHOVES, SHOVE, STRIKES, T_END,
                                U_LAYER, WALK, WALL_STOP, WIND, body_offsets, simulate)
from motion import save_gif
from motion_brute import CH, CW, PAD, S, L, key, largest_blob, on_canvas, scale_about, soft_mask, with_alpha
from motion_runner import marine_sprites
from rig import ease_out
from round2 import shadow_blob

RAW = Path(__file__).parent / "motion-matriarch" / "raw"
BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)

# ----------------------------------------------------------------------------- the rig (hero layer px)
HEAD_POLY = [(150, 0), (880, 0), (860, 220), (780, 250), (700, 300), (640, 420), (590, 480), (480, 480),
             (440, 420), (330, 460), (220, 450), (150, 360)]
SCYTHE_POLY = {"R": [(690, 250), (790, 250), (860, 350), (912, 470), (908, 572), (868, 572), (845, 460),
                     (780, 370), (690, 330)],
               "L": [(410, 320), (495, 330), (492, 420), (445, 520), (405, 612), (352, 602), (348, 520),
                     (380, 420)]}
PIVOT = {"R": (705, 300), "L": (458, 360)}
FEET = {"FL": ((185, 860), (80, 95)), "FR": ((850, 885), (78, 98)),
        "HL": ((255, 1255), (82, 95)), "HR": ((775, 1300), (88, 102))}
NECK = (512, 470)
GAPE = (0.690, (52, 44), (177, 26))      # scale, source top-left, destination top-left
SHANK_W = {"F": 175, "H": 185}
LEG_ROOT_IN = 0.55       # leg roots sit this fraction of the way out to the foot, well inside the body
HEAD_TURN = 180          # degrees the crown-and-head layer is turned (owner's experiment); 0 is the design
HEAD_TURN_Y = 300        # hero row it turns about
FOOT_SCALE = 1.1


def sway_warp(img, s, U, row0=0, scale=1.0):
    """Bend a head-up body image by the shoulder offset at the shoulder row and the hip offset at the hip
    row, interpolated between and carried on past them (brute_skeleton.sway_warp with this body's rows)."""
    (sx, sy), (hx, hy) = body_offsets(s)
    a = np.asarray(img).astype(float)
    h, w = a.shape[:2]
    rows = row0 + np.arange(h) / scale
    k = np.clip((rows - SH_ROW) / (HP_ROW - SH_ROW), -0.6, 1.6)
    dx = (sx + (hx - sx) * k) * U * scale
    dy = -(sy + (hy - sy) * k) * U * scale
    a[..., :3] *= a[..., 3:4] / 255
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    sxm = np.clip(xx - dx[:, None], 0, w - 1.001)
    sym = np.clip(yy - dy[:, None], 0, h - 1.001)
    x0, y0 = sxm.astype(int), sym.astype(int)
    fx, fy = (sxm - x0)[..., None], (sym - y0)[..., None]
    out = (a[y0, x0] * (1 - fx) * (1 - fy) + a[y0, x0 + 1] * fx * (1 - fy)
           + a[y0 + 1, x0] * (1 - fx) * fy + a[y0 + 1, x0 + 1] * fx * fy)
    al = out[..., 3:4]
    out[..., :3] = np.where(al > 0, out[..., :3] * 255 / np.maximum(al, 1e-6), 0)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


def trim(img, px=3):
    """Pull a cut's alpha in by a few pixels: the keyed edge keeps a pale fringe of the plate's grey."""
    a = img.getchannel("A").filter(ImageFilter.MinFilter(2 * px + 1))
    out = img.copy()
    out.putalpha(a)
    return out


def poly_mask(size, pts, blur=4):
    return soft_mask(size, lambda d: d.polygon(pts, fill=255), blur)


class Rig:
    def __init__(self):
        hero = key(RAW / "hero.png")
        size = hero.size
        ha = np.asarray(hero.getchannel("A")).astype(float)
        head_m = np.asarray(poly_mask(size, HEAD_POLY)) / 255
        blade_m = {k: np.clip(np.asarray(poly_mask(size, p)) / 255 - head_m, 0, 1) for k, p in SCYTHE_POLY.items()}
        foot_m = {}
        for n, ((cx, cy), (rx, ry)) in FEET.items():
            foot_m[n] = np.asarray(soft_mask(size, lambda d, cx=cx, cy=cy, rx=rx, ry=ry:
                                             d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=255), 5)) / 255
        keep = 1 - head_m
        for m in list(blade_m.values()) + list(foot_m.values()):
            keep = keep * (1 - m)
        body = Image.new("RGBA", size, (0, 0, 0, 0))
        body.alpha_composite(largest_blob(key(RAW / "body-under.png")))
        body.alpha_composite(with_alpha(hero, ha * keep))
        self.body = on_canvas(body)
        self.head = on_canvas(with_alpha(hero, ha * head_m))
        k, (sx, sy), (dx, dy) = GAPE
        g = largest_blob(key(RAW / "head-gape.png"))
        g = g.resize((round(g.width * k), round(g.height * k)), Image.LANCZOS)
        gl = Image.new("RGBA", size, (0, 0, 0, 0))
        gl.alpha_composite(g, (round(dx - sx * k), round(dy - sy * k)))
        self.gape = on_canvas(gl)
        if HEAD_TURN:
            centre = L(512, HEAD_TURN_Y)
            self.head = self.head.rotate(HEAD_TURN, center=centre, resample=Image.BICUBIC)
            self.gape = self.gape.rotate(HEAD_TURN, center=centre, resample=Image.BICUBIC)
        self.neck = (512, 2 * HEAD_TURN_Y - NECK[1]) if HEAD_TURN == 180 else NECK
        top = self.head.getchannel("A").point(lambda v: 255 if v > 100 else 0).getbbox()[1]
        self.nose = (ANCHOR_Y - (top / S - PAD)) / U_LAYER       # cells from the anchor to the front
        self.blades = {s: on_canvas(with_alpha(hero, ha * m)) for s, m in blade_m.items()}
        self.feet = {}
        for n, m in foot_m.items():
            cut = trim(with_alpha(hero, ha * m))
            box = Image.fromarray((m * 255).astype(np.uint8)).point(lambda v: 255 if v > 4 else 0).getbbox()
            cut = cut.crop(box)
            cut = cut.resize((max(1, int(cut.width * S)), max(1, int(cut.height * S))), Image.LANCZOS)
            c = FEET[n][0]
            self.feet[n] = (cut, ((c[0] - box[0]) * S, (c[1] - box[1]) * S))
        self.shanks = {}
        for part, path in (("F", "leg-front.png"), ("H", "leg-hind.png")):
            leg = trim(largest_blob(key(RAW / path)))
            leg = leg.crop(leg.getchannel("A").point(lambda v: 255 if v > 60 else 0).getbbox())
            leg = leg.crop((0, 0, leg.width, int(leg.height * 0.80)))     # hip at the top, without the foot
            w = SHANK_W[part] * S
            self.shanks[part] = leg.resize((int(w), int(leg.height * w / leg.width)), Image.LANCZOS)
        self.hero_area = float((ha > 128).sum()) * S * S

    def pose(self, sk):
        c = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
        y0 = sk["y"]

        def lp(p):   # skeleton (x, y) in cells -> layer px
            return 512 + p[0] * U_LAYER, ANCHOR_Y - (p[1] - y0) * U_LAYER

        (sx, sy), (hx, hy) = body_offsets(sk)
        ends = {}
        for n in FEET:
            fx, fy, fz = sk["feet"][n]
            ox = sx if n[0] == "F" else hx
            root_y = sk["legs"][n][0][1]
            ends[n] = (L(*lp((fx * LEG_ROOT_IN + ox, root_y))), L(*lp((fx, fy))), fz)
        for n in ("HL", "HR", "FL", "FR"):   # legs first, from inside the body out past the foot's centre
            a, b, _ = ends[n]
            shank = self.shanks[n[0]]
            ln = max(1.0, math.hypot(b[0] - a[0], b[1] - a[1]))
            im = shank.resize((shank.width, int(ln * 1.25 + shank.width * 0.3)), Image.LANCZOS)
            if n[1] == "R":
                im = im.transpose(Image.FLIP_LEFT_RIGHT)
            ang = math.degrees(math.atan2(b[0] - a[0], b[1] - a[1]))
            im = im.rotate(ang, expand=True, resample=Image.BICUBIC)
            mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
            c.alpha_composite(im, (int(mx - im.width / 2), int(my - im.height / 2)))
        for n in ("HL", "HR", "FL", "FR"):   # the feet over the legs, the body over both
            _, b, z = ends[n]
            img, (ox_, oy_) = self.feet[n]
            k = FOOT_SCALE * (1 + 1.2 * z)
            im = img.resize((max(1, int(img.width * k)), max(1, int(img.height * k))), Image.LANCZOS)
            c.alpha_composite(im, (int(b[0] - ox_ * k), int(b[1] - oy_ * k)))
        c.alpha_composite(sway_warp(self.body, sk, U_LAYER * S, row0=-PAD, scale=S))
        shift = (sx * U_LAYER * S, -sy * U_LAYER * S)       # the shoulders carry the scythes and the head
        over = []
        for side in ("L", "R"):
            ang, reach = sk["blades"][side]
            img = self.blades[side]
            if abs(ang) > 0.05:
                img = img.rotate(ang if side == "R" else -ang, center=L(*PIVOT[side]), resample=Image.BICUBIC)
            out = (1 if side == "R" else -1) * reach * U_LAYER * S
            fwd = 2.2 * reach * U_LAYER * S       # the arm reaches forward as it swings, past the crown's front
            img = img.transform(img.size, Image.AFFINE, (1, 0, -(shift[0] + out), 0, 1, -(shift[1] - fwd)),
                                resample=Image.BICUBIC)
            if ang > 70:     # swung forward past the crown's rim: the blade crosses over the head
                over.append(img)
            else:
                c.alpha_composite(img)
        hd = sk["head"]
        pivot = L(*self.neck)
        h = self.gape if hd["gape"] >= 0.5 else self.head     # the two heads do not register well enough to blend
        if abs(hd["k"] - 1) > 1e-3:
            h = scale_about(h, hd["k"], pivot)
        h = h.transform(h.size, Image.AFFINE, (1, 0, -shift[0], 0, 1, -(shift[1] + hd["dy"] * S)),
                        resample=Image.BICUBIC)
        c.alpha_composite(h)
        for img in over:
            c.alpha_composite(img)
        return c


# ----------------------------------------------------------------------------- world
WALL_X = 185.0          # east face of the house at the top left of brute-wall96 (wall cells in column 184)
ROW = {"A": 186.7, "B": 186.7, "C": 181.65}
NOSE = (ANCHOR_Y - 26) / U_LAYER
FRAMES = {96: ("brute-wall96", (186, 185)), 32: ("brute-wall32", (186, 184))}
CLIPS = [("A", "walk 0.7 cells/s: four-beat, legs under the body"),
         ("B", "reap: the right scythe sweeps across the front, then the left; feet planted through each strike"),
         ("C", "breach: plants at the wall, two crown shoves, a roar, then through")]


def frame_path(root, name):
    return sorted((Path(root) / name / "visuals" / "colony-breach" / "frames").glob("frame-*.png"))[0]


class Clip:
    def __init__(self, rig, cid, n):
        self.rig, self.cid = rig, cid
        self.frames = simulate(cid, n)
        self.row = ROW[cid]
        self.x0 = {"A": 189.9, "B": 191.0, "C": WALL_X + 0.25 + rig.nose + WALK * WALL_STOP}[cid]
        self.cache = {}
        self.victims = []
        if cid == "B":   # one Marine where each blade's tip passes mid-sweep
            for side, t0 in STRIKES.items():
                th = t0 + S_WIND + 0.45 * S_LASH
                f = int(round(th * FPS))
                piv = PIVOT[side]
                tip = (890, 550) if side == "R" else (372, 600)
                vx, vy = tip[0] - piv[0], tip[1] - piv[1]
                a = math.radians(130 if side == "R" else -130)
                rx = vx * math.cos(a) + vy * math.sin(a)
                ry = -vx * math.sin(a) + vy * math.cos(a)
                px, py = piv[0] + rx, piv[1] + ry
                right = (px - 512) / U_LAYER
                fwd = (ANCHOR_Y - py) / U_LAYER
                bx = self.x0 - self.frames[f]["y"]
                self.victims.append((th, (bx - fwd, self.row - right), side))

    def draw_body(self, tile, f, ppc, origin):
        sk = self.frames[f]
        if f not in self.cache:
            self.cache = {f: self.rig.pose(sk)}
        sc = ppc / (S * U_LAYER)
        body = self.cache[f].resize((max(1, int(CW * sc)), max(1, int(CH * sc))), Image.LANCZOS)
        body = body.rotate(90, expand=True, resample=Image.BICUBIC)       # head up -> head toward -x
        ax, ay = L(512, ANCHOR_Y)
        anc = (ay * sc, (CW - ax) * sc)
        cx, cy = origin[0] + (self.x0 - sk["y"]) * ppc, origin[1] + self.row * ppc
        sh, (ox, oy) = shadow_blob(ppc, 1.0)
        tile.alpha_composite(sh, (int(cx - 0.4 * ppc + ox - sh.width / 2), int(cy + oy - sh.height / 2)))
        tile.alpha_composite(body, (int(cx - anc[0]), int(cy - anc[1])))
        return sk["phase"]


def draw_victims(tile, clip, t, ppc, origin, marines):
    mk = ppc / 96
    for i, (th, (vx, vy), side) in enumerate(clip.victims):
        m = marines[i % len(marines)]
        if t >= th:
            k = ease_out(min(1, (t - th) / 0.5))
            m = Image.fromarray((np.asarray(m).astype(float) * [0.75, 0.75, 0.75, 1]).astype(np.uint8), "RGBA")
            m = m.rotate(260 * k * (1 if side == "R" else -1), expand=True, resample=Image.BICUBIC)
            out = -1 if side == "R" else 1    # thrown off the sweep: forward and away on the far side
            vx, vy = vx - 1.0 * k, vy - out * 1.3 * k
        m = m.convert("RGBa").resize((max(1, round(m.width * mk)), max(1, round(m.height * mk))),
                                     Image.LANCZOS).convert("RGBA")
        cx, cy = origin[0] + vx * ppc, origin[1] + vy * ppc
        sh, (ox, oy) = shadow_blob(ppc, 0.25)
        tile.alpha_composite(sh, (int(cx + ox - sh.width / 2), int(cy + oy - sh.height / 2)))
        tile.alpha_composite(m, (int(cx - m.width / 2), int(cy - m.height / 2)))


def wall_damage(tile, ppc, origin, t, row):
    """Cracks after the first shove; after the second a breach 2.6 cells wide through the wall, rubble
    pushed inward, dust and chunks."""
    hit1 = SHOVES[0] + WIND + SHOVE
    hit2 = SHOVES[1] + WIND + SHOVE
    if t < hit1:
        return
    d = ImageDraw.Draw(tile)
    cx, cy = origin[0] + WALL_X * ppc, origin[1] + row * ppc
    rng = np.random.default_rng(7)
    wc = tile.crop((int(cx - 0.6 * ppc), int(cy + 1.3 * ppc), int(cx - 0.4 * ppc), int(cy + 1.4 * ppc))).convert("RGB")
    wall_c = tuple(int(v) for v in np.asarray(wc).reshape(-1, 3).mean(0))
    w = max(1, int(0.05 * ppc))
    for i in range(12):
        a = rng.uniform(-1.4, 1.4)
        r = rng.uniform(0.4, 1.3) * ppc
        d.line([(cx, cy + rng.uniform(-0.8, 0.8) * ppc), (cx - 0.2 * ppc * rng.uniform(0, 1), cy + r * math.sin(a))],
               fill=(30, 26, 24, 255), width=w)
    if t >= hit2:
        x0, x1 = cx - 1.0 * ppc, cx + 0.05 * ppc
        y0, y1 = cy - 1.3 * ppc, cy + 1.3 * ppc
        fl = tile.crop((int(x0 - 0.5 * ppc), int(cy - 0.3 * ppc), int(x0 - 0.3 * ppc), int(cy - 0.2 * ppc))).convert("RGB")
        d.rectangle([x0, y0, x1, y1], fill=tuple(int(v) for v in np.asarray(fl).reshape(-1, 3).mean(0)) + (255,))
        for i in range(60):
            rx = cx + rng.uniform(-2.2, 0.4) * ppc
            ry = cy + rng.normal(0, 0.8) * ppc
            s = rng.uniform(0.05, 0.15) * ppc
            sh = rng.uniform(0.6, 1.0)
            pts = [(rx + s * rng.uniform(0.7, 1.2) * math.cos(k * 1.26 + i), ry + s * rng.uniform(0.7, 1.2)
                    * math.sin(k * 1.26 + i)) for k in range(5)]
            d.polygon([(px + 0.02 * ppc, py + 0.02 * ppc) for px, py in pts], fill=(40, 30, 24, 160))
            d.polygon(pts, fill=tuple(int(c * sh) for c in wall_c) + (255,))
    for hit, big in ((hit1, 0.9), (hit2, 1.8)):
        u = t - hit
        if 0 <= u < 1.0:
            dust = Image.new("RGBA", tile.size, (0, 0, 0, 0))
            dd = ImageDraw.Draw(dust)
            for i in range(12):
                a = -math.pi / 2 + (i - 5.5) * 0.28
                r = (0.3 + 1.0 * ease_out(u)) * big * ppc
                px, py = cx + 0.3 * r * math.cos(a) + 0.2 * r, cy + r * math.sin(a) * 1.3
                rad = (0.3 + 0.5 * u) * big * ppc
                dd.ellipse([px - rad, py - rad, px + rad, py + rad], fill=(176, 168, 156, int(140 * (1 - u))))
            tile.alpha_composite(dust.filter(ImageFilter.GaussianBlur(0.15 * ppc)))


def build(root, cid, out, title="MOTION STUDY 2 / head turned 180"):
    """One clip per GIF (the three together pass the 8 MB preview limit): 64 px per cell at left, 32 px per
    cell (play zoom) at right. Returns the frames, the clip and the panel's top row in the ground frame."""
    rig = Rig()
    marines = marine_sprites(root)
    g96 = Image.open(frame_path(root, FRAMES[96][0])).convert("RGBA")
    g64 = g96.resize((1280, 720), Image.LANCZOS)                       # the 96 px frame shown at 64 px per cell
    g32 = Image.open(frame_path(root, FRAMES[32][0])).convert("RGBA")
    o64 = (-(FRAMES[96][1][0] * 64) + 640, -(FRAMES[96][1][1] * 64) + 360)
    o32 = (-(FRAMES[32][1][0] * 32) + 960, -(FRAMES[32][1][1] * 32) + 540)
    n = int(T_END * FPS)
    clip = Clip(rig, cid, n)
    ttl = dict(CLIPS)[cid]
    W64, H64, W32 = 1280, 696, 600
    top96 = 24                                                          # below the frame's HUD strip
    frames = []
    for f in range(n):
        t = f / FPS
        img = Image.new("RGB", (1920, 1080), BG)
        d = ImageDraw.Draw(img)
        d.text((24, 10), f"RAV MATRIARCH  /  {title}  /  {cid}  {ttl}", font=font(22, True), fill=INK)
        d.text((24, 40), "Real time. Left: 64 px per cell. Right: 32 px per cell (play zoom). Charcoal Siege Crown "
                         "(round 3 #5), crown and head turned 180 degrees; Marines for scale.", font=font(14), fill=DIM)
        tile = g64.copy()
        if cid == "C":
            wall_damage(tile, 64, o64, t, clip.row)
        if cid == "B":
            draw_victims(tile, clip, t, 64, o64, marines)
        phase = clip.draw_body(tile, f, 64, o64)
        img.paste(tile.crop((0, top96, W64, top96 + H64)).convert("RGB"), (12, 90))
        t32 = g32.copy()
        if cid == "C":
            wall_damage(t32, 32, o32, t, clip.row)
        if cid == "B":
            draw_victims(t32, clip, t, 32, o32, marines)
        clip.draw_body(t32, f, 32, o32)
        cx32, cy32 = o32[0] + 186.5 * 32, o32[1] + clip.row * 32
        box = (int(cx32 - W32 / 2), int(cy32 - H64 / 2))
        img.paste(t32.crop((box[0], box[1], box[0] + W32, box[1] + H64)).convert("RGB"), (1308, 90))
        d.text((12, 68), "64 px per cell", font=font(14, True), fill=DIM)
        d.text((1308, 68), "32 px per cell (play zoom)", font=font(14, True), fill=DIM)
        d.text((1500, 12), phase, font=font(20, True), fill=ACC)
        d.text((1780, 44), f"t = {t:4.2f} s", font=font(16, True), fill=DIM)
        frames.append(img)
    save_gif(frames, out, FPS, accents=[(255, 168, 54), (60, 56, 58), (120, 24, 34), (236, 226, 206)])
    return frames, clip, top96


def key_sheet(runs, out):
    """runs: cid -> (frames, clip, top96). Key moments cropped around the Matriarch at 96 px per cell."""
    picks = [("A", 1.0, "walk"), ("B", STRIKES["R"] + S_WIND * 0.9, "right scythe cocked"),
             ("B", STRIKES["R"] + S_WIND + S_LASH + 0.05, "right reap"),
             ("B", STRIKES["L"] + S_WIND + S_LASH + 0.05, "left reap"),
             ("C", SHOVES[0] + WIND * 0.9, "rock back"), ("C", SHOVES[1] + WIND + SHOVE + 0.1, "shove 2: breach"),
             ("C", ROAR[0] + ROAR[1] / 2, "roar"), ("C", 6.2, "through")]
    sheet = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(sheet)
    d.text((24, 10), "MATRIARCH MOTION STUDY  /  key moments at 64 px per cell, 1:1", font=font(22, True), fill=INK)
    for i, (cid, tt, lab) in enumerate(picks):
        frames, clip, top96 = runs[cid]
        fi = min(len(frames) - 1, int(round(tt * FPS)))
        bx = clip.x0 - clip.frames[fi]["y"] - 0.4           # body centre, cells
        px = int((bx - FRAMES[96][1][0]) * 64 + 640)         # in the panel
        py = int((clip.row - FRAMES[96][1][1]) * 64 + 360) - top96
        x0 = max(0, min(1280 - 460, px - 230))
        y0 = max(0, min(696 - 460, py - 230))
        crop = frames[fi].crop((12 + x0, 90 + y0, 12 + x0 + 460, 90 + y0 + 460))
        x, y = 12 + (i % 4) * 476, 56 + (i // 4) * 506
        sheet.paste(crop, (x, y + 26))
        d.text((x, y + 2), f"{cid}  t = {tt:.2f} s  {lab}", font=font(15, True), fill=ACC)
    sheet.save(out)


def layers_sheet(out):
    sheet = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(sheet)
    d.text((24, 16), "MATRIARCH  /  design and rig layers", font=font(24, True), fill=INK)
    names = [("hero", "design: round 3 #5"), ("body-under", "layer: body"), ("head", "layer: crown and head"),
             ("head-gape", "layer: roar"), ("scythe", "layer: scythe"), ("leg-front", "layer: front leg"),
             ("leg-hind", "layer: hind leg")]
    for i, (n_, lab) in enumerate(names):
        im = Image.open(RAW / f"{n_}.png").convert("RGB")
        k = min(266 / im.width, 900 / im.height)
        im = im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)
        sheet.paste(im, (12 + i * 272, 64))
        d.text((16 + i * 272, 74 + im.height), lab, font=font(14, True), fill=ACC)
    sheet.save(out)


if __name__ == "__main__":
    root, out_dir = sys.argv[1], Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    runs = {}
    for i, (cid, _) in enumerate(CLIPS):
        runs[cid] = build(root, cid, out_dir / f"0{i + 1}-{cid}-{['walk', 'reap', 'breach'][i]}.gif")
    key_sheet(runs, out_dir / "04-key-moments.png")
    layers_sheet(out_dir / "05-layers.png")
