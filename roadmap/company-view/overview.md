# Company View — the army as a hierarchy

> The player runs a merc company. The mod already models captains,
> persistent six-marine fireteams, and named soldiers. No surface presents
> them as an organization, and the organization is destroyed the moment
> they deploy.

**Status:** design stage. Six stories contracted (C1–C6), none started.

## Concept

One consistent three-level object — **company → fireteam → marine** —
readable everywhere the player looks at their own force: in the fleet
between contracts, on the pre-battle deployment screen, in the battle HUD,
and in the after-action. Larger cards per deployed formation, each card
made of its fireteams, each fireteam expandable to its individuals.

The point is *grouping for comprehension*, not new mechanics. The player
should be able to answer "what shape is my army in right now, and where is
it" without arithmetic and without opening three screens.

## What exists today

### The campaign model already has the levels — minus the top one

`marine/MarineRoster` (891 lines) owns captains, fireteams, soldiers, and
the armory:

- `MarineCaptain` — rank, XP, status, traits, commendations.
  `Rank.fireteamCap()` bounds how many whole fireteams a captain may lead
  (Sergeant = 7 teams / 42 marines — the enum comment calls that "the full
  opening company").
- `MarineSquad` — the persistent six-marine fireteam. `CAPACITY = 6`,
  `homeCaptainId`, `stationingContractId`, member ids.
- `MarineSoldier` — name, aptitude, XP, status (ACTIVE / WIA / MIA / KIA),
  `unavailableUntilDay`, primary + grade, secondary, armor.
- `roster.squadsCommandedBy(captainId)` already returns exactly the set a
  "company card" would draw.

There is **no company entity**. "Company" exists only as loose aggregate
counts (`PersonnelReadiness.companyReady()`, `companyShortfall()`) and a
comment in `Rank`.

### The surfaces are flat lists

- `ops/SquadDeploymentScreen` (140 lines) — two columns of
  `[X] Name  n/6 RTD` plus WIA/MIA/KIA counts. No captain, no whereabouts,
  no individuals.
- `ops/ArmoryScreen` (1406 lines) — PERSONNEL / LOADOUTS tabs, per-soldier
  paper doll. Organized by inventory and by marine, not by formation.
- `ops/StationingScreen` (581 lines) — the only place stationing bindings
  are visible, and it is a separate screen from the roster.

### Battle has two of the three levels, keyed on the wrong id

- `battle/ui/panel/SquadOverviewPanel` — one 34px row per player squad:
  battle squad id, alive/peak, alert dot, weapon summary, morale bar.
- `battle/ui/panel/SquadDetailPanel` — one row per marine in the selected
  squad: HP bar, primary/secondary symbols + ammo, profile, role badge.
- They share one dock slot keyed off `ui/picking/Selection`, which is
  **view-only**: no order channel exists. The player's only in-battle
  agency is the six `battle/power/CommandPower`s.

So the drill-down already half exists — but it is keyed on the *ephemeral*
battle squad id, shows `SQUAD 3` instead of a name, and has no tier above
the squad.

### Fireteam identity is destroyed at the campaign → battle seam

This is the structural blocker, and it is the reason the battle HUD cannot
show the hierarchy today:

1. `ops/detachment/CampaignMarineDeployment.freeze` flattens the selected
   fireteams into a flat `List<MarineLoadout>` seat list.
2. `MarineLoadout` carries `campaignSoldierId` — individual identity
   survives — but **no fireteam id**.
3. `battle/air/InfantryPayload.tryDeploy` mints one battle `Squad` per
   *shuttle mission* on the first deboard; every seat in that sortie joins
   it. A battle squad is "whoever rode this dropship."
4. Shuttle capacities do not even match the fireteam: `ShuttleType`
   AEROSHUTTLE/KITE carry **4**, against a 6-marine team. A fireteam is
   split across lifts by arithmetic, not by intent.

`SquadDeploymentScreen` says this out loud in its own subtitle: *"tactical
squads form around their actual lifts."* That was a reasonable simplifying
choice; it is now the thing standing between the player and a legible army.

### Nothing aggregates whereabouts

`MarineSquad.stationingContractId` + `roster.squadsStationedOn(contractId)`
already model "this team is away on a garrison contract", and casualty
recovery already models "back on day N". No surface rolls those up, so the
player has to remember which teams are out.

## Scope

**In:**

- The hierarchy as a derived data model (C2).
- Fireteam identity surviving the drop, so a battle squad can name itself
  back to a campaign fireteam (C1).
- The card stack in Marine Ops — company band, fireteam cards, expand to
  individuals (C3).
- Whereabouts and deployed state on the card (C4).
- The same three levels in the battle HUD (C5).
- After-action attributed per fireteam (C6).

**Out:**

- **Giving the player in-battle orders.** `Selection` stays view-only in
  this track. The card is the natural surface for orders later — it is
  where "2nd Fireteam" becomes a clickable object — but proving the view
  comes first, and an order channel needs a design pass against the
  `MissionCommand` tier that currently owns `Squad.assignedObjective`
  (see [`../ai/stories/12-squad-of-squads.md`](../ai/stories/12-squad-of-squads.md)).
- **What a single marine row says about quality.** Aptitude bars, XP tiers,
  trait cards, and career history belong to progression
  [S8](../progression/stories/s8-roster-legibility.md). This track owns the
  *grouping*; S8 owns the *contents* of the leaf row. C3 reuses S8's type
  scale and leaves room for its row rather than re-litigating it.
- Changing fireteam size, rank caps, or the deployment-selection rules
  (`CaptainDeploymentPolicy` is consumed as-is).
- New persisted state. The roster stays the single source of truth; every
  rollup here is derived (and therefore xstream-free).

## Design commitments

1. **The fireteam is the persistent unit of organization; the battle squad
   is its in-battle instance.** One id maps them. Everything else in this
   track follows from that.
2. **A card is a captain's command.** "Deployed company cards" (plural)
   maps onto the thing the game already deploys: a captain plus the
   rank-capped set of fireteams they lead. A company-wide band sits above
   the cards. See Open questions — the alternative is one card for the
   whole roster.
3. **Company is derived, never persisted.** Built from `MarineRoster` on
   demand with deterministic ordering. No save-format change, no xstream
   surface, nothing to migrate.
4. **Read-only first.** Every story here ships a view. Mutation stays where
   it already lives (armory transfers, deployment toggles, stationing).
5. **One selection model, two hosts.** Company → fireteam → marine behaves
   the same in the ops screens and in the battle HUD, so the player learns
   it once.
6. **Type scale per role, not one font.** Display `orbitron24aabold`,
   header `orbitron20aa`, body `insignia17LTaa`, dense rows
   `insignia15LTaa`, numeric columns `arial14` (the only vanilla face with
   tabular digits). Bars and icons carry density before text does. Verified
   at UI scale 1.0x / 1.25x / 1.5x. Rationale and the measured table live in
   [S8](../progression/stories/s8-roster-legibility.md).
7. **The battle tier stays campaign-free.** C1 carries a plain id + label
   string across the seam — never a roster reference, never a lookup from
   inside `battle/`. Same rule the campaign→battle bridge already follows
   with `TargetProfile`.

## Stories

| Story | Title | Depends on |
| --- | --- | --- |
| [C1](stories/c1-fireteam-identity-through-the-drop.md) | Fireteam identity through the drop seam | — |
| [C2](stories/c2-formation-model.md) | Formation model — the hierarchy as data | — |
| [C3](stories/c3-company-card-stack.md) | Company card stack (fleet view) | C2 |
| [C4](stories/c4-whereabouts-and-deployed-state.md) | Whereabouts: where every team actually is | C2, C3 |
| [C5](stories/c5-battle-hud-company-rollup.md) | Battle HUD company rollup | C1, C2 |
| [C6](stories/c6-after-action-by-fireteam.md) | After-action by fireteam | C1 |

C1 and C2 are independent and can land in either order. C1 is the enabling
slice for anything that shows a *deployed* force under its real names.

## Cross-refs

- [`../ai/overview.md`](../ai/overview.md) — the squad and commander tiers
  this view makes legible. `Squad`, the four squad systems, and the seven
  `MissionCommand` implementations are what the HUD reports on.
- [`../progression/stories/s8-roster-legibility.md`](../progression/stories/s8-roster-legibility.md)
  — per-marine quality legibility and the type scale. Adjacent, not
  overlapping; C3 and S8 meet at the leaf row.
- [`../campaign/personnel/`](../campaign/personnel/) — the persistent
  fireteam spine, stationing, enlistment, and debrief this view reads.
- [`../command-powers/overview.md`](../command-powers/overview.md) — the
  other player-agency track; shares the battle HUD's bottom dock.

## Open questions

- **What is one card — a captain's command, or the whole roster?** The
  design commitment above picks *captain's command*, because that is what
  `CaptainDeploymentPolicy` already gates and what "cards" (plural)
  implies. The alternative — one company card for the whole roster, with
  the captain as a badge on each fireteam — is simpler and better for a
  one-captain early game. Decide before C3; C2 models both, so the captain
  grouping is a level that can be collapsed.
- **When a fireteam is split across two lifts, is it one battle squad or
  two?** C1 has to answer this. One squad keeps the name honest but lets
  leader-pull cohesion drag members across the map between two landings;
  two instances (`1st Fireteam (A)` / `(B)`) keep the AI honest and the
  name ugly. Leaning: one squad per fireteam **per landing zone**, joined
  across lifts that share an LZ.
- **Does the reserve pool get a card?** It is a `MarineSquad` with
  `reserve() == true` and no captain, currently filtered out of the
  deployment list. Probably a distinct band, not a card.
