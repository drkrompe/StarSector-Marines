# Radio voice effects guide

Approved Talon Two treatment: 2026-10-13. Use the existing clean voice take,
the stronger cockpit radio treatment, and **one semitone down** at unchanged
tempo. Two semitones down was auditioned and rejected as too crispy.

This is an authoring reference. `ProcessAudioTask.outputArguments` owns the
executable recipes; update this guide alongside any approved recipe change.
All commands below run from the root of the session worktree required by
`CLAUDE.md`.

## Character continuity

| Character / presentation | Manifest `processing` | Pitch |
| --- | --- | --- |
| Talon Two (`TALON TWO / WAYLINE AIR`) | `air_comms` | −1 semitone; tempo unchanged |
| Infantry Kestrel Two and ordinary radio transmissions | `comms` | Original pitch and tempo |
| Clean dialogue exchanges | `dialogue` | Original pitch and tempo; loudness matching |
| Jangala Approach, Hegemony traffic control (`jangala_arrival_hail_*`) | `orbital_comms` | Original pitch and tempo |
| Castle on the open cabin intercom in the same exchange | `comms` | Original pitch and tempo |
| Zoey Venn (`ZOEY VENN / WAYLINE`), every line | `comms` | Original pitch and tempo |

Talon Two currently uses Liam (`TX3LPaxmHKxFdv7VOQHJ`), also used by infantry
Kestrel Two. Retain the source voice, delivery direction and per-line metadata
in `mod/data/marines/wayline_narration.json`, with the MP3 generation receipts
beside the source takes. His portrait is `graphics/portraits/talon-two.png`.
The user accepted the current treatment; a distinct pilot voice is a future
casting decision, not authorization to regenerate the present bank.

Apply `air_comms` to Talon's own transmissions, including future lines for
this character. Other speakers mentioning Talon or his aircraft retain their
own treatment. Do not select the effect solely from a `gunship` filename.
The profile currently applies to all ten Talon lines in Junction and Thunder Run.
Do not assume every future pilot must share Talon's voice or pitch shift.

## Exact recipes

The chains below are the complete FFmpeg `-af` values, applied once to clean
source speech. Do not process an already filtered Ogg or preview MP3 again.

### Talon Two: `air_comms`

```text
rubberband=tempo=1:pitch=0.943874313:channels=together,highpass=f=420:p=2,lowpass=f=2500:p=2,acompressor=threshold=0.06:ratio=6:attack=3:release=70:makeup=3,asoftclip=type=tanh:threshold=0.38:oversample=4,volume=3,lowpass=f=2500:p=2,alimiter=limit=0.85:level=false:latency=true
```

Order matters:

1. Pitch shift by `2^(-1/12) = 0.943874313`, with `tempo=1` and linked stereo
   channels. This lowers the voice without slowing the sentence. Rubber Band's
   other settings retain their defaults, including shifted formants.
2. Narrow the radio band to 420–2500 Hz with two-pole high/low-pass filters.
3. Compress with threshold `0.06`, ratio `6`, attack `3`, release `70`, makeup
   `3`, then add tanh soft saturation at threshold `0.38`, oversampling `4`.
4. Apply linear gain `3`, then low-pass at 2500 Hz again so saturation does
   not restore the clean high end.
5. Limit at `0.85` with automatic level compensation disabled and latency
   compensation enabled.

### Ordinary radio: `comms`

```text
highpass=f=280:p=2,lowpass=f=3600:p=2,acompressor=threshold=0.125:ratio=3:attack=5:release=90:makeup=1.5,asoftclip=type=tanh:threshold=0.65:oversample=2,volume=2.5,alimiter=limit=0.89:level=false:latency=true
```

This retains original pitch and timing with a wider 280–3600 Hz band and
gentler compression/saturation. It is the comparison treatment for infantry.

### Orbital traffic control: `orbital_comms`

Proposed 2026-10-16 for the Jangala arrival hail (`storyboard-v3.md`); awaiting
the owner's audition.

