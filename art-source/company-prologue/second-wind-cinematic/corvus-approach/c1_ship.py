"""C1 Second Wind: the entrance and the run toward Jangala.

Owned by the ship pass. `build(add, duration)` adds Second Wind and its engine glows.

Second Wind is the one 3D body in the shot, so it is not a fixed card. Its flight is
a true path in the plate camera's own space (x right, y up, z toward camera; the
camera at the origin looking down -z, 50 mm across 36 mm, as render_planet.py):

- **Range** z(t) climbs from SW_RANGE_START_M to SW_RANGE_END_M without ever
  levelling off, so the ship keeps shrinking at a perceptible rate to the last frame.
- **Screen track** S(t) is what a body flying straight at a vanishing point does:
  it closes on that point in proportion to how fast its range grows. The vanishing
  point swings from SW_HEADING_START_PX onto Jangala during SW_TURN: the pilot's yaw
  correction, and the reason the track bends.
- **Attitude** is authored against the line of sight (a velocity-aligned hull flying at
  a planet near the camera axis is seen dead astern, deck edge-on): the camera sits
  SW_VIEW_ELEVATION_DEG above the deck and SW_VIEW_AZIMUTH_DEG off the starboard
  quarter. The azimuth swinging toward astern is the yaw to starboard onto Jangala,
  led by SW_ATTITUDE_LEAD_S, banked into the turn, with a slow trim roll.
- **Parallax** depth(t) comes from range through c1_core.depth_for, the traffic's camera model.

render_ship_sequence.py renders the master along exactly this path (it imports
`plan`) into layers/second-wind-seq/, one image per sample, with nozzle projections
alongside. Each image is cropped around the ship in plate pixels, so placement here is
the analytic track plus a perspective scale for any time between samples. If the
sequence is missing or was rendered for a different motion, the fixed entrance card
flies the same track instead (and says so).
"""
import hashlib
import json
import math
from functools import lru_cache

import numpy as np
from PIL import Image

from c1_core import BASE, H, PLANET, W, depth_for, glow_dot

# ---------------------------------------------------------------- scale and distance
# c1-layout.md: on-screen px = real length * FOCAL_PX / range; parallax depth ~ 1 / range.
FOCAL_PX = W * 50.0 / 36.0           # 2666.7 px: render_planet.py's 50 mm lens on a 36 mm sensor
# Real scale comes from the ship pipeline, not from here: second-wind-pipeline.md sets one
# metres-per-unit for every state and publishes it in geometry.json. Distances below are in hull
# lengths, so a rescale is that one number and a re-render; the picture is unchanged by it.
GEOMETRY_JSON = BASE.parents[1] / "second-wind-geometry" / "states" / "patched" / "geometry.json"
SW_LENGTH_UNITS = 100.9              # bow to stern of the model's X extent, in hull units
SW_METRES_PER_UNIT = json.loads(GEOMETRY_JSON.read_text(encoding="utf-8"))["metres_per_unit"]
SW_LENGTH_M = SW_LENGTH_UNITS * SW_METRES_PER_UNIT   # about 66 m at 0.65 m per unit
SW_BEAM_M = 0.74 * SW_LENGTH_M       # outrigger to outrigger, for reference: it is a wide hull
SW_RANGE_START_M = 2.0 * SW_LENGTH_M      # camera depth at t = 0, just off frame bottom left
SW_RANGE_END_M = 23.3 * SW_LENGTH_M       # at the last frame: small, still receding
SW_RANGE_EVEN = 0.8                  # share of the log-range climb spread evenly; the rest is front-loaded

