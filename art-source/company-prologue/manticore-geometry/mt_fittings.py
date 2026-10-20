"""Round 14: deck fittings at finished-model fidelity, sized in true metres.

Owner, round 13: "Strange low poly radar dish and light pole as well", and
across the board: "we're getting the rough shape right, but need to add
detail". These replace the round-13 primitives (a flat disc on a stick, a
bare rod with a ball on it):
- `dish`: a parabolic reflector revolved at 64 segments with a rolled rim lip,
  ribs behind it, a feed horn at the focus on three struts, trunnions in a
  yoke on a turntable, and a pedestal with a bolted base flange.
- `light_mast`: a tapered tube on a gusseted, bolted base flange with a collar,
  topped by a beacon housing (base plate, lens, guard bars, cap).
- `taper`: a tapered round member (whips, mast tubes) with an insulator foot.
Every part is built about the origin and placed by one matrix, so a fitting
can be aimed (azimuth, elevation) without breaking its own geometry.
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector
from mt_frames import struts


def _place(ob, m):
    ob.data.transform(m)
    ob.data.update()
    return ob


def taper(kit, name, p0, p1, r0, r1, mat, sides=24):
    """A round member tapering from r0 at p0 to r1 at p1, capped both ends."""
    p0, p1 = Vector(p0), Vector(p1)
    ax = (p1-p0)
    length = ax.length
    rot = ax.normalized().to_track_quat('Z', 'Y').to_matrix().to_4x4()
    verts, faces = [], []
    for z, r in ((0, r0), (length, r1)):
        for k in range(sides):
            a = 2*math.pi*k/sides
            verts.append((r*math.cos(a), r*math.sin(a), z))
    faces.append(tuple(reversed(range(sides))))
    faces.append(tuple(range(sides, 2*sides)))
    faces += [(k, (k+1) % sides, sides+(k+1) % sides, sides+k) for k in range(sides)]
    ob = kit.mesh(name, verts, faces, mat, 0)
    _place(ob, Matrix.Translation(p0) @ rot)
    return kit.smooth(ob, min(r0, r1)*.25, 1)


def _revolve(kit, name, profile, mat, steps=64):
    """Solid of revolution about local Z from an open (r, z) profile."""
    bm = bmesh.new()
    vs = [bm.verts.new((r, 0, z)) for r, z in profile]
    es = [bm.edges.new((a, b)) for a, b in zip(vs, vs[1:])]
    bmesh.ops.spin(bm, geom=vs+es, cent=(0, 0, 0), axis=(0, 0, 1), angle=2*math.pi, steps=steps, use_duplicate=False)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    return kit.finish(ob, name, mat, 0)


def dish(kit, name, base, roof_z, radius, az, el, mat, frame_mat, pedestal=.9):
    """A parabolic dish on a yoke and pedestal standing on the roof at `base`.
    `az`, `el` in degrees aim the reflector's axis. Parts are named
    `name`, `name pedestal`, `name yoke`, `name feed`."""
    R, depth, t, lip = radius, radius*.32, .07, radius*.06
    f = R*R/(4*depth)
    rings = 14
    front = [(R*k/rings, (R*k/rings)**2/(4*f)) for k in range(rings+1)]
    back = [(r, z-t) for r, z in reversed(front)]
    zr = front[-1][1]
    profile = front+[(R+lip*.5, zr+lip*.15), (R+lip, zr), (R+lip, zr-t*2.2), (R, zr-t*2.2)]+back[1:]
    pivot_h = R+.35
    pz = roof_z+pedestal+.45+pivot_h
    pivot = Vector((base[0], base[1], pz))
    aim = Matrix.Rotation(math.radians(az), 4, 'Z') @ Matrix.Rotation(math.radians(90-el), 4, 'Y')
    off = Matrix.Translation((0, 0, -depth*.55))       # the pivot near the reflector's middle
    m = Matrix.Translation(pivot) @ aim @ off
    refl = _revolve(kit, name, profile, mat)
    _place(refl, m)
    kit.smooth(refl, .02, 2, 40)
    # Ribs behind the reflector and a hub, the feed horn at the focus on three struts.
    hub = [Vector((0, 0, -t-.02)), Vector((0, 0, -t-.42))]
    segs = [(tuple(m @ hub[0]), tuple(m @ hub[1]), R*.16, R*.16, 24)]
    for k in range(8):
        a = 2*math.pi*k/8
        p_out = Vector((R*.92*math.cos(a), R*.92*math.sin(a), (R*.92)**2/(4*f)-t-.06))
        segs.append((tuple(m @ Vector((R*.18*math.cos(a), R*.18*math.sin(a), -t-.12))), tuple(m @ p_out), .05, .09))
    feed = Vector((0, 0, f))
    for k in range(3):
        a = 2*math.pi*k/3+math.pi/2
        segs.append((tuple(m @ Vector((R*.97*math.cos(a), R*.97*math.sin(a), zr+.02))), tuple(m @ feed), .035, .035, 12))
    kit.smooth(struts(kit, name+' feed struts', segs, frame_mat), .015, 1)
    horn = _revolve(kit, name+' feed', [(0, f+.32), (R*.08, f+.3), (R*.08, f+.05), (R*.14, f-.12), (R*.14, f-.18),
                                        (R*.05, f-.18), (0, f-.12)], frame_mat, 32)
    _place(horn, m)
    kit.smooth(horn, .01, 1)
    # Yoke: trunnion bosses either side, arms down to a turntable (azimuth frame only).
    yaw = Matrix.Translation(pivot) @ Matrix.Rotation(math.radians(az), 4, 'Z')
    w = R+lip+.3
    tt = -pivot_h
    arms = []
    for s in (1, -1):
        arms += [((0, s*w, 0), (0, s*w, tt+.15), .22, .3), ((0, s*(R+lip+.02), 0), (0, s*(w+.25), 0), .32, .32, 32)]
    arms.append(((0, -w, tt+.12), (0, w, tt+.12), .3, .2))
    yoke = struts(kit, name+' yoke', [(tuple(yaw @ Vector(a)), tuple(yaw @ Vector(b)), *rest) for a, b, *rest in arms],
                  frame_mat)
    kit.smooth(yoke, .05, 2)
    ped = _revolve(kit, name+' pedestal', [(0, .55+pedestal), (.95, .55+pedestal), (1.0, .5+pedestal), (1.0, .25+pedestal),
                                           (.55, .1+pedestal), (.42, pedestal), (.42, .22), (.85, .14), (.85, 0), (0, 0)],
                   frame_mat, 48)
    _place(ped, Matrix.Translation((base[0], base[1], roof_z)))
    kit.smooth(ped, .03, 2)
    bolts(kit, name+' flange bolts', base, roof_z+.14, .68, 10, frame_mat)
    return refl


def bolts(kit, name, centre, z, ring, n, mat, r=.06):
    segs = []
    for k in range(n):
        a = 2*math.pi*(k+.5)/n
        x, y = centre[0]+ring*math.cos(a), centre[1]+ring*math.sin(a)
        segs.append(((x, y, z-.02), (x, y, z+.08), r, r, 6))
    return struts(kit, name, segs, mat)


def light_mast(kit, name, base, roof_z, height, mat, beacon_mat, r0=.32, r1=.15, beacon_name=None):
    """A tapered mast on a gusseted, bolted flange, with a collar and a beacon
    housing on top. Returns the mast top (just under the housing)."""
    x, y = base
    flange = _revolve(kit, name+' base flange', [(0, .2), (.48, .2), (.62, .12), (.62, 0), (0, 0)], mat, 48)
    _place(flange, Matrix.Translation((x, y, roof_z)))
    kit.smooth(flange, .02, 2)
    bolts(kit, name+' flange bolts', base, roof_z+.12, .5, 8, mat, .045)
    gus = []
    for k in range(4):
        a = math.pi/4+k*math.pi/2
        c, s = math.cos(a), math.sin(a)
        gus.append(((x+c*(r0+.02), y+s*(r0+.02), roof_z+.18), (x+c*.55, y+s*.55, roof_z+.18), .03, .02))
        gus.append(((x+c*(r0+.02), y+s*(r0+.02), roof_z+.18), (x+c*(r0+.02), y+s*(r0+.02), roof_z+.85), .03, .03))
        gus.append(((x+c*.5, y+s*.5, roof_z+.2), (x+c*(r0+.03), y+s*(r0+.03), roof_z+.8), .025, .06))
    kit.smooth(struts(kit, name+' gussets', gus, mat), .01, 1)
    top = roof_z+.2+height
    taper(kit, name, (x, y, roof_z+.18), (x, y, top), r0, r1, mat)
    kit.smooth(_place(_revolve(kit, name+' collar', [(0, .16), (r0*.9, .16), (r0*.95, .08), (r0*.95, -.08), (r0*.9, -.16),
                                                     (0, -.16)], mat, 32),
                      Matrix.Translation((x, y, roof_z+.2+height*.55))), .01, 1)
    # Beacon housing: base plate, lens, four guard bars, cap.
    hz = top
    housing = _revolve(kit, name+' beacon housing', [(0, hz+.09), (.3, hz+.09), (.34, hz+.05), (.34, hz), (r1, hz-.08),
                                                     (0, hz-.08)], mat, 40)
    here = Matrix.Translation((x, y, 0))
    kit.smooth(_place(housing, here), .01, 1)
    lens = _revolve(kit, beacon_name or name+' beacon', [(0, hz+.48), (.13, hz+.47), (.2, hz+.42), (.22, hz+.3),
                                                         (.22, hz+.09), (0, hz+.09)], beacon_mat, 40)
    kit.smooth(_place(lens, here), .005, 1, 45)
    guards = []
    for k in range(4):
        a = math.pi/4+k*math.pi/2
        p = (x+.29*math.cos(a), y+.29*math.sin(a))
        guards.append(((*p, hz+.09), (*p, hz+.56), .022, .022, 8))
    kit.smooth(struts(kit, name+' beacon guards', guards, mat), .005, 1)
    cap = _revolve(kit, name+' beacon cap', [(0, hz+.62), (.25, hz+.62), (.33, hz+.57), (.33, hz+.54), (0, hz+.54)], mat, 40)
    kit.smooth(_place(cap, here), .01, 1)
    return top
