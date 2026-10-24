# Second Wind to Wayline: opening dialogue

Status: SHIPPED — the campaign-start and first-contract dialogue register, and the schedule hints through Dust Ledger; applied to the game. Sabine relays company-wide orders on the net; her other ground reports wait on battle evidence.

Written: 2026-10-14

Updated: 2026-10-20 — Prologue rewritten to seven pages; every forward button is the Commander's line, and the old eighth page folds into A WAY TO SURVIVE.

## How to read this script

This is the chronological dialogue register for a new Second Wind company, from
the new-game choice to Mara Venn's first payment after **Hold Until Relieved**. It
covers the prologue, Nessa's yard, Castle's preparation lessons, Sabine's
induction, Castle's hand-off, Mara's introduction, the Relief briefing and the
first-payment exchange. Counterattack, Secondhand Wings and the recurring jobs are
out of scope; Sabine's combat callouts are a proposal at the end.

Each line names the string key (or the code that builds it) that it would replace.
Mechanics stated in a line are unchanged: costs, stores, berths, rewards and button
names are the ones the game uses today. Lines that format a value keep their
`{0}` placeholders; strings passed through `Strings.format` double their
apostrophes.

`cerberus-company-start.md` and `first-mission-preparation.md` own the flow these
lines sit in; `wayline-opening-arc.md` owns the arc; `company-view-nouns.md` owns
Castle, Nessa and the founding field commander's canon. **PUBLIC**, **HIDDEN** and
**TO BE REVEALED** mean what they mean in `shepherd-recovery-dialogue.md`.

## Owner calls

1. **Nessa and Mara are sisters.** Castle hears of Wayline through Nessa; the yard
   visit carries the bridge. There is one story for every start port (owner,
   2026-10-20): a start that lands at a fallback port because Jangala is
   unavailable still meets Nessa, and Castle's, Nessa's and Mara's lines say
   Jangala. Only course, objective, Intel and mission text that names a place
   uses the port actually chosen.
2. **The founding field commander came off the Claim, and so did her squad.** She
   commanded the mine's escort section; twelve of them stayed with her. She is
   renamed **Sabine Hale** so she cannot be confused with Mara in
   passing, and she speaks at story beats instead of being a portrait.
3. **The player is "Commander"** in every rewritten or new line, Castle's and
   Mara's alike. Yard staff already say Commander.
4. **The company is unlicensed.** It cannot pay the Hegemony mercenary review
   board's licence. Licensed companies will not work for what Wayline can pay;
   unlicensed ones are what independents can afford. This is authored backstory,
   not a game mechanic or prerequisite.
5. **Sabine cannot be killed.** She carries too much of the long story and the
   company's radio voice to be a replaceable officer. The one loss path today, a
   wiped-out defeat that kills her squad's officer six times in ten, wounds her
   instead (out of action for 45 days, the existing wipe injury). No other
   officer gains this protection.
6. **No contract figures in dialogue.** Lines never name a negotiated fee. They
   say "credits", "the fee" or "paid", and the amount lives only in the offer's
   own terms, so a balance pass never has to rewrite or re-record a line.
7. **The first job is a delivery.** The company's visitor papers on Jangala
   forbid work, so its cover is a shipper's escort: Mara buys the company's own
   ore dust (the cargo declared in the arrival hail), delivered to the checkpoint
   gate, and Sabine's twelve ride up with it on a Wayline truck. The squad starts
   Relief on the ground beside that truck; no company boat flies.

## What was wrong

- **Castle stops being a person once the prologue ends.** The preparation Castle
  says "These stores cover one reusable shuttle and a fully equipped squad." That
  is a tooltip read aloud. The instructions stay; the speaker is the woman who
  kept the departure roster.
- **Nothing at Jangala knows Wayline exists**, and then a stranger offers the
  first paid job.
- **Two Venns, no explanation.**
- **The field commander arrives from nowhere**, and says nothing.
- **Mara's briefing is a work order in her voice.**

## Threads and parties

**The Commander** — PUBLIC: ran cable out of the Cerberus the night the lower habs
went dark; the crew chose them before the drive lit. Not a soldier, not a
merchant, not licensed.

**Castle** — PUBLIC: ore freighter hand, ran ore with Nessa; kept the departure
roster's margin; holds a 4,862-credit yard payment nobody may touch. Talks in
counts and understatement; protective by bookkeeping. HIDDEN: the yard payment
is the last of her own savings; the "month's pay on my account" chit is the
same money.

**The Harker crew** — PUBLIC: miners, cargo hands and their families; minimum
crew plus five. They kept the ship running. Castle will not make them fight.

**Sabine Hale and her twelve** — PUBLIC: Sabine commanded the Claim's escort
section on the ore runs. When the operator stopped paying for escorts, twelve of
her people stayed with her rather than take passage out, and they came aboard
Second Wind with their rifles and nothing else. They have not been paid in four
months. Sabine gives clear orders, keeps a counted muster and does the counting
herself. The founding squad's bill is their kit, stores and places on the
company roll; the people are already aboard. HIDDEN: she blames herself for the
two of her section who did not make the departure roster. UNDECIDED: the twelve's
names and voices.

