# Living world — next session

## State of play

The autonomous political sim ([`overview.md`](overview.md)) is being built
along the A–E slice spine. **A (genesis), B (player transfer), and C (drift +
autonomous promotion) are shipped.** The board is seeded with contested stakes;
player ops move them decisively while the background world moves them slowly.
ACTIVE houses adopt a deterministic local consolidation target (`c26ca415`),
drift 3–5 share every seventh day (`daf3ef1b`), and gain one daily promotion
point while holding a strict home-market majority (`11987f9a`). D1 is also
shipped: monthly autonomous plots bind an actor, rival, market, and industry;
advance one point per day; and resolve exactly once into a 40-share transfer
plus 30 promotion progress (`3137f238`, `2f7b4a86`, `1d6ca71c`).
D2 is shipped too: learned events persist as append-only snapshots, terminal
chains pass through the intimate/epic/silent editor exactly once, and debug
intel renders the resulting dispatches (`dc3da47d`, `068943ed`, `55eefe4f`).
D3 is shipped: relevant active plots roll deterministic weekly discovery,
persist uncertain rumor snapshots, and expose a stable threatened-house query
for intervention generation (`435b704e`, `b006dc3`, `3a16315e`).
D4 is shipped: intervention contracts distinguish opposed chains from parent
chains, threatened houses issue one bounded Strike offer, stale offers withdraw,
and a correctly attributed player victory fails the hostile plot exactly once
(`dd63a0ba`, `0a2f3c09`, `7b54ec81`).
E1 is shipped: houses with exhausted stake history become stable-id DORMANT
tombstones, their political work closes safely, stale consolidation targets
retarget or clear, and only meaningful disappearances enter the Chronicle
(`975db3ba`, `f736a7d6`, `eefc505c`, `500d2c17`).
E2 is shipped: houses persist a 30-day ambition-review clock; strong Tier-1/2
houses switch from consolidation to rank-targeted promotion intent at 75% of
their progress threshold; and Tier-3 houses at 750 progress / 1000 power persist
a throne claim against their faction identity (`f022d4ad`, `31bb6875`,
`3c58964b`, `57be6214`). Vertical ambitions deliberately do not create generic
consolidation chains.
The first vertical payload is also shipped: non-majority Tier-1/2 promotion
schemes bind a same-faction local rival for 60 days, resolve into +90 actor / -30
rival promotion progress plus a 20-share material shift, and safely short-circuit
if another route already promoted the actor (`0a00ceb0`, `156ac5b1`, `a4cd42c8`).
The throne-claim producer is shipped too: generic progress caps at Tier 3,
capped claimants run 180-day discoverable/intervenable civil wars, and
resolution prepares one persisted source-chain-unique handoff without changing
vanilla state or Tier 4 (`76c7579a`, `9bf2356b`, `bdb45f7b`, `35ec0ccf`,
`77657bb8`, `51fad0ca`).
The isolated throne-claim consumer is now shipped: one predeclared Claimant
League provides supported faction identity; a narrow adapter probes, repairs,
transfers, and verifies vanilla market/entity ownership; successful handoffs
become `APPLIED` and promote their claimant to Tier 4; and Chronicle dispatch
waits until that writeback finishes (`42f00725`, `ae35056d`, `870d5b96`,
`5174f44c`).
The first diplomacy consequence is shipped as a separate persisted lifecycle:
after ownership applies, the Claimant League and former ruler become mutually
hostile through a postcondition-first, partial-repair-safe adapter. Autonomous
success remains player-reputation-neutral until an explicit kingmaker choice can
be attributed (`ad6ff5fd`, `527535fb`).
Applied claims now also emit a dedicated immutable faction-flip dispatch that
names the claimant, displaced rival, source/result factions, market, and actual
writeback day (`d7be2649`, `31be86ed`).
The civil-war participation foundation is shipped: existing lineage encodes
claimant vs incumbent work, contracts snapshot their progress band, the first
successful operation locks chain allegiance, early work applies ±15/±30
progress, open-conflict assaults are decisive, completed stationing work is
recoverable, and successful claimant attribution snapshots into the throne
handoff (`926047e8`, `4332927f`, `50591863`, `3610923d`, `487134ae`).
The player-facing offer lifecycle is shipped too: a dedicated producer creates
one paired offer per side/band with a frozen contested objective, retires stale
offers, and shared battle/stationing acceptance immediately withdraws the
opposing choice without blocking later assault phases (`551081ab`, `4f6afb8b`).
Attributed terminal consequences complete the participation arc: cumulative
support scales fixed house-reputation deltas, applied claimant handoffs reward
the claimant side, same-day decisive incumbent failures reward the incumbent,
and autonomous or stale outcomes remain neutral through replay-safe persisted
lifecycles (`753f3969`, `0ef347d3`, `03422b15`).
The silent moral-compass foundation is shipped: four bounded hidden axes feed an
append-only source-unique choice ledger, and attributed civil-war outcomes move
institutionalism without making unsupported claims about mercy, integrity, or
stewardship (`facfa007`, `6765eac6`, `2a1924e7`). No numeric surface exists.
The civilian-rescue foundation is shipped as the second honest source family:
dedicated event rows persist trigger-unique identity, cost, stakes, choice, and
outcome; explicit refusal and positive rescue feed mercy/stewardship exactly
once; passive expiry and zero rescue remain morally neutral (`cf8b717b`,
`5fd8969d`, `1b2afcb4`). That foundation remains independent from swarm art,
AI, and battle content. The trigger/choice vertical is now shipped too:
deterministic 45-day epochs select one eligible market without
iteration-order dependence, Distress Net presents exact costs/stakes/deadline
and routes commit/refuse through the shared policy, and debug intel can force a
production-shaped local call (`0da1b89e`, `34cf0654`, `5572c538`, `24ba5bdc`).
Committed calls now remain open until the dedicated local battle reports them.
The committed-event mission-lineage foundation is shipped: missions/outcomes
carry explicit event id, market, and stakes without overloading contracts; a
pure factory creates a deterministic zero-economy Extraction-shaped snapshot;
and strict writeback accepts only an explicit in-range evacuation report for the
matching committed row (`2a5461a6`, `0d49d30e`, `fdfb0aef`). Generic Extraction
remains unchanged; the emitted event mission uses a dedicated battle factory.
The battle-side evacuation foundation is now shipped as well: an eight-member
identity tracker distinguishes active, evacuated, and lost representatives;
only a complete sealed cohort reports; and `MissionResolver` scales that report
to campaign stakes without consulting ordinary battle victory (`cc34a2ab`,
`1174cae9`, `03017229`). That first checkpoint deliberately left production
spawning and objective behavior unwired.
The controlled objective foundation now sits on that tracker: registered live
entities count only when they enter the lift zone, registered deaths/missing
entities become lost, ambient civilians are excluded by identity, and all
battle-terminal paths seal before Results (`a2fa2a70`, `5d6257f5`). Production
placement, routing, and mission emission are now shipped too: deterministic
residential-to-outer-band placement installs eight mission-only VIPs, a serial
system routes and boards them, campaign-event launch selects the dedicated
battle factory, and the matching market exposes one Distress Net mission
(`c870193f`, `d1dae861`, `2cd8416a`, `bee0c6b4`, `84e9e175`).
The intended biological threat payload is now shipped. Append-only
`SWARM_RUNNER` / `SWARM_PRESSURE` identity reuses the held alien sheets without
changing the battle-side faction model; runners prioritize registered active
evacuees, fall back to marines, and deal contact damage. Deterministic
LOW/MEDIUM/HIGH rosters place 12/24/40 runners outside the shelter and lift
zones, while the rescue factory now omits conventional defenders, defense-post
turrets, reinforcement providers, and fighter support (`94cb765b`, `80020b48`,
`6b386199`, `88421954`, `710d2981`).
The first playtest correction is shipped too: `SWARM_PRESSURE` no longer rolls
the generic on-hit fallback that made runners stop under sustained fire, and
full-distance contact pursuit is locked by regression tests (`fb50b964`).
Debug rescue also scales swarm size from simultaneous first-wave marine seats
instead of fighting the fixed production 12/24/40 roster or counting later
sortie cycles at time zero. LOW/MEDIUM/HIGH use 2:1, 3:1, and 4:1 pressure, with
a 24-cell shelter approach band so the first landing can establish a defense
(`8b2af722`, `e0d29078`). Production remains unchanged.
Separation's interaction with that pressure is now regression-locked too:
eight coincident runners all retain the shared target and sustain repeated
melee attacks after the crowd fans out (`4973fc91`). This closes the movement
primitive risk. The first stat/art feel pass is now shipped as well: generic
aliens drop from 30 to 7.5 HP and runners from 24 to 6 HP; both live archetypes
use generated true-overhead body/head/foot layers on the existing marine
compositor, without firearm layers, while the held alien sheets remain fallback
and corpse art (`bccbbe16`). The manual queue still owns roster size and
post-rebalance time-to-contact/kill feel. The modular creature now also reuses
one generated forearm/claw layer on both sides: claws stay beneath the carapace
at rest, then one alternating claw moves forward above the torso for each
contact swipe (`20d3bcb0`). Normal-zoom swipe readability remains manual.

