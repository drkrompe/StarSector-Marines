# Body-part consequences

Status: IN PROGRESS — implementation under way.

Written: 2026-10-15

Read `ballistics-nouns.md`, `body-part-cover.md` ("Part consequences"),
`combat-durability-nouns.md` and the Unit modifiers section of `ai-nouns.md`
first.

## Scope

A direct round that lands on a person already carries the body part it struck
to the durability resolver (`body-part-exposure.md`), but every part does the
same damage. This slice makes the part matter:

- **Part damage multipliers.** Each part carries a damage multiplier, applied
  to a payload that has a part before mitigation and armour. Head worth more,
  limbs less, calibrated so the share-weighted mean on an uncovered body is 1.00
  (within 0.02): in the open the expected damage is unchanged; behind cover,
  where the exposed parts skew toward head and arms, it shifts.
- **Limb wounds.** A legs or arms hit that removes structure (it got through
  any armour) leaves a wound. Leg wounds slow the man, arm wounds cost him
  accuracy, each through a named unit modifier, stacking to a floor. A head hit
  is damage, not a wound. Wounds last the battle unless dressed.
- **Dressing.** A squad medic's completed treatment dresses the patient's
  wounds.
- **Telemetry.** Battle-wide tallies of part hits and wounds by the struck
  side, readable by scenes and reports; the `fighting-position` scene reports
  them as readings.

Out of scope: per-part armour, area exposure, AI scoring of wounds or of
expected exposure, wound presentation beyond the modifier's name, mech hit
locations.

## Constraints

- Multipliers live on `BodyPart` beside the shares; nothing else re-derives
  them.
- Armour stays one whole-body layer; the part multiplier acts on the payload,
  so penetration and armour efficiency are unchanged.
- Partless payloads (area bursts, strafes, unattributed, hulls, creatures) are
  untouched.
- Wound effects are unit modifiers read through `UnitModifiers`, never a branch
  at a read site.
- Wounds are written only on the serialized damage path and the serial medic
  pass; the parallel dispatch only reads them.
- No feature flag.

## Acceptance

- [ ] Unit tests: calibration (Σ share × multiplier = 1.00 ± 0.02); head vs
  legs damage for the same payload; a legs hit that gets through slows the unit
  through `UnitModifiers.resolve(MOVE_SPEED)` and `explain()` names the wound;
  arms → accuracy; the floor holds; partless payloads unchanged; a hit absorbed
  wholly by armour leaves no wound; a completed treatment dresses wounds.
- [ ] Full `:test` green.
- [ ] Scenes before and after (`fighting-position`, `power-suit`,
  `late-arrival`, `assault-muster`, `squad-medic`).
- [ ] Commander evidence before and after: `conquest-trial`, `relief`.
- [ ] Docs: `ballistics-nouns.md`, `combat-durability-nouns.md`,
  `ai-nouns.md` Unit modifiers, `body-part-cover.md` Part consequences.

## Plan

1. `BodyPart.damageMultiplier`, applied in `DamageResolver.resolve` when a part
   is present, before mitigation and armour.
2. `WoundService` owned by the roster: per-unit leg and arm wound counts, the
   battle's part-hit and wound tallies; `WoundModifier` registered beside the
   other modifier sources, named `unitModifierWounded`.
3. `DamageResolver` records the part hit and, for a limb hit with structure
   damage, the wound.
4. `SquadMedicSystem` dresses the patient's wounds on a completed treatment.
5. Scene readings, tests, before/after evidence, docs.
