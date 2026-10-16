"""Atlas guide for the realism repaint: the reveal scene's own Atlas, greyed and relit.

python c3_atlas_guide.py     # writes guides/c3-atlas-guide.png (2048x1024 RGBA)

The reveal scene's Atlas (`PARALLAX | Atlas industrial transit` in
forest-reveal-realism.blend) is a single four-vertex plane textured with
parallax-window/traffic/atlas-painted-v1.png, so rendering it at C3's camera returns
that card at C3's size: C3 sees it beam-on, and its bearing changes about 12 degrees over
the shot, a 4 px end-face sliver (c3-layout.md), so no re-pose is needed. The guide is
therefore that card's exact silhouette with its structure kept as grey value and the
shared sun's light laid over it (top and upper-left lit, the flank in partial shade, the
underside darker). Its job is to pin silhouette, pose and scale; the brief and the
painted card carry identity.
"""
import numpy as np
from PIL import Image

from c3_core import BASE, CIN

CARD = CIN / "parallax-window" / "traffic" / "atlas-painted-v1.png"
OUT = BASE / "guides" / "c3-atlas-guide.png"
SIZE = (2048, 1024)


def main():
    im = Image.open(CARD).convert("RGBA")
    im = im.crop(im.getchannel("A").getbbox())
    s = min((SIZE[0] - 160) / im.width, (SIZE[1] - 160) / im.height)
    im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    a = np.asarray(im, np.float32) / 255
    lum = a[..., :3] @ np.array([0.299, 0.587, 0.114], np.float32)
    lum = 0.5 + (lum - lum[a[..., 3] > 0.5].mean()) * 0.8        # structure, flattened
    h, w = lum.shape
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    # shared sun: upper left, a little behind the camera -> top rows lit, falling to shade
    ramp = 1.15 - 0.55 * (yy / h) - 0.12 * (xx / w)
    g = np.clip(lum * ramp, 0.05, 0.95)
    rgba = np.dstack([g, g, g * 1.02, a[..., 3]])
    out = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    card = Image.fromarray((np.clip(rgba, 0, 1) * 255).astype(np.uint8))
    out.alpha_composite(card, ((SIZE[0] - w) // 2, (SIZE[1] - h) // 2))
    out.save(OUT)
    # the edit target Codex sees: the same on flat light grey, so the silhouette reads
    flat = Image.new("RGBA", SIZE, (214, 218, 222, 255))
    flat.alpha_composite(out)
    flat.convert("RGB").save(OUT.with_name("c3-atlas-guide-flat.png"))
    print("wrote", OUT, card.size)


if __name__ == "__main__":
    main()
