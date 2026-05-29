# Ambition & disposition — making houses scheme like people, not optimizers

> The behavioral layer of the [living world](overview.md). Design
> discussion, not a spec. Where the [`../mechanics.md`](../mechanics.md)
> `houseAmbition` column stops being a placeholder and starts driving
> *who covets what, and why* — the difference between a sector that
> schemes and one that just runs `number++`.

## The failure mode we're designing against

The naïve autonomous sim is **a universe of psychopaths climbing one
ladder.** Every house optimizes the same scalar (stakes → rank), so the
player models the whole simulation in five minutes and it goes
transparent. This is the seam that holds back MW5 and most sandbox
campaign layers: memoryless actors, all wanting the same thing, reading
as a spreadsheet in a costume.

**The seam-breaker is friction, and friction requires disagreement.** Two
houses that both want "more" only disagree about who gets it. Two houses
with different *values* disagree about what's worth doing at all. That
disagreement is drama — and it's what CK3 manufactures from a small
vocabulary of trait words. We steal the technique.

## Three composable layers (we already have two)

A house's behavior is the product of three axes, not one. Two exist; one
is new.

| Layer | What it is | Status |
| --- | --- | --- |
| **Flavor** | The *value system* — what this culture thinks is worth wanting. Corporate = growth (the legitimate psychopaths); Feudal = honor / legitimacy / bloodline; Underworld = territory / respect; Sectarian = purity / conversion. | Exists ([`../themes.md`](../themes.md)); today just a nameset |
| **Archetype** | The house's *situation*. FALLEN_NOBLE wants restoration; ESTABLISHED wants to *preserve* (a satisfied actor); SUSPICIOUS is the pure opportunist; TIME_RUSHED is cornered. | Exists ([`../narrative/overview.md`](../narrative/overview.md)) as **narrative-only** — promote to a behavioral driver |
| **Traits** | CK3-style disposition tags that bias *choices*. A house carries 1–2. The new layer. | New — extends the narrative doc's "modifier traits (SPITEFUL/RESIGNED/DELUSIONAL)" from flavor-text to behavior |

One byte does double duty: the same trait that shapes how the comms
officer *describes* a house also shapes how it *acts*. Tapestry and sim,
unified — no parallel personality system.

## The trait vocabulary

Small (~10), paired as opposites (the friction lives in the pairing),
grouped by the decision each biases. A house draws 1–2 at seed, weighted
by flavor + archetype.

