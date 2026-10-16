"""C1 traffic: the Hegemony flotilla, hero warship, Jangala Station and the freighter lanes.

Owned by the traffic pass. `build(add, duration, stand_ins)` adds Elements through
`add(...)` (same signature as `c1_core.Element`), back to front, and returns nothing.

Everything here sits in one camera (c1-layout.md, "Scale and distance"): a 50 mm lens
on a 36 mm sensor, so F = 2667 px. A body has a real length and a camera-space
position in metres; its screen position and size are projected from that, every
frame, and its parallax rate is DEPTH_FLOOR + TRUCK_M / distance. Nothing is placed by
pixel size alone, so a freighter cannot be painted at a size that puts it at the planet.

Light is c1_core.SUN_IN_CAMERA: a hard key from the upper left and toward camera,
black shadow sides, a faint cool planet-shine from the right, no haze at any distance.
Lane hulls are lit renders (traffic_render_cards.py); painted masters are graded toward
the same model by `light_grade`. Engines and running lights are separate additive
glows, so they can flicker and blink.
"""
import json
import math
from functools import lru_cache

import numpy as np
from PIL import Image, ImageFilter

from c1_core import BASE, DEPTH_FLOOR, H, W, depth_for, glow_dot, latest

# ---------------------------------------------------------------- camera model

F = W * 50.0 / 36.0             # focal length in px: 50 mm lens, 36 mm sensor (render_planet.py)
PITCH = math.radians(44.0)      # camera looks this far down across the orbital plane (flotilla_guides.py)
E_RIGHT = np.array([1.0, 0.0, 0.0])
E_AWAY = np.array([0.0, math.sin(PITCH), -math.cos(PITCH)])    # in-plane, away from camera
E_UP = np.array([0.0, math.cos(PITCH), math.sin(PITCH)])       # orbital-plane normal
JANGALA_RADIUS_M = 6.0e6
JANGALA_Z = JANGALA_RADIUS_M / math.sin(math.atan(380.0 / F))  # 42,500 km to the centre


def unproject(sx, sy, z):
    """Camera-space point (x right, y up, z toward camera) seen at frame pixel (sx, sy), z metres away."""
    return np.array([(sx - W / 2) / F * z, -(sy - H / 2) / F * z, -z])


def project(p):
    z = -p[2]
    return W / 2 + F * p[0] / z, H / 2 - F * p[1] / z, z


def heading(deg, climb=0.0):
    """Unit direction in the orbital plane, `deg` from screen right toward away; `climb` along its normal."""
    a = math.radians(deg)
    v = math.cos(a) * E_RIGHT + math.sin(a) * E_AWAY + climb * E_UP
    return v / np.linalg.norm(v)


# ---------------------------------------------------------------- light

SUN_2D = np.array([-0.62, -0.50]) / math.hypot(0.62, 0.50)       # toward Corvus on the image (y down)


def glow_mask(rgb, a):
    """Self-lit pixels: hot blue-white engine bells and the brightest lamps. Kept out of the grade."""
    mx = rgb.max(-1)
    blue = (rgb[..., 2] > 0.55) & (rgb[..., 2] - rgb[..., 0] > 0.16)
    teal = (rgb[..., 1] > 0.5) & (rgb[..., 2] > 0.5) & (rgb[..., 0] < 0.45)
    hot = mx > 0.95
    m = ((blue | teal | hot) & (a > 0.3)).astype(np.uint8) * 255
    m = Image.fromarray(m).filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(1.0))
    return np.asarray(m, np.float32)[..., None] / 255


