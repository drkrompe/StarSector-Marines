# Equipment lore catalog

Status: ACTIVE — canonical provenance and faction-fit reference for shipped Marine Operations equipment.

Written: 2026-08-26

Updated: 2026-09-01 — added the common-market Muster autogun, Quarry breaker
cannon, and Pioneer utility rocket cradle as lower-performance mech components
whose circulation does not make them faction defaults.

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

## Primary weapons

| Stable id | Shipped identity and origin | Credible circulation | Deliberate limits |
| --- | --- | --- | --- |
| `weapon.field-rifle` | **FR-1 Rook field rifle** — a widely preserved Domain colonial-security chemical-slug pattern. | Independent guards, Hegemony auxiliaries, League members, Church and Knight formations, Diktat regulars, pirates, and Pathers can all sustain examples. | No faction owns an exclusive Rook equivalent. Tri-Tachyon internal teams prefer more integrated arms when support exists. |
| `weapon.pulse-rifle` | **PLS Lancer pulse rifle** — a Tri-Tachyon corporate-security family using proprietary cells and diagnostics. | Tri-Tachyon issue; licensed or gray-market League and Diktat use; selected Hegemony specialists; recovered Independent, pirate, Church, Knight, or Pather examples. | It is not ordinary Church, Path, pirate, or Hegemony line issue, and those factions do not receive a cosmetic pulse clone. |
| `weapon.smg` | **SHD Rattler shredder carbine** — a black-market family of civilian flechette mechanisms cut into boarding arms, not one factory pattern. | Pirate shops, Pather cells, deniable contractors, and frontier black markets. | Formal militaries may recover or procure Rattlers for a specific breach, but should not treat the improvised family as universal standard issue. |
| `weapon.squad-automatic` | **SA Stalwart squad automatic** — a Hegemony-standardized Domain support-rifle package. | Hegemony formations; copied League and Diktat arsenals; Knight and Independent heavy-weapon teams. | It is not a characteristic Pather or pirate market product, though captured examples remain possible. |
| `weapon.dmr` | **RG Longbow rail carbine** — a Kazeron-led League interoperability project. | League coalition issue; licensed, purchased, or captured Hegemony, Tri-Tachyon, Church, Knight, Diktat, Independent, and outlaw examples. | Its availability need not imply political alignment with the League. Pathers and pirates should encounter it as scarce recovery, not a mirrored marksman industry. |
| `weapon.drone-pulse` | **DPLS Wisp drone pulse laser** — a Tri-Tachyon facility-security emitter reused across autonomous platforms. | Corporate drones and compatible autonomous security platforms. Remnant use of comparable energy weapons does not imply human compatibility or a transferable template. | It is not a marine collectible and must not acquire a handheld clone merely to fill a faction grid. |

## Special equipment

| Stable id | Shipped identity and origin | Credible circulation | Deliberate limits |
| --- | --- | --- | --- |
| `special.rocket-launcher` | **RKT-1 Annihilator rocket launcher** — a Hegemony cut-down of an old Domain rocket cassette, copied throughout low-tech arsenals. | Hegemony, League, Church, Knights, Diktat, Independent contractors, and recovered outlaw stocks. | Tri-Tachyon may use compact precision alternatives; Path and pirate use should be scarce recovery rather than clean licensed supply. |
| `special.anti-materiel-rifle` | **AMR-1 Breachlight** — a Tri-Tachyon magnetic anti-materiel rifle. | Corporate security, selected Hegemony or Lion's Guard patron issue, expensive League or Independent recovery. | Church, Path, and pirate markets do not receive an equivalent precision AMR by default. |
| `special.smoke-grenade` | **Wayfarer multispectral smoke** — a League interoperability answer to mismatched coalition optics. | League and Independent markets; corporate reconnaissance and specialist recovery elsewhere. | Smoke remains faction-neutral in simulation; availability does not grant a hidden optical advantage. |
| `special.frag-grenade` | **FRG-1 Shattercap** — a compact Kazeron security grenade whose specification leaked widely. | League, Diktat, Independent, pirate, and Pather circulation. | It remains anti-personnel ordnance, not a faction-specific wall breacher or universal heavy weapon. |
| `special.satchel-charge` | **Breachhand mag-clamp kit** — an industrial voidwork tool associated with Gilead guild maintenance and adapted for combat. | Church and Knight custodians; copied Pather, pirate, and Independent demolition sets. | Hegemony and Tri-Tachyon have other engineering channels and need not stock a renamed Breachhand equivalent without a distinct mechanic. |
| `special.breaching-cutter` | **CUT-1 Emberjaw thermal breacher** — an Independent breaker-yard hull-cutting tool carried into boarding work. | Independent salvage and shipbreaking crews; Church and Knight custodial cutters kept running past their service life; pirate conversions of whatever they recover. | Thermal and arc are mechanism variants of one breach-tool role, not two catalog entries. It is sustained local contact work with no blast, and it opens only an authored breach point — it is not a portable demolition charge and not a wall-removal tool. Corporate and Hegemony engineering channels need no renamed equivalent without a distinct mechanic. |
| `special.point-defence-emplacement` | **PDE-1 Palisade interceptor pod** — a Gilead voidwright shipping-lane point-defence cluster cut down onto a folding infantry tripod, from the same guild tradition as the Breachhand. | Church and Knight convoy custodians who already run lane defences; League and Independent contractors who buy the pattern under licence; recovered pirate and Pather sets running on whatever cells they can scavenge. | It is denial equipment and nothing else: it engages warheads, never bodies, and it is not an anti-infantry turret, a sentry gun, an observation post, or a squad member. Its magazine, its cell, and its own structure all run out, so it degrades a missile threat rather than removing one. Hegemony and Tri-Tachyon field their own hardpoint point defence and need no renamed infantry equivalent without a distinct mechanic. |
| `special.field-revetment` | **RVT-3 Rampart field revetment** — a Hegemony combat-engineering pattern of folding mesh baskets, liner and stakes, whose specification is old enough and simple enough that every arsenal builds its own. | Hegemony issue; ordinary market stock across the League, the Diktat, Independent contractors, and Church and Knight garrisons; pirate and Pather sets recovered from wherever a line was dug in. | It is cover, not a wall: a soldier steps over it, shoots over it, and sees over it, and it can never be used to close a route, seal a doorway, or cut a map in two. It protects one boundary and gives nothing at all against fire from the other three sides. It is field kit rather than a controlled pattern, so it circulates through markets and salvage rather than through licences, and no faction needs a renamed equivalent. |
| `special.vibro-blade` | **VBL-1 Quillon vibro-blade** — a rugged Hegemony boarding pattern whose specification spread through coalition arsenals. | Hegemony issue; copied League and Diktat patterns; broad Independent, Pather, and pirate circulation; rare Tri-Tachyon monofilament tools of the same role. | Vibro and monofilament are one edge role; a monofilament label buys presentation and provenance, never extra reach or a hidden bonus. It is anti-personnel reaction equipment and cannot be used on machinery, walls, or emplacements. |

