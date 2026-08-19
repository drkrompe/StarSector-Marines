# Background Radio v1 — Complete

Shipped in `ba6f3b5a`.

## What landed

- Twelve selected MW4 radio clips committed as reproducible audio-pipeline
  inputs: four visual-contact calls, four fallback calls, and four short
  copy/roger acknowledgements.
- Three positional sound pools registered in `sounds.json`, with effective
  pre-attenuation gain tuned to roughly 24%.
- `BattleRadioChatter`, a presentation-only controller that detects marine
  infantry contact and morale-break transitions, prioritizes fallback calls,
  and emits sparse acknowledgements from sustained engagements.
- A 2.5-second opening silence, seven-second global voice gap, and randomized
  16–28-second acknowledgement interval. Paused standalone battles do not
  advance these timers.
- Identical cue policy in `BattleScreen` and the vanilla-combat bridge's
  `GroundSimPresentation`, with each host retaining its own positional audio
  projection.
- Filtering for defenders, mechs, drones, and wiped squads; audio owns its own
  RNG and never changes simulation state or determinism.
- Focused coverage for transition detection, priority, filtering, timing, and
  reset behavior.
- Proprietary placeholder status recorded in the audio manifest and credits.

## Verification

- `gradlew.bat :test --tests com.dillon.starsectormarines.battle.audio.BattleRadioChatterTest`
- `gradlew.bat build`
- `gradlew.bat :asset-pipeline:processAudio` — 64 encoded, 0 failed; radio
  outputs confirmed as mono positional clips.

## Deferred

- In-game mix/voice-content feel pass.
- Objective, reinforcement, casualty, command-power, and extraction cues.
- Defender, mech, alien, patron, and named-captain net identities.
- Replacement of the proprietary placeholder clips before a stable public
  release.
