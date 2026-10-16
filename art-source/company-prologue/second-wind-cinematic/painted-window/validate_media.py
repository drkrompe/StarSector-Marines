"""Record full video decode and source-frame evidence for the motion review."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from fractions import Fraction
from PIL import Image, ImageChops, ImageStat

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--ffmpeg", default="ffmpeg")
parser.add_argument("--ffprobe", default="ffprobe")
args = parser.parse_args()
movie = HERE / "review/jangala-painted-window-test.mp4"
probe = json.loads(subprocess.check_output([
    args.ffprobe, "-v", "error", "-count_frames", "-show_streams",
    "-show_format", "-of", "json", str(movie)], text=True))
video = next(s for s in probe["streams"] if s["codec_type"] == "video")
assert (video["width"], video["height"]) == (1920, 1080)
assert Fraction(video["avg_frame_rate"]) == 24
assert int(video["nb_read_frames"]) == 96
assert abs(float(probe["format"]["duration"]) - 4) < .01
assert not any(s["codec_type"] == "audio" for s in probe["streams"])
subprocess.run([args.ffmpeg, "-v", "error", "-xerror", "-i", str(movie),
                "-f", "null", "-"], check=True)
hashes, changes, previous = [], [], None
for index in range(96):
    path = HERE / f"build/frames/frame_{index:04d}.png"
    with Image.open(path) as source:
        assert source.size == (1920, 1080)
        frame = source.convert("RGB")
    hashes.append(hashlib.sha256(frame.tobytes()).hexdigest())
    if previous is not None:
        changes.append(sum(ImageStat.Stat(ImageChops.difference(frame, previous)).mean)/3)
    previous = frame
assert len(set(hashes)) == 96, "Duplicated source frames"
assets = {}
for path in sorted((HERE / "assets").glob("*.png")):
    with Image.open(path) as source:
        assets[path.name] = {"size": list(source.size), "mode": source.mode,
                             "bytes": path.stat().st_size,
                             "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
report = {
    "passed": True, "movie": movie.relative_to(HERE).as_posix(),
    "full_decode": "FFmpeg -xerror completed with exit 0",
    "resolution": [1920,1080], "fps": 24, "decoded_frames": 96,
    "duration_seconds": 4, "audio": False, "codec": video["codec_name"],
    "pixel_format": video["pix_fmt"], "movie_bytes": movie.stat().st_size,
    "movie_sha256": hashlib.sha256(movie.read_bytes()).hexdigest(),
    "unique_source_frames": len(set(hashes)),
    "adjacent_mean_absolute_pixel_change_0_to_255": {
        "min": min(changes), "max": max(changes), "mean": sum(changes)/len(changes)},
    "assets": assets,
    "limits": "Decode, dimensions and uniqueness checks do not establish artistic acceptance."
}
(HERE / "review/media-validation.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
print(json.dumps({k:v for k,v in report.items() if k != "assets"},indent=2))
