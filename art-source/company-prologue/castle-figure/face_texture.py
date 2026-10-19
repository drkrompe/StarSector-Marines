"""Repaint Castle's face skin with Codex ImageGen, two ways, and measure where features land.

    python face_texture.py guides            # writes face-texture/guides/ from castle-figure.blend
    python face_texture.py measure           # landmark drift of each generation against its guide
    python face_texture.py bake              # skin textures and .blends for both arms (build/)
    python face_texture.py compare           # each skin on her model beside her portrait (build/)

The question (owner, 2026-10-19) is whether ImageGen can skin her face better painting straight
into MakeHuman's UV layout or repainting renders that are projected back onto the mesh.

- **UV-direct.** The head's UV island is cut out of her current skin albedo (the same mix and
  tint castle_face's shader makes), turned so the crown is up, and repainted in place. The
  layout is MakeHuman's: the face sits in the middle of a butterfly whose lobes are scalp and
  back of head, and only the pixels that land on the eye, nostril and mouth holes line up.
- **Render-and-project.** Three orthographic views of her face (front, three-quarter left and
  right) are rendered as flat albedo, in the rest pose, with hair, brows, lashes and headset
  hidden. Each is repainted, then projected back onto the head's texels, weighted by how
  squarely each view sees them and occlusion-tested, as the Second Wind projection spike did.

Drift is measured the same way for both: patches around named landmarks (eyes, nose tip, mouth
corners, chin, brows) in the guide are matched in the generation by normalised cross-correlation,
and the offset is reported in pixels and in millimetres on her face. A robust affine fitted to the
confident offsets registers each generation before it is baked; drift is reported both ways.

Guides, briefs, masters and provenance are kept in face-texture/ (ImageGen sources are tracked);
baked textures, .blends and review images are build outputs under build/castle-face-texture/.
"""
import json
import math
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
BLEND = HERE / "castle-figure.blend"
PKG = HERE / "face-texture"
GUIDES = PKG / "guides"
import os
# FACE_TEXTURE_GEN / FACE_TEXTURE_OUT redirect the generations read and the outputs written:
# baking the guides themselves is the round-trip control (it should give back today's skin).
GEN = Path(os.environ.get("FACE_TEXTURE_GEN", PKG / "generated"))
OUT = Path(os.environ.get("FACE_TEXTURE_OUT", REPO / "build" / "castle-face-texture"))

VIEW_PX = 1024
ORTHO_M = 0.27                       # metres across a view: forehead to chin with margin
VIEWS = {"front": 0.0, "left": 35.0, "right": -35.0}   # degrees round her (left = her left)
UV_ASPECT = 16 / 9                   # the island is padded to a landscape canvas
PATCH, SEARCH = 41, 48               # landmark patch and search radius, guide pixels


# ---------------------------------------------------------------- shared (plain Python)

def srgb_to_linear(a):
    import numpy as np
    return np.where(a <= 0.04045, a / 12.92, ((a + 0.055) / 1.055) ** 2.4)


def linear_to_srgb(a):
    import numpy as np
    a = np.clip(a, 0, 1)
    return np.where(a <= 0.0031308, a * 12.92, 1.055 * a ** (1 / 2.4) - 0.055)


def _in_blender():
    import importlib.util
    return importlib.util.find_spec("bpy") is not None


def _resize(a, size):
    """Bilinear resize of an HxWx3 array to (w, h), numpy only."""
    import numpy as np
    w, h = size
    if a.shape[1] == w and a.shape[0] == h:
        return a
    ys = np.linspace(0, a.shape[0] - 1, h)
    xs = np.linspace(0, a.shape[1] - 1, w)
    y0, x0 = np.floor(ys).astype(int), np.floor(xs).astype(int)
    y1, x1 = np.minimum(y0 + 1, a.shape[0] - 1), np.minimum(x0 + 1, a.shape[1] - 1)
    fy, fx = (ys - y0)[:, None, None], (xs - x0)[None, :, None]
    top = a[y0][:, x0] * (1 - fx) + a[y0][:, x1] * fx
    bot = a[y1][:, x0] * (1 - fx) + a[y1][:, x1] * fx
    return top * (1 - fy) + bot * fy


