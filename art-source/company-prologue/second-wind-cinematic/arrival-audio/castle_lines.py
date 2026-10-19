"""Castle's lines from the entry to C5 (owner's script, 2026-10-19; sfx-cues.md, "Castle's lines").

Jangala is a stop, not a home. After the hail (01-08, placed by build_hail_preview.timeline())
Castle speaks to the Commander, with one turn away to the ship's loudspeaker. Each line is a
catalog line in mod/data/marines/jangala_arrival_hail.json, encoded by
`:asset-pipeline:processAudio` from audio.json like the hail, so the composers place them by
these fixed shot times rather than off the hail clock:

  compose_entry, compose_c3, compose_c4 and the C5 export return captions(shot) and cues(shot).

Times are in the shot's own seconds; each line's span is its take (the catalog's durationSeconds).
C5's times are on C5's own clock (the joined C4-C5 camera adds 21.4 for its clock).
"""

import json
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[4] / "mod/data/marines/jangala_arrival_hail.json"

# shot -> [(t_in, t_out, catalog line id)]
LINES = {
    "entry": [(0.16, 4.0, "jangala_arrival_castle_planetside")],
    "c3": [(7.0, 12.2, "jangala_arrival_castle_reels")],
    "c4": [(1.0, 8.2, "jangala_arrival_castle_yard")],
    "c5": [(5.0, 11.95, "jangala_arrival_castle_all_hands"),     # the ship's loudspeaker, heard from the yard
           (19.6, 24.0, "jangala_arrival_castle_own_way")],
}


def captions(shot):
    """(t_in, t_out, speaker_id, catalog_line_id), over the whole take; the exporter keys the id."""
    return [(t_in, t_out, "castle", line_id) for t_in, t_out, line_id in LINES.get(shot, [])]


def cues(shot):
    """{t, sound, volume}: each take starts with its caption. The sound id is the catalog id."""
    return [{"t": t_in, "sound": line_id, "volume": 1.0} for t_in, _, line_id in LINES.get(shot, [])]


def review_pages(shot):
    """(t_in, t_out, speaker_id, text): one English page per take, for burnt-in review movies only.
    The shipped player pages the strings.json text itself (CaptionPager)."""
    text = {l["id"]: l["text"] for l in json.loads(CATALOG.read_text(encoding="utf-8"))["lines"]}
    return [(t_in, t_out, "castle", text[line_id]) for t_in, t_out, line_id in LINES.get(shot, [])]
