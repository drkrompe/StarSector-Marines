# Battle Radio

Status: ACTIVE — order exchanges are the net's only unscripted traffic; battle music follows the battle through four music states; mix, a sourced alert bed, and release-audio provenance remain unsettled.

Written: 2026-08-23

Updated: 2026-11-04 — the passive combat reports and company command's replies are gone (owner).

Read `stories.md` for the open-work board.

## Purpose

Battle radio is the quiet presentation layer that makes the deployed company's
net feel like people coordinating a battle. It voices orders squads have
actually accepted; it neither creates gameplay state nor serves as a continuous
voice track.

## Vocabulary and ownership

- A **speaker** is one voice in an exchange: company command, field command or an
  infantry callsign. Defenders, mechs and drones never speak on this net.
- **Company command** is the off-field company officer, distinct from squad
  speakers and employer mission contacts. In Marine Operations she occasionally
  addresses sampled assignment exchanges over a non-positional comms channel.
- **Field command** is the company's own officer on the ground, Morgan Hale.
  She sits between company command and the squad callsigns: she speaks for the
  landing as a whole, never for one squad's situation, and never for the ship.
- The **exchange gap** is the single global throttle on exchanges. It keeps the
  radio subordinate to combat sound and music.
- A **host** is a presentation environment: the battle screen.

`OperationRadio` owns exchange selection and presentation state; the host decides
when it may speak. The simulation owns tactical state and reports only the facts
the presentation layer may observe.

## No passive reports

Nobody on this net speaks in reaction to a combat fact (owner). Contact, a broken
squad, friendly fire, a spotted mech or an enemy down get no portrait, subtitle
or radio line, from the callsigns or from Castle. The fight is heard through the
marines' own barks (`colony-breach-nouns.md`), which are positional and carry no
portrait.

Every battle begins with a short opening silence. Thereafter exchange starts are
at least the exchange gap apart, and other speech closes the net for its own
length plus a quiet gap. An emitted line is never preempted by another exchange.

## Determinism and presentation boundary

Radio observes simulation output only after simulation advance. It never
mutates battle state, and its timing and clip selection use presentation-owned
randomness rather than the simulation's seeded stream. Consequently, radio
may vary between presentations without changing a replay or outcome.

Marine Operations presents sampled assignment exchanges in generated voices over
the shipboard radio net.

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

Each exchange turn maps to recorded lines of the generated ensemble, produced by
the pipeline and registered for the mod runtime. There is no second bank: when
the ensemble's catalog cannot load, the net stays silent. The proprietary
standard-pilot library that once served as a fallback was dropped before the
first public release (`v0.1-public-build.md`), and no proprietary radio audio
may return.

Audio builds cache each encode by source bytes, effective encoding settings,
and ffmpeg version/build. Adding or editing a manifest entry only encodes new
content or changed settings; timestamps, clip names, and output paths do not
invalidate other clips. Gradle shares this cache across session worktrees at
`GRADLE_USER_HOME/caches/starsector-marines/audio-v1` (normally under `.gradle`
in the user home). A cold or cleared cache needs one full encode. Only the
resulting Ogg files go into `mod/sounds`; cache files never ship with the mod.

New radio vocabulary needs a stable, confirmed simulation fact and a clear
speaker. Assignment exchanges observe accepted own-force directives and preserve
the presentation-only boundary. New identity treatments (faction, patron, alien,
or named-captain nets) may vary content or treatment but must not weaken those
laws.

Authored opening operations also expose a retained story-beat history, as defined
in `early-operation-nouns.md`. It is a dialogue input, separate from transient combat cues. Player presentation consumes only the public
projection; observer-only wave and defender-order events cannot become radio
intelligence. Story dialogue bypasses the exchange gap and chooses content by the enclosing
chapter identity. Stable event
keys do not by themselves grant a speaker, a sound asset, or playback priority.

The campaign battle host consumes both story arcs' public beats once
per attempt. Authored character clips own the voice channel ahead of order
exchanges. A mission line starts without waiting for the exchange gap and stops
any playing turn; exchanges stay quiet during story speech and observe the quiet
gap afterwards. Ordinary mission lines finish their sentences, including
when a newer event supersedes the instruction being spoken. An explicitly authored
emergency call with higher priority can interrupt routine speech. Emergency calls
wait for each other unless a newer one explicitly supersedes the current fact:
a shootdown report can replace a pilot's now-obsolete survival call. Interrupted
instructions return only while still relevant, within their original expiry window;
neither interruption nor replay extends that window. Accepted assignments
continue to be observed while speech owns the channel. Queued story lines expire in real time; later
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
mission contact. Castle speaks in assignment exchanges and authored mission
lines; she does not react to squad reports (owner).

The player normally oversees deployment from the command ship. Ground radio is
traffic between deployed units and shipboard command, not commentary addressed
to the player. The officer coordinates ground teams; individual squad voices
acknowledge and read back.

