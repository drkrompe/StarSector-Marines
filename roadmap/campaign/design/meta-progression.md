# Meta-progression

Status: DRAFT

Written: 2026-09-02

## Purpose

Meta-progression is the long arc of the mercenary company: what it accumulates,
what that admits it to, and what it costs to keep. This document is the
designer-facing frame that the campaign's child domains share. It exists
because five of them — progression, contracts, mission tiers, personnel, and
the living world — each defined their own thresholds without a shared
vocabulary, and the thresholds happen to agree: MRB credibility admits
Advanced equipment at 5 and Prestige at 20; operational recovery admits the
same after 5 and 15 victories; runway reads Desperate under six months and
Seasoned over twelve; officer rank caps the company at 3, 6, 10, 16 and 24
squads. Those are one ladder described five times. This document names the
ladder so that the next domain can join it instead of adding a sixth
description.

It owns the currency vocabulary, the stage vocabulary, the growth rule, and
the boundary between the company and the player's vanilla holdings. It owns
**no threshold**: every number above stays with the noun doc that settles it,
and a domain that moves one has moved a stage boundary and says so here.

Nothing in this document is a player-facing surface. There is no stage meter,
no rank-up, no progress bar. The player perceives the arc as the world's
changing posture toward the company ([[feedback_world_reactive_over_expressive]]
in project memory, `themes.md` here): seen but not heard, then acknowledged,
then treated as a peer, then — exceptionally — the protagonist.

## The four currencies

Everything the company accumulates or suffers reduces to four currencies.
Every existing gate in the campaign reads one of them.

| Currency | What it is | Produced by | Consumed by | Owner |
| --- | --- | --- | --- | --- |
| **Standing** — who will hire you | MRB credibility (cross-client) and house relationships (local). Deliberately two things; neither substitutes for vanilla faction reputation. | Settled contracts; attributed civil-war participation | Patron tier and therefore operation tier; licensed and patron equipment access; the contract feed; T3 claim eligibility | `contracts-nouns.md`, `living-world-nouns.md` |
| **Capability** — what you can field | The Armory's collection: template cards, grade, pattern tier, integral systems. Officer capacity. Lift and berths. Mechs. | Recovery, market, license, patron reward, victory milestones | Fielding the operation tier a patron demands | `progression-nouns.md`, `personnel-nouns.md`, `mechs-nouns.md` |
| **Reach** — where, and how much at once | Geographic range; standing commitments that bind detachments; feed range; location-bound investment | Stationing contracts; infrastructure | Everything that needs the company somewhere specific | `contracts-nouns.md`, `infrastructure-nouns.md` |
| **Pressure** — what it costs to exist | Runway. Upkeep, retainers, and the unbuilt sinks: scale inefficiency, licensing, insurance, infrastructure upkeep | Growth itself | The player's choices among commitments and locations | `economy.md` |

Standing and Capability are deep and shipped. Reach is nearly empty:
`infrastructure-nouns.md` is the reserved slot and holds no implementation.
Pressure has runway and nothing that bites at scale. That asymmetry is the
structural fact this frame exists to make visible: the arc today is pressured
early survival followed by a middle game that stabilises and never
destabilises again, which is the vanilla snowball the campaign tier was built
to resist. `economy.md`'s "late-game choices about the cost of scale" has no
mechanism behind it yet.

## Stages

Stages fall out of the currencies rather than being declared. They are a
designer's vocabulary for talking about pacing, and a checklist for asking
where a new feature first becomes reachable.

| Stage | World posture | Standing | Capability | Pressure | Reach |
| --- | --- | --- | --- | --- | --- |
| **0 Opening** | Indifferent | None; the Independent broker's ladder (`early-operation-nouns.md`) | One green squad on starter issue | Debt | Docked |
| **1 Seen, not heard** | Indifferent; generic briefings, passive world news | Tier 1 patrons; MRB below 5 | Common access; tier I–II patterns; 1–3 squads | Under six months of runway is the long default, not a dip | Docked, in system |
| **2 Acknowledged** | Peripheral references; patrons say they hear good things | Tier 2; MRB 5+; stationing opens and competes with vanilla time | Advanced access; tier III; 6–10 squads | Six to twelve months; retainers and upkeep start to matter | The feed; first infrastructure |
| **3 Peer** | Named; negotiated with; competing offers | Tier 3; MRB 20+; Planetary Assault, Conquest | Prestige access; tier IV specialist doctrine; 16–24 squads | Twelve months and more — where scale inefficiency must bite | Standing commitments the company cannot all honour |
| **4 Protagonist** | The sector pivots on the company | Kingmaker chains; the Claimant League; the testament (`t3-endgame-nouns.md`) | Full Strength | Exceptional | Exceptional |

