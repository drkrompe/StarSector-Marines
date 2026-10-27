# A Working Ship: dialogue and character notes

Status: ACTIVE — authored script and character reference.

Written: 2026-10-14

Updated: 2026-10-14 — restore five field calls to their recorded wording so existing takes stay valid.

## How to read this script

This is the chronological dialogue register for **A Working Ship**: Zoey's
referral after **A Cold Account**, then **Salvage Rights**, **Cold Start** and
**Working Passage** on Asharu in Corvus: briefing, field calls, debrief and
return-to-work lines, in the order they happen. It is also the character
reference for future writing. A new line should agree with what its speaker knows
at that point, and with what they are willing to say.

`early-operation-nouns.md` owns the mission and settlement rules;
`shepherd-recovery-arc.md` owns arc orientation. The runtime uses localized
dialogue, automatic speaker changes, portraits, Skip and Replay.

The runtime catalog (`wayline_narration.json`) and `strings.json` carry this
script. Six field calls keep the wording of their existing recordings:
`working_passage.underway`, `bend_build`, `militia_hold_0`, `narrows_build`,
`militia_hold_1` and `departure` play recorded takes; every other line is
text-only until a matching recording is approved. Zoey's referral, the Salvage
Rights drop exchange and the retry briefings are catalogued for recording but
have no playback trigger yet.

**PUBLIC** means known to Castle and the captain at that point. **HIDDEN** means
an author-established motive the character need not admit. **TO BE REVEALED**
names the scene where new knowledge becomes available. **UNDECIDED** is a writing
boundary, not a secret with an answer in this document.

Debug exercises identify themselves as exercises and award neither payment nor a
ship. A retry preserves confirmed work and losses; it does not replay the first
conversation as a fresh bargain. Field calls are ordered by mission flow, but
proximity, contact, casualty and hold calls can interleave and happen only when
their actual conditions occur. They are not a mandatory radio playlist.

## Tone and scale

This is a small story, and it should feel like one. Two salvage outfits are
fighting over a wreck in a canyon on a dry world in Hegemony space. Locally it is
life and livelihood. Across the Sector it is an ordinary week. Nobody is saving
anything larger than a camp and a season's income, and nobody talks as though
they were.

The company is new: one ship, a payroll, people it cannot afford to lose. It
sells rifles and judgment by the job. Vale's outfit is Independent in the way most
of the Sector's small crews are: no-one's law in particular, loyal to its own,
neutral when that pays and opportunistic when that pays better. Outsiders are a
means to an end until they have proven otherwise, and a referral is not proof.

Grounded, low-tech realism: hulls are worn and repaired with whatever is to hand,
money is short on both sides of the table, and the Hegemony is a distant,
indifferent weight that would take an interest only if something were worth its
trouble.

**The player must not feel the Shepherd being handed to them.** Morrow starts as
scrap on a cradle and Vale's money. Her usefulness is found because the company
and Vale are trying to solve a defensive problem, not because the script points
at her. Every time she becomes more useful she also becomes more valuable to Vale,
so the closer the company gets to wanting her, the harder she is to get.

## People and their interests

### Tamsin Vale — yardmaster, Basin Reclamation Cooperative

Vale runs a family salvage outfit with a cooperative's name on its permit. Most
of Basin's crews are kin or married into it; the rest have worked beside them long
enough to count. She finds money for parts, haggles with buyers, keeps people
fed and tells families when someone does not come back. Basin has worked this
valley for three cycles, and the camp around the wreck is everything it owns.

She is comfortable hiring violence to get that back. Her grievance does not make
every decision virtuous, nor every other scavenger a pirate: the outfit in her yard
is doing what any crew on Asharu would do if it thought it could get away with it,
Basin included.

She gives practical information freely when it helps the work. Questions about
the hold, her money or her people make her short.

- **PUBLIC, first meeting:** Basin was first into the valley after the wreckfall
  and holds the claim. A rival outfit came in shooting after rumours spread about
  something in the Colossus. Basin pulled most of its people out; the rivals now
  occupy the yard and call it abandoned. Vale wants the yard back.
- **PUBLIC, first meeting:** the Shepherd on the service cradle is another hull
  from the same fall, hauled in to be broken for parts. Her reactor and drone bay
  are the best scrap in the yard outside the Colossus, and Vale had a buyer
  interested in the bay before the attack.
- **PUBLIC, first meeting:** Basin buys its supplies, fuel and heavy machinery
  through Wayline because Wayline is the cheapest hauler working Asharu. Zoey is
  her supplier, not her friend. Buying cheap is a habit with Vale, and the day
  hands later in the arc are the same habit costing her more.
- **TO BE REVEALED, first meeting:** where the rumour may have come from. When the
  hold was opened, Vale put in a Wayline fuel order big enough to lift something
  heavy off-world, which a salvage camp never needs. Weeks later the rivals came.
  The order crossed Wayline's dispatch board while its schedules were being sold
  to pirates; she also talked too freely at Jangala. She suspects both and can
  prove neither. **UNDECIDED** which, if either, it was.
- **HIDDEN:** Jory's crew did not stay in the Colossus because they were trapped.
  They stayed to sit on the hold. Vale let family stay behind to guard the cargo
  rather than pull them out with everyone else, and tells herself it was their
  choice. She never says this; a reader can infer it from how she talks about them.
