# Side-objective toolkit

Status: IN PROGRESS — all four steps built and integrated, then discovery and per-job credits from the owner's answers; awaiting owner acceptance. The model is folded into `mission-command-nouns.md` (Side objectives) and `ui-nouns.md`.

Written: 2026-10-26

Read `mission-command-nouns.md` (objective ownership, the player-facing mission
picture), `ui-nouns.md` (the shared mission picture: checklist, beacons,
Locate), `battle-radio-nouns.md` (cues and the voice budget) and
`dust-ledger-crossing.md` (Side objectives) first.

## Why

The Dust Ledger has three optional jobs before the alarm (the salvagers'
coupling, the warden's nod, cutting the feed) and an optional catch after it
(the broker). Each one is written into the mission by hand.
`DustLedgerSequence` decides when it is offered, worked, finished or lapsed.
`DustLedgerChecklist` writes its row, and the briefing and debrief keys are
picked one at a time. A second mission with a side job would copy all of it.
The owner wants side jobs to be a standing tool: something a story offers,
the player chooses to take, the battle answers, and the campaign remembers.

## The noun

A **side objective** is an optional, mission-owned job with no bearing on
victory or defeat. It never appears in `WinCheckSystem`'s tally. A mission
authors one as data plus a small effect hook:

- **Hook.** Who offers it and why: an offerer (a named contact or a faction),
  a briefing line, a field line spoken when it opens, and a short checklist
  name and instruction. All of it comes from story keys.
- **Trigger.** How it is done. The vocabulary starts with the Dust Ledger's
  shapes:
  - **reach**: a company unit enters an area;
  - **hold**: a company unit stays in an area for N seconds, and the clock
    restarts if it leaves (warden, feed);
  - **carry**: a company unit takes a prop from one area to another (coupling);
  - **escort**: a captive or ally reaches an area alive (broker).

  A mission may add a custom trigger, but it uses the same interface.
- **Window.** When it is open and when it lapses: a phase (open until the
  alarm), a clock, or another objective's state. A lapsed job pays nothing,
  and one half done when its window closes also lapses.
- **Battle effect.** What completion changes, applied through the existing
  seams only:
  - allies join (an authored squad spawned or released);
  - defences go dark (`TurretStateService.cutPower`);
  - a reveal (spotters);
  - a hostile group stands down (the cantina holds).

  The effect is a callback the mission supplies. The toolkit never grants
  victory or knowledge a perspective may not have.
- **Return.** What the campaign keeps, so a job can pay off later:
  - an outcome on `MissionOutcome` (done, lapsed or failed, per job);
  - a debrief line chosen by that outcome;
  - a campaign mark: a durable, namespaced fact such as
    `corvus.harrow.coupling_delivered` that later missions, dialogue and
    offers can read.

  The mark belongs to the campaign layer, not the battle. Settlement writes it
  from the outcome, never from the live simulation. Nothing reads a mark in
  this story; reading one is the next consumer's work.

State is `OFFERED` (available, not started), `WORKING` (trigger in progress),
`DONE`, `LAPSED` (window closed first) or `FAILED` (the job was ruined: the
escort died). These map onto `MissionObjectiveSnapshot.State` for the picture.

## Presentation

- The checklist gains an **Optional** section beneath the mission's numbered
  steps. Its rows carry their own heading and mark, and never count toward the
  completed tally. A lapsed or failed row stays visible, struck, until the
  battle ends.
- Side-objective beacons use a distinct marker style (shape and colour) from
  the mission's numbered markers, so the map reads "must" apart from "may".
  Locate works on them as on any row.
- **Notices**: a short toast and an optional radio or contact line when a job
  opens, completes, lapses or fails. Lines go through the battle radio's voice
  budget, or through the narration catalog when the offerer is a contact.
  The toast is never the only record: the row shows the same state.
- The briefing lists the offered jobs under their own heading. The debrief
  names each job's result.

## Work

1. **Model and state owner.** Add `SideObjective` (definition plus live state)
   and a small service on `BattleSimulation` that ticks the triggers and
   windows and fires effects once. It also publishes rows into the mission
   picture. Unit-test each trigger and the window rule on the smallest input.
2. **Presentation.** Add the Optional section, the marker style and the
   notices through the existing checklist and beacon renderers. Take
   1920x1080 look shots of the Dust Ledger quiet phase with all three jobs
   offered, one working and one done.
3. **Return.** Add per-job outcomes on `MissionOutcome`, debrief line
   selection and the campaign-mark write in settlement. A save round-trip
   test covers the mark.
4. **Dust Ledger migration.** Re-express the coupling, warden, feed and
   broker on the toolkit and delete their bespoke code from
   `DustLedgerSequence` and `DustLedgerChecklist`. Behaviour must not change:
   the `battle.dustLedger.sideObjectives` lever keeps working, and
   `commanderEvidence -Pmission=corvus` on `DUST_LEDGER` reads the same before
   and after.

## Acceptance

- A mission adds a side job by declaring hook, trigger, window, effect and
  return, without touching the checklist, beacons or debrief code.
- The Dust Ledger plays the same with its four jobs on the toolkit, and its
  old bespoke code is gone.
- On screen, optional rows and beacons are distinct from required ones, and
  a lapsed job reads as lapsed.
- The campaign keeps a mark per finished job, and the debrief names it.
- Tests cover the triggers, windows and the mark. Each runs in under one
  second; no battle is played in `test`.

## Owner answers (2026-10-26)

- Marks are permanent until a story clears them.
- Some jobs are hidden until discovered in the field: a discovery trigger on
  the definition, a notice and a short "new" mark when found, and a missed job
  reported only by an authored line. Built.
- Pay depends on what the job is for: debrief text and mark, credits, or both,
  declared on the job's return and paid at settlement. Built; the Dust Ledger's
  amounts are an owner pick (`dust-ledger-crossing.md`, Side objectives).
