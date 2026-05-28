# Campaign contracts

> Continues from [`../themes.md`](../themes.md) / [`../economy.md`](../economy.md) /
> [`../mechanics.md`](../mechanics.md). Specifies the contract layer — the
> commercial agreement between a house and the player that sits between
> a chain (multi-month political plot) and a mission (single battle
> instance).

## Three-layer hierarchy

```
Chain     — multi-step political play. Months of in-game time.
            patron house drives a target's promotion / sabotage / claim.
            Lives in chains[]. See mechanics.md.
   ↓ spawns
Contract  — commercial agreement. Days to months. One scope of work,
            one paying house, defined payment + salvage terms.
            Lives in contracts[]. This document.
   ↓ spawns
Mission   — single battle instance. Hours of in-game time.
            ASSAULT / SABOTAGE / RAID / EXTRACTION / CONQUEST.
            Already exists; produced by MissionGenerator.
```

Each layer is a different *scope of decision* for the player:

- Chain choices are *which patron* — strategic alignment for the next
  10-mission arc.
- Contract choices are *which agreement* — tactical commitment of the
  next captain-week.
- Mission choices are *how to fight* — drop count, support roster,
  battlefield approach.

The mission resolver writes outward through the layers: a mission
outcome advances its contract; a contract completion advances its
chain (if any); a chain resolution mutates houses / stakes / rep.

## Two modes: Stationing vs Mission

Contracts come in two structurally different modes — independent of
which of the five contract *types* they are:

| Mode | Marines/captain | Income | Mission cadence | Default risk |
| --- | --- | --- | --- | --- |
| **Stationing** | Committed for term (parked) | Monthly retainer | Reactive only — spawn if attacked or triggered | High (patron stops paying) |
| **Mission** | Deploys + returns between phases | Lump sum / staged on completion | Scripted 1:1 or 1:N sequence | Low (single payout endpoint) |

The cost of Stationing is the *opportunity cost* on marines and
captain — they're pulled from the vanilla cargo pool, captain status
flips to `GARRISONED`, both unavailable until the contract resolves.
Mission-mode contracts pay better per game-day but require the player
to be actively engaged.

## The five contract types

| Type | Mode | Mission cadence | Scope | Default salvage |
| --- | --- | --- | --- | --- |
| **Strike** | Mission | 1:1 single battle | Sabotage / raid / kidnap / one-off assault | 0–80% (varies by sub-type) |
| **Escort** | Mission | 1:1 single battle | Extract a person or asset under fire | 10% (defender wrecks) |
| **Planetary Assault** | Mission | 1:N scripted 3-5 phases | Take or break a fortified position | 60–100% |
| **Garrison** | Stationing | Reactive defense | Hold a market / industry / region against attack | 25% (only on reactive defense missions) |
| **Cadre** | Stationing | Rare event-triggered | Train a defender's officers; captain XP gain | 5% (training incidents only) |

### Strike

The atomic offensive contract. Sabotage / raid / kidnap / one-off
assault — one mission, one outcome, one payout. Sub-type drives the
risk profile and salvage baseline:

- *Sabotage Strike* — covert; low salvage (0-10%), high cash.
- *Raid Strike* — grab cargo and leave; high salvage (50-80%), medium
  cash.
- *Decapitation Strike* — kill or capture a named individual; medium
  salvage, high cash, rep consequences with target house.

A Strike completes when its single Mission resolves. Cash on completion.

### Escort

Get a person or asset off a planet under fire. Spawns an EXTRACTION
mission with the asset as the extraction target. Payout structure:
flat base + bonus if asset arrives unharmed. Salvage is lean —
defender wrecks only, no industry-recovery.

Distinct from Strike because the loss state isn't "mission failed"
but "asset destroyed/captured" — the contract can succeed
mechanically (extraction complete) while failing narratively (asset
dead). Affects payout, not just rep.

### Planetary Assault

The big-ticket Tier-3+ contract. Take or break a fortified position
in a scripted 3-5 mission sequence — recon → softening → main assault
→ optional mop-up / consolidate. Each phase is its own Mission;
between phases the contract sits in IN_PROGRESS, the patron pays a
phase bonus, the player gets a few in-game days to refit.

