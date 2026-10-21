"""Asharu battle: the blockout scene and its key frames (see asharu-battle-shot.md).

    python battle_blockout.py                 # build in Blender, render every key frame, label them
    python battle_blockout.py --frames k1,k4  # some key frames only
    python battle_blockout.py --samples 16    # quicker, noisier
    python battle_blockout.py --post windows  # ... and post the run to the Drive review folder
    python battle_blockout.py --skip-render   # relabel the last raw renders
    python battle_blockout.py --run r7        # output under build/asharu-battle/r7/ instead of today's date;
                                              # --post then dates the review folder by the day r7 began
    python battle_blockout.py --rerender      # render every frame even if a finished one is on disk
    python battle_blockout.py --root DIR      # put run folders under DIR (a render-queue job's {work})
    python battle_blockout.py --date D        # the review folder's date, when the run starts (default today)
    python battle_blockout.py --half          # render at half size (a light check; labels drawn at full size)
    python battle_blockout.py --stage-only    # build and pose the scene in Blender, render nothing (a pre-flight)
    python battle_blockout.py --stage-only --los   # ... and ray-cast every shot: does the round meet its target?

A frame already finished in the run folder (its PNG, its labels, and the same samples and source)
is not rendered again, so a job stopped part-way resumes where it stopped. Pass --run with a fixed
name for a job that may resume on another day.

Run as plain Python it is the driver: it launches Blender on itself (blender_path.py), then
labels the frames with Pillow, draws the orbit diagram and writes NOTES.md. Inside Blender it
builds the scene and renders. Output goes to <repo>/build/asharu-battle/<date>/ (ignored);
renders are never committed.

World frame, metres, co-moving with the convoy: +X prograde (the direction of orbit), +Y north,
+Z local up. Asharu's centre is straight below at (0, 0, -(R + ALT)). The planet is a dense
spherical cap under the battle so its limb is a true curve, painted with the game's own desert
map read from the install (never copied) and broken up with procedural relief at the scales a
low orbit sees.

Hulls. Finished cinematic builds are appended once from build/asharu-battle/builds/<dir>/ and placed
as collection instances at their own metres per unit. A hull with no build yet is a stand-in:
the game sprite's silhouette raised into a lens-shaped slab, painted with the sprite, at the
size ship_scale.py's option C solves for it (roster.py). Every drive carries a stand-in glow and
plume at its exhaust anchor or the .ship's engine slot.
"""
import json
import math
import os
import subprocess
import sys
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
ART = PROLOGUE.parent
REPO = ART.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(PROLOGUE))
sys.path.insert(0, str(ART))
import roster
from motion import fleet_at   # ships move about their stations: seeded weave, bank and breathing spacing  # noqa: E402

# One folder per finished build (roster.FINISHED's dir), copied whole from Drive.
BUILDS = Path(os.environ.get('ASHARU_BUILDS') or REPO / 'build' / 'asharu-battle' / 'builds')


def build_file(f):
    return BUILDS / f['dir'] / f['file']


def build_manifest(f):
    return json.loads((BUILDS / f['dir'] / f.get('manifest', 'manifest.json')).read_text())
W, H = 1920, 1080

# Asharu and the orbit. Asharu's real size is not given anywhere in the game or the story; a
# small dry world a little smaller than Earth reads right and keeps the limb curving in frame.
R = 5.0e6          # planet radius, m
ALT = 250e3        # convoy altitude, m
SUN_EL, SUN_AZ = 16.0, -40.0   # degrees: low morning sun, ahead of the convoy and to the south
MAP_LAT, MAP_LON = 12.0, 95.0   # where on the game's desert01 map the battle is fought

# --------------------------------------------------------------------------------- key frames
# Each key frame: what the camera sees and where every ship is. Placements are
# (x, y, z, heading deg, pitch deg, roll deg, throttle 0-1). Effects are listed per frame.
CONVOY = {   # a working convoy in loose column: escorts at point, flanks and rear
    'wolf-m': (2300, -200, 60, 0, 1, 3, .6),
    'lasher-lead': (1500, 120, 80, 0, 1, -2, .6),
    'lasher-a': (950, -380, 140, 0, 0, 3, .6),
    'mule': (700, 260, -20, 0, 0, -2, .5),
    'buffalo-a': (350, -280, 50, 0, 1, 4, .5),
    'colossus': (0, 0, 0, 0, 0, 0, .5),
    'morrow': (-40, -165, -35, 0, 1, -3, .5),
    'phaeton': (-350, 300, 20, 0, 0, 2, .5),
    'tarsus': (-650, 90, 30, 0, 0, 2, .5),
    'buffalo-b': (-900, -300, -40, 0, -1, -3, .5),
    'dram': (-1150, 260, 60, 0, 1, 2, .5),
    'lasher-b': (-750, 640, -60, 0, -1, -4, .6),
    'hound-m': (-1500, -150, 110, 0, 2, 3, .6),
}


def climb(base, dx=0, dy=0, dz=0, heading=None, pitch=None, roll=None, throttle=None):
    x, y, z, h, p, r, t = base
    return (x + dx, y + dy, z + dz, h if heading is None else heading, p if pitch is None else pitch,
            r if roll is None else roll, t if throttle is None else throttle)


PIRATES_RISING = {   # coming up out of the platform, behind and below the convoy, climbing to it
    'falcon-p': (-6200, 250, -2900, 4, 14, -3, 1),
    'manticore-p': (-6900, -650, -3300, 2, 13, 4, 1),
    'colossus3': (-8200, 100, -3700, 3, 10, 2, .8),
    'buffalo2-a': (-7700, -1300, -3500, 0, 12, 5, 1),
    'hound-p-c': (-8600, 1500, -3900, 8, 11, -4, 1),
    'wolf-p-a': (-5200, -300, -2400, -2, 17, 8, 1),
    'wolf-p-b': (-5500, 700, -2600, 5, 16, -9, 1),
    'hound-p-a': (-5800, -1000, -2700, -4, 15, 6, 1),
    'hound-p-b': (-6000, 1200, -2900, 7, 15, -5, 1),
    'kite-p-a': (-6600, -150, -3100, 1, 14, 0, 1),
    'kite-p-b': (-6700, 450, -3150, 3, 14, 2, 1),
}

PLATFORM = (-9500, 1200, -4300)   # the derelict Asharu platform: about 4 km lower and 10 km astern


def moored(offset, throttle=0.0):
    """A pirate hull lying cold along the platform's flank: offset in metres from its centre,
    heading prograde with it, drives at throttle (0 is dark)."""
    return (PLATFORM[0] + offset[0], PLATFORM[1] + offset[1], PLATFORM[2] + offset[2], 0, 0, 0, throttle)


PIRATES_MOORED = {   # running dark among the platform's stripped hulks; the Falcon (P) lights first
    'falcon-p': moored((-150, 105, 0), .15),
    'manticore-p': moored((80, 110, -20)),
    'colossus3': moored((120, -110, 0)),
    'buffalo2-a': moored((230, 100, 10)),
    'hound-p-c': moored((-300, -105, 0)),
    'wolf-p-a': moored((-60, -120, 35)),
    'wolf-p-b': moored((60, 135, 35)),
    'hound-p-a': moored((-200, -130, -30)),
    'hound-p-b': moored((220, -120, -35)),
    'kite-p-a': moored((-330, 120, -15)),
    'kite-p-b': moored((340, 120, -20)),
}

# Where the Tarsus rides in the Colossus Mk.III's grapples, in Mk.III metres: the build's
# grapples.tarsus_origin_units (20.398, 0.49, -78.254) at 0.495 m/u, axes aligned.
CLAMP_TARSUS_M = (10.10, 0.24, -38.74)


def aboard(parent, offset, thr=0):
    """A ship riding with another: the parent's spec moved by offset (metres, parent frame),
    with the parent's heading, pitch and roll. Same rotation order as frame_of."""
    x, y, z, hd, pt, rl = parent[:6]
    ch, sh = math.cos(math.radians(hd)), math.sin(math.radians(hd))
    cp, sp = math.cos(math.radians(-pt)), math.sin(math.radians(-pt))
    cr, sr = math.cos(math.radians(rl)), math.sin(math.radians(rl))
    ox, oy, oz = offset
    oy, oz = cr * oy - sr * oz, sr * oy + cr * oz                    # roll about X
    ox, oz = cp * ox + sp * oz, -sp * ox + cp * oz                   # pitch about Y
    ox, oy = ch * ox - sh * oy, sh * ox + ch * oy                    # heading about Z
    return (x + ox, y + oy, z + oz, hd, pt, rl, thr)


STAR_GAIN = 1.0      # starfield brightness on the key frames (0 hides it)

C3_PRIZE = (-150, 330, 60, 30, -4, -6, .3)

