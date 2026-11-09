"""Renders the derelict gun post and control node look candidates in game and composes one picks card per option.

    python art-source/derelict-security/options.py [--question post|node] [--only <design>]

For each overlay, pack.py writes one post design and one node design into a folder under build/derelict-look/, and
createSnapshots plays the extensive derelict ruins fixture a moment (DerelictSecurityLookSuite) once as Domain security
and once as contingency security, drawing the overlay's art in place of the shipped sprites (-Dsnapshot.artOverlay).
A post card is four 960x540 crops at 100% scale about the post, 1920x1080: Domain on top, contingency below, folded
left and unfolded right. A node card is two 960x1080 crops about the node, Domain left. Crops are centred where the
suite prints the unit falls in its still.

Writes build/derelict-look/cards/<question>-<design>.jpg and build/derelict-look/options.json. Run from the worktree
root on Windows (gradlew.bat).
"""
import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = ROOT / "build" / "derelict-look"
STILLS = ROOT / "build" / "snapshots" / "derelict-security-look"
JAVA_HOME = r"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr"
RECOMMENDED = {"post": "popup", "node": "mast"}
# Each overlay packs one post and one node design; a card shows only its own question's unit.
OVERLAYS = [("popup", "mast"), ("clamshell", "pylon"), ("cupola", "cabinet"), ("popup", "bunker")]

QUESTIONS = {
    "post": {
        "id": "derelict-post-look",
        "question": "Which folding gun post should derelict security stand? (Domain top, contingency bottom; folded "
                    "left, as it waits powered down, unfolded right, as its zone wakes; in game at close zoom)",
        "options": {
            "popup": "A round head that rises out of a hatch-striped well, shut under a six-leaf iris: the plainest "
                     "'something is under that hatch' read, and the head turns like the Vulcan it fires as.",
            "clamshell": "A gun box between two armoured leaves that close over it into a flat plate: folded it "
                         "reads as a floor panel, so it surprises most; open, its leaves widen its shape.",
            "cupola": "A low dome that turns its gun slit down into an octagonal ring: folded it still reads as a "
                      "turret asleep, the least hidden of the three.",
        },
    },
    "node": {
        "id": "derelict-node-look",
        "question": "Which control node should a derelict security zone stand? (Domain left, contingency right; in "
                    "game at close zoom, its repair drone moved aside)",
        "options": {
            "mast": "A square armoured cabinet under a relay mast and dish: says 'relay, controller, unarmed' at a "
                    "glance and cannot be taken for a gun.",
            "pylon": "An octagonal armoured pillar with a sensor dome and a ring of lamps: the most 'machine mind' "
                     "of the four, but its dome reads a little like a turret.",
            "cabinet": "A bank of three armoured relay cabinets with heat fins: reads as installation equipment, the "
                       "widest footprint against its wall.",
            "bunker": "A low rounded armoured dome with antenna sockets and a cable trunk running out: the most "
                      "fortified look; the trunk shows it is wired into the site.",
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
        stills[name] = (Image.open(STILLS / f"{name}.png").convert("RGB"), points.get(name, (960, 540)))
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
        for row, lineage in enumerate(("domain", "contingency")):
            for col, state in enumerate(("folded", "unfolded")):
                tile = crop(renders[lineage][f"derelict-post-{state}"], 960, 540)
                label(tile, f"{'Domain' if lineage == 'domain' else 'Contingency'} security, {state}")
                out.paste(tile, (col * 960, row * 540))
        d.line((0, 540, 1920, 540), fill=(20, 20, 20), width=4)
    else:
        for col, lineage in enumerate(("domain", "contingency")):
            tile = crop(renders[lineage]["derelict-node"], 960, 1080)
            label(tile, "Domain security" if lineage == "domain" else "Contingency security")
            out.paste(tile, (col * 960, 0))
    d.line((960, 0, 960, 1080), fill=(20, 20, 20), width=4)
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
    files = {}
    for post, node in OVERLAYS:
        wanted = [(q, dsg) for q, dsg in (("post", post), ("node", node))
                  if (not a.question or q == a.question) and (not a.only or dsg == a.only)
                  and (q, dsg) not in files]
        if not wanted:
            continue
        overlay = OUT / f"overlay-{post}-{node}"
        subprocess.run([sys.executable, str(HERE / "pack.py"), "--post", post, "--node", node, "--root",
                        str(overlay)], check=True, stdout=subprocess.DEVNULL)
        renders = {lineage: render(overlay, lineage) for lineage in ("domain", "contingency")}
        for q, dsg in wanted:
            files[(q, dsg)] = card(q, dsg, renders)
    questions = []
    for q, spec in QUESTIONS.items():
        options = []
        for design, note in spec["options"].items():
            if (q, design) not in files:
                continue
            name = design.capitalize() + (" (recommended)" if design == RECOMMENDED[q] else "")
            options.append({"key": design, "label": name, "file": files[(q, design)].name, "note": note})
        if options:
            questions.append({"id": spec["id"], "question": spec["question"], "options": options})
    (OUT / "options.json").write_text(json.dumps(questions, indent=2))


if __name__ == "__main__":
    main()
