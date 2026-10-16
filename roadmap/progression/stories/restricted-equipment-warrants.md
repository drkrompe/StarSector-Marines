# Restricted equipment and military warrants

> Approved military power suits stop circulating. A company fields one only
> because the tradition that builds it has put its name on a military warrant.

Status: PLANNED — first of three; `hegemony-auxiliary-warrant.md` and
`warrant-obligations.md` follow it.
Written: 2026-10-15

Read `progression-nouns.md` (Kit and armory), `campaign-kit-arc.md` (the
direction this implements), `meta-progression.md` (the growth rule) and
`s6-unlock-ladder-expansion.md` (the channels and the reachability audit)
before implementing.

## Problem

Every tier-4 power suit is a `PRESTIGE` template, and `PRESTIGE` is a
threshold rather than a gate. After fifteen high-risk victories the
independent recovery roll can hand a mercenary company a Lion's Guard Mantle.
At MRB 20 an independent patron can do the same, as can a Knights Reliquary
section or the Hegemony's own XIV shock armour. The tradition that builds the
suit is never consulted.

`a-small-war.md` already records this as a release dependency. Strict
story-first suit progression needs a shared acquisition policy that gates new
military suit awards behind a real procurement milestone across every source.
It must also keep what existing saves have learned, and must not close the
current routes until a later obtainable route exists.

## Scope

**A fourth access tier, `RESTRICTED`.** It ranks above `PRESTIGE` in
`EquipmentAccessTier`. Because every channel compares ordinals against at most
`PRESTIGE`, market, licence, patron and recovery all refuse it without
special cases. Each switch over the tier names it explicitly:
`requiredMrb` and `requiredRecoveryVictories` answer "never". Nothing else
reads the tier as "more Prestige".

**The Restricted set is the six approved tier-4 suits.** Each catalog entry
already names its tradition, so the warrant's owner comes from data:

| Template | Tradition |
| --- | --- |
| `armor.heavy` | hegemony |
| `armor.arbalest` | hegemony |
| `armor.specter-heavy` | tritachyon |
| `armor.bulwark-heavy` | persean |
| `armor.reliquary-heavy` | knights_of_ludd |
| `armor.lions-mantle` | lions_guard |

`armor.foundry-breaker` stays `PRESTIGE` and recoverable from pirates and the
Path. It is the crude road to powered armour, and it is deliberately not
warranted. The tier-3 recovered Specter and Arbalest stay `ADVANCED`. Rule:
a template is Restricted because it is *approved military issue*, not because
it is powerful.

**A military warrant**, a new progression noun. It is a named, persistent
authorisation from one tradition that opens the specific Restricted templates
it lists. Always write it in full. The campaign already has a *civil-security
procurement warrant*: Rusk's bounded terms in `a-small-war.md`, which
explicitly grant "no military procurement status". That warrant is a contract
term. It lets the company keep listed refurbishment patterns from one job and
opens no access tier. A military warrant is the escalation the Agreus ending
points at. Neither noun may stand for the other. Player-facing text, the
noun doc and data always say "military warrant"; below, "warrant" is only
shorthand inside this story. A warrant has three parts:

- an issuing tradition;
- the templates it opens, all of that tradition's Restricted templates or a
  named subset;
- the event that granted it.

Holding a warrant does not teach anything. It makes the listed templates
*acquirable*, and the tradition's ordinary licence and patron channels may
then offer them. A warrant is permanent once granted. Losing it is
`warrant-obligations.md`'s business.

**Standing warrants.** These are the route that lets the gate close without
stranding anything. Each tradition grants its own warrant once two things
hold: the company is at the Prestige band (MRB 20), and its relationship with
that faction is at a declared threshold above the licence's Favorable. The
proposed threshold is Cooperative. One rule covers all five issuing factions.
`hegemony-auxiliary-warrant.md` adds an earlier, authored route for the
Hegemony on top of it. Lion's Guard and Knights kit is therefore not "never".
It is "only for a company that has become one of theirs", which most
companies will not.

