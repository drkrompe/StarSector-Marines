# Battle Radio

Status: ACTIVE — one presentation-only cue policy spans both battle hosts; mix and release-audio provenance remain unsettled.

Written: 2026-08-23

Updated: 2026-10-11 — mission speech finishes active lines and takes precedence over squad chatter.

Read `stories.md` for the open-work board.

## Purpose

Battle radio is the quiet, positional presentation layer that makes the
deployed marine infantry net feel responsive to the battle already being
simulated. It reinforces confirmed tactical moments; it neither creates
gameplay state nor serves as a continuous voice track.

## Vocabulary and ownership

- A **cue** is one eligible radio call: contact, fallback, check fire, mech
  spotted, enemy down, acknowledgement, or sustained-combat chatter.
- A **speaker** is the eligible marine infantry squad selected for a cue. A
  squad must be alive, belong to the Marine faction, and be neither a mech nor
  a drone. Defenders and ineligible or wiped squads never speak on this net.
- A **presentation event** is a fact the completed simulation exposes for the
  current frame. It is evidence for a cue, not a command back into the
  simulation.
- The **voice budget** is the single global throttle for every cue. It keeps
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

The two hosts consume the same policy, events, speaker selection, and voice
budget. Their different audio coordinate systems are deliberately the last
step, after a cue has selected its speaker centroid.

## Content and extension law

Each cue maps to a positional sound pool produced by the audio pipeline and
registered for the mod runtime. The current standard-pilot library is
proprietary placeholder content and must not be treated as cleared release
audio.

Audio builds cache each encode by source bytes, effective encoding settings,
and ffmpeg version/build. Adding or editing a manifest entry only encodes new
content or changed settings; timestamps, clip names, and output paths do not
invalidate other clips. Gradle shares this cache across session worktrees at
`GRADLE_USER_HOME/caches/starsector-marines/audio-v1` (normally under `.gradle`
in the user home). A cold or cleared cache needs one full encode. Only the
resulting Ogg files go into `mod/sounds`; cache files never ship with the mod.

New radio vocabulary needs a stable, confirmed simulation fact and a clear
eligible speaker. It must enter the shared policy rather than become a
host-specific shortcut, compete under the same voice budget, and preserve the
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
per attempt. Mara's non-positional clips own the voice channel ahead of marine
chatter. A mission line starts without waiting for the squad cooldown and stops
any playing squad call; squad chatter stays quiet while Mara speaks and observes
the normal gap afterwards. Mission lines never interrupt each other, including
when a newer event supersedes the instruction being spoken. Squad transitions
continue to be sampled while speech owns the channel. Queued story lines expire in real time; later
facts supersede obsolete instructions (for example, a lost militia line cancels
its earlier request for support if that request has not started playing).

Story subtitles name Mara and remain readable for the recorded duration even
when sound is muted or unavailable, in both strategic and direct-control views.
Speech runs in real time while simulation speed changes; pause allows the
current recording to finish but starts no new line. Completion discards pending
instructions but lets the active mission sentence finish. Leaving a screen stops its owned audio. Resizing does
not reset the battle cursor or replay briefings. The hybrid debug host has no
campaign chapter identity and therefore does not play Mara's story dialogue.
