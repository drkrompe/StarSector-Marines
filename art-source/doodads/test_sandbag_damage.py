"""Spatial checks for sandbag damage: retained edge alignment, quarter turns, and real breach voids."""
import json
from pathlib import Path
import unittest
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]

def cells(sheet, prefix):
    manifest = json.loads((ROOT / f"mod/data/tilesets/{sheet}.tileset.json").read_text(encoding="utf-8"))
    with Image.open(ROOT / "mod" / manifest["sheet"]) as source:
        atlas = source.convert("RGBA")
    return {d["id"].removeprefix(prefix): atlas.crop((d["col"] * 32, d["row"] * 32,
            d["col"] * 32 + 32, d["row"] * 32 + 32))
            for d in manifest["doodads"] if d["id"].startswith(prefix)}

class SandbagDamageArtTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.intact = cells("doodads", "doodad.sandbag-")
        cls.damage = cells("sandbag-damage", "doodad.sandbag-")

    def test_damaged_connections_stay_in_the_intact_edge_bands(self):
        self.assertEqual(16, len(self.damage))
        for shape in ("straight-n", "corner-nw"):
            intact = self.intact[shape]
            damaged = self.damage["damaged-" + shape]
            edges = (lambda i: (31, i), lambda i: (0, i)) if shape == "straight-n" else (lambda i: (31, i), lambda i: (i, 31))
            for edge in edges:
                old = {i for i in range(32) if intact.getpixel(edge(i))[3] > 128}
                new = {i for i in range(32) if damaged.getpixel(edge(i))[3] > 128}
                self.assertGreaterEqual(len(old & new), 6, shape)
                self.assertLessEqual(max(new), max(old) + 1, shape)

    def test_destroyed_shapes_leave_a_clear_breach_and_no_interior_carpet(self):
        straight = self.damage["destroyed-straight-n"]
        for y in range(32):
            for x in range(14, 18):
                self.assertLessEqual(straight.getpixel((x, y))[3], 16)
        corner = self.damage["destroyed-corner-nw"]
        for y in range(10):
            for x in range(10):
                self.assertLessEqual(corner.getpixel((x, y))[3], 16)
        for name, cell in self.damage.items():
            if not name.endswith(("straight-n", "corner-nw")): continue
            for y in range(20, 32):
                for x in range(20, 32):
                    self.assertLessEqual(cell.getpixel((x, y))[3], 16, name)

    def test_all_facings_are_exact_rotations(self):
        # The packer's independent Lanczos passes can round channels differently
        # by a few units after rotation. Exact orientation belongs to the retained sources.
        sources = ROOT / "art-source/doodads/sandbag-damage-sources"
        for state in ("damaged", "destroyed"):
            for shape, facings in (("straight", ("n", "e", "s", "w")), ("corner", ("nw", "ne", "se", "sw"))):
                with Image.open(sources / f"sandbag-{state}-{shape}-{facings[0]}.png") as image:
                    cell = image.convert("RGBA")
                for facing in facings:
                    with Image.open(sources / f"sandbag-{state}-{shape}-{facing}.png") as image:
                        self.assertEqual(cell.tobytes(), image.convert("RGBA").tobytes())
                    cell = cell.transpose(Image.Transpose.ROTATE_270)

if __name__ == "__main__":
    unittest.main()
