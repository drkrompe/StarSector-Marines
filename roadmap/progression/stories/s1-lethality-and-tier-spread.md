# S1 — Lethality floor and tier spread

> Firefights should resolve. Upgrades should be felt.

**Status:** Slice 0 (TTK harness) is shipped and the baseline is measured —
see "Measured baseline" below. Slices 1 and 2 are not started.

## Problem

Two measured facts from [`../audit.md`](../audit.md):

- A 25 HP marine under sustained pulse-rifle fire at a realistic ~40%
  landed-round rate takes **roughly 20 seconds** to drop. Engagements are
  attritional mush; positioning, cover, and all of the
  [`../../ballistics/`](../../ballistics/overview.md) fidelity get averaged
  away by sheer time-on-target.
- The **entire** equipment ladder moves damage by 8-13% and range by 16%.
  Milspec over Service is a rounding error. Masterwork — the chase item —
  is a 3% damage step over Milspec. *(Slice 0 measurement partly walks this
  back: the damage figure is right, but grade also moves cooldown, accuracy
  and spread, and the compounded effect on TTK is 1.75x. See "Measured
  baseline".)*

## Goal

1. Cut time-to-kill hard enough that a firefight is decided by who shoots
   first, from where, with what — not by who has more seconds.
2. Widen `EquipmentGrade` so each step is a recognizable power tier.

## Slice 0 — TTK harness *(shipped)*

`TtkHarness` + `TtkReportTest` under
`src/test/java/.../battle/balance/`. A scenario spawns two real units in an
arena and runs the shipped firing sub-phases — `FiringSystem`,
`InfantryWeapons` burst continuation, the pending-impact drain — until the
defender dies. No AI, no movement, no return fire, no morale.

It drives the real pipeline rather than modelling it because effective
lethality is not `damage / cooldown`: range falloff, lateral spread,
target-plane aim, directional cover re-expressed as physical interception, the
muzzle-proximity ramp on that interception, the cover damage curve, and the
armor package all sit between the trigger and the HP write. A closed-form
model would drop most of that.

It is statistical, not deterministic — `InfantryWeapons.fireShot` samples
`ThreadLocalRandom` because it runs inside the parallel unit dispatch, so a
trial cannot be seeded from the harness. Precision comes from trial count
instead, and every row carries the standard error of its own mean. **Compare
against bands, never against exact values.** The report lands at
`build/reports/balance/ttk-baseline.md`.

## Measured baseline

Every number below is from that harness at 120 trials/row: Service grade,
Regular/Steady soldier, 50% of effective range, defender in the open,
stationary shooter, no return fire. Standard error is ≤0.6 s except where
noted.

### The floor is worse than the audit estimated

| Weapon | vs militia | vs unarmored marine | vs T3 armor | vs T4 armor |
| --- | ---: | ---: | ---: | ---: |
| Field Rifle (11c) | **18/120 kills** | **0/120** | **0/120** | **0/120** |
| Pulse Rifle (12c) | 16.1 s | 30.2 s | 39.0 s | 47.1 s |
| Light MG (8c) | 11.2 s | 19.7 s | 25.6 s | 30.2 s |
| Railgun (16c) | 12.1 s | 25.1 s | 31.8 s | 38.2 s |

The audit's "roughly 20 seconds" was optimistic; the pulse rifle needs
**30 seconds** against an unarmored marine and **47** against T4. Aliens
(1.9 effective HP) and swarm runners (2.5) die in 1.8–2.9 s, which is the
living-world tuning working as intended and the reason those HP values cannot
simply ride a global damage scale.

**The Field Rifle cannot kill a marine.** Not "slowly" — 0 of 120 trials
inside a two-minute timeout, and only 18 of 120 against 15 HP militia. At
0.85 damage, a 1.45 s cycle and a ~21% landed rate it deals about 0.12 DPS,
so 25 HP is a ~200-second proposition. Recruit issue is not a weak starting
option, it is a non-functional one: a new company's marines are spectators
until someone hands them a pulse rifle.

### The grade ladder is already wider than the multipliers suggest

Pulse rifle vs an unarmored marine:

| Grade | TTK | vs Service |
| --- | ---: | ---: |
| Surplus | 40.0 s | +36% |
| Service | 29.5 s | — |
| Milspec | 26.2 s | −11% |
| Masterwork | 22.9 s | −22% |

**Surplus → Masterwork is 1.75x in measured TTK**, not the 1.14x the damage
multiplier alone implies. The audit's "8–13% end to end" measured `damageMult`
in isolation; grade also moves cooldown (1.10 → 0.88), accuracy and spread,
and those compound. Slice 2's proposed table is therefore a bigger change than
it looks — it should be re-derived against this measurement, not against the
audit figure.

### Experience and aptitude are *not* an order of magnitude behind grade

Pulse rifle, Service grade, vs an unarmored marine:

| Soldier | TTK |
| --- | ---: |
| Green / Steady | 36.9 s |
| Regular / Steady | 30.5 s |
| Veteran / Gifted | 25.1 s |
| Elite / Exceptional | 20.8 s |

That is **1.77x**, matching the equipment ladder almost exactly. Slice 2's
premise that the soldier ladder "sits an order of magnitude below grade" is
wrong as measured — though note this row varies *both* axes together, which
is how a real veteran presents. Widening grade without touching this will
still leave both ladders comparable; widening it as far as Slice 2 proposes
would make equipment clearly dominant, which is a design choice to make
deliberately rather than by accident.

### Cover is already the strongest single lever

Pulse rifle vs an unarmored marine, cover facing the shooter:

| Cover | 25% range | 50% range | 90% range |
| --- | ---: | ---: | ---: |
| open | 27.2 s | 29.5 s | 35.7 s |
| light | 36.4 s | 41.0 s | 48.7 s |
| solid | 50.4 s | 60.0 s | 68.1 s |
| hard | 74.5 s | 92.2 s *(118/120)* | 102.8 s *(86/120)* |

Open → hard is **3.1x**, and hard cover at long range stops resolving inside
two minutes at all. The design intent — "cover is the reason a fight lasts" —
is already true in ratio. What is broken is the floor it multiplies: 3x of
30 seconds is a stalemate, not a decision. **Lowering the floor is the whole
job; the cover curve itself needs no widening**, and may need narrowing once
the floor drops.

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
- `MarineWeapon.FIELD_RIFLE` specifically. A uniform global scale keeps it
  non-functional — 0.12 DPS scaled by even 6x is still 20+ seconds against an
  unarmored marine. Recruit issue needs its *ratio* to the pulse rifle
  corrected, not just the shared scale: it is currently 0.85 damage on a
  1.45 s cycle against the pulse rifle's 3 x 1.0 on a 1.0 s cycle, which is a
  5.1x DPS gap dressed up as a starter option.
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
- `ExperienceTier` (0.92 - 1.13) and `SoldierAptitude` (0.92 - 1.13) are
  **not** an order of magnitude below grade, as this slice originally
  assumed — measured, Green → Elite/Exceptional is 1.77x against grade's
  1.75x. Widening grade to the table above makes equipment clearly dominant
  over who the soldier is. That may still be the right call, but it is now a
  deliberate choice rather than a correction. S4 rebalances the experience
  ladder; **do not** widen it here, or the two passes will fight.

## Out of scope

- New weapon families — that is [S2](s2-weapon-catalog-expansion.md).
- Any change to how a round resolves. `../../ballistics/` S1-S4a semantics
  are settled and stay settled.
- Visual differentiation of grades — that is
  [S7](s7-grade-visual-identity.md).

## Acceptance

- ~~A repeatable **TTK harness** exists that reports measured time-to-kill per
  (weapon, grade, experience, target armor, range fraction, cover state).~~
  Shipped as Slice 0; see "Measured baseline" above for what it found.
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