Outcome closure has its first checkpoint too. The debug client offers direct
LOW/MEDIUM/HIGH swarm-rescue scenarios without campaign writeback; controlled
swarm fixtures verify zero/partial/full evacuation; and Results displays both
representative and campaign-scaled rescue totals (`38bc6323`, `a27064fc`,
`cf442e11`). Distress Net now retains the newest terminal rescue result after
the active call closes (`9e0417aa`).

Two reusable primitives now exist on top of `CampaignState`, and they are the
seams the rest of the thread builds on:

- **`StakeLedger`** — `seizeShare` + stake queries (`findStake`, `shareOf`,
  `totalClaimedShare`, `unclaimedShare`). Tombstone-at-zero soft-delete.
- **`HousePromotion`** — `addProgress` / `addProgressAndPromote` (carry +
  cascade; TIER_4 terminal).

Both are stateless ops, fully unit-tested. The intent: Slices C–D are mostly
"call these on a tick," not new mutation logic.

## Completed — G8 defector asylum

The first black-swan event now runs from deterministic trigger through choice,
mission emission, swarm battle, explicit report, campaign writeback, debrief,
hidden moral consequence, and durable terminal dispatch. The second archetype
is now complete in
[`complete/slice-g8-defector-asylum.md`](complete/slice-g8-defector-asylum.md): a discovered
political chain produces a costly asylum request, followed ten days later by an
explicit keep-the-promise/betrayal choice. Slice 1 is now shipped in `94a3f2f1`:
the two-stage transition authority, atomic resource/payment seams, legacy
backfill, and default-to-protection timeout are covered. Slice 2 is now shipped
in `7591dd86`: physical row order cannot change selection, epoch and source
identity prevent replay, and rescue/defector producers share one open-event
gate. Slice 3 is now shipped in `0235ff29`: a global Encrypted Channel
reconstructs the initial request, protected-custody interval, and follow-up
offer from persisted state; exact frozen terms and identities drive the copy;
all four buttons route through the existing exactly-once lifecycle authority;
and terminal presentation fails closed when a resolved row lacks its typed
outcome. Slice 4 is now shipped in `d2c4c01b`: refusal, protection, and betrayal
apply their frozen plot/reputation and hidden moral consequences exactly once;
event timeouts settle before the source chain's daily advancement; ended or
malformed sources fail closed without rewriting history; terminal Encrypted
Channel copy remains durable; and debug intel can force both stages through the
production lifecycle. Focused replay/load coverage and the full root automated
suite pass. Manual UI validation and post-rebalance swarm feel remain deferred.

