"""C2-only dressing: the bridge's viewport slits framed as windows cut through an armoured bulkhead.

Owner, 2026-10-20: the posts between the slits and the walls round them fill much of C2's frame and
read flat. This adds, on the room side only, what makes each slit a window in armour:

- an embrasure behind every mullion: a reveal casing that stays inside what the mullion already
  hides from every camera position in the shot that sees it (build's `eyes`), so no frame shows
  less glass. A casing square to the wall stands nearer the eye than its mullion, looks wider and
  sits off to one side of it; one splayed to a single station still hid the Legion's bow mid-walk.
  Where the eyes disagree most the casing is shallower. A post nobody sees is splayed to its station
  (CON_EYE, SIDE_EYE). Each carries a stiffening rib with a flange, a bolt column inside each cheek,
  panel seams and a frame-number plate; a corner post wraps both walls;
- a shallow bolted sill shelf with a nosing under the glass (the consoles' lamp rows sit just under
  it) and a header beam over it with a bolted fascia standing above the glass line;
- retaining strips screwed down round every pane over a black gasket line, reaching about a
  centimetre onto the glass;
- the posts' frame lines carried up the header zone and down the wall below the sill as bolted
  flat-bar stiffeners, a riveted seam along the header zone, and a conduit on saddle clamps along
  the forward walls with a junction box on the port chamfer.

Round 2 (owner, 2026-10-20: the first version read as low poly at this close camera): fixings at
the console hardware's scale (chamfered hex bolts on washers, varied, some socket caps; slotted
pan-head screws; domed rivets), riveted frame-number plates with raised numbers, and worn paint
(worn): grime in recesses, edges scuffed to steel, blotches, streaks and a faint orange peel over
each of the room's own flat paints, keeping its mean colour so the dressing sits beside the
approved room.

It is C2 dressing, not the ship model: the slit openings, the glass, the mullions (their widths too)
and the camera are untouched, and every part lies on the room side of the cut wall. It is built in
hull units from the model's own numbers (sw_bridge's glazing plan and mullion layout,
sw_bridge_interior's room) and parented to the appended ship's root, so it pitches with the ship.
c2_bridge_render.py builds it only when asked (C2_BULKHEAD_DETAIL=1 or --bulkhead) until the owner
accepts it. Runs inside Blender, with second-wind-geometry on sys.path.
"""
import math
import random

import bpy
from mathutils import Matrix, Vector

import sw_bridge_interior as I
from sw_bridge import along, plan

CON_EYE, SIDE_EYE = (16.75, 1.2), (13.0, 3.9)    # C2's forward station; the port station, leaning in
D = .16                                         # reveal depth into the room
HATCH = 1.05                                    # half-width of the aft hatch frame (aft wall, edge 6)
UP = Vector((0, 0, 1))


def mullions():
    """The posts between the slits as sw_bridge.build lays them, outside the glass:
    [((x, y), plan angle, width along the glass, depth across it, glass edge, fraction along it)]."""
    glass_plan = plan(9.0, 17.9, 4.8, 2.0, .8)
    out = []
    for edge, count in ((1, 2), (2, 3), (3, 2)):
        for k in range(count + 1):
            if edge != 2 and k == 0:
                continue
            x, y, ang, _ = along(glass_plan, edge, k / count, .3)
            out.append(((x + .1, y), ang, .6, .55, edge, k / count))
    for edge in (0, 4):
        for t in (.3, .75):
            x, y, ang, _ = along(glass_plan, edge, t, .3)
            out.append(((x, y), ang, .5, .5, edge, t))
    return out


class Parts(I.Parts):
    """sw_bridge_interior's part gatherer, plus a vertical prism over a plan polygon."""

    def prism(self, poly, z0, z1):
        lo = [self.bm.verts.new((x, y, z0)) for x, y in poly]
        hi = [self.bm.verts.new((x, y, z1)) for x, y in poly]
        self.bm.faces.new(lo)
        self.bm.faces.new(hi)
        for i in range(len(poly)):
            j = (i + 1) % len(poly)
            self.bm.faces.new((lo[i], lo[j], hi[j], hi[i]))
        return self


