# Meta-progression audit — 2026-08-22

> Snapshot of what the mod actually ships for squad customization,
> experience, equipment progression, and effectiveness progression. This is
> the baseline the [`stories/`](stories/) in this track are written against.
> Re-measure before assuming any number here still holds.

## Verdict in one line

The **plumbing is done** — persistence, allocation, atomicity, save repair,
and the UI surfaces all exist, which is the expensive part. What is
underweight is **content depth, reward cadence, and legibility**: the unlock
ladder ends around mission five, four armor patterns are unreachable, six
traits are inert enums, XP is a flat participation payout, and none of the
per-soldier quality data reaches the player.

## 1. Squad customization — the most complete axis

Shipped and player-facing:

- **Persistent six-marine fireteams** (`MarineSquad`) plus a reserve pool.
  Create, rename, transfer, fill vacancies, demobilize. Captains bind as a
  durable *home command*; `Rank.fireteamCap()` sets how many whole teams a
  captain can field.
- **Per-marine kit** — primary (`MarineWeapon` family × `EquipmentGrade`),
  secondary, and `MarineArmorPattern`, all allocation-aware against finite
  printed stock in `MarineArmory`.
- **Whole-team presets** (`SquadEquipmentPreset`): LINE / RECON / MARKSMAN /
  FIELD, applied atomically or refused with a typed reason
  (`SquadPresetResult`).
- **UI** — `ArmoryScreen` (~1400 lines): PERSONNEL / LOADOUTS tabs,
  WEAPONS / ARMOR / SPECIAL inventory tabs, per-family weapon sub-tabs, stat
  readouts, and print buttons.
- Armor choice carries a **visual identity into battle**:
  `CampaignMarineDeployment.armorFamily()` maps `MarineArmorPattern` to a
  `LayeredArmorFamily` sprite stack.

**Gap: catalog breadth, not machinery.** One secondary exists
(`ROCKET_LAUNCHER`). No per-marine role specialization — everyone deboards
`UnitRole.COMBATANT` except scripted `PLANTER` slots.

## 2. Experience — two separate ladders, both live, both thin

### Soldier XP

`MarineSoldier.experienceXp` to `SoldierProfile` to `ExperienceTier`:

| Tier | min XP | accuracy | cooldown | spread |
| --- | --- | --- | --- | --- |
| Green | 0 | x0.92 | x1.08 | x1.08 |
| Regular | 100 | x1.00 | x1.00 | x1.00 |
| Veteran | 350 | x1.07 | x0.95 | x0.92 |
| Elite | 800 | x1.13 | x0.90 | x0.84 |

Awarded **only post-mission, flat per survivor**
(`MissionResolver.applyPersonnelOutcome`): victory LOW 30 / MEDIUM 50 /
HIGH 80, defeat 10. Roughly two missions to Regular, seven to Veteran,
sixteen to Elite — and **every survivor of a given mission gains the same
amount**. Nothing a marine actually did feeds the number.

`CombatService.addExperience(long, int)` exists, refreshes derived stats
live, and **has no production caller** — tests only. In-battle XP is built
and unwired.

### Captain XP

A separate ladder. `MarineCaptain.xp` against `Rank` (PRIVATE to GENERAL,
thresholds 250 to 16000, doubling per tier). Sources:

- `MissionResolver` — `payoutEarned / 100`, or `/ 150` when more than two
  marines were lost. `Trait.NATURAL_LEADER` multiplies by 1.5.
- `CadreTrainingSystem` — 200 XP per whole month on an active Cadre
  contract.

Rank's **only** mechanical effect is `squadCap` / `fireteamCap`: command
breadth, never combat power.

## 3. Equipment progression — a real economy on a very short ladder

`MarineArmory` is permanent recipe unlocks + finite printed stock + one
shared currency (`fabricationMaterials`).

- **Income**: victories only — LOW 2 / MEDIUM 4 / HIGH 7
  (`MissionResolver.applyPersonnelOutcome`).
- **Costs**: primaries by grade SURPLUS 1 / SERVICE 2 / MILSPEC 4 /
  MASTERWORK 8; armor 3 (1 for armorless); secondary 5.
- **The entire unlock ladder** (`MarineArmory.recordVictory`):
  - 2 victories gives `PULSE_RIFLE` MILSPEC
  - 3 gives `SMG` MILSPEC
  - 4 gives `DMR` MILSPEC
  - 5 victories **and** at least one high-risk gives `DMR` MASTERWORK

