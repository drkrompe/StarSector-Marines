# Raid: wreck the works (first slice)

Status: IN PROGRESS

Written: 2026-11-09

Read `raid-mission.md` (Break), `raid-command.md` and `raid-resolution.md`
first.

## Intent

A raid to disrupt an industry plays as the owner's pick `raid-break`: wreck
the works. The battle reads the raid's intent, puts the industry's machines on
the map, and the share wrecked decides how long the industry stays broken.

## Scope

1. **Intent from the raid record.** The raid fork reads `temp.raidType`:
   `DISRUPT` carries the named industry into the briefing, and `VALUABLE`
   carries none, whatever industry its goods come from. (Today the fork passes
   the first objective's source industry, so a valuables raid on a commodity is
   briefed as a disrupting raid on the industry that makes it.)
2. **The works on the map.** A break Raid puts N machines (3, 4, 4, 5, 5 from
   first contract to full strength) in the map's industrial ground: the
   industrial zone's yards, lanes and docks and the buildings beside them, else
   the cordoned facility's buildings, else the Raid's own target candidates
   nearest one another. Each is reachable from the berth and indoors or in a
   yard, never on a doorway.
3. **The objective.** `RaidObjective` holds several sites. A site is wrecked
   by the existing service law (settled, uncontested, timed, reset on
   interruption). The way out opens with the first site wrecked; one settled
   Marine combatant at egress then ends the raid. A steal Raid keeps one site,
   so it plays as before.
4. **What it is worth.** The battle records the share wrecked with its victory;
   the commit multiplies the raid's effectiveness by it for a disrupting raid.
5. **Presentation.** The checklist and markers name the works and count the
   machines wrecked.

## Acceptance

- [ ] Unit tests: intent from raid type; site selection; multi-site objective
  (progress, reset, egress opens after the first site, completion, share);
  ledger and commit carry the share.
- [ ] Look shot of a break Raid's works with its machines marked, sent to the
  parent.

## Constraints

- No mission runs to judge the raid; prove rules with unit tests.
- Do not model the works as Sabotage sites: no stealth, no planting timer
  that hides the alarm.
- Story Raids (Dust Ledger, Agreus, District Escort, Cold Account) keep their
  authored single target.

## Exit

Fold the laws into `raid-mission.md` and `raid-command.md`, add a row to
`shipped.md`, delete this story.