class Kit:
    """The two things sw_bridge_interior.Parts needs of a kit, with materials taken from the
    appended ship by name (so the dressing wears the room's own paint)."""

    def finish(self, obj, name, mat, bevel=0):
        obj.name = name
        obj.data.materials.append(mat)
        if bevel:
            b = obj.modifiers.new('Manufactured edge radii', 'BEVEL')
            b.width, b.segments = bevel, 3
            obj.modifiers.new('Weighted corner normals', 'WEIGHTED_NORMAL')
        return obj


def material(name, rgb, metallic=0.0, rough=.65):
    m = bpy.data.materials.get(name)
    if m is None:
        m = bpy.data.materials.new(name)
        m.use_nodes = True
        bs = m.node_tree.nodes['Principled BSDF']
        bs.inputs['Base Color'].default_value = (*rgb, 1)
        bs.inputs['Metallic'].default_value = metallic
        bs.inputs['Roughness'].default_value = rough
    return m


def worn(src, chip=.5, grime=.8, mottle=.06):
    """A worn copy of one of the bridge's flat paints (the approved room is flat Principled colour, so
    its mean colour and finish are kept): grime settled into recesses (ambient occlusion), paint
    scuffed through to steel along edges (bevel normal against the true normal, broken up by noise),
    and a low mottle in colour and roughness. Noise is in object space, which here is hull units."""
    name = 'C2B | worn ' + src.name
    if name in bpy.data.materials:
        return bpy.data.materials[name]
    bs0 = src.node_tree.nodes.get('Principled BSDF') if src.use_nodes else None
    base = tuple(bs0.inputs['Base Color'].default_value) if bs0 else tuple(src.diffuse_color)
    metal = bs0.inputs['Metallic'].default_value if bs0 else .3
    rough = bs0.inputs['Roughness'].default_value if bs0 else .6
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    N, L = nt.nodes.new, nt.links.new
    bs = nt.nodes['Principled BSDF']
    tc = N('ShaderNodeTexCoord')

    def noise(scale, detail, stretch=None):
        n = N('ShaderNodeTexNoise')
        n.inputs['Scale'].default_value, n.inputs['Detail'].default_value = scale, detail
        if stretch:
            mp = N('ShaderNodeMapping')
            mp.inputs['Scale'].default_value = stretch
            L(tc.outputs['Object'], mp.inputs['Vector'])
            L(mp.outputs['Vector'], n.inputs['Vector'])
        else:
            L(tc.outputs['Object'], n.inputs['Vector'])
        return n.outputs['Fac']

    def mrange(src_, a, b, c=0.0, d=1.0):
        r = N('ShaderNodeMapRange')
        r.inputs['From Min'].default_value, r.inputs['From Max'].default_value = a, b
        r.inputs['To Min'].default_value, r.inputs['To Max'].default_value = c, d
        L(src_, r.inputs['Value'])
        return r.outputs['Result']

    def math_(op, a, b):
        n = N('ShaderNodeMath')
        n.operation = op
        for k, v in enumerate((a, b)):
            if isinstance(v, float):
                n.inputs[k].default_value = v
            else:
                L(v, n.inputs[k])
        return n.outputs['Value']

    def mix(fac, a, b):
        n = N('ShaderNodeMix')
        n.data_type = 'RGBA'
        rgba = {i.name: i for i in n.inputs if i.type == 'RGBA'}     # 'A'/'B' name float sockets too
        for sock, v in (('A', a), ('B', b)):
            if isinstance(v, tuple):
                rgba[sock].default_value = v
            else:
                L(v, rgba[sock])
        L(fac, n.inputs['Factor'])
        return next(o for o in n.outputs if o.type == 'RGBA')

    # Mottle: a broad variation of the paint, darker blotches of handling grime, and long vertical
    # streaks where condensation has run down the walls.
    tone = mrange(noise(6.0, 4.0), .3, .7, 1 - mottle, 1 + mottle)
    blotch = mrange(noise(3.0, 4.0), .48, .66, 1.0, .66)
    streak = mrange(noise(11.0, 6.0, (1.0, 1.0, .07)), .44, .72, 1.0, .72)
    shade = math_('MULTIPLY', math_('MULTIPLY', tone, blotch), streak)
    col = mix(math_('MINIMUM', shade, 1.0), (0.0, 0.0, 0.0, 1.0), base)
    col = mix(mrange(shade, 1.0, 1 + mottle, 0.0, 1.0), col, tuple(min(1.0, c * (1 + mottle)) for c in base[:3]) + (1.0,))
    # Grime packed into the recesses and corners.
    ao = N('ShaderNodeAmbientOcclusion')
    ao.inputs['Distance'].default_value, ao.samples = .05, 8
    dirt = math_('POWER', math_('SUBTRACT', 1.0, ao.outputs['AO']), .8)
    dirt = math_('MULTIPLY', dirt, mrange(noise(18.0, 6.0), .3, .7, .5, 1.3))
    dirt = math_('MINIMUM', math_('MULTIPLY', dirt, grime), 1.0)
    col = mix(dirt, col, (base[0] * .3, base[1] * .27, base[2] * .22, 1.0))
    # Paint worn through to steel on convex edges (occlusion seen from inside the surface), in
    # ragged patches rather than a clean line.
    edge_ao = N('ShaderNodeAmbientOcclusion')
    edge_ao.inside, edge_ao.samples = True, 8
    edge_ao.inputs['Distance'].default_value = .004
    edge = mrange(edge_ao.outputs['AO'], .9, .6, 0.0, 1.0)
    chips = math_('MULTIPLY', edge, mrange(noise(55.0, 8.0), .32, .52, 0.0, 1.0))
    chips = math_('MINIMUM', math_('MULTIPLY', chips, chip), 1.0)
    col = mix(chips, col, (.42, .41, .39, 1.0))
    L(col, bs.inputs['Base Color'])
    met = mix(chips, (metal, metal, metal, 1.0), (.9, .9, .9, 1.0))
    sep = N('ShaderNodeSeparateColor')
    L(met, sep.inputs[0])
    L(sep.outputs['Red'], bs.inputs['Metallic'])
    r = math_('ADD', mrange(noise(16.0, 5.0), .3, .7, rough - .12, rough + .12), math_('MULTIPLY', dirt, .15))
    r = math_('SUBTRACT', r, math_('MULTIPLY', chips, .35))
    L(r, bs.inputs['Roughness'])
    # Painted plate is never flat: a faint orange peel and the odd shallow dent break up highlights.
    bump = N('ShaderNodeBump')
    bump.inputs['Strength'].default_value, bump.inputs['Distance'].default_value = .25, .002
    peel = math_('ADD', noise(140.0, 4.0), math_('MULTIPLY', noise(4.0, 2.0), 1.5))
    L(peel, bump.inputs['Height'])
    L(bump.outputs['Normal'], bs.inputs['Normal'])
    return m