def load_rgb(path, size=None):
    """sRGB image as floats 0..1, HxWx3, top row first."""
    import numpy as np
    if _in_blender():
        import bpy
        img = bpy.data.images.load(str(path), check_existing=False)
        img.colorspace_settings.name = "Non-Color"     # raw sRGB values, no conversion
        a = np.asarray(img.pixels[:], dtype=np.float64).reshape(img.size[1], img.size[0], 4)[::-1, :, :3]
        bpy.data.images.remove(img)
    else:
        from PIL import Image
        a = np.asarray(Image.open(path).convert("RGB"), dtype=np.float64) / 255.0
    return _resize(a, size) if size is not None else a


def save_rgb(a, path):
    import numpy as np
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    a = np.clip(a, 0, 1)
    if _in_blender():
        import bpy
        h, w = a.shape[:2]
        img = bpy.data.images.new("save", w, h, alpha=False)
        img.colorspace_settings.name = "Non-Color"
        rgba = np.concatenate([a[::-1], np.ones((h, w, 1))], axis=2)
        img.pixels.foreach_set(rgba.astype(np.float32).ravel())
        img.filepath_raw, img.file_format = str(path), "PNG"
        img.save()
        bpy.data.images.remove(img)
    else:
        from PIL import Image
        Image.fromarray((a * 255 + 0.5).astype(np.uint8)).save(path)


def match(guide, gen, x, y):
    """Best offset of the guide patch at (x, y) inside gen, by normalised cross-correlation."""
    import numpy as np
    g = guide.mean(axis=2)
    t = gen.mean(axis=2)
    r = PATCH // 2
    x, y = int(round(x)), int(round(y))
    if not (r + SEARCH <= x < g.shape[1] - r - SEARCH and r + SEARCH <= y < g.shape[0] - r - SEARCH):
        return None
    p = g[y - r:y + r + 1, x - r:x + r + 1]
    p = (p - p.mean()) / (p.std() + 1e-6)
    best = (-2.0, 0, 0)
    for dy in range(-SEARCH, SEARCH + 1):
        for dx in range(-SEARCH, SEARCH + 1):
            q = t[y + dy - r:y + dy + r + 1, x + dx - r:x + dx + r + 1]
            c = float(((q - q.mean()) / (q.std() + 1e-6) * p).mean())
            if c > best[0]:
                best = (c, dx, dy)
    return best


def fit_affine(src, dst, tol=6.0):
    """Robust least-squares affine taking guide points onto generation points.

    Fits, drops the worst point while its residual exceeds tol pixels (keeping at least four),
    refits. Returns the 2x3 matrix and the labels' indices kept."""
    import numpy as np
    s, d = np.asarray(src, float), np.asarray(dst, float)
    keep = list(range(len(s)))
    while True:
        A = np.hstack([s[keep], np.ones((len(keep), 1))])
        M, *_ = np.linalg.lstsq(A, d[keep], rcond=None)
        res = np.linalg.norm(np.hstack([s[keep], np.ones((len(keep), 1))]) @ M - d[keep], axis=1)
        worst = int(np.argmax(res))
        if res[worst] <= tol or len(keep) <= 4:
            return M.T.tolist(), keep
        keep.pop(worst)


def warp_affine(gen, M):
    """Resample gen so that the guide position p reads gen at M @ (p, 1) (registration)."""
    import numpy as np
    M = np.asarray(M)
    h, w = gen.shape[:2]
    ys, xs = np.mgrid[0:h, 0:w].astype(float)
    qx = M[0, 0] * xs + M[0, 1] * ys + M[0, 2]
    qy = M[1, 0] * xs + M[1, 1] * ys + M[1, 2]
    qx, qy = np.clip(qx, 0, w - 1.001), np.clip(qy, 0, h - 1.001)
    x0, y0 = qx.astype(int), qy.astype(int)
    fx, fy = (qx - x0)[..., None], (qy - y0)[..., None]
    return ((gen[y0, x0] * (1 - fx) + gen[y0, x0 + 1] * fx) * (1 - fy)
            + (gen[y0 + 1, x0] * (1 - fx) + gen[y0 + 1, x0 + 1] * fx) * fy)