KEYFRAMES = {
    'k0': dict(
        shot='S1a', title='The view the player knows',
        caption='Straight down on the convoy, prograde up the frame: the ships as the player has always seen them, '
                'small against the desert far below. From here the camera tips down into the 3D fight.',
        camera=dict(loc=(80, 120, 11000), lens=100, look=(0.01, 0.0, -1.0)),
        ships=fleet_at(CONVOY, 10),
        labels=['colossus', 'morrow', 'tarsus', 'mule', 'phaeton', 'buffalo-a', 'lasher-a']),
    'k1': dict(
        shot='S1', title='Low orbit over Asharu',
        caption='A working convoy in loose column over the day side, slowing for its descent window: the Colossus '
                'with Morrow alongside, a Tarsus, a Mule, a Phaeton tanker, Buffalos and a Dram, escorted by '
                'three Lashers, a Wolf and a Hound.',
        camera=dict(loc=(-1900, 2700, 1500), target=(300, -80, -150), lens=35),
        ships=fleet_at(CONVOY, 16),
        labels=['colossus', 'morrow', 'tarsus', 'mule', 'phaeton', 'buffalo-a', 'buffalo-b', 'dram',
                'lasher-lead', 'lasher-a', 'lasher-b', 'wolf-m', 'hound-m']),
    'k1b': dict(
        shot='S3', title='Running dark',
        caption='Long lens from the convoy\'s tail, back down at the derelict platform it has just passed: dark, '
                'stripped, hulks in its bays. Then one of the hulks shows a light: the Falcon (P)\'s drives coming '
                'up. Eleven more lie cold beside her.',
        camera=dict(loc=(-1600, -120, 90), target=PLATFORM, lens=135),
        ships=PIRATES_MOORED,
        platform=PLATFORM,
        labels=['platform', 'falcon-p', 'colossus3', 'manticore-p']),
    'k2': dict(
        shot='S4', title='First contact',
        caption='Behind and below: drives light up in the derelict Asharu platform the convoy has just passed. '
                'Twelve pirate hulls climb out of it toward the column.',
        camera=dict(loc=(-4800, -2600, -1700), target=(-6500, 200, -3100), lens=35),
        ships=fleet_at({**{k: CONVOY[k] for k in ('hound-m', 'tarsus')}, **PIRATES_RISING}, 40),
        platform=PLATFORM,
        labels=['falcon-p', 'colossus3', 'manticore-p', 'wolf-p-a', 'kite-p-a', 'hound-p-b', 'platform']),
    'k3': dict(
        shot='S6', title='The screen breaks',
        caption='Looking aft along the convoy. Manticore (P) and Falcon (P) open from 3-4 km astern. The Phaeton '
                'tanker goes up, Lasher A is burning, and the lead Lasher turns back into the fire and is hit. '
                'The freighters keep running.',
        camera=dict(loc=(860, 640, 200), target=(-1300, -420, -280), lens=35),
        states={'phaeton': 'phaeton-wreck'},
        knobs={'phaeton': {'break_frame': {'ago_s': 2.0}}},     # 2 s into the cook-off: tank 1 has just gone
        ships={**fleet_at({k: v for k, v in CONVOY.items() if k not in ('lasher-lead', 'lasher-a', 'phaeton', 'wolf-m')}, 56,
                           keep=('lasher-b',)),   # steadied on her gun run at the Wolf (P)
               'lasher-lead': (330, 430, 70, 165, 4, -15, 1), 'lasher-a': (-150, 560, 160, 10, 6, 40, .2),
               'phaeton': (-350, 300, 20, 8, -4, 30, 0),
               'falcon-p': (-3100, -700, -650, 6, 4, -3, 1), 'manticore-p': (-2500, -1000, -480, 8, 3, 4, 1),
               'wolf-p-a': (-1250, 200, -120, 2, 6, 25, 1), 'wolf-p-b': (-1700, -650, -300, 3, 5, -20, 1),
               'buffalo2-a': (-3600, 150, -760, 0, 5, 3, 1)},
        fx=[('tracer', 'manticore-p', 'lasher-lead'), ('tracer', 'falcon-p', 'lasher-lead'),
            ('tracer', 'falcon-p', 'lasher-b'), ('tracer', 'lasher-lead', 'manticore-p'),
            ('tracer', 'lasher-b', 'wolf-p-a'), ('missiles', 'manticore-p', 'phaeton'),
            ('fire_on', 'lasher-a', (-20, 10, 6)),
            ('blast_on', 'lasher-lead', 14, (8, 6, 6))],
        cues={'lasher-lead': {'fire': 'all'}, 'lasher-b': {'fire': ['t3_1']},
              'manticore-p': {'fire': ['t1_1']}},
        labels=['lasher-lead', 'lasher-a', 'phaeton', 'colossus', 'morrow', 'falcon-p', 'manticore-p', 'wolf-p-a']),
    'k3c': dict(
        shot='S6b', title='The escorts answer',
        caption='Lasher B and the merchant Wolf turn back and roll over onto the climbing raiders, firing down. '
                'A Hound (P) takes the full Ammo Feeder burst on her bare armour and breaks up: the convoy draws blood.',
        camera=dict(loc=(-900, 700, 150), target=(-1600, 300, -350), lens=50),
        ships={'lasher-b': (-1300, 450, -150, 185, -15, 150, 1), 'wolf-m': (-1420, 560, -120, 175, -18, -140, 1),
               'hound-p-c': (-1800, 300, -500, 5, 12, -4, .4), 'buffalo2-a': (-4200, -1200, -1500, 3, 10, 3, 1),
               'manticore-p': (-3000, -800, -900, 8, 6, 4, 1)},
        fx=[('tracer', 'lasher-b', 'hound-p-c'), ('tracer', 'wolf-m', 'hound-p-c'),
            ('tracer', 'buffalo2-a', 'lasher-b'),
            ('blast_on', 'hound-p-c', 18, (0, 0, 0)), ('debris', 'hound-p-c', 50, (0, 0, 0))],
        labels=['lasher-b', 'wolf-m', 'hound-p-c', 'buffalo2-a']),
    'k3d': dict(
        shot='S6c', title='The column is cut',
        caption='Alongside the column at 300 m. The two Wolves (P) blink through the gap between the freighters, '
                'raking the Tarsus\'s drives as they pass. The Mule\'s guns and the merchant Wolf chase them; '
                'shields flare on both sides.',
        camera=dict(loc=(-250, -760, 90), target=(-200, 20, -10), lens=28),
        ships={**fleet_at({k: CONVOY[k] for k in ('colossus', 'morrow', 'tarsus', 'buffalo-b', 'mule')}, 72),
               'wolf-p-a': (-380, -60, -30, 75, 6, -60, 1), 'wolf-p-b': (-180, 80, 10, 95, 4, -70, 1),
               'wolf-m': (-560, -300, -60, 70, 4, -20, 1)},
        fx=[('tracer', 'wolf-p-a', 'tarsus'), ('tracer', 'wolf-p-b', 'tarsus'),
            ('tracer', 'wolf-m', 'wolf-p-a'), ('tracer', 'mule', 'wolf-p-b'),
            ('fire_on', 'tarsus', (-40, 0, 6), (-.2, .5, 1))],
        labels=['wolf-p-a', 'wolf-p-b', 'tarsus', 'wolf-m', 'colossus']),
    'k3e': dict(
        shot='S6d', title='The rear guard',
        caption='The convoy\'s Hound turns to face two Hounds (P) climbing at her. She kills the first at close range '
                'and takes the second\'s fire full on, flares going out as she falls back on the column.',
        camera=dict(loc=(-1450, 120, 160), target=(-1650, -180, 20), lens=35),
        ships={'hound-m': (-1500, -150, 110, 200, -25, 110, 1),
               'hound-p-a': (-1700, -50, -20, 10, 20, 0, .4), 'hound-p-b': (-1680, -380, 20, 30, 16, 0, 1)},
        fx=[('tracer', 'hound-m', 'hound-p-a'), ('tracer', 'hound-p-b', 'hound-m'),
            ('blast_on', 'hound-p-a', 14, (0, 0, 0)), ('debris', 'hound-p-a', 40, (0, 0, 0)),
            ('fire_on', 'hound-m', (-12, 4, 3)), ('flares', 'hound-m')],
        cues={'hound-m': {'fire': 'all'}, 'hound-p-b': {'fire': 'all'}},
        labels=['hound-m', 'hound-p-a', 'hound-p-b']),
    'k3b': dict(
        shot='S7', title='Boarding the Tarsus',
        caption='The Tarsus has fallen out of the column with her drives shot out. Kites (P) lie alongside with '
                'the boarders and a Wolf (P) covers them. The rest of the convoy runs on ahead without her.',
        camera=dict(loc=(-1250, 380, 170), target=(400, -40, -60), lens=35),
        states={'tarsus': 'tarsus-boarded'},
        ships={'tarsus': (-650, 90, 30, 6, -2, 9, 0),
               'kite-p-a': (-620, 160, 40, 6, -1, 9, .2), 'kite-p-b': (-700, 25, 0, 4, 2, 6, .2),
               'wolf-p-b': (-420, 330, 120, -20, -32, 110, 1),
               **fleet_at({'colossus': (1200, -80, 40, 0, 0, 2, .9), 'morrow': (1150, -250, 0, 0, 1, -3, .9),
                           'mule': (1900, 260, 20, 0, 0, -2, .9), 'buffalo-a': (1600, -350, 80, 0, 1, 4, .9)}, 86)},
        fx=[('tracer', 'wolf-p-b', 'tarsus', (30, 0, 10)), ('fire_on', 'tarsus', (-38, 6, 8), (-.2, .5, 1)),
            ('smoke', 'tarsus', (-38, 6, 8), (-.3, .4, 1))],
        labels=['tarsus', 'kite-p-a', 'kite-p-b', 'wolf-p-b', 'colossus', 'mule']),
    'k4': dict(
        shot='S8-S9', title='She will not be taken',
        caption='The Colossus Mk.III closes from above to take the Colossus too. Her port drive pod is shot out, '
                'but she fights: flares out, both turrets firing, a Kite (P) driven off. Morrow is under her keel '
                'with her drives lit, holding her up.',
        camera=dict(loc=(-420, 470, 120), target=(-40, 20, 20), lens=28),
        knobs={'colossus3': {'grapples': 0.5}},
        ships={'colossus': (0, 0, 0, 0, -3, 6, .2), 'morrow': (-10, 35, -78, 0, 8, 4, 1),
               'colossus3': (-60, 70, 175, -4, -8, 64, .6),
               'kite-p-b': (110, 210, 40, -60, 6, 32, 1),
               'wolf-p-a': (260, 160, 110, -150, 2, -40, 1)},
        fx=[('blast_on', 'colossus', 12, (-62, 30, 0)), ('fire_on', 'colossus', (-55, 36, 4)),
            ('tracer', 'colossus3', 'colossus', (-60, 30, 6)), ('tracer', 'colossus', 'wolf-p-a'),
            ('tracer', 'colossus', 'kite-p-b'), ('tracer', 'colossus', 'colossus3'),
            ('flares', 'colossus'), ('debris', 'colossus', 45, (-60, 34, 0))],
        labels=['colossus', 'morrow', 'colossus3', 'kite-p-b', 'wolf-p-a']),
    'k4b': dict(
        shot='S9b', title='The lead\'s last run',
        caption='Smoking from S6, the lead Lasher makes one more run over the Falcon (P), rolled onto her back. '
                'She presses the flagship\'s shield into overload and walks her guns down the open flank. The '
                'Falcon is hit hard, and her broadside answers.',
        camera=dict(loc=(-560, 900, -60), target=(-620, 280, -130), lens=28),
        ships={'falcon-p': (-700, 200, -100, 10, 6, -4, .8), 'lasher-lead': (-560, 380, 30, 200, -20, 160, 1)},
        fx=[('tracer', 'lasher-lead', 'falcon-p'), ('tracer', 'falcon-p', 'lasher-lead'),
            ('blast_on', 'falcon-p', 18, (-20, -14, 4)), ('fire_on', 'lasher-lead', (-20, 10, 6)),
            ('debris', 'lasher-lead', 25, (-10, 8, 0))],
        labels=['falcon-p', 'lasher-lead']),
    'k5': dict(
        shot='S10', title='The prize taken',
        caption='The pirates have their prize: the captured Tarsus is clamped to the Colossus Mk.III, with a Kite (P) '
                'still docked. Far below, the Colossus has lost her orbit, nose down, with Morrow still at her side. '
                'A burning freighter heading into the atmosphere is not worth chasing.',
        camera=dict(loc=(-900, 380, 420), target=(420, -60, -560), lens=35),
        states={'tarsus': 'tarsus-boarded'},
        knobs={'colossus3': {'grapples': 1.0}},
        ships={'falcon-p': (-560, 120, 120, 25, -6, 8, .8),
               'colossus3': C3_PRIZE, 'tarsus': aboard(C3_PRIZE, CLAMP_TARSUS_M),
               'kite-p-a': aboard(C3_PRIZE, (10, 66, -40)),
               'colossus': (480, -30, -780, 5, -26, 14, .15), 'morrow': (500, -175, -830, 0, -22, 8, 1)},
        fx=[('fire_on', 'colossus', (-55, 36, 4)), ('smoke', 'colossus', (-60, 34, 0)),
            ('debris', 'colossus', 70, (-30, 0, 10)),
            ('fire_on', 'falcon-p', (-20, -14, 4), (-.3, -.6, .7)), ('smoke', 'falcon-p', (-20, -14, 4), (-.6, -.4, .6))],
        labels=['colossus', 'morrow', 'falcon-p', 'colossus3', 'tarsus']),
    'k6': dict(
        shot='S12', title='Two trails toward the northern canyons',
        caption='Long lens from the pirates\' orbit. The Colossus and Morrow burn into the atmosphere, two plasma '
                'trails heading north and down toward the canyon country where Basin will find them.',
        camera=dict(loc=(0, 0, 0), target=None, lens=50, look=(0.9, 0.02, -0.42)),
        ships={},
        fx=[('entry_trails',)],
        labels=['trail-colossus', 'trail-morrow']),
}

ROSTER_SHEETS = ('r1', 'r2')   # top-down and broadside fleet sheets


# ======================================================================================= Blender

