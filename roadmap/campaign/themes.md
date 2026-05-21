# Campaign themes: space feudalism and the impact ladder

> Design discussion, not a spec. Captures the framing for the campaign /
> meta layer of the mod from the 2026-05-21 design session. Followup
> docs (mechanics, data schema, T3 endgame) will live alongside this one
> as they're written.

## The credibility problem

The mod's mission sim has grown rich tactically, but the campaign layer
is still "pick a faction, pick a mission, fight." Two issues fall out
of that as we scale up content:

1. **Plausibility**. There is no in-fiction reason the Hegemony would
   tolerate the player running a half-dozen sabotage ops on its core
   refinery worlds. Vanilla rep would tank in a single session and the
   player would stop being able to interact with the faction.
2. **Economy safety**. The disruption mechanic in
   [`MissionResolver`](../../src/main/java/com/dillon/starsectormarines/ops/MissionResolver.java)
   already pushes on vanilla industries. Sustained marine pressure that
   meaningfully reshapes the sector economy is fun *content*, but the
   game's economy isn't built to absorb arbitrary perturbations from a
   mod's side-game.

We need a layer where the player can be politically active *without*
either breaking vanilla rep or breaking the sector economy.

## The reframe: a feudal layer beneath the faction graph

Every vanilla Starsector faction is already internally feudal in its
lore — the base game just abstracts over it:

- **Hegemony** is an officer caste with internal politics.
- **Tri-Tachyon** is literal corporate boards.
- **Persean League** is a confederation of noble houses.
- **Sindrian Diktat** is one family's fiefdom.
- **Pirates** are decentralized warlords (Kanta is a name, not an
  org chart).
- **Luddic Path** is cells, not a hierarchy.
- **Independents** are local oligarchs and family-run stations.

The mod's campaign layer operates **one tier below the vanilla faction
graph**, modeling the houses / syndicates / cells / corporate boards
that already implicitly exist in lore. Marine ops are how the player
participates in that layer.

Blowing up a refinery on Sindria stops being "you attacked the Diktat"
and becomes "House Drennar's stake in the refinery just transferred to
House Korvath after a violent night." The planet still flies the
Diktat banner. From the outside it looks like an internal squabble,
which is what feudal polities *do*. Vanilla rep is untouched.

## The impact ladder

The player's ability to perturb the world is gated by a three-tier cost
gradient. Each tier costs the player more and touches vanilla state
more:

| Tier | Effect | Cost | Vanilla touch |
| --- | --- | --- | --- |
| T1 | Local political shift — one house gains influence over another on a single industry stake | Cheap; ~1-3 missions | None; sub-faction state only |
| T2 | Economic shift — industry disrupted, captured, or rerouted; planet conditions change visibly | Medium; ~5-10 missions in a chain | `Industry.setDisrupted`, condition mutation (already wired in `MissionResolver`) |
| T3 | Faction flip / planet defection / splinter | Expensive; 10+ missions and real vanilla rep consequences | Market ownership change, rep swings with the displaced faction |

T1 is the daily bread — small, frequent, flavor-driven. T2 is the
mid-arc the player builds up to over a few sessions on a planet. T3
is endgame; its vanilla-deep mechanics are deferred to a followup doc.

The Luddic Path insurrection arc is the canonical T1→T3 chain:

1. **T1.** Hit a corrupt local administrator on behalf of a Path cell.
   No one outside the planet notices.
2. **T2.** Disrupt enough planet industry that the cell visibly grows.
   The planet's stability dips, conditions list reflects it.
3. **T3.** Open insurrection. The Luddic Path proper moves a fleet in.
   Vanilla rep with the displaced faction swings *now*, because this is
   when the act becomes undeniable. Reward: a marginal nearby colony as
   the new patron's gratitude, **not the prize planet itself**. The
   player ends up positioned as junior partner to their patron, with a
   foothold to play later chains *against* that patron if they want.

The reward structure matters. The player shouldn't be able to take over
an Independent world cheaply just because Independent worlds make good
real estate — that would require real relationship consequences with
the displaced faction.

## Thematic flavors

The same house-vs-house mechanic carries four genre flavors depending
on the parent vanilla faction. Same data, same code path, different
namesets and mission texture:

- **Corporate** — Tri-Tachyon, megacorp-leaning Independents.
  Board seats, hostile takeovers, R&D theft, executive abductions.
- **Feudal** — Hegemony, Persean League, Diktat.
  Houses with heirs, succession assassinations, dynastic intrigue.
- **Underworld** — Pirates, free ports, low-stability worlds.
  Gangs, fences, smuggler wars, hits.