Defining feature: failure of any *non-final* phase doesn't fail the
contract — it forces a re-roll (next phase reflects the partial
outcome). Failure of the final phase fails the contract entirely.

Stake-transfer scale is large — Planetary Assault is how a Tier-3
patron seizes an entire industry from a rival in one arc.

### Garrison

Drop off marines + captain on a market. Patron pays monthly retainer
for the term. Contract idles in ACTIVE state day-to-day, transitioning
to IN_PROGRESS only when a reactive defense mission triggers (an
attack rolls against the market).

Garrison defense missions are NOT generated by `MissionGenerator` —
they're event-spawned by the campaign tier, triggered by:

- Rival house initiating a Strike against the protected market.
- Faction-level raid event (vanilla).
- Internal political flip — the patron's own faction defects.

Contract term: 1-6 months. Player can withdraw at any time (status →
ABANDONED), forfeiting remaining retainer and any pre-paid bonus. The
patron can default (status → DEFAULTED) for [reasons](#default--breach-mechanics)
below — triggers the extraction-mission flow from `economy.md`.

### Cadre

Captain parked at a market, training the patron's officers. The
captain accrues XP at an accelerated rate; the player accrues no
marines-on-the-field combat. Patron pays a monthly stipend (smaller
than Garrison — there's less risk on the player's side).

Rare "incident missions" can spawn during a Cadre — factory accident
during exercise, raid during live-fire training, defector reveals
sensitive info. Frequency: open question (see below). Without
incidents the Cadre is too passive — just clicking through months for
XP. With too many it's just Garrison with a different label.

## Rank-gated availability

Tightens the per-rank table from
[`../mechanics.md`](../mechanics.md#contract-type-gating):

| House rank | Contracts the house can offer |
| --- | --- |
| **Tier 1** (Baron / Manager / Capo / Cell Leader) | Strike (small sub-types), Garrison (single market, 1 month max) |
| **Tier 2** (Count / Director / Boss / Coordinator) | + Escort, Cadre, Garrison (cross-planet, longer terms) |
| **Tier 3** (Duke / VP / Don / Diocese) | + Planetary Assault, Strike (large sub-types incl. Decapitation) |
| **Tier 4** (Crown Claimant / CEO / Kingpin / Patriarch) | + chain-only T3-endgame contracts (faction civil war — see [`../t3-endgame/overview.md`](../t3-endgame/overview.md)) |

Working with low-tier patrons is how unproven mercs get hired; the
player grows alongside their patrons via the chain progression
mechanic. Tier-4 contracts are entry points into the
faction-flip endgame, not standard offerings.

## Lifecycle state machine

One unified state machine across both modes — Stationing spends most
of its life in ACTIVE; Mission spends most in IN_PROGRESS.

```
            ┌───────────┐
            │  ACTIVE   │ ◄─── created on player acceptance
            └───────────┘
                  │
       attack/    │ next-phase-ready
       trigger    │
                  ▼
            ┌───────────────┐
            │  IN_PROGRESS  │ ◄─── mission deployed / phase running
            └───────────────┘
              │     │      │
   success    │     │      │ phase-success (Planetary Assault)
   (one-shot) │     │      ▼
              │     │   [back to ACTIVE for next phase]
              │     │
              │     ▼
              │  ┌──────────┐
              │  │  FAILED  │ ◄─── critical mission failure
              │  └──────────┘
              ▼
        ┌────────────┐
        │ COMPLETED  │ ◄─── all phases done / term expired (Stationing)
        └────────────┘

  Side transitions from ACTIVE:
   - patron stops paying →  DEFAULTED  (spawns extraction mission)
   - player withdraws    →  ABANDONED  (forfeits remaining payout)
```

Six states: `ACTIVE / IN_PROGRESS / COMPLETED / DEFAULTED / FAILED /
ABANDONED`. Encoded as a `byte` on the `contracts[]` SoA row.

## `contracts[]` — sixth SoA table

Per [`../architecture.md`](../architecture.md) §1, this becomes the sixth
table in [`CampaignState`](../../../src/main/java/com/dillon/starsectormarines/campaign/CampaignState.java).
Skeleton columns:

```
id              long
patronHouseId   long
targetHouseId   long       // -1 for stationing/escort
chainId         long       // -1 for one-off (no parent chain)
type            byte       // STRIKE / ESCORT / PLANETARY_ASSAULT /
                           // GARRISON / CADRE
state           byte       // ACTIVE / IN_PROGRESS / ...
acceptedTick    int
expiresTick     int        // -1 for mission-mode (no expiry)
phasesTotal     byte       // 1 for Strike/Escort, 3-5 for Planetary Assault
phasesDone      byte
captainId       int        // interned via new captainRegistry IdRegistry
marketId        int        // interned
industryId      int        // -1 if not industry-targeted
basePayout      int
retainerPerMonth int       // 0 for mission-mode
salvageBaseline   byte     // 0..255 = % cap (per-type default)
salvageNegotiated byte     // 0..255 = actually-locked-in % at acceptance
cashMultiplier    byte     // 0..255; 100 = baseline, 110 = +10% (from salvage trim)
```

The mission resolver hooks here will add the writes path:
on mission victory, decrement `phasesDone` toward `phasesTotal`,
flip `state` when all phases land, then run the chain / stake / rep
updates from `mechanics.md`.

## Payment structures

Three structures, picked per type:

- **Lump sum** — Strike, Escort. Pay-on-completion; nothing during.
- **Staged** — Planetary Assault. Per-phase bonus + larger completion
  bonus. Phase bonus is small (10-20% of total) so the *full*
  completion is what the player optimizes for.
- **Retainer** — Garrison, Cadre. Monthly stipend paid into the
  player's vanilla credit pool. Per `economy.md`, baseline is
  `marineCount × $20 × 1.10` for Garrison, `× 0.4` for Cadre (much
  smaller — there's less risk on the player's side and the captain
  XP is its own reward).

Per-tier multipliers from `economy.md`:
- Tier 1 patron — 1× baseline.
- Tier 2 — 1.5×.
- Tier 3 — 3×.
- Tier 4 — 8× (when it exists at all).

## MRB reputation track

Mercenary Review Board reputation — the *industry credibility* score,
separate from per-house relationship rep tracked in
`playerReputation[]`. MRB rep is single-valued (player-wide), modulated
by:

- **Contract completions** — every COMPLETED contract adds rep,
  scaled by patron tier (T3 contract worth ~10× a T1).
- **Defaults / abandonments** — ABANDONED tanks rep; DEFAULTED is
  neutral (patron's fault, not yours, unless your behavior caused it).
- **Failure modes** — FAILED is small negative (you tried and lost is
  better than walking away).
- **War crimes** — special tag on certain outcomes. Defined as open
  question below.

Stored as a single `int playerMrbRep` on `CampaignState` (no need for
an SoA table — it's one number).

What MRB rep gates, per `economy.md`'s licensing tier table:

- Below threshold X → Unregistered (no licensing). Limited to T1 patrons.
- Threshold X → MRB Registered. T2 patrons unlocked.
- Higher thresholds → Faction Commission / Corporate Charter eligibility.

MRB rep also acts as a *floor* on which-tier patrons offer at all:
unknown mercs (low MRB) don't get Duke-level contracts no matter how
desperate the Duke is.

## Salvage rights — three-layer model

Per the design discussion: **(a) per-type baseline policy + (b)
negotiation knob at acceptance + (c) captain trait + fleet modifiers**.
This is the most interesting income knob in the game — the
MechWarrior Mercenaries appeal.

### Layer 1: per-type baseline

The `salvageBaseline` on each contract — what % of recoverable
wreckage the player is entitled to. Set per contract type at offer
generation:

| Type | Baseline | Notes |
| --- | --- | --- |
| Strike — Sabotage | 0–10% | Covert; you weren't there to take stuff |
| Strike — Raid | 50–80% | Taking stuff *is* the point |
| Strike — Decapitation | 30–50% | Target's gear + escort wrecks |
| Escort | 10% | Defender wrecks only |
| Planetary Assault | 60–100% | Phase-by-phase; final phase richest |
| Garrison (defense mission) | 25% | Only triggers on reactive defense |
| Cadre (incident) | 5% | Training accidents only |

Baseline is the *cap* — the player can negotiate downward, never
upward, at the contract offer.

### Layer 2: negotiation knob

At contract acceptance UI, the player picks `salvageNegotiated` in
the range `[0, salvageBaseline]`. Reducing salvage bumps cash via
`cashMultiplier`:

```
cashMultiplier(negotiated) = 100 + (salvageBaseline - negotiated) * 0.5
```

So negotiating from 60% baseline down to 0% salvage gives
`100 + 60 * 0.5 = 130` — a 30% cash bonus. The 0.5 curve is tunable;
the principle is *salvage is more valuable than cash* (because you can
pick high-value items), so trading it for cash should pay a premium.

This is the single most important player choice at contract
acceptance: "Am I broke this month, or am I building my arsenal?"

### Layer 3: captain trait + fleet modifiers

Multiplicative on top of the negotiated %:

- **Captain trait `SALVAGE_EXPERT`** (new) — +25% to recovered tonnage,
  +10% chance for a high-value-item roll. Pairs with the existing
  `Trait` enum.
- **Fleet salvage ships** — vanilla `Salvage Rig` hulls in the
  player's deployed fleet (or fleet at the source jump-point) — each
  adds +10% recovery rate (cap at +40% for 4 rigs). Detected via
  `FleetMemberAPI.getHullSpec().getHullId().equals("salvage_rig")` or
  similar at mission resolution.
- **Salvage Gantry hullmod** — half-effect modifier on any ship
  carrying it.
- **Future: tech-recovery marine trait** — unlocks blueprint salvage
  (otherwise blueprints are never in the recovery pool).

### Recoverable categories + the loot screen

What can actually drop, the post-battle picker UI, cargo-capacity
interaction, and fence-on-the-spot conversion live in
[`../loot/overview.md`](../loot/overview.md). The contract layer only
sets the *entitlement* (the three layers above); the loot feature
consumes it. The *mechanic* lives here; the *screen* is a separate
concern.

## Default / breach mechanics

Stationing contracts can default. Mission contracts effectively can't
— they're short and the payout is at the end.

Default triggers (for Stationing):

- **Patron falls** — patron house status → DEPOSED (lost a chain
  against them). Contract immediately DEFAULTED.
- **Patron promotes and ditches** — patron rank advances; new tier
  doesn't bother with old small-time contracts. Soft default with
  graceful exit if rep is high; hard default otherwise.
- **Patron-internal political flip** — a chain firing against the
  patron destabilizes them; they stop being able to pay.
- **Random monthly default roll** — small per-month chance scaled by
  patron's `housePower` (low-power patrons default more).

On default: the campaign tier spawns an extraction mission at the
garrisoned market — the player can fight in, recover their marines /
captain / cargo, and (often) extract grievance compensation by
*taking* what the patron can't pay. This is the loop's bridge into
the next phase.

The extraction mission is its own contract entry — type `EXTRACTION`
isn't in the five-type list because it's *system-generated*, not
patron-offered. The campaign tier creates it directly on default.

## Chain coupling

A contract advances its parent chain (if any) on completion:

```
onContractCompleted(contract):
  chain = chains[contract.chainId]
  if chain == null: return
  chain.progress += chainContributionFor(contract.type, outcome)
  if outcome.failed: chain.progress -= failurePenaltyFor(contract.type)

  if chain.progress >= chain.threshold:
    resolveChain(chain)
```

`chainContributionFor` is per-contract-type:
- Strike — small contribution (1-3% of threshold).
- Escort — medium (3-5%).
- Planetary Assault — large (15-30%).
- Garrison — passive — per-month tick contribution while ACTIVE.
- Cadre — minimal (1-2% on completion, primarily for the captain XP).

Defaults / abandonments *reverse* progress, on the principle that the
chain's narrative momentum depends on the player following through.

## Open questions

1. **Cadre incident frequency** — what's the right rate? Initial
   guess: 1 incident per 30 in-game days, scaling with patron tier.
   Open to tuning.
2. **War crimes definition** — which actions tank MRB rep beyond
   ordinary failure? Candidates: hitting confirmed civilian targets,
   killing surrendered defenders, accepting kidnap contracts on
   children, defecting mid-Planetary-Assault. Hard rule vs
   patron-dependent? Probably context-dependent — same act is fine
   for an Underworld patron, ruinous for an MRB-registered Corporate
   patron.
3. **Multi-contract simultaneity** — limit by available captains
   (one captain per contract), or also cap at N concurrent? Lean
   captain-only (lets player buy more captains to scale up; matches
   the BattleTech model).
4. **Garrison overrun vs withdraw mid-fight** — can the player
   abandon mid-defense to save the captain? Or is overrun
   all-or-nothing? Lean overrun = all-or-nothing — it makes Garrison
   genuinely risky and the choice to accept it weighty.
5. **Cadre training XP rate** — how much XP does a 2-month Cadre give
   the captain vs the same time spent on Strike missions? Should be
   *competitive* with mission XP (otherwise Cadre is just bad), but
   not strictly better (otherwise mission gameplay is bad).
6. **MRB scoring curve** — exact formula. Lean logarithmic so early
   contracts feel impactful and late-game grinding has diminishing
   returns. Specific curve a balance pass.
7. **Salvage when fleet can't carry it** — 75% fence-on-spot cash
   confirmed above, but: does this affect the "salvage > cash" axiom
   if it's auto-converted? Player choice: take less salvage so they
   can carry it all, or take more and accept the discount?
8. **Salvage Rig detection** — is the rig in the *deployed* fleet,
   or any fleet in the system? Lean deployed (more interesting fleet
   composition choice), but the system-wide version is more forgiving
   for skeleton.
9. **Captain availability on contract offer** — does the contract
   offer specify which captain it wants, or does the player assign
   any captain at acceptance? Lean player-assigns (more agency).

## Followup docs this gates

- [`../loot/overview.md`](../loot/overview.md) — post-battle salvage UI:
  item discovery, cargo capacity interaction, trait + fleet modifier
  surfacing, item value display. Needed before the loot loop is playable.
- [`../infrastructure/overview.md`](../infrastructure/overview.md) —
  buildings that modulate garrison default rates and per-house power.
  Defensive infra reduces Garrison default rolls; intel infra surfaces
  hidden pretenders sooner.
- [`../t3-endgame/overview.md`](../t3-endgame/overview.md) — the Tier-4
  contracts. Where this doc's "future" bracket actually lives.
- [`../narrative/overview.md`](../narrative/overview.md) — the patron
  tapestry: comms-officer narrator, archetype content axis, procedural
  fatigue discipline. Extracted from this doc.

## Narrative — the patron tapestry

The narrative side of the contract layer — comms-officer narrator,
the patron-archetype content axis (the `byte archetype` carried on the
contract's patron house), and the procedural-fatigue discipline — now
lives in [`../narrative/overview.md`](../narrative/overview.md). The
contract layer sets the commercial terms; what the player *reads* about
the patron behind those terms is the narrative feature's concern.

## Implementation hooks

When this doc moves into code:

1. `ContractType` enum (STRIKE / ESCORT / PLANETARY_ASSAULT /
   GARRISON / CADRE), byte-backed for SoA.
2. `ContractState` enum (ACTIVE / IN_PROGRESS / ... ), byte-backed.
3. `contracts[]` SoA columns added to `CampaignState`, plus
   `LongIntMap contractIndexById` for O(1) lookup.
4. Add `CampaignTable.CONTRACTS` enum value; revisit each existing
   System's reads/writes to see whether it now also touches contracts.
5. New `ContractLifecycleSystem` — the only System that writes
   contracts directly. Other systems read.
6. Mission resolver hook → on victory, look up the contract by the
   mission's parent contract id (new field on `Mission`), advance
   phase / mark COMPLETED / advance chain.
7. New captain `Trait.GARRISONED` status — the captain isn't
   deployable while parked.
