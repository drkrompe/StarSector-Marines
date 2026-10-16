"""Geometry-first guides for C4's depth layers (c4-layout.md).

"C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" --background --factory-startup --python build_c4_guides.py [-- --layers L4-near,L3-mid --samples 16 --scale 0.5]

Builds the c4_world.py massing as clay, one layer at a time, and renders each layer as a
pushbroom panorama at its representative depth: every vertex's X is scaled about the
plate's centre by y / d_rep, and a single level rectilinear camera then gives
column = f * (x - x_centre) / d_rep exactly, with true perspective vertically. That is the
plate the compositor slides. Layers L2-L4 render on a transparent film; L1 carries the sky.

Writes guides/c4-<layer>-guide.png at plate resolution (2x output). These are the edit
targets for the Codex reskin: they own placement, silhouette, horizon and scale.
"""
import math
import sys
from pathlib import Path

import bpy
import bmesh

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c4_camera as cam  # noqa: E402
import c4_world as world  # noqa: E402

CLAY = {
    "ground": (0.30, 0.37, 0.24), "plain": (0.25, 0.33, 0.21), "verge": (0.36, 0.44, 0.27),
    "scrub": (0.40, 0.42, 0.30), "crop_a": (0.27, 0.40, 0.19), "crop_b": (0.36, 0.47, 0.22),
    "crop_c": (0.52, 0.50, 0.30), "soil": (0.42, 0.34, 0.25), "track": (0.47, 0.42, 0.33),
    "road": (0.20, 0.20, 0.21), "marking": (0.85, 0.82, 0.70), "concrete": (0.42, 0.42, 0.40),
    "canopy": (0.11, 0.22, 0.10), "canopy2": (0.16, 0.28, 0.12), "water": (0.30, 0.40, 0.46),
    "building": (0.70, 0.69, 0.66), "roof": (0.43, 0.45, 0.48), "house": (0.74, 0.69, 0.60),
    "tank": (0.62, 0.64, 0.64), "silo": (0.66, 0.66, 0.64), "container": (0.55, 0.36, 0.25),
    "crane": (0.78, 0.46, 0.18), "steel": (0.40, 0.41, 0.43), "glass": (0.25, 0.33, 0.38),
    "pad": (0.52, 0.52, 0.50), "cradle": (0.55, 0.56, 0.60), "hull": (0.62, 0.58, 0.50),
    "fence": (0.45, 0.46, 0.46), "gate": (0.80, 0.62, 0.20), "trailer": (0.62, 0.30, 0.18),
    "mountain": (0.30, 0.36, 0.28),
}


def args():
    a = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    out = dict(layers=None, samples=16, scale=1.0)
    for k, v in zip(a[::2], a[1::2]):
        if k == "--layers":
            out["layers"] = v.split(",")
        elif k == "--samples":
            out["samples"] = int(v)
        elif k == "--scale":
            out["scale"] = float(v)
    return out


def material(name):
    m = bpy.data.materials.get("clay " + name)
    if m:
        return m
    m = bpy.data.materials.new("clay " + name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*CLAY[name], 1.0)
    b.inputs["Roughness"].default_value = 0.85 if name not in ("water", "glass") else 0.15
    return m


