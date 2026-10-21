# Breach audio

Status: READY

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
