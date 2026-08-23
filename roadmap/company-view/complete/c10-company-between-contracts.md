# C10 — The company between contracts: a campaign-map home

> The mod models a merc company in detail and gives the player nowhere to
> stand and look at it. Every roster surface hangs off a planet
> interaction, so "how is my army doing" is only answerable while docked.

**Status:** **shipped 2026-08-23** — slices 1–4 (`2b959e44`, `b204c237`,
`8ac4116a`, `20f498de`, `822065cd`). Independent of
C1 (`c1-fireteam-identity-through-the-drop.md`) — see [Sequencing](#sequencing) below.

## Problem

Marine Ops is reached one way: fly to a planet, open the interaction menu,
click through. From there the roster hangs off briefing → deployment →
armory. `StationingScreen` is reachable only through a patron client at the
market that contract belongs to.

That gating is an accident of where the entry point was built, not a
fiction. Checked against the code:

- `ArmoryScreen` (1406 lines) has **zero** market or planet references. Gear
  allocation, grades, and armour patterns are pure roster/armory work.
- `StationingWithdrawalService.withdraw(state, contractId, day)` takes no
  planet either. `StationingScreen.onWithdraw` is planet-bound only because
  of the screen it sits on.
- `roster.squadsCommandedBy(captainId)` — the company hierarchy the whole of
  this track renders — needs nothing but the roster.

The one thing that genuinely needs a planet is *responding* to a stationing
event, because a response builds a ground battle and a battle needs a map.
And contracts G32 (`g32-player-event-popup.md`)
already solved reaching that from anywhere: the event popup's **Deploy Now**
opens Marine Ops against the *contract's own* market regardless of where the
fleet is.

So the player now gets interrupted by a decision they can answer from deep
space, and then has no standing surface to review the decisions they
deferred, the contracts already running, or the company that would fight
them.

### What C4 (`c4-whereabouts-and-deployed-state.md`) already asked for, and why it is not enough

C4 names this exactly — *"there is also no way to look at the company at all
except inside a pre-battle flow"* — and its slice 3 contracts a "standalone
entry". But its design is a new `ScreenId` and an entry row **inside Marine
Ops**, which still requires the planet interaction to reach. That delivers
"readable without accepting a mission"; it does not deliver "readable while
not at a planet".

**C10 supersedes C4 slice 3.** C4 keeps the whereabouts model and the chips;
the standalone entry moves here, where the host and the entry point are the
actual work.

## Goal

A campaign-map button that opens the company's home: what shape the company
is in, what clocks are running against it, and the army-management work that
never needed a planet in the first place.

The organising test for every element on this screen is **"what decision does
this inform, and can it be made here?"** A destination that is only a readout
gets opened twice and then ignored.

## Design

### The entry point is an ability-bar button

The ability bar *is* the campaign-only HUD element. It is present on the
campaign map and vanishes with the rest of the HUD whenever a core tab,
dialog, or menu takes over — so the "visible in campaign view, gone when
other UI is open" requirement needs **no gating code of ours at all**.

- One row in `mod/data/campaign/abilities.csv` (merges with vanilla; columns
  `name, id, type, icon, plugin, desc, sortOrder, unlockedAtStart, …`).
- One `BaseAbilityPlugin` subclass overriding **`pressButton()`**, which is
  empty in the base class and is the click hook.
- Granted once from `onGameLoad` alongside the other `ensure*` registrations:
  `Global.getSector().getCharacterData().addAbility(id)`, then assigned to
  the first free `AbilitySlotAPI`. Vanilla's `AddAbility` rulecmd
  (`api/impl/campaign/rulecmd/AddAbility.java`, the slot-scan loop around
  line 30) is the copy-paste reference. Idempotent via
  `getCharacterData().getAbilities().contains(id)`, so it covers new games
  and existing saves through one path — same shape as every other `ensure`.

**Why not a custom HUD widget.** `CampaignUIRenderingListener` is real and
dispatched (`ListenerUtil.renderInUICoordsBelowUI` and its two siblings), and
paired with `CampaignInputListener` it would let the existing widget kit draw
anything we like. But we would then own hit-testing, z-order against vanilla
chrome, and an explicit visibility gate — the same
`getCurrentCoreTab() == null && !isShowingDialog() && !isShowingMenu()` triple
that `PlayerEventPresenter.isQuiet` already carries. That cost buys nothing
until the button needs to show live state (a badge when a response is
pending, a count of recovering marines). Keep it as the named escape hatch:
the `pressButton()` handler moves across unchanged.

**Why not an intel entry.** G32 already established that the mod's five intel
plugins are load-time singletons that only ever notify at creation. A sixth
would be pull-only and tell nobody anything.

### Implementation risk, with a fallback

Vanilla ships only `TOGGLE` and `DURATION` ability types; a press-only button
is not a shape vanilla uses. The plan is a `TOGGLE` whose `activate()` /
`deactivate()` are no-ops, with `isActive`, `isActiveOrInProgress`,
`showActiveIndicator`, `showProgressIndicator`, and `showCooldownIndicator`
all forced false, so only `pressButton()` does anything.

**Confirm in game before building the panes on top of it:** that
`pressButton()` fires on click, and that a TOGGLE with a no-op `activate()`
does not latch the active indicator. If it latches, try `DURATION` with no
`durationDays`; if that also misbehaves, fall back to the
`CampaignUIRenderingListener` + `CampaignInputListener` widget above. Slice 1
exists to settle this cheaply.

### The host is G32's planet-free dialog

`MarineOpsDialogPlugin` shipped in `89ad8bac`: it hosts the takeover as an
interaction of its own, with no planet menu behind it, and dismisses the
whole interaction when the panel closes rather than restoring empty vanilla
panels. That is already the host. The button calls it.

**The constraint that matters.** `MarineOpsContext` is planet-scoped by
construction. With a null planet it does not throw — `resolveClients` guards
every lookup — but it yields no market, no missions, and a client list that
degrades to Independent and Pirates. So:

> The company host opens on a new `ScreenId.COMPANY_HQ` and **never routes to
> `MISSION_SELECT`, `BRIEFING`, `SQUAD_DEPLOYMENT`, `BATTLE`, `RESULTS`, or
> `LOOT`.** Those screens assume a market, a mission, or a battle. The only
> transition out of HQ is to `ARMORY` and back.

`ctx.openArmoryFrom(ScreenId.COMPANY_HQ, 0)` already gives the armory the
right return screen, so that route needs no new plumbing.

### Three panes, ordered by what the player came for

#### Pane 1 — Standing

Headline: **runway, in months of payroll.** `OfficerMoodReader` already
assembles credits, `MonthlyReport.totalUpkeep`, debt and its trend, active
captains, ships, and MRB rep, and already owns the 6-month / 12-month bands
(`DESPERATE_RUNWAY_MONTHS`, `SEASONED_RUNWAY_MONTHS`). This pane **reuses the
reader** and does not re-derive any of it.

Runway earns the headline because it governs every other decision on the
screen: whether to take a retainer at a bad rate, whether to keep a squad
stationed, whether the company can afford the recovery time. It is also the
number that makes bankruptcy read as earned rather than sprung.

Also here:

- retainer income — sum of `contractRetainerPerMonth` over ACTIVE /
  IN_PROGRESS stationing rows, one loop;
- MRB standing (`playerMrbRep`) and employer standing from `repValue`;
- strength vs. available strength — C4 (`c4-whereabouts-and-deployed-state.md`)
  names the gap between "living marines on the books" and "deployable right
  now" as the interesting number; this pane is where it belongs.

The line at the top is the comms officer's, through
`CommsOfficerSummary` + `OfficerMoodReader.currentMood()` — the same
mood-driven composable voice axis the mission-select header already uses, per
day-seeded so it does not jitter. No new narrator, no new chrome.

#### Pane 2 — The clock

One list of everything with a deadline running against it, **sorted by days
remaining** — not a ledger organised by contract. The player's actual
question is "is anything about to bite me?"

Two row sources, already persisted:

| Row | Field | Shipped by |
| --- | --- | --- |
| Pending response | `contractResponseDeadlineTick` | G31 (`g31-stationing-response-deadlines.md`) |
| Stationing term ending | `contractExpiresTick` | G5 |

**Lapsing offers are deliberately not here.** *Revised 2026-08-22.* This pane
carries obligations, and obligations **bite** — a missed response deadline
costs reputation and loses the garrison, a term ending changes the company's
income. An offer merely **lapses**: nothing bad happens, the player just does
not get the job. Opportunities live on the board in
C11 (`c11-the-contract-board.md`), where the action attached to them actually
is. Mixing the two would put a row here whose only button is "fly somewhere
else."

`PlayerEventInbox.pending(state, roster, day)` already returns the
pending-response rows as notices in exactly this order — soonest deadline
first, contract id as tiebreak — so that source is a direct render of a
shipped projection, not a new query.

**This pane is where Hold goes.** G32's popup offers Deploy Now / Hold /
Write Them Off, and Hold currently dismisses into nowhere; G32 logged its own
follow-up asking for a surface where a popup dismissed in a hurry can be
reviewed. This is it. The popup and this list become one loop rather than two
features, and the deferred decision sits here with its countdown visibly
running.

**Respond** on a pending row calls
`PlayerEventPresenter.requestDeployment(notice)` — the identical path Deploy
Now already takes, which opens Marine Ops at the contract's own market from
anywhere in the sector. A route, not a second battle path, and not a
mutation.

#### Pane 3 — The company

Today this pane ships the army-management work that never needed a planet:

- a strength / ready / recovering rollup;
- a route into `ArmoryScreen`, verified planet-free, returning to HQ.

Tomorrow C3 (`c3-company-card-stack.md`)'s card stack lands **in this pane**
and C4 (`c4-whereabouts-and-deployed-state.md`)'s whereabouts chips with it.
C10 reserves the space and builds the room; C3 and C4 furnish it.

**Do not build a second card stack here.** Until C2/C3 land, the rollup is a
band and a count, deliberately thin — the pane's value before C3 is that the
armory is reachable at all.

## Sequencing

**Independent of C1, and can land before it.** C1 carries fireteam identity
across the drop seam to unblock the *deployed*-force views (C5, C6). C10
touches nothing in `battle/`, no drop seam, and no `MarineLoadout`. The two
can run in parallel worktrees with no conflict.

Against the rest of the track:

- **Panes 1 and 2 need nothing from C2 or C3.** Every number they show is
  already persisted or already computed. They are complete on the day C10
  ships.
- **Pane 3 upgrades in place** as C2 → C3 → C4 land. C10 does not block them
  and they do not block it.
- **C4 slice 3 folds into this story** (see above). C4's own note that the
  contracts track owns stationing mechanics still stands and is honoured
  below.

Recommendation: C10 first, because it converts C4's "a `ScreenId` inside a
planet dialog" into a real campaign-map home, and because it makes G32's Hold
option mean something. C1 remains the right pickup for anyone working the
battle seam.

## Slices

1. **The button.** `abilities.csv` row, `BaseAbilityPlugin` subclass,
   `ensure`-style grant + slot assignment. `pressButton()` opens the G32
   host on an empty `COMPANY_HQ` screen. Settles the TOGGLE-latching risk
   before anything is built on top.
2. ~~**Standing.**~~ **Shipped** (`b204c237`). Runway headline through
   `OfficerMoodReader.Snapshot`, retainer sum, MRB and employer standing,
   personnel gap, and the comms-officer line via `OfficerHeaderWidget`.
3. ~~**The clock.**~~ **Shipped** (`20f498de`). Merged deadline list over the two
   obligation sources, with **Respond** routing through
   `PlayerEventPresenter.requestDeployment`. Offer expiry belongs to
   C11 (`c11-the-contract-board.md`).
4. ~~**The company pane.**~~ **Shipped** (`822065cd`). Formation band, the personnel
   rollup moved in from STANDING, next recovery, and the armory route.

Slices 2–4 are independently valuable and can ship in any order after 1.

## Acceptance

- The button is on the campaign map, and absent in the fleet, refit, intel,
  and map tabs, in an interaction dialog, and in the pause menu — **with no
  visibility gate in our code**.
- Opening it from deep space, with no market in sensor range, produces the
  full screen with every pane populated.
- Runway reads in months of payroll and agrees with `OfficerMoodReader`'s
  band for the same inputs.
- Every clock row states its days remaining. If the list is capped, the
  off-screen count is stated — silent truncation is never acceptable
  (design commitment 9).
- A response the player pressed **Hold** on in the G32 popup appears in the
  clock pane, with its countdown running.
- **Respond** on a pending row reaches the same briefing, with the same
  detachment, that G32's Deploy Now reaches.
- The armory opens from the company pane and returns to HQ — not to mission
  select.
- No route from the HQ host reaches mission select, a briefing, or a battle.
- Dismissing the screen by any route returns to the campaign map and never
  strands the player in an option-less dialog.
- The screen writes no campaign or roster state.
- `gradlew.bat build` green.

## Automated verification

The pane math is pure and testable; the dialog half ships on in-game smoke,
the same gate as G5, G13, and G32.

- Runway banding is already covered by `OfficerMoodReader`'s tests — assert
  the pane consumes the reader rather than re-deriving.
- Retainer-income sum over a fixture contracts table, including that
  OFFERED and terminal rows contribute nothing.
- Clock-row merge and ordering across both obligation sources, with ties
  broken deterministically, and the empty case. Plus that an OFFERED row
  contributes nothing — the valence split is a rule, not an omission.
- **Manual smoke (shipping gate):** the button's presence/absence across
  every core tab and the pause menu; opening from deep space; layout at
  1.0x / 1.25x / 1.5x UI scale; the armory round trip; **Respond** into a
  real battle; and that no dismissal path strands the player.

## Files touched

- `mod/data/campaign/abilities.csv` — new, merges with vanilla.
- `campaign/ability/CompanyViewAbility.java` — new; `pressButton()` only.
- `StarsectorMarinesModPlugin.java` — `ensureCompanyViewAbility()`.
- `ops/ScreenId.java` — `COMPANY_HQ`.
- `ops/CompanyHqScreen.java` — new; the three panes.
- `ops/MarineOpsPanelPlugin.java` — register the screen; the routing
  constraint above.
- Read-only use of `campaign/` (contracts table, `PlayerEventInbox`,
  `OfficerMoodReader`) and `marine/` (roster rollups). No writes.

## Out of scope

- **Withdrawing from a stationing contract here.** `StationingWithdrawalService`
  is already planet-free, so this is a deliberate hold rather than a technical
  limit: withdrawal is a contract mutation with reputation consequences, and
  design commitment 4 keeps mutation where it already lives. C4 draws the same
  line for recalling a stationed team. If it is wanted, it belongs to the
  contracts track.
- **Changing stationing mechanics, terms, or payloads.**
  `roadmap/campaign/contracts/` owns those; G31 and G32
  have just reshaped them.
- **Deployment selection.** Seats and missions exist only in a briefing flow;
  that surface stays there.
- **A second card stack.** Pane 3 is a reserved space, not a competing
  implementation of C3.
- **New persisted state.** Every rollup is derived (design commitment 3).
- **Migrating the planet entry.** `MarineOpsCMD` and the patron-client route
  are untouched; this is an additional door, not a replacement.

## Open questions

- **Does the button belong to the player or to the company?** Granting it
  unconditionally at load is simplest. Gating it behind having a captain at
  all would make the early game read as an escalation, at the cost of a
  first-hour surface the player cannot find.
- ~~**Should the clock pane show contracts the player has not accepted?**~~
  **Settled 2026-08-22: no.** Obligations bite, opportunities lapse, so they
  are different surfaces — offer expiry moves to
  C11 (`c11-the-contract-board.md`). The one case that would earn an offer a
  row here is one the player has *decided on* and is flying toward, which
  needs a watchlist that is not modelled; C11 carries that as its own open
  question.
- **Does Standing want a trend, not just a level?** "Four months of payroll"
  is less useful than "four months, down from seven." `MonthlyReport` carries
  the previous report, so the data is there; whether one extra number earns
  its space is a layout call for slice 2.

---

## Slice 1 — shipped

`2b959e44`. `gradlew.bat build` green.

### What landed

- `mod/data/campaign/abilities.csv` — one row, merging with vanilla's table.
- `mod/graphics/icons/abilities/company_view.png` — 48x48 to match vanilla's
  ability icons; twelve pips in three fire teams of four, the same squad shape
  C7 settles and C3's rows will use.
- `ops/CompanyViewAbility` — `pressButton()` only.
- `ops/CompanyHqScreen` — stub: header, three placeholder lines naming the
  panes to come, and a Back that dismisses.
- `ScreenId.COMPANY_HQ`, registered on `MarineOpsPanelPlugin` with the routing
  constraint recorded in its class javadoc.
- `StarsectorMarinesModPlugin.ensureCompanyViewAbility` — grant plus a
  vanilla-shaped slot scan, self-defensive so a malformed row cannot take game
  load down.
- `AbilitiesCsvTest` — four checks on seams that otherwise fail silently.

### Deviations

**The ability lives in `ops/`, not `campaign/ability/`.** It is a door into an
ops screen, and `ops` already depends on `campaign`; putting it under
`campaign` would have inverted that dependency for no gain.

**The latching risk was smaller than this story claimed.** Extending
`BaseAbilityPlugin` directly — rather than `BaseToggleAbility` — already gives
`isActive()` false, `getProgressFraction()` zero, and `getCooldownFraction()`
one, so `showActiveIndicator`, `showProgressIndicator`, and
`showCooldownIndicator` all return false without being touched. Only the two
abstract cooldown accessors had to be satisfied. `activate()` and
`deactivate()` are still stubbed, because the base implementations call
`reportPlayerActivatedAbility` and would fire an activation event and an on/off
sound the player never asked for.

That lowers the risk but does not retire it: none of it proves the engine
routes a TOGGLE press through `pressButton()` at all. That is still a live-run
question.

### Still to confirm in game

- The button appears on the ability bar, with its icon and tooltip.
- Clicking it opens the company screen, and **nothing latches** — no active
  indicator, no cooldown sweep, no on/off sound.
- Back returns to the campaign map with no residual dialog.
- The button is absent in the fleet, refit, intel, and map tabs, in an
  interaction dialog, and in the pause menu.
- Opening it from deep space, with no market in sensor range, works.
- An existing save picks the ability up on load and places it on a free slot;
  dragging it off the bar and reloading does not put it back.

If a TOGGLE press does not reach `pressButton()`, the fallbacks are `DURATION`
with no `durationDays`, then the `CampaignUIRenderingListener` +
`CampaignInputListener` widget described above.

## Slice 2 — shipped

`b204c237`. `gradlew.bat build` green; 13 assertions in `CompanyStandingTest`.

### What landed

- `OfficerMoodReader.Snapshot` — the world-read extracted from `currentMood()`,
  carrying `runwayMonths()` next to the bands that already gate the mood.
  `currentMood()` is now `read().mood()`; `bucket(...)` and its existing tests are
  untouched. `DESPERATE_RUNWAY_MONTHS` / `SEASONED_RUNWAY_MONTHS` are public so the
  pane colours on the same numbers the officer reacts to.
- `CompanyStanding` — derived, never persisted: retainer income, employers ranked by
  standing, and strength against what can deploy today.
- `CompanyHqScreen` — the three columns it will keep, standing populated.
- `OfficerHeaderWidget` reused verbatim; with no client selected it falls to the
  overview flavor, and it needs no planet.

### Decisions worth keeping

**Runway leads, and an unknown runway says so.** `runwayMonths()` returns -1 when last
month's upkeep is zero or unknown, and the pane renders "Runway unknown" rather than a
number. A first-month campaign has no monthly report; printing anything there would
read as good news.

**Retainer counts only rows that are actually paying** — ACTIVE and IN_PROGRESS. An
OFFERED row has not been accepted and a terminal row has stopped. This number feeds the
runway the screen leads with, so overcounting it is the one direction it must never err
in.

**`unavailable` is the whole gap, not just casualties.** Wounded, stationed, and
reserve-pool marines all sit between "on the books" and "can go today", and the
player's question is how many they can actually send.

### Still to confirm in game

- Standing figures agree with the campaign's own numbers (credits, last month's
  upkeep, debt) on a real save.
