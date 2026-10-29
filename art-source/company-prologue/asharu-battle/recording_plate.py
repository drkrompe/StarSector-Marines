"""The Hegemony security terminal as a finished film plate: S0 opens on it, S11's last second and a half
breaks into it, and S13 closes on it (asharu-battle-shot.md, "Framing: the Hegemony's feed").

The owner, 2026-10-26: the viewer watches a Hegemony security terminal that tracks the sector's traffic
through every system's comm relay and every fleet's pings, and ranks what it sees for an intel analyst;
the Asharu fight is one minor item among many. It is a 90s/00s science-fiction terminal in the
Hegemony's red and orange: one phosphor, vector maps, inverse-video bars, a curved and scanlined tube.

It drills down in three views, never everything at once:
  SECTOR  the wide Hegemony overview: a few priority tracks across the sector, a routine sweep
  CORVUS  the system opened up: its local tracks, then an unknown off the Asharu platform, flagged
  EVENT   the flag opened in depth: attribution, force, significance, the terminal's own lookups
S0 runs SECTOR > CORVUS > EVENT and cuts to the picture on the optic it tasks (S1a). S13 runs back out:
the event downgraded as the pirates weigh as minor, Corvus, then the sector, where the event is
archived as not worth escalating among the threats that matter.

The plate carries everything but words: the chrome, the maps, markers, bars, gauges and scope, the
transitions and the tube. Every word is the game's, drawn at run time, localised; LAYOUT is where each
goes (per view), written out with --layout with the timeline the views move to. --words draws sample
words in, for judging the plate, never for the film.

Every frame is computed from its own time alone, so any frame renders the same alone, in any order.

    python recording_plate.py s0|s13 <out dir> [--words] [--frames A:B] [--percent P]
    python recording_plate.py s11 <out dir> --under <dir of the S11 frames> [--words]
    python recording_plate.py --layout <file.json>

Frames are '<kf>_f<sequence frame>.png' at 24 fps, kf 'rec0' (S0), 'm11' (S11), 'rec13' (S13).
"""
import json
import math
import random
import sys
from pathlib import Path

import radio_log

W, H, FPS = 1920, 1080, 24
# The owner, 2026-10-26: linger on each view, S0 need not hold to 10 s. S0 therefore opens with a pre-roll
# before the radio, in negative sequence time, so nothing after it moves: the terminal boots and shows the
# sector and Corvus in silence, and the event view opens as the tap brings the convoy's channel in at
# 1.8 s. S13 runs on past the old END OF RECORDING (radio_log.END_CARD_S) to hold the pull-back out.
SHOTS = {'s0': ('rec0', -21.0, 10.0, 'open'), 's11': ('m11', 118.0, 126.0, 'static'),
         's13': ('rec13', 138.0, 162.5, 'close')}
RECORDING_END_S = 124.5         # S11: the picture breaks into the terminal's static here (asharu_preview)
POWER_ON = (-20.6, -20.0)       # S0: black, then the tube warming up
BOOT = (-20.0, -11.4)            # S0: the Hegemony's crest alone, then settling into the header
CREST_TO_HEADER = (-12.0, -11.4)
# the boot's words, typed at a pace to be read, then held: (slot, at)
BOOT_TYPE = {'name': -18.0, 'sub': -16.9, 'status': -13.9}
BOOT_CPS = 16
CREST_PATH = Path(__import__('os').environ.get(
    'STARSECTOR_DIR', r'C:\Program Files (x86)\Fractal Softworks\Starsector')) / 'starsector-core/graphics/factions/crest_hegemony.png'

# one phosphor: the Hegemony's orange (hegemony.faction), with red for what alarms it
BLACK = (6, 2, 1)
PH = (255, 136, 34)
PH_D = (160, 74, 18)
PH_DD = (78, 32, 9)
GRID = (36, 14, 5)
HOT = (255, 214, 160)
RED = (255, 46, 26)
RED_D = (140, 24, 12)

HEADER = (72, 36, 1848, 96)
CRUMB_Y = 108
VP = (72, 148, 1848, 952)       # the viewport the three views share
FOOT = (72, 966, 1848, 1044)
SCOPE = (276, 976, 560, 1034)

# The views' timeline, sequence seconds: (view, in, out), each revealed raster-wise on the way in, with a
# zoom box between views (in S0 out of the thing selected, in S13 back into it).
S0_VIEWS = [('sector', BOOT[1], -6.15), ('corvus', -6.0, -1.6), ('event', -1.45, 99.0)]
S13_VIEWS = [('event', 0.0, 149.0), ('corvus', 149.15, 152.6), ('sector', 152.75, 157.6)]
ZOOMS_S0 = [(-6.15, -5.85, 'corvus_mark', 'vp'), (-1.6, -1.3, 'flag', 'vp')]
ZOOMS_S13 = [(149.0, 149.3, 'vp', 'flag'), (152.6, 152.9, 'vp', 'corvus_mark')]
REVEAL_S = .4
# The camera on the tube's face: S0 ends pushing into the optic, the fleet action over Asharu, so the cut
# to picture lands inside it; S13 opens there and pulls back out to the whole screen.
OPTIC_CAM = (96, 222, 900, 674)                 # the optic panel, at the screen's aspect
CAM_IN = (8.7, 10.0)
CAM_OUT = (138.25, 139.4)

# the sector: the core worlds where the game puts them, hyperspace coordinates from its
# data/campaign/starmap.json (y up), and the priority tracks among them
SYSTEMS = {
    'ASKONIA': (-4400, -4500), 'ALGEBBAR': (-9200, -7900), 'ARCADIA': (-2500, -11900), 'AZTLAN': (5900, -7300),
    'CANAAN': (-15500, -8600), 'CORVUS': (400, -9400), 'DUZAHK': (-2300, -7950), 'EOS EXODUS': (-12700, -11400),
    'GALATIA': (2850, -11550), 'HYBRASIL': (-200, 400), 'ISIRAH': (-14900, -4400), 'KUMARIKANDAM': (-11800, -4600),
    'MAGEC': (-7200, -10700), 'MAYASURA': (-6000, -200), 'NARAKA': (11000, -7500), 'PENELOPE': (-9500, -14500),
    'SAMARRA': (4800, -14250), 'THULE': (-10600, -1200), 'TIA': (6300, -1600), 'TYLE': (-19100, -2700),
    'VALHALLA': (400, -3300), 'WESTERNESSE': (-13900, 1600), 'YMA': (3300, -500), 'ZAGAN': (-3900, 3200),
}
MAP = (VP[0] + 40, VP[1] + 70, 1380, VP[3] - 40)   # the map's share of the viewport; the list sits right
HOME = 'AZTLAN'
REACH = (6000, 12000)           # rings round the Hegemony's capital, in hyperspace units
# (code, system, faction, what, score): sample intel, each on a system that faction holds in the game;
# the game may fill its own
TRACKS = [('TT-0412', 'HYBRASIL', 'TRI-TACHYON', 'capital task group', 88),
          ('SD-1187', 'ASKONIA', 'SINDRIAN DIKTAT', 'fleet exercise', 83),
          ('LP-0930', 'KUMARIKANDAM', 'LUDDIC PATH', 'cell traffic', 79),
          ('PL-2201', 'THULE', 'PERSEAN LEAGUE', 'fleet assembly', 74),
          ('PR-1544', 'MAGEC', 'PIRATES', 'raider group', 68),
          ('UN-3310', 'ISIRAH', 'UNKNOWN', 'drive wake', 53)]
SWEEP = [(-9.6, 'SAMARRA'), (-8.4, 'GALATIA'), (-7.2, 'CORVUS')]   # the routine sweep, landing on Corvus
# Fleet tracking: each priority track is a fleet on a course, (from, to, speed, phase), a creep along the
# course and back (to None: holding round its system). Others' positions carry an uncertainty ring; the
# Hegemony's own fleets (HEG_FLEETS) are known exactly.
FLEET_COURSE = {'TT-0412': ('HYBRASIL', 'YMA', .007, .25), 'SD-1187': ('ASKONIA', None, 9.0, 0.0),
                'LP-0930': ('KUMARIKANDAM', 'THULE', .006, .3), 'PL-2201': ('THULE', 'MAYASURA', .006, .2),
                'PR-1544': ('MAGEC', 'ALGEBBAR', .008, .35), 'UN-3310': ('ISIRAH', None, 0.0, 0.0)}
HEG_FLEETS = [('HN-1', 'AZTLAN', 'SAMARRA', .006, .3), ('HN-4', 'NARAKA', 'AZTLAN', .007, .5),
              ('HN-7', 'AZTLAN', 'VALHALLA', .005, .6)]
LIST = (1416, 640, 1832, 940)   # the priority list, bottom right of the sector view

# Corvus, from the game's Corvus.java and its markets (econ/corvus.json): (orbit radius, angle) round the
# star. Asharu is an independent world, not the Hegemony's; Jangala is the Hegemony's, with its military
# base; Garnir, a moon of Barad, holds a pirate depot. The Hegemony cares about Asharu only so far as it
# touches its own lanes.
BODIES = {'asharu': (2800, 55), 'array': (2800, 115), 'jangala': (4500, 245), 'relay': (4500, 185),
          'jump': (4500, 305), 'gate': (6000, 0), 'barad': (7800, 100)}
GARNIR = (790, 135)             # round Barad
BELT = 5650                     # Nemo's Belt
PLOT_C, PLOT_S = (560, 572), 350 / 8600
LOCAL = (1180, 196, 1832, 700)  # the holdings and local tracks, right of the Corvus plot
# who holds what: (name, holder, what)
HOLDINGS = [('JANGALA', 'HEGEMONY', 'size 6  military base'),
            ('ASHARU', 'INDEPENDENT', 'size 4  local admin'),
            ('GARNIR', 'PIRATES', 'depot  known')]
# The Hegemony patrol's route, as plot points (r, angle): Jangala's lanes and the array. In S13, once the
# event is weighed as a local matter, the route is redrawn to cover the jump approach and drops the
# array leg, the one that came nearest Asharu: the Hegemony tends its own lanes, not Asharu's.
ROUTE_OLD = [(4500, 245), (4500, 185), (2800, 115), (3600, 330), (4500, 305), (4500, 245)]
ROUTE_NEW = [(4500, 245), (4500, 185), (3700, 230), (5300, 300), (4500, 305), (4500, 245)]
UNKNOWN_SEEN, FLAG_S = -3.6, -2.7

# the event view
OPTIC = (96, 196, 900, 700)     # the close track on Asharu's low orbit
CHANNELS = (96, 724, 900, 936)
ATTR_ORDER = ['UNKNOWN', 'INDEPENDENT', 'PIRATES', 'SINDRIAN DIKTAT', 'TRI-TACHYON', 'LUDDIC PATH', 'PERSEAN LEAGUE']
BAR_X0, BAR_X1 = 1340, 1660
ATTR_Y0, ATTR_H = 266, 28
OUTSIDE_GAP = 16               # attribution: the outside factions sit apart from the local parties
FORCE_Y = (528, 558)
FORCE_SCALE = 40
FORCE_BANDS = ((0, 14, 'PICKET'), (14, 28, 'PATROL'), (28, 40, 'FLEET'))
GAUGE = (1100, 652, 1832, 672)
SPARK = (1100, 684, 1832, 730)
THRESHOLD = 70
LOG_Y0, LOG_H, LOG_ENTRIES = 776, 20, 4

EVENT_OPEN = -1.45
RESULT_DELAY, TYPE_CPS = .3, 24     # a query types at TYPE_CPS; its answer lands RESULT_DELAY after
ARCHIVE_S = 153.4              # the end card: the event archived, on the sector view (was END_CARD_S)
# the Outro, after the archive: the sweep moves on to the systems that matter, then the terminal goes
# back to the crest's slate and the tube tunes out, the boot in reverse
SWEEP_OUT = [(154.4, 'CORVUS'), (155.0, 'AZTLAN'), (156.2, 'HYBRASIL')]
CREST_BACK = (157.7, 158.3)
SLATE_OUT_TYPE = 158.4
TUNE_OUT = (161.0, 161.9)
ROLL_LOCK_S = 1.0               # S0: after the tube opens, the picture rolls and the snow clears, this long
LIST_GONE_S = ARCHIVE_S + .7
TAP_CLOSE_S = radio_log.CLOSING[-1][3] + .2    # S13: the tap closes as the last survivor's line ends
ADVISED_S = 150.2               # S13, on Corvus: the patrol route redrawn, Asharu left outside it

