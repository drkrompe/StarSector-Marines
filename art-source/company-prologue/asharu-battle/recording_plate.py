"""The recording screen as a finished film plate (owner, 2026-10-26: "Make a 'finished version' which has
most of the template and we can leave the words to runtime"). S0 opens on it, S11's last second and a
half breaks into its static, and S13 closes on it (asharu-battle-shot.md, "Framing: a comms recording").

The plate carries everything but words: the bezel, the header band, the channel column (a lamp and a
level meter per voice, lit while it speaks, dark red once it drops), the scope with the voice's waveform,
the playback bar, and the finish (bloom, scanlines, grain, vignette). Every word is the game's, drawn
over the plate at run time, localised, with the date computed from the player's arrival: LAYOUT is
where each goes, written out with --layout for the arrival player. --words draws them in, from
radio_log, as the game would: for judging the plate, never for the film.

Timing is radio_log's, in sequence seconds, and every frame is computed from its own time alone (the
waveform's noise is seeded by time), so any frame renders the same alone, in any order.

    python recording_plate.py s0|s13 <out dir> [--words] [--frames A:B] [--percent P]
    python recording_plate.py s11 <out dir> --under <dir of the S11 frames> [--words]
    python recording_plate.py --layout <file.json>

Frames are '<kf>_f<sequence frame>.png' at 24 fps, kf 'rec0' (S0), 'm11' (S11), 'rec13' (S13).
"""
import json
import math
import random
import sys
from pathlib import Path

import radio_log

W, H, FPS = 1920, 1080, 24
SHOTS = {'s0': ('rec0', 0.0, 10.0, 'open'), 's11': ('m11', 118.0, 126.0, 'static'),
         's13': ('rec13', 134.0, 147.0, 'close')}
RECORDING_END_S = 124.5         # S11: the picture breaks into the screen's static here (asharu_preview)
FADE_IN = (.8, 1.6)             # S0: black, then the screen

BG0, BG1 = (9, 12, 15), (14, 19, 23)
LINE = (44, 54, 62)
DIM = (70, 82, 92)
AMBER = (255, 178, 84)
DEAD = (120, 52, 46)
WORD = (232, 236, 240)

BEZEL = (64, 48, 1856, 1032)
HEADER_Y = 252
ROW0, ROW_H = 300, 44
LAMP_X, METER_X = 150, 500
SCOPE = (640, 300, 1792, 760)
BAR_Y = 812
CAPTION_Y = 872

# Where the game draws each word over the plate: pixels at 1920x1080, top-left origin; size is the cap
# height the plate's spacing was drawn for; align is the slot's anchor.
LAYOUT = {
    'place': dict(x=128, y=92, size=56, align='left', text='ASHARU'),
    'region': dict(x=128, y=166, size=28, align='left', text='Corvus · low orbit'),
    'date': dict(x=1792, y=98, size=28, align='right', text='arrival date − 3 cycles, the game\'s date format'),
    'clock': dict(x=1792, y=142, size=28, align='right', text='the recording clock, 06:41:12 at 0 s'),
    'source': dict(x=128, y=212, size=20, align='left', text='RECORDING · CONVOY COMMON'),
    'channels': [dict(x=176, y=ROW0 + i * ROW_H, size=22, align='left', text=ch,
                      dropped=' — no carrier' if ch in radio_log.DROPS else None)
                 for i, ch in enumerate(radio_log.SCREEN_CHANNELS)],
    'caption_who': dict(x=128, y=CAPTION_Y, size=28, align='left', text='the speaking callsign'),
    'caption_line': dict(x=128, y=CAPTION_Y + 44, size=30, align='left', text='the line, localised'),
    'end_card': dict(x=1216, y=530, size=56, align='center', text='END OF RECORDING', at=radio_log.END_CARD_S),
}


def lines_for(mode):
    return radio_log.OPENING if mode == 'open' else radio_log.CLOSING if mode == 'close' else []


