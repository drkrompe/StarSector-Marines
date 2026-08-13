# Named stationing detachments

**Status:** COMPLETE — named stationing lifecycle shipped (2026-08-12)

## Problem

Stationing predates persistent rank-and-file personnel. Acceptance currently
removes anonymous marine cargo, stores one `contractMarinesCommitted` count,
and marks only the captain `GARRISONED`. Incidents and garrison defenses scale
from that count, so their battles cannot deploy the actual fireteams, attribute
individual casualties, or return named survivors.

Now that briefing command is complete, stationing must become a real personnel
lifecycle. This is campaign/personnel authority. Battles consume a frozen
detachment and report identities; they do not decide who remains stationed or
who returns.

## Contract

### Persistent binding and availability

- Each non-reserve `MarineSquad` may carry one `stationingContractId`; `-1`
  means available. The squad id remains stable and home command is unchanged.
- Binding is whole-fireteam organizational state. Every member remains in the
  persistent roster with their existing ACTIVE/WIA/MIA/KIA disposition.
- A stationed fireteam is excluded from ordinary briefing defaults, borrowing,
  transfer, demobilization, and equipment mutation. WIA recovery still advances;
  a recovered member rejoins their still-stationed team rather than appearing
  in the home deployment pool.
- Only the selected active captain's rank-bounded formation may be stationed.
  Home teams are defaults; bounded borrowing does not rewrite home command.

### Acceptance and strength

- New stationing acceptance selects whole named fireteams. It never removes
  marine cargo: enlisted named personnel already left cargo when recruited.
- `contractMarinesCommitted` remains for retainers, existing trigger math, UI,
  and save compatibility, but for named assignments it is a derived living
  headcount: bound members whose status is ACTIVE or WIA. KIA/MIA are no longer
  committed strength.
- Incident battle seats use only ACTIVE bound members at mission creation.
  WIA remain assigned but do not deploy. Payloads therefore carry both the
  derived committed headcount and frozen fireteam ids/active seat count.
- Acceptance is atomic: validate captain, rank cap, unique available line
  teams, at least one ACTIVE member, contract terms, and contradictory-work
  rules before binding any team or changing the captain status.

### Legacy contracts

- A stationing row with `contractMarinesCommitted > 0` and no bound fireteams
  is a legacy anonymous assignment. Never auto-bind current roster teams: its
  cargo was already removed, so doing both would double-charge personnel.
- Legacy rows retain their existing count/cargo return behavior through
  completion, withdrawal, default extraction, incidents, and save compaction.
- New offers require the named roster path. There is no new anonymous fallback.

### Incidents, defenses, and casualties

- Cadre incidents and Garrison defenses freeze the contract's bound fireteam
  ids into the stationing mission and load only those ACTIVE soldiers into the
  local shuttle manifest.
- `MissionOutcome` is the sole named casualty input. Shared deterministic
  disposition writes ACTIVE/WIA/MIA/KIA first; stationing resolution then
  recomputes the contract's derived living strength from the bound teams.
- A successful response keeps surviving teams bound. WIA remain stationed and
  may recover before a later incident.
- A failed/overrun response clears every binding after the outcome has assigned
  individual fates. Survivors and WIA return to normal roster authority; the
  captain keeps the outcome's ACTIVE/INJURED/KIA state. No cargo is created.
- If no ACTIVE bound member can answer a pending battle, the response cannot
  launch and the assignment resolves through an explicit no-force failure path;
  it must not generate anonymous replacements.

### Completion, withdrawal, and employer default

- Normal completion and idle early withdrawal clear all matching fireteam
  bindings exactly once, restore a still-`GARRISONED` captain to ACTIVE, and do
  not mutate marine cargo. Individual WIA/MIA/KIA state is preserved.
- Employer default keeps teams bound while the extraction obligation is open.
  Successful extraction clears bindings for the named survivors. Failed
  extraction marks every still-recoverable bound member MIA, clears the
  bindings, and applies the existing captain injury consequence.
- Every terminal path clears `contractCaptainId` and the derived committed
  count only after its named or legacy personnel mutation succeeds. Replay sees
  no remaining authority and performs no second return.

### Presentation

- Stationing configuration uses the same selected/max whole-fireteam language
  as briefing and shows active heads plus WIA separately.
- Management names the bound fireteams and reports ACTIVE/WIA/MIA/KIA totals.
  Incident copy names the actual detachment rather than only a scalar count.
- Results already carries frozen commander/fireteam context; stationing missions
  populate it and use the normal named fireteam debrief.

## Slices

1. **Domain binding** — persist `stationingContractId`, add roster availability,
   bind/unbind/query/repair operations, and prevent roster/armory mutation while
   away. Cover legacy field defaulting and duplicate-binding repair.
2. **Named acceptance and management** — replace marine-count acceptance for
   new offers with captain-scoped whole-fireteam selection; derive committed
   strength and update local stationing UI. Preserve the anonymous legacy path.
