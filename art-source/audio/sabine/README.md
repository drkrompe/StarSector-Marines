# Sabine Hale

Approved voice: `voice.json` (ElevenLabs Voice Design candidate 1, recorded on
`eleven_v4`). Her lines live in the runtime catalogs beside the speakers they
share a scene with, each carrying her `voiceId`; the shared generator records
them (`python tools/generate_wayline_narration.py --line <id>`).

The brief asked for a calm, low Eastern European field commander. Voice Design
only offers v3-family models, and every candidate lost its accent once read on
v4, the model the game's recordings use; v3 and Multilingual v2 renders of the
same voice were judged worse overall. The owner accepted candidate 1 without the
accent. Delivery comes from per-line directions in `synthesisText`; the voice's
own baseline is calm, so lines that need strain or warmth must say so.

`audition/` keeps the one preview request and the emotion tests behind that
decision. `sabine-audition.py` and `sabine-voice.py` are the explicit, paid,
no-retry scripts that produced them; neither runs in a build. Candidate 3 was
saved for testing and then deleted from the account to free a voice slot.