def arms():
    meta = json.loads((GUIDES / "guides.json").read_text())
    out = [("uv", "uv-island", meta["uv"]["landmarks"], meta["uv"]["mm_per_px"])]
    for v in VIEWS:
        out.append(("proj", "view-" + v, meta["views"][v]["landmarks"], meta["views"][v]["mm_per_px"]))
    return meta, out


def measure():
    """Drift of every generation against its guide, raw and after registration."""
    import numpy as np
    meta, items = arms()
    report = {}
    for arm, name, marks, mmpp in items:
        gen_path = GEN / (name + ".png")
        if not gen_path.exists():
            continue
        guide = load_rgb(GUIDES / (name + ".png"))
        gen = load_rgb(gen_path, (guide.shape[1], guide.shape[0]))
        rows, src, dst = {}, [], []
        for label, (x, y) in marks.items():
            m = match(guide, gen, x, y)
            if m is None:
                continue
            c, dx, dy = m
            rows[label] = {"dx": dx, "dy": dy, "px": round(math.hypot(dx, dy), 1),
                           "mm": round(math.hypot(dx, dy) * mmpp, 1), "ncc": round(c, 2)}
            if c > 0.6:
                src.append((x, y))
                dst.append((x + dx, y + dy))
        entry = {"arm": arm, "landmarks": rows}
        if len(src) >= 4:
            M, kept = fit_affine(src, dst)
            entry["affine"] = M
            entry["affine_points"] = len(kept)
            reg = warp_affine(gen, M)
            save_rgb(reg, OUT / "registered" / (name + ".png"))
            after = {}
            for label, (x, y) in marks.items():
                m = match(guide, reg, x, y)
                if m is not None:
                    after[label] = round(math.hypot(m[1], m[2]) * mmpp, 1)
            entry["after_registration_mm"] = after
        good = [r["mm"] for r in rows.values() if r["ncc"] > 0.6]
        entry["median_mm"] = float(np.median(good)) if good else None
        entry["max_mm"] = max(good) if good else None
        if entry.get("after_registration_mm"):
            entry["median_mm_registered"] = float(np.median(list(entry["after_registration_mm"].values())))
        report[name] = entry
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "drift.json").write_text(json.dumps(report, indent=1))
    for name, e in report.items():
        print("%-12s median %s mm, max %s mm, registered median %s mm  %s" % (
            name, e["median_mm"], e["max_mm"], e.get("median_mm_registered"),
            {k: (v["mm"], v["ncc"]) for k, v in e["landmarks"].items()}))
    return report


# ---------------------------------------------------------------- Blender side

def _setup(bpy):
    """Rest pose, evaluated body and its effective skin albedo (linear, the shader's own mix)."""
    import numpy as np
    rig = bpy.data.objects["Castle rig"]
    rig.data.pose_position = "REST"
    # Work in MakeHuman's own frame (front -Y, left +X): undo the package's turn onto +X.
    rig["_turn"] = rig.rotation_euler[2]
    rig.rotation_euler[2] = 0.0
    bpy.data.objects["Castle figure root"].rotation_euler = (0, 0, 0)
    bpy.context.view_layer.update()
    body = bpy.data.objects["Castle body"]
    mat = body.data.materials[0]
    texs = [n for n in mat.node_tree.nodes if n.type == "TEX_IMAGE"]
    mix = [n for n in mat.node_tree.nodes if n.type == "MIX" and n.blend_type == "MIX"][0]
    tint = [n for n in mat.node_tree.nodes if n.type == "MIX" and n.blend_type == "MULTIPLY"][0]
    a, b = (np.asarray(t.image.pixels[:], dtype=np.float64).reshape(t.image.size[1], t.image.size[0], 4)[::-1, :, :3]
            for t in texs[:2])
    f = mix.inputs["Factor"].default_value
    albedo = (srgb_to_linear(a) * (1 - f) + srgb_to_linear(b) * f) * np.array(tint.inputs[7].default_value[:3])
    return rig, body, albedo


