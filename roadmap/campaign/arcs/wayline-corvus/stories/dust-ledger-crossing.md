# The Dust Ledger — Saltwell Cross

Status: IN PROGRESS — wired into the campaign: the chapter now plays Saltwell Cross with the quiet phase,
burning ledger, broker and side objectives. A second, more detailed map pass and difficulty tuning remain.
Written: 2026-10-25

Read `wayline-corvus-arc.md`, `early-operation-nouns.md` (Wayline across Corvus)
and `shepherd-recovery-dialogue.md` (Asharu's register) first. Map work also
reads `mapgen-nouns.md` (authored roads, terrain, damage) and `elevation-nouns.md`;
civilian work reads `staffed-buildings.md` and `ai-nouns.md`.

## Why

The shipped chapter is a generic procedural Raid: a seeded BALANCED town with a
garrison compound grown beside it, an administrative building relabelled
"Dispatch archive", off-map reinforcements, and one briefing sentence for why
Asharu does not care. This story replaces it with an authored map and a mission
that plays differently from every other Raid: the company walks in openly,
chooses its moment, and pays for preparation with time.

The chapter's place in the arc is unchanged: Asharu, Low risk, two squads
recommended, 22,000 credits, ordered before Blind Watch. The debrief still names
Teodor Prell's dispatch authority and the Warion listening post in every
victory.

## Fiction

**Asharu** is Independent in vanilla: a failed terraforming world of scattered
ruins and poor farmland, settled by salvage families, criminals and oddballs.
The Hegemony claims it but keeps no administration and sends patrols only to
chase pirates or collect tariffs. Wreck crews work the fall from an old
low-orbit battle (`shepherd-recovery-dialogue.md`), and Wayline hauls their
consumables a little under its rivals' rates.

**Saltwell Cross** is a dust-road trading post where the
crawler road from the salvage claims meets the road to the public freight
apron. Wayline lands freight there on a schedule. Sable Wake's broker, **Oskar
Teague**, keeps a counting house in the old terraforming pump house
at the east edge of town: tolerated, profitable to the market, not loved.

**Why the company is not in trouble.** Three layers, none of them a mechanic:

- The company lands as Wayline's scheduled freight drop on the public apron:
  a known, paying hauler doing what it always does.
- The target is a pirate crew, the one thing the only authority that patrols
  Asharu exists to chase. Castle: "The Hegemony would thank us, if it noticed."
- Mara has bought the market warden's (Tobias Ankrum's) indifference with a season's freight
  credit. The warden's price: the fight stays at the pump house, and the market
  is not touched. The warden speaks for the town only, not Asharu's government
  or the Independent faction.

No faction relation, market or Hegemony state changes, as with the rest of the
arc.

## Mission flow

1. **Quiet phase.** Company boats land on the freight apron. Sable Wake is not
   yet hostile; civilians work the market. The player positions squads anywhere
   outside the compound's **exclusion ring**, and may take side objectives.
   The HUD shows the broker's scheduled lift-off clock (starting value tuned in
   play, about five minutes).
2. **Alarm.** The first of:
   - any shot fired by or at the company;
   - a company unit inside the exclusion ring seen by a Sable Wake sentry
     (sight, not mere presence; the ring is the compound grounds plus a short
     margin, drawn on the map during the quiet phase);
   - the lift-off clock expiring, in which case the broker's skimmer leaves
     with him aboard and he radios the alarm on his way out.
   Civilians flee the market (existing flee behaviour); hidden Sable Wake
   drinkers in the cantina arm and join unless the warden's watch holds them.
3. **Burning ledger.** From the alarm, clerks feed the books into the burn
   barrel: a visible progress bar (about 75 seconds, tuned in play). Seizing the
   ledger room (the existing Raid seizure: uncontested hold inside the room)
   stops the burn. Unburned share at seizure is the **ledger grade**.
4. **Broker (optional).** On the alarm the broker leaves his office for the
   skimmer pad beyond the canal crossing. A company unit reaching him first
   makes him surrender; he then follows his captors and must reach the freight
   apron alive. If he boards, he escapes. If the clock expired, he is already
   gone.
5. **Return.** Any surviving company unit returns the records to the freight
   apron; the broker, if held, must arrive too for the catch to count.

