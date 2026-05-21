# Campaign economy: income, sinks, and the anti-snowball

> Design discussion, not a spec. Continues from
> [`themes.md`](themes.md). Specific numbers and balance values are
> deliberately omitted — those come from playtest. This doc captures
> shape and intent.

## Why economy first

The campaign tier's other systems — house politics, contract types,
chain progression — are *operations on the money loop*. If the money
loop has the right shape, designing the rest is mechanical. If it
doesn't, every other system bends to compensate.

The shape we're after is **hard-fail early, steady-grind mid,
intentional-investment late**. Vanilla Starsector snowballs cash hard
once past early game; the merc-company subgame needs equivalent
tension *throughout*, not just opening. This doc is how we get it.

## The campaign arc

Three phases, each defined by what's pressuring the player:

| Phase | Duration | Pressure | Player attention |
| --- | --- | --- | --- |
| Survival | Weeks 1-3 | Make next paycheck | Take any contract; two marines too many and salary's missing |
| Accumulation | Weeks 4-12 | Stabilize income | Garrisons stack; first infrastructure investments pay back |
| Scale challenge | Months 4+ | Inefficiency taxes growth | Intentional expansion vs. company rot |

Each phase changes the *kind* of decision the player optimizes for.
The rest of this doc is how the economy delivers each pressure on
schedule.

## Income sources

### Direct contracts

The current
[`MissionGenerator`](../../src/main/java/com/dillon/starsectormarines/ops/MissionGenerator.java) /
[`MissionResolver`](../../src/main/java/com/dillon/starsectormarines/ops/MissionResolver.java)
flow — one-shot ops that render through the battle simulation.
Payout already scales with planet size × risk × mission type. These
are the *windfall* income — big and lumpy, with high effort per
credit.

The existing payout math needs rebaselining once sinks are wired:
current payouts assume zero ongoing costs. Likely the floor rises
30-50% to keep MEDIUM-risk missions winnable after deployment cost
plus insurance. Numbers from playtest.

### Garrison contracts

Steady recurring income, tight margin. The "scraping by between real
jobs" rate that BattleTech players know — a multi-week or multi-month
contract to keep N marines on a planet, paid monthly.

Baseline rate is `salary × 1.10` per garrisoned marine per month (10%
over vanilla salary). Risk-scales upward on hot planets: a garrison
on a low-stability border world might pay `salary × 1.50` but lose
marines to skirmishes at a higher rate.

A garrison consumes:

- N marines from cargo (`Commodities.MARINES`).
- *Optionally* a captain to lead. Captain-led garrisons have lower
  default risk, lower marine loss rate, and accrue per-house
  reputation faster. The captain is *unavailable* for direct
  contracts while garrisoned — the strategic dial that makes the
  decision interesting.

Per-event simulated background activity (intel-feed pings, not
monthly bulk reports):

