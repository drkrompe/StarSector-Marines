"""Colonist layers: mechanical export of the civilian, engineer and scientist reskins.

    python art-source/colonist-modular-topdown/colonists.py export

The colonists wear the marines' anatomy, exactly as the ship crew do, and are cut
from the same silhouette guide (`art-source/templates/ship-crew-silhouettes.png`,
made by `ship_crew.py guide`). The frame layout, the nearest-neighbour downsample
and the marine-silhouette alpha bound are the crew's own, imported rather than
copied, so the two families cannot drift apart.
"""

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import sys

_spec = spec_from_file_location("ship_crew", Path("art-source/ship-crew/ship_crew.py"))
ship_crew = module_from_spec(_spec)
_spec.loader.exec_module(ship_crew)

ROLES = ("civilian", "engineer", "scientist")
SOURCE = Path("art-source/colonist-modular-topdown")
TARGET = Path("mod/graphics/battle/colonist-modular-topdown")


def export() -> None:
    ship_crew.export_roles(ROLES, SOURCE, TARGET)


if __name__ == "__main__":
    {"export": export}[sys.argv[1] if len(sys.argv) > 1 else "export"]()
