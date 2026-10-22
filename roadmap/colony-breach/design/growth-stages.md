# Growth stages: why they are built this way

Status: SHIPPED

Written: 2026-10-21

The model is `colony-breach-nouns.md` (Swarm strain: Growth stage, Age mix,
Pack, The Matriarch's stage, Clearance). The numbers live in code
(`SwarmStage`, `SwarmStageStats`, `SwarmAgeMix`, `BruteChargeRules`,
`SwarmPacks`). This doc keeps the reasons that the model does not show, what
was rejected, and the first balance reading.

## Decisions and their reasons

- **A stage is a property of a body, never a kind of body.** `UnitType` and
  `LayeredArmorFamily` are append-only, and a whelp Runner must behave, path
  and pose as a Runner. So a stage is a field on the body's identity, issued by
  `SwarmStrains` like the Brute's plate, and it rides the corpse.
- **The scales are 0.80 / 1.00 / 1.22.** About a fifth apart in length and
  1.6x in area from stage to stage: the smallest steps that read at play zoom
  (a standing Runner at 32 px per cell is 40, 50 and 62 px long).
- **Size and value carry the stage; spike length does not.** The look check
  measured whelp, adult and elder at mean body luminance 175, 136 and 84. With
  the arms folded, spike length does not read at play zoom; it shows in a
  strike. So the elder's long spikes and the whelp's nubs are flavour, not the
  stage's identity.
- **The elder keeps a pale spine ridge** (owner, treatment A). Plum alone put
  the elder at L 84 against the creep's 94; the ridge lifts it to 105. Rejected
  treatments: near-black with broader spikes, grey and hunched, an ivory bone
  crown.
- **The elder Runner's light plate (6 at rating 4)** makes an SMG's spray
  slide off and leaves rifles alone: a reason to put aimed fire on the leader,
  not a second Brute.
- **Mass follows volume** (scale cubed: 1.5, 3, 5.4), so the Brute's charge
  scales by momentum through the rules it already had. No stage of Brute
  breaks a fortified wall.
- **The Spitter's acid penetration does not scale with stage.** The air threat
  ladder is read from durability; an elder Spitter must not become armoured
  air's answer.
- **The draw is keyed by bore, pulse and body**, not drawn from the battle's
  stream, so the `growthStages=false` control changes no other roll.
- **The director counts stage weight, not bodies** (0.6 / 1 / 1.8; owner). A
  fed bore sends fewer, older bodies rather than the same count stronger, so
  feeding raises danger without a runaway in body count.
- **Navigation keeps one clearance.** Every stage of the small strains keeps a
  sim radius of 0.49 or less so one flow field per goal serves every body. The
  elder Brute (0.55 natural) is capped at 0.49 for navigation, the spatial
  index, ballistics and blast catch, and drawn and shoved at full size. A second
  field family at two-cell clearance was rejected: it doubles the field cost for
  one body type. The elder Brute squeezes through a door it visibly fills
  (owner).
- **The Matriarch's range is narrower** (0.88 / 1.00 / 1.12): she is already
  several cells long, and 1.22x would not fit the colony's streets. Her stage
  comes from her mother's feeding, not the age table, and she is the one body
  that matures in the field.
- **One sound take, pitched by size.** Pitch is the inverse of scale and volume
  0.85 / 1 / 1.15, so the creature stays recognisable at every stage. No new
  clips.
- **Art is a recolour, not new art.** Each Runner and Brute stage is a
  recoloured copy of the adult's bone sprites (`stage_sprites.py`, run by both
  exporters), so it poses on the same skeleton with no shader work.

## First balance reading (2026-10-21)

`commanderEvidence -Pmission=colony-breach`, four fixtures, each against the
`-Dbattle.swarm.growthStages=false` and `-Dbattle.swarm.packLeaders=false`
controls on the same commit. Every run evacuated 3 of 14 and the mother bore
never collapsed, so the readings are about tempo, not outcome:

| Fixture | Stages and packs | Stages off | Packs off |
| --- | --- | --- | --- |
| v1 | complete at tick 10749 | 11525 | 14827 |
| opp-hardpoints | 13858 (1 commit, 1 recall) | 13942 (2 commits, 1 recall) | 14158 (1 commit, 1 recall) |
| opp | 10749 | 11525 | 14827 |
| purge | 10749 | 11525 | 12104 (1 commit) |

With stages and packs on, the battle completes sooner in every fixture; taking
packs away moves it most. Why has not been traced. These are
starting values, not balance acceptance, which stays with
`rescue-pressure-acceptance.md`.
