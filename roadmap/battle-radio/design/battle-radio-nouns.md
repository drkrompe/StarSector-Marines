# Battle Radio

Status: ACTIVE — one presentation-only cue policy spans both battle hosts; mix and release-audio provenance remain unsettled.

Written: 2026-08-23

Updated: 2026-10-13 — Infantry radio faces follow the speaking actor; authored emergency calls can interrupt routine mission instructions.

Read `stories.md` for the open-work board.

## Purpose

Battle radio is the quiet presentation layer that makes the
deployed marine infantry net feel responsive to the battle already being
simulated. It reinforces confirmed tactical moments; it neither creates
gameplay state nor serves as a continuous voice track.

## Vocabulary and ownership

- A **cue** is one eligible radio call: contact, fallback, check fire, mech
  spotted, enemy down, acknowledgement, or sustained-combat chatter.
- A **speaker** is the eligible marine infantry squad providing evidence for a cue. A
  squad must be alive, belong to the Marine faction, and be neither a mech nor
  a drone. Defenders and ineligible or wiped squads never speak on this net.
- A **presentation event** is a fact the completed simulation exposes for the
  current frame. It is evidence for a cue, not a command back into the
  simulation.
- **Company command** is the off-field company officer, distinct from squad
  speakers and employer mission contacts. In Marine Operations she occasionally
  addresses sampled assignment exchanges over a non-positional comms channel.
- The **voice budget** is the single global throttle for every squad cue. It keeps
  the radio subordinate to combat sound and music.
- A **host** is a presentation environment: the standalone battle screen or
  the vanilla-combat bridge. Hosts share cue policy but own conversion from a
  speaker's simulation cell to their audio world.

`BattleRadioChatter` owns cue selection and per-battle presentation state; the
hosts own sound playback. The simulation owns tactical state and reports only
the facts the presentation layer may observe.

## Cue semantics

Contact is announced when an eligible squad is first observed engaged or
transitions into engagement. Fallback is announced when such a squad's morale
breaks. Check fire follows an actual damaging friendly ballistic hit on the
hit marine squad, rather than a predicted shot or a miss.

Mech spotted is a one-per-battle recognition call. It requires a live defender
mech that is currently visible plus an engaged eligible squad with current
range and line of sight; that observing squad speaks. Enemy down follows a
confirmed defender-combatant death and speaks from the nearest engaged
eligible squad, falling back to the nearest eligible squad when none is
engaged. Sustained engagement may occasionally produce an acknowledgement or
combat remark.

The controller retains a pending event while the voice budget is closed and
emits at most one cue when it opens. A higher-priority event replaces a
lower-priority pending cue: check fire, fallback, mech spotted, contact, enemy
down, then routine acknowledgement or combat. A pending cue is discarded if
its speaker is no longer eligible.

Every battle begins with a short opening silence. Thereafter all cue kinds
share one voice gap, and the routine sustained-engagement calls use a longer
random interval. The controller never preempts an emitted line and schedules
later cues only when the global voice gap opens.

## Determinism and presentation boundary

Radio observes simulation output only after simulation advance. It never
mutates battle state, and its timing and clip selection use presentation-owned
randomness rather than the simulation's seeded stream. Consequently, radio
may vary between presentations without changing a replay or outcome.

The two hosts consume the same combat cue policy, events, speaker eligibility,
and voice budget. The hybrid host positions stock calls at the speaker centroid;
Marine Operations presents generated voices over the shipboard radio net and
adds sampled assignment exchanges under that same budget.

## Content and extension law

Each cue maps to audio produced by the pipeline and registered for the mod
runtime. Marine Operations uses the generated ensemble; the hybrid host and
unavailable-catalog fallback retain the standard-pilot positional library. That
library is proprietary placeholder content and must not be treated as cleared
release audio.

Audio builds cache each encode by source bytes, effective encoding settings,
and ffmpeg version/build. Adding or editing a manifest entry only encodes new
content or changed settings; timestamps, clip names, and output paths do not
invalidate other clips. Gradle shares this cache across session worktrees at
`GRADLE_USER_HOME/caches/starsector-marines/audio-v1` (normally under `.gradle`
in the user home). A cold or cleared cache needs one full encode. Only the
resulting Ogg files go into `mod/sounds`; cache files never ship with the mod.

New radio vocabulary needs a stable, confirmed simulation fact and a clear
eligible speaker. Combat reports enter the shared cue policy; assignment
exchanges observe accepted own-force directives. Both compete under the same
voice budget and preserve the
presentation-only boundary. New identity treatments (faction, patron, alien,
or named-captain nets) may vary content or treatment but must not weaken those
laws.

Authored opening operations also expose a retained story-beat history, as defined
in `early-operation-nouns.md`. It is a dialogue input, separate from transient combat cues. Player presentation consumes only the public
projection; observer-only wave and defender-order events cannot become radio
intelligence. Story dialogue bypasses the squad voice budget and chooses content by the enclosing
chapter identity. Stable event
keys do not by themselves grant a speaker, a sound asset, or playback priority.

The campaign battle host consumes both story arcs' public beats once
per attempt. Authored character clips own the voice channel ahead of marine
chatter. A mission line starts without waiting for the squad cooldown and stops
any playing squad call; squad chatter stays quiet during story speech and observes
the normal gap afterwards. Ordinary mission lines finish their sentences, including
when a newer event supersedes the instruction being spoken. An explicitly authored
emergency call with higher priority can interrupt routine speech. Emergency calls
wait for each other unless a newer one explicitly supersedes the current fact:
a shootdown report can replace a pilot's now-obsolete survival call. Interrupted
instructions return only while still relevant, within their original expiry window;
neither interruption nor replay extends that window. Squad transitions
continue to be sampled while speech owns the channel. Queued story lines expire in real time; later
facts supersede obsolete instructions (for example, a lost militia line cancels
its earlier request for support if that request has not started playing).

