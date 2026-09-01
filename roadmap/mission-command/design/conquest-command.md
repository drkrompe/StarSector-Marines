# Conquest command

Status: ACTIVE — paired attacker and defender command is implemented; live convergence and reinforcement acceptance remain.

Written: 2026-08-27

Updated: 2026-09-01 — a home-track bound on marine capture allocation is built
and measured; it stops the far-track walk and ships off behind its own switch.
Earlier 2026-09-01 — the defender's reserve is a share of its mobile pool and
each threatened track's response scales with the threat, so a large garrison
commits in proportion to itself. Earlier 2026-08-30 — a track with no believed
front now stages forward, bounded by its neighbours' lead, instead of standing
still waiting for a sighting only advancing can produce. Earlier: a compact
player-facing three-lane projection in the battle HUD; lane-stage standoff read
from the squad's own corridor so a front believed off that line does not
withhold orders.

Read `mission-command-nouns.md` for the shared architecture and
`conquest-nouns.md` for territory, compounds, supply, keep, and victory law.

Conquest is a directional territorial command duel. Three lateral **tracks**
organize a readable front across the map's traversal axis. A track is a sticky
coordination preference, not an ownership fence: squads may support a neighbor
when their home track has no useful work, and the whole mobile force may
converge for the culminating keep or final contested compound.

## Marine command

The attacker balances front pressure with deliberate compound capture. Unknown
occupancy permits a measured probe, not a declaration of clearance. Fresh
distant capture allocations preserve squads already committed or adjacent, use
squads without useful front work first, and retain at least one executable
front squad while actionable resistance exists when force size permits.

**Capture allocation is the one map-global thing in a command built out of
tracks, and bounding it to the home track loses the battle.** The front push
supports a neighbouring track and no further; the distant capture fill ranks
every uncaptured compound on the map against every uncommitted squad on
straight-line distance alone, and the preserve pass then keeps whatever that
produced for the rest of the battle. The observed cost is a squad walking the
width of the map to a compound two tracks from where it was born, its own track
left to a fraction of its strength facing known contacts and the receiving track
already crowded — a squad out of the fight for about a minute, and a track that
does not advance while it is gone.

The bound that answers it is built and measured: pair a fresh detachment only
with a compound at most one track from home, and let it take a *neighbour's*
compound only while its own track has nothing worth doing — neither a defender
zone the front push would send it to, nor an uncaptured compound of its own.
Its own track's compound it may always take, ordering among the survivors is
unchanged, and a squad already holding a capture or standing at one keeps it
whatever track it is on, because those are about ground already reached. It does
what it claims — fresh far-track pairings go to zero — and on the reinforced
fixture it takes 6 compounds and holds 2 where the unbounded fill takes 12 and
holds 11. That is what the far-track walk was buying: a map whose compounds are
spread across three tracks does not offer every track work of its own, and a
squad refused a distant capture mostly stays where it is. It ships **off**;
held compounds are the outcome a Conquest is decided on. The switch and the
per-fixture numbers live on `ConquestCommand.HOME_TRACK_CAPTURES_PROPERTY`.

The reported cost is real, so the useful next question is narrower than the one
that was asked: not *whether* a squad may cross tracks for a compound, but
which far compound and how many squads may go. A squad refused by the bound and
left with nothing else already publishes its own assignment reason, because a
far-track refusal and an empty map are otherwise the same sentence.

The two convergence phases are map-global for a reason no measurement can move:
once the keep or one contested compound is the whole remaining objective, there
is no other front to hold.

When belief shows open-ground resistance but no discrete room is assignable,
an advance-track order stages behind the hostile frontier, bounded by friendly
lead, safe stride, reachability, and no-backtracking law. The frontier that
sets a squad's standoff is read from the corridor along that squad's own line
of advance rather than from the full width of its track: a track spans tens of
cells laterally, and a contact at its far edge is not in front of the squad. A
front believed in the track but not in that corridor drops the standoff and
lets the remaining bounds size the step — knowing the lane is contested
elsewhere is a reason to advance in step with the friendly line, never a reason
to stand still. Local contact then hands execution to squad doctrine.

A track with **no believed front at all** stages too, and this is the harder
half of the law. Refusing to was a deadlock rather than caution: a track nobody
advances through is a track nobody sights anything in, so the belief that would
authorize the advance can only be produced by the advance itself, and the
compound-capture path cannot break the cycle because distant detachments are
gated on a front that the same stall is what stops moving. Measured at ten
squads and ninety-eight marines standing still for an entire battle in the one
lateral band that happened to hold no defenders, never firing a shot, while the
commander correctly reported no actionable track target at every pulse.

