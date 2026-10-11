# Company officer

Approved voice: **Company Officer B - British Matriarch**,
`cuwCdnt9CKAB9XrTA1j0`, ElevenLabs `eleven_v4`, provider-default voice settings.
`voice.json` retains the original Voice Design prompt and provenance. The user
approved candidate B and her v4 emotional tests. No game recording was uploaded
or cloned. The character currently has a role title, not a chosen personal name.

The runtime catalog is `mod/data/marines/company_officer_narration.json`.
It keeps clean subtitles separate from emotional synthesis directions. There
are two takes for each of contact, fallback, check fire, enemy mech spotted,
and confirmed enemy down. These are short command responses to squad reports,
not squad voice replacements. Mara remains the mission contact.

Generate only missing/changed takes explicitly:

```powershell
python tools/generate_wayline_narration.py --cast company-officer
python tools/generate_wayline_narration.py --cast company-officer --generate
```

Use repeatable `--line SOUND_ID` for selected lines; replacing changed existing
recordings additionally requires `--replace`. Generation is paid authoring work,
never part of a build. Credentials stay in the author's environment. Each MP3
is the clean source take with an adjacent JSON hash/duration/provider receipt.
The same command defaults to Mara's Wayline catalog without changing her takes.

## Consistent comms

Manifest entries use `processing: "comms"`. `ProcessAudioTask` owns the single
FFmpeg recipe: 280 Hz high-pass and 3.6 kHz low-pass (two poles), 3:1 compression
at 0.125 amplitude with 5 ms attack / 90 ms release and 1.5 makeup, gentle tanh
soft clipping, 2.5 output gain to recover filtering loss, then an unboosted
0.89-amplitude limiter. Pitch and pacing stay
unchanged. The processed Ogg files are non-positional UI speech with sound-bank
volume 0.8. Clean source MP3s remain available for future HQ conversations.

Ordinary `:asset-pipeline:processAudio` builds the comms copies offline. Omit the
processing field for clean encoding; never rewrite the source MP3 to apply a
radio filter. The recipe itself participates in the per-clip cache, so tuning it
only rebuilds treated clips. Additional transmit clicks should be short separate
channel assets; broadband static is intentionally absent from this first profile.

Decode, receipts, duration, cache isolation, cooldown, expiry and interruption
are checked automatically. Performance quality and intelligibility against the
live battle mix still require listening. Guidance:
https://elevenlabs.io/docs/overview/capabilities/text-to-speech/best-practices
