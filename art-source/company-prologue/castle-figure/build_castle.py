"""Castle as a 3D figure: a MakeHuman body built by MPFB, rigged and posed, in metres.

    python build_castle.py [--hair mesh|curves] [--face portrait|plain] [--out PATH]   # Blender 5.2, headless
    blender --background --python build_castle.py -- [--hair mesh|curves] [--face portrait|plain] [--out PATH]

--face portrait (the default) shapes her face, half-smile and textured skin (castle_face.py);
--face plain is MakeHuman's default face for her age, in flat colours.

--hair mesh (the default) is MakeHuman's short04 cards with a modelled bun; --hair curves grooms
Blender hair curves instead (grow_curves_hair), a prototype the owner is still choosing between.

Castle is the company's shipboard operations officer (`art-source/portraits/castle/prompt.txt`):
a woman in her early fifties, warm pale olive skin, dark brown hair going silver at the
temples in a short practical bun, a worn charcoal-olive high-collared duty jacket over a faded
undershirt, dark work trousers and boots, a small earpiece with a short boom microphone. C2
films her from Second Wind's bridge, standing at the port comms bank on the hail; close shots
are repainted over these frames with ImageGen (`geometry-first-imagegen.md`), so the figure
carries the silhouette, proportions, pose and lighting, not the finish.

What this builds, in the file's own frame (metres, z up from the soles, she faces +X: the
mannequin convention in `sw_bridge_interior.figures`):

- collection 'Castle figure', holding everything below; append it, never link pieces of it;
- 'Castle figure root', an empty at her feet: place and turn this, nothing else;
- 'Castle rig', MPFB's default skeleton (MakeHuman 'default' rig with its weights), posed and
  keyed through C2;
- the body, eyes, eyebrows, hair and clothes MPFB fits to it, from MakeHuman's own CC0
  system-asset pack (`NOTES` in the review run lists them), each skinned to the rig;
- what that pack has no piece for, modelled here and parented to bones: the bun, the
  jacket's stand collar, the earpiece and its boom.

She stands at the console with a little weight on the right leg, and plays her part in C2 as
the action 'Castle reads back and braces' on C2's frames (frame = C2 seconds x 24): both hands
on the bank and her head bowed to the screens while Approach talks; up and out through the
port slits as the Legion comes abeam; her right hand at the earpiece and her left keying the
bank for her read-back; a small brace as the deck pitches down; her hand down again after.
Each state is solved here rather than keyed by hand, so it can be re-aimed: each arm is a
two-bone solve from the shoulder to a wrist target with the elbow pushed toward a pole, and
the hand is turned to a named finger direction and palm normal. Targets are given relative
to measured landmarks of the built body (the ear, the console's top in front of her), so a
different phenotype keeps her hand on her ear.

Materials are flat colours (diffuse_color for Workbench, the same base colour in a Principled
BSDF): MPFB's skin and clothes materials reference textures inside one machine's Blender
profile, and the finish comes from the ImageGen repaint, not from these. The .blend is a
build output (gitignored); rebuild it, or copy it from Drive (starsector/builds/castle/).

Requires the MPFB extension (2.0.17 tested) and the MakeHuman system-asset pack installed in
the Blender this runs under; it fails with a message naming what is missing.
"""
import math
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BLEND = HERE / "castle-figure.blend"
COLLECTION = "Castle figure"
ROOT = "Castle figure root"

# ---------------------------------------------------------------- who she is (MakeHuman macros)
# MakeHuman's age slider is piecewise: 0.5 is 25 years, 1.0 is 90. Early fifties is about 0.71.
PHENOTYPE = dict(gender=0.0, age=0.71, muscle=0.55, weight=0.52, proportions=0.6, height=0.62,
                 cupsize=0.45, firmness=0.45,
                 race=dict(caucasian=0.72, asian=0.12, african=0.16))      # warm pale olive
ASSETS = dict(eyes="high-poly.mhclo", eyebrows="eyebrow008.mhclo", eyelashes="eyelashes01.mhclo",
              hair="short04.mhclo")                    # swept back; the bun is added below
CLOTHES = ["male_casualsuit02.mhclo", "shoes02.mhclo"]  # long-sleeved top over trousers; boots

COLOURS = {          # linear-ish RGB for Workbench; read against the portrait's palette
    "skin": (0.56, 0.40, 0.31),
    "hair": (0.10, 0.075, 0.06),
    "jacket": (0.13, 0.14, 0.11),       # charcoal-olive
    "trousers": (0.075, 0.075, 0.07),
    "boots": (0.06, 0.05, 0.045),
    "eye": (0.30, 0.31, 0.28),
    "headset": (0.05, 0.05, 0.055),
    "headset band": (0.25, 0.24, 0.22),
}

# ---------------------------------------------------------------- the C2 stance (MPFB frame)
# MPFB builds her facing -Y with her left on +X. Positions below are in that frame, metres,
# relative to the floor. The comms bank's operator face stands 0.325 m in front of her toes'
# root (Castle at y 3.25, face at y 3.75 Second Wind units, 0.65 m each) and its top is
# 1.07 m up (sw_bridge_interior.CONSOLE_TOP), sloping up away from her.
CONSOLE_FRONT = 0.325
CONSOLE_TOP = 1.07
LEAN_DEG = 6.0          # bent a little toward the console from the lower back
HEAD_BOW_DEG = 12.0     # eyes on the screens
HEAD_TURN_DEG = -6.0    # a little toward her right hand
# Left hand: flat on the console's sloped face just past its near edge. The face is tipped 16
# degrees, high edge away from her, and its near edge stands about 4 cm above the casing top.
LEFT_WRIST = (0.17, -(CONSOLE_FRONT + 0.03), CONSOLE_TOP + 0.085)
LEFT_POLE = (0.55, 0.25, 1.0)                 # elbow out and back
LEFT_FINGERS = (-0.12, -1.0, 0.29)            # up the slope
LEFT_PALM = (0.0, -0.28, -1.0)                # palm on the face
# Right hand: up at the earpiece. The wrist sits below and in front of the ear, the fingers
# point up at it with the palm toward the head.
RIGHT_WRIST_FROM_EAR = (-0.045, -0.06, -0.12)
RIGHT_POLE = (-0.6, -0.25, 1.0)               # elbow out to her right and forward
RIGHT_FINGERS = (0.25, 0.25, 1.0)             # up, a little in and back toward the ear
RIGHT_PALM = (1.0, 0.1, 0.0)                  # toward the head
# Right hand at rest: on the console beside the left, mirrored.
RIGHT_REST_WRIST = (-0.17, -(CONSOLE_FRONT + 0.03), CONSOLE_TOP + 0.085)
RIGHT_REST_POLE = (-0.55, 0.25, 1.0)
RIGHT_REST_FINGERS = (0.12, -1.0, 0.29)

