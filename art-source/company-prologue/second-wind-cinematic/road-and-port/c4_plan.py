"""C4 panorama plan: a top-down map of the world and the layer guides with the window's
footprint at the start, middle and end (guides/c4-panorama-plan.png). Diagnostic only.

    python c4_plan.py
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c4_camera as cam  # noqa: E402
import c4_world as world  # noqa: E402

HORIZON_SKY = (204, 218, 232)


def load_layers():
    out = []
    for L in cam.LAYERS:
        if math.isinf(L["d_rep"]):
            continue
        im = Image.open(BASE / "guides" / ("c4-%s-guide.png" % L["id"])).convert("RGBA")
        im = im.resize((im.width // cam.PLATE_SCALE, im.height // cam.PLATE_SCALE), Image.LANCZOS)
        out.append((L, im))
    return out


# ------------------------------------------------------------------ plan

LAYER_COLOR = {"L1-far": (120, 150, 200), "L2-port": (200, 140, 90), "L3-mid": (120, 180, 110), "L4-near": (230, 210, 90),
               "L4f-fore": (240, 160, 200)}


def panorama_plan(layers):
    font = ImageFont.load_default()
    W = 2400
    strip_h = []
    strips = []
    for L, im in layers:
        box = im.getchannel("A").getbbox() if L["id"] != "L1-far" else (0, 0, im.width, int(cam.ground_y(L["y_band"][0])) + 20)
        y0, y1 = max(0, box[1] - 10), min(im.height, box[3] + 10)
        s = W / im.width if L["id"] == "L4f-fore" else W / layers[-1][1].width
        crop = im.crop((0, y0, im.width, y1))
        crop = crop.resize((round(crop.width * s), round(crop.height * s)), Image.LANCZOS)
        strips.append((L, crop, s, y0))
        strip_h.append(crop.height)
    map_h = 900
    H = map_h + sum(h + 40 for h in strip_h) + 60
    out = Image.new("RGB", (W, H), (28, 30, 34))
    d = ImageDraw.Draw(out)
    # top-down map: X across, sqrt(Y) down, with the camera track and three view wedges
    X0, X1, YMAX = -1600.0, 5600.0, 9000.0

    def mp(x, y):
        return 60 + (x - X0) / (X1 - X0) * (W - 120), 60 + math.sqrt(max(0.0, y) / YMAX) * (map_h - 120)

    d.text((60, 18), "C4 top-down plan (X along the flight track; distance outward on a square-root scale). "
                     "Camera track at Y=0, 220 m up, %.0f m/s for %.0f s." % (cam.SPEED, cam.DURATION),
           fill=(230, 230, 230), font=font)
    for p in world.PRIMS:
        col = LAYER_COLOR[p["layer"]]
        if p["kind"] in ("box", "plane", "prism"):
            (a, b), (c, e) = p["x"], p["y"]
            if p["kind"] == "plane" and p["mat"] in ("ground", "plain"):
                continue
            x0, y0 = mp(a, c)
            x1, y1 = mp(b, e)
            if p["mat"] in ("road",):
                d.rectangle([x0, y0, x1, max(y1, y0 + 2)], fill=(250, 250, 250))
            elif p["kind"] != "plane" or p["mat"] in ("concrete", "water"):
                d.rectangle([x0, y0, max(x1, x0 + 1), max(y1, y0 + 1)], outline=col)
        elif p["kind"] in ("cyl",):
            x, y = mp(*p["c"])
            d.point((x, y), fill=col)
    for y in (840, 1100, 1700, 3800):
        a, b = mp(X0, y), mp(X1, y)
        d.line([a, b], fill=(90, 90, 100))
    for L in cam.LAYERS:
        if not math.isinf(L["d_rep"]):
            x, y = mp(X0, L["y_band"][0])
            d.text((x + 4, y + 2), "%s (painted at %.0f m)" % (L["id"], L["d_rep"]), fill=LAYER_COLOR[L["id"]], font=font)
    a, b = mp(cam.camera_x(0), 0), mp(cam.camera_x(cam.DURATION), 0)
    d.line([a, b], fill=(255, 80, 80), width=5)
    half = math.radians(cam.HFOV_DEG / 2)
    for t in (0.0, cam.DURATION / 2, cam.DURATION):
        cx = cam.camera_x(t)
        for s in (-1, 1):
            pts = [mp(cx + s * math.tan(half) * y, y) for y in range(0, int(YMAX) + 1, 250)]
            d.line(pts, fill=(255, 120, 120), width=1)
        x, y = mp(cx, 0)
        d.text((x - 10, y - 16), "t=%.0f" % t, fill=(255, 140, 140), font=font)
    d.text(mp(-400, 960)[0:1] + (mp(0, 940)[1] - 14,), "freight road", fill=(255, 255, 255), font=font)
    # layer strips with the window's footprint at t = 0, mid and end
    y = map_h
    for L, crop, s, y0 in strips:
        d.text((60, y + 8), "%s  %s  slides %.0f px/s, %.0f px over the shot" % (
            L["id"], L["name"], cam.layer_rate(L), cam.layer_travel(L)), fill=LAYER_COLOR[L["id"]], font=font)
        out.paste(crop.convert("RGB"), (0, y + 26), crop.getchannel("A"))
        for t, col in ((0.0, (80, 220, 255)), (cam.DURATION / 2, (255, 255, 255)), (cam.DURATION, (255, 80, 80))):
            x0 = cam.screen_offset(L, t) * s
            d.rectangle([x0, y + 26, x0 + cam.OUT_W * s, y + 26 + crop.height], outline=col, width=2)
        y += crop.height + 40
    out.save(BASE / "guides" / "c4-panorama-plan.png")



if __name__ == "__main__":
    panorama_plan(load_layers())
    print("guides/c4-panorama-plan.png")
