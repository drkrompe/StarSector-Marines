# Market garrison model

Status: PLANNED

Written: 2026-10-15

Read `campaign-battle-bridge-nouns.md`, `polity-ground-doctrine.md`,
`mission-tier-nouns.md` and `nexerelin-nouns.md` first.

## Goal

Give every market one garrison estimate of our own: how many defenders it has
and of what kind. The estimate follows market size, stability, structures and
supply, in the same way vanilla and Nexerelin already reason about ground
defence. It is computed identically whether or not Nexerelin is installed. A
Nexerelin invasion fought as a Conquest then faces the garrison that market
actually has, and so does every other market-backed operation.

## Why

Today the target market reaches the defending force through three unrelated
signals:

- **Toughness.** The hostile garrison's toughness comes from the structures
  rating (`TargetProfile.defenseLevel`), capped by the mission tier's demand.
- **Hostile headcount.** This comes from the operation tier.
- **Allied headcount.** The allied garrison (`AlliedGarrisonSize`) is sized
  from vanilla's ground-defence value.

None of these says that a size-7 world has many more defenders than a size-3
outpost, or that a military base adds regulars and heavy weapons rather than
only toughness. Both mods it sits beside already say so:

- **Vanilla:** Population & Infrastructure sets base ground defence by size
  (10, 20, 50, then 100 per size step above 3). Stability and hazard multiply
  it, and so do Ground Defenses or Heavy Batteries (×2 or ×3) and Patrol HQ,
  Military Base or High Command (×1.1, ×1.2, ×1.3).
- **Nexerelin** (`GBUtils.estimateDefenderCounts`):
  - Headcount is 25 × 2^(size−1), scaled by stability.
  - The garrison is all militia below size 5, and three-quarters militia,
    one-quarter marines from size 5. A military market shifts another quarter
    from militia to marines.
  - Structures and some planet conditions add militia, marine and heavy
    shares (`groundBattleDefs.json`).
  - Shortages of marines, hand weapons and supplies shrink the regulars and
    heavy units.
  - Damage from a recent invasion (`$nex_garrisonDamage`) reduces everything
    until it recovers over the following months.

## The model

The **market garrison** is a plain value in the target profile, resolved on
the host thread by `TargetProfileResolver` with the rest of the market read.
It has three outputs:

1. **Headcount.** Read from vanilla's ground-defence value, as
   `AlliedGarrisonSize` already does, minus the company's own stationed
   strength. This keeps every vanilla and modded modifier on that stat,
   including those from other mods. Market size enters through vanilla's base
   value. Whether the curve also needs Nexerelin's steeper growth by size is a
   calibration question for slice 3, not a reason to bypass the stat.
2. **Mix.** Shares of three tiers:
   - **Militia:** local defenders on Surplus-grade common kit.
   - **Regulars:** the faction's own marines, on its normal roster and grade.
   - **Heavy:** mechs, vehicles and gun positions.

   Size sets the base split: all militia on small worlds, regulars appearing
   at size 5. Structures shift the split, using one table of our own, keyed
   by vanilla industry id: Ground Defenses and Heavy Batteries add regulars
   and heavy units; Patrol HQ, Military Base and High Command add regulars and
   some heavy units. Shortages of marines, hand weapons and supplies shrink
   the regular and heavy shares.
3. **Readiness.** A multiplier below one after a recent battle at the market.
   With Nexerelin installed, it reads the market memory key
   `$nex_garrisonDamage`. That is a plain memory read, so it needs no
   Nexerelin class. Without Nexerelin, readiness stays at one until a later
   slice gives the mod its own record of recent ground battles.

## Consumers

- **Hostile garrisons in market-backed operations.** The model says what is
  on the ground, and the tier's demand caps what this operation may be asked
  to face (precinct law: the world's rating says what is there; the demand
  says what the operation faces). Headcount becomes squads, and the mix
  becomes their grade and the heavy force. The tier keeps its ceiling, so a
  generated First Contract offer never fields a size-8 capital's full
  garrison. A player invasion is not an offer, so it faces the whole
  estimate (`nexerelin-nouns.md`).
- **Allied garrisons.** `AlliedGarrisonSize` reads headcount from the same
  model instead of dividing the raw stat itself, and allied militia stop
  being regulars.
- **The polity.** `polity-ground-doctrine.md` "Numbers" already names this
  headcount. Its doctrine choices (quality, numbers, heavy support) move the
  model's mix and count rather than a separate formula.

Neutral stays neutral: no market means no garrison estimate. Story and
fixture operations that state their own forces ignore the estimate.

## Laws

- One estimate per market, computed once at the launch boundary. Battle code
  never reads `MarketAPI`.
- Nexerelin is optional: the model is identical with and without it.
  Nexerelin can only add input, through its readiness memory key. The mod
  never writes Nexerelin's key itself; Nexerelin's own end of battle writes
  it when a battle is handed off through it.
- The structure table is data in the mod, keyed by vanilla industry id. An
  unknown industry contributes nothing, so a modded industry never crashes or
  invents defenders.
- No double counting. Stationed strength is subtracted once (existing bridge
  law), and stability is already inside vanilla's stat, so nothing scales by
  stability twice.

## Slices

1. **Estimate and table.** The garrison value in `TargetProfile`, the
   resolver read, the structure and shortage table, and the readiness read.
   Unit tests on the pure estimate cover size steps, each structure, the size
   5 regular threshold, shortages, readiness, stationed subtraction and
   Neutral. Fixture JSON round-trips the new value.
2. **Consumers.** Hostile garrison headcount and mix under the tier ceiling,
   and allied garrison headcount and militia grade. Existing evidence
   (`commanderEvidence`, `garrisonEvidence`) shows the change on fixtures; the
   canonical Conquest fixtures with no market stay unchanged.
3. **Calibration.** With Nexerelin installed, compare our estimate against
   `GBUtils.estimateDefenderCounts` on a spread of real markets (small
   outposts, mid colonies, military worlds, capitals) and record the ratio.
   Decide with the user whether the headcount curve needs steeper growth with
   size than vanilla's stat gives. This is evidence, not a test: Nexerelin is
   not on the test classpath.

## Acceptance

- Two markets that differ only in size field visibly different garrisons, in
  headcount and in the share of regulars.
- Adding a military base or heavy batteries to a market adds regulars and
  heavy units, not just toughness.
- The same market produces the same estimate with and without Nexerelin,
  except for readiness after a Nexerelin battle.
- Operations without a market, and fixtures that state their own forces, are
  unchanged.
