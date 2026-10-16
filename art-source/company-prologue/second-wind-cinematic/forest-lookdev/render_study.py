"""Render the retained forest study with background Blender, without modifying it."""
from pathlib import Path
import argparse
import sys
import bpy

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--frames", nargs="*", type=int, help="Optional selected source frames")
args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else [])
bpy.ops.wm.open_mainfile(filepath=str(HERE / "jangala-forest-lookdev.blend"))
scene = bpy.context.scene
frames = args.frames or list(range(120,192)) + list(range(396,468)) + list(range(801,849))
for texture in bpy.data.images:
    if texture.source == "FILE" and texture.filepath:
        resolved = Path(bpy.path.abspath(texture.filepath))
        if not resolved.is_file():
            raise FileNotFoundError(resolved)
output = HERE / "build" / "frames"
output.mkdir(parents=True, exist_ok=True)
for frame in frames:
    scene.frame_set(frame)
    scene.camera = max((m for m in scene.timeline_markers if m.camera and m.frame <= frame),
                       key=lambda m: m.frame).camera
    scene.render.filepath = str(output / f"frame_{frame:04d}.png")
    bpy.ops.render.render(write_still=True)
    print("STUDY_FRAME", frame, flush=True)
