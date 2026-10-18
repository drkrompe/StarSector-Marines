"""The one rule for finding the installed game's starsector-core (read-only reference data).

$STARSECTOR_CORE if set, else the platform default -- Windows: the Fractal Softworks install
under Program Files (x86); macOS: Starsector.app's Contents/Resources/Java, which holds the
same data/ and graphics/ trees.

    sys.path.insert(0, str(<art-source dir>)); from starsector_path import GAME_CORE
"""
import os
import sys
from pathlib import Path

_DEFAULTS = {
    "win32": r"C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core",
    "darwin": "/Applications/Starsector.app/Contents/Resources/Java",
}

GAME_CORE = Path(os.environ.get("STARSECTOR_CORE")
                 or _DEFAULTS.get(sys.platform, _DEFAULTS["win32"]))