- **HIDDEN:** Basin is short of money. The company's fees are coming out of what
  should have bought parts and wages, the repair machinery was paid for up front,
  the Wayline account for supplies and fuel is behind, and the sale of Morrow's bay
  was meant to carry the camp through this season. Her offer to pay after the shipment moves is
  real, not a dodge.
- **TO BE REVEALED, after Salvage Rights:** she means to stop paying the company.
  Day hands from the camps down the valley cost a third as much, and with the yard
  back and checkpoints on the roads she believes that is enough. Cold Start is
  meant to be the company's last job.
- **TO BE REVEALED, Cold Start field:** it was not enough. When Harlan's second
  assault comes, the checkpoints break, and Vale spends the drone bay she was
  saving for a buyer to hold her yard. She never quite says Castle was right.
- **TO BE REVEALED, Working Passage negotiation:** she cannot sell a flying Shepherd
  without a Hegemony port registering it, and a registry asking where a recovered
  warship hull came from is a registry asking what else came down in this valley.
  Castle names this. Vale does not concede the point aloud; she concedes the ship.
- **UNDECIDED:** the cargo's contents, legality, buyer and value. It may be an AI
  core, a blueprint cache, something else, or less than Vale thinks. Do not write
  any reading as established truth, and do not wink at one. Her guardedness has
  to support all of them.

**Voice:** short, practical, territorial, tired. Salvage talk: hulls, cuts,
pickings, the yard, the haul road. She names the rival crew by its boss, Harlan,
calls it *the squatters* when the abandonment claim is on her mind, and saves
*roaches* for when she is angry — a couple of times in the whole arc, so it still
stings. Most of the time it is just *they*. Castle never borrows Vale's insults;
she says Harlan's crew or the other crew. She counts in people and money, and she will not
pretend those two never pull against each other. No lectures about scavenger
society, no announced motives, no threats in every reply. Warmth shows in what
she notices and who she asks after, never in instant friendship with the company.

### Castle — company operations, speaking for the captain's interests

Castle arrives with Zoey's message, the survey packet and a proposed security
job, which is about what the player has. She has not met Vale. She can spot gaps
in an offer and press on them; she cannot know what is in a sealed hold, what
Vale can afford or whether a broken ship will fly. She prepares terms for the
captain, who decides whether to deploy.

- **PUBLIC:** the company needs paid work and must look after its people. Castle
  asks where a job ends, and does not sign up for open-ended fights.
- **HIDDEN:** the company has been running on one ship — the Second Wind, itself
  recovered from a wreck — and Castle has wanted a second hull since before Asharu.
  She does not bring it into the first two conversations, and she does not look at
  Morrow as a prize until Morrow has done something.
- **HIDDEN:** she knows what a second hull costs before it earns anything. Morrow
  has to be crewed out of Second Wind's own complement, leaving both ships on
  skeleton watches until the company can hire. She takes the ship anyway.
- **TO BE REVEALED, Salvage Rights drop:** with the boats already going down, she
  asks about the hold directly and backs off when Vale refuses. She does not forget
  she asked.
- **TO BE REVEALED, first meeting:** the company spent the Corvus work tracing
  who sold Wayline's schedules, so Castle cannot wave away Vale's suspicion of
  Wayline and does not try. She separates the company from its former employer
  instead: they took Wayline's contract; now they are taking Vale's.
- **TO BE REVEALED, Cold Start briefing:** she doubts the day hands and asks what
  Harlan's crew wanted with Morrow's bay. She makes the argument once, loses it, and
  accepts being sent home. The company is still on contract until it lifts, and
  when the assault comes she stays without being asked twice.
- **TO BE REVEALED, Working Passage negotiation:** she asks for Morrow, refuses to
  be paid in a share of cargo she cannot see, and gives Vale the Hegemony reason
  as a reason, not a threat. Her leverage is the new work and Vale's blocked road.
  She never claims the company repaired Morrow or supplied Basin's parts.

**Voice:** observant, dry, direct. Curious without being omniscient. She can push
a client hard without humiliating them, and she knows when to stop pushing. Casualty
lines take precedence over pleased ones.

### The captain and company

The captain is the player's role, not a scripted voice. The company is a mercenary
employer with a payroll, equipment and people at risk. Helping Basin and insisting
on payment are compatible. The script offers opportunities and bounded commitments;
it never declares the player's morality, finances or feelings about a casualty bill.

### Jory and the crew in the Colossus

Jory is Vale's cousin. Their crew is sealed inside the Colossus, where the rivals
have not found a way through, and keeps a quiet channel open to Vale. Their reports
are how Vale knows the yard is held, by roughly how many, and later what is massing
on the outer road. Jory is offscreen throughout and never speaks.

They are not the inspection party, the repair crew, the pickets or a rescue
objective. No scene claims the player rescued them, counts them as casualties or
puts them in the player's roster. Their story closes offscreen: once the road is
open, they can walk out.

### Teo and the Basin fitters

Teo is a young Basin fitter, kin to Vale, who gets Morrow's bay answering in Cold
Start and her lift working before Working Passage. He knows her better than anyone
alive, talks the company's crew through her drives, and stays in the valley. He
does not speak on the radio; Vale speaks about him.

Morrow's original crew died or scattered three cycles ago; nobody in this arc
knows or tracks them. **Nobody from Basin leaves with her.** Basin is a clan, and
its people are not for hire to outsiders, least of all as part of a sale. The
company crews Morrow out of its own fleet, thinning Second Wind's watch to do it,
and hires to fill both ships afterwards like any other captain would. The hull
agreement transfers a ship, never people.

### The day hands