- The runway colour flips at 6 and 12 months and matches the officer's tone.
- Three columns at 1.0x / 1.25x / 1.5x UI scale, with the employer list not
  overrunning the column.

## Slice 2 follow-up — an unknown month, and a clock you can move

`8ac4116a`. `gradlew.bat build` green.

Two defects surfaced by the first live run of the standing pane, both rooted in
the same thing: vanilla's `MonthlyReport` does not exist until a real in-game
month rolls over.

### Unknown upkeep printed as zero

The pane rendered "Upkeep last month: Cr. 0" (and a zero debt) on a young
campaign, because `SharedData.getData().getPreviousReport()` returns null until
the first rollover and the snapshot's numeric fields defaulted to zero. Credits
were right — those come off the fleet's cargo — so the line read as a company
with no costs rather than a screen with no data.

`OfficerMoodReader.Snapshot` now carries `hasMonthlyReport`, and the pane either
shows the three report-derived lines or says why it cannot: *"No monthly report
yet — upkeep, debt, and runway appear after the first month rolls over."*

This is the same rule `runwayMonths()` already followed by returning -1. The
principle is worth stating once for the whole track: **a derived number with no
input says so.** Zero is a claim about the company; blank is a claim about the
screen. Panes 2 and 3 inherit this.

### The debug panel could not advance a day

