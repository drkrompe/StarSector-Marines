"""Per-ship SWFX effects for a scene that places one build several times (W0 of asharu-effects-plan.md).

The Second Wind VFX kit (`second-wind-vfx/`) dresses one hull per file. Each build's `SWFX`
collection is driven by one `SWFX controls` empty. If a scene instances that collection for
three Lashers, all three read the same knobs and flash in step. `ShipFx` gives each placed
ship its own full copy instead:

- the build's `SWFX` tree, every object, light, mesh and material copied, with every driver,
  parent and pointer retargeted onto the copy, so the copy's drivers read the copy's controls;
- the hull too, when the build drives hull parts from its own control object (the Wolfs'
  `gear_down`, the Mk.III's `grapples`). Hull objects are copied and their meshes shared. A hull
  without a control stays one shared collection, instanced per ship;
- a per-ship time offset on the kit's `frame`-noise drivers, so engine flicker and beacon
  blinks are not in step either. Drivers that read a `*_frame` knob (the Phaeton wreck's
  `break_frame` cook-off) are left on the scene clock.

`ShipFx.set` / `ShipFx.key` drive one ship's knobs. `ShipFx.kit()` hands out a context the
kit's own helpers accept (`swfx_thrusters.ignition(ctx, ...)`, `swfx_guns.fire(ctx, ...)`),
bound to this ship's copy. `audit()` proves the copies are independent: no driver, parent or
pointer of one ship's copy reaches another ship's copy or the library original.

Every copy is also put to metre scale. The kit tunes a build in its own hull-unit file and a
scene places it at Scale(metres_per_unit): distances grow by mpu, but Cycles leaves a light's
power and a volume's per-length density as they were. `metre_scale` gives each light exposure
2*log2(mpu) and divides each volume's density and emission by mpu, so the copy lights and
fogs the metre scene as it did its own file.

And every object of a copy whose material reads its per-object colour (Object Info Color/Alpha,
or an Object attribute `color`: a tracer's burn, a puff's fade and seed) gets a material of its
own with that colour keyed on a node. Drawn through a collection instance, Cycles hands such a
material the instancer's colour, so a keyed `ob.color` never arrived and the puffs never faded.
`own_colours` copies the object's colour curves onto the node; the kit's shared materials are
untouched.

Runs inside Blender only. Used by `battle_blockout.py` (the scene) and `effects_bench.py`.
"""
import contextlib
import math
import hashlib
import importlib
import os
import re
import sys
from pathlib import Path

import bpy

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
KIT = PROLOGUE / 'second-wind-vfx'
if str(KIT) not in sys.path:
    sys.path.insert(0, str(KIT))

ROOT = 'SWFX'
CONTROLS = 'SWFX controls'
SUFFIX = re.compile(r'\.\d{3}$')
COPY = re.compile(r' ~?@[^@]*$')       # ' @<ship key>': a ship's copy; ' ~@<key>': set aside by kit()
STORES = ('objects', 'meshes', 'lights', 'materials', 'node_groups', 'curves', 'actions')
FLASH = 'SWFX guns flash '        # swfx_guns: '<PREFIX> flash <label>' / '<PREFIX> launch <label>'
LAUNCH = 'SWFX guns launch '
ANCHOR_RE = {'engine': re.compile(r'^SWFX anchor engine (\d+)$'),
             'turret': re.compile(r'^SWFX anchor turret (\d+) (.+)$')}


def base_name(name):
    """A copied or re-appended ID's kit name: 'SWFX controls.002 @lasher-a' -> 'SWFX controls'."""
    return SUFFIX.sub('', COPY.sub('', SUFFIX.sub('', name)))


def _drivers(idb):
    for owner in (idb, getattr(idb, 'node_tree', None), getattr(idb, 'shape_keys', None)):
        ad = getattr(owner, 'animation_data', None) if owner is not None else None
        if ad:
            yield from ad.drivers


def _pointers(struct):
    """(identifier, ID) for every writable ID pointer on an RNA struct (constraint, modifier, node)."""
    for p in struct.bl_rna.properties:
        if p.type != 'POINTER' or p.is_readonly:
            continue
        try:
            v = getattr(struct, p.identifier)
        except AttributeError:
            continue
        if isinstance(v, bpy.types.ID):
            yield p.identifier, v


