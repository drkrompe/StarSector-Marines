# Defense-intensity feel pass

Status: READY — implementation is shipped; manual play acceptance remains.
Written: 2026-08-23

Read `campaign-battle-bridge-nouns.md` before running or changing this
validation.

## Purpose

The target profile demonstrably changes generated overwatch candidates, but
the final question is experiential: does attacking a fortified market feel
meaningfully harder than attacking a soft target without making a small
operation unreasonable after battle-start force balancing?

## Validation pass

Play comparable operations against low- and high-defense markets with the same
risk and similar attacking strength where practical. Observe the realized
overwatch line, weapon weight, approach readability, and whether the attacking
force leaves enough budget for the target-world difference to reach play.

Record only observable failures and their mission/market context. If tuning is
needed, contract a separate bounded story against the standing bridge and
force-balance laws; do not reopen the retired implementation story.

## Acceptance

- A fortified market reads as more strongly defended when attacker budget can
  support that distinction.
- Static turret armament remains subordinate to the battle-start force budget,
  so small unsupported attacks are not overwhelmed merely by target selection;
  drone hubs remain outside this admission boundary.
- The approach remains playable and the defense difference is legible through
  placement and weapon weight rather than unexplained difficulty.
- Any required tuning is captured with observed evidence and a bounded target.

## Out of scope

- Automated tests for a manual encounter-feel judgment.
- New hard-installation terrain, defender roster rules, or economic districts.
- Speculative constant changes without a reproduced in-game failure.
