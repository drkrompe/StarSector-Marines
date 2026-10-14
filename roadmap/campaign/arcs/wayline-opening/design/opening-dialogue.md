# Second Wind to Wayline: opening dialogue

Status: DRAFT — proposed rewrite of the campaign-start and first-contract dialogue, for review before it replaces strings.

Written: 2026-10-14

Updated: 2026-10-14 — owner calls applied: Venn sisters, the licence the company cannot afford, the founding field commander and her twelve come off the Claim (renamed Sabine Hale), "Commander" throughout.

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
apostrophes when implemented. Keys still carry the old name `Mira`; renaming keys
is implementation, not script.

`cerberus-company-start.md` and `first-mission-preparation.md` own the flow these
lines sit in; `wayline-opening-arc.md` owns the arc; `company-view-nouns.md` owns
Castle, Nessa and the founding field commander's canon. **PUBLIC**, **HIDDEN** and
**TO BE REVEALED** mean what they mean in `shepherd-recovery-dialogue.md`.

## Owner calls

1. **Nessa and Mara are sisters.** Castle hears of Wayline through Nessa; the yard
   visit carries the bridge. Players starting at a fallback port never meet Nessa,
   so every line that depends on the relationship is Jangala-only.
2. **The founding field commander came off the Claim, and so did her squad.** She
   commanded the mine's escort section; twelve of them stayed with her. She is
   renamed **Sabine Hale** (proposed) so she cannot be confused with Mara in
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
roster's margin; holds a 5,000-credit yard payment nobody may touch. Talks in
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
Nessa's younger sister. When the cooperative's director, Teodor Prell (proposed
name), left for a post off-world, nobody senior was left who wanted the job, so
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

### Why Wayline (proposal)

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

Seeds between Relief and Dust Ledger, one line each, none of them explained:

| Chapter | Seed |
|---|---|
| Relief | Mara: the depot raiders "knew which night the gate crew was short." |
| Counterattack | Castle, on the depot's recovered office: their dispatch board was the one thing the raiders bothered to take. |
| Open the Junction | Sabine: they were waiting at the junction before our boats were down. Somebody told them we were coming. |
| Thunder Run | Castle, debrief: every ambush on the corridor sat on the loads that were worth something, and skipped the rest. |
| Dust Ledger | Mara reads Wayline's own schedules out of a pirate archive, under Prell's authority. |

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
> calling you Commander. You have 500 credits, a yard payment Castle will not let
> anyone touch, twelve unpaid escorts who stayed, and a name on Jangala.
>
> This start replaces the Galatia tutorial with the Second Wind prologue and
> company preparation.

## 1 — Prologue

Eight illustrated pages: a narration paragraph, then Castle. The present-day
bridge opens it; pages two to four go back to show how the Claim died and how
Second Wind was raised out of it; page five returns to the bridge. **THE WRITING
ON THE WALL** and **RAISING HER** are new pages (title, narration and Castle line
are new strings); the rest replace existing text.

Facts these pages establish: Harker's Claim is a remote asteroid mine, three
weeks' haul from the nearest port, that dug transplutonic ore (the game's
`rare_ore`, refined into Transplutonics) and was worth the trip while the good
seams lasted. Its operator ran it down quietly over years
while telling the workers output was falling, then posted a date when facility
support (air, heat, power) would end. The last carrier left eleven days before
that date with the administrators and the payroll; worker passage was priced in
credits nobody paid in scrip could raise. The Cerberus by the loading locks was
the old escort hull, laid up and stripped for eleven years; Sabine's section had
ridden the carriers since. Tethered at the locks with no shields up and no point
defence running, she spent those years taking the belt's debris one strike at a
time: that is where the sealed bunkrooms, the holed mess, the torn mech bay and
the jammed second boat bay come from. The crew kept a chalk count of the days left on her
hull, and lit her drive with four days on it.

**SECOND WIND**