**Nessa Venn** — PUBLIC: yard forewoman on Jangala; towed Castle's dead freighter
home and threatened to cut her loose at every buoy. Generous in deeds, stingy
with words. Her yard runs on parts that come up the Wayline road. Mara's older
sister.

**Mara Venn** — PUBLIC: Wayline's broker, and since the spring its acting head;
Nessa's younger sister. When the cooperative's director, Teodor Prell, left for a post off-world, nobody senior was left who wanted the job, so
the youngest person in the office who could read the books got it. She still
does the brokering because nobody else can. That is the overwork. HIDDEN: Relief's fee is most of what is left in the
cooperative's security fund; if the checkpoint falls, Wayline sells out to a
bigger hauler. She hires an unlicensed company because it is the only kind she
can afford, and she knows Castle knows that. TO BE REVEALED: the fund's state, at
Counterattack.

**Wayline's drivers and the checkpoint watch** — PUBLIC from Mara's introduction:
Wayline used to run freighters between worlds. It lost cargoes to interception,
sold its hulls to cover them, and is down to drivers hauling on one planet's
roads. They pool what is left so nobody has to sell their truck as well. The
watch is paid from the same collection as the company's fee. Later arcs, where
Wayline hauls to Asharu again, are Wayline flying again: the recovery the
company buys it.

### Why Wayline

The opening needs a reason the raiders are on Wayline's road rather than someone
else's, and Corvus Account already supplies the deep one: Sable Wake is buying
Wayline's schedules. This proposal makes that the cause of everything the
opening shows, and lets the player feel it long before anyone names it.

- **PUBLIC — the soft target.** Wayline is the cheapest, worst-protected freight
  outfit in reach: no licensed security, no ships left to run with, a watch made
  of drivers. Anyone would say that is why. It is true, and it is not the whole
  answer.
- **HIDDEN — they always know.** The interceptions that cost Wayline its hulls
  happened on its own sailings. The depot fell on the night the gate crew was
  short. Raiders arrive where Wayline will be, when it will be there. Somebody is
  selling the schedules, and they were selling them before the ships were sold.
- **TO BE REVEALED — Dust Ledger.** The raid on Sable Wake's dispatch archive
  turns up Wayline's schedules among the papers, released under Prell's own
  dispatch authority, the last of them dated the week he left. Mara learns her
  predecessor sold the cooperative he built, and she cannot work out why. Nothing
  in Corvus answers that. Prell's new post, and whoever Sable Wake answers to,
  is a thread left open for a later arc to pick up.
- **Prell, as the player meets him.** Only through others, and only well: Mara
  quotes his rules, the drivers miss him, and his signature is on the depot's
  old paperwork. Nobody suspects him, which is what makes the reveal land. He
  never appears in person in this arc.

Hints between Relief and Dust Ledger, one line each, none of them explained.
After Relief they sit in Mara's written victory message, the one place every
chapter has; she relays what Castle and Sabine saw rather than borrowing their
voices:

| Chapter | Where | Hint |
|---|---|---|
| Relief | Radio, on the way to the gate | "They knew which night the depot's gate crew was short. I still don't know how." |
| Counterattack | Victory message | They left the strongroom alone and took the dispatch board, every route and every schedule on it. |
| Open the Junction | Victory message | Sabine says they were dug in before your boats were down. Somebody told them we were coming. |
| Thunder Run | Victory message | Castle's report says every ambush sat on the loads worth something and let the rest go by. |
| Dust Ledger | Victory message | The archive holds Wayline's own schedules, released under Teodor Prell's dispatch authority, the last batch dated the week he left. |

## 0 — New game

`rules.csv` `smCerberusChoice` tooltip:

> Leave Harker's Claim aboard Second Wind, a Cerberus held together by the people
> who flew her out. No licence, no patron, five hundred credits. Build a mercenary
> company from nothing.

`rules.csv` `smCerberusSelected` text:

> The operator's last ore carrier left Harker's Claim with the administrators,
> the payroll and the good medical stores. Your people left later, in a Cerberus
> the mine had spent eleven years stripping for parts. Someone painted SECOND WIND
> over the registry. Somewhere between the locks and the jump point, they started
> calling you Commander. You have 473 credits, a yard payment Castle will not let
> anyone touch, twelve unpaid escorts who stayed, and a name on Jangala.
>
> This start replaces the Galatia tutorial with the Second Wind prologue and
> company preparation.

## 1 — Prologue

Seven illustrated pages (owner, 2026-10-20): a narration paragraph, then Castle,
then the Commander's answer, which is the page's forward button. There is no
"Continue": every page turns on a line the player says. The present-day bridge
opens it; pages two to four go back to show how the Claim died and how Second Wind
was raised out of it; page five returns to the bridge, and six and seven carry
the ship to its berth. The last answer, "Take us to {0}.", is the one line that
names the port the start actually chose; the story and Castle's voice say
Jangala everywhere.

