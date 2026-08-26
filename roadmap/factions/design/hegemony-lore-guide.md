# Hegemony lore guide

Status: ACTIVE — implementation reference for Hegemony ground-combat content.

Written: 2026-08-26

Updated: 2026-08-26 — established the Hegemony's institutional, equipment, mission, and battlefield direction.

Read `faction-lore-nouns.md` for evidence rules and shared setting laws.

## Canon anchors

The Hegemony is a martial successor state founded by a lost Domain battlegroup. It
extends Domain-style martial law through its own reading of emergency protocols and
frames military discipline as the means by which civilization, stability, and an
eventual restoration of the Domain survive. Civilian governments exist, vary by
world, and may retain elections, but remain subordinate to military authority.

Its current fleet data reinforces institutional conservatism: very high officer
quality, low hull quality, large warships, no phase doctrine, low randomization, and
ballistic/ordnance officer emphasis. Its catalog favors armor, ballistic weapons,
flak, missiles, standardized auxiliaries, and durable low-tech hulls. Hegemony text
also depicts training facilities, cadets, layered bunkers, hidden batteries, military
intelligence, inspections, and a bureaucracy that takes logistics and legality
seriously even when it bends either to the Great Cause.

## Strong ground-war inferences

- Ground forces are organized, ranked, and supplied through a professional military
  system rather than assembled as personal retinues.
- Veteran leadership and drilled combined arms matter more than pristine individual
  equipment. Old, repaired, or inelegant kit can remain trusted service issue.
- Garrisons build depth: checkpoints, mutually supporting positions, reserve routes,
  hardened command posts, ammunition points, and redundant fire control.
- Orders, identification, chain of custody, and after-action accounting matter.
  Covert activity exists, but the institution prefers a lawful-looking record or a
  classified order over improvisational deniability.
- Recapture of an official installation is both a military and legitimacy problem;
  an organized counterattack is more characteristic than abandoning it casually.

## Marine Operations direction

### Units and armor

The baseline force is disciplined line infantry in standardized sealed combat armor.
Recon troops may use lighter suits, but should still read as issued military kit.
Heavy battlesuits are utilitarian breach or shock assets: slab protection, service
markings, replaceable plates, external load points, and little ornamental excess.

Use clear echelons: local auxiliaries or militia, Navy/Marine regulars, veteran shock
troops, combat engineers, heavy-weapons teams, and scarce heavy-suit operators.
Veterans should be distinguished by training, coordination, and selected equipment,
not a universal health multiplier.

### Weapons and equipment

Prefer service slug rifles, gauss marksman or anti-armor weapons, squad automatics,
grenade launchers, micro-missile support, and robust crew-served ballistic systems.
Pulse or laser carbines fit elite shock teams and specialists, but should not displace
the faction's ballistic and logistical identity. Thermal breachers, arc cutters,
combat engineering charges, recoil assistance, tactical HUD relays, and reliable
field medical gear fit naturally.

Avoid making all Hegemony equipment crude. The faction preserves Domain practice and
can field sophisticated systems; its visual language is standardized, armored,
maintainable, and institutionally controlled rather than primitive.

### Tactics and command

Favor deliberate advances, suppression, bounded maneuver, prepared defenses, reserve
commitment, and organized recapture. A Hegemony commander should accept casualties to
hold an important line, but should understand logistics, fields of fire, and force
preservation. Brave frontal action is part of its officer culture; suicidal stupidity
is not.

### Facilities and objectives

Good Hegemony locations include training compounds, inspection posts, records and
signals centers, armories, logistics depots, buried command bunkers, civil-defense
shelters, shield-control nodes, and dispersed anti-orbital batteries. They should
have visible access control, standardized signage, redundant routes, and old Domain
infrastructure kept operational by layers of repair.

Capturing fire control, codes, magazines, command staff, or inspection archives can
unlock orbital approach, reduce reinforcement quality, reveal concealed batteries,
or establish legal evidence. Destroying a Hegemony-owned forge, food depot, or civil
system is normally an exceptional denial action, not routine Hegemony patronage.

### Contracts and voice

Credible offers include restoring order, retaking official sites, rescuing personnel,
recovering restricted technology, suppressing mutiny or piracy, securing evidence,
and conducting an operation whose public record differs from its sealed orders.
Briefings favor authority, duty, procedure, restoration, and the public good. A
Hegemony patron can be humane, cynical, ambitious, exhausted, or corrupt without the
institution ceasing to sound like the Hegemony.

## Anti-caricature rules

- Do not reduce the Hegemony to fascist cannon fodder; it contains sincere public
  servants and preserves real civil capacity alongside coercion.
- Do not make every officer inflexible. Doctrine is conservative, not incapable of
  adaptation.
- Do not treat anti-AI enforcement as mere superstition; it is law, state identity,
  wartime memory, and a claim to Domain legitimacy.
- Do not give every soldier elite gear because officer quality is high. Training and
  equipment condition are separate axes.

## Source anchors

- `strings/descriptions.csv`: `hegemony`, `planet_chicomoztoc`, `planet_eventide`,
  `planet_coatl`, `station_coatl`, Valkyrie, Enforcer, Dominator, and Onslaught rows.
- `world/factions/hegemony.faction`: doctrine, known weapons and hullmods, ranks,
  illegal commodities, and custom behavior.
- `campaign/rules.csv`: Hegemony commission, AI-core turn-in and inspection language,
  recruitment flavor, and High Hegemon/HEGINT dialogue.
