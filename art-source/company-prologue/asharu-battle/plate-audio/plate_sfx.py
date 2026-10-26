"""The recording plates' sampled sounds (S0, S13), generated with ElevenLabs.

  python art-source/company-prologue/asharu-battle/plate-audio/plate_sfx.py plan [--cue ID ...]
  python .../plate_sfx.py generate --cue ID [--cue ID ...] [--takes N]
  python .../plate_sfx.py check
  python .../plate_sfx.py pick CUE TAKE

The sounds a terminal's own beeps cannot make: the CRT set switching on and off, its hum, the room
it sits in, the relay's static. The terminal's own sounds (keys, beeps, alerts, the camera moves)
are synthesised against the plate's timeline in plate_audio.py.

The machinery is the Jangala arrival's (second-wind-cinematic/arrival-audio/make_sfx.py): one paid
request per take, never retried; takes and their receipts are sources, kept in `sfx-takes/` beside
this script and listed in `sfx-manifest.json`. Mixes are output and go under build/.
Requires ffmpeg/ffprobe on PATH and ELEVENLABS_API_KEY in the environment (never printed).
"""
import importlib.util
from pathlib import Path

HERE = Path(__file__).resolve().parent

# id: (prompt, seconds, loop, prompt_influence). A bed loops; spots do not.
CUES = {
    "crt_on": (
        "An old CRT television switched on in a quiet room: a sharp relay click, a deep degauss thunk "
        "with a wobbling electrical hum, then a faint high-pitched tube whine settling in, no music",
        4.0, False, 0.5),
    "crt_off": (
        "An old CRT television switched off: a soft electrical pop, a short static crackle and a quick "
        "descending whine fading to silence, no music",
        2.5, False, 0.5),
    "crt_hum": (
        "Steady hum of an old CRT monitor up close: low mains buzz with a very faint high-pitched flyback "
        "whine and soft electrical crackle, constant, no music",
        10.0, True, 0.4),
    "room_bed": (
        "Quiet military operations room at night: low air handling hum, distant computer fans and "
        "electronics, very faint, no voices, no music",
        15.0, True, 0.4),
    # second pass (owner: the set's on and off need another pass): a computer monitor rather than a TV,
    # and a heavier, older set
    "crt_on_monitor": (
        "A 1990s computer CRT monitor powering on: a crisp power switch click, a short low degauss hum "
        "that swells and settles, a faint electrostatic crackle on the glass, close up, no music",
        3.0, False, 0.6),
    "crt_on_heavy": (
        "A heavy old industrial CRT display switching on: a deep relay clunk, a resonant degauss boing "
        "that wobbles and decays, then a quiet steady electrical hum, no music",
        3.5, False, 0.6),
    "crt_off_monitor": (
        "A 1990s computer CRT monitor powering off: a crisp switch click and a quick electrostatic snap "
        "as the picture collapses, then a very faint fading whine, close up, no music",
        2.0, False, 0.6),
    "crt_off_heavy": (
        "A heavy old CRT display switching off: a relay clunk, a low electrical thump and a short "
        "descending tone dying away, no music",
        2.5, False, 0.6),
    "ui_transition": (
        "Short digital computer interface transition: a quick clean electronic sweep with soft data "
        "blips, 1990s science fiction terminal, no whoosh, no music",
        1.5, False, 0.6),
    # round two (owner: answers "more 90s sci-fi movie terminal, a la Starship Troopers"; pings "too
    # happy"; stamps denied)
    "answer_readout": (
        "A 1990s science fiction movie military computer terminal printing a result: a quick burst of "
        "chunky electronic data beeps ending on one firm tone, short, no music",
        1.0, False, 0.7),
    "radar_ping": (
        "A single low ominous military radar contact ping, dark and dry, short decay, no music",
        1.5, False, 0.7),
    "ui_stamp": (
        "A 1990s science fiction movie computer terminal locking a record: a hard low electronic "
        "confirmation tone with a short digital chirp, serious, no music",
        1.2, False, 0.7),
    "relay_static": (
        "Radio receiver static: steady soft hiss with faint crackle and slow interference warble, "
        "narrow band like an old radio, no voices, no music",
        10.0, True, 0.4),
}


def machinery():
    """make_sfx, pointed at this script's cues, takes and manifest."""
    path = HERE.parents[1] / "second-wind-cinematic" / "arrival-audio" / "make_sfx.py"
    spec = importlib.util.spec_from_file_location("make_sfx", path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    m.CUES, m.TAKES, m.MANIFEST = CUES, HERE / "sfx-takes", HERE / "sfx-manifest.json"
    m.BEDS = {cue for cue, spec in CUES.items() if spec[2]}
    return m


if __name__ == "__main__":
    import sys
    m = machinery()
    sys.argv[0] = __file__
    m.main()
