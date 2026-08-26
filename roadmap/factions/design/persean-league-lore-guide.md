# Persean League lore guide

Status: ACTIVE — implementation reference for Persean League ground-combat content.

Written: 2026-08-26

Updated: 2026-08-26 — established the League's institutional, equipment, mission, and battlefield direction.

Read `faction-lore-nouns.md` for evidence rules and shared setting laws.

## Canon anchors

The Persean League is an alliance formed to resist Hegemony domination. Its member
polities may disagree or even fight one another, but unite against external threats.
The League protects the sovereignty of constituent governments rather than promising
individual liberty or egalitarian rule; its laws specifically avoid intervention in
members' domestic politics.

Current doctrine is midline and coordinated: warship-heavy fleets, moderate numbers
and quality, missile specialization, electronic warfare, coordinated maneuver, and a
broad mixture of ballistic and energy equipment. League worlds include oligarchic
Kazeron, monarchic and media-rich Fikenhild, corporate interests, local military
forces, and varied civic arrangements. “League issue” is therefore a procurement and
interoperability problem, not one uniform national culture.

## Strong ground-war inferences

- League forces combine local militaries, planetary guards, contracted formations,
  and coalition assets with uneven traditions and equipment condition.
- Competent headquarters emphasize liaison, deconfliction, shared fire plans, and
  flexible reinforcement because political cohesion cannot be assumed.
- Member forces are strongly attached to local infrastructure and sovereignty. A
  League operation on a member world should know whose property and authority are at
  stake.
- Balanced combined arms and missile/fire-support coordination are more characteristic
  than one extreme technology or protection philosophy.
- Coalition commanders can be adaptable, but political caveats, prestige disputes,
  and incompatible procedures create friction.

## Marine Operations direction

### Units and armor

Use planetary regulars, city or installation guards, League expeditionary cadres,
member-state specialists, reservists, contracted auxiliaries, and political liaison
staff. Light and line armor should be practical and varied, with shared recognition,
communications, or ammunition standards layered over local manufacture. Heavy suits
fit wealthy member guards and coalition breach units, not every line squad.

Visible variation is desirable: two League squads may share role and command net
without sharing helmet, plate pattern, or heraldry. Variation must stay readable and
must not become random stat superiority.

### Weapons and equipment

Prefer dependable slug and gauss rifles, squad automatics, grenade launchers, guided
micro-missiles, portable sensors, and selected pulse or beam weapons. Missile and
support-weapon identity should appear through coordinated volleys, target marking,
and ammunition infrastructure rather than every infantryman carrying rockets.

Local equipment can range from inherited Domain arms to new League-associated
weapons. Captured, imported, and corporate systems are credible when provenance and
maintenance remain visible.

### Tactics and command

Favor balanced lines, distributed support, reinforcement of threatened objectives,
cross-unit coordination, and adaptation to local terrain. A League commander should
be capable of concentration without always behaving as one centrally standardized
army. Coalition friction belongs in mission setup or command constraints, not as
randomly incompetent unit AI.

### Facilities and objectives

Good sites include member-state capitols, customs and liaison posts, missile magazines,
planetary defense coordination, commercial spaceports, shipyards, estates, media
centers, floating arcologies, and mixed public-private industrial districts. Heraldry,
procedures, and construction should identify the owning polity beneath League-wide
systems.

Capturing a defense network, docking control, assembly archive, local broadcast
center, or coalition command post can determine whether a world resists, negotiates,
or accepts outside support. League clients normally preserve member infrastructure;
denial orders require consent, existential threat, or factional intrigue.

### Contracts and voice

Credible offers include defense assistance, relief of a member garrison, convoy or
spaceport security, recovery of a captured installation, anti-Hegemony operations,
interdiction of Pathers or warlords, coalition liaison, and deniable action that no
single member wants attributed to itself. Briefings invoke sovereignty, treaty duty,
local law, commercial interest, and the exact authority of the patron.

Always identify the subculture behind the contract. A Kazeron magnate, Westernesse
official, local admiral, and League secretariat should not speak with one generic
democratic voice.

## Anti-caricature rules

- Do not describe the League simply as “the democracy faction.” Member sovereignty,
  not universal civil liberty, is its unifying principle.
- Do not make variation mean disorder in every battle. Coalition institutions exist
  because members can coordinate against common threats.
- Do not make all equipment mediocre. Wealthy or specialized members can field
  excellent systems, but access is uneven.
- Do not erase local interests when the League owns a world or offers a contract.

## Source anchors

- `strings/descriptions.csv`: `persean`, Kazeron and League-world descriptions,
  `fikenhild`, Champion, Conquest, Pegasus, and League-associated weapon rows.
- `world/factions/persean_league.faction`: missile specialization, coordinated and
  electronic-warfare skills, mixed catalog, fleet composition, and legal policy.
- `campaign/rules.csv`: League commission, blockade, membership, and member-polity
  dialogue.
