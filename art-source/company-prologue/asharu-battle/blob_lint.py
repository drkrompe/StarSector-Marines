"""The white-blob lint (coordinator, 2026-10-23: the old HE look, a lobed white shape that holds):
on each finished frame, a connected region of near-white pixels that an effect owns, larger than a
blob's area, that persists past a flash's frames, is flagged with its frame and position.

  near-white  display luma above WHITE_LUMA (0-255) and saturation below WHITE_SAT
  owned       the effects layer makes more than OWNED of the pixel (composite_layers.effects_owned: its
              alpha or its share of the light, so an additive flash counts): what the kit drew, so the
              sun, the planet and a sunlit hull (backdrop and ships layers) can never trip it
  big         at least AREA_PX pixels as measured at 1920 wide (scaled by the frame's own size)
  persists    the same region (overlapping boxes) on consecutive frames for longer than HOLD_24
              frames at 24 fps; a torn HE's single white frame passes, a held flash does not
  lobed       at least THIN_PX thick as measured at 1920 wide (its area over its length along its
              main axis): a beam's or a tracer's white core is a line, never a blob (S8-S9 final
              f02208, 2026-10-25: a beam core 1195 px in an 84x358 box, about 3 px thick, held while the
              beam fired, once effects_owned counted additive light)
  excluded    a ship's drives: a region holding the screen point of any engine (engines_px) is a
              drive glow, by position, never by loosening the thresholds; a missile's motor and its
              glare count as engines (S5 final f01135, 2026-10-28: a Harpoon salvo leaving her tubes,
              lit for its whole burn); and a hull burning into the
              air: a region centred in the screen box of a hull the shot wraps in entry plasma (zones,
              S11 and S12) is her sheath, a glow held by design (S11 light check, 2026-10-26)

found() measures one frame (numpy, in Blender's Python); step() chains a frame's regions onto the frame
before. Lint does both as a span renders, so it must see the span's frames in order, and the chain
restarts when a frame is skipped (a resumed job) or at a chunk's first frame. A hold that runs across a
chunk seam is therefore caught only by chain(), which the shot's assembly runs over every frame's saved
regions in frame order (assemble_shot --blobs). chain() needs no numpy.
"""
WHITE_LUMA = 230.0
WHITE_SAT = .25
OWNED = .5
AREA_PX = 600          # at 1920 wide
THIN_PX = 10.0         # at 1920 wide: a region thinner than this is a streak, not a blob
HOLD_24 = 2            # a white region may hold this many frames at 24 fps


def regions(mask):
    """Connected regions (4-neighbour) of a boolean mask: [(area, (y0, x0, y1, x1), cy, cx, length)],
    length the region's extent along its main axis (a uniform bar's length from its pixel spread)."""
    import numpy as np
    h, w = mask.shape
    seen = np.zeros_like(mask, dtype=bool)
    out = []
    ys, xs = np.nonzero(mask)
    for y, x in zip(ys.tolist(), xs.tolist()):
        if seen[y, x]:
            continue
        stack, pts = [(y, x)], []
        seen[y, x] = True
        while stack:
            cy, cx = stack.pop()
            pts.append((cy, cx))
            for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    stack.append((ny, nx))
        a = np.array(pts, dtype=np.float64)
        spread = np.linalg.eigvalsh(np.cov(a.T)).max() if len(pts) > 1 else 0.0
        out.append((len(pts), (int(a[:, 0].min()), int(a[:, 1].min()), int(a[:, 0].max()), int(a[:, 1].max())),
                    float(a[:, 0].mean()), float(a[:, 1].mean()), max(1.0, (12.0 * spread) ** .5)))
    return out


def found(display, effects_owned, engines_px=(), zones=()):
    """One frame's white regions, drives and entry plasma left out: display (h, w, 3) 0-255 and
    effects_owned (h, w) 0-1, top row first; engines_px (x, y) pixel points, top-left origin; zones
    (x0, y0, x1, y1) pixel boxes of sheathed hulls. Returns [(area, box, cy, cx)]."""
    import numpy as np
    h, w = display.shape[:2]
    mx, mn = display.max(-1), display.min(-1)
    luma = display @ np.array([.2126, .7152, .0722], dtype=np.float32)
    sat = (mx - mn) / np.maximum(mx, 1.0)
    mask = (luma > WHITE_LUMA) & (sat < WHITE_SAT) & (effects_owned > OWNED)
    area = AREA_PX * (w / 1920.0) ** 2
    kept = []
    for r in regions(mask):
        if r[0] < area or r[0] / r[4] < THIN_PX * w / 1920.0:
            continue
        y0, x0, y1, x1 = r[1]
        pad = max(12.0 * w / 1920.0, .5 * max(y1 - y0, x1 - x0))
        if any(x0 - pad <= ex <= x1 + pad and y0 - pad <= ey <= y1 + pad for ex, ey in engines_px):
            continue
        if any(zx0 <= r[3] <= zx1 and zy0 <= r[2] <= zy1 for zx0, zy0, zx1, zy1 in zones):
            continue
        kept.append(r[:4])
    return kept


def _overlap(b0, b1, pad):
    return not (b0[2] + pad < b1[0] or b1[2] + pad < b0[0] or b0[3] + pad < b1[1] or b1[3] + pad < b0[1])


class Lint:
    def __init__(self):
        self.prev, self.prev_frame = [], None

    def frame(self, frame, dt, display, effects_owned, engines_px=(), zones=()):
        """frame: the span frame's number (consecutive frames chain); dt: seconds between frames; the
        rest as found(). Returns (rows, flagged) for this frame."""
        return self.step(frame, dt, display.shape[1], found(display, effects_owned, engines_px, zones))

    def step(self, frame, dt, width, kept):
        """Chain one frame's regions (found()) onto the frame before: (rows, flagged)."""
        chained = self.prev_frame is not None and frame == self.prev_frame + 1
        pad = 8.0 * width / 1920.0
        now, rows, flagged = [], [], []
        for r in kept:
            held = 1
            if chained:
                held += max((p[1] for p in self.prev if _overlap(p[0][1], r[1], pad)), default=0)
            now.append((r, held))
            # first sighting to last, in 24 fps frames; rounded, since dt comes from span times in seconds
            # and (f + 1) / 24 - f / 24 is a hair over 1/24: a two-frame hold read as 2.0000000000001 (S9b
            # final c00, 2026-10-25)
            frames_24 = round((held - 1) * dt * 24.0 + 1, 6)
            row = (f'white region {r[0]} px at x{int(r[3])} y{int(r[2])} (box {tuple(r[1])}), '
                   f'held {held} frame{"s" if held > 1 else ""} ({frames_24:.0f} at 24 fps)')
            rows.append(row)
            if frames_24 > HOLD_24:
                flagged.append(row)
        self.prev, self.prev_frame = now, frame
        return rows, flagged


def chain(records):
    """A whole shot's saved frames ({'frame', 'dt', 'width', 'regions'}, as battle_blockout writes them),
    chained in frame order across every chunk seam: [(frame, row)] for each region held too long."""
    lint, out = Lint(), []
    for rec in sorted(records, key=lambda r: r['frame']):
        kept = [(a, tuple(box), cy, cx) for a, box, cy, cx in rec['regions']]
        out += [(rec['frame'], row) for row in lint.step(rec['frame'], rec['dt'], rec['width'], kept)[1]]
    return out