# ---------------------------------------------------------------- track and attitude
SW_START_PX = (-250.0, 1280.0)       # screen position of the hull centre at t = 0
SW_HEADING_START_PX = (850.0, 160.0)   # vanishing point of the entry heading: the inbound lane, high and left
SW_HEADING_END_PX = (PLANET[0] + 110.0, PLANET[1] + 40.0)   # corrected to starboard, onto Jangala
SW_TURN = (5.0, 30.0)                # seconds over which the heading is corrected (smoothstep)
SW_VIEW_AZIMUTH_DEG = (32.0, 8.0)    # camera off the stern toward starboard: quarter view -> nearly astern
SW_VIEW_ELEVATION_DEG = (30.0, 21.0)  # camera above the deck plane
SW_ATTITUDE_LEAD_S = 1.5             # the bow leads the track through the turn
SW_BANK_GAIN = 7.0                   # bank degrees per degree/second of yaw rate (peak about 10 degrees)
SW_BANK_MAX_DEG = 14.0
SW_ROLL_WAVE = (1.2, 17.0)           # a pilot's slow trim: amplitude degrees, period seconds
SEQ_DIR = BASE / "layers" / "second-wind-seq"
GLOW_COLOR = (150, 205, 255)
GLOW_OF_MOUTH = 0.55                 # halo radius as a share of the projected bell-mouth radius

_DT = 1.0 / 240.0


def _smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def range_at(t, duration):
    """Camera depth in metres. Log-range rises fastest at the entrance and keeps rising
    at SW_RANGE_EVEN of its mean rate at the end: no ease-out to rest."""
    u = max(0.0, t / duration)
    climb = math.log(SW_RANGE_END_M / SW_RANGE_START_M)
    c = SW_RANGE_EVEN
    shape = c * u + (1 - c) * (1 - (1 - u) ** 2)
    if u > 1:   # past the end keep the final rate rather than stopping
        shape = 1 + c * (u - 1)
    return SW_RANGE_START_M * math.exp(climb * shape)


def depth_of_range(z):
    return depth_for(z)         # the same camera model as the traffic (c1_core)


def _ray(sx, sy):
    return np.array([(sx - W / 2) / FOCAL_PX, -(sy - H / 2) / FOCAL_PX, -1.0])


def _unit(v):
    return v / np.linalg.norm(v)


class Motion:
    """The whole flight sampled at 240 Hz, with lookups at any t."""

    def __init__(self, duration):
        self.duration = duration
        n = int(math.ceil((duration + 2.0) / _DT)) + 1
        t = np.arange(n) * _DT - 0.5
        z = np.array([range_at(max(0.0, x), duration) for x in t])
        # Before t = 0 the ship is still off frame, on the same track (extrapolated rate).
        z0, z1 = range_at(0, duration), range_at(_DT, duration)
        pre = t < 0
        z[pre] = z0 * (z1 / z0) ** (t[pre] / _DT)
        s = np.zeros((n, 2))
        vp = np.zeros((n, 2))
        a, b = np.array(SW_HEADING_START_PX), np.array(SW_HEADING_END_PX)
        i0 = int(np.searchsorted(t, 0.0))
        s[i0] = SW_START_PX
        for i in range(n):
            vp[i] = a + (b - a) * _smooth((t[i] - SW_TURN[0]) / (SW_TURN[1] - SW_TURN[0]))
        for i in range(i0, n - 1):      # exact for a straight run at a fixed vanishing point
            s[i + 1] = vp[i] + (s[i] - vp[i]) * z[i] / z[i + 1]
        for i in range(i0, 0, -1):
            s[i - 1] = vp[i] + (s[i] - vp[i]) * z[i] / z[i - 1]
        p = np.array([z[i] * _ray(*s[i]) for i in range(n)])

        # Attitude is authored against the line of sight. Pointed along its own velocity,
        # a ship flying at a planet near the camera axis is seen dead astern: four bell
        # mouths and a deck edge-on. So the camera keeps an elevation above the deck and an
        # azimuth off the starboard quarter; the azimuth swinging toward dead astern IS the
        # yaw to starboard onto Jangala (led ahead of the track), banked into the turn.
        u = np.array([_smooth((x + SW_ATTITUDE_LEAD_S - SW_TURN[0]) / (SW_TURN[1] - SW_TURN[0])) for x in t])
        azim = SW_VIEW_AZIMUTH_DEG[0] + (SW_VIEW_AZIMUTH_DEG[1] - SW_VIEW_AZIMUTH_DEG[0]) * u
        elev = SW_VIEW_ELEVATION_DEG[0] + (SW_VIEW_ELEVATION_DEG[1] - SW_VIEW_ELEVATION_DEG[0]) * u
        yaw_rate = -np.gradient(azim, _DT)            # deg/s, + is a turn to starboard
        amp, period = SW_ROLL_WAVE
        bank = np.clip(SW_BANK_GAIN * yaw_rate, -SW_BANK_MAX_DEG, SW_BANK_MAX_DEG) \
            + amp * np.sin(2 * math.pi * t / period)      # + is starboard down
        fwd, up = np.zeros((n, 3)), np.zeros((n, 3))
        for i in range(n):
            a, e = math.radians(azim[i]), math.radians(elev[i])
            ks = np.array([-math.cos(e) * math.cos(a), -math.cos(e) * math.sin(a), math.sin(e)])  # to camera, ship axes
            js = _unit(np.array([0.0, 0.0, 1.0]) - ks[2] * ks)
            ship = np.array([np.cross(js, ks), js, ks])            # rows: I, J, K in ship axes
            kc = _unit(-p[i])                                      # to camera, camera axes
            j0 = _unit(np.array([0.0, 1.0, 0.0]) - kc[1] * kc)     # screen up
            th = -math.radians(bank[i])                            # starboard down turns clockwise on screen
            jc = j0 * math.cos(th) + np.cross(kc, j0) * math.sin(th)
            cam = np.array([np.cross(jc, kc), jc, kc])
            rot = cam.T @ ship                                     # ship axes -> camera axes
            fwd[i], up[i] = rot[:, 0], rot[:, 2]
        self.t, self.z, self.s, self.p, self.fwd, self.up = t, z, s, p, fwd, up
        self.azimuth, self.elevation, self.bank = azim, elev, bank

    def _at(self, arr, t):
        x = (t - self.t[0]) / _DT
        i = int(min(len(self.t) - 2, max(0, math.floor(x))))
        w = min(1.0, max(0.0, x - i))
        return arr[i] * (1 - w) + arr[i + 1] * w

    def screen(self, t):
        return self._at(self.s, t)

    def range(self, t):
        return float(self._at(self.z, t))

    def depth(self, t):
        return depth_of_range(self.range(t))

    def basis(self, t):
        """(position m, bow, port, up) in camera space."""
        f = _unit(self._at(self.fwd, t))
        u = _unit(self._at(self.up, t))
        u = _unit(u - np.dot(u, f) * f)
        return self._at(self.p, t), f, np.cross(u, f), u


