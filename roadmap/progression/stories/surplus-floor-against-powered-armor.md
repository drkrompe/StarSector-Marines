# Surplus floor against powered armor

Status: PLANNED — contracted from the S1 lethality verification.
Written: 2026-10-15

Read `progression-nouns.md` (Composed combat quality, the measured lethality
readings) and `power-suit-infantry.md` before changing anything here.

## Problem

Surplus issue is meant to be the harsh bottom edge of the ladder: weak but usable.
Against powered armor it is not usable at all. On the `loadoutMatchup` ladder
(three seeds, twelve a side, surplus field rifles in Cordon shells attacking across
open ground), the measured readings were:

| Defenders (service field rifles) | Defender wins | Stalemates | Defenders left | Exchange | Damage dealt by attackers |
| --- | ---: | ---: | ---: | ---: | ---: |
| twelve Foundry-Breaker rigs | 1/3 | 2/3 | 12.0 | 0.0 | 103 |
| twelve XIV shock suits | 1/3 | 2/3 | 12.0 | 0.0 | 63 |
| twelve XIV shock suits, in a trench | 3/3 | 0/3 | 12.0 | 0.0 | 48 |
| high-risk pirate mix (5 outlaw, 4 reaver, 3 Foundry-Breaker) | 2/3 | 1/3 | 11.3 | 0.1 | 91 |
| twelve outlaw shells (unpowered control) | 2/3 | 1/3 | 10.3 | 0.2 | 155 |

Twelve surplus rifles cannot kill one powered suit. Pirate rosters field
Foundry-Breaker rigs at 5% of seats at medium risk and 25% at high risk, so a
surplus company that takes a high-risk pirate job meets a wall it cannot see
coming. That is the "silent unwinnable state" the S1 acceptance ruled out.

## Measured target

With the same fixture (surplus field rifles in Cordon shells, three seeds, open
field), against twelve Foundry-Breaker rigs on service field rifles:

- the attackers deal at least a third of one suit's armor-and-structure per fight
  on average, so focused fire visibly works; and
- they kill at least one rig in at least one of three fights.

They should still lose. The high-risk pirate mix should stay a loss for a surplus
company, with an exchange above 0.3 rather than 0.1.

## Constraints

- Do not raise the surplus grade across the board: the grade ladder's 3.5x span and
  the band calibration (`progression-nouns.md`) are guarded by `TtkReportTest`.
- Prefer the answer the catalog already intends for armor: the starter or early
  Common issue carrying something with penetration (a rocket tube, an
  anti-materiel round, grenades that matter against shells). A starter card that
  can answer one heavy is a way of fighting; a better rifle number is not.
- Alternatively, make the wall legible: the mission board and briefing name
  powered opposition for a high-risk pirate job. That answers "silent" without
  answering "unwinnable", so it is the weaker option.

## Acceptance

- The rerun table above meets the measured target, with the starter-vs-starter
  control row shown beside it from the same run.
- `TtkReportTest`'s guarded comparisons still pass.

## Plan

1. Decide between a starter penetration special and briefing legibility (or both).
2. Rerun the ladder and record the table.
3. Fold into `progression-nouns.md`.