Defeat is the ordinary defender elimination of the company. A fully burned
ledger still yields the strongbox (Prell's signature, the Warion lead), so the
story always advances on victory; grade and broker change only the debrief and
any bonus (see Open questions).

## Side objectives (quiet phase only)

Each must be completed before the alarm to pay off; one started and not finished
pays nothing. All live entirely outside the exclusion ring, so their cost is
clock time and squads not yet in position, never detection.

**The salvagers' coupling.** The Harrow crew (led by Anneke Harrow), a Wayline account,
sits stranded in their yard on the crawler road waiting for a drive coupling
that is in this drop. A squad carries the crate from the apron to the yard
through the market. Payoff: when the alarm sounds, a four-person armed salvage
team (militia quality, allied, separate from the company) advances on the
compound's east gate from the crawler road. They are a real force that can die;
the company owes them nothing in settlement.

**The warden's nod.** A squad leader walks to the warden's well house and
stays there briefly. Payoff: the town watch holds the cantina, so its hidden
Sable Wake drinkers never join, and two watchmen on market rooftops act as
spotters, revealing the compound yard to the company until the alarm.

**Cutting the feed.** Sable Wake runs the compound's guns off an illegal tap on
the town's old terraforming power line. The tap's junction box stands outside
the ring, between the Harrow lane and the postern. A squad that works it for a
short, unbroken time cuts the feed. Payoff: the gun nests are dead when the
alarm sounds. Cutting Sable Wake's own tap offends nobody in town. The pillboxes
are manned, not powered, so the feed does not touch them.

A fourth candidate, quietly cutting the compound's radio mast on the ridge to
delay the burn, is deferred until these three are played.

## Defences (owner, 2026-10-25)

The first headless runs on the larger compound read as a cakewalk. The owner
asked for environmental dangers that a careful approach or a side objective
evens out, built from the fort machinery (`forts.md`, `ground-defences.md`)
rather than new systems.

- **Gun nests.** Three powered nests, machine gun or light cannon, never flak:
  - one on the gate drive, covering the crawler-road approach;
  - one by the tank farm, covering the postern and the market approach;
  - one in the east yard, covering the ledger room's yard doors.

  They are deadly to infantry in the open. They fall to rockets, the Hound or
  charges, or go dead if the feed was cut. Rockets are in reach at this point in
  the campaign: the Convoy Escort Issue is on sale once Thunder Run settles
  (`campaign-kit-arc.md`). The nests are defences, never capture objectives.
- **Pillboxes.** Small hardened bunkers with firing slits, each covering an
  entrance from inside: the compound gate, the postern, and the pump house's
  yard doors. They are manned, not powered: Sable Wake crew in them fights from
  the slits under cover. They come from the existing posts (gate, wall and
  clerks), never extra headcount. They are held places, never capture objectives.
- **Volatile tanks (if the mechanic exists).** Fuel tanks in the tank farm that
  go up under fire, dangerous to whoever stands beside them. Use an existing
  explosive-prop or fire mechanic if there is one; otherwise leave it as a note.
  *Checked: none exists.* A prop's destroyed state is presentation only
  (`PropDamage`), and the fire system burns wooden props alone (`BattleFire`).
  The one prop-like blast is a parked airframe's cook-off
  (`AirframeCookOffSystem`, through `Detonations`). Volatile tanks need a hook
  from a prop's destroyed state to a detonation, so the tank farm is scenery
  until that hook exists.

Difficulty is then tuned against the worst approach (autonomous company, no side
objectives, which should cost real losses but still win) and the best approach
(feed cut, rockets on the nests, which should be clearly easier).

## Map

Authored like Relief and Shepherd Basin: a Java recipe plus road, terrain and
damage documents. 240x112 cells, wide enough for a compound of several yards. No compound filler and no grown garrison
precinct; buildings publish only through `HeldPlaces`, so nothing becomes a
capture point by accident. Surface palette follows the actual planet (ARID).

| Area | Content |
|---|---|
| West edge | Public freight apron: the company landing area (all berths one logical area), a parked Wayline hauler (civilian scenery), fuel bowser, crates. |
| Centre | The crossroads: a wide rutted crawler road from the east-north-east claims and the apron road. Caravanserai market along both: stalls, staffed warehouses and shops, the cantina, the warden's well house, a residential cluster. |
| Crawler road | The Harrow salvage yard with its stranded crawler, between market and compound. |
| North | A rock escarpment (raised cliffs) with a standable ridge reached by ramps, overlooking the compound yard: an overwatch position to take during the quiet phase. |
| South | The dry terraforming canal (excavated, impassable, see-through) with dead fields along it and two crossings. |
| East | The pump house compound, about 87x41: the pump house at its heart (counting room and ledger room with the burn barrel, broker's office, radio room; every office opens on the yard as well as the corridor, and the ledger room has four ways in), the motor pool and quarters west (crawler garage, vehicle shed, bunkhouse, mess, tank farm), the works east (workshop, stores), a walled yard with cover in every part of it, the gate with a boom and chicane on the crawler road, a postern to the market, a south gate, sentry posts. The defences (`Layout.nests`, `Layout.pillboxes`): three gun nests on paved pads (a light cannon on the gate drive, machine guns by the tank farm and in the east yard) and three pillboxes, at the gate, the postern and the pump house's yard doors, crewed from the gate, wall and clerks' posts. The feed junction (`Layout.feedJunction`) stands on its pad south of the back lane, with the power line carried overhead to the compound's west wall. The broker's skimmer pad lies beyond the southern canal crossing. |
| Beyond | Open hardpan, wreck debris and scattered ruins framing the edges. |

Three approaches to the compound must be real choices: through the market
(cover, civilians, short), along the ridge (overwatch, slow), and up the crawler
road past the salvage yard (direct, exposed).

**Art needs.** The map dresses these with stand-ins until art exists; none is
generated yet.

| Needed | Stand-in now |
|---|---|
| Market stall (table, goods, cloth) | `mess-table-long` under a street-kit awning, with a crate |
| The warden's well (head, windlass) | `industrial-pump-set` in the court |
| Burn barrel (ledger fire) | `industrial-drum-cluster` |
| Broker's skimmer, parked | a bare concrete pad |
| Compound sentry tower | a `fort-emplacement` prop beside the gate; the gate pillbox holds the gate itself |
| Power-line pylons, junction box | an overhead pipe rack; `industrial-switchboard` on a concrete pad |
| Market cart, burnt-out terraforming pipe | parked trucks; `industrial-pipe-bundle` pieces |

**Garrison.** One finite Sable Wake crew of about 18 at Low quality: gate and
wall sentries, roof watch, clerks' guards in the pump house, crawler crew, and
the cantina drinkers. No reinforcements, vehicles or aircraft.

## Work pieces

Each piece is a separate sub-session. They share no source files until the
wiring step; contracts between them are the named sites below.

**A. Map — `DustLedgerCrossing`.** The recipe, `dust-ledger` road, terrain and
damage documents, staffed buildings and ambient civilians placed in the market,
and a `SnapshotSuite` with look-preset shots. Its `Layout` record exposes:
landing area; ledger room (seizure cells) and burn barrel; broker office and
skimmer pad; exclusion ring cells; sentry posts and garrison posts by role;
cantina interior; warden's well house; Harrow yard and crate drop cell; the
salvage team's start on the crawler road; rooftop spotter posts; canal
crossings; ridge overwatch cells. Proves connectivity from the apron to every
site and keeps all three approaches open.

**B. Mission behaviour — `DustLedgerSequence`.** Quiet phase (defenders hold
fire and do not perceive the company as hostile outside the ring), alarm
triggers, lift-off clock, ledger burn and grade, broker flight, surrender,
escort and escape, the two side objectives and their payoffs (allied salvage
team; cantina held and spotters), objective checklist and markers. Takes its
own `Sites` input record so it is built and unit-tested against small
synthetic grids, independent of piece A.

**C. Story and text (written; `dust-ledger-dialogue.md`).** Briefing scene with Mara and Castle (warden arrangement,
broker, lift-off), side-objective field calls (Harrow, the warden), alarm and
burn barks, and debrief variants over ledger grade x broker caught/escaped,
with the canonical Prell and Warion facts in all of them. Catalog strings under
`corvusDustLedger*`; HUD and checklist keys belong to piece B under
`dustLedgerHud*`. A chapter dialogue register beside the arc doc if the scene
outgrows strings.

**D. Wiring (parent session).** Switch `CorvusChapter.DUST_LEDGER` to the
authored fixture, map `Layout` onto `Sites`, retire the procedural Raid path for
this chapter, update `CorvusWaylineCampaignTest` and the `wayline-corvus`
snapshots, run `commanderEvidence`-style headless playback with the recommended
company, and fold the model into `early-operation-nouns.md` on shipping.

## Acceptance

- [x] Authored map renders from the look preset; no capture point exists except
  the mission's own objectives.
- [x] Every site is reachable from the apron; all three approaches stay open.
- [x] Second map pass: detail, a road hierarchy and a larger compound with inner
  structure, on a larger map if needed (owner, 2026-10-25).
- [x] Quiet phase holds until a listed trigger; each trigger raises the alarm in
  a unit test.
- [ ] Ledger burn stops at seizure and reports its grade; a burned ledger still
  completes the chapter.
- [x] Broker surrender, escort, escape and clock departure each resolve.
- [x] Each side objective pays off only if complete before the alarm.
- [x] Every victory debrief names Prell's authority and the Warion lead: the
  debrief selects Castle's opening by grade, Mara's finding by grade and broker,
  and Castle's close by broker (`MissionDebriefConversation.dustLedgerLines`).
- [ ] Headless playback with two squads wins with acceptable losses, with and
  without side objectives. Baseline on the second-pass compound
  (`commanderEvidence -Pmission=corvus -Dbattle.corvus.chapter=DUST_LEDGER`):
  victory in 224 s, losing 5/24 infantry against a militia-only crew. The
  autonomous company walks into the ring, is sighted (alarm at tick 2860) and
  takes no side objective, so this measures the worst approach only. On the
  first compound the same run lost 12/24, and 24/24 with elite clerks. The
  side-objective runs and live player-controlled tuning remain.
- [ ] Live play on Asharu in a campaign, including save/reload mid-arc.

## Open questions

- **Bonus pay.** The arc pays each chapter once. Default: ledger grade and the
  broker change only the debrief. Alternative: an optional-objective bonus
  (`optional-objectives.md`) for a full ledger and the broker, which changes the
  single-payment rule.
- Names are settled: Saltwell Cross, Oskar Teague, Anneke Harrow, Tobias Ankrum (see `dust-ledger-dialogue.md`).
- Starting clock and burn durations are tuning values, set in play.
