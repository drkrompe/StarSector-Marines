# Mara Venn narration

Voice: Mora - Gritty and Enigmatic (`YHcCpa6SBWnKDaCPZJQR`), Eleven v4.
The runtime catalog at `mod/data/marines/wayline_narration.json` owns each
subtitle, synthesis text, stable sound/event ID, duration and queue policy.
Each MP3 has a JSON generation receipt with input/audio hashes.

| Chapter | Briefing | Tactical beats |
| --- | ---: | ---: |
| Hold Until Relieved | 1 | 6 |
| Take Back the Depot | 1 | 6 |
| Open the Junction | 1 | 11 |
| Last Lift | 1 | 6 |
| Relay Nine | 1 | 17 |
| Thunder Run | 1 | 14 |

The opening chapters use their story mission IDs. Broken Supply Route job-zero
attempt IDs resolve to stable `story_supply_route_*` catalog keys. Recurring
jobs and branch-choice dossiers are excluded. Mara voices the operational
Rill briefing and radio calls; she does not impersonate Iona.

Intentionally unvoiced hooks:

- All hidden road arrivals, reserve commitments and defender fallback orders.
- Victory, defeat and retreat: outcome presentation owns these, and completion
  discards pending tactical instructions.
- Last Lift's `cohort_resolved` and `last_boarding`: resolution can include
  confirmed deaths, so it must use the frozen outcome rather than an
  unconditional “everyone is aboard” line.

Each beat is eligible once per attempt. Eligibility does not guarantee playback:
paused presentation waits, obsolete queued instructions are superseded, and
stale speech expires. Active mission sentences finish without preemption.

Authoring is explicit: `python tools/generate_wayline_narration.py` previews
missing/changed takes; `--generate` makes paid requests only for those takes.
Changes to an existing take require `--replace`. Normal builds only encode
local source files and reuse the audio cache. Keep credentials outside the repo.

`WaylineNarrationTest` checks recorded sources, audio hashes, receipts, manifest
entries, sound registrations, chapter routing and public-hook coverage.
File validation and headless tests do not replace listening in the battle mix.