```text
aformat=channel_layouts=stereo,highpass=f=500:p=2,lowpass=f=2200:p=2,acompressor=threshold=0.05:ratio=8:attack=2:release=60:makeup=3,asoftclip=type=tanh:threshold=0.3:oversample=4,acrusher=bits=12:mode=log:aa=1,aeval='val(0)+0.001*(random(0)*2-1)|val(1)+0.001*(random(1)*2-1)':c=stereo,volume=3,highpass=f=500:p=2,lowpass=f=2200:p=2,alimiter=limit=0.85:level=false:latency=true
```

A station across orbit rather than a nearby gunship: a 500–2200 Hz band
(narrower than `air_comms`), harder compression and saturation, a 12-bit
logarithmic crusher for digital grit, and a faint constant seeded noise floor
(amplitude 0.001). The owner found the first proposal's floor annoying, and then
asked for the static toned down again (2026-10-23): it was 0.003 with a 10-bit crusher. Gain `3`
makes up the energy the narrow band removes, so treated lines land near
−19 LUFS, beside Castle's `comms` lines. The second band-pass keeps the grit and
the noise floor inside the radio band. There is no pitch change.

The noise is written per channel on a fixed stereo layout. A single shared
`aeval` expression (`c=same`) using `random()` crashes FFmpeg 8.1 on real takes,
although it passes on a synthetic tone. Squelch is not baked in: key-up and
key-down bursts are placed by the scene's timeline.

Radio clips with `kind: "ui"` encode as stereo, 44100 Hz, Ogg Vorbis quality 5,
with artwork and metadata removed (`-vn -map_metadata -1 -ac 2 -ar 44100
-c:a libvorbis -q:a 5`). Positional clips use mono instead.

## Recreate or extend Talon's treatment

1. Keep the approved clean MP3 and its generation receipt under
   `asset-pipeline/src/tool/resources/audio/wayline/`. For new recordings,
   follow the project's ElevenLabs narration skill and retain the speaker,
   model, synthesis text, selected take and hashes. Changing this effect alone
   requires no paid synthesis and does not change those source receipts.
2. Select the treatment in `asset-pipeline/src/tool/resources/audio/audio.json`.
   Existing inbound entry, also usable as the shape for a new Talon line:

   ```json
   {"id":"wayline_thunder_run_gunship_inbound","source":"wayline/wayline_thunder_run_gunship_inbound.mp3","kind":"ui","out":"story/wayline","processing":"air_comms"}
   ```

   New story lines still need their catalog, event and sound registration;
   the effect selector only determines audio processing.
3. Confirm FFmpeg supports `rubberband` (`ffmpeg -hide_banner -h filter=rubberband`).
   Use a build with librubberband enabled, on PATH or selected by `FFMPEG`.
   Then encode through the production pipeline:

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr'
   .\gradlew.bat :asset-pipeline:processAudio
   ```

   The pipeline writes `mod/sounds/story/wayline/<id>.ogg`. Its shared cache
   keys include the source bytes, FFmpeg version/build and complete recipe.
   Editing the recipe rebuilds affected encodes without regenerating speech
   or manually deleting the cache. Keep the same FFmpeg build if exact encode
   reproducibility matters.
4. Audition the generated Ogg. To make a portable preview of the established
   inbound reference, run:

   ```powershell
   New-Item -ItemType Directory -Force -Path build/audio-previews | Out-Null
   ffmpeg -y -v error -i mod/sounds/story/wayline/wayline_thunder_run_gunship_inbound.ogg -c:a libmp3lame -q:a 2 build/audio-previews/talon-two-inbound.mp3
   ```

   This converts the processed result for listening; it does not apply the
   filter a second time. The source inbound line is 5.28 seconds. Check that
   the processed line retains its duration and sentence ending, and audition
   an urgent line such as `wayline_thunder_run_gunship_missile_warning` too.
5. Check intelligibility, harsh consonants, distortion and relative loudness
   beside infantry speech and in the battle mix. The stronger saturation is
   intentional, but the approved pitch offset is only one semitone. Report
   listening results separately from successful encoding or automated checks.

For pipeline changes, run `:asset-pipeline:test --tests '*AudioEncodeCacheTest'`.
For new story line wiring, also run `:test --tests '*WaylineNarrationTest'`.
These checks complement listening; they do not judge voice performance.
