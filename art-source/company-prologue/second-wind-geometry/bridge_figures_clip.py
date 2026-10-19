"""A check clip of the bridge's two figures through C2: the pilot and Castle side by side.

    python bridge_figures_clip.py --out PATH.mp4 [--half] [--from S] [--to S]

Renders the patched bridge with its figures (sw_bridge_interior.figures) through
bridge_interior_review.py at two views, each a half-width panel: the pilot from C2's forward
station, Castle from the port station turned aft onto her. Every frame from --from to --to
C2 seconds (default 0 to 15) at 24 fps, joined left and right into 1920x1080 frames with the
C2 time burnt in, then encoded with ffmpeg. --half renders at 50% and scales up (a check pass
on the shared GPU). Workbench, never committed.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SW_BLEND = HERE / "states" / "patched" / "second-wind-geometry.blend"
VIEWS = ("15-pilot-from-forward-station", "09-castle-from-port-station")
W, H, FPS = 1920, 1080, 24


def main(argv):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    from PIL import Image, ImageDraw
    out = Path(argv[argv.index("--out") + 1]).resolve()
    t0 = float(argv[argv.index("--from") + 1]) if "--from" in argv else 0.0
    t1 = float(argv[argv.index("--to") + 1]) if "--to" in argv else 15.0
    f0, f1 = round(t0 * FPS), round(t1 * FPS)
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.check_call([BLENDER, "--background", "--factory-startup", str(SW_BLEND), "--python",
                               str(HERE / "bridge_interior_review.py"), "--", "--out", tmp, "--figures",
                               "--views", ",".join(VIEWS), "--frames", "%d:%d" % (f0, f1),
                               "--res", "%dx%d" % (W // 2, H), "--percent", "50" if "--half" in argv else "100",
                               "--lens-scale", "0.7"])
        for f in range(f0, f1 + 1):
            img = Image.new("RGB", (W, H))
            for k, v in enumerate(VIEWS):
                img.paste(Image.open(Path(tmp) / ("%s_%04d.png" % (v, f))).convert("RGB")
                          .resize((W // 2, H), Image.LANCZOS), (k * W // 2, 0))
            d = ImageDraw.Draw(img)
            d.line([(W // 2, 0), (W // 2, H)], fill=(60, 60, 60), width=2)
            d.text((16, H - 30), "C2 %.2f s" % (f / FPS), fill=(200, 200, 190))
            img.save(Path(tmp) / ("clip_%04d.png" % f))
        out.parent.mkdir(parents=True, exist_ok=True)
        subprocess.check_call(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", str(FPS),
                               "-start_number", str(f0), "-i", str(Path(tmp) / "clip_%04d.png"),
                               "-pix_fmt", "yuv420p", "-c:v", "libx264", "-crf", "20", str(out)])
    print("CLIP", out, flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
