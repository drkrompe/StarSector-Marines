"""Measure the midline carriers' tyre tread bands for the game's rolling-tyre trick (VehicleTreadRender).

For each tyre on the packed live hull frame (pack.py's sheet, frame 0), finds the tyre's exposed outer half past the
plate: the straight middle of it along the hull, from just inside its outer edge to the plate's edge, and the tread's
repeat in pixels (the lag at which the band's column brightness best matches itself). Prints one Band row per tyre:
segment, start, end, y, height, tileX, tileWidth, in frame pixels.

Run: python art-source/vehicles/midline-carriers/treads.py
"""
from pathlib import Path

from PIL import Image

from guide import HULLS, layout, length

HERE = Path(__file__).resolve().parent
SHEETS = HERE.parents[2] / "mod" / "graphics" / "battle" / "vehicles"
OUT_PX = 176


def frame(weight):
    im = Image.open(SHEETS / f"midline-carrier-{weight}.png").convert("RGBA")
    rows = 3 if len(HULLS[weight]["segments"]) > 2 else 2
    return im.crop((0, 0, im.width // 4, im.height // rows))


def lum(px):
    r, g, b, a = px
    return (r * 299 + g * 587 + b * 114) / 1000 if a > 128 else None


def opaque(px):
    return px[3] > 128


def is_plate(px):
    # The plate is warm tan; the tyre's rubber and its tread blocks are grey.
    r, g, b, a = px
    return a > 128 and r - b >= 28


def is_tyre(px):
    return opaque(px) and not is_plate(px)


def period(img, x0, x1, y0, y1):
    """The lag, pixels along the hull, at which the band best matches itself shifted: the tread's repeat."""
    best, best_lag = None, None
    for lag in range(12, 27):
        a, b = [], []
        for y in range(y0, y1):
            for x in range(x0, x1 - lag):
                p, q = lum(img.getpixel((x, y))), lum(img.getpixel((x + lag, y)))
                if p is not None and q is not None:
                    a.append(p)
                    b.append(q)
        ma, mb = sum(a) / len(a), sum(b) / len(b)
        num = sum((p - ma) * (q - mb) for p, q in zip(a, b))
        den = (sum((p - ma) ** 2 for p in a) * sum((q - mb) ** 2 for q in b)) ** .5
        if best is None or num / den > best:
            best, best_lag = num / den, lag
    return best_lag


def bands(weight):
    hull = HULLS[weight]
    img = frame(weight)
    fw, fh = img.size
    total = length(hull)
    edges, _ = layout(hull)
    nose = fw / 2 - total / 2 * OUT_PX
    wl = hull["wheel"][0]
    out = []
    for s, ((_, _, width, axles), (front, _)) in enumerate(zip(hull["segments"], edges)):
        # Rust on the tread can read as plate: the plate's edge is never nearer the tyre than the guide drew it.
        guide_plate = (round(fh / 2 - width / 2 * OUT_PX) - 2, round(fh / 2 + width / 2 * OUT_PX) + 2)
        for a in axles:
            cx = round(nose + (front + a) * OUT_PX)
            for top in (True, False):
                # Walk in from the frame's edge at the tyre's centre column: air, then tyre, then plate.
                ys = list(range(0, fh // 2)) if top else list(range(fh - 1, fh // 2, -1))
                # The frame's edge may carry a sliver of the next row's art: start past the first clear pixel.
                clear = next(i for i, y in enumerate(ys) if not opaque(img.getpixel((cx, y))))
                ys = ys[clear:]
                tyre_edge = next(y for y in ys if opaque(img.getpixel((cx, y))))
                floor = guide_plate[0] if top else guide_plate[1]
                rest = [y for y in ys[ys.index(tyre_edge):] if (y >= floor if top else y <= floor)]
                plate_edge = next(y for i, y in enumerate(rest[:-1])
                                  if is_plate(img.getpixel((cx, y))) and is_plate(img.getpixel((cx, rest[i + 1]))))
                # The tyre's run along the hull, on a row near its outer edge where nothing else is drawn.
                row = tyre_edge + 4 if top else tyre_edge - 4
                x0 = cx
                while x0 > 0 and opaque(img.getpixel((x0 - 1, row))):
                    x0 -= 1
                x1 = cx
                while x1 < fw - 1 and opaque(img.getpixel((x1 + 1, row))):
                    x1 += 1
                trim = round((x1 - x0) * .2)
                start, end = x0 + trim, x1 - trim
                if top:
                    y, height = tyre_edge + 2, plate_edge - tyre_edge - 4
                else:
                    y, height = plate_edge + 3, tyre_edge - plate_edge - 4
                tile = period(img, start - 6, end + 6, y, y + height)
                out.append((s, start, end, y, height, start, min(tile, end - start)))
    return out


def main():
    for weight in HULLS:
        print(weight)
        for b in bands(weight):
            print("    new Band(%d, %d, %d, %d, %d, %d, %d)," % b)


if __name__ == "__main__":
    main()
