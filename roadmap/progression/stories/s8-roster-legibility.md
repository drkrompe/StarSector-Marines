# S8 — Roster legibility: live acceptance

Status: IN PROGRESS — the Company Roster, captain trait surface and role-based type
faces have shipped (`progression-nouns.md`, Presentation law); live UI-scale
verification and the in-game feel pass remain.
Written: 2026-08-22
Updated: 2026-10-15 — narrowed to the live checks after the roster shipped.

## Remaining acceptance

- Open the Barracks' Company Roster in game at UI scale 1.0x, 1.25x and 1.5x and
  confirm every column stays readable: no clipped names, kit or service text, and
  the aptitude median outline visible on the step it marks.
- Numeric columns: the service record is prose ("4 ops · 0 kills · 0 wounds"), so
  digit alignment is not yet tested. If comparing service records by eye proves hard,
  split it into three right-aligned numeric columns.
- In-game feel pass on density. Headless evidence is
  `createSnapshots -Psnapshot=ui` (`barracks-roster-wide.png`, 1920x1080 at 100%).

## Open questions

- Should rank-and-file marines carry traits (a specialist mark earned in the
  field)? Deferred to `s10-trait-mechanics.md`; the roster row has room for one
  more column.
- Does the career readout want a per-mission timeline rather than lifetime totals?
  Decide once the roster's totals have been used in play.
