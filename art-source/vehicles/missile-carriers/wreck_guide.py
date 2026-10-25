"""Edit targets for the missile carriers' wrecked pods: each live pod, nose-left, enlarged onto a 1024x1024 canvas.

ImageGen damages the pod in place (`lrm-wreck.prompt.txt`, `srm-wreck.prompt.txt`); `pack.py` fits the raw back to
the live pod by its alpha bounding box, so the wreck keeps the pod's size on the sheet. Run from anywhere:
    python art-source/vehicles/missile-carriers/wreck_guide.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
PODS = HERE.parents[2] / "mod" / "graphics" / "battle" / "mech-modular-topdown"
CANVAS = 1024
LENGTH_PX = 720


def main():
    for name in ("lrm", "srm"):
        src = Image.open(PODS / f"{name}-pod.png").convert("RGBA")
        src = src.crop(src.getbbox()).rotate(90, expand=True)
        scale = LENGTH_PX / src.width
        pod = src.convert("RGBa").resize((round(src.width * scale), round(src.height * scale)),
                                         Image.LANCZOS).convert("RGBA")
        out = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
        out.alpha_composite(pod, ((CANVAS - pod.width) // 2, (CANVAS - pod.height) // 2))
        out.save(HERE / f"{name}-wreck-guide.png")
        print(f"wrote {HERE / f'{name}-wreck-guide.png'}")


if __name__ == "__main__":
    main()