def light_grade(img, black=0.03, gamma=1.5, key=0.35, shine=0.05, sat=1.0, gain=1.0):
    """Grade a painted card toward C1's vacuum light. Removes any haze lift (black point),
    crushes shadows toward black with a luminance power curve (highlights keep their
    value), adds the key's fall-off across the subject along the sun's screen direction,
    and lets the faint cool planet-shine into the shadow side facing away from the sun.
    Self-lit pixels (engines, lit rings, lamps) keep their original colour."""
    img = img.convert("RGBA")
    box = img.getchannel("A").getbbox()
    if box is None:
        return img
    if box != (0, 0) + img.size:
        whole = Image.new("RGBA", img.size, (0, 0, 0, 0))
        whole.paste(light_grade(img.crop(box), black, gamma, key, shine, sat, gain), box[:2])
        return whole
    arr = np.asarray(img, np.float32) / 255
    rgb, a = arr[..., :3], arr[..., 3]
    gm = glow_mask(rgb, a)
    lum = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    out = np.clip((rgb - black) / (1 - black), 0, 1)
    lum2 = np.clip((lum - black) / (1 - black), 0, 1)
    target = lum2 ** gamma
    out = out * (target / np.maximum(lum2, 1e-4))[..., None]
    if sat != 1.0:
        grey = (out @ np.array([0.2126, 0.7152, 0.0722], np.float32))[..., None]
        out = grey + (out - grey) * sat
    ys, xs = np.nonzero(a > 0.5)
    if len(xs):
        cx, cy = xs.mean(), ys.mean()
        half = 0.5 * math.hypot(xs.max() - xs.min(), ys.max() - ys.min()) + 1
        yy, xx = np.mgrid[:a.shape[0], :a.shape[1]]
        u = ((xx - cx) * SUN_2D[0] + (yy - cy) * SUN_2D[1]) / half
        out = out * np.clip(1 + key * u, 0.35, 1.5)[..., None]
        shade = np.clip(1 - target * 4, 0, 1) * np.clip(-u, 0, 1)
        out = out + shine * shade[..., None] * np.array([0.30, 0.50, 1.0], np.float32)
    out = np.clip(out * gain, 0, 1)
    out = out * (1 - gm) + rgb * gm
    return Image.fromarray((np.dstack([out, a]) * 255).astype(np.uint8), "RGBA")


def find_glows(img, min_px=2):
    """Engine bells painted into a card: clusters of hot blue pixels, as (x, y, radius) in card px."""
    arr = np.asarray(img.convert("RGBA"), np.float32) / 255
    rgb, a = arr[..., :3], arr[..., 3]
    m = (rgb[..., 2] > 0.70) & (rgb[..., 2] - rgb[..., 0] > 0.12) & (a > 0.5)
    todo = set(zip(*np.nonzero(m)))
    out = []
    while todo:                               # flood fill over 8-neighbours; masks are sparse
        stack = [todo.pop()]
        comp = []
        while stack:
            y, x = stack.pop()
            comp.append((y, x))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    q = (y + dy, x + dx)
                    if q in todo:
                        todo.remove(q)
                        stack.append(q)
        if len(comp) >= min_px:
            ys, xs = np.array(comp).T
            out.append((xs.mean(), ys.mean(), math.sqrt(len(comp) / math.pi)))
    return out


# ---------------------------------------------------------------- cards and bodies

class Card:
    """A lit card, its anchors in card pixels, and the card pixels its real length spans.
    `sized(f)` returns the card resampled near factor f (quantised, cached) so the compositor
    only ever scales by about 1: a 640 px render is never point-sampled down to 8 px."""

    def __init__(self, img, length_px, anchors=None):
        self.img, self.length_px, self.anchors = img, length_px, anchors or {}
        self._cache = {}

    def sized(self, f):
        q = 2 ** (round(math.log2(max(f, 1e-3)) * 12) / 12)
        if q not in self._cache:
            w, h = self.img.size
            self._cache[q] = self.img.resize((max(1, round(w * q)), max(1, round(h * q))), Image.LANCZOS)
        im = self._cache[q]
        return im, im.width / self.img.width


@lru_cache(maxsize=None)
def glow(radius_q, color, strength):
    return glow_dot(max(0.35, radius_q / 4), color, strength)


def qr(r):
    return max(1, round(r * 4))


