# Campaign personnel

Persistent personnel turn the battle roster into a mercenary company rather
than an anonymous marine count. Rank-and-file marines keep stable identities,
belong to six-person fireteams, carry armory allocations into battle, and
return with individual experience and dispositions.

## Shipped foundation

- `MarineRoster` owns serializable soldiers, fireteams, the reserve pool, and
  the shared `MarineArmory`. Legacy saves backfill squad membership through
  `readResolve`.
- The initial company complement is issued once. Later enlistment consumes one
  unassigned marine from player cargo; demobilizing a ready reservist returns
  one marine to cargo.
- The canonical briefing can select whole fireteams. Only their ready members
  overlay player-owned shuttle seats; employer seats retain scenario-authored
  personnel.
- Battle identities return as RTD, WIA, MIA, or KIA. WIA recovery advances on
  the campaign clock, while the Results screen presents a fireteam debrief.
- Armory equipment remains finite and allocation-aware. Whole-fireteam presets
  apply atomically or report why the issue cannot be completed.

## Invariants

- Tactical battle squads remain ephemeral; campaign fireteams are the durable
  organizational layer.
- Explicit squad selection fails closed. A stale or unavailable selection must
  never silently substitute personnel from another fireteam.
- Named personnel leave generic cargo when enlisted, so battle casualties must
  not also remove anonymous cargo marines.
- Employer personnel never acquire player campaign identities.
- Casualty disposition is deterministic for a frozen mission outcome.
- KIA and MIA open replacement billets; WIA retain their billet until recovery.

## Active story

[`stories/captain-fireteam-command.md`](stories/captain-fireteam-command.md)
locks the durable command relationship: one home captain per line fireteam, a
rank-scaled formation of whole fireteams, and the briefing-selected captain as
the authoritative mission commander. Home command supplies defaults but does
not strand squads when their captain is unavailable.
