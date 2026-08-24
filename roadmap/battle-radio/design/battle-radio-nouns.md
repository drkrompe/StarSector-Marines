# Battle Radio

Status: ACTIVE — one presentation-only cue policy spans both battle hosts; mix and release-audio provenance remain unsettled.

Written: 2026-08-23

Updated: 2026-08-24 — replaced the open-story count with direction-oriented status.

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

New radio vocabulary needs a stable, confirmed simulation fact and a clear
eligible speaker. It must enter the shared policy rather than become a
host-specific shortcut, compete under the same voice budget, and preserve the
presentation-only boundary. New identity treatments (faction, patron, alien,
or named-captain nets) may vary content or treatment but must not weaken those
laws.
