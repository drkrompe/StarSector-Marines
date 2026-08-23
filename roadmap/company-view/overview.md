# Company View — the army as a hierarchy

> The player runs a merc company. The mod already models captains,
> persistent six-marine fireteams, and named soldiers. No surface presents
> them as an organization, and the organization is destroyed the moment
> they deploy.

**Status:** ACTIVE
**Written:** 2026-08-22
**Updated:** 2026-08-23 — C14 makes the fire team the Fleet Armory's template-card tier.

## Concept

One consistent organizational object — **company → squad → fire team → marine** —
readable everywhere the player looks at their own force: in the fleet
between contracts, on the pre-battle deployment screen, in the battle HUD,
and in the after-action. One card per officer's command, each card made of
its squads. The Fleet Armory expands a squad through its three equipment-bearing
fire teams to the named marines filling their billets; battle command remains
squad-granular.

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
- The card stack in Marine Ops — company band, squad rows, expand to
  individuals (C3).
- Whereabouts and deployed state on the card (C4).
- The same three levels in the battle HUD (C5).
- After-action attributed per squad (C6).
- The organization itself: squad leaders, a coherent officer ladder, and
  the squad's size and one word for it (C7).
- Transport capacity denominated in fire teams, and squads that arrive
  across passes staying one squad (C8).
- Fire teams as the AI's maneuver element — stable bounding pairs, fix-and-
  flank, team-aware role assignment (C9).
- Fire-team template cards as the armory's routine equipment unit, with
  reusable four-billet designs and inventory-gated assignment (C14).
- A campaign-map home for the company, reachable while not at a planet,
  carrying standing, running deadlines, and the army management that never
  needed a market (C10).

**Out:**

- **Giving the player in-battle orders.** `Selection` stays view-only in
  this track. The card is the natural surface for orders later — it is
  where "2nd Squad" becomes a clickable object — but proving the view
  comes first, and an order channel needs a design pass against the
  `MissionCommand` tier that currently owns `Squad.assignedObjective`
  (see [`../ai/stories/12-squad-of-squads.md`](../ai/stories/12-squad-of-squads.md)).
- **What a single marine row says about quality.** Aptitude bars, XP tiers,
  trait cards, and career history belong to progression
  [S8](../progression/stories/s8-roster-legibility.md). This track owns the
  *grouping*; S8 owns the *contents* of the leaf row. C3 reuses S8's type
  scale and leaves room for its row rather than re-litigating it.
- Changing the deployment-selection *rules*. C7 resizes the squad, adds
  enlisted ranks and re-denominates the rank caps (marines → squads), but
  `CaptainDeploymentPolicy`'s logic — whole-squad selection, capped by the
  commanding officer — is consumed as-is.
- New persisted state *for the view*. The roster stays the single source of
  truth and every rollup here is derived (and therefore xstream-free). The
  one exception is C7's two genuine pieces of organizational data — a
  marine's enlisted rank and a squad's leader.

## Design commitments

1. **The squad is the persistent command unit; the fire team is its equipment
   and maneuver unit.** One squad id maps the campaign unit to its in-battle
   instance. A squad is **twelve marines in three four-marine fire teams**,
   led by an NCO — see C7 (`c7-organization-and-ranks.md`). Fire teams are a
   player-facing level in the Fleet Armory, where reusable four-billet cards
   are assigned, and the AI's maneuver element in C9
   (`c9-fire-teams-as-the-maneuver-element.md`). They do not become separate
   deployment selections or player order targets.
2. **A card is one officer's command — the company.** *Settled
   2026-08-22.* The named officer is the company commander; the squads
   under them are led by NCOs who are not officers. Early game that is one
   officer with two or three squads; as the organization grows past a
   company, a second officer means a second card, which is the merc-company-
   becomes-an-institution arc the campaign already tells. Where the current
   rank ladder makes this incoherent — a Private capped at five marines, a
   Sergeant capped at forty-two — the ladder changes
   (C7 (`c7-organization-and-ranks.md`)).
3. **The company rollup is derived, never persisted.** Built from
   `MarineRoster` on demand with deterministic ordering. No cached view
   state. (C7's enlisted rank and squad leader are persisted roster
   *facts*, not rollups. Nothing is shipped and there are no saves to
   preserve, so neither carries a migration burden — see
   C7 (`c7-organization-and-ranks.md`).)
4. **Mutation lives at the tier that owns it.** Deployment toggles remain on
   squads, Armory equipment mutation moves to fire-team cards, and stationing
   remains whole-squad. Read-only views consume those facts without inventing
   parallel state.
5. **One hierarchy, host-appropriate depth.** Company → squad is stable
   everywhere. The Armory drills through fire team to marine because it owns
   equipment; the battle HUD may stop at squad or use team grouping without
   exposing team-level player orders.
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
8. **A lift carries at least one whole fire team, and a squad forms up
   before it advances.** *Settled 2026-08-22.* Transport capacity is
   denominated in four-marine teams; only the heaviest transport lands a
   whole squad in one pass. Later arrivals join the same squad and catch
   up, and a still-assembling squad holds at its landing zone rather than
   feeding in piecemeal — under the shipped 9x lethality scale, trickling a
   squad forward is a wipe
   (C8 (`c8-lift-capacity-and-multi-pass-drops.md`)).
