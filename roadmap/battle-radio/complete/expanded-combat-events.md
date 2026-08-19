# Expanded Combat Events — Complete

Shipped in `64c6acf8`.

## What landed

- Expanded the radio library from 12 to 214 standard-pilot clips: 22 contact,
  22 fallback, 44 copy/roger acknowledgement, 44 check-fire, 21 sustained-
  combat, 20 mech-contact, and 41 enemy-down takes.
- Included the ordinary `checkfire2` and `killbrag2` alternate-line families;
  excluded campaign and special-character `_cas` / `_spe` takes.
- `checkfire*` now responds to an actual friendly ballistic impact on a marine
  and speaks from the hit infantry squad, rather than triggering on a miss or
  launch-time prediction.
- `fulllance*` fires at most once per battle when an engaged marine infantry
  squad has current range and line-of-sight contact with a player-visible live
  defender mech. The spotting squad owns the positional voice.
- `killbrag*` responds to a confirmed defender combatant death and selects the
  nearest engaged marine infantry squad as speaker.
- `fools*` shares the existing randomized 16–28-second sustained-engagement
  cadence with copy/roger acknowledgements.
- All new calls share the original seven-second global voice gap. Priority is
  check fire, fallback, mech spotted, contact, enemy down, then ambient combat
  or acknowledgement; no line interrupts audio already playing.
- Standalone and hybrid battles consume the same simulation event seams and
  presentation-only cue policy. Defenders, mechs, drones, and wiped squads
  remain ineligible as speakers.

## Verification

- `gradlew.bat :test --tests com.dillon.starsectormarines.battle.audio.BattleRadioChatterTest`
- `gradlew.bat build`
- `gradlew.bat :asset-pipeline:processAudio` — 266 encoded, 0 failed; all 214
  radio outputs generated as mono positional clips.
- `sounds.json` cross-check — 214 unique radio references, 0 missing outputs.
- `gradlew.bat deployMod --dry-run` — schedules `processAudio` before
  `deployMod`.

## Deferred

- In-game mix and content-feel tuning with the expanded random pools.
- Splash-damage friendly-fire warnings; this slice listens to modeled
  ballistic impacts, which retain shooter/victim squad identity.
- Objective, reinforcement, casualty, command-power, and extraction cues.
- Replacement of the proprietary placeholder clips before a stable public
  release.
