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
| A Cold Account | 0 | 13 |

Blind Watch adds a two-turn Mara/Castle after-action exchange about earned
initiative and preparing the company. Cold Account closes with six alternating
Castle/Mara turns: state power limits their protection without erasing their work.

Cold Account alternates Mara, Castle and Captain Irena Kord; its cast assignments
are retained in `cold-account-cast.json`. Four ordered opening turns establish
the patrol's requirement for authenticated proof. Actual company landing and
dispatch proximity provide two Castle cues. Archive capture cancels unspoken
approach content and begins the seven intervention beats. Kord's selected
veteran naval voice and portrait are shared throughout.
Six additional `debrief.cold_account.*` recordings alternate Castle and Mara
on successful results. They use the same cast and dialogue processing, but are
selected from the settled outcome rather than battle story events. Replay and
Stop remain presentation controls.

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

Thunder Run's seven `wayline_thunder_run_gunship_*` clips use the existing
Talon Two/Liam voice (`TX3LPaxmHKxFdv7VOQHJ`) and Castle command voice
(`cuwCdnt9CKAB9XrTA1j0`) with Eleven v4. Clean source takes and generation
receipts are retained here; the audio manifest applies AIR_COMMS to Talon Two's
ten Junction/Thunder Run transmissions, while other speakers keep COMMS.
Arrival, committed pass, actual missile launch, damage, loss, station
and return each have their own trigger. A loss supersedes obsolete pending
calls; the launch warning and survivor lines never imply a guaranteed crash.
These takes have technical encoding checks; delivery and battle-mix audition
remain part of the live play review.

Talon Two is the Wayline aviation callsign, distinct from infantry radio actor
Kestrel Two. The two roles currently share the Liam voice but use separate
recording banks and delivery direction. Stable gunship sound and event IDs do
not encode the callsign. Mission briefings and other speakers' references use
the same aviation name.

The pilot's flight-helmet portrait is `graphics/portraits/talon-two.png`.
AIR_COMMS narrows the voice to a 420–2500 Hz radio band, drives compression and
soft saturation harder, and filters the resulting high harmonics again. It
preserves source recordings, pitch and phrase timing; no paid synthesis is
needed to revise this treatment. Only generated Ogg files receive the filter.