Write each line with a clear speaker and recipient. Prefer an addressed callsign
within a conversation over collective addresses such as "all" or "ground teams".
An exchange's radio callsign is local casting, not a roster label or permanent
squad identity. Specific destinations, support relationships and successful
actions still require facts supplied by their trigger. A recorded order is
radio presentation, not evidence that an AI action has been issued or obeyed.

Assignment handshakes provide the officer's addressed coordination.

Command subtitles identify CASTLE / COMPANY COMMAND and use her original
painted portrait; field command identifies MORGAN HALE / FIELD COMMAND with
hers; infantry subtitles identify the current exchange's callsign
and show that radio actor's helmet-interior portrait. The same face follows the
actor through acknowledgements and readbacks. These are radio
cast identities, not portraits assigned to individual roster marines or squads.
They remain visible for the recorded lifetime even
when audio is muted or unavailable. Selection and timing are presentation state and leave
simulation randomness untouched.

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

## Field command

Field command is an alternative voicing of a company-level assignment exchange,
never a replacement for Castle's direct traffic. When one observation retasks
more than one squad, the sampled exchange is company-level; if the deployment's
leading officer is the company's founding officer, the rotation may give it to
her. Castle then calls Morgan, she acknowledges, Castle reads the same
instruction a squad would hear, Morgan hands it to the exchange's callsign, and
that callsign reads back. A withdrawal uses her last-out handoff. A single
squad's new task always stays between Castle and that squad, and when she is
not leading the deployment (wounded, aboard, or another officer selected) the
net is exactly the callsign net. She shares the exchange gap and cancellation
rules, and her callsign handoff is still exchange-local casting.

Further ground-commander reports (company landed and counted, compound taken,
company too thin to push, squads back together) are direction. Each needs its
own confirmed fact before it can be a turn.

## Operational exchanges

Operation chatter makes deployed units and shipboard command legible as people
coordinating an ongoing battle. It is optional to follow; objectives and critical
state must remain understandable without listening. Quiet intervals are part
of the mix, not empty slots to fill. Audition reels compress events for review
and do not prescribe the in-game speaking rate.

An **exchange** binds a speaker, an addressed recipient and a current operational
fact across several turns. A new assignment can use callout, acknowledgement,
instruction and readback; follow-up reports can use fewer turns. The instruction
waits for the acknowledgement's audio to finish, but the simulation never waits
for dialogue. Assignment traffic requires an accepted own-force directive;
routine renewals of the same task must not repeat the conversation. Arrival,
engagement, capture and hold are different facts, not interchangeable reports.

The net represents a larger battlefield, including maps with one to a hundred
squads. It does not simulate permanent radio identities. Cast rotates between
self-contained exchanges; a callsign and actor remain consistent throughout one
exchange, without binding that voice to a deployed squad's roster name. The
company officer and Kestrel's strained delivery anchor the cast, with Hammer's
worn, protective authority and Viper's forceful impatience giving infantry
voices different personalities. Jackal adds dry Irish understatement, Ox a warm
New Zealand bass and patient reassurance, and Rivet smoky Lancashire bluntness.
Each infantry actor takes part in assignment exchanges under the same rules. Rook's cockpit delivery remains authoring
content until an eligible physical aircraft and delivery evidence support it.

Changed accepted assignments are sampled into one pending exchange, never a
queue per squad. Unselected and throttled changes are consumed. The global
assignment cooldown does not shrink with army size; a hundred squads supply
more candidates, not a hundred conversations. Recreated assignment snapshots,
capture-zone renumbering at the same compound and a moving escort rally do not
constitute fresh orders. Admitted turns retain their order
and wait for actual playback completion plus a short quiet gap.

Calls and replies must share a valid context. Cancel unspoken turns when a unit
is lost, an assignment changes, or a later fact makes the exchange obsolete.
Only public or own-force facts may supply nouns; dialogue must not reveal hidden
enemy state. A recorded covering maneuver or successful landing needs evidence
of that action, not merely a plausible battlefield setting. Forced alignment
and edits at phrase boundaries can assemble recorded callsigns and destinations;
authored place names must match disclosed destinations or use a generic
instruction. Callsigns are exchange-local, not claims about a roster name. No synthesis API is part of runtime.

Future mixing may allow a quiet secondary monitored net behind one foreground
exchange, with ducking around identities, instructions and urgent warnings.
Foreground turn order remains protected, and one character cannot transmit
twice at once. Mission sentences retain their priority and completion guarantee
except for the explicitly authored emergency interruption described above.
Runtime currently uses one foreground exchange and no secondary-net overlap.
Pausing starts no new turn; stale pending and ongoing contexts expire in real
time. Completion drops unspoken turns and lets an active sentence finish. Zoey
preempts incidental speech and discards the interrupted exchange. Leaving the
host stops its audio; resizing does not restart it. Bespoke covering maneuvers,
resupply and transport conversations remain authoring auditions until their
required operational evidence is connected.
