"""Music candidates for the arrival's closing, generated with ElevenLabs' music model.

    python art-source/company-prologue/second-wind-cinematic/arrival-audio/make_music.py plan
    python art-source/company-prologue/second-wind-cinematic/arrival-audio/make_music.py generate [--candidate ID ...]
    python art-source/company-prologue/second-wind-cinematic/arrival-audio/make_music.py score [--take ID ...]
    python art-source/company-prologue/second-wind-cinematic/arrival-audio/make_music.py march [--take ID ...]

A score (owner, 2026-10-19: music for the whole arrival, C1 to the title) is one generation
from a composition plan whose sections are timed to the shots, so one piece and one theme run
the whole 130.6 s. Its takes go to music-takes/score_<id>_tNN.mp3; sfx_mix.py --score lays one
across every shot.

The closing cue (owner, 2026-10-19): hopeful but fragile. Refugees landing somewhere with sky
after a failing station: worn and industrial, a beginning, not a victory. No vocals. It comes in
under Castle's last line, swells as the camera rises (C5 18-26 s), resolves under the title (C5 24
s on) and fades to black with the yard by C5 33. Each candidate is 18 s laid from C5 15.5, so its
swell sits on the rise and its resolve on the title; sfx_mix.py --music lays one into c4c5.

Each take is saved to music-takes/<id>_tNN.mp3 with a JSON sidecar holding its prompt, request,
hash and measured loudness. Requires ffmpeg/ffprobe on PATH and ELEVENLABS_API_KEY in the
environment (never printed).
"""

import argparse
import hashlib
import json
from pathlib import Path
import urllib.error
import urllib.request

from make_sfx import credential, levels, probe

HERE = Path(__file__).resolve().parent
TAKES = HERE / "music-takes"
MODEL = "music_v1"
SECONDS = 18

SHAPE = ("18 seconds long. Starts almost silent and sparse for the first 3 seconds, swells gently but "
         "clearly from 3 to 8 seconds, reaches a warm open major chord at about 8 seconds and lets it "
         "ring out and thin away to silence by the end. Hopeful but fragile: refugees from a failing "
         "station landing somewhere with open sky, worn and humble, a beginning rather than a victory. "
         "Instrumental only, no vocals, no choir.")

CANDIDATES = {
    "pad_piano": "Ambient cinematic closing cue: a warm, slightly worn analog synth pad with soft felt "
                 "piano notes, slow and unhurried, around 60 BPM, a suspended uncertain colour that opens "
                 "into major. No drums. " + SHAPE,
    "strings_horn": "Cinematic closing cue: low strings, cellos and violas in long hushed bowed notes, with a "
                    "distant muted French horn rising far away during the swell. Intimate, restrained, no "
                    "percussion, no big orchestral hit. " + SHAPE,
    "guitar_ambient": "Sparse ambient closing cue: slow fingerpicked clean electric guitar with long reverb and "
                      "a faint tape-worn texture over an airy shimmering ambient bed, a few notes left hanging "
                      "in space. No drums. " + SHAPE,
    "industrial_pulse": "Industrial ambient closing cue: a soft muted pulse of metallic percussion like distant "
                        "machinery, a low synth bass drone and a slow rising pad above it; at the resolve the "
                        "pulse drops away and leaves only the warm chord. Worn, mechanical, human. " + SHAPE,
}


# The score's sections, on the arrival clock: C1 37.7, C2 15, entry 7, C3 16, C4 21.4, C5 33.5.
# Its shape: the hope is held back for the title (c5-v2-closing.md); until then it stays small
# and leaves room for the hail and Castle.
SCORE_SECTIONS = [
    ("Holding", 10000, ["near silence", "a soft cold low drone", "one or two sparse felt piano notes, far apart",
                        "vast empty space"]),
    ("Under the hail", 27700, ["very sparse and quiet, leaving room for radio voices", "low drone continues",
                               "a simple four-note piano figure stated once, thin and hesitant",
                               "a slow low swell under a passing ship around 0 to 6 seconds, then back down"]),
    ("Scrutiny", 15000, ["a muted metallic pulse enters, like distant machinery or a ship's reactor, slow and steady",
                         "tense but not alarmed, no melody", "low strings hold one dark note",
                         "the pulse stops dead at about 9.6 seconds leaving only the drone"]),
    ("Breathe", 7000, ["an exhale: the music almost drops out", "only a low rising swell of sustained low tones",
                       "no percussion, no melody"]),
    ("First open sky", 16000, ["the emotional centre, quiet and warm", "out of silence the four-note piano figure returns alone",
                               "a warm pad opens underneath and the harmony turns gently to major for the first time",
                               "fragile and unhurried, never loud"]),
    ("Greywater", 21400, ["the slow pulse returns, softer and warmer, work rather than threat",
                          "piano and pad continue quietly under speech",
                          "thins out to almost nothing over the last 6 seconds"]),
    ("We're down", 20000, ["very sparse: a few piano notes and a held pad", "patient, tired, relieved",
                           "stays low and out of the way"]),
    ("Rise and title", 13500, ["low strings and pad swell gently from the start, building for about 4 seconds",
                               "the four-note figure played fully once with strings",
                               "resolves to a warm open major chord at about 5 seconds, which rings and thins away",
                               "fades to silence by the end"]),
]
SCORE_TAKES = {
    "piano_pad": ["hopeful but fragile cinematic ambient score", "warm, slightly worn analog synth pad",
                  "soft felt piano carries the theme", "low strings only for weight",
                  "slow, around 60 BPM, D major with suspended uncertain colour", "worn, industrial, human, humble"],
    "strings": ["hopeful but fragile cinematic ambient score", "hushed low string ensemble carries the theme",
                "cellos and violas in long bowed notes", "a soft piano and a distant muted horn only at the title",
                "slow, around 60 BPM, D major with suspended uncertain colour", "worn, industrial, human, humble"],
}
SCORE_NEGATIVE = ["vocals", "choir", "lyrics", "epic trailer drums", "brass fanfare", "triumphant",
                  "electronic dance beat", "fast tempo"]