def blender_main(cfg):
    import bmesh
    import bpy
    import numpy as np
    from bpy_extras.object_utils import world_to_camera_view
    from mathutils import Euler, Matrix, Quaternion, Vector
    import ship_fx

    out = Path(cfg['out'])
    raw = out / 'raw'
    raw.mkdir(parents=True, exist_ok=True)

    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.resolution_x, scene.render.resolution_y = W, H
    scene.render.resolution_percentage = 50 if cfg.get('half') else 100     # --half: a light check
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = cfg['samples']
    scene.cycles.use_denoising = True
    scene.cycles.max_bounces = 4
    scene.cycles.transparent_max_bounces = 16
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Base Contrast'
    try:
        prefs = bpy.context.preferences.addons['cycles'].preferences
        for kind in ('OPTIX', 'CUDA', 'METAL'):
            try:
                prefs.compute_device_type = kind
                prefs.get_devices()
                if any(d.type == kind for d in prefs.devices):
                    for d in prefs.devices:
                        d.use = d.type == kind
                    scene.cycles.device = 'GPU'
                    break
            except TypeError:
                continue
    except KeyError:
        pass

    def sock(sockets, name, kind='RGBA'):
        return next(x for x in sockets if x.name == name and x.type == kind)

    def link(obj, coll=None):
        (coll or scene.collection).objects.link(obj)
        return obj

    def emission_mat(name, rgb, strength, fade=None):
        """Emission; fade='z' fades it out along the object's generated Z (plumes, trails)."""
        mat = bpy.data.materials.new(name)
        mat.use_nodes = True
        nt = mat.node_tree
        nt.nodes.remove(nt.nodes['Principled BSDF'])
        em = nt.nodes.new('ShaderNodeEmission')
        em.inputs['Color'].default_value = (*rgb, 1)
        em.inputs['Strength'].default_value = strength
        outn = nt.nodes['Material Output']
        if fade is None:
            nt.links.new(em.outputs[0], outn.inputs['Surface'])
            return mat
        tc = nt.nodes.new('ShaderNodeTexCoord')
        sep = nt.nodes.new('ShaderNodeSeparateXYZ')
        nt.links.new(tc.outputs['Generated'], sep.inputs[0])
        inv = nt.nodes.new('ShaderNodeMath')
        inv.operation = 'SUBTRACT'
        inv.inputs[0].default_value = 1.0
        nt.links.new(sep.outputs['Z'], inv.inputs[1])
        pw = nt.nodes.new('ShaderNodeMath')
        pw.operation = 'POWER'
        pw.inputs[1].default_value = fade
        nt.links.new(inv.outputs[0], pw.inputs[0])
        clear = nt.nodes.new('ShaderNodeBsdfTransparent')
        mix = nt.nodes.new('ShaderNodeMixShader')
        nt.links.new(pw.outputs[0], mix.inputs['Fac'])
        nt.links.new(clear.outputs[0], mix.inputs[1])
        nt.links.new(em.outputs[0], mix.inputs[2])
        nt.links.new(mix.outputs[0], outn.inputs['Surface'])
        return mat

    def principled(name, rgb, rough=.5, metal=.3, alpha=1.0):
        mat = bpy.data.materials.new(name)
        mat.use_nodes = True
        p = mat.node_tree.nodes['Principled BSDF']
        p.inputs['Base Color'].default_value = (*rgb, 1)
        p.inputs['Roughness'].default_value = rough
        p.inputs['Metallic'].default_value = metal
        p.inputs['Alpha'].default_value = alpha
        return mat

    # ---------------------------------------------------------------------------- world, sun
    world = bpy.data.worlds.new('Asharu | space')
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Color'].default_value = (.0008, .0009, .0014, 1)
    # A starfield (owner, 2026-10-21): a camera exposed for sunlit hulls would lose the stars, but space reads
    # wrong without them. Seen by the camera only (Light Path: Is Camera Ray), so they light nothing. Sharp
    # Voronoi points on the view direction, a few bright ones and many faint, with a faint warm/cool tint.
    wn, wl = world.node_tree.nodes, world.node_tree.links
    bg_out = wn['World Output']
    tc = wn.new('ShaderNodeTexCoord')
    vor = wn.new('ShaderNodeTexVoronoi')
    vor.voronoi_dimensions = '3D'
    vor.inputs['Scale'].default_value = 420.0
    wl.new(tc.outputs['Generated'], vor.inputs['Vector'])
    pts = wn.new('ShaderNodeMapRange')               # distance to the cell's point -> a tight dot
    pts.inputs['From Min'].default_value, pts.inputs['From Max'].default_value = 0.0, 0.06
    pts.inputs['To Min'].default_value, pts.inputs['To Max'].default_value = 1.0, 0.0
    wl.new(vor.outputs['Distance'], pts.inputs['Value'])
    mag = wn.new('ShaderNodeMath'); mag.operation = 'POWER'      # each cell's random colour as its magnitude
    sep = wn.new('ShaderNodeSeparateColor')
    wl.new(vor.outputs['Color'], sep.inputs['Color'])
    wl.new(sep.outputs['Red'], mag.inputs[0]); mag.inputs[1].default_value = 9.0
    lum = wn.new('ShaderNodeMath'); lum.operation = 'MULTIPLY'
    wl.new(pts.outputs['Result'], lum.inputs[0]); wl.new(mag.outputs['Value'], lum.inputs[1])
    tint = wn.new('ShaderNodeMix'); tint.data_type = 'RGBA'
    tint.inputs['A'].default_value, tint.inputs['B'].default_value = (1.0, .86, .72, 1), (.75, .85, 1.0, 1)
    wl.new(sep.outputs['Green'], tint.inputs['Factor'])
    star = wn.new('ShaderNodeBackground'); star.name = 'Stars'
    wl.new(tint.outputs['Result'], star.inputs['Color'])
    gain = wn.new('ShaderNodeMath'); gain.operation = 'MULTIPLY'; gain.name = 'Star gain'
    gain.inputs[1].default_value = 1.0
    wl.new(lum.outputs['Value'], gain.inputs[0]); wl.new(gain.outputs['Value'], star.inputs['Strength'])
    lp = wn.new('ShaderNodeLightPath')
    only_cam = wn.new('ShaderNodeMixShader')
    wl.new(lp.outputs['Is Camera Ray'], only_cam.inputs['Fac'])
    wl.new(wn['Background'].outputs['Background'], only_cam.inputs[1])
    add = wn.new('ShaderNodeAddShader')
    wl.new(wn['Background'].outputs['Background'], add.inputs[0]); wl.new(star.outputs['Background'], add.inputs[1])
    wl.new(add.outputs['Shader'], only_cam.inputs[2])
    wl.new(only_cam.outputs['Shader'], bg_out.inputs['Surface'])
    sun_dir = Vector((math.cos(math.radians(SUN_EL)) * math.cos(math.radians(SUN_AZ)),
                      math.cos(math.radians(SUN_EL)) * math.sin(math.radians(SUN_AZ)),
                      math.sin(math.radians(SUN_EL))))
    sun = link(bpy.data.objects.new('Asharu | Corvus', bpy.data.lights.new('Asharu | Corvus', 'SUN')))
    sun.data.energy = 4.6
    sun.data.color = (1.0, .96, .88)
    sun.data.angle = math.radians(.5)
    sun.rotation_euler = sun_dir.to_track_quat('Z', 'Y').to_euler()

    # ---------------------------------------------------------------------------- planet
    centre = Vector((0, 0, -(R + ALT)))

    def cap(name, radius, max_colat_deg, rings, segments):
        bm = bmesh.new()
        top = bm.verts.new((0, 0, radius))
        prev = None
        for i in range(1, rings + 1):
            th = math.radians(max_colat_deg) * (i / rings) ** 1.0
            ring = [bm.verts.new((radius * math.sin(th) * math.cos(2 * math.pi * j / segments),
                                  radius * math.sin(th) * math.sin(2 * math.pi * j / segments),
                                  radius * math.cos(th))) for j in range(segments)]
            for j in range(segments):
                k = (j + 1) % segments
                if prev is None:
                    bm.faces.new((top, ring[j], ring[k]))
                else:
                    bm.faces.new((prev[j], ring[j], ring[k], prev[k]))
            prev = ring
        me = bpy.data.meshes.new(name)
        bm.to_mesh(me)
        bm.free()
        for p in me.polygons:
            p.use_smooth = True
        obj = link(bpy.data.objects.new(name, me))
        obj.location = centre
        return obj

    ground = cap('Asharu | surface', R, 24.0, 420, 1024)
    gm = bpy.data.materials.new('Asharu | desert')
    gm.use_nodes = True
    nt = gm.node_tree
    bsdf = nt.nodes['Principled BSDF']
    bsdf.inputs['Roughness'].default_value = .95
    tc = nt.nodes.new('ShaderNodeTexCoord')
    # Map lookup by direction from the centre: the game's equirectangular desert01.jpg (scarps
    # and canyons between dune seas), turned so world north is the map's north and the battle
    # sits over latitude MAP_LAT, longitude MAP_LON.
    turn = nt.nodes.new('ShaderNodeVectorRotate')
    turn.rotation_type = 'Z_AXIS'
    turn.inputs['Angle'].default_value = math.radians(90)
    nt.links.new(tc.outputs['Object'], turn.inputs['Vector'])
    rot = nt.nodes.new('ShaderNodeVectorRotate')
    rot.rotation_type = 'EULER_XYZ'
    rot.inputs['Rotation'].default_value = (0, math.radians(90 - MAP_LAT), math.radians(MAP_LON))
    nt.links.new(turn.outputs[0], rot.inputs['Vector'])
    nrm = nt.nodes.new('ShaderNodeVectorMath')
    nrm.operation = 'NORMALIZE'
    nt.links.new(rot.outputs[0], nrm.inputs[0])
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(nrm.outputs[0], sep.inputs[0])

    def mnode(op, a, b=None, val=None):
        n = nt.nodes.new('ShaderNodeMath')
        n.operation = op
        for idx, x in enumerate((a, b)):
            if x is None:
                continue
            if isinstance(x, (int, float)):
                n.inputs[idx].default_value = x
            else:
                nt.links.new(x, n.inputs[idx])
        return n.outputs[0]

    lon = mnode('ARCTAN2', sep.outputs['Y'], sep.outputs['X'])
    lat = mnode('ARCSINE', sep.outputs['Z'])
    u = mnode('ADD', mnode('DIVIDE', lon, 2 * math.pi), .5)
    v = mnode('ADD', mnode('DIVIDE', lat, math.pi), .5)
    uv = nt.nodes.new('ShaderNodeCombineXYZ')
    nt.links.new(u, uv.inputs['X'])
    nt.links.new(v, uv.inputs['Y'])
    img = nt.nodes.new('ShaderNodeTexImage')
    img.image = bpy.data.images.load(str(roster.GAME_CORE / 'graphics' / 'planets' / 'desert01.jpg'))
    img.interpolation = 'Cubic'
    nt.links.new(uv.outputs[0], img.inputs['Vector'])
    # Relief at the scales a 250 km orbit resolves: basins (60 km), ridges (8 km), dune fields.
    def noise(scale, detail=6, rough=.55):
        n = nt.nodes.new('ShaderNodeTexNoise')
        n.inputs['Scale'].default_value = scale
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = rough
        nt.links.new(tc.outputs['Object'], n.inputs['Vector'])
        return n.outputs['Fac']
    basin = noise(1.6e-5)
    mid = noise(4.5e-5, 8, .6)
    ridge = noise(1.3e-4, 8, .62)
    fine = noise(1.2e-3, 4, .5)
    vein = mnode('POWER', mnode('SUBTRACT', 1.0, mnode('MULTIPLY', mnode('ABSOLUTE', mnode('SUBTRACT', noise(2.2e-4, 6, .55), .5)), 2.0)), 14.0)
    wave = nt.nodes.new('ShaderNodeTexWave')
    wave.inputs['Scale'].default_value = 6e-4
    wave.inputs['Distortion'].default_value = 6.0
    nt.links.new(tc.outputs['Object'], wave.inputs['Vector'])
    shade = mnode('ADD', mnode('MULTIPLY', mnode('SUBTRACT', basin, .5), 1.1),
                  mnode('ADD', mnode('MULTIPLY', mnode('SUBTRACT', ridge, .5), 1.6),
                        mnode('ADD', mnode('MULTIPLY', mnode('SUBTRACT', fine, .5), .35),
                              mnode('MULTIPLY', mnode('SUBTRACT', wave.outputs['Fac'], .5), .12))))
    shade = mnode('ADD', shade, mnode('ADD', mnode('MULTIPLY', mnode('SUBTRACT', mid, .5), 1.2),
                                        mnode('MULTIPLY', vein, -.55)))
    gain = mnode('MAXIMUM', mnode('ADD', 1.0, shade), .15)
    tint = nt.nodes.new('ShaderNodeMix')
    tint.data_type = 'RGBA'
    tint.blend_type = 'MULTIPLY'
    tint.inputs['Factor'].default_value = 1.0
    nt.links.new(img.outputs['Color'], sock(tint.inputs, 'A'))
    cmb = nt.nodes.new('ShaderNodeCombineColor')
    for ch in ('Red', 'Green', 'Blue'):
        nt.links.new(gain, cmb.inputs[ch])
    nt.links.new(cmb.outputs[0], sock(tint.inputs, 'B'))
    dim = nt.nodes.new('ShaderNodeMix')
    dim.data_type = 'RGBA'
    dim.blend_type = 'MULTIPLY'
    dim.inputs['Factor'].default_value = 1.0
    nt.links.new(sock(tint.outputs, 'Result'), sock(dim.inputs, 'A'))
    sock(dim.inputs, 'B').default_value = (.78, .7, .62, 1)
    nt.links.new(sock(dim.outputs, 'Result'), bsdf.inputs['Base Color'])
    bump = nt.nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = .35
    nt.links.new(mnode('ADD', ridge, mnode('MULTIPLY', fine, .5)), bump.inputs['Height'])
    nt.links.new(bump.outputs[0], bsdf.inputs['Normal'])
    ground.data.materials.append(gm)

    air = cap('Asharu | air', R + 14e3, 25.0, 300, 1024)
    am = bpy.data.materials.new('Asharu | air')
    am.use_nodes = True
    nt2 = am.node_tree
    nt2.nodes.remove(nt2.nodes['Principled BSDF'])
    lw = nt2.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    p3 = nt2.nodes.new('ShaderNodeMath')
    p3.operation = 'POWER'
    p3.inputs[1].default_value = 9.0
    nt2.links.new(lw.outputs['Facing'], p3.inputs[0])
    em = nt2.nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = (.78, .74, .68, 1)
    em.inputs['Strength'].default_value = 1.4
    clear = nt2.nodes.new('ShaderNodeBsdfTransparent')
    mix = nt2.nodes.new('ShaderNodeMixShader')
    nt2.links.new(p3.outputs[0], mix.inputs['Fac'])
    nt2.links.new(clear.outputs[0], mix.inputs[1])
    nt2.links.new(em.outputs[0], mix.inputs[2])
    nt2.links.new(mix.outputs[0], nt2.nodes['Material Output'].inputs['Surface'])
    air.data.materials.append(am)
    air.visible_shadow = False

    # ---------------------------------------------------------------------------- hull library
    library = {}      # game/finished key -> dict(coll, mpu, engines [(pos, dir, radius, style)], length_m)

    def excluded(coll):
        bpy.context.view_layer.layer_collection.children[coll.name].exclude = True
        return coll

    def append(path, name, extra=()):
        with bpy.data.libraries.load(str(path), link=False) as (src, dst):
            dst.collections = [name, *extra]
        colls = list(dst.collections)
        for coll in colls:
            scene.collection.children.link(coll)
            excluded(coll)
        return colls[0] if not extra else colls

    def tinted_copy(coll, rgb, label):
        new = bpy.data.collections.new(coll.name + ' | ' + label)
        scene.collection.children.link(new)
        excluded(new)
        mats = {}
        remap = {}
        for obj in coll.all_objects:
            o2 = obj.copy()
            if obj.data is not None and hasattr(obj.data, 'materials'):
                o2.data = obj.data.copy()
                for i, m in enumerate(o2.data.materials):
                    if m is None:
                        continue
                    if m.name not in mats:
                        m2 = m.copy()
                        m2.name = m.name + ' | ' + label
                        if m2.use_nodes:
                            for n in m2.node_tree.nodes:
                                if n.type == 'BSDF_PRINCIPLED':
                                    base = n.inputs['Base Color']
                                    mul = m2.node_tree.nodes.new('ShaderNodeMix')
                                    mul.data_type = 'RGBA'
                                    mul.blend_type = 'MULTIPLY'
                                    mul.inputs['Factor'].default_value = .85
                                    sock(mul.inputs, 'B').default_value = (*rgb, 1)
                                    if base.is_linked:
                                        m2.node_tree.links.new(base.links[0].from_socket, sock(mul.inputs, 'A'))
                                    else:
                                        sock(mul.inputs, 'A').default_value = base.default_value
                                    m2.node_tree.links.new(sock(mul.outputs, 'Result'), base)
                        mats[m.name] = m2
                    o2.data.materials[i] = mats[m.name]
            remap[obj] = o2
            new.objects.link(o2)
        for obj, o2 in remap.items():
            if obj.parent in remap:
                o2.parent = remap[obj.parent]
        return new

    def finished(key):
        f = roster.FINISHED[key]
        m = build_manifest(f)
        fx = None
        if (m.get('effects') or {}).get('collection'):     # the build's own SWFX, copied per ship (ShipFx)
            coll, fx = append(build_file(f), m['append_collection'], (m['effects']['collection'],))
        else:
            coll = append(build_file(f), m['append_collection'])
        ctl = m.get('control', {}).get('object') or m.get('break_up', {}).get('controls')
        ctl = next((o for o in coll.all_objects if ctl and o.name.split('.')[0] == ctl), None) or (
            bpy.data.objects.get(ctl) if ctl else None)
        eng = []
        for e in m['engines']:
            role = e.get('role', 'main drive')
            if any(w in role for w in ('lift', 'jet', 'rcs', 'manoeuv', 'maneuv')):    # glows are for the drives only
                continue
            if e.get('running') is False:    # a dead drive (a damaged state) shows no glow
                continue
            eng.append((Vector(e.get('mouth') or e['location']), Vector(e.get('axis') or e['exhaust_direction']),
                        e.get('radius') or e['mouth_radius'], e.get('style', 'LOW_TECH')))
        knobs = {}
        if ctl and 'control' in m:      # knobs: a list of {name, ...}, or a dict of name -> description
            ks = m['control'].get('knobs') or []
            pose = m['control'].get('saved_pose') or {}
            knobs = {n: pose.get(n, 0.0) for n in (ks if isinstance(ks, dict) else [k['name'] for k in ks])}
        hull_ctl = ctl if ctl is not None and ctl.name in coll.all_objects else None    # gear, grapples, break-up
        return dict(coll=coll, fx=fx, mpu=m['metres_per_unit'], engines=eng, length_m=f['length_m'], ctl=ctl,
                    knobs=knobs, manifest=m, hull_ctl=hull_ctl, swfx=bool(f.get('swfx')))

    def standin(game, length_m=None):
        """The sprite's silhouette as a lens-shaped slab in hull units (one sprite px per unit,
        about the .ship centre), painted with the sprite, sized by option C."""
        spec = roster.game_spec(game)
        im = bpy.data.images.load(spec['sprite'], check_existing=True)
        w, h = im.size
        px = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1]   # row 0 = top
        alpha = px[:, :, 3] > .5
        area = int(alpha.sum())
        rows = alpha.any(1).nonzero()[0]
        length_px = rows.max() - rows.min() + 1
        if length_m is None:
            mpu, units3, cls = roster.option_c(spec, area)
        else:
            cls = 'capital'
            mpu = length_m / length_px
            units3 = roster.ship_scale.estimate_units3(area, cls)
        step = 2 if max(w, h) < 260 else 3
        hh, ww = h // step, w // step
        cells = alpha[:hh * step, :ww * step].reshape(hh, step, ww, step).mean(axis=(1, 3)) > .5
        # Distance from the edge, in cells (4-neighbour erosion).
        d = np.zeros(cells.shape, dtype=np.float32)
        cur = cells.copy()
        while cur.any():
            d += cur
            pad = np.pad(cur, 1)
            cur = cur & pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]
        soft = max(2.0, d.max() * .55)
        prof = np.clip((d - 1) / soft, 0, 1) ** .35
        cell_area = step * step
        vol_per_h = (prof.sum() * (1 + .7)) * cell_area
        h0 = 2.0 * units3 / vol_per_h if vol_per_h > 0 else 1.0   # x2: reads as a hull beside built models
        cx, cy = spec['centre']
        bm = bmesh.new()
        uvl = bm.loops.layers.uv.new('UVMap')
        top, bot, uvs = {}, {}, {}
        for r, c in zip(*np.nonzero(cells)):
            pxx, pyy = c * step + step / 2, r * step + step / 2
            x = (h - pyy) - cy
            y = cx - pxx
            z = prof[r, c] * h0
            top[(r, c)] = bm.verts.new((x, y, z))
            bot[(r, c)] = top[(r, c)] if z == 0 else bm.verts.new((x, y, -.7 * z))
            uvs[(r, c)] = (pxx / w, 1 - pyy / h)
        for (r, c) in list(top):
            q = [(r, c), (r + 1, c), (r + 1, c + 1), (r, c + 1)]
            if not all(k in top for k in q):
                continue
            for layer, order in ((top, q), (bot, q[::-1])):
                vs = [layer[k] for k in order]
                if len(set(vs)) < 3:
                    continue
                try:
                    f = bm.faces.new(list(dict.fromkeys(vs)))
                except ValueError:
                    continue
                for loop in f.loops:
                    k = next(kk for kk in order if layer[kk] is loop.vert)
                    loop[uvl].uv = uvs[k]
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
        me = bpy.data.meshes.new('Stand-in | ' + game)
        bm.to_mesh(me)
        bm.free()
        for p in me.polygons:
            p.use_smooth = True
        mat = bpy.data.materials.new('Stand-in | ' + game)
        mat.use_nodes = True
        nt = mat.node_tree
        tex = nt.nodes.new('ShaderNodeTexImage')
        tex.image = im
        p = nt.nodes['Principled BSDF']
        nt.links.new(tex.outputs['Color'], p.inputs['Base Color'])
        p.inputs['Roughness'].default_value = .55
        p.inputs['Metallic'].default_value = .35
        me.materials.append(mat)
        coll = bpy.data.collections.new('Stand-in | ' + game)
        scene.collection.children.link(coll)
        excluded(coll)
        coll.objects.link(bpy.data.objects.new(me.name, me))
        eng = []
        for e in spec['engines']:
            a = math.radians(e['angle'])
            eng.append((Vector((e['location'][0], e['location'][1], 0)), Vector((math.cos(a), math.sin(a), 0)),
                        e['width'] * .32, e['style']))
        return dict(coll=coll, mpu=mpu, engines=eng, length_m=length_px * mpu, cls=cls)

    def hull(m):
        k = m['finished'] + ('|tint' if m.get('tint') else '') if m.get('finished') else m['game']
        if k in library:
            return library[k]
        if m.get('finished'):
            base_key = m['finished']
            if base_key not in library:
                library[base_key] = finished(base_key)
            if m.get('tint'):
                b = library[base_key]
                library[k] = {**b, 'coll': tinted_copy(b['coll'], m['tint'], 'pirate')}
        else:
            library[k] = standin(m['game'], m.get('published_m'))
        return library[k]

    members = {m['key']: m for m in roster.MERCHANT + roster.PIRATE + roster.REFERENCES}

    # ---------------------------------------------------------------------------- plumes, fx
    plume_me = bpy.data.meshes.new('Plume unit')
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=False, segments=16, radius1=1.0, radius2=.35, depth=1.0)
    bmesh.ops.translate(bm, verts=bm.verts[:], vec=(0, 0, .5))
    bm.to_mesh(plume_me)
    bm.free()
    box_me = bpy.data.meshes.new('Box unit')
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=2.0)
    bm.to_mesh(box_me)
    bm.free()
    bead_me = bpy.data.meshes.new('Bead unit')
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=2, radius=1.0)
    bm.to_mesh(bead_me)
    bm.free()
    STYLE_RGB = {'LOW_TECH': (1.0, .55, .25), 'MIDLINE': (1.0, .8, .45), 'HIGH_TECH': (.45, .7, 1.0)}
    plume_mats = {s: emission_mat('Plume ' + s, c, 14.0, fade=1.6) for s, c in STYLE_RGB.items()}
    bead_mats = {s: emission_mat('Drive bead ' + s, c, 40.0) for s, c in STYLE_RGB.items()}
    tracer_mat = emission_mat('Tracer', (1.0, .62, .3), 18.0)
    missile_mat = emission_mat('Missile', (1.0, .85, .6), 40.0)
    blast_mat = emission_mat('Blast', (1.0, .5, .18), 7.0)
    fire_mat = emission_mat('Fire', (1.0, .4, .12), 12.0, fade=1.2)
    flare_mat = emission_mat('Flare', (1.0, .9, .7), 60.0)
    plasma_mat = emission_mat('Entry plasma', (1.0, .62, .32), 30.0, fade=1.4)
    smoke_mat = principled('Smoke', (.16, .15, .14), rough=1, metal=0, alpha=.28)
    smoke_mat.node_tree.nodes['Principled BSDF'].inputs['Specular IOR Level'].default_value = 0.0   # matte, not glass
    debris_mat = principled('Debris', (.08, .08, .08), rough=.7, metal=.5)
    plume_me.materials.append(tracer_mat)
    bead_me.materials.append(tracer_mat)
    box_me.materials.append(tracer_mat)

    stage = bpy.data.collections.new('Stage')
    scene.collection.children.link(stage)
    placed = {}
    ships_fx = {}     # (ship key, build key) -> ShipFx: that ship's own SWFX (and hull, where it has gear)

    def ship_fx_for(key, m, lib):
        """The ship's own effects copy, when it needs one: always for a build that drives hull parts
        (gear, grapples) or carries scene effects (the wreck), and for every build under --swfx."""
        if lib.get('manifest') is None:
            return None
        if not (lib['hull_ctl'] is not None or lib['swfx'] or (cfg.get('swfx') and lib['fx'] is not None)):
            return None
        k = (key, m['finished'])
        if k not in ships_fx:
            fx = lib['fx'] if (cfg.get('swfx') or lib['swfx']) else None
            s = ship_fx.ShipFx(key, lib['coll'], fx, lib['manifest'], hull_ctl=lib['hull_ctl'],
                               clock=not lib['swfx'])
            for c in (s.hull if s.hull is not lib['coll'] else None, s.fx):
                if c is not None:
                    scene.collection.children.link(c)
                    excluded(c)
            ships_fx[k] = s
        return ships_fx[k]

    def frame_of(loc, heading, pitch, roll):
        q = (Quaternion((0, 0, 1), math.radians(heading)) @ Quaternion((0, 1, 0), -math.radians(pitch))
             @ Quaternion((1, 0, 0), math.radians(roll)))
        return Matrix.Translation(Vector(loc)) @ q.to_matrix().to_4x4()

    def place(key, spec, state=None):
        """state names another FINISHED build of the same hull (a condition state) for this frame."""
        x, y, z, hd, pt, rl, thr = spec
        m = {**members[key], 'finished': state} if state else members[key]
        lib = hull(m)
        sfx = ship_fx_for(key, m, lib)
        e = bpy.data.objects.new('Ship | ' + key, None)
        e.instance_type = 'COLLECTION'
        e.instance_collection = sfx.hull if sfx is not None else lib['coll']
        mw = frame_of((x, y, z), hd, pt, rl)
        e.matrix_world = mw @ Matrix.Scale(lib['mpu'], 4)
        link(e, stage)
        if sfx is not None and sfx.fx is not None:
            e2 = bpy.data.objects.new('Ship fx | ' + key, None)
            e2.instance_type = 'COLLECTION'
            e2.instance_collection = sfx.fx
            e2.matrix_world = e.matrix_world
            link(e2, stage)
        placed[key] = dict(matrix=mw, mpu=lib['mpu'], length=lib['length_m'], lib=lib, fx=sfx, throttle=thr)
        if thr <= 0 or (sfx is not None and sfx.fx is not None and not lib['swfx']):
            return      # no throttle, or the kit's own drives draw this ship's plumes
        for pos, dirn, rad, style in lib['engines']:
            style = style if style in STYLE_RGB else 'LOW_TECH'
            p = mw @ (pos * lib['mpu'])
            dw = (mw.to_3x3() @ dirn).normalized()
            r = max(rad * lib['mpu'], 1.0)
            obj('Drive bead | ' + key, bead_me, bead_mats[style], Matrix.Translation(p) @ Matrix.Scale(r * .9, 4))
            q = dw.to_track_quat('Z', 'Y')
            L = r * (4 + 8 * thr)
            obj('Drive plume | ' + key, plume_me, plume_mats[style], Matrix.Translation(p) @ q.to_matrix().to_4x4()
                @ Matrix.Diagonal((r * .9, r * .9, L, 1)))

    def at(key, local=(0, 0, 0)):
        """A point on a placed ship, local in metres in its own frame."""
        return placed[key]['matrix'] @ Vector(local)

    def obj(name, me, mat, matrix):
        o = bpy.data.objects.new(name, me)
        o.matrix_world = matrix
        link(o, stage)
        o.material_slots[0].link = 'OBJECT'
        o.material_slots[0].material = mat
        o.visible_shadow = mat in (smoke_mat, debris_mat)   # light and plasma cast no shadow
        return o

    rng = np.random.default_rng(7)

    def segment(p0, p1, radius, mat, name):
        d = p1 - p0
        q = d.normalized().to_track_quat('Z', 'Y')
        obj(name, plume_me, mat, Matrix.Translation(p0) @ q.to_matrix().to_4x4()
            @ Matrix.Diagonal((radius, radius, d.length, 1)))

    def muzzle_world(key, muzzle):
        """A shot's start: the bearing mount's muzzle (bearing()), on the placed ship."""
        if muzzle is None:
            return at(key)
        if muzzle[0] == 'anchor':
            lib = placed[key]['lib']
            o = next((o for o in lib['coll'].all_objects if o.name.split('.')[0] == muzzle[1]), None)
            if o is None:
                print('ASHARU muzzle anchor missing, firing from the hull centre:', key, muzzle[1], flush=True)
                return at(key)
            # an appended object outside the scene has no evaluated matrix_world: compose its chain
            w = o.matrix_basis
            while o.parent:
                w = o.parent.matrix_basis @ o.matrix_parent_inverse @ w
                o = o.parent
            return at(key, w.translation * lib['mpu'])
        return at(key, muzzle[1])

    def line_of_sight(kf, spec, muzzles, report):
        """--los: cast from each shot's muzzle to its aim point through the posed hulls (before any effect is
        made) and say what the round meets first: its target, the shooter's own hull, or another ship. A
        stand-in fires from inside its slab, so its own-hull hits are not meaningful."""
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        centres = {k: (at(k), placed[k]['length'] * .6) for k in placed if k in spec['ships']}
        for fx in spec.get('fx', ()):
            if fx[0] not in ('tracer', 'missiles') or fx[1] not in centres or fx[2] not in centres:
                continue
            a, b = fx[1], fx[2]
            p1 = at(b, fx[3] if fx[0] == 'tracer' and len(fx) > 3 else (0, 0, 0))

            def cast(muzzle):
                p0 = muzzle_world(a, muzzle)
                d = (p1 - p0)
                dist = d.length
                d.normalize()
                o = p0 + d * 2.0
                while True:
                    hit, loc, _, _, ob, _ = scene.ray_cast(dg, o, d, distance=max(0.0, dist - (o - p0).length))
                    if not hit:
                        return True, 'nothing (open space to the aim point)'
                    near = min(centres, key=lambda k: (loc - centres[k][0]).length)
                    if (loc - centres[near][0]).length > centres[near][1]:
                        o = loc + d * .5       # not a ship (sky, planet shell, platform): look past it
                        continue
                    what = ('its target' if near == b else 'ITS OWN HULL' if near == a else 'ANOTHER SHIP: ' + near) +                         f' at {(loc - p0).length:.0f} m of {dist:.0f} m' + ('' if near == b else f' ({ob.name})')
                    return near == b, what

            first = muzzles.get(fx[:3])
            clear, what = cast(first)
            tried = 1
            if not clear and members[a].get('finished'):
                for cand in BEARING_CANDIDATES.get((kf,) + tuple(fx[:3]), []):
                    if cand[5] == first:
                        continue
                    tried += 1
                    ok2, what2 = cast(cand[5])
                    if ok2:
                        muzzles[fx[:3]] = cand[5]
                        clear, what = True, what2 + f' (switched to another gun, {cand[2]}, after {tried - 1} blocked)'
                        break
            if report:
                standin = '' if members[a].get('finished') else '  (stand-in: fires from inside its slab)'
                print(f'ASHARU LOS {kf} {fx[0]:8} {a:12} -> {b:12} {what}{standin}', flush=True)

    def tracer(a, b, local=None, n=6, muzzle=None):
        p0 = muzzle_world(a, muzzle)
        p1 = at(b, local or (0, 0, 0)) + Vector(rng.normal(0, 12, 3))
        for i in range(n):
            f0 = rng.uniform(.08, .95)
            f1 = min(1.0, f0 + 30.0 / (p1 - p0).length)
            segment(p0.lerp(p1, f1), p0.lerp(p1, f0), .7, tracer_mat, 'Tracer')

    def missiles(a, b, n=5, muzzle=None):
        p0, p1 = muzzle_world(a, muzzle), at(b)
        for i in range(n):
            f = rng.uniform(.3, .9)
            p = p0.lerp(p1, f) + Vector(rng.normal(0, 60, 3))
            obj('Missile', bead_me, missile_mat, Matrix.Translation(p) @ Matrix.Scale(2.2, 4))
            back = (p0 - p1).normalized()
            segment(p, p + back * 140, 2.5, plume_mats['LOW_TECH'], 'Missile trail')

    def blast(p, radius):
        obj('Blast', bead_me, blast_mat, Matrix.Translation(p) @ Matrix.Scale(radius * .55, 4))
        for i in range(9):
            o = Vector(rng.normal(0, radius * .45, 3))
            obj('Blast lobe', bead_me, blast_mat, Matrix.Translation(p + o) @ Matrix.Scale(radius * rng.uniform(.2, .45), 4))
        obj('Blast smoke', bead_me, smoke_mat, Matrix.Translation(p) @ Matrix.Scale(radius * 1.15, 4))

    def debris(p, spread, n=40):
        for i in range(n):
            o = Vector(rng.normal(0, spread, 3))
            s = rng.uniform(.6, 3.5)
            m = Matrix.Translation(p + o) @ Euler(rng.uniform(0, 6.3, 3)).to_matrix().to_4x4() @ Matrix.Diagonal((s, s * .5, s * .2, 1))
            obj('Debris', bead_me, debris_mat, m)

    def fire(p, toward):
        for i in range(4):
            q = (toward + Vector(rng.normal(0, .25, 3))).normalized().to_track_quat('Z', 'Y')
            obj('Fire', plume_me, fire_mat, Matrix.Translation(p) @ q.to_matrix().to_4x4()
                @ Matrix.Diagonal((5, 5, rng.uniform(18, 35), 1)))

    def smoke(p, toward, n=10):
        for i in range(n):
            f = i / n
            o = toward * (20 + 180 * f) + Vector(rng.normal(0, 6 + 20 * f, 3))
            obj('Smoke', bead_me, smoke_mat, Matrix.Translation(p + o) @ Matrix.Scale(5 + 14 * f, 4))

    def flares(key):
        for i in range(10):
            p = at(key, (rng.uniform(-60, 40), rng.choice((-1, 1)) * rng.uniform(50, 120), rng.uniform(-10, 40)))
            obj('Flare', bead_me, flare_mat, Matrix.Translation(p) @ Matrix.Scale(1.6, 4))

    def entry_trails(labels3d):
        """Two plasma trails 100-180 km up, 150-350 km downrange and heading north, from orbit."""
        head_mat = emission_mat('Plasma head', (1, .85, .6), 60.0)
        for name, side, scale in (('trail-colossus', 0.0, 1.0), ('trail-morrow', -.0045, .7)):
            pts = []
            for i in range(31):
                f = i / 30
                th = (150e3 + 650e3 * f) / R              # downrange arc, prograde (north-east)
                alt = 150e3 - 105e3 * f ** .8
                sd = side + .0035 * f                      # a slight drift off the track
                dirn = Vector((math.sin(th), sd, math.cos(th))).normalized()
                pts.append(centre + dirn * (R + alt))
            # Faint where the heating starts, brightest and widest at the ships at the far end.
            for i in range(len(pts) - 1):
                f = i / (len(pts) - 1)
                rad = (250 + 1600 * f ** 1.5) * scale
                segment(pts[i + 1], pts[i], rad, plasma_mat, 'Plasma ' + name)
            head = pts[-1]
            obj('Plasma head', bead_me, head_mat, Matrix.Translation(head) @ Matrix.Scale(2200 * scale, 4))
            labels3d[name] = head

    def platform(p):
        """The derelict Asharu platform: bulk-carrier segments welded between emptied tanks."""
        mat = principled('Platform', (.32, .3, .28), rough=.75, metal=.4)
        base = Vector(p)
        for i in range(6):
            o = Vector((i * 110 - 280, rng.uniform(-8, 8), rng.uniform(-6, 6)))
            rot = Euler((0, 0, rng.uniform(-.08, .08))).to_matrix().to_4x4()
            obj('Platform bulk segment', box_me, mat, Matrix.Translation(base + o) @ rot @ Matrix.Diagonal((48, 22, 18, 1)))
        for i in range(18):
            o = Vector((rng.uniform(-320, 300), rng.choice((-1, 1)) * rng.uniform(40, 75), rng.uniform(-30, 30)))
            obj('Platform tank', bead_me, mat, Matrix.Translation(base + o) @ Matrix.Scale(rng.uniform(14, 22), 4))
        for side in (-1, 1):
            obj('Platform collector', box_me, mat, Matrix.Translation(base + Vector((0, side * 190, 0)))
                @ Matrix.Diagonal((150, 70, 1.5, 1)))
        obj('Platform spine', plume_me, mat, Matrix.Translation(base + Vector((-380, 0, 0)))
            @ Vector((1, 0, 0)).to_track_quat('Z', 'Y').to_matrix().to_4x4() @ Matrix.Diagonal((12, 12, 760, 1)))

    def apply_cue(s, cue):
        """One ship's effects cue for this frame (KEYFRAMES 'cues'): 'fire' lists the guns flashing
        ('t1_1' ..., or 'all'); any other entry sets that knob on this ship only."""
        for name, val in cue.items():
            if name == 'fire':
                for lab in (s.gun_labels() if val == 'all' else val):
                    s.flash(lab)
            else:
                s.set(name, val)

    def fx_report(kf):
        """--fx-check: what each ship's own copy reads on this frame, evaluated."""
        dg = bpy.context.evaluated_depsgraph_get()
        for k, pl in placed.items():
            if pl['fx'] is not None:
                row = pl['fx'].reading(dg)
                FX_ROWS.append(dict(frame=kf, ship=k, **row))
                print(f'ASHARU fx {kf} {k:12} {json.dumps(row)}', flush=True)
                # Light scale: the kit sizes its lights for a hull-unit file (watts / mpu^2); placed at
                # Scale(mpu) in this metre scene the distances grow by mpu but Cycles leaves a light's
                # power alone, so each lit lamp reads 1/mpu^2 too bright here.
                lights = [o for o in pl['fx'].ids if isinstance(o, bpy.types.Object) and o.type == 'LIGHT'
                          and o.users_collection]
                lit = [(o, o.data.evaluated_get(dg).energy) for o in lights]
                lit = [(o, e) for o, e in lit if e > 0]
                vols = sum(1 for o in pl['fx'].ids if isinstance(o, bpy.types.Material) and o.node_tree
                           and any(n.type in ('PRINCIPLED_VOLUME', 'VOLUME_SCATTER', 'VOLUME_ABSORPTION', 'EMISSION')
                                   and any(l.to_socket.name == 'Volume' for l in n.outputs[0].links)
                                   for n in o.node_tree.nodes if n.outputs))
                top = max(lit, key=lambda t: t[1]) if lit else None
                print(f'ASHARU light {kf} {k:12} mpu {pl["mpu"]:.3f} over x{1 / pl["mpu"] ** 2:.2f} '
                      f'lights {len(lights)} lit {len(lit)} sum {sum(e for _, e in lit):.0f} W'
                      + (f' top {ship_fx.base_name(top[0].name)} {top[1]:.0f} W -> {top[1] * pl["mpu"] ** 2:.0f} W'
                         if top else '') + f' volume-mats {vols}', flush=True)
                # Stale transforms: the copy is only ever instanced, never evaluated, so a stored
                # matrix_world can disagree with its parent chain; anything reading it is off by this.
                worst, name = 0.0, ''
                for o in pl['fx'].ids:
                    if isinstance(o, bpy.types.Object) and o.parent is not None:
                        d = (o.matrix_world.translation - mat_chain(o).translation).length
                        if d > worst:
                            worst, name = d, ship_fx.base_name(o.name)
                print(f'ASHARU stale {kf} {k:12} worst {worst:.2f} u = {worst * pl["mpu"]:.1f} m {name}', flush=True)

    def clear_stage():
        for o in list(stage.objects):
            bpy.data.objects.remove(o)
        placed.clear()

    cam = link(bpy.data.objects.new('Camera', bpy.data.cameras.new('Camera')))
    scene.camera = cam
    cam.data.sensor_fit = 'HORIZONTAL'
    cam.data.sensor_width = 36.0
    cam.data.clip_start = 2.0
    cam.data.clip_end = 4.0e6

    def aim(loc, target=None, lens=35, look=None, roll=0.0):
        cam.location = Vector(loc)
        d = (Vector(target) - Vector(loc)) if target is not None else Vector(look)
        cam.rotation_mode = 'QUATERNION'
        cam.rotation_quaternion = d.normalized().to_track_quat('-Z', 'Y') @ Quaternion((0, 0, 1), math.radians(roll))
        cam.data.lens = lens
        cam.data.type = 'PERSP'
        bpy.context.view_layer.update()

    def project(p):
        v = world_to_camera_view(scene, cam, Vector(p))
        if v.z <= 0 or not (-.05 <= v.x <= 1.05 and -.05 <= v.y <= 1.05):
            return None
        return [round(v.x * W, 1), round((1 - v.y) * H, 1)]

    def ship_label(key):
        if key not in placed:
            return None
        pl = placed[key]
        p = pl['matrix'].translation
        # Below the hull's footprint on screen: drop the point by half the hull's length.
        dn = cam.matrix_world.to_3x3() @ Vector((0, -1, 0))
        return project(p + dn * pl['length'] * .45)

    # ------------------------------------------------------------------------- roster sheets
    def bounds(lib):
        """(length, beam, height) of a library hull in metres, from its collection's objects."""
        lo, hi = Vector((1e9,) * 3), Vector((-1e9,) * 3)
        for o in lib['coll'].all_objects:
            if o.type != 'MESH':
                continue
            for c in o.bound_box:
                w = mat_chain(o) @ Vector(c)
                lo = Vector(map(min, lo, w))
                hi = Vector(map(max, hi, w))
        d = (hi - lo) * lib['mpu']
        return d.x, d.y, d.z

    def mat_chain(o):
        m = o.matrix_basis.copy()
        while o.parent is not None:
            m = o.parent.matrix_basis @ o.matrix_parent_inverse @ m
            o = o.parent
        return m

    def roster_sheet(kf):
        """Both fleets in rows at true relative size, with the scale references: r1 from above
        (the game's own view), r2 broadside. Orthographic, planet hidden, studio-lit."""
        ground.hide_render = True
        air.hide_render = True
        sun.data.energy = 4.0
        sun.rotation_euler = Vector((.35, -.55, .76)).to_track_quat('Z', 'Y').to_euler()
        rows = []
        for fleet, ms in (('merchant', roster.MERCHANT), ('pirate', roster.PIRATE), ('reference', roster.REFERENCES)):
            seen, row = set(), []
            for m in ms:
                tag = (m['game'], bool(m.get('tint')))
                if tag in seen:
                    continue
                seen.add(tag)
                row.append(m)
            rows.append(row)
        gap = 22.0
        width = max(sum(hull(m)['length_m'] for m in row) + gap * (len(row) - 1) for row in rows)
        beam = [max(bounds(hull(m))[1 if kf == 'r1' else 2] for m in row) for row in rows]
        heights = [b + 70.0 for b in beam]                 # the hull plus room for its label
        ortho = max(width * 1.06, sum(heights) * 1.25 * W / H)
        top = sum(heights) / 2 + 20.0
        lab = {}
        for ri, row in enumerate(rows):
            total = sum(hull(m)['length_m'] for m in row) + gap * (len(row) - 1)
            x = -total / 2
            yrow = top - sum(heights[:ri]) - beam[ri] / 2
            for m in row:
                L = hull(m)['length_m']
                cxm = x + L / 2
                if kf == 'r1':
                    loc = (cxm, yrow, 0)
                    place(m['key'], (*loc, 0, 0, 0, 0))
                else:
                    loc = (cxm, 0, yrow)
                    place(m['key'], (*loc, 0, 0, 0, 0))
                lab[m['key']] = dict(name=m['name'], length_m=round(L, 1), row=ri,
                                     basis=roster.size_row(m)['basis'], at=None, loc=list(loc))
                x += L + gap
        if kf == 'r1':
            cam.location = (0, 0, 2000)
            cam.rotation_mode = 'XYZ'
            cam.rotation_euler = (0, 0, 0)
        else:
            cam.location = (0, -2000, 0)
            cam.rotation_mode = 'XYZ'
            cam.rotation_euler = (math.radians(90), 0, 0)
        cam.data.type = 'ORTHO'
        cam.data.ortho_scale = ortho
        bpy.context.view_layer.update()
        for k, v in lab.items():
            pl = placed[k]
            p = pl['matrix'].translation
            b = beam[lab[k]['row']] / 2 + 8
            off = Vector((0, -b, 0)) if kf == 'r1' else Vector((0, 0, -b))
            v['at'] = project(p + off)
        lab['_metres_per_px'] = ortho / W
        world.node_tree.nodes['Background'].inputs['Color'].default_value = (.035, .037, .042, 1)
        world.node_tree.nodes['Star gain'].inputs[1].default_value = 0.0      # a roster sheet is a studio
        return lab

    labels = {}
    for kf in cfg['frames']:
        clear_stage()
        air.hide_render = False
        ground.hide_render = False
        sun.data.energy = 4.6
        sun.rotation_euler = sun_dir.to_track_quat('Z', 'Y').to_euler()
        world.node_tree.nodes['Background'].inputs['Color'].default_value = (.0008, .0009, .0014, 1)
        world.node_tree.nodes['Star gain'].inputs[1].default_value = STAR_GAIN
        if kf in KEYFRAMES:
            spec = KEYFRAMES[kf]
            for key, sp in spec['ships'].items():
                place(key, sp, spec.get('states', {}).get(key))
            for lib in library.values():     # a build's control knobs: this frame's pose, else its saved pose
                if lib.get('ctl') is not None:
                    for name, rest in lib['knobs'].items():
                        lib['ctl'][name] = rest
            for s in ships_fx.values():      # every ship's own copy: saved pose, no flashes
                s.reset()
            for key, pl in placed.items():   # the kit's drives follow the placement's throttle
                if pl['fx'] is not None and pl['fx'].fx is not None and not pl['lib']['swfx']:
                    pl['fx'].set('thr_throttle', pl['throttle'])
            for key, kn in spec.get('knobs', {}).items():
                for name, val in kn.items():
                    if isinstance(val, dict):     # {'ago_s': t}: a frame t seconds before this one
                        val = scene.frame_current - round(val['ago_s'] * scene.render.fps / scene.render.fps_base)
                    if placed[key]['fx'] is not None:
                        placed[key]['fx'].set(name, val)
                    else:
                        placed[key]['lib']['ctl'][name] = val
            for key, cue in spec.get('cues', {}).items() if cfg.get('swfx') else ():
                apply_cue(placed[key]['fx'], cue)
            if ships_fx or any(lib.get('ctl') is not None for lib in library.values()):
                ship_fx.refresh(scene)     # drivers read the knobs
                bpy.context.view_layer.update()
            if cfg.get('fx_check'):
                fx_report(kf)
            extra3d = {}
            if spec.get('platform'):
                platform(spec['platform'])
                extra3d['platform'] = Vector(spec['platform']) + Vector((0, 0, -140))
            muzzles = {(k, a, b): (best[5] if best else None) for k, a, b, best, _ in bearing(kf)}
            line_of_sight(kf, spec, muzzles, cfg.get('los'))     # picks, per shot, a bearing gun with a clear line
            for fx in spec.get('fx', ()):
                kind = fx[0]
                if kind == 'tracer':
                    tracer(fx[1], fx[2], fx[3] if len(fx) > 3 else None, muzzle=muzzles.get(fx[:3]))
                elif kind == 'missiles':
                    missiles(fx[1], fx[2], muzzle=muzzles.get(fx[:3]))
                elif kind == 'blast':
                    blast(at(fx[1]), fx[2])
                elif kind == 'blast_on':
                    blast(at(fx[1], fx[3]), fx[2])
                elif kind == 'fire_on':     # optional 4th: the flame's direction in the hull frame (default aft and up)
                    d = fx[3] if len(fx) > 3 else (-1, .4, .3)
                    fire(at(fx[1], fx[2]), (placed[fx[1]]['matrix'].to_3x3() @ Vector(d)).normalized())
                elif kind == 'smoke':
                    d = fx[3] if len(fx) > 3 else (-1, .2, .5)
                    smoke(at(fx[1], fx[2]), (placed[fx[1]]['matrix'].to_3x3() @ Vector(d)).normalized())
                elif kind == 'debris':
                    debris(at(fx[1], fx[3]) if len(fx) > 3 else at(fx[1]), fx[2])
                elif kind == 'flares':
                    flares(fx[1])
                elif kind == 'entry_trails':
                    entry_trails(extra3d)
            c = spec['camera']
            aim(c['loc'], c.get('target'), c['lens'], c.get('look'))
            lab = {}
            for key in spec['labels']:
                if key in extra3d:
                    lab[key] = project(extra3d[key])
                else:
                    lab[key] = ship_label(key)
            labels[kf] = lab
        else:
            labels[kf] = roster_sheet(kf)
        if cfg.get('stage_only'):                 # --stage-only: build and pose every hull, render nothing
            print('ASHARU staged', kf, flush=True)
            continue
        partial = raw / f'{kf}.partial'           # not *.png, so a queue's frame count never counts it
        scene.render.use_file_extension = False
        scene.render.filepath = str(partial)
        bpy.ops.render.render(write_still=True)
        os.replace(partial, raw / f'{kf}.png')      # a PNG under its own name is always a finished one
        store = raw / 'labels.json'   # saved per frame: a stopped job keeps every finished frame's labels
        old = json.loads(store.read_text()) if store.exists() else {}
        store.write_text(json.dumps({**old, kf: labels[kf]}, indent=1))
        (raw / f'{kf}.meta.json').write_text(json.dumps(frame_meta(cfg['samples'], cfg.get('half', False))))
        print('ASHARU rendered', kf, flush=True)
    if cfg.get('fx_check'):
        fx_verdict(list(ships_fx.values()), [lib.get('fx') for lib in library.values()], out)


