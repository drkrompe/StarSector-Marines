# G32 — Player event inbox and self-triggered event popup

**Status:** SHIPPED — `89ad8bac` (2026-08-22)
**Depends on:** G31, shipped (`e25fa582`, recorded in
[`../complete/g31-stationing-response-deadlines.md`](../complete/g31-stationing-response-deadlines.md)).
It supplies the response deadline the popup counts down to and the
`StationingLapseResolution` path behind "write them off". The countdown is a
direct read of `contractResponseDeadlineTick`; G31 shipped it as a persisted
column rather than deriving it, so the popup does not recompute anything.

## Goal

Push campaign events at the player instead of waiting to be found. Today a
Garrison defense or Cadre incident arms silently: there is no `MessageIntel`, no
`addMessage`, and no per-event `addIntel` anywhere in the campaign layer. The
only way to learn that a stationed detachment is under attack is to fly to that
market, open the planet dialog, enter Marine Ops, click the patron client, and
press **Manage** — and nothing distinguishes an armed assignment from an idle
one until that screen is open.

Ship a CK3-style event popup: unprompted, modal, campaign time frozen, our own
chrome, our own buttons, no vanilla dialog furniture.

## Why a popup rather than an intel entry

The mod's five intel plugins (`CivilianRescueIntel`, `DefectorAsylumIntel`,
`DeadLetterIntel`, `LastTestamentIntel`, `BridgeIntel`) are registered **once**
in `StarsectorMarinesModPlugin.onGameLoad` as permanent singleton panels that
mutate their contents per event. Three pass `forceNoMessage = true`; the two
passing `false` only ever fire a notification the first time the panel is
created, never when an event actually fires. So the intel route is already
proven to be pull-only, and adding a sixth silent panel would not tell anyone
anything.

The takeover chrome we want already exists and ships — it is simply not
self-triggering. `MarineOpsCMD` hides the vanilla text and visual panels and
calls `dialog.showCustomVisualDialog(w, h, new MarineOpsDialogDelegate(...))`,
handing the full canvas to our widget kit. `CustomVisualDialogDelegate` was
chosen over `CustomDialogDelegate` precisely because it does not force
confirm/cancel buttons into the chrome — we own dismissal. This story adds the
missing seam: firing that takeover without a planet interaction.

## Locked rules

### The inbox is a projection, not duplicated state

- `PlayerEventNotice` — immutable value type: notice kind, subject contract id,
  source identity (defense event key or incident due day), market id, triggered
  day, deadline day, bound captain id, committed marine count, and the trigger /
  incident label key.
- `PlayerEventInbox` — a pure static reader: given `CampaignState`, the roster,
  and the current day, it enumerates pending player decisions as notices in a
  deterministic order (soonest deadline first, contract id as tiebreak). No
  `Global` access, no side effects, fully headless-testable.
- The inbox **derives** notices from already-persisted domain state
  (`GarrisonDefensePayload`, `StationingIncidentPayload`). It never stores a
  parallel copy of an event. The domain remains the single source of truth.
- Sources in this story: pending Garrison defense, pending Cadre incident. The
  notice type and the consumer are deliberately source-agnostic so the
  black-swan `events[]` rows sitting in `PENDING_CHOICE` can be added as a
  further source later without touching the presenter or the popup.

### Acknowledgement is persisted and exactly-once

- Two new SoA columns on the contracts table record what has already been
  presented: the defense event key shown, and the incident due day shown. Ack
  lives next to the domain state it describes, so it inherits the existing
  growth/backfill and `ContractTableCompactor` alignment guarantees.
- A notice is marked acknowledged **only after `showInteractionDialog` returns
  `true`**. The call returns `false` whenever the UI is already showing a
  dialog; acknowledging on the attempt rather than the success would silently
  swallow events.
- Ack survives save/load. A notice already shown does not re-pop on the next
  game load.
- **One re-pop rule:** a deferred notice fires a second and final time when two
  days remain before its G31 deadline. That is the only re-pop; it is recorded
  by the same ack columns so it cannot loop.

### The presenter is polite

`PlayerEventPresenter`, an `EveryFrameScript` registered from an `ensure` in
`onGameLoad` alongside the other scripts, takes the first unacknowledged notice
and offers it. It does nothing at all when:

- `CampaignUIAPI.isShowingDialog()` is true;
- the player is in combat, in a core UI tab, or in a menu;
- a notice was already offered this frame.

A refused offer is simply retried on a later frame — the queue is the persisted
domain state, so nothing is lost across a save, a reload, or a battle.