Stage 4 is not a stage every company reaches. Most companies stay companies;
the Sforza outcome is the exception, and `themes.md` and `campaign-nouns.md`
already say so.

The stage boundaries are the thresholds the owning docs already hold. When a
domain retunes one, it should look across this row: an MRB gate that moves
without the runway band or the officer cap moving is a stage that has come
apart.

## The growth rule

A feature that touches progression states four things before it is
contracted:

1. which currency it **produces**;
2. which currency it **consumes** or gates on;
3. the earliest **stage** at which it is reachable;
4. its **counter-pressure** — what it costs, or what it puts at risk.

A feature that produces Capability or Standing with no counter-pressure is a
snowball and is refused at design time. A feature whose counter-pressure is
"the player might lose the battle" has named a battle outcome, not a campaign
cost, and needs a second answer. The rule is what lets the frame keep
growing: a new domain declares its producer and consumer edges and joins the
graph rather than adding a parallel ladder.

## Order of work

The gaps, in the order they should close:

1. **Pressure at scale.** Scale inefficiency and upkeep are the mechanism
   behind `economy.md`'s promise and the only thing that makes Stage 3 a
   stage rather than a plateau. Runway bands remain the cash-side anchor;
   new inputs tighten them, never loosen them.
2. **Reach.** Infrastructure is the mitigation for that pressure and the home
   for the polity boundary below. It cannot land before there is a pressure
   to mitigate, or it is a Capability source with no counter-pressure.
3. **Tiered rewards** (`tiered-rewards.md`), so scale is paid for as well as
   charged for.
4. **Contention for Standing.** Nothing today competes for it. Rival
   companies as institutions that bid and poach, and house relationships that
   conflict so that serving one costs another, are what turn Stage 3 into
   "choose whom to disappoint" rather than "more available work".

## The company and the polity

Vanilla lets the player found colonies and, in effect, a faction. The campaign
tier does not model that today, and its only contact with it is accidental:
the throne-claim writeback rejects a market whose faction is not the recorded
incumbent, which happens to exclude a player-owned market. This section makes
the boundary a policy.

The **company** is the campaign tier's player: the named mercenary
organisation with standing, personnel, an Armory, and a hull to live on. The
**polity** is the player's vanilla holdings: colonies, their markets, and the
player faction. Vanilla merges the two into one avatar. The campaign tier keeps
them apart and treats the polity, from the company's point of view, as a
venue.

- **Never a client.** The polity cannot hire the company. Work done on the
  polity's behalf earns no MRB credibility and no house relationship. This
  closes the obvious exploit and keeps Standing honest: credibility is what
  other people think of the company.
- **Never a house.** The living world seeds no houses, stakes, ambitions, or
  chains on player-faction markets, and a throne claim never targets one. The
  polity is outside the political field, not a participant in it.
- **A venue for Reach.** Player markets are the natural home for
  location-bound investment once `infrastructure-nouns.md` has a first slice:
  the depot, the training ground, the administrative office the contract
  board floats. On a foreign market that investment pays rent and answers to
  the host house. On the polity's own market it pays build and upkeep only,
  and ties the company's reach to a place it must defend. This is the "base
  of operations" reward of the BattleTech lineage, reached through the vanilla
  colony path rather than through T3, so the two do not compete for the same
  fantasy.
- **A battlefield.** Vanilla's pressure on the polity — pirate and Pather
  raids, punitive expeditions — can resolve as a ground operation the company
  fights rather than as vanilla's abstract raid roll. The polity is the
  employer at no fee and no credit. The detachment that fights is the
  company's own, so its casualties, kit, and recovery are settled by the
  ordinary personnel and loot authorities. This is the one place other than
  T3 where the campaign tier writes vanilla state, and it does so only through
  vanilla's own raid outcome path; the seam is described below.