FX_ROWS = []     # --fx-check: one row per ship per frame, what its own copy evaluated to


def fx_verdict(ships, library_fx, out):
    """--fx-check's verdict: every ship's copy reaches only itself (ship_fx.audit), no driver is
    invalid, and on some frame two ships of one build read different throttle or firing values."""
    import ship_fx
    audit = ship_fx.audit(ships, library_fx)
    invalid = sorted({s.key for s in ships for i in s.ids for fc in ship_fx._drivers(i) if not fc.driver.is_valid})
    builds = {}
    for s in ships:
        builds.setdefault(s.manifest['asset'] if 'asset' in s.manifest else s.manifest['append_collection'],
                          set()).add(s.key)
    pairs = []
    for kf in dict.fromkeys(r['frame'] for r in FX_ROWS):
        rows = {r['ship']: r for r in FX_ROWS if r['frame'] == kf}
        for build, keys in builds.items():
            here = sorted(k for k in keys if k in rows)
            for i, a in enumerate(here):
                for b in here[i + 1:]:
                    ra, rb = rows[a], rows[b]
                    diff = [n for n in ('thr_throttle', 'plume', 'firing', 'flash_W', 'gear_down', 'grapples')
                            if n in ra and ra.get(n) != rb.get(n)]
                    if diff:
                        pairs.append(dict(frame=kf, build=build, ships=[a, b], differ=diff,
                                          a={n: ra.get(n) for n in diff}, b={n: rb.get(n) for n in diff}))
    foreign = {k: v['foreign'] for k, v in audit.items() if v['foreign']}
    ok = not foreign and not invalid and bool(pairs)
    report = dict(ok=ok, ships=len(ships), audit={k: dict(own=v['own'], shared=v['shared'], foreign=v['foreign'])
                                                  for k, v in audit.items()},
                  invalid_drivers=invalid, independent_pairs=pairs, rows=FX_ROWS)
    (out / 'fx-check.json').write_text(json.dumps(report, indent=1))
    for k, v in audit.items():
        print(f"ASHARU fx audit {k:12} own {v['own']:5}  shared {v['shared']:4}  foreign {len(v['foreign'])}", flush=True)
    for pr in pairs:
        print(f"ASHARU fx independent {pr['frame']}: {pr['ships'][0]} {pr['a']}  vs  {pr['ships'][1]} {pr['b']}",
              flush=True)
    print('ASHARU fx-check', 'PASS' if ok else 'FAIL', out / 'fx-check.json', flush=True)
    if not ok:
        raise SystemExit(1)