def _head_mesh(bpy, body):
    """World positions, normals and UV triangles of the head (rest pose, evaluated)."""
    import numpy as np
    dg = bpy.context.evaluated_depsgraph_get()
    ev = body.evaluated_get(dg)
    me = ev.to_mesh()
    me.calc_loop_triangles()
    mw = np.array(ev.matrix_world)
    co = np.array([v.co for v in me.vertices]) @ mw[:3, :3].T + mw[:3, 3]
    nrm = np.array([v.normal for v in me.vertices]) @ mw[:3, :3].T
    uv = me.uv_layers.active.data
    tris, tuv = [], []
    for t in me.loop_triangles:
        vs = list(t.vertices)
        uvs = [tuple(uv[l].uv) for l in t.loops]
        if min(co[vs, 2]) > 1.30 and min(u for u, _ in uvs) > 0.6 and min(v for _, v in uvs) > 0.13:
            tris.append(vs)
            tuv.append(uvs)
    ev.to_mesh_clear()
    return co, nrm, np.array(tris), np.array(tuv)


def _landmarks(bpy, rig, co):
    """Named 3D landmarks on her skin (rest pose)."""
    import numpy as np
    from mathutils import Vector
    m = rig.matrix_world
    bone = lambda b: np.array(m @ rig.pose.bones[b].tail)
    eyes = {s: bone("eye." + s) for s in ("L", "R")}
    face = co[(co[:, 2] > 1.25) & (co[:, 1] < -0.03)]
    mid = face[np.abs(face[:, 0]) < 0.004]
    eye_z = (eyes["L"][2] + eyes["R"][2]) / 2
    nose = mid[(mid[:, 2] < eye_z - 0.01) & (mid[:, 2] > eye_z - 0.09)]
    nose_tip = nose[np.argmin(nose[:, 1])]
    lips = mid[(mid[:, 2] < nose_tip[2] - 0.015) & (mid[:, 2] > nose_tip[2] - 0.06)]
    mouth = lips[np.argmin(lips[:, 1])]
    chin_pts = mid[(mid[:, 2] < mouth[2] - 0.02) & (mid[:, 2] > mouth[2] - 0.08)]
    chin = chin_pts[np.argmin(chin_pts[:, 1])]
    out = {"nose tip": nose_tip, "mouth": mouth, "chin": chin}
    for s, sign in (("L", 1), ("R", -1)):
        e = eyes[s]
        near = lambda p: face[np.argmin(((face - p) ** 2).sum(1))]
        out["eye " + s] = near(e + np.array([0, -0.01, 0]))
        out["brow " + s] = near(e + np.array([sign * 0.005, -0.005, 0.03]))
        out["mouth corner " + s] = near(mouth + np.array([sign * 0.025, 0.012, 0.002]))
        out["cheekbone " + s] = near(e + np.array([sign * 0.03, 0.0, -0.035]))
    return out


def _uv_island(bpy, body, albedo, co, tris, tuv, marks):
    """Cut the head island out of the albedo, crown up, padded to UV_ASPECT; return its frame."""
    import numpy as np
    H, W = albedo.shape[:2]
    us, vs = tuv[..., 0].ravel(), tuv[..., 1].ravel()
    x0, x1 = int(us.min() * W) - 8, int(us.max() * W) + 8
    y0, y1 = int((1 - vs.max()) * H) - 8, int((1 - vs.min()) * H) + 8
    crop = albedo[y0:y1, x0:x1]
    rot = np.rot90(crop, k=-1)                      # crown (island's left) to the top
    h, w = rot.shape[:2]
    cw = max(w, int(math.ceil(h * UV_ASPECT)))
    pad_x = (cw - w) // 2
    canvas = np.empty((h, cw, 3))
    canvas[:] = np.median(crop.reshape(-1, 3), axis=0)
    canvas[:, pad_x:pad_x + w] = rot
    frame = {"box": [x0, y0, x1, y1], "pad_x": pad_x, "size": [cw, h], "texture": [W, H]}

    def to_canvas(u, v):                            # texture UV -> canvas pixel
        tx, ty = u * W - x0, (1 - v) * H - y0       # in the crop
        return (crop.shape[0] - 1 - ty) + pad_x, tx     # rot90(k=-1): (x, y) -> (h-1-y, x)

    # Each landmark's UV: the nearest head-triangle vertex's.
    vidx = tris.ravel()
    flat_uv = tuv.reshape(-1, 2)
    lm = {}
    for label, p in marks.items():
        i = np.argmin(((co[vidx] - p) ** 2).sum(1))
        lm[label] = list(to_canvas(*flat_uv[i]))
    # Millimetres per canvas pixel near the face: 3D distance over UV distance, eye to eye.
    eyes_uv = [np.array(lm["eye L"]), np.array(lm["eye R"])]
    mm = np.linalg.norm(marks["eye L"] - marks["eye R"]) * 1000 / np.linalg.norm(eyes_uv[0] - eyes_uv[1])
    return linear_to_srgb(canvas), frame, lm, float(mm)


