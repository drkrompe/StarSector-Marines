"""Deterministic, original swarm-whine sound design for derelict flocks (`derelict-drone-flocks.md`, slice 7).

    python generate.py

Writes a seamless mono loop per candidate beside this script, marines_flock_whine_<voice>.wav: fourteen small lift
fans heard as one body, each its own tone, drifting in pitch as it trims its thrust, over the air the fans move. The
game raises the loop's pitch with the flock's speed (`FlockAudio`), so these are the flock at rest. Designed machine
effects built from oscillators and noise with numpy; not recordings and not generated voice.

  hornet   high fan whine, the fourteen tones beating against each other: an insect swarm made of machines
  turbine  a lower ducted-fan hum under a thin blade whistle: steadier, more an engine than a swarm
  buzz     open rotors: a rough buzz chopped at the blade rate, angrier and closer to a multirotor
"""
from pathlib import Path
import wave

import numpy as np

RATE = 44100
SECONDS = 4.0
FADE = 0.5
MEMBERS = 14
HERE = Path(__file__).resolve().parent


def noise_band(rng, n, low, high):
    """White noise kept to [low, high] Hz."""
    spectrum = np.fft.rfft(rng.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / RATE)
    spectrum[(freqs < low) | (freqs > high)] = 0
    out = np.fft.irfft(spectrum, n)
    return out / np.max(np.abs(out))


def drift(rng, t, depth):
    """A slow wander of a fan's pitch, a share of it, from two incommensurate sines."""
    a, b = rng.uniform(.2, .9, 2)
    p, q = rng.uniform(0, 2 * np.pi, 2)
    return 1 + depth * (.6 * np.sin(2 * np.pi * a * t + p) + .4 * np.sin(2 * np.pi * b * t + q))


def fans(rng, t, base, spread, depth, partials, chop=None):
    """MEMBERS fan voices: each a tone near base Hz with harmonics, drifting; chop adds blade-rate pulsing."""
    out = np.zeros_like(t)
    for _ in range(MEMBERS):
        f = base * rng.uniform(1 - spread, 1 + spread) * drift(rng, t, depth)
        phase = 2 * np.pi * np.cumsum(f) / RATE
        voice = sum(level * np.sin(k * phase) for k, level in partials)
        if chop is not None:
            rate = chop * rng.uniform(.9, 1.1)
            voice *= .55 + .45 * np.abs(np.sin(np.pi * rate * t + rng.uniform(0, np.pi)))
        out += voice * rng.uniform(.6, 1.0)
    return out / MEMBERS


def hornet(rng, t):
    tone = fans(rng, t, 470, .07, .015, [(1, 1), (2, .35), (3, .12)])
    air = noise_band(rng, len(t), 1800, 6000)
    return tone + .18 * air


def turbine(rng, t):
    hum = fans(rng, t, 185, .05, .01, [(1, 1), (2, .5), (3, .2)])
    whistle = fans(rng, t, 1150, .04, .008, [(1, 1)])
    air = noise_band(rng, len(t), 300, 3500)
    return hum + .35 * whistle + .25 * air


def buzz(rng, t):
    rotor = fans(rng, t, 105, .08, .02, [(1, 1), (2, .7), (3, .5), (4, .35), (5, .25), (6, .18)], chop=38)
    air = noise_band(rng, len(t), 150, 2500)
    return rotor + .22 * air


VOICES = {"hornet": (hornet, 11), "turbine": (turbine, 12), "buzz": (buzz, 13)}


def seamless(signal):
    """A loop of SECONDS from SECONDS + FADE: the tail crossfaded into the head, so the join is unheard."""
    n, f = round(SECONDS * RATE), round(FADE * RATE)
    head, tail = signal[:f], signal[n:n + f]
    ramp = np.linspace(0, 1, f)
    loop = signal[:n].copy()
    loop[:f] = head * ramp + tail * (1 - ramp)
    return loop


def write(name, signal):
    # Every voice at one level (about -20 LUFS), so the candidates are compared on their sound, not their loudness.
    signal = signal / np.sqrt(np.mean(signal ** 2)) * .1
    signal = np.clip(signal, -.98, .98)
    pcm = np.round(signal * 32767).astype("<i2")
    with wave.open(str(HERE / f"{name}.wav"), "wb") as out:
        out.setparams((1, 2, RATE, len(pcm), "NONE", "not compressed"))
        out.writeframes(pcm.tobytes())


def main():
    t = np.arange(round((SECONDS + FADE) * RATE)) / RATE
    for name, (voice, seed) in VOICES.items():
        write(f"marines_flock_whine_{name}", seamless(voice(np.random.default_rng(seed), t)))
        print(name)


if __name__ == "__main__":
    main()
