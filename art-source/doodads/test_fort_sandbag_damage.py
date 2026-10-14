"""Checks for the placeholder fort sandbag damage states: ids, contract and the shape of each state."""
import json
from pathlib import Path
import unittest
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SHAPES = ("n", "e", "s", "w", "corner-ne", "corner-se", "corner-sw", "corner-nw")

def load():
    manifest = json.loads((ROOT / "mod/data/tilesets/fort-sandbag-damage.tileset.json").read_text(encoding="utf-8"))
    atlas = Image.open(ROOT / "mod" / manifest["sheet"]).convert("RGBA")
    size = manifest["cellPx"]
    cells = {d["id"]: atlas.crop((d["col"] * size, d["row"] * size, (d["col"] + 1) * size, (d["row"] + 1) * size))
             for d in manifest["doodads"]}
    return manifest, cells

def coverage(cell):
    return sum(1 for a in cell.getchannel("A").getdata() if a > 64)

class FortSandbagDamageArtTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest, cls.cells = load()
        intact = json.loads((ROOT / "mod/data/tilesets/fort-earthworks.tileset.json").read_text(encoding="utf-8"))
        cls.intact = Image.open(ROOT / "mod" / intact["sheet"]).convert("RGBA")
        cls.intactDefs = {d["id"]: d for d in intact["doodads"]}

    def test_contract_per_state(self):
        by_id = {d["id"]: d for d in self.manifest["doodads"]}
        self.assertEqual(16, len(by_id))
        for shape in SHAPES:
            damaged = by_id[f"doodad.fort-sandbag-damaged-{shape}"]
            destroyed = by_id[f"doodad.fort-sandbag-destroyed-{shape}"]
            self.assertEqual("heavy", damaged["cover"])
            self.assertEqual("parapet", damaged["fightingPosition"])
            self.assertEqual(self.intactDefs[f"doodad.fort-sandbag-{shape}"]["ballisticHalfHeight"],
                             damaged["ballisticHalfHeight"])
            self.assertEqual("none", destroyed["cover"])
            self.assertNotIn("fightingPosition", destroyed)

    def test_damaged_is_a_bitten_intact_and_destroyed_is_much_lower(self):
        size = self.manifest["cellPx"]
        for shape in SHAPES:
            d = self.intactDefs[f"doodad.fort-sandbag-{shape}"]
            whole = coverage(self.intact.crop((d["col"] * size, d["row"] * size, (d["col"] + 1) * size, (d["row"] + 1) * size)))
            damaged = coverage(self.cells[f"doodad.fort-sandbag-damaged-{shape}"])
            destroyed = coverage(self.cells[f"doodad.fort-sandbag-destroyed-{shape}"])
            self.assertNotEqual(damaged, whole, shape)  # bitten, with loose bags beside the breaks
            self.assertTrue(whole * 0.7 < damaged < whole * 1.15, shape)
            self.assertLess(destroyed, whole * 0.5, shape)
            self.assertGreater(destroyed, 0, shape)

    def test_descriptions_say_placeholder(self):
        for cell in self.manifest["cells"]:
            self.assertIn("PLACEHOLDER", cell["description"])

if __name__ == "__main__":
    unittest.main()
