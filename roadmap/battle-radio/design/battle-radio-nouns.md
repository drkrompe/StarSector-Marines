# Battle Radio

Status: ACTIVE — the battle radio is authored story dialogue only; battle music follows the battle through four music states; mix, a sourced alert bed, and release-audio provenance remain unsettled.

Written: 2026-08-23

Updated: 2026-11-04 — all unscripted radio is gone: combat reports, command replies and order exchanges (owner).

Read `stories.md` for the open-work board.

## Purpose

Battle radio is the presentation layer for what a battle says aloud: a mission's
authored dialogue and its music. It neither creates gameplay state nor serves as
a continuous voice track.

## Vocabulary and ownership

- **Company command** is the off-field company officer, Castle, distinct from
  employer mission contacts.
- **Field command** is the company's own officer on the ground, Morgan Hale. She
  speaks for the landing as a whole, never for one squad's situation, and never
  for the ship.
- A **host** is a presentation environment: the battle screen.

## Nothing unscripted

Only a mission's authored lines speak on the radio (owner). Nobody calls out a
combat fact (contact, a broken squad, friendly fire, a spotted mech, an enemy
down), and nobody voices a squad's orders. The fight is heard through the marines'
own barks (`colony-breach-nouns.md`), which are positional and carry no portrait.

## Determinism and presentation boundary

Radio observes simulation output only after simulation advance and never mutates
battle state, so its presentation cannot change a replay or outcome.

## Battle music

The **music director** (`BattleMusicDirector`) is presentation only, under the
same boundary as the radio: it reads published battle facts after each
advance, picks a **music state**, and tells the host what to play. It never
writes battle state and the battle never waits on it.

- **Quiet**: before contact, or a stealth phase.
- **Alert**: contact is near, or the fighting has lulled but not ended.
- **Combat**: the fighting.
- **Aftermath**: the battle is won or lost.

**Who decides.** A finished battle is in aftermath. Otherwise a mission may
declare its state as a published fact (`BattleMusicFacts`); the Dust Ledger
declares quiet while the company walks in as freight and combat from the
alarm on (`DustLedgerProgress.musicState`), and Blind Watch the same about its
alarm (`BlindWatchProgress.musicState`). A mission that declares nothing
gets the **heat rule**: quiet until first contact (the company fires or is
fired on), combat on contact and for a hold after the last one, then alert,
then quiet after a long lull. The heat clock runs on battle time, so a paused
battle keeps its music, and the hold is the hysteresis: steady sparse fire
stays combat, and a long lull settles once, through alert to quiet.

**Transitions.** Raising the music crossfades at once. Lowering fades slowly
and never cuts a track in its first seconds; the change waits until the track
has played that long. Each state has its own playlist with the no-repeat
shuffle, retained across battles.

**Tracks.** Combat plays the battle pool. A walk-in is never silent (owner):
quiet and aftermath play the battle's **quiet bed** (`QuietBed`), looped so a
long quiet phase never falls silent. A bed is a neutral, tense retro sci-fi
synth loop in the combat pool's family (about 130 BPM around E minor), so the
alarm's crossfade lands naturally (owner). A mission names its bed alongside
its music state; one that names none gets the default. The four beds:

- **Briefing drive**: a purposeful groove with a hinted theme, for a company
  that walks in with a plan. The Dust Ledger's bed.
- **Sequencer pulse**: cold, mechanical and the most neutral. The default, and Blind Watch's.
- **Frontier twang**: gritty outpost colour with a lonely guitar, for frontier
  colonies, militia and dusty ground.
- **Night ops**: dark, sparse and stealthy, for night raids and infiltration.