Hired guards from the camps down the valley: people who work other crews' wrecks
for a daily wage and will carry a rifle for a better one. They are not Basin and
not soldiers. Vale hires them after Salvage Rights to man checkpoints on the south
haul and quarry roads so she can stop paying the company. They are Cold Start's
outer pickets; Basin's own militia hold the inner line.

They are not cowards and the script never calls them that. They are underpaid
for what comes down those roads, and they know it. Vale treats them as her hire
— she asks the company to cover them and is sorry when they fall — but not as her
people, and nobody phones a family over them in her hearing. After Cold Start
most of them take their pay and go home. Working Passage's road holders are
Basin's own militia.

Inspection technicians, fitters, day hands and militia are different people with
different jobs. Losing a post does not mean everyone there died.

### Zoey Venn — Wayline broker, Basin's supplier

Zoey brokers for the Wayline freight cooperative out of Jangala. Wayline came
close to bankruptcy on Jangala during last year's raids and is recovering, but it is
one logistics outfit among several in Corvus and has lost ground it needs back.
To win it, Wayline hauls supplies, fuel and heavy machinery a little under its
rivals' rates, and that is how it has picked up contracts with the salvage crews
that move from wreck to wreck across Asharu. Basin is one of those accounts:
the ordinary consumables every outfit has to source locally, delivered into the
valley. Vale and Zoey are supplier and customer, and neither would call the
other a friend.

- **PUBLIC, referral:** Basin's yard is occupied, its account has gone quiet, and
  heavy machinery Vale has already paid for is sitting in Wayline's shed with
  nowhere to go. Zoey wants a paying customer back, and says so plainly.
- **PUBLIC, referral:** Wayline is running on thin margins to rebuild, so one
  Asharu account going quiet matters to it more than Zoey lets on. She does not
  plead; she mentions the margins once and moves on.
- **HIDDEN:** Zoey knows Vale's large fuel order crossed Wayline's dispatch board
  while Sable was buying its schedules. She does not know that is how the
  rumour spread, and she does not raise it. Sending a company she trusts is good
  business, and partly settling a debt she cannot prove she owes.
- She speaks once, in the referral. In the chapter conversations Vale and Castle
  talk about her; she does not take part. Her referral is not a guarantee about
  Vale's cargo, credit or intentions.

### The Hegemony and the rival outfit

Basin works under a local recovery permit filed at Jangala. The Hegemony does not
patrol a quarrel between two salvage crews; it would take an interest if something
in this valley looked worth its effort, and Vale would rather it never does. The
script establishes no confiscation law, imminent patrol, political alliance or
hidden deadline — only Vale's well-founded preference for being ignored.

The opposition is one armed rival salvage outfit, **Dace Harlan's crew** — another
family outfit from elsewhere on Asharu, much like Basin. Harlan never appears or
speaks; the name gives Vale and Castle something to call them besides an insult.
Its claim of abandonment is reported by Vale. Its people are opportunists after a rich site who
also saw what a working drone tender could do for whoever holds the yard. They
need not know the manifest any better than Castle does.

## Backstory and reveal order

Three cycles before this arc, two fleets fought in low orbit over Asharu. Hulls
came down across the surface; a Colossus and several of her escorts came down in
this valley. Basin was first in, filed the claim and built a camp and access road
while working the wreck. Other crews worked other wrecks elsewhere on Asharu.
These are authored local events, not a canonical vanilla battle.

One of the escorts was a Shepherd, broken-backed in the fall. Basin hauled her
onto the yard's service cradle to be broken for parts. Her name plate reads
**Morrow**.

Basin has bought its supplies, fuel and heavy machinery through Wayline for
most of that time. Recently, Basin opened a sealed hold in the Colossus, and Vale
put in a Wayline fuel order far beyond a salvage camp's usual draw. Rumours spread,
whether from that order or from Vale's own talk, and an armed rival outfit came in
shooting at shift change. Basin evacuated most of its people; Jory's crew stayed sealed in the
Colossus. The rivals began stripping the yard and, without Basin knowing, began
trying to wake Morrow's drone bay.

| Point in the arc | What Castle and the captain learn | What remains withheld |
|---|---|---|
| Zoey's referral | A Wayline customer, an occupied yard, Wayline's own stake in it | Zoey's worry about her dispatch board |
| Salvage Rights briefing | The wreckfall, Basin's claim, the fuel order and Vale's suspicion of Wayline, Jory's crew, occupied yard, Morrow as scrap | Hold contents, buyer, Basin's finances, how the rumour really spread |
| Salvage Rights drop | Vale will not discuss the hold, and why | Hold contents, permanently |
| Successful inspection | The rivals were trying to power Morrow's bay, not strip it | Why, or whether it could work |
| Salvage Rights debrief | Vale has hired cheaper guards; the next job is the company's last | Whether they will hold |
| Cold Start briefing | Day hands on checkpoints; Teo thinks Morrow's bay could build guns; Vale won't spend it | Whether any builders will answer |
| Cold Start field | Harlan's second assault breaks the checkpoints; Vale spends the bay; a wreck puts up a road gun | Any entitlement to the hull |
| Cold Start debrief | The day hands have gone home; Morrow is worth more to Vale now; the outer road is shut again | What Vale can pay for more |
| Working Passage briefing | Vale cannot pay until the shipment moves; refusal, loan, terms; the company must crew her itself | The manifest, permanently |
| Climb-out and settlement | Morrow joins the fleet on a skeleton crew drawn from Second Wind; upkeep and hiring are the company's | No later cargo reveal is promised |

