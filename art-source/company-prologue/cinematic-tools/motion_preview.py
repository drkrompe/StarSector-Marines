"""Motion preview: a whole shot (or sequence) as near-free Workbench frames, for judging motion and
composition before any lit render (cinematic-production skill, "Review motion on a preview first").

No lighting cost, no textures beyond a ground's or planet's base colour, no volumes:

- every craft is one flat colour of its own, named in a legend and tagged on screen;
- planets and ground keep their base image texture, shown flat (Workbench TEXTURE colour);
- effects and volumes are hidden and drawn as cheap timed proxies: drive plumes as cones whose
  length follows the throttle, missiles as small streaks, bursts as brief wire shells, beams and
  tracers as lines a few pixels wide at any range, shields as wire shells;
- burnt in afterwards: frame number, timecode, shot id and title, action- and title-safe guides,
  the legend, and optional per-craft motion trails over the last N seconds;
- half size by default, every frame or every second frame (--step 2 is 12 fps at 24), assembled
  with ffmpeg into an MP4 that plays at the shot's real speed (fps / step), labelled at 1920x1080.

A preview judges motion and composition only: paths, timing, spacing, speed, attitude, banking,
what is in frame and when, and whether every drive is lit when it should be. It cannot judge
lighting, materials, volume look, motion blur or anything about the finished surface.

Two halves:

- Inside Blender (`Preview`): a shot builder poses its scene, then hands the preview its crafts,
  plumes and effects, and `render_shot` keys every proxy for every preview frame and renders the
  shot as one Workbench animation (geometry is uploaded once per shot, not once per frame).
  `preview_animated_scene` does all of this for a scene that already animates itself and carries
  thruster markers (the Jangala shots: `thrust-markers/README.md`).
- Plain Python (`finish`): reads `manifest.json` and the frames, draws the overlays with Pillow
  at 1920x1080 and pipes them to ffmpeg; writes `<name>.mp4`, a `<name>-web.mp4` for a review
  page, and 1600 px JPEG stills of each shot's key moment under `jpg/`.

    python motion_preview.py finish <out>              # overlays + MP4 + stills from a rendered run
    python motion_preview.py run --out <out> -- <blender args...>
                                                       # Blender (the builder's own preview mode), then finish

A builder's mode hook stays small: see `asharu-battle/asharu_preview.py` (the Asharu key-frame
sequence) and `c4x_scene.py --motion-preview` (an animated Jangala shot).
"""
import json
import math
import os
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ART = HERE.parents[1]          # art-source
W, H = 1920, 1080              # what the owner sees; the render itself is scale% of it

# Distinct flat colours, light enough to read on a dark sky and on desert. Two families so a
# shot with two sides reads at a glance: COOL for one side, WARM for the other; NEUTRAL otherwise.
COOL = [(0.30, 0.62, 1.00), (0.20, 0.90, 0.85), (0.55, 0.95, 0.35), (0.70, 0.55, 1.00),
        (0.10, 0.45, 0.80), (0.60, 0.85, 1.00), (0.15, 0.70, 0.45), (0.85, 0.75, 1.00),
        (0.35, 0.80, 0.65), (0.45, 0.45, 0.95), (0.75, 1.00, 0.60), (0.20, 0.60, 0.65),
        (0.95, 0.95, 0.95), (0.50, 0.70, 0.85)]
WARM = [(1.00, 0.32, 0.25), (1.00, 0.62, 0.15), (1.00, 0.88, 0.25), (0.95, 0.35, 0.70),
        (0.80, 0.20, 0.15), (1.00, 0.55, 0.50), (0.85, 0.55, 0.20), (1.00, 0.75, 0.70),
        (0.75, 0.30, 0.55), (0.95, 0.80, 0.55), (0.65, 0.40, 0.25), (1.00, 0.45, 0.85)]
NEUTRAL = COOL + WARM
FX_RGB = {'plume': (1.0, 0.62, 0.20), 'plume-blue': (0.45, 0.75, 1.0), 'plume-white': (1.0, 0.92, 0.6),
          'tracer': (1.0, 0.95, 0.35), 'missile': (1.0, 1.0, 1.0), 'burst': (1.0, 0.45, 0.10),
          'beam': (0.55, 1.0, 1.0), 'shield': (0.40, 0.85, 1.0), 'fire': (1.0, 0.30, 0.05),
          'smoke': (0.45, 0.45, 0.45), 'flare': (1.0, 1.0, 0.75), 'debris': (0.75, 0.75, 0.75)}

# Words that mark an object as an effect or a volume rather than a hull (matched against an
# object's name split at anything that is not a letter or digit, lower case).
# Hull words that could also name a part ('fire control', 'blast shield', 'keel beam', 'missile
# rack') are left out on purpose; a builder hides its own stand-in effects by name.
EFFECT_WORDS = {'swfx', 'vfx', 'fx', 'plume', 'plumes', 'smoke', 'dust', 'haze', 'steam', 'flame',
                'flames', 'glow', 'spark', 'sparks', 'trail', 'trails', 'flare', 'flares', 'tracer',
                'tracers', 'volume', 'shadow', 'debris', 'plasma', 'bead', 'embers', 'shimmer', 'wash',
                'cloud', 'fog', 'mist'}


def slug(s):
    return re.sub(r'[^A-Za-z0-9]+', '-', s).strip('-')


def words(name):
    return {w for w in re.split(r'[^a-z0-9]+', name.lower()) if w}


# =============================================================================== Blender side