# The query log: (t typed, call, answer, kind). The terminal's lookups; their answers move the numbers.
QUERIES = [
    (-1.2, 'track.open(CV-2215)', 'UNREGISTERED  ~6 returns  ASHARU PLATFORM', 'flag'),
    (-.05, 'relay.tap(CORVUS, "convoy common")', 'OPEN', 'ok'),
    (1.95, 'link(CV-2215, GARNIR DEPOT)', 'unconfirmed  radio dark', 'flag'),
    (3.75, 'transponder.near(CV-2215)', 'ISS CARAVEL  12 hulls  INDEPENDENT', 'note'),
    (6.0, 'registry.origin(ISS CARAVEL)', 'not Corvus  out-system freight', 'note'),
    (8.3, 'optic.task(OBS-3, CV-2215)', 'ACQUIRING', 'ok'),
    (39.6, 'sensor.sweep(ASHARU PLATFORM)', '11 drives  no transponders  UNREGISTERED', 'flag'),
    (51.2, 'engagement.detect(CV-2215)', 'weapons fire  Tanker lost', 'flag'),
    (87.0, 'transponder.lost(ISS SOBIESKI)', 'Freighter  boarded', 'flag'),
    (125.0, 'transponder.lost(ISS CARAVEL, ISS MORROW)', 'Heavy Freighter  atmospheric entry', 'flag'),
    (138.3, 'engagement.summary(CV-2215)', 'concluded  convoy dispersed  4 lost', 'note'),
    (140.0, 'faction.weight(PIRATES, CORVUS)', 'local  GARNIR-linked  no outside faction', 'down'),
    (142.0, 'threat.project(hostile, 30d)', 'picket-grade', 'down'),
    (144.0, 'analyst.threshold(CV-2215)', 'below 70  not queued', 'down'),
    (146.0, 'route(CV-2215, HSS JANGALA-2)', 'advisory sent', 'ok'),
]


def answered(q):
    """When a query's answer lands: its call typed out ('> ' and all), then RESULT_DELAY."""
    return q[0] + (len(q[1]) + 2) / TYPE_CPS + RESULT_DELAY


R = [answered(q) for q in QUERIES]
TAP_Q, OPTIC_Q = QUERIES[1], QUERIES[5]
WEIGHED = R[11]                 # S13: the pirates weighed as minor, and the event starts downgrading
NOT_ESCALATED_S = R[13]         # S13: below the threshold, not queued: the motif carried to the shutdown

# The event's significance, 0-100; THRESHOLD would queue it for the analyst. Flagged in Corvus, it climbs
# as the lookups land, spikes with the ambush to just short, and relaxes once the pirates weigh as minor.
SIG = [(FLAG_S, 22), (FLAG_S + .3, 30), (R[0] - .05, 30), (R[0] + .25, 34), (R[3] - .05, 34), (R[3] + .25, 40),
       (R[4] - .05, 40), (R[4] + .25, 44), (10, 45),
       (40.0, 45), (44, 56), (51.6, 63), (66, 67), (110, 62), (126, 60), (138, 58), (R[11] - .05, 58),
       (R[11] + .3, 46), (R[12] - .05, 46), (R[12] + .3, 32), (R[13] - .05, 32), (R[13] + .3, 24),
       (R[14] - .05, 24), (R[14] + .3, 21)]
ATTR = {
    'UNKNOWN': [(EVENT_OPEN, 1.0), (R[0] - .05, 1.0), (R[0] + .2, .72), (40, .7), (46, .2), (138, .06)],
    'INDEPENDENT': [(EVENT_OPEN, 0), (R[3] - .05, 0), (R[3] + .2, .95), (138, .97)],
    'PIRATES': [(EVENT_OPEN, 0), (R[0] - .05, 0), (R[0] + .2, .22), (40, .3), (52, .86), (138, .88), (R[11] - .05, .88), (R[11] + .3, .93)],
    'SINDRIAN DIKTAT': [(EVENT_OPEN, 0), (R[0] - .05, 0), (R[0] + .2, .06), (138, .01)],
    'TRI-TACHYON': [(EVENT_OPEN, 0), (R[0] - .05, 0), (R[0] + .2, .09), (R[4] - .05, .09), (R[4] + .2, .05), (138, .02)],
    'LUDDIC PATH': [(EVENT_OPEN, 0), (R[0] - .05, 0), (R[0] + .2, .04), (138, .01)],
    'PERSEAN LEAGUE': [(EVENT_OPEN, 0), (R[0] - .05, 0), (R[0] + .2, .02), (138, .01)],
}
FORCE_A = [(R[3] - .05, 0), (R[3] + .2, 12)]
# the convoy's four lost (the shot doc's tally): BATORY destroyed, SOBIESKI taken, WARDEN LEAD
# burned off the Falcon's hull, CARAVEL down through the atmosphere; the pirates lose two Hounds
FORCE_A_LOST = [(51, 0), (52, 1), (87, 1), (88, 2), (108.5, 2), (109.5, 3), (125, 3), (126, 4)]
FORCE_B = [(40.0, 0), (41, 11)]
FORCE_B_LOST = [(65, 0), (66, 1), (76, 1), (77, 2)]
FORCE_B_SPREAD = [(R[0] - .05, 0), (R[0] + .2, 15), (40.0, 15), (41, 1)]


def ramp(t, a, b):
    return max(0.0, min(1.0, (t - a) / (b - a))) if b > a else float(t >= a)


def ease(u):
    return u * u * (3 - 2 * u)


def track(points, t):
    """A keyframed value at t, eased between its keys and held past either end."""
    if t <= points[0][0]:
        return points[0][1]
    for (a, va), (b, vb) in zip(points, points[1:]):
        if t <= b:
            return va + (vb - va) * ease(ramp(t, a, b))
    return points[-1][1]


def flux(key, t, amp):
    """Intel is never still: a smooth wander of +-amp, its own for each key, a function of t alone."""
    h = sum(ord(ch) * (k + 1) for k, ch in enumerate(key)) % 97
    return amp * (.6 * math.sin(t * .9 + h) + .3 * math.sin(t * 2.3 + h * 1.7) + .1 * math.sin(t * 5.1 + h * .3))


def lines_for(mode):
    return radio_log.OPENING if mode == 'open' else radio_log.CLOSING if mode == 'close' else []


def envelope(t, lines):
    """How loud the channel is at t (0-1): each line a voice, syllables at about 5 a second, falling
    away at its commas and full stops, seeded by the line so it always speaks the same."""
    for _, who, t0, t1, text in lines:
        if t0 <= t <= t1:
            u = (t - t0) / (t1 - t0)
            # pauses where the line's punctuation falls, by its position in the text
            pause = 1.0
            for k, ch in enumerate(text):
                if ch in ',.?':
                    d = abs(u - k / len(text)) * (t1 - t0)
                    pause = min(pause, .15 + .85 * min(1.0, d / .18))
            syl = .55 + .45 * math.sin(t * 2 * math.pi * 4.7 + len(text)) * math.sin(t * 2 * math.pi * 1.3 + t0)
            edge = min(1.0, (t - t0) / .08, (t1 - t) / .12)
            return max(0.0, edge * pause * (.35 + .65 * abs(syl))), who
    return 0.0, None


def live(t, mode):
    """The callsigns lit at t, and those whose carrier is gone."""
    on = {ln[1] for ln in lines_for(mode) if ln[2] <= t <= ln[3]}
    dead = {k for k, t_off in radio_log.DROPS.items() if t >= t_off}
    if mode == 'static':        # the dying channels flicker as the plasma takes them
        rnd = random.Random(int(t * 1000))
        on |= {k for k, t_off in radio_log.DROPS.items() if t < t_off and rnd.random() > .35}
    return on, dead


def sig(t):
    return track(SIG, t)


def views(t, mode):
    """The views showing at t: (view, alpha, reveal), reveal the fraction of rows drawn in so far."""
    spans = S0_VIEWS if mode == 'open' else S13_VIEWS if mode == 'close' else [('event', 0, 999)]
    out = []
    for name, a, b in spans:
        if a - .01 <= t < b + .25:
            alpha = 1 - ramp(t, b, b + .25)
            reveal = 1.0 if a <= .01 else ramp(t, a, a + REVEAL_S)
            out.append((name, alpha, reveal))
    return out


def sys_xy(name):
    """A system's place on the sector map: the game's hyperspace coordinates, fitted into MAP."""
    xs, ys = [p[0] for p in SYSTEMS.values()], [p[1] for p in SYSTEMS.values()]
    k = min((MAP[2] - MAP[0]) / (max(xs) - min(xs)), (MAP[3] - MAP[1]) / (max(ys) - min(ys)))
    ox = MAP[0] + ((MAP[2] - MAP[0]) - k * (max(xs) - min(xs))) / 2
    oy = MAP[1] + ((MAP[3] - MAP[1]) - k * (max(ys) - min(ys))) / 2
    x, y = SYSTEMS[name]
    return ox + (x - min(xs)) * k, oy + (max(ys) - y) * k


def map_scale():
    xs, ys = [p[0] for p in SYSTEMS.values()], [p[1] for p in SYSTEMS.values()]
    return min((MAP[2] - MAP[0]) / (max(xs) - min(xs)), (MAP[3] - MAP[1]) / (max(ys) - min(ys)))


def plot_xy(r, ang):
    a = math.radians(ang)
    return PLOT_C[0] + r * PLOT_S * math.cos(a), PLOT_C[1] - r * PLOT_S * math.sin(a)


def unknown_marks(t):
    """The unknown's returns off the Asharu platform on the Corvus plot: a few, flickering."""
    ax, ay = plot_xy(*BODIES['asharu'])
    out = []
    for k in range(6):
        if random.Random(int(t * 7) * 31 + k).random() > .45:
            out.append((ax - 30 + k * 9, ay + 16 - (k % 3) * 8))
    return out


def rects():
    """The boxes the zooms run between."""
    cx, cy = sys_xy('CORVUS')
    ax, ay = plot_xy(*BODIES['asharu'])
    return {'vp': VP, 'corvus_mark': (cx - 16, cy - 16, cx + 16, cy + 16), 'flag': (ax - 44, ay - 6, ax + 24, ay + 34)}


def camera(t, mode):
    """The part of the screen the camera shows at t, or None for the whole of it."""
    full = (0, 0, W, H)
    if mode == 'open' and t >= CAM_IN[0]:
        return lerp_box(full, OPTIC_CAM, ease(ramp(t, *CAM_IN)) ** 1.6)
    if mode == 'close' and t < CAM_OUT[1]:
        return lerp_box(OPTIC_CAM, full, ease(ramp(t, *CAM_OUT)))
    return None


def look(a, box):
    """An HxWx3 float layer, cropped to box and scaled back up to the full frame."""
    import numpy as np
    from PIL import Image
    return np.stack([np.asarray(Image.fromarray(np.ascontiguousarray(a[..., k])).resize((W, H), Image.BICUBIC, box=box),
                                np.float32) for k in range(3)], -1)


def lerp_box(a, b, u):
    return tuple(p + (q - p) * u for p, q in zip(a, b))


_FONTS = {}


def font(size):
    """The judging font: Lucida Console, a terminal's own; the game draws its own."""
    from PIL import ImageFont
    if size not in _FONTS:
        try:
            _FONTS[size] = ImageFont.truetype('lucon.ttf', int(size * 1.38))
        except OSError:
            _FONTS[size] = ImageFont.load_default()
    return _FONTS[size]