## 0. The referral

### After A Cold Account — Zoey's introduction

Delivered with the HQ notice *Zoey's introduction to Tamsin Vale*, once Cold
Account's aftermath has been acknowledged. It is business correspondence between
people who have just been through something together, not a celebration and not a
favour between friends. It replaces nothing in Cold Account's own aftermath, and
it does not start a clock: Zoey says outright that the work waits for the company.

**Zoey:** Castle. Something for when the company's ready, and not before. One of our accounts on Asharu has a problem.

**Castle:** Wayline has accounts on Asharu now?

**Zoey:** A few. We haul supplies, fuel and heavy machinery out to the salvage crews there, a little under what the other outfits on Jangala charge. It's how you win customers back after a year like ours.

**Castle:** And this one?

**Zoey:** Basin Reclamation. A salvage crew working a wreck field in the northern canyons. Tamsin Vale runs it. She came to us because we were cheapest, and she counts every credit like it's her last.

**Castle:** What's gone wrong?

**Zoey:** Another salvage crew walked in with guns and took her yard. Her account's gone quiet, and there's a load of heavy machinery she's already paid for sitting in our shed with nowhere to deliver it.

**Castle:** So this is Wayline's problem as much as hers.

**Zoey:** At our rates, I can't afford to lose a paying customer. She'd like her yard back. You'd like to be paid. I don't see anyone losing.

**Castle:** Is she a friend of yours?

**Zoey:** She's a customer. Not a happy one. She'll tell you why herself, if she decides she likes you. The survey's attached. I told her you were good and quiet.

## 1. Salvage Rights

### Before — the first meeting

Castle has read the survey and Zoey's message: a Wayline customer on Asharu, a
rival outfit in the yard, a security job. Vale has Zoey's word on the company and
a grudge against Zoey's freight office. She gives the history because the company
needs it to fight, and stops there. Nobody has mentioned a ship.

**Vale:** Zoey says you're the company that found out who was selling Wayline's schedules.

**Castle:** We were.

**Vale:** Then you'll understand why I'm careful what I say in front of Wayline's people.

**Castle:** We're not Wayline's people. We took their contract. Now we'd be taking yours.

**Vale:** We'll see. You've read the survey, so you've seen the valley. Here's the rest. Three cycles back, two fleets fought it out in low orbit over Asharu. A Colossus and a few of her escorts came down here. We were the first crew in. The claim's ours.

**Castle:** Three cycles is a long time on one wreck.

**Vale:** It's a Colossus. You don't strip one in a season. That fight dropped hulls from here to the southern flats, so every crew on Asharu had its own pickings and nobody bothered with us. Then word got round we'd found something.

**Castle:** Got round how?

**Vale:** When we opened the hold, I put in a fuel order with Wayline. Enough to lift something heavy off-world, and a camp like ours never needs that much. A few weeks later Dace Harlan's crew came in shooting at shift change.

**Castle:** That's about when Wayline's schedules were being sold.

**Vale:** You'd know better than I would. Or it was me, talking too loud in a Jangala bar. I can't prove either. We got most of our people out, and now the roaches are stripping our yard and telling anyone who'll listen it was abandoned.

**Castle:** Most of your people.

**Vale:** My cousin Jory's crew is still inside the Colossus, sealed in where the squatters haven't found a way through. They've kept a quiet channel open. That's how I know the yard's held, and roughly by how many.

**Castle:** This is Hegemony space. Is anyone else going to take an interest?

**Vale:** Our permit's on file at Jangala. The Hegemony doesn't send a patrol over two salvage crews squabbling in a ditch. It'd come quick enough if somebody gave it a reason. So we settle this ourselves, and quietly.

**Castle:** Then tell me where it ends. We're not going into your Colossus, and we're not chasing this outfit across Asharu.

**Vale:** Good. I don't want you in her, and Jory's people will keep out of your way. You take the receiving store and the workshop; my militia follow you in and hold them. Then the service cradle.

**Castle:** What's on the cradle?

**Vale:** A Shepherd. Another hull off that fight. Broke her back coming down; we hauled her in to cut up. Her reactor and drone bay are the best scrap in this yard outside the Colossus, and I had a buyer asking after that bay before any of this. Jory says they've had her panels off for a week.

**Vale:** I want to know what they've taken. Two of my techs on her, with your rifles round them, long enough to find out. Cover them from her south side. Harlan's lot come and go along the south haul road. That's where they'll come back.

### During — yard entry and protected inspection

Operational speech gets shorter once the company is on the ground. These calls
do not reveal unseen enemy positions or send the company into the Colossus.

#### Boats on the way down — the hold question

Cue: `shepherd.salvage.descent` (proposed; no such cue exists yet).

The first boat is committed and descending, and nobody is on the ground. The fleet
takes long enough to put the company down that the briefing's one unasked
question fits here, and it lands harder with the company already committed: Castle
asks while her people are in the air, and Vale answers knowing they are.

This is a four-turn exchange, which the story radio does not support today — each
cue plays one line. It is the conversation the briefing used to end on, moved;
`shepherd.salvage.arrival` follows it. If marines reach the ground before the
exchange has finished, the remaining turns are dropped and the arrival orders
play: an order outranks an argument.

**Castle:** Vale, while we're on the way down. What did you find in that hold?

**Vale:** Salvage. Mine.

**Castle:** My people are about to be shot at over it. I'd like to know what for.