- **Skirmishes** — random per-event marine losses ("Sindria
  garrison: 2 KIA in unrest, contract intact").
- **Payment defaults** — random per-event "employer is delayed"
  events that escalate into extraction missions (see
  [Payment-default extraction](#payment-default-extraction-mission)).

### Side income (deferred)

Late-game: planetary infrastructure may generate small passive income
(Training Facility could rent out as a regional academy,
Administrative Office could broker contracts for cuts). Out of scope
for the first economy pass.

## Sink ledger

### Vanilla baseline

- **Marine salary**: $20/marine/month. Unchanged from vanilla. This
  is what the `salary × 1.10` garrison income refers to.
- **Vanilla fleet supply burn**: untouched. Marines under our
  management consume supplies as vanilla already accounts for. We do
  *not* layer per-marine-per-day burn on top.

### Per-mission

- **Deployment cost** — fuel + supplies pulled from the fleet on
  mission accept. Scaled by drop count and distance from current
  system to target system. Forces mission location to matter and
  sets a minimum payout threshold below which a mission is a net
  loss even on victory.
- **Insurance payout on loss** — per-marine-lost, paid out of
  credits, routed through the merc registry. In-fiction: a
  bereaved-family fund. Mechanically: a real cost on carelessness
  that scales with mistakes, not just contract difficulty.

### Ongoing

- **Licensing tier fees** — see [Licensing](#licensing-the-identity-dial).
- **Scale inefficiency** — see
  [Scale inefficiency](#scale-inefficiency-the-anti-snowball).

### Investment

- **Per-planet infrastructure** — Encampment, Training Facility,
  Administrative Office. One-time builds with monthly maintenance,
  reduce per-planet operational friction.
- **Per-region infrastructure** — Regional HQ. Expensive, amortizes
  across all ops in a faction or region.

## Scale inefficiency (the anti-snowball)

Modeled on Songs of Syx's resource spoilage: larger operations bleed
more per month. Without active management, the cost of being big
catches up with the revenue of being big. This is what keeps the
late-game from snowballing into boredom.

### Bleed dimensions

- **Cash overhead** — admin/broker fees grow as a % of monthly
  revenue. A company pulling $50k/month loses a flat slice; a
  company pulling $500k/month loses a bigger flat slice *plus* a
  higher %.
- **Supply waste** — logistics across distributed garrisons lose a
  % of supplies in transit. More garrisons spread further means
  more waste.
- **Garrison default rate** — coordination failures rise as
  garrisons multiply. A company managing 3 garrisons coordinates
  each well; a company managing 15 has drift.
- **Reputation gain decay** — marginal contract matters less to a
  company already known. Late-game contracts give less per-house
  and per-MRB reputation than early-game ones, unless mitigation
  kicks in.

### Mitigation track

This is where meta-progression gets *teeth* — not unlocking content,
but staving off entropy:

- **Player traits** — persistent across the company. Quartermaster
  trait reduces supply waste; Administrator reduces cash overhead.
  Earned via story milestones (first faction-flip, first
  T3 chain completion, etc.).
- **Captain traits** — per-officer. `Trait.LOGISTICS_CHIEF` in
  [`Trait.java`](../../src/main/java/com/dillon/starsectormarines/marine/Trait.java)
  is already defined but currently unwired — this is where it earns
  its keep. Reduces cash overhead in operations the captain runs,
  reduces garrison default rate when stationed. Strong reason to
  assign a logistics-trait captain as a permanent garrison commander
  rather than a strike-team lead.
- **Per-planet infrastructure** — Encampment (reduces marine loss
  rate), Training Facility (reduces loss rate + accelerates XP),
  Administrative Office (reduces per-planet cash overhead). Each is
  a monthly maintenance sink and a one-time build cost.
- **Per-region infrastructure** — Regional HQ (one per faction or
  geographic region). Expensive, amortizes overhead reduction across
  all ops in that region. Late-game investment that justifies
  consolidating operations geographically.

### Curve shape

Open question: linear, quadratic, or step-function. Lean quadratic
or step so the *feel* of "I just got too big" is legible to the
player rather than incremental and invisible.

## Licensing (the identity dial)

Layered on contract types. The player's licensing posture determines
which contracts even appear, and some postures are *mutually
exclusive* — the player can't be a registered legitimate operator
and a black-market enforcer for the same area at the same time.

| Tier | Cost | Unlocks | Constraints |
| --- | --- | --- | --- |
| Unregistered | $0 | Any contract, pirate-tier pay | No insurance; defaults frequent; corporate / faction-flagged work locked |
| MRB Registered | Monthly fee | Independent + standard house-vs-house work | Required for insurance kick-in; bars black-market patronage above tier 1 |
| Faction Commission (per faction) | Monthly + earned rep | High-pay faction-flagged contracts | Exclusivity: no work against commissioning faction while commissioned |
| Corporate Charter | Monthly + chartering corp cost | Hostile-takeover / corporate-flavored contracts (Tri-Tachyon flavor) | Stacks with MRB; bars patronage above tier 1 |
| Black-Market Patronage | Monthly protection to pirate / Path warlords | Underworld and sectarian contracts | Mutually exclusive with MRB above tier 1 |

The mutual exclusivity is what makes licensing more than a fee
list — the player commits to an *identity* (registered legitimate
operator vs. underworld muscle) and the world responds with which
contracts appear, which clients open up, and which close forever.

## Bankruptcy (hard failure)

The player **can** go bankrupt. The 3-4 week survival window is the
campaign's crucible — the part where the player learns the system
has teeth before they ever feel safe. Soft floors and "you can't
lose more than X" guards are off the design table.

### Trigger conditions

- Credits below zero *and* unable to make the next monthly payroll
  cycle.
- Cascading: missed payroll → captains lose loyalty → garrison
  contracts default → breach penalties → less income → deeper hole.

### Consequences

- **Captains desert** — named officers leave the roster. Default
  semantics: gone permanently.
- **Active garrison contracts auto-cancel** — with breach penalties
  recorded by the MRB. Future contracts at higher fees or lower
  available tier.
- **Direct contracts in flight fail** — marines stranded, missions
  marked breached, reputation hit across affected houses.
- **At credits floor**: game-over state for the merc-ops sub-game.
  Player can continue vanilla Starsector but the merc subgame is
  retired *unless* they grind their way back through a pity loop.

### Pity loop (open)

Vanilla cargo-marine grunt work — escorts, simple raids — at the
*Unregistered* tier with minimal pay. Earns way back over weeks of
grunt work, not handed out. Lets the player recover without
trivializing failure.

This shape is non-trivial to design well; deferred to a focused pass
once the rest of economy is shipped and we see how often bankruptcy
actually fires in practice.

## Payment-default extraction mission

The most generative bridge mechanic in the economy: when a
garrison's employer defaults on payment (per-event roll), the
meta-sim spawns a direct contract that arrives in the player's
intel feed. *Go collect.*

This is how the meta-sim **talks to the player** without requiring a
planet visit. It also gives a natural use for **dispatching a
captain from the fleet** — when the player is too far to handle it
themselves, they can send a captain (slower resolution, captain
unavailable for direct contracts during the trip, no fleet
displacement).

Resolution outcomes:

- **Successful extraction** — payment recovered, captain returns,
  employer reputation tanked but recoverable.
- **Failed extraction** — payment lost, captain may be injured or
  killed, MRB records a contract breach against the *employer*
  (small insurance payout to player).
- **Player ignores** — payment lost; the garrison may collapse;
  future contracts in that system reduced.

This mechanic is the bridge between the background sim and the
player-facing mission queue. Without it, the simulation is silent;
with it, the player feels the world acting on them.

## Open questions

1. **Exact balance numbers** — defer to a focused pass after the
   system shape is implemented. This doc is intentionally
   number-free except for `salary × 1.10` and the vanilla
   $20/marine/month.
2. **Scale inefficiency curve** — linear, quadratic, or
   step-function? Lean quadratic or step for legibility.
3. **Licensing exclusivity UX** — hard-block contract list, or show
   the contract greyed out with the conflict reason? Greyed-with-reason
   is more informative.
4. **Scale inefficiency: aggregate or per-region?** Aggregate is
   simpler; per-region rewards focused operations. Probably aggregate
   with per-region *mitigation* (Regional HQ helps only that region).
5. **Captain desertion on bankruptcy** — lose them forever (clean,
   harsh) or "retired" with a small chance to re-recruit at high
   cost (more interesting, more state)? Default to forever; revisit
   if playtest shows it's too punishing.
6. **Pity loop semantics** — exact mechanics of grinding back from
   game-over. Likely its own design pass.
7. **Vanilla supply-burn interaction** — confirm vanilla already
   abstracts marine consumption so our per-drop deployment cost
   doesn't double-charge. Worth a code spike before implementation.
8. **Per-planet infrastructure stacking** — can a planet host
   multiple buildings? Probably yes; each takes a build slot scaled
   to planet size or our garrison footprint there.
9. **Garrison default rate scaling** — does the per-garrison default
   probability scale with company size (the bleed dimension) or only
   with planet risk? Probably both, multiplicatively.

## Followup docs

- [`mechanics.md`](mechanics.md) — data model the economy operates
  on (houses, stakes, reputation, chains, mission → stake-transfer
  resolution).
- `contracts.md` — full contract-type specifications (Strike,
  Garrison, Cadre, Escort, Planetary Assault) with their terms,
  salvage rights, time commitments, and how each maps to direct
  contracts vs. background-sim garrison contracts.
- `infrastructure.md` — per-planet and per-region buildings: build
  cost, monthly maintenance, exact mitigation effects, stacking
  rules.
- `t3-endgame.md` — faction flips, market ownership change, vanilla
  rep consequences, the "marginal colony as reward" mechanic. Where
  the economy and mechanics docs cross into vanilla state.