## Completed — G9 Silent Colony

The third black-swan archetype is complete in
[`complete/slice-g9-silent-colony.md`](complete/slice-g9-silent-colony.md). A dead colony's
automated burst asks the company to fund a blind expedition; a later dedicated
mission will reveal stranded survivors, a sealed archive, and the colony's own
dormant automated threat only after commitment.

Slice 1 is shipped in `4d50805d`. `SILENT_COLONY` is append-only event identity;
the event row freezes exact expedition cost, representative survivor stakes, and
a hidden deterministic threat seed. Funding is atomic, refusal/expiry reveal
nothing, and only an explicit later mission report can resolve survivor count
plus archive `LOST`/`RECOVERED`. Legacy saves backfill safe sentinels and replay
cannot charge or resolve twice.

Slice 2 is shipped in `33b073bd`. Starting on day 90, one unused dead site per
90-day epoch is selected from live decivilized, abandoned-station, or
condition-only ruins identities behind the common open-event gate. Only the
chosen site is interned. Severity freezes exact expedition stores and survivor
stakes, while selection and the hidden threat seed remain stable across source
enumeration order. The globally registered Dead Letter stays hidden until a
valid row exists, reconstructs pending/committed/terminal state after load, and
routes funding/refusal without revealing survivors, archive, threat, reward, or
moral meaning. Focused compatibility/replay tests and the full root suite pass.