@lru_cache(maxsize=4)
def motion(duration):
    return Motion(duration)


def motion_key(duration, fps):
    """Changes whenever anything that shapes the rendered sequence changes."""
    names = sorted(k for k in globals() if k.startswith(("SW_", "FOCAL_", "ORBIT_")))
    blob = json.dumps([[k, globals()[k]] for k in names] + [round(duration, 4), fps], default=str)
    return hashlib.sha1(blob.encode()).hexdigest()[:16]


def plan(duration, fps):
    """Samples for render_ship_sequence.py: one per 1/fps from t = 0 to just past the end."""
    m = motion(duration)
    out = []
    for i in range(int(math.ceil((duration + 0.25) * fps)) + 1):
        t = i / fps
        p, f, port, u = m.basis(t)
        out.append({"index": i, "t": t, "position": [float(x) for x in p], "bow": [float(x) for x in f],
                    "port": [float(x) for x in port], "up": [float(x) for x in u],
                    "screen": [float(x) for x in m.screen(t)], "range": m.range(t)})
    return {"duration": duration, "fps": fps, "key": motion_key(duration, fps), "focal_px": FOCAL_PX,
            "frame": [W, H], "length_m": SW_LENGTH_M, "samples": out}


# ---------------------------------------------------------------- compositing

class Sequence:
    def __init__(self, meta):
        self.meta = meta
        self.fps = meta["fps"]
        self.samples = meta["samples"]
        self.by_index = {s["index"]: s for s in self.samples}
        self.indices = np.array(sorted(self.by_index))

    def sample(self, t):
        """The rendered sample nearest t (a partial render, --only, holds its nearest)."""
        want = round(t * self.fps)
        k = int(np.searchsorted(self.indices, want))
        near = [self.indices[j] for j in (k - 1, k) if 0 <= j < len(self.indices)]
        return self.by_index[int(min(near, key=lambda i: abs(i - want)))]

    @lru_cache(maxsize=6)
    def load(self, index):
        s = self.by_index[index]
        if not s.get("file"):
            return Image.new("RGBA", (2, 2), (0, 0, 0, 0))
        return Image.open(SEQ_DIR / s["file"]).convert("RGBA")


