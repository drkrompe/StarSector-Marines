"""Editable fixed-view 2.5D compositor. Requires Pillow, numpy, and FFmpeg.

Run with --preview for review stills, or without it to stream the complete shot
to H.264. Every path in the config is relative to the config's own directory.
No source art is rewritten. No geometry, rotation, or perspective is synthesized.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw


def lerp(a, b, u):
    return np.asarray(a) * (1 - u) + np.asarray(b) * u


def load_layer(path, transparent=False):
    if not path.is_file():
        raise ValueError(f"Missing input: {path}. Place the supplied artwork there or edit inputs in composition.json.")
    with Image.open(path) as source:
        if transparent and "A" not in source.getbands() and "transparency" not in source.info:
            raise ValueError(f"{path.name} needs a real alpha channel; an opaque checkerboard is not transparency.")
        layer = source.convert("RGBA")
    if transparent:
        alpha = np.asarray(layer.getchannel("A"))
        if not np.any(alpha == 0) or not np.any(alpha > 0):
            raise ValueError(f"{path.name} must contain both transparent and visible pixels.")
    return layer


def cover(layer, size, overscan):
    factor = max(size[0] / layer.width, size[1] / layer.height) * overscan
    return layer.resize((round(layer.width * factor), round(layer.height * factor)), Image.Resampling.LANCZOS)


def place(canvas, layer, center):
    left, top = center[0] - layer.width / 2, center[1] - layer.height / 2
    ix, iy = math.floor(left), math.floor(top)
    # Fractional translation uses premultiplied alpha so transparent edge RGB
    # cannot bleed into the vessel or create a dark fringe during interpolation.
    shifted = layer.convert("RGBa").transform(
        (layer.width + 2, layer.height + 2), Image.Transform.AFFINE,
        (1, 0, -(left - ix), 0, 1, -(top - iy)), Image.Resampling.BICUBIC,
    ).convert("RGBA")
    canvas.alpha_composite(shifted, (ix, iy))


class Compositor:
    def __init__(self, config, base):
        self.config = config
        self.size = (int(config["width"]), int(config["height"]))
        self.fps = float(config["fps"])
        self.duration = float(config["duration_seconds"])
        if min(self.size) < 2 or any(n % 2 for n in self.size) or self.fps <= 0 or self.duration <= 0:
            raise ValueError("Output dimensions must be positive even integers; fps and duration must be positive.")
        self.count = round(self.fps * self.duration)
        if self.count < 2:
            raise ValueError("The composition needs at least two frames.")
        self.layers = {name: load_layer(base / path, name != "background") for name, path in config["inputs"].items()}
        for required in ("background", "ship", "foreground"):
            if required not in self.layers:
                raise ValueError(f"Missing required input key: {required}")
        self.ship_bounds = self.layers["ship"].getchannel("A").getbbox()
        self.layers["ship"] = self.layers["ship"].crop(self.ship_bounds)
        for name in ("background", "foreground"):
            self.layers[name] = cover(self.layers[name], self.size, float(config[name]["overscan"]))
        opacity = float(config["foreground"].get("opacity", 1))
        self.layers["foreground"].putalpha(self.layers["foreground"].getchannel("A").point(lambda x: round(x * opacity)))
        yy, xx = np.mgrid[-1:1:complex(self.size[1]), -1:1:complex(self.size[0])]
        self.vignette = (1 - config["grade"]["vignette"] * np.clip((xx * xx + yy * yy) / 1.6, 0, 1))[..., None]

    def frame(self, seconds):
        # Gentle continuous motion, with enough residual velocity to avoid a parked endpoint.
        u = min(1.0, max(0.0, seconds / ((self.count - 1) / self.fps)))
        movement = 0.75 * u + 0.25 * (u * u * (3 - 2 * u))
        canvas = Image.new("RGBA", self.size, (0, 0, 0, 255))
        midpoint = np.asarray(self.size) / 2
        bg = self.config["background"]
        place(canvas, self.layers["background"], midpoint + lerp(bg["offset_start"], bg["offset_end"], u))
        ship_config = self.config["ship"]
        ship = self.layers["ship"]
        width = float(lerp(ship_config["width_start"], ship_config["width_end"], movement))
        ship = ship.resize((round(width), round(width * ship.height / ship.width)), Image.Resampling.LANCZOS)
        # A broad weak dock-light sweep changes RGB only; the original alpha is retained.
        rgba = np.array(ship)
        x = np.linspace(0, 1, ship.width)[None, :, None]
        ship_grade = ship_config.get("grade", {})
        rgb = rgba[..., :3].astype(np.float32)
        luminance = np.sum(rgb * np.array([0.2126, 0.7152, 0.0722]), axis=2, keepdims=True)
        rgb = luminance + (rgb - luminance) * float(ship_grade.get("saturation", 1))
        knots = ship_grade.get("luminance_knots", [0, 255])
        gains = ship_grade.get("gain_knots", [1, 1])
        if len(knots) != len(gains) or len(knots) < 2 or any(b <= a for a, b in zip(knots, knots[1:])):
            raise ValueError("ship.grade luminance_knots must increase and match gain_knots in length.")
        rgb *= np.interp(luminance, knots, gains)
        # Dock-facing bounce is multiplicative: the crater blacks stay black.
        bounce_width = max(0.001, float(ship_grade.get("dock_bounce_width", 0.32)))
        bounce = np.exp(-(x / bounce_width) ** 2)
        rgb *= 1 + bounce * float(ship_grade.get("dock_bounce_strength", 0)) * np.asarray(ship_grade.get("dock_bounce_rgb", [1, 0.6, 0.24]))
        sweep = np.exp(-((x - (0.9 - 0.65 * movement)) / 0.42) ** 2)
        strength = float(ship_config.get("light_strength", 0))
        gain = 1 + strength * sweep * (0.7 + 0.3 * math.sin(seconds * 0.5))
        rgba[..., :3] = np.clip(rgb * gain, 0, 255).astype(np.uint8)
        place(canvas, Image.fromarray(rgba), lerp(ship_config["center_start"], ship_config["center_end"], movement))
        fg = self.config["foreground"]
        place(canvas, self.layers["foreground"], midpoint + lerp(fg["offset_start"], fg["offset_end"], u))
        pixels = np.array(canvas.convert("RGB"), dtype=np.float32)
        grade = self.config["grade"]
        luminance = np.sum(pixels * np.array([0.2126, 0.7152, 0.0722]), axis=2, keepdims=True)
        pixels = (luminance + (pixels - luminance) * grade["saturation"]) * grade["exposure"]
        pixels *= np.asarray(grade["rgb_gain"], dtype=np.float32) * self.vignette
        if grade.get("grain", 0):
            rng = np.random.default_rng(8400 + round(seconds * self.fps))
            pixels += rng.normal(0, grade["grain"], (self.size[1], self.size[0], 1)).astype(np.float32)
        aspect = float(grade.get("letterbox_aspect", 0))
        if aspect > self.size[0] / self.size[1]:
            bar = round((self.size[1] - self.size[0] / aspect) / 2)
            pixels[:bar] = 0
            pixels[-bar:] = 0
        return Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8))


def preview(comp, output):
    times = comp.config.get("preview_times", [0, 3, 6, 9, 11.9])
    # Frames are always composed at full resolution before contact-sheet reduction.
    sheet = Image.new("RGB", (1920, 1080), "#101419")
    draw = ImageDraw.Draw(sheet)
    for i, seconds in enumerate(times):
        frame = comp.frame(float(seconds))
        frame.save(output / f"preview-{i:02d}-{seconds:g}s.png")
        thumb = frame.resize((640, 360), Image.Resampling.LANCZOS)
        x, y = (i % 3) * 640, (i // 3) * 390 + 120
        sheet.paste(thumb, (x, y))
        draw.text((x + 14, y + 12), f"{seconds:g} seconds", fill="white", stroke_width=2, stroke_fill="black")
    for name, seconds in (("first", 0), ("middle", comp.duration / 2), ("last", (comp.count - 1) / comp.fps)):
        comp.frame(seconds).save(output / f"{name}.png")
    sheet.save(output / "contact-sheet.png")


def render(comp, output, ffmpeg):
    if not ffmpeg:
        raise ValueError("FFmpeg was not found. Add it to PATH or supply --ffmpeg C:/path/to/ffmpeg.exe.")
    destination = output / "arrival-cinematic.mp4"
    temporary = output / "arrival-cinematic.partial.mp4"
    encode = comp.config["encoding"]
    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "rawvideo", "-pixel_format", "rgb24", "-video_size", f"{comp.size[0]}x{comp.size[1]}", "-framerate", str(comp.fps), "-i", "pipe:0", "-an", "-c:v", "libx264", "-crf", str(encode["crf"]), "-preset", encode["preset"], "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(temporary)]
    log_path = output / "ffmpeg.log"
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, stdin=subprocess.PIPE, stderr=log)
        try:
            for index in range(comp.count):
                process.stdin.write(comp.frame(index / comp.fps).tobytes())
                if index % int(comp.fps) == 0:
                    print(f"Rendered {index}/{comp.count} frames", flush=True)
            process.stdin.close()
            if process.wait() != 0:
                raise ValueError(f"FFmpeg failed; see {log_path}")
        except BaseException:
            if process.stdin and not process.stdin.closed:
                process.stdin.close()
            if process.poll() is None:
                process.terminate()
            process.wait()
            raise
    temporary.replace(destination)
    print(f"Saved {destination}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=Path(__file__).with_name("composition.json"))
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--ffmpeg", default=shutil.which("ffmpeg"))
    args = parser.parse_args()
    try:
        config_path = args.config.resolve()
        config = json.loads(config_path.read_text(encoding="utf-8-sig"))
        comp = Compositor(config, config_path.parent)
        output = config_path.parent / config.get("output_directory", "build")
        output.mkdir(parents=True, exist_ok=True)
        metadata = {"composition": config, "frame_count": comp.count, "actual_duration_seconds": comp.count / comp.fps, "ship_alpha_bounds": comp.ship_bounds, "source_dimensions": {name: list(Image.open(config_path.parent / path).size) for name, path in config["inputs"].items()}, "config": config_path.name, "audio": None}
        (output / "render-metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
        preview(comp, output) if args.preview else render(comp, output, args.ffmpeg)
        print(f"Outputs: {output}")
    except (ValueError, OSError, KeyError, TypeError) as error:
        print(f"Cinematic build failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
