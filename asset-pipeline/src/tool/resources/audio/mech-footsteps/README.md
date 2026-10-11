# Mech footfalls

Four ElevenLabs Sound Effects v2 source takes: two heavy muffled ground impacts
and two separate pneumatic strokes. `generation.json` retains prompts and settings;
the adjacent receipts retain input/audio hashes and billing metadata, never credentials.

From the repository root:

```powershell
python tools/generate_mech_footsteps.py              # dry run
python tools/generate_mech_footsteps.py --generate   # paid requests for missing takes only
python tools/prepare_mech_footsteps.py               # prepare separate layers; no API calls
.\gradlew.bat :asset-pipeline:processAudio           # cached mono Ogg encoding
```

The generator reads `ELEVENLABS_API_KEY` from the process environment or the
Windows user's environment. Never put credentials in the catalog or commands.
Unchanged takes are skipped after verifying both input and audio hashes.
`--clip ID` limits generation; replacing a changed take requires `--replace`.
Network failures are not retried automatically because the request may be billed.

Preparation preserves MP3 originals and writes four independent mono WAV masters.
Impacts retain low body through a 28Hz high-pass and 1100Hz low-pass, with up to
700ms decay and -4.5dBFS peak. Pneumatics use 140–3200Hz, up to 300ms, and -8dBFS
peak. Both trim leading silence and fade the edges. No machinery is mixed into an
impact. `preparation.json` records source and master hashes. Tuning these offline
filters needs no credits; rerun preparation and the audio pipeline afterward.

`build/audio-preview/impacts.wav` and `pneumatics.wav` audition the isolated layers.
`foot-events.wav` demonstrates overlap at illustrative timing and runtime gains;
it is only an audition artifact, never a shipped walking recording. Runtime pitch,
distance, and actual gait vary in game.

Only the four encoded one-shots ship. Each actual foot lift plays a pneumatic
stroke; that foot's touchdown plays a separate impact at its planted position.
The two pools have independent variation and volume in `MechFootstepAudio`.
Nearby events play with small pitch variations, and impacts have their own
admission budget so machinery cannot crowd them out. Source
generation is an explicit authoring action; builds and gameplay never call the API.

[ElevenLabs sound-effects API](https://elevenlabs.io/docs/api-reference/text-to-sound-effects/convert)
