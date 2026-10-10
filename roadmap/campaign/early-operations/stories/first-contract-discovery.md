# Jangala first-contract discovery

Status: READY FOR LIVE ACCEPTANCE
Written: 2026-10-10
Updated: 2026-10-10 — finite Wayline chapters, closing correspondence and separate recurring offers; live acceptance remains.

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
Complete Counterattack and verify the HQ lead retires while Venn preserves the completed chapter record
and offers distinct Militia Relief / Depot Clearance jobs alongside other brokers.
Neither old story ID may be offered again; defeat leaves the current chapter retryable. Check a preexisting save
with opening victories, fallback routing without accessible Jangala, and the
no-accessible-office state. Verify that Plot Course dismisses the HQ cleanly from
both campaign-map and planetary hosts without launching combat.

Focused eligibility, progression, settlement-source and UI tests plus assembly and
shipped-jar boundary checks cover the implemented path. The first-contract
snapshot catalog includes chapter offers, victory/defeat correspondence, the
completed dossier, repeatable postings, referral, fallback and unavailable office
states at 1920x1080 and 100%. These controlled headless fixtures do not establish
live navigation or save/reload acceptance. Battle pacing, force ratios and
objective readability remain in `opening-ladder-live-acceptance.md`.
