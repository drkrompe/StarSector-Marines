# ECS story board

Status: ACTIVE — five bounded acceptance or presentation follow-ons remain open.

Written: 2026-08-23

The ECS storage and identity migration is shipped. This board contains only
bounded work that remains genuinely open; completed migration slices are in
`shipped.md`.

| Story | Status | Next outcome | Gate |
| --- | --- | --- | --- |
| `vehicle-control-lifecycle-playtest.md` | READY FOR ACCEPTANCE | Human playtest of the full incoming → dock → reverse → departing vehicle lifecycle after the stateless, id-keyed cutover. | Observe the complete lifecycle in a real battle; S6 rename remains explicitly optional. |
| `authored-walk-animation.md` | PARKED | Add optional walk-cycle animation to the authored appearance flow. | Usable walk-cycle sprite sheets and a scoped acceptance plan. |
| `secondary-aim-facing.md` | PROPOSED | Finish the existing secondary-aim facing contract with an explicit absent/dead-target fallback. | Choose the fallback and cover the live, absent, and dead target paths. |
| `fx-child-entities.md` | PARKED | Decide whether muzzle, smoke, and impact effects should become lifecycle-owned child entities. | A separate effects design; do not subsume current working effects opportunistically. |
| `fire-stance-normalization.md` | PARKED | Normalize moving/stanced firing semantics as a deliberate behavior change. | A dedicated playtest/acceptance scope defines intended stance semantics before code changes. |

No generic “systems to columns” continuation is queued. The prior conversion
reached its positive-value terminus: a candidate must bring a current profile,
a behavior-preserving data-only walk, and a concrete consumer before it belongs
on this board.