# ---------------------------------------------------------------- her part in C2 (owner, 2026-10-19)
# Small and purposeful: she listens to Approach with both hands on the bank and her eyes on the
# screens; looks up and out through the port slits as the Legion comes abeam (8.0 s, under
# "Patrol has your signature"); brings her hand to the earpiece and keys the bank for her
# read-back ("Corridor four, mounts cold. Thank you, Approach.", 9.76-13.76 s); braces a little
# as the de-orbit pitches the ship nose-down (from 9.6 s); and lowers her hand when she is done.
# Each state: lean (degrees forward from the lower back), head bow (positive: down) and turn
# (positive: to her left), where the right hand is ('console' or 'ear'), and the curl of her
# left and right fingers (the left presses the transmit key while she speaks, and grips as
# the deck tips).
LISTENING = dict(lean=LEAN_DEG, bow=HEAD_BOW_DEG, turn=HEAD_TURN_DEG, right="console", curl=(12, 14))
LOOKING = dict(lean=3.0, bow=-4.0, turn=2.0, right="console", curl=(12, 14))
SPEAKING = dict(lean=5.0, bow=5.0, turn=HEAD_TURN_DEG, right="ear", curl=(30, 28))
BRACED = dict(SPEAKING, lean=-1.5, bow=7.0, curl=(48, 28))
# When, in C2 seconds (frame = t x 24): each (rise, fall) span is eased.
LOOK_SPAN = ((7.1, 7.8), (9.2, 9.9))
SPEAK_SPAN = ((9.25, 9.75), (13.9, 14.6))
BRACE_SPAN = ((9.7, 10.7), (12.8, 13.9))
FPS, LAST_FRAME = 24, 18 * 24
BREATH_S, BREATH_DEG = 3.8, 0.7

# ---------------------------------------------------------------- groomed hair (--hair curves)
# Blender's own procedural hair node groups (CC0, bundled with Blender) do the grooming; this
# script only lays the guides: one per scalp vertex, gathered over the scalp into the bun, arcs
# wrapping the bun's core, and the loose fringe and wisps. Lengths in metres.
HAIR_ASSETS = "assets/nodes/procedural_hair_node_assets.blend"     # under Blender's datafiles
HAIR_DENSITY = 7.0e5          # interpolated strands per m2 of scalp
HAIR_RADIUS = 0.00012         # root radius; the profile tapers to the tip
HAIRLINE_REACH = 0.012       # scalp is the skin within this of short04's cards
NAPE_DROP = 0.085            # the nape hairline, below eye height (short04 runs to the collar)
SIDEBURN_DROP = 0.015        # in front of the ear, no scalp below this under eye height
HAIRLINE_SOFT = (0.15, 0.4, 0.7)   # strand density on the scalp's edge ring and the next two in
HAIR_LIFT = (0.0015, 0.007)  # off the scalp at the root, and by the bun: volume, not slicked flat
WOBBLE = 0.0025              # a guide's sideways wander on its way up, for a gathered look
# The bun: up the back of the head at this elevation from the head's centre, its centre this far
# off the scalp, its half-widths across, up the head and outward (a fist-sized updo).
BUN_ELEVATION = 14.0
BUN_LIFT = 0.03
BUN_RADII = (0.046, 0.038, 0.034)
BUN_REACH = 0.05             # scalp guides stop combing and tuck in once this near its centre
BUN_GUIDES, BUN_COPIES = 240, 12
# The loose hair: fringe guides rooted over the front of the crown, this deep from the brow and
# between these x (her right is -x), the share left hanging at the cheek, and wisps at her right
# and left temple.
FRINGE_DEPTH, FRINGE_X = 0.05, (-0.045, 0.05)
FRINGE_ROOTS, FRINGE_HANG = 200, 0.3
FRINGE_LOW = 0.045            # the fringe stays above this height over eye level (the brow)
WISPS = (9, 0)
LOOSE_COPIES = 16
# Silver at the temples: strands rooted within these distances of either temple are grey,
# fading out between them; her right temple at this share (the art greys her left most), a
# share of fringe strands at FRINGE_GREY, and a sprinkle of faintly grey strands everywhere.
TEMPLE_GREY = (0.006, 0.026)
TEMPLE_RIGHT = 0.6
FRINGE_GREY = 0.5
SALT, SALT_GREY = 0.015, 0.35   # share of stray grey strands elsewhere, and how grey
MELANIN = dict(dark=0.72, grey=0.10, redness_dark=0.30, redness_grey=0.10)
HAIR_RGB = dict(dark=(0.028, 0.017, 0.011), grey=(0.21, 0.205, 0.2))   # EEVEE: direct colour


def _node_mod(ob, group, values):
    """Add `group` as a geometry-nodes modifier and set its inputs by their interface names."""
    mod = ob.modifiers.new(group.name, "NODES")
    mod.node_group = group
    ids = {i.name: i.identifier for i in group.interface.items_tree
           if i.item_type == "SOCKET" and i.in_out == "INPUT"}
    for name, value in values.items():
        socket = getattr(mod.properties.inputs, ids[name])    # Blender 5: no ID properties here
        if isinstance(value, tuple) and value[0] == "attribute":
            socket.type, socket.attribute_name = "ATTRIBUTE", value[1]
        else:
            socket.value = value
    return mod


def _grey_group():
    """A node group storing each strand's 'grey': its guides' 'temple' greying (interpolated onto
    the children), or SALT_GREY for a sprinkle of random strands."""
    import bpy
    g = bpy.data.node_groups.new("Castle hair grey", "GeometryNodeTree")
    g.interface.new_socket("Geometry", in_out="INPUT", socket_type="NodeSocketGeometry")
    g.interface.new_socket("Geometry", in_out="OUTPUT", socket_type="NodeSocketGeometry")
    n, link = g.nodes, g.links.new
    gin, gout = n.new("NodeGroupInput"), n.new("NodeGroupOutput")
    store = n.new("GeometryNodeStoreNamedAttribute")
    store.data_type, store.domain = "FLOAT", "CURVE"
    store.inputs["Name"].default_value = "grey"
    temple = n.new("GeometryNodeInputNamedAttribute")
    temple.data_type = "FLOAT"
    temple.inputs["Name"].default_value = "temple"
    rnd = n.new("FunctionNodeRandomValue")
    rnd.data_type = "FLOAT"
    rnd.inputs["Seed"].default_value = 7
    salt = n.new("ShaderNodeMath")
    salt.operation = "LESS_THAN"
    salt.inputs[1].default_value = SALT
    link(rnd.outputs[0], salt.inputs[0])
    salt_grey = n.new("ShaderNodeMath")
    salt_grey.operation = "MULTIPLY"
    salt_grey.inputs[1].default_value = SALT_GREY
    link(salt.outputs[0], salt_grey.inputs[0])
    mx = n.new("ShaderNodeMath")
    mx.operation = "MAXIMUM"
    link(temple.outputs[0], mx.inputs[0])
    link(salt_grey.outputs[0], mx.inputs[1])
    link(gin.outputs[0], store.inputs["Geometry"])
    link(mx.outputs[0], store.inputs["Value"])
    link(store.outputs[0], gout.inputs[0])
    return g


