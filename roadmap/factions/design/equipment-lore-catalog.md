# Equipment lore catalog

Status: ACTIVE — canonical provenance and faction-fit reference for shipped Marine Operations equipment.

Written: 2026-08-26

Updated: 2026-10-30 — the mech lasers take family names by how they fire (BL beam, PL pulse, LL lance; `mechs-nouns.md`,
Weapon names); their lore names live in the descriptions, and the Lion's Guard Thermal Lance stays a thermal projector,
not a laser. Also 2026-10-30 — the mech cannons take `<Family>-<N>` names: the Muster autogun is the AC-6 (QS), the Quarry
breaker the DC-4 (QS), beside the SRM-3 (QS) rack, all QuickSell budget builds, as lower-performance mech components
whose circulation does not make them faction defaults. Earlier: added the naming guide (faction schemes and grade marks)
and moved Luddic, Lion's Guard and pirate kit onto it.

Read `faction-lore-nouns.md` for evidence tiers and the individual faction guides
for institutional context. `progression-nouns.md` owns acquisition and issue;
`moddable-weapons-nouns.md` owns weapon behavior and stable ids.

## Authority

Starsector establishes faction doctrine, technological preferences, trade, salvage,
and a fragmented Domain inheritance, but it does not canonically name these infantry
models. The names and exact manufacturers below are **mod direction** constrained by
those canon anchors. They explain the shipped catalog; they do not claim that a Rook
or Janus suit exists in vanilla lore.

One model may circulate through manufacture, licensing, copying, capture, salvage,
or black-market recovery. A faction's use of an item does not erase its provenance,
and provenance does not allegiance-lock player equipment. Do not add a faction-color
clone when an existing family and grade already express the same battlefield choice.
A separate model earns a stable id only when it introduces a distinct tactical role,
delivery behavior, protection tradeoff, or special activation.

## Naming guide

Equipment reads as **faction industrial output**: a product line from a named
maker or procurement office, graded from standard issue to special service. It
never reads as relics, blessings or spectacle. A pattern name is a plain noun a
quartermaster would write on a crate (Rook, Legionary, Bastion, Holdfast); the
faction voice lives in the card noun, the grade mark and the issuer line, not in
adjectives. Before naming something new, place it in this table.

| Tradition | Maker / issuer voice | Card noun | Grade marks, standard → special |
| --- | --- | --- | --- |
| Hegemony | Ordnance Board patterns built in the Chicomoztoc works; Domain-spec drawings | **Schedule** (tables of equipment) | Auxiliary → Line → Shock → **XIV** (Battlegroup restricted schedule) |
| Tri-Tachyon | Asset Protection products with corporate model names | **Package** | Contract → Response → Blacksite |
| Persean League | Joint Procurement standards on Kazeron drawings, built by member worlds | **Standard** | Member → Coalition → Breach |
| Sindrian Diktat | Office of Supply state issue; Askonia fuel industry | **Issue** | Security → Regular → Lion's Guard |
| Lion's Guard | Its own procurement office, answering to the Executor | **Issue**, by allotment | Prestige only: politics and procurement, never theatre |
| Luddic Church | Wardens' Commissary; Gilead workshops rebuilding inherited shells by hand | **Kit** | Parish → Guard (commissary-inspected rebuilds) |
| Knights of Ludd | The Knights' armoury on Hesperus; suits signed out to one wearer | **Armoury issue** | Heavy issue after handling and maintenance trials |
| Luddic Path | No factory: cell workshops and caches of captured kit | **Cache** | None: what the cell has |
| Pirates | Blackforge shops and black-market quartermasters | **Lot**, **rig** | Patchwork → Mk2 → Breaker |
| Independents | Free Port outfitters, contractors, cooperatives | **Kit**, **Issue** | Watch → Militia → Contract |

Laws:

- **Luddic kit is conservative, not holy.** It is low-tech, maintained by hand,
  governed rather than automated, and logged by an inspector. No "consecrated",
  "reliquary", "sanctified" or "blessed" in a name or a description; faith may
  appear as the reason a rule exists, never as a property of the equipment.