`CampaignDebugIntel`'s "Force daily tick" runs every system at
`CampaignClock.day()` — the *current* day. It re-runs a day; it never leaves
one. No deadline, retainer cadence, incident timer, or offer expiry could be
reached with it, which made every dated behaviour on this screen unobservable.
The button is relabelled "Force daily tick (run all systems, same day)" to stop
it being read as time travel.

Added **Skip 1 / 7 / 30 days**, backed by `CampaignClock.skipDays`. Vanilla's
`CampaignClockAPI` is read-only — there is no advance or set — so the skip
re-anchors instead: epoch timestamp restamped to now, epoch day set to the
target, leaving future real time accruing normally from the new anchor. The
per-frame memo keys on that timestamp and a re-anchor inside one frame can land
on the timestamp it already cached, so it is dropped explicitly rather than
trusted to differ.

The buttons run **one full system pass per day crossed**, so a 30-day skip is
thirty passes and timers fire in order rather than being jumped over.

**What it does not do.** Only this mod's tier moves. Vanilla's economy does
not, so skipping days will never produce a monthly report — upkeep, debt, and a
computable runway still need a real in-game month. Which is exactly why the
standing pane was blank on those fields in the first place.

## Slice 3 — shipped

`20f498de`. `gradlew.bat build` green; 13 assertions in `CompanyClocksTest`.