def _remap(M):
    """Point every copied ID's parents, drivers, constraints, modifiers, node pointers and
    materials at the copies in M (old ID -> new ID)."""
    for old, new in M.items():
        if isinstance(new, bpy.types.Collection):
            continue
        for fc in _drivers(new):
            for v in fc.driver.variables:
                for t in v.targets:
                    if t.id in M:
                        t.id = M[t.id]
        if isinstance(new, bpy.types.Object):
            if new.parent in M:
                mpi = new.matrix_parent_inverse.copy()
                new.parent = M[new.parent]
                new.matrix_parent_inverse = mpi
            for c in new.constraints:
                for ident, v in _pointers(c):
                    if v in M:
                        setattr(c, ident, M[v])
            for m in new.modifiers:
                for ident, v in _pointers(m):
                    if v in M:
                        setattr(m, ident, M[v])
            if new.instance_collection in M:
                new.instance_collection = M[new.instance_collection]
            for slot in new.material_slots:
                if slot.link == 'OBJECT' and slot.material in M:
                    slot.material = M[slot.material]
        data_mats = getattr(new, 'materials', None)
        if data_mats is not None and not isinstance(new, bpy.types.Object):
            for i, m in enumerate(data_mats):
                if m in M:
                    data_mats[i] = M[m]
        nt = getattr(new, 'node_tree', None) if not isinstance(new, bpy.types.NodeTree) else new
        if nt is not None:
            for n in nt.nodes:
                for ident, v in _pointers(n):
                    if v in M:
                        setattr(n, ident, M[v])


def _offset_clock(ids, offset):
    """Shift the kit's frame-noise drivers by `offset` frames. A driver reading a '*_frame'
    knob (an event keyed to the scene clock, like the wreck's break_frame) keeps the clock."""
    n = 0
    for idb in ids:
        for fc in _drivers(idb):
            d = fc.driver
            # A variable reading a '*_frame' knob keeps the clock whatever it is called (the
            # wreck's sections read break_frame as 'bf').
            if 'frame' not in d.expression or any('frame' in v.name or 'frame' in v.targets[0].data_path
                                                  for v in d.variables):
                continue
            expr = re.sub(r'\bframe\b', f'(frame+{offset})', d.expression)
            if len(expr) < 250:
                d.expression = expr
                n += 1
    return n


def refresh(scene):
    """swfx_core.refresh_drivers for any number of controls: clear the sticky invalid flags
    and re-evaluate, so drivers read knobs set from Python this session."""
    for o in bpy.data.objects:
        if base_name(o.name) == CONTROLS or o.get('ship_fx_hull_control'):
            o.update_tag()
    bpy.context.view_layer.update()
    for store in (bpy.data.objects, bpy.data.lights, bpy.data.materials, bpy.data.meshes,
                  bpy.data.node_groups, bpy.data.shape_keys):
        for idb in store:
            for fc in _drivers(idb):
                fc.driver.is_valid = True
    scene.frame_set(scene.frame_current)


