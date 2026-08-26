# Knights of Ludd lore guide

Status: ACTIVE — implementation reference for Knights of Ludd ground-combat content.

Written: 2026-08-26

Updated: 2026-08-26 — established the Knights' institutional, equipment, mission, and battlefield direction.

Read `faction-lore-nouns.md` and `luddic-church-lore-guide.md` before using this guide.

## Canon anchors

The Knights of Ludd are the military branch of the Church of Galactic Redemption,
modeled on monastic warrior societies. Within Church territory they enforce
“Technological Correctness” and religious law; outside it they may attempt the same
covertly. Hesperus is their center, ringed with monasteries and defended by unseen
bunkers, heavy batteries, and missile silos. Its shrine commemorates dead Knights
with weathered armor plates and empty helmets.

Canon also presents the Knights as capable of institutional debate. A council of
inquisitors considered destroying recovered Domain knowledge, but an expert persuaded
them to preserve it; that archive enabled recovery of the Invictus design. Their
equipment access favors armor, hardened systems, ordnance capacity, missiles, and
survivability. Their minimal faction file intentionally inherits much of its broader
identity from the Church rather than defining a separate economy.

## Strong ground-war inferences

- Knights are a smaller, professional, ideologically screened force rather than the
  ordinary mass of Church defenders.
- Technical expertise and theological authority coexist. Inquisitors, armorers,
  archivists, and commanders decide whether a device is permissible and how it may be
  used.
- Fortified sanctuaries, concealed batteries, disciplined heavy weapons, and prepared
  counterattack are central to their defensive identity.
- Outside Church space they favor compact expeditions, covert enforcement, recovery,
  interdiction, and work through sympathetic local networks.
- Sacrifice means accepted duty and risk; it does not require tactically pointless
  death or Pather-style indiscriminate violence.

## Marine Operations direction

### Units and armor

Use sworn line Knights, aspirants or retainers, Knight-inquisitors, combat engineers,
relic custodians, heavy-weapons teams, and consecrated heavy-suit operators. Their
armor should be rugged, sealed, carefully maintained, and personalized through vows,
campaign marks, scripture, or repaired plates while retaining a recognizable order.

Heavy powered armor is particularly appropriate here: not because the Knights ignore
Luddic teaching, but because dangerous tools can be borne as a controlled obligation
in defense of the faith. Keep the equipment's mechanical role ordinary and put the
religious distinction in provenance, presentation, and access.

### Weapons and equipment

Prefer maintained slug and gauss arms, heavy ballistic support, simple missiles,
breaching charges, arc cutters, hardened communications, and selected relic-grade
systems. Energy or advanced sensor equipment may be sanctioned for a specific task
and custodian. Its rarity should feel like controlled access, not writer inconsistency.

Knights should not inherit Pather IED and martyr equipment by faction-name proximity.
If a story explores captured Pather devices, make custody and disposal the point.

### Tactics and command

Favor disciplined advance, mutually supporting heavy positions, stubborn defense,
organized counterattack, and mission-focused sacrifice. Knights can close aggressively
on a profane machine or breach, but they should preserve comrades, relics, civilians,
and defensible ground when those are the mission's purpose.

### Facilities and objectives

Good sites include monastery-fortresses, armories, reliquaries, archives awaiting
judgment, inquisitorial workshops, concealed batteries, silo complexes, hardened
pilgrim shelters, and training courts built into hostile terrain. Their spaces should
join austerity, ritual, accumulated campaign history, and serious military engineering.

Capturing the fire-control network, shield chapel, archive seal, reliquary vault, or
senior custodian can determine whether orbital support is possible and whether a
recovered device is preserved, condemned, or fought over.

### Contracts and voice

Credible offers include relic recovery, interdiction of proscribed technology,
defense of a shrine, rescue of pilgrims or clergy, destruction of a specific
abomination, covert enforcement outside Church space, and containment of a Pather
cell. Briefings emphasize vows, custody, correct use, witness, duty, and the exact
moral status of the target.

## Anti-caricature rules

- Do not write Knights as Pathers with better armor. They are hierarchical,
  institutional, and tied to the settled Church.
- Do not make them ignorant of technology. Their authority requires technical
  understanding as well as doctrine.
- Do not make every Knight a heavy-suit operator or inquisitor.
- Do not assume every advanced artifact is destroyed; canon explicitly supports
  debate, preservation, and controlled use.

## Source anchors

- `strings/descriptions.csv`: `knights_of_ludd`, `hesperus`, `hesperus_shrine`,
  `shrine_hesperus`, and Invictus rows.
- `world/factions/knights_of_ludd.faction`: identity, illegal commodities, hardened
  hullmod set, names, portraits, and voices.
- Church campaign and Hesperus material in `campaign/rules.csv` for inquisitorial and
  institutional voice.