- **Prestige is procurement.** The Lion's Guard is written as allotment, budget
  and political favour. Its kit can be conspicuous because the regime wants it
  seen, not because the suit is a performance.
- **Recovered and rebuilt are grades too.** A recovered pattern keeps its
  maker's name and says "Recovered" in front; a pirate second life takes a Mk
  number rather than a new heroic name.
- **Titles fit the card.** Card titles stay within about 30 characters and lead
  with the tradition; pattern names stay short enough that the noun survives a
  chip truncation.

## Primary weapons

| Stable id | Shipped identity and origin | Credible circulation | Deliberate limits |
| --- | --- | --- | --- |
| `weapon.field-rifle` | **FR-1 Rook field rifle** — a widely preserved Domain colonial-security chemical-slug pattern. | Independent guards, Hegemony auxiliaries, League members, Church and Knight formations, Diktat regulars, pirates, and Pathers can all sustain examples. | No faction owns an exclusive Rook equivalent. Tri-Tachyon internal teams prefer more integrated arms when support exists. |
| `weapon.pulse-rifle` | **PLS Lancer pulse rifle** — a Tri-Tachyon corporate-security family using proprietary cells and diagnostics. | Tri-Tachyon issue; licensed or gray-market League and Diktat use; selected Hegemony specialists; recovered Independent, pirate, Church, Knight, or Pather examples. | It is not ordinary Church, Path, pirate, or Hegemony line issue, and those factions do not receive a cosmetic pulse clone. |
| `weapon.smg` | **GR-SMG Rattler submachine gun** — a black-market family of cheap pistol-calibre slug automatics rebuilt for boarding work, not one factory pattern. | Pirate shops, Pather cells, deniable contractors, and frontier black markets. | Formal militaries may recover or procure Rattlers for a specific breach, but should not treat the improvised family as universal standard issue. |
| `weapon.squad-automatic` | **SA Stalwart squad automatic** — a Hegemony-standardized Domain support-rifle package. | Hegemony formations; copied League and Diktat arsenals; Knight and Independent heavy-weapon teams. | It is not a characteristic Pather or pirate market product, though captured examples remain possible. |
| `weapon.dmr` | **RG Longbow rail carbine** — a Kazeron-led League interoperability project. | League coalition issue; licensed, purchased, or captured Hegemony, Tri-Tachyon, Church, Knight, Diktat, Independent, and outlaw examples. | Its availability need not imply political alignment with the League. Pathers and pirates should encounter it as scarce recovery, not a mirrored marksman industry. |
| `weapon.drone-pulse` | **DPLS Wisp drone pulse laser** — a Tri-Tachyon facility-security emitter reused across autonomous platforms. | Corporate drones and compatible autonomous security platforms. Remnant use of comparable energy weapons does not imply human compatibility or a transferable template. | It is not a marine collectible and must not acquire a handheld clone merely to fill a faction grid. |

## Special equipment