9. **Scale is governed at the source, and the view degrades rather than
   forbids.** Hundreds of squads is not a state the meta game or the UI can
   carry, so the officer rank cap and the lift capacity bound what reaches
   one battle (a dozen squads, realistically) — that is the governor, not a
   UI limit. The *roster* still grows past that as the organization becomes
   a battalion, so the fleet view compacts and paginates within the officer
   grouping. Silent truncation is never acceptable; the off-screen count is
   always stated.

## Stories

| Story | Title | Depends on |
| --- | --- | --- |
| ~~C1~~ (`c1-fireteam-identity-through-the-drop.md`) | Squad identity through the drop seam — **shipped 2026-08-22** | — |
| C2 (`c2-formation-model.md`) | Formation model — the hierarchy as data | — |
| C3 (`c3-company-card-stack.md`) | Company card stack (fleet view) | C2 |
| C4 (`c4-whereabouts-and-deployed-state.md`) | Whereabouts: where every team actually is | C2, C3 |
| C5 (`c5-battle-hud-company-rollup.md`) | Battle HUD company rollup | C1, C2 |
| C6 (`c6-after-action-by-fireteam.md`) | After-action by squad | C1 |
| ~~C7~~ (`c7-organization-and-ranks.md`) | Organization and ranks — **shipped 2026-08-22** | — |
| C8 (`c8-lift-capacity-and-multi-pass-drops.md`) | Lift capacity in fire teams, multi-pass drops — **slices 1-3 shipped** | pairs with C1 |
| C9 (`c9-fire-teams-as-the-maneuver-element.md`) | Fire teams as the maneuver element | C7 |
| ~~C10~~ (`c10-company-between-contracts.md`) | The company between contracts: a campaign-map home — **shipped 2026-08-23** | — |
| C11 (`c11-the-contract-board.md`) | The contract board: seeing work you are not standing on | C10 slice 1 |
| ~~C12~~ (`c12-the-debug-company.md`) | The debug company: a fixture that mimics the campaign — **slices 1-2 shipped 2026-08-23** | C1, C7 |
| C13 (`c13-the-task-force.md`) | The task force: command scoped per officer — **slice 1 shipped 2026-08-23** | C7, C12 |
| C14 (`c14-fire-team-template-cards.md`) | Fire-team template cards — **slice 1 shipped 2026-08-23** | C7 |

C1, C2, C7, and C10 are independent and can land in any order. C1 is the
enabling slice for anything that shows a *deployed* force under its real
names; C8 is what makes the deployed force match the one the player
selected; C7 settles the language and the command scope the UI stories
render. C10 built the between-contracts home the track's first surface
needed — the campaign-map entry point and a planet-free host — and reserved
the pane C3 and C4 later furnish. C11 fills that home's outward-facing half:
C10 answers "how is my company", C11 answers "where should it go next".

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

## Settled questions

- **What is one card?** One officer's command — the company. Squads under
  it are led by NCOs, and the rank ladder changes to make that coherent.
  See design commitment 2 and C7 (`c7-organization-and-ranks.md`).
- **Is a squad split across lifts one battle squad or two?** One. The
  capacity floor makes the split rare, and where it still happens the later
  arrivals join the same squad and catch up. See commitment 8 and
  C8 (`c8-lift-capacity-and-multi-pass-drops.md`).
- **How big is a squad?** Twelve, in three fire teams of four. *Revised
  from six on 2026-08-22.* The quality model in
  `progression-nouns.md` shipped a
  **9x infantry lethality scale** (pulse rifle vs an unarmored marine:
  ~30 s to ~3.3 s), which leaves a six-marine squad past
  `SquadFallbackSystem`'s trigger ratio within seconds of contact and gives
  the shipped two-team bounding overwatch nothing to split. Twelve restores
  the headroom those systems assume, matches the real structure, and keeps
  the row count low — a 72-marine company is six rows.

- **Where is the company readable between contracts?** From a campaign-map
  ability-bar button opening a planet-free host, not from a route inside the
  planet-scoped Marine Ops dialog. *Settled 2026-08-22.* The ability bar is
  already the campaign-only HUD element, so it hides itself with the rest of
  the HUD and needs no visibility gate of ours; the host is the one
  G32 (`g32-player-event-popup.md`) shipped. See C10
  (`c10-company-between-contracts.md`), which supersedes C4's slice 3.

- **Where does the player see work they are not standing on?** On a contract
  board in the same planet-free host, with distance governing *resolution*
  rather than existence — docked shows everything, in-system shows everything
  at every market in the system, and an earned sector-wide feed shows that an
  offer exists without its terms. *Settled 2026-08-22.* Acceptance stays local
  at every tier, because the thing that makes an employer a character rather
  than a row is the briefing, and the briefing is attached to arriving. See
  C11 (`c11-the-contract-board.md`).

- **Do lapsing offers belong on C10's clock pane?** No — obligations bite and
  opportunities lapse, so they are different surfaces. A missed response
  deadline costs reputation and loses a garrison; a missed offer costs nothing
  but the job. Offer expiry lives on C11's board, where the action is, and
  C10's clock pane renders two sources rather than three. *Settled
  2026-08-22.*

## Open questions

- **Does the reserve pool get a card?** It is a `MarineSquad` with
  `reserve() == true` and no captain, currently filtered out of the
  deployment list. Probably a distinct band, not a card.
