# S6 — Unlock ladder expansion

> The ladder is four rungs long and ends at mission five. Four armor
> patterns are fully authored and unreachable.

Status: PLANNED — depends on the remaining S2E–S2G special-equipment stories;
armor expansion also depends on
`powered-assault-armor-roles.md`.
Written: 2026-08-22
Updated: 2026-08-26 — landed faction market, license, and completed-patron reward consumers; operational recovery remains.

Read `progression-nouns.md`, `faction-lore-nouns.md`, and
`powered-assault-armor-roles.md` before implementing this story.

## Problem

`MarineArmory.recordVictory` is the whole progression ladder:

- 2 victories: `PULSE_RIFLE` MILSPEC
- 3: shredder-carbine (`SMG` compatibility handle) MILSPEC
- 4: `DMR` and `SQUAD_AUTOMATIC` MILSPEC
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

- Every `MarineArmorPattern` gets a reachable unlock, laddered by its own role
  and actual capability rather than blindly by its current `tier` field. The
  initial mapping from `powered-assault-armor-roles.md` keeps tier 2 light
  patterns (`BLUE_SCOUT`, `OUTLAW`, `MILITIA`) early, tier 3 line patterns
  (`CHARCOAL`, `ARMY_GREEN`) mid — both currently starter issue, so decide
  whether they stay starter — and the tier 4 `RED_ELITE` heavy battlesuit as a
  genuine chase. Future high-end light suits remain eligible for late unlocks;
  role is not tier.
- Fill the grade matrix: MILSPEC and MASTERWORK for every primary family,
  not just `DMR`. Special equipment remains an item family rather than a grade
  matrix unless its own story explicitly authors grades.
- Add a **ships-nothing-stranded check**: a test that asserts every
  player primary x `EquipmentGrade`, every special-equipment id, and every
  `MarineArmorPattern` is either starter issue or reachable through some unlock
  path. This is the guard that stops the audit's finding from recurring the
  next time an asset is authored.

## Slice 2 — Recover and collect equipment template cards

**Recommended direction.** Replace victory-count milestones with recovered or
purchased **equipment template cards** — permanent capabilities the player can
find, earn, license, and collect rather than a counter handing them abstractly.

Why this is the right reframe:

- It is native to Starsector. The player already understands blueprints as
  things they salvage, are paid in, purchase, and go looking for. Template cards
  are the infantry-equipment form of that collection loop.
- It makes templates a **loot and reward payload**, which plugs straight into
  S5's channels instead of needing a parallel system.
- It makes the ladder *world-reactive* — what you unlock depends on where
  you have been fighting and who you have been working for
  ([[feedback_world_reactive_over_expressive]]).
- It gives special missions a reward that is not money
  ([[feedback_patron_narrative_discoverable]]).

Sources for a template card: high-risk operation loot, patron contract reward,
story/special mission, market purchase, MRB licensing tier, and a modest set of
early-campaign milestone grants so a green company is not gated behind luck
in its first hours.

Faction source pools now provide the shared data boundary for four of those
channels: market, license, patron, and recovery. They resolve exact campaign
faction ids, fall back to the Independent pool for unknown factions, and let
submods add unique card/channel claims to either their own faction or an
existing pool. The pool weights are not acquisition by themselves. The first
live consumer now populates ordinary open markets with a deterministic monthly
weighted selection: larger markets carry more cards, templates already owned
by the player are omitted, and `license` offers join the same stock only at
Favorable-or-better standing. Unknown faction ids consume the Independent
fallback pool while explicit exclusions such as the Remnants remain empty.
Completed patron contracts now consume the same ledger that records patron
history and issue one weighted card from the patron faction's `patron` pool.
The delivery is exactly once across battle-resolved and time-resolved contracts,
omits templates already learned or held in cargo, retries when fleet cargo is
temporarily unavailable, and compensates existing saves from their unprocessed
completion history. System-generated extraction work is not a patron reward.
Operational recovery remains the work of this story.

The parameterized cargo item and its right-click learning transition are now
shipped. A source can create its validated payload from any stable
equipment-template id; successful learning moves the capability into
`MarineArmory` without entering vanilla ship-production knowledge. The item is
tagged out of automatic drops and the generic Codex while this story authors
its factional recovery, reward, and provenance treatments.

