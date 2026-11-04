# Company officer

Approved voice: **Company Officer B - British Matriarch**,
`cuwCdnt9CKAB9XrTA1j0`, ElevenLabs `eleven_v4`, provider-default voice settings.
`voice.json` retains the original Voice Design prompt and provenance. The user
approved candidate B and her v4 emotional tests. No game recording was uploaded
or cloned. Castle's lines live in the catalogs of the things she speaks in: the
operation radio, the Wayline story casts and the company guide. She has no bank
of her own and does not react to squad reports.

## Consistent comms

Manifest entries use `processing: "comms"`. `ProcessAudioTask` owns the single
FFmpeg recipe: 280 Hz high-pass and 3.6 kHz low-pass (two poles), 3:1 compression
at 0.125 amplitude with 5 ms attack / 90 ms release and 1.5 makeup, gentle tanh
soft clipping, 2.5 output gain to recover filtering loss, then an unboosted
0.89-amplitude limiter. Pitch and pacing stay
unchanged. The processed Ogg files are non-positional UI speech with sound-bank
volume 0.8.

Ordinary `:asset-pipeline:processAudio` builds the comms copies offline. Omit the
processing field for clean encoding; never rewrite the source MP3 to apply a
radio filter. The recipe itself participates in the per-clip cache, so tuning it
only rebuilds treated clips. Additional transmit clicks should be short separate
channel assets; broadband static is intentionally absent from this first profile.
