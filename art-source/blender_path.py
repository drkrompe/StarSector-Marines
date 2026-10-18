"""The one rule for finding Blender, shared by every art-source build script that launches it.

$BLENDER if set, else the platform default -- Windows: Blender 5.2 under Program Files;
macOS: /Applications/Blender.app; anything else: `blender` on PATH. blender.sh beside this
file is the shell twin and must agree with it.

    sys.path.insert(0, str(<art-source dir>)); from blender_path import BLENDER

Run as a script it launches Blender with its arguments: python blender_path.py --background ...
"""
import os
import shutil
import subprocess
import sys

_DEFAULTS = {
    "win32": r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe",
    "darwin": "/Applications/Blender.app/Contents/MacOS/Blender",
}


def blender_executable() -> str:
    return (os.environ.get("BLENDER")
            or _DEFAULTS.get(sys.platform)
            or shutil.which("blender")
            or "blender")


BLENDER = blender_executable()

if __name__ == "__main__":
    sys.exit(subprocess.call([BLENDER, *sys.argv[1:]]))