Facts these pages establish: Harker's Claim is a remote asteroid mine, three
weeks' haul from the nearest port, that dug transplutonic ore (the game's
`rare_ore`, refined into Transplutonics) and was worth the trip while the good
seams lasted. Its operator ran it down quietly over years, then posted a date
when facility support (air, heat, power) would end. The last carrier left eleven
days before that date with the administrators and the payroll; worker passage
was priced in credits nobody paid in scrip could raise. Castle signed for every
carrier and knew what they carried. The Cerberus had been the Claim's escort
until a raid holed her reactor shroud; the operator stripped her best guns and
moored her out in the belt, where years of drifting grit warped her frame and
left her drive rough and hungry for spares: that is where the sealed bunkrooms,
the holed mess, the torn mech bay and the jammed second boat bay come from. The
crew towed her in with an ore tug, gave her the lower habs' air plant to seal
her, and lit her drive on the third try, with five days left on the chalk count
beside her airlock. They set out for Jangala from the start, on the strength of
Castle's one contact there, Nessa Venn at Greywater Down. Everyone aboard holds
worthless company scrip; the real credits Castle quietly saved over the years are
what pay for the yard, and nobody else knows how little that is. Forty-five
people are aboard.

**SECOND WIND** (`second-wind`)

`openingCompanyPrologueTheAlarmAboveTheHatchHasFinally`
> The hatch alarm stopped an hour ago. Castle still looks up at it whenever the
> drive changes pitch. Someone has wedged a folded shift roster under the
> navigation console to stop it rattling. Harker's Claim is several jumps behind
> you, and nobody aboard has said its name since the first.

`openingCompanyPrologueYouCanLetGoOfTheChair` — Castle
> You can let go of the chair, Commander. She's holding. I sent everyone I could
> spare to get some sleep. Half of them are in the galley instead, asking each
> other what Jangala's like. None of them have been past the Claim's beacon. I'm
> the only one aboard who's seen it, and that was a long time ago.

`openingCompanyPrologueThenGoAndGetSomeSleepYourself` — Commander
> Then go and get some sleep yourself.

**THE WRITING ON THE WALL** (`claim`)

`openingCompanyPrologueHarkerSClaimWasThreeWeeksHaul`
> Harker's Claim was three weeks' haul from the nearest port, and the
> transplutonic ore in its seams was worth every day of it. Then the good seams
> thinned, and slowly the Claim stopped paying. The second shaft was "suspended
> pending survey." The drill rigs went out on the carriers "for refurbishment" and
> never came back. The clinic lost its surgeon to a transfer nobody had applied
> for. Carriers came monthly instead of weekly, and left heavier than they
> arrived. The notice that finally went up in the canteen was two lines long and
> ended with a date.

`openingCompanyPrologueISignedForEveryOneOfThoseCarriers` — Castle
> I signed for every one of those carriers, Commander. Nine years of manifests,
> and I knew a reactor stack from a crate of ore. I told myself it wasn't mine to
> ask. That's the part I keep coming back to.

`openingCompanyPrologueYouWerenTTheOneWhoLeft` — Commander
> You weren't the one who left.

**WHAT WE LEFT BEHIND** (`last-carrier`)

`openingCompanyPrologueTheLastOreCarrierTookTheMine`
> The date was when facility support would end: air, heat, power. The last ore
> carrier left eleven days before it, with the administrators and the payroll
> aboard. Passage for workers was offered, priced in credits nobody paid in scrip
> could raise. The rest of the Claim kept reporting for shifts until the ration
> counter stopped taking company scrip.

`openingCompanyPrologueEveryoneAboardHasAPocketfulOfScrip` — Castle
> Everyone aboard has a pocketful of scrip, and it spends at one counter in the
> sector, and that counter's dark. Over the years I kept back a little in real
> credits, the kind that spend anywhere. Not much. It's what gets us a yard.
> Nobody else knows how little it is.

`openingCompanyPrologueThenItStaysBetweenUsUntilWeLand` — Commander
> Then it stays between us until we land.

**RAISING HER** (`raising-her`)

`openingCompanyPrologueTheCerberusHadBeenTheClaimSEscort`
> The Cerberus had been the Claim's escort until the run she came home with her
> reactor shroud holed and half her crew in the clinic. Raiders, the report said,
> and never said whose. Fixing her would have cost more than she was worth, so
> the operator stripped her best guns, moored her out in the belt and stopped
> thinking about her. Years of drifting grit did the rest: her frame never sat
> true again, her drive ran rough, and everything aboard ate spares. You towed
> her in to the locks with an ore tug. The first time you lit her drive, the
> coils blew and took the inspection lamps with them. The second time, nothing at
> all. The days chalked beside her airlock went from twelve to five while you
> pulled coil out of a hauler that hadn't flown since before the Claim had a
> name.

`openingCompanyPrologueWeGaveHerTheLowerHabsAirPlant` — Castle
> We gave her the lower habs' air plant to get her sealed. After that there was
> no going back down. If she hadn't lit on the third try, I'd have been standing
> in the dark with everyone asking me what next.

`openingCompanyPrologueSheLit` — Commander
> She lit.

**THE DEPARTURE ROSTER** (`departure-roster`)

`openingCompanyPrologueTheDepartureRosterIsInYourHandwriting`
> The departure roster is in your handwriting. Castle's additions run down the
> margin in a smaller hand: fuel, seals, filters, one more person who needed a
> berth, then another. Near the bottom, in block capitals, Sabine Hale has added
> her escort section: fifteen names, two struck through. While you were checking
> the last pressure patch, someone painted SECOND WIND over the old registry and
> didn't sign it.

