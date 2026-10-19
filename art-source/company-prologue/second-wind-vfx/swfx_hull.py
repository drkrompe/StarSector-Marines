"""Which hull the kit dresses: the Second Wind by default, or another hull's profile.

The kit was written for the Second Wind and still defaults to it, untouched.
Another hull reuses it by supplying a profile: a folder holding `vfx_hull.py`,
chosen by the environment variable SWFX_HULL (or `swfx.apply(hull=DIR)`, which
sets it). A profile overrides any of these module-level names; whatever it
leaves out keeps the Second Wind's value:

  GEOMETRY_JSON     path of the hull's geometry.json (its metres_per_unit)
  ENGINES           [(x, y, width), ...] in engine index order
  ENGINE_INDICES    engine indices the throttle knobs cover (default 1..len(ENGINES))
  BELL_R            {width: bell mouth radius (hull units)}
  PORT_ENGINES, STARBOARD_ENGINES   index tuples; any other index is 'centre'
  STATES            condition table (swfx_core.STATES shape)
  MOUNTS            [(x, y, angle, scale, twin[, kind]), ...] in turret index order;
                    kind 'gun' (default) or 'launcher' (a missile mount: launch
                    effects instead of muzzle flash, tracer and brass)
  DECLARED_MUZZLES  True: guns come from the ship's own 'Anchor turret <i> <tag>
                    muzzle' empties (+X along the line of fire) instead of the
                    Second Wind's muzzle-brake parts; a multi-barrel mount tags
                    its barrels 'barrel <k>' (or 'tube <k>' on a launcher), and
                    k is the gun's index in its knob names (t<i>_<k>)
  LAMPS             [(object name prefix, role, colour or None), ...]
  RCS_QUADS         art-placed RCS quads ({} for none; geometry RCS anchors
                    always win when present)

The profile is loaded once per import; `swfx.apply` reloads this module first,
so a shot can switch hulls between calls.
"""
import importlib.util
import os
from pathlib import Path

ENV = 'SWFX_HULL'


def _load():
    d = os.environ.get(ENV)
    if not d:
        return None
    path = Path(d).resolve() / 'vfx_hull.py'
    spec = importlib.util.spec_from_file_location('vfx_hull', path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.DIR = path.parent
    return mod


PROFILE = _load()
NAME = getattr(PROFILE, 'NAME', 'Second Wind')


def get(name, default):
    """The profile's value for `name`, else the Second Wind's `default`."""
    return getattr(PROFILE, name, default) if PROFILE is not None else default