After that it is flat forever. **No armor or secondary is ever unlocked
past starter issue.** `BLUE_SCOUT`, `RED_ELITE`, `OUTLAW`, and `MILITIA`
are fully authored — stats, icons, sprite layers — and **unreachable in a
real campaign**. `RED_ELITE` (0.20 damage reduction, +8 HP) is the best
armor in the game and no path leads to it.

There is also **no source of parts other than winning**: no market
conversion, no battlefield loot, no breakdown of recovered enemy gear, no
mission reward channel.

## 4. Effectiveness progression — narrow by construction

`InfantryCombatStats` composes three independent axes: weapon family (base)
x `EquipmentGrade` x (`SoldierAptitude` x `ExperienceTier`).

| Grade | range | damage | accuracy | cooldown | spread |
| --- | --- | --- | --- | --- | --- |
| Surplus | x0.92 | x0.95 | x0.90 | x1.10 | x1.18 |
| Service | x1.00 | x1.00 | x1.00 | x1.00 | x1.00 |
| Milspec | x1.05 | x1.05 | x1.07 | x0.94 | x0.88 |
| Masterwork | x1.08 | x1.08 | x1.13 | x0.88 | x0.76 |

End-to-end accuracy spread, worst kit and worst marine
(Surplus / Limited / Green) to best (Masterwork / Exceptional / Elite):
**0.78x to 1.44x, about 1.85x**. Damage and range move on grade alone —
0.95 to 1.08 and 0.92 to 1.08, an **8-13% span across the entire ladder**.

`SoldierAptitude` is innate, rolled at recruitment
(5% Exceptional / 20% Gifted / 65% Steady / 10% Limited) and never
improves. It is the lottery axis.

Armor is the strongest single lever: 0-20% damage reduction and 0-8 HP
against a 25 HP baseline (`UnitType.MARINE`).

### Baseline lethality, measured

Raw sustained output before hit rolls, at Service grade:

| Weapon | damage x burst / cooldown | raw DPS |
| --- | --- | --- |
| Field Rifle | 0.85 x 1 / 1.45 | 0.59 |
| Pulse Rifle | 1.0 x 3 / 1.0 | 3.00 |
| LMG (`SMG`) | 0.7 x 3 / 0.5 | 4.20 |
| Railgun (`DMR`) | 4.0 x 1 / 1.8 | 2.22 |

A 25 HP marine under sustained pulse-rifle fire at a realistic ~40%
landed-round rate takes **roughly 20 seconds to drop**. That is the
concrete shape of "too light on lethality".

## 5. Company tier

- **MRB reputation** gates patron access (`ContractEligibility`):
  TIER_1 always, TIER_2 at 5, TIER_3 at 20, TIER_4 never (endgame flip
  only).
- **Captain roster capacity** is hardcoded; the backlog wants
  `f(playerLevel)`.
- **Command-powers S5** (command-point budget curve) is not shipped. Powers
  are sourced diegetically from the committed fleet detachment, not from a
  leveled budget.

## 6. Traits are largely scaffolding

Eleven `Trait` values; **four do anything**:

| Trait | Effect | Site |
| --- | --- | --- |
| `NATURAL_LEADER` | x1.5 captain XP | `MissionResolver:243` |
| `SALVAGE_EXPERT` | loot recovery bonus | `LootRecoveryModifiers:24` |
| `FIELD_MEDIC` | casualty reduction | `MissionResolver:174` |
| `IDEALIST` / `CYNICAL` | flavor only, no combat effect *by design* | `MarineCaptain.resolveMoralOutlook` |

Inert: `SIEGE_SPECIALIST`, `SAPPER`, `SCOUT`, `COMBAT_ENGINEER`, `VETERAN`,
`LOGISTICS_CHIEF`.

`Trait`'s own Javadoc says "granted on recruitment **or as a level-up
reward**", but the only grant site is `CaptainCandidate:60` at creation.
Promotion awards a rank and a commendation line — never a trait. Captains
are static after hire apart from the one-shot moral outlook.

**There is no trait UI at all.** Nothing in `ArmoryScreen` or the personnel
surfaces lets the player read, compare, or reason about traits.

## 7. What the player never sees

Consolidating the legibility gap, because it cuts across every axis:

- `SoldierAptitude` — rolled, load-bearing, never surfaced beyond a
  one-letter code in `SoldierProfile.shortLabel()` (`"V/G"`).
- `ExperienceTier` — same one-letter treatment, plus a raw XP integer.
- Traits — no surface.
- Career history — not tracked at all. No shots, hits, kills, missions,
  or wounds are retained per soldier.
- In battle, nothing distinguishes an Elite/Exceptional marine from a
  Green/Limited one. The stats differ; the presentation does not.
