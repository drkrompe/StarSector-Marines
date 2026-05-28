# Moral compass — the silent track

> Design discussion, not a spec. Continues from [`themes.md`](themes.md).
> The discipline here (never surface it) is load-bearing — read the
> "Design discipline" section before proposing any UI for this.

A silent moral-compass track records the *character* of the player's
choices over time. The track is **never visible to the player as a
number**. It surfaces only through diegetic channels, and only when
those surfaces are narratively earned.

## Why it exists

Every other player-progression track we've designed is mechanical and
optimizable — credits, MRB rep, stake share, contract count. The moral
compass is the *one* track that resists optimization. The player can't
grind it; they can only *be themselves* across hundreds of choices, and
learn what they've been only when the world reflects it back. This is the
structural prerequisite for the Kingdom-of-Heaven-style kingmaker capstone
speech (see [`t3-endgame/overview.md`](t3-endgame/overview.md)).

## Shape

A small set of integer dimensions, not one number — probably 3–5 axes:
ruthlessness / honor, faction loyalty, civilian protection, ideological
alignment, mercenary professionalism. Encoded as `int` columns on
`CampaignState` (cheap, persistent).

## How it surfaces — diegetic only

Never as a UI number. No "Honor: 47/100" display, ever. The track exists;
the player never sees it. It leaks through:

- **Captain trait drift** — a captain may pick up `CYNICAL` after enough
  morally-questionable contracts.
- **Soldier overheard comments** in roster review ("Sgt. Hale said the
  Jangala job didn't sit right with her").
- **Captain transfers** — a captain whose values diverge enough from the
  player's pattern may put in for transfer.
- **NPC dialog gates** — some patrons refuse contracts with players whose
  compass reads "ruthless" beyond a threshold; others *prefer* them.
- **Briefing flavor shifts** — patrons of an alignment write differently to
  a player they perceive as similar (see
  [`narrative/overview.md`](narrative/overview.md)).
- **The kingmaker capstone** — the one moment the compass is *explicitly*
  surfaced: the deposed ruler's testament names what the player chose to
  become. Reserved for narrative apex.

## What feeds the compass

- Contract acceptance/refusal (refusing a fallen-noble for ethics ticks
  honor; accepting a SUSPICIOUS patron's grey contracts ticks ruthlessness
  — see [`narrative/overview.md`](narrative/overview.md) archetypes).
- Mission outcomes (collateral damage, civilians killed, surrendered
  defenders' treatment).
- [Black-swan event](events.md) responses — the densest compass-touching
  moments.
- Salvage choices (taking blueprints/weapons vs leaving them for the
  population — see [`loot/overview.md`](loot/overview.md)).
- Chain participation (running an ELEVATE_HEIR vs a SABOTAGE_PROMOTION
  shifts different axes).

## Design discipline — withholding

The discipline is *withholding*. The future temptation will be to add
"+honor" notifications on actions, or a dashboard for the player to
optimize against. **Resist.** The compass is a system for the *world* to
read, not the player. Optimization-resistance is the entire point — it's
the silent "show, don't tell" track until the big reveals. See
[[feedback-world-reactive-over-expressive]] (the compass *is* the world
reacting to player character) and
[[feedback-patron-narrative-discoverable]] (it's one of the silent threads
the player learns to read).

## Implementation surfaces

- `CampaignState`: 3–5 `int` columns for the axes.
- New `MoralCompassSystem` (a `CampaignSystem` impl) — reads contract
  outcomes, event responses, mission results; writes the axes. Runs after
  `MissionResolutionSystem`.
- A `CaptainTraitDriftSystem` — reads compass + captain time-in-service,
  occasionally promotes a captain to a new trait (CYNICAL, IDEALIST, …).
- NPC dialog gates — content-side, read the compass via a getter.
- Capstone scene infrastructure — lives in
  [`t3-endgame/overview.md`](t3-endgame/overview.md) when written.

## Related

- [black-swan events](events.md) — highest-density compass-touching content.
- [`t3-endgame/overview.md`](t3-endgame/overview.md) — the kingmaker
  capstone reveal.
- [`narrative/overview.md`](narrative/overview.md) — patron dialog gates +
  briefing flavor the compass feeds.
