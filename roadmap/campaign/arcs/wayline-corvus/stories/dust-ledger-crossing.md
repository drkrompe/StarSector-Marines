# The Dust Ledger — Saltwell Cross

Status: IN PROGRESS — wired into the campaign: the chapter now plays Saltwell Cross with the quiet phase,
burning ledger and the broker as its one bonus objective. A second, more detailed map pass and difficulty tuning remain.
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
Jack Prell's dispatch authority and the Warion listening post in every
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
apron. Wayline lands freight there on a schedule. Sable's broker, **Oskar
Teague**, keeps a counting house in the old terraforming pump house
at the east edge of town: tolerated, profitable to the market, not loved.

**Why the company is not in trouble.** Three layers, none of them a mechanic:

- The company lands as Wayline's scheduled freight drop on the public apron:
  a known, paying hauler doing what it always does.
- The target is a pirate crew, the one thing the only authority that patrols
  Asharu exists to chase. Castle: "The Hegemony would thank us, if it noticed."
- Zoey has bought the market warden's (Tobias Ankrum's) indifference with a season's freight
  credit. The warden's price: the fight stays at the pump house, and the market
  is not touched. The warden speaks for the town only, not Asharu's government
  or the Independent faction.

No faction relation, market or Hegemony state changes, as with the rest of the
arc.

## Mission flow

1. **Quiet phase.** Company boats land on the freight apron. Sable is not
   yet hostile; civilians work the market. The player positions squads anywhere
   outside the compound's **exclusion ring**.
   The HUD shows the broker's scheduled lift-off clock (starting value tuned in
   play, about five minutes).
2. **Alarm.** The first of:
   - any shot fired by or at the company;
   - a company unit inside the exclusion ring seen by a Sable sentry
     (sight, not mere presence; the ring is the compound grounds plus a short
     margin, drawn on the map during the quiet phase);
   - the lift-off clock expiring, in which case the broker's skimmer leaves
     with him aboard and he radios the alarm on his way out.
   Civilians flee the market (existing flee behaviour).
3. **Burning ledger.** From the alarm, clerks feed the books into the burn
   barrel: a visible progress bar (about 75 seconds, tuned in play). Seizing the
   ledger room (the existing Raid seizure: uncontested hold inside the room)
   stops the burn. Unburned share at seizure is the **ledger grade**.
4. **Broker (bonus).** On the alarm the broker leaves his office for the
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

## Bonus objective: the broker

The mission has one side objective on the toolkit (`mission-command-nouns.md`),
declared in `DustLedgerSideObjectives`: take Teague alive. It pays 3,000
credits, Wayline's price for him (owner, 2026-10-26), beside a Low,
22,000-credit contract, and earns the mark "Wayline will remember".

It is the one bonus because it is a real choice made under fire. When the alarm
sounds the clerks start burning the ledger and Teague runs for his skimmer pad
beyond the canal crossing, so the player either commits everything to the
ledger room or splits a squad off to run him down and walk him back to the apron
alive. The lift-off clock makes the quiet phase part of the same choice: wait too
long and he leaves before the shooting starts.

The pad has a marker from the start. His surrender, delivery and death are the
job's own lines; his flight and boarding are the sequence's beats.

**Cut (owner, 2026-11-10).** Three quiet-phase jobs shipped and were removed for
being errands whose payoffs came only after the alarm: carrying the Harrow
crew's drive coupling to their yard (an allied salvage team at the alarm), the
warden's nod at the well house (the watch holding the cantina's hidden drinkers,
and rooftop spotters on the yard), and cutting the nests' power tap. The
cantina's drinkers went with them. The map keeps the Harrow yard, the well house
and the junction box as scenery.

## Defences (owner, 2026-10-25)

The first headless runs on the larger compound read as a cakewalk. The owner
asked for environmental dangers that a careful approach evens out, built from the fort machinery (`forts.md`, `ground-defences.md`)
rather than new systems.

