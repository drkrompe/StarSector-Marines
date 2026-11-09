"""Renders the second round's derelict security look candidates in game and composes one picks card per option.

    python art-source/derelict-security/options-v2.py

For each node design, pack-v2.py writes an overlay from the v2b sheets (painted after Starsector's own derelict ship
sprites) under build/derelict-look-v2/ (the cabinet's carries the restyled
pop-up post as well), and createSnapshots plays the extensive derelict ruins fixture a moment
(DerelictSecurityLookSuite) once as Domain security and once as contingency security, drawing the overlay's art in
place of the shipped sprites (-Dsnapshot.artOverlay). The suite stands a company Hound beside each post and node, so
every card shows the candidate next to a shipped mech. A node card is two 960x1080 crops, Domain left; a post card is
four 960x540 crops, Domain on top, folded left. Crops are centred between where the suite prints the unit and its
Hound fall, so both are in frame. The current post's card comes from an overlay without the post, so it draws the
shipped sprites.

Writes build/derelict-look-v2/cards/<question>-<design>.jpg and build/derelict-look-v2/options.json. Run from the
worktree root on Windows (gradlew.bat).
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = ROOT / "build" / "derelict-look-v2"
STILLS = ROOT / "build" / "snapshots" / "derelict-security-look"
JAVA_HOME = r"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr"
LINEAGES = ("domain", "contingency")
NAMES = {"domain": "Domain security", "contingency": "Contingency security"}
# node design -> whether its overlay carries the restyled post.
OVERLAYS = {"cabinet": True, "array": False, "junction": False}
RECOMMENDED = {"node": "cabinet", "post": "restyle"}

QUESTIONS = {
    "node": {
        "id": "derelict-node-look",
        "question": "Which control node should a derelict security zone stand? Redrawn after Starsector's own "
                    "derelict ships (Domain left, contingency right; in game at close zoom beside a company Hound for scale and style)",
        "options": {
            "cabinet": "An armoured relay cabinet: a louvred vent door, a door of status lamps, a hazard band and "
                       "whip antennas folded along its spine. The plainest 'comms box, not a gun' read, and it "
                       "sits against a wall like a real installation.",
            "array": "A flat phased-array uplink: a tiled antenna face between two radiator fin banks. Reads as "
                     "signal gear, but from above its tiled face can pass for a crate or a floor plate.",
            "junction": "A cable junction: three capped relay canisters on a hazard-edged skid, joined by cable "
                        "bundles. The most 'wired into the site' look; its round caps are the closest of the three "
                        "to a turret ring.",
        },
    },
    "post": {
        "id": "derelict-post-restyle",
        "question": "The picked pop-up post, redrawn after Starsector's own derelict ships: keep the shipped one or "
                    "take the restyle? (Domain top, contingency bottom; folded left, unfolded right; beside a company Hound)",
        "options": {
            "restyle": "The same post and geometry in the derelict ships' look: sage-green and khaki "
                       "plates, a yellow hazard diagonal, brass and rust greebles, a red eye, no eagle emblem. "
                       "Matches the new nodes.",
            "current": "The shipped post as it is: photo-real riveted steel with an eagle emblem on its base. "
                       "Stays in game unless the restyle is picked.",
        },
    },
}


def render(overlay, lineage):
    env = dict(os.environ, JAVA_HOME=JAVA_HOME)
    cmd = ["cmd", "/c", str(ROOT / "gradlew.bat"), "createSnapshots", "-Psnapshot=derelict-security-look",
           f"-Dsnapshot.artOverlay={overlay}", f"-Dsnapshot.derelict.lineage={lineage}"]
    run = subprocess.run(cmd, cwd=ROOT, env=env, check=True, capture_output=True, text=True)
    points = {m.group(1): (int(m.group(2)), int(m.group(3)))
              for m in re.finditer(r"\[look/derelict\] (\S+) at (-?\d+),(-?\d+)", run.stdout)}
    stills = {}
    for name in ("derelict-post-folded", "derelict-post-unfolded", "derelict-node"):
        x, y = points.get(name, (960, 540))
        hx, hy = points.get(name + "-hound", (x, y))
        stills[name] = (Image.open(STILLS / f"{name}.png").convert("RGB"), ((x + hx) // 2, (y + hy) // 2))
    return stills


def crop(still, w, h):
    img, (x, y) = still
    x0 = min(max(0, x - w // 2), img.width - w)
    y0 = min(max(0, y - h // 2), img.height - h)
    return img.crop((x0, y0, x0 + w, y0 + h))


def label(img, text):
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 26)
    except OSError:
        font = ImageFont.load_default()
    d.rectangle((16, 16, 32 + d.textlength(text, font=font), 58), fill=(20, 20, 20))
    d.text((24, 21), text, fill=(235, 235, 235), font=font)


def card(question, design, renders):
    out = Image.new("RGB", (1920, 1080))
    d = ImageDraw.Draw(out)
    if question == "post":
        for row, lineage in enumerate(LINEAGES):
            for col, state in enumerate(("folded", "unfolded")):
                tile = crop(renders[lineage][f"derelict-post-{state}"], 960, 540)
                label(tile, f"{NAMES[lineage]}, {state}, beside a Hound")
                out.paste(tile, (col * 960, row * 540))
        d.line((0, 540, 1920, 540), fill=(20, 20, 20), width=4)
    else:
        for col, lineage in enumerate(LINEAGES):
            tile = crop(renders[lineage]["derelict-node"], 960, 1080)
            label(tile, f"{NAMES[lineage]}, beside a company Hound")
            out.paste(tile, (col * 960, 0))
    d.line((960, 0, 960, 1080), fill=(20, 20, 20), width=4)
    path = OUT / "cards" / f"{question}-{design}.jpg"
    out.save(path, quality=92)
    print(question, design, "->", path, flush=True)
    return path


def main():
    (OUT / "cards").mkdir(parents=True, exist_ok=True)
    files = {}
    for node, post in OVERLAYS.items():
        overlay = OUT / f"overlay-{node}"
        args = [sys.executable, str(HERE / "pack-v2.py"), "--node", node, "--root", str(overlay)]
        subprocess.run(args + (["--post"] if post else []), check=True, stdout=subprocess.DEVNULL)
        renders = {lineage: render(overlay, lineage) for lineage in LINEAGES}
        files[("node", node)] = card("node", node, renders)
        if post:
            files[("post", "restyle")] = card("post", "restyle", renders)
        elif ("post", "current") not in files:
            files[("post", "current")] = card("post", "current", renders)
    questions = []
    for q, spec in QUESTIONS.items():
        options = []
        for design, note in spec["options"].items():
            name = design.capitalize() + (" (recommended)" if design == RECOMMENDED[q] else "")
            options.append({"key": design, "label": name, "file": files[(q, design)].name, "note": note})
        questions.append({"id": spec["id"], "question": spec["question"], "options": options})
    (OUT / "options.json").write_text(json.dumps(questions, indent=2))


if __name__ == "__main__":
    main()