`openingCompanyPrologueSabineDidnTAskIfThereWasRoom` — Castle
> Sabine didn't ask if there was room. She asked where her people should stand.
> The two she struck off were down the lower shaft when it went dark. She hasn't
> said their names since, and I haven't asked.

`openingCompanyPrologueThenNobodyElseGetsLeft` — Commander
> Then nobody else gets left.

**SOMEWHERE TO DOCK** (`somewhere-to-dock`)

`openingCompanyPrologueCorvusFillsTheNavigationDisplay`
> Corvus fills the navigation display: working yards, steady freight, Hegemony
> patrols crawling along the lanes. You plotted for it before the Claim was out of
> sight, on the strength of one name. Now the relay finally answers, and Castle
> opens a thread that is years old and ends in the middle of an argument: Nessa
> Venn, Jangala.

`openingCompanyPrologueAnOldFriendIOweHerMore` — Castle (arrival cinematic ahead)
> An old friend. I owe her more than she'll let me say. If she's still at
> Greywater Down, she'll find us a cradle. If she isn't, we're a long way from
> anyone else who owes me anything. I'll tell you the rest on the way down.

`openingCompanyPrologueNessaVennWeRanOreTogetherBefore` — Castle (no arrival
cinematic, so the memory is told here)
> Nessa Venn. We ran ore together before she settled into the yards on Jangala.
> Once spent three days towing my dead freighter home and swore she'd cut me loose
> at every buoy. Never did. If she's still at Greywater Down, she'll find us a
> cradle. If she isn't, we're a long way from anyone else who owes me anything.

`openingCompanyPrologueSendIt` — Commander
> Send it.

**A WAY TO SURVIVE** (`cargo-billets`)

`openingCompanyPrologueACargoHandSleepsAgainstTheBridgeHatch`
> A cargo hand sleeps against the bridge hatch with her boots on. The crew take
> turns in the bunks that still hold pressure. In the hold, Sabine's twelve sleep
> in shifts beside their stacked rifles. Below decks are two wrecked Hermes
> shuttles and compartments you haven't dared put back on the power bus.

`openingCompanyPrologueNessaAnsweredSheSHeldACradle` — Castle
> Nessa answered. She's held a cradle for us, and she'll do the work for what
> I've saved: bunks and a proper fit, nothing more. After that we're forty-five
> people in a yard with an empty galley. We'll need work, Commander, and soon.

`openingCompanyPrologueTakeUsTo` — Commander, `{0}` the start port; plots the
course and closes the prologue (`openingCompanyPrologueTakeCommand` when no port
is reachable at all)
> Take us to {0}.

The strip under the picture reads `openingCompanyPrologueCrewAboardSpendableCredits`
("Crew {0} / 45 aboard / {1} spendable credits"), then the held-payment line and
the course objective.

### Ship survey: where the damage came from

Castle's room survey (`openingShipCondition…`) describes damage the player can
inspect and repair. These lines give it a cause.

`openingShipConditionSheSBeenThroughHardServiceThe`
> Eleven years of the belt's small rocks. The pitting stays, but this room is
> safe to use.

`openingShipConditionTheseTwoBunkroomsTookTheSameHit`
> One strike holed both of these bunkrooms while she was still tied up at the
> Claim. Racks are crushed and the pressure seals are gone. The yard can reopen
> both as one job.

`openingShipConditionTheMessTookAHitWeAre`
> A rock came through the mess years before we ever lit her. We're feeding
> everyone field rations in the working quarters, and they deserve better. The
> yard can seal the breach and put the galley and tables right.

`openingShipConditionThereIsRoomForAFullLance`
> There's room for a full lance, but the belt has been through the pressure skin
> and all four gantries are wrecked. Nobody goes through that hatch until a
> recovery crew makes it safe.

`openingShipConditionASecondBoatBelongsHereOnceThe`
> A second boat belongs here. Debris jammed the pressure door and bent the launch
> rails; once the yard makes them safe, we earn the space back.

## 2 — Jangala yard

After the arrival cinematic the founding conversion plays as a four-page storybook
on the prologue's screen (`CompanyYardScreen`). It sets up why the company goes to
work: the bill, the runway it leaves, the road up, and the cover story. Page 1's
Next pays and installs; after that the pages are a straight click-through. There
is no Skip or "Return to port" on these pages, and Back is off on pages 1 and 2, so
the bill cannot be reached again once it is paid. Each Next button carries the
Commander's reply. Second Wind's conversion plays all four pages, with Nessa, at
whichever port the start chose.

The figures (owner, 2026-10-20) are stated in days of runway, not rescaled. The
story's daily board-and-berth burn for the forty-five aboard is about 21.34
credits, which is close to vanilla's own monthly crew and marine bill. Story only:
nothing charges it.

| Figure | Value |
|---|---|
| Ship's account at the start | 473 (twenty-two days) |
| Nessa's estimate | 4,986: materials 3,300, yard time 1,562, pressure test 124 |
| Castle's chits (the founding reserve) | 4,862, nine years of her share |
| Shortfall | 124, the pressure test, which Nessa waives (option R1), so the reserve pays the whole order and the protected start converts on an empty account |