def lane_card(hull, heading_id, tint=None):
    stem = "traffic-lane-v1-%s-%s" % (hull, heading_id)
    meta = LANE_META["cards"][stem]
    img = Image.open(BASE / "assets" / (stem + ".png")).convert("RGBA")
    if tint is not None:
        arr = np.asarray(img, np.float32)
        arr[..., :3] = np.clip(arr[..., :3] * np.array(tint, np.float32), 0, 255)
        img = Image.fromarray(arr.astype(np.uint8), "RGBA")
    img = light_grade(img, black=0.0, gamma=1.1, key=0.15, shine=0.03, sat=0.8, gain=1.35)
    return Card(img, meta["length_m"] / meta["m_per_card_px"], meta)


LANE_META = json.loads((BASE / "assets" / "traffic-lane-v1.json").read_text())


def flicker(seed, depth=0.10, rate=1.0):
    """Engine output: two incommensurate wobbles and a slow breath, never a flat sticker glow."""
    r = np.random.default_rng(seed)
    f1, f2, f3 = r.uniform(5, 9) * rate, r.uniform(11, 17) * rate, r.uniform(0.15, 0.4)
    p1, p2, p3 = r.uniform(0, 6.28, 3)
    return lambda t: 1 - depth * (0.5 + 0.25 * math.sin(f1 * t + p1) + 0.15 * math.sin(f2 * t + p2)
                                   + 0.10 * math.sin(f3 * t + p3))


def strobe(period, phase, on=0.07, double=False):
    def a(t):
        u = (t + phase) % period
        return 1.0 if u < on or (double and 0.22 <= u < 0.22 + on) else 0.0
    return a


def slow_blink(period, phase):
    """A beacon that fades up and down rather than switching: an incandescent-style rotator."""
    return lambda t: max(0.0, math.sin(2 * math.pi * ((t + phase) / period))) ** 3


class Body:
    """A rigid craft: a Card at a camera-space trajectory traj(t) -> metres, with real length
    `length_m`. Adds the hull Element and its glows/lights as children."""

    def __init__(self, name, card, length_m, traj, alpha=None):
        self.name, self.card, self.length_m, self.traj, self.alpha = name, card, length_m, traj, alpha
        self.parts = []          # (kind, anchor(card px), params)

    def factor(self, t):
        return F * self.length_m / -self.traj(t)[2] / self.card.length_px

    def screen_len(self, t):
        return F * self.length_m / -self.traj(t)[2]

    def engine(self, xy, color=(150, 200, 255), size=0.06, strength=0.9, seed=0, gate=None, minimum=0.6):
        self.parts.append(("engine", xy, dict(color=color, size=size, strength=strength, seed=seed,
                                              gate=gate, minimum=minimum)))
        return self

    def light(self, xy, color, alpha, size=0.6, strength=1.0):
        self.parts.append(("light", xy, dict(color=color, alpha=alpha, size=size, strength=strength)))
        return self

    def mid_z(self, duration):
        return -self.traj(duration / 2)[2]

    def add_to(self, add):
        body = self

        def path(t):
            sx, sy, _ = project(body.traj(t))
            f = body.factor(t)
            _, actual = body.card.sized(f)
            return sx, sy, f / actual

        hull = add(self.name, self.card.img, lambda t: depth_for(-body.traj(t)[2]), None, path=path,
                   frames=lambda t: body.card.sized(body.factor(t))[0], alpha=self.alpha)
        for i, (kind, (ax, ay), p) in enumerate(self.parts):
            off = (lambda ax, ay: lambda t: (ax * body.card.sized(body.factor(t))[1],
                                             ay * body.card.sized(body.factor(t))[1]))(ax, ay)
            if kind == "engine":
                fl = flicker(p["seed"] * 31 + i)
                core_r = (lambda p: lambda t: max(p["minimum"], p["size"] * body.screen_len(t)))(p)
                for layer, (rmul, smul, col) in enumerate(((1.0, 1.0, (225, 240, 255)), (2.6, 0.4, p["color"]))):
                    frames = (lambda rmul, smul, col, p, core_r: lambda t: glow(
                        qr(core_r(t) * rmul), col, p["strength"] * smul))(rmul, smul, col, p, core_r)
                    gate = p["gate"]
                    al = (lambda fl, gate: lambda t: fl(t) * (gate(t) if gate else 1.0) *
                          (body.alpha(t) if body.alpha else 1.0))(fl, gate)
                    add("%s-engine-%d-%d" % (self.name, i, layer), frames(0), None, None, additive=True,
                        parent=hull, offset=off, frames=frames, alpha=al)
            else:
                r = p["size"]
                img = glow(qr(r), p["color"], p["strength"])
                al = (lambda la: lambda t: la(t) * (body.alpha(t) if body.alpha else 1.0))(p["alpha"])
                add("%s-light-%d" % (self.name, i), img, None, None, additive=True, parent=hull, offset=off,
                    alpha=al)
        return hull