def off(ob):
    """Stop an object drawing in the render. An instancer also stops instancing: Workbench still
    draws a collection instance whose empty is only disabled in renders, so a hull would render
    under its flat copy and its effect volumes would show through as grey solids."""
    ob.hide_render = True
    if ob.instance_type == 'COLLECTION':
        ob.instance_type = 'NONE'


def _alive(idb):
    """False once Blender has deleted the datablock behind a cached reference."""
    try:
        idb.name
        return True
    except ReferenceError:
        return False


class Preview:
    """One preview run in one Blender session: any number of shots, each rendered as its own
    Workbench animation into `out/frames/f#####.png` (global frame numbers at `fps`).

    Per shot: `begin_shot`, then `craft` for every craft (with a `pose(t)` callable when the
    preview moves it, else the scene's own animation moves it), `plume` / `tracer` / `missile` /
    `burst` / `beam` / `shield` / `cone` for the proxies, then `render_shot`. Every proxy
    callable takes t, seconds into the cinematic, and works in world space."""

    def __init__(self, scene, out, fps=24, step=2, scale=50, light='STUDIO', dry=False):
        import bpy
        self.bpy, self.scene = bpy, scene
        self.out = Path(out)
        self.frames = self.out / 'frames'
        self.frames.mkdir(parents=True, exist_ok=True)
        self.fps, self.step, self.scale, self.dry = fps, max(1, int(step)), int(scale), dry
        mf = self.out / 'manifest.json'
        self.manifest = json.loads(mf.read_text()) if mf.exists() else {}
        self.manifest.update(fps=fps, step=self.step, scale=self.scale, size=[W, H])
        self.manifest.setdefault('crafts', {})
        self.manifest.setdefault('shots', {})
        self.coll = bpy.data.collections.new('Motion preview')
        scene.collection.children.link(self.coll)
        self._mats, self._baked, self._used = {}, {}, {}
        self._shot = None
        self._units()
        self._setup(light)

    # ------------------------------------------------------------------ scene settings
    def _setup(self, light):
        s = self.scene
        s.render.engine = 'BLENDER_WORKBENCH'
        s.render.resolution_x, s.render.resolution_y = W, H
        s.render.resolution_percentage = self.scale
        sh = s.display.shading
        sh.light = light                      # STUDIO: free, and shows attitude and bank; FLAT: pure silhouettes
        sh.color_type = 'TEXTURE'             # image textures flat; everything else its material's colour
        sh.show_object_outline = True
        sh.object_outline_color = (0.0, 0.0, 0.0)
        for attr in ('show_cavity', 'show_shadows', 'show_xray', 'show_specular_highlight', 'use_dof'):
            if hasattr(sh, attr):
                setattr(sh, attr, False)
        s.display.render_aa = '5'
        s.render.use_motion_blur = False
        s.render.film_transparent = False
        s.render.use_stamp = False
        s.view_settings.view_transform = 'Standard'   # flat colours stay what they are
        try:
            s.view_settings.look = 'None'
        except TypeError:
            pass
        s.render.image_settings.file_format = 'PNG'
        s.render.image_settings.color_mode = 'RGB'
        if s.world is None:
            s.world = self.bpy.data.worlds.new('Preview sky')
        s.world.color = (0.012, 0.014, 0.02)

    def sky(self, rgb):
        """The flat background (Workbench draws the world's viewport colour)."""
        self.scene.world.color = rgb

    def _units(self):
        import bmesh
        bpy = self.bpy

        def mesh(name, build):
            bm = bmesh.new()
            build(bm)
            me = bpy.data.meshes.new(name)
            bm.to_mesh(me)
            bm.free()
            return me
        # cone: base radius 1 at z=0, point at z=1 (a plume from the nozzle down +Z)
        self.cone_me = mesh('Preview cone', lambda bm: (
            bmesh.ops.create_cone(bm, cap_ends=True, segments=12, radius1=1.0, radius2=0.0, depth=1.0),
            bmesh.ops.translate(bm, verts=bm.verts[:], vec=(0, 0, .5))))
        # rod: radius 1, z from 0 to 1 (a line between two points)
        self.rod_me = mesh('Preview rod', lambda bm: (
            bmesh.ops.create_cone(bm, cap_ends=True, segments=6, radius1=1.0, radius2=1.0, depth=1.0),
            bmesh.ops.translate(bm, verts=bm.verts[:], vec=(0, 0, .5))))
        self.ball_me = mesh('Preview ball', lambda bm: bmesh.ops.create_icosphere(bm, subdivisions=1, radius=1.0))

        def shell(bm, half):
            bmesh.ops.create_icosphere(bm, subdivisions=2, radius=1.0)
            if half:
                bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.z < -1e-4], context='VERTS')
        self.shell_me = mesh('Preview shell', lambda bm: shell(bm, False))
        self.cap_me = mesh('Preview half shell', lambda bm: shell(bm, True))

    def flat(self, rgb):
        """A flat material of this colour (Workbench reads the viewport display colour)."""
        key = tuple(round(c, 3) for c in rgb)
        if key in self._mats and not _alive(self._mats[key]):
            print('PREVIEW material %s was deleted by the scene; made again' % (key,), flush=True)
            del self._mats[key]
        if key not in self._mats:
            m = self.bpy.data.materials.new('Preview %.2f %.2f %.2f' % key)
            m.diffuse_color = (*key, 1.0)
            self._mats[key] = m
        return self._mats[key]

    # ------------------------------------------------------------------ hiding effects
    def is_effect(self, ob):
        """A volume, an effect object, or anything hung under a thruster marker."""
        if ob.type == 'VOLUME':
            return True
        if ob.get('thrust_plume') or ob.get('thrust_kind'):
            return True
        if words(ob.name) & EFFECT_WORDS:
            return True
        for slot in getattr(ob, 'material_slots', ()):
            m = slot.material
            if m is None or m.node_tree is None:
                continue
            for n in m.node_tree.nodes:
                if n.type == 'OUTPUT_MATERIAL' and n.inputs['Volume'].is_linked and not n.inputs['Surface'].is_linked:
                    return True
        p = ob.parent
        while p is not None:
            if p.get('thrust_kind'):
                return True
            p = p.parent
        if ob.type == 'MESH' and any(ps for ps in ob.particle_systems):
            return False                      # an emitter is usually a surface too; its particles are hidden below
        return False

    def hide_effects(self, objects=None, also=(), materials=()):
        """Hide every effect and volume among `objects` (default: the whole scene), plus any object
        whose name contains one of `also` or that wears a material named in `materials` (a shot's
        own list of effect and blended-card materials). Particle systems are switched off for render."""
        n = 0
        materials = set(materials)
        for ob in (objects if objects is not None else self.scene.objects):
            if ob.name in self.coll.objects:
                continue
            if (self.is_effect(ob) or any(a in ob.name for a in also)
                    or any(sl.material is not None and sl.material.name in materials
                           for sl in getattr(ob, 'material_slots', ()))):
                off(ob)
                n += 1
            for mod in getattr(ob, 'modifiers', ()):
                if mod.type in ('PARTICLE_SYSTEM', 'FLUID'):
                    mod.show_render = False
        print('PREVIEW hid %d effect objects' % n, flush=True)
        return n

    # ------------------------------------------------------------------ crafts
    def _bake(self, coll):
        """One mesh of everything a collection instance draws (hull parts, turrets, fittings; not
        its effects), in the collection's own space. Shared by every craft of that collection."""
        if coll.name in self._baked and _alive(self._baked[coll.name]):
            return self._baked[coll.name]
        import time
        import numpy as np
        bpy = self.bpy
        clock = time.time()
        e = bpy.data.objects.new('Preview bake', None)      # at the origin: instances come out in the collection's space
        e.instance_type = 'COLLECTION'
        e.instance_collection = coll
        self.scene.collection.objects.link(e)
        dg = bpy.context.evaluated_depsgraph_get()
        dg.update()
        arrays = {}                    # one source mesh's (co, corner verts, face sizes), however often it is drawn
        cos, corners, sizes = [], [], []
        nv = parts = 0
        for inst in dg.object_instances:
            if not inst.is_instance or inst.parent is None or inst.parent.original != e:
                continue                                      # (a nested instance's parent is the top instancer)
            ob = inst.object
            orig = ob.original
            if ob.type not in ('MESH', 'CURVE', 'SURFACE', 'META', 'FONT'):
                continue
            if orig.hide_render or self.is_effect(orig):
                continue
            plain = ob.type == 'MESH' and not orig.modifiers and not orig.data.shape_keys
            key = ('data', orig.data.name) if plain else ('object', orig.name)
            if key not in arrays:
                me = orig.data if plain else ob.to_mesh()
                if me is None or not len(me.polygons):
                    arrays[key] = None
                else:
                    co = np.empty(len(me.vertices) * 3, dtype=np.float32)
                    me.vertices.foreach_get('co', co)
                    cv = np.empty(len(me.loops), dtype=np.int32)
                    me.loops.foreach_get('vertex_index', cv)
                    fs = np.empty(len(me.polygons), dtype=np.int32)
                    me.polygons.foreach_get('loop_total', fs)
                    arrays[key] = (co.reshape(-1, 3), cv, fs)
                if not plain:
                    ob.to_mesh_clear()
            a = arrays[key]
            if a is None:
                continue
            m = np.array(inst.matrix_world, dtype=np.float32)
            cos.append(a[0] @ m[:3, :3].T + m[:3, 3])
            corners.append(a[1] + nv)
            sizes.append(a[2])
            nv += len(a[0])
            parts += 1
        out = bpy.data.meshes.new('Preview hull | ' + coll.name)
        if parts:
            co, cv, fs = np.concatenate(cos), np.concatenate(corners), np.concatenate(sizes)
            out.vertices.add(len(co))
            out.vertices.foreach_set('co', co.ravel())
            out.loops.add(len(cv))
            out.loops.foreach_set('vertex_index', cv)
            out.polygons.add(len(fs))
            out.polygons.foreach_set('loop_start', np.concatenate(([0], np.cumsum(fs)[:-1])).astype(np.int32))
            try:                       # face sizes follow from the starts in Blender 4+; older ones need them
                out.polygons.foreach_set('loop_total', fs)
            except (AttributeError, TypeError, RuntimeError):
                pass
            out.update(calc_edges=True)
            if out.validate():
                print('PREVIEW %s: baked mesh needed repair' % coll.name, flush=True)
        bpy.data.objects.remove(e)
        print('PREVIEW baked %s: %d parts, %d faces in %.1f s' % (coll.name, parts, len(out.polygons),
                                                                time.time() - clock), flush=True)
        self._baked[coll.name] = out
        return out

    def _paint(self, ob, mat):
        for slot in ob.material_slots:
            slot.link = 'OBJECT'
            slot.material = mat
        if not ob.material_slots and ob.type == 'MESH':
            ob.data.materials.append(None)
            ob.material_slots[0].link = 'OBJECT'
            ob.material_slots[0].material = mat

    def colour_for(self, name, group=None):
        """A craft's colour: the one it was first given in this run, so it never changes between shots."""
        known = self.manifest['crafts'].get(name)
        if known:
            return tuple(known['rgb'])
        pal = {'cool': COOL, 'warm': WARM}.get(group, NEUTRAL)
        k = self._used.get(group, sum(1 for c in self.manifest['crafts'].values() if c.get('group') == group))
        self._used[group] = k + 1
        return pal[k % len(pal)]

    def craft(self, name, obj, label=None, group=None, pose=None, held=False, rgb=None):
        """Paint a craft in its own colour and track it. obj places the craft: an empty instancing a
        collection (baked into one flat mesh; the empty stops drawing) or a hull root whose meshes
        are painted in place. pose(t) -> 4x4 world matrix moves it (keyed per preview frame); without
        it the scene's own animation moves it. held marks a craft the shot does not move."""
        rgb = tuple(rgb or self.colour_for(name, group))
        mat = self.flat(rgb)
        body = obj
        if obj.instance_type == 'COLLECTION' and obj.instance_collection is not None:
            body = self.bpy.data.objects.new('Preview | ' + name, self._bake(obj.instance_collection))
            self.coll.objects.link(body)
            body.matrix_world = obj.matrix_world.copy()
            off(obj)
            if pose is None:              # follow the instancer's own animation
                body.parent = obj
                body.matrix_parent_inverse.identity()
                body.matrix_basis.identity()
            self._paint(body, mat)
            self._shot['objects'].append(body)
        else:
            for o in [obj] + list(obj.children_recursive):
                if o.hide_render or self.is_effect(o):
                    continue
                if o.instance_type == 'COLLECTION' and o.instance_collection is not None:
                    sub = self.bpy.data.objects.new('Preview | %s | %s' % (name, o.name), self._bake(o.instance_collection))
                    self.coll.objects.link(sub)
                    sub.parent = o
                    sub.matrix_parent_inverse.identity()
                    off(o)
                    self._paint(sub, mat)
                    self._shot['objects'].append(sub)
                elif o.type in ('MESH', 'CURVE', 'SURFACE', 'META', 'FONT'):
                    self._paint(o, mat)
        self.manifest['crafts'].setdefault(name, dict(rgb=list(rgb), label=label or name, group=group))
        self._shot['crafts'][name] = dict(obj=body, pose=pose, held=held)
        return body

    # ------------------------------------------------------------------ proxies
    def _proxy(self, name, me, kind, rgb=None):
        if not all(_alive(m) for m in (self.cone_me, self.rod_me, self.ball_me, self.shell_me, self.cap_me)):
            print('PREVIEW unit meshes were deleted by the scene; made again', flush=True)
            self._units()
            me = {'Preview cone': self.cone_me, 'Preview rod': self.rod_me, 'Preview ball': self.ball_me,
                  'Preview shell': self.shell_me, 'Preview half shell': self.cap_me}.get(getattr(me, 'name', ''), self.ball_me)
        o = self.bpy.data.objects.new('Preview fx | ' + name, me)
        self.coll.objects.link(o)
        self._paint(o, self.flat(rgb or FX_RGB[kind]))
        o.rotation_mode = 'QUATERNION'
        self._shot['objects'].append(o)
        return o

    def _wire(self, o, thickness=.03):
        mod = o.modifiers.new('Wire', 'WIREFRAME')
        mod.thickness = thickness
        mod.use_replace = True
        return o

    def px(self, d):
        """World size of one rendered pixel at distance d from the camera."""
        cam = self.scene.camera.data
        return d * (cam.sensor_width / cam.lens) / (W * self.scale / 100.0)

    def _cam(self):
        return self.scene.camera.matrix_world.translation

    def _set(self, o, matrix, visible):
        """Store one frame's state; render_shot keys it."""
        o['_m'] = [list(r) for r in matrix] if visible else None

    def _seg_matrix(self, p0, p1, radius):
        from mathutils import Matrix
        d = p1 - p0
        L = max(d.length, 1e-6)
        q = (d / L).to_track_quat('Z', 'Y')
        return Matrix.Translation(p0) @ q.to_matrix().to_4x4() @ Matrix.Diagonal((radius, radius, L, 1))

    def plume(self, name, at, rgb_kind='plume'):
        """A drive plume. at(t) -> (origin, direction, radius_m, throttle 0..1), world space; the cone
        runs down the exhaust, 4 + 8 x throttle nozzle radii long (the Asharu stand-in's rule), and
        is gone at zero throttle. A plume is drawn at least two pixels wide so a far drive still reads."""
        from mathutils import Matrix
        o = self._proxy('plume ' + name, self.cone_me, rgb_kind)

        def upd(t):
            p, d, r, thr = at(t)
            if thr <= 0.01:
                return self._set(o, None, False)
            r = max(r, self.px((p - self._cam()).length) * 1.2)
            L = r * (4 + 8 * thr)
            q = d.normalized().to_track_quat('Z', 'Y')
            self._set(o, Matrix.Translation(p) @ q.to_matrix().to_4x4() @ Matrix.Diagonal((r * .9, r * .9, L, 1)), True)
        self._shot['updates'].append((o, upd))

    def line(self, name, ends, kind='beam', width_px=1.5, rgb=None):
        """A line between two moving points: ends(t) -> (p0, p1), or None while it is off."""
        o = self._proxy(name, self.rod_me, kind, rgb)

        def upd(t):
            e = ends(t)
            if e is None:
                return self._set(o, None, False)
            p0, p1 = e
            r = self.px(((p0 + p1) / 2 - self._cam()).length) * width_px / 2
            self._set(o, self._seg_matrix(p0, p1, r), True)
        self._shot['updates'].append((o, upd))

    def tracer(self, name, ends, t0, t1, every=.45, flight=.6, dashes=3):
        """Gunfire from a to b between t0 and t1: bursts of short dashes, one burst every `every`
        seconds, each dash crossing the distance in `flight` seconds. ends(t) -> (muzzle, target)."""
        for i in range(dashes):
            def dash(t, i=i):
                if not (t0 <= t <= t1 + flight):
                    return None
                age = (t - t0) % every - i * .06
                if age < 0 or age > flight or t - age > t1:
                    return None
                a, b = ends(t)
                f = age / flight
                g = min(1.0, f + max(.04, 25.0 / max((b - a).length, 1.0)))
                return a.lerp(b, f), a.lerp(b, g)
            self.line('%s %d' % (name, i), dash, 'tracer', width_px=2.0)

    def missile(self, name, ends, launch, arrive, wobble=0.0):
        """One missile from a to b, launched at `launch`, arriving at `arrive`: a short white streak
        with its exhaust trail behind it. ends(t) -> (launcher, target)."""
        def body(t):
            if not (launch <= t <= arrive):
                return None
            a, b = ends(t)
            f = (t - launch) / max(arrive - launch, 1e-3)
            p = a.lerp(b, f)
            back = (a - b).normalized()
            n = self.px((p - self._cam()).length)
            return p, p - back * n * 8.0     # a head 8 px long, pointing at the target
        self.line(name + ' head', body, 'missile', width_px=3.0)

        def trail(t):
            if not (launch <= t <= arrive):
                return None
            a, b = ends(t)
            f = (t - launch) / max(arrive - launch, 1e-3)
            p = a.lerp(b, f)
            q = a.lerp(b, max(0.0, f - .12))
            return q, p
        self.line(name + ' trail', trail, 'plume', width_px=1.5)

    def burst(self, name, at, t, radius, life=.8, kind='burst'):
        """A brief wire shell: grows from 0.3 to 1.2 x radius over `life` seconds from t. at(t) -> centre."""
        from mathutils import Matrix
        o = self._wire(self._proxy(name, self.shell_me, kind))

        def upd(tt):
            if not (t <= tt <= t + life):
                return self._set(o, None, False)
            f = (tt - t) / life
            r = max(radius * (.3 + .9 * f), self.px((at(tt) - self._cam()).length) * 4)
            self._set(o, Matrix.Translation(at(tt)) @ Matrix.Scale(r, 4), True)
        self._shot['updates'].append((o, upd))

    def shield(self, name, at, radius, toward=None, t0=-1e9, t1=1e9):
        """A wire shell round a craft (a half shell facing toward(t) when given). at(t) -> centre."""
        from mathutils import Matrix
        o = self._wire(self._proxy(name, self.cap_me if toward else self.shell_me, 'shield'), .015)

        def upd(t):
            if not (t0 <= t <= t1):
                return self._set(o, None, False)
            c = at(t)
            rot = Matrix.Identity(4)
            if toward:
                rot = (toward(t) - c).normalized().to_track_quat('Z', 'Y').to_matrix().to_4x4()
            self._set(o, Matrix.Translation(c) @ rot @ Matrix.Scale(radius, 4), True)
        self._shot['updates'].append((o, upd))

    def cone(self, name, at, kind='fire', min_px=3):
        """A fixed cone: at(t) -> (origin, direction, radius, length) or None (fire, smoke)."""
        from mathutils import Matrix
        o = self._proxy(name, self.cone_me, kind)

        def upd(t):
            v = at(t)
            if v is None:
                return self._set(o, None, False)
            p, d, r, L = v
            r = max(r, self.px((p - self._cam()).length) * min_px / 2)
            q = d.normalized().to_track_quat('Z', 'Y')
            self._set(o, Matrix.Translation(p) @ q.to_matrix().to_4x4() @ Matrix.Diagonal((r, r, max(L, 2 * r), 1)), True)
        self._shot['updates'].append((o, upd))

    def dots(self, name, at, kind='flare', n=8, size_px=3):
        """n small balls: at(t) -> list of n positions (or None to hide them)."""
        from mathutils import Matrix
        objs = [self._proxy('%s %d' % (name, i), self.ball_me, kind) for i in range(n)]

        def upd(t):
            ps = at(t)
            for i, o in enumerate(objs):
                if ps is None or i >= len(ps) or ps[i] is None:
                    self._set(o, None, False)
                else:
                    r = self.px((ps[i] - self._cam()).length) * size_px / 2
                    self._set(o, Matrix.Translation(ps[i]) @ Matrix.Scale(r, 4), True)
        for o in objs:
            self._shot['updates'].append((o, lambda t: None))
        self._shot['updates'].append((objs[0], upd))

    # ------------------------------------------------------------------ shots
    def begin_shot(self, shot_id, title, start_s, end_s, caption='', key_s=None, notes=()):
        """A shot from start_s to end_s seconds into the cinematic (the sequence's own clock)."""
        self._shot = dict(id=shot_id, title=title, caption=caption, start=round(start_s * self.fps),
                          end=round(end_s * self.fps), key=None if key_s is None else round(key_s * self.fps),
                          notes=list(notes), crafts={}, updates=[], objects=[])

    def _frames(self):
        s = self._shot
        first = s['start'] + (-s['start']) % self.step
        return list(range(first, s['end'], self.step))

    def render_shot(self, before=None):
        """Key every craft and proxy on every preview frame of the shot, record where each craft is
        on screen, and render the shot as one Workbench animation. before(frame, t) runs first on
        each frame (a builder's own per-frame pose); the scene's frame is set for each one."""
        from bpy_extras.object_utils import world_to_camera_view
        import time
        from mathutils import Matrix
        bpy, scene, s = self.bpy, self.scene, self._shot
        frames = self._frames()
        clock = time.time()
        track = {}
        animated = {id(o): o for o, _ in s['updates']}
        for c in s['crafts'].values():
            if c['pose'] is not None:
                c['obj'].rotation_mode = 'QUATERNION'
        for o in animated.values():
            o.hide_render = False
        edit = bpy.context.preferences.edit       # constant between keys: nothing slides between preview frames
        interp, edit.keyframe_new_interpolation_type = edit.keyframe_new_interpolation_type, 'CONSTANT'
        for f in frames:
            t = f / self.fps
            scene.frame_set(f)
            if before is not None:
                before(f, t)
            for name, c in s['crafts'].items():
                if c['pose'] is not None:
                    c['obj'].matrix_world = c['pose'](t)
                    for path in ('location', 'rotation_quaternion', 'scale'):
                        c['obj'].keyframe_insert(path, frame=f)
            bpy.context.view_layer.update()
            for o, upd in s['updates']:
                upd(t)
            for o in animated.values():
                m = o.get('_m')
                o.hide_render = m is None
                o.keyframe_insert('hide_render', frame=f)
                if m is not None:
                    o.matrix_world = Matrix(m)
                    for path in ('location', 'rotation_quaternion', 'scale'):
                        o.keyframe_insert(path, frame=f)
            cam = scene.camera
            row = {}
            for name, c in s['crafts'].items():
                p = c['obj'].matrix_world.translation
                v = world_to_camera_view(scene, cam, p)
                row[name] = [round(v.x * W, 1), round((1 - v.y) * H, 1), round(v.z, 1)]
            track[str(f)] = row
        edit.keyframe_new_interpolation_type = interp
        rec = {k: s[k] for k in ('id', 'title', 'caption', 'start', 'end', 'key', 'notes')}
        rec.update(rendered=True, crafts={n: dict(held=c['held']) for n, c in s['crafts'].items()})
        (self.frames / ('track-%s.json' % slug(s['id']))).write_text(json.dumps(track))
        todo = [f for f in frames if not (self.frames / ('f%05d.png' % f)).exists()]
        if todo and not self.dry:
            scene.render.filepath = str(self.frames / 'f#####')
            scene.render.use_file_extension = True
            scene.render.use_overwrite = False       # a resumed job keeps the frames it has
            scene.render.use_placeholder = True
            scene.frame_start, scene.frame_end, scene.frame_step = frames[0], frames[-1], self.step
            keyed = time.time()
            bpy.ops.render.render(animation=True)
        else:
            keyed = time.time()
        print('PREVIEW shot %s: %d frames (%d rendered now)%s, keyed in %.0f s, rendered in %.0f s' % (
            s['id'], len(frames), len(todo), ' DRY' if self.dry else '', keyed - clock, time.time() - keyed), flush=True)
        self.manifest['shots'][s['id']] = rec
        (self.out / 'manifest.json').write_text(json.dumps(self.manifest, indent=1))
        return rec

    def end_shot(self):
        """Remove the shot's preview objects (the next shot builds its own)."""
        for o in self._shot['objects']:
            if o.name in self.bpy.data.objects:
                self.bpy.data.objects.remove(o)
        self._shot = None


