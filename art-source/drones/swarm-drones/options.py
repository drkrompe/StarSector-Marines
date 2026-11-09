"""Renders the swarm-drone and cradle look candidates in game and composes one picks card per option.

    python art-source/drones/swarm-drones/options.py [--question drone|cradle] [--only <design>]

For each candidate design, pack.py writes it into an overlay folder under build/flock-look/, and createSnapshots plays
the derelict ruins fixture's close shot (DerelictFlocksRuinsLookSuite, -Dsnapshot.flocks.closeOnly) once as Domain
security and once as contingency security, drawing the overlay's art in place of the shipped sprites
(-Dsnapshot.artOverlay). A card is the two stills' middle halves side by side at 100% scale, 1920x1080, Domain left.
The drone question holds the cradle at its recommended design and the cradle question the drone at its.

Writes build/flock-look/cards/<question>-<design>.jpg and build/flock-look/options.json. Run from the worktree root on
Windows (gradlew.bat).
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = ROOT / "build" / "flock-look"
STILL = ROOT / "build" / "snapshots" / "derelict-flocks-ruins-look" / "look" / "flocks-close-close-02.png"
JAVA_HOME = r"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr"
RECOMMENDED = {"drone": "dart", "cradle": "carousel"}

QUESTIONS = {
    "drone": {
        "id": "derelict-swarm-drone",
        "question": "Which swarm drone should derelict flocks fly? (Domain livery left, contingency right; in game, "
                    "a flock of 14 waiting over its cradle)",
        "options": {
            "disc": "One shrouded lift fan with a chin gun: a round, sturdy little machine. Reads as a fan, not a "
                    "heading, so a flock's turns show less.",
            "quad": "An X frame with four open rotors: the cheapest-looking and most familiar drone shape; light "
                    "and busy in a flock.",
            "tandem": "Two fans fore and aft on a spine: a long shape that shows its heading, a little like a "
                      "dragonfly.",
            "dart": "A lozenge body pushed by a tail fan: the nose shows where each drone points, so the flock's "
                    "streaming and wrapping read at a glance.",
        },
    },
    "cradle": {
        "id": "derelict-flock-cradle",
        "question": "Which cradle should house a derelict flock? (Domain livery left, contingency right; in game, "
                    "with its flock of 14 over it)",
        "options": {
            "rack": "A docking rack, two rows of seven cups: says 'fourteen drones live here' at a glance; compact "
                    "for a doorway.",
            "carousel": "A round drum with fourteen hatches round the rim and a mast: the most distinctive "
                        "silhouette, but the largest footprint.",
            "hangar": "A low box with its roof doors open on the docked drones: reads as a building, quieter on "
                      "the map.",
            "block": "A square of docking bays with hatch caps, a few open: blocky and fortified, the most "
                     "'security installation' of the four.",
        },
    },
}
CRADLE_PANEL = {"block": "silo"}


def render(overlay, lineage):
    env = dict(os.environ, JAVA_HOME=JAVA_HOME)
    cmd = ["cmd", "/c", str(ROOT / "gradlew.bat"), "createSnapshots", "-Psnapshot=derelict-flocks-ruins-look", "-q",
           "-Dsnapshot.flocks.closeOnly=true", f"-Dsnapshot.artOverlay={overlay}",
           f"-Dsnapshot.flocks.lineage={lineage}"]
    subprocess.run(cmd, cwd=ROOT, env=env, check=True, stdout=subprocess.DEVNULL)
    return Image.open(STILL).convert("RGB")


def label(img, text):
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 26)
    except OSError:
        font = ImageFont.load_default()
    d.rectangle((16, 16, 32 + d.textlength(text, font=font), 58), fill=(20, 20, 20))
    d.text((24, 21), text, fill=(235, 235, 235), font=font)


def card(question, design):
    drone = design if question == "drone" else RECOMMENDED["drone"]
    cradle = design if question == "cradle" else RECOMMENDED["cradle"]
    overlay = OUT / f"overlay-{drone}-{cradle}"
    subprocess.run([sys.executable, str(HERE / "pack.py"), "--drone", drone,
                    "--cradle", CRADLE_PANEL.get(cradle, cradle), "--root", str(overlay)], check=True,
                   stdout=subprocess.DEVNULL)
    halves = []
    for lineage in ("domain", "contingency"):
        still = render(overlay, lineage)
        still.save(OUT / "stills" / f"{drone}-{cradle}-{lineage}.png")
        half = still.crop((480, 0, 1440, 1080))
        label(half, "Domain security" if lineage == "domain" else "Contingency security")
        halves.append(half)
    out = Image.new("RGB", (1920, 1080))
    out.paste(halves[0], (0, 0))
    out.paste(halves[1], (960, 0))
    ImageDraw.Draw(out).line((960, 0, 960, 1080), fill=(20, 20, 20), width=4)
    path = OUT / "cards" / f"{question}-{design}.jpg"
    out.save(path, quality=90)
    print(question, design, "->", path, flush=True)
    return path


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--question", choices=sorted(QUESTIONS))
    p.add_argument("--only")
    a = p.parse_args()
    (OUT / "cards").mkdir(parents=True, exist_ok=True)
    (OUT / "stills").mkdir(parents=True, exist_ok=True)
    questions = []
    for q, spec in QUESTIONS.items():
        if a.question and q != a.question:
            continue
        options = []
        for design, note in spec["options"].items():
            if a.only and design != a.only:
                continue
            path = card(q, design)
            name = design.capitalize() + (" (recommended)" if design == RECOMMENDED[q] else "")
            options.append({"key": design, "label": name, "file": path.name, "note": note})
        questions.append({"id": spec["id"], "question": spec["question"], "options": options})
    (OUT / "options.json").write_text(json.dumps(questions, indent=2))


if __name__ == "__main__":
    main()
