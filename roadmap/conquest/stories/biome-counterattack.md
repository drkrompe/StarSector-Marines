# Biome counterattack — the staged "bulge"

> A signposted defender counteroffensive that inverts progressive
> reinforcement: instead of trickle re-manning the contested front,
> the defender commits a burst of reinforcement tickets to mass
> squads and retake a *conceded* biome slice — pushing the frontline
> back toward the beachhead. A staged set piece (muster → telegraph →
> assault → resolve) with its own up-front budget commitment, conveyed
> to the player so they can dig in, fall back, or counter-counter.

## Why

[`progressive-reinforcement.md`](progressive-reinforcement.md) is
reactive and defensive: the defender holds or restores a line, and
the front only ever moves one direction — toward the defender. Once
the player takes a slice, it stays taken. There's no moment where
hard-won ground is threatened, no swing.

A counterattack supplies that swing. The defender *institution* fights
back (world-reactive, not expressive): it gambles a chunk of its
reinforcement reserve on reclaiming territory, the player has to
re-fight for it, and the budget burn is a visible, dramatic
expression of the reinforcement economy rather than a slow drain.
Done right it's the tension/relief beat that makes a siege feel alive
— and a failed bulge that empties the defender's reserves becomes a
natural anti-snowball lever working in the player's favor for the
rest of the push.

## Design

### What makes it a "bulge" (vs. progressive reinforcement)

Progressive reinforcement **spreads** squads thin via round-robin to
hold the contested front. The bulge **masses** them at one point to
push into territory already lost. It is the deliberate *exception* to
the frontline eligibility rule: the one case where the defender
dispatches into a **conceded** slice (no current defender presence),
overriding the filter by Commander decision.

It sits at the **Commander tier** — a strategic objective ("mass and
retake the CITY district") above the per-node round-robin, the same
tier the mission-type designs already call for.

### Trigger conditions

A bulge is rare and conditional — a set piece, not a background loop.
It musters only when all of:

- **Surplus budget.** The defender's `REINFORCEMENT` reserve is above
  a counterattack threshold — they can afford the gamble.
- **Stable front.** Progressive reinforcement is in overflow (every
  contested-front recapture target is already fulfilled), so squads
  aren't urgently needed to plug holes. The overflow budget that
  would have gone to patrol is pooled into the bulge instead.
- **A reclaimable target.** A conceded slice exists adjacent-behind
  the front — the rear-most slice the marines took. That's the bulge
  objective.
- **Not already running.** At most one active bulge; a cooldown
  spaces them out so each lands as an event.

### Ticket-spend burst

The bulge **reserves a lump of the reinforcement budget up front** —
N tickets earmarked at muster, distinct from the steady per-dispatch
debit. It is all-or-nothing: if the defender can't afford the full
burst, no bulge. The commitment is the point — a real bet of reserves
that pays off (reclaimed slice) or is lost (wiped wave, depleted
reserve).

The earmarked tickets fund a concentrated wave of squads, all
targeting nodes in the **one** conceded slice — massed, not
round-robin spread.

### Staging & telegraph (the set piece)

Phases: **muster → telegraph → assault → resolve.**

- **Telegraph.** Before the wave arrives, the player gets a diegetic
  warning — the comms officer reports defender command is massing for
  a counterattack on `<district>` (player-POV, no cutaway; see the
  comms-officer-as-narrator model). A signpost on the threatened slice
  gives the player a window to dig in, fall back, or pre-empt. The
  build-up should be *felt* (incoming chatter, then the wave), not a
  pop-up.
- **Assault.** Squads stage at delivery zones in still-held defender
  territory (the two-coordinate split), then advance as a coordinated
  push into the conceded slice. Commander objective: re-establish
  presence and retake the slice's compound + nodes.

### Resolve & frontline shift

- **Success.** Defenders re-establish presence in the slice →
  `progressive-reinforcement`'s frontline filter re-includes it → the
  contested band shifts back toward the marines. The player has lost
  ground and must re-fight for it. No special bookkeeping — the shift
  is emergent from presence returning.
- **Failure.** The committed wave is wiped → the earmarked tickets are
  spent for nothing → the front holds. The depleted reserve makes the
  rest of the defender's push weaker.
- Either way, the comms officer delivers an after-action read.

### Built on progressive reinforcement

This story is **blocked on** progressive reinforcement landing. It
reuses, rather than rebuilds:

- `RecaptureTargetRegistry` — knows lost nodes per slice; the bulge
  reads conceded slices and their nodes as objectives.
- Two-coordinate split — stage in held territory, advance into the
  conceded slice.
- Frontline filter — defines conceded slices (the bulge's targets)
  and re-includes a slice on success.
- Commander tier — owns the muster decision and the massed objective.

## Implementation slices (sketch)

### Slice 1: Commander counterattack decision

Detect bulge conditions (surplus reserve + overflow/stable front +
a conceded slice behind the front). Reserve the ticket burst
all-or-nothing. Pick the target slice (rear-most conceded).

### Slice 2: Massed dispatch

Spawn the concentrated wave against one conceded slice's nodes under a
single Commander objective ("retake slice"). Squads converge rather
than round-robin spread. Delivery via the existing means, still gated
by compound supply.

### Slice 3: Telegraph + phase state machine

`muster → telegraph → assault → resolve` lifecycle. Comms-officer
warning at telegraph, threatened-slice signpost, after-action read at
resolve. Tune lead time.

### Slice 4: Resolve + frontline integration

Success path re-establishes defender presence (frontline filter
re-includes the slice); failure path leaves the reserve burned.
Confirm the contested band shifts as a natural consequence.

### Slice 5: Tune the swing

Budget cost, rarity/cooldown, telegraph timing, wave size. Playtest
the loss-then-reclaim rhythm — the bulge must threaten without making
the map feel like it refuses to stay taken.

## Open questions

- **Budget cost.** How expensive is a bulge relative to steady
  reinforcement, and can more than one fire per battle? (Set-piece →
  expensive; probably ≤1–2 per battle.)
- **Player-triggered bulges.** Should over-extending — pushing too
  fast and leaving the rear thin — *invite* a counterattack? That
  makes the bulge world-reactive to player aggression rather than a
  pure budget timer, which fits the design philosophy.
- **Telegraph lead time.** Long enough to react, short enough to feel
  threatening.
- **Emergent vs. scripted.** Always-available emergent behavior, or
  reserved for high-stakes patron missions that script a named
  counterattack? (Lean emergent + institutional; high-stakes missions
  can still author one.)
- **Aftermath persistence.** Does a failed bulge measurably ease the
  rest of the push (spent reserves), or reset cleanly?

## Cross-refs

- [`progressive-reinforcement.md`](progressive-reinforcement.md) —
  parent story; supplies the frontline filter, recapture registry,
  two-coordinate split, and Commander tier this builds on. **Blocks
  this story.**
- [`../central-keep.md`](../central-keep.md) — compound-as-supply
  gates still constrain bulge delivery means.
- [`compound-spread.md`](compound-spread.md) — the tiered biome bands
  that define which slices can be conceded and reclaimed.
