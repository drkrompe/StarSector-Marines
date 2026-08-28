# Powered assault-armor roles

Status: PARTIALLY SHIPPED — the cross-faction line/heavy pattern catalog, bounded
stat side-grades, acquisition sources, tiered built-in doctrine matrix, defender
adoption, and Armory comparison presentation are live. Explicit role/provenance
fields and concealment still coordinate with `s6-unlock-ladder-expansion.md` and
`target-faction-ground-rosters.md`; concealment behavior requires an explicit shared
perception contract.

Written: 2026-08-24

Updated: 2026-08-28 — shipped the Fleet Armory pattern comparison surface (`FleetArmoryViewModel.armorComparisonCards`, `armory-armor-comparison.mlx`) so every catalogued pattern reads side by side; explicit role/provenance data fields remain outstanding.

Read `progression-nouns.md`, `combat-durability-nouns.md`, `mechs-nouns.md`,
and `faction-lore-nouns.md` before implementing this story.

## Problem

`MarineArmorPattern` currently exposes pool, rating, movement, incoming
accuracy, icon, and a numeric tier. The authored patterns form a useful stat
ladder, but the player cannot tell what kind of suit each one is meant to be,
why two patterns at a similar price behave differently, or how recovered
Hegemony, Tri-Tachyon, Church, or outlaw armor should feel distinct.

The faction-roster direction says only “armor treatment.” Without a shared
equipment model, that phrase can collapse into palette swaps, hidden faction
bonuses, or bespoke unit types. The setting's light infiltration suits,
pressurized line armor, and walking-tank battlesuits instead provide three
clear tactical roles that can serve player equipment and defender composition
through the same data.

## Goal

Make powered assault armor a lateral loadout choice with three readable roles,
then express faction traditions as concrete pattern provenance. A suit's role
answers what job and tradeoff it has; its pattern answers which concrete item
the company owns; provenance answers who built or maintained that tradition.
None of those answers replaces weapon family, weapon grade, marine profile, or
experience.

## Role catalog

| Role | Battlefield promise | Mechanical envelope |
| --- | --- | --- |
| Light infiltration / recon | Sealed rapid-deployment armor for scouting, boarding-style movement, and fast objective work | Lowest powered protection and best movement; only patterns with an explicit concealment suite may reduce hostile-acquisition signature |
| Standard line combat | Pressurized powered armor for hazardous worlds, hard-vacuum actions, and sustained infantry fighting | Middle protection and mobility baseline; recoil assistance, tactical relay, and jump-assist language do not create free weapon, command, or traversal bonuses |
| Heavy mechanized battlesuit | A one-person walking tank for breach and frontline assault | Highest infantry armor capacity/rating and clearest movement/handling cost; remains infantry rather than a light mech |

`ARMORLESS` is an unpowered field kit, not a fourth powered role. The compatibility
patterns `BLUE_SCOUT`, `MILITIA`, and `OUTLAW` are light; `CHARCOAL` and
`ARMY_GREEN` are line; and `RED_ELITE` is heavy. Additive line patterns are Aegis,
Palatine, Furnace, and Reaver; additive heavy patterns are Specter, Bulwark,
Reliquary, Lion's Mantle, and Foundry-breaker. The mapping is explicit catalog
interpretation, not permission to infer role forever from tier or enum ordinal.

Role is deliberately not quality. Future masterwork recon armor can remain
light, and a welded industrial exosuit can remain heavy while being unreliable
or poorly rated. The first balance pass should preserve the broad live
survivability scale rather than using the new vocabulary as cover for a second
lethality rewrite.

## Faction pattern catalog

The first catalog covers every core faction plus the neutral fallback:

| Provenance | Powered-armor character and issue direction |
| --- | --- |
| Hegemony | Standardized Domain-pattern line armor and utilitarian heavy suits; durable, repairable, formation-friendly, with light recon issue kept specialist rather than universal |
| Tri-Tachyon | Sleek composite light and line suits with neural interfaces, electronic-warfare housings, and drone-control integration; high-end capability is precise and maintenance-intensive rather than a blanket stat tier |
| Persean League | Licensed and locally manufactured line patterns with practical light specialists and limited heavy issue; flexible midline coverage over one extreme |
| Luddic Church | Carefully maintained legacy line armor and artisan-restored heavy suits; heavy operators read as consecrated guardians who passed strict spiritual sanction, not as evidence that the faction casually embraces every technology |
| Luddic Path | Industrial exoskeletons, salvaged load-bearing frames, welded plate, and illicit combat-stim interfaces; aggressive light and crude heavy patterns carry real protection/mobility liabilities rather than hidden fanatic immunity |
| Sindrian Diktat | Rigid state-security line issue and prestige heavy guard suits, with mixed ballistic/energy integration and petro-industrial materials |
| Pirates | Stolen recon suits, civilian work frames, scrap plate, and dangerously modified heavy rigs; widest condition variance and no guaranteed access to intact high-tech functions |
| Independents / MRB | Mixed serviceable line and light patterns with mercenary heavy acquisitions; the compatibility baseline for player starter gear and unknown/modded factions |

Faction language describes authored availability, visuals, maintenance, and
bounded pattern tradeoffs. Neural uplinks, combat stims, consecration, or drone
integration do not become automatic faction-wide buffs. A mechanic exists only
when a typed capability and its owning battle system implement it. Finite stim
utility belongs to `s2f-combat-stim-injectors.md`; neural/HUD integration is
provenance and presentation until a dedicated interface story names a
non-duplicative mechanic.

