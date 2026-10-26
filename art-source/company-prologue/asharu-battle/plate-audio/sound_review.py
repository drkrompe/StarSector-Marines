"""The recording plates' sound review page: every judged sound, a jump to just before each place it
plays, its options heard in context, and the owner's approve / deny and notes.

  python art-source/company-prologue/asharu-battle/plate-audio/sound_review.py --s0 S0.mp4 --s13 S13.mp4
  python .../sound_review.py --verdicts verdicts.json      (print the owner's answers, from an ArtifactData list)

Writes build/asharu-sound-review/: index.html (from sound-review.html), media/<shot>.mp4 (the plate with
the current mix, about 3.5 Mb/s so each stays under an Artifact file's 15 MB) and
media/<shot>-<family>-<option>.mp3 (the whole shot's mix with that one option swapped in; the page
plays it against the muted picture), and media/solo-<shot>-<family>[-<option>].mp3 (that family's
sounds alone, for the page's Play solo). Publish index.html with the Artifact tool, declaring `db` and
`user`, and pass media/ through `files`. Answers are `db` documents verdicts/<family>:
{options: {<key>: "approve" | "deny"}, note, at}.
"""
import argparse
import json
import subprocess
from pathlib import Path

import plate_audio as pa

HERE = Path(__file__).resolve().parent
OUT = pa.ROOT / "build" / "asharu-sound-review"
SHOT_START = {shot: pa.rp.SHOTS[shot][1] for shot in ("s0", "s13")}

# what each judged sound is, for the page; the families with OPTIONS are the ones to choose between
FAMILIES = {
    "crt_on": ("The set switching on", "The CRT powering up before the roll-lock: switch, degauss, tube."),
    "crt_off": ("The set switching off", "The tune-out at the end of S13."),
    "text": ("Text being typed", "Every character the terminal writes: the boot slate, each query, the sign-off."),
    "transition": ("View changes", "A view writing in, the zoom boxes between views, and the camera's push "
                                   "into the optic (S0) and pull back out (S13)."),
    "answer": ("Query answer tones", "One tone per answer, by its kind: ok rises, a note is one blip, "
                                     "a flag falls, a down-weighting steps down three times."),
    "flag": ("Contact and flag alert", "A blip when the unknown shows, three when it is flagged."),
    "stamp": ("Stamps", "NOT ESCALATED and ARCHIVED: a low thump with a tone."),
    "crest": ("The crest moving", "A hum swell as the crest settles into the header, and back."),
    "sweep": ("Sector sweep", "A double blip on each system the routine sweep visits."),
    "ping": ("Relay pings", "The Corvus relay and sensor array pinging, soft and echoed."),
    "route": ("Patrol route redrawn", "A patter of data as the route is drawn anew (S13)."),
    "radio": ("The relay channel", "Static under the tap, the squelch opening and closing on each line. "
                                   "The voices are not recorded yet."),
    "beds": ("The room and the set", "The operations room's air and the CRT's hum, under everything."),
}
ORDER = list(FAMILIES)


def mp3(x, path, solo=False):
    """x as an mp3; a solo (one family alone, mostly silence) as a small mono one."""
    wav = path.with_suffix(".wav")
    pa.encode(x, wav)
    rate = ["-ac", "1", "-b:a", "64k"] if solo else ["-b:a", "128k"]
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-c:a", "libmp3lame", *rate, str(path)], check=True)
    wav.unlink()


def build(videos):
    media = OUT / "media"
    media.mkdir(parents=True, exist_ok=True)
    cues = {}
    for shot, video in videos.items():
        x, marks = pa.build(shot)
        wav = media / (shot + ".wav")
        pa.encode(x, wav)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(video), "-i", str(wav), "-map", "0:v", "-map", "1:a",
                        "-c:v", "libx264", "-b:v", "3400k", "-maxrate", "3800k", "-bufsize", "7000k",
                        "-pix_fmt", "yuv420p", "-preset", "slow", "-c:a", "aac", "-b:a", "128k",
                        "-movflags", "+faststart", str(media / (shot + ".mp4"))], check=True)
        wav.unlink()
        for family, label, t in marks:
            cues.setdefault(family, []).append(dict(shot=shot, label=label, t=round(t - SHOT_START[shot], 2)))
        for family in sorted({f for f, _, _ in marks}):
            options = pa.OPTIONS.get(family)
            if not options:                             # one sound: its solo only
                y, _ = pa.build(shot, only=family)
                mp3(y, media / ("solo-%s-%s.mp3" % (shot, family)), solo=True)
                continue
            for k in options:
                y, _ = pa.build(shot, {family: k})
                mp3(y, media / ("%s-%s-%s.mp3" % (shot, family, k)))
                y, _ = pa.build(shot, {family: k}, only=family)
                mp3(y, media / ("solo-%s-%s-%s.mp3" % (shot, family, k)), solo=True)
                print("  %s %s %s" % (shot, family, k), flush=True)
    families = []
    for fam in ORDER:
        if fam not in cues:
            continue
        title, about = FAMILIES[fam]
        opts = [dict(key=k, label=v[0], current=pa.CHOICE[fam] == k) for k, v in pa.OPTIONS.get(fam, {}).items()]
        families.append(dict(id=fam, title=title, about=about, options=opts,
                             cues=sorted(cues[fam], key=lambda c: (c["shot"] != "s0", c["t"]))))
    page = (HERE / "sound-review.html").read_text(encoding="utf-8")
    page = page.replace("__FAMILIES__", json.dumps(families, ensure_ascii=False))
    (OUT / "index.html").write_text(page, encoding="utf-8")
    print("wrote", OUT / "index.html")


def verdicts(path):
    rows = json.loads(Path(path).read_text(encoding="utf-8"))
    for row in rows.get("documents", rows if isinstance(rows, list) else []):
        d = row.get("data", row)
        print(row.get("id", "?"), json.dumps(d, ensure_ascii=False))


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--s0")
    ap.add_argument("--s13")
    ap.add_argument("--verdicts")
    a = ap.parse_args()
    if a.verdicts:
        verdicts(a.verdicts)
    else:
        build({k: v for k, v in (("s0", a.s0), ("s13", a.s13)) if v})