class Canvas:
    """A layer and its glow, drawn together; words go through put()."""

    def __init__(self, words):
        from PIL import Image, ImageDraw
        self.im = Image.new('RGB', (W, H), BLACK)
        self.gl = Image.new('RGB', (W, H))
        self.d, self.g = ImageDraw.Draw(self.im), ImageDraw.Draw(self.gl)
        self.words = words

    def draw(self, fn, *a, glow=False, **k):
        getattr(self.d, fn)(*a, **k)
        if glow:
            getattr(self.g, fn)(*a, **k)

    def put(self, slot, text, fill=PH, dy=0, glow=False):
        """Sample words in a slot, if judging; returns where the text ends."""
        if not self.words or not text:
            return slot['x']
        f = font(slot['size'])
        w = self.d.textlength(text, font=f)
        x = slot['x'] - (w if slot['align'] == 'right' else w / 2 if slot['align'] == 'center' else 0)
        self.d.text((x, slot['y'] + dy), text, font=f, fill=fill)
        if glow:
            self.g.text((x, slot['y'] + dy), text, font=f, fill=fill)
        return x + w


def dim(c, a):
    return tuple(int(b + (v - b) * a) for v, b in zip(c, BLACK))


def blink(t, hz=2.0):
    return int(t * hz * 2) % 2 == 0


def brackets(c, box, col, n=10, w=2, glow=True):
    x0, y0, x1, y1 = box
    for x, y, sx, sy in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        c.draw('line', (x, y + sy * n, x, y, x + sx * n, y), fill=col, width=w, glow=glow)


def tag(c, x, y, slot, text, col=PH, inverse=False):
    """A boxed tag, sized to its words: inverse video for what the terminal wants the analyst to see."""
    tw = c.d.textlength(text, font=font(slot['size'])) if c.words else len(text) * slot['size'] * .83
    box = (x, y, x + tw + 14, y + slot['size'] * 1.38 + 8)
    c.draw('rectangle', box, fill=col if inverse else None, outline=col, width=1)
    c.put(dict(slot, x=x + 7, y=y + 4, align='left'), text, fill=BLACK if inverse else col)
    return box


def attr_y(k):
    """An attribution row's top: the local parties, then a gap, then the outside factions."""
    return ATTR_Y0 + k * ATTR_H + (OUTSIDE_GAP if k >= 3 else 0)


def S(x, y, size, align='left', text='', **kw):
    return dict(x=x, y=y, size=size, align=align, text=text, **kw)


