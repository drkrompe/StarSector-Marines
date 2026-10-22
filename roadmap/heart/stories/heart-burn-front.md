Status: IN PROGRESS (behind `-Dbattle.heart.burnFront=true`)

Written: 2026-10-22

Updated: 2026-10-22 — built behind the switch and measured; armour mix and sector width are with the owner (`heart-burn-front-armour`, `heart-burn-company-sector`); hulls now driven by the shared allied commander (`allied-command.md`); Hounds stay mission-driven until it stations mechs.

# The Burn's front

Model: `heart-nouns.md` (Allied front, Battlegroup, Company sector; The Burn's
flow). Related: `bloom-mission-arc.md` (each movement larger and more allied),
`cordon-allied-columns.md` (the general allied column this should converge on).

## Why

Measured on seeds 202-606, The Burn's company loses on every seed with every
single lever (following the barrage, frontage, forming on the farmland, six
shuttles, calmer bores, weaker Spitters): the swarm's numbers beat 24 marines,
not any one creature. Allied columns that follow the barrage were the best
experiment (two satellites of three on one seed). The owner's direction: "a
proper battlefield with the player's forces playing a more localized role",
with allied armour.

## Scope

- **The front.** The whole approach is divided across into sectors. The
  company holds one, centred on its landing; allied battlegroups hold the rest.
- **Battlegroups.** Each is militia infantry with armour: an escort APC, a
  technical, a breakthrough tank and a Hound pair in every group (the heavy
  mix, measured better; the standard mix keeps tanks beside the company and
  Hounds on the outer flanks). Each owns its sector's
  satellites, then goes on to the nearest mother.
- **Following the barrage.** Every battlegroup follows the guns exactly as the
  company does (`BarrageScreen`): it forms on the farmland until the first line
  lifts, then stands short of the firing line and comes up as each line lifts,
  and goes for its bore once the barrage has passed it. Armour steps with its
  infantry and lets its guns work.
- **The company's sector.** The company's lane narrows to its sector: its own
  satellite first, then the nearest mother.
- **Allied rules hold.** Battlegroups are `ALLY`: they never end the battle and
  never touch the roster. Won when the heart is down, lost when the company is
  broken or the battery overrun, as today.

Out of scope: allied waves landing behind lifted lines, the APC carrying and
deboarding its riflemen (they walk beside it), the general allied line
commander of `cordon-allied-columns.md`.

## Acceptance

- Behind the switch, five seeds against today's main: deaths by beat,
  satellites and mothers down, whether the heart falls.
- A 1920x1080 whole-map frame of the front and a close-up of the company's
  sector.
- Look questions (armour mix and density, sector width) to the owner as picks.

## Measured (seeds 202-606, against main)

Main: no satellite down on any seed, the company broken by ticks 6,900-22,800.
Standard front: 1 satellite (404); company broken later (15,000-26,000), 303
times out. Heavy front: 3 satellites on 404, 1 on 202, 303 and 505; swarm
killed nearly doubled. No mother is touched in any run: the next step is the
company and the front reaching the mothers.

## Plan

1. Battlegroup spawning and ordering on the barrage screen (`HeartLanes`
   extended with armour and the screen). Hulls are deployed and stationed
   through `AlliedCommand` (`allied-command.md`); each post is the lane's
   across, short of the hold. Hounds stay on the lane's own squad orders until
   the allied commander stations mechs.
2. The company's sector width in `BloomHeartSetup`.
3. Measure, render, picks.
