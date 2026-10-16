# S9 — In-battle quality conveyance

> The stats differ. The presentation does not. During the fight the player
> has no idea which of their marines is the veteran.

Status: IN PROGRESS — slices 1–2 shipped; the live feel pass decides slices 3–4.
Written: 2026-08-22
Updated: 2026-10-15 — slices 1–2 shipped and folded into `progression-nouns.md` (Presentation law).

## Problem

A Green/Limited marine and an Elite/Exceptional marine are meaningfully
different shooters and are rendered identically. The player's investment in
experience is invisible at the exact moment it is being spent.

There is also no in-fight feedback attributing anything to anyone: no
indication that *this* marine is the one landing rounds, taking the
casualties, or holding the flank.

## Scope split with S7

To stop the two stories from each inventing a marker:

- **S7 owns equipment-driven signal** — anything that comes from the weapon
  or armor the marine was issued.
- **S9 owns person-driven signal** — anything that comes from who the
  marine is and what they are doing right now.

## Shipped

Slices 1 and 2 (the squad panel readout and the hover readout) are shipped and
described in `progression-nouns.md`, Presentation law. Name, experience tier,
aptitude and HP were already on the card and its tooltip. The tooltip now adds
the mission record (rounds landed, kills, damage, status), and the top
contributor is marked on the card. *Suppressed* was dropped from the status
list: the simulation keeps no per-marine suppression state, and the panel does
not invent one.

## Remaining — live feel pass

Play a live battle at 1920x1080 and 100% UI scale, and confirm that:

- the mark and the mission line read as information rather than clutter;
- the 170px tooltip still clears the squad panel at UI scales 1.25 and 1.5.

That pass decides whether slice 3 runs at all.

## Slice 3 — Restrained in-world marker

Only if slices 1 and 2 prove insufficient.

The battlefield already carries fog-of-war state, squad selection,
objective markers, contact ghosts, and (with S7) equipment chrome. A
veteran chevron is defensible; a fourth overlay system is not. If this
slice runs, it should be one small persistent mark for the top experience
tier and nothing else.

**Recommendation: treat Slice 3 as opt-in after playtesting slices 1-2.**

## Slice 4 — Threshold moments

If S4's deferred live-XP question resolves toward wanting a mid-battle
beat, this is where it lives — a marine crossing into Veteran or Elite gets
a readable moment, while the actual stat change still settles
post-mission. Presentation of a threshold crossed, not a live stat mutation.

The `battle-radio-nouns.md` pipeline is the
obvious delivery vehicle and already has a global voice budget and a
presentation-only cue policy that keeps audio out of sim determinism.

## Out of scope

- Any sim behavior change. Conveyance only.
- Equipment chrome — `s7-grade-visual-identity.md`.
- Campaign-side roster views — `s8-roster-legibility.md`.
- Post-battle results presentation. The Results debrief already exists and
  gets its career payload from S3/S8.

## Acceptance

- During a live battle the player can identify their strongest marines and
  see what each is contributing, without pausing or leaving the battle
  screen.
- Zero sim reads of presentation state; determinism unchanged.
- No measurable frame cost in the render path; no per-frame `glGet`
  readbacks ([[async_renderer_bridge_glget_stall]]).
- Draw work goes through the existing batching path
  ([[render2d_batching]]) and the layered draw-list pipeline, not a bespoke
  overlay.
- Orbitron 20 floor respected ([[ui_font_minimum]]).
- In-game pass confirming the additions read as information rather than
  clutter. This is the whole risk of the story.

## Open questions

- Does the squad panel have room, or does this force a panel redesign?
  Worth checking before committing to slice 1's field list.
- Should enemy quality be conveyed too? Knowing you are fighting veterans
  is tactically meaningful and would pair with the shipped contact-belief
  and commander-influence work — but it is information the player arguably
  should have to earn through recon rather than read off a sprite.
