# Deployment readiness and debug personnel

**Status:** SHIPPED (2026-08-12)

## Commit chain

- `f169cebe` locked the production and debug readiness contract.
- `605cda22` gave every debug-picker mission an explicit debug source,
  Recruit/Mixed/Veteran fixtures, complete player-seat allocation, and a hard
  no-writeback outcome path.
- `59c4864b` added exact selected/company readiness accounting and cargo-bounded
  bulk enlistment into line fireteams.
- `75413bfc` surfaced the counts in briefing and assignment UI, routed selection
  shortages separately from company shortages, and returned mission-scoped
  armory visits to their originating screen.

## What landed

- Debug missions no longer depend on the persistent roster. The briefing
  cycles three non-persistent personnel/equipment presets, including manifests
  large enough for the 40-drop override. Fixture loadouts have no campaign
  soldier ids, employer seats remain scenario-authored, and debug resolution
  cannot pay out or mutate personnel, captains, armory, industries, contracts,
  or events.
- Production missions remain strict. The shared readiness model distinguishes
  selected-ready personnel from total line-company strength and reports the
  exact shortfall.
- A selection-only shortage opens fireteam assignment. A company shortage opens
  the armory with the mission seat target. Bulk enlistment consumes one cargo
  marine per named recruit, fills vacant line billets, creates line fireteams as
  required, stops when cargo runs out, and never routes recruits into reserve.
- Newly created fireteams are not silently added to an explicit mission
  selection. Returning to the briefing therefore changes a remaining block from
  recruitment to assignment when appropriate.

## Verification

- Fixture tests cover all three presets at 280 seats and verify their identities
  are non-persistent and their profiles/equipment differ.
- Logistics tests cover full and partial cargo-bounded bulk enlistment.
- Readiness tests cover selection-only versus company-wide shortages and the
  implicit whole-line selection.
- `gradlew.bat build --no-daemon --max-workers=1` passed after the UI wiring.
- Manual playtesting was intentionally deferred for this session.
