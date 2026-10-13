"""Deterministic, original tender sound design; Python standard library only.

Run from any directory. Writes retained mono PCM masters beside this script.
These are designed machine effects, not recordings or generated voice.
"""
import math
import json
import random
import struct
import wave
from pathlib import Path

RATE = 44100
ROOT = Path(__file__).resolve().parent


def render(name, seconds, seed, voice):
    rng = random.Random(seed)
    phase = 0.0
    low = 0.0
    samples = []
    for i in range(round(seconds * RATE)):
        t = i / RATE
        u = t / seconds
        noise = rng.uniform(-1, 1)
        low += .12 * (noise - low)
        frequency, tone, hiss, pulse = voice(t, u)
        phase += 2 * math.pi * frequency / RATE
        motor = math.sin(phase) + .32 * math.sin(phase * 2) + .12 * math.sin(phase * 5)
        envelope = min(1, t / .008, (seconds - t) / .025)
        samples.append(envelope * (tone * motor + hiss * (noise - low) + pulse))
    peak = max(abs(x) for x in samples)
    scale = .72 / max(peak, .72)
    with wave.open(str(ROOT / (name + '.wav')), 'wb') as out:
        out.setparams((1, 2, RATE, len(samples), 'NONE', 'not compressed'))
        out.writeframes(b''.join(struct.pack('<h', round(x * scale * 32767)) for x in samples))


def launch(t, u):
    return 150 + 500 * u, .22 * math.sin(math.pi * u), .10 * math.sin(math.pi * u), .2 * math.exp(-t * 70) * math.sin(t * 1500)


def dock(t, u):
    latch = .33 * math.exp(-abs(t - .21) * 95) * (math.sin(t * 850) + .4 * math.sin(t * 2310))
    return 650 - 480 * u, .16 * (1 - u), .045 * (1 - u), latch


def weld(t, u):
    modulation = .45 + .55 * math.sin(t * 97) ** 2
    crackle = .17 * math.exp(-((t * 71) % 1) * 17) * math.sin(t * 15600)
    return 112, .085 * modulation, .20 * modulation, crackle


def hit(t, u):
    return 160 - 110 * u, .21 * math.exp(-t * 14), .25 * math.exp(-t * 19), .2 * math.sin(t * 9400) * math.exp(-t * 24)


def alert(t, u):
    return 520 if t < .15 else 340, .15 * math.sin(math.pi * ((t * 6) % 1)) ** 2, .025, 0


def complete(t, u):
    latch = .25 * math.exp(-t * 38) * math.sin(t * 1200)
    confirm = sum(.07 * math.exp(-max(0, t - delay) * 12) * math.sin(t * freq * 2 * math.pi)
                  for delay, freq in ((.13, 740), (.25, 1110)) if t >= delay)
    return 95, .05 * math.exp(-t * 14), .035 * math.exp(-t * 30), latch + confirm


def audition():
    names = ['launch', 'weld_1', 'weld_2', 'weld_3', 'complete', 'dock', 'hit', 'alert']
    frames = bytearray()
    markers = []
    for name in names:
        frames.extend(bytes(round(.65 * RATE) * 2))
        markers.append({'seconds': round(len(frames) / (2 * RATE), 3), 'effect': name})
        with wave.open(str(ROOT / ('marines_tender_' + name + '.wav')), 'rb') as source:
            frames.extend(source.readframes(source.getnframes()))
    frames.extend(bytes(round(.65 * RATE) * 2))
    with wave.open(str(ROOT / 'audition.wav'), 'wb') as out:
        out.setparams((1, 2, RATE, len(frames) // 2, 'NONE', 'not compressed'))
        out.writeframes(frames)
    (ROOT / 'audition.json').write_text(json.dumps(markers, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    render('marines_tender_launch', .48, 101, launch)
    render('marines_tender_dock', .37, 102, dock)
    for variant in range(3):
        render('marines_tender_weld_' + str(variant + 1), .24, 200 + variant, weld)
    render('marines_tender_hit', .24, 301, hit)
    render('marines_tender_alert', .34, 302, alert)
    render('marines_tender_complete', .52, 303, complete)
    audition()