Slice 3 is shipped in `9a87c85b`. A committed row now emits one stable local
zero-economy expedition mission with frozen event/site/threat lineage. Its
dedicated factory installs the exact 6–12-member survivor cohort, a separate
timed archive-room objective, and a hidden-seed-selected autonomous defense
profile made only from turrets and drone hubs. The seed freezes threat profile,
map, and placement independently of the ordinary battle seed. Results reports
survivor and archive facts separately, and an unfinished battle cannot invent
archive loss. Existing civilian rescue remains on its biological-swarm path.

Slice 4 is shipped in `43bd3693`. Silent Colony now dispatches through its own
strict resolver, which binds the explicit survivor/archive report to the frozen
event key, market, cohort, threat seed, and zero-economy mission envelope. The
first valid report closes the event and creates one immutable Chronicle
snapshot; committed saves can still resolve, while terminal save/load or mission
replay cannot replace the facts or duplicate the dispatch. Dead Letter retains
the terminal report, and debug intel can force an eligible local site through
production terms. Archive evidence remains narrative-only, and no moral row is
recorded because the mission never asks the player to state a priority or make a
promise.

No living-world story is currently active. Contract the next black-swan or
political follow-through slice before implementation; manual UI validation and
swarm tuning remain in the shared deferred queue.

## Open forks still unresolved (design)

- Horizontal (stake competition) vs vertical (loyalty/rebellion) axis — which
  to wire first. Leaning horizontal. See [`ambition.md`](ambition.md).
- "Information has to pay rent" — don't surface a trait on the dossier before
  the system it gates ships. See [`ambition.md`](ambition.md).
- The 7 open questions in [`overview.md`](overview.md) §"Open questions"
  (drift cadence/magnitude, conservation, Chronicle storage, discovery surface,
  …) — most are C/D balance-pass concerns.

## Follow-ups surfaced by the Slice B review

- **`StakeLedger` composite index.** Weekly drift is still small enough for the
  current linear scans. Add `(house,market,industry) → row` only if profiling
  or Slice D chain volume makes it worthwhile.
- **Debug `forceComplete` political shift.** `CampaignDebugIntel.forceComplete`
  flips contract state + rep but intentionally skips the stake seizure /
  promotion (it has no struck industry). For playtesting the political layer
  without running battles, give it a target-derived industry and call the same
  primitives — small, deferred.