def preview_animated_scene(scene, out, shot, title, frames=None, step=2, scale=50, caption='',
                           hide=(), hide_materials=(), fps=None, dry=False):
    """A Jangala-style shot that animates itself and carries thruster markers: every craft the
    markers name is painted in its own colour, every working main and lift nozzle gets a plume
    cone that follows its keyed throttle, effects and volumes are hidden, and the shot renders as
    one Workbench animation. frames: (first, last) scene frames (default the scene's range)."""
    import bpy
    from mathutils import Vector
    sys.path.insert(0, str(HERE.parent / 'second-wind-cinematic' / 'thrust-markers'))
    import thrust_markers as tm
    fps = fps or round(scene.render.fps / scene.render.fps_base)
    a, b = frames or (scene.frame_start, scene.frame_end)
    mp = Preview(scene, out, fps=fps, step=step, scale=scale, dry=dry)
    mp.hide_effects(also=hide, materials=hide_materials)
    mp.begin_shot(shot, title, a / fps, (b + 1) / fps, caption=caption)
    marks = [m for m in bpy.data.objects if m.get('thrust_kind') in ('main', 'lift')]
    crafts = {}
    for m in marks:
        root = tm.top(m)
        crafts.setdefault(m.get('thrust_craft') or root.name, root)
    for name, root in sorted(crafts.items()):
        mp.craft(name, root, label=name)
    for m in marks:
        if m.get('thrust_working') is False:
            continue
        r_m = float(m.get('radius_m', 1.0))

        def at(t, m=m, r_m=r_m):
            mw = m.matrix_world                   # render_shot has set the scene to this frame
            return mw.translation.copy(), mw.to_3x3() @ Vector((1, 0, 0)), r_m, tm.throttle_at(m, round(t * fps))
        mp.plume('%s %s' % (m.get('thrust_craft'), m.name.split('|')[-1].strip()), at,
                 'plume' if m['thrust_kind'] == 'main' else 'plume-white')
    print('PREVIEW %s: %d crafts, %d nozzles' % (shot, len(crafts), len(marks)), flush=True)
    rec = mp.render_shot()
    mp.end_shot()
    return rec


