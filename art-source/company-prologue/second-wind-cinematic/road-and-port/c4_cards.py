"""C4 moving-element cards: silhouette guides for Codex, then cutting the painted masters.

    python c4_cards.py guides   # guides/cards/c4-haulers-guide.png, c4-air-guide.png
    python c4_cards.py cut      # assets/c4-*-v*.png -> cards/*.png (RGBA, final size) + cards.json

Moving things are never baked into plates (c4-layout.md). Each card is painted large from a
silhouette whose proportions and view angle are the shot's: the haulers and the sprayer are
seen from 220 m up and about 900 m out, a 12.7 degree look-down, so a card shows the side
face and a sliver of roof; the lifter climbs 6.6 km away and is seen side-on. The guide owns
the silhouette; the game's hauler (`art-source/vehicles/hauler/`) and the C1 Atlas own the
identities.

Each master is painted on a flat chroma green so the cut can key it: alpha is the guide's
silhouette (grown a little) refined by distance from the key colour.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c4_camera as cam  # noqa: E402

KEY = (0, 177, 64)
W, H = 1886, 834
GDIR, ASSETS, CARDS = BASE / "guides" / "cards", BASE / "assets", BASE / "cards"
LOOK_DOWN = math.degrees(math.atan(cam.ALTITUDE / 980.0))     # 12.7 degrees at the road

# Hauler: a Wayline road train, nose right. Metres along the length, from the tail.
HAULER = dict(length=32.0, module=12.0, coupling=0.8, saddle=1.2, cab=6.0, height=4.2, cab_h=3.8, chassis=1.2,
              width=2.6)
ROWS, COLS = 4, 2
VARIANTS = ["loaded", "loaded", "loaded", "loaded", "loaded", "empty", "empty", "empty"]


def hauler_rects():
    """Sheet rectangle (x0, y0, x1, y1) of each hauler slot and the px-per-metre."""
    k = 27.5
    out = []
    for i in range(ROWS * COLS):
        c, r = i % COLS, i // COLS
        x0 = 30 + c * 943
        y0 = 30 + r * 200
        out.append((x0, y0, x0 + HAULER["length"] * k, y0 + 170))
    return out, k


def draw_hauler(d, x0, base, k, empty, sliver):
    """Side elevation, nose right, with the roof sliver a 12.7 degree look-down shows."""
    g = HAULER
    shade_side, shade_roof, dark, wheel = (150, 150, 146), (196, 196, 190), (70, 72, 74), (40, 40, 42)

    def box(a, b, z0, z1, side=shade_side):
        d.rectangle([x0 + a * k, base - z1 * k, x0 + b * k, base - z0 * k], fill=side)
        d.rectangle([x0 + a * k, base - z1 * k - sliver, x0 + b * k, base - z1 * k], fill=shade_roof)

    x = 0.0
    for m in range(2):
        a, b = x, x + g["module"]
        box(a, b, 0.55, g["chassis"], dark)                                  # chassis rail and skirt
        for ax in (a + 1.4, a + 2.8, b - 2.8, b - 1.4):                      # wheels under the skirt
            d.ellipse([x0 + (ax - 0.55) * k, base - 1.1 * k, x0 + (ax + 0.55) * k, base], fill=wheel)
        box(a, b, 0.55, g["chassis"], dark)
        if empty:
            box(a + 0.2, b - 0.2, g["chassis"], g["chassis"] + 0.25, (120, 112, 100))   # bare deck
            for s in np.linspace(a + 0.4, b - 0.6, 5):                       # stanchions
                d.rectangle([x0 + s * k, base - (g["chassis"] + 1.6) * k, x0 + (s + 0.2) * k, base - g["chassis"] * k],
                            fill=dark)
        else:
            box(a + 0.2, b - 0.2, g["chassis"], g["height"])                 # freight module
            d.rectangle([x0 + (a + 0.2) * k, base - g["height"] * k, x0 + (b - 0.2) * k,
                         base - (g["height"] - 0.35) * k], fill=(178, 118, 70))   # shoulder rail
        x = b + g["coupling"]
    x -= g["coupling"]
    box(x, x + g["saddle"], 0.55, 2.6, dark)                                 # machinery saddle
    x += g["saddle"]
    a, b = x, x + g["cab"]
    box(a, b, 0.55, g["chassis"], dark)
    for ax in (a + 1.2, b - 1.5):
        d.ellipse([x0 + (ax - 0.6) * k, base - 1.2 * k, x0 + (ax + 0.6) * k, base], fill=wheel)
    box(a, b, 0.55, g["chassis"], dark)
    d.polygon([(x0 + a * k, base - g["chassis"] * k), (x0 + a * k, base - g["cab_h"] * k),
               (x0 + (b - 0.9) * k, base - g["cab_h"] * k), (x0 + b * k, base - (g["cab_h"] - 0.9) * k),
               (x0 + b * k, base - g["chassis"] * k)], fill=(132, 138, 118))
    d.polygon([(x0 + a * k, base - g["cab_h"] * k - sliver), (x0 + (b - 1.0) * k, base - g["cab_h"] * k - sliver),
               (x0 + (b - 0.9) * k, base - g["cab_h"] * k), (x0 + a * k, base - g["cab_h"] * k)], fill=shade_roof)
    d.rectangle([x0 + (b - 1.6) * k, base - (g["cab_h"] - 0.5) * k, x0 + (b - 0.25) * k,
                 base - (g["cab_h"] - 1.4) * k], fill=(60, 74, 84))           # windscreen band


def guides():
    GDIR.mkdir(parents=True, exist_ok=True)
    rects, k = hauler_rects()
    im = Image.new("RGB", (W, H), KEY)
    d = ImageDraw.Draw(im)
    sliver = HAULER["width"] * math.sin(math.radians(LOOK_DOWN)) * k
    for (x0, y0, x1, y1), v in zip(rects, VARIANTS):
        draw_hauler(d, x0, y1 - 8, k, v == "empty", sliver)
    im.save(GDIR / "c4-haulers-guide.png")

    # air sheet: the Atlas-class lifter nose-up (left) and the crop tender (right)
    im = Image.new("RGB", (W, H), KEY)
    d = ImageDraw.Draw(im)
    s = 3.5                                       # px per metre for the lifter
    cx, top = 160, 30
    L, B = 220 * s, 34 * s
    d.rectangle([cx - B / 2, top + 40, cx + B / 2, top + L - 70], fill=(150, 150, 150))          # container body
    d.polygon([(cx - B / 2, top + 40), (cx + B / 2, top + 40), (cx + B * 0.28, top), (cx - B * 0.28, top)],
              fill=(176, 176, 172))                                                             # bridge nose
    for ox in (-0.32, 0.0, 0.32):                                                               # engine cluster
        d.rectangle([cx + ox * B - 18, top + L - 70, cx + ox * B + 18, top + L], fill=(110, 110, 112))
    for yy in np.arange(top + 70, top + L - 80, 48):                                            # container bays
        d.line([cx - B / 2, yy, cx + B / 2, yy], fill=(120, 120, 120), width=4)
    # crop tender: 12 m body, four lift pods, a reagent tank, a spray boom slung beneath
    k2 = 52.0
    x0, base = 700, 640
    d.rectangle([x0 + 1.0 * k2, base - 3.4 * k2, x0 + 11.0 * k2, base - 1.6 * k2], fill=(196, 176, 92))       # body
    d.ellipse([x0 + 3.0 * k2, base - 4.6 * k2, x0 + 8.8 * k2, base - 2.9 * k2], fill=(205, 205, 200))          # tank
    for px in (0.0, 9.6):
        d.rounded_rectangle([x0 + px * k2, base - 3.0 * k2, x0 + (px + 2.4) * k2, base - 1.8 * k2], 18,
                            fill=(90, 92, 96))                                                                # lift pods
    d.rectangle([x0 - 4.0 * k2, base - 1.25 * k2, x0 + 16.0 * k2, base - 1.05 * k2], fill=(70, 70, 72))       # boom
    for nx in np.linspace(-3.6, 15.6, 17):
        d.rectangle([x0 + nx * k2, base - 1.05 * k2, x0 + (nx + 0.08) * k2, base - 0.7 * k2], fill=(70, 70, 72))
    d.rectangle([x0 + 5.6 * k2, base - 1.6 * k2, x0 + 6.2 * k2, base - 1.25 * k2], fill=(70, 70, 72))         # boom hanger
    im.save(GDIR / "c4-air-guide.png")
    meta = dict(key=KEY, look_down_deg=round(LOOK_DOWN, 2), hauler=dict(rects=rects, px_per_m=k, variants=VARIANTS),
                lifter=dict(box=[cx - B / 2 - 20, top - 10, cx + B / 2 + 20, top + L + 10], px_per_m=s, length_m=220),
                tender=dict(box=[x0 - 4.0 * k2 - 20, base - 4.8 * k2, x0 + 16.0 * k2 + 20, base - 0.5 * k2],
                            px_per_m=k2))
    (GDIR / "cards-guides.json").write_text(json.dumps(meta, indent=1) + "\n", encoding="utf-8")
    print("card guides written; look-down %.1f deg" % LOOK_DOWN)


# ------------------------------------------------------------------ cut

def latest(stem):
    vs = sorted(ASSETS.glob(stem + "-v*.png"), key=lambda p: int(p.stem.rsplit("-v", 1)[1]))
    return vs[-1] if vs else None


def key_alpha(rgb, guide_rgb):
    """Alpha from the painted master: far from the chroma key and inside the grown silhouette."""
    key = np.array(KEY, np.float32) / 255
    dist = np.abs(rgb - key).sum(-1)
    a = np.clip((dist - 0.25) / 0.35, 0, 1)
    sil = (np.abs(guide_rgb - key).sum(-1) > 0.1).astype(np.uint8) * 255
    sil = np.asarray(Image.fromarray(sil).filter(ImageFilter.MaxFilter(9)), np.float32) / 255
    a = a * sil
    # despill: pull green toward the other two channels where the key bled in
    g = np.minimum(rgb[..., 1], np.maximum(rgb[..., 0], rgb[..., 2]) * 1.02)
    rgb = rgb.copy()
    rgb[..., 1] = np.where(a < 0.999, g, rgb[..., 1])
    return rgb, a


def crop_card(rgb, a, box, out_w=None, out_h=None):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    sub = np.dstack([rgb[y0:y1, x0:x1], a[y0:y1, x0:x1]])
    im = Image.fromarray((np.clip(sub, 0, 1) * 255).astype(np.uint8), "RGBA")
    bb = im.getchannel("A").point(lambda v: 255 if v > 20 else 0).getbbox()
    im = im.crop(bb)
    if out_w or out_h:
        w = out_w or round(im.width * out_h / im.height)
        h = out_h or round(im.height * out_w / im.width)
        im = im.resize((w, h), Image.LANCZOS)
    return im


def cut():
    CARDS.mkdir(exist_ok=True)
    meta = json.loads((GDIR / "cards-guides.json").read_text(encoding="utf-8"))
    out = {}
    src = latest("c4-haulers")
    if src:
        rgb = np.asarray(Image.open(src).convert("RGB").resize((W, H), Image.LANCZOS), np.float32) / 255
        g = np.asarray(Image.open(GDIR / "c4-haulers-guide.png").convert("RGB"), np.float32) / 255
        rgb, a = key_alpha(rgb, g)
        for i, (box, v) in enumerate(zip(meta["hauler"]["rects"], meta["hauler"]["variants"])):
            x0, y0, x1, y1 = box
            card = crop_card(rgb, a, (x0 - 10, y0 - 15, x1 + 10, y1 + 2))
            # 4x the on-screen size at the road (67 px long there); compose scales it by depth
            card = card.resize((round(cam.px_per_m(980.0) * 32 * 4), round(card.height * cam.px_per_m(980.0) * 32 * 4
                                                                            / card.width)), Image.LANCZOS)
            name = "hauler-%s-%d.png" % (v, i)
            card.save(CARDS / name)
            out[name] = dict(kind="hauler", variant=v, length_m=32.0, px_per_m=card.width / 32.0, source=src.name)
    src = latest("c4-air")
    if src:
        rgb = np.asarray(Image.open(src).convert("RGB").resize((W, H), Image.LANCZOS), np.float32) / 255
        g = np.asarray(Image.open(GDIR / "c4-air-guide.png").convert("RGB"), np.float32) / 255
        rgb, a = key_alpha(rgb, g)
        lif = crop_card(rgb, a, meta["lifter"]["box"])
        lif = lif.resize((round(lif.width * 300 / lif.height), 300), Image.LANCZOS)      # 4x its ~69 px
        lif.save(CARDS / "lifter.png")
        out["lifter.png"] = dict(kind="lifter", length_m=220.0, px_per_m=lif.height / 220.0, source=src.name)
        ten = crop_card(rgb, a, meta["tender"]["box"])
        ten.save(CARDS / "tender.png")
        out["tender.png"] = dict(kind="tender", span_m=24.0, px_per_m=ten.width / 24.0, source=src.name)
    (CARDS / "cards.json").write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
    print("cards:", ", ".join(out))


if __name__ == "__main__":
    {"guides": guides, "cut": cut}.get(sys.argv[1] if len(sys.argv) > 1 else "", lambda: print(__doc__))()
