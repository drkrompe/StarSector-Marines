# Stranded detachment: the defence report and the pickup

Status: DRAFT

Written: 2026-10-15

Read `contracts-nouns.md` (stationing, response, recovery, settlement, laws 4–7
and 11), `meta-progression.md` ("Defending the polity: the vanilla seam"),
`stationed-ground-defence.md` (the absent settlement this builds on),
`personnel-nouns.md` (assignment, WIA/MIA/KIA), `company-view-nouns.md` (the HQ
situation board and intel terminal) and `living-world-nouns.md` (the Chronicle)
before implementing this story.

## Goal

A stationed Garrison now fights vanilla raids while the player is elsewhere,
and an unanswered defence settles from what vanilla did: held, held with
losses, or overrun. Two things are missing on the player's side of that.

1. **The player is never told.** The settlement writes casualties onto the
   roster and moves the contract, and nothing reports it. A squad comes back
   thinner with no account of why, and the company's stationed strength never
   reads back as something that mattered.
2. **An overrun garrison has nowhere to be.** Today an overrun fails the
   assignment and releases the survivors on the same day. The captain is
   restored to active duty and the squads return to the company as if they
   had flown home. A garrison that was beaten on a planet the employer no
   longer controls is not at the company's disposal the next morning.

After this story the company hears about every absent defence through its
own report stream, and an overrun garrison is **gone to ground**: its
survivors are still the company's, still reserved, and waiting on a planet
for somebody to fetch them. The fetch is a fly-by pickup through the planet
interaction, not a ground battle. Waiting has a cost.

Under the growth rule in `meta-progression.md`: produces Capability (the
survivors return) and consumes Reach (a trip the player chose to make) and
time; first reachable at Stage 2 with the first Garrison; counter-pressure is
the attrition clock on survivors left too long.

## Vocabulary

This story amends existing nouns rather than adding parallel ones. On ship,
the **Recovery** and **stranded detachment** entries, and law 7, fold into
`contracts-nouns.md`; the defence report folds into the intel terminal entry
in `company-view-nouns.md`.

- **Recovery** (`contracts-nouns.md`) is today the system-generated follow-on
  to an employer-breached stationing contract. It widens to the follow-on to
  a stationing assignment whose survivors cannot simply come home: employer
  breach, or a garrison overrun in the player's absence. Recovery still is not
  a patron-offered work type and pays nothing.
- A **stranded detachment** is the named personnel a recovery is about: the
  surviving squads and bound captain of the parent assignment, still reserved
  by it (law 4), on the parent's market. It is not a new roster or a new
  status; it is the parent assignment held open past its settlement, exactly
  as a defaulted contract already holds its detachment until extraction
  resolves.
- A recovery is **resolved** one of two ways, chosen by why it exists:
  - **Extraction**, the existing fought `EXTRACTION` mission, for employer
    breach. Unchanged by this story.
  - **Pickup**, new: the player's fleet reaches the market and collects the
    detachment through the planet interaction. No battle runs.
- A **defence report** is a company-stream Chronicle fact written when a
  vanilla-triggered Garrison defence settles without the player. It is a
  learned record of the settlement, not a second settlement store.

## Shape

### The defence report

`AbsentDefenceResolution` settles exactly once per event key. On that same
settlement it appends one Chronicle row of a new company-report event type,
carrying what the settlement already knows:

- the market, the attacker faction, and the campaign day;
- the graded outcome (held, held with losses, overrun);
- the marines struck, split by fate (WIA, KIA, MIA), and the living seats
  left;
- the detachment's stationed contribution against the raid's ground strength,
  which is the number that shows the retainer bought something.

The HQ intel terminal shows it in the company operations stream with
confirmed confidence, the way a Silent Colony result already appears there.
Its detail names the struck marines; the roster remains the authority on
their current status, so the report reads names at write time and never
re-derives them.

A won fought defence and a held absent one both deserve a line, but the
fought one already has a debrief. This story reports **absent** settlements
only; a fought defence keeps its debrief and adds nothing here.

A defence whose raid never landed (`applyHeld`) still writes a report: "the
raid turned back" is the cheapest evidence the stationed strength worked.

### Gone to ground

On an overrun, `AbsentDefenceResolution` stops releasing the assignment.
Instead:

1. Casualties are written as today (the same seeded marines).
2. The contract settles failed as today, with the same employer consequence.
3. The surviving squads and the bound captain **stay reserved** by the parent
   assignment. The captain stays `GARRISONED`; no "recalled" note is written.
4. A recovery row is created with the parent as its source, marked for
   resolution by pickup, with a **recovery deadline** on the campaign clock.

