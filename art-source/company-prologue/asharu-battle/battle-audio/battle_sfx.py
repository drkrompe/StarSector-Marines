"""The Asharu battle's sound effects (S1 to S11), generated with ElevenLabs.

  python art-source/company-prologue/asharu-battle/battle-audio/battle_sfx.py plan [--cue ID ...]
  python .../battle_sfx.py generate --cue ID [--cue ID ...] [--takes N]
  python .../battle_sfx.py check
  python .../battle_sfx.py pick CUE TAKE

The cue sheet is the Battle Cut page's Sound effects tab (its shared "sfx" collection): each cue's
time, bus, level and fades are set there, against the picture. `sfx-cues.json` beside this script is
that sheet's generation half, read back from the page: per cue id the description it is generated
from, its length, whether it loops, and how literally to take the description. Sound direction
(owner, 2026-10-29): the voices and some effects go through the comms effect, as the open mics hear
them; the rest is the reconstruction's own full battle sound.

The machinery is the Jangala arrival's (second-wind-cinematic/arrival-audio/make_sfx.py): one paid
request per take, never retried; takes and their receipts are sources, kept in `sfx-takes/` and
listed in `sfx-manifest.json`. Requires ffmpeg/ffprobe on PATH and ELEVENLABS_API_KEY in the
environment (never printed).
"""
import importlib.util
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def cues():
    """id: (description, seconds, loop, prompt influence), from the cue file."""
    sheet = json.loads((HERE / "sfx-cues.json").read_text(encoding="utf-8"))
    return {cue: (c["prompt"], float(c["seconds"]), bool(c.get("loop")), float(c.get("influence", 0.5)))
            for cue, c in sheet.items()}


def machinery():
    """make_sfx, pointed at this cue sheet, its takes and its manifest."""
    path = HERE.parents[1] / "second-wind-cinematic" / "arrival-audio" / "make_sfx.py"
    spec = importlib.util.spec_from_file_location("make_sfx", path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    m.CUES, m.TAKES, m.MANIFEST = cues(), HERE / "sfx-takes", HERE / "sfx-manifest.json"
    m.BEDS = {cue for cue, spec in m.CUES.items() if spec[2]}
    return m


if __name__ == "__main__":
    import sys
    m = machinery()
    sys.argv[0] = __file__
    m.main()