def load_sequence(duration):
    path = SEQ_DIR / "meta.json"
    if not path.exists():
        return None
    meta = json.loads(path.read_text(encoding="utf-8"))
    if meta.get("key") != motion_key(duration, meta.get("fps")):
        print("c1_ship: layers/second-wind-seq/ was rendered for another motion; "
              "re-run render_ship_sequence.py. Flying the fixed card meanwhile.")
        return None
    missing = [s["index"] for s in meta["samples"] if s.get("file") and not (SEQ_DIR / s["file"]).exists()]
    if missing:
        print("c1_ship: %d sequence frames missing (first %d); flying the fixed card." % (len(missing), missing[0]))
        return None
    return Sequence(meta)


def build(add, duration):
    m = motion(duration)
    seq = load_sequence(duration)
    if seq is None:
        _build_card(add, duration, m)
        return

    def path(t):
        s = seq.sample(t)
        ss = s["supersample"]
        scale = s["range"] / m.range(t) / ss       # held image, current range: perspective scale
        ox, oy = s["origin_px"]
        iw, ih = s["size"]
        sx, sy = m.screen(t)
        return sx + (iw / 2 - ox) * scale, sy + (ih / 2 - oy) * scale, scale

    def frames(t):
        return seq.load(seq.sample(t)["index"])

    sw = add("second-wind", None, m.depth, None, path=path, frames=frames)

    @lru_cache(maxsize=64)
    def dot(radius_q):
        return glow_dot(radius_q / 4.0, GLOW_COLOR, 0.9)

    dark = {"x": -1e4, "y": -1e4, "r": 1.0, "facing": 0.0, "visible": 0.0}   # sample with the ship off frame

    def nozzle(t, j):
        noz = seq.sample(t).get("nozzles", [])
        return noz[j] if j < len(noz) else dark

    for j in range(max(len(s.get("nozzles", [])) for s in seq.samples)):
        if j + 1 not in LIT_ENGINES:      # nozzles are sorted by name: index j is Engine j+1
            continue

        def offset(t, j=j):
            n = nozzle(t, j)
            return n["x"], n["y"]

        def glow(t, j=j):
            return dot(max(4, round(nozzle(t, j)["r"] * GLOW_OF_MOUTH * 4)))

        def strength(t, j=j):
            n = nozzle(t, j)
            return n["visible"] * min(1.0, max(0.0, (n["facing"] - 0.15) / 0.45))

        add("sw-engine-%d" % j, None, m.depth, None, additive=True, parent=sw, offset=offset,
            frames=glow, alpha=strength)


# Arrival is the patched state: only the starboard pod (Engines 3 and 4) runs; the port pod is cold.
LIT_ENGINES = (3, 4)


# The fixed entrance card (render_ship_card.py --view entrance) flown along the same track.
ENTRANCE_NOZZLES = [(255, 815), (370, 815), (1005, 1005), (1085, 1025)]   # bell mouths, 2048x1152 render


def _build_card(add, duration, m):
    card = Image.open(BASE / "layers" / "second-wind-entrance.png").convert("RGBA")
    box = card.getchannel("A").getbbox()
    card = card.crop(box)
    # The card's crop is about 1.25 hull lengths across in projection from astern three-quarter.
    full_w = 1.25 * SW_LENGTH_M * FOCAL_PX

    def path(t):
        sx, sy = m.screen(t)
        return sx, sy, full_w / m.range(t) / card.width

    sw = add("second-wind", card, m.depth, None, path=path)
    for i, (nx, ny) in enumerate(ENTRANCE_NOZZLES):
        add("sw-engine-%d" % i, glow_dot(16, GLOW_COLOR, 0.9), m.depth, None, additive=True,
            parent=sw, offset=(nx - box[0], ny - box[1]))
