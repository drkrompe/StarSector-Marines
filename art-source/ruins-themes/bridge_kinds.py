"""Block-out kinds for the mega forge's transfer bridge (``forge-bridge``): a heavy deck carried east-west over the
breakout's lava pool on the transfer aisle (``RuinsDelveStage.transferBridge``). Each piece is 2 cells along the span
(east-west) by 4 across (north-south); the kerbs and railings run along its north and south edges.

- ``b/span``: the deck on two box girders, flush to both short ends so spans join end to end. Its plates and the
  girders' stiffeners repeat on a half-cell period so ``export.py`` heals it seamlessly along the span.
- ``b/end``: the abutment at the west bank (mirrored for the east): a cast footing on the floor in the west half, the
  deck landing on it, flush to the east edge.
- ``b/damaged``: a span with its north railing torn away and the deck buckled across its middle.
"""
from PIL import ImageDraw

BODY, EDGE, DARK, LIGHT = (128, 132, 138, 255), (70, 74, 80, 255), (40, 42, 46, 255), (176, 180, 186, 255)
GIRDER = (96, 100, 106, 255)
FOOT = (150, 146, 138, 255)
MELT = (214, 120, 48, 255)


def _deck(d, x0, x1, y, h, torn=False, buckle=False):
    """The deck from x0 to x1 over the piece's full height: girder edges, kerbs, railings and plates."""
    rail, kerb, girder = 0.05 * h, 0.06 * h, 0.07 * h
    # Box girders showing past the deck along both long edges, with the melt's glow on their outer faces.
    d.rectangle((x0, y, x1, y + girder), fill=GIRDER)
    d.rectangle((x0, y + h - girder, x1, y + h), fill=GIRDER)
    d.rectangle((x0, y, x1, y + 8), fill=MELT)
    d.rectangle((x0, y + h - 8, x1, y + h), fill=MELT)
    top, bot = y + girder, y + h - girder
    d.rectangle((x0, top, x1, bot), fill=BODY)
    # Kerbs and railings along both long edges.
    for ky, sign in ((top, 1), (bot, -1)):
        if torn and sign == 1:
            continue
        k0, k1 = sorted((ky, ky + sign * kerb))
        d.rectangle((x0, k0, x1, k1), fill=EDGE)
        r0, r1 = sorted((ky + sign * kerb, ky + sign * (kerb + rail)))
        d.rectangle((x0, r0, x1, r1), fill=LIGHT)
    if torn:
        # The torn railing's stubs and a bent length hanging off the edge.
        for px in range(x0 + 40, x1, 80):
            d.rectangle((px - 6, top, px + 6, top + kerb), fill=EDGE)
        d.line([(x0 + 30, top + 10), (x0 + (x1 - x0) * 0.5, top - 4), (x1 - 40, top + 26)], fill=LIGHT, width=10)
    # Deck plates on a half-cell period, so the span repeats in step.
    pitch = 80
    for px in range(x0, x1 + 1, pitch):
        d.line([(px, top + kerb + rail), (px, bot - kerb - rail)], fill=DARK, width=4)
    mid = (top + bot) / 2
    d.line([(x0, mid), (x1, mid)], fill=EDGE, width=6)
    if buckle:
        cx = (x0 + x1) / 2
        d.polygon([(cx - 70, top + kerb + rail), (cx + 40, top + kerb + rail), (cx + 90, bot - kerb - rail),
                   (cx - 30, bot - kerb - rail)], fill=DARK)
        d.line([(cx - 70, top + kerb + rail), (cx - 30, bot - kerb - rail)], fill=LIGHT, width=6)


def draw_kind(im, d, kind, x, y, w, h):
    if not kind.startswith("b/"):
        return False
    if kind == "b/span":
        _deck(d, x, x + w - 1, y, h)
    elif kind == "b/damaged":
        _deck(d, x, x + w - 1, y, h, torn=True, buckle=True)
    elif kind == "b/end":
        half = x + w // 2
        # The cast footing on the bank: a plinth across the whole width with anchor bolts, its west edge on the floor.
        d.rectangle((x + 10, y + 10, half + 30, y + h - 10), fill=FOOT, outline=EDGE, width=8)
        for by in (y + 0.2 * h, y + 0.8 * h):
            for bx in (x + 50, x + 110):
                d.ellipse((bx - 12, by - 12, bx + 12, by + 12), fill=DARK)
        _deck(d, half - 20, x + w - 1, y, h)
        # The deck's end plate where it bears on the footing.
        d.rectangle((half - 30, y + 0.13 * h, half - 10, y + 0.87 * h), fill=EDGE)
    return True
