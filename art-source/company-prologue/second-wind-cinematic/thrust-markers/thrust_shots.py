"""What each shot's craft are doing with their engines: the per-shot half of the thruster markers.

Each scene script calls one function here at the end of its build, passing its own module (the
way c4x_scene hands itself to port_life), so the timings below are read from the scene's own
constants and cannot drift from the motion:

    c1v2_scene.build()              -> mark_c1(c1v2_scene)
    c2_bridge_render.blender_main() -> mark_c2(scene, plan)
    c4x_scene.build()               -> mark_c4(c4x_scene)   (C5 builds C4 and plays on, so this
                                                             covers C5 as well)

Every craft the scene shows gets markers (thrust_markers.find_craft finds them by their nozzles,
so a ship another session adds is covered without a change here). The craft named below get the
throttle their shot gives them; anything else gets thrust_markers.auto, which reads it off the
craft's own keyed motion (parked: off; climbing or sinking: lift; travelling: cruise).

No craft flies without its drive showing (`require_plumes`): before a shot renders, every craft
whose main or lift nozzles thrust on any frame must have plume geometry driven by its markers, or
carry its own drive effects, or be declared never in frame. Otherwise the build stops with the
craft's names. In C4/C5 every craft the port flies is given `far_plume` on its markers here
(CINE_PLUMES=0 leaves them off, as a control that may not be rendered as a final).
"""
import json
import os
import re
import sys
from pathlib import Path

import bpy

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import thrust_markers as tm  # noqa: E402

CINE = HERE.parent
STATES = CINE / 'second-wind-states' / 'patched'
MUDSKIPPER_GEOMETRY = CINE.parent / 'mudskipper-geometry' / 'geometry.json'
DRIVE_PART = re.compile(r'^Drive (\d+) (?:glow|core)$')   # the scenes' stand-in drive glows


def sw_manifest():
    return json.loads((STATES / 'manifest.json').read_text())


def sw_working():
    """Which of Second Wind's bells run in the patched state (3 and 4; 1 is crushed, 2 cold)."""
    m = sw_manifest()
    return {e['index']: e['index'] in m['engines_running'] for e in m['engines']}


def mudskipper_engines():
    g = json.loads(MUDSKIPPER_GEOMETRY.read_text())
    return {int(e['object_prefix'].split()[1]): {'role': e['role'], 'dir': e['exhaust_direction']}
            for e in g['engines']}


def mark_any(craft_obj, label=None, working=None, activity=''):
    """Markers for one craft: its anchors, or (a hull with none, the skinned Mudskipper) its
    bells, measured with the hull's geometry.json engine table."""
    marks = tm.mark(craft_obj, label, working=working, activity=activity)
    if marks and all('measured' in m['thrust_source'] for m in marks):
        marks = tm.mark(craft_obj, label, engines=mudskipper_engines(), working=working, activity=activity)
    return marks


def follow(obj, marker):
    """Hang an existing plume or glow off a marker, keeping where it is relative to the craft."""
    rel = tm.chain(marker).inverted() @ tm.chain(obj)
    obj.parent = marker
    obj.matrix_parent_inverse.identity()
    obj.matrix_basis = rel


def pulses(times, width, level, base=0.0):
    """Throttle keys for short RCS firings centred on these times (seconds)."""
    keys = [(0.0, base)]
    for t in times:
        keys += [(t - width, base), (t, level), (t + width, base)]
    return keys


def _rest(found, done, fps, frames, lift=False, plume=None):
    """Every craft the shot did not name: markers, and throttle from its own motion. lift: in
    air, a hull with no belly jets gets its mains vectored down as lift markers too, and a craft
    standing still in the air reads as hovering. plume: a function (marker, kind, radius_m) that
    hangs a drive plume on a marker and returns its objects (`port_fleet.far_plume`); every craft
    that thrusts then gets one on each nozzle it fires (`hang_plumes`). Returns {craft: plumes}."""
    hung = {}
    for name, ob in sorted(found.items()):
        if name in done:
            continue
        marks = mark_any(ob)
        if lift:
            marks += tm.lift_from_mains(ob)
        ob['thrust_activity'] = tm.auto(ob, marks, fps, frames, hover=lift)
        if plume is not None:
            objs = hang_plumes(ob, marks, plume, frames)
            if objs:
                hung[name] = objs
    return hung


# ---------------------------------------------------------------- plumes

