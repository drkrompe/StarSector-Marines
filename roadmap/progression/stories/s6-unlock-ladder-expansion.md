# S6 — Unlock ladder expansion

> The ladder is four rungs long and ends at mission five. Four armor
> patterns are fully authored and unreachable.

Status: PLANNED — depends on `s5-parts-acquisition-channels.md`,
`s2-primary-weapon-catalog-expansion.md`, and the S2A–S2D special-equipment
stories.
Written: 2026-08-22
Updated: 2026-08-24 — completed the core-faction equipment direction and tied schematic provenance to intact installation recovery.

## Problem

`MarineArmory.recordVictory` is the whole progression ladder:

- 2 victories: `PULSE_RIFLE` MILSPEC
- 3: `SMG` MILSPEC
- 4: `DMR` MILSPEC
- 5 victories and at least one high-risk: `DMR` MASTERWORK

Then flat, forever. Consequences:

- **No additional armor pattern is ever unlocked.** The anti-materiel rifle is
  now starter issue, while `BLUE_SCOUT`, `RED_ELITE`,
  `OUTLAW`, and `MILITIA` have stats, icons, and sprite layers and cannot
  be reached in a real campaign. `RED_ELITE` is the best armor in the game.
- Masterwork exists for exactly one weapon.
- A pure victory counter is the least interesting possible gate: it does
  not care what you fought, where, for whom, or how.

## Goal

A ladder that stays alive for 30+ missions, reaches every authored asset,
and gates on things the player recognizes as achievements.

And — the part that decides whether this is interesting rather than merely
long — a ladder that is **lateral as well as vertical**. Slice 3 makes
faction the second axis, so progression is about *character* of kit, not
only tier of kit.

## Slice 1 — Close the stranded assets

The smallest correct fix, shippable on its own:

- Every `MarineArmorPattern` gets a reachable unlock, laddered by its own
  `tier` field: tier 2 (`BLUE_SCOUT`, `OUTLAW`, `MILITIA`) early, tier 3
  (`CHARCOAL`, `ARMY_GREEN`) mid — both currently starter issue, so decide
  whether they stay starter — and tier 4 (`RED_ELITE`) as a genuine chase.
- Fill the grade matrix: MILSPEC and MASTERWORK for every primary family,
  not just `DMR`. Special equipment remains an item family rather than a grade
  matrix unless its own story explicitly authors grades.
- Add a **ships-nothing-stranded check**: a test that asserts every
  player primary x `EquipmentGrade`, every special-equipment id, and every
  `MarineArmorPattern` is either starter issue or reachable through some unlock
  path. This is the guard that stops the audit's finding from recurring the
  next time an asset is authored.

## Slice 2 — Reframe recipes as recoverable blueprints

**Recommended direction.** Replace victory-count milestones with
**recovered fabrication schematics** — a recipe is something you *find*,
not something a counter hands you.

Why this is the right reframe:

- It is native to Starsector. The player already understands blueprints as
  a thing you salvage, are paid in, and go looking for.
- It makes recipes a **loot and reward payload**, which plugs straight into
  S5's channels instead of needing a parallel system.
- It makes the ladder *world-reactive* — what you unlock depends on where
  you have been fighting and who you have been working for
  ([[feedback_world_reactive_over_expressive]]).
- It gives special missions a reward that is not money
  ([[feedback_patron_narrative_discoverable]]).

Sources for a schematic: high-risk operation loot, patron contract reward,
story/special mission, MRB licensing tier, and a modest set of
early-campaign milestone grants so a green company is not gated behind luck
in its first hours.

Installation recovery is specific: `intact-installation-recovery.md` may admit
a faction-provenance schematic only when the matching site was secured in an
eligible state and accepted terms grant recovery. Destroying the site or merely
fighting on a market with that faction cannot produce the same pristine pool.

## Slice 3 — Factional equipment identity

**The direction that makes this story worth doing.** Slices 1 and 2 make
the ladder longer; this makes it *lateral*.

A purely vertical ladder — tier 1 to tier 2 to tier 3 — gets boring no
matter how many rungs it has. Factional gear turns rungs into **side-grades
with identity**, so the interesting question stops being "what tier am I
on" and becomes "who do I work for, and where do I fight".

Starsector's factions differ enormously and the mod should read that:

| Flavor | Gear character |
| --- | --- |
| Hegemony | Rugged, ballistic, cheap to keep running, unglamorous |
| Tri-Tachyon | Energy-based, high ceiling, finicky and expensive |
| Persean League | Balanced midline families, dependable mass issue, flexible support |
| Luddic Church | Maintained low-tech patterns, protective and durable, limited taboo equipment |
| Luddic Path | Crude, brutal, dangerous to the user as well as the target |
| Sindrian Diktat | State-issue ballistic/energy mix, concentrated prestige grades |
| Pirate / outlaw | Unreliable with a nasty edge; erratic grade quality |
| Independent / MRB | The neutral baseline the company starts on |

