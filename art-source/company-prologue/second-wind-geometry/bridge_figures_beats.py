"""Key-beat sheets of the bridge's two figures through C2, for choosing: a light pass.

    python bridge_figures_beats.py --out DIR

Four moments of each figure's C2 action, each a quarter-frame Workbench panel (480x1080), one
1920x1080 sheet per figure: the pilot from behind (C2's port station), Castle from the port station
turned aft onto her. Eight renders in all, cheap on the shared GPU; the full-motion clip
(bridge_figures_clip.py) is for once the beats are chosen. Never committed.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SW_BLEND = HERE / "states" / "patched" / "second-wind-geometry.blend"
W, H, FPS = 1920, 1080, 24
SHEETS = (   # (sheet, view, (C2 second, what to look at) x 4)
    # The pilot from the port station, behind his left shoulder: the only way C2 sees him.
    ("01-pilot-beats", "16-pilot-from-port-station",
     ((5.0, "5.0 s: ahead, hands at rest"), (8.0, "8.0 s: Legion abeam, he looks to port"),
      (10.0, "10.0 s: pushed for the de-orbit"), (14.7, "14.7 s: let go, watching it climb"))),
    ("02-castle-beats", "09-castle-from-port-station",
     ((5.0, "5.0 s: listening to Approach"), (8.0, "8.0 s: looks out at the Legion"),
      (11.0, "11.0 s: read-back, keyed, braced"), (14.8, "14.8 s: hand down, done"))),
)

LENS = {"16-pilot-from-port-station": "4.0"}   # he is further off from there


def main(argv):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    from PIL import Image, ImageDraw
    out = Path(argv[argv.index("--out") + 1]).resolve()
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for sheet, view, beats in SHEETS[:1] if "--pilot" in argv else SHEETS:
            frames = [round(t * FPS) for t, _ in beats]
            subprocess.check_call([BLENDER, "--background", "--factory-startup", str(SW_BLEND), "--python",
                                   str(HERE / "bridge_interior_review.py"), "--", "--out", tmp, "--figures",
                                   "--views", view, "--frames", ",".join(map(str, frames)),
                                   "--res", "%dx%d" % (W // 4, H), "--lens-scale", LENS.get(view, "1.8")])
            img = Image.new("RGB", (W, H))
            d = ImageDraw.Draw(img)
            for k, (f, (_, label)) in enumerate(zip(frames, beats)):
                img.paste(Image.open(Path(tmp) / ("%s_%04d.png" % (view, f))).convert("RGB"), (k * W // 4, 0))
                d.text((k * W // 4 + 12, H - 26), label, fill=(210, 210, 200))
                if k:
                    d.line([(k * W // 4, 0), (k * W // 4, H)], fill=(60, 60, 60), width=2)
            img.save(out / (sheet + ".png"))
            print("SHEET", sheet, flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
