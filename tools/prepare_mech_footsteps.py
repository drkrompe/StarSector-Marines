"""Prepare separate impact and pneumatic one-shots; never bake a walking cycle.

Requires ffmpeg. No network calls. Isolated and event-sequence previews are written under build/audio-preview/.
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


def load_layer(name, kind):
    raw = subprocess.check_output([
        "ffmpeg", "-v", "error", "-i", str(SOURCES / (name + ".mp3")),
        "-af", ("highpass=f=28,lowpass=f=1100" if kind == "impact"
                else "highpass=f=140,lowpass=f=3200"), "-ar", str(RATE), "-ac", "1", "-f", "f32le", "-"])
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
    catalog = json.loads((SOURCES / "generation.json").read_text(encoding="utf-8"))
    masters = {}
    receipts = {}
    for clip in catalog["clips"]:
        name, kind = clip["id"], clip["kind"]
        samples = load_layer(name, kind)
        duration = 0.70 if kind == "impact" else 0.30
        samples = samples[:int(RATE * duration)]
        for i in range(len(samples)):
            fade_in = min(1, i / (RATE * 0.002))
            fade_out = min(1, (len(samples) - 1 - i) / (RATE * 0.06))
            samples[i] *= fade_in * fade_out
        gain = 10 ** ((-4.5 if kind == "impact" else -8) / 20) / max(abs(x) for x in samples)
        samples = [x * gain for x in samples]
        target = SOURCES / (name + ".wav")
        save(target, samples)
        receipts[name] = hashlib.sha256(target.read_bytes()).hexdigest()
        masters[name] = samples
    (SOURCES / "preparation.json").write_text(json.dumps({
        "recipe": "prepare_mech_footsteps.py: separate mono 44.1kHz masters; onset trim; impact 28-1100Hz, up to 700ms, peak -4.5dBFS; pneumatic 140-3200Hz, up to 300ms, peak -8dBFS; 2ms/60ms fades; NO layer mixing",
        "sourceSha256": {clip["id"] + ".mp3": hashlib.sha256(
            (SOURCES / (clip["id"] + ".mp3")).read_bytes()).hexdigest()
            for clip in catalog["clips"]},
        "masterSha256": receipts
    }, indent=2) + "\n", encoding="utf-8")

    def preview(name, events, seconds):
        mix = [0.0] * int(RATE * seconds)
        for time, sound, gain in events:
            offset = int(RATE * time)
            for i, value in enumerate(masters[sound]):
                if offset + i < len(mix):
                    mix[offset + i] += value * gain
        save(ROOT / "build/audio-preview" / (name + ".wav"), mix)

    impacts = [clip["id"] for clip in catalog["clips"] if clip["kind"] == "impact"]
    preview("impacts", [(0.2 + i * 1.2, sound, 1)
                        for i, sound in enumerate(impacts)], len(impacts) * 1.2 + 0.4)
    preview("pneumatics", [(0.2 + i * 0.8, "marines_mech_lift_0" + str(i + 1), 1)
                           for i in range(2)], 2)
    # Audition only. Runtime timing comes exclusively from actual per-foot gait transitions.
    events = []
    for i in range(10):
        start = 0.2 + i * 0.43
        events.append((start, "marines_mech_lift_0" + str(i % 2 + 1), 0.20))
        events.append((start + 0.34, impacts[i % len(impacts)], 0.65))
    preview("foot-events", events, 5.5)
    print("Prepared independent one-shots and previews under build/audio-preview/")


if __name__ == "__main__":
    main()