# Where the game draws each word: pixels at 1920x1080, top-left origin; size the cap height the plate's
# spacing was drawn for; align the slot's anchor; text says what goes there. Grouped by view.
LAYOUT = {
    'chrome': dict(
        title=S(176, 50, 20, text='HEGEMONY NAVY  //  SECURITY DIRECTORATE  //  TRAFFIC ANALYSIS'),
        date=S(1832, 44, 13, 'right', text="the feed's date: arrival date − 3 cycles, the game's date format"),
        clock=S(1832, 68, 13, 'right', text='the feed clock, 06:41:12 at 0 s'),
        crumb=S(VP[0], CRUMB_Y, 13, text='the drill-down path: PERSEAN SECTOR > CORVUS > the event id'),
        status=S(VP[2], CRUMB_Y, 13, 'right', text='feed status: LIVE // the node'),
        verdict=S(0, 0, 12, text="the event's verdict, NOT ESCALATED, then ARCHIVED // NOT ESCALATED", at=NOT_ESCALATED_S),
        audio=S(88, 980, 12, text='RELAY AUDIO'),
        channel=S(88, 1008, 11, text='the tapped channel'),
        who=S(580, 980, 12, text='the speaking callsign'),
        line=S(580, 1004, 12, text='the line, localised, revealed as spoken', wrap=1500 - 580, line_h=18),
        counters=[S(x, 980, 10, text=k, value=S(x, 1002, 18, text=v)) for x, k, v in (
            (1530, 'TRACKS', 'tracked contacts'), (1640, 'FLAGGED', 'flagged events'), (1750, 'QUEUE', 'queued for the analyst'))],
    ),
    'boot': dict(
        name=S(960, 660, 26, 'center', text='HEGEMONY NAVY'),
        sub=S(960, 712, 14, 'center', text='SECURITY DIRECTORATE // TRAFFIC ANALYSIS'),
        status=S(960, 760, 12, 'center', text='ESTABLISHING SECTOR FEED, dots as it connects'),
    ),
    'sector': dict(
        title=S(VP[0] + 24, VP[1] + 14, 14, text='PERSEAN SECTOR // PRIORITY TRACKS, shown of tracked'),
        systems={n: S(sys_xy(n)[0] + 20, sys_xy(n)[1] + 8, 10, text=n) for n in SYSTEMS},
        track_tags={tr[0]: S(0, 0, 11, text=tr[0], at='by its fleet marker') for tr in TRACKS},
        fleet_tags=S(0, 0, 10, text='the Hegemony fleet code, by its marker'),
        legend=[S(VP[0] + 52, VP[3] - 74 + k * 22, 10, text=n) for k, n in enumerate(
            ('HEGEMONY FLEET', 'TRACKED FLEET  COURSE', 'POSITION UNCERTAINTY'))],
        list_title=S(LIST[0] + 12, LIST[1] + 9, 12, text='PRIORITY // SECTOR'),
        list_rows=dict(x=LIST[0] + 12, y0=LIST[1] + 46, line_h=40, size=12, fields='rank, code, system; score'),
        sweep=S(0, 0, 11, text='SWEEP  the system name, at the sweep box'),
        end_card=S(0, 0, 14, text='ARCHIVED // NOT ESCALATED', at=ARCHIVE_S, near='CORVUS'),
    ),
    'corvus': dict(
        title=S(VP[0] + 24, VP[1] + 14, 14, text='CORVUS // LOCAL TRACKS, count'),
        bodies={k: S(plot_xy(*v)[0] + (-14 if k == 'array' else 14), plot_xy(*v)[1] + 6, 10, 'right' if k == 'array' else 'left',
                     text=k.upper()) for k, v in BODIES.items()},
        local_title=S(LOCAL[0] + 12, LOCAL[1] + 9, 12, text='CORVUS // HOLDINGS'),
        holding_rows=dict(x=LOCAL[0] + 12, y0=LOCAL[1] + 46, line_h=34, size=12, fields='world, holder; what'),
        traffic_title=S(LOCAL[0] + 12, LOCAL[1] + 163, 12, text='CORVUS // LOCAL TRACKS'),
        local_rows=dict(x=LOCAL[0] + 12, y0=LOCAL[1] + 202, line_h=34, size=12, fields='code, kind; state'),
        garnir=S(0, 0, 10, text='GARNIR, by the depot'),
        route=S(0, 0, 10, text='the patrol route, and its adjustment'),
        asharu_note=S(0, 0, 11, text='ASHARU // INDEPENDENT // OUTSIDE PATROL', at=ADVISED_S),
        flag=S(0, 0, 12, text='FLAG  the event id', at=FLAG_S),
    ),
    'event': dict(
        title=S(VP[0] + 24, VP[1] + 14, 14, text='the event id // what // where'),
        state=S(VP[2] - 24, VP[1] + 14, 14, 'right', text="the event's state"),
        optic=S(OPTIC[0] + 10, OPTIC[1] + 8, 11, text='OPTIC // ASHARU LOW ORBIT'),
        platform=S(OPTIC[0] + 582, OPTIC[1] + 292, 10, text='PLATFORM'),
        tap=S(CHANNELS[0] + 10, CHANNELS[1] + 8, 11, text='TAP // the channel'),
        channels=[S(CHANNELS[0] + 34 + (i // 6) * 400, CHANNELS[1] + 36 + (i % 6) * 28, 11, text=ch,
                    dropped='  NO CARRIER' if ch in radio_log.DROPS else None)
                  for i, ch in enumerate(radio_log.SCREEN_CHANNELS)],
        attr_head=S(1100, 236, 11, text='ATTRIBUTION // PARTY TO EVENT'),
        attr_rows=[S(1100, attr_y(k), 12, text=f, pct=S(1832, attr_y(k), 12, 'right', text='role nn%'))
                   for k, f in enumerate(ATTR_ORDER)],
        outside=S(1100, attr_y(3) - OUTSIDE_GAP - 4, 10, text='OUTSIDE FACTION // its verdict: ? or NONE'),
        force_head=S(1100, 496, 11, text='FORCE // HULLS'),
        force_rows=[S(1100, FORCE_Y[0] - 2, 12, text='the convoy', count=S(1832, FORCE_Y[0] - 2, 12, 'right', text='n')),
                    S(1100, FORCE_Y[1] - 2, 12, text='the contact', count=S(1832, FORCE_Y[1] - 2, 12, 'right', text='n'))],
        force_bands=[S(BAR_X0 + (BAR_X1 - BAR_X0) * (a + b) / 2 / FORCE_SCALE, FORCE_Y[1] + 24, 9, 'center', text=n)
                     for a, b, n in FORCE_BANDS],
        sig_head=S(1100, 612, 11, text='SIGNIFICANCE // TO ANALYST'),
        verdict=S(1424, 604, 12, text='NOT ESCALATED', at=NOT_ESCALATED_S),
        sig_value=S(1832, 606, 20, 'right', text='the score'),
        threshold=S(GAUGE[0] + (GAUGE[2] - GAUGE[0]) * THRESHOLD / 100, 632, 9, 'center', text='ESCALATE 70'),
        log_head=S(1100, 750, 11, text='QUERY'),
        log_rows=dict(x=1100, y0=LOG_Y0, line_h=LOG_H, entries=LOG_ENTRIES, size=12, type_cps=TYPE_CPS, result_delay=RESULT_DELAY),
    ),
}
TIMELINE = dict(s0_views=S0_VIEWS, s13_views=S13_VIEWS, zooms_s0=ZOOMS_S0, zooms_s13=ZOOMS_S13, reveal_s=REVEAL_S,
                sweep=SWEEP, unknown_seen=UNKNOWN_SEEN, flag=FLAG_S, archive=ARCHIVE_S, list_gone=LIST_GONE_S,
                tap_close=TAP_CLOSE_S, threshold=THRESHOLD, significance=SIG, attribution=ATTR,
                force=dict(a=FORCE_A, a_lost=FORCE_A_LOST, b=FORCE_B, b_spread=FORCE_B_SPREAD, scale=FORCE_SCALE),
                queries=[dict(at=q[0], kind=q[3], sample=[q[1], q[2]]) for q in QUERIES])


def chevron(c, x, y, ang, col, s=7):
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    pts = [(s, 0), (-s, s * .7), (-s * .4, 0), (-s, -s * .7)]
    c.draw('polygon', [(x + p * ca - q * sa, y - p * sa - q * ca) for p, q in pts], outline=col, width=2)


def panel_list(c, box, title_slot, title):
    c.draw('rectangle', box, fill=BLACK, outline=PH_D, width=1)
    c.draw('rectangle', (box[0], box[1], box[2], box[1] + 32), fill=PH_D)
    c.put(title_slot, title, fill=BLACK)


def draw_sector(c, t, mode):
    L = LAYOUT['sector']
    for x in range(VP[0] + 24, VP[2] - 23, 48):          # hyperspace: a dotted grid
        for y in range(VP[1] + 24, VP[3] - 23, 48):
            c.draw('point', (x, y), fill=PH_DD)
    hx, hy = sys_xy(HOME)                               # the Hegemony's reach, rings round its capital
    for r in REACH:
        rr = r * map_scale()
        for k in range(0, 360, 4):
            c.draw('arc', (hx - rr, hy - rr, hx + rr, hy + rr), k, k + 2, fill=PH_DD)
    for n in SYSTEMS:
        x, y = sys_xy(n)
        if n == HOME:                                   # the Hegemony's own: a diamond
            c.draw('polygon', ((x, y - 9), (x + 9, y), (x, y + 9), (x - 9, y)), outline=PH, width=2, glow=True)
        else:
            c.draw('ellipse', (x - 4, y - 4, x + 4, y + 4), fill=PH_D)
        c.put(L['systems'][n], n, fill=PH_D)
    def pp(x):                                          # along a course and back
        return 1 - abs((x % 2) - 1)

    for code, a0, b0, speed, phase in HEG_FLEETS:       # the Hegemony's own fleets, known exactly
        (xa, ya), (xb, yb) = sys_xy(a0), sys_xy(b0)
        u = pp(phase + speed * t)
        x, y = xa + (xb - xa) * u, ya + (yb - ya) * u
        dashed(c, [(x, y), (xb, yb)], PH_DD, on=3, off=6)
        c.draw('polygon', ((x, y - 6), (x + 6, y), (x, y + 6), (x - 6, y)), outline=PH, width=2)
        c.put(dict(L['fleet_tags'], x=x + 10, y=y + 6), code, fill=PH_D)
    for k, (code, system, fac, what, score) in enumerate(TRACKS):   # the tracked fleets
        a0, b0, speed, phase = FLEET_COURSE[code]
        xa, ya = sys_xy(a0)
        if b0 is None:                                  # holding round its system
            ang = math.radians(phase + t * speed)
            x, y, hd = xa + 26 * math.cos(ang), ya - 26 * math.sin(ang), math.degrees(ang) + 90
        else:
            xb, yb = sys_xy(b0)
            u = pp(phase + speed * t)
            x, y = xa + (xb - xa) * u, ya + (yb - ya) * u
            hd = math.degrees(math.atan2(ya - yb, xb - xa)) + (180 if (phase + speed * t) % 2 > 1 else 0)
            c.draw('line', (xa, ya, x, y), fill=PH_DD, width=1)          # where it has been
            dashed(c, [(x, y), (xb, yb)], PH_D, on=4, off=6)            # its projected course
        if code == 'UN-3310':                           # an unknown's position wanders
            x, y = x + flux(code + 'x', t, 9), y + flux(code + 'y', t, 7)
        col = RED if score >= THRESHOLD + 10 else PH
        unc = (22 if code == 'UN-3310' else 12) + flux(code + 'r', t, 3)
        for q in range(0, 360, 30):                     # the position's uncertainty
            c.draw('arc', (x - unc, y - unc, x + unc, y + unc), q, q + 15, fill=dim(col, .5))
        chevron(c, x, y, hd, col, 6)
        on = blink(t + k * .23, 1.2) or score < THRESHOLD + 10
        brackets(c, (x - 10, y - 10, x + 10, y + 10), col if on else dim(col, .4), n=5)
        tag(c, x + unc + 4, y - 30, L['track_tags'][code], code, col=col)
    for k, slot in enumerate(L['legend']):              # the legend
        lx, ly = slot['x'] - 22, slot['y'] + 8
        if k == 0:
            c.draw('polygon', ((lx, ly - 6), (lx + 6, ly), (lx, ly + 6), (lx - 6, ly)), outline=PH, width=2)
        elif k == 1:
            chevron(c, lx, ly, 0, PH, 6)
            c.draw('line', (lx + 10, ly, lx + 16, ly), fill=PH_D)
        else:
            for q in range(0, 360, 30):
                c.draw('arc', (lx - 9, ly - 9, lx + 9, ly + 9), q, q + 15, fill=PH_D)
        c.put(slot, slot['text'], fill=PH_D)
    sweep = SWEEP if mode == 'open' else SWEEP_OUT if mode == 'close' else []
    if sweep:                                           # the routine sweep: to Corvus, and in the Outro on
        pos, name = None, None
        for i, (ta, na) in enumerate(sweep):
            tb = sweep[i + 1][0] if i + 1 < len(sweep) else 999
            if ta <= t < tb:
                u = ease(ramp(t, ta, ta + .3))
                prev, cur = sys_xy(sweep[max(0, i - 1)][1]), sys_xy(na)
                pos, name = (prev[0] + (cur[0] - prev[0]) * u, prev[1] + (cur[1] - prev[1]) * u), na
        if pos:
            s = 22
            brackets(c, (pos[0] - s, pos[1] - s, pos[0] + s, pos[1] + s), HOT, n=8)
            c.draw('line', (pos[0] - s - 12, pos[1], pos[0] - s - 3, pos[1]), fill=HOT)
            c.draw('line', (pos[0] + s + 3, pos[1], pos[0] + s + 12, pos[1]), fill=HOT)
            if name == sweep[-1][1] and t >= sweep[-1][0] + .3:
                tag(c, pos[0] - 70, pos[1] + s + 10, L['sweep'], 'SWEEP  ' + name, col=HOT, inverse=blink(t, 3))
            elif t >= sweep[0][0]:
                tag(c, pos[0] - 70, pos[1] + s + 10, L['sweep'], 'SWEEP  ' + name, col=PH_D)
    if mode == 'close':                                 # the event: Corvus marked, then archived
        x, y = sys_xy('CORVUS')
        a = 1 - ramp(t, ARCHIVE_S + .4, LIST_GONE_S)
        if a > 0:
            brackets(c, (x - 13, y - 13, x + 13, y + 13), dim(PH_D, a), n=7)
        if t >= ARCHIVE_S:
            k = ramp(t, ARCHIVE_S, ARCHIVE_S + .12)
            tag(c, x - 150, y + 26, L['end_card'], 'ARCHIVED // NOT ESCALATED', col=dim(PH, k), inverse=True)
        elif a > 0:
            tag(c, x + 18, y - 32, L['track_tags']['TT-0412'], 'CV-2215', col=PH_D)
    panel_list(c, LIST, L['list_title'], 'PRIORITY // SECTOR')
    lr = L['list_rows']
    rows = list(TRACKS)
    if mode == 'close' and t < LIST_GONE_S:
        rows = rows[:5] + [('CV-2215', 'CORVUS', 'PIRATES', '', round(sig(t)))]
    for k, (code, system, fac, what, score) in enumerate(rows):
        y = lr['y0'] + k * lr['line_h']
        hot = code == 'CV-2215'
        a = 1 - ramp(t, ARCHIVE_S + .4, LIST_GONE_S) if hot else 1
        if mode == 'close' and k == 5 and not hot:      # the routine item back in the slot
            a = ramp(t, LIST_GONE_S, LIST_GONE_S + .2)
        if not hot:                                     # the scores wander as the intel does
            score = score + flux(code, t, 2.5)
        col = dim(PH_D if hot else RED if score >= THRESHOLD + 10 else PH, a)
        slot = dict(x=lr['x'], y=y, size=lr['size'], align='left')
        c.put(slot, '%d  %-8s %s' % (k + 1, code, system), fill=col)
        c.put(dict(slot, x=LIST[2] - 12, align='right'), 'ARCHIVED' if hot and t >= ARCHIVE_S else '%d' % round(score), fill=col)
        for s in range(10):
            c.draw('rectangle', (LIST[2] - 150 + s * 8, y + 20, LIST[2] - 145 + s * 8, y + 26),
                   fill=col if s < int(score / 10) else GRID)
    c.put(L['title'], 'PERSEAN SECTOR // PRIORITY TRACKS  6 OF 1,284 // FLEET TRACKING', fill=PH)


def garnir_xy():
    bx, by = plot_xy(*BODIES['barad'])
    a = math.radians(GARNIR[1])
    return bx + GARNIR[0] * PLOT_S * math.cos(a), by - GARNIR[0] * PLOT_S * math.sin(a)


def along(pts, u):
    """The point and heading u (0-1) of the way round a polyline of screen points."""
    segs = [(p, q, math.hypot(q[0] - p[0], q[1] - p[1])) for p, q in zip(pts, pts[1:])]
    d = (u % 1) * sum(n for _, _, n in segs)
    for p, q, n in segs:
        if d <= n:
            k = d / n if n else 0
            return (p[0] + (q[0] - p[0]) * k, p[1] + (q[1] - p[1]) * k), math.degrees(math.atan2(p[1] - q[1], q[0] - p[0]))
        d -= n
    return pts[-1], 0.0


def dashed(c, pts, col, on=8, off=6, upto=1.0, width=1):
    """A dashed polyline, drawn up to a fraction of its length."""
    total = sum(math.hypot(q[0] - p[0], q[1] - p[1]) for p, q in zip(pts, pts[1:])) * upto
    d = 0.0
    for p, q in zip(pts, pts[1:]):
        n = math.hypot(q[0] - p[0], q[1] - p[1])
        k = 0.0
        while k < n and d + k < total:
            e = min(n, k + on, total - d)
            c.draw('line', (p[0] + (q[0] - p[0]) * k / n, p[1] + (q[1] - p[1]) * k / n,
                            p[0] + (q[0] - p[0]) * e / n, p[1] + (q[1] - p[1]) * e / n), fill=col, width=width)
            k += on + off
        d += n


def draw_corvus(c, t, mode):
    L = LAYOUT['corvus']
    cx, cy = PLOT_C
    for r in (2800, 4500, 6000, 7800):
        rr = r * PLOT_S
        c.draw('ellipse', (cx - rr, cy - rr, cx + rr, cy + rr), outline=PH_D if r == 2800 else PH_DD, width=1)
    rr = BELT * PLOT_S                                  # Nemo's Belt, dotted
    for k in range(0, 360, 3):
        a = math.radians(k)
        c.draw('point', (cx + rr * math.cos(a), cy - rr * math.sin(a)), fill=PH_D)
    c.draw('line', (cx - 400, cy, cx + 400, cy), fill=GRID)
    c.draw('line', (cx, VP[1] + 40, cx, VP[3] - 10), fill=GRID)
    sweep = (t * 45) % 360
    for k in range(18):
        a = math.radians(sweep - k * 1.6)
        c.draw('line', (cx, cy, cx + 380 * math.cos(a), cy - 380 * math.sin(a)), fill=dim(PH, .35 * (1 - k / 18)), width=2)
    for key, period, off in (('relay', 3.0, 0.0), ('array', 2.2, .9)):     # pings
        px, py = plot_xy(*BODIES[key])
        u = ((t + off) % period) / period
        rr = 8 + 170 * u
        c.draw('ellipse', (px - rr, py - rr, px + rr, py + rr), outline=dim(PH, .5 * (1 - u)), width=2)
    jp = plot_xy(*BODIES['jump'])                       # the lanes: the jump point to Jangala and to Asharu
    for key in ('jangala', 'asharu'):
        dashed(c, [jp, plot_xy(*BODIES[key])], PH_DD, on=4, off=8)
    c.draw('ellipse', (cx - 9, cy - 9, cx + 9, cy + 9), fill=HOT, glow=True)
    for key in ('asharu', 'jangala', 'barad'):
        px, py = plot_xy(*BODIES[key])
        r = 12 if key == 'barad' else 9
        c.draw('ellipse', (px - r, py - r, px + r, py + r), outline=PH, width=2)
    gx, gy = garnir_xy()
    c.draw('ellipse', (gx - 4, gy - 4, gx + 4, gy + 4), outline=PH_D, width=1)
    c.draw('polygon', ((gx + 10, gy - 12), (gx + 18, gy - 2), (gx + 2, gy - 2)), outline=RED, width=2)   # the pirate depot
    jx, jy = plot_xy(*BODIES['jangala'])
    c.draw('polygon', ((jx - 22, jy - 8), (jx - 16, jy - 14), (jx - 10, jy - 8), (jx - 16, jy - 2)), fill=PH, glow=True)   # Jangala Station
    ax, ay = plot_xy(*BODIES['asharu'])
    for key in ('relay', 'array'):
        px, py = plot_xy(*BODIES[key])
        c.draw('polygon', ((px, py - 7), (px + 7, py), (px, py + 7), (px - 7, py)), outline=PH, width=2, glow=True)
    c.draw('ellipse', (jp[0] - 9, jp[1] - 9, jp[0] + 9, jp[1] + 9), outline=PH_D, width=1)
    c.draw('ellipse', (jp[0] - 4, jp[1] - 4, jp[0] + 4, jp[1] + 4), outline=PH_D, width=1)
    px, py = plot_xy(*BODIES['gate'])
    c.draw('rectangle', (px - 7, py - 7, px + 7, py + 7), outline=PH_D, width=1)
    names = {'asharu': 'ASHARU', 'array': 'SENSOR ARRAY', 'jangala': 'JANGALA', 'relay': 'RELAY',
             'jump': 'JUMP POINT', 'gate': 'GATE', 'barad': 'BARAD'}
    for k, v in L['bodies'].items():
        c.put(v, names[k], fill=PH_D)
    c.put(dict(L['garnir'], x=gx + 22, y=gy - 14), 'GARNIR', fill=RED_D)

    # the Hegemony patrol and its route; in S13 the route is redrawn once the event is weighed local
    old = [plot_xy(*p) for p in ROUTE_OLD]
    new = [plot_xy(*p) for p in ROUTE_NEW]
    redraw = ramp(t, ADVISED_S, ADVISED_S + 1.2) if mode == 'close' else 0.0
    if redraw < 1:
        dashed(c, old, dim(PH, .55 * (1 - ramp(t, ADVISED_S, ADVISED_S + .5))), on=10, off=6)
    if redraw > 0:
        dashed(c, new, PH, on=10, off=6, upto=ease(redraw), width=2)
    route = new if redraw >= 1 else old
    for k in range(2):
        (px, py), hd = along(route, t * .012 + k * .5)
        chevron(c, px, py, hd, PH)
    if mode == 'close' and t >= ADVISED_S + 1.2:
        tag(c, new[3][0] + 12, new[3][1] + 8, L['route'], 'ROUTE A ADJ  +JUMP APPROACH', col=PH)
        tag(c, ax + 26, ay - 64, L['asharu_note'], 'ASHARU // INDEPENDENT // OUTSIDE PATROL', col=PH_D)

    # the rest of Corvus's traffic
    u = (t * .01 + .3) % 1                              # a trader in from the jump point to Jangala
    chevron(c, jp[0] + (jx - jp[0]) * u, jp[1] + (jy - jp[1]) * u, math.degrees(math.atan2(jp[1] - jy, jx - jp[0])), PH_D)
    u = (t * .008 + .6) % 1                             # one out of Asharu for the jump point
    chevron(c, ax + (jp[0] - ax) * u, ay + (jp[1] - ay) * u, math.degrees(math.atan2(ay - jp[1], jp[0] - ax)), PH_D)
    for k in range(2):                                  # Asharu's own defence, close in
        a = math.radians(t * 14 + k * 180)
        chevron(c, ax + 24 * math.cos(a), ay - 24 * math.sin(a), math.degrees(a) + 90, PH_D, 5)
    bx, by = plot_xy(*BODIES['barad'])
    for k in range(2):                                  # the Garnir pirates, known, round Barad
        a = math.radians(-t * 8 + k * 150)
        chevron(c, bx + 50 * math.cos(a), by - 50 * math.sin(a), math.degrees(a) - 90, RED_D, 5)
    a = math.radians(15 + t * 1.2)                      # a scavenger on the belt
    chevron(c, cx + BELT * PLOT_S * math.cos(a), cy - BELT * PLOT_S * math.sin(a), math.degrees(a) + 90, PH_DD, 5)
    for k in range(12):                                 # the convoy, a column on Asharu's orbit
        if mode != 'open' and k in (2, 5, 8, 11):
            continue
        a = math.radians(55 + 16 - k * 2.6 + (t * .2 if mode == 'open' else 0))
        x, y = cx + 3250 * PLOT_S * math.cos(a), cy - 3250 * PLOT_S * math.sin(a)
        c.draw('polygon', ((x, y - 3), (x + 3, y), (x, y + 3), (x - 3, y)), fill=PH)
    seen = t >= UNKNOWN_SEEN or mode != 'open'
    if mode == 'open' and seen:
        for x, y in unknown_marks(t):
            c.draw('line', (x - 4, y, x + 4, y), fill=RED, glow=True)
            c.draw('line', (x, y - 4, x, y + 4), fill=RED, glow=True)
    elif mode != 'open':
        for k in range(11):
            rr = random.Random(2216 + k)
            chevron(c, ax - 20 + rr.uniform(-12, 30), ay + rr.uniform(-10, 24), 200, RED, 5)
    flagged = t >= FLAG_S or mode != 'open'
    box = rects()['flag']
    if flagged:
        col = RED if mode == 'open' else PH_D
        brackets(c, box, HOT if mode == 'open' and t < FLAG_S + .6 and blink(t, 3) else col, n=9)
        tag(c, box[0] - 40, box[3] + 6, L['flag'], 'FLAG  CV-2215' if mode == 'open' else 'CV-2215  NOT ESCALATED // LOCAL',
            col=col, inverse=mode == 'open')

    # the holdings, and the local tracks
    panel_list(c, (LOCAL[0], LOCAL[1], LOCAL[2], LOCAL[1] + 150), L['local_title'], 'CORVUS // HOLDINGS')
    hr = L['holding_rows']
    for k, (name, holder, what) in enumerate(HOLDINGS):
        y = hr['y0'] + k * hr['line_h']
        col = PH if holder == 'HEGEMONY' else RED_D if holder == 'PIRATES' else PH_D
        slot = dict(x=hr['x'], y=y, size=hr['size'], align='left')
        c.put(slot, '%-9s %s' % (name, holder), fill=col)
        c.put(dict(slot, x=LOCAL[2] - 14, align='right'), what, fill=col)
    panel_list(c, (LOCAL[0], LOCAL[1] + 154, LOCAL[2], LOCAL[3]), L['traffic_title'], 'CORVUS // LOCAL TRACKS')
    lr = L['local_rows']
    adj = mode == 'close' and t >= ADVISED_S + 1.2
    # registry prefixes as the game writes them: HSS for the Hegemony, ISS for an independent; a pirate has none
    rows = [('HSS JANGALA-2', 'PATROL', 'ROUTE ADJ' if adj else 'ROUTE A', PH),
            ('ISS IN-3307', 'TRADER', 'INBOUND', PH_D), ('ISS IN-3311', 'TRADER', 'OUTBOUND', PH_D),
            ('AS-DEF', 'ASHARU DEFENCE', 'LOCAL', PH_D), ('GN-PAT', 'UNREGISTERED', 'GARNIR', RED_D),
            ('ISS SC-0088', 'SCAVENGER', 'BELT', PH_DD)]
    if mode == 'open':
        rows.append(('ISS CARAVEL', 'CONVOY 12', 'TRANSIT', PH))
        if seen:
            rows.append(('CV-2215', 'UNREGISTERED ~6', 'FLAG' if flagged else 'RESOLVING', RED))
    else:
        rows += [('ISS CARAVEL', 'CONVOY', '8 / 12', PH_D), ('CV-2215', 'UNREGISTERED 11', 'LOCAL MATTER', PH_D)]
    for k, (code, kind, state, col) in enumerate(rows):
        y = lr['y0'] + k * lr['line_h']
        slot = dict(x=lr['x'], y=y, size=lr['size'], align='left')
        if code == 'CV-2215' and mode == 'open' and flagged:
            c.draw('rectangle', (LOCAL[0] + 4, y - 8, LOCAL[2] - 4, y + 24), fill=RED)
            col = BLACK
        c.put(slot, '%-14s %s' % (code, kind), fill=col)
        c.put(dict(slot, x=LOCAL[2] - 14, align='right'), state, fill=col)
    c.put(L['title'], 'CORVUS // 3 HOLDERS // LOCAL TRACKS  %d' % len(rows), fill=PH)


def limb():
    """The optic's planet: its centre on screen and its radius (Asharu's limb, its brightest arc)."""
    ox0, oy0, ox1, oy1 = OPTIC
    pw, ph = ox1 - ox0 - 2, oy1 - oy0 - 2
    return ox0 + 1 + pw / 2, oy0 + 1 + ph - 150 + 1400, 1400


def limb_y(x):
    """The limb's height at screen x."""
    cx, cy, r = limb()
    return cy - math.sqrt(max(0.0, r ** 2 - (x - cx) ** 2))


def optic_label(c, x, y, text, col, size=9, glow=False, right=False):
    """A label in the optic, slid back inside the panel rather than clipped at its edge."""
    w = font(size).getlength(text) if c.words else 0
    x = x - w if right else x
    x = max(OPTIC[0] + 8, min(x, OPTIC[2] - 8 - w))
    y = max(OPTIC[1] + 26, min(y, OPTIC[3] - 8 - size * 1.4))
    c.put(dict(x=x, y=y, size=size, align='left'), text, fill=col, glow=glow)


# S13's optic, in optic-relative points: the wrecks (label, x, y, colour) and the prize
WRECKS = [('ISS BATORY  WRECK', 60, 300, PH_D), ('ISS WARDEN LEAD  WRECK', 560, 262, PH_D),
          ('WRECK', 720, 185, RED_D), ('WRECK', 720, 300, RED_D)]
PRIZE = ('ISS SOBIESKI  TAKEN', 620, 120)
# The two ships coming down, each a decaying orbit: (label, angle round the planet from overhead at the
# start and at the limb, in radians, height above the limb at the start in px, lag on the fall in s).
# Past the limb the track is projected on to the surface, to where each is reckoned to come down.
FALLING = [('ISS CARAVEL', .06, -.16, 175, 0.0), ('ISS MORROW', .08, -.13, 160, .7)]
FALL = (138.0, 150.0)
PROJECT = .22               # how far past the limb the projected track runs, as a share of the fall


def fall_at(f, v):
    """Where a falling ship is at v of her fall (0 the start, 1 the limb, more on to the projected
    surface): her height bleeds off slowly, then faster, as she follows the planet's curve down."""
    _, a0, a1, h0, _ = f
    cx, cy, r = limb()
    a = a0 + (a1 - a0) * v
    if v <= 1:
        rr = r + h0 * (1 - v ** 1.8)
        return cx + rr * math.sin(a), cy - rr * math.cos(a)
    x = cx + r * math.sin(a)                            # the projection: on to the near face, foreshortened
    return x, limb_y(x) + 260 * (v - 1)


def draw_wreckfall(c, t):
    """S13: what the optic keeps of the fight: the wrecks marked where they died, debris falling from them
    to the limb, the taken freighter among the pirates, and the two ships coming down in an arc over the
    limb, their tracks projected on to where they will land."""
    ox0, oy0 = OPTIC[:2]
    for w, (label, x, y, col) in enumerate(WRECKS):
        x, y = ox0 + x, oy0 + y
        c.draw('line', (x - 6, y - 6, x + 6, y + 6), fill=col, width=2)
        c.draw('line', (x - 6, y + 6, x + 6, y - 6), fill=col, width=2)
        optic_label(c, x + 10, y - 7, label, col)
        for k in range(5):                              # its debris, falling slow and steady to the limb
            rr = random.Random(7000 + w * 10 + k)
            fall = ((t - FALL[0]) * rr.uniform(4, 9) + rr.uniform(0, 300)) % 300
            dx, dy = x + rr.uniform(-14, 14) + fall * rr.uniform(-.1, .25), y + 8 + fall
            if dy < limb_y(dx) - 2:
                c.draw('point', (dx, dy), fill=col)
                c.draw('point', (dx, dy - 3), fill=dim(col, .5))
    x, y = ox0 + PRIZE[1], oy0 + PRIZE[2]
    c.draw('polygon', ((x, y - 6), (x + 6, y), (x, y + 6), (x - 6, y)), outline=RED, width=2)
    optic_label(c, x + 10, y - 7, PRIZE[0], RED)
    for k, f in enumerate(FALLING):
        lag = f[4]
        u = .3 + .7 * ease(ramp(t, FALL[0] + lag, FALL[1] + lag))
        end = 1 + PROJECT
        dashed(c, [fall_at(f, u + (end - u) * j / 30) for j in range(31)], PH_D, on=4, off=5)   # projected
        ix, iy = fall_at(f, end)                        # the reckoned crash site, on the face
        pulse = .5 + .5 * math.sin(t * 4 + k * 1.7)
        c.draw('ellipse', (ix - 16, iy - 5, ix + 16, iy + 5), outline=dim(RED, .5 + .5 * pulse), width=2)
        c.draw('line', (ix - 22, iy, ix - 18, iy), fill=RED_D)
        c.draw('line', (ix + 18, iy, ix + 22, iy), fill=RED_D)
        n = 28
        for j in range(n):                              # the plasma trail behind her, along the arc
            v0, v1 = max(0.0, u - .35 * (j + 1) / n), max(0.0, u - .35 * j / n)
            c.draw('line', (*fall_at(f, v0), *fall_at(f, v1)), fill=dim(HOT, (1 - j / n) ** 1.5), width=2, glow=True)
        hx, hy = fall_at(f, u)
        if u < 1:
            c.draw('polygon', ((hx, hy - 6), (hx + 6, hy), (hx, hy + 6), (hx - 6, hy)), fill=HOT, glow=True)
        optic_label(c, hx - 12 if k == 0 else hx + 12, hy - 28 if k == 0 else hy + 6,   # above left, below right
                    f[0] + ('  ENTRY' if u < 1 else '  BELOW LIMB'), HOT if u < 1 else PH_D, right=k == 0)
    ix, iy = fall_at(FALLING[1], 1 + PROJECT)
    optic_label(c, ix + 26, iy - 6, 'PROJ IMPACT  CARAVEL / MORROW', RED_D)


def draw_event(c, t, mode):
    from PIL import Image, ImageDraw
    L = LAYOUT['event']
    # the optic: Asharu's limb in vector arcs, the platform, the convoy, the contact
    ox0, oy0, ox1, oy1 = OPTIC
    pw, ph = ox1 - ox0 - 2, oy1 - oy0 - 2
    limb = Image.new('RGB', (pw, ph), BLACK)
    ld = ImageDraw.Draw(limb)
    for k in range(9):
        ld.arc((pw / 2 - 1400 + k * 40, ph - 150 + k * 40, pw / 2 + 1400 - k * 40, ph + 2650 - k * 40),
               200, 340, fill=PH_D if k == 0 else PH_DD, width=2 if k == 0 else 1)
    c.im.paste(limb, (ox0 + 1, oy0 + 1))
    c.draw('rectangle', OPTIC, outline=PH_D, width=1)
    for k in range(1, 8):
        x = ox0 + (ox1 - ox0) * k / 8
        c.draw('line', (x, oy0 + 1, x, oy0 + 6), fill=PH_D)
    plx, ply = ox0 + 560, oy0 + 300
    c.draw('rectangle', (plx - 14, ply - 6, plx + 14, ply + 6), outline=PH, width=2)
    c.put(L['platform'], 'PLATFORM', fill=PH_D)
    rnd = random.Random(2215)
    pts = []
    for k in range(12):
        if mode == 'open':
            x, y, lost = ox0 + 110 + k * 30 + t * 4, oy0 + 170 + 6 * math.sin(k * 1.3), False
        else:
            x, y, lost = ox0 + 50 + rnd.uniform(0, 250), oy0 + 50 + rnd.uniform(0, 150), k in (2, 5, 8, 11)
        if not lost:
            c.draw('polygon', ((x, y - 6), (x + 6, y), (x, y + 6), (x - 6, y)), outline=PH, width=2)
            pts.append((x, y))
    if mode == 'open':
        for k in range(6):
            if random.Random(int(t * 7) * 31 + k).random() > .4:
                x, y = plx - 80 + k * 22, ply + 26 - (k % 3) * 14
                c.draw('line', (x - 6, y, x + 6, y), fill=RED, width=2, glow=True)
                c.draw('line', (x, y - 6, x, y + 6), fill=RED, width=2, glow=True)
    else:
        for k in range(9):                              # the pirates, less the two Hounds lost
            rr = random.Random(2216 + k)
            x, y = ox0 + 450 + rr.uniform(0, 160), oy0 + 75 + rr.uniform(0, 115)
            c.draw('polygon', ((x + 7, y), (x - 6, y + 5), (x - 3, y), (x - 6, y - 5)), outline=RED, width=2)
        draw_wreckfall(c, t)
    b = (min(p[0] for p in pts) - 16, min(p[1] for p in pts) - 16, max(p[0] for p in pts) + 16, max(p[1] for p in pts) + 16)
    if mode == 'open' and t >= OPTIC_Q[0]:           # the optic closing on the convoy
        b = lerp_box(b, (ox0 + 10, oy0 + 30, ox1 - 10, oy1 - 10), 1 - ease(ramp(t, OPTIC_Q[0], R[5])))
        brackets(c, b, HOT, n=16, w=3)
        if t >= R[5]:
            c.put(dict(x=b[0], y=b[3] + 6, size=11, align='left'), 'OBS-3  ACQUIRING', fill=HOT, glow=True)
    else:
        brackets(c, b, PH, n=10)
    if mode != 'open':                                  # the optic labels its tracks once the transponders are read
        optic_label(c, b[0], b[3] + 6, 'ISS CARAVEL  8 / 12', PH, size=11)
        optic_label(c, ox0 + 450, oy0 + 42, 'UNREGISTERED  9 / 11', RED, size=11)
    elif t >= R[3] and not (t >= OPTIC_Q[0] and t >= R[5]):
        c.put(dict(x=b[0], y=b[3] + 6, size=11, align='left'), 'ISS CARAVEL  12', fill=PH)
    c.put(L['optic'], 'OPTIC // ASHARU LOW ORBIT', fill=PH_D)
    # the channels the tap has heard
    c.draw('rectangle', CHANNELS, outline=PH_D, width=1)
    on, dead = live(t, mode)
    tapped = mode != 'open' or t >= R[1]
    c.put(L['tap'], 'TAP // CONVOY COMMON' if tapped else 'TAP // --', fill=PH_D)
    level, who = envelope(t, lines_for(mode))
    rr = random.Random(int(round(t * FPS)))
    for slot in L['channels']:
        ch = slot['text']
        if min(ln[2] for ln in radio_log.LINES if ln[1] == ch) > t + .05 or not tapped:
            continue
        x, y = slot['x'] - 18, slot['y'] + 8
        if ch in on:
            c.draw('rectangle', (x - 6, y - 6, x + 6, y + 6), fill=PH, glow=True)
            lv = level if ch == who else rr.uniform(.2, .6)
            for s in range(8):
                c.draw('rectangle', (slot['x'] + 220 + s * 8, y - 5, slot['x'] + 225 + s * 8, y + 5),
                       fill=PH if s / 8 < lv * rr.uniform(.8, 1.2) else GRID)
        else:
            c.draw('rectangle', (x - 6, y - 6, x + 6, y + 6), outline=RED_D if ch in dead else PH_DD, width=2)
        c.put(slot, ch + (slot['dropped'] if ch in dead else ''), fill=RED if ch in dead else PH if ch in on else PH_D)
    # attribution, force, significance, the log
    c.draw('line', (1100, 228, 1832, 228), fill=PH_DD)
    c.put(L['attr_head'], 'ATTRIBUTION // PARTY TO EVENT', fill=PH_D)
    roles = {'INDEPENDENT': 'CONVOY', 'PIRATES': 'GARNIR' if mode != 'open' else ''}
    oy = attr_y(3) - OUTSIDE_GAP / 2 - 1                # the outside factions: the question the Hegemony cares about
    c.draw('line', (1100 + 230, oy, 1832, oy), fill=PH_DD)
    verdict = 'NONE' if mode != 'open' and t >= WEIGHED else '?'
    c.put(L['outside'], 'OUTSIDE FACTION  ' + verdict, fill=HOT if verdict == 'NONE' else PH_D)
    for k, (fac, slot) in enumerate(zip(ATTR_ORDER, L['attr_rows'])):
        y = attr_y(k) + 2
        v = track(ATTR[fac], t)
        if v > .01:                                     # the estimate never quite settles
            v = max(0.0, min(1.0, v + flux(fac, t, .025 if mode == 'open' and t < 10 else .012)))
        col = RED if fac in ('PIRATES', 'UNKNOWN') and v > .3 else PH
        n = int(round(v * 20))
        for s in range(20):
            x = BAR_X0 + s * (BAR_X1 - BAR_X0) / 20
            c.draw('rectangle', (x, y, x + (BAR_X1 - BAR_X0) / 20 - 4, y + 14), fill=col if s < n else GRID, glow=s < n and v > .3)
        c.put(slot, fac, fill=col if v > .3 else PH_D)
        role = roles.get(fac, '') if v > .3 else ''
        c.put(slot['pct'], (role + '  ' if role else '') + '%3d%%' % round(v * 100), fill=col if v > .3 else PH_D)
    c.draw('line', (1100, 488, 1832, 488), fill=PH_DD)
    c.put(L['force_head'], 'FORCE // HULLS', fill=PH_D)

    def fx(n):
        return BAR_X0 + (BAR_X1 - BAR_X0) * n / FORCE_SCALE
    for a0, a1, _ in FORCE_BANDS:
        c.draw('line', (fx(a0) + 2, FORCE_Y[1] + 20, fx(a1) - 2, FORCE_Y[1] + 20), fill=PH_D, width=2)
    for s in L['force_bands']:
        c.put(s, s['text'], fill=PH_D)
    na, lost = track(FORCE_A, t), track(FORCE_A_LOST, t)
    nb, spread, lost_b = track(FORCE_B, t), track(FORCE_B_SPREAD, t), track(FORCE_B_LOST, t)
    for k, (fy, n, total, col) in enumerate(((FORCE_Y[0], na - lost, na, PH), (FORCE_Y[1], nb - lost_b, nb, RED))):
        for s in range(FORCE_SCALE):
            fill = col if s < round(n) else RED_D if s < round(total) else GRID
            c.draw('rectangle', (fx(s), fy, fx(s + 1) - 3, fy + 14), fill=fill)
    if spread > 2:                                      # unresolved: what it might be
        for s in range(1, int(spread)):
            c.draw('rectangle', (fx(s), FORCE_Y[1] + 3, fx(s + 1) - 4, FORCE_Y[1] + 11), outline=RED_D)
    if na > .1:
        c.put(L['force_rows'][0], 'ISS CARAVEL GROUP', fill=PH)
        c.put(L['force_rows'][0]['count'], '%d' % round(na) + ('  LOST %d' % round(lost) if lost > .5 else ''), fill=PH)
    if spread > 2 or nb > .5:
        c.put(L['force_rows'][1], 'UNREGISTERED GROUP' if nb > .5 else 'UNRESOLVED', fill=RED)
        c.put(L['force_rows'][1]['count'], ('%d' % round(nb) + ('  LOST %d' % round(lost_b) if lost_b > .5 else ''))
              if nb > .5 else '1-15 ?', fill=RED)
    c.draw('line', (1100, 600, 1832, 600), fill=PH_DD)
    c.put(L['sig_head'], 'SIGNIFICANCE // TO ANALYST', fill=PH_D)
    s_now = sig(t) + flux('sig', t, .9)
    col = RED if s_now >= 60 else PH
    c.put(L['sig_value'], '%d' % round(s_now), fill=col, glow=True)
    g0, gy0, g1, gy1 = GAUGE
    for s in range(50):
        x = g0 + (g1 - g0) * s / 50
        c.draw('rectangle', (x, gy0, x + (g1 - g0) / 50 - 4, gy1), fill=col if s < s_now / 2 else GRID, glow=s < s_now / 2)
    tx = g0 + (g1 - g0) * THRESHOLD / 100
    c.draw('line', (tx, gy0 - 8, tx, gy1 + 8), fill=HOT, width=2)
    c.put(L['threshold'], 'ESCALATE 70', fill=HOT)
    if mode == 'close' and t >= NOT_ESCALATED_S:        # the verdict, stamped by the gauge
        tag(c, 1424, 604, L['verdict'], 'NOT ESCALATED', col=HOT if t < NOT_ESCALATED_S + .6 and blink(t, 4) else PH, inverse=True)
    sx0, sy0, sx1, sy1 = SPARK
    ty = sy1 - (sy1 - sy0) * THRESHOLD / 100
    for x in range(sx0, sx1, 10):
        c.draw('line', (x, ty, x + 4, ty), fill=PH_D)
    span = max(t, 10.0) - FLAG_S
    pts = [(sx0 + (sx1 - sx0) * k / 240, sy1 - (sy1 - sy0) * sig(FLAG_S + span * k / 240) / 100)
           for k in range(241) if FLAG_S + span * k / 240 <= t]
    if len(pts) > 1:
        c.draw('line', pts, fill=PH, width=2, glow=True)
    c.draw('line', (1100, 742, 1832, 742), fill=PH_DD)
    c.put(L['log_head'], 'QUERY', fill=PH_D)
    lr = L['log_rows']
    kinds = {'ok': PH, 'note': HOT, 'flag': RED, 'down': PH_D}
    for k, (t0, call, ans, kind) in enumerate([q for q in QUERIES if q[0] <= t][-lr['entries']:]):
        y = lr['y0'] + k * 2 * lr['line_h']
        slot = dict(x=lr['x'], y=y, size=lr['size'], align='left')
        end = c.put(slot, ('> ' + call)[:int((t - t0) * TYPE_CPS)], fill=PH)
        if t < answered((t0, call)):
            if blink(t, 3):
                c.draw('rectangle', (end + 3, y + 1, end + 13, y + 17), fill=PH, glow=True)
        else:
            c.put(dict(slot, y=y + lr['line_h']), '  = ' + ans, fill=kinds[kind])
    if mode == 'open':
        state = 'FLAGGED // RESOLVING' if t < R[5] else 'FLAGGED // OPTIC TASKED'
    else:
        state = 'ENGAGEMENT CONCLUDED' if t < WEIGHED else 'DOWNGRADING'
    c.put(L['title'], 'CV-2215 // ' + ('UNKNOWN CONTACT' if mode == 'open' else 'CONVOY AMBUSH') + ' // ASHARU LOW ORBIT', fill=PH)
    c.put(L['state'], state, fill=RED if mode == 'open' else PH, glow=mode == 'open')


DRAW = {'sector': draw_sector, 'corvus': draw_corvus, 'event': draw_event}


_CREST = None


def crest_src():
    """The game's Hegemony crest, read where the game keeps it (never copied into the repo), as one
    phosphor: its black disc black, its orange ring the screen's orange, its cream phoenix and stars hot."""
    global _CREST
    if _CREST is None:
        import numpy as np
        from PIL import Image
        if not CREST_PATH.exists():
            raise SystemExit(f'{CREST_PATH}: the Hegemony crest is read from the game install (set STARSECTOR_DIR)')
        a = np.asarray(Image.open(CREST_PATH).convert('RGB'), np.float32) / 255
        lum = a @ np.array([.299, .587, .114], np.float32)
        lo, hi = np.clip((lum - .12) / .33, 0, 1)[..., None], np.clip((lum - .45) / .35, 0, 1)[..., None]
        col = np.array(BLACK) + (np.array(PH) - np.array(BLACK)) * lo + (np.array(HOT) - np.array(PH)) * hi
        n = a.shape[0]
        yy, xx = np.mgrid[:n, :n]
        r = np.hypot(xx - (n - 1) / 2, yy - (n - 1) / 2) / (n / 2)
        _CREST = (col.astype(np.float32), r)
    return _CREST


def crest(size, t, spin, reveal=1.0):
    """The crest at size px, its ring of stars turned by spin degrees, a glint passing down it every five
    seconds, drawn in to reveal (0-1) row by row: (RGB image, mask)."""
    import numpy as np
    from PIL import Image
    col, r = crest_src()
    n = col.shape[0]
    img = Image.fromarray(col.astype(np.uint8))
    turned = np.asarray(img.rotate(spin, resample=Image.BICUBIC), np.float32)
    band = ((r > .64) & (r < .93))[..., None]
    a = np.where(band, turned, col)
    u = (t % 5.0) / .7                                  # the glint
    if u < 1:
        y = np.arange(n, dtype=np.float32)[:, None, None] / n
        a = a + np.exp(-((y - (u * 1.3 - .15)) / .05) ** 2) * (a.sum(2, keepdims=True) > 60) * 120
    mask = (r < .995).astype(np.float32)
    mask[int(n * reveal):] = 0
    im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).resize((size, size), Image.LANCZOS)
    return im, Image.fromarray((mask * 255).astype(np.uint8)).resize((size, size), Image.LANCZOS)


