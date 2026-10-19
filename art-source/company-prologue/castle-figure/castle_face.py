"""Castle's face and skin, kept apart from build_castle.py's body, hair and pose.

build_castle.py calls three things from here, and owns nothing about the face itself:

- FACE_TARGETS: MakeHuman face modifiers (target name, weight), handed to MPFB with the
  phenotype, so eyes, eyebrows and eyelashes are fitted to the face they sit on;
- dress_face(body, parts): after MPFB has built her: the lopsided half-smile, and textured
  materials for skin, eyes, eyebrows and eyelashes, each keeping a flat diffuse_color so
  Workbench reviews read as before;
- pack(): before saving, packs every image into the .blend so it opens on a machine without
  MPFB's asset folders (the Mac takes it from Drive).

The reference is her portrait (`art-source/portraits/castle/castle-master.png`) and the
two full-figure cards in `art-source/hq-command-deck/`: a long face with high, prominent
cheekbones, a long straight nose, a firm jaw and squared chin, deep-set grey-green eyes with
crow's feet, fairly straight brows, thin lips held in a restrained half-smile that lifts on her
left, and warm, weathered, pale olive skin. A 3D figure gets the planes of that face; the
ImageGen repaint (`geometry-first-imagegen.md`) gets its brushwork.

Every texture is MakeHuman's own, from the CC0 system-asset pack MPFB installs. The skin is
our own Principled BSDF over MakeHuman's diffuse, not MPFB's enhanced-skin node tree, whose
region masks ship with the plug-in rather than the CC0 pack.
"""
import os

import bpy

# MakeHuman face modifiers. A modifier pair (decr/incr, in/out ...) is two targets: name the
# side wanted. Weights are 0..1 of the full target.
FACE_TARGETS = [
    # A long face, lean below the cheekbones; older planes.
    ("head-oval", 0.2), ("head-scale-vert-incr", 0.12),
    ("head-fat-decr", 0.25), ("head-age-incr", 0.45),
    ("l-cheek-bones-incr", 0.8), ("r-cheek-bones-incr", 0.8),
    ("l-cheek-volume-decr", 0.4), ("r-cheek-volume-decr", 0.4),
    ("l-cheek-inner-decr", 0.2), ("r-cheek-inner-decr", 0.2),
    # A firm jaw and squared chin.
    ("chin-bones-incr", 0.35), ("chin-width-incr", 0.25), ("chin-prominent-incr", 0.25),
    ("chin-height-incr", 0.15),
    # A long, straight, narrow-tipped nose.
    ("nose-scale-vert-incr", 0.15), ("nose-scale-depth-incr", 0.1), ("nose-point-down", 0.15),
    ("nose-greek-incr", 0.3), ("nose-scale-horiz-decr", 0.1), ("nose-point-width-decr", 0.25), ("nose-width2-decr", 0.15),
    ("nose-flaring-decr", 0.15),
    # Deep-set eyes under a firm brow, a little hooded, bags of age.
    ("eyebrows-trans-forward", 0.3), ("eyebrows-angle-down", 0.25),
    ("l-eye-push1-in", 0.25), ("r-eye-push1-in", 0.25),
    ("l-eye-scale-incr", 0.15), ("r-eye-scale-incr", 0.15),
    ("l-eye-eyefold-down", 0.25), ("r-eye-eyefold-down", 0.25),
    ("l-eye-bag-incr", 0.35), ("r-eye-bag-incr", 0.35),
    # Thin lips, a wider mouth, the folds beside it.
    ("mouth-upperlip-volume-decr", 0.2), ("mouth-lowerlip-volume-incr", 0.1),
    ("mouth-scale-horiz-incr", 0.12), ("mouth-laugh-lines-in", 0.45),
    ("forehead-temple-decr", 0.2),
]

# The half-smile: MakeHuman's zygomatic "corner puller" expression unit, mostly on her left
# (MPFB's +X), a trace on her right, the corners pressed a little so it stays closed-mouthed.
SMILE_LEFT, SMILE_RIGHT, SMILE_PRESS = 0.4, 0.0, 0.3

SKIN = "middleage_caucasian_female"
SKIN_AGED = ("old_caucasian_female", 0.45)   # its diffuse mixed in for lines and weathering
SKIN_TINT = (0.9, 0.86, 0.7)        # multiplies MakeHuman's diffuse: away from pink, toward olive
SKIN_FLAT = (0.56, 0.40, 0.31)      # Workbench
EYE = "grey"                        # her eyes are a pale grey-green: MakeHuman's grey,
EYE_TINT = (0.86, 0.95, 0.78)      # tinted toward green


def _mpfb():
    from bl_ext.blender_org.mpfb.services.assetservice import AssetService
    from bl_ext.blender_org.mpfb.services.locationservice import LocationService
    from bl_ext.blender_org.mpfb.services.targetservice import TargetService
    return AssetService, LocationService, TargetService


def human_info_targets():
    """FACE_TARGETS in the form MPFB's human_info['targets'] takes."""
    return [{"target": name, "value": value} for name, value in FACE_TARGETS]


