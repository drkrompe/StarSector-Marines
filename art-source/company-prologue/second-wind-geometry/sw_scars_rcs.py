"""Scars: the RCS attitude thrusters (sw_rcs). One row per cluster.

derelict: unpowered, so every bell is dead (anchor working=False). The parts
bin lost bells too, mostly from the windward port side: the whole stern-port
block was unbolted off the thrust housing (a bare mounting footprint, studs
and a cut feed stub on the wall), the pod-port block was stripped of all four
bells (bare studs round open ports), and single bells went from bow-stbd
(its stbd bell) and the dorsal block (up-2).

patched: the escape put back only what the flight needed, from salvage.
- Roll both ways is a couple at the pods: the C1 starboard bank is pod-port
  down with pod-stbd up. So pod-port's up and down bells were refitted from
  salvage (another hull's bells, a mismatched finish). Its port and fore
  bells stay missing, their ports blanked: lateral push to starboard and
  braking still come from bow-port and the bow fore bells.
- Yaw: the ship runs on the starboard pod alone, whose thrust line is 31
  units to starboard, so the main drive itself swings the bow to port. The
  RCS mostly holds the bow back to starboard (bow-port port with stern-stbd
  stbd), which is intact. bow-stbd's stbd bell (bow to port while coasting)
  was refitted from salvage; stern-port, which only helps turn to port, stays
  missing with its feed blind-flanged, and stays so through the refit, while
  the port pod is still cold.
- Pitch and heave keep dorsal up-1, the ventral pair and the stern up and
  down bells, so dorsal up-2 stays missing, its port blanked.
refit: as patched, except dorsal up-2 is reinstalled new (as built).
restored: every cluster reinstalled as built and working ('gone').

Actions per state (params['plan'][state], keyed by bell axis, '*' for the
rest of the cluster, 'block' for the whole housing):
  dead      present but unpowered: working=False
  stripped  bell taken: hidden, bare studs round its open port, not working
  blanked   bell still missing, its port closed by a bolted blank flange
  salvage   a salvaged bell refitted: object-level salvage finish, working
  block     the whole block unbolted: housing and bells hidden, a bare
            footprint with studs and a cut (derelict) or blind-flanged feed.
"""
import math
import bpy
from mathutils import Vector
from sw_damage import Scar, basis
import sw_rcs

V = Vector
TAU = math.tau


def anchors(cluster):
    out = []
    for o in bpy.data.objects:
        if o.type == 'EMPTY' and o.name.startswith(f'RCS {cluster} ') and o.name.endswith(' anchor'):
            out.append(o)
    return sorted(out, key=lambda o: o.name)


def bell_base(anchor):
    """(base point, exhaust axis, mouth radius in units, flange thickness)."""
    r = anchor['radius_m']/sw_rcs.MPU
    _, _, end, tf = sw_rcs.bell_profile(r)
    a = (anchor.matrix_world.to_3x3() @ V((1, 0, 0))).normalized()
    return anchor.matrix_world.translation-a*end, a, r, tf


def studs(d, scar, anchor):
    """Bare studs where the bell's flange bolts were, round its open port."""
    base, a, r, _ = bell_base(anchor)
    e1, e2 = basis(a)
    part = d.part(scar, 'bare studs', d.steel, .005)
    for k in range(6):
        t = TAU*(k+.5)/6
        part.disc(base+a*.12+(e1*math.cos(t)+e2*math.sin(t))*.72*r, a, .045, .24, 6)


def blank(d, scar, anchor):
    """A salvaged blank flange bolted over the empty port."""
    base, a, r, _ = bell_base(anchor)
    e1, e2 = basis(a)
    d.part(scar, 'blank flange', d.salvage[2], .02).disc(base+a*.05, a, .9*r, .1, 12)
    bolts = d.part(scar, 'blank flange bolts', d.steel, .005)
    for k in range(6):
        t = TAU*(k+.5)/6
        bolts.disc(base+a*.13+(e1*math.cos(t)+e2*math.sin(t))*.72*r, a, .06, .07, 6)