def _deform_group():
    """Deform Curves on Surface, as a node group the groom's modifier stack can hold."""
    import bpy
    g = bpy.data.node_groups.new("Castle hair follows the scalp", "GeometryNodeTree")
    g.interface.new_socket("Geometry", in_out="INPUT", socket_type="NodeSocketGeometry")
    g.interface.new_socket("Geometry", in_out="OUTPUT", socket_type="NodeSocketGeometry")
    gin, gout = g.nodes.new("NodeGroupInput"), g.nodes.new("NodeGroupOutput")
    deform = g.nodes.new("GeometryNodeDeformCurvesOnSurface")
    g.links.new(gin.outputs[0], deform.inputs[0])
    g.links.new(deform.outputs[0], gout.inputs[0])
    return g


def _hair_material():
    """Principled Hair BSDF greyed by each strand's 'grey' attribute; flat for Workbench.

    Two outputs: by melanin for Cycles, and by direct colour for EEVEE, which ignores the
    melanin parametrisation (it renders the BSDF's default near-black whatever the melanin).
    """
    import bpy
    m = bpy.data.materials.new("Castle hair groom")
    m.diffuse_color = (*COLOURS["hair"], 1.0)
    m.use_nodes = True
    t = m.node_tree
    t.nodes.clear()
    grey = t.nodes.new("ShaderNodeAttribute")
    grey.attribute_type, grey.attribute_name = "GEOMETRY", "grey"
    for target, parametrization in (("CYCLES", "MELANIN"), ("EEVEE", "COLOR")):
        out = t.nodes.new("ShaderNodeOutputMaterial")
        out.target = target
        bsdf = t.nodes.new("ShaderNodeBsdfHairPrincipled")
        bsdf.parametrization = parametrization
        bsdf.inputs["Roughness"].default_value = 0.28
        bsdf.inputs["Radial Roughness"].default_value = 0.4
        bsdf.inputs["Coat"].default_value = 0.08
        bsdf.inputs["Random Roughness"].default_value = 0.15
        if parametrization == "MELANIN":
            bsdf.inputs["Random Color"].default_value = 0.12
            for socket, lo, hi in (("Melanin", MELANIN["dark"], MELANIN["grey"]),
                                   ("Melanin Redness", MELANIN["redness_dark"], MELANIN["redness_grey"])):
                mr = t.nodes.new("ShaderNodeMapRange")
                mr.inputs["To Min"].default_value, mr.inputs["To Max"].default_value = lo, hi
                t.links.new(grey.outputs["Fac"], mr.inputs["Value"])
                t.links.new(mr.outputs["Result"], bsdf.inputs[socket])
        else:
            mix = t.nodes.new("ShaderNodeMix")
            mix.data_type = "RGBA"
            mix.inputs["A"].default_value = (*HAIR_RGB["dark"], 1.0)
            mix.inputs["B"].default_value = (*HAIR_RGB["grey"], 1.0)
            t.links.new(grey.outputs["Fac"], mix.inputs["Factor"])
            t.links.new(mix.outputs["Result"], bsdf.inputs["Color"])
        t.links.new(bsdf.outputs[0], out.inputs["Surface"])
    return m