**Vale:** You're being shot at over my yard, my stores and my workshop, and you're paid for every bit of it. Ask Zoey whether I settle. The last time that hold went on anybody's paperwork, I got this. I won't do it twice with people Wayline sent me.

#### Company presence at the yard

Cue: `shepherd.salvage.arrival`.

**Vale:** You're down, Captain. Receiving store first, then the workshop. My militia are behind you. Leave the Colossus to us.

#### Company first comes within sighting range of Morrow

Cue: `shepherd.salvage.morrow_sighted`.

**Vale:** There's the Shepherd. Morrow, by her hull plate. Panels open all down her flank; somebody's been busy. Let's see what's left in her.

#### Receiving store secured

Cue: `shepherd.salvage.workshop`.

**Castle:** Receiving store's secure. Workshop next, Captain. Watch the gaps between those salvage stacks.

#### Workshop secured

Cue: `shepherd.salvage.cradle`.

**Vale:** Workshop's ours. The cradle's just past it. Clear the ground on Morrow's south side for my techs, and keep it clear.

#### Service access secured; inspection party begins its crossing

Cue: `shepherd.salvage.inspection`.

**Vale:** South side's clear. My techs are crossing now. Stay close enough to cover them while they work.

#### Hostiles interrupt the inspection — conditional

Cue: `shepherd.salvage.inspection_contested`.

**Castle:** Hostiles at the cradle. The techs have stopped work. Clear them some room, Captain.

#### Road counterattack warning

Cue: `shepherd.salvage.road_warning`.

**Castle:** Captain, keep an eye on the south haul road. It's their way back in. Don't pull cover off the techs to chase them.

#### Protected inspection completed

Cue: `shepherd.salvage.secured`.

**Vale:** They're done. Her bay's still in her, and the squatters had cable run into it. They weren't cutting her up, Captain. They were trying to wake her. Your fee's released.

#### Inspection party lost — failure branch

Cue: `shepherd.salvage.failed`.

**Castle:** We've lost the inspection party, Captain. Pull back. Nothing more gets done here without another crew.

### After — the yard is Basin's again

The fee is paid and the yard is held, but nothing works. Castle is curious about
the cables; Vale is not interested in what the rivals wanted, only in what she
still owns. The next job is power, not the ship.

**Castle:** Payment's received. Why would a salvage crew want a wrecked drone tender awake?

**Vale:** Same reason anyone wants builders on a yard they mean to keep, I'd guess. Doesn't matter now. The bay's still in her, and she's still mine.

**Castle:** They won't have gone far.

**Vale:** Past the quarry, Jory says, and still going. I've got my yard back and no power to work it. The heavy machinery to fix that is still in Wayline's shed, and they stripped the pump station. Zoey can deliver now there's a yard to deliver to. One more contract: cover my fitters while we get the lights on.

**Castle:** One more?

**Vale:** After that I've got guards coming for the roads. Nothing personal, Castle. You're good, and you're not cheap.

**If no inspection technicians were lost:**

**Castle:** We'll get the company ready. Keep your fitters together until we're there to cover them.

**If inspection technicians were lost, replace that Castle turn:**

**Castle:** You lost people at that cradle. We'll put ours in order before we take another crew out there.

**Vale:** Do. The rig will be on the receiving apron. Come to me when you're ready.

### Withdrawal or failed inspection

No victory conversation or fee. The results account records confirmed company
and technician losses. A later attempt provides a new inspection party; it does
not bring those people back. The briefing keeps the original contract and adds
its recorded technician-loss readout. Jory's crew is not part of that count.

## 2. Cold Start

### Before — the last job

The company is ashore in a retaken, dark yard, on what Vale means to be its last
contract. She has hired day hands for checkpoints on the outer roads and expects
to send the company home once the lights are on. Castle doubts the guards and says
so once. Morrow's bay comes up as Teo's idea, and Vale declines to spend her best
scrap on guns she doesn't think she needs. Nobody plans to build anything.

**Vale:** Wayline's finally dropped the distribution rig on the receiving apron. My fitters take it to the workshop, bring the pump station back and put shore power into the yard. You keep them breathing while they do it. Then you're done.

**Castle:** And the roads?

**Vale:** Checkpoints on the south haul and the quarry road, manned by day hands from the camps down the valley. My own militia hold the inner line.

**Castle:** Day hands.

**Vale:** Armed, paid, and a third of what you cost.

**Castle:** Harlan's crew took this yard off your own people. They'll know what a checkpoint is worth.

**Vale:** Harlan's past the quarry. Jory's watched for three days and they haven't come near.

**Castle:** Your techs said Harlan's crew were trying to wake Morrow's bay. Did anyone ask why?

**Vale:** Teo thinks her builders could put guns up round the yard. Maybe they could. That bay's the most saleable thing I own outside the Colossus, and every hour those drones work is wear on it. I'm not burning it on guns I don't need.

**Castle:** Your yard. Captain, we cover the rig and the pump, and when the lights come on we lift.

**Vale:** Keep fire off my fitters. And if those checkpoints call for help, they're on my payroll. Cover them back to the inner line.

### During — delivery, power and construction

The short Castle opening call is an operational recap for the battle. It does
not replace the briefing conversation.

#### Battle opening — company already ashore

Cue: `briefing`.

**Castle:** Captain, last job on Asharu. Get the rig to the workshop, then cover Vale's fitters at the pump. When the lights come on, we lift.

#### Repair rig waiting on the receiving apron

Cue: `cold_start.convoy_arrived`.

