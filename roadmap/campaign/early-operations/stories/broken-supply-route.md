# The Broken Supply Route

Status: PROPOSED — alpha follow-on arc; implement after the Wayline mission pass.
Written: 2026-10-10

## Scope and dramatic question

Wayline has its depot back, but reopening a road reveals who depends on it.
Mara introduces **Iona Serr**, a dispatch coordinator trying to reconnect an
outlying settlement with the cooperative. The company can save the settlement's
stranded people or recover the relay that keeps the route commercially viable.
Both requests are legitimate. The cost is which future the company makes possible,
not a hidden morality score or a higher number attached to the same mission.

This is an authored local incident, initially offered at Mara's current office
after Wayline completes. Freeze the incident's accessible planet and named
sites when introduced; later office movement must not relocate accepted work.
Use fictional sites within that planet, without claiming ownership of a vanilla
industry. Validate/fallback the planet before offering; pause unavailable accepted
work with an explanation rather than inventing success or failure.

Canonical boundaries: `early-operation-nouns.md`, `contracts-nouns.md`,
`narrative-nouns.md`, `conquest-nouns.md`, and `extraction-command.md`.
The entire arc below is planned content, not available missions or shipped rewards.

## Playable sequence

**1. Open the Junction** — common opening, small Conquest.
A raider toll position has severed the route. Take its few clearly named compounds
and command post so dispatch can assess the two isolated sites. This introduces
capture/hold and coordinated advances after Wayline's elimination battles.
Target two to four player squads, with employer help; exact force and lift values
must follow the Wayline evidence. Prototype payout: 12,000 credits on victory.

**2. Dispatch decision** — correspondence after the common victory.
Iona has enough local escort and evacuation transport for one operation. Choosing
which request receives those assets commits the company to that branch. The
other local crew has to withdraw. This is a disclosed allocation decision;
waiting in menus or flying elsewhere never silently runs out a timer.

| Commitment | Immediate operation | Cost disclosed before commitment | Lasting benefit after success |
|---|---|---|---|
| **Bring them home** | **Last Lift**: escort a stranded settlement crew to extraction while conventional raiders interdict the route. | The freight crew abandons the relay; the commercial route stays suspended. Lower cash: proposed 10,000 credits. | Iona becomes a recurring relief/recovery contact. Unlock a distinct paid recovery posting; the rescued count is recorded and acknowledged. |
| **Keep the road open** | **Relay Nine**: capture the relay compound and its approaches before dispatch reconnects the route. | Local escorts withdraw the settlement crew without company cover; no claim that the company rescued them. Higher cash: proposed 16,000 credits. | Mara gains a distinct freight-security posting. The cooperative retains the route, but Iona declines to offer the company's recovery work. |

Each playthrough contains two battles, the decision, and a closing dispatch.
The unchosen branch is unavailable for that campaign, visibly recorded as
**not undertaken**, never shown as a failed battle or a second source of rewards.
Neither branch grants free marines, officers, equipment, discounts, or a new
relationship currency. This leaves the inventory economy alone and avoids
pretending faction standing is a personal contact relationship.

## Character and correspondence beats

- Mara after Junction: "You've opened the junction. Iona has two crews asking
  for the same escorts. Hear her out before you sign."
- Iona, people route: "The relay can wait. Those drivers can't make that crossing
  alone. Get them to the lift; we'll find a different way to earn our keep."
- Mara, road route: "If the relay goes, every crew on this run loses the work.
  Iona won't like it. I won't pretend she should."
- People ending: Iona's thanks names the actual rescued count; losses qualify the
  message. Mara accepts the decision but records the suspended freight route.
- Road ending: Mara confirms the recovered relay and freight-security access;
  Iona records that the company chose the route. Do not invent off-screen deaths
  among the withdrawn settlement crew.
- Failed attempts acknowledge actual losses and retain the chosen operation.
  Ordinary job debriefs never replay either epilogue.

## Choice and persistence contract

Show both requests together with objective, recommended force, cash, explicit
opportunity cost, and unlocked contact work. Portraits and mission/resource
symbols aid scanning; hover hints carry detail. The confirmation names the
selected support commitment and permanently unavailable alternative.

Inspection, Back, opening briefing, and canceled deployment do not choose a
branch. Commit once at an explicit "Commit support" action after terms are
reviewable; that action persists the branch before enabling its launch. It is
permanent even if the player loses, Retreats, reloads, or delays. Failure keeps
the chosen job retryable and grants no branch unlock. Successful settlement
writes chapter completion, payment, branch reward access, and correspondence
exactly once from one terminal outcome. Re-entering Results or revisiting the
broker cannot repeat any of these effects.

The arc needs persisted chapter/choice/target identity beyond Wayline's linear
completion facts. Use one authoritative record with stable IDs and a versioned
save migration; do not encode a choice in a display string or infer it from
current cash, roster strength, mission selection, or a mutable UI cache.
Old saves with Wayline complete start this arc uncommitted. Missing or unknown
branch values must not unlock both branches or grant rewards.

## Gameplay dependencies and bounded implementation order

1. Play/review Wayline Relief then Counterattack. Establish arrival time, readable
   militia/depot places, expected losses, lift demand, and honest victory prose.
2. Author a genuinely small Conquest profile for Junction and Relay Nine. Current
   Conquest requires 42/84 squads and all-compound territorial victory: changing
   only mission title or risk is insufficient. Preserve its territorial authority,
   reachable capture rooms, and command honesty; define the small profile's map,
   compound count, force floor, and reinforcement policy explicitly.
3. Prove Last Lift using the existing extraction corridor/payload boundary.
   Conventional raiders must use the appropriate defender adapter, not silently
   inherit the Civilian Rescue swarm. Freeze initial/rescued/lost counts; success
   requires survivors boarded. Partial rescue may succeed under a stated minimum;
   choose and display that threshold before authoring payouts. Do not implement
   a generic "hold for X minutes" victory as a shortcut.
4. Add the arc record and guarded choice transition, authored offers and separate
   repeatable unlocks, then shared settlement/correspondence. Reuse deployment,
   personnel writeback and navigation; no standalone economy or event simulator.
5. Integrate the two-request decision into the broker/HQ flow; review headless at
   1920x1080, 100%, followed by campaign/save and mission evidence.

## Acceptance

- Finish Wayline and discover this arc without a random roll; older completed
  saves receive the same opportunity. Introductory story IDs remain retired.
- Common victory exposes exactly two understandable commitments. Canceling the
  review does not pick one; committing twice cannot switch or combine them.
- Save/reload before choice, after commitment, after defeat, after Retreat, and
  after victory preserves target, branch, availability, losses, and rewards.
- The routes play differently and unlock different recurring work; they are not
  merely different ending text. Neither blocks unrelated sandbox contracts.
- Results distinguish actual rescued people, known withdrawals, and unknown
  fates. Story outcomes do not mutate vanilla planet ownership or industries.
- Finishing one branch yields one completed history, one branch unlock, and no
  offer for the abandoned alternative. Recurring outcomes cannot advance it.
- Exercise no named officer, two-squad, and four-squad forces through documented
  construction fixtures; register with existing evidence selectors. Evidence
  timeouts remain timeouts, not inferred victories.

Open tuning decisions: rescue success threshold, force/support budgets, final
payments, and the two recurring job packages. Decide these during mission work;
they do not weaken the fixed, exclusive branch and visible consequence contract.