def line(p0, direction, s):
    """Trajectory along a straight line: s(t) metres travelled."""
    return lambda t: p0 + direction * s(t)


def cruise(v, s0=0.0):
    return lambda t: s0 + v * t


def brake(s0, s_stop, t_stop):
    """Constant deceleration from s0 to rest at s_stop at t_stop, then holding."""
    return lambda t: s_stop - (s_stop - s0) * (1 - min(t, t_stop) / t_stop) ** 2


def depart(s0, accel, t0=0.0):
    """At rest until t0, then constant acceleration."""
    return lambda t: s0 + 0.5 * accel * max(0.0, t - t0) ** 2


def guided_body(name, asset, guide, length_m, traj, seed=0, engine=0.6, strobe_period=1.3):
    """A near hull whose card was painted over a lit guide: same framing, so the guide's scale
    and anchors (port, starboard, dorsal top) apply to the card directly."""
    path = BASE / "assets" / (asset + ".png")
    meta_path = BASE / "guides" / (guide + ".json")
    if not path.exists() or not meta_path.exists():
        return None
    raw = Image.open(path).convert("RGBA")
    raw.putalpha(raw.getchannel("A").point(lambda v: 0 if v <= 8 else v))
    meta = next(iter(json.loads(meta_path.read_text())["cards"].values()))
    guide_px = Image.open(BASE / "guides" / (guide + ".png")).width
    k = raw.width / guide_px                              # guide px -> card px
    img = light_grade(raw, black=0.02, gamma=1.12, key=0.12, shine=0.03)
    card = Card(img, length_m / (meta["m_per_card_px"] / k), meta)
    b = Body(name, card, length_m, traj)
    for i, (gx, gy, gr) in enumerate(find_glows(raw)):
        b.engine((gx, gy), size=gr / card.length_px * 1.5, strength=engine, seed=seed + i, minimum=0.9)
    sc = lambda xy: (xy[0] * k, xy[1] * k)
    b.light(sc(meta["port"]), (255, 70, 55), lambda t: 0.9, size=1.3)
    b.light(sc(meta["starboard"]), (90, 255, 130), lambda t: 0.8, size=1.3)
    b.light(sc(meta["top"]), (255, 255, 255), strobe(strobe_period, seed * 0.37 % strobe_period, double=True),
            size=1.8)
    return b


def lane_body(name, hull, heading_id, p0, direction, s, alpha=None, tint=None, nav=True, seed=0,
              engine_gate=None, engine_strength=0.9):
    card = lane_card(hull, heading_id, tint)
    m = card.anchors
    b = Body(name, card, m["length_m"], line(p0, direction, s), alpha)
    facing = m["stern_facing"]
    vis = max(0.25, min(1.0, facing + 0.6))
    for k, (sx, sy) in enumerate(m["sterns"]):
        b.engine((sx, sy), size=0.05, strength=0.9 * vis * engine_strength, seed=seed + k, gate=engine_gate,
                 minimum=0.55)
    if nav:
        r = np.random.default_rng(seed + 99)
        b.light(tuple(m["port"]), (255, 70, 55), lambda t: 1.0, size=1.1, strength=1.3)
        b.light(tuple(m["starboard"]), (90, 255, 130), lambda t: 0.85, size=1.1, strength=1.3)
        b.light(tuple(m["top"]), (255, 255, 255), strobe(r.uniform(1.1, 1.6), r.uniform(0, 2), double=True),
                size=1.6, strength=1.5)
    return b