class ShipFx:
    """One placed ship's own effects (and, for a build with a hull control, its own hull).

    hull_coll / fx_coll: the build's appended (excluded) library collections.
    hull_ctl: the build's hull-side control object (manifest 'control.object'), or None.
    manifest: the build's manifest.json (effects anchors, metres per unit, kit profile).
    own_hull: copy the hull's objects (meshes shared) even without a hull control, so this
    ship's turrets train on their own (swfx_guns.aim)."""

    def __init__(self, key, hull_coll, fx_coll, manifest, hull_ctl=None, clock=True, own_hull=False):
        self.key = key
        self.manifest = manifest
        self.mpu = float(manifest['metres_per_unit'])
        self.M = {}
        own_hull = hull_ctl is not None or own_hull
        self.own_hull = own_hull       # its turrets can train (swfx_guns.aim) without moving another ship's
        if own_hull:
            # Hull parts are copied (their meshes shared) so this ship's gear moves on its own.
            self.hull = self._copy_tree(hull_coll, copy_data=lambda o: _has_drivers(o.data))
        else:
            self.hull = hull_coll
        self.fx = self._copy_tree(fx_coll, copy_data=lambda o: True) if fx_coll is not None else None
        _remap(self.M)
        self.ids = set(self.M.values())
        self.controls = next((n for o, n in self.M.items() if isinstance(o, bpy.types.Object)
                              and base_name(o.name) == CONTROLS), None)
        self.hull_ctl = self.M.get(hull_ctl) if own_hull else hull_ctl
        if self.hull_ctl is not None and own_hull:
            self.hull_ctl['ship_fx_hull_control'] = True
        self.offset = int(hashlib.sha256(key.encode()).hexdigest()[:6], 16) % 997 if clock else 0
        self.clocked = _offset_clock(self.ids, self.offset) if self.offset else 0
        self.rest = {k: v for k, v in (self.controls.items() if self.controls is not None else ())
                     if isinstance(v, (int, float))}
        self.hull_rest = {k: v for k, v in (self.hull_ctl.items() if self.hull_ctl is not None else ())
                          if isinstance(v, (int, float)) and k != 'ship_fx_hull_control'}
        self.groups = {base_name(c.name): c for c in ([self.fx, *self.fx.children_recursive] if self.fx else ())}
        self.own_colours()
        self.metre_scale()

    def remove(self):
        """Delete this copy outright (its objects, data, materials, trees, actions, collections):
        a scene rebuilding a ship for the next key frame starts from the library again, so what
        the kit built for one frame never shows on another."""
        ids = [i for i in self.ids if i is not None]
        for c in (self.fx, self.hull if self.hull is not None and self.hull in self.ids else None):
            if c is not None:
                ids.append(c)
        bpy.data.batch_remove(set(ids))
        self.ids = set()

    # ----------------------------------------------------------------------- per-object colour
    def own_colours(self):
        """Each object here whose material reads its object colour: its own material, with the
        colour keyed on a node from the object's own colour curves (idempotent: re-run after the
        kit's helpers key more). Returns how many objects it routed."""
        n = 0
        for ob in [o for o in self.ids if isinstance(o, bpy.types.Object)]:
            for slot in ob.material_slots:
                m = slot.material
                if m is None or m.node_tree is None:
                    continue
                if m.get(OWN_COLOUR) is None:
                    if not _colour_readers(m.node_tree):
                        continue
                    m2 = m.copy()
                    m2.name = f'{base_name(m.name)} | {base_name(ob.name)} @{self.key}'
                    _route_colour(m2.node_tree)
                    m2[OWN_COLOUR] = base_name(ob.name)
                    self.ids.add(m2)
                    slot.link = 'OBJECT'
                    slot.material = m = m2
                _key_colour(ob, m.node_tree)
                n += 1
        return n

    # ----------------------------------------------------------------------- metre scale
    def metre_scale(self):
        """This copy's lights and volumes, from hull units to the metre scene (idempotent)."""
        r = metre_scale(self.ids, self.mpu)
        if r['unhandled']:
            print(f'SHIP_FX {self.key}: volume inputs not metre-scaled:', sorted(set(r['unhandled'])), flush=True)
        return r

    # ----------------------------------------------------------------------- copying
    def _copy_tree(self, root, copy_data):
        M, key = self.M, self.key

        def coll(c):
            n = bpy.data.collections.new(f'{base_name(c.name)} @{key}')
            M[c] = n
            for ch in c.children:
                n.children.link(coll(ch))
            return n

        new_root = coll(root)
        for c in [root, *root.children_recursive]:
            for o in c.objects:
                if o not in M:
                    o2 = o.copy()
                    o2.name = f'{base_name(o.name)} @{key}'
                    M[o] = o2
                    if o.data is not None and copy_data(o):
                        if o.data not in M:
                            M[o.data] = o.data.copy()
                            M[o.data].name = f'{base_name(o.data.name)} @{key}'
                            for m in getattr(o.data, 'materials', ()):
                                if m is not None and m not in M:
                                    M[m] = m.copy()
                                    M[m].name = f'{base_name(m.name)} @{key}'
                        o2.data = M[o.data]
                    ad = o2.animation_data
                    if ad and ad.action:
                        if ad.action not in M:
                            M[ad.action] = ad.action.copy()
                        ad.action = M[ad.action]
                    for slot in o.material_slots:
                        if slot.link == 'OBJECT' and slot.material is not None and slot.material not in M:
                            M[slot.material] = slot.material.copy()
                    for mod in o.modifiers:
                        if mod.type == 'NODES' and mod.node_group is not None:
                            self._copy_node_group(mod.node_group)
                M[c].objects.link(M[o])
        return new_root

    def _copy_node_group(self, tree):
        """A geometry-nodes tree with drivers (the fire group's embers read fir_exp_frame) is this
        ship's own, with the materials its Set Material sockets name; _remap then points the
        modifier, its drivers and those sockets at the copies. Shared, the embers of every copy
        kept the library original's clock."""
        M, key = self.M, self.key
        if tree in M or not _has_drivers(tree):
            return
        t2 = M[tree] = tree.copy()
        t2.name = f'{base_name(tree.name)} @{key}'
        for n in t2.nodes:
            for sock in n.inputs:
                m = getattr(sock, 'default_value', None) if sock.type == 'MATERIAL' else None
                if m is not None:
                    if m not in M:
                        M[m] = m.copy()
                        M[m].name = f'{base_name(m.name)} @{key}'
                    sock.default_value = M[m]

    # ----------------------------------------------------------------------- knobs
    def reset(self):
        """Every knob back to the build's saved value, keys dropped, flash and launch boxes dark."""
        for ctl, rest in ((self.controls, self.rest), (self.hull_ctl, self.hull_rest)):
            if ctl is None:
                continue
            if ctl.animation_data:
                ctl.animation_data.action = None
            for k, v in rest.items():
                ctl[k] = v
            ctl.update_tag()
        for o in self.ids:
            if isinstance(o, bpy.types.Object) and base_name(o.name).startswith((FLASH, LAUNCH)):
                if o.animation_data:
                    o.animation_data_clear()
                o.hide_render = True

    def _ctl_for(self, name):
        if self.controls is not None and name in self.controls.keys():
            return self.controls
        if self.hull_ctl is not None and name in self.hull_ctl.keys():
            return self.hull_ctl
        raise KeyError(f'{self.key}: no knob {name!r} on this build')

    def get(self, name):
        return self._ctl_for(name)[name]

    def set(self, name, value):
        """Set one knob of this ship only (an SWFX knob, or a hull knob such as gear_down)."""
        c = self._ctl_for(name)
        c[name] = float(value)
        c.update_tag()

    def key(self, name, frame, value, interpolation=None):
        c = self._ctl_for(name)
        c[name] = float(value)
        c.keyframe_insert(f'["{name}"]', frame=frame)
        if interpolation and c.animation_data and c.animation_data.action:
            import swfx_core
            fc = swfx_core.find_fcurve(c, f'["{name}"]')
            for kp in fc.keyframe_points if fc else ():
                if abs(kp.co.x - frame) < 1e-4:
                    kp.interpolation = interpolation

    def flash(self, label, value=1.0, seed=None):
        """A still's muzzle flash: gun <label> ('t1_1') lit at `value`, its flash volume shown."""
        self.set(f'gun_flash_{label}', value)
        self.set(f'gun_seed_{label}', seed if seed is not None else
                 int(hashlib.sha256(f'{self.key}|{label}'.encode()).hexdigest()[:4], 16) / 65535)
        box = self.object(FLASH + label)
        if box is not None:
            box.hide_render = False

    def reading(self, dg=None):
        """What this ship's copy evaluates to on the current frame: throttle, the strongest
        throttle-driven plume value, guns flashing, total flash-light watts, hull knobs."""
        dg = dg or bpy.context.evaluated_depsgraph_get()
        row = {}
        if self.controls is not None:
            ev = self.controls.evaluated_get(dg)
            knob = lambda n: float(ev.get(n, 0))   # noqa: E731
            row['thr_throttle'] = round(knob('thr_throttle'), 3)
            row['plume'] = round(self._driven_out('thr_throttle', dg), 3)
            row['firing'] = {lab: round(knob('gun_flash_' + lab), 2) for lab in self.gun_labels()
                             if knob('gun_flash_' + lab) > 0}
            row['flash_W'] = round(sum(o.evaluated_get(dg).energy for o in self.ids
                                       if isinstance(o, bpy.types.Light)
                                       and any(v.name.startswith('gun_flash_t') for fc in _drivers(o)
                                               for v in fc.driver.variables)), 1)
            row['clock'] = self.offset
        if self.hull_ctl is not None:
            ev = self.hull_ctl.evaluated_get(dg)
            row.update({n: round(float(ev.get(n, 0)), 2) for n in self.hull_rest})
        return row

    def _driven_out(self, var, dg):
        """The largest evaluated output of this ship's material Value nodes driven by `var`."""
        best = 0.0
        for m in self.ids:
            if not isinstance(m, bpy.types.Material) or m.node_tree is None or m.node_tree.animation_data is None:
                continue
            ev = m.evaluated_get(dg).node_tree
            for fc in m.node_tree.animation_data.drivers:
                if var in [v.name for v in fc.driver.variables] and fc.data_path.startswith('nodes['):
                    try:
                        best = max(best, float(ev.nodes[fc.data_path.split('"')[1]].outputs[0].default_value))
                    except (KeyError, TypeError, IndexError):
                        pass
        return best

    def object(self, kit_name):
        """This ship's copy of a kit object, by the kit's own name."""
        return next((o for o in self.ids if isinstance(o, bpy.types.Object) and base_name(o.name) == kit_name), None)

    def gun_labels(self):
        """'t<turret>_<k>' of every gun (not launcher) on this ship: the names of its flash knobs."""
        return sorted(base_name(o.name)[len(FLASH):] for o in self.ids
                      if isinstance(o, bpy.types.Object) and base_name(o.name).startswith(FLASH)
                      and not base_name(o.name).startswith('SWFX guns flash light'))

    def anchor_empties(self, kind):
        """(match, empty) for this copy's 'SWFX anchor engine <i>' / 'SWFX anchor turret <i> <tag>'."""
        pat = ANCHOR_RE[kind]
        out = []
        for o in self.ids:
            if isinstance(o, bpy.types.Object):
                m = pat.match(base_name(o.name))
                if m:
                    out.append((m, o))
        return sorted(out, key=lambda mo: mo[1].name)

    def engine_radius(self, i):
        e = self.manifest.get('effects') or {}
        rows = e.get('engines') or (e.get('anchors') or {}).get('engines')
        row = next((r for r in rows if r.get('index') == i), None) if isinstance(rows, list) else None
        return float(row['radius']) if row else 1.0

    # ----------------------------------------------------------------------- the kit's helpers
    def profile(self):
        """The hull profile folder the build was dressed with (from its manifest), or None."""
        kit = (self.manifest.get('effects') or {}).get('kit') or ''
        m = re.search(r'hull profile ([\w./-]+?)/vfx_hull\.py', kit)
        return PROLOGUE / m.group(1) if m and (PROLOGUE / m.group(1) / 'vfx_hull.py').exists() else None

    @contextlib.contextmanager
    def kit(self, scene=None):
        """A swfx_core.Context bound to this ship's copy, for the kit's keying helpers:

            with ship.kit() as (ctx, kit):
                kit.thrusters.ignition(ctx, 1, ramp_frames=12)
                kit.guns.fire(ctx, 1, 4, shots=6, interval_frames=3)

        Inside the block this ship's copies carry the kit's own names (the kit finds its
        materials and meshes by name), the kit's modules are loaded for this build's hull
        profile and scale, and swfx_guns' rigs are this ship's flash and launch volumes. Whatever
        the helpers create is this ship's and is renamed out of the way on exit."""
        scene = scene or bpy.context.scene
        env_old = os.environ.get('SWFX_HULL')
        variant_old = os.environ.get('WOLF_VARIANT')
        # A profile that dresses more than one build reads which one from the environment
        # (wolf-cinematic: WOLF_VARIANT=merchant), as export_effects.py set it.
        if self.manifest.get('variant') == 'merchant':
            os.environ['WOLF_VARIANT'] = 'merchant'
        else:
            os.environ.pop('WOLF_VARIANT', None)
        prof = self.profile()
        if prof:
            os.environ['SWFX_HULL'] = str(prof)
        else:
            os.environ.pop('SWFX_HULL', None)
        mods = {}
        for name in ('swfx_hull', 'swfx_core', 'swfx_anchors', 'swfx_guns', 'swfx_thrusters', 'swfx_lighting'):
            mods[name] = importlib.reload(importlib.import_module(name))
        core, anchors_mod, guns = mods['swfx_core'], mods['swfx_anchors'], mods['swfx_guns']
        core.METRES_PER_UNIT = self.mpu
        before = {s: set(getattr(bpy.data, s)) for s in STORES}
        swapped = self._claim_names()
        try:
            state = str(self.controls.get('swfx_state', 'clean')) if self.controls is not None else 'clean'
            ctx = core.Context.__new__(core.Context)
            ctx.scene, ctx.state, ctx.records = scene, state, {}
            ctx.spec = core.STATES.get(state) or next(iter(core.STATES.values()))
            ctx.root, ctx.controls = self.fx, self.controls
            ctx.anchors = self._anchors(anchors_mod)
            groups = self.groups
            ctx.collection = lambda group: groups.get(f'{ROOT} | {group}') or self.fx
            guns._RIGS.clear()
            for a in ctx.anchors.guns:
                lab = guns.gun_label(a)
                launcher = getattr(a, 'kind', 'gun') == 'launcher'
                box = self.object((LAUNCH if launcher else FLASH) + lab)
                light = self.object(('SWFX guns launch light ' if launcher else 'SWFX guns flash light ') + lab)
                guns._RIGS[(a.turret, a.tag)] = dict(anchor=a, flash=box, light=light, count=0)
            yield ctx, _Kit(mods)
        finally:
            for store, idb, mine in reversed(swapped):
                idb.name = mine
            for s in STORES:
                for idb in set(getattr(bpy.data, s)) - before[s]:
                    self.ids.add(idb)
                    idb.name = f'{base_name(idb.name)} @{self.key}'
            self.own_colours()      # the helpers key ob.color, which an instance never shows
            self.metre_scale()      # whatever the helpers made is at hull scale too
            if env_old is None:
                os.environ.pop('SWFX_HULL', None)
            else:
                os.environ['SWFX_HULL'] = env_old
            if variant_old is None:
                os.environ.pop('WOLF_VARIANT', None)
            else:
                os.environ['WOLF_VARIANT'] = variant_old

    def _claim_names(self):
        """Give this ship's IDs their kit names for the duration of kit(); returns the undo list."""
        undo = []
        for idb in self.ids:
            store = next((s for s in STORES if isinstance(idb, _TYPES[s])), None)
            if store is None:
                continue
            want = base_name(idb.name)
            mine = idb.name
            holder = getattr(bpy.data, store).get(want)
            if holder is idb:
                continue
            if holder is not None:
                old = holder.name
                holder.name = f'{want} ~@{self.key}'
                undo.append((store, holder, old))
            idb.name = want
            undo.append((store, idb, mine))
        return undo

    def _anchors(self, anchors_mod):
        """swfx_anchors.Anchors rebuilt over this copy's anchor empties. A mount's kind (gun,
        energy, launcher) comes from the hull profile's MOUNTS, else from its tag."""
        a = anchors_mod.Anchors()
        for m, ob in self.anchor_empties('engine'):
            i = int(m.group(1))
            a.engines.append(anchors_mod.EngineAnchor(i, ob, self.engine_radius(i), 8))
        mounts = getattr(anchors_mod, 'MOUNTS', ()) if self.profile() else ()
        for m, ob in self.anchor_empties('turret'):
            i, tag = int(m.group(1)), m.group(2)
            kind = anchors_mod.mount_kind(mounts[i - 1]) if i <= len(mounts) else (
                'launcher' if tag.startswith('tube ') else 'gun')
            b = re.match(r'(?:barrel|tube) (\d+)$', tag)
            a.guns.append(anchors_mod.GunAnchor(i, tag, ob, ob.empty_display_size / .8, False, kind,
                                                int(b.group(1)) if b else None))
        return a