# ======================================================================================= driver

def frame_meta(samples, half=False):
    """What a finished frame was rendered with: its samples and the staging source it came from."""
    import hashlib
    h = hashlib.sha256()
    for f in (HERE / 'battle_blockout.py', HERE / 'roster.py'):
        h.update(f.read_bytes())
    return {'samples': samples, 'half': half, 'source_sha256': h.hexdigest()}


def finished(raw, kf, samples, half=False):
    """Whether raw/<kf>.png is a finished frame of this staging at these samples."""
    png, meta, store = raw / f'{kf}.png', raw / f'{kf}.meta.json', raw / 'labels.json'
    try:
        if json.loads(meta.read_text()) != frame_meta(samples, half) or kf not in json.loads(store.read_text()):
            return False
        with open(png, 'rb') as fh:
            fh.seek(-12, 2)
            return fh.read(12)[4:8] == b'IEND'
    except (OSError, ValueError):
        return False


def git_commit():
    try:
        return subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD'], cwd=REPO, text=True).strip()
    except Exception:
        return 'unknown'


def label_frames(run):
    from PIL import Image, ImageDraw
    from review_font import review_font
    labels = json.loads((run / 'raw' / 'labels.json').read_text())
    f_title, f_cap, f_lab = review_font(30, True), review_font(21), review_font(19, True)
    names = {m['key']: m['name'] for m in roster.MERCHANT + roster.PIRATE + roster.REFERENCES}
    names.update({'platform': 'Asharu platform (derelict)', 'trail-colossus': 'Colossus', 'trail-morrow': 'Morrow'})
    pirates = {m['key'] for m in roster.PIRATE}
    order = [k for k in KEYFRAMES] + list(ROSTER_SHEETS)
    outputs = []
    for i, kf in enumerate([k for k in order if (run / 'raw' / f'{k}.png').exists()]):
        im = Image.open(run / 'raw' / f'{kf}.png').convert('RGB')
        if im.size != (W, H):       # a --half light check: labels are placed for the full frame
            im = im.resize((W, H), Image.LANCZOS)
        dr = ImageDraw.Draw(im, 'RGBA')
        lab = labels.get(kf, {})
        if kf in KEYFRAMES:
            spec = KEYFRAMES[kf]
            title = f"{spec['shot']}  {spec['title']}"
            cap = spec['caption']
            boxes = []
            for key, p in sorted(lab.items(), key=lambda kv: (kv[1] or [0, 0])[1]):
                if not p:
                    continue
                col = (255, 140, 110, 255) if key in pirates else (150, 210, 255, 255)
                txt = names.get(key, key)
                tw = dr.textlength(txt, font=f_lab)
                x, y = min(max(p[0] - tw / 2, 8), W - tw - 8), min(max(p[1], 70), H - 120)
                while any(x < bx1 and x + tw > bx0 and y < by1 and y + 26 > by0 for bx0, by0, bx1, by1 in boxes):
                    y += 28
                boxes.append((x - 5, y - 2, x + tw + 5, y + 24))
                dr.rectangle((x - 5, y - 2, x + tw + 5, y + 24), fill=(0, 0, 0, 140))
                dr.text((x, y), txt, font=f_lab, fill=col)
        else:
            mpp = lab.pop('_metres_per_px')
            title = 'Fleet roster from above' if kf == 'r1' else 'Fleet roster broadside'
            cap = ('Every hull at true relative size. Blue: merchant convoy. Red: pirates. Grey: known ships for scale. '
                   'Sprite-painted slabs are stand-ins at option C size; the rest are the finished models.')
            for key, v in lab.items():
                if not v['at']:
                    continue
                col = ((255, 140, 110, 255) if v['row'] == 1 else (150, 210, 255, 255) if v['row'] == 0
                       else (200, 200, 200, 255))
                for j, txt in enumerate((v['name'], f"{v['length_m']:.0f} m" +
                                         ('' if v['basis'].startswith('finished') else '  stand-in'))):
                    tw = dr.textlength(txt, font=f_lab)
                    dr.text((v['at'][0] - tw / 2, v['at'][1] + j * 22), txt, font=f_lab, fill=col)
            bar = 100 / mpp
            dr.rectangle((60, H - 150, 60 + bar, H - 142), fill=(230, 230, 230, 255))
            dr.text((60, H - 138), '100 m', font=f_lab, fill=(230, 230, 230, 255))
        dr.rectangle((0, 0, W, 58), fill=(0, 0, 0, 170))
        dr.text((22, 12), title, font=f_title, fill=(255, 255, 255, 255))
        dr.rectangle((0, H - 92, W, H), fill=(0, 0, 0, 170))
        y = H - 82
        for line in wrap(dr, cap, f_cap, W - 44):
            dr.text((22, y), line, font=f_cap, fill=(230, 230, 230, 255))
            y += 27
        name = f"{order.index(kf) + 2:02d}-{kf}-{slug(title)}.png"   # by shot order: two jobs' halves interleave
        im.save(run / name)
        outputs.append(name)
    return outputs


