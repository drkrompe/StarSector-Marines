"""Render the saved greybox with its timeline cameras in background Blender."""
from pathlib import Path
import argparse
import sys
import bpy

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--all", action="store_true")
parser.add_argument("--start", type=int, default=1)
parser.add_argument("--end", type=int, default=780)
args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else [])
scene = bpy.context.scene
markers = sorted((m for m in scene.timeline_markers if m.camera), key=lambda m: m.frame)
if len(markers) != 6:
    raise RuntimeError("Expected the six approved shot cameras in the saved scene")
output = HERE / "build" / "frames"
output.mkdir(parents=True, exist_ok=True)
for frame in range(args.start, args.end + 1):
    scene.frame_set(frame)
    scene.camera = max((m for m in markers if m.frame <= frame), key=lambda m: m.frame).camera
    scene.render.filepath = str(output / f"frame_{frame:04d}.png")
    bpy.ops.render.render(write_still=True)
    if frame % 60 == 0:
        print(f"ANIMATIC_RENDER {frame}/780", flush=True)
print("ANIMATIC_RENDER_COMPLETE", flush=True)