class _Kit:
    """The kit's modules as loaded for one ship: kit.guns, kit.thrusters, kit.lighting, kit.core."""

    def __init__(self, mods):
        self.guns, self.thrusters = mods['swfx_guns'], mods['swfx_thrusters']
        self.lighting, self.core, self.anchors = mods['swfx_lighting'], mods['swfx_core'], mods['swfx_anchors']


_TYPES = {'objects': bpy.types.Object, 'meshes': bpy.types.Mesh, 'lights': bpy.types.Light,
          'materials': bpy.types.Material, 'node_groups': bpy.types.NodeTree, 'curves': bpy.types.Curve,
          'actions': bpy.types.Action}


OWN_COLOUR = 'ship_fx_own_colour'   # on a material: the object whose colour it carries on a node
COLOUR_NODE, ALPHA_NODE = 'ShipFx object colour', 'ShipFx object alpha'


def _colour_readers(tree):
    """(node, output) pairs that read the drawing object's colour, with links out."""
    out = []
    for n in tree.nodes:
        if n.type == 'OBJECT_INFO':
            names = ('Color', 'Alpha')
        elif n.type == 'ATTRIBUTE' and n.attribute_type == 'OBJECT' and n.attribute_name == 'color':
            names = ('Color', 'Vector', 'Fac', 'Alpha')
        else:
            continue
        out += [(n, n.outputs[k]) for k in names if k in n.outputs and n.outputs[k].is_linked]
    return out


