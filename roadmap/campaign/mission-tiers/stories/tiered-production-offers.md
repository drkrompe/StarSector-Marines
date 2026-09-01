# Tiered production offers

Status: IN PROGRESS

Written: 2026-08-23

Read `mission-tier-nouns.md` before implementing this story.

## Goal

Give ordinary patron work a campaign-authored force envelope: persist scale
from the patron's place in the campaign, keep risk as target pressure, and make
the offer honest for a company that may field anything from one equipped squad
to a larger body of green replacements.

## Acceptance

- Production-generated and contract missions explicitly carry their selected
  tier through every mission boundary.
- Standard Tier-1, Tier-2, and Tier-3 patron work selects First Contract,
  Established, and Veteran scale respectively. Planetary Assault is clamped to
  its Reinforced floor. Legacy rows without a stored tier use the same policy
  once and do not fall back to target risk.
- Offer policy never produces a type below its tier floor.
- Risk remains a separately derived within-tier pressure signal.
- The recommendation ladder agrees with the campaign/debug company ladder:
  1, 3, 6, 17, and 34 squads.
- Tier-1 patrons may offer both existing low-scale Strike and Escort work, so
  the opening contract ecosystem is not one repeated mission shape.
- Mission dossiers and briefings name the scale and recommended squad count.
  Briefing compares selected issued-experience bands with an honest, coarse
  opposition-quality expectation; quantity and quality remain separate facts.
- An ordinary generated one-shot mission requires at least one ready fire team,
  not every seat in the recommended lift plan. Launch freezes only the selected
  named marines and trims the player lift manifest to those personnel, so an
  understrength deployment neither recruits automatically nor invents generated
  substitutes in unused seats.
- Story, event, stationing, debug, and Conquest deployment gates retain their
  existing authored behavior.
- Debug and authored event/story missions retain explicit, explainable tier
  choices rather than inheriting production policy accidentally.

## Out of scope

Retuning defender populations, mission-type weights, payout/salvage curves,
Conquest arrival law, or compatibility paths that still serve headless callers.