- **Gun nests.** Three powered nests, machine gun or light cannon, never flak,
  one outside each way into the compound and facing across it (a wall blocks
  direct fire whole, so a nest inside the wall would have only the yard to
  shoot at; the yard doors are the pillboxes' to cover):
  - the light cannon north of the crawler gate, on the sand between the ridge's
    foot and the roads, looking south over the ground in front of the gate
    where the crawler road and the ridge road meet;
  - a machine gun south-west of the postern, across its front and the market
    approach;
  - a machine gun on the south gate's west flank, down the track to the
    skimmer pad.

  They are deadly to infantry in the open. They fall to rockets, the Hound or
  charges. Rockets are in reach at this point in
  the campaign: the Convoy Escort Issue is on sale once Thunder Run settles
  (`campaign-kit-arc.md`). The nests are defences, never capture objectives.
- **Pillboxes.** Small hardened bunkers with firing slits, each covering an
  entrance from inside: the compound gate, the postern, and the pump house's
  yard doors. They are manned, not powered: Sable crew in them fights from
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

- **The rocket truck (owner, 2026-10-26).** A lone Hound with SRMs walked
  through the nests and pillboxes, so Sable fields one shipped hull: an SRM
  technical (anti-armor, holds fire at men, rockets reaching 18 cells). It parks
  in the east yard by the ledger room's east door. The quiet phase covers it
  like every Sable body. From the alarm it works the corners: every two
  seconds it looks at the nearest company armor and drives down the yard's lane
  to the stand that armor cannot see (a wall between) that lies nearest it,
  keeping the one it holds unless another is clearly nearer. With armor in reach
  and in sight it stops and fires. Seen across open ground a Hound's chaingun
  outranges it, which is why it fights from behind corners rather than chasing.
  With no company armor down it stays parked. Rockets, the Hound's weapons and
  charges kill it.

Difficulty is the owner's playtest: the autonomous company should cost real
losses but still win, and a player who brings rockets to bear on the nests should
find it clearly easier. With the feed cut gone the nests are always live, so the
playtest checks they are not too harsh.

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
| East | The pump house compound, about 87x41: the pump house at its heart (counting room and ledger room with the burn barrel, broker's office, radio room; every office opens on the yard as well as the corridor, and the ledger room has four ways in), the motor pool and quarters west (crawler garage, vehicle shed, bunkhouse, mess, tank farm), the works east (workshop, stores), a walled yard with cover in every part of it, the gate with a boom and chicane on the crawler road, a postern to the market, a south gate, sentry posts. The defences (`Layout.nests`, `Layout.pillboxes`): three gun nests outside the wall, one flanking each way in (a light cannon at the crawler gate, machine guns at the postern and the south gate), each laid on its facing and three pillboxes, at the gate, the postern and the pump house's yard doors, crewed from the gate, wall and clerks' posts. The east yard leaves a light hull a lane (`Layout.technicalStands`): north between the pump house and the workshop to the gate drive and out through the gate, south past the clerks' pillbox into the south yard. No wall is opened for it, so infantry gain no way in. The feed junction (`Layout.feedJunction`) stands on its pad south of the back lane, with the power line carried overhead to the compound's west wall. The broker's skimmer pad lies beyond the southern canal crossing. |
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

**Garrison.** One finite Sable crew of about 18 at Low quality: gate and
wall sentries, roof watch, clerks' guards in the pump house, crawler crew, and
the cantina drinkers. The one reinforcement is the road reserve below; no
aircraft.

**Road reserve** (owner, 2026-10-30). The first company unit to step into the
exclusion ring, sighted or not, starts a short clock (25 seconds, untuned). When
it runs out two squads of six Sable militia and a Hound muster at the crawler
road's far end, east of the compound's gate, with a field line from Castle. The
Hound carries a light machine gun and no rocket pod, and is attached to the second
squad, so the two stay together; the first squad goes to hold the ledger room, the
second and its Hound the ground round the pump house. The reserve comes once,
whether or not the alarm has sounded, and counts as pending defenders until it
arrives (`DustLedgerReinforcements`; `Layout.reinforcementCells`).

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
escort and escape, objective checklist and markers. Takes its
own `Sites` input record so it is built and unit-tested against small
synthetic grids, independent of piece A.

**C. Story and text (written; `dust-ledger-dialogue.md`).** Briefing scene with Zoey and Castle (warden arrangement,
broker, lift-off), alarm and burn barks, and debrief variants over ledger grade x broker caught/escaped,
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
- [x] The broker is the only side objective, and pays only once delivered alive.
- [x] Every victory debrief names Prell's authority and the Warion lead: the
  debrief selects Castle's opening by grade, Zoey's finding by grade and broker,
  and Castle's close by broker (`MissionDebriefConversation.dustLedgerLines`).
- [ ] Headless playback with two squads wins with acceptable losses. Baseline on the second-pass compound
  (`commanderEvidence -Pmission=corvus -Dbattle.corvus.chapter=DUST_LEDGER`):
  victory in 224 s, losing 5/24 infantry against a militia-only crew. The
  autonomous company walks into the ring, is sighted (alarm at tick 2860) and
  never chases the broker. On the first compound the same run lost 12/24, and
  24/24 with elite clerks. Live player-controlled tuning remains.
- [ ] Live play on Asharu in a campaign, including save/reload mid-arc.

## Open questions

- Names are settled: Saltwell Cross, Oskar Teague, Anneke Harrow, Tobias Ankrum (see `dust-ledger-dialogue.md`).
- Starting clock and burn durations are tuning values, set in play.