def _route_colour(tree):
    """Relink every reader of the object colour to an RGB node (colour) and a Value node (alpha)."""
    rgb = tree.nodes.new('ShaderNodeRGB')
    rgb.name = rgb.label = COLOUR_NODE
    alpha = tree.nodes.new('ShaderNodeValue')
    alpha.name = alpha.label = ALPHA_NODE
    for n, sock in _colour_readers(tree):
        src = alpha.outputs[0] if sock.name == 'Alpha' else rgb.outputs[0]
        if sock.name == 'Fac':       # an attribute's Fac is the colour's average
            avg = tree.nodes.new('ShaderNodeRGBToBW')
            tree.links.new(rgb.outputs[0], avg.inputs[0])
            src = avg.outputs[0]
        for link in list(sock.links):
            tree.links.new(src, link.to_socket)
        rgb.location, alpha.location = n.location, (n.location.x, n.location.y - 160)


def _key_colour(ob, tree):
    """Copy ob.color (keyed or still) onto the material's colour and alpha nodes."""
    rgb, alpha = tree.nodes.get(COLOUR_NODE), tree.nodes.get(ALPHA_NODE)
    if rgb is None:
        return
    for i in range(4):
        sock, idx = (rgb.outputs[0], i) if i < 3 else (alpha.outputs[0], -1)
        path = sock.path_from_id('default_value')
        src = find_fcurve(ob, 'color', i)
        dst = find_fcurve(tree, path, max(idx, 0))
        if src is None or len(src.keyframe_points) == 0:
            if dst is not None:
                dst.keyframe_points.clear()
                dst.keyframe_points.insert(0.0, ob.color[i]).interpolation = 'CONSTANT'
            if idx < 0:
                sock.default_value = ob.color[i]
            else:
                sock.default_value[idx] = ob.color[i]
            continue
        if dst is None:
            if idx < 0:
                sock.keyframe_insert('default_value', frame=src.keyframe_points[0].co[0])
            else:
                sock.keyframe_insert('default_value', index=idx, frame=src.keyframe_points[0].co[0])
            dst = find_fcurve(tree, path, max(idx, 0))
        k = len(src.keyframe_points)
        flat = [0.0] * (2 * k)
        dst.keyframe_points.clear()
        dst.keyframe_points.add(k)
        for prop in ('co', 'handle_left', 'handle_right'):
            src.keyframe_points.foreach_get(prop, flat)
            dst.keyframe_points.foreach_set(prop, flat)
        for prop in ('interpolation', 'handle_left_type', 'handle_right_type', 'easing'):
            vals = [getattr(p, prop) for p in src.keyframe_points]
            for p, v in zip(dst.keyframe_points, vals):
                setattr(p, prop, v)
        dst.extrapolation = src.extrapolation
        dst.update()