### What landed

- `CompanyClocks` — pure, `Global`-free derivation of every obligation deadline,
  soonest first, contract id then kind as tiebreaks so the column cannot reshuffle
  between frames. `CompanyClocks.current()` is the thin live-world entry point;
  `rows(state, roster, day)` is what the tests drive.
- `CompanyHqScreen` — the middle column, populated. Each row states days remaining,
  what it is, and where; response rows carry a **Respond** button.
- `PlayerEventTarget.market(int)` / `displayName(int)` — the registry-slot overloads,
  so a term row that has no notice can still name its market. The notice-shaped
  callers now delegate to them.
- Eleven new `Strings` keys; `companyHqDeadlinesPending` retired.

### Decisions worth keeping

**Obligations bite, opportunities lapse.** The pane carries pending responses and
term boundaries and nothing else. A lapsing offer would put a row on this column
whose only button is "fly somewhere else" — that belongs to
C11 (`c11-the-contract-board.md`). The rule is pinned by a test rather than left to
the next person's judgement.

**A contract can own two clocks, and the second one knows about the first.** A
stationing row with a pending response produces both a RESPONSE and a TERM_ENDING
entry, because they are two deadlines with two consequences and two answers. The
term entry carries `failsOnExpiry`, which is G31's invariant — a term boundary
reached with a response still owing fails the contract rather than completing it —
surfaced *before* it fires rather than explained afterwards.

