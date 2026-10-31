"""Offline checks: no credentials, network, ffmpeg or source writes."""
import unittest

from generate_wellhollow_audio import plan


def catalog(model, *lines):
    return {"modelId": model, "voiceSettings": {}, "lines": list(lines)}


def line(sound_id, voice, key, synthesis):
    return {"id": sound_id, "voiceId": voice, "textKey": key, "synthesisText": synthesis}


class PlanTest(unittest.TestCase):
    def test_every_catalog_is_planned_in_each_lines_own_voice(self):
        briefing = catalog("m1", line("brief", "orens", "kBrief", "[warm] Hello."))
        radio = catalog("m2", line("radio", "castle", "kRadio", "[grim, on the radio] Truck lost."))
        items = plan([briefing, radio], {"kBrief": "Hello.", "kRadio": "Truck lost."})
        self.assertEqual(["brief", "radio"], [i["id"] for i in items])
        self.assertEqual(["orens", "castle"], [i["voice"] for i in items])
        self.assertEqual("m2", items[1]["payload"]["model_id"])
        self.assertIn("/castle?", items[1]["url"])

    def test_a_stale_line_in_any_catalog_rejects_the_whole_batch(self):
        fresh = catalog("m", line("brief", "orens", "kBrief", "[warm] Hello."))
        stale = catalog("m", line("radio", "castle", "kRadio", "[grim] Old words."))
        with self.assertRaisesRegex(SystemExit, "Stale synthesis text for radio"):
            plan([fresh, stale], {"kBrief": "Hello.", "kRadio": "New words."})


if __name__ == "__main__":
    unittest.main()