PLUMES = os.environ.get('CINE_PLUMES', '1') != '0'
NEW_PLUME = 'thrust_plume_added'        # on each plume `hang_plumes` adds: the craft's name


def hang_plumes(craft_obj, marks, plume, frames):
    """A drive plume on each of the craft's working main and lift nozzles that fires somewhere in
    frames, sized by its marker's throttle, so the plume follows what the motion says the engines
    are doing: cruising on the mains (pointing aft), climbing, sinking or hovering on lift, and a
    vectored drive swinging between the two. A craft that already draws its drives (the VFX kit's
    thruster volumes, or a glow tagged as its drive) is left as it is. Each plume hides when its
    craft does. Returns the plume objects."""
    if craft_obj.get('swfx_controls') or tm.craft_drive_fx(craft_obj):
        return []                      # linked with the kit's effects, or already drawing its drives
    out = []
    for m in marks:
        if (m['thrust_kind'] not in tm.PLUMED_KINDS or not m['thrust_working']
                or tm.max_throttle(m, frames) <= 1e-3):
            continue
        for ob in plume(m, m['thrust_kind'], float(m.get('radius_m', 1.5))):
            ob[NEW_PLUME] = craft_obj.name
            _hide_with(ob, craft_obj)
            out.append(ob)
    return out


def far_plume():
    """`port_fleet.far_plume`: the drive's core and plume as soft emissive cones on a marker, the
    look the port's departing Colossus set. Lives with the port's fleet; C1 borrows it."""
    ie = str(CINE / 'industrial-exterior')
    if ie not in sys.path:
        sys.path.insert(0, ie)
    import port_fleet
    return port_fleet.far_plume


def _hide_with(ob, craft_obj):
    """Copy the craft's keyed hide_render (port_life puts climbers away above C5's camera)."""
    keys = [tuple(k.co) for fc in tm.fcurves(craft_obj) if fc.data_path == 'hide_render'
            for k in fc.keyframe_points]
    if not keys:
        ob.hide_render = craft_obj.hide_render
        return
    for f, v in keys:
        ob.hide_render = bool(v > 0.5)
        ob.keyframe_insert('hide_render', frame=int(round(f)))
    for fc in tm.fcurves(ob):
        if fc.data_path == 'hide_render':
            for k in fc.keyframe_points:
                k.interpolation = 'CONSTANT'


_GUARD = {}


def require_plumes(fps, frames, shot):
    """Refuse a shot in which a craft thrusts with nothing drawing its drive (`tm.plume_faults`):
    SystemExit naming each craft and its bare nozzles. With CINE_PLUMES=0 (the plumes-off control)
    a review build only warns; a final (CINE_FINAL=1) is refused all the same. Also arms a
    render_init handler that asks again as the render starts, so a shot whose script forgets to
    call this cannot render past it either. Returns the faults (empty when it passes)."""
    _GUARD.update(fps=fps, frames=tuple(frames), shot=shot)
    if _check_plumes not in bpy.app.handlers.render_init:
        bpy.app.handlers.render_init.append(_check_plumes)
    return _plume_verdict(exit_on_fault=True)


def _refusing():
    return PLUMES or os.environ.get('CINE_FINAL', '0') == '1'


def _plume_verdict(exit_on_fault):
    faults, exempt = tm.plume_faults(_GUARD['frames'])
    shot = _GUARD['shot']
    for name, why in exempt:
        print('THRUST_PLUMES %s exempt %s: %s' % (shot, name, why), flush=True)
    if not faults:
        print('THRUST_PLUMES %s PASS: every craft that thrusts shows its drive' % shot, flush=True)
        return []
    lines = ['%s (%s)' % (name, ', '.join(n.split(' | ')[-1] for n in bare)) for name, bare in faults]
    msg = ('THRUST_PLUMES %s FAIL: %d craft thrust with no plume drawn: %s'
           % (shot, len(faults), '; '.join(lines)))
    print(msg, flush=True)
    if not _refusing():
        print('THRUST_PLUMES %s: CINE_PLUMES=0 control, not a final: building anyway' % shot, flush=True)
    elif exit_on_fault:
        raise SystemExit(msg)
    return faults


def _check_plumes(scene, *_):
    if _plume_verdict(exit_on_fault=False) and _refusing():
        # An exception in a handler is printed and the render carries on: stop the process instead.
        if bpy.app.background:
            sys.stdout.flush()
            os._exit(1)
        raise RuntimeError('THRUST_PLUMES: craft thrust with no plume drawn (see the console)')


