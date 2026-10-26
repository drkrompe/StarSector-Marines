# Battle music director

Status: IN PROGRESS — built and integrated; the model is in `battle-radio-nouns.md` (Battle music). Four synth quiet beds ship and a mission names its own; the Dust Ledger plays the owner's pick, Briefing drive. Awaiting the owner's acceptance of the Dust Ledger walk-in clip with it; retire on acceptance.

Written: 2026-10-26

Updated: 2026-10-26 — four quiet beds ship, chosen per mission; the Dust Ledger plays Briefing drive (owner's pick).

Read `battle-radio-nouns.md` (presentation-only audio, hosts, the voice
budget) and `dust-ledger-crossing.md` (the quiet phase and the alarm) first.

## Why

Battle music today is one shuffled pool of combat tracks
(`marines_battle_music_02` to `_10`). `BattleScreen.startBattleAudio` starts it
when the battle opens and `BattleMusicPlaylist` keeps it going to the end.
Nothing in the battle can change it. The Dust Ledger opens with a quiet phase:
the company walks in as a freight drop and chooses its moment. Full combat
music over that tells the player a fight is on when none is. The owner wants
the battle music raised only when the alarm sounds, and generally music that
follows what the battle is doing.

## The noun

The **music director** is presentation only. It reads published battle facts
each frame, picks a **music state**, and asks the host to play that state's
tracks. It never writes simulation state, and the simulation never waits on
it. A host does the playback.

Music states:

- **Quiet**: before contact, or a stealth phase. A low tension bed, or silence
  under the map's ambient loops.
- **Alert**: contact is near, or an alarm is raised but no fighting yet.
- **Combat**: the current combat pool.
- **Aftermath**: the battle is won or lost, or a long lull follows a fight.
  This falls back to quiet when no aftermath track exists.

**Who decides the state:**
- A mission may declare its music states itself, through a small published
  fact such as "the mission's music state is X". The Dust Ledger declares
  quiet until the alarm and combat from the alarm on.
- A mission that declares nothing gets the **default heat rule**: combat
  while the company has fired or been fired on in the last N seconds, then
  alert, then quiet after a longer lull. Hysteresis stops it flapping.
- Battles that only fight, such as Conquest or a skirmish, land in combat
  within seconds, as they do today.

**Transitions** crossfade (`playCustomMusic` fade in and out). Raising the
music is fast, a second or two. Lowering it waits for the lull rule and
fades slowly. A track is never cut in its first seconds unless the music is
being raised. Within one state the director keeps today's no-repeat shuffle,
with one `BattleMusicPlaylist` per state.

## Tracks

- **quiet**: a bed, never silence (owner, 2026-10-26: "we need pre-battle
  music"). The owner's direction for it (2026-10-26, rejecting both the
  Marine Ops theme and the arrival's strings score): "We need some more
  neutral music loops that tie in to action. Ideally something scifi synthy
  like 90s action or starcrafty". So the bed is tense but not combat, retro
  analog synth (pads, a pulsing bass, an arpeggiated ostinato) at a moderate
  tempo, and sits in the combat pool's family (measured about 130 BPM, E minor
  and its neighbours) so the alarm's crossfade lands naturally. Candidates are
  generated through ElevenLabs by `make_quiet_beds.py` (art-source, with each
  take's request beside it), cut to bar-aligned loops of at least 90 s.
  Prompts describe the style in plain musical terms and never name a work or
  an artist. The owner picked Briefing drive for the Dust Ledger and kept all
  four "for future missions" (2026-10-26), so each ships as a `QuietBed` and a
  mission names its own; one that names none gets Sequencer pulse, the most
  neutral. The strings take stays in the arrival cinematic only. The bed sits
  under the combat pool and fades out over the raise crossfade at the alarm. The Marine Ops theme, "Next
  Move" (`marines_intel_music`), is the menu's alone and never plays in battle
  (owner, 2026-10-26: 'Pre-battle - no we should not play "next move"');
- **alert**: the combat files at reduced volume (the `_alert` sound sets)
  until an alert bed is sourced;
- **aftermath**: the quiet bed.

Sourcing tracks is the owner's call. `backlog.md` already names a candidate
music pack for "when the battle music direction is contracted". This story is
that contract for the state model; buying or licensing tracks is not part of
it.

## Work

1. **Director and state rule.** Add the director as a small pure class with
   `BattleMusicPlaylist` per state, plus the default heat rule. Unit-test the
   state choice, the hysteresis and the no-cut rule on synthetic fact
   sequences.
2. **Published fact.** Add an optional mission music state on the battle's
   presentation facts, and declare it from the Dust Ledger's quiet phase and
   alarm. A unit test covers the declaration.
3. **Hosts.** Drive the standalone `BattleScreen` from the director. Check
   whether the vanilla-combat bridge host plays battle music at all; if it
   does, route it through the same director.
4. **Evidence.** Log each music-state change with its reason in the battle
   log. Record a Dust Ledger battle video with the music on, from the look
   preset at 1920x1080, through the quiet phase, the alarm and the fight. Put
   it on Drive and show it to the owner. Headless runs have no audio, so the
   log is the check.

## Acceptance

- In the Dust Ledger, no combat music plays before the alarm, and combat
  music starts within two seconds of it.
- A battle that declares nothing reaches combat music within seconds of first
  contact, and settles after a long lull without flapping.
- The track order within a state keeps today's no-repeat shuffle.
- Tests cover the state choice and declarations, each in under one second.

## Open questions

- The alert bed: which track, and where from? This is the owner's call.
  Until then: the combat files at reduced volume.
- Should a victory or defeat sting play? Default: no, until tracks exist.