Installation recovery is specific: `intact-installation-recovery.md` may admit
a faction-provenance template only when the matching site was secured in an
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
| Hegemony | Standardized slug rifles and gauss marksman weapons, rugged squad automatics, and limited elite pulse/laser issue; utilitarian and maintainable |
| Tri-Tachyon | Pulse and eventual distinct laser families, precision gauss weapons, micro-missile candidates, and visible neural/HUD integration; high ceiling, finicky and expensive |
| Persean League | Balanced slug, gauss, pulse, grenade, and support-weapon mix with dependable mass issue and flexible local manufacture |
| Luddic Church | Maintained slug/gauss patterns, industrial arc cutters, protective gear, and tightly sanctioned advanced energy equipment |
| Luddic Path | Crude slugthrowers, shredder carbines, combat stims, carried IEDs, and defender-only martyr rigs; dangerous to user and target alike |
| Sindrian Diktat | State-issue ballistic/energy mix, grenade/support weapons, and concentrated prestige grades for guard formations |
| Pirate / outlaw | Scavenged shredder and slug weapons, stolen energy gear, illicit stims, and carried improvised charges; widest condition variance |
| Independent / MRB | Service slug baseline with mixed gauss, energy, breaching, and mercenary specialist acquisitions |

Mechanism guides plausible sources without becoming a faction lock. Elite
Hegemony troops may field energy weapons, a mercenary may recover a Church arc
cutter, and Tri-Tachyon does not own every laser forever. Neural/HUD housings
remain provenance/presentation until a separate mechanic earns an authority.
Defender-only martyr rigs and any defender-only IED variant carry an explicit
non-player catalog reason rather than becoming unreachable player assets by
accident.

Why this fits the mod's existing commitments:

- It makes the ladder **world-reactive** — what you can field depends on
  where you have operated and who has employed you
  ([[feedback_world_reactive_over_expressive]]).
- It gives Slice 2's collection reframe somewhere much better to go. A
  template recovered from a Hegemony armory is a *specific* thing, not a
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

The first implementation now supplies faction availability as template-card
source data without changing combat stats. Provenance presentation and any
thin grade-side treatment remain later work and must compose with
family/grade/profile rather than entering the availability registry. It does
**not** grant set bonuses. Genuinely exclusive weapon families remain
separately authored content rather than empty cells in a faction cross-product.

Armor uses the same provenance principle but not weapon grade. A recovered suit
template names a concrete pattern whose light, line, or heavy role and faction
tradition are authored by `powered-assault-armor-roles.md`. Do not flatten
Hegemony Domain-pattern heavy armor, Tri-Tachyon composite recon armor, Church
consecrated legacy suits, and Path/pirate industrial rigs into one faction tint
over `RED_ELITE`; their silhouette, availability, maintenance, and bounded
tradeoff must agree with the pattern the player actually issues.

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
- Specific recovered template cards or faction licenses, so rare capability
  remains tied to operations and relationships rather than only fleet wealth.
- MRB licensing tier, which is already computed
  (`ContractEligibility`) and currently gates only patron access. Extending
  it to armory access makes company reputation mean something concrete.
- Patron standing with a specific house — a Corporate patron opening its
  equipment catalog reads correctly.
- Named operational achievements, e.g. clearing a hardened military site.

## Out of scope

- New special equipment to unlock —
  `s2e-close-contact-boarding-tools.md`, `s2f-combat-stim-injectors.md`, and
  `s2g-martyr-rigs-and-carried-ieds.md`. New armor roles and patterns belong to
  `powered-assault-armor-roles.md`. The shipped AMR, smoke grenades, and satchel
  kits are already starter templates, while the shipped frag template has a temporary
  two-victory acquisition rung. All must remain covered by the stranded-asset
  check, and Slice 2 may replace the frag milestone with an equally reachable
  recovery path rather than strand or duplicate it.
- Visual differentiation of unlocked tiers —
  `s7-grade-visual-identity.md`.
- Repricing the shipped base-game cargo issue costs; tune them after the
  acquisition ladder and early-company play pass establish real pressure.

## Acceptance

- Every authored weapon, grade, special item, and armor pattern is reachable,
  enforced by the stranded-asset test.
- The ladder has meaningful rungs past mission 30, with target collection
  breadth stated explicitly at missions 5, 15, and 30.
- Advanced capability is not reachable by unrestricted money alone. Market
  cards may still require faction access, licensing, or operational discovery.
- Every core faction has at least one reachable, recognizable template-provenance
  path or explicit catalog reason for having none in a given equipment family;
  unknown/modded factions use the Independent baseline unless merged data
  declares their own treatment.
- Faction treatments are side-grades over the same family/grade/profile laws,
  not a hidden faction power tier or set-bonus system.
- The ladder contains useful light, line, and heavy suit choices beyond the
  opening without ordering all light armor before all line armor before all
  heavy armor as a disguised quality ladder.
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
- Do recovered cards fully replace milestone grants, or coexist? Leaning:
  coexist, with milestones covering the guaranteed early ladder and
  recovery/purchase channels covering everything above it — so no player is ever hard-stuck
  behind a drop that did not come.
- Coherent faction kits remain an achievable collection/presentation goal, but
  the first implementation has no set bonus; the default company is a mongrel
  mercenary armory assembled from work and recovery across the Sector.