def _image(path):
    img = bpy.data.images.load(str(path), check_existing=True)
    return img


def _textured(name, image_path, flat, *, alpha=False, rough=0.5, sss=None, tint=None, blend=None):
    m = bpy.data.materials.new("Castle " + name)
    m.diffuse_color = (*flat, 1.0)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = _image(image_path)
    tex.location = (-700, 200)
    colour = tex.outputs["Color"]
    if blend is not None:                   # (second image, its share): a mix of two diffuses
        tex2 = nt.nodes.new("ShaderNodeTexImage")
        tex2.image = _image(blend[0])
        tex2.location = (-700, -100)
        mix = nt.nodes.new("ShaderNodeMix")
        mix.data_type = "RGBA"
        mix.inputs["Factor"].default_value = blend[1]
        nt.links.new(colour, mix.inputs[6])
        nt.links.new(tex2.outputs["Color"], mix.inputs[7])
        colour = mix.outputs[2]
    if tint is not None:
        mix = nt.nodes.new("ShaderNodeMix")
        mix.data_type, mix.blend_type = "RGBA", "MULTIPLY"
        mix.inputs["Factor"].default_value = 1.0
        mix.inputs[7].default_value = (*tint, 1.0)
        nt.links.new(colour, mix.inputs[6])
        colour = mix.outputs[2]
    nt.links.new(colour, bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = rough
    if alpha:
        nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
        m.surface_render_method = "DITHERED"
    if sss is not None:
        bsdf.inputs["Subsurface Weight"].default_value = sss
        bsdf.inputs["Subsurface Radius"].default_value = (1.0, 0.35, 0.2)
        bsdf.inputs["Subsurface Scale"].default_value = 0.004          # metres
    return m


def _assign(ob, mat):
    ob.data.materials.clear()
    ob.data.materials.append(mat)
    ob.color = mat.diffuse_color


def _smile(body):
    """Load the mouth expression units and mask the smile to one side with vertex groups."""
    _, LocationService, TargetService = _mpfb()
    units = os.path.join(LocationService.get_mpfb_data("targets"), "expression", "units", "caucasian")
    xs = [v.co.x for v in body.data.vertices]
    span = 0.03                     # metres across the lips over which the smile fades out
    groups = {}
    for side, sign in (("left", 1.0), ("right", -1.0)):
        vg = body.vertex_groups.new(name="Castle smile " + side)
        for v, x in zip(body.data.vertices, xs):
            w = min(max(0.5 + sign * x / span, 0.0), 1.0)
            if w > 0:
                vg.add([v.index], w, "REPLACE")
        groups[side] = vg.name
    for side, weight in (("left", SMILE_LEFT), ("right", SMILE_RIGHT)):
        key = TargetService.load_target(body, os.path.join(units, "mouth-corner-puller.target.gz"),
                                        weight=weight, name="Castle smile " + side)
        key.vertex_group = groups[side]
    TargetService.load_target(body, os.path.join(units, "mouth-compression.target.gz"),
                              weight=SMILE_PRESS, name="Castle smile press")


def dress_face(body, parts):
    """The smile, then textured skin, eyes, eyebrows and eyelashes. parts: rig's mesh children."""
    AssetService, _, _ = _mpfb()
    _smile(body)

    def diffuse(skin):
        d = os.path.dirname(AssetService.find_asset_absolute_path(skin + ".mhmat", asset_subdir="skins"))
        return os.path.join(d, next(f for f in os.listdir(d) if f.endswith("_diffuse.png")))

    _assign(body, _textured("skin", diffuse(SKIN), SKIN_FLAT, rough=0.55, sss=0.12, tint=SKIN_TINT,
                            blend=(diffuse(SKIN_AGED[0]), SKIN_AGED[1])))

    def asset_png(subdir, mhclo, png):
        return os.path.join(os.path.dirname(AssetService.find_asset_absolute_path(mhclo, asset_subdir=subdir)), png)

    eye_mat = AssetService.find_asset_absolute_path(EYE + ".mhmat", asset_subdir="eyes")
    for ob in parts:
        key = ob.name.split(" ", 1)[-1]
        if key.startswith("high-poly"):
            _assign(ob, _textured("eye", os.path.join(os.path.dirname(eye_mat), EYE + "_eye.png"),
                                  (0.30, 0.31, 0.28), alpha=True, rough=0.15, tint=EYE_TINT))
        elif key.startswith("eyebrow"):
            _assign(ob, _textured("eyebrows", asset_png("eyebrows", key + ".mhclo", key + ".png"),
                                  (0.10, 0.075, 0.06), alpha=True, rough=0.7, tint=(0.3, 0.24, 0.2)))
        elif key.startswith("eyelashes"):
            _assign(ob, _textured("eyelashes", asset_png("eyelashes", key + ".mhclo", key + ".png"),
                                  (0.06, 0.05, 0.045), alpha=True, rough=0.7))


def pack():
    """Pack every image into the .blend: MakeHuman's textures live in one machine's profile."""
    for img in bpy.data.images:
        if img.source == "FILE" and not img.packed_file:
            img.pack()