# =============================================================================== plain Python side

def _font(size, bold=False):
    sys.path.insert(0, str(ART))
    from review_font import review_font
    return review_font(size, bold)


def timecode(f, fps):
    s, ff = divmod(f, fps)
    m, s = divmod(s, 60)
    h, m = divmod(m, 60)
    return '%02d:%02d:%02d:%02d' % (h, m, s, ff)


def _wrap(dr, text, font, width):
    out, line = [], ''
    for w in text.split():
        t = (line + ' ' + w).strip()
        if dr.textlength(t, font=font) > width and line:
            out.append(line)
            line = w
        else:
            line = t
    return out + ([line] if line else [])


def finish(out, ffmpeg='ffmpeg', name='motion-preview', trails=2.0, tags=True, timeline=None,
           sequence='', stills_width=1600, web_width=1280):
    """Overlays, MP4 and stills for a rendered run. timeline: the full ordered list of shot ids
    (with their slates' text) when the sequence has shots the run did not render:
    [dict(id, title, caption, start, end, rendered=False), ...]; default: the manifest's shots.
    An entry may carry screen(f) -> a 1920x1080 PIL image, or None for the rendered frame: a shot
    that is a 2D screen rather than a camera (a playback screen), or one that breaks into one."""
    from PIL import Image, ImageDraw
    out = Path(out)
    man = json.loads((out / 'manifest.json').read_text())
    fps, step = man['fps'], man['step']
    shots = timeline or sorted(man['shots'].values(), key=lambda s: s['start'])
    shots = [dict(man['shots'].get(s['id'], s), **({'screen': s['screen']} if s.get('screen') else {}))
             if s.get('rendered', True) else s for s in shots]
    crafts = man['crafts']
    f_big, f_mid, f_small, f_tag = _font(64, True), _font(26, True), _font(21), _font(16, True)
    jpg = out / 'jpg'
    jpg.mkdir(exist_ok=True)
    mp4 = out / (name + '.mp4')
    enc = subprocess.Popen([ffmpeg, '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24',
                            '-s', '%dx%d' % (W, H), '-r', '%g' % (fps / step), '-i', '-',
                            '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p',
                            '-movflags', '+faststart', str(mp4)], stdin=subprocess.PIPE)
    t0 = shots[0]['start'] if shots else 0
    stills = []
    n_out = 0
    for si, s in enumerate(shots):
        track = {}
        tf = out / 'frames' / ('track-%s.json' % slug(s['id']))
        if s.get('rendered') and tf.exists():
            track = {int(k): v for k, v in json.loads(tf.read_text()).items()}
        first = s['start'] + (-s['start']) % step
        frames = list(range(first, s['end'], step))
        key = s.get('key')
        if key is None or not (s['start'] <= key < s['end']):
            key = frames[len(frames) // 2] if frames else None
        key_f = min(frames, key=lambda f: abs(f - key)) if frames else None
        for f in frames:
            im = s['screen'](f) if s.get('screen') else None
            if im is not None:
                im = im.convert('RGB')
            elif s.get('rendered'):
                png = out / 'frames' / ('f%05d.png' % f)
                if png.exists():
                    im = Image.open(png).convert('RGB').resize((W, H), Image.BILINEAR)
                else:
                    im = Image.new('RGB', (W, H), (40, 40, 44))
                    ImageDraw.Draw(im).text((W / 2 - 200, H / 2), 'frame not rendered (dry run)', font=f_mid,
                                            fill=(200, 200, 200))
                im = _overlay(im, s, f, fps, step, track, crafts, trails, tags, sequence, (f_mid, f_small, f_tag))
            else:
                im = _slate(s, f, fps, sequence, (f_big, f_mid, f_small))
            enc.stdin.write(im.tobytes())
            n_out += 1
            if f == key_f:
                nm = '%02d-%s.jpg' % (si + 1, slug(s['id']).lower())
                im.resize((stills_width, round(stills_width * H / W)), Image.LANCZOS).save(jpg / nm, quality=88)
                stills.append(dict(file='jpg/' + nm, shot=s['id'], title=s['title'], frame=f,
                                   time=round((f - t0) / fps, 2), rendered=bool(s.get('rendered'))))
    enc.stdin.close()
    if enc.wait():
        raise SystemExit('ffmpeg failed')
    web = out / (name + '-web.mp4')
    subprocess.check_call([ffmpeg, '-y', '-loglevel', 'error', '-i', str(mp4), '-vf', 'scale=%d:-2' % web_width,
                           '-c:v', 'libx264', '-preset', 'slow', '-crf', '27', '-pix_fmt', 'yuv420p',
                           '-movflags', '+faststart', str(web)])
    summary = dict(mp4=mp4.name, web=web.name, frames=n_out, playback_fps=fps / step,
                   seconds=round(n_out * step / fps, 2), stills=stills,
                   shots=[dict(id=s['id'], title=s['title'], rendered=bool(s.get('rendered')),
                               seconds=round((s['end'] - s['start']) / fps, 2), notes=s.get('notes', []))
                          for s in shots])
    (out / 'preview.json').write_text(json.dumps(summary, indent=1))
    print('PREVIEW wrote', mp4, '(%d frames at %g fps)' % (n_out, fps / step), flush=True)
    return summary


def _overlay(im, s, f, fps, step, track, crafts, trails, tags, sequence, fonts):
    from PIL import ImageDraw
    f_mid, f_small, f_tag = fonts
    dr = ImageDraw.Draw(im, 'RGBA')
    # action safe 93%, title safe 90%
    for frac, col in ((.93, (255, 255, 255, 70)), (.90, (255, 220, 90, 90))):
        mx, my = W * (1 - frac) / 2, H * (1 - frac) / 2
        dr.rectangle((mx, my, W - mx, H - my), outline=col, width=1)
    dr.line((W / 2 - 14, H / 2, W / 2 + 14, H / 2), fill=(255, 255, 255, 60))
    dr.line((W / 2, H / 2 - 14, W / 2, H / 2 + 14), fill=(255, 255, 255, 60))
    # trails: each craft's screen path over the last `trails` seconds of this shot
    if trails > 0:
        span = round(trails * fps)
        for name in s.get('crafts', {}):
            pts = [track[g][name] for g in range(max(s['start'], f - span), f + 1)
                   if g in track and name in track[g] and track[g][name][2] > 0]
            rgb = tuple(int(255 * c) for c in crafts[name]['rgb'])
            for i in range(1, len(pts)):
                a = int(40 + 180 * i / len(pts))
                dr.line((pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1]), fill=rgb + (a,), width=2)
    # on-screen tags
    here = track.get(f, {})
    if tags:
        for name, p in here.items():
            if p[2] <= 0 or not (0 <= p[0] <= W and 0 <= p[1] <= H):
                continue
            rgb = tuple(int(255 * c) for c in crafts[name]['rgb'])
            txt = crafts[name]['label']
            dr.text((p[0] + 9, p[1] + 6), txt, font=f_tag, fill=(0, 0, 0, 200))
            dr.text((p[0] + 8, p[1] + 5), txt, font=f_tag, fill=rgb + (255,))
    # top bar: shot and clock
    dr.rectangle((0, 0, W, 46), fill=(0, 0, 0, 165))
    head = '%s%s  %s' % (sequence + '  ' if sequence else '', s['id'], s['title'])
    dr.text((18, 9), head, font=f_mid, fill=(255, 255, 255, 255))
    clock = 'f %05d   shot %04d/%04d   %s   %g fps preview of %d' % (
        f, f - s['start'], s['end'] - s['start'], timecode(f, fps), fps / step, fps)
    dr.text((W - 18 - dr.textlength(clock, font=f_small), 12), clock, font=f_small, fill=(230, 230, 230, 255))
    if s.get('key') is not None and abs(f - s['key']) < step:
        tw = dr.textlength('KEY FRAME', font=f_small)
        dr.rectangle((W / 2 - tw / 2 - 14, 54, W / 2 + tw / 2 + 14, 86), fill=(255, 210, 60, 220))
        dr.text((W / 2 - tw / 2, 58), 'KEY FRAME', font=f_small, fill=(0, 0, 0, 255))
    # legend: this shot's crafts, then its notes
    names = list(s.get('crafts', {}))
    if names:
        cols = 2 if len(names) > 9 else 1
        rows = math.ceil(len(names) / cols)
        bw = 270 * cols
        x0, y0 = 24, H - 30 - rows * 26 - 14
        dr.rectangle((x0 - 10, y0 - 10, x0 + bw, H - 34), fill=(0, 0, 0, 150))
        for i, name in enumerate(names):
            c, r = divmod(i, rows)
            x, y = x0 + c * 270, y0 + r * 26
            rgb = tuple(int(255 * v) for v in crafts[name]['rgb'])
            dr.rectangle((x, y + 3, x + 18, y + 21), fill=rgb + (255,))
            lab = crafts[name]['label'] + ('  (held)' if s['crafts'][name].get('held') else '')
            dr.text((x + 26, y + 1), lab, font=f_small, fill=(235, 235, 235, 255))
    notes = s.get('notes') or []
    if notes:
        y = H - 34 - 26 * len(notes)
        for n in notes:
            tw = dr.textlength(n, font=f_small)
            dr.rectangle((W - tw - 34, y - 2, W - 14, y + 24), fill=(0, 0, 0, 150))
            dr.text((W - tw - 24, y), n, font=f_small, fill=(255, 220, 140, 255))
            y += 26
    return im


