"""The one rule for the review-sheet label font: Arial wherever it lives.

Windows keeps it in C:/Windows/Fonts, macOS in /System/Library/Fonts/Supplemental; DejaVu
Sans stands in on Linux. The first that exists wins, so a sheet's labels read the same on
both machines.

    sys.path.insert(0, str(<art-source dir>)); from review_font import review_font
"""
from pathlib import Path

from PIL import ImageFont

_CANDIDATES = {
    False: ("C:/Windows/Fonts/arial.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
    True: ("C:/Windows/Fonts/arialbd.ttf",
           "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
           "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
}


def review_font(size, bold=False):
    for path in _CANDIDATES[bold]:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    raise OSError("no review font found; looked for " + ", ".join(_CANDIDATES[bold]))
