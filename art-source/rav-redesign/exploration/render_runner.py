"""The Runner's art on its skeleton: `skeleton.Skeleton` drives, the round-5 layers are drawn bone by bone
from above, on the colony LZ beside Marines, at 96, 32 and 17.1 px per cell.

  python render_runner.py <render root> <out.gif> [<key frames.png>]

Layers (rounds/round-5/layers/, generated over round 5's approved top-down plate):
  body.png   torso, head and tail without limbs; split here into a head-and-neck piece and the trunk
  front.png  one front leg laid straight, shoulder at the top
  hind.png   one hind leg laid straight, hip at the top
  arm.png    one spike arm laid straight, socket at the bottom, spike at the top

Each bone takes the slice of its layer between its two joints, turned to the bone's direction as seen from
above and shortened to its projected length, so a leg swinging under the body foreshortens instead of
sliding. Legs draw under the body and arms over it. Sprites are small and turned on their own, never on a
full-frame canvas, which is what keeps a render to seconds.

<render root> holds the LZ close-ups that `motion_runner.py` uses (rav-lz96, rav-lz32, rav-lz17).
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageOps

import skeleton as sk
from boards import font
from motion import save_gif
from motion_runner import ground, key, marine_sprites
from rig import smooth
from round2 import shadow_blob

LAYERS = Path(__file__).parent / "rounds" / "round-5" / "layers"
BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)
PPC17 = 1920 / 112

# rows in the layer images (1024x1536) where each joint sits
BODY_ROWS = dict(nose=55, head=333, neck=420, shoulder=541, hip=958)
FRONT_ROWS = [120, 700, 1300]                  # shoulder, elbow, paw
HIND_ROWS = [100, 450, 870, 1300]              # hip, stifle, hock, paw
ARM_ROWS = [1330, 740, 300, 71]                # socket, elbow, wrist, spike tip (the arm points up)
BODY_PX_PER_CELL = (BODY_ROWS["hip"] - BODY_ROWS["shoulder"]) / sk.SPINE


def slices(img, rows, flip=False):
    """Cut a straight limb into one slice per bone, each with its root row at the top."""
    if flip:
        img = img.rotate(180)
        rows = [1536 - r for r in rows]
    a = np.asarray(img.getchannel("A")) > 128
    xs = np.nonzero(a.any(0))[0]
    cx = (xs.min() + xs.max()) / 2
    out = []
    for i, (r0, r1) in enumerate(zip(rows, rows[1:])):
        top = r0 - (40 if i else 90)
        bottom = r1 + (40 if i < len(rows) - 2 else 160)
        out.append((img.crop((0, max(0, top), 1024, min(1536, bottom))), (cx, r0 - max(0, top)), r1 - r0))
    return out


class Art:
    def __init__(self):
        body = key(LAYERS / "body.png")
        n = BODY_ROWS["neck"]
        self.fore = body.crop((0, 0, 1024, n + 40))
        trunk = body.copy()
        a = np.asarray(trunk.getchannel("A")).astype(float)
        ramp = np.clip((np.arange(1536) - (n - 30)) / 40, 0, 1)[:, None]
        trunk.putalpha(Image.fromarray((a * ramp).astype(np.uint8)))
        self.trunk = trunk
        self.front = slices(key(LAYERS / "front.png"), FRONT_ROWS)
        self.hind = slices(key(LAYERS / "hind.png"), HIND_ROWS)
        self.arm = slices(key(LAYERS / "arm.png"), ARM_ROWS, flip=True)
        self.cache = {}

    def scaled(self, name, img, k):
        """A layer piece at k screen px per layer px, cached per zoom."""
        ck = (name, round(k, 4))
        if ck not in self.cache:
            self.cache[ck] = img.resize((max(1, int(img.width * k)), max(1, int(img.height * k))), Image.LANCZOS)
        return self.cache[ck]


def put_bone(tile, piece, p0, p1, width_k, native_len, mirror=False, lift=1.0):
    """Draw a bone slice with its root at p0 pointing at p1 (screen px), shortened to |p1 - p0|."""
    img, (rx, ry), _ = piece
    if mirror:
        img = ImageOps.mirror(img)
        rx = img.width - rx
    length = math.hypot(p1[0] - p0[0], p1[1] - p0[1])
    ky = max(0.2, length / max(1e-6, native_len)) * width_k
    kx = width_k * lift
    w, h = max(1, int(img.width * kx)), max(1, int(img.height * ky))
    s = img.resize((w, h), Image.BILINEAR)
    rx, ry = rx * kx, ry * ky
    size = int(2 * max(w, h) + 4)
    pad = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pad.alpha_composite(s, (int(size / 2 - rx), int(size / 2 - ry)))
    phi = math.degrees(math.atan2(p1[0] - p0[0], p1[1] - p0[1]))
    pad = pad.rotate(phi, resample=Image.BICUBIC)
    tile.alpha_composite(pad, (int(p0[0] - size / 2), int(p0[1] - size / 2)))


def draw_runner(tile, art, s, ppc, to_screen):
    """Draw one skeleton frame from above. to_screen maps (x, y, z) cells to screen px."""
    k_body = ppc / BODY_PX_PER_CELL
    sh, hp = s["sh"], s["hp"]
    # legs first, under the body: from above a hound shows its paws past the shoulder and haunch plates
    for n in ("HL", "HR", "FL", "FR"):
        pts = s["legs"][n]
        mirror = n[1] == "R"
        rows, pieces = (FRONT_ROWS, art.front) if n[0] == "F" else (HIND_ROWS, art.hind)
        lift = 1 + 1.5 * pts[-1][2]
        for i, piece in enumerate(pieces):
            p0, p1 = to_screen(pts[i]), to_screen(pts[i + 1])
            seg3d = math.dist(pts[i], pts[i + 1])
            native = piece[2]
            wk = (seg3d * ppc) / native  # screen px per layer px along this bone at full length
            put_bone(tile, piece, p0, p1, wk * 1.25, native * wk, mirror, lift if i == len(pieces) - 1 else 1.0)
    # trunk: shoulder row on the shoulders, hip row on the hips
    s0, s1 = to_screen((s["shx"], sh[0], sh[1])), to_screen((s["hpx"], hp[0], hp[1]))
    trunk = (art.trunk, (512, BODY_ROWS["shoulder"]), BODY_ROWS["hip"] - BODY_ROWS["shoulder"])
    put_bone(tile, trunk, s0, s1, k_body, (BODY_ROWS["hip"] - BODY_ROWS["shoulder"]) * k_body)
    # head and neck: the neck row near the shoulders, the head row on the skeleton's head
    neck_root = (s["shx"], sh[0] + (BODY_ROWS["shoulder"] - BODY_ROWS["neck"]) / BODY_PX_PER_CELL, sh[1])
    n0, n1 = to_screen(neck_root), to_screen(s["head"])
    native_px = (BODY_ROWS["neck"] - BODY_ROWS["head"]) / BODY_PX_PER_CELL * ppc  # the head pivots, never stretches
    dl = math.hypot(n1[0] - n0[0], n1[1] - n0[1]) or 1.0
    n1 = (n0[0] + (n1[0] - n0[0]) * native_px / dl, n0[1] + (n1[1] - n0[1]) * native_px / dl)
    fore = (art.fore, (512, BODY_ROWS["neck"]), BODY_ROWS["neck"] - BODY_ROWS["head"])
    native = (BODY_ROWS["neck"] - BODY_ROWS["head"]) * k_body
    put_bone(tile, (ImageOps.flip(fore[0]), (512, fore[0].height - BODY_ROWS["neck"]), fore[2]), n0, n1, k_body,
             native)
    # spike arms on top
    for i, pts in enumerate(s["arms"]):
        mirror = i == 1
        for j, piece in enumerate(art.arm):
            p0, p1 = to_screen(pts[j]), to_screen(pts[j + 1])
            seg3d = math.dist(pts[j], pts[j + 1])
            wk = (seg3d * ppc) / piece[2]
            put_bone(tile, piece, p0, p1, wk * 1.1, piece[2] * wk, mirror)


def build(root, out):
    art = Art()
    skel = sk.Skeleton()
    n = int(sk.T_END * sk.FPS)
    raw = [skel.frame(f / sk.FPS, 1 / sk.FPS) for f in range(n)]
    hit = next(s for f, s in enumerate(raw) if f / sk.FPS >= sk.T_HOLD)
    vy, vx = hit["tips"][0][1], hit["tips"][0][0]
    marines = marine_sprites(root)
    g = {96: ground(root, 96), 32: ground(root, 32), 17: ground(root, 17)}
    action_y = vy - 0.6  # scene centre, cells along the run
    panels = [(96, (16, 74, 1256, 974)), (32, (1272, 74, 1904, 494)), (PPC17, (1272, 530, 1588, 710))]
    frames = []
    for f, s in enumerate(raw):
        t = f / sk.FPS
        img = Image.new("RGB", (1920, 1080), BG)
        d = ImageDraw.Draw(img)
        d.text((24, 10), "RAV BLOOM RUNNER  /  ART ON THE SKELETON  /  swipe, bowed stab, latch, drag",
               font=font(24, True), fill=INK)
        d.text((24, 42), "Round-5 layers drawn bone by bone on the 3D skeleton, seen from above. Colony LZ ground, "
                         "real Marine sprites, live unit shadow. Real time.", font=font(14), fill=DIM)
        for ppc, (x0, y0, x1, y1) in panels:
            zk = 96 if ppc == 96 else (32 if ppc == 32 else 17)
            W, H = x1 - x0, y1 - y0
            cx, cy = 960 + 190 * ppc / 96, 560 + 20 * ppc / 96  # a tarmac patch at the LZ
            tile = g[zk].crop((int(cx - W / 2), int(cy - H / 2), int(cx - W / 2) + W, int(cy - H / 2) + H)).copy()

            def to_screen(p, W=W, H=H, ppc=ppc):
                return W / 2 + (p[1] - action_y) * ppc, H / 2 + p[0] * ppc

            mk = ppc / 96

            def small(m, dim=1.0):
                m = m.convert("RGBa").resize((max(1, round(m.width * mk)), max(1, round(m.height * mk))),
                                             Image.LANCZOS).convert("RGBA")
                if dim != 1.0:
                    m = Image.fromarray((np.asarray(m).astype(float) * [dim, dim, dim, 1]).astype(np.uint8), "RGBA")
                return m

            def put(m, p, radius, angle=0.0):
                px, py = to_screen(p)
                sh_, (ox, oy) = shadow_blob(ppc, radius)
                tile.alpha_composite(sh_, (int(px + ox - sh_.width / 2), int(py + oy - sh_.height / 2)))
                if angle:
                    m = m.rotate(angle, expand=True, resample=Image.BICUBIC)
                tile.alpha_composite(m, (int(px - m.width / 2), int(py - m.height / 2)))

            for i, (du, dv) in enumerate([(1.3, -0.9), (1.6, 0.8), (2.2, 0.1)]):
                put(small(marines[(i + 1) % len(marines)]), (dv, vy + du, 0), 0.25)
            body_c = (0, (s["sh"][0] + s["hp"][0]) / 2, 0)
            bx, by = to_screen(body_c)
            sh_, (ox, oy) = shadow_blob(ppc, 0.34)
            tile.alpha_composite(sh_, (int(bx + ox - sh_.width / 2), int(by + oy - sh_.height / 2)))
            if t < sk.T_HOLD:
                k = smooth(min(1, max(0.0, t - sk.T_SWIPE_HIT) / 0.15))
                put(small(marines[0]), (vx + 0.1 * (1 - k), vy + 0.04 * k, 0), 0.25)
                draw_runner(tile, art, s, ppc, to_screen)
            else:
                draw_runner(tile, art, s, ppc, to_screen)
                tip = s["tips"][0]
                put(small(marines[0], 0.8), (tip[0], tip[1], 0), 0.22, 90)
                # the spike and jaws over the victim
                pts = s["arms"][0]
                put_bone(tile, art.arm[2], to_screen(pts[2]), to_screen(pts[3]),
                         math.dist(pts[2], pts[3]) * ppc / art.arm[2][2] * 1.1,
                         math.dist(pts[2], pts[3]) * ppc)
            if ppc == PPC17:
                img.paste(tile.convert("RGB").resize((W * 2, H * 2), Image.NEAREST), (x0, y0))
            else:
                img.paste(tile.convert("RGB"), (x0, y0))
        d.text((24, 980), "96 px per cell", font=font(14), fill=DIM)
        d.text((1272, 500), "32 px per cell", font=font(14), fill=DIM)
        d.text((1272, 896), "17.1 px per cell, shown x2", font=font(14), fill=DIM)
        phases = ["gallop", "brake", "side swipe", "stab wind-up", "stab", "impale", "latch", "drag off"]
        for i, ph in enumerate(phases):
            x = 1272 + (i % 4) * 158
            yy = 930 + (i // 4) * 40
            on = ph == s["phase"]
            d.rectangle([x, yy, x + 152, yy + 32], fill=ACC if on else (34, 38, 46))
            d.text((x + 8, yy + 7), ph, font=font(14, True), fill=BG if on else DIM)
        d.text((1700, 14), f"t = {t:4.2f} s", font=font(16, True), fill=DIM)
        frames.append(img)
    save_gif(frames, out, sk.FPS, accents=[(255, 168, 54), (214, 170, 214), (150, 40, 90), (236, 226, 206)])
    return frames


if __name__ == "__main__":
    fr = build(sys.argv[1], sys.argv[2])
    if len(sys.argv) > 3:
        picks = [0.3, 1.5, 1.63, 2.12, 2.26, 3.6]
        sheet = Image.new("RGB", (1920, 1080), BG)
        d = ImageDraw.Draw(sheet)
        d.text((24, 12), "RUNNER ART ON THE SKELETON  /  key frames at 96 px per cell, 1:1", font=font(22, True),
               fill=INK)
        for i, tt in enumerate(picks):
            im = fr[int(round(tt * sk.FPS))].crop((16 + 300, 74 + 230, 16 + 300 + 620, 74 + 230 + 440))
            sheet.paste(im, ((i % 3) * 636 + 12, 56 + (i // 3) * 510))
            d.text(((i % 3) * 636 + 16, 56 + (i // 3) * 510 + 444), f"t = {tt:.2f} s  {sk.Skeleton.phase(tt)}",
                   font=font(16, True), fill=ACC)
        sheet.save(sys.argv[3])