Why this fits the mod's existing commitments:

- It makes the ladder **world-reactive** — what you can field depends on
  where you have operated and who has employed you
  ([[feedback_world_reactive_over_expressive]]).
- It gives Slice 2's blueprint reframe somewhere much better to go. A
  schematic recovered from a Hegemony armory is a *specific* thing, not a
  generic unlock token.
- It gives patron contract rewards real flavor, and pairs with the shipped
  house-flavor work in
  `themes.md` (Corporate /
  Feudal / Underworld / Sectarian) rather than inventing a second axis.
- It gives `s7-grade-visual-identity.md` far more to signal than grade
  alone.

### The two hard problems

**1. Combinatorial blowup.** Faction x family x grade is a large cross
product, and most cells would be filler. **Recommended: faction is a thin
modifier layer over the existing family, not a full cross product.** A
faction supplies a stat skew, a visual treatment, and an availability
source — the same shape `EquipmentGrade` already has, composed alongside
it in `InfantryCombatStats` rather than duplicating `MarineWeapon` entries.
A small number of genuinely faction-exclusive families can then exist as
real chase items without the catalog exploding.

For the first implementation, faction identity attaches to recipe provenance
and a thin grade-side treatment. It supplies availability, presentation, and a
bounded stat skew composed with family/grade/profile. It does **not** grant set
bonuses. Genuinely exclusive weapon families remain separately authored content
rather than empty cells in a faction cross-product.

**2. Coherence versus mongrel.** Can the player field a matched
single-faction kit, or are they always running whatever they scavenged?
Both are defensible and they produce different games:

- *Coherent* invites a set-bonus/identity fantasy ("we are a Hegemony-
  pattern outfit") and gives the player a long-term goal.
- *Mongrel* is more honest to a merc company scraping by, and pairs with
  the debt-start pressure the campaign economy already applies
  ([[feedback_hard_failure_preference]]).

Leaning: **mongrel by default, coherence as an achievable late aspiration**
— which is also the arc the rest of the campaign tier tells.

### Out of scope for this slice

Faction-specific *mechs* and vehicles. The mech roster has its own track
and its own identity work in flight; do not front-run it.

## Slice 4 — Multi-axis gating

Where a milestone gate is still the right tool, gate on more than a count:

- Total and high-risk victories (existing).
- Advanced components held or spent — ties the ceiling to S5's chase
  currency.
- MRB licensing tier, which is already computed
  (`ContractEligibility`) and currently gates only patron access. Extending
  it to armory access makes company reputation mean something concrete.
- Patron standing with a specific house — a Corporate patron opening
  fabrication lines reads correctly.
- Named operational achievements, e.g. clearing a hardened military site.

## Out of scope

- Where parts come from — `s5-parts-acquisition-channels.md`.
- New gear to unlock — `s2-primary-weapon-catalog-expansion.md` and
  `s2d-frag-grenades.md`. The shipped AMR, smoke grenades, and satchel kits are
  already starter issue and must remain covered by the stranded-asset check.
- Visual differentiation of unlocked tiers —
  `s7-grade-visual-identity.md`.
- Any change to printing costs beyond repricing against S5's stated income
  curve.

## Acceptance

- Every authored weapon, grade, special item, and armor pattern is reachable,
  enforced by the stranded-asset test.
- The ladder has meaningful rungs past mission 30, verified against S5's
  stated income curve at missions 5, 15, and 30.
- No rung is reachable by money alone.
- Every core faction has at least one reachable, recognizable recipe-provenance
  path or explicit catalog reason for having none in a given equipment family;
  unknown/modded factions use the Independent baseline unless merged data
  declares their own treatment.
- Faction treatments are side-grades over the same family/grade/profile laws,
  not a hidden faction power tier or set-bonus system.
- Legacy saves migrate: existing `unlockedRecipes` are honored, and a
  long-running save is not retroactively stripped of anything it had.
  `MarineArmory.readResolve` already carries this responsibility — extend
  it, do not replace it.
- An in-game pass confirming the early-company experience still gets a
  visible upgrade in its first few missions. The ladder growing longer must
  not make the opening feel emptier.

## Open questions

- Should `CHARCOAL` and `ARMY_GREEN` remain starter issue? Making the
  player start armorless and *earn* their first real armor is a stronger
  opening beat, but it interacts with early-operations balance, which is
  currently tuned around a company that has them.
- Do schematics fully replace milestone unlocks, or coexist? Leaning:
  coexist, with milestones covering the guaranteed early ladder and
  schematics covering everything above it — so no player is ever hard-stuck
  behind a drop that did not come.
- Coherent faction kits remain an achievable collection/presentation goal, but
  the first implementation has no set bonus; the default company is a mongrel
  mercenary armory assembled from work and recovery across the Sector.