def slug(s):
    import re
    return re.sub(r'[^a-z0-9]+', '-', s.lower()).strip('-')[:40]


def wrap(dr, text, font, width):
    words, lines, cur = text.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if dr.textlength(t, font=font) > width and cur:
            lines.append(cur)
            cur = w
        else:
            cur = t
    return lines + [cur]


def orbit_diagram(run):
    """01: where the battle happens. Main picture: Asharu's limb, the convoy's orbit, the battle and
    the fall to the northern canyons (altitude exaggerated). Inset: the battle itself side-on at
    kilometre scale, from KEYFRAMES k1 and k2."""
    from PIL import Image, ImageDraw
    from review_font import review_font
    im = Image.new('RGB', (W, H), (8, 9, 14))
    dr = ImageDraw.Draw(im, 'RGBA')
    ft, fl, fs = review_font(34, True), review_font(22, True), review_font(20)
    EX = 2.2                                  # altitude exaggeration
    KM_PX = 1.6                               # km per pixel
    x0, ysurf = 260, 880                      # the battle's ground point on screen

    def P(downrange_km, alt_km):
        """Screen point: downrange along the orbit (north-east to the right), altitude up."""
        th = downrange_km / (R / 1e3)
        rr = R / 1e3 + alt_km * EX
        x = x0 + rr * math.sin(th) / KM_PX
        y = ysurf + (R / 1e3 - rr * math.cos(th)) / KM_PX
        return x, y

    ground = [P(d, 0) for d in range(-600, 2800, 20)]
    dr.polygon(ground + [(W, H), (0, H)], fill=(150, 116, 80))
    dr.line(ground, fill=(196, 160, 116), width=3)
    dr.line([P(d, 14) for d in range(-600, 2800, 20)], fill=(200, 190, 170, 90), width=2)
    orbit = [P(d, ALT / 1e3) for d in range(-600, 2800, 20)]
    dr.line(orbit, fill=(120, 170, 230), width=3)
    fall = [P(d, ALT / 1e3 * (1 - (d / 1450) ** 1.25)) for d in range(0, 1451, 10)]
    dr.line(fall, fill=(255, 180, 110), width=5)
    glow = [P(d, ALT / 1e3 * (1 - (d / 1450) ** 1.25)) for d in range(650, 1451, 10)]
    dr.line(glow, fill=(255, 225, 170), width=9)
    bx, by = P(0, ALT / 1e3)
    dr.ellipse((bx - 11, by - 11, bx + 11, by + 11), outline=(255, 255, 255), width=3)
    cx, cy = P(1450, 0)
    dr.polygon([(cx - 70, cy + 4), (cx - 40, cy - 38), (cx - 18, cy - 14), (cx + 10, cy - 52),
                (cx + 34, cy - 20), (cx + 64, cy + 4)], fill=(105, 80, 58))

    def label(xy, txt, col, dx=0, dy=-34):
        tw = dr.textlength(txt, font=fs)
        dr.text((min(max(xy[0] + dx - tw / 2, 12), W - tw - 12), xy[1] + dy), txt, font=fs, fill=col)

    label((bx, by), 'the battle: low orbit, 250 km up', (255, 255, 255), dx=40, dy=-44)
    label(P(1700, ALT / 1e3), 'convoy orbit, prograde to the north-east', (120, 170, 230), dx=-60, dy=14)
    label(P(420, 180), 'Colossus and Morrow lose their orbit', (255, 180, 110), dx=-200, dy=14)
    label(P(1000, 95), 'entry: plasma trails (S12)', (255, 225, 170), dx=170, dy=-10)
    label((cx, cy), "northern canyon wall: Basin's valley (the crash: a later shot)", (240, 220, 190), dx=-60, dy=22)
    dr.text((22, 18), 'Orbital geometry: where the battle is fought', font=ft, fill=(255, 255, 255))
    for j, line in enumerate((
            f'Asharu taken as {R / 1e6:.0f},000 km radius, convoy at {ALT / 1e3:.0f} km (altitude drawn {EX:.1f}x). '
            'Orbit about 6 km/s; the desert slides past beneath at about 1.3 degrees a second.',
            'From 250 km the horizon sits 17.6 degrees below level: wide shots tilt down to put the limb high in '
            'frame; looking down, the desert fills the frame behind the ships.',
            'Sun low (16 degrees up), ahead and to the south-east: long shadows across the dunes, ships side-lit. '
            'The fall runs about 1,500 km downrange.')):
        dr.text((22, 64 + j * 28), line, font=fs, fill=(210, 210, 210))
    # Inset: the battle side-on, km scale, from the k1/k2 placements.
    ix0, iy0, iw, ih = W - 820, 170, 790, 430
    dr.rectangle((ix0, iy0, ix0 + iw, iy0 + ih), fill=(14, 16, 24), outline=(90, 96, 110), width=2)
    dr.text((ix0 + 16, iy0 + 10), 'Inset: the ambush, side-on (1 km grid)', font=fl, fill=(230, 230, 230))
    sx, sz = 50.0, 50.0                        # px per km

    def Q(xm, zm):
        return ix0 + iw - 140 + xm / 1e3 * sx, iy0 + 120 - zm / 1e3 * sz

    for k in range(-12, 3):
        x, _ = Q(k * 1e3, 0)
        dr.line((x, iy0 + 46, x, iy0 + ih - 10), fill=(40, 44, 56))
    for k in range(-6, 2):
        _, y = Q(0, k * 1e3)
        if iy0 + 46 < y < iy0 + ih:
            dr.line((ix0 + 4, y, ix0 + iw - 4, y), fill=(40, 44, 56))
    plat = KEYFRAMES['k2']['platform']
    px, py = Q(plat[0], plat[2])
    dr.rectangle((px - 18, py - 7, px + 18, py + 7), fill=(150, 140, 128))
    label((px, py), 'derelict platform', (200, 190, 175), dy=14)
    for key, sp in KEYFRAMES['k2']['ships'].items():
        if key in CONVOY:
            continue
        x, y = Q(sp[0], sp[2])
        dr.ellipse((x - 4, y - 4, x + 4, y + 4), fill=(230, 120, 90))
    for key, sp in CONVOY.items():
        x, y = Q(sp[0], sp[2])
        dr.ellipse((x - 5, y - 5, x + 5, y + 5), fill=(120, 170, 230))
    a, b = Q(-8.5e3, -3.6e3), Q(-1.8e3, -.4e3)
    dr.line((a, b), fill=(230, 120, 90), width=3)
    dr.polygon([(b[0], b[1]), (b[0] - 16, b[1] - 2), (b[0] - 10, b[1] + 13)], fill=(230, 120, 90))
    label(Q(0, 0), 'convoy column', (120, 170, 230), dx=0, dy=-38)
    label(Q(-5.2e3, -2.9e3), 'pirates climb out and close', (230, 120, 90), dx=60, dy=40)
    gx, gy = Q(1.5e3, -4.6e3)
    dr.line((gx - sx, gy, gx, gy), fill=(230, 230, 230), width=3)
    dr.text((gx - sx, gy + 6), '1 km', font=fs, fill=(230, 230, 230))
    dr.text((ix0 + 16, iy0 + ih - 34), 'prograde to the right; the platform sits about 4 km lower and 10 km astern',
            font=fs, fill=(170, 170, 180))
    name = '01-orbit-geometry.png'
    im.save(run / name)
    return name