def stamp(name, text, centre, R, size, relief):
    """Raised characters (a stamped or cast number plate): a font curve made a mesh, its local
    X along R's first column, up R's third, standing `relief` off the face R's second column faces."""
    cu = bpy.data.curves.new(name, 'FONT')
    cu.body, cu.size, cu.extrude = text, size, relief / 2
    cu.align_x, cu.align_y = 'CENTER', 'CENTER'
    tmp = bpy.data.objects.new(name, cu)
    bpy.context.collection.objects.link(tmp)
    x, nn, up = R.col[0], R.col[1], R.col[2]
    tmp.matrix_world = Matrix.Translation(Vector(centre) + Vector(nn) * relief / 2) @         Matrix((-Vector(x), up, nn)).transposed().to_4x4()
    bpy.context.view_layer.update()
    me = bpy.data.meshes.new_from_object(tmp.evaluated_get(bpy.context.evaluated_depsgraph_get()))
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    ob.matrix_world = tmp.matrix_world.copy()
    bpy.data.objects.remove(tmp, do_unlink=True)
    bpy.data.curves.remove(cu)
    return ob


def build(root, eyes=()):
    """Build the dressing in hull units and parent every part to `root` (the ship's root). `eyes` is
    the shot's camera path, [((x, y) in hull units, yaw deg, half field of view deg)]: each casing
    stays inside what its mullion hides from every one of them that sees it."""
    eyes = [(Vector((x, y, 0)), yaw, half) for (x, y), yaw, half in eyes]
    M = {
        'lining': material('Bridge interior lining', (.15, .16, .145), .15, .7),
        'casing': material('Bridge console casing', (.10, .108, .112), .4, .5),
        'hardware': material('Bridge console hardware', (.24, .25, .26), .7, .35),
        'label': material('Bridge label plate', (.52, .51, .46), .1, .5),
        'gasket': material('Bridge hatch gasket', (.01, .01, .01), 0, .8),
        'seam': material('Bridge lining seam', (.035, .037, .034), 0, .8),
        'steel': material('Engine collars', (.19, .21, .22), .65),
    }
    kit = Kit()
    before = set(bpy.data.objects)
    SILL, HEADER, FLOOR, CEILING = I.SILL, I.HEADER, I.FLOOR, I.CEILING
    room = plan(9.15, I.FRONT, 4.65, 1.95, .75)
    sill_in = plan(9.23, I.FRONT - .08, 4.57, 1.95, .75)       # shallow: the consoles' lamp rows sit just under it
    head_in = plan(9.33, I.FRONT - .18, 4.47, 1.95, .75)
    n = len(room)

    def frame_of(e):
        """Edge e of the room wall: start, unit tangent, inward normal, length (the plan is CCW)."""
        a, b = Vector((*room[e], 0)), Vector((*room[(e + 1) % n], 0))
        t = (b - a).normalized()
        return a, t, Vector((-t.y, t.x, 0)), (b - a).length

    def basis(t, nn):
        return Matrix((t, nn, UP)).transposed()

    P = Parts
    casing, ribs, bolts, seams, plates = P(), P(), P(), P(), P()
    strips, gasket, screws, sill, fascia, header, lip = P(), P(), P(), P(), P(), P(), P()
    stiff, rivets, conduit, saddles, jbox = P(), P(), P(), P(), P()

    # Fixings at the console hardware's scale (hull units; 0.01 is 6.5 mm): hex bolts on washers
    # (a chamfered head, turned at random, a few a size up, one in eight a socket cap), slotted
    # pan-head screws, and domed rivets. A row is regular in pitch and nothing else.
    heads, washers, domes, recesses = P(), P(), P(), P()
    rng = random.Random(20261020)

    def fixing(kind, at, normal):
        nrm = Vector(normal).normalized()
        q = nrm.to_track_quat('Z', 'Y').to_matrix() @ Matrix.Rotation(rng.uniform(0, math.tau), 3, 'Z')
        at = Vector(at)
        if kind == 'rivet':
            domes.cyl(at + nrm * .0015, .0072, .003, q, 16, .006)
            domes.cyl(at + nrm * .0042, .006, .0024, q, 16, .0028)
            return
        if kind == 'screw':
            domes.cyl(at + nrm * .0018, .0068, .0036, q, 20, .0058)
            recesses.box(at + nrm * .0037, (.0105, .0016, .0012), q)
            return
        k = rng.uniform(.92, 1.06) * (1.18 if rng.random() < .12 else 1.0)
        washers.cyl(at + nrm * .0014 * k, .0165 * k, .0028 * k, q, 24, .0155 * k)
        if rng.random() < .125:                 # socket cap: a round head with a hex socket
            domes.cyl(at + nrm * .0078 * k, .0098 * k, .01 * k, q, 24, .0088 * k)
            recesses.cyl(at + nrm * .0129 * k, .0042 * k, .0004, q, 6)
            return
        heads.cyl(at + nrm * .0062 * k, .0118 * k, .0068 * k, q, 6)
        heads.cyl(at + nrm * .0111 * k, .0118 * k, .003 * k, q, 6, .0094 * k)       # the chamfer
        if rng.random() < .7:                   # a thread end standing proud of the head
            domes.cyl(at + nrm * .0135 * k, .0052 * k, .002 * k, q, 12, .0044 * k)

    def bolt_row(parts, p0, p1, normal, pitch, *_):
        kind = 'screw' if parts is screws else 'rivet' if parts is rivets else 'bolt'
        p0, p1 = Vector(p0), Vector(p1)
        count = max(1, int((p1 - p0).length / pitch))
        for k in range(count + 1):
            fixing(kind, p0.lerp(p1, k / count), normal)

    # ---- embrasures behind the mullions.
    z0, z1 = SILL - .02, HEADER + .02
    zm = (SILL + HEADER) / 2
    groups = {}                                 # one casing per mullion, one per corner pair
    for (mx, my), ang, width, depth, edge, frac in mullions():
        c, s = math.cos(ang), math.sin(ang)
        box = [Vector((mx + c * du - s * dv, my + s * du + c * dv, 0))
               for du in (-width / 2, width / 2) for dv in (-depth / 2, depth / 2)]
        if frac in (0.0, 1.0):
            v = edge if frac == 0.0 else (edge + 1) % n
            groups.setdefault(('corner', v), []).extend(box)
        else:
            groups[('wall', edge, round(frac, 3))] = box

    def cast(E, ray, edges, d):
        """Where a sight line from E first crosses the plane d in from the glass of one of `edges`, as a
        distance along the wall chain (the second wall of a corner continues the first)."""
        best = None
        for k, e in enumerate(edges):
            a, t, nn, L = frame_of(e)
            den = ray.dot(nn)
            if den >= 0:
                continue
            s_ = (d - (E - a).dot(nn)) / den
            if s_ > 0 and (best is None or s_ < best[0]):
                best = (s_, (E + ray * s_ - a).dot(t) + (frame_of(edges[0])[3] if k else 0))
        return best[1]

    def point(edges, s_, d):
        La = frame_of(edges[0])[3]
        e, u = (edges[0], s_) if len(edges) == 1 or s_ <= La else (edges[1], s_ - La)
        a, t, nn, _ = frame_of(e)
        return a + t * u + nn * d, e, u

    labels = []                                 # (face point, basis, normal) of each frame-number plate
    posts = []                                  # (wall edge, u of a cheek at the glass, outward sign, own frame line)
    for key, box in groups.items():
        edges = [key[1]] if key[0] == 'wall' else [(key[1] - 1) % n, key[1]]
        centre = sum(box, Vector()) / len(box)
        seen = [E for E, yaw, half in eyes
                if abs((math.degrees(math.atan2(*reversed((centre - E).xy))) - yaw + 180) % 360 - 180) < half]
        if not seen:                            # out of every frame: splay it to its station
            side = -1 if edges[0] in (0, 7) else 1 if edges[0] in (4, 5) else 0
            E = Vector((*(SIDE_EYE if side else CON_EYE), 0))
            E.y *= -1 if side < 0 else 1
            seen = [E]

        def shadow(d):                          # what the mullion hides from every eye, at depth d
            spans_ = [[cast(E, c - E, edges, d) for c in box] for E in seen]
            return max(min(x) for x in spans_), min(max(x) for x in spans_)

        s0 = shadow(0)
        for depth in (D, .12, .08, .05):        # shallower where the eyes disagree the most
            sD = shadow(depth)
            if sD[1] - sD[0] >= .12:
                break
        if s0[1] - s0[0] < .14:                 # never thinner than a post can be
            m = sum(s0) / 2
            s0 = (m - .07, m + .07)
        if sD[1] - sD[0] < .1:
            m = sum(sD) / 2
            sD = (m - .05, m + .05)
        La = frame_of(edges[0])[3]
        across = lambda span: len(edges) == 2 and span[0] < La < span[1]
        L0, eL, uL = point(edges, s0[0], 0)
        R0, eR, uR = point(edges, s0[1], 0)
        LD, fL, _ = point(edges, sD[0], depth)
        RD, fR, _ = point(edges, sD[1], depth)
        V = Vector((*room[key[1]], 0)) if key[0] == 'corner' else None
        poly = [L0] + ([V] if across(s0) else []) + [R0, RD]
        inner = None
        if across(sD):
            _, _, n0, _ = frame_of(edges[0])
            _, _, n1, _ = frame_of(edges[1])
            bis = (n0 + n1).normalized()
            inner = V + bis * (depth / bis.dot(n0))
            poly.append(inner)
        poly.append(LD)
        casing.prism([(q.x, q.y) for q in poly], z0, z1)
        # The inner face: a rib with a flange down its middle, a bolt column inside each cheek where
        # there is room, panel seams and a frame-number plate. Across a corner it faces the bisector.
        if inner is None:
            mid, f, _ = point(edges, sum(sD) / 2, depth)
            _, t, nn, _ = frame_of(f)
        else:
            nn, mid = bis, inner
            t = Vector((-nn.y, nn.x, 0))
        R = basis(t, nn)
        ribs.box(mid + nn * .02 + UP * zm, (.05, .04, z1 - z0), R)
        ribs.box(mid + nn * .045 + UP * zm, (.09, .012, z1 - z0 - .06), R)
        for Q, e in ((LD, fL), (RD, fR)):
            if (mid - Q).length > .1:
                q = Q + (mid - Q).normalized() * .04
                bolt_row(bolts, q + UP * (SILL + .07), q + UP * (HEADER - .07), frame_of(e)[2], .11)
        if inner is None and (RD - LD).length > .08:
            for dz in (-.2, .12):
                seams.box(mid + nn * .003 + UP * (zm + dz), ((RD - LD).length - .02, .006, .012), R)
        labels.append((mid + nn * .065, R, nn))
        posts += [(eL, uL, -1, key[0] == 'wall'), (eR, uR, 1, key[0] == 'wall')]

    # ---- sill and header along every wall (stopping either side of the aft hatch), and the
    # retaining strips and gasket along the bottom and top of every pane.
    def spans(e, L):
        if e != 6:
            return [(0, L)]
        a, t, _, _ = frame_of(e)
        mid = -a.dot(t)                         # where y = 0 crosses the aft wall
        return [(0, mid - HATCH), (mid + HATCH, L)]

    for e in range(n):
        a, t, nn, L = frame_of(e)
        R = basis(t, nn)
        p = lambda uu, d, z=0.0: a + t * uu + nn * d + UP * z
        whole = spans(e, L) == [(0, L)]
        if whole:                               # mitred to the next wall: quads between the rings
            for ring, za, zb, parts in ((sill_in, SILL - .06, SILL - .005, sill),
                                        (head_in, HEADER + .005, HEADER + .09, header)):
                parts.prism([room[e], room[(e + 1) % n], ring[(e + 1) % n], ring[e]], za, zb)
        for u0, u1 in spans(e, L):
            span, mid = u1 - u0, (u0 + u1) / 2
            if not whole:
                sill.box(p(mid, .04, SILL - .0325), (span, .08, .055), R)
                header.box(p(mid, .09, HEADER + .0475), (span, .18, .085), R)
            # The sill's nosing and the bolts down its top; the header beam's fascia, standing up off
            # its room edge (never below the glass line, so it hides none of the pane).
            fascia.box(p(mid, .08, SILL - .03), (span - .04, .016, .06), R)
            bolt_row(bolts, p(u0 + .1, .045, SILL - .005), p(u1 - .1, .045, SILL - .005), UP, .18, .012, .01)
            lip.box(p(mid, .195, HEADER + .07), (span - .04, .03, .13), R)
            bolt_row(bolts, p(u0 + .12, .21, HEADER + .07), p(u1 - .12, .21, HEADER + .07), nn, .2)
            for z, sgn in ((SILL - .007, 1), (HEADER + .007, -1)):
                strips.box(p(mid, .0125, z), (span, .025, .026), R)
                gasket.box(p(mid, .006, z + sgn * .017), (span, .012, .008), R)
                bolt_row(screws, p(u0 + .06, .025, z), p(u1 - .06, .025, z), nn, .15, .009, .008, 8)
            seams.box(p(mid, .018, 19.92), (span - .06, .006, .015), R)
            bolt_row(rivets, p(u0 + .1, .02, 19.97), p(u1 - .1, .02, 19.97), nn, .1, .012, .01, 6)

    # Retaining strips up both sides of every post (on the glass, half under the cheek, 1 cm proud
    # of it), and each wall post's frame line carried above and below the band as a stiffener.
    lines = {}
    for e, u, sgn, own in posts:
        a, t, nn, L = frame_of(e)
        R = basis(t, nn)
        p = lambda uu, d, z=0.0: a + t * uu + nn * d + UP * z
        strips.box(p(u, .0125, zm), (.032, .025, HEADER - SILL), R)
        gasket.box(p(u + sgn * .02, .006, zm), (.008, .012, HEADER - SILL), R)
        bolt_row(screws, p(u + sgn * .006, .025, SILL + .08), p(u + sgn * .006, .025, HEADER - .08), nn, .14,
                 .007, .008, 8)
        if own:
            lines.setdefault(e, []).append(u)
    for e, us in lines.items():
        a, t, nn, L = frame_of(e)
        R = basis(t, nn)
        p = lambda uu, d, z=0.0: a + t * uu + nn * d + UP * z
        us = sorted(us)
        for k in range(0, len(us) - 1, 2):
            u = (us[k] + us[k + 1]) / 2
            for za, zb in ((HEADER + .09, CEILING - .03), (FLOOR + .1, SILL - .15)):
                stiff.box(p(u, .05, (za + zb) / 2), (.07, .06, zb - za), R)
                stiff.box(p(u, .095, (za + zb) / 2), (.13, .012, zb - za), R)
                for du in (-.045, .045):
                    bolt_row(bolts, p(u + du, .1, za + .08), p(u + du, .1, zb - .08), nn, .16, .014, .012)

    # A conduit along the header zone of the forward walls on saddle clamps, with a junction box
    # on the port chamfer.
    zc, dc = 20.12, .09
    pts = []
    for e in (1, 2, 3):
        a, t, nn, L = frame_of(e)
        R = basis(t, nn)
        pts += [a + t * .25 + nn * dc + UP * zc, a + t * (L - .25) + nn * dc + UP * zc]
        k_n = int(L / .7) + 1
        for k in range(1, k_n):
            u = k * L / k_n
            saddles.box(a + t * u + nn * (dc / 2) + UP * zc, (.05, dc, .085), R)
            saddles.box(a + t * u + nn * .006 + UP * zc, (.12, .012, .12), R)
    conduit.tube(pts, .032, 10)
    a, t, nn, L = frame_of(3)
    R = basis(t, nn)
    jc = a + t * (L * .5) + nn * .08 + UP * 19.98
    jbox.box(jc, (.28, .16, .26), R)
    q = nn.to_track_quat('Z', 'Y').to_matrix()
    for du in (-.11, .11):
        for dz in (-.1, .1):
            fixing('screw', jc + t * du + nn * .08 + UP * dz, nn)
    plates.box(jc + nn * .082 + UP * .02, (.16, .004, .06), R)
    for du in (-.068, .068):
        fixing('rivet', jc + t * du + nn * .084 + UP * .02, nn)
    stamps = [stamp('C2B | bulkhead junction box legend', 'JB 4-07  440V', jc + nn * .084 + UP * .02, R, .022, .0012)]

    # Frame-number plates high on each post: a pale plate riveted top and bottom, the frame number
    # cast into it. Frames are numbered round the room from starboard aft, as a yard would.
    def bearing(item):
        c = item[0] - Vector((12.5, 0, 0))
        return math.atan2(c.x, -c.y)
    for k, (at, R_, nn_) in enumerate(sorted(labels, key=bearing)):
        c = at + UP * (HEADER - .12)
        plates.box(c + nn_ * .002, (.075, .004, .062), R_)
        for dz in (-.022, .022):
            fixing('rivet', c + nn_ * .004 + UP * dz, nn_)
        stamps.append(stamp('C2B | bulkhead frame number %d' % (k + 1), 'FR %d' % (21 + k), c + nn_ * .004, R_,
                            .02, .0012))

    W = {key: worn(mat, *args) for key, mat, args in (
        ('lining', M['lining'], (.9, 1.4, .1)), ('casing', M['casing'], (.9, 1.2, .1)),
        ('hardware', M['hardware'], (.4, 1.6, .14)), ('label', M['label'], (.5, 1.4, .12)), ('steel', M['steel'], (.6, 1.2, .1)))}
    M = {**M, **W}
    casing.build(kit, 'C2B | bulkhead reveal casings', M['lining'], bevel=.012)
    ribs.build(kit, 'C2B | bulkhead post ribs', M['casing'], bevel=.006)
    heads.build(kit, 'C2B | bulkhead bolt heads', M['hardware'], bevel=.0007)
    washers.build(kit, 'C2B | bulkhead bolt washers', M['hardware'], bevel=.0006)
    domes.build(kit, 'C2B | bulkhead screw and rivet heads', M['hardware'], bevel=.0006)
    recesses.build(kit, 'C2B | bulkhead screw slots', M['gasket'])
    for ob in stamps:
        ob.data.materials.append(material('Bridge console knob', (.012, .012, .014), 0, .35))
    seams.build(kit, 'C2B | bulkhead seams', M['seam'])
    plates.build(kit, 'C2B | bulkhead frame plates', M['label'], bevel=.0015)
    strips.build(kit, 'C2B | bulkhead retaining strips', M['casing'], bevel=.004)
    gasket.build(kit, 'C2B | bulkhead glazing gaskets', M['gasket'])
    sill.build(kit, 'C2B | bulkhead sill', M['lining'], bevel=.008)
    fascia.build(kit, 'C2B | bulkhead sill fascia', M['casing'], bevel=.006)
    header.build(kit, 'C2B | bulkhead header beam', M['lining'], bevel=.008)
    lip.build(kit, 'C2B | bulkhead header lip', M['casing'], bevel=.006)
    stiff.build(kit, 'C2B | bulkhead wall stiffeners', M['casing'], bevel=.006)
    conduit.build(kit, 'C2B | bulkhead header conduit', M['steel'], smooth=True)
    saddles.build(kit, 'C2B | bulkhead conduit saddles', M['hardware'])
    jbox.build(kit, 'C2B | bulkhead junction box', M['casing'], bevel=.01)
    made = [o for o in bpy.data.objects if o not in before]
    for o in made:
        o.parent = root
        o.matrix_parent_inverse = Matrix.Identity(4)
    return made
