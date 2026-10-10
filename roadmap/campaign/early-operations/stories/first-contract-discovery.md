# Jangala first-contract discovery

Status: READY FOR LIVE ACCEPTANCE
Written: 2026-10-10
Updated: 2026-10-10 — named broker, location policy, linked briefings and headless flow implemented.

## Scope

Mara Venn, an Independent broker based canonically on Jangala, introduces the
company to Relief and Counterattack for the Wayline freight cooperative. HQ
shows her portrait, office, payment, local menu route and Plot Course. Other
Independent brokers refer new companies to her; both opening victories unlock
their recurring militia work. Existing story completion survives unchanged.
The shared office policy provides an accessible planetary fallback when needed.

Canonical model: `early-operation-nouns.md`; presentation boundaries:
`company-view-nouns.md`. Alpha gate: `v0.1-alpha.md`.

## Acceptance

- Jangala takes precedence over Independent markets when accessible.
- Hidden, hostile, player-owned and non-planetary markets cannot be fallback offices.
- Stable market ordering makes fallback selection repeatable across save/reload.
- Relief exists at the office regardless of seed or company size; Counterattack
  requires Relief victory. Defeat never sets a completion fact.
- Other brokers offer referrals before both victories and recurring work after.
- Patron clients and locked clients cannot borrow the opening sequence.
- HQ distinguishes no squads, first work, follow-up and completed introduction.
- No available office is explained and cannot be plotted.
- The production HQ and contract board render at 1920x1080 and 100% UI scale.

## Remaining live acceptance

From a normal new save, follow HQ's Plot Course to Jangala. Open Marine operations,
choose Mara Venn, inspect her affiliation and Relief, and confirm normal deployment
requirements. Save/reload before and after Relief; confirm Counterattack remains
available and her briefing acknowledges the same militia and cooperative.
Complete Counterattack and verify the HQ lead retires while Venn retains the jobs
and other Independent brokers offer recurring local work. Check a preexisting save
with opening victories, fallback routing without accessible Jangala, and the
no-accessible-office state. Verify that Plot Course dismisses the HQ cleanly from
both campaign-map and planetary hosts without launching combat.

The focused suite passes 24 tests, including the retained course-button interaction.
Assembly and the shipped-jar boundary pass.
`createSnapshots -Psnapshot=first-contract` supplies nine controlled headless HQ and
contract-board frames, including referrals, fallback and unavailable offices. It does not establish live navigation or save/reload
acceptance. Battle pacing, force ratios and objective readability remain in
`opening-ladder-live-acceptance.md`.