def check(frames):
    """Where each ship lands in each key frame, without rendering: centre pixel, length in
    pixels, and whether it is in frame. The camera math is Blender's track ('-Z', 'Y')."""
    lengths = {m['key']: roster.size_row(m)['length_m'] for m in roster.MERCHANT + roster.PIRATE}
    for kf in frames:
        if kf not in KEYFRAMES:
            continue
        c = KEYFRAMES[kf]['camera']
        loc = c['loc']
        f = [b - a for a, b in zip(loc, c['target'])] if c.get('target') else list(c['look'])
        n = math.sqrt(sum(x * x for x in f))
        f = [x / n for x in f]
        r = [f[1], -f[0], 0.0]
        n = math.sqrt(sum(x * x for x in r))
        r = [x / n for x in r]
        u = [r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]]
        fpx = c['lens'] / 36.0 * W
        pitch = math.degrees(math.asin(f[2]))
        dip = math.degrees(math.acos(R / (R + ALT + loc[2])))
        hy = H / 2 - fpx * math.tan(math.radians(-dip - pitch))
        print(f'{kf}: pitch {pitch:.1f}, horizon at y={hy:.0f}' + ('' if 0 <= hy <= H else ' (out of frame)'))
        for key, sp in KEYFRAMES[kf]['ships'].items():
            d = [b - a for a, b in zip(loc, sp[:3])]
            z = sum(a * b for a, b in zip(d, f))
            if z <= 0:
                print(f'   {key:12} behind camera')
                continue
            x = W / 2 + fpx * sum(a * b for a, b in zip(d, r)) / z
            y = H / 2 - fpx * sum(a * b for a, b in zip(d, u)) / z
            inside = 0 <= x <= W and 60 <= y <= H - 90
            print(f'   {key:12} ({x:6.0f}, {y:6.0f})  {lengths[key] / z * fpx:5.0f} px  {z:6.0f} m'
                  + ('' if inside else '   OUT'))
        for kind, a, b, best, tgt_el in bearing(kf):
            if best is None:
                verdict = 'no guns known'
            elif best[0]:
                verdict = f'bears ({best[2]}, el {best[3]:+.0f}, {best[4]:+.0f} off rest)'
            else:
                verdict = f'CANNOT BEAR: target {tgt_el:+.0f} deg off her deck plane, best {best[2]} short by {best[1]:.0f} deg'
            print(f'   {kind:8} {a:12} -> {b:12} {verdict}')
        for line in cue_lines(kf):
            print('   ' + line)