**Vale:** The rig's on the apron, Captain. Stay with it along the workshop road. I paid for every bolt of it.

#### Repair rig delivered to the workshop

Cue: `cold_start.workshop`.

**Vale:** Rig's at the workshop. Now clear the pump station for my fitters. They need to reconnect shore power.

#### Living technicians begin protected pump work

Cue: `cold_start.pump_started`.

**Vale:** They're on the pump connection. Keep that ground covered until they're done.

#### Shore power restored; yard lights and builders available

Cue: `cold_start.power_restored`.

**Vale:** Shore power's on, and Teo says Morrow's bay is answering. Damn the buyer. Select Build Vulcan Turret and put a road gun on clear ground within reach of her bay.

#### First actual road gun completed

Cue: `cold_start.turret_ready`.

**Castle:** Captain, that's a road gun up, built by a wreck on a cradle. It'll hold longer than a checkpoint. Keep the approaches clear while Vale's fitters finish.

#### Road gun established at the inner line

Cue: `cold_start.inner_line_fortified`.

**Vale:** The new gun's covering the inner line. Keep the fitters working, Captain. We've bought them some room.

### During — pressure on the outer line

This is the turn of the mission: Harlan's second assault, coming down roads Vale
had handed to day hands. The company has not lifted yet, and stays. These calls
may interleave with delivery and construction, so none of them may assume power is
on or a gun exists. Each road has its own physical picket. Contact, withdrawal, loss of a post and survivors reaching
an inner hold are separate facts. A well-supported picket need not be overrun.

#### South haul: contact — conditional

Cue: `cold_start.south_haul_contact`.

**Vale:** South haul checkpoint's taking fire. They're day hands, Captain, but they're my hire. Give them a hand if you can.

#### South haul: picket withdrawing — conditional

Cue: `cold_start.south_haul_withdrawing`.

**Vale:** South haul checkpoint's running. Can't say I blame them, for what I'm paying. Cover them back to the inner line.

#### South haul: outer post no longer holding — conditional

Cue: `cold_start.south_haul_overrun`.

**Vale:** South haul checkpoint's gone. So much for proper guards. Watch for anyone coming back, and keep that approach covered.

#### South haul: survivors physically reach the inner hold — conditional

Cue: `cold_start.south_haul_inner_hold`.

**Vale:** The south haul hands have made the inner line. They're staying put, at least. Help them hold it.

#### Quarry road: contact — conditional

Cue: `cold_start.quarry_contact`.

**Vale:** Quarry checkpoint has contact. There's only three of them out there, Captain. Support them if you can.

#### Quarry road: picket withdrawing — conditional

Cue: `cold_start.quarry_withdrawing`.

**Vale:** Quarry checkpoint's falling back. Keep the crossing covered for them.

#### Quarry road: outer post no longer holding — conditional

Cue: `cold_start.quarry_overrun`.

**Vale:** The quarry checkpoint's gone. Keep the inner line covered. There may still be people coming in.

#### Quarry road: survivors physically reach the inner hold — conditional

Cue: `cold_start.quarry_inner_hold`.

**Vale:** The quarry hands are in, on the inner line with my militia. Keep that ground covered.

#### Public approach warning

Cue: `cold_start.approach`.

**Vale:** That's not a probe. Harlan's coming for the yard. Castle, I know you were about to lift. Stay. Keep them off my fitters, and watch any drones that are out. They won't stand up to gunfire.

#### South road party breaks off at the company screen — conditional

Cue: `cold_start.south_entry_denied`.

**Vale:** That party on the south haul road has broken off at your screen. Keep watching the entry.

#### Quarry road party breaks off at the company screen — conditional

Cue: `cold_start.quarry_entry_denied`.

**Vale:** They've broken off at your quarry-road screen. Don't leave the entry open.

#### Covered workshop repairs completed

Cue: `cold_start.complete`.

**Vale:** Workshop's running, and Morrow's bay is drawing steady. That's the job done, Captain. I'll settle your fee.

### During — alternative failure calls

#### Repair crew lost

Cue: `cold_start.crew_lost`.

**Vale:** We've lost the repair crew. Pull back, Captain. What they finished stays finished. I'll have to find other hands.

#### Distribution rig destroyed

Cue: `cold_start.carrier_lost`.

**Vale:** The rig's gone. We can't do this without it. Pull the company back, Captain.

#### Company can no longer protect the work

Cue: `cold_start.company_lost`.

**Vale:** Your company can't cover the work any longer. Break off. We'll pick up from whatever's finished.

#### No builders remain for required construction

Cue: `cold_start.builders_lost`.

**Vale:** No builders left to finish the gun. Break off, Captain. The delivery and the power work will keep.

### After — repair paid; another problem emerges

The cheap plan has failed in front of everyone, and Vale paid for it twice: the
day hands' wages and the company's fee. Both sides have now seen what Morrow can
do, and both reprice her. Castle sees a capability; Vale sees a better sale. The
outer road is shut again. There is still no offer and no promise of the ship.

**Castle:** Captain, did you see how fast those builders put that gun up? Off a wreck we found in pieces.

**Vale:** I saw. Teo had her bay talking before the lights were steady. A Shepherd with a working bay is worth three of her in pieces, so don't look at her like that, Castle. Your fee's sent.

**If there were no company or repair-technician losses:**

**Castle:** And your checkpoints?

**Vale:** Paid off. Most of them have gone home, and I can't say I blame them. Harlan's back on the outer road, and Jory says he's had company since. Finish up with your people, then come and see me. Seems I'm not done with you after all.