- The contract-targeting / affinity / tier-scaling items in
  [`complete/slice-b-player-transfer.md`](complete/slice-b-player-transfer.md)
  §"Follow-ups".

## Commit chain

- Slice A — genesis seeding (`HouseSeeder` rewrite + overview + README row).
- Ambition layer doc (`ambition.md`).
- Slice B — `StakeLedger` + `HousePromotion` + `MissionResolver` wiring +
  tests + this doc set.
- Slice C1 — home-market-majority autonomous promotion (`11987f9a`).
- Slice C2a — minimal persisted consolidation ambitions (`c26ca415`).
- Slice C2b — exactly-once weekly 3–5 share drift (`daf3ef1b`).
- Slice D1a — autonomous chain identity + lifecycle (`3137f238`).
- Slice D1b — monthly deterministic chain creation (`2f7b4a86`).
- Slice D1c — daily advancement + exactly-once resolution (`1d6ca71c`).
- Slice D2a — append-only learned Chronicle storage (`dc3da47d`).
- Slice D2b — intimate/epic/silent terminal editor (`068943ed`).
- Slice D2c — Chronicle debug-intel dispatches (`55eefe4f`).
- Slice D3a — active-rumor persistence (`435b704e`).
- Slice D3b — deterministic active discovery (`b006dc3`).
- Slice D3c — discovered-threat intervention query (`3a16315e`).
- Slice D4a — separate opposed-chain contract lineage (`dd63a0ba`).
- Slice D4b — bounded threatened-house intervention offers (`0a2f3c09`).
- Slice D4c — validated player-success chain intervention (`7b54ec81`).
- Slice E1a — empty-house dormancy + political cleanup (`975db3ba`, `f736a7d6`).
- Slice E1b — stale consolidation re-evaluation (`eefc505c`).
- Slice E1c — selective dormancy Chronicle events (`500d2c17`).
- Slice E2a — persisted monthly ambition review (`f022d4ad`).
- Slice E2b — power/progress-gated promotion intent (`31bb6875`).
- Slice E2c — faction-targeted Tier-3 throne claims (`3c58964b`).
- Slice E2d — vertical-chain integration guards (`57be6214`).
- Slice F1a — ordinary promotion-chain contract (`0a00ceb0`).
- Slice F1b — deterministic promotion-chain creation (`156ac5b1`).
- Slice F1c — exactly-once promotion-chain resolution (`a4cd42c8`).
- Slice F2a — Tier-3 progress handoff cap (`76c7579a`).
- Slice F2b — throne-handoff contract + persistence (`49f057ad`, `9bf2356b`).
- Slice F2c — civil-war creation + handoff preparation (`bdb45f7b`, `35ec0ccf`).
- Slice F2d — discovery/intervention + replay guards (`77657bb8`, `51fad0ca`).
- Slice F3a — isolated handoff consumer (`42f00725`).
- Slice F3b — predeclared Claimant League (`ae35056d`).
- Slice F3c — vanilla ownership adapter (`870d5b96`).
- Slice F3d — post-writeback Chronicle ordering (`5174f44c`).
- Slice F4a — persisted consequence lifecycle (`ad6ff5fd`).
- Slice F4b — replay-safe diplomatic rupture (`527535fb`).
- Slice F5a — immutable faction-flip Chronicle snapshot (`d7be2649`).
- Slice F5b — faction-flip dispatch rendering (`31be86ed`).
- Slice F6a — civil-war participation contract (`926047e8`).
- Slice F6b — persisted participation schema (`4332927f`).
- Slice F6c — validated weighted/decisive contributions (`50591863`).
- Slice F6d — throne attribution snapshot (`3610923d`).
- Slice F6e — recoverable contribution integration (`487134ae`).
- Slice F7a — paired band offer generation (`551081ab`).
- Slice F7b — shared acceptance and opposing withdrawal (`4f6afb8b`).
- Slice F8a — terminal player-consequence contract (`753f3969`).
- Slice F8b — persisted consequence lifecycles (`0ef347d3`).
- Slice F8c — attributed claimant/incumbent reputation (`03422b15`).
- Slice G1a — silent compass/ledger contract (`facfa007`).
- Slice G1b — persisted axes and source ledger (`6765eac6`).
- Slice G1c — civil-war moral source consumer (`2a1924e7`).
- Slice G2a — civilian-rescue lifecycle contract (`cf8b717b`).
- Slice G2b — campaign-event persistence (`5fd8969d`).
- Slice G2c — rescue choices, expiry, and moral recording (`1b2afcb4`).
- Slice G3 contract — deterministic trigger and Distress Net (`0da1b89e`).
- Slice G3a — automatic civilian-rescue producer (`34cf0654`).
- Slice G3b — registered Distress Net choice surface (`5572c538`).
- Slice G3c — debug local reachability (`24ba5bdc`).
- Slice G4 contract — mission lineage and explicit rescue reports (`2a5461a6`).
- Slice G4a — event mission/outcome fields, key, and factory (`0d49d30e`).
- Slice G4b — strict replay-safe rescue outcome bridge (`fdfb0aef`).
- Slice G5 contract — representative evacuation cohort (`cc34a2ab`).
- Slice G5a — tracker, scaling, and outcome report (`1174cae9`, `03017229`).
- Slice G5b — objective, death, and terminal accounting (`a2fa2a70`, `5d6257f5`).
- Slice G5c — placement, installation, and routing (`c870193f`, `d1dae861`,
  `2cd8416a`).
