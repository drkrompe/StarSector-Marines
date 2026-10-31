# Campaign nouns

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-13 — campaign overview and dedicated authored-arc plan homes.

## Purpose

The campaign tier is the persistent mercenary-company consequence layer around
operations. The player is a named mercenary company working inside the
political space beneath Starsector's faction graph, not a new faction that
silently replaces it. Campaign play makes the company's commitments,
relationships, and consequences survive the battle that produced them.

It is an umbrella tier, not one universal policy feature. Its child domains
own their own models and concrete work boards; this document preserves the
shared vocabulary and authority boundaries that keep those domains from
becoming parallel campaign simulators.

`campaign-overview.md` is the reading guide to the authored route, its capability
progression and the shared campaign systems. Each authored arc has a dedicated
folder with an enduring overview, setting material where needed, and its own
open-work board and stories. These planning views describe the campaign; they
do not add progression gates or replace a domain's canonical rules.

## Campaign shape

An **operation** is the playable tactical work. A **contract** is the
commercial commitment that can authorise an operation. A **political chain** is
the longer-running house conflict that contracts may advance or oppose. A
**campaign event** is an exceptional, cost-shaped choice that is not a second
contract. These are distinct authorities: a battle result reports facts; its
owning campaign policy decides what those facts settle.

The campaign records durable identities, accepted commitments, observed
outcomes, and attributed consequences. It must not infer a political result,
moral choice, or recovery right merely because an operation ended. Each such
consequence requires its own persisted authority and exactly-once boundary.

## Ending an operation

Confirmed **Retreat** is a **withdrawal**: it ends an active operation as defeat
and opens the same Results settlement as objective completion. It preserves
recorded personnel and boat losses and kills nobody further. Its one cost is
wounds: survivors still on the ground outside every **extraction circle** (a
fixed radius around each pad the company landed on) come home wounded, while
those inside one extract unhurt. The circles are drawn on the map while Retreat
is being confirmed. There is no
extraction sequence. Rewards, officer recovery, contracts, events, and
opening-operation retry follow their existing defeat policies. Zero engaged
troops with zero losses is not a total wipe.

A **story retry** erases a story operation's attempt instead of settling it:
nothing reaches Results, the arc's launch claim is withdrawn without counting
the attempt, supplies spent on powers return, and the company goes back to the
briefing on the same map. It is offered on a story defeat beside Accept defeat,
and as Restart inside Retreat's confirmation. One retry is free per visit to
mission select; each further one spends a Starsector story point. Only story
operations retry; contracts and recurring work settle every attempt.

A **replay** re-flies a story chapter the company has already won. The mission
board's "Replay Story Missions" lamp key (off by default, saved with the game) shows a
**Company records** contact listing one replay per chapter won on that planet
(the opening at the broker's office port such as Jangala, the supply route at its
site, Working Ship on Asharu, Agreus on Agreus, each Corvus chapter on its own
world); a chapter launches only from its own planet. The Khettara Bloom keeps
its own replays and is not listed. A replay builds the chapter's
battle off the arc's state, is flown by the company as it stands now, and on
victory pays the chapter's credits (Working Passage, which paid none, pays Cold
Start's). Personnel, losses and loot settle as for any operation; the arc's
progress, story completion, awards, campaign marks and debriefs are untouched,
and a replay does not retry.

**Cheats** are the alpha's Debug key on the mission board, beside Replay Story
Missions: off by default for players (on in a dev build) and saved with the
game. While it is on, the board lists **Debug missions**, every mission with
sandbox forces, loadouts and settings that never touch the company or the
campaign; Company HQ shows its debug button; and a battle's debug panel offers
**Win**, a general "I win": it ends any battle as the company's outright
victory. Every story record reports each objective met and nobody lost, so the
arc settles it as a full success (fee, chapter advance, rewards); the company's
own losses and experience are what the battle had reached.

Terminal civilian evacuation facts freeze at either ending boundary. A result
already decided by objectives cannot be replaced by Retreat. The active battle's
campaign settlement is claimed once before writeback, so repeated exit input
cannot replay it. Debug-source operations retain their non-persistent policy.

## Political scale

The company normally acts through houses and local interests within a vanilla
faction. This allows frequent local political change without pretending every
operation is a faction war. As consequences become broader, their authority
must become more explicit and more expensive: local influence and commercial
standing remain campaign-owned; exceptional faction ownership and diplomacy
cross only through `t3-endgame-nouns.md`.

The player remains a company even when its work materially reshapes the world.
MRB credibility is cross-client company standing; house relationships are local
political history. Neither is a substitute for vanilla faction reputation.

## Ownership

`architecture.md` owns the persistent-runtime contract.
`campaign-framework-nouns.md` owns monotonic time and ordered autonomous
execution. `living-world-nouns.md` owns houses, stakes, ambition, political
chains, and learned Chronicle truth. `contracts-nouns.md` owns agreements and
settlements; `loot-nouns.md` owns recovery; `campaign-event-nouns.md` owns
exceptional event lifecycle; `mission-tier-nouns.md` owns scale vocabulary;
`early-operation-nouns.md` owns the authored green-company opening ladder;
`personnel-nouns.md` owns company identities, organization, availability, and
outcomes; `infrastructure-nouns.md` owns future location-bound investments;
`t3-endgame-nouns.md` owns the exceptional vanilla-state handoff; and
`narrative-nouns.md` owns truthful presentation.

`meta-progression.md` owns the long-arc frame the child domains share: the
four currencies the company accumulates or suffers, the stages that fall out
of them, the growth rule a new feature must answer, and the boundary between
the company and the player's vanilla holdings. It owns no threshold; each
number stays with the domain that settles it. `polity-ground-doctrine.md`
owns how the polity's own ground forces are composed: a roster derived from
its markets the way vanilla derives fleet quality, never authored.

`themes.md`, `economy.md`, and `backgrounds.md` are direction for future
campaign work. `moral-compass.md` owns the hidden record that can connect
otherwise separate campaign choices without turning it into an optimisation
surface.

## Laws

- A campaign extension must name its authoritative persisted fact, its terminal
  boundary, and the feature that may change it.
- A player-facing screen projects campaign truth; it does not become a parallel
  simulator, event queue, or settlement path.
- Local campaign simulation never directly escalates into vanilla faction
  ownership or diplomacy. That exceptional boundary belongs only to T3.
- New content may add a contract, chain, event, or authored operation, but must
  not blur their commitment, reward, and resolution rules.
- The player's vanilla holdings — the **polity** — are a venue, never a
  client and never a house. Work on the polity's behalf earns no MRB
  credibility, no house relationship, and no settlement; the living world
  seeds no political identity on a player-faction market. Defending the
  polity settles only through vanilla's own raid outcome path.
  `meta-progression.md` owns the boundary.