### The popup owns its chrome

- `PlayerEventDialogPlugin implements InteractionDialogPlugin` — on `init`,
  hides the text and visual panels and calls `showCustomVisualDialog` with the
  event delegate, mirroring `MarineOpsCMD`'s proven sequence.
- `PlayerEventDialogDelegate implements CustomVisualDialogDelegate` — owns a
  standalone `CustomUIPanelPlugin` for the popup. It does **not** join
  `MarineOpsPanelPlugin`'s `ScreenId` router, which is planet-scoped by
  construction (`MarineOpsContext(PlanetAPI)`).
- Built from the existing `ui/` widget kit (`PanelWidget`, `LabelWidget`,
  `ButtonWidget`), honouring the Orbitron 20 font floor.
- Sized as a modal card, not a full-canvas takeover — this is an interruption,
  not a destination.

### Three options, and the fleet's location does not gate them

A Garrison defense is fought by the stationed detachment using local transport
cycles. The player's fleet is fictionally irrelevant to it, so the popup does
not require the player to fly there:

- **Deploy now** — dismisses the popup and opens the full Marine Ops dialog
  against the *notice's* market planet (resolved from `marketId` through
  `CampaignState.marketRegistry`), pre-seeded with the stationing contract and
  its pending response so it lands on the existing respond path. This reuses
  `MarineOpsPanelPlugin` and the whole briefing → deployment → battle → results
  stack wholesale; the popup adds no second battle path.
- **Hold** — dismiss. The notice stays pending and re-pops once at deadline
  minus two days. No state mutation.
- **Write them off** — `StationingLapseResolution.apply(...)`, applied
  immediately, behind a confirm. Explicitly choosing the bad outcome now rather
  than letting the clock choose it.

The card shows the trigger source, the bound captain, the committed detachment,
and the days remaining, so all three options are informed.

### Scope guard

Only stationing events pop in this story. The living-world intel events keep
their current pull-only surfaces until a deliberate follow-up moves them onto
the inbox.

## Non-goals

- No change to any battle, resolution, or reputation policy — G32 is
  presentation over shipped domain operations.
- No migration of the five existing intel panels.
- No sector-map waypoint, no message-log integration, no event history archive.
- No new event sources.
- No remote *management* surface. G13 deliberately kept assignment management
  local; this story adds a push notification and a response route, not a
  sector-wide management console.

## Acceptance

- An armed Garrison defense produces a popup within a few seconds of the player
  returning to the campaign map, wherever the fleet is.
- The popup cannot appear on top of another dialog, in combat, or in a menu.
- Each notice pops exactly once, plus exactly one deadline reminder.
- A popup dismissed by any route never loses the underlying event.
- **Deploy now** from anywhere in the sector reaches the same briefing the
  local **Manage → Respond** path reaches today, with the same detachment.
- **Write them off** produces the identical end state as letting the G31
  deadline lapse.
- Save, reload, and the popup state is exactly where it was.
- `gradlew.bat build` green.

## Automated verification

The domain half is fully testable; the dialog half follows the G5 / G13
precedent where in-game smoke is the shipping gate.

- `PlayerEventInboxTest` — notice enumeration for both sources, deterministic
  ordering, deadline projection, empty inbox, and exclusion of already-resolved
  or terminal rows.
- `PlayerEventAckTest` — exactly-once presentation, the single deadline re-pop,
  no re-pop after acknowledgement, and ack survival across a simulated
  save/load.
- `CampaignStateEventNoticeColumnsTest` — growth and backfill of the two new
  columns.
- `ContractTableCompactorTest` — extended for ack-column alignment during
  compaction.
- `PlayerEventPresenterTest` — offer suppression while a dialog is showing, and
  the critical rule that a refused offer is not acknowledged.
- **Manual smoke (shipping gate):** popup layout and legibility at several
  resolutions; all three buttons; remote **Deploy now** into a real battle;
  confirm dialog on **Write them off**; and that no popup can strand the player
  in a dialog with no exit.

## Follow-ups this opens

- Move the living-world `PENDING_CHOICE` events onto `PlayerEventInbox` so the
  Distress Net and Encrypted Channel decisions push as well. The inbox is being
  shaped for this, but doing it is its own story.
- Consider a lightweight event-history record so the player can review a popup
  they dismissed in a hurry.

---

## Shipped

`89ad8bac`. `gradlew.bat build` green; 24 new assertions across four suites plus the
extended compactor suite.

### What landed

**Domain (`campaign/`)**

