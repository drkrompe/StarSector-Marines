"""Offline footfall mix: retain API originals, normalize layers, write three mono masters.

Requires ffmpeg. No network calls. The walk preview is written under build/audio-preview/.
"""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import wave

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "asset-pipeline/src/tool/resources/audio/mech-footsteps"
RATE = 44100


def load_layer(name):
    raw = subprocess.check_output([
        "ffmpeg", "-v", "error", "-i", str(SOURCES / (name + ".mp3")),
        "-af", "highpass=f=50,lowpass=f=7000", "-ar", str(RATE), "-ac", "1", "-f", "f32le", "-"])
    samples = list(struct.unpack("<" + "f" * (len(raw) // 4), raw))
    peak = max(abs(x) for x in samples)
    if peak < 0.00001:
        raise SystemExit("Silent source: " + name)
    # Remove only leading near-silence; keep a 3ms margin before the onset.
    onset = next(i for i, x in enumerate(samples) if abs(x) >= peak * 0.025)
    samples = samples[max(0, onset - int(RATE * 0.003)):]
    return [x / peak for x in samples]


def save(path, samples):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(RATE)
        audio.writeframes(struct.pack("<" + "h" * len(samples),
                                      *(round(max(-1, min(1, x)) * 32767) for x in samples)))


def main():
    mechanism = load_layer("marines_mech_step_mechanism")
    variants = []
    receipts = {}
    for number in range(1, 4):
        name = "marines_mech_step_0" + str(number)
        impact = load_layer(name)
        # Quiet mechanism begins 25ms after ground contact; one runtime voice per step.
        offset = int(RATE * 0.025)
        mix = [0.0] * int(RATE * 0.5)
        for i, x in enumerate(impact[:len(mix)]):
            mix[i] += x
        for i, x in enumerate(mechanism[:len(mix) - offset]):
            mix[i + offset] += x * 0.22
        for i in range(len(mix)):
            fade_in = min(1, i / (RATE * 0.001))
            fade_out = min(1, (len(mix) - 1 - i) / (RATE * 0.05))
            mix[i] *= fade_in * fade_out
        gain = 10 ** (-6 / 20) / max(abs(x) for x in mix)
        mix = [x * gain for x in mix]
        target = SOURCES / (name + ".wav")
        save(target, mix)
        receipts[name] = hashlib.sha256(target.read_bytes()).hexdigest()
        variants.append(mix)
    (SOURCES / "preparation.json").write_text(json.dumps({
        "recipe": "prepare_mech_footsteps.py: mono 44.1kHz, 50Hz high-pass, 7kHz low-pass; onset trim; peak-normalized layers; mechanism at 22% +25ms; mix peak -6dBFS; 1ms/50ms fades",
        "sourceSha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                         for p in sorted(SOURCES.glob("*.mp3"))},
        "masterSha256": receipts
    }, indent=2) + "\n", encoding="utf-8")
    preview = [0.0] * (RATE * 8)
    for index in range(14):
        offset = int(RATE * (0.25 + index * 0.50))
        for i, x in enumerate(variants[index % 3]):
            preview[offset + i] += x
    target = ROOT / "build/audio-preview/mech-walk.wav"
    save(target, preview)
    print("Prepared three footfalls; preview:", target)


if __name__ == "__main__":
    main()