**If company personnel or repair technicians were lost, replace those last two turns:**

**Castle:** We'll need time before we take on anything else. We've people to account for.

**Vale:** So do I. Take it. The outer road's still shut, and I won't talk about that over the radio. Come and see me when you can.

### Returning after an unfinished repair

There is no completion fee or success conversation. The deployment readout records
replacement personnel and confirmed losses. With no completed delivery, the
ordinary repair briefing still describes the work ahead; it makes no claim that
the previous attempt never happened.

**Cargo delivered; shore power unfinished — replacement briefing:**

**Vale:** The rig's still at the workshop, but the pump connection isn't finished. I've found another repair party. Cover them while they get shore power in, then we try Morrow's bay for that road gun. Same contract. I've found more day hands for the checkpoints, for whatever that's worth; help them back to the inner line if they have to come in.

**Cargo delivered and shore power restored — replacement briefing:**

**Vale:** The rig's at the workshop and shore power's holding, so we pick up from there. Another repair party's ready. Get that road gun out of Morrow's bay and keep the fitters covered. Same contract. I've found more day hands for the checkpoints, for whatever that's worth; help them back to the inner line if they have to come in.

## 3. Working Passage

### Before — a new agreement

The original two contracts are finished and paid. Vale's problem is the outer
haul road: the shipment cannot move while the rivals and their new friends hold it,
and her militia cannot open it. She cannot pay for that work until the shipment
sells. Castle will not work for a share of cargo she has never seen. Morrow is the
one thing on the table both of them can value, and Vale's first answer is no.

**Vale:** My haulers are loaded and going nowhere. The roaches have dug in on the outer road, and two nights ago another crew's boat came down at the quarry.

**Castle:** So they're waiting for your shipment to move.

**Vale:** They're waiting for it to move without you beside it. I need the road opened to the eastern yard, with positions along it my militia can hold after you've gone.

**Castle:** Positions built by what?

**Vale:** By Morrow. Teo and the fitters have her lift working. She'll hover. She can crawl the road behind you and put her builders down wherever you've cleared.

**Castle:** That's a bigger job than the yard. What does it pay?

**Vale:** A share when the shipment sells.

**Castle:** When it sells. To a buyer we don't know, for cargo you won't describe, out past a crew that's already shown it'll kill for it.

**Vale:** You've had every credit I owed you, on time. Wayline hasn't, so you can guess who I paid first.

**Castle:** We have, and I'd like it to stay that way. Our payroll doesn't wait on your buyer.

**Vale:** Then what do you want?

**Castle:** Morrow.

**Vale:** No.

**Vale:** She's the best thing in this yard that isn't in the Colossus. Her bay was meant to carry this camp through the season, and now she flies, she's worth three times that.

**Castle:** Who are you going to sell her to?

**Vale:** That's my business.

**Castle:** Whoever buys a flying Shepherd has to register her at a Hegemony port. Somebody asks where she came from. Then somebody asks what else came down in this valley.

**Vale:** Is that a threat?

**Castle:** It's a reason. We've no interest in telling anyone anything. A mercenary company coming out of a wreck field with a recovered hull is nobody's news. It's how we got our first ship.

**Vale:** Take her for the job, then. Bring her back when the road's done, and I'll pay you out of the shipment on top.

**Castle:** That's the same promise with a ship attached.

**Vale:** Then hear mine. You get the hull and nothing else. Every spare we've already pulled off her stays here. The guns she builds stay here. Not a credit for the road, and not a bolt out of the Colossus.

**Castle:** Agreed.

**Vale:** And she's not yours until the road's handed to my militia and she's clear of this basin. You don't lift while my people are still walking into position.

**Castle:** They'll have cover all the way up. We hand over each position before we move on. Who's flying her?

**Vale:** Your own people. Nobody from Basin goes with her. Not Teo, not anyone.

**Castle:** I wasn't going to ask.

**Vale:** Good. Teo will walk your crew through her before the drive test. Then he comes home.

**Castle:** We'll put a crew across from Second Wind. Both ships will be running thin until we can hire. Send the terms and I'll put it to the Captain.

**Vale:** I'll send them. Get her out in one piece, Castle. I've watched enough ships come down in this valley.

**Vale:** Morrow's holding a hover. Bring her along the road to the eastern yard. Clear the pump bend and use Build Vulcan Turret on ground her builders can reach, then do the same at the narrows. Cover the work and wait for my militia to take over. Keep the company with her for the drive test. If the drives give out, break off and we'll recover her. The deal stands either way.

### During — escort, construction and handover

Morrow moves with company cover. A build cue is permission and instruction to
begin work, not proof a gun exists. A militia report waits for surviving holders
to reach their actual position; it can arrive after the next phase has opened
and is absent if no holders survive. Physical militia arrival is not a phase
advancement gate. Hidden reserve-entry events remain silent.

#### Morrow actually moves under her own power

Cue: `working_passage.underway`.

**Castle:** Captain, Morrow is moving under her own power. Keep the company ahead of her and clear that pump bend.

#### Pump bend secured; construction available

Cue: `working_passage.bend_build`.

**Vale:** Pump bend clear. Bring Morrow up behind the company and build a road gun. Keep her builders covered, Captain.

#### Pump bend approach warning

Cue: `working_passage.road_team_0`.

**Vale:** Watch the south haul approach while the builders work at the bend, Captain.

#### Pump-bend gun completed; company advances