- `PlayerEventNotice` — immutable value type. Carries a `Strings` *key*, not resolved
  text, so the projection stays free of `Global` and a translation mod still wins at
  draw time.
- `PlayerEventInbox` — static projection: `pending`, `nextToPresent`, `acknowledge`,
  `isAcknowledged`. Derives everything from `GarrisonDefensePayload` /
  `StationingIncidentPayload`; stores nothing.
- `CampaignState.contractNoticeAckKey` / `contractNoticeAckStage`, with `addContract`
  reset, `readResolve` backfill, `ensureContractCapacity` growth, and
  `ContractTableCompactor` row copy.

**Presentation (`ops/event/`)**

- `PlayerEventPresenter` — `EveryFrameScript` registered from
  `StarsectorMarinesModPlugin.ensurePlayerEventPresenter`.
- `PlayerEventDialogPlugin` / `PlayerEventDialogDelegate` / `PlayerEventCard` — the
  modal card, built on the existing widget kit.
- `PlayerEventTarget` — market-slot → live `MarketAPI` / `PlanetAPI` / display name.

**Shared seam (`ops/`)**

- `StationingResponseLaunch` — extracted verbatim from `StationingScreen.onRespond`,
  now the single route from a pending response to its briefing, plus `writeOff`.
- `MarineOpsDialogPlugin` — opens the Marine Ops takeover as an interaction of its own.
- `MarineOpsPanelPlugin` / `MarineOpsDialogDelegate` gained a context `seed` and a
  caller-supplied dismissal.

### Deviations from the contracted rules

**Two ack columns, but a different two.** The story specified "the defense event key
shown, and the incident due day shown" — one column per source. Shipped instead as one
identity column plus one stage column. Same column count, and it is strictly better on
two counts: a contract row is `GARRISON` or `CADRE` and never both, so one identity
column already covers both sources unambiguously; and the per-source shape had no room
at all for the locked one-re-pop rule, which needs to distinguish "shown" from
"reminded". Encoding that stage in a spare bit of an event key would have been a hack.
The source-agnostic pair also takes the black-swan `PENDING_CHOICE` rows later with no
schema change.

**The presenter runs while paused.** The story listed the suppression conditions as
dialog / combat / core tab / menu; paused was never on that list, and `runWhilePaused`
returning false turned out to be actively wrong here. Interaction dialogs pause the
campaign, so a Deploy hand-off queued from inside the popup would never have opened if
the player came back from the dialog still paused. Events now also reach a player who
paused to think, which is the CK3 behaviour the story was chasing anyway.

**Deploy is withheld when the market has no planet.** Not contemplated by the story. A
stationing contract on an orbital station has no `getPlanetEntity()`, so there is
nowhere for a ground battle to happen. The button disables itself with
`eventPopupDeployUnavailable` rather than opening a Marine Ops screen that cannot
launch anything; Hold and Write Them Off still work.

### Notes for the next reader

- The Deploy hand-off is a two-step: the card calls
  `PlayerEventPresenter.requestDeployment` and dismisses, and the presenter opens
  Marine Ops on a later frame. It cannot be done inline —
  `showInteractionDialog` refuses while the popup is still up.
- Both self-triggered dialog plugins dismiss their host interaction on panel close,
  attempted immediately and retried from `advance`. Restoring the vanilla text/visual
  panels the way `MarineOpsCMD` does would leave the player in an option-less dialog
  with no exit, because there is no planet menu underneath.
- The interaction target for both is the player's fleet, never the remote planet — the
  detachment fights with local transport and the player's position is fictionally
  irrelevant, so there is no reason to involve a distant entity.

### Manual smoke — the shipping gate, not yet run

Per the G5 / G13 precedent the dialog half ships on in-game verification:

- popup layout and legibility at several resolutions;
- all three buttons, including the confirm step on **Write Them Off**;
- remote **Deploy Now** into a real battle, and that its detachment matches the local
  **Manage → Respond** path;
- that no popup, and no Marine Ops dialog opened from one, can strand the player in a
  dialog with no exit;
- save/reload mid-notice, confirming no re-pop.

### Follow-ups this opened

- Move the living-world `PENDING_CHOICE` events onto `PlayerEventInbox` so the Distress
  Net and Encrypted Channel decisions push as well. The columns already accommodate it.
- A lightweight event-history record so a popup dismissed in a hurry can be reviewed.
- `StationingScreen` still owns its own `incidentLabel` / `defenseLabel` switches
  alongside `PlayerEventInbox`'s key mapping. Two switches over the same enums; worth
  collapsing when one of them next changes.