def grow_curves_hair(body, rig, hair_mesh, eye_z):
    """Groom her hair as Blender hair curves pulled back into a short bun.

    Built in the rest pose, in the file's MPFB frame (she faces -Y, back is +Y). The groom rides
    a hidden copy of the scalp skin that the rig deforms like the body (Deform Curves on Surface),
    because the nape is weighted to the neck: hair fixed to the head bone sinks into it when she
    bows her head. Returns what rides the head bone instead: the bun, its core and the loose
    fringe and wisps.

    The look is castle-v2.master.png's: a loose, fist-sized updo high on the back of the crown,
    hair gathered up into it with volume on top, a fringe swept to her right that hangs at the
    temple, a few wisps at the cheeks, and silver sweeping back from the temples.
    """
    import random
    import bmesh
    import bpy
    from mathutils import Matrix, Vector, noise
    from mathutils.bvhtree import BVHTree

    lib = Path(bpy.utils.system_resource("DATAFILES")) / HAIR_ASSETS
    names = ["Attach Hair Curves to Surface", "Interpolate Hair Curves", "Clump Hair Curves",
             "Frizz Hair Curves", "Hair Curves Noise", "Set Hair Curve Profile", "Duplicate Hair Curves"]
    with bpy.data.libraries.load(str(lib)) as (src, dst):
        missing = [x for x in names if x not in src.node_groups]
        dst.node_groups = [x for x in names if x in src.node_groups]
    if missing:
        sys.exit("Blender's hair node assets lack %s (%s)" % (missing, lib))
    groups = {x: bpy.data.node_groups[x] for x in names}
    scene = bpy.context.scene
    rng = random.Random(1951)

    # ---- the scalp: the skin under MakeHuman's short04 hair, which draws a natural hairline
    # (the pack's 'scalp' vertex group is only the nape), real body verts only, ears left out
    bi, ei = body.vertex_groups["body"].index, body.vertex_groups["ears"].index

    def weight(v, idx):
        return next((g.weight for g in v.groups if g.group == idx), 0.0)

    hm = hair_mesh.matrix_world
    cover = BVHTree.FromPolygons([hm @ v.co for v in hair_mesh.data.vertices],
                                 [tuple(p.vertices) for p in hair_mesh.data.polygons])
    # The body's shape is its phenotype shape keys; its vertex coordinates are MakeHuman's
    # neutral base. Read the key mix with the modifiers (armature, helper mask, subdivision)
    # switched off, so vertex indices still match.
    shown = [(m, m.show_viewport) for m in body.modifiers]
    for m, _ in shown:
        m.show_viewport = False
    bpy.context.view_layer.update()
    ev = body.evaluated_get(bpy.context.evaluated_depsgraph_get())
    shaped = [body.matrix_world @ v.co for v in ev.data.vertices]
    for m, s in shown:
        m.show_viewport = s
    bpy.context.view_layer.update()
    # In front of the ear, short04 runs down into a sideburn; the art has the hair swept up
    # above the ear there.
    ear_front = min(shaped[v.index].y for v in body.data.vertices if weight(v, ei) > 0.5)

    def sideburn(c):
        return abs(c.x) > 0.04 and c.z < eye_z - SIDEBURN_DROP and c.y < ear_front + 0.01

    keep = {v.index for v in body.data.vertices
            if weight(v, bi) > 0.5 and weight(v, ei) < 0.5 and shaped[v.index].z > eye_z - NAPE_DROP
            and not sideburn(shaped[v.index])
            and cover.find_nearest(shaped[v.index], HAIRLINE_REACH)[0] is not None}
    # short04 stands off the skin down the middle of the nape: close holes a few rings across.
    ring = {}
    for e in body.data.edges:
        a, b = e.vertices
        ring.setdefault(a, []).append(b)
        ring.setdefault(b, []).append(a)
    allowed = {v.index for v in body.data.vertices
               if weight(v, bi) > 0.5 and weight(v, ei) < 0.5 and shaped[v.index].z > eye_z - NAPE_DROP
               and not sideburn(shaped[v.index])}
    grown = set(keep)
    for _ in range(3):
        grown |= {n for i in grown for n in ring.get(i, ()) if n in allowed}
    for _ in range(3):
        grown = {i for i in grown if i in keep or all(n in grown for n in ring.get(i, ()))}
    keep = grown
    bm = bmesh.new()
    bm.from_mesh(body.data)
    bm.verts.ensure_lookup_table()
    for v in bm.verts:
        v.co = body.matrix_world.inverted() @ shaped[v.index]
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.index not in keep], context="VERTS")
    bm.transform(body.matrix_world)
    me = bpy.data.meshes.new("Castle scalp")
    bm.to_mesh(me)
    bm.free()
    scalp = bpy.data.objects.new("Castle scalp", me)
    scene.collection.objects.link(scalp)
    scalp.hide_render = True
    scalp.display_type = "WIRE"
    for vg in body.vertex_groups:              # same order, so the copied weights line up
        scalp.vertex_groups.new(name=vg.name)
    sv = [v.co.copy() for v in me.vertices]
    rest = me.attributes.new("rest_position", "FLOAT_VECTOR", "POINT")
    rest.data.foreach_set("vector", [c for v in sv for c in v])
    scalp.modifiers.new("Armature", "ARMATURE").object = rig
    bvh = BVHTree.FromPolygons(sv, [tuple(p.vertices) for p in me.polygons])
    centre = Vector((0.0, sum(v.y for v in sv) / len(sv), eye_z))

    def surface(p):
        loc, nrm, _, _ = bvh.find_nearest(p)
        if nrm.dot(loc - centre) < 0:
            nrm = -nrm
        return loc, nrm

    # The temple: the front of the hairline at her side, between the ear's top and the brow.
    side = [v for v in sv if v.x > 0.05 and eye_z - 0.005 < v.z < eye_z + 0.035]
    temple = min(side, key=lambda v: v.y)

    def curves_object(name, strands):
        cv = bpy.data.hair_curves.new(name)
        cv.add_curves([len(s) for s in strands])
        cv.attributes["position"].data.foreach_set("vector", [c for s in strands for p in s for c in p])
        ob = bpy.data.objects.new(name, cv)
        scene.collection.objects.link(ob)
        return ob

    # A softer hairline: fewer strands on the scalp's outer rings.
    order = sorted(keep)
    edge = {i for i in keep if any(n not in keep for n in ring.get(i, ()))}
    rings = [edge]
    for _ in HAIRLINE_SOFT[1:]:
        done = set().union(*rings)
        rings.append({i for i in keep - done if any(n in rings[-1] for n in ring.get(i, ()))})
    soft = {i: d for band, d in zip(rings, HAIRLINE_SOFT) for i in band}
    me.attributes.new("hair_density", "FLOAT", "POINT").data.foreach_set(
        "value", [soft.get(i, 1.0) for i in order])
    on_edge = [i in edge for i in order]

    # The head itself, for keeping loose strands (fringe, wisps) off the skin.
    near_head = {v.index for v in body.data.vertices
                 if weight(v, bi) > 0.5 and shaped[v.index].z > eye_z - 0.16}
    head_polys = [tuple(p.vertices) for p in body.data.polygons
                  if all(i in near_head for i in p.vertices)]
    head_bvh = BVHTree.FromPolygons(shaped, head_polys)
    head_c = Vector((0.0, centre.y, eye_z + 0.02))

    def clear(p, gap):
        """Push `p` out of the head until it stands `gap` off the skin."""
        loc, nrm, _, _ = head_bvh.find_nearest(p)
        if loc is None:
            return p
        if nrm.dot(loc - head_c) < 0:
            nrm = -nrm
        d = (p - loc).dot(nrm)
        return p + nrm * (gap - d) if d < gap else p

    def spline(ctrl, n):
        """A Catmull-Rom curve through `ctrl`, `n` points."""
        c = [ctrl[0] * 2 - ctrl[1], *ctrl, ctrl[-1] * 2 - ctrl[-2]]
        out = []
        for k in range(n):
            t = k / (n - 1) * (len(ctrl) - 1)
            i = min(int(t), len(ctrl) - 2)
            s = t - i
            p0, p1, p2, p3 = c[i], c[i + 1], c[i + 2], c[i + 3]
            out.append(0.5 * (2 * p1 + (p2 - p0) * s + (2 * p0 - 5 * p1 + 4 * p2 - p3) * s * s
                              + (3 * p1 - p0 - 3 * p2 + p3) * s * s * s))
        return out

    def jitter(r):
        return Vector((rng.uniform(-r, r), rng.uniform(-r, r), rng.uniform(-r, r)))

    # ---- the bun: a loose, gathered updo high on the back of the crown (castle-v2.master.png)
    up = Vector((0.0, math.cos(math.radians(BUN_ELEVATION)), math.sin(math.radians(BUN_ELEVATION))))
    hit, n_hit, _, _ = bvh.ray_cast(Vector((0.0, centre.y, eye_z + 0.02)), up)
    if n_hit.dot(up) < 0:
        n_hit = -n_hit
    axis = n_hit.normalized()
    u = (Vector((1.0, 0.0, 0.0)) - axis * axis.x).normalized()
    w = axis.cross(u)
    bun_c = hit + axis * BUN_LIFT
    ru, rw, ra = BUN_RADII

    def on_bun(d, scale=1.0):
        """The point of the bun's ellipsoid in local direction `d`, kept above the scalp."""
        d = d.normalized()
        p = bun_c + u * (d.x * ru * scale) + w * (d.y * rw * scale) + axis * (d.z * ra * scale)
        h = (p - hit).dot(axis)
        return p + axis * (0.004 - h) if h < 0.004 else p

    # A lumpy core under the strands, so the bun reads solid rather than see-through.
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=24, v_segments=14, radius=1.0)
    for v in bm.verts:
        d = v.co.normalized()
        lump = 0.9 + 0.12 * noise.noise(d * 2.2 + Vector((3.1, 0.7, 5.3)))
        v.co = (on_bun(d, 0.86 * lump) - bun_c)
    core_me = bpy.data.meshes.new("Castle hair bun core")
    bm.to_mesh(core_me)
    bm.free()
    core = bpy.data.objects.new("Castle hair bun core", core_me)
    core.location = bun_c
    scene.collection.objects.link(core)
    core_mat = bpy.data.materials.new("Castle hair bun core")
    core_mat.diffuse_color = (*COLOURS["hair"], 1.0)
    core_mat.use_nodes = True
    core_bsdf = core_mat.node_tree.nodes.get("Principled BSDF")
    core_bsdf.inputs["Base Color"].default_value = (*HAIR_RGB["dark"], 1.0)
    core_bsdf.inputs["Roughness"].default_value = 0.6
    core_me.materials.append(core_mat)
    for f in core_me.polygons:
        f.use_smooth = True

    # Gathered strands wrapping the core along random arcs.
    coil = []
    for _ in range(BUN_GUIDES):
        a = Vector((rng.gauss(0, 1), rng.gauss(0, 1), rng.gauss(0, 1))).normalized()
        s0 = a.orthogonal().normalized()
        span = math.radians(rng.uniform(140, 320))
        rf = rng.uniform(1.0, 1.16)
        pts = []
        for k in range(32):
            th = span * k / 31
            d = Matrix.Rotation(th, 3, a) @ s0
            pts.append(tuple(on_bun(d, rf + 0.05 * noise.noise(d * 4 + a * 7))))
        coil.append(pts)

    # ---- the scalp: one guide per vertex, gathered up over the head into the bun, with volume
    def tuck():
        d = Vector((rng.gauss(0, 1), rng.gauss(0, 1), -abs(rng.gauss(0, 0.6)) - 0.3))
        return on_bun(d, 0.8)

    guides = []
    for root in sv:
        pts, p, travelled = [root.copy()], root.copy(), 0.0
        reach = (bun_c - root).length
        wob = w.cross(Vector((rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1)))).normalized()
        phase = rng.uniform(0, 2 * math.pi)
        for _ in range(90):
            if (p - bun_c).length < BUN_REACH:
                break
            loc, nrm = surface(p)
            to_b = bun_c - p
            t = to_b - nrm * to_b.dot(nrm)
            step = (t if t.length > 1e-6 else to_b).normalized() * 0.006
            loc, nrm = surface(p + step)
            travelled += step.length
            f = min(travelled / max(reach, 1e-3), 1.0)
            lift = HAIR_LIFT[0] + (HAIR_LIFT[1] - HAIR_LIFT[0]) * f ** 0.6
            sway = wob * (WOBBLE * math.sin(phase + travelled * 90.0) * min(f * 4, 1.0))
            p = loc + nrm * lift + sway
            pts.append(p.copy())
        end = tuck()
        pts += [p.lerp(end, 0.5) + axis * 0.006, end]
        guides.append([tuple(q) for q in pts])
    groom = curves_object("Castle hair", guides)
    groom.data.surface = scalp
    groom.data.surface_uv_map = "UVMap"

    # ---- loose hair: the fringe swept to her right, and wisps hanging at the temples
    def temple_of(sign):
        return min((v for v in sv if v.x * sign > 0.05 and eye_z - 0.005 < v.z < eye_z + 0.035),
                   key=lambda v: v.y)

    def onto(p, gap):
        """The skin point nearest `p`, stood `gap` off it."""
        loc, nrm, _, _ = head_bvh.find_nearest(p)
        if nrm.dot(loc - head_c) < 0:
            nrm = -nrm
        return loc + nrm * gap

    def settle(ctrl, n, gap0, gap1):
        """Spline through `ctrl`, smoothed, then kept off the skin (the gap growing root to tip)."""
        pts = spline(ctrl, n)
        for _ in range(3):
            pts = [pts[0]] + [(pts[k - 1] + pts[k] * 2 + pts[k + 1]) / 4 for k in range(1, n - 1)] + [pts[-1]]
            pts = [pts[0]] + [clear(p, gap0 + (gap1 - gap0) * min(k / 8, 1.0)) for k, p in enumerate(pts[1:], 1)]
        return pts

    temple_r = temple_of(-1)
    front_y = min(v.y for v in sv)
    front_edge = [v for v, e in zip(sv, on_edge) if e and v.y < centre.y - 0.03]

    def hairline_z(x):
        near = [v.z for v in front_edge if abs(v.x - x) < 0.01]
        return min(near) if near else eye_z + 0.05

    crown_front = [v for v in sv if v.y < front_y + FRINGE_DEPTH and FRINGE_X[0] < v.x < FRINGE_X[1]
                   and v.z > hairline_z(v.x) + 0.004]
    loose = []
    for root in rng.sample(crown_front, min(FRINGE_ROOTS, len(crown_front))):
        x2 = max(root.x - rng.uniform(0.02, 0.05), -0.058)
        x3 = (x2 + temple_r.x) / 2

        def brow_z(x, below):
            # Down over the hairline, never below the top of the brow.
            return max(hairline_z(x) - below, eye_z + FRINGE_LOW)

        # Combed forward along the scalp from the root, then across the top of the forehead.
        ctrl = [root, onto(root + Vector((x2 - root.x, -0.012, 0.0)) * 0.4, 0.006),
                onto(Vector((x2, front_y, brow_z(x2, rng.uniform(0.004, 0.012)))), 0.005),
                onto(Vector((x3, front_y, brow_z(x3, rng.uniform(0.006, 0.016)))), 0.005),
                onto(Vector((temple_r.x, temple_r.y + 0.004, eye_z + rng.uniform(0.015, 0.035))), 0.006)]
        if rng.random() < FRINGE_HANG:         # left hanging at the cheek
            ctrl.append(onto(Vector((temple_r.x, temple_r.y + rng.uniform(0.005, 0.02),
                                     eye_z - rng.uniform(0.035, 0.075))), 0.004))
        else:                                  # pinned back over the ear into the bun
            ctrl += [onto(Vector((temple_r.x, temple_r.y + 0.055, eye_z + 0.035)), 0.009), tuck()]
        loose.append(settle(ctrl, 36, 0.004, 0.007))
    for sign, count in ((-1, WISPS[0]), (1, WISPS[1])):
        t = temple_of(sign)
        side = [v for v, e in zip(sv, on_edge) if e and v.x * sign > 0.05 and t.y + 0.015 < v.y < t.y + 0.045
                and eye_z - 0.035 < v.z < eye_z + 0.02]
        for root in rng.sample(side, min(count, len(side))):
            drop = rng.uniform(0.05, 0.1)
            ctrl = [root, onto(root + Vector((0, -0.003, -0.02)), 0.003),
                    onto(root + Vector((0, -0.006, -drop * 0.6)), 0.003),
                    onto(root + Vector((0, -0.008, -drop)), 0.004)]
            loose.append(settle(ctrl, 22, 0.003, 0.004))
    fringe = curves_object("Castle hair loose", [[tuple(p) for p in s] for s in loose])
    bun = curves_object("Castle hair bun", coil)

    # Silver at the temples, by where each strand is rooted (strongest on her left, as in the
    # art); Interpolate carries the guides' value to their children.
    lo, hi = TEMPLE_GREY

    def temple_grey(r):
        side = 1.0 if r.x > 0 else TEMPLE_RIGHT
        return side * min(max((hi - (Vector((abs(r.x), r.y, r.z)) - temple).length) / (hi - lo), 0.0), 1.0)

    groom.data.attributes.new("temple", "FLOAT", "CURVE").data.foreach_set(
        "value", [temple_grey(r) for r in sv])
    fringe.data.attributes.new("temple", "FLOAT", "CURVE").data.foreach_set(
        "value", [FRINGE_GREY * (rng.random() < 0.12) for s in loose])

    grey = _grey_group()
    mat = _hair_material()
    for ob in (groom, bun, fringe):
        ob.data.materials.append(mat)
    _node_mod(groom, groups["Attach Hair Curves to Surface"],
              {"Surface Object": scalp, "Surface UV Map": ("attribute", "UVMap"), "Resting Surface": True})
    _node_mod(groom, groups["Interpolate Hair Curves"],
              {"Resting Surface": True, "Part by Mesh Islands": False, "Interpolation Guides": 5,
               "Density": HAIR_DENSITY, "Density Mask": ("attribute", "hair_density"), "Seed": 3})
    _node_mod(groom, groups["Clump Hair Curves"],
              {"Factor": 0.35, "Shape": 0.3, "Existing Guide Map": False, "Guide Distance": 0.008})
    _node_mod(bun, groups["Duplicate Hair Curves"], {"Amount": BUN_COPIES, "Radius": 0.003})
    _node_mod(fringe, groups["Duplicate Hair Curves"], {"Amount": LOOSE_COPIES, "Radius": 0.0025})
    for ob, frizz, wander in ((groom, 0.0008, 0.0012), (bun, 0.0012, 0.0012), (fringe, 0.0012, 0.0015)):
        _node_mod(ob, groups["Frizz Hair Curves"], {"Distance": frizz})
        _node_mod(ob, groups["Hair Curves Noise"], {"Distance": wander, "Scale": 45.0})
        _node_mod(ob, groups["Set Hair Curve Profile"], {"Radius": HAIR_RADIUS, "Shape": 0.5})
        ob.modifiers.new("Grey at the temples", "NODES").node_group = grey
    # Last, as Blender's own hair setup has it: the finished groom rides the posed scalp.
    follow = groom.modifiers.new("Follow the scalp", "NODES")
    follow.node_group, follow.use_pin_to_last = _deform_group(), True
    for ob in (scalp, groom):
        ob.parent = rig
        ob.matrix_parent_inverse = rig.matrix_world.inverted()
    return [bun, core, fringe]