def _label(name):
    return name.split(' | ')[0] if ' | ' in name else name


# ---------------------------------------------------------------- C1 corvus-approach

def mark_c1(c1):
    """C1: Second Wind braking down the lane past the camera, then opening its drive toward
    Jangala; the Hegemony picket cruising in formation with its fighters at full burn; the
    Mudskipper and the customs Hound crossing; the outbound Atlas card."""
    scene = bpy.context.scene
    fps, frames = c1.FPS, (0, c1.FRAMES - 1)
    found = tm.find_craft(scene)
    done = set()
    hero = sw_manifest()['root']
    end = c1.DURATION
    for name, ob in found.items():
        if name == hero:
            m = mark_any(ob, 'Second Wind', working=sw_working(),
                         activity='braking to the pass, then burning for Jangala')
            # "a hull braking hard to cross the customs line slowly, then opening its drive"
            tm.key(m, [(0, 0.25), (c1.PASS_T, 0.25), (c1.PASS_T + 2.0, 1.0), (end, 1.0)], fps, kinds=('main',))
            # "RCS correcting" through the close pass (hail 01, 3.1-12.3 s)
            tm.key(m, pulses((4.2, 6.9, 9.3, 11.6), 0.25, 0.8), fps, kinds=('maneuver', 'lift'))
            for light in [o for o in bpy.data.objects if o.parent == ob and DRIVE_PART.match(o.name)]:
                i = int(DRIVE_PART.match(light.name).group(1))
                mk = bpy.data.objects.get('%sSecond Wind | main %02d' % (tm.PREFIX, i))
                if mk is not None:
                    follow(light, mk)
        elif name.startswith('Flotilla '):
            m = mark_any(ob, name.replace(' | ', ' '), activity='cruising in formation')
            tm.key(m, [(0, 0.35)], fps, kinds=('main',))
            tm.key(m, [(0, 0.0)], fps, kinds=('maneuver', 'lift'))
        elif name.startswith('Fighter '):
            m = mark_any(ob, name.replace(' | ', ' '), activity='full burn, looping the picket')
            tm.key(m, [(0, 1.0)], fps)
        elif name.startswith('Mudskipper'):
            m = mark_any(ob, 'Mudskipper', activity='crossing the lane')
            tm.key(m, [(0, 0.8)], fps, kinds=('main',))
            tm.key(m, [(0, 0.0)], fps, kinds=('maneuver', 'lift'))
        elif name.startswith('HOUND'):
            m = mark_any(ob, 'Customs Hound', activity='crossing at full throttle')
            tm.key(m, [(0, 1.0)], fps, kinds=('main',))
            tm.key(m, [(0, 0.0)], fps, kinds=('maneuver', 'lift'))
        else:
            continue
        done.add(name)
    card = bpy.data.objects.get('Card | Atlas outbound')
    if card is not None:
        img = card.active_material.node_tree.nodes['Image Texture'].image
        m = tm.card(card, 'Atlas outbound', img, activity='outbound, under drive')
        tm.key(m, [(0, 0.8)], fps)
    _rest(found, done, fps, frames)
    if PLUMES:
        # The picket's Lashers and Hounds and the lane freighters cruise 5-9 km out with only a
        # stand-in light (or nothing) at their bells; the Onslaught, Legion, Mudskipper, customs
        # Hound and Second Wind carry the kit's thrusters and the Broadswords their drive beads.
        hung = {}
        for name, ob in sorted(found.items()):
            objs = hang_plumes(ob, tm.markers(ob), far_plume(), frames)
            if objs:
                hung[name] = objs
        print('THRUST_PLUMES c1 added on %d craft: %s' % (len(hung), ', '.join(
            '%s x%d' % (n, len(o)) for n, o in sorted(hung.items())) or '-'), flush=True)
    require_plumes(fps, frames, 'c1')


# ---------------------------------------------------------------- C2 patrol-abeam (bridge)