- **Scrutiny, optional.** The Mercenary Review Board exists in fiction
  because governments fear the concentration of mercenary power. A company
  that owns worlds is a power, not a contractor. A later story may cap, tax,
  or condition MRB credibility on the polity's size. That would make colony
  ownership a real choice against the company's own career instead of a free
  bonus, which is the [[feedback_hard_failure_preference]] shape.

## Defending the polity: the vanilla seam

Vanilla already models "marines defend your colony" as a number: a
player-owned market's defender strength counts the marines sitting in its
storage. The campaign tier's offer is to replace that number with a battle
when the player is there to fight it, and to leave the number alone when they
are not.

The shape of a vanilla colony raid in 0.98a is a fleet group
(`GenericRaidFGI`) with four actions: prepare at the source, travel, a
**payload** action at the target system, and return. The payload is
`FGRaidAction`: its fleets are ordered to the target markets and hold there
for up to the payload duration, and each fleet that reaches a market performs
the raid — a strength ratio against the market's ground defences that costs
stability and, for some expeditions, disrupts an industry. If the player is
out of spawn range the whole payload autoresolves on the same ratio. A raid
against a market that is `RECENTLY_RAIDED` by the same faction, or that has
already been raided its allotted times, is skipped.

Two things make the ground option honest rather than a hack:

- **Going home is a supported outcome.** `FleetGroupIntel.abort` expires the
  route, marks the current action finished, gives every fleet a return
  assignment, and notifies the fleet group's listener. For a pirate raid that
  listener is the hostile-activity factor, and its abort handler is what
  grants the piracy respite. A ground defence that wins can therefore end the
  invasion the same way a lost space battle would, and collect the same
  consequence.
- **The number is still there when the battle is not.** The
  `GROUND_DEFENSES_MOD` stat is how every vanilla defence industry contributes
  to defender strength. A detachment stationed on the polity can contribute to
  that stat for as long as it is stationed, so the autoresolve path and the
  raid-in-absence still feel the company's presence. That is the Garrison
  stationing model already in `contracts-nouns.md`, pointed at the polity.

The player-facing shape is one option, offered where the raid fleets are:
while the payload action is live and a raid fleet is at the market, the planet
interaction can offer to meet the landing on the ground. A win aborts the
fleet group. A loss lets vanilla's own raid resolution run. There is no third
result and no partial credit; the ground battle settles only whether the raid
lands.

Laws for this seam:

- The campaign tier ends an invasion only through the fleet group's own abort
  and applies a raid only through vanilla's own raid path. It never edits
  market ownership, stability, or industries directly, and it never routes
  through the T3 endgame consumer.
- A polity defence earns no MRB credibility, no house relationship, no
  contract settlement, and no patron reward. Its only campaign consequences
  are the personnel and recovery facts of the battle itself.
- A polity defence is one operation in the ordinary battle pipeline: mission
  type, tier, and map come from `mission-tier-nouns.md` and `precincts.md`,
  with the raid fleet's strength as the tier input rather than a patron's
  demand.

Open questions:

- Older raid shapes — `RaidIntel` and its stages, punitive expeditions,
  Hegemony inspections — are separate machinery. Whether the first slice
  covers only the fleet-group shape, or wraps both behind one seam, is
  unsettled.
- A fleet group holds a single listener slot and the hostile-activity factor
  occupies it. The abort path works without touching that slot; a design that
  needs to observe the raid rather than end it must find another hook.
- Whether a Garrison detachment stationed on a **patron's** market answers a
  vanilla raid through the same seam. It should: that is the stationing
  response `contracts-nouns.md` already describes, and this seam would give
  it a vanilla trigger.

## Boundaries

`campaign-nouns.md` owns the umbrella vocabulary and points here for the
long-arc frame. `t3-endgame-nouns.md` owns the only path to vanilla political
change and now states that the polity is never a claim target. Each currency's
thresholds stay with the owner named in the table above. `economy.md` remains
the direction for Pressure; `infrastructure-nouns.md` remains the direction
for Reach.
