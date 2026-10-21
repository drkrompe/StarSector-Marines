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

Runs inside Blender only. Used by `battle_blockout.py` (the scene) and `effects_bench.py`.
"""
import contextlib
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
                M[c].objects.link(M[o])
        return new_root

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