| Stable id | Shipped identity and origin | Credible circulation | Deliberate limits |
| --- | --- | --- | --- |
| `special.srm-1` | **SRM-1** (Annihilator pattern rocket launcher) — a Hegemony cut-down of an old Domain rocket cassette, copied throughout low-tech arsenals. | Hegemony, League, Church, Knights, Diktat, Independent contractors, and recovered outlaw stocks. | Tri-Tachyon may use compact precision alternatives; Path and pirate use should be scarce recovery rather than clean licensed supply. |
| `special.anti-materiel-rifle` | **AMR-1 Breachlight** — a Tri-Tachyon magnetic anti-materiel rifle. | Corporate security, selected Hegemony or Lion's Guard patron issue, expensive League or Independent recovery. | Church, Path, and pirate markets do not receive an equivalent precision AMR by default. |
| `special.smoke-grenade` | **Wayfarer multispectral smoke** — a League interoperability answer to mismatched coalition optics. | League and Independent markets; corporate reconnaissance and specialist recovery elsewhere. | Smoke remains faction-neutral in simulation; availability does not grant a hidden optical advantage. |
| `special.frag-grenade` | **FRG-1 Shattercap** — a compact Kazeron security grenade whose specification leaked widely. | League, Diktat, Independent, pirate, and Pather circulation. | It remains anti-personnel ordnance, not a faction-specific wall breacher or universal heavy weapon. |
| `special.satchel-charge` | **Breachhand mag-clamp kit** — an industrial voidwork tool associated with Gilead guild maintenance and adapted for combat. | Church and Knight custodians; copied Pather, pirate, and Independent demolition sets. | Hegemony and Tri-Tachyon have other engineering channels and need not stock a renamed Breachhand equivalent without a distinct mechanic. |
| `special.point-defence-emplacement` | **PDE-1 Palisade interceptor pod** — a Gilead voidwright shipping-lane point-defence cluster cut down onto a folding infantry tripod, from the same guild tradition as the Breachhand. | Church and Knight convoy custodians who already run lane defences; League and Independent contractors who buy the pattern under licence; recovered pirate and Pather sets running on whatever cells they can scavenge. | It is denial equipment and nothing else: it engages warheads, never bodies, and it is not an anti-infantry turret, a sentry gun, an observation post, or a squad member. Its magazine, its cell, and its own structure all run out, so it degrades a missile threat rather than removing one. Hegemony and Tri-Tachyon field their own hardpoint point defence and need no renamed infantry equivalent without a distinct mechanic. |
| `special.field-mortar` | **MTR-1 Anvil field mortar** — a Hegemony two-piece infantry mortar (smoothbore tube, baseplate, bipod) of old Domain pattern, copied wherever an army wants fire it can drop behind a wall. | Hegemony issue; Independent and Lion's Guard contractors under licence; recovered sets in pirate and Pather hands. | It is crew-served indirect fire and nothing more: a dead zone inside its minimum range, a small magazine, a slow rate, no sight of its own (it fires on what its section has seen) and no protection for a gunner who is rushed. Lose the gunner and the tube is lost. It is not a mech gun, not a turret and not a rocket launcher. |
| `special.comms-radio` | **FOR-2 Tallyman forward-observer set** — a pack radio and handset of old Hegemony fire-direction pattern, common wherever an army keeps aircraft it wants aimed. | Hegemony issue; Independent and Lion's Guard contractors under licence; elite squads of every power by roster; recovered sets in pirate and Pather hands. | It directs an air pool onto ground its carrier can see and nothing more: it needs a live, unsuppressed carrier with sight of the target, a long call time and a cooldown, and a strike over a strong defence meets anti-air. It is not a sensor, not a communications relay and not an air wing of its own. |

## Mech weapon components

These components define a commercial floor beneath formal military equipment.
They are deliberately worse because they are security or industrial conversions,
not because salvage itself implies poor quality. Recovery is one circulation
channel among manufacture, trade, capture, and black-market resale.

| Stable weapon id | Model and provenance | Plausible circulation | Limits and identity |
| --- | --- | --- | --- |
| `weapon.mech-ac-6-qs` | **AC-6 (QS)**, the Muster: QuickSell's autocannon for militia walkers that could not afford a Bastion (the AC-6), built round a commodity receiver and an open feed, the long barrel and hardened rounds dropped and the tolerances let go. Cheaper, smaller and worse is the QuickSell promise. | Independent commercial worlds, local militias, auxiliaries, contractors, and captured pirate or Pather stocks. | The AC-6's six-round burst and ten-round tray at a faster, softer, wilder cycle: shorter-ranged, less accurate and far less penetrating than the AC-6 or a military chaingun. It is common-market hardware, not Independent faction issue. |
| `weapon.mech-dc-4-qs` | **DC-4 (QS)**, the Quarry breaker: QuickSell's demolition cannon for contractors who could not get a Foundry Breaker (the DC-5), a mining projector with the bore, shell and bin cut down to four rounds and no work spent on the sights. | Industrial markets, remote extraction works, breaker yards, contractors, and outlaw conversions or recovery. | A crude short-range structural hit with poor accuracy, low armor penetration, slow cycling, and a four-shell bin. Its wall damage does not make it a military DC-5 equivalent. |
| `weapon.mech-srm-3-qs` | **SRM-3 (QS)** — the QuickSell line: a smaller, cheaper SRM for militia and security units, made by cutting the sealed SRM rack back until it could be stamped and welded in bulk (no boost motor, no armoured warhead fuse, no sealed housing, loose tubes on an open shoulder rail). Cheaper, smaller and worse is the QuickSell gimmick. | Militia and security units, frontier and industrial markets, contractors, and recovered outlaw use. | One salvo of three straight-fire rockets, recharged as an SRM rack is, loose dispersion, and no contact charge, military boost guidance or indirect-fire capability. It is weaker and more exposed than a sealed SRM pod. |

