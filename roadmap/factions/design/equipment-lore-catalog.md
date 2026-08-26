# Equipment lore catalog

Status: ACTIVE — canonical provenance and faction-fit reference for shipped Marine Operations equipment.

Written: 2026-08-26

Updated: 2026-08-26 — grounded the first shipped weapon, special-equipment, armor, and built-in loadout catalog.

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

## Armor patterns

| Stable id | Shipped identity and origin | Credible circulation | Deliberate limits |
| --- | --- | --- | --- |
| `armor.field-fatigues` | **Domain-pattern vac fatigues** — ubiquitous sealed working dress with a load web. | Every human faction can maintain local versions. | This is unpowered field kit, not a light powered-assault suit. |
| `armor.militia` | **Ward security kit** — a frontier category for locally assembled torso plate, sealed helmet, and impact layers. | Independent, Church, planetary auxiliary, Diktat security, pirate, and Pather use. | Ward is a trade category rather than one faction's standardized factory model. |
| `armor.outlaw` | **Blackforge patchwork rig** — industrial exoframe, salvaged security shell, ship plate, and hand-fitted seals. | Pirates, Pathers, deniable raiders, and frontier recovery. | Formal factions do not need a parade-quality Blackforge equivalent; captured suits remain visually irregular. |
| `armor.scout` | **Janus scout suit** — Tri-Tachyon composite reconnaissance protection with sensor and electronic-warfare integration. | Corporate teams; imported League and Diktat specialists; gray-market Independent users. | It is not ordinary Hegemony, Church, pirate, or Pather line protection. |
| `armor.combat` | **Bastion line armor** — a Kazeron-led interchangeable coalition pattern. | League forces and exported, licensed, or captured Hegemony, Independent, and Diktat stocks. | Shared line role does not make it the Hegemony's preferred institutional suit. |
| `armor.line` | **Legionary line suit** — standardized, pressure-sealed Hegemony armor designed for campaign repair. | Hegemony regulars, licensed Diktat formations, Church and Knight custodians, and battlefield recovery. | Tri-Tachyon internal forces prefer integrated composite systems; pirates and Pathers do not maintain clean Legionary issue at scale. |
| `armor.heavy` | **XIV heavy battlesuit** — a Hegemony Domain-spec breach and shock pattern with powered bracing. | Hegemony shock units, Knight custodians, Lion's Guard prestige formations, wealthy League forces, and rare recovered examples. | Heavy is a role, not universal top-tier faction armor. Tri-Tachyon heavy systems may become a separate item only if their mechanics justify it. |

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
- **Sindrian Civilian Security Equipment** distributes imported Janus suits and Ward
  kits unevenly; **Hegemony Line Protection** concentrates Legionary/Bastion plate;
  **Tri-Tachyon Recon Protection** concentrates Janus suits on high-value operators.

## Availability law

`faction-equipment.faction-equipment.json` is the live player-acquisition mapping.
Ordinary market stock represents sustainable circulation; license and patron sources
represent institutional control; recovery represents plausible capture or salvage.
Absence is meaningful. Do not fill every faction row with every tactical role, and do
not infer that a defender roster weight guarantees a lawful player market source.

Use exact vanilla campaign ids at this boundary, including `sindrian_diktat`,
`lions_guard`, and singular `remnant`. Remnant equipment declares explicit human
incompatibility instead of falling through to the Independent pool.