The objection that refusal was making — an own-force push on no intelligence
must not become a lone flank walking off ahead of the force that would have to
support it — is answered by a bound instead. An unscouted track may lead its
*neighbouring* tracks' friendly lead by the same margin a believed one may lead
its own, so the line advances abreast and a track that has run out ahead holds
until the rest come up. The relaxation is confined to staging: a squad already
in contact is not stuck, and an exterior contact the commander has not yet
placed stays ambient rather than becoming a fabricated forward order. A blind
advance is published under its own reason, and does not count as the actionable
front work that holds squads back from distant captures — staging blind is what
a squad does precisely when there is none.

When only the canonical keep remains, all available assault squads converge
across track boundaries. If the keep is held and one earlier compound is the
sole contested objective, the capture quota stays deliberate while other
squads receive bounded cross-track clear support. Ordinary track allocation
returns when the front reopens.

## Defender command

The defender mobilizes only from defender influence. A legal contact produces
a coarse threatened track and band, never an enemy identity or exact cell.
Starting patrols form the mobile pool; born garrisons retain their posts.
Threatened tracks receive one responder before concentration, home and adjacent
tracks are preferred, and part of the free pool remains in reserve when the pool
permits it. Expired reports release only defender-command-owned responses.

**The reserve is a share of the mobile pool, not a count, and the response a
track receives scales with what is believed to be in it.** A fixed one-squad
reserve and a fixed two responders per track read as caution at a starting
force of a few patrols and as abdication at sixty: three threatened tracks
drawing six squads while the rest hold their home tracks is a tenth of a
defence committed while the base is taken compound by compound. So a quarter of
the pool is held back above a floor of one squad, and each threatened track may
draw its own fraction of the remaining budget — its share of the believed
contacts summed across every active threat, floored at the old fixed cap so a
small pool behaves exactly as it did. Counted contacts weight that share rather
than the diffused pressure field, which is sampled at influence-block centres
and measures the block grid as much as the front. Concentration remains
strictly second: every threatened track still receives its first responder
before any track receives a second.

This is a bound on how much force answers a threat, never a relaxation of
belief honesty. The share is computed from the same coarse threatened-track
picture, so a larger response is a larger response to a report — not a finer
one, and never an enemy identity or an exact cell.

Explicit hold, recapture, and relief tasks outrank soft track response. A
Conquest convoy enters through the strict defender rear edge, uses a frozen
behind-front deployment hint, and hands its passenger squad directly to
`conquest-defender` with the request's relief objective. Shuttle and walk-in
forces keep reinforcement ownership until another explicit mission policy
defines their handoff.

## Picture and evidence

The perspective front picture publishes phase, track extents, friendly body and
lead, believed-hostile frontier and pressure, preferred/effective track,
reserve, the response budget and the per-track responder cap it was split into,
assignment reason, and exact commander target or labelled zone marker.
It includes frozen own-squad position, leader zone, local contact, execution
suspension, active-path count, and the count of own members in the squad's
assigned target zone. Exact whole-zone occupancy, capture progress, and
ownership transitions remain neutral referee facts.

The player-facing **lane brief** is the compact Marine-perspective projection of
that picture. It appears only for Conquest and names the command phase, command
pool and reserve, then keeps Alpha, Bravo, and Charlie visible as three stable
rows. Each row reports the effective squad and live-member commitment, the
dominant assignment kind, and either known contact, an active compound objective,
or simple on-line status. It is an explanation of published orders, not another
sensor: no-contact means no commander report, and the brief never consults
neutral occupancy, capture progress, or the defender snapshot.

Conquest evidence measures assignment churn, response latency, reserve time,
track concentration, target closure, target-zone arrival, capture-zone
presence, captures/losses, casualties, and terminal or timeout result. A
secure-compound trip ends once on target entry, retarget, release, squad loss,
execution suspension, observation gap, timeout, or terminal result. Its local
contact, active-path, and quiet-travel observations remain overlapping context,
so a lethal contact-bound approach cannot be mislabeled as unexplained idle.
The current acceptance work is tracked by
`front-command-and-keep-convergence.md`,
`defender-track-mobilization.md`, and
`defender-convoy-deployment-and-handoff.md`.

Derived evidence further separates a retarget to another compound, a changed
capture marker, and a replacement assignment. For squads lost during a secure
trip it records the final living pulse's objective distance and fraction of the
approach completed, plus whether that pulse showed local contact, only track
belief, no published contact, or missing track context. These are pulse-level
observations rather than exact casualty coordinates. The current trace does
not publish normalized own-squad forward progress, so reports do not pretend
to place a loss behind or beyond the normalized hostile front.
