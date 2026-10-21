# Breach audio

Status: IN PROGRESS (clips, catalog and registration landed; system hooks pending)

Written: 2026-10-21

Model: `colony-breach-nouns.md` (Beats). Radio law: `battle-radio-nouns.md`.

## Scope

1. **Swarm SFX** (ElevenLabs sound effects): runner chitter and death, brute
   bellow, impact and wall-break, spitter lob and acid splash, bore eruption
   (ground rupture), bore ambience loop by tier, bore collapse, spore-vent
   burst, matriarch roar. Several variants per sound so a swarm is not one
   clip on repeat. Positional where they come from a body or a bore.
2. **Radio and narration** (ElevenLabs voices): a colony militia voice and a
   colony administrator voice on the shelter radio, plus Marine company lines
   for the PLAYER beats in the noun doc's beat table (`tremor`,
   `bore_erupted_N`, `mother_bore_erupted`, `lift_down`, `cohort_boarded`, …).
   Design the voices with the existing personality/audition tooling and
   record receipts like the Wayline set.
3. **Pipeline.** Generation scripts under `tools/` or `art-source/audio/`
   (dry-run by default, credentials from the environment, as
   `generate_wayline_narration.py` does), sources and receipts tracked, clips
   registered in `audio.json` → `processAudio` → `sounds.json`. Never commit
   encoded `mod/sounds/` output.
4. **Hookup.** Narration keyed to beats through `StoryNarrationQueue`; radio
   chatter vocabulary per the radio content law (a confirmed sim fact and an
   eligible speaker). Swarm SFX hooked where bodies attack, die and spawn, and
   where bores erupt and collapse — if those systems have not landed yet,
   register the clips and leave a short list of hook points for the owning
   story rather than touching its files.

## Acceptance

- Owner can audition every clip; a short review sheet lists them.
- Beats that exist on `main` play their lines in a live-shaped battle.
- No secrets committed; generation is reproducible from tracked scripts.

## Landed

- `tools/generate_colony_breach_audio.py` (dry-run default; `--generate`, `--replace`,
  `--id`, `--register`). Speech catalog `mod/data/marines/colony_breach_narration.json`
  (mission id `story_colony_breach`, event = the full beat id); SFX spec and sources
  under `asset-pipeline/src/tool/resources/audio/colony-breach/`. Receipts sit beside
  each take.
- `WaylineNarration.FEATURE_PATHS` merges the catalog into the built-ins, so beats
  published to `StoryBeatLog` play through `StoryNarrationQueue` unchanged.
- SFX ids are one `sounds.json` entry per sound with its variants (the engine picks one):
  `marines_breach_` + `runner_chitter`, `runner_attack`, `runner_death`, `brute_bellow`,
  `brute_impact`, `brute_wall_break`, `brute_death`, `spitter_lob`, `spitter_splash`,
  `spitter_death`, `bore_eruption`, `bore_ambience_fissure|bore|maw` (loops),
  `bore_collapse`, `spore_vent_burst`, `matriarch_roar`.
- Voices are provisional library picks (militia: Sadie; administrator: David; Marine
  company lines: Castle). Swap `voiceId` and regenerate with `--replace`.

## Hook points still pending

The owning systems have not landed (or have no sound cue feed), so no hook files were
edited:

1. **Mission to catalog.** `WaylineNarration.storyMissionId` must return
   `"story_colony_breach"` for the Colony Breach mission and its debug scenario
   (`breach-mission-spine.md`). Until then no line plays.
2. **Beats.** The breach sequence must emit the `colony_breach.*` ids from the nouns
   table as PLAYER beats with exactly those ids; lines exist for `landfall`,
   `militia_contact`, `shelter_reached`, `tremor`, `bore_erupted|grew_N_bore|grew_N_maw|collapsed_N`
   for N=1..4, `mother_bore_erupted|collapsed`, `matriarch_emerged`, `tender_down`,
   `hardpoint_established`, `lift_inbound`, `lift_down`, `cohort_boarded`, `colony_lost`.
   A beat with no line is silent, so numbers above 4 need lines added to the catalog.
3. **Swarm bodies** (`colony-breach-nouns.md`, Swarm strain): a presentation cue feed, as `TenderCues` does, for
   body attack, death and per-strain bellow/lob; a `TenderAudio`-style player admits them
   under a per-kind gap and plays them positionally with pitch jitter.
4. **Bores**: hooked. `BoreCues` feeds `BoreAudio` (eruption, collapse, surge one-shots and a
   positional ambience loop per living bore that follows its tier).
5. **Spore vents**: hooked. `weapon.spore-burst` names `spore_vent_burst` as its fire sound.
6. **Matriarch** (`matriarch.md`): `matriarch_roar` at emergence; spitter splash on acid impact.

7. **Harvest and surge** (`swarm-harvest.md`): `body_drag` while a body is carried to a bore is pending; `bore_surge` at each surge is hooked through `BoreCues`. `first_harvest` has a militia line; `bore_fed_N` and `surge_N` are OBSERVER_ONLY and stay silent as beats.
