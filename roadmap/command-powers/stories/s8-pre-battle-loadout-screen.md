# S8 — Pre-Battle Loadout & Briefing Acceptance

Status: READY — implementation is shipped; manual mixed-detachment acceptance remains.

Written: 2026-05-30

Updated: 2026-08-23 — folded shipped slices and narrowed the story to live-play acceptance.

Read `command-powers-nouns.md` before running this story.

## Goal

Confirm in live play that the canonical briefing makes fleet commitment and
command-deck selection understandable, and that the launched battle receives
exactly the support the player chose.

## Contract under test

- The mission dossier hands off through **Brief & Deploy**; it does not expose a
  second commitment editor.
- **Your Fleet Brings** owns independently committable transports, fighter
  carriers, and power-source ships. **Employer Provides** remains a distinct,
  read-only co-source.
- Withholding or restoring one power-source ship removes or restores only the
  powers contributed by that member.
- The command deck accepts only available cards that fit its budget. Slotting
  changes what enters battle; it does not spend the displayed at-use resources.
- Deploy resolves the committed detachment once. The battle roster, shuttle
  manifest, and fighter cover match the final briefing choices.

## Manual acceptance

1. Open a mission from its dossier and confirm **Brief & Deploy** reaches the
   full-canvas briefing with no commitment controls left on the dossier.
2. Use a fleet with more than one power source plus eligible transport and
   fighter support. Withhold individual source ships and confirm their unique
   cards disappear; recommit them and confirm the cards return without
   disturbing unrelated contributions.
3. Verify player contributions and employer offerings remain visually distinct,
   including a mission where both sources contribute support.
4. Build a mixed command deck near the budget limit, change the transport and
   fighter commitments, choose a captain, and deploy.
5. In battle, confirm the visible command-power roster is exactly the selected
   deck and that only the committed shuttle/fighter support arrives.
6. Back out once before deployment and confirm returning to the mission list
   does not launch or retain a stale battle setup.

## Acceptance

The screen is legible without relying on implementation knowledge, source
withholding changes availability predictably, and every launched support kind
matches the final briefing state. Record any feel or correctness failure as a
new focused story; once the pass succeeds, fold and delete this story.

## Out of scope

- The command-level capacity curve (`s5-command-level-progression.md`).
- New landing-zone, air-defense, or craft-risk mechanics
  (`s6-drop-geography.md`).
- New command-power behaviors or balance tuning beyond a defect discovered by
  this acceptance pass.