def colour_audit(ship):
    """Proof of own_colours on one ShipFx copy: objects still reading their object colour
    directly (want 0), objects routed, keyed colour curves, and the worst difference between an
    object's colour curve and its material node's, sampled across the keys (want 0)."""
    direct, routed, keyed, worst = set(), 0, 0, 0.0
    for o in ship.ids:
        if not isinstance(o, bpy.types.Object):
            continue
        for sl in o.material_slots:
            m = sl.material
            if m is None or m.node_tree is None:
                continue
            if m.get(OWN_COLOUR) is None:
                if _colour_readers(m.node_tree):
                    direct.add(base_name(o.name))
                continue
            routed += 1
            nodes = m.node_tree.nodes
            for i in range(4):
                src = find_fcurve(o, 'color', i)
                if src is None or not len(src.keyframe_points):
                    continue
                keyed += 1
                sock = (nodes[COLOUR_NODE] if i < 3 else nodes[ALPHA_NODE]).outputs[0]
                dst = find_fcurve(m.node_tree, sock.path_from_id('default_value'), i if i < 3 else 0)
                f0, f1 = src.keyframe_points[0].co[0], src.keyframe_points[-1].co[0]
                for j in range(9):
                    f = f0 - 1 + (f1 - f0 + 2) * j / 8
                    worst = max(worst, abs(src.evaluate(f) - dst.evaluate(f)) if dst else float('inf'))
    return dict(routed=routed, keyed_curves=keyed, worst=worst, direct=len(direct),
                direct_names=sorted(direct)[:4])


