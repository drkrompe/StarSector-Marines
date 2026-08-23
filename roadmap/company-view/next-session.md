# Company View — Next Session

## Where we are

Track opened 2026-08-22 from an inventory of the shipped squad AI and
commander ("squad of squads") tiers. Twelve stories contracted (C1–C12).
**Four have shipped work.** `c7-organization-and-ranks.md` and
`c1-fireteam-identity-through-the-drop.md` are complete;
`c8-lift-capacity-and-multi-pass-drops.md` has three of four slices in,
`c10-company-between-contracts.md` is complete — all four slices, now in
`complete/` — and `c12-the-debug-company.md` has two of three. Everything
else is design stage.

The organization is now settled in code, so every later story renders a
real hierarchy rather than a proposed one:

- A squad is twelve marines in three four-marine fire teams
  (`MarineSquad.TEAM_SIZE`, `TEAMS_PER_SQUAD`, `CAPACITY`). Team membership
  is **derived from roster order**, not stored.
- Officer rank caps command in squads: `Rank.squadCommandCap`, Lieutenant 3
  through Colonel 24. The company starts under a Lieutenant.
- Squads have NCOs: `EnlistedRank` on `MarineSoldier`,
  `MarineSquad.leaderSoldierId`, re-derived by
  `MarineRoster.refreshLeadership` after any membership or fitness change.
  Successors are picked by rank, then experience, then a stable id
  tiebreak.
- The UI says "squad" for the twelve and "fire team" for the four.
- A deploying seat carries a `CampaignSquadTag` — squad id, frozen label,
  leader flag, expected strength. Null on every generated spawn.
- `CampaignSquadIndex` groups landing marines by `(campaign squad, LZ)`, so
  a squad crossing in three lifts is one unit on the ground and the
  campaign NCO leads it.
- `ShuttleType` capacity is declared in whole fire teams;
  `Squad.FIRE_TEAM_SIZE` is the single authority for the number.
- `SquadFormUpSystem` holds a still-arriving squad at its LZ by clearing the
  advancing assignment, once, after the commander pass.
- **Debug missions field a real company.** `DebugCompany.roster(stage)`
  builds a detached `MarineRoster`, so a debug deployment goes through the
  same `freezeSelection` the campaign does and earns the same tags, NCO
  leaders and multi-lift joins. `DebugCompanyStage` picks the point on the
  campaign arc (First Contract / Established / Veteran Company) and moves
  squad count, experience, kit and mech support together.

Read [`overview.md`](overview.md) first — it holds the inventory of what
exists today and the seven design commitments the stories assume.

## The finding that created this track

Campaign fireteam identity does not survive deployment:

- `CampaignMarineDeployment.freeze` flattens selected fireteams into a flat
  `List<MarineLoadout>` seat list.
- `MarineLoadout` carries `campaignSoldierId` but **no fireteam id**.
- `InfantryPayload.tryDeploy` mints one battle `Squad` per *shuttle
  mission*, so a battle squad is "whoever rode this dropship."
- `ShuttleType.AEROSHUTTLE` / `KITE` carry 4 seats against a 6-marine
  `MarineSquad.CAPACITY`, so lifts split teams by arithmetic.

Everything that wants to show a *deployed* force under its real names is
blocked on that seam. That is C1 (`c1-fireteam-identity-through-the-drop.md`).

## Recommended pickup

**Play a mission first.** Four shipped stories changed what a deployment
*is* — twelve-marine squads, lifts denominated in fire teams, a squad that
assembles at its LZ before it steps off, and a debug company that finally
exercises all three — and none of the behavioural half has been seen in
play. Two numbers are first guesses:
`SquadFormUpSystem.FORM_UP_TIMEOUT` (60s) and the opening ladder's force
ratios, which `../campaign/early-operations/next-session.md` has been
holding for exactly this.

C12 makes that pass cheap: a DEBUG mission at **Established** fields three
twelve-marine squads with NCO leaders, and a transport picker set below the
company's strength forces the multi-lift path the form-up gate exists for.
Before C12 a debug mission could not reach any of it.

For force-ratio work the **squad dial** in the briefing's COMPANY DEBUG row
is the tool — it resizes the company from 0 to 40 squads (0-480 marines)
without changing its quality, so "what does this mission actually need" is
one picker rather than a code edit.

**Then C2 (`c2-formation-model.md`).** It is the prerequisite for all three
UI stories, and its second slice (rewire `SquadDeploymentScreen`'s counts,
no visible change) is a safe proof. C5 and C6 are unblocked now that C1 has
landed, and both consume the label the seam carries.

**`c8-lift-capacity-and-multi-pass-drops.md` slice 4** is the one loose end
in otherwise-complete work: the rejoin state for a genuinely-late arrival.
Smaller than it was — the form-up gate removed the worst case.

