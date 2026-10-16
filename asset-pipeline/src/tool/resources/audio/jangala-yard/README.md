# Jangala yard (arrival C5)

Original deterministic DSP masters for the arrival's C5 NESSA'S YARD, authored locally with
numpy. No licensed recording, remote generation or voice is used; the gantry radio is
syllable-gated band-limited buzz with squelch, so nothing in it needs subtitling.
`generate.py` retains the design: stereo 44.1 kHz 16-bit PCM. Run `python generate.py` to
regenerate the WAVs, then `gradlew.bat :asset-pipeline:processAudio` to encode them to
`mod/sounds/story/jangala-arrival/`.

| id | length | what |
|---|---:|---|
| `c5_yard_spool_down` | 2.8 s | the bridge from C4: the ship's drive and auxiliaries winding down, heard from outside |
| `c5_yard_jungle_bed` | 10 s | distant cicada buzz from the tree line, nearer chirps, low wind and far machinery |
| `c5_yard_steam_vent` | 9 s | the cradle vents; breathes on the steam sheets' 2.9 s cycle |
| `c5_yard_hull_ticks` | 9 s | hot plate cooling: sparse ticks and pings |
| `c5_yard_winch` | 4 s | the gantry jib's winch, one lower-and-raise of the crate's 4 s loop |
| `c5_yard_radio_chatter` | 2.6 s | faint gantry radio, unintelligible |
| `c5_yard_crane_horn` | 2.4 s | a distant crane's two-tone horn off the halls |
| `c5_yard_boots` | 4.5 s | five pairs of boots on dry concrete, arriving late |

Timing lives in `compose_c5.audio_cues()` (art-source/.../nessas-yard), which schedules these
against the video clock. Taste acceptance still wants a listen in the shipped player.