# ---------------------------------------------------------------- scene


STATION_Z = 22000.0
STATION_SCREEN = (1095.0, 352.0)
STATION_HEIGHT_M = 2400.0


def build(add, duration, stand_ins):
    bodies = []          # (distance at mid-shot, adder)
    sky_far = []

    # ---- distant traffic over Jangala: running lights in low orbit, thousands of km out
    rng = np.random.default_rng(7)
    for i in range(14):
        ang = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(0.55, 1.02) * 380
        x0 = 1290 + rr * math.cos(ang)
        y0 = 560 + rr * math.sin(ang) * 0.9
        vx, vy = rng.uniform(-0.9, 0.9), rng.uniform(-0.3, 0.3)
        col = [(255, 235, 200), (255, 120, 90), (200, 230, 255)][i % 3]
        per, ph = rng.uniform(1.4, 3.2), rng.uniform(0, 3)
        sky_far.append((x0, y0, vx, vy, col, per, ph, rng.uniform(0.35, 0.8)))
    for i, (x0, y0, vx, vy, col, per, ph, s) in enumerate(sky_far):
        add("orbit-light-%d" % i, glow(qr(0.55), col, s), DEPTH_FLOOR, [(0, x0, y0, 1.0)], vx=vx, vy=vy,
            additive=True, alpha=(lambda per, ph: lambda t: 0.25 + 0.75 * strobe(per, ph, on=0.12)(t))(per, ph))

    # ---- Jangala Station: an outer customs station 30 km out, the lanes' focus
    station = latest("station")
    s_img = light_grade(Image.open(station).convert("RGBA"), black=0.02, gamma=1.55, key=0.45, shine=0.05)
    s_img.putalpha(s_img.getchannel("A").point(lambda a: 0 if a <= 8 else a))
    box = s_img.getchannel("A").getbbox()
    s_img = s_img.crop(box)
    s_card = Card(s_img, s_img.height)
    S3 = unproject(STATION_SCREEN[0], STATION_SCREEN[1], STATION_Z)
    st = Body("station", s_card, STATION_HEIGHT_M, lambda t: S3 + E_RIGHT * 0.4 * t)
    sx0, sy0 = box[0], box[1]
    for (x, y), col, al, size in [
            ((493, 25), (255, 70, 50), slow_blink(2.4, 0.0), 2.4),          # mast-head beacon
            ((560, 112), (255, 70, 50), slow_blink(2.4, 1.2), 1.8),
            ((40, 652), (255, 255, 255), strobe(1.6, 0.0, on=0.09), 2.2),            # docking-arm tips
            ((985, 742), (255, 255, 255), strobe(1.6, 0.8, on=0.09), 2.2),
            ((215, 820), (255, 200, 120), lambda t: 0.8, 1.5),              # lower berth flood
            ((513, 1448), (255, 160, 60), slow_blink(3.1, 0.4), 2.2),       # keel beacon
    ]:
        st.light((x - sx0, y - sy0), col, al, size=size, strength=1.5)
    # approach chase lights running in along the right arm to its berth while the shuttle below
    # is guided in; they go dark once it is docked
    for k in range(5):
        x, y = 760 + k * 45, 708 + k * 7
        st.light((x - sx0, y - sy0), (120, 220, 255),
                 (lambda k: lambda t: 1.0 if t < 26.5 and ((t * 1.25 - (4 - k) * 0.12) % 1.0) < 0.1 else 0.0)(k),
                 size=1.3, strength=1.3)
    bodies.append((STATION_Z, st.add_to))

    def station_point(cx, cy, along_m=0.0):
        """Camera-space point at station card pixel (cx, cy) (uncropped coordinates)."""
        k = STATION_HEIGHT_M / s_img.height
        return S3 + np.array([(cx - sx0 - s_img.width / 2) * k, -(cy - sy0 - s_img.height / 2) * k, along_m])

    # ---- inbound lane: a loose queue toward the station's lower docks, braking at the head
    berth_low = station_point(215, 820) + E_UP * -350 + E_AWAY * 0
    d_in = heading(22)
    inbound = [  # hull, s0 (m before the berth), speed m/s or brake, lateral (m), up (m)
        ("tarsus", -2400, ("brake", -300, 30.0), 0, 0),
        ("buffalo", -4600, ("cruise", 70), 300, -200),
        ("atlas", -6800, ("cruise", 64), -400, 150),
        ("mule", -9100, ("cruise", 78), 200, -350),
        ("nebula", -11300, ("cruise", 82), -250, 100),
        ("prometheus", -13900, ("cruise", 90), 300, -200),
        ("valkyrie", -16900, ("cruise", 105), 0, 300),
        ("mercury", -19800, ("cruise", 115), -200, -150),
    ]
    side = np.cross(E_UP, d_in)
    for i, (hull, s0, mot, lat, up) in enumerate(inbound):
        p0 = berth_low + side * lat + E_UP * up
        s = brake(s0, mot[1], mot[2]) if mot[0] == "brake" else cruise(mot[1], s0)
        gate = None
        if mot[0] == "brake":       # main drive off once braking; the bow thrusters do the work
            gate = (lambda tb: lambda t: max(0.0, 1 - t / 4.0) if t < tb else 0.0)(mot[2])
        b = lane_body("in-%d-%s" % (i, hull), hull, "in", p0, d_in, s, seed=10 + i, engine_gate=gate)
        if mot[0] == "brake":
            bx, by = b.card.anchors["bow"]
            b.light((bx, by), (230, 240, 255), (lambda tb: lambda t: 0.8 * strobe(0.9, 0.3, on=0.09)(t)
                                                if 3.0 < t < tb else 0.0)(mot[2]), size=0.5)
        bodies.append((b.mid_z(duration), b.add_to))

    # ---- outbound lane: undocking from the lower berths and accelerating out past Jangala's
    # night side, right and toward camera
    berth_out = station_point(760, 980) + E_UP * -250
    d_out = heading(-25)
    side_o = np.cross(E_UP, d_out)
    outbound = [("mercury", 150, 1.6, 4.0, 0, 0), ("colossus", 2600, 0.0, 0, -250, 120),
                ("buffalo", 6400, 0.0, 0, 300, -100), ("tarsus", 10600, 0.0, 0, -150, 250),
                ("mule", 15000, 0.0, 0, 200, -150)]
    for i, (hull, s0, accel, t0, lat, up) in enumerate(outbound):
        p0 = berth_out + side_o * lat + E_UP * up
        s = depart(s0, accel, t0) if accel else cruise(80 + 12 * i, s0)
        b = lane_body("out-%d-%s" % (i, hull), hull, "cross", p0, d_out, s, seed=40 + i)
        bodies.append((b.mid_z(duration), b.add_to))

    # ---- descent corridor: shuttles leaving the keel toward Jangala, shrinking toward the globe
    keel = station_point(513, 1470)
    planet_c = unproject(1290, 560, JANGALA_Z)
    d_down = (planet_c - keel) / np.linalg.norm(planet_c - keel)
    for i, (hull, t0, acc) in enumerate([("aeroshuttle", -14.0, 22.0), ("hermes", 2.0, 18.0),
                                         ("aeroshuttle", 19.0, 25.0)]):
        p0 = keel + E_RIGHT * (120 * i - 120)
        s = (lambda t0, acc: lambda t: 60 + 0.5 * acc * max(0.0, t - t0) ** 2)(t0, acc)
        fade = (lambda t0: lambda t: min(1.0, max(0.0, (t - t0) / 1.5)))(t0)
        b = lane_body("down-%d-%s" % (i, hull), hull, "away", p0, d_down, s, alpha=fade, seed=60 + i,
                      nav=False, engine_strength=1.6)
        bodies.append((b.mid_z(duration), b.add_to))

    # ---- arriving shuttle: braking hard along the right arm's chase lights onto its berth
    berth_r = station_point(985, 742) + E_RIGHT * 160
    d_arr = heading(194)
    b = lane_body("arrival-hermes", "hermes", "out", berth_r, d_arr,
                  brake(-5200, -60, 26.0), seed=70,
                  engine_gate=lambda t: 0.0)
    bx, by = b.card.anchors["bow"]
    b.light((bx, by), (230, 240, 255), lambda t: (0.9 if (t % 0.7) < 0.08 else 0.0) if t < 25 else 0.0,
            size=0.6)
    bodies.append((b.mid_z(duration), b.add_to))

    # ---- holding at the outer berths, waiting on customs: parked, lights on, drives cold
    for i, (hull, cx, cy, z) in enumerate([("nebula", 1215, 470, 21400), ("buffalo", 1275, 508, 21000),
                                            ("colossus", 1190, 540, 21800)]):
        p0 = unproject(cx, cy, z)
        b = lane_body("held-%d-%s" % (i, hull), hull, "park", p0, heading(150), cruise(0.6), seed=150 + i,
                      engine_gate=lambda t: 0.0)
        bodies.append((b.mid_z(duration), b.add_to))

    # ---- the near hulls: painted over blockouts lit by this sun (traffic_render_cards.py --guide),
    # so they carry C1's light already and need only a mild grade. Each is placed by its guide's
    # own scale, metres per card pixel, with the hull's origin at the card centre.

    # a working Tarsus 3.2 km out, outbound from the station and crossing below the night side to
    # screen left: the one near civilian hull heading against the lanes' flow
    tb = guided_body("tarsus-near", "traffic-near-tarsus-out-v1", "traffic-near-tarsus-out", 190.0,
                     line(unproject(1830, 705, 3500), heading(194), cruise(20.0)), seed=130, engine=0.35)
    if tb:
        bodies.append((tb.mid_z(duration), tb.add_to))

    # hero Hegemony warship: a Dominator 2.2 km out on a slow station-keeping glide
    hb = guided_body("heg-hero", "heg-hero-v4", "traffic-hero-dominator-v2", 175.0,
                     line(unproject(1800, 104, 2500), heading(206), cruise(4.5)), seed=90, engine=0.7,
                     strobe_period=1.9)
    if hb:
        bodies.append((hb.mid_z(duration), hb.add_to))

    # hero Atlas: the bulk freighter 3.6 km out, inbound, passing above the picket
    ab = guided_body("atlas", "traffic-atlas-v3", "traffic-hero-atlas-v2", 384.0,
                     line(unproject(-150, 118, 3600), heading(4), cruise(30.0)), seed=110, engine=0.6,
                     strobe_period=1.4)
    if ab:
        bodies.append((ab.mid_z(duration), ab.add_to))

    # ---- tug crossing the near-mid field toward the inbound queue
    tug_p0 = unproject(820, 770, 9000)
    b = lane_body("tug-ox", "ox", "cross", tug_p0, heading(-25), cruise(22), seed=80)
    bodies.append((b.mid_z(duration), b.add_to))

    # ---- the Hegemony battlegroup, a picket screening the lane 7-11 km out
    flot = add_flotilla(add, duration, bodies)

    for _, adder in sorted(bodies, key=lambda b: -b[0]):
        adder(add)