def envelope(t, lines):
    """How loud the channel is at t (0-1): each line a voice, syllables at about 5 a second, falling
    away at its commas and full stops, seeded by the line so it always speaks the same."""
    for _, who, t0, t1, text in lines:
        if t0 <= t <= t1:
            u = (t - t0) / (t1 - t0)
            # pauses where the line's punctuation falls, by its position in the text
            pause = 1.0
            for k, ch in enumerate(text):
                if ch in ',.?':
                    d = abs(u - k / len(text)) * (t1 - t0)
                    pause = min(pause, .15 + .85 * min(1.0, d / .18))
            syl = .55 + .45 * math.sin(t * 2 * math.pi * 4.7 + len(text)) * math.sin(t * 2 * math.pi * 1.3 + t0)
            edge = min(1.0, (t - t0) / .08, (t1 - t) / .12)
            return max(0.0, edge * pause * (.35 + .65 * abs(syl))), who
    return 0.0, None


def live(t, mode):
    """The callsigns lit at t, and those whose carrier is gone."""
    lines = lines_for(mode)
    on = {ln[1] for ln in lines if ln[2] <= t <= ln[3]}
    dead = {k for k, t_off in radio_log.DROPS.items() if t >= t_off}
    if mode == 'static':        # the dying channels flicker as the plasma takes them
        rnd = random.Random(int(t * 1000))
        on |= {k for k, t_off in radio_log.DROPS.items() if t < t_off and rnd.random() > .35}
    return on, dead


