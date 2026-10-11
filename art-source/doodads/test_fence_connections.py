"""Spatial contracts for the six shipped fence cells (no world or renderer needed)."""

import json
from pathlib import Path
import unittest

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
CONNECTIONS = {
    "corner-nw": "ES", "corner-ne": "SW", "corner-sw": "NE", "corner-se": "NW",
    "straight-h": "EW", "straight-v": "NS",
}


class FenceConnectionsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        manifest = json.loads((ROOT / "mod/data/tilesets/doodads.tileset.json").read_text(encoding="utf-8"))
        with Image.open(ROOT / "mod" / manifest["sheet"]) as source:
            atlas = source.convert("RGBA")
        cls.cells = {}
        for item in manifest["doodads"]:
            prefix = "doodad.industrial-fence-"
            if item["id"].startswith(prefix):
                x, y = item["col"] * 32, item["row"] * 32
                cls.cells[item["id"][len(prefix):]] = atlas.crop((x, y, x + 32, y + 32))

    def test_each_connected_edge_has_both_rails_at_the_shared_centerline(self):
        self.assertEqual(set(CONNECTIONS), set(self.cells))
        for name, cell in self.cells.items():
            for side in "NESW":
                with self.subTest(piece=name, side=side):
                    for depth in range(3):
                        def pixel(along):
                            return {"N": (along, depth), "E": (31 - depth, along),
                                    "S": (along, 31 - depth), "W": (depth, along)}[side]
                        solid = {i for i in range(32) if cell.getpixel(pixel(i))[3] > 128}
                        if side in CONNECTIONS[name]:
                            self.assertTrue(solid & {13, 14}, "First rail stops before the join")
                            self.assertTrue(solid & {17, 18}, "Second rail stops before the join")
                            self.assertTrue(solid <= set(range(12, 20)), "Fence is off-center or over-wide")
                        else:
                            self.assertFalse(solid, "Unconnected edge must remain open")

    def test_all_possible_join_pairs_overlap_on_both_rails(self):
        for name, cell in self.cells.items():
            for other_name, other in self.cells.items():
                for a, b in (("E", "W"), ("S", "N")):
                    if a not in CONNECTIONS[name] or b not in CONNECTIONS[other_name]:
                        continue
                    with self.subTest(first=name, second=other_name, side=a):
                        overlap = set()
                        for i in range(32):
                            p, q = ((31, i), (0, i)) if a == "E" else ((i, 31), (i, 0))
                            if cell.getpixel(p)[3] > 128 and other.getpixel(q)[3] > 128:
                                overlap.add(i)
                        self.assertTrue(overlap & {13, 14})
                        self.assertTrue(overlap & {17, 18})


if __name__ == "__main__":
    unittest.main()
