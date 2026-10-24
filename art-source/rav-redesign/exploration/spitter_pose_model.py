"""A 3D pose blockout of the Spitter, rendered from straight above as an ImageGen guide, with side and three-quarter
views to check the pose.

  python spitter_pose_model.py <out dir>

The body is a tube swept along a 3D spine (a chain of spheres along a Catmull-Rom curve, each sphere's radius from the
girth profile), posed like a hydralisk: the body rises out of its tail, leans back over the haunch, and the neck curves
forward so the head looks out ahead. The tail lies on the ground, curled tight round the base standing (pose-top.png)
and trailing straight back in an S swimming (pose-swim.png), the same length in both. The layer guides render each
part alone in the same frame (part-body.png, part-head.png, part-tail.png, the tail straight). Rendered with a depth
buffer and plain grey Lambert shading on the project's flat grey; the top view is a 1024x1536 canvas facing up.
Units are canvas px (760 to a cell): x right, y forward (up the top view), z up.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

# the body's spine from the base it rises from to the snout: (x, y, z, radius)
BODY = [
    (0, -40, 80, 95),         # the base, where the tail leaves it
    (0, -90, 230, 92),        # the haunch, leaning back
    (0, -70, 400, 86),
    (0, 10, 540, 76),         # the chest, coming forward
    (0, 100, 620, 50),        # the neck
    (0, 190, 640, 62),        # the head
    (0, 290, 615, 14),        # the snout
]
# the tail, the same length in both poses: its length, girth at the base and the tip
TAIL_LENGTH, TAIL_BASE_R, TAIL_TIP_R = 800.0, 92.0, 6.0
# standing, it curls tight round the base this far (degrees), leaving it heading back and to the right
CURL_TURN, CURL_START = 280.0, 125.0
# swimming, it trails straight back in an S: the wave's swing (degrees) and its wavelength along the tail
SWIM_SWING, SWIM_WAVE = 28.0, 520.0


def chain(points, n=60, tint=1.0):
    """Spheres along a straight run of (x, y, z, radius) points, for the head's features."""
    out = []
    for a, b in zip(points, points[1:]):
        for t in np.linspace(0, 1, n, endpoint=False):
            out.append(tuple(np.add(a, np.multiply(np.subtract(b, a), t))) + (tint,))
    return out


def head(flare=1.0):
    """The head's features over the skull: a heavy brow, a hydralisk crest of two blades swept back over the neck with
    a ridge between them, and dark eyes, so the guide says which way it looks and where its face is. flare spreads the
    crest blades."""
    parts = []
    for side in (-1, 1):
        parts += chain([(side * 44, 222, 690, 16), (side * 52, 180, 692, 16)])           # the brow
        parts += chain([(side * 50, 185, 696, 18), (side * 100 * flare, 105, 676, 14),
                        (side * 140 * flare, 25 + 40 * (flare - 1), 640, 6)], tint=1.12)  # the crest blades
        parts += chain([(side * 36, 238, 694, 13), (side * 36, 234, 694, 13)], n=4, tint=0.22)   # the eyes
    parts += chain([(0, 275, 668, 10), (0, 190, 706, 13), (0, 60, 676, 8)], tint=1.12)    # the ridge
    return parts


# spitting, the head rears back on the neck, snout up toward the camera, and the lower jaw drops forward
NECK_PIVOT = (100.0, 620.0)     # (y, z) the head pitches about
SPIT_PITCH, SPIT_FLARE = 28.0, 1.35


def spitting_head(skull):
    """The head as it spits: skull and features pitched back about the neck, the crest flared, the lower jaw hanging
    open forward of the snout with the dark mouth between them, which is all of the mouth the camera above can see."""
    a = np.radians(SPIT_PITCH)
    py, pz = NECK_PIVOT

    def pitch(sphere):
        x, y, z = sphere[:3]
        dy, dz = y - py, z - pz
        return (x, py + dy * np.cos(a) - dz * np.sin(a), pz + dy * np.sin(a) + dz * np.cos(a)) + tuple(sphere[3:])

    parts = [pitch(sph) for sph in list(skull) + head(SPIT_FLARE)]
    jaw = []
    for side in (-1, 1):
        jaw += chain([(side * 34, 170, 600, 24), (side * 26, 280, 560, 14), (side * 10, 320, 545, 8)])   # jaw sides
    mouth = chain([(0, 200, 600, 30), (0, 290, 575, 18)], tint=0.3)                                   # the open mouth
    return jaw + mouth + parts


def tail(curled, n=600, straight=False):
    """The tail's spheres from the base back to the tip, curled round the base, trailing in an S, or straight back."""
    x, y = BODY[0][0], BODY[0][1]
    ds = TAIL_LENGTH / n
    heading = np.radians(CURL_START if curled else 180.0)   # degrees counter-clockwise from forward
    out = []
    for i in range(n):
        t = i / (n - 1)
        r = TAIL_BASE_R + (TAIL_TIP_R - TAIL_BASE_R) * t
        z = r + (BODY[0][2] - TAIL_BASE_R) * max(0.0, 1 - t / 0.15)   # down from the base onto the ground
        out.append((x, y, z, r))
        if curled:
            heading += np.radians(CURL_TURN) / n
            step = heading
        else:
            swing = 0.0 if straight else np.radians(SWIM_SWING) * np.sin(2 * np.pi * i * ds / SWIM_WAVE)
            step = heading + swing
        x += -np.sin(step) * ds
        y += np.cos(step) * ds
    return out