def launch(argv):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    # Not --factory-startup: that would leave the MPFB extension disabled.
    cmd = [BLENDER, "--background", "--python", str(Path(__file__).resolve()), "--", *argv]
    sys.exit(subprocess.call(cmd))


def blender_main(argv):
    import bpy
    from mathutils import Matrix, Vector

    out = Path(argv[argv.index("--out") + 1]) if "--out" in argv else BLEND
    hair_mode = argv[argv.index("--hair") + 1] if "--hair" in argv else "mesh"
    if hair_mode not in ("mesh", "curves"):
        sys.exit("--hair is mesh (MakeHuman short04 and a modelled bun) or curves (a groom)")
    face_mode = argv[argv.index("--face") + 1] if "--face" in argv else "portrait"
    if face_mode not in ("portrait", "plain"):
        sys.exit("--face is portrait (castle_face: her face, smile and textured skin) or plain")
    sys.path.insert(0, str(HERE))
    import castle_face

    try:
        import addon_utils
        addon_utils.enable("bl_ext.blender_org.mpfb", default_set=True)
        from bl_ext.blender_org.mpfb.services.humanservice import HumanService
        from bl_ext.blender_org.mpfb.services.assetservice import AssetService
    except Exception as exc:     # noqa: BLE001
        sys.exit("MPFB is not installed in this Blender (Get Extensions > MPFB): %s" % exc)
    for part, name in [*((k, v) for k, v in ASSETS.items()), *(("clothes", c) for c in CLOTHES)]:
        if AssetService.find_asset_absolute_path(name, asset_subdir=part) is None:
            sys.exit("MakeHuman asset %s/%s is missing: install the MakeHuman system asset "
                     "pack in MPFB (Apply assets > Load pack)." % (part, name))

    # An empty scene, without a factory reset (which would also unload MPFB).
    for ob in list(bpy.data.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    for col in list(bpy.data.collections):
        bpy.data.collections.remove(col)
    scene = bpy.context.scene

    # ---- the body
    info = HumanService._create_default_human_info_dict()
    info["phenotype"] = PHENOTYPE
    info["name"] = "Castle"
    info["rig"] = "default"
    info.update(ASSETS)
    info["clothes"] = list(CLOTHES)
    info["skin_material_type"] = "NONE"
    if face_mode == "portrait":
        info["targets"] = castle_face.human_info_targets()
    settings = HumanService.get_default_deserialization_settings()
    settings.update(scale=0.1, subdiv_levels=1, override_skin_model="NONE")   # 0.1: dm to metres
    body = HumanService.deserialize_from_dict(info, settings)
    rig = body.parent
    rig.name = rig.data.name = "Castle rig"
    body.name = "Castle body"
    parts = [o for o in rig.children if o.type == "MESH"]
    for o in parts:
        if o is not body:
            o.name = "Castle " + o.name.split(".", 1)[-1]

    # ---- flat materials
    def flat(name):
        m = bpy.data.materials.get("Castle " + name) or bpy.data.materials.new("Castle " + name)
        rgb = COLOURS[name]
        m.diffuse_color = (*rgb, 1.0)
        m.roughness = 0.75
        m.use_nodes = True
        bsdf = m.node_tree.nodes.get("Principled BSDF")
        if bsdf is not None:
            bsdf.inputs["Base Color"].default_value = (*rgb, 1.0)
            bsdf.inputs["Roughness"].default_value = 0.75
        return m

    def paint(ob, name):
        ob.data.materials.clear()
        ob.data.materials.append(flat(name))
        ob.color = ob.data.materials[0].diffuse_color

    by_role = {"body": "skin", "high-poly": "eye", "eyebrow": "hair", "eyelashes": "hair",
               "short04": "hair", "shoes02": "boots", "male_casualsuit02": "jacket"}
    for o in parts:
        key = "body" if o is body else o.name.split(" ", 1)[1]
        paint(o, next(v for k, v in by_role.items() if key.startswith(k)))

    # The top-and-trousers suit is one mesh: faces below the hem around the hips take the
    # trousers colour (rest pose, arms hang well outside |x| 0.25).
    suit = bpy.data.objects["Castle male_casualsuit02"]
    suit.name = "Castle duty jacket and trousers"
    suit.data.materials.append(flat("trousers"))
    hem = 0.96
    for poly in suit.data.polygons:
        c = poly.center
        if c.z < hem and abs(c.x) < 0.25:
            poly.material_index = 1
    if face_mode == "portrait":
        castle_face.dress_face(body, parts)

    # ---- landmarks of the built body (rest pose, MPFB frame)
    verts = [body.matrix_world @ v.co for v in body.data.vertices]
    bones = rig.data.bones
    head_b = bones["head"]
    eye_z = (bones["eye.R"].head_local.z + bones["eye.L"].head_local.z) / 2
    band = [v for v in verts if abs(v.z - (eye_z - 0.02)) < 0.025 and v.y > -0.09]
    ear_r = min(band, key=lambda v: v.x)                       # her right ear, outermost point
    hair = bpy.data.objects["Castle short04"]
    hverts = [hair.matrix_world @ v.co for v in hair.data.vertices]
    nape_back = max(v.y for v in hverts if abs(v.x) < 0.03 and abs(v.z - eye_z) < 0.05)
    neck_c = bones["neck01"].head_local
    mouth = Vector((-0.024, bones["eye.R"].head_local.y - 0.005, eye_z - 0.075))

    # ---- what the pack has no piece for, bone-parented in the rest pose
    def bone_parent(ob, bone_name):
        b = bones[bone_name]
        ob.parent = rig
        ob.parent_type = "BONE"
        ob.parent_bone = bone_name
        # Bone parent space sits at the bone's tail, oriented with the bone.
        rest = rig.matrix_world @ Matrix.Translation(b.tail_local) @ b.matrix_local.to_3x3().to_4x4()
        ob.matrix_parent_inverse = rest.inverted()

    def add(op, name, mat, **kw):
        op(**kw)
        ob = bpy.context.object
        ob.name = ob.data.name = "Castle " + name
        paint(ob, mat)
        bpy.ops.object.shade_smooth()
        return ob

    if hair_mode == "mesh":
        bun = add(bpy.ops.mesh.primitive_uv_sphere_add, "bun", "hair", segments=20, ring_count=12,
                  radius=1.0, location=(0, nape_back - 0.004, eye_z - 0.015))
        bun.scale = (0.036, 0.026, 0.03)
        bone_parent(bun, "head")
    else:
        for ob in grow_curves_hair(body, rig, hair, eye_z):
            bone_parent(ob, "head")
        bpy.data.objects.remove(hair, do_unlink=True)

    collar = add(bpy.ops.mesh.primitive_cone_add, "duty jacket collar", "jacket", vertices=24,
                 radius1=0.075, radius2=0.066, depth=0.055, end_fill_type="NOTHING",
                 location=(neck_c.x, neck_c.y + 0.008, neck_c.z + 0.012))
    collar.rotation_euler = (math.radians(-8), 0, 0)
    collar.modifiers.new("Thickness", "SOLIDIFY").thickness = 0.006
    bone_parent(collar, "neck01")

    cup = add(bpy.ops.mesh.primitive_cylinder_add, "earpiece", "headset", vertices=20,
              radius=0.014, depth=0.012, location=(ear_r.x - 0.005, ear_r.y, ear_r.z),
              rotation=(0, math.radians(90), 0))
    bone_parent(cup, "head")
    boom_a = Vector((ear_r.x - 0.010, ear_r.y - 0.008, ear_r.z - 0.006))
    boom_b = mouth + Vector((-0.035, -0.015, 0.0))
    d = boom_b - boom_a
    boom = add(bpy.ops.mesh.primitive_cylinder_add, "boom microphone", "headset", vertices=10,
               radius=0.0032, depth=d.length, location=(boom_a + boom_b) / 2)
    boom.rotation_euler = d.to_track_quat("Z", "Y").to_euler()
    bone_parent(boom, "head")
    tip = add(bpy.ops.mesh.primitive_uv_sphere_add, "boom microphone capsule", "headset",
              segments=12, ring_count=8, radius=0.0055, location=boom_b)
    bone_parent(tip, "head")
    band_o = add(bpy.ops.mesh.primitive_torus_add, "earpiece hook", "headset band",
                 major_radius=0.019, minor_radius=0.0022, major_segments=24, minor_segments=6,
                 location=(ear_r.x + 0.002, ear_r.y + 0.008, ear_r.z + 0.012),
                 rotation=(0, math.radians(90), 0))
    bone_parent(band_o, "head")

    # ---- the pose
    view = bpy.context.view_layer
    pbs = rig.pose.bones

    def rotate_world(name, axis, deg):
        """Turn a pose bone (and so its children) about an armature-space axis through its head."""
        pb = pbs[name]
        h = pb.head.copy()
        r = Matrix.Rotation(math.radians(deg), 4, Vector(axis))
        pb.matrix = Matrix.Translation(h) @ r @ Matrix.Translation(-h) @ pb.matrix
        view.update()

    def frame(y, n):
        y = Vector(y).normalized()
        x = Vector(n).cross(y).normalized()
        z = x.cross(y)
        return Matrix((x, y, z)).transposed()

    def aim(name, direction):
        """Point a bone along `direction` by the smallest turn from its rest direction."""
        pb = pbs[name]
        rest = pb.bone.matrix_local.to_3x3()
        q = rest.col[1].rotation_difference(Vector(direction).normalized())
        m = (q.to_matrix() @ rest).to_4x4()
        m.translation = pb.head
        pb.matrix = m
        view.update()

    def orient(name, fingers, palm, rest_palm):
        """Turn a hand so its bone axis runs along `fingers` and its palm faces `palm`."""
        pb = pbs[name]
        rest = pb.bone.matrix_local.to_3x3()
        q = frame(fingers, palm) @ frame(rest.col[1], rest_palm).transposed()
        m = (q @ rest).to_4x4()
        m.translation = pb.head
        pb.matrix = m
        view.update()

    def two_bone(side, wrist, pole):
        """Shoulder-to-wrist solve on the MakeHuman arm (two segments of two bones each)."""
        up = ["upperarm01." + side, "upperarm02." + side]
        lo = ["lowerarm01." + side, "lowerarm02." + side]
        a = sum(bones[b].length for b in up)
        b = sum(bones[x].length for x in lo)
        s = pbs[up[0]].head.copy()
        w = Vector(wrist)
        d = w - s
        L = min(d.length, (a + b) * 0.999)
        dn = d.normalized()
        # Elbow distance along the reach and off it (law of cosines), off toward the pole.
        along = (a * a - b * b + L * L) / (2 * L)
        off = math.sqrt(max(a * a - along * along, 0.0))
        p = Vector(pole) - s
        perp = (p - dn * p.dot(dn)).normalized()
        elbow = s + dn * along + perp * off
        for name in up:
            aim(name, elbow - s)
        wrist_pt = elbow + (w - elbow).normalized() * b
        for name in lo:
            aim(name, wrist_pt - elbow)
        return wrist_pt

    def curl(side, finger_deg, thumb_deg, palm):
        n = Vector(palm).normalized()
        for f in range(1, 6):
            deg = thumb_deg if f == 1 else finger_deg
            for j in range(1, 4):
                name = "finger%d-%d.%s" % (f, j, side)
                pb = pbs[name]
                axis = (pb.tail - pb.head).normalized().cross(n)
                # Whichever way brings the tip toward the palm side.
                best = None
                for sgn in (1, -1):
                    r = Matrix.Rotation(math.radians(sgn * deg), 3, axis)
                    t = r @ (pb.tail - pb.head)
                    if best is None or t.dot(n) > best[1]:
                        best = (sgn, t.dot(n))
                rotate_world(name, axis, best[0] * deg)

    rest_palm = {}
    for side in ("L", "R"):
        # MakeHuman's rest pose hangs the arms with the palms toward the thighs.
        rest_palm[side] = Vector((-1.0, 0, 0)) if side == "L" else Vector((1.0, 0, 0))

    def pose(spec):
        """Pose her from the rest pose for one state; returns every pose bone's matrix_basis."""
        for pb in pbs:
            pb.matrix_basis = Matrix.Identity(4)
        view.update()
        rotate_world("spine04", (1, 0, 0), spec["lean"] * 0.6)
        rotate_world("spine02", (1, 0, 0), spec["lean"] * 0.4)
        rotate_world("neck01", (1, 0, 0), spec["bow"] * 0.4)
        rotate_world("head", (1, 0, 0), spec["bow"] * 0.6)
        rotate_world("head", (0, 0, 1), spec["turn"])
        # Weight on the right leg: the left knee eases and the left hip drops a touch.
        rotate_world("upperleg01.L", (1, 0, 0), -4)
        rotate_world("lowerleg01.L", (1, 0, 0), 8)
        rotate_world("foot.L", (1, 0, 0), -4)
        # Landmarks move with the bent spine and bowed head; read them back posed.
        head_pose = pbs["head"].matrix @ head_b.matrix_local.inverted()
        if spec["right"] == "ear":
            right = (head_pose @ ear_r + Vector(RIGHT_WRIST_FROM_EAR), RIGHT_POLE, RIGHT_FINGERS, RIGHT_PALM)
        else:
            right = (Vector(RIGHT_REST_WRIST), RIGHT_REST_POLE, RIGHT_REST_FINGERS, LEFT_PALM)
        for side, (wrist, pole, fingers, palm), fdeg in (
                ("L", (Vector(LEFT_WRIST), LEFT_POLE, LEFT_FINGERS, LEFT_PALM), spec["curl"][0]),
                ("R", right, spec["curl"][1])):
            two_bone(side, wrist, pole)
            orient("wrist." + side, fingers, palm, rest_palm[side])
            curl(side, fdeg, 10, palm)
        return {pb.name: pb.matrix_basis.copy() for pb in pbs}

    states = [pose(st) for st in (LISTENING, LOOKING, SPEAKING, BRACED)]
    for pb in pbs:
        pb.matrix_basis = states[0][pb.name]
    view.update()

    # Re-ground: the soles on z 0 after the leg change.
    dg = bpy.context.evaluated_depsgraph_get()
    ev = body.evaluated_get(dg)
    lowest = min((ev.matrix_world @ v.co).z for v in ev.data.vertices)
    shoes = bpy.data.objects["Castle shoes02"].evaluated_get(dg)
    lowest = min(lowest, min((shoes.matrix_world @ v.co).z for v in shoes.data.vertices))
    rig.location.z -= lowest

    # ---- the appendable package: a root at her feet, turned so she faces +X
    col = bpy.data.collections.new(COLLECTION)
    scene.collection.children.link(col)
    root = bpy.data.objects.new(ROOT, None)
    root.empty_display_type, root.empty_display_size = "ARROWS", 0.3
    col.objects.link(root)
    rig.parent = root
    rig.rotation_euler = (0, 0, math.radians(90))      # MPFB's -Y front onto +X
    for ob in [rig, *rig.children_recursive]:
        for c in list(ob.users_collection):
            c.objects.unlink(ob)
        col.objects.link(ob)
    rig.show_in_front = False
    rig.hide_render = True
    root["height_m"] = round(max(v.z for v in verts) - min(v.z for v in verts), 3)

    # ---- her part in C2, keyed on every frame: the states chained (each blends from all before
    # it), with a breath in the chest on top
    def ease(x):
        x = min(max(x, 0.0), 1.0)
        return x * x * x * (x * (x * 6 - 15) + 10)

    def ramp(sec, span):
        (u0, u1), (d0, d1) = span
        if sec <= u0 or sec >= d1:
            return 0.0
        if sec < u1:
            return ease((sec - u0) / (u1 - u0))
        if sec > d0:
            return 1.0 - ease((sec - d0) / (d1 - d0))
        return 1.0

    moving = [pb for pb in pbs if any(not all(abs(states[0][pb.name][i][j] - st[pb.name][i][j]) < 1e-5
                                              for i in range(4) for j in range(4)) for st in states[1:])]
    if "spine04" in pbs and pbs["spine04"] not in moving:
        moving.append(pbs["spine04"])
    parts_ = {}
    for pb in moving:
        pb.rotation_mode = "QUATERNION"
        locs, quats = [], []
        for st in states:
            loc, q, _ = st[pb.name].decompose()
            if quats and quats[-1].dot(q) < 0:
                q.negate()
            locs.append(loc)
            quats.append(q)
        parts_[pb.name] = (locs, quats)
    hidden = [o for o in scene.objects if o.type in ("MESH", "CURVES") and not o.hide_viewport]
    for o in hidden:
        o.hide_viewport = True
    rig.animation_data_create()
    rig.animation_data.action = bpy.data.actions.new("Castle reads back and braces")
    for frame in range(0, LAST_FRAME + 1):
        sec = frame / FPS
        chain = (ramp(sec, LOOK_SPAN), ramp(sec, SPEAK_SPAN), ramp(sec, BRACE_SPAN))
        for pb in moving:
            locs, quats = parts_[pb.name]
            loc, q = locs[0], quats[0]
            for k, w in enumerate(chain, 1):
                loc, q = loc.lerp(locs[k], w), q.slerp(quats[k], w)
            pb.location, pb.rotation_quaternion = loc, q
        view.update()
        rotate_world("spine04", (1, 0, 0), BREATH_DEG * math.sin(2 * math.pi * (sec + 1.1) / BREATH_S))
        for pb in moving:
            pb.keyframe_insert("location", frame=frame)
            pb.keyframe_insert("rotation_quaternion", frame=frame)
    for o in hidden:
        o.hide_viewport = False
    scene.frame_set(0)
    root["look_s"], root["speak_s"], root["brace_s"] = [list(map(list, x)) for x in (LOOK_SPAN, SPEAK_SPAN, BRACE_SPAN)]
    root["source"] = "art-source/company-prologue/castle-figure/build_castle.py"
    root["frame"] = "metres; z up from the soles; she faces +X"

    out.parent.mkdir(parents=True, exist_ok=True)
    castle_face.pack()
    bpy.ops.wm.save_as_mainfile(filepath=str(out), compress=True)
    print("CASTLE height %.3f m, ear %s -> %s" % (root["height_m"], tuple(round(c, 3) for c in ear_r),
                                                   out), flush=True)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_main(argv)
    else:
        launch(argv)