If every stationed marine fell, nothing is stranded and no recovery is
created: the assignment releases as today and the captain's fate follows the
existing overrun path.

The situation board leads with the recovery as an obligation, with its
deadline, the way it leads with a pending response. The defence report and the
obligation are separate: one is history, the other is owed work.

### The pickup

While a pickup recovery is live, the defended market's planet interaction
offers to collect the detachment. Choosing it:

- releases the parent assignment through the existing `releaseStationing`
  path, so the squads and captain return through the one ownership boundary
  that already exists;
- settles the recovery completed;
- writes a short company report ("detachment recovered from X") and a line on
  the captain's record.

There is no ground battle and no mission launch. The risk is the trip:
reaching the market past whatever now holds it. If the market's owner is
hostile to the player, the vanilla interaction and its fleets apply as they
would to any approach; this story adds no encounter of its own.

The option requires the player fleet to be at the market and nothing else.
Lift capacity is not modelled; the company's own boats carry its own people.

### Waiting has a cost

A pickup recovery is not free to postpone. On each fixed interval after the
overrun, a seeded share of the stranded survivors is lost: chosen from the
parent's event key and the interval index, so a replayed day strikes the same
people, and moved to MIA (never KIA: nobody saw them die). The captain is
struck last.

When the deadline passes, the recovery settles failed through the existing
`failStationingExtraction` path: every remaining survivor goes MIA, the
captain is injured and released with a note on their record, and the
company report says the detachment was not recovered.

Proposal for the first pass, not a number the docs own: a 30-day deadline and
one loss per stranded squad every 10 days.

## Laws this story adds or restates

- A stranded detachment's personnel return only through `releaseStationing`
  or fail only through `failStationingExtraction`, both run by the recovery's
  own settlement. No screen, report, or interaction moves them otherwise
  (`contracts-nouns.md` law 4).
- The pickup and its attrition never write market ownership, stability,
  industries, or fleets. They move only the company's own personnel
  (`meta-progression.md` seam laws).
- A recovery settles exactly once. Attrition is keyed by event and interval
  and must be idempotent on a replayed day, like absent casualties already are.
- A defence report is appended once per settled event key and is never
  rewritten; a later recovery writes its own report rather than editing it
  (Chronicle is learned and immutable).

## Slices

1. **Defence report.** New Chronicle company-report event type; append on
   every absent settlement; HQ terminal formatting. Unit tests: one report per
   event key under replay, fields match the settlement.
2. **Gone to ground.** Overrun keeps the assignment reserved and creates the
   pickup recovery with its deadline; all-dead overrun still releases. Tests
   on the resolution with a fake roster.
3. **Pickup.** Planet-interaction option, release, settlement and report.
   Situation-board obligation row.
4. **Attrition and expiry.** Seeded interval losses and the deadline failure.
   Tests pin same-people-on-replay and the captain-last order.
5. **Live pass.** One overrun with the player elsewhere: read the report,
   watch a loss land, fly to the market and collect the survivors; a second
   left to expire.

## Acceptance

- Every absent vanilla-triggered defence produces exactly one company report
  in the HQ intel terminal naming the outcome, the struck marines, and the
  stationed contribution.
- An overrun with survivors leaves them reserved, shows a recovery obligation
  with its deadline, and returns them through the planet interaction without a
  battle.
- Left alone, the detachment loses the same marines on every replay, and the
  remainder go MIA at the deadline.
- A breach-spawned recovery still resolves through its fought extraction.

## Out of scope

- Changing breach recovery to a pickup. A patron that defaults is still on
  the ground and may obstruct the company; whether breach should also be a
  pickup is an open question below.
- Reports for fought defences, which already debrief.
- Prisoner exchange or ransom for MIA marines.
- A ground mission to rescue a stranded detachment. The direction is a
  fly-by pickup.

## Open questions

- **Market changes hands while stationed.** Nothing today ends a Garrison
  when its market is captured (by vanilla or by Nexerelin, whose invasions the
  raid seam deliberately ignores). Capture is the clearest case for "gone to
  ground" and probably belongs to this story's recovery, triggered by an
  ownership change rather than a raid. Leaning: yes, as a sixth slice, once
  the pickup exists.
- **Breach as pickup.** Should a breached contract on a market the patron
  still holds use the pickup too, keeping the fought extraction only when the
  patron is hostile? Leaning: decide after the live pass.
- **Posting overrun.** A posting on the player's own market cannot really
  strand anyone the player can't reach. Leaning: postings release as today.
- **Named casualties in the report.** If a report names twelve people, the
  detail grows long. Leaning: name the dead and missing, count the wounded.