- Slice G5d — dedicated battle launch and local emission (`bee0c6b4`,
  `84e9e175`).
- Slice G6 contract — swarm-defense threat identity (`94cb765b`).
- Slice G6a — append-only runner archetype (`80020b48`).
- Slice G6b — evacuee-first pressure behavior (`6b386199`).
- Slice G6c — deterministic risk-scaled roster (`88421954`).
- Slice G6d — dedicated rescue-factory integration (`710d2981`).
- Slice G7a — direct debug swarm-rescue missions (`38bc6323`).
- G6 playtest correction — implacable pursuit under fire (`fb50b964`).
- G6 debug scaling — swarm pressure follows first-wave strength and preserves
  an opening deployment phase (`8b2af722`, `e0d29078`).
- G6 health/art feel pass — quarter-HP aliens and modular live xeno composition
  (`bccbbe16`).
- G6 layered swipe follow-up — generated fore-claw layer and alternating
  foreground contact animation (`20d3bcb0`).
- Slice G7b — debug-safe zero/partial/full outcome bridge (`a27064fc`).
- Slice G7c — representative/scaled evacuation debrief (`cf442e11`).
- Slice G7d — durable Distress Net resolution dispatch (`9e0417aa`).
- G8 contract — discovered-chain defector asylum and delayed integrity choice
  (`6337ce55`).
- G8 Slice 1 — append-only persistence and two-stage lifecycle (`94a3f2f1`).
- G8 Slice 2 — deterministic discovered-chain producer (`7591dd86`).
- G8 Slice 3 — reconstructible Encrypted Channel choices (`0235ff29`).
- G8 Slice 4 — world reaction and terminal closure (`d2c4c01b`).
- G9 contract + Slice 1 — Silent Colony persisted expedition authority
  (`4d50805d`).
- G9 Slice 2 — deterministic dead-site producer and reconstructible Dead Letter
  (`33b073bd`).
- G9 Slice 3 — dedicated expedition mission, autonomous threat, survivor cohort,
  physical archive, and dual debrief report (`9a87c85b`).
- G9 Slice 4 — strict event closure, immutable Chronicle report, durable Dead
  Letter facts, and debug/save/replay reachability (`43bd3693`).