Cue: `working_passage.narrows`.

**Vale:** There's a gun at the pump bend now. Keep that position covered while you move through the narrows. The haulers will need this road behind you.

#### Surviving militia physically establish the pump-bend hold — conditional; may arrive later

Cue: `working_passage.militia_hold_0`.

**Vale:** My first militia team is in position at the pump bend, Captain. Bring your company forward when you're ready.

#### Quarry-road approach warning

Cue: `working_passage.road_team_1`.

**Vale:** Keep someone on the quarry road while you clear the narrows. That's where the new crew came down.

#### Narrows secured; construction available

Cue: `working_passage.narrows_build`.

**Vale:** Narrows secured. Build the next gun before moving on, Captain. We need the southern approach covered.

#### Rear pump-road warning

Cue: `working_passage.road_team_2`.

**Vale:** Don't leave the pump road open behind you, Captain. We still need that position.

#### Road position ready; eastern yard remains to be cleared

Cue: `working_passage.climb_out`.

**Vale:** The position's ready. Keep the narrows covered and clear the eastern yard for her drive test. Give her room to leave.

#### Surviving militia physically establish the narrows hold — conditional; may arrive later

Cue: `working_passage.militia_hold_1`.

**Vale:** My second militia team is holding the narrows, Captain. Keep the company with Morrow for the climb-out.

#### Morrow reaches the eastern yard; protected drive work begins

Cue: `working_passage.spool`.

**Vale:** She's in the eastern yard. Your crew are spooling her drives, and Teo's talking them through it. Stay with her, Captain; keep the approach clear.

#### Final approach warning

Cue: `working_passage.road_team_3`.

**Vale:** Hold the last approach. Morrow needs that yard clear until she's away.

#### Drive test completed; physical climb-out begins

Cue: `working_passage.departure`.

**Vale:** Drive test complete. Morrow is climbing, Captain. Hold that approach until she's clear of the basin.

#### Morrow physically clears the basin

Cue: `working_passage.complete`.

**Vale:** She's clear of the basin. Registry's yours, Captain. We hauled her in to cut her up. Look at her now.

### During — alternative abort calls

#### Departure aborted

Cue: `working_passage.failed`.

**Vale:** Abort the departure. Pull back, Captain. I'll get Morrow recovered. The deal stands; we'll have to try the road again.

#### Company can no longer provide cover

Cue: `working_passage.company_lost`.

**Vale:** There's not enough of your company left to cover her. Break off. I'll get her recovered. The deal stands.

#### Builders exhausted before construction is finished

Cue: `working_passage.builders_lost`.

**Vale:** Morrow's out of builders. We can't finish the road with this wing. Break off; we'll recover her and fit another.

### After — ship accepted into the fleet

The departure receipt authorizes the transfer. This is a practical handover
between people who have worked together and still do not entirely trust each
other, not a revelation that Vale gave away a wonder-ship. The company has a
second hull, two thin crews and hiring to do. Vale has a road, a shipment she
can finally move, and every one of her own people still in the valley.

**Castle:** Registry confirms it, Captain. Morrow's ours. She's flying on a skeleton crew off Second Wind, and Second Wind's not much better off. We'll need to hire before we ask much of either of them.

**Vale:** Have your crew check her drone-bay filters first. Basin dust gets into everything.

**Castle:** Any chance of hands from the camp?

**Vale:** Try the port at Jangala. Nobody in this valley's leaving it for you.

**If no militia or company personnel were lost:**

**Vale:** Jory's crew can walk out of the Colossus tonight. Safe passage, Castle.

**If company personnel were lost but no militia were lost:**

**Vale:** I know what it cost your people to get her out. I'll leave you to them. Safe passage, Castle.

**If militia were lost, this takes precedence over the other farewells:**

**Vale:** I'd better go. There are families waiting on a call from me, and some of them are mine. Look after her, Castle.

### Returning after an aborted departure

No ship is awarded and the completed repair contract is not replayed. Vale
arranges recovery and another attempt with the same hull. Confirmed losses
remain; replacement militia are other people. The agreed terms stand, so the
negotiation does not repeat.

**Vale:** We still have a deal, Castle. Teo needs time on her drives before we try again. I'll see to it, and I'll send fresh holders. Clear the pump bend and the narrows, cover the building, then hand the positions over. Stay with her through the drive test. When the road's ours and she's clear of the basin, she's yours.

## Continuing the relationship

The cargo is still Basin's business when the arc ends. It does not need a reveal
to justify the deal. Do not add a final wink that confirms illegal goods or a
valuable core or schematic merely to close a mystery this arc has not resolved.

Vale's later optional referral to Dormer on Agreus follows the actual handover.
It goes through Zoey because Zoey is still Basin's supplier: with the road open,
the account is paying again. Whether Vale ever forgives Wayline's dispatch board
is left open, and the referral is a business courtesy, not a reconciliation.
It is another business lead, not a reward owed by the Hegemony or an
obligation for the company to accept the next contract. Its future dialogue
belongs with that arc's own character and knowledge register.

For a new client relationship, establish the same distinctions before writing
the exchange:

- what the captain and Castle were told before the meeting;
- what the client stands to gain, and what they stand to lose;
- what they will not volunteer, and why they don't trust the company with it;
- what service they are actually buying, and where it ends;
- what new event would justify changing those terms, and what each side gives up.

Let a question or counteroffer bring that information out when it becomes
relevant. A client should never hand over more than the situation forces out
of them.