`openingCompanyPrologueTheAlarmAboveTheHatchHasFinally`
> The hatch alarm stopped an hour ago. Castle still looks up at it whenever the
> drive changes pitch. Someone has wedged a folded shift roster under the
> navigation console to stop it rattling. Harker's Claim is several jumps behind
> you, and nobody aboard has said its name since the first.

`openingCompanyPrologueYouCanLetGoOfTheChair` — Castle
> You can let go of the chair, Commander. She's holding. I sent everyone I could
> spare to get some sleep. They keep asking me where we're headed, and what
> happens when we get there. I told them you'd already picked a direction. Don't
> make a liar of me.

**THE WRITING ON THE WALL** (new page)

New narration
> Harker's Claim was three weeks' haul from the nearest port, and the
> transplutonic ore in its seams was worth every day of it. Then the good seams
> thinned, and slowly the Claim stopped paying. The second shaft was "suspended pending
> survey." The drill rigs went out on the carriers "for refurbishment" and never
> came back. The clinic lost its surgeon to a transfer nobody had applied for.
> Carriers came monthly instead of weekly, and left heavier than they arrived. The
> notice that finally went up in the canteen was two lines long and ended with a
> date.

New — Castle
> I ran the Claim's manifests for nine years. Every month they told us output was
> down, outbound tonnage went up. Spare rigs, reactor stock, the clinic's
> scanner, all listed as ore. They weren't failing, Commander. They were
> packing.

**WHAT WE LEFT BEHIND**

`openingCompanyPrologueTheLastOreCarrierTookTheMine`
> The date was when facility support would end: air, heat, power. The last ore
> carrier left eleven days before it, with the administrators and the payroll
> aboard. Passage for workers was offered, priced in credits nobody paid in scrip
> could raise. The rest of the Claim kept reporting for shifts until the ration
> counter stopped taking company scrip.

`openingCompanyPrologueStillHadAMonthSPayOn` — Castle, `{0}` one of the next two
> Still had a month's pay on my account at the Claim. Kept the chit. Couldn't tell
> you why. {0}What's left in the ship's account is everybody's. Make it last.

`openingCompanyPrologueTheRealMoneySMostlySpokenFor` (yard payment held)
> The real money's spoken for. The yard payment's locked away where nobody can
> borrow against it, and the shuttle parts and the squad's kit are paid.

`openingCompanyPrologueIVePaidForShuttlePartsAnd` (no held payment)
> Shuttle parts and the squad's kit are paid for. The yard money has to come out
> of what's left.

**RAISING HER** (new page)

New narration
> The Cerberus by the loading locks had been the Claim's escort hull until the
> operator decided ore didn't need escorting. For eleven years she was a parts
> bin, tethered at the locks with her shields cold and her point defence
> stripped, and the belt's small rocks found her one at a time. Someone chalked the days left beside her airlock, and someone else kept the
> count honest. You took drive coil from a hauler that hadn't flown since before
> the Claim had a name, pressure seals off the doors of habs nobody would need
> again, and a navigation core out of the administrator's empty office. Sabine's
> section unbolted the point-defence mounts from the gantries and carried them
> aboard by hand. You still have the list of everything you couldn't replace. It
> is longer than the list of what you could.

New — Castle
> Four days left on the chalk when you lit her drive. I watched the whole loading
> dock hold its breath. Nobody asked whether she'd fly, Commander. Not once. They
> asked what to carry next.

**THE DEPARTURE ROSTER**

`openingCompanyPrologueTheDepartureRosterIsInYourHandwriting`
> The departure roster is in your handwriting. Castle's additions run down the
> margin in a smaller hand: fuel, seals, filters, one more person who needed a
> berth, then another. Near the bottom, in block capitals, Sabine Hale has added
> her escort section: fifteen names, two struck through. Nobody held a vote
> about the command chair. While you were checking the last pressure patch,
> someone painted SECOND WIND over the old registry and didn't sign it.