def _camera(view_deg, aim):
    """Orthographic view frame: forward, right, up (her front is -Y in the rest frame)."""
    import numpy as np
    a = math.radians(view_deg)
    to_cam = np.array([math.sin(a), -math.cos(a), 0.0])     # positive: round to her left (+X)
    fwd = -to_cam
    up = np.array([0, 0, 1.0])
    right = np.cross(fwd, up)
    return fwd, right / np.linalg.norm(right), up


def _project(p, aim, right, up):
    x = ((p - aim) @ right / ORTHO_M + 0.5) * VIEW_PX
    y = (0.5 - (p - aim) @ up / ORTHO_M) * VIEW_PX
    return x, y


def _emission_scene(bpy):
    """Swap every material for its unlit colour, so a render is albedo (sRGB, Standard view)."""
    for mat in bpy.data.materials:
        if not mat.use_nodes:
            continue
        nt = mat.node_tree
        bsdf = next((n for n in nt.nodes if n.type == "BSDF_PRINCIPLED"), None)
        out = next((n for n in nt.nodes if n.type == "OUTPUT_MATERIAL"), None)
        if bsdf is None or out is None:
            continue
        em = nt.nodes.new("ShaderNodeEmission")
        base = bsdf.inputs["Base Color"]
        if base.is_linked:
            nt.links.new(base.links[0].from_socket, em.inputs["Color"])
        else:
            em.inputs["Color"].default_value = base.default_value
        surf = em.outputs["Emission"]
        if bsdf.inputs["Alpha"].is_linked:
            tr = nt.nodes.new("ShaderNodeBsdfTransparent")
            mix = nt.nodes.new("ShaderNodeMixShader")
            nt.links.new(bsdf.inputs["Alpha"].links[0].from_socket, mix.inputs["Fac"])
            nt.links.new(tr.outputs[0], mix.inputs[1])
            nt.links.new(surf, mix.inputs[2])
            surf = mix.outputs[0]
        nt.links.new(surf, out.inputs["Surface"])


