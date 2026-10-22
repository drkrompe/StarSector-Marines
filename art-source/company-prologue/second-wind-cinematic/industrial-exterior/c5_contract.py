"""C5's runtime-contract data (`runtime-contract.md`): what `cinematic_export.py` reads for the shot.

`c5_scene.py` builds the picture in Blender and imports bpy, so the exporter cannot import it;
the hooks live here instead, in plain Python, with the shot's timing copied from the scene and
from `c5_title.py` (which lays the title on the frames the exporter encodes).

Sound is a stand-in from the yard sounds already shipped until the arrival mix lands
(`arrival-audio/sfx-cues.md`, "C4-C5"). The jungle bed carries the whole shot, continuing from
C4, and goes out with the fade to black. The yard's spot sounds sit in the close part, while
the camera is 220 m over the ramp (0-18 s); the steam thins away under the rise. No boots: from
220 m up there are no footsteps (owner, 2026-10-19). The C5 tail under the title is left to the
music (Ending A), which is the open final-mix item.
"""
import sys
from pathlib import Path

sys.path.insert(1, str(Path(__file__).resolve().parent.parent / "arrival-audio"))
import castle_lines  # noqa: E402

DURATION = 40.0                # keep equal to c5_scene.DURATION: 960 frames
RISE = (18.0, 26.0)            # c5_scene.RISE: the camera goes straight up
FADE_OUT = (38.0, 39.5)        # c5_title: the title's fade to black, on C5's clock


def duration():
    return DURATION


def captions():
    """Castle's all-hands over the loudspeaker, then her last line to the Commander (castle_lines.py)."""
    return castle_lines.captions("c5")


def overlays():
    return []


def audio_cues():
    """(t, sound_id, volume[, until, fade_in, fade_out]). Sound ids are built by processAudio from
    audio.json and registered in mod/data/config/sounds.json."""
    fade = FADE_OUT[1] - FADE_OUT[0]
    return [
        (0.0, "c5_yard_jungle_bed", 0.40, FADE_OUT[1], 0.0, fade),      # from C4, out with the black
        (0.0, "c5_yard_spool_down", 0.55),                              # the engines going dark
        (0.4, "c5_yard_hull_ticks", 0.50),
        (1.0, "c5_yard_winch", 0.22),
        (1.7, "c5_yard_radio_chatter", 0.20),
        (3.2, "c5_yard_crane_horn", 0.22),
        (4.0, "c5_yard_steam_vent", 0.45, RISE[0] + 4.0, 0.4, 6.0),     # vents as the engines go dark, thins under the rise
        (5.0, "c5_yard_winch", 0.22),
        (10.0, "c5_yard_hull_ticks", 0.35),                             # still cooling, quieter
        (13.0, "c5_yard_winch", 0.18),
        (14.5, "c5_yard_radio_chatter", 0.16),
    ] + [(c["t"], c["sound"], c["volume"]) for c in castle_lines.cues("c5")]