def ring_spin(t):
    return t * 4 + 300 * (1 - ease(ramp(t, BOOT[0] + ROLL_LOCK_S, BOOT[0] + ROLL_LOCK_S + 1.8)))


def draw_boot(c, t):
    """S0's boot: the crest drawn in large, the directorate named under it, then the crest settling into
    the header's corner as the terminal comes up."""
    L = LAYOUT['boot']
    out = t > 0                                         # the Outro: back from the header to the slate
    u = 1 - ease(ramp(t, *CREST_BACK)) if out else ease(ramp(t, *CREST_TO_HEADER))
    size = int(380 + (56 - 380) * u)
    cx, cy = 960 + (120 - 960) * u, 440 + (66 - 440) * u
    rev = 1.0 if out else ramp(t, BOOT[0] + ROLL_LOCK_S, BOOT[0] + ROLL_LOCK_S + 1.0)   # once the picture holds
    im, mask = crest(size, t, ring_spin(t), rev)
    c.im.paste(im, (int(cx - size / 2), int(cy - size / 2)), mask)
    c.gl.paste(im.point(lambda v: v // 2), (int(cx - size / 2), int(cy - size / 2)), mask)
    if rev < 1:
        y = cy - size / 2 + size * rev
        c.draw('line', (cx - size / 2, y, cx + size / 2, y), fill=HOT, width=2, glow=True)
    if out:
        a = ramp(t, CREST_BACK[1], CREST_BACK[1] + .3)
        status = 'FEED IDLE // TRACKING CONTINUES'
        lines = ((L['name'], 'HEGEMONY NAVY', CREST_BACK[1] - 9), (L['sub'], 'SECURITY DIRECTORATE  //  TRAFFIC ANALYSIS', CREST_BACK[1] - 9),
                 (L['status'], status, SLATE_OUT_TYPE))
    else:
        a = 1 - ramp(t, CREST_TO_HEADER[0], CREST_TO_HEADER[0] + .2)
        status = 'ESTABLISHING SECTOR FEED'
        dots = int(max(0.0, t - BOOT_TYPE['status'] - len(status) / BOOT_CPS) * 3)
        lines = ((L['name'], 'HEGEMONY NAVY', BOOT_TYPE['name']),
                 (L['sub'], 'SECURITY DIRECTORATE  //  TRAFFIC ANALYSIS', BOOT_TYPE['sub']),
                 (L['status'], status + ' ' + '.' * min(6, dots), BOOT_TYPE['status']))
    for slot, text, at in lines:
        if t >= at and a > 0:
            shown = text[:max(1, int((t - at) * BOOT_CPS))]
            c.put(slot, shown, fill=dim(HOT if slot is L['name'] else PH, a), glow=slot is L['name'])


def draw_chrome(c, t, mode, top, with_crest=True):
    """The header in inverse video with the Hegemony's mark, the crumb line, the viewport's frame, the foot
    with the relay audio and the counters."""
    Lc = LAYOUT['chrome']
    c.draw('rectangle', HEADER, fill=PH)
    c.draw('polygon', ((1468, 36), (1848, 36), (1848, 96), (1444, 96)), fill=BLACK)
    c.draw('line', ((1468, 38), (1846, 38), (1846, 94), (1446, 94), (1468, 38)), fill=PH, width=2)
    if with_crest:                                      # the Hegemony's crest, its stars turning
        im, mask = crest(56, t, ring_spin(t))
        c.im.paste(im, (92, 38), mask)
    c.put(Lc['title'], 'HEGEMONY NAVY // SECURITY DIRECTORATE // TRAFFIC ANALYSIS', fill=BLACK)
    c.put(Lc['date'], '14 RUULIN  C.203', fill=PH)
    clock = 6 * 3600 + 41 * 60 + 12 + t
    c.put(Lc['clock'], '%02d:%02d:%05.2f' % (clock // 3600, clock % 3600 // 60, clock % 60), fill=HOT)
    crumb = {'sector': 'PERSEAN SECTOR', 'corvus': 'PERSEAN SECTOR  >  CORVUS',
             'event': 'PERSEAN SECTOR  >  CORVUS  >  CV-2215'}[top]
    c.put(Lc['crumb'], crumb + (' _' if blink(t) else ''), fill=PH)
    c.put(Lc['status'], 'LIVE // NODE JANGALA RELAY', fill=RED if blink(t, .8) else RED_D)
    if mode == 'close' and NOT_ESCALATED_S <= t < CREST_BACK[0]:   # the verdict, carried through the Outro
        text = 'CV-2215  ARCHIVED // NOT ESCALATED' if t >= ARCHIVE_S else 'CV-2215  NOT ESCALATED'
        w = c.d.textlength(text, font=font(Lc['verdict']['size'])) if c.words else len(text) * 10
        flash = t < NOT_ESCALATED_S + .6 and blink(t, 4)
        tag(c, 1540 - w - 14, CRUMB_Y - 5, Lc['verdict'], text, col=HOT if flash else PH, inverse=True)
    x0, y0, x1, y1 = VP
    ch = 18
    c.draw('line', [(x0 + ch, y0), (x1 - ch, y0), (x1, y0 + ch), (x1, y1 - ch), (x1 - ch, y1), (x0 + ch, y1),
                    (x0, y1 - ch), (x0, y0 + ch), (x0 + ch, y0)], fill=PH, width=2)
    for k in range(1, 40):                              # the viewport's rulers
        x = x0 + (x1 - x0) * k / 40
        c.draw('line', (x, y1, x, y1 - (9 if k % 5 == 0 else 4)), fill=PH_D)
    for k in range(1, 20):
        y = y0 + (y1 - y0) * k / 20
        c.draw('line', (x1, y, x1 - (9 if k % 5 == 0 else 4), y), fill=PH_D)
    c.draw('rectangle', FOOT, outline=PH_D, width=1)
    c.draw('rectangle', (FOOT[0], FOOT[1], 262, FOOT[3]), fill=PH_D)
    tapped = mode != 'open' or t >= R[1]
    closed = mode == 'close' and t >= TAP_CLOSE_S
    c.put(Lc['audio'], 'RELAY AUDIO', fill=BLACK)
    c.put(Lc['channel'], 'CLOSED' if closed else 'CONVOY COMMON' if tapped else 'MONITOR 07', fill=BLACK)
    sx0, sy0, sx1, sy1 = SCOPE
    c.draw('rectangle', SCOPE, outline=PH_DD, width=1)
    c.draw('line', (sx0, (sy0 + sy1) / 2, sx1, (sy0 + sy1) / 2), fill=GRID)
    yc, amp = (sy0 + sy1) / 2, (sy1 - sy0) * .44
    lines = lines_for(mode)
    rnd = random.Random(int(round(t * FPS)) * 7919 + 17)
    pts, hot = [], 0.0
    for k in range(0, sx1 - sx0, 2):
        ts = t - .5 + .5 * k / (sx1 - sx0)
        e = envelope(ts, lines)[0] if not closed else 0.0
        n = random.Random(int(ts * 4000)).gauss(0, 1)
        v = e * amp * (.55 * n + .45 * math.sin(ts * 2 * math.pi * 180)) + (1 - e) * random.Random(k + int(t * 97)).gauss(0, .8)
        if mode == 'static' and t >= RECORDING_END_S:
            v += rnd.gauss(0, 1) * amp * .5 * min(1.0, (t - RECORDING_END_S) * 2)
        pts.append((sx0 + k, yc + max(-amp, min(amp, v))))
        hot = max(hot, e)
    c.draw('line', pts, fill=PH if hot > .02 else PH_D, width=2, glow=hot > .02)
    for _, who, t0, t1, line in lines:
        if t0 <= t <= t1 + 1.0 and not closed:
            c.put(Lc['who'], who, fill=HOT, glow=True)
            shown = line[:int(len(line) * ramp(t, t0, t1))]
            rows, cur = [], ''
            for wd in shown.split(' '):
                if c.words and c.d.textlength((cur + ' ' + wd).strip(), font=font(Lc['line']['size'])) > Lc['line']['wrap']:
                    rows.append(cur)
                    cur = wd
                else:
                    cur = (cur + ' ' + wd).strip()
            rows.append(cur)
            for k, r in enumerate(rows[-2:]):
                c.put(Lc['line'], r, fill=PH, dy=k * Lc['line']['line_h'])
    flagged = 37 + (mode != 'open' or t >= FLAG_S) - (mode == 'close' and t >= ARCHIVE_S)
    for slot, v in zip(Lc['counters'], ('%d,%03d' % divmod(1284 + int(t * 1.7) % 9, 1000), str(flagged), '6')):
        c.draw('line', (slot['x'] - 12, 974, slot['x'] - 12, 1036), fill=PH_DD)
        c.put(slot, slot['text'], fill=PH_D)
        c.put(slot['value'], v, fill=PH)


_WARP = None


def warp():
    """The tube's curve: for each screen pixel, where on the flat plate it reads (bilinear)."""
    global _WARP
    if _WARP is None:
        import numpy as np
        yy, xx = np.mgrid[:H, :W].astype(np.float32)
        nx, ny = (xx - W / 2) / (W / 2), (yy - H / 2) / (H / 2)
        k = (1 + .018 * (nx * nx + ny * ny)) * .99
        sx, sy = W / 2 + nx * k * W / 2, H / 2 + ny * k * H / 2
        x0, y0 = np.floor(sx).astype(np.int32), np.floor(sy).astype(np.int32)
        fx, fy = (sx - x0)[..., None], (sy - y0)[..., None]
        inside = ((sx >= 0) & (sx < W - 1) & (sy >= 0) & (sy < H - 1))[..., None]
        _WARP = (np.clip(x0, 0, W - 2), np.clip(y0, 0, H - 2), fx, fy, inside)
    return _WARP


def frame(t, mode, words=False, under=None):
    """One plate frame at sequence time t: a PIL RGB image, 1920x1080."""
    import numpy as np
    from PIL import Image, ImageDraw, ImageFilter
    showing = views(t, mode)
    top = max(showing, key=lambda v: v[1] * (.5 + v[2]))[0] if showing else 'sector'
    booting = mode == 'open' and t < CREST_TO_HEADER[1] or mode == 'close' and t >= CREST_BACK[0]
    c = Canvas(words)
    draw_chrome(c, t, mode, top, with_crest=not booting)
    base = np.asarray(c.im, np.float32)
    glow = np.asarray(c.gl, np.float32)
    if booting:                                         # the chrome wipes in as the crest settles
        edge = W * (1 - ease(ramp(t, *CREST_BACK)) if mode == 'close' else ease(ramp(t, *CREST_TO_HEADER)))
        cols = (np.arange(W) < edge)[None, :, None]
        base = np.where(cols, base, np.array(BLACK, np.float32))
        glow = glow * cols
        if 0 < edge < W:                                # the wipe's write head
            base[30:1050, int(edge):int(edge) + 3] += np.array(HOT, np.float32) * .6
        b = Canvas(words)
        draw_boot(b, t)
        base = base + np.asarray(b.im, np.float32) - np.array(BLACK, np.float32)
        glow = glow + np.asarray(b.gl, np.float32)

    # the views, each on its own layer, written into the viewport row by row
    x0, y0, x1, y1 = VP[0] + 3, VP[1] + 3, VP[2] - 2, VP[3] - 2
    for name, alpha, reveal in showing:
        v = Canvas(words)
        DRAW[name](v, t, mode)
        cut = int(y0 + (y1 - y0) * reveal)
        base[y0:cut, x0:x1] += (np.asarray(v.im, np.float32)[y0:cut, x0:x1] - np.array(BLACK, np.float32)) * alpha
        glow[y0:cut, x0:x1] += np.asarray(v.gl, np.float32)[y0:cut, x0:x1] * alpha
        if reveal < 1:                                  # the write head
            base[max(y0, cut - 2):cut + 1, x0:x1] += np.array(HOT, np.float32) * .55
    for za, zb, src, dst in ZOOMS_S0 if mode == 'open' else ZOOMS_S13 if mode == 'close' else []:
        if za <= t < zb:                                # the zoom box and its echoes
            u = ease(ramp(t, za, zb))
            R = rects()
            layer = Image.new('RGB', (W, H))
            ld = ImageDraw.Draw(layer)
            for k in range(3):
                ld.rectangle(lerp_box(R[src], R[dst], max(0.0, u - k * .12)), outline=dim(HOT if k == 0 else PH, 1 - k * .35), width=2)
            box = lerp_box(R[src], R[dst], u)
            for (ax, ay), (bx, by) in (((box[0], box[1]), R[src][:2]), ((box[2], box[3]), R[src][2:])):
                ld.line((ax, ay, bx, by), fill=PH_D)
            lay = np.asarray(layer, np.float32)
            base += lay
            glow += lay
    cam = camera(t, mode)
    if cam is not None:                                 # the camera pushed in on the tube's face
        base, glow = look(base, cam), np.clip(look(glow, cam), 0, None)

    # the tube: bloom, flicker, the rolling band, scanlines, grain, the curved glass, the vignette
    g = Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8))
    a = base + .8 * np.asarray(g.filter(ImageFilter.GaussianBlur(5)), np.float32)
    a += .45 * np.asarray(g.filter(ImageFilter.GaussianBlur(22)), np.float32)
    n = int(round(t * FPS))
    a *= 1 + .025 * random.Random(n * 3 + 1).uniform(-1, 1)
    ry = np.arange(H, dtype=np.float32) / H
    a *= (1 + .06 * np.exp(-((ry - ((t * .35) % 1.3 - .15)) / .05) ** 2))[:, None, None]
    a *= (1.0 - .22 * (np.arange(H) % 3 == 0) - .08 * (np.arange(H) % 3 == 1))[:, None, None]
    a += np.random.default_rng(n + 10000).normal(0, 3.0, (H, W, 1)) * np.array([1, .55, .3], np.float32)
    wx, wy, fx, fy, inside = warp()
    a = (a[wy, wx] * (1 - fx) * (1 - fy) + a[wy, wx + 1] * fx * (1 - fy)
         + a[wy + 1, wx] * (1 - fx) * fy + a[wy + 1, wx + 1] * fx * fy) * inside
    gy, gx = np.ogrid[:H, :W]
    r = np.hypot((gx - W / 2) / (W / 2), (gy - H / 2) / (H / 2))
    a *= (1.0 - .35 * np.clip(r - .55, 0, 1) ** 1.4)[..., None]
    if mode == 'open':
        a = crt_on(a, t, n)
    if mode == 'close':
        a = crt_off(a, t)
    out = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    if mode == 'static' and under is not None:
        out = break_up(t, under, out)
    return out


def crt_on(a, t, n):
    """A CRT set switched on: a dot, drawn out into a line, opening into the raster; then the picture
    rolling until the hold locks, snow clearing, and the brightness settling from its surge."""
    import numpy as np
    u = ramp(t, *POWER_ON)
    yy = np.abs(np.arange(H) - H / 2)[:, None, None]
    xx = np.abs(np.arange(W) - W / 2)[None, :, None]
    tint = np.array([1, .75, .5], np.float32)
    if t < POWER_ON[0]:
        return a * 0
    if u < 1:
        wide = W / 2 * ease(ramp(u, 0, .25))
        line = np.exp(-(yy / 3.0) ** 2) * (xx <= wide) * (1 - ramp(u, .45, 1)) * 420
        dot = np.exp(-((yy / 5.0) ** 2 + (xx / 5.0) ** 2)) * (1 - ramp(u, 0, .2)) * 500
        return a * (yy <= H / 2 * ease(ramp(u, .3, 1.0))) * (.3 + .7 * u) + (line + dot) * tint
    k = ramp(t, POWER_ON[1], POWER_ON[1] + ROLL_LOCK_S)
    if k < 1:
        off = int(H * .55 * (1 - ease(k)) ** 2)         # the vertical hold catching
        if off:
            a = np.roll(a, off, axis=0)
            a[max(0, off - 18):off] *= .1               # the blanking bar rolling through
        snow = np.random.default_rng(n + 20000).uniform(0, 1, (H // 2, W // 2, 1)).repeat(2, 0).repeat(2, 1)
        a = a * (1 + .35 * (1 - k)) + snow * 150 * (1 - k) ** 1.5 * tint
    return a


def crt_off(a, t):
    """The set switched off: the picture squashing to a bright line, the line to a dot, the dot fading."""
    import numpy as np
    if t < TUNE_OUT[0]:
        return a
    u = ramp(t, *TUNE_OUT)
    tint = np.array([1, .8, .6], np.float32)
    yy = (np.arange(H) - H / 2)[:, None, None]
    xx = (np.arange(W) - W / 2)[None, :, None]
    if u < .4:
        sv = max(.004, 1 - ease(u / .4))
        src = np.clip((H / 2 + yy[:, 0, 0] / sv).astype(np.int32), 0, H - 1)
        out = a[src] * (np.abs(yy) <= H / 2 * sv) * min(3.0, 1 / math.sqrt(sv))
        return out + np.exp(-(yy / (3 + 40 * sv)) ** 2) * 160 * ease(u / .4) * tint
    if u < .65:
        sh = 1 - ease((u - .4) / .25)
        return np.exp(-(yy / 2.5) ** 2) * (np.abs(xx) <= W / 2 * sh + 4) * 380 * tint
    glow = (1 - ramp(u, .65, 1.0)) ** 2
    return np.exp(-((yy / 4.0) ** 2 + (xx / 4.0) ** 2)) * 520 * glow * tint + 0 * a


def break_up(t, picture, screen):
    """S11: the picture breaking into the screen's static from RECORDING_END_S: torn rows of the picture
    and static first, the screen showing through the noise and taking the frame by the shot's end."""
    import numpy as np
    from PIL import Image
    if t < RECORDING_END_S:
        return picture
    k = min(1.0, (t - RECORDING_END_S) / (126.0 - RECORDING_END_S))
    rng = np.random.default_rng(int(round(t * FPS)) + 7)
    p = np.asarray(picture.convert('RGB').resize((W, H)), np.float32)
    s = np.asarray(screen, np.float32)
    noise = rng.uniform(0, 255, (H // 4, W // 4, 1)).repeat(4, 0).repeat(4, 1)
    rows = rng.random(H)[:, None, None] < .15 + .6 * k                           # torn bands
    shift = (rng.integers(-60, 60, H) * (rows[:, 0, 0])).astype(int)
    torn = p[np.arange(H)[:, None], (np.arange(W)[None, :] - shift[:, None]) % W]
    pic = np.where(rows, torn * .6 + noise * .4, p)
    mix = np.clip(k * 1.4 - .2, 0, 1)
    out = pic * (1 - mix) + (s * (1 - .5 * (1 - k)) + noise * .5 * (1 - k)) * mix
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def main(argv):
    if argv[0] == '--layout':
        Path(argv[1]).write_text(json.dumps(dict(size=[W, H], slots=LAYOUT, timeline=TIMELINE), indent=1, ensure_ascii=False),
                                 encoding='utf-8')
        return
    shot, out = argv[0], Path(argv[1])
    kf, t0, t1, mode = SHOTS[shot]
    words = '--words' in argv
    under = Path(argv[argv.index('--under') + 1]) if '--under' in argv else None
    a, b = round(t0 * FPS), round(t1 * FPS)
    if '--frames' in argv:
        a, b = (int(v) for v in argv[argv.index('--frames') + 1].split(':'))
    if shot == 's11':
        a = max(a, round(RECORDING_END_S * FPS))      # only the break-up; the picture before it is the render's
    pct = float(argv[argv.index('--percent') + 1]) if '--percent' in argv else 100.0
    out.mkdir(parents=True, exist_ok=True)
    from PIL import Image
    first = round(t0 * FPS)
    for n in range(a, b):
        # a shot with a pre-roll (S0) counts its frames from its own start; the others by sequence frame
        name = f'{kf}_f{n - first if first < 0 else n:05d}.png'
        pic = None
        if under is not None:
            src = under / name
            if not src.exists():
                raise SystemExit(f'{src}: the S11 frame to break up is missing')
            pic = Image.open(src)
        im = frame(n / FPS, mode, words, pic)
        if pct != 100.0:
            im = im.resize((round(W * pct / 100), round(H * pct / 100)), Image.LANCZOS)
        im.save(out / name)
    print(f'recording_plate {shot}: frames {a}..{b - 1} -> {out}')


if __name__ == '__main__':
    main(sys.argv[1:])