**Respond is a route, not a second battle path.** It calls
`PlayerEventPresenter.requestDeployment` and closes the dialog, exactly as the event
popup's Deploy Now does. `showInteractionDialog` refuses while our own dialog is up,
so the hand-off has to go through the presenter's quiet gate on a later frame. This
is what finally gives G32's **Hold** somewhere to come back to.

**An overdue clock reads as due, never negative.** The lapse system runs on day
boundaries and the pane is read on frames, so a row can legitimately be seen past
its deadline.

### Still to confirm in game

- A pending response appears here with the same countdown the popup showed, and
  **Respond** reaches the same briefing that Deploy Now reaches.
- Pressing **Hold** on a popup and then opening the company view finds the decision
  waiting, with its clock running.
- The urgency colours flip at 2 and 7 days.
- Rows fit the middle column at 1.0x / 1.25x / 1.5x UI scale — a two- or three-line
  row plus a button is the tallest thing this screen renders, and `CLOCK_LIMIT` of 6
  is a guess at what fits rather than a measurement.
- With more than six clocks, the "+N more not shown" line appears and is accurate.

## Slice 4 — shipped

`822065cd`. `gradlew.bat build` green; 4 new assertions in `CompanyStandingTest`
(32 in the two company-view suites).

### What landed

- `CompanyStanding.lineSquads` / `stationedSquads` / `nextRecoveryDay`, with their
  statics. Still derived, still never persisted.
