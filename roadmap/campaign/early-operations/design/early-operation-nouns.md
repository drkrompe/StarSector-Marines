# Early operation nouns

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-02 — reconciled recurring Independent work with current live-acceptance scope.

Early operations are the opening proof that a new marine company can take
meaningful work without being asked to behave like an invasion force. They are
authored First Contract missions, not weakened generic battles. Their force
model remains aimed at a small company as it grows; the fixed, modest payout is
the reason for a veteran company to move on, rather than an eligibility rule
withdrawing the work.

## Opening operation pair

The **opening operation pair** is recurring Independent-broker work. Relief
establishes that the player can reinforce a local line; counterattack then asks
that company to secure the bandits' depot. Both remain available after
completion, and defeat leaves the relevant operation available to retry.
Completing Relief unlocks Counterattack permanently, even if the company later
grows or suffers losses. Neither operation is gated on the roster remaining
green or small.

The Independent broker is the pair's sole owner. Offers use the ordinary client
surface rather than a synthetic tutorial client or a planet-specific route.
The seeded availability roll is stable for the same planet and client, though
completing Relief can add the newly unlocked Counterattack. Currently each
operation appears at roughly one in three eligible Independent brokers. Whether
that is a dependable first-offer route is an open alpha discovery question in
`v0.1-alpha.md`.

## Small-company promise

An **early operation** is tuned around scarce player lift, mixed persistent
equipment, and a company still learning to fight together. Employer militia
makes the battle larger but never replaces the player contribution: local
troops take their authored seats and campaign personnel retains ownership of
player seats.

The enemy is a finite ragtag force. An early operation deliberately excludes
regular infantry, mechs, static defense posts, fighter cover, and open-ended
reinforcements. Tuning must first change local force ratios, approach timing,
or battlefield pressure—not add late-game support merely to make a battle more
eventful.

## Authored scenario identity

**Opening operation kind** gives each rung its tactical premise. Relief owns a
local defensive anchor and asks militia to hold until the player arrives;
counterattack omits that predeployed line and makes the joint advance the
problem. Both use small-scale terrain and militia-only non-player forces, but
their shared campaign tier does not erase their different scenario roles.

The finite-force command duel uses one public scenario place rather than hidden
enemy positions. In Relief, the local line keeps authored garrison ownership,
mobile friendly squads preserve its anchor, and raiders advance on that anchor.
In Counterattack, the joint force advances on the bandit-depot anchor while the
finite raider force defends it. Each side plans from a frozen own-force frame;
contact response remains squad doctrine. The anchor/depot intent does not alter
the current elimination objective or victory authority. This is a reusable
command-architecture seam, not a rule later Assault missions inherit.

## Boundaries and acceptance

Operation tier supplies the shared scale vocabulary; it does not replace the
small-company force promise or scenario identity. Company-view owns the player's
squad and lift semantics. Battle setup owns finite force composition and
mission command. The opening pair owns only progression between its missions.
Explicitly persisting that authored tier through the mission boundary belongs
to `retire-risk-tier-bridges.md`.

An eventual local-survival incentive must consume allied casualty facts frozen
into the mission outcome. Until that outcome boundary exists, it is an
extension point rather than a bounded early-operation story.

The remaining live play is tracked by `opening-ladder-live-acceptance.md`: it
must validate arrival timing, actual lift requirements, force ratios, and
briefing claims before another opening variant is contracted. Whether a new
player can dependably discover the first offer remains a separate alpha gate.
Shipped implementation evidence is in `shipped.md`.
