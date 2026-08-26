# Independents lore guide

Status: ACTIVE — implementation reference for Independent and mercenary ground-combat content.

Written: 2026-08-26

Updated: 2026-08-26 — established Independent diversity, equipment, mission, and battlefield direction.

Read `faction-lore-nouns.md` for evidence rules and shared setting laws.

## Canon anchors

Independents are explicitly not a unified faction. They are polities and free agents
defined mostly by non-alignment with a major power. They share reputation and trade,
and may cooperate in short-term security actions to preserve their freedom, but also
compete, distrust one another, and ignore a neighbor's trouble.

Current doctrine is a broad middle: moderate quality, quantity, aggression, and ship
size; mixed warships, carriers, phase ships, and combat freighters; wide commander-
skill possibilities; and equipment drawn from general Independent tags. The separate
mercenary faction data represents elite contractors that spawn as Independent, with
high officer and ship quality and a cross-faction equipment pool. Independent worlds
range from scientific stations and commercial ports to mines, local republics,
company towns, and frontier settlements.

## Strong ground-war inferences

- Every Independent force needs a local answer: municipal militia, corporate guard,
  planetary regulars, union defense, frontier volunteers, mercenaries, or a hybrid.
- Procurement follows trade access, local industry, patron wealth, salvage, and the
  threat environment. There is no universal Independent uniform or arsenal.
- Defenders know their own infrastructure and may cooperate rapidly when survival or
  autonomy is at stake, but aid from neighbors is contingent rather than guaranteed.
- Mercenary professionalism is a contract and company identity, not a separate
  civilization. Companies preserve personnel, reputation, equipment, and future work.
- Practical adaptation and mixed support are more characteristic than a single
  doctrinal extreme.

## Marine Operations direction

### Units and armor

Use local militia, constables, site security, professional planetary troops,
volunteers, prospectors, technical crews, hired specialists, and named mercenary
companies. Armor can span work suits, acquired service patterns, locally fabricated
plates, corporate security gear, and rare premium systems. Visual cohesion should
match the actual institution: a militia may be mixed, while a long-lived mercenary
company can be highly standardized.

The Independent fallback roster is a gameplay baseline, not permission to omit local
story. Name the employer, settlement, and reason these people fight.

### Weapons and equipment

Use service slug rifles as the most portable baseline, then add gauss, energy,
shredder, breaching, grenade, support, and specialist gear according to local supply.
Industrial colonies naturally field cutters and load frames; wealthy stations may buy
high-tech security systems; veteran mercenaries may maintain a curated mixed arsenal.

Equipment provenance is especially useful here. A recovered Hegemony automatic or
Tri-Tachyon pulse carbine can carry history without turning the unit into that faction.

### Tactics and command

Favor balanced local defense, terrain knowledge, flexible reinforcement, and
pragmatic withdrawal or negotiation when the contract or community can no longer be
saved. Mercenary units should understand force preservation and mission terms;
volunteers defending their homes may accept risks no contractor would.

### Facilities and objectives

Good sites include ports, mines, farms, research stations, union halls, municipal
centers, commercial depots, salvage yards, local defense batteries, habitat utilities,
and mixed-use settlements. Construction should reflect local industry and prior
ownership rather than a generic gray faction palette.

Capturing utilities, local government, payroll or contract records, docking control,
mining machinery, research archives, or the defense center can determine whether the
community keeps functioning. Independent owners normally preserve livelihood
infrastructure because no larger state is guaranteed to rebuild it.

### Contracts and voice

Credible offers include community defense, rescue, convoy or facility security,
recovery of stolen machinery, anti-pirate raids, labor or corporate disputes,
scientific extraction, debt enforcement, and mercenary work for almost any faction.
Briefings should use the patron's local culture and practical stakes. A miner, mayor,
station board, union organizer, scientist, and company commander must not share one
generic Independent voice.

## Anti-caricature rules

- Do not turn Independents into one liberal republic or neutral government.
- Do not equate non-alignment with weakness; some local institutions and mercenary
  companies are formidable.
- Do not make mixed equipment visually or mechanically random without provenance.
- Do not assume solidarity. Cooperation is real, but often short-term and conditional.

## Source anchors

- `strings/descriptions.csv`: `independent`, Nova Maxios and other Independent-world
  descriptions, frontier markets, and off-book economy passages.
- `world/factions/independent.faction`: decentralized behavior, mixed doctrine,
  commerce, legal policy, and mercenary fleet names.
- `world/factions/mercenary.faction`: elite contractor quality, broad `merc` equipment
  access, and Independent spawn identity.
- `campaign/rules.csv`: local contacts, mercenary contracts, scientific patrons,
  traders, miners, and Independent commission alternatives.
