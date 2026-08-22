# Monotonic campaign clock

**Status:** CODE COMPLETE — in-game confirmation pending (2026-08-22)

**Implemented in:** `03b5baea`

## The bug

Every duration the campaign tier measured was read from
`Global.getSector().getClock().getDay()`, at 28 call sites, with
`getTimestamp()` / `getElapsedDaysSince()` used nowhere.

`getDay()` is not a day counter. It is a **calendar component** that sits beside
`getCycle()`, `getMonth()`, and `getHour()` on `CampaignClockAPI`, and vanilla
gates on its exact value — `RemnantStationFleetManager` on
`getClock().getDay() == 15`, `KantaCMD` on `== 28`. It wraps roughly monthly.

Consequences across the tier, all silent:

- **Retainers** — `paymentThrough - lastPaid` goes negative on every month
  rollover, so whole-month payments were only ever delivered inside a single
  calendar month.
- **Stationing defaults** — the 30-day checkpoint clock could not advance
  past a wrap.
- **Cadre incidents** — a 24–36 day cadence anchored on a value with a period
  of ~30 rarely resolved.
- **Offer expiry, injury recovery, captain tenure** — same class of failure.
- **G31 response deadlines** — a 7-day window armed on calendar day 27 targets
  day 34, which `getDay()` never reaches.

Found while validating G31's deadline arithmetic. See
[`../../contracts/complete/g31-stationing-response-deadlines.md`](../../contracts/complete/g31-stationing-response-deadlines.md).

## What shipped

```
campaign/
  CampaignClock.java     NEW — day() / dayFloat(), the tier's monotonic day
                         counter. Wraps CampaignClockAPI.getElapsedDaysSince,
                         which is vanilla's own duration idiom.
  CampaignState.java     + clockEpochTimestamp / clockEpochDay, the anchor.

28 call sites across 15 files now read CampaignClock instead of the calendar
component. StarsectorMarinesModPlugin.onGameLoad ensures CampaignState before
the roster, since the anchor lives on the state and starter-captain creation
stamps a day.
```

### The counter is anchored, not absolute

The obvious fix — an absolute day derived from a fixed epoch — would have
produced values around 450,000 while every stored `*Tick` column and every
personnel day field held values in the 1–30 range. Each stored timer would have
read as thousands of days stale: retainers would settle thousands of months at
once, and every deadline and expiry would fire on the first tick after upgrade.
Correcting that would have meant an offset rebase across 39 `CampaignState`
columns plus the personnel graph — a large, one-shot, hard-to-verify migration.

Instead `clockEpochTimestamp` is stamped on first use and paired with
`clockEpochDay = max(0, lastTickDay)`, so the counter **continues the numbering
the save already had**:

```
day = clockEpochDay + max(0, clock.getElapsedDaysSince(clockEpochTimestamp))
```

A legacy save resumes from whatever day number it was last on and counts
forward correctly from there. A new game starts at zero. No column rebase, no
migration pass, no save break.

What this does not do is repair history: values written *before* the fix are
already scrambled by the wraps that happened while they were being written.
Nothing can reconstruct those. The point is that the scale stays continuous and
stops degrading.

### Details worth keeping

- Elapsed days are clamped at zero, so a rewound or re-anchored clock can never
  walk the counter backwards and re-fire every "have N days passed" check.
- A per-frame memo keys on the sector clock's timestamp plus the epoch.
  Resolving the anchor walks the sector script list, and UI code reads the day
  inside `render()` once per card per frame; the memo collapses that to one
  lookup per frame, and including the epoch in the key means a different save
  can never read a stale value.
- `dayFloat()` exists for the personnel graph, whose recovery and injury fields
  are floats.

## Automated verification

- `CampaignClockTest` — anchor continuity for both legacy and new-game
  anchoring, the month-boundary case that motivated the fix (a 7-day window
  armed on day 27 now reaches day 34), backwards-clamping, and monotonicity
  across a simulated year.
- `gradlew.bat build` green — full suite, 0 failures.

## Still to confirm in game

The reading of `getDay()` is from vanilla source, not from a live run. Before
building further time-based mechanics on top, confirm in a running campaign
that `CampaignClock.day()` advances by one per in-game day and crosses a month
boundary without resetting. The debug intel's contract panel and its "Force
daily tick" toggle are the fastest way to watch it.

Two assumptions to sanity-check at the same time:

- `getElapsedDaysSince(getTimestamp())` returns days, not hours or seconds.
- The anchor stamps correctly on an existing save — `clockEpochDay` should pick
  up that save's last tick day rather than zero.

## Follow-ups

- **Date display.** Commendation lines read `"Day " + day + ": …"`, which is now
  a counter value rather than a calendar date. `CampaignClockAPI.getDateString()`
  / `getShortDate()` would read better in player-facing text. Cosmetic, and
  deliberately out of scope here.
- **Timestamps over day numbers.** The vanilla-idiomatic shape is to persist a
  `long` timestamp per event and call `getElapsedDaysSince` at read time. That
  would remove the anchor entirely, at the cost of widening ~39 columns from
  `int` to `long`. Worth considering only if the anchor proves fragile.
