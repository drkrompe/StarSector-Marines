# Body-part consequences

Status: IN REVIEW — implemented with before/after evidence; awaiting owner review of the Conquest-trial rifles control and the Relief swings.

Written: 2026-10-15

Updated: 2026-10-15 — implemented; evidence recorded in Acceptance.

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

Chosen values: head ×1.6, torso ×1.1, legs ×0.85, arms ×0.75 (share-weighted
mean 1.005). Each leg wound ×0.85 move speed, each arm wound ×0.85 accuracy,
stacking to a floor of ×0.6.

- [x] Unit tests (`BodyPartConsequencesTest`, `SquadMedicSystemTest`):
  calibration; head vs legs for one payload; a legs hit slows through
  `UnitModifiers.resolve(MOVE_SPEED)` and `explain()` names "Leg wound"; arms →
  accuracy only; stacking to the floor; head/torso leave no wound; a hit the
  plate absorbs whole leaves none; partless payloads unchanged; dressing ends
  the wound; telemetry tallies; a completed treatment dresses and an
  interrupted one does not.
- [x] Full `:test` green (8944 tests).
- [x] Scenes before and after (`fighting-position`, `power-suit`,
  `late-arrival`, `assault-muster`, `squad-medic`): every verdict passes on
  both (25/25 loops). Open-ground fights are no longer byte-identical, since
  damage now varies by part. Fighting-position marine-side hit shares (new
  readings) — subject: head 15%, arms 22%, torso 33%, legs 31% of 458; control:
  head 17%, arms 21%, torso 34%, legs 30% of 463. These are battle-wide for the
  side, not only marines in the trench. Under-fire-free: 5 of 12 alive at the
  end against 8. Squad-medic planted: no deaths against 3. Power-suit one-xiv:
  the XIV fell at tick 1496 where before it survived with 7.5 armour.
  Assault-muster plan-with-lance reached the gate 642 ticks later.
- [x] Conquest trial before and after: 7 of 8 won against 8 of 8. The
  rifles-only on-path control (`on-path-41-rifles`), which won at tick 8910 of
  9000 before, now times out with the objective taken and the keep not taken.
  Marine losses 213 against 176 across the eight; defender losses 368 against
  365.
- [x] Relief before and after: outcomes swing both ways. 8101-late went from a
  win to a defeat; 8101-relief from a timeout to a win; 8103-late from a defeat
  to a win; 8103-relief from a defeat to a timeout. The other five are
  unchanged (timeouts). Wins 2 against 1, defeats 1 against 2.
- [x] Docs: `ballistics-nouns.md`, `combat-durability-nouns.md`,
  `ai-nouns.md` Unit modifiers, `body-part-cover.md` Part consequences.
- [ ] Owner review; then fold and ledger.

Not done here: the medic does not choose a patient for his wounds alone, since
wounds need structure lost and the wounded are already the hurt.

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
