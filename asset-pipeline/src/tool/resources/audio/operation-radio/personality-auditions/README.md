# Infantry personality auditions

Selected cast: **Jackal A, Ox B and Rivet B**. The selected previews are retained.
All three are saved as reusable provider voices; their receipts and
`ensemble/cast.json` record the permanent voice IDs.
Their production-model combat reports and assignment exchanges are registered in
the live bank; these original previews retain the casting reference. Performance
and live mix acceptance still require listening. Warden, Relay and all unselected alternatives were rejected
by the user. Their audio masters have been removed; request and rejection metadata
remain for provenance, and Git retains the original audition set.

| Role | Intended distinction |
| --- | --- |
| Jackal A | Irish man; dry conversational humour that stops when people are at risk. |
| Ox B | Older New Zealand man; warm bass and stubborn reassurance. |
| Rivet B | Older Lancashire woman; smoky, practical irritation and blunt encouragement. |

`catalog.json` retains the descriptions, clean transcripts, delivery directions,
seeds and complete design requests. Each role's receipt records returned A/B/C
candidate IDs, source hashes, measured durations and explicit selection/rejection
status. A/B/C refers to response order, not a quality ranking.

The design endpoint uses `eleven_ttv_v3`, its expressive Voice Design model.
These previews are not a test of the production `eleven_v4` synthesis model.
The live bank uses the saved voices with `eleven_v4`, one retained clean source
per independently triggered turn, with radio processing applied by the audio build.

The sample sentences are a compact performance sequence with several different
situations. They do not describe one actual battle or add event triggers. The
runtime evidence rules in `battle-radio-nouns.md` still govern any later use.

## Authoring

From the repository root:

```powershell
python tools/design_radio_personalities.py
python tools/design_radio_personalities.py --generate
python tools/design_radio_personalities.py --save-selected
python tools/design_radio_personalities.py --render build/personality-auditions
```

Default execution validates retained candidates without loading credentials or
making requests. Rejected roles and candidates are excluded from validation,
generation and new renders; they cannot be silently regenerated. A missing
approved master or receipt fails validation. `--generate` sends one paid request per missing role, with no
automatic retries. `--role warden` restricts validation/generation to that role;
rendering includes every non-rejected role. `--save-selected` saves only explicit
catalog/receipt selections, retains the returned voice ID and updates the ensemble
cast. Repeating it reuses saved IDs. An unresolved save leaves a separate pending
marker and requires checking the provider account before another attempt.
An unresolved design request leaves a
pending marker; a received raw response is retained until all previews and the
receipt are safely written. Inspect these before retrying. Changed inputs need
a separately retained audition set, not replacement of the original candidates.

Rendering is offline. Individual clean files preserve provider bytes. Radio
previews use the existing comms recipe and the ensemble's 0.8 playback gain;
clean and radio reels play remaining candidates in original order with two seconds between candidates. The
output `review.json` records candidate start times, peaks, RMS and clipped-sample
counts. Decoding and level checks do not establish accent, acting, pronunciation
or intelligibility in the live battle mix; those remain listening decisions.

Guidance checked 2026-10-12:

- https://elevenlabs.io/docs/eleven-creative/voices/voice-design
- https://elevenlabs.io/docs/api-reference/text-to-voice/design
- https://elevenlabs.io/docs/overview/capabilities/text-to-speech/best-practices