# The march score (owner, 2026-10-23: "start this with the quiet sombre, but ... build toward
# starship troopers esque toward the end"): one piece for the whole arrival, C1 to the black, so
# the build is never broken. Sombre under the hails, building from the de-orbit burn, the marching
# theme in the open sky, the drums on the descent, the peak at the title and a resolve into the
# black. The sections are on the arrival clock (C5 is 40 s): 137.125 s.
MARCH_SECTIONS = [
    ("Cold approach", 12300, ["near silence, sombre and cold", "a low string drone, very quiet",
                              "one distant solemn horn note, far away", "no percussion, vast empty space"]),
    ("Under the hails", 25400, ["quiet and sombre, leaving room for radio voices",
                                "low strings hold slow dark minor chords", "a hushed solo horn hints a four-note theme once",
                                "a faint soft timpani roll far off near the end", "no snare, no melody over the voices"]),
    ("Bridge", 15000, ["still sombre but tense", "a soft muffled snare drum pattern enters very quietly, like a distant drill",
                       "low brass sustains underneath", "slowly gathering, restrained, in a low register"]),
    ("Through the fire", 7000, ["a steady rising crescendo", "timpani and snare roll build together",
                                "low brass swell climbs", "cuts off with one big low hit at about 5.5 seconds"]),
    ("Open sky", 16000, ["the marching theme is stated for the first time by unison French horns, noble and broad",
                         "strings underneath, a light steady snare march pulse at a moderate march tempo",
                         "moves from minor toward major", "mid volume, room for a voice from 7 to 12 seconds"]),
    ("Descent", 16400, ["a driving military snare ostinato and field drums", "low brass and strings carry the march under a voice for the first 8 seconds",
                        "then a steady, building crescendo: trumpets join, the theme climbs",
                        "momentum and resolve, heroic"]),
    ("Touchdown", 5000, ["a big full brass statement and cymbal crash at the start", "drums keep driving"]),
    ("The march", 19600, ["full heroic martial march: brass carries the rousing marching theme over driving snare and military percussion",
                          "trumpets and horns in harmony, strings in fast ostinato", "stirring and proud",
                          "slightly held back from 5 to 12 seconds under a voice, then pushing on"]),
    ("Peak and title", 9400, ["the climax: the whole orchestra, a big heroic brass statement of the marching theme",
                              "crashing cymbals, timpani and snare", "lands on a huge bright major chord at about 4.5 seconds"]),
    ("Resolve", 11025, ["the final major chord held broad and proud by the brass, then a timpani roll",
                        "a grand closing cadence that decays and fades to complete silence by 9.5 seconds"]),
]
MARCH_TAKES = {
    "march": ["heroic martial orchestral film score, a classic late-90s science-fiction military epic",
              "big brass and French horns carry a rousing marching theme", "driving snare drum and military field percussion",
              "full symphony orchestra, D minor resolving to D major", "patriotic, stirring, cinematic"],
    "anthem": ["epic martial orchestral film score", "sombre beginning building to a heroic military anthem",
               "heavy low brass, trombones and tubas, bold trumpet fanfares at the climax",
               "snare drums, timpani and big orchestral percussion", "C minor resolving to C major, broad and noble"],
    "horns": ["cinematic military orchestral score, sombre then heroic", "noble French horn theme that grows into a full brass march",
              "string ostinatos, crisp marching snare and bass drum", "classic Hollywood orchestral sound, no synths",
              "B-flat major march with minor-key opening"],
}
MARCH_NEGATIVE = ["vocals", "choir", "lyrics", "electronic", "synth", "rock drums", "electric guitar", "dance beat"]


def march_plan(take):
    return {"positive_global_styles": MARCH_TAKES[take], "negative_global_styles": MARCH_NEGATIVE,
            "sections": [{"section_name": name, "positive_local_styles": styles,
                          "negative_local_styles": [], "duration_ms": ms, "lines": []}
                         for name, ms, styles in MARCH_SECTIONS]}


