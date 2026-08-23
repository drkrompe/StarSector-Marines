from __future__ import annotations

import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image


SOURCE_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SOURCE_DIR))

from pack_texture_atlas import AtlasPackError, import_tileable, pack_manifest


class TextureAtlasPackerTest(unittest.TestCase):

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _write_manifest(self, value: dict) -> Path:
        path = self.root / "atlas.json"
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    @staticmethod
    def _pixels(image: Image.Image) -> list[tuple[int, int, int, int]]:
        return [tuple(pixel) for pixel in np.asarray(image).reshape(-1, 4)]

    def test_auto_strip_targets_receive_wrapped_guards(self) -> None:
        base = Image.new("RGBA", (12, 6), (0, 0, 0, 0))
        base.paste((20, 30, 40, 255), (0, 1, 4, 5))
        base.paste((50, 60, 70, 255), (8, 1, 12, 5))
        base.save(self.root / "base.png")

        material = Image.new("RGBA", (2, 2))
        material.putdata([
            (255, 0, 0, 255), (0, 255, 0, 255),
            (0, 0, 255, 255), (255, 255, 0, 255),
        ])
        material.save(self.root / "material.png")

        manifest = self._write_manifest({
            "version": 1,
            "atlases": [{
                "id": "strip",
                "base": "base.png",
                "output": "packed.png",
                "layout": {"mode": "auto-strip", "alphaThreshold": 16, "minGap": 4},
                "materials": [{
                    "id": "ground",
                    "source": "material.png",
                    "guardPx": 1,
                    "requireOpaque": True,
                    "targets": [{"frame": 0}, {"frame": 1}],
                }],
            }],
        })

        prepared = pack_manifest(manifest)
        self.assertEqual(2, prepared[0].placements)
        with Image.open(self.root / "packed.png") as packed:
            expected = [
                (255, 255, 0, 255), (0, 0, 255, 255), (255, 255, 0, 255), (0, 0, 255, 255),
                (0, 255, 0, 255), (255, 0, 0, 255), (0, 255, 0, 255), (255, 0, 0, 255),
                (255, 255, 0, 255), (0, 0, 255, 255), (255, 255, 0, 255), (0, 0, 255, 255),
                (0, 255, 0, 255), (255, 0, 0, 255), (0, 255, 0, 255), (255, 0, 0, 255),
            ]
            self.assertEqual(expected, self._pixels(packed.crop((0, 1, 4, 5))))
            self.assertEqual(
                self._pixels(packed.crop((0, 1, 4, 5))),
                self._pixels(packed.crop((8, 1, 12, 5))),
            )
            self.assertEqual((0, 0, 0, 0), packed.getpixel((6, 3)))

    def test_grid_cell_target_preserves_unlisted_pixels(self) -> None:
        Image.new("RGBA", (8, 4), (9, 8, 7, 255)).save(self.root / "base.png")
        Image.new("RGBA", (2, 2), (80, 90, 100, 255)).save(self.root / "material.png")
        manifest = self._write_manifest({
            "version": 1,
            "atlases": [{
                "id": "grid",
                "base": "base.png",
                "output": "packed.png",
                "layout": {"mode": "grid", "cellPx": 4},
                "materials": [{
                    "id": "sand",
                    "source": "material.png",
                    "guardPx": 1,
                    "targets": [{"cell": [1, 0]}],
                }],
            }],
        })

        pack_manifest(manifest)
        with Image.open(self.root / "packed.png") as packed:
            self.assertEqual((9, 8, 7, 255), packed.getpixel((1, 1)))
            self.assertEqual((80, 90, 100, 255), packed.getpixel((6, 2)))

    def test_check_only_validates_without_writing(self) -> None:
        Image.new("RGBA", (4, 4), (1, 2, 3, 255)).save(self.root / "base.png")
        Image.new("RGBA", (2, 2), (4, 5, 6, 255)).save(self.root / "material.png")
        manifest = self._write_manifest({
            "version": 1,
            "atlases": [{
                "id": "check",
                "base": "base.png",
                "output": "packed.png",
                "layout": {"mode": "grid", "cellPx": 4},
                "materials": [{
                    "id": "tile",
                    "source": "material.png",
                    "guardPx": 1,
                    "targets": [{"cell": [0, 0]}],
                }],
            }],
        })

        pack_manifest(manifest, check_only=True)
        self.assertFalse((self.root / "packed.png").exists())

    def test_overlapping_targets_fail_before_output_is_written(self) -> None:
        Image.new("RGBA", (6, 4), (1, 2, 3, 255)).save(self.root / "base.png")
        Image.new("RGBA", (2, 2), (4, 5, 6, 255)).save(self.root / "material.png")
        manifest = self._write_manifest({
            "version": 1,
            "atlases": [{
                "id": "overlap",
                "base": "base.png",
                "output": "packed.png",
                "layout": {"mode": "grid", "cellPx": 2},
                "materials": [{
                    "id": "tile",
                    "source": "material.png",
                    "targets": [{"rect": [0, 0, 2, 2]}, {"rect": [1, 0, 2, 2]}],
                }],
            }],
        })

        with self.assertRaisesRegex(AtlasPackError, "overlaps"):
            pack_manifest(manifest)
        self.assertFalse((self.root / "packed.png").exists())

    def test_auto_strip_expected_frame_count_is_enforced(self) -> None:
        Image.new("RGBA", (4, 4), (1, 2, 3, 255)).save(self.root / "base.png")
        Image.new("RGBA", (4, 4), (4, 5, 6, 255)).save(self.root / "material.png")
        manifest = self._write_manifest({
            "version": 1,
            "atlases": [{
                "id": "count",
                "base": "base.png",
                "output": "packed.png",
                "layout": {
                    "mode": "auto-strip",
                    "alphaThreshold": 16,
                    "minGap": 4,
                    "expectedFrames": 2,
                },
                "materials": [{
                    "id": "tile",
                    "source": "material.png",
                    "targets": [{"frame": 0}],
                }],
            }],
        })

        with self.assertRaisesRegex(AtlasPackError, "detected 1 frames; expected 2"):
            pack_manifest(manifest)
        self.assertFalse((self.root / "packed.png").exists())

    @unittest.skipUnless(shutil.which("ffmpeg"), "ffmpeg is not installed")
    def test_ffmpeg_importer_outputs_requested_rgba_size(self) -> None:
        source = self.root / "source.png"
        output = self.root / "imported.png"
        Image.new("RGB", (8, 8), (30, 60, 90)).save(source)

        import_tileable(source, output, (3, 5))

        with Image.open(output) as imported:
            self.assertEqual((3, 5), imported.size)
            self.assertEqual("RGBA", imported.mode)


if __name__ == "__main__":
    unittest.main()
