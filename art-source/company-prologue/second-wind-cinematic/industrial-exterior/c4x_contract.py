"""C4's runtime-contract data (`runtime-contract.md`): what `cinematic_export.py` reads for the shot.

`c4x_scene.py` builds the picture in Blender and imports bpy, so the exporter cannot import it;
the hooks live here instead, in plain Python, with the shot's timing copied from the scene.

Sound is a stand-in from the sounds already shipped until the arrival mix lands
(`arrival-audio/sfx-cues.md`, "C4-C5"): C3's cabin carried over the cut and out within a
second as the picture goes outside, the exterior wind for the high pan and the descent,
Castle's yard line over the intercom, and the yard's jungle bed coming up as the camera
comes down over Kilo Nine. C5 picks the bed up on its first frame.
"""
import sys
from pathlib import Path

sys.path.insert(1, str(Path(__file__).resolve().parent.parent / "arrival-audio"))
import castle_lines  # noqa: E402

FPS = 24
LATE = 5.4                     # keep equal to c4x_scene.LATE
DURATION = 16.0 + LATE         # c4x_scene.DURATION: 514 frames
YARD_BED_IN = 17.4             # sfx-cues.md: the yard bed comes in as the ship settles on Kilo Nine


def duration():
    return DURATION


def captions():
    """Castle's yard line over its whole take (castle_lines.py); the exporter keys the id."""
    return castle_lines.captions("c4")


def overlays():
    return []


def audio_cues():
    """(t, sound_id, volume[, until, fade_in, fade_out])."""
    return [
        (0.0, "jangala_arrival_cabin_bed", 0.30, 1.0, 0.0, 0.9),   # C3's cabin, out as the picture goes outside
        (0.0, "jangala_arrival_wind", 0.55),                       # open air at altitude over the pan (12.5 s)
        (10.5, "jangala_arrival_wind", 0.35),                      # a second, quieter pass under the descent
        (YARD_BED_IN, "c5_yard_jungle_bed", 0.40, DURATION, 2.0, 0.0),
    ] + [(c["t"], c["sound"], c["volume"]) for c in castle_lines.cues("c4")]