def find_fcurve(idb, path, index=0):
    """An F-curve of idb's action, legacy or layered (Blender 5)."""
    ad = getattr(idb, 'animation_data', None)
    act = ad.action if ad else None
    if act is None:
        return None
    curves = getattr(act, 'fcurves', None)
    if curves is not None:
        return curves.find(path, index=index)
    slot = getattr(ad, 'action_slot', None)       # this ID's own slot, not another user's of the action
    for layer in act.layers:
        for strip in layer.strips:
            bag = strip.channelbag(slot) if slot is not None else None
            for b in ([bag] if bag is not None else [] if slot is not None else strip.channelbags):
                fc = b.fcurves.find(path, index=index)
                if fc:
                    return fc
    return None


SCALED = 'ship_fx_metre_scale'    # on a light or material: the mpu it was put to metre scale for
# volume shader -> its inputs that add up along a ray (per unit length)
VOLUME_INPUTS = {'PRINCIPLED_VOLUME': ('Density', 'Emission Strength', 'Blackbody Intensity'),
                 'VOLUME_SCATTER': ('Density',), 'VOLUME_ABSORPTION': ('Density',),
                 'VOLUME_COEFFICIENTS': ('Absorption Coefficients', 'Scatter Coefficients', 'Emission'),
                 'EMISSION': ('Strength',)}


def metre_scale(ids, mpu):
    """Lights and volume materials among ids, tuned in a hull-unit file, put right for a scene
    that places them at Scale(mpu). A light's power is not scaled by its object, so it takes
    exposure 2*log2(mpu) (energy drivers and keys untouched). A volume's density and emission add
    up per world unit, and a ray through the scaled copy is mpu times as long, so each is divided
    by mpu. Idempotent: a datablock already done for this mpu is left alone. Returns counts."""
    out = dict(lights=0, volumes=0, unhandled=[])
    ev = 2 * math.log2(mpu)
    for idb in ids:
        if isinstance(idb, bpy.types.Light):
            idb.exposure = ev
            out['lights'] += 1
        elif isinstance(idb, bpy.types.Material) and idb.node_tree is not None:
            if idb.get(SCALED) == mpu:
                continue
            leaves, other = _volume_leaves(idb.node_tree)
            if not leaves and not other:
                continue
            for n in leaves:
                for name in VOLUME_INPUTS[n.type]:
                    _times(idb.node_tree, n.inputs.get(name), 1.0 / mpu)
            out['unhandled'] += [f'{idb.name}: {n.type}' for n in other]
            idb[SCALED] = mpu
            out['volumes'] += 1
    return out


