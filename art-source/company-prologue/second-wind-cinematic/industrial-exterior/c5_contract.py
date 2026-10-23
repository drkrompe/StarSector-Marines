"""C5's runtime-contract data (`runtime-contract.md`): what `cinematic_export.py` reads for the shot.

`c5_scene.py` builds the picture in Blender and imports bpy, so the exporter cannot import it;
the hooks live here instead, in plain Python, with the shot's timing copied from the scene and
from `c5_title.py` (which lays the title on the frames the exporter encodes).

Sound: Castle's two lines here; every effect is the cue sheet's (`arrival-audio/sfx-cues.md`,
"C4-C5"), rendered by `arrival-audio/build_arrival_stems.py` and added by the exporter, and the
music is `arrival-audio/arrival-mix.json`'s.
"""
import sys
from pathlib import Path

sys.path.insert(1, str(Path(__file__).resolve().parent.parent / "arrival-audio"))
import castle_lines  # noqa: E402

DURATION = 40.0                # keep equal to c5_scene.DURATION: 960 frames


def duration():
    return DURATION


def captions():
    """Castle's all-hands over the loudspeaker, then her last line to the Commander (castle_lines.py)."""
    return castle_lines.captions("c5")


def overlays():
    return []


def audio_cues():
    """(t, sound_id, volume): Castle's all-hands over the loudspeaker and her last line."""
    return [(c["t"], c["sound"], c["volume"]) for c in castle_lines.cues("c5")]