def frame(t, mode, words=False, under=None):
    """One plate frame at sequence time t: a PIL RGB image, 1920x1080."""
    import numpy as np
    from PIL import Image, ImageDraw, ImageFilter
    rnd = random.Random(int(round(t * FPS)) * 7919 + 17)
    yy = np.linspace(0, 1, H, dtype=np.float32)[:, None, None]
    base = np.array(BG0, np.float32) * (1 - yy) + np.array(BG1, np.float32) * yy
    im = Image.fromarray(np.broadcast_to(base, (H, W, 3)).astype(np.uint8))
    glow = Image.new('RGB', (W, H))                     # what is lit, bloomed and added
    dr, dg = ImageDraw.Draw(im), ImageDraw.Draw(glow)

    # the bezel: a rounded frame, corner brackets, the header rule
    dr.rounded_rectangle(BEZEL, 18, outline=LINE, width=2)
    for cx, cy, sx, sy in ((BEZEL[0], BEZEL[1], 1, 1), (BEZEL[2], BEZEL[1], -1, 1),
                           (BEZEL[0], BEZEL[3], 1, -1), (BEZEL[2], BEZEL[3], -1, -1)):
        x, y = cx + sx * 22, cy + sy * 22
        dr.line((x, y, x + sx * 36, y), fill=DIM, width=2)
        dr.line((x, y, x, y + sy * 36), fill=DIM, width=2)
    dr.line((128, HEADER_Y, 1792, HEADER_Y), fill=LINE, width=1)
    # the playback mark, a small triangle (the recording playing), and the signal bars, at the end of the
    # source row, clear of the date and clock slots
    tri = ((1712, 206), (1712, 228), (1731, 217))
    dg.polygon(tri, fill=AMBER)
    dr.polygon(tri, fill=AMBER)
    for k in range(5):
        h = 5 + k * 4
        on = k < 4 or rnd.random() > .5
        dr.rectangle((1748 + k * 9, 228 - h, 1753 + k * 9, 228), fill=AMBER if on else DIM)

    # the channel column
    on, dead = live(t, mode)
    level, who = envelope(t, lines_for(mode))
    for i, ch in enumerate(radio_log.SCREEN_CHANNELS):
        y = ROW0 + i * ROW_H + 14
        if ch in on:
            dg.ellipse((LAMP_X - 9, y - 9, LAMP_X + 9, y + 9), fill=AMBER)
            dr.ellipse((LAMP_X - 9, y - 9, LAMP_X + 9, y + 9), fill=AMBER)
            lv = level if ch == who else rnd.uniform(.2, .6)
            for k in range(10):
                h = 4 + 14 * max(0.0, min(1.0, lv * rnd.uniform(.7, 1.3) - k * .07))
                c = AMBER if k / 10 < lv + .05 else DIM
                dr.rectangle((METER_X + k * 7, y + 9 - h, METER_X + k * 7 + 4, y + 9), fill=c)
        else:
            dr.ellipse((LAMP_X - 9, y - 9, LAMP_X + 9, y + 9), outline=DEAD if ch in dead else DIM, width=2)
            for k in range(10):
                dr.rectangle((METER_X + k * 7, y + 7, METER_X + k * 7 + 4, y + 9), fill=LINE)
        dr.line((128, y + 26, 560, y + 26), fill=(20, 26, 31), width=1)

    # the scope: a graticule, and the last half second of the channel scrolling across it
    x0, y0, x1, y1 = SCOPE
    dr.rectangle(SCOPE, outline=LINE, width=1)
    for k in range(1, 12):
        x = x0 + (x1 - x0) * k / 12
        dr.line((x, y0, x, y1), fill=(20, 27, 32), width=1)
    for k in range(1, 6):
        y = y0 + (y1 - y0) * k / 6
        dr.line((x0, y, x1, y), fill=(20, 27, 32) if k != 3 else LINE, width=1)
    yc, amp = (y0 + y1) / 2, (y1 - y0) * .42
    pts, hot = [], 0.0
    lines = lines_for(mode)
    for k in range(0, x1 - x0, 3):
        ts = t - .5 + .5 * k / (x1 - x0)
        e, _ = envelope(ts, lines)
        n = random.Random(int(ts * 4000)).gauss(0, 1)
        v = e * amp * (.55 * n + .45 * math.sin(ts * 2 * math.pi * 180)) + (1 - e) * random.Random(k + int(t * 97)).gauss(0, 1.2)
        if mode == 'static' and t >= RECORDING_END_S:
            v += rnd.gauss(0, 1) * amp * .5 * min(1.0, (t - RECORDING_END_S) * 2)
        pts.append((x0 + k, yc + max(-amp, min(amp, v))))
        hot = max(hot, e)
    trace = AMBER if hot > .02 or mode == 'static' else DIM
    dr.line(pts, fill=trace, width=2)
    if hot > .02:
        dg.line(pts, fill=AMBER, width=4)

    # the playback bar over the whole recording
    pos = x0 + (x1 - x0) * max(0.0, min(1.0, t / radio_log.END_CARD_S))
    dr.line((x0, BAR_Y, x1, BAR_Y), fill=LINE, width=4)
    dr.line((x0, BAR_Y, pos, BAR_Y), fill=DIM, width=4)
    for k in range(11):
        x = x0 + (x1 - x0) * k / 10
        dr.line((x, BAR_Y + 8, x, BAR_Y + 14), fill=LINE, width=1)
    dg.rectangle((pos - 2, BAR_Y - 9, pos + 2, BAR_Y + 9), fill=(230, 232, 236))
    dr.rectangle((pos - 2, BAR_Y - 9, pos + 2, BAR_Y + 9), fill=(230, 232, 236))
    if mode == 'close' and t >= radio_log.END_CARD_S:     # the log has ended: the screen goes quiet
        dr.rectangle((x0 + 1, y0 + 1, x1 - 1, y1 - 1), fill=BG0)

    if words:
        draw_words(im, t, mode, on, dead)

    a = np.asarray(im, np.float32)
    a += .8 * np.asarray(glow.filter(ImageFilter.GaussianBlur(9)), np.float32)
    a += .5 * np.asarray(glow.filter(ImageFilter.GaussianBlur(30)), np.float32)
    a *= (1.0 - .07 * (np.arange(H) % 3 == 0))[:, None, None]                    # scanlines
    gy, gx = np.ogrid[:H, :W]
    r = np.hypot((gx - W / 2) / (W / 2), (gy - H / 2) / (H / 2))
    a *= (1.0 - .28 * np.clip(r - .45, 0, 1) ** 1.5)[..., None]                  # vignette
    a += np.random.default_rng(int(round(t * FPS)) + 101).normal(0, 3.0, (H, W, 1))  # grain
    if mode == 'open':
        a *= max(0.0, min(1.0, (t - FADE_IN[0]) / (FADE_IN[1] - FADE_IN[0])))
    out = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    if mode == 'static' and under is not None:
        out = break_up(t, under, out)
    return out