**Page 1 — WHAT IT COSTS** (`yard-chits`)
> Nessa's estimate comes through to the ship's slate as one page. Materials, 3,300.
> Yard time, 1,562. Pressure test, 124. Total, 4,986. It buys bunks where the
> cargo lashings were, a proper command fit and a workshop gantry. It does not
> open the sealed rooms, and it does not fix either of the Hermes shuttles.
>
> Castle sets credit chits on the counter one at a time. One from her locker. One
> from the bottom of the med kit. One that has been sewn into a jacket lining
> since the Claim. The last she holds for a moment before she puts it down.

Castle:
> Four thousand, eight hundred and sixty-two. Nine years of my share, and some of
> it was meant for other people. It's a hundred and twenty-four short.
>
> I told Nessa. She looked at the pressure test line for a while, then said she'd
> be standing in the compartment for it anyway.

Commander (Next): *Pay her. Install the conversion.*

Without the reserve, the chits paragraph is dropped and Castle says the estimate
comes out of the ship's account (`openingYardCashDialogue`).

**Page 2 — TWENTY-TWO DAYS** (`yard-tally`)
> The lashings are gone from the port-side space. Steel bunks stand in their place,
> and someone has already put a pair of boots under a rack. Forty-five people
> sleep aboard tonight: the Harker crew and their families, Sabine and her twelve,
> and you. The yard bills the berth by the day.

Castle:
> Here's the arithmetic, Commander. Feeding forty-five and keeping Nessa's berth
> comes to about twenty-one credits a day. The ship's account holds four hundred
> and seventy-three.
>
> That's twenty-two days. After that we're living on a ship nobody is paying to
> keep docked.

Commander (Next): *We'll need to make a living somehow.*

The figures box reads the live account and works the days out from it.

**Page 3 — THE ROAD UP** (`yard-road`)
> Nessa's racks are half empty. Most of her parts come up the Wayline road from the
> highland depots, and raiders have held that road for a month. The drivers'
> cooperative that runs it has lost its director, a third of its trucks and most of
> its nerve.

Castle:
> I've got a lead on something that actually pays. I don't like it.
>
> Nessa's sister Mara got handed the cooperative when the director left. She's
> keeping the drivers from selling their trucks, and she needs guards on that
> road. She can't afford anyone with a licence, so she'd take us.
>
> It's a gamble, Commander. Those raiders have been killing drivers for a month.
> If it goes wrong we come back short of people, with a ship we still can't feed
> and nobody left who'd hire us. If we sit here, we lose the berth in twenty-two
> days anyway.
>
> I told Nessa we'd be looking for work. I didn't tell her our papers say we can't
> do any.

Commander (Next): *Then we roll the dice. Find us a way in.*

**Page 4 — ORE DUST** (`yard-ore-dust`)

Castle:
> Our papers say visitors. Visitors berth, eat and spend. They don't work, not for
> anybody on Jangala.
>
> But a shipper can see its own cargo delivered, right to the buyer's door. We
> told Approach we were carrying ore dust, and we are. So we sell it to Mara,
> delivery guaranteed, and Sabine's people ride with it to make sure it arrives.
> How much dust it takes to make that true is between us and the buyer. Mara
> isn't going to weigh it.
>
> It isn't quite legal, Commander. But Sabine's people haven't been paid in four
> months, and I'd rather explain this to a customs officer than explain that to
> them.

Commander (Next): *Then let's go deliver some ore dust.*

The last page's objective line points at Marine operations and the prepaid depot
(`openingYardNextStep`).

### Planet-panel yard

Any later conversion, and the planet menu's own yard entry, still report in the
interaction panel:

`openingCompanyYardCMDNessaVennMetUsAtTheBerth` — Castle (Second Wind)
> Nessa met us at the berth. Took one look at the patch under the collar and asked
> if I'd done it myself. I said I'd supervised. She remembers what that means.
> Here's her estimate, Commander. She held to it.

`openingCompanyYardCMDTheYardCanMakeRoomForBunks` — Castle (any other Cerberus)
> The yard will clear space for bunks and a workshop. I asked them to leave the
> compartments that still hold pressure alone. Look the estimate over, Commander.
> We only get to do this once.

`openingCompanyYardCMDIVeKeptThisPaymentAsideSince` — Castle
> I've kept this payment apart since we left the Claim. It doesn't touch the
> ship's account. Shuttle parts and the squad's kit are paid as well.

`openingCompanyYardCMDTheYardHasFinishedEighteenMorePlaces` — Castle (every
completion)
> The yard's done. Eighteen more berths, a proper command fit and a workshop
> gantry, for twenty-five units of hold we're going to miss. It's permanent work,
> Commander. No taking it back.

`openingCompanyYardCMDNessaSaysSheLlKeepTheOld` — Castle (Second Wind completion)
> Nessa's hanging the old pressure seals on her office wall. Says I have a
> reputation to keep up. She stayed for the pressure test herself and left it off
> the bill. Don't mention it. She'll deny it.

`openingCompanyYardCMDOurPrepaidPreparationStoresAreInThis` — Castle
> Our prepaid stores are in this planet's depot, and they only reach the workshop
> while we're docked here. Open Marine operations and name the converted Cerberus
> our command ship. I'll walk you through the rest.