All three are valid future subsystem-inventory or market stock. None belongs in
`FactionMechLoadouts` merely because a faction can plausibly buy, capture, or
maintain one; an authored doctrine must choose that weaker fit explicitly.

The energy components are military and corporate patterns, not this commercial floor, and are named by
how they fire (`mechs-nouns.md`, Weapon names); a lore name stays in the description. The LL-3 is the Lance
Cannon, Tri-Tachyon's emitter for facilities that cannot be resupplied; the PL-2 (AP) is the walker-scale member
of the AR-PULSE security family; the BL and PL lasers are the frigate's point-defence emitters and the laser
tank's gun taken onto walkers. The **Thermal Lance** (`weapon.mech-thermal-lance`) is Lion's Brand, a Sindrian
prestige thermal projector fed from fuel cells, issued to Lion's Guard formations by allotment: a heat lance
whose impact burns, not a laser, so it keeps its own name outside the laser families.

## Armor patterns

The XIV, Specter, coalition Bulwark, Holdfast, Lion's Mantle, Foundry-breaker,
and Arbalest are explicitly POWER_SUIT infantry equipment. This is mod direction
about wearable mechanized shells, not a vanilla-canon claim or a faction-wide
bonus. The named wearer remains an infantry marine. Advanced Redoubt, Outrider,
and Corpsman equipment remains STANDARD; powered assistance and high tier alone
do not imply this category. `power-suit-infantry.md` owns the shared direction
and the distinct XIV, Specter, and Foundry-breaker visual concepts. Existing
patterns retain their credible acquisition channels; classification adds no new
license, fabrication, upkeep, or repair authority.

