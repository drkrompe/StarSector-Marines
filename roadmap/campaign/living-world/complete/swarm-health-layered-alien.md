# Swarm health and layered alien art — shipped

Shipped in `bccbbe16`.

## What landed

- Quartered the two alien health pools: generic `ALIEN` 30 → 7.5 HP and
  civilian-rescue `SWARM_RUNNER` 24 → 6 HP. Damage, speed, roster counts, and
  pursuit behavior are unchanged, so marines can remove individual attackers
  consistently without removing the crowd-pressure premise.
- Generated retained transparent body, head, and digitigrade-foot sources with
  built-in ImageGen, then normalized them into the marine compositor's
  150/72/38 px registration widths with a reproducible project-local script.
- Added append-only `LayeredArmorFamily.XENO` and gave both alien archetypes the
  existing presentation-only layered animation state: continuous body facing,
  independent head look, and alternating-foot locomotion.
- Kept `alien.png` as the all-or-nothing live load fallback and
  `alien-dead.png` as corpse art. Alien firearm layers are explicitly disabled.
- Added contract coverage for the exact health values, layered eligibility,
  append-only xeno family ordinal, and default body/head selection.

## Verification

- Visually inspected the generated 224 px layered composition preview.
- Rebuilt the runtime assets twice from retained sources with no working-tree
  drift.
- `gradlew.bat :test --tests ...SwarmRunnerContractTest --tests
  ...LayeredFacingSystemTest` passed.
- `gradlew.bat build` passed, including the full root and asset-pipeline suites.

## Still manual

Run LOW/MEDIUM/HIGH swarm-rescue scenarios in game to judge roster size,
time-to-contact, layered motion at battle zoom, and whether 6 HP makes runners
too fragile under automatic fire. Tune from observed kill cadence rather than
restoring generic durability by default.