def _slate(s, f, fps, sequence, fonts):
    from PIL import Image, ImageDraw
    f_big, f_mid, f_small = fonts
    im = Image.new('RGB', (W, H), (14, 15, 18))
    dr = ImageDraw.Draw(im, 'RGBA')
    dr.text((160, 330), s['id'], font=f_big, fill=(255, 255, 255))
    dr.text((160, 420), s['title'], font=f_mid, fill=(230, 230, 230))
    dr.text((160, 470), 'not blocked out yet: %.0f s held on this card' % ((s['end'] - s['start']) / fps),
            font=f_small, fill=(255, 200, 120))
    y = 530
    for line in _wrap(dr, s.get('caption', ''), f_small, W - 320)[:8]:
        dr.text((160, y), line, font=f_small, fill=(190, 190, 190))
        y += 30
    clock = '%s   f %05d   %s' % (sequence, f, timecode(f, fps))
    dr.text((W - 40 - dr.textlength(clock, font=f_small), H - 50), clock, font=f_small, fill=(150, 150, 150))
    return im


def main():
    argv = sys.argv[1:]
    if not argv or argv[0] not in ('finish', 'run'):
        raise SystemExit(__doc__)
    if '--' in argv:
        own, rest = argv[1:argv.index('--')], argv[argv.index('--') + 1:]
    else:
        own, rest = argv[1:], []

    def opt(flag, default=None):
        return own[own.index(flag) + 1] if flag in own else default
    out = Path(opt('--out', own[0] if own and not own[0].startswith('--') else '.'))
    ffmpeg = opt('--ffmpeg', 'ffmpeg')
    if argv[0] == 'run':
        sys.path.insert(0, str(ART))
        from blender_path import BLENDER
        subprocess.check_call([str(BLENDER), '-b', '--factory-startup', '--python-exit-code', '1'] + rest)
    finish(out, ffmpeg=ffmpeg, name=opt('--name', 'motion-preview'), trails=float(opt('--trails', 2.0)),
           sequence=opt('--sequence', ''))


if __name__ == '__main__':
    main()