# The ending (owner, 2026-10-19: strings up, an emotional build to the title, then a fade back into
# the game): C5 from the yard to black, 33.5 s, laid over the strings score from C5 0 (arrival 97.1).
ENDING_SECTIONS = [
    ("Gathering", 20000, ["hushed low strings, slow and patient, under speech",
                          "a gradual, steadily rising build over the whole section: more players join, the line climbs",
                          "tension resolving into hope, an emotional crescendo still held back"]),
    ("Title", 6000, ["the emotional peak: the full string section swells and lands on a warm open major chord",
                     "broad, heartfelt, not triumphant"]),
    # C5 is 40 s (owner: linger on the title): the peak still lands on the title's arrival at
    # 25-26 s, the music carries the hold, and resolves into the black at 38-39.5.
    ("Hold", 11000, ["the warm major harmony is held and carried, gently moving, full but calm",
                     "the piano melody returns softly over sustained strings, hopeful, unhurried"]),
    ("Into the black", 3000, ["a final soft resolution that decays quickly to complete silence"]),
]
ENDING_TAKES = {
    "strings": ["cinematic string ensemble score", "cellos, violas and violins in long bowed notes",
                "slow, around 60 BPM, D major", "emotional, hopeful, fragile, worn and human"],
    "strings_piano": ["cinematic string ensemble score with a soft felt piano melody on top",
                      "cellos, violas and violins in long bowed notes", "a distant muted horn joins at the peak",
                      "slow, around 60 BPM, D major", "emotional, hopeful, fragile, worn and human"],
}


def ending_plan(take):
    return {"positive_global_styles": ENDING_TAKES[take], "negative_global_styles": SCORE_NEGATIVE,
            "sections": [{"section_name": name, "positive_local_styles": styles,
                          "negative_local_styles": [], "duration_ms": ms, "lines": []}
                         for name, ms, styles in ENDING_SECTIONS]}


def score_plan(take):
    return {"positive_global_styles": SCORE_TAKES[take], "negative_global_styles": SCORE_NEGATIVE,
            "sections": [{"section_name": name, "positive_local_styles": styles,
                          "negative_local_styles": [], "duration_ms": ms, "lines": []}
                         for name, ms, styles in SCORE_SECTIONS]}


def takes(candidate):
    return sorted(TAKES.glob(candidate + "_t[0-9][0-9].mp3"))


def generate(candidates, score=False, plan=None, prefix="score_"):
    key = credential()
    TAKES.mkdir(parents=True, exist_ok=True)
    for candidate in candidates:
        if score:
            payload = {"composition_plan": (plan or score_plan)(candidate), "model_id": MODEL,
                       "respect_sections_durations": True}
            candidate = prefix + candidate
        else:
            payload = {"prompt": CANDIDATES[candidate], "music_length_ms": SECONDS * 1000,
                       "model_id": MODEL, "force_instrumental": True}
        audio = TAKES / ("%s_t%02d.mp3" % (candidate, len(takes(candidate)) + 1))
        request = urllib.request.Request(
            "https://api.elevenlabs.io/v1/music?output_format=mp3_44100_128",
            data=json.dumps(payload).encode(), headers={"xi-api-key": key, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                data = response.read()
                meta = {"requestId": response.headers.get("request-id"),
                        "characterCost": response.headers.get("character-cost")}
        except urllib.error.HTTPError as error:
            detail = error.read()[:400].decode("utf-8", "replace")
            raise SystemExit("Generation failed: HTTP %d %s; no retry made." % (error.code, detail)) from None
        except (OSError, urllib.error.URLError):
            raise SystemExit("Generation connection failed; no retry made.") from None
        audio.write_bytes(data)
        peak, lufs = levels(audio)
        meta.update(modelId=MODEL, prompt=payload.get("prompt"),
                    request={k: v for k, v in payload.items() if k not in ("model_id", "prompt")},
                    audioSha256=hashlib.sha256(data).hexdigest(), durationSeconds=probe(audio),
                    peakDb=peak, integratedLufs=lufs)
        audio.with_suffix(".json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
        print("Saved %s %5.2fs peak %5.1f dB  %s LUFS  cost %s" % (
            audio.name, meta["durationSeconds"], peak, lufs, meta["characterCost"]), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("stage", choices=("plan", "generate", "score", "ending", "march"))
    parser.add_argument("--candidate", action="append", choices=list(CANDIDATES))
    parser.add_argument("--take", action="append", choices=sorted(set(SCORE_TAKES) | set(ENDING_TAKES) | set(MARCH_TAKES)))
    args = parser.parse_args()
    if args.stage == "ending":
        generate(args.take or list(ENDING_TAKES), score=True, plan=ending_plan, prefix="ending_")
        return
    if args.stage == "march":
        generate(args.take or list(MARCH_TAKES), score=True, plan=march_plan, prefix="rescore_")
        return
    if args.stage == "score":
        generate(args.take or list(SCORE_TAKES), score=True)
        return
    chosen = args.candidate or list(CANDIDATES)
    if args.stage == "plan":
        for candidate in chosen:
            print("%s (%d s, %d takes so far)\n  %s\n" % (candidate, SECONDS, len(takes(candidate)),
                                                         CANDIDATES[candidate]))
    else:
        generate(chosen)


if __name__ == "__main__":
    main()