`openingCompanyPrologueYouRememberTheNightTheyShutOff` — Castle
> Remember the night they cut power to the lower habs? You were down there
> running cable out of this wreck. That's when people started bringing me their
> names. Not when we lit the drive. So you can stop looking at me every time
> somebody says "Commander."

**SOMEWHERE TO DOCK**

`openingCompanyPrologueCorvusHasWorkingYardsAndRegularTraffic` (Jangala)
> Corvus has working yards, steady freight and Hegemony patrols crawling across
> the navigation display. Castle scrolls past the public yard listings to a
> private address: Nessa Venn, Jangala. The last message in the thread is years
> old and ends in the middle of an argument. Castle reads it twice before she
> starts a new one.

`openingCompanyPrologueCastleSearchesThePortListingsForSomewhere` (fallback)
> Castle works down the port listings for anywhere that will berth a civilian
> hull and has a yard that can actually do the work. Half of them want a deposit
> just to reply. A published berth and a working yard are worth more than anyone's
> promise.

`openingCompanyPrologueNessaVennWeRanOreTogetherBefore` — Castle (Jangala)
> Nessa Venn. We ran ore together before she settled into the yards on Jangala.
> Once spent three days towing my dead freighter home and swore she'd cut me loose
> at every buoy. Never did. She'll have opinions about this ship. Let me talk
> first.

`openingCompanyPrologueHasAWorkingYardIDonT` — Castle (fallback)
> {0} has a working yard. I don't know a soul there. We ask for a berth and a
> written estimate, and we keep our story short.

`openingCompanyPrologueNoAnswerFromAYardWeCan` — Castle (no port)
> No yard in reach is answering. I'll keep trying. We didn't come all this way to
> live on emergency power.

**A WAY TO EARN**

`openingCompanyPrologueACargoHandSleepsBesideTheBridge`
> A cargo hand sleeps against the bridge hatch with her boots on. The crew take
> turns in the bunks that still hold pressure. In the cargo hold, Sabine's twelve
> sleep in shifts beside their stacked rifles, the way they did on the ore runs.
> Below decks are two wrecked Hermes and compartments you haven't dared put back
> on the power bus. Castle has been reading surface security notices: depots,
> convoy stops, freight roads that need guarding and can't afford anyone
> established.

`openingCompanyPrologueYouSaidWeCouldTakeContractWork` — Castle
> You said once we were clear we'd take contract work. I've been looking. A proper
> company carries a Hegemony review-board licence; the fee alone is more than this
> ship is worth. So we take work from people who can't afford licensed companies
> either. There are plenty of them. Sabine's people haven't been paid in four
> months and they still stand watch. One shuttle. One squad on the roll, kitted
> and fed. Then we see.

**THE FIRST STEP**

`openingCompanyPrologueCastlePutsTheYardEstimateBesideThe`
> Castle lays the yard estimate on top of the old shift roster. For a moment she
> holds it flat, her palm over the operator's crest. Then she slides both aside so
> you can reach the navigation controls.

`openingCompanyPrologueThenWeGetABoatFlyingAnd` — Castle, `{0}` a destination
part then a payment part
> {0}Then we get a boat flying and Sabine's people aboard her. After that...
> well. We might actually be a company, Commander.

`openingCompanyPrologueNessaSentAnEstimateCalledTheJob` (Jangala)
> Nessa sent an estimate. She called the job "ambitious" twice, which beats
> "scrap." Five thousand for the conversion, and she's found room in her stores
> for ours.

`openingCompanyPrologueWeLlGetTheYardAtTo` (fallback)
> The yard at {0} will do the conversion. Five thousand, if they hold to the
> estimate.

`openingCompanyPrologueWeLlFindABerthThenBunks` (no port)
> We find a berth. Then bunks, a workshop, and someone who can tell us which of
> those shuttles is worth saving.

`openingCompanyPrologueIVeKeptThePaymentSafe` / `…WeLlHaveToWatchThatYard`
> The payment's safe. / We'll be watching that yard money.

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

Castle reports the yard visit in the planet's interaction panel.