**Every consumer honours the gate:**

- Faction market, licence and patron offers, and the recovery and loot rolls,
  refuse a Restricted template unless the company holds a warrant that names
  it. In addition, a warranted template is offered only by its issuing
  tradition's own pool: an independent patron never sells Hegemony military
  issue.
- `EquipmentCollectionCurve` (the breadth safety net) never grants a
  Restricted template.
- Victory milestones never grant one.
- `ReleasedKit` never lists one as releasable to the company's polity. A
  warrant authorises the company, not whoever it arms.
- `CampaignDebugIntel` may still teach them, as test acquisition.

**The reachability audit.** `FactionEquipmentCatalog.validateReachability`
already accepts three kinds of owner: starter issue, an eventually eligible
faction source, or a declared story owner. It gains a fourth: a warrant rule
that names the template. A Restricted template with no warrant rule fails the
build. So does a Restricted template that is still reachable through an
unwarranted source.

**Fleet Armory legibility.** A Restricted card the company has not unlocked
shows who issues it and what opens it, for example *"Hegemony military issue
— requires a Hegemony military warrant"*. It never reads as a locked Prestige card
with a victory count. A held warrant appears on the company's record with its
issuer, its date and what it opens.

**Save compatibility.** Templates a save has already learned stay learned.
Squads already fitted keep their suits. The gate refuses only *new*
acquisition. No migration revokes anything. A save that learned a Restricted
suit through the old open routes is grandfathered, not punished.

## Out of scope

- The authored Hegemony warrant beat (`hegemony-auxiliary-warrant.md`).
- Obligations and lapse (`warrant-obligations.md`).
- New loadout cards for the pod, pack and revetment (`campaign-kit-arc.md`'s
  arc map; not part of the tier).
- Enemy and ally rosters. Defenders keep fielding approved suits;
  `faction-ground-rosters.roster.json` is untouched.

## Acceptance

- The six templates are `RESTRICTED` and Foundry-Breaker is still `PRESTIGE`.
  `loadoutCardSheet` shows XIV Shock, Specter Protection, Bulwark, Reliquary
  and Lion's Mantle as Restricted. Its sources column lists only their issuing
  tradition's licence or patron channel, each marked as warrant-gated.
- A unit test on `EquipmentAcquisitionEligibility` shows that maximum progress
  (every victory, MRB 20) still refuses a Restricted template on every source
  without a warrant. With the issuer's warrant, it allows the issuer's own
  licence and patron channels and nothing else.
- A unit test on the standing-warrant rule grants it at the Prestige band with
  the threshold relationship, and not one band or one relationship step below.
- A unit test pins that the safety net, victory milestones and polity release
  never yield a Restricted template.
- `validateReachability` fails for a Restricted template with no warrant rule,
  and for one that keeps an unwarranted source.
- A save that learned `armor.heavy` before the change still holds and fits it.
- Fleet Armory snapshot at 1920x1080, 100% UI scale, shows a locked Restricted
  card with its issuer, and a held warrant on the company record.

## Plan

1. Tier and the eligibility switches, with the unit tests. No data moves yet.
2. Military warrant record in `CampaignState`: issuer, templates, grant reason. It
   serialises in the interned-id shape the other id columns use, and skips
   unknown templates on read like `ReleasedKit` does.
3. The standing-warrant rule, evaluated where faction standing is already
   read for licences.
4. Gate the consumers (offers, recovery, safety net, milestones, polity
   release) and extend the audit.
5. Move the six templates to `restricted` and trim their pool entries to the
   issuing tradition's licence and patron. Regenerate `loadoutCardSheet`.
6. Armory legibility and the snapshot.
7. Fold: the Restricted tier and military warrants enter `progression-nouns.md` (Kit and
   armory). `a-small-war.md`'s release dependency cites the shipped gate.
