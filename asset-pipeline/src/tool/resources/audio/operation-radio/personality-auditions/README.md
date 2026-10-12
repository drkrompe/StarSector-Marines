# Infantry personality auditions

Five proposed ensemble personalities, with three Voice Design candidates each.
These are casting previews, not selected runtime actors. No candidate has been
saved to a permanent provider voice slot or registered in the game's audio bank.

| Role | Intended distinction |
| --- | --- |
| Warden | Mature South African woman; low, precise composure under pressure. |
| Jackal | Irish man; dry conversational humour that stops when people are at risk. |
| Relay | Young North American woman; meticulous reports fraying under pressure. |
| Ox | Older New Zealand man; warm bass and stubborn reassurance. |
| Rivet | Older Lancashire woman; smoky, practical irritation and blunt encouragement. |

`catalog.json` retains the descriptions, clean transcripts, delivery directions,
seeds and complete design requests. Each role's receipt records returned A/B/C
candidate IDs, source hashes and measured durations. Keep those mappings when
selecting a candidate; A/B/C refers to response order, not a quality ranking.

The design endpoint uses `eleven_ttv_v3`, its expressive Voice Design model.
These previews are not a test of the production `eleven_v4` synthesis model.
After casting, save the chosen generated voice and audition it on the production
model before generating its independently triggered combat clips.

The sample sentences are a compact performance sequence with several different
situations. They do not describe one actual battle or add event triggers. The
runtime evidence rules in `battle-radio-nouns.md` still govern any later use.

## Authoring

From the repository root:

```powershell
python tools/design_radio_personalities.py
python tools/design_radio_personalities.py --generate
python tools/design_radio_personalities.py --render build/personality-auditions
```

Default execution validates retained candidates without loading credentials or
making requests. `--generate` sends one paid request per missing role, with no
automatic retries. `--role warden` restricts validation/generation to that role;
rendering always compares the complete cast. An unresolved request leaves a
pending marker; a received raw response is retained until all previews and the
receipt are safely written. Inspect these before retrying. Changed inputs need
a separately retained audition set, not replacement of the original candidates.

Rendering is offline. Individual clean files preserve provider bytes. Radio
previews use the existing comms recipe and the ensemble's 0.8 playback gain;
clean and radio reels play A, B, C with two seconds between candidates. The
output `review.json` records candidate start times, peaks, RMS and clipped-sample
counts. Decoding and level checks do not establish accent, acting, pronunciation
or intelligibility in the live battle mix; those remain listening decisions.

Guidance checked 2026-10-12:

- https://elevenlabs.io/docs/eleven-creative/voices/voice-design
- https://elevenlabs.io/docs/api-reference/text-to-voice/design
- https://elevenlabs.io/docs/overview/capabilities/text-to-speech/best-practices