3. **Release lifecycle** — branch completion, withdrawal, and default extraction
   between named bindings and legacy cargo; make every path replay-safe.
4. **Incident battle bridge** — extend payloads/factories with frozen fireteam
   identity and ACTIVE seats, apply named casualty outcomes, recompute strength,
   and explicitly resolve no-force responses.
5. **Debrief and migration closure** — management/debrief presentation, save
   repair coverage, compaction guards, and full legacy/new lifecycle matrices.

## Slice 1 complete

`1432dda1` persists the fireteam-side contract id and adds atomic rank-bounded
bind, query, availability, and replay-safe release operations. Stationed teams
are excluded from ordinary line readiness and captain deployment selection.
`379c4874` closes every home-roster mutation path: recruitment, transfer,
command reassignment, captain removal, and individual or whole-team equipment
changes reject stationed fireteams, with disabled armory controls and explicit
save repair for legacy defaults and illegal reserve bindings. Focused binding,
command-policy, armory, and readiness tests pass. Cargo, stationing acceptance,
and legacy contract settlement remain unchanged for Slice 2.

## Slice 2 complete

`113c4603` adds the campaign acceptance transaction for named detachments. It
validates and binds unique rank-bounded fireteams without touching cargo, counts
ACTIVE and WIA as living committed strength, excludes KIA/MIA, and freezes the
resulting terms and captain assignment through the existing contract columns.
`d529805d` moves the live stationing offer screen exclusively onto that route:
home teams default in, available teams may be borrowed within command cap,
large rosters paginate, and management names the bound teams with RTD/WIA
strength. The old cargo-consuming acceptance helper is package-only test
coverage; no live offer can create a new anonymous assignment. Existing
anonymous active contracts and all settlement paths remain unchanged for the
Slice 3 named/legacy branch.

## Slice 3 complete

`a31e9a2a` branches idle early withdrawal by personnel authority. Named
assignments release their bound fireteams and create no cargo; anonymous legacy
rows still return the exact stored marine count. A failed cargo delivery or
named release leaves contract columns, captain state, and reputation untouched,
and replay cannot settle the same withdrawal twice. `0039f9b5` applies the same
authority split to normal completion. `c05999e2` completes employer-default
extraction: teams remain bound while recovery is pending, success releases them
without cargo, and failure marks every recoverable ACTIVE/WIA member MIA before
release while preserving prior KIA/MIA. Captain consequences, employer breach,
legacy cargo restoration, retry semantics, and replay guards remain intact.
The combined completion, withdrawal, extraction, binding, and acceptance tests
pass; Slice 3 is closed.

## Slice 4 complete

`492864ef` freezes bound fireteam IDs and current ACTIVE seats into both Cadre
incident and Garrison defense payloads. WIA remain in committed living strength
but do not consume battle seats; local lift sizing now follows the frozen ACTIVE
count. The response transition seeds the battle context from those immutable
team IDs, while anonymous legacy payloads retain their scalar seat behavior.
`1a2fc58e` applies individual battle fates before campaign writeback, rejects
stale frozen formations, recomputes named living strength from the bound roster,
keeps successful survivors stationed, and clears named authority after a failed
response. `9e4c1ac2` closes the zero-ACTIVE-seat case without launching an empty
battle: WIA identities are preserved, teams are released, the unengaged captain
returns ACTIVE, and terminal contract authority is cleared. Anonymous payloads
continue through their existing scalar loss path. Focused payload, mission
factory, casualty, no-force, and legacy resolution tests pass; Slice 4 is closed.

## Slice 5 complete

`721372d8` prevents compaction from removing any terminal stationing or linked
extraction row that still owns named bindings, anonymous cargo, or a captain.
`a8b72598` adds daily cross-graph save reconciliation: valid named rows repair
derived living strength and captain availability, dangling or invalid bindings
release safely, and anonymous rows are never auto-bound or recounted.
`496cea19` distinguishes named stationed detachments from legacy aggregate
personnel in Results and includes bound WIA who did not consume battle seats.
The combined Stationing, GarrisonDefense, ExtractionResolution, compaction,
repair, payload, binding, and debrief test matrix passes. All five slices and
every acceptance criterion below are complete.

## Acceptance

- One fireteam cannot be stationed on two contracts or selected for an ordinary
  mission while away.
- New acceptance consumes no anonymous cargo and binds only whole, available
  teams within the active captain's rank cap.
- Every incident deploys only the identities already stationed; no unrelated or
  generated player personnel fill missing named seats.
- Completion, withdrawal, response failure, and both extraction outcomes each
  settle named personnel exactly once without cargo duplication.
- Anonymous saves continue to return the exact stored cargo count and are never
  silently converted into named assignments.
- Stationing Results remains stable if home command or roster organization
  changes after the battle.

## Explicit non-goals

- No battle-AI or map-generation changes.
- No per-market physical barracks inventory or travel-time simulation.
- No mid-term reinforcement/replacement UI in v1.
- No conversion of ambient/employer marines into player personnel.