def _volume_leaves(tree):
    """The shader nodes that feed the active output's Volume, through mix and add shaders:
    (known volume shaders, anything else)."""
    outs = [n for n in tree.nodes if n.type == 'OUTPUT_MATERIAL']
    out = next((n for n in outs if n.is_active_output), outs[0] if outs else None)
    if out is None or not out.inputs['Volume'].is_linked:
        return [], []
    leaves, other, todo, seen = [], [], [out.inputs['Volume'].links[0].from_node], set()
    while todo:
        n = todo.pop()
        if n in seen:
            continue
        seen.add(n)
        if n.type in ('MIX_SHADER', 'ADD_SHADER'):
            todo += [l.from_node for i in n.inputs if i.type == 'SHADER' for l in i.links]
        elif n.type == 'REROUTE':
            todo += [l.from_node for l in n.inputs[0].links]
        elif n.type in VOLUME_INPUTS:
            leaves.append(n)
        else:
            other.append(n)
    return leaves, other


def _times(tree, sock, k):
    """Scale one float or vector input by k: its value, or whatever is linked into it."""
    if sock is None:
        return
    if sock.is_linked:
        link = sock.links[0]
        vec = sock.type == 'VECTOR' or link.from_socket.type in ('VECTOR', 'RGBA')
        m = tree.nodes.new('ShaderNodeVectorMath' if vec else 'ShaderNodeMath')
        m.operation = 'SCALE' if vec else 'MULTIPLY'
        m.label = 'metre scale'
        m.location = sock.node.location.x - 160, sock.node.location.y - 40
        tree.links.new(link.from_socket, m.inputs[0])
        if vec:
            m.inputs['Scale'].default_value = k
        else:
            m.inputs[1].default_value = k
        tree.links.new(m.outputs[0], sock)
    elif hasattr(sock, 'default_value'):
        path = f'nodes["{sock.node.name}"].inputs[{list(sock.node.inputs).index(sock)}].default_value'
        ad = tree.animation_data
        drivers = [fc for fc in (ad.drivers if ad else ()) if fc.data_path == path]
        if drivers:         # a driven input: the driver would overwrite a scaled value, so scale it
            for fc in drivers:
                fc.driver.expression = f'({fc.driver.expression})*{k!r}'
            return
        if ad and ad.action and any(fc.data_path == path for fc in getattr(ad.action, 'fcurves', ())):
            raise ValueError(f'{tree.name}: keyed volume input {path} (metre scale wants a driver or a value)')
        v = sock.default_value
        if isinstance(v, float):
            sock.default_value = v * k
        else:
            sock.default_value = [c * k for c in v]


def _has_drivers(data):
    return data is not None and any(True for _ in _drivers(data))


def audit(ships, library=()):
    """Every driver target, parent and pointer of each ship's copy, sorted into: its own copy,
    shared (a library hull it rides, read-only), or FOREIGN (another ship's copy, or a library
    SWFX original). Returns {ship key: {'own': n, 'shared': n, 'foreign': [descriptions]}}."""
    owner = {}
    for s in ships:
        for idb in s.ids:
            owner[idb] = s.key
    lib = set()
    for c in library:
        if c is not None:
            lib.update(c.all_objects)
            lib.add(c)
    out = {}
    for s in ships:
        row = {'own': 0, 'shared': 0, 'foreign': []}

        def note(what, target):
            if target is None:
                return
            k = owner.get(target)
            if k == s.key:
                row['own'] += 1
            elif k is None and target not in lib:
                row['shared'] += 1
            else:
                row['foreign'].append(f'{what} -> {target.name} ({k or "library SWFX"})')

        for idb in s.ids:
            for fc in _drivers(idb):
                for v in fc.driver.variables:
                    for t in v.targets:
                        note(f'{idb.name} driver {fc.data_path}', t.id)
            if isinstance(idb, bpy.types.Object):
                note(f'{idb.name} parent', idb.parent)
                for c in idb.constraints:
                    for _, v in _pointers(c):
                        note(f'{idb.name} constraint', v)
        out[s.key] = row
    return out
