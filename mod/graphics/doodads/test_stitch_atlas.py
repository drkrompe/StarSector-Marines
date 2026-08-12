import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image, ImageDraw

from stitch_atlas import BuildOptions, build_atlas


class StitchAtlasTest(unittest.TestCase):

    def test_builds_deterministic_grid_and_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "sources"
            source.mkdir()
            self._cutout(source / "bravo.png", (20, 10), (255, 0, 0, 255))
            self._cutout(source / "alpha.png", (8, 24), (0, 255, 0, 255))
            (source / "_atlas.json").write_text(
                json.dumps(
                    {
                        "assets": {
                            "bravo.png": {
                                "id": "doodad.bravo-custom",
                                "cover": "heavy",
                                "order": 1,
                            }
                        }
                    }
                ),
                encoding="utf-8",
            )
            image_path = root / "atlas.png"
            manifest_path = root / "atlas.tileset.json"
            options = BuildOptions(
                input_dir=source,
                output_image=image_path,
                output_manifest=manifest_path,
                sheet_path="graphics/test/atlas.png",
                metadata_path=source / "_atlas.json",
                columns=2,
            )

            _, _, count = build_atlas(options)

            self.assertEqual(2, count)
            with Image.open(image_path) as atlas:
                self.assertEqual((64, 32), atlas.size)
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            self.assertEqual("doodad.bravo-custom", manifest["doodads"][0]["id"])
            self.assertEqual("heavy", manifest["doodads"][0]["cover"])
            self.assertEqual([0, 0], [manifest["doodads"][0]["col"], manifest["doodads"][0]["row"]])
            self.assertEqual("doodad.alpha", manifest["doodads"][1]["id"])
            self.assertEqual([1, 0], [manifest["doodads"][1]["col"], manifest["doodads"][1]["row"]])

    def test_rejects_fully_opaque_source_by_default(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "sources"
            source.mkdir()
            Image.new("RGB", (16, 16), (20, 30, 40)).save(source / "matte.png")
            options = BuildOptions(
                input_dir=source,
                output_image=root / "atlas.png",
                output_manifest=root / "atlas.json",
                sheet_path="graphics/test/atlas.png",
                metadata_path=source / "_atlas.json",
            )

            with self.assertRaisesRegex(ValueError, "fully opaque"):
                build_atlas(options)

    @staticmethod
    def _cutout(path: Path, size: tuple[int, int], color: tuple[int, int, int, int]):
        image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        x = (64 - size[0]) // 2
        y = (64 - size[1]) // 2
        ImageDraw.Draw(image).rectangle(
            (x, y, x + size[0] - 1, y + size[1] - 1),
            fill=color,
        )
        image.save(path)


if __name__ == "__main__":
    unittest.main()
