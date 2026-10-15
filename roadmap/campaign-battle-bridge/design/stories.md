# Campaign → Battle Bridge — Open Stories

Status: ACTIVE — 4 open stories

Written: 2026-08-23

Updated: 2026-10-15 — added the market garrison model.

Read `campaign-battle-bridge-nouns.md` before changing a bridge story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `defense-intensity-feel-pass.md` | Ready | The shipped overwatch scaling has headless wiring coverage but still needs representative in-game tuning acceptance; manual acceptance only. |
| `market-garrison-model.md` | In progress — consumers wired; calibration next | One garrison estimate per market (strength, quality, heavy lean, readiness) of our own, identical with or without Nexerelin, mixed through every squad. Needed by `nex-invasion-conquest.md` slice 2. |
| `target-profile-defense-authority-cleanup.md` | Proposed | `MissionLaunch` currently resolves both `TargetProfile` and the separate `DetachmentResolver.planetHasHeavyArmaments` market scan. Consolidate one market read without silently changing the mech predicate. |

Economic district content is not bridge backlog: its shared substrate and
spaceport apron/campus are shipped, while mining, refinery, agriculture, and
art work remain with the economic-districts feature. Stability weighting has
no approved consumer story yet. Faction-specific facility treatment is owned
by `target-faction-facility-treatment.md`; ground roster and doctrine consumers
remain in their own feature buckets.
