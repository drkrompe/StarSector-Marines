"""Ships the mass-driver sheet: `pit-massdriver.png` with its near-invisible haze cleared (alpha under 8 to fully
transparent, colour zeroed), so no stray tint bleeds in when the frames are filtered down.

    python art-source/voids/open-pit/massdriver-export.py
"""
from pathlib import Path

import numpy as np
from PIL import Image

here = Path(__file__).parent
x = np.array(Image.open(here / "pit-massdriver.png").convert("RGBA"))
x[x[..., 3] < 8] = 0
out = here.parents[2] / "mod" / "graphics" / "battle" / "voids" / "pit-massdriver.png"
Image.fromarray(x).save(out)
print(out)
