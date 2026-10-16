"""Dozer blade geometry in 768-pixel hull-frame coordinates (nose toward -x).

This is the geometry authority: the guide draws it as flat grey, and the pack
uses its alpha (slightly dilated) to cut the generated blade out of the
ImageGen edit, so the hull pixels in the sheet stay exactly the registered
breakthrough hull.
"""
from PIL import Image, ImageDraw

CY = 384
HALF = 204          # blade half-span: 180..588, tracks are 196..563
LEAD = 22           # cutting-edge x at the centerline
BOW = -12           # wing tips curve forward to x=10 (374 px = 2.24 cells ahead of center)
DEPTH = 28          # moldboard depth seen from above
ARMS = [(262, 282), (486, 506)]
RAMS = [(304, 314), (454, 464)]
ARM_END = 118       # arms run under the hull nose (hull composites over them)


def lead_x(y):
    t = (y - CY) / HALF
    return LEAD + BOW * t * t


def draw(scale=1, wreck=False):
    """Flat-grey guide geometry on a transparent 768*scale square.

    The wreck variant skews the blade (upper wing knocked back), kinks the
    upper push arm, snaps the upper ram and tears a notch out of the lower
    moldboard; the extraction mask is this same silhouette.
    """
    if wreck:
        return _draw_wreck(scale)
    s = scale
    im = Image.new('RGBA', (768 * s, 768 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # push arms and rams first (behind the moldboard)
    for (y0, y1) in ARMS:
        d.rectangle([30 * s, y0 * s, ARM_END * s, y1 * s], fill=(118, 118, 118, 255))
        d.rectangle([30 * s, (y0 + 4) * s, ARM_END * s, (y0 + 7) * s], fill=(150, 150, 150, 255))
    for (y0, y1) in RAMS:
        d.rectangle([34 * s, y0 * s, 70 * s, y1 * s], fill=(165, 165, 165, 255))     # chrome rod
        d.rectangle([70 * s, (y0 - 2) * s, ARM_END * s, (y1 + 2) * s], fill=(96, 96, 96, 255))  # cylinder
    # moldboard as a polygon band
    top = HALF
    pts_front = [(lead_x(y), y) for y in range(CY - top, CY + top + 1, 4)]
    pts_back = [(lead_x(y) + DEPTH, y) for y in range(CY + top, CY - top - 1, -4)]
    poly = pts_front + pts_back
    d.polygon([(x * s, y * s) for x, y in poly], fill=(140, 140, 140, 255))
    # bare cutting edge strip
    edge = [(lead_x(y), y) for y in range(CY - top, CY + top + 1, 4)] + \
           [(lead_x(y) + 5, y) for y in range(CY + top, CY - top - 1, -4)]
    d.polygon([(x * s, y * s) for x, y in edge], fill=(200, 200, 200, 255))
    # rear stiffener ribs
    for y in range(CY - top + 24, CY + top - 10, 34):
        x = lead_x(y) + DEPTH
        d.rectangle([x * s, (y - 3) * s, (x + 8) * s, (y + 3) * s], fill=(105, 105, 105, 255))
    # end wings
    for sign in (-1, 1):
        y = CY + sign * top
        x = lead_x(y)
        d.rectangle([x * s, min(y, y + sign * 6) * s, (x + DEPTH + 10) * s, max(y, y + sign * 6) * s],
                    fill=(125, 125, 125, 255))
    return im


def _skew(y):
    # upper wing pushed back up to 16 px, lower end unchanged
    t = (CY + HALF - y) / (2 * HALF)
    return 16 * t * t


def _draw_wreck(s):
    im = Image.new('RGBA', (768 * s, 768 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    P = lambda pts: [(x * s, y * s) for x, y in pts]
    (a0, a1), (b0, b1) = ARMS
    # upper arm kinked: from hull straight, then bent toward -y
    d.polygon(P([(ARM_END, a0), (78, a0), (52, a0 - 14), (44, a0 - 12), (44, a1 - 12), (52, a1 - 14), (76, a1), (ARM_END, a1)]),
              fill=(118, 118, 118, 255))
    # lower arm intact
    d.rectangle([30 * s, b0 * s, ARM_END * s, b1 * s], fill=(118, 118, 118, 255))
    d.rectangle([30 * s, (b0 + 4) * s, ARM_END * s, (b0 + 7) * s], fill=(150, 150, 150, 255))
    # upper ram snapped: cylinder stub plus a separate bent rod
    (r0, r1), (q0, q1) = RAMS
    d.rectangle([84 * s, (r0 - 2) * s, ARM_END * s, (r1 + 2) * s], fill=(96, 96, 96, 255))
    d.polygon(P([(40, r0 - 6), (64, r0 + 2), (62, r1 + 1), (38, r1 - 6)]), fill=(165, 165, 165, 255))
    # lower ram intact
    d.rectangle([34 * s, q0 * s, 70 * s, q1 * s], fill=(165, 165, 165, 255))
    d.rectangle([70 * s, (q0 - 2) * s, ARM_END * s, (q1 + 2) * s], fill=(96, 96, 96, 255))
    # skewed moldboard
    ys = list(range(CY - HALF, CY + HALF + 1, 4))
    front = [(lead_x(y) + _skew(y), y) for y in ys]
    back = [(lead_x(y) + _skew(y) + DEPTH, y) for y in reversed(ys)]
    d.polygon(P(front + back), fill=(140, 140, 140, 255))
    edge = front + [(lead_x(y) + _skew(y) + 5, y) for y in reversed(ys)]
    d.polygon(P(edge), fill=(200, 200, 200, 255))
    for y in range(CY - HALF + 24, CY + HALF - 10, 34):
        x = lead_x(y) + _skew(y) + DEPTH
        d.rectangle([x * s, (y - 3) * s, (x + 8) * s, (y + 3) * s], fill=(105, 105, 105, 255))
    # torn notch in the lower moldboard, cutting edge ripped away there
    ny = CY + 92
    d.polygon(P([(lead_x(ny - 22) - 2, ny - 22), (lead_x(ny) + 18, ny - 4), (lead_x(ny + 6) + 12, ny + 10),
                 (lead_x(ny + 26) - 2, ny + 26), (0, ny + 26), (0, ny - 22)]), fill=(0, 0, 0, 0))
    # lower wing only; the upper wing plate is torn off
    y = CY + HALF
    x = lead_x(y)
    d.rectangle([x * s, y * s, (x + DEPTH + 10) * s, (y + 6) * s], fill=(125, 125, 125, 255))
    return im
