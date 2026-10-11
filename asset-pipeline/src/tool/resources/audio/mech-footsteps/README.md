# Mech footfalls

Four ElevenLabs Sound Effects v2 source takes: three short ground contacts and
one quiet linkage layer. `generation.json` retains the prompts and settings;
the adjacent receipts retain input/audio hashes and billing metadata, never credentials.

From the repository root:

```powershell
python tools/generate_mech_footsteps.py              # dry run
python tools/generate_mech_footsteps.py --generate   # paid requests for missing takes only
python tools/prepare_mech_footsteps.py               # offline mix; no API calls
.\gradlew.bat :asset-pipeline:processAudio           # cached mono Ogg encoding
```

The generator reads `ELEVENLABS_API_KEY` from the process environment or the
Windows user's environment. Never put credentials in the catalog or commands.
Unchanged takes are skipped after verifying both input and audio hashes.
`--clip ID` limits generation; replacing a changed take requires `--replace`.
Network failures are not retried automatically because the request may be billed.

The mixer preserves MP3 originals, balances layers, combines the linkage at 22%
with a 25ms delay, and writes three 0.5s mono WAV masters at -6dBFS peak. It also
creates `build/audio-preview/mech-walk.wav`, a sequence at preview level (in-game
gain and distance attenuation are quieter). `preparation.json` records the source
and resulting master hashes. Change the offline recipe to tune layers without
spending credits; rerun the mixer and audio pipeline afterward.

Only the three encoded mixes ship. Each actual foot plant plays one nearby
positional sound with non-repeating variants and slight pitch variation. Source
generation is an explicit authoring action; builds and gameplay never call the API.

[ElevenLabs sound-effects API](https://elevenlabs.io/docs/api-reference/text-to-sound-effects/convert)