`openingCompanyYardCMDNessaVennMetUsAtTheBerth` — Castle (Jangala)
> Nessa met us at the berth. Took one look at the patch under the collar and asked
> if I'd done it myself. I said I'd supervised. She remembers what that means.
> Here's her estimate, Commander. She held to it.

**New, Jangala only, after the estimate** — Castle; the Wayline bridge
> Her racks are half empty. Most of her parts come up the Wayline road, and
> raiders have had it shut for a month. Her sister Mara got handed the drivers'
> cooperative when its director left, and she's wearing herself thin keeping them
> from selling the trucks too. I said we'd be looking for work, and that we've no licence. Nessa said Mara can't afford
> anyone who has one. Then she said, "Talk to her when you've got something that
> flies. Not before."

`openingCompanyYardCMDTheYardCanMakeRoomForBunks` — Castle (fallback)
> The yard will clear space for bunks and a workshop. I asked them to leave the
> compartments that still hold pressure alone. Look the estimate over, Commander.
> We only get to do this once.

`openingCompanyYardCMDIVeKeptThisPaymentAsideSince` — Castle
> I've kept this payment apart since we left the Claim. It doesn't touch the
> ship's account. Shuttle parts and the squad's kit are paid as well.

`openingCompanyYardCMDTheYardHasFinishedEighteenMorePlaces` — Castle (fallback
completion)
> The yard's done. Eighteen more berths, a proper command fit and a workshop
> gantry, for twenty-five units of hold we're going to miss. It's permanent work,
> Commander. No taking it back.

`openingCompanyYardCMDNessaSaysSheLlKeepTheOld` — Castle (Jangala completion)
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
> First, the conversion: barracks and a workshop, built into her for good. Five
> thousand credits. {0}Choose Return, dock at {1} and open Marine operations from
> the planet menu; I'll have the yard order waiting. It isn't in the standard
> Refit picker, and Additional Berthing won't do it. Nothing gets designated until
> the yard has done its work. Our prepaid stores will wait in the planetary depot.

`openingCerberusCompanyStartIHaveKeptThatPaymentSeparateFrom`
> That payment is kept apart from our spending money. The yard draws it directly,
> even if the ship's account is empty.

`openingCerberusCompanyStartTheConversionIsInstalledStayDockedAt`
> The conversion is in. Stay docked at {0}, open Marine operations and name our
> Cerberus the company's command ship. While we're docked here, the preparation
> bills draw on our depot stock. Then we repair the first Hermes, settle Sabine's
> appointment and put her section on the roll.

`openingCerberusCompanyStartOurShipIsHomeNowStayDocked`
> She's ours now, Commander, properly. Stay docked while we use the prepaid
> stores: patch the first wrecked Hermes on the Boat Deck, settle Sabine's first
> specialty in Barracks, then muster her squad in the Armory and put it under her.
> I'll take you through each order.

`openingCompanyPreparationGuideCommanderChooseAHomeWithQuartersAnd`
> Commander, pick our home: a ship with quarters and a boat bay. These stores
> cover one shuttle, which can make the trip as often as we need, and kit for one
> full squad. Sabine's appointment is settled, first training point and all.\n\n{0}

`openingCompanyPreparationGuideTheCrewIsPreparingOurQuartersAnd`
> The crew are clearing our quarters and the hangar. When the deck's ready we
> commission our lift, sit down with Sabine and muster her squad.

`openingCompanyPreparationGuideOurHomeIsReadyComeDownTo`
> Home's ready. Come down to the Boat Deck and we'll get our first shuttle flying.

`openingCompanyPreparationGuideSecondWindCameWithTwoWreckedHermes`
> Second Wind came with two wrecked Hermes. We can reach this one; the other's
> behind the sealed second bay. Let's patch this one for the first contracts.

`openingCompanyPreparationGuideGoodThatBoatIsOursWithA`
> Good. That boat's ours: a berth on the deck and a bill that's paid. She can make
> the trip as many times as the squad needs. Plating and drive can wait for money
> we haven't earned yet. Now, Sabine.