## Shipped doctrine matrix

The first live breadth pass contains nineteen deterministic twelve-billet armor
cards: one mixed tier-I frontier baseline and six faction-authored alternatives in each
of tiers II, III, and IV. A doctrine tier is expected squad capability, not the
maximum catalog tier of any single leader's suit; this permits auxiliary schedules
to mix a few institutional shells into common security protection without pretending
the whole formation is line infantry.

Within a peer band, mechanics follow provenance through concrete pattern stats.
Hegemony favors pool/rating and accepts slower, easier targets. Tri-Tachyon gives up
raw plate for speed and reduced incoming accuracy. League patterns occupy the
balanced center. Church and Knight suits emphasize rating with a mobility penalty.
Diktat and Lion's Guard suits emphasize pool while remaining conspicuous. Outlaw
patterns use very high crude pool, weak rating, and worsening handling as weight
rises. No doctrine applies a faction-wide multiplier after issue.

The tier-I baseline now distinguishes three forms of bad protection inside that one
doctrine: mobile Domain-pattern fatigues with no armor capacity, expired Cordon security
shells with a little rated plate, and slow Lashplate cargo harnesses with crude pool
but almost no resistance. They reuse the established low-end militia/outlaw
silhouettes and are intentionally broad circulation categories rather than a new
faction-equivalent matrix.

## Data and authority

- Add a closed armor-role vocabulary, including an explicit `UNPOWERED`
  sentinel, and a stable provenance id to armor-pattern data. Persist the
  concrete pattern id; derive neither role nor provenance from display text,
  color, tier, or faction at runtime.
- Keep pattern stats explicit. Role supplies validation bands and comparison
  language, not a second stack of hidden multipliers.
- Campaign Armory owns template cards, cargo-backed assignment, provenance copy,
  and maintenance/fabrication price. Deployment freezes the selected pattern's
  plain combat values and capabilities.
- Battle durability consumes pool and rating. Infantry movement consumes the
  frozen movement tradeoff. Rendering consumes the same role/provenance for
  silhouette and material treatment without feeding presentation back into
  simulation.
- Defender rosters choose eligible concrete patterns from the frozen target
  faction profile. They do not create faction-specific damage rules or replace
  player-owned kits.
- Merged data may add modded provenance entries and patterns, but unknown
  faction ids continue through the Independent baseline and invalid pattern
  references fail loud.

## Concealment-suite boundary

Active optical camouflage belongs to specific light patterns, not to every
light suit and not to faction identity as a whole. When contracted, it must be
a symmetric signature mechanic shared by tactical perception and player
presentation: movement, firing, taking damage, and objective interaction make
the wearer easier to acquire; breaking contact does not delete an enemy's
honestly earned memory. The visual treatment may shimmer or blur, but render
alpha is never detection authority.

Digital disguises that mimic civilian vac-suits or technician clothing are a
campaign/covert-entry capability. They have no honest use in the current open
battle simulation and remain out of scope until a mission owns infiltration,
inspection, or pre-contact identity state.

## Mech boundary

A heavy battlesuit is still one named marine in one billet. It uses infantry
pathing, cover, primary and special-equipment slots, morale, casualty, and
recovery authority. It does not gain a chassis, hardpoints, a mech lance,
vehicle collision, or Mech Support delivery. If a future suit needs those
things, it has crossed the boundary and must become Mechs content rather than
an oversized armor pattern.

## Acceptance

- Every shipped armor pattern has one explicit classification (`UNPOWERED`,
  light, line, or heavy) and provenance; save migration preserves the player's
  assigned pattern and stock exactly.
- Armory comparison names role, protection, mobility, and provenance without
  presenting role as tier. A player can explain why a light suit may be a
  later unlock than a crude heavy one. **Shipped**: the Fleet Armory's Compare
  Patterns surface (reachable from the fire-team armor picker) lists every
  catalogued pattern side by side with its unit class, tier, protection and
  mobility meters, integral system (via the shared `IntegralSystemCopy`), and
  provenance copy, sorted by tier then name. It remains presentation-only and
  does not select or issue a pattern; the closed role vocabulary and stable
  provenance id fields it will eventually read are still outstanding below.
- Representative Hegemony, Tri-Tachyon, Church, Path, and pirate suits are
  distinguishable by more than color; League, Diktat, Independent, and modded
  fallback behavior also have deterministic catalog coverage.
- Player deployment and every defender creation path freeze and consume the
  same concrete armor-pattern data.
- Heavy suits remain valid infantry in pathing, cover, weapons, casualty, and
  recovery tests; they never enter mech inventory or force-budget authority.
- No optical-camouflage stat ships until acquisition, memory, symmetry, and
  presentation are tested through one explicit perception contract.
- The balance report compares at least one unpowered, light, line, and heavy
  target so protection and movement costs remain readable on the live
  lethality scale.

## Out of scope

- A boarding or hostile-environment survival subsystem. Sealing and vacuum
  readiness are setting truth, not a new hazard meter in this story.
- Jump traversal, vertical movement, or a universal jet ability.
- Digital-disguise mission logic.
- Combat-stim activation, contact boarding weapons, and martyr/IED equipment;
  those retain their own special-item stories rather than becoming armor
  pattern powers.
- New mech chassis, vehicle frames, or faction-wide set bonuses.
- Making all faction suits immediately player-printable; acquisition remains
  `s6-unlock-ladder-expansion.md` authority.
