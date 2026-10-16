"""C4 THE ROAD AND THE PORT: blockout animatic stills and the panorama plan (c4-layout.md).

python compose_c4.py            # review/c4-blockout-t00..t20.png and guides/c4-panorama-plan.png
python compose_c4.py --frames   # also every frame to build/cinematic/c4-road-and-port/frames

Slides the four clay layer guides (build_c4_guides.py) at their c4_camera.py rates, applies
atmospheric haze by layer, puts the fixed window frame on top and draws the named moving
elements as simple stand-ins at their real world positions: hauler convoys on the freight
road, the crop sprayer, two lifters climbing out of the far launch cradles, and landing
lights. Nothing here rotates or rescales: every element only translates.
"""
import argparse
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c4_camera as cam  # noqa: E402
import c4_world as world  # noqa: E402

ROOT = BASE.parents[3]
HORIZON_SKY = (204, 218, 232)
LAYER = {L["id"]: L for L in cam.LAYERS}


def load_layers():
    out = []
    for L in cam.LAYERS:
        if math.isinf(L["d_rep"]):
            continue
        im = Image.open(BASE / "guides" / ("c4-%s-guide.png" % L["id"])).convert("RGBA")
        im = im.resize((im.width // cam.PLATE_SCALE, im.height // cam.PLATE_SCALE), Image.LANCZOS)
        haze = Image.new("RGBA", im.size, HORIZON_SKY + (255,))
        rgb = Image.blend(im, haze, L["haze"])
        rgb.putalpha(im.getchannel("A"))
        out.append((L, rgb))
    frame = Image.open(BASE / "guides" / "c4-window-frame-blockout.png").convert("RGBA")
    return out, frame


def screen(layer_id, x, y, z, t):
    """Screen position of a world point riding a layer (moves at that layer's rate)."""
    L = LAYER[layer_id]
    sx = cam.plate_x(L, x) / cam.PLATE_SCALE - cam.screen_offset(L, t)
    sy = cam.HORIZON_Y + cam.F_PX * (cam.ALTITUDE - z) / y
    return sx, sy


# ------------------------------------------------------------------ moving elements

def haulers(t):
    """(x, lane_y, direction) for every hauler. Inbound loaded convoy toward the port, outbound empties."""
    out = []
    for i in range(5):
        out.append((-420 + i * 62 + 18.0 * t, world.ROAD_LANES[0], 1))
    for i in range(3):
        out.append((980 - i * 58 - 18.0 * t, world.ROAD_LANES[1], -1))
    for i in range(2):
        out.append((1900 - i * 60 - 18.0 * t, world.ROAD_LANES[1], -1))
    return out


def draw_hauler(d, t, x, lane, direction):
    L = LAYER["L4-near"]
    sx, sy = screen("L4-near", x, lane, 0, t)
    k = cam.px_per_m(L["d_rep"])
    length, height = 32 * k, 4.2 * cam.px_per_m(lane)
    cab = 6 * k
    x0 = sx - length / 2
    d.rectangle([x0, sy - 1, x0 + length, sy + 2], fill=(40, 42, 40, 120))          # contact shadow
    trailer = [x0 + (0 if direction > 0 else cab), sy - height, x0 + length - (cab if direction > 0 else 0), sy]
    d.rectangle(trailer, fill=(150, 80, 50, 255), outline=(90, 50, 35, 255))
    c0 = x0 + length - cab if direction > 0 else x0
    d.rectangle([c0, sy - height * 1.15, c0 + cab, sy], fill=(205, 200, 185, 255))


def draw_sprayer(d, t):
    x, y = 120 + 5.0 * t, 905.0
    sx, sy = screen("L4-near", x, y, 2.5, t)
    k = cam.px_per_m(LAYER["L4-near"]["d_rep"])
    d.polygon([(sx - 12 * k, sy + 6), (sx + 12 * k, sy + 6), (sx + 10 * k, sy - 1), (sx - 10 * k, sy - 1)],
              fill=(235, 240, 235, 90))                                                  # spray fan
    d.rectangle([sx - 12 * k, sy - 2, sx + 12 * k, sy - 1], fill=(60, 60, 60, 255))     # boom
    d.rectangle([sx - 3 * k, sy - 6, sx + 3 * k, sy - 1], fill=(225, 190, 60, 255))     # tender body


LIFTERS = [  # cradle, launch time, climb acceleration m/s^2
    (world.CRADLES[0], -10.0, 1.2),
    (world.CRADLES[1], 7.5, 1.2),
]


def draw_lifters(d, t):
    k = cam.px_per_m(LAYER["L1-far"]["d_rep"])
    for (cx, cy), t0, acc in LIFTERS:
        if t < t0:
            continue
        z = 70 + 0.5 * acc * (t - t0) ** 2
        sx, sy = screen("L1-far", cx, cy, z, t)
        w, h = 34 * k * 6400 / cy, 220 * k * 6400 / cy
        d.ellipse([sx - w * 1.4, sy + h * 0.45, sx + w * 1.4, sy + h * 1.6], fill=(255, 236, 200, 150))   # plume
        d.rectangle([sx - w / 2, sy - h / 2, sx + w / 2, sy + h / 2], fill=(150, 150, 150, 255))
        d.polygon([(sx - w / 2, sy - h / 2), (sx + w / 2, sy - h / 2), (sx, sy - h / 2 - w)], fill=(150, 150, 150, 255))


def draw_lights(d, t):
    for i, (x, y) in enumerate(world.LIGHT_MASTS_L2):
        if (t * 1.25 + i * 0.11) % 1.0 < 0.22:
            sx, sy = screen("L2-port", x, y, 26, t)
            d.ellipse([sx - 2, sy - 2, sx + 2, sy + 2], fill=(255, 90, 60, 255))
    if (t * 0.8) % 1.0 < 0.2:
        sx, sy = screen("L1-far", 3000, 7400, 180, t)
        d.ellipse([sx - 2, sy - 2, sx + 2, sy + 2], fill=(255, 255, 255, 255))


def frame_image(t, layers, frame):
    canvas = Image.new("RGBA", (cam.OUT_W, cam.OUT_H), HORIZON_SKY + (255,))
    for L, im in layers:
        canvas.alpha_composite(im, (round(-cam.screen_offset(L, t)), 0))
        over = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(over)
        if L["id"] == "L1-far":
            draw_lifters(d, t)
        if L["id"] == "L2-port":
            draw_lights(d, t)
        if L["id"] == "L4-near":
            for x, lane, direction in haulers(t):
                draw_hauler(d, t, x, lane, direction)
            draw_sprayer(d, t)
        canvas.alpha_composite(over)
    canvas.alpha_composite(frame)
    return canvas.convert("RGB")


# ------------------------------------------------------------------ plan

LAYER_COLOR = {"L1-far": (120, 150, 200), "L2-port": (200, 140, 90), "L3-mid": (120, 180, 110), "L4-near": (230, 210, 90)}


def panorama_plan(layers):
    font = ImageFont.load_default()
    W = 2400
    strip_h = []
    strips = []
    for L, im in layers:
        box = im.getchannel("A").getbbox() if L["id"] != "L1-far" else (0, 0, im.width, int(cam.ground_y(L["y_band"][0])) + 20)
        y0, y1 = max(0, box[1] - 10), min(im.height, box[3] + 10)
        s = W / im.width if L["id"] == "L4-near" else W / layers[-1][1].width
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frames", action="store_true")
    a = ap.parse_args()
    layers, frame = load_layers()
    (BASE / "review").mkdir(exist_ok=True)
    for t in (0, 5, 10, 15, 20):
        tt = min(t, cam.DURATION - 1 / cam.FPS)
        frame_image(tt, layers, frame).save(BASE / "review" / ("c4-blockout-t%02d.png" % t))
    panorama_plan(layers)
    if a.frames:
        out = ROOT / "build" / "cinematic" / "c4-road-and-port" / "frames"
        out.mkdir(parents=True, exist_ok=True)
        for i in range(round(cam.DURATION * cam.FPS)):
            frame_image(i / cam.FPS, layers, frame).save(out / ("%05d.png" % i))
    print("C4 stills in review/, plan in guides/")


if __name__ == "__main__":
    main()