Story subtitles identify the actual speaker and portrait and remain readable
for the recorded duration even
when sound is muted or unavailable, in both strategic and direct-control views.
Speech runs in real time while simulation speed changes; pause allows the
current recording to finish but starts no new line. Completion discards pending
instructions but lets the active mission sentence finish. Leaving a screen stops its owned audio. Resizing does
not reset the battle cursor or replay briefings. The hybrid debug host has no
campaign chapter identity and therefore does not play Mara's story dialogue.

The same catalog may explicitly mark an authored line as text-only, in which case
its declared reading duration owns the subtitle and no sound lookup occurs.
Silent reading yields to pending recorded mission speech, then returns if still
relevant; a text-only draft cannot hold a time-critical recording behind silence.
An absent portrait uses the retained text panel. A confirmed terminal report may
explicitly survive completion and start on the completed battlefield, after any
active sentence finishes; this exception never retains ordinary tactical
instructions or delays mission settlement. Cold Account uses recorded Mara,
Castle and Kord lines with speaker-specific voice assignments. Its final patrol
report starts during the mission-owned departure if it occurs before company
extraction. The last survivor pickup's physical return owns victory; neither
capital departure nor audio duration can postpone it. A separate replayable
Castle/Mara conversation runs on the successful results screen, after settlement,
with readable portrait-led turns and explicit Replay and Stop controls. Leaving
results stops its audio. Valid Corvus retry identities resolve to the same chapter set.

## Company command

The company officer is an original mature British female Voice Design selection,
retained by stable voice ID with clean source takes and generation provenance.
Her callsign is Castle. She belongs to the company rather than Wayline.
Mara remains the employer's
mission contact. Command replies are available in the Marine Operations battle
host; the hybrid debug host has no company command identity and retains its
squad net. Both hosts still use the same squad cue policy.

The player normally oversees deployment from the command ship. Ground radio is
traffic between deployed units and shipboard command, not commentary addressed
to the player. The officer coordinates and supports ground teams; individual
squad voices report their own situation and address other units. Friendly-fire
warnings concern a confirmed incident among those units, without implying the
player chose to cause it. Direct control can place the player behind a unit's
actions, but does not change who a generic net-wide warning addresses.

Write each line with a clear speaker and recipient. Prefer an addressed callsign
within a conversation over collective addresses such as "all" or "ground teams".
An exchange's radio callsign is local casting, not a roster label or permanent
squad identity. Specific destinations, support relationships and successful
actions still require facts supplied by their trigger. A recorded order is
radio presentation, not evidence that an AI action has been issued or obeyed.

The generated ensemble supplies tactical reports admitted by the existing cue
policy. The older generic officer replies remain only as a fallback when the
ensemble catalog is unavailable. No generic reply is added behind an ensemble
report. Assignment handshakes provide the officer's addressed coordination.

Each ensemble actor may supply several recordings for one combat cue. Selection
uses every recording in that cue's pool before repeating, avoids the preceding
line when a pool refills, and prefers a different actor when unused alternatives
remain. Each cue keeps its own history across intervening events and mission
speech; a new battle resets that history. More recordings expand variety without
increasing the voice budget or inventing tactical facts. A one-line pool remains
valid and necessarily repeats.

Command subtitles identify CASTLE / COMPANY COMMAND and use her original
painted portrait; infantry subtitles identify the current exchange's callsign
and show that radio actor's helmet-interior portrait. The same face follows the
actor through acknowledgements, readbacks and combat reports. These are radio
cast identities, not portraits assigned to individual roster marines or squads.
They remain visible for the recorded lifetime even
when audio is muted or unavailable. Selection and timing are presentation state and leave
simulation randomness untouched.

Clean generated masters remain separate from a shared offline comms profile:
band limiting, moderate compression, subtle saturation and peak limiting. The
profile is an optional audio-manifest treatment and participates in the encode
cache key. Changing it rebuilds treated clips without regenerating speech or
invalidating unrelated clean audio. Alternating story speakers may instead use
a clean dialogue loudness treatment,
with a shared target and peak ceiling; source takes remain untouched.
Runtime playback needs no provider account,
API access or DSP dependency. Transmit clicks or interference, if introduced,
are separate channel effects rather than instructions in synthesis text.

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
reports different personalities. Jackal adds dry Irish understatement, Ox a warm
New Zealand bass and patient reassurance, and Rivet smoky Lancashire bluntness.
Each infantry actor participates in both assignment exchanges and combat reports
under the same event and voice-budget rules. Rook's cockpit delivery remains authoring
content until an eligible physical aircraft and delivery evidence support it.

Changed accepted assignments are sampled into one pending exchange, never a
queue per squad. Unselected and throttled changes are consumed. The global
assignment cooldown does not shrink with army size; a hundred squads supply
more candidates, not a hundred conversations. Recreated assignment snapshots,
capture-zone renumbering at the same compound and a moving escort rally do not
constitute fresh orders. Confirmed combat reports get first use of an open
channel before a new assignment exchange. Admitted turns retain their order
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
time. Completion drops unspoken turns and lets an active sentence finish. Mara
preempts incidental speech and discards the interrupted exchange. Leaving the
host stops its audio; resizing does not restart it. Bespoke covering maneuvers,
resupply and transport conversations remain authoring auditions until their
required operational evidence is connected.
