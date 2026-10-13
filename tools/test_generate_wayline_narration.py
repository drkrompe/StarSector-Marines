"""Offline checks: no credentials, network, ffmpeg or source writes."""
import unittest

from generate_wayline_narration import generation_plan


class GenerationPlanTest(unittest.TestCase):
    def catalog(self, *lines):
        return {"voiceId": "mara", "modelId": "eleven_v4", "voiceSettings": {}, "lines": list(lines)}

    def test_unrecorded_drafts_are_not_part_of_a_default_batch(self):
        self.assertEqual([], generation_plan(self.catalog({"id": "vale", "voiced": False}), "wayline"))

    def test_draft_cannot_silently_use_catalog_voice(self):
        for fields in ({}, {"voiceId": ""}):
            line = {"id": "vale", "voiced": False, "synthesisText": "Shore power is on.", **fields}
            with self.assertRaisesRegex(SystemExit, "No assigned voice"):
                generation_plan(self.catalog(line), "wayline", ["vale"])

    def test_explicit_castle_draft_can_be_prepared_without_enabling_runtime_speech(self):
        line = {"id": "castle", "voiced": False, "voiceId": "castle-voice", "synthesisText": "Company is at the yard."}
        plan = generation_plan(self.catalog(line), "wayline", ["castle"])
        self.assertEqual("castle-voice", plan[0][1])
        self.assertEqual("eleven_v4", plan[0][2]["model_id"])
        self.assertFalse(line["voiced"])

    def test_entire_batch_is_rejected_before_returning_any_requests(self):
        ready = {"id": "ready", "synthesisText": "Clear."}
        draft = {"id": "vale", "voiceId": "", "voiced": False, "synthesisText": "Hold."}
        with self.assertRaisesRegex(SystemExit, "No assigned voice"):
            generation_plan(self.catalog(ready, draft), "wayline", ["ready", "vale"])
        with self.assertRaisesRegex(SystemExit, "Unknown line"):
            generation_plan(self.catalog(ready), "wayline", ["missing"])
        with self.assertRaisesRegex(SystemExit, "No synthesis text"):
            generation_plan(self.catalog({"id": "missing_text"}), "wayline")


if __name__ == "__main__":
    unittest.main()
