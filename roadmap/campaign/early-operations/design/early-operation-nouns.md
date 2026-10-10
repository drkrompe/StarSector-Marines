# Early operation nouns

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-10 — finite Wayline chapters, completed correspondence, and separate recurring work.

Early operations are the opening proof that a new marine company can take
meaningful work without being asked to behave like an invasion force. They are
authored First Contract missions, not weakened generic battles. Their force
model remains aimed at a small company as it grows; the fixed, modest payout is
the reason for a veteran company to move on, rather than an eligibility rule
withdrawing the work.

## Story arcs, chapters, and recurring work

A **story arc** is a finite sequence of authored operations for a named contact,
with a problem, a resolution, and a visible completed record. A **chapter** is
one operation within it. Victory closes that chapter; defeat or Retreat leaves
it available after the company recovers. A **recurring job** is a separate
posting, with its own identity and terms, which may reuse a chapter's tactical
setup without replaying its historical event. Company growth never resets a
chapter or removes ordinary small jobs.

The opening arc is **Wayline: An Open Road**. **Mara Venn**, an Independent
broker based canonically on **Jangala in Corvus**, represents the **Wayline
freight cooperative**, an authored local commercial interest. Neither her
affiliation nor its militia implies that Jangala's government is Independent.
Wayline's drivers pooled money for local protection; raiders occupied their
freight depot and now threaten its militia post. This is a local company story,
not a simulated change to vanilla market ownership, trade, or faction relations.

1. **Hold Until Relieved**: land beside the militia and break the bandit assault.
   Successful settlement pays 6,000 credits, records Relief complete, and unlocks
   the depot chapter. Recovered traffic logs connect the two operations; the
   story does not require or invent surviving militia witnesses.
2. **Take Back the Depot**: join the local advance and eliminate the depot's
   bandit force. Successful settlement pays 9,000 credits and closes the arc.
   Mara reports that dispatch can resume and refers the company to other
   Independent brokers. Her completed dossier preserves both chapter labels.

Only the current uncompleted chapter is offered at Venn's accessible office.
Other Independent brokers refer the company there until the final victory.
Relief is visible before the first squad is founded; deployment owns personnel
and lift checks. Chapters have no seed lottery or maximum company size.

After the arc, **Militia Relief** and **Depot Clearance** become distinct
repeatable postings at eligible Independent brokers, including Venn. Their
names, identities, source, and local-employer briefings distinguish them from
Wayline's closed chapters. They reuse the finite opening battle setups and
payments. Their victories never write story-completion facts or replay Mara's
story debrief. They remain available after success or failure. This authored
exception does not fabricate ordinary patron contract rows.

Progress derives from existing opening victory facts. Older Relief victories
advance directly to the depot; older Counterattack victories close the arc.
No meeting flag, re-acceptance, or migration resets that history. The final
victory is authoritative even for a legacy record missing its earlier fact.

Story results contain a message from Mara alongside normal settlement and
personnel facts. Allied and company losses are acknowledged only from the frozen
outcome. A total militia loss is never described as a line saved intact. Story
text does not introduce a survival bonus or override battle victory.

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
the arc retires the notice; its completed dossier and separate recurring work
remain. The broker is an authored Marine Operations
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
mission command. The arc owns chapter progression and correspondence; recurring
work has no authority to advance it.
Explicitly persisting that authored tier through the mission boundary belongs
to `retire-risk-tier-bridges.md`.

An eventual local-survival incentive must consume allied casualty facts frozen
into the mission outcome. The outcome already carries these facts for debriefs;
a payout or objective incentive remains a separate gameplay change.

The remaining live play is tracked by `opening-ladder-live-acceptance.md`: it
must validate arrival timing, actual lift requirements, force ratios, and
briefing claims. The next gameplay pass starts with Relief, then Counterattack.
`broken-supply-route.md` plans a follow-on alpha arc with a committed choice;
its implementation depends on that mission pass and a small Conquest envelope.
Live first-offer discovery and save/reload acceptance remains a separate alpha
gate tracked in `first-contract-discovery.md`.
Shipped implementation evidence is in `shipped.md`.