| Stable id | Shipped identity and origin | Credible circulation | Deliberate limits |
| --- | --- | --- | --- |
| `armor.field-fatigues` | **Domain-pattern vac fatigues** — ubiquitous sealed working dress with a load web. | Every human faction can maintain local versions. | This is unpowered field kit, not a light powered-assault suit. |
| `armor.cordon-shell` | **Cordon surplus shell** — an obsolete station-security cuirass returned to use after its seals, shock liners, or inspection marks failed. | Independent, League, Church, Hegemony auxiliary, Diktat security, and captured pirate stocks. | Cordon is unreliable tier-I salvage, not a faction's current professional security standard and not a cheap Ward equivalent. |
| `armor.lashplate-harness` | **Lashplate salvage harness** — a cargo frame and pressure web carrying breaker-yard slabs with almost no material matching. | Independent breaker crews, pirates, Pathers, and battlefield recovery. | Crude capacity is paid for with poor rating, slow balance, exposed lines, and a conspicuous silhouette; formal factions do not need a clean equivalent. |
| `armor.militia` | **Ward security kit** — a frontier category for locally assembled torso plate, sealed helmet, and impact layers. | Independent, Church, planetary auxiliary, Diktat security, pirate, and Pather use. | Ward is a trade category rather than one faction's standardized factory model. |
| `armor.outlaw` | **Blackforge patchwork rig** — industrial exoframe, salvaged security shell, ship plate, and hand-fitted seals. | Pirates, Pathers, deniable raiders, and frontier recovery. | Formal factions do not need a parade-quality Blackforge equivalent; captured suits remain visually irregular. |
| `armor.scout` | **Janus scout suit** — Tri-Tachyon composite reconnaissance protection with sensor and electronic-warfare integration. | Corporate teams; imported League and Diktat specialists; gray-market Independent users. | It is not ordinary Hegemony, Church, pirate, or Pather line protection. |
| `armor.combat` | **Bastion line armor** — a Kazeron-led interchangeable coalition pattern. | League forces and exported, licensed, or captured Hegemony, Independent, and Diktat stocks. | Shared line role does not make it the Hegemony's preferred institutional suit. |
| `armor.line` | **Legionary line suit** — standardized, pressure-sealed Hegemony armor designed for campaign repair. | Hegemony regulars, licensed Diktat formations, Church and Knight custodians, and battlefield recovery. | Tri-Tachyon internal forces prefer integrated composite systems; pirates and Pathers do not maintain clean Legionary issue at scale. |
| `armor.aegis-composite` | **Aegis composite line suit** — a Tri-Tachyon response pattern with predictive threat displays and powered balance correction, which make it hard to hit rather than hard to stop. | Corporate response teams and expensive licensed or recovered Independent examples. | Its speed and reduced incoming hit profile are bought with less capacity and rating than low-tech line armor; no neural-interface faction bonus exists outside the suit, and the rack is small, finite, and does not refill in the field. |
| `armor.palatine` | **Gilead rebuilt line suit** — a Church workshop pattern rebuilt by hand from inherited Domain shells and fitted resistant plate. | Church guard sections, Knight garrisons, and rare licensed or recovered examples. | High armor rating comes with low capacity and a real mobility cost; careful upkeep grants no hidden immunity. |
| `armor.furnace-line` | **Furnace state line suit** — thick petrochemical laminate over a conventional Sindrian pressure frame. | Diktat regulars, Lion's Guard formations, and exported or recovered Independent stocks. | Its deep capacity is paired with ordinary resistance, weight, and a conspicuous target profile. |
| `armor.reaver` | **Blackforge Mk2 reinforced rig** — a veteran patchwork frame with powered bracing and additional ship plate. | Pirate warbands, Pather cells, and battlefield recovery. | Raw capacity and useful speed do not erase its poor armor rating or irregular maintenance. |
| `armor.heavy` | **XIV heavy battlesuit** — a Hegemony Domain-spec breach and shock pattern with powered bracing, carrying the high-tech shield. | Hegemony shock units, Knight custodians, Lion's Guard prestige formations, wealthy League forces, and rare recovered examples. | Heavy is a role, not universal top-tier faction armor. Tri-Tachyon heavy systems may become a separate item only if their mechanics justify it. |
| `armor.specter-heavy` | **Specter composite battlesuit** — a high-output Tri-Tachyon breach shell carrying the high-tech shield. | Corporate blacksite security and exceptionally rare patron or recovered Independent issue. | It is the fastest and hardest-to-hit heavy pattern, with materially less capacity/rating than XIV armor and no free drone or neural mechanic. |
| `armor.bulwark-heavy` | **Bulwark coalition battlesuit** — modular League heavy armor built around replaceable actuators and shared control standards, carrying the mid-tech shield. | League breach formations, wealthy member worlds, and mercenary patron or recovery channels. | Balance and repairability are its identity; it does not match each specialist heavy pattern at that pattern's strongest axis. |
| `armor.reliquary-heavy` | **Holdfast rebuilt battlesuit** — a legacy Domain heavy shell rebuilt by hand in the Knights' armoury on Hesperus and signed out to one Knight after handling trials, carrying the high-tech shield. | Knights of Ludd and scarce Church or Independent patron/recovery channels. | Exceptional rating is paid for with lower capacity and the heaviest deliberate movement; the armoury's custody is provenance, not damage reduction. |
| `armor.lions-mantle` | **Lion's Mantle guard battlesuit** — prestige Sindrian armor whose oversized cooling feeds the high-tech shield. | Lion's Guard issue by political allotment and rare captured or diverted examples. | The largest formal-faction armor reserve is slow and easy to hit; visibility is a liability as well as flavor. |
| `armor.foundry-breaker` | **Foundry-breaker industrial rig** — a cargo exoskeleton buried under illicit servos and welded ship plate. Its shoulder bracing and a salvaged riot screen are wired to one trigger as the low-tech shield. | Pirate and Pather foundry cells; Independent access is recovery-only. | Enormous crude capacity cannot substitute for rating, mobility, or target denial, and the rig remains infantry rather than a mech. |

