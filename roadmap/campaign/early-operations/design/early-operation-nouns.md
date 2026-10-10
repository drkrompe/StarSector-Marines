# Early operation nouns

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-10 — Jangala introduction through Mara Venn and the Wayline opening sequence.

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

**Mara Venn** is the named Independent broker who introduces the company to
this work. Her canonical office is on **Jangala in Corvus**. She represents the
**Wayline freight cooperative**, an authored local commercial interest; neither
her affiliation nor its militia implies that Jangala's government is Independent.
Relief protects Wayline's militia line; the survivors identify its occupied
freight depot for Counterattack. HQ and the local client board show her portrait
and affiliation; the mission briefings carry her name and consistent voice.

Before the first Counterattack victory, the pair is offered only at Venn's
current office. Other Independent brokers refer the company there. Relief is
available before the first squad is founded; deployment still owns personnel
and lift checks. Relief victory unlocks Counterattack at that office while
leaving Relief retryable and repeatable. Completing both opens recurring militia
work at other eligible Independent brokers. Those local jobs use local employer
copy rather than pretending Venn is present everywhere. Existing completion
facts carry forward unchanged, including victories earned before the named
introduction existed. No meeting or acceptance flag can reset that progress.

The office uses reachable campaign geography. Prefer Jangala when it is a visible,
populated, non-player planetary market whose owner is not hostile to the player.
Otherwise use a qualifying Independent planet, then another qualifying planet,
with stable market-id ordering within each preference. Stations, hidden markets,
and player colonies cannot become an office. This is a read-only location policy,
not a simulated travelling contact: current campaign facts determine its location,
and HQ and local offers consult the same answer. If no office is accessible, HQ
says so and disables its course control. It never points to an absent planet.

HQ provides opening guidance with Venn's portrait, the Independent crest, her
current planet/system, the exact mission and payment, and preparation advice.
The local route is **Marine operations → Mara Venn**. **Plot Course** lays in a
vanilla navigation course and dismisses HQ; it neither launches an operation nor
accepts work. Relief completion advances the notice to the depot job. Completing
both retires the notice, not the work. The broker is an authored Marine Operations
client, not an additional vanilla comm-directory person or relationship track.
Faction-client access and deployment checks still apply; this exception does not
refill ordinary patron contracts or add invented news events.

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
briefing claims before another opening variant is contracted. Live first-offer discovery and save/reload acceptance remains a separate alpha
gate tracked in `first-contract-discovery.md`.
Shipped implementation evidence is in `shipped.md`.