class Builder:
    """One merged mesh per material for the layer being built."""

    def __init__(self, layer):
        self.layer = layer
        x0, x1 = cam.layer_x_range(layer)
        self.xc = (x0 + x1) / 2
        self.d = layer["d_rep"]
        self.meshes = {}

    def warp(self, x, y, z):
        return (self.xc + (x - self.xc) * y / self.d, y, z)

    def bm(self, mat):
        if mat not in self.meshes:
            self.meshes[mat] = bmesh.new()
        return self.meshes[mat]

    def quad_grid(self, mat, xs, ys, zf):
        bm = self.bm(mat)
        rows = [[bm.verts.new(self.warp(x, y, zf(x, y))) for x in xs] for y in ys]
        for j in range(len(ys) - 1):
            for i in range(len(xs) - 1):
                bm.faces.new((rows[j][i], rows[j][i + 1], rows[j + 1][i + 1], rows[j + 1][i]))

    def plane(self, p):
        (x0, x1), (y0, y1), z = p["x"], p["y"], p["z"]
        nx = max(1, int((x1 - x0) / 80))
        self.quad_grid(p["mat"], [x0 + (x1 - x0) * i / nx for i in range(nx + 1)], [y0, y1], lambda x, y: z)

    def box(self, p):
        (x0, x1), (y0, y1), (z0, z1) = p["x"], p["y"], p["z"]
        bm = self.bm(p["mat"])
        v = [bm.verts.new(self.warp(x, y, z)) for z in (z0, z1) for y in (y0, y1) for x in (x0, x1)]
        for f in ((0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)):
            bm.faces.new([v[i] for i in f])

    def prism(self, p):
        (x0, x1), (y0, y1), (z0, z1) = p["x"], p["y"], p["z"]
        bm = self.bm(p["mat"])
        ym = (y0 + y1) / 2
        a = [bm.verts.new(self.warp(x0, y, z)) for y, z in ((y0, z0), (ym, z1), (y1, z0))]
        b = [bm.verts.new(self.warp(x1, y, z)) for y, z in ((y0, z0), (ym, z1), (y1, z0))]
        bm.faces.new((a[0], a[1], b[1], b[0]))
        bm.faces.new((a[1], a[2], b[2], b[1]))
        bm.faces.new((a[0], a[2], a[1]))
        bm.faces.new((b[0], b[1], b[2]))

    def cyl(self, p, seg=14):
        (cx, cy), r, (z0, z1) = p["c"], p["r"], p["z"]
        bm = self.bm(p["mat"])
        lo, hi = [], []
        for i in range(seg):
            a = 2 * math.pi * i / seg
            x, y = cx + r * math.cos(a), cy + r * math.sin(a)
            lo.append(bm.verts.new(self.warp(x, y, z0)))
            hi.append(bm.verts.new(self.warp(x, y, z1)))
        for i in range(seg):
            j = (i + 1) % seg
            bm.faces.new((lo[i], lo[j], hi[j], hi[i]))
        bm.faces.new(hi)

    def dome(self, p, seg=8, rings=3):
        (cx, cy), r, h, z0 = p["c"], p["r"], p["h"], p["z0"]
        bm = self.bm(p["mat"])
        rows = []
        for k in range(rings):
            phi = (math.pi / 2) * k / rings
            rr, zz = r * math.cos(phi), z0 + h * math.sin(phi)
            rows.append([bm.verts.new(self.warp(cx + rr * math.cos(2 * math.pi * i / seg),
                                                cy + rr * math.sin(2 * math.pi * i / seg), zz)) for i in range(seg)])
        top = bm.verts.new(self.warp(cx, cy, z0 + h))
        for k in range(rings - 1):
            for i in range(seg):
                j = (i + 1) % seg
                bm.faces.new((rows[k][i], rows[k][j], rows[k + 1][j], rows[k + 1][i]))
        for i in range(seg):
            bm.faces.new((rows[-1][i], rows[-1][(i + 1) % seg], top))

    def terrain(self, p):
        (x0, x1), (y0, y1) = p["x"], p["y"]
        xs = [x0 + (x1 - x0) * i / 260 for i in range(261)]
        ys = [y0 + (y1 - y0) * (j / 90) ** 1.6 for j in range(91)]
        self.quad_grid(p["mat"], xs, ys, world.terrain_height)

    def build(self, prims):
        for p in prims:
            if p["layer"] == self.layer["id"]:
                getattr(self, p["kind"])(p)
        col = bpy.data.collections.new(self.layer["id"])
        bpy.context.scene.collection.children.link(col)
        for mat, bm in self.meshes.items():
            me = bpy.data.meshes.new("%s %s" % (self.layer["id"], mat))
            bm.to_mesh(me)
            bm.free()
            me.materials.append(material(mat))
            ob = bpy.data.objects.new(me.name, me)
            col.objects.link(ob)
        return col