## Built-in loadout provenance

Built-in doctrines are recoverable establishments, not faction locks. They teach the
player how an institution composes shared equipment:

- **Frontier Security Equipment / Frontier Patchwork Protection** concentrates a few
  traded Lancers and locally assembled Ward kits above ubiquitous Rooks and fatigues.
- **Path Cell Assault Cache** is intentionally asymmetric: Rooks, blackforge
  Rattlers, industrial Breachhands, smoke, and a scarce captured heavy arm. It does
  not issue clean Lancers merely to mirror a state assault table.
- **League Line Fire-Team Standard** mixes Rooks, Lancers, Stalwarts, Longbows, and
  Wayfarer smoke because interoperability, not one national forge, is its identity.
- **Blacksite Breach Package** pairs supported Lancers with serial-free
  Rattlers and one diverted Shattercap for a short blacksite action.
- **Hegemony Fire Support Schedule** centers serviceable Rooks, Stalwarts, and
  Longbows around tightly controlled Annihilator and Breachlight issue.
- Armor establishments form a visible power matrix: the mixed **Frontier
  Patchwork** baseline, where each team stretches one sound Ward kit across Cordon,
  Lashplate, and fatigue billets; six light/security schedules at tier II; six fully powered
  line schedules at tier III; and six battlesuit schedules at tier IV. Faction peers
  are side-grades rather than mirrors. Hegemony plate is standardized, Tri-Tachyon
  composite issue is faster and harder to hit, League issue is balanced, Church and
  Knight legacy armor is resistant but slow, Sindrian plate is deep and conspicuous,
  and outlaw rigs survive through crude volume with weak resistance.

## Integral systems

An armour pattern may carry one **integral system**: a capability the suit
itself has, authored per pattern and declared by very few. `progression-nouns.md`
owns the rule that a system is behaviour and never durability; this catalog owns
whether a given tradition would plausibly build one and what it would look like.

A system is provenance made mechanical, so it is the sharpest tool this catalog
has for keeping faction suits from becoming palette swaps. It is also the
easiest to overuse: a system on every pattern is a tax on the tier rather than a
reason to want one suit, and the shipped catalog keeps carriers to a small
minority on purpose.

**Breaching is the assault role's signature.** Every ASSAULT pattern carries a
personal shield. That is a claim about the role rather than about the tier: a
future tier-IV scout or line suit would still declare nothing. For the first
release the shield comes in three shared tiers — Low, Mid and High Tech — and a
tradition's character lives in the suit that wears it, not in a private copy of
the shield (`integral-system-slate.md`).

**Payload delivery is not a role signature, and not a suit's either.** Faction
lore carries micro-missile support across Hegemony, League, and Tri-Tachyon
traditions alike. It was once the Aegis composite's integral system (and the
Packframe's and the Arbalests'); it is now the **SRM-3 (Pod)**, a Tri-Tachyon-pattern
shoulder pod issued as a billet's carried special, so any section's weapons card
can hand it to a rifleman who keeps shooting while it fires.

`integral-system-slate.md` owns which patterns carry what and why; it also holds
the remaining individual (non-family) systems that are still direction rather
than authored.

## Availability law

`faction-equipment.faction-equipment.json` is the live player-acquisition mapping.
Ordinary market stock represents sustainable circulation; license and patron sources
represent institutional control; recovery represents plausible capture or salvage.
Absence is meaningful. Do not fill every faction row with every tactical role, and do
not infer that a defender roster weight guarantees a lawful player market source.

Use exact vanilla campaign ids at this boundary, including `sindrian_diktat`,
`lions_guard`, and singular `remnant`. Remnant equipment declares explicit human
incompatibility instead of falling through to the Independent pool.
