# Mech battle doctrine overrides

Status: IN PROGRESS — the layered implementation and deterministic evidence
are complete; the live battle interaction pass remains.

Written: 2026-08-30

Updated: 2026-08-30 — made Long-Range Support visibly prefer a broad allied
front with a clear rear-oblique firing lane, including bounded standoff around
combat-zone assignments and mid-fight switches with partly spent LRMs.

Read `mechs-nouns.md`, `ai-nouns.md`, and `ui-nouns.md` before changing this
story.

## Goal

Let the player select an exact deployed friendly mech, choose how its whole
lance coordinates, and change how that member serves the current assignment.
Both choices are battle-local; doctrine may be cleared back to the role frozen
at deployment.

## Player contract

- The selected-Mech plate shows variant identity, deployed doctrine, and the
  currently effective doctrine.
- The plate visibly separates **Lance Order** from **Field Doctrine**. Form on
  Lead and Free Reign affect every member of the selected mech's battle lance;
  doctrine choices affect only the exact selected mech.
- Form on Lead is the default and preserves the existing cohesive,
  role-slotted lance maneuver. Free Reign releases generic formation steering
  and Brawler ally-distance tethering so every member follows its own doctrine.
- Brawler, Tank, Long Range Support, and Balanced are available in one click.
  Tank is the concise player label for the canonical Frontline Support doctrine.
- Reset Doctrine clears the battle override and restores the deployed doctrine.
- The control appears only for an exact live player mech. Enemy units,
  infantry, stale selections, and rescue-pickup mechs have no doctrine control.
- A click takes effect at the next serialized command phase and produces an
  immediate local replan. It does not rewrite the current assignment, reveal
  enemies, refill weapons, repair damage, or reset morale. A doctrine click
  does not change siblings; a lance-order click deliberately changes the whole
  battle lance.

## Doctrine acceptance

- A Form-on-Lead Brawler walks point inside the existing cohesive lead bound. A
  Free-Reign Brawler closes on the perceived enemy position without waiting on
  that generic ally bound, while remaining leashed to the current assignment.
  Returning to Form recalls a separated Brawler toward its live lance lead;
  mission authority may still constrain that recall.
- Frontline Support chooses a legal allied infantry or mech anchor, occupies
  its threat-facing side, and does not roam after contacts when no anchor
  exists. It may still hold, turn, and fire in self-defense.
- Long-Range Support seeks useful medium-to-long range, line of sight, and a
  credible allied screen. A legal screened position wins categorically over an
  unscreened one; cover and walking distance choose within those categories.
  The screen is a broad threat-facing front rather than an ally standing in the
  projectile ray, and every friendly body must remain clear of that ray. A
  close threat causes it to open distance and fight, not cling to a stale
  cached firing cell.
- Balanced approaches the middle or outer part of its installed direct-fire
  band, accepts a close threat already upon it, and returns to its assignment
  when that interruption ends. It does not require special allied geometry.
- Four different roles in one lance execute per member. No shared squad goal
  turns every member into the role with the highest goal priority.
- Free Reign removes generic lance formation only. Frontline Support still
  requires a legal ally anchor and Long-Range Support still values a credible
  screen because those relationships belong to their doctrines.
- Survival, rescue, and mission-command laws continue to outrank role manner.

## Deterministic evidence

- Focused tests cover deployed/effective/reset state, request validation,
  same-tick plan invalidation, sibling isolation, lance-wide order changes, and
  mixed-role execution.
- Field-shaped regressions switch a partly spent Bulwark from Tank to
  Long-Range Support ahead of an ordinary Marine screen, require a clear
  rear-oblique firing lane, make screening outrank a covered forward cell, and
  prove that a combat-zone order permits only a bounded standoff while the
  perceived enemy remains inside its assigned zone. A role round trip cannot
  erase an unfinished all-racks rearm cycle.
- Paired Brawler evidence holds hardware, contact, assignment, and seed fixed:
  Form on Lead stays within the lance lead bound while Free Reign separates and
  enters its close band. Formation tests prove Free Reign removes role-slot
  steering without removing physical collision separation.
- A bounded doctrine evidence scene uses identical hardware, assignment,
  contact, and seed for the four doctrines. It records threat distance,
  signed position relative to an allied screen, and the effect of removing that
  support midway through the run.
- Existing Sirocco overwatch, assault assignment, mech morale, formation, and
  campaign-freeze tests remain green.

## Out of scope

- Persisting a new default from battle or adding the Mech Lab authoring flow.
- Player-authored destinations, targets, waypoints, or knowledge.
- Per-member lance orders; coordination mode belongs to the battle lance.
- New chassis, weapons, damage rules, morale tuning, or campaign inventory.
- Treating the UI label Tank as a new hardware family.

## Exit

Keep the durable contracts in `mechs-nouns.md`, `ai-nouns.md`, and
`ui-nouns.md`. Move this story to `shipped.md` and delete it after focused
automation, deterministic evidence, and the live battle interaction pass all
agree.
