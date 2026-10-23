"""C4's runtime-contract data (`runtime-contract.md`): what `cinematic_export.py` reads for the shot.

`c4x_scene.py` builds the picture in Blender and imports bpy, so the exporter cannot import it;
the hooks live here instead, in plain Python, with the shot's timing copied from the scene.

Sound: Castle's yard line here; every effect is the cue sheet's (`arrival-audio/sfx-cues.md`,
"C4-C5"), rendered by `arrival-audio/build_arrival_stems.py` and added by the exporter.
"""
import sys
from pathlib import Path

sys.path.insert(1, str(Path(__file__).resolve().parent.parent / "arrival-audio"))
import castle_lines  # noqa: E402

FPS = 24
LATE = 5.4                     # keep equal to c4x_scene.LATE
DURATION = 16.0 + LATE         # c4x_scene.DURATION: 514 frames


def duration():
    return DURATION


def captions():
    """Castle's yard line over its whole take (castle_lines.py); the exporter keys the id."""
    return castle_lines.captions("c4")


def overlays():
    return []


def audio_cues():
    """(t, sound_id, volume): Castle's yard line over the intercom."""
    return [(c["t"], c["sound"], c["volume"]) for c in castle_lines.cues("c4")]
