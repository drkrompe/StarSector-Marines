"""Jangala's place on screen through C1 v2's camera, for the planet lock (runtime-contract.md).

    blender --background --factory-startup --python c1v2_planet_track.py

Writes build/cinematic/c1-corvus/planet-track.json: [[t, cx, cy, r], ...] in 1920x1080 pixels at
OVERLAY_SAMPLE_HZ, from the same `camera_quat` and planet placement `c1v2_scene` renders with
(the camera sits at the origin and only turns). It renders nothing and takes a few seconds.
`compose_c1.overlays()` reads it; `cinematic_export.py --shot c1` needs it. Output, never committed.
"""
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import c1v2_scene as scene  # noqa: E402
import render_planet as planet  # noqa: E402

OUT = HERE.parents[3] / "build" / "cinematic" / "c1-corvus" / "planet-track.json"
OVERLAY_SAMPLE_HZ = 8     # keep equal to compose_c1.OVERLAY_SAMPLE_HZ


def track():
    """[t, cx, cy, r] for every sample where the planet is in front of the camera."""
    alpha = math.atan(planet.DISC_RADIUS_PX / scene.F_PX)     # as c1v2_scene.add_planet places it
    distance = 30000.0
    radius = distance * math.sin(alpha)
    centre = scene.direction_at_px(*planet.DISC_CENTRE_PX) * distance
    out = []
    for i in range(int(scene.DURATION * OVERLAY_SAMPLE_HZ) + 1):
        t = i / OVERLAY_SAMPLE_HZ
        v = scene.camera_quat(t).inverted() @ centre
        depth = -v.z
        if depth <= 0:
            continue
        out.append([round(t, 3), round(scene.W / 2 + scene.F_PX * v.x / depth, 2),
                    round(scene.H / 2 - scene.F_PX * v.y / depth, 2), round(scene.F_PX * radius / depth, 2)])
    return out


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    samples = track()
    OUT.write_text(json.dumps(samples) + "\n", encoding="utf-8")
    print("wrote %s, %d samples" % (OUT, len(samples)))