FLOTILLA_WINDOW = (247, 148, 576, 324)   # frame x, y, w, h shared by both fleet guides
FLOTILLA_Z = {"heg-flotilla-far": 11000.0, "heg-flotilla-mid": 7600.0}   # flotilla_guides.ANCHORS
FLOTILLA_HEADING = 223.8                 # flotilla_guides.HEADING in the orbital plane


def add_flotilla(add, duration, bodies):
    guides = json.loads((BASE / "guides" / "flotilla-guides.json").read_text())
    peel = [h for h in guides["hulls"] if h["class"] == "lasher"]
    d = heading(FLOTILLA_HEADING)
    for stem in ("heg-flotilla-far", "heg-flotilla-mid"):
        path = BASE / "assets" / (stem + "-v2-registered.png")
        path = path if path.exists() else latest(stem)
        if not path:
            continue
        z0 = FLOTILLA_Z[stem]
        layer = Image.open(path).convert("RGBA").resize((FLOTILLA_WINDOW[2] * 2, FLOTILLA_WINDOW[3] * 2),
                                                         Image.LANCZOS)
        layer.putalpha(layer.getchannel("A").point(lambda a: 0 if a <= 8 else a))
        if stem.endswith("far"):
            # the two frigates detach as their own bodies; clear them from the painted layer
            arr = np.asarray(layer).copy()
            for h in peel:
                x0, y0, x1, y1 = h["frame_box"]
                arr[max(0, int((y0 - FLOTILLA_WINDOW[1] - 3) * 2)):int((y1 - FLOTILLA_WINDOW[1] + 3) * 2),
                    max(0, int((x0 - FLOTILLA_WINDOW[0] - 3) * 2)):int((x1 - FLOTILLA_WINDOW[0] + 3) * 2), 3] = 0
            layer = Image.fromarray(arr, "RGBA")
        raw = layer
        graded = light_grade(raw, black=0.06, gamma=1.45, key=0.0, shine=0.0, sat=1.15, gain=1.12)
        card = Card(graded, graded.width)
        cx = FLOTILLA_WINDOW[0] + FLOTILLA_WINDOW[2] / 2
        cy = FLOTILLA_WINDOW[1] + FLOTILLA_WINDOW[3] / 2
        p0 = unproject(cx, cy, z0)
        span = FLOTILLA_WINDOW[2] / F * z0                 # metres the window spans at z0
        b = Body(stem, card, span, line(p0, d, cruise(3.0)))
        for k, (gx, gy, gr) in enumerate(find_glows(raw, min_px=3)):
            b.engine((gx, gy), size=gr / card.length_px * 1.4, strength=0.55, seed=200 + k + (50 if "mid" in stem
                                                                                                else 0), minimum=0.7)
        r = np.random.default_rng(3 if "far" in stem else 4)
        for h in guides["hulls"]:
            if h["layer"] not in stem or h["class"] == "lasher":
                continue
            x0, y0, x1, y1 = h["frame_box"]
            px = ((x0 + x1) / 2 - FLOTILLA_WINDOW[0]) * 2
            py = ((y0 + y1) / 2 - FLOTILLA_WINDOW[1]) * 2
            b.light((px, py), (255, 255, 255), strobe(r.uniform(1.6, 2.4), r.uniform(0, 2), double=True), size=0.55)
            b.light(((x0 - FLOTILLA_WINDOW[0]) * 2 + 5, (y1 - FLOTILLA_WINDOW[1]) * 2 - 6), (255, 60, 50),
                    lambda t: 0.55, size=0.45)
        bodies.append((z0, b.add_to))

    # the two Lashers peel off toward the inbound lane: the patrol C2 meets
    for i, h in enumerate(peel):
        x0, y0, x1, y1 = h["frame_box"]
        p0 = unproject((x0 + x1) / 2, (y0 + y1) / 2, FLOTILLA_Z["heg-flotilla-far"])
        t0 = 9.0 + 1.6 * i
        s = (lambda t0: lambda t: 3.0 * t + 0.5 * 5.0 * max(0.0, t - t0) ** 2)(t0)
        gate = (lambda t0: lambda t: 0.35 + 0.65 * min(1.0, max(0.0, (t - t0) / 1.2)))(t0)
        b = lane_body("patrol-%d" % i, "lasher", "flot", p0, d + heading(FLOTILLA_HEADING + 90) * 0.12 * (i - 0.5),
                      s, tint=(1.15, 0.86, 0.70), seed=300 + i, engine_gate=gate, engine_strength=0.8)
        bodies.append((b.mid_z(duration), b.add_to))