- `CompanyHqScreen.buildRoster` — the formation band, the personnel rollup, the next
  recovery, and an **Armory** button.
- The personnel block **moved** out of `buildStanding`. `buildPlaceholder` is gone;
  every column is real.
- `companyHqPersonnelHeader`, `companyHqRosterPending`, and
  `companyHqDeadlinesPending` retired; seven keys added.

### Decisions worth keeping

**The people belong under the header that names them.** Slice 2 put strength-vs-available
in STANDING because C4 (`c4-whereabouts-and-deployed-state.md`) named that gap as the
interesting number. It is — but with slice 4 populating a column literally headed
ROSTER, keeping the count under a financial header would have meant either duplicating
it or leaving ROSTER to say nothing about people. STANDING is now money and reputation;
ROSTER is people. This is a revision of a shipped slice, made deliberately.

**The reserve pool is not a formation.** `MarineRoster.reserveSquad()` is a
`MarineSquad`, so a naive `squads().size()` overstates the company by one — always, and
invisibly. It is also created lazily, which is why the test asserts the count does not
move when it appears rather than assuming it is there.

**Next recovery is the screen's one forward-looking number.** "3 wounded" says how bad
it is; "next marine back in 4 days" says whether to wait. `nextRecoveryDay` returns -1
when nobody is recovering, and the line is omitted rather than rendered as zero — the
same rule the runway and the monthly report already follow.

**One derivation feeds both columns.** `rebuild()` reads `CompanyStanding.current` once
and passes it to STANDING and ROSTER. Two reads could straddle a day boundary or a
monthly rollover and put disagreeing numbers side by side on a single screen.

**The armory needed nothing.** `ArmoryScreen` has no market or planet reference in 1443
lines, exactly as the story's audit claimed, so it runs unchanged on the planet-free
host. `openArmoryFrom(ScreenId.COMPANY_HQ, 0)` sets the return screen so Back lands
here and not on mission select — which this host must never reach.

### Still to confirm in game

- The armory opens from the company view, allocates gear, and Back returns to HQ.
- The formation band matches the roster, and a stationed squad shows as away.
- The recovery line appears only while someone is wounded, and counts down.
- All three columns at 1.0x / 1.25x / 1.5x UI scale.
