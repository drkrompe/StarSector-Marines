"""Cut the heart's origin props mechanically: one tileset per kit, every frame its guide slot scaled to the raw.

Reads `geometry.json` (written by make_guide.py) and each accepted raw `art-source/tilesets/origin-<kit>.raw.png`,
writes `art-source/tilesets/origin-<kit>.tileset-authoring.json` with one entry per prop and state, and exports it
through the authoring tools (`tileset_export`), which crops each full placement frame and resamples it to 128 px a
cell. No pixel is painted here: the raw's own alpha is kept, and a frame is the guide's rect, never a trimmed box.

State ids (`heart-origins.md`): grown is the prop's own id, the state the heart places; `-damaged` and `-destroyed`
are its battle-damage pair (`PropDamage`); `-clean` is the thing before the bloom and `-swallowed` the telling
object under its thickest growth, both static.

usage: python art-source/heart-origins/export.py [kit ...]   (default: every kit with a raw)
"""
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
TILESETS = ROOT / "art-source" / "tilesets"
SUFFIX = {"grown": "", "clean": "-clean", "swallowed": "-swallowed", "damaged": "-damaged", "destroyed": "-destroyed"}

# cover and ballistic half height while standing; every destroyed state releases both
COVER = {
    "origin-workings.shaft": ("heavy", 0.6), "origin-workings.pump-shed": ("heavy", 1.1),
    "origin-workings.pipe-run": ("med", 0.4), "origin-workings.pipe-elbow": ("med", 0.4),
    "origin-workings.pipe-mouth": ("med", 0.4),
    "origin-freight.split-container": ("heavy", 1.1), "origin-freight.container-rust": ("heavy", 1.1),
    "origin-freight.container-blue": ("heavy", 1.1), "origin-freight.seafood-crates": ("med", 0.5),
    "origin-freight.gantry-span": ("none", 0.0),
    "origin-freight.gantry-leg": ("heavy", 1.1), "origin-freight.unloading-truck": ("heavy", 0.55),
    "origin-lab.annex": ("heavy", 0.6), "origin-lab.tank-rack": ("med", 0.5), "origin-lab.sample-freezer": ("med", 0.4),
    "origin-park.bandstand": ("heavy", 1.1), "origin-park.pond": ("light", 0.2), "origin-park.swings": ("light", 0.3), "origin-park.slide": ("light", 0.3),
    "origin-park.climber": ("light", 0.3), "origin-park.roundabout": ("light", 0.2), "origin-park.bench": ("light", 0.25),
}
NOTES = {
    "workings-shaft": "The reservoir workings' capped shaft, burst from below: the workings origin's telling object.",
    "workings-yard": "The workings' pump shed and the pipeline (run, elbow, and the mouth where it goes into the ground).",
    "freight-split": "A refrigerated container split open from inside: the freight yard origin's telling object.",
    "freight-boxes": "Ordinary 40-foot shipping containers, rust-red and steel-blue, for the freight yard's stack, "
                     "and a pallet of seafood crates (burst and empty once grown).",
    "freight-crane": "The freight yard's gantry crane (an overhead span that stands over walkable ground on two legs) "
                     "and a flatbed stopped mid-unload.",
    "lab-annex": "A sealed laboratory annex blown outward from inside: the research site origin's telling object.",
    "lab-kit": "The research site's specimen tank rack (water tanks, a crustacean in each) and sample freezer, "
               "broken open from inside and empty once grown.",
    "park-bandstand": "An octagonal park bandstand, half swallowed: the park origin's telling object.",
    "park-pond": "The park's pond, drained and torn open from below: the park origin's other telling object.",
    "park-play": "The park's swings, slide, climbing frame, roundabout and bench.",
}


def document(kit, spec):
    raw_path = TILESETS / f"origin-{kit}.raw.png"
    raw = Image.open(raw_path)
    gw, gh = spec["size"]
    sx, sy = raw.width / gw, raw.height / gh
    entries = []
    for e in spec["entries"]:
        x, y, w, h = e["rect"]
        state = e["state"]
        cover, half = COVER[e["id"]] if state != "destroyed" else ("none", 0.0)
        entries.append({"rect": [round(x * sx), round(y * sy), round(w * sx), round(h * sy)],
                        "id": e["id"] + SUFFIX[state], "cover": cover, "ballisticHalfHeight": half,
                        "footprintCells": e["footprintCells"], "included": True})
    note = (f"{NOTES[kit]} Geometry-first ImageGen reskin of art-source/templates/heart-{kit}-guide.png "
            f"(art-source/heart-origins/make_guide.py); the raw came back {raw.width}x{raw.height} against a "
            f"{gw}x{gh} guide and every full placement frame is the guide slot scaled by that ratio, native alpha "
            f"kept. Brief: art-source/heart-origins/prompts/common.txt + {kit}.txt. Produced only by "
            f"art-source/heart-origins/export.py; see heart-origins.md.")
    return {"sheetName": f"origin-{kit}", "sheet": f"art-source/tilesets/origin-{kit}.raw.png", "cellPx": 128,
            "gridCols": 1, "gridRows": 1, "idPrefix": f"origin-{kit}", "alphaMin": 40,
            "outputSheet": f"graphics/doodads/origin-{kit}.png", "note": note, "entries": entries}


def main(kits):
    geometry = json.loads((HERE / "geometry.json").read_text())
    for kit, spec in geometry["kits"].items():
        if kits and kit not in kits:
            continue
        if not (TILESETS / f"origin-{kit}.raw.png").exists():
            print("no raw yet:", kit)
            continue
        doc = document(kit, spec)
        (TILESETS / f"origin-{kit}.tileset-authoring.json").write_text(json.dumps(doc, indent=1) + "\n")
        tool = ROOT / "tools" / ("authoring.cmd" if sys.platform == "win32" else "authoring.sh")
        subprocess.run([str(tool), "tileset_export", "-"], input=json.dumps({"name": f"origin-{kit}"}),
                       text=True, check=True, cwd=ROOT)
        print("exported", kit, len(doc["entries"]), "frames")


if __name__ == "__main__":
    main(sys.argv[1:])