`openingCompanyPreparationGuideOurLiftIsAboardComeToBarracks`
> Our lift's aboard. Come to Barracks; Sabine's waiting.

`openingCompanyPreparationGuideWeWillMusterTheSquadOnceOur`
> We muster the squad once the ship, the lift and the field commander are ready.
> Those first.

`openingCompanyPreparationGuideTwelveMarinesThirtySixSuppliesAndTwenty`
> Twelve marines, thirty-six supplies and twenty-four food. That's Sabine's
> section on the company roll: kitted, fed and paid from today. Check the
> highlighted bill and choose Found Line Squad; then we hand them back to her.

`openingCompanyPreparationGuideTheSquadAndItsFieldKitAre`
> They're on the roll and kitted. Back to Barracks to give Sabine her standing
> command. Assigning them costs nothing more.

`openingCompanyPreparationGuideNothingWasSpentByLeavingCommanderOur`
> Leaving didn't cost us anything, Commander. The rest of the shuttle parts and
> squad stores are waiting at {0}. They're held ashore, so I can't draw them from
> up here. Go back, choose Marine operations in the planet menu, and we'll pick up
> where we stopped. Don't buy them twice.

`openingCerberusCompanyStartYourCompanyIsReadyCommanderTheFirst`
> We're a company, Commander. Mara Venn's first contract is waiting in
> Headquarters. Whatever stores we didn't use stay in the depot for when we're
> back.

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
> for our first lift and a full squad. Bring the whole bill aboard before we
> start, and we'll see this through together.

`openingOfficerInductionGuideOurPeopleNeedAWayDownAnd`
> Our people need a way down and a way home. Pick an empty berth and commission
> its shuttle. One boat can ferry the squad in trips; more boats put them down
> together. Keep the squad's stores aside for the next step.

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

**New — Sabine, on assignment** (shown with the assignment step's completion,
founding officer only)
> Twelve present, Commander. Counted. First time anyone's paid them to stand
> watch since the Claim. Give me somewhere to take them.

## 5 — The first job

`openingBarracksCommandMaraVennAnsweredWaylineNeedsItsCheckpoint` — Castle
> Mara Venn answered. Wayline wants its freight checkpoint relieved, and they'll
> pay if we break the assault. She sounds like she hasn't slept in a week. I've put
> our name forward. First job, Commander. Choose Ready for the briefing, or follow
> Mara's directions from HQ. Take Sabine's squad. Let's give Mara a reason to call
> again.

### Briefing and landing (proposal: the introduction becomes the briefing)

Relief splits Mara's words by where the player is. Before launch she is a
stranger asking for help, so the briefing is her introduction: who she is, what
Wayline lost, and what she is asking for. The tactical detail moves into the
mission, spoken over the radio while the one Hermes makes its three trips, when
the player is looking at the apron, the gate and the roads it describes. The
trips take long enough to carry it, and a briefing heard over the ground it
names is easier to follow than one heard before the map exists.

What the player needs to choose squads and lift stays on the briefing screen as
unvoiced text, so nothing that matters is only said once over the radio.

**Briefing, voiced turns** — Mara (replaces the `WaylineArc` introduction and the
recorded `relief` briefing; ordered `briefing.*` turns). Shared by every start
port, so it does not mention Nessa; the sisters are connected only where the
player has met Nessa.

1. > Mara Venn. Castle sent your particulars, and I've read them twice. I broker
   > for Wayline, and since the spring I run it, because our director took a post
   > off-world and somebody had to.
2. > A year ago we ran freighters between worlds. We lost three cargoes in a season
   > and sold the hulls to cover them, so now we're drivers on one planet's roads,
   > pooling what's left so nobody has to sell their truck as well.
3. > Raiders took our depot a month ago. Now they're at our freight
   > checkpoint{location}, and if that falls there's no road left to haul on. The
   > watch holding it is paid from the same collection as your fee. I know half of
   > them from the loading shifts.
