# Market garrison model

Status: IMPLEMENTED — all three slices done with untuned defaults; live play acceptance open.

Written: 2026-10-15

Updated: 2026-10-15 — Calibrated headlessly against Nexerelin's formula; doctrine and emplacements wired.

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
It has four outputs: strength, quality, heavy lean and readiness.

1. **Headcount.** Read from vanilla's ground-defence value, as
   `AlliedGarrisonSize` already does, minus the company's own stationed
   strength. This keeps every vanilla and modded modifier on that stat,
   including those from other mods. Market size enters through vanilla's base
   value. Whether the curve also needs Nexerelin's steeper growth by size is a
   calibration question for slice 3, not a reason to bypass the stat.
2. **Quality and heavy lean, not tiers of squad.** The game already models
   defenders through the faction's ground roster: a bulk issue and an elite
   issue, each billet rolling its own weapon, armour and grade, plus mechs and
   defence installations. So the estimate does not sort the garrison into
   militia squads and regular squads. It produces two dials:
   - **Quality:** the share of infantry drawn from the elite issue. The rest
     come from the bulk issue.
   - **Heavy lean:** how far the force leans toward mechs, vehicles and
     emplacements.

   They come from three weights: bulk, elite and heavy. Every garrison has
   some elites, so a small colony is a thinly stiffened garrison rather than a
   squad of militia: about one in five below size 5, one in three from size 5.
   A military market moves a quarter of the bulk weight to elite. Structures
   add weight through one table of our own, keyed by industry id
   (`data/world/garrison/garrison_structures.json`, merged across mods so
   another mod can add its own industries): Ground Defenses and Heavy
   Batteries add bulk, elite and heavy weight; Patrol HQ, Military Base, High
   Command and Lion's Guard add elite and some heavy weight; Heavy Industry and
   Orbital Works add a little heavy weight. Shortages of marines thin the elite
   weight, and shortages of marines, hand weapons or supplies thin the heavy
   weight, each down to half.
3. **Readiness.** A multiplier below one after a recent battle at the market.
   With Nexerelin installed, it reads the market memory key
   `$nex_garrisonDamage`. That is a plain memory read, so it needs no
   Nexerelin class. Without Nexerelin, readiness stays at one until a later
   slice gives the mod its own record of recent ground battles.

## Consumers

- **Hostile garrisons in market-backed operations.** The model says what is
  on the ground, and the tier's demand caps what this operation may be asked
  to face (precinct law: the world's rating says what is there; the demand
  says what the operation faces). **The tier sets a band, and the market
  picks the place in it.** A contract is an operation the market's owner
  meets with the share of its garrison the situation calls for, not the whole
  garrison, so the tier's count is the ceiling and 70% of it the floor; the
  market's strength (4 ground-defence points per defender) picks where it
  lands. Heavy lean does the same for mechs: the risk curve's candidates are
  the ceiling, and a market keeps between half and all of them, anchor first.
  Nothing grows past today's numbers. Quality sets the elite share. **Squads
  are mixed.** Today `BattleSetup.allocateDefenders` queues every elite before
  every bulk soldier, so the top-priority posts get all-elite squads and
  everything else gets all-bulk squads. Slice 2 spreads elites evenly across
  squads, so each squad mixes veterans and bulk troops in proportion to
  quality, each rolling their own kit. The tier keeps its ceiling, so a
  generated First Contract offer never fields a size-8 capital's full
  garrison. A player invasion is not an offer, so it faces the whole
  estimate (`nexerelin-nouns.md`).
- **Allied garrisons.** `AlliedGarrisonSize` reads headcount from the same
  model instead of dividing the raw stat itself, and its quality mixes the
  allied squads the same way.
- **The polity.** `polity-ground-doctrine.md` "Numbers" already names this
  headcount. Its doctrine choices (quality, numbers, heavy support) move the
  model's dials rather than a separate formula.

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

1. **Estimate and table** (implemented). `MarketGarrison` on
   `TargetProfile`, `MarketGarrisonEstimate`, `GarrisonStructureCatalog` and
   its data file, and the resolver read, including Nexerelin's readiness key.
   Unit tests cover size, military shift, structures, unknown industries,
   shortages, readiness and stationed subtraction. Fixture JSON round-trips
   the value.
2. **Mixed squads and consumers** (implemented). Elites spread evenly
   through defender and allied squads (`DefenderRoster.infantryOrder`). A
   market's quality sets the hostile and allied elite share, its strength
   places the headcount in the tier's band (`DefenderRoster.bandedTotal`), and
   its heavy lean keeps a share of the mech candidates
   (`DefenderRoster.bandedMechs`). `DefenderRoster.wholeGarrisonCount` sizes a
   garrison faced whole, for player invasions. The polity doctrine moves its
   own market's dials (`PolityGarrisonDoctrine`: +0.1 elite share per quality
   point, +0.1 heavy lean per heavy-support point; numbers stays the allied
   headcount multiplier so it is not counted twice). Conquest's batteries and
   nests use the same half-to-all band as mechs (`MarketGarrison.keepHeavy`):
   the structure still decides whether there are any, and the garrison's
   heavy lean how many are manned. Existing
   evidence
   (`commanderEvidence`, `garrisonEvidence`) shows the change on fixtures; the
   canonical Conquest fixtures with no market stay unchanged.
3. **Calibration** (done headlessly). A live install only helps once
   someone plays, so both formulas were ported from source (vanilla 0.98a and
   Nexerelin `9b0ddac`) and compared on sample markets at stability 6 and
   hazard 125%, with no shortages:

   | Market | Vanilla strength | Our whole count | Our elite share | Nex marine share by strength | Our heavy lean | Nex heavy share |
   |---|---|---|---|---|---|---|
   | Size 3 outpost | 44 | 11 | 0.20 | 0.00 | 0.00 | 0.00 |
   | Size 3 + Ground Defenses | 88 | 22 | 0.29 | 0.25 | 0.22 | 0.19 |
   | Size 5 colony | 175 | 44 | 0.33 | 0.25 | 0.00 | 0.00 |
   | Size 5 + Ground Defenses | 350 | 88 | 0.38 | 0.37 | 0.20 | 0.19 |
   | Size 6 + Heavy Batteries + Military Base | 945 | 236 | 0.54 | 0.50 | 0.31 | 0.24 |
   | Size 8 + Heavy Batteries + High Command | 1706 | 427 | 0.55 | 0.50 | 0.32 | 0.25 |

   Quality and heavy lean track Nexerelin closely. The outpost difference is
   deliberate: every garrison here mixes in some elites. Headcount is the one
   real divergence: Nexerelin doubles per size step, while vanilla's stat
   grows about linearly above size 3. Vanilla's curve stays, because it is
   what other mods modify and contract work is capped by tier anyway; only a
   player invasion faces the whole count. Remaining calibration is a play
   pass on the untuned constants (`DefenderRoster.BAND_FLOOR` and
   `STRENGTH_PER_DEFENDER`, `MarketGarrison.HEAVY_BAND_FLOOR` and
   `HEAVY_LEAN_FOR_FULL`, the structure table and the doctrine steps).

## Acceptance

- Two markets that differ only in size field visibly different garrisons, in
  headcount and in the share of regulars.
- Adding a military base or heavy batteries to a market adds regulars and
  heavy units, not just toughness.
- The same market produces the same estimate with and without Nexerelin,
  except for readiness after a Nexerelin battle.
- Operations without a market, and fixtures that state their own forces, are
  unchanged.