def cue_lines(kf):
    """--check's view of a frame's effects cues: what each ship's own SWFX copy is set to (under
    --swfx) beside the other ships of its build, and anything a cue names that is not there."""
    spec = KEYFRAMES[kf]
    cues = spec.get('cues', {})
    if not cues:
        return []
    out = []
    by_build = {}
    for key, sp in spec['ships'].items():
        m = next((m for m in roster.MERCHANT + roster.PIRATE if m['key'] == key), {})
        build = spec.get('states', {}).get(key) or m.get('finished')
        if build:
            by_build.setdefault(build, []).append(key)
    for key, cue in cues.items():
        if key not in spec['ships']:
            out.append(f'cue      {key:12} NOT IN FRAME')
            continue
    for build, keys in by_build.items():
        if not any(k in cues for k in keys):
            continue
        for k in keys:
            cue = cues.get(k, {})
            fire = cue.get('fire', [])
            bad = []
            if fire != 'all' and fire:
                try:
                    have = set(gun_labels(build))
                    bad = [lab for lab in fire if have and lab not in have]
                except OSError:
                    pass
            knobs = {n: v for n, v in cue.items() if n != 'fire'}
            out.append(f"cue      {k:12} [{build}] throttle {spec['ships'][k][6]:.2f}  fire {fire or '-'}"
                       + (f'  knobs {knobs}' if knobs else '') + (f'  UNKNOWN GUNS {bad}' if bad else ''))
    return out


def gun_labels(build):
    """'t<turret>_<k>' for the guns a build's manifest lists (a list-form effects.anchors.guns)."""
    rows = ((build_manifest(roster.FINISHED[build]).get('effects') or {}).get('anchors') or {}).get('guns')
    return [f"t{g['turret']}_{g.get('barrel', 1)}" for g in rows if g.get('kind', 'gun') != 'launcher'] \
        if isinstance(rows, list) else []


GUN_ELEVATION = (-10.0, 85.0)   # a dorsal turret's reach above (and just below) its own deck plane
MISSILE_CONE = 90.0              # a guided missile can leave a fixed tube up to this far off its target


def _rot(hd, pt, rl):
    """Local-to-world rotation of a ship spec, the same order as frame_of: Rz(hd) Ry(-pt) Rx(rl)."""
    def rz(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, -s, 0], [s, c, 0], [0, 0, 1]]

    def ry(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]

    def rx(a):
        c, s = math.cos(a), math.sin(a)
        return [[1, 0, 0], [0, c, -s], [0, s, c]]

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(mul(rz(math.radians(hd)), ry(-math.radians(pt))), rx(math.radians(rl)))


def muzzle_of(t, mpu, mount_m):
    """Where a mount's shot leaves, from the build's current muzzle data: ('m', metres in the ship's
    frame), or ('anchor', name) when the manifest names the anchor without placing it (the anchor
    is then found in the appended build). Builds write it differently: muzzle_locations (units),
    muzzles / muzzle entries with an 'at' (units), or muzzles as anchor names."""
    locs = t.get('muzzle_locations')
    if isinstance(locs, list) and locs and isinstance(locs[0], list):
        return 'm', [v * mpu for v in locs[0]]
    named = [t.get(k) for k in ('muzzles', 'muzzle_anchors') if isinstance(t.get(k), list)]
    for m in sum(named, []) + [t.get('muzzle')]:
        if isinstance(m, dict) and isinstance(m.get('at'), list):
            return 'm', [v * mpu for v in m['at']]
        if isinstance(m, dict) and m.get('anchor'):
            return 'anchor', m['anchor']
        if isinstance(m, str):
            return 'anchor', m
    return 'm', mount_m


def mounts(key, state=None):
    """A ship's guns in its own frame, metres: (position, rest bearing, arc, kind). Finished hulls
    from their manifests (dorsal turrets that train in azimuth, fixed hardpoints); stand-ins from
    the game's weapon slots, laid on the slab's deck."""
    m = next(r for r in roster.MERCHANT + roster.PIRATE if r['key'] == key)
    fin = state or m.get('finished')
    out = []
    if fin:
        f = roster.FINISHED[fin]
        man = build_manifest(f)
        mpu = man['metres_per_unit']
        rows = list(man.get('turrets') or []) + list(man.get('launchers') or [])   # some builds list launchers apart
        for w in man.get('weapon_slots', []) + man.get('weapons', []):
            rows.append(w)
        for t in rows if isinstance(rows, list) else []:
            at = t.get('pivot_at') or t.get('location') or t.get('at') or (t.get('pivot') or {}).get('at')
            if at is None:
                mz = (t.get('muzzles') or [{}])[0]
                at = mz.get('at') if isinstance(mz, dict) else None
            if at is None:
                continue
            rest = t.get('rest_angle_deg', t.get('angle_deg', t.get('angle', 0)))
            arc = t.get('arc_deg', t.get('arc', 360))
            kind = 'hardpoint' if t.get('mount') == 'HARDPOINT' else 'turret'
            pos = [v * mpu for v in at] + [0] * (3 - len(at))
            launcher = t.get('kind') == 'launcher' or 'missile' in str(t.get('type', '')).lower()
            out.append((pos, rest, arc, kind, muzzle_of(t, mpu, pos), launcher))
    else:
        spec = roster.game_spec(m['game'])
        _, length_px, _ = roster.sprite_stats(spec['sprite'])
        mpu = roster.size_row(m)['length_m'] / length_px
        for w in spec['weapons']:
            pos = [w['location'][0] * mpu, w['location'][1] * mpu, 0.0]
            out.append((pos, w['angle'], w['arc'], 'hardpoint' if w['mount'] == 'HARDPOINT' else 'turret',
                        ('m', pos), w.get('type') in ('MISSILE', 'COMPOSITE', 'SYNERGY', 'UNIVERSAL')))
    return out


def bearing(kf):
    """For every shot in a key frame, whether the shooter has a gun that can bear on its target."""
    spec = KEYFRAMES[kf]
    ships = spec['ships']
    states = spec.get('states', {})
    rows = []
    for fx in spec.get('fx', []):
        if fx[0] not in ('tracer', 'missiles') or fx[1] not in ships or fx[2] not in ships:
            continue
        a, b = fx[1], fx[2]
        sa, sb = ships[a], ships[b]
        R = _rot(*sa[3:6])
        d = [sb[i] - sa[i] for i in range(3)]
        loc = [sum(R[j][i] * d[j] for j in range(3)) for i in range(3)]     # world -> shooter frame
        best, every = None, []
        for pos, rest, arc, kind, muzzle, launcher in mounts(a, states.get(a)):
            fits = launcher == (fx[0] == 'missiles')      # missiles from a launcher, tracers from a gun, when one bears
            v = [loc[i] - pos[i] for i in range(3)]
            h = math.hypot(v[0], v[1])
            el = math.degrees(math.atan2(v[2], h))
            az = math.degrees(math.atan2(v[1], v[0]))
            off = (az - rest + 180) % 360 - 180
            if kind == 'hardpoint':
                cone = math.degrees(math.acos(max(-1, min(1, v[0] / math.sqrt(h * h + v[2] ** 2)))))
                ok = cone <= (MISSILE_CONE if fx[0] == 'missiles' else max(arc / 2, 3))
                miss = cone
            else:
                in_az = abs(off) <= arc / 2 or el >= 75
                ok = in_az and GUN_ELEVATION[0] <= el <= GUN_ELEVATION[1]
                miss = max(0.0, GUN_ELEVATION[0] - el, abs(off) - arc / 2 if el < 75 else 0)
            every.append((ok, miss, kind, el, off, muzzle, fits))
            if best is None or (ok, fits, -miss) > (best[0], best[6], -best[1]):
                best = (ok, miss, kind, el, off, muzzle, fits)
        tgt_el = math.degrees(math.atan2(loc[2], math.hypot(loc[0], loc[1])))
        rows.append((fx[0], a, b, best, tgt_el))
        BEARING_CANDIDATES[(kf,) + tuple(fx[:3])] = sorted([e for e in every if e[0]], key=lambda e: (not e[6], e[1]))
    return rows


BEARING_CANDIDATES = {}     # (kf, kind, shooter, target) -> every mount that bears, launcher-fit first then closest


def main():
    argv = sys.argv[1:]
    if '--check' in argv:
        check(list(KEYFRAMES))
        return

    def opt(flag, default=None):
        return argv[argv.index(flag) + 1] if flag in argv else default

    frames = opt('--frames', ','.join(list(KEYFRAMES) + list(ROSTER_SHEETS))).split(',')
    run = Path(opt('--root', REPO / 'build' / 'asharu-battle')) / opt('--run', date.today().isoformat())
    run.mkdir(parents=True, exist_ok=True)
    started = run / 'run-date.txt'      # the day the run began: a resume after midnight posts under it
    if not started.exists():
        started.write_text(opt('--date', date.today().isoformat()))   # a queue round pins one date
    run_date = started.read_text().strip()
    samples = int(opt('--samples', 48))
    if '--fx-check' in argv:        # per-ship effects: stage the cued frames under the kit, no render
        if '--frames' not in argv:
            frames = [kf for kf in KEYFRAMES if KEYFRAMES[kf].get('cues')]
        argv += ['--stage-only', '--swfx']
    if '--stage-only' in argv:      # the whole Blender scene build for these frames, CPU only, nothing written
        from blender_path import BLENDER
        env = dict(os.environ, ASHARU_CFG=json.dumps(dict(out=str(run), frames=frames, samples=1, stage_only=True,
                                                          los='--los' in argv, swfx='--swfx' in argv,
                                                          fx_check='--fx-check' in argv)))
        subprocess.check_call([str(BLENDER), '--background', '--factory-startup', '--python-exit-code', '1',
                               '--python', __file__], env=env)
        return
    if '--skip-render' not in argv:
        half = '--half' in argv
        todo = [kf for kf in frames if '--rerender' in argv or not finished(run / 'raw', kf, samples, half)]
        done = [kf for kf in frames if kf not in todo]
        if done:
            print('ASHARU already finished, not rendered again:', ','.join(done), flush=True)
        if todo:
            from blender_path import BLENDER
            cfg = dict(out=str(run), frames=todo, samples=samples, half=half, swfx='--swfx' in argv)
            env = dict(os.environ, ASHARU_CFG=json.dumps(cfg))
            subprocess.check_call([str(BLENDER), '--background', '--factory-startup', '--python', __file__], env=env)
    outputs = [orbit_diagram(run)] + label_frames(run)
    print('\n'.join(str(run / o) for o in outputs))
    post = opt('--post')
    if post:
        import shutil
        drive = Path('G:/My Drive/starsector') if os.name == 'nt' else next(
            Path.home().glob('Library/CloudStorage/GoogleDrive-*/My Drive/starsector'))
        dest = drive / 'review' / 'cinematic-asharu-battle' / f"{run_date}_{opt('--what', 'blockout')}_{post}"
        marker = dest / '.asharu-run'         # one run may post in parts (two jobs); another run may not
        if dest.exists() and not (marker.exists() and marker.read_text().strip() == run.name):
            raise SystemExit(f'{dest} exists and belongs to another run')
        dest.mkdir(parents=True, exist_ok=True)
        marker.write_text(run.name)
        for o in outputs:
            shutil.copy2(run / o, dest / o)
        if (run / 'NOTES.md').exists():
            shutil.copy2(run / 'NOTES.md', dest / 'NOTES.md')
        print('POSTED', dest)


if __name__ == '__main__':
    try:
        import bpy  # noqa: F401
    except ImportError:
        bpy = None
    if bpy is not None and os.environ.get('ASHARU_CFG'):
        blender_main(json.loads(os.environ['ASHARU_CFG']))
    else:
        main()
