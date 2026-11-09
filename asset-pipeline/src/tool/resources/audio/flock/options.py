"""Records the swarm-whine candidates in game, one clip each, for the owner's picks (`owner-review.md`).

    python asset-pipeline/src/tool/resources/audio/flock/options.py

For each voice generate.py writes, the audio manifest's whine is pointed at its loop (processAudio encodes it into
the gitignored mod/sounds), and createSnapshots plays the derelict
ruins fixture's whine clip (DerelictFlocksRuinsLookSuite, -Dsnapshot.flocks.whineOnly) with its soundtrack mixed
(-Dsnapshot.lookSound): a flock rising from its wait into its first strike, gunfire and all. The manifest is put back on
the shipped voice at the end. Writes build/flock-look/whine/<voice>.mp4 and a poster still beside each, and adds the question to
build/flock-look/options.json. Run from the worktree root on Windows.
"""
import json
import os
import shutil
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[5]
OUT = ROOT / "build" / "flock-look" / "whine"
LOOK = ROOT / "build" / "snapshots" / "derelict-flocks-ruins-look" / "look"
MANIFEST = ROOT / "asset-pipeline" / "src" / "tool" / "resources" / "audio" / "audio.json"
JAVA_HOME = r"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr"
RECOMMENDED = "hornet"
VOICES = {
    "hornet": "High fan whine, fourteen tones beating against each other: an insect swarm made of machines. "
              "Unmistakable over gunfire and never confused with an engine.",
    "turbine": "A lower ducted-fan hum under a thin blade whistle: steadier, more an engine than a swarm; sits "
               "with the vehicles' turbines.",
    "buzz": "Open rotors: a rough buzz chopped at the blade rate, angrier and closer to a multirotor; the most "
            "aggressive, and the closest to the Rav's register.",
}


def source(voice):
    """Points the audio manifest's whine at this voice; processAudio (run by -Dsnapshot.lookSound) encodes it."""
    text = MANIFEST.read_text(encoding="utf-8")
    start = text.index('"source": "flock/marines_flock_whine_') + len('"source": "flock/marines_flock_whine_')
    end = text.index('.wav"', start)
    MANIFEST.write_text(text[:start] + voice + text[end:], encoding="utf-8")


def record(voice):
    source(voice)
    env = dict(os.environ, JAVA_HOME=JAVA_HOME)
    subprocess.run(["cmd", "/c", str(ROOT / "gradlew.bat"), "createSnapshots", "-Psnapshot=derelict-flocks-ruins-look",
                    "-q", "-Dsnapshot.flocks.whineOnly=true", "-Dsnapshot.lookSound=true"],
                   cwd=ROOT, env=env, check=True, stdout=subprocess.DEVNULL)
    clip = OUT / f"{voice}.mp4"
    # Under the picks page's 15 MB a file: 20 s near 3.5 Mb/s.
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(LOOK / "flocks-whine-close.mp4"),
                    "-c:v", "libx264", "-b:v", "3500k", "-maxrate", "4000k", "-bufsize", "8000k",
                    "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", str(clip)], check=True)
    shutil.copy(LOOK / "flocks-whine-close-02.png", OUT / f"{voice}.png")
    print(voice, "->", clip, flush=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    try:
        for voice in VOICES:
            record(voice)
    finally:
        source(RECOMMENDED)
    options = [{"key": v, "label": v.capitalize() + (" (recommended)" if v == RECOMMENDED else ""),
                "file": f"{v}.png", "video": f"{v}.mp4", "note": note} for v, note in VOICES.items()]
    question = {"id": "derelict-swarm-whine",
                "question": "Which whine should a derelict flock make? (It rises in pitch as the flock speeds up and "
                            "thins as drones are shot down; play each clip with sound.)",
                "options": options}
    path = ROOT / "build" / "flock-look" / "options.json"
    questions = json.loads(path.read_text()) if path.exists() else []
    questions = [q for q in questions if q["id"] != question["id"]] + [question]
    path.write_text(json.dumps(questions, indent=2))


if __name__ == "__main__":
    main()
