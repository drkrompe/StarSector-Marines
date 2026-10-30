"""Cut one short clip and one poster still per weapon from the weapon reels, for the kit ladder's Weapons tab.

    python tools/kits/cut_weapon_clips.py <weapon-reel/index.json> <out dir>

Reads the reel index the weapon reel suite writes beside its reels (weapon-reel-<category>.mp4) and, for
each clip, writes <out>/<shot>.mp4 (the clip after its title card: 960x540, H.264 crf 30, AAC 64 kbps,
faststart) and <out>/<shot>.jpg (a 480x270 poster from just after the first round). Existing outputs newer
than their reel are kept, so a re-run only cuts what changed. The outputs are rendered media: keep them
outside the repository. Upload the mp4s to the artifact's asset store and record shot -> url in
<out>/assets.json for build.py --clips (tools/kits/build.py docstring).
"""
import json
import subprocess
import sys
from pathlib import Path


def run(cmd):
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit("ffmpeg failed: %s\n%s" % (" ".join(cmd), result.stderr[-2000:]))


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    index = Path(sys.argv[1])
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    reels = json.loads(index.read_text(encoding="utf-8"))["reels"]
    total, count = 0, 0
    for reel in reels:
        source = index.parent / reel["file"]
        if not source.exists():
            raise SystemExit("reel %s is missing" % source)
        for clip in reel["clips"]:
            shot = clip["shot"]
            # The title card holds the first 1.5 s of each chapter; fireStart is where it ends.
            begin = clip.get("fireStart", clip["start"])
            length = clip["end"] - begin
            mp4, jpg = out / (shot + ".mp4"), out / (shot + ".jpg")
            if not (mp4.exists() and mp4.stat().st_mtime > source.stat().st_mtime):
                run(["ffmpeg", "-y", "-loglevel", "error", "-ss", "%.3f" % begin, "-i", str(source), "-t", "%.3f" % length,
                     "-vf", "scale=960:540:flags=lanczos", "-c:v", "libx264", "-preset", "slow", "-crf", "30",
                     "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "64k", "-ac", "2", "-movflags", "+faststart", str(mp4)])
            if not (jpg.exists() and jpg.stat().st_mtime > source.stat().st_mtime):
                still = min(clip["end"] - 0.1, clip["firstRound"] + 0.4)
                run(["ffmpeg", "-y", "-loglevel", "error", "-ss", "%.3f" % still, "-i", str(source), "-frames:v", "1",
                     "-vf", "scale=480:270:flags=lanczos", "-q:v", "6", str(jpg)])
            total += mp4.stat().st_size
            count += 1
            print("%-40s %4.1f s  %4d KB" % (shot, length, mp4.stat().st_size // 1024))
    print("%d clips, %.1f MB in %s" % (count, total / 1048576, out))


if __name__ == "__main__":
    main()