They are generated takes cut to seamless 49-bar loops (`make_quiet_beds.py`
in art-source keeps each request). A bed sits under the combat pool (music is
encoded to one loudness) and a raise fades it out over its crossfade. A state with one track loops it; a state with several
shuffles them. Until an alert bed is sourced (the owner's call), alert plays
the combat files at reduced volume (the `_alert` sound sets). The Marine Ops
theme, "Next Move" (`marines_intel_music`), is the menu's alone and never
plays in battle (owner). The battle screen is the only music host. Each change of state is
logged with its reason. Headless look recordings run the same director and
mix its tracks into the soundtrack under `-Dsnapshot.lookSound=true`.

## Content and extension law

Every recorded line is generated, produced by the pipeline and registered for the
mod runtime. The proprietary standard-pilot library that once served as a fallback
was dropped before the first public release (`v0.1-public-build.md`), and no
proprietary radio audio may return.

Audio builds cache each encode by source bytes, effective encoding settings,
and ffmpeg version/build. Adding or editing a manifest entry only encodes new
content or changed settings; timestamps, clip names, and output paths do not
invalidate other clips. Gradle shares this cache across session worktrees at
`GRADLE_USER_HOME/caches/starsector-marines/audio-v1` (normally under `.gradle`
in the user home). A cold or cleared cache needs one full encode. Only the
resulting Ogg files go into `mod/sounds`; cache files never ship with the mod.

New radio traffic is authored per mission. It needs a stable, confirmed simulation
fact and a clear speaker, and preserves the presentation-only boundary.

Authored opening operations also expose a retained story-beat history, as defined
in `early-operation-nouns.md`. It is a dialogue input, separate from transient combat cues. Player presentation consumes only the public
projection; observer-only wave and defender-order events cannot become radio
intelligence. Story dialogue chooses content by the enclosing
chapter identity. Stable event
keys do not by themselves grant a speaker, a sound asset, or playback priority.

The campaign battle host consumes both story arcs' public beats once
per attempt. Ordinary mission lines finish their sentences, including
when a newer event supersedes the instruction being spoken. An explicitly authored
emergency call with higher priority can interrupt routine speech. Emergency calls
wait for each other unless a newer one explicitly supersedes the current fact:
a shootdown report can replace a pilot's now-obsolete survival call. Interrupted
instructions return only while still relevant, within their original expiry window;
neither interruption nor replay extends that window. Queued story lines expire in real time; later
facts supersede obsolete instructions (for example, a lost militia line cancels
its earlier request for support if that request has not started playing).

Story subtitles identify the actual speaker and portrait and remain readable
for the recorded duration even
when sound is muted or unavailable, in both strategic and direct-control views.
The **voice-over switch** on the Settings page (`VoiceOver`) silences all recorded speech:
narration, radio, marine barks and film dialogue. Subtitles and all
other sound stay. It is saved with the game and on until the player turns it off.
Speech runs in real time while simulation speed changes; pause allows the
current recording to finish but starts no new line. Completion discards pending
instructions but lets the active mission sentence finish. Leaving a screen stops its owned audio. Resizing does
not reset the battle cursor or replay briefings.

The same catalog may explicitly mark an authored line as text-only, in which case
its declared reading duration owns the subtitle and no sound lookup occurs.
Silent reading yields to pending recorded mission speech, then returns if still
relevant; a text-only draft cannot hold a time-critical recording behind silence.
An absent portrait uses the retained text panel. A confirmed terminal report may
explicitly survive completion and start on the completed battlefield, after any
active sentence finishes; this exception never retains ordinary tactical
instructions or delays mission settlement. Cold Account uses recorded Zoey,
Castle and Kord lines with speaker-specific voice assignments. Its final patrol
report starts during the mission-owned departure if it occurs before company
extraction. The last survivor pickup's physical return owns victory; neither
capital departure nor audio duration can postpone it. A separate replayable
Castle/Zoey conversation runs on the successful results screen, after settlement,
with readable portrait-led turns and explicit Replay and Stop controls. Leaving
results stops its audio. Valid Corvus retry identities resolve to the same chapter set.

## Company command

The company officer is an original mature British female Voice Design selection,
retained by stable voice ID with clean source takes and generation provenance.
Her callsign is Castle. She belongs to the company rather than Wayline.
Zoey remains the employer's
mission contact. Castle speaks in authored mission lines and results-screen
conversations; she does not react to the battle or voice squad orders (owner).

The player normally oversees deployment from the command ship. Ground radio is
traffic between deployed units and shipboard command, not commentary addressed
to the player.

Write each line with a clear speaker and recipient. A recorded order is radio
presentation, not evidence that an AI action has been issued or obeyed.

Story subtitles identify the speaker and use that character's portrait. They remain
visible for the recorded lifetime even when audio is muted or unavailable.

Clean generated masters remain separate from a shared offline comms profile:
band limiting, moderate compression, subtle saturation and peak limiting. The
profile is an optional audio-manifest treatment and participates in the encode
cache key. Changing it rebuilds treated clips without regenerating speech or
invalidating unrelated clean audio. Talon Two retains the same source voice as
infantry actor Kestrel Two, but has a distinct face in a flight helmet and a
narrower, more compressed and saturated cockpit radio treatment. The treatment
belongs to his transmissions; other speakers mentioning the aircraft retain
their own presentation. A modest downward pitch shift further distinguishes
the pilot while preserving speech timing.
Alternating story speakers may instead use
a clean dialogue loudness treatment,
with a shared target and peak ceiling; source takes remain untouched.
Runtime playback needs no provider account,
API access or DSP dependency. Transmit clicks or interference, if introduced,
are separate channel effects rather than instructions in synthesis text.