def mark_c2(scene, p):
    """C2: Second Wind coasting until its de-orbit burn under the read-back; the Legion idling
    past ("the patrol coasts"); its Broadswords at full throttle."""
    fps = p['fps']
    found = tm.find_craft(scene)
    try:
        import c2_bridge_blockout as bb
        drop = bb.DROP_T
    except ImportError:
        drop = 9.6
    done = set()
    for name, ob in found.items():
        if name == 'C2B | ship frame':
            m = mark_any(ob, 'Second Wind', working=sw_working(), activity='coasting, then the de-orbit burn')
            ob[tm.PLUME_EXEMPT] = 'the camera is on its bridge: its drives are never in frame'
            tm.key(m, [(0, 0.0), (drop, 0.0), (drop + 0.6, 1.0), (p['samples'][-1]['t'], 1.0)], fps, kinds=('main',))
            tm.key(m, pulses((drop + 0.3, drop + 1.6, drop + 2.9), 0.25, 0.7), fps, kinds=('maneuver', 'lift'))
        elif name in ('C2B | Legion root', 'LEGION | root'):
            m = mark_any(ob, 'Legion', activity='idling past (the patrol coasts)')
            tm.key(m, [(0, 0.2)], fps, kinds=('main',))
            tm.key(m, [(0, 0.0)], fps, kinds=('maneuver', 'lift'))
        elif name.startswith('C2B | Broadsword'):
            m = mark_any(ob, name.split(' | ')[1], activity='full throttle')
            tm.key(m, [(0, 1.0)], fps)
            # The flames ride the exhaust anchors today; hang them off the markers instead.
            for fl in [o for o in ob.children_recursive if o.type == 'MESH' and o.parent
                       and 'exhaust anchor' in o.parent.name and not o.name.startswith(tm.PREFIX)]:
                k = tm.ENGINE_ANCHOR.match(fl.parent.name)
                mk = bpy.data.objects.get('%s%s | main %02d' % (tm.PREFIX, name.split(' | ')[1], int(k.group(1)))) if k else None
                if mk is not None:
                    follow(fl, mk)
        else:
            continue
        done.add(name)
    end = max(s['frame'] for s in p['samples'])
    _rest(found, done, fps, (0, end))
    require_plumes(fps, (0, end), 'c2')


# ---------------------------------------------------------------- C4 + C5 industrial-exterior

def mark_c4(c4):
    """C4 and C5: Second Wind braking in on its mains, hovering on its belly jets over Kilo Nine,
    down at T_DOWN and spooled off; afterwards parked through C5. The port's traffic (berthed
    Hounds, the Hermes lighters and Mudskipper ferry, the lifters climbing off and coming down,
    and whatever else the port is given) is read from its own motion, and each one that thrusts is
    given `port_fleet.far_plume` on the nozzles it fires (the look the departing Colossus already
    has), unless CINE_PLUMES=0. Second Wind's and the Colossus's plumes are hung later in
    c4x_scene.build, which calls `require_plumes` once every plume is on. Returns the last frame."""
    scene = bpy.context.scene
    fps = c4.FPS
    try:
        import port_life
        end = port_life.end_frame()
    except Exception:  # port_life not in this build: C4 alone
        end = c4.FRAMES - 1
    found = tm.find_craft(scene)
    hero = sw_manifest()['root']
    done = set()
    ob = found.get(hero)
    if ob is not None:
        m = mark_any(ob, 'Second Wind', working=sw_working(),
                     activity='braking in, hover over Kilo Nine, touchdown, parked')
        down = c4.T_DOWN
        tm.key(m, [(0, 1.0), (down, 1.0), (down + 1.5, 0.0)], fps, kinds=('main',))
        tm.key(m, [(0, 0.0), (c4.ARRIVE - 2.0, 0.0), (c4.ARRIVE, 1.0), (down, 1.0), (down + 1.0, 0.0)],
               fps, kinds=('lift',))
        tm.key(m, [(0, 0.0), (c4.ARRIVE - 1.0, 0.0), (c4.ARRIVE, 0.3), (down, 0.3), (down + 0.5, 0.0)],
               fps, kinds=('maneuver',))
        for part in [o for o in bpy.data.objects if o.parent == ob and DRIVE_PART.match(o.name)]:
            mk = bpy.data.objects.get('%sSecond Wind | main %02d' % (tm.PREFIX, int(DRIVE_PART.match(part.name).group(1))))
            if mk is not None:
                follow(part, mk)
        done.add(hero)
    plume = None
    if PLUMES:
        import port_fleet
        plume = port_fleet.far_plume
    hung = _rest(found, done, fps, (0, end), lift=True, plume=plume)
    if hung:
        import port_fleet                   # nobody walks into a plume (C5's walk maps skip these)
        port_fleet.PARTS.update(o.name for objs in hung.values() for o in objs)
    print('THRUST_PLUMES c4 added on %d craft: %s' % (len(hung), ', '.join(
        '%s x%d' % (n, len(o)) for n, o in sorted(hung.items())) or '-'), flush=True)
    return end
