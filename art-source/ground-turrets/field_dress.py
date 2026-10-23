"""Field dressing for the earthwork autogun and cannon heads, which the image tool refused to repaint four times.

Instead of a flat recolour, the brutalist head takes the colour statistics of the generated earthwork grenade head
(its sand-and-khaki paint) channel by channel, and the band of burlap sacks lashed across that head's roof is lifted,
scaled to the target body's width and laid across the target's roof just behind its tubes. Both inputs are retained
generations; nothing is painted by hand.
"""
from PIL import Image, ImageStat


def _body_rows(img):
    """(first body row, body x0, body x1): the first row wider than half the widest row, and its opaque span."""
    a = img.getchannel("A").load()
    w, h = img.size
    spans = []
    for y in range(h):
        xs = [x for x in range(w) if a[x, y] > 128]
        spans.append((len(xs), xs[0] if xs else 0, xs[-1] if xs else 0))
    limit = max(s[0] for s in spans) * 0.5
    for y in range(h):
        if spans[y][0] > limit:
            return y, spans[y][1], spans[y][2]
    raise ValueError("no body")


def colour_transfer(target, source):
    """target recoloured so each channel's mean and spread over its opaque pixels match source's."""
    t_mask = target.getchannel("A").point(lambda v: 255 if v > 128 else 0)
    s_mask = source.getchannel("A").point(lambda v: 255 if v > 128 else 0)
    t_stat = ImageStat.Stat(target.convert("RGB"), t_mask)
    s_stat = ImageStat.Stat(source.convert("RGB"), s_mask)
    bands = []
    for i, band in enumerate(target.convert("RGB").split()):
        k = s_stat.stddev[i] / max(1e-3, t_stat.stddev[i])
        m_t, m_s = t_stat.mean[i], s_stat.mean[i]
        bands.append(band.point(lambda v, k=k, m_t=m_t, m_s=m_s: max(0, min(255, int(m_s + (v - m_t) * k)))))
    out = Image.merge("RGB", bands).convert("RGBA")
    out.putalpha(target.getchannel("A"))
    return out


def sack_band(source, rows=0.16):
    """The roof strip of the source head just below its tubes, where its sacks are lashed: a band `rows` of the
    object's height deep, across the body's span at that row."""
    y, x0, x1 = _body_rows(source)
    depth = max(4, int(rows * source.size[1]))
    return source.crop((x0, y, x1 + 1, y + depth))


def dress(target, source):
    """target (a brutalist head, composed) in source's (the earthwork grenade head's) field dress."""
    out = colour_transfer(target, source)
    band = sack_band(source)
    y, x0, x1 = _body_rows(out)
    width = x1 - x0 + 1
    height = max(1, round(band.size[1] * width / band.size[0]))
    band = band.resize((width, height), Image.LANCZOS)
    # Keep the band inside the target's own silhouette so the outline (and the muzzles) do not change.
    clip = out.getchannel("A").crop((x0, y, x0 + width, y + height))
    band_alpha = Image.composite(band.getchannel("A"), Image.new("L", band.size, 0), clip.point(lambda v: 255 if v > 128 else 0))
    band.putalpha(band_alpha)
    out.alpha_composite(band, (x0, y))
    return out