def footprint(d, scar, cluster, capped):
    """The whole block unbolted: the mounting plate's bare footprint on the
    wall, a ring of studs and the propellant feed stub."""
    housing = bpy.data.objects.get(f'RCS {cluster} housing')
    lo, hi = d.box(housing)
    lat = [o for o in anchors(cluster) if o['axis'] in ('port', 'stbd')]
    s = 1 if lat and lat[0]['axis'] == 'port' else -1
    wall = lo.y if s > 0 else hi.y  # the plate's face on the wall
    w = V((0, s, 0))
    cx, cz = (lo.x+hi.x)/2, (lo.z+hi.z)/2
    hx, hz = (hi.x-lo.x)/2, (hi.z-lo.z)/2
    pad = d.part(scar, 'bare footprint', d.backing, .01)
    pad.obox(V((cx, wall+s*.015, cz)), V((1,0,0)), w, V((0,0,1)), hx-.02, .015, hz-.02)
    part = d.part(scar, 'bare studs', d.steel, .005)
    for sz in (-1, 1):
        for f in (-.6, 0, .6):
            part.disc(V((cx+f*(hx-.1), wall, cz+sz*(hz-.14)))+w*.16, w, .05, .32, 6)
    feed = V((cx, wall, cz-.3))
    d.part(scar, 'feed stub', d.steel, .01).disc(feed+w*.21, w, .16, .42, 10)
    if capped:
        d.part(scar, 'feed blind flange', d.salvage[2], .01).disc(feed+w*.46, w, .26, .08, 10)
    else:
        d.part(scar, 'feed cut end', d.dark, 0).disc(feed+w*.42, w, .1, .03, 10)


def kind_rcs(d, scar, status):
    """One RCS cluster in one state (see the module docstring for actions).
    params: cluster, plan {state: {axis | '*' | 'block': action}}."""
    P = scar.params
    cluster = P['cluster']
    plan = P['plan'].get(d.state, {})
    if plan.get('block'):
        for o in [bpy.data.objects.get(f'RCS {cluster} housing')]+[
                bpy.data.objects.get(f'RCS {cluster} {a["axis"]} nozzle') for a in anchors(cluster)]:
            d.hide(o, scar)
        for a in anchors(cluster):
            d.tag(a, 'working', False, scar)
        footprint(d, scar, cluster, plan['block'] == 'capped')
        return
    for a in anchors(cluster):
        act = plan.get(a['axis'], plan.get('*'))
        bell = bpy.data.objects.get(f'RCS {cluster} {a["axis"]} nozzle')
        if act == 'dead':
            d.tag(a, 'working', False, scar)
        elif act in ('stripped', 'blanked'):
            d.hide(bell, scar)
            d.tag(a, 'working', False, scar)
            if act == 'stripped':
                studs(d, scar, a)
            else:
                blank(d, scar, a)
        elif act == 'salvage':
            d.override(bell, d.salvage[1], scar)


KINDS = {'rcs': kind_rcs}

DEAD = {'derelict': {'*': 'dead'}}

SCARS = [
    Scar('rcs-bow-port', 'rcs', 'open gone gone gone',
         'Dead while unpowered; working as built from the escape on.',
         cluster='bow-port', plan=DEAD),
    Scar('rcs-bow-stbd', 'rcs', 'open patched patched gone',
         'stbd bell taken in the belt; refitted from salvage for the escape, '
         'reinstalled as built at restoration.',
         cluster='bow-stbd', plan={'derelict': {'*': 'dead', 'stbd': 'stripped'},
                                   'patched': {'stbd': 'salvage'},
                                   'refit': {'stbd': 'salvage'}}),
    Scar('rcs-pod-port', 'rcs', 'open patched patched gone',
         'All four bells stripped; the escape refitted salvaged up and down '
         'bells for the roll couple, port and fore stay blanked until restored.',
         cluster='pod-port', plan={'derelict': {'*': 'stripped'},
                                   'patched': {'up': 'salvage', 'down': 'salvage', '*': 'blanked'},
                                   'refit': {'up': 'salvage', 'down': 'salvage', '*': 'blanked'}}),
    Scar('rcs-pod-stbd', 'rcs', 'open gone gone gone',
         'Dead while unpowered; working as built from the escape on.',
         cluster='pod-stbd', plan=DEAD),
    Scar('rcs-stern-port', 'rcs', 'open open open gone',
         'Whole block unbolted off the port thrust housing; its feed blind-'
         'flanged for the escape. Not needed while the port pod is cold (the '
         'starboard drive already yaws the bow to port); reinstalled at restoration.',
         cluster='stern-port', plan={'derelict': {'block': 'cut'},
                                     'patched': {'block': 'capped'},
                                     'refit': {'block': 'capped'}}),
    Scar('rcs-stern-stbd', 'rcs', 'open gone gone gone',
         'Dead while unpowered; working as built from the escape on.',
         cluster='stern-stbd', plan=DEAD),
    Scar('rcs-dorsal', 'rcs', 'open patched replaced gone',
         'up-2 taken in the belt and blanked for the escape; a new bell fitted at the refit.',
         cluster='dorsal', plan={'derelict': {'*': 'dead', 'up-2': 'stripped'},
                                 'patched': {'up-2': 'blanked'}}),
    Scar('rcs-ventral', 'rcs', 'open gone gone gone',
         'Dead while unpowered; working as built from the escape on.',
         cluster='ventral', plan=DEAD),
]