- **Sectarian** — Luddic Church, Luddic Path.
  Cells, schisms, heretic-hunts, holy relics.

A single mission generator picks the flavor by parent faction and
draws from the matching name lists, mission archetypes, and prose
templates.

## House as primitive

Each populated market hosts 2-4 *houses* (or syndicates / cells /
corporate boards — pick by flavor). Each house owns stakes in some
subset of the planet's industries. Missions are framed as one house's
move against another's stake. T2 escalation transfers the stake;
T3 transfers the planet.

Houses are our invention. Vanilla never sees them directly. The mod's
serialized state owns the entire sub-faction graph.

### Rank ladder

Houses carry a **rank** that scales their political horizon. The
vocabulary varies per flavor but the structure is universal:

| Tier | Visibility | Feudal | Corporate | Underworld | Sectarian |
| --- | --- | --- | --- | --- | --- |
| 1 | Planet-local | Baron | Manager | Capo | Cell Leader |
| 2 | System-local | Count | Director | Boss | Cell Coordinator |
| 3 | Faction-local | Duke / Margrave | Vice-President | Don / Warlord | Diocese / High Theocrat |
| 4 | Sector-wide (rare) | Crown Claimant | CEO / Chairman | Kingpin | Patriarch / Living Saint |

A Tier-1 Baron sees only the houses on her own planet; a Tier-2 Count
plots across the star system; a Tier-3 Duke maneuvers faction-wide.
**Tier 4 is the T3 endgame** — a Duke claiming the crown *is* the
faction civil war (the Sforza arc made literal), and this is when
vanilla state finally swings. Tier 1-3 stay entirely in our layer.

Houses themselves have meta-progression: they want to climb the
ladder, which gives them ambition independent of the player. A house
hires mercs *because* it's trying to consolidate enough stakes to
promote. The player's chains are the vehicle that accelerates an
aligned patron's climb — a Baron who pays for ten ops isn't buying
muscle, she's buying her own promotion to Count.

This is also the design that collapses the visibility-model question:
the `relationships[]` graph is naturally sparse because most houses
are Tier-1 and their political horizon is one market. No special
casing for Independent (which is solved automatically — every
Independent presence is just a local house), no N² explosion.

## Captains as trust network

The captain roster ([`MarineRosterScript`](../../src/main/java/com/dillon/starsectormarines/marine/MarineRosterScript.java))
already persists named captains with rank, traits, and a commendation
log. We extend that with a per-house reputation overlay:

- A captain who has run multiple successful ops for House Korvath
  becomes *Korvath's preferred contractor*, and Korvath ops anywhere in
  the sector ask for her by name.
- High reputation **compresses chains**. Late-game heavy ops resolve
  a planet dispute in 1-3 missions instead of 10+. The player is
  recognized as a true army for hire, not just a contractor.
- Burned bridges remember. Working against a house tanks rep with that
  house specifically, independently of the parent vanilla faction.

This gives the existing captain layer a strategic purpose beyond
per-mission stat blocks — captains are political instruments, not just
unit modifiers.

## The player as a mercenary company

The sector after the Domain Collapse is the kind of place where
mercenaries are *standard practice of war* — not outlaws with letters
of marque, but registered institutional actors with names, traditions,
and reputations. This is the same lineage as BattleTech's Inner Sphere
after the fall of the Star League: houses can no longer field the
armies their grandfathers took for granted, and the gap is filled by
named companies — Wolf's Dragoons, the Kell Hounds, the Gray Death
Legion. Marine ops in this mod sits in that tradition.

BattleTech is the modern touchstone, but the historical source is the
Italian *condottieri* of the 13th-15th centuries. Sir John Hawkwood's
White Company served Florence, Pisa, Milan, and the Pope across his
career, paid through formal contracts (*condotta*) that specified
terms, length, salvage, and command. Francesco Sforza, a condottiero,
took Milan outright in 1450 — the historical precedent that the T3
"company becomes faction" path is a real pattern, not a designer's
conceit. Machiavelli warned the city-states against mercenaries
precisely because companies could grow powerful enough to shape
politics, which is the in-fiction reason a Mercenary Review Board
analogue exists at all: sector governments fear their own mercs and
want them watched.

That settles the player's *position* in the world cleanly:

- **The player is a named merc company, not a faction.** They have a
  registered identity, a captain roster as their named officers, and a
  reputation tracked separately from any single client. Long-term they
  may carve out a colony, but that's the *company's base of operations*,
  not a faction capital. Wolf's Dragoons founded Outreach but remained
  Wolf's Dragoons.