## Mech weapon components

These components define a commercial floor beneath formal military equipment.
They are deliberately worse because they are security or industrial conversions,
not because salvage itself implies poor quality. Recovery is one circulation
channel among manufacture, trade, capture, and black-market resale.

| Stable weapon id | Model and provenance | Plausible circulation | Limits and identity |
| --- | --- | --- | --- |
| `weapon.mech-muster-autogun` | **CAG-6 Muster commercial autogun** — a mass-market convoy and settlement-security weapon assembled around commodity receivers and exposed feeds. | Independent commercial worlds, local militias, auxiliaries, contractors, and captured pirate or Pather stocks. | Shorter-ranged, less accurate, less penetrating, and far less saturating than military chainguns or autocannons. It is common-market hardware, not Independent faction issue. |
| `weapon.mech-quarry-breaker` | **QBC-2 Quarry breaker cannon** — a mining and demolition projector pressed into battlefield service. | Industrial markets, remote extraction works, breaker yards, contractors, and outlaw conversions or recovery. | A crude short-range structural hit with poor accuracy, low armor penetration, slow cycling, and a tiny finite shell bin. Its wall damage does not make it a military Foundry Breaker equivalent. |
| `weapon.mech-pioneer-rocket` | **PRC-4 Pioneer utility rocket cradle** — an open rail for seismic charges and obstacle-clearing rockets adapted to shoulder hardpoints. | Industrial and frontier markets, public works, remote survey crews, contractors, and recovered outlaw use. | Four straight-fire rockets per trigger, finite salvos, loose dispersion, and no military boost guidance or indirect-fire capability. It is weaker and more exposed than a sealed SRM pod. |

All three are valid future subsystem-inventory or market stock. None belongs in
`FactionMechLoadouts` merely because a faction can plausibly buy, capture, or
maintain one; an authored doctrine must choose that weaker fit explicitly.