4. > Licensed companies cost more than we have. You cost what we have. Help them
   > hold that gate, and you'll be paid on success, agreed in writing. I'd rather
   > you than nothing, and I expect you'd rather us than nothing too. One job at a
   > time, Commander.

**Briefing screen, unvoiced** — the tactical summary
> Land on the rear unloading apron and reinforce the checkpoint gate. Raiders will
> come up the freight and warehouse service roads. Wayline supplies its militia
> and one shuttle. Break the assault and bring as many people home as you can.

**In mission, while the boat shuttles** — one ordered exchange that starts at
launch and plays straight through, a line at a time, while the Hermes goes back
and forth. It is not keyed to individual landings, so it needs no new mission
events and cannot fall out of order. Mara unless marked.

1. > Mara here. Your first boat's headed for the rear unloading apron. Our watch is
   > holding the gate, but not for long on their own.
2. > Sabine: First team goes down and holds the apron. The rest follow as the boat
   > turns round. Nobody walks to that gate alone.
3. > The raiders are coming up the freight and warehouse service roads. That's
   > where the gate gets hit.
4. > Our militia are coming in on the only shuttle we've got. Keep them with your
   > people. They're drivers defending their road.
5. > They knew which night the depot's gate crew was short. I still don't know
   > how. This is where we stop them taking the road as well.
6. > Break the assault and bring as many of our people home as you can, Commander.

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
seed moves out of the introduction into turn 5, where it has room to sit. Every
Mara turn needs a recording; Sabine's is text until she has a voice.

## 6 — First payment

`MissionDebriefConversation` first-contract exchange, after Relief victory.

Castle — receipt
> Payment's in. I counted it twice. Our first paid contract, Commander. Somebody
> paid us to do exactly what we said we'd do.

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

## Proposal — Sabine on the company net

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
Exchanges that are about more than one squad, or about the company as a whole,
are hers to take; single-squad exchanges stay with the callsigns. She is not
bound to any squad's roster name. She speaks only in battles she is actually
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

This needs its own story under the battle-radio model: a fixed ground-commander
actor in the radio catalog, the rule for which exchanges she takes, recordings in
a chosen voice, and a check that the net is unchanged when she is not deployed.
It is not part of the text rewrite. Until then her story lines stay text with her
portrait.

## Implementation notes

- Structural additions: two new prologue pages (eight in all, which changes the
  page count, the page arrays and the `company-prologue` snapshot frames), the
  Jangala-only Wayline paragraph in Nessa's yard visit, Sabine's assignment line,
  her Relief briefing line and her two debrief muster lines. Every other line
  replaces existing text in place.
- Renaming Mira to Sabine changes the officer's minted name, portrait file and
  string keys outright. The game has not launched, so no save carries the old
  name and nothing needs to keep accepting it.
- Mara as Wayline's acting head changes canon that calls her an independent
  broker (`wayline-opening-arc.md`, `early-operation-nouns.md`, and Vale's view of
  her in `shepherd-recovery-dialogue.md`). Prell and the Dust Ledger reveal
  belong in `wayline-corvus-arc.md` and its chapter dialogue.
- Relief's briefing becomes Mara's introduction as ordered `briefing.*` turns,
  and the tactical lines move into the mission as one ordered radio exchange
  started at launch; no new mission events are needed. The briefing screen keeps
  an unvoiced tactical summary.
  Every changed Mara line needs a new recording.
- Sabine's protection changes the standing model in the same commit as the code:
  `personnel-nouns.md` ("grants no immunity from loss") and `company-view-nouns.md`
  ("a surviving officer can carry the unfinished lesson") are rewritten, and the
  induction's fallback to another officer is removed.
- Prologue and lesson lines are text with Castle's portrait; none is recorded
  today. The Relief briefing is recorded in Mara's voice and needs a new take if
  its words change.
- Fallback ports never see Nessa, Corvus copy or the yard's Wayline paragraph, as
  `cerberus-company-start.md` requires.