def blender_guides():
    import bpy
    import numpy as np
    from mathutils import Matrix, Vector
    rig, body, albedo = _setup(bpy)
    co, nrm, tris, tuv = _head_mesh(bpy, body)
    marks = _landmarks(bpy, rig, co)
    GUIDES.mkdir(parents=True, exist_ok=True)
    canvas, frame, uv_lm, uv_mm = _uv_island(bpy, body, albedo, co, tris, tuv, marks)
    save_rgb(canvas, GUIDES / "uv-island.png")
    meta = {"uv": dict(frame, landmarks=uv_lm, mm_per_px=uv_mm), "views": {},
            "landmarks_3d": {k: v.tolist() for k, v in marks.items()}}

    # ---- the projection views: flat albedo, rest pose, skin and eyes only
    scene = bpy.context.scene
    _emission_scene(bpy)
    for ob in bpy.data.objects:
        n = ob.name
        hide = ob.type in ("CURVES", "LIGHT") or any(k in n for k in (
            "hair", "short04", "bun", "eyebrow", "eyelash", "earpiece", "boom", "collar"))
        ob.hide_render = hide
    scene.render.engine = "BLENDER_EEVEE"
    scene.view_settings.view_transform = "Standard"
    world = bpy.data.worlds.new("Guide world")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.18, 0.18, 0.18, 1)
    scene.world = world
    scene.render.resolution_x = scene.render.resolution_y = VIEW_PX
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = False
    cam = bpy.data.objects.new("Guide camera", bpy.data.cameras.new("Guide camera"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.type, cam.data.ortho_scale = "ORTHO", ORTHO_M
    cam.data.clip_start, cam.data.clip_end = 0.01, 5
    aim = (marks["eye L"] + marks["eye R"]) / 2 + np.array([0, 0, -0.035])
    for v, deg in VIEWS.items():
        fwd, right, up = _camera(deg, aim)
        cam.matrix_world = Matrix((
            (right[0], up[0], -fwd[0], aim[0] - fwd[0]),
            (right[1], up[1], -fwd[1], aim[1] - fwd[1]),
            (right[2], up[2], -fwd[2], aim[2] - fwd[2]),
            (0, 0, 0, 1)))
        scene.render.filepath = str(GUIDES / ("view-%s.png" % v))
        bpy.ops.render.render(write_still=True)
        lm = {}
        for label, p in marks.items():
            # Only landmarks this view sees squarely enough to match.
            n = nrm[np.argmin(((co - p) ** 2).sum(1))]
            if n @ -fwd > 0.35:
                lm[label] = list(_project(p, aim, right, up))
        meta["views"][v] = {"deg": deg, "aim": aim.tolist(), "fwd": fwd.tolist(), "right": right.tolist(),
                            "up": up.tolist(), "landmarks": lm, "mm_per_px": ORTHO_M * 1000 / VIEW_PX}
    (GUIDES / "guides.json").write_text(json.dumps(meta, indent=1))
    print("GUIDES", GUIDES, flush=True)


def _raster_head(tris, tuv, co, nrm, W, H):
    """Per texel of the head island: world position, normal, coverage."""
    import numpy as np
    pos = np.zeros((H, W, 3))
    nor = np.zeros((H, W, 3))
    cov = np.zeros((H, W), bool)
    for t, uvs in zip(tris, tuv):
        px = uvs[:, 0] * W - 0.5
        py = (1 - uvs[:, 1]) * H - 0.5
        xa, xb = int(max(math.floor(px.min()), 0)), int(min(math.ceil(px.max()), W - 1))
        ya, yb = int(max(math.floor(py.min()), 0)), int(min(math.ceil(py.max()), H - 1))
        if xb < xa or yb < ya:
            continue
        ys, xs = np.mgrid[ya:yb + 1, xa:xb + 1]
        d = (py[1] - py[2]) * (px[0] - px[2]) + (px[2] - px[1]) * (py[0] - py[2])
        if abs(d) < 1e-12:
            continue
        l0 = ((py[1] - py[2]) * (xs - px[2]) + (px[2] - px[1]) * (ys - py[2])) / d
        l1 = ((py[2] - py[0]) * (xs - px[2]) + (px[0] - px[2]) * (ys - py[2])) / d
        l2 = 1 - l0 - l1
        inside = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
        if not inside.any():
            continue
        L = np.stack([l0, l1, l2], -1)[inside]
        yy, xx = ys[inside], xs[inside]
        pos[yy, xx] = L @ co[t]
        nor[yy, xx] = L @ nrm[t]
        cov[yy, xx] = True
    n = np.linalg.norm(nor, axis=2, keepdims=True)
    return pos, nor / np.maximum(n, 1e-9), cov


def _zbuffer(tris, co, view):
    """Depth of the nearest head surface per view pixel."""
    import numpy as np
    aim, fwd, right, up = (np.array(view[k]) for k in ("aim", "fwd", "right", "up"))
    x, y = _project(co, aim, right, up)
    z = (co - aim) @ fwd
    zb = np.full((VIEW_PX, VIEW_PX), np.inf)
    for t in tris:
        px, py, pz = x[t], y[t], z[t]
        xa, xb = int(max(math.floor(px.min()), 0)), int(min(math.ceil(px.max()), VIEW_PX - 1))
        ya, yb = int(max(math.floor(py.min()), 0)), int(min(math.ceil(py.max()), VIEW_PX - 1))
        if xb < xa or yb < ya:
            continue
        ys, xs = np.mgrid[ya:yb + 1, xa:xb + 1]
        d = (py[1] - py[2]) * (px[0] - px[2]) + (px[2] - px[1]) * (py[0] - py[2])
        if abs(d) < 1e-12:
            continue
        l0 = ((py[1] - py[2]) * (xs - px[2]) + (px[2] - px[1]) * (ys - py[2])) / d
        l1 = ((py[2] - py[0]) * (xs - px[2]) + (px[0] - px[2]) * (ys - py[2])) / d
        l2 = 1 - l0 - l1
        inside = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
        if inside.any():
            dz = l0 * pz[0] + l1 * pz[1] + l2 * pz[2]
            sub = zb[ya:yb + 1, xa:xb + 1]
            sub[inside] = np.minimum(sub[inside], dz[inside])
    return zb


def _sample(img, x, y):
    import numpy as np
    h, w = img.shape[:2]
    x, y = np.clip(x, 0, w - 1.001), np.clip(y, 0, h - 1.001)
    x0, y0 = x.astype(int), y.astype(int)
    fx, fy = (x - x0)[..., None], (y - y0)[..., None]
    return ((img[y0, x0] * (1 - fx) + img[y0, x0 + 1] * fx) * (1 - fy)
            + (img[y0 + 1, x0] * (1 - fx) + img[y0 + 1, x0 + 1] * fx) * fy)


def _feather(mask, px):
    """A soft 0..1 version of a mask: three box blurs of width 2px+1 (about a Gaussian)."""
    import numpy as np
    a = np.asarray(mask, dtype=np.float64)
    k = 2 * int(px) + 1
    for _ in range(3):
        for axis in (0, 1):
            c = np.cumsum(np.pad(a, [(k // 2 + 1, k // 2)] * 1 + [(0, 0)] if axis == 0 else [(0, 0), (k // 2 + 1, k // 2)],
                                 mode="edge"), axis=axis)
            a = ((c[k:] - c[:-k]) if axis == 0 else (c[:, k:] - c[:, :-k])) / k
    return a


def blender_bake():
    """Both arms baked into full skin textures, and a .blend per arm with that texture alone."""
    import bpy
    import numpy as np
    meta = json.loads((GUIDES / "guides.json").read_text())
    drift = json.loads((OUT / "drift.json").read_text()) if (OUT / "drift.json").exists() else {}
    rig, body, albedo = _setup(bpy)
    co, nrm, tris, tuv = _head_mesh(bpy, body)
    H, W = albedo.shape[:2]
    pos, nor, cov = _raster_head(tris, tuv, co, nrm, W, H)
    base = linear_to_srgb(albedo)
    results = {}

    def gen_for(name):
        reg = OUT / "registered" / (name + ".png")
        src = reg if reg.exists() else GEN / (name + ".png")
        g = load_rgb(GUIDES / (name + ".png"))
        return load_rgb(src, (g.shape[1], g.shape[0])) if src.exists() else None

    # ---- UV-direct: undo the pad and the turn, paste over the island
    u = meta["uv"]
    gen = gen_for("uv-island")
    if gen is not None:
        x0, y0, x1, y1 = u["box"]
        w_rot = (y1 - y0)
        rot = gen[:, u["pad_x"]:u["pad_x"] + w_rot]
        crop = np.rot90(rot, k=1)
        tex = base.copy()
        alpha = _feather(cov[y0:y1, x0:x1], 3)[..., None] * cov[y0:y1, x0:x1, None]
        tex[y0:y1, x0:x1] = crop * alpha + tex[y0:y1, x0:x1] * (1 - alpha)
        results["uv"] = tex

    # ---- render-and-project: per texel, the views that see it, weighted by facing
    acc = np.zeros((H, W, 3))
    wsum = np.zeros((H, W))
    yy, xx = np.nonzero(cov)
    P, N = pos[yy, xx], nor[yy, xx]
    for v, view in meta["views"].items():
        gen = gen_for("view-" + v)
        if gen is None:
            continue
        aim, fwd, right, up = (np.array(view[k]) for k in ("aim", "fwd", "right", "up"))
        zb = _zbuffer(tris, co, view)
        x, y = _project(P, aim, right, up)
        z = (P - aim) @ fwd
        inb = (x >= 0) & (x < VIEW_PX - 1) & (y >= 0) & (y < VIEW_PX - 1)
        vis = np.zeros(len(P), bool)
        xi, yi = np.clip(x.astype(int), 0, VIEW_PX - 1), np.clip(y.astype(int), 0, VIEW_PX - 1)
        vis[inb] = z[inb] <= zb[yi[inb], xi[inb]] + 0.002
        w = np.clip(N @ -fwd, 0, 1) ** 3 * vis
        col = _sample(gen, x, y)
        acc[yy, xx] += col * w[:, None]
        wsum[yy, xx] += w
    if wsum.max() > 0:
        painted = acc / np.maximum(wsum, 1e-9)[..., None]
        alpha = np.clip(wsum / 0.25, 0, 1)
        alpha = np.minimum(alpha, _feather(alpha > 0.5, 4))[..., None]
        results["proj"] = painted * alpha + base * (1 - alpha)

    # ---- one .blend per arm: the skin's texture replaced, its mix and tint bypassed
    OUT.mkdir(parents=True, exist_ok=True)
    rig.data.pose_position = "POSE"
    rig.rotation_euler[2] = rig["_turn"]
    del rig["_turn"]
    mat = body.data.materials[0]
    nt = mat.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    tex_node = next(n for n in nt.nodes if n.type == "TEX_IMAGE")
    for arm, tex in results.items():
        path = OUT / ("skin-%s.png" % arm)
        save_rgb(tex, path)
        img = bpy.data.images.load(str(path), check_existing=False)
        img.pack()
        tex_node.image = img
        nt.links.new(tex_node.outputs["Color"], bsdf.inputs["Base Color"])
        bpy.ops.wm.save_as_mainfile(filepath=str(OUT / ("castle-%s.blend" % arm)), copy=True, compress=True)
        print("BAKED", arm, path, flush=True)


def compare():
    """Her model with each skin beside her portrait, from the portrait's angle (EEVEE), and turns.

    Writes OUT/review/: 01 the portrait and the three skins side by side; then per skin its
    face_review sheets (beside the portrait, and front / three-quarter / profile)."""
    from PIL import Image, ImageDraw
    review = OUT / "review"
    review.mkdir(parents=True, exist_ok=True)
    blends = [("current", BLEND), ("uv-direct", OUT / "castle-uv.blend"), ("projected", OUT / "castle-proj.blend")]
    blends = [(n, b) for n, b in blends if b.exists()]
    for n, b in blends:
        subprocess.check_call([sys.executable, str(HERE / "face_review.py"), "--out", str(review),
                               "--blend", str(b), "--prefix", n + "-"])
    W, H = 1920, 1080
    cols = 1 + len(blends)
    cw = W // cols
    sheet = Image.new("RGB", (W, H), (12, 12, 14))
    draw = ImageDraw.Draw(sheet)
    portrait = Image.open(HERE.parents[1] / "portraits" / "castle" / "castle-master.png").convert("RGB")
    panels = [("portrait (target)", portrait.crop((150, 120, 950, 1020)))]
    for n, _ in blends:
        full = Image.open(review / ("%s-face-vs-portrait-eevee.png" % n)).convert("RGB")
        panels.append((n, full.crop((H, 0, W, H))))
    for i, (label, im) in enumerate(panels):
        k = min(cw / im.size[0], (H - 40) / im.size[1])
        im = im.resize((int(im.size[0] * k), int(im.size[1] * k)), Image.LANCZOS)
        sheet.paste(im, (i * cw + (cw - im.size[0]) // 2, (H - 40 - im.size[1]) // 2))
        draw.text((i * cw + 12, H - 30), label, fill=(220, 220, 210))
    sheet.save(review / "01-skins-vs-portrait.png")
    print("COMPARE", review, flush=True)


def launch(argv):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    cmd = argv[0] if argv else ""
    if cmd == "measure":
        measure()
        return
    if cmd == "compare":
        compare()
        return
    if cmd not in ("guides", "bake"):
        sys.exit(__doc__)
    subprocess.check_call([BLENDER, "--background", "--factory-startup", str(BLEND), "--python",
                           str(Path(__file__).resolve()), "--", cmd])


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        {"guides": blender_guides, "bake": blender_bake}[argv[0]]()
    else:
        launch(argv)