**C10 — The company between contracts** (`c10-company-between-contracts.md`)
is unblocked too, and is the one story here that ships player-visible value
without C2 first: two of its three panes (standing, running deadlines) read
state that is already persisted or already computed, and its third pane is
the reserved space C3 and C4 later furnish. It also gives
G32 (`g32-player-event-popup.md`)'s **Hold**
option somewhere to go — today a deferred event popup dismisses into nothing.
**Slices 1-2 shipped** (`2b959e44`, `b204c237`): the ability, the planet-free host, and a
standing pane led by runway in months of payroll. The spike question slice 1
existed to answer is **settled in game**: the ability opens the screen and the
host dismisses cleanly. A follow-up (`8ac4116a`) fixed the pane printing an
unknown upkeep as `Cr. 0` and gave the debug panel a day-skip that actually
moves the clock. **Slice 3** (`20f498de`) landed the clocks column — and with it
somewhere for G32's **Hold** to come back to — and **slice 4** (`822065cd`) the
roster column and the armory route. The story is closed; the next thing to touch
this screen is C3's card stack landing in the ROSTER column, or
C11 (`c11-the-contract-board.md`) adding the board. `c11-the-contract-board.md` was
contracted 2026-08-22 and takes lapsing offers off that pane.

~~C7 — Organization and ranks~~ **shipped 2026-08-22**; the record is in
`c7-organization-and-ranks.md` under `complete/`. One consequence worth
carrying into the next pickup: a stationed squad's monthly retainer
doubled, because `StationingContractTerms` is linear in committed marines
and nothing was tuned to compensate.

## Decisions taken (2026-08-22)

1. **A card is one officer's command — the company.** The named officer
   commands the company; its squads are led by NCOs who are not modelled as
   officers. The rank ladder is incoherent for that role today (a Private
   capped at 5 marines, a starting Sergeant capped at 42 because seven
   six-marine teams had to fit), so it changes: officer ranks denominated
   in squads, a separate enlisted ladder, and an explicit squad leader.
   That shipped as `c7-organization-and-ranks.md`.
2. **A lift carries at least one whole fire team, and a split squad stays
   one squad.** Capacity is denominated in four-marine teams — only a
   Valkyrie lands a whole squad in one pass — and late arrivals join their
   squad and catch up rather than forming a new unit. That is
   C8 (`c8-lift-capacity-and-multi-pass-drops.md`), and it settles
   C1's open split-lift question.
3. **A squad is twelve marines in three fire teams of four.** *Revised
   from six.* Progression S1 shipped a **9x infantry lethality scale**
   (pulse rifle vs an unarmored marine: ~30 s to ~3.3 s), so six puts a
   squad past `SquadFallbackSystem`'s trigger ratio within seconds and
   leaves the shipped two-team bounding overwatch nothing to split. Fire
   teams are modelled but behind the scenes — the AI's maneuver element
   (C9 (`c9-fire-teams-as-the-maneuver-element.md`)), not a card
   level. Lifts are denominated in teams, so only a Valkyrie lands a squad
   intact and an assembling squad forms up at its LZ before advancing.
4. **Large organizations degrade, never get forbidden.** Past ~8 squads the
   UI compacts, past ~20 it paginates within the officer grouping, and the
   off-screen count is always stated. C3 and C5 carry the tiers.

5. **Save compatibility is not a constraint.** Nothing is shipped to
   anyone and there are no saves to preserve, so `Rank` can be edited
   freely — no legacy alias map, no migration slice. (The mechanism still
   matters once saves exist: xstream writes enums by name, so deleting a
   constant is the breaking direction; adding a field is the safe one.)
6. **The company is reachable from the campaign map, not from a planet.**
   The between-contracts surface gets an ability-bar button opening the
   planet-free host [G32](../campaign/contracts/complete/g32-player-event-popup.md)
   shipped. The ability bar is already the campaign-only HUD element, so it
   hides itself whenever a core tab, dialog, or menu opens and needs no
   visibility gate of ours. Checked while deciding: `ArmoryScreen` has zero
   market/planet references and `StationingWithdrawalService.withdraw` takes
   no planet, so most roster work was gated by where its button sits, not by
   any fiction. That is C10 (`c10-company-between-contracts.md`), and
   it supersedes C4's slice 3.
7. **Offers are already sector-wide; only the view is local.**
   `ContractGenerator` iterates every house in the sector and never reads the
   player's position — the gating is two lines in
   `MissionGenerator.generateFromContracts` (patron matches the client, market
   matches the docked planet), which exist because a patron client inside a
   planet interaction was the only surface there has ever been. So the board is
   a presentation story, not a model change. Distance governs **resolution**,
   not existence, and acceptance stays local at every tier so the patron-voice
   machinery keeps its hook. The sector-wide feed is gated by MRB standing,
   then a one-time activation fee, then cancellable monthly maintenance. That
   is C11 (`c11-the-contract-board.md`).