def break_up(t, picture, screen):
    """S11: the picture breaking into the screen's static from RECORDING_END_S: torn rows of the picture
    and static first, the screen showing through the noise and taking the frame by the shot's end."""
    import numpy as np
    from PIL import Image
    if t < RECORDING_END_S:
        return picture
    k = min(1.0, (t - RECORDING_END_S) / (126.0 - RECORDING_END_S))
    rng = np.random.default_rng(int(round(t * FPS)) + 7)
    p = np.asarray(picture.convert('RGB').resize((W, H)), np.float32)
    s = np.asarray(screen, np.float32)
    noise = rng.uniform(0, 255, (H // 4, W // 4, 1)).repeat(4, 0).repeat(4, 1)
    rows = rng.random(H)[:, None, None] < .15 + .6 * k                           # torn bands
    shift = (rng.integers(-60, 60, H) * (rows[:, 0, 0])).astype(int)
    torn = p[np.arange(H)[:, None], (np.arange(W)[None, :] - shift[:, None]) % W]
    pic = np.where(rows, torn * .6 + noise * .4, p)
    mix = np.clip(k * 1.4 - .2, 0, 1)
    out = pic * (1 - mix) + (s * (1 - .5 * (1 - k)) + noise * .5 * (1 - k)) * mix
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def draw_words(im, t, mode, on, dead):
    """The words the game draws, as LAYOUT places them: for judging the plate, never baked into it."""
    from PIL import ImageDraw, ImageFont
    dr = ImageDraw.Draw(im)

    def font(size, bold=False):
        for name in ('bahnschrift.ttf', 'arialbd.ttf' if bold else 'arial.ttf'):
            try:
                return ImageFont.truetype(name, int(size * 1.35))
            except OSError:
                pass
        return ImageFont.load_default()

    def put(slot, text, fill=WORD, bold=False):
        f = font(slot['size'], bold)
        w = dr.textlength(text, font=f)
        x = slot['x'] - (w if slot['align'] == 'right' else w / 2 if slot['align'] == 'center' else 0)
        dr.text((x, slot['y']), text, font=f, fill=fill)

    put(LAYOUT['place'], 'ASHARU', bold=True)
    put(LAYOUT['region'], 'Corvus  ·  low orbit', fill=(190, 196, 204))
    put(LAYOUT['date'], '14 Ruulin, c.203', fill=(190, 196, 204))
    clock = 6 * 3600 + 41 * 60 + 12 + t
    put(LAYOUT['clock'], '%02d:%02d:%05.2f' % (clock // 3600, clock % 3600 // 60, clock % 60), fill=(190, 196, 204))
    put(LAYOUT['source'], 'RECORDING  ·  CONVOY COMMON', fill=DIM)
    for slot in LAYOUT['channels']:
        ch = slot['text']
        put(slot, ch + (slot['dropped'] if ch in dead else ''), fill=AMBER if ch in on else DEAD if ch in dead else DIM)
    for _, who, t0, t1, line in lines_for(mode):
        if t0 <= t <= t1:
            put(LAYOUT['caption_who'], who, fill=AMBER, bold=True)
            put(LAYOUT['caption_line'], line)
    if mode == 'close' and t >= radio_log.END_CARD_S:
        put(LAYOUT['end_card'], 'END OF RECORDING', bold=True)


def main(argv):
    if argv[0] == '--layout':
        Path(argv[1]).write_text(json.dumps(dict(size=[W, H], slots=LAYOUT), indent=1, ensure_ascii=False), encoding='utf-8')
        return
    shot, out = argv[0], Path(argv[1])
    kf, t0, t1, mode = SHOTS[shot]
    words = '--words' in argv
    under = Path(argv[argv.index('--under') + 1]) if '--under' in argv else None
    a, b = round(t0 * FPS), round(t1 * FPS)
    if '--frames' in argv:
        a, b = (int(v) for v in argv[argv.index('--frames') + 1].split(':'))
    if shot == 's11':
        a = max(a, round(RECORDING_END_S * FPS))      # only the break-up; the picture before it is the render's
    pct = float(argv[argv.index('--percent') + 1]) if '--percent' in argv else 100.0
    out.mkdir(parents=True, exist_ok=True)
    from PIL import Image
    for n in range(a, b):
        name = f'{kf}_f{n:05d}.png'
        pic = None
        if under is not None:
            src = under / name
            if not src.exists():
                raise SystemExit(f'{src}: the S11 frame to break up is missing')
            pic = Image.open(src)
        im = frame(n / FPS, mode, words, pic)
        if pct != 100.0:
            im = im.resize((round(W * pct / 100), round(H * pct / 100)), Image.LANCZOS)
        im.save(out / name)
    print(f'recording_plate {shot}: frames {a}..{b - 1} -> {out}')


if __name__ == '__main__':
    main(sys.argv[1:])