## Armor patterns

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
| `armor.aegis-composite` | **Aegis composite line suit** — a Tri-Tachyon response pattern with predictive threat displays and powered balance correction, which the suit spends as a **predictive volley**: a shoulder-launched brace of Halcyon micro-missiles at a self-picked target, the second authored integral system. | Corporate response teams and expensive licensed or recovered Independent examples. | Its speed and reduced incoming hit profile are bought with less capacity and rating than low-tech line armor; no neural-interface faction bonus exists outside the suit, and the rack is small, finite, and does not refill in the field. |
| `armor.palatine` | **Palatine legacy line suit** — a Church pattern rebuilt from inherited shells and artisan-fitted resistant plate. | Church sanctioned guards, Knight custodians, and rare licensed or recovered examples. | High armor rating comes with low capacity and a real mobility cost; consecration grants no hidden immunity. |
| `armor.furnace-line` | **Furnace state line suit** — thick petrochemical laminate over a conventional Sindrian pressure frame. | Diktat regulars, Lion's Guard formations, and exported or recovered Independent stocks. | Its deep capacity is paired with ordinary resistance, weight, and a conspicuous target profile. |
| `armor.reaver` | **Reaver reinforced rig** — a veteran Blackforge frame with powered bracing and additional ship plate. | Pirate warbands, Pather cells, and battlefield recovery. | Raw capacity and useful speed do not erase its poor armor rating or irregular maintenance. |
| `armor.heavy` | **XIV heavy battlesuit** — a Hegemony Domain-spec breach and shock pattern with powered bracing, which it spends as **assault bracing**: the most reliably available breach in the catalog. | Hegemony shock units, Knight custodians, Lion's Guard prestige formations, wealthy League forces, and rare recovered examples. | Heavy is a role, not universal top-tier faction armor. Tri-Tachyon heavy systems may become a separate item only if their mechanics justify it. |
| `armor.specter-heavy` | **Specter composite battlesuit** — a high-output Tri-Tachyon breach shell whose continuous threat prediction is spent as a **predictive breach**: the briefest and most frequent window in the family. | Corporate blacksite security and exceptionally rare patron or recovered Independent issue. | It is the fastest and hardest-to-hit heavy pattern, with materially less capacity/rating than XIV armor and no free drone or neural mechanic. |
| `armor.bulwark-heavy` | **Bulwark coalition battlesuit** — modular League heavy armor built around replaceable actuators and shared control standards, expressed as an **interlock advance** covering a wider front than one wearer needs. | League breach formations, wealthy member worlds, and mercenary patron or recovery channels. | Balance and repairability are its identity; it does not match each specialist heavy pattern at that pattern's strongest axis. |
| `armor.reliquary-heavy` | **Reliquary consecrated battlesuit** — an artisan-restored legacy shell entrusted through Knightly trials, whose **consecrated advance** buys almost no speed and the best screen anyone has. | Knights of Ludd and scarce Church or Independent patron/recovery channels. | Exceptional rating is paid for with lower capacity and the heaviest deliberate movement; spiritual sanction is provenance, not damage reduction. |
| `armor.lions-mantle` | **Lion's Mantle guard battlesuit** — prestige Sindrian armor whose oversized cooling funds one long, bright **blazon advance** per engagement and then needs a very long time to recover. | Lion's Guard patron issue and rare captured or diverted examples. | The largest formal-faction armor reserve is slow and easy to hit; spectacle is a liability as well as flavor. |
| `armor.foundry-breaker` | **Foundry-breaker industrial rig** — a cargo exoskeleton buried under illicit servos and welded ship plate. Its shoulder bracing and a salvaged riot screen are wired to one trigger as the **breaching assist**, the first authored integral system. | Pirate and Pather foundry cells; Independent access is recovery-only. | Enormous crude capacity cannot substitute for rating, mobility, or target denial, and the rig remains infantry rather than a mech. |

## Built-in loadout provenance

Built-in doctrines are recoverable establishments, not faction locks. They teach the
player how an institution composes shared equipment:

- **Frontier Security Equipment / Frontier Patchwork Protection** concentrates a few
  traded Lancers and locally assembled Ward kits above ubiquitous Rooks and fatigues.
- **Luddic Path Assault Equipment** is intentionally asymmetric: Rooks, blackforge
  Rattlers, industrial Breachhands, smoke, and a scarce captured heavy arm. It does
  not issue clean Lancers merely to mirror a state assault table.
- **League Coalition Line Equipment** mixes Rooks, Lancers, Stalwarts, Longbows, and
  Wayfarer smoke because interoperability, not one national forge, is its identity.
- **Corporate Blacksite Breach** pairs supported Lancers with serial-free
  Rattlers and one diverted Shattercap for a short blacksite action.
- **Hegemony Auxiliary Fire Support** centers serviceable Rooks, Stalwarts, and
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

**Breaching is the assault role's signature.** All six ASSAULT patterns carry a
version of the same breaching assist. That is a claim about the role rather
than about the tier: a future tier-IV scout or line suit would still declare
nothing. Each tradition spends the effect on the axis it is actually good at —
Hegemony doctrine on availability, League interoperability on a wider covering
arc, Tri-Tachyon prediction on a brief frequent window, Knightly custody on the
screen at the cost of nearly all the speed, Sindrian prestige on one long
conspicuous gesture, and the pirate copy on raw shove with nothing else.

The pirates' version is the only one without a proper designation, which is the
correct amount of respect for a cargo exoskeleton with servos welded to it.

**Payload delivery is not a role signature; it is one suit's answer.** Unlike
breaching, micro-missile support is not what any single role is *for* — faction
lore carries it across Hegemony, League, and Tri-Tachyon traditions alike, which
is three plausible homes and no obvious single one. The Tri-Tachyon Aegis
carries the first one, a **predictive volley**, because its own catalog copy
already says "predictive threat displays" and "a difficult firing solution":
the Specter spends that same prediction on evasion because breaching is what
the Specter is for, while the Aegis is a line suit built to make itself hard to
hit rather than to cross a room — so its display is spent locking a shot
instead of dodging one. It is not placed on a Hegemony or League pattern
because their assault heavies already carry the breach family, and stacking a
second system onto an already-decorated ASSAULT pattern would blur "one family,
one role."

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