| Axis | Trait ↔ opposite | Biases |
| --- | --- | --- |
| **Initiative** | `AMBITIOUS` ↔ `CONTENT` | Whether the house initiates at all. CONTENT = satisfied, static backdrop. |
| **Targeting** | `VENGEFUL` ↔ `PRAGMATIC` | VENGEFUL locks a specific rival (a remembered slight) and pursues past profit; PRAGMATIC re-picks the best opportunity each evaluation. **This is the grudge↔opportunism spectrum.** |
| **Loyalty** | `STALWART` ↔ `TREACHEROUS` | Behavior toward the liege (the system's Tier-2 house). STALWART defends the liege's interests; TREACHEROUS betrays for advantage. **This is the vertical axis.** |
| **Risk** | `BRAVE` ↔ `CRAVEN` | Willingness to fight when threatened vs. fold and go DORMANT. |
| **Norms** | `HONORABLE` ↔ `ZEALOUS` | HONORABLE keeps deals and respects rivals' standing; ZEALOUS subordinates everything to the flavor's value (a Sectarian zealot, a Corporate growth-absolutist). Drives **value-based affinity**. |

Two things the earlier brainstorm asked for fall straight out:

- **Grudge vs opportunism** = `VENGEFUL` vs `PRAGMATIC`. A Vengeful house's
  grudge is *born from a Chronicle event* (it lost a stake, an heir, or
  the player burned it) — so the sim is path-dependent and the player can
  read the cause. A Pragmatic house is the cold opportunist — a legitimate
  archetype, as long as it isn't *everyone*.
- **The satisfied majority** = `CONTENT` + `CRAVEN` + ESTABLISHED. The
  single biggest anti-psychopath lever: **~70–80% of houses should not be
  climbing.** Static, defensive, just living. The ambitious few become
  legible *figures* against stable ground; a sector where everyone schemes
  is noise. (Also fixes consolidation-runaway and tempo.)

## Two axes of conflict

One-dimensionality dies when there's more than one thing to fight over.

### Horizontal — stake competition (the substrate)

House-vs-house on a shared industry, driven by the drift/chain loops in
[overview.md](overview.md). Targeting traits decide *who* a house moves
against.

### Vertical — loyalty & rebellion (the CK3 borrow)

The rank ladder is currently just visibility/contract-gating. Traits turn
it into a **loyalty structure**, activating the Tier-2 liege house that
today does nothing behaviorally:

> A `STALWART` Baron backs their Count's interests — putting them at odds
> with the `AMBITIOUS` upstart on the same market scheming to climb over
> both of them.

This hands the player a genuinely new lever. Flipping a market is no
longer pure stake-grinding — it's deciding whether to **turn the loyal
vassal** (hard; needs leverage) or **back the restless upstart** (the
Ambitious one was going to rebel anyway — you just armed them). A CK3
faction/rebellion loop, built entirely from already-seeded structure (the
system liege, the ladder, `relationships[]`). The most interesting houses
live where the two axes pull against each other.

## Friction is the content engine

Traits mean nothing in isolation — they generate behavior by rubbing
against structure and neighbors. The combinatorics of a small set across
the hierarchy is what scales to a sector without hand-authoring:

- A `STALWART` vassal under a weak liege defends them anyway — irrational,
  loyal, memorable.
- Two `AMBITIOUS` houses under one Count fight *each other* first — the
  player plays kingmaker.
- A `ZEALOUS` Sectarian beside a `PRAGMATIC` Corporate: mutual contempt on
  *principle*, spawning conflict pure stake-economics never would. This
  gives `relationships[]` **texture** — not just "who's strong," but "who
  can't stand whom, and why."

### Mistakes are content

Houses act on **rank-gated visibility** (already seeded): a Tier-1 Baron
can't see past its market, so it makes locally-rational, globally-dumb
moves — overreaches, fails, and the failure is a story (and an opening for
the player). Perfect optimizers are boring and exploitable; fallible
actors generate drama. Bounded rationality is a feature.

## Feeling out a patron — the discovery surface

> The open design question from the 2026-05-29 session: should traits be
> *surfaced as symbols*, and how does that square with the
> "[never show the number](../moral-compass.md)" discipline?

The tension is real: badge-collecting is satisfying and the player needs
*some* handle to make decisions — but a grid of trait icons shown on first
contact is just the spreadsheet again, and kills the discoverable
discipline ([[feedback-patron-narrative-discoverable]]).

**The reconciliation: symbols are *earned knowledge*, not displayed
stats.** Fog-of-war on personality. You walk into a market not knowing
these houses; the picture fills in through participation. A trait glyph
appears on a patron's dossier *once you've witnessed it* — you don't see
"Vengeful" until you've watched the Kessar chase one rival across two
contracts; then the mark unlocks ("you've come to know them as
grudge-keepers"). Qualitative marks, never meters with numbers.

This splits cleanly into **two badge surfaces**, which is what makes it
work:

1. **Patron knowledge** — what you've learned about *them* (their traits,
   revealed as witnessed). The "feeling out your patron" discovery. Driven
   by the comms officer as narrator: *"first read: they seem the careful
   type"* → later → *"confirmed — they don't forget a slight."* Confidence
   grows with contracts run.
2. **Relationship standing** — what you *are* to them: `Trusted`,
   `Preferred Contractor`, `Burned bridge`. This is **"having worked for
   X" made tangible** — the badge the player wears, riding the existing
   `playerReputation[]` + captain preferred-contractor design.

Texture that falls out:

- **Some traits read openly, some hide.** A `ZEALOUS` Sectarian wears it;
  a `TREACHEROUS` house you find out the hard way. The SUSPICIOUS
  archetype's entire point is *you can't read them upfront* — first
  contact deliberately shows almost nothing.
- **A reason to scout.** Low-commitment contracts (and the Cadre type,
  high political access) become a way to *feel out* a patron before a big
  commitment. The `SCOUT` captain trait and intel
  [`../infrastructure/`](../infrastructure/overview.md) speed the reveal.
- **Discipline guard:** marks represent *earned knowledge and
  relationship*, not raw stats. No completionist trait-grid to optimize
  against; the satisfaction is "I know these people now," not "I collected
  the set."

### Information has to pay rent

> The sharpest open critique (2026-05-29 session): discovery still risks
> leaning into *"what do I actually do with this now?"* beyond
> roleplaying. Knowing the Kessar are Vengeful is only texture unless it
> changes a decision.

Discipline: **a trait earns a place on the dossier only if it gates a
player decision.** Pure-roleplay marks get cut — consistent with
[[feedback-world-reactive-over-expressive]] (the player deprioritizes
expressive-only layers). Candidate consequences, each of which requires
the *named system* to actually key off traits:

- **Risk-pricing the contract** — a `TREACHEROUS` patron defaults / turns
  on you more often, so knowing it shifts your *payment-structure* choice
  (demand lump-sum upfront; avoid stationing exposure). Needs the
  default/breach mechanics
  ([`../contracts/overview.md`](../contracts/overview.md) §Default) to
  scale by trait.
- **Where to invest a chain** — backing a `CRAVEN` patron's war wastes
  captain-months on a house that folds; an `AMBITIOUS` one follows
  through. Traits tell you *who's worth a multi-mission commitment.*
- **Negotiation leverage** — a `VENGEFUL` patron mid-grudge needs you and
  overpays; a `PRAGMATIC` one walks if a better deal appears. Shifts the
  salvage/cash knob.
- **Anticipating the Chronicle** — knowing the upstart is `AMBITIOUS`
  lets you *predict* the move on the Count and position to profit. The
  "reading the wire is a skill" payoff: traits make the world predictable
  enough to *plan against* — the real answer to "what do I do with this."
- **Standing → terms** — `Trusted` unlocks chain-compression / better
  terms (already designed, [`../themes.md`](../themes.md) §captains).

The honest dependency: most of these consequences don't exist yet, so
until the contract / negotiation / chain systems key off traits,
discovery *is* roleplay-only. The sequencing rule that follows: **don't
surface a trait before the system it feeds exists.** Traits light up on
the dossier as their downstream consequence ships — not before.

### Data-model note

"What the player knows about house X" is per-house knowledge — its natural
home is the `playerReputation[]` row (already sparse, already created on
first contact): add a `repDiscoveredTraits` bitmask (+ optional
`repFamiliarity`). The house's *actual* traits live in a new `houseTraits`
bitmask byte on `houses[]`. The dossier renders the intersection: actual
traits AND-masked by discovered.

## The open fork

**Do we make the vertical loyalty axis first-class now, or ship ambition
horizontal-only first?**

- *Horizontal-first* — traits drive target selection on stake competition;
  liege houses stay inert. Smaller, proves the trait engine, fits the
  A–E [slice spine](overview.md) cleanly (lands around Slice C/D).
- *Vertical-now* — wire loyalty/rebellion from the start; richer, the most
  direct kill for one-dimensionality, but a bigger surface (activates
  liege houses, adds a rebellion chain archetype, needs loyalty/affinity
  wiring). Cheaper to design in now than to retrofit.

Leaning toward proving the trait engine horizontally first, then layering
the vertical axis as its own slice once traits demonstrably read as
character. Decision pending.

## Open questions

1. **Trait assignment** — fully deterministic from flavor+archetype+seed,
   or a roll with flavor-weighted odds? Determinism keeps genesis
   reproducible; the sim diverges after.
2. **Trait mutability** — do traits drift (a betrayed `STALWART` turns
   `TREACHEROUS`; a `VENGEFUL` house whose rival is dead cools to
   `PRAGMATIC`)? Path-dependent traits are more alive but more state.
3. **Generational grudges** — does a Feudal house's vendetta outlive the
   individuals (persists across the abstracted "house"), while an
   Underworld one dies with the Boss? Flavor-dependent grudge persistence.
4. **Discovery confidence model** — binary (known/unknown) per trait, or a
   confidence ramp the comms officer voices? Ramp is richer, more state.
5. **Reading cost** — is feeling-out free (just run contracts) or does it
   want an active spend (a scouting action, a Cadre, an intel building)?

## Related

- [overview.md](overview.md) — the two-tempo engine traits drive.
- [`../narrative/overview.md`](../narrative/overview.md) — the modifier-trait
  axis this promotes to behavior; the comms-officer discovery narrator.
- [`../mechanics.md`](../mechanics.md) — `houseAmbition` / `relationships[]`
  / rank-visibility this builds on.
- [`../moral-compass.md`](../moral-compass.md) — the never-show-the-number
  discipline the discovery surface has to honor.
- Memory: [[feedback-world-reactive-over-expressive]],
  [[feedback-patron-narrative-discoverable]], [[user-engine-game-framing]].
</content>
