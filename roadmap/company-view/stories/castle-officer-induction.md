# Castle's officer induction

Status: IMPLEMENTED — live campaign acceptance open
Written: 2026-10-11
Updated: 2026-10-11 — Persistent induction, compact SVG progression controls and Mira's dedicated portrait implemented.

Alpha pickup under `company-view-nouns.md`, `personnel-nouns.md` and
`narrative-nouns.md`.

Castle introduces Lieutenant Mira Hale as the company's recurring first field
commander. Her portrait popover spotlights the real officer dossier, specialty
training and home-command controls. Progress follows completed orders, not Next
clicks. The first ordinary deployment requires this induction; existing saves
and detached debug companies remain exempt.

Mira receives the ordinary appointment training point. The player chooses its
specialty permanently. Raising her squad retains the normal cargo-backed founding
bill; the introduction creates no free marines, equipment or machines. Castle is
a pragmatic matriarch: warm, responsible and direct. Mira remains an ordinary
mortal officer with her own service history and promotion-driven progression.

Acceptance:
- New-company induction persists across save/load and cannot be skipped by
  dismissing the popover or using another deployment entry point.
- Compact training actions pair specialty SVG emblems with two visible rank markers; no full-width training buttons. Castle's lesson shows five SVG milestones.
- Mira uses a dedicated female portrait; legacy default Mira portraits upgrade without replacing customized portraits or imposing induction.
- Real clicks open Mira's dossier, train one specialty, found a squad in Armory
  if necessary, and assign it in Barracks. Multiple spotlight holes preserve
  the relevant controls and scrolling.
- Dismissal permits resupply; Company Guide resumes the unfinished lesson.
- The blocked briefing offers a route to induction and preserves the selected
  mission for return. Unrelated first-campaign mission design remains separate.
- Focused state tests and retained UI snapshots exercise the sequence and gate.
- Live campaign save/load and enlarged-scale interaction acceptance remain
  explicit follow-up checks; this text lesson does not claim recorded voice.

Remaining acceptance is the live new-company sequence, campaign save/reload at
specialty and founding boundaries, and the return to the originally selected
briefing. Headless evidence walks real spotlight controls at 100% and 150% scale,
including wheel scrolling to the third specialty and actual command assignment.
It substitutes the Armory screen with its ordinary founding authority and does
not establish a live cross-room transition. Recorded officer-induction audio
remains a separate cast-production pass.