def setup_world(sky):
    w = bpy.data.worlds.new("C4 sky")
    bpy.context.scene.world = w
    w.use_nodes = True
    nt = w.node_tree
    nt.nodes.clear()
    tc = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    bg = nt.nodes.new("ShaderNodeBackground")
    out = nt.nodes.new("ShaderNodeOutputWorld")
    nt.links.new(tc.outputs["Generated"], sep.inputs[0])
    nt.links.new(sep.outputs["Z"], ramp.inputs[0])
    nt.links.new(ramp.outputs[0], bg.inputs["Color"])
    nt.links.new(bg.outputs[0], out.inputs[0])
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = (0.80, 0.86, 0.92, 1)
    ramp.color_ramp.elements[1].position = 0.35
    ramp.color_ramp.elements[1].color = (0.36, 0.55, 0.82, 1)
    bg.inputs["Strength"].default_value = 0.9 if sky else 0.7


def setup_sun():
    sun = bpy.data.lights.new("C4 sun", "SUN")
    sun.energy = 3.0
    sun.angle = math.radians(0.6)
    ob = bpy.data.objects.new("C4 sun", sun)
    bpy.context.scene.collection.objects.link(ob)
    sx, sy, sz = cam.SUN_TOWARD
    n = math.sqrt(sx * sx + sy * sy + sz * sz)
    # a sun lamp shines along its local -Z: point -Z away from the sun
    from mathutils import Vector
    ob.rotation_euler = Vector((sx / n, sy / n, sz / n)).to_track_quat("Z", "Y").to_euler()


def render_layer(layer, prims, samples, scale):
    for c in list(bpy.data.collections):
        bpy.data.collections.remove(c)
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o)
    for m in list(bpy.data.meshes):
        bpy.data.meshes.remove(m)
    sc = bpy.context.scene
    b = Builder(layer)
    b.build(prims)
    setup_sun()
    sky = layer["id"] == "L1-far"
    setup_world(sky)
    w, h = cam.plate_size(layer)
    camd = bpy.data.cameras.new("C4 plate camera")
    camd.sensor_fit = "HORIZONTAL"
    camd.sensor_width = 36.0
    camd.lens = cam.F_PX * cam.PLATE_SCALE * 36.0 / w
    camd.shift_y = -(h / 2 - cam.HORIZON_Y * cam.PLATE_SCALE) / w
    camd.clip_start, camd.clip_end = 1.0, 120000.0
    co = bpy.data.objects.new("C4 plate camera", camd)
    sc.collection.objects.link(co)
    co.location = (b.xc, 0.0, cam.ALTITUDE)
    co.rotation_euler = (math.radians(90), 0, 0)
    sc.camera = co
    sc.render.engine = "CYCLES"
    prefs = bpy.context.preferences.addons["cycles"].preferences
    try:
        prefs.compute_device_type = "OPTIX"
        prefs.get_devices()
        for d in prefs.devices:
            d.use = True
        sc.cycles.device = "GPU"
    except Exception as e:  # noqa: BLE001
        print("OPTIX unavailable, CPU:", e)
    sc.cycles.samples = samples
    sc.cycles.use_denoising = True
    sc.render.film_transparent = not sky
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.render.resolution_percentage = int(scale * 100)
    sc.view_settings.view_transform = "Standard"
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA" if not sky else "RGB"
    out = BASE / "guides" / ("c4-%s-guide.png" % layer["id"])
    sc.render.filepath = str(out)
    bpy.ops.render.render(write_still=True)
    print("wrote", out.name, w, h, "lens %.3f shift_y %.4f" % (camd.lens, camd.shift_y))


def main():
    a = args()
    want = a["layers"] or [L["id"] for L in cam.LAYERS if not math.isinf(L["d_rep"])]
    for L in cam.LAYERS:
        if L["id"] in want:
            render_layer(L, world.PRIMS, a["samples"], a["scale"])


main()
