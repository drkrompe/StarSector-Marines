"""Picture cards for a Spitter head round: each head close up, and set on the current back and tail at HEAD_SCALE.

  python spitter_head_cards.py <round dir> <out png>

The heads were generated in place on the parts canvas, so each is keyed and composited over the back and the tail
exactly where the exporter puts them. Writes one 1920x1080 sheet of up to six cards.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "spitter"))
from export_spitter_rig import RAW, keyed, to_image  # noqa: E402

GROUND = (60, 74, 54, 255)
NECK = (512, 330)        # canvas px the head is scaled about, where it meets the back
HEAD_SCALE = 0.5         # the heads were generated far too large for the body


def shrink_about_neck(head, k):
    """The head scaled by k about NECK, on the same canvas."""
    w, h = head.size
    small = head.resize((round(w * k), round(h * k)), Image.LANCZOS)
    out = Image.new("RGBA", head.size, (0, 0, 0, 0))
    out.alpha_composite(small, (round(NECK[0] * (1 - k)), round(NECK[1] * (1 - k))))
    return out


def card(head_path, back, tail, label):
    full = to_image(keyed(head_path))
    c = Image.new("RGBA", (640, 540), GROUND)
    close = full.crop((180, 0, 844, 560)).resize((380, 320), Image.LANCZOS)
    head = shrink_about_neck(full, HEAD_SCALE)
    c.alpha_composite(close, (10, 60))
    whole = Image.new("RGBA", (1024, 1536), (0, 0, 0, 0))
    whole.alpha_composite(tail)
    whole.alpha_composite(back)
    whole.alpha_composite(head)
    small = whole.crop((150, 0, 874, 1536)).resize((215, 456), Image.LANCZOS)
    c.alpha_composite(small, (410, 60))
    d = ImageDraw.Draw(c)
    try:
        font = ImageFont.truetype("arial.ttf", 26)
    except OSError:
        font = ImageFont.load_default()
    d.rectangle((0, 0, 640, 44), fill=(17, 24, 32, 255))
    d.text((14, 8), label, fill=(230, 230, 230, 255), font=font)
    return c


def main():
    round_dir, out = Path(sys.argv[1]), Path(sys.argv[2])
    back = to_image(keyed(RAW / "part-back.png"))
    tail = to_image(keyed(RAW / "part-tail.png"))
    heads = sorted((round_dir / "raw").glob("h*.png"))
    sheet = Image.new("RGBA", (1920, 1080), (17, 24, 32, 255))
    for i, path in enumerate(heads[:6]):
        name = path.stem.split("-", 1)
        label = f"#{name[0][1:]}  {name[1].replace('-', ' ').title()}"
        sheet.alpha_composite(card(path, back, tail, label), ((i % 3) * 640, (i // 3) * 540))
    sheet.convert("RGB").save(out)


if __name__ == "__main__":
    main()