## 3 — Castle's preparation

Castle's lesson panel. Instructions are unchanged; the speaker is the same woman
who kept the roster.

`openingCerberusCompanyStartFirstWeNeedMercenaryCompanyConversionOur`
> First, the conversion: barracks and a workshop, built into her for good. The
> yard has the estimate. {0}Choose Return, dock at {1} and open Marine operations from
> the planet menu; I'll have the yard order waiting. It isn't in the standard
> Refit picker, and Additional Berthing won't do it. Nothing gets designated until
> the yard has done its work. Our prepaid stores will wait in the planetary depot.

`openingCerberusCompanyStartIHaveKeptThatPaymentSeparateFrom`
> My chits pay for it, not the ship's account. The yard takes them even if the
> account is empty.

`openingCerberusCompanyStartTheConversionIsInstalledStayDockedAt`
> The conversion is in. Stay docked at {0}, open Marine operations and name our
> Cerberus the company's command ship. While we're docked here, the preparation
> bills draw on our depot stock. Then we settle Sabine's appointment and put her
> section on the roll.

`openingCerberusCompanyStartOurShipIsHomeNowStayDocked`
> She's ours now, Commander, properly. Stay docked while we use the prepaid
> stores: settle Sabine's first specialty in Barracks, then muster her squad in
> the Armory and put it under her. I'll take you through each order.

`openingCompanyPreparationGuideCommanderChooseAHomeWithQuartersAnd`
> Commander, pick our home: a ship with quarters and a boat bay. These stores
> kit one full squad now and patch one shuttle after Mara's job. Sabine's
> appointment is settled, first training point and all.\n\n{0}

`openingCompanyPreparationGuideTheCrewIsPreparingOurQuartersAnd`
> The crew are clearing our quarters. When they're done we sit down with Sabine
> and muster her squad.

`openingCompanyPreparationGuideSecondWindCameWithTwoWreckedHermes`
> Second Wind came with two wrecked Hermes. We can reach this one; the other's
> behind the sealed second bay. Let's patch this one for the depot job.

`openingCompanyPreparationGuideOurLiftIsAboardComeToBarracks`
> Home's ready. Come to Barracks; Sabine's waiting.

`openingCompanyPreparationGuideTheBoatsCanWait`
> The boats can wait. Mara's truck takes us up to the checkpoint; we patch a Hermes
> after that job. Sabine first.

`openingCompanyPreparationGuideWeWillMusterTheSquadOnceOur`
> We muster the squad once the ship and the field commander are ready.
> Those first.

`openingCompanyPreparationGuideTwelveMarinesThirtySixSuppliesAndTwenty`
> Twelve marines, thirty-six supplies and twenty-four food. That's Sabine's
> section on the company roll: kitted, fed and paid from today. Check the
> highlighted bill and choose Found Line Squad; then we hand them back to her.

`openingCompanyPreparationGuideTheSquadAndItsFieldKitAre`
> They're on the roll and kitted. Back to Barracks to give Sabine her standing
> command. Assigning them costs nothing more.

`openingCompanyPreparationGuideNothingWasSpentByLeavingCommanderOur`
> Leaving didn't cost us anything, Commander. The rest of our prepaid stores are
> waiting at {0}. They're held ashore, so I can't draw them from
> up here. Go back, choose Marine operations in the planet menu, and we'll pick up
> where we stopped. Don't buy them twice.

`openingCerberusCompanyStartYourCompanyIsReadyCommanderTheFirst`
> We're a company, Commander, as far as anyone's asking. Mara's signed for our
> ore dust; her delivery order is waiting in Headquarters. Whatever stores we
> didn't use stay in the depot for when we're back.

### First lift (after Relief)

Relief needs no boat, so the first Hermes is repaired in Castle's first-lift
lesson between Relief and Counterattack, the first ferry job. The Boat Deck
steps reuse the repair and commission lines above.

`lessonFirstLiftTitle` / `lessonFirstLiftHandoff`
> OUR FIRST LIFT
>
> No truck takes us into Mara's depot, Commander. That job needs a boat. The
> prepaid Hermes parts are ours; come to the Boat Deck and we'll get her flying.

`openingCompanyPreparationGuideTheHermesPartsAreStillWaitingAt`
> The Hermes parts are still waiting at {0}, Commander. They're held ashore, so I
> can't draw them from up here. Go back, choose Marine operations in the planet
> menu, and we'll get her flying.

Counterattack's briefing, with no airworthy boat, adds: "The depot is the
company's first ferry job. Repair our first Hermes on the Boat Deck."

The conversion line names no figure; the yard page states it. It says "the
yard", not "Nessa".

## 4 — Sabine

`openingBarracksCommandMiraHaleFoundingFieldCommanderAFormer` — dossier
> Sabine Hale / Founding field commander. Commanded the escort section on Harker's
> Claim's ore runs. When the operator stopped paying for escorts, twelve of her
> people stayed with her, and all thirteen came aboard Second Wind. She wants clear
> orders and a counted muster, and she does the counting herself. Her specialty,
> promotions and service record grow through your decisions.

Sabine always survives to finish her own induction, so these lines can speak
about her directly.

