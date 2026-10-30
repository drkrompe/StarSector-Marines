"""Cut the weapon-reel snapshot suite's clips into labelled reels with sound, one per category.

python tools/weapon_reel.py [--only KEYS] [--skip-render] [--out DIR] [--card-seconds 1.5]

1. Runs `gradlew createSnapshots -Psnapshot=weapon-reel -Dsnapshot.lookSound=true` (skipped with
   --skip-render), which plays every weapon at a target through the look preset and writes
   build/snapshots/weapon-reel/look/<category>-<key>-reel.mp4 (1920x1080, 30 fps, its soundtrack mixed
   offline from the battle's own sound starts, guns only) and build/snapshots/weapon-reel/manifest.json.
   Set JAVA_HOME first, as for any Gradle run (CLAUDE.md).
2. For each clip in the manifest: a title card (name, id, role, firing mode, shooter, sound ids), then
   the clip with its name in the corner.
3. Joins them into <out>/weapon-reel-<category>.mp4 (H.264 + AAC 48 kHz stereo, chapters per weapon)
   and writes <out>/index.md and <out>/index.json with each weapon's start time in its reel.

--only passes -Dsnapshot.weaponReel.only (keys, shot names or categories, comma separated) and cuts
only what the manifest lists. Output defaults to build/weapon-reel. Needs ffmpeg and ffprobe on PATH
(or $FFMPEG). Rendered output: never commit it.
"""
import argparse
import json
import os
import platform
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SUITE_DIR = ROOT / "build" / "snapshots" / "weapon-reel"
FPS = 30
CATEGORY_TITLES = {"mech": "Mech weapons", "vehicle": "Vehicle guns", "infantry": "Infantry weapons"}
FONTS = [
    "C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf",
    "/System/Library/Fonts/Supplemental/Arial.ttf", "/Library/Fonts/Arial.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]
BOLD_FONTS = [
    "C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf", "/Library/Fonts/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def ffmpeg() -> str:
    exe = os.environ.get("FFMPEG") or shutil.which("ffmpeg")
    if not exe:
        sys.exit("ffmpeg is not on PATH (nor named by FFMPEG)")
    return exe


def ffprobe() -> str:
    exe = os.environ.get("FFMPEG")
    if exe:
        probe = Path(exe).with_name(Path(exe).name.replace("ffmpeg", "ffprobe"))
        if probe.exists():
            return str(probe)
    exe = shutil.which("ffprobe")
    if not exe:
        sys.exit("ffprobe is not on PATH")
    return exe


def first_font(candidates) -> Path:
    for f in candidates:
        if Path(f).exists():
            return Path(f)
    sys.exit("no font found for the cards; tried " + ", ".join(candidates))


def render(only: str | None) -> None:
    gradlew = ROOT / ("gradlew.bat" if platform.system() == "Windows" else "gradlew")
    cmd = [str(gradlew), "createSnapshots", "-Psnapshot=weapon-reel", "-Dsnapshot.lookSound=true"]
    if only:
        cmd.append("-Dsnapshot.weaponReel.only=" + only)
    if platform.system() != "Windows":
        cmd.insert(0, "sh")
    print("running:", " ".join(cmd), flush=True)
    subprocess.run(cmd, cwd=ROOT, check=True)


def has_audio(video: Path) -> bool:
    out = subprocess.run([ffprobe(), "-v", "error", "-select_streams", "a", "-show_entries", "stream=index",
                          "-of", "csv=p=0", str(video)], capture_output=True, text=True, check=True).stdout
    return bool(out.strip())


def duration(video: Path) -> float:
    out = subprocess.run([ffprobe(), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                          str(video)], capture_output=True, text=True, check=True).stdout
    return float(out.strip())


def card_lines(clip: dict, n: int, total: int) -> list[tuple[str, int, str]]:
    """(text, size, font) top to bottom."""
    sounds = []
    if clip.get("fireSound"):
        sounds.append("fire " + clip["fireSound"])
    if clip.get("loopSound"):
        sounds.append("loop " + clip["loopSound"])
    if clip.get("impactSound"):
        sounds.append("impact " + clip["impactSound"])
    lines = [
        (f"{CATEGORY_TITLES.get(clip['category'], clip['category'])}   {n} / {total}", 34, "regular"),
        (clip["name"] or clip["weaponId"], 84, "bold"),
        (clip["weaponId"], 36, "regular"),
    ]
    detail = [s for s in (clip.get("role"), clip.get("mode")) if s]
    if detail:
        lines.append(("  -  ".join(detail), 40, "regular"))
    lines.append(("fired by " + clip["shooter"], 32, "regular"))
    lines.append(("sound: " + ("   ".join(sounds) if sounds else "none authored"), 32, "regular"))
    return lines


def write_text(work: Path, name: str, text: str) -> str:
    (work / name).write_text(text, encoding="utf-8")
    return name


def segment(work: Path, clip: dict, video: Path, n: int, total: int, card_seconds: float, out: Path) -> float:
    """Title card then the clip, one H.264/AAC segment. Returns the card's length."""
    lines = card_lines(clip, n, total)
    y = 300
    card_filters = []
    for i, (text, size, font) in enumerate(lines):
        name = write_text(work, f"card{n}_{i}.txt", text)
        fontfile = "bold.ttf" if font == "bold" else "regular.ttf"
        colour = "white" if i == 1 else "0xC8D0D8"
        card_filters.append(f"drawtext=fontfile={fontfile}:textfile={name}:fontsize={size}:fontcolor={colour}"
                            f":x=160:y={y}")
        y += int(size * 1.6) + (20 if i == 1 else 0)
    caption = write_text(work, f"caption{n}.txt", f"{clip['name'] or clip['weaponId']}   {clip['weaponId']}")
    audio = has_audio(video)
    inputs = ["-f", "lavfi", "-i", f"color=c=0x15191E:s=1920x1080:r={FPS}:d={card_seconds}",
              "-f", "lavfi", "-t", str(card_seconds), "-i", "anullsrc=r=48000:cl=stereo",
              "-i", str(video)]
    if not audio:
        inputs += ["-f", "lavfi", "-t", str(duration(video)), "-i", "anullsrc=r=48000:cl=stereo"]
    clip_audio = "[2:a]" if audio else "[3:a]"
    graph = (
        f"[0:v]{','.join(card_filters)},format=yuv420p,setsar=1[cv];"
        f"[2:v]fps={FPS},scale=1920:1080,drawbox=x=0:y=0:w=iw:h=64:color=black@0.45:t=fill,"
        f"drawtext=fontfile=regular.ttf:textfile={caption}:fontsize=30:fontcolor=white:x=24:y=16,"
        f"format=yuv420p,setsar=1[pv];"
        f"[1:a]aformat=sample_rates=48000:channel_layouts=stereo[ca];"
        f"{clip_audio}aresample=48000,aformat=sample_rates=48000:channel_layouts=stereo[pa];"
        f"[cv][ca][pv][pa]concat=n=2:v=1:a=1[v][a]"
    )
    cmd = [ffmpeg(), "-y", "-loglevel", "error", *inputs, "-filter_complex", graph, "-map", "[v]", "-map", "[a]",
           "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-r", str(FPS),
           "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2", str(out)]
    subprocess.run(cmd, cwd=work, check=True)
    return card_seconds


def chapters_metadata(entries: list[dict]) -> str:
    lines = [";FFMETADATA1"]
    for e in entries:
        lines += ["[CHAPTER]", "TIMEBASE=1/1000", f"START={int(e['start'] * 1000)}", f"END={int(e['end'] * 1000)}",
                  "title=" + (e["name"] or e["weaponId"]).replace("=", "\\=").replace(";", "\\;")]
    return "\n".join(lines) + "\n"


def stamp(seconds: float) -> str:
    m, s = divmod(seconds, 60)
    return f"{int(m)}:{s:04.1f}"


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--only", help="keys, shot names or categories to render and cut, comma separated")
    p.add_argument("--skip-render", action="store_true", help="cut the clips already under build/snapshots")
    p.add_argument("--out", default=str(ROOT / "build" / "weapon-reel"), help="where the reels go")
    p.add_argument("--card-seconds", type=float, default=1.5)
    args = p.parse_args()

    if not args.skip_render:
        render(args.only)
    manifest = json.loads((SUITE_DIR / "manifest.json").read_text(encoding="utf-8"))
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    by_category: dict[str, list[dict]] = {}
    for clip in manifest["clips"]:
        by_category.setdefault(clip["category"], []).append(clip)

    index = {"reels": [], "leftOut": manifest.get("leftOut", [])}
    with tempfile.TemporaryDirectory(prefix="weapon-reel-") as tmp:
        work = Path(tmp)
        shutil.copy(first_font(FONTS), work / "regular.ttf")
        shutil.copy(first_font(BOLD_FONTS), work / "bold.ttf")
        for category in ("mech", "vehicle", "infantry"):
            clips = by_category.get(category, [])
            if not clips:
                continue
            segments = []
            entries = []
            t = 0.0
            for n, clip in enumerate(clips, 1):
                video = SUITE_DIR / clip["video"]
                if not video.exists():
                    print(f"  missing {video}; left out", flush=True)
                    continue
                seg = work / f"{category}-{n:02d}.mp4"
                print(f"  {category} {n}/{len(clips)} {clip['shot']}", flush=True)
                card = segment(work, clip, video, n, len(clips), args.card_seconds, seg)
                length = duration(seg)
                entry = dict(clip)
                entry.update(start=round(t, 2), fireStart=round(t + card, 2), end=round(t + length, 2))
                if clip.get("firstShotFrame", -1) >= 0:
                    entry["firstRound"] = round(t + card + clip["firstShotFrame"] / FPS, 2)
                entries.append(entry)
                segments.append(seg)
                t += length
            listing = work / f"{category}.txt"
            listing.write_text("".join(f"file '{s.name}'\n" for s in segments), encoding="utf-8")
            meta = work / f"{category}.meta"
            meta.write_text(chapters_metadata(entries), encoding="utf-8")
            reel = out / f"weapon-reel-{category}.mp4"
            subprocess.run([ffmpeg(), "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", listing.name,
                            "-i", meta.name, "-map_metadata", "1", "-map_chapters", "1", "-c", "copy",
                            "-movflags", "+faststart", str(reel)], cwd=work, check=True)
            size = reel.stat().st_size / 1e6
            print(f"wrote {reel} ({stamp(t)}, {size:.1f} MB)", flush=True)
            index["reels"].append({"category": category, "file": reel.name, "seconds": round(t, 2),
                                   "megabytes": round(size, 1), "clips": entries})

    (out / "index.json").write_text(json.dumps(index, indent=2), encoding="utf-8")
    md = ["# Weapon reel", ""]
    for reel in index["reels"]:
        md += [f"## {CATEGORY_TITLES[reel['category']]}: `{reel['file']}` ({stamp(reel['seconds'])},"
               f" {reel['megabytes']} MB)", "",
               "| Starts | First round | Weapon | Id | Mode | Fired by | Fire sound | Impact sound | Loop | Rounds |",
               "|---|---|---|---|---|---|---|---|---|---|"]
        for e in reel["clips"]:
            md.append("| {} | {} | {} | `{}` | {} | {} | {} | {} | {} | {} |".format(
                stamp(e["start"]), stamp(e["firstRound"]) if "firstRound" in e else "-", e["name"], e["weaponId"],
                e.get("mode", ""), e["shooter"], e.get("fireSound") or "none", e.get("impactSound") or "none",
                e.get("loopSound") or "", e.get("rounds", "")))
        md.append("")
    if index["leftOut"]:
        md += ["## Left out", ""] + [f"- {s}" for s in index["leftOut"]] + [""]
    (out / "index.md").write_text("\n".join(md), encoding="utf-8")
    print(f"wrote {out / 'index.md'}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
