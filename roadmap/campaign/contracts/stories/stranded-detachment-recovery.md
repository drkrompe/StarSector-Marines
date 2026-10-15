# Stranded detachment: the defence report and the pickup

Status: IN PROGRESS

Written: 2026-10-15

Updated: 2026-10-15 — slice 1 (defence report) shipped; slices 2–7 remain.

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
- A recovery is **resolved** one of two ways, chosen by whether the market
  will let the company leave:
  - **Pickup**, new and the default: the player's fleet reaches the market
    and collects the detachment through the planet interaction. No battle
    runs. Every overrun and capture recovery is a pickup, and so is a breach
    recovery while the market's owner is not hostile to the player.
  - **Extraction**, the existing fought `EXTRACTION` mission, only for a
    breach recovery on a market whose owner is hostile to the player: the
    detachment has to be fought out.
- A **defence report** is a company-stream Chronicle fact written when a
  vanilla-triggered Garrison defence settles without the player. It is a
  learned record of the settlement, not a second settlement store.

## Shape

### The defence report

`AbsentDefenceResolution` settles exactly once per event key. On that same
settlement it appends one Chronicle row of a new company-report event type,
carrying what the settlement already knows:

- **where:** the market, its system, and its planet conditions;
- **who:** the attacker faction and the raid's shape (pirate-base raid,
  punitive expedition, or fleet-group raid), and the stationed squads and
  captain by name;
- **when:** the day the raid landed or turned back;
- **what:** the graded outcome (held, held with losses, overrun); the killed
  and missing by name and the wounded as a count; the living seats left;
- **how much it mattered:** the detachment's stationed contribution against
  the raid's ground strength, the number that shows the retainer bought
  something.

The HQ intel terminal shows it in the company operations stream with
confirmed confidence, the way a Silent Colony result already appears there.
Names are frozen into the row when it is written. The roster stays the
authority on each marine's current status, and the report never re-derives
who was struck.

**The account.** Above the facts the report reads as a short after-action
account in the comms officer's voice, so a reader gets a story and not just a
table. The account is composed when the row is written, seeded from the event
key, and frozen into the row, so it reads the same forever and on every
replay. It has two parts:

- **The spine** is the facts above, written as prose: "Pirate raiders from
  the Askonia belt came down on Sindria's ore terminal on day 214. Sergeant
  Vey's section held the landing road for six hours before the line broke."
  Every claim in the spine is a persisted fact.
- **The colour** is drawn from seeded pools keyed by outcome × raid shape ×
  planet condition: weather, terrain, the hour, how the comms traffic
  sounded, the mood of the survivors' check-in. A hot world gets heat shimmer
  over the wire; a toxic one gets rebreathers; a held defence ends on relief,
  an overrun one on static.

`narrative-nouns.md` law 1 sets the boundary: narrative never invents
motive, hidden knowledge, or off-screen consequence. Colour is framing in the
`themes.md` sense, and it is bounded:

- it never credits a named marine with a deed or a kill;
- it never states a consequence for the market, the employer, or the
  attacker;
- it never contradicts a fact in the spine.

"The survivors report the raiders came in low out of the dust" is colour.
"Corporal Hale killed four of them at the gate" is an invented fact and is
out. Whether named-marine anecdotes should ever be allowed is a narrative
decision, and it would need an amendment to that law.

The pools live in `strings.json` beside the existing HQ news strings, so the
voice can be written and localised without code changes.

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

**Breach recovery uses the pickup too**, unless the market's owner is hostile
to the player when the recovery is created. Hostility is read then and frozen
on the recovery, so a later diplomatic swing cannot turn a promised pickup
into a battle or the reverse. This matters most to Nexerelin players, where
houses fall and markets change hands far more often than in vanilla: breach
becomes routine there, and routine recovery should not demand a fight.

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

1. **Defence report.** *Shipped.* New Chronicle company-report event type; append on
   every absent settlement; the frozen account (spine plus seeded colour);
   HQ terminal formatting. Unit tests: one report per event key under
   replay, fields match the settlement, the same seed composes the same
   account, and no colour line names a marine.
2. **Gone to ground.** Overrun keeps the assignment reserved and creates the
   pickup recovery with its deadline; all-dead overrun still releases. Tests
   on the resolution with a fake roster.
3. **Pickup.** Planet-interaction option, release, settlement and report.
   Situation-board obligation row.
4. **Attrition and expiry.** Seeded interval losses and the deadline failure.
   Tests pin same-people-on-replay and the captain-last order.
5. **Captured market.** A Garrison whose market changes owner (vanilla
   transfer, decivilisation, or a Nexerelin invasion the raid seam ignores)
   strands its detachment through the same recovery and pickup, with a
   defence report whose spine says the market fell rather than that a raid
   landed. Detection reads market ownership on the daily tick; no Nexerelin
   class is referenced. A posting on the player's own market that the player
   loses follows the same path.
6. **Breach as pickup.** A breach recovery on a market not hostile to the
   player is created as a pickup, with the hostility read frozen on the row;
   a hostile one keeps the fought extraction. Tests pin both branches and
   that a later relation change does not flip the frozen choice.
7. **Live pass. Not a gate.** The owner will not be running live passes on
   this soon, so the story ships on unit and headless evidence. When a live
   pass does happen: one overrun with the player elsewhere, the report read,
   a loss landing, the survivors collected; a second left to expire; and,
   with Nexerelin installed, one Garrison market invaded.

## Decided while shipping

- **The raid's shape is not in the report yet.** The pending defence payload
  carries the attacker faction but not whether the raid was a pirate-base
  raid, a punitive expedition or a fleet group. Adding it means carrying the
  shape from the threat reader through the arming trigger onto the payload.
- **"When" is the day the defence armed**, not a landing day. Vanilla does not
  record when a raid touched down; the day the raid reached its action stage
  is the nearest frozen fact.
- **The colour pools are pipe-separated lines in `strings.json`**, one key per
  climate and per outcome. A pool line takes no arguments, which is what keeps
  it from naming anybody; a unit test reads the shipped file and enforces it.

## Acceptance

- Every absent vanilla-triggered defence produces exactly one company report
  in the HQ intel terminal with its frozen account: where, who, when and
  what, the killed and missing by name, the wounded as a count, and the
  stationed contribution.
- A Garrison whose market changes hands strands its survivors and offers
  the same pickup.
- An overrun with survivors leaves them reserved, shows a recovery obligation
  with its deadline, and returns them through the planet interaction without a
  battle.
- Left alone, the detachment loses the same marines on every replay, and the
  remainder go MIA at the deadline.
- A breach recovery resolves by pickup on a market not hostile to the
  player, and through its fought extraction on a hostile one.
- An overrun posting releases its survivors as today; a posting whose market
  the player loses strands them.

## Out of scope

- Reports for fought defences, which already debrief.
- Prisoner exchange or ransom for MIA marines.
- A ground mission to rescue a stranded detachment. The direction is a
  fly-by pickup.

## Decided

- **Posting overrun releases.** A posting is on the player's own market, so
  there is nobody between the company and its people: an overrun posting
  releases its survivors as today. A posting whose market the player loses
  strands them like any capture (slice 5).