`openingOfficerInductionGuideFirstCommanderChooseAShipWithQuarters`
> First, Commander, a ship with quarters and a boat bay. I've counted the stores
> for a full squad, and for our first lift after Mara's job. Bring the whole bill
> aboard before we start, and we'll see this through together.

`openingOfficerInductionGuideOurPeopleNeedAWayDownAnd`
> Our people need a way down and a way home. Pick an empty berth and commission
> its shuttle. One boat can ferry the squad in trips; more boats put them down
> together.

`openingOfficerInductionGuideCommanderWillLeadOurFirstFormationYou`
> Sabine leads our first formation, Commander. She's been bringing those twelve
> home since the ore runs; now she does it for us. You set the course, she brings
> our people through it. Open her portrait on the left. Let's give her a proper
> start.

`openingOfficerInductionGuideSheHasOneTrainingPointCoverProtects`
> She has one training point. Cover keeps her troops alive under fire; Recovery
> Planning gets our wounded back on their feet; Salvage Stewardship brings more
> home from the field. Read all three, then choose. It's permanent. Her next
> promotion earns another.

`openingOfficerInductionGuideNowSheNeedsAFormationRaiseA`
> Now her people need to be ours on paper. Raise a squad in the Armory, then come
> back here. Twelve marines, thirty-six supplies and twenty-four food buy their
> field kit and stores. Check the bill, then put their names on the roll.

`openingOfficerInductionGuideGoodSheHasASpecialtyAndPeople`
> Good. She has a specialty and people to lead. At briefing, put her squad on the
> mission. Every officer who goes earns their own service record. Bring them home
> and we'll build on what they learn.