BACKGROUND = 175.0
LIGHT = np.array([-0.45, 0.35, 0.82])
LIGHT /= np.linalg.norm(LIGHT)


SPLINE_STEPS = 200          # catmull samples per spine span
NECK_POINT = 4              # the BODY point where the head's layer begins


def catmull(points, per=SPLINE_STEPS):
    p = np.asarray(points, dtype=float)
    p = np.vstack([p[0], p, p[-1]])
    out = []
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = p[i - 1], p[i], p[i + 1], p[i + 2]
        for t in np.linspace(0, 1, per, endpoint=False):
            t2, t3 = t * t, t * t * t
            out.append(0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                              + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    out.append(p[-2])
    return np.array(out)


def render(spheres, axes, size, centre, scale):
    """Orthographic render: axes picks (screen right, screen up, toward camera) as rows of a 3x3 basis."""
    w, h = size
    img = np.full((h, w), BACKGROUND)
    depth = np.full((h, w), -1e9)
    basis = np.asarray(axes, dtype=float)
    yy, xx = np.mgrid[0:h, 0:w]
    for sphere in spheres:
        x, y, z, r = sphere[:4]
        tint = sphere[4] if len(sphere) > 4 else 1.0
        c = basis @ np.array([x, y, z])
        sx, sy, sz, sr = centre[0] + c[0] * scale, centre[1] - c[1] * scale, c[2], r * scale
        x0, x1 = int(max(0, sx - sr - 1)), int(min(w, sx + sr + 2))
        y0, y1 = int(max(0, sy - sr - 1)), int(min(h, sy + sr + 2))
        if x0 >= x1 or y0 >= y1:
            continue
        dx = (xx[y0:y1, x0:x1] - sx) / sr
        dy = (sy - yy[y0:y1, x0:x1]) / sr
        d2 = dx * dx + dy * dy
        inside = d2 <= 1
        dz = np.sqrt(np.clip(1 - d2, 0, 1))
        zz = sz + dz * r
        win = inside & (zz > depth[y0:y1, x0:x1])
        normal_view = np.stack([dx, dy, dz], -1)
        normal_world = normal_view @ basis          # back to world axes for the light
        shade = 0.22 + 0.78 * np.clip(normal_world @ LIGHT, 0, 1)
        world_z = z + (normal_world[..., 2] * r)
        lift = 0.55 + 0.45 * np.clip(world_z / 660.0, 0, 1)   # higher is lighter: the head nearest the camera
        img[y0:y1, x0:x1][win] = ((40 + 200 * shade * lift) * tint)[win]
        depth[y0:y1, x0:x1][win] = zz[win]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    body = list(catmull(BODY)) + head()
    spheres = tail(True)[::-1] + body
    swimming = tail(False)[::-1] + body
    top = render(spheres, [(1, 0, 0), (0, 1, 0), (0, 0, 1)], (1024, 1536), (512, 560), 1.0)
    render(swimming, [(1, 0, 0), (0, 1, 0), (0, 0, 1)], (1024, 1536), (512, 560), 1.0).save(out / "pose-swim.png")
    top.save(out / "pose-top.png")
    side = render(spheres, [(0, 1, 0), (0, 0, 1), (1, 0, 0)], (1024, 1024), (560, 900), 1.0)
    a = np.radians(35)
    e = np.radians(30)
    right = np.array([np.cos(a), np.sin(a), 0])
    forward = np.array([-np.sin(a), np.cos(a), 0])
    up = np.cos(e) * np.array([0, 0, 1]) - np.sin(e) * forward
    toward = np.cross(right, up)
    three = render(spheres, [right, up, toward], (1024, 1024), (512, 760), 0.95)
    sheet = Image.new("L", (3072, 1536), int(BACKGROUND))
    sheet.paste(top, (0, 0))
    sheet.paste(side, (1024, 256))
    sheet.paste(three, (2048, 256))
    sheet.save(out / "pose-views.png")
    # the layer guides, each part alone in the same frame: the body from its base to the neck, the head from the neck
    # on, and the tail straight back from the base, so the rig can curl and swing it
    spine = list(catmull(BODY))
    neck = NECK_POINT * SPLINE_STEPS
    top_view = [(1, 0, 0), (0, 1, 0), (0, 0, 1)]
    render(spine[:neck + 20], top_view, (1024, 1536), (512, 560), 1.0).save(out / "part-body.png")
    render(spine[neck - 20:] + head(), top_view, (1024, 1536), (512, 560), 1.0).save(out / "part-head.png")
    render(spitting_head(spine[neck - 20:]), top_view, (1024, 1536), (512, 560), 1.0).save(out / "part-head-spit.png")
    side_view = [(0, 1, 0), (0, 0, 1), (1, 0, 0)]
    render(spitting_head(spine[neck - 20:]) + spine[:neck], side_view, (1024, 1024), (560, 900), 1.0).save(
        out / "part-head-spit-side.png")
    render(tail(False, straight=True)[::-1], top_view, (1024, 1536), (512, 560), 1.0).save(out / "part-tail.png")


if __name__ == "__main__":
    main()
