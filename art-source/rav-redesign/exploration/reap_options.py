"""The Matriarch's reap, as it is and with three fixes for a sweep that hides under the crown, for the owner
to pick from: the motion study's clip B (two reaps into Marines) trimmed to the reaps, at 64 px per cell and
at play zoom (32 px per cell), one GIF each.

  python reap_options.py <render root> <out dir>

  A-now           the study before the pick (the owner chose B, now the default)
  B-longer        scythes 1.4x longer, so the blade clears the crown's rim
  C-further-out   scythes mounted 0.35 cells further out from the body
  D-with-head     the scythes turn with the head as it turns toward each blade
"""
import sys
from pathlib import Path

import motion_matriarch as mm
from matriarch_skeleton import FPS, S_BACK, S_HOLD, S_LASH, S_WIND, STRIKES
from motion import save_gif

VARIANTS = [
    ("A-now", "AS IT WAS", dict(BLADE_SCALE=1.0)),
    ("B-longer", "LONGER SCYTHES", dict(BLADE_SCALE=1.4)),
    ("C-further-out", "MOUNTED FURTHER OUT", dict(BLADE_OUT=0.35)),
    ("D-with-head", "TURNED WITH THE HEAD", dict(BLADE_WITH_HEAD=True)),
]
T0 = STRIKES["R"] - 0.2
T1 = STRIKES["L"] + S_WIND + S_LASH + S_HOLD + S_BACK + 0.2


def main(root, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    scratch = out_dir / "_full.gif"
    defaults = dict(BLADE_SCALE=1.0, BLADE_OUT=0.0, BLADE_WITH_HEAD=False)
    for name, title, knobs in VARIANTS:
        for k, v in {**defaults, **knobs}.items():
            setattr(mm, k, v)
        frames, _, _ = mm.build(root, "B", scratch, title=f"REAP OPTION {name[0]}: {title}")
        trimmed = frames[int(T0 * FPS):int(T1 * FPS)]
        out = out_dir / f"reap-{name}.gif"
        save_gif(trimmed, out, FPS, accents=[(255, 168, 54), (60, 56, 58), (120, 24, 34), (236, 226, 206)])
        print(out.name, round(out.stat().st_size / 1e6, 2), "MB")
    scratch.unlink(missing_ok=True)


if __name__ == "__main__":
    main(sys.argv[1], Path(sys.argv[2]))