**New — Sabine, on assignment** (Castle relays it in the lesson's final step,
since the lesson panel speaks only with Castle's portrait)
> Twelve present, Commander. Counted. First time anyone's paid them to stand
> watch since the Claim. Give me somewhere to take them.

## 5 — The first job

`openingBarracksCommandMaraVennAnsweredWaylineNeedsItsCheckpoint` — Castle
> Mara Venn answered. She'll take our ore dust, delivered to Wayline's freight
> checkpoint, and the checkpoint's under attack. Her truck's waiting to carry it
> up the road, and Sabine's squad rides with it. She sounds like she hasn't slept
> in a week. First job, Commander. Choose Ready for the briefing, or follow Mara's
> directions from HQ. Let's give Mara a reason to call again.

### Briefing and landing: the introduction is the briefing

Relief splits Mara's words by where the player is. Before launch she is a
stranger asking for help, so the briefing is her introduction: who she is, what
Wayline lost, and what she is asking for. The tactical detail moves into the
mission, spoken over the radio while the squad walks from Wayline's truck to the
gate, when the player is looking at the apron, the gate and the roads it
describes. The walk takes long enough to carry it, and a briefing heard over the
ground it names is easier to follow than one heard before the map exists.

What the player needs to choose squads stays on the briefing screen as unvoiced
text, so nothing that matters is only said once over the radio.

**Briefing, voiced turns** — Mara (replaces the `WaylineArc` introduction and the
recorded `relief` briefing; ordered `briefing.*` turns). Shared by every start
port, so it does not mention Nessa; the sisters are connected only where the
player has met Nessa.

1. > Castle sent your particulars, and I've read them twice. I broker
   > for Wayline, and since the spring I run it, because our director took a post
   > off-world and somebody had to.
2. > A year ago we ran freighters between worlds. We lost three cargoes in a season
   > and sold the hulls to cover them, so now we're drivers on one planet's roads,
   > pooling what's left so nobody has to sell their truck as well.
3. > Raiders took our depot a month ago. Now they're at our freight
   > checkpoint{location}, and if that falls there's no road left to haul on. The
   > watch holding it is paid from the same collection as your fee. I know half of
   > them from the loading shifts.
4. > Licensed companies cost more than we have. You cost what we have. On paper
   > you're escorting a delivery to my checkpoint, and you're paid when it
   > arrives. In practice, help my watch hold that gate. One job at a time,
   > Commander.

**Briefing, Sabine** (recorded in her voice, after Mara's four turns)
> We're off the truck behind the gate. Two teams, one on each road in. Get on the
> wall and let them come to us.

The cover story is said once, before the fight (Mara's turn 4), and paid off once
after it (Castle's receipt). Nobody in the fight talks about the dust (owner,
2026-10-20).

This was first written into the mission radio, but a recorded line takes over
from a text-only one at once, so a text line in the middle of Mara's exchange
would have flashed and vanished. In the briefing, turns play one after another.

**Briefing screen, unvoiced** — the tactical summary
> Your squad starts beside Wayline's truck behind the checkpoint. Reinforce the
> checkpoint gate. Raiders will come up the freight and warehouse service roads.
> Wayline brings in its militia by shuttle. Break the assault and bring as many
> people home as you can.

**In mission, while the company moves up** — one ordered exchange that starts at
launch and plays straight through, a line at a time, while the squad walks from
the truck to the gate. It is not keyed to individual arrivals, so it cannot fall
out of order. All Mara.

1. > Mara here. Your people are off the truck and behind the gate. Our watch is
   > holding, but not for long on their own.
2. > The raiders are coming up the freight and warehouse service roads. That's
   > where the gate gets hit.
3. > Our militia are coming in on the only shuttle we've got. Keep them with your
   > people. They're drivers defending their road.
4. > They knew which night the depot's gate crew was short. I still don't know
   > how. This is where we stop them taking the road as well.
5. > Break the assault and bring as many of our people home as you can, Commander.

The beats tied to what actually happens keep firing on their own events, after or
between these turns:

| Beat | Line |
|---|---|
| `checkpoint_reached` | You've reached the checkpoint. Hold together, and watch the approaches. We need this gate standing when it's over. |
| `militia_line_weakened` | The checkpoint watch is taking heavy losses. Get support to the line before it breaks. |
| `militia_line_lost` | We've lost the checkpoint's own watch. Finish this, Commander. Bring the rest of our people home. |

The old `player_landed` and `employer_landed` lines are folded into the exchange
and retired. An urgent event line (the militia breaking) interrupts the
exchange, and the exchange resumes after it rather than being lost. The depot
hint moves out of the introduction into turn 4, where it has room to sit.

## 6 — First payment

`MissionDebriefConversation` first-contract exchange, after Relief victory.

Castle — receipt
> Payment's in. I counted it twice. Mara paid for one sack of ore dust, delivered.
> Our first paid job, Commander. Somebody paid us to do exactly what we said we'd
> do.

**New** Sabine — muster, no losses
> All twelve back aboard. Counted twice.

**New** Sabine — muster, with losses
> I've counted twice. The roll's shorter. I'll give Castle the names.

Mara — names
> The drivers asked who answered the call. I gave them your company's name.
> They'll remember it. It's the only good news they've had this month.

Castle — cost, with losses
> We've got names to take off the roll. I'll see to those before I count what we
> can afford.

Castle — cost, no losses
> Crew pay and supplies first. It's good to hold a receipt that isn't for
> something we had to sell.

Mara — next
> Get your people in order. When you're ready, come back and we'll talk about the
> depot. I'd like this to be the start of something. Wayline needs it to be.

## Sabine on the company net

Sabine joins the existing net as one more voice in its rotation, not as a
parallel system. What sets her apart is her authority, not her mechanics. The
net already has two levels, and she fills the gap between them:

| Voice | Where | Speaks for |
|---|---|---|
| Castle | CIC, aboard ship | The company. Coordinates from above, assigns work, relays what the ship can see. |
| **Sabine** | **On the ground** | **The company on the ground.** Runs control across squads: who moves, who covers, where they gather. |
| Squad callsigns (Kestrel, Hammer, Viper, Jackal, Ox, Rivet) | On the ground | One squad. Report their own situation and acknowledge. |

So her lines are the ones a ground commander says: she directs one squad in
relation to another, reports the company's state up to Castle, and answers
Castle for the whole landing. She never reports a single squad's contact the way
a callsign does, and she never speaks for the ship.

She takes part in the same exchanges under the same rules: sampled from
accepted assignments, sharing the voice budget, rotating with the callsign cast.
Her exchanges are **alternatives, not replacements**. Castle keeps talking to
squads directly, exactly as today; an exchange about more than one squad, or
about the company as a whole, may be voiced either way, Castle to a callsign or
Castle through Sabine, and the rotation picks between them. Single-squad
contact reports stay with the callsigns. She is not bound to any squad's roster name. She speaks only in battles she is actually
deployed in; when she is wounded or left aboard, the net runs exactly as it does
today. Examples:

| Exchange | Lines |
|---|---|
| Castle assigns, Sabine directs | **Castle:** Sabine, CIC. The east compound's yours. **Sabine:** Copy, Castle. Hammer, take the gate. Viper, cover them from the wall. |
| Landing complete | **Sabine:** Castle, Sabine. Everyone's down and counted. Moving off the pad. |
| Consolidate after capture | **Sabine:** Compound's ours. Kestrel, sit on it. Everyone else, on me. |
| Withdrawal | **Sabine:** Castle, we're pulling back to the pad. Ox, you're last out. Nobody gets left. |
| Company thinning | **Sabine:** Castle, we're thin down here. I can hold. I can't push. |
| Squads rejoined | **Castle:** Sabine, how's the line? **Sabine:** Back together. Ready when you are. |

The first row is in the game, as the battle-radio model's field-command relay
(`battle-radio-nouns.md`): when several squads are retasked together and she
leads the deployment, Castle may call her instead of a callsign, read the same
order, and hear her hand it to one callsign, who answers. Withdrawals use the
last-out handoff from the fourth row. The other rows each need a confirmed
battle fact the net does not observe yet (company landed, compound captured,
company strength, squads reassembled).

Owner call 6 reaches past the opening. Counterattack, Open the Junction and
Thunder Run no longer name their fees in Mara's voice. Thunder Run's per-carrier
schedule is a term the player needs, so it follows her briefing as a plain
**Terms:** line rather than as something she says.

## Still open

- Sabine's landing, capture, strength and regroup reports, each once the net
  can observe its fact.

Sabine has a voice (`art-source/audio/sabine/README.md`): calm and low, without
the Eastern European accent the brief asked for, which the recording model does
not keep. Her Relief briefing turn is recorded. Her muster lines and the
assignment line stay written, because the exchanges they sit in are written
correspondence with no recordings for anyone.
