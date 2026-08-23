# Company View — Next Session

## Where we are

Track opened 2026-08-22 from an inventory of the shipped squad AI and
commander ("squad of squads") tiers. Ten stories contracted (C1–C10).
**Two have shipped work.** `c7-organization-and-ranks.md` is complete —
slices 1–4; its slice 5 moved into C1, which owns the seam it needs.
`c10-company-between-contracts.md` has slices 1–2 in: the campaign-map
entry point (`2b959e44`) and the standing pane (`b204c237`). Everything
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

**C1 (`c1-fireteam-identity-through-the-drop.md`).**
No dependencies, small, and it unblocks C5 and C6. Slice 1 (fields +
deterministic freeze ordering) is pure plumbing verifiable by unit test.

C2 (`c2-formation-model.md`) is equally
unblocked and can land in parallel; it is the prerequisite for all three UI
stories and its second slice (rewire `SquadDeploymentScreen`'s counts, no
visible change) is a safe proof.

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
host dismisses cleanly. Slices 3 (running clocks) and 4 (roster) are next; the
clocks pane is where G32's **Hold** finally goes.

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
7. **Take C8's capacity change now, re-tune after.** The opening ladder's
   force ratios are mid-playtest against today's seats, and raising the
   floor moves them 50–100% — tuning against numbers we intend to replace
   is wasted work. Flagged in
   [`../campaign/early-operations/next-session.md`](../campaign/early-operations/next-session.md).

### Consequences worth knowing before starting

- **Scale is governed at the source, not by the UI.** The officer rank cap
  and the lift capacity together bound what reaches one battle — a dozen
  squads, realistically. Hundreds of squads is a state neither the meta
  game nor the HUD can carry, so future cap numbers get picked with that
  ceiling in mind. The *roster* still outgrows it, which is why the fleet
  view paginates and the battle view does not need to.
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

- `2b959e44` — C10 slice 1: a campaign-map door into the company
- `b204c237` — C10 slice 2: standing, led by months of payroll
- `2e187f54` — C7 slices 1+2: squad of twelve in three fire teams; officer
  ranks counted in squads
- `976bb87a` — C7 slice 3: `EnlistedRank`, squad leaders, deterministic
  promotion on loss
- `2786a3ed` — C7 slice 4: display sweep to "squad"; leader and NCO rank in
  `ArmoryScreen`
