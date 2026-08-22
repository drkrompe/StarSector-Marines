# S1 — Lethality floor and tier spread

> Firefights should resolve. Upgrades should be felt.

**Status:** not started. No dependencies — start here.

## Problem

Two measured facts from [`../audit.md`](../audit.md):

- A 25 HP marine under sustained pulse-rifle fire at a realistic ~40%
  landed-round rate takes **roughly 20 seconds** to drop. Engagements are
  attritional mush; positioning, cover, and all of the
  [`../../ballistics/`](../../ballistics/overview.md) fidelity get averaged
  away by sheer time-on-target.
- The **entire** equipment ladder moves damage by 8-13% and range by 16%.
  Milspec over Service is a rounding error. Masterwork — the chase item —
  is a 3% damage step over Milspec.

## Goal

1. Cut time-to-kill hard enough that a firefight is decided by who shoots
   first, from where, with what — not by who has more seconds.
2. Widen `EquipmentGrade` so each step is a recognizable power tier.

## Slice 1 — Lethality budget

Define target time-to-kill as the authored contract, then solve the numbers
for it rather than nudging constants.

Proposed targets (Service grade, Regular experience, single shooter,
50% of effective range, target in the open):

| Target | Target TTK |
| --- | --- |
| Unarmored marine / militia | 2.5 - 4 s |
| Mid armor (T2/T3) | 4 - 6 s |
| Heavy armor (T4) | 7 - 10 s |

Behind hard cover, the same shooter should be looking at 2-3x those
numbers — cover becomes the reason a fight lasts, instead of HP pools.

### Levers, and the trap

Both HP and damage have to move together. Raising weapon damage alone
breaks recently-tuned relationships; lowering HP alone changes the meaning
of every armor bonus.

**This slice must re-derive, not merely inherit:**

- `UnitType.MARINE.maxHp` (25) and every other infantry-class HP in
  `UnitType`.
- Alien / swarm HP. Living-world rescue tuning drove generic aliens to
  **1.875 HP** and runners to **2.5 HP** through several passes explicitly
  to make marine weapons feel lethal against them. Those numbers were
  chosen against *today's* damage values and will invert if damage moves.
- `MarineArmorPattern.bonusHp` — +8 HP is 32% of a 25 HP marine. Against a
  smaller pool it becomes dominant; express armor bonus HP as a fraction of
  baseline, or retune every entry.
- `MarineSecondary.ROCKET_LAUNCHER.damage` (18) and its
  `wallDamagePerHit` (50), plus `MapTurret` and mech HP, so anti-materiel
  weapons keep their identity relative to small arms.
- Structural / wall HP, which shares the same damage scale.

Recommended approach: pick **one global infantry damage scale factor**,
apply it uniformly, then hand-correct the handful of entries whose *ratio*
to small arms should change. A uniform scale keeps every existing
relationship intact by construction and makes the diff auditable.

## Slice 2 — Grade spread

Widen `EquipmentGrade` so grade is a tier, not a trim level. Starting
proposal (tune against Slice 1's TTK harness):

| Grade | range | damage | accuracy | cooldown | spread |
| --- | --- | --- | --- | --- | --- |
| Surplus | x0.90 | x0.80 | x0.85 | x1.20 | x1.30 |
| Service | x1.00 | x1.00 | x1.00 | x1.00 | x1.00 |
| Milspec | x1.10 | x1.30 | x1.10 | x0.88 | x0.78 |
| Masterwork | x1.20 | x1.65 | x1.20 | x0.78 | x0.60 |

Damage span becomes ~2.06x (from 1.14x); compounded with accuracy and
cooldown, effective DPS across the ladder spans roughly 3x. Surplus becomes
a genuine liability rather than a mild discount, which gives the early
company a reason to care about the armory at all.

Second-order effects to check in the same slice:

- Grades apply to **defenders and employer soldiers** too
  (`InfantryLoadoutRolls`, `DefenderRoster`). A wider spread makes
  risk-scaled enemy rosters swing harder. Verify HIGH-risk defender
  composition is still winnable and LOW is still trivial.
- `ExperienceTier` (0.92 - 1.13) and `SoldierAptitude` (0.92 - 1.13) now sit
  an order of magnitude below grade in impact. S4 rebalances the experience
  ladder; **do not** widen it here, or the two passes will fight.

## Out of scope

- New weapon families — that is [S2](s2-weapon-catalog-expansion.md).
- Any change to how a round resolves. `../../ballistics/` S1-S4a semantics
  are settled and stay settled.
- Visual differentiation of grades — that is
  [S7](s7-grade-visual-identity.md).

## Acceptance

- A repeatable **TTK harness** exists — debug-panel or test-side — that
  reports measured time-to-kill per (weapon, grade, experience, target
  armor, range fraction, cover state). This is the artifact that makes the
  tuning pass reviewable and re-runnable, and it is a deliverable, not a
  scratch script.
- Measured TTK lands inside the target table above.
- Alien/swarm, mech, turret, and wall relationships are explicitly
  re-derived and recorded in `complete/`, with before/after values.
- Existing living-world rescue and early-operations scenarios still resolve
  to their intended outcomes; both have hand-tuned force ratios that this
  change perturbs.
- In-game feel pass before the story closes. This one cannot be signed off
  from tests.

## Open questions

- Should Surplus at x0.80 damage be *worse than nothing* in a way that
  makes the player retire it, or should it stay a viable budget option for
  a large green company? Leaning: viable but visibly poor, since scale
  inefficiency already punishes fielding a big cheap company.
- Does the TTK target differ for player marines versus AI defenders?
  Leaning no — symmetric lethality is easier to reason about and reads as
  fair.