- **A neutral broker legitimizes the company across factions.** The
  BattleTech Mercenary Review Board analogue — in Starsector terms,
  likely an Independent-aligned organization that aggregates contract
  history, flags breaches, and rates the company's reliability. Vanilla
  rep is local to each faction; the merc registry is the *cross-faction*
  reputation track that compresses chains and gates heavy contracts.
- **Contract *types* are a design surface in their own right.** Today
  the mod has one-shot strike missions. BattleTech's vocabulary
  suggests others worth modeling:
  - **Strike** — current default, one-off short-tempo op.
  - **Garrison** — multi-week planetary presence; small recurring ops;
    reputation accrues with local houses; competes with the player's
    vanilla activities (you've picked where you live for a stretch).
  - **Cadre** — train local militia or planetary guard; low combat
    tempo, high political access (you're inside the patron's
    decision-making for the duration).
  - **Escort** — accompany a fleet or convoy across systems; integrates
    with vanilla travel.
  - **Planetary Assault** — T3 anchor; explicit, formal, expensive.

  Time-commitment contracts (Garrison, Cadre especially) are the
  interesting ones — they trade flexibility for depth of relationship
  with the patron, and pair naturally with the chain-compression
  reputation mechanic.

## Sector-wide simulation

The Persean Sector has on the order of low-hundreds of populated markets
across ~70-80 systems. A packed-table data-oriented layout — arrays of
house records, industry stakes, reputation values, and active chains —
can simulate *all* of it cheaply on every campaign tick, without
spinning up state lazily per visit.

Houses tick independently of player presence. While the player is
elsewhere:

- House ambitions advance or fail by AI roll on a per-day cadence.
- Rival houses resolve their own disputes without us. Sometimes the
  player returns to find the political map shifted.
- A "missed" T2 outcome is something the player can discover on revisit
  and react to — a refinery now flying a different sub-banner, a cell
  that lost its leader while you were away.

This is what makes the campaign feel *alive* rather than
scripted-to-the-player. DOD layout keeps per-tick cost negligible at
sector scale (a few thousand rows).

## Open questions

1. **Chain progress gating** — is it just completed mission count, or
   do mission outcomes matter (full victory vs. costly victory advances
   faster)? Tying it to battle-sim outcomes makes the tactical layer
   feed the meta layer.
2. **Cross-house reputation interactions** — does working for one
   feudal house make rival houses *more* receptive (you've proven you
   can be hired) or *less* (you're tagged with their banner)? Probably
   varies by flavor; corporate boards behave differently than feudal
   houses here.
3. **Does the merc company ever become an institutional faction in its
   own right?** BattleTech's Wolf's Dragoons eventually founded the
   Outworlds Alliance, but most named companies stayed companies
   forever. Default answer: the player stays a company; the T3 colony
   reward is the company's *base*, not a faction capital. Revisit only
   if late playtest demands it.
4. **AI house behavior** — scripted "ambition" goals (House Korvath
   wants Hybrasil heavy industry) or utility-scored opportunism?
   Scripted is easier to author; utility is more replayable. Likely a
   mix: each house has a long-term ambition plus tactical opportunism.
5. **Story missions interaction** — story missions
   ([`StoryMissionRegistry`](../../src/main/java/com/dillon/starsectormarines/ops/mission/story/StoryMissionRegistry.java))
   are one-shot scripted; T1-T3 chains are procedurally driven. They
   probably interleave — story missions bookend chains as marquee
   moments inside a generated arc.
6. **What does the player see?** Long arcs need a quest-tracker /
   intel feed surface. The Bridge intel entry is the obvious home, but
   the UX of "I'm 4/12 missions into flipping Sindria for House Korvath"
   is its own design problem.

## Followup docs to write

- [`economy.md`](economy.md) — money loop. Income sources, sink
  ledger, scale-inefficiency curves, mitigation track, licensing
  tiers, bankruptcy semantics, campaign-arc shape. **Drafted.**
- [`mechanics.md`](mechanics.md) — data model. SoA tables (houses,
  stakes, relationships, chains, player reputation), tick loop,
  rank-ladder visibility, stake-transfer mechanics, hidden-heir
  layer. **Drafted.**
- `contracts.md` — contract types (Strike, Garrison, Cadre, Escort,
  Planetary Assault), their time-commitment semantics, salvage and
  payment terms, the merc-registry reputation track.
- `infrastructure.md` — per-planet and per-region buildings: build
  cost, monthly maintenance, exact mitigation effects, stacking rules.
- `t3-endgame.md` — faction flips, market ownership change, vanilla
  rep consequences, splinter-faction creation, the "marginal colony as
  reward" mechanic.
- `flavors/` — per-flavor authoring notes: nameset, mission archetype
  catalog, prose templates. One file per flavor.