8. **Take C8's capacity change now, re-tune after.** The opening ladder's
   force ratios are mid-playtest against today's seats, and raising the
   floor moves them 50–100% — tuning against numbers we intend to replace
   is wasted work. Flagged in
   [`../campaign/early-operations/next-session.md`](../campaign/early-operations/next-session.md).

### Consequences worth knowing before starting

- **The offer caps are the real constraint on the board.**
  `GLOBAL_OFFER_CAP` is 20 and `PER_PATRON_OFFER_CAP` is 1, so the sector holds
  twenty live offers and a patron never shows a choice. Filters have nothing to
  bite on until that moves, which is why C11's slice 1 is tuning rather than
  UI. It also means pagination is not needed yet — `ScrollRegionWidget` exists
  and twenty rows fit it.
- **A derived number with no input says so.** `MonthlyReport` does not exist
  until an in-game month rolls over, and `PlayerEventInbox` is empty before any
  contract arrives, so every pane in C10 has a legitimate blank state. Render
  the reason, never a zero — a zero is a claim about the company.
- **Dated behaviour is testable now.** The debug intel's **Skip 1 / 7 / 30
  days** advances every campaign-tier timer, one full system pass per day
  crossed. It does **not** advance vanilla's economy, so no amount of skipping
  produces a monthly report.

- **Scale is governed at the source, not by the UI.** ~~The officer rank cap
  and the lift capacity together bound what reaches one battle — a dozen
  squads, realistically.~~ **Contradicted by play 2026-08-23:** a CONQUEST
  at HIGH risk authorises **40 drops = 480 seats**, and play reports it
  wanting 200-400 marines — 17 to 34 squads, against `Rank.COLONEL`'s cap of
  24. The lift was never the constraint; the *command ladder* is. Three ways
  out (more officers per deployment, higher rank caps, or CONQUEST/HIGH is
  mistuned) are laid out in `c12-the-debug-company.md`; **none is chosen
  yet**. The roster still outgrows the battle either way, which is why the
  fleet view paginates and the battle view does not need to.
- `AirSystem.java:471` resets `mission.squadId` per cycle today, so every
  reinforcement wave currently mints an unrelated squad. C8 slice 2 scopes
  that; C5 must not paper over it with display-only grouping.
- The rejoin behaviour C8 needs is mostly already there —
  `RegroupPosture` + `InfantryCohesion.cohesionOverride`. Extend that
  layer; a second cohesion mechanism would fight the first.

## Boundaries worth restating

- **No orders.** `Selection` stays view-only in this track. The card is the
  obvious future order surface, but `Squad.assignedObjective` is owned by
  the `MissionCommand` tier and a player channel into it needs its own
  design pass.
- **S8 owns the leaf row.** Progression
  [S8](../progression/stories/s8-roster-legibility.md) defines what a
  single marine row says (aptitude bars, XP tiers, traits, career) and the
  per-role type scale. This track owns the grouping and reserves the space.
- **Nothing new gets persisted.** `MarineRoster` stays authoritative; every
  rollup here is derived.
- **Contracts track is live.** C4 reads stationing state that G31/G32 are
  actively reshaping — read
  [`../campaign/contracts/next-session.md`](../campaign/contracts/next-session.md)
  before touching term/deadline fields.

## Commit chain

- `00ace1b0` — C1 (all four slices) + C8 slice 1: the identity seam, and
  lift capacity in fire teams
- `6e3908b0` — C8 slice 3: an assembling squad holds at its LZ
- C12 slices 1-2: the debug company — a detached `MarineRoster` through the
  same deployment path, staged along the campaign arc

- `2b959e44` — C10 slice 1: a campaign-map door into the company
- `b204c237` — C10 slice 2: standing, led by months of payroll
- `8ac4116a` — C10 slice 2 follow-up: an unknown month says so; debug
  **Skip 1 / 7 / 30 days** via `CampaignClock.skipDays`
- `20f498de` — C10 slice 3: obligations only, soonest first; Respond routes
  through the presenter
- `822065cd` — C10 slice 4: the people move under the ROSTER header; formation
  band, next recovery, and the armory door
- `ce031aba` — fix: an armed convoy vehicle no longer crashes the damage
  pipeline (an APC turret's attacker id has `GROUND_IDENTITY`, not
  `IDENTITY`, and the telemetry seam's faction read is fail-loud)
- `2e187f54` — C7 slices 1+2: squad of twelve in three fire teams; officer
  ranks counted in squads
- `976bb87a` — C7 slice 3: `EnlistedRank`, squad leaders, deterministic
  promotion on loss
- `2786a3ed` — C7 slice 4: display sweep to "squad"; leader and NCO rank in
  `ArmoryScreen`
